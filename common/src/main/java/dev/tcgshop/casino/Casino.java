package dev.tcgshop.casino;

import dev.masterball.api.platform.Net;
import dev.masterball.api.platform.event.PlayerEvents;
import dev.tcgshop.TcgShop;
import dev.tcgshop.advancement.CardAdvancements;
import dev.tcgshop.economy.CobbleDollarsBridge;
import dev.tcgshop.entity.CustomerEntity;
import dev.tcgshop.entity.NpcLines;
import dev.tcgshop.pricing.PriceService;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.random.RandomGenerator;

/**
 * Casino server (1.0 beta 3). Every bet is validated here (machine nearby, legal bet, limits, money) and settled
 * before the client hears about it: the screens only animate the outcome they receive. Money: CobbleDollars.
 * <ul>
 *   <li>Owner income: {@link CasinoConfig#ownerSharePercent} of the bets other players place goes to the machine's
 *       tray, but never more than half of the game's house edge ({@link #OWNER_EDGE_CAP}): otherwise two players (or
 *       an alt account) taking turns at each other's blackjack table (0.4% edge) would earn more from the cut than
 *       they lose to the house, printing money. For the rounds shop customers play (npcTick) the owner earns the
 *       house's theoretical win (stake x house edge): with the real swings and a tray that cannot go below 0, an
 *       owner emptying the tray often would keep every customer loss and never pay a customer win.</li>
 *   <li>Blackjack rounds live in memory per player; disconnecting, or opening another table, stands every open hand
 *       and pays the result, so a stake is never lost to a closed screen.</li>
 * </ul>
 */
public final class Casino {
    private static final RandomGenerator RNG = new SecureRandom();
    private static final int SPIN_COOLDOWN_TICKS = 15;
    private static final double REACH_SQ = 8 * 8;
    private static final int NPC_LINES = 8;
    /** The owner's cut of another player's bet is at most this fraction of the game's house edge. */
    private static final double OWNER_EDGE_CAP = 0.5;
    /** House edges (fraction of the stake): slots from the exact paytable, single-zero roulette 1/37, blackjack with
     *  basic strategy ~0.4% (tools/casino_rtp.py) and with the customers' "hit below 17" play ~5.4% (simulated). */
    private static final double SLOT_EDGE = 1 - SlotMath.exactRtp(), ROULETTE_EDGE = 1 / 37.0, BJ_EDGE = 0.004, BJ_NPC_EDGE = 0.054;

    private record Session(BlockPos pos, Blackjack game) {}

    private static final Map<UUID, Session> BLACKJACK = new HashMap<>();
    private static final Map<UUID, Long> LAST_SPIN = new HashMap<>();

    private Casino() {}

    public static void register() {
        CasinoConfig.load();
        Net.INSTANCE.s2c(CasinoPayloads.Open.ID, CasinoPayloads.Open.CODEC);
        Net.INSTANCE.s2c(CasinoPayloads.Update.ID, CasinoPayloads.Update.CODEC);
        Net.INSTANCE.c2s(CasinoPayloads.Act.ID, CasinoPayloads.Act.CODEC);
        Net.INSTANCE.receive(CasinoPayloads.Act.ID, (p, player) -> player.server.execute(() -> act(player, p)));
        PlayerEvents.LEAVE.register(player -> {
            ServerPlayer pl = player;
            Session s = BLACKJACK.remove(pl.getUUID());
            if (s != null) resolve(pl, s, false);
            LAST_SPIN.remove(pl.getUUID());
        });
        TcgShop.LOGGER.info("Casino odds: slots RTP {}%, roulette 97.30%, blackjack ~99.6% (basic strategy), wheel 75.9-88.9%, scratch {}/{}/{}%",
                String.format(java.util.Locale.ROOT, "%.2f", SlotMath.exactRtp() * 100),
                Math.round(ScratchMath.rtp(ScratchMath.Tier.BRONZE) * 100), Math.round(ScratchMath.rtp(ScratchMath.Tier.SILVER) * 100),
                Math.round(ScratchMath.rtp(ScratchMath.Tier.GOLD) * 100));
    }

    // ------------------------------------------------------------------ opening a machine

    public static void open(ServerPlayer p, BlockPos pos, CasinoGame g) {
        if (!CasinoConfig.INSTANCE.enabled) {
            p.displayClientMessage(Component.translatable("tcgshop.casino.disabled").withStyle(ChatFormatting.RED), true);
            return;
        }
        if (!Net.INSTANCE.canSend(p, CasinoPayloads.Open.ID)) {
            p.displayClientMessage(Component.translatable("tcgshop.casino.need_client").withStyle(ChatFormatting.RED), false);
            return;
        }
        CompoundTag d = base(p, g);
        if (p.level().getBlockEntity(pos) instanceof CasinoBlockEntity be) d.putString("OwnerName", be.ownerName);
        if (g == CasinoGame.BLACKJACK) {
            Session s = BLACKJACK.get(p.getUUID());
            if (s != null && !s.pos().equals(pos)) { // a round left open at another table: stand and pay it
                BLACKJACK.remove(p.getUUID());
                resolve(p, s, true);
                d.putLong("Balance", CobbleDollarsBridge.getBalance(p));
            } else if (s != null) d.put("State", s.game().toNbt());
        }
        Net.INSTANCE.send(p, new CasinoPayloads.Open(g.key, pos, d));
    }

    private static CompoundTag base(ServerPlayer p, CasinoGame g) {
        CasinoConfig c = CasinoConfig.INSTANCE;
        CompoundTag d = new CompoundTag();
        d.putLong("Balance", CobbleDollarsBridge.getBalance(p));
        d.putBoolean("Economy", CobbleDollarsBridge.isAvailable());
        d.putLong("Min", c.minBet);
        d.putLong("Max", g.maxBet());
        d.putInt("MaxChips", c.rouletteMaxChips);
        return d;
    }

    // ------------------------------------------------------------------ actions

    private static void act(ServerPlayer p, CasinoPayloads.Act a) {
        if (!CasinoConfig.INSTANCE.enabled) return;
        CasinoGame g = CasinoGame.byKey(a.game());
        if (g == null) return;
        if (p.distanceToSqr(a.pos().getCenter()) > REACH_SQ) return;
        if (!(p.level().getBlockState(a.pos()).getBlock() instanceof CasinoMachineBlock b) || b.game != g) return;
        if (!(p.level().getBlockEntity(a.pos()) instanceof CasinoBlockEntity be)) return;
        CompoundTag d = a.data();
        switch (g) {
            case SLOTS, VIP_SLOTS -> slots(p, be, g, d);
            case ROULETTE -> roulette(p, be, d);
            case WHEEL -> wheel(p, be, d);
            case BLACKJACK -> blackjack(p, be, d);
            case DICE -> dice(p, be, d);
            case COIN -> coin(p, be, d);
        }
    }

    /** Spin games: one spin per animation (and a short server cooldown against packet spam). */
    private static boolean cooldown(ServerPlayer p) {
        long now = p.serverLevel().getGameTime();
        Long last = LAST_SPIN.get(p.getUUID());
        if (last != null && now - last < SPIN_COOLDOWN_TICKS && now >= last) return true;
        LAST_SPIN.put(p.getUUID(), now);
        return false;
    }

    private static boolean validBet(ServerPlayer p, long bet, long max) {
        CasinoConfig c = CasinoConfig.INSTANCE;
        if (bet < c.minBet || bet > max) {
            p.displayClientMessage(Component.translatable("tcgshop.casino.bad_bet", PriceService.format(c.minBet), PriceService.format(max)).withStyle(ChatFormatting.RED), true);
            return false;
        }
        return true;
    }

    private static boolean charge(ServerPlayer p, long amount) {
        if (!CobbleDollarsBridge.isAvailable()) {
            p.displayClientMessage(Component.translatable("tcgshop.casino.no_economy").withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (amount <= 0 || !CobbleDollarsBridge.tryCharge(p, amount)) {
            p.displayClientMessage(Component.translatable("tcgshop.casino.no_money", PriceService.format(amount)).withStyle(ChatFormatting.RED), true);
            ding(p, SoundEvents.VILLAGER_NO, 0.6f, 1f);
            return false;
        }
        return true;
    }

    /**
     * No gambling with the Wholesaler's money: while the player has an active loan (dev.tcgshop.phone.Loans) every new
     * stake (spin, roulette bets, blackjack deal / double / split) is refused with a notice. Returns true if refused.
     * A blackjack round already running can still be played out (hit / stand), so no stake is ever left hanging.
     */
    private static boolean loanBlocked(ServerPlayer p) {
        if (!dev.tcgshop.phone.Loans.hasActiveLoan(p.getServer(), p.getUUID())) return false;
        dev.tcgshop.phone.Loans.Credit c = dev.tcgshop.phone.Loans.peek(p.getServer(), p.getUUID());
        long owed = c == null || c.loan == null ? 0 : c.loan.owed;
        p.displayClientMessage(Component.translatable("tcgshop.casino.loan_blocked", PriceService.format(owed)).withStyle(ChatFormatting.RED), true);
        ding(p, SoundEvents.VILLAGER_NO, 0.6f, 1f);
        return true;
    }

    /**
     * Pays, records the round, gives the owner their share and handles achievements / announcements.
     * edge: the game's house edge for this bet (caps the owner's cut, see the class comment).
     */
    private static void settle(ServerPlayer p, CasinoBlockEntity be, CasinoGame g, long bet, long payout, double edge) {
        if (payout > 0) CobbleDollarsBridge.add(p, payout);
        if (be != null) {
            be.record(bet, payout);
            double share = Math.min(CasinoConfig.INSTANCE.ownerSharePercent / 100.0, Math.max(0, edge) * OWNER_EDGE_CAP);
            if (be.owner != null && !be.isOwner(p)) be.tray += (long) Math.floor(bet * share);
        }
        if (payout > bet) {
            CardAdvancements.grant(p, "casino_first_win");
            if (payout - bet >= 1_000_000) CardAdvancements.grant(p, "casino_millionaire");
        }
        announce(p, Component.translatable("tcgshop.casino.game." + g.key), payout);
    }

    private static void announce(ServerPlayer p, Component where, long payout) {
        long from = CasinoConfig.INSTANCE.announceWinsFrom;
        if (from <= 0 || payout < from) return;
        p.getServer().getPlayerList().broadcastSystemMessage(Component.translatable("tcgshop.casino.announce", p.getDisplayName(),
                Component.literal(PriceService.format(payout)).withStyle(ChatFormatting.GOLD), where).withStyle(ChatFormatting.LIGHT_PURPLE), false);
    }

    private static void ding(ServerPlayer p, net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
        p.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
    }

    private static void send(ServerPlayer p, CasinoGame g, CompoundTag d) {
        d.putLong("Balance", CobbleDollarsBridge.getBalance(p));
        if (Net.INSTANCE.canSend(p, CasinoPayloads.Update.ID)) Net.INSTANCE.send(p, new CasinoPayloads.Update(g.key, d));
    }

    // ------------------------------------------------------------------ slots

    private static void slots(ServerPlayer p, CasinoBlockEntity be, CasinoGame g, CompoundTag d) {
        long bet = d.getLong("Bet");
        if (!validBet(p, bet, g.maxBet()) || loanBlocked(p) || cooldown(p) || !charge(p, bet)) return;
        int[] reels = SlotMath.spin(RNG);
        int mult = SlotMath.multiplier(reels);
        long payout = bet * mult;
        settle(p, be, g, bet, payout, SLOT_EDGE);
        if (reels[0] == SlotMath.GEM && reels[1] == SlotMath.GEM && reels[2] == SlotMath.GEM) CardAdvancements.grant(p, "casino_jackpot");
        be.animate(reels);
        p.serverLevel().playSound(null, be.getBlockPos(), SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.BLOCKS, 0.6f, 1.6f);
        CompoundTag out = new CompoundTag();
        out.putIntArray("Reels", reels);
        out.putInt("Mult", mult);
        out.putLong("Bet", bet);
        out.putLong("Payout", payout);
        send(p, g, out);
    }

    // ------------------------------------------------------------------ roulette

    private static void roulette(ServerPlayer p, CasinoBlockEntity be, CompoundTag d) {
        CasinoConfig c = CasinoConfig.INSTANCE;
        ListTag list = d.getList("Bets", Tag.TAG_COMPOUND);
        if (list.isEmpty() || list.size() > c.rouletteMaxChips) return;
        Map<Long, Long> bets = new HashMap<>();
        long total = 0;
        for (int i = 0; i < list.size(); i++) {
            CompoundTag b = list.getCompound(i);
            long mask = b.getLong("M"), amount = b.getLong("A");
            if (!RouletteMath.legal(mask) || amount < c.minBet || amount > c.rouletteMaxTotalBet) return; // forged or out of limits
            total += amount;
            if (total > c.rouletteMaxTotalBet) break;
            bets.merge(mask, amount, Long::sum);
        }
        if (!validBet(p, total, c.rouletteMaxTotalBet) || loanBlocked(p) || cooldown(p) || !charge(p, total)) return;
        int n = RNG.nextInt(37);
        long payout = 0;
        List<Long> won = new ArrayList<>();
        for (Map.Entry<Long, Long> e : bets.entrySet()) {
            long w = RouletteMath.payout(e.getKey(), e.getValue(), n);
            if (w > 0) {
                payout += w;
                won.add(e.getKey());
                if (Long.bitCount(e.getKey()) == 1) CardAdvancements.grant(p, "casino_straight_up");
            }
        }
        settle(p, be, CasinoGame.ROULETTE, total, payout, ROULETTE_EDGE);
        be.animate(n);
        p.serverLevel().playSound(null, be.getBlockPos(), SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.BLOCKS, 0.6f, 1.3f);
        CompoundTag out = new CompoundTag();
        out.putInt("N", n);
        out.putLong("Bet", total);
        out.putLong("Payout", payout);
        long[] w = new long[won.size()];
        for (int i = 0; i < w.length; i++) w[i] = won.get(i);
        out.putLongArray("Won", w);
        send(p, CasinoGame.ROULETTE, out);
    }

    // ------------------------------------------------------------------ fortune wheel

    private static void wheel(ServerPlayer p, CasinoBlockEntity be, CompoundTag d) {
        int sym = d.getInt("Sym");
        long bet = d.getLong("Bet");
        if (sym < 0 || sym >= WheelMath.SYMBOLS) return;
        if (!validBet(p, bet, CasinoGame.WHEEL.maxBet()) || loanBlocked(p) || cooldown(p) || !charge(p, bet)) return;
        int seg = WheelMath.spin(RNG);
        long payout = WheelMath.payout(sym, bet, seg);
        settle(p, be, CasinoGame.WHEEL, bet, payout, 1 - WheelMath.rtp(sym));
        if (payout > 0 && sym >= 5) CardAdvancements.grant(p, "casino_wheel_joker");
        be.animate(seg);
        p.serverLevel().playSound(null, be.getBlockPos(), SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.BLOCKS, 0.6f, 1.1f);
        CompoundTag out = new CompoundTag();
        out.putInt("Seg", seg);
        out.putInt("Sym", sym);
        out.putLong("Bet", bet);
        out.putLong("Payout", payout);
        send(p, CasinoGame.WHEEL, out);
    }

    // ------------------------------------------------------------------ 1.1: dice table and coin toss

    private static void dice(ServerPlayer p, CasinoBlockEntity be, CompoundTag d) {
        int pick = d.getInt("Pick");
        long bet = d.getLong("Bet");
        if (pick < 0 || pick >= DiceMath.COUNT) return;
        if (!validBet(p, bet, CasinoGame.DICE.maxBet()) || loanBlocked(p) || cooldown(p) || !charge(p, bet)) return;
        int d1 = DiceMath.roll(RNG), d2 = DiceMath.roll(RNG);
        long payout = DiceMath.payout(pick, bet, d1, d2);
        settle(p, be, CasinoGame.DICE, bet, payout, 1 - DiceMath.rtp(pick));
        be.animate(d1, d2);
        p.serverLevel().playSound(null, be.getBlockPos(), dev.tcgshop.TcgSounds.CASINO_CHIP, SoundSource.BLOCKS, 0.7f, 1f);
        CompoundTag out = new CompoundTag();
        out.putInt("D1", d1);
        out.putInt("D2", d2);
        out.putInt("Pick", pick);
        out.putLong("Bet", bet);
        out.putLong("Payout", payout);
        send(p, CasinoGame.DICE, out);
    }

    private static void coin(ServerPlayer p, CasinoBlockEntity be, CompoundTag d) {
        int pick = d.getInt("Pick");
        long bet = d.getLong("Bet");
        if (pick < 0 || pick >= CoinMath.COUNT) return;
        if (!validBet(p, bet, CasinoGame.COIN.maxBet()) || loanBlocked(p) || cooldown(p) || !charge(p, bet)) return;
        int side = CoinMath.flip(RNG);
        long payout = CoinMath.payout(pick, bet, side);
        settle(p, be, CasinoGame.COIN, bet, payout, 1 - CoinMath.rtp(pick));
        be.animate(side);
        p.serverLevel().playSound(null, be.getBlockPos(), dev.tcgshop.TcgSounds.CASINO_CHIP, SoundSource.BLOCKS, 0.7f, 1.4f);
        CompoundTag out = new CompoundTag();
        out.putInt("Side", side);
        out.putInt("Pick", pick);
        out.putLong("Bet", bet);
        out.putLong("Payout", payout);
        send(p, CasinoGame.COIN, out);
    }

    // ------------------------------------------------------------------ blackjack

    private static void blackjack(ServerPlayer p, CasinoBlockEntity be, CompoundTag d) {
        String a = d.getString("A");
        Session s = BLACKJACK.get(p.getUUID());
        if (s != null && !s.pos().equals(be.getBlockPos())) { // round open elsewhere: settle it first
            BLACKJACK.remove(p.getUUID());
            resolve(p, s, true);
            s = null;
        }
        switch (a) {
            case "deal" -> {
                if (s != null) break; // a round is already running: just resend it
                long bet = d.getLong("Bet");
                if (!validBet(p, bet, CasinoGame.BLACKJACK.maxBet()) || loanBlocked(p) || !charge(p, bet)) return;
                s = new Session(be.getBlockPos(), new Blackjack(bet, RNG));
                BLACKJACK.put(p.getUUID(), s);
                p.serverLevel().playSound(null, be.getBlockPos(), SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 0.8f, 1.4f);
            }
            case "hit" -> { if (s != null) s.game().hit(); }
            case "stand" -> { if (s != null) s.game().stand(); }
            case "double" -> {
                if (s == null || !s.game().canDouble()) break;
                if (loanBlocked(p) || !charge(p, s.game().current().bet)) break;
                s.game().doubleDown();
            }
            case "split" -> {
                if (s == null || !s.game().canSplit()) break;
                if (loanBlocked(p) || !charge(p, s.game().hands.get(0).bet)) break;
                s.game().split();
            }
            default -> {}
        }
        CompoundTag out = new CompoundTag();
        if (s != null) {
            Blackjack bj = s.game();
            out = bj.toNbt();
            if (bj.finished) {
                BLACKJACK.remove(p.getUUID());
                settleBlackjack(p, be, bj);
                out.putLong("Payout", bj.totalPayout());
                out.putLong("Bet", bj.totalBet());
            }
            int[] view = new int[2];
            view[0] = Blackjack.total(bj.hands.get(0).cards);
            view[1] = bj.finished ? Blackjack.total(bj.dealer) : 0;
            be.animate(view);
        } else out.putBoolean("Idle", true);
        send(p, CasinoGame.BLACKJACK, out);
    }

    private static void settleBlackjack(ServerPlayer p, CasinoBlockEntity be, Blackjack bj) {
        settle(p, be, CasinoGame.BLACKJACK, bj.totalBet(), bj.totalPayout(), BJ_EDGE);
        for (Blackjack.Hand h : bj.hands) if ("blackjack".equals(h.outcome)) CardAdvancements.grant(p, "casino_blackjack");
    }

    /** A round the player left: every open hand stands, the dealer plays, the result is paid. */
    private static void resolve(ServerPlayer p, Session s, boolean tell) {
        Blackjack bj = s.game();
        bj.forceStand();
        CasinoBlockEntity be = p.getServer() != null && p.level().getBlockEntity(s.pos()) instanceof CasinoBlockEntity b ? b : null;
        settleBlackjack(p, be, bj);
        if (tell) p.displayClientMessage(Component.translatable("tcgshop.casino.bj.auto_stand", PriceService.format(bj.totalPayout())).withStyle(ChatFormatting.YELLOW), false);
    }

    // ------------------------------------------------------------------ scratch cards

    public static void scratch(ServerPlayer p, ItemStack stack, ScratchMath.Tier tier, boolean instant) {
        if (!CasinoConfig.INSTANCE.enabled) {
            p.displayClientMessage(Component.translatable("tcgshop.casino.disabled").withStyle(ChatFormatting.RED), true);
            return;
        }
        if (!CobbleDollarsBridge.isAvailable()) {
            p.displayClientMessage(Component.translatable("tcgshop.casino.no_economy").withStyle(ChatFormatting.RED), true);
            return;
        }
        long face = faceValue(tier, stack);
        stack.consume(1, p);
        int mult = ScratchMath.roll(tier, RNG);
        long prize = face * mult;
        int[] grid = ScratchMath.grid(tier, mult, RNG);
        if (prize > 0) CobbleDollarsBridge.add(p, prize);
        if (mult >= 2) CardAdvancements.grant(p, "casino_first_win");
        if (mult >= 100) CardAdvancements.grant(p, "casino_scratch_top");
        if (prize - face >= 1_000_000) CardAdvancements.grant(p, "casino_millionaire");
        announce(p, Component.translatable("tcgshop.casino.scratch." + tier.key), prize);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.7f, 1.6f);
        if (instant || !Net.INSTANCE.canSend(p, CasinoPayloads.Open.ID)) {
            p.displayClientMessage(prize > 0
                    ? Component.translatable("tcgshop.casino.scratch.won", Component.literal(PriceService.format(prize)).withStyle(ChatFormatting.GOLD)).withStyle(ChatFormatting.GREEN)
                    : Component.translatable("tcgshop.casino.scratch.lost").withStyle(ChatFormatting.GRAY), true);
            if (prize > 0) ding(p, SoundEvents.PLAYER_LEVELUP, 0.7f, 1.4f);
            return;
        }
        CompoundTag d = new CompoundTag();
        d.putInt("Tier", tier.ordinal());
        d.putIntArray("Grid", grid);
        d.putInt("Mult", mult);
        d.putLong("Face", face);
        d.putLong("Prize", prize);
        d.putLong("Balance", CobbleDollarsBridge.getBalance(p));
        Net.INSTANCE.send(p, new CasinoPayloads.Open("scratch", BlockPos.ZERO, d));
    }

    /**
     * Face value a ticket pays multiples of: CasinoConfig.scratchFaceValues, but never above the ticket's store price
     * (itemPrices in tcgshop-economy.json). The two live in different files; raising the face value without the price
     * would otherwise turn a 59-70% ticket into free money.
     */
    public static long faceValue(ScratchMath.Tier tier, ItemStack ticket) {
        long face = CasinoConfig.INSTANCE.scratchFace(tier);
        long price = dev.tcgshop.shop.ShopServer.priceOf(ticket.copyWithCount(1), 1.0);
        return price > 0 ? Math.min(face, price) : face;
    }

    // ------------------------------------------------------------------ owner

    /** Owner: takes the tray and reads the machine's statistics. */
    public static void collect(ServerPlayer p, CasinoBlockEntity be) {
        long tray = be.tray;
        if (tray > 0 && CobbleDollarsBridge.add(p, tray)) {
            be.tray = 0;
            be.setChanged();
            ding(p, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
            p.displayClientMessage(Component.translatable("tcgshop.casino.collected", Component.literal(PriceService.format(tray)).withStyle(ChatFormatting.GOLD)).withStyle(ChatFormatting.GREEN), false);
        } else p.displayClientMessage(Component.translatable("tcgshop.casino.tray_empty").withStyle(ChatFormatting.GRAY), false);
        String rtp = be.wagered > 0 ? String.format(java.util.Locale.ROOT, "%.1f%%", be.paid * 100.0 / be.wagered) : "-";
        p.displayClientMessage(Component.translatable("tcgshop.casino.stats", be.rounds, PriceService.format(be.wagered), PriceService.format(be.paid), rtp)
                .withStyle(ChatFormatting.GRAY), false);
    }

    // ------------------------------------------------------------------ shop customers play

    /** Called every 10 s per machine: a nearby customer may play one round with the owner as the house. */
    static void npcTick(ServerLevel world, CasinoBlockEntity be) {
        CasinoConfig c = CasinoConfig.INSTANCE;
        if (!c.enabled || !c.npcGamblers || be.owner == null || world.random.nextDouble() >= c.npcPlayChance) return;
        List<CustomerEntity> near = world.getEntitiesOfClass(CustomerEntity.class, new AABB(be.getBlockPos()).inflate(5, 2, 5),
                e -> e.isAlive() && e.type() != CustomerEntity.Type.THIEF);
        if (near.isEmpty()) return;
        CustomerEntity npc = near.get(world.random.nextInt(near.size()));
        long bet = c.npcMinBet + (long) (RNG.nextDouble() * (c.npcMaxBet - c.npcMinBet + 1));
        bet = Math.max(c.npcMinBet, bet / 50 * 50);
        long payout;
        double edge;
        CasinoGame g = be.game();
        switch (g) {
            case SLOTS, VIP_SLOTS -> {
                int[] r = SlotMath.spin(RNG);
                payout = bet * SlotMath.multiplier(r);
                edge = SLOT_EDGE;
                be.animate(r);
            }
            case ROULETTE -> {
                int n = RNG.nextInt(37);
                long mask = RNG.nextInt(4) == 0 ? RouletteMath.mask(1 + RNG.nextInt(36))
                        : RNG.nextBoolean() ? RouletteMath.RED_MASK : RouletteMath.BLACK_MASK;
                payout = RouletteMath.payout(mask, bet, n);
                edge = ROULETTE_EDGE;
                be.animate(n);
            }
            case WHEEL -> {
                int sym = RNG.nextInt(3) == 0 ? RNG.nextInt(WheelMath.SYMBOLS) : RNG.nextInt(2);
                int seg = WheelMath.spin(RNG);
                payout = WheelMath.payout(sym, bet, seg);
                edge = 1 - WheelMath.rtp(sym);
                be.animate(seg);
            }
            case DICE -> { // 1.1: customers mostly bet under / over 7
                int pick = RNG.nextInt(4) == 0 ? RNG.nextInt(DiceMath.COUNT) : RNG.nextBoolean() ? 0 : 2;
                int d1 = DiceMath.roll(RNG), d2 = DiceMath.roll(RNG);
                payout = DiceMath.payout(pick, bet, d1, d2);
                edge = 1 - DiceMath.rtp(pick);
                be.animate(d1, d2);
            }
            case COIN -> {
                int pick = RNG.nextInt(20) == 0 ? 2 : RNG.nextInt(2);
                int side = CoinMath.flip(RNG);
                payout = CoinMath.payout(pick, bet, side);
                edge = 1 - CoinMath.rtp(pick);
                be.animate(side);
            }
            default -> {
                Blackjack bj = new Blackjack(bet, RNG);
                bj.autoPlay();
                bet = bj.totalBet();
                payout = bj.totalPayout();
                edge = BJ_NPC_EDGE;
                be.animate(Blackjack.total(bj.hands.get(0).cards), Blackjack.total(bj.dealer));
            }
        }
        be.record(bet, payout);
        // the house's theoretical win, not the round's swing (see the class comment: no free-roll on an emptied tray)
        be.tray += Math.max(0, Math.round(bet * edge));
        be.setChanged();
        npc.getLookControl().setLookAt(be.getBlockPos().getX() + 0.5, be.getBlockPos().getY() + 0.8, be.getBlockPos().getZ() + 0.5);
        boolean win = payout > bet;
        NpcLines.speak(npc, Component.translatable("tcgshop.casino.npc." + (win ? "win" : payout == bet ? "push" : "lose") + "." + RNG.nextInt(NPC_LINES),
                PriceService.format(win ? payout : bet)));
        world.playSound(null, be.getBlockPos(), win ? SoundEvents.PLAYER_LEVELUP : SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.BLOCKS,
                win ? 0.4f : 0.5f, win ? 1.6f : 1.2f);
    }
}

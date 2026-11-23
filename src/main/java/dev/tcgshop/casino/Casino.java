package dev.tcgshop.casino;

import dev.tcgshop.TcgShop;
import dev.tcgshop.advancement.CardAdvancements;
import dev.tcgshop.economy.CobbleDollarsBridge;
import dev.tcgshop.entity.CustomerEntity;
import dev.tcgshop.entity.NpcLines;
import dev.tcgshop.pricing.PriceService;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

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
        PayloadTypeRegistry.playS2C().register(CasinoPayloads.Open.ID, CasinoPayloads.Open.CODEC);
        PayloadTypeRegistry.playS2C().register(CasinoPayloads.Update.ID, CasinoPayloads.Update.CODEC);
        PayloadTypeRegistry.playC2S().register(CasinoPayloads.Act.ID, CasinoPayloads.Act.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(CasinoPayloads.Act.ID, (p, ctx) -> ctx.server().execute(() -> act(ctx.player(), p)));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayerEntity pl = handler.getPlayer();
            Session s = BLACKJACK.remove(pl.getUuid());
            if (s != null) resolve(pl, s, false);
            LAST_SPIN.remove(pl.getUuid());
        });
        TcgShop.LOGGER.info("Casino odds: slots RTP {}%, roulette 97.30%, blackjack ~99.6% (basic strategy), wheel 75.9-88.9%, scratch {}/{}/{}%",
                String.format(java.util.Locale.ROOT, "%.2f", SlotMath.exactRtp() * 100),
                Math.round(ScratchMath.rtp(ScratchMath.Tier.BRONZE) * 100), Math.round(ScratchMath.rtp(ScratchMath.Tier.SILVER) * 100),
                Math.round(ScratchMath.rtp(ScratchMath.Tier.GOLD) * 100));
    }

    // ------------------------------------------------------------------ opening a machine

    public static void open(ServerPlayerEntity p, BlockPos pos, CasinoGame g) {
        if (!CasinoConfig.INSTANCE.enabled) {
            p.sendMessage(Text.translatable("tcgshop.casino.disabled").formatted(Formatting.RED), true);
            return;
        }
        if (!ServerPlayNetworking.canSend(p, CasinoPayloads.Open.ID)) {
            p.sendMessage(Text.translatable("tcgshop.casino.need_client").formatted(Formatting.RED), false);
            return;
        }
        NbtCompound d = base(p, g);
        if (p.getWorld().getBlockEntity(pos) instanceof CasinoBlockEntity be) d.putString("OwnerName", be.ownerName);
        if (g == CasinoGame.BLACKJACK) {
            Session s = BLACKJACK.get(p.getUuid());
            if (s != null && !s.pos().equals(pos)) { // a round left open at another table: stand and pay it
                BLACKJACK.remove(p.getUuid());
                resolve(p, s, true);
                d.putLong("Balance", CobbleDollarsBridge.getBalance(p));
            } else if (s != null) d.put("State", s.game().toNbt());
        }
        ServerPlayNetworking.send(p, new CasinoPayloads.Open(g.key, pos, d));
    }

    private static NbtCompound base(ServerPlayerEntity p, CasinoGame g) {
        CasinoConfig c = CasinoConfig.INSTANCE;
        NbtCompound d = new NbtCompound();
        d.putLong("Balance", CobbleDollarsBridge.getBalance(p));
        d.putBoolean("Economy", CobbleDollarsBridge.isAvailable());
        d.putLong("Min", c.minBet);
        d.putLong("Max", g.maxBet());
        d.putInt("MaxChips", c.rouletteMaxChips);
        return d;
    }

    // ------------------------------------------------------------------ actions

    private static void act(ServerPlayerEntity p, CasinoPayloads.Act a) {
        if (!CasinoConfig.INSTANCE.enabled) return;
        CasinoGame g = CasinoGame.byKey(a.game());
        if (g == null) return;
        if (p.squaredDistanceTo(a.pos().toCenterPos()) > REACH_SQ) return;
        if (!(p.getWorld().getBlockState(a.pos()).getBlock() instanceof CasinoMachineBlock b) || b.game != g) return;
        if (!(p.getWorld().getBlockEntity(a.pos()) instanceof CasinoBlockEntity be)) return;
        NbtCompound d = a.data();
        switch (g) {
            case SLOTS, VIP_SLOTS -> slots(p, be, g, d);
            case ROULETTE -> roulette(p, be, d);
            case WHEEL -> wheel(p, be, d);
            case BLACKJACK -> blackjack(p, be, d);
        }
    }

    /** Spin games: one spin per animation (and a short server cooldown against packet spam). */
    private static boolean cooldown(ServerPlayerEntity p) {
        long now = p.getServerWorld().getTime();
        Long last = LAST_SPIN.get(p.getUuid());
        if (last != null && now - last < SPIN_COOLDOWN_TICKS && now >= last) return true;
        LAST_SPIN.put(p.getUuid(), now);
        return false;
    }

    private static boolean validBet(ServerPlayerEntity p, long bet, long max) {
        CasinoConfig c = CasinoConfig.INSTANCE;
        if (bet < c.minBet || bet > max) {
            p.sendMessage(Text.translatable("tcgshop.casino.bad_bet", PriceService.format(c.minBet), PriceService.format(max)).formatted(Formatting.RED), true);
            return false;
        }
        return true;
    }

    private static boolean charge(ServerPlayerEntity p, long amount) {
        if (!CobbleDollarsBridge.isAvailable()) {
            p.sendMessage(Text.translatable("tcgshop.casino.no_economy").formatted(Formatting.RED), true);
            return false;
        }
        if (amount <= 0 || !CobbleDollarsBridge.tryCharge(p, amount)) {
            p.sendMessage(Text.translatable("tcgshop.casino.no_money", PriceService.format(amount)).formatted(Formatting.RED), true);
            ding(p, SoundEvents.ENTITY_VILLAGER_NO, 0.6f, 1f);
            return false;
        }
        return true;
    }

    /**
     * No gambling with the Wholesaler's money: while the player has an active loan (dev.tcgshop.phone.Loans) every new
     * stake (spin, roulette bets, blackjack deal / double / split) is refused with a notice. Returns true if refused.
     * A blackjack round already running can still be played out (hit / stand), so no stake is ever left hanging.
     */
    private static boolean loanBlocked(ServerPlayerEntity p) {
        if (!dev.tcgshop.phone.Loans.hasActiveLoan(p.getServer(), p.getUuid())) return false;
        dev.tcgshop.phone.Loans.Credit c = dev.tcgshop.phone.Loans.peek(p.getServer(), p.getUuid());
        long owed = c == null || c.loan == null ? 0 : c.loan.owed;
        p.sendMessage(Text.translatable("tcgshop.casino.loan_blocked", PriceService.format(owed)).formatted(Formatting.RED), true);
        ding(p, SoundEvents.ENTITY_VILLAGER_NO, 0.6f, 1f);
        return true;
    }

    /**
     * Pays, records the round, gives the owner their share and handles achievements / announcements.
     * edge: the game's house edge for this bet (caps the owner's cut, see the class comment).
     */
    private static void settle(ServerPlayerEntity p, CasinoBlockEntity be, CasinoGame g, long bet, long payout, double edge) {
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
        announce(p, Text.translatable("tcgshop.casino.game." + g.key), payout);
    }

    private static void announce(ServerPlayerEntity p, Text where, long payout) {
        long from = CasinoConfig.INSTANCE.announceWinsFrom;
        if (from <= 0 || payout < from) return;
        p.getServer().getPlayerManager().broadcast(Text.translatable("tcgshop.casino.announce", p.getDisplayName(),
                Text.literal(PriceService.format(payout)).formatted(Formatting.GOLD), where).formatted(Formatting.LIGHT_PURPLE), false);
    }

    private static void ding(ServerPlayerEntity p, net.minecraft.sound.SoundEvent sound, float volume, float pitch) {
        p.getServerWorld().playSound(null, p.getX(), p.getY(), p.getZ(), sound, SoundCategory.PLAYERS, volume, pitch);
    }

    private static void send(ServerPlayerEntity p, CasinoGame g, NbtCompound d) {
        d.putLong("Balance", CobbleDollarsBridge.getBalance(p));
        if (ServerPlayNetworking.canSend(p, CasinoPayloads.Update.ID)) ServerPlayNetworking.send(p, new CasinoPayloads.Update(g.key, d));
    }

    // ------------------------------------------------------------------ slots

    private static void slots(ServerPlayerEntity p, CasinoBlockEntity be, CasinoGame g, NbtCompound d) {
        long bet = d.getLong("Bet");
        if (!validBet(p, bet, g.maxBet()) || loanBlocked(p) || cooldown(p) || !charge(p, bet)) return;
        int[] reels = SlotMath.spin(RNG);
        int mult = SlotMath.multiplier(reels);
        long payout = bet * mult;
        settle(p, be, g, bet, payout, SLOT_EDGE);
        if (reels[0] == SlotMath.GEM && reels[1] == SlotMath.GEM && reels[2] == SlotMath.GEM) CardAdvancements.grant(p, "casino_jackpot");
        be.animate(reels);
        p.getServerWorld().playSound(null, be.getPos(), SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(), SoundCategory.BLOCKS, 0.6f, 1.6f);
        NbtCompound out = new NbtCompound();
        out.putIntArray("Reels", reels);
        out.putInt("Mult", mult);
        out.putLong("Bet", bet);
        out.putLong("Payout", payout);
        send(p, g, out);
    }

    // ------------------------------------------------------------------ roulette

    private static void roulette(ServerPlayerEntity p, CasinoBlockEntity be, NbtCompound d) {
        CasinoConfig c = CasinoConfig.INSTANCE;
        NbtList list = d.getList("Bets", NbtElement.COMPOUND_TYPE);
        if (list.isEmpty() || list.size() > c.rouletteMaxChips) return;
        Map<Long, Long> bets = new HashMap<>();
        long total = 0;
        for (int i = 0; i < list.size(); i++) {
            NbtCompound b = list.getCompound(i);
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
        p.getServerWorld().playSound(null, be.getPos(), SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(), SoundCategory.BLOCKS, 0.6f, 1.3f);
        NbtCompound out = new NbtCompound();
        out.putInt("N", n);
        out.putLong("Bet", total);
        out.putLong("Payout", payout);
        long[] w = new long[won.size()];
        for (int i = 0; i < w.length; i++) w[i] = won.get(i);
        out.putLongArray("Won", w);
        send(p, CasinoGame.ROULETTE, out);
    }

    // ------------------------------------------------------------------ fortune wheel

    private static void wheel(ServerPlayerEntity p, CasinoBlockEntity be, NbtCompound d) {
        int sym = d.getInt("Sym");
        long bet = d.getLong("Bet");
        if (sym < 0 || sym >= WheelMath.SYMBOLS) return;
        if (!validBet(p, bet, CasinoGame.WHEEL.maxBet()) || loanBlocked(p) || cooldown(p) || !charge(p, bet)) return;
        int seg = WheelMath.spin(RNG);
        long payout = WheelMath.payout(sym, bet, seg);
        settle(p, be, CasinoGame.WHEEL, bet, payout, 1 - WheelMath.rtp(sym));
        if (payout > 0 && sym >= 5) CardAdvancements.grant(p, "casino_wheel_joker");
        be.animate(seg);
        p.getServerWorld().playSound(null, be.getPos(), SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(), SoundCategory.BLOCKS, 0.6f, 1.1f);
        NbtCompound out = new NbtCompound();
        out.putInt("Seg", seg);
        out.putInt("Sym", sym);
        out.putLong("Bet", bet);
        out.putLong("Payout", payout);
        send(p, CasinoGame.WHEEL, out);
    }

    // ------------------------------------------------------------------ blackjack

    private static void blackjack(ServerPlayerEntity p, CasinoBlockEntity be, NbtCompound d) {
        String a = d.getString("A");
        Session s = BLACKJACK.get(p.getUuid());
        if (s != null && !s.pos().equals(be.getPos())) { // round open elsewhere: settle it first
            BLACKJACK.remove(p.getUuid());
            resolve(p, s, true);
            s = null;
        }
        switch (a) {
            case "deal" -> {
                if (s != null) break; // a round is already running: just resend it
                long bet = d.getLong("Bet");
                if (!validBet(p, bet, CasinoGame.BLACKJACK.maxBet()) || loanBlocked(p) || !charge(p, bet)) return;
                s = new Session(be.getPos(), new Blackjack(bet, RNG));
                BLACKJACK.put(p.getUuid(), s);
                p.getServerWorld().playSound(null, be.getPos(), SoundEvents.ITEM_BOOK_PAGE_TURN, SoundCategory.BLOCKS, 0.8f, 1.4f);
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
        NbtCompound out = new NbtCompound();
        if (s != null) {
            Blackjack bj = s.game();
            out = bj.toNbt();
            if (bj.finished) {
                BLACKJACK.remove(p.getUuid());
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

    private static void settleBlackjack(ServerPlayerEntity p, CasinoBlockEntity be, Blackjack bj) {
        settle(p, be, CasinoGame.BLACKJACK, bj.totalBet(), bj.totalPayout(), BJ_EDGE);
        for (Blackjack.Hand h : bj.hands) if ("blackjack".equals(h.outcome)) CardAdvancements.grant(p, "casino_blackjack");
    }

    /** A round the player left: every open hand stands, the dealer plays, the result is paid. */
    private static void resolve(ServerPlayerEntity p, Session s, boolean tell) {
        Blackjack bj = s.game();
        bj.forceStand();
        CasinoBlockEntity be = p.getServer() != null && p.getWorld().getBlockEntity(s.pos()) instanceof CasinoBlockEntity b ? b : null;
        settleBlackjack(p, be, bj);
        if (tell) p.sendMessage(Text.translatable("tcgshop.casino.bj.auto_stand", PriceService.format(bj.totalPayout())).formatted(Formatting.YELLOW), false);
    }

    // ------------------------------------------------------------------ scratch cards

    public static void scratch(ServerPlayerEntity p, ItemStack stack, ScratchMath.Tier tier, boolean instant) {
        if (!CasinoConfig.INSTANCE.enabled) {
            p.sendMessage(Text.translatable("tcgshop.casino.disabled").formatted(Formatting.RED), true);
            return;
        }
        if (!CobbleDollarsBridge.isAvailable()) {
            p.sendMessage(Text.translatable("tcgshop.casino.no_economy").formatted(Formatting.RED), true);
            return;
        }
        long face = faceValue(tier, stack);
        stack.decrementUnlessCreative(1, p);
        int mult = ScratchMath.roll(tier, RNG);
        long prize = face * mult;
        int[] grid = ScratchMath.grid(tier, mult, RNG);
        if (prize > 0) CobbleDollarsBridge.add(p, prize);
        if (mult >= 2) CardAdvancements.grant(p, "casino_first_win");
        if (mult >= 100) CardAdvancements.grant(p, "casino_scratch_top");
        if (prize - face >= 1_000_000) CardAdvancements.grant(p, "casino_millionaire");
        announce(p, Text.translatable("tcgshop.casino.scratch." + tier.key), prize);
        p.getServerWorld().playSound(null, p.getBlockPos(), SoundEvents.ITEM_BOOK_PAGE_TURN, SoundCategory.PLAYERS, 0.7f, 1.6f);
        if (instant || !ServerPlayNetworking.canSend(p, CasinoPayloads.Open.ID)) {
            p.sendMessage(prize > 0
                    ? Text.translatable("tcgshop.casino.scratch.won", Text.literal(PriceService.format(prize)).formatted(Formatting.GOLD)).formatted(Formatting.GREEN)
                    : Text.translatable("tcgshop.casino.scratch.lost").formatted(Formatting.GRAY), true);
            if (prize > 0) ding(p, SoundEvents.ENTITY_PLAYER_LEVELUP, 0.7f, 1.4f);
            return;
        }
        NbtCompound d = new NbtCompound();
        d.putInt("Tier", tier.ordinal());
        d.putIntArray("Grid", grid);
        d.putInt("Mult", mult);
        d.putLong("Face", face);
        d.putLong("Prize", prize);
        d.putLong("Balance", CobbleDollarsBridge.getBalance(p));
        ServerPlayNetworking.send(p, new CasinoPayloads.Open("scratch", BlockPos.ORIGIN, d));
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
    public static void collect(ServerPlayerEntity p, CasinoBlockEntity be) {
        long tray = be.tray;
        if (tray > 0 && CobbleDollarsBridge.add(p, tray)) {
            be.tray = 0;
            be.markDirty();
            ding(p, SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
            p.sendMessage(Text.translatable("tcgshop.casino.collected", Text.literal(PriceService.format(tray)).formatted(Formatting.GOLD)).formatted(Formatting.GREEN), false);
        } else p.sendMessage(Text.translatable("tcgshop.casino.tray_empty").formatted(Formatting.GRAY), false);
        String rtp = be.wagered > 0 ? String.format(java.util.Locale.ROOT, "%.1f%%", be.paid * 100.0 / be.wagered) : "-";
        p.sendMessage(Text.translatable("tcgshop.casino.stats", be.rounds, PriceService.format(be.wagered), PriceService.format(be.paid), rtp)
                .formatted(Formatting.GRAY), false);
    }

    // ------------------------------------------------------------------ shop customers play

    /** Called every 10 s per machine: a nearby customer may play one round with the owner as the house. */
    static void npcTick(ServerWorld world, CasinoBlockEntity be) {
        CasinoConfig c = CasinoConfig.INSTANCE;
        if (!c.enabled || !c.npcGamblers || be.owner == null || world.random.nextDouble() >= c.npcPlayChance) return;
        List<CustomerEntity> near = world.getEntitiesByClass(CustomerEntity.class, new Box(be.getPos()).expand(5, 2, 5),
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
        be.markDirty();
        npc.getLookControl().lookAt(be.getPos().getX() + 0.5, be.getPos().getY() + 0.8, be.getPos().getZ() + 0.5);
        boolean win = payout > bet;
        NpcLines.speak(npc, Text.translatable("tcgshop.casino.npc." + (win ? "win" : payout == bet ? "push" : "lose") + "." + RNG.nextInt(NPC_LINES),
                PriceService.format(win ? payout : bet)));
        world.playSound(null, be.getPos(), win ? SoundEvents.ENTITY_PLAYER_LEVELUP : SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(), SoundCategory.BLOCKS,
                win ? 0.4f : 0.5f, win ? 1.6f : 1.2f);
    }
}

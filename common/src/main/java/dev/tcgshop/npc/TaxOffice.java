package dev.tcgshop.npc;

import dev.masterball.api.card.CardInfo;
import dev.masterball.api.platform.Net;
import dev.masterball.api.platform.event.PlayerEvents;
import dev.masterball.api.platform.event.ServerEvents;
import dev.tcgshop.TcgShop;
import dev.tcgshop.collection.BinderData;
import dev.tcgshop.collection.BinderRegistry;
import dev.tcgshop.collection.BinderSlots;
import dev.tcgshop.collection.CollectionService;
import dev.tcgshop.compat.CobblemonBattles;
import dev.tcgshop.compat.MayorBattle;
import dev.tcgshop.economy.CobbleDollarsBridge;
import dev.tcgshop.entity.MayorEntity;
import dev.tcgshop.entity.TcgEntities;
import dev.tcgshop.phone.PhoneMessages;
import dev.tcgshop.pricing.PriceService;
import dev.tcgshop.shop.CardSales;
import dev.tcgshop.shop.ShopServer;
import dev.tcgshop.shop.ShopTier;
import dev.tcgshop.shop.ShopZones;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 1.1: shop taxes and the Mayor (FEATURES_1_1_B D1). Owners of an Iron shop (NpcConfig.taxMinTier) or better are
 * registered the first time they are seen with one; from then on, every taxIntervalDays Minecraft days (overworld day
 * counter, so sleeping counts) the bill falls due and a Mayor ({@link MayorEntity}) comes to the owner: at their nearest
 * shop when they are within 96 blocks of one, or, from the day after it fell due, wherever they are. He stays until the
 * bill is settled ({@link #STATE_DUE}, saved here, so restarts, logouts or unloaded chunks never cancel it).
 *
 * <ul>
 *   <li><b>Pay</b>: wallet first, then shop registers. Left alone until the next due day.</li>
 *   <li><b>Refuse</b>: with Cobblemon, a real trainer battle against his competitive team of six (compat/MayorBattle;
 *       if Cobblemon refuses it, a chain of {@link #FALLBACK_WINS} strong battles through compat/CobblemonBattles).
 *       Win = no tax this period. Lose, forfeit, flee, disconnect or walk away = he takes one random card from the
 *       player's binder ({@link #takeCard}), always. Without Cobblemon (or without any Pokémon able to battle) refusing
 *       means he takes the card right away.</li>
 *   <li><b>Ignored</b> for taxGraceDays: he collects by force with taxLateFee on top (wallet + registers); if that is
 *       not enough, he takes a card instead.</li>
 * </ul>
 * Tax = property part (taxPropertyRate of every owned shop core's price) + income part (income rate of the revenue
 * earned since the last settled bill: taxIncomeRate at the first taxed tier, + taxIncomeRatePerTier per tier above it,
 * up to taxIncomeRateMax), clamped to taxMinimum..taxMaximum. The bill is frozen when it falls due.
 */
public final class TaxOffice extends SavedData {
    private static final Factory<TaxOffice> TYPE = new Factory<>(TaxOffice::new, TaxOffice::fromNbt, null);

    public static final int STATE_IDLE = 0, STATE_DUE = 1, STATE_DUEL = 2;
    /** Fallback (no real trainer battle): wild battles in a row the player must win. */
    public static final int FALLBACK_WINS = 3;

    /** One taxpayer. */
    public static final class Entry {
        public long nextDue = -1, revenueMark, dueSince = -1;
        public int state;
        /** Frozen bill (when it fell due). */
        public long amount, property, income, earned;
        public double rate;
        public int tier;
        @Nullable public UUID mayor;
        /** Fallback chain: battles won so far. */
        public int chainWins;
        public int paid, won, lost;
    }

    /** The parts of a bill. */
    public record Bill(long property, long income, long earned, double rate, long total, int tierRank) {}

    private final Map<UUID, Entry> players = new HashMap<>();

    public static TaxOffice get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE, "tcgshop_taxes");
    }

    public static void register() {
        NpcConfig.load();
        ServerEvents.TICK_END.register(TaxOffice::tick);
        PlayerEvents.JOIN.register(p -> {
            if (p.getServer() != null) check(p.getServer(), p);
        });
    }

    /** Entry of that player (null if never registered). */
    @Nullable
    public Entry entry(UUID player) {
        return players.get(player);
    }

    public Entry entryOrNew(UUID player) {
        return players.computeIfAbsent(player, u -> new Entry());
    }

    /** Overworld day number (dayTime / 24000: sleeping skips days like everywhere else in the mod). */
    public static long today(MinecraftServer server) {
        return server.overworld().getDayTime() / 24000L;
    }

    // ------------------------------------------------------------------ the bill

    /** Rate on the income for a tier rank (pure). */
    public static double incomeRate(int tierRank) {
        NpcConfig c = NpcConfig.INSTANCE;
        ShopTier min = ShopTier.byKey(c.taxMinTier);
        int steps = Math.max(0, tierRank - (min == null ? ShopTier.IRON.rank() : min.rank()));
        return Math.min(c.taxIncomeRateMax, c.taxIncomeRate + c.taxIncomeRatePerTier * steps);
    }

    /** Bill from raw numbers (pure; the selftest checks it): shop core prices, revenue earned since the last bill, best tier. */
    public static Bill bill(List<Long> corePrices, long earned, int bestRank) {
        NpcConfig c = NpcConfig.INSTANCE;
        long property = 0;
        for (long p : corePrices) property += Math.round(Math.max(0, p) * c.taxPropertyRate);
        double rate = incomeRate(bestRank);
        long income = Math.round(Math.max(0, earned) * rate);
        long total = Math.max(c.taxMinimum, Math.min(c.taxMaximum, property + income));
        return new Bill(property, income, Math.max(0, earned), rate, total, bestRank);
    }

    /** The player's current bill (all their shops, any dimension, dormant ones included). */
    public static Bill billFor(MinecraftServer server, UUID player, Entry e) {
        List<Long> cores = new ArrayList<>();
        int best = -1;
        for (ShopZones.Zone z : ShopZones.ownedBy(server, player)) {
            cores.add(z.tierInfo().price);
            best = Math.max(best, z.tierInfo().rank());
        }
        return bill(cores, totalRevenue(server, player) - e.revenueMark, best);
    }

    static long totalRevenue(MinecraftServer server, UUID player) {
        long t = 0;
        for (ShopZones.Zone z : ShopZones.ownedBy(server, player)) t += Math.max(0, z.revenue);
        return t;
    }

    /** Does this player have a shop that pays taxes? */
    public static boolean taxed(MinecraftServer server, UUID player) {
        ShopTier min = ShopTier.byKey(NpcConfig.INSTANCE.taxMinTier);
        ShopTier best = ShopZones.highestOwned(server, player);
        return best != null && best.atLeast(min == null ? ShopTier.IRON : min);
    }

    // ------------------------------------------------------------------ schedule

    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % 100 != 51) return;
        for (ServerPlayer p : List.copyOf(server.getPlayerList().getPlayers())) {
            try {
                check(server, p);
            } catch (Throwable t) {
                TcgShop.LOGGER.warn("[mayor] tax check failed for {}: {}", p.getGameProfile().getName(), t.toString());
            }
        }
    }

    /** Registers / advances the player's taxes and makes sure a Mayor is on the way while a bill is open. */
    static void check(MinecraftServer server, ServerPlayer p) {
        NpcConfig c = NpcConfig.INSTANCE;
        if (!c.taxEnabled) return;
        TaxOffice office = get(server);
        Entry e = office.players.get(p.getUUID());
        long today = today(server);
        if (!taxed(server, p.getUUID())) {
            if (e != null && e.state == STATE_DUE) { // no taxed shop anymore: the visit is called off, the schedule kept
                e.state = STATE_IDLE;
                e.nextDue = today + c.taxIntervalDays;
                dismiss(server, e, null);
                office.setDirty();
            }
            return;
        }
        if (e == null || e.nextDue < 0) {
            e = office.entryOrNew(p.getUUID());
            e.nextDue = today + c.taxIntervalDays;
            e.revenueMark = totalRevenue(server, p.getUUID());
            e.state = STATE_IDLE;
            office.setDirty();
            PhoneMessages.send(server, p.getUUID(), SENDER, Component.translatable("tcgshop.mayor.msg.registered", c.taxIntervalDays), true);
            return;
        }
        if (e.state == STATE_IDLE && e.nextDue - today > 2L * c.taxIntervalDays) { // the clock went back (/time set)
            e.nextDue = today + c.taxIntervalDays;
            office.setDirty();
        }
        long revenueNow = totalRevenue(server, p.getUUID());
        if (e.revenueMark > revenueNow) { // a shop was abandoned / sold: its lifetime revenue no longer counts, neither does the mark
            e.revenueMark = revenueNow;
            office.setDirty();
        }
        switch (e.state) {
            case STATE_IDLE -> {
                if (today >= e.nextDue) becomeDue(server, p, e, today);
            }
            case STATE_DUE -> {
                if (today >= e.dueSince + c.taxGraceDays) forceCollect(server, p, e);
                else ensureMayor(server, p, e, today, false);
            }
            case STATE_DUEL -> {
                if (!MayorBattle.active(p.getUUID()) && !fallbackActive(server, e)) // the battle is gone (restart, crash): walking away
                    lose(server, p, e, "gone");
            }
            default -> e.state = STATE_IDLE;
        }
    }

    static final String SENDER = "tcgshop.msg.sender.mayor";

    private static void becomeDue(MinecraftServer server, ServerPlayer p, Entry e, long today) {
        Bill b = billFor(server, p.getUUID(), e);
        e.state = STATE_DUE;
        e.dueSince = today;
        e.amount = b.total();
        e.property = b.property();
        e.income = b.income();
        e.earned = b.earned();
        e.rate = b.rate();
        e.tier = b.tierRank();
        e.chainWins = 0;
        get(server).setDirty();
        Component amt = money(e.amount);
        p.displayClientMessage(Component.translatable("tcgshop.mayor.due", amt).withStyle(ChatFormatting.GOLD), false);
        PhoneMessages.send(server, p.getUUID(), SENDER, Component.translatable("tcgshop.mayor.msg.due", amt, NpcConfig.INSTANCE.taxGraceDays), true);
        TcgShop.LOGGER.info("[mayor] tax due for {}: {} (property {} + income {} = {}% of {})", p.getGameProfile().getName(),
                e.amount, e.property, e.income, Math.round(e.rate * 1000) / 10.0, e.earned);
        ensureMayor(server, p, e, today, false);
    }

    @Nullable
    static MayorEntity findMayor(MinecraftServer server, @Nullable UUID id) {
        if (id == null) return null;
        for (ServerLevel w : server.getAllLevels()) {
            Entity en = w.getEntity(id);
            if (en instanceof MayorEntity m && m.isAlive()) return m;
        }
        return null;
    }

    /** A Mayor near the taxpayer while the bill is open: at their nearest shop, or (from the day after) wherever they are. */
    private static void ensureMayor(MinecraftServer server, ServerPlayer p, Entry e, long today, boolean anywhere) {
        MayorEntity m = findMayor(server, e.mayor);
        if (m != null && !m.isLeaving() && m.level() == p.level() && m.distanceToSqr(p) < 128 * 128) return;
        if (m != null && !m.isLeaving()) m.discard();
        if (p.isSpectator() || p.isSleeping()) return;
        ServerLevel w = p.serverLevel();
        boolean nearShop = false;
        for (ShopZones.Zone z : ShopZones.get(w).all())
            if (z.owner.equals(p.getUUID()) && z.core.getCenter().distanceToSqr(p.position()) < 96 * 96) { nearShop = true; break; }
        if (!nearShop && !anywhere && today < e.dueSince + 1) return; // first day: he waits for you at your shop
        BlockPos pos = ground(w, p.position(), 6, 12);
        if (pos == null) return;
        MayorEntity nm = TcgEntities.MAYOR.create(w);
        if (nm == null) return;
        nm.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, w.random.nextFloat() * 360f, 0f);
        nm.setTaxpayer(p.getUUID());
        w.addFreshEntity(nm);
        e.mayor = nm.getUUID();
        get(server).setDirty();
        w.sendParticles(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 10, 0.4, 0.6, 0.4, 0.02);
        w.playSound(null, pos, SoundEvents.BELL_BLOCK, SoundSource.NEUTRAL, 0.6f, 1.2f);
        p.displayClientMessage(Component.translatable("tcgshop.mayor.arrived", money(e.amount)).withStyle(ChatFormatting.GOLD), false);
        nm.say("greet");
    }

    /** Is this Mayor the one the TaxOffice is expecting for that player (bill open)? */
    public static boolean expects(MinecraftServer server, UUID player, UUID mayor) {
        Entry e = get(server).players.get(player);
        return e != null && e.state != STATE_IDLE && mayor.equals(e.mayor);
    }

    private static void dismiss(MinecraftServer server, Entry e, @Nullable String line) {
        MayorEntity m = findMayor(server, e.mayor);
        if (m != null) m.leave(line);
        e.mayor = null;
    }

    // ------------------------------------------------------------------ the window

    /** Right-click on the Mayor: the bill window (UiPayloads.Open "mayor"). */
    public static void open(ServerPlayer p, MayorEntity m) {
        MinecraftServer server = p.getServer();
        if (server == null) return;
        Entry e = get(server).players.get(p.getUUID());
        if (e == null || e.state == STATE_IDLE || !m.getUUID().equals(e.mayor)) {
            m.leave(null);
            return;
        }
        if (e.state == STATE_DUEL) {
            p.displayClientMessage(Component.translatable("tcgshop.mayor.in_battle").withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        CompoundTag d = new CompoundTag();
        d.putInt("id", m.getId());
        d.putString("name", m.getDisplayName().getString());
        d.putLong("amount", e.amount);
        d.putLong("property", e.property);
        d.putLong("income", e.income);
        d.putLong("earned", e.earned);
        d.putDouble("rate", e.rate);
        ShopTier minTier = ShopTier.byKey(NpcConfig.INSTANCE.taxMinTier);
        d.putString("tier", (minTier == null ? ShopTier.IRON : minTier).key); // the rule: this tier or better pays taxes
        d.putLong("wallet", CobbleDollarsBridge.isAvailable() ? CobbleDollarsBridge.getBalance(p) : 0);
        d.putLong("registers", registers(server, p.getUUID()));
        d.putBoolean("cobblemon", CobblemonBattles.available());
        d.putInt("interval", NpcConfig.INSTANCE.taxIntervalDays);
        d.putLong("daysLeft", Math.max(0, e.dueSince + NpcConfig.INSTANCE.taxGraceDays - today(server)));
        d.putLong("binder", binderCards(p));
        var payload = new dev.tcgshop.network.UiPayloads.Open("mayor", d);
        if (Net.INSTANCE.canSend(p, payload.type())) Net.INSTANCE.send(p, payload);
    }

    /** UiPayloads.Act "mayor": action = pay | refuse. */
    public static void answer(ServerPlayer p, CompoundTag d) {
        MinecraftServer server = p.getServer();
        if (server == null) return;
        if (!(p.level().getEntity(d.getInt("id")) instanceof MayorEntity m) || m.distanceToSqr(p) > 12 * 12) return;
        Entry e = get(server).players.get(p.getUUID());
        if (e == null || e.state != STATE_DUE || !m.getUUID().equals(e.mayor)) return;
        switch (d.getString("action")) {
            case "pay" -> pay(server, p, e, m);
            case "refuse" -> refuse(server, p, e, m);
            default -> {}
        }
    }

    // ------------------------------------------------------------------ outcomes

    private static void pay(MinecraftServer server, ServerPlayer p, Entry e, MayorEntity m) {
        long have = (CobbleDollarsBridge.isAvailable() ? Math.max(0, CobbleDollarsBridge.getBalance(p)) : 0) + registers(server, p.getUUID());
        if (have < e.amount) {
            p.displayClientMessage(Component.translatable("tcgshop.mayor.cant_afford", money(e.amount), money(have)).withStyle(ChatFormatting.RED), false);
            return;
        }
        if (!charge(server, p, e.amount)) {
            p.displayClientMessage(Component.translatable("tcgshop.mayor.cant_afford", money(e.amount), money(have)).withStyle(ChatFormatting.RED), false);
            return;
        }
        e.paid++;
        p.displayClientMessage(Component.translatable("tcgshop.mayor.paid", money(e.amount), NpcConfig.INSTANCE.taxIntervalDays).withStyle(ChatFormatting.GREEN), false);
        p.level().playSound(null, p.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.8f, 0.9f);
        PhoneMessages.send(server, p.getUUID(), SENDER, Component.translatable("tcgshop.mayor.msg.paid", money(e.amount)), false);
        settle(server, p, e, "paid");
    }

    private static void refuse(MinecraftServer server, ServerPlayer p, Entry e, MayorEntity m) {
        if (!CobblemonBattles.available()) { // no Cobblemon: he takes a card straight away
            lose(server, p, e, "refused");
            return;
        }
        String err = MayorBattle.start(p, m, m.getDisplayName().getString(), (srv, player, r, reason) -> battleEnded(srv, player, r == MayorBattle.Result.WIN, reason));
        if (err == null) {
            e.state = STATE_DUEL;
            get(server).setDirty();
            m.setDueling(true);
            m.say("duel");
            p.displayClientMessage(Component.translatable("tcgshop.mayor.battle_start").withStyle(ChatFormatting.GOLD), false);
            p.displayClientMessage(Component.translatable("tcgshop.mayor.battle_rules", NpcConfig.INSTANCE.mayorWalkAwayBlocks).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), false);
            return;
        }
        if (err.equals(MayorBattle.ERR_NO_POKEMON)) { // nobody able to fight: a forfeit
            p.displayClientMessage(Component.translatable("tcgshop.mayor.no_pokemon").withStyle(ChatFormatting.RED), false);
            lose(server, p, e, "forfeit");
            return;
        }
        if (err.equals(MayorBattle.ERR_BUSY)) {
            p.displayClientMessage(Component.translatable("tcgshop.mayor.busy").withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        // Cobblemon refused the trainer battle: fall back to a chain of strong battles
        e.chainWins = 0;
        if (CobblemonBattles.challenge(p, m)) {
            e.state = STATE_DUEL;
            get(server).setDirty();
            m.say("duel");
            p.displayClientMessage(Component.translatable("tcgshop.mayor.chain_start", FALLBACK_WINS).withStyle(ChatFormatting.GOLD), false);
        } else { // no battle of any kind could be set up: refusing still has its price (a card)
            p.displayClientMessage(Component.translatable("tcgshop.mayor.cant_battle").withStyle(ChatFormatting.RED), false);
            lose(server, p, e, "refused");
        }
    }

    private static boolean fallbackActive(MinecraftServer server, Entry e) {
        MayorEntity m = findMayor(server, e.mayor);
        return m != null && CobblemonBattles.isFighting(m);
    }

    /** MayorEntity.onBattleEnd: one battle of the fallback chain is over. */
    public static void onFallbackBattle(ServerPlayer p, MayorEntity m, boolean won) {
        MinecraftServer server = p.getServer();
        if (server == null) return;
        Entry e = get(server).players.get(p.getUUID());
        if (e == null || e.state != STATE_DUEL || !m.getUUID().equals(e.mayor)) return;
        if (!won) {
            battleEnded(server, p.getUUID(), false, "lost");
            return;
        }
        e.chainWins++;
        get(server).setDirty();
        if (e.chainWins >= FALLBACK_WINS) {
            battleEnded(server, p.getUUID(), true, "won");
            return;
        }
        p.displayClientMessage(Component.translatable("tcgshop.mayor.chain_next", e.chainWins, FALLBACK_WINS).withStyle(ChatFormatting.GOLD), false);
        if (!CobblemonBattles.challenge(p, m)) battleEnded(server, p.getUUID(), false, "walked");
    }

    /** End of the Mayor's battle (real or fallback). */
    static void battleEnded(MinecraftServer server, UUID player, boolean won, String reason) {
        Entry e = get(server).players.get(player);
        if (e == null || e.state != STATE_DUEL) return;
        ServerPlayer p = server.getPlayerList().getPlayer(player);
        MayorEntity m = findMayor(server, e.mayor);
        if (m != null) m.setDueling(false);
        if (won) {
            e.won++;
            if (p != null) p.displayClientMessage(Component.translatable("tcgshop.mayor.won", NpcConfig.INSTANCE.taxIntervalDays).withStyle(ChatFormatting.GREEN), false);
            PhoneMessages.send(server, player, SENDER, Component.translatable("tcgshop.mayor.msg.won"), false);
            settleOffline(server, player, e, "won");
            return;
        }
        if (p == null) { // disconnected mid-battle: resolved when they come back (state DUEL without a battle = walked away)
            get(server).setDirty();
            return;
        }
        lose(server, p, e, reason);
    }

    /** Loss, forfeit, walking away, refusing without Cobblemon: one random binder card, always. */
    static void lose(MinecraftServer server, ServerPlayer p, Entry e, String reason) {
        e.lost++;
        Taken t = takeCard(server, p, p.getRandom());
        Component why = Component.translatable("tcgshop.mayor.reason." + reason);
        if (t != null) {
            String from = t.fromBinder ? "" : "_inv"; // the binders were empty: the card came out of the inventory
            p.displayClientMessage(Component.translatable("tcgshop.mayor.took" + from, why, t.name, money(t.value)).withStyle(ChatFormatting.RED), false);
            PhoneMessages.send(server, p.getUUID(), SENDER, Component.translatable("tcgshop.mayor.msg.took" + from, t.name, money(t.value)), false);
        } else { // no card anywhere: the tax itself, as far as the money goes
            long got = chargeUpTo(server, p, e.amount);
            p.displayClientMessage(Component.translatable("tcgshop.mayor.took_money", why, money(got)).withStyle(ChatFormatting.RED), false);
        }
        p.level().playSound(null, p.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 0.8f, 0.8f);
        settle(server, p, e, "took");
    }

    /** Ignored for taxGraceDays: amount + late fee by force, or a card if the money is not there. */
    private static void forceCollect(MinecraftServer server, ServerPlayer p, Entry e) {
        long due = Math.round(e.amount * (1 + NpcConfig.INSTANCE.taxLateFee));
        long have = (CobbleDollarsBridge.isAvailable() ? Math.max(0, CobbleDollarsBridge.getBalance(p)) : 0) + registers(server, p.getUUID());
        if (have >= due && charge(server, p, due)) {
            p.displayClientMessage(Component.translatable("tcgshop.mayor.forced", money(due)).withStyle(ChatFormatting.RED), false);
            PhoneMessages.send(server, p.getUUID(), SENDER, Component.translatable("tcgshop.mayor.msg.forced", money(due)), false);
            settle(server, p, e, "took");
            return;
        }
        lose(server, p, e, "ignored");
    }

    /** Bill settled (any way): the next one in taxIntervalDays, the revenue mark moves up, the Mayor leaves. */
    private static void settle(MinecraftServer server, ServerPlayer p, Entry e, String line) {
        settleOffline(server, p.getUUID(), e, line);
        ShopServer.syncZones(p);
    }

    private static void settleOffline(MinecraftServer server, UUID player, Entry e, String line) {
        e.state = STATE_IDLE;
        e.nextDue = today(server) + NpcConfig.INSTANCE.taxIntervalDays;
        e.revenueMark = totalRevenue(server, player);
        e.dueSince = -1;
        e.chainWins = 0;
        dismiss(server, e, line);
        get(server).setDirty();
    }

    // ------------------------------------------------------------------ money

    /** Money in the registers (zone balance) of that player's shops, any dimension, dormant ones included. */
    static long registers(MinecraftServer server, UUID owner) {
        long t = 0;
        for (ServerLevel sw : server.getAllLevels())
            for (ShopZones.Zone z : ShopZones.get(sw).allAndDormant()) if (z.owner.equals(owner)) t += Math.max(0, z.balance);
        return t;
    }

    /**
     * Takes exactly `amount` (wallet first, then registers). False (nothing taken) if it is not all there: the wallet
     * is charged first and the registers only once that charge went through, so a refused wallet charge never
     * leaves the registers half-drained.
     */
    static boolean charge(MinecraftServer server, ServerPlayer p, long amount) {
        if (amount <= 0) return true;
        long wallet = CobbleDollarsBridge.isAvailable() ? Math.max(0, CobbleDollarsBridge.getBalance(p)) : 0;
        long fromWallet = Math.min(amount, wallet), fromRegisters = amount - fromWallet;
        if (registers(server, p.getUUID()) < fromRegisters) return false;
        if (fromWallet > 0 && !CobbleDollarsBridge.tryCharge(p, fromWallet)) return false;
        long got = takeFromRegisters(server, p.getUUID(), fromRegisters);
        if (got < fromRegisters) { // the registers changed in between (should not happen on the server thread): undo it all
            refundRegisters(server, p.getUUID(), got);
            if (fromWallet > 0) CobbleDollarsBridge.add(p, fromWallet);
            return false;
        }
        return true;
    }

    /** Takes up to `amount` from that owner's registers. Returns what was taken. */
    private static long takeFromRegisters(MinecraftServer server, UUID owner, long amount) {
        long got = 0;
        for (ServerLevel sw : server.getAllLevels()) {
            if (got >= amount) break;
            ShopZones zs = ShopZones.get(sw);
            boolean dirty = false;
            for (ShopZones.Zone z : zs.allAndDormant()) {
                if (got >= amount) break;
                if (!z.owner.equals(owner) || z.balance <= 0) continue;
                long take = Math.min(z.balance, amount - got);
                z.balance -= take;
                got += take;
                dirty = true;
            }
            if (dirty) zs.setDirty();
        }
        return got;
    }

    /** Puts money back into the first register of that owner (undo of a failed charge). */
    private static void refundRegisters(MinecraftServer server, UUID owner, long amount) {
        if (amount <= 0) return;
        for (ServerLevel sw : server.getAllLevels()) {
            ShopZones zs = ShopZones.get(sw);
            for (ShopZones.Zone z : zs.allAndDormant()) {
                if (!z.owner.equals(owner)) continue;
                z.balance += amount;
                zs.setDirty();
                return;
            }
        }
    }

    /** Takes up to `amount` (wallet first, then registers). Returns what was taken. */
    static long chargeUpTo(MinecraftServer server, ServerPlayer p, long amount) {
        long got = 0;
        if (amount <= 0) return 0;
        if (CobbleDollarsBridge.isAvailable()) {
            long take = Math.min(amount, Math.max(0, CobbleDollarsBridge.getBalance(p)));
            if (take > 0 && CobbleDollarsBridge.tryCharge(p, take)) got = take;
        }
        return got + takeFromRegisters(server, p.getUUID(), amount - got);
    }

    // ------------------------------------------------------------------ the card

    /** A card the Mayor took (fromBinder false: the binders were empty, it came out of the inventory). */
    public record Taken(Component name, long value, String id, boolean fromBinder) {
        public Taken(Component name, long value, String id) { this(name, value, id, true); }
    }

    /** Cards in all the player's binders (the carried one and every binder they own, wherever it is). */
    static long binderCards(ServerPlayer p) {
        long n = 0;
        for (BinderData b : binders(p)) n += b.totalCards();
        return n;
    }

    /**
     * Every binder the player owns (the carried one included, once). All of them, not just the carried one: carrying a
     * binder with one cheap card must not shield the real collection kept at home.
     */
    private static List<BinderData> binders(ServerPlayer p) {
        List<BinderData> out = new ArrayList<>();
        BinderData carried = CollectionService.ownedBinder(p);
        if (carried != null) out.add(carried);
        MinecraftServer server = p.getServer();
        if (server != null) for (BinderData b : BinderRegistry.get(server).ownedBy(p.getUUID())) if (b != carried) out.add(b);
        return out;
    }

    /** Which of these binders a random copy falls in (weighted by copies; pure, the selftest uses it). -1 if all are empty. */
    public static int pickBinder(List<BinderData> binders, RandomSource r) {
        long total = 0;
        for (BinderData b : binders) total += Math.max(0, b.totalCards());
        if (total <= 0) return -1;
        long pick = (long) (r.nextDouble() * total);
        for (int i = 0; i < binders.size(); i++) {
            long n = Math.max(0, binders.get(i).totalCards());
            if (pick < n) return i;
            pick -= n;
        }
        return binders.size() - 1;
    }

    /**
     * Takes one random card from the player's whole binder collection (every owned binder; each copy equally likely,
     * special prints and graded slabs included). No card in any binder: one random card from the inventory. Nothing
     * at all: null.
     */
    @Nullable
    public static Taken takeCard(MinecraftServer server, ServerPlayer p, RandomSource r) {
        List<BinderData> all = binders(p);
        int bi = pickBinder(all, r);
        Taken t = bi < 0 ? null : takeFrom(all.get(bi), r);
        if (t == null) for (BinderData b : all) if ((t = takeFrom(b, r)) != null) break; // a stale count: any card
        if (t != null) {
            BinderRegistry.get(server).setDirty();
            CollectionService.sync(p);
            return t;
        }
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < 36; i++) if (CardInfo.isCard(p.getInventory().getItem(i))) slots.add(i);
        if (slots.isEmpty()) return null;
        int slot = slots.get(r.nextInt(slots.size()));
        ItemStack st = p.getInventory().getItem(slot).split(1);
        p.getInventory().setChanged();
        return new Taken(st.getHoverName(), CardSales.unitValue(st), CardInfo.id(st).toString(), false);
    }

    /** One random copy out of that binder (pure on the binder data; the selftest uses it). */
    @Nullable
    public static Taken takeFrom(BinderData b, RandomSource r) {
        long total = b.totalCards();
        if (total <= 0) return null;
        long pick = (long) (r.nextDouble() * total);
        for (String id : BinderSlots.ids(b.counts)) {
            int n = b.count(id);
            if (pick < n) {
                ItemStack st = BinderSlots.stackFor(id, 1);
                if (b.remove(id, 1) <= 0) return null;
                return new Taken(st.isEmpty() ? Component.literal(id) : st.getHoverName(), st.isEmpty() ? 0 : CardSales.unitValue(st), id);
            }
            pick -= n;
        }
        for (BinderData.Special s : new ArrayList<>(b.specials)) {
            if (pick < s.count) {
                ItemStack st = b.removeSpecial(s, 1);
                if (st.isEmpty()) return null;
                return new Taken(st.getHoverName(), CardSales.unitValue(st), CardInfo.id(st).toString());
            }
            pick -= s.count;
        }
        return null;
    }

    // ------------------------------------------------------------------ helpers

    static Component money(long v) {
        return Component.literal(PriceService.format(v)).withStyle(ChatFormatting.GOLD);
    }

    @Nullable
    static BlockPos ground(ServerLevel world, Vec3 c, int minD, int maxD) {
        for (int attempt = 0; attempt < 16; attempt++) {
            double ang = world.random.nextDouble() * Math.PI * 2;
            int dist = minD + world.random.nextInt(Math.max(1, maxD - minD));
            int x = (int) Math.floor(c.x + Math.cos(ang) * dist), z = (int) Math.floor(c.z + Math.sin(ang) * dist);
            if (!world.hasChunkAt(new BlockPos(x, 0, z))) continue;
            int y = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos pos = new BlockPos(x, y, z);
            if (Math.abs(y - c.y) > 12) continue;
            if (ShopZones.isShopRoof(world, pos)) continue;
            if (world.getBlockState(pos.below()).isRedstoneConductor(world, pos.below()) && world.isEmptyBlock(pos) && world.isEmptyBlock(pos.above())) return pos;
        }
        // indoors / caves: right next to the player if there is room
        BlockPos at = BlockPos.containing(c);
        for (BlockPos q : BlockPos.betweenClosed(at.offset(-3, -1, -3), at.offset(3, 1, 3))) {
            if (q.distSqr(at) < 4) continue;
            if (world.getBlockState(q.below()).isRedstoneConductor(world, q.below()) && world.isEmptyBlock(q) && world.isEmptyBlock(q.above())) return q.immutable();
        }
        return null;
    }

    /** /tcgshop debug mayor: makes that player's bill fall due now (registering them if needed) and sends the Mayor to them. */
    public static void debugDue(ServerPlayer p) {
        MinecraftServer server = p.getServer();
        if (server == null) return;
        TaxOffice office = get(server);
        Entry e = office.entryOrNew(p.getUUID());
        long today = today(server);
        if (e.nextDue < 0) e.revenueMark = Math.max(0, totalRevenue(server, p.getUUID()) - 100_000); // as if $100,000 was earned
        if (e.state == STATE_IDLE) {
            e.nextDue = today;
            becomeDue(server, p, e, today);
        }
        ensureMayor(server, p, e, today, true);
        office.setDirty();
    }

    // ------------------------------------------------------------------ NBT

    /** Forgets a player (selftest cleanup). */
    void remove(UUID player) {
        if (players.remove(player) != null) setDirty();
    }

    static TaxOffice fromNbt(CompoundTag nbt, HolderLookup.Provider lookup) {
        TaxOffice s = new TaxOffice();
        CompoundTag o = nbt.getCompound("players");
        for (String k : o.getAllKeys()) {
            try {
                CompoundTag c = o.getCompound(k);
                Entry e = new Entry();
                e.nextDue = c.contains("next_due") ? c.getLong("next_due") : -1;
                e.revenueMark = c.getLong("revenue_mark");
                e.dueSince = c.contains("due_since") ? c.getLong("due_since") : -1;
                e.state = c.getInt("state");
                e.amount = c.getLong("amount");
                e.property = c.getLong("property");
                e.income = c.getLong("income");
                e.earned = c.getLong("earned");
                e.rate = c.getDouble("rate");
                e.tier = c.getInt("tier");
                e.mayor = c.hasUUID("mayor") ? c.getUUID("mayor") : null;
                e.chainWins = c.getInt("chain_wins");
                e.paid = c.getInt("paid");
                e.won = c.getInt("won");
                e.lost = c.getInt("lost");
                s.players.put(UUID.fromString(k), e);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return s;
    }

    @Override
    public CompoundTag save(CompoundTag nbt, HolderLookup.Provider lookup) {
        CompoundTag o = new CompoundTag();
        players.forEach((id, e) -> {
            CompoundTag c = new CompoundTag();
            c.putLong("next_due", e.nextDue);
            c.putLong("revenue_mark", e.revenueMark);
            c.putLong("due_since", e.dueSince);
            c.putInt("state", e.state);
            c.putLong("amount", e.amount);
            c.putLong("property", e.property);
            c.putLong("income", e.income);
            c.putLong("earned", e.earned);
            c.putDouble("rate", e.rate);
            c.putInt("tier", e.tier);
            if (e.mayor != null) c.putUUID("mayor", e.mayor);
            c.putInt("chain_wins", e.chainWins);
            c.putInt("paid", e.paid);
            c.putInt("won", e.won);
            c.putInt("lost", e.lost);
            o.put(id.toString(), c);
        });
        nbt.put("players", o);
        return nbt;
    }
}

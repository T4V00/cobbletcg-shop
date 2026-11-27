package dev.tcgshop.phone;

import dev.masterball.api.platform.Net;
import dev.tcgshop.TcgShop;
import dev.tcgshop.economy.CobbleDollarsBridge;
import dev.tcgshop.pricing.PriceService;
import dev.tcgshop.shop.ShopZones;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 1.0 Wholesaler loans (stored in the Overworld: data/tcgshop_loans.dat).
 * <p>
 * <b>Offers.</b> From phone tier {@link PhoneConfig#loanMinTier} (Pro) the Wholesaler now and then sends a gold-bordered
 * message offering a loan (accepted / declined from the message, valid {@link PhoneConfig#loanOfferDays} days). The
 * higher the phone tier, the more often (loanOfferGapDays) and the bigger (loanAmountByTier, hard cap loanMaxAmount =
 * 50,000). Offers only arrive while the player is online, carries the phone, has no loan and no pending offer.
 * <p>
 * <b>Credit score</b> (0-100, starts at 50) is the player's credit history:
 * <ul>
 *   <li>amount: x0.5 (score 0) .. x1.5 (score 100) of the tier's amount;</li>
 *   <li>interest: loanRateMax (score 0, 20%) .. loanRateMin (score 100, 10%);</li>
 *   <li>frequency: score >= 70 offers come 30% sooner, score < 35 60% later; term: +2 days at >= 75, -1 below 30.</li>
 * </ul>
 * Repaying on time: +6 (+4 more if repaid within the first half of the term). Repaying late: -(4 + 3 per day late), at
 * most -25. Every loan repaid is kept in the history (last {@link #HISTORY} loans).
 * <p>
 * <b>While a loan is active</b> nothing can be bought from the phone (Market, Furniture app, upgrades): the server rejects
 * it with a notice ({@link #blocksPhonePurchase}). It is repaid from the phone (Stats app → Loans), in full or partly.
 * <p>
 * <b>Late loans.</b> Reminders by message the day before the due date, on the due date and every 2 days while late. Each
 * day late adds a fee of loanLateFeePerDay (2%) of what is still owed (the debt never grows past twice the loan). After
 * loanVeryLateDays (7) days late the loan is <i>very late</i>, once: credit -15, every shop of the borrower loses
 * loanRepPenalty (3) reputation, and no new offers until it is repaid (plus 5 days).
 * <p>
 * <b>Collection.</b> From loanCollectAfterDays (7) days late the Wholesaler collects the debt by force, one installment
 * a day (loanCollectPerDay = 25% of what was owed when collection started): first from the borrower's shop registers,
 * then from their CobbleDollars (only while online). Every collection is a (late) repayment and is reported by a gold
 * message; when the debt reaches 0 the loan closes as a late one in the credit history.
 * <p>
 * While a loan is active, hiring staff from the phone and buying other players' card offers (/tcgshop offer) are
 * blocked too ({@link #blocksPurchase}).
 */
public final class Loans extends SavedData {
    /** Loans remembered in the credit history. */
    public static final int HISTORY = 10;
    public static final int SCORE_START = 50;
    /** Days without offers after repaying a very late loan. */
    private static final int BLOCK_AFTER_VERY_LATE = 5;

    /** An active loan. */
    public static final class Loan {
        public long principal, owed, paid;
        public double rate;
        /** In-game days: taken, due, last reminder, last day a late fee was added. */
        public long taken, due, lastRemind = -1, lastFeeDay;
        public boolean veryLate;
        /** What was owed when the forced collection started (0 = not collecting yet): sets the daily installment. */
        public long collectBase;

        CompoundTag write() {
            CompoundTag c = new CompoundTag();
            c.putLong("p", principal);
            c.putLong("o", owed);
            c.putLong("pd", paid);
            c.putDouble("r", rate);
            c.putLong("tk", taken);
            c.putLong("du", due);
            c.putLong("lr", lastRemind);
            c.putLong("lf", lastFeeDay);
            c.putBoolean("vl", veryLate);
            if (collectBase > 0) c.putLong("cb", collectBase);
            return c;
        }

        static Loan read(CompoundTag c) {
            Loan l = new Loan();
            l.principal = c.getLong("p");
            l.owed = Math.max(0, c.getLong("o"));
            l.paid = c.getLong("pd");
            l.rate = c.getDouble("r");
            l.taken = c.getLong("tk");
            l.due = c.getLong("du");
            l.lastRemind = c.contains("lr") ? c.getLong("lr") : -1;
            l.lastFeeDay = c.contains("lf") ? c.getLong("lf") : l.due;
            l.veryLate = c.getBoolean("vl");
            l.collectBase = Math.max(0, c.getLong("cb"));
            return l;
        }
    }

    /** A repaid loan in the credit history. lateDays = 0: on time. */
    public record Hist(long amount, long days, long term, long lateDays) {}

    /** Credit record of one player. */
    public static final class Credit {
        public int score = SCORE_START, repaid, onTime, late;
        /** In-game day from which the next offer may come (-1 = not scheduled yet). */
        public long nextOffer = -1;
        /** No offers before this in-game day (-1 = none; Long.MAX_VALUE = until the very late loan is repaid). */
        public long blockedUntil = -1;
        /** Message id of the pending offer (-1 = none). */
        public long offerMsg = -1;
        public @Nullable Loan loan;
        public final List<Hist> hist = new ArrayList<>();

        CompoundTag write() {
            CompoundTag c = new CompoundTag();
            c.putInt("score", score);
            c.putInt("repaid", repaid);
            c.putInt("onTime", onTime);
            c.putInt("late", late);
            c.putLong("next", nextOffer);
            c.putLong("blocked", blockedUntil);
            c.putLong("offer", offerMsg);
            if (loan != null) c.put("loan", loan.write());
            ListTag h = new ListTag();
            for (Hist e : hist) {
                CompoundTag x = new CompoundTag();
                x.putLong("a", e.amount);
                x.putLong("d", e.days);
                x.putLong("t", e.term);
                x.putLong("l", e.lateDays);
                h.add(x);
            }
            c.put("hist", h);
            return c;
        }

        static Credit read(CompoundTag c) {
            Credit r = new Credit();
            r.score = c.contains("score") ? clampScore(c.getInt("score")) : SCORE_START;
            r.repaid = c.getInt("repaid");
            r.onTime = c.getInt("onTime");
            r.late = c.getInt("late");
            r.nextOffer = c.contains("next") ? c.getLong("next") : -1;
            r.blockedUntil = c.contains("blocked") ? c.getLong("blocked") : -1;
            r.offerMsg = c.contains("offer") ? c.getLong("offer") : -1;
            if (c.contains("loan", Tag.TAG_COMPOUND)) r.loan = Loan.read(c.getCompound("loan"));
            ListTag h = c.getList("hist", Tag.TAG_COMPOUND);
            for (int i = 0; i < h.size() && r.hist.size() < HISTORY; i++) {
                CompoundTag x = h.getCompound(i);
                r.hist.add(new Hist(x.getLong("a"), x.getLong("d"), x.getLong("t"), x.getLong("l")));
            }
            return r;
        }
    }

    private static final Factory<Loans> TYPE = new Factory<>(Loans::new, Loans::fromNbt, null);

    private final Map<UUID, Credit> players = new HashMap<>();
    /** Last in-game day processed by the daily step (-1 = never). */
    private long lastDay = -1;

    public static Loans get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE, "tcgshop_loans");
    }

    private Credit credit(UUID id) {
        return players.computeIfAbsent(id, k -> {
            setDirty();
            return new Credit();
        });
    }

    /** That player's credit record, or null if they never had one (read only). */
    public static @Nullable Credit peek(MinecraftServer server, UUID player) {
        return server == null || player == null ? null : get(server).players.get(player);
    }

    private static int clampScore(int s) {
        return Math.max(0, Math.min(100, s));
    }

    // ------------------------------------------------------------------ public API (contract)

    /** Does that player owe the Wholesaler money right now? (works for offline players). */
    public static boolean hasActiveLoan(MinecraftServer server, UUID player) {
        Credit c = peek(server, player);
        return c != null && c.loan != null && c.loan.owed > 0;
    }

    /**
     * Phone purchases (Market, Furniture app, upgrades) are not allowed while a loan is active: if this player has one,
     * shows the reason inside the phone and returns true (the caller must then cancel the purchase).
     */
    public static boolean blocksPhonePurchase(ServerPlayer p) {
        return blocksPurchase(p, "tcgshop.loan.notice.blocked");
    }

    /**
     * Same check as {@link #blocksPhonePurchase} with a specific notice body (bodyKey gets one argument: what is owed).
     * Used for hiring staff from the phone and for buying other players' card offers (/tcgshop offer accept).
     */
    public static boolean blocksPurchase(ServerPlayer p, String bodyKey) {
        if (p == null || !hasActiveLoan(p.getServer(), p.getUUID())) return false;
        Loan l = get(p.getServer()).credit(p.getUUID()).loan;
        PhoneApps.notice(p, "error", "tcgshop.loan.notice.blocked.title", bodyKey,
                PriceService.format(l == null ? 0 : l.owed));
        p.level().playSound(null, p.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 0.6f, 1f);
        return true;
    }

    // ------------------------------------------------------------------ offers

    /** Server tick (every 20 s from PhoneApps): daily step for every loan and offers for online players. */
    static void tick(MinecraftServer server) {
        Loans st = get(server);
        long day = PhoneMessages.day(server);
        if (st.lastDay < 0 || day < st.lastDay) { // first run, or /time set backwards: just remember the day
            st.lastDay = day;
            st.setDirty();
        } else if (day > st.lastDay) {
            st.lastDay = day;
            st.setDirty();
            st.daily(server, day);
        }
        PhoneConfig c = PhoneConfig.INSTANCE;
        if (!c.loansEnabled || !CobbleDollarsBridge.isAvailable()) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) st.maybeOffer(p, day);
    }

    private void maybeOffer(ServerPlayer p, long day) {
        PhoneConfig c = PhoneConfig.INSTANCE;
        int tier = PhoneUpgrades.level(p);
        if (tier < Math.max(1, c.loanMinTier) || !PhoneUpgrades.hasPhone(p)) return;
        Credit cr = credit(p.getUUID());
        if (cr.loan != null || (cr.blockedUntil >= 0 && day < cr.blockedUntil)) return;
        if (pendingOffer(p.getServer(), p.getUUID(), cr, day) != null) return;
        RandomSource rnd = p.getServer().overworld().random;
        int gap = gapDays(tier, cr.score);
        if (cr.nextOffer < 0 || cr.nextOffer - day > gap * 2L) { // first time at the right tier (or /time set backwards): within one gap
            cr.nextOffer = day + 1 + rnd.nextInt(Math.max(1, gap));
            setDirty();
            return;
        }
        if (day < cr.nextOffer) return;
        long amount = offerAmount(tier, cr.score, rnd);
        cr.nextOffer = day + gap + rnd.nextInt(Math.max(1, gap / 2 + 1));
        setDirty();
        if (amount < 100) return;
        double rate = offerRate(cr.score);
        int term = offerTerm(tier, cr.score);
        long total = amount + interest(amount, rate);
        CompoundTag x = new CompoundTag();
        x.putLong("amount", amount);
        x.putDouble("rate", rate);
        x.putInt("term", term);
        x.putLong("exp", day + c.loanOfferDays);
        x.putString("st", "");
        PhoneMessages.Msg m = PhoneMessages.sendFull(p.getServer(), p.getUUID(), PhoneMessages.SENDER_WHOLESALER, "", null, "wholesaler",
                Component.translatable("tcgshop.loan.offer.body", PriceService.format(amount), pct(rate), term, PriceService.format(total), c.loanOfferDays),
                true, PhoneMessages.KIND_LOAN_OFFER, x);
        if (m != null) cr.offerMsg = m.id;
        setDirty();
        sync(p);
    }

    /** The pending (still valid) offer message, or null. An expired one is marked "expired" on the way. */
    private @Nullable PhoneMessages.Msg pendingOffer(MinecraftServer server, UUID player, Credit cr, long day) {
        if (cr.offerMsg < 0) return null;
        PhoneMessages.Msg m = PhoneMessages.find(server, player, cr.offerMsg);
        if (m == null || !PhoneMessages.KIND_LOAN_OFFER.equals(m.kind) || !m.extra.getString("st").isEmpty()) {
            cr.offerMsg = -1;
            setDirty();
            return null;
        }
        if (day > m.extra.getLong("exp")) {
            m.extra.putString("st", "expired");
            cr.offerMsg = -1;
            setDirty();
            PhoneMessages.changed(server, player);
            return null;
        }
        return m;
    }

    static int gapDays(int tier, int score) {
        int[] g = PhoneConfig.INSTANCE.loanOfferGapDays;
        int base = g[Math.max(0, Math.min(g.length - 1, tier))];
        double k = score >= 70 ? 0.7 : score < 35 ? 1.6 : 1.0;
        return Math.max(1, (int) Math.round(base * k));
    }

    static long offerAmount(int tier, int score, RandomSource rnd) {
        PhoneConfig c = PhoneConfig.INSTANCE;
        long base = c.loanAmountByTier[Math.max(0, Math.min(c.loanAmountByTier.length - 1, tier))];
        double v = base * (0.5 + clampScore(score) / 100.0) * (0.6 + 0.4 * rnd.nextDouble());
        long a = Math.round(v / 100.0) * 100L;
        return Math.max(0, Math.min(c.loanMaxAmount, a));
    }

    static double offerRate(int score) {
        PhoneConfig c = PhoneConfig.INSTANCE;
        double r = c.loanRateMin + (c.loanRateMax - c.loanRateMin) * (1 - clampScore(score) / 100.0);
        return Math.round(r * 200) / 200.0; // steps of 0.5%
    }

    static int offerTerm(int tier, int score) {
        int[] t = PhoneConfig.INSTANCE.loanTermDays;
        int base = t[Math.max(0, Math.min(t.length - 1, tier))];
        return Math.max(2, base + (score >= 75 ? 2 : score < 30 ? -1 : 0));
    }

    static long interest(long amount, double rate) {
        return Math.round(amount * rate);
    }

    static String pct(double rate) {
        double v = Math.round(rate * 1000) / 10.0; // one decimal, without floating point noise (15.000000000000002)
        return v == Math.floor(v) ? String.valueOf((long) v) : String.format(Locale.ROOT, "%.1f", v);
    }

    // ------------------------------------------------------------------ daily step: reminders, late fees, very late

    private void daily(MinecraftServer server, long day) {
        PhoneConfig c = PhoneConfig.INSTANCE;
        for (Map.Entry<UUID, Credit> e : players.entrySet()) {
            UUID id = e.getKey();
            Credit cr = e.getValue();
            Loan l = cr.loan;
            if (l == null) continue;
            boolean changed = false;
            // late fees: one per day past the due date (also for days the server skipped, at most 60)
            if (day > l.due && c.loanLateFeePerDay > 0) {
                long from = Math.max(l.lastFeeDay, l.due);
                long cap = Math.max(l.owed, l.principal * 2 - l.paid);
                for (long d = from + 1; d <= day && d - from <= 60; d++) {
                    long fee = Math.max(1, Math.round(l.owed * c.loanLateFeePerDay));
                    l.owed = Math.min(cap, l.owed + fee);
                }
                l.lastFeeDay = day;
                changed = true;
            }
            long late = day - l.due;
            // forced collection after the grace period (its daily message replaces the "late" reminder)
            boolean collecting = c.loanCollectPerDay > 0 && late >= c.loanCollectAfterDays;
            String remind = null;
            if (late == -1) remind = "tomorrow";
            else if (late == 0) remind = "today";
            else if (late > 0 && !collecting && (l.lastRemind < 0 || day - l.lastRemind >= 2)) remind = "late";
            if (remind != null && l.lastRemind != day) {
                l.lastRemind = day;
                changed = true;
                Component body = remind.equals("late")
                        ? Component.translatable("tcgshop.loan.remind.late", late, PriceService.format(l.owed), pct(c.loanLateFeePerDay))
                        : Component.translatable("tcgshop.loan.remind." + remind, PriceService.format(l.owed));
                PhoneMessages.sendFull(server, id, PhoneMessages.SENDER_WHOLESALER, "", null, "wholesaler", body, true, PhoneMessages.KIND_TEXT, null);
            }
            if (late >= c.loanVeryLateDays && !l.veryLate) {
                l.veryLate = true;
                cr.score = clampScore(cr.score - 15);
                cr.blockedUntil = Long.MAX_VALUE;
                changed = true;
                int pen = repPenalty(server, id, c.loanRepPenalty);
                PhoneMessages.sendFull(server, id, PhoneMessages.SENDER_WHOLESALER, "", null, "wholesaler",
                        Component.translatable("tcgshop.loan.very_late", late, PriceService.format(l.owed), pen), true, PhoneMessages.KIND_TEXT, null);
                TcgShop.LOGGER.info("Loans: {} is {} days late (owes {}); credit -15, reputation -{}", id, late, l.owed, pen);
            }
            if (collecting && l.owed > 0) {
                changed = true;
                if (collect(server, id, cr, l, day)) { // fully repaid by force: closed as a late loan
                    ServerPlayer online = server.getPlayerList().getPlayer(id);
                    if (online != null) sync(online);
                    continue;
                }
            }
            if (changed) {
                setDirty();
                ServerPlayer online = server.getPlayerList().getPlayer(id);
                if (online != null) sync(online);
            }
        }
    }

    /**
     * One daily installment of the forced collection of an overdue loan: first from the borrower's shop registers, then
     * from their CobbleDollars (only while online). The money taken is a late repayment; a gold Wholesaler message
     * reports it. Returns true if the debt is fully repaid (the loan is then closed as a late one).
     */
    private boolean collect(MinecraftServer server, UUID id, Credit cr, Loan l, long day) {
        PhoneConfig c = PhoneConfig.INSTANCE;
        if (l.collectBase <= 0) l.collectBase = l.owed;
        long want = Math.min(l.owed, Math.max(1, (long) Math.ceil(l.collectBase * c.loanCollectPerDay)));
        // 1) shop registers (works offline)
        long fromShops = 0;
        boolean anyZone = false;
        for (ServerLevel w : server.getAllLevels()) {
            if (fromShops >= want) break;
            ShopZones zones = ShopZones.get(w);
            boolean dirty = false;
            for (ShopZones.Zone z : zones.allAndDormant()) { // 1.1: a dormant shop's till counts too
                if (fromShops >= want) break;
                if (!z.owner.equals(id) || z.balance <= 0) continue;
                long take = Math.min(z.balance, want - fromShops);
                z.balance -= take;
                fromShops += take;
                dirty = true;
            }
            if (dirty) { zones.setDirty(); anyZone = true; }
        }
        if (anyZone) dev.tcgshop.shop.ShopServer.syncZones(server);
        // 2) CobbleDollars (the bridge needs the player entity: online only)
        long fromWallet = 0;
        ServerPlayer online = server.getPlayerList().getPlayer(id);
        if (fromShops < want && online != null && CobbleDollarsBridge.isAvailable()) {
            long take = Math.min(want - fromShops, Math.max(0, CobbleDollarsBridge.getBalance(online)));
            if (take > 0 && CobbleDollarsBridge.tryCharge(online, take)) fromWallet = take;
        }
        long got = fromShops + fromWallet;
        l.owed = Math.max(0, l.owed - got);
        l.paid += got;
        setDirty();
        Component body = got <= 0
                ? Component.translatable("tcgshop.loan.collect.empty", PriceService.format(l.owed))
                : Component.translatable(l.owed > 0 ? "tcgshop.loan.collect.partial" : "tcgshop.loan.collect.done",
                        PriceService.format(got), PriceService.format(fromShops), PriceService.format(fromWallet), PriceService.format(l.owed));
        PhoneMessages.sendFull(server, id, PhoneMessages.SENDER_WHOLESALER, "", null, "wholesaler", body, true, PhoneMessages.KIND_TEXT, null);
        TcgShop.LOGGER.info("Loans: collected {} from {} ({} registers, {} CobbleDollars); still owes {}", got, id, fromShops, fromWallet, l.owed);
        if (l.owed > 0) return false;
        close(server, id, cr, day);
        return true;
    }

    /** Small reputation penalty on every shop of that owner. Returns the points taken (0 if they have no shop). */
    private static int repPenalty(MinecraftServer server, UUID owner, int points) {
        if (points <= 0) return 0;
        boolean any = false;
        for (ServerLevel w : server.getAllLevels()) {
            ShopZones zones = ShopZones.get(w);
            boolean dirty = false;
            for (ShopZones.Zone z : zones.allAndDormant()) { // 1.1: dormant shops too
                if (!z.owner.equals(owner)) continue;
                if (z.repExact >= 0) {
                    z.repExact = Math.max(0, z.repExact - points);
                    z.reputation = (int) Math.round(z.repExact);
                } else z.reputation = Math.max(0, z.reputation - points);
                dirty = true;
            }
            if (dirty) { zones.setDirty(); any = true; }
        }
        if (any) dev.tcgshop.shop.ShopServer.syncZones(server);
        return any ? points : 0;
    }

    // ------------------------------------------------------------------ client actions

    /** loan_accept / loan_decline (id = offer message), loan_repay (amount; <= 0 = everything), loans (sync). */
    static void handle(ServerPlayer p, CompoundTag d) {
        switch (d.getString("a")) {
            case "loans" -> sync(p);
            case "loan_accept" -> get(p.getServer()).accept(p, d.getLong("id"));
            case "loan_decline" -> get(p.getServer()).decline(p, d.getLong("id"));
            case "loan_repay" -> get(p.getServer()).repay(p, d.getLong("amount"));
            case "loan_diamond" -> get(p.getServer()).requestDiamond(p);
            default -> {}
        }
    }

    private void accept(ServerPlayer p, long msgId) {
        MinecraftServer server = p.getServer();
        long day = PhoneMessages.day(server);
        if (!PhoneUpgrades.hasPhone(p)) return;
        PhoneMessages.Msg m = PhoneMessages.find(server, p.getUUID(), msgId);
        if (m == null || !PhoneMessages.KIND_LOAN_OFFER.equals(m.kind)) return;
        Credit cr = credit(p.getUUID());
        String st = m.extra.getString("st");
        if (!st.isEmpty()) {
            PhoneMessages.changed(server, p.getUUID());
            return;
        }
        if (day > m.extra.getLong("exp")) {
            m.extra.putString("st", "expired");
            if (cr.offerMsg == msgId) cr.offerMsg = -1;
            setDirty();
            PhoneMessages.changed(server, p.getUUID());
            PhoneApps.notice(p, "error", "tcgshop.loan.notice.expired.title", "tcgshop.loan.notice.expired");
            return;
        }
        if (cr.loan != null) {
            PhoneApps.notice(p, "error", "tcgshop.loan.notice.has_loan.title", "tcgshop.loan.notice.has_loan", PriceService.format(cr.loan.owed));
            return;
        }
        if (!CobbleDollarsBridge.isAvailable()) {
            PhoneApps.notice(p, "error", "tcgshop.phone.notice.no_bank.title", "tcgshop.phone.notice.no_bank");
            return;
        }
        if (m.extra.getBoolean("diamond")) { // review fix: the Diamond shop (and the rest) must still be there when accepting
            String why = diamondBlock(server, p.getUUID(), PhoneUpgrades.level(p), day);
            if (why != null) {
                PhoneApps.notice(p, "error", "tcgshop.loan.diamond.title", why);
                return;
            }
        }
        long cap = m.extra.getBoolean("diamond") ? PhoneConfig.INSTANCE.diamondLoanAmount : PhoneConfig.INSTANCE.loanMaxAmount; // 1.1: Diamond loan
        long amount = Math.max(0, Math.min(cap, m.extra.getLong("amount")));
        double rate = Math.max(0, m.extra.getDouble("rate"));
        int term = Math.max(1, m.extra.getInt("term"));
        if (amount <= 0 || !CobbleDollarsBridge.add(p, amount)) {
            PhoneApps.notice(p, "error", "tcgshop.loan.notice.failed.title", "tcgshop.loan.notice.failed");
            return;
        }
        Loan l = new Loan();
        l.principal = amount;
        l.rate = rate;
        l.owed = amount + interest(amount, rate);
        l.taken = day;
        l.due = day + term;
        l.lastFeeDay = l.due;
        cr.loan = l;
        cr.offerMsg = -1;
        m.extra.putString("st", "accepted");
        m.read = true;
        setDirty();
        PhoneMessages.changed(server, p.getUUID());
        PhoneApps.notice(p, "success", "tcgshop.loan.notice.accepted.title", "tcgshop.loan.notice.accepted",
                PriceService.format(amount), PriceService.format(l.owed), l.due + 1);
        p.level().playSound(null, p.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.7f, 1.1f);
        TcgShop.LOGGER.info("Loans: {} borrowed {} at {}% for {} days", p.getName().getString(), amount, pct(rate), term);
        sync(p);
    }

    // ------------------------------------------------------------------ 1.1: Diamond loan

    /** Does this player own a shop of Diamond tier or better (dormant ones included)? */
    public static boolean ownsDiamondShop(MinecraftServer server, UUID owner) {
        for (ServerLevel w : server.getAllLevels())
            for (ShopZones.Zone z : ShopZones.get(w).allAndDormant())
                if (z.owner.equals(owner) && dev.tcgshop.shop.ShopTier.byRank(z.tier).atLeast(dev.tcgshop.shop.ShopTier.DIAMOND)) return true;
        return false;
    }

    /**
     * Why this player cannot request the Diamond loan right now (a translation key), or null if they can. Same rules as
     * the Wholesaler's offers (phone tier, no active loan, not blocked by a very late loan) plus a Diamond shop.
     */
    static @Nullable String diamondBlock(MinecraftServer server, UUID id, int phoneTier, long day) {
        PhoneConfig c = PhoneConfig.INSTANCE;
        if (!c.diamondLoanEnabled || c.diamondLoanAmount <= 0) return "tcgshop.loan.diamond.off";
        if (!ownsDiamondShop(server, id)) return "tcgshop.loan.diamond.need_shop";
        if (phoneTier < Math.max(1, c.loanMinTier)) return "tcgshop.loan.diamond.need_phone";
        Credit cr = get(server).players.get(id);
        if (cr != null && cr.loan != null) return "tcgshop.loan.diamond.has_loan";
        if (cr != null && cr.blockedUntil >= 0 && day < cr.blockedUntil) return "tcgshop.loan.blocked";
        return null;
    }

    /** The player asks the Wholesaler for the Diamond loan: it arrives as a normal gold offer message (accept / decline). */
    private void requestDiamond(ServerPlayer p) {
        MinecraftServer server = p.getServer();
        if (!PhoneUpgrades.hasPhone(p)) return;
        long day = PhoneMessages.day(server);
        String why = diamondBlock(server, p.getUUID(), PhoneUpgrades.level(p), day);
        if (why == null && !CobbleDollarsBridge.isAvailable()) why = "tcgshop.phone.notice.no_bank";
        if (why != null) {
            PhoneApps.notice(p, "error", "tcgshop.loan.diamond.title", why);
            sync(p);
            return;
        }
        PhoneConfig c = PhoneConfig.INSTANCE;
        Credit cr = credit(p.getUUID());
        PhoneMessages.Msg old = pendingOffer(server, p.getUUID(), cr, day);
        if (old != null) { // the smaller pending offer is replaced
            old.extra.putString("st", "declined");
            old.read = true;
            cr.offerMsg = -1;
        }
        long amount = c.diamondLoanAmount;
        double rate = offerRate(cr.score);
        int term = c.diamondLoanTermDays;
        CompoundTag x = new CompoundTag();
        x.putLong("amount", amount);
        x.putDouble("rate", rate);
        x.putInt("term", term);
        x.putLong("exp", day + c.loanOfferDays);
        x.putString("st", "");
        x.putBoolean("diamond", true);
        PhoneMessages.Msg m = PhoneMessages.sendFull(server, p.getUUID(), PhoneMessages.SENDER_WHOLESALER, "", null, "wholesaler",
                Component.translatable("tcgshop.loan.diamond.offer", PriceService.format(amount), pct(rate), term,
                        PriceService.format(amount + interest(amount, rate)), c.loanOfferDays),
                true, PhoneMessages.KIND_LOAN_OFFER, x);
        if (m != null) cr.offerMsg = m.id;
        setDirty();
        PhoneMessages.changed(server, p.getUUID());
        PhoneApps.notice(p, "success", "tcgshop.loan.diamond.title", "tcgshop.loan.diamond.sent");
        sync(p);
    }

    private void decline(ServerPlayer p, long msgId) {
        MinecraftServer server = p.getServer();
        PhoneMessages.Msg m = PhoneMessages.find(server, p.getUUID(), msgId);
        if (m == null || !PhoneMessages.KIND_LOAN_OFFER.equals(m.kind) || !m.extra.getString("st").isEmpty()) return;
        m.extra.putString("st", "declined");
        m.read = true;
        Credit cr = credit(p.getUUID());
        if (cr.offerMsg == msgId) cr.offerMsg = -1;
        setDirty();
        PhoneMessages.changed(server, p.getUUID());
        PhoneApps.notice(p, "info", "tcgshop.loan.notice.declined.title", "tcgshop.loan.notice.declined");
        sync(p);
    }

    private void repay(ServerPlayer p, long requested) {
        MinecraftServer server = p.getServer();
        if (!PhoneUpgrades.hasPhone(p)) return;
        Credit cr = credit(p.getUUID());
        Loan l = cr.loan;
        if (l == null) {
            sync(p);
            return;
        }
        if (!CobbleDollarsBridge.isAvailable()) {
            PhoneApps.notice(p, "error", "tcgshop.phone.notice.no_bank.title", "tcgshop.phone.notice.no_bank");
            return;
        }
        long pay = requested <= 0 ? l.owed : Math.min(requested, l.owed);
        long balance = CobbleDollarsBridge.getBalance(p);
        if (pay <= 0) return;
        if (balance < pay || !CobbleDollarsBridge.tryCharge(p, pay)) {
            PhoneApps.notice(p, "error", "tcgshop.loan.notice.no_money.title", "tcgshop.loan.notice.no_money",
                    PriceService.format(pay), PriceService.format(balance));
            return;
        }
        l.owed -= pay;
        l.paid += pay;
        setDirty();
        p.level().playSound(null, p.blockPosition(), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 0.8f, 1.5f);
        if (l.owed > 0) {
            PhoneApps.notice(p, "success", "tcgshop.loan.notice.partial.title", "tcgshop.loan.notice.partial",
                    PriceService.format(pay), PriceService.format(l.owed));
            sync(p);
            return;
        }
        close(server, p.getUUID(), cr, PhoneMessages.day(server));
        PhoneApps.notice(p, "success", "tcgshop.loan.notice.repaid.title", "tcgshop.loan.notice.repaid", PriceService.format(pay), cr.score);
        sync(p);
    }

    /** The loan is fully repaid: credit history and score. */
    private void close(MinecraftServer server, UUID id, Credit cr, long day) {
        Loan l = cr.loan;
        if (l == null) return;
        long term = Math.max(1, l.due - l.taken), took = Math.max(0, day - l.taken), late = Math.max(0, day - l.due);
        int before = cr.score;
        if (late == 0) {
            cr.score = clampScore(cr.score + 6 + (took * 2 <= term ? 4 : 0));
            cr.onTime++;
        } else {
            cr.score = clampScore(cr.score - (int) Math.min(25, 4 + 3 * late));
            cr.late++;
        }
        cr.repaid++;
        cr.hist.add(0, new Hist(l.principal, took, term, late));
        while (cr.hist.size() > HISTORY) cr.hist.remove(cr.hist.size() - 1);
        cr.blockedUntil = l.veryLate ? day + BLOCK_AFTER_VERY_LATE : -1;
        cr.nextOffer = Math.max(cr.nextOffer, day + 1);
        cr.loan = null;
        setDirty();
        int delta = cr.score - before;
        PhoneMessages.sendFull(server, id, PhoneMessages.SENDER_WHOLESALER, "", null, "wholesaler",
                Component.translatable(late == 0 ? "tcgshop.loan.closed.on_time" : "tcgshop.loan.closed.late", cr.score, (delta >= 0 ? "+" : "") + delta),
                true, PhoneMessages.KIND_TEXT, null);
    }

    // ------------------------------------------------------------------ sync

    /** Sends the loan, credit and history to the client (k = "loans"). */
    public static void sync(ServerPlayer p) {
        if (!Net.INSTANCE.canSend(p, PhonePayloads.Apps.ID)) return;
        MinecraftServer server = p.getServer();
        PhoneConfig c = PhoneConfig.INSTANCE;
        long day = PhoneMessages.day(server);
        Credit cr = get(server).players.get(p.getUUID());
        CompoundTag d = new CompoundTag();
        d.putString("k", "loans");
        d.putLong("day", day);
        d.putBoolean("enabled", c.loansEnabled);
        d.putInt("minTier", c.loanMinTier);
        d.putLong("max", c.loanMaxAmount);
        d.putDouble("fee", c.loanLateFeePerDay);
        d.putInt("veryLate", c.loanVeryLateDays);
        d.putInt("collectDays", c.loanCollectPerDay > 0 ? c.loanCollectAfterDays : -1); // -1 = never collected by force
        d.putDouble("collectPct", c.loanCollectPerDay);
        d.putInt("score", cr == null ? SCORE_START : cr.score);
        // 1.1: Diamond loan (requested from the app): amount, term and why it is not available (absent = available)
        d.putLong("diamondAmount", c.diamondLoanEnabled ? c.diamondLoanAmount : 0);
        d.putInt("diamondTerm", c.diamondLoanTermDays);
        String dWhy = diamondBlock(server, p.getUUID(), PhoneUpgrades.level(p), day);
        if (dWhy != null) d.putString("diamondWhy", dWhy);
        if (cr != null) {
            d.putInt("repaid", cr.repaid);
            d.putInt("onTime", cr.onTime);
            d.putInt("late", cr.late);
            d.putBoolean("blocked", cr.blockedUntil >= 0 && day < cr.blockedUntil);
            if (cr.offerMsg >= 0) d.putLong("offer", cr.offerMsg);
            if (cr.loan != null) {
                Loan l = cr.loan;
                CompoundTag x = new CompoundTag();
                x.putLong("principal", l.principal);
                x.putLong("owed", l.owed);
                x.putLong("paid", l.paid);
                x.putDouble("rate", l.rate);
                x.putLong("taken", l.taken);
                x.putLong("due", l.due);
                x.putBoolean("veryLate", l.veryLate);
                d.put("loan", x);
            }
            ListTag h = new ListTag();
            for (Hist e : cr.hist) {
                CompoundTag x = new CompoundTag();
                x.putLong("a", e.amount);
                x.putLong("d", e.days);
                x.putLong("t", e.term);
                x.putLong("l", e.lateDays);
                h.add(x);
            }
            d.put("hist", h);
        }
        Net.INSTANCE.send(p, new PhonePayloads.Apps(d));
    }

    // ------------------------------------------------------------------ persistence

    private static Loans fromNbt(CompoundTag nbt, HolderLookup.Provider lookup) {
        Loans s = new Loans();
        s.lastDay = nbt.contains("lastDay") ? nbt.getLong("lastDay") : -1;
        CompoundTag p = nbt.getCompound("players");
        for (String k : p.getAllKeys()) {
            try {
                s.players.put(UUID.fromString(k), Credit.read(p.getCompound(k)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return s;
    }

    @Override
    public CompoundTag save(CompoundTag nbt, HolderLookup.Provider lookup) {
        nbt.putLong("lastDay", lastDay);
        CompoundTag p = new CompoundTag();
        players.forEach((k, v) -> p.put(k.toString(), v.write()));
        nbt.put("players", p);
        return nbt;
    }
}

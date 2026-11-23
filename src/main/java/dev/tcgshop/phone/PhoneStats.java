package dev.tcgshop.phone;

import dev.tcgshop.pricing.PriceService;
import dev.tcgshop.shop.ShopMissions;
import dev.tcgshop.shop.ShopZones;
import dev.tcgshop.shop.TipLedger;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 1.0 phone Stats app, server side: money in the registers of all your shops, reputation, tips (TipLedger), charisma,
 * loan / credit, and «what's going for and against you»: pros and cons derived from the shops' state (reputation
 * components, missions, reviews, cleanliness, security, staff, capacity, loan...). Sent on request (a = "stats") as
 * {@link PhonePayloads.Apps} with k = "stats".
 * <p>
 * Each pro / con is {t: translation key, a: [args], w: weight, tip: optional advice key}; an arg starting with
 * {@link PhoneApps#KEY_ARG} is a translation key (translated on the client). Both lists come sorted by weight.
 */
public final class PhoneStats {
    private PhoneStats() {}

    /** Most pros / cons sent. */
    private static final int MAX_ITEMS = 9;

    private record Item(String key, List<String> args, int weight, String tip) {}

    public static void send(ServerPlayerEntity p) {
        if (!ServerPlayNetworking.canSend(p, PhonePayloads.Apps.ID)) return;
        if (!PhoneUpgrades.hasPhone(p)) return;
        ServerPlayNetworking.send(p, new PhonePayloads.Apps(build(p.getServer(), p.getUuid())));
    }

    static NbtCompound build(MinecraftServer server, UUID owner) {
        List<ShopZones.Zone> mine = new ArrayList<>();
        long costs = 0;
        for (ServerWorld w : server.getWorlds()) {
            ShopZones zs = ShopZones.get(w);
            costs += zs.costs(owner);
            for (ShopZones.Zone z : zs.all()) if (z.owner.equals(owner)) mine.add(z);
        }
        long day = PhoneMessages.day(server);
        long cash = 0, revenue = 0, wages = 0;
        int sales = 0, customers = 0, complaints = 0, stolen = 0, detained = 0, repMax = -1, repSum = 0, smellMax = 0, detectors = 0;
        int reviews = 0, overflow = 0;
        double starSum = 0;
        boolean guard = false, cashier = false;
        Map<String, int[]> factors = new LinkedHashMap<>(); // key -> {sum, count}
        NbtList shops = new NbtList();
        ShopZones.Zone best = null;
        for (ShopZones.Zone z : mine) {
            cash += z.balance;
            revenue += z.revenue;
            wages += z.wagesPaid;
            sales += z.sales;
            customers += z.customers;
            complaints += z.complaints;
            stolen += z.stolen;
            detained += z.detained;
            repSum += z.reputation;
            repMax = Math.max(repMax, z.reputation);
            smellMax = Math.max(smellMax, z.smell);
            detectors += z.detectors;
            guard |= z.guard;
            cashier |= z.cashier || z.cashier2;
            reviews += z.reviews.size();
            for (ShopZones.Review r : z.reviews) starSum += r.stars();
            overflow = Math.max(overflow, ShopMissions.overflowToday(z, day));
            for (Map.Entry<String, Integer> e : z.factors.entrySet()) {
                if (e.getKey().startsWith("m_")) continue;
                int[] acc = factors.computeIfAbsent(e.getKey(), k -> new int[2]);
                acc[0] += e.getValue();
                acc[1]++;
            }
            if (best == null || z.reputation > best.reputation) best = z;
            NbtCompound s = new NbtCompound();
            s.putInt("tier", z.tier);
            s.putInt("rep", z.reputation);
            s.putLong("bal", z.balance);
            s.putLong("rev", z.revenue);
            s.putInt("miss", ShopMissions.completedCount(z));
            s.putLong("core", z.core.asLong());
            shops.add(s);
        }
        TipLedger.Entry tips = TipLedger.of(server, owner);
        long tipsToday = TipLedger.today(server, owner, day);
        int charisma = dev.tcgshop.shop.Charisma.level(server, owner);
        Loans.Credit credit = Loans.peek(server, owner);
        int score = credit == null ? Loans.SCORE_START : credit.score;
        Loans.Loan loan = credit == null ? null : credit.loan;

        NbtCompound d = new NbtCompound();
        d.putString("k", "stats");
        d.putLong("day", day);
        d.putInt("shops", mine.size());
        d.putLong("cash", cash);
        d.putLong("revenue", revenue);
        d.putLong("costs", costs);
        d.putLong("wages", wages);
        d.putInt("sales", sales);
        d.putInt("customers", customers);
        d.putInt("rep", repMax);
        d.putInt("repAvg", mine.isEmpty() ? -1 : Math.round(repSum / (float) mine.size()));
        d.putDouble("stars", reviews == 0 ? 0 : starSum / reviews);
        d.putInt("reviews", reviews);
        d.putLong("tipsTotal", tips.total);
        d.putInt("tipsCount", tips.count);
        d.putLong("tipsBest", tips.best);
        d.putLong("tipsToday", tipsToday);
        d.putInt("charisma", charisma);
        d.putInt("credit", score);
        if (loan != null) {
            d.putLong("loanOwed", loan.owed);
            d.putLong("loanDue", loan.due);
        }
        if (best != null && best.missions >= 0) {
            d.putInt("missions", ShopMissions.completedCount(best));
            d.putInt("missionsTotal", ShopMissions.total());
        }
        d.put("shopList", shops);

        // ---------------------------------------------------------------- pros and cons
        List<Item> pros = new ArrayList<>(), cons = new ArrayList<>();
        if (mine.isEmpty()) cons.add(item("tcgshop.stats.con.no_shop", 20, null));
        else {
            if (repMax >= 80) pros.add(item("tcgshop.stats.pro.rep_high", 10, null, String.valueOf(repMax)));
            else if (repMax < 40) cons.add(item("tcgshop.stats.con.rep_low", 10, null, String.valueOf(repMax)));
            // reputation components, averaged over your shops (small ones are left out)
            for (Map.Entry<String, int[]> e : factors.entrySet()) {
                int v = Math.round(e.getValue()[0] / (float) Math.max(1, e.getValue()[1]));
                String name = PhoneApps.KEY_ARG + "tcgshop.rep." + e.getKey();
                if (v >= 2) pros.add(item("tcgshop.stats.factor", Math.min(9, v), null, name, "+" + v));
                else if (v <= -2) cons.add(item("tcgshop.stats.factor", Math.min(12, -v), "tcgshop.rep.tip." + e.getKey(), name, String.valueOf(v)));
            }
            if (best != null && best.missions >= 0) {
                // 1.0: all the missions (MissionLedger), not only the 12 core ones of the 0.9 mask
                int done = ShopMissions.completedCount(best), total = ShopMissions.total();
                if (done >= total) pros.add(item("tcgshop.stats.pro.missions_all", 9, null));
                else if (done * 3 >= total * 2) pros.add(item("tcgshop.stats.pro.missions", 5, null, String.valueOf(done), String.valueOf(total)));
                else {
                    int next = ShopMissions.nextStep(best);
                    String nk = next < 0 ? "" : PhoneApps.KEY_ARG + "tcgshop.mission." + ShopMissions.Mission.values()[next].key;
                    cons.add(item("tcgshop.stats.con.missions", 8, next < 0 ? null : "tcgshop.mission." + ShopMissions.Mission.values()[next].key + ".hint",
                            String.valueOf(done), String.valueOf(total), nk));
                }
            }
            double avg = reviews == 0 ? 0 : starSum / reviews;
            String avgS = String.format(Locale.ROOT, "%.1f", avg);
            if (reviews == 0) cons.add(item("tcgshop.stats.con.no_reviews", 3, null));
            else if (avg >= 4) pros.add(item("tcgshop.stats.pro.reviews", 7, null, avgS, String.valueOf(reviews)));
            else if (avg < 2.5 && reviews >= 3) cons.add(item("tcgshop.stats.con.reviews", 8, null, avgS, String.valueOf(reviews)));
            if (smellMax >= 20) cons.add(item("tcgshop.stats.con.smell", 7, "tcgshop.rep.tip.smell", String.valueOf(smellMax)));
            else if (smellMax == 0) pros.add(item("tcgshop.stats.pro.clean", 3, null));
            if (guard) pros.add(item("tcgshop.stats.pro.guard", 4, null));
            else if (stolen > detained) cons.add(item("tcgshop.stats.con.thefts", 6, "tcgshop.phone.security_tip", String.valueOf(stolen)));
            if (detectors > 0) pros.add(item("tcgshop.stats.pro.detectors", 2, null, String.valueOf(detectors)));
            if (cashier) pros.add(item("tcgshop.stats.pro.cashier", 3, null));
            if (complaints >= 5 && complaints * 5 > customers) cons.add(item("tcgshop.stats.con.complaints", 5, null, String.valueOf(complaints)));
            if (overflow >= ShopMissions.OVERFLOW_SUGGEST) cons.add(item("tcgshop.stats.con.full", 4, null, String.valueOf(overflow)));
        }
        if (tipsToday > 0) pros.add(item("tcgshop.stats.pro.tips_today", 4, null, PriceService.format(tipsToday)));
        else if (tips.total > 0) pros.add(item("tcgshop.stats.pro.tips_total", 2, null, PriceService.format(tips.total), String.valueOf(tips.count)));
        if (charisma >= 5) pros.add(item("tcgshop.stats.pro.charisma", 3, null, String.valueOf(charisma)));
        if (loan != null) {
            boolean late = day > loan.due;
            cons.add(item(late ? "tcgshop.stats.con.loan_late" : "tcgshop.stats.con.loan", late ? 14 : 9, null,
                    PriceService.format(loan.owed), String.valueOf(loan.due + 1), String.valueOf(Math.max(0, day - loan.due))));
        }
        if (credit != null && credit.repaid > 0 && score >= 70) pros.add(item("tcgshop.stats.pro.credit", 3, null, String.valueOf(score)));
        else if (score < 35) cons.add(item("tcgshop.stats.con.credit", 5, null, String.valueOf(score)));
        d.put("pros", write(pros));
        d.put("cons", write(cons));
        return d;
    }

    private static Item item(String key, int weight, String tip, String... args) {
        return new Item(key, List.of(args), weight, tip == null ? "" : tip);
    }

    private static NbtList write(List<Item> items) {
        items.sort(Comparator.comparingInt(Item::weight).reversed());
        NbtList l = new NbtList();
        for (int i = 0; i < Math.min(MAX_ITEMS, items.size()); i++) {
            Item it = items.get(i);
            NbtCompound c = new NbtCompound();
            c.putString("t", it.key);
            NbtList a = new NbtList();
            for (String s : it.args) a.add(NbtString.of(s));
            c.put("a", a);
            c.putInt("w", it.weight);
            if (!it.tip.isEmpty()) c.putString("tip", it.tip);
            l.add(c);
        }
        return l;
    }
}

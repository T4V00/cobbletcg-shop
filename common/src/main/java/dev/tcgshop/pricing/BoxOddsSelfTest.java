package dev.tcgshop.pricing;

import dev.masterball.api.card.CardCatalog;
import dev.masterball.api.card.PrintVariant;
import dev.masterball.api.card.Rarity;
import dev.masterball.api.pack.PackContents;
import dev.tcgshop.TcgShop;
import dev.tcgshop.shop.BoosterBox;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Selftest step "box_odds" (/tcgshop debug selftest): how often opening a whole booster box pays for itself.
 *
 * <p>For every set with sealed packs it simulates {@link #BOXES_PER_SET} boxes of {@link BoosterBox#PACKS} sealed packs
 * with the REAL pull code ({@link PackContents#rollTier} + {@link PackContents#open}, print variants included) and values
 * every card at its real market price in CobbleDollars ({@link PriceService#priceCd(ItemStack)}: the price a card is
 * listed / sold at, print variant and today's trend included). Each box is compared with the box's market price
 * ({@link PriceService#sealedCd} BOX). Logged: median and 90th percentile of P(box value &gt; box price) across sets, the
 * mean value/price ratio, the 5 worst sets and, per set, where its value comes from.
 *
 * <h2>Two groups of sets</h2>
 * Box prices are built on the PRICING reference odds ({@link PackContents#expectedValue}), but many sets also have a real
 * sealed price far above what their cards are worth (vintage boxes, collector sets): those never pay for themselves,
 * whatever the pull odds. So the targets are checked where the pull odds actually decide the outcome:
 * <ul>
 *   <li>every set: P(profit) at most {@link #MAX_SET} (target about 25 %);</li>
 *   <li>"content-priced" sets, whose box price is anchored to its contents (36 packs at the pricing reference value are
 *       worth at least {@link #ANCHOR_MIN} of the box price): median P(profit) between {@link #MEDIAN_MIN} and
 *       {@link #MEDIAN_MAX} and mean value/price between {@link #RATIO_MIN} and {@link #RATIO_MAX} (so a future table
 *       edit can't silently break them).</li>
 * </ul>
 * It also checks that the pull expected value stays below the pricing one (prices must not follow the pull odds down).
 */
public final class BoxOddsSelfTest {
    public static final int BOXES_PER_SET = 2000;
    /** Wall-clock budget: past it the remaining sets are skipped (sets run in a seeded shuffled order, so it's a sample). */
    public static final long BUDGET_MS = 15_000;
    /** A set is "content-priced" when 36 packs at the pricing reference value are worth at least this share of its box price. */
    public static final double ANCHOR_MIN = 0.75;
    /**
     * Checked bounds (calibrated targets ± tolerance; values also move a little with the daily market trend). The 2026-11
     * calibration gave: content-priced median P(profit) ~6 %, worst set ~22 %, mean value/price ~0.47. A mean
     * value/price of 0.6+ is not reachable together with the 25 % cap: the content-priced modern sets are hit-driven
     * (a few Special Illustration / Hyper / god pulls carry the box), so their P(profit) already reaches ~20-27 % at a
     * value/price of only 0.55-0.63.
     */
    public static final double MEDIAN_MIN = 0.04, MEDIAN_MAX = 0.16, MAX_SET = 0.28, RATIO_MIN = 0.38, RATIO_MAX = 0.75;

    private BoxOddsSelfTest() {}

    /** shares: value share of basic slots, mid slot, luck slot, semi-god packs, god packs, print-variant premium. */
    private record SetResult(String set, double pProfit, double ratio, long boxPrice, double anchor, double[] shares) {
        boolean contentPriced() { return anchor >= ANCHOR_MIN; }
    }

    private record Stats(int n, double median, double p90, double max, String maxSet, double meanRatio) {}

    public static void run() {
        long t0 = System.currentTimeMillis();
        List<String> sets = new ArrayList<>();
        for (var si : CardCatalog.sets()) if (!BoosterBox.isDeadSet(si.id())) sets.add(si.id());
        int total = sets.size();
        if (total == 0) throw new IllegalStateException("no set with sealed packs");
        // seeded shuffle: if the budget runs out, the sets simulated are a fair sample
        RandomSource shuffle = RandomSource.create(20261127L);
        for (int i = sets.size() - 1; i > 0; i--) {
            int j = shuffle.nextInt(i + 1);
            String tmp = sets.get(i); sets.set(i, sets.get(j)); sets.set(j, tmp);
        }
        PrintVariant[] variants = PrintVariant.values();
        Map<Item, long[]> priceCache = new HashMap<>(); // item -> price per variant ordinal (-1 = not computed yet)
        Map<Item, Long> standardCache = new HashMap<>();
        List<SetResult> results = new ArrayList<>();
        double evRefSum = 0, evPullSum = 0;
        for (String set : sets) {
            if (System.currentTimeMillis() - t0 > BUDGET_MS && results.size() >= 10) break;
            long boxPrice = PriceService.sealedCd(set, PriceService.Sealed.BOX);
            RandomSource rnd = RandomSource.create(0x5EEDL ^ set.hashCode());
            int wins = 0;
            double valueSum = 0;
            double[] parts = new double[6];
            for (int b = 0; b < BOXES_PER_SET; b++) {
                long box = 0;
                for (int k = 0; k < BoosterBox.PACKS; k++) {
                    int tier = PackContents.rollTier(rnd);
                    List<ItemStack> cards = PackContents.open(set, tier, rnd);
                    for (int slot = 0; slot < cards.size(); slot++) {
                        ItemStack c = cards.get(slot);
                        long[] row = priceCache.computeIfAbsent(c.getItem(), it -> {
                            long[] a = new long[variants.length];
                            java.util.Arrays.fill(a, -1);
                            return a;
                        });
                        int v = PrintVariant.of(c).ordinal();
                        if (row[v] < 0) row[v] = PriceService.priceCd(c);
                        box += row[v];
                        // where the value comes from: god / semi-god pack, else the normal pack's slot (3 basic, mid, luck)
                        parts[tier == 3 ? 4 : tier == 2 ? 3 : slot < 3 ? 0 : slot == 3 ? 1 : 2] += row[v];
                        if (v != PrintVariant.STANDARD.ordinal())
                            parts[5] += row[v] - standardCache.computeIfAbsent(c.getItem(), it -> PriceService.priceCd(new ItemStack(it)));
                    }
                }
                if (box > boxPrice) wins++;
                valueSum += box;
            }
            double mean = valueSum / BOXES_PER_SET;
            for (int i = 0; i < parts.length; i++) parts[i] /= Math.max(1, valueSum);
            double refPack = PriceService.packExpectedUsd(set);
            double anchor = BoosterBox.PACKS * refPack / Math.max(1e-9, PriceService.sealedUsd(set, PriceService.Sealed.BOX));
            results.add(new SetResult(set, wins / (double) BOXES_PER_SET, mean / Math.max(1, boxPrice), boxPrice, anchor, parts));
            evRefSum += refPack;
            evPullSum += PackContents.pullExpectedValue(set, id -> PriceService.baseUsd(id, Rarity.of(id)));
        }
        int n = results.size();
        List<SetResult> byP = new ArrayList<>(results);
        byP.sort(Comparator.comparingDouble(SetResult::pProfit));
        List<SetResult> anchored = new ArrayList<>();
        for (SetResult r : byP) if (r.contentPriced()) anchored.add(r);
        Stats all = stats(byP), content = stats(anchored);
        long ms = System.currentTimeMillis() - t0;
        TcgShop.LOGGER.info("[selftest] box_odds: {} of {} sets with sealed packs{}, {} boxes x {} packs each, {} ms",
                n, total, n < total ? " (SAMPLED: time budget reached)" : "", BOXES_PER_SET, BoosterBox.PACKS, ms);
        TcgShop.LOGGER.info("[selftest] box_odds: all sets: P(box value > box price) median {}% · p90 {}% · max {}% ({}) · mean value/price {} · pull EV / pricing EV {}",
                pct(all.median()), pct(all.p90()), pct(all.max()), all.maxSet(), fmt(all.meanRatio()), fmt(evPullSum / Math.max(1e-9, evRefSum)));
        if (content.n() > 0)
            TcgShop.LOGGER.info("[selftest] box_odds: {} content-priced sets (box price <= 36 reference packs / {}): P(profit) median {}% · p90 {}% · max {}% · mean value/price {}",
                    content.n(), ANCHOR_MIN, pct(content.median()), pct(content.p90()), pct(content.max()), fmt(content.meanRatio()));
        StringBuilder worst = new StringBuilder();
        for (int i = n - 1; i >= Math.max(0, n - 5); i--) worst.append(describe(byP.get(i))).append(i > Math.max(0, n - 5) ? " | " : "");
        TcgShop.LOGGER.info("[selftest] box_odds worst: {}", worst);
        StringBuilder line = new StringBuilder();
        for (SetResult r : byP) line.append(r.set()).append(r.contentPriced() ? "* " : " ").append(pct(r.pProfit())).append("%/").append(fmt(r.ratio())).append(' ');
        TcgShop.LOGGER.info("[selftest] box_odds all (P%/ratio, * = content-priced): {}", line.toString().trim());
        if (evPullSum > evRefSum * 1.0001)
            throw new IllegalStateException("pull odds are more generous than the pricing reference odds");
        if (all.max() > MAX_SET)
            throw new IllegalStateException("set " + all.maxSet() + " profits in " + pct(all.max()) + "% of boxes (max " + pct(MAX_SET) + "%)");
        if (content.n() < 5) throw new IllegalStateException("only " + content.n() + " content-priced sets: the pricing changed?");
        if (content.median() < MEDIAN_MIN || content.median() > MEDIAN_MAX)
            throw new IllegalStateException("content-priced median P(profit) " + pct(content.median()) + "% outside [" + pct(MEDIAN_MIN) + ", " + pct(MEDIAN_MAX) + "]%");
        if (content.meanRatio() < RATIO_MIN || content.meanRatio() > RATIO_MAX)
            throw new IllegalStateException("content-priced mean value/price " + fmt(content.meanRatio()) + " outside [" + RATIO_MIN + ", " + RATIO_MAX + "]");
    }

    /** Stats of sets already sorted by P(profit). */
    private static Stats stats(List<SetResult> sorted) {
        int n = sorted.size();
        if (n == 0) return new Stats(0, 0, 0, 0, "-", 0);
        double median = n % 2 == 1 ? sorted.get(n / 2).pProfit() : (sorted.get(n / 2 - 1).pProfit() + sorted.get(n / 2).pProfit()) / 2;
        double p90 = sorted.get(Math.max(0, (int) Math.ceil(0.9 * n) - 1)).pProfit();
        double ratio = 0;
        for (SetResult r : sorted) ratio += r.ratio();
        return new Stats(n, median, p90, sorted.get(n - 1).pProfit(), sorted.get(n - 1).set(), ratio / n);
    }

    private static String describe(SetResult r) {
        double[] s = r.shares();
        return String.format(Locale.ROOT, "%s P=%s%% ratio=%s box=%d anchor=%s (value: basic %s%%, mid %s%%, luck %s%%, semi %s%%, god %s%%; variant premium %s%%)",
                r.set(), pct(r.pProfit()), fmt(r.ratio()), r.boxPrice(), fmt(r.anchor()), pct(s[0]), pct(s[1]), pct(s[2]), pct(s[3]), pct(s[4]), pct(s[5]));
    }

    private static String pct(double x) { return String.format(Locale.ROOT, "%.1f", 100 * x); }

    private static String fmt(double x) { return String.format(Locale.ROOT, "%.3f", x); }
}

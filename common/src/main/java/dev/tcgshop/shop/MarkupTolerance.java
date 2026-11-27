package dev.tcgshop.shop;

import dev.tcgshop.EconomyConfig;

/**
 * 1.1 (customers update): how far above market customers are willing to go, by shop reputation.
 * <p>
 * The reputation premium ({@link ShopZones#repPremium}) is the "free" markup a famous shop gets. Anything perceived
 * above market AFTER that premium (the perceived ratio, already divided by 1 + premium) makes a purchase much less
 * likely: the buy chance is multiplied by {@code exp(-over / width)}, and that width grows with reputation (from
 * {@link EconomyConfig#markupFalloffLowRep} at reputation 0 to {@link EconomyConfig#markupFalloffHighRep} at 100). A
 * small shop selling 20 % over market sells almost nothing; a famous one still sells now and then.
 * <p>
 * Hagglers get the same idea: the part of their price ceiling above market is scaled by
 * {@link #haggleCeilScale(int)} ({@link EconomyConfig#haggleMarkupRepMin} at reputation 0, full at 100).
 */
public final class MarkupTolerance {
    private MarkupTolerance() {}

    /** Falloff width (fraction of market) at that reputation. */
    public static double width(int rep) {
        EconomyConfig c = EconomyConfig.INSTANCE;
        double r = Math.max(0, Math.min(100, rep)) / 100.0;
        double lo = Math.max(0.005, c.markupFalloffLowRep), hi = Math.max(lo, c.markupFalloffHighRep);
        return lo + (hi - lo) * r;
    }

    /**
     * Multiplier on the buy chance for a perceived price ratio (price / market, already divided by 1 + reputation
     * premium and multiplied by the tier difficulty). 1 at or below market. widthMult > 1 = less price-sensitive
     * (wealthy customers).
     */
    public static double buyMult(double perceivedRatio, int rep, double widthMult) {
        double over = perceivedRatio - 1;
        if (over <= 0 || Double.isNaN(over)) return 1;
        return Math.exp(-over / (width(rep) * Math.max(0.1, widthMult)));
    }

    /** Share (0-1) of a haggler's above-market ceiling that is kept at that reputation. */
    public static double haggleCeilScale(int rep) {
        double min = Math.max(0, Math.min(1, EconomyConfig.INSTANCE.haggleMarkupRepMin));
        return min + (1 - min) * Math.max(0, Math.min(100, rep)) / 100.0;
    }

    /** A haggler's ceiling multiplier (of market): the part above 1 shrinks in shops with a low reputation. */
    public static double ceilMult(double cm, int rep) {
        return cm <= 1 ? cm : 1 + (cm - 1) * haggleCeilScale(rep);
    }
}

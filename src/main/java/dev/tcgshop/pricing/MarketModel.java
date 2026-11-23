package dev.tcgshop.pricing;

/**
 * Dynamic market: multiplies the base price by a trend that changes every "market day".
 *
 * It is 100% deterministic (smooth noise from a server seed), so:
 *  - client and server compute exactly the same thing without sending thousands of prices every day,
 *  - the history (phone charts) is obtained by evaluating past days, without storing anything.
 *
 * Trend = slow per-set wave (hype for a collection) + fast set wave + the card's own oscillation.
 * Rarer cards are more volatile, as in the real market.
 */
public final class MarketModel {
    public static final double MIN_MULT = 0.5, MAX_MULT = 2.0;

    private MarketModel() {}

    public static double multiplier(long seed, String setId, String cardId, int rarityTier, double day, double volatility) {
        if (volatility <= 0) return 1.0;
        double set = 0.16 * noise(seed, setId, day / 9.0) + 0.06 * noise(seed ^ 0x5DEECE66DL, setId, day / 3.0);
        double card = 0.10 * (1 + rarityTier * 0.4) * noise(seed ^ 0xBF58476D1CE4E5B9L, cardId, day / 4.0);
        double m = Math.exp(volatility * (set + card));
        return Math.max(MIN_MULT, Math.min(MAX_MULT, m));
    }

    /** Smoothed 1D value noise, in [-1, 1]. */
    static double noise(long seed, String key, double x) {
        long i = (long) Math.floor(x);
        double f = x - i;
        double a = hash(seed, key, i), b = hash(seed, key, i + 1);
        double t = f * f * (3 - 2 * f);
        return a + (b - a) * t;
    }

    private static double hash(long seed, String key, long i) {
        long h = seed ^ (key.hashCode() * 0x9E3779B97F4A7C15L) ^ (i * 0xC2B2AE3D27D4EB4FL);
        h ^= (h >>> 33);
        h *= 0xFF51AFD7ED558CCDL;
        h ^= (h >>> 33);
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= (h >>> 33);
        return ((h >>> 11) * 0x1.0p-53) * 2 - 1;
    }
}

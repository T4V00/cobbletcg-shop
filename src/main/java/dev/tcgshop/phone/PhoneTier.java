package dev.tcgshop.phone;

import java.util.Map;

/**
 * TCG phone tiers (0.9). The tier belongs to the PLAYER (not the item): if you lose the phone, the new one stays the same.
 *
 * <pre>
 * Tier   Name                 Cost        Unlocks
 * 0      Basic                free        Basic Market (modern packs and their x36 boxes, furniture, shop blocks, tools),
 *                                         My shop, Collection, Quick sell, Friends, Titles, Achievements, Settings, Upgrades
 * 1      Plus                 20,000      Prices app · packs up to $20 · Mystery Box in the Market
 * 2      Pro                  60,000      Displays, Furniture and TCG League apps (1.0) · packs up to $50 · −3% Market
 * 3      Ultra                150,000     CobbleGrade app · Poké plushies · packs up to $150 · quick sell +3%
 * 4      Max                  400,000     Poké Roulette · packs up to $500 · −6% Market
 * 5      Elite                1,200,000   packs up to $2000 (vintage) · Wholesaler with +4 box sets and −3% · −8% Market · sell +5%
 * 6      MasterBall Edition   5,000,000   EVERYTHING (including sets locked by shop level) · −12% Market · Wholesaler −5% ·
 *                                         sell +8% · 1 call per day: Collector or Wholesaler
 * </pre>
 * Costs and price limits are changed in config/tcgshop-phone.json. Since 1.0 the x36 booster box of every pack follows
 * the same tier limit as the pack itself (PhoneConfig.boxMinTier can raise it again).
 */
public enum PhoneTier {
    BASIC("basic", 0xFF8A93A6),
    PLUS("plus", 0xFF43A047),
    PRO("pro", 0xFF2F80ED),
    ULTRA("ultra", 0xFF9B51E0),
    MAX("max", 0xFFF2994A),
    ELITE("elite", 0xFFE04848),
    MASTERBALL("masterball", 0xFFB3262E);

    public final String key;
    public final int color;

    PhoneTier(String key, int color) {
        this.key = key;
        this.color = color;
    }

    public static final int MAX_LEVEL = values().length - 1;

    public static PhoneTier of(int level) {
        return values()[Math.max(0, Math.min(MAX_LEVEL, level))];
    }

    /** Default cost of upgrading TO this tier (index = tier). */
    public static final long[] DEFAULT_COSTS = {0, 20_000, 60_000, 150_000, 400_000, 1_200_000, 5_000_000};
    /** Max price (market USD of the set's PACK) that can be bought at each tier; the last one has no limit. */
    public static final double[] DEFAULT_PACK_USD = {8, 20, 50, 150, 500, 2_000};

    /** Phone Market price multiplier per tier. */
    public static final double[] PHONE_DISCOUNT = {1.0, 1.0, 0.97, 0.97, 0.94, 0.92, 0.88};
    /** Wholesaler price multiplier per tier. */
    public static final double[] WHOLESALE_DISCOUNT = {1.0, 1.0, 1.0, 1.0, 1.0, 0.97, 0.95};
    /** Bonus on top of the phone's quick-sell rate (0.03 = +3 points). */
    public static final double[] SELL_BONUS = {0, 0, 0, 0.03, 0.03, 0.05, 0.08};
    /** Extra box sets the Wholesaler brings. */
    public static final int[] EXTRA_BOX_SETS = {0, 0, 0, 0, 0, 4, 8};

    /** Minimum tier of each phone app (those not listed here: tier 0). 1.0: the TCG League moved from Max (4) to Pro (2). */
    public static final Map<String, Integer> APP_TIER = Map.of(
            "prices", 1,
            "displays", 2,
            "furniture", 2,
            "grade", 3,
            "league", 2,
            "loans", 2); // 1.0: Wholesaler loans (PhoneConfig.loanMinTier, default Pro)

    public static int appTier(String app) {
        return APP_TIER.getOrDefault(app, 0);
    }

    /** Tier with the daily call (Collector / Wholesaler). */
    public static final int CALL_TIER = MAX_LEVEL;
    /** Tier that bypasses the shop-level set lock (PackLock). */
    public static final int PACKLOCK_BYPASS_TIER = MAX_LEVEL;
    /**
     * Tier from which the phone Market sells boxes (0.9 default; since 1.0 boxes follow their pack's tier and the minimum
     * comes from PhoneConfig.boxMinTier) / Mystery Box / plushies / Roulette.
     */
    public static final int PHONE_BOX_TIER = 2, PHONE_MYSTERY_TIER = 1, PHONE_PLUSH_TIER = 3, ROULETTE_TIER = 4;
}

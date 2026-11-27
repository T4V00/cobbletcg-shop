package dev.tcgshop.shop;

import dev.tcgshop.EconomyConfig;

/**
 * 1.1: mid-game difficulty scaled by the shop tier. Paper, Wood and Brick are unchanged; from
 * {@link EconomyConfig#tierDifficultyStartRank} (Stone = rank 3 by default) every tier adds one "step", reaching the full
 * effect at Netherite (rank 11). All the knobs live in tcgshop-economy.json (tier* keys); 0 turns each one off.
 * <ul>
 *   <li>Customers perceive prices as higher (tighter tolerance; PICKY and WHALE thresholds tighten the same way).</li>
 *   <li>Hagglers' most-they-would-pay ceiling is a bit lower.</li>
 *   <li>Thieves come a little more often and roll stronger tiers more often (guards and detectors still subtract).</li>
 * </ul>
 */
public final class TierDifficulty {
    private TierDifficulty() {}

    public static final int MAX_RANK = ShopTier.NETHERITE.rank();

    /** Tier steps above the start (0 = early game, unchanged). */
    public static int steps(int rank) {
        int start = Math.max(1, Math.min(MAX_RANK, EconomyConfig.INSTANCE.tierDifficultyStartRank));
        return Math.max(0, rank - (start - 1));
    }

    /** 0 below the start tier .. 1 at Netherite. */
    public static double scale(int rank) {
        int start = Math.max(1, Math.min(MAX_RANK, EconomyConfig.INSTANCE.tierDifficultyStartRank));
        int span = MAX_RANK - start + 1;
        return span <= 0 ? 0 : Math.max(0, Math.min(1, steps(rank) / (double) span));
    }

    public static int rank(ShopZones.Zone z) {
        return z == null ? 0 : z.tierInfo().rank();
    }

    /** Multiplier on the perceived price ratio used for the buy decision (1 = unchanged). */
    public static double perceivedMult(ShopZones.Zone z) {
        EconomyConfig c = EconomyConfig.INSTANCE;
        return 1 + Math.min(Math.max(0, c.tierPriceToleranceMax), Math.max(0, c.tierPriceToleranceStep) * steps(rank(z)));
    }

    /** Multiplier on a haggler's price ceiling (1 = unchanged, lower = stingier). */
    public static double haggleCeilMult(ShopZones.Zone z) {
        EconomyConfig c = EconomyConfig.INSTANCE;
        return 1 - Math.min(Math.max(0, c.tierHaggleCeilingMax), Math.max(0, c.tierHaggleCeilingStep) * steps(rank(z)));
    }

    /** Extra thief spawn weight (added after the guard/detector floor). */
    public static double thiefWeightAdd(ShopZones.Zone z) {
        return Math.max(0, EconomyConfig.INSTANCE.tierThiefWeightMax) * scale(rank(z));
    }

    /** 0..tierThiefTierShift: how much the thief tier roll leans toward the stronger tiers. */
    public static double thiefTierShift(ShopZones.Zone z) {
        return Math.max(0, EconomyConfig.INSTANCE.tierThiefTierShift) * scale(rank(z));
    }
}

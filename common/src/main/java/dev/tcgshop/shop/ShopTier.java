package dev.tcgshop.shop;

import org.jetbrains.annotations.Nullable;

/**
 * Shop block tiers: zone side in blocks, protected height above the block, price in ₽,
 * base capacity (customers inside at once, see ShopMissions.capacity) and material color. Below, ShopZones.BELOW blocks are always protected.
 *
 * 1.0: 12 tiers (Paper 5 ... Netherite 100). The enum order is the tier rank (Paper = 0 ... Netherite = 11) and the
 * zones store the rank in memory ({@link ShopZones.Zone#tier}); on disk they store the tier KEY ("tier_key") plus the
 * zone side ("size"), and the old "tier" int keeps its pre-1.0 meaning (index in {@link #LEGACY}), see ShopZones.Zone.
 * Always compare tiers with {@link #rank()} / {@link #atLeast(ShopTier)}, never with hard-coded numbers.
 */
public enum ShopTier {
    // key, side, height above, price, base capacity, color, customer spawn multiplier, max selling displays (0 = no limit)
    PAPER("paper", 5, 4, 3_000, 1, 0xFFE9DFC4, 0.25, 1),
    WOOD("wood", 10, 6, 10_000, 2, 0xFF9C6B3C, 1.0, 0),
    BRICK("brick", 16, 7, 25_000, 3, 0xFFA4553E, 1.0, 0),
    STONE("stone", 24, 8, 45_000, 4, 0xFF8A8A8A, 1.0, 0),
    COPPER("copper", 32, 9, 75_000, 5, 0xFFC8703F, 1.0, 0),
    IRON("iron", 40, 10, 110_000, 6, 0xFFD8D8D8, 1.0, 0),
    GOLD("gold", 48, 12, 250_000, 7, 0xFFF2C230, 1.0, 0),
    LAPIS("lapis", 56, 14, 380_000, 8, 0xFF2F5FC8, 1.0, 0),
    DIAMOND("diamond", 64, 16, 550_000, 9, 0xFF4EE2EC, 1.0, 0),
    AMETHYST("amethyst", 72, 18, 750_000, 10, 0xFFA066D8, 1.0, 0),
    EMERALD("emerald", 80, 20, 1_000_000, 11, 0xFF2ECC71, 1.0, 0),
    NETHERITE("netherite", 100, 24, 2_000_000, 14, 0xFF4A3F45, 1.0, 0);

    /** Pre-1.0 tier table: the old "tier" int saved in the zones is an index in this array. */
    private static final ShopTier[] LEGACY = {WOOD, STONE, IRON, GOLD, DIAMOND, EMERALD, NETHERITE};
    /** Zone side of each pre-1.0 tier (wood was 16×16 before 1.0): existing zones keep it. */
    private static final int[] LEGACY_SIZE = {16, 24, 32, 48, 64, 80, 100};

    public final String key;
    public final int size;
    public final int up;
    public final long price;
    public final int maxCustomers;
    public final int color;
    /** Multiplier for the customer arrival chance (ShopLife.spawnCustomers): the Paper stall gets very few people. */
    public final double spawnMult;
    /** Maximum selling displays inside the zone (0 = no limit). Home displays do not count. */
    public final int maxDisplays;

    ShopTier(String key, int size, int up, long price, int maxCustomers, int color, double spawnMult, int maxDisplays) {
        this.key = key;
        this.size = size;
        this.up = up;
        this.price = price;
        this.maxCustomers = maxCustomers;
        this.color = color;
        this.spawnMult = spawnMult;
        this.maxDisplays = maxDisplays;
    }

    /** Next tier (or null if already the maximum). */
    public ShopTier next() {
        return ordinal() + 1 < values().length ? values()[ordinal() + 1] : null;
    }

    /** Tier rank: Paper = 0 ... Netherite = 11. */
    public int rank() {
        return ordinal();
    }

    /** Is this tier the same as or above {@code other}? */
    public boolean atLeast(ShopTier other) {
        return other == null || rank() >= other.rank();
    }

    /** Tier by key ("wood") or enum name ("WOOD"), ignoring case. Null if unknown. */
    @Nullable
    public static ShopTier byKey(String key) {
        if (key == null) return null;
        for (ShopTier t : values()) if (t.key.equalsIgnoreCase(key) || t.name().equalsIgnoreCase(key)) return t;
        return null;
    }

    /** Tier by rank, clamped to the valid range (never null). */
    public static ShopTier byRank(int rank) {
        ShopTier[] v = values();
        return v[Math.max(0, Math.min(v.length - 1, rank))];
    }

    /** Tier of a pre-1.0 save ("tier" int = index in the old 7-tier table), clamped. */
    public static ShopTier fromLegacy(int legacyIndex) {
        return LEGACY[Math.max(0, Math.min(LEGACY.length - 1, legacyIndex))];
    }

    /** Zone side a pre-1.0 zone had with that old index (wood 16, stone 24, iron 32, gold 48, diamond 64, emerald 80, netherite 100). */
    public static int legacySize(int legacyIndex) {
        return LEGACY_SIZE[Math.max(0, Math.min(LEGACY_SIZE.length - 1, legacyIndex))];
    }

    /** Old "tier" int written next to the key so a pre-1.0 build can still read the save: the highest old tier at or below this one. */
    public int legacyIndex() {
        int best = 0;
        for (int i = 0; i < LEGACY.length; i++) if (LEGACY[i].rank() <= rank()) best = i;
        return best;
    }

    /** One shop per player until they own a shop of this tier or higher (then they may own more). */
    public static final ShopTier MULTI_SHOP_TIER = AMETHYST;

    /** Can a player whose best shop is {@code highestOwned} own more than one shop? (true from Amethyst). Null = owns none. */
    public static boolean canOwnMultipleShops(@Nullable ShopTier highestOwned) {
        return highestOwned != null && highestOwned.atLeast(MULTI_SHOP_TIER);
    }

    /** Base capacity of a zone of this side: the best capacity among the tiers that fit in it (keeps old, bigger zones' capacity). */
    public static int capacityForSize(int side) {
        int best = 0;
        for (ShopTier t : values()) if (t.size <= side) best = Math.max(best, t.maxCustomers);
        return best;
    }
}

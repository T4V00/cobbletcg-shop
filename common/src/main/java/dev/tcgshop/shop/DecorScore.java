package dev.tcgshop.shop;

import dev.tcgshop.EconomyConfig;
import dev.tcgshop.compat.CobbleFurniesCompat;
import dev.tcgshop.luxury.LuxuryShop;
import dev.tcgshop.luxury.StatueTier;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

/**
 * 1.1 (customers update): the shop's "decoration liking" (0-100 %), shown as the Decoration bar of the Manager Goggles
 * and in every decor item's tooltip.
 * <p>
 * Every decor object placed in the shop (furniture, plushies, statues, posters, play tables, displays...) adds
 * {@link #points decoration points}; many copies of the same object count less and less ({@link #copyWeight}). The
 * liking is {@code 100 * (1 - exp(-points / k))}, where k grows with the shop's side ({@link #k}): the first pieces
 * raise it quickly, the last percents need a lot of decor (it is meant to be hard to max, so buying decor pays off).
 * <p>
 * ShopLife.recompute stores the result in the zone factors (hidden "m_" keys, saved and synced): {@code m_decor_pct},
 * {@code m_decor_pts} (x10) and {@code m_statue} (best luxury statue tier in the shop, 0 = none). Other systems can give
 * their own blocks a value with {@link #register}.
 */
public final class DecorScore {
    private DecorScore() {}

    /** Points by luxury statue tier (index 1..7). */
    private static final int[] STATUE_POINTS = {0, 6, 9, 13, 18, 24, 32, 42};
    /** Explicit values (block / item id -> points), e.g. new decor blocks of other features. */
    private static final Map<ResourceLocation, Integer> EXTRA = new HashMap<>();

    public static void register(ResourceLocation id, int points) {
        EXTRA.put(id, Math.max(0, points));
    }

    /** Decoration points of a placed block / its item (same registry id). 0 = not decoration. */
    public static int points(@Nullable ResourceLocation id) {
        if (id == null) return 0;
        Integer x = EXTRA.get(id);
        if (x != null) return x;
        String ns = id.getNamespace(), path = id.getPath();
        if ("minecraft".equals(ns)) return 0;
        StatueTier st = LuxuryShop.tierOf(id);
        if (st != null) return STATUE_POINTS[Math.max(0, Math.min(STATUE_POINTS.length - 1, st.index()))];
        if (path.equals("statue_part")) return 0;
        if (dev.tcgshop.TcgShop.MOD_ID.equals(ns)) return ownPoints(id, path);
        if (CobbleFurniesCompat.isPlushBlock(id)) return 2;
        if (CobbleFurniesCompat.isFurnitureBlock(id) || CobbleFurniesCompat.isFurniture(id)) {
            return switch (FurnitureShop.category(id)) {
                case "decor" -> 3;
                case "plants", "lighting", "storage" -> 2;
                case "building" -> 0;
                default -> 1;
            };
        }
        if (path.contains("statue") || path.contains("trophy")) return 3;
        return 0;
    }

    private static int ownPoints(ResourceLocation id, String path) {
        Block b = BuiltInRegistries.BLOCK.containsKey(id) ? BuiltInRegistries.BLOCK.get(id) : null;
        if (b instanceof ShopDisplayBlock d) {
            if (!d.shopOnly()) return 0; // home displays are not shop decor
            if (b == TcgBlocks.PEDESTAL) return 3;
            if (b == TcgBlocks.SHOWCASE || b == TcgBlocks.CARD_WALL || path.contains("rgb")) return 2;
            return 1; // shelves, stands, ledges...
        }
        if (b == TcgBlocks.PLAY_TABLE) return 3;
        if (b == TcgBlocks.POSTER || b == TcgBlocks.ROULETTE) return 2;
        if (b == TcgBlocks.CASH_REGISTER || b == TcgBlocks.THEFT_DETECTOR) return 0;
        for (String w : new String[]{"casino", "slot_machine", "blackjack", "plush", "plant", "lamp", "decor", "rug", "banner", "painting", "neon"})
            if (path.contains(w)) return 2;
        if (path.contains("trash_can")) return 1;
        return 0;
    }

    public static int points(ItemStack st) {
        return st == null || st.isEmpty() ? 0 : points(BuiltInRegistries.ITEM.getKey(st.getItem()));
    }

    /** Weight of the n-th copy (1-based) of the same object: 3 full, then halves, then quarters. */
    public static double copyWeight(int nth) {
        return nth <= 3 ? 1.0 : nth <= 8 ? 0.5 : 0.25;
    }

    /** Points needed for ~63 % in a shop of that side (bigger shops need more decor). */
    public static double k(int zoneSize) {
        return (25 + 2.5 * Math.max(4, zoneSize)) * Math.max(0.1, EconomyConfig.INSTANCE.decorDifficulty);
    }

    /** Decoration liking 0-100 for those points in a shop of that side. */
    public static int pct(double points, int zoneSize) {
        if (points <= 0) return 0;
        return (int) Math.round(100 * (1 - Math.exp(-points / k(zoneSize))));
    }

    /** Exact liking (0-100, with decimals) for the tooltips. */
    public static double pctExact(double points, int zoneSize) {
        return pctExactK(points, k(zoneSize));
    }

    /** Exact liking for a given k (the server's, synced to the owner: decorDifficulty is a server setting). */
    public static double pctExactK(double points, double k) {
        return points <= 0 ? 0 : 100 * (1 - Math.exp(-points / Math.max(0.1, k)));
    }

    /** Weighted points one more copy of an object adds when the shop already has {@code copies} of it. */
    public static double addedPoints(int points, int copies) {
        return points * copyWeight(Math.max(0, copies) + 1);
    }

    /**
     * 1.1: is this block the secondary half of a 2-block object (the head of a bed, the upper half of tall furniture)?
     * Those are skipped so the object counts once.
     */
    public static boolean isSecondaryPart(net.minecraft.world.level.block.state.BlockState s) {
        for (net.minecraft.world.level.block.state.properties.Property<?> pr : s.getProperties()) {
            String n = pr.getName(), v = String.valueOf(s.getValue(pr)).toLowerCase(java.util.Locale.ROOT);
            if (n.equals("half") && v.equals("upper")) return true;
            if (n.equals("part") && v.equals("head")) return true;
        }
        return false;
    }

    /** Reputation factor "decor" (-3 .. 15) for a liking. */
    public static int repFactor(int pct) {
        return (int) Math.round(-3 + 18 * Math.sqrt(Math.max(0, Math.min(100, pct)) / 100.0));
    }

    /** Decoration liking of a zone (last recompute; 0 if unknown). */
    public static int zonePct(ShopZones.Zone z) {
        return z == null ? 0 : Math.max(0, Math.min(100, z.factors.getOrDefault("m_decor_pct", 0)));
    }

    /** Best luxury statue tier in the zone (1..7; 0 = none). */
    public static int zoneStatue(ShopZones.Zone z) {
        return z == null ? 0 : Math.max(0, z.factors.getOrDefault("m_statue", 0));
    }
}

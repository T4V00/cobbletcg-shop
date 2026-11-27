package dev.tcgshop.shop;

import dev.tcgshop.EconomyConfig;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * 1.1: reselling old shop cores (shop_core_&lt;tier&gt;, e.g. the one handed back when a shop is upgraded) through the phone's
 * quick sell and the Wholesaler, at {@link EconomyConfig#coreResaleRate} of what that core costs to buy
 * ({@link ShopServer#priceOf}, so an admin's itemPrices override is followed).
 * A core linked to a dormant shop ({@link TcgBlocks#SHOP_LINK}, Feature "dormant shops") is never bought while that shop
 * still exists: it is the owner's only way to restore it. A stale link (shop abandoned) sells like a plain core.
 * Tiers that can be crafted (Paper: data/tcgshop/recipe/shop_core_paper.json) are never bought, or crafting and reselling
 * them would turn the ingredients (a diamond) into money with no limit. The rate is capped at {@link #maxRate()} so that
 * buying a core during the biggest store sale and reselling it never makes a profit.
 * Shared by client (grid prices) and server (the sale itself).
 */
public final class CoreResale {
    private CoreResale() {}

    /** Is it a shop core item (any tier, linked or not)? */
    public static boolean isCore(ItemStack st) {
        return st != null && !st.isEmpty() && st.getItem() instanceof ShopCoreItem;
    }

    /** Zone id a linked core points at, or null for a plain core / any other item. */
    @Nullable
    public static UUID linkedZone(ItemStack st) {
        if (!isCore(st)) return null;
        CompoundTag l = st.get(TcgBlocks.SHOP_LINK);
        return l != null && l.hasUUID("zone") ? l.getUUID("zone") : null;
    }

    /** Core tiers that have a crafting recipe: never resold (no ingredients-to-money loop). */
    public static final java.util.Set<ShopTier> CRAFTED_TIERS = java.util.EnumSet.of(ShopTier.PAPER);

    /** Highest allowed resale rate: 1 minus the biggest store sale (Discounts.PERCENTS, 50 %), so no buy-low / resell loop. */
    public static double maxRate() {
        int max = 0;
        for (int p : Discounts.PERCENTS) max = Math.max(max, p);
        return Math.max(0, 1 - max / 100.0);
    }

    /** Resale rate (clamped to 0..maxRate; 0 = cores are not bought). */
    public static double rate() {
        return Math.max(0, Math.min(maxRate(), EconomyConfig.INSTANCE.coreResaleRate));
    }

    /** The tier of a core item, or null for any other item. */
    @Nullable
    public static ShopTier tierOf(ItemStack st) {
        return isCore(st) && ((ShopCoreItem) st.getItem()).getBlock() instanceof ShopCoreBlock b ? b.tier : null;
    }

    /** Can this core be resold at all (a core of a tier that cannot be crafted)? */
    public static boolean resellable(ItemStack st) {
        ShopTier t = tierOf(st);
        return t != null && !CRAFTED_TIERS.contains(t);
    }

    /** What one core of this tier pays (0 = not bought: not a core, a crafted tier, or resale off). */
    public static long unitPrice(ItemStack st) {
        if (!resellable(st) || rate() <= 0) return 0;
        return Math.max(1, Math.round(ShopServer.priceOf(st.copyWithCount(1), 1.0) * rate()));
    }

    /** Server: is this core still the key of a dormant shop (so it must not be sold)? */
    public static boolean lockedByDormantShop(MinecraftServer server, ItemStack st) {
        return dormantOwner(server, st) != null;
    }

    /** Server: owner of the dormant shop this core is still the key of, or null (plain core, stale link, active shop). */
    @Nullable
    public static UUID dormantOwner(MinecraftServer server, ItemStack st) {
        UUID id = linkedZone(st);
        if (id == null || server == null) return null;
        ShopOpenClose.Found f = ShopOpenClose.find(server, id);
        return f != null && f.zone().dormant ? f.zone().owner : null;
    }
}

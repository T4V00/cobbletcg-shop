package dev.tcgshop.compat;

import dev.masterball.api.platform.Platform;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Compatibility with CobbleFurnies (furniture, modid cobblefurnies) and Pokéblocks (plushies: items with "pokedoll" in the id).
 * No compile-time dependency: registry id only. If neither is installed, the furniture shop is
 * hidden (Furniture Vendor, Wholesaler tab and phone app).
 */
public final class CobbleFurniesCompat {
    public static final String FURNIES = "cobblefurnies", POKEBLOCKS = "pokeblocks";
    private static List<ResourceLocation> cache;

    private CobbleFurniesCompat() {}

    public static boolean furniesLoaded() {
        return Platform.INSTANCE.isModLoaded(FURNIES);
    }

    public static boolean plushLoaded() {
        return Platform.INSTANCE.isModLoaded(POKEBLOCKS);
    }

    /** Is there anything to sell? */
    public static boolean available() {
        return (furniesLoaded() || plushLoaded()) && !all().isEmpty();
    }

    public static boolean isFurniture(ResourceLocation id) {
        return FURNIES.equals(id.getNamespace());
    }

    public static boolean isPlush(ResourceLocation id) {
        return POKEBLOCKS.equals(id.getNamespace()) && id.getPath().contains("pokedoll");
    }

    /** 0.9.5: Lucky's Cozyhome Refurnished (chairs, sofas, armchairs...): its furniture is also used in the shop. */
    public static final String COZYHOME = "cozyhome";
    /** Building blocks from those mods (floors, wool, tiles...): not "furniture" to look at. */
    private static final String[] NOT_FURNITURE = {"carpet", "wool", "floor", "planks", "mosaic", "ceramic", "porcelain", "tile", "wall_support", "slab", "stairs"};

    /**
     * 0.9.5: Is it a placed furniture block from CobbleFurnies or Cozyhome that a customer would stop to look at?
     * Registry id only.
     */
    public static boolean isFurnitureBlock(ResourceLocation id) {
        if (id == null || !(FURNIES.equals(id.getNamespace()) || COZYHOME.equals(id.getNamespace()))) return false;
        String p = id.getPath();
        for (String w : NOT_FURNITURE) if (p.contains(w)) return false;
        return true;
    }

    /** 0.9.5: Is it a placed plushie? (Pokéblocks "pokedoll" or any block with "plush" in its name). */
    public static boolean isPlushBlock(ResourceLocation id) {
        return id != null && (isPlush(id) || (!"minecraft".equals(id.getNamespace()) && id.getPath().contains("plush")));
    }

    /** Can it be bought/sold in the furniture shop? */
    public static boolean tradeable(ResourceLocation id) {
        return id != null && (isFurniture(id) || isPlush(id)) && BuiltInRegistries.ITEM.containsKey(id) && BuiltInRegistries.ITEM.get(id) != Items.AIR;
    }

    public static boolean tradeable(ItemStack st) {
        return !st.isEmpty() && tradeable(BuiltInRegistries.ITEM.getKey(st.getItem()));
    }

    /** All registered furniture and plushies (furniture first, then plushies; alphabetical order). */
    public static List<ResourceLocation> all() {
        if (cache != null) return cache;
        List<ResourceLocation> furn = new ArrayList<>(), plush = new ArrayList<>();
        for (ResourceLocation id : BuiltInRegistries.ITEM.keySet()) {
            Item it = BuiltInRegistries.ITEM.get(id);
            if (it == Items.AIR) continue;
            if (isFurniture(id)) furn.add(id);
            else if (isPlush(id)) plush.add(id);
        }
        furn.sort(java.util.Comparator.comparing(ResourceLocation::getPath));
        plush.sort(java.util.Comparator.comparing(ResourceLocation::getPath));
        List<ResourceLocation> out = new ArrayList<>(furn);
        out.addAll(plush);
        List<ResourceLocation> res = Collections.unmodifiableList(out);
        if (!res.isEmpty()) cache = res; // a still-empty registry is not cached
        return res;
    }
}

package dev.tcgshop.compat;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

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
    private static List<Identifier> cache;

    private CobbleFurniesCompat() {}

    public static boolean furniesLoaded() {
        return FabricLoader.getInstance().isModLoaded(FURNIES);
    }

    public static boolean plushLoaded() {
        return FabricLoader.getInstance().isModLoaded(POKEBLOCKS);
    }

    /** Is there anything to sell? */
    public static boolean available() {
        return (furniesLoaded() || plushLoaded()) && !all().isEmpty();
    }

    public static boolean isFurniture(Identifier id) {
        return FURNIES.equals(id.getNamespace());
    }

    public static boolean isPlush(Identifier id) {
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
    public static boolean isFurnitureBlock(Identifier id) {
        if (id == null || !(FURNIES.equals(id.getNamespace()) || COZYHOME.equals(id.getNamespace()))) return false;
        String p = id.getPath();
        for (String w : NOT_FURNITURE) if (p.contains(w)) return false;
        return true;
    }

    /** 0.9.5: Is it a placed plushie? (Pokéblocks "pokedoll" or any block with "plush" in its name). */
    public static boolean isPlushBlock(Identifier id) {
        return id != null && (isPlush(id) || (!"minecraft".equals(id.getNamespace()) && id.getPath().contains("plush")));
    }

    /** Can it be bought/sold in the furniture shop? */
    public static boolean tradeable(Identifier id) {
        return id != null && (isFurniture(id) || isPlush(id)) && Registries.ITEM.containsId(id) && Registries.ITEM.get(id) != Items.AIR;
    }

    public static boolean tradeable(ItemStack st) {
        return !st.isEmpty() && tradeable(Registries.ITEM.getId(st.getItem()));
    }

    /** All registered furniture and plushies (furniture first, then plushies; alphabetical order). */
    public static List<Identifier> all() {
        if (cache != null) return cache;
        List<Identifier> furn = new ArrayList<>(), plush = new ArrayList<>();
        for (Identifier id : Registries.ITEM.getIds()) {
            Item it = Registries.ITEM.get(id);
            if (it == Items.AIR) continue;
            if (isFurniture(id)) furn.add(id);
            else if (isPlush(id)) plush.add(id);
        }
        furn.sort(java.util.Comparator.comparing(Identifier::getPath));
        plush.sort(java.util.Comparator.comparing(Identifier::getPath));
        List<Identifier> out = new ArrayList<>(furn);
        out.addAll(plush);
        List<Identifier> res = Collections.unmodifiableList(out);
        if (!res.isEmpty()) cache = res; // a still-empty registry is not cached
        return res;
    }
}

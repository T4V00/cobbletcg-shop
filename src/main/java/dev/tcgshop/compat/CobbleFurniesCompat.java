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
 * Compatibilidad con CobbleFurnies (muebles, modid cobblefurnies) y Pokéblocks (peluches: objetos con "pokedoll" en el id).
 * Sin dependencia de compilación: sólo por id de registro. Si ninguno de los dos está instalado, la tienda de muebles se
 * oculta (Vendedor de muebles, pestaña del Mayorista y app del teléfono).
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

    /** ¿Hay algo que vender? */
    public static boolean available() {
        return (furniesLoaded() || plushLoaded()) && !all().isEmpty();
    }

    public static boolean isFurniture(Identifier id) {
        return FURNIES.equals(id.getNamespace());
    }

    public static boolean isPlush(Identifier id) {
        return POKEBLOCKS.equals(id.getNamespace()) && id.getPath().contains("pokedoll");
    }

    /** 0.9.5: Lucky's Cozyhome Refurnished (sillas, sofás, sillones...): sus muebles también se usan en la tienda. */
    public static final String COZYHOME = "cozyhome";
    /** Bloques de construcción de esos mods (suelos, lana, azulejos...): no son "muebles" que mirar. */
    private static final String[] NOT_FURNITURE = {"carpet", "wool", "floor", "planks", "mosaic", "ceramic", "porcelain", "tile", "wall_support", "slab", "stairs"};

    /**
     * 0.9.5: ¿Es un mueble colocado (bloque) de CobbleFurnies o Cozyhome al que un cliente se pararía a mirar?
     * Sólo por id de registro.
     */
    public static boolean isFurnitureBlock(Identifier id) {
        if (id == null || !(FURNIES.equals(id.getNamespace()) || COZYHOME.equals(id.getNamespace()))) return false;
        String p = id.getPath();
        for (String w : NOT_FURNITURE) if (p.contains(w)) return false;
        return true;
    }

    /** 0.9.5: ¿Es un peluche colocado? (Pokéblocks "pokedoll" o cualquier bloque con "plush" en el nombre). */
    public static boolean isPlushBlock(Identifier id) {
        return id != null && (isPlush(id) || (!"minecraft".equals(id.getNamespace()) && id.getPath().contains("plush")));
    }

    /** ¿Se puede comprar/vender en la tienda de muebles? */
    public static boolean tradeable(Identifier id) {
        return id != null && (isFurniture(id) || isPlush(id)) && Registries.ITEM.containsId(id) && Registries.ITEM.get(id) != Items.AIR;
    }

    public static boolean tradeable(ItemStack st) {
        return !st.isEmpty() && tradeable(Registries.ITEM.getId(st.getItem()));
    }

    /** Todos los muebles y peluches registrados (muebles primero, luego peluches; orden alfabético). */
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
        if (!res.isEmpty()) cache = res; // no se cachea un registro aún vacío
        return res;
    }
}

package dev.tcgshop.collection;

import dev.tcgshop.card.CardInfo;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Cartas del Binder General como "casillas" extra para todo lo que antes sólo usaba el inventario (Coleccionista,
 * gradeo, Mayorista, intercambios entre jugadores...). Casillas 0-35 = inventario; 1000+i = la carta i del Binder
 * (ids ordenados alfabéticamente, igual en cliente y servidor). Siempre se revalida en el servidor al usarla.
 */
public final class BinderSlots {
    private BinderSlots() {}

    public static final int BASE = 1000;

    public static boolean isBinder(int slot) { return slot >= BASE; }

    /** Ids del Binder con al menos una copia, en orden estable. */
    public static List<String> ids(Map<String, Integer> counts) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, Integer> e : counts.entrySet()) if (e.getValue() != null && e.getValue() > 0) out.add(e.getKey());
        Collections.sort(out);
        return out;
    }

    public static ItemStack stackFor(String id, int count) {
        Identifier ident = Identifier.tryParse(id);
        Item item = ident == null ? null : CardInfo.item(ident);
        return item == null || count <= 0 ? ItemStack.EMPTY : new ItemStack(item, count);
    }

    // ------------------------------------------------------------------ servidor

    private static String binderId(ServerPlayerEntity p, int slot) {
        BinderData b = CollectionService.ownedBinder(p);
        if (b == null) return null;
        List<String> ids = ids(b.counts);
        int i = slot - BASE;
        return i >= 0 && i < ids.size() ? ids.get(i) : null;
    }

    /** Lo que hay en esa casilla (copia de vista para el Binder: no modificar). */
    public static ItemStack peek(ServerPlayerEntity p, int slot) {
        if (slot >= 0 && slot < 36) return p.getInventory().getStack(slot);
        if (!isBinder(slot)) return ItemStack.EMPTY;
        BinderData b = CollectionService.ownedBinder(p);
        String id = binderId(p, slot);
        return b == null || id == null ? ItemStack.EMPTY : stackFor(id, b.count(id));
    }

    /** Saca n cartas de esa casilla (del inventario o del Binder) y las devuelve. */
    public static ItemStack take(ServerPlayerEntity p, int slot, int n) {
        if (slot >= 0 && slot < 36) return p.getInventory().getStack(slot).split(n);
        if (!isBinder(slot)) return ItemStack.EMPTY;
        BinderData b = CollectionService.ownedBinder(p);
        String id = binderId(p, slot);
        if (b == null || id == null) return ItemStack.EMPTY;
        int got = b.remove(id, Math.max(1, n));
        CollectionService.dirty(p);
        return stackFor(id, got);
    }

    /** ¿Tiene esta carta exacta (sin gradear) en el Binder? Devuelve su casilla o -1. */
    public static int findInBinder(ServerPlayerEntity p, ItemStack want) {
        if (!CardInfo.isCard(want) || dev.tcgshop.grading.Grading.isGraded(want)) return -1;
        BinderData b = CollectionService.ownedBinder(p);
        if (b == null) return -1;
        String id = CardInfo.id(want).toString();
        if (b.count(id) <= 0) return -1;
        return BASE + ids(b.counts).indexOf(id);
    }

    /** Tras sacar cartas del Binder: guardar y avisar al cliente. */
    public static void sync(ServerPlayerEntity p) {
        CollectionService.dirty(p);
        CollectionService.sync(p);
    }
}

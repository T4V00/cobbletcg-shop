package dev.tcgshop.collection;

import dev.masterball.api.card.CardInfo;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * General Binder cards as extra "slots" for everything that used to use only the inventory (Collector,
 * grading, Wholesaler, player-to-player trades...). Slots 0-35 = inventory; 1000+i = card i of the Binder
 * (ids sorted alphabetically, same on client and server). Always revalidated on the server when used.
 */
public final class BinderSlots {
    private BinderSlots() {}

    public static final int BASE = 1000;

    public static boolean isBinder(int slot) { return slot >= BASE; }

    /** Binder ids with at least one copy, in stable order. */
    public static List<String> ids(Map<String, Integer> counts) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, Integer> e : counts.entrySet()) if (e.getValue() != null && e.getValue() > 0) out.add(e.getKey());
        Collections.sort(out);
        return out;
    }

    public static ItemStack stackFor(String id, int count) {
        ResourceLocation ident = ResourceLocation.tryParse(id);
        Item item = ident == null ? null : CardInfo.item(ident);
        return item == null || count <= 0 ? ItemStack.EMPTY : new ItemStack(item, count);
    }

    // ------------------------------------------------------------------ server

    private static String binderId(ServerPlayer p, int slot) {
        BinderData b = CollectionService.ownedBinder(p);
        if (b == null) return null;
        List<String> ids = ids(b.counts);
        int i = slot - BASE;
        return i >= 0 && i < ids.size() ? ids.get(i) : null;
    }

    /** What is in that slot (view copy for the Binder: do not modify). */
    public static ItemStack peek(ServerPlayer p, int slot) {
        if (slot >= 0 && slot < 36) return p.getInventory().getItem(slot);
        if (!isBinder(slot)) return ItemStack.EMPTY;
        BinderData b = CollectionService.ownedBinder(p);
        String id = binderId(p, slot);
        return b == null || id == null ? ItemStack.EMPTY : stackFor(id, b.count(id));
    }

    /** Takes n cards out of that slot (from the inventory or the Binder) and returns them. */
    public static ItemStack take(ServerPlayer p, int slot, int n) {
        if (slot >= 0 && slot < 36) return p.getInventory().getItem(slot).split(n);
        if (!isBinder(slot)) return ItemStack.EMPTY;
        BinderData b = CollectionService.ownedBinder(p);
        String id = binderId(p, slot);
        if (b == null || id == null) return ItemStack.EMPTY;
        int got = b.remove(id, Math.max(1, n));
        CollectionService.dirty(p);
        return stackFor(id, got);
    }

    /** Does the Binder hold this exact (ungraded) card? Returns its slot or -1. */
    public static int findInBinder(ServerPlayer p, ItemStack want) {
        if (!CardInfo.isCard(want) || dev.tcgshop.grading.Grading.isGraded(want)) return -1;
        BinderData b = CollectionService.ownedBinder(p);
        if (b == null) return -1;
        String id = CardInfo.id(want).toString();
        if (b.count(id) <= 0) return -1;
        return BASE + ids(b.counts).indexOf(id);
    }

    /** After taking cards out of the Binder: save and notify the client. */
    public static void sync(ServerPlayer p) {
        CollectionService.dirty(p);
        CollectionService.sync(p);
    }
}

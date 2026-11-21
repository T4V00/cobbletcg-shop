package dev.tcgshop.client.binder;

import dev.tcgshop.collection.BinderSlots;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Cliente: las cartas de tu Binder como casillas 1000+ (ver BinderSlots), para elegirlas igual que las del inventario. */
public final class ClientBinderSlots {
    private ClientBinderSlots() {}

    public static List<Integer> slots() {
        List<Integer> out = new ArrayList<>();
        List<String> ids = BinderSlots.ids(ClientCollection.counts);
        for (int i = 0; i < ids.size(); i++) out.add(BinderSlots.BASE + i);
        return out;
    }

    /** Lo que hay en una casilla (inventario o Binder). */
    public static ItemStack stack(PlayerEntity p, int slot) {
        if (slot >= 0 && slot < 36) return p == null ? ItemStack.EMPTY : p.getInventory().getStack(slot);
        if (!BinderSlots.isBinder(slot)) return ItemStack.EMPTY;
        List<String> ids = BinderSlots.ids(ClientCollection.counts);
        int i = slot - BinderSlots.BASE;
        if (i < 0 || i >= ids.size()) return ItemStack.EMPTY;
        return BinderSlots.stackFor(ids.get(i), ClientCollection.count(ids.get(i)));
    }
}

package dev.tcgshop.binder;

import dev.tcgshop.collection.BinderTag;
import dev.tcgshop.compat.AccessoriesCompat;
import dev.tcgshop.item.TcgItems;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Finds a player's Personal Binder wherever it is: the given hand, then main hand, off hand, inventory and
 * Accessories slots (1.0: the starter binder lives in the accessory slot). Same order on client and server, so the
 * screen and the server always work on the same stack. Stacks returned are live (modifying them modifies the item).
 */
public final class PersonalBinders {
    private PersonalBinders() {}

    /** All Personal Binders the player carries, in lookup order. */
    public static List<ItemStack> carried(PlayerEntity player, @Nullable Hand hand) {
        List<ItemStack> out = new ArrayList<>();
        if (hand != null) add(out, player.getStackInHand(hand));
        add(out, player.getMainHandStack());
        add(out, player.getOffHandStack());
        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < inv.size(); i++) add(out, inv.getStack(i));
        for (ItemStack st : AccessoriesCompat.equipped(player, TcgItems.PERSONAL_BINDER)) add(out, st);
        return out;
    }

    private static void add(List<ItemStack> out, ItemStack st) {
        if (!st.isOf(TcgItems.PERSONAL_BINDER)) return;
        for (ItemStack o : out) if (o == st) return;
        out.add(st);
    }

    /** The player's own Personal Binder (bound to them), or EMPTY. */
    public static ItemStack owned(PlayerEntity player, @Nullable Hand hand) {
        for (ItemStack st : carried(player, hand)) {
            BinderTag tag = st.get(TcgItems.BINDER_TAG);
            if (tag != null && tag.owner().equals(player.getUuid())) return st;
        }
        return ItemStack.EMPTY;
    }

    /** The player's own Personal Binder or, failing that, an unbound one (the caller binds it). EMPTY if none. */
    public static ItemStack ownedOrUnbound(PlayerEntity player, @Nullable Hand hand) {
        ItemStack own = owned(player, hand);
        if (!own.isEmpty()) return own;
        for (ItemStack st : carried(player, hand)) if (st.get(TcgItems.BINDER_TAG) == null) return st;
        return ItemStack.EMPTY;
    }
}

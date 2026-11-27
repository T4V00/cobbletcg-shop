package dev.tcgshop.compat;

import dev.masterball.api.platform.Platform;
import dev.tcgshop.TcgShop;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Integration with Accessories (io.wispforest) via reflection. Works on client and server. */
public final class AccessoriesCompat {
    private static Method getOptionally, getEquipped, entryStack, entryRef, refName, refIndex, refGet, refSet, refOf;
    private static boolean tried;

    private AccessoriesCompat() {}

    private static boolean init() {
        if (!tried) {
            tried = true;
            if (Platform.INSTANCE.isModLoaded("accessories")) {
                try {
                    Class<?> cap = Class.forName("io.wispforest.accessories.api.AccessoriesCapability");
                    getOptionally = cap.getMethod("getOptionally", LivingEntity.class);
                    getEquipped = cap.getMethod("getEquipped", Item.class);
                    Class<?> entry = Class.forName("io.wispforest.accessories.api.slot.SlotEntryReference");
                    entryStack = entry.getMethod("stack");
                    try { // 1.1: slot references, to empty and refill a slot through Accessories (BinderKeep)
                        Class<?> ref = Class.forName("io.wispforest.accessories.api.slot.SlotReference");
                        entryRef = entry.getMethod("reference");
                        refName = ref.getMethod("slotName");
                        refIndex = ref.getMethod("slot");
                        refGet = ref.getMethod("getStack");
                        refSet = ref.getMethod("setStack", ItemStack.class);
                        refOf = ref.getMethod("of", LivingEntity.class, String.class, int.class);
                    } catch (Throwable t) {
                        entryRef = null;
                    }
                } catch (Throwable t) {
                    TcgShop.LOGGER.warn("Could not link with Accessories", t);
                    getOptionally = null;
                }
            }
        }
        return getOptionally != null;
    }

    /** Equipped stacks of that item in Accessories slots (live: modifying them modifies the equipped one). */
    public static List<ItemStack> equipped(LivingEntity entity, Item item) {
        List<ItemStack> out = new ArrayList<>();
        if (!init()) return out;
        try {
            Optional<?> cap = (Optional<?>) getOptionally.invoke(null, entity);
            if (cap.isEmpty()) return out;
            for (Object ref : (List<?>) getEquipped.invoke(cap.get(), item)) out.add((ItemStack) entryStack.invoke(ref));
        } catch (Throwable ignored) {
        }
        return out;
    }

    public static boolean isEquipped(LivingEntity entity, Item item) {
        return !equipped(entity, item).isEmpty();
    }

    /** 1.1: an item taken out of an Accessories slot (slot name + index, to put it back in the same place). */
    public record Taken(String slot, int index, ItemStack stack) {}

    /**
     * 1.1: takes every equipped stack of that item out of its Accessories slot through the slot reference (so Accessories
     * marks the container changed and syncs it). Falls back to emptying the live stack when the reference API is missing.
     */
    public static List<Taken> take(LivingEntity entity, Item item) {
        List<Taken> out = new ArrayList<>();
        if (!init()) return out;
        try {
            Optional<?> cap = (Optional<?>) getOptionally.invoke(null, entity);
            if (cap.isEmpty()) return out;
            for (Object e : (List<?>) getEquipped.invoke(cap.get(), item)) {
                ItemStack live = (ItemStack) entryStack.invoke(e);
                if (live == null || live.isEmpty()) continue;
                ItemStack copy = live.copy();
                String slot = "";
                int index = -1;
                boolean done = false;
                if (entryRef != null) {
                    try {
                        Object ref = entryRef.invoke(e);
                        slot = (String) refName.invoke(ref);
                        index = (Integer) refIndex.invoke(ref);
                        done = Boolean.TRUE.equals(refSet.invoke(ref, ItemStack.EMPTY));
                    } catch (Throwable t) {
                        slot = "";
                        index = -1;
                    }
                }
                if (!done) { live.setCount(0); slot = ""; index = -1; }
                out.add(new Taken(slot, index, copy));
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    /** 1.1: puts a stack back into that Accessories slot if it is empty. False when it could not (then use the inventory). */
    public static boolean putBack(LivingEntity entity, String slot, int index, ItemStack stack) {
        if (slot == null || slot.isEmpty() || index < 0 || !init() || refOf == null) return false;
        try {
            Object ref = refOf.invoke(null, entity, slot, index);
            ItemStack now = (ItemStack) refGet.invoke(ref);
            if (now != null && !now.isEmpty()) return false;
            return Boolean.TRUE.equals(refSet.invoke(ref, stack));
        } catch (Throwable t) {
            return false;
        }
    }
}

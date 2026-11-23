package dev.tcgshop.compat;

import dev.tcgshop.TcgShop;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Integration with Accessories (io.wispforest) via reflection. Works on client and server. */
public final class AccessoriesCompat {
    private static Method getOptionally, getEquipped, entryStack;
    private static boolean tried;

    private AccessoriesCompat() {}

    private static boolean init() {
        if (!tried) {
            tried = true;
            if (FabricLoader.getInstance().isModLoaded("accessories")) {
                try {
                    Class<?> cap = Class.forName("io.wispforest.accessories.api.AccessoriesCapability");
                    getOptionally = cap.getMethod("getOptionally", LivingEntity.class);
                    getEquipped = cap.getMethod("getEquipped", Item.class);
                    entryStack = Class.forName("io.wispforest.accessories.api.slot.SlotEntryReference").getMethod("stack");
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
}

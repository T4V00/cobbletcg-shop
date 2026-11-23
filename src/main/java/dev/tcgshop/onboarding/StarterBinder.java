package dev.tcgshop.onboarding;

import dev.tcgshop.TcgShop;
import dev.tcgshop.collection.BinderTag;
import dev.tcgshop.compat.AccessoriesCompat;
import dev.tcgshop.item.PersonalBinderItem;
import dev.tcgshop.item.TcgItems;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

import java.lang.reflect.Method;

/**
 * 1.0 starter binder: {@link PersonalBinderItem#createStarter} put straight into an Accessories slot (the binder is
 * tagged for the belt/charm slots in data/accessories/tags), via reflection so there is no compile-time dependency.
 * Without Accessories, or if no slot takes it, it goes to the inventory.
 */
public final class StarterBinder {
    private static Method get, equip;
    private static boolean tried;

    private StarterBinder() {}

    private static boolean init() {
        if (!tried) {
            tried = true;
            if (FabricLoader.getInstance().isModLoaded("accessories")) {
                try {
                    Class<?> cap = Class.forName("io.wispforest.accessories.api.AccessoriesCapability");
                    get = cap.getMethod("get", LivingEntity.class);
                    equip = cap.getMethod("equipAccessory", ItemStack.class, boolean.class);
                } catch (Throwable t) {
                    TcgShop.LOGGER.warn("Could not link with Accessories to equip the starter binder", t);
                    get = null;
                }
            }
        }
        return get != null && equip != null;
    }

    /** Gives the player their starter binder. true if it ended up equipped in an Accessories slot. */
    public static boolean give(ServerPlayerEntity player) {
        ItemStack binder = PersonalBinderItem.createStarter(player);
        if (tryEquip(player, binder)) return true;
        player.getInventory().offerOrDrop(binder);
        return false;
    }

    /**
     * Tries to equip a COPY of the binder (never swapping out what is already worn). Whether it worked is checked by
     * looking for that exact binder (same binder id) among the equipped ones, so the original is only handed out when
     * it was not equipped: never two binders, never none.
     */
    private static boolean tryEquip(ServerPlayerEntity player, ItemStack binder) {
        if (!init()) return false;
        BinderTag tag = binder.get(TcgItems.BINDER_TAG);
        if (tag == null) return false;
        try {
            Object cap = get.invoke(null, player);
            if (cap == null) return false;
            equip.invoke(cap, binder.copy(), false);
        } catch (Throwable t) {
            TcgShop.LOGGER.warn("Could not equip the starter binder in an Accessories slot", t);
        }
        for (ItemStack st : AccessoriesCompat.equipped(player, TcgItems.PERSONAL_BINDER)) {
            BinderTag t = st.get(TcgItems.BINDER_TAG);
            if (t != null && t.id().equals(tag.id())) return true;
        }
        return false;
    }
}

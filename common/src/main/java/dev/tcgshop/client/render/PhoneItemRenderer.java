package dev.tcgshop.client.render;

import dev.masterball.api.platform.Platform;
import dev.masterball.api.platform.ClientPlatform;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.tcgshop.client.phone.PhoneClient;
import dev.tcgshop.item.PhoneItem;
import dev.tcgshop.item.TcgItems;
import dev.tcgshop.phone.PhoneTier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import java.util.List;

/**
 * 1.0 beta 3: the TCG phone in 3D. Each tier (PhoneTier) has its own model and texture (tools/gen_items_3d.py:
 * models/item/3d/tcg_phone_&lt;tier&gt;.json): body colour and material, metal frame, camera layout and a screen with the
 * app icons that tier unlocks. In the GUI it draws that tier's 16 px icon (models/item/icon/tcg_phone_&lt;tier&gt;.json).
 *
 * <p>Which tier: the tier belongs to the player. A phone in the local player's own inventory uses PhoneClient.tier
 * (instant after an upgrade); any other phone (other players' hands, ground, frames) uses the tier the server stamped
 * on the stack ({@link PhoneItem#TIER}); unstamped phones (creative tab, store previews) fall back to the local tier.</p>
 */
public final class PhoneItemRenderer implements ClientPlatform.ItemRenderer {
    private static final ResourceLocation[] MODELS = new ResourceLocation[PhoneTier.values().length];
    private static final ResourceLocation[] ICONS = new ResourceLocation[PhoneTier.values().length];

    private PhoneItemRenderer() {}

    static void register(List<ResourceLocation> extra) {
        for (PhoneTier t : PhoneTier.values()) {
            MODELS[t.ordinal()] = ToolItemRenderer.modelId("tcg_phone_" + t.key);
            ICONS[t.ordinal()] = ToolItemRenderer.iconId("tcg_phone_" + t.key);
            extra.add(MODELS[t.ordinal()]);
            extra.add(ICONS[t.ordinal()]);
        }
        ClientPlatform.INSTANCE.itemRenderer(TcgItems.TCG_PHONE, new PhoneItemRenderer());
    }

    /** Tier to draw for this phone stack. */
    public static int tierOf(ItemStack stack) {
        int local = PhoneClient.enabled ? PhoneClient.tier : PhoneTier.MAX_LEVEL; // disabled system: everyone is top tier
        if (inLocalInventory(stack)) return clamp(local);
        int stamped = PhoneItem.shownTier(stack);
        return stamped >= 0 ? stamped : clamp(local);
    }

    private static int clamp(int t) {
        return Math.max(0, Math.min(PhoneTier.MAX_LEVEL, t));
    }

    /** Identity check (no copies, no allocation): is this exact stack one of the local player's slots? */
    private static boolean inLocalInventory(ItemStack stack) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return false;
        Inventory inv = p.getInventory();
        for (ItemStack s : inv.items) if (s == stack) return true;
        for (ItemStack s : inv.offhand) if (s == stack) return true;
        return false;
    }

    @Override
    public void render(ItemStack stack, ItemDisplayContext mode, PoseStack matrices, MultiBufferSource vertexConsumers,
                       int light, int overlay) {
        int tier = tierOf(stack);
        ToolItemRenderer.draw(mode == ItemDisplayContext.GUI ? ICONS[tier] : MODELS[tier], stack, mode, matrices,
                vertexConsumers, light, overlay);
    }
}

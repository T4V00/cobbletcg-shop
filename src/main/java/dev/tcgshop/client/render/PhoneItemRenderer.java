package dev.tcgshop.client.render;

import dev.tcgshop.client.phone.PhoneClient;
import dev.tcgshop.item.PhoneItem;
import dev.tcgshop.item.TcgItems;
import dev.tcgshop.phone.PhoneTier;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

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
public final class PhoneItemRenderer implements BuiltinItemRendererRegistry.DynamicItemRenderer {
    private static final Identifier[] MODELS = new Identifier[PhoneTier.values().length];
    private static final Identifier[] ICONS = new Identifier[PhoneTier.values().length];

    private PhoneItemRenderer() {}

    static void register(List<Identifier> extra) {
        for (PhoneTier t : PhoneTier.values()) {
            MODELS[t.ordinal()] = ToolItemRenderer.modelId("tcg_phone_" + t.key);
            ICONS[t.ordinal()] = ToolItemRenderer.iconId("tcg_phone_" + t.key);
            extra.add(MODELS[t.ordinal()]);
            extra.add(ICONS[t.ordinal()]);
        }
        BuiltinItemRendererRegistry.INSTANCE.register(TcgItems.TCG_PHONE, new PhoneItemRenderer());
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
        ClientPlayerEntity p = MinecraftClient.getInstance().player;
        if (p == null) return false;
        PlayerInventory inv = p.getInventory();
        for (ItemStack s : inv.main) if (s == stack) return true;
        for (ItemStack s : inv.offHand) if (s == stack) return true;
        return false;
    }

    @Override
    public void render(ItemStack stack, ModelTransformationMode mode, MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                       int light, int overlay) {
        int tier = tierOf(stack);
        ToolItemRenderer.draw(mode == ModelTransformationMode.GUI ? ICONS[tier] : MODELS[tier], stack, mode, matrices,
                vertexConsumers, light, overlay);
    }
}

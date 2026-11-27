package dev.tcgshop.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.masterball.api.card.CardInfo;
import dev.tcgshop.client.render.ShopRenderers;
import dev.tcgshop.grading.Grading;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Graded cards are ALWAYS shown inside their rigid CobbleGrade slab (hand, inventory, frames, ground):
 * plastic, edges and a label with the brand and the grade. In the inventory the card shrinks a little so the label fits.
 * The cobbletcg card (builtin/entity model) is 0.85 tall in model space, centered at (0.5, 0.625, 0) and faces +Z.
 */
@Mixin(ItemRenderer.class)
public abstract class ItemRendererMixin {
    private static final float CARD_H = 0.85f;

    private static boolean slabbed(ItemStack stack) {
        return !stack.isEmpty() && Grading.isGraded(stack) && CardInfo.isCard(stack);
    }

    @Inject(method = "render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/resources/model/BakedModel;)V",
            at = @At("HEAD"))
    private void tcgshop$shrinkSlab(ItemStack stack, ItemDisplayContext mode, boolean leftHanded, PoseStack matrices, MultiBufferSource vcp,
                                    int light, int overlay, BakedModel model, CallbackInfo ci) {
        if (!slabbed(stack)) return;
        matrices.pushPose();
        if (mode == ItemDisplayContext.GUI) {
            matrices.translate(0, -0.07f, 0);
            matrices.scale(0.8f, 0.8f, 0.8f);
        }
    }

    @Inject(method = "render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/resources/model/BakedModel;)V",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;popPose()V"), require = 0)
    private void tcgshop$drawSlab(ItemStack stack, ItemDisplayContext mode, boolean leftHanded, PoseStack matrices, MultiBufferSource vcp,
                                  int light, int overlay, BakedModel model, CallbackInfo ci) {
        if (!slabbed(stack)) return;
        matrices.pushPose();
        matrices.translate(0.5f, 0.5f + 0.125f, 0f);
        matrices.mulPose(Axis.YP.rotationDegrees(180)); // the slab is drawn as seen from -Z
        ShopRenderers.DisplayRenderer.slab(matrices, vcp, stack, CARD_H, light, overlay, Grading.grade(stack));
        matrices.popPose();
    }

    @Inject(method = "render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/resources/model/BakedModel;)V",
            at = @At("RETURN"))
    private void tcgshop$popSlab(ItemStack stack, ItemDisplayContext mode, boolean leftHanded, PoseStack matrices, MultiBufferSource vcp,
                                 int light, int overlay, BakedModel model, CallbackInfo ci) {
        if (slabbed(stack)) matrices.popPose();
    }
}

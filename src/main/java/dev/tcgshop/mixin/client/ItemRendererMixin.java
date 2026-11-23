package dev.tcgshop.mixin.client;

import dev.masterball.api.card.CardInfo;
import dev.tcgshop.client.render.ShopRenderers;
import dev.tcgshop.grading.Grading;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.RotationAxis;
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

    @Inject(method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;IILnet/minecraft/client/render/model/BakedModel;)V",
            at = @At("HEAD"))
    private void tcgshop$shrinkSlab(ItemStack stack, ModelTransformationMode mode, boolean leftHanded, MatrixStack matrices, VertexConsumerProvider vcp,
                                    int light, int overlay, BakedModel model, CallbackInfo ci) {
        if (!slabbed(stack)) return;
        matrices.push();
        if (mode == ModelTransformationMode.GUI) {
            matrices.translate(0, -0.07f, 0);
            matrices.scale(0.8f, 0.8f, 0.8f);
        }
    }

    @Inject(method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;IILnet/minecraft/client/render/model/BakedModel;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;pop()V"), require = 0)
    private void tcgshop$drawSlab(ItemStack stack, ModelTransformationMode mode, boolean leftHanded, MatrixStack matrices, VertexConsumerProvider vcp,
                                  int light, int overlay, BakedModel model, CallbackInfo ci) {
        if (!slabbed(stack)) return;
        matrices.push();
        matrices.translate(0.5f, 0.5f + 0.125f, 0f);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180)); // the slab is drawn as seen from -Z
        ShopRenderers.DisplayRenderer.slab(matrices, vcp, stack, CARD_H, light, overlay, Grading.grade(stack));
        matrices.pop();
    }

    @Inject(method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;IILnet/minecraft/client/render/model/BakedModel;)V",
            at = @At("RETURN"))
    private void tcgshop$popSlab(ItemStack stack, ModelTransformationMode mode, boolean leftHanded, MatrixStack matrices, VertexConsumerProvider vcp,
                                 int light, int overlay, BakedModel model, CallbackInfo ci) {
        if (slabbed(stack)) matrices.pop();
    }
}

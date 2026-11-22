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
 * Las cartas gradeadas se ven SIEMPRE dentro de su funda rígida de CobbleGrade (mano, inventario, marcos, suelo):
 * plástico, cantos y etiqueta con la marca y la nota. En el inventario la carta se encoge un poco para que quepa la etiqueta.
 * La carta de cobbletcg (modelo builtin/entity) ocupa en el espacio del modelo 0,85 de alto, centrada en (0,5; 0,625; 0) y mira a +Z.
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
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180)); // la funda se dibuja vista desde -Z
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

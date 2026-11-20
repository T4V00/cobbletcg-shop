package dev.tcgshop.mixin.client;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(PlayerEntityRenderer.class)
public interface PlayerEntityRendererInvoker {
    @Invoker("renderLabelIfPresent")
    void tcgshop$renderLabel(AbstractClientPlayerEntity player, Text text, MatrixStack matrices, VertexConsumerProvider consumers, int light, float tickDelta);
}

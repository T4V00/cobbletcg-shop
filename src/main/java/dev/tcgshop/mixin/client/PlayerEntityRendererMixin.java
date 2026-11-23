package dev.tcgshop.mixin.client;

import dev.tcgshop.client.binder.ClientCollection;
import dev.tcgshop.collection.Titles;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Draws the player's TCG title ("Pikachu Fan"...) above their name. */
@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerEntityRendererMixin {
    @Unique
    private static boolean tcgshop$drawing;

    /** Minecraft does not draw your own player's label: in third person we draw your title anyway. */
    @Inject(method = "render(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("TAIL"))
    private void tcgshop$selfTitle(AbstractClientPlayerEntity player, float yaw, float tickDelta, MatrixStack matrices,
                                   VertexConsumerProvider consumers, int light, CallbackInfo ci) {
        net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
        if (player != mc.player || mc.options.getPerspective().isFirstPerson() || mc.options.hudHidden) return;
        String key = ClientCollection.playerTitles.get(player.getUuidAsString());
        if (key == null || key.isEmpty()) key = ClientCollection.title;
        if (key == null || key.isEmpty()) return;
        tcgshop$drawing = true;
        ((PlayerEntityRendererInvoker) this).tcgshop$renderLabel(player, Titles.display(key).copy().formatted(Formatting.GOLD, Formatting.ITALIC),
                matrices, consumers, light, tickDelta);
        tcgshop$drawing = false;
    }

    @Inject(method = "renderLabelIfPresent(Lnet/minecraft/client/network/AbstractClientPlayerEntity;Lnet/minecraft/text/Text;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;IF)V",
            at = @At("HEAD"))
    private void tcgshop$title(AbstractClientPlayerEntity player, Text text, MatrixStack matrices, VertexConsumerProvider consumers,
                               int light, float tickDelta, CallbackInfo ci) {
        if (tcgshop$drawing) return;
        String key = ClientCollection.playerTitles.get(player.getUuidAsString());
        if (key == null || key.isEmpty()) return;
        tcgshop$drawing = true;
        matrices.push();
        matrices.translate(0, 0.25f, 0);
        ((PlayerEntityRendererInvoker) this).tcgshop$renderLabel(player, Titles.display(key).copy().formatted(Formatting.GOLD, Formatting.ITALIC),
                matrices, consumers, light, tickDelta);
        matrices.pop();
        tcgshop$drawing = false;
    }
}

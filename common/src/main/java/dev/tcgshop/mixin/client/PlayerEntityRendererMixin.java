package dev.tcgshop.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.tcgshop.client.binder.ClientCollection;
import dev.tcgshop.collection.Titles;
import net.minecraft.ChatFormatting;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Draws the player's TCG title ("Pikachu Fan"...) above their name. */
@Mixin(PlayerRenderer.class)
public abstract class PlayerEntityRendererMixin {
    @Unique
    private static boolean tcgshop$drawing;

    /** Minecraft does not draw your own player's label: in third person we draw your title anyway. */
    @Inject(method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("TAIL"))
    private void tcgshop$selfTitle(AbstractClientPlayer player, float yaw, float tickDelta, PoseStack matrices,
                                   MultiBufferSource consumers, int light, CallbackInfo ci) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (player != mc.player || mc.options.getCameraType().isFirstPerson() || mc.options.hideGui) return;
        String key = ClientCollection.playerTitles.get(player.getStringUUID());
        if (key == null || key.isEmpty()) key = ClientCollection.title;
        if (key == null || key.isEmpty()) return;
        tcgshop$drawing = true;
        ((PlayerEntityRendererInvoker) this).tcgshop$renderLabel(player, Titles.display(key).copy().withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC),
                matrices, consumers, light, tickDelta);
        tcgshop$drawing = false;
    }

    @Inject(method = "renderNameTag(Lnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/network/chat/Component;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IF)V",
            at = @At("HEAD"))
    private void tcgshop$title(AbstractClientPlayer player, Component text, PoseStack matrices, MultiBufferSource consumers,
                               int light, float tickDelta, CallbackInfo ci) {
        if (tcgshop$drawing) return;
        String key = ClientCollection.playerTitles.get(player.getStringUUID());
        if (key == null || key.isEmpty()) return;
        tcgshop$drawing = true;
        matrices.pushPose();
        matrices.translate(0, 0.25f, 0);
        ((PlayerEntityRendererInvoker) this).tcgshop$renderLabel(player, Titles.display(key).copy().withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC),
                matrices, consumers, light, tickDelta);
        matrices.popPose();
        tcgshop$drawing = false;
    }
}

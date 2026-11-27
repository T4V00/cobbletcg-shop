package dev.tcgshop.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(PlayerRenderer.class)
public interface PlayerEntityRendererInvoker {
    @Invoker("renderNameTag")
    void tcgshop$renderLabel(AbstractClientPlayer player, Component text, PoseStack matrices, MultiBufferSource consumers, int light, float tickDelta);
}

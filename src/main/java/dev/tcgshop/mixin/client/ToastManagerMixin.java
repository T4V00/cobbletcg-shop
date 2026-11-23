package dev.tcgshop.mixin.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.tcgshop.client.render.RichAdvancementToast;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.toast.AdvancementToast;
import net.minecraft.client.toast.Toast;
import net.minecraft.client.toast.ToastManager;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Replaces Minecraft's advancement toast (title only) with one that also shows the description, and gives every toast
 * its own layer.
 *
 * <p>Why the layer: fills and text written to the shared GUI vertex buffer can stay pending (inside
 * DrawContext.draw(Runnable) batches, or when a screen/HUD mod writes to ctx.getVertexConsumers() directly) and are
 * only flushed at the end of the frame, while texture/sprite draws (like the background of vanilla toasts) go to the
 * screen immediately, with whatever depth-test state is current. So pending HUD/screen text was drawn AFTER the toast
 * background and showed through it. Before the toasts we flush everything pending and clear the depth buffer, so the
 * toasts are painted on top of all of it; after them we flush again so nothing later mixes with them.</p>
 */
@Mixin(ToastManager.class)
public abstract class ToastManagerMixin {
    @Shadow
    @Final
    private List<?> visibleEntries;

    @Inject(method = "add", at = @At("HEAD"), cancellable = true)
    private void tcgshop$richAdvancement(Toast toast, CallbackInfo ci) {
        // with Advancement Plaques installed we leave the toast to it (otherwise its plaques did not show)
        if (toast instanceof AdvancementToast adv && !net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("advancementplaques")) {
            ((ToastManager) (Object) this).add(new RichAdvancementToast(((AdvancementToastAccessor) adv).tcgshop$advancement()));
            ci.cancel();
        }
    }

    @Inject(method = "draw(Lnet/minecraft/client/gui/DrawContext;)V", at = @At("HEAD"))
    private void tcgshop$ownLayerStart(DrawContext context, CallbackInfo ci) {
        if (visibleEntries.isEmpty() || MinecraftClient.getInstance().options.hudHidden) return;
        context.draw(); // HUD / screen fills and text still in the batch go down now, under the toasts
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, MinecraftClient.IS_SYSTEM_MAC); // nothing below can poke through
    }

    @Inject(method = "draw(Lnet/minecraft/client/gui/DrawContext;)V", at = @At("TAIL"))
    private void tcgshop$ownLayerEnd(DrawContext context, CallbackInfo ci) {
        if (visibleEntries.isEmpty() || MinecraftClient.getInstance().options.hudHidden) return;
        context.draw();
    }
}

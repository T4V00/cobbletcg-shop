package dev.tcgshop.mixin.client;

import dev.tcgshop.client.render.RichAdvancementToast;
import net.minecraft.client.toast.AdvancementToast;
import net.minecraft.client.toast.Toast;
import net.minecraft.client.toast.ToastManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replaces Minecraft's advancement toast (title only) with one that also shows the description. */
@Mixin(ToastManager.class)
public abstract class ToastManagerMixin {
    @Inject(method = "add", at = @At("HEAD"), cancellable = true)
    private void tcgshop$richAdvancement(Toast toast, CallbackInfo ci) {
        // with Advancement Plaques installed we leave the toast to it (otherwise its plaques did not show)
        if (toast instanceof AdvancementToast adv && !net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("advancementplaques")) {
            ((ToastManager) (Object) this).add(new RichAdvancementToast(((AdvancementToastAccessor) adv).tcgshop$advancement()));
            ci.cancel();
        }
    }
}

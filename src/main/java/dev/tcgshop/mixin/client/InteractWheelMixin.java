package dev.tcgshop.mixin.client;

import dev.tcgshop.client.CobblemonWheelCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Rueda de interacción de jugador de Cobblemon: añade la opción de intercambiar cartas. */
@Pseudo
@Mixin(targets = "com.cobblemon.mod.common.client.gui.interact.wheel.InteractWheelGuiFactoryKt", remap = false)
public class InteractWheelMixin {
    @Inject(method = "createPlayerInteractGui", at = @At("RETURN"), require = 0, remap = false)
    private static void tcgshop$addCardTrade(CallbackInfoReturnable<Object> cir) {
        CobblemonWheelCompat.addCardTrade(cir.getReturnValue());
    }
}

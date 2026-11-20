package dev.tcgshop.mixin;

import dev.tcgshop.pack.PackCapture;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * cobbletcg entrega el contenido de los sobres con offerOrDrop. Durante una apertura controlada
 * (PackOpener) lo capturamos y cancelamos la inserción.
 */
@Mixin(PlayerInventory.class)
public abstract class PlayerInventoryMixin {
    @Shadow @Final public PlayerEntity player;

    @Inject(method = "offerOrDrop", at = @At("HEAD"), cancellable = true)
    private void tcgshop$capture(ItemStack stack, CallbackInfo ci) {
        if (player instanceof ServerPlayerEntity sp && PackCapture.onOffer(sp, stack)) ci.cancel();
    }
}

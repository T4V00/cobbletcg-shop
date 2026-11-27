package dev.tcgshop.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.1: the mod's binders never drop on death (dev.tcgshop.binder.BinderKeep). Runs at the very start of ServerPlayer.die
 * with an early priority, before vanilla, Accessories or gravestone mods collect the inventory. A death cancelled later in
 * die (NeoForge LivingDeathEvent, revive / totem mods) is caught at the end of the tick and the binders are handed back.
 */
@Mixin(value = ServerPlayer.class, priority = 500)
public abstract class ServerPlayerDeathMixin {
    @Inject(method = "die", at = @At("HEAD"))
    private void tcgshop$keepBinders(DamageSource source, CallbackInfo ci) {
        dev.tcgshop.binder.BinderKeep.onDeath((ServerPlayer) (Object) this);
    }
}

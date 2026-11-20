package dev.tcgshop.mixin;

import dev.tcgshop.EconomyConfig;
import net.minecraft.advancement.AdvancementDisplay;
import net.minecraft.advancement.AdvancementEntry;
import net.minecraft.advancement.PlayerAdvancementTracker;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Anuncia en el chat los logros de CobbleTCG Shop y de cobbletcg aunque su archivo no lo pida (por defecto sólo
 * los desafíos salían en el chat). Respeta la regla announceAdvancements y la opción announceAllAdvancements.
 */
@Mixin(PlayerAdvancementTracker.class)
public abstract class PlayerAdvancementTrackerMixin {
    @Shadow
    private ServerPlayerEntity owner;

    @Shadow
    public abstract net.minecraft.advancement.AdvancementProgress getProgress(AdvancementEntry advancement);

    @Inject(method = "grantCriterion", at = @At("RETURN"))
    private void tcgshop$announce(AdvancementEntry entry, String criterion, CallbackInfoReturnable<Boolean> cir) {
        if (!Boolean.TRUE.equals(cir.getReturnValue()) || owner == null || !EconomyConfig.INSTANCE.announceAllAdvancements) return;
        String ns = entry.id().getNamespace();
        if (!ns.equals("tcgshop") && !ns.equals("cobbletcg")) return;
        AdvancementDisplay d = entry.value().display().orElse(null);
        if (d == null || d.isHidden() || d.shouldAnnounceToChat()) return; // los que ya se anuncian solos, no repetir
        if (!getProgress(entry).isDone()) return;
        if (!owner.getServerWorld().getGameRules().getBoolean(GameRules.ANNOUNCE_ADVANCEMENTS)) return;
        owner.getServer().getPlayerManager().broadcast(d.getFrame().getChatAnnouncementText(entry, owner), false);
    }
}

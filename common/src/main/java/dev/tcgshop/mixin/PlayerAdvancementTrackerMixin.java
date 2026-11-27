package dev.tcgshop.mixin;

import dev.tcgshop.EconomyConfig;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Announces MasterBall Traders Co. advancements in chat even if their file does not ask for it (by default only
 * challenges showed up in chat). Respects the announceAdvancements rule and the announceAllAdvancements option.
 */
@Mixin(PlayerAdvancements.class)
public abstract class PlayerAdvancementTrackerMixin {
    @Shadow
    private ServerPlayer player;

    @Shadow
    public abstract net.minecraft.advancements.AdvancementProgress getOrStartProgress(AdvancementHolder advancement);

    @Inject(method = "award", at = @At("RETURN"))
    private void tcgshop$announce(AdvancementHolder entry, String criterion, CallbackInfoReturnable<Boolean> cir) {
        if (!Boolean.TRUE.equals(cir.getReturnValue()) || player == null || !EconomyConfig.INSTANCE.announceAllAdvancements) return;
        String ns = entry.id().getNamespace();
        if (!ns.equals("tcgshop")) return;
        DisplayInfo d = entry.value().display().orElse(null);
        if (d == null || d.isHidden() || d.shouldAnnounceChat()) return; // those already announced on their own: don't repeat
        if (!getOrStartProgress(entry).isDone()) return;
        if (!player.serverLevel().getGameRules().getBoolean(GameRules.RULE_ANNOUNCE_ADVANCEMENTS)) return;
        player.getServer().getPlayerList().broadcastSystemMessage(d.getType().createAnnouncement(entry, player), false);
    }
}

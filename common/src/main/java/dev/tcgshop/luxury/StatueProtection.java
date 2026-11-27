package dev.tcgshop.luxury;

import dev.masterball.api.platform.event.PlayerEvents;
import dev.tcgshop.shop.ShopZones;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Shop zone protection for multi-block statues. ShopProtection only checks the block being broken or
 * placed; a statue occupies several: the base and the parts above ({@link StatuePartBlock}). All of them are checked here.
 * Same rule as ShopProtection: only the owner builds inside their zone; creative + operator bypasses everything.
 */
public final class StatueProtection {
    private StatueProtection() {}

    private static boolean bypass(@Nullable Player p) {
        return p != null && p.isCreative() && p.hasPermissions(2);
    }

    /**
     * Someone else's zone that prevents that player from building/breaking at pos, or null if allowed (or off the server: the client has no
     * zones and the server is authoritative). player null (no player) = any zone prevents it.
     */
    @Nullable
    public static ShopZones.Zone blocking(Level world, @Nullable Player player, BlockPos pos) {
        if (!(world instanceof ServerLevel sw) || bypass(player)) return null;
        ShopZones.Zone z = ShopZones.get(sw).at(pos);
        if (z == null) return null;
        return player != null && z.owner.equals(player.getUUID()) ? null : z;
    }

    public static boolean canBuild(Level world, @Nullable Player player, BlockPos pos) {
        return blocking(world, player, pos) == null;
    }

    /** First foreign zone overlapped by the statue of that tier placed with its base at base (base and parts), or null. */
    @Nullable
    public static ShopZones.Zone blockingStatue(Level world, @Nullable Player player, BlockPos base, StatueTier tier) {
        for (int i = 0; i <= tier.parts; i++) {
            ShopZones.Zone z = blocking(world, player, base.above(i));
            if (z != null) return z;
        }
        return null;
    }

    public static void sendProtected(Player player, ShopZones.Zone z) {
        player.displayClientMessage(Component.translatable("tcgshop.shop.protected", z.ownerName).withStyle(ChatFormatting.RED), true);
    }

    /**
     * Breaking a part breaks (and drops) the base: if the base is in someone else's zone, the part cannot be broken even if it
     * is outside the zone (otherwise a statue could be taken out of a protected shop by breaking it from above).
     */
    static void register() {
        PlayerEvents.BREAK_BLOCK.register((world, player, pos, state, be) -> {
            if (!(state.getBlock() instanceof StatuePartBlock)) return true;
            BlockPos base = pos.below(state.getValue(StatuePartBlock.LEVEL));
            BlockState bs = world.getBlockState(base);
            if (!(bs.getBlock() instanceof StatueBlock)) return true;
            ShopZones.Zone z = blocking(world, player, base);
            if (z == null) return true;
            sendProtected(player, z);
            return false;
        });
    }
}

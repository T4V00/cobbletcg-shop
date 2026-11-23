package dev.tcgshop.luxury;

import dev.tcgshop.shop.ShopZones;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Shop zone protection for multi-block statues. ShopProtection only checks the block being broken or
 * placed; a statue occupies several: the base and the parts above ({@link StatuePartBlock}). All of them are checked here.
 * Same rule as ShopProtection: only the owner builds inside their zone; creative + operator bypasses everything.
 */
public final class StatueProtection {
    private StatueProtection() {}

    private static boolean bypass(@Nullable PlayerEntity p) {
        return p != null && p.isCreative() && p.hasPermissionLevel(2);
    }

    /**
     * Someone else's zone that prevents that player from building/breaking at pos, or null if allowed (or off the server: the client has no
     * zones and the server is authoritative). player null (no player) = any zone prevents it.
     */
    @Nullable
    public static ShopZones.Zone blocking(World world, @Nullable PlayerEntity player, BlockPos pos) {
        if (!(world instanceof ServerWorld sw) || bypass(player)) return null;
        ShopZones.Zone z = ShopZones.get(sw).at(pos);
        if (z == null) return null;
        return player != null && z.owner.equals(player.getUuid()) ? null : z;
    }

    public static boolean canBuild(World world, @Nullable PlayerEntity player, BlockPos pos) {
        return blocking(world, player, pos) == null;
    }

    /** First foreign zone overlapped by the statue of that tier placed with its base at base (base and parts), or null. */
    @Nullable
    public static ShopZones.Zone blockingStatue(World world, @Nullable PlayerEntity player, BlockPos base, StatueTier tier) {
        for (int i = 0; i <= tier.parts; i++) {
            ShopZones.Zone z = blocking(world, player, base.up(i));
            if (z != null) return z;
        }
        return null;
    }

    public static void sendProtected(PlayerEntity player, ShopZones.Zone z) {
        player.sendMessage(Text.translatable("tcgshop.shop.protected", z.ownerName).formatted(Formatting.RED), true);
    }

    /**
     * Breaking a part breaks (and drops) the base: if the base is in someone else's zone, the part cannot be broken even if it
     * is outside the zone (otherwise a statue could be taken out of a protected shop by breaking it from above).
     */
    static void register() {
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, be) -> {
            if (!(state.getBlock() instanceof StatuePartBlock)) return true;
            BlockPos base = pos.down(state.get(StatuePartBlock.LEVEL));
            BlockState bs = world.getBlockState(base);
            if (!(bs.getBlock() instanceof StatueBlock)) return true;
            ShopZones.Zone z = blocking(world, player, base);
            if (z == null) return true;
            sendProtected(player, z);
            return false;
        });
    }
}

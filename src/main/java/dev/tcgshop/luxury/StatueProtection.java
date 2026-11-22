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
 * Protección de zonas de tienda para las estatuas de varios bloques. ShopProtection sólo mira el bloque que se rompe o
 * coloca; una estatua ocupa varios: la base y las piezas de encima ({@link StatuePartBlock}). Aquí se comprueban todas.
 * Mismo criterio que ShopProtection: sólo el dueño construye dentro de su zona; creativo + operador lo salta todo.
 */
public final class StatueProtection {
    private StatueProtection() {}

    private static boolean bypass(@Nullable PlayerEntity p) {
        return p != null && p.isCreative() && p.hasPermissionLevel(2);
    }

    /**
     * Zona ajena que impide a ese jugador construir/romper en pos, o null si puede (o fuera del servidor: en el cliente no
     * hay zonas y manda el servidor). player null (sin jugador) = cualquier zona lo impide.
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

    /** Primera zona ajena que pisa la estatua de ese nivel colocada con la base en base (base y piezas), o null. */
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
     * Romper una pieza rompe (y suelta) la base: si la base está en una zona ajena, no se deja romper la pieza aunque ella
     * quede fuera de la zona (si no, se podría sacar una estatua de una tienda protegida rompiéndola desde arriba).
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

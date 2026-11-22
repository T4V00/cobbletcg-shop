package dev.tcgshop.luxury.house;

import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * Colocación de la finca en el mundo. El diseño ({@link MansionDesign}) usa coordenadas locales: x 0..99 de oeste a
 * este, z 0..99 de norte a sur (la verja de entrada está en z = 0, mirando al norte) e y relativa al suelo. Aquí se pasa
 * a coordenadas del mundo: el sur local apunta hacia donde mira el jugador, de modo que la puerta queda mirándole.
 */
public final class MansionPlan {
    /** Distancia entre los pies del jugador y la verja. */
    public static final int GAP = 3;

    public final BlockPos origin;
    public final Direction facing;
    public final BlockRotation rotation;
    private final Direction east;

    public MansionPlan(BlockPos origin, Direction facing) {
        this.origin = origin;
        this.facing = facing.getAxis().isHorizontal() ? facing : Direction.SOUTH;
        this.rotation = rotationFor(this.facing);
        this.east = rotation.rotate(Direction.EAST);
    }

    /** Plan centrado delante de un jugador en feet mirando hacia facing. */
    public static MansionPlan inFrontOf(BlockPos feet, Direction facing) {
        Direction f = facing.getAxis().isHorizontal() ? facing : Direction.SOUTH;
        Direction e = rotationFor(f).rotate(Direction.EAST);
        BlockPos o = feet.offset(f, GAP).offset(e, -MansionDesign.W / 2);
        return new MansionPlan(o, f);
    }

    /** Giro que lleva el sur local a facing. */
    public static BlockRotation rotationFor(Direction facing) {
        return switch (facing) {
            case WEST -> BlockRotation.CLOCKWISE_90;
            case NORTH -> BlockRotation.CLOCKWISE_180;
            case EAST -> BlockRotation.COUNTERCLOCKWISE_90;
            default -> BlockRotation.NONE;
        };
    }

    public BlockPos.Mutable world(int lx, int y, int lz, BlockPos.Mutable out) {
        return out.set(origin.getX() + east.getOffsetX() * lx + facing.getOffsetX() * lz,
                origin.getY() + y,
                origin.getZ() + east.getOffsetZ() * lx + facing.getOffsetZ() * lz);
    }

    public BlockPos world(int lx, int y, int lz) {
        return world(lx, y, lz, new BlockPos.Mutable()).toImmutable();
    }

    /**
     * Punto del mundo (para entidades) a partir de coordenadas locales con decimales: se toma el bloque local que lo
     * contiene y se conserva la posición dentro del bloque (x.5 = centro del bloque, sea cual sea el giro).
     */
    public Vec3d center(double lx, double y, double lz) {
        BlockPos b = world((int) Math.floor(lx), (int) Math.floor(y), (int) Math.floor(lz));
        return new Vec3d(b.getX() + 0.5, b.getY() + (y - Math.floor(y)), b.getZ() + 0.5);
    }

    /** Dirección local (diseño) -> dirección del mundo. */
    public Direction dir(Direction local) {
        return local.getAxis().isVertical() ? local : rotation.rotate(local);
    }

    /** Caja que ocupa la finca (de los cimientos a lo más alto). */
    public Box box() {
        BlockPos a = world(0, MansionDesign.Y_MIN, 0), b = world(MansionDesign.W - 1, MansionDesign.Y_MAX, MansionDesign.D - 1);
        return new Box(Math.min(a.getX(), b.getX()), a.getY(), Math.min(a.getZ(), b.getZ()),
                Math.max(a.getX(), b.getX()) + 1, b.getY() + 1, Math.max(a.getZ(), b.getZ()) + 1);
    }
}

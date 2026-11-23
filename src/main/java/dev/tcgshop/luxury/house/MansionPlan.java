package dev.tcgshop.luxury.house;

import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * Placement of the estate in the world. The design ({@link MansionDesign}) uses local coordinates: x 0..99 from west to
 * east, z 0..99 from north to south (the entrance gate is at z = 0, facing north) and y relative to the ground. Here they are
 * converted to world coordinates: local south points where the player is looking, so the door ends up facing them.
 */
public final class MansionPlan {
    /** Distance between the player's feet and the gate. */
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

    /** Plan centered in front of a player at feet facing facing. */
    public static MansionPlan inFrontOf(BlockPos feet, Direction facing) {
        Direction f = facing.getAxis().isHorizontal() ? facing : Direction.SOUTH;
        Direction e = rotationFor(f).rotate(Direction.EAST);
        BlockPos o = feet.offset(f, GAP).offset(e, -MansionDesign.W / 2);
        return new MansionPlan(o, f);
    }

    /** Rotation that maps local south to facing. */
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
     * World point (for entities) from fractional local coordinates: takes the local block that
     * contains it and keeps the position within the block (x.5 = block center, whatever the rotation).
     */
    public Vec3d center(double lx, double y, double lz) {
        BlockPos b = world((int) Math.floor(lx), (int) Math.floor(y), (int) Math.floor(lz));
        return new Vec3d(b.getX() + 0.5, b.getY() + (y - Math.floor(y)), b.getZ() + 0.5);
    }

    /** Local (design) direction -> world direction. */
    public Direction dir(Direction local) {
        return local.getAxis().isVertical() ? local : rotation.rotate(local);
    }

    /** Box occupied by the estate (from the foundations to the highest point). */
    public Box box() {
        BlockPos a = world(0, MansionDesign.Y_MIN, 0), b = world(MansionDesign.W - 1, MansionDesign.Y_MAX, MansionDesign.D - 1);
        return new Box(Math.min(a.getX(), b.getX()), a.getY(), Math.min(a.getZ(), b.getZ()),
                Math.max(a.getX(), b.getX()) + 1, b.getY() + 1, Math.max(a.getZ(), b.getZ()) + 1);
    }
}

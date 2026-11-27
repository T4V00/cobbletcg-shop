package dev.tcgshop.shop;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.Nullable;

/**
 * 1.1 (staff update): where people stand around a cash register.
 *
 * Before, the queue always formed on the side the register's FACING points to, at the register's own height, and the
 * cashier stood on the other side. But FACING points at whoever placed the block, so a register placed from behind the
 * counter put the queue behind the counter (on the cashier's side), and a register standing on a counter put every
 * queue spot in mid-air: none of them counted as "standable", the whole queue collapsed onto one spot and only the
 * customer who happened to reach it could be served. Here the customer side is the more open one (the counter's back is
 * usually a narrow aisle against a wall) and every spot is moved down to the floor (a register on a counter, slabs,
 * carpets).
 */
public final class RegisterSpots {
    private RegisterSpots() {}

    /** Can a person stand at p (two free blocks with something solid underneath)? */
    public static boolean standable(ServerLevel w, BlockPos p) {
        BlockState below = w.getBlockState(p.below());
        return !w.getBlockState(p).blocksMotion() && !w.getBlockState(p.above()).blocksMotion()
                && (below.blocksMotion() || below.isFaceSturdy(w, p.below(), Direction.UP));
    }

    /** The standable spot at p's column closest to p's height (same height, then 1-2 below, then 1 above), or null. */
    @Nullable
    public static BlockPos floorNear(ServerLevel w, BlockPos p) {
        for (int dy : new int[]{0, -1, -2, 1}) {
            BlockPos q = p.offset(0, dy, 0);
            if (standable(w, q)) return q.immutable();
        }
        return null;
    }

    private static Direction facing(ServerLevel w, BlockPos reg) {
        BlockState s = w.getBlockState(reg);
        return s.hasProperty(BlockStateProperties.HORIZONTAL_FACING) ? s.getValue(BlockStateProperties.HORIZONTAL_FACING) : Direction.NORTH;
    }

    /** Free floor cells in a 3-wide, 5-deep area on side d of the register (how open that side is). */
    private static int openness(ServerLevel w, BlockPos reg, Direction d) {
        int n = 0;
        Direction side = d.getClockWise();
        for (int k = 1; k <= 5; k++)
            for (int s = -1; s <= 1; s++)
                if (floorNear(w, reg.relative(d, k).relative(side, s)) != null) n++;
        return n;
    }

    /** The side customers queue on: the register's facing, unless the opposite side is clearly more open. */
    public static Direction customerSide(ServerLevel w, BlockPos reg) {
        Direction f = facing(w, reg);
        int a = openness(w, reg, f), b = openness(w, reg, f.getOpposite());
        return b > a + 1 ? f.getOpposite() : f;
    }

    /** Where the first customer stands to pay (the floor in front of the register on the customer side), or null. */
    @Nullable
    public static BlockPos front(ServerLevel w, BlockPos reg) {
        Direction d = customerSide(w, reg);
        BlockPos p = floorNear(w, reg.relative(d));
        if (p == null) p = floorNear(w, reg.relative(d, 2)); // a counter piece in front of the register
        return p;
    }

    /** Where a cashier works: behind the register (the side away from the customers), on the floor. */
    public static BlockPos cashierPost(ServerLevel w, BlockPos reg) {
        Direction back = customerSide(w, reg).getOpposite();
        BlockPos p = floorNear(w, reg.relative(back));
        if (p == null) p = floorNear(w, reg.relative(back, 2));
        if (p == null) p = floorNear(w, reg.relative(back.getClockWise()));
        if (p == null) p = floorNear(w, reg.relative(back.getCounterClockWise()));
        return p != null ? p : reg.above().immutable();
    }

    /** Is p on the customers' side of the register (never behind the counter with the cashier)? */
    private static boolean ahead(BlockPos p, BlockPos reg, Direction d) {
        return (p.getX() - reg.getX()) * d.getStepX() + (p.getZ() - reg.getZ()) * d.getStepZ() >= 1;
    }

    /**
     * The queue: spot 0 at the counter, the rest one behind another moving away from the register (turning to a side at
     * walls and furniture); when the line is blocked it keeps filling the free floor around it, so two customers never
     * share a spot. Up to 12 spots; spot k beyond the end returns the last one.
     */
    public static BlockPos queueSpot(ServerLevel w, BlockPos reg, int k) {
        BlockPos first = front(w, reg);
        if (first == null) return reg.relative(customerSide(w, reg)).immutable();
        Direction d = customerSide(w, reg);
        List<BlockPos> line = new ArrayList<>();
        line.add(first);
        BlockPos cur = first;
        Direction[] prefs = {d, d.getClockWise(), d.getCounterClockWise()};
        while (line.size() <= k && line.size() < 12) {
            BlockPos next = null;
            for (Direction dir : prefs) {
                BlockPos n = floorNear(w, cur.relative(dir));
                if (n != null && Math.abs(n.getY() - cur.getY()) <= 1 && !line.contains(n) && ahead(n, reg, d)) { next = n; break; }
            }
            if (next == null) { // blocked: any free floor next to the line (closest to its end first)
                outer:
                for (int i = line.size() - 1; i >= 0; i--)
                    for (Direction dir : Direction.Plane.HORIZONTAL) {
                        BlockPos n = floorNear(w, line.get(i).relative(dir));
                        if (n != null && !line.contains(n) && ahead(n, reg, d)) { next = n; break outer; }
                    }
            }
            if (next == null) break;
            line.add(next);
            cur = next;
        }
        return line.get(Math.min(k, line.size() - 1));
    }
}

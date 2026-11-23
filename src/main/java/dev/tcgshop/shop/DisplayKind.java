package dev.tcgshop.shop;

import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import org.jetbrains.annotations.Nullable;

import java.util.function.IntToDoubleFunction;

/**
 * Blueprint of each display fixture. They are multi-block structures (width W x height H x depth D): the
 * "master" block (part 0) stores the products and draws them all; the other parts only provide shape and collision.
 *
 * Local "facing north" coordinates in blocks, with origin at the master's corner: x in [0, W] (towards the
 * left of the customer looking at the fixture), y in [0, H], z in [0, D] (z = 0 is the front, -Z). Cards appear
 * the size of a card in a frame (~0.94 blocks tall). It is used by rendering, the highlight of the
 * targeted product, right-click buying and customers alike (client and server compute the same slot).
 */
public enum DisplayKind {
    /** Standing display: two large cards on a stand (2 x 2). */
    CARD_STAND(2, 2, 1, Layout.UPRIGHT,
            s(0.52, 1.13, 0.48, 1.08, 1, 14, false), s(1.48, 1.13, 0.48, 1.08, 1, 14, false)),
    /** Glass counter showcase: 4 cards lying flat under the glass (3 x 1). */
    DISPLAY_CASE(3, 1, 1, Layout.LYING,
            s(0.40, 0.63, 0.50, 0.92, 1, 68, false), s(1.133, 0.63, 0.50, 0.92, 1, 68, false),
            s(1.867, 0.63, 0.50, 0.92, 1, 68, false), s(2.60, 0.63, 0.50, 0.92, 1, 68, false)),
    /** Wall shelf: 3 shelves x 4 packs (3 x 3). */
    PACK_SHELF(3, 3, 1, Layout.UPRIGHT, grid(new double[]{0.42, 1.14, 1.86, 2.58}, new double[]{2.42, 1.47, 0.52}, 0.42, 0.80, 1, 8)),
    /** Double-sided supermarket gondola: 2 shelves x 4 on each side (3 x 2). Place several to make aisles. */
    GONDOLA(3, 2, 1, Layout.DOUBLE, concat(
            grid(new double[]{0.42, 1.14, 1.86, 2.58}, new double[]{1.42, 0.50}, 0.27, 0.78, 1, 6),
            grid(new double[]{0.42, 1.14, 1.86, 2.58}, new double[]{1.42, 0.50}, 0.73, 0.78, -1, 6))),
    /** Pallet of booster boxes: 2 columns x 3 stacked boxes (2 x 2). Here "y" is the box's base and "size" its width. */
    BOX_PALLET(2, 2, 1, Layout.BOXES,
            s(0.5, 0.10, 0.5, 0.92, 1, 0, false), s(1.5, 0.10, 0.5, 0.92, 1, 0, false),
            s(0.5, 0.68, 0.5, 0.92, 1, 0, false), s(1.5, 0.68, 0.5, 0.92, 1, 0, false),
            s(0.5, 1.26, 0.5, 0.92, 1, 0, false), s(1.5, 1.26, 0.5, 0.92, 1, 0, false)),
    /** Pedestal with a glass case: one huge product spinning inside (1 x 3). */
    PEDESTAL(1, 3, 1, Layout.SPIN, s(0.5, 1.82, 0.5, 1.24, 1, 0, true)),
    /** Wall of graded cards (slabs) hung on the wall: 4 x 3 cards (3 x 3). */
    CARD_WALL(3, 3, 1, Layout.UPRIGHT, grid(new double[]{0.42, 1.14, 1.86, 2.58}, new double[]{2.45, 1.50, 0.55}, 0.66, 0.78, 1, 0)),
    /** Checkout counter: 3 cards on tilted stands (2 x 1). */
    COUNTER(2, 1, 1, Layout.UPRIGHT, grid(new double[]{0.38, 1.0, 1.62}, new double[]{0.86}, 0.52, 0.70, 1, 50)),
    /** Glass tower showcase: 3 cards, one per shelf (1 x 3). */
    SHOWCASE(1, 3, 1, Layout.UPRIGHT, grid(new double[]{0.5}, new double[]{2.30, 1.45, 0.60}, 0.5, 0.72, 1, 8)),
    /** Wall ledge: 3 leaning cards (2 x 1). */
    LEDGE(2, 1, 1, Layout.UPRIGHT, grid(new double[]{0.38, 1.0, 1.62}, new double[]{0.52}, 0.62, 0.70, 1, 10)),
    /** Booster box shelf: 3 x 2 boxes (3 x 2). */
    BOX_SHELF(3, 2, 1, Layout.BOXES,
            s(0.5, 0.08, 0.5, 0.9, 1, 0, false), s(1.5, 0.08, 0.5, 0.9, 1, 0, false), s(2.5, 0.08, 0.5, 0.9, 1, 0, false),
            s(0.5, 1.06, 0.5, 0.9, 1, 0, false), s(1.5, 1.06, 0.5, 0.9, 1, 0, false), s(2.5, 1.06, 0.5, 0.9, 1, 0, false)),
    /** Wall frame for a single card (1 x 1). */
    FRAME(1, 1, 1, Layout.UPRIGHT, s(0.5, 0.5, 0.84, 0.82, 1, 0, false)),

    // ---- 1.0 RGB displays (shop.RgbDisplays): new shapes, same slot and hit-test system as above
    /** Tower of graded slabs: 2 columns x 3 shelves (2 x 3). */
    SLAB_TOWER(2, 3, 1, Layout.UPRIGHT, grid(new double[]{0.5, 1.5}, new double[]{2.30, 1.40, 0.50}, 0.5, 0.60, 1, 0)),
    /** Low turntable: one product spinning on a lit platter (1 x 1). */
    TURNTABLE(1, 1, 1, Layout.SPIN, s(0.5, 0.64, 0.5, 0.56, 1, 0, true)),
    /** Booster box stand: 2 columns x 3 boxes (2 x 3). */
    BOX_STAND(2, 3, 1, Layout.BOXES,
            s(0.5, 0.08, 0.5, 0.9, 1, 0, false), s(1.5, 0.08, 0.5, 0.9, 1, 0, false),
            s(0.5, 1.06, 0.5, 0.9, 1, 0, false), s(1.5, 1.06, 0.5, 0.9, 1, 0, false),
            s(0.5, 2.04, 0.5, 0.9, 1, 0, false), s(1.5, 2.04, 0.5, 0.9, 1, 0, false)),
    /** Giant neon wall frame: one huge card (2 x 2). */
    BIG_FRAME(2, 2, 1, Layout.UPRIGHT, s(1.0, 0.92, 0.84, 1.3, 1, 0, false)),
    /** Small glass cube counter: 3 cards lying under the glass (2 x 1). */
    GLASS_CUBE(2, 1, 1, Layout.LYING,
            s(0.36, 0.63, 0.50, 0.80, 1, 68, false), s(1.0, 0.63, 0.50, 0.80, 1, 68, false), s(1.64, 0.63, 0.50, 0.80, 1, 68, false)),
    /** Collector shelf: 2 shelves x 5 cards (3 x 2). */
    MULTI_SHELF(3, 2, 1, Layout.UPRIGHT, grid(new double[]{0.35, 0.925, 1.5, 2.075, 2.65}, new double[]{1.47, 0.53}, 0.5, 0.62, 1, 8)),
    /** Grand gallery wall: 5 x 3 cards hung on the wall (3 x 3). */
    GRAND_WALL(3, 3, 1, Layout.UPRIGHT, grid(new double[]{0.32, 0.91, 1.5, 2.09, 2.68}, new double[]{2.36, 1.42, 0.48}, 0.66, 0.66, 1, 0)),
    /** Three card frames in a row on the wall (3 x 1). */
    WALL_TRIO(3, 1, 1, Layout.UPRIGHT, grid(new double[]{0.5, 1.5, 2.5}, new double[]{0.5}, 0.84, 0.82, 1, 0));

    public enum Layout { UPRIGHT, LYING, DOUBLE, BOXES, SPIN }

    public record Slot(double x, double y, double z, double size, int face, float tilt, boolean spin) {}

    public final int w, h, d;
    public final Layout layout;
    public final Slot[] slots;

    DisplayKind(int w, int h, int d, Layout layout, Slot... slots) {
        this.w = w;
        this.h = h;
        this.d = d;
        this.layout = layout;
        this.slots = slots;
    }

    private static Slot s(double x, double y, double z, double size, int face, float tilt, boolean spin) {
        return new Slot(x, y, z, size, face, tilt, spin);
    }

    private static Slot[] grid(double[] xs, double[] ys, double z, double size, int face, float tilt) {
        Slot[] out = new Slot[xs.length * ys.length];
        int i = 0;
        for (double y : ys) for (double x : xs) out[i++] = s(x, y, z, size, face, tilt, false);
        return out;
    }

    private static Slot[] concat(Slot[] a, Slot[] b) {
        Slot[] out = new Slot[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    /** Hung on a wall (frames, card walls): products are drawn straight, without the acrylic foot or the "messy" jitter. */
    public boolean wallHung() {
        return switch (this) {
            case FRAME, CARD_WALL, BIG_FRAME, GRAND_WALL, WALL_TRIO -> true;
            default -> false;
        };
    }

    // ------------------------------------------------------------------ structure parts

    public int parts() { return w * h * d; }

    /** Local (i, j, k) of a part. */
    public int[] cell(int part) {
        return new int[]{part % w, (part / w) % h, part / (w * h)};
    }

    public int partIndex(int i, int j, int k) {
        if (i < 0 || j < 0 || k < 0 || i >= w || j >= h || k >= d) return -1;
        return i + w * (j + h * k);
    }

    /** World offset of local cell (i, j, k) depending on which way the fixture faces. */
    public static BlockPos offset(Direction facing, int i, int j, int k) {
        return switch (facing) {
            case SOUTH -> new BlockPos(-i, j, -k);
            case WEST -> new BlockPos(k, j, -i);
            case EAST -> new BlockPos(-k, j, i);
            default -> new BlockPos(i, j, k);
        };
    }

    public BlockPos partPos(BlockPos master, Direction facing, int part) {
        int[] c = cell(part);
        return master.add(offset(facing, c[0], c[1], c[2]));
    }

    public BlockPos masterOf(BlockPos partPos, Direction facing, int part) {
        int[] c = cell(Math.max(0, Math.min(parts() - 1, part)));
        return partPos.subtract(offset(facing, c[0], c[1], c[2]));
    }

    /** On placement: the targeted block ends up at the center of the width (3-wide ones are centered). */
    public BlockPos masterForClick(BlockPos clicked, Direction facing) {
        return clicked.subtract(offset(facing, w >= 3 ? 1 : 0, 0, 0));
    }

    /** Outline/collision shape of a part (already rotated). */
    public VoxelShape shape(Direction facing, int part) {
        int[] c = cell(part);
        double[] b = switch (this) {
            case CARD_STAND -> c[1] == 0 ? new double[]{0, 0, 3, 16, 16, 13} : new double[]{0, 0, 4, 16, 11, 12};
            case PEDESTAL -> c[1] == 0 ? new double[]{2, 0, 2, 14, 16, 14} : new double[]{1, 0, 1, 15, 16, 15};
            case CARD_WALL -> new double[]{0, 0, 9, 16, 16, 16};
            case SHOWCASE -> new double[]{0.5, 0, 0.5, 15.5, 16, 15.5};
            case LEDGE -> new double[]{0, 0, 5, 16, 15, 16};
            case FRAME -> new double[]{0.5, 0.5, 13, 15.5, 15.5, 16};
            case COUNTER -> new double[]{0, 0, 1, 16, 15, 16};
            case PACK_SHELF -> new double[]{0, 0, 1, 16, 16, 16};
            case SLAB_TOWER -> new double[]{0, 0, 0.5, 16, 16, 15.5};
            case TURNTABLE -> new double[]{1, 0, 1, 15, 15, 15};
            case BIG_FRAME -> new double[]{0, 0, 13, 16, 16, 16};
            case MULTI_SHELF -> new double[]{0, 0, 1, 16, 16, 16};
            case GRAND_WALL -> new double[]{0, 0, 9, 16, 16, 16};
            case WALL_TRIO -> new double[]{0, 0.5, 13, 16, 15.5, 16};
            default -> null;
        };
        if (b == null) return VoxelShapes.fullCube();
        return rotated(facing, b);
    }

    private static VoxelShape rotated(Direction facing, double[] b) {
        double x0 = b[0], y0 = b[1], z0 = b[2], x1 = b[3], y1 = b[4], z1 = b[5];
        return switch (facing) {
            case SOUTH -> Block.createCuboidShape(16 - x1, y0, 16 - z1, 16 - x0, y1, 16 - z0);
            case WEST -> Block.createCuboidShape(z0, y0, 16 - x1, z1, y1, 16 - x0);
            case EAST -> Block.createCuboidShape(16 - z1, y0, x0, 16 - z0, y1, x1);
            default -> Block.createCuboidShape(x0, y0, z0, x1, y1, z1);
        };
    }

    // ------------------------------------------------------------------ slots

    /** Converts a world point relative to the master block into local "facing north" coordinates. */
    public static Vec3d toLocal(Direction facing, Vec3d rel) {
        double x = rel.x - 0.5, z = rel.z - 0.5;
        return switch (facing) {
            case SOUTH -> new Vec3d(-x + 0.5, rel.y, -z + 0.5);
            case WEST -> new Vec3d(-z + 0.5, rel.y, x + 0.5);
            case EAST -> new Vec3d(z + 0.5, rel.y, -x + 0.5);
            default -> rel;
        };
    }

    /** Inverse of toLocal: local point -> relative to the master block in the world. */
    public static Vec3d toWorld(Direction facing, Vec3d local) {
        return switch (facing) {
            case SOUTH -> new Vec3d(1 - local.x, local.y, 1 - local.z);
            case WEST -> new Vec3d(local.z, local.y, 1 - local.x);
            case EAST -> new Vec3d(1 - local.z, local.y, local.x);
            default -> local;
        };
    }

    /** Slot center in the world (so customers walk up to the product). */
    public Vec3d slotWorld(Direction facing, BlockPos master, int slot) {
        Slot s = slots[Math.max(0, Math.min(slots.length - 1, slot))];
        Vec3d front = new Vec3d(s.x, 0, s.face < 0 ? d + 0.8 : -0.8); // in front of the product, at ground level
        Vec3d rel = toWorld(facing, front);
        return new Vec3d(master.getX() + rel.x, master.getY(), master.getZ() + rel.z);
    }

    /** Targeted slot (hit point relative to the master block), or -1. */
    public int slotAt(Direction facing, Vec3d relHit) {
        Vec3d p = toLocal(facing, relHit);
        if (layout == Layout.SPIN) return 0;
        int best = -1;
        double bestD = Double.MAX_VALUE;
        for (int i = 0; i < slots.length; i++) {
            Slot s = slots[i];
            double halfW = s.size * 0.38 + 0.04, halfH = s.size * 0.52 + 0.04;
            double dx = Math.abs(p.x - s.x), d2;
            switch (layout) {
                case LYING -> {
                    double dz = Math.abs(p.z - s.z);
                    if (dx > halfW || dz > halfH) continue;
                    d2 = dx * dx + dz * dz;
                }
                case DOUBLE -> {
                    if ((s.face > 0) != (p.z < 0.5)) continue; // gondola side you are looking at
                    double dy = Math.abs(p.y - s.y);
                    if (dx > halfW || dy > halfH) continue;
                    d2 = dx * dx + dy * dy;
                }
                case BOXES -> {
                    double cy = s.y + s.size * 0.31, dy = Math.abs(p.y - cy);
                    if (dx > s.size * 0.5 || dy > s.size * 0.32) continue;
                    d2 = dx * dx + dy * dy;
                }
                default -> {
                    double dy = Math.abs(p.y - s.y);
                    if (dx > halfW || dy > halfH) continue;
                    d2 = dx * dx + dy * dy;
                }
            }
            if (d2 < bestD) { best = i; bestD = d2; }
        }
        return best;
    }

    /**
     * Targeted slot along the player's view ray (1.0): eye and hit point relative to the master block. The products
     * sit deeper than the outline's front face (shelves, gondolas, box shelves...), so the hit point on the outline
     * alone picks the wrong row (or none) when looking up or down at them. Here the ray is extended through the
     * fixture and tested against each product's own card plane (tilt included) or box volume; the nearest one wins.
     * Falls back to {@link #slotAt(Direction, Vec3d)} when the ray crosses no product (or without an eye position).
     * scale: per-slot look scale chosen by the owner (null = 1); it only ever enlarges the clickable area.
     */
    public int slotAt(Direction facing, @Nullable Vec3d relEye, Vec3d relHit, @Nullable IntToDoubleFunction scale) {
        if (layout == Layout.SPIN) return 0;
        if (relEye == null) return slotAt(facing, relHit);
        Vec3d o = toLocal(facing, relEye), p = toLocal(facing, relHit);
        Vec3d dir = p.subtract(o);
        double len = dir.length();
        if (len < 1e-6) return slotAt(facing, relHit);
        dir = dir.multiply(1 / len);
        int best = -1;
        double bestT = Double.MAX_VALUE, bestC = Double.MAX_VALUE;
        for (int i = 0; i < slots.length; i++) {
            Slot s = slots[i];
            double k = scale == null ? 1 : Math.max(1, Math.min(ShopDisplayBlockEntity.MAX_SCALE, scale.applyAsDouble(i)));
            double t, c;
            if (layout == Layout.BOXES) {
                // box resting on its base, centered on (x, z): width = size, height 0.62, depth 0.8 (as drawn)
                double bw = s.size * k, bh = bw * 0.62, bd = bw * 0.8, m = 0.04;
                t = rayBox(o, dir, s.x - bw / 2 - m, s.y - m, s.z - bd / 2 - m, s.x + bw / 2 + m, s.y + bh + m, s.z + bd / 2 + m);
                if (t < 0) continue;
                double qx = o.x + dir.x * t, qy = o.y + dir.y * t;
                c = Math.abs(qx - s.x) / bw + Math.abs(qy - (s.y + bh / 2)) / bh;
            } else {
                // card plane through (x, y, z) facing -Z (+Z on the back of a gondola), tilted back by `tilt`
                double th = Math.toRadians(s.tilt), fz = s.face < 0 ? 1 : -1;
                double ny = Math.sin(th), nz = fz * Math.cos(th); // normal (towards the viewer)
                double uy = Math.cos(th), uz = -fz * Math.sin(th); // "up" of the card
                double den = dir.y * ny + dir.z * nz;
                if (den > -1e-4) continue; // seen from behind or edge-on
                t = ((s.y - o.y) * ny + (s.z - o.z) * nz) / den;
                if (t <= 0) continue;
                double qx = o.x + dir.x * t - s.x, qy = o.y + dir.y * t - s.y, qz = o.z + dir.z * t - s.z;
                double halfW = s.size * k * 0.38 + 0.04, halfH = s.size * k * 0.52 + 0.04;
                double u = Math.abs(qx), v = Math.abs(qy * uy + qz * uz);
                if (u > halfW || v > halfH) continue;
                c = u / halfW + v / halfH;
            }
            // nearest along the ray; on the same plane (same t), the closest to its center
            if (t < bestT - 1e-3 || (Math.abs(t - bestT) <= 1e-3 && c < bestC)) {
                best = i;
                bestT = Math.min(t, bestT);
                bestC = c;
            }
        }
        return best >= 0 ? best : slotAt(facing, relHit);
    }

    /** Distance along the (unit) ray to the box, 0 if it starts inside, or -1 if it misses. */
    private static double rayBox(Vec3d o, Vec3d d, double x0, double y0, double z0, double x1, double y1, double z1) {
        double tMin = 0, tMax = Double.MAX_VALUE;
        double[] os = {o.x, o.y, o.z}, ds = {d.x, d.y, d.z}, lo = {x0, y0, z0}, hi = {x1, y1, z1};
        for (int a = 0; a < 3; a++) {
            if (Math.abs(ds[a]) < 1e-9) {
                if (os[a] < lo[a] || os[a] > hi[a]) return -1;
                continue;
            }
            double t1 = (lo[a] - os[a]) / ds[a], t2 = (hi[a] - os[a]) / ds[a];
            if (t1 > t2) { double tmp = t1; t1 = t2; t2 = tmp; }
            tMin = Math.max(tMin, t1);
            tMax = Math.min(tMax, t2);
            if (tMin > tMax) return -1;
        }
        return tMin;
    }
}

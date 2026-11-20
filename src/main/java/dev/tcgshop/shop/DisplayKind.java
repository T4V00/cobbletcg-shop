package dev.tcgshop.shop;

import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;

/**
 * Plano de cada mueble expositor. Son estructuras de varios bloques (ancho W x alto H x fondo D): el bloque
 * "maestro" (parte 0) guarda los productos y los dibuja todos; el resto de partes sólo dan forma y colisión.
 *
 * Coordenadas locales "mirando al norte" en bloques, con origen en la esquina del maestro: x en [0, W] (hacia la
 * izquierda del cliente que mira el mueble), y en [0, H], z en [0, D] (z = 0 es el frente, -Z). Las cartas se ven
 * del tamaño de una carta en un marco (~0,94 bloques de alto). Lo usan a la vez el renderizado, el resaltado del
 * producto apuntado, la compra con clic derecho y los clientes (cliente y servidor calculan la misma casilla).
 */
public enum DisplayKind {
    /** Expositor de pie: dos cartas grandes sobre un atril (2 x 2). */
    CARD_STAND(2, 2, 1, Layout.UPRIGHT,
            s(0.52, 1.13, 0.48, 1.08, 1, 14, false), s(1.48, 1.13, 0.48, 1.08, 1, 14, false)),
    /** Vitrina-mostrador de cristal: 4 cartas tumbadas bajo el cristal (3 x 1). */
    DISPLAY_CASE(3, 1, 1, Layout.LYING,
            s(0.40, 0.63, 0.50, 0.92, 1, 68, false), s(1.133, 0.63, 0.50, 0.92, 1, 68, false),
            s(1.867, 0.63, 0.50, 0.92, 1, 68, false), s(2.60, 0.63, 0.50, 0.92, 1, 68, false)),
    /** Estantería de pared: 3 baldas x 4 sobres (3 x 3). */
    PACK_SHELF(3, 3, 1, Layout.UPRIGHT, grid(new double[]{0.42, 1.14, 1.86, 2.58}, new double[]{2.42, 1.47, 0.52}, 0.42, 0.80, 1, 8)),
    /** Góndola de supermercado de doble cara: 2 baldas x 4 por cada lado (3 x 2). Pon varias para hacer pasillos. */
    GONDOLA(3, 2, 1, Layout.DOUBLE, concat(
            grid(new double[]{0.42, 1.14, 1.86, 2.58}, new double[]{1.42, 0.50}, 0.27, 0.78, 1, 6),
            grid(new double[]{0.42, 1.14, 1.86, 2.58}, new double[]{1.42, 0.50}, 0.73, 0.78, -1, 6))),
    /** Palé de cajas de sobres: 2 columnas x 3 cajas apiladas (2 x 2). Aquí "y" es la base de la caja y "size" su ancho. */
    BOX_PALLET(2, 2, 1, Layout.BOXES,
            s(0.5, 0.10, 0.5, 0.92, 1, 0, false), s(1.5, 0.10, 0.5, 0.92, 1, 0, false),
            s(0.5, 0.68, 0.5, 0.92, 1, 0, false), s(1.5, 0.68, 0.5, 0.92, 1, 0, false),
            s(0.5, 1.26, 0.5, 0.92, 1, 0, false), s(1.5, 1.26, 0.5, 0.92, 1, 0, false)),
    /** Pedestal con urna de cristal: un producto enorme girando dentro (1 x 3). */
    PEDESTAL(1, 3, 1, Layout.SPIN, s(0.5, 1.82, 0.5, 1.24, 1, 0, true)),
    /** Muro de cartas gradadas (slabs) colgadas en la pared: 4 x 3 cartas (3 x 3). */
    CARD_WALL(3, 3, 1, Layout.UPRIGHT, grid(new double[]{0.42, 1.14, 1.86, 2.58}, new double[]{2.45, 1.50, 0.55}, 0.66, 0.78, 1, 0)),
    /** Mostrador de caja: 3 cartas en atriles inclinados (2 x 1). */
    COUNTER(2, 1, 1, Layout.UPRIGHT, grid(new double[]{0.38, 1.0, 1.62}, new double[]{0.86}, 0.52, 0.70, 1, 50)),
    /** Vitrina torre de cristal: 3 cartas, una por balda (1 x 3). */
    SHOWCASE(1, 3, 1, Layout.UPRIGHT, grid(new double[]{0.5}, new double[]{2.30, 1.45, 0.60}, 0.5, 0.72, 1, 8)),
    /** Repisa de pared: 3 cartas apoyadas (2 x 1). */
    LEDGE(2, 1, 1, Layout.UPRIGHT, grid(new double[]{0.38, 1.0, 1.62}, new double[]{0.52}, 0.62, 0.70, 1, 10)),
    /** Estantería de cajas de sobres: 3 x 2 cajas (3 x 2). */
    BOX_SHELF(3, 2, 1, Layout.BOXES,
            s(0.5, 0.08, 0.5, 0.9, 1, 0, false), s(1.5, 0.08, 0.5, 0.9, 1, 0, false), s(2.5, 0.08, 0.5, 0.9, 1, 0, false),
            s(0.5, 1.06, 0.5, 0.9, 1, 0, false), s(1.5, 1.06, 0.5, 0.9, 1, 0, false), s(2.5, 1.06, 0.5, 0.9, 1, 0, false)),
    /** Marco de pared para una sola carta (1 x 1). */
    FRAME(1, 1, 1, Layout.UPRIGHT, s(0.5, 0.5, 0.84, 0.82, 1, 0, false));

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

    // ------------------------------------------------------------------ partes de la estructura

    public int parts() { return w * h * d; }

    /** (i, j, k) local de una parte. */
    public int[] cell(int part) {
        return new int[]{part % w, (part / w) % h, part / (w * h)};
    }

    public int partIndex(int i, int j, int k) {
        if (i < 0 || j < 0 || k < 0 || i >= w || j >= h || k >= d) return -1;
        return i + w * (j + h * k);
    }

    /** Desplazamiento en el mundo de la celda local (i, j, k) según hacia dónde mira el mueble. */
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

    /** Al colocar: el bloque apuntado queda en el centro del ancho (los de 3 de ancho se centran). */
    public BlockPos masterForClick(BlockPos clicked, Direction facing) {
        return clicked.subtract(offset(facing, w >= 3 ? 1 : 0, 0, 0));
    }

    /** Forma de contorno/colisión de una parte (ya girada). */
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

    // ------------------------------------------------------------------ casillas

    /** Pasa un punto del mundo relativo al bloque maestro a coordenadas locales "mirando al norte". */
    public static Vec3d toLocal(Direction facing, Vec3d rel) {
        double x = rel.x - 0.5, z = rel.z - 0.5;
        return switch (facing) {
            case SOUTH -> new Vec3d(-x + 0.5, rel.y, -z + 0.5);
            case WEST -> new Vec3d(-z + 0.5, rel.y, x + 0.5);
            case EAST -> new Vec3d(z + 0.5, rel.y, -x + 0.5);
            default -> rel;
        };
    }

    /** Inversa de toLocal: punto local -> relativo al bloque maestro en el mundo. */
    public static Vec3d toWorld(Direction facing, Vec3d local) {
        return switch (facing) {
            case SOUTH -> new Vec3d(1 - local.x, local.y, 1 - local.z);
            case WEST -> new Vec3d(local.z, local.y, 1 - local.x);
            case EAST -> new Vec3d(1 - local.z, local.y, local.x);
            default -> local;
        };
    }

    /** Centro de la casilla en el mundo (para que los clientes caminen hasta el producto). */
    public Vec3d slotWorld(Direction facing, BlockPos master, int slot) {
        Slot s = slots[Math.max(0, Math.min(slots.length - 1, slot))];
        Vec3d front = new Vec3d(s.x, 0, s.face < 0 ? d + 0.8 : -0.8); // delante del producto, a ras de suelo
        Vec3d rel = toWorld(facing, front);
        return new Vec3d(master.getX() + rel.x, master.getY(), master.getZ() + rel.z);
    }

    /** Casilla apuntada (punto de impacto relativo al bloque maestro), o -1. */
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
                    if ((s.face > 0) != (p.z < 0.5)) continue; // cara de la góndola por la que miras
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
}

package dev.tcgshop.luxury;

import net.minecraft.block.Block;
import net.minecraft.util.Rarity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import org.jetbrains.annotations.Nullable;

/**
 * Los 7 niveles de estatua de lujo (v0.9): cada uno 10 veces más caro que el anterior, más grande y de mejor material.
 * Los precios por defecto se pueden cambiar en config/tcgshop-luxury.json (ver {@link LuxuryConfig}).
 * <p>
 * Tamaños: los modelos pueden salirse del bloque (de -16 a 32 píxeles), pero la colisión se queda dentro de cada bloque.
 * Las estatuas de más de un bloque de alto colocan encima bloques invisibles {@link StatuePartBlock} ("statue_part")
 * que ocupan ese hueco (no se puede meter nada dentro de la estatua) y se rompen junto con la base.
 */
public enum StatueTier {
    // key, precio por defecto, color del nombre, piezas encima, luz, rareza
    BRONZE("bronze", 1_000L, 0xCD7F32, 0, 0, Rarity.UNCOMMON,
            Block.createCuboidShape(4.5, 0, 4.5, 11.5, 9, 11.5), null, null),
    SILVER("silver", 10_000L, 0xD4D9E0, 0, 0, Rarity.UNCOMMON,
            VoxelShapes.union(Block.createCuboidShape(3, 0, 3, 13, 3.5, 13), Block.createCuboidShape(5.5, 3.5, 5.5, 10.5, 16, 10.5)),
            null, null),
    GOLD("gold", 100_000L, 0xFFD24A, 1, 4, Rarity.RARE,
            VoxelShapes.union(Block.createCuboidShape(2, 0, 2, 14, 3, 14), Block.createCuboidShape(4.5, 3, 4.5, 11.5, 16, 11.5)),
            Block.createCuboidShape(3.5, 0, 3.5, 12.5, 8, 12.5), null),
    EMERALD("emerald", 1_000_000L, 0x3FE08A, 1, 6, Rarity.RARE,
            VoxelShapes.union(Block.createCuboidShape(1, 0, 1, 15, 4.5, 15), Block.createCuboidShape(5, 4.5, 5, 11, 16, 11)),
            Block.createCuboidShape(2, 0, 5.5, 14, 12, 10.5), null),
    DIAMOND("diamond", 10_000_000L, 0x6FF0F7, 1, 8, Rarity.EPIC,
            VoxelShapes.union(Block.createCuboidShape(0, 0, 0, 16, 4.5, 16), Block.createCuboidShape(4, 4.5, 4, 12, 16, 12)),
            Block.createCuboidShape(2, 0, 2, 14, 16, 14), null),
    NETHERITE("netherite", 100_000_000L, 0xC08CFF, 1, 11, Rarity.EPIC,
            VoxelShapes.union(Block.createCuboidShape(0, 0, 0, 16, 7, 16), Block.createCuboidShape(3, 7, 3, 13, 16, 13)),
            Block.createCuboidShape(4, 0, 4, 12, 16, 12), null),
    MASTERBALL("masterball", 1_000_000_000L, 0xD15CFF, 2, 15, Rarity.EPIC,
            VoxelShapes.union(Block.createCuboidShape(0, 0, 0, 16, 7, 16), Block.createCuboidShape(4, 7, 4, 12, 16, 12)),
            VoxelShapes.union(Block.createCuboidShape(4, 0, 4, 12, 8, 12), Block.createCuboidShape(2, 8, 2, 14, 16, 14)),
            VoxelShapes.union(Block.createCuboidShape(0, 0, 0, 16, 10, 16), Block.createCuboidShape(5, 10, 5, 11, 16, 11)));

    public final String key;
    public final long defaultPrice;
    public final int color;
    /** Bloques invisibles que ocupa por encima de la base (0, 1 o 2). */
    public final int parts;
    /** Nivel de luz que emite (los niveles altos brillan). */
    public final int light;
    public final Rarity rarity;
    /** Formas por orientación: índice = cuartos de vuelta en sentido horario desde el norte (N, E, S, O). */
    private final VoxelShape[] baseShapes, part1Shapes, part2Shapes;
    /** Precio que ha mandado el servidor (para el tooltip en el cliente); -1 = usar la configuración local. */
    public long clientPrice = -1;

    StatueTier(String key, long defaultPrice, int color, int parts, int light, Rarity rarity, VoxelShape baseShape,
               @Nullable VoxelShape part1, @Nullable VoxelShape part2) {
        this.key = key;
        this.defaultPrice = defaultPrice;
        this.color = color;
        this.parts = parts;
        this.light = light;
        this.rarity = rarity;
        this.baseShapes = rotations(baseShape);
        this.part1Shapes = rotations(part1 == null ? VoxelShapes.empty() : part1);
        this.part2Shapes = rotations(part2 == null ? VoxelShapes.empty() : part2);
    }

    /** Las 4 orientaciones de una forma definida mirando al norte (como el modelo, que el blockstate gira con y). */
    private static VoxelShape[] rotations(VoxelShape north) {
        VoxelShape[] out = new VoxelShape[4];
        out[0] = north;
        for (int q = 1; q < 4; q++) {
            VoxelShape r = VoxelShapes.empty();
            for (Box b : out[q - 1].getBoundingBoxes()) // 90° horario alrededor del centro: (x, z) -> (1 - z, x)
                r = VoxelShapes.union(r, VoxelShapes.cuboid(1 - b.maxZ, b.minY, b.minX, 1 - b.minZ, b.maxY, b.maxX));
            out[q] = r;
        }
        return out;
    }

    private static int quarter(Direction facing) {
        return switch (facing) {
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> 0;
        };
    }

    /** 1..7 */
    public int index() { return ordinal() + 1; }

    public String blockName() { return "statue_" + key; }

    /** Alto total en bloques (para el tooltip). */
    public int heightBlocks() { return 1 + parts; }

    /** Forma de la base mirando hacia facing. */
    public VoxelShape baseShape(Direction facing) {
        return baseShapes[quarter(facing)];
    }

    /** Forma de la pieza de ese nivel (1 o 2) mirando hacia facing. */
    public VoxelShape partShape(int level, Direction facing) {
        return (level >= 2 ? part2Shapes : part1Shapes)[quarter(facing)];
    }

    /** Precio real (servidor): el de la configuración. Es lo que se cobra. */
    public long price() {
        return LuxuryConfig.INSTANCE.price(this);
    }

    /** Precio para enseñar en el cliente: lo último que mandó el servidor (catálogo) o, si no, la configuración local. */
    public long displayPrice() {
        return clientPrice > 0 ? clientPrice : price();
    }

    @Nullable
    public static StatueTier byIndex(int i) {
        return i >= 1 && i <= values().length ? values()[i - 1] : null;
    }

    @Nullable
    public static StatueTier byKey(String key) {
        for (StatueTier t : values()) if (t.key.equals(key)) return t;
        return null;
    }
}

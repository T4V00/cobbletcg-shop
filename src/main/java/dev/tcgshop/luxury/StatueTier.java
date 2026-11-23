package dev.tcgshop.luxury;

import net.minecraft.block.Block;
import net.minecraft.util.Rarity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import org.jetbrains.annotations.Nullable;

/**
 * The 7 luxury statue tiers (v0.9): each one 10 times more expensive than the previous, bigger and of better material.
 * Default prices can be changed in config/tcgshop-luxury.json (see {@link LuxuryConfig}).
 * <p>
 * Sizes: models may extend outside the block (from -16 to 32 pixels), but collision stays within each block.
 * Statues taller than one block place invisible {@link StatuePartBlock} blocks ("statue_part") above
 * that fill that space (nothing can be put inside the statue) and break together with the base.
 */
public enum StatueTier {
    // key, default price, name color, parts above, light, rarity
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
    /** Invisible blocks it occupies above the base (0, 1 or 2). */
    public final int parts;
    /** Light level it emits (high tiers glow). */
    public final int light;
    public final Rarity rarity;
    /** Shapes per orientation: index = clockwise quarter turns from north (N, E, S, W). */
    private final VoxelShape[] baseShapes, part1Shapes, part2Shapes;
    /** Price sent by the server (for the client tooltip); -1 = use the local config. */
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

    /** The 4 orientations of a shape defined facing north (like the model, which the blockstate rotates with y). */
    private static VoxelShape[] rotations(VoxelShape north) {
        VoxelShape[] out = new VoxelShape[4];
        out[0] = north;
        for (int q = 1; q < 4; q++) {
            VoxelShape r = VoxelShapes.empty();
            for (Box b : out[q - 1].getBoundingBoxes()) // 90° clockwise around the center: (x, z) -> (1 - z, x)
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

    /** Total height in blocks (for the tooltip). */
    public int heightBlocks() { return 1 + parts; }

    /** Shape of the base facing facing. */
    public VoxelShape baseShape(Direction facing) {
        return baseShapes[quarter(facing)];
    }

    /** Shape of the part at that level (1 or 2) facing facing. */
    public VoxelShape partShape(int level, Direction facing) {
        return (level >= 2 ? part2Shapes : part1Shapes)[quarter(facing)];
    }

    /** Real price (server): the configured one. This is what gets charged. */
    public long price() {
        return LuxuryConfig.INSTANCE.price(this);
    }

    /** Price to show on the client: the last one the server sent (catalog) or, failing that, the local config. */
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

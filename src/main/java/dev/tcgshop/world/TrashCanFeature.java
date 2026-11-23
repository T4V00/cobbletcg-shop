package dev.tcgshop.world;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.TallPlantBlock;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.util.FeatureContext;

/**
 * Natural trash cans (1.0), added to the biomes by {@link WorldLoot#register()} in the vegetal decoration step,
 * which runs after the chunk's structures (villages) are built.
 * <ul>
 *   <li>street: looks for a spot next to a village street (dirt path) inside the chunk and places one can facing
 *   the path ({@link WorldLootConfig#streetCanChance} per chunk that has a street; refills);</li>
 *   <li>wild: a lonely can on grass/dirt/sand at the placed feature's position, in plains-like biomes
 *   ({@link WorldLootConfig#wildCanChance} per chunk; searched once, never refills).</li>
 * </ul>
 * Only blocks of the feature's own chunk are read or written.
 */
public class TrashCanFeature extends Feature<DefaultFeatureConfig> {
    private static final int STREET_TRIES = 48;
    private final boolean street;

    public TrashCanFeature(boolean street) {
        super(DefaultFeatureConfig.CODEC);
        this.street = street;
    }

    @Override
    public boolean generate(FeatureContext<DefaultFeatureConfig> ctx) {
        WorldLootConfig c = WorldLootConfig.INSTANCE;
        StructureWorldAccess world = ctx.getWorld();
        Random r = ctx.getRandom();
        BlockPos origin = ctx.getOrigin();
        if (street) {
            if (r.nextDouble() >= c.streetCanChance) return false;
            ChunkPos cp = new ChunkPos(origin);
            for (int i = 0; i < STREET_TRIES; i++) {
                // stay one block inside the chunk: neighbours are read and must belong to this chunk
                int x = cp.getStartX() + 1 + r.nextInt(14), z = cp.getStartZ() + 1 + r.nextInt(14);
                BlockPos pos = new BlockPos(x, world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z), z);
                if (!canStand(world, pos) || nearDoor(world, pos)) continue;
                Direction toPath = null;
                for (Direction d : Direction.Type.HORIZONTAL) {
                    if (world.getBlockState(pos.down().offset(d)).isOf(Blocks.DIRT_PATH)) { toPath = d; break; }
                }
                if (toPath == null) continue;
                world.setBlockState(pos, TrashCanBlock.natural(TrashCanBlock.LootKind.STREET, toPath), Block.NOTIFY_ALL);
                return true;
            }
            return false;
        }
        if (r.nextDouble() >= c.wildCanChance) return false;
        BlockPos pos = origin;
        if (!canStand(world, pos) || !isNaturalGround(world.getBlockState(pos.down()))) return false;
        // wild cans are searched once and never refill (loot=wild)
        world.setBlockState(pos, TrashCanBlock.natural(TrashCanBlock.LootKind.WILD, Direction.Type.HORIZONTAL.random(r)), Block.NOTIFY_ALL);
        return true;
    }

    /** Air (or a small plant) with a solid, full-topped, non-path block below and free space above. */
    private static boolean canStand(StructureWorldAccess world, BlockPos pos) {
        BlockState at = world.getBlockState(pos);
        if (!(at.isAir() || (at.isIn(BlockTags.REPLACEABLE_BY_TREES) && !(at.getBlock() instanceof TallPlantBlock) && at.getFluidState().isEmpty())))
            return false;
        BlockState above = world.getBlockState(pos.up());
        if (!above.isAir() && !above.isIn(BlockTags.LEAVES)) return false;
        BlockPos down = pos.down();
        BlockState below = world.getBlockState(down);
        return !below.isOf(Blocks.DIRT_PATH) && below.getFluidState().isEmpty() && below.isSideSolidFullSquare(world, down, Direction.UP)
                && !below.isIn(BlockTags.LEAVES) && !(below.getBlock() instanceof net.minecraft.block.BlockWithEntity);
    }

    private static boolean isNaturalGround(BlockState s) {
        return s.isOf(Blocks.GRASS_BLOCK) || s.isOf(Blocks.DIRT) || s.isOf(Blocks.COARSE_DIRT) || s.isOf(Blocks.PODZOL)
                || s.isOf(Blocks.SAND) || s.isOf(Blocks.RED_SAND) || s.isOf(Blocks.SNOW_BLOCK);
    }

    /** Doors next to the spot (villagers and players must still get in). */
    private static boolean nearDoor(StructureWorldAccess world, BlockPos pos) {
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++)
                for (int dy = 0; dy <= 1; dy++)
                    if (world.getBlockState(pos.add(dx, dy, dz)).getBlock() instanceof DoorBlock) return true;
        return false;
    }
}

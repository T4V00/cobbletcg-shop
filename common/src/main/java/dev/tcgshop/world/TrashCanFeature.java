package dev.tcgshop.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

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
public class TrashCanFeature extends Feature<NoneFeatureConfiguration> {
    private static final int STREET_TRIES = 48;
    private final boolean street;

    public TrashCanFeature(boolean street) {
        super(NoneFeatureConfiguration.CODEC);
        this.street = street;
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldLootConfig c = WorldLootConfig.INSTANCE;
        WorldGenLevel world = ctx.level();
        RandomSource r = ctx.random();
        BlockPos origin = ctx.origin();
        if (street) {
            if (r.nextDouble() >= c.streetCanChance) return false;
            ChunkPos cp = new ChunkPos(origin);
            for (int i = 0; i < STREET_TRIES; i++) {
                // stay one block inside the chunk: neighbours are read and must belong to this chunk
                int x = cp.getMinBlockX() + 1 + r.nextInt(14), z = cp.getMinBlockZ() + 1 + r.nextInt(14);
                BlockPos pos = new BlockPos(x, world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
                if (!canStand(world, pos) || nearDoor(world, pos)) continue;
                Direction toPath = null;
                for (Direction d : Direction.Plane.HORIZONTAL) {
                    if (world.getBlockState(pos.below().relative(d)).is(Blocks.DIRT_PATH)) { toPath = d; break; }
                }
                if (toPath == null) continue;
                world.setBlock(pos, TrashCanBlock.natural(TrashCanBlock.LootKind.STREET, toPath), Block.UPDATE_ALL);
                return true;
            }
            return false;
        }
        if (r.nextDouble() >= c.wildCanChance) return false;
        BlockPos pos = origin;
        if (!canStand(world, pos) || !isNaturalGround(world.getBlockState(pos.below()))) return false;
        // wild cans are searched once and never refill (loot=wild)
        world.setBlock(pos, TrashCanBlock.natural(TrashCanBlock.LootKind.WILD, Direction.Plane.HORIZONTAL.getRandomDirection(r)), Block.UPDATE_ALL);
        return true;
    }

    /** Air (or a small plant) with a solid, full-topped, non-path block below and free space above. */
    private static boolean canStand(WorldGenLevel world, BlockPos pos) {
        BlockState at = world.getBlockState(pos);
        if (!(at.isAir() || (at.is(BlockTags.REPLACEABLE_BY_TREES) && !(at.getBlock() instanceof DoublePlantBlock) && at.getFluidState().isEmpty())))
            return false;
        BlockState above = world.getBlockState(pos.above());
        if (!above.isAir() && !above.is(BlockTags.LEAVES)) return false;
        BlockPos down = pos.below();
        BlockState below = world.getBlockState(down);
        return !below.is(Blocks.DIRT_PATH) && below.getFluidState().isEmpty() && below.isFaceSturdy(world, down, Direction.UP)
                && !below.is(BlockTags.LEAVES) && !(below.getBlock() instanceof net.minecraft.world.level.block.BaseEntityBlock);
    }

    private static boolean isNaturalGround(BlockState s) {
        return s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.DIRT) || s.is(Blocks.COARSE_DIRT) || s.is(Blocks.PODZOL)
                || s.is(Blocks.SAND) || s.is(Blocks.RED_SAND) || s.is(Blocks.SNOW_BLOCK);
    }

    /** Doors next to the spot (villagers and players must still get in). */
    private static boolean nearDoor(WorldGenLevel world, BlockPos pos) {
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++)
                for (int dy = 0; dy <= 1; dy++)
                    if (world.getBlockState(pos.offset(dx, dy, dz)).getBlock() instanceof DoorBlock) return true;
        return false;
    }
}

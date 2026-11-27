package dev.tcgshop.luxury;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Invisible part above a tall statue (level 1 = right above the base, 2 = the next one). Fills the space that
 * the base model draws, has collision fitted to the figure and, on the MasterBall statue, level 2 draws the top
 * of the orb and the crown. Breaking it breaks the whole statue (and drops the statue); if orphaned, it is removed.
 * It has no item of its own and drops nothing.
 */
public class StatuePartBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty LEVEL = IntegerProperty.create("level", 1, 2);
    public static final IntegerProperty TIER = IntegerProperty.create("tier", 1, 7);

    public StatuePartBlock(Properties settings) {
        super(settings);
        registerDefaultState(getStateDefinition().any().setValue(FACING, Direction.NORTH).setValue(LEVEL, 1).setValue(TIER, 3));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LEVEL, TIER);
    }

    private static StatueTier tierOf(BlockState state) {
        StatueTier t = StatueTier.byIndex(state.getValue(TIER));
        return t == null ? StatueTier.GOLD : t;
    }

    private static BlockPos basePos(BlockPos pos, BlockState state) {
        return pos.below(state.getValue(LEVEL));
    }

    @Override
    public BlockState playerWillDestroy(Level world, BlockPos pos, BlockState state, Player player) {
        if (!world.isClientSide) {
            BlockPos base = basePos(pos, state);
            BlockState bs = world.getBlockState(base);
            // base in someone else's zone: not broken from here (StatueProtection already prevents it earlier; just in case)
            if (bs.getBlock() instanceof StatueBlock && StatueProtection.canBuild(world, player, base)) {
                // breaking the part = breaking the statue (in creative without dropping anything, like doors)
                if (player.isCreative()) world.setBlock(base, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                else world.destroyBlock(base, true, player);
            }
        }
        return super.playerWillDestroy(world, pos, state, player);
    }

    @Override
    protected void onRemove(BlockState state, Level world, BlockPos pos, BlockState newState, boolean moved) {
        // removed without a player (commands, explosions...): break the base, which drops the statue
        if (!world.isClientSide && !state.is(newState.getBlock())) {
            BlockPos base = basePos(pos, state);
            if (world.getBlockState(base).getBlock() instanceof StatueBlock) world.destroyBlock(base, true);
        }
        super.onRemove(state, world, pos, newState, moved);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor world,
                                                   BlockPos pos, BlockPos neighborPos) {
        if (direction == Direction.DOWN) {
            int level = state.getValue(LEVEL);
            boolean ok = level == 1
                    ? neighborState.getBlock() instanceof StatueBlock
                    : neighborState.is(this) && neighborState.getValue(LEVEL) == level - 1;
            if (!ok) return Blocks.AIR.defaultBlockState(); // orphaned
        }
        return super.updateShape(state, direction, neighborState, world, pos, neighborPos);
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader world, BlockPos pos, BlockState state) {
        return new ItemStack(LuxuryBlocks.item(tierOf(state)));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return tierOf(state).partShape(state.getValue(LEVEL), state.getValue(FACING));
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return tierOf(state).partShape(state.getValue(LEVEL), state.getValue(FACING));
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter world, BlockPos pos) {
        return true;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
}

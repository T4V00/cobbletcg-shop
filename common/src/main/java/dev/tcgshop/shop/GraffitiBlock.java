package dev.tcgshop.shop;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 1.1 (cleanliness update): graffiti that rowdy customers paint on the walls of a grimy shop ({@link Cleanliness#tick}).
 * A flat layer on the wall (its back on the opposite side of FACING, like the wall freshener) with no item and no drops;
 * it can't be broken by hand, pistons, water or explosions. Wall air fresheners erase the nearest one with each puff, and
 * the deodorant spray scrubs one off by hand (shop owner). Each one counts like a dropping in the cleanliness score.
 */
public class GraffitiBlock extends Block {
    public static final MapCodec<GraffitiBlock> CODEC = simpleCodec(GraffitiBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** Number of designs (textures graffiti_0..3, tools/gen_clean_assets.py). */
    public static final int VARIANTS = 4;
    public static final IntegerProperty VARIANT = IntegerProperty.create("variant", 0, VARIANTS - 1);

    private static final VoxelShape N = Block.box(0, 0, 15, 16, 16, 16), S = Block.box(0, 0, 0, 16, 16, 1),
            E = Block.box(0, 0, 0, 1, 16, 16), W = Block.box(15, 0, 0, 16, 16, 16);

    public GraffitiBlock(Properties settings) {
        super(settings);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(VARIANT, 0));
    }

    @Override
    protected MapCodec<? extends Block> codec() { return CODEC; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING, VARIANT); }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> S;
            case EAST -> E;
            case WEST -> W;
            default -> N;
        };
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected VoxelShape getVisualShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter world, BlockPos pos) { return true; }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader world, BlockPos pos) {
        Direction f = state.getValue(FACING);
        BlockPos back = pos.relative(f.getOpposite());
        return world.getBlockState(back).isFaceSturdy(world, back, f);
    }

    /** The wall is gone: so is the paint (the zone list is pruned on the next tick). */
    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor world,
                                     BlockPos pos, BlockPos neighborPos) {
        if (direction == state.getValue(FACING).getOpposite() && !canSurvive(state, world, pos)) return Blocks.AIR.defaultBlockState();
        return super.updateShape(state, direction, neighborState, world, pos, neighborPos);
    }

    /** Left behind by a shop that moved (or no shop tracks it): it fades away. */
    @Override
    protected void randomTick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
        if (Cleanliness.untracked(world, pos, true)) world.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (world.isClientSide) return ItemInteractionResult.SUCCESS;
        if (player instanceof ServerPlayer sp) Cleanliness.useGraffiti((ServerLevel) world, pos, sp, stack, hand);
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit) {
        if (world.isClientSide) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer sp) Cleanliness.useGraffiti((ServerLevel) world, pos, sp, ItemStack.EMPTY, InteractionHand.MAIN_HAND);
        return InteractionResult.SUCCESS;
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader world, BlockPos pos, BlockState state) { return ItemStack.EMPTY; }

    @Override
    public boolean dropFromExplosion(Explosion explosion) { return false; }
}

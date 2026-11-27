package dev.tcgshop.shop;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 1.1 (cleanliness update): droppings left in a shop by customers or Pokémon ({@link Cleanliness#tick}). A flat,
 * carpet-like layer with no item and no drops: it can't be broken by hand, pistons, water or explosions; only the mop
 * cleans it (the shop owner, anyone outside shops). A big pile (from a Pokémon) takes two swipes.
 */
public class PoopBlock extends Block {
    public static final MapCodec<PoopBlock> CODEC = simpleCodec(PoopBlock::new);
    public static final BooleanProperty BIG = BooleanProperty.create("big");
    private static final VoxelShape SMALL_SHAPE = Block.box(3, 0, 3, 13, 2, 13), BIG_SHAPE = Block.box(1, 0, 1, 15, 3, 15);

    public PoopBlock(Properties settings) {
        super(settings);
        registerDefaultState(stateDefinition.any().setValue(BIG, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() { return CODEC; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(BIG); }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return state.getValue(BIG) ? BIG_SHAPE : SMALL_SHAPE;
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
        return world.getBlockState(pos.below()).isFaceSturdy(world, pos.below(), Direction.UP);
    }

    /** No floor below any more: it goes away (the zone list is pruned on the next tick). */
    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor world,
                                     BlockPos pos, BlockPos neighborPos) {
        if (direction == Direction.DOWN && !canSurvive(state, world, pos)) return Blocks.AIR.defaultBlockState();
        return super.updateShape(state, direction, neighborState, world, pos, neighborPos);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        // always consume the click: no block, bucket or bone meal goes "through" the dropping
        if (world.isClientSide) return ItemInteractionResult.SUCCESS;
        if (player instanceof ServerPlayer sp) Cleanliness.usePoop((ServerLevel) world, pos, state, sp, stack, hand);
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit) {
        if (world.isClientSide) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer sp) Cleanliness.usePoop((ServerLevel) world, pos, state, sp, ItemStack.EMPTY, InteractionHand.MAIN_HAND);
        return InteractionResult.SUCCESS;
    }

    /** Left behind by a shop that moved (or no shop tracks it): it goes away. */
    @Override
    protected void randomTick(BlockState state, ServerLevel world, BlockPos pos, net.minecraft.util.RandomSource random) {
        if (Cleanliness.untracked(world, pos, false)) world.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader world, BlockPos pos, BlockState state) { return ItemStack.EMPTY; }

    @Override
    public boolean dropFromExplosion(Explosion explosion) { return false; }
}

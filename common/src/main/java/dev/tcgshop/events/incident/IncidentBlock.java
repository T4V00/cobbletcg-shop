package dev.tcgshop.events.incident;

import com.mojang.serialization.MapCodec;
import dev.tcgshop.shop.TcgBlocks;
import java.util.EnumMap;
import java.util.Map;
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
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * tcgshop:incident — puddle, debris, dirt, droppings, scorch mark, sparks, mess or package. Placed ONLY by the
 * "incident" effect (there is never an item or creative tab) in AIR inside the shop, and removes itself once its
 * event is no longer active or unfixed.
 *
 * Safety (critique E): no collision, no drops (dropsNothing), unbreakable by hand and explosion-resistant,
 * PistonBehavior.BLOCK, "solid" so water does not wash it away, non-flammable, no pick-block. Only the shop owner
 * (or an op in creative) fixes it, with the tool it asks for (never buckets or fire).
 */
public class IncidentBlock extends BaseEntityBlock {
    public static final MapCodec<IncidentBlock> CODEC = simpleCodec(IncidentBlock::new);
    public static final EnumProperty<IncidentKind> KIND = EnumProperty.create("kind", IncidentKind.class);

    private static final Map<IncidentKind, VoxelShape> SHAPES = new EnumMap<>(IncidentKind.class);

    static {
        for (IncidentKind k : IncidentKind.values())
            SHAPES.put(k, k == IncidentKind.PACKAGE ? Block.box(3, 0, 3, 13, k.height, 13)
                    : Block.box(0, 0, 0, 16, k.height, 16));
    }

    public IncidentBlock(Properties settings) {
        super(settings);
        registerDefaultState(getStateDefinition().any().setValue(KIND, IncidentKind.PUDDLE));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(KIND); }

    @Override
    protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(KIND));
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
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new IncidentBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level world, BlockState state, BlockEntityType<T> type) {
        return world.isClientSide ? null : createTickerHelper(type, TcgBlocks.INCIDENT_BE, (w, p, s, be) -> be.serverTick((ServerLevel) w, s));
    }

    // ------------------------------------------------------------------ use (owner only)

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        // empty hand: handled by onUse (which also tries the tool in the other hand)
        if (stack.isEmpty()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        // always consume the click: that way no bucket, lighter or block gets used "through" the incident
        if (world.isClientSide) return ItemInteractionResult.SUCCESS;
        if (player instanceof ServerPlayer sp) IncidentModule.onPlayerUse((ServerLevel) world, pos, state, sp, stack, hand);
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit) {
        if (world.isClientSide) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer sp) IncidentModule.onPlayerUse((ServerLevel) world, pos, state, sp, ItemStack.EMPTY, InteractionHand.MAIN_HAND);
        return InteractionResult.SUCCESS;
    }

    // ------------------------------------------------------------------ cleanup

    @Override
    protected void onRemove(BlockState state, Level world, BlockPos pos, BlockState newState, boolean moved) {
        if (!world.isClientSide && !state.is(newState.getBlock()) && world.getBlockEntity(pos) instanceof IncidentBlockEntity be)
            IncidentModule.onBlockGone((ServerLevel) world, pos, be);
        super.onRemove(state, world, pos, newState, moved);
    }

    /** No floor below (the owner removes the block underneath): it disappears (onStateReplaced counts it as removed). */
    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor world,
                                                   BlockPos pos, BlockPos neighborPos) {
        if (direction == Direction.DOWN && !neighborState.isFaceSturdy(world, neighborPos, Direction.UP))
            return Blocks.AIR.defaultBlockState();
        return super.updateShape(state, direction, neighborState, world, pos, neighborPos);
    }

    /** Safety net: a block without a block entity, or from an event that is no longer running, removes itself. */
    @Override
    protected void randomTick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
        if (!(world.getBlockEntity(pos) instanceof IncidentBlockEntity be) || !be.stillWanted(world)) IncidentModule.removeSilently(world, pos);
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader world, BlockPos pos, BlockState state) { return ItemStack.EMPTY; }

    @Override
    public boolean dropFromExplosion(Explosion explosion) { return false; }
}

package dev.tcgshop.events.incident;

import com.mojang.serialization.MapCodec;
import dev.tcgshop.shop.TcgBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldView;
import net.minecraft.world.explosion.Explosion;

import java.util.EnumMap;
import java.util.Map;

/**
 * tcgshop:incident — puddle, debris, dirt, droppings, scorch mark, sparks, mess or package. Placed ONLY by the
 * "incident" effect (there is never an item or creative tab) in AIR inside the shop, and removes itself once its
 * event is no longer active or unfixed.
 *
 * Safety (critique E): no collision, no drops (dropsNothing), unbreakable by hand and explosion-resistant,
 * PistonBehavior.BLOCK, "solid" so water does not wash it away, non-flammable, no pick-block. Only the shop owner
 * (or an op in creative) fixes it, with the tool it asks for (never buckets or fire).
 */
public class IncidentBlock extends BlockWithEntity {
    public static final MapCodec<IncidentBlock> CODEC = createCodec(IncidentBlock::new);
    public static final EnumProperty<IncidentKind> KIND = EnumProperty.of("kind", IncidentKind.class);

    private static final Map<IncidentKind, VoxelShape> SHAPES = new EnumMap<>(IncidentKind.class);

    static {
        for (IncidentKind k : IncidentKind.values())
            SHAPES.put(k, k == IncidentKind.PACKAGE ? Block.createCuboidShape(3, 0, 3, 13, k.height, 13)
                    : Block.createCuboidShape(0, 0, 0, 16, k.height, 16));
    }

    public IncidentBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(KIND, IncidentKind.PUDDLE));
    }

    @Override
    protected MapCodec<? extends BlockWithEntity> getCodec() { return CODEC; }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) { builder.add(KIND); }

    @Override
    protected BlockRenderType getRenderType(BlockState state) { return BlockRenderType.MODEL; }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPES.get(state.get(KIND));
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return VoxelShapes.empty();
    }

    @Override
    protected VoxelShape getCameraCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return VoxelShapes.empty();
    }

    @Override
    protected boolean isTransparent(BlockState state, BlockView world, BlockPos pos) { return true; }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) { return new IncidentBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return world.isClient ? null : validateTicker(type, TcgBlocks.INCIDENT_BE, (w, p, s, be) -> be.serverTick((ServerWorld) w, s));
    }

    // ------------------------------------------------------------------ use (owner only)

    @Override
    protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        // empty hand: handled by onUse (which also tries the tool in the other hand)
        if (stack.isEmpty()) return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        // always consume the click: that way no bucket, lighter or block gets used "through" the incident
        if (world.isClient) return ItemActionResult.SUCCESS;
        if (player instanceof ServerPlayerEntity sp) IncidentModule.onPlayerUse((ServerWorld) world, pos, state, sp, stack, hand);
        return ItemActionResult.SUCCESS;
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (world.isClient) return ActionResult.SUCCESS;
        if (player instanceof ServerPlayerEntity sp) IncidentModule.onPlayerUse((ServerWorld) world, pos, state, sp, ItemStack.EMPTY, Hand.MAIN_HAND);
        return ActionResult.SUCCESS;
    }

    // ------------------------------------------------------------------ cleanup

    @Override
    protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!world.isClient && !state.isOf(newState.getBlock()) && world.getBlockEntity(pos) instanceof IncidentBlockEntity be)
            IncidentModule.onBlockGone((ServerWorld) world, pos, be);
        super.onStateReplaced(state, world, pos, newState, moved);
    }

    /** No floor below (the owner removes the block underneath): it disappears (onStateReplaced counts it as removed). */
    @Override
    protected BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighborState, WorldAccess world,
                                                   BlockPos pos, BlockPos neighborPos) {
        if (direction == Direction.DOWN && !neighborState.isSideSolidFullSquare(world, neighborPos, Direction.UP))
            return Blocks.AIR.getDefaultState();
        return super.getStateForNeighborUpdate(state, direction, neighborState, world, pos, neighborPos);
    }

    /** Safety net: a block without a block entity, or from an event that is no longer running, removes itself. */
    @Override
    protected void randomTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        if (!(world.getBlockEntity(pos) instanceof IncidentBlockEntity be) || !be.stillWanted(world)) IncidentModule.removeSilently(world, pos);
    }

    @Override
    public ItemStack getPickStack(WorldView world, BlockPos pos, BlockState state) { return ItemStack.EMPTY; }

    @Override
    public boolean shouldDropItemsOnExplosion(Explosion explosion) { return false; }
}

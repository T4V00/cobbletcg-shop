package dev.tcgshop.casino;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * A casino machine / table. Right click: play. Owner, sneaking with an empty hand: collect the tray (their share
 * of other players' bets and the house result of the customers' rounds) and see the machine's statistics.
 * Placeable anywhere; only the owner (or creative mode) can break it, and breaking it pays out the tray.
 */
public class CasinoMachineBlock extends BlockWithEntity {
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    public final CasinoGame game;
    private final VoxelShape north, south, east, west;

    public CasinoMachineBlock(CasinoGame game, VoxelShape north, Settings settings) {
        super(settings);
        this.game = game;
        this.north = north;
        this.east = rotate(north, 1);
        this.south = rotate(north, 2);
        this.west = rotate(north, 3);
        setDefaultState(getDefaultState().with(FACING, Direction.NORTH));
    }

    /** Rotates a north-facing shape clockwise (seen from above) by quarter turns, like the blockstate "y" rotation. */
    private static VoxelShape rotate(VoxelShape shape, int quarters) {
        VoxelShape[] out = {VoxelShapes.empty()};
        shape.forEachBox((x0, y0, z0, x1, y1, z1) -> {
            double ax = x0, az = z0, bx = x1, bz = z1;
            for (int i = 0; i < quarters; i++) { // (x, z) -> (1 - z, x)
                double nax = 1 - bz, nbx = 1 - az, naz = ax, nbz = bx;
                ax = nax;
                bx = nbx;
                az = naz;
                bz = nbz;
            }
            out[0] = VoxelShapes.union(out[0], VoxelShapes.cuboid(Math.min(ax, bx), y0, Math.min(az, bz), Math.max(ax, bx), y1, Math.max(az, bz)));
        });
        return out[0];
    }

    @Override
    protected MapCodec<? extends BlockWithEntity> getCodec() {
        return createCodec(s -> new CasinoMachineBlock(game, north, s));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return switch (state.get(FACING)) {
            case SOUTH -> south;
            case EAST -> east;
            case WEST -> west;
            default -> north;
        };
    }

    @Override
    protected BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new CasinoBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return world.isClient ? null : validateTicker(type, CasinoBlocks.MACHINE_BE, (w, p, s, be) -> be.serverTick((ServerWorld) w));
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.onPlaced(world, pos, state, placer, stack);
        if (!world.isClient && placer instanceof PlayerEntity p && world.getBlockEntity(pos) instanceof CasinoBlockEntity be) {
            be.owner = p.getUuid();
            be.ownerName = p.getName().getString();
            be.sync();
        }
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!world.isClient && player instanceof ServerPlayerEntity sp && world.getBlockEntity(pos) instanceof CasinoBlockEntity be) {
            if (sp.isSneaking() && be.isOwner(sp)) Casino.collect(sp, be);
            else Casino.open(sp, pos, game);
        }
        return ActionResult.success(world.isClient);
    }

    /** Only the owner (or a creative player) can break a machine; machines placed before an owner existed are free. */
    @Override
    protected float calcBlockBreakingDelta(BlockState state, PlayerEntity player, BlockView world, BlockPos pos) {
        if (!player.isCreative() && world.getBlockEntity(pos) instanceof CasinoBlockEntity be && be.owner != null && !be.isOwner(player)) return 0f;
        return super.calcBlockBreakingDelta(state, player, world, pos);
    }

    @Override
    public BlockState onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        if (!world.isClient && player instanceof ServerPlayerEntity sp && world.getBlockEntity(pos) instanceof CasinoBlockEntity be
                && be.tray > 0 && (be.owner == null || be.isOwner(sp)))
            Casino.collect(sp, be);
        return super.onBreak(world, pos, state, player);
    }
}

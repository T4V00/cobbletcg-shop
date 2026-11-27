package dev.tcgshop.casino;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A casino machine / table. Right click: play. Owner, sneaking with an empty hand: collect the tray (their share
 * of other players' bets and the house result of the customers' rounds) and see the machine's statistics.
 * Placeable anywhere; only the owner (or creative mode) can break it, and breaking it pays out the tray.
 */
public class CasinoMachineBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public final CasinoGame game;
    private final VoxelShape north, south, east, west;

    public CasinoMachineBlock(CasinoGame game, VoxelShape north, Properties settings) {
        super(settings);
        this.game = game;
        this.north = north;
        this.east = rotate(north, 1);
        this.south = rotate(north, 2);
        this.west = rotate(north, 3);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    /** Rotates a north-facing shape clockwise (seen from above) by quarter turns, like the blockstate "y" rotation. */
    private static VoxelShape rotate(VoxelShape shape, int quarters) {
        VoxelShape[] out = {Shapes.empty()};
        shape.forAllBoxes((x0, y0, z0, x1, y1, z1) -> {
            double ax = x0, az = z0, bx = x1, bz = z1;
            for (int i = 0; i < quarters; i++) { // (x, z) -> (1 - z, x)
                double nax = 1 - bz, nbx = 1 - az, naz = ax, nbz = bx;
                ax = nax;
                bx = nbx;
                az = naz;
                bz = nbz;
            }
            out[0] = Shapes.or(out[0], Shapes.box(Math.min(ax, bx), y0, Math.min(az, bz), Math.max(ax, bx), y1, Math.max(az, bz)));
        });
        return out[0];
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(s -> new CasinoMachineBlock(game, north, s));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> south;
            case EAST -> east;
            case WEST -> west;
            default -> north;
        };
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CasinoBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level world, BlockState state, BlockEntityType<T> type) {
        return world.isClientSide ? null : createTickerHelper(type, CasinoBlocks.MACHINE_BE, (w, p, s, be) -> be.serverTick((ServerLevel) w));
    }

    @Override
    public void setPlacedBy(Level world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(world, pos, state, placer, stack);
        if (!world.isClientSide && placer instanceof Player p && world.getBlockEntity(pos) instanceof CasinoBlockEntity be) {
            be.owner = p.getUUID();
            be.ownerName = p.getName().getString();
            be.sync();
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit) {
        if (!world.isClientSide && player instanceof ServerPlayer sp && world.getBlockEntity(pos) instanceof CasinoBlockEntity be) {
            if (sp.isShiftKeyDown() && be.isOwner(sp)) Casino.collect(sp, be);
            else Casino.open(sp, pos, game);
        }
        return InteractionResult.sidedSuccess(world.isClientSide);
    }

    /** Only the owner (or a creative player) can break a machine; machines placed before an owner existed are free. */
    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter world, BlockPos pos) {
        if (!player.isCreative() && world.getBlockEntity(pos) instanceof CasinoBlockEntity be && be.owner != null && !be.isOwner(player)) return 0f;
        return super.getDestroyProgress(state, player, world, pos);
    }

    @Override
    public BlockState playerWillDestroy(Level world, BlockPos pos, BlockState state, Player player) {
        if (!world.isClientSide && player instanceof ServerPlayer sp && world.getBlockEntity(pos) instanceof CasinoBlockEntity be
                && be.tray > 0 && (be.owner == null || be.isOwner(sp)))
            Casino.collect(sp, be);
        return super.playerWillDestroy(world, pos, state, player);
    }
}

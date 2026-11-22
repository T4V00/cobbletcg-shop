package dev.tcgshop.luxury;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldView;

/**
 * Pieza invisible encima de una estatua alta (nivel 1 = justo encima de la base, 2 = la siguiente). Ocupa el hueco que
 * pinta el modelo de la base, tiene colisión ajustada a la figura y, en la estatua MasterBall, el nivel 2 pinta la parte
 * de arriba del orbe y la corona. Romperla rompe la estatua entera (y suelta la estatua); si se queda huérfana, se quita.
 * No tiene objeto propio ni suelta nada.
 */
public class StatuePartBlock extends Block {
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    public static final IntProperty LEVEL = IntProperty.of("level", 1, 2);
    public static final IntProperty TIER = IntProperty.of("tier", 1, 7);

    public StatuePartBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH).with(LEVEL, 1).with(TIER, 3));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, LEVEL, TIER);
    }

    private static StatueTier tierOf(BlockState state) {
        StatueTier t = StatueTier.byIndex(state.get(TIER));
        return t == null ? StatueTier.GOLD : t;
    }

    private static BlockPos basePos(BlockPos pos, BlockState state) {
        return pos.down(state.get(LEVEL));
    }

    @Override
    public BlockState onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        if (!world.isClient) {
            BlockPos base = basePos(pos, state);
            BlockState bs = world.getBlockState(base);
            // la base en una zona ajena: no se rompe desde aquí (StatueProtection ya lo impide antes; por si acaso)
            if (bs.getBlock() instanceof StatueBlock && StatueProtection.canBuild(world, player, base)) {
                // romper la pieza = romper la estatua (en creativo sin soltar nada, como las puertas)
                if (player.isCreative()) world.setBlockState(base, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL | Block.SKIP_DROPS);
                else world.breakBlock(base, true, player);
            }
        }
        return super.onBreak(world, pos, state, player);
    }

    @Override
    protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        // quitada sin jugador (comandos, explosiones...): se rompe la base, que suelta la estatua
        if (!world.isClient && !state.isOf(newState.getBlock())) {
            BlockPos base = basePos(pos, state);
            if (world.getBlockState(base).getBlock() instanceof StatueBlock) world.breakBlock(base, true);
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }

    @Override
    protected BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighborState, WorldAccess world,
                                                   BlockPos pos, BlockPos neighborPos) {
        if (direction == Direction.DOWN) {
            int level = state.get(LEVEL);
            boolean ok = level == 1
                    ? neighborState.getBlock() instanceof StatueBlock
                    : neighborState.isOf(this) && neighborState.get(LEVEL) == level - 1;
            if (!ok) return Blocks.AIR.getDefaultState(); // huérfana
        }
        return super.getStateForNeighborUpdate(state, direction, neighborState, world, pos, neighborPos);
    }

    @Override
    public ItemStack getPickStack(WorldView world, BlockPos pos, BlockState state) {
        return new ItemStack(LuxuryBlocks.item(tierOf(state)));
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return tierOf(state).partShape(state.get(LEVEL), state.get(FACING));
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return tierOf(state).partShape(state.get(LEVEL), state.get(FACING));
    }

    @Override
    protected boolean isTransparent(BlockState state, BlockView world, BlockPos pos) {
        return true;
    }

    @Override
    protected BlockState rotate(BlockState state, BlockRotation rotation) {
        return state.with(FACING, rotation.rotate(state.get(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, BlockMirror mirror) {
        return state.rotate(mirror.getRotation(state.get(FACING)));
    }
}

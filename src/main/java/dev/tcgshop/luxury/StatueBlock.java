package dev.tcgshop.luxury;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Base de una estatua de lujo. Mira hacia el jugador al colocarla. Las de más de un bloque de alto necesitan el hueco
 * libre encima: colocan ahí piezas invisibles ({@link StatuePartBlock}) que ocupan el espacio y se quitan con la base.
 * La colisión se queda dentro de cada bloque (robusto con el movimiento del jugador y los mobs); el modelo puede ser
 * más ancho (los niveles 6 y 7 sobresalen un poco por los lados).
 */
public class StatueBlock extends Block {
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    public final StatueTier tier;

    public StatueBlock(StatueTier tier, Settings settings) {
        super(settings);
        this.tier = tier;
        setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /** ¿Hay hueco (altura del mundo y bloques reemplazables) para las piezas de encima? */
    static boolean fitsAbove(ItemPlacementContext ctx, int parts) {
        World world = ctx.getWorld();
        BlockPos pos = ctx.getBlockPos();
        for (int i = 1; i <= parts; i++) {
            BlockPos p = pos.up(i);
            if (p.getY() >= world.getTopY() || !world.getBlockState(p).canReplace(ctx)) return false;
        }
        return true;
    }

    /** Pieza de encima de la estatua de ese nivel, mirando hacia facing. */
    static BlockState partState(StatueTier tier, Direction facing, int level) {
        return LuxuryBlocks.STATUE_PART.getDefaultState()
                .with(StatuePartBlock.FACING, facing)
                .with(StatuePartBlock.LEVEL, level)
                .with(StatuePartBlock.TIER, tier.index());
    }

    @Nullable
    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        World world = ctx.getWorld();
        BlockPos pos = ctx.getBlockPos();
        PlayerEntity player = ctx.getPlayer();
        Direction facing = ctx.getHorizontalPlayerFacing().getOpposite();
        if (!fitsAbove(ctx, tier.parts)) return null; // no cabe
        // ninguna pieza puede caer en una zona de tienda ajena (ShopProtection sólo mira la base)
        if (StatueProtection.blockingStatue(world, player, pos, tier) != null) return null;
        // ni dentro de un jugador o un mob (BlockItem sólo comprueba las entidades contra la base)
        ShapeContext sc = player == null ? ShapeContext.absent() : ShapeContext.of(player);
        for (int i = 1; i <= tier.parts; i++)
            if (!world.canPlace(partState(tier, facing, i), pos.up(i), sc)) return null;
        return getDefaultState().with(FACING, facing);
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.onPlaced(world, pos, state, placer, stack);
        if (world.isClient) return;
        for (int i = 1; i <= tier.parts; i++)
            world.setBlockState(pos.up(i), partState(tier, state.get(FACING), i), Block.NOTIFY_ALL);
    }

    @Override
    protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock()) && !world.isClient) {
            // se quitan las piezas de encima (no sueltan nada: la base ya suelta la estatua)
            for (int i = 1; i <= tier.parts; i++) {
                BlockPos p = pos.up(i);
                BlockState s = world.getBlockState(p);
                if (s.isOf(LuxuryBlocks.STATUE_PART) && s.get(StatuePartBlock.LEVEL) == i)
                    world.setBlockState(p, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL | Block.SKIP_DROPS);
            }
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return tier.baseShape(state.get(FACING));
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return tier.baseShape(state.get(FACING));
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

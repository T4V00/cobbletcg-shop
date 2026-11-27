package dev.tcgshop.luxury;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Base of a luxury statue. Faces the player when placed. Statues taller than one block need the space above to be
 * free: they place invisible parts there ({@link StatuePartBlock}) that fill the space and are removed with the base.
 * Collision stays within each block (robust against player and mob movement); the model can be
 * wider (tiers 6 and 7 stick out a bit on the sides).
 */
public class StatueBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public final StatueTier tier;

    public StatueBlock(StatueTier tier, Properties settings) {
        super(settings);
        this.tier = tier;
        registerDefaultState(getStateDefinition().any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /** Is there room (world height and replaceable blocks) for the parts above? */
    static boolean fitsAbove(BlockPlaceContext ctx, int parts) {
        Level world = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        for (int i = 1; i <= parts; i++) {
            BlockPos p = pos.above(i);
            if (p.getY() >= world.getMaxBuildHeight() || !world.getBlockState(p).canBeReplaced(ctx)) return false;
        }
        return true;
    }

    /** Upper part of the statue of that tier, facing facing. */
    static BlockState partState(StatueTier tier, Direction facing, int level) {
        return LuxuryBlocks.STATUE_PART.defaultBlockState()
                .setValue(StatuePartBlock.FACING, facing)
                .setValue(StatuePartBlock.LEVEL, level)
                .setValue(StatuePartBlock.TIER, tier.index());
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Level world = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        Player player = ctx.getPlayer();
        Direction facing = ctx.getHorizontalDirection().getOpposite();
        if (!fitsAbove(ctx, tier.parts)) return null; // doesn't fit
        // no part may land in someone else's shop zone (ShopProtection only checks the base)
        if (StatueProtection.blockingStatue(world, player, pos, tier) != null) return null;
        // nor inside a player or a mob (BlockItem only checks entities against the base)
        CollisionContext sc = player == null ? CollisionContext.empty() : CollisionContext.of(player);
        for (int i = 1; i <= tier.parts; i++)
            if (!world.isUnobstructed(partState(tier, facing, i), pos.above(i), sc)) return null;
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    public void setPlacedBy(Level world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(world, pos, state, placer, stack);
        if (world.isClientSide) return;
        for (int i = 1; i <= tier.parts; i++)
            world.setBlock(pos.above(i), partState(tier, state.getValue(FACING), i), Block.UPDATE_ALL);
    }

    @Override
    protected void onRemove(BlockState state, Level world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && !world.isClientSide) {
            // remove the parts above (they drop nothing: the base already drops the statue)
            for (int i = 1; i <= tier.parts; i++) {
                BlockPos p = pos.above(i);
                BlockState s = world.getBlockState(p);
                if (s.is(LuxuryBlocks.STATUE_PART) && s.getValue(StatuePartBlock.LEVEL) == i)
                    world.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
            }
        }
        super.onRemove(state, world, pos, newState, moved);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return tier.baseShape(state.getValue(FACING));
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return tier.baseShape(state.getValue(FACING));
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

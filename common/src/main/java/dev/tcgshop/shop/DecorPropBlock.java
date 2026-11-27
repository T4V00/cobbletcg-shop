package dev.tcgshop.shop;

import com.mojang.serialization.MapCodec;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 1.1 (cleanliness update): the new decorative objects (potted palm, neon sign, rug, wall clock, gumball machine,
 * aquarium, floor lamp, card banner, cardboard shopkeeper, welcome mat). Plain facing blocks with a north-facing shape
 * (rotated for the other facings); wall ones hang on the side of a block. They are sold by the Furniture Seller and the
 * phone's Furniture app ({@link CleanBlocks#DECOR}) and raise the shop's decoration liking ({@link DecorScore}).
 */
public class DecorPropBlock extends HorizontalDirectionalBlock {
    /** north-facing shape, wall-mounted (back on the south side), walk-through (rug, mat, banner). */
    public record Spec(VoxelShape north, boolean wall, boolean walkThrough) {}

    private final Spec spec;
    private final Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);

    public DecorPropBlock(Properties settings, Spec spec) {
        super(settings);
        this.spec = spec;
        for (Direction d : Direction.Plane.HORIZONTAL) shapes.put(d, rotate(spec.north(), d));
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(p -> new DecorPropBlock(p, spec));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING); }

    public Spec spec() { return spec; }

    /** A north-facing shape turned to face d (north = as is, south = 180°, east / west = 90° either way). */
    static VoxelShape rotate(VoxelShape north, Direction d) {
        if (d == Direction.NORTH) return north;
        VoxelShape[] out = {Shapes.empty()};
        for (AABB b : north.toAabbs()) {
            double x0 = b.minX, z0 = b.minZ, x1 = b.maxX, z1 = b.maxZ;
            AABB r = switch (d) {
                case SOUTH -> new AABB(1 - x1, b.minY, 1 - z1, 1 - x0, b.maxY, 1 - z0);
                case EAST -> new AABB(1 - z1, b.minY, x0, 1 - z0, b.maxY, x1);
                default -> new AABB(z0, b.minY, 1 - x1, z1, b.maxY, 1 - x0); // WEST
            };
            out[0] = Shapes.or(out[0], Shapes.create(r));
        }
        return out[0].optimize();
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return shapes.get(state.getValue(FACING));
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return spec.walkThrough() ? Shapes.empty() : shapes.get(state.getValue(FACING));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        if (!spec.wall()) return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
        Direction face = ctx.getClickedFace();
        if (face.getAxis().isVertical()) face = ctx.getHorizontalDirection().getOpposite();
        BlockState st = defaultBlockState().setValue(FACING, face);
        return st.canSurvive(ctx.getLevel(), ctx.getClickedPos()) ? st : null;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader world, BlockPos pos) {
        if (spec.wall()) {
            Direction f = state.getValue(FACING);
            BlockPos back = pos.relative(f.getOpposite());
            return world.getBlockState(back).isFaceSturdy(world, back, f);
        }
        return !world.getBlockState(pos.below()).isAir();
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor world,
                                     BlockPos pos, BlockPos neighborPos) {
        Direction support = spec.wall() ? state.getValue(FACING).getOpposite() : Direction.DOWN;
        if (direction == support && !canSurvive(state, world, pos)) return Blocks.AIR.defaultBlockState();
        return super.updateShape(state, direction, neighborState, world, pos, neighborPos);
    }

    /** The item: a one-line description (its decoration value is added by the decor tooltip). */
    public static class PropItem extends BlockItem {
        public PropItem(Block block, Item.Properties settings) { super(block, settings); }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag type) {
            tooltip.add(Component.translatable(getDescriptionId() + ".tip").withStyle(ChatFormatting.GRAY));
        }
    }
}

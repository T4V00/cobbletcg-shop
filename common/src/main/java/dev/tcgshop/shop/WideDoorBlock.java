package dev.tcgshop.shop;

import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import org.jetbrains.annotations.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 1.1 (cleanliness update): the storefront shutter, a door 3 blocks wide and 2 high that opens and closes as one piece
 * (it rolls up into a housing at the top). Six parts: LEFT / CENTER / RIGHT x lower / upper; the lower CENTER is the
 * "main" part (the one that drops the item and that the shop's door detection uses, {@link #isMainPart}).
 * <p>
 * It is a real {@link DoorBlock} (tag minecraft:wooden_doors), so customers path through it and open it with the vanilla
 * door goal like any wooden door; every open / close / redstone change applies to all six parts. Open, it has no
 * collision at all (nobody bumps their head on the housing), and it refuses to close while anyone stands in it, so a
 * customer can never get stuck inside the panel; a close refused that way (door goal, redstone off) is retried every
 * second until the doorway is clear ({@link #tick}). Breaking any part removes the whole door.
 * <p>
 * It is placed from the center: it needs the 3x2 cells free (no block, nobody standing there), a solid floor under the
 * three lower ones, and none of the six cells in someone else's shop (ShopProtection also checks every part on break).
 */
public class WideDoorBlock extends DoorBlock {
    public static final MapCodec<WideDoorBlock> CODEC = simpleCodec(WideDoorBlock::new);
    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);

    public enum Part implements StringRepresentable {
        LEFT(-1), CENTER(0), RIGHT(1);

        /** Offset along {@link #side} from the center column. */
        public final int offset;

        Part(int offset) { this.offset = offset; }

        @Override
        public String getSerializedName() { return name().toLowerCase(Locale.ROOT); }

        public static Part of(int offset) { return offset < 0 ? LEFT : offset > 0 ? RIGHT : CENTER; }
    }

    // closed panel (same side as a vanilla door with that facing), open rail at the floor and roll housing at the top
    private static final VoxelShape CLOSED_N = Block.box(0, 0, 13, 16, 16, 16), CLOSED_S = Block.box(0, 0, 0, 16, 16, 3),
            CLOSED_W = Block.box(13, 0, 0, 16, 16, 16), CLOSED_E = Block.box(0, 0, 0, 3, 16, 16);
    private static final VoxelShape RAIL_N = Block.box(0, 0, 13, 16, 1, 16), RAIL_S = Block.box(0, 0, 0, 16, 1, 3),
            RAIL_W = Block.box(13, 0, 0, 16, 1, 16), RAIL_E = Block.box(0, 0, 0, 3, 1, 16);
    private static final VoxelShape ROLL_N = Block.box(0, 12, 11, 16, 16, 16), ROLL_S = Block.box(0, 12, 0, 16, 16, 5),
            ROLL_W = Block.box(11, 12, 0, 16, 16, 16), ROLL_E = Block.box(0, 12, 0, 5, 16, 16);

    public WideDoorBlock(Properties settings) {
        super(BlockSetType.COPPER, settings);
        registerDefaultState(defaultBlockState().setValue(PART, Part.CENTER));
    }

    @Override
    public MapCodec<? extends DoorBlock> codec() { return CODEC; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(PART);
    }

    // ------------------------------------------------------------------ geometry

    /** Direction from the LEFT part to the RIGHT part. */
    public static Direction side(Direction facing) { return facing.getClockWise(); }

    /** The lower CENTER part of the door this state belongs to. */
    public static BlockPos mainPos(BlockState state, BlockPos pos) {
        BlockPos p = pos.relative(side(state.getValue(FACING)), -state.getValue(PART).offset);
        return state.getValue(HALF) == DoubleBlockHalf.UPPER ? p.below() : p;
    }

    /** The six positions of a door whose main (lower center) part is at main. */
    public static List<BlockPos> parts(BlockPos main, Direction facing) {
        List<BlockPos> out = new ArrayList<>(6);
        Direction s = side(facing);
        for (int dy = 0; dy <= 1; dy++)
            for (int o = -1; o <= 1; o++) out.add(main.relative(s, o).above(dy));
        return out;
    }

    private static BlockState partState(BlockState base, int offset, int dy) {
        return base.setValue(PART, Part.of(offset)).setValue(HALF, dy == 0 ? DoubleBlockHalf.LOWER : DoubleBlockHalf.UPPER);
    }

    /** Vanilla doors are all "main"; of a wide door only the lower center counts (one door, not six). */
    public static boolean isMainPart(BlockState s) {
        return !(s.getBlock() instanceof WideDoorBlock) || (s.getValue(PART) == Part.CENTER && s.getValue(HALF) == DoubleBlockHalf.LOWER);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        Direction f = state.getValue(FACING);
        if (!state.getValue(OPEN)) return pick(f, CLOSED_N, CLOSED_S, CLOSED_W, CLOSED_E);
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? pick(f, RAIL_N, RAIL_S, RAIL_W, RAIL_E) : pick(f, ROLL_N, ROLL_S, ROLL_W, ROLL_E);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        if (state.getValue(OPEN)) return Shapes.empty();
        return getShape(state, world, pos, context);
    }

    private static VoxelShape pick(Direction f, VoxelShape n, VoxelShape s, VoxelShape w, VoxelShape e) {
        return switch (f) {
            case SOUTH -> s;
            case WEST -> w;
            case EAST -> e;
            default -> n;
        };
    }

    // ------------------------------------------------------------------ placing and breaking

    /** The 3x2 box of a door whose main part is at main. */
    public static AABB doorBox(BlockPos main, Direction facing) {
        Direction s = side(facing);
        BlockPos a = main.relative(s, -1), b = main.relative(s, 1).above();
        return new AABB(Math.min(a.getX(), b.getX()), a.getY(), Math.min(a.getZ(), b.getZ()),
                Math.max(a.getX(), b.getX()) + 1, b.getY() + 1, Math.max(a.getZ(), b.getZ()) + 1);
    }

    /** The first shop (other than the player's own) that any of these cells lies in, or null. Ops in creative pass. */
    @Nullable
    public static ShopZones.Zone foreignZone(net.minecraft.server.level.ServerLevel w, @Nullable Player player, List<BlockPos> cells) {
        if (player != null && player.isCreative() && player.hasPermissions(2)) return null;
        ShopZones zones = ShopZones.get(w);
        for (BlockPos p : cells) {
            ShopZones.Zone z = zones.at(p);
            if (z != null && (player == null || !z.owner.equals(player.getUUID()))) return z;
        }
        return null;
    }

    /** Why the door can't go there (a lang key), or null when it can. */
    @Nullable
    public static String placementProblem(Level w, BlockPos main, Direction facing, @Nullable BlockPlaceContext ctx) {
        if (main.getY() >= w.getMaxBuildHeight() - 1) return "tcgshop.wide_door.no_room";
        for (BlockPos p : parts(main, facing)) {
            if (!w.getWorldBorder().isWithinBounds(p)) return "tcgshop.wide_door.no_room";
            BlockState st = w.getBlockState(p);
            if (!p.equals(main) && !(ctx != null ? st.canBeReplaced(ctx) : st.canBeReplaced())) return "tcgshop.wide_door.no_room";
            if (p.getY() == main.getY() && !w.getBlockState(p.below()).isFaceSturdy(w, p.below(), Direction.UP)) return "tcgshop.wide_door.no_floor";
        }
        // nobody may stand in any of the six cells (the closed panels would trap them)
        if (!w.getEntitiesOfClass(Entity.class, doorBox(main, facing), e -> e.isAlive() && !e.isSpectator() && e.blocksBuilding).isEmpty())
            return "tcgshop.wide_door.occupied";
        return null;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Level w = ctx.getLevel();
        BlockPos main = ctx.getClickedPos();
        Direction facing = ctx.getHorizontalDirection();
        String why = placementProblem(w, main, facing, ctx);
        if (why == null && w instanceof net.minecraft.server.level.ServerLevel sw) {
            ShopZones.Zone z = foreignZone(sw, ctx.getPlayer(), parts(main, facing));
            if (z != null) {
                if (ctx.getPlayer() != null) ctx.getPlayer().displayClientMessage(net.minecraft.network.chat.Component.translatable("tcgshop.shop.protected", z.ownerName)
                        .withStyle(net.minecraft.ChatFormatting.RED), true);
                return null;
            }
        }
        if (why != null) {
            if (!w.isClientSide && ctx.getPlayer() != null)
                ctx.getPlayer().displayClientMessage(net.minecraft.network.chat.Component.translatable(why).withStyle(net.minecraft.ChatFormatting.YELLOW), true);
            return null;
        }
        boolean powered = false;
        for (BlockPos p : parts(main, facing)) powered |= w.hasNeighborSignal(p);
        return defaultBlockState().setValue(FACING, facing).setValue(PART, Part.CENTER).setValue(HALF, DoubleBlockHalf.LOWER)
                .setValue(POWERED, powered).setValue(OPEN, powered);
    }

    @Override
    public void setPlacedBy(Level world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        Direction s = side(state.getValue(FACING));
        for (int dy = 0; dy <= 1; dy++)
            for (int o = -1; o <= 1; o++) {
                if (dy == 0 && o == 0) continue;
                world.setBlock(pos.relative(s, o).above(dy), partState(state, o, dy), 3);
            }
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader world, BlockPos pos) {
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER) return world.getBlockState(pos.below()).is(this);
        return world.getBlockState(pos.below()).isFaceSturdy(world, pos.below(), Direction.UP);
    }

    /** Any missing sibling (above/below, left/right) or a missing floor removes this part, so the door goes as a whole. */
    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState other, LevelAccessor world, BlockPos pos, BlockPos otherPos) {
        DoubleBlockHalf half = state.getValue(HALF);
        Direction facing = state.getValue(FACING), s = side(facing);
        int off = state.getValue(PART).offset;
        if (dir == Direction.UP && half == DoubleBlockHalf.LOWER) return sibling(state, other, off, DoubleBlockHalf.UPPER) ? state : Blocks.AIR.defaultBlockState();
        if (dir == Direction.DOWN && half == DoubleBlockHalf.UPPER) return sibling(state, other, off, DoubleBlockHalf.LOWER) ? state : Blocks.AIR.defaultBlockState();
        if (dir == Direction.DOWN) return canSurvive(state, world, pos) ? state : Blocks.AIR.defaultBlockState();
        if (dir == s && off < 1) return sibling(state, other, off + 1, half) ? state : Blocks.AIR.defaultBlockState();
        if (dir == s.getOpposite() && off > -1) return sibling(state, other, off - 1, half) ? state : Blocks.AIR.defaultBlockState();
        return state;
    }

    private boolean sibling(BlockState self, BlockState other, int offset, DoubleBlockHalf half) {
        return other.is(this) && other.getValue(FACING) == self.getValue(FACING) && other.getValue(PART) == Part.of(offset) && other.getValue(HALF) == half;
    }

    /**
     * Survival: the item always comes from the main part (the loot table only drops there), so breaking another part
     * drops the main one first; creative: nothing drops. The other parts then vanish through updateShape.
     */
    @Override
    public BlockState playerWillDestroy(Level world, BlockPos pos, BlockState state, Player player) {
        if (!world.isClientSide) {
            BlockPos main = mainPos(state, pos);
            if (!main.equals(pos)) {
                BlockState ms = world.getBlockState(main);
                if (ms.is(this)) {
                    if (!player.isCreative()) Block.dropResources(ms, world, main, null, player, player.getMainHandItem());
                    world.setBlock(main, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                }
            }
        }
        // skip DoorBlock's half handling (ours covers all six parts)
        spawnDestroyParticles(world, player, pos, state);
        if (state.is(net.minecraft.tags.BlockTags.GUARDED_BY_PIGLINS)) net.minecraft.world.entity.monster.piglin.PiglinAi.angerNearbyPiglins(player, false);
        world.gameEvent(GameEvent.BLOCK_DESTROY, pos, GameEvent.Context.of(player, state));
        return state;
    }

    // ------------------------------------------------------------------ opening as one

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit) {
        boolean open = !state.getValue(OPEN);
        if (open) cancelClose(world, mainPos(state, pos));
        if (!open && blocked(world, state, pos, null)) {
            if (!world.isClientSide) player.displayClientMessage(net.minecraft.network.chat.Component.translatable("tcgshop.wide_door.blocked")
                    .withStyle(net.minecraft.ChatFormatting.YELLOW), true);
            return InteractionResult.sidedSuccess(world.isClientSide);
        }
        setAll(world, state, pos, open, state.getValue(POWERED), player);
        return InteractionResult.sidedSuccess(world.isClientSide);
    }

    /** Called by the vanilla door goal of customers / villagers too: the whole door opens or closes. */
    @Override
    public void setOpen(@Nullable Entity entity, Level world, BlockState state, BlockPos pos, boolean open) {
        if (!state.is(this) || state.getValue(OPEN) == open) return;
        if (open) cancelClose(world, mainPos(state, pos));
        if (!open && blocked(world, state, pos, null)) { // someone is still in the doorway: stays open, closes once they are out
            scheduleClose(world, mainPos(state, pos));
            return;
        }
        setAll(world, state, pos, open, state.getValue(POWERED), entity);
    }

    // ------------------------------------------------------------------ closing later (doorway busy)

    /** Doors (main part) that were asked to close while someone stood in them, per level. */
    private static final java.util.Map<Level, java.util.Set<Long>> PENDING_CLOSE = new java.util.WeakHashMap<>();

    private void scheduleClose(Level world, BlockPos main) {
        if (world.isClientSide) return;
        PENDING_CLOSE.computeIfAbsent(world, k -> new java.util.HashSet<>()).add(main.asLong());
        world.scheduleTick(main, this, 20);
    }

    private static void cancelClose(Level world, BlockPos main) {
        java.util.Set<Long> s = PENDING_CLOSE.get(world);
        if (s != null) s.remove(main.asLong());
    }

    /** Is a close of the door at main waiting for the doorway to clear? (selftest) */
    public static boolean closePending(Level world, BlockPos main) {
        java.util.Set<Long> s = PENDING_CLOSE.get(world);
        return s != null && s.contains(main.asLong());
    }

    /** Retries a refused close every second until nobody stands in the doorway (unless it was opened again meanwhile). */
    @Override
    protected void tick(BlockState state, net.minecraft.server.level.ServerLevel world, BlockPos pos, net.minecraft.util.RandomSource random) {
        if (!closePending(world, pos)) return;
        if (!state.is(this) || !isMainPart(state) || !state.getValue(OPEN) || state.getValue(POWERED)) { cancelClose(world, pos); return; }
        if (blocked(world, state, pos, null)) { world.scheduleTick(pos, this, 20); return; }
        cancelClose(world, pos);
        setAll(world, state, pos, false, false, null);
    }

    @Override
    protected void neighborChanged(BlockState state, Level world, BlockPos pos, Block block, BlockPos fromPos, boolean moving) {
        if (world.isClientSide || block == this) return;
        BlockPos main = mainPos(state, pos);
        boolean powered = false;
        for (BlockPos p : parts(main, state.getValue(FACING))) powered |= world.hasNeighborSignal(p);
        if (powered == state.getValue(POWERED)) return;
        boolean stuck = !powered && state.getValue(OPEN) && blocked(world, state, pos, null); // never closes on someone...
        if (powered) cancelClose(world, main);
        setAll(world, state, pos, powered || stuck, powered, null);
        if (stuck) scheduleClose(world, main); // ...but closes as soon as the doorway is clear
    }

    @Override
    protected void onExplosionHit(BlockState state, Level world, BlockPos pos, Explosion explosion, BiConsumer<ItemStack, BlockPos> drops) {
        // a wind charge toggles the door once: only the main part may (the others pass as "powered", which DoorBlock skips)
        super.onExplosionHit(isMainPart(state) ? state : state.setValue(POWERED, true), world, pos, explosion, drops);
    }

    /** Is a living entity standing in the doorway (any of the six parts, plus a little margin)? */
    public static boolean blocked(Level world, BlockState state, BlockPos pos, @Nullable Entity ignore) {
        AABB box = doorBox(mainPos(state, pos), state.getValue(FACING)).inflate(0.05);
        return !world.getEntitiesOfClass(LivingEntity.class, box, e -> e != ignore && e.isAlive() && !e.isSpectator()).isEmpty();
    }

    private void setAll(Level world, BlockState state, BlockPos pos, boolean open, boolean powered, @Nullable Entity who) {
        boolean changed = state.getValue(OPEN) != open;
        BlockPos main = mainPos(state, pos);
        for (BlockPos p : parts(main, state.getValue(FACING))) {
            BlockState s = world.getBlockState(p);
            if (s.is(this)) world.setBlock(p, s.setValue(OPEN, open).setValue(POWERED, powered), Block.UPDATE_CLIENTS | Block.UPDATE_IMMEDIATE);
        }
        if (!changed) return;
        world.playSound(who instanceof Player pl ? pl : null, main, open ? type().doorOpen() : type().doorClose(), SoundSource.BLOCKS, 1.0f,
                world.getRandom().nextFloat() * 0.1f + 0.75f);
        world.gameEvent(who, open ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, main);
    }

    // ------------------------------------------------------------------ rotation / mirror (structures)

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        if (mirror == Mirror.NONE) return state;
        Part p = state.getValue(PART);
        return state.rotate(mirror.getRotation(state.getValue(FACING))).setValue(PART, Part.of(-p.offset));
    }
}

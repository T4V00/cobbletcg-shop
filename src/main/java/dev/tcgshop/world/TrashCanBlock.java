package dev.tcgshop.world;

import com.mojang.serialization.MapCodec;
import dev.tcgshop.TcgShop;
import dev.tcgshop.TcgSounds;
import dev.tcgshop.pricing.PriceService;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.particle.ItemStackParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

import java.util.List;

/**
 * 1.0 trash can. The crafted one ({@code loot=none}) is decoration and never gives anything. Natural ones are
 * placed by world generation: {@code loot=street} (village streets and the Bankrupt Card Shop's door; refills after
 * {@link WorldLootConfig#refillDays}), {@code loot=wild} (lonely cans in plains-like biomes, searched once, never
 * refills) and {@code loot=bankrupt} (the Bankrupt Card Shop's unique can, searched once).
 * Wild cans generated before {@code loot=wild} existed are saved as {@code loot=street} and keep refilling.
 * Breaking any can drops the plain decorative item, so the loot flag can't be carried around.
 *
 * <p>Right-click starts a short search: {@value #SEARCH_STEPS} steps of {@value #STEP_TICKS} ticks with rustling
 * sounds, paper particles and arm swings; the result comes at the end (see {@link TrashLoot} for the odds).
 * Walking away (more than 6 blocks) cancels the search without using the can up.
 */
public class TrashCanBlock extends BlockWithEntity {
    public static final MapCodec<TrashCanBlock> CODEC = createCodec(TrashCanBlock::new);
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    public static final EnumProperty<LootKind> LOOT = EnumProperty.of("loot", LootKind.class);
    public static final BooleanProperty SEARCHED = BooleanProperty.of("searched");

    static final int SEARCH_STEPS = 3, STEP_TICKS = 8;
    private static final double MAX_DIST_SQ = 6 * 6;
    private static final VoxelShape SHAPE = VoxelShapes.union(Block.createCuboidShape(3, 0, 3, 13, 13, 13),
            Block.createCuboidShape(2.75, 13, 2.75, 13.25, 15.75, 13.25));
    private static final VoxelShape SHAPE_OPEN = Block.createCuboidShape(3, 0, 3, 13, 13, 13);

    /** Who placed it / what it holds. New values go at the end (block states are saved by name). */
    public enum LootKind implements StringIdentifiable {
        NONE("none"), STREET("street"), BANKRUPT("bankrupt"), WILD("wild");

        private final String name;

        LootKind(String name) { this.name = name; }

        @Override
        public String asString() { return name; }
    }

    public TrashCanBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH).with(LOOT, LootKind.NONE).with(SEARCHED, false));
    }

    /** State for world generation. */
    public static BlockState natural(LootKind kind, Direction facing) {
        return WorldLoot.TRASH_CAN.getDefaultState().with(FACING, facing).with(LOOT, kind);
    }

    @Override
    protected MapCodec<? extends BlockWithEntity> getCodec() { return CODEC; }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, LOOT, SEARCHED);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        // placed by a player: always decorative
        return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return state.get(SEARCHED) ? SHAPE_OPEN : SHAPE;
    }

    @Override
    protected BlockRenderType getRenderType(BlockState state) { return BlockRenderType.MODEL; }

    @Override
    protected BlockState rotate(BlockState state, BlockRotation rotation) {
        return state.with(FACING, rotation.rotate(state.get(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, BlockMirror mirror) {
        return state.rotate(mirror.getRotation(state.get(FACING)));
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) { return new TrashCanBlockEntity(pos, state); }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType options) {
        tooltip.add(Text.translatable("block.tcgshop.trash_can.tip1").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("block.tcgshop.trash_can.tip2").formatted(Formatting.DARK_GRAY));
    }

    // ------------------------------------------------------------------ refill (street cans only; wild and bankrupt cans are one-time)

    @Override
    protected boolean hasRandomTicks(BlockState state) {
        return state.get(LOOT) == LootKind.STREET && state.get(SEARCHED);
    }

    @Override
    protected void randomTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        if (state.get(LOOT) == LootKind.STREET && state.get(SEARCHED)
                && world.getBlockEntity(pos) instanceof TrashCanBlockEntity be && be.searcher == null && refillDue(world, be))
            world.setBlockState(pos, state.with(SEARCHED, false), Block.NOTIFY_LISTENERS);
    }

    private static boolean refillDue(World world, TrashCanBlockEntity be) {
        int days = WorldLootConfig.INSTANCE.refillDays;
        if (days <= 0) return false;
        return be.searchedAt < 0 || world.getTime() - be.searchedAt >= days * 24_000L;
    }

    // ------------------------------------------------------------------ search

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (world.isClient) return ActionResult.SUCCESS;
        if (!(player instanceof ServerPlayerEntity sp) || !(world instanceof ServerWorld sw)) return ActionResult.PASS;
        startSearch(state, sw, pos, sp);
        return ActionResult.CONSUME;
    }

    private void startSearch(BlockState state, ServerWorld world, BlockPos pos, ServerPlayerEntity sp) {
        Random r = world.random;
        LootKind kind = state.get(LOOT);
        if (kind == LootKind.NONE) {
            world.playSound(null, pos, SoundEvents.BLOCK_IRON_TRAPDOOR_OPEN, SoundCategory.BLOCKS, 0.5f, 1.3f);
            sp.sendMessage(variant("tcgshop.trash.decor", 4, r).formatted(Formatting.GRAY), true);
            return;
        }
        if (!(world.getBlockEntity(pos) instanceof TrashCanBlockEntity be)) return;
        if (be.searcher != null) {
            if (!be.searcher.equals(sp.getUuid())) sp.sendMessage(Text.translatable("tcgshop.trash.busy").formatted(Formatting.GRAY), true);
            return;
        }
        if (state.get(SEARCHED)) {
            if (kind == LootKind.STREET && refillDue(world, be)) {
                state = state.with(SEARCHED, false);
                world.setBlockState(pos, state, Block.NOTIFY_ALL);
            } else {
                world.playSound(null, pos, SoundEvents.BLOCK_LANTERN_HIT, SoundCategory.BLOCKS, 0.6f, 0.8f);
                String key = switch (kind) {
                    case BANKRUPT -> "tcgshop.bankrupt.empty";
                    case WILD -> "tcgshop.trash.empty_wild";
                    default -> "tcgshop.trash.empty";
                };
                sp.sendMessage(variant(key, 3, r).formatted(Formatting.GRAY), true);
                return;
            }
        }
        be.searcher = sp.getUuid();
        be.step = 0;
        world.playSound(null, pos, SoundEvents.BLOCK_IRON_TRAPDOOR_OPEN, SoundCategory.BLOCKS, 0.7f, 0.9f);
        stepEffects(world, pos, sp, 0, r);
        sp.sendMessage(variant("tcgshop.trash.searching", 3, r).formatted(Formatting.GRAY), true);
        world.scheduleBlockTick(pos, this, STEP_TICKS);
    }

    @Override
    protected void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        if (!(world.getBlockEntity(pos) instanceof TrashCanBlockEntity be) || be.searcher == null) return;
        ServerPlayerEntity sp = world.getServer().getPlayerManager().getPlayer(be.searcher);
        if (sp == null || sp.isRemoved() || sp.getWorld() != world || sp.squaredDistanceTo(Vec3d.ofCenter(pos)) > MAX_DIST_SQ) {
            be.searcher = null;
            if (sp != null) sp.sendMessage(Text.translatable("tcgshop.trash.cancel").formatted(Formatting.GRAY), true);
            return;
        }
        be.step++;
        if (be.step < SEARCH_STEPS) {
            stepEffects(world, pos, sp, be.step, random);
            world.scheduleBlockTick(pos, this, STEP_TICKS);
            return;
        }
        be.searcher = null;
        finish(state, world, pos, sp, be, random);
    }

    private static void stepEffects(ServerWorld world, BlockPos pos, ServerPlayerEntity sp, int step, Random r) {
        sp.swingHand(Hand.MAIN_HAND, true);
        world.playSound(null, pos, step % 2 == 0 ? SoundEvents.ITEM_BUNDLE_INSERT : SoundEvents.ITEM_BUNDLE_REMOVE_ONE,
                SoundCategory.BLOCKS, 0.8f, 0.8f + r.nextFloat() * 0.4f);
        world.playSound(null, pos, SoundEvents.BLOCK_COMPOSTER_FILL, SoundCategory.BLOCKS, 0.5f, 0.8f + r.nextFloat() * 0.3f);
        world.spawnParticles(new ItemStackParticleEffect(ParticleTypes.ITEM, new ItemStack(step == 1 ? Items.ROTTEN_FLESH : Items.PAPER)),
                pos.getX() + 0.5, pos.getY() + 0.95, pos.getZ() + 0.5, 5, 0.18, 0.08, 0.18, 0.06);
    }

    private static void finish(BlockState state, ServerWorld world, BlockPos pos, ServerPlayerEntity sp, TrashCanBlockEntity be, Random r) {
        boolean bankrupt = state.get(LOOT) == LootKind.BANKRUPT;
        TrashLoot.Find f = bankrupt ? TrashLoot.bankrupt(r) : TrashLoot.street(r);
        world.setBlockState(pos, state.with(SEARCHED, true), Block.NOTIFY_ALL);
        be.searchedAt = world.getTime();
        be.searches++;
        be.markDirty();
        String prefix = bankrupt ? "tcgshop.bankrupt." : "tcgshop.trash.";
        switch (f.kind()) {
            case PACK, CARD -> {
                ItemStack shown = f.stack().copy(); // offerOrDrop may empty the stack it is given
                sp.getInventory().offerOrDrop(f.stack());
                String key = prefix + (f.kind() == TrashLoot.Kind.PACK ? "pack" : "card");
                sp.sendMessage(variant(key, bankrupt ? 3 : 4, r, shown.toHoverableText(), PriceService.format(f.value()))
                        .formatted(bankrupt ? Formatting.GOLD : Formatting.YELLOW), false);
                world.playSound(null, pos, TcgSounds.CARD_RARE, SoundCategory.PLAYERS, 0.9f, bankrupt ? 1.0f : 1.2f);
                world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 6, 0.25, 0.2, 0.25, 0.0);
                if (bankrupt)
                    TcgShop.LOGGER.info("{} found {} in a Bankrupt Card Shop trash can", sp.getName().getString(), shown.getItem());
            }
            case JUNK -> {
                ItemStack shown = f.stack().copy();
                sp.getInventory().offerOrDrop(f.stack());
                sp.sendMessage(variant(prefix + "junk", bankrupt ? 3 : 4, r, shown.toHoverableText()).formatted(Formatting.GRAY), false);
                world.playSound(null, pos, SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 0.6f, 0.7f);
            }
            case NOTHING -> {
                sp.sendMessage(variant(prefix + "nothing", 4, r).formatted(Formatting.GRAY), true);
                world.playSound(null, pos, SoundEvents.BLOCK_IRON_TRAPDOOR_CLOSE, SoundCategory.BLOCKS, 0.6f, 0.8f);
            }
        }
    }

    /** One of n numbered variants of a message: key.0 .. key.(n-1). */
    static MutableText variant(String key, int n, Random r, Object... args) {
        return Text.translatable(key + "." + r.nextInt(n), args);
    }
}

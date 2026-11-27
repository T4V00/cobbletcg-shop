package dev.tcgshop.world;

import com.mojang.serialization.MapCodec;
import dev.tcgshop.TcgShop;
import dev.tcgshop.TcgSounds;
import dev.tcgshop.pricing.PriceService;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

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
public class TrashCanBlock extends BaseEntityBlock {
    public static final MapCodec<TrashCanBlock> CODEC = simpleCodec(TrashCanBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<LootKind> LOOT = EnumProperty.create("loot", LootKind.class);
    public static final BooleanProperty SEARCHED = BooleanProperty.create("searched");

    static final int SEARCH_STEPS = 3, STEP_TICKS = 8;
    private static final double MAX_DIST_SQ = 6 * 6;
    private static final VoxelShape SHAPE = Shapes.or(Block.box(3, 0, 3, 13, 13, 13),
            Block.box(2.75, 13, 2.75, 13.25, 15.75, 13.25));
    private static final VoxelShape SHAPE_OPEN = Block.box(3, 0, 3, 13, 13, 13);

    /** Who placed it / what it holds. New values go at the end (block states are saved by name). */
    public enum LootKind implements StringRepresentable {
        NONE("none"), STREET("street"), BANKRUPT("bankrupt"), WILD("wild");

        private final String name;

        LootKind(String name) { this.name = name; }

        @Override
        public String getSerializedName() { return name; }
    }

    public TrashCanBlock(Properties settings) {
        super(settings);
        registerDefaultState(getStateDefinition().any().setValue(FACING, Direction.NORTH).setValue(LOOT, LootKind.NONE).setValue(SEARCHED, false));
    }

    /** State for world generation. */
    public static BlockState natural(LootKind kind, Direction facing) {
        return WorldLoot.TRASH_CAN.defaultBlockState().setValue(FACING, facing).setValue(LOOT, kind);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LOOT, SEARCHED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        // placed by a player: always decorative
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return state.getValue(SEARCHED) ? SHAPE_OPEN : SHAPE;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new TrashCanBlockEntity(pos, state); }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag options) {
        tooltip.add(Component.translatable("block.tcgshop.trash_can.tip1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("block.tcgshop.trash_can.tip2").withStyle(ChatFormatting.DARK_GRAY));
    }

    // ------------------------------------------------------------------ refill (street cans only; wild and bankrupt cans are one-time)

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return state.getValue(LOOT) == LootKind.STREET && state.getValue(SEARCHED);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
        if (state.getValue(LOOT) == LootKind.STREET && state.getValue(SEARCHED)
                && world.getBlockEntity(pos) instanceof TrashCanBlockEntity be && be.searcher == null && refillDue(world, be))
            world.setBlock(pos, state.setValue(SEARCHED, false), Block.UPDATE_CLIENTS);
    }

    private static boolean refillDue(Level world, TrashCanBlockEntity be) {
        int days = WorldLootConfig.INSTANCE.refillDays;
        if (days <= 0) return false;
        return be.searchedAt < 0 || world.getGameTime() - be.searchedAt >= days * 24_000L;
    }

    // ------------------------------------------------------------------ search

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit) {
        if (world.isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer sp) || !(world instanceof ServerLevel sw)) return InteractionResult.PASS;
        startSearch(state, sw, pos, sp);
        return InteractionResult.CONSUME;
    }

    private void startSearch(BlockState state, ServerLevel world, BlockPos pos, ServerPlayer sp) {
        RandomSource r = world.random;
        LootKind kind = state.getValue(LOOT);
        if (kind == LootKind.NONE) {
            world.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.5f, 1.3f);
            sp.displayClientMessage(variant("tcgshop.trash.decor", 4, r).withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (!(world.getBlockEntity(pos) instanceof TrashCanBlockEntity be)) return;
        if (be.searcher != null) {
            if (!be.searcher.equals(sp.getUUID())) sp.displayClientMessage(Component.translatable("tcgshop.trash.busy").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (state.getValue(SEARCHED)) {
            if (kind == LootKind.STREET && refillDue(world, be)) {
                state = state.setValue(SEARCHED, false);
                world.setBlock(pos, state, Block.UPDATE_ALL);
            } else {
                world.playSound(null, pos, SoundEvents.LANTERN_HIT, SoundSource.BLOCKS, 0.6f, 0.8f);
                String key = switch (kind) {
                    case BANKRUPT -> "tcgshop.bankrupt.empty";
                    case WILD -> "tcgshop.trash.empty_wild";
                    default -> "tcgshop.trash.empty";
                };
                sp.displayClientMessage(variant(key, 3, r).withStyle(ChatFormatting.GRAY), true);
                return;
            }
        }
        be.searcher = sp.getUUID();
        be.step = 0;
        world.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.7f, 0.9f);
        stepEffects(world, pos, sp, 0, r);
        sp.displayClientMessage(variant("tcgshop.trash.searching", 3, r).withStyle(ChatFormatting.GRAY), true);
        world.scheduleTick(pos, this, STEP_TICKS);
    }

    @Override
    protected void tick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
        if (!(world.getBlockEntity(pos) instanceof TrashCanBlockEntity be) || be.searcher == null) return;
        ServerPlayer sp = world.getServer().getPlayerList().getPlayer(be.searcher);
        if (sp == null || sp.isRemoved() || sp.level() != world || sp.distanceToSqr(Vec3.atCenterOf(pos)) > MAX_DIST_SQ) {
            be.searcher = null;
            if (sp != null) sp.displayClientMessage(Component.translatable("tcgshop.trash.cancel").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        be.step++;
        if (be.step < SEARCH_STEPS) {
            stepEffects(world, pos, sp, be.step, random);
            world.scheduleTick(pos, this, STEP_TICKS);
            return;
        }
        be.searcher = null;
        finish(state, world, pos, sp, be, random);
    }

    private static void stepEffects(ServerLevel world, BlockPos pos, ServerPlayer sp, int step, RandomSource r) {
        sp.swing(InteractionHand.MAIN_HAND, true);
        world.playSound(null, pos, step % 2 == 0 ? SoundEvents.BUNDLE_INSERT : SoundEvents.BUNDLE_REMOVE_ONE,
                SoundSource.BLOCKS, 0.8f, 0.8f + r.nextFloat() * 0.4f);
        world.playSound(null, pos, SoundEvents.COMPOSTER_FILL, SoundSource.BLOCKS, 0.5f, 0.8f + r.nextFloat() * 0.3f);
        world.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(step == 1 ? Items.ROTTEN_FLESH : Items.PAPER)),
                pos.getX() + 0.5, pos.getY() + 0.95, pos.getZ() + 0.5, 5, 0.18, 0.08, 0.18, 0.06);
    }

    private static void finish(BlockState state, ServerLevel world, BlockPos pos, ServerPlayer sp, TrashCanBlockEntity be, RandomSource r) {
        boolean bankrupt = state.getValue(LOOT) == LootKind.BANKRUPT;
        TrashLoot.Find f = bankrupt ? TrashLoot.bankrupt(r) : TrashLoot.street(r);
        world.setBlock(pos, state.setValue(SEARCHED, true), Block.UPDATE_ALL);
        be.searchedAt = world.getGameTime();
        be.searches++;
        be.setChanged();
        String prefix = bankrupt ? "tcgshop.bankrupt." : "tcgshop.trash.";
        switch (f.kind()) {
            case PACK, CARD -> {
                ItemStack shown = f.stack().copy(); // offerOrDrop may empty the stack it is given
                sp.getInventory().placeItemBackInInventory(f.stack());
                String key = prefix + (f.kind() == TrashLoot.Kind.PACK ? "pack" : "card");
                sp.displayClientMessage(variant(key, bankrupt ? 3 : 4, r, shown.getDisplayName(), PriceService.format(f.value()))
                        .withStyle(bankrupt ? ChatFormatting.GOLD : ChatFormatting.YELLOW), false);
                world.playSound(null, pos, TcgSounds.CARD_RARE, SoundSource.PLAYERS, 0.9f, bankrupt ? 1.0f : 1.2f);
                world.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 6, 0.25, 0.2, 0.25, 0.0);
                if (bankrupt)
                    TcgShop.LOGGER.info("{} found {} in a Bankrupt Card Shop trash can", sp.getName().getString(), shown.getItem());
            }
            case JUNK -> {
                ItemStack shown = f.stack().copy();
                sp.getInventory().placeItemBackInInventory(f.stack());
                sp.displayClientMessage(variant(prefix + "junk", bankrupt ? 3 : 4, r, shown.getDisplayName()).withStyle(ChatFormatting.GRAY), false);
                world.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.6f, 0.7f);
            }
            case NOTHING -> {
                sp.displayClientMessage(variant(prefix + "nothing", 4, r).withStyle(ChatFormatting.GRAY), true);
                world.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.6f, 0.8f);
            }
        }
    }

    /** One of n numbered variants of a message: key.0 .. key.(n-1). */
    static MutableComponent variant(String key, int n, RandomSource r, Object... args) {
        return Component.translatable(key + "." + r.nextInt(n), args);
    }
}

package dev.tcgshop.shop;

import com.mojang.serialization.MapCodec;
import dev.masterball.api.card.CardInfo;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Animated shop objects: poster, play table, theft detector, roulette and cash register. */
public final class DecorBlocks {
    private DecorBlocks() {}

    /** Shared block entity: set (poster), animation start and value (roulette result). */
    public static class DecorEntity extends BlockEntity {
        public String set = "mew151";
        public long animStart = -1000;
        public int value = -1;
        /** Play table: the match's cards in play (Active + Bench on each side) to draw them on the table. */
        public final java.util.List<String> board = new java.util.ArrayList<>();

        public DecorEntity(BlockPos pos, BlockState state) { super(TcgBlocks.DECOR_BE, pos, state); }

        public void animate(int value) {
            this.value = value;
            this.animStart = level == null ? 0 : level.getGameTime();
            sync();
        }

        public void sync() {
            setChanged();
            if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }

        @Override
        protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider lookup) {
            super.saveAdditional(nbt, lookup);
            nbt.putString("Set", set);
            nbt.putLong("Anim", animStart);
            nbt.putInt("Value", value);
            net.minecraft.nbt.ListTag b = new net.minecraft.nbt.ListTag();
            for (String s : board) b.add(net.minecraft.nbt.StringTag.valueOf(s));
            nbt.put("Board", b);
        }

        @Override
        protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider lookup) {
            super.loadAdditional(nbt, lookup);
            if (nbt.contains("Set")) set = nbt.getString("Set");
            animStart = nbt.getLong("Anim");
            value = nbt.getInt("Value");
            board.clear();
            net.minecraft.nbt.ListTag b = nbt.getList("Board", net.minecraft.nbt.Tag.TAG_STRING);
            for (int i = 0; i < b.size(); i++) {
                String id = dev.masterball.api.card.CardInfo.migrate(b.getString(i));
                board.add(dev.masterball.api.card.CardCatalog.isRemovedId(id) ? "" : id); // TCG Pocket: removed in 0.9
            }
        }

        @Override
        public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

        @Override
        public CompoundTag getUpdateTag(HolderLookup.Provider lookup) { return saveWithoutMetadata(lookup); }
    }

    /** Base: block with horizontal facing and a decor block entity. */
    public abstract static class FacingDecor extends BaseEntityBlock {
        public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
        private final VoxelShape north, south, east, west;

        protected FacingDecor(Properties settings, VoxelShape north, VoxelShape south, VoxelShape east, VoxelShape west) {
            super(settings);
            this.north = north;
            this.south = south;
            this.east = east;
            this.west = west;
            registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING); }

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
        protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new DecorEntity(pos, state); }
    }

    private static List<String> sets() {
        List<String> out = new ArrayList<>();
        for (ResourceLocation id : BuiltInRegistries.ITEM.keySet())
            if (CardInfo.NS.equals(id.getNamespace()) && id.getPath().contains("/sealed_")) out.add(CardInfo.setId(id));
        return out;
    }

    // ------------------------------------------------------------------ poster

    /** Wall poster with a pack's art. Right-click (zone owner or outside shops): changes the set. */
    public static class PosterBlock extends FacingDecor implements ShopFurniture {
        public PosterBlock(Properties s) {
            super(s, Block.box(1, 0, 15, 15, 16, 16), Block.box(1, 0, 0, 15, 16, 1),
                    Block.box(0, 0, 1, 1, 16, 15), Block.box(15, 0, 1, 16, 16, 15));
        }

        @Override
        protected MapCodec<? extends BaseEntityBlock> codec() { return simpleCodec(PosterBlock::new); }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit) {
            if (world instanceof ServerLevel sw && world.getBlockEntity(pos) instanceof DecorEntity be) {
                ShopZones.Zone z = ShopZones.get(sw).at(pos);
                if (z == null || z.owner.equals(player.getUUID())) {
                    List<String> sets = sets();
                    if (!sets.isEmpty()) {
                        int i = sets.indexOf(be.set);
                        be.set = sets.get((i + (player.isShiftKeyDown() ? sets.size() - 1 : 1)) % sets.size());
                        be.sync();
                        world.playSound(null, pos, SoundEvents.PAINTING_PLACE, SoundSource.BLOCKS, 0.7f, 1.2f);
                    }
                }
            }
            return InteractionResult.sidedSuccess(world.isClientSide);
        }
    }

    // ------------------------------------------------------------------ play table

    /** Competitive play table with playmat and cards. Right-click: shuffle (animation). Adds reputation. */
    public static class PlayTableBlock extends FacingDecor implements ShopFurniture {
        private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 13, 16);

        public PlayTableBlock(Properties s) { super(s, SHAPE, SHAPE, SHAPE, SHAPE); }

        /** The play table works anywhere (home, tournaments...), not only in the shop. */
        @Override
        public boolean shopOnly() { return false; }

        @Override
        protected MapCodec<? extends BaseEntityBlock> codec() { return simpleCodec(PlayTableBlock::new); }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit) {
            // click: wait for an opponent / play a league match · Shift + click: play against the house
            if (!world.isClientSide && player instanceof net.minecraft.server.level.ServerPlayer sp)
                dev.tcgshop.ptcg.PtcgDuels.useTable(sp, pos, sp.isShiftKeyDown());
            return InteractionResult.sidedSuccess(world.isClientSide);
        }
    }

    // ------------------------------------------------------------------ theft detector

    /**
     * Theft detector gate: if a thief steals nearby (12 blocks) the alarm goes off (red light and bell),
     * the thief is marked and becomes much easier to catch.
     */
    public static class TheftDetectorBlock extends FacingDecor implements ShopFurniture {
        public static final BooleanProperty ALARM = BooleanProperty.create("alarm");
        private static final VoxelShape NS = Block.box(0, 0, 6, 16, 16, 10), EW = Block.box(6, 0, 0, 10, 16, 16);

        public TheftDetectorBlock(Properties s) {
            super(s, NS, NS, EW, EW);
            registerDefaultState(defaultBlockState().setValue(ALARM, false));
        }

        @Override
        protected MapCodec<? extends BaseEntityBlock> codec() { return simpleCodec(TheftDetectorBlock::new); }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            super.createBlockStateDefinition(builder);
            builder.add(ALARM);
        }

        public static void trigger(ServerLevel world, BlockPos pos) {
            BlockState st = world.getBlockState(pos);
            if (!(st.getBlock() instanceof TheftDetectorBlock)) return;
            world.setBlock(pos, st.setValue(ALARM, true), 3);
            world.scheduleTick(pos, st.getBlock(), 80);
            world.playSound(null, pos, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 2f, 1.4f);
            world.playSound(null, pos, SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.BLOCKS, 2f, 2f);
        }

        @Override
        protected void tick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
            if (state.getValue(ALARM)) world.setBlock(pos, state.setValue(ALARM, false), 3);
        }
    }

    // ------------------------------------------------------------------ cash register

    /** Cash register: the owner takes payments here; the drawer opens with each sale. If a cashier is hired, they serve. */
    public static class RegisterBlock extends FacingDecor implements ShopFurniture {
        private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 11, 14);

        public RegisterBlock(Properties s) { super(s, SHAPE, SHAPE, SHAPE, SHAPE); }

        @Override
        protected MapCodec<? extends BaseEntityBlock> codec() { return simpleCodec(RegisterBlock::new); }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit) {
            if (world instanceof ServerLevel sw && player instanceof ServerPlayer sp) {
                ShopZones.Zone z = ShopZones.get(sw).at(pos);
                if (z != null) ShopServer.useRegister(sp, z);
                if (world.getBlockEntity(pos) instanceof DecorEntity be) be.animate(0);
            }
            return InteractionResult.sidedSuccess(world.isClientSide);
        }
    }

    // ------------------------------------------------------------------ roulette

    /** Poké Roulette (only bought with the TCG Phone): bet on red or black. */
    public static class RouletteBlock extends FacingDecor {
        private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 14, 16);

        public RouletteBlock(Properties s) { super(s, SHAPE, SHAPE, SHAPE, SHAPE); }

        @Override
        protected MapCodec<? extends BaseEntityBlock> codec() { return simpleCodec(RouletteBlock::new); }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit) {
            if (player instanceof ServerPlayer sp) Roulette.open(sp, pos);
            return InteractionResult.sidedSuccess(world.isClientSide);
        }
    }
}

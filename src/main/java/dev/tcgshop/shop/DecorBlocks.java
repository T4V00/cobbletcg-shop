package dev.tcgshop.shop;

import com.mojang.serialization.MapCodec;
import dev.masterball.api.card.CardInfo;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/** Objetos de tienda con animación: póster, mesa de juego, detector de robos, ruleta y caja registradora. */
public final class DecorBlocks {
    private DecorBlocks() {}

    /** Entidad de bloque común: set (póster), inicio de animación y valor (resultado de la ruleta). */
    public static class DecorEntity extends BlockEntity {
        public String set = "mew151";
        public long animStart = -1000;
        public int value = -1;
        /** Mesa de juego: cartas en juego de la partida (Activo + Banca de cada lado) para dibujarlas sobre la mesa. */
        public final java.util.List<String> board = new java.util.ArrayList<>();

        public DecorEntity(BlockPos pos, BlockState state) { super(TcgBlocks.DECOR_BE, pos, state); }

        public void animate(int value) {
            this.value = value;
            this.animStart = world == null ? 0 : world.getTime();
            sync();
        }

        public void sync() {
            markDirty();
            if (world != null) world.updateListeners(pos, getCachedState(), getCachedState(), 3);
        }

        @Override
        protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
            super.writeNbt(nbt, lookup);
            nbt.putString("Set", set);
            nbt.putLong("Anim", animStart);
            nbt.putInt("Value", value);
            net.minecraft.nbt.NbtList b = new net.minecraft.nbt.NbtList();
            for (String s : board) b.add(net.minecraft.nbt.NbtString.of(s));
            nbt.put("Board", b);
        }

        @Override
        protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
            super.readNbt(nbt, lookup);
            if (nbt.contains("Set")) set = nbt.getString("Set");
            animStart = nbt.getLong("Anim");
            value = nbt.getInt("Value");
            board.clear();
            net.minecraft.nbt.NbtList b = nbt.getList("Board", net.minecraft.nbt.NbtElement.STRING_TYPE);
            for (int i = 0; i < b.size(); i++) {
                String id = dev.masterball.api.card.CardInfo.migrate(b.getString(i));
                board.add(dev.masterball.api.card.CardCatalog.isRemovedId(id) ? "" : id); // TCG Pocket: quitado en la 0.9
            }
        }

        @Override
        public Packet<ClientPlayPacketListener> toUpdatePacket() { return BlockEntityUpdateS2CPacket.create(this); }

        @Override
        public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup lookup) { return createNbt(lookup); }
    }

    /** Base: bloque con orientación horizontal y entidad de decoración. */
    public abstract static class FacingDecor extends BlockWithEntity {
        public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
        private final VoxelShape north, south, east, west;

        protected FacingDecor(Settings settings, VoxelShape north, VoxelShape south, VoxelShape east, VoxelShape west) {
            super(settings);
            this.north = north;
            this.south = south;
            this.east = east;
            this.west = west;
            setDefaultState(getDefaultState().with(FACING, Direction.NORTH));
        }

        @Override
        protected void appendProperties(StateManager.Builder<Block, BlockState> builder) { builder.add(FACING); }

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
        protected BlockRenderType getRenderType(BlockState state) { return BlockRenderType.MODEL; }

        @Override
        public BlockEntity createBlockEntity(BlockPos pos, BlockState state) { return new DecorEntity(pos, state); }
    }

    private static List<String> sets() {
        List<String> out = new ArrayList<>();
        for (Identifier id : Registries.ITEM.getIds())
            if (CardInfo.NS.equals(id.getNamespace()) && id.getPath().contains("/sealed_")) out.add(CardInfo.setId(id));
        return out;
    }

    // ------------------------------------------------------------------ póster

    /** Póster de pared con el arte de un sobre. Clic derecho (dueño de la zona o fuera de tiendas): cambia de set. */
    public static class PosterBlock extends FacingDecor implements ShopFurniture {
        public PosterBlock(Settings s) {
            super(s, Block.createCuboidShape(1, 0, 15, 15, 16, 16), Block.createCuboidShape(1, 0, 0, 15, 16, 1),
                    Block.createCuboidShape(0, 0, 1, 1, 16, 15), Block.createCuboidShape(15, 0, 1, 16, 16, 15));
        }

        @Override
        protected MapCodec<? extends BlockWithEntity> getCodec() { return createCodec(PosterBlock::new); }

        @Override
        protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
            if (world instanceof ServerWorld sw && world.getBlockEntity(pos) instanceof DecorEntity be) {
                ShopZones.Zone z = ShopZones.get(sw).at(pos);
                if (z == null || z.owner.equals(player.getUuid())) {
                    List<String> sets = sets();
                    if (!sets.isEmpty()) {
                        int i = sets.indexOf(be.set);
                        be.set = sets.get((i + (player.isSneaking() ? sets.size() - 1 : 1)) % sets.size());
                        be.sync();
                        world.playSound(null, pos, SoundEvents.ENTITY_PAINTING_PLACE, SoundCategory.BLOCKS, 0.7f, 1.2f);
                    }
                }
            }
            return ActionResult.success(world.isClient);
        }
    }

    // ------------------------------------------------------------------ mesa de juego

    /** Mesa de juego competitivo con tapete y cartas. Clic derecho: barajar (animación). Suma reputación. */
    public static class PlayTableBlock extends FacingDecor implements ShopFurniture {
        private static final VoxelShape SHAPE = Block.createCuboidShape(0, 0, 0, 16, 13, 16);

        public PlayTableBlock(Settings s) { super(s, SHAPE, SHAPE, SHAPE, SHAPE); }

        /** La mesa de juego vale en cualquier sitio (casa, torneos...), no sólo en la tienda. */
        @Override
        public boolean shopOnly() { return false; }

        @Override
        protected MapCodec<? extends BlockWithEntity> getCodec() { return createCodec(PlayTableBlock::new); }

        @Override
        protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
            // clic: esperar rival / jugar partida de liga · Mayús + clic: jugar contra la casa
            if (!world.isClient && player instanceof net.minecraft.server.network.ServerPlayerEntity sp)
                dev.tcgshop.ptcg.PtcgDuels.useTable(sp, pos, sp.isSneaking());
            return ActionResult.success(world.isClient);
        }
    }

    // ------------------------------------------------------------------ detector de robos

    /**
     * Arco detector de robos: si un ladrón roba cerca (12 bloques) salta la alarma (luz roja y campana),
     * el ladrón queda marcado y es mucho más fácil atraparlo.
     */
    public static class TheftDetectorBlock extends FacingDecor implements ShopFurniture {
        public static final BooleanProperty ALARM = BooleanProperty.of("alarm");
        private static final VoxelShape NS = Block.createCuboidShape(0, 0, 6, 16, 16, 10), EW = Block.createCuboidShape(6, 0, 0, 10, 16, 16);

        public TheftDetectorBlock(Settings s) {
            super(s, NS, NS, EW, EW);
            setDefaultState(getDefaultState().with(ALARM, false));
        }

        @Override
        protected MapCodec<? extends BlockWithEntity> getCodec() { return createCodec(TheftDetectorBlock::new); }

        @Override
        protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
            super.appendProperties(builder);
            builder.add(ALARM);
        }

        public static void trigger(ServerWorld world, BlockPos pos) {
            BlockState st = world.getBlockState(pos);
            if (!(st.getBlock() instanceof TheftDetectorBlock)) return;
            world.setBlockState(pos, st.with(ALARM, true), 3);
            world.scheduleBlockTick(pos, st.getBlock(), 80);
            world.playSound(null, pos, SoundEvents.BLOCK_BELL_USE, SoundCategory.BLOCKS, 2f, 1.4f);
            world.playSound(null, pos, SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), SoundCategory.BLOCKS, 2f, 2f);
        }

        @Override
        protected void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
            if (state.get(ALARM)) world.setBlockState(pos, state.with(ALARM, false), 3);
        }
    }

    // ------------------------------------------------------------------ caja registradora

    /** Caja registradora: el dueño cobra aquí; el cajón se abre con cada venta. Si hay cajero contratado, él atiende. */
    public static class RegisterBlock extends FacingDecor implements ShopFurniture {
        private static final VoxelShape SHAPE = Block.createCuboidShape(2, 0, 2, 14, 11, 14);

        public RegisterBlock(Settings s) { super(s, SHAPE, SHAPE, SHAPE, SHAPE); }

        @Override
        protected MapCodec<? extends BlockWithEntity> getCodec() { return createCodec(RegisterBlock::new); }

        @Override
        protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
            if (world instanceof ServerWorld sw && player instanceof ServerPlayerEntity sp) {
                ShopZones.Zone z = ShopZones.get(sw).at(pos);
                if (z != null) ShopServer.useRegister(sp, z);
                if (world.getBlockEntity(pos) instanceof DecorEntity be) be.animate(0);
            }
            return ActionResult.success(world.isClient);
        }
    }

    // ------------------------------------------------------------------ ruleta

    /** Ruleta Poké (sólo se compra con el Teléfono TCG): apuesta a rojo o negro. */
    public static class RouletteBlock extends FacingDecor {
        private static final VoxelShape SHAPE = Block.createCuboidShape(0, 0, 0, 16, 14, 16);

        public RouletteBlock(Settings s) { super(s, SHAPE, SHAPE, SHAPE, SHAPE); }

        @Override
        protected MapCodec<? extends BlockWithEntity> getCodec() { return createCodec(RouletteBlock::new); }

        @Override
        protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
            if (player instanceof ServerPlayerEntity sp) Roulette.open(sp, pos);
            return ActionResult.success(world.isClient);
        }
    }
}

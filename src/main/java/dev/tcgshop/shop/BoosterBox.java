package dev.tcgshop.shop;

import com.mojang.serialization.MapCodec;
import dev.tcgshop.TcgSounds;
import dev.tcgshop.card.CardInfo;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.sound.SoundCategory;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Caja de sobres (booster box) de un set: se ve como una caja de verdad con el arte del sobre.
 * Clic derecho en el aire con ella: se abre y da 36 sobres sellados. Colocada: producto para tu tienda.
 */
public final class BoosterBox {
    public static final int PACKS = 36;
    /** Tamaño de la caja colocada (bloques). */
    public static final float W = 0.94f, H = 0.6f, D = 0.64f;
    /** Ticks que dura la animación de apertura de una caja colocada. */
    public static final int OPEN_TICKS = 72;

    private BoosterBox() {}

    @Nullable
    public static Item sealedFor(String set) {
        for (Identifier id : Registries.ITEM.getIds()) {
            if (CardInfo.NS.equals(id.getNamespace()) && id.getPath().startsWith(set + "/sealed_")) return Registries.ITEM.get(id);
        }
        return null;
    }

    public static ItemStack of(String set) {
        ItemStack st = new ItemStack(TcgBlocks.BOOSTER_BOX_ITEM);
        st.set(TcgBlocks.BOX_SET, set);
        return st;
    }

    // ------------------------------------------------------------------ bloque

    public static class BoxBlock extends BlockWithEntity {
        public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
        private static final VoxelShape SHAPE_NS = Block.createCuboidShape(0.5, 0, 2.9, 15.5, 9.6, 13.1);
        private static final VoxelShape SHAPE_EW = Block.createCuboidShape(2.9, 0, 0.5, 13.1, 9.6, 15.5);

        public BoxBlock(Settings settings) {
            super(settings);
            setDefaultState(getDefaultState().with(FACING, Direction.NORTH));
        }

        @Override
        protected MapCodec<? extends BlockWithEntity> getCodec() { return createCodec(BoxBlock::new); }

        @Override
        protected void appendProperties(StateManager.Builder<Block, BlockState> builder) { builder.add(FACING); }

        @Override
        public BlockState getPlacementState(ItemPlacementContext ctx) {
            return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
        }

        @Override
        protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
            return state.get(FACING).getAxis() == Direction.Axis.Z ? SHAPE_NS : SHAPE_EW;
        }

        @Override
        protected BlockRenderType getRenderType(BlockState state) { return BlockRenderType.ENTITYBLOCK_ANIMATED; }

        @Override
        public BlockEntity createBlockEntity(BlockPos pos, BlockState state) { return new BoxEntity(pos, state); }

        @Nullable
        @Override
        public <T extends BlockEntity> net.minecraft.block.entity.BlockEntityTicker<T> getTicker(World world, BlockState state,
                net.minecraft.block.entity.BlockEntityType<T> type) {
            return world.isClient ? null : validateTicker(type, TcgBlocks.BOX_BE, (w, p, s, be) -> be.serverTick((net.minecraft.server.world.ServerWorld) w));
        }

        /** Clic derecho a una caja colocada: se abre con su animación y, al terminar, te da los 36 sobres. */
        @Override
        protected net.minecraft.util.ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, net.minecraft.util.hit.BlockHitResult hit) {
            if (world instanceof net.minecraft.server.world.ServerWorld sw && world.getBlockEntity(pos) instanceof BoxEntity be && be.openStart < 0) {
                ShopZones.Zone z = ShopZones.get(sw).at(pos);
                if (z != null && !z.owner.equals(player.getUuid())) {
                    player.sendMessage(Text.translatable("tcgshop.box.not_yours").formatted(Formatting.RED), true);
                    return net.minecraft.util.ActionResult.FAIL;
                }
                be.openStart = world.getTime();
                be.opener = player.getUuid();
                be.markDirty();
                world.updateListeners(pos, state, state, 3);
                world.playSound(null, pos, net.minecraft.sound.SoundEvents.ITEM_BUNDLE_DROP_CONTENTS, SoundCategory.BLOCKS, 1f, 0.7f);
            }
            return net.minecraft.util.ActionResult.success(world.isClient);
        }

        @Override
        public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
            if (world.getBlockEntity(pos) instanceof BoxEntity be) {
                be.set = stack.getOrDefault(TcgBlocks.BOX_SET, "mew151");
                be.markDirty();
                world.updateListeners(pos, state, state, 3);
            }
        }

        @Override
        public BlockState onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
            if (!world.isClient && !player.isCreative() && world.getBlockEntity(pos) instanceof BoxEntity be && be.openStart < 0) {
                dropStack(world, pos, of(be.set));
            }
            return super.onBreak(world, pos, state, player);
        }

        @Override
        public ItemStack getPickStack(net.minecraft.world.WorldView world, BlockPos pos, BlockState state) {
            return world.getBlockEntity(pos) instanceof BoxEntity be ? of(be.set) : super.getPickStack(world, pos, state);
        }
    }

    public static class BoxEntity extends BlockEntity {
        public String set = "mew151";
        public long openStart = -1;
        public java.util.UUID opener;

        void serverTick(net.minecraft.server.world.ServerWorld world) {
            if (openStart < 0) return;
            long t = world.getTime() - openStart;
            if (t == 12) world.playSound(null, pos, net.minecraft.sound.SoundEvents.ITEM_BOOK_PAGE_TURN, SoundCategory.BLOCKS, 1f, 0.6f);
            if (t == 30) world.playSound(null, pos, TcgSounds.PACK_OPEN, SoundCategory.BLOCKS, 0.8f, 1.2f);
            if (t < OPEN_TICKS) return;
            Item sealed = sealedFor(set);
            PlayerEntity p = opener == null ? null : world.getPlayerByUuid(opener);
            if (sealed != null) {
                for (int i = 0; i < PACKS; i += 16) {
                    ItemStack st = new ItemStack(sealed, Math.min(16, PACKS - i));
                    if (p != null && p.squaredDistanceTo(pos.toCenterPos()) < 24 * 24) p.getInventory().offerOrDrop(st);
                    else Block.dropStack(world, pos, st);
                }
                if (p != null) p.sendMessage(Text.translatable("tcgshop.box.opened", PACKS, sealed.getName()).formatted(Formatting.GOLD), true);
            }
            world.spawnParticles(new net.minecraft.particle.BlockStateParticleEffect(net.minecraft.particle.ParticleTypes.BLOCK,
                    net.minecraft.block.Blocks.BROWN_WOOL.getDefaultState()), pos.getX() + 0.5, pos.getY() + 0.4, pos.getZ() + 0.5, 30, 0.3, 0.2, 0.3, 0.05);
            world.playSound(null, pos, net.minecraft.sound.SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.BLOCKS, 1f, 0.8f);
            openStart = -1;
            world.removeBlock(pos, false);
        }

        public BoxEntity(BlockPos pos, BlockState state) { super(TcgBlocks.BOX_BE, pos, state); }

        @Override
        protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
            super.writeNbt(nbt, lookup);
            nbt.putString("Set", set);
            nbt.putLong("Open", openStart);
            if (opener != null) nbt.putUuid("Opener", opener);
        }

        @Override
        protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
            super.readNbt(nbt, lookup);
            set = nbt.getString("Set");
            openStart = nbt.contains("Open") ? nbt.getLong("Open") : -1;
            opener = nbt.containsUuid("Opener") ? nbt.getUuid("Opener") : null;
        }

        @Override
        public Packet<ClientPlayPacketListener> toUpdatePacket() { return BlockEntityUpdateS2CPacket.create(this); }

        @Override
        public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup lookup) { return createNbt(lookup); }
    }

    // ------------------------------------------------------------------ ítem

    public static class BoxItem extends BlockItem {
        public BoxItem(Block block, Settings settings) { super(block, settings); }

        @Override
        public Text getName(ItemStack stack) {
            String set = stack.getOrDefault(TcgBlocks.BOX_SET, "mew151");
            return Text.translatable("item.tcgshop.booster_box.named", Text.translatable("tcgshop.set." + set));
        }

        @Override
        public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
            ItemStack stack = user.getStackInHand(hand);
            if (!world.isClient) {
                String boxSet = stack.getOrDefault(TcgBlocks.BOX_SET, "mew151");
                Item sealed = sealedFor(boxSet);
                if (sealed == null) return TypedActionResult.fail(stack);
                for (int i = 0; i < PACKS; i += 16) user.getInventory().offerOrDrop(new ItemStack(sealed, Math.min(16, PACKS - i)));
                stack.decrementUnlessCreative(1, user);
                world.playSound(null, user.getBlockPos(), TcgSounds.PACK_OPEN, SoundCategory.PLAYERS, 1f, 0.8f);
                user.sendMessage(Text.translatable("tcgshop.box.opened", PACKS, sealed.getName()).formatted(Formatting.GOLD), true);
                if (user instanceof net.minecraft.server.network.ServerPlayerEntity sp
                        && net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(sp, dev.tcgshop.network.BoxOpenPayload.ID))
                    net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(sp,
                            new dev.tcgshop.network.BoxOpenPayload(boxSet, PACKS));
            }
            return TypedActionResult.success(stack, world.isClient);
        }

        @Override
        public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
            tooltip.add(Text.translatable("item.tcgshop.booster_box.tip", PACKS).formatted(Formatting.GRAY));
            tooltip.add(Text.translatable("item.tcgshop.booster_box.tip2").formatted(Formatting.DARK_GRAY));
        }
    }
}

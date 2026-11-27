package dev.tcgshop.shop;

import dev.masterball.api.platform.Net;
import com.mojang.serialization.MapCodec;
import dev.tcgshop.TcgSounds;
import dev.masterball.api.card.CardInfo;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
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
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A set's booster box: looks like a real box with the pack art.
 * Right-click in the air with it: it opens and gives 36 sealed packs. Placed: a product for your shop.
 */
public final class BoosterBox {
    public static final int PACKS = 36;
    /** Size of the placed box (blocks). */
    public static final float W = 0.94f, H = 0.6f, D = 0.64f;
    /** Duration in ticks of a placed box's opening animation. */
    public static final int OPEN_TICKS = 72;

    private BoosterBox() {}

    /** set -> sealed pack (Optional.empty if the set has none). The item registry no longer changes in-game. */
    private static final java.util.Map<String, java.util.Optional<Item>> SEALED = new java.util.concurrent.ConcurrentHashMap<>();

    @Nullable
    public static Item sealedFor(String set) {
        if (set == null || set.isEmpty()) return null;
        return SEALED.computeIfAbsent(set, s -> {
            for (ResourceLocation id : BuiltInRegistries.ITEM.keySet()) {
                if (CardInfo.NS.equals(id.getNamespace()) && id.getPath().startsWith(s + "/sealed_")) return java.util.Optional.of(BuiltInRegistries.ITEM.get(id));
            }
            return java.util.Optional.empty();
        }).orElse(null);
    }

    /** Removed set (TCG Pocket, since 0.9) or one without packs? Such a box can't be opened or sold. */
    public static boolean isDeadSet(@Nullable String set) {
        return set == null || dev.masterball.api.migrate.RemovedItems.isRemovedSet(set) || sealedFor(set) == null;
    }

    /** Booster box of a removed set or one without packs: ShopServer doesn't accept it as a product or price it. */
    public static boolean isRemovedBox(ItemStack st) {
        return st.is(TcgBlocks.BOOSTER_BOX_ITEM) && isDeadSet(st.getOrDefault(TcgBlocks.BOX_SET, "mew151"));
    }

    public static ItemStack of(String set) {
        ItemStack st = new ItemStack(TcgBlocks.BOOSTER_BOX_ITEM);
        st.set(TcgBlocks.BOX_SET, set);
        return st;
    }

    // ------------------------------------------------------------------ block

    public static class BoxBlock extends BaseEntityBlock {
        public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
        private static final VoxelShape SHAPE_NS = Block.box(0.5, 0, 2.9, 15.5, 9.6, 13.1);
        private static final VoxelShape SHAPE_EW = Block.box(2.9, 0, 0.5, 13.1, 9.6, 15.5);

        public BoxBlock(Properties settings) {
            super(settings);
            registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
        }

        @Override
        protected MapCodec<? extends BaseEntityBlock> codec() { return simpleCodec(BoxBlock::new); }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING); }

        @Override
        public BlockState getStateForPlacement(BlockPlaceContext ctx) {
            return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
        }

        @Override
        protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
            return state.getValue(FACING).getAxis() == Direction.Axis.Z ? SHAPE_NS : SHAPE_EW;
        }

        @Override
        protected RenderShape getRenderShape(BlockState state) { return RenderShape.ENTITYBLOCK_ANIMATED; }

        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new BoxEntity(pos, state); }

        @Nullable
        @Override
        public <T extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(Level world, BlockState state,
                net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
            return world.isClientSide ? null : createTickerHelper(type, TcgBlocks.BOX_BE, (w, p, s, be) -> be.serverTick((net.minecraft.server.level.ServerLevel) w));
        }

        /** Right-clicking a placed box: it opens with its animation and, when done, gives you the 36 packs. */
        @Override
        protected net.minecraft.world.InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, net.minecraft.world.phys.BlockHitResult hit) {
            if (world instanceof net.minecraft.server.level.ServerLevel sw && world.getBlockEntity(pos) instanceof BoxEntity be && be.openStart < 0) {
                ShopZones.Zone z = ShopZones.get(sw).at(pos);
                if (z != null && !z.owner.equals(player.getUUID())) {
                    player.displayClientMessage(Component.translatable("tcgshop.box.not_yours").withStyle(ChatFormatting.RED), true);
                    return net.minecraft.world.InteractionResult.FAIL;
                }
                be.openStart = world.getGameTime();
                be.opener = player.getUUID();
                be.setChanged();
                world.sendBlockUpdated(pos, state, state, 3);
                world.playSound(null, pos, net.minecraft.sounds.SoundEvents.BUNDLE_DROP_CONTENTS, SoundSource.BLOCKS, 1f, 0.7f);
            }
            return net.minecraft.world.InteractionResult.sidedSuccess(world.isClientSide);
        }

        @Override
        public void setPlacedBy(Level world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
            if (world.getBlockEntity(pos) instanceof BoxEntity be) {
                be.set = stack.getOrDefault(TcgBlocks.BOX_SET, "mew151");
                be.setChanged();
                world.sendBlockUpdated(pos, state, state, 3);
            }
        }

        @Override
        public BlockState playerWillDestroy(Level world, BlockPos pos, BlockState state, Player player) {
            if (!world.isClientSide && !player.isCreative() && world.getBlockEntity(pos) instanceof BoxEntity be && be.openStart < 0
                    && !dev.masterball.api.migrate.RemovedItems.isRemovedSet(be.set)) {
                popResource(world, pos, of(be.set));
            }
            return super.playerWillDestroy(world, pos, state, player);
        }

        @Override
        public ItemStack getCloneItemStack(net.minecraft.world.level.LevelReader world, BlockPos pos, BlockState state) {
            return world.getBlockEntity(pos) instanceof BoxEntity be ? of(be.set) : super.getCloneItemStack(world, pos, state);
        }
    }

    public static class BoxEntity extends BlockEntity {
        public String set = "mew151";
        public long openStart = -1;
        public java.util.UUID opener;

        void serverTick(net.minecraft.server.level.ServerLevel world) {
            if (dev.masterball.api.migrate.RemovedItems.isRemovedSet(set)) {
                // TCG Pocket box placed before 0.9: the set no longer exists (same as its saved cards and packs)
                dev.masterball.api.migrate.RemovedItems.noteDropped();
                world.removeBlock(worldPosition, false);
                return;
            }
            if (openStart < 0) return;
            long t = world.getGameTime() - openStart;
            if (t == 12) world.playSound(null, worldPosition, net.minecraft.sounds.SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 1f, 0.6f);
            if (t == 30) world.playSound(null, worldPosition, TcgSounds.PACK_OPEN, SoundSource.BLOCKS, 0.8f, 1.2f);
            if (t < OPEN_TICKS) return;
            Item sealed = sealedFor(set);
            Player p = opener == null ? null : world.getPlayerByUUID(opener);
            if (sealed != null) {
                for (int i = 0; i < PACKS; i += 16) {
                    ItemStack st = new ItemStack(sealed, Math.min(16, PACKS - i));
                    if (p != null && p.distanceToSqr(worldPosition.getCenter()) < 24 * 24) p.getInventory().placeItemBackInInventory(st);
                    else Block.popResource(world, worldPosition, st);
                }
                if (p != null) p.displayClientMessage(Component.translatable("tcgshop.box.opened", PACKS, sealed.getDescription()).withStyle(ChatFormatting.GOLD), true);
            }
            if (sealed == null) {
                // set without packs: the box isn't consumed, it stays closed
                openStart = -1;
                setChanged();
                world.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
                return;
            }
            world.sendParticles(new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK,
                    net.minecraft.world.level.block.Blocks.BROWN_WOOL.defaultBlockState()), worldPosition.getX() + 0.5, worldPosition.getY() + 0.4, worldPosition.getZ() + 0.5, 30, 0.3, 0.2, 0.3, 0.05);
            world.playSound(null, worldPosition, net.minecraft.sounds.SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 1f, 0.8f);
            openStart = -1;
            world.removeBlock(worldPosition, false);
        }

        public BoxEntity(BlockPos pos, BlockState state) { super(TcgBlocks.BOX_BE, pos, state); }

        @Override
        protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider lookup) {
            super.saveAdditional(nbt, lookup);
            nbt.putString("Set", set);
            nbt.putLong("Open", openStart);
            if (opener != null) nbt.putUUID("Opener", opener);
        }

        @Override
        protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider lookup) {
            super.loadAdditional(nbt, lookup);
            set = nbt.getString("Set");
            openStart = nbt.contains("Open") ? nbt.getLong("Open") : -1;
            opener = nbt.hasUUID("Opener") ? nbt.getUUID("Opener") : null;
        }

        @Override
        public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

        @Override
        public CompoundTag getUpdateTag(HolderLookup.Provider lookup) { return saveWithoutMetadata(lookup); }
    }

    // ------------------------------------------------------------------ item

    public static class BoxItem extends BlockItem {
        static {
            // saved boxes of a TCG Pocket set: discarded on load, like its cards and packs (masterball-api)
            dev.masterball.api.migrate.RemovedItems.registerSetComponent("tcgshop:booster_box", "tcgshop:box_set");
        }

        public BoxItem(Block block, Properties settings) { super(block, settings); }

        @Override
        public Component getName(ItemStack stack) {
            String set = stack.getOrDefault(TcgBlocks.BOX_SET, "mew151");
            return Component.translatable("item.tcgshop.booster_box.named", Component.translatableWithFallback("masterball.set." + set, set));
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
            ItemStack stack = user.getItemInHand(hand);
            if (!world.isClientSide) {
                // 1.1: with the Box-Opening Gloves on, ask first ("Open the whole box?"); the answer opens it (pack.BoxGloves)
                if (user instanceof net.minecraft.server.level.ServerPlayer sp && dev.tcgshop.pack.BoxGloves.ask(sp, hand, stack))
                    return InteractionResultHolder.success(stack);
                if (!openToPacks(user, stack)) return InteractionResultHolder.fail(stack);
            }
            return InteractionResultHolder.sidedSuccess(stack, world.isClientSide);
        }

        /** Opens one box of the stack into its 36 sealed packs (with the box animation on the client). false if the set has no packs. */
        public static boolean openToPacks(Player user, ItemStack stack) {
            Level world = user.level();
            String boxSet = stack.getOrDefault(TcgBlocks.BOX_SET, "mew151");
            Item sealed = sealedFor(boxSet);
            if (sealed == null) return false;
            for (int i = 0; i < PACKS; i += 16) user.getInventory().placeItemBackInInventory(new ItemStack(sealed, Math.min(16, PACKS - i)));
            stack.consume(1, user);
            world.playSound(null, user.blockPosition(), TcgSounds.PACK_OPEN, SoundSource.PLAYERS, 1f, 0.8f);
            user.displayClientMessage(Component.translatable("tcgshop.box.opened", PACKS, sealed.getDescription()).withStyle(ChatFormatting.GOLD), true);
            if (user instanceof net.minecraft.server.level.ServerPlayer sp
                    && Net.INSTANCE.canSend(sp, dev.tcgshop.network.BoxOpenPayload.ID))
                Net.INSTANCE.send(sp,
                        new dev.tcgshop.network.BoxOpenPayload(boxSet, PACKS));
            return true;
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag type) {
            tooltip.add(Component.translatable("item.tcgshop.booster_box.tip", PACKS).withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("item.tcgshop.booster_box.tip2").withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}

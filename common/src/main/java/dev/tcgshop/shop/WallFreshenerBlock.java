package dev.tcgshop.shop;

import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3f;

/**
 * 1.1 (cleanliness update): the wall air freshener. Mounted on a wall, inside a shop it puffs every
 * {@link CleanConfig#sprayInterval} ticks while the shop has bad smell, grime or graffiti nearby, removing some of each
 * (and erasing the nearest graffiti) and using one of its {@link #MAX_CHARGES} charges. The item keeps the used charges in
 * its DAMAGE component WITHOUT a max damage, so it is not "damageable": vanilla repair (crafting grid, grindstone, anvil)
 * can't merge two cans into free charges; {@link FreshenerItem} draws the charge bar itself. The deodorant spray refills it. Cheap and long-lasting: the passive way to keep a shop fresh.
 */
public class WallFreshenerBlock extends BaseEntityBlock {
    public static final MapCodec<WallFreshenerBlock> CODEC = simpleCodec(WallFreshenerBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty EMPTY = BooleanProperty.create("empty");
    /** Charges of a full can (also the item's max damage). */
    public static final int MAX_CHARGES = 240;
    /** Charges added by one refill, and the deodorant uses it costs. */
    public static final int REFILL = 80, REFILL_COST = 8;

    // shapes for a block facing that way (its back on the opposite wall)
    private static final VoxelShape N = Block.box(5, 4, 12, 11, 13, 16), S = Block.box(5, 4, 0, 11, 13, 4),
            E = Block.box(0, 4, 5, 4, 13, 11), W = Block.box(12, 4, 5, 16, 13, 11);

    public WallFreshenerBlock(Properties settings) {
        super(settings);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(EMPTY, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING, EMPTY); }

    @Override
    protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> S;
            case EAST -> E;
            case WEST -> W;
            default -> N;
        };
    }

    /** Only on the side of a wall (the face clicked must be horizontal). */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction face = ctx.getClickedFace();
        if (face.getAxis().isVertical()) {
            // clicked a floor/ceiling: try the wall the player is looking at
            face = ctx.getHorizontalDirection().getOpposite();
        }
        BlockState st = defaultBlockState().setValue(FACING, face);
        return st.canSurvive(ctx.getLevel(), ctx.getClickedPos()) ? st : null;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader world, BlockPos pos) {
        Direction f = state.getValue(FACING);
        BlockPos back = pos.relative(f.getOpposite());
        return world.getBlockState(back).isFaceSturdy(world, back, f);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor world,
                                     BlockPos pos, BlockPos neighborPos) {
        if (direction == state.getValue(FACING).getOpposite() && !canSurvive(state, world, pos)) return Blocks.AIR.defaultBlockState();
        return super.updateShape(state, direction, neighborState, world, pos, neighborPos);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new FreshenerEntity(pos, state); }

    /** Placed from an item: the charges came with it (applyImplicitComponents), so an empty can shows as empty right away. */
    @Override
    public void setPlacedBy(Level world, BlockPos pos, BlockState state, @org.jetbrains.annotations.Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(world, pos, state, placer, stack);
        if (!world.isClientSide && world.getBlockEntity(pos) instanceof FreshenerEntity be) be.setCharges(be.charges);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level world, BlockState state, BlockEntityType<T> type) {
        return world.isClientSide ? null : createTickerHelper(type, CleanBlocks.FRESHENER_BE, (w, p, s, be) -> be.serverTick((ServerLevel) w, s));
    }

    // ------------------------------------------------------------------ use: refill / show charges

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(dev.tcgshop.item.TcgItems.DEODORANT_SPRAY)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (world.isClientSide) return ItemInteractionResult.SUCCESS;
        if (world.getBlockEntity(pos) instanceof FreshenerEntity be && player instanceof ServerPlayer sp) {
            if (be.charges >= MAX_CHARGES) {
                sp.displayClientMessage(Component.translatable("tcgshop.freshener.full", be.charges, MAX_CHARGES).withStyle(ChatFormatting.YELLOW), true);
                return ItemInteractionResult.SUCCESS;
            }
            int left = stack.getMaxDamage() - stack.getDamageValue();
            if (left < REFILL_COST) {
                sp.displayClientMessage(Component.translatable("tcgshop.freshener.need_spray", REFILL_COST).withStyle(ChatFormatting.RED), true);
                return ItemInteractionResult.SUCCESS;
            }
            stack.hurtAndBreak(REFILL_COST, sp, LivingEntity.getSlotForHand(hand));
            be.setCharges(Math.min(MAX_CHARGES, be.charges + REFILL));
            world.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 0.7f, 1.3f);
            sp.displayClientMessage(Component.translatable("tcgshop.freshener.refilled", be.charges, MAX_CHARGES).withStyle(ChatFormatting.AQUA), true);
        }
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit) {
        if (world.isClientSide) return InteractionResult.SUCCESS;
        if (world.getBlockEntity(pos) instanceof FreshenerEntity be && player instanceof ServerPlayer sp) {
            ShopZones.Zone z = ShopZones.get((ServerLevel) world).at(pos);
            sp.displayClientMessage(Component.translatable(z == null ? "tcgshop.freshener.status_outside" : "tcgshop.freshener.status",
                    be.charges, MAX_CHARGES).withStyle(be.charges > 0 ? ChatFormatting.AQUA : ChatFormatting.RED), true);
        }
        return InteractionResult.SUCCESS;
    }

    // ------------------------------------------------------------------ block entity

    public static class FreshenerEntity extends BlockEntity {
        int charges = MAX_CHARGES;

        public FreshenerEntity(BlockPos pos, BlockState state) { super(CleanBlocks.FRESHENER_BE, pos, state); }

        public int charges() { return charges; }

        public void setCharges(int c) {
            charges = Math.max(0, Math.min(MAX_CHARGES, c));
            setChanged();
            if (level != null) {
                BlockState st = getBlockState();
                if (st.hasProperty(EMPTY) && st.getValue(EMPTY) != (charges <= 0)) level.setBlock(worldPosition, st.setValue(EMPTY, charges <= 0), 3);
            }
        }

        void serverTick(ServerLevel w, BlockState state) {
            int iv = CleanConfig.INSTANCE.sprayInterval;
            if (Math.floorMod(w.getGameTime() + (worldPosition.asLong() & 63), iv) != 0) return;
            tryPuff(w, state);
        }

        /** One puff now if it is in a shop that needs it (true = puffed). Package-private for the selftest. */
        boolean tryPuff(ServerLevel w, BlockState state) {
            if (charges <= 0) {
                if (state.hasProperty(EMPTY) && !state.getValue(EMPTY)) setCharges(0); // e.g. an old world's empty can
                return false;
            }
            ShopZones.Zone z = ShopZones.get(w).at(worldPosition);
            if (z == null || z.dormant) return false;
            if (!Cleanliness.freshenerPuff(w, z, worldPosition)) return false;
            setCharges(charges - 1);
            puffFx(w, state);
            return true;
        }

        private void puffFx(ServerLevel w, BlockState state) {
            if (w.getNearestPlayer(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5, 24, false) == null) return;
            Direction f = state.getValue(FACING);
            double x = worldPosition.getX() + 0.5 + f.getStepX() * 0.25, y = worldPosition.getY() + 0.7, zz = worldPosition.getZ() + 0.5 + f.getStepZ() * 0.25;
            DustParticleOptions mist = new DustParticleOptions(new Vector3f(0.78f, 0.95f, 1f), 1.1f);
            for (int i = 1; i <= 5; i++)
                w.sendParticles(mist, x + f.getStepX() * i * 0.25, y + i * 0.03, zz + f.getStepZ() * i * 0.25, 2, 0.06 * i, 0.05 * i, 0.06 * i, 0);
            w.playSound(null, worldPosition, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.18f, 1.9f);
        }

        @Override
        protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider lookup) {
            super.saveAdditional(nbt, lookup);
            nbt.putInt("Charges", charges);
        }

        @Override
        protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider lookup) {
            super.loadAdditional(nbt, lookup);
            charges = nbt.contains("Charges") ? Math.max(0, Math.min(MAX_CHARGES, nbt.getInt("Charges"))) : MAX_CHARGES;
        }

        /** Placed from an item: the charges come from its durability (damage = used charges). */
        @Override
        protected void applyImplicitComponents(DataComponentInput input) {
            super.applyImplicitComponents(input);
            Integer dmg = input.get(DataComponents.DAMAGE);
            charges = Math.max(0, Math.min(MAX_CHARGES, MAX_CHARGES - (dmg == null ? 0 : dmg)));
        }

        /** Broken: the dropped item keeps the charges (loot table copy_components). */
        @Override
        protected void collectImplicitComponents(DataComponentMap.Builder builder) {
            super.collectImplicitComponents(builder);
            if (charges < MAX_CHARGES) builder.set(DataComponents.DAMAGE, MAX_CHARGES - charges); // a full can stacks like a new one
        }
    }

    /** Charges left in a freshener item (DAMAGE = used charges; no MAX_DAMAGE, so read the raw component). */
    public static int itemCharges(ItemStack stack) {
        Integer dmg = stack.get(DataComponents.DAMAGE);
        return Math.max(0, Math.min(MAX_CHARGES, MAX_CHARGES - (dmg == null ? 0 : dmg)));
    }

    /** The item: shows charges (tooltip and its own bar) instead of durability. */
    public static class FreshenerItem extends BlockItem {
        public FreshenerItem(Block block, Item.Properties settings) { super(block, settings); }

        @Override
        public boolean isBarVisible(ItemStack stack) { return itemCharges(stack) < MAX_CHARGES; }

        @Override
        public int getBarWidth(ItemStack stack) { return Math.round(13f * itemCharges(stack) / MAX_CHARGES); }

        @Override
        public int getBarColor(ItemStack stack) {
            return net.minecraft.util.Mth.hsvToRgb(Math.max(0f, itemCharges(stack) / (float) MAX_CHARGES) / 3f, 1f, 1f);
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag type) {
            tooltip.add(Component.translatable("block.tcgshop.wall_freshener.tip").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("tcgshop.freshener.charges", itemCharges(stack), MAX_CHARGES).withStyle(ChatFormatting.DARK_AQUA));
            tooltip.add(Component.translatable("tcgshop.freshener.refill_tip").withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}

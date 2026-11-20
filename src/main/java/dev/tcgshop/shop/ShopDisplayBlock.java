package dev.tcgshop.shop;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Expositor de tienda de varios bloques (atril, vitrina-mostrador, estantería, góndola, palé, pedestal, muro).
 * Todas las partes comparten bloque; la parte 0 (maestro) lleva el inventario y dibuja los productos.
 *  · Clic derecho sobre un producto: lo compras al momento (el dinero va directo a la caja). También en tu tienda.
 *  · Dueño con carta / sobre / caja / peluche / mystery box en la mano: lo pone a la venta en esa casilla.
 *  · Dueño + Mayús + clic: gestionar precios y retirar productos.
 */
public class ShopDisplayBlock extends BlockWithEntity implements ShopFurniture {
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    public static final IntProperty PART = IntProperty.of("part", 0, 15);
    public final DisplayKind kind;
    /** Expositor "de casa": se coloca en cualquier sitio, sólo su dueño pone y quita cartas, y no se vende nada. */
    public final boolean home;

    public ShopDisplayBlock(DisplayKind kind, Settings settings) {
        this(kind, false, settings);
    }

    public ShopDisplayBlock(DisplayKind kind, boolean home, Settings settings) {
        super(settings);
        this.kind = kind;
        this.home = home;
        setDefaultState(getDefaultState().with(FACING, Direction.NORTH).with(PART, 0));
    }

    public int slots() { return kind.slots.length; }

    @Override
    protected MapCodec<? extends BlockWithEntity> getCodec() {
        return createCodec(s -> new ShopDisplayBlock(kind, home, s));
    }

    @Override
    public boolean shopOnly() { return !home;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART);
    }

    /** Bloque maestro de la estructura a la que pertenece esta parte. */
    public BlockPos master(BlockState state, BlockPos pos) {
        return kind.masterOf(pos, state.get(FACING), state.get(PART));
    }

    @Nullable
    public static ShopDisplayBlockEntity masterEntity(World world, BlockState state, BlockPos pos) {
        if (!(state.getBlock() instanceof ShopDisplayBlock b)) return null;
        return world.getBlockEntity(b.master(state, pos)) instanceof ShopDisplayBlockEntity be ? be : null;
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        Direction facing = ctx.getHorizontalPlayerFacing().getOpposite();
        World world = ctx.getWorld();
        BlockPos clicked = ctx.getBlockPos();
        BlockPos master = kind.masterForClick(clicked, facing);
        int clickedPart = 0;
        for (int p = 0; p < kind.parts(); p++) {
            BlockPos c = kind.partPos(master, facing, p);
            if (c.equals(clicked)) { clickedPart = p; continue; }
            if (c.getY() < world.getBottomY() || c.getY() >= world.getTopY() || !world.getBlockState(c).isReplaceable()) {
                if (ctx.getPlayer() != null && !world.isClient)
                    ctx.getPlayer().sendMessage(Text.translatable("tcgshop.shop.no_room", kind.w, kind.h, kind.d).formatted(Formatting.RED), true);
                return null;
            }
        }
        return getDefaultState().with(FACING, facing).with(PART, clickedPart);
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);
        if (world.isClient) return;
        BlockPos master = master(state, pos);
        Direction facing = state.get(FACING);
        int self = state.get(PART);
        for (int p = 0; p < kind.parts(); p++) {
            if (p == self) continue;
            world.setBlockState(kind.partPos(master, facing, p), state.with(PART, p), Block.NOTIFY_ALL);
        }
        if (placer != null && world.getBlockEntity(master) instanceof ShopDisplayBlockEntity be) {
            be.owner = placer.getUuid();
            be.changed();
        }
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return kind.shape(state.get(FACING), state.get(PART));
    }

    @Override
    protected BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return state.get(PART) == 0 ? new ShopDisplayBlockEntity(pos, state) : null;
    }

    private int slotFor(BlockState state, BlockPos master, BlockHitResult hit) {
        Vec3d rel = hit.getPos().subtract(master.getX(), master.getY(), master.getZ());
        return kind.slotAt(state.get(FACING), rel);
    }

    @Override
    protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (!ShopServer.isProduct(stack)) return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (home) {
            if (world instanceof ServerWorld && player instanceof ServerPlayerEntity sp && world.getBlockEntity(master(state, pos)) instanceof ShopDisplayBlockEntity be) {
                int slot = slotFor(state, master(state, pos), hit);
                if (slot >= 0 && !be.getStack(slot).isEmpty() && !(be.mayEdit(sp) && ShopServer.canMerge(be.getStack(slot), stack)))
                    return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
                if (!be.mayEdit(sp)) {
                    sp.sendMessage(Text.translatable("tcgshop.home.not_yours").formatted(Formatting.RED), true);
                    return ItemActionResult.FAIL;
                }
                if (slot >= 0 && ShopServer.canMerge(be.getStack(slot), stack)) {
                    ItemStack cur = be.getStack(slot).copy();
                    int add = Math.min(stack.getCount(), cur.getMaxCount() - cur.getCount());
                    cur.increment(add);
                    stack.decrement(add);
                    be.setStack(slot, cur);
                    world.playSound(null, pos, net.minecraft.sound.SoundEvents.ENTITY_ITEM_FRAME_ADD_ITEM, net.minecraft.sound.SoundCategory.BLOCKS, 1f, 1.3f);
                    return ItemActionResult.success(world.isClient);
                }
                int target = slot >= 0 ? slot : -1;
                if (target < 0) for (int i = 0; i < be.size(); i++) if (be.getStack(i).isEmpty()) { target = i; break; }
                if (target < 0) {
                    sp.sendMessage(Text.translatable("tcgshop.shop.full").formatted(Formatting.RED), true);
                    return ItemActionResult.FAIL;
                }
                be.setStack(target, stack.split(1));
                world.playSound(null, pos, net.minecraft.sound.SoundEvents.ENTITY_ITEM_FRAME_ADD_ITEM, net.minecraft.sound.SoundCategory.BLOCKS, 1f, 1.1f);
            }
            return ItemActionResult.success(world.isClient);
        }
        if (world instanceof ServerWorld sw && player instanceof ServerPlayerEntity sp) {
            BlockPos master = master(state, pos);
            ShopZones.Zone z = ShopZones.get(sw).at(master);
            if (z == null || !z.owner.equals(sp.getUuid())) return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            if (world.getBlockEntity(master) instanceof ShopDisplayBlockEntity be) {
                int slot = slotFor(state, master, hit);
                if (slot >= 0 && !be.getStack(slot).isEmpty() && !ShopServer.canMerge(be.getStack(slot), stack))
                    return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION; // comprar lo que hay
                ShopServer.stock(sp, be, stack, slot);
            }
        }
        return ItemActionResult.success(world.isClient);
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        BlockPos master = master(state, pos);
        if (home) {
            if (world instanceof ServerWorld && player instanceof ServerPlayerEntity sp && world.getBlockEntity(master) instanceof ShopDisplayBlockEntity be) {
                int slot = slotFor(state, master, hit);
                if (slot >= 0 && !be.getStack(slot).isEmpty()) {
                    if (be.mayEdit(sp) && sp.isSneaking()) { // agachado: la coges al momento
                        sp.getInventory().offerOrDrop(be.removeStack(slot));
                        world.playSound(null, pos, net.minecraft.sound.SoundEvents.ENTITY_ITEM_FRAME_REMOVE_ITEM, net.minecraft.sound.SoundCategory.BLOCKS, 1f, 1f);
                    } else if (be.mayEdit(sp)) ShopServer.openSlot(sp, be, slot); // menú: tomar o editar
                    else sp.sendMessage(be.getStack(slot).getName().copy().formatted(Formatting.YELLOW), true);
                } else sp.sendMessage(Text.translatable("tcgshop.home.hint").formatted(Formatting.GRAY), true);
            }
            return ActionResult.success(world.isClient);
        }
        if (world instanceof ServerWorld sw && player instanceof ServerPlayerEntity sp && world.getBlockEntity(master) instanceof ShopDisplayBlockEntity be) {
            ShopZones.Zone z = ShopZones.get(sw).at(master);
            boolean owner = z != null && z.owner.equals(sp.getUuid());
            int slot = slotFor(state, master, hit);
            if (owner && sp.isSneaking()) ShopServer.openDisplay(sp, be);
            else if (owner && slot >= 0 && !be.getStack(slot).isEmpty()) ShopServer.openSlot(sp, be, slot); // tomar o editar esa carta
            else if (slot >= 0 && !be.getStack(slot).isEmpty()) ShopServer.buyDirect(sp, be, slot);
            else if (owner) ShopServer.openDisplay(sp, be);
            else sp.sendMessage(Text.translatable("tcgshop.shop.empty_slot").formatted(Formatting.GRAY), true);
        }
        return ActionResult.success(world.isClient);
    }

    /** Romper cualquier parte rompe el mueble entero; el objeto (y lo expuesto) sale del maestro. */
    @Override
    public BlockState onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        if (!world.isClient && state.get(PART) != 0) {
            BlockPos master = master(state, pos);
            BlockState ms = world.getBlockState(master);
            if (ms.isOf(this) && ms.get(PART) == 0) world.breakBlock(master, !player.isCreative(), player);
        }
        return super.onBreak(world, pos, state, player);
    }

    @Override
    protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (state.isOf(newState.getBlock())) {
            super.onStateReplaced(state, world, pos, newState, moved);
            return;
        }
        if (world.getBlockEntity(pos) instanceof ShopDisplayBlockEntity be) ItemScatterer.spawn(world, pos, be);
        super.onStateReplaced(state, world, pos, newState, moved);
        if (world.isClient) return;
        BlockPos master = master(state, pos);
        Direction facing = state.get(FACING);
        for (int p = 0; p < kind.parts(); p++) {
            BlockPos c = kind.partPos(master, facing, p);
            if (c.equals(pos)) continue;
            BlockState cs = world.getBlockState(c);
            if (cs.isOf(this) && cs.get(FACING) == facing && cs.get(PART) == p) {
                if (p == 0) world.breakBlock(c, true); // el maestro suelta el mueble y sus productos
                else world.setBlockState(c, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL | Block.SKIP_DROPS);
            }
        }
    }
}

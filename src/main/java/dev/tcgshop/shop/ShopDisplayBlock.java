package dev.tcgshop.shop;

import com.mojang.serialization.MapCodec;
import dev.tcgshop.TcgShop;
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
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockRotation;
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

import java.util.ArrayList;
import java.util.List;

/**
 * Multi-block shop display (stand, counter showcase, shelf, gondola, pallet, pedestal, wall).
 * All parts share the block; part 0 (master) holds the inventory and draws the products.
 *  · Right-click a product: you buy it instantly (the money goes straight to the register). Also in your own shop.
 *  · Owner holding a card / pack / box / plush / mystery box (main hand, or off hand with the main hand empty):
 *    puts it up for sale in the slot under the crosshair (or the first free one).
 *  · Owner + Shift + click: manage prices and remove products.
 */
public class ShopDisplayBlock extends BlockWithEntity implements ShopFurniture {
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    public static final IntProperty PART = IntProperty.of("part", 0, 15);
    public final DisplayKind kind;
    /** "Home" display: can be placed anywhere, only its owner adds and removes cards, and nothing is sold. */
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

    /** Master block of the structure this part belongs to. */
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
        // 1.0: every part must be inside the shop (before, only the clicked block was checked, so a wide display could
        // stick out with its master outside the zone: it then rejected every card). And never into someone else's shop.
        if (world instanceof ServerWorld sw && ctx.getPlayer() != null) {
            Text why = zoneProblem(sw, ctx.getPlayer(), master, facing, clicked);
            if (why != null) {
                ctx.getPlayer().sendMessage(why.copy().formatted(Formatting.RED), true);
                return null;
            }
        }
        return getDefaultState().with(FACING, facing).with(PART, clickedPart);
    }

    /** Why this fixture can't go here (zones), or null if it can. Operators in creative skip it (as ShopProtection). */
    @Nullable
    private Text zoneProblem(ServerWorld world, PlayerEntity player, BlockPos master, Direction facing, BlockPos clicked) {
        if (player.isCreative() && player.hasPermissionLevel(2)) return null;
        ShopZones zones = ShopZones.get(world);
        ShopZones.Zone own = zones.at(clicked);
        for (int p = 0; p < kind.parts(); p++) {
            ShopZones.Zone z = zones.at(kind.partPos(master, facing, p));
            if (z != null && !z.owner.equals(player.getUuid())) return Text.translatable("tcgshop.shop.protected", z.ownerName);
            if (!home && (z == null || own == null || !z.id.equals(own.id)))
                return Text.translatable("tcgshop.display.must_fit", kind.w, kind.h, kind.d);
        }
        return null;
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

    /** Structure blocks / editors rotating the fixture: the parts stay consistent because offsets follow FACING. */
    @Override
    protected BlockState rotate(BlockState state, BlockRotation rotation) {
        return state.with(FACING, rotation.rotate(state.get(FACING)));
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

    /**
     * Slot targeted by a player whose eyes are at `eye` (world coordinates; null = use only the hit point) and who
     * clicked `hit` on any part of the structure whose master is `master`, or -1. Same math on client (highlight)
     * and server (placing / buying).
     */
    public int slotFor(BlockState state, BlockPos master, BlockHitResult hit, @Nullable Vec3d eye, @Nullable ShopDisplayBlockEntity be) {
        Vec3d rel = hit.getPos().subtract(master.getX(), master.getY(), master.getZ());
        Vec3d relEye = eye == null ? null : eye.subtract(master.getX(), master.getY(), master.getZ());
        return kind.slotAt(state.get(FACING), relEye, rel, be == null ? null : i -> be.lookScale(i));
    }

    /**
     * Inventory of the structure (server). If the master block is there but lost its block entity (old saves,
     * /setblock, editors), it is recreated empty instead of leaving a dead display; if the master is missing, the
     * player is told to rebuild the fixture.
     */
    @Nullable
    private ShopDisplayBlockEntity entity(ServerWorld world, BlockState state, BlockPos pos, @Nullable ServerPlayerEntity player) {
        BlockPos master = master(state, pos);
        if (world.getBlockEntity(master) instanceof ShopDisplayBlockEntity be) return be;
        BlockState ms = world.getBlockState(master);
        if (ms.isOf(this) && ms.get(PART) == 0 && ms.get(FACING) == state.get(FACING)) {
            world.addBlockEntity(new ShopDisplayBlockEntity(master, ms));
            TcgShop.LOGGER.warn("[display] {} at {} had no inventory: recreated it", Registries.BLOCK.getId(this), master.toShortString());
            if (world.getBlockEntity(master) instanceof ShopDisplayBlockEntity fixed) return fixed;
        }
        if (player != null) player.sendMessage(Text.translatable("tcgshop.display.broken").formatted(Formatting.RED), true);
        return null;
    }

    /** What happened when trying to put the held product on the display. */
    private enum Put { PASS, DONE, FAIL }

    /**
     * Puts `stack` (held by `player`) on the display: restocks the targeted slot if it holds the same product,
     * else the targeted free slot, else the first free one. PASS = the targeted slot holds something else (or you
     * aren't the shop's owner): the normal click handles it (buy / take / edit).
     */
    private Put put(ServerWorld world, ServerPlayerEntity player, BlockState state, ShopDisplayBlockEntity be, BlockHitResult hit, ItemStack stack) {
        BlockPos master = be.getPos();
        int slot = slotFor(state, master, hit, player.getEyePos(), be);
        if (home) {
            boolean mine = be.mayEdit(player);
            if (slot >= 0 && !be.getStack(slot).isEmpty() && !(mine && ShopServer.canMerge(be.getStack(slot), stack))) return Put.PASS;
            if (!mine) {
                player.sendMessage(Text.translatable("tcgshop.home.not_yours").formatted(Formatting.RED), true);
                return Put.FAIL;
            }
            if (slot >= 0 && ShopServer.canMerge(be.getStack(slot), stack)) {
                ItemStack cur = be.getStack(slot).copy();
                int add = Math.min(stack.getCount(), cur.getMaxCount() - cur.getCount());
                cur.increment(add);
                stack.decrement(add);
                be.setStack(slot, cur);
                world.playSound(null, master, net.minecraft.sound.SoundEvents.ENTITY_ITEM_FRAME_ADD_ITEM, net.minecraft.sound.SoundCategory.BLOCKS, 1f, 1.3f);
                return Put.DONE;
            }
            int target = slot;
            if (target < 0) for (int i = 0; i < be.size(); i++) if (be.getStack(i).isEmpty()) { target = i; break; }
            if (target < 0) {
                player.sendMessage(Text.translatable("tcgshop.shop.full").formatted(Formatting.RED), true);
                return Put.FAIL;
            }
            be.setStack(target, stack.split(1));
            world.playSound(null, master, net.minecraft.sound.SoundEvents.ENTITY_ITEM_FRAME_ADD_ITEM, net.minecraft.sound.SoundCategory.BLOCKS, 1f, 1.1f);
            return Put.DONE;
        }
        ShopZones.Zone z = ShopZones.get(world).at(master);
        if (z == null) {
            // the shop is keyed by the master block: tell the owner why their own display takes nothing
            Text why = outsideReason(world, state, master, player, be);
            if (why == null) return Put.PASS;
            player.sendMessage(why.copy().formatted(Formatting.RED), true);
            return Put.FAIL;
        }
        if (!z.owner.equals(player.getUuid())) return Put.PASS;
        if (slot >= 0 && !be.getStack(slot).isEmpty() && !ShopServer.canMerge(be.getStack(slot), stack)) return Put.PASS; // buy / edit what's there
        ShopServer.stock(player, be, stack, slot);
        return Put.DONE;
    }

    /** Shop display whose master is in no zone: message for its owner, or null for anyone else. */
    @Nullable
    private Text outsideReason(ServerWorld world, BlockState state, BlockPos master, ServerPlayerEntity player, ShopDisplayBlockEntity be) {
        ShopZones zones = ShopZones.get(world);
        Direction facing = state.get(FACING);
        for (int p = 1; p < kind.parts(); p++) {
            ShopZones.Zone z = zones.at(kind.partPos(master, facing, p));
            if (z != null && z.owner.equals(player.getUuid())) return Text.translatable("tcgshop.display.sticks_out");
        }
        return player.getUuid().equals(be.owner) ? Text.translatable("tcgshop.display.no_shop") : null;
    }

    @Override
    protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (!ShopServer.isProduct(stack)) return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!(world instanceof ServerWorld sw) || !(player instanceof ServerPlayerEntity sp)) return ItemActionResult.success(true);
        // refusals answer CONSUME, not FAIL: a non-accepted result lets vanilla go on to the item's own useOnBlock,
        // which would place a held booster box (a BlockItem) against the display
        ShopDisplayBlockEntity be = entity(sw, state, pos, sp);
        if (be == null) return ItemActionResult.CONSUME;
        return switch (put(sw, sp, state, be, hit, stack)) {
            case PASS -> ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            case DONE, FAIL -> ItemActionResult.CONSUME;
        };
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!(world instanceof ServerWorld sw) || !(player instanceof ServerPlayerEntity sp)) return ActionResult.success(true);
        ShopDisplayBlockEntity be = entity(sw, state, pos, sp);
        if (be == null) return ActionResult.CONSUME;
        // empty main hand and a product in the off hand: put that one up (before, the off hand was ignored)
        ItemStack off = sp.getOffHandStack();
        if (sp.getMainHandStack().isEmpty() && ShopServer.isProduct(off) && put(sw, sp, state, be, hit, off) != Put.PASS) return ActionResult.CONSUME;
        BlockPos master = be.getPos();
        int slot = slotFor(state, master, hit, sp.getEyePos(), be);
        if (home) {
            if (slot >= 0 && !be.getStack(slot).isEmpty()) {
                if (be.mayEdit(sp) && sp.isSneaking()) { // sneaking: you take it instantly
                    sp.getInventory().offerOrDrop(be.removeStack(slot));
                    world.playSound(null, pos, net.minecraft.sound.SoundEvents.ENTITY_ITEM_FRAME_REMOVE_ITEM, net.minecraft.sound.SoundCategory.BLOCKS, 1f, 1f);
                } else if (be.mayEdit(sp)) ShopServer.openSlot(sp, be, slot); // menu: take or edit
                else sp.sendMessage(be.getStack(slot).getName().copy().formatted(Formatting.YELLOW), true);
            } else sp.sendMessage(Text.translatable("tcgshop.home.hint").formatted(Formatting.GRAY), true);
            return ActionResult.CONSUME;
        }
        ShopZones.Zone z = ShopZones.get(sw).at(master);
        boolean owner = z != null && z.owner.equals(sp.getUuid());
        if (owner && sp.isSneaking()) ShopServer.openDisplay(sp, be);
        else if (owner && slot >= 0 && !be.getStack(slot).isEmpty()) ShopServer.openSlot(sp, be, slot); // take or edit that card
        else if (slot >= 0 && !be.getStack(slot).isEmpty()) ShopServer.buyDirect(sp, be, slot);
        else if (owner) ShopServer.openDisplay(sp, be);
        else {
            Text why = z == null ? outsideReason(sw, state, master, sp, be) : null; // the owner learns why it takes nothing
            sp.sendMessage(why != null ? why.copy().formatted(Formatting.RED) : Text.translatable("tcgshop.shop.empty_slot").formatted(Formatting.GRAY), true);
        }
        return ActionResult.CONSUME;
    }

    /** Breaking any part breaks the whole fixture; the item (and what's displayed) drops from the master. */
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
                if (p == 0) world.breakBlock(c, true); // the master drops the fixture and its products
                else world.setBlockState(c, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL | Block.SKIP_DROPS);
            }
        }
    }

    // ------------------------------------------------------------------ self-test

    /**
     * /tcgshop debug selftest "display_slots" (1.0): builds every display block in the four facings and, for each
     * slot, aims at the product from in front (two distances, eye level and one block higher), raycasts the real
     * outline shapes of the parts and checks that the hit lands on that very slot. Returns the number of checks.
     */
    public static int selfTest(ServerWorld world, BlockPos base) {
        List<String> bad = new ArrayList<>();
        int checks = 0, n = 0;
        Direction[] facings = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
        for (Block blk : Registries.BLOCK) {
            if (!(blk instanceof ShopDisplayBlock b)) continue;
            String name = Registries.BLOCK.getId(b).getPath();
            for (Direction f : facings) {
                BlockPos master = base.add((n % 9) * 5, 0, (n / 9) * 5); // compact grid: stays within a few chunks
                n++;
                BlockState st = b.getDefaultState().with(FACING, f);
                world.setBlockState(master, st);
                b.onPlaced(world, master, st, null, ItemStack.EMPTY);
                if (!(world.getBlockEntity(master) instanceof ShopDisplayBlockEntity be) || be.size() != b.slots()) {
                    bad.add(name + " " + f + ": no inventory");
                    world.removeBlock(master, false);
                    continue;
                }
                Vec3d origin = new Vec3d(master.getX(), master.getY(), master.getZ());
                for (int i = 0; i < b.kind.slots.length; i++) {
                    DisplayKind.Slot s = b.kind.slots[i];
                    Vec3d aim = switch (b.kind.layout) {
                        case BOXES -> new Vec3d(s.x(), s.y() + s.size() * 0.31, s.z() - s.size() * 0.4);
                        default -> new Vec3d(s.x(), s.y(), s.z());
                    };
                    boolean back = s.face() < 0;
                    for (double dist : new double[]{1.0, 2.0}) {
                        for (double eyeY : new double[]{1.62, 2.62}) {
                            Vec3d eyeL = new Vec3d(s.x(), eyeY, back ? b.kind.d + dist : -dist);
                            Vec3d eye = origin.add(DisplayKind.toWorld(f, eyeL)), target = origin.add(DisplayKind.toWorld(f, aim));
                            Vec3d end = eye.add(target.subtract(eye).normalize().multiply(6));
                            BlockHitResult hit = null;
                            double bestD = Double.MAX_VALUE;
                            for (int p = 0; p < b.kind.parts(); p++) {
                                BlockPos pp = b.kind.partPos(master, f, p);
                                BlockHitResult h = world.getBlockState(pp).getOutlineShape(world, pp).raycast(eye, end, pp);
                                if (h != null && h.getPos().squaredDistanceTo(eye) < bestD) {
                                    hit = h;
                                    bestD = h.getPos().squaredDistanceTo(eye);
                                }
                            }
                            checks++;
                            if (hit == null) {
                                bad.add(name + " " + f + " slot " + i + ": not clickable");
                                continue;
                            }
                            int got = b.slotFor(world.getBlockState(hit.getBlockPos()), master, hit, eye, be);
                            if (got != i) bad.add(name + " " + f + " slot " + i + " -> " + got + " (dist " + dist + ", eye " + eyeY + ")");
                        }
                    }
                }
                world.removeBlock(master, false);
            }
        }
        if (!bad.isEmpty())
            throw new IllegalStateException(bad.size() + "/" + checks + " display slot checks failed, e.g. " + bad.subList(0, Math.min(8, bad.size())));
        return checks;
    }
}

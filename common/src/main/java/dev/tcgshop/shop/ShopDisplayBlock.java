package dev.tcgshop.shop;

import com.mojang.serialization.MapCodec;
import dev.tcgshop.TcgShop;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Multi-block shop display (stand, counter showcase, shelf, gondola, pallet, pedestal, wall).
 * All parts share the block; part 0 (master) holds the inventory and draws the products.
 *  · Right-click a product: you buy it instantly (the money goes straight to the register). Also in your own shop.
 *  · Owner holding a card / pack / box / plush / mystery box (main hand, or off hand with the main hand empty):
 *    puts it up for sale in the slot under the crosshair (or the first free one).
 *  · Owner + Shift + click: manage prices and remove products.
 */
public class ShopDisplayBlock extends BaseEntityBlock implements ShopFurniture {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 15);
    public final DisplayKind kind;
    /** "Home" display: can be placed anywhere, only its owner adds and removes cards, and nothing is sold. */
    public final boolean home;
    /** 1.0 RGB displays: default LED animation ({@link RgbLighting} mode), or -1 for a display without LEDs. */
    public final int rgbDefault;

    public ShopDisplayBlock(DisplayKind kind, Properties settings) {
        this(kind, false, settings);
    }

    public ShopDisplayBlock(DisplayKind kind, boolean home, Properties settings) {
        this(kind, home, -1, settings);
    }

    public ShopDisplayBlock(DisplayKind kind, boolean home, int rgbDefault, Properties settings) {
        super(settings);
        this.kind = kind;
        this.home = home;
        this.rgbDefault = rgbDefault;
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(PART, 0));
    }

    public int slots() { return kind.slots.length; }

    /** Has animated RGB lighting (drawn by client.render.RgbLights). */
    public boolean isRgb() { return rgbDefault >= 0; }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(s -> new ShopDisplayBlock(kind, home, rgbDefault, s));
    }

    @Override
    public boolean shopOnly() { return !home;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART);
    }

    /** Master block of the structure this part belongs to. */
    public BlockPos master(BlockState state, BlockPos pos) {
        return kind.masterOf(pos, state.getValue(FACING), state.getValue(PART));
    }

    @Nullable
    public static ShopDisplayBlockEntity masterEntity(Level world, BlockState state, BlockPos pos) {
        if (!(state.getBlock() instanceof ShopDisplayBlock b)) return null;
        return world.getBlockEntity(b.master(state, pos)) instanceof ShopDisplayBlockEntity be ? be : null;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction facing = ctx.getHorizontalDirection().getOpposite();
        Level world = ctx.getLevel();
        BlockPos clicked = ctx.getClickedPos();
        BlockPos master = kind.masterForClick(clicked, facing);
        int clickedPart = 0;
        for (int p = 0; p < kind.parts(); p++) {
            BlockPos c = kind.partPos(master, facing, p);
            if (c.equals(clicked)) { clickedPart = p; continue; }
            if (c.getY() < world.getMinBuildHeight() || c.getY() >= world.getMaxBuildHeight() || !world.getBlockState(c).canBeReplaced()) {
                if (ctx.getPlayer() != null && !world.isClientSide)
                    ctx.getPlayer().displayClientMessage(Component.translatable("tcgshop.shop.no_room", kind.w, kind.h, kind.d).withStyle(ChatFormatting.RED), true);
                return null;
            }
        }
        // 1.0: every part must be inside the shop (before, only the clicked block was checked, so a wide display could
        // stick out with its master outside the zone: it then rejected every card). And never into someone else's shop.
        if (world instanceof ServerLevel sw && ctx.getPlayer() != null) {
            Component why = zoneProblem(sw, ctx.getPlayer(), master, facing, clicked);
            if (why != null) {
                ctx.getPlayer().displayClientMessage(why.copy().withStyle(ChatFormatting.RED), true);
                return null;
            }
        }
        return defaultBlockState().setValue(FACING, facing).setValue(PART, clickedPart);
    }

    /** Why this fixture can't go here (zones), or null if it can. Operators in creative skip it (as ShopProtection). */
    @Nullable
    private Component zoneProblem(ServerLevel world, Player player, BlockPos master, Direction facing, BlockPos clicked) {
        if (player.isCreative() && player.hasPermissions(2)) return null;
        ShopZones zones = ShopZones.get(world);
        ShopZones.Zone own = zones.at(clicked);
        for (int p = 0; p < kind.parts(); p++) {
            ShopZones.Zone z = zones.at(kind.partPos(master, facing, p));
            if (z != null && !z.owner.equals(player.getUUID())) return Component.translatable("tcgshop.shop.protected", z.ownerName);
            if (!home && (z == null || own == null || !z.id.equals(own.id)))
                return Component.translatable("tcgshop.display.must_fit", kind.w, kind.h, kind.d);
        }
        return null;
    }

    @Override
    public void setPlacedBy(Level world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        super.setPlacedBy(world, pos, state, placer, itemStack);
        if (world.isClientSide) return;
        BlockPos master = master(state, pos);
        Direction facing = state.getValue(FACING);
        int self = state.getValue(PART);
        for (int p = 0; p < kind.parts(); p++) {
            if (p == self) continue;
            world.setBlock(kind.partPos(master, facing, p), state.setValue(PART, p), Block.UPDATE_ALL);
        }
        if (placer != null && world.getBlockEntity(master) instanceof ShopDisplayBlockEntity be) {
            be.owner = placer.getUUID();
            be.changed();
        }
    }

    /** Structure blocks / editors rotating the fixture: the parts stay consistent because offsets follow FACING. */
    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return kind.shape(state.getValue(FACING), state.getValue(PART));
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PART) == 0 ? new ShopDisplayBlockEntity(pos, state) : null;
    }

    /**
     * Slot targeted by a player whose eyes are at `eye` (world coordinates; null = use only the hit point) and who
     * clicked `hit` on any part of the structure whose master is `master`, or -1. Same math on client (highlight)
     * and server (placing / buying).
     */
    public int slotFor(BlockState state, BlockPos master, BlockHitResult hit, @Nullable Vec3 eye, @Nullable ShopDisplayBlockEntity be) {
        Vec3 rel = hit.getLocation().subtract(master.getX(), master.getY(), master.getZ());
        Vec3 relEye = eye == null ? null : eye.subtract(master.getX(), master.getY(), master.getZ());
        return kind.slotAt(state.getValue(FACING), relEye, rel, be == null ? null : i -> be.lookScale(i));
    }

    /**
     * Inventory of the structure (server). If the master block is there but lost its block entity (old saves,
     * /setblock, editors), it is recreated empty instead of leaving a dead display; if the master is missing, the
     * player is told to rebuild the fixture.
     */
    @Nullable
    private ShopDisplayBlockEntity entity(ServerLevel world, BlockState state, BlockPos pos, @Nullable ServerPlayer player) {
        BlockPos master = master(state, pos);
        if (world.getBlockEntity(master) instanceof ShopDisplayBlockEntity be) return be;
        BlockState ms = world.getBlockState(master);
        if (ms.is(this) && ms.getValue(PART) == 0 && ms.getValue(FACING) == state.getValue(FACING)) {
            world.setBlockEntity(new ShopDisplayBlockEntity(master, ms));
            TcgShop.LOGGER.warn("[display] {} at {} had no inventory: recreated it", BuiltInRegistries.BLOCK.getKey(this), master.toShortString());
            if (world.getBlockEntity(master) instanceof ShopDisplayBlockEntity fixed) return fixed;
        }
        if (player != null) player.displayClientMessage(Component.translatable("tcgshop.display.broken").withStyle(ChatFormatting.RED), true);
        return null;
    }

    /** What happened when trying to put the held product on the display. */
    private enum Put { PASS, DONE, FAIL }

    /**
     * Puts `stack` (held by `player`) on the display: restocks the targeted slot if it holds the same product,
     * else the targeted free slot, else the first free one. PASS = the targeted slot holds something else (or you
     * aren't the shop's owner): the normal click handles it (buy / take / edit).
     */
    private Put put(ServerLevel world, ServerPlayer player, BlockState state, ShopDisplayBlockEntity be, BlockHitResult hit, ItemStack stack) {
        BlockPos master = be.getBlockPos();
        int slot = slotFor(state, master, hit, player.getEyePosition(), be);
        if (home) {
            boolean mine = be.mayEdit(player);
            if (slot >= 0 && !be.getItem(slot).isEmpty() && !(mine && ShopServer.canMerge(be.getItem(slot), stack))) return Put.PASS;
            if (!mine) {
                player.displayClientMessage(Component.translatable("tcgshop.home.not_yours").withStyle(ChatFormatting.RED), true);
                return Put.FAIL;
            }
            if (dev.tcgshop.entity.CollectorTheft.refuseHot(player, stack)) return Put.FAIL; // 1.0 beta 3: no hiding a hunted card
            if (slot >= 0 && ShopServer.canMerge(be.getItem(slot), stack)) {
                ItemStack cur = be.getItem(slot).copy();
                int add = Math.min(stack.getCount(), cur.getMaxStackSize() - cur.getCount());
                cur.grow(add);
                stack.shrink(add);
                be.setItem(slot, cur);
                world.playSound(null, master, net.minecraft.sounds.SoundEvents.ITEM_FRAME_ADD_ITEM, net.minecraft.sounds.SoundSource.BLOCKS, 1f, 1.3f);
                return Put.DONE;
            }
            int target = slot;
            if (target < 0) for (int i = 0; i < be.getContainerSize(); i++) if (be.getItem(i).isEmpty()) { target = i; break; }
            if (target < 0) {
                player.displayClientMessage(Component.translatable("tcgshop.shop.full").withStyle(ChatFormatting.RED), true);
                return Put.FAIL;
            }
            be.setItem(target, stack.split(1));
            world.playSound(null, master, net.minecraft.sounds.SoundEvents.ITEM_FRAME_ADD_ITEM, net.minecraft.sounds.SoundSource.BLOCKS, 1f, 1.1f);
            return Put.DONE;
        }
        ShopZones.Zone z = ShopZones.get(world).at(master);
        if (z == null) {
            // the shop is keyed by the master block: tell the owner why their own display takes nothing
            Component why = outsideReason(world, state, master, player, be);
            if (why == null) return Put.PASS;
            player.displayClientMessage(why.copy().withStyle(ChatFormatting.RED), true);
            return Put.FAIL;
        }
        if (!z.owner.equals(player.getUUID())) return Put.PASS;
        if (slot >= 0 && !be.getItem(slot).isEmpty() && !ShopServer.canMerge(be.getItem(slot), stack)) return Put.PASS; // buy / edit what's there
        if (dev.tcgshop.entity.CollectorTheft.refuseHot(player, stack)) return Put.FAIL; // 1.0 beta 3: a hunted card is not put up for sale
        ShopServer.stock(player, be, stack, slot);
        return Put.DONE;
    }

    /**
     * 1.0 RGB displays: sneak + right-click with a dye (LED color) or glowstone dust (next animation), on any part.
     * Called from RgbLighting's UseBlockCallback: vanilla skips the block's own use handlers while sneaking with an
     * item in hand, so onUseWithItem never sees it.
     */
    public void configureLights(ServerLevel world, ServerPlayer player, BlockState state, BlockPos pos, ItemStack stack) {
        if (!isRgb()) return;
        ShopDisplayBlockEntity be = entity(world, state, pos, player);
        if (be != null) RgbLighting.configure(world, player, be, stack, mayConfigureLights(world, player, be));
    }

    /** Who may change the LEDs: home displays their owner; shop displays the shop's owner (or whoever placed it); ops in creative. */
    private boolean mayConfigureLights(ServerLevel world, ServerPlayer player, ShopDisplayBlockEntity be) {
        if (player.isCreative() && player.hasPermissions(2)) return true;
        if (home) return be.mayEdit(player);
        ShopZones.Zone z = ShopZones.get(world).at(be.getBlockPos());
        return z != null ? z.owner.equals(player.getUUID()) : player.getUUID().equals(be.owner);
    }

    /** Shop display whose master is in no zone: message for its owner, or null for anyone else. */
    @Nullable
    private Component outsideReason(ServerLevel world, BlockState state, BlockPos master, ServerPlayer player, ShopDisplayBlockEntity be) {
        ShopZones zones = ShopZones.get(world);
        Direction facing = state.getValue(FACING);
        for (int p = 1; p < kind.parts(); p++) {
            ShopZones.Zone z = zones.at(kind.partPos(master, facing, p));
            if (z != null && z.owner.equals(player.getUUID())) return Component.translatable("tcgshop.display.sticks_out");
        }
        return player.getUUID().equals(be.owner) ? Component.translatable("tcgshop.display.no_shop") : null;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!ShopServer.isProduct(stack)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!(world instanceof ServerLevel sw) || !(player instanceof ServerPlayer sp)) return ItemInteractionResult.sidedSuccess(true);
        // refusals answer CONSUME, not FAIL: a non-accepted result lets vanilla go on to the item's own useOnBlock,
        // which would place a held booster box (a BlockItem) against the display
        ShopDisplayBlockEntity be = entity(sw, state, pos, sp);
        if (be == null) return ItemInteractionResult.CONSUME;
        return switch (put(sw, sp, state, be, hit, stack)) {
            case PASS -> ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            case DONE, FAIL -> ItemInteractionResult.CONSUME;
        };
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(world instanceof ServerLevel sw) || !(player instanceof ServerPlayer sp)) return InteractionResult.sidedSuccess(true);
        ShopDisplayBlockEntity be = entity(sw, state, pos, sp);
        if (be == null) return InteractionResult.CONSUME;
        // empty main hand and a product in the off hand: put that one up (before, the off hand was ignored)
        ItemStack off = sp.getOffhandItem();
        if (sp.getMainHandItem().isEmpty() && ShopServer.isProduct(off) && put(sw, sp, state, be, hit, off) != Put.PASS) return InteractionResult.CONSUME;
        BlockPos master = be.getBlockPos();
        int slot = slotFor(state, master, hit, sp.getEyePosition(), be);
        if (home) {
            if (slot >= 0 && !be.getItem(slot).isEmpty()) {
                if (be.mayEdit(sp) && sp.isShiftKeyDown()) { // sneaking: you take it instantly
                    sp.getInventory().placeItemBackInInventory(be.removeItemNoUpdate(slot));
                    world.playSound(null, pos, net.minecraft.sounds.SoundEvents.ITEM_FRAME_REMOVE_ITEM, net.minecraft.sounds.SoundSource.BLOCKS, 1f, 1f);
                } else if (be.mayEdit(sp)) ShopServer.openSlot(sp, be, slot); // menu: take or edit
                else sp.displayClientMessage(be.getItem(slot).getHoverName().copy().withStyle(ChatFormatting.YELLOW), true);
            } else sp.displayClientMessage(Component.translatable("tcgshop.home.hint").withStyle(ChatFormatting.GRAY), true);
            return InteractionResult.CONSUME;
        }
        ShopZones.Zone z = ShopZones.get(sw).at(master);
        boolean owner = z != null && z.owner.equals(sp.getUUID());
        if (owner && sp.isShiftKeyDown()) ShopServer.openDisplay(sp, be);
        else if (owner && slot >= 0 && !be.getItem(slot).isEmpty()) ShopServer.openSlot(sp, be, slot); // take or edit that card
        else if (slot >= 0 && !be.getItem(slot).isEmpty()) ShopServer.buyDirect(sp, be, slot);
        else if (owner) ShopServer.openDisplay(sp, be);
        else {
            Component why = z == null ? outsideReason(sw, state, master, sp, be) : null; // the owner learns why it takes nothing
            sp.displayClientMessage(why != null ? why.copy().withStyle(ChatFormatting.RED) : Component.translatable("tcgshop.shop.empty_slot").withStyle(ChatFormatting.GRAY), true);
        }
        return InteractionResult.CONSUME;
    }

    /** Breaking any part breaks the whole fixture; the item (and what's displayed) drops from the master. */
    @Override
    public BlockState playerWillDestroy(Level world, BlockPos pos, BlockState state, Player player) {
        if (!world.isClientSide && state.getValue(PART) != 0) {
            BlockPos master = master(state, pos);
            BlockState ms = world.getBlockState(master);
            if (ms.is(this) && ms.getValue(PART) == 0) world.destroyBlock(master, !player.isCreative(), player);
        }
        return super.playerWillDestroy(world, pos, state, player);
    }

    @Override
    protected void onRemove(BlockState state, Level world, BlockPos pos, BlockState newState, boolean moved) {
        if (state.is(newState.getBlock())) {
            super.onRemove(state, world, pos, newState, moved);
            return;
        }
        if (world.getBlockEntity(pos) instanceof ShopDisplayBlockEntity be) Containers.dropContents(world, pos, be);
        super.onRemove(state, world, pos, newState, moved);
        if (world.isClientSide) return;
        BlockPos master = master(state, pos);
        Direction facing = state.getValue(FACING);
        for (int p = 0; p < kind.parts(); p++) {
            BlockPos c = kind.partPos(master, facing, p);
            if (c.equals(pos)) continue;
            BlockState cs = world.getBlockState(c);
            if (cs.is(this) && cs.getValue(FACING) == facing && cs.getValue(PART) == p) {
                if (p == 0) world.destroyBlock(c, true); // the master drops the fixture and its products
                else world.setBlock(c, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
            }
        }
    }

    // ------------------------------------------------------------------ self-test

    /**
     * /tcgshop debug selftest "display_slots" (1.0): builds every display block in the four facings and, for each
     * slot, aims at the product from in front (two distances, eye level and one block higher), raycasts the real
     * outline shapes of the parts and checks that the hit lands on that very slot. Returns the number of checks.
     * It walks the block registry, so every display (home, classic and the 1.0 RGB ones) and every DisplayKind in use is
     * covered automatically; it also fails if a DisplayKind has no block at all (an untested kind).
     */
    public static int selfTest(ServerLevel world, BlockPos base) {
        List<String> bad = new ArrayList<>();
        int checks = 0, n = 0;
        Direction[] facings = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
        java.util.EnumSet<DisplayKind> tested = java.util.EnumSet.noneOf(DisplayKind.class);
        for (Block blk : BuiltInRegistries.BLOCK) {
            if (!(blk instanceof ShopDisplayBlock b)) continue;
            String name = BuiltInRegistries.BLOCK.getKey(b).getPath();
            tested.add(b.kind);
            for (Direction f : facings) {
                BlockPos master = base.offset((n % 9) * 5, 0, (n / 9) * 5); // compact grid: stays within a few chunks
                n++;
                BlockState st = b.defaultBlockState().setValue(FACING, f);
                world.setBlockAndUpdate(master, st);
                b.setPlacedBy(world, master, st, null, ItemStack.EMPTY);
                if (!(world.getBlockEntity(master) instanceof ShopDisplayBlockEntity be) || be.getContainerSize() != b.slots()) {
                    bad.add(name + " " + f + ": no inventory");
                    world.removeBlock(master, false);
                    continue;
                }
                Vec3 origin = new Vec3(master.getX(), master.getY(), master.getZ());
                for (int i = 0; i < b.kind.slots.length; i++) {
                    DisplayKind.Slot s = b.kind.slots[i];
                    Vec3 aim = switch (b.kind.layout) {
                        case BOXES -> new Vec3(s.x(), s.y() + s.size() * 0.31, s.z() - s.size() * 0.4);
                        default -> new Vec3(s.x(), s.y(), s.z());
                    };
                    boolean back = s.face() < 0;
                    for (double dist : new double[]{1.0, 2.0}) {
                        for (double eyeY : new double[]{1.62, 2.62}) {
                            Vec3 eyeL = new Vec3(s.x(), eyeY, back ? b.kind.d + dist : -dist);
                            Vec3 eye = origin.add(DisplayKind.toWorld(f, eyeL)), target = origin.add(DisplayKind.toWorld(f, aim));
                            Vec3 end = eye.add(target.subtract(eye).normalize().scale(6));
                            BlockHitResult hit = null;
                            double bestD = Double.MAX_VALUE;
                            for (int p = 0; p < b.kind.parts(); p++) {
                                BlockPos pp = b.kind.partPos(master, f, p);
                                BlockHitResult h = world.getBlockState(pp).getShape(world, pp).clip(eye, end, pp);
                                if (h != null && h.getLocation().distanceToSqr(eye) < bestD) {
                                    hit = h;
                                    bestD = h.getLocation().distanceToSqr(eye);
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
        for (DisplayKind k : DisplayKind.values()) if (!tested.contains(k)) bad.add(k + ": no display block uses it (untested)");
        if (!bad.isEmpty())
            throw new IllegalStateException(bad.size() + "/" + checks + " display slot checks failed, e.g. " + bad.subList(0, Math.min(8, bad.size())));
        return checks;
    }
}

package dev.tcgshop.shop;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Shop fixture with N slots for sale, each with its price in ₽.
 * Synced to the client to draw the displayed cards/packs.
 */
public class ShopDisplayBlockEntity extends BlockEntity implements Container {
    private NonNullList<ItemStack> items;
    private long[] prices;
    /** Look of each slot: scale x1000 and X/Y/Z rotation in degrees (4 ints per slot). */
    private int[] look;
    /** Who placed it (home displays: only they add and remove). */
    public java.util.UUID owner;
    /** 1.0 RGB displays: LED animation chosen by the owner ({@link RgbLighting}), -1 = the block's default. */
    private int rgbMode = -1;
    /** 1.0 RGB displays: color of the {@link RgbLighting#STATIC} mode (0xRRGGBB). */
    private int rgbColor = 0xFFFFFF;

    public ShopDisplayBlockEntity(BlockPos pos, BlockState state) {
        super(TcgBlocks.DISPLAY_BE, pos, state);
        int n = state.getBlock() instanceof ShopDisplayBlock b ? b.slots() : 4;
        items = NonNullList.withSize(n, ItemStack.EMPTY);
        prices = new long[n];
        look = defaultLook(n);
    }

    private static int[] defaultLook(int n) {
        int[] l = new int[n * 4];
        for (int i = 0; i < n; i++) l[i * 4] = 1000;
        return l;
    }

    public static final float MIN_SCALE = 0.5f, MAX_SCALE = 1.8f;

    public float lookScale(int slot) {
        return slot >= 0 && slot * 4 < look.length ? look[slot * 4] / 1000f : 1f;
    }

    /** Slot rotation in degrees: axis 0 = X, 1 = Y, 2 = Z. */
    public int lookRot(int slot, int axis) {
        return slot >= 0 && slot * 4 + 1 + axis < look.length ? look[slot * 4 + 1 + axis] : 0;
    }

    public boolean hasLook(int slot) {
        return lookScale(slot) != 1f || lookRot(slot, 0) != 0 || lookRot(slot, 1) != 0 || lookRot(slot, 2) != 0;
    }

    public void setLook(int slot, float scale, int rx, int ry, int rz) {
        if (slot < 0 || slot * 4 + 3 >= look.length) return;
        look[slot * 4] = Math.round(Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale)) * 1000);
        look[slot * 4 + 1] = wrap(rx);
        look[slot * 4 + 2] = wrap(ry);
        look[slot * 4 + 3] = wrap(rz);
        changed();
    }

    public void resetLook(int slot) {
        setLook(slot, 1f, 0, 0, 0);
    }

    private static int wrap(int deg) {
        int d = Math.floorMod(deg + 180, 360) - 180;
        return d;
    }

    public boolean isHome() {
        return getBlockState().getBlock() instanceof ShopDisplayBlock b && b.home;
    }

    public boolean mayEdit(Player p) {
        return owner == null || owner.equals(p.getUUID()) || p.isCreative();
    }

    public long price(int slot) { return slot >= 0 && slot < prices.length ? prices[slot] : 0; }

    public void setPrice(int slot, long price) {
        if (slot < 0 || slot >= prices.length) return;
        prices[slot] = Math.max(1, Math.min(1_000_000_000L, price));
        changed();
    }

    /** LED animation in use (the block's default when the owner never chose one). */
    public int rgbMode() {
        if (rgbMode >= 0) return rgbMode;
        return getBlockState().getBlock() instanceof ShopDisplayBlock b && b.isRgb() ? b.rgbDefault : RgbLighting.CHASE;
    }

    public int rgbColor() { return rgbColor; }

    public void setRgb(int mode, int color) {
        rgbMode = RgbLighting.valid(mode) ? mode : -1;
        rgbColor = color & 0xFFFFFF;
        changed();
    }

    public void changed() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider lookup) {
        super.saveAdditional(nbt, lookup);
        ContainerHelper.saveAllItems(nbt, items, true, lookup);
        nbt.putLongArray("Prices", prices);
        nbt.putIntArray("Look", look);
        if (owner != null) nbt.putUUID("Owner", owner);
        if (rgbMode >= 0) {
            nbt.putInt("RgbMode", rgbMode);
            nbt.putInt("RgbColor", rgbColor);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider lookup) {
        super.loadAdditional(nbt, lookup);
        items = NonNullList.withSize(items.size(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(nbt, items, lookup);
        long[] p = nbt.getLongArray("Prices");
        prices = new long[items.size()];
        System.arraycopy(p, 0, prices, 0, Math.min(p.length, prices.length));
        look = defaultLook(items.size());
        if (nbt.contains("Look")) {
            int[] l = nbt.getIntArray("Look");
            System.arraycopy(l, 0, look, 0, Math.min(l.length, look.length));
        }
        owner = nbt.hasUUID("Owner") ? nbt.getUUID("Owner") : null;
        rgbMode = nbt.contains("RgbMode") && RgbLighting.valid(nbt.getInt("RgbMode")) ? nbt.getInt("RgbMode") : -1;
        rgbColor = nbt.contains("RgbColor") ? nbt.getInt("RgbColor") & 0xFFFFFF : 0xFFFFFF;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider lookup) {
        return saveWithoutMetadata(lookup);
    }

    // --- Inventory ---
    @Override public int getContainerSize() { return items.size(); }
    @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getItem(int slot) { return items.get(slot); }
    @Override public ItemStack removeItem(int slot, int amount) {
        ItemStack r = ContainerHelper.removeItem(items, slot, amount);
        changed();
        return r;
    }
    @Override public ItemStack removeItemNoUpdate(int slot) {
        ItemStack r = ContainerHelper.takeItem(items, slot);
        if (items.get(slot).isEmpty() && slot * 4 < look.length) { look[slot * 4] = 1000; look[slot * 4 + 1] = look[slot * 4 + 2] = look[slot * 4 + 3] = 0; }
        changed();
        return r;
    }
    @Override public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        if (stack.isEmpty() && slot * 4 < look.length) { look[slot * 4] = 1000; look[slot * 4 + 1] = look[slot * 4 + 2] = look[slot * 4 + 3] = 0; }
        changed();
    }
    @Override public boolean stillValid(Player player) { return false; }
    /** 1.0 beta 3: hoppers can't sneak in a card stolen from a collector who is still hunting for it. */
    @Override public boolean canPlaceItem(int slot, ItemStack stack) {
        return level == null || level.isClientSide || !dev.tcgshop.entity.CollectorTheft.isHot(level.getServer(), stack);
    }
    @Override public void clearContent() { items.clear(); }
}

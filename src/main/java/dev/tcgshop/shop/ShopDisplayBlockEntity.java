package dev.tcgshop.shop;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;

/**
 * Shop fixture with N slots for sale, each with its price in ₽.
 * Synced to the client to draw the displayed cards/packs.
 */
public class ShopDisplayBlockEntity extends BlockEntity implements Inventory {
    private DefaultedList<ItemStack> items;
    private long[] prices;
    /** Look of each slot: scale x1000 and X/Y/Z rotation in degrees (4 ints per slot). */
    private int[] look;
    /** Who placed it (home displays: only they add and remove). */
    public java.util.UUID owner;

    public ShopDisplayBlockEntity(BlockPos pos, BlockState state) {
        super(TcgBlocks.DISPLAY_BE, pos, state);
        int n = state.getBlock() instanceof ShopDisplayBlock b ? b.slots() : 4;
        items = DefaultedList.ofSize(n, ItemStack.EMPTY);
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
        return getCachedState().getBlock() instanceof ShopDisplayBlock b && b.home;
    }

    public boolean mayEdit(PlayerEntity p) {
        return owner == null || owner.equals(p.getUuid()) || p.isCreative();
    }

    public long price(int slot) { return slot >= 0 && slot < prices.length ? prices[slot] : 0; }

    public void setPrice(int slot, long price) {
        if (slot < 0 || slot >= prices.length) return;
        prices[slot] = Math.max(1, Math.min(1_000_000_000L, price));
        changed();
    }

    public void changed() {
        markDirty();
        if (world != null) world.updateListeners(pos, getCachedState(), getCachedState(), 3);
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        super.writeNbt(nbt, lookup);
        Inventories.writeNbt(nbt, items, true, lookup);
        nbt.putLongArray("Prices", prices);
        nbt.putIntArray("Look", look);
        if (owner != null) nbt.putUuid("Owner", owner);
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        super.readNbt(nbt, lookup);
        items = DefaultedList.ofSize(items.size(), ItemStack.EMPTY);
        Inventories.readNbt(nbt, items, lookup);
        long[] p = nbt.getLongArray("Prices");
        prices = new long[items.size()];
        System.arraycopy(p, 0, prices, 0, Math.min(p.length, prices.length));
        look = defaultLook(items.size());
        if (nbt.contains("Look")) {
            int[] l = nbt.getIntArray("Look");
            System.arraycopy(l, 0, look, 0, Math.min(l.length, look.length));
        }
        owner = nbt.containsUuid("Owner") ? nbt.getUuid("Owner") : null;
    }

    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup lookup) {
        return createNbt(lookup);
    }

    // --- Inventory ---
    @Override public int size() { return items.size(); }
    @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getStack(int slot) { return items.get(slot); }
    @Override public ItemStack removeStack(int slot, int amount) {
        ItemStack r = Inventories.splitStack(items, slot, amount);
        changed();
        return r;
    }
    @Override public ItemStack removeStack(int slot) {
        ItemStack r = Inventories.removeStack(items, slot);
        if (items.get(slot).isEmpty() && slot * 4 < look.length) { look[slot * 4] = 1000; look[slot * 4 + 1] = look[slot * 4 + 2] = look[slot * 4 + 3] = 0; }
        changed();
        return r;
    }
    @Override public void setStack(int slot, ItemStack stack) {
        items.set(slot, stack);
        if (stack.isEmpty() && slot * 4 < look.length) { look[slot * 4] = 1000; look[slot * 4 + 1] = look[slot * 4 + 2] = look[slot * 4 + 3] = 0; }
        changed();
    }
    @Override public boolean canPlayerUse(PlayerEntity player) { return false; }
    @Override public void clear() { items.clear(); }
}

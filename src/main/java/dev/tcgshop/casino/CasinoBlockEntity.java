package dev.tcgshop.casino;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.UUID;

/**
 * State of a casino machine: its owner, the owner's tray and lifetime statistics (server only), and the last
 * outcome with the tick it happened (synced, so everyone around sees the reels / wheel spin to that result).
 */
public class CasinoBlockEntity extends BlockEntity {
    public UUID owner;
    public String ownerName = "";
    /** Owner's money waiting in the machine. */
    public long tray;
    /** Lifetime totals: staked, paid back to players, rounds. */
    public long wagered, paid;
    public int rounds;
    /** Last outcome shown by the renderer: slot symbols, roulette number, wheel segment... */
    public int[] result = new int[0];
    public long animStart = -1000;

    public CasinoBlockEntity(BlockPos pos, BlockState state) {
        super(CasinoBlocks.MACHINE_BE, pos, state);
    }

    public boolean isOwner(PlayerEntity p) {
        return owner != null && owner.equals(p.getUuid());
    }

    public CasinoGame game() {
        return getCachedState().getBlock() instanceof CasinoMachineBlock b ? b.game : CasinoGame.SLOTS;
    }

    public void animate(int... result) {
        this.result = result.clone();
        this.animStart = world == null ? 0 : world.getTime();
        sync();
    }

    public void sync() {
        markDirty();
        if (world != null) world.updateListeners(pos, getCachedState(), getCachedState(), 3);
    }

    public void record(long bet, long payout) {
        wagered += bet;
        paid += payout;
        rounds++;
        markDirty();
    }

    /** Every 10 seconds: maybe a shop customer standing nearby plays a round (Casino.npcRound). */
    public void serverTick(ServerWorld world) {
        if ((world.getTime() + (pos.asLong() & 0xFF)) % 200 != 0) return;
        Casino.npcTick(world, this);
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        super.writeNbt(nbt, lookup);
        writeClient(nbt);
        nbt.putLong("Tray", tray);
        nbt.putLong("Wagered", wagered);
        nbt.putLong("Paid", paid);
        nbt.putInt("Rounds", rounds);
    }

    /** What the clients need (no money figures). */
    private void writeClient(NbtCompound nbt) {
        if (owner != null) nbt.putUuid("Owner", owner);
        nbt.putString("OwnerName", ownerName);
        nbt.putIntArray("Result", result);
        nbt.putLong("Anim", animStart);
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        super.readNbt(nbt, lookup);
        owner = nbt.containsUuid("Owner") ? nbt.getUuid("Owner") : null;
        ownerName = nbt.getString("OwnerName");
        result = nbt.getIntArray("Result");
        animStart = nbt.contains("Anim") ? nbt.getLong("Anim") : -1000;
        tray = nbt.getLong("Tray");
        wagered = nbt.getLong("Wagered");
        paid = nbt.getLong("Paid");
        rounds = nbt.getInt("Rounds");
    }

    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup lookup) {
        NbtCompound n = new NbtCompound();
        writeClient(n);
        return n;
    }
}

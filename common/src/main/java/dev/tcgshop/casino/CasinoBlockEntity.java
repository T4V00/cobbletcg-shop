package dev.tcgshop.casino;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

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

    public boolean isOwner(Player p) {
        return owner != null && owner.equals(p.getUUID());
    }

    public CasinoGame game() {
        return getBlockState().getBlock() instanceof CasinoMachineBlock b ? b.game : CasinoGame.SLOTS;
    }

    public void animate(int... result) {
        this.result = result.clone();
        this.animStart = level == null ? 0 : level.getGameTime();
        sync();
    }

    public void sync() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    public void record(long bet, long payout) {
        wagered += bet;
        paid += payout;
        rounds++;
        setChanged();
    }

    /** Every 10 seconds: maybe a shop customer standing nearby plays a round (Casino.npcRound). */
    public void serverTick(ServerLevel world) {
        if ((world.getGameTime() + (worldPosition.asLong() & 0xFF)) % 200 != 0) return;
        Casino.npcTick(world, this);
    }

    @Override
    protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider lookup) {
        super.saveAdditional(nbt, lookup);
        writeClient(nbt);
        nbt.putLong("Tray", tray);
        nbt.putLong("Wagered", wagered);
        nbt.putLong("Paid", paid);
        nbt.putInt("Rounds", rounds);
    }

    /** What the clients need (no money figures). */
    private void writeClient(CompoundTag nbt) {
        if (owner != null) nbt.putUUID("Owner", owner);
        nbt.putString("OwnerName", ownerName);
        nbt.putIntArray("Result", result);
        nbt.putLong("Anim", animStart);
    }

    @Override
    protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider lookup) {
        super.loadAdditional(nbt, lookup);
        owner = nbt.hasUUID("Owner") ? nbt.getUUID("Owner") : null;
        ownerName = nbt.getString("OwnerName");
        result = nbt.getIntArray("Result");
        animStart = nbt.contains("Anim") ? nbt.getLong("Anim") : -1000;
        tray = nbt.getLong("Tray");
        wagered = nbt.getLong("Wagered");
        paid = nbt.getLong("Paid");
        rounds = nbt.getInt("Rounds");
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider lookup) {
        CompoundTag n = new CompoundTag();
        writeClient(n);
        return n;
    }
}

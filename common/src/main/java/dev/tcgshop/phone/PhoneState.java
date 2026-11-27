package dev.tcgshop.phone;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Each player's phone tier (stored in the Overworld: data/tcgshop_phone.dat). */
public class PhoneState extends SavedData {
    public static final class Entry {
        public int tier;
        /** Player from before 0.9: keeps all apps. */
        public boolean legacy;
        /**
         * World tick (getTime(), only moves forward: unaffected by /time set or doDaylightCycle) when the call was used
         * (-1 = never). Before 0.9.1 the day number was stored in "call"; that value is discarded.
         */
        public long lastCallTick = -1;
        /** 1.1: same, for the daily Furniture Seller call (its own cooldown). */
        public long lastFurnCallTick = -1;

        CompoundTag write() {
            CompoundTag c = new CompoundTag();
            c.putInt("tier", tier);
            c.putBoolean("legacy", legacy);
            c.putLong("callTick", lastCallTick);
            if (lastFurnCallTick >= 0) c.putLong("furnTick", lastFurnCallTick);
            return c;
        }

        static Entry read(CompoundTag c) {
            Entry e = new Entry();
            e.tier = Math.max(0, Math.min(PhoneTier.MAX_LEVEL, c.getInt("tier")));
            e.legacy = c.getBoolean("legacy");
            e.lastCallTick = c.contains("callTick") ? c.getLong("callTick") : -1;
            e.lastFurnCallTick = c.contains("furnTick") ? c.getLong("furnTick") : -1;
            return e;
        }
    }

    private static final Factory<PhoneState> TYPE = new Factory<>(PhoneState::new, PhoneState::fromNbt, null);

    private final Map<UUID, Entry> players = new HashMap<>();

    public static PhoneState get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE, "tcgshop_phone");
    }

    /** null if this player has no record yet (created by the migration on join). */
    public Entry find(UUID id) {
        return players.get(id);
    }

    public Entry getOrCreate(UUID id) {
        return players.computeIfAbsent(id, k -> {
            setDirty();
            return new Entry();
        });
    }

    private static PhoneState fromNbt(CompoundTag nbt, HolderLookup.Provider lookup) {
        PhoneState s = new PhoneState();
        CompoundTag p = nbt.getCompound("players");
        for (String k : p.getAllKeys()) {
            try {
                s.players.put(UUID.fromString(k), Entry.read(p.getCompound(k)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return s;
    }

    @Override
    public CompoundTag save(CompoundTag nbt, HolderLookup.Provider lookup) {
        CompoundTag p = new CompoundTag();
        players.forEach((k, v) -> p.put(k.toString(), v.write()));
        nbt.put("players", p);
        return nbt;
    }
}

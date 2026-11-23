package dev.tcgshop.phone;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Each player's phone tier (stored in the Overworld: data/tcgshop_phone.dat). */
public class PhoneState extends PersistentState {
    public static final class Entry {
        public int tier;
        /** Player from before 0.9: keeps all apps. */
        public boolean legacy;
        /**
         * World tick (getTime(), only moves forward: unaffected by /time set or doDaylightCycle) when the call was used
         * (-1 = never). Before 0.9.1 the day number was stored in "call"; that value is discarded.
         */
        public long lastCallTick = -1;

        NbtCompound write() {
            NbtCompound c = new NbtCompound();
            c.putInt("tier", tier);
            c.putBoolean("legacy", legacy);
            c.putLong("callTick", lastCallTick);
            return c;
        }

        static Entry read(NbtCompound c) {
            Entry e = new Entry();
            e.tier = Math.max(0, Math.min(PhoneTier.MAX_LEVEL, c.getInt("tier")));
            e.legacy = c.getBoolean("legacy");
            e.lastCallTick = c.contains("callTick") ? c.getLong("callTick") : -1;
            return e;
        }
    }

    private static final Type<PhoneState> TYPE = new Type<>(PhoneState::new, PhoneState::fromNbt, null);

    private final Map<UUID, Entry> players = new HashMap<>();

    public static PhoneState get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE, "tcgshop_phone");
    }

    /** null if this player has no record yet (created by the migration on join). */
    public Entry find(UUID id) {
        return players.get(id);
    }

    public Entry getOrCreate(UUID id) {
        return players.computeIfAbsent(id, k -> {
            markDirty();
            return new Entry();
        });
    }

    private static PhoneState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        PhoneState s = new PhoneState();
        NbtCompound p = nbt.getCompound("players");
        for (String k : p.getKeys()) {
            try {
                s.players.put(UUID.fromString(k), Entry.read(p.getCompound(k)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return s;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        NbtCompound p = new NbtCompound();
        players.forEach((k, v) -> p.put(k.toString(), v.write()));
        nbt.put("players", p);
        return nbt;
    }
}

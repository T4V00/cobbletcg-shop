package dev.tcgshop.phone;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Nivel del teléfono de cada jugador (guardado en el Overworld: data/tcgshop_phone.dat). */
public class PhoneState extends PersistentState {
    public static final class Entry {
        public int tier;
        /** Jugador de antes de la 0.9: conserva todas las apps. */
        public boolean legacy;
        /**
         * Tick del mundo (getTime(), sólo avanza: no le afecta /time set ni doDaylightCycle) en que usó la llamada
         * (-1 = nunca). Antes de 0.9.1 se guardaba el número de día en "call"; ese valor se descarta.
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

    /** null si este jugador aún no tiene registro (se crea con la migración al entrar). */
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

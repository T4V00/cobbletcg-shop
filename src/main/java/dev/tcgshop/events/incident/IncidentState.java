package dev.tcgshop.events.incident;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The incident module's own (world) state: how many reward packages each owner has opened this in-game
 * week (weekly cap per OWNER, across all their shops and worlds; critique C1/F #88).
 */
public final class IncidentState extends PersistentState {
    private static final Type<IncidentState> TYPE = new Type<>(IncidentState::new, IncidentState::fromNbt, null);

    private record Week(long week, int count) {}

    private final Map<UUID, Week> packages = new HashMap<>();

    public static IncidentState get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE, "tcgshop_incidents");
    }

    /** Reward packages opened by that owner in the week (day / 7). */
    public int packagesThisWeek(UUID owner, long day) {
        Week w = packages.get(owner);
        return w == null || w.week() != day / 7 ? 0 : w.count();
    }

    public void addPackage(UUID owner, long day) {
        packages.put(owner, new Week(day / 7, packagesThisWeek(owner, day) + 1));
        markDirty();
    }

    private static IncidentState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        IncidentState s = new IncidentState();
        NbtCompound p = nbt.getCompound("packages");
        for (String k : p.getKeys()) {
            try {
                NbtCompound c = p.getCompound(k);
                s.packages.put(UUID.fromString(k), new Week(c.getLong("w"), c.getInt("n")));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return s;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        NbtCompound p = new NbtCompound();
        for (Map.Entry<UUID, Week> e : packages.entrySet()) {
            NbtCompound c = new NbtCompound();
            c.putLong("w", e.getValue().week());
            c.putInt("n", e.getValue().count());
            p.put(e.getKey().toString(), c);
        }
        nbt.put("packages", p);
        return nbt;
    }
}

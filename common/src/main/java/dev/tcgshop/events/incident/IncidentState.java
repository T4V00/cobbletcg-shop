package dev.tcgshop.events.incident;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The incident module's own (world) state: how many reward packages each owner has opened this in-game
 * week (weekly cap per OWNER, across all their shops and worlds; critique C1/F #88).
 */
public final class IncidentState extends SavedData {
    private static final Factory<IncidentState> TYPE = new Factory<>(IncidentState::new, IncidentState::fromNbt, null);

    private record Week(long week, int count) {}

    private final Map<UUID, Week> packages = new HashMap<>();

    public static IncidentState get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE, "tcgshop_incidents");
    }

    /** Reward packages opened by that owner in the week (day / 7). */
    public int packagesThisWeek(UUID owner, long day) {
        Week w = packages.get(owner);
        return w == null || w.week() != day / 7 ? 0 : w.count();
    }

    public void addPackage(UUID owner, long day) {
        packages.put(owner, new Week(day / 7, packagesThisWeek(owner, day) + 1));
        setDirty();
    }

    private static IncidentState fromNbt(CompoundTag nbt, HolderLookup.Provider lookup) {
        IncidentState s = new IncidentState();
        CompoundTag p = nbt.getCompound("packages");
        for (String k : p.getAllKeys()) {
            try {
                CompoundTag c = p.getCompound(k);
                s.packages.put(UUID.fromString(k), new Week(c.getLong("w"), c.getInt("n")));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return s;
    }

    @Override
    public CompoundTag save(CompoundTag nbt, HolderLookup.Provider lookup) {
        CompoundTag p = new CompoundTag();
        for (Map.Entry<UUID, Week> e : packages.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putLong("w", e.getValue().week());
            c.putInt("n", e.getValue().count());
            p.put(e.getKey().toString(), c);
        }
        nbt.put("packages", p);
        return nbt;
    }
}

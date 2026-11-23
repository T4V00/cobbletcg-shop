package dev.tcgshop.shop;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 1.0 beta 3: world state for shop security and the unique customers (saved as "tcgshop_security" in the overworld).
 *
 *  · Per shop (zone id): guard upgrade levels (armor, weapon, training, detection; 0-3 each, see GuardUpgrades), the last
 *    in-game day of an armed robbery / heist (cooldowns) and how many of them were foiled or got away.
 *  · Per unique customer (FixedCustomers id 0-9) and player: how many times they visited that player's shops and the last
 *    day, so they greet you by name when they come back and never show up twice on the same day.
 *
 * Kept apart from ShopZones so older saves and the zone format are untouched.
 */
public final class SecurityLedger extends PersistentState {
    private static final Type<SecurityLedger> TYPE = new Type<>(SecurityLedger::new, SecurityLedger::fromNbt, null);

    /** Security record of one shop. */
    public static final class ZoneSec {
        /** Guard upgrade levels, indexed by GuardUpgrades.Stat ordinal. */
        public final int[] guard = new int[4];
        /** Last in-game day of an armed robbery / a heist at this shop (-1000 = never). */
        public long lastRobberyDay = -1000, lastHeistDay = -1000;
        public int robberiesFoiled, robberiesLost, heistsFoiled, heistsLost;
    }

    /** What a unique customer remembers about one player. */
    public static final class Memory {
        public int visits;
        public long lastDay = -1000;
    }

    private final Map<UUID, ZoneSec> zones = new HashMap<>();
    /** fixed customer id -> player uuid -> memory. */
    private final Map<Integer, Map<UUID, Memory>> fixed = new HashMap<>();

    public static SecurityLedger get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE, "tcgshop_security");
    }

    /** That shop's security record (created on demand). */
    public static ZoneSec zone(MinecraftServer server, UUID zoneId) {
        SecurityLedger l = get(server);
        return l.zones.computeIfAbsent(zoneId, k -> new ZoneSec());
    }

    /** What unique customer `id` remembers of `player` (created on demand; call dirty() after changing it). */
    public static Memory memory(MinecraftServer server, int id, UUID player) {
        SecurityLedger l = get(server);
        return l.fixed.computeIfAbsent(id, k -> new HashMap<>()).computeIfAbsent(player, k -> new Memory());
    }

    /** Has `player` ever been visited by unique customer `id`? (does not create the record) */
    public static boolean met(MinecraftServer server, int id, UUID player) {
        Map<UUID, Memory> m = get(server).fixed.get(id);
        Memory mem = m == null ? null : m.get(player);
        return mem != null && mem.visits > 0;
    }

    /** Last day unique customer `id` visited `player` (-1000 = never; does not create the record). */
    public static long lastDay(MinecraftServer server, int id, UUID player) {
        Map<UUID, Memory> m = get(server).fixed.get(id);
        Memory mem = m == null ? null : m.get(player);
        return mem == null ? -1000 : mem.lastDay;
    }

    public static void dirty(MinecraftServer server) {
        get(server).markDirty();
    }

    // ------------------------------------------------------------------ NBT

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        NbtList zl = new NbtList();
        for (Map.Entry<UUID, ZoneSec> e : zones.entrySet()) {
            ZoneSec s = e.getValue();
            NbtCompound c = new NbtCompound();
            c.putUuid("Zone", e.getKey());
            c.putIntArray("Guard", s.guard);
            c.putLong("LastRobbery", s.lastRobberyDay);
            c.putLong("LastHeist", s.lastHeistDay);
            c.putIntArray("Stats", new int[]{s.robberiesFoiled, s.robberiesLost, s.heistsFoiled, s.heistsLost});
            zl.add(c);
        }
        nbt.put("Zones", zl);
        NbtList fl = new NbtList();
        for (Map.Entry<Integer, Map<UUID, Memory>> e : fixed.entrySet()) {
            for (Map.Entry<UUID, Memory> m : e.getValue().entrySet()) {
                NbtCompound c = new NbtCompound();
                c.putInt("Id", e.getKey());
                c.putUuid("Player", m.getKey());
                c.putInt("Visits", m.getValue().visits);
                c.putLong("LastDay", m.getValue().lastDay);
                fl.add(c);
            }
        }
        nbt.put("Fixed", fl);
        return nbt;
    }

    private static SecurityLedger fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        SecurityLedger l = new SecurityLedger();
        NbtList zl = nbt.getList("Zones", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < zl.size(); i++) {
            NbtCompound c = zl.getCompound(i);
            if (!c.containsUuid("Zone")) continue;
            ZoneSec s = new ZoneSec();
            int[] g = c.getIntArray("Guard");
            for (int k = 0; k < Math.min(g.length, s.guard.length); k++) s.guard[k] = Math.max(0, Math.min(GuardUpgrades.MAX_LEVEL, g[k]));
            if (c.contains("LastRobbery")) s.lastRobberyDay = c.getLong("LastRobbery");
            if (c.contains("LastHeist")) s.lastHeistDay = c.getLong("LastHeist");
            int[] st = c.getIntArray("Stats");
            if (st.length >= 4) {
                s.robberiesFoiled = st[0];
                s.robberiesLost = st[1];
                s.heistsFoiled = st[2];
                s.heistsLost = st[3];
            }
            l.zones.put(c.getUuid("Zone"), s);
        }
        NbtList fl = nbt.getList("Fixed", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < fl.size(); i++) {
            NbtCompound c = fl.getCompound(i);
            if (!c.containsUuid("Player")) continue;
            Memory m = new Memory();
            m.visits = c.getInt("Visits");
            m.lastDay = c.getLong("LastDay");
            l.fixed.computeIfAbsent(c.getInt("Id"), k -> new HashMap<>()).put(c.getUuid("Player"), m);
        }
        return l;
    }
}

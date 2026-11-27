package dev.tcgshop.shop;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
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
public final class SecurityLedger extends SavedData {
    private static final Factory<SecurityLedger> TYPE = new Factory<>(SecurityLedger::new, SecurityLedger::fromNbt, null);

    /** Security record of one shop. */
    public static final class ZoneSec {
        /** Guard upgrade levels, indexed by GuardUpgrades.Stat ordinal. */
        public final int[] guard = new int[4];
        /** 1.1: cashier upgrade levels, indexed by CashierUpgrades.Stat ordinal (shared by both registers). */
        public final int[] cashier = new int[4];
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
        return server.overworld().getDataStorage().computeIfAbsent(TYPE, "tcgshop_security");
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
        get(server).setDirty();
    }

    // ------------------------------------------------------------------ NBT

    @Override
    public CompoundTag save(CompoundTag nbt, HolderLookup.Provider lookup) {
        ListTag zl = new ListTag();
        for (Map.Entry<UUID, ZoneSec> e : zones.entrySet()) {
            ZoneSec s = e.getValue();
            CompoundTag c = new CompoundTag();
            c.putUUID("Zone", e.getKey());
            c.putIntArray("Guard", s.guard);
            c.putIntArray("Cashier", s.cashier);
            c.putLong("LastRobbery", s.lastRobberyDay);
            c.putLong("LastHeist", s.lastHeistDay);
            c.putIntArray("Stats", new int[]{s.robberiesFoiled, s.robberiesLost, s.heistsFoiled, s.heistsLost});
            zl.add(c);
        }
        nbt.put("Zones", zl);
        ListTag fl = new ListTag();
        for (Map.Entry<Integer, Map<UUID, Memory>> e : fixed.entrySet()) {
            for (Map.Entry<UUID, Memory> m : e.getValue().entrySet()) {
                CompoundTag c = new CompoundTag();
                c.putInt("Id", e.getKey());
                c.putUUID("Player", m.getKey());
                c.putInt("Visits", m.getValue().visits);
                c.putLong("LastDay", m.getValue().lastDay);
                fl.add(c);
            }
        }
        nbt.put("Fixed", fl);
        return nbt;
    }

    private static SecurityLedger fromNbt(CompoundTag nbt, HolderLookup.Provider lookup) {
        SecurityLedger l = new SecurityLedger();
        ListTag zl = nbt.getList("Zones", Tag.TAG_COMPOUND);
        for (int i = 0; i < zl.size(); i++) {
            CompoundTag c = zl.getCompound(i);
            if (!c.hasUUID("Zone")) continue;
            ZoneSec s = new ZoneSec();
            int[] g = c.getIntArray("Guard");
            for (int k = 0; k < Math.min(g.length, s.guard.length); k++) s.guard[k] = Math.max(0, Math.min(GuardUpgrades.MAX_LEVEL, g[k]));
            int[] cl = c.getIntArray("Cashier"); // 1.1: absent in older saves = no cashier upgrades
            for (int k = 0; k < Math.min(cl.length, s.cashier.length); k++)
                s.cashier[k] = Math.max(0, Math.min(CashierUpgrades.maxLevel(CashierUpgrades.Stat.values()[k]), cl[k]));
            if (c.contains("LastRobbery")) s.lastRobberyDay = c.getLong("LastRobbery");
            if (c.contains("LastHeist")) s.lastHeistDay = c.getLong("LastHeist");
            int[] st = c.getIntArray("Stats");
            if (st.length >= 4) {
                s.robberiesFoiled = st[0];
                s.robberiesLost = st[1];
                s.heistsFoiled = st[2];
                s.heistsLost = st[3];
            }
            l.zones.put(c.getUUID("Zone"), s);
        }
        ListTag fl = nbt.getList("Fixed", Tag.TAG_COMPOUND);
        for (int i = 0; i < fl.size(); i++) {
            CompoundTag c = fl.getCompound(i);
            if (!c.hasUUID("Player")) continue;
            Memory m = new Memory();
            m.visits = c.getInt("Visits");
            m.lastDay = c.getLong("LastDay");
            l.fixed.computeIfAbsent(c.getInt("Id"), k -> new HashMap<>()).put(c.getUUID("Player"), m);
        }
        return l;
    }
}

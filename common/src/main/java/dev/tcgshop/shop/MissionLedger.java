package dev.tcgshop.shop;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 1.0 shop missions beyond the original 12: per zone, the full set of completed missions (a BitSet, so it can grow past
 * 64 missions), the ones ever completed (for the one-time notice) and the counters some missions need (best day,
 * resolved events, peak customers inside...). Kept in its own overworld PersistentState "tcgshop_missions", keyed by
 * zone id, so the zone NBT (ShopZones) is not touched. The first 12 missions still live in Zone.missions / missionsEver
 * (same bits as 0.9), so old worlds keep working and are migrated on the first evaluation.
 *
 * The last loaded instance is kept in {@link #active} because ShopMissions.completedCount(zone) / capacity have no
 * server at hand. On the client it is never used (the screens read synced NBT).
 */
public final class MissionLedger extends SavedData {
    private static final Factory<MissionLedger> TYPE = new Factory<>(MissionLedger::new, MissionLedger::fromNbt, null);

    /** Mission data of one zone. */
    public static final class Data {
        @Nullable public UUID owner;
        public final BitSet done = new BitSet(), ever = new BitSet();
        /** False until the first 1.0 evaluation (then everything already met is marked as "ever" without notices). */
        public boolean initialized;
        /** Cached number of completed missions (done.cardinality()). */
        public int completed;
        // ---- daily sales / revenue (snapshot at the start of the day: no hook in the sale path needed)
        public long day = -1;
        public int salesAtDay;
        public long revenueAtDay;
        public int bestDaySales;
        public long bestDayRevenue;
        // ---- counters
        public int eventsResolved, peakInside;
        /** Event history entries already counted ("day|id"), bounded. */
        public final List<String> seenEvents = new ArrayList<>();

        public int salesToday(ShopZones.Zone z) { return Math.max(0, z.sales - salesAtDay); }

        public long revenueToday(ShopZones.Zone z) { return Math.max(0, z.revenue - revenueAtDay); }

        /** Starts a new day if needed and updates the best day. */
        public void rollDay(ShopZones.Zone z, long today) {
            if (day < 0 || today < day) {
                day = today;
                salesAtDay = z.sales;
                revenueAtDay = z.revenue;
                return;
            }
            bestDaySales = Math.max(bestDaySales, salesToday(z));
            bestDayRevenue = Math.max(bestDayRevenue, revenueToday(z));
            if (today > day) {
                day = today;
                salesAtDay = z.sales;
                revenueAtDay = z.revenue;
            }
        }
    }

    private final Map<UUID, Data> zones = new HashMap<>();

    @Nullable
    private static MissionLedger active;

    public static MissionLedger get(MinecraftServer server) {
        MissionLedger l = server.overworld().getDataStorage().computeIfAbsent(TYPE, "tcgshop_missions");
        active = l;
        return l;
    }

    static void clearActive() { active = null; }

    /** Data of that zone, created if missing (needs the ledger loaded: ShopLife.recompute loads it). */
    public static Data of(MinecraftServer server, ShopZones.Zone z) {
        MissionLedger l = get(server);
        Data d = l.zones.computeIfAbsent(z.id, u -> new Data());
        if (d.owner == null || !d.owner.equals(z.owner)) { d.owner = z.owner; l.setDirty(); }
        return d;
    }

    /** Read-only view without a server: null if the ledger was not loaded yet or the zone has no data. */
    @Nullable
    public static Data peek(ShopZones.Zone z) {
        MissionLedger l = active;
        return l == null ? null : l.zones.get(z.id);
    }

    /** All zone data of one owner (for owner-wide luck such as the tip chance). */
    public static List<Data> ofOwner(UUID owner) {
        List<Data> out = new ArrayList<>();
        MissionLedger l = active;
        if (l == null || owner == null) return out;
        for (Data d : l.zones.values()) if (owner.equals(d.owner)) out.add(d);
        return out;
    }

    public static void dirty() {
        MissionLedger l = active;
        if (l != null) l.setDirty();
    }

    /** Forget a removed zone (optional cleanup). */
    public static void forget(MinecraftServer server, UUID zoneId) {
        MissionLedger l = get(server);
        if (l.zones.remove(zoneId) != null) l.setDirty();
    }

    // ------------------------------------------------------------------ NBT

    private static BitSet bits(long[] a) { return BitSet.valueOf(a == null ? new long[0] : a); }

    private static MissionLedger fromNbt(CompoundTag nbt, HolderLookup.Provider lookup) {
        MissionLedger s = new MissionLedger();
        CompoundTag o = nbt.getCompound("zones");
        for (String k : o.getAllKeys()) {
            try {
                CompoundTag c = o.getCompound(k);
                Data d = new Data();
                if (c.hasUUID("owner")) d.owner = c.getUUID("owner");
                d.done.or(bits(c.getLongArray("done")));
                d.ever.or(bits(c.getLongArray("ever")));
                d.initialized = c.getBoolean("init");
                d.completed = d.done.cardinality();
                d.day = c.contains("day") ? c.getLong("day") : -1;
                d.salesAtDay = c.getInt("sales_at_day");
                d.revenueAtDay = c.getLong("revenue_at_day");
                d.bestDaySales = c.getInt("best_day_sales");
                d.bestDayRevenue = c.getLong("best_day_revenue");
                d.eventsResolved = c.getInt("events_resolved");
                d.peakInside = c.getInt("peak_inside");
                ListTag se = c.getList("seen_events", 8);
                for (int i = 0; i < se.size(); i++) d.seenEvents.add(se.getString(i));
                s.zones.put(UUID.fromString(k), d);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return s;
    }

    @Override
    public CompoundTag save(CompoundTag nbt, HolderLookup.Provider lookup) {
        CompoundTag o = new CompoundTag();
        for (Map.Entry<UUID, Data> me : zones.entrySet()) {
            Data d = me.getValue();
            CompoundTag c = new CompoundTag();
            if (d.owner != null) c.putUUID("owner", d.owner);
            c.putLongArray("done", d.done.toLongArray());
            c.putLongArray("ever", d.ever.toLongArray());
            c.putBoolean("init", d.initialized);
            c.putLong("day", d.day);
            c.putInt("sales_at_day", d.salesAtDay);
            c.putLong("revenue_at_day", d.revenueAtDay);
            c.putInt("best_day_sales", d.bestDaySales);
            c.putLong("best_day_revenue", d.bestDayRevenue);
            c.putInt("events_resolved", d.eventsResolved);
            c.putInt("peak_inside", d.peakInside);
            ListTag se = new ListTag();
            for (String s : d.seenEvents) se.add(StringTag.valueOf(s));
            c.put("seen_events", se);
            o.put(me.getKey().toString(), c);
        }
        nbt.put("zones", o);
        return nbt;
    }
}

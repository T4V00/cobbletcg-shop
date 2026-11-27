package dev.tcgshop.events;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * Event state of ONE shop (the zone's ext.events sub-compound). Old worlds: all empty / -1.
 *  · active   running events
 *  · log      memory for reviews (2 days)
 *  · hist     summary of the last 7 days for the phone
 *  · created  day the shop was first seen (grace period)
 *  · rev      revenue per day (8-day ring, rev[0] = today) for the reward money caps
 */
public final class ZoneEvents {
    public final List<EventInstance> active = new ArrayList<>();
    public final List<IncidentRecord> log = new ArrayList<>();
    public final List<Hist> hist = new ArrayList<>();
    public long createdDay = -1;
    public final long[] rev = new long[8];
    public long revDay = -1;

    /** History day: id, category and outcome ("resolved", "expired", "done", "ended"). */
    public record Hist(long day, String id, String category, String outcome) {}

    // ---- not saved: filled by ShopLife.recompute (EventManager.onRecompute)
    public transient boolean hasRegister;
    public transient List<BlockPos> doors = List.of();
    public transient int priceFactor;
    public transient long lastRecompute = -1;

    public boolean hasActive() { return !active.isEmpty(); }

    public EventInstance byUid(long uid) {
        for (EventInstance e : active) if (e.uid == uid) return e;
        return null;
    }

    public IncidentRecord record(long uid) {
        for (IncidentRecord r : log) if (r.uid == uid) return r;
        return null;
    }

    /** Adds the day's revenue (8-day ring). */
    public void addRevenue(long day, long amount) {
        roll(day);
        rev[0] += Math.max(0, amount);
    }

    private void roll(long day) {
        if (revDay < 0) { revDay = day; return; }
        if (day <= revDay) return;
        int shift = (int) Math.min(rev.length, day - revDay);
        for (int i = rev.length - 1; i >= 0; i--) rev[i] = i - shift >= 0 ? rev[i - shift] : 0;
        revDay = day;
    }

    /** Average revenue of the last n COMPLETE days (excluding today). */
    public double avgDaily(long today, int n) {
        roll(today);
        long s = 0;
        int k = Math.max(1, Math.min(rev.length - 1, n));
        for (int i = 1; i <= k; i++) s += rev[i];
        return s / (double) k;
    }

    public void addHist(long day, String id, String category, String outcome) {
        hist.add(0, new Hist(day, id, category, outcome));
        hist.removeIf(h -> day - h.day() > 7);
        while (hist.size() > 24) hist.remove(hist.size() - 1);
    }

    /** Removes from the log whatever ended more than 2 days ago. */
    public void trimLog(long now) {
        log.removeIf(r -> r.end >= 0 && now - r.end > 48_000 || now < r.start - 24_000);
        while (log.size() > 32) log.remove(0);
    }

    // ------------------------------------------------------------------ NBT

    public CompoundTag write() {
        CompoundTag c = new CompoundTag();
        ListTag al = new ListTag();
        for (EventInstance e : active) al.add(e.toNbt());
        c.put("active", al);
        ListTag ll = new ListTag();
        for (IncidentRecord r : log) ll.add(r.toNbt());
        c.put("log", ll);
        ListTag hl = new ListTag();
        for (Hist h : hist) {
            CompoundTag x = new CompoundTag();
            x.putLong("d", h.day());
            x.putString("i", h.id());
            x.putString("c", h.category());
            x.putString("o", h.outcome());
            hl.add(x);
        }
        c.put("hist", hl);
        c.putLong("created", createdDay);
        c.putLongArray("rev", rev.clone());
        c.putLong("rev_day", revDay);
        return c;
    }

    public static ZoneEvents read(CompoundTag c) {
        ZoneEvents z = new ZoneEvents();
        if (c == null || c.isEmpty()) return z;
        ListTag al = c.getList("active", Tag.TAG_COMPOUND);
        for (int i = 0; i < al.size(); i++) z.active.add(EventInstance.fromNbt(al.getCompound(i)));
        ListTag ll = c.getList("log", Tag.TAG_COMPOUND);
        for (int i = 0; i < ll.size(); i++) z.log.add(IncidentRecord.fromNbt(ll.getCompound(i)));
        ListTag hl = c.getList("hist", Tag.TAG_COMPOUND);
        for (int i = 0; i < hl.size(); i++) {
            CompoundTag x = hl.getCompound(i);
            z.hist.add(new Hist(x.getLong("d"), x.getString("i"), x.getString("c"), x.getString("o")));
        }
        z.createdDay = c.contains("created") ? c.getLong("created") : -1;
        long[] r = c.getLongArray("rev");
        System.arraycopy(r, 0, z.rev, 0, Math.min(r.length, z.rev.length));
        z.revDay = c.contains("rev_day") ? c.getLong("rev_day") : -1;
        return z;
    }
}

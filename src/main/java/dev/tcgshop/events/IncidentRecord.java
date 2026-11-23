package dev.tcgshop.events;

import net.minecraft.nbt.NbtCompound;

/**
 * What the shop "remembers" about an event for reviews (ext.events.log, kept for 2 days):
 * when it started, when it was fixed (-1 = not fixed), when it ended, the review tag, whether it is positive,
 * the mood it adds/subtracts, the chance of mentioning it and how many customers arrived while it was still unfixed.
 */
public final class IncidentRecord {
    public final long uid;
    public final String id, tag;
    public final long start;
    public long resolvedAt = -1, end = -1;
    public final boolean positive, negative;
    public final int mood;
    public final double chance, fixedChance;
    public int customersDuring;

    public IncidentRecord(long uid, String id, String tag, long start, boolean positive, boolean negative, int mood, double chance, double fixedChance) {
        this.uid = uid;
        this.id = id;
        this.tag = tag;
        this.start = start;
        this.positive = positive;
        this.negative = negative;
        this.mood = mood;
        this.chance = chance;
        this.fixedChance = fixedChance;
    }

    /** End of the "unfixed" stretch (or the end of the event, or "still ongoing" = Long.MAX_VALUE). */
    public long activeUntil() {
        if (resolvedAt >= 0) return resolvedAt;
        return end >= 0 ? end : Long.MAX_VALUE;
    }

    public boolean activeAt(long t) { return t >= start && t < activeUntil(); }

    public NbtCompound toNbt() {
        NbtCompound c = new NbtCompound();
        c.putLong("uid", uid);
        c.putString("id", id);
        c.putString("tag", tag);
        c.putLong("start", start);
        c.putLong("res", resolvedAt);
        c.putLong("end", end);
        c.putBoolean("pos", positive);
        c.putBoolean("neg", negative);
        c.putInt("mood", mood);
        c.putDouble("ch", chance);
        c.putDouble("fch", fixedChance);
        c.putInt("cust", customersDuring);
        return c;
    }

    public static IncidentRecord fromNbt(NbtCompound c) {
        IncidentRecord r = new IncidentRecord(c.getLong("uid"), c.getString("id"), c.getString("tag"), c.getLong("start"),
                c.getBoolean("pos"), c.getBoolean("neg"), c.getInt("mood"), c.getDouble("ch"), c.getDouble("fch"));
        r.resolvedAt = c.contains("res") ? c.getLong("res") : -1;
        r.end = c.contains("end") ? c.getLong("end") : -1;
        r.customersDuring = c.getInt("cust");
        return r;
    }
}

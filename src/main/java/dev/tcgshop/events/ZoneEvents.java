package dev.tcgshop.events;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Estado de eventos de UNA tienda (sub-compuesto ext.events de la zona). Mundos antiguos: todo vacío / -1.
 *  · active   eventos en marcha
 *  · log      memoria para reseñas (2 días)
 *  · hist     resumen de los últimos 7 días para el teléfono
 *  · created  día en que se vio la tienda por primera vez (periodo de gracia)
 *  · rev      ingresos por día (anillo de 8 días, rev[0] = hoy) para los topes de dinero de los premios
 */
public final class ZoneEvents {
    public final List<EventInstance> active = new ArrayList<>();
    public final List<IncidentRecord> log = new ArrayList<>();
    public final List<Hist> hist = new ArrayList<>();
    public long createdDay = -1;
    public final long[] rev = new long[8];
    public long revDay = -1;

    /** Día del historial: id, categoría y resultado ("resolved", "expired", "done", "ended"). */
    public record Hist(long day, String id, String category, String outcome) {}

    // ---- no se guarda: lo rellena ShopLife.recompute (EventManager.onRecompute)
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

    /** Suma ingresos del día (anillo de 8 días). */
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

    /** Media de ingresos de los últimos n días COMPLETOS (sin hoy). */
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

    /** Quita del log lo que terminó hace más de 2 días. */
    public void trimLog(long now) {
        log.removeIf(r -> r.end >= 0 && now - r.end > 48_000 || now < r.start - 24_000);
        while (log.size() > 32) log.remove(0);
    }

    // ------------------------------------------------------------------ NBT

    public NbtCompound write() {
        NbtCompound c = new NbtCompound();
        NbtList al = new NbtList();
        for (EventInstance e : active) al.add(e.toNbt());
        c.put("active", al);
        NbtList ll = new NbtList();
        for (IncidentRecord r : log) ll.add(r.toNbt());
        c.put("log", ll);
        NbtList hl = new NbtList();
        for (Hist h : hist) {
            NbtCompound x = new NbtCompound();
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

    public static ZoneEvents read(NbtCompound c) {
        ZoneEvents z = new ZoneEvents();
        if (c == null || c.isEmpty()) return z;
        NbtList al = c.getList("active", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < al.size(); i++) z.active.add(EventInstance.fromNbt(al.getCompound(i)));
        NbtList ll = c.getList("log", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < ll.size(); i++) z.log.add(IncidentRecord.fromNbt(ll.getCompound(i)));
        NbtList hl = c.getList("hist", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < hl.size(); i++) {
            NbtCompound x = hl.getCompound(i);
            z.hist.add(new Hist(x.getLong("d"), x.getString("i"), x.getString("c"), x.getString("o")));
        }
        z.createdDay = c.contains("created") ? c.getLong("created") : -1;
        long[] r = c.getLongArray("rev");
        System.arraycopy(r, 0, z.rev, 0, Math.min(r.length, z.rev.length));
        z.revDay = c.contains("rev_day") ? c.getLong("rev_day") : -1;
        return z;
    }
}

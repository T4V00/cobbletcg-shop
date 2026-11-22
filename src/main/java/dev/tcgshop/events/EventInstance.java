package dev.tcgshop.events;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Un evento en marcha en una tienda. Se guarda en la zona (ext.events.active). Su fin es un tiempo ABSOLUTO del mundo
 * (world.getTime()) que nunca se alarga por estar la zona descargada.
 *
 * Los "bloqueos" (disabledSlots, closedDoors, registerDown, lightingOverride) los ponen los módulos con los métodos de
 * abajo; el núcleo los junta en {@link Mods} para los clientes, la caja, las puertas y la luz. Al terminar el evento
 * desaparecen solos (son parte de la instancia).
 */
public final class EventInstance {
    public final long uid;
    public final String defId;
    public final String variant;
    public final UUID zoneId;
    public final UUID owner;
    public final long start, day;
    public long end;
    public long resolvedAt = -1;
    public String resolvedHow = "";
    /** Fase libre para los efectos (p. ej. el visitante ya llegó). */
    public int stage;
    /** Datos por efecto: data.getCompound("e" + índice) — ver EventContext.data(). Y datos comunes ("o_*" = resultado). */
    public NbtCompound data = new NbtCompound();
    /** Visitantes de este evento todavía en camino o dentro (uuid de entidad). */
    public final List<UUID> visitors = new ArrayList<>();
    public int visitorsDone;
    /** Tag de reseña que sustituye al de la definición (lo puede poner un módulo/variante). Vacío = el de la definición. */
    public String reviewTag = "";
    // ---- bloqueos del mundo (módulos)
    final Set<Long> disabledDisplays = new HashSet<>();
    final Set<String> disabledSlots = new HashSet<>();
    final Set<Long> closedDoors = new HashSet<>();
    boolean registerDown;
    int lightingOverride = -1;

    public EventInstance(long uid, String defId, String variant, UUID zoneId, UUID owner, long start, long end, long day) {
        this.uid = uid;
        this.defId = defId;
        this.variant = variant == null ? "" : variant;
        this.zoneId = zoneId;
        this.owner = owner;
        this.start = start;
        this.end = end;
        this.day = day;
    }

    public boolean resolved() { return resolvedAt >= 0; }

    // ------------------------------------------------------------------ API de bloqueos (llamar y luego EventManager.invalidate(zone))

    /** Expositor entero inutilizable (los clientes no lo miran ni compran de él). */
    public void disableDisplay(BlockPos pos) { disabledDisplays.add(pos.asLong()); }

    /** Una casilla concreta inutilizable (p. ej. "producto tirado por el suelo"): el objeto NO se mueve del expositor. */
    public void disableSlot(BlockPos pos, int slot) { disabledSlots.add(pos.asLong() + ":" + slot); }

    public void enableDisplay(BlockPos pos) {
        disabledDisplays.remove(pos.asLong());
        disabledSlots.removeIf(s -> s.startsWith(pos.asLong() + ":"));
    }

    /** Puerta bloqueada: no entran clientes por ella (si todas lo están, esperan fuera). pos = mitad inferior. */
    public void closeDoor(BlockPos door) { closedDoors.add(door.asLong()); }

    public void openDoor(BlockPos door) { closedDoors.remove(door.asLong()); }

    /** Caja averiada: el CAJERO no puede cobrar sobres/cajas (el dueño junto a la caja sí). */
    public void setRegisterDown(boolean down) { registerDown = down; }

    /** Luz que "ven" los clientes en el recálculo (0-15), -1 = la real. */
    public void setLightingOverride(int level) { lightingOverride = Math.max(-1, Math.min(15, level)); }

    public void clearLocks() {
        disabledDisplays.clear();
        disabledSlots.clear();
        closedDoors.clear();
        registerDown = false;
        lightingOverride = -1;
    }

    // ------------------------------------------------------------------ NBT

    public NbtCompound toNbt() {
        NbtCompound c = new NbtCompound();
        c.putLong("uid", uid);
        c.putString("id", defId);
        c.putString("var", variant);
        c.putUuid("zone", zoneId);
        c.putUuid("owner", owner);
        c.putLong("start", start);
        c.putLong("end", end);
        c.putLong("day", day);
        c.putLong("res", resolvedAt);
        c.putString("how", resolvedHow);
        c.putInt("stage", stage);
        c.put("data", data.copy());
        NbtList vl = new NbtList();
        for (UUID u : visitors) vl.add(NbtString.of(u.toString()));
        c.put("vis", vl);
        c.putInt("vdone", visitorsDone);
        c.putString("rtag", reviewTag);
        c.putLongArray("ddisp", disabledDisplays.stream().mapToLong(Long::longValue).toArray());
        NbtList sl = new NbtList();
        for (String s : disabledSlots) sl.add(NbtString.of(s));
        c.put("dslot", sl);
        c.putLongArray("doors", closedDoors.stream().mapToLong(Long::longValue).toArray());
        c.putBoolean("regdown", registerDown);
        c.putInt("light", lightingOverride);
        return c;
    }

    public static EventInstance fromNbt(NbtCompound c) {
        EventInstance e = new EventInstance(c.getLong("uid"), c.getString("id"), c.getString("var"),
                c.containsUuid("zone") ? c.getUuid("zone") : new UUID(0, 0), c.containsUuid("owner") ? c.getUuid("owner") : new UUID(0, 0),
                c.getLong("start"), c.getLong("end"), c.getLong("day"));
        e.resolvedAt = c.contains("res") ? c.getLong("res") : -1;
        e.resolvedHow = c.getString("how");
        e.stage = c.getInt("stage");
        e.data = c.getCompound("data").copy();
        NbtList vl = c.getList("vis", NbtElement.STRING_TYPE);
        for (int i = 0; i < vl.size(); i++) {
            try { e.visitors.add(UUID.fromString(vl.getString(i))); } catch (IllegalArgumentException ignored) {}
        }
        e.visitorsDone = c.getInt("vdone");
        e.reviewTag = c.getString("rtag");
        for (long l : c.getLongArray("ddisp")) e.disabledDisplays.add(l);
        NbtList sl = c.getList("dslot", NbtElement.STRING_TYPE);
        for (int i = 0; i < sl.size(); i++) e.disabledSlots.add(sl.getString(i));
        for (long l : c.getLongArray("doors")) e.closedDoors.add(l);
        e.registerDown = c.getBoolean("regdown");
        e.lightingOverride = c.contains("light") ? c.getInt("light") : -1;
        return e;
    }
}

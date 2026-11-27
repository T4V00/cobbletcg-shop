package dev.tcgshop.events;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

/**
 * An event running in a shop. Saved in the zone (ext.events.active). Its end is an ABSOLUTE world time
 * (world.getTime()) that is never extended because the zone is unloaded.
 *
 * The "locks" (disabledSlots, closedDoors, registerDown, lightingOverride) are set by modules with the methods
 * below; the core merges them into {@link Mods} for customers, the register, doors and light. When the event ends
 * they disappear on their own (they are part of the instance).
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
    /** Free-form stage for the effects (e.g. the visitor has already arrived). */
    public int stage;
    /** Per-effect data: data.getCompound("e" + index) — see EventContext.data(). Plus shared data ("o_*" = outcome). */
    public CompoundTag data = new CompoundTag();
    /** Visitors of this event still on their way or inside (entity uuid). */
    public final List<UUID> visitors = new ArrayList<>();
    public int visitorsDone;
    /** Review tag that replaces the definition's (a module/variant may set it). Empty = the definition's. */
    public String reviewTag = "";
    // ---- world locks (modules)
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

    // ------------------------------------------------------------------ Locks API (call, then EventManager.invalidate(zone))

    /** Whole display unusable (customers neither look at it nor buy from it). */
    public void disableDisplay(BlockPos pos) { disabledDisplays.add(pos.asLong()); }

    /** A specific slot unusable (e.g. "product knocked onto the floor"): the item is NOT moved out of the display. */
    public void disableSlot(BlockPos pos, int slot) { disabledSlots.add(pos.asLong() + ":" + slot); }

    public void enableDisplay(BlockPos pos) {
        disabledDisplays.remove(pos.asLong());
        disabledSlots.removeIf(s -> s.startsWith(pos.asLong() + ":"));
    }

    /** Blocked door: customers do not come in through it (if all are blocked, they wait outside). pos = lower half. */
    public void closeDoor(BlockPos door) { closedDoors.add(door.asLong()); }

    public void openDoor(BlockPos door) { closedDoors.remove(door.asLong()); }

    /** Broken register: the CASHIER cannot ring up packs/boxes (the owner at the register can). */
    public void setRegisterDown(boolean down) { registerDown = down; }

    /** Light that customers "see" during the recompute (0-15), -1 = the real one. */
    public void setLightingOverride(int level) { lightingOverride = Math.max(-1, Math.min(15, level)); }

    public void clearLocks() {
        disabledDisplays.clear();
        disabledSlots.clear();
        closedDoors.clear();
        registerDown = false;
        lightingOverride = -1;
    }

    // ------------------------------------------------------------------ NBT

    public CompoundTag toNbt() {
        CompoundTag c = new CompoundTag();
        c.putLong("uid", uid);
        c.putString("id", defId);
        c.putString("var", variant);
        c.putUUID("zone", zoneId);
        c.putUUID("owner", owner);
        c.putLong("start", start);
        c.putLong("end", end);
        c.putLong("day", day);
        c.putLong("res", resolvedAt);
        c.putString("how", resolvedHow);
        c.putInt("stage", stage);
        c.put("data", data.copy());
        ListTag vl = new ListTag();
        for (UUID u : visitors) vl.add(StringTag.valueOf(u.toString()));
        c.put("vis", vl);
        c.putInt("vdone", visitorsDone);
        c.putString("rtag", reviewTag);
        c.putLongArray("ddisp", disabledDisplays.stream().mapToLong(Long::longValue).toArray());
        ListTag sl = new ListTag();
        for (String s : disabledSlots) sl.add(StringTag.valueOf(s));
        c.put("dslot", sl);
        c.putLongArray("doors", closedDoors.stream().mapToLong(Long::longValue).toArray());
        c.putBoolean("regdown", registerDown);
        c.putInt("light", lightingOverride);
        return c;
    }

    public static EventInstance fromNbt(CompoundTag c) {
        EventInstance e = new EventInstance(c.getLong("uid"), c.getString("id"), c.getString("var"),
                c.hasUUID("zone") ? c.getUUID("zone") : new UUID(0, 0), c.hasUUID("owner") ? c.getUUID("owner") : new UUID(0, 0),
                c.getLong("start"), c.getLong("end"), c.getLong("day"));
        e.resolvedAt = c.contains("res") ? c.getLong("res") : -1;
        e.resolvedHow = c.getString("how");
        e.stage = c.getInt("stage");
        e.data = c.getCompound("data").copy();
        ListTag vl = c.getList("vis", Tag.TAG_STRING);
        for (int i = 0; i < vl.size(); i++) {
            try { e.visitors.add(UUID.fromString(vl.getString(i))); } catch (IllegalArgumentException ignored) {}
        }
        e.visitorsDone = c.getInt("vdone");
        e.reviewTag = c.getString("rtag");
        for (long l : c.getLongArray("ddisp")) e.disabledDisplays.add(l);
        ListTag sl = c.getList("dslot", Tag.TAG_STRING);
        for (int i = 0; i < sl.size(); i++) e.disabledSlots.add(sl.getString(i));
        for (long l : c.getLongArray("doors")) e.closedDoors.add(l);
        e.registerDown = c.getBoolean("regdown");
        e.lightingOverride = c.contains("light") ? c.getInt("light") : -1;
        return e;
    }
}

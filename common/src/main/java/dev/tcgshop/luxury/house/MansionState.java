package dev.tcgshop.luxury.house;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;

/**
 * Mansions built (or under construction) in a world: owner, origin, orientation and resident Pokémon. Used to avoid
 * building one on top of another, to resume construction interrupted by a server shutdown and to watch over the
 * residents. Saved in the world's data/tcgshop_mansions.dat.
 */
public class MansionState extends SavedData {
    /** A resident Pokémon: entity uuid, home (world position) and the radius it may wander. */
    public record Resident(UUID uuid, String species, double x, double y, double z, int radius) {
        CompoundTag write() {
            CompoundTag c = new CompoundTag();
            c.putUUID("id", uuid);
            c.putString("sp", species);
            c.putDouble("x", x);
            c.putDouble("y", y);
            c.putDouble("z", z);
            c.putInt("r", radius);
            return c;
        }

        static Resident read(CompoundTag c) {
            return new Resident(c.getUUID("id"), c.getString("sp"), c.getDouble("x"), c.getDouble("y"), c.getDouble("z"), c.getInt("r"));
        }
    }

    public static final class Record {
        public final UUID id;
        public final UUID owner;
        public String ownerName;
        /** World position of the estate's local corner (0, 0, 0) (at ground level). */
        public final BlockPos origin;
        /** Direction the builder was facing: the back of the estate goes that way (the door faces them). */
        public final Direction facing;
        public final long seed;
        public long createdDay;
        public boolean done;
        public boolean residentsSpawned;
        /** Construction attempts (so a failing job is not resumed forever). */
        public int attempts;
        public final List<Resident> residents = new ArrayList<>();

        public Record(UUID id, UUID owner, String ownerName, BlockPos origin, Direction facing, long seed) {
            this.id = id;
            this.owner = owner;
            this.ownerName = ownerName;
            this.origin = origin;
            this.facing = facing;
            this.seed = seed;
        }

        public MansionPlan plan() {
            return new MansionPlan(origin, facing);
        }

        public AABB box() {
            return plan().box();
        }

        CompoundTag write() {
            CompoundTag c = new CompoundTag();
            c.putUUID("id", id);
            c.putUUID("owner", owner);
            c.putString("owner_name", ownerName == null ? "" : ownerName);
            c.putIntArray("origin", new int[]{origin.getX(), origin.getY(), origin.getZ()});
            c.putString("facing", facing.getSerializedName());
            c.putLong("seed", seed);
            c.putLong("day", createdDay);
            c.putBoolean("done", done);
            c.putBoolean("residents_spawned", residentsSpawned);
            c.putInt("attempts", attempts);
            ListTag l = new ListTag();
            for (Resident r : residents) l.add(r.write());
            c.put("residents", l);
            return c;
        }

        @Nullable
        static Record read(CompoundTag c) {
            int[] o = c.getIntArray("origin");
            if (o.length < 3 || !c.hasUUID("id") || !c.hasUUID("owner")) return null;
            Direction f = Direction.byName(c.getString("facing"));
            if (f == null || f.getAxis().isVertical()) f = Direction.SOUTH;
            Record r = new Record(c.getUUID("id"), c.getUUID("owner"), c.getString("owner_name"), new BlockPos(o[0], o[1], o[2]), f, c.getLong("seed"));
            r.createdDay = c.getLong("day");
            r.done = c.getBoolean("done");
            r.residentsSpawned = c.getBoolean("residents_spawned");
            r.attempts = c.getInt("attempts");
            ListTag l = c.getList("residents", 10);
            for (int i = 0; i < l.size(); i++) r.residents.add(Resident.read(l.getCompound(i)));
            return r;
        }
    }

    private static final Factory<MansionState> TYPE = new Factory<>(MansionState::new, MansionState::fromNbt, null);

    private final List<Record> records = new ArrayList<>();

    public static MansionState get(ServerLevel world) {
        return world.getDataStorage().computeIfAbsent(TYPE, "tcgshop_mansions");
    }

    public List<Record> all() { return records; }

    @Nullable
    public Record overlapping(AABB box) {
        for (Record r : records) if (r.box().intersects(box)) return r;
        return null;
    }

    @Nullable
    public Record byId(UUID id) {
        for (Record r : records) if (r.id.equals(id)) return r;
        return null;
    }

    public void add(Record r) {
        records.add(r);
        setDirty();
    }

    /** Deletes the record (op command). The blocks and residents stay in the world. */
    public boolean remove(Record r) {
        boolean ok = records.remove(r);
        if (ok) setDirty();
        return ok;
    }

    private static MansionState fromNbt(CompoundTag nbt, HolderLookup.Provider lookup) {
        MansionState s = new MansionState();
        ListTag l = nbt.getList("mansions", 10);
        for (int i = 0; i < l.size(); i++) {
            Record r = Record.read(l.getCompound(i));
            if (r != null) s.records.add(r);
        }
        return s;
    }

    @Override
    public CompoundTag save(CompoundTag nbt, HolderLookup.Provider lookup) {
        ListTag l = new ListTag();
        for (Record r : records) l.add(r.write());
        nbt.put("mansions", l);
        return nbt;
    }
}

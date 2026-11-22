package dev.tcgshop.luxury.house;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.PersistentState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Mansiones construidas (o en obras) de un mundo: dueño, origen, orientación y Pokémon residentes. Sirve para no
 * construir una encima de otra, para reanudar una obra interrumpida por un cierre del servidor y para vigilar a los
 * residentes. Se guarda en data/tcgshop_mansions.dat del mundo.
 */
public class MansionState extends PersistentState {
    /** Un Pokémon residente: uuid de la entidad, casa (posición del mundo) y radio que puede alejarse. */
    public record Resident(UUID uuid, String species, double x, double y, double z, int radius) {
        NbtCompound write() {
            NbtCompound c = new NbtCompound();
            c.putUuid("id", uuid);
            c.putString("sp", species);
            c.putDouble("x", x);
            c.putDouble("y", y);
            c.putDouble("z", z);
            c.putInt("r", radius);
            return c;
        }

        static Resident read(NbtCompound c) {
            return new Resident(c.getUuid("id"), c.getString("sp"), c.getDouble("x"), c.getDouble("y"), c.getDouble("z"), c.getInt("r"));
        }
    }

    public static final class Record {
        public final UUID id;
        public final UUID owner;
        public String ownerName;
        /** Posición del mundo de la esquina local (0, 0, 0) de la finca (a ras de suelo). */
        public final BlockPos origin;
        /** Hacia dónde mira el jugador que la construyó: el fondo de la finca va en esa dirección (la puerta mira hacia él). */
        public final Direction facing;
        public final long seed;
        public long createdDay;
        public boolean done;
        public boolean residentsSpawned;
        /** Intentos de construcción (para no reanudar para siempre una obra que falla). */
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

        public Box box() {
            return plan().box();
        }

        NbtCompound write() {
            NbtCompound c = new NbtCompound();
            c.putUuid("id", id);
            c.putUuid("owner", owner);
            c.putString("owner_name", ownerName == null ? "" : ownerName);
            c.putIntArray("origin", new int[]{origin.getX(), origin.getY(), origin.getZ()});
            c.putString("facing", facing.asString());
            c.putLong("seed", seed);
            c.putLong("day", createdDay);
            c.putBoolean("done", done);
            c.putBoolean("residents_spawned", residentsSpawned);
            c.putInt("attempts", attempts);
            NbtList l = new NbtList();
            for (Resident r : residents) l.add(r.write());
            c.put("residents", l);
            return c;
        }

        @Nullable
        static Record read(NbtCompound c) {
            int[] o = c.getIntArray("origin");
            if (o.length < 3 || !c.containsUuid("id") || !c.containsUuid("owner")) return null;
            Direction f = Direction.byName(c.getString("facing"));
            if (f == null || f.getAxis().isVertical()) f = Direction.SOUTH;
            Record r = new Record(c.getUuid("id"), c.getUuid("owner"), c.getString("owner_name"), new BlockPos(o[0], o[1], o[2]), f, c.getLong("seed"));
            r.createdDay = c.getLong("day");
            r.done = c.getBoolean("done");
            r.residentsSpawned = c.getBoolean("residents_spawned");
            r.attempts = c.getInt("attempts");
            NbtList l = c.getList("residents", 10);
            for (int i = 0; i < l.size(); i++) r.residents.add(Resident.read(l.getCompound(i)));
            return r;
        }
    }

    private static final Type<MansionState> TYPE = new Type<>(MansionState::new, MansionState::fromNbt, null);

    private final List<Record> records = new ArrayList<>();

    public static MansionState get(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(TYPE, "tcgshop_mansions");
    }

    public List<Record> all() { return records; }

    @Nullable
    public Record overlapping(Box box) {
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
        markDirty();
    }

    /** Borra el registro (comando de op). Los bloques y los residentes se quedan en el mundo. */
    public boolean remove(Record r) {
        boolean ok = records.remove(r);
        if (ok) markDirty();
        return ok;
    }

    private static MansionState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        MansionState s = new MansionState();
        NbtList l = nbt.getList("mansions", 10);
        for (int i = 0; i < l.size(); i++) {
            Record r = Record.read(l.getCompound(i));
            if (r != null) s.records.add(r);
        }
        return s;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        NbtList l = new NbtList();
        for (Record r : records) l.add(r.write());
        nbt.put("mansions", l);
        return nbt;
    }
}

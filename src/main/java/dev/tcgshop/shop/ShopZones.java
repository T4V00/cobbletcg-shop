package dev.tcgshop.shop;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.PersistentState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Zonas de tienda de una dimensión. Cada bloque de tienda crea una zona cuadrada centrada en él (16x16 … 100x100)
 * que va de BELOW bloques por debajo a tier.up por encima. Guarda también las métricas de la tienda: ventas,
 * reputación, empleados, seguridad y clientes.
 */
public class ShopZones extends PersistentState {
    public static final int BELOW = 2;

    /** Reseña de un cliente al salir: estrellas (1-5) y motivo (tcgshop.review.<key>). */
    public record Review(String name, int stars, String key, long day) {
        public NbtCompound toNbt() {
            NbtCompound c = new NbtCompound();
            c.putString("n", name);
            c.putInt("s", stars);
            c.putString("k", key);
            c.putLong("d", day);
            return c;
        }

        public static Review fromNbt(NbtCompound c) {
            return new Review(c.getString("n"), c.getInt("s"), c.getString("k"), c.getLong("d"));
        }
    }

    /** Cliente habitual (salió contento): puede volver con el mismo nombre y aspecto. */
    public record Regular(String name, int skin, String title, int visits) {}

    public static final class Zone {
        public final UUID id;
        public final UUID owner;
        public String ownerName;
        public final BlockPos core;
        public final int tier;
        // dinero
        public long revenue, balance, wagesPaid;
        public int sales;
        // empleados
        public boolean cashier, guard;
        public String cashierName = "", guardName = "";
        public long lastWageDay = -1;
        // seguridad y clientes
        public int stolen, detained, recovered, customers, complaints, dealsAccepted, whales;
        // reputación (0-100) y su desglose (clave de texto -> puntos)
        public int reputation = 50;
        public final Map<String, Integer> factors = new LinkedHashMap<>();
        public int decor, detectors, displays, buzz;
        /** Mal olor de la tienda (0-100): lo traen algunos clientes y se quita con el spray desodorante. */
        public int smell;
        /** Últimas reseñas (la más reciente primero) y clientes habituales que pueden volver. */
        public final java.util.List<Review> reviews = new java.util.ArrayList<>();
        public final java.util.List<Regular> regulars = new java.util.ArrayList<>();
        /** Clientes que se fueron sin que los atendieras, rechazos del día (con reseña negativa) y mala fama por matar NPC. */
        public int unattended, rejectPenalties, infamy;
        /** Segundo puesto de caja. */
        public boolean cashier2;
        /** Buena o mala fama por cómo tratas al mendigo (-15..15, vuelve a 0 con los días). */
        public int charity;
        public String cashier2Name = "";
        /** Último estado del turno visto (para avisar al dueño al empezar/acabar). No se guarda. */
        public Boolean shiftSeen;
        public long rejectDay = -1, infamyDay = -1;
        /** Lo que han notado los clientes del entorno en el último cálculo (no se guarda): no_roof, dark, hostile... */
        public final java.util.Set<String> flags = new java.util.HashSet<>();

        public void addReview(String name, int stars, String key, long day) {
            reviews.add(0, new Review(name, stars, key, day));
            while (reviews.size() > 24) reviews.remove(reviews.size() - 1);
        }

        public double avgStars() {
            if (reviews.isEmpty()) return 0;
            double s = 0;
            for (Review r : reviews) s += r.stars();
            return s / reviews.size();
        }

        public void remember(String name, int skin, String title) {
            for (int i = 0; i < regulars.size(); i++) {
                Regular r = regulars.get(i);
                if (r.name().equals(name)) { regulars.set(i, new Regular(name, skin, title, r.visits() + 1)); return; }
            }
            regulars.add(new Regular(name, skin, title, 1));
            if (regulars.size() > 16) {
                int worst = 0;
                for (int i = 1; i < regulars.size(); i++) if (regulars.get(i).visits() < regulars.get(worst).visits()) worst = i;
                regulars.remove(worst);
            }
        }

        public Zone(UUID id, UUID owner, String ownerName, BlockPos core, int tier) {
            this.id = id;
            this.owner = owner;
            this.ownerName = ownerName;
            this.core = core;
            this.tier = tier;
        }

        public ShopTier tierInfo() {
            return ShopTier.values()[Math.max(0, Math.min(ShopTier.values().length - 1, tier))];
        }

        public Box box() {
            return boxFor(core, tierInfo());
        }

        public boolean contains(BlockPos p) {
            Box b = box();
            return p.getX() >= b.minX && p.getX() < b.maxX && p.getY() >= b.minY && p.getY() < b.maxY && p.getZ() >= b.minZ && p.getZ() < b.maxZ;
        }

        NbtCompound write() {
            NbtCompound c = new NbtCompound();
            c.putUuid("id", id);
            c.putUuid("owner", owner);
            c.putString("owner_name", ownerName);
            c.putLong("core", core.asLong());
            c.putInt("tier", tier);
            c.putLong("revenue", revenue);
            c.putLong("balance", balance);
            c.putLong("wages", wagesPaid);
            c.putInt("sales", sales);
            c.putBoolean("cashier", cashier);
            c.putBoolean("guard", guard);
            c.putString("cashier_name", cashierName);
            c.putString("guard_name", guardName);
            c.putLong("last_wage_day", lastWageDay);
            c.putIntArray("metrics", new int[]{stolen, detained, recovered, customers, complaints, dealsAccepted, whales, reputation, decor, detectors, displays, buzz, smell});
            NbtCompound f = new NbtCompound();
            factors.forEach(f::putInt);
            c.put("factors", f);
            net.minecraft.nbt.NbtList rl = new net.minecraft.nbt.NbtList();
            for (Review r : reviews) rl.add(r.toNbt());
            c.put("reviews", rl);
            net.minecraft.nbt.NbtList gl = new net.minecraft.nbt.NbtList();
            for (Regular r : regulars) {
                NbtCompound g = new NbtCompound();
                g.putString("n", r.name());
                g.putInt("k", r.skin());
                g.putString("t", r.title());
                g.putInt("v", r.visits());
                gl.add(g);
            }
            c.put("regulars", gl);
            NbtCompound x = new NbtCompound();
            x.putInt("unattended", unattended);
            x.putInt("reject_pen", rejectPenalties);
            x.putLong("reject_day", rejectDay);
            x.putInt("infamy", infamy);
            x.putLong("infamy_day", infamyDay);
            x.putBoolean("cashier2", cashier2);
            x.putInt("charity", charity);
            x.putString("cashier2_name", cashier2Name);
            c.put("ext", x);
            return c;
        }

        static Zone read(NbtCompound c) {
            Zone z = new Zone(c.getUuid("id"), c.getUuid("owner"), c.getString("owner_name"), BlockPos.fromLong(c.getLong("core")), c.getInt("tier"));
            z.revenue = c.getLong("revenue");
            z.balance = c.getLong("balance");
            z.wagesPaid = c.getLong("wages");
            z.sales = c.getInt("sales");
            z.cashier = c.getBoolean("cashier");
            z.guard = c.getBoolean("guard");
            z.cashierName = c.getString("cashier_name");
            z.guardName = c.getString("guard_name");
            z.lastWageDay = c.contains("last_wage_day") ? c.getLong("last_wage_day") : -1;
            int[] m = c.getIntArray("metrics");
            int[] v = new int[13];
            System.arraycopy(m, 0, v, 0, Math.min(m.length, v.length));
            z.stolen = v[0]; z.detained = v[1]; z.recovered = v[2]; z.customers = v[3]; z.complaints = v[4];
            z.dealsAccepted = v[5]; z.whales = v[6]; z.reputation = m.length > 7 ? v[7] : 50; z.decor = v[8]; z.detectors = v[9]; z.displays = v[10]; z.buzz = v[11]; z.smell = v[12];
            NbtCompound f = c.getCompound("factors");
            for (String k : f.getKeys()) z.factors.put(k, f.getInt(k));
            net.minecraft.nbt.NbtList rl = c.getList("reviews", 10);
            for (int i = 0; i < rl.size(); i++) z.reviews.add(Review.fromNbt(rl.getCompound(i)));
            net.minecraft.nbt.NbtList gl = c.getList("regulars", 10);
            for (int i = 0; i < gl.size(); i++) {
                NbtCompound g = gl.getCompound(i);
                z.regulars.add(new Regular(g.getString("n"), g.getInt("k"), g.getString("t"), g.getInt("v")));
            }
            NbtCompound x = c.getCompound("ext"); // mundos antiguos: todo a 0
            z.unattended = x.getInt("unattended");
            z.rejectPenalties = x.getInt("reject_pen");
            z.rejectDay = x.contains("reject_day") ? x.getLong("reject_day") : -1;
            z.infamy = x.getInt("infamy");
            z.infamyDay = x.contains("infamy_day") ? x.getLong("infamy_day") : -1;
            z.cashier2 = x.getBoolean("cashier2");
            z.charity = x.getInt("charity");
            z.cashier2Name = x.getString("cashier2_name");
            return z;
        }
    }

    public static Box boxFor(BlockPos core, ShopTier t) {
        int half = t.size / 2;
        return new Box(core.getX() - half, core.getY() - BELOW, core.getZ() - half,
                core.getX() + half + 1, core.getY() + t.up + 1, core.getZ() + half + 1);
    }

    private static final Type<ShopZones> TYPE = new Type<>(ShopZones::new, ShopZones::fromNbt, null);

    private final List<Zone> zones = new ArrayList<>();
    /** Gastos de cada jugador en mercancía (Mayorista / teléfono): para el beneficio de sus tiendas. */
    private final Map<UUID, Long> costs = new HashMap<>();

    public static ShopZones get(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(TYPE, "tcgshop_shops");
    }

    public List<Zone> all() { return zones; }

    @Nullable
    public Zone at(BlockPos pos) {
        for (Zone z : zones) if (z.contains(pos)) return z;
        return null;
    }

    @Nullable
    public Zone byCore(BlockPos core) {
        for (Zone z : zones) if (z.core.equals(core)) return z;
        return null;
    }

    @Nullable
    public Zone byId(UUID id) {
        for (Zone z : zones) if (z.id.equals(id)) return z;
        return null;
    }

    public boolean overlaps(Box box) {
        for (Zone z : zones) if (z.box().intersects(box)) return true;
        return false;
    }

    public void add(Zone z) { zones.add(z); markDirty(); }

    public void remove(Zone z) { zones.remove(z); markDirty(); }

    public long costs(UUID player) { return costs.getOrDefault(player, 0L); }

    public void addCost(UUID player, long amount) {
        costs.merge(player, amount, Long::sum);
        markDirty();
    }

    public long revenue(UUID player) {
        long r = 0;
        for (Zone z : zones) if (z.owner.equals(player)) r += z.revenue;
        return r;
    }

    private static ShopZones fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        ShopZones s = new ShopZones();
        NbtList l = nbt.getList("zones2", 10);
        for (int i = 0; i < l.size(); i++) s.zones.add(Zone.read(l.getCompound(i)));
        if (!nbt.contains("zones2")) { // migración desde la versión anterior (formato con codec)
            NbtList old = nbt.getList("zones", 10);
            for (int i = 0; i < old.size(); i++) {
                NbtCompound o = old.getCompound(i);
                int[] p = o.getIntArray("core");
                if (p.length < 3) continue;
                Zone z = new Zone(o.getUuid("id"), o.getUuid("owner"), o.getString("owner_name"), new BlockPos(p[0], p[1], p[2]), o.getInt("tier"));
                z.revenue = o.getLong("revenue");
                z.balance = o.getLong("balance");
                z.sales = o.getInt("sales");
                s.zones.add(z);
            }
        }
        NbtCompound c = nbt.getCompound("costs2");
        for (String k : c.getKeys()) s.costs.put(UUID.fromString(k), c.getLong(k));
        return s;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        NbtList l = new NbtList();
        for (Zone z : zones) l.add(z.write());
        nbt.put("zones2", l);
        NbtCompound c = new NbtCompound();
        costs.forEach((k, v) -> c.putLong(k.toString(), v));
        nbt.put("costs2", c);
        return nbt;
    }
}

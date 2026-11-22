package dev.tcgshop.shop;

import net.minecraft.item.ItemStack;
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

        /** Frase completa de la reseña (1120 variantes por estrellas y motivo, tools/gen_lang_reviews.py); fija por cliente y día. */
        public String textKey() {
            int n = Math.floorMod((name + "#" + day + "#" + key).hashCode(), 8);
            return "tcgshop.reviewtext." + key + "." + Math.max(1, Math.min(5, stars)) + "." + n;
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
        /**
         * Reputación con decimales (sube despacio, baja deprisa). -1 = aún no calculada (mundos antiguos: se toma
         * {@link #reputation}). {@link #reputation} es siempre su valor redondeado.
         */
        public double repExact = -1;
        /** Último objetivo y techo de reputación calculados (no se guardan; los enseña el teléfono vía factors m_target / m_ceiling). */
        public int repTarget = -1, repCeiling = 100;
        // ---- 0.9: misiones de la tienda y aforo (ShopMissions)
        /** Misiones cumplidas (bit i = ShopMissions.Mission i). -1 = aún sin calcular (mundos antiguos: flujo normal). */
        public int missions = -1;
        /** Misiones que se han cumplido alguna vez (para avisar sólo la primera vez, sin spam si una va y viene). */
        public int missionsEver;
        /** Asientos (sillas, bancos, sofás de muebles) y mesas de juego de la tienda en el último cálculo. */
        public int seats, playTables;
        /** Clientes que se quedaron sin entrar por aforo: hoy y el día anterior (para sugerir mejorar la tienda). */
        public int overflowToday, overflowPrev;
        public long overflowDay = -1;
        /** No se guardan: progreso de cada misión del último cálculo (null = sin calcular) y cuándo se calculó. */
        public int[] missionProgress;
        public long lastCalc = -1;
        /**
         * No se guarda: lo que suman / restan ahora mismo los eventos de luz y decoración (apagón, Flaaffy...) a la
         * reputación. Sólo cuenta para la reputación efectiva de los clientes (ShopLife.effectiveRep), nunca para
         * settleReputation, igual que Mods.repDelta.
         */
        public int eventEnvRep;
        /** 0.9 eventos aleatorios: eventos activos, memoria para reseñas, historial e ingresos por día (ext.events). */
        public dev.tcgshop.events.ZoneEvents events = new dev.tcgshop.events.ZoneEvents();

        /** Aforo actual: base del nivel + bonus de misiones y asientos. */
        public int capacity() { return ShopMissions.capacity(this); }

        /** Multiplicador de llegada de clientes por las misiones cumplidas. */
        public double flow() { return ShopMissions.flow(missions); }

        public void addReview(String name, int stars, String key, long day) {
            reviews.add(0, new Review(name, stars, key, day));
            while (reviews.size() > 24) reviews.remove(reviews.size() - 1);
        }

        /**
         * Aplica el valor "bruto" de reputación que sale del entorno (ShopLife.recompute) con la economía de la
         * reputación:
         *  · rendimientos decrecientes por encima de repSoftCap (los últimos puntos cuestan mucho más),
         *  · un techo por experiencia (ventas hechas) y por estrellas medias (con 5+ reseñas),
         *  · sube poco a poco (más despacio cuanto más alta) y baja deprisa.
         */
        public void settleReputation(int raw) {
            dev.tcgshop.EconomyConfig c = dev.tcgshop.EconomyConfig.INSTANCE;
            int knee = Math.max(0, Math.min(100, c.repSoftCap));
            double t = Math.max(0, Math.min(100, raw));
            if (t > knee && knee < 100) t = knee + (100 - knee) * Math.pow((t - knee) / (100.0 - knee), Math.max(1, c.repTopExponent));
            // techo: hace falta experiencia (ventas) para llegar arriba, y no se puede estar muy alto con malas reseñas
            double exp = c.repSalesForMax <= 0 ? 1 : Math.min(1, sales / (double) c.repSalesForMax);
            double ceiling = knee + (100 - knee) * Math.sqrt(exp);
            if (reviews.size() >= 5) ceiling = Math.min(ceiling, 40 + avgStars() * 12); // 5★ = 100, 4★ = 88, 3★ = 76
            t = Math.min(t, ceiling);
            if (repExact < 0) repExact = reputation;
            double gap = t - repExact;
            if (gap > 0) {
                double slow = Math.max(0.15, 1 - repExact / 125.0); // cuanto más alta, más le cuesta
                double step = Math.min(c.repMaxRisePerUpdate, Math.max(0.05, gap * c.repRiseRate * slow));
                repExact = Math.min(t, repExact + step);
            } else if (gap < 0) {
                // baja deprisa sólo si el objetivo bajo se repite (dos cálculos seguidos); una lectura suelta
                // (un monstruo que pasa, un cálculo raro) apenas la mueve, para no perder en un minuto lo que cuesta horas
                boolean confirmed = repTarget >= 0 && repTarget <= Math.round(t) + 2;
                double rate = confirmed ? c.repFallRate : Math.min(c.repFallRate, 0.05);
                repExact = Math.max(t, repExact + (confirmed ? Math.min(-0.25, gap * rate) : gap * rate));
            }
            repExact = Math.max(0, Math.min(100, repExact));
            reputation = (int) Math.round(repExact);
            repTarget = (int) Math.round(t);
            repCeiling = (int) Math.round(ceiling);
            factors.put("m_target", repTarget);
            factors.put("m_ceiling", repCeiling);
        }

        /** Lo que los clientes están dispuestos a pagar por encima del mercado en esta tienda (0.2 = +20%). */
        public double premiumFor(ItemStack st) {
            return repPremium(reputation, isSealed(st));
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

        public NbtCompound write() {
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
            x.putDouble("rep_exact", repExact);
            x.putInt("missions", missions);
            x.putInt("missions_ever", missionsEver);
            x.putInt("seats", seats);
            x.putInt("play_tables", playTables);
            x.putInt("ovf_today", overflowToday);
            x.putInt("ovf_prev", overflowPrev);
            x.putLong("ovf_day", overflowDay);
            x.put("events", events.write());
            c.put("ext", x);
            return c;
        }

        public static Zone read(NbtCompound c) {
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
            z.repExact = x.contains("rep_exact") ? x.getDouble("rep_exact") : -1;
            z.missions = x.contains("missions") ? x.getInt("missions") : -1;
            z.missionsEver = x.getInt("missions_ever");
            z.seats = x.getInt("seats");
            z.playTables = x.getInt("play_tables");
            z.overflowToday = x.getInt("ovf_today");
            z.overflowPrev = x.getInt("ovf_prev");
            z.overflowDay = x.contains("ovf_day") ? x.getLong("ovf_day") : -1;
            z.events = dev.tcgshop.events.ZoneEvents.read(x.getCompound("events"));
            return z;
        }
    }

    // ------------------------------------------------------------------ economía de la reputación

    /**
     * Sobreprecio que aceptan los clientes según la reputación de la tienda (configurable en tcgshop-economy.json):
     * nada hasta repPremiumStart, luego crece con la curva repPremiumCurve hasta repPremiumMax a 100. Sobres y cajas
     * (repPremiumSealedBonus) se pagan aún mejor en una tienda famosa. Por debajo de repPenaltyBelow es negativo:
     * en una tienda de mala fama quieren precios por debajo del mercado. Común a cliente y servidor (el cliente
     * tiene EconomyConfig sincronizado), así la ventana de trato puede enseñarlo.
     */
    public static double repPremium(int rep, boolean sealed) {
        dev.tcgshop.EconomyConfig c = dev.tcgshop.EconomyConfig.INSTANCE;
        if (rep < c.repPenaltyBelow && c.repPenaltyBelow > 0)
            return -c.repPenaltyMax * (c.repPenaltyBelow - rep) / (double) c.repPenaltyBelow;
        if (rep <= c.repPremiumStart || c.repPremiumMax <= 0) return 0;
        double x = Math.min(1, (rep - c.repPremiumStart) / (double) Math.max(1, 100 - c.repPremiumStart));
        double p = c.repPremiumMax * Math.pow(x, Math.max(0.1, c.repPremiumCurve));
        return sealed ? p * Math.max(0, c.repPremiumSealedBonus) : p;
    }

    /** Sobres, cajas y Mystery Box (los productos "sellados"). */
    public static boolean isSealed(ItemStack st) {
        return dev.masterball.api.card.CardInfo.isPack(st) || st.isOf(TcgBlocks.BOOSTER_BOX_ITEM) || st.isOf(TcgBlocks.MYSTERY_BOX);
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

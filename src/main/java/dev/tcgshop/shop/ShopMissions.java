package dev.tcgshop.shop;

import dev.tcgshop.pricing.PriceService;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

/**
 * Misiones de la tienda (0.9): pasos concretos para atraer clientes. Se evalúan a partir del estado de la tienda en
 * ShopLife.recompute (cada minuto, sin escanear bloques por su cuenta) y se guardan como máscara de bits en la zona.
 *
 *  · Flujo de clientes: la probabilidad de que llegue alguien se multiplica por {@link #flow(int)}, que crece con las
 *    misiones cumplidas (0 → 35 %, todas → 115 %). Las tiendas que ya cumplían las condiciones no pierden clientes:
 *    las misiones son de estado, se cumplen solas al recalcular.
 *  · Aforo: base por nivel de tienda (ShopTier.maxCustomers) + hasta {@link #MAX_BONUS} por hitos de misiones y por
 *    asientos / mesas de juego. Con la tienda llena, los clientes hacen cola fuera (ShopLife.queueGate).
 */
public final class ShopMissions {
    private ShopMissions() {}

    /** Orden = de lo más fácil a lo más caro: la primera sin cumplir es el "Siguiente paso". */
    public enum Mission {
        STOCK("stock", 2),        // 2 expositores con producto
        REGISTER("register", 1),  // caja registradora o cajero contratado
        BUILDING("building", 1),  // local con techo y paredes
        LIGHT("light", 8),        // buena iluminación (luz media >= 8)
        PRICES("prices", 1),      // precios justos (factor de precio >= 0)
        DECOR("decor", 3),        // decoración mínima
        VARIETY("variety", 6),    // 6 productos distintos
        BOX("box", 1),            // una caja de sobres a la venta
        CLEAN("clean", 1),        // sin mal olor ni monstruos dentro
        PLAY("play", 1),          // mesa de juego o 2 asientos
        REVIEWS("reviews", 5),    // 5 reseñas con media de 3★ o más
        TIER("tier", 2);          // tienda de Piedra o mejor (nivel 2)

        public final String key;
        public final int goal;

        Mission(String key, int goal) {
            this.key = key;
            this.goal = goal;
        }
    }

    public static final int COUNT = Mission.values().length;
    public static final double FLOW_MIN = 0.35, FLOW_MAX = 1.15;
    /** Bonus máximo de aforo sobre la base del nivel. */
    public static final int MAX_BONUS = 3;
    /** Hitos de misiones que dan +1 de aforo. */
    public static final int MILESTONE_A = 6, MILESTONE_B = COUNT;
    /** Clientes que se quedan sin entrar en un día para sugerir mejorar la tienda. */
    public static final int OVERFLOW_SUGGEST = 4;

    /** Lo que ShopLife.recompute ha visto de la tienda. */
    public static final class Snapshot {
        public int stockedDisplays, stocked, variety, decor, price, smell, hostilesInside, seats, playTables;
        public boolean boosterBox, register, envKnown;
        public double roof, walls, light;
    }

    /** Evalúa las misiones; deja el progreso de cada una en z.missionProgress y devuelve la máscara. */
    public static int evaluate(ShopZones.Zone z, Snapshot s) {
        int[] prog = new int[COUNT];
        prog[Mission.STOCK.ordinal()] = s.stockedDisplays;
        prog[Mission.REGISTER.ordinal()] = s.register || z.cashier || z.cashier2 ? 1 : 0;
        prog[Mission.BUILDING.ordinal()] = s.envKnown && s.roof >= 0.6 && s.walls >= 0.5 ? 1 : 0;
        prog[Mission.LIGHT.ordinal()] = s.envKnown ? (int) Math.floor(s.light) : 0;
        prog[Mission.PRICES.ordinal()] = s.stocked > 0 && s.price >= 0 ? 1 : 0;
        prog[Mission.DECOR.ordinal()] = s.decor;
        prog[Mission.VARIETY.ordinal()] = s.variety;
        prog[Mission.BOX.ordinal()] = s.boosterBox ? 1 : 0;
        prog[Mission.CLEAN.ordinal()] = s.smell < 20 && s.hostilesInside == 0 ? 1 : 0;
        prog[Mission.PLAY.ordinal()] = s.playTables >= 1 || s.seats >= 2 ? 1 : 0;
        prog[Mission.REVIEWS.ordinal()] = z.reviews.size() >= 5 && z.avgStars() < 3 ? Math.min(4, z.reviews.size()) : z.reviews.size();
        prog[Mission.TIER.ordinal()] = z.tier + 1;
        int mask = 0;
        for (Mission m : Mission.values()) if (prog[m.ordinal()] >= m.goal) mask |= 1 << m.ordinal();
        z.missionProgress = prog;
        return mask;
    }

    public static boolean done(int mask, Mission m) {
        return mask >= 0 && (mask & (1 << m.ordinal())) != 0;
    }

    public static int count(int mask) {
        return mask < 0 ? 0 : Integer.bitCount(mask & ((1 << COUNT) - 1));
    }

    /** Primera misión sin cumplir (el "Siguiente paso") o -1 si están todas. */
    public static int nextStep(int mask) {
        for (Mission m : Mission.values()) if (!done(mask, m)) return m.ordinal();
        return -1;
    }

    /** Multiplicador de llegada de clientes: 0.35 sin misiones … 1.15 con todas (curva suave). Sin calcular: 1. */
    public static double flow(int mask) {
        if (mask < 0) return 1.0;
        double f = count(mask) / (double) COUNT;
        return FLOW_MIN + (FLOW_MAX - FLOW_MIN) * Math.pow(f, 0.8);
    }

    /** Bonus de aforo: +1 con 6 misiones, +1 con todas, +1 por zona de juego (mesa de juego o 3+ asientos). Máx. +3. */
    public static int capacityBonus(ShopZones.Zone z) {
        int n = count(z.missions), b = 0;
        if (n >= MILESTONE_A) b++;
        if (n >= MILESTONE_B) b++;
        if (z.playTables >= 1 || z.seats >= 3) b++;
        return Math.min(MAX_BONUS, b);
    }

    public static int capacity(ShopZones.Zone z) {
        return z.tierInfo().maxCustomers + capacityBonus(z);
    }

    /**
     * Aforo real ahora mismo: el de la tienda + el modificador temporal de los eventos (Mods.maxCustAdd, p. ej. una
     * avalancha +2 o una obra -2). Es el que usa la puerta (ShopLife.queueGate), los techos de aparición y la ventana.
     */
    public static int effectiveCapacity(ShopZones.Zone z) {
        return Math.max(1, capacity(z) + dev.tcgshop.events.EventManager.mods(z).maxCustAdd);
    }

    /** Cuántos pueden esperar fuera en la cola (según el aforo propio de la tienda, sin eventos). */
    public static int queueMax(ShopZones.Zone z) {
        return 2 + capacity(z) / 4;
    }

    // ------------------------------------------------------------------ desbordes de aforo (para sugerir mejorar)

    private static void rollDay(ShopZones.Zone z, long day) {
        if (z.overflowDay == day) return;
        z.overflowPrev = z.overflowDay == day - 1 ? z.overflowToday : 0;
        z.overflowToday = 0;
        z.overflowDay = day;
    }

    public static void addOverflow(ShopZones.Zone z, long day) {
        rollDay(z, day);
        z.overflowToday = Math.min(9999, z.overflowToday + 1);
    }

    public static int overflowToday(ShopZones.Zone z, long day) {
        return z.overflowDay == day ? z.overflowToday : 0;
    }

    public static int overflowPrev(ShopZones.Zone z, long day) {
        if (z.overflowDay == day) return z.overflowPrev;
        return z.overflowDay == day - 1 ? z.overflowToday : 0;
    }

    /** ¿Se llena a menudo? Entonces la ventana sugiere pasar al siguiente nivel. */
    public static boolean suggestUpgrade(ShopZones.Zone z, long day) {
        if (z.tierInfo().next() == null) return false;
        return overflowToday(z, day) >= OVERFLOW_SUGGEST || overflowPrev(z, day) >= OVERFLOW_SUGGEST;
    }

    // ------------------------------------------------------------------ datos para la ventana y el teléfono

    /** Lo que ve el dueño al hacer clic derecho en el bloque de tienda (ventana "shop_status"). */
    public static NbtCompound statusNbt(ServerWorld world, ShopZones.Zone z, ServerPlayerEntity owner) {
        ShopZones zones = ShopZones.get(world);
        long day = world.getTimeOfDay() / 24000;
        NbtCompound d = new NbtCompound();
        d.putString("owner", z.ownerName);
        d.putInt("tier", z.tier);
        d.putInt("rep", z.reputation);
        d.putString("revenue", PriceService.format(z.revenue));
        d.putString("balance", PriceService.format(z.balance));
        long profit = zones.revenue(owner.getUuid()) - zones.costs(owner.getUuid());
        d.putString("profit", PriceService.format(profit));
        d.putBoolean("profit_neg", profit < 0);
        d.putInt("sales", z.sales);
        d.putInt("reviews", z.reviews.size());
        d.putDouble("stars", z.avgStars());
        d.putInt("customers", z.customers);
        // aforo
        int capEff = effectiveCapacity(z);
        d.putInt("cap", capEff);
        d.putInt("cap_event", capEff - capacity(z));
        d.putInt("cap_base", z.tierInfo().maxCustomers);
        d.putInt("cap_bonus", capacityBonus(z));
        d.putInt("inside", ShopLife.insideCount(world, z));
        d.putInt("queued", ShopLife.queuedCount(z));
        d.putInt("queue_max", queueMax(z));
        d.putInt("seats", z.seats);
        d.putInt("play_tables", z.playTables);
        d.putInt("ovf_today", overflowToday(z, day));
        d.putInt("ovf_prev", overflowPrev(z, day));
        d.putBoolean("upgrade", suggestUpgrade(z, day));
        ShopTier next = z.tierInfo().next();
        d.putString("next_tier", next == null ? "" : next.key);
        d.putInt("next_cap", next == null ? 0 : next.maxCustomers + capacityBonus(z));
        // misiones y flujo
        d.putInt("missions", z.missions);
        d.putInt("flow", (int) Math.round(flow(z.missions) * 100));
        d.putInt("flow_max", (int) Math.round(FLOW_MAX * 100));
        if (z.missionProgress != null) d.putIntArray("prog", z.missionProgress);
        int[] goals = new int[COUNT];
        for (Mission m : Mission.values()) goals[m.ordinal()] = m.goal;
        d.putIntArray("goal", goals);
        return d;
    }

    /** Resumen para la sincronización de zonas del dueño (teléfono): va dentro del compuesto "ext". */
    public static void writeSync(ShopZones.Zone z, NbtCompound ext) {
        ext.putInt("missions", z.missions);
        ext.putInt("mission_count", count(z.missions));
        ext.putInt("flow", (int) Math.round(flow(z.missions) * 100));
        ext.putInt("cap", effectiveCapacity(z));
        ext.putInt("queued", ShopLife.queuedCount(z));
    }
}

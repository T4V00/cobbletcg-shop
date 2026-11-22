package dev.tcgshop.events;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.tcgshop.TcgShop;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * config/tcgshop-events.json — todos los números de los eventos aleatorios (sólo servidor). Mismo patrón que
 * EconomyConfig: campos públicos, textos de ayuda (_help), topes en sanitize() y recarga con /tcgshop reload.
 */
public class EventsConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("tcgshop-events.json");

    public static EventsConfig INSTANCE = new EventsConfig();

    public Map<String, String> _help = new LinkedHashMap<>();

    /** Interruptor general. */
    public boolean enabled = true;
    /** Probabilidad por dueño y día de Minecraft de que pase ALGÚN evento (una tirada fija por dueño y día). */
    public double dailyChance = 0.10;
    /** Eventos naturales como mucho por dueño y día (entre todas sus tiendas). */
    public int maxPerOwnerPerDay = 1;
    /** El dueño tiene que estar conectado y a esta distancia (bloques) de la tienda para que el evento empiece. */
    public int ownerRange = 96;
    /** Momento del día (ticks 0-24000) entre los que puede empezar un evento: el turno de día de la tienda. */
    public int shiftStart = 1000, shiftEnd = 11500;
    /** Pesos de categoría y de rareza dentro de una tirada con éxito. */
    public Map<String, Double> categoryWeights = new LinkedHashMap<>(Map.of("positive", 40.0, "neutral", 30.0, "negative", 30.0));
    public Map<String, Double> tierWeights = new LinkedHashMap<>(Map.of("common", 65.0, "uncommon", 25.0, "rare", 8.0, "extraordinary", 2.0));
    /** Días que tarda en poder repetirse el mismo evento, por rareza. */
    public Map<String, Integer> tierCooldownDays = new LinkedHashMap<>(Map.of("common", 5, "uncommon", 10, "rare", 20, "extraordinary", 40));
    /** Probabilidad de que cada evento exista en este mundo (el "mazo" del mundo), por rareza. */
    public Map<String, Double> deckPresence = new LinkedHashMap<>(Map.of("common", 1.0, "uncommon", 0.85, "rare", 0.65, "extraordinary", 0.40));
    /** Peso de un evento que está entre los últimos historySize del dueño. */
    public double historyPenalty = 0.25;
    public int historySize = 10;
    /** Gracia: sin eventos negativos hasta tener estas ventas y días de vida en la tienda. */
    public int graceMinSales = 20, graceMinDays = 3;
    /** Los positivos también piden un mínimo (que no compense abrir muchas tiendas vacías). */
    public int positiveMinSales = 5, positiveMinDisplays = 2;
    /** Multiplicador global de la duración de los eventos. */
    public double durationMult = 1.0;
    /** Precio máximo (sobre el mercado) que acepta un visitante de evento. */
    public double visitorPriceCap = 1.15;
    /** Probabilidad de la reseña "lo arreglaron rápido" si se arregló antes de que llegara el cliente (por defecto). */
    public double reviewFixedChance = 0.25;
    /** Dinero de premios por dueño y día: el menor entre moneyCapAbs y moneyCapPct de sus ingresos medios diarios (mínimo moneyCapFloor). */
    public long moneyCapAbs = 10_000, moneyCapFloor = 500;
    public double moneyCapPct = 0.10;
    /** Tope semanal = esto x el tope diario. */
    public double weeklyCapMult = 3.0;
    /** Propina máxima sobre una venta. */
    public double tipMaxPct = 0.10;
    /** Avisos (toast) al dueño al empezar / arreglarse un evento. */
    public boolean toasts = true;
    /** Dimensiones sin eventos (ids, p. ej. "minecraft:the_nether"). */
    public List<String> dimensionBlacklist = new ArrayList<>();
    /** Ajustes por evento: {"tcgshop:pipe_leak": {"enabled": false, "weight": 0.5, "durationMult": 1.0, "cooldownDays": 8}}. */
    public Map<String, EventOverride> overrides = new LinkedHashMap<>();
    /** Topes globales de los modificadores sumados. */
    public Clamps clamps = new Clamps();

    public static class EventOverride {
        public Boolean enabled;
        public Double weight, durationMult;
        public Integer cooldownDays;
    }

    public static class Clamps {
        public double[] spawnMult = {0.5, 1.6}, maxCustAdd = {-2, 2}, buyMult = {0.6, 1.3}, perceivedMult = {0.9, 1.15},
                haggleMult = {0.85, 1.2}, patienceMult = {0.5, 1.5}, serviceMult = {0.6, 1.6}, moodStart = {-2, 2}, repDelta = {-8, 6};
        public double revenueMultMax = 1.15;
        public double typeWeightMax = 4.0;

        void sanitize() {
            spawnMult = pair(spawnMult, 0.5, 1.6, 0.1, 3);
            maxCustAdd = pair(maxCustAdd, -2, 2, -6, 6);
            buyMult = pair(buyMult, 0.6, 1.3, 0.1, 2);
            perceivedMult = pair(perceivedMult, 0.9, 1.15, 0.5, 2);
            haggleMult = pair(haggleMult, 0.85, 1.2, 0.5, 2);
            patienceMult = pair(patienceMult, 0.5, 1.5, 0.2, 3);
            serviceMult = pair(serviceMult, 0.6, 1.6, 0.2, 3);
            moodStart = pair(moodStart, -2, 2, -4, 4);
            repDelta = pair(repDelta, -8, 6, -20, 20);
            revenueMultMax = Math.max(1, Math.min(1.15, revenueMultMax));
            typeWeightMax = Math.max(1, Math.min(10, typeWeightMax));
        }

        private static double[] pair(double[] a, double dlo, double dhi, double min, double max) {
            if (a == null || a.length != 2 || Double.isNaN(a[0]) || Double.isNaN(a[1])) return new double[]{dlo, dhi};
            double lo = Math.max(min, Math.min(max, Math.min(a[0], a[1]))), hi = Math.max(min, Math.min(max, Math.max(a[0], a[1])));
            return new double[]{lo, hi};
        }
    }

    // ------------------------------------------------------------------ consultas

    public double categoryWeight(EventDef.Category c) { return Math.max(0, categoryWeights.getOrDefault(c.key(), 0.0)); }

    public double tierWeight(EventDef.Tier t) { return Math.max(0, tierWeights.getOrDefault(t.key(), 0.0)); }

    public double presence(EventDef.Tier t) { return Math.max(0, Math.min(1, deckPresence.getOrDefault(t.key(), 1.0))); }

    public int cooldownDays(EventDef def) {
        EventOverride o = overrides.get(def.id());
        if (o != null && o.cooldownDays != null) return Math.max(0, o.cooldownDays);
        if (def.conditions().cooldownDays() >= 0) return def.conditions().cooldownDays();
        return Math.max(0, tierCooldownDays.getOrDefault(def.tier().key(), 5));
    }

    public boolean enabled(EventDef def) {
        EventOverride o = overrides.get(def.id());
        return o == null || o.enabled == null || o.enabled;
    }

    public double weight(EventDef def) {
        EventOverride o = overrides.get(def.id());
        return Math.max(0, def.weight() * (o != null && o.weight != null ? o.weight : 1.0));
    }

    public double durationMult(EventDef def) {
        EventOverride o = overrides.get(def.id());
        return Math.max(0.1, Math.min(4, durationMult * (o != null && o.durationMult != null ? o.durationMult : 1.0)));
    }

    // ------------------------------------------------------------------ carga

    public static void load() {
        EventsConfig c = null;
        try {
            if (Files.exists(FILE)) try (Reader r = Files.newBufferedReader(FILE)) { c = GSON.fromJson(r, EventsConfig.class); }
        } catch (Exception e) {
            TcgShop.LOGGER.error("No se pudo leer {} (se usan los valores por defecto)", FILE, e);
        }
        if (c == null) c = new EventsConfig();
        c.sanitize();
        c.fillHelp();
        INSTANCE = c;
        save();
    }

    public static void save() {
        try (Writer w = Files.newBufferedWriter(FILE)) {
            GSON.toJson(INSTANCE, w);
        } catch (Exception e) {
            TcgShop.LOGGER.error("No se pudo guardar {}", FILE, e);
        }
    }

    void sanitize() {
        dailyChance = EventDef.clamp(dailyChance, 0, 1);
        maxPerOwnerPerDay = Math.max(0, Math.min(5, maxPerOwnerPerDay));
        ownerRange = Math.max(16, Math.min(512, ownerRange));
        shiftStart = Math.max(0, Math.min(23_000, shiftStart));
        shiftEnd = Math.max(shiftStart + 500, Math.min(24_000, shiftEnd));
        categoryWeights = weights(categoryWeights, Map.of("positive", 40.0, "neutral", 30.0, "negative", 30.0));
        tierWeights = weights(tierWeights, Map.of("common", 65.0, "uncommon", 25.0, "rare", 8.0, "extraordinary", 2.0));
        deckPresence = weights(deckPresence, Map.of("common", 1.0, "uncommon", 0.85, "rare", 0.65, "extraordinary", 0.40));
        deckPresence.replaceAll((k, v) -> Math.max(0, Math.min(1, v)));
        Map<String, Integer> cd = new LinkedHashMap<>(Map.of("common", 5, "uncommon", 10, "rare", 20, "extraordinary", 40));
        if (tierCooldownDays != null) tierCooldownDays.forEach((k, v) -> { if (v != null && cd.containsKey(k)) cd.put(k, Math.max(0, Math.min(365, v))); });
        tierCooldownDays = cd;
        historyPenalty = EventDef.clamp(historyPenalty, 0, 1);
        historySize = Math.max(0, Math.min(40, historySize));
        graceMinSales = Math.max(0, graceMinSales);
        graceMinDays = Math.max(0, Math.min(60, graceMinDays));
        positiveMinSales = Math.max(0, positiveMinSales);
        positiveMinDisplays = Math.max(0, positiveMinDisplays);
        durationMult = EventDef.clamp(durationMult, 0.1, 4);
        visitorPriceCap = EventDef.clamp(visitorPriceCap, 0.8, 1.5);
        reviewFixedChance = EventDef.clamp(reviewFixedChance, 0, 1);
        moneyCapAbs = Math.max(0, moneyCapAbs);
        moneyCapFloor = Math.max(0, Math.min(moneyCapAbs, moneyCapFloor));
        moneyCapPct = EventDef.clamp(moneyCapPct, 0, 1);
        weeklyCapMult = EventDef.clamp(weeklyCapMult, 1, 7);
        tipMaxPct = EventDef.clamp(tipMaxPct, 0, 0.5);
        if (dimensionBlacklist == null) dimensionBlacklist = new ArrayList<>();
        if (overrides == null) overrides = new LinkedHashMap<>();
        overrides.values().removeIf(java.util.Objects::isNull);
        if (clamps == null) clamps = new Clamps();
        clamps.sanitize();
        if (_help == null) _help = new LinkedHashMap<>();
    }

    private static Map<String, Double> weights(Map<String, Double> in, Map<String, Double> def) {
        Map<String, Double> out = new LinkedHashMap<>();
        for (String k : List.of("positive", "neutral", "negative", "common", "uncommon", "rare", "extraordinary")) {
            if (!def.containsKey(k)) continue;
            Double v = in == null ? null : in.get(k);
            out.put(k, v == null || v.isNaN() ? def.get(k) : Math.max(0, v));
        }
        return out;
    }

    private void fillHelp() {
        _help.clear();
        _help.put("aplicar", "Edita este archivo y usa /tcgshop reload (operador). Los eventos se definen en data/<ns>/tcgshop_events/*.json (datapacks pueden añadir, cambiar o desactivar eventos con {\"enabled\": false}).");
        _help.put("enabled", "Interruptor general de los eventos aleatorios. /tcgshop event start funciona aunque esté desactivado.");
        _help.put("dailyChance", "Probabilidad por dueño y día de Minecraft de que pase algún evento (0.10 = 10%). Una sola tirada fija por dueño y día: recargar el mundo no la cambia.");
        _help.put("maxPerOwnerPerDay", "Eventos naturales como mucho por dueño y día, entre todas sus tiendas.");
        _help.put("ownerRange", "Sólo empieza si el dueño está conectado y a esta distancia de la tienda; si no aparece en todo el día, se pierde (nunca se castiga a quien no juega).");
        _help.put("shiftStart/shiftEnd", "Momento del día (0-24000 ticks; 0 = 6:00) entre los que empieza el evento: un momento al azar del turno de día.");
        _help.put("categoryWeights/tierWeights", "Reparto cuando sale evento: categoría (positivo/neutral/negativo) y rareza (común/poco común/raro/extraordinario).");
        _help.put("tierCooldownDays", "Días que tarda un mismo evento en poder repetirse, por rareza.");
        _help.put("deckPresence", "Probabilidad de que cada evento exista en este mundo, por rareza (se sortea una vez por mundo; /tcgshop event deck reroll).");
        _help.put("historyPenalty/historySize", "Peso de un evento que salió entre los últimos N del dueño (0.25 = 4 veces menos probable). La misma plantilla nunca sale dos veces seguidas.");
        _help.put("graceMinSales/graceMinDays", "Una tienda nueva no recibe eventos negativos hasta tener estas ventas y días de vida.");
        _help.put("positiveMinSales/positiveMinDisplays", "Mínimo de ventas y expositores para eventos positivos (abrir muchas tiendas vacías no da premios).");
        _help.put("durationMult", "Multiplicador de la duración de todos los eventos.");
        _help.put("visitorPriceCap", "Precio máximo, sobre el de mercado, que paga un visitante de evento (coleccionista, cliente legendario...).");
        _help.put("reviewFixedChance", "Probabilidad de la reseña 'hubo un problema pero lo arreglaron rápido' si lo arreglaste antes de que llegara ese cliente.");
        _help.put("moneyCapAbs/moneyCapFloor/moneyCapPct/weeklyCapMult", "Dinero máximo de premios por dueño: al día el menor entre moneyCapAbs y moneyCapPct de tus ingresos medios diarios (mínimo moneyCapFloor); a la semana weeklyCapMult veces eso.");
        _help.put("tipMaxPct", "Propina máxima sobre una venta (evento 'propina generosa').");
        _help.put("toasts", "Avisos en pantalla al dueño cuando empieza o se arregla un evento.");
        _help.put("dimensionBlacklist", "Dimensiones sin eventos.");
        _help.put("overrides", "Ajustes por evento: enabled, weight (multiplica el peso), durationMult y cooldownDays. Ej.: {\"tcgshop:rumor\": {\"weight\": 0.5}}.");
        _help.put("clamps", "Topes de la suma de modificadores de los eventos activos (p. ej. revenueMultMax nunca pasa de 1.15).");
    }
}

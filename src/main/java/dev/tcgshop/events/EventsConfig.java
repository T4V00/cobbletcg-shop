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
 * config/tcgshop-events.json — all the numbers of the random events (server only). Same pattern as
 * EconomyConfig: public fields, help texts (_help), caps in sanitize() and reload with /tcgshop reload.
 */
public class EventsConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("tcgshop-events.json");

    public static EventsConfig INSTANCE = new EventsConfig();

    public Map<String, String> _help = new LinkedHashMap<>();

    /** Master switch. */
    public boolean enabled = true;
    /** Chance per owner and Minecraft day that ANY event happens (one fixed roll per owner and day). */
    public double dailyChance = 0.10;
    /** Maximum natural events per owner and day (across all their shops). */
    public int maxPerOwnerPerDay = 1;
    /** The owner has to be online and within this distance (blocks) of the shop for the event to start. */
    public int ownerRange = 96;
    /** Time of day (ticks 0-24000) between which an event can start: the shop's day shift. */
    public int shiftStart = 1000, shiftEnd = 11500;
    /** Category and rarity weights within a successful roll. */
    public Map<String, Double> categoryWeights = new LinkedHashMap<>(Map.of("positive", 40.0, "neutral", 30.0, "negative", 30.0));
    public Map<String, Double> tierWeights = new LinkedHashMap<>(Map.of("common", 65.0, "uncommon", 25.0, "rare", 8.0, "extraordinary", 2.0));
    /** Days before the same event can repeat, per rarity. */
    public Map<String, Integer> tierCooldownDays = new LinkedHashMap<>(Map.of("common", 5, "uncommon", 10, "rare", 20, "extraordinary", 40));
    /** Chance that each event exists in this world (the world's "deck"), per rarity. */
    public Map<String, Double> deckPresence = new LinkedHashMap<>(Map.of("common", 1.0, "uncommon", 0.85, "rare", 0.65, "extraordinary", 0.40));
    /** Weight of an event that is among the owner's last historySize. */
    public double historyPenalty = 0.25;
    public int historySize = 10;
    /** Grace: no negative events until the shop has these sales and days of life. */
    public int graceMinSales = 20, graceMinDays = 3;
    /** Positive ones also require a minimum (so opening many empty shops does not pay off). */
    public int positiveMinSales = 5, positiveMinDisplays = 2;
    /** Global multiplier for event duration. */
    public double durationMult = 1.0;
    /** Maximum price (over market) an event visitor accepts. */
    public double visitorPriceCap = 1.15;
    /** Chance of the "they fixed it quickly" review if it was fixed before the customer arrived (default). */
    public double reviewFixedChance = 0.25;
    /** Reward money per owner and day: the lower of moneyCapAbs and moneyCapPct of their average daily revenue (minimum moneyCapFloor). */
    public long moneyCapAbs = 10_000, moneyCapFloor = 500;
    public double moneyCapPct = 0.10;
    /** Weekly cap = this x the daily cap. */
    public double weeklyCapMult = 3.0;
    /** Maximum tip on a sale. */
    public double tipMaxPct = 0.10;
    /** Notifications (toast) to the owner when an event starts / is fixed. */
    public boolean toasts = true;
    /** Dimensions without events (ids, e.g. "minecraft:the_nether"). */
    public List<String> dimensionBlacklist = new ArrayList<>();
    /** Per-event settings: {"tcgshop:pipe_leak": {"enabled": false, "weight": 0.5, "durationMult": 1.0, "cooldownDays": 8}}. */
    public Map<String, EventOverride> overrides = new LinkedHashMap<>();
    /** Global caps of the summed modifiers. */
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

    // ------------------------------------------------------------------ queries

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

    // ------------------------------------------------------------------ loading

    public static void load() {
        EventsConfig c = null;
        try {
            if (Files.exists(FILE)) try (Reader r = Files.newBufferedReader(FILE)) { c = GSON.fromJson(r, EventsConfig.class); }
        } catch (Exception e) {
            TcgShop.LOGGER.error("Could not read {} (using default values)", FILE, e);
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
            TcgShop.LOGGER.error("Could not save {}", FILE, e);
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
        _help.put("aplicar", "Edit this file and run /tcgshop reload (operator). Events are defined in data/<ns>/tcgshop_events/*.json (datapacks can add, change or disable events with {\"enabled\": false}).");
        _help.put("enabled", "Master switch for random events. /tcgshop event start works even when it is off.");
        _help.put("dailyChance", "Chance per owner per Minecraft day that some event happens (0.10 = 10%). One fixed roll per owner per day: reloading the world does not change it.");
        _help.put("maxPerOwnerPerDay", "Maximum natural events per owner per day, across all their shops.");
        _help.put("ownerRange", "Only starts if the owner is online and within this distance of the shop; if they never show up that day it is lost (players who are not playing are never punished).");
        _help.put("shiftStart/shiftEnd", "Time of day (0-24000 ticks; 0 = 6:00) between which the event starts: a random moment of the day shift.");
        _help.put("categoryWeights/tierWeights", "Split when an event rolls: category (positive/neutral/negative) and rarity (common/uncommon/rare/extraordinary).");
        _help.put("tierCooldownDays", "Days before the same event can repeat, per rarity.");
        _help.put("deckPresence", "Chance that each event exists in this world, per rarity (rolled once per world; /tcgshop event deck reroll).");
        _help.put("historyPenalty/historySize", "Weight of an event that was among the owner's last N (0.25 = 4 times less likely). The same template never happens twice in a row.");
        _help.put("graceMinSales/graceMinDays", "A new shop gets no negative events until it has this many sales and days of age.");
        _help.put("positiveMinSales/positiveMinDisplays", "Minimum sales and displays for positive events (opening many empty shops gives no rewards).");
        _help.put("durationMult", "Multiplier for the duration of all events.");
        _help.put("visitorPriceCap", "Maximum price, relative to market, that an event visitor pays (collector, legendary customer...).");
        _help.put("reviewFixedChance", "Chance of the review 'there was a problem but they fixed it fast' if you fixed it before that customer arrived.");
        _help.put("moneyCapAbs/moneyCapFloor/moneyCapPct/weeklyCapMult", "Maximum reward money per owner: per day the lower of moneyCapAbs and moneyCapPct of your average daily income (at least moneyCapFloor); per week weeklyCapMult times that.");
        _help.put("tipMaxPct", "Maximum tip on a sale ('generous tip' event).");
        _help.put("toasts", "On-screen notices to the owner when an event starts or is fixed.");
        _help.put("dimensionBlacklist", "Dimensions without events.");
        _help.put("overrides", "Per-event settings: enabled, weight (multiplies the weight), durationMult and cooldownDays. E.g.: {\"tcgshop:rumor\": {\"weight\": 0.5}}.");
        _help.put("clamps", "Caps on the sum of modifiers from active events (e.g. revenueMultMax never goes above 1.15).");
    }
}

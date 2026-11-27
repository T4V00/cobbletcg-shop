package dev.tcgshop.events;

import dev.masterball.api.platform.Platform;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.tcgshop.shop.ShopTier;
import dev.tcgshop.shop.ShopZones;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;

/**
 * Conditions of an event (the JSON "conditions" object). All optional. The history ones (cooldownDays,
 * maxPerWeek) and exclusiveGroup are checked by EventManager against the owner's state.
 *
 * nearbyPokemon uses a pluggable predicate ({@link #setNearbyPokemon}) registered by the actor module
 * (it can read the species via reflection). Without the module: with no species, any nearby "cobblemon:" entity counts; with
 * species, the condition fails (better not to fire an event for a Pokémon that cannot be checked).
 */
public record EventConditions(List<String> biomes, List<String> notBiomes, Set<String> weather, int[] time,
                              int repMin, int repMax, int minTier, int maxTier, Set<String> requires, int minDisplays, int minSales,
                              int minRegulars, double minStars, int minDecor, Set<String> flags, Set<String> notFlags,
                              double minTemp, double maxTemp, NearbyPokemon nearbyPokemon, String exclusiveGroup,
                              int cooldownDays, int maxPerWeek) {

    public record NearbyPokemon(List<String> species, int radius, int min) {}

    /** Nearby-Pokémon predicate (registered by ActorModule). Empty species = any. */
    @FunctionalInterface
    public interface NearbyPokemonPredicate {
        boolean test(ServerLevel world, ShopZones.Zone zone, List<String> species, int radius, int min);
    }

    private static NearbyPokemonPredicate nearby = EventConditions::defaultNearby;

    /**
     * Time of day (0-24000) used to evaluate "time" instead of the world's; -1 = the real one. Only used by the
     * scheduler (server thread) to see whether an event fits later today (night events).
     */
    static int timeOverride = -1;

    public static void setNearbyPokemon(NearbyPokemonPredicate p) { nearby = p == null ? EventConditions::defaultNearby : p; }

    /** Default minimum tier of an event: Wood, so the Paper stall gets no random events unless an event sets "minTier": "paper". */
    public static final String DEFAULT_MIN_TIER = "wood";

    /** No conditions at all (variants without their own "conditions": they only narrow the event's, so no tier floor). */
    public static final EventConditions ALWAYS = parseVariant(new JsonObject());

    private static boolean defaultNearby(ServerLevel world, ShopZones.Zone z, List<String> species, int radius, int min) {
        if (!species.isEmpty()) return false;
        int n = 0;
        for (net.minecraft.world.entity.Entity e : world.getEntities(null, z.box().inflate(radius))) {
            if (e.getTags().contains(EventManager.EVENT_TAG)) continue;
            if (net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getNamespace().equals("cobblemon") && ++n >= min) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ parsing

    /** An event's own conditions: without "minTier" the event needs a Wood shop or higher (the Paper stall gets none). */
    public static EventConditions parse(JsonObject o) {
        return parse(o, DEFAULT_MIN_TIER);
    }

    /**
     * A variant's conditions: they are tested on top of the event's, so without "minTier" they add no tier floor
     * (an event that opts into the Paper stall keeps all its variants there).
     */
    public static EventConditions parseVariant(JsonObject o) {
        return parse(o, ShopTier.PAPER.key);
    }

    private static EventConditions parse(JsonObject o, String defaultMinTier) {
        int[] time = null;
        if (o.has("time")) {
            JsonArray a = o.getAsJsonArray("time");
            time = new int[]{Math.floorMod(a.get(0).getAsInt(), 24000), Math.floorMod(a.get(1).getAsInt(), 24000)};
        }
        int repMin = 0, repMax = 100;
        if (o.has("rep")) { long[] r = EventDef.range(o.get("rep"), 0); repMin = (int) r[0]; repMax = (int) Math.max(r[0], r[1]); }
        NearbyPokemon np = null;
        if (o.has("nearbyPokemon")) {
            JsonObject n = o.getAsJsonObject("nearbyPokemon");
            np = new NearbyPokemon(strings(n, "species"), Math.max(1, Math.min(64, EventDef.integer(n, "radius", 24))), Math.max(1, EventDef.integer(n, "min", 1)));
        }
        double minTemp = EventDef.dbl(o, "minTemp", -10), maxTemp = EventDef.dbl(o, "maxTemp", 10);
        return new EventConditions(strings(o, "biomes"), strings(o, "notBiomes"), lower(strings(o, "weather")), time, repMin, repMax,
                tier(EventDef.str(o, "minTier", defaultMinTier)), tier(EventDef.str(o, "maxTier", "netherite")), lower(strings(o, "requires")),
                EventDef.integer(o, "minDisplays", 0), EventDef.integer(o, "minSales", 0), EventDef.integer(o, "minRegulars", 0),
                EventDef.dbl(o, "minStars", 0), EventDef.integer(o, "minDecor", 0), lower(strings(o, "flags")), lower(strings(o, "notFlags")),
                minTemp, maxTemp, np, EventDef.str(o, "exclusiveGroup", ""), EventDef.integer(o, "cooldownDays", -1), EventDef.integer(o, "maxPerWeek", 0));
    }

    private static int tier(String key) {
        ShopTier t = ShopTier.byKey(key); // 1.0: ranks (Paper = 0 ... Netherite = 11), compared with Zone.tier
        return t != null ? t.rank() : 0;
    }

    private static List<String> strings(JsonObject o, String k) {
        if (!o.has(k)) return List.of();
        List<String> out = new ArrayList<>();
        JsonElement e = o.get(k);
        if (e.isJsonArray()) for (JsonElement x : e.getAsJsonArray()) out.add(x.getAsString());
        else out.add(e.getAsString());
        return Collections.unmodifiableList(out);
    }

    private static Set<String> lower(List<String> in) {
        Set<String> s = new HashSet<>();
        for (String x : in) s.add(x.toLowerCase(Locale.ROOT));
        return Collections.unmodifiableSet(s);
    }

    // ------------------------------------------------------------------ evaluation

    /**
     * Are they met in this shop right now? why (optional) receives the reason for the first failure (for /tcgshop event and eventtest).
     * extraRequires: implicit requirements of the effects (e.g. "cobblemon" for actors).
     */
    public boolean test(ServerLevel world, ShopZones.Zone z, Set<String> extraRequires, StringBuilder why) {
        Holder<Biome> biome = world.getBiome(z.core);
        if (!biomes.isEmpty() && biomes.stream().noneMatch(b -> biomeMatches(biome, b))) return fail(why, "biome");
        if (notBiomes.stream().anyMatch(b -> biomeMatches(biome, b))) return fail(why, "notBiome");
        float temp = biome.value().getBaseTemperature();
        if (temp < minTemp || temp > maxTemp) return fail(why, "temperature " + temp);
        if (!weather.isEmpty()) {
            String w = world.isThundering() ? "thunder" : world.isRaining() && biome.value().hasPrecipitation() ? "rain" : "clear";
            boolean ok = weather.contains(w) || (w.equals("thunder") && weather.contains("rain")) || (!w.equals("clear") && weather.contains("wet"));
            if (!ok) return fail(why, "weather " + w);
        }
        if (time != null) {
            int t = timeOverride >= 0 ? timeOverride : (int) Math.floorMod(world.getDayTime(), 24000L);
            boolean in = time[0] <= time[1] ? t >= time[0] && t <= time[1] : t >= time[0] || t <= time[1];
            if (!in) return fail(why, "time " + t);
        }
        int rep = z.reputation;
        if (rep < repMin || rep > repMax) return fail(why, "rep " + rep);
        if (z.tier < minTier || z.tier > maxTier) return fail(why, "tier");
        if (z.displays < minDisplays) return fail(why, "displays " + z.displays);
        if (z.sales < minSales) return fail(why, "sales " + z.sales);
        if (z.regulars.size() < minRegulars) return fail(why, "regulars");
        if (minStars > 0 && (z.reviews.size() < 3 || z.avgStars() < minStars)) return fail(why, "stars");
        if (z.decor < minDecor) return fail(why, "decor " + z.decor);
        for (String f : flags) if (!z.flags.contains(f)) return fail(why, "flag " + f);
        for (String f : notFlags) if (z.flags.contains(f)) return fail(why, "notFlag " + f);
        Set<String> req = new HashSet<>(requires);
        if (extraRequires != null) req.addAll(extraRequires);
        for (String r : req) if (!has(world, z, r)) return fail(why, "requires " + r);
        if (nearbyPokemon != null && !nearby.test(world, z, nearbyPokemon.species(), nearbyPokemon.radius(), nearbyPokemon.min()))
            return fail(why, "nearbyPokemon");
        return true;
    }

    private static boolean has(ServerLevel world, ShopZones.Zone z, String req) {
        return switch (req) {
            case "cashier" -> z.cashier || z.cashier2;
            case "guard" -> z.guard;
            case "no_guard" -> !z.guard;
            case "unguarded" -> !z.guard && z.detectors < 2; // neither a guard nor 2 detectors ("guarded" goal not met)
            case "overpriced" -> z.factors.getOrDefault("price", 0) < 0; // "price_ok" goal not met
            case "staff_slot" -> !z.cashier || !z.cashier2 || !z.guard;
            case "register" -> z.events.hasRegister;
            case "door" -> !z.events.doors.isEmpty();
            case "playtable" -> z.playTables > 0;
            case "actors" -> dev.tcgshop.events.actor.ActorModule.available();
            case "cobblemon" -> Platform.INSTANCE.isModLoaded("cobblemon");
            case "grading_order" -> dev.tcgshop.grading.GradingService.hasPending(world.getServer(), z.owner);
            case "clean" -> z.smell == 0 && dev.tcgshop.shop.Cleanliness.score(z) >= 90 && !z.flags.contains("hostile"); // 1.1: droppings, graffiti and grime count too
            case "no_cashier" -> !z.cashier && !z.cashier2;
            default -> true;
        };
    }

    private static boolean biomeMatches(Holder<Biome> biome, String spec) {
        if (spec.startsWith("#")) {
            ResourceLocation id = ResourceLocation.tryParse(spec.substring(1));
            return id != null && biome.is(TagKey.create(Registries.BIOME, id));
        }
        ResourceLocation id = ResourceLocation.tryParse(spec);
        return id != null && biome.is(id);
    }

    private static boolean fail(StringBuilder why, String s) {
        if (why != null) why.append(s);
        return false;
    }
}

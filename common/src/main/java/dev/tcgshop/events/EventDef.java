package dev.tcgshop.events;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Immutable definition of a random event, read from data/&lt;ns&gt;/tcgshop_events/&lt;path&gt;.json (EventRegistry).
 * An event has NO code of its own: it only composes effects ({@link EventEffect}) registered by type in {@link EffectTypes}.
 * The full format is in events/EFFECTS.md.
 */
public record EventDef(String id, Category category, Tier tier, String template, double weight,
                       long durMin, long durMax, boolean untilDayEnd, EventConditions conditions,
                       List<EventEffect> effects, List<Variant> variants, Review review, Notify notice,
                       String chainNext, int chainWithinDays, double chainMult) {

    public enum Category {
        POSITIVE, NEUTRAL, NEGATIVE;
        public String key() { return name().toLowerCase(Locale.ROOT); }
        public static Category parse(String s) { return valueOf(s.trim().toUpperCase(Locale.ROOT)); }
    }

    public enum Tier {
        COMMON, UNCOMMON, RARE, EXTRAORDINARY;
        public String key() { return name().toLowerCase(Locale.ROOT); }
        public static Tier parse(String s) { return valueOf(s.trim().toUpperCase(Locale.ROOT)); }
    }

    /** Weighted variant: adds (or replaces, replace=true) effects; may have its own conditions. */
    public record Variant(String key, int weight, boolean replace, List<EventEffect> effects, EventConditions conditions) {}

    /**
     * Review memory: shared tag (line family tcgshop.reviewtext.ev_&lt;tag&gt;.*), chance that a customer
     * who was present mentions it, mood added/subtracted, and chance of the "_fixed" review if it was fixed earlier.
     */
    public record Review(String tag, double chance, int mood, double fixedChance) {
        public static final Review NONE = new Review("", 0, 0, 0);
        public boolean present() { return !tag.isEmpty(); }
    }

    /** Notice to the owner (toast) and lines from customers who enter while it is active (tcgshop.evline.&lt;lines&gt;.&lt;n&gt;). */
    public record Notify(boolean toast, String lines, int count, double chance) {
        public static final Notify DEFAULT = new Notify(true, "", 0, 0);
    }

    // ------------------------------------------------------------------ helpers

    /** Language key prefix: tcgshop.event.&lt;path&gt; (with the namespace if it is not tcgshop). */
    public String langKey() { return langKey(id); }

    public static String langKey(String id) {
        int c = id.indexOf(':');
        String ns = c < 0 ? "tcgshop" : id.substring(0, c), path = c < 0 ? id : id.substring(c + 1);
        return "tcgshop.event." + (ns.equals("tcgshop") ? "" : ns + ".") + path.replace('/', '.');
    }

    /** Effects of a specific variant (base + variant, or only the variant if replace). */
    public List<EventEffect> effectsFor(String variantKey) {
        if (variantKey == null || variantKey.isEmpty()) return effects;
        for (Variant v : variants) {
            if (!v.key().equals(variantKey)) continue;
            if (v.replace()) return v.effects();
            List<EventEffect> out = new ArrayList<>(effects);
            out.addAll(v.effects());
            return out;
        }
        return effects;
    }

    /** Does the player have at least one way to fix it? (mandatory for negative ones) */
    public boolean hasFix() {
        for (EventEffect e : effects) if (e.providesFix()) return true;
        if (variants.isEmpty()) return false;
        // with variants: ALL of them must be fixable
        for (Variant v : variants) {
            boolean ok = false;
            for (EventEffect e : effectsFor(v.key())) if (e.providesFix()) { ok = true; break; }
            if (!ok) return false;
        }
        return true;
    }

    public boolean negative() { return category == Category.NEGATIVE; }

    // ------------------------------------------------------------------ parsing

    /** Reads a definition. Throws IllegalArgumentException with a clear message if something does not add up. */
    public static EventDef parse(String id, JsonObject o) {
        Category cat = Category.parse(str(o, "category", "neutral"));
        Tier tier = Tier.parse(str(o, "tier", "common"));
        String template = str(o, "template", id);
        double weight = o.has("weight") ? Math.max(0, o.get("weight").getAsDouble()) : 1.0;
        long dMin = 6000, dMax = 6000;
        boolean dayEnd = false;
        if (o.has("duration")) {
            JsonElement d = o.get("duration");
            if (d.isJsonPrimitive() && d.getAsJsonPrimitive().isString()) dayEnd = d.getAsString().equalsIgnoreCase("day");
            else if (d.isJsonPrimitive()) dMin = dMax = d.getAsLong();
            else if (d.isJsonObject()) {
                JsonObject dob = d.getAsJsonObject();
                if (str(dob, "type", "").equalsIgnoreCase("day")) dayEnd = true;
                if (dob.has("ticks")) { long[] r = range(dob.get("ticks"), 6000); dMin = r[0]; dMax = r[1]; }
            } else if (d.isJsonArray()) { long[] r = range(d, 6000); dMin = r[0]; dMax = r[1]; }
        }
        dMin = Math.max(200, Math.min(24000, dMin));
        dMax = Math.max(dMin, Math.min(24000, dMax));
        EventConditions cond = EventConditions.parse(o.has("conditions") ? o.getAsJsonObject("conditions") : new JsonObject());
        List<EventEffect> effects = parseEffects(id, o.has("effects") ? o.getAsJsonArray("effects") : new JsonArray());
        List<Variant> variants = new ArrayList<>();
        if (o.has("variants")) {
            for (JsonElement ve : o.getAsJsonArray("variants")) {
                JsonObject v = ve.getAsJsonObject();
                String key = str(v, "key", "v" + variants.size());
                int w = v.has("weight") ? Math.max(0, v.get("weight").getAsInt()) : 1;
                boolean replace = v.has("replace") && v.get("replace").getAsBoolean();
                List<EventEffect> ve2 = parseEffects(id + "#" + key, v.has("effects") ? v.getAsJsonArray("effects") : new JsonArray());
                EventConditions vc = v.has("conditions") ? EventConditions.parseVariant(v.getAsJsonObject("conditions")) : EventConditions.ALWAYS;
                variants.add(new Variant(key, w, replace, Collections.unmodifiableList(ve2), vc));
            }
        }
        Review review = Review.NONE;
        if (o.has("review")) {
            JsonObject r = o.getAsJsonObject("review");
            int defMood = cat == Category.NEGATIVE ? -1 : cat == Category.POSITIVE ? 1 : 0;
            review = new Review(str(r, "tag", ""), clamp(dbl(r, "chance", 0.5), 0, 1),
                    Math.max(-3, Math.min(3, r.has("mood") ? r.get("mood").getAsInt() : defMood)), clamp(dbl(r, "fixedChance", -1), -1, 1));
        }
        Notify notice = Notify.DEFAULT;
        if (o.has("notify")) {
            JsonObject n = o.getAsJsonObject("notify");
            notice = new Notify(!n.has("toast") || n.get("toast").getAsBoolean(), str(n, "lines", ""),
                    n.has("count") ? Math.max(0, n.get("count").getAsInt()) : 0, clamp(dbl(n, "chance", 0.35), 0, 1));
        }
        String chain = "";
        int within = 0;
        double mult = 1;
        if (o.has("chain")) {
            JsonObject c = o.getAsJsonObject("chain");
            chain = str(c, "next", "");
            within = c.has("withinDays") ? Math.max(1, c.get("withinDays").getAsInt()) : 3;
            mult = clamp(dbl(c, "weightMult", 3), 1, 20);
        }
        return new EventDef(id, cat, tier, template, weight, dMin, dMax, dayEnd, cond, Collections.unmodifiableList(effects),
                Collections.unmodifiableList(variants), review, notice, chain, within, mult);
    }

    private static List<EventEffect> parseEffects(String id, JsonArray arr) {
        List<EventEffect> out = new ArrayList<>();
        for (JsonElement e : arr) {
            if (!e.isJsonObject()) throw new IllegalArgumentException("effect is not an object");
            JsonObject eo = e.getAsJsonObject();
            String type = str(eo, "type", "");
            EffectTypes.Factory f = EffectTypes.get(type);
            if (f == null) throw new IllegalArgumentException("unknown effect type '" + type + "' (module not loaded?)");
            out.add(f.create(eo, id));
        }
        return out;
    }

    // ------------------------------------------------------------------ JSON utilities (also used by the effects)

    public static String str(JsonObject o, String k, String def) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : def;
    }

    public static double dbl(JsonObject o, String k, double def) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsDouble() : def;
    }

    public static int integer(JsonObject o, String k, int def) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsInt() : def;
    }

    public static boolean bool(JsonObject o, String k, boolean def) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsBoolean() : def;
    }

    /** Number or [min,max] -> {min,max}. */
    public static long[] range(JsonElement e, long def) {
        if (e == null || e.isJsonNull()) return new long[]{def, def};
        if (e.isJsonArray()) {
            JsonArray a = e.getAsJsonArray();
            long lo = a.size() > 0 ? a.get(0).getAsLong() : def, hi = a.size() > 1 ? a.get(1).getAsLong() : lo;
            return new long[]{Math.min(lo, hi), Math.max(lo, hi)};
        }
        long v = e.getAsLong();
        return new long[]{v, v};
    }

    public static double clamp(double v, double lo, double hi) {
        return Double.isNaN(v) ? lo : Math.max(lo, Math.min(hi, v));
    }
}

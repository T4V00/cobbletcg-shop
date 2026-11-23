package dev.tcgshop.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.tcgshop.network.PtcgPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Real TCG client: engine card -> cobbletcg card mapping (for the image), details and League. */
public final class ClientPtcg {
    private static Map<String, String> itemByName;
    /** Card details received in the views (attacks, abilities, texts...). */
    public static final Map<String, JsonObject> DEFS = new HashMap<>();
    public static JsonObject league;
    public static int leagueVersion;

    private ClientPtcg() {}

    @Nullable
    public static String itemId(String fullName) {
        if (itemByName == null) {
            itemByName = new HashMap<>();
            try {
                var res = MinecraftClient.getInstance().getResourceManager().getResource(Identifier.of("tcgshop", "ptcg/cards.json"));
                if (res.isPresent()) try (var r = new InputStreamReader(res.get().getInputStream(), StandardCharsets.UTF_8)) {
                    JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
                    for (Map.Entry<String, JsonElement> e : o.entrySet()) itemByName.putIfAbsent(e.getValue().getAsString(), e.getKey());
                }
            } catch (Exception ignored) {}
        }
        String id = itemByName.get(fullName);
        if (id == null) id = basicEnergyItem(fullName);
        return id;
    }

    private static final Pattern BASIC_ENERGY = Pattern.compile("^(Grass|Fire|Water|Lightning|Psychic|Fighting|Darkness|Metal|Fairy) Energy \\S+$");
    private static Map<String, String> energyByType;

    /** Engine basic Energy (e.g. "Fire Energy SVE") -> real cobbletcg Energy card of the same type (prefers Crown Zenith). */
    @Nullable
    private static String basicEnergyItem(String fullName) {
        Matcher mt = BASIC_ENERGY.matcher(fullName);
        if (!mt.matches()) return null;
        if (energyByType == null) {
            energyByType = new HashMap<>();
            for (Map.Entry<String, String> e : itemByName.entrySet()) {
                Matcher m2 = BASIC_ENERGY.matcher(e.getKey());
                if (!m2.matches()) continue;
                String prev = energyByType.get(m2.group(1));
                if (prev == null || e.getKey().endsWith(" CRZ")) energyByType.put(m2.group(1), e.getValue());
            }
        }
        return energyByType.get(mt.group(1));
    }

    public static ItemStack stack(String fullName) {
        String id = itemId(fullName);
        Identifier i = id == null ? null : Identifier.tryParse(id);
        return i != null && Registries.ITEM.containsId(i) ? new ItemStack(Registries.ITEM.get(i)) : ItemStack.EMPTY;
    }

    public static void absorbDefs(JsonObject view) {
        if (view.has("defs") && view.get("defs").isJsonObject())
            for (Map.Entry<String, JsonElement> e : view.getAsJsonObject("defs").entrySet()) DEFS.put(e.getKey(), e.getValue().getAsJsonObject());
    }

    @Nullable
    public static JsonObject def(String fullName) {
        JsonObject d = DEFS.get(fullName);
        if (d == null && league != null && league.has("pool"))
            for (JsonElement e : league.getAsJsonArray("pool")) if (e.getAsJsonObject().get("f").getAsString().equals(fullName)) return e.getAsJsonObject();
        return d;
    }

    // ------------------------------------------------------------------ card texts in the game language

    private static String i18nLang;
    private static Map<String, JsonObject> i18n = Map.of();

    /** Card texts in the game language (assets/tcgshop/ptcg/i18n/<language>.json, from TCGdex), or null. */
    @Nullable
    private static JsonObject localized(String fullName) {
        String code = MinecraftClient.getInstance().getLanguageManager().getLanguage();
        String lang = code == null ? "en" : code.substring(0, Math.min(2, code.length()));
        if (!lang.equals(i18nLang)) {
            i18nLang = lang;
            Map<String, JsonObject> m = new HashMap<>();
            if (!lang.equals("en")) try {
                var res = MinecraftClient.getInstance().getResourceManager().getResource(Identifier.of("tcgshop", "ptcg/i18n/" + lang + ".json"));
                if (res.isPresent()) try (var r = new InputStreamReader(res.get().getInputStream(), StandardCharsets.UTF_8)) {
                    for (Map.Entry<String, JsonElement> e : JsonParser.parseReader(r).getAsJsonObject().entrySet())
                        m.put(e.getKey(), e.getValue().getAsJsonObject());
                }
            } catch (Exception ignored) {}
            i18n = m;
        }
        return i18n.get(fullName);
    }

    private static String pick(@Nullable JsonObject loc, String arr, int idx, int part, int expectedSize, String fallback) {
        if (loc == null || !loc.has(arr)) return fallback;
        var a = loc.getAsJsonArray(arr);
        if (a.size() != expectedSize || idx >= a.size()) return fallback; // if they don't match, better to use the original
        String s = a.get(idx).getAsJsonArray().get(part).getAsString();
        return s.isEmpty() && part == 1 ? fallback.isEmpty() ? "" : s : s;
    }

    /** Card name in the game language. */
    public static String cardName(String fullName, String fallback) {
        JsonObject l = localized(fullName);
        return l != null && l.has("n") && !l.get("n").getAsString().isEmpty() ? l.get("n").getAsString() : fallback;
    }

    public static String attackName(String fullName, int idx, int count, String fallback) { return pick(localized(fullName), "a", idx, 0, count, fallback); }

    public static String attackText(String fullName, int idx, int count, String fallback) { return pick(localized(fullName), "a", idx, 1, count, fallback); }

    public static String powerName(String fullName, int idx, int count, String fallback) { return pick(localized(fullName), "p", idx, 0, count, fallback); }

    public static String powerText(String fullName, int idx, int count, String fallback) { return pick(localized(fullName), "p", idx, 1, count, fallback); }

    public static String cardText(String fullName, String fallback) {
        JsonObject l = localized(fullName);
        return l != null && l.has("t") ? l.get("t").getAsString() : fallback;
    }

    // ------------------------------------------------------------------ real energy icons

    private static final String[] ENERGY_NAMES = {null, "Grass", "Fire", "Water", "Lightning", "Psychic", "Fighting", "Darkness", "Metal"};
    private static final Map<Integer, Identifier> ENERGY_TEX = new HashMap<>();

    /** Texture of the real basic Energy card (cobbletcg) of the type, to crop its orb as an icon; null if none. */
    @Nullable
    public static Identifier energyTexture(int type) {
        if (type < 1 || type >= ENERGY_NAMES.length) return null;
        return ENERGY_TEX.computeIfAbsent(type, t -> {
            String id = itemId(ENERGY_NAMES[t] + " Energy SVE");
            Identifier iid = id == null ? null : Identifier.tryParse(id);
            if (iid == null) return null;
            Identifier tex = dev.masterball.api.card.CardInfo.texture(iid);
            return dev.tcgshop.client.screen.Draw.textureExists(tex) ? tex : null;
        });
    }

    private static final Pattern PARAM = Pattern.compile("\\{\\{\\s*(\\w+)\\s*}}");

    /** Translates an engine key (message or log) and fills in its {{ parameters }}. */
    public static String tr(String prefix, String key, @Nullable JsonObject params) {
        String full = "tcgshop.ptcg." + prefix + "." + key;
        String s = Text.translatable(full).getString();
        if (s.equals(full)) s = key.replace("LOG_", "").replace('_', ' ').toLowerCase();
        Matcher mt = PARAM.matcher(s);
        StringBuilder sb = new StringBuilder();
        while (mt.find()) {
            String v = params != null && params.has(mt.group(1)) ? params.get(mt.group(1)).getAsString() : "";
            mt.appendReplacement(sb, Matcher.quoteReplacement(v));
        }
        mt.appendTail(sb);
        return sb.toString();
    }

    public static void send(JsonObject action) {
        if (ClientPlayNetworking.canSend(PtcgPayloads.Action.ID)) ClientPlayNetworking.send(new PtcgPayloads.Action(action.toString()));
    }

    public static void leagueAction(JsonObject a) {
        if (ClientPlayNetworking.canSend(PtcgPayloads.LeagueAction.ID)) ClientPlayNetworking.send(new PtcgPayloads.LeagueAction(a.toString()));
    }

    public static void applyLeague(String json) {
        try {
            league = JsonParser.parseString(json).getAsJsonObject();
            leagueVersion++;
        } catch (Exception ignored) {}
    }

    // ------------------------------------------------------------------ energy types

    private static final int[] TYPE_COLORS = {0xFF9A9A9A, 0xFF5BAE3C, 0xFFE04A2A, 0xFF3D8AE0, 0xFFF2CC2E, 0xFF9A5CC6, 0xFFC0703A, 0xFF3A3640,
            0xFF9AA4B0, 0xFFEFEFEF, 0xFFE882B4, 0xFFC9A227};
    private static final String[] TYPE_LETTER = {"*", "G", "R", "W", "L", "P", "F", "D", "M", "C", "Y", "N"};

    public static int typeColor(int t) { return t >= 0 && t < TYPE_COLORS.length ? TYPE_COLORS[t] : 0xFF888888; }

    public static String typeLetter(int t) { return t >= 0 && t < TYPE_LETTER.length ? TYPE_LETTER[t] : "?"; }
}

package dev.tcgshop.client.screen.league;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.tcgshop.client.ClientPtcg;
import dev.tcgshop.client.binder.StylePainter;
import dev.tcgshop.client.binder.Ui;
import dev.tcgshop.client.screen.Draw;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.text.Normalizer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import static dev.tcgshop.client.screen.Draw.withAlpha;

/**
 * TCG League deck editor (phone app, "My deck" tab).
 *  · Left: your collection as cards, with name search, filters (Pokémon / Trainer / Energy, type,
 *    set, available only) and sort order. Click = add · Shift + click = as many copies as fit · right click = remove.
 *  · Right: summary (split, stage curve, Trainer and Energy types), live validation warnings
 *    (60 cards, 4-copy rule, Basic Pokémon, Radiant, copies you are missing, evolutions without their previous stage)
 *    plus the engine's result (validateDeck: ACE SPEC, banned combinations, format) and the deck list
 *    grouped with − / + buttons.
 *  · Top: live 60 counter and Save · Check · Autocomplete (the engine's buildDeck around what you have
 *    already added) · +Energy (fills with Basic Energy of your types) · Undo · Clear.
 * Does not use the phone's areas: keeps its own click zones (left/right button and Shift).
 */
public final class DeckBuilderPanel {
    /** What the phone lends the editor: the editable deck list, the "unsaved" state and sending to the server. */
    public interface Host {
        List<String> deck();

        boolean dirty();

        void setDirty(boolean dirty);

        void send(String action, @Nullable List<String> deck);
    }

    private interface Click { void run(int button, boolean shift); }

    private record Hit(int x, int y, int w, int h, Click action) {}

    private record Issue(int level, Component text) {} // 0 = ok, 1 = tip, 2 = warning, 3 = error

    private static final int SURF = 0xF0171A26, SURF2 = 0xF01F2333, LINE = 0xFF2C3149;
    private static final int DECK_SIZE = 60, MAX_COPIES = 4, UNDO_MAX = 40;
    private static final String[] CATS = {"all", "pokemon", "trainer", "energy"};
    private static final String[] SORTS = {"stage", "name", "type", "hp", "owned", "set"};
    /** Types (engine CardType) that can be filtered: Grass…Metal, Colorless, Fairy, Dragon. */
    private static final int[] TYPES = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11};
    private static final int COL_P = 0xFFE0604A, COL_T = 0xFF4FA3E8, COL_E = 0xFFF2CC2E;
    /** Engine stage: 2 = Basic, 3 = Stage 1, 4 = Stage 2 (the rest: VMAX, VSTAR, BREAK, Restored...). */
    private static final int STAGE_BASIC = 2, STAGE_1 = 3, STAGE_2 = 4;

    private final Host host;
    private final List<Hit> hits = new ArrayList<>();
    private final Deque<List<String>> undo = new ArrayDeque<>();

    // filters
    private String query = "";
    private boolean focused;
    private int cat, sort, typeFilter = -1;
    private String setFilter = "";
    private boolean onlyAvail;
    private double poolScroll, deckScroll;

    // zones of this frame (mouse wheel)
    private int poolX, poolY, poolW, poolH, deckX, deckY, deckW, deckH;
    private long lastRender;
    private List<FormattedCharSequence> tooltip;

    // pending save: the deck stays "unsaved" until the server confirms it (SAVED) or rejects it
    private @Nullable List<String> pendingSave;
    private @Nullable JsonObject saveSentOn; // sync present when Save was pressed (its reply will be a different one)
    private long saveSentAt;
    private String saveError = ""; // engine's reason if it rejected the last Save (cleared on edit)
    // fingerprint of the deck sent with Check: a result for another deck (late reply) does not count
    private int checkHash;
    private boolean checkSent;

    // collection index (rebuilt when a new sync arrives)
    private JsonObject indexed;
    private final Map<String, JsonObject> info = new HashMap<>();
    private final Map<String, Integer> owned = new HashMap<>();
    private final Set<String> basicEnergy = new HashSet<>();
    private final Map<Integer, String> energyByType = new HashMap<>();
    private final List<String> sets = new ArrayList<>();
    private final Map<String, String> searchKey = new HashMap<>();
    // cached filtered list
    private String filterKey = "";
    private List<String> filtered = new ArrayList<>();

    public DeckBuilderPanel(Host host) {
        this.host = host;
    }

    private static Font tr() { return Minecraft.getInstance().font; }

    private static int gi(@Nullable JsonObject o, String k) { return o != null && o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsInt() : 0; }

    private static String gs(@Nullable JsonObject o, String k) { return o != null && o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : ""; }

    // ------------------------------------------------------------------ card data

    private void reindex(JsonObject L) {
        if (L == indexed) return;
        indexed = L;
        info.clear();
        owned.clear();
        basicEnergy.clear();
        energyByType.clear();
        searchKey.clear();
        TreeSet<String> s = new TreeSet<>();
        if (L.has("energies")) for (JsonElement e : L.getAsJsonArray("energies")) {
            JsonObject o = e.getAsJsonObject();
            String f = gs(o, "f");
            info.put(f, o);
            basicEnergy.add(f);
            energyByType.putIfAbsent(gi(o, "type"), f);
        }
        if (L.has("pool")) for (JsonElement e : L.getAsJsonArray("pool")) {
            JsonObject o = e.getAsJsonObject();
            String f = gs(o, "f");
            info.put(f, o);
            owned.put(f, gi(o, "count"));
            if (!gs(o, "s").isEmpty()) s.add(gs(o, "s"));
        }
        sets.clear();
        sets.addAll(s);
        if (!setFilter.isEmpty() && !sets.contains(setFilter)) setFilter = "";
        filterKey = "";
    }

    private boolean isBasicEnergy(String f) { return basicEnergy.contains(f); }

    /** 1 = Pokémon, 2 = Trainer, 3 = Energy. */
    private int kind(String f) {
        if (isBasicEnergy(f)) return 3;
        int st = gi(info.get(f), "st");
        return st >= 1 && st <= 3 ? st : 2;
    }

    /** Card type (CardType) for the filter and colors: the Pokémon's or Basic Energy's type; -1 if it has none. */
    private int type(String f) {
        JsonObject o = info.get(f);
        if (o == null) return -1;
        if (isBasicEnergy(f)) return gi(o, "type");
        if (kind(f) == 1 && o.has("ct")) return gi(o, "ct");
        return -1;
    }

    private String baseName(String f) {
        JsonObject o = info.get(f);
        String n = gs(o, "n");
        if (!n.isEmpty()) return n;
        int sp = f.lastIndexOf(' ');
        return sp > 0 ? f.substring(0, sp) : f;
    }

    private String name(String f) { return ClientPtcg.cardName(f, baseName(f)); }

    private String set(String f) {
        String s = gs(info.get(f), "s");
        if (!s.isEmpty()) return s;
        int sp = f.lastIndexOf(' ');
        return sp > 0 ? f.substring(sp + 1) : "";
    }

    private int owned(String f) { return isBasicEnergy(f) ? 999 : owned.getOrDefault(f, 0); }

    private static String norm(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    private String searchKey(String f) {
        return searchKey.computeIfAbsent(f, k -> norm(name(k) + " " + baseName(k) + " " + set(k) + " " + gs(info.get(k), "num")));
    }

    // ------------------------------------------------------------------ deck counts

    private final class Stats {
        final Map<String, Integer> byCard = new LinkedHashMap<>();
        final Map<String, Integer> byName = new HashMap<>();
        int n, pokemon, trainer, energy, basics, stage1, stage2, otherStage, radiant;
        final int[] trainerType = new int[4]; // Item, Supporter, Stadium, Tool
        final Map<Integer, Integer> energyType = new LinkedHashMap<>();
        final Map<Integer, Integer> pokemonType = new HashMap<>();
        final Set<String> names = new HashSet<>();

        Stats(List<String> deck) {
            for (String f : deck) {
                n++;
                byCard.merge(f, 1, Integer::sum);
                JsonObject o = info.get(f);
                names.add(baseName(f));
                if (!isBasicEnergy(f)) byName.merge(baseName(f), 1, Integer::sum);
                switch (kind(f)) {
                    case 1 -> {
                        pokemon++;
                        int stage = gi(o, "stage");
                        if (stage == STAGE_BASIC) basics++;
                        else if (stage == STAGE_1) stage1++;
                        else if (stage == STAGE_2) stage2++;
                        else otherStage++;
                        if (hasTag(o, "Radiant")) radiant++;
                        int t = type(f);
                        if (t >= 0) pokemonType.merge(t, 1, Integer::sum);
                    }
                    case 2 -> {
                        trainer++;
                        int tt = gi(o, "tt");
                        if (tt >= 0 && tt < 4) trainerType[tt]++;
                    }
                    default -> {
                        energy++;
                        int t = isBasicEnergy(f) ? type(f) : -1;
                        energyType.merge(t, 1, Integer::sum);
                    }
                }
            }
        }

        int card(String f) { return byCard.getOrDefault(f, 0); }

        int nameCount(String f) { return isBasicEnergy(f) ? 0 : byName.getOrDefault(baseName(f), 0); }
    }

    private static boolean hasTag(@Nullable JsonObject o, String tag) {
        if (o == null || !o.has("ex") || !o.get("ex").isJsonArray()) return false;
        for (JsonElement e : o.getAsJsonArray("ex")) if (e.getAsString().equals(tag)) return true;
        return false;
    }

    /** Whether one more copy can be added (and if not, why). */
    private @Nullable Component cantAdd(String f, Stats s) {
        if (s.n >= DECK_SIZE) return Component.translatable("tcgshop.league.db.why.full");
        if (isBasicEnergy(f)) return null;
        if (s.card(f) >= owned(f)) return Component.translatable("tcgshop.league.db.why.owned", owned(f));
        if (s.nameCount(f) >= MAX_COPIES) return Component.translatable("tcgshop.league.db.why.four", name(f));
        return null;
    }

    // ------------------------------------------------------------------ deck changes

    private void snapshot() {
        undo.push(new ArrayList<>(host.deck()));
        while (undo.size() > UNDO_MAX) undo.removeLast();
    }

    private void changed(SoundEvent sound, float pitch) {
        host.setDirty(true);
        JsonObject L = ClientPtcg.league;
        if (L != null) L.remove("check"); // the engine's check is no longer valid
        saveError = "";
        filterKey = "";
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, 0.4f));
    }

    private void add(String f, int times) {
        Stats s = new Stats(host.deck());
        int done = 0;
        snapshot();
        for (int i = 0; i < times; i++) {
            if (cantAdd(f, s) != null) break;
            host.deck().add(f);
            s = new Stats(host.deck());
            done++;
        }
        if (done == 0) { undo.pop(); denied(); return; }
        changed(dev.tcgshop.TcgSounds.CARD_SLIDE, 1.3f);
    }

    private void remove(String f, int times) {
        if (!host.deck().contains(f)) return;
        snapshot();
        for (int i = 0; i < times && host.deck().remove(f); i++) { /* removes one copy per iteration */ }
        changed(dev.tcgshop.TcgSounds.CARD_FLIP, 0.9f);
    }

    private void clearAll() {
        if (host.deck().isEmpty()) return;
        snapshot();
        host.deck().clear();
        changed(dev.tcgshop.TcgSounds.CARD_FLIP, 0.7f);
    }

    private boolean undoLast() {
        if (undo.isEmpty()) return false;
        List<String> prev = undo.pop();
        host.deck().clear();
        host.deck().addAll(prev);
        changed(dev.tcgshop.TcgSounds.CARD_FLIP, 1.1f);
        return true;
    }

    /** Fills the free slots with Basic Energy split by your Pokémon's types (those that have a Basic Energy). */
    private void fillEnergy() {
        Stats s = new Stats(host.deck());
        int free = DECK_SIZE - s.n;
        Map<Integer, Integer> weights = new LinkedHashMap<>();
        for (var e : s.pokemonType.entrySet()) if (energyByType.containsKey(e.getKey())) weights.put(e.getKey(), e.getValue());
        if (free <= 0 || weights.isEmpty()) { denied(); return; }
        int total = 0;
        for (int w : weights.values()) total += w;
        // proportional split using the largest remainder method
        Map<Integer, Integer> give = new LinkedHashMap<>();
        List<double[]> rest = new ArrayList<>();
        int given = 0;
        for (var e : weights.entrySet()) {
            double exact = free * e.getValue() / (double) total;
            int whole = (int) Math.floor(exact);
            give.put(e.getKey(), whole);
            given += whole;
            rest.add(new double[]{e.getKey(), exact - whole});
        }
        rest.sort((a, b) -> Double.compare(b[1], a[1]));
        for (int i = 0; given < free && i < rest.size(); i++, given++) give.merge((int) rest.get(i)[0], 1, Integer::sum);
        snapshot();
        for (var e : give.entrySet()) for (int i = 0; i < e.getValue(); i++) host.deck().add(energyByType.get(e.getKey()));
        changed(dev.tcgshop.TcgSounds.CARD_SLIDE, 1.1f);
    }

    private static void denied() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.playSound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BASS.value(), 0.5f, 0.6f);
    }

    private static void click() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.playSound(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 0.4f, 1.4f);
    }

    // ------------------------------------------------------------------ validation

    /** Order-independent deck fingerprint (the same list in another order is the same deck). */
    private static int deckHash(List<String> deck) {
        List<String> sorted = new ArrayList<>(deck);
        sorted.sort(null);
        return sorted.hashCode();
    }

    /**
     * Whether the Check result in L belongs to the deck you are viewing: the reply carries the list the engine
     * validated ("checked") and it must match the one you sent and the current one; otherwise it is for another deck and is ignored.
     */
    private boolean engineChecked(JsonObject L) {
        if (!host.dirty() || !checkSent || !L.has("check") || !L.get("check").getAsBoolean()) return false;
        if (!L.has("checked") || !L.get("checked").isJsonArray()) return false;
        List<String> c = new ArrayList<>();
        for (JsonElement e : L.getAsJsonArray("checked")) c.add(e.getAsString());
        int h = deckHash(c);
        return h == checkHash && h == deckHash(host.deck());
    }

    private boolean saving() { return pendingSave != null && System.currentTimeMillis() - saveSentAt < 10_000; }

    /** Checks whether the new sync is the reply to the pending Save and, if so, applies it. */
    private void absorbSaveReply(JsonObject L) {
        if (pendingSave == null || L == saveSentOn) return;
        String msg = gs(L, "msg");
        // other replies (periodic sync, Check, Autocomplete, queue...): keep waiting for the Save reply
        if (msg.isEmpty() || msg.equals("CHECK") || msg.startsWith("AUTO") || msg.equals("CLEARED") || msg.equals("QUEUED") || msg.equals("UNQUEUED")) return;
        List<String> sent = pendingSave;
        pendingSave = null;
        saveSentOn = null;
        if (msg.equals("SAVED")) {
            saveError = "";
            // if you edited while the reply was on its way, those changes remain unsaved
            if (deckHash(sent) == deckHash(host.deck())) host.setDirty(false);
        } else {
            saveError = msg; // the engine rejected it: your edited list is kept and the reason is shown
        }
    }

    private List<Issue> issues(JsonObject L, Stats s, boolean checked) {
        List<Issue> out = new ArrayList<>();
        String valid = gs(L, "valid"), msg = gs(L, "msg");
        boolean dirty = host.dirty();
        if (s.n < DECK_SIZE) out.add(new Issue(3, Component.translatable("tcgshop.league.db.err.missing", DECK_SIZE - s.n)));
        else if (s.n > DECK_SIZE) out.add(new Issue(3, Component.translatable("tcgshop.league.db.err.extra", s.n - DECK_SIZE)));
        for (var e : s.byName.entrySet())
            if (e.getValue() > MAX_COPIES) out.add(new Issue(3, Component.translatable("tcgshop.league.db.err.copies", e.getKey(), e.getValue())));
        for (var e : s.byCard.entrySet()) {
            String f = e.getKey();
            if (isBasicEnergy(f)) continue;
            if (!info.containsKey(f)) out.add(new Issue(3, Component.translatable("tcgshop.league.db.err.unknown", f)));
            else if (e.getValue() > owned(f)) out.add(new Issue(3, Component.translatable("tcgshop.league.db.err.owned", name(f), owned(f))));
        }
        if (s.n > 0 && s.basics == 0) out.add(new Issue(3, Component.translatable("tcgshop.league.db.err.basic")));
        if (s.radiant > 1) out.add(new Issue(3, Component.translatable("tcgshop.league.db.err.radiant")));
        // engine result (validateDeck): the only thing that knows about ACE SPEC, Star, Prism and banned combinations
        if (dirty && !saveError.isEmpty()) out.add(new Issue(3, Component.translatable("tcgshop.league.db.save_bad", deckErr(saveError))));
        if (checked) {
            if (valid.isEmpty()) out.add(new Issue(0, Component.translatable("tcgshop.league.db.engine_ok")));
            else out.add(new Issue(3, Component.translatable("tcgshop.league.db.engine_bad", deckErr(valid))));
        } else if (!dirty) {
            if (valid.isEmpty() && s.n == DECK_SIZE) out.add(new Issue(0, Component.translatable(msg.equals("SAVED") ? "tcgshop.league.db.saved_ok" : "tcgshop.league.db.stored_ok")));
            else if (!valid.isEmpty() && !valid.equals("EMPTY")) out.add(new Issue(3, Component.translatable("tcgshop.league.db.engine_bad", deckErr(valid))));
            else if (msg.startsWith("AUTO")) out.add(new Issue(0, Component.translatable("tcgshop.league.db.auto_done")));
        }
        // tips (do not prevent playing)
        int warnEvo = 0;
        for (String f : s.byCard.keySet()) {
            JsonObject o = info.get(f);
            String ev = gs(o, "ev");
            if (kind(f) == 1 && !ev.isEmpty() && !s.names.contains(ev)) {
                if (warnEvo++ < 2) out.add(new Issue(2, Component.translatable("tcgshop.league.db.warn.evo", name(f), ClientPtcg.cardName(ev, ev))));
            }
        }
        if (warnEvo > 2) out.add(new Issue(2, Component.translatable("tcgshop.league.db.warn.evo_more", warnEvo - 2)));
        if (s.pokemon > 0 && s.energy == 0 && s.n >= 20) out.add(new Issue(2, Component.translatable("tcgshop.league.db.warn.no_energy")));
        else if (s.n == DECK_SIZE && s.energy > 0 && s.energy < 8) out.add(new Issue(1, Component.translatable("tcgshop.league.db.warn.few_energy", s.energy)));
        else if (s.n == DECK_SIZE && s.energy > 22) out.add(new Issue(1, Component.translatable("tcgshop.league.db.warn.many_energy", s.energy)));
        if (dirty && !checked && s.n == DECK_SIZE && out.stream().noneMatch(i -> i.level == 3))
            out.add(new Issue(1, Component.translatable("tcgshop.league.db.hint_check")));
        if (dirty && saving()) out.add(new Issue(1, Component.translatable("tcgshop.league.db.saving")));
        else if (dirty) out.add(new Issue(1, Component.translatable("tcgshop.league.deck.unsaved")));
        out.sort(Comparator.comparingInt((Issue i) -> -i.level));
        return out;
    }

    /** Readable engine reason ("ACE_SPEC:Name" -> translated text with the name). */
    private static String deckErr(String code) {
        String[] parts = code.split(":", 2);
        return Component.translatable("tcgshop.ptcg.deck_err." + parts[0], parts.length > 1 ? parts[1] : "").getString();
    }

    private static int issueColor(int level) {
        return switch (level) { case 0 -> 0xFF6BE07B; case 1 -> 0xFF8BC8FF; case 2 -> 0xFFFFC233; default -> 0xFFFF8A80; };
    }

    private static String issueIcon(int level) {
        return switch (level) { case 0 -> "✔"; case 1 -> "ℹ"; case 2 -> "⚠"; default -> "✖"; };
    }

    // ------------------------------------------------------------------ drawing

    /**
     * Draws the editor in the panel (px, py, pw, ph). (sx, sy, sw, sh) is the phone screen, used to place the large
     * card preview.
     */
    public void render(GuiGraphics ctx, JsonObject L, int px, int py, int pw, int ph, int mx, int my, int sx, int sy, int sw, int sh) {
        hits.clear();
        tooltip = null;
        lastRender = System.currentTimeMillis();
        reindex(L);
        absorbSaveReply(L);
        List<String> deck = host.deck();
        Stats s = new Stats(deck);
        boolean checked = engineChecked(L);
        List<Issue> issues = issues(L, s, checked);

        // ---- header: live 60 counter and buttons
        int n = s.n;
        int col = n == DECK_SIZE ? 0xFF6BE07B : n > DECK_SIZE ? 0xFFFF6B6B : 0xFFFFC233;
        ctx.drawString(tr(), Component.literal(n + "/" + DECK_SIZE).withStyle(ChatFormatting.BOLD), px + 2, py + 3, col, true);
        int barX = px + 4 + tr().width(n + "/" + DECK_SIZE) + 4;
        Ui.progress(ctx, barX, py + 4, 52, 6, Math.min(1f, n / (float) DECK_SIZE), col);
        boolean errors = issues.stream().anyMatch(i -> i.level == 3);
        String[] ids = {"save", "check", "auto", "energy", "undo", "clear"};
        Component[] labels = {
                Component.translatable("tcgshop.league.save"),
                Component.translatable("tcgshop.league.check"),
                Component.translatable(n > 0 && n < DECK_SIZE ? "tcgshop.league.db.autocomplete" : "tcgshop.league.auto"),
                Component.translatable("tcgshop.league.db.fill_energy"),
                Component.literal("↶"),
                Component.translatable("tcgshop.league.deck.clear")};
        boolean hasPool = L.has("pool") && L.getAsJsonArray("pool").size() > 0;
        boolean[] enabled = {
                host.dirty() && n == DECK_SIZE && !errors && !saving(),
                n == DECK_SIZE && host.dirty() && !checked,
                hasPool && n < DECK_SIZE,
                n < DECK_SIZE && s.pokemonType.keySet().stream().anyMatch(energyByType::containsKey),
                !undo.isEmpty(),
                n > 0};
        int[] accents = {0xFF3C8A4A, 0xFF8A7420, 0xFF5A4AA0, 0xFF9C7A12, 0xFF4A5470, 0xFF7A2A3A};
        int avail = px + pw - (barX + 58) - 4;
        int natural = 0;
        int[] widths = new int[ids.length];
        for (int i = 0; i < ids.length; i++) { widths[i] = tr().width(labels[i]) + 10; natural += widths[i] + 3; }
        float shrink = natural > avail ? avail / (float) natural : 1f;
        int bx = px + pw;
        for (int i = ids.length - 1; i >= 0; i--) {
            int w = Math.max(14, Math.round(widths[i] * shrink));
            bx -= w + 3;
            Ui.button(ctx, bx, py + 1, w, 13, labels[i], mx, my, enabled[i], accents[i]);
            if (Ui.in(mx, my, bx, py + 1, w, 13)) tip(Component.translatable("tcgshop.league.db.hint." + ids[i]), mx, my);
            if (!enabled[i]) continue;
            String id = ids[i];
            hits.add(new Hit(bx, py + 1, w, 13, (b, sh2) -> {
                if (b != 0) return;
                click();
                switch (id) {
                    case "save" -> {
                        // not marked as saved until the server replies (absorbSaveReply)
                        pendingSave = new ArrayList<>(host.deck());
                        saveSentOn = ClientPtcg.league;
                        saveSentAt = System.currentTimeMillis();
                        saveError = "";
                        host.send("save", new ArrayList<>(pendingSave));
                    }
                    case "check" -> {
                        checkHash = deckHash(host.deck());
                        checkSent = true;
                        host.send("check", new ArrayList<>(host.deck()));
                    }
                    case "auto" -> { snapshot(); host.setDirty(false); host.send("auto", host.deck().size() < DECK_SIZE ? new ArrayList<>(host.deck()) : null); }
                    case "energy" -> fillEnergy();
                    case "undo" -> undoLast();
                    default -> clearAll();
                }
            }));
        }

        int top = py + 17, h = ph - 17;
        int colW = Math.round((pw - 6) * 0.58f), dw = pw - 6 - colW, dx = px + colW + 6;
        String[] hover = {null};
        drawPool(ctx, L, s, px, top, colW, h, mx, my, hover);
        drawDeck(ctx, s, issues, dx, top, dw, h, mx, my, hover);

        // ---- large preview of the card under the mouse
        if (hover[0] != null) {
            String f = hover[0];
            int pwid = Math.min(96, sh / 2), phgt = Math.round(pwid / Draw.CARD_RATIO);
            int prx = mx < px + pw / 2 ? Math.min(sx + sw - pwid - 6, mx + 14) : Math.max(sx + 6, mx - 14 - pwid);
            int pry = Mth.clamp(my - phgt / 2 - 6, sy + 16, sy + sh - phgt - 22);
            ctx.flush(); // 1.0: flush the lists first so their batched text can't end up on top of the preview
            PoseStack m = ctx.pose();
            m.pushPose();
            m.translate(0, 0, 400);
            ctx.fill(prx + 3, pry + 4, prx + pwid + 3, pry + phgt + 4, 0x66000000);
            card(ctx, f, prx, pry, pwid, phgt, 1f);
            // info card: name, HP, set and copies
            JsonObject o = info.get(f);
            String line2 = (kind(f) == 1 && gi(o, "hp") > 0 ? gi(o, "hp") + " " + Component.translatable("tcgshop.league.db.sort.hp").getString() + " · " : "") + set(f) + (gs(o, "num").isEmpty() ? "" : " " + gs(o, "num"));
            String line3 = isBasicEnergy(f) ? Component.translatable("tcgshop.league.db.free_energy").getString()
                    : Component.translatable("tcgshop.league.db.copies", s.card(f), owned(f)).getString();
            int cw = Math.max(pwid, Math.max(tr().width(name(f)), Math.max(tr().width(line2), tr().width(line3))) + 8);
            int cx = Math.min(prx, sx + sw - cw - 6), cy = pry + phgt + 3;
            Ui.rounded(ctx, cx, cy, cw, 31, 4, 0xFF101420);
            Ui.roundedOutline(ctx, cx, cy, cw, 31, 4, 0xFF3A4060);
            ctx.drawString(tr(), name(f), cx + 4, cy + 3, 0xFFFFFFFF, false);
            ctx.drawString(tr(), line2, cx + 4, cy + 12, 0xFFAEB6CC, false);
            ctx.drawString(tr(), line3, cx + 4, cy + 21, 0xFFFFD34D, false);
            m.popPose();
        }
        // the phone draws this inside its scissor: the tooltip is handed to the screen, which paints it at the end
        if (tooltip != null) deferTooltip(tooltip);
    }

    private void tip(Component t, int mx, int my) {
        tooltip = tr().split(t, 170);
    }

    /** Deferred tooltip: Screen.renderWithTooltip draws it after render(), outside the phone's scissor. */
    static void deferTooltip(List<FormattedCharSequence> lines) {
        Screen screen = Minecraft.getInstance().screen;
        if (screen != null) screen.setTooltipForNextRenderPass(lines, DefaultTooltipPositioner.INSTANCE, true);
    }

    /** The card's real image, or a colored card with the name if there is no image. */
    private void card(GuiGraphics ctx, String f, int x, int y, int w, int h, float alpha) {
        ItemStack st = ClientPtcg.stack(f);
        ctx.fill(x, y + 1, x + w + 1, y + h + 2, withAlpha(0xFF000000, 0.35f * alpha));
        if (!st.isEmpty()) {
            Draw.cardFace(ctx, dev.masterball.api.card.CardInfo.texture(dev.masterball.api.card.CardInfo.id(st)), st, x, y, w, h, alpha);
            return;
        }
        int k = kind(f);
        int c = k == 1 ? 0xFFE0A040 : k == 2 ? 0xFF6BC5FF : 0xFFF2CC2E;
        if (type(f) >= 0) c = ClientPtcg.typeColor(type(f));
        ctx.fillGradient(x, y, x + w, y + h, withAlpha(Ui.brighten(c, 50), alpha), withAlpha(Ui.darken(c, 30), alpha));
        Ui.outline(ctx, x, y, w, h, withAlpha(0xFFF4E7A8, alpha));
        String nm = name(f);
        PoseStack m = ctx.pose();
        m.pushPose();
        float sc = Math.max(0.3f, Math.min(0.55f, (w - 3f) / Math.max(1, tr().width(nm))));
        m.translate(x + 1.5f, y + 2, 0);
        m.scale(sc, sc, 1);
        ctx.drawString(tr(), nm, 0, 0, withAlpha(0xFF1A2236, alpha), false);
        m.popPose();
    }

    private static void small(GuiGraphics ctx, String s, float x, float y, float scale, int color) {
        PoseStack m = ctx.pose();
        m.pushPose();
        m.translate(x, y, 0);
        m.scale(scale, scale, 1);
        ctx.drawString(tr(), s, 0, 0, color, false);
        m.popPose();
    }

    private static void small(GuiGraphics ctx, Component s, float x, float y, float scale, int color) {
        PoseStack m = ctx.pose();
        m.pushPose();
        m.translate(x, y, 0);
        m.scale(scale, scale, 1);
        ctx.drawString(tr(), s, 0, 0, color, false);
        m.popPose();
    }

    // ------------------------------------------------------------------ left: collection

    private List<String> filtered(JsonObject L, Stats s) {
        String key = query + "|" + cat + "|" + sort + "|" + typeFilter + "|" + setFilter + "|" + onlyAvail + "|" + (onlyAvail ? host.deck().hashCode() : 0)
                + "|" + System.identityHashCode(L);
        if (key.equals(filterKey)) return filtered;
        filterKey = key;
        List<String> all = new ArrayList<>();
        if (L.has("energies")) for (JsonElement e : L.getAsJsonArray("energies")) all.add(gs(e.getAsJsonObject(), "f"));
        if (L.has("pool")) for (JsonElement e : L.getAsJsonArray("pool")) all.add(gs(e.getAsJsonObject(), "f"));
        String q = norm(query.trim());
        List<String> out = new ArrayList<>();
        for (String f : all) {
            if (cat != 0 && kind(f) != cat) continue;
            if (typeFilter >= 0 && type(f) != typeFilter) continue;
            if (!setFilter.isEmpty() && (isBasicEnergy(f) || !set(f).equals(setFilter))) continue;
            if (!q.isEmpty() && !searchKey(f).contains(q)) continue;
            if (onlyAvail && cantAdd(f, s) != null) continue;
            out.add(f);
        }
        Comparator<String> byName = Comparator.comparing(this::name, String.CASE_INSENSITIVE_ORDER);
        Comparator<String> c = switch (SORTS[sort]) {
            case "name" -> byName;
            case "type" -> Comparator.comparingInt((String f) -> type(f) < 0 ? 99 : type(f)).thenComparingInt(this::kind).thenComparing(byName);
            case "hp" -> Comparator.comparingInt((String f) -> -gi(info.get(f), "hp")).thenComparingInt(this::kind).thenComparing(byName);
            case "owned" -> Comparator.comparingInt((String f) -> isBasicEnergy(f) ? 1 : 0).thenComparingInt(f -> -owned(f)).thenComparing(byName);
            case "set" -> Comparator.comparing(this::set).thenComparingInt(f -> num(gs(info.get(f), "num"))).thenComparing(byName);
            default -> Comparator.comparingInt(this::kind).thenComparingInt(this::stageOrder).thenComparing(byName);
        };
        out.sort(c);
        filtered = out;
        return out;
    }

    private static int num(String s) {
        try { return Integer.parseInt(s.replaceAll("\\D", "")); } catch (NumberFormatException ex) { return 9999; }
    }

    /** Order within each class: Basic, Stage 1, Stage 2, others · Item, Tool, Supporter, Stadium · Basic Energy by type. */
    private int stageOrder(String f) {
        JsonObject o = info.get(f);
        return switch (kind(f)) {
            case 1 -> { int st = gi(o, "stage"); yield st == STAGE_BASIC ? 0 : st == STAGE_1 ? 1 : st == STAGE_2 ? 2 : 3; }
            case 2 -> { int tt = gi(o, "tt"); yield tt == 0 ? 0 : tt == 3 ? 1 : tt == 1 ? 2 : 3; }
            default -> isBasicEnergy(f) ? type(f) : 50;
        };
    }

    private void drawPool(GuiGraphics ctx, JsonObject L, Stats s, int px, int top, int colW, int h, int mx, int my, String[] hover) {
        Ui.roundGradient(ctx, px, top, colW, h, SURF2, SURF);
        Ui.outline(ctx, px, top, colW, h, LINE);
        // row 1: search · sort · available only
        int y1 = top + 3;
        Component sortL = Component.translatable("tcgshop.league.db.sort", Component.translatable("tcgshop.league.db.sort." + SORTS[sort]));
        Component availL = Component.translatable("tcgshop.league.db.only_avail");
        int sortW = Math.min(Ui.chipWidth(sortL), colW / 3), availW = Math.min(Ui.chipWidth(availL), colW / 4);
        int searchW = colW - 8 - sortW - availW - 6;
        searchBox(ctx, px + 4, y1, searchW, 12, mx, my);
        int x = px + 4 + searchW + 3;
        Ui.chip(ctx, x, y1, sortW, 12, sortL, false, Ui.in(mx, my, x, y1, sortW, 12), 0xFF8E7BE8, 0);
        if (Ui.in(mx, my, x, y1, sortW, 12)) tip(Component.translatable("tcgshop.league.db.hint.sort"), mx, my);
        hits.add(new Hit(x, y1, sortW, 12, (b, sh) -> { click(); sort = Math.floorMod(sort + (b == 1 ? -1 : 1), SORTS.length); poolScroll = 0; }));
        x += sortW + 3;
        Ui.chip(ctx, x, y1, availW, 12, availL, onlyAvail, Ui.in(mx, my, x, y1, availW, 12), 0xFF4ADE80, 0);
        if (Ui.in(mx, my, x, y1, availW, 12)) tip(Component.translatable("tcgshop.league.db.hint.only_avail"), mx, my);
        hits.add(new Hit(x, y1, availW, 12, (b, sh) -> { click(); onlyAvail = !onlyAvail; poolScroll = 0; }));
        // row 2: class · type · set
        int y2 = top + 18;
        x = px + 4;
        for (int k = 0; k < CATS.length; k++) {
            Component label = Component.translatable("tcgshop.league.deck.filter." + CATS[k]);
            int w = Math.min(Ui.chipWidth(label), (colW - 8) / 7);
            Ui.chip(ctx, x, y2, w, 12, label, cat == k, Ui.in(mx, my, x, y2, w, 12), 0xFF4FB4FF, 0);
            int kk = k;
            hits.add(new Hit(x, y2, w, 12, (b, sh) -> { click(); cat = kk; poolScroll = 0; }));
            x += w + 2;
        }
        x += 2;
        Component setL = setFilter.isEmpty() ? Component.translatable("tcgshop.league.db.set_all") : Component.literal(setFilter);
        int setW = Math.min(Ui.chipWidth(setL), 44);
        int typesW = px + colW - 4 - setW - 3 - x;
        int dot = Math.max(5, Math.min(9, typesW / TYPES.length - 1));
        for (int t : TYPES) {
            boolean sel = typeFilter == t, hov = Ui.in(mx, my, x, y2 + 1, dot, 10);
            int cx = x + dot / 2, cy = y2 + 6, r = Math.max(2, dot / 2);
            if (sel || hov) StylePainter.circle(ctx, cx, cy, r + 1, 0xFFFFFFFF);
            StylePainter.circle(ctx, cx, cy, r, typeFilter < 0 || sel ? ClientPtcg.typeColor(t) : withAlpha(ClientPtcg.typeColor(t), 0.35f));
            if (hov) tip(Component.translatable("tcgshop.league.db.type." + t), mx, my);
            hits.add(new Hit(x, y2 + 1, dot, 10, (b, sh) -> { click(); typeFilter = typeFilter == t ? -1 : t; poolScroll = 0; }));
            x += dot + 1;
        }
        int setX = px + colW - 4 - setW;
        Ui.chip(ctx, setX, y2, setW, 12, setL, !setFilter.isEmpty(), Ui.in(mx, my, setX, y2, setW, 12), 0xFFE0A040, 0);
        if (Ui.in(mx, my, setX, y2, setW, 12)) tip(Component.translatable("tcgshop.league.db.hint.set"), mx, my);
        hits.add(new Hit(setX, y2, setW, 12, (b, sh) -> {
            click();
            if (sets.isEmpty()) return;
            int i = setFilter.isEmpty() ? -1 : sets.indexOf(setFilter);
            i += b == 1 ? -1 : 1;
            if (i < -1) i = sets.size() - 1;
            setFilter = i < 0 || i >= sets.size() ? "" : sets.get(i);
            poolScroll = 0;
        }));

        // card grid
        List<String> items = filtered(L, s);
        int gy = top + 33, gh = h - 42, gap = 4; // the controls line sits at the bottom
        poolX = px; poolY = gy; poolW = colW; poolH = gh;
        int cw = Math.max(22, Math.min(34, (colW - 10) / 6 - gap)), chh = Math.round(cw / Draw.CARD_RATIO);
        int cols = Math.max(1, (colW - 8 + gap) / (cw + gap)), rowH = chh + gap + 7;
        int rows = (items.size() + cols - 1) / cols;
        poolScroll = Mth.clamp(poolScroll, 0, Math.max(0, rows * rowH - gh + 4));
        ctx.enableScissor(px + 1, gy, px + colW - 1, gy + gh);
        for (int k = 0; k < items.size(); k++) {
            String f = items.get(k);
            boolean energy = isBasicEnergy(f);
            int x0 = px + 5 + (k % cols) * (cw + gap), y0 = gy + 2 + (k / cols) * rowH - (int) poolScroll;
            if (y0 + rowH < gy || y0 > gy + gh) continue;
            int inDeck = s.card(f);
            Component why = cantAdd(f, s);
            boolean can = why == null;
            boolean hov = Ui.in(mx, my, x0, y0, cw, chh) && my >= gy && my < gy + gh;
            if (hov) {
                hover[0] = f;
                if (!can) tip(why, mx, my);
            }
            card(ctx, f, x0, y0 - (hov ? 2 : 0), cw, chh, can || inDeck > 0 ? 1f : 0.4f);
            if (hov) Ui.outline(ctx, x0 - 1, y0 - 3, cw + 2, chh + 2, can ? 0xFF4FD1FF : 0xFFFF6B6B);
            if (gi(info.get(f), "graded") > 0) {
                ctx.fill(x0, y0, x0 + 9, y0 + 6, 0xFFFFC233);
                small(ctx, "CG", x0 + 1, y0 + 1, 0.5f, 0xFF1A2236);
            }
            if (inDeck > 0) Ui.badge(ctx, x0 + cw - 2, y0 - 2, inDeck);
            String cnt = energy ? "∞" : inDeck + "/" + owned(f);
            small(ctx, cnt, x0 + cw / 2f - tr().width(cnt) * 0.3f, y0 + chh + 1.5f, 0.6f, can ? 0xFFFFD34D : 0xFF777788);
            int hy = Math.max(y0, gy), hh = Math.min(y0 + chh, gy + gh) - hy; // only the visible part is clickable
            if (hh > 0) hits.add(new Hit(x0, hy, cw, hh, (b, shift) -> {
                if (b == 1) remove(f, shift ? 99 : 1);
                else add(f, shift ? (energy ? 10 : MAX_COPIES) : 1);
            }));
        }
        ctx.disableScissor();
        // scrollbar
        if (rows * rowH > gh) {
            float frac = (float) (poolScroll / Math.max(1, rows * rowH - gh + 4));
            int bh = Math.max(12, gh * gh / (rows * rowH));
            int by = gy + Math.round((gh - bh) * frac);
            Ui.rounded(ctx, px + colW - 4, by, 2, bh, 1, 0x88FFFFFF);
        }
        if (items.isEmpty()) {
            boolean anyFilter = cat != 0 || typeFilter >= 0 || !setFilter.isEmpty() || onlyAvail || !query.isBlank();
            if (!anyFilter) wrap(ctx, Component.translatable("tcgshop.league.no_pool"), px + 6, gy + 6, colW - 12, Ui.MUTED);
            else {
                int yy = wrap(ctx, Component.translatable("tcgshop.league.db.no_match"), px + 6, gy + 6, colW - 12, Ui.MUTED);
                Component rl = Component.translatable("tcgshop.league.db.reset_filters");
                int rw = Ui.chipWidth(rl) + 6;
                Ui.button(ctx, px + 6, yy + 4, rw, 13, rl, mx, my, true, 0xFF4A5470);
                hits.add(new Hit(px + 6, yy + 4, rw, 13, (b, sh) -> { click(); resetFilters(); }));
            }
        }
        // controls help at the bottom
        small(ctx, Component.translatable("tcgshop.league.db.controls"), px + 4, top + h - 7, 0.55f, 0xFF7A80A0);
    }

    private void resetFilters() {
        cat = 0;
        typeFilter = -1;
        setFilter = "";
        onlyAvail = false;
        query = "";
        poolScroll = 0;
    }

    private int wrap(GuiGraphics ctx, Component t, int x, int y, int w, int color) {
        for (FormattedCharSequence line : tr().split(t, w)) {
            ctx.drawString(tr(), line, x, y, color, false);
            y += 10;
        }
        return y;
    }

    private void searchBox(GuiGraphics ctx, int x, int y, int w, int h, int mx, int my) {
        boolean hov = Ui.in(mx, my, x, y, w, h);
        Ui.rounded(ctx, x, y, w, h, 4, focused ? 0xEE0E1018 : hov ? 0xCC161A26 : 0xAA0E1018);
        Ui.roundedOutline(ctx, x, y, w, h, 4, focused ? 0xFFFFFFFF : 0x66FFFFFF);
        ctx.drawString(tr(), "⌕", x + 3, y + 2, 0xFFAAAAAA, false);
        int tx = x + 12, tw = w - 24;
        String shownText = query;
        while (!shownText.isEmpty() && tr().width(shownText) > tw) shownText = shownText.substring(1); // the end stays visible
        if (query.isEmpty() && !focused) ctx.drawString(tr(), Ui.ellipsis(Component.translatable("tcgshop.league.db.search").getString(), tw), tx, y + 2, 0xFF666A80, false);
        else ctx.drawString(tr(), shownText, tx, y + 2, 0xFFFFFFFF, false);
        if (focused && (System.currentTimeMillis() / 500) % 2 == 0) {
            int cx = tx + tr().width(shownText) + 1;
            ctx.fill(cx, y + 2, cx + 1, y + h - 2, 0xFFFFFFFF);
        }
        hits.add(new Hit(x, y, w - (query.isEmpty() ? 0 : 11), h, (b, sh) -> {
            focused = true;
            if (b == 1) { query = ""; poolScroll = 0; }
        }));
        if (!query.isEmpty()) {
            boolean xh = Ui.in(mx, my, x + w - 11, y, 11, h);
            ctx.drawString(tr(), "×", x + w - 9, y + 2, xh ? 0xFFFF8A80 : 0xFF9A9AAE, false);
            hits.add(new Hit(x + w - 11, y, 11, h, (b, sh) -> { query = ""; poolScroll = 0; focused = true; }));
        }
    }

    // ------------------------------------------------------------------ right: summary, warnings and deck list

    private void drawDeck(GuiGraphics ctx, Stats s, List<Issue> issues, int dx, int top, int dw, int h, int mx, int my, String[] hover) {
        Ui.roundGradient(ctx, dx, top, dw, h, SURF2, SURF);
        Ui.outline(ctx, dx, top, dw, h, LINE);
        int x = dx + 4, w = dw - 8, y = top + 3;
        // Pokémon / Trainer / Energy split with a 60 bar
        small(ctx, Component.translatable("tcgshop.league.deck.split", s.pokemon, s.trainer, s.energy), x, y, 0.7f, 0xFFDDE3F0);
        y += 8;
        Ui.rounded(ctx, x, y, w, 5, 2, 0xFF0B0D13);
        int px1 = Math.round(w * Math.min(1f, s.pokemon / (float) DECK_SIZE));
        int px2 = Math.round(w * Math.min(1f, (s.pokemon + s.trainer) / (float) DECK_SIZE));
        int px3 = Math.round(w * Math.min(1f, s.n / (float) DECK_SIZE));
        if (px1 > 0) ctx.fill(x, y + 1, x + px1, y + 4, COL_P);
        if (px2 > px1) ctx.fill(x + px1, y + 1, x + px2, y + 4, COL_T);
        if (px3 > px2) ctx.fill(x + px2, y + 1, x + px3, y + 4, COL_E);
        if (Ui.in(mx, my, x, y - 8, w, 13)) tip(Component.translatable("tcgshop.league.db.hint.split"), mx, my);
        y += 8;
        // curve: Pokémon stages · Trainer types · Energy by type
        List<int[]> bars = new ArrayList<>(); // value, color
        List<String> barLabels = new ArrayList<>();
        bars.add(new int[]{s.basics, COL_P}); barLabels.add(Component.translatable("tcgshop.league.db.curve.basic").getString());
        bars.add(new int[]{s.stage1, COL_P}); barLabels.add(Component.translatable("tcgshop.league.db.curve.s1").getString());
        bars.add(new int[]{s.stage2, COL_P}); barLabels.add(Component.translatable("tcgshop.league.db.curve.s2").getString());
        if (s.otherStage > 0) { bars.add(new int[]{s.otherStage, COL_P}); barLabels.add(Component.translatable("tcgshop.league.db.curve.other").getString()); }
        int sep1 = bars.size();
        String[] tts = {"item", "supporter", "stadium", "tool"};
        for (int i = 0; i < 4; i++) { bars.add(new int[]{s.trainerType[i], COL_T}); barLabels.add(Component.translatable("tcgshop.league.db.curve." + tts[i]).getString()); }
        int sep2 = bars.size();
        for (var e : s.energyType.entrySet()) {
            int t = e.getKey();
            bars.add(new int[]{e.getValue(), t < 0 ? 0xFFB0B0C0 : ClientPtcg.typeColor(t)});
            barLabels.add(t < 0 ? "★" : ClientPtcg.typeLetter(t));
        }
        int chartH = h > 200 ? 34 : 26;
        int max = 1;
        for (int[] b : bars) max = Math.max(max, b[0]);
        int gapX = 2, seps = 2;
        float bw = Math.max(3f, (w - gapX * (bars.size() - 1) - seps * 4) / (float) Math.max(1, bars.size()));
        float bx = x;
        int baseY = y + chartH - 6;
        for (int i = 0; i < bars.size(); i++) {
            if (i == sep1 || i == sep2) { ctx.fill(Math.round(bx + 1), y + 2, Math.round(bx + 2), baseY, 0x33FFFFFF); bx += 4; }
            int v = bars.get(i)[0], c = bars.get(i)[1];
            int bh = v == 0 ? 0 : Math.max(2, Math.round((chartH - 14) * v / (float) max));
            int x0 = Math.round(bx), x1 = Math.round(bx + bw);
            ctx.fill(x0, baseY - (chartH - 14), x1, baseY, 0x14FFFFFF);
            if (bh > 0) ctx.fillGradient(x0, baseY - bh, x1, baseY, Ui.brighten(c, 40), c);
            if (v > 0) small(ctx, String.valueOf(v), (x0 + x1) / 2f - tr().width(String.valueOf(v)) * 0.25f, baseY - bh - 5, 0.5f, 0xFFFFFFFF);
            String lab = barLabels.get(i);
            float ls = Math.min(0.5f, (bw + 1) / Math.max(1, tr().width(lab)));
            small(ctx, lab, (x0 + x1) / 2f - tr().width(lab) * ls / 2f, baseY + 1.5f, ls, 0xFF9AA0B8);
            if (Ui.in(mx, my, x0, y, Math.max(1, x1 - x0), chartH)) tip(Component.literal(lab + ": " + v), mx, my);
            bx += bw + gapX;
        }
        y += chartH + 1;
        // warnings (errors first)
        int maxIssues = h > 220 ? 5 : h > 170 ? 3 : 2;
        int shownIssues = 0;
        for (Issue is : issues) {
            if (shownIssues == maxIssues) {
                int left = issues.size() - maxIssues;
                small(ctx, Component.translatable("tcgshop.league.db.more", left), x, y, 0.6f, 0xFF9AA0B8);
                if (Ui.in(mx, my, x, y, w, 7)) {
                    List<FormattedCharSequence> all = new ArrayList<>();
                    for (Issue i2 : issues) all.addAll(tr().split(Component.literal(issueIcon(i2.level) + " ").append(i2.text), 200));
                    tooltip = all;
                }
                y += 7;
                break;
            }
            String line = Ui.ellipsis(issueIcon(is.level) + " " + is.text.getString(), Math.round(w / 0.65f));
            small(ctx, line, x, y, 0.65f, issueColor(is.level));
            if (Ui.in(mx, my, x, y, w, 7)) tip(is.text, mx, my);
            y += 7;
            shownIssues++;
        }
        y += 2;
        ctx.fill(x, y, x + w, y + 1, 0x22FFFFFF);
        y += 2;
        // grouped list
        int ly = y, lh = top + h - 3 - ly;
        deckX = dx; deckY = ly; deckW = dw; deckH = lh;
        List<String> keys = new ArrayList<>(s.byCard.keySet());
        keys.sort(Comparator.comparingInt(this::kind).thenComparingInt(this::stageOrder).thenComparing(this::name, String.CASE_INSENSITIVE_ORDER));
        int rowH = 10;
        List<Object> rows = new ArrayList<>(); // Integer = class header, String = card
        int lastKind = 0;
        for (String f : keys) {
            int k = kind(f);
            if (k != lastKind) { rows.add(k); lastKind = k; }
            rows.add(f);
        }
        deckScroll = Mth.clamp(deckScroll, 0, Math.max(0, rows.size() * rowH - lh + 2));
        ctx.enableScissor(dx + 1, ly, dx + dw - 1, ly + lh);
        for (int i = 0; i < rows.size(); i++) {
            int ry = ly + i * rowH - (int) deckScroll;
            if (ry + rowH < ly || ry > ly + lh) continue;
            Object r = rows.get(i);
            if (r instanceof Integer k) {
                int cnt = k == 1 ? s.pokemon : k == 2 ? s.trainer : s.energy;
                int c = k == 1 ? COL_P : k == 2 ? COL_T : COL_E;
                ctx.fill(x, ry + 4, x + 2, ry + 8, c);
                small(ctx, Component.translatable("tcgshop.league.db.group." + k, cnt).withStyle(ChatFormatting.BOLD), x + 4, ry + 3, 0.65f, c);
                continue;
            }
            String f = (String) r;
            boolean hov = Ui.in(mx, my, x, ry, w, rowH) && my >= ly && my < ly + lh;
            if (hov) { Ui.rounded(ctx, x - 1, ry, w + 2, rowH, 2, 0x22FFFFFF); hover[0] = f; }
            int t = type(f);
            StylePainter.circle(ctx, x + 3, ry + 5, 2, t >= 0 ? ClientPtcg.typeColor(t) : kind(f) == 2 ? COL_T : 0xFFB0B0C0);
            int c = s.card(f);
            boolean over = !isBasicEnergy(f) && (c > owned(f) || s.nameCount(f) > MAX_COPIES);
            small(ctx, "×" + c, x + 8, ry + 2.5f, 0.75f, over ? 0xFFFF8A80 : 0xFFFFD34D);
            int nameX = x + 22, btn = 8;
            int nameW = w - 22 - btn * 2 - 4;
            small(ctx, Ui.ellipsis(name(f), Math.round(nameW / 0.75f)), nameX, ry + 2.5f, 0.75f, over ? 0xFFFF8A80 : 0xFFE6E9F2);
            // − / + buttons
            int bmx = x + w - btn * 2 - 2, bpx = x + w - btn;
            boolean canPlus = cantAdd(f, s) == null;
            miniButton(ctx, bmx, ry + 1, btn, "−", Ui.in(mx, my, bmx, ry + 1, btn, btn), true);
            miniButton(ctx, bpx, ry + 1, btn, "+", Ui.in(mx, my, bpx, ry + 1, btn, btn), canPlus);
            if (ry >= ly && ry + rowH <= ly + lh) {
                hits.add(new Hit(bmx, ry + 1, btn, btn, (b, shift) -> remove(f, shift ? 99 : 1)));
                hits.add(new Hit(bpx, ry + 1, btn, btn, (b, shift) -> add(f, shift ? (isBasicEnergy(f) ? 10 : MAX_COPIES) : 1)));
                hits.add(new Hit(x, ry, w - btn * 2 - 3, rowH, (b, shift) -> {
                    if (b == 1) add(f, 1);
                    else remove(f, shift ? 99 : 1);
                }));
            }
        }
        ctx.disableScissor();
        if (rows.size() * rowH > lh) {
            float frac = (float) (deckScroll / Math.max(1, rows.size() * rowH - lh + 2));
            int bh = Math.max(10, lh * lh / (rows.size() * rowH));
            Ui.rounded(ctx, dx + dw - 3, ly + Math.round((lh - bh) * frac), 2, bh, 1, 0x88FFFFFF);
        }
        if (keys.isEmpty()) wrap(ctx, Component.translatable("tcgshop.league.db.empty_hint"), x, ly + 2, w, Ui.MUTED);
    }

    private static void miniButton(GuiGraphics ctx, int x, int y, int s, String label, boolean hover, boolean enabled) {
        Ui.rounded(ctx, x, y, s, s, 2, !enabled ? 0x30FFFFFF : hover ? 0xFF4A5A88 : 0xFF2A3048);
        Ui.roundedOutline(ctx, x, y, s, s, 2, hover && enabled ? 0xFFFFFFFF : 0x55FFFFFF);
        ctx.drawString(tr(), label, x + (s - tr().width(label)) / 2 + 1, y, enabled ? 0xFFFFFFFF : 0xFF66667A, false);
    }

    // ------------------------------------------------------------------ input (forwarded by the phone)

    /** Click inside the editor. Returns true if it consumed it. */
    public boolean mouseClicked(double mx, double my, int button) {
        if (!shown()) return false;
        boolean shift = Screen.hasShiftDown();
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (Ui.in(mx, my, h.x, h.y, h.w, h.h)) {
                focused = false; // the search box refocuses if the click was on it
                h.action.run(button, shift);
                return true;
            }
        }
        focused = false;
        return false;
    }

    public boolean mouseScrolled(double mx, double my, double v) {
        if (!shown()) return false;
        if (Ui.in(mx, my, poolX, poolY, poolW, poolH)) { poolScroll -= v * 30; return true; }
        if (Ui.in(mx, my, deckX, deckY, deckW, deckH)) { deckScroll -= v * 20; return true; }
        return false;
    }

    public boolean charTyped(char chr) {
        if (!shown() || !focused) return false;
        if (chr >= ' ' && chr != 127 && query.length() < 40) {
            query += chr;
            poolScroll = 0;
        }
        return true;
    }

    /** Keys: with the search box active it takes all of them (Esc / Enter close it); otherwise Ctrl+Z undoes and Ctrl+F searches. */
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!shown()) return false;
        if (focused) {
            switch (keyCode) {
                case GLFW.GLFW_KEY_BACKSPACE -> {
                    if (Screen.hasControlDown()) query = "";
                    else if (!query.isEmpty()) query = query.substring(0, query.length() - 1);
                    poolScroll = 0;
                }
                case GLFW.GLFW_KEY_ESCAPE, GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_TAB -> focused = false;
                default -> { }
            }
            return true;
        }
        if (Screen.hasControlDown() && keyCode == GLFW.GLFW_KEY_Z) return undoLast() || true;
        if (Screen.hasControlDown() && keyCode == GLFW.GLFW_KEY_F) { focused = true; return true; }
        return false;
    }

    /** Call when the phone stops drawing the editor (another tab / another app) to release focus and clicks. */
    public void hide() {
        lastRender = 0;
        focused = false;
        hits.clear();
    }

    public boolean searchFocused() { return shown() && focused; }

    /** Whether it was drawn just now (if the phone switches tab, it stops receiving input on its own). */
    private boolean shown() { return System.currentTimeMillis() - lastRender < 250; }
}

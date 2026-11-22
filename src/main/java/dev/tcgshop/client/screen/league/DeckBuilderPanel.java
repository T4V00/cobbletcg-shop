package dev.tcgshop.client.screen.league;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.tcgshop.client.ClientPtcg;
import dev.tcgshop.client.binder.StylePainter;
import dev.tcgshop.client.binder.Ui;
import dev.tcgshop.client.screen.Draw;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.HoveredTooltipPositioner;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;
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

import static dev.tcgshop.client.screen.Draw.withAlpha;

/**
 * Editor de mazos de la Liga TCG (app del teléfono, pestaña «Mi mazo»).
 *  · Izquierda: tu colección como cartas, con buscador por nombre, filtros (Pokémon / Entrenador / Energía, tipo,
 *    set, sólo disponibles) y orden. Clic = añadir · Mayús + clic = todas las copias que quepan · clic derecho = quitar.
 *  · Derecha: resumen (reparto, curva de fases, tipos de Entrenador y de Energía), avisos de validación en vivo
 *    (60 cartas, regla de 4 copias, Pokémon Básico, Radiante, copias que te faltan, evoluciones sin su fase anterior)
 *    más el resultado del motor (validateDeck: AS TÁCTICO, combinaciones prohibidas, formato) y la lista del mazo
 *    agrupada con botones − / +.
 *  · Arriba: contador 60 en vivo y Guardar · Comprobar · Autocompletar (buildDeck del motor alrededor de lo que ya
 *    has puesto) · +Energía (rellena con Energía básica de tus tipos) · Deshacer · Vaciar.
 * No usa las áreas del teléfono: guarda sus propias zonas de clic (botón izquierdo/derecho y Mayús).
 */
public final class DeckBuilderPanel {
    /** Lo que el teléfono le presta al editor: la lista editable del mazo, el estado «sin guardar» y el envío al servidor. */
    public interface Host {
        List<String> deck();

        boolean dirty();

        void setDirty(boolean dirty);

        void send(String action, @Nullable List<String> deck);
    }

    private interface Click { void run(int button, boolean shift); }

    private record Hit(int x, int y, int w, int h, Click action) {}

    private record Issue(int level, Text text) {} // 0 = bien, 1 = consejo, 2 = aviso, 3 = error

    private static final int SURF = 0xF0171A26, SURF2 = 0xF01F2333, LINE = 0xFF2C3149;
    private static final int DECK_SIZE = 60, MAX_COPIES = 4, UNDO_MAX = 40;
    private static final String[] CATS = {"all", "pokemon", "trainer", "energy"};
    private static final String[] SORTS = {"stage", "name", "type", "hp", "owned", "set"};
    /** Tipos (CardType del motor) que se pueden filtrar: Planta…Metal, Incoloro, Hada, Dragón. */
    private static final int[] TYPES = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11};
    private static final int COL_P = 0xFFE0604A, COL_T = 0xFF4FA3E8, COL_E = 0xFFF2CC2E;
    /** Stage del motor: 2 = Básico, 3 = Fase 1, 4 = Fase 2 (el resto: VMAX, VSTAR, BREAK, Restaurado...). */
    private static final int STAGE_BASIC = 2, STAGE_1 = 3, STAGE_2 = 4;

    private final Host host;
    private final List<Hit> hits = new ArrayList<>();
    private final Deque<List<String>> undo = new ArrayDeque<>();

    // filtros
    private String query = "";
    private boolean focused;
    private int cat, sort, typeFilter = -1;
    private String setFilter = "";
    private boolean onlyAvail;
    private double poolScroll, deckScroll;

    // zonas de este fotograma (rueda del ratón)
    private int poolX, poolY, poolW, poolH, deckX, deckY, deckW, deckH;
    private long lastRender;
    private List<OrderedText> tooltip;

    // guardado pendiente: el mazo sigue «sin guardar» hasta que el servidor confirma (SAVED) o lo rechaza
    private @Nullable List<String> pendingSave;
    private @Nullable JsonObject saveSentOn; // sincronización que había al pulsar Guardar (su respuesta será otra)
    private long saveSentAt;
    private String saveError = ""; // motivo del motor si rechazó el último Guardar (se borra al editar)
    // huella del mazo enviado con Comprobar: un resultado de otro mazo (respuesta tardía) no vale
    private int checkHash;
    private boolean checkSent;

    // índice de la colección (se rehace cuando llega una sincronización nueva)
    private JsonObject indexed;
    private final Map<String, JsonObject> info = new HashMap<>();
    private final Map<String, Integer> owned = new HashMap<>();
    private final Set<String> basicEnergy = new HashSet<>();
    private final Map<Integer, String> energyByType = new HashMap<>();
    private final List<String> sets = new ArrayList<>();
    private final Map<String, String> searchKey = new HashMap<>();
    // lista filtrada en caché
    private String filterKey = "";
    private List<String> filtered = new ArrayList<>();

    public DeckBuilderPanel(Host host) {
        this.host = host;
    }

    private static TextRenderer tr() { return MinecraftClient.getInstance().textRenderer; }

    private static int gi(@Nullable JsonObject o, String k) { return o != null && o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsInt() : 0; }

    private static String gs(@Nullable JsonObject o, String k) { return o != null && o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : ""; }

    // ------------------------------------------------------------------ datos de las cartas

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

    /** 1 = Pokémon, 2 = Entrenador, 3 = Energía. */
    private int kind(String f) {
        if (isBasicEnergy(f)) return 3;
        int st = gi(info.get(f), "st");
        return st >= 1 && st <= 3 ? st : 2;
    }

    /** Tipo (CardType) de la carta para el filtro y los colores: tipo del Pokémon o de la Energía básica; -1 si no tiene. */
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

    // ------------------------------------------------------------------ recuentos del mazo

    private final class Stats {
        final Map<String, Integer> byCard = new LinkedHashMap<>();
        final Map<String, Integer> byName = new HashMap<>();
        int n, pokemon, trainer, energy, basics, stage1, stage2, otherStage, radiant;
        final int[] trainerType = new int[4]; // Objeto, Partidario, Estadio, Herramienta
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

    /** Si se puede añadir una copia más (y si no, por qué). */
    private @Nullable Text cantAdd(String f, Stats s) {
        if (s.n >= DECK_SIZE) return Text.translatable("tcgshop.league.db.why.full");
        if (isBasicEnergy(f)) return null;
        if (s.card(f) >= owned(f)) return Text.translatable("tcgshop.league.db.why.owned", owned(f));
        if (s.nameCount(f) >= MAX_COPIES) return Text.translatable("tcgshop.league.db.why.four", name(f));
        return null;
    }

    // ------------------------------------------------------------------ cambios en el mazo

    private void snapshot() {
        undo.push(new ArrayList<>(host.deck()));
        while (undo.size() > UNDO_MAX) undo.removeLast();
    }

    private void changed(SoundEvent sound, float pitch) {
        host.setDirty(true);
        JsonObject L = ClientPtcg.league;
        if (L != null) L.remove("check"); // la comprobación del motor ya no vale
        saveError = "";
        filterKey = "";
        MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(sound, pitch, 0.4f));
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
        for (int i = 0; i < times && host.deck().remove(f); i++) { /* quita una copia cada vuelta */ }
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

    /** Rellena los huecos con Energía básica repartida según los tipos de tus Pokémon (los que tienen Energía básica). */
    private void fillEnergy() {
        Stats s = new Stats(host.deck());
        int free = DECK_SIZE - s.n;
        Map<Integer, Integer> weights = new LinkedHashMap<>();
        for (var e : s.pokemonType.entrySet()) if (energyByType.containsKey(e.getKey())) weights.put(e.getKey(), e.getValue());
        if (free <= 0 || weights.isEmpty()) { denied(); return; }
        int total = 0;
        for (int w : weights.values()) total += w;
        // reparto proporcional con el método del mayor resto
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
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) mc.player.playSound(net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), 0.5f, 0.6f);
    }

    private static void click() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) mc.player.playSound(net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK.value(), 0.4f, 1.4f);
    }

    // ------------------------------------------------------------------ validación

    /** Huella del mazo sin importar el orden (la misma lista con otro orden es el mismo mazo). */
    private static int deckHash(List<String> deck) {
        List<String> sorted = new ArrayList<>(deck);
        sorted.sort(null);
        return sorted.hashCode();
    }

    /**
     * Si el resultado de Comprobar que hay en L es del mazo que estás viendo: la respuesta trae la lista que validó el
     * motor («checked») y tiene que coincidir con la que enviaste y con la actual; si no, es de otro mazo y se ignora.
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

    /** Mira si la sincronización nueva es la respuesta al Guardar pendiente y, si lo es, la aplica. */
    private void absorbSaveReply(JsonObject L) {
        if (pendingSave == null || L == saveSentOn) return;
        String msg = gs(L, "msg");
        // otras respuestas (sincronización periódica, Comprobar, Autocompletar, cola...): seguimos esperando la del Guardar
        if (msg.isEmpty() || msg.equals("CHECK") || msg.startsWith("AUTO") || msg.equals("CLEARED") || msg.equals("QUEUED") || msg.equals("UNQUEUED")) return;
        List<String> sent = pendingSave;
        pendingSave = null;
        saveSentOn = null;
        if (msg.equals("SAVED")) {
            saveError = "";
            // si editaste mientras llegaba la respuesta, esos cambios siguen sin guardar
            if (deckHash(sent) == deckHash(host.deck())) host.setDirty(false);
        } else {
            saveError = msg; // el motor lo rechazó: se queda tu lista editada y se muestra el motivo
        }
    }

    private List<Issue> issues(JsonObject L, Stats s, boolean checked) {
        List<Issue> out = new ArrayList<>();
        String valid = gs(L, "valid"), msg = gs(L, "msg");
        boolean dirty = host.dirty();
        if (s.n < DECK_SIZE) out.add(new Issue(3, Text.translatable("tcgshop.league.db.err.missing", DECK_SIZE - s.n)));
        else if (s.n > DECK_SIZE) out.add(new Issue(3, Text.translatable("tcgshop.league.db.err.extra", s.n - DECK_SIZE)));
        for (var e : s.byName.entrySet())
            if (e.getValue() > MAX_COPIES) out.add(new Issue(3, Text.translatable("tcgshop.league.db.err.copies", e.getKey(), e.getValue())));
        for (var e : s.byCard.entrySet()) {
            String f = e.getKey();
            if (isBasicEnergy(f)) continue;
            if (!info.containsKey(f)) out.add(new Issue(3, Text.translatable("tcgshop.league.db.err.unknown", f)));
            else if (e.getValue() > owned(f)) out.add(new Issue(3, Text.translatable("tcgshop.league.db.err.owned", name(f), owned(f))));
        }
        if (s.n > 0 && s.basics == 0) out.add(new Issue(3, Text.translatable("tcgshop.league.db.err.basic")));
        if (s.radiant > 1) out.add(new Issue(3, Text.translatable("tcgshop.league.db.err.radiant")));
        // resultado del motor (validateDeck): lo único que sabe de AS TÁCTICO, Estrella, Prisma y combinaciones prohibidas
        if (dirty && !saveError.isEmpty()) out.add(new Issue(3, Text.translatable("tcgshop.league.db.save_bad", deckErr(saveError))));
        if (checked) {
            if (valid.isEmpty()) out.add(new Issue(0, Text.translatable("tcgshop.league.db.engine_ok")));
            else out.add(new Issue(3, Text.translatable("tcgshop.league.db.engine_bad", deckErr(valid))));
        } else if (!dirty) {
            if (valid.isEmpty() && s.n == DECK_SIZE) out.add(new Issue(0, Text.translatable(msg.equals("SAVED") ? "tcgshop.league.db.saved_ok" : "tcgshop.league.db.stored_ok")));
            else if (!valid.isEmpty() && !valid.equals("EMPTY")) out.add(new Issue(3, Text.translatable("tcgshop.league.db.engine_bad", deckErr(valid))));
            else if (msg.startsWith("AUTO")) out.add(new Issue(0, Text.translatable("tcgshop.league.db.auto_done")));
        }
        // consejos (no impiden jugar)
        int warnEvo = 0;
        for (String f : s.byCard.keySet()) {
            JsonObject o = info.get(f);
            String ev = gs(o, "ev");
            if (kind(f) == 1 && !ev.isEmpty() && !s.names.contains(ev)) {
                if (warnEvo++ < 2) out.add(new Issue(2, Text.translatable("tcgshop.league.db.warn.evo", name(f), ClientPtcg.cardName(ev, ev))));
            }
        }
        if (warnEvo > 2) out.add(new Issue(2, Text.translatable("tcgshop.league.db.warn.evo_more", warnEvo - 2)));
        if (s.pokemon > 0 && s.energy == 0 && s.n >= 20) out.add(new Issue(2, Text.translatable("tcgshop.league.db.warn.no_energy")));
        else if (s.n == DECK_SIZE && s.energy > 0 && s.energy < 8) out.add(new Issue(1, Text.translatable("tcgshop.league.db.warn.few_energy", s.energy)));
        else if (s.n == DECK_SIZE && s.energy > 22) out.add(new Issue(1, Text.translatable("tcgshop.league.db.warn.many_energy", s.energy)));
        if (dirty && !checked && s.n == DECK_SIZE && out.stream().noneMatch(i -> i.level == 3))
            out.add(new Issue(1, Text.translatable("tcgshop.league.db.hint_check")));
        if (dirty && saving()) out.add(new Issue(1, Text.translatable("tcgshop.league.db.saving")));
        else if (dirty) out.add(new Issue(1, Text.translatable("tcgshop.league.deck.unsaved")));
        out.sort(Comparator.comparingInt((Issue i) -> -i.level));
        return out;
    }

    /** Motivo legible del motor ("ACE_SPEC:Nombre" -> texto traducido con el nombre). */
    private static String deckErr(String code) {
        String[] parts = code.split(":", 2);
        return Text.translatable("tcgshop.ptcg.deck_err." + parts[0], parts.length > 1 ? parts[1] : "").getString();
    }

    private static int issueColor(int level) {
        return switch (level) { case 0 -> 0xFF6BE07B; case 1 -> 0xFF8BC8FF; case 2 -> 0xFFFFC233; default -> 0xFFFF8A80; };
    }

    private static String issueIcon(int level) {
        return switch (level) { case 0 -> "✔"; case 1 -> "ℹ"; case 2 -> "⚠"; default -> "✖"; };
    }

    // ------------------------------------------------------------------ dibujo

    /**
     * Dibuja el editor en el panel (px, py, pw, ph). (sx, sy, sw, sh) es la pantalla del teléfono, para colocar la vista
     * previa grande de la carta.
     */
    public void render(DrawContext ctx, JsonObject L, int px, int py, int pw, int ph, int mx, int my, int sx, int sy, int sw, int sh) {
        hits.clear();
        tooltip = null;
        lastRender = System.currentTimeMillis();
        reindex(L);
        absorbSaveReply(L);
        List<String> deck = host.deck();
        Stats s = new Stats(deck);
        boolean checked = engineChecked(L);
        List<Issue> issues = issues(L, s, checked);

        // ---- cabecera: contador 60 en vivo y botones
        int n = s.n;
        int col = n == DECK_SIZE ? 0xFF6BE07B : n > DECK_SIZE ? 0xFFFF6B6B : 0xFFFFC233;
        ctx.drawText(tr(), Text.literal(n + "/" + DECK_SIZE).formatted(Formatting.BOLD), px + 2, py + 3, col, true);
        int barX = px + 4 + tr().getWidth(n + "/" + DECK_SIZE) + 4;
        Ui.progress(ctx, barX, py + 4, 52, 6, Math.min(1f, n / (float) DECK_SIZE), col);
        boolean errors = issues.stream().anyMatch(i -> i.level == 3);
        String[] ids = {"save", "check", "auto", "energy", "undo", "clear"};
        Text[] labels = {
                Text.translatable("tcgshop.league.save"),
                Text.translatable("tcgshop.league.check"),
                Text.translatable(n > 0 && n < DECK_SIZE ? "tcgshop.league.db.autocomplete" : "tcgshop.league.auto"),
                Text.translatable("tcgshop.league.db.fill_energy"),
                Text.literal("↶"),
                Text.translatable("tcgshop.league.deck.clear")};
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
        for (int i = 0; i < ids.length; i++) { widths[i] = tr().getWidth(labels[i]) + 10; natural += widths[i] + 3; }
        float shrink = natural > avail ? avail / (float) natural : 1f;
        int bx = px + pw;
        for (int i = ids.length - 1; i >= 0; i--) {
            int w = Math.max(14, Math.round(widths[i] * shrink));
            bx -= w + 3;
            Ui.button(ctx, bx, py + 1, w, 13, labels[i], mx, my, enabled[i], accents[i]);
            if (Ui.in(mx, my, bx, py + 1, w, 13)) tip(Text.translatable("tcgshop.league.db.hint." + ids[i]), mx, my);
            if (!enabled[i]) continue;
            String id = ids[i];
            hits.add(new Hit(bx, py + 1, w, 13, (b, sh2) -> {
                if (b != 0) return;
                click();
                switch (id) {
                    case "save" -> {
                        // no se marca como guardado hasta que el servidor responda (absorbSaveReply)
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

        // ---- vista previa grande de la carta bajo el ratón
        if (hover[0] != null) {
            String f = hover[0];
            int pwid = Math.min(96, sh / 2), phgt = Math.round(pwid / Draw.CARD_RATIO);
            int prx = mx < px + pw / 2 ? Math.min(sx + sw - pwid - 6, mx + 14) : Math.max(sx + 6, mx - 14 - pwid);
            int pry = MathHelper.clamp(my - phgt / 2 - 6, sy + 16, sy + sh - phgt - 22);
            MatrixStack m = ctx.getMatrices();
            m.push();
            m.translate(0, 0, 400);
            ctx.fill(prx + 3, pry + 4, prx + pwid + 3, pry + phgt + 4, 0x66000000);
            card(ctx, f, prx, pry, pwid, phgt, 1f);
            // ficha: nombre, PS, set y copias
            JsonObject o = info.get(f);
            String line2 = (kind(f) == 1 && gi(o, "hp") > 0 ? gi(o, "hp") + " PS · " : "") + set(f) + (gs(o, "num").isEmpty() ? "" : " " + gs(o, "num"));
            String line3 = isBasicEnergy(f) ? Text.translatable("tcgshop.league.db.free_energy").getString()
                    : Text.translatable("tcgshop.league.db.copies", s.card(f), owned(f)).getString();
            int cw = Math.max(pwid, Math.max(tr().getWidth(name(f)), Math.max(tr().getWidth(line2), tr().getWidth(line3))) + 8);
            int cx = Math.min(prx, sx + sw - cw - 6), cy = pry + phgt + 3;
            Ui.rounded(ctx, cx, cy, cw, 31, 4, 0xF0101420);
            Ui.roundedOutline(ctx, cx, cy, cw, 31, 4, 0xFF3A4060);
            ctx.drawText(tr(), name(f), cx + 4, cy + 3, 0xFFFFFFFF, false);
            ctx.drawText(tr(), line2, cx + 4, cy + 12, 0xFFAEB6CC, false);
            ctx.drawText(tr(), line3, cx + 4, cy + 21, 0xFFFFD34D, false);
            m.pop();
        }
        // el teléfono dibuja esto dentro de su recorte (scissor): el tooltip se le pasa a la pantalla, que lo pinta al final
        if (tooltip != null) deferTooltip(tooltip);
    }

    private void tip(Text t, int mx, int my) {
        tooltip = tr().wrapLines(t, 170);
    }

    /** Tooltip diferido: Screen.renderWithTooltip lo dibuja después de render(), ya fuera del recorte del teléfono. */
    static void deferTooltip(List<OrderedText> lines) {
        Screen screen = MinecraftClient.getInstance().currentScreen;
        if (screen != null) screen.setTooltip(lines, HoveredTooltipPositioner.INSTANCE, true);
    }

    /** Imagen real de la carta o una tarjeta de color con el nombre si no hay imagen. */
    private void card(DrawContext ctx, String f, int x, int y, int w, int h, float alpha) {
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
        MatrixStack m = ctx.getMatrices();
        m.push();
        float sc = Math.max(0.3f, Math.min(0.55f, (w - 3f) / Math.max(1, tr().getWidth(nm))));
        m.translate(x + 1.5f, y + 2, 0);
        m.scale(sc, sc, 1);
        ctx.drawText(tr(), nm, 0, 0, withAlpha(0xFF1A2236, alpha), false);
        m.pop();
    }

    private static void small(DrawContext ctx, String s, float x, float y, float scale, int color) {
        MatrixStack m = ctx.getMatrices();
        m.push();
        m.translate(x, y, 0);
        m.scale(scale, scale, 1);
        ctx.drawText(tr(), s, 0, 0, color, false);
        m.pop();
    }

    private static void small(DrawContext ctx, Text s, float x, float y, float scale, int color) {
        MatrixStack m = ctx.getMatrices();
        m.push();
        m.translate(x, y, 0);
        m.scale(scale, scale, 1);
        ctx.drawText(tr(), s, 0, 0, color, false);
        m.pop();
    }

    // ------------------------------------------------------------------ izquierda: colección

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

    /** Orden dentro de cada clase: Básico, Fase 1, Fase 2, otros · Objeto, Herramienta, Partidario, Estadio · Energía básica por tipo. */
    private int stageOrder(String f) {
        JsonObject o = info.get(f);
        return switch (kind(f)) {
            case 1 -> { int st = gi(o, "stage"); yield st == STAGE_BASIC ? 0 : st == STAGE_1 ? 1 : st == STAGE_2 ? 2 : 3; }
            case 2 -> { int tt = gi(o, "tt"); yield tt == 0 ? 0 : tt == 3 ? 1 : tt == 1 ? 2 : 3; }
            default -> isBasicEnergy(f) ? type(f) : 50;
        };
    }

    private void drawPool(DrawContext ctx, JsonObject L, Stats s, int px, int top, int colW, int h, int mx, int my, String[] hover) {
        Ui.roundGradient(ctx, px, top, colW, h, SURF2, SURF);
        Ui.outline(ctx, px, top, colW, h, LINE);
        // fila 1: buscador · orden · sólo disponibles
        int y1 = top + 3;
        Text sortL = Text.translatable("tcgshop.league.db.sort", Text.translatable("tcgshop.league.db.sort." + SORTS[sort]));
        Text availL = Text.translatable("tcgshop.league.db.only_avail");
        int sortW = Math.min(Ui.chipWidth(sortL), colW / 3), availW = Math.min(Ui.chipWidth(availL), colW / 4);
        int searchW = colW - 8 - sortW - availW - 6;
        searchBox(ctx, px + 4, y1, searchW, 12, mx, my);
        int x = px + 4 + searchW + 3;
        Ui.chip(ctx, x, y1, sortW, 12, sortL, false, Ui.in(mx, my, x, y1, sortW, 12), 0xFF8E7BE8, 0);
        if (Ui.in(mx, my, x, y1, sortW, 12)) tip(Text.translatable("tcgshop.league.db.hint.sort"), mx, my);
        hits.add(new Hit(x, y1, sortW, 12, (b, sh) -> { click(); sort = Math.floorMod(sort + (b == 1 ? -1 : 1), SORTS.length); poolScroll = 0; }));
        x += sortW + 3;
        Ui.chip(ctx, x, y1, availW, 12, availL, onlyAvail, Ui.in(mx, my, x, y1, availW, 12), 0xFF4ADE80, 0);
        if (Ui.in(mx, my, x, y1, availW, 12)) tip(Text.translatable("tcgshop.league.db.hint.only_avail"), mx, my);
        hits.add(new Hit(x, y1, availW, 12, (b, sh) -> { click(); onlyAvail = !onlyAvail; poolScroll = 0; }));
        // fila 2: clase · tipo · set
        int y2 = top + 18;
        x = px + 4;
        for (int k = 0; k < CATS.length; k++) {
            Text label = Text.translatable("tcgshop.league.deck.filter." + CATS[k]);
            int w = Math.min(Ui.chipWidth(label), (colW - 8) / 7);
            Ui.chip(ctx, x, y2, w, 12, label, cat == k, Ui.in(mx, my, x, y2, w, 12), 0xFF4FB4FF, 0);
            int kk = k;
            hits.add(new Hit(x, y2, w, 12, (b, sh) -> { click(); cat = kk; poolScroll = 0; }));
            x += w + 2;
        }
        x += 2;
        Text setL = setFilter.isEmpty() ? Text.translatable("tcgshop.league.db.set_all") : Text.literal(setFilter);
        int setW = Math.min(Ui.chipWidth(setL), 44);
        int typesW = px + colW - 4 - setW - 3 - x;
        int dot = Math.max(5, Math.min(9, typesW / TYPES.length - 1));
        for (int t : TYPES) {
            boolean sel = typeFilter == t, hov = Ui.in(mx, my, x, y2 + 1, dot, 10);
            int cx = x + dot / 2, cy = y2 + 6, r = Math.max(2, dot / 2);
            if (sel || hov) StylePainter.circle(ctx, cx, cy, r + 1, 0xFFFFFFFF);
            StylePainter.circle(ctx, cx, cy, r, typeFilter < 0 || sel ? ClientPtcg.typeColor(t) : withAlpha(ClientPtcg.typeColor(t), 0.35f));
            if (hov) tip(Text.translatable("tcgshop.league.db.type." + t), mx, my);
            hits.add(new Hit(x, y2 + 1, dot, 10, (b, sh) -> { click(); typeFilter = typeFilter == t ? -1 : t; poolScroll = 0; }));
            x += dot + 1;
        }
        int setX = px + colW - 4 - setW;
        Ui.chip(ctx, setX, y2, setW, 12, setL, !setFilter.isEmpty(), Ui.in(mx, my, setX, y2, setW, 12), 0xFFE0A040, 0);
        if (Ui.in(mx, my, setX, y2, setW, 12)) tip(Text.translatable("tcgshop.league.db.hint.set"), mx, my);
        hits.add(new Hit(setX, y2, setW, 12, (b, sh) -> {
            click();
            if (sets.isEmpty()) return;
            int i = setFilter.isEmpty() ? -1 : sets.indexOf(setFilter);
            i += b == 1 ? -1 : 1;
            if (i < -1) i = sets.size() - 1;
            setFilter = i < 0 || i >= sets.size() ? "" : sets.get(i);
            poolScroll = 0;
        }));

        // rejilla de cartas
        List<String> items = filtered(L, s);
        int gy = top + 33, gh = h - 42, gap = 4; // abajo queda la línea de controles
        poolX = px; poolY = gy; poolW = colW; poolH = gh;
        int cw = Math.max(22, Math.min(34, (colW - 10) / 6 - gap)), chh = Math.round(cw / Draw.CARD_RATIO);
        int cols = Math.max(1, (colW - 8 + gap) / (cw + gap)), rowH = chh + gap + 7;
        int rows = (items.size() + cols - 1) / cols;
        poolScroll = MathHelper.clamp(poolScroll, 0, Math.max(0, rows * rowH - gh + 4));
        ctx.enableScissor(px + 1, gy, px + colW - 1, gy + gh);
        for (int k = 0; k < items.size(); k++) {
            String f = items.get(k);
            boolean energy = isBasicEnergy(f);
            int x0 = px + 5 + (k % cols) * (cw + gap), y0 = gy + 2 + (k / cols) * rowH - (int) poolScroll;
            if (y0 + rowH < gy || y0 > gy + gh) continue;
            int inDeck = s.card(f);
            Text why = cantAdd(f, s);
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
            small(ctx, cnt, x0 + cw / 2f - tr().getWidth(cnt) * 0.3f, y0 + chh + 1.5f, 0.6f, can ? 0xFFFFD34D : 0xFF777788);
            int hy = Math.max(y0, gy), hh = Math.min(y0 + chh, gy + gh) - hy; // sólo la parte visible se puede pulsar
            if (hh > 0) hits.add(new Hit(x0, hy, cw, hh, (b, shift) -> {
                if (b == 1) remove(f, shift ? 99 : 1);
                else add(f, shift ? (energy ? 10 : MAX_COPIES) : 1);
            }));
        }
        ctx.disableScissor();
        // barra de desplazamiento
        if (rows * rowH > gh) {
            float frac = (float) (poolScroll / Math.max(1, rows * rowH - gh + 4));
            int bh = Math.max(12, gh * gh / (rows * rowH));
            int by = gy + Math.round((gh - bh) * frac);
            Ui.rounded(ctx, px + colW - 4, by, 2, bh, 1, 0x88FFFFFF);
        }
        if (items.isEmpty()) {
            boolean anyFilter = cat != 0 || typeFilter >= 0 || !setFilter.isEmpty() || onlyAvail || !query.isBlank();
            if (!anyFilter) wrap(ctx, Text.translatable("tcgshop.league.no_pool"), px + 6, gy + 6, colW - 12, Ui.MUTED);
            else {
                int yy = wrap(ctx, Text.translatable("tcgshop.league.db.no_match"), px + 6, gy + 6, colW - 12, Ui.MUTED);
                Text rl = Text.translatable("tcgshop.league.db.reset_filters");
                int rw = Ui.chipWidth(rl) + 6;
                Ui.button(ctx, px + 6, yy + 4, rw, 13, rl, mx, my, true, 0xFF4A5470);
                hits.add(new Hit(px + 6, yy + 4, rw, 13, (b, sh) -> { click(); resetFilters(); }));
            }
        }
        // ayuda de controles al pie
        small(ctx, Text.translatable("tcgshop.league.db.controls"), px + 4, top + h - 7, 0.55f, 0xFF7A80A0);
    }

    private void resetFilters() {
        cat = 0;
        typeFilter = -1;
        setFilter = "";
        onlyAvail = false;
        query = "";
        poolScroll = 0;
    }

    private int wrap(DrawContext ctx, Text t, int x, int y, int w, int color) {
        for (OrderedText line : tr().wrapLines(t, w)) {
            ctx.drawText(tr(), line, x, y, color, false);
            y += 10;
        }
        return y;
    }

    private void searchBox(DrawContext ctx, int x, int y, int w, int h, int mx, int my) {
        boolean hov = Ui.in(mx, my, x, y, w, h);
        Ui.rounded(ctx, x, y, w, h, 4, focused ? 0xEE0E1018 : hov ? 0xCC161A26 : 0xAA0E1018);
        Ui.roundedOutline(ctx, x, y, w, h, 4, focused ? 0xFFFFFFFF : 0x66FFFFFF);
        ctx.drawText(tr(), "⌕", x + 3, y + 2, 0xFFAAAAAA, false);
        int tx = x + 12, tw = w - 24;
        String shownText = query;
        while (!shownText.isEmpty() && tr().getWidth(shownText) > tw) shownText = shownText.substring(1); // se ve el final
        if (query.isEmpty() && !focused) ctx.drawText(tr(), Ui.ellipsis(Text.translatable("tcgshop.league.db.search").getString(), tw), tx, y + 2, 0xFF666A80, false);
        else ctx.drawText(tr(), shownText, tx, y + 2, 0xFFFFFFFF, false);
        if (focused && (System.currentTimeMillis() / 500) % 2 == 0) {
            int cx = tx + tr().getWidth(shownText) + 1;
            ctx.fill(cx, y + 2, cx + 1, y + h - 2, 0xFFFFFFFF);
        }
        hits.add(new Hit(x, y, w - (query.isEmpty() ? 0 : 11), h, (b, sh) -> {
            focused = true;
            if (b == 1) { query = ""; poolScroll = 0; }
        }));
        if (!query.isEmpty()) {
            boolean xh = Ui.in(mx, my, x + w - 11, y, 11, h);
            ctx.drawText(tr(), "×", x + w - 9, y + 2, xh ? 0xFFFF8A80 : 0xFF9A9AAE, false);
            hits.add(new Hit(x + w - 11, y, 11, h, (b, sh) -> { query = ""; poolScroll = 0; focused = true; }));
        }
    }

    // ------------------------------------------------------------------ derecha: resumen, avisos y lista del mazo

    private void drawDeck(DrawContext ctx, Stats s, List<Issue> issues, int dx, int top, int dw, int h, int mx, int my, String[] hover) {
        Ui.roundGradient(ctx, dx, top, dw, h, SURF2, SURF);
        Ui.outline(ctx, dx, top, dw, h, LINE);
        int x = dx + 4, w = dw - 8, y = top + 3;
        // reparto Pokémon / Entrenador / Energía con barra de 60
        small(ctx, Text.translatable("tcgshop.league.deck.split", s.pokemon, s.trainer, s.energy), x, y, 0.7f, 0xFFDDE3F0);
        y += 8;
        Ui.rounded(ctx, x, y, w, 5, 2, 0xFF0B0D13);
        int px1 = Math.round(w * Math.min(1f, s.pokemon / (float) DECK_SIZE));
        int px2 = Math.round(w * Math.min(1f, (s.pokemon + s.trainer) / (float) DECK_SIZE));
        int px3 = Math.round(w * Math.min(1f, s.n / (float) DECK_SIZE));
        if (px1 > 0) ctx.fill(x, y + 1, x + px1, y + 4, COL_P);
        if (px2 > px1) ctx.fill(x + px1, y + 1, x + px2, y + 4, COL_T);
        if (px3 > px2) ctx.fill(x + px2, y + 1, x + px3, y + 4, COL_E);
        if (Ui.in(mx, my, x, y - 8, w, 13)) tip(Text.translatable("tcgshop.league.db.hint.split"), mx, my);
        y += 8;
        // curva: fases de Pokémon · tipos de Entrenador · Energía por tipo
        List<int[]> bars = new ArrayList<>(); // valor, color
        List<String> barLabels = new ArrayList<>();
        bars.add(new int[]{s.basics, COL_P}); barLabels.add(Text.translatable("tcgshop.league.db.curve.basic").getString());
        bars.add(new int[]{s.stage1, COL_P}); barLabels.add(Text.translatable("tcgshop.league.db.curve.s1").getString());
        bars.add(new int[]{s.stage2, COL_P}); barLabels.add(Text.translatable("tcgshop.league.db.curve.s2").getString());
        if (s.otherStage > 0) { bars.add(new int[]{s.otherStage, COL_P}); barLabels.add(Text.translatable("tcgshop.league.db.curve.other").getString()); }
        int sep1 = bars.size();
        String[] tts = {"item", "supporter", "stadium", "tool"};
        for (int i = 0; i < 4; i++) { bars.add(new int[]{s.trainerType[i], COL_T}); barLabels.add(Text.translatable("tcgshop.league.db.curve." + tts[i]).getString()); }
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
            if (v > 0) small(ctx, String.valueOf(v), (x0 + x1) / 2f - tr().getWidth(String.valueOf(v)) * 0.25f, baseY - bh - 5, 0.5f, 0xFFFFFFFF);
            String lab = barLabels.get(i);
            float ls = Math.min(0.5f, (bw + 1) / Math.max(1, tr().getWidth(lab)));
            small(ctx, lab, (x0 + x1) / 2f - tr().getWidth(lab) * ls / 2f, baseY + 1.5f, ls, 0xFF9AA0B8);
            if (Ui.in(mx, my, x0, y, Math.max(1, x1 - x0), chartH)) tip(Text.literal(lab + ": " + v), mx, my);
            bx += bw + gapX;
        }
        y += chartH + 1;
        // avisos (errores primero)
        int maxIssues = h > 220 ? 5 : h > 170 ? 3 : 2;
        int shownIssues = 0;
        for (Issue is : issues) {
            if (shownIssues == maxIssues) {
                int left = issues.size() - maxIssues;
                small(ctx, Text.translatable("tcgshop.league.db.more", left), x, y, 0.6f, 0xFF9AA0B8);
                if (Ui.in(mx, my, x, y, w, 7)) {
                    List<OrderedText> all = new ArrayList<>();
                    for (Issue i2 : issues) all.addAll(tr().wrapLines(Text.literal(issueIcon(i2.level) + " ").append(i2.text), 200));
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
        // lista agrupada
        int ly = y, lh = top + h - 3 - ly;
        deckX = dx; deckY = ly; deckW = dw; deckH = lh;
        List<String> keys = new ArrayList<>(s.byCard.keySet());
        keys.sort(Comparator.comparingInt(this::kind).thenComparingInt(this::stageOrder).thenComparing(this::name, String.CASE_INSENSITIVE_ORDER));
        int rowH = 10;
        List<Object> rows = new ArrayList<>(); // Integer = cabecera de clase, String = carta
        int lastKind = 0;
        for (String f : keys) {
            int k = kind(f);
            if (k != lastKind) { rows.add(k); lastKind = k; }
            rows.add(f);
        }
        deckScroll = MathHelper.clamp(deckScroll, 0, Math.max(0, rows.size() * rowH - lh + 2));
        ctx.enableScissor(dx + 1, ly, dx + dw - 1, ly + lh);
        for (int i = 0; i < rows.size(); i++) {
            int ry = ly + i * rowH - (int) deckScroll;
            if (ry + rowH < ly || ry > ly + lh) continue;
            Object r = rows.get(i);
            if (r instanceof Integer k) {
                int cnt = k == 1 ? s.pokemon : k == 2 ? s.trainer : s.energy;
                int c = k == 1 ? COL_P : k == 2 ? COL_T : COL_E;
                ctx.fill(x, ry + 4, x + 2, ry + 8, c);
                small(ctx, Text.translatable("tcgshop.league.db.group." + k, cnt).formatted(Formatting.BOLD), x + 4, ry + 3, 0.65f, c);
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
            // botones − / +
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
        if (keys.isEmpty()) wrap(ctx, Text.translatable("tcgshop.league.db.empty_hint"), x, ly + 2, w, Ui.MUTED);
    }

    private static void miniButton(DrawContext ctx, int x, int y, int s, String label, boolean hover, boolean enabled) {
        Ui.rounded(ctx, x, y, s, s, 2, !enabled ? 0x30FFFFFF : hover ? 0xFF4A5A88 : 0xFF2A3048);
        Ui.roundedOutline(ctx, x, y, s, s, 2, hover && enabled ? 0xFFFFFFFF : 0x55FFFFFF);
        ctx.drawText(tr(), label, x + (s - tr().getWidth(label)) / 2 + 1, y, enabled ? 0xFFFFFFFF : 0xFF66667A, false);
    }

    // ------------------------------------------------------------------ entrada (el teléfono la reenvía)

    /** Clic dentro del editor. Devuelve true si lo ha usado. */
    public boolean mouseClicked(double mx, double my, int button) {
        if (!shown()) return false;
        boolean shift = Screen.hasShiftDown();
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (Ui.in(mx, my, h.x, h.y, h.w, h.h)) {
                focused = false; // el buscador vuelve a enfocarse si el clic era suyo
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

    /** Teclas: con el buscador activo se las queda todas (Esc / Intro lo cierran); si no, Ctrl+Z deshace y Ctrl+F busca. */
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

    /** Llamar cuando el teléfono deja de dibujar el editor (otra pestaña / otra app) para soltar el foco y los clics. */
    public void hide() {
        lastRender = 0;
        focused = false;
        hits.clear();
    }

    public boolean searchFocused() { return shown() && focused; }

    /** Si se ha dibujado hace nada (si el teléfono cambia de pestaña deja de recibir entrada solo). */
    private boolean shown() { return System.currentTimeMillis() - lastRender < 250; }
}

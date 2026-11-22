package dev.tcgshop.client.screen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import dev.tcgshop.TcgSounds;
import dev.masterball.api.card.CardInfo;
import dev.tcgshop.client.ClientPtcg;
import dev.tcgshop.client.PtcgAudio;
import dev.tcgshop.client.binder.StylePainter;
import dev.tcgshop.client.binder.Ui;
import dev.tcgshop.client.screen.duel.DuelFx;
import dev.tcgshop.client.screen.duel.DuelPalette;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.function.IntConsumer;
import java.util.function.IntPredicate;

import static dev.tcgshop.client.screen.Draw.withAlpha;

/**
 * Mesa del Pokémon TCG real (motor de twinleaf.gg) con un diseño claro y limpio a pantalla completa:
 *  · Clic en cualquier carta (tuya, del rival, de la mano, el Estadio...) = se amplía ahí mismo con sus ataques y
 *    habilidades; desde la ampliación atacas, usas habilidades, te retiras o juegas la carta.
 *  · Arrastra cartas de la mano a su destino (se ilumina) y tu Activo a la Banca para retirarte.
 *  · Registro detallado (botón ☰ o L): lo que dice el motor más daño recibido, PS restantes, condiciones, premios...
 *  · Pista (botón ?, tecla S): la IA del motor te marca la jugada recomendada.
 *  · Animaciones de ataque, daño flotante, K.O., evolución, monedas, turno y premios. Paneles para todas las decisiones.
 */
public class PtcgDuelScreen extends Screen {
    private record Area(int x, int y, int w, int h, Runnable action) {}

    private record Drop(String key, JsonObject target) {}

    private record LogLine(int turn, String text, int color, double at, @Nullable String card, int kind) {}

    /** Muelle amortiguado: lo que hace que todo se mueva "buttery" (con inercia y un pequeño rebote). */
    private static final class Spr {
        float x, v;

        Spr(float x) { this.x = x; }

        void to(float target, float dt, float k, float d) {
            int n = Math.max(1, (int) Math.ceil(dt / 0.006f));
            float h = dt / n;
            for (int i = 0; i < n; i++) {
                v += (k * (target - x) - d * v) * h;
                x += v * h;
            }
        }
    }

    /** Estado animado de una carta (mano, mesa, ampliación o arrastre). */
    private static final class Anim {
        final Spr x = new Spr(0), y = new Spr(0), a = new Spr(0), s = new Spr(1), rx = new Spr(0), ry = new Spr(0), lift = new Spr(0);
        String f = "";
        boolean placed;

        void tilt(float trx, float tryy, float dt) {
            rx.to(trx, dt, 170, 13);
            ry.to(tryy, dt, 170, 13);
        }
    }

    private static final class Fx {
        String text;
        int color, kind; // kind: 0 texto, 1 daño, 2 curación, 3 K.O.
        float x, y, scale, drift;
        double start, life;
    }

    /** Pokémon debilitado: su carta tiembla, se apaga y se hunde en la mesa. */
    private static final class KoGhost {
        String f;
        float x, y, w, h;
        double start;
        boolean mine;
    }

    /** Carta de Premio que vuela a la mano de quien la coge, dándose la vuelta. */
    private static final class PrizeFly {
        float x0, y0, x1, y1, w, h;
        double start;
    }

    private static final class Particle {
        float x, y, vx, vy, size;
        int color;
        double start, life;
    }

    private static final class Fly {
        String f;
        float x0, y0, x1, y1, w, h;
        double start;
    }

    private static final int TOP = 1, BOTTOM = 2, BOARD = 0, ACTIVE = 1, BENCH = 2;
    // paleta clara
    private static final int INK = 0xFF22324A, SOFT = 0xFF6B7F99, LINE = 0xFFD3E0EE, PANEL2 = 0xFFF3F7FC,
            BLUE = 0xFF2F8CFF, GREEN = 0xFF22B866, RED = 0xFFFF4D6D, GOLD = 0xFFFFB020, VIOLET = 0xFF9B6BFF;
    private static final int[] COND_COLORS = {0xFFFFC21A, 0xFFFF6FD1, 0xFF7C93FF, 0xFFA45CFF, 0xFFFF7A2E};
    private static final Random RNG = new Random();

    private JsonObject v, prev;
    private final List<Area> areas = new ArrayList<>();
    private final Map<String, int[]> rects = new HashMap<>();
    private final List<Drop> drops = new ArrayList<>();
    private final List<Fx> fx = new ArrayList<>();
    private final List<Particle> parts = new ArrayList<>();
    private final List<Particle> confetti = new ArrayList<>(); // por encima de la pantalla final
    private final List<Fly> flies = new ArrayList<>();
    private final List<LogLine> log = new ArrayList<>();
    private final List<KoGhost> koGhosts = new ArrayList<>();
    private final List<PrizeFly> prizeFlies = new ArrayList<>();
    private final Map<String, DuelFx.HpBar> hpBars = new HashMap<>();
    // transiciones: registro deslizante, velo de decisiones y confeti del final
    private float drawerT;
    private boolean confettiDone;

    // ratón / mano
    private List<Text> tooltip;
    private int hoverHand = -1, selectedHand = -1, pressHand = -1, dragHand = -1;
    private boolean pressActive, dragActive, dragging, retreatMode, concedeArmed, peek, menuOpen;
    private double pressX, pressY, concedeAt, hoverAt;
    private float dragX, dragY;
    private int logScroll, lastHoverHand = -1;
    @Nullable private String pressKey;

    // ampliación de carta
    @Nullable private String zoomKey, zoomCard;
    private double zoomAt;
    private int[] zoomFrom;

    // decisiones
    private final Set<Integer> picks = new LinkedHashSet<>();
    private final JsonArray assigns = new JsonArray();
    private int promptId = -1, srcPick = -1;
    @Nullable private JsonObject srcTarget;
    private final Map<String, Integer> dmgMap = new HashMap<>();

    // efectos
    private String banner = "", bannerSub = "";
    private int bannerColor;
    private double promptAt = now(), bannerAt = -10, shakeAt = -10, coinAt = -10, receivedAt = now(), changedAt = now();
    private float shakePow;
    private boolean coinHeads, coinMine, wasMyTurn, wasOver;
    private int lastLogId = -1, lastTurn = -1;
    private String toast = "";
    private int toastColor;
    private double toastAt = -10;
    @Nullable private JsonObject suggestion;
    private int suggestKey;
    private boolean suggestPending;
    private double errorAt = -10;
    private int lastPlayedHand = -1;
    private final List<double[]> rings = new ArrayList<>(); // x, y, color, start, radio
    private double slashAt = -10, vignetteAt = -10, turnFxAt = -10, startedAt = now();
    private int vignetteColor;
    private boolean turnFxMine, musicStarted, endSoundDone;
    private String bannerCard = "";
    private final Map<String, Double> hitAt = new HashMap<>(), lungeAt = new HashMap<>();
    private double lastHoverSound;
    private float slashX, slashY;
    private int slashColor;

    // animación continua (muelles)
    private final List<Anim> handAnim = new ArrayList<>();
    private final Map<String, Anim> boardAnim = new HashMap<>();
    private final Anim zoomAnim = new Anim(), dragAnim = new Anim();
    private final Map<String, Optional<Identifier>> texCache = new HashMap<>();
    private double lastFrame = now();
    private float dt, lastDragX, lastDragY, dragVx, dragVy;
    private boolean handReady, boardReady;
    private int mouseXNow, mouseYNow;
    // carta jugada por el rival, mostrada en grande un momento
    private String showF = "";
    private double showAt = -10;
    // inicio de partida: presentación "VS" y moneda 3D grande
    private final double introAt = now();
    private double bigCoinAt = -10;
    private boolean introSkipped, bigCoinLanded;
    private String coinWho = "";
    private float handRaise = 1;
    // pila de descartes ampliada
    @Nullable private String discardSide;
    private double discardAt = -10, discardScroll;

    private double introEnd() {
        if (introSkipped) return 0;
        return Math.max(introAt + 1.9, bigCoinAt > 0 ? bigCoinAt + 2.8 : 0);
    }

    private boolean intro() { return now() < introEnd(); }

    public PtcgDuelScreen(String json) {
        super(Text.translatable("tcgshop.duel.title"));
        update(json);
    }

    private static double now() { return System.nanoTime() / 1e9; }

    @Override
    protected void init() {
        super.init();
        if (!musicStarted && v != null && !over()) {
            musicStarted = true;
            PtcgAudio.startMusic(b(v, "ai") ? "battle.pvn.default" : "battle.pvp.default");
            PtcgAudio.play("poke_ball.open", SoundEvents.BLOCK_CHEST_OPEN, 1f, 0.7f);
        }
    }

    @Override
    public void removed() {
        PtcgAudio.stopMusic();
        super.removed();
    }

    @Override
    public boolean shouldPause() { return false; }

    // ================================================================== datos

    public void update(String json) {
        JsonObject nv;
        try { nv = JsonParser.parseString(json).getAsJsonObject(); } catch (Exception e) { return; }
        if (!nv.has("phase")) return;
        ClientPtcg.absorbDefs(nv);
        prev = v;
        v = nv;
        receivedAt = now();
        JsonObject p = prompt();
        int id = p == null ? -1 : p.get("id").getAsInt();
        if (id != promptId) {
            promptId = id;
            promptAt = now();
            resetPrompt();
            peek = false;
            if (id >= 0) zoomKey = null;
        }
        if (v.has("error")) errorToast(ClientPtcg.tr("msg", s(v, "error"), null));
        String sg = s(v, "suggest");
        if (!sg.isEmpty()) {
            suggestPending = false;
            if (sg.equals("none")) { suggestion = null; toast(ui("suggest_none"), SOFT); }
            else try {
                suggestion = JsonParser.parseString(sg).getAsJsonObject();
                suggestKey = stateKey();
                sound(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 1.4f, 0.8f);
            } catch (Exception e) { suggestion = null; }
        } else if (suggestion != null && stateKey() != suggestKey) suggestion = null;
        int turn = i(v, "turn", 0);
        if (turn != lastTurn) {
            if (turn > 0) addLog("— " + ui("turn_n", turn) + " · " + (myTurn() ? ui("you") : s(side("opp"), "name")) + " —",
                    DuelPalette.side(myTurn()), null, DuelPalette.K_TURN);
            lastTurn = turn;
        }
        if (prev == null && i(v, "turn", 0) > 1) introSkipped = true;
        processLogs(prev == null);
        if (prev != null) diffEffects(prev, v);
        boolean mt = myTurn();
        if (prev != null && mt != wasMyTurn && !over()) {
            turnFxAt = now();
            turnFxMine = mt;
            if (mt) {
                PtcgAudio.play("item.pokedex.open", SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), 1f, 0.8f);
                sound(SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), 1.5f, 0.6f);
            } else PtcgAudio.play("pc.off", SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), 0.9f, 0.6f);
        }
        wasMyTurn = mt;
        if (!mt || prompt() != null) { selectedHand = -1; retreatMode = false; cancelDrag(); }
        if (over() && !wasOver) {
            changedAt = now();
            wasOver = true;
            zoomKey = null;
            PtcgAudio.stopMusic();
            boolean win = i(v, "winner", -1) == i(v, "meIndex", 0);
            if (win) {
                PtcgAudio.play("evolution.full", SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1f, 0.9f);
                sound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1f, 0.6f);
            } else sound(SoundEvents.BLOCK_BEACON_DEACTIVATE, 0.8f, 0.9f);
        }
    }

    private int stateKey() {
        return (String.valueOf(v.get("me")) + v.get("opp") + v.get("prompt") + v.get("turn")).hashCode();
    }

    private void resetPrompt() {
        picks.clear();
        while (assigns.size() > 0) assigns.remove(0);
        srcPick = -1;
        srcTarget = null;
        dmgMap.clear();
    }

    @Nullable
    private JsonObject prompt() { return v.has("prompt") && v.get("prompt").isJsonObject() ? v.getAsJsonObject("prompt") : null; }

    private boolean myTurn() { return b(v, "myTurn") && !over(); }

    private boolean canAct() { return myTurn() && prompt() == null && !b(v, "waiting"); }

    private boolean over() { return i(v, "phase", 0) == 7 || i(v, "winner", -1) != -1; }

    @Nullable
    private JsonObject side(String k) { return v.has(k) && v.get(k).isJsonObject() ? v.getAsJsonObject(k) : null; }

    @Nullable
    private static JsonObject obj(@Nullable JsonElement e) { return e != null && e.isJsonObject() ? e.getAsJsonObject() : null; }

    private static int i(@Nullable JsonObject o, String k, int def) { return o != null && o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsInt() : def; }

    private static String s(@Nullable JsonObject o, String k) { return o != null && o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : ""; }

    private static boolean b(@Nullable JsonObject o, String k) { return o != null && o.has(k) && o.get(k).isJsonPrimitive() && o.get(k).getAsBoolean(); }

    private static JsonArray arr(@Nullable JsonObject o, String k) { return o != null && o.has(k) && o.get(k).isJsonArray() ? o.getAsJsonArray(k) : new JsonArray(); }

    private static String ui(String key, Object... args) { return Text.translatable("tcgshop.ptcg.ui." + key, args).getString(); }

    /** Nombre de la carta en el idioma del juego (para mostrar). */
    private static String name(String f) {
        JsonObject d = ClientPtcg.def(f);
        return ClientPtcg.cardName(f, d == null ? f : s(d, "n"));
    }

    /** Nombre de la carta en el motor (inglés): para comparar evoluciones y registros. */
    private static String ename(String f) {
        JsonObject d = ClientPtcg.def(f);
        return d == null ? f : s(d, "n");
    }

    private JsonArray handCards() { return arr(side("me"), "handCards"); }

    @Nullable
    private JsonObject monAt(String sideKey, int slot, int index) {
        JsonObject pl = side(sideKey);
        if (pl == null) return null;
        if (slot == ACTIVE) return obj(pl.get("active"));
        JsonArray bench = arr(pl, "bench");
        return index < bench.size() ? obj(bench.get(index)) : null;
    }

    private static boolean isMonKey(@Nullable String key) {
        return key != null && (key.startsWith("me:") || key.startsWith("opp:")) && key.split(":").length == 3;
    }

    @Nullable
    private JsonObject monByKey(@Nullable String key) {
        if (!isMonKey(key)) return null;
        String[] p = key.split(":");
        try { return monAt(p[0], Integer.parseInt(p[1]), Integer.parseInt(p[2])); } catch (NumberFormatException e) { return null; }
    }

    private static JsonObject target(int player, int slot, int index) {
        JsonObject t = new JsonObject();
        t.addProperty("player", player);
        t.addProperty("slot", slot);
        t.addProperty("index", index);
        return t;
    }

    private static JsonObject targetOf(JsonObject t) { return target(i(t, "player", 2), i(t, "slot", 1), i(t, "index", 0)); }

    private static boolean sameTarget(@Nullable JsonObject a, @Nullable JsonObject b) {
        return a != null && b != null && i(a, "player", 0) == i(b, "player", 0) && i(a, "slot", 0) == i(b, "slot", 0) && i(a, "index", 0) == i(b, "index", 0);
    }

    private static boolean inTargets(JsonArray list, JsonObject t) {
        for (JsonElement e : list) if (sameTarget(obj(e), t)) return true;
        return false;
    }

    private static String keyOf(JsonObject t) {
        return (i(t, "player", 2) == BOTTOM ? "me" : "opp") + ":" + i(t, "slot", 1) + ":" + (i(t, "slot", 1) == ACTIVE ? 0 : i(t, "index", 0));
    }

    // ================================================================== acciones

    private void send(JsonObject o) { ClientPtcg.send(o); }

    private void act(JsonObject a) {
        JsonObject o = new JsonObject();
        o.addProperty("k", "act");
        o.add("a", a);
        send(o);
        suggestion = null;
    }

    private void act(String t) {
        JsonObject a = new JsonObject();
        a.addProperty("t", t);
        act(a);
    }

    private void resolve(JsonElement result) {
        JsonObject p = prompt();
        if (p == null) return;
        JsonObject o = new JsonObject();
        o.addProperty("k", "resolve");
        o.addProperty("id", p.get("id").getAsInt());
        o.add("r", result);
        send(o);
        click();
    }

    private void auto() {
        JsonObject o = new JsonObject();
        o.addProperty("k", "auto");
        send(o);
        click();
    }

    private void askSuggestion() {
        if (!canAct() || suggestPending) return;
        suggestPending = true;
        JsonObject o = new JsonObject();
        o.addProperty("k", "suggest");
        send(o);
        click();
    }

    private void play(int hand, JsonObject t) {
        JsonArray h = handCards();
        if (hand < 0 || hand >= h.size()) return;
        String f = h.get(hand).getAsString();
        int[] from = rects.get("hand:" + hand);
        int[] to = rects.get(i(t, "slot", 0) == BOARD ? "zone:board" : keyOf(t));
        if (from != null && to != null) {
            Fly fl = new Fly();
            fl.f = f;
            fl.x0 = dragging ? dragX - hw / 2f : from[0];
            fl.y0 = dragging ? dragY - hh / 2f : from[1];
            fl.w = hw;
            fl.h = hh;
            fl.x1 = to[0] + to[2] / 2f - hw / 2f;
            fl.y1 = to[1] + to[3] / 2f - hh / 2f;
            fl.start = now();
            flies.add(fl);
        }
        lastPlayedHand = hand;
        if (to != null) ring(to[0] + to[2] / 2f, to[1] + to[3] / 2f, BLUE, Math.max(to[2], to[3]) * 0.9f);
        JsonObject a = new JsonObject();
        a.addProperty("t", "play");
        a.addProperty("hand", hand);
        a.add("target", t);
        act(a);
        selectedHand = -1;
        zoomKey = null;
        sound(TcgSounds.CARD_SLIDE, 1.1f, 0.7f);
    }

    private void retreat(int bench) {
        JsonObject a = new JsonObject();
        a.addProperty("t", "retreat");
        a.addProperty("bench", bench);
        act(a);
        retreatMode = false;
        sound(TcgSounds.CARD_FLIP, 0.9f, 0.7f);
    }

    private void attack(String name) {
        JsonObject a = new JsonObject();
        a.addProperty("t", "attack");
        a.addProperty("name", name);
        act(a);
        zoomKey = null;
    }

    private void ability(String name, int slot, int index) {
        JsonObject a = new JsonObject();
        a.addProperty("t", "ability");
        a.addProperty("name", name);
        a.add("target", target(BOTTOM, slot, index));
        act(a);
        zoomKey = null;
        sound(SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, 1.3f, 0.7f);
    }

    /** Aviso de "no se puede": sacudida, icono rojo y sonido; la carta que intentaste jugar tiembla en la mano. */
    private void errorToast(String s) {
        toast(s, RED);
        errorAt = now();
        sound(SoundEvents.ENTITY_VILLAGER_NO, 1.1f, 0.6f);
        sound(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), 0.6f, 0.6f);
    }

    private void toast(String s, int color) {
        toast = s;
        toastColor = color;
        toastAt = now();
    }

    private void banner(String text, int color, String sub) {
        banner = text;
        bannerSub = sub;
        bannerColor = color;
        bannerAt = now();
    }

    private void sound(SoundEvent e, float pitch, float vol) {
        MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(e, pitch, vol));
    }

    private void click() { PtcgAudio.play("gui.click", SoundEvents.UI_BUTTON_CLICK.value(), 1.1f, 0.45f); }

    private void cancelDrag() {
        dragging = false;
        dragHand = -1;
        dragActive = false;
        pressHand = -1;
        pressActive = false;
        pressKey = null;
    }

    private void openDiscard(String sideKey) {
        discardSide = sideKey;
        discardAt = now();
        discardScroll = 0;
        retreatMode = false;
        selectedHand = -1;
        sound(TcgSounds.CARD_SLIDE, 1.1f, 0.5f);
    }

    /** Pila de descartes ampliada: todas las cartas, de la más reciente a la más antigua, con pestañas tú / rival. */
    private void discardView(DrawContext ctx, int mx, int my, double t) {
        JsonArray disc = arr(side(discardSide), "discard");
        float in = (float) Draw.easeOutCubic(MathHelper.clamp((t - discardAt) / 0.25, 0, 1));
        ctx.fill(0, 0, W, H, withAlpha(0xFF0E1E36, 0.55f * in));
        areas.add(new Area(0, 0, W, H, () -> discardSide = null));
        int pw = Math.min(W - 30, 480), ph = H - 44, px = cx - pw / 2, py = 22 + Math.round((1 - in) * 24);
        panel(ctx, px, py, pw, ph, withAlpha(0xFFF7FAFE, Math.max(0.2f, in)));
        areas.add(new Area(px, py, pw, ph, () -> {}));
        boolean mine = "me".equals(discardSide);
        ctx.fill(px + 1, py + 1, px + pw - 1, py + 3, mine ? BLUE : RED);
        text(ctx, ui("discard_title"), px + 12, py + 9, INK, 1.15f, false);
        text(ctx, ui("discard_list", disc.size()), px + 12, py + 22, SOFT, 0.7f, false);
        // pestañas
        int tx = px + pw - 12;
        for (String k : new String[]{"opp", "me"}) {
            String l = k.equals("me") ? ui("you") : s(side("opp"), "name");
            int n = arr(side(k), "discard").size(), w = tw(l + " " + n, 0.75f) + 16;
            tx -= w;
            boolean sel = k.equals(discardSide), hov = Ui.in(mx, my, tx, py + 9, w, 14);
            int col = k.equals("me") ? BLUE : RED;
            pill(ctx, tx, py + 9, w, 14, sel ? col : hov ? 0xFFEAF2FB : 0xFFFFFFFF);
            roundOutline(ctx, tx, py + 9, w, 14, sel ? col : LINE);
            text(ctx, l + " " + n, tx + 8, py + 12.5f, sel ? 0xFFFFFFFF : INK, 0.75f, false);
            areas.add(new Area(tx, py + 9, w, 14, () -> { if (!k.equals(discardSide)) { discardSide = k; discardAt = now(); discardScroll = 0; click(); } }));
            tx -= 4;
        }
        int gx = px + 10, gy = py + 36, gw = pw - 20, gh = ph - 46;
        round(ctx, gx, gy, gw, gh, 0xFFE9F0F8);
        if (disc.isEmpty()) {
            textC(ctx, ui("discard_empty"), cx, gy + gh / 2f - 4, SOFT, 1f);
            return;
        }
        int cw = Math.max(34, Math.min(52, gw / 8)), chh = Math.round(cw / Draw.CARD_RATIO), gap = 7;
        int cols = Math.max(1, (gw - 10 + gap) / (cw + gap)), rows = (disc.size() + cols - 1) / cols;
        int x0 = gx + (gw - (cols * (cw + gap) - gap)) / 2;
        discardScroll = MathHelper.clamp(discardScroll, 0, Math.max(0, rows * (chh + gap) + 8 - gh));
        MatrixStack m = ctx.getMatrices();
        ctx.enableScissor(gx, gy + 1, gx + gw, gy + gh - 1);
        int hovK = -1;
        float hx = 0, hy = 0;
        for (int k = 0; k < disc.size(); k++) {
            String f = disc.get(disc.size() - 1 - k).getAsString();
            float ccx = x0 + (k % cols) * (cw + gap) + cw / 2f, ccy = gy + 6 + (k / cols) * (chh + gap) + chh / 2f - (float) discardScroll;
            if (ccy + chh < gy || ccy - chh > gy + gh) continue;
            boolean hov = Ui.in(mx, my, Math.round(ccx - cw / 2f), Math.round(ccy - chh / 2f), cw, chh) && Ui.in(mx, my, gx, gy, gw, gh);
            if (hov) { hovK = k; hx = ccx; hy = ccy; continue; }
            float ap = (float) Draw.easeOutBack(MathHelper.clamp((t - discardAt - Math.min(k, 30) * 0.015) / 0.28, 0, 1));
            if (ap <= 0) continue;
            m.push();
            m.translate(ccx, ccy + (1 - ap) * 14, 0);
            m.scale(0.7f + 0.3f * ap, 0.7f + 0.3f * ap, 1);
            card3d(ctx, f, cw, chh, 0, 0, Math.min(1, ap), 0.15f, 0, 0f, t);
            m.pop();
            final String ff = f;
            areas.add(new Area(Math.round(ccx - cw / 2f), Math.round(ccy - chh / 2f), cw, chh, () -> openZoom("card", ff)));
        }
        if (hovK >= 0) { // la carta bajo el ratón se levanta y se inclina hacia él
            String f = disc.get(disc.size() - 1 - hovK).getAsString();
            m.push();
            m.translate(hx, hy - 3, 50);
            m.scale(1.18f, 1.18f, 1);
            card3d(ctx, f, cw, chh, MathHelper.clamp(-(my - hy) / (chh * 0.6f), -1, 1) * 14, MathHelper.clamp((mx - hx) / (cw * 0.6f), -1, 1) * 18, 1f, 1f,
                    withAlpha(BLUE, 0.6f), 0f, t);
            m.pop();
            tooltip = describeShort(f);
            final String ff = f;
            areas.add(new Area(Math.round(hx - cw / 2f), Math.round(hy - chh / 2f), cw, chh, () -> openZoom("card", ff)));
        }
        ctx.disableScissor();
        if (rows * (chh + gap) + 8 > gh) {
            float frac = gh / (float) (rows * (chh + gap) + 8);
            int sh = Math.max(12, Math.round(gh * frac)), sy = gy + Math.round((float) (discardScroll / Math.max(1, rows * (chh + gap) + 8 - gh)) * (gh - sh));
            round(ctx, gx + gw - 4, sy, 3, sh, 0x8022324A);
        }
    }

    private void openZoom(String key, String card) {
        zoomKey = key;
        zoomCard = card;
        zoomAt = now();
        zoomFrom = rects.get(key);
        zoomAnim.ry.x = 75; // entra girando y se asienta con un pequeño rebote
        zoomAnim.ry.v = 0;
        zoomAnim.rx.x = -10;
        zoomAnim.rx.v = 0;
        retreatMode = false;
        selectedHand = -1;
        sound(TcgSounds.CARD_FLIP, 1.2f, 0.5f);
    }

    // ================================================================== registro

    private void addLog(String text, int color) { addLog(text, color, null, DuelPalette.K_PLAY); }

    private void addLog(String text, int color, int kind) { addLog(text, color, null, kind); }

    private void addLog(String text, int color, @Nullable String card) { addLog(text, color, card, DuelPalette.K_PLAY); }

    private void addLog(String text, int color, @Nullable String card, int kind) {
        log.add(new LogLine(i(v, "turn", 0), text, color, now(), card == null || card.isEmpty() ? null : card, kind));
        while (log.size() > 300) log.remove(0);
        logScroll = 0;
    }

    /** Carta completa (fullName) de un nombre del motor que acaba de jugar ese lado: Partidario, Estadio, descarte, herramientas... */
    @Nullable
    private String findPlayed(String sideKey, String engineName) {
        if (engineName.isEmpty()) return null;
        JsonObject pl = side(sideKey);
        for (JsonElement e : arr(pl, "supporter")) if (ename(e.getAsString()).equals(engineName)) return e.getAsString();
        if (ename(s(v, "stadium")).equals(engineName)) return s(v, "stadium");
        JsonArray disc = arr(pl, "discard");
        for (int k = disc.size() - 1; k >= Math.max(0, disc.size() - 8); k--) if (ename(disc.get(k).getAsString()).equals(engineName)) return disc.get(k).getAsString();
        List<JsonObject> mons = new ArrayList<>();
        JsonObject a = obj(pl == null ? null : pl.get("active"));
        if (a != null) mons.add(a);
        for (JsonElement b : arr(pl, "bench")) if (b.isJsonObject()) mons.add(b.getAsJsonObject());
        for (JsonObject m : mons) {
            if (ename(s(m, "f")).equals(engineName)) return s(m, "f");
            for (JsonElement tl : arr(m, "tools")) if (ename(tl.getAsString()).equals(engineName)) return tl.getAsString();
        }
        for (Map.Entry<String, JsonObject> d : ClientPtcg.DEFS.entrySet()) if (s(d.getValue(), "n").equals(engineName)) return d.getKey();
        return null;
    }

    /** Pone en el idioma del juego los nombres de cartas que trae un registro del motor. */
    private JsonObject localizeParams(@Nullable JsonObject p, String sideKey) {
        if (p == null) return null;
        JsonObject out = p.deepCopy();
        for (String k : new String[]{"card", "pokemon"}) {
            if (!out.has(k) || !out.get(k).isJsonPrimitive()) continue;
            String en = out.get(k).getAsString();
            String full = findPlayed(sideKey, en);
            if (full == null) full = findPlayed(sideKey.equals("me") ? "opp" : "me", en);
            if (full != null) out.addProperty(k, ClientPtcg.cardName(full, en));
        }
        String act = s(monAt(sideKey, ACTIVE, 0), "f");
        if (!act.isEmpty()) {
            if (out.has("attack")) out.addProperty("attack", localAttack(act, s(p, "attack")));
            if (out.has("ability")) out.addProperty("ability", localPower(act, s(p, "ability")));
        }
        return out;
    }

    private static String localAttack(String f, String en) {
        JsonArray ats = arr(ClientPtcg.def(f), "attacks");
        for (int k = 0; k < ats.size(); k++) if (s(ats.get(k).getAsJsonObject(), "n").equals(en)) return ClientPtcg.attackName(f, k, ats.size(), en);
        return en;
    }

    private static String localPower(String f, String en) {
        JsonArray pws = arr(ClientPtcg.def(f), "powers");
        for (int k = 0; k < pws.size(); k++) if (s(pws.get(k).getAsJsonObject(), "n").equals(en)) return ClientPtcg.powerName(f, k, pws.size(), en);
        return en;
    }

    private void processLogs(boolean initial) {
        String myName = s(side("me"), "name");
        for (JsonElement e : arr(v, "logs")) {
            JsonObject l = e.getAsJsonObject();
            int id = i(l, "i", -1);
            if (id <= lastLogId) continue;
            lastLogId = id;
            JsonObject p = obj(l.get("p"));
            String who = s(p, "name");
            boolean mine = who.equals(myName);
            String sk = mine ? "me" : "opp";
            String m = s(l, "m");
            String card = switch (m) {
                case "LOG_PLAYER_PLAYS_ITEM", "LOG_PLAYER_PLAYS_SUPPORTER", "LOG_PLAYER_PLAYS_STADIUM", "LOG_PLAYER_PLAYS_TOOL",
                     "LOG_PLAYER_PLAYS_BASIC_POKEMON", "LOG_PLAYER_EVOLVES_POKEMON", "LOG_PLAYER_ATTACHES_CARD" -> findPlayed(sk, s(p, "card"));
                case "LOG_PLAYER_USES_ATTACK", "LOG_PLAYER_USES_ABILITY" -> s(monAt(sk, ACTIVE, 0), "f");
                default -> null;
            };
            boolean koLine = m.equals("LOG_POKEMON_KO");
            boolean flipLine = m.equals("LOG_PLAYER_FLIPS_HEADS") || m.equals("LOG_PLAYER_FLIPS_TAILS");
            int lineCol = koLine ? DuelPalette.LOG_KO : m.equals("LOG_PLAYER_EVOLVES_POKEMON") ? DuelPalette.LOG_EVOLVE : who.isEmpty() ? DuelPalette.LOG_SYSTEM : mine ? BLUE : RED;
            addLog(ClientPtcg.tr("log", m, localizeParams(p, sk)), lineCol, card,
                    koLine ? DuelPalette.K_KO : who.isEmpty() || flipLine ? DuelPalette.K_SYSTEM : DuelPalette.K_PLAY);
            boolean flip = m.equals("LOG_PLAYER_FLIPS_HEADS") || m.equals("LOG_PLAYER_FLIPS_TAILS");
            if (flip && i(v, "turn", 0) == 0 && bigCoinAt < 0 && !introSkipped && now() - introAt < 30) {
                // la moneda que decide quién empieza: en grande, en el centro
                bigCoinAt = Math.max(now(), introAt + 1.7);
                bigCoinLanded = false;
                coinHeads = m.endsWith("HEADS");
                coinWho = who;
                coinMine = mine;
                continue;
            }
            if (initial) continue;
            if (!mine && card != null && m.startsWith("LOG_PLAYER_PLAYS_") && !m.equals("LOG_PLAYER_PLAYS_BASIC_POKEMON")) {
                showF = card;
                showAt = now();
            }
            switch (m) {
                case "LOG_PLAYER_USES_ATTACK" -> {
                    String atk = s(monAt(mine ? "me" : "opp", ACTIVE, 0), "f");
                    bannerCard = atk;
                    banner(localAttack(atk, s(p, "attack")), mine ? BLUE : RED, ui(mine ? "you_attack" : "opp_attacks", who));
                    boardAnim(sk + ":1:0").s.v += 3.5f;
                    lungeAt.put((mine ? "me" : "opp") + ":1:0", now());
                    float[] c = center((mine ? "opp" : "me") + ":1:0");
                    if (c != null) slash(c[0], c[1], mine ? BLUE : RED);
                    if (!mine) { vignetteAt = now(); vignetteColor = RED; shakeAt = now(); shakePow = 6; }
                    if (!atk.isEmpty()) PtcgAudio.cry(ename(atk), 1f, 0.9f);
                    sound(SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 0.9f, 0.8f);
                    sound(mine ? SoundEvents.ENTITY_PLAYER_ATTACK_CRIT : SoundEvents.ENTITY_GENERIC_EXPLODE.value(), 1.2f, mine ? 0.6f : 0.35f);
                }
                case "LOG_PLAYER_USES_ABILITY" -> {
                    bannerCard = "";
                    banner(localPower(s(monAt(sk, ACTIVE, 0), "f"), s(p, "ability")), VIOLET, ui("uses_ability", who));
                    PtcgAudio.play("item.use", SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, 1.2f, 0.7f);
                    float[] c = center((mine ? "me" : "opp") + ":1:0");
                    if (c != null) burst(c[0], c[1], VIOLET, 18, 1.6f);
                }
                case "LOG_PLAYER_PLAYS_ITEM", "LOG_PLAYER_PLAYS_SUPPORTER", "LOG_PLAYER_PLAYS_STADIUM", "LOG_PLAYER_PLAYS_TOOL" -> {
                    sound(TcgSounds.CARD_FLIP, mine ? 1.1f : 0.95f, 0.6f);
                    PtcgAudio.play("item.pokedex.click", SoundEvents.UI_BUTTON_CLICK.value(), 1.1f, 0.4f);
                }
                case "LOG_PLAYER_ATTACHES_CARD" -> sound(SoundEvents.BLOCK_AMETHYST_BLOCK_HIT, 1.3f, 0.8f);
                case "LOG_PLAYER_DRAWS_CARD" -> sound(TcgSounds.CARD_SLIDE, 1.3f, 0.35f);
                case "LOG_PLAYER_FLIPS_HEADS", "LOG_PLAYER_FLIPS_TAILS" -> {
                    coinAt = Math.max(now(), coinAt + 1.2); // varias tiradas seguidas: una detrás de otra
                    coinHeads = s(l, "m").endsWith("HEADS");
                    coinMine = mine;
                    sound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.8f, 0.35f);
                }
                case "LOG_POKEMON_KO" -> {
                    String key = findMon(prev, who);
                    float[] c = key == null ? null : center(key);
                    if (c != null) {
                        Fx ko = floatText(ui("ko"), 0xFFFFFFFF, c[0], c[1], 2.6f);
                        ko.kind = 3;
                        ko.life = 1.7;
                        burst(c[0], c[1], GOLD, 40, 3.2f);
                        burst(c[0], c[1], RED, 30, 2.4f);
                        ring(c[0], c[1], DuelPalette.LOG_KO, 70);
                        // fantasma de la carta debilitada (el nuevo estado ya no la trae)
                        JsonObject dead = monByKeyIn(prev, key);
                        int[] r = rects.get(key);
                        if (dead != null && r != null) {
                            KoGhost g = new KoGhost();
                            g.f = s(dead, "f");
                            g.x = r[0];
                            g.y = r[1];
                            g.w = r[2];
                            g.h = r[3];
                            g.start = now();
                            g.mine = key.startsWith("me");
                            koGhosts.add(g);
                        }
                    }
                    shakeAt = now();
                    shakePow = 8;
                    vignetteAt = now();
                    vignetteColor = GOLD;
                    PtcgAudio.cry(who, 0.75f, 0.9f); // el registro trae el nombre del motor
                    PtcgAudio.play("poke_ball.recall", SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST, 1f, 0.8f);
                    sound(SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST, 0.8f, 0.6f);
                }
                default -> {}
            }
        }
    }

    // ================================================================== efectos

    private Fx floatText(String text, int color, float x, float y, float scale) {
        Fx f = new Fx();
        f.text = text;
        f.color = color;
        f.x = x;
        f.y = y;
        f.scale = scale;
        f.start = now();
        f.life = 1.4;
        f.drift = (RNG.nextFloat() - 0.5f) * 18;
        fx.add(f);
        return f;
    }

    /** Número de daño / curación que "explota" y flota: más grande y más rojo cuanto mayor es el golpe. */
    private void damageText(int amount, boolean heal, float x, float y) {
        float sc = heal ? 1.6f : MathHelper.clamp(1.7f + amount / 90f, 1.7f, 3.2f);
        int col = heal ? DuelPalette.HP_HIGH : amount >= 120 ? 0xFFFF2A4A : amount >= 60 ? 0xFFFF5A3C : 0xFFFF8A3C;
        Fx f = floatText((heal ? "+" : "-") + amount, col, x, y, sc);
        f.kind = heal ? 2 : 1;
        f.life = heal ? 1.3 : 1.5;
    }

    @Nullable
    private JsonObject monByKeyIn(@Nullable JsonObject view, @Nullable String key) {
        if (view == null || !isMonKey(key)) return null;
        String[] p = key.split(":");
        JsonObject pl = obj(view.get(p[0]));
        if (pl == null) return null;
        try {
            if (Integer.parseInt(p[1]) == ACTIVE) return obj(pl.get("active"));
            JsonArray bench = arr(pl, "bench");
            int idx = Integer.parseInt(p[2]);
            return idx < bench.size() ? obj(bench.get(idx)) : null;
        } catch (NumberFormatException e) { return null; }
    }

    private void burst(float x, float y, int color, int n, float speed) { burst(parts, x, y, color, n, speed); }

    private void burst(List<Particle> into, float x, float y, int color, int n, float speed) {
        for (int k = 0; k < n; k++) {
            Particle p = new Particle();
            double a = RNG.nextDouble() * Math.PI * 2, sp = speed * (0.4 + RNG.nextDouble());
            p.x = x;
            p.y = y;
            p.vx = (float) (Math.cos(a) * sp);
            p.vy = (float) (Math.sin(a) * sp) - speed * 0.3f;
            p.size = 1 + RNG.nextInt(3);
            p.color = RNG.nextBoolean() ? color : 0xFFFFFFFF;
            p.start = now();
            p.life = 0.6 + RNG.nextDouble() * 0.6;
            into.add(p);
        }
    }

    @Nullable
    private float[] center(String key) {
        int[] r = rects.get(key);
        return r == null ? null : new float[]{r[0] + r[2] / 2f, r[1] + r[3] / 2f};
    }

    private void ring(float x, float y, int color, float radius) { rings.add(new double[]{x, y, color, now(), radius}); }

    private void slash(float x, float y, int color) {
        slashAt = now();
        slashX = x;
        slashY = y;
        slashColor = color;
        burst(x, y, color, 24, 3f);
    }

    /** Daño / curación, evoluciones, energías, condiciones y premios: efectos en pantalla y líneas del registro. */
    private void diffEffects(JsonObject a, JsonObject b) {
        for (String sideKey : new String[]{"me", "opp"}) {
            JsonObject pa = obj(a.get(sideKey)), pb = obj(b.get(sideKey));
            if (pa == null || pb == null) continue;
            String owner = s(pb, "name");
            int col = sideKey.equals("me") ? BLUE : RED;
            int nb = Math.max(arr(pa, "bench").size(), arr(pb, "bench").size());
            for (int idx = -1; idx < nb; idx++) {
                JsonObject ma = idx < 0 ? obj(pa.get("active")) : idx < arr(pa, "bench").size() ? obj(arr(pa, "bench").get(idx)) : null;
                JsonObject mb = idx < 0 ? obj(pb.get("active")) : idx < arr(pb, "bench").size() ? obj(arr(pb, "bench").get(idx)) : null;
                String key = sideKey + ":" + (idx < 0 ? ACTIVE + ":0" : BENCH + ":" + idx);
                float[] c = center(key);
                if (ma == null && mb != null) {
                    // Pokémon nuevo en juego: Poké Ball y su grito
                    PtcgAudio.play("poke_ball.send_out", SoundEvents.ENTITY_CHICKEN_EGG, 1f, 0.7f);
                    PtcgAudio.cry(ename(s(mb, "f")), 1f, 0.75f);
                    if (c != null) { burst(c[0], c[1], 0xFFFFFFFF, 18, 1.8f); ring(c[0], c[1], sideKey.equals("me") ? BLUE : RED, 50); }
                    continue;
                }
                if (ma == null || mb == null) continue;
                String mn = name(s(mb, "f"));
                boolean same = s(ma, "f").equals(s(mb, "f"));
                if (same) {
                    int hp = i(mb, "hp", 0), dm = i(mb, "dmg", 0);
                    int d = dm - i(ma, "dmg", 0);
                    if (d > 0) {
                        addLog(ui("log_damage", mn, owner, d, Math.max(0, hp - dm), hp), DuelPalette.LOG_DAMAGE, DuelPalette.K_DAMAGE);
                        if (c != null) {
                            damageText(d, false, c[0], c[1]);
                            burst(c[0], c[1], RED, 10 + d / 10, 2.2f);
                            if (d >= 100) burst(c[0], c[1], GOLD, 16, 3f);
                        }
                        // sacudida proporcional al golpe (si la anterior ya terminó, empieza de cero)
                        if (now() - shakeAt > 0.35) shakePow = 0;
                        shakeAt = now();
                        shakePow = Math.max(shakePow, Math.min(8, 2 + d / 25f));
                        hitAt.put(key, now());
                        Anim ha = boardAnim(key);
                        ha.ry.v += (RNG.nextBoolean() ? 1 : -1) * Math.min(900, 300 + d * 4);
                        ha.rx.v += (sideKey.equals("me") ? -1 : 1) * Math.min(700, 200 + d * 3);
                        if (sideKey.equals("me")) { vignetteAt = now(); vignetteColor = RED; }
                        sound(SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, 1f, 0.7f);
                        PtcgAudio.play("poke_ball.hit", SoundEvents.ENTITY_PLAYER_HURT, 1f, 0.5f);
                    } else if (d < 0) {
                        addLog(ui("log_heal", mn, owner, -d, Math.max(0, hp - dm), hp), DuelPalette.LOG_HEAL, DuelPalette.K_HEAL);
                        PtcgAudio.play("item.medicine.spray.use", SoundEvents.ENTITY_PLAYER_LEVELUP, 1.2f, 0.6f);
                        if (c != null) {
                            damageText(-d, true, c[0], c[1]);
                            burst(c[0], c[1], GREEN, 10, 1.4f);
                        }
                    }
                    int en = arr(mb, "en").size() - arr(ma, "en").size();
                    if (en > 0 && c != null) {
                        JsonArray l = arr(mb, "en");
                        int ecol = energyColor(l.get(l.size() - 1).getAsString());
                        burst(c[0], c[1], ecol, 14, 1.6f);
                        ring(c[0], c[1], ecol, 40);
                    } else if (en < 0) addLog(ui("log_energy_lost", mn, owner, -en), DuelPalette.LOG_SYSTEM, DuelPalette.K_SYSTEM);
                    Set<Integer> ca = new HashSet<>(), cb = new HashSet<>();
                    for (JsonElement e : arr(ma, "sc")) ca.add(e.getAsInt());
                    for (JsonElement e : arr(mb, "sc")) cb.add(e.getAsInt());
                    for (int k : cb) if (!ca.contains(k) && k >= 0 && k < 5) {
                        addLog(ui("log_cond_on", mn, ui("cond." + k)), COND_COLORS[k]);
                        sound(k == 3 ? SoundEvents.BLOCK_BREWING_STAND_BREW : k == 4 ? SoundEvents.ITEM_FIRECHARGE_USE : SoundEvents.ENTITY_EVOKER_CAST_SPELL, 1.2f, 0.5f);
                    }
                    for (int k : ca) if (!cb.contains(k) && k >= 0 && k < 5) addLog(ui("log_cond_off", mn, ui("cond." + k)), DuelPalette.LOG_SYSTEM, DuelPalette.K_SYSTEM);
                } else {
                    boolean evo = false;
                    for (JsonElement e : arr(mb, "below")) if (e.getAsString().equals(s(ma, "f"))) evo = true;
                    // la evolución ya la registra el motor (LOG_PLAYER_EVOLVES_POKEMON, en violeta): aquí solo efectos
                    if (evo && c != null) {
                        floatText(ui("evolved"), VIOLET, c[0], c[1] - 10, 1.2f);
                        ring(c[0], c[1], VIOLET, 60);
                        burst(c[0], c[1], VIOLET, 30, 2.6f);
                        sound(SoundEvents.BLOCK_BEACON_POWER_SELECT, 1.6f, 0.6f);
                        PtcgAudio.play("evolution.ui", null, 1f, 0.7f);
                        PtcgAudio.cry(ename(s(mb, "f")), 1f, 0.85f);
                    }
                    if (!evo && idx < 0) addLog(ui("log_new_active", owner, mn), col);
                }
            }
            int prizes = i(pa, "prizes", 6) - i(pb, "prizes", 6);
            if (prizes > 0) {
                addLog(ui("log_prizes", owner, prizes, i(pb, "prizes", 0)), DuelPalette.LOG_PRIZE, DuelPalette.K_PRIZE);
                PtcgAudio.play("gui.levelup", SoundEvents.ENTITY_PLAYER_LEVELUP, 1f, 0.7f);
                float[] pc = center(sideKey + ":prizes");
                int[] pr = rects.get(sideKey + ":prizes");
                if (pc != null) {
                    floatText(ui("prize_taken", prizes), GOLD, pc[0], pc[1], 1.3f);
                    burst(pc[0], pc[1], GOLD, 16, 1.8f);
                    ring(pc[0], pc[1], GOLD, 40);
                }
                if (pr != null) { // las cartas de Premio vuelan a la mano de quien las coge
                    boolean mineSide = sideKey.equals("me");
                    int pw = Math.round(bw * 0.5f), ph = Math.round(pw / Draw.CARD_RATIO);
                    for (int k = 0; k < Math.min(prizes, 6); k++) {
                        PrizeFly pf = new PrizeFly();
                        pf.w = pw;
                        pf.h = ph;
                        pf.x0 = pr[0] + pr[2] / 2f;
                        pf.y0 = pr[1] + pr[3] / 2f;
                        pf.x1 = cx + (k - (prizes - 1) / 2f) * pw * 0.8f;
                        pf.y1 = mineSide ? H - hh * 0.35f : 6;
                        pf.start = now() + k * 0.12;
                        prizeFlies.add(pf);
                    }
                }
            }
            int drawn = i(pb, "hand", 0) - i(pa, "hand", 0), deckDelta = i(pa, "deck", 0) - i(pb, "deck", 0);
            if (deckDelta > 0 && drawn > 0) addLog(ui("log_draw", owner, Math.min(drawn, deckDelta), i(pb, "deck", 0)), DuelPalette.LOG_SYSTEM, DuelPalette.K_SYSTEM);
            int disc = arr(pb, "discard").size() - arr(pa, "discard").size();
            if (disc > 2) addLog(ui("log_discard", owner, disc), DuelPalette.LOG_SYSTEM, DuelPalette.K_SYSTEM);
        }
        String sa = s(a, "stadium"), sb = s(b, "stadium");
        if (!sb.isEmpty() && !sb.equals(sa)) addLog(ui("log_stadium", name(sb)), DuelPalette.LOG_STADIUM);
    }

    @Nullable
    private String findMon(@Nullable JsonObject view, String monName) {
        if (view == null) return null;
        for (String sideKey : new String[]{"opp", "me"}) {
            JsonObject pl = obj(view.get(sideKey));
            if (pl == null) continue;
            JsonObject a = obj(pl.get("active"));
            if (a != null && ename(s(a, "f")).equals(monName)) return sideKey + ":1:0";
            JsonArray bench = arr(pl, "bench");
            for (int k = 0; k < bench.size(); k++) {
                JsonObject m = obj(bench.get(k));
                if (m != null && ename(s(m, "f")).equals(monName)) return sideKey + ":2:" + k;
            }
        }
        return null;
    }

    // ================================================================== layout

    private int W, H, mx0, mx1, cx, bw, bh, aw, ah, hw, hh, oppBenchY, oppActiveY, midY, myActiveY, myBenchY, handBaseY, leftX, rightX;

    private void layout() {
        W = width;
        H = height;
        mx0 = 4;
        mx1 = W - 4;
        cx = W / 2;
        float bhF = (H - 52) / 5.35f;
        float bwMax = (W - 110) / 8.6f;
        bhF = Math.min(bhF, bwMax / Draw.CARD_RATIO);
        bh = Math.max(28, Math.round(bhF));
        bw = Math.round(bh * Draw.CARD_RATIO);
        ah = Math.round(bh * 1.3f);
        aw = Math.round(ah * Draw.CARD_RATIO);
        hh = Math.round(bh * 1.22f);
        hw = Math.round(hh * Draw.CARD_RATIO);
        oppBenchY = 26;
        oppActiveY = oppBenchY + bh + 10;
        midY = oppActiveY + ah + 8;
        myActiveY = midY + 10;
        myBenchY = myActiveY + ah + 12;
        handBaseY = H - Math.round(hh * 0.56f);
        leftX = mx0 + 10;
        rightX = mx1 - 10;
    }

    // ================================================================== dibujo básico

    private void text(DrawContext ctx, String s, float x, float y, int color, float scale, boolean shadow) {
        MatrixStack m = ctx.getMatrices();
        m.push();
        m.translate(x, y, 0);
        m.scale(scale, scale, 1);
        ctx.drawText(textRenderer, s, 0, 0, color, shadow);
        m.pop();
    }

    private void textC(DrawContext ctx, String s, float x, float y, int color, float scale) {
        text(ctx, s, x - textRenderer.getWidth(s) * scale / 2f, y, color, scale, false);
    }

    private void textCS(DrawContext ctx, String s, float x, float y, int color, float scale) {
        text(ctx, s, x - textRenderer.getWidth(s) * scale / 2f, y, color, scale, true);
    }

    private int tw(String s, float scale) { return Math.round(textRenderer.getWidth(s) * scale); }

    /** Panel redondeado con degradado suave (blanco → azul muy claro), brillo arriba y sombra suave. */
    private static void panel(DrawContext ctx, int x, int y, int w, int h, int fill) {
        float al = (fill >>> 24) / 255f;
        ctx.fill(x + 1, y + 3, x + w + 1, y + h + 4, withAlpha(0x1A0A2A50, al));
        ctx.fill(x, y + 2, x + w, y + h + 2, withAlpha(0x140A2A50, al));
        int bottom = DuelPalette.lerp(fill, withAlpha(DuelPalette.PANEL_BOTTOM, al), 0.85f);
        DuelFx.gradientPanel(ctx, x, y, w, h, fill, bottom, withAlpha(LINE, Math.max(0.3f, al)));
    }

    private static void round(DrawContext ctx, int x, int y, int w, int h, int c) {
        if (w < 6 || h < 6) { ctx.fill(x, y, x + w, y + h, c); return; }
        ctx.fill(x + 3, y, x + w - 3, y + h, c);
        ctx.fill(x + 1, y + 1, x + 3, y + h - 1, c);
        ctx.fill(x + w - 3, y + 1, x + w - 1, y + h - 1, c);
        ctx.fill(x, y + 3, x + 1, y + h - 3, c);
        ctx.fill(x + w - 1, y + 3, x + w, y + h - 3, c);
    }

    private static void roundOutline(DrawContext ctx, int x, int y, int w, int h, int c) {
        if (w < 6 || h < 6) { Ui.outline(ctx, x, y, w, h, c); return; }
        ctx.fill(x + 3, y, x + w - 3, y + 1, c);
        ctx.fill(x + 3, y + h - 1, x + w - 3, y + h, c);
        ctx.fill(x, y + 3, x + 1, y + h - 3, c);
        ctx.fill(x + w - 1, y + 3, x + w, y + h - 3, c);
        ctx.fill(x + 1, y + 1, x + 3, y + 2, c);
        ctx.fill(x + 1, y + 2, x + 2, y + 3, c);
        ctx.fill(x + w - 3, y + 1, x + w - 1, y + 2, c);
        ctx.fill(x + w - 2, y + 2, x + w - 1, y + 3, c);
        ctx.fill(x + 1, y + h - 2, x + 3, y + h - 1, c);
        ctx.fill(x + 1, y + h - 3, x + 2, y + h - 2, c);
        ctx.fill(x + w - 3, y + h - 2, x + w - 1, y + h - 1, c);
        ctx.fill(x + w - 2, y + h - 3, x + w - 1, y + h - 2, c);
    }

    private static void pill(DrawContext ctx, int x, int y, int w, int h, int color) {
        int r = Math.min(h / 2, 4);
        if (w < r * 2 + 1) { ctx.fill(x, y, x + w, y + h, color); return; }
        ctx.fill(x + r, y, x + w - r, y + h, color);
        for (int k = 0; k < r; k++) {
            int inset = r - (int) Math.round(Math.sqrt(r * r - (r - k - 0.5) * (r - k - 0.5)));
            ctx.fill(x + k, y + inset, x + k + 1, y + h - inset, color);
            ctx.fill(x + w - k - 1, y + inset, x + w - k, y + h - inset, color);
        }
    }

    private static void corners(DrawContext ctx, int x, int y, int w, int h, int len, int color) {
        ctx.fill(x, y, x + len, y + 1, color);
        ctx.fill(x, y, x + 1, y + len, color);
        ctx.fill(x + w - len, y, x + w, y + 1, color);
        ctx.fill(x + w - 1, y, x + w, y + len, color);
        ctx.fill(x, y + h - 1, x + len, y + h, color);
        ctx.fill(x, y + h - len, x + 1, y + h, color);
        ctx.fill(x + w - len, y + h - 1, x + w, y + h, color);
        ctx.fill(x + w - 1, y + h - len, x + w, y + h, color);
    }

    private static void ring(DrawContext ctx, float cx, float cy, float r, int color, float frac, int thick) {
        int steps = Math.max(24, Math.round(r * 5));
        for (int k = 0; k < steps * frac; k++) {
            double a = -Math.PI / 2 + k * Math.PI * 2 / steps;
            int px = Math.round(cx + (float) Math.cos(a) * r), py = Math.round(cy + (float) Math.sin(a) * r);
            ctx.fill(px, py, px + thick, py + thick, color);
        }
    }

    private static void shadow(DrawContext ctx, int x, int y, int w, int h) {
        ctx.fill(x + 1, y + 2, x + w + 1, y + h + 3, 0x300A2A50);
        ctx.fill(x + 2, y + 4, x + w + 2, y + h + 5, 0x180A2A50);
    }

    private void energyDot(DrawContext ctx, int cx, int cy, int r, int type) {
        net.minecraft.util.Identifier tex = r >= 3 ? ClientPtcg.energyTexture(type) : null;
        int tc = DuelPalette.energy(type);
        if (tex != null) { // aro del color del tipo alrededor del símbolo real
            StylePainter.circle(ctx, cx + 1, cy + 1, r + 1, 0x500A2A50);
            StylePainter.circle(ctx, cx, cy, r + 1, Ui.darken(tc, 20));
            Draw.circleTexture(ctx, tex, cx, cy, r, 0.5f, 0.4928f, 0.226f, 0.1625f);
            return;
        }
        StylePainter.circle(ctx, cx + 1, cy + 1, r + 1, 0x400A2A50);
        StylePainter.circle(ctx, cx, cy, r + 1, Ui.darken(tc, 45));
        StylePainter.circle(ctx, cx, cy, r, tc);
        if (r >= 3) StylePainter.circle(ctx, cx - r / 3, cy - r / 3, Math.max(1, r / 3), withAlpha(0xFFFFFFFF, 0.5f));
        if (r >= 4) {
            String l = ClientPtcg.typeLetter(type);
            float sc = r >= 6 ? 0.75f : 0.55f;
            text(ctx, l, cx - tw(l, sc) / 2f + 0.5f, cy - 3.6f * sc, DuelPalette.energyInk(type), sc, false);
        }
    }

    private static int energyType(String f) {
        JsonObject d = ClientPtcg.def(f);
        JsonArray prov = arr(d, "prov");
        return prov.size() > 0 ? prov.get(0).getAsInt() : 9;
    }

    private static int energyColor(String f) { return DuelPalette.energy(energyType(f)); }

    /** Píldora de color con el nombre de un tipo de Energía (tipos, debilidad, resistencia). Devuelve su ancho. */
    private int typeBadge(DrawContext ctx, int x, int y, int type, String extra, float scale) {
        String l = ui("type." + type) + (extra.isEmpty() ? "" : " " + extra);
        int w = tw(l, scale) + 8, h = Math.round(8 * scale) + 3, c = DuelPalette.energy(type);
        Ui.roundedGradient(ctx, x, y, w, h, Math.min(4, h / 2), Ui.brighten(c, 25), Ui.darken(c, 15));
        text(ctx, l, x + 4, y + 2, DuelPalette.energyInk(type), scale, false);
        return w;
    }

    /** Imagen de la carta (la real de cobbletcg si existe, también las Energías; si no, una carta limpia con sus datos). */
    private void card(DrawContext ctx, String fullName, float x, float y, float w, float h, float alpha) {
        ItemStack st = ClientPtcg.stack(fullName);
        if (!st.isEmpty()) {
            Draw.cardFace(ctx, CardInfo.texture(CardInfo.id(st)), st, x, y, w, h, alpha);
            return;
        }
        int ix = Math.round(x), iy = Math.round(y), iw = Math.round(w), ih = Math.round(h);
        JsonObject d = ClientPtcg.def(fullName);
        int stype = i(d, "st", 0);
        int col = 0xFF8A94A8;
        if (stype == 3) col = energyColor(fullName);
        else if (stype == 1 && arr(d, "ct").size() > 0) col = DuelPalette.energy(arr(d, "ct").get(0).getAsInt());
        else if (stype == 2) col = i(d, "tt", 0) == 1 ? 0xFFF08A4B : i(d, "tt", 0) == 2 ? 0xFF3FBF7F : i(d, "tt", 0) == 3 ? 0xFF7A8AE0 : 0xFF4FA3E8;
        ctx.fill(ix, iy, ix + iw, iy + ih, withAlpha(0xFFF4E7A8, alpha));
        ctx.fillGradient(ix + 1, iy + 1, ix + iw - 1, iy + ih - 1, withAlpha(Ui.brighten(col, 70), alpha), withAlpha(col, alpha));
        ctx.fill(ix + 3, iy + Math.round(ih * 0.17f), ix + iw - 3, iy + Math.round(ih * 0.55f), withAlpha(0x30FFFFFF, alpha));
        String nm = d == null ? fullName : s(d, "n");
        float sc = Math.max(0.3f, Math.min(0.7f, (w - 5f) / Math.max(1, textRenderer.getWidth(nm))));
        text(ctx, nm, x + 2.5f, y + 2.5f, withAlpha(0xFF1A2236, alpha), sc, false);
        if (stype == 3) energyDot(ctx, ix + iw / 2, iy + ih / 2, Math.max(4, iw / 4), energyType(fullName));
        else if (stype == 2) {
            String tt = ui("trainer." + i(d, "tt", 0));
            text(ctx, tt, x + w / 2f - tw(tt, 0.5f) / 2f, y + h * 0.62f, withAlpha(0xFF1A2236, alpha), 0.5f, false);
        }
    }

    private void back(DrawContext ctx, int x, int y, int w, int h) {
        ctx.fill(x, y, x + w, y + h, 0xFFFFFFFF);
        ctx.fillGradient(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF4C8DF0, 0xFF2856B8);
        int r = Math.max(2, Math.min(w, h) / 4), ccx = x + w / 2, ccy = y + h / 2;
        StylePainter.circle(ctx, ccx, ccy, r + 1, 0xFF1A2236);
        StylePainter.circle(ctx, ccx, ccy, r, 0xFFFFFFFF);
        for (int dy = -r; dy < 0; dy++) {
            int half = (int) Math.round(Math.sqrt(r * r - dy * dy));
            ctx.fill(ccx - half, ccy + dy, ccx + half + 1, ccy + dy + 1, 0xFFFF4D6D);
        }
        ctx.fill(ccx - r, ccy, ccx + r + 1, ccy + 1, 0xFF1A2236);
        if (r >= 3) StylePainter.circle(ctx, ccx, ccy, Math.max(1, r / 3), 0xFFFFFFFF);
    }

    /** Carta con marco blanco limpio y sombra (en 3D, plana). */
    private void framedCard(DrawContext ctx, String f, int x, int y, int w, int h, float alpha) {
        MatrixStack m = ctx.getMatrices();
        m.push();
        m.translate(x + w / 2f, y + h / 2f, 0);
        card3d(ctx, f, w, h, 0, 0, alpha, 0, 0, 0f, now());
        m.pop();
    }

    private void framedCardFlat(DrawContext ctx, String f, int x, int y, int w, int h, float alpha) {
        shadow(ctx, x, y, w, h);
        round(ctx, x - 1, y - 1, w + 2, h + 2, withAlpha(0xFFFFFFFF, alpha));
        card(ctx, f, x, y, w, h, alpha);
    }

    @Nullable
    private Identifier texOf(String f) {
        return texCache.computeIfAbsent(f, k -> {
            ItemStack st = ClientPtcg.stack(k);
            if (st.isEmpty()) return Optional.empty();
            Identifier tx = CardInfo.texture(CardInfo.id(st));
            return Draw.textureExists(tx) ? Optional.of(tx) : Optional.empty();
        }).orElse(null);
    }

    private static final java.util.regex.Pattern RARE = java.util.regex.Pattern.compile("(^Radiant |\\b(ex|EX|GX|V|VMAX|VSTAR|BREAK|LV\\.X|Prism Star|ACE SPEC)\\b)");

    /** Lámina holo: 2 si es graduada, 1 si es una carta rara (ex, V, GX, Radiante...), 0 normal. */
    private int foilOf(String f, boolean mine) {
        if (gradeOf(mine, f) > 0) return 2;
        return RARE.matcher(ename(f)).find() ? 1 : 0;
    }

    /**
     * Carta 3D centrada en el origen actual: sombra, canto, marco, perspectiva, reflejo y holo. Sin textura (faltan
     * los resource packs de cobbletcg) cae a la carta plana, achatada según el giro para no perder el movimiento.
     */
    private void card3d(DrawContext ctx, String f, float w, float h, float rx, float ry, float alpha, float lift, int glow, float dim, double t) {
        card3d(ctx, f, w, h, rx, ry, alpha, lift, glow, dim, t, -1);
    }

    private void card3d(DrawContext ctx, String f, float w, float h, float rx, float ry, float alpha, float lift, int glow, float dim, double t, int foil) {
        Identifier tex = texOf(f);
        if (foil < 0) foil = foilOf(f, true);
        if (tex != null) {
            Card3D.shadow(ctx, w, h, rx, ry, lift, alpha);
            Card3D.draw(ctx, tex, w, h, rx, ry, alpha, foil, glow, dim, t);
            return;
        }
        MatrixStack m = ctx.getMatrices();
        m.push();
        m.scale(Math.max(0.06f, MathHelper.cos(ry * MathHelper.RADIANS_PER_DEGREE)), Math.max(0.06f, MathHelper.cos(rx * MathHelper.RADIANS_PER_DEGREE)), 1);
        int iw = Math.round(w), ih = Math.round(h);
        if (glow != 0) round(ctx, -iw / 2 - 3, -ih / 2 - 3, iw + 6, ih + 6, withAlpha(glow, 0.5f * alpha));
        framedCardFlat(ctx, f, -iw / 2, -ih / 2, iw, ih, alpha);
        if (dim > 0) round(ctx, -iw / 2, -ih / 2, iw, ih, withAlpha(0xFF0A1A30, 0.45f * dim));
        m.pop();
    }

    private Anim boardAnim(String key) { return boardAnim.computeIfAbsent(key, k -> new Anim()); }

    // ================================================================== render

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        if (v == null) return;
        layout();
        areas.clear();
        drops.clear();
        tooltip = null;
        double t = now();
        dt = (float) MathHelper.clamp(t - lastFrame, 0, 0.05);
        lastFrame = t;
        mouseXNow = mouseX;
        mouseYNow = mouseY;
        MatrixStack m = ctx.getMatrices();
        boolean promptOpen = prompt() != null && !over();
        boolean intro = intro();
        boolean modal = (promptOpen && !peek) || zoomKey != null || discardSide != null || over() || intro;
        int bmx = modal ? -9999 : mouseX, bmy = modal ? -9999 : mouseY;

        background(ctx, t);
        float sx = 0, sy = 0;
        double sa = t - shakeAt;
        if (sa < 0.42) { // sacudida que se apaga con suavidad (cuadrática) y mezcla dos frecuencias para no ser mecánica
            float d = (float) (1 - sa / 0.42), k = d * d * shakePow;
            sx = (float) (Math.sin(t * 87) * 0.7 + Math.sin(t * 53 + 1.3) * 0.3) * k;
            sy = (float) (Math.cos(t * 71) * 0.7 + Math.cos(t * 41 + 0.7) * 0.3) * k * 0.6f;
        }
        m.push();
        m.translate(sx, sy, 0);
        board(ctx, bmx, bmy, t);
        m.pop();
        boardReady = true;
        hud(ctx, bmx, bmy, t);
        logFeed(ctx, bmx, bmy, t);
        if (canAct() && zoomKey == null) computeDrops();
        dropHighlights(ctx, mouseX, mouseY, t);
        previewTargets(ctx, t);
        if (canAct() && suggestion != null && !dragging && zoomKey == null) suggestionOverlay(ctx, t);
        if (intro) { syncHand(handCards()); handReady = true; }
        else hand(ctx, bmx, bmy, t);
        effects(ctx, t);
        // registro completo: entra y sale deslizándose (se sigue dibujando mientras se cierra)
        drawerT = DuelFx.approach(drawerT, menuOpen ? 1 : 0, 14f, dt);
        if (menuOpen && drawerT > 0.995f) drawerT = 1;
        if (!menuOpen && drawerT < 0.01f) drawerT = 0;
        if (menuOpen || drawerT > 0) logDrawer(ctx, mouseX, mouseY, t);

        m.push();
        m.translate(0, 0, 300);
        if (zoomKey != null && !over()) zoom(ctx, mouseX, mouseY, t);
        else if (discardSide != null && !over()) discardView(ctx, mouseX, mouseY, t);
        else if (promptOpen && !peek && !intro) promptPanel(ctx, prompt(), mouseX, mouseY, t);
        else if (promptOpen && !intro) peekBar(ctx, mouseX, mouseY);
        if (zoomKey == null) showcaseDraw(ctx, t);
        vignetteDraw(ctx, t);
        turnDraw(ctx, t);
        bannerDraw(ctx, t);
        coinDraw(ctx, t);
        bigCoinDraw(ctx, t);
        introDraw(ctx, t);
        if (dragging) dragged(ctx, t);
        if (!toast.isEmpty() && t - toastAt < 3.4) toastDraw(ctx, t);
        if (over()) result(ctx, mouseX, mouseY, t);
        m.pop();
        if (tooltip != null) {
            m.push();
            m.translate(0, 0, 500);
            ctx.drawTooltip(textRenderer, tooltip, mouseX, mouseY);
            m.pop();
        }
    }

    private void background(DrawContext ctx, double t) {
        ctx.fillGradient(0, 0, W, H, 0xFFE9F2FB, 0xFFD3E4F4);
        String bid = boardId();
        if (DuelBackgrounds.draw(ctx, bid, 0, 0, W, H, Math.max(160, Math.min(W, H) / 2), t, 1f)) {
            // velo para que el tablero se lea: bordes más oscuros y el tapete un poco aclarado
            ctx.fillGradient(0, 0, W, H / 5, 0x55000000, 0x00000000);
            ctx.fillGradient(0, H - H / 5, W, H, 0x00000000, 0x55000000);
            round(ctx, mx0, 2, mx1 - mx0, H - 4, 0x40FFFFFF);
        }
        ctx.fillGradient(mx0, 2, mx1, midY, 0x00FFFFFF, 0x26FF9DB3);
        ctx.fillGradient(mx0, midY, mx1, H - 2, 0x2670B4FF, 0x00FFFFFF);
        float r = Math.min(W, H) * 0.32f;
        StylePainter.circle(ctx, cx, midY, Math.round(r), 0x30FFFFFF);
        StylePainter.circle(ctx, cx, midY, Math.round(r * 0.72f), 0x26FFFFFF);
        ring(ctx, cx, midY, r, 0x40FFFFFF, 1, 2);
        ring(ctx, cx, midY, r * 0.72f, 0x30FFFFFF, 1, 1);
        float pulse = 0.5f + 0.25f * (float) Math.sin(t * 2);
        int lc = myTurn() ? BLUE : RED;
        ctx.fill(mx0 + 14, midY, mx1 - 14, midY + 1, withAlpha(lc, 0.35f * pulse + 0.15f));
        ctx.fill(mx0 + 14, midY - 1, mx1 - 14, midY + 2, withAlpha(lc, 0.08f));
        roundOutline(ctx, mx0, 2, mx1 - mx0, H - 4, 0x60FFFFFF);
        // tapete: huecos marcados para el Activo y la Banca de cada lado, con el color de quien juega
        int bt = 5 * (bw + 8) - 8, bx = cx - bt / 2;
        for (int side = 0; side < 2; side++) {
            boolean mine = side == 1;
            int ay = mine ? myActiveY : oppActiveY, by = mine ? myBenchY : oppBenchY;
            int tint = mine ? BLUE : RED;
            boolean turnSide = mine == myTurn() && !over();
            float pz = turnSide ? 0.5f + 0.5f * (float) Math.sin(t * 2.4) : 0;
            round(ctx, cx - aw / 2 - 7, ay - 7, aw + 14, ah + 14, withAlpha(0xFFFFFFFF, 0.22f));
            roundOutline(ctx, cx - aw / 2 - 7, ay - 7, aw + 14, ah + 14, withAlpha(turnSide ? tint : 0xFFFFFFFF, turnSide ? 0.35f + 0.3f * pz : 0.45f));
            corners(ctx, cx - aw / 2 - 10, ay - 10, aw + 20, ah + 20, 6, withAlpha(tint, 0.35f + 0.25f * pz));
            round(ctx, bx - 8, by - 5, bt + 16, bh + 10, withAlpha(0xFFFFFFFF, 0.14f));
            roundOutline(ctx, bx - 8, by - 5, bt + 16, bh + 10, withAlpha(0xFFFFFFFF, 0.35f));
            ctx.fillGradient(bx - 4, mine ? by + bh + 3 : by - 4, bx + bt + 4, mine ? by + bh + 5 : by - 2, withAlpha(tint, 0.0f), withAlpha(tint, 0.25f));
        }
    }

    // ------------------------------------------------------------------ tablero

    private void board(DrawContext ctx, int mx, int my, double t) {
        JsonObject me = side("me"), opp = side("opp");
        benchRow(ctx, opp, "opp", oppBenchY, mx, my, t);
        monSlot(ctx, opp == null ? null : obj(opp.get("active")), "opp:1:0", cx - aw / 2, oppActiveY, aw, ah, mx, my, t, false);
        monSlot(ctx, me == null ? null : obj(me.get("active")), "me:1:0", cx - aw / 2, myActiveY, aw, ah, mx, my, t, true);
        benchRow(ctx, me, "me", myBenchY, mx, my, t);
        int pw = Math.round(bw * 0.5f);
        prizes(ctx, opp, "opp", leftX, oppActiveY + 4, pw, mx, my);
        prizes(ctx, me, "me", leftX, myActiveY + 4, pw, mx, my);
        piles(ctx, opp, "opp", rightX - bw, oppActiveY + 4, mx, my, true);
        piles(ctx, me, "me", rightX - bw, myActiveY + ah - bh - 4, mx, my, false);
        String stadium = s(v, "stadium");
        int sw = Math.round(bw * 0.85f), shh = Math.round(sw / Draw.CARD_RATIO), stx = cx - aw / 2 - 18 - sw, sty = midY - shh / 2;
        rects.put("stadium", new int[]{stx, sty, sw, shh});
        if (!stadium.isEmpty()) {
            framedCard(ctx, stadium, stx, sty, sw, shh, 1f);
            chip(ctx, ui("stadium"), stx + sw / 2, sty - 9, GREEN);
            if (Ui.in(mx, my, stx, sty, sw, shh)) hoverOutline(ctx, stx, sty, sw, shh);
            areas.add(new Area(stx, sty, sw, shh, () -> openZoom("stadium", stadium)));
        }
        for (String sideKey : new String[]{"opp", "me"}) {
            JsonArray sup = arr(side(sideKey), "supporter");
            if (sup.size() == 0) continue;
            String f = sup.get(0).getAsString();
            int spx = cx + aw / 2 + 18, spy = sideKey.equals("opp") ? midY - shh - 4 : midY + 4;
            framedCard(ctx, f, spx, spy, sw, shh, 1f);
            chip(ctx, ui("supporter"), spx + sw / 2, sideKey.equals("opp") ? spy - 9 : spy + shh + 2, 0xFFF08A4B);
            if (Ui.in(mx, my, spx, spy, sw, shh)) hoverOutline(ctx, spx, spy, sw, shh);
            areas.add(new Area(spx, spy, sw, shh, () -> openZoom("card", f)));
        }
        rects.put("zone:board", new int[]{cx - aw * 2, oppActiveY + ah / 3, aw * 4, myActiveY + ah * 2 / 3 - oppActiveY - ah / 3});
        int oh = i(opp, "hand", 0);
        if (oh > 0) {
            int cw = Math.round(bw * 0.7f), chh = Math.round(cw / Draw.CARD_RATIO), step = Math.min(cw / 2, 120 / Math.max(1, oh));
            int total = (oh - 1) * step + cw, x0 = cx - total / 2;
            for (int k = 0; k < oh; k++) back(ctx, x0 + k * step, -chh + 14, cw, chh);
            String hs = String.valueOf(oh);
            pill(ctx, x0 + total + 3, 3, tw(hs, 0.7f) + 8, 9, 0xE0FFFFFF);
            text(ctx, hs, x0 + total + 7, 4.5f, INK, 0.7f, false);
        }
    }

    private void chip(DrawContext ctx, String s, int cxp, int y, int color) {
        int w = tw(s, 0.55f) + 8;
        pill(ctx, cxp - w / 2, y, w, 8, color);
        text(ctx, s, cxp - w / 2f + 4, y + 1.6f, 0xFFFFFFFF, 0.55f, false);
    }

    private static void hoverOutline(DrawContext ctx, int x, int y, int w, int h) {
        roundOutline(ctx, x - 2, y - 2, w + 4, h + 4, BLUE);
    }

    private void benchRow(DrawContext ctx, @Nullable JsonObject pl, String sideKey, int y, int mx, int my, double t) {
        JsonArray bench = arr(pl, "bench");
        int slots = Math.max(5, bench.size());
        int w = slots > 5 ? Math.round(bw * 5f / slots) : bw, h = Math.round(w / Draw.CARD_RATIO), gap = 8;
        int total = slots * (w + gap) - gap, x0 = cx - total / 2;
        for (int k = 0; k < slots; k++) {
            JsonObject b = k < bench.size() ? obj(bench.get(k)) : null;
            monSlot(ctx, b, sideKey + ":2:" + k, x0 + k * (w + gap), y + (bh - h), w, h, mx, my, t, sideKey.equals("me"));
        }
    }

    /** Un Pokémon en juego: PS grandes con barra encima, contadores de daño, energías, herramienta, habilidad y condiciones. */
    private void monSlot(DrawContext ctx, @Nullable JsonObject p, String key, int x, int y, int w, int h, int mx, int my, double t, boolean mine) {
        rects.put(key, new int[]{x, y, w, h});
        double la = t - lungeAt.getOrDefault(key, -10.0);
        if (la < 0.45) y += Math.round((key.startsWith("opp") ? 1 : -1) * (float) Math.sin(la / 0.45 * Math.PI) * ah * 0.4f);
        boolean hover = Ui.in(mx, my, x, y, w, h) && !dragging;
        Anim an = boardAnim(key);
        if (p == null) {
            an.f = "";
            hpBars.remove(key);
            round(ctx, x, y, w, h, 0x26FFFFFF);
            roundOutline(ctx, x, y, w, h, 0x70FFFFFF);
            return;
        }
        String f = s(p, "f");
        if (!an.f.equals(f)) {
            if (boardReady) { // entra en juego o evoluciona: cae sobre la mesa con rebote
                an.s.x = 1.5f;
                an.s.v = 0;
                an.rx.x = -38;
                an.ry.x = (RNG.nextFloat() - 0.5f) * 30;
                an.lift.x = 1.4f;
            }
            an.f = f;
            hpBars.remove(key); // otro Pokémon (o evolución): la barra empieza en su valor real
        }
        if (dragActive && key.equals("me:1:0")) {
            round(ctx, x, y, w, h, 0x30FFFFFF);
            corners(ctx, x, y, w, h, 6, BLUE);
            return;
        }
        float ccx = x + w / 2f, ccy = y + h / 2f;
        if (hover) {
            an.tilt(MathHelper.clamp(-(my - ccy) / (h * 0.55f), -1, 1) * 11, MathHelper.clamp((mx - ccx) / (w * 0.55f), -1, 1) * 14, dt);
            an.s.to(1.07f, dt, 260, 22);
            an.lift.to(0.7f, dt, 200, 20);
        } else {
            boolean act = key.endsWith(":1:0");
            float br = act ? (float) Math.sin(t * 0.9 + (key.startsWith("me") ? 0 : 2)) : 0;
            an.tilt(act ? br * 2.2f : 0, act ? (float) Math.cos(t * 0.7) * 3f : 0, dt);
            an.s.to(1f, dt, 230, 18);
            an.lift.to(0f, dt, 160, 18);
        }
        MatrixStack m = ctx.getMatrices();
        m.push();
        m.translate(ccx, ccy, 0);
        m.scale(an.s.x, an.s.x, 1);
        m.translate(-ccx, -ccy, 0);
        monSlotBody(ctx, p, f, an, key, x, y, w, h, t, mine, hover);
        m.pop();
    }

    private void monSlotBody(DrawContext ctx, JsonObject p, String f, Anim an, String key, int x, int y, int w, int h, double t, boolean mine, boolean hover) {
        boolean canAttack = mine && key.equals("me:1:0") && canAct() && anyAttackOk();
        if (canAttack) {
            float p2 = 0.5f + 0.5f * (float) Math.sin(t * 3);
            Draw.glow(ctx, x + w / 2f, y + h / 2f, w, h, 0xFF7FD0FF, 0.3f + 0.25f * p2);
        }
        // turno del rival: su Activo brilla en rojo suave
        if (!mine && key.equals("opp:1:0") && !myTurn() && !over()) {
            float p3 = 0.5f + 0.5f * (float) Math.sin(t * 3);
            Draw.glow(ctx, x + w / 2f, y + h / 2f, w, h, 0xFFFF8FA3, 0.15f + 0.15f * p3);
        }
        float rot = 0;
        JsonArray sc = arr(p, "sc");
        for (JsonElement c : sc) {
            int k = c.getAsInt();
            if (k == 2) rot = -90;
            else if (k == 0) rot = 90;
            else if (k == 1) rot = 180;
        }
        MatrixStack m = ctx.getMatrices();
        m.push();
        m.translate(x + w / 2f, y + h / 2f, 0);
        if (rot != 0 && key.endsWith(":1:0")) {
            m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rot));
            float k = Math.abs(rot) == 90 ? w / (float) h : 1;
            m.scale(k, k, 1);
        }
        JsonArray toolsBehind = arr(p, "tools");
        for (int k = Math.min(2, toolsBehind.size()) - 1; k >= 0; k--) { // la herramienta asoma por detrás, como en la mesa real
            String tf = toolsBehind.get(k).getAsString();
            float tw0 = w * 0.62f, th0 = tw0 / Draw.CARD_RATIO;
            m.push();
            m.translate(w * 0.36f + k * 5, h * 0.2f + k * 3, 0);
            m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(16 + k * 8));
            card3d(ctx, tf, tw0, th0, an.rx.x * 0.6f, an.ry.x * 0.6f, 1f, 0.1f, withAlpha(0xFF8EA2FF, 0.7f), 0f, t);
            m.pop();
        }
        card3d(ctx, f, w, h, an.rx.x, an.ry.x, 1f, Math.max(0, an.lift.x), 0, 0f, t, foilOf(f, mine));
        m.pop();
        slabOverlay(ctx, x, y, w, h, gradeOf(mine, f));
        int hp = i(p, "hp", 0), dmg = i(p, "dmg", 0), left = Math.max(0, hp - dmg);
        int ph = hpPill(ctx, x, y, w, h, left, hp, key, t);
        JsonArray en = arr(p, "en");
        int er = Math.max(3, Math.min(6, w / 9));
        int eBase = y + h - ph / 2 - 3;
        int perCol = Math.max(1, (eBase - y - 6) / (er * 2 + 2));
        for (int k = 0; k < Math.min(12, en.size()); k++)
            energyDot(ctx, x - 1 + (k / perCol) * (er * 2 + 2), eBase - er - (k % perCol) * (er * 2 + 2), er, energyType(en.get(k).getAsString()));
        JsonArray tools = arr(p, "tools");
        if (tools.size() > 0) {
            String tf = tools.get(0).getAsString();
            String tl = "⚙ " + Ui.ellipsis(name(tf), Math.round(w * 1.6f));
            int tlw = Math.min(Math.round(w * 1.15f), tw(tl, 0.55f) + 8), tlx = x + w / 2 - tlw / 2, tly = y + h - ph - 9;
            pill(ctx, tlx, tly, tlw, 9, 0xF0FFFFFF);
            roundOutline(ctx, tlx, tly, tlw, 9, 0xFF6F84F0);
            text(ctx, Ui.ellipsis(tl, Math.round((tlw - 6) / 0.55f)), tlx + 4, tly + 2f, 0xFF3446B8, 0.55f, false);
            if (Ui.in(mouseXNow, mouseYNow, tlx, tly, tlw, 9)) {
                List<Text> tt = new ArrayList<>(describeShort(tf));
                tt.add(0, Text.literal(ui("tool")).formatted(Formatting.BLUE));
                tooltip = tt;
            }
            areas.add(new Area(tlx, tly, tlw, 9, () -> openZoom("card", tf)));
        }
        JsonObject d = ClientPtcg.def(f);
        if (arr(d, "powers").size() > 0) {
            StylePainter.circle(ctx, x + 6, y + 6, 6, 0xFFFFFFFF);
            StylePainter.circle(ctx, x + 6, y + 6, 5, VIOLET);
            text(ctx, "✦", x + 3.2f, y + 3, 0xFFFFFFFF, 0.7f, false);
        }
        int cy = y + 14;
        for (JsonElement c : sc) {
            int k = c.getAsInt();
            if (k < 0 || k >= COND_COLORS.length) continue;
            chip(ctx, ui("cond." + k), x + w / 2, cy, COND_COLORS[k]);
            cy += 9;
        }
        if (canAttack) {
            String rs = "⚔ " + ui("ready_attack");
            float p4 = 0.85f + 0.15f * (float) Math.sin(t * 4);
            int rw = tw(rs, 0.6f) + 10;
            pill(ctx, x + w / 2 - rw / 2, y - 11, rw, 9, withAlpha(BLUE, p4));
            text(ctx, rs, x + w / 2f - rw / 2f + 5, y - 9.4f, 0xFFFFFFFF, 0.6f, false);
        }
        if (hover) hoverOutline(ctx, x, y, w, h);
    }

    /** PS restantes en una píldora grande del color de la salud, sobre el borde inferior de la carta. Devuelve su alto. */
    private int hpPill(DrawContext ctx, int x, int y, int w, int h, int left, int hp, String key, double t) {
        float frac = hp == 0 ? 0 : left / (float) hp;
        // barra animada: baja con suavidad y deja un "fantasma" claro detrás (o una franja verde delante al curarse)
        DuelFx.HpBar bar = hpBars.computeIfAbsent(key, k -> new DuelFx.HpBar());
        bar.update(frac, t, dt);
        float shown = bar.shown, ghost = bar.ghost;
        int col = DuelPalette.hp(shown);
        double hit = t - hitAt.getOrDefault(key, -10.0);
        float pop = hit < 0.5 ? 1 + 0.25f * (float) Math.sin(hit / 0.5 * Math.PI) : 1;
        float hs = MathHelper.clamp(w / 32f, 0.8f, 1.45f) * pop;
        // el número cuenta hacia abajo (o arriba) junto con la barra
        int shownHp = Math.abs(shown - frac) < 0.002f ? left : Math.round(shown * hp);
        String hpS = String.valueOf(shownHp);
        boolean showLabel = w >= 34;
        int tW = tw(hpS, hs) + (showLabel ? tw(ui("hp"), 0.55f * pop) + 3 : 0);
        int ph = Math.round(8 * hs) + 4, pw = Math.max(tW + 12, Math.round(w * 0.62f));
        int px = x + w / 2 - pw / 2, py = y + h - ph / 2 - 2;
        if (hit < 0.6) {
            float k = (float) (1 - hit / 0.6);
            round(ctx, x, y, w, h, withAlpha(RED, 0.45f * k));
        }
        if (frac <= 0.25f && left > 0) {
            float pl = 0.5f + 0.5f * (float) Math.sin(t * 6);
            roundOutline(ctx, x - 2, y - 2, w + 4, h + 4, withAlpha(RED, 0.5f + 0.5f * pl));
        }
        ctx.fill(px + 1, py + 2, px + pw + 1, py + ph + 2, 0x400A2A50);
        pill(ctx, px - 1, py - 1, pw + 2, ph + 2, 0xFFFFFFFF);
        pill(ctx, px, py, pw, ph, Ui.darken(col, 80));
        if (ghost > shown + 0.002f) { // fantasma: amarillo claro tras un golpe, verde claro al curarse
            int gw = Math.max(ph, Math.round(pw * ghost));
            pill(ctx, px, py, gw, ph, bar.healing() ? DuelPalette.HP_HEAL : DuelPalette.HP_GHOST);
        }
        int fw = Math.max(ph, Math.round(pw * shown));
        if (left > 0 || shown > 0.003f) {
            pill(ctx, px, py, fw, ph, col);
            ctx.fill(px + 2, py + ph - 2, px + fw - 2, py + ph - 1, withAlpha(Ui.darken(col, 40), 0.6f)); // sombra inferior
        }
        ctx.fill(px + 3, py + 1, px + fw - 3, py + 2, withAlpha(0xFFFFFFFF, 0.5f));
        float tx = x + w / 2f - tW / 2f;
        text(ctx, hpS, tx, py + (ph - 8 * hs) / 2f + 0.5f, 0xFFFFFFFF, hs, true);
        if (showLabel) text(ctx, ui("hp"), tx + tw(hpS, hs) + 3, py + ph / 2f - 1.5f, 0xE0FFFFFF, 0.55f * pop, true);
        return ph;
    }

    /** Funda de CobbleGrade alrededor de una carta: canto de metacrilato y etiqueta con la nota encima. */
    private void slabOverlay(DrawContext ctx, int x, int y, int w, int h, int grade) {
        if (grade <= 0) return;
        int col = dev.tcgshop.grading.Grading.color(grade) | 0xFF000000;
        int m = Math.max(2, w / 18);
        roundOutline(ctx, x - m - 1, y - m - 1, w + 2 * m + 2, h + 2 * m + 2, 0xFF9FB2C8);
        roundOutline(ctx, x - m, y - m, w + 2 * m, h + 2 * m, 0xFFE4ECF5);
        roundOutline(ctx, x - m + 1, y - m + 1, w + 2 * m - 2, h + 2 * m - 2, 0xFFDCE5F0);
        int lh = Math.max(7, Math.round(w * 0.2f)), ly = y - m - lh - 1;
        round(ctx, x - m, ly, w + 2 * m, lh + 1, 0xFFFAFAF7);
        ctx.fill(x - m + 1, ly, x + w + m - 1, ly + Math.max(1, lh / 6), col);
        String g = dev.tcgshop.grading.Grading.format(grade);
        float gs = Math.min(1f, (lh - 2) / 8f);
        int bw = tw(g, gs) + 4;
        round(ctx, x + w + m - bw - 1, ly + 1, bw, lh - 1, col);
        text(ctx, g, x + w + m - bw + 1, ly + 1 + (lh - 1 - 8 * gs) / 2f, 0xFFFFFFFF, gs, false);
        String brand = w >= 40 ? "COBBLEGRADE" : "CG";
        float bs = Math.min(0.6f, (w - bw - 2) / (float) Math.max(1, textRenderer.getWidth(brand)));
        text(ctx, brand, x - m + 2, ly + (lh - 8 * bs) / 2f + 0.5f, 0xFF1E2A44, bs, false);
    }

    private int gradeOf(boolean mine, String f) { return i(obj(v.get(mine ? "gMe" : "gOpp")), f, 0); }

    private boolean anyAttackOk() {
        for (JsonElement e : arr(side("me"), "atkOk")) if (e.getAsBoolean()) return true;
        return false;
    }

    private void prizes(DrawContext ctx, @Nullable JsonObject pl, String sideKey, int x, int y, int w, int mx, int my) {
        if (pl == null) return;
        int h = Math.round(w / Draw.CARD_RATIO), left = i(pl, "prizes", 0), stepY = Math.round(h * 0.38f);
        for (int k = 0; k < 6; k++) {
            int px = x + (k % 2) * (w + 3), py = y + (k / 2) * stepY;
            if (k < left) {
                shadow(ctx, px, py, w, h);
                back(ctx, px, py, w, h);
            } else roundOutline(ctx, px, py, w, h, 0x60FFFFFF);
        }
        int totalH = 2 * stepY + h;
        rects.put(sideKey + ":prizes", new int[]{x, y, 2 * w + 3, totalH});
        String s = left + "/6";
        int pw = tw(s, 0.7f) + 10;
        pill(ctx, x + w + 1 - pw / 2, y + totalH + 3, pw, 10, 0xF0FFFFFF);
        text(ctx, s, x + w + 6 - pw / 2f, y + totalH + 4.5f, 0xFFB07A00, 0.7f, false);
        if (Ui.in(mx, my, x, y, 2 * w + 3, totalH)) tooltip = List.of(Text.literal(ui("prizes_hint", left)));
    }

    private void piles(DrawContext ctx, @Nullable JsonObject pl, String sideKey, int x, int y, int mx, int my, boolean opp) {
        if (pl == null) return;
        rects.put(sideKey + ":deck", new int[]{x, y, bw, bh});
        int deck = i(pl, "deck", 0);
        for (int k = Math.min(3, (deck + 9) / 10); k > 0; k--) back(ctx, x + k, y - k, bw, bh);
        if (deck > 0) { shadow(ctx, x, y, bw, bh); back(ctx, x, y, bw, bh); }
        else roundOutline(ctx, x, y, bw, bh, 0x60FFFFFF);
        String ds = String.valueOf(deck);
        pill(ctx, x + bw / 2 - 10, y + bh - 11, 20, 10, 0xF0FFFFFF);
        text(ctx, ds, x + bw / 2f - tw(ds, 0.7f) / 2f, y + bh - 9.5f, INK, 0.7f, false);
        if (Ui.in(mx, my, x, y, bw, bh))
            tooltip = List.of(Text.literal(ui("deck_count", deck, i(pl, "hand", 0))), Text.literal(ui("lost_count", i(pl, "lost", 0))).formatted(Formatting.GRAY));
        JsonArray disc = arr(pl, "discard");
        int dx = x - bw - 8;
        rects.put(sideKey + ":discard", new int[]{dx, y, bw, bh});
        if (disc.size() > 0) {
            double t = now();
            boolean hov = Ui.in(mx, my, dx, y, bw, bh);
            MatrixStack m = ctx.getMatrices();
            int nShow = Math.min(4, disc.size());
            for (int k = nShow - 1; k >= 0; k--) { // montón desordenado: las de debajo asoman giradas
                int idx = disc.size() - 1 - k;
                String fk = disc.get(idx).getAsString();
                float rot = ((idx * 37) % 17 - 8) * 1.1f, ox = ((idx * 53) % 7 - 3) * 0.7f, oy = -k * 1.3f;
                m.push();
                m.translate(dx + bw / 2f + ox, y + bh / 2f + oy - (k == 0 && hov ? 2 : 0), 0);
                m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(k == 0 ? rot * 0.35f : rot));
                card3d(ctx, fk, bw, bh, 0, 0, 1f, k == 0 ? (hov ? 0.9f : 0.25f) : 0f, k == 0 && hov ? withAlpha(BLUE, 0.7f) : 0, k == 0 ? 0f : 0.3f, t);
                m.pop();
            }
            String n = String.valueOf(disc.size());
            int bwid = Math.max(12, tw(n, 0.7f) + 7);
            pill(ctx, dx + bw - bwid + 3, y - 4, bwid, 11, 0xFF22324A);
            text(ctx, n, dx + bw - bwid + 3 + (bwid - tw(n, 0.7f)) / 2f, y - 2, 0xFFFFFFFF, 0.7f, false);
            if (hov) {
                tooltip = new ArrayList<>();
                tooltip.add(Text.literal(ui("discard_list", disc.size())).formatted(Formatting.BOLD));
                for (int k = disc.size() - 1; k >= Math.max(0, disc.size() - 6); k--) tooltip.add(Text.literal("· " + name(disc.get(k).getAsString())).formatted(Formatting.GRAY));
                if (disc.size() > 6) tooltip.add(Text.literal("…").formatted(Formatting.DARK_GRAY));
                tooltip.add(Text.literal(ui("discard_open")).formatted(Formatting.AQUA));
            }
            areas.add(new Area(dx, y, bw, bh, () -> openDiscard(sideKey)));
        } else roundOutline(ctx, dx, y, bw, bh, 0x60FFFFFF);
        int ly = opp ? y + bh + 3 : y - 9;
        textCS(ctx, ui("discard"), dx + bw / 2f, ly, 0xFFFFFFFF, 0.55f);
        textCS(ctx, ui("deck"), x + bw / 2f, ly, 0xFFFFFFFF, 0.55f);
    }

    // ------------------------------------------------------------------ HUD

    private void hud(DrawContext ctx, int mx, int my, double t) {
        JsonObject opp = side("opp"), me = side("me");
        nameChip(ctx, leftX, 6, s(opp, "name"), RED, !myTurn() && !over(), ui("hand_n", i(opp, "hand", 0)));
        nameChip(ctx, leftX, H - 22, s(me, "name"), BLUE, myTurn(), "");
        String mode = ui("mode." + s(v, "mode")) + (b(v, "ai") ? " · " + ui("level." + i(v, "level", 1)) : "");
        int mw = tw(mode, 0.65f) + 12;
        pill(ctx, rightX - mw, 5, mw, 11, b(v, "ranked") ? 0xF0FFF4D6 : 0xF0FFFFFF);
        text(ctx, mode, rightX - mw + 6, 7, b(v, "ranked") ? 0xFFB07A00 : SOFT, 0.65f, false);
        boolean turn = canAct();
        String ts = over() ? ui("finished") : myTurn() ? ui("your_turn") : b(v, "ai") && b(v, "waiting") ? ui("ai_thinking") : ui("opp_turn");
        if (b(v, "waiting") && !over()) ts += ".".repeat(1 + (int) (t * 3) % 3);
        int ew = Math.max(70, tw(ui("end_turn"), 1f) + 26), eh = 18, ex = rightX - ew, ey = midY - eh / 2;
        rects.put("btn:pass", new int[]{ex, ey, ew, eh});
        boolean eHover = turn && Ui.in(mx, my, ex, ey, ew, eh);
        if (turn) { // tu turno: botón azul brillante que late suavemente
            float pz = 0.5f + 0.5f * (float) Math.sin(t * 3);
            Ui.rounded(ctx, ex - 2, ey - 2, ew + 4, eh + 4, 10, withAlpha(BLUE, 0.12f + 0.12f * pz));
            glossy(ctx, ex, ey, ew, eh, BLUE, eHover, true);
        } else {
            ctx.fill(ex + 1, ey + 3, ex + ew + 1, ey + eh + 3, 0x220A2A50);
            pill(ctx, ex, ey, ew, eh, 0xC0FFFFFF);
            roundOutline(ctx, ex, ey, ew, eh, LINE);
        }
        float esc = Math.min(1f, (ew - 8f) / Math.max(1, textRenderer.getWidth(ui("end_turn"))));
        if (turn) textCS(ctx, ui("end_turn"), ex + ew / 2f, ey + 5 + pressed(eHover), 0xFFFFFFFF, esc);
        else textC(ctx, ui("end_turn"), ex + ew / 2f, ey + 5, 0xFFA9B6C8, esc);
        if (turn) areas.add(new Area(ex, ey, ew, eh, () -> act("pass")));
        int tsw = tw(ts, 0.7f) + 12;
        pill(ctx, ex + ew / 2 - tsw / 2, ey - 14, tsw, 10, myTurn() ? BLUE : over() ? SOFT : RED);
        text(ctx, ts, ex + ew / 2f - tsw / 2f + 6, ey - 12.5f, 0xFFFFFFFF, 0.7f, false);
        textCS(ctx, ui("turn_n", i(v, "turn", 0)), ex + ew / 2f, ey + eh + 4, 0xFFFFFFFF, 0.6f);
        if (!over() && (turn || prompt() != null)) {
            int idle = Math.max(1, i(v, "idle", 150));
            double leftS = Math.max(0, i(v, "idleLeft", idle) - (t - receivedAt));
            float frac = (float) (leftS / idle);
            float rx = ex - 12, ry = midY;
            StylePainter.circle(ctx, Math.round(rx), Math.round(ry), 8, 0xF0FFFFFF);
            ring(ctx, rx, ry, 6, 0xFFDDE5EF, 1, 1);
            ring(ctx, rx, ry, 6, frac > 0.3f ? BLUE : RED, frac, 1);
            textC(ctx, String.valueOf((int) leftS), rx + 0.5f, ry - 2, frac > 0.3f ? INK : RED, 0.5f);
        }
        roundButton(ctx, rightX - 12, H - 16, 10, suggestPending ? "…" : "?", GOLD, mx, my, turn && !suggestPending, "btn:suggest", this::askSuggestion,
                ui("suggest"));
        roundButton(ctx, rightX - 38, H - 16, 10, "☰", BLUE, mx, my, true, "btn:menu", () -> { menuOpen = !menuOpen; click(); }, ui("log") + " (L)");
        String hint = retreatMode ? ui("pick_retreat") : selectedHand >= 0 && !dragging ? ui("pick_target") : "";
        if (!hint.isEmpty()) {
            int w = tw(hint, 0.8f) + 16;
            pill(ctx, cx - w / 2, midY - 6, w, 12, 0xF0FFFFFF);
            roundOutline(ctx, cx - w / 2, midY - 6, w, 12, BLUE);
            text(ctx, hint, cx - w / 2f + 8, midY - 3, BLUE, 0.8f, false);
        }
    }

    private void nameChip(DrawContext ctx, int x, int y, String name, int color, boolean active, String extra) {
        String n = Ui.ellipsis(name, 90);
        int w = 20 + tw(n, 0.8f) + (extra.isEmpty() ? 6 : tw(extra, 0.6f) + 12);
        pill(ctx, x, y, w, 16, 0xF0FFFFFF);
        // de quién es el turno: la ficha se tiñe del color del jugador con un degradado
        if (active) DuelFx.hGradient(ctx, x + 3, y + 1, x + w - 3, y + 15, withAlpha(color, 0.28f), withAlpha(color, 0.04f));
        ctx.fill(x + 4, y + 14, x + w - 4, y + 15, withAlpha(color, active ? 0.9f : 0.35f));
        roundOutline(ctx, x, y, w, 16, active ? color : LINE);
        float p = 0.5f + 0.5f * (float) Math.sin(now() * 5);
        if (active) StylePainter.circle(ctx, x + 8, y + 8, 7, withAlpha(color, 0.25f + 0.25f * p));
        StylePainter.circle(ctx, x + 8, y + 8, 6, color);
        String l = name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase();
        text(ctx, l, x + 8 - tw(l, 0.75f) / 2f + 0.5f, y + 5.2f, 0xFFFFFFFF, 0.75f, false);
        text(ctx, n, x + 17, y + 4.5f, active ? Ui.darken(color, 50) : INK, 0.8f, false);
        if (!extra.isEmpty()) text(ctx, extra, x + 22 + tw(n, 0.8f), y + 5.5f, SOFT, 0.6f, false);
    }

    private void roundButton(DrawContext ctx, int cxp, int cyp, int r, String label, int color, int mx, int my, boolean enabled, String key, Runnable action, String tip) {
        rects.put(key, new int[]{cxp - r, cyp - r, r * 2, r * 2});
        boolean hover = enabled && Ui.in(mx, my, cxp - r, cyp - r, r * 2, r * 2);
        int base = enabled ? color : 0xFFB8C4D4;
        StylePainter.circle(ctx, cxp + 1, cyp + 2, r, 0x300A2A50);
        StylePainter.circle(ctx, cxp, cyp, r, base);
        StylePainter.circle(ctx, cxp, cyp, r - 2, hover ? base : 0xFFFFFFFF);
        textC(ctx, label, cxp + 0.5f, cyp - 3.5f, hover ? 0xFFFFFFFF : base, 1f);
        if (hover) tooltip = List.of(Text.literal(tip));
        if (enabled) areas.add(new Area(cxp - r, cyp - r, r * 2, r * 2, action));
    }

    private int drawerW() { return Math.min(260, W / 2); }

    private record LogRow(OrderedText text, LogLine line, boolean sep, boolean first, int entry) {}

    /** Registro completo: cabecera, turnos como separadores, cada jugada con su color, miniatura y las nuevas resaltadas. */
    private void logDrawer(DrawContext ctx, int mx, int my, double t) {
        int w = drawerW(), x = mx0 + 6, y = 24, h = H - 48;
        boolean live = menuOpen; // cerrándose: solo se dibuja, ya no recibe clics
        MatrixStack m = ctx.getMatrices();
        m.push();
        m.translate(-(1 - drawerT) * (w + 14), 0, 250);
        panel(ctx, x, y, w, h, 0xFAFFFFFF);
        Ui.roundedGradient(ctx, x + 1, y + 1, w - 2, 21, 3, 0xFFE6F0FF, 0xFFF4F8FF);
        ctx.fill(x + 1, y + 21, x + w - 1, y + 22, LINE);
        ctx.fill(x + 4, y + 21, x + w / 2, y + 22, withAlpha(BLUE, 0.6f));
        ctx.fill(x + w / 2, y + 21, x + w - 4, y + 22, withAlpha(RED, 0.6f));
        StylePainter.circle(ctx, x + 12, y + 11, 6, BLUE);
        text(ctx, "☰", x + 9.2f, y + 7.6f, 0xFFFFFFFF, 0.7f, false);
        text(ctx, ui("log").toUpperCase(), x + 22, y + 7.5f, INK, 0.9f, false);
        String cnt = ui("log_count", log.size());
        text(ctx, cnt, x + 26 + tw(ui("log").toUpperCase(), 0.9f), y + 9, SOFT, 0.6f, false);
        boolean xh = Ui.in(mx, my, x + w - 18, y + 4, 14, 14);
        if (xh) StylePainter.circle(ctx, x + w - 11, y + 11, 6, 0xFFFFE3E8);
        text(ctx, "×", x + w - 14, y + 6, xh ? RED : SOFT, 1.2f, false);
        int ly0 = y + 26, lh = h - 26 - 26;
        float sc = 0.78f;
        int textX = x + 22, wrap = Math.round((w - 32) / sc);
        List<LogRow> rows = new ArrayList<>();
        int entry = 0;
        for (LogLine l : log) {
            boolean sep = l.text().startsWith("—");
            boolean first = true;
            for (OrderedText ot : textRenderer.wrapLines(Text.literal(sep ? l.text().replace("—", "").trim() : l.text()), wrap)) {
                rows.add(new LogRow(ot, l, sep, first, entry));
                first = false;
            }
            entry++;
        }
        int step = 9, max = lh / step;
        logScroll = MathHelper.clamp(logScroll, 0, Math.max(0, rows.size() - max));
        int start = Math.max(0, rows.size() - max - logScroll);
        // el scissor no sigue a la matriz: se desplaza a mano junto con el cajón
        int off = Math.round(-(1 - drawerT) * (w + 14));
        ctx.enableScissor(x + 2 + off, ly0, x + w - 2 + off, ly0 + lh);
        int ly = ly0;
        for (int k = start; k < Math.min(rows.size(), start + max); k++) {
            LogRow r = rows.get(k);
            LogLine l = r.line();
            if (r.sep()) { // separador de turno con el color de quien juega
                int sw = tw(l.text().replace("—", "").trim(), 0.7f) + 14;
                ctx.fill(x + 10, ly + 4, x + w - 10, ly + 5, withAlpha(l.color(), 0.35f));
                pill(ctx, x + w / 2 - sw / 2, ly, sw, 9, l.color());
                drawOrdered(ctx, r.text(), x + w / 2f - sw / 2f + 7, ly + 1.5f, 0xFFFFFFFF, 0.7f);
            } else {
                float age = (float) (t - l.at());
                int bg = r.entry() % 2 == 0 ? 0x00FFFFFF : 0x0C2F8CFF;
                if (age < 2f) bg = withAlpha(l.color(), 0.16f * (1 - age / 2f));
                if ((bg >>> 24) > 0) ctx.fill(x + 6, ly - 1, x + w - 6, ly + step - 1, bg);
                ctx.fill(x + 8, ly - 1, x + 10, ly + step - 1, withAlpha(l.color(), 0.85f));
                if (r.first() && l.card() != null) {
                    int th = step + 1, tww = Math.max(4, Math.round(th * Draw.CARD_RATIO));
                    card(ctx, l.card(), x + 12, ly - 1, tww, th, 1f);
                } else if (r.first() && !DuelPalette.icon(l.kind()).isEmpty()) {
                    String ic = DuelPalette.icon(l.kind());
                    text(ctx, ic, x + 15.5f - tw(ic, 0.7f) / 2f, ly + 0.5f, l.color(), 0.7f, false);
                }
                drawOrdered(ctx, r.text(), textX, ly + 0.5f, l.color() == INK ? INK : Ui.darken(l.color(), 10), sc);
            }
            ly += step;
        }
        ctx.disableScissor();
        if (rows.size() > max) { // barra de desplazamiento
            int trackH = lh, barH = Math.max(12, trackH * max / rows.size());
            int barY = ly0 + (trackH - barH) * (rows.size() - max - logScroll) / Math.max(1, rows.size() - max);
            ctx.fill(x + w - 5, ly0, x + w - 3, ly0 + trackH, 0x200A2A50);
            ctx.fill(x + w - 5, barY, x + w - 3, barY + barH, withAlpha(BLUE, 0.7f));
        }
        if (live) {
            areas.add(new Area(x, y, w, h, () -> {})); // los clics dentro del panel no atraviesan al tablero
            areas.add(new Area(x + w - 18, y + 4, 14, 14, () -> menuOpen = false));
        }
        if (!over()) {
            if (concedeArmed && now() - concedeAt > 3) concedeArmed = false;
            String cl = ui(concedeArmed ? "concede_sure" : "concede");
            int bw2 = w - 20, by = y + h - 20;
            boolean ch = Ui.in(mx, my, x + 10, by, bw2, 14);
            pill(ctx, x + 10, by, bw2, 14, concedeArmed ? RED : ch ? 0xFFFFE3E8 : PANEL2);
            textC(ctx, cl, x + w / 2f, by + 3.5f, concedeArmed ? 0xFFFFFFFF : RED, 0.8f);
            if (live) areas.add(new Area(x + 10, by, bw2, 14, () -> {
                if (concedeArmed) act("concede");
                concedeArmed = !concedeArmed;
                concedeAt = now();
            }));
        }
        m.pop();
    }

    /**
     * Registro siempre a la vista junto a la mesa: las últimas jugadas entran deslizándose con su color y la miniatura
     * de la carta, la más nueva destaca y las viejas se apagan. Clic = registro completo.
     */
    private void logFeed(DrawContext ctx, int mx, int my, double t) {
        if (menuOpen || log.isEmpty()) return;
        int pwz = Math.round(bw * 0.5f);
        int x = leftX + 2 * pwz + 3 + 12;
        int stx = cx - aw / 2 - 18 - Math.round(bw * 0.85f);
        int w = Math.min(205, stx - 12 - x);
        if (w < 64) return;
        int top = oppActiveY + 12, bottom = myActiveY + ah;
        float sc = 0.62f;
        int thumbH = 13, thumbW = Math.round(thumbH * Draw.CARD_RATIO);
        int wrap = Math.round((w - 14 - thumbW) / sc);
        List<Integer> idx = new ArrayList<>();
        List<List<OrderedText>> wrapped = new ArrayList<>();
        int used = 0;
        for (int k = log.size() - 1; k >= 0 && idx.size() < 7; k--) {
            LogLine l = log.get(k);
            boolean sep = l.text().startsWith("—");
            List<OrderedText> lines = sep ? List.of() : textRenderer.wrapLines(Text.literal(l.text()), wrap);
            if (lines.size() > 2) lines = lines.subList(0, 2);
            int eh = sep ? 9 : lines.size() <= 1 ? 15 : 19;
            if (used + eh + 2 > bottom - top) break;
            used += eh + 2;
            idx.add(k);
            wrapped.add(lines);
        }
        int y = bottom - used;
        // cabecera
        boolean hh2 = Ui.in(mx, my, x, y - 13, w, 11);
        String title = ui("feed_title").toUpperCase();
        text(ctx, title, x + 2, y - 11, hh2 ? BLUE : 0xFF5C6F8A, 0.62f, false);
        ctx.fill(x + 6 + tw(title, 0.62f), y - 8, x + w - 14, y - 7, 0x400A2A50);
        text(ctx, "☰", x + w - 10, y - 12, hh2 ? BLUE : SOFT, 0.8f, false);
        if (hh2) tooltip = List.of(Text.literal(ui("feed_more") + " (L)"));
        for (int n = idx.size() - 1; n >= 0; n--) {
            LogLine l = log.get(idx.get(n));
            List<OrderedText> lines = wrapped.get(n);
            int rank = n;
            boolean sep = l.text().startsWith("—");
            int eh = sep ? 9 : lines.size() <= 1 ? 15 : 19;
            float age = (float) (t - l.at());
            float in = (float) Draw.easeOutBack(Math.min(1, age / 0.35));
            int ox = Math.round((1 - in) * -36);
            float al = MathHelper.clamp(age / 0.18f, 0, 1) * Math.max(0.5f, 1 - rank * 0.09f);
            if (sep) {
                String st = l.text().replace("—", "").trim();
                float ssc = 0.55f;
                int sw = Math.min(w, tw(st, ssc) + 12);
                pill(ctx, x + ox, y, sw, 9, withAlpha(Ui.darken(l.color(), 20), 0.85f * al));
                text(ctx, Ui.ellipsis(st, Math.round((sw - 10) / ssc)), x + ox + 6, y + 1.8f, withAlpha(0xFFFFFFFF, al), ssc, false);
            } else {
                boolean hov = Ui.in(mx, my, x, y, w, eh);
                ctx.fill(x + ox + 1, y + 2, x + ox + w + 1, y + eh + 2, withAlpha(0x1A0A2A50, al));
                pill(ctx, x + ox, y, w, eh, withAlpha(hov ? 0xFFF4F9FF : 0xFFFFFFFF, 0.95f * al));
                float flash = Math.max(0, 1 - age / 1.6f);
                if (flash > 0) {
                    pill(ctx, x + ox, y, w, eh, withAlpha(l.color(), 0.22f * flash * al));
                    roundOutline(ctx, x + ox - 1, y - 1, w + 2, eh + 2, withAlpha(l.color(), 0.8f * flash * al));
                }
                ctx.fill(x + ox + 2, y + 3, x + ox + 4, y + eh - 3, withAlpha(l.color(), al));
                int tx = x + ox + 7;
                if (l.card() != null) {
                    card(ctx, l.card(), tx, y + (eh - thumbH) / 2f, thumbW, thumbH, al);
                    tx += thumbW + 4;
                } else if (!DuelPalette.icon(l.kind()).isEmpty()) { // icono de la categoría: daño, curación, K.O., premio...
                    String ic = DuelPalette.icon(l.kind());
                    int icx = tx + thumbW / 2, icy = y + eh / 2;
                    StylePainter.circle(ctx, icx, icy, 4, withAlpha(l.color(), 0.18f * al));
                    text(ctx, ic, icx - tw(ic, 0.6f) / 2f + 0.5f, icy - 2.2f, withAlpha(l.color(), al), 0.6f, false);
                    tx += thumbW + 4;
                }
                float lyy = y + (lines.size() <= 1 ? 5f : 3.5f);
                // texto del color de su categoría (oscurecido para leerse bien sobre blanco); las jugadas normales en tinta
                int ink = l.kind() == DuelPalette.K_PLAY ? (rank == 0 ? INK : 0xFF3E506A) : Ui.darken(l.color(), rank == 0 ? 25 : 10);
                for (OrderedText ot : lines) {
                    drawOrdered(ctx, ot, tx, lyy, withAlpha(ink, al), sc);
                    lyy += 6.5f;
                }
                if (hov) {
                    List<Text> tip = new ArrayList<>();
                    if (l.card() != null) tip.add(Text.literal(name(l.card())).formatted(Formatting.BOLD));
                    for (OrderedText ot : textRenderer.wrapLines(Text.literal(l.text()), 220)) {
                        StringBuilder sb = new StringBuilder();
                        ot.accept((i2, style, cp) -> { sb.appendCodePoint(cp); return true; });
                        tip.add(Text.literal(sb.toString()));
                    }
                    tooltip = tip;
                }
            }
            y += eh + 2;
        }
        int fy = bottom - used - 13;
        areas.add(new Area(x, fy, w, used + 13, () -> { menuOpen = true; click(); }));
    }

    /** Carta que juega el rival: entra girando, se queda en grande un momento y se va a su pila. */
    private void showcaseDraw(DrawContext ctx, double t) {
        double a = t - showAt;
        if (showF.isEmpty() || a > 2.0) return;
        float ch = Math.min(H * 0.48f, 180), cw = ch * Draw.CARD_RATIO;
        float ccx = cx + aw * 0.5f + cw * 0.5f + 26, ccy = midY;
        if (ccx + cw / 2 > rightX - bw - 10) ccx = cx;
        float x, y, s, rx, ry, al;
        if (a < 0.4) {
            float e = (float) Draw.easeOutBack(a / 0.4);
            x = ccx;
            y = -ch / 2 + (ccy + ch / 2) * (float) Draw.easeOutCubic(a / 0.4);
            s = 0.55f + 0.45f * e;
            ry = 90 * (1 - (float) Draw.easeOutCubic(a / 0.4));
            rx = -20 * (1 - e);
            al = Math.min(1, (float) a / 0.12f);
        } else if (a < 1.55) {
            float k = (float) (a - 0.4);
            x = ccx;
            y = ccy;
            s = 1;
            rx = (float) Math.sin(k * 2.6) * 5;
            ry = (float) Math.cos(k * 2.0) * 9;
            al = 1;
        } else {
            float e = (float) Draw.easeInCubic((a - 1.55) / 0.45);
            float[] d = center("opp:discard");
            float tx = d == null ? ccx : d[0], ty = d == null ? ccy : d[1];
            x = ccx + (tx - ccx) * e;
            y = ccy + (ty - ccy) * e;
            s = 1 - 0.75f * e;
            rx = 10 * e;
            ry = -20 * e;
            al = 1 - 0.6f * e;
        }
        float dim = a < 0.3 ? (float) a / 0.3f : a > 1.55 ? 1 - (float) (a - 1.55) / 0.45f : 1;
        ctx.fill(0, 0, W, H, withAlpha(0xFF0E1E36, 0.18f * dim));
        MatrixStack m = ctx.getMatrices();
        m.push();
        m.translate(x, y, 0);
        m.scale(s, s, 1);
        Draw.glow(ctx, 0, 0, cw, ch, RED, 0.25f * al);
        card3d(ctx, showF, cw, ch, rx, ry, al, 1.4f, withAlpha(RED, 0.6f), 0f, t, foilOf(showF, false));
        m.pop();
        if (a > 0.3 && a < 1.6) {
            float la = Math.min(1, (float) (a - 0.3) / 0.15f) * (a > 1.45 ? 1 - (float) (a - 1.45) / 0.15f : 1);
            String who = ui("opp_plays", s(side("opp"), "name"));
            String nm = name(showF);
            int lw = Math.max(tw(who, 0.65f), tw(nm, 0.95f)) + 20, lx = Math.round(x - lw / 2f), ly = Math.round(y + ch / 2f + 8);
            ctx.fill(lx + 1, ly + 3, lx + lw + 1, ly + 25, withAlpha(0x300A2A50, la));
            pill(ctx, lx, ly, lw, 22, withAlpha(0xFFFFFFFF, 0.97f * la));
            ctx.fill(lx + 8, ly + 20, lx + lw - 8, ly + 21, withAlpha(RED, la));
            textC(ctx, who, x, ly + 3, withAlpha(SOFT, la), 0.65f);
            textC(ctx, nm, x, ly + 10, withAlpha(INK, la), 0.95f);
        }
    }

    private String suggestionText() {
        JsonObject sg = suggestion;
        if (sg == null) return "";
        return switch (s(sg, "t")) {
            case "play" -> {
                JsonObject tg = obj(sg.get("target"));
                String where = i(tg, "slot", 0) == BOARD ? ui("on_board") : i(tg, "slot", 0) == BENCH && monAt("me", BENCH, i(tg, "index", 0)) == null
                        ? ui("to_bench") : ui("on_mon", name(s(monAt(i(tg, "player", 2) == BOTTOM ? "me" : "opp", i(tg, "slot", 1), i(tg, "index", 0)), "f")));
                yield ui("suggest_play", name(s(sg, "f")), where);
            }
            case "attack" -> ui("suggest_attack", s(sg, "name"));
            case "retreat" -> ui("suggest_retreat", name(s(monAt("me", BENCH, i(sg, "bench", 0)), "f")));
            case "ability" -> ui("suggest_ability", s(sg, "name"));
            case "stadium" -> ui("suggest_stadium");
            case "pass" -> ui("suggest_pass");
            default -> "";
        };
    }

    // ------------------------------------------------------------------ mano y arrastre

    private boolean playable(int k) {
        JsonArray h = handCards();
        if (k >= h.size()) return false;
        JsonObject d = ClientPtcg.def(h.get(k).getAsString());
        JsonObject used = obj(side("me") == null ? null : side("me").get("used"));
        int st = i(d, "st", 0);
        if (st == 3 && b(used, "energy")) return false;
        if (st == 2 && i(d, "tt", 0) == 1 && b(used, "sup")) return false;
        return !dropsFor(k).isEmpty();
    }

    private List<Drop> dropsFor(int k) {
        List<Drop> out = new ArrayList<>();
        JsonArray h = handCards();
        if (k < 0 || k >= h.size()) return out;
        JsonObject d = ClientPtcg.def(h.get(k).getAsString());
        int st = i(d, "st", 0);
        JsonObject me = side("me");
        JsonArray bench = arr(me, "bench");
        if (st == 1) {
            int stage = i(d, "stage", 2);
            if (stage == 2 || stage == 1) {
                for (int b = 0; b < bench.size(); b++) if (!bench.get(b).isJsonObject()) out.add(new Drop("me:2:" + b, target(BOTTOM, BENCH, b)));
            } else {
                String ev = s(d, "ev");
                JsonObject a = obj(me == null ? null : me.get("active"));
                if (a != null && ename(s(a, "f")).equals(ev)) out.add(new Drop("me:1:0", target(BOTTOM, ACTIVE, 0)));
                for (int b = 0; b < bench.size(); b++) {
                    JsonObject mb = obj(bench.get(b));
                    if (mb != null && ename(s(mb, "f")).equals(ev)) out.add(new Drop("me:2:" + b, target(BOTTOM, BENCH, b)));
                }
            }
        } else if (st == 3 || (st == 2 && i(d, "tt", 0) == 3)) {
            if (me != null && me.get("active").isJsonObject()) out.add(new Drop("me:1:0", target(BOTTOM, ACTIVE, 0)));
            for (int b = 0; b < bench.size(); b++) if (bench.get(b).isJsonObject()) out.add(new Drop("me:2:" + b, target(BOTTOM, BENCH, b)));
        } else if (st == 2) out.add(new Drop("zone:board", target(BOTTOM, BOARD, 0)));
        return out;
    }

    private void computeDrops() {
        drops.clear();
        int k = dragging ? dragHand : selectedHand;
        if (k >= 0) drops.addAll(dropsFor(k));
        else if (dragActive || retreatMode) {
            JsonArray bench = arr(side("me"), "bench");
            for (int b = 0; b < bench.size(); b++) if (bench.get(b).isJsonObject()) drops.add(new Drop("me:2:" + b, target(BOTTOM, BENCH, b)));
        }
        if (!dragging) for (Drop d : drops) {
            int[] r = rects.get(d.key);
            if (r == null) continue;
            JsonObject tg = d.target;
            int hand = selectedHand;
            areas.add(new Area(r[0], r[1], r[2], r[3], () -> {
                if (hand >= 0) play(hand, tg);
                else if (retreatMode) retreat(i(tg, "index", 0));
            }));
        }
    }

    @Nullable
    private Drop dropAt(double mx, double my) {
        for (Drop d : drops) {
            int[] r = rects.get(d.key);
            if (r != null && Ui.in(mx, my, r[0] - 3, r[1] - 3, r[2] + 6, r[3] + 6)) return d;
        }
        return null;
    }

    private void dropHighlights(DrawContext ctx, int mx, int my, double t) {
        if (drops.isEmpty()) return;
        Drop over = dropAt(mx, my);
        float p = 0.5f + 0.5f * (float) Math.sin(t * 6);
        for (Drop d : drops) {
            int[] r = rects.get(d.key);
            if (r == null) continue;
            boolean hot = d == over;
            int col = hot ? GREEN : BLUE;
            if (d.key.equals("zone:board")) {
                round(ctx, r[0], r[1], r[2], r[3], withAlpha(col, hot ? 0.18f : 0.08f + 0.05f * p));
                roundOutline(ctx, r[0], r[1], r[2], r[3], withAlpha(col, 0.8f));
                String s = ui(dragging ? "drop_play" : "click_play");
                int w = tw(s, 0.9f) + 16;
                pill(ctx, r[0] + r[2] / 2 - w / 2, r[1] + r[3] / 2 - 7, w, 14, 0xF0FFFFFF);
                textC(ctx, s, r[0] + r[2] / 2f, r[1] + r[3] / 2f - 3.5f, col, 0.9f);
                continue;
            }
            Draw.glow(ctx, r[0] + r[2] / 2f, r[1] + r[3] / 2f, r[2], r[3], col, hot ? 0.5f : 0.2f + 0.15f * p);
            roundOutline(ctx, r[0] - 3, r[1] - 3, r[2] + 6, r[3] + 6, withAlpha(col, hot ? 1f : 0.55f + 0.4f * p));
            roundOutline(ctx, r[0] - 2, r[1] - 2, r[2] + 4, r[3] + 4, withAlpha(col, hot ? 1f : 0.55f + 0.4f * p));
        }
    }

    /** Empareja las cartas de la mano con su estado animado (así no saltan cuando juegas o robas una). */
    private void syncHand(JsonArray hand) {
        List<Anim> old = new ArrayList<>(handAnim);
        boolean[] used = new boolean[old.size()];
        handAnim.clear();
        for (int k = 0; k < hand.size(); k++) {
            String f = hand.get(k).getAsString();
            Anim found = null;
            for (int j = 0; j < old.size(); j++) if (!used[j] && old.get(j).f.equals(f)) { used[j] = true; found = old.get(j); break; }
            if (found == null) { found = new Anim(); found.f = f; }
            handAnim.add(found);
        }
    }

    private void hand(DrawContext ctx, int mx, int my, double t) {
        JsonArray hand = handCards();
        int n = hand.size();
        boolean wantUp = dragging || selectedHand >= 0 || lastHoverHand >= 0 || (my > 0 && my >= H - hh * 0.8f);
        handRaise += ((wantUp ? 1 : 0) - handRaise) * Math.min(1, dt * 11);
        // sin el ratón cerca, la mano baja y deja ver tu Banca
        int handBaseY = this.handBaseY + Math.round((1 - handRaise) * hh * 0.32f);
        hoverHand = -1;
        syncHand(hand);
        if (n == 0) { handReady = true; return; }
        int avail = Math.min(W - 160, Math.max(hw * 3, n * hw));
        int step = n <= 1 ? hw : Math.min(Math.round(hw * 0.85f), (avail - hw) / (n - 1));
        int total = (n - 1) * step + hw, x0 = cx - total / 2;
        if (!dragging)
            for (int k = n - 1; k >= 0; k--) if (Ui.in(mx, my, x0 + k * step, handBaseY - 4, k == n - 1 ? hw : step, H - handBaseY + 4)) { hoverHand = k; break; }
        if (hoverHand != lastHoverHand) {
            hoverAt = t;
            lastHoverHand = hoverHand;
            if (hoverHand >= 0 && t - lastHoverSound > 0.06) { lastHoverSound = t; sound(TcgSounds.CARD_SLIDE, 1.6f, 0.12f); }
        }
        float c = (n - 1) / 2f, ang = MathHelper.clamp(26f / n, 1.2f, 4.5f);
        boolean turn = canAct();
        float[] deck = center("me:deck");
        for (int k = 0; k < n; k++) {
            Anim an = handAnim.get(k);
            int x = x0 + k * step;
            rects.put("hand:" + k, new int[]{x, handBaseY, hw, hh});
            float tx = x + hw / 2f, ty, ta, ts, trx = 0, tryy = 0;
            if (k == selectedHand) {
                ty = handBaseY + hh * 0.5f;
                ta = 0;
                ts = 1.08f;
                trx = (float) Math.sin(t * 2.2) * 4;
                tryy = (float) Math.cos(t * 1.7) * 6;
            } else if (k == hoverHand) {
                ty = H - 4 - hh * 0.42f;
                ta = 0;
                ts = 1.15f;
                float ccy = ty - hh * ts / 2f;
                trx = MathHelper.clamp(-(my - ccy) / (hh * 0.6f), -1, 1) * 13;
                tryy = MathHelper.clamp((mx - tx) / (hw * 0.6f), -1, 1) * 17;
            } else {
                ta = (k - c) * ang;
                ty = handBaseY + hh + (k - c) * (k - c) * 0.8f;
                ts = 1;
                if (hoverHand >= 0) {
                    int d = k - hoverHand;
                    if (Math.abs(d) <= 2) tx += Math.signum(d) * hw * (Math.abs(d) == 1 ? 0.32f : 0.14f);
                }
            }
            if (!an.placed) {
                an.placed = true;
                if (handReady && deck != null) { // carta robada: sale volando del mazo y se da la vuelta
                    an.x.x = deck[0] + k * 6;
                    an.y.x = deck[1] + hh * 0.3f - k * hh * 0.12f;
                    an.a.x = -25;
                    an.s.x = 0.55f;
                    an.ry.x = 88;
                    an.rx.x = 12;
                    sound(TcgSounds.CARD_SLIDE, 1.4f + RNG.nextFloat() * 0.2f, 0.25f);
                } else {
                    an.x.x = tx;
                    an.y.x = ty;
                    an.a.x = ta;
                    an.s.x = ts;
                }
            }
            if (dragging && k == dragHand) {
                an.x.x = dragX;
                an.y.x = dragY + hh * dragAnim.s.x / 2f;
                an.a.x = dragAnim.a.x;
                an.s.x = dragAnim.s.x;
                an.x.v = an.y.v = an.a.v = an.s.v = 0;
                an.rx.x = dragAnim.rx.x;
                an.ry.x = dragAnim.ry.x;
                continue;
            }
            an.x.to(tx, dt, 210, 24);
            an.y.to(ty, dt, 210, 24);
            an.a.to(ta, dt, 190, 22);
            an.s.to(ts, dt, 260, 22);
            an.tilt(trx, tryy, dt);
        }
        handReady = true;
        for (int k = 0; k < n; k++) if (k != hoverHand && k != selectedHand && !(dragging && k == dragHand)) drawHand(ctx, hand, k, turn, t);
        if (selectedHand >= 0 && selectedHand < n && !(dragging && selectedHand == dragHand)) drawHand(ctx, hand, selectedHand, turn, t);
        if (hoverHand >= 0 && hoverHand != selectedHand) {
            drawHand(ctx, hand, hoverHand, turn, t);
            String why = turn ? unplayableReason(hoverHand) : null;
            if (why != null) {
                Anim an = handAnim.get(hoverHand);
                int ww = tw(why, 0.75f) + 14, wx = Math.round(an.x.x - ww / 2f), wy = Math.round(an.y.x - hh * an.s.x) - 16;
                pill(ctx, wx, wy, ww, 12, 0xF0FFF1F3);
                roundOutline(ctx, wx, wy, ww, 12, RED);
                text(ctx, why, wx + 7, wy + 3, 0xFFB0213A, 0.75f, false);
            }
        }
    }

    private void drawHand(DrawContext ctx, JsonArray hand, int k, boolean turn, double t) {
        Anim an = handAnim.get(k);
        float shakeX = k == lastPlayedHand && t - errorAt < 0.45 ? (float) Math.sin((t - errorAt) * 70) * 4 : 0;
        boolean pl = turn && playable(k);
        handCard(ctx, hand.get(k).getAsString(), an.x.x + shakeX, an.y.x, an.a.x, an.s.x, an.rx.x, an.ry.x, pl, k == selectedHand, turn && !pl, k == hoverHand, t);
    }

    /** Por qué no puedes jugar ahora esa carta de la mano (null si sí puedes). */
    @Nullable
    private String unplayableReason(int k) {
        JsonArray h = handCards();
        if (k < 0 || k >= h.size()) return null;
        JsonObject d = ClientPtcg.def(h.get(k).getAsString());
        JsonObject used = obj(side("me") == null ? null : side("me").get("used"));
        int st = i(d, "st", 0);
        if (st == 3 && b(used, "energy")) return ClientPtcg.tr("msg", "ENERGY_ALREADY_ATTACHED", null);
        if (st == 2 && i(d, "tt", 0) == 1 && b(used, "sup")) return ClientPtcg.tr("msg", "SUPPORTER_ALREADY_PLAYED", null);
        if (!dropsFor(k).isEmpty()) return null;
        if (st == 1 && (i(d, "stage", 2) == 2 || i(d, "stage", 2) == 1)) return ClientPtcg.tr("msg", "BENCH_IS_FULL", null);
        if (st == 1) return ui("no_evolve_target", s(d, "ev"));
        return ClientPtcg.tr("msg", "CANNOT_PLAY_THIS_CARD", null);
    }

    /** Al pasar el ratón por una carta de la mano: sus destinos posibles se marcan suavemente. */
    private void previewTargets(DrawContext ctx, double t) {
        if (hoverHand < 0 || dragging || selectedHand >= 0 || !canAct()) return;
        float p = 0.5f + 0.5f * (float) Math.sin(t * 5);
        for (Drop d : dropsFor(hoverHand)) {
            int[] r = rects.get(d.key);
            if (r == null || d.key.equals("zone:board")) continue;
            corners(ctx, r[0] - 4, r[1] - 4, r[2] + 8, r[3] + 8, 7, withAlpha(BLUE, 0.5f + 0.4f * p));
        }
    }

    private void handCard(DrawContext ctx, String f, float cxp, float bottom, float angle, float scale, float rx, float ry, boolean playable, boolean selected,
                          boolean dim, boolean lifted, double t) {
        MatrixStack m = ctx.getMatrices();
        m.push();
        m.translate(cxp, bottom, 0);
        if (angle != 0) m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(angle));
        m.scale(scale, scale, 1);
        m.translate(0, -hh / 2f, 0);
        if (selected) Draw.glow(ctx, 0, 0, hw, hh, GOLD, 0.45f);
        int glow = selected ? GOLD : playable ? withAlpha(0xFF5FC4FF, 0.55f + 0.35f * (float) Math.sin(t * 4 + cxp * 0.05)) : 0;
        card3d(ctx, f, hw, hh, rx, ry, 1f, lifted || selected ? 1f : 0.15f, glow, dim ? 1f : 0f, t);
        slabOverlay(ctx, -hw / 2, -hh / 2, hw, hh, gradeOf(true, f));
        m.pop();
    }

    private void dragged(DrawContext ctx, double t) {
        MatrixStack m = ctx.getMatrices();
        String f;
        int w, h;
        if (dragActive) {
            JsonObject a = monAt("me", ACTIVE, 0);
            if (a == null) return;
            f = s(a, "f");
            w = aw;
            h = ah;
        } else {
            JsonArray hd = handCards();
            if (dragHand < 0 || dragHand >= hd.size()) return;
            f = hd.get(dragHand).getAsString();
            w = hw;
            h = hh;
        }
        // la carta se inclina con la velocidad del arrastre y se balancea al parar
        if (dt > 0) {
            float vx = (dragX - lastDragX) / dt, vy = (dragY - lastDragY) / dt, k = Math.min(1, dt * 14);
            dragVx += (vx - dragVx) * k;
            dragVy += (vy - dragVy) * k;
        }
        lastDragX = dragX;
        lastDragY = dragY;
        Drop over = dropAt(dragX, dragY);
        dragAnim.ry.to(MathHelper.clamp(-dragVx * 0.045f, -34, 34), dt, 150, 10);
        dragAnim.rx.to(MathHelper.clamp(dragVy * 0.04f, -30, 30), dt, 150, 10);
        dragAnim.a.to(MathHelper.clamp(dragVx * 0.012f, -11, 11), dt, 140, 11);
        dragAnim.s.to(over != null ? 1.0f : 1.14f, dt, 240, 20);
        m.push();
        m.translate(dragX, dragY, 0);
        m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(dragAnim.a.x));
        m.scale(dragAnim.s.x, dragAnim.s.x, 1);
        card3d(ctx, f, w, h, dragAnim.rx.x, dragAnim.ry.x, 1f, over != null ? 0.6f : 1.8f, over != null ? GREEN : BLUE, 0f, t);
        m.pop();
    }

    private void suggestionOverlay(DrawContext ctx, double t) {
        JsonObject sg = suggestion;
        if (sg == null) return;
        String from = null, to = null;
        switch (s(sg, "t")) {
            case "play" -> {
                from = "hand:" + i(sg, "hand", 0);
                JsonObject tg = obj(sg.get("target"));
                to = tg == null ? null : i(tg, "slot", 0) == BOARD ? "zone:board" : keyOf(tg);
            }
            case "attack" -> to = "me:1:0";
            case "retreat" -> { from = "me:1:0"; to = "me:2:" + i(sg, "bench", 0); }
            case "ability" -> {
                JsonObject tg = obj(sg.get("target"));
                to = tg != null ? keyOf(tg) : "me:1:0";
            }
            case "stadium" -> to = "stadium";
            case "pass" -> to = "btn:pass";
            default -> {}
        }
        float p = 0.5f + 0.5f * (float) Math.sin(t * 5);
        for (String k : new String[]{from, to}) {
            int[] r = k == null ? null : rects.get(k);
            if (r == null) continue;
            Draw.glow(ctx, r[0] + r[2] / 2f, r[1] + r[3] / 2f, r[2], r[3], GOLD, 0.25f + 0.25f * p);
            roundOutline(ctx, r[0] - 3, r[1] - 3, r[2] + 6, r[3] + 6, withAlpha(GOLD, 0.6f + 0.4f * p));
        }
        float[] a = from == null ? null : center(from), b = to == null ? null : center(to);
        if (a != null && b != null) {
            float dx = b[0] - a[0], dy = b[1] - a[1], len = (float) Math.sqrt(dx * dx + dy * dy);
            int dots = (int) (len / 7);
            double off = (t * 30) % 7;
            for (int k = 0; k < dots; k++) {
                float f = (float) ((k * 7 + off) / len);
                if (f > 1) break;
                int px = Math.round(a[0] + dx * f), py = Math.round(a[1] + dy * f - (float) Math.sin(f * Math.PI) * len * 0.15f);
                ctx.fill(px - 1, py - 1, px + 2, py + 2, withAlpha(GOLD, 0.35f + 0.65f * f));
            }
            StylePainter.circle(ctx, Math.round(b[0]), Math.round(b[1]), 4, GOLD);
        }
        String st = suggestionText();
        if (!st.isEmpty()) {
            int w = Math.min(W / 2, tw(st, 0.75f) + 16);
            int x = rightX - 54 - w, y = H - 22;
            pill(ctx, x, y, w, 13, 0xF8FFF6DE);
            roundOutline(ctx, x, y, w, 13, GOLD);
            text(ctx, Ui.ellipsis(st, Math.round((w - 14) / 0.75f)), x + 8, y + 3.5f, 0xFF9A6A00, 0.75f, false);
        }
    }

    // ------------------------------------------------------------------ ampliación de carta (clic)

    private void zoom(DrawContext ctx, int mx, int my, double t) {
        String f = zoomCard;
        if (f == null) { zoomKey = null; return; }
        JsonObject mon = monByKey(zoomKey);
        if (isMonKey(zoomKey)) {
            if (mon == null) { zoomKey = null; return; }
            f = s(mon, "f");
        }
        float in = (float) Draw.easeOutCubic(MathHelper.clamp((t - zoomAt) / 0.22, 0, 1));
        ctx.fill(0, 0, W, H, withAlpha(0xFF0E1E36, 0.45f * in));
        areas.add(new Area(0, 0, W, H, () -> zoomKey = null));
        JsonObject d = ClientPtcg.def(f);
        int ch = Math.min(H - 40, 250), cw = Math.round(ch * Draw.CARD_RATIO);
        int dw = Math.min(210, W - cw - 50);
        int total = cw + 10 + dw, x0 = cx - total / 2, y0 = (H - ch) / 2;
        float fx0 = zoomFrom == null ? x0 : zoomFrom[0], fy0 = zoomFrom == null ? y0 : zoomFrom[1];
        float fw = zoomFrom == null ? cw : zoomFrom[2], fh = zoomFrom == null ? ch : zoomFrom[3];
        float x = fx0 + (x0 - fx0) * in, y = fy0 + (y0 - fy0) * in, w = fw + (cw - fw) * in, h = fh + (ch - fh) * in;
        boolean zOpp = zoomKey != null && zoomKey.startsWith("opp");
        int zg = zOpp ? gradeOf(false, f) : gradeOf(true, f);
        // la carta ampliada sigue al ratón en 3D, con reflejo y holo (menos giro si va en su funda graduada)
        float zcx = x + w / 2f, zcy = y + h / 2f, lim = zg > 0 ? 0.3f : 1f;
        zoomAnim.tilt(MathHelper.clamp(-(my - zcy) / (h * 0.7f), -1, 1) * 18 * lim, MathHelper.clamp((mx - zcx) / (w * 0.9f), -1, 1) * 24 * lim, dt);
        MatrixStack zm = ctx.getMatrices();
        zm.push();
        zm.translate(zcx, zcy, 0);
        card3d(ctx, f, w, h, zoomAnim.rx.x, zoomAnim.ry.x, 1f, 1.3f * in, 0, 0f, t, foilOf(f, !zOpp));
        zm.pop();
        if (in > 0.95f) slabOverlay(ctx, Math.round(x), Math.round(y), Math.round(w), Math.round(h), zg);
        areas.add(new Area(Math.round(x), Math.round(y), Math.round(w), Math.round(h), () -> {}));
        if (in < 0.6f) return;
        int px = x0 + cw + 10, py = y0;
        List<Runnable> deferred = new ArrayList<>();
        int ph = detailPanel(ctx, f, d, mon, px, py, dw, ch, mx, my, deferred);
        areas.add(new Area(px, py, dw, ph, () -> {}));
        for (Runnable r : deferred) r.run();
        textCS(ctx, ui("zoom_close"), cx, y0 + ch + 6, 0xFFFFFFFF, 0.65f);
    }

    /** Detalles de la carta ampliada con botones de acción (atacar, habilidad, retirarse, jugar, usar estadio). */
    private int detailPanel(DrawContext ctx, String f, @Nullable JsonObject d, @Nullable JsonObject mon, int x, int y, int w, int maxH,
                            int mx, int my, List<Runnable> deferred) {
        boolean mineActive = "me:1:0".equals(zoomKey), mine = zoomKey != null && zoomKey.startsWith("me:");
        boolean turn = canAct();
        int cy = y + 8;
        List<Runnable> draws = new ArrayList<>();
        int stype = i(d, "st", 0);
        // cabecera teñida del tipo de la carta (Pokémon: su tipo; Energía: la suya; Entrenador: su clase)
        int headCol = stype == 1 && arr(d, "ct").size() > 0 ? DuelPalette.energy(arr(d, "ct").get(0).getAsInt())
                : stype == 3 ? energyColor(f) : stype == 2 ? (i(d, "tt", 0) == 1 ? 0xFFF08A4B : i(d, "tt", 0) == 2 ? 0xFF3FBF7F : i(d, "tt", 0) == 3 ? 0xFF7A8AE0 : 0xFF4FA3E8)
                : SOFT;
        draws.add(() -> {
            Ui.roundedGradient(ctx, x + 1, y + 1, w - 2, 34, 3, withAlpha(headCol, 0.30f), withAlpha(headCol, 0.04f));
            ctx.fill(x + 4, y + 1, x + w - 4, y + 3, headCol);
        });
        final int fy = cy;
        draws.add(() -> text(ctx, Ui.ellipsis(name(f), Math.round((w - 16) / 1.1f)), x + 8, fy, INK, 1.1f, true));
        cy += 13;
        String sub = stype == 1 ? ui("stage." + i(d, "stage", 2)) : stype == 2 ? ui("trainer." + i(d, "tt", 0)) : ui("energy");
        final int sy = cy;
        final int hp = mon != null ? i(mon, "hp", 0) : i(d, "hp", 0), left = mon != null ? Math.max(0, hp - i(mon, "dmg", 0)) : hp;
        final String hs = stype == 1 ? left + "/" + hp + " " + ui("hp") : "";
        draws.add(() -> {
            text(ctx, sub, x + 8, sy, Ui.darken(SOFT, 15), 0.7f, false);
            if (stype == 1) { // tipos como píldoras de color, sin pisar los PS alineados a la derecha
                int bx2 = x + 12 + tw(sub, 0.7f), lim = x + w - 12 - tw(hs, 0.8f);
                for (JsonElement tE : arr(d, "ct")) {
                    int tt = tE.getAsInt();
                    if (bx2 + tw(ui("type." + tt), 0.6f) + 8 > lim) break;
                    bx2 += typeBadge(ctx, bx2, sy - 1, tt, "", 0.6f) + 3;
                }
            }
        });
        if (stype == 1) {
            draws.add(() -> text(ctx, hs, x + w - 8 - tw(hs, 0.8f), sy - 1, Ui.darken(DuelPalette.hp(hp == 0 ? 0 : left / (float) hp), 25), 0.8f, false));
        }
        cy += 11;
        if (mon != null) {
            JsonArray en = arr(mon, "en");
            if (en.size() > 0) {
                final int ey = cy;
                draws.add(() -> {
                    text(ctx, ui("attached"), x + 8, ey + 1, SOFT, 0.6f, false);
                    int ex = x + 8 + tw(ui("attached"), 0.6f) + 8;
                    for (JsonElement e : en) { energyDot(ctx, ex, ey + 4, 4, energyType(e.getAsString())); ex += 11; }
                });
                cy += 12;
            }
            for (JsonElement tl : arr(mon, "tools")) {
                final int ty = cy;
                final String tn = name(tl.getAsString());
                draws.add(() -> text(ctx, ui("tool") + ": " + tn, x + 8, ty, 0xFF5A6AC8, 0.65f, false));
                cy += 9;
            }
        }
        cy += 2;
        String[] parts = zoomKey == null ? new String[0] : zoomKey.split(":");
        int slot = isMonKey(zoomKey) ? Integer.parseInt(parts[1]) : ACTIVE, idx = isMonKey(zoomKey) ? Integer.parseInt(parts[2]) : 0;
        JsonArray powersArr = arr(d, "powers");
        for (int pi = 0; pi < powersArr.size(); pi++) {
            JsonObject pw = powersArr.get(pi).getAsJsonObject();
            String pn = s(pw, "n");
            String pnShow = ClientPtcg.powerName(f, pi, powersArr.size(), pn);
            List<OrderedText> lines = textRenderer.wrapLines(Text.literal(symbols(ClientPtcg.powerText(f, pi, powersArr.size(), s(pw, "t")))), Math.round((w - 22) / 0.65f));
            int bh2 = 14 + lines.size() * 7 + 4;
            boolean usable = mine && turn && isMonKey(zoomKey);
            final int by = cy;
            draws.add(() -> {
                boolean hover = usable && Ui.in(mx, my, x + 6, by, w - 12, bh2);
                round(ctx, x + 6, by, w - 12, bh2, hover ? 0xFFEDE4FF : 0xFFF6F2FF);
                roundOutline(ctx, x + 6, by, w - 12, bh2, usable ? VIOLET : 0xFFE2D8F8);
                text(ctx, "✦ " + pnShow, x + 11, by + 4, VIOLET, 0.85f, false);
                if (usable) text(ctx, ui("use"), x + w - 12 - tw(ui("use"), 0.7f), by + 5, VIOLET, 0.7f, false);
                int ly = by + 14;
                for (OrderedText ot : lines) { drawOrdered(ctx, ot, x + 11, ly, 0xFF5B5470, 0.65f); ly += 7; }
            });
            if (usable) deferred.add(() -> areas.add(new Area(x + 6, by, w - 12, bh2, () -> ability(pn, slot, slot == ACTIVE ? 0 : idx))));
            cy += bh2 + 3;
        }
        JsonArray atkOk = arr(side("me"), "atkOk");
        JsonArray attacks = arr(d, "attacks");
        for (int k = 0; k < attacks.size(); k++) {
            JsonObject at = attacks.get(k).getAsJsonObject();
            String an = s(at, "n");
            String anShow = ClientPtcg.attackName(f, k, attacks.size(), an);
            String atText = symbols(ClientPtcg.attackText(f, k, attacks.size(), s(at, "t")));
            List<OrderedText> lines = atText.isEmpty() ? List.of() : textRenderer.wrapLines(Text.literal(atText), Math.round((w - 22) / 0.65f));
            int bh2 = 16 + lines.size() * 7 + (lines.isEmpty() ? 0 : 3);
            boolean ok = !mineActive || k >= atkOk.size() || atkOk.get(k).getAsBoolean();
            boolean usable = mineActive && turn && ok;
            final int by = cy;
            draws.add(() -> {
                boolean hover = usable && Ui.in(mx, my, x + 6, by, w - 12, bh2);
                JsonArray cost = arr(at, "c");
                int ac = cost.size() > 0 ? DuelPalette.energy(cost.get(0).getAsInt()) : DuelPalette.energy(9);
                if (usable) Ui.roundedGradient(ctx, x + 6, by, w - 12, bh2, 3, hover ? 0xFFFFE3E9 : 0xFFFFF7F9, hover ? 0xFFFFD3DC : 0xFFFFEBEF);
                else round(ctx, x + 6, by, w - 12, bh2, PANEL2);
                ctx.fill(x + 7, by + 2, x + 9, by + bh2 - 2, withAlpha(ac, usable || !mineActive ? 1f : 0.45f)); // franja del tipo del coste
                roundOutline(ctx, x + 6, by, w - 12, bh2, usable ? (hover ? Ui.darken(RED, 30) : RED) : LINE);
                int ex = x + 13;
                for (JsonElement c : cost) { energyDot(ctx, ex, by + 7, 4, c.getAsInt()); ex += 10; }
                int txtCol = usable || !mineActive ? INK : 0xFF9AA6B8;
                text(ctx, anShow, ex + 2, by + 4, txtCol, 0.9f, false);
                String dm = i(at, "d", 0) > 0 ? i(at, "d", 0) + s(at, "dc") : "";
                if (!dm.isEmpty()) text(ctx, dm, x + w - 12 - tw(dm, 1.1f), by + 3, usable ? Ui.darken(RED, 35) : txtCol, 1.1f, false);
                int ly = by + 15;
                for (OrderedText ot : lines) { drawOrdered(ctx, ot, x + 11, ly, SOFT, 0.65f); ly += 7; }
                if (mineActive && turn && !ok) text(ctx, ui("no_energy"), x + w - 12 - tw(ui("no_energy"), 0.55f), by + bh2 - 7, RED, 0.55f, false);
            });
            if (usable) deferred.add(() -> areas.add(new Area(x + 6, by, w - 12, bh2, () -> attack(an))));
            cy += bh2 + 3;
        }
        if (stype != 1) {
            List<OrderedText> lines = textRenderer.wrapLines(Text.literal(symbols(ClientPtcg.cardText(f, s(d, "text")))), Math.round((w - 16) / 0.7f));
            final int ty = cy;
            draws.add(() -> { int ly = ty; for (OrderedText ot : lines) { drawOrdered(ctx, ot, x + 8, ly, INK, 0.7f); ly += 8; } });
            cy += lines.size() * 8 + 4;
        }
        if (stype == 1) {
            // Debilidad / Resistencia como píldoras del color del tipo; Retirada con sus Energías Incoloras
            JsonArray weak = arr(d, "weak"), res = arr(d, "res");
            final int retreatN = i(d, "retreat", 0);
            // maquetación previa: cada bloque (etiqueta + píldora) baja a otra línea si no cabe en el panel
            List<Object[]> wItems = new ArrayList<>(); // {isWeak, tipo, valor}
            for (JsonArray list : new JsonArray[]{weak, res}) {
                boolean isWeak = list == weak;
                for (JsonElement we : list) {
                    JsonObject wo = obj(we);
                    if (wo == null) continue;
                    int vv = i(wo, "v", 0);
                    String val = vv == 0 ? (isWeak ? "×2" : "") : vv > 0 ? "+" + vv : String.valueOf(vv);
                    wItems.add(new Object[]{isWeak, i(wo, "t", 9), val});
                }
            }
            int[][] wPos = new int[wItems.size() + 1][2]; // {x, línea}; el último es la Retirada
            int lx = x + 8, line = 0, right = x + w - 6;
            for (int k = 0; k < wItems.size(); k++) {
                Object[] it = wItems.get(k);
                String val = (String) it[2];
                int bw = tw(ui((Boolean) it[0] ? "weak" : "resist"), 0.6f) + 3
                        + tw(ui("type." + it[1]) + (val.isEmpty() ? "" : " " + val), 0.55f) + 8;
                if (lx > x + 8 && lx + bw > right) { lx = x + 8; line++; }
                wPos[k] = new int[]{lx, line};
                lx += bw + 7;
            }
            int rlw = tw(ui("retreat_n", retreatN), 0.6f) + 5 + Math.min(5, retreatN) * 8;
            if (lx > x + 8 && lx + rlw > right) { lx = x + 8; line++; }
            wPos[wItems.size()] = new int[]{lx, line};
            final int wy0 = cy + 1;
            draws.add(() -> {
                for (int k = 0; k < wItems.size(); k++) {
                    Object[] it = wItems.get(k);
                    boolean isWeak = (Boolean) it[0];
                    int wx = wPos[k][0], wy = wy0 + wPos[k][1] * 11;
                    String lab = ui(isWeak ? "weak" : "resist");
                    text(ctx, lab, wx, wy + 1, isWeak ? Ui.darken(RED, 20) : Ui.darken(GREEN, 20), 0.6f, false);
                    wx += tw(lab, 0.6f) + 3;
                    typeBadge(ctx, wx, wy - 1, (Integer) it[1], (String) it[2], 0.55f);
                }
                int wx = wPos[wItems.size()][0], wy = wy0 + wPos[wItems.size()][1] * 11;
                String rl = ui("retreat_n", retreatN);
                text(ctx, rl, wx, wy + 1, SOFT, 0.6f, false);
                int rx2 = wx + tw(rl, 0.6f) + 5;
                for (int k = 0; k < Math.min(5, retreatN) && rx2 < x + w - 6; k++, rx2 += 8) energyDot(ctx, rx2, wy + 3, 3, 9);
            });
            cy += 12 + line * 11;
            if (!s(d, "ev").isEmpty()) {
                final int ey = cy;
                draws.add(() -> text(ctx, ui("evolves_from", s(d, "ev")), x + 8, ey, SOFT, 0.65f, false)); // las especies se llaman igual en las cartas en español
                cy += 9;
            }
        }
        JsonObject used = obj(side("me") == null ? null : side("me").get("used"));
        List<String[]> buttons = new ArrayList<>();
        if (mineActive && turn && !b(used, "retreat") && hasBench()) buttons.add(new String[]{"retreat", ui("retreat") + " (" + i(side("me"), "retreatCost", 0) + ")"});
        if ("stadium".equals(zoomKey) && turn && !b(used, "stadium")) buttons.add(new String[]{"stadium", ui("use_stadium")});
        int handIdx = zoomKey != null && zoomKey.startsWith("hand:") ? Integer.parseInt(zoomKey.substring(5)) : -1;
        if (handIdx >= 0 && turn) buttons.add(new String[]{"play", dropsFor(handIdx).size() == 1 ? ui("play_card") : ui("choose_target")});
        for (String[] bt : buttons) {
            final int by = cy + 2;
            boolean enabled = !bt[0].equals("play") || !dropsFor(handIdx).isEmpty();
            draws.add(() -> {
                boolean hover = enabled && Ui.in(mx, my, x + 6, by, w - 12, 16);
                pill(ctx, x + 6, by, w - 12, 16, !enabled ? 0xFFE3E9F1 : hover ? Ui.brighten(BLUE, 25) : BLUE);
                textC(ctx, bt[1], x + w / 2f, by + 4.5f, enabled ? 0xFFFFFFFF : 0xFF9AA6B8, 0.85f);
            });
            if (enabled) deferred.add(() -> areas.add(new Area(x + 6, by, w - 12, 16, () -> {
                switch (bt[0]) {
                    case "retreat" -> { zoomKey = null; retreatMode = true; click(); }
                    case "stadium" -> { zoomKey = null; act("stadium"); }
                    default -> {
                        List<Drop> ds = dropsFor(handIdx);
                        zoomKey = null;
                        if (ds.size() == 1) play(handIdx, ds.get(0).target);
                        else { selectedHand = handIdx; click(); }
                    }
                }
            })));
            cy += 20;
        }
        int ph = Math.min(maxH, cy - y + 6);
        panel(ctx, x, y, w, ph, 0xFFFFFFFF);
        ctx.enableScissor(x, y, x + w, y + ph);
        for (Runnable r : draws) r.run();
        ctx.disableScissor();
        return ph;
    }

    /** {R}, {W}... de los textos de las cartas -> nombre del tipo de Energía. */
    private static String symbols(String t) {
        if (t == null || t.indexOf('{') < 0) return t == null ? "" : t;
        String[][] map = {{"{G}", "type.1"}, {"{R}", "type.2"}, {"{W}", "type.3"}, {"{L}", "type.4"}, {"{P}", "type.5"}, {"{F}", "type.6"},
                {"{D}", "type.7"}, {"{M}", "type.8"}, {"{C}", "type.9"}, {"{Y}", "type.10"}, {"{N}", "type.11"}};
        for (String[] m : map) t = t.replace(m[0], "[" + ui(m[1]) + "]");
        return t;
    }

    private void drawOrdered(DrawContext ctx, OrderedText ot, float x, float y, int color, float scale) {
        MatrixStack m = ctx.getMatrices();
        m.push();
        m.translate(x, y, 0);
        m.scale(scale, scale, 1);
        ctx.drawText(textRenderer, ot, 0, 0, color, false);
        m.pop();
    }

    private boolean hasBench() {
        for (JsonElement e : arr(side("me"), "bench")) if (e.isJsonObject()) return true;
        return false;
    }

    // ------------------------------------------------------------------ efectos

    /** Dibuja (y purga las caducadas) una lista de partículas con gravedad. */
    private void particles(DrawContext ctx, List<Particle> list, double t) {
        for (Iterator<Particle> it = list.iterator(); it.hasNext(); ) {
            Particle p = it.next();
            double a = (t - p.start) / p.life;
            if (a >= 1) { it.remove(); continue; }
            float age = (float) (t - p.start) * 60;
            float x = p.x + p.vx * age, y = p.y + p.vy * age + 0.04f * age * age;
            int s = Math.round(p.size);
            ctx.fill(Math.round(x), Math.round(y), Math.round(x) + s, Math.round(y) + s, withAlpha(p.color, (float) (1 - a)));
        }
    }

    private void effects(DrawContext ctx, double t) {
        koGhostsDraw(ctx, t);
        prizeFliesDraw(ctx, t);
        for (Iterator<double[]> it = rings.iterator(); it.hasNext(); ) {
            double[] r = it.next();
            double a = (t - r[3]) / 0.55;
            if (a >= 1) { it.remove(); continue; }
            float e = (float) Draw.easeOutCubic(a);
            ring(ctx, (float) r[0], (float) r[1], (float) (6 + r[4] * e), withAlpha((int) (long) r[2], (float) (1 - a)), 1, 2);
            ring(ctx, (float) r[0], (float) r[1], (float) (3 + r[4] * e * 0.7), withAlpha((int) (long) r[2], (float) (0.6 * (1 - a))), 1, 1);
        }
        for (Iterator<Fly> it = flies.iterator(); it.hasNext(); ) {
            Fly f = it.next();
            double a = (t - f.start) / 0.28;
            if (a >= 1) { it.remove(); continue; }
            float e = (float) Draw.easeOutCubic(a), sc = 1.1f - 0.4f * e;
            float x = f.x0 + (f.x1 - f.x0) * e, y = f.y0 + (f.y1 - f.y0) * e - (float) Math.sin(e * Math.PI) * 18;
            MatrixStack fm = ctx.getMatrices();
            fm.push();
            fm.translate(x + f.w / 2f, y + f.h / 2f, 0);
            fm.scale(sc, sc, 1);
            card3d(ctx, f.f, f.w, f.h, 28 * (1 - e), -14 * (1 - e), 1 - 0.6f * e, 1.6f * (1 - e), 0, 0f, t);
            fm.pop();
        }
        double sa = t - slashAt;
        if (sa < 0.4) {
            float k = (float) (sa / 0.4);
            MatrixStack m = ctx.getMatrices();
            m.push();
            m.translate(slashX, slashY, 0);
            m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-35));
            int len = Math.round(aw * 1.6f * Math.min(1, k * 3));
            int th = Math.max(1, Math.round(4 * (1 - k)));
            ctx.fill(-len / 2, -th, len / 2, th, withAlpha(0xFFFFFFFF, 1 - k));
            ctx.fill(-len / 2, -th * 3, len / 2, th * 3, withAlpha(slashColor, 0.5f * (1 - k)));
            m.pop();
            ctx.fill(0, 0, W, H, withAlpha(0xFFFFFFFF, 0.18f * (1 - k)));
        }
        particles(ctx, parts, t);
        for (Iterator<Fx> it = fx.iterator(); it.hasNext(); ) {
            Fx f = it.next();
            double age = t - f.start, a = age / f.life;
            if (a >= 1) { it.remove(); continue; }
            MatrixStack m = ctx.getMatrices();
            m.push();
            m.translate(0, 0, 200);
            float al = (float) Math.min(1, (1 - a) * 2.5);
            if (f.kind == 1 || f.kind == 2 || f.kind == 3) {
                // número de daño: entra enorme, rebota elástico, sube con una curva suave y se desvía un poco a un lado
                float pop = DuelFx.outElastic(age / 0.55);
                float sc = f.scale * (f.kind == 2 ? 0.8f + 0.2f * pop : 1.6f - 0.6f * pop);
                float rise = DuelFx.outCubic(a) * (f.kind == 3 ? 18 : 34);
                float x = f.x + f.drift * DuelFx.outCubic(a);
                float y = f.y - rise - 4 * sc;
                if (f.kind == 1 && age < 0.15) x += (float) Math.sin(age * 120) * 2.5f * (1 - (float) age / 0.15f); // temblor del impacto
                int outline = withAlpha(f.kind == 2 ? 0xFF0B4A26 : 0xFF3A0610, al);
                DuelFx.outlinedC(ctx, textRenderer, f.text, x, y, withAlpha(f.color, al), outline, Math.max(0.1f, sc));
                if (f.kind == 3 && age < 0.5) { // K.O.: destello blanco que se expande detrás
                    float g = 1 - (float) age / 0.5f;
                    Draw.glow(ctx, f.x, f.y, 60 * (1 + (1 - g)), 30 * (1 + (1 - g)), DuelPalette.LOG_KO, 0.5f * g);
                }
            } else {
                float pop = (float) Draw.easeOutBack(Math.min(1, a * 4));
                float y = f.y - DuelFx.outCubic(a) * 26;
                textCS(ctx, f.text, f.x + f.drift * 0.3f * (float) a, y - 4 * f.scale, withAlpha(f.color, al), f.scale * Math.max(0.1f, pop));
            }
            m.pop();
        }
    }

    /** Pokémon debilitado: tiembla en rojo, se apaga, gira un poco y se hunde desvaneciéndose. */
    private void koGhostsDraw(DrawContext ctx, double t) {
        for (Iterator<KoGhost> it = koGhosts.iterator(); it.hasNext(); ) {
            KoGhost g = it.next();
            double age = t - g.start;
            if (age > 1.1) { it.remove(); continue; }
            float shake = age < 0.35 ? (float) Math.sin(age * 85) * 3.5f * (1 - (float) age / 0.35f) : 0;
            float sink = DuelFx.inCubic((age - 0.3) / 0.8);
            float al = 1 - sink;
            if (al <= 0.01f) continue;
            MatrixStack m = ctx.getMatrices();
            m.push();
            m.translate(g.x + g.w / 2f + shake, g.y + g.h / 2f + sink * (g.mine ? 22 : -22), 60);
            m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(sink * (g.mine ? -14 : 14)));
            float sc = 1 - 0.18f * sink;
            m.scale(sc, sc, 1);
            float flash = age < 0.3 ? 1 - (float) age / 0.3f : 0;
            card3d(ctx, g.f, g.w, g.h, sink * 30, 0, al, 0.2f, flash > 0 ? withAlpha(DuelPalette.LOG_KO, flash) : 0, Math.min(1, (float) age * 2.5f), t);
            m.pop();
        }
    }

    /** Cartas de Premio volando a la mano: arco con giro, estela dorada y destello al llegar. */
    private void prizeFliesDraw(DrawContext ctx, double t) {
        for (Iterator<PrizeFly> it = prizeFlies.iterator(); it.hasNext(); ) {
            PrizeFly p = it.next();
            double age = t - p.start;
            if (age < 0) continue;
            if (age > 0.75) {
                burst(p.x1, p.y1, GOLD, 10, 1.4f);
                it.remove();
                continue;
            }
            float e = DuelFx.inOutCubic(age / 0.75);
            float x = p.x0 + (p.x1 - p.x0) * e, y = p.y0 + (p.y1 - p.y0) * e - (float) Math.sin(e * Math.PI) * 40;
            float sc = 1 + 0.6f * (float) Math.sin(e * Math.PI);
            float flipX = Math.abs((float) Math.cos(e * Math.PI * 2)); // se da la vuelta dos veces por el camino
            MatrixStack m = ctx.getMatrices();
            m.push();
            m.translate(x, y, 120);
            Draw.glow(ctx, 0, 0, p.w * 1.8f, p.h * 1.8f, GOLD, 0.35f * (1 - e * 0.5f));
            m.scale(sc * Math.max(0.08f, flipX), sc, 1);
            int iw = Math.round(p.w), ih = Math.round(p.h);
            back(ctx, -iw / 2, -ih / 2, iw, ih);
            roundOutline(ctx, -iw / 2 - 1, -ih / 2 - 1, iw + 2, ih + 2, GOLD);
            m.pop();
            if (RNG.nextFloat() < dt * 40) { // estela de chispas (por tiempo, no por fotograma)
                Particle sp = new Particle();
                sp.x = x;
                sp.y = y;
                sp.vx = (RNG.nextFloat() - 0.5f) * 0.6f;
                sp.vy = (RNG.nextFloat() - 0.5f) * 0.6f;
                sp.size = 1 + RNG.nextInt(2);
                sp.color = RNG.nextBoolean() ? GOLD : 0xFFFFF4C2;
                sp.start = t;
                sp.life = 0.4 + RNG.nextDouble() * 0.3;
                parts.add(sp);
            }
        }
    }

    /** Destello de color en los bordes de la pantalla (te atacan, K.O.). */
    private void vignetteDraw(DrawContext ctx, double t) {
        double a = t - vignetteAt;
        if (a > 0.9) return;
        float k = (float) (1 - a / 0.9);
        int band = Math.max(30, H / 4);
        ctx.fillGradient(0, 0, W, band, withAlpha(vignetteColor, 0.55f * k), withAlpha(vignetteColor, 0));
        ctx.fillGradient(0, H - band, W, H, withAlpha(vignetteColor, 0), withAlpha(vignetteColor, 0.55f * k));
        int steps = 10, sw = Math.max(3, W / 40);
        for (int s2 = 0; s2 < steps; s2++) {
            float al = 0.5f * k * (1 - s2 / (float) steps);
            ctx.fill(s2 * sw, 0, (s2 + 1) * sw, H, withAlpha(vignetteColor, al * 0.6f));
            ctx.fill(W - (s2 + 1) * sw, 0, W - s2 * sw, H, withAlpha(vignetteColor, al * 0.6f));
        }
    }

    /** Cambio de turno: franja que cruza la pantalla con "TU TURNO" / "TURNO DEL RIVAL" y destello. */
    private void turnDraw(DrawContext ctx, double t) {
        double a = t - turnFxAt;
        if (a > 1.7) return;
        int col = turnFxMine ? BLUE : RED, dark = DuelPalette.sideDark(turnFxMine);
        float flash = (float) Math.max(0, 1 - a / 0.35);
        ctx.fill(0, 0, W, H, withAlpha(col, 0.22f * flash));
        // franja: entra deslizándose con frenada suave, se queda y sale hacia el otro lado desvaneciéndose
        float in = DuelFx.outQuint(a / 0.45), out = a > 1.3 ? DuelFx.inCubic((a - 1.3) / 0.4) : 0;
        float al = Math.min(1, (float) a / 0.12f) * (1 - out);
        int dir = turnFxMine ? 1 : -1; // tu turno entra por la izquierda, el del rival por la derecha
        int bh = 46, by = midY - bh / 2;
        int bx0 = Math.round(dir * (-W + W * in + W * out * 1.2f));
        // sombra y bordes blancos
        ctx.fill(bx0, by - 4, bx0 + W, by + bh + 5, withAlpha(0xFF0A1A30, 0.18f * al));
        ctx.fill(bx0, by - 3, bx0 + W, by + bh + 3, withAlpha(0xFFFFFFFF, 0.9f * al));
        ctx.fillGradient(bx0, by, bx0 + W, by + bh, withAlpha(Ui.brighten(col, 45), al), withAlpha(dark, al));
        DuelFx.hGradient(ctx, bx0, by, bx0 + W, by + 2, withAlpha(0xFFFFFFFF, 0f), withAlpha(0xFFFFFFFF, 0.5f * al));
        for (int k = 0; k < 6; k++) {
            int sx = bx0 + (int) ((t * 220 * dir + k * 140) % (W + 140) + W + 140) % (W + 140) - 70;
            ctx.fill(sx, by, sx + 14, by + bh, withAlpha(0xFFFFFFFF, 0.12f * al));
        }
        // flechas ">>>" que avanzan en la dirección del turno
        int ch = bh / 2 - 4;
        for (int k = 0; k < 3; k++) {
            float ph = (float) ((t * 1.6 + k / 3.0) % 1.0);
            int ax = bx0 + W / 2 + dir * Math.round(W * 0.3f + ph * 26);
            float ca = al * (float) Math.sin(ph * Math.PI) * 0.7f;
            for (int r = -ch; r <= ch; r++) {
                int off = (ch - Math.abs(r)) / 2;
                ctx.fill(ax + dir * off, midY + r, ax + dir * off + 3 * dir, midY + r + 1, withAlpha(0xFFFFFFFF, ca));
            }
            int ax2 = bx0 + W / 2 - dir * Math.round(W * 0.3f + (1 - ph) * 26);
            for (int r = -ch; r <= ch; r++) {
                int off = (ch - Math.abs(r)) / 2;
                ctx.fill(ax2 + dir * off, midY + r, ax2 + dir * off + 3 * dir, midY + r + 1, withAlpha(0xFFFFFFFF, ca * 0.6f));
            }
        }
        String big = (turnFxMine ? ui("your_turn") : ui("opp_turn")).toUpperCase();
        String sub = ui("turn_n", i(v, "turn", 0)) + " · " + ui(turnFxMine ? "turn_hint_me" : "turn_hint_opp");
        int tx = bx0 + W / 2;
        float bigSc = Math.min(2.2f, (W * 0.5f) / Math.max(1, textRenderer.getWidth(big)));
        float pop = 0.85f + 0.15f * DuelFx.outBack((a - 0.1) / 0.4);
        DuelFx.outlinedC(ctx, textRenderer, big, tx, by + 23 - 4 * bigSc * pop - 3, withAlpha(0xFFFFFFFF, al), withAlpha(dark, 0.8f * al), bigSc * pop);
        float subSc = Math.min(0.8f, (W - 30f) / Math.max(1, textRenderer.getWidth(sub)));
        textCS(ctx, sub, tx, by + 33, withAlpha(0xFFFFFFFF, 0.9f * al), subSc);
    }

    private void bannerDraw(DrawContext ctx, double t) {
        double a = t - bannerAt;
        if (a > 1.6 || banner.isEmpty()) return;
        float in = (float) Draw.easeOutBack(Math.min(1, a / 0.3)), out = a > 1.25 ? (float) ((a - 1.25) / 0.35) : 0;
        float alpha = Math.min(1, (float) a / 0.15f) * (1 - out);
        String big = banner.toUpperCase();
        int w = Math.max(tw(big, 1.8f), tw(bannerSub, 0.75f)) + 56, h = bannerSub.isEmpty() ? 30 : 40;
        int x = cx - w / 2, y = midY - h / 2;
        MatrixStack m = ctx.getMatrices();
        m.push();
        m.translate(cx, midY, 0);
        float k = (0.8f + 0.2f * Math.max(0, in)) * Math.min(1f, (W - 24f) / Math.max(1, w)); // nunca se sale de la pantalla
        m.scale(k, k, 1);
        m.translate(-cx, -midY, 0);
        ctx.fill(x + 2, y + 4, x + w + 2, y + h + 4, withAlpha(0xFF0A2A50, 0.18f * alpha));
        pill(ctx, x, y, w, h, withAlpha(0xFFFFFFFF, 0.96f * alpha));
        Ui.roundedGradient(ctx, x + 2, y + 1, w - 4, h / 2, Math.min(8, h / 4), withAlpha(bannerColor, 0.16f * alpha), withAlpha(bannerColor, 0f));
        DuelFx.hGradient(ctx, x + 8, y + h - 3, x + w - 8, y + h - 1, withAlpha(bannerColor, 0.2f * alpha), withAlpha(bannerColor, alpha));
        if (!bannerSub.isEmpty()) textC(ctx, bannerSub, cx, y + 5, withAlpha(SOFT, alpha), 0.75f);
        textC(ctx, big, cx, y + (bannerSub.isEmpty() ? 8 : 15), withAlpha(bannerColor, alpha), 1.8f);
        if (!bannerCard.isEmpty() && alpha > 0.3f) {
            int cw = 22, chh = Math.round(cw / Draw.CARD_RATIO);
            framedCard(ctx, bannerCard, x - cw / 2, midY - chh / 2, cw, chh, alpha);
        }
        m.pop();
    }

    /** Moneda lanzada: altura (0..1), giro y bamboleo en el instante a (s). Termina mostrando cara o cruz. */
    private static float[] coinPose(double a, boolean heads, double air) {
        float end = 360f * 5 + (heads ? 0 : 180);
        if (a < air) {
            double p = Math.max(0, a / air);
            float hgt = (float) (4 * p * (1 - p));
            float flip = (float) (end * (1 - Math.pow(1 - p, 2.4)));
            return new float[]{hgt, flip, (float) Math.sin(a * 9) * 10};
        }
        double b = a - air;
        float bounce = (float) (Math.abs(Math.sin(b * 11)) * Math.exp(-b * 7) * 0.12);
        float wob = (float) (Math.sin(b * 26) * 16 * Math.exp(-b * 5));
        return new float[]{bounce, end + wob * 0.5f, wob};
    }

    /** Moneda de quien lanza: la que tenga equipada cada jugador (la casa usa la de su nivel). */
    private int coinStyle() {
        String id = s(v, coinMine ? "coinMe" : "coinOpp");
        return dev.tcgshop.duel.Cosmetics.coinIndex(id.isEmpty() ? "gold" : id);
    }

    /** Fondo de tablero equipado en la Liga (cada jugador ve el suyo). */
    private String boardId() {
        if (v != null && v.has("board")) return v.get("board").getAsString();
        com.google.gson.JsonObject L = ClientPtcg.league;
        if (L == null || !L.has("cos")) return "classic";
        com.google.gson.JsonObject c = L.getAsJsonObject("cos");
        return c.has("board") ? c.get("board").getAsString() : "classic";
    }

    /** Moneda de efectos durante la partida (ataques que lanzan monedas). */
    private void coinDraw(DrawContext ctx, double t) {
        double a = t - coinAt;
        if (a < 0 || a > 2.2) return;
        float alpha = a > 1.8 ? (float) (1 - (a - 1.8) / 0.4) : (float) Math.min(1, a / 0.1);
        float r = 15;
        float[] pose = coinPose(a, coinHeads, 0.9);
        float x = cx + aw + 40, ground = midY + 2, y = ground - pose[0] * 46;
        MatrixStack m = ctx.getMatrices();
        m.push();
        m.translate(x, ground + r * 0.95f, 0);
        Coin3D.shadow(ctx, r * (1 - pose[0] * 0.45f), alpha * 0.7f);
        m.pop();
        m.push();
        m.translate(x, y, 0);
        Coin3D.draw(ctx, r, pose[1], 16 + pose[2], coinStyle(), alpha);
        m.pop();
        if (a >= 0.95) {
            float pop = (float) Draw.easeOutBack(MathHelper.clamp((a - 0.95) / 0.25, 0, 1));
            String s = ui(coinHeads ? "coin_heads" : "coin_tails");
            int w = tw(s, 1f) + 14;
            m.push();
            m.translate(x, midY + r + 10, 0);
            m.scale(pop, pop, 1);
            pill(ctx, -w / 2, -6, w, 13, withAlpha(0xFFFFFFFF, alpha));
            roundOutline(ctx, -w / 2, -6, w, 13, withAlpha(coinHeads ? GOLD : SOFT, alpha));
            textC(ctx, s, 0, -3, withAlpha(coinHeads ? 0xFFB07A00 : SOFT, alpha), 1f);
            m.pop();
        }
    }

    /** La moneda del principio, que decide quién empieza: grande, en el centro, con su resultado. */
    private void bigCoinDraw(DrawContext ctx, double t) {
        if (bigCoinAt < 0 || introSkipped) return;
        double a = t - bigCoinAt;
        if (a < 0 || a > 2.8) return;
        final double air = 1.25;
        float alpha = a > 2.35 ? (float) (1 - (a - 2.35) / 0.45) : (float) Math.min(1, a / 0.18);
        ctx.fill(0, 0, W, H, withAlpha(0xFF08111F, 0.6f * alpha));
        float ty = H * 0.17f;
        float tin = (float) Draw.easeOutCubic(MathHelper.clamp(a / 0.35, 0, 1));
        textCS(ctx, ui("coin_title").toUpperCase(), cx, ty - (1 - tin) * 10, withAlpha(0xFFFFFFFF, alpha * tin), 1.5f);
        textCS(ctx, ui("coin_flipper", coinWho), cx, ty + 17, withAlpha(0xFFC9D8EE, alpha * tin), 0.8f);
        float r = Math.max(18, Math.min(H, W) * 0.1f);
        float[] pose = coinPose(a, coinHeads, air);
        float ground = H * 0.55f, y = ground - pose[0] * H * 0.24f, size = r * (1 + pose[0] * 0.22f);
        MatrixStack m = ctx.getMatrices();
        m.push();
        m.translate(cx, ground + r * 0.95f, 0);
        Coin3D.shadow(ctx, r * (1.1f - pose[0] * 0.5f), alpha * (0.9f - pose[0] * 0.5f));
        m.pop();
        if (a > air - 0.05) { // destello al caer
            float g = (float) Math.max(0, 1 - (a - air) / 0.5);
            Draw.glow(ctx, cx, ground, size * 2.6f, size * 2.6f, coinHeads ? GOLD : 0xFFB8C8DC, 0.5f * g * alpha);
        }
        m.push();
        m.translate(cx, y, 0);
        Coin3D.draw(ctx, size, pose[1], 18 + pose[2], coinStyle(), alpha);
        m.pop();
        if (!bigCoinLanded && a >= air) {
            bigCoinLanded = true;
            sound(SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), coinHeads ? 1.4f : 0.9f, 0.7f);
            sound(SoundEvents.BLOCK_CHAIN_PLACE, 1.6f, 0.5f);
            burst(cx, ground, coinHeads ? GOLD : 0xFFB8C8DC, 26, 2.2f);
        }
        if (a > air + 0.1) {
            float pop = (float) Draw.easeOutBack(MathHelper.clamp((a - air - 0.1) / 0.3, 0, 1));
            String big = ui(coinHeads ? "coin_heads" : "coin_tails").toUpperCase();
            String me = s(side("me"), "name"), opp = s(side("opp"), "name");
            String chooser = coinHeads ? coinWho : (coinWho.equals(me) ? opp : me);
            String sub = chooser.equals(me) ? ui("coin_you_choose") : ui("coin_choose", chooser);
            float ry0 = ground + r * 1.6f + 6;
            m.push();
            m.translate(cx, ry0, 0);
            m.scale(pop, pop, 1);
            int w = Math.max(tw(big, 1.8f), tw(sub, 0.8f)) + 40, h = 38;
            ctx.fill(-w / 2 + 2, 4, w / 2 + 2, h + 4, withAlpha(0xFF000000, 0.25f * alpha));
            pill(ctx, -w / 2, 0, w, h, withAlpha(0xFFFFFFFF, 0.97f * alpha));
            ctx.fill(-w / 2 + 10, h - 3, w / 2 - 10, h - 2, withAlpha(coinHeads ? GOLD : SOFT, alpha));
            textC(ctx, big, 0, 6, withAlpha(coinHeads ? 0xFFB07A00 : INK, alpha), 1.8f);
            textC(ctx, sub, 0, 25, withAlpha(chooser.equals(me) ? BLUE : RED, alpha), 0.8f);
            m.pop();
        }
    }

    /** Presentación de la partida: la mesa aparece desde negro y las dos franjas de jugador se cruzan con un "VS". */
    private void introDraw(DrawContext ctx, double t) {
        double a = t - introAt;
        if (introSkipped || a > 1.95) return;
        float fade = (float) MathHelper.clamp(1 - (a - 0.15) / 0.6, 0, 1);
        if (fade > 0) ctx.fill(0, 0, W, H, withAlpha(0xFF070D18, fade));
        float in = (float) Draw.easeOutCubic(MathHelper.clamp((a - 0.05) / 0.45, 0, 1));
        float out = a > 1.45 ? (float) Draw.easeInCubic(MathHelper.clamp((a - 1.45) / 0.45, 0, 1)) : 0;
        float alpha = 1 - out;
        if (alpha <= 0) return;
        int bh2 = 30, gap = 8;
        int yTop = midY - bh2 - gap / 2, yBot = midY + gap / 2;
        int xo = Math.round(W * (1 - in) + W * out * 0.4f), xm = Math.round(-W * (1 - in) - W * out * 0.4f);
        String me = s(side("me"), "name"), opp = s(side("opp"), "name");
        // rival (arriba, desde la derecha)
        ctx.fill(xo, yTop - 2, xo + W, yTop + bh2 + 2, withAlpha(0xFFFFFFFF, 0.9f * alpha));
        ctx.fillGradient(xo, yTop, xo + W, yTop + bh2, withAlpha(Ui.brighten(RED, 35), alpha), withAlpha(RED, alpha));
        textCS(ctx, opp.toUpperCase(), xo + W / 2f + 40, yTop + 8, withAlpha(0xFFFFFFFF, alpha), 1.6f);
        // tú (abajo, desde la izquierda)
        ctx.fill(xm, yBot - 2, xm + W, yBot + bh2 + 2, withAlpha(0xFFFFFFFF, 0.9f * alpha));
        ctx.fillGradient(xm, yBot, xm + W, yBot + bh2, withAlpha(Ui.brighten(BLUE, 35), alpha), withAlpha(BLUE, alpha));
        textCS(ctx, me.toUpperCase(), xm + W / 2f - 40, yBot + 8, withAlpha(0xFFFFFFFF, alpha), 1.6f);
        for (int k = 0; k < 5; k++) { // destellos que recorren las franjas
            int sx1 = xo + (int) ((a * 260 + k * 170) % (W + 120)) - 60, sx2 = xm + W - (int) ((a * 260 + k * 170) % (W + 120)) + 40;
            ctx.fill(sx1, yTop, sx1 + 12, yTop + bh2, withAlpha(0xFFFFFFFF, 0.12f * alpha));
            ctx.fill(sx2, yBot, sx2 + 12, yBot + bh2, withAlpha(0xFFFFFFFF, 0.12f * alpha));
        }
        // "VS"
        float vs = (float) Draw.easeOutBack(MathHelper.clamp((a - 0.35) / 0.3, 0, 1)) * (1 - out);
        if (vs > 0) {
            MatrixStack m = ctx.getMatrices();
            m.push();
            m.translate(cx, midY, 0);
            m.scale(vs, vs, 1);
            m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((1 - vs) * -40));
            StylePainter.circle(ctx, 0, 0, 21, withAlpha(0xFFFFFFFF, alpha));
            StylePainter.circle(ctx, 0, 0, 18, withAlpha(0xFF1B2A44, alpha));
            textC(ctx, "VS", 0.5f, -6, withAlpha(GOLD, alpha), 1.6f);
            m.pop();
        }
        String mode = ui("mode." + s(v, "mode")) + (b(v, "ai") ? " · " + ui("level." + i(v, "level", 1)) : "");
        textCS(ctx, mode, cx, yBot + bh2 + 10, withAlpha(0xFFFFFFFF, alpha * in), 0.75f);
        if (a > 0.6) textCS(ctx, ui("intro_skip"), cx, H - 14, withAlpha(0xFFFFFFFF, 0.5f * alpha), 0.6f);
    }

    private void toastDraw(DrawContext ctx, double t) {
        double a = t - toastAt;
        float alpha = a > 2.9 ? (float) (1 - (a - 2.9) / 0.5) : (float) Math.min(1, a * 6);
        boolean err = toastColor == RED;
        float sc = err ? 1f : 0.9f;
        int w = Math.min(W - 40, tw(toast, sc) + 40), h = err ? 22 : 18;
        int shake = err && a < 0.4 ? Math.round((float) Math.sin(a * 60) * 5 * (float) (1 - a / 0.4)) : 0;
        int x = cx - w / 2 + shake, y = 22 + Math.round((1 - Math.min(1, (float) a * 6)) * -12);
        ctx.fill(x + 1, y + 3, x + w + 1, y + h + 3, withAlpha(0x400A2A50, alpha));
        pill(ctx, x - 1, y - 1, w + 2, h + 2, withAlpha(err ? RED : 0xFFFFFFFF, alpha));
        pill(ctx, x, y, w, h, withAlpha(err ? 0xFFFFF1F3 : 0xFFFFFFFF, alpha));
        int ic = h / 2 - 2;
        StylePainter.circle(ctx, x + 4 + ic, y + h / 2, ic, withAlpha(toastColor, alpha));
        String icon = err ? "!" : toastColor == GOLD ? "✧" : toastColor == GREEN ? "✔" : "i";
        text(ctx, icon, x + 4 + ic - tw(icon, 0.8f) / 2f + 0.5f, y + h / 2f - 3, withAlpha(0xFFFFFFFF, alpha), 0.8f, false);
        text(ctx, Ui.ellipsis(toast, Math.round((w - 30) / sc)), x + 10 + ic * 2, y + (h - 8 * sc) / 2f + 0.5f, withAlpha(err ? 0xFFB0213A : INK, alpha), sc, false);
    }

    // ================================================================== decisiones

    private int ppx, ppy, ppw, pph;

    private void peekBar(DrawContext ctx, int mx, int my) {
        String s = ui("back_to_choice");
        int w = tw(s, 0.9f) + 24, x = cx - w / 2, y = 22;
        boolean hover = Ui.in(mx, my, x, y, w, 16);
        pill(ctx, x, y, w, 16, hover ? 0xFFFFF3D6 : 0xFFFFFFFF);
        roundOutline(ctx, x, y, w, 16, GOLD);
        text(ctx, s, x + 12, y + 4.5f, 0xFFB07A00, 0.9f, false);
        areas.add(new Area(x, y, w, 16, () -> peek = false));
    }

    private void promptPanel(DrawContext ctx, JsonObject p, int mx, int my, double t) {
        float veil = DuelFx.outCubic((t - promptAt) / 0.25); // el velo oscuro aparece con suavidad
        ctx.fill(0, 0, W, H, withAlpha(0xFF0E1E36, 0.4f * veil));
        areas.add(new Area(0, 0, W, H, () -> {}));
        String kind = s(p, "kind");
        ppw = Math.min(W - 40, 480);
        pph = Math.min(H - 40, kind.equals("confirm") || kind.equals("select") && arr(p, "values").size() < 5 ? 150 : 300);
        ppx = cx - ppw / 2;
        ppy = (H - pph) / 2;
        float in = (float) Draw.easeOutBack(MathHelper.clamp((t - promptAt) / 0.25, 0, 1));
        MatrixStack m = ctx.getMatrices();
        m.push();
        m.translate(cx, H / 2f, 0);
        float sc = 0.92f + 0.08f * in;
        m.scale(sc, sc, 1);
        m.translate(-cx, -H / 2f + (1 - DuelFx.outCubic((t - promptAt) / 0.25)) * 10, 0);
        panel(ctx, ppx, ppy, ppw, pph, 0xFFFFFFFF);
        DuelFx.hGradient(ctx, ppx + 3, ppy, ppx + ppw - 3, ppy + 3, Ui.brighten(BLUE, 40), VIOLET);
        String src = s(p, "src");
        int tx = ppx + 12, ty = ppy + 10;
        if (!src.isEmpty()) {
            int cw = 18, chh = Math.round(cw / Draw.CARD_RATIO);
            framedCard(ctx, src, tx, ty - 2, cw, chh, 1f);
            text(ctx, name(src), tx + cw + 6, ty, SOFT, 0.7f, false);
            tx += cw + 6;
            ty += 9;
        }
        String title = ClientPtcg.tr("msg", s(p, "msg"), null);
        if (title.isBlank()) title = ui("prompt." + kind);
        for (OrderedText line : textRenderer.wrapLines(Text.literal(title).formatted(Formatting.BOLD), ppx + ppw - 12 - tx - 70)) {
            ctx.drawText(textRenderer, line, tx, ty, INK, false);
            ty += 10;
        }
        String pk = ui("see_board");
        int pkw = tw(pk, 0.7f) + 12;
        boolean ph = Ui.in(mx, my, ppx + ppw - pkw - 10, ppy + 9, pkw, 12);
        pill(ctx, ppx + ppw - pkw - 10, ppy + 9, pkw, 12, ph ? 0xFFE3EEFB : PANEL2);
        text(ctx, pk, ppx + ppw - pkw - 4, ppy + 11.5f, BLUE, 0.7f, false);
        areas.add(new Area(ppx + ppw - pkw - 10, ppy + 9, pkw, 12, () -> peek = true));
        int bodyY = Math.max(ty, ppy + 24) + 4, footY = ppy + pph - 24, bodyH = footY - 14 - bodyY;
        int bx = ppx + 12;
        boolean cancel = b(p, "cancel");
        int min = i(p, "min", 0), max = i(p, "max", 1);
        switch (kind) {
            case "cards" -> bx = promptCards(ctx, p, bodyY, bodyH, footY, bx, mx, my, min, max);
            case "pokemon" -> {
                JsonArray targets = arr(p, "targets");
                hint(ctx, ui("pick_n", min, max, picks.size()), footY);
                tileRow(ctx, targets, bodyY + 12, bodyH - 12, mx, my, k -> picks.contains(k), k -> toggle(k, max), null);
                bx = primary(ctx, bx, footY, mx, my, picks.size() >= min && picks.size() <= max, () -> {
                    JsonArray r = new JsonArray();
                    for (int k : picks) r.add(targetOf(targets.get(k).getAsJsonObject()));
                    resolve(r);
                });
            }
            case "prize" -> {
                JsonArray slots = arr(p, "slots");
                int count = Math.min(i(p, "count", 1), slots.size());
                int cw = 38, chh = Math.round(cw / Draw.CARD_RATIO), gap = 8, total = slots.size() * (cw + gap) - gap;
                int x0 = cx - total / 2;
                for (int k = 0; k < slots.size(); k++) {
                    int px = x0 + k * (cw + gap), py = bodyY + Math.max(0, (bodyH - chh) / 2) - (picks.contains(k) ? 6 : 0);
                    if (picks.contains(k)) Draw.glow(ctx, px + cw / 2f, py + chh / 2f, cw, chh, GOLD, 0.5f);
                    shadow(ctx, px, py, cw, chh);
                    back(ctx, px, py, cw, chh);
                    if (Ui.in(mx, my, px, py, cw, chh)) hoverOutline(ctx, px, py, cw, chh);
                    int idx = k;
                    areas.add(new Area(px, py, cw, chh, () -> toggle(idx, count)));
                }
                hint(ctx, ui("pick_prizes", count), footY);
                bx = primary(ctx, bx, footY, mx, my, picks.size() == count, () -> {
                    JsonArray r = new JsonArray();
                    for (int k : picks) r.add(k);
                    resolve(r);
                });
            }
            case "confirm" -> {
                int bw2 = 96;
                bigButton(ctx, cx - bw2 - 5, footY - 12, bw2, 22, ui("yes"), mx, my, GREEN, () -> resolve(new JsonPrimitive(true)));
                bigButton(ctx, cx + 5, footY - 12, bw2, 22, ui("no"), mx, my, RED, () -> resolve(new JsonPrimitive(false)));
                cancel = false;
                bx = -1;
            }
            case "select" -> {
                JsonArray values = arr(p, "values");
                int oy = bodyY, bw2 = Math.min(ppw - 40, 280);
                for (int k = 0; k < values.size(); k++) {
                    String label = ClientPtcg.tr("msg", values.get(k).getAsString(), null);
                    int idx = k;
                    bigButton(ctx, cx - bw2 / 2, oy, bw2, 18, label, mx, my, BLUE, () -> resolve(new JsonPrimitive(idx)));
                    oy += 21;
                    if (oy > footY - 18) break;
                }
            }
            case "ok" -> {
                JsonArray cards = arr(p, "cards");
                if (cards.size() > 0) cardGrid(ctx, cards, bodyY, bodyH, mx, my, new HashSet<>(), false, 0, false);
                bx = primary(ctx, bx, footY, mx, my, true, () -> resolve(new JsonPrimitive(true)));
                cancel = false;
            }
            case "attach" -> bx = promptAttach(ctx, p, bodyY, bodyH, footY, bx, mx, my);
            case "moveEnergy" -> bx = promptMoveEnergy(ctx, p, bodyY, bodyH, footY, bx, mx, my);
            case "discardEnergy" -> bx = promptDiscardEnergy(ctx, p, bodyY, bodyH, footY, bx, mx, my);
            case "chooseEnergy" -> bx = promptChooseEnergy(ctx, p, bodyY, bodyH, footY, bx, mx, my);
            case "putDamage" -> bx = promptPutDamage(ctx, p, bodyY, bodyH, footY, bx, mx, my);
            case "moveDamage", "removeDamage" -> bx = promptMoveDamage(ctx, p, bodyY, bodyH, footY, bx, mx, my, kind.equals("removeDamage"));
            case "order" -> bx = promptOrder(ctx, p, bodyY, bodyH, footY, bx, mx, my);
            case "chooseAttack" -> promptChooseAttack(ctx, p, bodyY, bodyH, mx, my);
            default -> {
                int ly = bodyY;
                for (OrderedText line : textRenderer.wrapLines(Text.translatable("tcgshop.ptcg.ui.unknown_prompt"), ppw - 24)) {
                    ctx.drawText(textRenderer, line, ppx + 12, ly, SOFT, false);
                    ly += 10;
                }
            }
        }
        if (bx >= 0 && cancel) bx = secondary(ctx, bx, footY, mx, my, ui("cancel"), () -> resolve(JsonNull.INSTANCE));
        if (bx >= 0 && (!picks.isEmpty() || assigns.size() > 0 || !dmgMap.isEmpty()))
            secondary(ctx, bx, footY, mx, my, ui("reset"), () -> { resetPrompt(); click(); });
        String al = ui("auto");
        int aw2 = tw(al, 0.8f) + 18;
        boolean ah2 = Ui.in(mx, my, ppx + ppw - aw2 - 12, footY, aw2, 16);
        pill(ctx, ppx + ppw - aw2 - 12, footY, aw2, 16, ah2 ? 0xFFE3EEFB : PANEL2);
        text(ctx, al, ppx + ppw - aw2 - 3, footY + 4.5f, SOFT, 0.8f, false);
        areas.add(new Area(ppx + ppw - aw2 - 12, footY, aw2, 16, this::auto));
        m.pop();
    }

    private void hint(DrawContext ctx, String s, int footY) { text(ctx, s, ppx + 12, footY - 11, SOFT, 0.75f, false); }

    private int primary(DrawContext ctx, int x, int y, int mx, int my, boolean ok, Runnable r) {
        String l = ui("confirm");
        int w = tw(l, 0.9f) + 28;
        boolean hover = ok && Ui.in(mx, my, x, y, w, 16);
        if (ok) glossy(ctx, x, y, w, 16, BLUE, hover, true);
        else {
            Ui.rounded(ctx, x, y, w, 16, 8, 0xFFE3E9F1);
            Ui.roundedOutline(ctx, x, y, w, 16, 8, 0xFFCBD5E3);
        }
        float pr = pressed(hover);
        text(ctx, l, x + 14, y + 4.5f + pr, ok ? 0xFFFFFFFF : 0xFF9AA6B8, 0.9f, ok);
        if (ok) areas.add(new Area(x, y, w, 16, r));
        return x + w + 6;
    }

    private int secondary(DrawContext ctx, int x, int y, int mx, int my, String l, Runnable r) {
        int w = tw(l, 0.8f) + 20;
        boolean hover = Ui.in(mx, my, x, y, w, 16);
        glossy(ctx, x, y, w, 16, 0xFFFFFFFF, hover, false);
        text(ctx, l, x + 10, y + 4.5f + pressed(hover), INK, 0.8f, false);
        areas.add(new Area(x, y, w, 16, r));
        return x + w + 6;
    }

    private static float pressed(boolean hover) {
        return hover && MinecraftClient.getInstance().mouse.wasLeftButtonClicked() ? 1 : 0;
    }

    /**
     * Botón brillante del duelo: sombra, degradado, reflejo de cristal arriba, labio inferior del color y halo al pasar
     * el ratón; se hunde un píxel al pulsarlo. filled = relleno del color (si no, blanco con borde del color).
     */
    private static void glossy(DrawContext ctx, int x, int y, int w, int h, int accent, boolean hover, boolean filled) {
        int r = Math.min(h / 2, 9), dy = Math.round(pressed(hover));
        if (dy == 0) Ui.rounded(ctx, x, y + 2, w, h, r, 0x33203048);
        if (hover) Ui.rounded(ctx, x - 2, y - 2 + dy, w + 4, h + 4, r + 2, withAlpha(accent == 0xFFFFFFFF ? BLUE : accent, 0.22f));
        int top = filled ? Ui.brighten(accent, hover ? 40 : 22) : hover ? 0xFFFFFFFF : 0xFFFBFCFE;
        int bot = filled ? Ui.darken(accent, hover ? 10 : 25) : hover ? 0xFFE6F0FC : 0xFFE9EEF5;
        Ui.roundedGradient(ctx, x, y + dy, w, h, r, top, bot);
        Ui.roundedGradient(ctx, x + 1, y + dy + 1, w - 2, Math.max(2, h / 2 - 1), Math.max(0, r - 1), filled ? 0x55FFFFFF : 0x99FFFFFF, 0x00FFFFFF);
        Ui.roundedOutline(ctx, x, y + dy, w, h, r, filled ? Ui.darken(accent, 45) : hover ? BLUE : 0xFFC9D4E3);
    }

    private void bigButton(DrawContext ctx, int x, int y, int w, int h, String l, int mx, int my, int accent, Runnable r) {
        boolean hover = Ui.in(mx, my, x, y, w, h);
        glossy(ctx, x, y, w, h, hover ? accent : 0xFFFFFFFF, hover, hover);
        if (!hover) {
            int rr = Math.min(h / 2, 9);
            Ui.roundedOutline(ctx, x, y, w, h, rr, accent);
            ctx.fill(x + rr, y + h - 2, x + w - rr, y + h - 1, withAlpha(accent, 0.6f));
        }
        float sc = Math.min(0.9f, (w - 10) / (float) Math.max(1, textRenderer.getWidth(l)));
        textC(ctx, l, x + w / 2f, y + h / 2f - 3.5f * sc + pressed(hover), hover ? 0xFFFFFFFF : accent, sc);
        areas.add(new Area(x, y, w, h, r));
    }

    private void toggle(int k, int max) {
        if (!picks.remove(k)) {
            if (max == 1) picks.clear();
            if (picks.size() < Math.max(1, max)) picks.add(k);
        }
        click();
    }

    private int promptCards(DrawContext ctx, JsonObject p, int bodyY, int bodyH, int footY, int bx, int mx, int my, int min, int max) {
        JsonArray cards = arr(p, "cards");
        Set<Integer> blocked = new HashSet<>();
        for (JsonElement b : arr(p, "blocked")) blocked.add(b.getAsInt());
        cardGrid(ctx, cards, bodyY, bodyH, mx, my, blocked, b(p, "secret"), max, true);
        hint(ctx, ui("pick_n", min, max, picks.size()), footY);
        return primary(ctx, bx, footY, mx, my, picks.size() >= min && picks.size() <= max, () -> {
            JsonArray r = new JsonArray();
            picks.stream().sorted().forEach(r::add);
            resolve(r);
        });
    }

    private void cardGrid(DrawContext ctx, JsonArray cards, int y, int h, int mx, int my, Set<Integer> blocked, boolean secret, int max, boolean numbered) {
        int n = cards.size();
        if (n == 0) return;
        int w = ppw - 24, x = ppx + 12;
        int cw = 46, gap = 7;
        int chh = Math.round(cw / Draw.CARD_RATIO);
        int cols = Math.max(1, (w + gap) / (cw + gap)), rows = (n + cols - 1) / cols;
        while (rows * (chh + gap) > h && cw > 18) {
            cw -= 2;
            chh = Math.round(cw / Draw.CARD_RATIO);
            cols = Math.max(1, (w + gap) / (cw + gap));
            rows = (n + cols - 1) / cols;
        }
        int rowW = Math.min(n, cols) * (cw + gap) - gap, x0 = x + (w - rowW) / 2;
        List<Integer> order = new ArrayList<>(picks);
        for (int k = 0; k < n; k++) {
            int px = x0 + (k % cols) * (cw + gap), py = y + (k / cols) * (chh + gap);
            if (py + chh > y + h + 4) break;
            String f = cards.get(k).getAsString();
            boolean bl = blocked.contains(k), sel = picks.contains(k);
            if (sel) {
                py -= 4;
                Draw.glow(ctx, px + cw / 2f, py + chh / 2f, cw, chh, BLUE, 0.35f);
            }
            if (secret) { shadow(ctx, px, py, cw, chh); back(ctx, px, py, cw, chh); }
            else framedCard(ctx, f, px, py, cw, chh, bl ? 0.3f : 1f);
            if (sel) {
                roundOutline(ctx, px - 2, py - 2, cw + 4, chh + 4, BLUE);
                if (numbered) {
                    String num = String.valueOf(order.indexOf(k) + 1);
                    StylePainter.circle(ctx, px + cw - 3, py + 3, 6, BLUE);
                    text(ctx, num, px + cw - 3 - tw(num, 0.7f) / 2f + 0.5f, py + 0.6f, 0xFFFFFFFF, 0.7f, false);
                }
            }
            if (Ui.in(mx, my, px, py, cw, chh)) {
                if (!sel) hoverOutline(ctx, px, py, cw, chh);
                if (!secret) tooltip = describeShort(f);
            }
            int idx = k;
            if (!bl && max > 0) areas.add(new Area(px, py, cw, chh, () -> toggle(idx, max)));
        }
    }

    private List<Text> describeShort(String f) {
        List<Text> l = new ArrayList<>();
        JsonObject d = ClientPtcg.def(f);
        l.add(Text.literal(name(f)).formatted(Formatting.BOLD));
        if (d == null) return l;
        JsonArray pws = arr(d, "powers"), ats = arr(d, "attacks");
        for (int k = 0; k < pws.size(); k++) l.add(Text.literal("✦ " + ClientPtcg.powerName(f, k, pws.size(), s(pws.get(k).getAsJsonObject(), "n"))).formatted(Formatting.LIGHT_PURPLE));
        for (int k = 0; k < ats.size(); k++) {
            JsonObject at = ats.get(k).getAsJsonObject();
            l.add(Text.literal(ClientPtcg.attackName(f, k, ats.size(), s(at, "n")) + (i(at, "d", 0) > 0 ? "  " + i(at, "d", 0) + s(at, "dc") : "")).formatted(Formatting.WHITE));
        }
        String txt = symbols(ClientPtcg.cardText(f, s(d, "text")));
        if (!txt.isEmpty()) for (OrderedText ot : textRenderer.wrapLines(Text.literal(txt), 200)) {
            StringBuilder sb = new StringBuilder();
            ot.accept((idx, style, cp) -> { sb.appendCodePoint(cp); return true; });
            l.add(Text.literal(sb.toString()).formatted(Formatting.GRAY));
        }
        return l;
    }

    private void tileRow(DrawContext ctx, JsonArray targets, int y, int h, int mx, int my, IntPredicate selected, @Nullable IntConsumer onClick,
                         @Nullable EnergyClick onEnergy) {
        for (int k = 0; k < targets.size(); k++) {
            int[] r = tileRect(targets.size(), k, y, h, onEnergy != null);
            tile(ctx, targets.get(k).getAsJsonObject(), k, r[0], r[1], r[2], r[3], mx, my, selected.test(k), onClick, onEnergy);
        }
    }

    private int[] tileRect(int n, int k, int y, int h, boolean energies) {
        int w = ppw - 24, gap = 10;
        int cw = Math.min(58, (w - (n - 1) * gap) / Math.max(1, n)), rows = 1;
        int extra = energies ? 24 : 14;
        if (cw < 38) {
            rows = 2;
            int perRow = (n + 1) / 2;
            cw = Math.min(58, (w - (perRow - 1) * gap) / perRow);
        }
        int chh = Math.round(cw / Draw.CARD_RATIO);
        while (rows * (chh + extra + 8) > h && cw > 20) {
            cw -= 2;
            chh = Math.round(cw / Draw.CARD_RATIO);
        }
        int perRow = (n + rows - 1) / rows;
        int row = k / perRow, col = k % perRow, inRow = Math.min(perRow, n - row * perRow);
        int rowW = inRow * (cw + gap) - gap;
        return new int[]{cx - rowW / 2 + col * (cw + gap), y + row * (chh + extra + 8), cw, chh};
    }

    private interface EnergyClick { void click(int tile, int energyIndex); boolean selected(int tile, int energyIndex); }

    private void tile(DrawContext ctx, JsonObject t, int k, int x, int y, int w, int h, int mx, int my, boolean sel, @Nullable IntConsumer onClick,
                      @Nullable EnergyClick onEnergy) {
        String f = s(t, "f");
        if (sel) {
            y -= 3;
            Draw.glow(ctx, x + w / 2f, y + h / 2f, w, h, BLUE, 0.4f);
        }
        framedCard(ctx, f, x, y, w, h, 1f);
        int hp = i(t, "hp", 0), dmg = i(t, "dmg", 0) + dmgMap.getOrDefault("put:" + k, 0) - dmgMap.getOrDefault("rem:" + k, 0) * 10
                + dmgMap.getOrDefault("in:" + k, 0) * 10 - dmgMap.getOrDefault("out:" + k, 0) * 10;
        int left = Math.max(0, hp - dmg);
        float frac = hp == 0 ? 0 : left / (float) hp;
        ctx.fill(x, y - 4, x + w, y - 1, 0xFFFFFFFF);
        ctx.fill(x + 1, y - 3, x + 1 + Math.round((w - 2) * frac), y - 2, DuelPalette.hp(frac));
        String hs = String.valueOf(left);
        text(ctx, hs, x + w - tw(hs, 0.8f), y - 11, INK, 0.8f, false);
        if (sel) roundOutline(ctx, x - 2, y - 2, w + 4, h + 4, BLUE);
        if (Ui.in(mx, my, x, y, w, h)) {
            if (!sel) hoverOutline(ctx, x, y, w, h);
            tooltip = describeShort(f);
        }
        String label = ui(b(t, "mine") ? "yours" : "rival") + " · " + ui(i(t, "slot", 1) == ACTIVE ? "active" : "bench");
        float ls = Math.min(0.6f, w / (float) Math.max(1, textRenderer.getWidth(label)));
        textC(ctx, label, x + w / 2f, y + h + 3, b(t, "mine") ? BLUE : RED, ls);
        if (onClick != null) areas.add(new Area(x, y, w, h, () -> onClick.accept(k)));
        if (onEnergy != null) {
            JsonArray en = arr(t, "en");
            int r = 4, step = 10, perRow = Math.max(1, w / step);
            int ex0 = x + w / 2 - (Math.min(en.size(), perRow) * step) / 2 + step / 2;
            for (int e = 0; e < en.size(); e++) {
                JsonObject eo = en.get(e).getAsJsonObject();
                int ex = ex0 + (e % perRow) * step, ey = y + h + 15 + (e / perRow) * step;
                int ei = i(eo, "i", 0);
                if (onEnergy.selected(k, ei)) StylePainter.circle(ctx, ex, ey, r + 3, GOLD);
                energyDot(ctx, ex, ey, r, energyType(s(eo, "f")));
                if (Ui.in(mx, my, ex - r - 1, ey - r - 1, r * 2 + 3, r * 2 + 3)) tooltip = List.of(Text.literal(name(s(eo, "f"))));
                areas.add(new Area(ex - r - 1, ey - r - 1, r * 2 + 3, r * 2 + 3, () -> onEnergy.click(k, ei)));
            }
        }
    }

    private void badgeAt(DrawContext ctx, int[] r, String s, int color) {
        int w = tw(s, 0.8f) + 10;
        pill(ctx, r[0] + r[2] / 2 - w / 2, r[1] - 22, w, 11, color);
        text(ctx, s, r[0] + r[2] / 2f - tw(s, 0.8f) / 2f, r[1] - 20, 0xFFFFFFFF, 0.8f, false);
    }

    private int promptAttach(DrawContext ctx, JsonObject p, int bodyY, int bodyH, int footY, int bx, int mx, int my) {
        JsonArray cards = arr(p, "cards"), targets = arr(p, "targets");
        Set<Integer> blocked = new HashSet<>();
        for (JsonElement b : arr(p, "blocked")) blocked.add(b.getAsInt());
        int min = i(p, "min", 0), max = i(p, "max", 1);
        boolean sameT = b(p, "sameTarget"), diffT = b(p, "differentTargets");
        int cw = 32, chh = Math.round(cw / Draw.CARD_RATIO), gap = 6;
        int n = cards.size();
        while (n * (cw + gap) > ppw - 24 && cw > 16) { cw -= 2; chh = Math.round(cw / Draw.CARD_RATIO); }
        int rowW = n * (cw + gap) - gap, x0 = cx - rowW / 2;
        for (int k = 0; k < n; k++) {
            int px = x0 + k * (cw + gap), py = bodyY;
            String f = cards.get(k).getAsString();
            JsonObject asg = assignOf(k);
            boolean bl = blocked.contains(k) || i(ClientPtcg.def(f), "st", 0) != 3;
            boolean sel = srcPick == k;
            if (sel) { py -= 3; Draw.glow(ctx, px + cw / 2f, py + chh / 2f, cw, chh, GOLD, 0.5f); }
            framedCard(ctx, f, px, py, cw, chh, bl || asg != null ? 0.35f : 1f);
            if (asg != null) {
                StylePainter.circle(ctx, px + cw / 2, py + chh / 2, 6, GREEN);
                text(ctx, "✔", px + cw / 2f - 2.5f, py + chh / 2f - 3.5f, 0xFFFFFFFF, 0.8f, false);
            }
            if (sel) roundOutline(ctx, px - 2, py - 2, cw + 4, chh + 4, GOLD);
            if (Ui.in(mx, my, px, py, cw, chh)) tooltip = describeShort(f);
            int idx = k;
            if (!bl) areas.add(new Area(px, py, cw, chh, () -> {
                if (assignOf(idx) != null) removeAssign(idx);
                else srcPick = srcPick == idx ? -1 : idx;
                click();
            }));
        }
        int ty = bodyY + chh + 22;
        int th = bodyH - (ty - bodyY);
        tileRow(ctx, targets, ty, th, mx, my, k -> srcPick >= 0, k -> {
            if (srcPick < 0) { toast(ui("attach_pick_energy"), SOFT); return; }
            JsonObject to = targetOf(targets.get(k).getAsJsonObject());
            if (assigns.size() >= max) { toast(ui("max_reached", max), SOFT); return; }
            if (sameT && assigns.size() > 0 && !sameTarget(obj(assigns.get(0).getAsJsonObject().get("to")), to)) { toast(ui("same_target"), SOFT); return; }
            if (diffT) for (JsonElement e : assigns) if (sameTarget(obj(e.getAsJsonObject().get("to")), to)) { toast(ui("different_targets"), SOFT); return; }
            JsonObject a = new JsonObject();
            a.add("to", to);
            a.addProperty("index", srcPick);
            assigns.add(a);
            srcPick = -1;
            click();
        }, null);
        for (int k = 0; k < targets.size(); k++) {
            int c = 0;
            for (JsonElement e : assigns) if (sameTarget(obj(e.getAsJsonObject().get("to")), targets.get(k).getAsJsonObject())) c++;
            if (c > 0) badgeAt(ctx, tileRect(targets.size(), k, ty, th, false), "+" + c, GREEN);
        }
        hint(ctx, ui("attach_hint", assigns.size(), min, max), footY);
        return primary(ctx, bx, footY, mx, my, assigns.size() >= min && assigns.size() <= max, () -> resolve(assigns.deepCopy()));
    }

    @Nullable
    private JsonObject assignOf(int cardIdx) {
        for (JsonElement e : assigns) if (i(e.getAsJsonObject(), "index", -1) == cardIdx) return e.getAsJsonObject();
        return null;
    }

    private void removeAssign(int cardIdx) {
        for (int k = 0; k < assigns.size(); k++) if (i(assigns.get(k).getAsJsonObject(), "index", -1) == cardIdx) { assigns.remove(k); return; }
    }

    private int promptMoveEnergy(DrawContext ctx, JsonObject p, int bodyY, int bodyH, int footY, int bx, int mx, int my) {
        JsonArray targets = arr(p, "targets"), bFrom = arr(p, "blockedFrom"), bTo = arr(p, "blockedTo");
        int min = i(p, "min", 0), max = i(p, "max", 99);
        int ty = bodyY + 14;
        tileRow(ctx, targets, ty, bodyH - 14, mx, my, k -> srcTarget != null && sameTarget(srcTarget, targets.get(k).getAsJsonObject()), k -> {
            JsonObject to = targetOf(targets.get(k).getAsJsonObject());
            if (srcTarget == null || srcPick < 0) { toast(ui("move_pick_energy"), SOFT); return; }
            if (sameTarget(srcTarget, to)) { srcTarget = null; srcPick = -1; return; }
            if (inTargets(bTo, to)) { toast(ClientPtcg.tr("msg", "INVALID_TARGET", null), RED); return; }
            if (assigns.size() >= max) { toast(ui("max_reached", max), SOFT); return; }
            JsonObject a = new JsonObject();
            a.add("from", srcTarget);
            a.add("to", to);
            a.addProperty("index", srcPick);
            assigns.add(a);
            srcTarget = null;
            srcPick = -1;
            click();
        }, new EnergyClick() {
            public void click(int tile, int ei) {
                JsonObject from = targetOf(targets.get(tile).getAsJsonObject());
                if (inTargets(bFrom, from)) { toast(ClientPtcg.tr("msg", "INVALID_TARGET", null), RED); return; }
                for (int k = 0; k < assigns.size(); k++) {
                    JsonObject a = assigns.get(k).getAsJsonObject();
                    if (sameTarget(obj(a.get("from")), from) && i(a, "index", -1) == ei) { assigns.remove(k); return; }
                }
                srcTarget = from;
                srcPick = ei;
                PtcgDuelScreen.this.click();
            }

            public boolean selected(int tile, int ei) {
                JsonObject from = targetOf(targets.get(tile).getAsJsonObject());
                if (srcTarget != null && sameTarget(srcTarget, from) && srcPick == ei) return true;
                for (JsonElement e : assigns) if (sameTarget(obj(e.getAsJsonObject().get("from")), from) && i(e.getAsJsonObject(), "index", -1) == ei) return true;
                return false;
            }
        });
        for (int k = 0; k < targets.size(); k++) {
            int c = 0;
            for (JsonElement e : assigns) if (sameTarget(obj(e.getAsJsonObject().get("to")), targets.get(k).getAsJsonObject())) c++;
            if (c > 0) badgeAt(ctx, tileRect(targets.size(), k, ty, bodyH - 14, true), "+" + c, GREEN);
        }
        hint(ctx, ui("move_energy_hint", assigns.size()), footY);
        return primary(ctx, bx, footY, mx, my, assigns.size() >= min && assigns.size() <= max, () -> resolve(assigns.deepCopy()));
    }

    private int promptDiscardEnergy(DrawContext ctx, JsonObject p, int bodyY, int bodyH, int footY, int bx, int mx, int my) {
        JsonArray targets = arr(p, "targets");
        int min = i(p, "min", 0), max = i(p, "max", 99);
        tileRow(ctx, targets, bodyY + 14, bodyH - 14, mx, my, k -> false, null, new EnergyClick() {
            public void click(int tile, int ei) {
                JsonObject from = targetOf(targets.get(tile).getAsJsonObject());
                for (int k = 0; k < assigns.size(); k++) {
                    JsonObject a = assigns.get(k).getAsJsonObject();
                    if (sameTarget(obj(a.get("from")), from) && i(a, "index", -1) == ei) { assigns.remove(k); return; }
                }
                if (assigns.size() >= max) { toast(ui("max_reached", max), SOFT); return; }
                JsonObject a = new JsonObject();
                a.add("from", from);
                a.addProperty("index", ei);
                assigns.add(a);
                PtcgDuelScreen.this.click();
            }

            public boolean selected(int tile, int ei) {
                JsonObject from = targetOf(targets.get(tile).getAsJsonObject());
                for (JsonElement e : assigns) if (sameTarget(obj(e.getAsJsonObject().get("from")), from) && i(e.getAsJsonObject(), "index", -1) == ei) return true;
                return false;
            }
        });
        hint(ctx, ui("discard_energy_hint", assigns.size(), min, max), footY);
        return primary(ctx, bx, footY, mx, my, assigns.size() >= min && assigns.size() <= max, () -> resolve(assigns.deepCopy()));
    }

    private int promptChooseEnergy(DrawContext ctx, JsonObject p, int bodyY, int bodyH, int footY, int bx, int mx, int my) {
        JsonArray cards = arr(p, "cards"), cost = arr(p, "cost");
        int ex = cx - cost.size() * 6;
        text(ctx, ui("cost"), ex - tw(ui("cost"), 0.7f) - 6, bodyY + 1, SOFT, 0.7f, false);
        for (JsonElement c : cost) { energyDot(ctx, ex + 5, bodyY + 4, 4, c.getAsInt()); ex += 12; }
        cardGrid(ctx, cards, bodyY + 14, bodyH - 14, mx, my, new HashSet<>(), false, cards.size(), false);
        hint(ctx, ui("choose_energy_hint", picks.size()), footY);
        return primary(ctx, bx, footY, mx, my, !picks.isEmpty() || cost.isEmpty(), () -> {
            JsonArray r = new JsonArray();
            picks.stream().sorted().forEach(r::add);
            resolve(r);
        });
    }

    private int promptPutDamage(DrawContext ctx, JsonObject p, int bodyY, int bodyH, int footY, int bx, int mx, int my) {
        JsonArray targets = arr(p, "targets");
        int total = i(p, "damage", 0), step = Math.max(10, i(p, "step", 10));
        boolean partial = b(p, "partial");
        int placed = 0;
        for (int k = 0; k < targets.size(); k++) placed += dmgMap.getOrDefault("put:" + k, 0);
        int fp = placed, ty = bodyY + 18;
        tileRow(ctx, targets, ty, bodyH - 18, mx, my, k -> dmgMap.getOrDefault("put:" + k, 0) > 0, k -> {
            if (fp + step > total) { toast(ui("damage_all_placed"), SOFT); return; }
            dmgMap.merge("put:" + k, step, Integer::sum);
            sound(SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(), 1.4f, 0.5f);
        }, null);
        for (int k = 0; k < targets.size(); k++) {
            int d = dmgMap.getOrDefault("put:" + k, 0);
            if (d <= 0) continue;
            int[] r = tileRect(targets.size(), k, ty, bodyH - 18, false);
            badgeAt(ctx, r, "+" + d, RED);
            int kk = k, rx = r[0] + r[2] - 9, ry = r[1] + r[3] + 11;
            pill(ctx, rx, ry, 10, 10, RED);
            text(ctx, "−", rx + 3, ry + 1.5f, 0xFFFFFFFF, 0.8f, false);
            areas.add(new Area(rx, ry, 10, 10, () -> {
                int nv = dmgMap.getOrDefault("put:" + kk, 0) - step;
                if (nv <= 0) dmgMap.remove("put:" + kk);
                else dmgMap.put("put:" + kk, nv);
            }));
        }
        hint(ctx, ui("put_damage_hint", placed, total), footY);
        return primary(ctx, bx, footY, mx, my, placed == total || (partial && placed > 0), () -> {
            JsonArray r = new JsonArray();
            for (int k = 0; k < targets.size(); k++) {
                int d = dmgMap.getOrDefault("put:" + k, 0);
                if (d <= 0) continue;
                JsonObject o = new JsonObject();
                o.add("target", targetOf(targets.get(k).getAsJsonObject()));
                o.addProperty("damage", d);
                r.add(o);
            }
            resolve(r);
        });
    }

    private int promptMoveDamage(DrawContext ctx, JsonObject p, int bodyY, int bodyH, int footY, int bx, int mx, int my, boolean remove) {
        JsonArray targets = arr(p, "targets"), bFrom = arr(p, "blockedFrom"), bTo = arr(p, "blockedTo");
        int min = i(p, "min", 0), max = i(p, "max", 99);
        tileRow(ctx, targets, bodyY + 18, bodyH - 18, mx, my, k -> !remove && srcPick == k, k -> {
            JsonObject t = targetOf(targets.get(k).getAsJsonObject());
            int curDmg = i(targets.get(k).getAsJsonObject(), "dmg", 0) - 10 * (dmgMap.getOrDefault("out:" + k, 0) + dmgMap.getOrDefault("rem:" + k, 0))
                    + 10 * dmgMap.getOrDefault("in:" + k, 0);
            if (assigns.size() >= max) { toast(ui("max_reached", max), SOFT); return; }
            if (remove) {
                if (curDmg < 10 || inTargets(bFrom, t)) { toast(ui("no_damage_here"), SOFT); return; }
                JsonObject a = new JsonObject();
                a.add("from", t);
                a.add("to", t);
                assigns.add(a);
                dmgMap.merge("rem:" + k, 1, Integer::sum);
                click();
                return;
            }
            if (srcPick < 0) {
                if (curDmg < 10 || inTargets(bFrom, t)) { toast(ui("no_damage_here"), SOFT); return; }
                srcPick = k;
                click();
                return;
            }
            if (srcPick == k) { srcPick = -1; return; }
            if (inTargets(bTo, t)) { toast(ClientPtcg.tr("msg", "INVALID_TARGET", null), RED); return; }
            JsonObject a = new JsonObject();
            a.add("from", targetOf(targets.get(srcPick).getAsJsonObject()));
            a.add("to", t);
            assigns.add(a);
            dmgMap.merge("out:" + srcPick, 1, Integer::sum);
            dmgMap.merge("in:" + k, 1, Integer::sum);
            int src = srcPick;
            int srcLeft = i(targets.get(src).getAsJsonObject(), "dmg", 0) - 10 * dmgMap.getOrDefault("out:" + src, 0) + 10 * dmgMap.getOrDefault("in:" + src, 0);
            if (srcLeft < 10) srcPick = -1;
            click();
        }, null);
        hint(ctx, ui(remove ? "remove_damage_hint" : "move_damage_hint", assigns.size() * 10), footY);
        return primary(ctx, bx, footY, mx, my, assigns.size() >= min && assigns.size() <= max, () -> resolve(assigns.deepCopy()));
    }

    private int promptOrder(DrawContext ctx, JsonObject p, int bodyY, int bodyH, int footY, int bx, int mx, int my) {
        JsonArray cards = arr(p, "cards");
        cardGrid(ctx, cards, bodyY + 4, bodyH - 4, mx, my, new HashSet<>(), false, cards.size(), true);
        hint(ctx, ui("order_hint", picks.size(), cards.size()), footY);
        return primary(ctx, bx, footY, mx, my, picks.size() == cards.size(), () -> {
            JsonArray r = new JsonArray();
            for (int k : picks) r.add(k);
            resolve(r);
        });
    }

    private void promptChooseAttack(DrawContext ctx, JsonObject p, int bodyY, int bodyH, int mx, int my) {
        JsonArray cards = arr(p, "cards"), attacks = arr(p, "attacks"), blocked = arr(p, "blocked");
        int n = cards.size(), colW = Math.min(150, (ppw - 24) / Math.max(1, n)), x0 = cx - n * colW / 2;
        for (int k = 0; k < n; k++) {
            int x = x0 + k * colW + 4, y = bodyY;
            String f = cards.get(k).getAsString();
            int cw = 36, chh = Math.round(cw / Draw.CARD_RATIO);
            framedCard(ctx, f, x + (colW - 8 - cw) / 2, y, cw, chh, 1f);
            if (Ui.in(mx, my, x + (colW - 8 - cw) / 2, y, cw, chh)) tooltip = describeShort(f);
            int by = y + chh + 6;
            JsonArray al = k < attacks.size() ? attacks.get(k).getAsJsonArray() : new JsonArray();
            for (JsonElement a : al) {
                String an = a.getAsString();
                boolean bl = false;
                for (JsonElement be : blocked) {
                    JsonObject bo = obj(be);
                    if (bo != null && i(bo, "index", -1) == k && s(bo, "attack").equals(an)) bl = true;
                }
                int idx = k;
                if (bl) {
                    pill(ctx, x, by, colW - 8, 16, 0xFFE3E9F1);
                    textC(ctx, an, x + (colW - 8) / 2f, by + 4.5f, 0xFF9AA6B8, 0.75f);
                } else bigButton(ctx, x, by, colW - 8, 16, an, mx, my, RED, () -> {
                    JsonObject r = new JsonObject();
                    r.addProperty("index", idx);
                    r.addProperty("attack", an);
                    resolve(r);
                });
                by += 19;
                if (by > bodyY + bodyH) break;
            }
        }
    }

    // ================================================================== final

    private void result(DrawContext ctx, int mx, int my, double t) {
        int w = i(v, "winner", -1), meIdx = i(v, "meIndex", 0);
        boolean win = w == meIdx, draw = w == 3 || w == -1;
        double age = t - changedAt;
        // el fondo se oscurece poco a poco, el panel entra con escala y rebote y el título cae un poco después
        float veil = DuelFx.outCubic(age / 0.6);
        ctx.fill(0, 0, W, H, withAlpha(win ? 0xFF1A1405 : 0xFF0E1E36, 0.6f * veil));
        if (!win && !draw) ctx.fillGradient(0, 0, W, H / 3, withAlpha(RED, 0.25f * veil), withAlpha(RED, 0f));
        areas.add(new Area(0, 0, W, H, () -> {}));
        float in = DuelFx.outBack((age - 0.1) / 0.5);
        float tin = DuelFx.outElastic((age - 0.35) / 0.9);
        if (win) Draw.rays(ctx, W / 2f, H / 2f - 30, Math.max(W, H), t * 25, 0xFFFFE08A, 0.18f * veil, 14);
        if (win && !confettiDone && age > 0.3) { // confeti dorado, azul y blanco
            confettiDone = true;
            for (int k = 0; k < 4; k++) {
                float bx = W * (0.2f + 0.2f * k), byy = H * 0.3f;
                burst(confetti, bx, byy, GOLD, 22, 3.2f);
                burst(confetti, bx, byy, BLUE, 12, 2.6f);
                burst(confetti, bx, byy, GREEN, 8, 2.2f);
            }
        }
        int pw = Math.min(W - 24, 270), ph = 170, px = W / 2 - pw / 2, py = H / 2 - ph / 2;
        MatrixStack rm = ctx.getMatrices();
        rm.push();
        rm.translate(W / 2f, H / 2f, 0);
        float psc = Math.max(0.05f, 0.6f + 0.4f * in);
        rm.scale(psc, psc, 1);
        rm.translate(-W / 2f, -H / 2f, 0);
        int accent = win ? GOLD : draw ? SOFT : RED;
        float pal = Math.min(1, (float) age / 0.2f);
        if (win) Draw.glow(ctx, W / 2f, H / 2f, pw + 30, ph + 30, GOLD, 0.35f * pal);
        panel(ctx, px, py, pw, ph, withAlpha(0xFFFFFFFF, pal));
        Ui.roundedGradient(ctx, px + 1, py + 1, pw - 2, 44, 3, withAlpha(accent, 0.28f * pal), withAlpha(accent, 0f));
        DuelFx.hGradient(ctx, px + 3, py, px + pw - 3, py + 3, Ui.brighten(accent, 50), Ui.darken(accent, 30));
        String title = ui(draw ? "draw" : win ? "victory" : "defeat").toUpperCase();
        float tsc = Math.min(2.4f, (pw - 20f) / Math.max(1, textRenderer.getWidth(title))) * Math.max(0.1f, 0.5f + 0.5f * tin);
        DuelFx.outlinedC(ctx, textRenderer, title, W / 2f, py + 20 - 4 * tsc, accent == SOFT ? INK : accent, withAlpha(Ui.darken(accent, 90), 0.35f), tsc);
        String subT = ui(draw ? "result_draw_sub" : win ? "result_win_sub" : "result_lose_sub");
        float ssc = Math.min(0.8f, (pw - 16f) / Math.max(1, textRenderer.getWidth(subT)));
        textC(ctx, subT, W / 2f, py + 34, withAlpha(SOFT, DuelFx.prog(t, changedAt + 0.4, 0.3)), ssc);
        // resumen: turnos y premios de cada uno, con el color de cada jugador
        float stA = DuelFx.prog(t, changedAt + 0.55, 0.35);
        if (stA > 0) {
            int myTaken = 6 - i(side("me"), "prizes", 6), oppTaken = 6 - i(side("opp"), "prizes", 6);
            String s1 = ui("result_turns", i(v, "turn", 0));
            String s2 = ui("result_prizes_you", myTaken), s3 = ui("result_prizes_opp", oppTaken);
            int sy0 = py + 46 + Math.round((1 - DuelFx.outCubic(stA)) * 6);
            int cw3 = (pw - 24) / 3;
            for (int k = 0; k < 3; k++) {
                int bx = px + 12 + k * cw3, col = k == 0 ? SOFT : k == 1 ? BLUE : RED;
                Ui.rounded(ctx, bx + 2, sy0, cw3 - 4, 16, 6, withAlpha(k == 0 ? PANEL2 : DuelPalette.sideSoft(k == 1), stA));
                String st = k == 0 ? s1 : k == 1 ? s2 : s3;
                float sc3 = Math.min(0.7f, (cw3 - 10f) / Math.max(1, textRenderer.getWidth(st)));
                textC(ctx, st, bx + cw3 / 2f, sy0 + 8 - 4 * sc3, withAlpha(Ui.darken(col, 25), stA), sc3);
            }
        }
        int ly = py + 70;
        int rd = i(v, "ratingDelta", 0);
        if (b(v, "ranked") && !draw) {
            String rs = ui("rating_change", (rd >= 0 ? "+" : "") + rd);
            int rw = tw(rs, 1f) + 18;
            pill(ctx, W / 2 - rw / 2, ly, rw, 14, rd >= 0 ? 0xFFE3F8EC : 0xFFFFE3E8);
            textC(ctx, rs, W / 2f, ly + 3, rd >= 0 ? GREEN : RED, 1f);
            ly += 18;
        }
        int reward = i(v, "reward", 0);
        if (reward > 0) textC(ctx, ui("reward", reward), W / 2f, ly + 2, 0xFFB07A00, 0.9f);
        int penalty = i(v, "penalty", 0);
        if (penalty > 0) textC(ctx, ui("penalty", penalty), W / 2f, ly + 2, RED, 0.9f);
        int bw2 = 96, by = py + ph - 30;
        // los botones se dibujan dentro de la escala: el ratón se pasa al espacio escalado y sus áreas se llevan al de pantalla
        int a0 = areas.size();
        mx = Math.round(W / 2f + (mx - W / 2f) / psc);
        my = Math.round(H / 2f + (my - H / 2f) / psc);
        if (b(v, "ai")) {
            bigButton(ctx, W / 2 - bw2 - 5, by, bw2, 20, ui("rematch"), mx, my, BLUE, () -> {
                JsonObject a = new JsonObject();
                a.addProperty("a", "play");
                a.addProperty("mode", s(v, "mode").equals("ranked_ai") ? "ranked_ai" : "casual");
                a.addProperty("level", i(v, "level", 1));
                ClientPtcg.leagueAction(a);
                click();
            });
            bigButton(ctx, W / 2 + 5, by, bw2, 20, ui("close"), mx, my, GREEN, this::close);
        } else bigButton(ctx, W / 2 - bw2 / 2, by, bw2, 20, ui("close"), mx, my, GREEN, this::close);
        for (int k = a0; k < areas.size(); k++) {
            Area a = areas.get(k);
            int ax = Math.round(W / 2f + (a.x() - W / 2f) * psc), ay = Math.round(H / 2f + (a.y() - H / 2f) * psc);
            areas.set(k, new Area(ax, ay, Math.round(a.w() * psc), Math.round(a.h() * psc), a.action()));
        }
        rm.pop();
        // confeti por encima del velo y del panel
        rm.push();
        rm.translate(0, 0, 50);
        particles(ctx, confetti, t);
        rm.pop();
    }

    // ================================================================== entrada

    private boolean runAreas(double mouseX, double mouseY) {
        for (int k = areas.size() - 1; k >= 0; k--) {
            Area a = areas.get(k);
            if (Ui.in(mouseX, mouseY, a.x, a.y, a.w, a.h)) {
                a.action.run();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (v == null) return true;
        if (intro()) { // un clic salta la presentación
            introSkipped = true;
            bigCoinAt = -10;
            return true;
        }
        if (button == 1) {
            if (zoomKey == null) discardSide = null;
            selectedHand = -1;
            retreatMode = false;
            concedeArmed = false;
            srcPick = -1;
            srcTarget = null;
            zoomKey = null;
            cancelDrag();
            return true;
        }
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        boolean modal = (prompt() != null && !over() && !peek) || zoomKey != null || discardSide != null || over();
        // ampliación, decisiones y final: sus áreas tapan la mesa
        if (modal) { runAreas(mouseX, mouseY); return true; }
        // registro abierto: sus botones primero
        if (menuOpen) {
            int w = drawerW(), x = mx0 + 6;
            if (Ui.in(mouseX, mouseY, x, 24, w, H - 48)) { runAreas(mouseX, mouseY); return true; }
        }
        // mano: pulsar (clic = ampliar, arrastrar = jugar)
        if (hoverHand >= 0) {
            pressHand = hoverHand;
            pressX = mouseX;
            pressY = mouseY;
            return true;
        }
        // destinos iluminados (carta elegida o retirada) y botones del HUD
        if ((selectedHand >= 0 || retreatMode) && runAreas(mouseX, mouseY)) return true;
        for (String key : new String[]{"btn:pass", "btn:suggest", "btn:menu"}) {
            int[] r = rects.get(key);
            if (r != null && Ui.in(mouseX, mouseY, r[0], r[1], r[2], r[3])) return runAreas(mouseX, mouseY) || true;
        }
        // Pokémon en juego: clic = ampliar; tu Activo también se puede arrastrar a la Banca
        for (Map.Entry<String, int[]> e : rects.entrySet()) {
            String key = e.getKey();
            if (!isMonKey(key)) continue;
            int[] r = e.getValue();
            if (!Ui.in(mouseX, mouseY, r[0], r[1], r[2], r[3]) || monByKey(key) == null) continue;
            pressKey = key;
            pressActive = key.equals("me:1:0") && canAct();
            pressX = mouseX;
            pressY = mouseY;
            return true;
        }
        if (runAreas(mouseX, mouseY)) return true;
        selectedHand = -1;
        retreatMode = false;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        boolean canDragHand = pressHand >= 0 && canAct();
        if (button == 0 && (canDragHand || pressActive)) {
            if (!dragging && Math.hypot(mouseX - pressX, mouseY - pressY) > 5) {
                dragging = true;
                dragHand = canDragHand ? pressHand : -1;
                dragActive = !canDragHand && pressActive;
                selectedHand = -1;
                retreatMode = false;
                dragX = (float) mouseX;
                dragY = (float) mouseY;
                lastDragX = dragX;
                lastDragY = dragY;
                dragVx = dragVy = 0;
                Anim src = canDragHand && pressHand < handAnim.size() ? handAnim.get(pressHand) : null;
                dragAnim.rx.x = src == null ? 0 : src.rx.x;
                dragAnim.ry.x = src == null ? 0 : src.ry.x;
                dragAnim.a.x = 0;
                dragAnim.s.x = src == null ? 1f : src.s.x;
                dragAnim.rx.v = dragAnim.ry.v = dragAnim.a.v = dragAnim.s.v = 0;
                sound(TcgSounds.CARD_FLIP, 1.3f, 0.4f);
            }
            if (dragging) {
                dragX = (float) mouseX;
                dragY = (float) mouseY;
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && (pressHand >= 0 || pressKey != null)) {
            if (dragging) {
                if (canAct()) computeDrops();
                Drop d = dropAt(mouseX, mouseY);
                if (d != null) {
                    if (dragActive) retreat(i(d.target, "index", 0));
                    else play(dragHand, d.target);
                } else if (dragHand >= 0 && !Ui.in(mouseX, mouseY, mx0, handBaseY, mx1 - mx0, H)) {
                    toast(ui(dropsFor(dragHand).isEmpty() ? "no_target" : "drop_on_glow"), SOFT);
                }
            } else if (pressHand >= 0) {
                JsonArray h = handCards();
                if (pressHand < h.size()) openZoom("hand:" + pressHand, h.get(pressHand).getAsString());
            } else if (pressKey != null) {
                JsonObject mon = monByKey(pressKey);
                if (mon != null) openZoom(pressKey, s(mon, "f"));
            }
            cancelDrag();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (discardSide != null && zoomKey == null) {
            discardScroll -= verticalAmount * 24;
            return true;
        }
        if (menuOpen) {
            logScroll += verticalAmount > 0 ? 2 : -2;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (v != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE && zoomKey == null && discardSide != null) {
                discardSide = null;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE && (zoomKey != null || menuOpen || selectedHand >= 0 || retreatMode)) {
                zoomKey = null;
                menuOpen = false;
                selectedHand = -1;
                retreatMode = false;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_SPACE && canAct() && zoomKey == null) { act("pass"); return true; }
            if (keyCode == GLFW.GLFW_KEY_S && canAct()) { askSuggestion(); return true; }
            if (keyCode == GLFW.GLFW_KEY_L) { menuOpen = !menuOpen; return true; }
            if (keyCode == GLFW.GLFW_KEY_TAB && prompt() != null) { peek = !peek; return true; }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}

package dev.tcgshop.client.screen.grade;

import dev.masterball.api.card.CardInfo;
import dev.masterball.api.card.Rarity;
import dev.tcgshop.TcgSounds;
import dev.tcgshop.client.binder.Catalog;
import dev.tcgshop.client.binder.ClientCollection;
import dev.tcgshop.client.binder.Ui;
import dev.tcgshop.client.screen.Draw;
import dev.tcgshop.collection.BinderSlots;
import dev.tcgshop.grading.Grading;
import dev.tcgshop.network.GradePayloads;
import dev.tcgshop.pricing.PriceService;
import dev.tcgshop.shop.ShopServer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.HoveredTooltipPositioner;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.IntConsumer;

import static dev.tcgshop.client.screen.Draw.withAlpha;

/**
 * CobbleGrade · «Enviar cartas» (app del teléfono).
 * <ul>
 *   <li>Rejilla con las cartas gradeables del inventario y del Binder General (marca "B").</li>
 *   <li>Filtros: buscador (nombre, set o número), set (desplegable), familia de rareza, franja de valor, orden
 *       (valor / nombre / set / número, ascendente o descendente) y ocultar las cartas que ya tienes gradeadas.</li>
 *   <li>Selección múltiple "pintando": pulsa sobre una carta y arrastra; todas las que toques pasan al estado de la
 *       primera (elegida o no). Mayús+clic elige un rango. Ctrl+A / «Elegir visibles» y «Limpiar».</li>
 *   <li>Tira de cartas elegidas (clic para quitar), selector de servicio con la tarifa total en vivo de cada uno y
 *       valor esperado tras el gradeo (media de tiradas simuladas con las mismas probabilidades del servidor).</li>
 * </ul>
 * Se envía GradePayloads.SUBMIT con las casillas elegidas (inventario 0-35 o Binder 1000+) y el id de carta esperado en cada una.
 */
public final class GradeSendPanel {
    private static final int SURF = 0xF0171A26, SURF2 = 0xF01F2333, LINE = 0xFF2C3149, ACCENT = 0xFF1E8FA8;
    private static final int[] SERVICE_COL = {0xFF3FA66B, 0xFF3A8FD8, 0xFFE0892B};
    private static final long[] BAND_MIN = {0, 0, 1_000, 10_000, 100_000}, BAND_MAX = {Long.MAX_VALUE, 1_000, 10_000, 100_000, Long.MAX_VALUE};
    private static final String[] SORTS = {"value", "name", "set", "number"};
    private static final int ROW = 13, GAP = 3, EV_ROLLS = 400;

    /** Una carta gradeable (copia de vista: no modificar). */
    private record Entry(int slot, ItemStack stack, Identifier id, String key, String lname, String set, String lset, int number,
                         Rarity rarity, long value) {}

    private record Hit(int x, int y, int w, int h, IntConsumer action) {}

    private record CardRect(int x, int y, int w, int h, int slot, int idx) {}

    /** Casillas elegidas (en orden de selección) y la carta que había en cada una al elegirla. */
    public final Set<Integer> sel = new LinkedHashSet<>();
    private final Map<Integer, String> selKey = new HashMap<>();
    /** Servicio elegido (índice de Grading.Service). */
    public int service = 1;
    private final Runnable onSubmitted;

    // filtros
    private TextFieldWidget search;
    private String setFilter = "";
    private Rarity.Family family;
    private int band, sort;
    private boolean desc = true, hideOwnedGraded;
    private boolean setOpen;
    private double dropScroll;

    // datos (se reconstruyen sólo cuando cambia el inventario, el Binder o el día de mercado)
    private final List<Entry> all = new ArrayList<>();
    private final Map<Integer, Entry> bySlot = new HashMap<>();
    private final Set<String> ownedGraded = new HashSet<>();
    private final Map<String, String> setNames = new HashMap<>();
    private Map<String, Integer> setOrder;
    private long sig = Long.MIN_VALUE;
    private int version;
    private List<Entry> view = new ArrayList<>();
    private String viewKey = "";
    /** clave de carta -> {valor esperado, P(10), P(9+)}. */
    private final Map<String, double[]> evCache = new HashMap<>();
    private long evDay = Long.MIN_VALUE;
    private int evBudget;

    // entrada y geometría del último fotograma
    private final List<Hit> hits = new ArrayList<>(), dropHits = new ArrayList<>();
    private final List<CardRect> cards = new ArrayList<>();
    private int px, py, pw, ph, gx, gy, gw, gh, sbX, sbY, sbW, sbH, ddX, ddY, ddW, ddH, setBx, setBy, setBw;
    private double scroll;
    private Boolean paint;
    /** Casilla de la última carta pulsada (ancla de Mayús+clic); se busca en la vista actual al usarla. */
    private int anchorSlot = -1;
    private double lastMx, lastMy, limitFlash = -10, armedAt = -10;
    private List<Text> tip;

    public GradeSendPanel(Runnable onSubmitted) {
        this.onSubmitted = onSubmitted;
    }

    private static double now() { return System.nanoTime() / 1e9; }

    private static String compact(long v) {
        long a = Math.abs(v);
        String s = a >= 1_000_000 ? String.format(Locale.ROOT, "%.1fM", a / 1e6) : a >= 1000 ? String.format(Locale.ROOT, "%.1fk", a / 1e3) : String.valueOf(a);
        return (v < 0 ? "-" : "") + s;
    }

    private static void sound(float pitch) {
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.getSoundManager().play(PositionedSoundInstance.master(TcgSounds.CARD_SLIDE, pitch, 0.45f));
    }

    // ------------------------------------------------------------------ datos

    private void ensureSearch(TextRenderer tr) {
        if (search != null) return;
        search = new TextFieldWidget(tr, 0, 0, 100, 10, Text.translatable("tcgshop.grade.f.search"));
        search.setPlaceholder(Text.translatable("tcgshop.grade.f.search").formatted(Formatting.DARK_GRAY));
        search.setDrawsBackground(false);
        search.setMaxLength(48);
        search.setChangedListener(s -> scroll = 0);
    }

    private String setName(String set) {
        return setNames.computeIfAbsent(set, s -> I18n.hasTranslation("masterball.set." + s) ? I18n.translate("masterball.set." + s) : s);
    }

    private int setIndex(String set) {
        if (setOrder == null) {
            setOrder = new HashMap<>();
            List<Catalog.CardSet> sets = Catalog.sets();
            for (int i = 0; i < sets.size(); i++) setOrder.put(sets.get(i).id(), i);
        }
        return setOrder.getOrDefault(set, Integer.MAX_VALUE);
    }

    private static long signature(PlayerEntity p) {
        long s = PriceService.marketDay();
        for (int i = 0; i < 36; i++) {
            ItemStack st = p.getInventory().getStack(i);
            s = s * 31 + (st.isEmpty() ? 0 : System.identityHashCode(st.getItem()) * 64L + st.getCount() + Grading.grade(st) * 7919L);
        }
        s = s * 31 + ClientCollection.counts.hashCode();
        return s * 31 + System.identityHashCode(ClientCollection.counts);
    }

    private Entry entry(int slot, ItemStack st) {
        Identifier id = CardInfo.id(st);
        String set = CardInfo.setId(id);
        return new Entry(slot, st, id, id.toString(), st.getName().getString().toLowerCase(Locale.ROOT), set,
                setName(set).toLowerCase(Locale.ROOT), CardInfo.cardNumber(id), Rarity.of(st), ShopServer.priceOf(st.copyWithCount(1), 1.0));
    }

    private void refresh(PlayerEntity p) {
        long s = signature(p);
        if (s == sig) return;
        sig = s;
        all.clear();
        bySlot.clear();
        ownedGraded.clear();
        for (int i = 0; i < 36; i++) {
            ItemStack st = p.getInventory().getStack(i);
            if (!CardInfo.isCard(st)) continue;
            if (Grading.isGraded(st)) { ownedGraded.add(CardInfo.id(st).toString()); continue; }
            all.add(entry(i, st));
        }
        List<String> ids = BinderSlots.ids(ClientCollection.counts);
        for (int k = 0; k < ids.size(); k++) {
            ItemStack st = BinderSlots.stackFor(ids.get(k), ClientCollection.count(ids.get(k)));
            if (CardInfo.isCard(st) && !Grading.isGraded(st)) all.add(entry(BinderSlots.BASE + k, st));
        }
        for (Entry e : all) bySlot.put(e.slot, e);
        // si una casilla elegida ya no tiene la misma carta (se movió o se vendió), se quita de la selección
        sel.removeIf(slot -> { Entry e = bySlot.get(slot); return e == null || !Objects.equals(selKey.get(slot), e.key); });
        selKey.keySet().retainAll(sel);
        version++;
    }

    private boolean filtersActive() {
        return !search.getText().isBlank() || !setFilter.isEmpty() || family != null || band != 0 || hideOwnedGraded;
    }

    private void resetFilters() {
        search.setText("");
        setFilter = "";
        family = null;
        band = 0;
        hideOwnedGraded = false;
        scroll = 0;
    }

    private List<Entry> view() {
        String q = search.getText().trim().toLowerCase(Locale.ROOT);
        String key = version + "|" + q + "|" + setFilter + "|" + family + "|" + band + "|" + sort + "|" + desc + "|" + hideOwnedGraded;
        if (key.equals(viewKey)) return view;
        viewKey = key;
        String qNum = q.startsWith("#") ? q.substring(1) : q;
        List<Entry> out = new ArrayList<>();
        for (Entry e : all) {
            if (!setFilter.isEmpty() && !setFilter.equals(e.set)) continue;
            if (family != null && e.rarity.family != family) continue;
            if (band != 0 && (e.value < BAND_MIN[band] || e.value >= BAND_MAX[band])) continue;
            if (hideOwnedGraded && ownedGraded.contains(e.key)) continue;
            if (!q.isEmpty() && !e.lname.contains(q) && !e.lset.contains(q) && !e.set.contains(q)
                    && !(e.number >= 0 && String.valueOf(e.number).equals(qNum))) continue;
            out.add(e);
        }
        Comparator<Entry> c = switch (sort) {
            case 1 -> Comparator.comparing(Entry::lname);
            case 2 -> Comparator.<Entry>comparingInt(e -> setIndex(e.set)).thenComparingInt(Entry::number);
            case 3 -> Comparator.<Entry>comparingInt(Entry::number).thenComparing(Entry::lname);
            default -> Comparator.comparingLong(Entry::value);
        };
        if (desc) c = c.reversed();
        out.sort(c.thenComparingInt(Entry::slot));
        view = out;
        return out;
    }

    /**
     * Valor esperado de la carta tras gradearla: se simulan EV_ROLLS tiradas con Grading.roll (las mismas reglas que
     * el servidor, incluida la antigüedad) y se promedia el precio de mercado de cada nota. Se calcula poco a poco
     * (unas pocas cartas por fotograma) y se guarda hasta que cambia el día de mercado.
     */
    private double[] ev(Entry e) {
        long day = PriceService.marketDay();
        if (day != evDay) { evCache.clear(); evDay = day; }
        double[] r = evCache.get(e.key);
        if (r != null || evBudget <= 0) return r;
        evBudget--;
        Random rnd = Random.create(e.key.hashCode() * 31L + 7);
        int[] hist = new int[101];
        for (int i = 0; i < EV_ROLLS; i++) hist[MathHelper.clamp(Grading.roll(rnd, e.id), 10, 100)]++;
        double ev = 0, p10 = 0, p9 = 0;
        ItemStack base = e.stack.copyWithCount(1);
        for (int g = 10; g <= 100; g++) {
            if (hist[g] == 0) continue;
            double f = hist[g] / (double) EV_ROLLS;
            ItemStack gs = base.copy();
            gs.set(Grading.GRADE, g);
            ev += ShopServer.priceOf(gs, 1.0) * f;
            if (g >= 100) p10 += f;
            if (g >= 90) p9 += f;
        }
        r = new double[]{ev, p10, p9};
        evCache.put(e.key, r);
        return r;
    }

    // ------------------------------------------------------------------ selección

    private void apply(int slot, boolean add) {
        if (add) {
            if (sel.contains(slot)) return;
            if (sel.size() >= Grading.MAX_PER_ORDER) { limitFlash = now(); return; }
            Entry e = bySlot.get(slot);
            if (e == null) return;
            sel.add(slot);
            selKey.put(slot, e.key);
        } else {
            sel.remove(slot);
            selKey.remove(slot);
        }
        armedAt = -10; // cambiar la selección desarma la confirmación
    }

    private void selectShown() {
        for (Entry e : view) {
            if (sel.size() >= Grading.MAX_PER_ORDER) { limitFlash = now(); break; }
            apply(e.slot, true);
        }
    }

    public void clear() {
        sel.clear();
        selKey.clear();
        armedAt = -10;
    }

    private void applyAt(double mx, double my) {
        if (paint == null || !Ui.in(mx, my, gx, gy, gw, gh)) return;
        for (CardRect r : cards) if (Ui.in(mx, my, r.x, r.y, r.w, r.h)) { apply(r.slot, paint); return; }
    }

    // ------------------------------------------------------------------ dibujo

    public void render(DrawContext ctx, int px, int py, int pw, int ph, int mx, int my, float delta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        TextRenderer tr = mc.textRenderer;
        this.px = px;
        this.py = py;
        this.pw = pw;
        this.ph = ph;
        hits.clear();
        dropHits.clear();
        cards.clear();
        tip = null;
        evBudget = 4;
        ensureSearch(tr);
        if (mc.player == null) return;
        refresh(mc.player);
        List<Entry> shown = view();

        int sideW = pw >= 420 ? 140 : pw >= 330 ? 122 : 108;
        int lw = pw - sideW - 6;
        int barH = filterBar(ctx, tr, px, py, lw, mx, my, shown);
        int stripH = 27;
        gx = px;
        gy = py + barH + 2;
        gw = lw;
        gh = Math.max(30, ph - barH - 2 - stripH - 3);
        grid(ctx, tr, shown, mx, my);
        strip(ctx, tr, px, gy + gh + 3, lw, stripH, mx, my, shown);
        side(ctx, tr, px + lw + 6, py, sideW, ph, mx, my);
        if (setOpen) dropdown(ctx, tr, mx, my);
        // el panel se dibuja dentro del recorte (scissor) del teléfono: el tooltip se le pasa a la pantalla para que
        // lo pinte después (renderWithTooltip), fuera del recorte, y no salga cortado por el borde del móvil
        if (tip != null && mc.currentScreen != null) {
            List<OrderedText> lines = new ArrayList<>(tip.size());
            for (Text t : tip) lines.add(t.asOrderedText());
            mc.currentScreen.setTooltip(lines, HoveredTooltipPositioner.INSTANCE, true);
        }
    }

    /** Coloca un control en la barra de filtros (fluye a la línea siguiente si no cabe). */
    private int fx, fyy, fx0, fw0;

    private int[] place(int w) {
        if (fx > fx0 && fx + w > fx0 + fw0) { fx = fx0; fyy += ROW + GAP; }
        int[] r = {fx, fyy};
        fx += w + GAP;
        return r;
    }

    private boolean chip(DrawContext ctx, int x, int y, int w, Text label, boolean selected, int accent, int mx, int my) {
        boolean hover = Ui.in(mx, my, x, y, w, ROW) && !setOpen;
        Ui.chip(ctx, x, y, w, ROW, label, selected, hover, accent, 0);
        return hover;
    }

    private int filterBar(DrawContext ctx, TextRenderer tr, int x, int y, int w, int mx, int my, List<Entry> shown) {
        fx0 = fx = x;
        fyy = y;
        fw0 = w;
        // conjuntos presentes (para el desplegable y las fichas de rareza)
        Text setLabel = setFilter.isEmpty() ? Text.translatable("tcgshop.grade.f.set_all") : Text.literal(setName(setFilter));
        int setW = Math.min(110, Math.max(54, Ui.chipWidth(setLabel) + 8));
        Text sortLabel = Text.translatable("tcgshop.grade.f.sort." + SORTS[sort]);
        int sortW = Math.max(52, Ui.chipWidth(sortLabel)), dirW = 13;
        int rest = setW + sortW + dirW + GAP * 3;
        int searchW = w - rest >= 90 ? w - rest : w;
        // buscador
        int[] p = place(searchW);
        sbX = p[0];
        sbY = p[1];
        sbW = searchW;
        sbH = ROW;
        boolean focused = search.isFocused();
        Ui.rounded(ctx, sbX, sbY, sbW, sbH, 5, focused ? 0xEE0E1018 : 0xAA0E1018);
        Ui.roundedOutline(ctx, sbX, sbY, sbW, sbH, 5, focused ? 0xFFFFFFFF : 0x66FFFFFF);
        ctx.drawText(tr, "⌕", sbX + 4, sbY + 3, 0xFF9A9FB8, false);
        search.setX(sbX + 13);
        search.setY(sbY + 3);
        search.setWidth(sbW - 16);
        search.render(ctx, mx, my, 0f);
        // set (desplegable)
        p = place(setW);
        setBx = ddX = p[0];
        setBy = p[1];
        setBw = setW;
        ddY = p[1] + ROW + 1;
        if (chip(ctx, p[0], p[1], setW, Text.empty().append(setLabel).append(" ▾"), !setFilter.isEmpty(), 0xFF5C6BC0, mx, my))
            tip = List.of(Text.translatable("tcgshop.grade.f.set_tip"));
        hits.add(new Hit(p[0], p[1], setW, ROW, b -> { setOpen = !setOpen; dropScroll = 0; }));
        // orden + sentido
        p = place(sortW);
        chip(ctx, p[0], p[1], sortW, sortLabel, false, 0xFF8E44AD, mx, my);
        hits.add(new Hit(p[0], p[1], sortW, ROW, b -> {
            sort = Math.floorMod(sort + (b == 1 ? -1 : 1), SORTS.length);
            desc = sort == 0; // valor: de más caro a más barato; el resto, A→Z / 1→N
            scroll = 0;
        }));
        p = place(dirW);
        if (chip(ctx, p[0], p[1], dirW, Text.literal(desc ? "▼" : "▲"), false, 0xFF8E44AD, mx, my))
            tip = List.of(Text.translatable(desc ? "tcgshop.grade.f.desc" : "tcgshop.grade.f.asc"));
        hits.add(new Hit(p[0], p[1], dirW, ROW, b -> { desc = !desc; scroll = 0; }));
        // familias de rareza presentes
        List<Rarity.Family> fams = new ArrayList<>();
        Set<Rarity.Family> present = new HashSet<>();
        for (Entry e : all) present.add(e.rarity.family);
        for (Rarity.Family f : Rarity.Family.values()) if (present.contains(f)) fams.add(f);
        Text allLbl = Text.translatable("tcgshop.grade.f.rarity_all");
        int chipsW = Ui.chipWidth(allLbl);
        for (Rarity.Family f : fams) chipsW += GAP + Ui.chipWidth(f.shortName()) + 6;
        fx = fx0;
        fyy += ROW + GAP;
        if (chipsW <= w) {
            int aw = Ui.chipWidth(allLbl);
            p = place(aw);
            chip(ctx, p[0], p[1], aw, allLbl, family == null, ACCENT, mx, my);
            hits.add(new Hit(p[0], p[1], aw, ROW, b -> { family = null; scroll = 0; }));
            for (Rarity.Family f : fams) {
                int cw = Ui.chipWidth(f.shortName()) + 6;
                p = place(cw);
                if (chip(ctx, p[0], p[1], cw, f.shortName(), family == f, f.color, mx, my)) tip = List.of(f.displayName());
                if (family != f) Ui.rounded(ctx, p[0] + 3, p[1] + 5, 3, 3, 1, f.color);
                hits.add(new Hit(p[0], p[1], cw, ROW, b -> { family = family == f ? null : f; scroll = 0; }));
            }
        } else {
            // estrecho: una sola ficha que va rotando (clic derecho: hacia atrás)
            Text lbl = family == null ? allLbl : family.shortName();
            int cw = Math.max(56, Ui.chipWidth(lbl) + 8);
            p = place(cw);
            chip(ctx, p[0], p[1], cw, Text.empty().append(lbl).append(" ⇅"), family != null, family == null ? ACCENT : family.color, mx, my);
            hits.add(new Hit(p[0], p[1], cw, ROW, b -> {
                if (fams.isEmpty()) return;
                int i = family == null ? -1 : fams.indexOf(family);
                i += b == 1 ? -1 : 1;
                family = i < 0 || i >= fams.size() ? (i < -1 ? fams.get(fams.size() - 1) : null) : fams.get(i);
                scroll = 0;
            }));
        }
        // franja de valor
        Text bandLbl = Text.translatable("tcgshop.grade.f.band." + band);
        int bw = Math.max(50, Ui.chipWidth(bandLbl));
        p = place(bw);
        if (chip(ctx, p[0], p[1], bw, bandLbl, band != 0, 0xFFD39B1A, mx, my)) tip = List.of(Text.translatable("tcgshop.grade.f.band_tip"));
        hits.add(new Hit(p[0], p[1], bw, ROW, b -> { band = Math.floorMod(band + (b == 1 ? -1 : 1), BAND_MIN.length); scroll = 0; }));
        // ocultar las que ya tienes gradeadas
        Text hideLbl = Text.translatable("tcgshop.grade.f.hide_graded");
        int hw = Ui.chipWidth(hideLbl) + 10;
        p = place(hw);
        if (chip(ctx, p[0], p[1], hw, Text.literal(hideOwnedGraded ? "✔ " : "").append(hideLbl), hideOwnedGraded, 0xFF2E9C5A, mx, my))
            tip = List.of(Text.translatable("tcgshop.grade.f.hide_graded_tip"));
        hits.add(new Hit(p[0], p[1], hw, ROW, b -> { hideOwnedGraded = !hideOwnedGraded; scroll = 0; }));
        if (filtersActive()) {
            p = place(13);
            if (chip(ctx, p[0], p[1], 13, Text.literal("✕"), false, 0xFFE53935, mx, my)) tip = List.of(Text.translatable("tcgshop.grade.f.reset"));
            hits.add(new Hit(p[0], p[1], 13, ROW, b -> resetFilters()));
        }
        return fyy + ROW - y;
    }

    private void grid(DrawContext ctx, TextRenderer tr, List<Entry> shown, int mx, int my) {
        Ui.roundGradient(ctx, gx, gy, gw, gh, SURF2, SURF);
        Ui.outline(ctx, gx, gy, gw, gh, LINE);
        if (all.isEmpty()) {
            paragraph(ctx, tr, Text.translatable("tcgshop.grade.f.no_cards"), gx + 8, gy + 8, gw - 16, Ui.MUTED);
            return;
        }
        if (shown.isEmpty()) {
            int y = paragraph(ctx, tr, Text.translatable("tcgshop.grade.f.none"), gx + 8, gy + 8, gw - 16, Ui.MUTED);
            int bw = Math.min(120, gw - 16);
            Ui.button(ctx, gx + 8, y + 4, bw, 16, Text.translatable("tcgshop.grade.f.reset"), mx, my, true, ACCENT);
            hits.add(new Hit(gx + 8, y + 4, bw, 16, b -> resetFilters()));
            return;
        }
        int cw = gw >= 260 ? 36 : 30, ch = Math.round(cw / Draw.CARD_RATIO), gap = 5, rowH = ch + 12;
        int cols = Math.max(1, (gw - 10 + gap) / (cw + gap)), rows = (shown.size() + cols - 1) / cols;
        int content = rows * rowH + 6;
        // auto-desplazamiento mientras pintas la selección por el borde
        if (paint != null && mx >= gx && mx < gx + gw) {
            if (my < gy + 6) scroll -= 4;
            else if (my > gy + gh - 6) scroll += 4;
        }
        scroll = MathHelper.clamp(scroll, 0, Math.max(0, content - gh));
        int ox = gx + (gw - (cols * (cw + gap) - gap)) / 2;
        Grading.Service cur = Grading.Service.of(service);
        double t = now();
        boolean full = sel.size() >= Grading.MAX_PER_ORDER;
        Entry hovered = null;
        ctx.enableScissor(gx + 1, gy + 1, gx + gw - 1, gy + gh - 1);
        for (int k = 0; k < shown.size(); k++) {
            Entry e = shown.get(k);
            int x = ox + (k % cols) * (cw + gap), y = gy + 5 + (k / cols) * rowH - (int) scroll;
            if (y + rowH < gy || y > gy + gh) continue;
            boolean on = sel.contains(e.slot), hover = Ui.in(mx, my, x, y, cw, ch) && Ui.in(mx, my, gx, gy, gw, gh) && !setOpen;
            float alpha = on || hover ? 1f : full ? 0.45f : 0.8f;
            if (on) Draw.glow(ctx, x + cw / 2f, y + ch / 2f, cw, ch, Ui.GOLD, 0.3f);
            ctx.fill(x + 2, y + 3, x + cw + 2, y + ch + 3, 0x55000000);
            Draw.cardFace(ctx, CardInfo.texture(e.id), e.stack, x, y, cw, ch, alpha);
            if (hover && e.rarity.tier > 0) Draw.holo(ctx, x, y, cw, ch, e.rarity.tier, e.rarity.color, t, mx);
            ctx.fill(x, y + ch, x + cw, y + ch + 1, e.rarity.family.color); // franja con el color de la familia de rareza
            if (e.stack.getCount() > 1) {
                String c = "x" + e.stack.getCount();
                Ui.round(ctx, x + 1, y + ch - 10, tr.getWidth(c) + 4, 9, 0xCC000000);
                ctx.drawText(tr, c, x + 3, y + ch - 9, 0xFFFFFFFF, false);
            }
            if (BinderSlots.isBinder(e.slot)) {
                Ui.round(ctx, x + 1, y + 1, 9, 9, 0xEE3A6FD8);
                ctx.drawText(tr, "B", x + 3, y + 2, 0xFFFFFFFF, false);
            }
            // pista de valor esperado: punto verde si gradearla sale a cuenta con este servicio, rojo si no
            double[] ev = ev(e);
            if (ev != null) {
                long gain = Math.round(ev[0]) - e.value - cur.fee(e.value);
                Ui.round(ctx, x + cw - 6, y + ch - 6, 4, 4, gain > 0 ? 0xFF43D96B : 0xFFE5534B);
            }
            if (on) {
                Ui.outline(ctx, x - 1, y - 1, cw + 2, ch + 2, Ui.GOLD);
                Ui.outline(ctx, x - 2, y - 2, cw + 4, ch + 4, withAlpha(Ui.GOLD, 0.45f));
                Ui.round(ctx, x + cw - 11, y + 2, 9, 9, 0xFF2E9C5A);
                ctx.drawText(tr, "✔", x + cw - 10, y + 3, 0xFFFFFFFF, false);
            } else if (hover) Ui.outline(ctx, x - 1, y - 1, cw + 2, ch + 2, 0xFFFFFFFF);
            String pr = compact(e.value);
            float s = Math.min(1f, (cw + 4f) / Math.max(1, tr.getWidth(pr)));
            var m = ctx.getMatrices();
            m.push();
            m.translate(x + cw / 2f, y + ch + 3, 0);
            m.scale(s, s, 1);
            ctx.drawText(tr, pr, -tr.getWidth(pr) / 2, 0, on ? Ui.GOLD : 0xFFE8D9A8, true);
            m.pop();
            if (hover) hovered = e;
            int ry = Math.max(y, gy), rh = Math.min(y + ch, gy + gh) - ry;
            if (rh > 0) cards.add(new CardRect(x, ry, cw, rh, e.slot, k));
        }
        ctx.disableScissor();
        if (content > gh) {
            int bh = Math.max(12, Math.round(gh * gh / (float) content));
            int by = gy + Math.round((float) (scroll / Math.max(1, content - gh)) * (gh - bh));
            Ui.round(ctx, gx + gw - 4, by, 3, bh, 0x88FFFFFF);
        }
        if (paint != null) applyAt(mx, my); // también si la rejilla se desplaza bajo el ratón quieto
        if (hovered != null && paint == null) tip = cardTip(hovered, cur);
    }

    private List<Text> cardTip(Entry e, Grading.Service cur) {
        List<Text> l = new ArrayList<>();
        l.add(e.stack.getName());
        l.add(Text.literal(setName(e.set) + (e.number >= 0 ? " · #" + e.number : "")).formatted(Formatting.GRAY));
        l.add(e.rarity.family.displayName().copy().withColor(e.rarity.family.color & 0xFFFFFF));
        l.add(Text.translatable("tcgshop.grade.f.tip_value", PriceService.format(e.value)).formatted(Formatting.GOLD));
        l.add(Text.translatable("tcgshop.grade.f.tip_fee", Text.translatable("tcgshop.grade.service." + cur.key),
                PriceService.format(cur.fee(e.value))).formatted(Formatting.GRAY));
        double[] ev = evCache.get(e.key);
        if (ev != null) {
            long gain = Math.round(ev[0]) - e.value - cur.fee(e.value);
            l.add(Text.translatable("tcgshop.grade.f.tip_ev", PriceService.format(Math.round(ev[0]))).formatted(Formatting.AQUA));
            l.add(Text.translatable("tcgshop.grade.f.tip_odds", String.format(Locale.ROOT, "%.1f%%", ev[1] * 100),
                    String.format(Locale.ROOT, "%.1f%%", ev[2] * 100)).formatted(Formatting.DARK_AQUA));
            l.add(Text.translatable("tcgshop.grade.f.tip_gain", (gain >= 0 ? "+" : "") + PriceService.format(gain))
                    .formatted(gain >= 0 ? Formatting.GREEN : Formatting.RED));
        }
        if (BinderSlots.isBinder(e.slot)) l.add(Text.translatable("tcgshop.grade.f.tip_binder").formatted(Formatting.BLUE));
        if (ownedGraded.contains(e.key)) l.add(Text.translatable("tcgshop.grade.f.tip_owned").formatted(Formatting.YELLOW));
        l.add(Text.translatable("tcgshop.grade.f.tip_help").formatted(Formatting.DARK_GRAY));
        return l;
    }

    /** Tira inferior: contador, miniaturas de lo elegido (clic = quitar), «Elegir visibles» y «Limpiar». */
    private void strip(DrawContext ctx, TextRenderer tr, int x, int y, int w, int h, int mx, int my, List<Entry> shown) {
        Ui.roundGradient(ctx, x, y, w, h, SURF2, SURF);
        Ui.outline(ctx, x, y, w, h, LINE);
        boolean flash = now() - limitFlash < 0.6;
        String cnt = sel.size() + "/" + Grading.MAX_PER_ORDER;
        int cntCol = flash ? ((int) (now() * 10) % 2 == 0 ? 0xFFFF5A5A : 0xFFFFFFFF) : sel.size() >= Grading.MAX_PER_ORDER ? 0xFFFF9F43 : Ui.GOLD;
        ctx.drawText(tr, Text.literal(cnt).formatted(Formatting.BOLD), x + 6, y + 5, cntCol, true);
        ctx.drawText(tr, Text.translatable("tcgshop.grade.f.cards"), x + 6, y + 15, 0xFF8A8FA8, false);
        int labelW = Math.max(tr.getWidth(cnt) + 4, tr.getWidth(Text.translatable("tcgshop.grade.f.cards"))) + 10;
        Text allT = Text.translatable("tcgshop.grade.f.select_shown", shown.size());
        Text clrT = Text.translatable("tcgshop.grade.f.clear");
        int bh = 16, clrW = Ui.chipWidth(clrT) + 6, allW = Math.min(Ui.chipWidth(allT) + 6, Math.max(40, w / 3));
        int bx2 = x + w - 5 - clrW, bx1 = bx2 - 4 - allW, by = y + (h - bh) / 2;
        boolean canAll = !shown.isEmpty() && sel.size() < Grading.MAX_PER_ORDER;
        if (Ui.button(ctx, bx1, by, allW, bh, allT, mx, my, canAll, ACCENT) && !setOpen) tip = List.of(Text.translatable("tcgshop.grade.f.select_shown_tip"));
        if (canAll) hits.add(new Hit(bx1, by, allW, bh, b -> selectShown()));
        Ui.button(ctx, bx2, by, clrW, bh, clrT, mx, my, !sel.isEmpty(), 0xFF8A3A3A);
        if (!sel.isEmpty()) hits.add(new Hit(bx2, by, clrW, bh, b -> clear()));
        // miniaturas
        int tw = 13, th = Math.round(tw / Draw.CARD_RATIO), step = tw + 2;
        int tx = x + labelW, avail = bx1 - 6 - tx, fit = Math.max(0, avail / step);
        if (sel.isEmpty()) {
            Ui.text(ctx, Text.translatable("tcgshop.grade.f.strip_empty"), tx, y, Math.max(10, avail), h, 0xFF6F748C, false);
            return;
        }
        List<Integer> list = new ArrayList<>(sel);
        int showN = list.size() > fit ? Math.max(0, fit - 1) : list.size();
        int ty = y + (h - th) / 2;
        for (int i = 0; i < showN; i++) {
            Entry e = bySlot.get(list.get(i));
            if (e == null) continue;
            int cx = tx + i * step;
            boolean hover = Ui.in(mx, my, cx, ty, tw, th) && !setOpen;
            Draw.cardFace(ctx, CardInfo.texture(e.id), e.stack, cx, ty - (hover ? 1 : 0), tw, th, 1f);
            if (hover) {
                Ui.outline(ctx, cx - 1, ty - 2, tw + 2, th + 2, 0xFFFF6B6B);
                tip = List.of(e.stack.getName(), Text.translatable("tcgshop.grade.f.strip_remove").formatted(Formatting.GRAY));
            }
            int slot = e.slot;
            hits.add(new Hit(cx, ty, tw, th, b -> apply(slot, false)));
        }
        if (showN < list.size()) {
            String more = "+" + (list.size() - showN);
            ctx.drawText(tr, more, tx + showN * step + 2, y + (h - 8) / 2, 0xFFDDDDDD, false);
        }
    }

    /** Panel lateral: servicios con la tarifa total en vivo, resumen de valor y botón de enviar (con confirmación). */
    private void side(DrawContext ctx, TextRenderer tr, int x, int y, int w, int h, int mx, int my) {
        Ui.roundGradient(ctx, x, y, w, h, SURF2, SURF);
        Ui.outline(ctx, x, y, w, h, LINE);
        ctx.drawText(tr, Text.translatable("tcgshop.grade.service").formatted(Formatting.BOLD), x + 6, y + 5, 0xFFFFFFFF, false);
        // totales de la selección
        long raw = 0, evSum = 0;
        boolean evDone = true;
        Grading.Service[] svc = Grading.Service.values();
        long[] fees = new long[svc.length];
        for (int slot : sel) {
            Entry e = bySlot.get(slot);
            if (e == null) continue;
            raw += e.value;
            for (int i = 0; i < svc.length; i++) fees[i] += svc[i].fee(e.value);
            double[] ev = ev(e);
            if (ev == null) evDone = false;
            else evSum += Math.round(ev[0]);
        }
        int rowH = h >= 200 ? 28 : 22;
        for (int i = 0; i < svc.length; i++) {
            Grading.Service sv = svc[i];
            int col = SERVICE_COL[i % SERVICE_COL.length];
            int by = y + 16 + i * (rowH + 3);
            boolean on = service == i, hover = Ui.in(mx, my, x + 4, by, w - 8, rowH) && !setOpen;
            Ui.rounded(ctx, x + 4, by, w - 8, rowH, 5, on ? withAlpha(col, 0.38f) : hover ? 0x33FFFFFF : 0x22000000);
            Ui.rounded(ctx, x + 6, by + 3, 2, rowH - 6, 1, col);
            if (on) Ui.roundedOutline(ctx, x + 4, by, w - 8, rowH, 5, Ui.brighten(col, 70));
            String name = Ui.ellipsis(Text.translatable("tcgshop.grade.service." + sv.key).getString(), w - 34);
            ctx.drawText(tr, Text.literal(name).formatted(Formatting.BOLD), x + 11, by + 3, 0xFFFFFFFF, false);
            // radio
            Ui.rounded(ctx, x + w - 15, by + 3, 8, 8, 4, 0xFF0E1018);
            if (on) Ui.rounded(ctx, x + w - 13, by + 5, 4, 4, 2, Ui.brighten(col, 60));
            ctx.drawText(tr, dev.tcgshop.client.ClientGrading.duration(sv.ticks), x + 11, by + rowH - 10, 0xFFB8D8E8, false);
            String fee = sel.isEmpty() ? compact(sv.baseFee) + "+" + Math.round(sv.pct * 100) + "%" : compact(fees[i]);
            ctx.drawText(tr, fee, x + w - 8 - tr.getWidth(fee), by + rowH - 10, Ui.GOLD, false);
            if (hover) tip = List.of(Text.translatable("tcgshop.grade.service." + sv.key).formatted(Formatting.BOLD),
                    Text.translatable("tcgshop.grade.f.svc_time", dev.tcgshop.client.ClientGrading.duration(sv.ticks)).formatted(Formatting.GRAY),
                    Text.translatable("tcgshop.grade.f.svc_fee", PriceService.format(sv.baseFee), Math.round(sv.pct * 100)).formatted(Formatting.GRAY),
                    Text.translatable("tcgshop.grade.f.svc_total", PriceService.format(fees[i])).formatted(Formatting.GOLD));
            int fi = i;
            hits.add(new Hit(x + 4, by, w - 8, rowH, b -> { service = fi; armedAt = -10; }));
        }
        int ly = y + 16 + svc.length * (rowH + 3) + 2;
        ctx.fill(x + 6, ly, x + w - 6, ly + 1, 0x33FFFFFF);
        ly += 4;
        int bh = 18, bY = y + h - bh - 6;
        long fee = fees[Math.floorMod(service, svc.length)];
        // cartas n/20 con barra
        if (ly + 14 <= bY - 2) {
            statLine(ctx, tr, x, ly, w, Text.translatable("tcgshop.grade.f.stat_cards"), sel.size() + " / " + Grading.MAX_PER_ORDER,
                    sel.size() >= Grading.MAX_PER_ORDER ? 0xFFFF9F43 : 0xFFFFFFFF);
            Ui.progress(ctx, x + 6, ly + 10, w - 12, 4, sel.size() / (float) Grading.MAX_PER_ORDER, ACCENT);
            ly += 17;
        }
        Object[][] lines = {
                {"tcgshop.grade.f.stat_value", PriceService.format(raw), 0xFFE8D9A8, null},
                {"tcgshop.grade.f.stat_fee", PriceService.format(fee), Ui.GOLD, null},
                {"tcgshop.grade.f.stat_ev", sel.isEmpty() ? "—" : evDone ? "~" + PriceService.format(evSum) : "…", 0xFF6BE0F5, "tcgshop.grade.f.stat_ev_tip"},
                {"tcgshop.grade.f.stat_gain", sel.isEmpty() || !evDone ? "—" : (evSum - raw - fee >= 0 ? "+" : "") + PriceService.format(evSum - raw - fee),
                        sel.isEmpty() || !evDone ? 0xFF9A9FB8 : evSum - raw - fee >= 0 ? 0xFF6BE07B : 0xFFFF6B6B, "tcgshop.grade.f.stat_gain_tip"}};
        for (Object[] l : lines) {
            if (ly + 10 > bY - 4) break;
            statLine(ctx, tr, x, ly, w, Text.translatable((String) l[0]), (String) l[1], (int) l[2]);
            if (l[3] != null && Ui.in(mx, my, x + 4, ly - 1, w - 8, 10) && !setOpen) tip = List.of(Text.translatable((String) l[3]));
            ly += 11;
        }
        if (ly + 22 <= bY - 4) {
            for (var line : tr.wrapLines(Text.translatable("tcgshop.grade.f.send_note"), w - 12)) {
                if (ly + 9 > bY - 4) break;
                ctx.drawText(tr, line, x + 6, ly, 0xFF6F748C, false);
                ly += 9;
            }
        }
        // enviar: primer clic arma, segundo confirma (4 s)
        boolean can = !sel.isEmpty();
        boolean armed = can && now() - armedAt < 4;
        Text label = armed ? Text.translatable("tcgshop.grade.f.confirm", compact(fee)) : Text.translatable("tcgshop.grade.send");
        Ui.button(ctx, x + 6, bY, w - 12, bh, label, mx, my, can, armed ? 0xFF3C8A4A : ACCENT);
        if (can) hits.add(new Hit(x + 6, bY, w - 12, bh, b -> {
            if (now() - armedAt >= 4) { armedAt = now(); return; }
            // con cada casilla va la carta que el jugador ha confirmado: si al llegar al servidor hay otra (el Binder
            // se ha reordenado o el hueco ha cambiado), el servidor se la salta en vez de gradear otra carta
            List<Integer> slots = new ArrayList<>(sel);
            List<String> expected = new ArrayList<>(slots.size());
            for (int sl : slots) expected.add(selKey.getOrDefault(sl, ""));
            ClientPlayNetworking.send(new GradePayloads.Action(GradePayloads.SUBMIT, service, slots, expected, 0));
            clear();
            onSubmitted.run();
        }));
    }

    private void statLine(DrawContext ctx, TextRenderer tr, int x, int y, int w, Text label, String value, int color) {
        int vw = tr.getWidth(value);
        ctx.drawText(tr, Ui.ellipsis(label.getString(), w - 16 - vw), x + 6, y, 0xFF9A9FB8, false);
        ctx.drawText(tr, value, x + w - 6 - vw, y, color, false);
    }

    /** Desplegable de sets (sólo los que tienes), encima de todo. */
    private void dropdown(DrawContext ctx, TextRenderer tr, int mx, int my) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        List<String> sets = new ArrayList<>();
        for (Entry e : all) if (counts.merge(e.set, 1, Integer::sum) == 1) sets.add(e.set);
        sets.sort(Comparator.<String>comparingInt(this::setIndex).thenComparing(Comparator.naturalOrder()));
        int rowH = 11, n = sets.size() + 1;
        ddW = Math.min(gw, 170);
        ddX = Math.min(ddX, gx + gw - ddW);
        int maxRows = Math.max(3, Math.min(n, (py + ph - ddY - 4) / rowH));
        ddH = maxRows * rowH + 4;
        dropScroll = MathHelper.clamp(dropScroll, 0, Math.max(0, n * rowH - (ddH - 4)));
        var m = ctx.getMatrices();
        m.push();
        m.translate(0, 0, 200);
        Ui.rounded(ctx, ddX + 2, ddY + 3, ddW, ddH, 5, 0x66000000);
        Ui.roundedGradient(ctx, ddX, ddY, ddW, ddH, 5, 0xFA23283A, 0xFA161A26);
        Ui.roundedOutline(ctx, ddX, ddY, ddW, ddH, 5, 0xFF5C6BC0);
        ctx.enableScissor(ddX + 1, ddY + 2, ddX + ddW - 1, ddY + ddH - 2);
        for (int i = 0; i < n; i++) {
            int ry = ddY + 2 + i * rowH - (int) dropScroll;
            if (ry + rowH < ddY || ry > ddY + ddH) continue;
            String set = i == 0 ? "" : sets.get(i - 1);
            boolean on = setFilter.equals(set), hover = Ui.in(mx, my, ddX + 2, ry, ddW - 4, rowH) && Ui.in(mx, my, ddX, ddY, ddW, ddH);
            if (on || hover) Ui.rounded(ctx, ddX + 2, ry, ddW - 4, rowH, 3, on ? 0x665C6BC0 : 0x33FFFFFF);
            String c = String.valueOf(i == 0 ? all.size() : counts.get(set));
            String name = i == 0 ? Text.translatable("tcgshop.grade.f.set_all").getString() : setName(set);
            ctx.drawText(tr, Ui.ellipsis(name, ddW - 14 - tr.getWidth(c)), ddX + 5, ry + 2, on ? Ui.GOLD : 0xFFE8EAF2, false);
            ctx.drawText(tr, c, ddX + ddW - 5 - tr.getWidth(c), ry + 2, 0xFF8A8FA8, false);
            int top = Math.max(ry, ddY + 2), bot = Math.min(ry + rowH, ddY + ddH - 2);
            if (bot > top) dropHits.add(new Hit(ddX + 2, top, ddW - 4, bot - top, b -> { setFilter = set; setOpen = false; scroll = 0; }));
        }
        ctx.disableScissor();
        m.pop();
        tip = null; // el desplegable tapa lo de debajo
    }

    private static int paragraph(DrawContext ctx, TextRenderer tr, Text text, int x, int y, int w, int color) {
        for (var line : tr.wrapLines(text, w)) {
            ctx.drawText(tr, line, x, y, color, false);
            y += 10;
        }
        return y;
    }

    // ------------------------------------------------------------------ entrada

    /** Clic. Devuelve true si el panel lo ha usado (cualquier clic dentro del panel cuenta como usado). */
    public boolean mouseClicked(double mx, double my, int button) {
        if (search == null) return false;
        if (setOpen) {
            if (Ui.in(mx, my, ddX, ddY, ddW, ddH)) {
                for (int i = dropHits.size() - 1; i >= 0; i--) {
                    Hit h = dropHits.get(i);
                    if (Ui.in(mx, my, h.x, h.y, h.w, h.h)) { h.action.accept(button); sound(1.3f); break; }
                }
                return true;
            }
            setOpen = false;
            if (!Ui.in(mx, my, px, py, pw, ph)) return false;
            // clic sobre el propio botón del set: sólo cerrar
            if (Ui.in(mx, my, setBx, setBy, setBw, ROW)) return true;
        }
        if (Ui.in(mx, my, sbX, sbY, sbW, sbH)) {
            search.setFocused(true);
            if (button == 1) search.setText("");
            else search.mouseClicked(mx, my, button);
            return true;
        }
        search.setFocused(false);
        if (!Ui.in(mx, my, px, py, pw, ph)) return false;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (Ui.in(mx, my, h.x, h.y, h.w, h.h)) {
                h.action.accept(button);
                sound(1.2f);
                return true;
            }
        }
        for (CardRect r : cards) {
            if (!Ui.in(mx, my, r.x, r.y, r.w, r.h)) continue;
            int anchorIdx = anchorIndex();
            if (button == 0 && Screen.hasShiftDown() && anchorIdx >= 0) {
                // Mayús+clic: elige todo el rango desde la última carta pulsada (su posición en la vista actual,
                // que puede haber cambiado con los filtros o el orden)
                int a = Math.min(anchorIdx, r.idx), b = Math.max(anchorIdx, r.idx);
                for (int k = a; k <= b && k < view.size(); k++) apply(view.get(k).slot, true);
            } else if (button == 0) {
                paint = !sel.contains(r.slot);
                apply(r.slot, paint);
                anchorSlot = r.slot;
                lastMx = mx;
                lastMy = my;
            } else if (button == 1) {
                apply(r.slot, false);
            }
            sound(sel.contains(r.slot) ? 1.25f : 0.9f);
            return true;
        }
        return true;
    }

    /** Posición del ancla en la vista actual, o -1 si ya no está (filtrada, vendida o movida). */
    private int anchorIndex() {
        if (anchorSlot < 0) return -1;
        Entry e = bySlot.get(anchorSlot);
        if (e == null) return -1;
        for (int k = 0; k < view.size(); k++) if (view.get(k) == e) return k;
        return -1;
    }

    /** Arrastrar con el botón pulsado: va "pintando" la selección por todas las cartas que toca el ratón. */
    public boolean mouseDragged(double mx, double my) {
        if (paint == null) return false;
        double dx = mx - lastMx, dy = my - lastMy;
        int steps = Math.max(1, (int) Math.ceil(Math.sqrt(dx * dx + dy * dy) / 4));
        for (int i = 1; i <= steps; i++) applyAt(lastMx + dx * i / steps, lastMy + dy * i / steps);
        lastMx = mx;
        lastMy = my;
        return true;
    }

    public void mouseReleased() { paint = null; }

    public void scroll(double mx, double my, double v) {
        if (setOpen && Ui.in(mx, my, ddX, ddY, ddW, ddH)) dropScroll -= v * 22;
        else scroll -= v * 28;
    }

    public boolean charTyped(char chr, int modifiers) {
        return search != null && search.isFocused() && (search.charTyped(chr, modifiers) || true);
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (search == null) return false;
        if (search.isFocused()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_ENTER) { search.setFocused(false); return true; }
            search.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && setOpen) { setOpen = false; return true; }
        if (keyCode == GLFW.GLFW_KEY_A && Screen.hasControlDown()) { selectShown(); return true; }
        return false;
    }

    /** Se llama al salir de la pestaña / app: quita el foco del buscador y cierra el desplegable. */
    public void blur() {
        if (search != null) search.setFocused(false);
        setOpen = false;
        paint = null;
    }
}

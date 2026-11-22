package dev.tcgshop.client.screen;

import dev.masterball.api.card.Rarity;
import dev.tcgshop.client.binder.Catalog;
import dev.tcgshop.client.binder.ClientCollection;
import dev.tcgshop.client.binder.Ui;
import dev.tcgshop.network.UiPayloads;
import dev.tcgshop.pricing.PriceService;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Mercado del Binder: vende varias cartas a la vez directamente desde tu Binder General (venta rápida), ofrece una
 * carta a otro jugador con tu precio, marca favoritas (protegidas: nunca se venden por error), filtra por expansión,
 * rareza, valor y cantidad, ordena, y mira el historial de operaciones y el valor total de tu colección.
 */
public class BinderMarketScreen extends Screen {
    private NbtCompound data;
    private final Set<String> fav = new HashSet<>();
    private final Set<String> sel = new HashSet<>();
    private final List<int[]> rects = new ArrayList<>();
    private final List<Runnable> acts = new ArrayList<>();
    /** Filas visibles (para la selección arrastrando) y si el arrastre marca o desmarca. */
    private final List<Object[]> rowHits = new ArrayList<>();
    private Boolean dragAdd;
    private TextFieldWidget search;
    private boolean history, keepOne = true, confirm, offering, onlyFav;
    private int setIdx = -1, rarityIdx = -1, valueBand, qtyBand, sort;
    private double scroll;
    // oferta
    private int offerTo, offerQty = 1;
    private long offerPrice;

    private static final long[][] BANDS = {{0, Long.MAX_VALUE}, {0, 999}, {1000, 9_999}, {10_000, 99_999}, {100_000, Long.MAX_VALUE}};
    private static final String[] BAND_KEYS = {"all", "lt1k", "1k10k", "10k100k", "gt100k"};

    public BinderMarketScreen(NbtCompound d) {
        super(Text.translatable("tcgshop.bm2.title"));
        update(d);
    }

    public void update(NbtCompound d) {
        data = d;
        fav.clear();
        for (NbtElement e : d.getList("fav", NbtElement.STRING_TYPE)) fav.add(e.asString());
        confirm = false;
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    protected void init() {
        String prev = search == null ? "" : search.getText();
        search = new TextFieldWidget(textRenderer, width - 168, 30, 150, 14, Text.translatable("tcgshop.binder.search"));
        search.setPlaceholder(Text.translatable("tcgshop.binder.search").formatted(Formatting.DARK_GRAY));
        search.setText(prev);
        search.setChangedListener(s -> scroll = 0);
        addSelectableChild(search);
    }

    private static void act(NbtCompound d) {
        ClientPlayNetworking.send(new UiPayloads.Act("binder", d));
    }

    private double rate() { return data.contains("rate") ? data.getDouble("rate") : 0.5; }

    private record Row(Catalog.Card card, int count, long unit) {}

    private List<Row> rows() {
        List<Row> out = new ArrayList<>();
        List<Catalog.CardSet> sets = Catalog.sets();
        String q = search == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
        for (Map.Entry<String, Integer> e : ClientCollection.counts.entrySet()) {
            if (e.getValue() <= 0) continue;
            Catalog.Card c = Catalog.card(e.getKey());
            if (c == null) continue;
            if (setIdx >= 0 && setIdx < sets.size() && !sets.get(setIdx).id().equals(c.set())) continue;
            if (rarityIdx >= 0 && c.rarity().family != Rarity.Family.values()[rarityIdx]) continue;
            if (onlyFav && !fav.contains(c.key())) continue;
            if (qtyBand == 1 && e.getValue() < 2) continue;
            if (qtyBand == 2 && e.getValue() < 5) continue;
            long unit = PriceService.priceCd(c.stack());
            if (unit < BANDS[valueBand][0] || unit > BANDS[valueBand][1]) continue;
            if (!q.isEmpty() && !c.stack().getName().getString().toLowerCase(Locale.ROOT).contains(q)) continue;
            out.add(new Row(c, e.getValue(), unit));
        }
        Comparator<Row> cmp = switch (sort) {
            case 1 -> Comparator.comparingInt((Row r) -> r.count).reversed();
            case 2 -> Comparator.comparing((Row r) -> r.card.stack().getName().getString());
            case 3 -> Comparator.comparingLong((Row r) -> r.unit * r.count).reversed();
            default -> Comparator.comparingLong((Row r) -> r.unit).reversed();
        };
        out.sort(cmp);
        return out;
    }

    private int sellQty(Row r) {
        return keepOne ? r.count - 1 : r.count;
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        rects.clear();
        acts.clear();
        rowHits.clear();
        ctx.fillGradient(0, 0, width, height, 0xE0101420, 0xF0080A10);
        int x = 14, w = width - 28;
        Draw.scaledCentered(ctx, Text.translatable("tcgshop.bm2.title").formatted(Formatting.BOLD), width / 2f, 8, 1.3f, 0xFFFFFFFF);
        // valor total de la colección
        long total = 0;
        int owned = 0;
        for (Map.Entry<String, Integer> e : ClientCollection.counts.entrySet()) {
            Catalog.Card c = Catalog.card(e.getKey());
            if (c == null || e.getValue() <= 0) continue;
            total += PriceService.priceCd(c.stack()) * e.getValue();
            owned += e.getValue();
        }
        ctx.drawTextWithShadow(textRenderer, Text.translatable("tcgshop.bm2.value", PriceService.format(total), owned), x, 10, Ui.GOLD);
        tab(ctx, x, 28, Text.translatable("tcgshop.bm2.tab_cards"), !history, mx, my, () -> history = false);
        tab(ctx, x + 84, 28, Text.translatable("tcgshop.bm2.tab_history"), history, mx, my, () -> history = true);
        if (!data.getBoolean("binder")) {
            Ui.text(ctx, Text.translatable("tcgshop.binder.need"), 0, height / 2, width, 10, 0xFFFF7A5C, false);
            return;
        }
        if (history) { renderHistory(ctx, x, 50, w, height - 64); return; }
        search.render(ctx, mx, my, delta);
        // filtros
        int fy = 48, fx = x;
        List<Catalog.CardSet> sets = Catalog.sets();
        Text setLabel = setIdx < 0 ? Text.translatable("tcgshop.bm2.f_all_sets") : sets.get(setIdx).name();
        fx = chip(ctx, fx, fy, Text.translatable("tcgshop.bm2.f_set", setLabel), mx, my, () -> setIdx = setIdx + 1 >= sets.size() ? -1 : setIdx + 1, () -> setIdx = setIdx - 1 < -1 ? sets.size() - 1 : setIdx - 1);
        Text rl = rarityIdx < 0 ? Text.translatable("tcgshop.bm2.f_all") : Rarity.Family.values()[rarityIdx].displayName();
        fx = chip(ctx, fx, fy, Text.translatable("tcgshop.bm2.f_rarity", rl), mx, my, () -> rarityIdx = rarityIdx + 1 >= Rarity.Family.values().length ? -1 : rarityIdx + 1, () -> rarityIdx = rarityIdx - 1 < -1 ? Rarity.Family.values().length - 1 : rarityIdx - 1);
        fx = chip(ctx, fx, fy, Text.translatable("tcgshop.bm2.f_value", Text.translatable("tcgshop.bm2.band." + BAND_KEYS[valueBand])), mx, my, () -> valueBand = (valueBand + 1) % BANDS.length, () -> valueBand = (valueBand + BANDS.length - 1) % BANDS.length);
        fx = chip(ctx, fx, fy, Text.translatable("tcgshop.bm2.f_qty", Text.translatable("tcgshop.bm2.qty." + qtyBand)), mx, my, () -> qtyBand = (qtyBand + 1) % 3, () -> qtyBand = (qtyBand + 2) % 3);
        fx = chip(ctx, fx, fy, Text.translatable("tcgshop.bm2.sort." + sort), mx, my, () -> sort = (sort + 1) % 4, () -> sort = (sort + 3) % 4);
        chip(ctx, fx, fy, Text.translatable(onlyFav ? "tcgshop.bm2.f_fav_on" : "tcgshop.bm2.f_fav_off"), mx, my, () -> onlyFav = !onlyFav, () -> onlyFav = !onlyFav);
        // lista
        List<Row> rows = rows();
        int ly = 66, lh = height - ly - 52, rowH = 16;
        scroll = MathHelper.clamp(scroll, 0, Math.max(0, rows.size() * rowH - lh));
        Ui.round(ctx, x, ly - 2, w, lh + 4, 0x66000000);
        ctx.enableScissor(x, ly, x + w, ly + lh);
        int colQty = x + w - 300, colUnit = x + w - 250, colSell = x + w - 170, colTot = x + w - 90;
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            int y = ly + i * rowH - (int) scroll;
            if (y + rowH < ly || y > ly + lh) continue;
            String key = r.card.key();
            boolean on = sel.contains(key), isFav = fav.contains(key), hover = Ui.in(mx, my, x, y, w, rowH) && Ui.in(mx, my, x, ly, w, lh);
            if (on) ctx.fill(x, y, x + w, y + rowH, 0x553A6FD8);
            else if (hover) ctx.fill(x, y, x + w, y + rowH, 0x22FFFFFF);
            // casilla
            Ui.round(ctx, x + 4, y + 3, 10, 10, on ? 0xFF3A6FD8 : 0xFF2A2F44);
            if (on) ctx.drawText(textRenderer, "✔", x + 5, y + 4, 0xFFFFFFFF, false);
            // favorita
            ctx.drawText(textRenderer, isFav ? "★" : "☆", x + 18, y + 4, isFav ? 0xFFFFD34D : 0xFF6A7090, false);
            if (Ui.in(mx, my, x, ly, w, lh)) {
                rects.add(new int[]{x, Math.max(y, ly), w, rowH});
                acts.add(() -> { if (!sel.remove(key)) sel.add(key); confirm = false; dragAdd = sel.contains(key); });
                int hy = Math.max(y, ly);
                rowHits.add(new Object[]{x, hy, w, Math.min(y + rowH, ly + lh) - hy, key});
                rects.add(new int[]{x + 16, Math.max(y, ly), 12, rowH}); // la estrella va encima de la fila
                acts.add(() -> { NbtCompound d = new NbtCompound(); d.putString("a", "fav"); d.putString("id", key); act(d); if (!fav.remove(key)) fav.add(key); });
            }
            String name = Ui.ellipsis(r.card.stack().getName().getString(), colQty - x - 150);
            ctx.drawText(textRenderer, name, x + 32, y + 4, r.card.rarity().color | 0xFF000000, false);
            ctx.drawText(textRenderer, Ui.ellipsis(Text.translatable("masterball.set." + r.card.set()).getString(), 110), colQty - 118, y + 4, 0xFF8A90A8, false);
            ctx.drawText(textRenderer, "×" + r.count, colQty, y + 4, r.count > 1 ? 0xFF8BE08B : 0xFFCCCCCC, false);
            ctx.drawText(textRenderer, PriceService.format(r.unit), colUnit, y + 4, Ui.GOLD, false);
            long sellUnit = Math.max(1, Math.round(r.unit * rate()));
            ctx.drawText(textRenderer, PriceService.format(sellUnit), colSell, y + 4, isFav ? 0xFF6A7090 : 0xFFFF9F6B, false);
            ctx.drawText(textRenderer, PriceService.format(r.unit * r.count), colTot, y + 4, 0xFFDDE3F0, false);
        }
        ctx.disableScissor();
        // cabecera de columnas
        ctx.drawText(textRenderer, Text.translatable("tcgshop.bm2.col_qty"), colQty, ly - 11, 0xFF8A90A8, false);
        ctx.drawText(textRenderer, Text.translatable("tcgshop.bm2.col_market"), colUnit, ly - 11, 0xFF8A90A8, false);
        ctx.drawText(textRenderer, Text.translatable("tcgshop.bm2.col_quick", Math.round(rate() * 100)), colSell, ly - 11, 0xFF8A90A8, false);
        ctx.drawText(textRenderer, Text.translatable("tcgshop.bm2.col_total"), colTot, ly - 11, 0xFF8A90A8, false);
        if (rows.isEmpty()) Ui.text(ctx, Text.translatable("tcgshop.bm2.empty"), x, ly + lh / 2, w, 10, Ui.MUTED, false);
        bottomBar(ctx, x, height - 46, w, rows, mx, my);
    }

    private void bottomBar(DrawContext ctx, int x, int y, int w, List<Row> rows, int mx, int my) {
        Ui.roundGradient(ctx, x, y, w, 40, 0xF01F2333, 0xF0171A26);
        // lo seleccionado (que siga en la lista filtrada o no)
        int n = 0;
        long pay = 0;
        Row single = null;
        int favSel = 0;
        for (String k : sel) {
            Catalog.Card c = Catalog.card(k);
            int count = ClientCollection.count(k);
            if (c == null || count <= 0) continue;
            if (fav.contains(k)) { favSel++; continue; }
            Row r = new Row(c, count, PriceService.priceCd(c.stack()));
            int q = sellQty(r);
            if (q <= 0) continue;
            n += q;
            pay += Math.max(1, Math.round(r.unit * rate())) * q;
            single = sel.size() == 1 ? r : null;
        }
        if (sel.size() == 1 && single == null) {
            String k = sel.iterator().next();
            Catalog.Card c = Catalog.card(k);
            if (c != null && ClientCollection.count(k) > 0) single = new Row(c, ClientCollection.count(k), PriceService.priceCd(c.stack()));
        }
        ctx.drawText(textRenderer, Text.translatable("tcgshop.bm2.selected", sel.size(), n, PriceService.format(pay)), x + 8, y + 6, 0xFFFFFFFF, false);
        if (favSel > 0) ctx.drawText(textRenderer, Text.translatable("tcgshop.bm2.fav_protected", favSel), x + 8, y + 18, 0xFFFFD34D, false);
        Ui.button(ctx, x + 8, y + 22, 112, 14, Text.translatable(keepOne ? "tcgshop.bm2.keep_one_on" : "tcgshop.bm2.keep_one_off"), mx, my, true, 0xFF39415E);
        rects.add(new int[]{x + 8, y + 22, 112, 14});
        acts.add(() -> { keepOne = !keepOne; confirm = false; });
        Ui.button(ctx, x + 124, y + 22, 90, 14, Text.translatable("tcgshop.bm2.select_all"), mx, my, true, 0xFF39415E);
        rects.add(new int[]{x + 124, y + 22, 90, 14});
        acts.add(() -> { if (sel.isEmpty()) for (Row r : rows) sel.add(r.card.key()); else sel.clear(); confirm = false; });
        // vender
        final int fn = n;
        Text sellLabel = confirm ? Text.translatable("tcgshop.bm2.confirm_sell", PriceService.format(pay)) : Text.translatable("tcgshop.bm2.sell");
        int bw = 150;
        Ui.button(ctx, x + w - bw - 8, y + 6, bw, 28, sellLabel, mx, my, n > 0, confirm ? 0xFFB0442A : 0xFF2E8A4A);
        if (n > 0) {
            rects.add(new int[]{x + w - bw - 8, y + 6, bw, 28});
            acts.add(() -> {
                if (!confirm) { confirm = true; return; }
                NbtCompound d = new NbtCompound();
                d.putString("a", "sell");
                NbtList items = new NbtList();
                for (String k : sel) {
                    if (fav.contains(k)) continue;
                    int count = ClientCollection.count(k);
                    int q = keepOne ? count - 1 : count;
                    if (q <= 0) continue;
                    NbtCompound it = new NbtCompound();
                    it.putString("id", k);
                    it.putInt("q", q);
                    items.add(it);
                }
                d.put("items", items);
                act(d);
                sel.clear();
                confirm = false;
            });
        }
        // ofertar a un jugador (una carta)
        NbtList online = data.getList("online", NbtElement.COMPOUND_TYPE);
        int ox = x + w - bw - 8 - 168;
        if (single != null && !online.isEmpty()) {
            if (!offering) {
                Ui.button(ctx, ox, y + 6, 160, 28, Text.translatable("tcgshop.bm2.offer"), mx, my, true, 0xFF8E44AD);
                Row s0 = single;
                rects.add(new int[]{ox, y + 6, 160, 28});
                acts.add(() -> { offering = true; offerQty = 1; offerPrice = Math.max(1, s0.unit); offerTo = 0; });
            } else offerPanel(ctx, x, y - 64, w, single, online, mx, my);
        } else offering = false;
    }

    private void offerPanel(DrawContext ctx, int x, int y, int w, Row r, NbtList online, int mx, int my) {
        int pw = 330, px = x + w - pw - 8;
        Ui.roundGradient(ctx, px, y, pw, 60, 0xF02A1F3A, 0xF01A1426);
        Ui.outline(ctx, px, y, pw, 60, 0xFF8E44AD);
        offerTo = Math.floorMod(offerTo, online.size());
        NbtCompound to = online.getCompound(offerTo);
        int bx = px + 6;
        ctx.drawText(textRenderer, Text.translatable("tcgshop.bm2.offer_to"), bx, y + 6, 0xFFDDD0F0, false);
        bx += textRenderer.getWidth(Text.translatable("tcgshop.bm2.offer_to")) + 4;
        bx = small(ctx, bx, y + 3, "◀", mx, my, () -> offerTo--);
        String nm = to.getString("n");
        ctx.drawText(textRenderer, nm, bx + 2, y + 6, 0xFFFFFFFF, false);
        bx += textRenderer.getWidth(nm) + 6;
        small(ctx, bx, y + 3, "▶", mx, my, () -> offerTo++);
        int qx = px + 200;
        ctx.drawText(textRenderer, Text.translatable("tcgshop.bm2.offer_qty"), qx, y + 6, 0xFFDDD0F0, false);
        qx += textRenderer.getWidth(Text.translatable("tcgshop.bm2.offer_qty")) + 4;
        qx = small(ctx, qx, y + 3, "-", mx, my, () -> offerQty = Math.max(1, offerQty - 1));
        ctx.drawText(textRenderer, String.valueOf(offerQty), qx + 2, y + 6, 0xFFFFFFFF, false);
        small(ctx, qx + 14, y + 3, "+", mx, my, () -> offerQty = Math.min(Math.min(64, r.count), offerQty + 1));
        int py = y + 22;
        ctx.drawText(textRenderer, Text.translatable("tcgshop.bm2.offer_price"), px + 6, py + 3, 0xFFDDD0F0, false);
        int b2 = px + 6 + textRenderer.getWidth(Text.translatable("tcgshop.bm2.offer_price")) + 4;
        b2 = small(ctx, b2, py, "-10%", mx, my, () -> offerPrice = Math.max(1, Math.round(offerPrice * 0.9)));
        b2 = small(ctx, b2, py, "-1%", mx, my, () -> offerPrice = Math.max(1, offerPrice - Math.max(1, offerPrice / 100)));
        String ps = PriceService.format(offerPrice);
        ctx.drawText(textRenderer, Text.literal(ps).formatted(Formatting.BOLD), b2 + 2, py + 3, Ui.GOLD, false);
        b2 += textRenderer.getWidth(ps) + 8;
        b2 = small(ctx, b2, py, "+1%", mx, my, () -> offerPrice += Math.max(1, offerPrice / 100));
        b2 = small(ctx, b2, py, "+10%", mx, my, () -> offerPrice = Math.round(offerPrice * 1.1) + 1);
        small(ctx, b2, py, Text.translatable("tcgshop.bm2.offer_market").getString(), mx, my, () -> offerPrice = Math.max(1, r.unit * offerQty));
        Ui.button(ctx, px + pw - 168, y + 42, 100, 14, Text.translatable("tcgshop.bm2.offer_send"), mx, my, true, 0xFF8E44AD);
        rects.add(new int[]{px + pw - 168, y + 42, 100, 14});
        UUID target = to.getUuid("u");
        acts.add(() -> {
            NbtCompound d = new NbtCompound();
            d.putString("a", "offer");
            d.putString("id", r.card.key());
            d.putInt("q", offerQty);
            d.putLong("price", offerPrice);
            d.putUuid("to", target);
            act(d);
            offering = false;
        });
        Ui.button(ctx, px + pw - 62, y + 42, 56, 14, Text.translatable("tcgshop.bm2.cancel"), mx, my, true, 0xFF39324A);
        rects.add(new int[]{px + pw - 62, y + 42, 56, 14});
        acts.add(() -> offering = false);
        ctx.drawText(textRenderer, Text.translatable("tcgshop.bm2.offer_note"), px + 6, y + 45, 0xFF8A80A0, false);
    }

    private void renderHistory(DrawContext ctx, int x, int y, int w, int h) {
        NbtList hist = data.getList("hist", NbtElement.COMPOUND_TYPE);
        Ui.round(ctx, x, y, w, h, 0x66000000);
        if (hist.isEmpty()) { Ui.text(ctx, Text.translatable("tcgshop.bm2.no_history"), x, y + h / 2, w, 10, Ui.MUTED, false); return; }
        SimpleDateFormat f = new SimpleDateFormat("dd/MM HH:mm", Locale.ROOT);
        int ry = y + 6;
        for (int i = 0; i < hist.size() && ry < y + h - 12; i++) {
            NbtCompound e = hist.getCompound(i);
            Catalog.Card c = Catalog.card(e.getString("c"));
            String name = c == null ? e.getString("c") : c.stack().getName().getString();
            String k = e.getString("k");
            int col = k.equals("offer_bought") ? 0xFF6BC5FF : 0xFF8BE08B;
            ctx.drawText(textRenderer, f.format(new Date(e.getLong("t"))), x + 8, ry, 0xFF8A90A8, false);
            ctx.drawText(textRenderer, Text.translatable("tcgshop.bm2.h." + k, e.getInt("q"), Ui.ellipsis(name, 200), PriceService.format(e.getLong("p")), e.getString("w")), x + 80, ry, col, false);
            ry += 12;
        }
    }

    private void tab(DrawContext ctx, int x, int y, Text label, boolean on, int mx, int my, Runnable r) {
        Ui.chip(ctx, x, y, 80, 14, label, on, Ui.in(mx, my, x, y, 80, 14), 0xFF3A6FD8, 0);
        rects.add(new int[]{x, y, 80, 14});
        acts.add(r);
    }

    /** Filtro: clic izquierdo avanza, clic derecho retrocede (aquí: clic = avanzar; el segundo runnable para la rueda). */
    private int chip(DrawContext ctx, int x, int y, Text label, int mx, int my, Runnable next, Runnable prev) {
        int w = textRenderer.getWidth(label) + 12;
        boolean hover = Ui.in(mx, my, x, y, w, 13);
        Ui.round(ctx, x, y, w, 13, hover ? 0xFF39415E : 0xFF262B3E);
        ctx.drawText(textRenderer, label, x + 6, y + 3, 0xFFDDE3F0, false);
        rects.add(new int[]{x, y, w, 13});
        acts.add(() -> { next.run(); scroll = 0; });
        return x + w + 4;
    }

    private int small(DrawContext ctx, int x, int y, String label, int mx, int my, Runnable r) {
        int w = textRenderer.getWidth(label) + 8;
        Ui.button(ctx, x, y, w, 13, Text.literal(label), mx, my, true, 0xFF39415E);
        rects.add(new int[]{x, y, w, 13});
        acts.add(r);
        return x + w + 3;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (search != null && search.mouseClicked(mouseX, mouseY, button)) { setFocused(search); return true; }
        if (search != null) search.setFocused(false);
        dragAdd = null;
        // de arriba abajo: los últimos añadidos (botones de la barra/oferta) tienen prioridad
        for (int i = rects.size() - 1; i >= 0; i--) {
            int[] r = rects.get(i);
            if (Ui.in(mouseX, mouseY, r[0], r[1], r[2], r[3])) {
                acts.get(i).run();
                if (client != null && client.player != null) client.player.playSound(net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK.value(), 0.35f, 1.3f);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** Selección rápida: mantener pulsado sobre una carta y arrastrar por encima de otras las marca (o desmarca) todas. */
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (button == 0 && dragAdd != null && !history) {
            for (Object[] r : rowHits) {
                if (!Ui.in(mouseX, mouseY, (int) r[0], (int) r[1], (int) r[2], (int) r[3])) continue;
                String key = (String) r[4];
                if (dragAdd) sel.add(key); else sel.remove(key);
                confirm = false;
                break;
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragAdd = null;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double h, double v) {
        scroll -= v * 24;
        return true;
    }

    @SuppressWarnings("unused")
    private static Map<String, Integer> copy(Map<String, Integer> m) { return new LinkedHashMap<>(m); }
}

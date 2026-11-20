package dev.tcgshop.client.binder;

import dev.tcgshop.card.Rarity;
import dev.tcgshop.client.screen.Draw;
import dev.tcgshop.collection.Titles;
import dev.tcgshop.network.BinderActionPayload;
import dev.tcgshop.pricing.PriceService;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static dev.tcgshop.client.screen.Draw.withAlpha;

/**
 * Binder General: todas tus cartas en un único inventario.
 *  · Pestañas laterales: ★ Todas, ♥ Deseadas, ✦ Nuevas y una por pack (con progreso).
 *  · Pestañas superiores por rareza (globales o dentro del pack elegido), buscador y huecos de cartas que faltan.
 *  · Notificaciones ★: se quitan al pasar el cursor; doble clic en una pestaña limpia todas las suyas.
 *  · Panel de detalle (precio, tendencia 7 días, deseada, sacar cartas) y selector de título.
 */
public class GeneralBinderScreen extends Screen {
    private static final Rarity[] RARITY_TABS = {Rarity.COMMON, Rarity.DOUBLE_RARE, Rarity.ULTRA_RARE, Rarity.ILLUSTRATION_RARE,
            Rarity.SPECIAL_ILLUSTRATION_RARE, Rarity.HYPER_RARE};
    private static final int CELL_W = 36, CELL_H = 50, GAP = 5, HEADER_H = 20;

    private enum Mode { SET, WISH, NEW }

    private record Area(int x, int y, int w, int h, Runnable action) {}

    private record Row(Catalog.CardSet set, List<Catalog.Card> cards, int y, int h) {}

    private final List<Area> areas = new ArrayList<>();
    private Mode mode = Mode.SET;
    private String selectedSet;
    private Rarity selectedRarity;
    private boolean showMissing = true;
    private String selectedCard;
    private TextFieldWidget search;
    private double scroll, sideScroll;
    private List<Row> rows = new ArrayList<>();
    private int contentHeight;
    private boolean dirty = true, titlesOpen;
    private String lastClickedTab;
    private long lastClickTime;
    private int lastHash;

    private int px, py, pw, ph, sideX, sideY, sideW, sideH, gridX, gridY, gridW, gridH, detX, detW;

    public GeneralBinderScreen() {
        super(Text.translatable("tcgshop.binder.general"));
    }

    @Override
    protected void init() {
        Catalog.rebuild();
        pw = Math.min(width - 10, 700);
        ph = Math.min(height - 10, 420);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        sideX = px + 8;
        sideY = py + 36;
        sideW = Math.min(150, pw / 5);
        sideH = ph - 44;
        detW = Math.min(140, pw / 5);
        detX = px + pw - detW - 8;
        gridX = sideX + sideW + 8;
        gridY = py + 60;
        gridW = detX - 8 - gridX;
        gridH = py + ph - 8 - gridY;
        String prev = search == null ? "" : search.getText();
        int sw = Math.min(160, pw / 4);
        search = new TextFieldWidget(textRenderer, px + pw / 2 - sw / 2, py + 10, sw, 14, Text.translatable("tcgshop.binder.search"));
        search.setPlaceholder(Text.translatable("tcgshop.binder.search").formatted(Formatting.DARK_GRAY));
        search.setText(prev);
        search.setChangedListener(s -> { dirty = true; scroll = 0; });
        addSelectableChild(search);
        dirty = true;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    // ------------------------------------------------------------------ filtros y layout

    private boolean matches(Catalog.Card c) {
        if (selectedRarity != null && c.rarity() != selectedRarity) return false;
        boolean owned = ClientCollection.count(c.key()) > 0;
        if (mode == Mode.WISH && !ClientCollection.wishlist.contains(c.key())) return false;
        if (mode == Mode.NEW && !ClientCollection.fresh.contains(c.key())) return false;
        if (mode == Mode.SET && !owned && !showMissing) return false;
        String q = search.getText().trim().toLowerCase(Locale.ROOT);
        return q.isEmpty() || c.name().getString().toLowerCase(Locale.ROOT).contains(q);
    }

    private void layout() {
        rows = new ArrayList<>();
        int cols = Math.max(1, (gridW - 10 + GAP) / (CELL_W + GAP));
        int y = 0;
        for (Catalog.CardSet set : Catalog.sets()) {
            if (mode == Mode.SET && selectedSet != null && !selectedSet.equals(set.id())) continue;
            List<Catalog.Card> list = new ArrayList<>();
            for (Catalog.Card c : set.cards()) if (matches(c)) list.add(c);
            if (list.isEmpty()) continue;
            rows.add(new Row(set, null, y, HEADER_H));
            y += HEADER_H;
            for (int i = 0; i < list.size(); i += cols) {
                rows.add(new Row(set, list.subList(i, Math.min(list.size(), i + cols)), y, CELL_H + GAP));
                y += CELL_H + GAP;
            }
            y += 6;
        }
        contentHeight = y;
        scroll = MathHelper.clamp(scroll, 0, Math.max(0, contentHeight - gridH + 8));
        dirty = false;
    }

    // ------------------------------------------------------------------ render

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fillGradient(0, 0, width, height, 0x90101018, 0xB0050508);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ClientCollection.tick();
        int hash = ClientCollection.counts.hashCode() * 31 + ClientCollection.wishlist.hashCode() + (mode == Mode.NEW ? ClientCollection.fresh.hashCode() : 0);
        if (hash != lastHash) { lastHash = hash; dirty = true; }
        if (dirty) layout();
        areas.clear();
        double t = System.nanoTime() / 1e9;

        renderBackground(ctx, mouseX, mouseY, delta);
        Ui.leather(ctx, px, py, pw, ph, 0xFF7A2328, 0xFF471216);
        ctx.fillGradient(px + 6, py + 32, px + pw - 6, py + ph - 6, 0xFF262A3C, 0xFF1A1D2A);
        Ui.outline(ctx, px + 6, py + 32, pw - 12, ph - 38, 0xFF0B0C12);

        renderHeader(ctx, mouseX, mouseY);
        Map<String, Integer> freshBySet = new HashMap<>();
        Map<Rarity, Integer> freshByRarity = new HashMap<>();
        for (String k : ClientCollection.fresh) {
            Catalog.Card c = Catalog.card(k);
            if (c == null) continue;
            freshBySet.merge(c.set(), 1, Integer::sum);
            if (mode != Mode.SET || selectedSet == null || selectedSet.equals(c.set())) freshByRarity.merge(c.rarity(), 1, Integer::sum);
        }
        renderSidebar(ctx, mouseX, mouseY, freshBySet);
        renderRarityTabs(ctx, mouseX, mouseY, freshByRarity);
        Catalog.Card hovered = titlesOpen ? null : renderGrid(ctx, mouseX, mouseY, t);
        if (titlesOpen) renderGridFrameOnly(ctx);
        renderDetail(ctx, mouseX, mouseY, hovered, t);
        search.render(ctx, mouseX, mouseY, delta);
        if (titlesOpen) renderTitles(ctx, mouseX, mouseY);
    }

    private void renderHeader(DrawContext ctx, int mouseX, int mouseY) {
        // emblema
        StylePainter.circle(ctx, px + 18, py + 16, 8, 0xFF151515);
        StylePainter.circle(ctx, px + 18, py + 16, 7, 0xFFE8463C);
        ctx.fill(px + 11, py + 16, px + 26, py + 23, 0xFFF5F5F5);
        ctx.fill(px + 11, py + 15, px + 26, py + 17, 0xFF151515);
        StylePainter.circle(ctx, px + 18, py + 16, 2, 0xFFF5F5F5);
        ctx.drawTextWithShadow(textRenderer, Text.translatable("tcgshop.binder.general").formatted(Formatting.BOLD), px + 32, py + 8, Ui.GOLD);
        String owner = client != null && client.player != null ? client.player.getName().getString() : "";
        ctx.drawText(textRenderer, Text.translatable("tcgshop.binder.of", owner), px + 32, py + 19, 0xFFE8C8C0, false);

        int owned = 0, total = Catalog.totalCards();
        long value = 0;
        for (Map.Entry<String, Integer> e : ClientCollection.counts.entrySet()) {
            Catalog.Card c = Catalog.card(e.getKey());
            if (c == null) continue;
            owned++;
            value += PriceService.priceCd(c.stack()) * e.getValue();
        }
        // botón de título
        Text tb = Text.translatable("tcgshop.binder.title_button");
        int tbw = Ui.chipWidth(tb) + 4, tbx = px + pw - tbw - 10;
        Ui.button(ctx, tbx, py + 8, tbw, 16, tb, mouseX, mouseY, true, 0xFF8E44AD);
        areas.add(new Area(tbx, py + 8, tbw, 16, () -> titlesOpen = !titlesOpen));
        // pastillas de estadísticas
        String pct = String.format(Locale.ROOT, "%.1f%%", total == 0 ? 0 : owned * 100.0 / total);
        int x = tbx - 6;
        x = pill(ctx, x, py + 8, Text.literal("₽ " + PriceService.format(value)), 0xFF8A6A1C);
        x = pill(ctx, x, py + 8, Text.literal(pct), 0xFF2F6FB0);
        pill(ctx, x, py + 8, Text.literal(owned + "/" + total), 0xFF2E7D3A);
    }

    /** Pastilla alineada a la derecha que termina en x; devuelve el nuevo x. */
    private int pill(DrawContext ctx, int right, int y, Text text, int color) {
        int w = textRenderer.getWidth(text) + 10;
        int x = right - w;
        if (x < search.getX() + search.getWidth() + 6) return right;
        Ui.roundGradient(ctx, x, y, w, 16, Ui.brighten(color, 20), Ui.darken(color, 20));
        Ui.outline(ctx, x, y, w, 16, 0xFF0B0C12);
        ctx.drawText(textRenderer, text, x + 5, y + 4, 0xFFFFFFFF, true);
        return x - 4;
    }

    private void renderSidebar(DrawContext ctx, int mouseX, int mouseY, Map<String, Integer> freshBySet) {
        Ui.round(ctx, sideX, sideY, sideW, sideH, 0xFF12141F);
        int rowH = 26;
        List<Catalog.CardSet> sets = Catalog.sets();
        int specials = 3;
        int total = (sets.size() + specials) * rowH + 6;
        sideScroll = MathHelper.clamp(sideScroll, 0, Math.max(0, total - sideH + 4));
        ctx.enableScissor(sideX, sideY + 2, sideX + sideW, sideY + sideH - 2);
        int y = sideY + 3 - (int) sideScroll;
        drawSpecialTab(ctx, Mode.SET, null, Text.translatable("tcgshop.binder.all_sets"), 0xFFE0B33C, sideX + 3, y, sideW - 6, rowH - 3, mouseX, mouseY,
                ClientCollection.fresh.size());
        y += rowH;
        drawSpecialTab(ctx, Mode.WISH, null, Text.translatable("tcgshop.binder.wishlist", ClientCollection.wishlist.size()), 0xFFFF6FA8,
                sideX + 3, y, sideW - 6, rowH - 3, mouseX, mouseY, 0);
        y += rowH;
        drawSpecialTab(ctx, Mode.NEW, null, Text.translatable("tcgshop.binder.new_cards"), 0xFFFFE066, sideX + 3, y, sideW - 6, rowH - 3,
                mouseX, mouseY, ClientCollection.fresh.size());
        y += rowH + 6;
        for (Catalog.CardSet s : sets) {
            if (y + rowH >= sideY && y <= sideY + sideH) {
                int own = 0;
                for (Catalog.Card c : s.cards()) if (ClientCollection.count(c.key()) > 0) own++;
                drawSetTab(ctx, s, sideX + 3, y, sideW - 6, rowH - 3, mouseX, mouseY, freshBySet.getOrDefault(s.id(), 0), own, s.cards().size());
            }
            y += rowH;
        }
        ctx.disableScissor();
    }

    private void drawSpecialTab(DrawContext ctx, Mode m, String id, Text label, int accent, int x, int y, int w, int h, int mx, int my, int fresh) {
        boolean sel = mode == m && (m != Mode.SET || selectedSet == null);
        boolean hover = Ui.in(mx, my, x, y, w, h) && my >= sideY && my < sideY + sideH;
        Ui.roundGradient(ctx, x, y, w, h, sel ? withAlpha(accent, 0.55f) : hover ? 0xFF30354B : 0xFF1F2333, sel ? withAlpha(accent, 0.3f) : 0xFF181B28);
        Ui.outline(ctx, x, y, w, h, sel ? accent : 0xFF2B3045);
        Ui.text(ctx, label, x, y, w, h, 0xFFFFFFFF, true);
        if (fresh > 0 && m == Mode.SET) Ui.badge(ctx, x + w - 6, y + 1, fresh);
        String key = m == Mode.SET ? "all" : m == Mode.NEW ? "new" : "wish";
        areas.add(new Area(x, Math.max(y, sideY), w, h, () -> {
            if (isDoubleClick(key) && m != Mode.WISH) clearFresh("all");
            else { mode = m; selectedSet = null; selectedRarity = null; scroll = 0; dirty = true; }
        }));
    }

    private void drawSetTab(DrawContext ctx, Catalog.CardSet set, int x, int y, int w, int h, int mx, int my, int fresh, int own, int total) {
        boolean sel = mode == Mode.SET && set.id().equals(selectedSet);
        boolean hover = Ui.in(mx, my, x, y, w, h) && my >= sideY && my < sideY + sideH;
        Ui.roundGradient(ctx, x, y, w, h, sel ? 0xFF8A6A1C : hover ? 0xFF30354B : 0xFF1F2333, sel ? 0xFF5C4410 : 0xFF181B28);
        Ui.outline(ctx, x, y, w, h, sel ? Ui.GOLD : 0xFF2B3045);
        float pph = h - 4;
        Draw.pack(ctx, set.packTexture(), set.packStack(), x + 3, y + 2, pph * Draw.PACK_RATIO, pph, 1f);
        int tx = x + 6 + Math.round(pph * Draw.PACK_RATIO), tw = w - (tx - x) - 4;
        Text name = set.name();
        float s = Math.min(1f, tw / (float) Math.max(1, textRenderer.getWidth(name)));
        s = Math.max(0.65f, s);
        var m = ctx.getMatrices();
        m.push();
        m.translate(tx, y + 3, 0);
        m.scale(s, s, 1);
        ctx.drawText(textRenderer, Ui.ellipsis(name.getString(), (int) (tw / s)), 0, 0, Ui.TEXT, true);
        m.pop();
        Ui.progress(ctx, tx, y + h - 8, tw - 22, 5, total == 0 ? 0 : own / (float) total, own == total ? 0xFF43C463 : 0xFF3A7BD5);
        String pct = (total == 0 ? 0 : own * 100 / total) + "%";
        m.push();
        m.translate(x + w - 3 - textRenderer.getWidth(pct) * 0.7f, y + h - 9, 0);
        m.scale(0.7f, 0.7f, 1);
        ctx.drawText(textRenderer, pct, 0, 0, own == total ? 0xFF43C463 : Ui.MUTED, false);
        m.pop();
        if (fresh > 0) Ui.badge(ctx, x + w - 6, y + 1, fresh);
        if (hover) setTooltip(Text.literal(set.name().getString() + "  ·  " + own + "/" + total));
        String tabKey = "set:" + set.id();
        areas.add(new Area(x, Math.max(y, sideY), w, h, () -> {
            if (isDoubleClick(tabKey)) clearFresh(tabKey);
            else { mode = Mode.SET; selectedSet = set.id(); selectedRarity = null; scroll = 0; dirty = true; }
        }));
    }

    private void renderRarityTabs(DrawContext ctx, int mouseX, int mouseY, Map<Rarity, Integer> freshByRarity) {
        int y = py + 38, h = 16;
        List<Text> labels = new ArrayList<>();
        labels.add(Text.translatable("tcgshop.binder.all_rarities"));
        for (Rarity r : RARITY_TABS) labels.add(Text.translatable("tcgshop.rarity.short." + r.key));
        Text missing = Text.translatable(showMissing ? "tcgshop.binder.missing_on" : "tcgshop.binder.missing_off");
        int natural = 0;
        for (Text l : labels) natural += Ui.chipWidth(l) + 3;
        int missW = Ui.chipWidth(missing);
        float k = Math.min(1f, (gridW - missW - 6f) / natural);
        int x = gridX;
        int totalFresh = 0;
        for (int v : freshByRarity.values()) totalFresh += v;
        for (int i = 0; i < labels.size(); i++) {
            int w = Math.max(24, (int) (Ui.chipWidth(labels.get(i)) * k));
            Rarity r = i == 0 ? null : RARITY_TABS[i - 1];
            boolean sel = selectedRarity == r;
            int accent = r == null ? 0xFFCCCCCC : r.color;
            Ui.chip(ctx, x, y, w, h, labels.get(i), sel, Ui.in(mouseX, mouseY, x, y, w, h), accent, r == null ? totalFresh : freshByRarity.getOrDefault(r, 0));
            if (r != null && Ui.in(mouseX, mouseY, x, y, w, h)) setTooltip(r.displayName());
            String key = r == null ? (mode == Mode.SET && selectedSet != null ? "set:" + selectedSet : "all") : "rarity:" + r.key;
            areas.add(new Area(x, y, w, h, () -> {
                if (isDoubleClick("r:" + key)) clearFresh(key);
                else { selectedRarity = r; dirty = true; scroll = 0; }
            }));
            x += w + 3;
        }
        if (mode == Mode.SET) {
            int tx = gridX + gridW - missW;
            Ui.chip(ctx, tx, y, missW, h, missing, false, Ui.in(mouseX, mouseY, tx, y, missW, h), 0xFF7A7A90, 0);
            areas.add(new Area(tx, y, missW, h, () -> { showMissing = !showMissing; dirty = true; }));
        }
    }

    private void renderGridFrameOnly(DrawContext ctx) {
        Ui.round(ctx, gridX, gridY, gridW, gridH, 0xFF10121B);
    }

    private Catalog.Card renderGrid(DrawContext ctx, int mouseX, int mouseY, double t) {
        Ui.round(ctx, gridX, gridY, gridW, gridH, 0xFF10121B);
        if (rows.isEmpty()) {
            String key = mode == Mode.WISH ? "tcgshop.binder.empty_wish" : mode == Mode.NEW ? "tcgshop.binder.empty_new" : "tcgshop.binder.empty";
            int y = gridY + gridH / 2 - 10;
            for (var line : textRenderer.wrapLines(Text.translatable(key), gridW - 30)) {
                ctx.drawCenteredTextWithShadow(textRenderer, line, gridX + gridW / 2, y, Ui.MUTED);
                y += 10;
            }
            return null;
        }
        Catalog.Card hovered = null;
        int hx = 0, hy = 0;
        ctx.enableScissor(gridX + 1, gridY + 1, gridX + gridW - 1, gridY + gridH - 1);
        int baseY = gridY + 5 - (int) scroll;
        for (Row row : rows) {
            int y = baseY + row.y;
            if (y + row.h < gridY || y > gridY + gridH) continue;
            if (row.cards == null) {
                int own = 0;
                for (Catalog.Card c : row.set.cards()) if (ClientCollection.count(c.key()) > 0) own++;
                int total = row.set.cards().size();
                Ui.roundGradient(ctx, gridX + 4, y + 1, gridW - 8, HEADER_H - 4, 0xFF2A2F44, 0xFF1F2333);
                ctx.drawTextWithShadow(textRenderer, row.set.name().copy().formatted(Formatting.BOLD), gridX + 9, y + 5, Ui.GOLD);
                String prog = own + " / " + total;
                int pwd = textRenderer.getWidth(prog);
                ctx.drawTextWithShadow(textRenderer, prog, gridX + gridW - pwd - 76, y + 5, own == total ? 0xFF43C463 : Ui.MUTED);
                Ui.progress(ctx, gridX + gridW - 70, y + 6, 60, 6, total == 0 ? 0 : own / (float) total, own == total ? 0xFF43C463 : 0xFF3A7BD5);
                continue;
            }
            int x = gridX + 7;
            for (Catalog.Card c : row.cards) {
                boolean hover = Ui.in(mouseX, mouseY, x, y, CELL_W, CELL_H) && Ui.in(mouseX, mouseY, gridX, gridY, gridW, gridH);
                if (hover) { hovered = c; hx = x; hy = y; ClientCollection.markSeen(c.key()); }
                else drawCell(ctx, c, x, y, false, t);
                String key = c.key();
                if (y >= gridY - CELL_H && y <= gridY + gridH)
                    areas.add(new Area(x, Math.max(y, gridY), CELL_W, Math.min(CELL_H, gridY + gridH - Math.max(y, gridY)), () -> selectedCard = key));
                x += CELL_W + GAP;
            }
        }
        if (hovered != null) drawCell(ctx, hovered, hx, hy - 2, true, t); // encima, levantada
        ctx.disableScissor();
        if (contentHeight > gridH) {
            int barH = Math.max(16, gridH * gridH / contentHeight);
            int barY = gridY + (int) ((gridH - barH) * (scroll / Math.max(1, contentHeight - gridH + 8)));
            Ui.round(ctx, gridX + gridW - 5, barY, 3, barH, 0x99FFFFFF);
        }
        return hovered;
    }

    private void drawCell(DrawContext ctx, Catalog.Card c, int x, int y, boolean hover, double t) {
        int count = ClientCollection.count(c.key());
        boolean fresh = ClientCollection.fresh.contains(c.key());
        boolean selected = c.key().equals(selectedCard);
        boolean wish = ClientCollection.wishlist.contains(c.key());
        if (count > 0) {
            if (fresh) Draw.glow(ctx, x + CELL_W / 2f, y + CELL_H / 2f, CELL_W, CELL_H, 0xFFFFE066, 0.35f + 0.25f * (float) Math.sin(t * 5));
            ctx.fill(x + 2, y + 3, x + CELL_W + 2, y + CELL_H + 3, 0x77000000);
            Draw.cardFace(ctx, c.texture(), c.stack(), x, y, CELL_W, CELL_H, 1f);
            if (hover && c.rarity().tier > 0) Draw.holo(ctx, x, y, CELL_W, CELL_H, c.rarity().tier, c.rarity().color, t, x + CELL_W / 2);
            Ui.outline(ctx, x, y, CELL_W, CELL_H, selected ? 0xFFFFFFFF : c.rarity().tier > 0 ? c.rarity().color : 0xFF000000);
            if (count > 1) {
                String s = "x" + count;
                int w = textRenderer.getWidth(s) + 3;
                ctx.fill(x + CELL_W - w, y + CELL_H - 9, x + CELL_W, y + CELL_H, 0xCC000000);
                ctx.drawText(textRenderer, s, x + CELL_W - w + 2, y + CELL_H - 8, 0xFFFFFFFF, false);
            }
            if (fresh) ctx.drawText(textRenderer, "★", x + 2, y + 2, withAlpha(0xFFFFE066, 0.6f + 0.4f * (float) Math.sin(t * 6)), true);
        } else {
            Ui.roundGradient(ctx, x, y, CELL_W, CELL_H, 0xFF22263A, 0xFF181B28);
            Ui.outline(ctx, x, y, CELL_W, CELL_H, selected ? 0xFFFFFFFF : withAlpha(c.rarity().color, 0.45f));
            String num = c.number() < 0 ? "?" : String.format(Locale.ROOT, "%03d", c.number());
            ctx.drawText(textRenderer, num, x + (CELL_W - textRenderer.getWidth(num)) / 2, y + CELL_H / 2 - 4, 0xFF6A6F8C, false);
            ctx.fill(x + 3, y + CELL_H - 5, x + CELL_W - 3, y + CELL_H - 3, withAlpha(c.rarity().color, 0.5f));
        }
        if (wish) ctx.drawText(textRenderer, "♥", x + CELL_W - 8, y + 2, 0xFFFF6FA8, true);
        if (hover) Ui.outline(ctx, x - 1, y - 1, CELL_W + 2, CELL_H + 2, 0xFFFFFFFF);
    }

    private void renderDetail(DrawContext ctx, int mouseX, int mouseY, Catalog.Card hovered, double t) {
        Ui.round(ctx, detX, sideY, detW, sideH, 0xFF12141F);
        Catalog.Card c = hovered != null ? hovered : selectedCard == null ? null : Catalog.card(selectedCard);
        int x = detX + 6, w = detW - 12;
        if (c == null) {
            int y = sideY + 12;
            for (String k : List.of("tcgshop.binder.tip1", "tcgshop.binder.tip2", "tcgshop.binder.tip3", "tcgshop.binder.tip4")) {
                for (var line : textRenderer.wrapLines(Text.translatable(k), w)) {
                    ctx.drawTextWithShadow(textRenderer, line, x, y, Ui.MUTED);
                    y += 10;
                }
                y += 6;
            }
        } else {
            int count = ClientCollection.count(c.key());
            int cw = Math.min(w, 100), ch = Math.round(cw / Draw.CARD_RATIO);
            int maxH = sideH - 120;
            if (ch > maxH) { ch = maxH; cw = Math.round(ch * Draw.CARD_RATIO); }
            int cx = detX + (detW - cw) / 2, cy = sideY + 8;
            if (count > 0) {
                if (c.rarity().tier > 0) Draw.glow(ctx, cx + cw / 2f, cy + ch / 2f, cw, ch, c.rarity().color, 0.3f);
                ctx.fill(cx + 3, cy + 4, cx + cw + 3, cy + ch + 4, 0x77000000);
                Draw.cardFace(ctx, c.texture(), c.stack(), cx, cy, cw, ch, 1f);
                if (c.rarity().tier > 0) Draw.holo(ctx, cx, cy, cw, ch, c.rarity().tier, c.rarity().color, t, mouseX);
            } else {
                Draw.cardFace(ctx, c.texture(), c.stack(), cx, cy, cw, ch, 1f);
                ctx.fill(cx, cy, cx + cw, cy + ch, 0xC0101018); // velada: aún no la tienes
                Ui.outline(ctx, cx, cy, cw, ch, withAlpha(c.rarity().color, 0.5f));
                Ui.text(ctx, Text.translatable("tcgshop.binder.not_owned"), cx, cy + ch / 2 - 6, cw, 12, 0xFFDDDDDD, true);
            }
            int y = cy + ch + 5;
            for (var line : textRenderer.wrapLines(c.name(), w)) {
                ctx.drawTextWithShadow(textRenderer, line, x, y, Ui.TEXT);
                y += 10;
            }
            ctx.drawText(textRenderer, Text.translatable("tcgshop.set." + c.set()), x, y, Ui.MUTED, false);
            y += 10;
            ctx.drawTextWithShadow(textRenderer, c.rarity().displayName(), x, y, c.rarity().color);
            y += 10;
            long now = PriceService.priceCd(c.stack());
            long week = PriceService.priceCdOnDay(c.id(), c.rarity(), Math.max(0, PriceService.marketDay() - 7));
            double ch7 = week == 0 ? 0 : (now - week) * 100.0 / week;
            ctx.drawTextWithShadow(textRenderer, Text.literal(PriceService.format(now)), x, y, Ui.GOLD);
            String trend = String.format(Locale.ROOT, "%+.1f%%", ch7);
            ctx.drawTextWithShadow(textRenderer, trend, x + w - textRenderer.getWidth(trend), y, ch7 >= 0 ? 0xFF6BE07B : 0xFFFF6B6B);
            y += 10;
            ctx.drawTextWithShadow(textRenderer, Text.translatable("tcgshop.binder.have", count), x, y, count > 0 ? 0xFF8BE08B : Ui.MUTED);
            y += 13;
            String key = c.key();
            boolean wished = ClientCollection.wishlist.contains(key);
            Text wl = Text.translatable(wished ? "tcgshop.binder.wished" : "tcgshop.binder.wish");
            Ui.button(ctx, x, y, w, 15, wl, mouseX, mouseY, true, wished ? 0xFFB0306A : 0xFF4A3550);
            areas.add(new Area(x, y, w, 15, () -> ClientCollection.action(BinderActionPayload.WISH_TOGGLE, key, 0)));
            y += 18;
            int bw = (w - 4) / 2;
            Ui.button(ctx, x, y, bw, 15, Text.translatable("tcgshop.binder.take1"), mouseX, mouseY, count > 0, 0xFF2F6FB0);
            if (count > 0) areas.add(new Area(x, y, bw, 15, () -> ClientCollection.action(BinderActionPayload.WITHDRAW, key, 1)));
            Ui.button(ctx, x + bw + 4, y, bw, 15, Text.translatable("tcgshop.binder.take_all"), mouseX, mouseY, count > 0, 0xFF2F6FB0);
            if (count > 0) areas.add(new Area(x + bw + 4, y, bw, 15, () -> ClientCollection.action(BinderActionPayload.WITHDRAW, key, count)));
        }
        int by = sideY + sideH - 22;
        Ui.button(ctx, x, by, w, 18, Text.translatable("tcgshop.binder.deposit"), mouseX, mouseY, true, 0xFF3C8A4A);
        areas.add(new Area(x, by, w, 18, () -> ClientCollection.action(BinderActionPayload.DEPOSIT_ALL, "", 0)));
        if (Ui.in(mouseX, mouseY, x, by, w, 18)) setTooltip(Text.translatable("tcgshop.binder.deposit_tip"));
    }

    private void renderTitles(DrawContext ctx, int mouseX, int mouseY) {
        int w = Math.min(260, gridW - 20), h = Math.min(230, gridH - 10);
        int x = gridX + (gridW - w) / 2, y = gridY + 5;
        Ui.leather(ctx, x, y, w, h, 0xFF3A2650, 0xFF22162F);
        Ui.text(ctx, Text.translatable("tcgshop.binder.titles").formatted(Formatting.BOLD), x, y + 4, w, 12, Ui.GOLD, true);
        int ry = y + 22;
        List<String> options = new ArrayList<>();
        options.add("");
        options.addAll(ClientCollection.titles);
        if (ClientCollection.titles.isEmpty()) {
            for (var line : textRenderer.wrapLines(Text.translatable("tcgshop.binder.no_titles"), w - 16)) {
                ctx.drawTextWithShadow(textRenderer, line, x + 8, ry + 22, Ui.MUTED);
                ry += 10;
            }
        }
        for (String opt : options) {
            if (ry > y + h - 18) break;
            boolean sel = opt.equals(ClientCollection.title);
            Text label = opt.isEmpty() ? Text.translatable("tcgshop.binder.no_title") : Titles.display(opt);
            Ui.chip(ctx, x + 8, ry, w - 16, 15, label, sel, Ui.in(mouseX, mouseY, x + 8, ry, w - 16, 15), 0xFFB983FF, 0);
            areas.add(new Area(x + 8, ry, w - 16, 15, () -> ClientCollection.action(BinderActionPayload.TITLE_SET, opt, 0)));
            ry += 17;
        }
        Ui.button(ctx, x + w - 58, y + h - 20, 50, 15, Text.translatable("gui.done"), mouseX, mouseY, true, 0xFF3C8A4A);
        areas.add(new Area(x + w - 58, y + h - 20, 50, 15, () -> titlesOpen = false));
    }

    // ------------------------------------------------------------------ entrada

    private boolean isDoubleClick(String tabKey) {
        long now = System.currentTimeMillis();
        boolean dbl = tabKey.equals(lastClickedTab) && now - lastClickTime < 350;
        lastClickedTab = tabKey;
        lastClickTime = now;
        return dbl;
    }

    private void clearFresh(String filter) {
        if (filter.equals("all")) ClientCollection.fresh.clear();
        else ClientCollection.fresh.removeIf(k -> {
            Catalog.Card c = Catalog.card(k);
            if (c == null) return false;
            if (filter.startsWith("set:")) return c.set().equals(filter.substring(4));
            if (filter.startsWith("rarity:")) return c.rarity().key.equals(filter.substring(7))
                    && (mode != Mode.SET || selectedSet == null || c.set().equals(selectedSet));
            return false;
        });
        if (filter.startsWith("rarity:") && mode == Mode.SET && selectedSet != null) {
            List<String> ids = new ArrayList<>();
            for (Catalog.CardSet s : Catalog.sets())
                if (s.id().equals(selectedSet)) for (Catalog.Card c : s.cards()) if (c.rarity().key.equals(filter.substring(7))) ids.add(c.key());
            ClientCollection.action(BinderActionPayload.MARK_SEEN, String.join(",", ids), 0);
            return;
        }
        ClientCollection.action(BinderActionPayload.CLEAR_FRESH, filter, 0);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (search.mouseClicked(mouseX, mouseY, button)) {
            setFocused(search);
            return true;
        }
        search.setFocused(false);
        for (int i = areas.size() - 1; i >= 0; i--) {
            Area a = areas.get(i);
            if (Ui.in(mouseX, mouseY, a.x, a.y, a.w, a.h)) {
                a.action.run();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double h, double v) {
        if (Ui.in(mouseX, mouseY, sideX, sideY, sideW, sideH)) sideScroll -= v * 26;
        else scroll = MathHelper.clamp(scroll - v * (CELL_H + GAP), 0, Math.max(0, contentHeight - gridH + 8));
        return true;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        return search.isFocused() && search.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (search.isFocused() && keyCode != GLFW.GLFW_KEY_ESCAPE) return search.keyPressed(keyCode, scanCode, modifiers) || true;
        if (titlesOpen && keyCode == GLFW.GLFW_KEY_ESCAPE) { titlesOpen = false; return true; }
        if (dev.tcgshop.client.TcgShopClient.OPEN_BINDER.matchesKey(keyCode, scanCode)) {
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}

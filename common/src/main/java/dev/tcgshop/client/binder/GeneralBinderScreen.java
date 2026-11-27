package dev.tcgshop.client.binder;

import dev.masterball.api.card.CardInfo;
import dev.masterball.api.card.Rarity;
import dev.tcgshop.client.screen.Draw;
import dev.tcgshop.collection.BinderData;
import dev.tcgshop.collection.Titles;
import dev.tcgshop.network.BinderActionPayload;
import dev.tcgshop.pricing.PriceService;
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
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import static dev.tcgshop.client.screen.Draw.withAlpha;

/**
 * General Binder: all your cards in a single inventory.
 *  · Side tabs: ★ All, ♥ Wishlist, ✦ New, ◆ Variants & graded and one per pack (with progress).
 *  · Top tabs by rarity (global or within the selected pack), search box and placeholders for missing cards.
 *  · Price row (1.0): preset ranges, min / max and sort by price; combined with pack, rarity, search and missing filters.
 *  · Multi-select (1.0): Ctrl+click (or Ctrl+drag) toggles cards, Shift+click selects a range, Ctrl+A / «Select all»
 *    picks every card shown; «Take 1 of each» / «Take all» withdraws the selection in one go (only what fits).
 *  · ★ notifications: cleared after ~1.5 s of hovering, or when leaving «New» / closing (the ones you looked at);
 *    new cards stay in «New» while the binder is open. Double-clicking a tab clears all of its notifications.
 *  · Detail panel (price, 7-day trend, wishlist, withdraw cards) and title picker.
 */
public class GeneralBinderScreen extends Screen {
    private static final Rarity.Family[] RARITY_TABS = Rarity.Family.values();
    private static final int CELL_W = 36, CELL_H = 50, GAP = 5, HEADER_H = 20;
    /** Hover time needed to count a new card as seen, and duration of the glow fade-out. */
    private static final long SEEN_HOVER_MS = 1500, FADE_MS = 700;
    /** Price filter presets (in the shop currency): any, < 100, 100-1K, 1K-10K, 10K-100K, 100K+. */
    private static final long[][] PRICE_PRESETS = {
            {0, Long.MAX_VALUE}, {0, 99}, {100, 999}, {1_000, 9_999}, {10_000, 99_999}, {100_000, Long.MAX_VALUE}};
    /** Batch withdraw: max entries / characters per packet (the server caps entries too). */
    private static final int BATCH_ENTRIES = 300, BATCH_CHARS = 30_000;

    private enum Mode { SET, WISH, NEW, SPECIAL }

    private record Area(int x, int y, int w, int h, Runnable action) {}

    /** cards == null: set header; matched / value = cards shown in that set and their value (price filter summary). */
    private record Row(Catalog.CardSet set, List<Catalog.Card> cards, int y, int h, int matched, long value) {}

    private final List<Area> areas = new ArrayList<>();
    private Mode mode = Mode.SET;
    private String selectedSet;
    private Rarity.Family selectedRarity;
    /** Empty slots of missing cards: off when the binder opens, so the first thing you see is the cards you own. */
    private boolean showMissing = false;
    private String selectedCard;
    private EditBox search, minField, maxField;
    private double scroll, sideScroll;
    private List<Row> rows = new ArrayList<>();
    private int contentHeight;
    private boolean dirty = true, titlesOpen;
    private String lastClickedTab;
    private long lastClickTime;
    private int lastHash;
    /** Cards that have been new while the binder is open: «New» keeps them even after they have been seen. */
    private final Set<String> sessionNew = new HashSet<>();
    /** New cards you looked at (even briefly): marked as seen when leaving «New» or closing. */
    private final Set<String> glanced = new HashSet<>();
    /** Moment each card was marked as seen, to fade its glow out instead of removing it abruptly. */
    private final Map<String, Long> fadeStart = new HashMap<>();
    private String hoverKey;
    /** Zoomed view of the right-panel card (overlay: the binder keeps scroll, filters and tab). */
    private final CardZoom zoom = new CardZoom();
    private long hoverSince;

    // price filter / sort (1.0)
    private int pricePreset;
    /** 0 = card number, 1 = price high to low, 2 = price low to high. */
    private int sortMode;
    private final Map<String, Long> priceCache = new HashMap<>();
    /** Special cards (variants / graded) as catalog entries, key "#" + signature. Rebuilt on every layout. */
    private final Map<String, Catalog.Card> specialCards = new LinkedHashMap<>();

    // multi-select (1.0)
    private final Set<String> selection = new LinkedHashSet<>();
    /** Cards shown, in display order (for Shift ranges and «Select all»). */
    private List<Catalog.Card> visible = new ArrayList<>();
    private String anchorKey;
    /** Ctrl+drag over cells: true = adding, false = removing, null = not painting. */
    private Boolean paintSelect;

    private int px, py, pw, ph, sideX, sideY, sideW, sideH, gridX, gridY, gridW, gridH, detX, detW, priceY;
    /**
     * 1.1 review fix: the «Closest to completion» panel is docked at the bottom of the left sidebar (always visible, also
     * while a card is hovered or selected) when the screen is tall enough; listH is then the height left for the set list.
     * On very small screens it falls back to the empty detail panel.
     */
    private static final int CLOSEST_H = 128;
    private int listH;
    private boolean closestDocked;

    public GeneralBinderScreen() {
        super(Component.translatable("tcgshop.binder.general"));
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
        closestDocked = sideH - CLOSEST_H - 4 >= 150;
        listH = closestDocked ? sideH - CLOSEST_H - 4 : sideH;
        detW = Math.min(140, pw / 5);
        detX = px + pw - detW - 8;
        gridX = sideX + sideW + 8;
        priceY = py + 58;
        gridY = py + 77;
        gridW = detX - 8 - gridX;
        gridH = py + ph - 8 - gridY;
        String prev = search == null ? "" : search.getValue();
        int sw = Math.min(160, pw / 4);
        search = new EditBox(font, px + pw / 2 - sw / 2, py + 10, sw, 14, Component.translatable("tcgshop.binder.search"));
        search.setHint(Component.translatable("tcgshop.binder.search").withStyle(ChatFormatting.DARK_GRAY));
        search.setValue(prev);
        search.setResponder(s -> { dirty = true; scroll = 0; });
        addWidget(search);
        // price row: [preset] [min] [max] [sort]
        String prevMin = minField == null ? "" : minField.getValue(), prevMax = maxField == null ? "" : maxField.getValue();
        int fieldW = Math.max(26, gridW * 15 / 100);
        minField = priceField(gridX + presetW() + 3, fieldW, "tcgshop.binder.price_min", prevMin);
        maxField = priceField(gridX + presetW() + 6 + fieldW, fieldW, "tcgshop.binder.price_max", prevMax);
        dirty = true;
    }

    private EditBox priceField(int x, int w, String key, String text) {
        EditBox f = new EditBox(font, x, priceY, w, 14, Component.translatable(key));
        f.setHint(Component.translatable(key).withStyle(ChatFormatting.DARK_GRAY));
        f.setFilter(s -> s.matches("\\d{0,12}"));
        f.setValue(text);
        f.setResponder(s -> { dirty = true; scroll = 0; });
        addWidget(f);
        return f;
    }

    private int presetW() { return Math.max(56, gridW * 34 / 100); }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------------ filters and layout

    private boolean matches(Catalog.Card c) {
        if (selectedRarity != null && c.rarity().family != selectedRarity) return false;
        boolean owned = ClientCollection.count(c.key()) > 0;
        if (mode == Mode.WISH && !ClientCollection.wishlist.contains(c.key())) return false;
        if (mode == Mode.NEW && !sessionNew.contains(c.key())) return false;
        if (mode == Mode.SET && !owned && !showMissing) return false;
        if (priceFiltered()) {
            long p = price(c);
            if (p < priceMin() || p > priceMax()) return false;
        }
        String q = search.getValue().trim().toLowerCase(Locale.ROOT);
        return q.isEmpty() || c.name().getString().toLowerCase(Locale.ROOT).contains(q);
    }

    /** Current price of that card (its own variant / grade for special cards), cached per layout. */
    private long price(Catalog.Card c) {
        Long p = priceCache.get(c.key());
        if (p == null) {
            p = PriceService.priceCd(c.stack());
            priceCache.put(c.key(), p);
        }
        return p;
    }

    private static long parse(EditBox f, long fallback) {
        String s = f == null ? "" : f.getValue().trim();
        if (s.isEmpty()) return fallback;
        try { return Long.parseLong(s); } catch (NumberFormatException e) { return fallback; }
    }

    private long priceMin() { return Math.max(PRICE_PRESETS[pricePreset][0], parse(minField, 0)); }

    private long priceMax() { return Math.min(PRICE_PRESETS[pricePreset][1], parse(maxField, Long.MAX_VALUE)); }

    private boolean priceFiltered() { return priceMin() > 0 || priceMax() < Long.MAX_VALUE; }

    private void rebuildSpecials() {
        specialCards.clear();
        for (ItemStack st : ClientCollection.specials) {
            if (st.isEmpty() || !CardInfo.isCard(st)) continue;
            Catalog.Card base = Catalog.card(CardInfo.id(st).toString());
            if (base == null) continue;
            String key = "#" + BinderData.signature(st);
            specialCards.putIfAbsent(key, new Catalog.Card(base.id(), key, st, base.rarity(), base.number(), base.texture(), base.set()));
        }
    }

    /** Catalog entry of a key (plain card id, or "#" + signature for a special card). */
    private Catalog.Card cardFor(String key) {
        if (key == null) return null;
        return key.startsWith("#") ? specialCards.get(key) : Catalog.card(key);
    }

    private void layout() {
        rows = new ArrayList<>();
        visible = new ArrayList<>();
        priceCache.clear();
        rebuildSpecials();
        Map<String, List<Catalog.Card>> specialsBySet = new HashMap<>();
        if (mode == Mode.SPECIAL) for (Catalog.Card c : specialCards.values()) specialsBySet.computeIfAbsent(c.set(), k -> new ArrayList<>()).add(c);
        int cols = Math.max(1, (gridW - 10 + GAP) / (CELL_W + GAP));
        int y = 0;
        for (Catalog.CardSet set : Catalog.sets()) {
            if ((mode == Mode.SET || mode == Mode.SPECIAL) && selectedSet != null && !selectedSet.equals(set.id())) continue;
            List<Catalog.Card> source = mode == Mode.SPECIAL ? specialsBySet.getOrDefault(set.id(), List.of()) : set.cards();
            List<Catalog.Card> list = new ArrayList<>();
            for (Catalog.Card c : source) if (matches(c)) list.add(c);
            if (list.isEmpty()) continue;
            if (sortMode == 1) list.sort(Comparator.<Catalog.Card>comparingLong(this::price).reversed().thenComparing(Catalog.Card::key));
            else if (sortMode == 2) list.sort(Comparator.<Catalog.Card>comparingLong(this::price).thenComparing(Catalog.Card::key));
            long value = 0;
            for (Catalog.Card c : list) value += price(c) * Math.max(0, ClientCollection.count(c.key()));
            rows.add(new Row(set, null, y, HEADER_H, list.size(), value));
            y += HEADER_H;
            for (int i = 0; i < list.size(); i += cols) {
                rows.add(new Row(set, list.subList(i, Math.min(list.size(), i + cols)), y, CELL_H + GAP, 0, 0));
                y += CELL_H + GAP;
            }
            visible.addAll(list);
            y += 6;
        }
        contentHeight = y;
        scroll = Mth.clamp(scroll, 0, Math.max(0, contentHeight - gridH + 8));
        dirty = false;
    }

    // ------------------------------------------------------------------ render

    @Override
    public void renderBackground(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        ctx.fillGradient(0, 0, width, height, 0x90101018, 0xB0050508);
    }

    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        int realX = mouseX, realY = mouseY;
        if (zoom.isOpen()) { mouseX = -10000; mouseY = -10000; } // no hover or tooltips underneath the zoomed view
        ClientCollection.tick();
        sessionNew.addAll(ClientCollection.fresh); // cards that arrive while the binder is open stay too
        int hash = ClientCollection.counts.hashCode() * 31 + ClientCollection.wishlist.hashCode() + (mode == Mode.NEW ? sessionNew.hashCode() : 0)
                + ClientCollection.specialsHash() * 17 + ClientCollection.specials.size();
        if (hash != lastHash) { lastHash = hash; dirty = true; }
        if (dirty) layout();
        selection.removeIf(k -> ClientCollection.count(k) <= 0); // taken out / no longer owned
        areas.clear();
        double t = System.nanoTime() / 1e9;

        renderBackground(ctx, mouseX, mouseY, delta);
        Ui.leather(ctx, px, py, pw, ph, 0xFF7A2328, 0xFF471216);
        ctx.fillGradient(px + 6, py + 32, px + pw - 6, py + ph - 6, 0xFF262A3C, 0xFF1A1D2A);
        Ui.outline(ctx, px + 6, py + 32, pw - 12, ph - 38, 0xFF0B0C12);

        renderHeader(ctx, mouseX, mouseY);
        Map<String, Integer> freshBySet = new HashMap<>();
        Map<Rarity.Family, Integer> freshByRarity = new HashMap<>();
        for (String k : ClientCollection.fresh) {
            Catalog.Card c = Catalog.card(k);
            if (c == null) continue;
            freshBySet.merge(c.set(), 1, Integer::sum);
            if (mode != Mode.SET || selectedSet == null || selectedSet.equals(c.set())) freshByRarity.merge(c.rarity().family, 1, Integer::sum);
        }
        renderSidebar(ctx, mouseX, mouseY, freshBySet);
        renderRarityTabs(ctx, mouseX, mouseY, freshByRarity);
        renderPriceRow(ctx, mouseX, mouseY);
        Catalog.Card hovered = titlesOpen ? null : renderGrid(ctx, mouseX, mouseY, t);
        if (titlesOpen) { renderGridFrameOnly(ctx); trackHover(null); }
        renderDetail(ctx, mouseX, mouseY, hovered, t);
        search.render(ctx, mouseX, mouseY, delta);
        minField.render(ctx, mouseX, mouseY, delta);
        maxField.render(ctx, mouseX, mouseY, delta);
        if (titlesOpen) renderTitles(ctx, mouseX, mouseY);
        if (zoom.isOpen()) zoom.render(ctx, width, height, realX, realY);
    }

    private void renderHeader(GuiGraphics ctx, int mouseX, int mouseY) {
        // emblem
        StylePainter.circle(ctx, px + 18, py + 16, 8, 0xFF151515);
        StylePainter.circle(ctx, px + 18, py + 16, 7, 0xFFE8463C);
        ctx.fill(px + 11, py + 16, px + 26, py + 23, 0xFFF5F5F5);
        ctx.fill(px + 11, py + 15, px + 26, py + 17, 0xFF151515);
        StylePainter.circle(ctx, px + 18, py + 16, 2, 0xFFF5F5F5);
        ctx.drawString(font, Component.translatable("tcgshop.binder.general").withStyle(ChatFormatting.BOLD), px + 32, py + 8, Ui.GOLD);
        String owner = minecraft != null && minecraft.player != null ? minecraft.player.getName().getString() : "";
        ctx.drawString(font, Component.translatable("tcgshop.binder.of", owner), px + 32, py + 19, 0xFFE8C8C0, false);

        int owned = 0, total = Catalog.totalCards();
        long value = 0;
        for (Map.Entry<String, Integer> e : ClientCollection.counts.entrySet()) {
            Catalog.Card c = Catalog.card(e.getKey());
            if (c == null) continue;
            owned++;
            value += PriceService.priceCd(c.stack()) * e.getValue();
        }
        for (int i = 0; i < ClientCollection.specials.size(); i++)
            value += PriceService.priceCd(ClientCollection.specials.get(i)) * ClientCollection.specialCounts.get(i);
        // title button
        Component tb = Component.translatable("tcgshop.binder.title_button");
        int tbw = Ui.chipWidth(tb) + 4, tbx = px + pw - tbw - 10;
        Ui.button(ctx, tbx, py + 8, tbw, 16, tb, mouseX, mouseY, true, 0xFF8E44AD);
        areas.add(new Area(tbx, py + 8, tbw, 16, () -> titlesOpen = !titlesOpen));
        // stat pills
        String pct = String.format(Locale.ROOT, "%.1f%%", total == 0 ? 0 : owned * 100.0 / total);
        int x = tbx - 6;
        x = pill(ctx, x, py + 8, Component.literal("₽ " + PriceService.format(value)), 0xFF8A6A1C);
        x = pill(ctx, x, py + 8, Component.literal(pct), 0xFF2F6FB0);
        pill(ctx, x, py + 8, Component.literal(owned + "/" + total), 0xFF2E7D3A);
    }

    /** Right-aligned pill ending at x; returns the new x. */
    private int pill(GuiGraphics ctx, int right, int y, Component text, int color) {
        int w = font.width(text) + 10;
        int x = right - w;
        if (x < search.getX() + search.getWidth() + 6) return right;
        Ui.roundGradient(ctx, x, y, w, 16, Ui.brighten(color, 20), Ui.darken(color, 20));
        Ui.outline(ctx, x, y, w, 16, 0xFF0B0C12);
        ctx.drawString(font, text, x + 5, y + 4, 0xFFFFFFFF, true);
        return x - 4;
    }

    private void renderSidebar(GuiGraphics ctx, int mouseX, int mouseY, Map<String, Integer> freshBySet) {
        if (closestDocked) { // 1.1: «Closest to completion» + reward, always on screen
            int cy = sideY + listH + 4;
            Ui.round(ctx, sideX, cy, sideW, CLOSEST_H, 0xFF12141F);
            ctx.enableScissor(sideX, cy, sideX + sideW, cy + CLOSEST_H);
            closestSets(ctx, mouseX, mouseY, sideX + 6, cy + 6, sideW - 12);
            ctx.disableScissor();
        }
        Ui.round(ctx, sideX, sideY, sideW, listH, 0xFF12141F);
        int rowH = 26;
        List<Catalog.CardSet> sets = Catalog.sets();
        int specials = 4;
        int total = (sets.size() + specials) * rowH + 6;
        sideScroll = Mth.clamp(sideScroll, 0, Math.max(0, total - listH + 4));
        ctx.enableScissor(sideX, sideY + 2, sideX + sideW, sideY + listH - 2);
        int y = sideY + 3 - (int) sideScroll;
        drawSpecialTab(ctx, Mode.SET, null, Component.translatable("tcgshop.binder.all_sets"), 0xFFE0B33C, sideX + 3, y, sideW - 6, rowH - 3, mouseX, mouseY,
                ClientCollection.fresh.size());
        y += rowH;
        drawSpecialTab(ctx, Mode.WISH, null, Component.translatable("tcgshop.binder.wishlist", ClientCollection.wishlist.size()), 0xFFFF6FA8,
                sideX + 3, y, sideW - 6, rowH - 3, mouseX, mouseY, 0);
        y += rowH;
        drawSpecialTab(ctx, Mode.NEW, null, Component.translatable("tcgshop.binder.new_cards"), 0xFFFFE066, sideX + 3, y, sideW - 6, rowH - 3,
                mouseX, mouseY, ClientCollection.fresh.size());
        y += rowH;
        int specialCopies = 0;
        for (int n : ClientCollection.specialCounts) specialCopies += n;
        drawSpecialTab(ctx, Mode.SPECIAL, null, Component.translatable("tcgshop.binder.specials", specialCopies), 0xFF7FE3FF, sideX + 3, y, sideW - 6, rowH - 3,
                mouseX, mouseY, 0);
        if (Ui.in(mouseX, mouseY, sideX + 3, y, sideW - 6, rowH - 3) && mouseY >= sideY && mouseY < sideY + listH)
            setTooltipForNextRenderPass(Component.translatable("tcgshop.binder.specials_tip"));
        y += rowH + 6;
        for (Catalog.CardSet s : sets) {
            if (y + rowH >= sideY && y <= sideY + listH) {
                int own = 0;
                for (Catalog.Card c : s.cards()) if (ClientCollection.count(c.key()) > 0) own++;
                drawSetTab(ctx, s, sideX + 3, y, sideW - 6, rowH - 3, mouseX, mouseY, freshBySet.getOrDefault(s.id(), 0), own, s.cards().size());
            }
            y += rowH;
        }
        ctx.disableScissor();
    }

    private void drawSpecialTab(GuiGraphics ctx, Mode m, String id, Component label, int accent, int x, int y, int w, int h, int mx, int my, int fresh) {
        boolean sel = mode == m && (m != Mode.SET || selectedSet == null);
        boolean hover = Ui.in(mx, my, x, y, w, h) && my >= sideY && my < sideY + listH;
        Ui.roundGradient(ctx, x, y, w, h, sel ? withAlpha(accent, 0.55f) : hover ? 0xFF30354B : 0xFF1F2333, sel ? withAlpha(accent, 0.3f) : 0xFF181B28);
        Ui.outline(ctx, x, y, w, h, sel ? accent : 0xFF2B3045);
        Ui.text(ctx, label, x, y, w, h, 0xFFFFFFFF, true);
        if (fresh > 0 && m == Mode.SET) Ui.badge(ctx, x + w - 6, y + 1, fresh);
        String key = switch (m) {
            case SET -> "all";
            case NEW -> "new";
            case SPECIAL -> "special";
            default -> "wish";
        };
        sideArea(x, y, w, h, () -> {
            if (isDoubleClick(key) && m != Mode.WISH && m != Mode.SPECIAL) clearFresh("all");
            else { switchMode(m); selectedSet = null; selectedRarity = null; scroll = 0; dirty = true; }
        });
    }

    /** Click area of a sidebar row, clipped to the visible set list (so it never covers the docked panel below). */
    private void sideArea(int x, int y, int w, int h, Runnable action) {
        int top = Math.max(y, sideY), bottom = Math.min(y + h, sideY + listH);
        if (bottom > top) areas.add(new Area(x, top, w, bottom - top, action));
    }

    private void drawSetTab(GuiGraphics ctx, Catalog.CardSet set, int x, int y, int w, int h, int mx, int my, int fresh, int own, int total) {
        boolean sel = mode == Mode.SET && set.id().equals(selectedSet);
        boolean hover = Ui.in(mx, my, x, y, w, h) && my >= sideY && my < sideY + listH;
        Ui.roundGradient(ctx, x, y, w, h, sel ? 0xFF8A6A1C : hover ? 0xFF30354B : 0xFF1F2333, sel ? 0xFF5C4410 : 0xFF181B28);
        Ui.outline(ctx, x, y, w, h, sel ? Ui.GOLD : 0xFF2B3045);
        float pph = h - 4;
        Draw.pack(ctx, set.packTexture(), set.packStack(), x + 3, y + 2, pph * Draw.PACK_RATIO, pph, 1f);
        int tx = x + 6 + Math.round(pph * Draw.PACK_RATIO), tw = w - (tx - x) - 4;
        Component name = set.name();
        float s = Math.min(1f, tw / (float) Math.max(1, font.width(name)));
        s = Math.max(0.65f, s);
        var m = ctx.pose();
        m.pushPose();
        m.translate(tx, y + 3, 0);
        m.scale(s, s, 1);
        ctx.drawString(font, Ui.ellipsis(name.getString(), (int) (tw / s)), 0, 0, Ui.TEXT, true);
        m.popPose();
        Ui.progress(ctx, tx, y + h - 8, tw - 22, 5, total == 0 ? 0 : own / (float) total, own == total ? 0xFF43C463 : 0xFF3A7BD5);
        String pct = (total == 0 ? 0 : own * 100 / total) + "%";
        m.pushPose();
        m.translate(x + w - 3 - font.width(pct) * 0.7f, y + h - 9, 0);
        m.scale(0.7f, 0.7f, 1);
        ctx.drawString(font, pct, 0, 0, own == total ? 0xFF43C463 : Ui.MUTED, false);
        m.popPose();
        if (fresh > 0) Ui.badge(ctx, x + w - 6, y + 1, fresh);
        if (hover) setTooltipForNextRenderPass(Component.literal(set.name().getString() + "  ·  " + own + "/" + total));
        String tabKey = "set:" + set.id();
        sideArea(x, y, w, h, () -> {
            if (isDoubleClick(tabKey)) clearFresh(tabKey);
            else { switchMode(Mode.SET); selectedSet = set.id(); selectedRarity = null; scroll = 0; dirty = true; }
        });
    }

    private void renderRarityTabs(GuiGraphics ctx, int mouseX, int mouseY, Map<Rarity.Family, Integer> freshByRarity) {
        int y = py + 38, h = 16;
        List<Component> labels = new ArrayList<>();
        labels.add(Component.translatable("tcgshop.binder.all_rarities"));
        for (Rarity.Family r : RARITY_TABS) labels.add(r.shortName());
        Component missing = Component.translatable(showMissing ? "tcgshop.binder.missing_on" : "tcgshop.binder.missing_off");
        int natural = 0;
        for (Component l : labels) natural += Ui.chipWidth(l) + 3;
        int missW = Ui.chipWidth(missing);
        float k = Math.min(1f, (gridW - missW - 6f) / natural);
        int x = gridX;
        int totalFresh = 0;
        for (int v : freshByRarity.values()) totalFresh += v;
        for (int i = 0; i < labels.size(); i++) {
            int w = Math.max(24, (int) (Ui.chipWidth(labels.get(i)) * k));
            Rarity.Family r = i == 0 ? null : RARITY_TABS[i - 1];
            boolean sel = selectedRarity == r;
            int accent = r == null ? 0xFFCCCCCC : r.color;
            Ui.chip(ctx, x, y, w, h, labels.get(i), sel, Ui.in(mouseX, mouseY, x, y, w, h), accent, r == null ? totalFresh : freshByRarity.getOrDefault(r, 0));
            if (r != null && Ui.in(mouseX, mouseY, x, y, w, h)) setTooltipForNextRenderPass(r.displayName());
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

    /** Price row (1.0): preset range, min / max fields (drawn by the widgets) and sort. */
    private void renderPriceRow(GuiGraphics ctx, int mouseX, int mouseY) {
        int h = 14, pw0 = presetW();
        Component label = Component.translatable("tcgshop.binder.price", presetLabel(pricePreset));
        boolean hoverP = Ui.in(mouseX, mouseY, gridX, priceY, pw0, h);
        Ui.chip(ctx, gridX, priceY, pw0, h, label, pricePreset != 0, hoverP, 0xFFE0B33C, 0);
        areas.add(new Area(gridX, priceY, pw0, h, () -> {
            pricePreset = Math.floorMod(pricePreset + (hasShiftDown() ? -1 : 1), PRICE_PRESETS.length);
            dirty = true;
            scroll = 0;
        }));
        if (hoverP) setTooltipForNextRenderPass(Component.translatable("tcgshop.binder.price_tip"));
        int sx = maxField.getX() + maxField.getWidth() + 3, sw = gridX + gridW - sx;
        if (sw >= 24) {
            Component sort = Component.translatable("tcgshop.binder.sort", Component.translatable("tcgshop.binder.sort." + sortMode));
            boolean hoverS = Ui.in(mouseX, mouseY, sx, priceY, sw, h);
            Ui.chip(ctx, sx, priceY, sw, h, sort, sortMode != 0, hoverS, 0xFF3A7BD5, 0);
            areas.add(new Area(sx, priceY, sw, h, () -> { sortMode = (sortMode + 1) % 3; dirty = true; }));
        }
        if (Ui.in(mouseX, mouseY, minField.getX(), priceY, maxField.getX() + maxField.getWidth() - minField.getX(), h))
            setTooltipForNextRenderPass(Component.translatable("tcgshop.binder.price_minmax_tip"));
    }

    private static Component presetLabel(int i) {
        if (i == 0) return Component.translatable("tcgshop.binder.price_any");
        long lo = PRICE_PRESETS[i][0], hi = PRICE_PRESETS[i][1];
        if (lo == 0) return Component.literal("< " + shortNum(hi + 1));
        if (hi == Long.MAX_VALUE) return Component.literal(shortNum(lo) + "+");
        return Component.literal(shortNum(lo) + "–" + shortNum(hi + 1));
    }

    private static String shortNum(long v) {
        if (v >= 1_000_000 && v % 1_000_000 == 0) return v / 1_000_000 + "M";
        if (v >= 1_000 && v % 1_000 == 0) return v / 1_000 + "K";
        return String.valueOf(v);
    }

    private void renderGridFrameOnly(GuiGraphics ctx) {
        Ui.round(ctx, gridX, gridY, gridW, gridH, 0xFF10121B);
    }

    private Catalog.Card renderGrid(GuiGraphics ctx, int mouseX, int mouseY, double t) {
        Ui.round(ctx, gridX, gridY, gridW, gridH, 0xFF10121B);
        if (rows.isEmpty()) {
            String key = mode == Mode.WISH ? "tcgshop.binder.empty_wish" : mode == Mode.NEW ? "tcgshop.binder.empty_new"
                    : mode == Mode.SPECIAL ? "tcgshop.binder.empty_special" : "tcgshop.binder.empty";
            int y = gridY + gridH / 2 - 10;
            for (var line : font.split(Component.translatable(key), gridW - 30)) {
                ctx.drawCenteredString(font, line, gridX + gridW / 2, y, Ui.MUTED);
                y += 10;
            }
            trackHover(null);
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
                Ui.roundGradient(ctx, gridX + 4, y + 1, gridW - 8, HEADER_H - 4, 0xFF2A2F44, 0xFF1F2333);
                ctx.drawString(font, row.set.name().copy().withStyle(ChatFormatting.BOLD), gridX + 9, y + 5, Ui.GOLD);
                if (priceFiltered() || mode == Mode.SPECIAL) { // summary of what the filter shows within this pack
                    Component sum = Component.translatable("tcgshop.binder.price_summary", row.matched, PriceService.format(row.value));
                    int sw = font.width(sum);
                    ctx.drawString(font, sum, gridX + gridW - sw - 10, y + 5, Ui.MUTED);
                    continue;
                }
                int own = 0;
                for (Catalog.Card c : row.set.cards()) if (ClientCollection.count(c.key()) > 0) own++;
                int total = row.set.cards().size();
                String prog = own + " / " + total;
                int pwd = font.width(prog);
                ctx.drawString(font, prog, gridX + gridW - pwd - 76, y + 5, own == total ? 0xFF43C463 : Ui.MUTED);
                Ui.progress(ctx, gridX + gridW - 70, y + 6, 60, 6, total == 0 ? 0 : own / (float) total, own == total ? 0xFF43C463 : 0xFF3A7BD5);
                continue;
            }
            int x = gridX + 7;
            for (Catalog.Card c : row.cards) {
                boolean hover = Ui.in(mouseX, mouseY, x, y, CELL_W, CELL_H) && Ui.in(mouseX, mouseY, gridX, gridY, gridW, gridH);
                if (hover) { hovered = c; hx = x; hy = y; }
                else drawCell(ctx, c, x, y, false, t);
                String key = c.key();
                if (y >= gridY - CELL_H && y <= gridY + gridH)
                    areas.add(new Area(x, Math.max(y, gridY), CELL_W, Math.min(CELL_H, gridY + gridH - Math.max(y, gridY)), () -> clickCell(key)));
                x += CELL_W + GAP;
            }
        }
        trackHover(hovered == null ? null : hovered.key());
        if (hovered != null) drawCell(ctx, hovered, hx, hy - 2, true, t); // on top, raised
        ctx.disableScissor();
        if (contentHeight > gridH) {
            int barH = Math.max(16, gridH * gridH / contentHeight);
            int barY = gridY + (int) ((gridH - barH) * (scroll / Math.max(1, contentHeight - gridH + 8)));
            Ui.round(ctx, gridX + gridW - 5, barY, 3, barH, 0x99FFFFFF);
        }
        return hovered;
    }

    private void drawCell(GuiGraphics ctx, Catalog.Card c, int x, int y, boolean hover, double t) {
        int count = ClientCollection.count(c.key());
        boolean fresh = ClientCollection.fresh.contains(c.key());
        float freshA = fresh ? 1f : fadeAlpha(c.key()); // «new» glow (fades out after it is seen)
        boolean selected = c.key().equals(selectedCard);
        boolean wish = ClientCollection.wishlist.contains(c.key());
        boolean picked = selection.contains(c.key());
        if (count > 0) {
            if (freshA > 0) Draw.glow(ctx, x + CELL_W / 2f, y + CELL_H / 2f, CELL_W, CELL_H, 0xFFFFE066, (0.35f + 0.25f * (float) Math.sin(t * 5)) * freshA);
            ctx.fill(x + 2, y + 3, x + CELL_W + 2, y + CELL_H + 3, 0x77000000);
            Draw.cardFace(ctx, c.texture(), c.stack(), x, y, CELL_W, CELL_H, 1f);
            if (hover && c.rarity().tier > 0) Draw.holo(ctx, x, y, CELL_W, CELL_H, c.rarity().tier, c.rarity().color, t, x + CELL_W / 2);
            if (c.key().startsWith("#")) SpecialCards.badge(ctx, font, c.stack(), x, y, CELL_W);
            Ui.outline(ctx, x, y, CELL_W, CELL_H, selected ? 0xFFFFFFFF : c.rarity().tier > 0 ? c.rarity().color : 0xFF000000);
            if (count > 1) {
                String s = "x" + count;
                int w = font.width(s) + 3;
                ctx.fill(x + CELL_W - w, y + CELL_H - 9, x + CELL_W, y + CELL_H, 0xCC000000);
                ctx.drawString(font, s, x + CELL_W - w + 2, y + CELL_H - 8, 0xFFFFFFFF, false);
            }
            if (freshA > 0.05f) ctx.drawString(font, "★", x + 2, y + 2, withAlpha(0xFFFFE066, (0.6f + 0.4f * (float) Math.sin(t * 6)) * freshA), true);
            if (fresh && hover && c.key().equals(hoverKey)) { // «seen» bar while you keep hovering
                float p = Math.min(1f, (System.currentTimeMillis() - hoverSince) / (float) SEEN_HOVER_MS);
                ctx.fill(x + 1, y + CELL_H - 3, x + 1 + Math.round((CELL_W - 2) * p), y + CELL_H - 1, 0xFFFFE066);
            }
            if (picked) { // multi-select mark
                ctx.fill(x, y, x + CELL_W, y + CELL_H, 0x403A7BD5);
                Ui.outline(ctx, x - 1, y - 1, CELL_W + 2, CELL_H + 2, Ui.GOLD);
                ctx.fill(x + CELL_W - 10, y + 1, x + CELL_W - 1, y + 10, 0xE0101018);
                ctx.drawString(font, "✔", x + CELL_W - 9, y + 2, Ui.GOLD, false);
            }
        } else {
            Ui.roundGradient(ctx, x, y, CELL_W, CELL_H, 0xFF22263A, 0xFF181B28);
            Ui.outline(ctx, x, y, CELL_W, CELL_H, selected ? 0xFFFFFFFF : withAlpha(c.rarity().color, 0.45f));
            String num = c.number() < 0 ? "?" : String.format(Locale.ROOT, "%03d", c.number());
            ctx.drawString(font, num, x + (CELL_W - font.width(num)) / 2, y + CELL_H / 2 - 4, 0xFF6A6F8C, false);
            ctx.fill(x + 3, y + CELL_H - 5, x + CELL_W - 3, y + CELL_H - 3, withAlpha(c.rarity().color, 0.5f));
        }
        if (wish) ctx.drawString(font, "♥", x + CELL_W - 8, y + 2, 0xFFFF6FA8, true);
        if (hover) Ui.outline(ctx, x - 1, y - 1, CELL_W + 2, CELL_H + 2, 0xFFFFFFFF);
    }

    /**
     * 1.1: «Closest to completion»: the three unfinished sets with the highest share of cards owned (click one to open
     * it), and the reward for completing a set (Master Ball + CobbleDollars, as configured on the server). Returns the
     * next free y.
     */
    private int closestSets(GuiGraphics ctx, int mouseX, int mouseY, int x, int y, int w) {
        record Near(Catalog.CardSet set, int own, int total) {}
        List<Near> near = new ArrayList<>();
        for (Catalog.CardSet s : Catalog.sets()) {
            int own = 0;
            for (Catalog.Card card : s.cards()) if (ClientCollection.count(card.key()) > 0) own++;
            if (own > 0 && own < s.cards().size()) near.add(new Near(s, own, s.cards().size()));
        }
        near.sort(Comparator.<Near>comparingDouble(n -> -n.own() / (double) n.total()).thenComparingInt(n -> n.total() - n.own()));
        ctx.drawString(font, Component.translatable("tcgshop.binder.closest").withStyle(ChatFormatting.BOLD), x, y, Ui.GOLD, false);
        y += 12;
        if (near.isEmpty()) {
            for (var line : font.split(Component.translatable("tcgshop.binder.closest_none"), w)) {
                ctx.drawString(font, line, x, y, Ui.MUTED, false);
                y += 10;
            }
        }
        for (int i = 0; i < Math.min(3, near.size()); i++) {
            Near n = near.get(i);
            int rowH = 22;
            boolean hover = Ui.in(mouseX, mouseY, x - 2, y - 1, w + 4, rowH);
            Ui.round(ctx, x - 2, y - 1, w + 4, rowH, hover ? 0xFF2A3046 : 0xFF1A1D2B);
            String cnt = n.own() + "/" + n.total();
            ctx.drawString(font, (i + 1) + ".", x + 1, y + 2, 0xFFFFE066, false);
            ctx.drawString(font, Ui.ellipsis(n.set().name().getString(), w - 16 - font.width(cnt) - 6), x + 13, y + 2, Ui.TEXT, false);
            ctx.drawString(font, cnt, x + w - font.width(cnt), y + 2, 0xFF8BE08B, false);
            Ui.progress(ctx, x + 1, y + 13, w - 2, 5, n.own() / (float) n.total(), 0xFF3A7BD5);
            String id = n.set().id();
            areas.add(new Area(x - 2, y - 1, w + 4, rowH, () -> { switchMode(Mode.SET); selectedSet = id; selectedRarity = null; scroll = 0; dirty = true; }));
            if (hover) setTooltipForNextRenderPass(Component.translatable("tcgshop.binder.closest_tip", n.total() - n.own()));
            y += rowH + 2;
        }
        long money = ClientCollection.rewardMoney, balls = ClientCollection.rewardBalls;
        if (money > 0 || balls > 0) {
            y += 2;
            Component reward = balls > 0 && money > 0 ? Component.translatable("tcgshop.binder.reward", balls, PriceService.format(money))
                    : balls > 0 ? Component.translatable("tcgshop.binder.reward_balls", balls)
                    : Component.translatable("tcgshop.binder.reward_money", PriceService.format(money));
            List<net.minecraft.util.FormattedCharSequence> lines = font.split(reward, w - 4);
            Ui.round(ctx, x - 2, y - 2, w + 4, lines.size() * 10 + 4, 0x664A3A10);
            for (var line : lines) {
                ctx.drawString(font, line, x, y, 0xFFFFD34D, false);
                y += 10;
            }
            y += 2;
        }
        return y;
    }

    private void renderDetail(GuiGraphics ctx, int mouseX, int mouseY, Catalog.Card hovered, double t) {
        Ui.round(ctx, detX, sideY, detW, sideH, 0xFF12141F);
        Catalog.Card c = hovered != null ? hovered : cardFor(selectedCard);
        int x = detX + 6, w = detW - 12;
        int bottomH = selection.isEmpty() ? 20 : 48; // multi-select controls above the deposit button
        if (c == null) {
            // 1.1: the 3 sets closest to completion + the reward (here only when the sidebar has no room for them)
            int y = closestDocked ? sideY + 8 : closestSets(ctx, mouseX, mouseY, x, sideY + 8, w) + 4;
            for (String k : List.of("tcgshop.binder.tip1", "tcgshop.binder.tip2", "tcgshop.binder.tip3", "tcgshop.binder.tip4", "tcgshop.binder.tip5")) {
                for (var line : font.split(Component.translatable(k), w)) {
                    if (y > sideY + sideH - 30 - bottomH) break;
                    ctx.drawString(font, line, x, y, Ui.MUTED);
                    y += 10;
                }
                y += 6;
            }
        } else {
            boolean special = c.key().startsWith("#");
            int count = ClientCollection.count(c.key());
            int cw = Math.min(w, 100), ch = Math.round(cw / Draw.CARD_RATIO);
            int maxH = sideH - 128 - bottomH - (special ? 10 : 0); // 8 px for the «Click to view larger» hint
            if (ch > maxH) { ch = Math.max(20, maxH); cw = Math.round(ch * Draw.CARD_RATIO); }
            int cx = detX + (detW - cw) / 2, cy = sideY + 8;
            if (count > 0) {
                if (c.rarity().tier > 0) Draw.glow(ctx, cx + cw / 2f, cy + ch / 2f, cw, ch, c.rarity().color, 0.3f);
                ctx.fill(cx + 3, cy + 4, cx + cw + 3, cy + ch + 4, 0x77000000);
                Draw.cardFace(ctx, c.texture(), c.stack(), cx, cy, cw, ch, 1f);
                if (c.rarity().tier > 0) Draw.holo(ctx, cx, cy, cw, ch, c.rarity().tier, c.rarity().color, t, mouseX);
            } else {
                Draw.cardFace(ctx, c.texture(), c.stack(), cx, cy, cw, ch, 1f);
                ctx.fill(cx, cy, cx + cw, cy + ch, 0xC0101018); // veiled: you don't own it yet
                Ui.outline(ctx, cx, cy, cw, ch, withAlpha(c.rarity().color, 0.5f));
                Ui.text(ctx, Component.translatable("tcgshop.binder.not_owned"), cx, cy + ch / 2 - 6, cw, 12, 0xFFDDDDDD, true);
            }
            // click on the large card = view it zoomed in
            boolean overCard = !titlesOpen && Ui.in(mouseX, mouseY, cx, cy, cw, ch);
            if (overCard) Ui.outline(ctx, cx - 1, cy - 1, cw + 2, ch + 2, 0xFFFFFFFF);
            final Catalog.Card zc = c;
            final boolean zOwned = count > 0;
            areas.add(new Area(cx, cy, cw, ch, () -> zoom.open(zc, zOwned)));
            Draw.scaledCentered(ctx, Component.translatable("tcgshop.binder.zoom_hint"), cx + cw / 2f, cy + ch + 2, 0.7f, overCard ? Ui.GOLD : Ui.MUTED);
            int y = cy + ch + 10;
            for (var line : font.split(c.name(), w)) {
                ctx.drawString(font, line, x, y, Ui.TEXT);
                y += 10;
            }
            ctx.drawString(font, Component.translatable("masterball.set." + c.set()), x, y, Ui.MUTED, false);
            y += 10;
            ctx.drawString(font, c.rarity().displayName(), x, y, c.rarity().color);
            y += 10;
            if (special) for (Component line : SpecialCards.lines(c.stack())) {
                ctx.drawString(font, line, x, y, 0xFFFFFFFF);
                y += 10;
            }
            long now = PriceService.priceCd(c.stack());
            long week = PriceService.priceCdOnDay(c.id(), c.rarity(), Math.max(0, PriceService.marketDay() - 7));
            long weekNow = PriceService.priceCd(c.id(), c.rarity());
            double ch7 = week == 0 ? 0 : (weekNow - week) * 100.0 / week; // trend of the card itself (variants follow it)
            ctx.drawString(font, Component.literal(PriceService.format(now)), x, y, Ui.GOLD);
            String trend = String.format(Locale.ROOT, "%+.1f%%", ch7);
            ctx.drawString(font, trend, x + w - font.width(trend), y, ch7 >= 0 ? 0xFF6BE07B : 0xFFFF6B6B);
            y += 10;
            ctx.drawString(font, Component.translatable("tcgshop.binder.have", count), x, y, count > 0 ? 0xFF8BE08B : Ui.MUTED);
            y += 13;
            String key = c.key();
            if (!special) { // the wishlist is per card, not per variant / slab
                boolean wished = ClientCollection.wishlist.contains(key);
                Component wl = Component.translatable(wished ? "tcgshop.binder.wished" : "tcgshop.binder.wish");
                Ui.button(ctx, x, y, w, 15, wl, mouseX, mouseY, true, wished ? 0xFFB0306A : 0xFF4A3550);
                areas.add(new Area(x, y, w, 15, () -> ClientCollection.action(BinderActionPayload.WISH_TOGGLE, key, 0)));
                y += 18;
            }
            int bw = (w - 4) / 2;
            Ui.button(ctx, x, y, bw, 15, Component.translatable("tcgshop.binder.take1"), mouseX, mouseY, count > 0, 0xFF2F6FB0);
            if (count > 0) areas.add(new Area(x, y, bw, 15, () -> ClientCollection.action(BinderActionPayload.WITHDRAW, key, 1)));
            Ui.button(ctx, x + bw + 4, y, bw, 15, Component.translatable("tcgshop.binder.take_all"), mouseX, mouseY, count > 0, 0xFF2F6FB0);
            if (count > 0) areas.add(new Area(x + bw + 4, y, bw, 15, () -> ClientCollection.action(BinderActionPayload.WITHDRAW, key, count)));
        }
        int by = sideY + sideH - 22;
        renderSelectionControls(ctx, mouseX, mouseY, x, w, by);
        Ui.button(ctx, x, by, w, 18, Component.translatable("tcgshop.binder.deposit"), mouseX, mouseY, true, 0xFF3C8A4A);
        areas.add(new Area(x, by, w, 18, () -> ClientCollection.action(BinderActionPayload.DEPOSIT_ALL, "", 0)));
        if (Ui.in(mouseX, mouseY, x, by, w, 18)) setTooltipForNextRenderPass(Component.translatable("tcgshop.binder.deposit_tip"));
    }

    /** Multi-select controls (1.0), stacked above the deposit button at y = by. */
    private void renderSelectionControls(GuiGraphics ctx, int mouseX, int mouseY, int x, int w, int by) {
        int bw = (w - 4) / 2, h = 15;
        int y = by - h - 4;
        Ui.button(ctx, x, y, bw, h, Component.translatable("tcgshop.binder.sel_all"), mouseX, mouseY, !visible.isEmpty(), 0xFF444A60);
        if (!visible.isEmpty()) areas.add(new Area(x, y, bw, h, this::selectAllVisible));
        if (Ui.in(mouseX, mouseY, x, y, bw, h)) setTooltipForNextRenderPass(Component.translatable("tcgshop.binder.sel_all_tip"));
        boolean any = !selection.isEmpty();
        Ui.button(ctx, x + bw + 4, y, bw, h, Component.translatable("tcgshop.binder.sel_clear"), mouseX, mouseY, any, 0xFF444A60);
        if (any) areas.add(new Area(x + bw + 4, y, bw, h, selection::clear));
        if (!any) return;
        y -= h + 3;
        Ui.button(ctx, x, y, bw, h, Component.translatable("tcgshop.binder.sel_take1"), mouseX, mouseY, true, 0xFF2F6FB0);
        areas.add(new Area(x, y, bw, h, () -> takeSelected(false)));
        if (Ui.in(mouseX, mouseY, x, y, bw, h)) setTooltipForNextRenderPass(Component.translatable("tcgshop.binder.sel_take1_tip"));
        Ui.button(ctx, x + bw + 4, y, bw, h, Component.translatable("tcgshop.binder.sel_take_all"), mouseX, mouseY, true, 0xFF2F6FB0);
        areas.add(new Area(x + bw + 4, y, bw, h, () -> takeSelected(true)));
        if (Ui.in(mouseX, mouseY, x + bw + 4, y, bw, h)) setTooltipForNextRenderPass(Component.translatable("tcgshop.binder.sel_take_all_tip"));
        int copies = 0;
        for (String k : selection) copies += ClientCollection.count(k);
        Component info = Component.translatable("tcgshop.binder.sel_info", selection.size(), copies);
        Draw.scaledCentered(ctx, info, x + w / 2f, y - 10, Math.min(1f, (w - 2f) / Math.max(1, font.width(info))), Ui.GOLD);
    }

    private void renderTitles(GuiGraphics ctx, int mouseX, int mouseY) {
        int w = Math.min(260, gridW - 20), h = Math.min(230, gridH - 10);
        int x = gridX + (gridW - w) / 2, y = gridY + 5;
        Ui.leather(ctx, x, y, w, h, 0xFF3A2650, 0xFF22162F);
        Ui.text(ctx, Component.translatable("tcgshop.binder.titles").withStyle(ChatFormatting.BOLD), x, y + 4, w, 12, Ui.GOLD, true);
        int ry = y + 22;
        List<String> options = new ArrayList<>();
        options.add("");
        options.addAll(ClientCollection.titles);
        if (ClientCollection.titles.isEmpty()) {
            for (var line : font.split(Component.translatable("tcgshop.binder.no_titles"), w - 16)) {
                ctx.drawString(font, line, x + 8, ry + 22, Ui.MUTED);
                ry += 10;
            }
        }
        for (String opt : options) {
            if (ry > y + h - 18) break;
            boolean sel = opt.equals(ClientCollection.title);
            Component label = opt.isEmpty() ? Component.translatable("tcgshop.binder.no_title") : Titles.display(opt);
            Ui.chip(ctx, x + 8, ry, w - 16, 15, label, sel, Ui.in(mouseX, mouseY, x + 8, ry, w - 16, 15), 0xFFB983FF, 0);
            areas.add(new Area(x + 8, ry, w - 16, 15, () -> ClientCollection.action(BinderActionPayload.TITLE_SET, opt, 0)));
            ry += 17;
        }
        Ui.button(ctx, x + w - 58, y + h - 20, 50, 15, Component.translatable("gui.done"), mouseX, mouseY, true, 0xFF3C8A4A);
        areas.add(new Area(x + w - 58, y + h - 20, 50, 15, () -> titlesOpen = false));
    }

    // ------------------------------------------------------------------ multi-select

    /** Click on a cell: Shift = select the range from the last clicked card; otherwise show it in the detail panel. */
    private void clickCell(String key) {
        if (hasShiftDown() && anchorKey != null && !anchorKey.equals(key)) {
            selectRange(anchorKey, key);
            return;
        }
        selectedCard = key;
        anchorKey = key;
    }

    private void toggle(String key) {
        if (ClientCollection.count(key) <= 0) return; // only cards you have can be taken out
        if (!selection.remove(key)) selection.add(key);
    }

    private void selectRange(String from, String to) {
        int a = -1, b = -1;
        for (int i = 0; i < visible.size(); i++) {
            String k = visible.get(i).key();
            if (k.equals(from)) a = i;
            if (k.equals(to)) b = i;
        }
        if (a < 0 || b < 0) { toggle(to); anchorKey = to; return; }
        for (int i = Math.min(a, b); i <= Math.max(a, b); i++) {
            String k = visible.get(i).key();
            if (ClientCollection.count(k) > 0) selection.add(k);
        }
        anchorKey = to;
    }

    private void selectAllVisible() {
        for (Catalog.Card c : visible) if (ClientCollection.count(c.key()) > 0) selection.add(c.key());
    }

    /** Sends the selection to the server in batches: 1 copy of each card, or every copy. */
    private void takeSelected(boolean all) {
        List<String> parts = new ArrayList<>();
        for (String k : selection) {
            int n = ClientCollection.count(k);
            if (n > 0) parts.add(k + "=" + (all ? n : 1));
        }
        StringBuilder sb = new StringBuilder();
        int entries = 0;
        for (String part : parts) {
            if (entries > 0 && (entries >= BATCH_ENTRIES || sb.length() + part.length() + 1 > BATCH_CHARS)) {
                ClientCollection.action(BinderActionPayload.WITHDRAW_MANY, sb.toString(), 0);
                sb.setLength(0);
                entries = 0;
            }
            if (entries > 0) sb.append(';');
            sb.append(part);
            entries++;
        }
        if (entries > 0) ClientCollection.action(BinderActionPayload.WITHDRAW_MANY, sb.toString(), 0);
        if (all) selection.clear();
    }

    /** Card cell under the mouse, or null. */
    private String cellAt(double mx, double my) {
        if (titlesOpen || !Ui.in(mx, my, gridX, gridY, gridW, gridH)) return null;
        int baseY = gridY + 5 - (int) scroll;
        for (Row row : rows) {
            if (row.cards == null) continue;
            int y = baseY + row.y;
            if (my < y || my >= y + CELL_H) continue;
            int x = gridX + 7;
            for (Catalog.Card c : row.cards) {
                if (Ui.in(mx, my, x, y, CELL_W, CELL_H)) return c.key();
                x += CELL_W + GAP;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ input

    private boolean isDoubleClick(String tabKey) {
        long now = System.currentTimeMillis();
        boolean dbl = tabKey.equals(lastClickedTab) && now - lastClickTime < 350;
        lastClickedTab = tabKey;
        lastClickTime = now;
        return dbl;
    }

    private void clearFresh(String filter) {
        Predicate<String> cleared = filter.equals("all") ? k -> true : k -> {
            Catalog.Card c = Catalog.card(k);
            if (c == null) return false;
            if (filter.startsWith("set:")) return c.set().equals(filter.substring(4));
            if (filter.startsWith("rarity:")) return c.rarity().family.key.equals(filter.substring(7))
                    && (mode != Mode.SET || selectedSet == null || c.set().equals(selectedSet));
            return false;
        };
        ClientCollection.fresh.removeIf(cleared);
        // clearing on purpose does remove them from «New» (unlike just looking at them)
        sessionNew.removeIf(cleared);
        glanced.removeIf(cleared);
        if (filter.startsWith("rarity:") && mode == Mode.SET && selectedSet != null) {
            List<String> ids = new ArrayList<>();
            for (Catalog.CardSet s : Catalog.sets())
                if (s.id().equals(selectedSet)) for (Catalog.Card c : s.cards()) if (c.rarity().family.key.equals(filter.substring(7))) ids.add(c.key());
            ClientCollection.action(BinderActionPayload.MARK_SEEN, String.join(",", ids), 0);
            return;
        }
        ClientCollection.action(BinderActionPayload.CLEAR_FRESH, filter, 0);
    }

    /** Switches tab; if you leave «New», the ones you looked at count as seen and are removed from the list. */
    private void switchMode(Mode next) {
        if (mode == Mode.NEW && next != Mode.NEW) {
            commitGlanced();
            sessionNew.retainAll(ClientCollection.fresh);
        }
        mode = next;
    }

    /** Cursor tracking: a new card counts as seen after SEEN_HOVER_MS of continuous hovering, never instantly. */
    private void trackHover(String key) {
        long now = System.currentTimeMillis();
        if (key == null) { hoverKey = null; return; }
        if (!key.equals(hoverKey)) { hoverKey = key; hoverSince = now; }
        if (!ClientCollection.fresh.contains(key)) return;
        glanced.add(key);
        if (now - hoverSince >= SEEN_HOVER_MS) seen(key, now);
    }

    private void seen(String key, long now) {
        if (ClientCollection.fresh.contains(key)) {
            ClientCollection.markSeen(key);
            fadeStart.put(key, now);
        }
        glanced.remove(key);
    }

    private void commitGlanced() {
        long now = System.currentTimeMillis();
        for (String k : new ArrayList<>(glanced)) seen(k, now);
        glanced.clear();
        ClientCollection.flushSeen();
    }

    private float fadeAlpha(String key) {
        Long st = fadeStart.get(key);
        if (st == null) return 0f;
        long dt = System.currentTimeMillis() - st;
        if (dt >= FADE_MS) { fadeStart.remove(key); return 0f; }
        return 1f - dt / (float) FADE_MS;
    }

    @Override
    public void removed() {
        commitGlanced(); // on close: the ones you looked at stay seen
        super.removed();
    }

    private EditBox focusedField() {
        if (search != null && search.isFocused()) return search;
        if (minField != null && minField.isFocused()) return minField;
        if (maxField != null && maxField.isFocused()) return maxField;
        return null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (zoom.isOpen()) return zoom.mouseClicked(mouseX, mouseY, button);
        for (EditBox f : List.of(search, minField, maxField)) {
            if (f.mouseClicked(mouseX, mouseY, button)) {
                for (EditBox o : List.of(search, minField, maxField)) if (o != f) o.setFocused(false);
                setFocused(f);
                return true;
            }
        }
        search.setFocused(false);
        minField.setFocused(false);
        maxField.setFocused(false);
        paintSelect = null;
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && hasControlDown()) { // Ctrl+click / Ctrl+drag: multi-select
            String key = cellAt(mouseX, mouseY);
            if (key != null && ClientCollection.count(key) > 0) {
                paintSelect = !selection.contains(key);
                toggle(key);
                anchorKey = key;
                return true;
            }
        }
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
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (zoom.isOpen()) return true;
        if (paintSelect != null) {
            String key = cellAt(mouseX, mouseY);
            if (key != null && ClientCollection.count(key) > 0) {
                if (paintSelect) selection.add(key);
                else selection.remove(key);
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (paintSelect != null) {
            paintSelect = null;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double h, double v) {
        if (zoom.isOpen()) return true;
        if (Ui.in(mouseX, mouseY, sideX, sideY, sideW, listH)) sideScroll -= v * 26;
        else scroll = Mth.clamp(scroll - v * (CELL_H + GAP), 0, Math.max(0, contentHeight - gridH + 8));
        return true;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (zoom.isOpen()) return true;
        EditBox f = focusedField();
        return f != null && f.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (zoom.isOpen()) return zoom.keyPressed(keyCode, scanCode);
        EditBox f = focusedField();
        if (f != null && keyCode != GLFW.GLFW_KEY_ESCAPE) return f.keyPressed(keyCode, scanCode, modifiers) || true;
        if (titlesOpen && keyCode == GLFW.GLFW_KEY_ESCAPE) { titlesOpen = false; return true; }
        if (keyCode == GLFW.GLFW_KEY_A && hasControlDown()) { selectAllVisible(); return true; }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && !selection.isEmpty()) { selection.clear(); return true; }
        if (dev.tcgshop.client.TcgShopClient.OPEN_BINDER.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}

package dev.tcgshop.client.screen;

import dev.tcgshop.client.binder.Ui;
import dev.tcgshop.compat.CobbleFurniesCompat;
import dev.tcgshop.luxury.LuxuryShop;
import dev.tcgshop.luxury.StatueTier;
import dev.tcgshop.network.FurniturePayloads;
import dev.tcgshop.pricing.PriceService;
import dev.tcgshop.shop.FurnitureShop;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.HoveredTooltipPositioner;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Panel de la tienda de muebles (CobbleFurnies) y peluches: carril de categorías, comprar / vender, buscador, rejilla
 * paginada con iconos y precios, cantidad y total. Se usa dentro de la pantalla del Vendedor de muebles
 * (FurnitureScreen), en la pestaña Muebles del Mayorista (StoreScreen) y como app del teléfono (PhoneScreen).
 * El catálogo y los precios los manda el servidor (FurniturePayloads.Open); las compras/ventas se validan allí.
 */
public class FurnitureApp {
    /** excl = artículo de Exclusivos (estatua, lux != null, o la Llave de la Mansión): precio fijo, de uno en uno. */
    private record Entry(Identifier id, ItemStack stack, String cat, long buy, long sell, String name, @Nullable StatueTier lux, boolean excl) {}

    /** Color de la ficha de la Llave de la Mansión (dorado). */
    private static final int MANSION_COLOR = 0xFFC640;

    /** Panel que espera / muestra datos ahora mismo (el último que se creó o pidió el catálogo). */
    @Nullable private static FurnitureApp active;

    private static final int ACCENT = 0xFFC08A52, SURF = 0xF0171A26, SURF2 = 0xF01F2333, LINE = 0xFF2C3149;
    private static final int TILE_W = 44, TILE_H = 52, GAP = 4;

    public final int source;
    private final Screen host;
    private final List<Entry> entries = new ArrayList<>();
    private final List<int[]> rects = new ArrayList<>();
    private final List<Runnable> clicks = new ArrayList<>();
    private final TextFieldWidget search;
    private boolean loaded, selling;
    /** Motivo por el que el servidor no nos atiende (clave de traducción) o null. */
    @Nullable private String error;
    private long balance = -1;
    private String category = "all";
    private int page, qty = 1;
    @Nullable private Identifier selected;
    private double pageAt = -10, armedAt = -10;
    // zonas de este fotograma (para la rueda del ratón y el buscador)
    private int sbX, sbY, sbW, gridX, gridY, gridW, gridH, pages = 1;

    public FurnitureApp(int source, Screen host) {
        this.source = source;
        this.host = host;
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        search = new TextFieldWidget(tr, 0, 0, 100, 12, Text.translatable("tcgshop.binder.search"));
        search.setPlaceholder(Text.translatable("tcgshop.binder.search").formatted(Formatting.DARK_GRAY));
        search.setDrawsBackground(false);
        search.setMaxLength(48);
        search.setChangedListener(s -> { page = 0; pageAt = now(); });
        active = this;
    }

    private static double now() { return System.nanoTime() / 1e9; }

    /**
     * ¿El servidor vende estatuas de lujo? Lo manda él al entrar y tras /tcgshop reload (FurniturePayloads.Open con
     * source SOURCE_LUXURY_SYNC); nunca se mira la config local del cliente.
     */
    private static boolean serverLuxury;

    /**
     * ¿Hay algo que vender en el teléfono? CobbleFurnies / Pokéblocks o las estatuas de lujo del servidor (si no, no hay
     * app). El Mayorista no vende estatuas: su pestaña Muebles mira sólo CobbleFurniesCompat.available().
     */
    public static boolean available() {
        return CobbleFurniesCompat.available() || serverLuxury;
    }

    /** Info de las estatuas según el servidor (precios del tooltip incluidos). */
    private static void acceptLuxurySync(FurniturePayloads.Open p) {
        for (StatueTier t : StatueTier.values()) t.clientPrice = -1;
        dev.tcgshop.luxury.house.MansionKeyItem.clientPrice = -1;
        boolean any = false;
        for (int i = 0; i < p.ids().size(); i++) {
            Identifier lid = Identifier.tryParse(p.ids().get(i));
            if (LuxuryShop.isMansionKey(lid)) {
                dev.tcgshop.luxury.house.MansionKeyItem.clientPrice = p.buy().get(i);
                any = true;
                continue;
            }
            StatueTier t = LuxuryShop.tierOf(lid);
            if (t == null) continue;
            t.clientPrice = p.buy().get(i);
            any = true;
        }
        serverLuxury = any;
    }

    /** Olvida la info del servidor (al desconectarse). */
    public static void resetServerInfo() {
        serverLuxury = false;
        for (StatueTier t : StatueTier.values()) t.clientPrice = -1;
        dev.tcgshop.luxury.house.MansionKeyItem.clientPrice = -1;
    }

    /** ¿El catálogo recibido trae estatuas de lujo? (botón Exclusivos del Vendedor de muebles) */
    public boolean hasLuxury() {
        for (Entry e : entries) if (e.excl) return true;
        return false;
    }

    /** Salta a una categoría (p.ej. "luxury" desde el botón Exclusivos del Vendedor). */
    public void showCategory(String cat) {
        if (selling) setSelling(false);
        category = cat;
        page = 0;
        pageAt = now();
    }

    /** Icono para la app / pestaña. */
    public static ItemStack icon() {
        Identifier chair = Identifier.of(CobbleFurniesCompat.FURNIES, "poke_ball_chair");
        if (Registries.ITEM.containsId(chair)) return new ItemStack(Registries.ITEM.get(chair));
        List<Identifier> all = CobbleFurniesCompat.all();
        return all.isEmpty() ? new ItemStack(Items.PAINTING) : new ItemStack(Registries.ITEM.get(all.get(0)));
    }

    /** Pide el catálogo al servidor (al abrir la pestaña / app). */
    public void request() {
        active = this;
        search.setFocused(false);
        ClientPlayNetworking.send(new FurniturePayloads.Request(source));
    }

    /** Receptor del cliente (registrar en TcgShopClient): rellena el panel abierto o abre la pantalla del Vendedor. */
    public static void receive(FurniturePayloads.Open p) {
        if (p.source() == FurniturePayloads.SOURCE_LUXURY_SYNC) {
            acceptLuxurySync(p);
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        FurnitureApp a = active;
        if (a != null && a.source == p.source() && mc.currentScreen == a.host) {
            a.accept(p);
            return;
        }
        if (p.open() && p.full() && p.source() != -1) {
            FurnitureScreen s = new FurnitureScreen(p.source());
            s.app().accept(p);
            mc.setScreen(s);
        }
    }

    void accept(FurniturePayloads.Open p) {
        balance = p.balance();
        if (!p.error().isEmpty()) {
            // rechazo del servidor: se deja de esperar y se enseña el motivo
            error = p.error();
            entries.clear();
            selected = null;
            loaded = true;
            return;
        }
        if (!p.full()) return;
        error = null;
        entries.clear();
        for (int i = 0; i < p.ids().size(); i++) {
            Identifier id = Identifier.tryParse(p.ids().get(i));
            if (id == null || !Registries.ITEM.containsId(id)) continue;
            ItemStack st = new ItemStack(Registries.ITEM.get(id));
            if (st.isEmpty()) continue;
            StatueTier lux = LuxuryShop.tierOf(id);
            if (lux != null) lux.clientPrice = p.buy().get(i); // para el tooltip del objeto
            boolean key = LuxuryShop.isMansionKey(id);
            if (key) dev.tcgshop.luxury.house.MansionKeyItem.clientPrice = p.buy().get(i);
            entries.add(new Entry(id, st, FurnitureShop.category(id), p.buy().get(i), p.sell().get(i),
                    st.getName().getString().toLowerCase(Locale.ROOT), lux, lux != null || key));
        }
        loaded = true;
        if (selected != null && entries.stream().noneMatch(e -> e.id.equals(selected))) selected = null;
    }

    // ------------------------------------------------------------------ datos

    /** Unidades en el inventario principal. sellable = sólo las que la tienda recompra (peluches o muebles con marca). */
    private Map<Item, Integer> owned(boolean sellable) {
        Map<Item, Integer> m = new HashMap<>();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return m;
        var inv = mc.player.getInventory();
        for (int i = 0; i < 36; i++) { // igual que el servidor: sólo el inventario principal
            ItemStack st = inv.getStack(i);
            if (st.isEmpty() || (sellable && !FurnitureShop.isShopStock(st))) continue;
            m.merge(st.getItem(), st.getCount(), Integer::sum);
        }
        return m;
    }

    private boolean matches(Entry e, Map<Item, Integer> owned, String cat, String q) {
        if (!cat.equals("all") && !e.cat.equals(cat)) return false;
        if (selling && owned.getOrDefault(e.stack.getItem(), 0) <= 0) return false;
        return q.isEmpty() || e.name.contains(q) || e.id.getPath().contains(q);
    }

    private List<Entry> visible(Map<Item, Integer> owned) {
        String q = search.getText().trim().toLowerCase(Locale.ROOT);
        List<Entry> out = new ArrayList<>();
        for (Entry e : entries) if (matches(e, owned, category, q)) out.add(e);
        return out;
    }

    @Nullable
    private Entry find(@Nullable Identifier id) {
        if (id == null) return null;
        for (Entry e : entries) if (e.id.equals(id)) return e;
        return null;
    }

    private void area(int x, int y, int w, int h, Runnable r) {
        rects.add(new int[]{x, y, w, h});
        clicks.add(r);
    }

    // ------------------------------------------------------------------ dibujo

    public void render(DrawContext ctx, int x, int y, int w, int h, int mouseX, int mouseY, float delta) {
        rects.clear();
        clicks.clear();
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        Ui.roundGradient(ctx, x, y, w, h, SURF2, SURF);
        Ui.outline(ctx, x, y, w, h, LINE);
        if (!loaded) {
            int dots = (int) (now() * 3) % 4;
            Ui.text(ctx, Text.translatable("tcgshop.furn.loading").append(".".repeat(dots)), x, y + h / 2 - 5, w, 10, Ui.MUTED, false);
            return;
        }
        if (error != null || entries.isEmpty()) {
            Ui.text(ctx, Text.translatable(error != null ? error : "tcgshop.furn.unavailable"), x, y + h / 2 - 5, w, 10, Ui.MUTED, false);
            return;
        }
        // al vender sólo cuenta lo que la tienda recompra (comprado aquí y sin colocar)
        Map<Item, Integer> owned = owned(selling);
        String q = search.getText().trim().toLowerCase(Locale.ROOT);

        // ---- carril de categorías (con contador según el modo y la búsqueda)
        // filas más bajas si no caben todas las categorías (14 desde que existe Exclusivos); mínimo 11 px
        int railW = MathHelper.clamp(w / 5, 74, 104);
        int rowH = MathHelper.clamp((h - 11) / FurnitureShop.CATEGORIES.length, 11, 15), textDy = (rowH - 7) / 2;
        int rx = x + 4, ry = y + 4;
        Ui.roundGradient(ctx, rx, ry, railW, h - 8, 0x70000000, 0x50000000);
        for (int i = 0; i < FurnitureShop.CATEGORIES.length; i++) {
            String c = FurnitureShop.CATEGORIES[i];
            int yy = ry + 3 + i * rowH;
            if (yy + rowH > y + h - 4) break;
            int n = 0;
            for (Entry e : entries) if (matches(e, owned, c, q)) n++;
            if (n == 0 && !c.equals("all") && !c.equals(category)) {
                // categoría vacía: se ve apagada pero sigue ahí para que el carril no salte
                ctx.drawText(tr, Ui.ellipsis(Text.translatable("tcgshop.furn.cat." + c).getString(), railW - 26), rx + 7, yy + textDy, 0xFF555A6E, false);
                continue;
            }
            boolean sel = c.equals(category), hov = Ui.in(mouseX, mouseY, rx + 2, yy, railW - 4, rowH - 1);
            if (sel) {
                Ui.rounded(ctx, rx + 2, yy, railW - 4, rowH - 1, 4, Draw.withAlpha(ACCENT, 0.45f));
                Ui.roundedOutline(ctx, rx + 2, yy, railW - 4, rowH - 1, 4, ACCENT);
                ctx.fill(rx + 4, yy + 3, rx + 5, yy + rowH - 4, 0xFFFFFFFF);
            } else if (hov) Ui.rounded(ctx, rx + 2, yy, railW - 4, rowH - 1, 4, 0x22FFFFFF);
            String label = Ui.ellipsis(Text.translatable("tcgshop.furn.cat." + c).getString(), railW - 30);
            ctx.drawText(tr, label, rx + 8, yy + textDy, sel ? 0xFFFFFFFF : hov ? 0xFFE8EAF2 : 0xFFB8BCCB, sel);
            String cnt = String.valueOf(n);
            ctx.drawText(tr, cnt, rx + railW - 6 - tr.getWidth(cnt), yy + textDy, sel ? Ui.GOLD : 0xFF8A8FA8, false);
            area(rx + 2, yy, railW - 4, rowH - 1, () -> { category = c; page = 0; pageAt = now(); });
        }

        // ---- cabecera: comprar / vender, buscador y saldo
        int cx = x + railW + 10, cw = w - railW - 14, hy = y + 5;
        Text tBuy = Text.translatable("tcgshop.furn.mode_buy"), tSell = Text.translatable("tcgshop.furn.mode_sell");
        int bw = Math.max(Ui.chipWidth(tBuy), Ui.chipWidth(tSell)) + 4;
        Ui.chip(ctx, cx, hy, bw, 14, tBuy, !selling, Ui.in(mouseX, mouseY, cx, hy, bw, 14), ACCENT, 0);
        area(cx, hy, bw, 14, () -> setSelling(false));
        Ui.chip(ctx, cx + bw + 3, hy, bw, 14, tSell, selling, Ui.in(mouseX, mouseY, cx + bw + 3, hy, bw, 14), 0xFF4CAF50, 0);
        area(cx + bw + 3, hy, bw, 14, () -> setSelling(true));
        Text bal = balance >= 0 ? Text.translatable("tcgshop.furn.balance", PriceService.format(balance)) : Text.empty();
        int balW = tr.getWidth(bal);
        ctx.drawText(tr, bal, cx + cw - balW, hy + 3, Ui.GOLD, true);
        sbX = cx + bw * 2 + 10;
        sbW = Math.max(40, Math.min(140, cx + cw - balW - 8 - sbX));
        sbY = hy;
        Ui.round(ctx, sbX, sbY, sbW, 14, search.isFocused() ? 0xEE0E1018 : 0xAA0E1018);
        Ui.outline(ctx, sbX, sbY, sbW, 14, search.isFocused() ? 0xFFFFFFFF : 0x66FFFFFF);
        ctx.drawText(tr, "⌕", sbX + 4, sbY + 3, 0xFFAAAAAA, false);
        search.setX(sbX + 14);
        search.setY(sbY + 3);
        search.setWidth(sbW - 18);
        search.render(ctx, mouseX, mouseY, delta);

        // ---- rejilla paginada
        int detailH = 40, pagerH = 14;
        gridX = cx;
        gridY = hy + 20;
        gridW = cw;
        gridH = Math.max(TILE_H, y + h - 4 - detailH - pagerH - gridY);
        int cols = Math.max(1, (gridW + GAP) / (TILE_W + GAP));
        int rows = Math.max(1, (gridH + GAP) / (TILE_H + GAP));
        int per = cols * rows;
        List<Entry> vis = visible(owned);
        pages = Math.max(1, (vis.size() + per - 1) / per);
        page = MathHelper.clamp(page, 0, pages - 1);
        int gx = gridX + (gridW - (cols * (TILE_W + GAP) - GAP)) / 2;
        float appear = (float) MathHelper.clamp((now() - pageAt) / 0.18, 0, 1);
        Entry hovered = null;
        if (vis.isEmpty()) {
            Ui.text(ctx, Text.translatable(selling ? "tcgshop.furn.nothing_to_sell" : "tcgshop.furn.empty"), gridX, gridY + gridH / 2 - 5, gridW, 10, Ui.MUTED, false);
            if (selling) // por qué no sale lo fabricado o lo ya colocado
                Ui.text(ctx, Text.translatable("tcgshop.furn.buyback_rule"), gridX, gridY + gridH / 2 + 8, gridW, 10, 0xFF6E7390, false);
        }
        for (int k = page * per; k < Math.min(vis.size(), (page + 1) * per); k++) {
            Entry e = vis.get(k);
            int slot = k - page * per;
            int tx = gx + (slot % cols) * (TILE_W + GAP), ty = gridY + (slot / cols) * (TILE_H + GAP);
            ty += Math.round((1 - appear) * 6); // pequeña entrada al cambiar de página
            boolean sel = e.id.equals(selected), hov = Ui.in(mouseX, mouseY, tx, ty, TILE_W, TILE_H);
            if (hov) hovered = e;
            Ui.rounded(ctx, tx + 1, ty + 2, TILE_W, TILE_H, 4, 0x40000000);
            if (e.excl) {
                // Exclusivos: fondo con el color del nivel y borde que late un poco
                int c = 0xFF000000 | (e.lux != null ? e.lux.color : MANSION_COLOR);
                float pulse = 0.55f + 0.25f * (float) Math.sin(now() * 3 + (e.lux != null ? e.lux.ordinal() : 7));
                Ui.roundedGradient(ctx, tx, ty - (hov ? 1 : 0), TILE_W, TILE_H, 4,
                        Draw.withAlpha(c, sel ? 0.55f : hov ? 0.42f : 0.30f), sel ? 0xFF2A1A08 : 0xFF14101E);
                Ui.roundedOutline(ctx, tx, ty - (hov ? 1 : 0), TILE_W, TILE_H, 4, sel ? Ui.GOLD : Draw.withAlpha(c, hov ? 1f : pulse));
            } else {
                Ui.roundedGradient(ctx, tx, ty - (hov ? 1 : 0), TILE_W, TILE_H, 4,
                        sel ? 0xFF5A4028 : hov ? 0xFF33384E : 0xFF232739, sel ? 0xFF3A2816 : 0xFF181B27);
                Ui.roundedOutline(ctx, tx, ty - (hov ? 1 : 0), TILE_W, TILE_H, 4, sel ? Ui.GOLD : hov ? 0xCCFFFFFF : 0xFF2E3348);
            }
            var m = ctx.getMatrices();
            m.push();
            m.translate(tx + TILE_W / 2f, ty + 17 - (hov ? 1 : 0), 0);
            float s = hov ? 1.75f : 1.6f;
            m.scale(s, s, 1);
            ctx.drawItem(e.stack, -8, -8);
            m.pop();
            int have = owned.getOrDefault(e.stack.getItem(), 0);
            if (have > 0) {
                m.push();
                m.translate(0, 0, 250); // por encima del objeto
                String hs = "x" + have;
                ctx.drawText(tr, hs, tx + TILE_W - 3 - tr.getWidth(hs), ty + 3, 0xFF8BE08B, true);
                m.pop();
            }
            long price = selling ? e.sell : e.buy;
            String ps = PriceService.format(price);
            if (tr.getWidth(ps) > TILE_W - 4) ps = compact(price); // 1.000.000.000 ₽ no cabe en la ficha
            Ui.text(ctx, Text.literal(ps), tx + 1, ty + TILE_H - 13, TILE_W - 2, 10, selling ? 0xFF8BE08B : Ui.GOLD, true);
            area(tx, ty, TILE_W, TILE_H, () -> {
                if (!e.id.equals(selected)) { selected = e.id; qty = 1; armedAt = -10; }
            });
        }

        // ---- paginador
        int py = gridY + gridH + 1;
        String pl = (page + 1) + " / " + pages;
        int plw = tr.getWidth(pl), pcx = gridX + gridW / 2;
        Ui.button(ctx, pcx - plw / 2 - 22, py, 16, 12, Text.literal("‹"), mouseX, mouseY, page > 0, 0xFF4A5070);
        if (page > 0) area(pcx - plw / 2 - 22, py, 16, 12, () -> turn(-1));
        ctx.drawText(tr, pl, pcx - plw / 2, py + 2, 0xFFDDE0EA, false);
        Ui.button(ctx, pcx + plw / 2 + 6, py, 16, 12, Text.literal("›"), mouseX, mouseY, page < pages - 1, 0xFF4A5070);
        if (page < pages - 1) area(pcx + plw / 2 + 6, py, 16, 12, () -> turn(1));
        Text found = Text.translatable("tcgshop.furn.found", vis.size());
        ctx.drawText(tr, found, gridX + 2, py + 2, 0xFF8A8FA8, false);

        // ---- detalle: cantidad, total y botón
        detail(ctx, tr, cx, y + h - detailH - 2, cw, detailH - 2, owned, mouseX, mouseY);

        if (hovered != null) {
            List<Text> tip = new ArrayList<>();
            tip.add(hovered.stack.getName());
            tip.add(Text.translatable("tcgshop.furn.cat." + hovered.cat).formatted(Formatting.DARK_GRAY));
            tip.add(Text.translatable("tcgshop.furn.tip_buy", PriceService.format(hovered.buy)).formatted(Formatting.GOLD));
            if (hovered.lux != null) {
                StatueTier t = hovered.lux;
                tip.add(Text.translatable("tcgshop.lux.tip_tier", t.index(), StatueTier.values().length).withColor(t.color));
                tip.add(Text.translatable("tcgshop.lux.flavor." + t.key).formatted(Formatting.DARK_GRAY, Formatting.ITALIC));
                tip.add(Text.translatable("tcgshop.lux.tip_size", t.heightBlocks()).formatted(Formatting.GRAY));
                tip.add(Text.translatable("tcgshop.lux.tip_no_resale").formatted(Formatting.RED));
                tooltip(tip);
                return;
            }
            if (hovered.excl) { // Llave de la Mansión
                tip.add(Text.translatable("tcgshop.mansion.tip_size").withColor(MANSION_COLOR));
                tip.add(Text.translatable("tcgshop.mansion.flavor").formatted(Formatting.DARK_GRAY, Formatting.ITALIC));
                tip.add(Text.translatable("tcgshop.lux.tip_no_resale").formatted(Formatting.RED));
                tooltip(tip);
                return;
            }
            tip.add(Text.translatable("tcgshop.furn.tip_sell", PriceService.format(hovered.sell)).formatted(Formatting.GREEN));
            if (!CobbleFurniesCompat.isPlush(hovered.id))
                tip.add(Text.translatable("tcgshop.furn.buyback_rule").formatted(Formatting.DARK_GRAY, Formatting.ITALIC));
            int have = owned.getOrDefault(hovered.stack.getItem(), 0);
            if (have > 0) tip.add(Text.translatable("tcgshop.furn.owned", have).formatted(Formatting.GRAY));
            tooltip(tip);
        }
    }

    private void detail(DrawContext ctx, TextRenderer tr, int x, int y, int w, int h, Map<Item, Integer> owned, int mouseX, int mouseY) {
        Ui.roundGradient(ctx, x, y, w, h, 0xFF2A2E42, 0xFF1C1F2D);
        Ui.outline(ctx, x, y, w, h, LINE);
        Entry e = find(selected);
        if (e == null || (selling && owned.getOrDefault(e.stack.getItem(), 0) <= 0)) {
            Ui.text(ctx, Text.translatable(selling ? "tcgshop.furn.hint_sell" : "tcgshop.furn.hint_buy"), x, y, w, h, Ui.MUTED, false);
            return;
        }
        int have = owned.getOrDefault(e.stack.getItem(), 0);
        int maxQty = e.excl ? 1 : selling ? Math.max(1, have) : 64; // las estatuas, de una en una
        qty = MathHelper.clamp(qty, 1, maxQty);
        long unit = selling ? e.sell : e.buy, total = unit * qty;
        ctx.drawItem(e.stack, x + 6, y + (h - 16) / 2);
        int btnW = Math.min(118, Math.max(84, w / 4));
        int ctrlW = 16 + 28 + 16 + (selling ? 34 : 0) + 6;
        int textW = w - 30 - btnW - ctrlW - 12;
        ctx.drawText(tr, Ui.ellipsis(e.stack.getName().getString(), Math.max(20, textW)), x + 26, y + 6, Ui.TEXT, true);
        Text sub = Text.translatable("tcgshop.furn.unit", PriceService.format(unit)).append("  ")
                .append(Text.translatable("tcgshop.furn.owned", have));
        ctx.drawText(tr, Ui.ellipsis(sub.getString(), Math.max(20, textW)), x + 26, y + 20, 0xFF9AA0B8, false);
        // cantidad (Mayús = de 10 en 10)
        int qx = x + w - btnW - 8 - ctrlW, qy = y + (h - 16) / 2;
        boolean shift = Screen.hasShiftDown();
        Ui.button(ctx, qx, qy, 16, 16, Text.literal("-"), mouseX, mouseY, qty > 1, 0xFF444A60);
        area(qx, qy, 16, 16, () -> { qty = Math.max(1, qty - (Screen.hasShiftDown() ? 10 : 1)); armedAt = -10; });
        Ui.text(ctx, Text.literal("x" + qty), qx + 16, qy, 28, 16, 0xFFFFFFFF, true);
        Ui.button(ctx, qx + 44, qy, 16, 16, Text.literal("+"), mouseX, mouseY, qty < maxQty, 0xFF444A60);
        int mq = maxQty;
        area(qx + 44, qy, 16, 16, () -> { qty = Math.min(mq, qty + (Screen.hasShiftDown() ? 10 : 1)); armedAt = -10; });
        if (selling) {
            Ui.button(ctx, qx + 62, qy, 32, 16, Text.translatable("tcgshop.furn.all"), mouseX, mouseY, qty < maxQty, 0xFF3C6A44);
            area(qx + 62, qy, 32, 16, () -> { qty = mq; armedAt = -10; });
        }
        if (shift && Ui.in(mouseX, mouseY, qx, qy, 60, 16))
            tooltip(List.of(Text.translatable("tcgshop.furn.shift_hint")));
        // botón: ventas grandes piden un segundo clic para confirmar
        boolean afford = selling || balance < 0 || total <= balance;
        boolean big = total >= 100_000;
        boolean armed = big && now() - armedAt < 3;
        int bx = x + w - btnW - 6, by = y + 5, bh = h - 10;
        Text label = Text.translatable(armed ? "tcgshop.furn.confirm" : selling ? "tcgshop.furn.sell_btn" : "tcgshop.furn.buy_btn",
                PriceService.format(total));
        Ui.button(ctx, bx, by, btnW, bh, label, mouseX, mouseY, afford, armed ? 0xFFB02020 : selling ? 0xFF2E9C5A : 0xFFD39B1A);
        if (!afford && Ui.in(mouseX, mouseY, bx, by, btnW, bh))
            tooltip(List.of(Text.translatable("tcgshop.furn.cant_afford").formatted(Formatting.RED)));
        if (afford) {
            Identifier id = e.id;
            int q = qty;
            boolean sell = selling;
            long confirmedUnit = unit; // el servidor rechaza la compra si su precio ya no es este
            area(bx, by, btnW, bh, () -> {
                if (big && now() - armedAt >= 3) { armedAt = now(); return; }
                armedAt = -10;
                ClientPlayNetworking.send(new FurniturePayloads.Trade(source, id.toString(), q, sell, confirmedUnit));
                if (sell) qty = 1;
            });
        }
    }

    /**
     * Tooltip diferido: se lo pasamos a la pantalla actual para que lo pinte después de todo (fuera del recorte del
     * teléfono y por encima del resto), en vez de ctx.drawTooltip dentro del scissor.
     */
    private static void tooltip(List<Text> lines) {
        Screen screen = MinecraftClient.getInstance().currentScreen;
        if (screen == null || lines.isEmpty()) return;
        List<OrderedText> out = new ArrayList<>(lines.size());
        for (Text t : lines) out.add(t.asOrderedText());
        screen.setTooltip(out, HoveredTooltipPositioner.INSTANCE, true);
    }

    /** Precio corto para las fichas: 10k, 1M, 1.000M... */
    private static String compact(long v) {
        String suf = dev.tcgshop.TcgShopConfig.INSTANCE.currencySuffix;
        if (v >= 1_000_000L && v % 1_000_000L == 0) return PriceService.format(v / 1_000_000L).replace(suf, "") + "M" + suf;
        if (v >= 1_000_000L) return String.format(Locale.ROOT, "%.1fM", v / 1_000_000.0) + suf;
        if (v >= 10_000L) return (v / 1_000L) + "k" + suf;
        return PriceService.format(v);
    }

    private void setSelling(boolean s) {
        if (selling == s) return;
        selling = s;
        page = 0;
        qty = 1;
        armedAt = -10;
        pageAt = now();
    }

    private void turn(int d) {
        int np = MathHelper.clamp(page + d, 0, pages - 1);
        if (np != page) { page = np; pageAt = now(); }
    }

    // ------------------------------------------------------------------ entrada

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (loaded && Ui.in(mouseX, mouseY, sbX, sbY, sbW, 14)) {
            search.setFocused(true);
            search.mouseClicked(mouseX, mouseY, button);
            return true;
        }
        search.setFocused(false);
        if (button != 0) return false;
        for (int i = rects.size() - 1; i >= 0; i--) {
            int[] r = rects.get(i);
            if (Ui.in(mouseX, mouseY, r[0], r[1], r[2], r[3])) {
                clicks.get(i).run();
                return true;
            }
        }
        return false;
    }

    /** Rueda: pasa de página sobre la rejilla. */
    public boolean mouseScrolled(double mouseX, double mouseY, double v) {
        if (!loaded || v == 0) return false;
        turn(v > 0 ? -1 : 1);
        return true;
    }

    public boolean charTyped(char chr, int modifiers) {
        return search.isFocused() && search.charTyped(chr, modifiers);
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (search.isFocused()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_ENTER) {
                search.setFocused(false);
                return true;
            }
            search.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_PAGE_UP) { turn(-1); return true; }
        if (keyCode == GLFW.GLFW_KEY_RIGHT || keyCode == GLFW.GLFW_KEY_PAGE_DOWN) { turn(1); return true; }
        return false;
    }

    /** Quita el foco del buscador (al cambiar de app/pestaña). */
    public void unfocus() {
        search.setFocused(false);
    }

    public boolean searchFocused() {
        return search.isFocused();
    }
}

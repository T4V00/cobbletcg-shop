package dev.tcgshop.client.screen;

import dev.tcgshop.card.Rarity;
import dev.tcgshop.client.ShopClient;
import dev.tcgshop.client.binder.Catalog;
import dev.tcgshop.client.binder.ClientCollection;
import dev.tcgshop.client.binder.StylePainter;
import dev.tcgshop.client.binder.Ui;
import dev.tcgshop.collection.Titles;
import dev.tcgshop.item.TcgItems;
import dev.tcgshop.network.BinderActionPayload;
import dev.tcgshop.network.StaffPayload;
import dev.tcgshop.pricing.PriceService;
import dev.tcgshop.shop.BoosterBox;
import dev.tcgshop.shop.ShopServer;
import dev.tcgshop.shop.ShopZones;
import dev.tcgshop.shop.TcgBlocks;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.advancement.AdvancementsScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

import static dev.tcgshop.client.screen.Draw.withAlpha;

/**
 * Teléfono TCG. La pantalla de inicio va en vertical (reloj, widgets de caja, reputación y colección, apps con
 * iconos 3D y dock); al abrir una app el teléfono gira a horizontal con una animación para dejar sitio:
 * barra superior, carril lateral de secciones y panel principal.
 *  · Mercado      TODO lo del Mayorista + sobres + peluches + Ruleta Poké, por categorías, con buscador y orden.
 *  · Precios      buscador, filtro de set y de rareza, ficha con la carta y gráfica de 30 días.
 *  · Mi tienda    resumen, personal (cajero / guardia), seguridad, reputación y reseñas de los clientes.
 *  · Colección (Binder General) · Títulos · Logros.
 */
public class PhoneScreen extends Screen {
    private enum App { HOME, MARKET, PRICES, SHOP, TITLES, GRADE, LEAGUE, SELL, FRIENDS, DISPLAYS }

    private record Area(int x, int y, int w, int h, Runnable action) {}

    private record AppDef(String key, int color, Supplier<ItemStack> icon) {}

    private static final int SURF = 0xF0171A26, SURF2 = 0xF01F2333, LINE = 0xFF2C3149;

    private final CatalogPanel market;
    private final List<Area> areas = new ArrayList<>();
    private final List<AppDef> apps = new ArrayList<>();
    private App app = App.HOME;
    private double morphStart = -10, appOpened = -10;
    private float morphFrom, morphTo;
    private TextFieldWidget search;
    private String priceCard, priceSet = "", shopTab = "summary", gradeTab = "send";
    private final java.util.Set<Integer> gradeSel = new java.util.LinkedHashSet<>();
    private int gradeService = 1;
    private String leagueTab = "play";
    /** Submenú de Venta rápida: cartas del inventario o del Binder. */
    private String sellTab = "inv";
    private final CosmeticsPanel cosPanel = new CosmeticsPanel();
    private int aiLevel = 1;
    private double lastLeagueSync;
    private final List<String> deckEdit = new ArrayList<>();
    private boolean deckDirty;

    private Rarity priceRarity;
    // filtro y orden de precios (app Precios)
    private int priceBand, priceSort;
    private static final long[] BAND_MIN = {0, 0, 1_000, 10_000, 100_000}, BAND_MAX = {Long.MAX_VALUE, 1_000, 10_000, 100_000, Long.MAX_VALUE};
    private static final String[] BAND_LABEL = {"all", "<1k", "1k-10k", "10k-100k", ">100k"};
    private final java.util.Map<String, Long> priceCache = new java.util.HashMap<>();
    private long priceCacheDay = Long.MIN_VALUE;
    // venta rápida
    private final CardPickGrid quickSell = new CardPickGrid();
    private double sellArmedAt = -10;
    private int zoneIndex;
    private double listScroll;

    // marco y pantalla de este fotograma
    private int fx, fy, fw, fh, sx, sy, sw, sh;

    public PhoneScreen(List<ItemStack> goods, List<Long> prices) {
        super(Text.translatable("item.tcgshop.tcg_phone"));
        this.market = new CatalogPanel(-1, goods, prices);
        apps.add(new AppDef("market", 0xFFD39B1A, () -> {
            List<Catalog.CardSet> s = Catalog.sets();
            return BoosterBox.of(s.isEmpty() ? "mew151" : s.get(0).id());
        }));
        apps.add(new AppDef("prices", 0xFF2E9C5A, () -> ItemStack.EMPTY));
        apps.add(new AppDef("shop", 0xFF2F6FB0, () -> new ItemStack(TcgBlocks.CASH_REGISTER_ITEM)));
        apps.add(new AppDef("collection", 0xFFB3262E, () -> new ItemStack(TcgItems.GENERAL_BINDER)));
        apps.add(new AppDef("titles", 0xFF8E44AD, () -> new ItemStack(Items.NAME_TAG)));
        apps.add(new AppDef("grade", 0xFF1E8FA8, () -> ItemStack.EMPTY));
        apps.add(new AppDef("league", 0xFFC62828, () -> new ItemStack(TcgBlocks.PLAY_TABLE_ITEM)));
        apps.add(new AppDef("advancements", 0xFFE07A1F, () -> new ItemStack(Items.NETHER_STAR)));
        apps.add(new AppDef("sell", 0xFFD9472B, () -> new ItemStack(Items.GOLD_INGOT)));
        apps.add(new AppDef("displays", 0xFF5C6BC0, () -> new ItemStack(TcgBlocks.DISPLAY_CASE_ITEM)));
        apps.add(new AppDef("friends", 0xFF26A69A, () -> new ItemStack(Items.PLAYER_HEAD)));
        quickSell.price = st -> dev.tcgshop.shop.CardSales.offer(st, dev.tcgshop.shop.CardSales.PHONE_RATE);
    }

    private long cachedPrice(Catalog.Card c) {
        long day = PriceService.marketDay();
        if (day != priceCacheDay) { priceCache.clear(); priceCacheDay = day; }
        return priceCache.computeIfAbsent(c.key(), k -> PriceService.priceCd(c.stack()));
    }

    @Override
    protected void init() {
        Catalog.rebuild();
        String prev = search == null ? "" : search.getText();
        search = new TextFieldWidget(textRenderer, 0, 0, 120, 14, Text.translatable("tcgshop.binder.search"));
        search.setPlaceholder(Text.translatable("tcgshop.binder.search").formatted(Formatting.DARK_GRAY));
        search.setText(prev);
        search.setDrawsBackground(false);
        search.setChangedListener(s -> { market.filter = s; listScroll = 0; });
        addSelectableChild(search);
    }

    @Override
    public boolean shouldPause() { return false; }

    private static double now() { return System.nanoTime() / 1e9; }

    private void open(App a) {
        if (a == app) return;
        float cur = morph();
        morphFrom = cur;
        morphTo = a == App.HOME ? 0 : 1;
        if (morphFrom != morphTo) morphStart = now();
        app = a;
        appOpened = now();
        listScroll = 0;
        search.setFocused(false);
    }

    /** 0 = vertical, 1 = horizontal (con easing). */
    private float morph() {
        double p = MathHelper.clamp((now() - morphStart) / 0.38, 0, 1);
        double e = p < 0.5 ? 4 * p * p * p : 1 - Math.pow(-2 * p + 2, 3) / 2;
        return (float) (morphFrom + (morphTo - morphFrom) * e);
    }

    private boolean morphing() { return now() - morphStart < 0.38; }

    private void layout() {
        float p = morph();
        int pH = Math.min(height - 16, 400), pW = Math.round(pH * 0.56f);
        int lW = Math.min(width - 16, 620), lH = Math.min(height - 16, Math.max(250, Math.round(lW * 0.56f)));
        fw = Math.round(pW + (lW - pW) * p);
        fh = Math.round(pH + (lH - pH) * p);
        fx = (width - fw) / 2;
        fy = (height - fh) / 2;
        int bezel = 7;
        sx = fx + bezel;
        sy = fy + bezel;
        sw = fw - bezel * 2;
        sh = fh - bezel * 2;
    }

    // ------------------------------------------------------------------ dibujo

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        areas.clear();
        market.beginFrame();
        double t = now();
        layout();
        ctx.fillGradient(0, 0, width, height, 0x99080A12, 0xCC020306);
        float p = morph();
        // halo del color de la app detrás del teléfono
        int aura = app == App.HOME ? 0xFF6B4FD8 : def(app.name().toLowerCase(Locale.ROOT)).color;
        for (int k = 5; k >= 1; k--) Ui.rounded(ctx, fx - k * 6, fy - k * 6, fw + k * 12, fh + k * 12, 22 + k * 6, withAlpha(aura, 0.03f));
        // carcasa metálica con canto pulido
        Ui.rounded(ctx, fx + 4, fy + 10, fw + 2, fh + 2, 22, 0x70000000);
        Ui.roundedGradient(ctx, fx - 4, fy - 4, fw + 8, fh + 8, 24, 0xFF9AA0B2, 0xFF2A2D36);
        Ui.roundedGradient(ctx, fx - 3, fy - 3, fw + 6, fh + 6, 23, 0xFF5A6070, 0xFF1C1E26);
        Ui.roundedGradient(ctx, fx - 1, fy - 1, fw + 2, fh + 2, 21, 0xFF15161C, 0xFF07080A);
        ctx.fill(fx + 20, fy - 4, fx + fw - 20, fy - 3, 0x88FFFFFF);
        // botones laterales
        if (p < 0.5f) {
            Ui.rounded(ctx, fx + fw + 3, fy + 64, 3, 34, 1, 0xFF6A7080);
            Ui.rounded(ctx, fx - 6, fy + 56, 3, 18, 1, 0xFF6A7080);
            Ui.rounded(ctx, fx - 6, fy + 80, 3, 18, 1, 0xFF6A7080);
        } else {
            Ui.rounded(ctx, fx + 64, fy - 6, 34, 3, 1, 0xFF6A7080);
            Ui.rounded(ctx, fx + fw - 98, fy - 6, 18, 3, 1, 0xFF6A7080);
        }
        wallpaper(ctx, t);
        statusBar(ctx, p);
        // isla de la cámara
        if (p < 0.5f) {
            Ui.rounded(ctx, sx + sw / 2 - 24, sy + 3, 48, 10, 5, 0xFF000000);
            StylePainter.circle(ctx, sx + sw / 2 + 16, sy + 8, 2, 0xFF1A2A44);
        } else {
            Ui.rounded(ctx, sx + 3, sy + sh / 2 - 24, 10, 48, 5, 0xFF000000);
            StylePainter.circle(ctx, sx + 8, sy + sh / 2 + 16, 2, 0xFF1A2A44);
        }
        // indicador de inicio
        boolean hh = Ui.in(mouseX, mouseY, sx + sw / 2 - 40, sy + sh - 10, 80, 10);
        Ui.rounded(ctx, sx + sw / 2 - 30, sy + sh - 6, 60, 4, 2, hh ? 0xFFFFFFFF : 0xCCDDDDDD);
        areas.add(new Area(sx + sw / 2 - 40, sy + sh - 10, 80, 10, () -> open(App.HOME)));

        if (morphing()) { Ui.cornerMask(ctx, sx, sy, sw, sh, 16, 0xFF0A0B0E); return; } // mientras gira sólo se ve el fondo
        float fade = (float) MathHelper.clamp((t - Math.max(appOpened, morphStart + 0.38)) / 0.18, 0, 1);
        ctx.enableScissor(sx, sy + 13, sx + sw, sy + sh - 8);
        switch (app) {
            case HOME -> home(ctx, mouseX, mouseY, t);
            case MARKET -> marketApp(ctx, mouseX, mouseY, delta, t);
            case PRICES -> pricesApp(ctx, mouseX, mouseY, delta, t);
            case SHOP -> shopApp(ctx, mouseX, mouseY, t);
            case TITLES -> titlesApp(ctx, mouseX, mouseY);
            case GRADE -> gradeApp(ctx, mouseX, mouseY, t);
            case LEAGUE -> leagueApp(ctx, mouseX, mouseY, t);
            case SELL -> sellApp(ctx, mouseX, mouseY, t);
            case FRIENDS -> friendsApp(ctx, mouseX, mouseY);
            case DISPLAYS -> displaysApp(ctx, mouseX, mouseY);
        }
        ctx.disableScissor();
        if (fade < 1) ctx.fill(sx, sy + 13, sx + sw, sy + sh - 8, withAlpha(0xFF0B0D16, 1 - fade));
        Ui.cornerMask(ctx, sx, sy, sw, sh, 16, 0xFF0A0B0E);
    }

    private void wallpaper(DrawContext ctx, double t) {
        boolean home = app == App.HOME || morphing();
        if (home) {
            ctx.fillGradient(sx, sy, sx + sw, sy + sh, 0xFF2B2A8A, 0xFF0B0A26);
            // aurora: manchas de color que se mueven despacio
            int[] cols = {0xFF7A4FE8, 0xFF2EA8E8, 0xFFE84FA8, 0xFF4FE8B0};
            for (int i = 0; i < 4; i++) {
                int gx = sx + (int) (sw * (0.15 + i * 0.25) + Math.sin(t * 0.27 + i * 2.1) * sw * 0.12);
                int gy = sy + (int) (sh * (0.22 + 0.18 * i) + Math.cos(t * 0.21 + i * 1.3) * sh * 0.06);
                for (int r = Math.min(sw, sh) / 3; r > 0; r -= 7) StylePainter.circle(ctx, gx, gy, r, withAlpha(cols[i], 0.03f));
            }
            // estrellitas que parpadean
            for (int k = 0; k < 26; k++) {
                int px = sx + 6 + (int) ((k * 97L) % Math.max(1, sw - 12)), py = sy + 16 + (int) ((k * 61L) % Math.max(1, sh - 30));
                float tw = 0.3f + 0.7f * (float) Math.max(0, Math.sin(t * 1.7 + k * 1.9));
                ctx.fill(px, py, px + 1, py + 1, withAlpha(0xFFFFFFFF, 0.25f + 0.55f * tw));
            }
            // marca de agua de Poké Ball
            int cx = sx + sw / 2, cy = sy + sh - 70, r = Math.min(sw, sh) / 4;
            StylePainter.circle(ctx, cx, cy, r, 0x0CFFFFFF);
            ctx.fill(cx - r, cy - 1, cx + r, cy + 2, 0x10000000);
            StylePainter.circle(ctx, cx, cy, r / 4, 0x14FFFFFF);
        } else {
            int accent = def(app.name().toLowerCase(Locale.ROOT)).color;
            ctx.fillGradient(sx, sy, sx + sw, sy + sh, 0xFF161A2A, 0xFF0A0C14);
            ctx.fillGradient(sx, sy + 35, sx + sw, sy + 120, withAlpha(accent, 0.12f), withAlpha(accent, 0f));
            for (int gx = sx + 12; gx < sx + sw; gx += 24) ctx.fill(gx, sy + 36, gx + 1, sy + sh - 8, 0x06FFFFFF);
        }
    }

    private void statusBar(DrawContext ctx, float p) {
        long time = client != null && client.world != null ? (client.world.getTimeOfDay() + 6000) % 24000 : 0;
        String clock = String.format(Locale.ROOT, "%02d:%02d", time / 1000, (time % 1000) * 60 / 1000);
        int y = sy + 3, left = p < 0.5f ? sx + 8 : sx + 16;
        ctx.drawText(textRenderer, Text.literal(clock).formatted(Formatting.BOLD), left, y, 0xFFFFFFFF, false);
        if (client != null && client.player != null && dev.tcgshop.economy.CobbleDollarsBridge.isAvailable()) {
            String wallet = "₽ " + compact(dev.tcgshop.economy.CobbleDollarsBridge.getBalance(client.player));
            int wx = left + textRenderer.getWidth(clock) + 8;
            Ui.round(ctx, wx - 3, y - 2, textRenderer.getWidth(wallet) + 6, 11, 0x55000000);
            ctx.drawText(textRenderer, wallet, wx, y, Ui.GOLD, false);
        }
        int rx = sx + sw - 8;
        // batería
        ctx.fill(rx - 14, y, rx - 1, y + 7, 0xFFFFFFFF);
        ctx.fill(rx - 13, y + 1, rx - 2, y + 6, 0xFF000000);
        ctx.fill(rx - 12, y + 2, rx - 4, y + 5, 0xFF43C463);
        ctx.fill(rx - 1, y + 2, rx, y + 5, 0xFFFFFFFF);
        // cobertura
        for (int i = 0; i < 4; i++) ctx.fill(rx - 30 + i * 3, y + 6 - i * 2, rx - 28 + i * 3, y + 7, 0xFFFFFFFF);
        ctx.drawText(textRenderer, "5G", rx - 44, y, 0xFFDDDDDD, false);
    }

    // ------------------------------------------------------------------ componentes

    private void appIcon(DrawContext ctx, AppDef a, int x, int y, int s, boolean hover) {
        int lift = hover ? 2 : 0, r = Math.max(3, s / 4);
        if (hover) Ui.rounded(ctx, x - 3, y - 3 - lift, s + 6, s + 6, r + 3, withAlpha(a.color, 0.35f));
        Ui.rounded(ctx, x + 1, y + 3 - lift + 1, s, s, r, 0x40000000);
        Ui.rounded(ctx, x, y + 2 - lift, s, s, r, 0x55000000);
        Ui.roundedGradient(ctx, x, y - lift, s, s, r, Ui.brighten(a.color, 60), Ui.darken(a.color, 35));
        Ui.roundedGradient(ctx, x + 1, y + 1 - lift, s - 2, s / 2, r - 1, 0x55FFFFFF, 0x08FFFFFF);
        Ui.roundedOutline(ctx, x, y - lift, s, s, r, withAlpha(0xFFFFFFFF, hover ? 0.7f : 0.22f));
        ItemStack icon = a.icon.get();
        var m = ctx.getMatrices();
        if (!icon.isEmpty()) {
            float k = s / 16f * 0.72f;
            m.push();
            m.translate(x + s / 2f, y + s / 2f - lift, 0);
            m.scale(k, k, 1);
            ctx.drawItem(icon, -8, -8);
            m.pop();
        } else if (a.key.equals("grade")) { // funda de carta gradeada dibujada
            int w = s * 9 / 20, h = s * 13 / 20, gx = x + (s - w) / 2, gy = y + (s - h) / 2 - lift + 1;
            ctx.fill(gx - 1, gy - 1, gx + w + 1, gy + h + 1, 0xCCFFFFFF);
            ctx.fill(gx, gy, gx + w, gy + h / 4, 0xFFF2F2F2);
            ctx.fill(gx, gy, gx + 2, gy + h / 4, 0xFFE5B21E);
            ctx.fill(gx + 2, gy + h / 4 + 2, gx + w - 2, gy + h - 2, 0xFF2F6FB0);
            ctx.fill(gx + 4, gy + h / 4 + 4, gx + w - 4, gy + h - 4, 0xFF6BC5FF);
            int ready = dev.tcgshop.client.ClientGrading.readyCount();
            if (ready > 0) Ui.badge(ctx, x + s - 3, y - 2 - lift, ready);
        } else { // gráfica de precios dibujada
            int bx = x + s / 5, by = y + s - s / 4 - lift, bw = Math.max(2, s / 9);
            int[] hs = {4, 7, 5, 10, 13};
            for (int i = 0; i < hs.length; i++) {
                int bh = hs[i] * s / 22;
                ctx.fill(bx + i * (bw + 1), by - bh, bx + i * (bw + 1) + bw, by, 0xFFFFFFFF);
            }
            ctx.drawText(textRenderer, Text.literal("↗").formatted(Formatting.BOLD), x + s - 11, y + 3 - lift, 0xFFFFE680, true);
        }
    }

    private void widget(DrawContext ctx, int x, int y, int w, int h, Text label, Text value, int color) {
        Ui.glass(ctx, x, y, w, h, 8);
        ctx.drawText(textRenderer, label, x + 6, y + 5, 0xFFD8DCF0, false);
        var m = ctx.getMatrices();
        m.push();
        m.translate(x + 6, y + 17, 0);
        float s = Math.min(1.5f, (w - 12f) / Math.max(1, textRenderer.getWidth(value)));
        m.scale(s, s, 1);
        ctx.drawText(textRenderer, value, 0, 0, color, true);
        m.pop();
    }

    /** Anillo de progreso (aro de puntos). */
    private static void ring(DrawContext ctx, int cx, int cy, int r, int thick, float frac, int color) {
        int steps = Math.max(24, r * 5);
        for (int i = 0; i < steps; i++) {
            double a = -Math.PI / 2 + i * Math.PI * 2 / steps;
            boolean on = i < steps * frac;
            for (int k = 0; k < thick; k++) {
                int px = cx + (int) Math.round(Math.cos(a) * (r - k)), py = cy + (int) Math.round(Math.sin(a) * (r - k));
                ctx.fill(px, py, px + 1, py + 1, on ? color : 0x33FFFFFF);
            }
        }
    }

    private void topBar(DrawContext ctx, AppDef a, int mouseX, int mouseY) {
        int y = sy + 13;
        ctx.fillGradient(sx, y, sx + sw, y + 22, withAlpha(Ui.brighten(a.color, 10), 0.96f), withAlpha(Ui.darken(a.color, 50), 0.96f));
        ctx.fill(sx, y, sx + sw, y + 1, 0x40FFFFFF);
        ctx.fillGradient(sx, y + 22, sx + sw, y + 27, 0x66000000, 0x00000000);
        boolean hb = Ui.in(mouseX, mouseY, sx + 10, y + 3, 16, 16);
        Ui.rounded(ctx, sx + 10, y + 3, 16, 16, 8, hb ? 0x66FFFFFF : 0x2AFFFFFF);
        if (hb) Ui.roundedOutline(ctx, sx + 10, y + 3, 16, 16, 8, 0xAAFFFFFF);
        Ui.text(ctx, Text.literal("‹").formatted(Formatting.BOLD), sx + 10, y + 3, 16, 16, 0xFFFFFFFF, true);
        areas.add(new Area(sx + 10, y + 3, 16, 16, () -> open(App.HOME)));
        Ui.rounded(ctx, sx + 31, y + 6, 3, 10, 1, 0xCCFFFFFF);
        ctx.drawText(textRenderer, Text.translatable("tcgshop.phone." + a.key).formatted(Formatting.BOLD), sx + 39, y + 7, 0xFFFFFFFF, true);
    }

    private void searchBox(DrawContext ctx, int x, int y, int w, int mouseX, int mouseY, float delta) {
        Ui.round(ctx, x, y, w, 15, search.isFocused() ? 0xEE0E1018 : 0xAA0E1018);
        Ui.outline(ctx, x, y, w, 15, search.isFocused() ? 0xFFFFFFFF : 0x66FFFFFF);
        ctx.drawText(textRenderer, "⌕", x + 4, y + 4, 0xFFAAAAAA, false);
        search.setX(x + 14);
        search.setY(y + 4);
        search.setWidth(w - 18);
        search.render(ctx, mouseX, mouseY, delta);
    }

    /** Carril lateral de secciones. Devuelve el ancho usado. */
    private float railSelY = -1;

    private int rail(DrawContext ctx, String[] keys, String[] labels, int[] counts, String selected, int accent, int mouseX, int mouseY,
                     java.util.function.Consumer<String> pick) {
        int x = sx + 8, y = sy + 41, w = Math.min(104, sw / 4), rowH = 18;
        int panelH = Math.min(sh - 54, keys.length * rowH + 8);
        Ui.roundedGradient(ctx, x - 3, y - 4, w + 6, panelH, 7, 0x70000000, 0x50000000);
        Ui.roundedOutline(ctx, x - 3, y - 4, w + 6, panelH, 7, 0x22FFFFFF);
        int selIdx = -1;
        for (int i = 0; i < keys.length; i++) if (keys[i].equals(selected)) selIdx = i;
        if (selIdx >= 0) {
            float target = y + selIdx * rowH;
            if (railSelY < 0 || Math.abs(railSelY - target) > rowH * keys.length) railSelY = target;
            railSelY += (target - railSelY) * 0.3f;
            int ry = Math.round(railSelY);
            Ui.rounded(ctx, x - 1, ry - 1, w + 2, rowH, 6, withAlpha(accent, 0.18f));
            Ui.roundedGradient(ctx, x, ry, w, rowH - 2, 5, withAlpha(Ui.brighten(accent, 20), 0.55f), withAlpha(accent, 0.3f));
            Ui.roundedOutline(ctx, x, ry, w, rowH - 2, 5, withAlpha(accent, 0.9f));
            Ui.rounded(ctx, x + 2, ry + 3, 2, rowH - 8, 1, 0xFFFFFFFF);
        }
        for (int i = 0; i < keys.length; i++) {
            int ry = y + i * rowH;
            if (ry + rowH > sy + sh - 10) break;
            boolean sel = i == selIdx, hover = Ui.in(mouseX, mouseY, x, ry, w, rowH - 2);
            if (!sel && hover) Ui.rounded(ctx, x, ry, w, rowH - 2, 5, 0x22FFFFFF);
            String lbl = Ui.ellipsis(labels[i], w - (counts != null ? 26 : 12));
            ctx.drawText(textRenderer, lbl, x + 8, ry + 5, sel ? 0xFFFFFFFF : hover ? 0xFFE8EAF2 : 0xFFB8BCCB, sel);
            if (counts != null && counts[i] >= 0) {
                String c = String.valueOf(counts[i]);
                int cw = textRenderer.getWidth(c) + 6;
                Ui.rounded(ctx, x + w - 4 - cw, ry + 3, cw, 10, 5, sel ? 0x66000000 : 0x33000000);
                ctx.drawText(textRenderer, c, x + w - 1 - cw, ry + 4, sel ? Ui.GOLD : 0xFF8A8FA8, false);
            }
            String k = keys[i];
            areas.add(new Area(x, ry, w, rowH - 2, () -> pick.accept(k)));
        }
        return w + 14;
    }

    private void tile(DrawContext ctx, int x, int y, int w, int h, String labelKey, String value, int color) {
        Ui.rounded(ctx, x + 1, y + 2, w, h, 6, 0x40000000);
        Ui.roundedGradient(ctx, x, y, w, h, 6, SURF2, SURF);
        Ui.roundedOutline(ctx, x, y, w, h, 6, LINE);
        Ui.rounded(ctx, x + 2, y + 4, 2, h - 8, 1, color);
        ctx.drawText(textRenderer, Ui.ellipsis(Text.translatable(labelKey).getString(), w - 12), x + 8, y + 5, 0xFF9A9FB8, false);
        var m = ctx.getMatrices();
        m.push();
        m.translate(x + 8, y + 17, 0);
        float s = Math.min(1.35f, (w - 14f) / Math.max(1, textRenderer.getWidth(value)));
        m.scale(s, s, 1);
        ctx.drawText(textRenderer, Text.literal(value).formatted(Formatting.BOLD), 0, 0, color, true);
        m.pop();
    }

    private int paragraph(DrawContext ctx, Text text, int x, int y, int w, int color) {
        for (var line : textRenderer.wrapLines(text, w)) {
            ctx.drawText(textRenderer, line, x, y, color, false);
            y += 10;
        }
        return y;
    }

    // ------------------------------------------------------------------ inicio

    private void home(DrawContext ctx, int mouseX, int mouseY, double t) {
        long tod = client != null && client.world != null ? client.world.getTimeOfDay() : 0;
        long time = (tod + 6000) % 24000;
        String clock = String.format(Locale.ROOT, "%02d:%02d", time / 1000, (time % 1000) * 60 / 1000);
        Draw.scaledCentered(ctx, Text.literal(clock).formatted(Formatting.BOLD), sx + sw / 2f, sy + 24, 3.2f, 0xFFFFFFFF);
        String part = time < 6000 ? "night" : time < 12000 ? "morning" : time < 18000 ? "afternoon" : "evening";
        Draw.scaledCentered(ctx, Text.translatable("tcgshop.phone.day", tod / 24000 + 1).append(" · ").append(Text.translatable("tcgshop.phone.time." + part)),
                sx + sw / 2f, sy + 54, 1f, 0xFFCCD6FF);
        // widgets
        int wy = sy + 70, ww = (sw - 22) / 2;
        long balance = 0;
        int rep = -1;
        for (ShopClient.ZoneInfo z : myZones()) { balance += z.balance(); rep = Math.max(rep, z.reputation()); }
        long profit = ShopClient.myRevenue - ShopClient.myCosts;
        widget(ctx, sx + 8, wy, ww, 40, Text.translatable("tcgshop.phone.w_register"), Text.literal(PriceService.format(balance)), Ui.GOLD);
        Ui.glass(ctx, sx + 14 + ww, wy, ww, 40, 8);
        int rc = rep >= 80 ? 0xFF43C463 : rep >= 50 ? 0xFFFFD34D : 0xFFE05050;
        ring(ctx, sx + 14 + ww + 20, wy + 20, 13, 3, Math.max(0, rep) / 100f, rc);
        Ui.text(ctx, Text.literal(rep < 0 ? "—" : String.valueOf(rep)).formatted(Formatting.BOLD), sx + 14 + ww + 8, wy + 13, 24, 14, 0xFFFFFFFF, true);
        ctx.drawText(textRenderer, Text.translatable("tcgshop.phone.w_rep"), sx + 14 + ww + 38, wy + 9, 0xFFD8DCF0, false);
        ctx.drawText(textRenderer, Text.literal((profit >= 0 ? "+" : "") + compact(profit)), sx + 14 + ww + 38, wy + 22, profit >= 0 ? 0xFF6BE07B : 0xFFFF6B6B, true);
        // progreso de colección
        int total = Math.max(1, Catalog.totalCards()), have = ClientCollection.counts.size();
        int cy = wy + 46;
        Ui.glass(ctx, sx + 8, cy, sw - 16, 24, 8);
        ctx.drawText(textRenderer, Text.translatable("tcgshop.phone.w_collection", have, total), sx + 14, cy + 4, 0xFFFFFFFF, false);
        String pct = String.format(Locale.ROOT, "%.1f%%", have * 100f / total);
        ctx.drawText(textRenderer, pct, sx + sw - 14 - textRenderer.getWidth(pct), cy + 4, Ui.GOLD, false);
        Ui.progress(ctx, sx + 14, cy + 15, sw - 28, 5, have / (float) total, 0xFFB3262E);
        // apps
        int cols = 4, rowsN = (apps.size() + cols - 1) / cols;
        int gy = cy + 34, availH = (sy + sh - Math.min(28, (sw - 60) / 4) - 26) - gy;
        int is = Math.max(14, Math.min(Math.min(40, (sw - 50) / 4), availH / rowsN - 20)), gap = (sw - cols * is) / (cols + 1);
        for (int i = 0; i < apps.size(); i++) {
            AppDef a = apps.get(i);
            int ix = sx + gap + (i % cols) * (is + gap), iy = gy + (i / cols) * (is + 20);
            boolean hover = Ui.in(mouseX, mouseY, ix, iy, is, is);
            appIcon(ctx, a, ix, iy, is, hover);
            Ui.text(ctx, Text.translatable("tcgshop.phone." + a.key), ix - gap / 2, iy + is + 3, is + gap, 10, 0xFFFFFFFF, true);
            areas.add(new Area(ix, iy, is, is, () -> openApp(a.key)));
        }
        // dock
        int ds = Math.min(28, (sw - 60) / 4), dy = sy + sh - ds - 18;
        Ui.glass(ctx, sx + 10, dy - 5, sw - 20, ds + 10, Math.min(14, (ds + 10) / 2));
        int[] dock = {0, 2, 3, 1};
        for (int i = 0; i < dock.length; i++) {
            AppDef a = apps.get(dock[i]);
            int dx = sx + 10 + (sw - 20) * (2 * i + 1) / 8 - ds / 2;
            appIcon(ctx, a, dx, dy, ds, Ui.in(mouseX, mouseY, dx, dy, ds, ds));
            areas.add(new Area(dx, dy, ds, ds, () -> openApp(a.key)));
        }
    }

    private List<ShopClient.ZoneInfo> myZones() {
        List<ShopClient.ZoneInfo> mine = new ArrayList<>();
        for (ShopClient.ZoneInfo z : ShopClient.zones) if (client != null && client.player != null && z.owner().equals(client.player.getUuid())) mine.add(z);
        return mine;
    }

    private static String compact(long v) {
        long a = Math.abs(v);
        String s = a >= 1_000_000 ? String.format(Locale.ROOT, "%.1fM", a / 1e6) : a >= 1000 ? String.format(Locale.ROOT, "%.1fk", a / 1e3) : String.valueOf(a);
        return (v < 0 ? "-" : "") + s;
    }

    private void openApp(String key) {
        switch (key) {
            case "market" -> open(App.MARKET);
            case "prices" -> open(App.PRICES);
            case "shop" -> open(App.SHOP);
            case "titles" -> open(App.TITLES);
            case "sell" -> {
                quickSell.sel.clear();
                quickSell.restartAppear();
                open(App.SELL);
            }
            case "league" -> {
                leagueSend("sync", null);
                open(App.LEAGUE);
            }
            case "grade" -> {
                dev.tcgshop.client.ClientGrading.request();
                open(App.GRADE);
            }
            case "collection" -> {
                close();
                ClientCollection.action(BinderActionPayload.OPEN_GENERAL, "", 0);
            }
            case "binder_market" -> {
                close();
                dev.tcgshop.client.ClientSocial.act("binder_open", "open");
            }
            case "friends" -> {
                dev.tcgshop.client.ClientSocial.act("friends", "refresh");
                listScroll = 0;
                open(App.FRIENDS);
            }
            case "displays" -> {
                dev.tcgshop.client.ClientSocial.act("displays", "list");
                listScroll = 0;
                open(App.DISPLAYS);
            }
            case "advancements" -> {
                if (client != null && client.player != null) client.setScreen(new AdvancementsScreen(client.player.networkHandler.getAdvancementHandler()));
            }
            default -> {}
        }
    }

    // ------------------------------------------------------------------ amigos

    private void friendsApp(DrawContext ctx, int mouseX, int mouseY) {
        AppDef a = def("friends");
        topBar(ctx, a, mouseX, mouseY);
        var data = dev.tcgshop.client.ClientSocial.friends;
        int px = sx + 8, py = sy + 41, pw = sw - 16, ph = sh - 52;
        int lw = Math.min(130, pw / 3);
        Ui.roundGradient(ctx, px, py, lw, ph, SURF2, SURF);
        Ui.outline(ctx, px, py, lw, ph, LINE);
        var friends = data.getList("friends", 10);
        var incoming = data.getList("incoming", 10);
        var online = data.getList("online", 10);
        int y = py + 4;
        ctx.drawText(textRenderer, Text.translatable("tcgshop.friends.list", friends.size()).formatted(Formatting.BOLD), px + 5, y, 0xFFFFFFFF, false);
        y += 12;
        String sel = dev.tcgshop.client.ClientSocial.selectedFriend;
        net.minecraft.nbt.NbtCompound selected = null;
        for (int i = 0; i < friends.size() && y < py + ph - 14; i++) {
            var f = friends.getCompound(i);
            String u = f.getUuid("u").toString();
            boolean on = u.equals(sel), hover = Ui.in(mouseX, mouseY, px + 3, y, lw - 6, 12);
            if (on) selected = f;
            Ui.round(ctx, px + 3, y, lw - 6, 12, on ? withAlpha(a.color, 0.5f) : hover ? 0x22FFFFFF : 0);
            StylePainter.circle(ctx, px + 9, y + 6, 2, f.getBoolean("on") ? 0xFF43C463 : 0xFF6A6F80);
            ctx.drawText(textRenderer, Ui.ellipsis(f.getString("n"), lw - 22), px + 15, y + 2, 0xFFFFFFFF, false);
            areas.add(new Area(px + 3, y, lw - 6, 12, () -> dev.tcgshop.client.ClientSocial.selectedFriend = u));
            y += 13;
        }
        if (friends.isEmpty()) { ctx.drawText(textRenderer, Text.translatable("tcgshop.friends.none"), px + 5, y, Ui.MUTED, false); y += 12; }
        if (!incoming.isEmpty()) {
            y += 4;
            ctx.drawText(textRenderer, Text.translatable("tcgshop.friends.requests").formatted(Formatting.BOLD), px + 5, y, 0xFF6BC5FF, false);
            y += 12;
            for (int i = 0; i < incoming.size() && y < py + ph - 14; i++) {
                var r = incoming.getCompound(i);
                java.util.UUID u = r.getUuid("u");
                ctx.drawText(textRenderer, Ui.ellipsis(r.getString("n"), lw - 40), px + 5, y + 2, 0xFFFFFFFF, false);
                Ui.button(ctx, px + lw - 33, y, 14, 12, Text.literal("✔"), mouseX, mouseY, true, 0xFF2E8A4A);
                Ui.button(ctx, px + lw - 17, y, 14, 12, Text.literal("✖"), mouseX, mouseY, true, 0xFF7A2020);
                areas.add(new Area(px + lw - 33, y, 14, 12, () -> friendAct("accept", u)));
                areas.add(new Area(px + lw - 17, y, 14, 12, () -> friendAct("decline", u)));
                y += 14;
            }
        }
        if (!online.isEmpty()) {
            y += 4;
            ctx.drawText(textRenderer, Text.translatable("tcgshop.friends.add").formatted(Formatting.BOLD), px + 5, y, 0xFFFFD34D, false);
            y += 12;
            for (int i = 0; i < online.size() && y < py + ph - 14; i++) {
                var r = online.getCompound(i);
                java.util.UUID u = r.getUuid("u");
                boolean sent = r.getBoolean("sent");
                ctx.drawText(textRenderer, Ui.ellipsis(r.getString("n"), lw - 26), px + 5, y + 2, 0xFFDDDDDD, false);
                Ui.button(ctx, px + lw - 17, y, 14, 12, Text.literal(sent ? "…" : "+"), mouseX, mouseY, !sent, 0xFF3A6FD8);
                if (!sent) areas.add(new Area(px + lw - 17, y, 14, 12, () -> friendAct("request", u)));
                y += 14;
            }
        }
        // detalle del amigo
        int dx = px + lw + 6, dw = pw - lw - 6;
        if (selected == null) {
            paragraph(ctx, Text.translatable("tcgshop.friends.tip"), dx + 4, py + 6, dw - 8, Ui.MUTED);
            return;
        }
        var s = selected.getCompound("s");
        ctx.drawTextWithShadow(textRenderer, Text.literal(selected.getString("n")).formatted(Formatting.BOLD), dx + 2, py + 2,
                selected.getBoolean("on") ? 0xFF8BE08B : 0xFFFFFFFF);
        java.util.UUID fu = selected.getUuid("u");
        Ui.button(ctx, dx + dw - 60, py, 58, 12, Text.translatable("tcgshop.friends.remove"), mouseX, mouseY, true, 0xFF5A2A2A);
        areas.add(new Area(dx + dw - 60, py, 58, 12, () -> { friendAct("remove", fu); dev.tcgshop.client.ClientSocial.selectedFriend = ""; }));
        int cols = 3, gap = 4, tw = (dw - gap * 2) / cols, th = 30, ty = py + 16;
        String[][] tl = {
                {"tcgshop.friends.s_rating", String.valueOf(s.getInt("rating"))},
                {"tcgshop.friends.s_wins", s.getInt("wins") + " / " + s.getInt("losses")},
                {"tcgshop.friends.s_unique", String.valueOf(s.getInt("unique"))},
                {"tcgshop.friends.s_packs", String.valueOf(s.getInt("packs"))},
                {"tcgshop.friends.s_sales", String.valueOf(s.getInt("sales"))},
                {"tcgshop.friends.s_revenue", compact(s.getLong("revenue"))}};
        for (int i = 0; i < tl.length; i++)
            tile(ctx, dx + (i % cols) * (tw + gap), ty + (i / cols) * (th + gap), tw, th, tl[i][0], tl[i][1], i < 2 ? 0xFFFF6B6B : i < 4 ? 0xFFB983FF : Ui.GOLD);
        int sy2 = ty + (th + gap) * 2 + 4;
        var shops = s.getList("shops", 10);
        ctx.drawText(textRenderer, Text.translatable("tcgshop.friends.shops", shops.size()).formatted(Formatting.BOLD), dx + 2, sy2, 0xFFFFFFFF, false);
        sy2 += 12;
        for (int i = 0; i < shops.size() && sy2 < py + ph - 12; i++) {
            var z = shops.getCompound(i);
            Ui.roundGradient(ctx, dx, sy2, dw, 20, SURF2, SURF);
            String tier = Text.translatable("tcgshop.shop.tier." + dev.tcgshop.shop.ShopTier.values()[Math.max(0, Math.min(6, z.getInt("tier")))].key).getString();
            ctx.drawText(textRenderer, Text.translatable("tcgshop.friends.shop_line", tier, z.getInt("rep"), String.format(Locale.ROOT, "%.1f", z.getInt("stars") / 10.0), z.getInt("sales")),
                    dx + 4, sy2 + 2, 0xFFDDE3F0, false);
            ctx.drawText(textRenderer, Text.translatable("tcgshop.friends.shop_pos", z.getInt("x"), z.getInt("y"), z.getInt("z"), z.getString("dim")), dx + 4, sy2 + 11, 0xFF8A90A8, false);
            sy2 += 22;
        }
    }

    private static void friendAct(String action, java.util.UUID u) {
        net.minecraft.nbt.NbtCompound d = new net.minecraft.nbt.NbtCompound();
        d.putString("a", action);
        d.putUuid("u", u);
        dev.tcgshop.client.ClientSocial.act("friends", d);
    }

    // ------------------------------------------------------------------ gestor de expositores

    private void displaysApp(DrawContext ctx, int mouseX, int mouseY) {
        AppDef a = def("displays");
        topBar(ctx, a, mouseX, mouseY);
        var data = dev.tcgshop.client.ClientSocial.displays;
        int px = sx + 8, py = sy + 41, pw = sw - 16, ph = sh - 52;
        // acciones en bloque
        int bx = px;
        String[][] bulk = {{"tcgshop.displays.all_market", "1.0"}, {"tcgshop.displays.all_90", "0.9"}, {"tcgshop.displays.all_110", "1.1"}, {"tcgshop.displays.all_125", "1.25"}};
        for (String[] b : bulk) {
            Text l = Text.translatable(b[0]);
            int w = textRenderer.getWidth(l) + 10;
            Ui.button(ctx, bx, py, w, 13, l, mouseX, mouseY, true, 0xFF39415E);
            double f = Double.parseDouble(b[1]);
            areas.add(new Area(bx, py, w, 13, () -> {
                net.minecraft.nbt.NbtCompound d = new net.minecraft.nbt.NbtCompound();
                d.putString("a", "all_market");
                d.putDouble("f", f);
                dev.tcgshop.client.ClientSocial.act("displays", d);
            }));
            bx += w + 4;
        }
        // lista de productos expuestos
        java.util.List<Object[]> rows = new java.util.ArrayList<>();
        var zones = data.getList("zones", 10);
        long totalValue = 0;
        for (int zi = 0; zi < zones.size(); zi++) {
            var ds = zones.getCompound(zi).getList("displays", 10);
            for (int di = 0; di < ds.size(); di++) {
                var d = ds.getCompound(di);
                var sl = d.getList("slots", 10);
                for (int si = 0; si < sl.size(); si++) {
                    var s = sl.getCompound(si);
                    ItemStack st = client == null || client.world == null ? ItemStack.EMPTY : ItemStack.fromNbt(client.world.getRegistryManager(), s.get("item")).orElse(ItemStack.EMPTY);
                    rows.add(new Object[]{d.getLong("pos"), s.getInt("i"), st, s.getLong("price"), s.getLong("market"), d.getString("block")});
                    totalValue += s.getLong("price") * Math.max(1, st.getCount());
                }
            }
        }
        ctx.drawText(textRenderer, Text.translatable("tcgshop.displays.summary", rows.size(), PriceService.format(totalValue)), px, py + 17, 0xFFCCD2E6, false);
        int ly = py + 29, lh = ph - 29, rowH = 22;
        listScroll = MathHelper.clamp(listScroll, 0, Math.max(0, rows.size() * rowH - lh));
        if (rows.isEmpty()) { paragraph(ctx, Text.translatable(zones.isEmpty() ? "tcgshop.displays.no_shop" : "tcgshop.displays.empty"), px + 4, ly + 6, pw - 8, Ui.MUTED); return; }
        ctx.enableScissor(px, ly, px + pw, ly + lh);
        for (int i = 0; i < rows.size(); i++) {
            Object[] r = rows.get(i);
            int y = ly + i * rowH - (int) listScroll;
            if (y + rowH < ly || y > ly + lh) continue;
            long pos = (long) r[0];
            int slot = (int) r[1];
            ItemStack st = (ItemStack) r[2];
            long price = (long) r[3], market = (long) r[4];
            Ui.roundGradient(ctx, px, y, pw, rowH - 2, SURF2, SURF);
            ctx.drawItem(st, px + 3, y + 2);
            net.minecraft.util.math.BlockPos bp = net.minecraft.util.math.BlockPos.fromLong(pos);
            ctx.drawText(textRenderer, Ui.ellipsis(st.getName().getString(), pw - 190), px + 22, y + 2, 0xFFFFFFFF, false);
            ctx.drawText(textRenderer, Text.translatable((String) r[5]).getString() + " · " + bp.getX() + " " + bp.getY() + " " + bp.getZ(), px + 22, y + 11, 0xFF8A90A8, false);
            double diff = market == 0 ? 0 : (price - market) * 100.0 / market;
            String ps = PriceService.format(price);
            int rx = px + pw - 118;
            ctx.drawText(textRenderer, ps, rx - textRenderer.getWidth(ps) - 4, y + 2, Ui.GOLD, false);
            String dfs = String.format(Locale.ROOT, "%+.0f%%", diff);
            ctx.drawText(textRenderer, dfs, rx - textRenderer.getWidth(dfs) - 4, y + 11, diff > 15 ? 0xFFFF7A5C : diff < -15 ? 0xFF6BC5FF : 0xFF8BE08B, false);
            boolean vis = y >= ly && y + rowH <= ly + lh + 4;
            String[] lbl = {"-5%", "+5%", "M"};
            long[] np = {Math.max(1, Math.round(price * 0.95)), Math.round(price * 1.05) + 1, Math.max(1, market)};
            for (int k = 0; k < 3; k++) {
                int bxx = rx + k * 38;
                Ui.button(ctx, bxx, y + 3, 35, 14, Text.literal(lbl[k]), mouseX, mouseY, true, k == 2 ? 0xFF8A6A1C : 0xFF39415E);
                long newPrice = np[k];
                if (vis) areas.add(new Area(bxx, y + 3, 35, 14, () -> {
                    net.minecraft.nbt.NbtCompound d = new net.minecraft.nbt.NbtCompound();
                    d.putString("a", "price");
                    d.putLong("pos", pos);
                    d.putInt("i", slot);
                    d.putLong("price", newPrice);
                    dev.tcgshop.client.ClientSocial.act("displays", d);
                }));
            }
        }
        ctx.disableScissor();
    }

    private AppDef def(String key) {
        for (AppDef a : apps) if (a.key.equals(key)) return a;
        return apps.get(0);
    }

    // ------------------------------------------------------------------ mercado

    private void marketApp(DrawContext ctx, int mouseX, int mouseY, float delta, double t) {
        AppDef a = def("market");
        topBar(ctx, a, mouseX, mouseY);
        int barY = sy + 13;
        searchBox(ctx, sx + sw - 200, barY + 4, 150, mouseX, mouseY, delta);
        Text sl = Text.literal(market.sort == 0 ? "⇅" : market.sort == 1 ? "₽ ↑" : "₽ ↓");
        Ui.chip(ctx, sx + sw - 44, barY + 4, 34, 15, sl, market.sort != 0, Ui.in(mouseX, mouseY, sx + sw - 44, barY + 4, 34, 15), 0xFFFFE680, 0);
        areas.add(new Area(sx + sw - 44, barY + 4, 34, 15, () -> market.sort = (market.sort + 1) % 3));
        String[] keys = CatalogPanel.CATEGORIES;
        String[] labels = new String[keys.length];
        int[] counts = new int[keys.length];
        for (int i = 0; i < keys.length; i++) {
            labels[i] = Text.translatable("tcgshop.cat." + keys[i]).getString();
            counts[i] = market.count(keys[i]);
        }
        int rw = rail(ctx, keys, labels, counts, market.category, a.color, mouseX, mouseY, k -> { market.category = k; market.resetScroll(); });
        market.render(ctx, sx + rw, sy + 40, sw - rw - 8, sh - 50, mouseX, mouseY, t);
    }

    // ------------------------------------------------------------------ precios

    private void pricesApp(DrawContext ctx, int mouseX, int mouseY, float delta, double t) {
        AppDef a = def("prices");
        topBar(ctx, a, mouseX, mouseY);
        int barY = sy + 13;
        // selector de set en la barra
        List<Catalog.CardSet> sets = Catalog.sets();
        int si = -1;
        for (int i = 0; i < sets.size(); i++) if (sets.get(i).id().equals(priceSet)) si = i;
        Text setLabel = si < 0 ? Text.translatable("tcgshop.binder.all_sets") : sets.get(si).name();
        int selW = Math.min(150, sw / 4), selX = sx + sw - selW - 170;
        Ui.round(ctx, selX, barY + 4, selW, 15, 0xAA0E1018);
        Ui.text(ctx, setLabel, selX + 12, barY + 4, selW - 24, 15, 0xFFFFFFFF, true);
        ctx.drawText(textRenderer, "‹", selX + 4, barY + 7, 0xFFFFFFFF, true);
        ctx.drawText(textRenderer, "›", selX + selW - 8, barY + 7, 0xFFFFFFFF, true);
        final int fsi = si;
        areas.add(new Area(selX, barY + 4, 12, 15, () -> { priceSet = fsi <= 0 ? (fsi == 0 ? "" : sets.get(sets.size() - 1).id()) : sets.get(fsi - 1).id(); listScroll = 0; }));
        areas.add(new Area(selX + selW - 12, barY + 4, 12, 15, () -> { priceSet = fsi + 1 < sets.size() ? sets.get(fsi + 1).id() : ""; listScroll = 0; }));
        searchBox(ctx, sx + sw - 162, barY + 4, 152, mouseX, mouseY, delta);
        // carril: rarezas
        Rarity[] rs = {null, Rarity.COMMON, Rarity.DOUBLE_RARE, Rarity.ULTRA_RARE, Rarity.ILLUSTRATION_RARE, Rarity.SPECIAL_ILLUSTRATION_RARE, Rarity.HYPER_RARE};
        String[] keys = new String[rs.length], labels = new String[rs.length];
        for (int i = 0; i < rs.length; i++) {
            keys[i] = rs[i] == null ? "all" : rs[i].key;
            labels[i] = rs[i] == null ? Text.translatable("tcgshop.cat.all").getString() : rs[i].displayName().getString();
        }
        int rw = rail(ctx, keys, labels, null, priceRarity == null ? "all" : priceRarity.key, a.color, mouseX, mouseY, k -> {
            priceRarity = null;
            for (Rarity r : rs) if (r != null && r.key.equals(k)) priceRarity = r;
            listScroll = 0;
        });
        int px = sx + rw, py = sy + 41, pw = sw - rw - 8, ph = sh - 52;
        int listW = pw * 55 / 100, detX = px + listW + 8, detW = pw - listW - 8;
        // lista de resultados
        String q = search.getText().trim().toLowerCase(Locale.ROOT);
        List<Catalog.Card> res = new ArrayList<>();
        int cap = priceBand != 0 || priceSort != 0 ? 20_000 : 600;
        for (Catalog.CardSet s : sets) {
            if (!priceSet.isEmpty() && !s.id().equals(priceSet)) continue;
            for (Catalog.Card c : s.cards()) {
                if (priceRarity != null && c.rarity() != priceRarity) continue;
                if (q.length() >= 2 && !c.name().getString().toLowerCase(Locale.ROOT).contains(q)) continue;
                if (priceBand != 0) {
                    long pv = cachedPrice(c);
                    if (pv < BAND_MIN[priceBand] || pv >= BAND_MAX[priceBand]) continue;
                }
                res.add(c);
                if (res.size() >= cap) break;
            }
            if (res.size() >= cap) break;
        }
        if (priceSort != 0) {
            res.sort(java.util.Comparator.comparingLong(this::cachedPrice));
            if (priceSort == 2) java.util.Collections.reverse(res);
        }
        if (res.size() > 600) res = new ArrayList<>(res.subList(0, 600));
        Ui.roundGradient(ctx, px, py, listW, ph, SURF2, SURF);
        Ui.outline(ctx, px, py, listW, ph, LINE);
        // chips de rango de precio + orden
        int chx = px + 4, chy = py + 3;
        for (int b = 0; b < BAND_LABEL.length; b++) {
            Text lbl = b == 0 ? Text.translatable("tcgshop.cat.all") : Text.literal(BAND_LABEL[b]);
            int cwid = textRenderer.getWidth(lbl) + 8;
            if (chx + cwid > px + listW - 30) break;
            boolean on = priceBand == b, hv = Ui.in(mouseX, mouseY, chx, chy, cwid, 12);
            Ui.round(ctx, chx, chy, cwid, 12, on ? withAlpha(a.color, 0.85f) : hv ? 0x44FFFFFF : 0x22FFFFFF);
            ctx.drawText(textRenderer, lbl, chx + 4, chy + 2, on ? 0xFFFFFFFF : 0xFFC8CCDA, false);
            int fb = b;
            areas.add(new Area(chx, chy, cwid, 12, () -> { priceBand = fb; listScroll = 0; }));
            chx += cwid + 3;
        }
        String so = priceSort == 0 ? "⇅" : priceSort == 1 ? "₽↑" : "₽↓";
        int sow = textRenderer.getWidth(so) + 8, sox = px + listW - sow - 4;
        Ui.round(ctx, sox, chy, sow, 12, priceSort != 0 ? withAlpha(Ui.GOLD, 0.6f) : 0x22FFFFFF);
        ctx.drawText(textRenderer, so, sox + 4, chy + 2, priceSort != 0 ? 0xFF1A1400 : 0xFFC8CCDA, false);
        areas.add(new Area(sox, chy, sow, 12, () -> { priceSort = (priceSort + 1) % 3; listScroll = 0; }));
        String cnt = res.size() >= 600 ? "600+" : String.valueOf(res.size());
        int rowH = 14, top = py + 18, h = ph - 21;
        ctx.drawText(textRenderer, cnt, sox - textRenderer.getWidth(cnt) - 4, chy + 2, 0xFF8A8FA8, false);
        listScroll = MathHelper.clamp(listScroll, 0, Math.max(0, res.size() * rowH - h));
        ctx.enableScissor(px + 1, top, px + listW - 1, top + h);
        for (int i = 0; i < res.size(); i++) {
            int y = top + i * rowH - (int) listScroll;
            if (y < top - rowH || y > top + h) continue;
            Catalog.Card c = res.get(i);
            boolean hover = Ui.in(mouseX, mouseY, px + 3, y, listW - 6, rowH - 1) && mouseY >= top && mouseY < top + h;
            boolean sel = c.key().equals(priceCard);
            if (sel) Ui.round(ctx, px + 3, y, listW - 6, rowH - 1, withAlpha(a.color, 0.4f));
            else if (hover) Ui.round(ctx, px + 3, y, listW - 6, rowH - 1, 0x22FFFFFF);
            ctx.fill(px + 6, y + 3, px + 9, y + rowH - 4, c.rarity().color);
            String price = PriceService.format(cachedPrice(c));
            int pwid = textRenderer.getWidth(price);
            ctx.drawText(textRenderer, Ui.ellipsis(c.name().getString(), listW - 24 - pwid), px + 13, y + 3, 0xFFFFFFFF, false);
            ctx.drawText(textRenderer, price, px + listW - 7 - pwid, y + 3, Ui.GOLD, false);
            String key = c.key();
            if (y >= top && y + rowH <= top + h + rowH) areas.add(new Area(px + 3, Math.max(y, top), listW - 6, rowH - 1, () -> priceCard = key));
        }
        ctx.disableScissor();
        if (res.isEmpty()) Ui.text(ctx, Text.translatable("tcgshop.phone.no_results"), px, py + ph / 2 - 5, listW, 10, Ui.MUTED, false);
        // ficha
        Ui.roundGradient(ctx, detX, py, detW, ph, SURF2, SURF);
        Ui.outline(ctx, detX, py, detW, ph, LINE);
        Catalog.Card sel = priceCard == null ? null : Catalog.card(priceCard);
        if (sel == null) {
            paragraph(ctx, Text.translatable("tcgshop.phone.search_hint"), detX + 8, py + 10, detW - 16, Ui.MUTED);
            return;
        }
        int cw = Math.min(64, detW / 2 - 10), ch = Math.round(cw / Draw.CARD_RATIO);
        Draw.glow(ctx, detX + 8 + cw / 2f, py + 8 + ch / 2f, cw, ch, sel.rarity().color, 0.25f);
        Draw.cardFace(ctx, sel.texture(), sel.stack(), detX + 8, py + 8, cw, ch, 1f);
        if (sel.rarity().tier > 0) Draw.holo(ctx, detX + 8, py + 8, cw, ch, sel.rarity().tier, sel.rarity().color, t, mouseX);
        int tx = detX + cw + 16, tw = detW - cw - 22, ty = py + 10;
        for (var line : textRenderer.wrapLines(sel.name().copy().formatted(Formatting.BOLD), tw)) {
            ctx.drawTextWithShadow(textRenderer, line, tx, ty, 0xFFFFFFFF);
            ty += 10;
        }
        ctx.drawTextWithShadow(textRenderer, sel.rarity().displayName(), tx, ty + 2, sel.rarity().color);
        Draw.scaledCentered(ctx, Text.literal(PriceService.format(PriceService.priceCd(sel.stack()))).formatted(Formatting.BOLD), tx + tw / 2f, ty + 16, 1.4f, Ui.GOLD);
        int owned = ClientCollection.count(sel.key());
        ctx.drawText(textRenderer, Text.translatable("tcgshop.phone.owned", owned), tx, ty + 34, owned > 0 ? 0xFF6BE07B : Ui.MUTED, false);
        // gráfica
        int gx = detX + 8, gy = py + ch + 16, gw = detW - 16, gh = Math.max(36, py + ph - gy - 8);
        Ui.round(ctx, gx, gy, gw, gh, 0x55000000);
        long day = PriceService.marketDay();
        long[] v = new long[30];
        long min = Long.MAX_VALUE, max = 0;
        for (int i = 0; i < 30; i++) {
            v[i] = PriceService.priceCdOnDay(sel.id(), sel.rarity(), Math.max(0, day - 29 + i));
            min = Math.min(min, v[i]);
            max = Math.max(max, v[i]);
        }
        int col = v[29] >= v[0] ? 0xFF6BE07B : 0xFFFF6B6B;
        for (int k = 1; k < 4; k++) ctx.fill(gx + 2, gy + 12 + (gh - 16) * k / 4, gx + gw - 2, gy + 13 + (gh - 16) * k / 4, 0x14FFFFFF);
        int prevX = -1, prevY = -1;
        for (int i = 0; i < 30; i++) {
            int x = gx + 3 + i * (gw - 6) / 29, y = gy + gh - 4 - (int) ((v[i] - min) * (gh - 18) / Math.max(1, max - min));
            ctx.fill(x, y + 2, x + 1, gy + gh - 2, withAlpha(col, 0.15f));
            if (prevX >= 0) {
                int steps = Math.max(1, Math.abs(x - prevX));
                for (int s = 0; s <= steps; s++) ctx.fill(prevX + (x - prevX) * s / steps, prevY + (y - prevY) * s / steps, prevX + (x - prevX) * s / steps + 1,
                        prevY + (y - prevY) * s / steps + 2, col);
            }
            prevX = x;
            prevY = y;
        }
        double ch30 = v[0] == 0 ? 0 : (v[29] - v[0]) * 100.0 / v[0];
        ctx.drawText(textRenderer, Text.translatable("tcgshop.phone.chart").append(String.format(Locale.ROOT, "  %+.1f%%", ch30)), gx + 4, gy + 3, col, false);
    }

    // ------------------------------------------------------------------ venta rápida

    private void sellApp(DrawContext ctx, int mouseX, int mouseY, double t) {
        AppDef a = def("sell");
        topBar(ctx, a, mouseX, mouseY);
        Text slogan = Text.translatable("tcgshop.sell.phone_slogan");
        ctx.drawText(textRenderer, slogan, sx + sw - 10 - textRenderer.getWidth(slogan), sy + 20, 0xCCFFFFFF, false);
        // submenú: vender lo del inventario o lo del Binder (antes eran dos apps separadas)
        int invCount = client == null || client.player == null ? 0 : CardPickGrid.cardSlots(client.player).size();
        int rw = rail(ctx, new String[]{"inv", "binder"}, new String[]{Text.translatable("tcgshop.sell.tab_inv").getString(),
                Text.translatable("tcgshop.sell.tab_binder").getString()}, new int[]{invCount, -1}, sellTab, a.color, mouseX, mouseY, k -> sellTab = k);
        int px = sx + rw, py = sy + 41, pw = sw - rw - 8, ph = sh - 52;
        if (sellTab.equals("binder")) { sellBinderTab(ctx, a, px, py, pw, ph, mouseX, mouseY); return; }
        int sideW = Math.min(140, pw / 3), gw = pw - sideW - 8;
        Ui.roundGradient(ctx, px, py, gw, ph, SURF2, SURF);
        Ui.outline(ctx, px, py, gw, ph, LINE);
        ItemStack hov = ItemStack.EMPTY;
        if (quickSell.isEmpty()) paragraph(ctx, Text.translatable("tcgshop.sell.no_cards"), px + 8, py + 10, gw - 16, Ui.MUTED);
        else hov = quickSell.render(ctx, px + 4, py + 4, gw - 8, ph - 8, mouseX, mouseY, 32);
        int x = px + gw + 8, y = py;
        Ui.roundGradient(ctx, x, y, sideW, ph, SURF2, SURF);
        Ui.outline(ctx, x, y, sideW, ph, LINE);
        ctx.fill(x + 1, y + 1, x + sideW - 1, y + 3, a.color);
        ctx.drawText(textRenderer, Text.translatable("tcgshop.phone.sell").formatted(Formatting.BOLD), x + 7, y + 8, 0xFFFFFFFF, false);
        int ty = paragraph(ctx, Text.translatable("tcgshop.sell.phone_hint"), x + 7, y + 21, sideW - 14, 0xFFB8BED6);
        // elegir todas / ninguna
        int hb = (sideW - 18) / 2;
        Ui.button(ctx, x + 6, ty + 4, hb, 14, Text.translatable("tcgshop.sell.all"), mouseX, mouseY, true, 0xFF4A5070);
        areas.add(new Area(x + 6, ty + 4, hb, 14, () -> { if (client != null) quickSell.sel.addAll(CardPickGrid.cardSlots(client.player)); }));
        Ui.button(ctx, x + 12 + hb, ty + 4, hb, 14, Text.translatable("tcgshop.sell.none"), mouseX, mouseY, !quickSell.sel.isEmpty(), 0xFF4A5070);
        areas.add(new Area(x + 12 + hb, ty + 4, hb, 14, quickSell.sel::clear));
        long got = client != null && client.player != null ? quickSell.total(client.player) : 0;
        int cards = 0;
        if (client != null && client.player != null) for (int sl : quickSell.sel) cards += client.player.getInventory().getStack(sl).getCount();
        int sy2 = ty + 26;
        ctx.drawText(textRenderer, Text.translatable("tcgshop.sell.count", cards), x + 7, sy2, 0xFFDDDDDD, false);
        ctx.drawText(textRenderer, Text.translatable("tcgshop.sell.market", PriceService.format(got * 2)), x + 7, sy2 + 11, 0xFF8A8FA8, false);
        Draw.scaledCentered(ctx, Text.literal("+" + PriceService.format(got)).formatted(Formatting.BOLD), x + sideW / 2f, sy2 + 26, 1.35f, Ui.GOLD);
        int bh = 20, bY = y + ph - bh - 6;
        boolean can = !quickSell.sel.isEmpty();
        boolean armed = can && now() - sellArmedAt < 3;
        Ui.button(ctx, x + 6, bY, sideW - 12, bh, Text.translatable(armed ? "tcgshop.sell.confirm" : "tcgshop.sell.sell_btn"), mouseX, mouseY, can,
                armed ? 0xFFB02020 : a.color);
        if (can) areas.add(new Area(x + 6, bY, sideW - 12, bh, () -> {
            if (now() - sellArmedAt < 3) {
                ClientPlayNetworking.send(new dev.tcgshop.network.MarketPayloads.Sell(-1, dev.tcgshop.network.MarketPayloads.SELL_CARDS,
                        new ArrayList<>(quickSell.sel)));
                quickSell.sel.clear();
                sellArmedAt = -10;
            } else sellArmedAt = now();
        }));
        if (!hov.isEmpty()) {
            long v = dev.tcgshop.shop.CardSales.unitValue(hov);
            ctx.drawTooltip(textRenderer, List.of(hov.getName(), Text.translatable("tcgshop.sell.market", PriceService.format(v)).formatted(Formatting.GRAY),
                    Text.translatable("tcgshop.sell.you_get", PriceService.format(quickSell.price.applyAsLong(hov))).formatted(Formatting.GOLD)), mouseX, mouseY);
        }
    }

    /** Pestaña Binder de la venta: resumen de tu colección y acceso al Mercado del Binder (venta en bloque, favoritas, ofertas). */
    private void sellBinderTab(DrawContext ctx, AppDef a, int px, int py, int pw, int ph, int mouseX, int mouseY) {
        Ui.roundGradient(ctx, px, py, pw, ph, SURF2, SURF);
        Ui.outline(ctx, px, py, pw, ph, LINE);
        ctx.fill(px + 1, py + 1, px + pw - 1, py + 3, a.color);
        long value = 0;
        int cards = 0, dupes = 0;
        for (var e : ClientCollection.counts.entrySet()) {
            var c = dev.tcgshop.client.binder.Catalog.card(e.getKey());
            if (c == null || e.getValue() <= 0) continue;
            value += PriceService.priceCd(c.stack()) * e.getValue();
            cards += e.getValue();
            if (e.getValue() > 1) dupes += e.getValue() - 1;
        }
        ctx.drawText(textRenderer, Text.translatable("tcgshop.sell.binder_title").formatted(Formatting.BOLD), px + 8, py + 9, 0xFFFFFFFF, false);
        int cw = (pw - 24) / 3, ty = py + 24;
        tile(ctx, px + 8, ty, cw, 34, "tcgshop.sell.b_cards", String.valueOf(cards), 0xFFFFFFFF);
        tile(ctx, px + 12 + cw, ty, cw, 34, "tcgshop.sell.b_dupes", String.valueOf(dupes), 0xFF8BE08B);
        tile(ctx, px + 16 + cw * 2, ty, cw, 34, "tcgshop.sell.b_value", compact(value), Ui.GOLD);
        int y = paragraph(ctx, Text.translatable("tcgshop.sell.binder_hint"), px + 8, ty + 42, pw - 16, 0xFFB8BED6);
        int bw = Math.min(220, pw - 16), bx = px + (pw - bw) / 2, by = Math.min(py + ph - 30, y + 8);
        Ui.button(ctx, bx, by, bw, 22, Text.translatable("tcgshop.sell.open_binder_market"), mouseX, mouseY, true, a.color);
        areas.add(new Area(bx, by, bw, 22, () -> {
            close();
            dev.tcgshop.client.ClientSocial.act("binder_open", "open");
        }));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double h, double v) {
        if (app == App.SELL) { quickSell.scroll(v); return true; }
        if (app == App.MARKET) market.scroll(v);
        else if (app == App.LEAGUE && hoverDeckList) deckScroll -= v * 30;
        else if (app == App.GRADE || app == App.LEAGUE) listScroll -= v * 30;
        else listScroll -= v * 28;
        return true;
    }

    // ------------------------------------------------------------------ mi tienda

    private void shopApp(DrawContext ctx, int mouseX, int mouseY, double t) {
        AppDef a = def("shop");
        topBar(ctx, a, mouseX, mouseY);
        List<ShopClient.ZoneInfo> mine = myZones();
        if (mine.isEmpty()) {
            paragraph(ctx, Text.translatable("tcgshop.phone.no_shop"), sx + 20, sy + 50, sw - 40, 0xFFCCCCCC);
            return;
        }
        zoneIndex = Math.floorMod(zoneIndex, mine.size());
        ShopClient.ZoneInfo z = mine.get(zoneIndex);
        // selector de tienda en la barra
        int barY = sy + 13, selW = 190, selX = sx + sw - selW - 10;
        Ui.round(ctx, selX, barY + 4, selW, 15, 0xAA0E1018);
        ctx.fill(selX + 3, barY + 7, selX + 6, barY + 16, z.tierInfo().color | 0xFF000000);
        Ui.text(ctx, Text.translatable("tcgshop.shop.tier." + z.tierInfo().key).append(" · " + z.core().toShortString()), selX + 14, barY + 4, selW - 28, 15, 0xFFFFFFFF, true);
        if (mine.size() > 1) {
            ctx.drawText(textRenderer, "›", selX + selW - 8, barY + 7, 0xFFFFFFFF, true);
            areas.add(new Area(selX, barY + 4, selW, 15, () -> zoneIndex++));
        }
        String[] tabs = {"summary", "staff", "security", "reputation", "reviews"};
        String[] labels = new String[tabs.length];
        int[] counts = new int[tabs.length];
        for (int i = 0; i < tabs.length; i++) {
            labels[i] = Text.translatable("tcgshop.phone.tab." + tabs[i]).getString();
            counts[i] = tabs[i].equals("reviews") ? z.reviews().size() : -1;
        }
        int rw = rail(ctx, tabs, labels, counts, shopTab, a.color, mouseX, mouseY, k -> { shopTab = k; listScroll = 0; });
        int px = sx + rw, py = sy + 41, pw = sw - rw - 8, ph = sh - 52;
        switch (shopTab) {
            case "staff" -> staffTab(ctx, z, px, py, pw, mouseX, mouseY);
            case "security" -> securityTab(ctx, z, px, py, pw);
            case "reputation" -> repTab(ctx, z, px, py, pw, ph);
            case "reviews" -> reviewsTab(ctx, z, px, py, pw, ph);
            default -> summaryTab(ctx, z, px, py, pw);
        }
    }

    private void summaryTab(DrawContext ctx, ShopClient.ZoneInfo z, int px, int py, int pw) {
        long profit = ShopClient.myRevenue - ShopClient.myCosts;
        Object[][] tiles = {
                {"tcgshop.phone.s_balance", PriceService.format(z.balance()), Ui.GOLD},
                {"tcgshop.phone.s_revenue", PriceService.format(z.revenue()), 0xFF6BE07B},
                {"tcgshop.phone.s_profit", PriceService.format(profit), profit >= 0 ? 0xFF6BE07B : 0xFFFF6B6B},
                {"tcgshop.phone.s_costs", PriceService.format(ShopClient.myCosts), 0xFFFF6B6B},
                {"tcgshop.phone.s_wages", PriceService.format(z.wages()), 0xFFFF9F6B},
                {"tcgshop.phone.s_sales", String.valueOf(z.sales()), 0xFFFFFFFF},
                {"tcgshop.phone.s_customers", String.valueOf(z.metric(3)), 0xFFFFFFFF},
                {"tcgshop.phone.s_deals", String.valueOf(z.metric(5)), 0xFFB983FF},
                {"tcgshop.phone.s_whales", String.valueOf(z.metric(6)), 0xFFFFD34D},
                {"tcgshop.phone.s_complaints", String.valueOf(z.metric(4)), 0xFFFF9F6B},
                {"tcgshop.phone.s_rating", z.reviews().isEmpty() ? "—" : String.format(Locale.ROOT, "%.1f ★", z.avgStars()), 0xFFFFD34D},
                {"tcgshop.phone.s_regulars", String.valueOf(z.regulars()), 0xFF6BC5FF}};
        int cols = pw >= 330 ? 4 : 3, gap = 5, tw = (pw - gap * (cols - 1)) / cols, th = 34;
        for (int i = 0; i < tiles.length; i++) {
            int x = px + (i % cols) * (tw + gap), y = py + (i / cols) * (th + gap);
            tile(ctx, x, y, tw, th, (String) tiles[i][0], (String) tiles[i][1], (int) tiles[i][2]);
        }
    }

    private void staffTab(DrawContext ctx, ShopClient.ZoneInfo z, int px, int py, int pw, int mouseX, int mouseY) {
        // dos puestos de caja (turno de día) y el guardia (siempre)
        int cw = (pw - 12) / 3;
        staffCard(ctx, z, px, py, cw, "cashier", z.cashier(), z.cashierName(), ShopServer.CASHIER_HIRE, ShopServer.CASHIER_WAGE,
                new ItemStack(TcgBlocks.CASH_REGISTER_ITEM), mouseX, mouseY);
        staffCard(ctx, z, px + cw + 6, py, cw, "cashier2", z.ext().getBoolean("cashier2"), z.ext().getString("cashier2_name"),
                ShopServer.CASHIER_HIRE, ShopServer.CASHIER_WAGE, new ItemStack(TcgBlocks.CASH_REGISTER_ITEM), mouseX, mouseY);
        staffCard(ctx, z, px + (cw + 6) * 2, py, cw, "guard", z.guard(), z.guardName(), ShopServer.GUARD_HIRE, ShopServer.GUARD_WAGE,
                new ItemStack(Items.IRON_SWORD), mouseX, mouseY);
        boolean day = z.ext().getBoolean("day");
        int y2 = paragraph(ctx, Text.translatable(day ? "tcgshop.phone.shift_day" : "tcgshop.phone.shift_night"), px + 4, py + 90, pw - 8,
                day ? 0xFF8BE08B : 0xFFFFC266);
        paragraph(ctx, Text.translatable("tcgshop.phone.staff_tip2"), px + 4, y2 + 3, pw - 8, 0xFFAAB0C8);
    }

    private void staffCard(DrawContext ctx, ShopClient.ZoneInfo z, int x, int y, int w, String role, boolean hired, String name, long hire, long wage,
                           ItemStack icon, int mouseX, int mouseY) {
        int h = 84;
        Ui.roundGradient(ctx, x, y, w, h, hired ? 0xF0213A2C : SURF2, SURF);
        Ui.outline(ctx, x, y, w, h, hired ? 0xFF43C463 : LINE);
        Ui.round(ctx, x + 6, y + 6, 26, 26, 0x44000000);
        var m = ctx.getMatrices();
        m.push();
        m.translate(x + 19, y + 19, 0);
        m.scale(1.3f, 1.3f, 1);
        ctx.drawItem(icon, -8, -8);
        m.pop();
        ctx.drawTextWithShadow(textRenderer, Text.translatable("tcgshop.role." + role).formatted(Formatting.BOLD), x + 38, y + 8, 0xFFFFFFFF);
        ctx.drawText(textRenderer, hired ? Text.literal(Ui.ellipsis(name, w - 44)) : Text.translatable("tcgshop.phone.vacant"), x + 38, y + 20,
                hired ? 0xFF8BE08B : 0xFF999999, false);
        Ui.text(ctx, Text.literal(Ui.ellipsis(Text.translatable("tcgshop.phone.hire_cost", PriceService.format(hire)).getString(), w - 12)), x + 6, y + 38, w - 12, 10, 0xFFCCCCCC, false);
        Ui.text(ctx, Text.literal(Ui.ellipsis(Text.translatable("tcgshop.phone.wage_cost", PriceService.format(wage)).getString(), w - 12)), x + 6, y + 49, w - 12, 10, 0xFFCCCCCC, false);
        int bw = w - 16, by = y + h - 20;
        Ui.button(ctx, x + 8, by, bw, 14, Text.translatable(hired ? "tcgshop.phone.fire" : "tcgshop.phone.hire"), mouseX, mouseY, true, hired ? 0xFF7A2020 : 0xFF3C8A4A);
        areas.add(new Area(x + 8, by, bw, 14, () -> ClientPlayNetworking.send(new StaffPayload(z.id(), role, !hired))));
    }

    private void securityTab(DrawContext ctx, ShopClient.ZoneInfo z, int px, int py, int pw) {
        int cols = 3, gap = 5, tw = (pw - gap * 2) / cols, th = 34;
        tile(ctx, px, py, tw, th, "tcgshop.phone.s_detained", String.valueOf(z.metric(1)), 0xFF6BE07B);
        tile(ctx, px + tw + gap, py, tw, th, "tcgshop.phone.s_recovered", String.valueOf(z.metric(2)), 0xFF6BE07B);
        tile(ctx, px + (tw + gap) * 2, py, tw, th, "tcgshop.phone.s_stolen", String.valueOf(z.metric(0)), 0xFFFF6B6B);
        tile(ctx, px, py + th + gap, tw, th, "tcgshop.phone.s_detectors", String.valueOf(z.metric(9)), 0xFFFFFFFF);
        tile(ctx, px + tw + gap, py + th + gap, tw * 2 + gap, th, "tcgshop.phone.s_guard", z.guard() ? z.guardName() : "—", z.guard() ? 0xFF6BC5FF : 0xFF888888);
        int y = py + (th + gap) * 2 + 4;
        y = paragraph(ctx, Text.translatable(z.guard() ? "tcgshop.phone.security_guard_on" : "tcgshop.phone.security_guard_off"), px + 4, y, pw - 8,
                z.guard() ? 0xFF8BE08B : 0xFFFFC266);
        paragraph(ctx, Text.translatable("tcgshop.phone.security_tip"), px + 4, y + 4, pw - 8, 0xFFAAB0C8);
    }

    private void repTab(DrawContext ctx, ShopClient.ZoneInfo z, int px, int py, int pw, int ph) {
        int rep = z.reputation();
        int col = rep >= 80 ? 0xFF43C463 : rep >= 50 ? 0xFFFFD34D : 0xFFE05050;
        int lw = 86;
        Ui.roundGradient(ctx, px, py, lw, ph, SURF2, SURF);
        Ui.outline(ctx, px, py, lw, ph, LINE);
        ring(ctx, px + lw / 2, py + 40, 30, 5, rep / 100f, col);
        Draw.scaledCentered(ctx, Text.literal(String.valueOf(rep)).formatted(Formatting.BOLD), px + lw / 2f, py + 33, 1.8f, col);
        Ui.text(ctx, Text.translatable("tcgshop.phone.w_rep"), px, py + 76, lw, 10, 0xFFCCCCCC, false);
        if (!z.reviews().isEmpty())
            Ui.text(ctx, Text.literal(String.format(Locale.ROOT, "%.1f ★", z.avgStars())), px, py + 90, lw, 10, Ui.GOLD, true);
        int x = px + lw + 8, w = pw - lw - 8, y = py;
        if (z.factors().isEmpty()) {
            paragraph(ctx, Text.translatable("tcgshop.phone.rep_wait"), x, y + 4, w, Ui.MUTED);
            return;
        }
        // tres notas independientes (Estética, Calidad/precio, Seguridad)
        String[] mk = {"m_aesthetics", "m_value", "m_safety"};
        int[] mc = {0xFFE07BD8, 0xFFFFD34D, 0xFF6BC5FF};
        if (z.factors().containsKey("m_value")) {
            int mw = (w - 8) / 3;
            for (int i = 0; i < 3; i++) {
                int v = z.factors().getOrDefault(mk[i], 0), mx0 = x + i * (mw + 4);
                Ui.roundGradient(ctx, mx0, y, mw, 26, SURF2, SURF);
                Ui.text(ctx, Text.translatable("tcgshop.rep." + mk[i]), mx0, y + 3, mw, 10, 0xFFCCD2E6, false);
                Ui.progress(ctx, mx0 + 5, y + 14, mw - 30, 5, v / 100f, mc[i]);
                String vs = String.valueOf(v);
                ctx.drawText(textRenderer, vs, mx0 + mw - 5 - textRenderer.getWidth(vs), y + 13, mc[i], false);
            }
            y += 32;
        }
        int mid = x + w / 2 + 30;
        for (var e : z.factors().entrySet()) {
            if (e.getKey().startsWith("m_")) continue;
            if (y > py + ph - 24) break;
            int v = e.getValue();
            ctx.drawText(textRenderer, Ui.ellipsis(Text.translatable("tcgshop.rep." + e.getKey()).getString(), w / 2 + 20), x, y + 2, 0xFFDDDDDD, false);
            int bw = Math.abs(v) * (x + w - mid - 22) / 15;
            ctx.fill(mid, y + 1, mid + 1, y + 10, 0x55FFFFFF);
            if (v > 0) ctx.fill(mid + 1, y + 3, mid + 1 + bw, y + 8, 0xFF6BE07B);
            else if (v < 0) ctx.fill(mid - bw, y + 3, mid, y + 8, 0xFFFF6B6B);
            String vs = (v > 0 ? "+" : "") + v;
            ctx.drawText(textRenderer, vs, x + w - textRenderer.getWidth(vs), y + 2, v > 0 ? 0xFF6BE07B : v < 0 ? 0xFFFF6B6B : 0xFFAAAAAA, false);
            y += 12;
        }
        String worst = null;
        int wv = Integer.MAX_VALUE;
        for (var e : z.factors().entrySet()) if (e.getValue() < wv && !e.getKey().equals("buzz") && !e.getKey().startsWith("m_")) { wv = e.getValue(); worst = e.getKey(); }
        if (worst != null) paragraph(ctx, Text.literal("✦ ").append(Text.translatable("tcgshop.rep.tip." + worst)), x, y + 6, w, 0xFFFFD34D);
    }

    private void reviewsTab(DrawContext ctx, ShopClient.ZoneInfo z, int px, int py, int pw, int ph) {
        List<ShopZones.Review> rv = z.reviews();
        Ui.roundGradient(ctx, px, py, pw, 26, SURF2, SURF);
        Ui.outline(ctx, px, py, pw, 26, LINE);
        double avg = z.avgStars();
        ctx.drawText(textRenderer, Text.literal(stars((int) Math.round(avg))).formatted(Formatting.BOLD), px + 8, py + 9, Ui.GOLD, true);
        ctx.drawText(textRenderer, Text.translatable("tcgshop.phone.reviews_head", String.format(Locale.ROOT, "%.1f", avg), rv.size(), z.regulars()),
                px + 70, py + 9, 0xFFDDDDDD, false);
        int top = py + 30, h = ph - 30, rowH = 26;
        if (rv.isEmpty()) {
            paragraph(ctx, Text.translatable("tcgshop.phone.no_reviews"), px + 8, top + 8, pw - 16, Ui.MUTED);
            return;
        }
        listScroll = MathHelper.clamp(listScroll, 0, Math.max(0, rv.size() * rowH - h));
        long day = client != null && client.world != null ? client.world.getTimeOfDay() / 24000 : 0;
        ctx.enableScissor(px, top, px + pw, top + h);
        for (int i = 0; i < rv.size(); i++) {
            int y = top + i * rowH - (int) listScroll;
            if (y + rowH < top || y > top + h) continue;
            ShopZones.Review r = rv.get(i);
            int sc = r.stars() >= 4 ? 0xFF43C463 : r.stars() <= 2 ? 0xFFE05050 : 0xFFFFD34D;
            Ui.roundGradient(ctx, px, y, pw, rowH - 3, SURF2, SURF);
            ctx.fill(px + 1, y + 1, px + 3, y + rowH - 4, sc);
            ctx.drawText(textRenderer, Text.literal(stars(r.stars())), px + 8, y + 3, Ui.GOLD, false);
            ctx.drawText(textRenderer, Text.literal(Ui.ellipsis(r.name(), pw / 2)).formatted(Formatting.BOLD), px + 64, y + 3, 0xFFFFFFFF, false);
            String when = day - r.day() <= 0 ? Text.translatable("tcgshop.phone.today").getString() : Text.translatable("tcgshop.phone.days_ago", day - r.day()).getString();
            ctx.drawText(textRenderer, when, px + pw - 6 - textRenderer.getWidth(when), y + 3, 0xFF8A8FA8, false);
            ctx.drawText(textRenderer, Ui.ellipsis("«" + Text.translatable("tcgshop.review." + r.key()).getString() + "»", pw - 16), px + 8, y + 13, 0xFFC8CCDA, false);
        }
        ctx.disableScissor();
    }

    private static String stars(int n) {
        n = Math.max(0, Math.min(5, n));
        return "★".repeat(n) + "☆".repeat(5 - n);
    }

    // ------------------------------------------------------------------ títulos

    private void titlesApp(DrawContext ctx, int mouseX, int mouseY) {
        AppDef a = def("titles");
        topBar(ctx, a, mouseX, mouseY);
        List<String> options = new ArrayList<>();
        options.add("");
        options.addAll(ClientCollection.titles);
        int cols = sw >= 420 ? 3 : 2, gap = 6, cw = (sw - 24 - gap * (cols - 1)) / cols, y0 = sy + 44;
        for (int i = 0; i < options.size(); i++) {
            String opt = options.get(i);
            int x = sx + 12 + (i % cols) * (cw + gap), y = y0 + (i / cols) * 20;
            if (y > sy + sh - 26) break;
            boolean sel = opt.equals(ClientCollection.title);
            Text label = opt.isEmpty() ? Text.translatable("tcgshop.binder.no_title") : Titles.display(opt);
            Ui.chip(ctx, x, y, cw, 16, label, sel, Ui.in(mouseX, mouseY, x, y, cw, 16), 0xFFB983FF, 0);
            areas.add(new Area(x, y, cw, 16, () -> ClientCollection.action(BinderActionPayload.TITLE_SET, opt, 0)));
        }
        if (ClientCollection.titles.isEmpty())
            paragraph(ctx, Text.translatable("tcgshop.binder.no_titles"), sx + 14, y0 + 30, sw - 28, 0xFFCCCCCC);
    }


    // ------------------------------------------------------------------ CobbleGrade

    private void gradeApp(DrawContext ctx, int mouseX, int mouseY, double t) {
        AppDef a = def("grade");
        topBar(ctx, a, mouseX, mouseY);
        ctx.drawText(textRenderer, Text.translatable("tcgshop.grade.slogan"), sx + sw - 10 - textRenderer.getWidth(Text.translatable("tcgshop.grade.slogan")),
                sy + 20, 0xCCFFFFFF, false);
        String[] tabs = {"send", "orders", "odds"};
        String[] labels = new String[tabs.length];
        int[] counts = new int[tabs.length];
        for (int i = 0; i < tabs.length; i++) {
            labels[i] = Text.translatable("tcgshop.grade.tab." + tabs[i]).getString();
            counts[i] = tabs[i].equals("orders") ? dev.tcgshop.client.ClientGrading.orders.size() : tabs[i].equals("send") ? gradeSel.size() : -1;
        }
        int rw = rail(ctx, tabs, labels, counts, gradeTab, a.color, mouseX, mouseY, k -> { gradeTab = k; listScroll = 0; });
        int px = sx + rw, py = sy + 41, pw = sw - rw - 8, ph = sh - 52;
        switch (gradeTab) {
            case "orders" -> gradeOrders(ctx, px, py, pw, ph, mouseX, mouseY);
            case "odds" -> gradeOdds(ctx, px, py, pw);
            default -> gradeSend(ctx, px, py, pw, ph, mouseX, mouseY, t);
        }
    }

    private List<Integer> gradeableSlots() {
        List<Integer> out = new ArrayList<>();
        if (client == null || client.player == null) return out;
        for (int i = 0; i < 36; i++) {
            ItemStack st = client.player.getInventory().getStack(i);
            if (dev.tcgshop.card.CardInfo.isCard(st) && !dev.tcgshop.grading.Grading.isGraded(st)) out.add(i);
        }
        return out;
    }

    private void gradeSend(DrawContext ctx, int px, int py, int pw, int ph, int mouseX, int mouseY, double t) {
        List<Integer> slots = gradeableSlots();
        gradeSel.retainAll(slots);
        int sideW = 128, gw = pw - sideW - 8;
        Ui.roundGradient(ctx, px, py, gw, ph, SURF2, SURF);
        Ui.outline(ctx, px, py, gw, ph, LINE);
        if (slots.isEmpty()) paragraph(ctx, Text.translatable("tcgshop.grade.no_cards"), px + 8, py + 10, gw - 16, Ui.MUTED);
        int cw = 34, chh = Math.round(cw / Draw.CARD_RATIO), gap = 5, cols = Math.max(1, (gw - 8 + gap) / (cw + gap));
        int rows = (slots.size() + cols - 1) / cols;
        listScroll = MathHelper.clamp(listScroll, 0, Math.max(0, rows * (chh + gap) - (ph - 8)));
        int ox = px + (gw - (cols * (cw + gap) - gap)) / 2;
        ItemStack hovered = ItemStack.EMPTY;
        ctx.enableScissor(px + 1, py + 1, px + gw - 1, py + ph - 1);
        for (int k = 0; k < slots.size(); k++) {
            int slot = slots.get(k);
            ItemStack st = client.player.getInventory().getStack(slot);
            int x = ox + (k % cols) * (cw + gap), y = py + 4 + (k / cols) * (chh + gap) - (int) listScroll;
            if (y + chh < py || y > py + ph) continue;
            boolean sel = gradeSel.contains(slot), hover = Ui.in(mouseX, mouseY, x, y, cw, chh) && Ui.in(mouseX, mouseY, px, py, gw, ph);
            Draw.cardFace(ctx, dev.tcgshop.card.CardInfo.texture(dev.tcgshop.card.CardInfo.id(st)), st, x, y, cw, chh, sel || hover ? 1f : 0.75f);
            if (st.getCount() > 1) ctx.drawText(textRenderer, "x" + st.getCount(), x + 2, y + chh - 9, 0xFFFFFFFF, true);
            if (sel) {
                Ui.outline(ctx, x - 1, y - 1, cw + 2, chh + 2, Ui.GOLD);
                Ui.outline(ctx, x - 2, y - 2, cw + 4, chh + 4, withAlpha(Ui.GOLD, 0.5f));
                Ui.round(ctx, x + cw - 11, y + 2, 9, 9, 0xFF2E9C5A);
                ctx.drawText(textRenderer, "✔", x + cw - 10, y + 3, 0xFFFFFFFF, false);
            } else if (hover) Ui.outline(ctx, x - 1, y - 1, cw + 2, chh + 2, 0xFFFFFFFF);
            if (hover) hovered = st;
            if (y >= py && y + chh <= py + ph) areas.add(new Area(x, y, cw, chh, () -> {
                if (!gradeSel.remove(slot) && gradeSel.size() < dev.tcgshop.grading.Grading.MAX_PER_ORDER) gradeSel.add(slot);
            }));
        }
        ctx.disableScissor();
        // panel lateral: servicio, total y enviar
        int x = px + gw + 8, y = py;
        Ui.roundGradient(ctx, x, y, sideW, ph, SURF2, SURF);
        Ui.outline(ctx, x, y, sideW, ph, LINE);
        ctx.drawText(textRenderer, Text.translatable("tcgshop.grade.service").formatted(Formatting.BOLD), x + 6, y + 5, 0xFFFFFFFF, false);
        long total = 0;
        dev.tcgshop.grading.Grading.Service[] svc = dev.tcgshop.grading.Grading.Service.values();
        for (int i = 0; i < svc.length; i++) {
            dev.tcgshop.grading.Grading.Service sv = svc[i];
            int by = y + 17 + i * 30;
            boolean sel = gradeService == i, hover = Ui.in(mouseX, mouseY, x + 4, by, sideW - 8, 27);
            Ui.round(ctx, x + 4, by, sideW - 8, 27, sel ? withAlpha(0xFF1E8FA8, 0.55f) : hover ? 0x33FFFFFF : 0x22000000);
            if (sel) Ui.outline(ctx, x + 4, by, sideW - 8, 27, 0xFF6BE0F5);
            ctx.drawText(textRenderer, Text.translatable("tcgshop.grade.service." + sv.key).formatted(Formatting.BOLD), x + 9, by + 4, 0xFFFFFFFF, false);
            ctx.drawText(textRenderer, dev.tcgshop.client.ClientGrading.duration(sv.ticks), x + 9, by + 15, 0xFFB8D8E8, false);
            String fee = compact(sv.baseFee) + " +" + Math.round(sv.pct * 100) + "%";
            ctx.drawText(textRenderer, fee, x + sideW - 9 - textRenderer.getWidth(fee), by + 15, Ui.GOLD, false);
            int fi = i;
            areas.add(new Area(x + 4, by, sideW - 8, 27, () -> gradeService = fi));
        }
        dev.tcgshop.grading.Grading.Service cur = dev.tcgshop.grading.Grading.Service.of(gradeService);
        for (int slot : gradeSel) total += cur.fee(ShopServer.priceOf(client.player.getInventory().getStack(slot).copyWithCount(1), 1.0));
        int ty = y + 17 + svc.length * 30 + 4;
        ctx.drawText(textRenderer, Text.translatable("tcgshop.grade.selected", gradeSel.size(), dev.tcgshop.grading.Grading.MAX_PER_ORDER), x + 6, ty, 0xFFDDDDDD, false);
        ctx.drawText(textRenderer, Text.translatable("tcgshop.grade.total", PriceService.format(total)).formatted(Formatting.BOLD), x + 6, ty + 11, Ui.GOLD, false);
        int bh = 18, bY = y + ph - bh - 6;
        boolean can = !gradeSel.isEmpty();
        Ui.button(ctx, x + 6, bY, sideW - 12, bh, Text.translatable("tcgshop.grade.send"), mouseX, mouseY, can, 0xFF1E8FA8);
        if (can) areas.add(new Area(x + 6, bY, sideW - 12, bh, () -> {
            ClientPlayNetworking.send(new dev.tcgshop.network.GradePayloads.Action(dev.tcgshop.network.GradePayloads.SUBMIT, gradeService,
                    new ArrayList<>(gradeSel), 0));
            gradeSel.clear();
            gradeTab = "orders";
        }));
        paragraph(ctx, Text.translatable("tcgshop.grade.send_note"), x + 6, ty + 26, sideW - 12, 0xFF8A8FA8);
        if (!hovered.isEmpty()) {
            long v = ShopServer.priceOf(hovered.copyWithCount(1), 1.0);
            ctx.drawTooltip(textRenderer, List.of(hovered.getName(), Text.literal(PriceService.format(v)).formatted(Formatting.GOLD),
                    Text.translatable("tcgshop.grade.fee_one", PriceService.format(cur.fee(v))).formatted(Formatting.GRAY)), mouseX, mouseY);
        }
    }

    private void gradeOrders(DrawContext ctx, int px, int py, int pw, int ph, int mouseX, int mouseY) {
        List<dev.tcgshop.network.GradePayloads.Order> orders = dev.tcgshop.client.ClientGrading.orders;
        if (orders.isEmpty()) {
            paragraph(ctx, Text.translatable("tcgshop.grade.no_orders"), px + 6, py + 8, pw - 12, Ui.MUTED);
            return;
        }
        long now = dev.tcgshop.client.ClientGrading.now();
        int rowH = 46;
        listScroll = MathHelper.clamp(listScroll, 0, Math.max(0, orders.size() * rowH - ph));
        ctx.enableScissor(px, py, px + pw, py + ph);
        for (int i = 0; i < orders.size(); i++) {
            var o = orders.get(i);
            int y = py + i * rowH - (int) listScroll;
            if (y + rowH < py || y > py + ph) continue;
            boolean ready = now >= o.ready();
            Ui.roundGradient(ctx, px, y, pw, rowH - 4, ready ? 0xF0213A2C : SURF2, SURF);
            Ui.outline(ctx, px, y, pw, rowH - 4, ready ? 0xFF43C463 : LINE);
            // miniaturas
            int tw = 16, th = Math.round(tw / Draw.CARD_RATIO);
            for (int k = 0; k < Math.min(5, o.cards().size()); k++) {
                ItemStack st = o.cards().get(k);
                Draw.cardFace(ctx, dev.tcgshop.card.CardInfo.texture(dev.tcgshop.card.CardInfo.id(st)), st, px + 6 + k * 9, y + 9, tw, th, ready ? 1f : 0.55f);
            }
            int tx = px + 6 + Math.min(5, o.cards().size()) * 9 + 14;
            ctx.drawText(textRenderer, Text.translatable("tcgshop.grade.order", o.id(), o.cards().size()).formatted(Formatting.BOLD), tx, y + 5, 0xFFFFFFFF, false);
            ctx.drawText(textRenderer, Text.translatable("tcgshop.grade.service." + dev.tcgshop.grading.Grading.Service.of(o.service()).key), tx, y + 16, 0xFFB8D8E8, false);
            float prog = (float) MathHelper.clamp((now - o.submitted()) / (double) Math.max(1, o.ready() - o.submitted()), 0, 1);
            int barW = pw - (tx - px) - 96;
            Ui.progress(ctx, tx, y + 28, Math.max(30, barW), 6, prog, ready ? 0xFF43C463 : 0xFF1E8FA8);
            if (ready) {
                int bx = px + pw - 84;
                Ui.button(ctx, bx, y + 11, 76, 18, Text.translatable("tcgshop.grade.collect"), mouseX, mouseY, true, 0xFF3C8A4A);
                int id = o.id();
                areas.add(new Area(bx, y + 11, 76, 18, () -> ClientPlayNetworking.send(new dev.tcgshop.network.GradePayloads.Action(
                        dev.tcgshop.network.GradePayloads.COLLECT, 0, List.of(), id))));
            } else {
                Text left = dev.tcgshop.client.ClientGrading.duration(o.ready() - now);
                ctx.drawText(textRenderer, Text.translatable("tcgshop.grade.left", left), px + pw - 8 - textRenderer.getWidth(Text.translatable("tcgshop.grade.left", left)),
                        y + 16, 0xFFDDDDDD, false);
            }
        }
        ctx.disableScissor();
    }

    private void gradeOdds(DrawContext ctx, int px, int py, int pw) {
        ctx.drawText(textRenderer, Text.translatable("tcgshop.grade.odds_title").formatted(Formatting.BOLD), px + 2, py + 2, 0xFFFFFFFF, false);
        int y = py + 16, labelW = 62, multW = 70, barMax = pw - labelW - multW - 46;
        double maxPct = 0;
        for (var b : dev.tcgshop.grading.Grading.ODDS) maxPct = Math.max(maxPct, b.percent());
        for (var b : dev.tcgshop.grading.Grading.ODDS) {
            int col = dev.tcgshop.grading.Grading.color(b.to());
            String range = b.from() == b.to() ? dev.tcgshop.grading.Grading.format(b.to())
                    : dev.tcgshop.grading.Grading.format(b.from()) + "–" + dev.tcgshop.grading.Grading.format(b.to());
            Ui.round(ctx, px, y, pw, 15, 0x33000000);
            ctx.drawText(textRenderer, Text.literal(range).formatted(Formatting.BOLD), px + 5, y + 4, col, false);
            int bw = (int) Math.round(barMax * b.percent() / maxPct);
            ctx.fillGradient(px + labelW, y + 4, px + labelW + Math.max(2, bw), y + 11, Ui.brighten(col, 30), col);
            String pct = (b.percent() == Math.floor(b.percent()) ? String.valueOf((int) b.percent()) : String.valueOf(b.percent())) + "%";
            ctx.drawText(textRenderer, pct, px + labelW + Math.max(2, bw) + 4, y + 4, 0xFFFFFFFF, false);
            String mult = String.format(Locale.ROOT, b.from() == b.to() ? "x%.1f" : "x%.1f–%.1f", dev.tcgshop.grading.Grading.multiplier(b.from()),
                    dev.tcgshop.grading.Grading.multiplier(b.to()));
            ctx.drawText(textRenderer, mult, px + pw - 6 - textRenderer.getWidth(mult), y + 4, Ui.GOLD, false);
            y += 17;
        }
        paragraph(ctx, Text.translatable("tcgshop.grade.odds_note"), px + 2, y + 4, pw - 4, 0xFFAAB0C8);
    }


    // ------------------------------------------------------------------ Liga TCG (TCG real)

    private com.google.gson.JsonObject lg() { return dev.tcgshop.client.ClientPtcg.league; }

    private static int gi(com.google.gson.JsonObject o, String k) { return o != null && o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsInt() : 0; }

    private static String gs(com.google.gson.JsonObject o, String k) { return o != null && o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : ""; }

    private void leagueSend(String action, List<String> deck) {
        com.google.gson.JsonObject o = new com.google.gson.JsonObject();
        o.addProperty("a", action);
        if (deck != null) {
            com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
            deck.forEach(arr::add);
            o.add("deck", arr);
        }
        dev.tcgshop.client.ClientPtcg.leagueAction(o);
    }

    private void leagueApp(DrawContext ctx, int mouseX, int mouseY, double t) {
        AppDef a = def("league");
        topBar(ctx, a, mouseX, mouseY);
        com.google.gson.JsonObject L = lg();
        if (L != null && dev.tcgshop.client.ClientPtcg.leagueVersion != deckVersion && !deckDirty) {
            deckVersion = dev.tcgshop.client.ClientPtcg.leagueVersion;
            deckEdit.clear();
            for (var e : L.getAsJsonArray("deck")) deckEdit.add(e.getAsString());
        }
        String[] tabs = {"play", "rank", "deck", "coins", "boards", "goals", "rules"};
        String[] labels = new String[tabs.length];
        int[] counts = new int[tabs.length];
        for (int i = 0; i < tabs.length; i++) {
            labels[i] = Text.translatable("tcgshop.league.tab." + tabs[i]).getString();
            counts[i] = tabs[i].equals("deck") ? deckEdit.size() : -1;
        }
        int rw = rail(ctx, tabs, labels, counts, leagueTab, a.color, mouseX, mouseY, k -> { leagueTab = k; listScroll = 0; deckScroll = 0; });
        int px = sx + rw, py = sy + 41, pw = sw - rw - 8, ph = sh - 52;
        if (L == null) {
            paragraph(ctx, Text.translatable("tcgshop.league.loading"), px + 6, py + 8, pw - 12, Ui.MUTED);
            return;
        }
        switch (leagueTab) {
            case "deck" -> leagueDeck(ctx, L, px, py, pw, ph, mouseX, mouseY);
            case "rules" -> paragraph(ctx, Text.translatable("tcgshop.ptcg.rules"), px + 4, py + 4, pw - 8, 0xFFDDE3F0);
            case "play" -> leaguePlay(ctx, L, px, py, pw, ph, mouseX, mouseY, t);
            case "coins" -> listScroll = cosPanel.coins(ctx, L, px, py, pw, ph, mouseX, mouseY, t, listScroll, (x, y, w, h, r) -> areas.add(new Area(x, y, w, h, r)));
            case "boards" -> listScroll = cosPanel.boards(ctx, L, px, py, pw, ph, mouseX, mouseY, t, listScroll, (x, y, w, h, r) -> areas.add(new Area(x, y, w, h, r)));
            case "goals" -> listScroll = cosPanel.goals(ctx, L, px, py, pw, ph, mouseX, mouseY, t, listScroll);
            default -> leagueRank(ctx, L, px, py, pw, ph);
        }
    }

    private static int rankOf(int rating) {
        int k = 0;
        for (int i = 0; i < dev.tcgshop.duel.League.RANK_AT.length; i++) if (rating >= dev.tcgshop.duel.League.RANK_AT[i]) k = i;
        return k;
    }

    private static final int[] RANK_COLORS = {0xFFB0763A, 0xFFC0C8D4, 0xFFFFC233, 0xFF6FE3D9, 0xFF7FC8FF, 0xFFE05AFF};

    private void leagueRank(DrawContext ctx, com.google.gson.JsonObject L, int px, int py, int pw, int ph) {
        int rating = gi(L, "rating"), rank = rankOf(rating), col = RANK_COLORS[rank];
        int lw = 104;
        Ui.roundGradient(ctx, px, py, lw, ph, SURF2, SURF);
        Ui.outline(ctx, px, py, lw, ph, LINE);
        int cx = px + lw / 2, cy = py + 34;
        StylePainter.circle(ctx, cx, cy, 24, Ui.darken(col, 60));
        StylePainter.circle(ctx, cx, cy, 21, col);
        StylePainter.circle(ctx, cx, cy, 14, Ui.darken(col, 30));
        Draw.scaledCentered(ctx, Text.literal(String.valueOf(rank + 1)).formatted(Formatting.BOLD), cx, cy - 6, 1.6f, 0xFFFFFFFF);
        Ui.text(ctx, Text.translatable("tcgshop.league.rank." + rank).formatted(Formatting.BOLD), px, py + 62, lw, 10, col, true);
        Ui.text(ctx, Text.literal(rating + " pts"), px, py + 74, lw, 10, 0xFFFFFFFF, false);
        int w = gi(L, "wins"), l = gi(L, "losses");
        Ui.text(ctx, Text.translatable("tcgshop.league.wl", w, l), px, py + 90, lw, 10, 0xFFCCCCCC, false);
        Ui.text(ctx, Text.translatable("tcgshop.league.winrate", w + l == 0 ? 0 : Math.round(w * 100f / (w + l))), px, py + 101, lw, 10, 0xFFCCCCCC, false);
        Ui.text(ctx, Text.translatable("tcgshop.league.ai_wins", gi(L, "aiWins")), px, py + 112, lw, 10, 0xFF8BE0FF, false);
        int[] at = dev.tcgshop.duel.League.RANK_AT;
        if (rank + 1 < at.length) {
            Ui.progress(ctx, px + 8, py + 126, lw - 16, 5, (rating - at[rank]) / (float) (at[rank + 1] - at[rank]), col);
            Ui.text(ctx, Text.translatable("tcgshop.league.next", at[rank + 1] - rating), px, py + 133, lw, 10, 0xFF9AA0B8, false);
        }
        int x = px + lw + 8, tw = pw - lw - 8;
        ctx.drawText(textRenderer, Text.translatable("tcgshop.league.top").formatted(Formatting.BOLD), x, py + 2, 0xFFFFFFFF, false);
        var top = L.getAsJsonArray("top");
        if (top.isEmpty()) paragraph(ctx, Text.translatable("tcgshop.league.no_top"), x, py + 16, tw, Ui.MUTED);
        for (int i = 0; i < top.size(); i++) {
            var r = top.get(i).getAsJsonObject();
            int y = py + 14 + i * 15;
            if (y > py + ph - 34) break;
            Ui.round(ctx, x, y, tw, 13, i % 2 == 0 ? 0x33000000 : 0x1A000000);
            ctx.drawText(textRenderer, "#" + (i + 1), x + 4, y + 3, i < 3 ? 0xFFFFD34D : 0xFFAAAAAA, false);
            ctx.drawText(textRenderer, Ui.ellipsis(gs(r, "name"), tw - 110), x + 24, y + 3, 0xFFFFFFFF, false);
            String st = gi(r, "rating") + "  " + gi(r, "wins") + "-" + gi(r, "losses");
            ctx.drawText(textRenderer, st, x + tw - 4 - textRenderer.getWidth(st), y + 3, RANK_COLORS[rankOf(gi(r, "rating"))], false);
        }
        paragraph(ctx, Text.translatable("tcgshop.league.how"), x, py + ph - 30, tw, 0xFF9AA0B8);
    }

    private double deckScroll;
    private int deckVersion = -1;

    private com.google.gson.JsonObject poolEntry(com.google.gson.JsonObject L, String f) {
        for (var e : L.getAsJsonArray("pool")) if (gs(e.getAsJsonObject(), "f").equals(f)) return e.getAsJsonObject();
        for (var e : L.getAsJsonArray("energies")) if (gs(e.getAsJsonObject(), "f").equals(f)) return e.getAsJsonObject();
        return null;
    }

    private boolean isEnergy(com.google.gson.JsonObject L, String f) {
        for (var e : L.getAsJsonArray("energies")) if (gs(e.getAsJsonObject(), "f").equals(f)) return true;
        return false;
    }

    private int deckFilter;
    private static final String[] DECK_FILTERS = {"all", "pokemon", "trainer", "energy"};

    /** Imagen real de la carta (cobbletcg) o una tarjeta de color con el nombre si no hay imagen. */
    private void deckCard(DrawContext ctx, String f, com.google.gson.JsonObject info, int x, int y, int w, int h, float alpha) {
        ItemStack st = dev.tcgshop.client.ClientPtcg.stack(f);
        ctx.fill(x, y + 1, x + w + 1, y + h + 2, Draw.withAlpha(0xFF000000, 0.35f * alpha));
        if (!st.isEmpty()) {
            Draw.cardFace(ctx, dev.tcgshop.card.CardInfo.texture(dev.tcgshop.card.CardInfo.id(st)), st, x, y, w, h, alpha);
            return;
        }
        int stype = gi(info, "st");
        int col = stype == 1 ? 0xFFE0A040 : stype == 2 ? 0xFF6BC5FF : 0xFFF2CC2E;
        if (info != null && info.has("type")) col = dev.tcgshop.client.ClientPtcg.typeColor(gi(info, "type"));
        ctx.fillGradient(x, y, x + w, y + h, Draw.withAlpha(Ui.brighten(col, 50), alpha), Draw.withAlpha(Ui.darken(col, 30), alpha));
        Ui.outline(ctx, x, y, w, h, Draw.withAlpha(0xFFF4E7A8, alpha));
        String n = info == null ? f : gs(info, "n");
        var m = ctx.getMatrices();
        m.push();
        float sc = Math.min(0.55f, (w - 3f) / Math.max(1, textRenderer.getWidth(n)));
        m.translate(x + 1.5f, y + 2, 0);
        m.scale(Math.max(0.3f, sc), Math.max(0.3f, sc), 1);
        ctx.drawText(textRenderer, n, 0, 0, Draw.withAlpha(0xFF1A2236, alpha), false);
        m.pop();
    }

    private static boolean deckFilterOk(int filter, com.google.gson.JsonObject e, boolean energy) {
        int st = energy ? 3 : gi(e, "st");
        return filter == 0 || (filter == 1 && st == 1) || (filter == 2 && st == 2) || (filter == 3 && st == 3);
    }

    private void leagueDeck(DrawContext ctx, com.google.gson.JsonObject L, int px, int py, int pw, int ph, int mouseX, int mouseY) {
        String valid = gs(L, "valid");
        String msg = gs(L, "msg");
        boolean checked = deckDirty && L.has("check") && L.get("check").getAsBoolean();
        String shown = !deckDirty || checked ? valid : "";
        // ---- cabecera: contador con barra, estado y botones
        int n = deckEdit.size();
        int col = n == 60 ? 0xFF6BE07B : n > 60 ? 0xFFFF6B6B : 0xFFFFC233;
        ctx.drawText(textRenderer, Text.literal(n + "/60").formatted(Formatting.BOLD), px + 2, py + 1, col, true);
        Ui.progress(ctx, px + 36, py + 3, 60, 5, Math.min(1f, n / 60f), col);
        String status;
        int scol;
        if (!shown.isEmpty() && !shown.equals("EMPTY")) { status = "✖ " + deckErr(shown); scol = 0xFFFF8A80; }
        else if (checked || (!deckDirty && valid.isEmpty() && n == 60)) { status = "✔ " + Text.translatable("tcgshop.league.play.deck_ok").getString(); scol = 0xFF6BE07B; }
        else if (!deckDirty && msg.equals("SAVED")) { status = Text.translatable("tcgshop.league.play.saved").getString(); scol = 0xFF6BE07B; }
        else if (deckDirty) { status = Text.translatable("tcgshop.league.deck.unsaved").getString(); scol = 0xFFFFC233; }
        else { status = ""; scol = 0; }
        int bx = px + pw - 4;
        String[][] btns = {{"save", "tcgshop.league.save"}, {"check", "tcgshop.league.check"}, {"clear", "tcgshop.league.deck.clear"}, {"auto", "tcgshop.league.auto"}};
        for (String[] b : btns) {
            Text label = Text.translatable(b[1]);
            int w = textRenderer.getWidth(label) + 10;
            bx -= w + 3;
            boolean en = switch (b[0]) {
                case "save" -> deckDirty && n == 60;
                case "check" -> n == 60 && deckDirty && !checked;
                case "clear" -> n > 0;
                default -> true;
            };
            int accent = switch (b[0]) { case "save" -> 0xFF3C8A4A; case "check" -> 0xFF6A5A20; case "clear" -> 0xFF7A2A3A; default -> 0xFF5A4AA0; };
            Ui.button(ctx, bx, py, w, 13, label, mouseX, mouseY, en, accent);
            if (en) areas.add(new Area(bx, py, w, 13, switch (b[0]) {
                case "save" -> () -> { deckDirty = false; leagueSend("save", new ArrayList<>(deckEdit)); };
                case "check" -> () -> leagueSend("check", new ArrayList<>(deckEdit));
                case "clear" -> () -> { deckEdit.clear(); deckDirty = true; staleCheck(); };
                default -> () -> { deckDirty = false; leagueSend("auto", deckEdit.size() < 60 ? new ArrayList<>(deckEdit) : null); };
            }));
        }
        if (!status.isEmpty()) ctx.drawText(textRenderer, Ui.ellipsis(status, bx - px - 104), px + 102, py + 3, scol, false);
        int top = py + 17, h = ph - 17;
        int colW = Math.round((pw - 6) * 0.6f), dw = pw - 6 - colW, dx = px + colW + 6;
        String hoverF = null;
        com.google.gson.JsonObject hoverInfo = null;
        // ---- izquierda: tu colección como cartas (clic = añadir)
        Ui.roundGradient(ctx, px, top, colW, h, SURF2, SURF);
        Ui.outline(ctx, px, top, colW, h, LINE);
        int fx = px + 4;
        for (int k = 0; k < DECK_FILTERS.length; k++) {
            Text label = Text.translatable("tcgshop.league.deck.filter." + DECK_FILTERS[k]);
            int w = Ui.chipWidth(label);
            boolean hov = Ui.in(mouseX, mouseY, fx, top + 3, w, 12);
            Ui.chip(ctx, fx, top + 3, w, 12, label, deckFilter == k, hov, 0xFF4FB4FF, 0);
            int kk = k;
            areas.add(new Area(fx, top + 3, w, 12, () -> { deckFilter = kk; listScroll = 0; }));
            fx += w + 3;
        }
        // lista de lo que se puede añadir: energías básicas (gratis) + tus cartas
        List<com.google.gson.JsonObject> items = new ArrayList<>();
        for (var en : L.getAsJsonArray("energies")) {
            var eo = en.getAsJsonObject().deepCopy();
            eo.addProperty("energy", true);
            if (deckFilterOk(deckFilter, eo, true)) items.add(eo);
        }
        for (var e : L.getAsJsonArray("pool")) {
            var eo = e.getAsJsonObject();
            if (deckFilterOk(deckFilter, eo, false)) items.add(eo);
        }
        int gy = top + 19, gh = h - 21, gap = 4;
        int cw = Math.max(24, Math.min(34, (colW - 10) / 6 - gap)), chh = Math.round(cw / Draw.CARD_RATIO);
        int cols = Math.max(1, (colW - 8 + gap) / (cw + gap)), rowH = chh + gap + 7;
        int rows = (items.size() + cols - 1) / cols;
        listScroll = MathHelper.clamp(listScroll, 0, Math.max(0, rows * rowH - gh + 4));
        ctx.enableScissor(px + 1, gy, px + colW - 1, gy + gh);
        for (int k = 0; k < items.size(); k++) {
            var e = items.get(k);
            boolean energy = e.has("energy");
            String f = gs(e, "f");
            int x = px + 5 + (k % cols) * (cw + gap), y = gy + 2 + (k / cols) * rowH - (int) listScroll;
            if (y + rowH < gy || y > gy + gh) continue;
            int inDeck = 0;
            for (String d : deckEdit) if (d.equals(f)) inDeck++;
            int max = energy ? 60 : gi(e, "count");
            boolean can = n < 60 && (energy || inDeck < max);
            boolean hov = Ui.in(mouseX, mouseY, x, y, cw, chh) && mouseY >= gy && mouseY < gy + gh;
            if (hov) { hoverF = f; hoverInfo = e; }
            deckCard(ctx, f, e, x, y - (hov ? 2 : 0), cw, chh, can ? 1f : 0.4f);
            if (hov) Ui.outline(ctx, x - 1, y - 3, cw + 2, chh + 2, can ? 0xFF4FD1FF : 0xFFFF6B6B);
            if (gi(e, "graded") > 0) {
                ctx.fill(x, y, x + 9, y + 6, 0xFFFFC233);
                var mm = ctx.getMatrices();
                mm.push();
                mm.translate(x + 1, y + 1, 0);
                mm.scale(0.5f, 0.5f, 1);
                ctx.drawText(textRenderer, "CG", 0, 0, 0xFF1A2236, false);
                mm.pop();
            }
            if (inDeck > 0) Ui.badge(ctx, x + cw - 2, y - 2, inDeck);
            String cnt = energy ? "∞" : inDeck + "/" + max;
            var mm = ctx.getMatrices();
            mm.push();
            mm.translate(x + cw / 2f - textRenderer.getWidth(cnt) * 0.3f, y + chh + 1.5f, 0);
            mm.scale(0.6f, 0.6f, 1);
            ctx.drawText(textRenderer, cnt, 0, 0, can ? 0xFFFFD34D : 0xFF777788, false);
            mm.pop();
            if (can && y >= gy) areas.add(new Area(x, y, cw, chh, () -> {
                deckEdit.add(f);
                deckDirty = true;
                staleCheck();
                if (client != null) client.getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.master(dev.tcgshop.TcgSounds.CARD_SLIDE, 1.3f, 0.4f));
            }));
        }
        ctx.disableScissor();
        if (items.isEmpty()) paragraph(ctx, Text.translatable("tcgshop.league.no_pool"), px + 6, gy + 6, colW - 12, Ui.MUTED);
        // ---- derecha: el mazo como cartas agrupadas (clic = quitar una)
        Ui.roundGradient(ctx, dx, top, dw, h, SURF2, SURF);
        Ui.outline(ctx, dx, top, dw, h, LINE);
        java.util.LinkedHashMap<String, Integer> counts = new java.util.LinkedHashMap<>();
        List<String> sorted = new ArrayList<>(deckEdit);
        sorted.sort(java.util.Comparator.comparingInt((String f) -> { var e = poolEntry(L, f); return isEnergy(L, f) ? 2 : e == null ? 3 : gi(e, "st") == 1 ? 0 : gi(e, "st") == 2 ? 1 : 2; })
                .thenComparing(f -> f));
        int nP = 0, nT = 0, nE = 0;
        for (String f : sorted) {
            counts.merge(f, 1, Integer::sum);
            var e = poolEntry(L, f);
            if (isEnergy(L, f) || (e != null && gi(e, "st") == 3)) nE++;
            else if (e != null && gi(e, "st") == 2) nT++;
            else nP++;
        }
        var mm0 = ctx.getMatrices();
        mm0.push();
        mm0.translate(dx + 4, top + 4, 0);
        mm0.scale(0.75f, 0.75f, 1);
        ctx.drawText(textRenderer, Text.translatable("tcgshop.league.deck.split", nP, nT, nE), 0, 0, 0xFFDDE3F0, false);
        mm0.pop();
        List<String> keys = new ArrayList<>(counts.keySet());
        int dgy = top + 14, dgh = h - 16, dcw = Math.max(20, Math.min(26, (dw - 8) / 4 - 3)), dch = Math.round(dcw / Draw.CARD_RATIO);
        int dcols = Math.max(1, (dw - 6 + 3) / (dcw + 3)), drow = dch + 4;
        int drows = (keys.size() + dcols - 1) / dcols;
        deckScroll = MathHelper.clamp(deckScroll, 0, Math.max(0, drows * drow - dgh + 4));
        ctx.enableScissor(dx + 1, dgy, dx + dw - 1, dgy + dgh);
        for (int k = 0; k < keys.size(); k++) {
            String f = keys.get(k);
            int x = dx + 4 + (k % dcols) * (dcw + 3), y = dgy + 2 + (k / dcols) * drow - (int) deckScroll;
            if (y + drow < dgy || y > dgy + dgh) continue;
            var e = poolEntry(L, f);
            boolean hov = Ui.in(mouseX, mouseY, x, y, dcw, dch) && mouseY >= dgy && mouseY < dgy + dgh;
            if (hov) { hoverF = f; hoverInfo = e; }
            deckCard(ctx, f, e, x, y, dcw, dch, 1f);
            if (hov) Ui.outline(ctx, x - 1, y - 1, dcw + 2, dch + 2, 0xFFFF6B6B);
            int c = counts.get(f);
            String cs = "×" + c;
            int bw = textRenderer.getWidth(cs) / 2 + 4;
            ctx.fill(x + dcw - bw, y + dch - 6, x + dcw, y + dch, 0xE0101420);
            var mm = ctx.getMatrices();
            mm.push();
            mm.translate(x + dcw - bw + 2, y + dch - 5, 0);
            mm.scale(0.5f, 0.5f, 1);
            ctx.drawText(textRenderer, cs, 0, 0, 0xFFFFD34D, false);
            mm.pop();
            if (y >= dgy) areas.add(new Area(x, y, dcw, dch, () -> {
                deckEdit.remove(f);
                deckDirty = true;
                staleCheck();
                if (client != null) client.getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.master(dev.tcgshop.TcgSounds.CARD_FLIP, 0.9f, 0.4f));
            }));
        }
        ctx.disableScissor();
        if (keys.isEmpty()) paragraph(ctx, Text.translatable("tcgshop.ptcg.deck_empty_hint"), dx + 4, dgy + 4, dw - 8, Ui.MUTED);
        hoverDeckList = Ui.in(mouseX, mouseY, dx, top, dw, h);
        // ---- vista previa grande de la carta bajo el ratón
        if (hoverF != null) {
            int pwid = Math.min(96, sh / 2), phgt = Math.round(pwid / Draw.CARD_RATIO);
            int prx = mouseX < px + pw / 2 ? Math.min(sx + sw - pwid - 6, mouseX + 14) : Math.max(sx + 6, mouseX - 14 - pwid);
            int pry = MathHelper.clamp(mouseY - phgt / 2, sy + 4, sy + sh - phgt - 4);
            var m = ctx.getMatrices();
            m.push();
            m.translate(0, 0, 400);
            ctx.fill(prx + 3, pry + 4, prx + pwid + 3, pry + phgt + 4, 0x66000000);
            deckCard(ctx, hoverF, hoverInfo, prx, pry, pwid, phgt, 1f);
            m.pop();
        }
    }

    private void staleCheck() {
        var L = lg();
        if (L != null) L.remove("check");
    }

    /** Motivo legible de un mazo inválido ("ACE_SPEC:Nombre" -> texto traducido con el nombre). */
    private static String deckErr(String code) {
        String[] parts = code.split(":", 2);
        return Text.translatable("tcgshop.ptcg.deck_err." + parts[0], parts.length > 1 ? parts[1] : "").getString();
    }

    private static final int[] AI_COLORS = {0xFF5BD68A, 0xFF4FB4FF, 0xFFFF9F43, 0xFFE05AFF};

    private void leaguePlay(DrawContext ctx, com.google.gson.JsonObject L, int px, int py, int pw, int ph, int mouseX, int mouseY, double t) {
        boolean queued = L.has("queued") && L.get("queued").getAsBoolean();
        boolean inMatch = L.has("inMatch") && L.get("inMatch").getAsBoolean();
        if ((queued || inMatch) && t - lastLeagueSync > 3) { lastLeagueSync = t; leagueSend("sync", null); }
        String valid = gs(L, "valid");
        boolean hasPool = L.has("pool") && L.getAsJsonArray("pool").size() > 0;
        boolean empty = valid.equals("EMPTY");
        boolean ready = valid.isEmpty() || (empty && hasPool);
        // --- tarjeta del mazo
        int lw = Math.min(150, pw / 3);
        Ui.roundGradient(ctx, px, py, lw, ph, SURF2, SURF);
        Ui.outline(ctx, px, py, lw, ph, LINE);
        int icx = px + lw / 2, icy = py + 28;
        int icol = valid.isEmpty() ? 0xFF4ADE80 : empty && hasPool ? 0xFFFFC233 : 0xFFFF5C7A;
        float pulse = 0.5f + 0.5f * (float) Math.sin(t * 3);
        StylePainter.circle(ctx, icx, icy, 20, Draw.withAlpha(icol, 0.15f + 0.1f * pulse));
        StylePainter.circle(ctx, icx, icy, 15, icol);
        StylePainter.circle(ctx, icx, icy, 12, Ui.darken(icol, 70));
        Draw.scaledCentered(ctx, Text.literal(valid.isEmpty() ? "✔" : empty && hasPool ? "A" : "✖").formatted(Formatting.BOLD), icx, icy - 6, 1.4f, icol);
        String title = valid.isEmpty() ? "tcgshop.league.play.deck_ok" : empty ? (hasPool ? "tcgshop.league.play.deck_auto" : "tcgshop.league.play.deck_none") : "tcgshop.league.play.deck_bad";
        Ui.text(ctx, Text.translatable(title).formatted(Formatting.BOLD), px, py + 52, lw, 10, icol, true);
        String detail = valid.isEmpty() ? Text.translatable("tcgshop.league.play.deck_ok_hint", L.getAsJsonArray("deck").size()).getString()
                : empty ? Text.translatable(hasPool ? "tcgshop.league.play.deck_auto_hint" : "tcgshop.league.no_pool").getString() : deckErr(valid);
        paragraph(ctx, Text.literal(detail), px + 6, py + 66, lw - 12, 0xFFC8CEE0);
        Ui.button(ctx, px + 8, py + ph - 38, lw - 16, 14, Text.translatable("tcgshop.league.play.edit_deck"), mouseX, mouseY, true, 0xFF2F6FB0);
        areas.add(new Area(px + 8, py + ph - 38, lw - 16, 14, () -> { leagueTab = "deck"; listScroll = 0; }));
        Ui.button(ctx, px + 8, py + ph - 20, lw - 16, 14, Text.translatable("tcgshop.league.auto"), mouseX, mouseY, hasPool, 0xFF5A4AA0);
        if (hasPool) areas.add(new Area(px + 8, py + ph - 20, lw - 16, 14, () -> { deckDirty = false; leagueSend("auto", deckEdit.size() < 60 ? new ArrayList<>(deckEdit) : null); }));
        int x = px + lw + 8, w = pw - lw - 8, y = py;
        // --- partida en curso
        if (inMatch) {
            Ui.roundGradient(ctx, x, y, w, 30, 0xF0203A2A, 0xF0142418);
            Ui.outline(ctx, x, y, w, 30, 0xFF4ADE80);
            ctx.drawText(textRenderer, Text.translatable("tcgshop.league.play.in_match").formatted(Formatting.BOLD), x + 8, y + 11, 0xFF9CF0B8, false);
            Ui.button(ctx, x + w - 98, y + 8, 90, 14, Text.translatable("tcgshop.league.play.resume"), mouseX, mouseY, true, 0xFF1F8A55);
            areas.add(new Area(x + w - 98, y + 8, 90, 14, () -> playMode("casual")));
            y += 36;
        }
        // --- nivel de la IA
        ctx.drawText(textRenderer, Text.translatable("tcgshop.league.play.vs_ai").formatted(Formatting.BOLD), x, y + 2, 0xFFFFFFFF, false);
        y += 13;
        com.google.gson.JsonArray levels = L.has("levels") ? L.getAsJsonArray("levels") : new com.google.gson.JsonArray();
        int cw = (w - 3 * 5) / 4;
        for (int k = 0; k < 4; k++) {
            int cx2 = x + k * (cw + 5);
            boolean sel = aiLevel == k, hover = Ui.in(mouseX, mouseY, cx2, y, cw, 34);
            int col = AI_COLORS[k];
            Ui.roundGradient(ctx, cx2, y, cw, 34, sel ? Draw.withAlpha(col, 0.35f) : hover ? 0xF0262B3E : SURF2, sel ? Draw.withAlpha(col, 0.15f) : SURF);
            Ui.outline(ctx, cx2, y, cw, 34, sel ? col : LINE);
            Ui.text(ctx, Text.translatable("tcgshop.ptcg.ui.level." + k).formatted(Formatting.BOLD), cx2, y + 3, cw, 10, sel ? 0xFFFFFFFF : col, true);
            var lv = k < levels.size() ? levels.get(k).getAsJsonObject() : null;
            Ui.text(ctx, Text.translatable("tcgshop.league.play.ai_rating", gi(lv, "rating")), cx2, y + 14, cw, 9, 0xFFAEB6CC, false);
            Ui.text(ctx, Text.literal("$" + gi(lv, "reward")), cx2, y + 23, cw, 9, 0xFFFFD34D, false);
            int kk = k;
            areas.add(new Area(cx2, y, cw, 34, () -> aiLevel = kk));
        }
        y += 40;
        int half = (w - 6) / 2;
        boolean canPlay = ready && !inMatch && !queued;
        modeCard(ctx, x, y, half, 46, "casual", 0xFF2F8FD0, canPlay, mouseX, mouseY, () -> playMode("casual"));
        int myRating = gi(L, "rating");
        var lvSel = aiLevel < levels.size() ? levels.get(aiLevel).getAsJsonObject() : null;
        double exp = 1 / (1 + Math.pow(10, (gi(lvSel, "rating") - myRating) / 400.0));
        int gain = Math.max(3, (int) Math.round(24 * (1 - exp))), loss = Math.max(3, (int) Math.round(24 * exp));
        modeCard(ctx, x + half + 6, y, half, 46, "ranked_ai", 0xFFD0A020, canPlay, mouseX, mouseY, () -> playMode("ranked_ai"));
        Ui.text(ctx, Text.literal("+" + gain + " / -" + loss + " pts · -$" + dev.tcgshop.ptcg.PtcgDuels.LEVEL_PENALTY[Math.max(0, Math.min(3, aiLevel))]), x + half + 6, y + 33, half, 9, 0xFFFFE08A, false);
        y += 52;
        // --- cola contra jugadores
        ctx.drawText(textRenderer, Text.translatable("tcgshop.league.play.vs_players").formatted(Formatting.BOLD), x, y + 2, 0xFFFFFFFF, false);
        y += 13;
        int qh = Math.max(30, Math.min(44, py + ph - y));
        Ui.roundGradient(ctx, x, y, w, qh, SURF2, SURF);
        Ui.outline(ctx, x, y, w, qh, queued ? 0xFF4FD1FF : LINE);
        if (queued) {
            for (int k = 0; k < 8; k++) {
                double a = t * 4 + k * Math.PI / 4;
                int dx = (int) Math.round(Math.cos(a) * 7), dy = (int) Math.round(Math.sin(a) * 7);
                ctx.fill(x + 16 + dx, y + qh / 2 + dy, x + 18 + dx, y + qh / 2 + 2 + dy, Draw.withAlpha(0xFF4FD1FF, (k + 1) / 8f));
            }
            ctx.drawText(textRenderer, Text.translatable("tcgshop.league.play.searching"), x + 32, y + qh / 2 - 9, 0xFFBFEFFF, false);
            ctx.drawText(textRenderer, Text.translatable("tcgshop.league.play.queue_size", gi(L, "queueSize")), x + 32, y + qh / 2 + 2, 0xFF8C96B4, false);
            Ui.button(ctx, x + w - 78, y + qh / 2 - 7, 70, 14, Text.translatable("tcgshop.league.play.cancel"), mouseX, mouseY, true, 0xFF8A2A3A);
            areas.add(new Area(x + w - 78, y + qh / 2 - 7, 70, 14, () -> playMode("cancel_queue")));
        } else {
            paragraph(ctx, Text.translatable("tcgshop.league.play.queue_hint"), x + 6, y + 4, w - 96, 0xFF8C96B4);
            Ui.button(ctx, x + w - 86, y + qh / 2 - 7, 78, 14, Text.translatable("tcgshop.league.play.find"), mouseX, mouseY, canPlay, 0xFF1F8A55);
            if (canPlay) areas.add(new Area(x + w - 86, y + qh / 2 - 7, 78, 14, () -> playMode("queue")));
        }
    }

    private void modeCard(DrawContext ctx, int x, int y, int w, int h, String mode, int accent, boolean enabled, int mouseX, int mouseY, Runnable r) {
        boolean hover = enabled && Ui.in(mouseX, mouseY, x, y, w, h);
        Ui.roundGradient(ctx, x, y, w, h, hover ? Ui.brighten(Ui.darken(accent, 70), 20) : Ui.darken(accent, 80), Ui.darken(accent, 100));
        Ui.outline(ctx, x, y, w, h, hover ? 0xFFFFFFFF : enabled ? accent : LINE);
        ctx.fill(x + 1, y + 1, x + w - 1, y + 3, enabled ? accent : 0xFF3A3F50);
        Ui.text(ctx, Text.translatable("tcgshop.league.play.mode." + mode).formatted(Formatting.BOLD), x, y + 7, w, 10, enabled ? 0xFFFFFFFF : 0xFF77778A, true);
        Ui.text(ctx, Text.translatable("tcgshop.league.play.mode." + mode + ".sub"), x, y + 19, w, 9, enabled ? 0xFFC8CEE0 : 0xFF66667A, false);
        if (!mode.equals("ranked_ai")) Ui.text(ctx, Text.translatable("tcgshop.league.play.start"), x, y + 32, w, 9, enabled ? accent : 0xFF55556A, false);
        if (enabled) areas.add(new Area(x, y, w, h, r));
    }

    private void playMode(String mode) {
        com.google.gson.JsonObject o = new com.google.gson.JsonObject();
        o.addProperty("a", "play");
        o.addProperty("mode", mode);
        o.addProperty("level", aiLevel);
        dev.tcgshop.client.ClientPtcg.leagueAction(o);
        lastLeagueSync = 0;
    }

    private boolean hoverDeckList;

    // ------------------------------------------------------------------ entrada

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (morphing()) return true;
        if ((app == App.MARKET || app == App.PRICES) && search.mouseClicked(mouseX, mouseY, button)) {
            setFocused(search);
            search.setFocused(true);
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
        if (app == App.MARKET && market.click(mouseX, mouseY)) return true;
        if (app == App.SELL && sellTab.equals("inv") && quickSell.click(mouseX, mouseY)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        return search.isFocused() && search.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (search.isFocused() && keyCode != GLFW.GLFW_KEY_ESCAPE) return search.keyPressed(keyCode, scanCode, modifiers) || true;
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && search.isFocused()) {
            search.setFocused(false);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && app != App.HOME) {
            open(App.HOME);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** Venta rápida: mantener pulsado y arrastrar por encima de varias cartas para elegirlas (o quitarlas) de golpe. */
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (button == 0 && app == App.SELL && sellTab.equals("inv") && quickSell.drag(mouseX, mouseY)) return true;
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        quickSell.release();
        return super.mouseReleased(mouseX, mouseY, button);
    }
}

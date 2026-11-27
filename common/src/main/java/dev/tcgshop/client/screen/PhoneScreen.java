package dev.tcgshop.client.screen;

import dev.masterball.api.platform.Platform;
import dev.masterball.api.platform.ClientPlatform;
import dev.masterball.api.card.Rarity;
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
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

import static dev.tcgshop.client.screen.Draw.withAlpha;

/**
 * TCG phone. The home screen is portrait (clock, register, reputation and collection widgets, apps with
 * 3D icons and a dock); opening an app rotates the phone to landscape with an animation to make room:
 * top bar, side rail of sections and main panel.
 *  · Market       EVERYTHING from the Wholesaler + packs + plushies + Poké Roulette, by category, with search and sort.
 *  · Prices       search, set and rarity filter, card detail with the card and a 30-day chart.
 *  · My shop      summary, staff (cashier / guard), security, reputation and customer reviews.
 *  · Collection (General Binder) · Titles · Advancements.
 */
public class PhoneScreen extends Screen {
    private enum App { HOME, MARKET, PRICES, SHOP, TITLES, GRADE, LEAGUE, SELL, FRIENDS, DISPLAYS, SETTINGS, FURNITURE, UPGRADES, MESSAGES, STATS, LOANS, RANKING }

    private record Area(int x, int y, int w, int h, Runnable action) {}

    private record AppDef(String key, int color, Supplier<ItemStack> icon) {}

    private static final int SURF = 0xF0171A26, SURF2 = 0xF01F2333, LINE = 0xFF2C3149;

    private CatalogPanel market;
    /** Upgrades app (0.9): phone tiers, buying upgrades and the daily call. */
    private final dev.tcgshop.client.phone.UpgradesPanel upgrades = new dev.tcgshop.client.phone.UpgradesPanel();
    /** 1.0 apps: Messages (inbox, compose, loan offers), Stats (money, tips, pros and cons) and Loans (Wholesaler credit). */
    private final dev.tcgshop.client.phone.MessagesApp messagesApp = new dev.tcgshop.client.phone.MessagesApp();
    private final dev.tcgshop.client.phone.StatsApp statsApp = new dev.tcgshop.client.phone.StatsApp();
    private final dev.tcgshop.client.phone.LoansApp loansApp = new dev.tcgshop.client.phone.LoansApp();
    /** 1.1: Ranking app (beat Don Cobbledome). */
    private final dev.tcgshop.client.phone.RankingApp rankingApp = new dev.tcgshop.client.phone.RankingApp();
    /** Settings section: "general" or "wallpaper" (1.0). */
    private String settingsTab = "general";
    /** The Market's catalog as the server sent it (for the 1.0 «On sale» strip). */
    private List<ItemStack> marketGoods;
    private List<Long> marketPrices;
    /** Locked app the player tried to open (shows the «Requires phone X» prompt), or null. */
    private String lockedPrompt;
    private final List<Area> areas = new ArrayList<>();
    private final List<AppDef> apps = new ArrayList<>();
    private App app = App.HOME;
    private double morphStart = -10, appOpened = -10;
    private float morphFrom, morphTo;
    private EditBox search;
    private String priceCard, priceSet = "", shopTab = "summary", gradeTab = "send";
    /** CobbleGrade · «Send cards»: filters, drag selection, services and expected value (see GradeSendPanel). */
    private final dev.tcgshop.client.screen.grade.GradeSendPanel gradePanel = new dev.tcgshop.client.screen.grade.GradeSendPanel(() -> {
        gradeTab = "orders";
        listScroll = 0;
    });
    private String leagueTab = "play";
    /** Quick Sell submenu: cards from the inventory or from the Binder. */
    private String sellTab = "inv";
    private final CosmeticsPanel cosPanel = new CosmeticsPanel();
    /** Furniture app (CobbleFurnies / plushies) with phone prices (home delivery). */
    private final FurnitureApp furniture = new FurnitureApp(-1, this);
    private int aiLevel = 1;
    private double lastLeagueSync;
    private final List<String> deckEdit = new ArrayList<>();
    private boolean deckDirty;
    /** Deck editor and TCG League help (client/screen/league). */
    private final dev.tcgshop.client.screen.league.DeckBuilderPanel deckPanel = new dev.tcgshop.client.screen.league.DeckBuilderPanel(new dev.tcgshop.client.screen.league.DeckBuilderPanel.Host() {
        @Override public List<String> deck() { return deckEdit; }
        @Override public boolean dirty() { return deckDirty; }
        @Override public void setDirty(boolean dirty) { deckDirty = dirty; }
        @Override public void send(String action, List<String> deck) { leagueSend(action, deck); }
    });
    private final dev.tcgshop.client.screen.league.LeagueHelpPanel leagueHelp = new dev.tcgshop.client.screen.league.LeagueHelpPanel();

    private Rarity.Family priceRarity;
    // price filter and sort (Prices app)
    private int priceBand, priceSort;
    /** 1.1: the Prices list shows (and filters / sorts by) each card's Grade 10 value instead of its raw price. */
    private boolean priceG10;
    private static final long[] BAND_MIN = {0, 0, 1_000, 10_000, 100_000}, BAND_MAX = {Long.MAX_VALUE, 1_000, 10_000, 100_000, Long.MAX_VALUE};
    private static final String[] BAND_LABEL = {"all", "<1k", "1k-10k", "10k-100k", ">100k"};
    private final java.util.Map<String, Long> priceCache = new java.util.HashMap<>();
    private long priceCacheDay = Long.MIN_VALUE;
    // quick sell
    private final CardPickGrid quickSell = new CardPickGrid();
    private double sellArmedAt = -10;
    private int zoneIndex;
    /** 1.1: dormant shop whose "Abandon" button was clicked once (the second click confirms). */
    private java.util.UUID abandonArmed;
    private double listScroll;

    // frame and screen for this render frame
    private int fx, fy, fw, fh, sx, sy, sw, sh;

    public PhoneScreen(List<ItemStack> goods, List<Long> prices) {
        super(Component.translatable("item.tcgshop.tcg_phone"));
        this.market = new CatalogPanel(-1, goods, prices);
        this.marketGoods = goods;
        this.marketPrices = prices;
        apps.add(new AppDef("market", 0xFFD39B1A, () -> {
            List<Catalog.CardSet> s = Catalog.sets();
            return BoosterBox.of(s.isEmpty() ? "mew151" : s.get(0).id());
        }));
        apps.add(new AppDef("prices", 0xFF2E9C5A, () -> ItemStack.EMPTY));
        apps.add(new AppDef("shop", 0xFF2F6FB0, () -> new ItemStack(TcgBlocks.CASH_REGISTER_ITEM)));
        apps.add(new AppDef("collection", 0xFFB3262E, () -> new ItemStack(TcgItems.GENERAL_BINDER)));
        // 1.0 apps (after the first four: the dock uses indices 0-3)
        apps.add(new AppDef("messages", 0xFF2E8BE8, () -> ItemStack.EMPTY));
        apps.add(new AppDef("stats", 0xFF7C4DFF, () -> ItemStack.EMPTY));
        apps.add(new AppDef("loans", 0xFFB8860B, () -> ItemStack.EMPTY));
        apps.add(new AppDef("ranking", 0xFFD4A017, () -> ItemStack.EMPTY)); // 1.1
        apps.add(new AppDef("titles", 0xFF8E44AD, () -> new ItemStack(Items.NAME_TAG)));
        apps.add(new AppDef("grade", 0xFF1E8FA8, () -> ItemStack.EMPTY));
        apps.add(new AppDef("league", 0xFFC62828, () -> new ItemStack(TcgBlocks.PLAY_TABLE_ITEM)));
        apps.add(new AppDef("advancements", 0xFFE07A1F, () -> new ItemStack(Items.NETHER_STAR)));
        apps.add(new AppDef("sell", 0xFFD9472B, () -> new ItemStack(Items.GOLD_INGOT)));
        apps.add(new AppDef("displays", 0xFF5C6BC0, () -> new ItemStack(TcgBlocks.DISPLAY_CASE_ITEM)));
        apps.add(new AppDef("friends", 0xFF26A69A, () -> new ItemStack(Items.PLAYER_HEAD)));
        if (FurnitureApp.available()) apps.add(new AppDef("furniture", 0xFF8D6E63, FurnitureApp::icon));
        if (!dev.tcgshop.client.phone.PhoneClient.furnCallDisabled()) // 1.1: daily Furniture Seller call (hidden when the server turns it off)
            apps.add(new AppDef("furncall", 0xFF6D9F3A, () -> new ItemStack(dev.tcgshop.shop.CleanBlocks.POTTED_PALM)));
        apps.add(new AppDef("upgrades", 0xFFB3262E, () -> new ItemStack(TcgItems.TCG_PHONE)));
        apps.add(new AppDef("settings", 0xFF607D8B, () -> new ItemStack(Items.COMPARATOR)));
        quickSell.price = st -> dev.tcgshop.shop.CardSales.offer(st, dev.tcgshop.shop.CardSales.PHONE_RATE + dev.tcgshop.client.phone.PhoneClient.sellBonus());
        // 1.1: old shop cores sell here too (at coreResaleRate of their price); one still linked to a dormant shop (or to a shop this client cannot see) is shown locked; crafted tiers (Paper) are not bought
        quickSell.accept = st -> dev.masterball.api.card.CardInfo.isCard(st) || dev.tcgshop.shop.CoreResale.unitPrice(st) > 0;
        quickSell.extra = dev.tcgshop.shop.CoreResale::unitPrice;
        quickSell.locked = CardPickGrid::lockedCore;
    }

    /**
     * The server sends the phone catalog: if the phone is already open (e.g. after buying an upgrade) the
     * Market is refreshed without closing the current app; otherwise it is opened.
     */
    public static void openOrRefresh(List<ItemStack> goods, List<Long> prices) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        boolean refreshOnly = refreshOnlyNext;
        refreshOnlyNext = false;
        if (mc.screen instanceof PhoneScreen ps) ps.refreshMarket(goods, prices);
        else if (!refreshOnly) mc.setScreen(new PhoneScreen(goods, prices));
    }

    /**
     * The next phone catalog only refreshes an already-open phone (set by the "refresh" Sync the server sends
     * after buying an upgrade): if the player closed it or opened another screen, it is not reopened on top.
     */
    public static boolean refreshOnlyNext;

    private void refreshMarket(List<ItemStack> goods, List<Long> prices) {
        CatalogPanel fresh = new CatalogPanel(-1, goods, prices);
        fresh.copyStateFrom(market);
        fresh.lockFilter = market.lockFilter; // 1.0: keep the locked/unlocked filter after an upgrade
        market = fresh;
        marketGoods = goods;
        marketPrices = prices;
    }

    private long cachedPrice(Catalog.Card c) {
        long day = PriceService.marketDay();
        if (day != priceCacheDay) { priceCache.clear(); priceCacheDay = day; }
        if (priceG10) return priceCache.computeIfAbsent("10|" + c.key(), k -> PriceService.grade10Cd(c.id(), c.rarity()));
        return priceCache.computeIfAbsent(c.key(), k -> PriceService.priceCd(c.stack()));
    }

    @Override
    protected void init() {
        Catalog.rebuild();
        String prev = search == null ? "" : search.getValue();
        search = new EditBox(font, 0, 0, 120, 14, Component.translatable("tcgshop.binder.search"));
        search.setHint(Component.translatable("tcgshop.binder.search").withStyle(ChatFormatting.DARK_GRAY));
        search.setValue(prev);
        search.setBordered(false);
        search.setResponder(s -> { market.filter = s; listScroll = 0; });
        addWidget(search);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private static double now() { return System.nanoTime() / 1e9; }

    /** 1.1: phone UI sounds (client only). */
    private static void uiSound(net.minecraft.sounds.SoundEvent e, float pitch) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.getSoundManager() != null) mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(e, pitch));
    }

    private boolean openSounded;

    @Override
    public void added() {
        super.added();
        if (!openSounded) { openSounded = true; uiSound(dev.tcgshop.TcgSounds.PHONE_OPEN, 1f); }
    }

    @Override
    public void removed() {
        super.removed();
        uiSound(dev.tcgshop.TcgSounds.PHONE_CLOSE, 1f);
    }

    private void open(App a) {
        if (a == app) return;
        uiSound(a == App.HOME ? dev.tcgshop.TcgSounds.PHONE_TAP : dev.tcgshop.TcgSounds.PHONE_APP, a == App.HOME ? 0.9f : 1f);
        float cur = morph();
        morphFrom = cur;
        morphTo = a == App.HOME ? 0 : 1;
        if (morphFrom != morphTo) morphStart = now();
        app = a;
        appOpened = now();
        listScroll = 0;
        search.setFocused(false);
    }

    /** 0 = portrait, 1 = landscape (with easing). */
    private float morph() {
        double p = Mth.clamp((now() - morphStart) / 0.38, 0, 1);
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

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        areas.clear();
        market.beginFrame();
        double t = now();
        layout();
        ctx.fillGradient(0, 0, width, height, 0x99080A12, 0xCC020306);
        float p = morph();
        // halo in the app's color behind the phone
        int aura = app == App.HOME ? 0xFF6B4FD8 : def(app.name().toLowerCase(Locale.ROOT)).color;
        for (int k = 5; k >= 1; k--) Ui.rounded(ctx, fx - k * 6, fy - k * 6, fw + k * 12, fh + k * 12, 22 + k * 6, withAlpha(aura, 0.03f));
        // metal casing with a polished edge
        Ui.rounded(ctx, fx + 4, fy + 10, fw + 2, fh + 2, 22, 0x70000000);
        Ui.roundedGradient(ctx, fx - 4, fy - 4, fw + 8, fh + 8, 24, 0xFF9AA0B2, 0xFF2A2D36);
        Ui.roundedGradient(ctx, fx - 3, fy - 3, fw + 6, fh + 6, 23, 0xFF5A6070, 0xFF1C1E26);
        Ui.roundedGradient(ctx, fx - 1, fy - 1, fw + 2, fh + 2, 21, 0xFF15161C, 0xFF07080A);
        ctx.fill(fx + 20, fy - 4, fx + fw - 20, fy - 3, 0x88FFFFFF);
        // side buttons
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
        // camera island
        if (p < 0.5f) {
            Ui.rounded(ctx, sx + sw / 2 - 24, sy + 3, 48, 10, 5, 0xFF000000);
            StylePainter.circle(ctx, sx + sw / 2 + 16, sy + 8, 2, 0xFF1A2A44);
        } else {
            Ui.rounded(ctx, sx + 3, sy + sh / 2 - 24, 10, 48, 5, 0xFF000000);
            StylePainter.circle(ctx, sx + 8, sy + sh / 2 + 16, 2, 0xFF1A2A44);
        }
        // home indicator
        boolean hh = Ui.in(mouseX, mouseY, sx + sw / 2 - 40, sy + sh - 10, 80, 10);
        Ui.rounded(ctx, sx + sw / 2 - 30, sy + sh - 6, 60, 4, 2, hh ? 0xFFFFFFFF : 0xCCDDDDDD);
        areas.add(new Area(sx + sw / 2 - 40, sy + sh - 10, 80, 10, () -> open(App.HOME)));

        if (morphing()) { Ui.cornerMask(ctx, sx, sy, sw, sh, 16, 0xFF0A0B0E); return; } // while rotating only the background is shown
        float fade = (float) Mth.clamp((t - Math.max(appOpened, morphStart + 0.38)) / 0.18, 0, 1);
        // 1.0: with the mouse over a notice, the app underneath does not react (no hover, no tooltips)
        int realMouseX = mouseX, realMouseY = mouseY;
        if (dev.tcgshop.client.phone.PhoneNotices.contains(mouseX, mouseY)) { mouseX = -9999; mouseY = -9999; }
        ctx.enableScissor(sx, sy + 13, sx + sw, sy + sh - 8);
        switch (app) {
            case HOME -> home(ctx, mouseX, mouseY, t);
            case MARKET -> marketApp(ctx, mouseX, mouseY, delta, t);
            case PRICES -> pricesApp(ctx, mouseX, mouseY, delta, t);
            case SHOP -> shopApp(ctx, mouseX, mouseY, t);
            case TITLES -> titlesApp(ctx, mouseX, mouseY);
            case GRADE -> gradeApp(ctx, mouseX, mouseY, t);
            case LEAGUE -> {
                boolean helpOpen = leagueHelp.isOpen(); // with the help open, what's underneath does not react to the mouse
                leagueApp(ctx, helpOpen ? -9999 : mouseX, helpOpen ? -9999 : mouseY, t);
                leagueHelp.render(ctx, lg(), sx, sy, sw, sh, mouseX, mouseY, t);
            }
            case SELL -> sellApp(ctx, mouseX, mouseY, t);
            case FRIENDS -> friendsApp(ctx, mouseX, mouseY);
            case DISPLAYS -> displaysApp(ctx, mouseX, mouseY);
            case SETTINGS -> settingsApp(ctx, mouseX, mouseY);
            case FURNITURE -> furnitureApp(ctx, mouseX, mouseY, delta);
            case UPGRADES -> upgradesApp(ctx, mouseX, mouseY, t);
            case MESSAGES -> {
                topBar(ctx, def("messages"), mouseX, mouseY);
                messagesApp.render(ctx, sx + 8, sy + 41, sw - 16, sh - 52, mouseX, mouseY, delta, (x, y, w, h, r) -> areas.add(new Area(x, y, w, h, r)));
            }
            case STATS -> {
                topBar(ctx, def("stats"), mouseX, mouseY);
                statsApp.render(ctx, sx + 8, sy + 41, sw - 16, sh - 52, mouseX, mouseY);
            }
            case RANKING -> {
                topBar(ctx, def("ranking"), mouseX, mouseY);
                rankingApp.render(ctx, sx + 8, sy + 41, sw - 16, sh - 52, mouseX, mouseY);
            }
            case LOANS -> {
                topBar(ctx, def("loans"), mouseX, mouseY);
                loansApp.render(ctx, sx + 8, sy + 41, sw - 16, sh - 52, mouseX, mouseY, (x, y, w, h, r) -> areas.add(new Area(x, y, w, h, r)),
                        this::openMessage);
            }
        }
        // 1.0: notices (purchase done, not enough money...) on their own opaque layer above the app
        int nw = Math.min(sw - 16, 260);
        dev.tcgshop.client.phone.PhoneNotices.render(ctx, sx + (sw - nw) / 2, sy + 16, nw, realMouseX, realMouseY);
        ctx.disableScissor();
        if (fade < 1) ctx.fill(sx, sy + 13, sx + sw, sy + sh - 8, withAlpha(0xFF0B0D16, 1 - fade));
        Ui.cornerMask(ctx, sx, sy, sw, sh, 16, 0xFF0A0B0E);
    }

    private void wallpaper(GuiGraphics ctx, double t) {
        boolean home = app == App.HOME || morphing();
        if (home) {
            // 1.0: the player's wallpaper (Settings → Wallpaper; the default one is the original aurora)
            dev.tcgshop.client.phone.PhoneWallpaper.draw(ctx, sx, sy, sw, sh, t);
        } else {
            int accent = def(app.name().toLowerCase(Locale.ROOT)).color;
            ctx.fillGradient(sx, sy, sx + sw, sy + sh, 0xFF161A2A, 0xFF0A0C14);
            ctx.fillGradient(sx, sy + 35, sx + sw, sy + 120, withAlpha(accent, 0.12f), withAlpha(accent, 0f));
            for (int gx = sx + 12; gx < sx + sw; gx += 24) ctx.fill(gx, sy + 36, gx + 1, sy + sh - 8, 0x06FFFFFF);
        }
    }

    private void statusBar(GuiGraphics ctx, float p) {
        long time = minecraft != null && minecraft.level != null ? (minecraft.level.getDayTime() + 6000) % 24000 : 0;
        String clock = String.format(Locale.ROOT, "%02d:%02d", time / 1000, (time % 1000) * 60 / 1000);
        int y = sy + 3, left = p < 0.5f ? sx + 8 : sx + 16;
        ctx.drawString(font, Component.literal(clock).withStyle(ChatFormatting.BOLD), left, y, 0xFFFFFFFF, false);
        if (minecraft != null && minecraft.player != null && dev.tcgshop.economy.CobbleDollarsBridge.isAvailable()) {
            String wallet = "₽ " + compact(dev.tcgshop.economy.CobbleDollarsBridge.getBalance(minecraft.player));
            int wx = left + font.width(clock) + 8;
            Ui.round(ctx, wx - 3, y - 2, font.width(wallet) + 6, 11, 0x55000000);
            ctx.drawString(font, wallet, wx, y, Ui.GOLD, false);
        }
        int rx = sx + sw - 8;
        // battery
        ctx.fill(rx - 14, y, rx - 1, y + 7, 0xFFFFFFFF);
        ctx.fill(rx - 13, y + 1, rx - 2, y + 6, 0xFF000000);
        ctx.fill(rx - 12, y + 2, rx - 4, y + 5, 0xFF43C463);
        ctx.fill(rx - 1, y + 2, rx, y + 5, 0xFFFFFFFF);
        // signal
        for (int i = 0; i < 4; i++) ctx.fill(rx - 30 + i * 3, y + 6 - i * 2, rx - 28 + i * 3, y + 7, 0xFFFFFFFF);
        ctx.drawString(font, "5G", rx - 44, y, 0xFFDDDDDD, false);
    }

    // ------------------------------------------------------------------ components

    private void appIcon(GuiGraphics ctx, AppDef a, int x, int y, int s, boolean hover) {
        appIconBase(ctx, a, x, y, s, hover);
        // 1.0: unread messages / pending loan offer or loan due
        int badgeY = y - 2 - (hover ? 2 : 0);
        if (a.key.equals("messages")) {
            int n = dev.tcgshop.client.phone.PhoneAppsClient.unread();
            if (n > 0) Ui.badge(ctx, x + s - 3, badgeY, n);
        } else if (a.key.equals("loans") && (dev.tcgshop.client.phone.PhoneAppsClient.pendingOffer() || loanDue())) Ui.badge(ctx, x + s - 3, badgeY, 1);
        if (!dev.tcgshop.client.phone.PhoneClient.appUnlocked(a.key)) { // app from a higher phone tier: greyed out with a padlock
            int lift = hover ? 2 : 0, r = Math.max(3, s / 4);
            Ui.rounded(ctx, x, y - lift, s, s, r, 0xB0141620);
            int ls = Math.max(8, s / 2);
            dev.tcgshop.client.phone.LockIcon.draw(ctx, x + s / 2 - Math.round(ls * 0.4f), y + s / 2 - ls / 2 - lift, ls,
                    dev.tcgshop.phone.PhoneTier.of(dev.tcgshop.client.phone.PhoneClient.appTier(a.key)).color);
        }
    }

    private void appIconBase(GuiGraphics ctx, AppDef a, int x, int y, int s, boolean hover) {
        int lift = hover ? 2 : 0, r = Math.max(3, s / 4);
        if (hover) Ui.rounded(ctx, x - 3, y - 3 - lift, s + 6, s + 6, r + 3, withAlpha(a.color, 0.35f));
        Ui.rounded(ctx, x + 1, y + 3 - lift + 1, s, s, r, 0x40000000);
        Ui.rounded(ctx, x, y + 2 - lift, s, s, r, 0x55000000);
        Ui.roundedGradient(ctx, x, y - lift, s, s, r, Ui.brighten(a.color, 60), Ui.darken(a.color, 35));
        Ui.roundedGradient(ctx, x + 1, y + 1 - lift, s - 2, s / 2, r - 1, 0x55FFFFFF, 0x08FFFFFF);
        Ui.roundedOutline(ctx, x, y - lift, s, s, r, withAlpha(0xFFFFFFFF, hover ? 0.7f : 0.22f));
        ItemStack icon = a.icon.get();
        var m = ctx.pose();
        if (!icon.isEmpty()) {
            float k = s / 16f * 0.72f;
            m.pushPose();
            m.translate(x + s / 2f, y + s / 2f - lift, 0);
            m.scale(k, k, 1);
            ctx.renderItem(icon, -8, -8);
            m.popPose();
        } else if (a.key.equals("grade")) { // drawn graded-card slab
            int w = s * 9 / 20, h = s * 13 / 20, gx = x + (s - w) / 2, gy = y + (s - h) / 2 - lift + 1;
            ctx.fill(gx - 1, gy - 1, gx + w + 1, gy + h + 1, 0xCCFFFFFF);
            ctx.fill(gx, gy, gx + w, gy + h / 4, 0xFFF2F2F2);
            ctx.fill(gx, gy, gx + 2, gy + h / 4, 0xFFE5B21E);
            ctx.fill(gx + 2, gy + h / 4 + 2, gx + w - 2, gy + h - 2, 0xFF2F6FB0);
            ctx.fill(gx + 4, gy + h / 4 + 4, gx + w - 4, gy + h - 4, 0xFF6BC5FF);
            int ready = dev.tcgshop.client.ClientGrading.readyCount();
            if (ready > 0) Ui.badge(ctx, x + s - 3, y - 2 - lift, ready);
        } else if (a.key.equals("messages")) { // drawn envelope
            int w = s * 13 / 20, h = s * 9 / 20, ex = x + (s - w) / 2, ey = y + (s - h) / 2 - lift;
            ctx.fill(ex - 1, ey - 1, ex + w + 1, ey + h + 1, 0x66000000);
            ctx.fill(ex, ey, ex + w, ey + h, 0xFFF4F6FB);
            for (int i = 0; i <= w / 2; i++) { // flap
                int fy = ey + i * h / Math.max(1, w);
                ctx.fill(ex + i, fy, ex + i + 1, fy + 1, 0xFF8FA6C8);
                ctx.fill(ex + w - 1 - i, fy, ex + w - i, fy + 1, 0xFF8FA6C8);
            }
        } else if (a.key.equals("stats")) { // drawn pie + bars
            int pr = s / 4, pcx = x + s / 3 + 1, pcy = y + s / 2 - lift;
            StylePainter.circle(ctx, pcx, pcy, pr, 0xFFFFFFFF);
            for (int dy = -pr; dy <= 0; dy++) for (int dx = 0; dx <= pr; dx++) // a quarter in gold
                if (dx * dx + dy * dy <= pr * pr) ctx.fill(pcx + dx, pcy + dy, pcx + dx + 1, pcy + dy + 1, 0xFFFFD34D);
            int bw = Math.max(2, s / 10), bx = x + s * 3 / 5, by = y + s - s / 4 - lift;
            int[] hs = {5, 9, 13};
            for (int i = 0; i < hs.length; i++) {
                int bh = hs[i] * s / 24;
                ctx.fill(bx + i * (bw + 1), by - bh, bx + i * (bw + 1) + bw, by, 0xFFFFFFFF);
            }
        } else if (a.key.equals("ranking")) { // 1.1: drawn trophy
            int tw = s / 2, tx = x + (s - tw) / 2, ty = y + s / 5 - lift;
            ctx.fill(tx, ty, tx + tw, ty + tw / 2 + 2, 0xFFFFE680);
            ctx.fill(tx + 2, ty + tw / 2 + 2, tx + tw - 2, ty + tw / 2 + 4, 0xFFFFD34D);
            ctx.fill(tx - 2, ty + 1, tx, ty + tw / 3, 0xFFFFE680);
            ctx.fill(tx + tw, ty + 1, tx + tw + 2, ty + tw / 3, 0xFFFFE680);
            ctx.fill(tx + tw / 2 - 1, ty + tw / 2 + 4, tx + tw / 2 + 1, ty + tw / 2 + 7, 0xFFFFD34D);
            ctx.fill(tx + 1, ty + tw / 2 + 7, tx + tw - 1, ty + tw / 2 + 9, 0xFFB8860B);
            ctx.drawString(font, Component.literal("1").withStyle(ChatFormatting.BOLD), tx + tw / 2 - 2, ty + 2, 0xFF8A6A12, false);
        } else if (a.key.equals("loans")) { // drawn coin stack
            int cw = s / 2, cx0 = x + (s - cw) / 2, base = y + s - s / 4 - lift, ch = Math.max(2, s / 10);
            for (int i = 0; i < 4; i++) {
                int cy0 = base - i * (ch + 1);
                ctx.fill(cx0 + (i % 2), cy0 - ch, cx0 + cw + (i % 2), cy0, 0xFFFFD34D);
                ctx.fill(cx0 + (i % 2), cy0 - 1, cx0 + cw + (i % 2), cy0, 0xFFB8860B);
            }
            ctx.drawString(font, Component.literal("₽").withStyle(ChatFormatting.BOLD), x + s - 10, y + 3 - lift, 0xFFFFFFFF, true);
        } else { // drawn price chart
            int bx = x + s / 5, by = y + s - s / 4 - lift, bw = Math.max(2, s / 9);
            int[] hs = {4, 7, 5, 10, 13};
            for (int i = 0; i < hs.length; i++) {
                int bh = hs[i] * s / 22;
                ctx.fill(bx + i * (bw + 1), by - bh, bx + i * (bw + 1) + bw, by, 0xFFFFFFFF);
            }
            ctx.drawString(font, Component.literal("↗").withStyle(ChatFormatting.BOLD), x + s - 11, y + 3 - lift, 0xFFFFE680, true);
        }
    }

    private void widget(GuiGraphics ctx, int x, int y, int w, int h, Component label, Component value, int color) {
        Ui.glass(ctx, x, y, w, h, 8);
        ctx.drawString(font, label, x + 6, y + 5, 0xFFD8DCF0, false);
        var m = ctx.pose();
        m.pushPose();
        m.translate(x + 6, y + 17, 0);
        float s = Math.min(1.5f, (w - 12f) / Math.max(1, font.width(value)));
        m.scale(s, s, 1);
        ctx.drawString(font, value, 0, 0, color, true);
        m.popPose();
    }

    /** Progress ring (ring of dots). */
    private static void ring(GuiGraphics ctx, int cx, int cy, int r, int thick, float frac, int color) {
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

    private void topBar(GuiGraphics ctx, AppDef a, int mouseX, int mouseY) {
        int y = sy + 13;
        ctx.fillGradient(sx, y, sx + sw, y + 22, withAlpha(Ui.brighten(a.color, 10), 0.96f), withAlpha(Ui.darken(a.color, 50), 0.96f));
        ctx.fill(sx, y, sx + sw, y + 1, 0x40FFFFFF);
        ctx.fillGradient(sx, y + 22, sx + sw, y + 27, 0x66000000, 0x00000000);
        boolean hb = Ui.in(mouseX, mouseY, sx + 10, y + 3, 16, 16);
        Ui.rounded(ctx, sx + 10, y + 3, 16, 16, 8, hb ? 0x66FFFFFF : 0x2AFFFFFF);
        if (hb) Ui.roundedOutline(ctx, sx + 10, y + 3, 16, 16, 8, 0xAAFFFFFF);
        Ui.text(ctx, Component.literal("‹").withStyle(ChatFormatting.BOLD), sx + 10, y + 3, 16, 16, 0xFFFFFFFF, true);
        areas.add(new Area(sx + 10, y + 3, 16, 16, () -> open(App.HOME)));
        Ui.rounded(ctx, sx + 31, y + 6, 3, 10, 1, 0xCCFFFFFF);
        ctx.drawString(font, Component.translatable("tcgshop.phone." + a.key).withStyle(ChatFormatting.BOLD), sx + 39, y + 7, 0xFFFFFFFF, true);
    }

    private void searchBox(GuiGraphics ctx, int x, int y, int w, int mouseX, int mouseY, float delta) {
        Ui.round(ctx, x, y, w, 15, search.isFocused() ? 0xEE0E1018 : 0xAA0E1018);
        Ui.outline(ctx, x, y, w, 15, search.isFocused() ? 0xFFFFFFFF : 0x66FFFFFF);
        ctx.drawString(font, "⌕", x + 4, y + 4, 0xFFAAAAAA, false);
        search.setX(x + 14);
        search.setY(y + 4);
        search.setWidth(w - 18);
        search.render(ctx, mouseX, mouseY, delta);
    }

    /** Side rail of sections. Returns the width used. */
    private float railSelY = -1;

    private int rail(GuiGraphics ctx, String[] keys, String[] labels, int[] counts, String selected, int accent, int mouseX, int mouseY,
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
            ctx.drawString(font, lbl, x + 8, ry + 5, sel ? 0xFFFFFFFF : hover ? 0xFFE8EAF2 : 0xFFB8BCCB, sel);
            if (counts != null && counts[i] >= 0) {
                String c = String.valueOf(counts[i]);
                int cw = font.width(c) + 6;
                Ui.rounded(ctx, x + w - 4 - cw, ry + 3, cw, 10, 5, sel ? 0x66000000 : 0x33000000);
                ctx.drawString(font, c, x + w - 1 - cw, ry + 4, sel ? Ui.GOLD : 0xFF8A8FA8, false);
            }
            String k = keys[i];
            areas.add(new Area(x, ry, w, rowH - 2, () -> pick.accept(k)));
        }
        return w + 14;
    }

    private void tile(GuiGraphics ctx, int x, int y, int w, int h, String labelKey, String value, int color) {
        Ui.rounded(ctx, x + 1, y + 2, w, h, 6, 0x40000000);
        Ui.roundedGradient(ctx, x, y, w, h, 6, SURF2, SURF);
        Ui.roundedOutline(ctx, x, y, w, h, 6, LINE);
        Ui.rounded(ctx, x + 2, y + 4, 2, h - 8, 1, color);
        ctx.drawString(font, Ui.ellipsis(Component.translatable(labelKey).getString(), w - 12), x + 8, y + 5, 0xFF9A9FB8, false);
        var m = ctx.pose();
        m.pushPose();
        m.translate(x + 8, y + 17, 0);
        float s = Math.min(1.35f, (w - 14f) / Math.max(1, font.width(value)));
        m.scale(s, s, 1);
        ctx.drawString(font, Component.literal(value).withStyle(ChatFormatting.BOLD), 0, 0, color, true);
        m.popPose();
    }

    private int paragraph(GuiGraphics ctx, Component text, int x, int y, int w, int color) {
        for (var line : font.split(text, w)) {
            ctx.drawString(font, line, x, y, color, false);
            y += 10;
        }
        return y;
    }

    // ------------------------------------------------------------------ home

    private void home(GuiGraphics ctx, int mouseX, int mouseY, double t) {
        long tod = minecraft != null && minecraft.level != null ? minecraft.level.getDayTime() : 0;
        long time = (tod + 6000) % 24000;
        String clock = String.format(Locale.ROOT, "%02d:%02d", time / 1000, (time % 1000) * 60 / 1000);
        Draw.scaledCentered(ctx, Component.literal(clock).withStyle(ChatFormatting.BOLD), sx + sw / 2f, sy + 24, 3.2f, 0xFFFFFFFF);
        String part = time < 6000 ? "night" : time < 12000 ? "morning" : time < 18000 ? "afternoon" : "evening";
        Draw.scaledCentered(ctx, Component.translatable("tcgshop.phone.day", tod / 24000 + 1).append(" · ").append(Component.translatable("tcgshop.phone.time." + part)),
                sx + sw / 2f, sy + 54, 1f, 0xFFCCD6FF);
        // widgets
        int wy = sy + 70, ww = (sw - 22) / 2;
        long balance = 0;
        int rep = -1;
        for (ShopClient.ZoneInfo z : myZones()) { balance += z.balance(); rep = Math.max(rep, z.reputation()); }
        long profit = ShopClient.myRevenue - ShopClient.myCosts;
        widget(ctx, sx + 8, wy, ww, 40, Component.translatable("tcgshop.phone.w_register"), Component.literal(PriceService.format(balance)), Ui.GOLD);
        Ui.glass(ctx, sx + 14 + ww, wy, ww, 40, 8);
        int rc = rep >= 80 ? 0xFF43C463 : rep >= 50 ? 0xFFFFD34D : 0xFFE05050;
        ring(ctx, sx + 14 + ww + 20, wy + 20, 13, 3, Math.max(0, rep) / 100f, rc);
        Ui.text(ctx, Component.literal(rep < 0 ? "—" : String.valueOf(rep)).withStyle(ChatFormatting.BOLD), sx + 14 + ww + 8, wy + 13, 24, 14, 0xFFFFFFFF, true);
        ctx.drawString(font, Component.translatable("tcgshop.phone.w_rep"), sx + 14 + ww + 38, wy + 9, 0xFFD8DCF0, false);
        ctx.drawString(font, Component.literal((profit >= 0 ? "+" : "") + compact(profit)), sx + 14 + ww + 38, wy + 22, profit >= 0 ? 0xFF6BE07B : 0xFFFF6B6B, true);
        // collection progress
        int total = Math.max(1, Catalog.totalCards()), have = ClientCollection.counts.size();
        int cy = wy + 46;
        Ui.glass(ctx, sx + 8, cy, sw - 16, 24, 8);
        ctx.drawString(font, Component.translatable("tcgshop.phone.w_collection", have, total), sx + 14, cy + 4, 0xFFFFFFFF, false);
        String pct = String.format(Locale.ROOT, "%.1f%%", have * 100f / total);
        ctx.drawString(font, pct, sx + sw - 14 - font.width(pct), cy + 4, Ui.GOLD, false);
        Ui.progress(ctx, sx + 14, cy + 15, sw - 28, 5, have / (float) total, 0xFFB3262E);
        // apps
        int cols = apps.size() > 16 ? 5 : 4, rowsN = (apps.size() + cols - 1) / cols; // 1.0: 5 columns with the new apps
        int gy = cy + 34, availH = (sy + sh - Math.min(28, (sw - 60) / 4) - 26) - gy;
        int is = Math.max(14, Math.min(Math.min(40, (sw - 10 * (cols + 1)) / cols), availH / rowsN - 20)), gap = (sw - cols * is) / (cols + 1);
        for (int i = 0; i < apps.size(); i++) {
            AppDef a = apps.get(i);
            int ix = sx + gap + (i % cols) * (is + gap), iy = gy + (i / cols) * (is + 20);
            boolean hover = Ui.in(mouseX, mouseY, ix, iy, is, is);
            appIcon(ctx, a, ix, iy, is, hover);
            Ui.text(ctx, Component.translatable("tcgshop.phone." + a.key), ix - gap / 2, iy + is + 3, is + gap, 10,
                    dev.tcgshop.client.phone.PhoneClient.appUnlocked(a.key) ? 0xFFFFFFFF : 0xFF8A8FA8, true);
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
        if (lockedPrompt != null)
            dev.tcgshop.client.phone.UpgradesPanel.lockPrompt(ctx, sx, sy + 13, sw, sh - 21, lockedPrompt, mouseX, mouseY,
                    (x, y, w, h, r) -> areas.add(new Area(x, y, w, h, r)), () -> openApp("upgrades"), () -> lockedPrompt = null);
    }

    private List<ShopClient.ZoneInfo> myZones() {
        List<ShopClient.ZoneInfo> mine = new ArrayList<>();
        for (ShopClient.ZoneInfo z : ShopClient.zones) if (minecraft != null && minecraft.player != null && z.owner().equals(minecraft.player.getUUID())) mine.add(z);
        return mine;
    }

    private static String compact(long v) {
        long a = Math.abs(v);
        String s = a >= 1_000_000 ? String.format(Locale.ROOT, "%.1fM", a / 1e6) : a >= 1000 ? String.format(Locale.ROOT, "%.1fk", a / 1e3) : String.valueOf(a);
        return (v < 0 ? "-" : "") + s;
    }

    private void openApp(String key) {
        lockedPrompt = null;
        boolean owes = key.equals("loans") && dev.tcgshop.client.phone.PhoneAppsClient.hasLoan(); // a loan can always be repaid
        if (!owes && !dev.tcgshop.client.phone.PhoneClient.appUnlocked(key)) { // instead of the app, the prompt to unlock it
            lockedPrompt = key;
            return;
        }
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
                leagueHelp.onEnter();
            }
            case "grade" -> {
                dev.tcgshop.client.ClientGrading.request();
                open(App.GRADE);
            }
            case "collection" -> {
                onClose();
                ClientCollection.action(BinderActionPayload.OPEN_GENERAL, "", 0);
            }
            case "binder_market" -> {
                onClose();
                dev.tcgshop.client.ClientSocial.act("binder_open", "open");
            }
            case "settings" -> open(App.SETTINGS);
            case "messages" -> {
                messagesApp.onEnter();
                open(App.MESSAGES);
            }
            case "stats" -> {
                statsApp.onEnter();
                open(App.STATS);
            }
            case "loans" -> {
                loansApp.onEnter();
                open(App.LOANS);
            }
            case "ranking" -> {
                rankingApp.onEnter();
                open(App.RANKING);
            }
            case "upgrades" -> {
                listScroll = 0;
                open(App.UPGRADES);
            }
            case "furniture" -> {
                furniture.request();
                open(App.FURNITURE);
            }
            case "furncall" -> { // 1.1: no screen, one tap calls the Furniture Seller (the server checks tier and cooldown)
                ItemStack icon = new ItemStack(dev.tcgshop.shop.CleanBlocks.POTTED_PALM);
                if (dev.tcgshop.client.phone.PhoneClient.furnUsed)
                    dev.tcgshop.client.phone.PhoneNotices.push(dev.tcgshop.client.phone.PhoneNotices.Kind.INFO,
                            Component.translatable("tcgshop.phone.furncall.title"), Component.translatable("tcgshop.phoneup.furn_call.used"), icon);
                else dev.tcgshop.client.phone.PhoneClient.callFurniture(); // the notice comes with the server's answer (PhoneClient.apply)
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
                if (minecraft != null && minecraft.player != null) minecraft.setScreen(new AdvancementsScreen(minecraft.player.connection.getAdvancements()));
            }
            default -> {}
        }
    }

    /** 1.0: opens a message in the Messages app (from a notice or the Loans app). */
    private void openMessage(long id) {
        lockedPrompt = null;
        messagesApp.open(id);
        open(App.MESSAGES);
    }

    /** 1.0: is the active loan due today or late? (badge on the Loans app). */
    private static boolean loanDue() {
        if (!dev.tcgshop.client.phone.PhoneAppsClient.hasLoan()) return false;
        return dev.tcgshop.client.phone.PhoneAppsClient.loan().getLong("due") <= dev.tcgshop.client.phone.PhoneAppsClient.loans.getLong("day");
    }

    // ------------------------------------------------------------------ phone upgrades (0.9)

    private void upgradesApp(GuiGraphics ctx, int mouseX, int mouseY, double t) {
        topBar(ctx, def("upgrades"), mouseX, mouseY);
        listScroll = upgrades.render(ctx, sx + 8, sy + 41, sw - 16, sh - 52, mouseX, mouseY, t, listScroll,
                (x, y, w, h, r) -> areas.add(new Area(x, y, w, h, r)));
    }

    // ------------------------------------------------------------------ furniture

    private void furnitureApp(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        topBar(ctx, def("furniture"), mouseX, mouseY);
        furniture.render(ctx, sx + 8, sy + 41, sw - 16, sh - 52, mouseX, mouseY, delta);
    }
    // ------------------------------------------------------------------ friends

    private void friendsApp(GuiGraphics ctx, int mouseX, int mouseY) {
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
        ctx.drawString(font, Component.translatable("tcgshop.friends.list", friends.size()).withStyle(ChatFormatting.BOLD), px + 5, y, 0xFFFFFFFF, false);
        y += 12;
        String sel = dev.tcgshop.client.ClientSocial.selectedFriend;
        net.minecraft.nbt.CompoundTag selected = null;
        for (int i = 0; i < friends.size() && y < py + ph - 14; i++) {
            var f = friends.getCompound(i);
            String u = f.getUUID("u").toString();
            boolean on = u.equals(sel), hover = Ui.in(mouseX, mouseY, px + 3, y, lw - 6, 12);
            if (on) selected = f;
            Ui.round(ctx, px + 3, y, lw - 6, 12, on ? withAlpha(a.color, 0.5f) : hover ? 0x22FFFFFF : 0);
            StylePainter.circle(ctx, px + 9, y + 6, 2, f.getBoolean("on") ? 0xFF43C463 : 0xFF6A6F80);
            ctx.drawString(font, Ui.ellipsis(f.getString("n"), lw - 22), px + 15, y + 2, 0xFFFFFFFF, false);
            areas.add(new Area(px + 3, y, lw - 6, 12, () -> dev.tcgshop.client.ClientSocial.selectedFriend = u));
            y += 13;
        }
        if (friends.isEmpty()) { ctx.drawString(font, Component.translatable("tcgshop.friends.none"), px + 5, y, Ui.MUTED, false); y += 12; }
        if (!incoming.isEmpty()) {
            y += 4;
            ctx.drawString(font, Component.translatable("tcgshop.friends.requests").withStyle(ChatFormatting.BOLD), px + 5, y, 0xFF6BC5FF, false);
            y += 12;
            for (int i = 0; i < incoming.size() && y < py + ph - 14; i++) {
                var r = incoming.getCompound(i);
                java.util.UUID u = r.getUUID("u");
                ctx.drawString(font, Ui.ellipsis(r.getString("n"), lw - 40), px + 5, y + 2, 0xFFFFFFFF, false);
                Ui.button(ctx, px + lw - 33, y, 14, 12, Component.literal("✔"), mouseX, mouseY, true, 0xFF2E8A4A);
                Ui.button(ctx, px + lw - 17, y, 14, 12, Component.literal("✖"), mouseX, mouseY, true, 0xFF7A2020);
                areas.add(new Area(px + lw - 33, y, 14, 12, () -> friendAct("accept", u)));
                areas.add(new Area(px + lw - 17, y, 14, 12, () -> friendAct("decline", u)));
                y += 14;
            }
        }
        if (!online.isEmpty()) {
            y += 4;
            ctx.drawString(font, Component.translatable("tcgshop.friends.add").withStyle(ChatFormatting.BOLD), px + 5, y, 0xFFFFD34D, false);
            y += 12;
            for (int i = 0; i < online.size() && y < py + ph - 14; i++) {
                var r = online.getCompound(i);
                java.util.UUID u = r.getUUID("u");
                boolean sent = r.getBoolean("sent");
                ctx.drawString(font, Ui.ellipsis(r.getString("n"), lw - 26), px + 5, y + 2, 0xFFDDDDDD, false);
                Ui.button(ctx, px + lw - 17, y, 14, 12, Component.literal(sent ? "…" : "+"), mouseX, mouseY, !sent, 0xFF3A6FD8);
                if (!sent) areas.add(new Area(px + lw - 17, y, 14, 12, () -> friendAct("request", u)));
                y += 14;
            }
        }
        // friend details
        int dx = px + lw + 6, dw = pw - lw - 6;
        if (selected == null) {
            paragraph(ctx, Component.translatable("tcgshop.friends.tip"), dx + 4, py + 6, dw - 8, Ui.MUTED);
            return;
        }
        var s = selected.getCompound("s");
        ctx.drawString(font, Component.literal(selected.getString("n")).withStyle(ChatFormatting.BOLD), dx + 2, py + 2,
                selected.getBoolean("on") ? 0xFF8BE08B : 0xFFFFFFFF);
        java.util.UUID fu = selected.getUUID("u");
        Ui.button(ctx, dx + dw - 60, py, 58, 12, Component.translatable("tcgshop.friends.remove"), mouseX, mouseY, true, 0xFF5A2A2A);
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
        ctx.drawString(font, Component.translatable("tcgshop.friends.shops", shops.size()).withStyle(ChatFormatting.BOLD), dx + 2, sy2, 0xFFFFFFFF, false);
        sy2 += 12;
        for (int i = 0; i < shops.size() && sy2 < py + ph - 12; i++) {
            var z = shops.getCompound(i);
            Ui.roundGradient(ctx, dx, sy2, dw, 20, SURF2, SURF);
            String tier = Component.translatable("tcgshop.shop.tier." + dev.tcgshop.shop.ShopTier.byRank(z.getInt("tier")).key).getString();
            ctx.drawString(font, Component.translatable("tcgshop.friends.shop_line", tier, z.getInt("rep"), String.format(Locale.ROOT, "%.1f", z.getInt("stars") / 10.0), z.getInt("sales")),
                    dx + 4, sy2 + 2, 0xFFDDE3F0, false);
            ctx.drawString(font, Component.translatable("tcgshop.friends.shop_pos", z.getInt("x"), z.getInt("y"), z.getInt("z"), z.getString("dim")), dx + 4, sy2 + 11, 0xFF8A90A8, false);
            sy2 += 22;
        }
    }

    private static void friendAct(String action, java.util.UUID u) {
        net.minecraft.nbt.CompoundTag d = new net.minecraft.nbt.CompoundTag();
        d.putString("a", action);
        d.putUUID("u", u);
        dev.tcgshop.client.ClientSocial.act("friends", d);
    }

    // ------------------------------------------------------------------ display case manager

    private void displaysApp(GuiGraphics ctx, int mouseX, int mouseY) {
        AppDef a = def("displays");
        topBar(ctx, a, mouseX, mouseY);
        var data = dev.tcgshop.client.ClientSocial.displays;
        int px = sx + 8, py = sy + 41, pw = sw - 16, ph = sh - 52;
        // bulk actions
        int bx = px;
        String[][] bulk = {{"tcgshop.displays.all_market", "1.0"}, {"tcgshop.displays.all_90", "0.9"}, {"tcgshop.displays.all_110", "1.1"}, {"tcgshop.displays.all_125", "1.25"}};
        for (String[] b : bulk) {
            Component l = Component.translatable(b[0]);
            int w = font.width(l) + 10;
            Ui.button(ctx, bx, py, w, 13, l, mouseX, mouseY, true, 0xFF39415E);
            double f = Double.parseDouble(b[1]);
            areas.add(new Area(bx, py, w, 13, () -> {
                net.minecraft.nbt.CompoundTag d = new net.minecraft.nbt.CompoundTag();
                d.putString("a", "all_market");
                d.putDouble("f", f);
                dev.tcgshop.client.ClientSocial.act("displays", d);
            }));
            bx += w + 4;
        }
        // list of displayed products
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
                    ItemStack st = minecraft == null || minecraft.level == null ? ItemStack.EMPTY : ItemStack.parse(minecraft.level.registryAccess(), s.get("item")).orElse(ItemStack.EMPTY);
                    rows.add(new Object[]{d.getLong("pos"), s.getInt("i"), st, s.getLong("price"), s.getLong("market"), d.getString("block")});
                    totalValue += s.getLong("price") * Math.max(1, st.getCount());
                }
            }
        }
        ctx.drawString(font, Component.translatable("tcgshop.displays.summary", rows.size(), PriceService.format(totalValue)), px, py + 17, 0xFFCCD2E6, false);
        int ly = py + 29, lh = ph - 29, rowH = 22;
        listScroll = Mth.clamp(listScroll, 0, Math.max(0, rows.size() * rowH - lh));
        if (rows.isEmpty()) { paragraph(ctx, Component.translatable(zones.isEmpty() ? "tcgshop.displays.no_shop" : "tcgshop.displays.empty"), px + 4, ly + 6, pw - 8, Ui.MUTED); return; }
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
            ctx.renderItem(st, px + 3, y + 2);
            net.minecraft.core.BlockPos bp = net.minecraft.core.BlockPos.of(pos);
            ctx.drawString(font, Ui.ellipsis(st.getHoverName().getString(), pw - 190), px + 22, y + 2, 0xFFFFFFFF, false);
            ctx.drawString(font, Component.translatable((String) r[5]).getString() + " · " + bp.getX() + " " + bp.getY() + " " + bp.getZ(), px + 22, y + 11, 0xFF8A90A8, false);
            double diff = market == 0 ? 0 : (price - market) * 100.0 / market;
            String ps = PriceService.format(price);
            int rx = px + pw - 118;
            ctx.drawString(font, ps, rx - font.width(ps) - 4, y + 2, Ui.GOLD, false);
            String dfs = String.format(Locale.ROOT, "%+.0f%%", diff);
            ctx.drawString(font, dfs, rx - font.width(dfs) - 4, y + 11, diff > 15 ? 0xFFFF7A5C : diff < -15 ? 0xFF6BC5FF : 0xFF8BE08B, false);
            boolean vis = y >= ly && y + rowH <= ly + lh + 4;
            String[] lbl = {"-5%", "+5%", "M"};
            long[] np = {Math.max(1, Math.round(price * 0.95)), Math.round(price * 1.05) + 1, Math.max(1, market)};
            for (int k = 0; k < 3; k++) {
                int bxx = rx + k * 38;
                Ui.button(ctx, bxx, y + 3, 35, 14, Component.literal(lbl[k]), mouseX, mouseY, true, k == 2 ? 0xFF8A6A1C : 0xFF39415E);
                long newPrice = np[k];
                if (vis) areas.add(new Area(bxx, y + 3, 35, 14, () -> {
                    net.minecraft.nbt.CompoundTag d = new net.minecraft.nbt.CompoundTag();
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

    // ------------------------------------------------------------------ settings

    /** Player settings: how NPC speech bubbles look (color, background, size), bars and pack opening. */
    private void settingsApp(GuiGraphics ctx, int mouseX, int mouseY) {
        AppDef a = def("settings");
        topBar(ctx, a, mouseX, mouseY);
        var cs = dev.tcgshop.client.ClientSettings.INSTANCE;
        var gc = dev.tcgshop.TcgShopConfig.INSTANCE;
        // 1.0: sections (general settings / wallpaper)
        String[] stabs = {"general", "wallpaper"};
        String[] slabels = {Component.translatable("tcgshop.settings.tab.general").getString(), Component.translatable("tcgshop.settings.tab.wallpaper").getString()};
        int srw = rail(ctx, stabs, slabels, null, settingsTab, a.color, mouseX, mouseY, k -> settingsTab = k);
        if (settingsTab.equals("wallpaper")) {
            dev.tcgshop.client.phone.PhoneWallpaper.renderPicker(ctx, sx + srw, sy + 41, sw - srw - 8, sh - 52, mouseX, mouseY, now(),
                    (x, y, w, h, r) -> areas.add(new Area(x, y, w, h, r)));
            return;
        }
        int px = sx + srw, py = sy + 41, pw = sw - srw - 8, ph = sh - 52;
        int lw = Math.min(pw - 120, Math.max(150, pw / 2)), rowH = 20, y = py;
        String[] onoff = {"tcgshop.settings.off", "tcgshop.settings.on"};
        y = settingRow(ctx, px, y, lw, "tcgshop.settings.bubbles", onoff[cs.bubbles ? 1 : 0], mouseX, mouseY, () -> cs.bubbles = !cs.bubbles);
        y = settingRow(ctx, px, y, lw, "tcgshop.settings.text_color", "tcgshop.settings.tc." + cs.bubbleTextColor, mouseX, mouseY,
                () -> cs.bubbleTextColor = (cs.bubbleTextColor + 1) % dev.tcgshop.client.ClientSettings.TEXT_COLORS.length);
        y = settingRow(ctx, px, y, lw, "tcgshop.settings.background", "tcgshop.settings.bg." + cs.bubbleBackground, mouseX, mouseY,
                () -> cs.bubbleBackground = (cs.bubbleBackground + 1) % dev.tcgshop.client.ClientSettings.BG_COLORS.length);
        y = settingRow(ctx, px, y, lw, "tcgshop.settings.size", "tcgshop.settings.sz." + cs.bubbleSize, mouseX, mouseY,
                () -> cs.bubbleSize = (cs.bubbleSize + 1) % dev.tcgshop.client.ClientSettings.SIZES.length);
        y = settingRow(ctx, px, y, lw, "tcgshop.settings.bars", onoff[cs.patienceBars ? 1 : 0], mouseX, mouseY, () -> cs.patienceBars = !cs.patienceBars);
        y = settingRow(ctx, px, y, lw, "tcgshop.settings.pack_anim", onoff[gc.packOpeningAnimation ? 1 : 0], mouseX, mouseY, () -> {
            gc.packOpeningAnimation = !gc.packOpeningAnimation;
            dev.tcgshop.TcgShopConfig.save();
        });
        y = settingRow(ctx, px, y, lw, "tcgshop.settings.tooltip_price", onoff[gc.showPriceInTooltip ? 1 : 0], mouseX, mouseY, () -> {
            gc.showPriceInTooltip = !gc.showPriceInTooltip;
            dev.tcgshop.TcgShopConfig.save();
        });
        y = settingRow(ctx, px, y, lw, "tcgshop.settings.phone_upgrades",
                "tcgshop.phoneup.tier." + dev.tcgshop.phone.PhoneTier.of(dev.tcgshop.client.phone.PhoneClient.tier).key, mouseX, mouseY, () -> openApp("upgrades"));
        // speech bubble preview with the current settings
        int vx = px + lw + 10, vw = pw - lw - 10, vy = py;
        Ui.roundGradient(ctx, vx, vy, vw, ph, 0xFF3A6A3A, 0xFF1E3A24); // background "grass"
        ctx.drawString(font, Component.translatable("tcgshop.settings.preview").withStyle(ChatFormatting.BOLD), vx + 6, vy + 5, 0xFFFFFFFF, true);
        if (cs.bubbles) {
            Component sample = Component.translatable("tcgshop.settings.sample");
            var lines = font.split(sample, Math.max(40, vw - 30));
            float sc = Math.min(1.25f, cs.size());
            int tw2 = 0;
            for (var l : lines) tw2 = Math.max(tw2, font.width(l));
            int bw2 = tw2 + 10, bh2 = lines.size() * 10 + 7;
            var m = ctx.pose();
            m.pushPose();
            m.translate(vx + vw / 2f, vy + 26, 0);
            m.scale(sc, sc, 1);
            int bx2 = -bw2 / 2, by2 = 0, bg = cs.bgColor();
            if (bg != 0) {
                ctx.fill(bx2 - 1, by2 - 1, bx2 + bw2 + 1, by2 + bh2 + 1, 0xFF26232E);
                ctx.fill(bx2, by2, bx2 + bw2, by2 + bh2, bg);
            }
            for (int i = 0; i < lines.size(); i++)
                ctx.drawString(font, lines.get(i), bx2 + 5, by2 + 4 + i * 10, cs.textColor(), bg == 0);
            m.popPose();
        }
        if (cs.patienceBars) {
            int wcx = vx + vw / 2, wcy = vy + ph - 20; // patience wheel (65% remaining)
            for (int i = 0; i < 24; i++) {
                double ang = Math.toRadians(i * 15 - 90);
                int sx = wcx + (int) Math.round(Math.cos(ang) * 10), sy = wcy + (int) Math.round(Math.sin(ang) * 10);
                ctx.fill(sx - 1, sy - 1, sx + 1, sy + 1, i < 16 ? 0xFF5BD46B : 0xCC101018);
            }
        }
    }

    private int settingRow(GuiGraphics ctx, int x, int y, int w, String labelKey, String valueKey, int mouseX, int mouseY, Runnable change) {
        Ui.roundGradient(ctx, x, y, w, 18, SURF2, SURF);
        ctx.drawString(font, Ui.ellipsis(Component.translatable(labelKey).getString(), w - 80), x + 6, y + 5, 0xFFDDE3F0, false);
        Component v = Component.translatable(valueKey);
        int bw = Math.max(64, font.width(v) + 12);
        Ui.button(ctx, x + w - bw - 3, y + 2, bw, 14, v, mouseX, mouseY, true, 0xFF39415E);
        areas.add(new Area(x + w - bw - 3, y + 2, bw, 14, () -> {
            change.run();
            dev.tcgshop.client.ClientSettings.save();
        }));
        return y + 21;
    }

    private AppDef def(String key) {
        for (AppDef a : apps) if (a.key.equals(key)) return a;
        return apps.get(0);
    }

    // ------------------------------------------------------------------ market

    private void marketApp(GuiGraphics ctx, int mouseX, int mouseY, float delta, double t) {
        AppDef a = def("market");
        topBar(ctx, a, mouseX, mouseY);
        int barY = sy + 13;
        searchBox(ctx, sx + sw - 200, barY + 4, 150, mouseX, mouseY, delta);
        Component sl = Component.literal(market.sort == 0 ? "⇅" : market.sort == 1 ? "₽ ↑" : "₽ ↓");
        Ui.chip(ctx, sx + sw - 44, barY + 4, 34, 15, sl, market.sort != 0, Ui.in(mouseX, mouseY, sx + sw - 44, barY + 4, 34, 15), 0xFFFFE680, 0);
        areas.add(new Area(sx + sw - 44, barY + 4, 34, 15, () -> market.sort = (market.sort + 1) % 3));
        // 1.0: locked / unlocked filter (products of a higher phone tier), only with the upgrade system on
        if (dev.tcgshop.client.phone.PhoneClient.enabled) {
            Component ll = Component.translatable("tcgshop.phone.market.lock." + market.lockFilter);
            int lcw = Ui.chipWidth(ll) + 12, lcx = sx + sw - 206 - lcw;
            boolean lh = Ui.in(mouseX, mouseY, lcx, barY + 4, lcw, 15);
            Ui.chip(ctx, lcx, barY + 4, lcw, 15, Component.empty(), market.lockFilter != 0, lh, 0xFFFFE680, 0);
            int lockCol = market.lockFilter == 0 ? 0xFFB8BCCB : market.lockFilter == 1 ? 0xFF2E7D45 : 0xFF8A2A2A;
            dev.tcgshop.client.phone.LockIcon.draw(ctx, lcx + 5, barY + 7, 9, lockCol);
            Ui.text(ctx, ll, lcx + 14, barY + 4, lcw - 16, 15, market.lockFilter != 0 ? 0xFF15151C : Ui.TEXT, market.lockFilter == 0);
            areas.add(new Area(lcx, barY + 4, lcw, 15, () -> { market.lockFilter = (market.lockFilter + 1) % 3; market.resetScroll(); }));
            if (lh) setTooltipForNextRenderPass(font.split(Component.translatable("tcgshop.phone.market.lock.tip"), 200),
                    net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner.INSTANCE, true);
        }
        String[] keys = CatalogPanel.CATEGORIES;
        String[] labels = new String[keys.length];
        int[] counts = new int[keys.length];
        for (int i = 0; i < keys.length; i++) {
            labels[i] = Component.translatable("tcgshop.cat." + keys[i]).getString();
            counts[i] = market.count(keys[i]);
        }
        int rw = rail(ctx, keys, labels, counts, market.category, a.color, mouseX, mouseY, k -> { market.category = k; market.resetScroll(); });
        // 1.0: with an active loan nothing can be bought from the phone (the server refuses it too); «On sale» strip
        int top = sy + 40;
        if (dev.tcgshop.client.phone.PhoneAppsClient.hasLoan()) {
            int bw = sw - rw - 8;
            Ui.rounded(ctx, sx + rw, top, bw, 14, 4, 0xCC6A1A1A);
            Ui.text(ctx, Component.translatable("tcgshop.loan.market_banner"), sx + rw, top + 2, bw, 10, 0xFFFFD0D0, false);
            areas.add(new Area(sx + rw, top, bw, 14, () -> openApp("loans")));
            top += 17;
        }
        top += dev.tcgshop.client.phone.DiscountTag.strip(ctx, sx + rw, top, sw - rw - 8, marketGoods, marketPrices, mouseX, mouseY);
        market.render(ctx, sx + rw, top, sw - rw - 8, sh - 50 - (top - sy - 40), mouseX, mouseY, t);
    }

    // ------------------------------------------------------------------ prices

    private void pricesApp(GuiGraphics ctx, int mouseX, int mouseY, float delta, double t) {
        AppDef a = def("prices");
        topBar(ctx, a, mouseX, mouseY);
        int barY = sy + 13;
        // set selector in the bar
        List<Catalog.CardSet> sets = Catalog.sets();
        int si = -1;
        for (int i = 0; i < sets.size(); i++) if (sets.get(i).id().equals(priceSet)) si = i;
        Component setLabel = si < 0 ? Component.translatable("tcgshop.binder.all_sets") : sets.get(si).name();
        int selW = Math.min(150, sw / 4), selX = sx + sw - selW - 170;
        Ui.round(ctx, selX, barY + 4, selW, 15, 0xAA0E1018);
        Ui.text(ctx, setLabel, selX + 12, barY + 4, selW - 24, 15, 0xFFFFFFFF, true);
        ctx.drawString(font, "‹", selX + 4, barY + 7, 0xFFFFFFFF, true);
        ctx.drawString(font, "›", selX + selW - 8, barY + 7, 0xFFFFFFFF, true);
        final int fsi = si;
        areas.add(new Area(selX, barY + 4, 12, 15, () -> { priceSet = fsi <= 0 ? (fsi == 0 ? "" : sets.get(sets.size() - 1).id()) : sets.get(fsi - 1).id(); listScroll = 0; }));
        areas.add(new Area(selX + selW - 12, barY + 4, 12, 15, () -> { priceSet = fsi + 1 < sets.size() ? sets.get(fsi + 1).id() : ""; listScroll = 0; }));
        searchBox(ctx, sx + sw - 162, barY + 4, 152, mouseX, mouseY, delta);
        // rail: rarities
        Rarity.Family[] rs = new Rarity.Family[Rarity.Family.values().length + 1];
        System.arraycopy(Rarity.Family.values(), 0, rs, 1, rs.length - 1);
        String[] keys = new String[rs.length], labels = new String[rs.length];
        for (int i = 0; i < rs.length; i++) {
            keys[i] = rs[i] == null ? "all" : rs[i].key;
            labels[i] = rs[i] == null ? Component.translatable("tcgshop.cat.all").getString() : rs[i].displayName().getString();
        }
        int rw = rail(ctx, keys, labels, null, priceRarity == null ? "all" : priceRarity.key, a.color, mouseX, mouseY, k -> {
            priceRarity = null;
            for (Rarity.Family r : rs) if (r != null && r.key.equals(k)) priceRarity = r;
            listScroll = 0;
        });
        int px = sx + rw, py = sy + 41, pw = sw - rw - 8, ph = sh - 52;
        int listW = pw * 55 / 100, detX = px + listW + 8, detW = pw - listW - 8;
        // results list
        String q = search.getValue().trim().toLowerCase(Locale.ROOT);
        List<Catalog.Card> res = new ArrayList<>();
        int cap = priceBand != 0 || priceSort != 0 ? 20_000 : 600;
        for (Catalog.CardSet s : sets) {
            if (!priceSet.isEmpty() && !s.id().equals(priceSet)) continue;
            for (Catalog.Card c : s.cards()) {
                if (priceRarity != null && c.rarity().family != priceRarity) continue;
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
        // price range chips + sort
        int chx = px + 4, chy = py + 3;
        for (int b = 0; b < BAND_LABEL.length; b++) {
            Component lbl = b == 0 ? Component.translatable("tcgshop.cat.all") : Component.literal(BAND_LABEL[b]);
            int cwid = font.width(lbl) + 8;
            if (chx + cwid > px + listW - 30) break;
            boolean on = priceBand == b, hv = Ui.in(mouseX, mouseY, chx, chy, cwid, 12);
            Ui.round(ctx, chx, chy, cwid, 12, on ? withAlpha(a.color, 0.85f) : hv ? 0x44FFFFFF : 0x22FFFFFF);
            ctx.drawString(font, lbl, chx + 4, chy + 2, on ? 0xFFFFFFFF : 0xFFC8CCDA, false);
            int fb = b;
            areas.add(new Area(chx, chy, cwid, 12, () -> { priceBand = fb; listScroll = 0; }));
            chx += cwid + 3;
        }
        String so = priceSort == 0 ? "⇅" : priceSort == 1 ? "₽↑" : "₽↓";
        int sow = font.width(so) + 8, sox = px + listW - sow - 4;
        // 1.1: Grade 10 toggle: the list shows what a perfect slab of each card is worth
        String g10 = "G10";
        int g10w = font.width(g10) + 8, g10x = sox - g10w - 3;
        boolean g10h = Ui.in(mouseX, mouseY, g10x, chy, g10w, 12);
        Ui.round(ctx, g10x, chy, g10w, 12, priceG10 ? 0xCC6BC5FF : g10h ? 0x44FFFFFF : 0x22FFFFFF);
        ctx.drawString(font, g10, g10x + 4, chy + 2, priceG10 ? 0xFF0A1A2A : 0xFFC8CCDA, false);
        areas.add(new Area(g10x, chy, g10w, 12, () -> { priceG10 = !priceG10; listScroll = 0; }));
        if (g10h) setTooltipForNextRenderPass(Component.translatable("tcgshop.phone.g10_toggle"));
        sox = g10x;
        Ui.round(ctx, sox, chy, sow, 12, priceSort != 0 ? withAlpha(Ui.GOLD, 0.6f) : 0x22FFFFFF);
        ctx.drawString(font, so, sox + 4, chy + 2, priceSort != 0 ? 0xFF1A1400 : 0xFFC8CCDA, false);
        areas.add(new Area(sox, chy, sow, 12, () -> { priceSort = (priceSort + 1) % 3; listScroll = 0; }));
        String cnt = res.size() >= 600 ? "600+" : String.valueOf(res.size());
        int rowH = 14, top = py + 18, h = ph - 21;
        ctx.drawString(font, cnt, sox - font.width(cnt) - 4, chy + 2, 0xFF8A8FA8, false);
        listScroll = Mth.clamp(listScroll, 0, Math.max(0, res.size() * rowH - h));
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
            int pwid = font.width(price);
            ctx.drawString(font, Ui.ellipsis(c.name().getString(), listW - 24 - pwid), px + 13, y + 3, 0xFFFFFFFF, false);
            ctx.drawString(font, price, px + listW - 7 - pwid, y + 3, priceG10 ? 0xFF6BC5FF : Ui.GOLD, false);
            String key = c.key();
            if (y >= top && y + rowH <= top + h + rowH) areas.add(new Area(px + 3, Math.max(y, top), listW - 6, rowH - 1, () -> priceCard = key));
        }
        ctx.disableScissor();
        if (res.isEmpty()) Ui.text(ctx, Component.translatable("tcgshop.phone.no_results"), px, py + ph / 2 - 5, listW, 10, Ui.MUTED, false);
        // detail card
        Ui.roundGradient(ctx, detX, py, detW, ph, SURF2, SURF);
        Ui.outline(ctx, detX, py, detW, ph, LINE);
        Catalog.Card sel = priceCard == null ? null : Catalog.card(priceCard);
        if (sel == null) {
            paragraph(ctx, Component.translatable("tcgshop.phone.search_hint"), detX + 8, py + 10, detW - 16, Ui.MUTED);
            return;
        }
        int cw = Math.min(64, detW / 2 - 10), ch = Math.round(cw / Draw.CARD_RATIO);
        Draw.glow(ctx, detX + 8 + cw / 2f, py + 8 + ch / 2f, cw, ch, sel.rarity().color, 0.25f);
        Draw.cardFace(ctx, sel.texture(), sel.stack(), detX + 8, py + 8, cw, ch, 1f);
        if (sel.rarity().tier > 0) Draw.holo(ctx, detX + 8, py + 8, cw, ch, sel.rarity().tier, sel.rarity().color, t, mouseX);
        int tx = detX + cw + 16, tw = detW - cw - 22, ty = py + 10;
        for (var line : font.split(sel.name().copy().withStyle(ChatFormatting.BOLD), tw)) {
            ctx.drawString(font, line, tx, ty, 0xFFFFFFFF);
            ty += 10;
        }
        ctx.drawString(font, sel.rarity().displayName(), tx, ty + 2, sel.rarity().color);
        Draw.scaledCentered(ctx, Component.literal(PriceService.format(PriceService.priceCd(sel.stack()))).withStyle(ChatFormatting.BOLD), tx + tw / 2f, ty + 16, 1.4f, Ui.GOLD);
        int owned = ClientCollection.count(sel.key());
        ctx.drawString(font, Component.translatable("tcgshop.phone.owned", owned), tx, ty + 34, owned > 0 ? 0xFF6BE07B : Ui.MUTED, false);
        // 1.1: what a Grade 10 slab of this card is worth today (and its multiplier over the raw card)
        long g10v = PriceService.grade10Cd(sel.id(), sel.rarity()), rawv = PriceService.priceCd(sel.id(), sel.rarity());
        String mult = rawv > 0 ? String.format(Locale.ROOT, " (x%.1f)", g10v / (double) rawv) : "";
        for (var line : font.split(Component.translatable("tcgshop.phone.g10_value", PriceService.format(g10v) + mult), tw)) {
            ctx.drawString(font, line, tx, ty + 46, 0xFF6BC5FF, false);
            ty += 10;
        }
        // chart
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
        ctx.drawString(font, Component.translatable("tcgshop.phone.chart").append(String.format(Locale.ROOT, "  %+.1f%%", ch30)), gx + 4, gy + 3, col, false);
    }

    // ------------------------------------------------------------------ quick sell

    private void sellApp(GuiGraphics ctx, int mouseX, int mouseY, double t) {
        AppDef a = def("sell");
        topBar(ctx, a, mouseX, mouseY);
        Component slogan = Component.translatable("tcgshop.sell.phone_slogan");
        ctx.drawString(font, slogan, sx + sw - 10 - font.width(slogan), sy + 20, 0xCCFFFFFF, false);
        // submenu: sell from the inventory or from the Binder (these used to be two separate apps)
        int invCount = minecraft == null || minecraft.player == null ? 0 : CardPickGrid.cardSlots(minecraft.player).size();
        int rw = rail(ctx, new String[]{"inv", "binder"}, new String[]{Component.translatable("tcgshop.sell.tab_inv").getString(),
                Component.translatable("tcgshop.sell.tab_binder").getString()}, new int[]{invCount, -1}, sellTab, a.color, mouseX, mouseY, k -> sellTab = k);
        int px = sx + rw, py = sy + 41, pw = sw - rw - 8, ph = sh - 52;
        if (sellTab.equals("binder")) { sellBinderTab(ctx, a, px, py, pw, ph, mouseX, mouseY); return; }
        int sideW = Math.min(140, pw / 3), gw = pw - sideW - 8;
        Ui.roundGradient(ctx, px, py, gw, ph, SURF2, SURF);
        Ui.outline(ctx, px, py, gw, ph, LINE);
        ItemStack hov = ItemStack.EMPTY;
        if (quickSell.isEmpty()) paragraph(ctx, Component.translatable("tcgshop.sell.no_cards"), px + 8, py + 10, gw - 16, Ui.MUTED);
        else hov = quickSell.render(ctx, px + 4, py + 4, gw - 8, ph - 8, mouseX, mouseY, 32);
        int x = px + gw + 8, y = py;
        Ui.roundGradient(ctx, x, y, sideW, ph, SURF2, SURF);
        Ui.outline(ctx, x, y, sideW, ph, LINE);
        ctx.fill(x + 1, y + 1, x + sideW - 1, y + 3, a.color);
        ctx.drawString(font, Component.translatable("tcgshop.phone.sell").withStyle(ChatFormatting.BOLD), x + 7, y + 8, 0xFFFFFFFF, false);
        int ty = paragraph(ctx, Component.translatable("tcgshop.sell.phone_hint"), x + 7, y + 21, sideW - 14, 0xFFB8BED6);
        if (dev.tcgshop.shop.CoreResale.rate() > 0) // 1.1: old shop cores are bought too
            ty = paragraph(ctx, Component.translatable("tcgshop.sell.core_hint", Math.round(dev.tcgshop.shop.CoreResale.rate() * 100)), x + 7, ty + 2, sideW - 14, 0xFFE8C77A);
        // select all / none
        int hb = (sideW - 18) / 2;
        Ui.button(ctx, x + 6, ty + 4, hb, 14, Component.translatable("tcgshop.sell.all"), mouseX, mouseY, true, 0xFF4A5070);
        areas.add(new Area(x + 6, ty + 4, hb, 14, () -> { if (minecraft != null) quickSell.sel.addAll(CardPickGrid.cardSlots(minecraft.player)); }));
        Ui.button(ctx, x + 12 + hb, ty + 4, hb, 14, Component.translatable("tcgshop.sell.none"), mouseX, mouseY, !quickSell.sel.isEmpty(), 0xFF4A5070);
        areas.add(new Area(x + 12 + hb, ty + 4, hb, 14, quickSell.sel::clear));
        long got = minecraft != null && minecraft.player != null ? quickSell.total(minecraft.player) : 0;
        int cards = 0;
        long market = 0; // 1.1: summed per item (cores and cards pay different rates)
        if (minecraft != null && minecraft.player != null) for (int sl : quickSell.sel) {
            ItemStack it = minecraft.player.getInventory().getItem(sl);
            cards += it.getCount();
            if (!it.isEmpty()) market += dev.tcgshop.shop.CardSales.unitValue(it) * it.getCount();
        }
        int sy2 = ty + 26;
        ctx.drawString(font, Component.translatable("tcgshop.sell.count", cards), x + 7, sy2, 0xFFDDDDDD, false);
        ctx.drawString(font, Component.translatable("tcgshop.sell.market", PriceService.format(market)), x + 7, sy2 + 11, 0xFF8A8FA8, false);
        Draw.scaledCentered(ctx, Component.literal("+" + PriceService.format(got)).withStyle(ChatFormatting.BOLD), x + sideW / 2f, sy2 + 26, 1.35f, Ui.GOLD);
        int bh = 20, bY = y + ph - bh - 6;
        boolean can = !quickSell.sel.isEmpty();
        boolean armed = can && now() - sellArmedAt < 3;
        Ui.button(ctx, x + 6, bY, sideW - 12, bh, Component.translatable(armed ? "tcgshop.sell.confirm" : "tcgshop.sell.sell_btn"), mouseX, mouseY, can,
                armed ? 0xFFB02020 : a.color);
        if (can) areas.add(new Area(x + 6, bY, sideW - 12, bh, () -> {
            if (now() - sellArmedAt < 3) {
                ClientPlatform.INSTANCE.send(new dev.tcgshop.network.MarketPayloads.Sell(-1, dev.tcgshop.network.MarketPayloads.SELL_CARDS,
                        new ArrayList<>(quickSell.sel)));
                quickSell.sel.clear();
                sellArmedAt = -10;
            } else sellArmedAt = now();
        }));
        if (!hov.isEmpty()) {
            long v = dev.tcgshop.shop.CardSales.unitValue(hov);
            boolean core = dev.tcgshop.shop.CoreResale.isCore(hov);
            List<Component> tip = new ArrayList<>(List.of(hov.getHoverName(), Component.translatable("tcgshop.sell.market", PriceService.format(v)).withStyle(ChatFormatting.GRAY)));
            String lockWhy = core ? CardPickGrid.coreLockReason(hov) : null;
            if (lockWhy != null) tip.add(Component.translatable(lockWhy).withStyle(ChatFormatting.RED));
            else if (core) {
                tip.add(Component.translatable("tcgshop.sell.core_tip", Math.round(dev.tcgshop.shop.CoreResale.rate() * 100)).withStyle(ChatFormatting.YELLOW));
                tip.add(Component.translatable("tcgshop.sell.you_get", PriceService.format(dev.tcgshop.shop.CoreResale.unitPrice(hov))).withStyle(ChatFormatting.GOLD));
            } else tip.add(Component.translatable("tcgshop.sell.you_get", PriceService.format(quickSell.price.applyAsLong(hov))).withStyle(ChatFormatting.GOLD));
            // outside the phone's scissor: the Screen draws it at the end of the frame
            setTooltipForNextRenderPass(tip.stream().map(Component::getVisualOrderText).toList(), net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner.INSTANCE, true);
        }
    }

    /** Binder tab of the sell app: summary of your collection and access to the Binder Market (bulk sale, favorites, offers). */
    private void sellBinderTab(GuiGraphics ctx, AppDef a, int px, int py, int pw, int ph, int mouseX, int mouseY) {
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
        ctx.drawString(font, Component.translatable("tcgshop.sell.binder_title").withStyle(ChatFormatting.BOLD), px + 8, py + 9, 0xFFFFFFFF, false);
        int cw = (pw - 24) / 3, ty = py + 24;
        tile(ctx, px + 8, ty, cw, 34, "tcgshop.sell.b_cards", String.valueOf(cards), 0xFFFFFFFF);
        tile(ctx, px + 12 + cw, ty, cw, 34, "tcgshop.sell.b_dupes", String.valueOf(dupes), 0xFF8BE08B);
        tile(ctx, px + 16 + cw * 2, ty, cw, 34, "tcgshop.sell.b_value", compact(value), Ui.GOLD);
        int y = paragraph(ctx, Component.translatable("tcgshop.sell.binder_hint"), px + 8, ty + 42, pw - 16, 0xFFB8BED6);
        int bw = Math.min(220, pw - 16), bx = px + (pw - bw) / 2, by = Math.min(py + ph - 30, y + 8);
        Ui.button(ctx, bx, by, bw, 22, Component.translatable("tcgshop.sell.open_binder_market"), mouseX, mouseY, true, a.color);
        areas.add(new Area(bx, by, bw, 22, () -> {
            onClose();
            dev.tcgshop.client.ClientSocial.act("binder_open", "open");
        }));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double h, double v) {
        if (dev.tcgshop.client.phone.PhoneNotices.contains(mouseX, mouseY)) return true;
        if (app == App.LEAGUE && leagueHelp.mouseScrolled(mouseX, mouseY, v)) return true;
        if (app == App.LEAGUE && leagueTab.equals("deck") && deckPanel.mouseScrolled(mouseX, mouseY, v)) return true;
        if (app == App.FURNITURE) { furniture.mouseScrolled(mouseX, mouseY, v); return true; }
        if (app == App.MESSAGES) { messagesApp.scroll(v); return true; }
        if (app == App.STATS) { statsApp.scroll(v); return true; }
        if (app == App.LOANS) { loansApp.scroll(v); return true; }
        if (app == App.RANKING) { rankingApp.scroll(v); return true; }
        if (app == App.SELL) { quickSell.scroll(v); return true; }
        if (app == App.GRADE && gradeTab.equals("send")) { gradePanel.scroll(mouseX, mouseY, v); return true; }
        if (app == App.MARKET) market.scroll(v);
        else if (app == App.LEAGUE && hoverDeckList) deckScroll -= v * 30;
        else if (app == App.GRADE || app == App.LEAGUE) listScroll -= v * 30;
        else listScroll -= v * 28;
        return true;
    }

    // ------------------------------------------------------------------ my shop

    private void shopApp(GuiGraphics ctx, int mouseX, int mouseY, double t) {
        AppDef a = def("shop");
        topBar(ctx, a, mouseX, mouseY);
        List<ShopClient.ZoneInfo> mine = myZones();
        if (mine.isEmpty()) {
            paragraph(ctx, Component.translatable("tcgshop.phone.no_shop"), sx + 20, sy + 50, sw - 40, 0xFFCCCCCC);
            return;
        }
        zoneIndex = Math.floorMod(zoneIndex, mine.size());
        ShopClient.ZoneInfo z = mine.get(zoneIndex);
        // shop selector in the bar
        int barY = sy + 13, selW = 190, selX = sx + sw - selW - 10;
        Ui.round(ctx, selX, barY + 4, selW, 15, 0xAA0E1018);
        ctx.fill(selX + 3, barY + 7, selX + 6, barY + 16, z.tierInfo().color | 0xFF000000);
        Ui.text(ctx, Component.translatable("tcgshop.shop.tier." + z.tierInfo().key).append(" · " + z.core().toShortString()), selX + 14, barY + 4, selW - 28, 15, 0xFFFFFFFF, true);
        if (mine.size() > 1) {
            ctx.drawString(font, "›", selX + selW - 8, barY + 7, 0xFFFFFFFF, true);
            areas.add(new Area(selX, barY + 4, selW, 15, () -> { zoneIndex++; abandonArmed = null; }));
        }
        if (z.dormant()) { // 1.1: core removed: only the till and abandoning are possible until it is restored
            dormantPanel(ctx, z, sx + 12, sy + 41, sw - 24, sh - 52, mouseX, mouseY);
            return;
        }
        String[] tabs = {"summary", "staff", "upgrades", "security", "reputation", "reviews", "events"};
        String[] labels = new String[tabs.length];
        int[] counts = new int[tabs.length];
        for (int i = 0; i < tabs.length; i++) {
            labels[i] = Component.translatable("tcgshop.phone.tab." + tabs[i]).getString();
            counts[i] = tabs[i].equals("reviews") ? z.reviews().size()
                    : tabs[i].equals("events") ? (z.ext().getCompound("ev").getList("active", 10).isEmpty() ? -1 : z.ext().getCompound("ev").getList("active", 10).size()) : -1;
        }
        int rw = rail(ctx, tabs, labels, counts, shopTab, a.color, mouseX, mouseY, k -> { shopTab = k; listScroll = 0; });
        int px = sx + rw, py = sy + 41, pw = sw - rw - 8, ph = sh - 52;
        switch (shopTab) {
            case "staff" -> staffTab(ctx, z, px, py, pw, mouseX, mouseY);
            case "upgrades" -> upgradesTab(ctx, z, px, py, pw, ph, mouseX, mouseY); // 1.1: cashier and guard upgrades
            case "security" -> securityTab(ctx, z, px, py, pw);
            case "reputation" -> repTab(ctx, z, px, py, pw, ph);
            case "reviews" -> reviewsTab(ctx, z, px, py, pw, ph);
            case "events" -> eventsTab(ctx, z, px, py, pw, ph);
            default -> summaryTab(ctx, z, px, py, pw, ph, mouseX, mouseY);
        }
    }

    private void summaryTab(GuiGraphics ctx, ShopClient.ZoneInfo z, int px, int py, int pw, int ph, int mouseX, int mouseY) {
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
        // withdraw the register's money to your account, from anywhere
        int rows = (tiles.length + cols - 1) / cols, by = Math.min(py + ph - 20, py + rows * (th + gap) + 4);
        // 1.1: open / close button: its own row when there is room, otherwise a third button next to the two withdraws
        boolean ownRow = by + 40 <= py + ph;
        int bw = ownRow ? (pw - gap) / 2 : (pw - gap * 2) / 3;
        // 1.1: the register window (withdraw by percentage / custom amount with an "are you sure?", or deposit)
        boolean has = z.balance() > 0;
        Ui.button(ctx, px, by, bw, 18, Component.translatable("tcgshop.phone.withdraw_btn", PriceService.format(z.balance())), mouseX, mouseY, has, 0xFF2E8A4A);
        Ui.button(ctx, px + bw + gap, by, bw, 18, Component.translatable("tcgshop.phone.deposit_btn"), mouseX, mouseY, true, 0xFF3A6FD8);
        if (has) areas.add(new Area(px, by, bw, 18, () -> openRegister(z, false)));
        areas.add(new Area(px + bw + gap, by, bw, 18, () -> openRegister(z, true)));
        int tx = ownRow ? px : px + (bw + gap) * 2, ty = ownRow ? by + 22 : by, tbw = ownRow ? pw : bw;
        Ui.button(ctx, tx, ty, tbw, 18, Component.translatable(z.closed() ? "tcgshop.phone.shop_open" : "tcgshop.phone.shop_close"), mouseX, mouseY, true,
                z.closed() ? 0xFF2E8A4A : 0xFFB83A3A);
        areas.add(new Area(tx, ty, tbw, 18, () -> toggleOpen(z)));
    }

    /** 1.1: the owner opens or closes the shop (ShopOpenClose.toggle on the server). */
    static void toggleOpen(ShopClient.ZoneInfo z) {
        net.minecraft.nbt.CompoundTag d = new net.minecraft.nbt.CompoundTag();
        d.putUUID("zone", z.id());
        dev.tcgshop.client.ClientSocial.act("shop_open_toggle", d);
    }

    /** 1.1: a dormant shop (core removed): what is kept, its till, and abandoning it (two clicks). */
    private void dormantPanel(GuiGraphics ctx, ShopClient.ZoneInfo z, int px, int py, int pw, int ph, int mouseX, int mouseY) {
        int y = paragraph(ctx, Component.translatable("tcgshop.phone.dormant").withStyle(ChatFormatting.BOLD), px, py, pw, 0xFFFFC266);
        y = paragraph(ctx, Component.translatable("tcgshop.phone.dormant_hint"), px, y + 3, pw, 0xFFAAB0C8) + 6;
        int gap = 5, tw = (pw - gap * 2) / 3, th = 34;
        tile(ctx, px, y, tw, th, "tcgshop.phone.s_balance", PriceService.format(z.balance()), Ui.GOLD);
        tile(ctx, px + tw + gap, y, tw, th, "tcgshop.phone.s_revenue", PriceService.format(z.revenue()), 0xFF6BE07B);
        tile(ctx, px + (tw + gap) * 2, y, tw, th, "tcgshop.phone.s_sales", String.valueOf(z.sales()), 0xFFFFFFFF);
        int by = Math.min(py + ph - 20, y + th + 8), bw = (pw - gap) / 2;
        boolean has = z.balance() > 0;
        Ui.button(ctx, px, by, bw, 18, Component.translatable("tcgshop.phone.withdraw_btn", PriceService.format(z.balance())), mouseX, mouseY, has, 0xFF2E8A4A);
        if (has) areas.add(new Area(px, by, bw, 18, () -> openRegister(z, false)));
        boolean armed = z.id().equals(abandonArmed);
        Ui.button(ctx, px + bw + gap, by, bw, 18, Component.translatable(armed ? "tcgshop.phone.abandon_confirm" : "tcgshop.phone.abandon"), mouseX, mouseY, true,
                armed ? 0xFFE03A3A : 0xFF8A2E2E);
        areas.add(new Area(px + bw + gap, by, bw, 18, () -> {
            if (!z.id().equals(abandonArmed)) { abandonArmed = z.id(); return; }
            abandonArmed = null;
            net.minecraft.nbt.CompoundTag d = new net.minecraft.nbt.CompoundTag();
            d.putUUID("zone", z.id());
            dev.tcgshop.client.ClientSocial.act("shop_abandon", d);
        }));
        if (armed && by + 22 + 10 <= py + ph)
            paragraph(ctx, Component.translatable("tcgshop.phone.abandon_warn", PriceService.format(z.balance())), px, by + 23, pw, 0xFFFF8A8A);
    }

    /** 1.1: opens the register window over the phone (closing it comes back here). */
    private void openRegister(ShopClient.ZoneInfo z, boolean deposit) {
        if (minecraft != null) minecraft.setScreen(new RegisterScreen(z.id(), deposit, this));
    }

    private void staffTab(GuiGraphics ctx, ShopClient.ZoneInfo z, int px, int py, int pw, int mouseX, int mouseY) {
        // two cashier posts (day shift) and the guards (always; 1.1: up to three)
        int cw = (pw - 12) / 3;
        staffCard(ctx, z, px, py, cw, "cashier", z.cashier(), z.cashierName(), ShopServer.CASHIER_HIRE, ShopServer.CASHIER_WAGE,
                new ItemStack(TcgBlocks.CASH_REGISTER_ITEM), mouseX, mouseY);
        staffCard(ctx, z, px + cw + 6, py, cw, "cashier2", z.ext().getBoolean("cashier2"), z.ext().getString("cashier2_name"),
                ShopServer.CASHIER_HIRE, ShopServer.CASHIER_WAGE, new ItemStack(TcgBlocks.CASH_REGISTER_ITEM), mouseX, mouseY);
        guardCard(ctx, z, px + (cw + 6) * 2, py, cw, mouseX, mouseY);
        boolean day = z.ext().getBoolean("day");
        int y2 = paragraph(ctx, Component.translatable(day ? "tcgshop.phone.shift_day" : "tcgshop.phone.shift_night"), px + 4, py + 90, pw - 8,
                day ? 0xFF8BE08B : 0xFFFFC266);
        y2 = paragraph(ctx, Component.translatable("tcgshop.phone.staff_tip2"), px + 4, y2 + 3, pw - 8, 0xFFAAB0C8);
        paragraph(ctx, Component.translatable("tcgshop.phone.staff_tip_upgrades"), px + 4, y2 + 3, pw - 8, 0xFF9FD8FF);
    }

    private void staffCard(GuiGraphics ctx, ShopClient.ZoneInfo z, int x, int y, int w, String role, boolean hired, String name, long hire, long wage,
                           ItemStack icon, int mouseX, int mouseY) {
        int h = 84;
        staffCardFrame(ctx, x, y, w, h, hired, icon, Component.translatable("tcgshop.role." + role));
        ctx.drawString(font, hired ? Component.literal(Ui.ellipsis(name, w - 44)) : Component.translatable("tcgshop.phone.vacant"), x + 38, y + 20,
                hired ? 0xFF8BE08B : 0xFF999999, false);
        Ui.text(ctx, Component.literal(Ui.ellipsis(Component.translatable("tcgshop.phone.hire_cost", PriceService.format(hire)).getString(), w - 12)), x + 6, y + 38, w - 12, 10, 0xFFCCCCCC, false);
        Ui.text(ctx, Component.literal(Ui.ellipsis(Component.translatable("tcgshop.phone.wage_cost", PriceService.format(wage)).getString(), w - 12)), x + 6, y + 49, w - 12, 10, 0xFFCCCCCC, false);
        int bw = w - 16, by = y + h - 20;
        Ui.button(ctx, x + 8, by, bw, 14, Component.translatable(hired ? "tcgshop.phone.fire" : "tcgshop.phone.hire"), mouseX, mouseY, true, hired ? 0xFF7A2020 : 0xFF3C8A4A);
        areas.add(new Area(x + 8, by, bw, 14, () -> ClientPlatform.INSTANCE.send(new StaffPayload(z.id(), role, !hired))));
    }

    private void staffCardFrame(GuiGraphics ctx, int x, int y, int w, int h, boolean hired, ItemStack icon, Component title) {
        Ui.roundGradient(ctx, x, y, w, h, hired ? 0xF0213A2C : SURF2, SURF);
        Ui.outline(ctx, x, y, w, h, hired ? 0xFF43C463 : LINE);
        Ui.round(ctx, x + 6, y + 6, 26, 26, 0x44000000);
        var m = ctx.pose();
        m.pushPose();
        m.translate(x + 19, y + 19, 0);
        m.scale(1.3f, 1.3f, 1);
        ctx.renderItem(icon, -8, -8);
        m.popPose();
        ctx.drawString(font, title.copy().withStyle(ChatFormatting.BOLD), x + 38, y + 8, 0xFFFFFFFF);
    }

    /** 1.1: the guards card: how many are hired (of 3), their names, + Hire / - Fire (the last hired one). */
    private void guardCard(GuiGraphics ctx, ShopClient.ZoneInfo z, int x, int y, int w, int mouseX, int mouseY) {
        int h = 84;
        int n = Math.max(z.guard() ? 1 : 0, z.ext().getInt("guards")), max = Math.max(1, z.ext().contains("guards_max") ? z.ext().getInt("guards_max") : 3);
        staffCardFrame(ctx, x, y, w, h, n > 0, new ItemStack(Items.IRON_SWORD), Component.translatable("tcgshop.role.guards"));
        ctx.drawString(font, Component.translatable("tcgshop.phone.guards_count", n, max), x + 38, y + 20, n > 0 ? 0xFF8BE08B : 0xFF999999, false);
        StringBuilder names = new StringBuilder();
        for (int i = 0; i < n; i++) {
            String nm = i == 0 ? z.guardName() : z.ext().getString("guard_name_" + i);
            if (nm.isEmpty()) continue;
            if (names.length() > 0) names.append(", ");
            names.append(nm);
        }
        Ui.text(ctx, Component.literal(Ui.ellipsis(n == 0 ? Component.translatable("tcgshop.phone.vacant").getString() : names.toString(), w - 12)), x + 6, y + 36, w - 12, 10,
                n > 0 ? 0xFFDDE3F0 : 0xFF999999, false);
        Ui.text(ctx, Component.literal(Ui.ellipsis(Component.translatable("tcgshop.phone.hire_cost", PriceService.format(ShopServer.GUARD_HIRE)).getString(), w - 12)), x + 6, y + 47, w - 12, 10, 0xFFCCCCCC, false);
        Ui.text(ctx, Component.literal(Ui.ellipsis(Component.translatable("tcgshop.phone.wage_each", PriceService.format(ShopServer.GUARD_WAGE)).getString(), w - 12)), x + 6, y + 57, w - 12, 10, 0xFFCCCCCC, false);
        int bw = (w - 20) / 2, by = y + h - 20;
        boolean canHire = n < max, canFire = n > 0;
        Ui.button(ctx, x + 8, by, bw, 14, Component.translatable("tcgshop.phone.hire_one"), mouseX, mouseY, canHire, 0xFF3C8A4A);
        Ui.button(ctx, x + 12 + bw, by, bw, 14, Component.translatable("tcgshop.phone.fire_one"), mouseX, mouseY, canFire, 0xFF7A2020);
        if (canHire) areas.add(new Area(x + 8, by, bw, 14, () -> ClientPlatform.INSTANCE.send(new StaffPayload(z.id(), "guard", true))));
        if (canFire) areas.add(new Area(x + 12 + bw, by, bw, 14, () -> ClientPlatform.INSTANCE.send(new StaffPayload(z.id(), "guard", false))));
    }

    // ------------------------------------------------------------------ 1.1: staff upgrades tab

    /** Upgrade card waiting for its confirm click ("cashier_up_speed"...) and when it was armed. */
    private String upArmed;
    private double upArmedAt;

    private static final ItemStack[] CASHIER_ICONS = {new ItemStack(Items.SUGAR), new ItemStack(Items.LEATHER_BOOTS), new ItemStack(Items.SPYGLASS), new ItemStack(Items.GOLD_INGOT)};
    private static final ItemStack[] GUARD_ICONS = {new ItemStack(Items.IRON_CHESTPLATE), new ItemStack(Items.IRON_SWORD), new ItemStack(Items.GOLDEN_APPLE), new ItemStack(Items.ENDER_EYE)};

    /**
     * One clear card per upgrade, for the cashiers (shared by both registers) and the guards (shared by every guard): its
     * level, what it does now, what the next level gives, the price and a single button (second click within 3 s confirms).
     */
    private void upgradesTab(GuiGraphics ctx, ShopClient.ZoneInfo z, int px, int py, int pw, int ph, int mouseX, int mouseY) {
        int[] cup = z.ext().getIntArray("cup"), gup = z.ext().getIntArray("gup");
        boolean anyCashier = z.cashier() || z.ext().getBoolean("cashier2"), anyGuard = z.guard();
        int cols = pw >= 300 ? 2 : 1, gap = 5, cw = (pw - gap * (cols - 1) - 4) / cols, chh = 54, head = 16;
        int rowsC = (4 + cols - 1) / cols;
        int contentH = (head + rowsC * (chh + gap)) * 2 + 6;
        listScroll = Mth.clamp(listScroll, 0, Math.max(0, contentH - ph));
        ctx.enableScissor(px, py, px + pw, py + ph);
        int y = py - (int) listScroll;
        long wallet = phoneWallet();
        // cashiers
        ctx.drawString(font, Component.translatable("tcgshop.phone.up.cashiers").withStyle(ChatFormatting.BOLD), px + 2, y + 3, 0xFFFFD34D, false);
        if (!anyCashier) ctx.drawString(font, Component.translatable("tcgshop.phone.up.need_cashier"), px + 4 + font.width(Component.translatable("tcgshop.phone.up.cashiers").withStyle(ChatFormatting.BOLD)) + 6, y + 3, 0xFFFF9F6B, false);
        y += head;
        dev.tcgshop.shop.CashierUpgrades.Stat[] cs = dev.tcgshop.shop.CashierUpgrades.Stat.values();
        for (int i = 0; i < cs.length; i++) {
            var s = cs[i];
            int lv = i < cup.length ? cup[i] : 0, max = dev.tcgshop.shop.CashierUpgrades.maxLevel(s);
            String key = s.name().toLowerCase(Locale.ROOT);
            boolean locked = !dev.tcgshop.shop.CashierUpgrades.unlocked(cup.length >= 4 ? cup : new int[4], s, z.tier());
            Component lockText = locked ? Component.translatable("tcgshop.phone.up.haggle_req",
                    Component.translatable("tcgshop.shop.tier." + dev.tcgshop.shop.CashierUpgrades.haggleMinTier().key)) : null;
            int cx = px + (i % cols) * (cw + gap), cy = y + (i / cols) * (chh + gap);
            upgradeCard(ctx, z, cx, cy, cw, chh, py, ph, CASHIER_ICONS[i], Component.translatable("tcgshop.cashup.stat." + key),
                    "tcgshop.cashup.effect." + key + ".", lv, max, lv < max ? dev.tcgshop.shop.CashierUpgrades.cost(s, lv + 1) : -1,
                    anyCashier, locked, lockText, s == dev.tcgshop.shop.CashierUpgrades.Stat.HAGGLE, "cashier_up_" + key, wallet, mouseX, mouseY);
        }
        y += rowsC * (chh + gap) + 6;
        // guards
        ctx.drawString(font, Component.translatable("tcgshop.phone.up.guards").withStyle(ChatFormatting.BOLD), px + 2, y + 3, 0xFF6BC5FF, false);
        if (!anyGuard) ctx.drawString(font, Component.translatable("tcgshop.phone.up.need_guard"), px + 4 + font.width(Component.translatable("tcgshop.phone.up.guards").withStyle(ChatFormatting.BOLD)) + 6, y + 3, 0xFFFF9F6B, false);
        y += head;
        dev.tcgshop.shop.GuardUpgrades.Stat[] gs = dev.tcgshop.shop.GuardUpgrades.Stat.values();
        for (int i = 0; i < gs.length; i++) {
            var s = gs[i];
            int lv = i < gup.length ? gup[i] : 0, max = dev.tcgshop.shop.GuardUpgrades.MAX_LEVEL;
            String key = s.name().toLowerCase(Locale.ROOT);
            int cx = px + (i % cols) * (cw + gap), cy = y + (i / cols) * (chh + gap);
            upgradeCard(ctx, z, cx, cy, cw, chh, py, ph, GUARD_ICONS[i], Component.translatable("tcgshop.guardup.stat." + key),
                    "tcgshop.guardup.effect." + key + ".", lv, max, lv < max ? dev.tcgshop.shop.GuardUpgrades.cost(s, lv + 1) : -1,
                    anyGuard, false, null, false, "guard_up_" + key, wallet, mouseX, mouseY);
        }
        ctx.disableScissor();
        if (contentH > ph) {
            int bh = Math.max(16, ph * ph / contentH), byy = py + (int) ((ph - bh) * (listScroll / Math.max(1, contentH - ph)));
            Ui.rounded(ctx, px + pw - 2, byy, 2, bh, 1, 0x66FFFFFF);
        }
    }

    private void upgradeCard(GuiGraphics ctx, ShopClient.ZoneInfo z, int x, int y, int w, int h, int clipY, int clipH, ItemStack icon, Component name,
                             String effectKey, int lv, int max, long cost, boolean staffed, boolean locked, @org.jetbrains.annotations.Nullable Component lockText,
                             boolean special, String role, long wallet, int mouseX, int mouseY) {
        if (y + h < clipY || y > clipY + clipH) return;
        boolean maxed = lv >= max;
        int accent = special ? 0xFFE0A030 : maxed ? 0xFF43C463 : 0xFF3A6FD8;
        Ui.rounded(ctx, x + 1, y + 2, w, h, 6, 0x40000000);
        Ui.roundedGradient(ctx, x, y, w, h, 6, maxed ? 0xF0213A2C : SURF2, SURF);
        Ui.roundedOutline(ctx, x, y, w, h, 6, special && !maxed ? withAlpha(accent, 0.8f) : LINE);
        Ui.rounded(ctx, x + 3, y + 5, 2, h - 10, 1, accent);
        ctx.renderItem(icon, x + 8, y + 5);
        ctx.drawString(font, name.copy().withStyle(ChatFormatting.BOLD), x + 28, y + 5, 0xFFFFFFFF, false);
        // level: one pip per level
        for (int i = 0; i < max; i++) Ui.rounded(ctx, x + w - 8 - (max - i) * 9, y + 6, 7, 7, 2, i < lv ? accent : 0xFF2A2E40);
        String lvl = Component.translatable("tcgshop.phone.up.level", lv, max).getString();
        ctx.drawString(font, lvl, x + 28, y + 16, 0xFF8A8FA8, false);
        if (special && !maxed) ctx.drawString(font, Component.translatable("tcgshop.phone.up.hard"), x + 32 + font.width(lvl), y + 16, 0xFFE0A030, false);
        int bw = 74, bx = x + w - bw - 6, by = y + h - 19, tw = w - 14 - (maxed ? 0 : bw + 4);
        Component now = Component.translatable("tcgshop.phone.up.now", Component.translatable(effectKey + lv));
        Component next = maxed ? Component.translatable("tcgshop.phone.up.maxed") : Component.translatable("tcgshop.phone.up.next", Component.translatable(effectKey + (lv + 1)));
        ctx.drawString(font, Ui.ellipsis(now.getString(), tw), x + 8, y + 27, 0xFFB8BCCB, false);
        ctx.drawString(font, Ui.ellipsis(next.getString(), tw), x + 8, y + 38, maxed ? 0xFF6BE07B : 0xFF8BE08B, false);
        boolean visible = Ui.in(mouseX, mouseY, x, clipY, w, clipH);
        if (Ui.in(mouseX, mouseY, x, y, w, h) && visible) {
            List<net.minecraft.util.FormattedCharSequence> tip = new ArrayList<>();
            tip.add(name.copy().withStyle(ChatFormatting.BOLD).getVisualOrderText());
            tip.addAll(font.split(now, 220));
            tip.addAll(font.split(next, 220));
            if (lockText != null) tip.addAll(font.split(lockText.copy().withStyle(ChatFormatting.GOLD), 220));
            setTooltipForNextRenderPass(tip);
        }
        if (maxed) return;
        if (locked) {
            Ui.chip(ctx, bx, by, bw, 14, Component.translatable("tcgshop.phone.up.locked"), false, false, 0xFF6A6F88, 0);
            return;
        }
        boolean armed = role.equals(upArmed) && System.nanoTime() / 1e9 - upArmedAt < 3;
        boolean afford = wallet >= cost;
        boolean enabled = staffed && afford;
        Component label = armed ? Component.translatable("tcgshop.phone.up.confirm") : Component.literal("₽ " + dev.tcgshop.client.phone.UpgradesPanel.compact(cost));
        Ui.button(ctx, bx, by, bw, 14, label, mouseX, mouseY, enabled, armed ? 0xFF2E9C5A : accent);
        if (enabled && visible && by >= clipY && by + 14 <= clipY + clipH)
            areas.add(new Area(bx, by, bw, 14, () -> {
                if (role.equals(upArmed) && System.nanoTime() / 1e9 - upArmedAt < 3) {
                    upArmed = null;
                    ClientPlatform.INSTANCE.send(new StaffPayload(z.id(), role, true));
                } else {
                    upArmed = role;
                    upArmedAt = System.nanoTime() / 1e9;
                }
            }));
    }

    private static long phoneWallet() {
        var c = net.minecraft.client.Minecraft.getInstance();
        return c.player != null && dev.tcgshop.economy.CobbleDollarsBridge.isAvailable() ? dev.tcgshop.economy.CobbleDollarsBridge.getBalance(c.player) : Long.MAX_VALUE;
    }

    private void securityTab(GuiGraphics ctx, ShopClient.ZoneInfo z, int px, int py, int pw) {
        int cols = 3, gap = 5, tw = (pw - gap * 2) / cols, th = 34;
        tile(ctx, px, py, tw, th, "tcgshop.phone.s_detained", String.valueOf(z.metric(1)), 0xFF6BE07B);
        tile(ctx, px + tw + gap, py, tw, th, "tcgshop.phone.s_recovered", String.valueOf(z.metric(2)), 0xFF6BE07B);
        tile(ctx, px + (tw + gap) * 2, py, tw, th, "tcgshop.phone.s_stolen", String.valueOf(z.metric(0)), 0xFFFF6B6B);
        tile(ctx, px, py + th + gap, tw, th, "tcgshop.phone.s_detectors", String.valueOf(z.metric(9)), 0xFFFFFFFF);
        tile(ctx, px + tw + gap, py + th + gap, tw * 2 + gap, th, "tcgshop.phone.s_guard", z.guard() ? (z.ext().getInt("guards") > 1 ? z.ext().getInt("guards") + " · " + z.guardName() : z.guardName()) : "—", z.guard() ? 0xFF6BC5FF : 0xFF888888);
        int y = py + (th + gap) * 2 + 4;
        y = paragraph(ctx, Component.translatable(z.guard() ? "tcgshop.phone.security_guard_on" : "tcgshop.phone.security_guard_off"), px + 4, y, pw - 8,
                z.guard() ? 0xFF8BE08B : 0xFFFFC266);
        paragraph(ctx, Component.translatable("tcgshop.phone.security_tip"), px + 4, y + 4, pw - 8, 0xFFAAB0C8);
    }

    private void repTab(GuiGraphics ctx, ShopClient.ZoneInfo z, int px, int py, int pw, int ph) {
        int rep = z.reputation();
        int col = rep >= 80 ? 0xFF43C463 : rep >= 50 ? 0xFFFFD34D : 0xFFE05050;
        int lw = 86;
        Ui.roundGradient(ctx, px, py, lw, ph, SURF2, SURF);
        Ui.outline(ctx, px, py, lw, ph, LINE);
        ring(ctx, px + lw / 2, py + 40, 30, 5, rep / 100f, col);
        Draw.scaledCentered(ctx, Component.literal(String.valueOf(rep)).withStyle(ChatFormatting.BOLD), px + lw / 2f, py + 33, 1.8f, col);
        Ui.text(ctx, Component.translatable("tcgshop.phone.w_rep"), px, py + 76, lw, 10, 0xFFCCCCCC, false);
        if (!z.reviews().isEmpty())
            Ui.text(ctx, Component.literal(String.format(Locale.ROOT, "%.1f ★", z.avgStars())), px, py + 90, lw, 10, Ui.GOLD, true);
        if (z.factors().containsKey("m_target"))
            Ui.text(ctx, Component.translatable("tcgshop.haggle.rep_target", z.factors().get("m_target"), z.factors().getOrDefault("m_ceiling", 100)), px, py + 102, lw, 10, 0xFF9FD8FF, false);
        int x = px + lw + 8, w = pw - lw - 8, y = py;
        if (z.factors().isEmpty()) {
            paragraph(ctx, Component.translatable("tcgshop.phone.rep_wait"), x, y + 4, w, Ui.MUTED);
            return;
        }
        // three independent scores (Aesthetics, Value for money, Security)
        String[] mk = {"m_aesthetics", "m_value", "m_safety"};
        int[] mc = {0xFFE07BD8, 0xFFFFD34D, 0xFF6BC5FF};
        if (z.factors().containsKey("m_value")) {
            int mw = (w - 8) / 3;
            for (int i = 0; i < 3; i++) {
                int v = z.factors().getOrDefault(mk[i], 0), mx0 = x + i * (mw + 4);
                Ui.roundGradient(ctx, mx0, y, mw, 26, SURF2, SURF);
                Ui.text(ctx, Component.translatable("tcgshop.rep." + mk[i]), mx0, y + 3, mw, 10, 0xFFCCD2E6, false);
                Ui.progress(ctx, mx0 + 5, y + 14, mw - 30, 5, v / 100f, mc[i]);
                String vs = String.valueOf(v);
                ctx.drawString(font, vs, mx0 + mw - 5 - font.width(vs), y + 13, mc[i], false);
            }
            y += 32;
        }
        int mid = x + w / 2 + 30;
        for (var e : z.factors().entrySet()) {
            if (e.getKey().startsWith("m_")) continue;
            if (y > py + ph - 24) break;
            int v = e.getValue();
            ctx.drawString(font, Ui.ellipsis(Component.translatable("tcgshop.rep." + e.getKey()).getString(), w / 2 + 20), x, y + 2, 0xFFDDDDDD, false);
            int bw = Math.abs(v) * (x + w - mid - 22) / 15;
            ctx.fill(mid, y + 1, mid + 1, y + 10, 0x55FFFFFF);
            if (v > 0) ctx.fill(mid + 1, y + 3, mid + 1 + bw, y + 8, 0xFF6BE07B);
            else if (v < 0) ctx.fill(mid - bw, y + 3, mid, y + 8, 0xFFFF6B6B);
            String vs = (v > 0 ? "+" : "") + v;
            ctx.drawString(font, vs, x + w - font.width(vs), y + 2, v > 0 ? 0xFF6BE07B : v < 0 ? 0xFFFF6B6B : 0xFFAAAAAA, false);
            y += 12;
        }
        String worst = null;
        int wv = Integer.MAX_VALUE;
        for (var e : z.factors().entrySet()) if (e.getValue() < wv && !e.getKey().equals("buzz") && !e.getKey().startsWith("m_")) { wv = e.getValue(); worst = e.getKey(); }
        if (worst != null) paragraph(ctx, Component.literal("✦ ").append(Component.translatable("tcgshop.rep.tip." + worst)), x, y + 6, w, 0xFFFFD34D);
    }

    private void reviewsTab(GuiGraphics ctx, ShopClient.ZoneInfo z, int px, int py, int pw, int ph) {
        List<ShopZones.Review> rv = z.reviews();
        Ui.roundGradient(ctx, px, py, pw, 26, SURF2, SURF);
        Ui.outline(ctx, px, py, pw, 26, LINE);
        double avg = z.avgStars();
        ctx.drawString(font, Component.literal(stars((int) Math.round(avg))).withStyle(ChatFormatting.BOLD), px + 8, py + 9, Ui.GOLD, true);
        ctx.drawString(font, Component.translatable("tcgshop.phone.reviews_head", String.format(Locale.ROOT, "%.1f", avg), rv.size(), z.regulars()),
                px + 70, py + 9, 0xFFDDDDDD, false);
        int top = py + 30, h = ph - 30, rowH = 36;
        if (rv.isEmpty()) {
            paragraph(ctx, Component.translatable("tcgshop.phone.no_reviews"), px + 8, top + 8, pw - 16, Ui.MUTED);
            return;
        }
        listScroll = Mth.clamp(listScroll, 0, Math.max(0, rv.size() * rowH - h));
        long day = minecraft != null && minecraft.level != null ? minecraft.level.getDayTime() / 24000 : 0;
        ctx.enableScissor(px, top, px + pw, top + h);
        for (int i = 0; i < rv.size(); i++) {
            int y = top + i * rowH - (int) listScroll;
            if (y + rowH < top || y > top + h) continue;
            ShopZones.Review r = rv.get(i);
            int sc = r.stars() >= 4 ? 0xFF43C463 : r.stars() <= 2 ? 0xFFE05050 : 0xFFFFD34D;
            Ui.roundGradient(ctx, px, y, pw, rowH - 3, SURF2, SURF);
            ctx.fill(px + 1, y + 1, px + 3, y + rowH - 4, sc);
            ctx.drawString(font, Component.literal(stars(r.stars())), px + 8, y + 3, Ui.GOLD, false);
            ctx.drawString(font, Component.literal(Ui.ellipsis(r.name(), pw / 2)).withStyle(ChatFormatting.BOLD), px + 64, y + 3, 0xFFFFFFFF, false);
            String when = day - r.day() <= 0 ? Component.translatable("tcgshop.phone.today").getString() : Component.translatable("tcgshop.phone.days_ago", day - r.day()).getString();
            ctx.drawString(font, when, px + pw - 6 - font.width(when), y + 3, 0xFF8A8FA8, false);
            String full = net.minecraft.client.resources.language.I18n.exists(r.textKey())
                    ? Component.translatable(r.textKey()).getString() : Component.translatable("tcgshop.review." + r.key()).getString();
            var wrapped = font.split(Component.literal("«" + full + "»"), pw - 16);
            for (int li = 0; li < Math.min(2, wrapped.size()); li++)
                ctx.drawString(font, wrapped.get(li), px + 8, y + 13 + li * 9, 0xFFC8CCDA, false);
        }
        ctx.disableScissor();
    }

    /**
     * 0.9 random events: active event (what is happening, how to fix it and how long it has left) and what happened today and
     * over the last 7 days. Data: ext.ev from the zone sync (EventManager.writeSync).
     */
    private void eventsTab(GuiGraphics ctx, ShopClient.ZoneInfo z, int px, int py, int pw, int ph) {
        net.minecraft.nbt.CompoundTag ev = z.ext().getCompound("ev");
        net.minecraft.nbt.ListTag act = ev.getList("active", 10), hist = ev.getList("hist", 10);
        long clientNow = minecraft != null && minecraft.level != null ? minecraft.level.getGameTime() : 0;
        long syncedAt = ev.getLong("now");
        int top = py, bottom = py + ph;
        ctx.enableScissor(px, top, px + pw, bottom);
        int y = top - (int) listScroll;
        if (act.isEmpty()) {
            Ui.roundGradient(ctx, px, y, pw, 30, SURF2, SURF);
            Ui.outline(ctx, px, y, pw, 30, LINE);
            paragraph(ctx, Component.translatable("tcgshop.phone.ev.none"), px + 8, y + 6, pw - 16, Ui.MUTED);
            y += 36;
        }
        for (int i = 0; i < act.size(); i++) {
            net.minecraft.nbt.CompoundTag a = act.getCompound(i);
            String k = a.getString("k"), cat = a.getString("cat");
            boolean res = a.getBoolean("res"), fix = a.getBoolean("fix");
            int col = cat.equals("positive") ? 0xFF43C463 : cat.equals("negative") ? 0xFFE05050 : 0xFF6BC5FF;
            var desc = font.split(Component.translatable(k + ".desc"), pw - 18);
            var fixL = fix && !res ? font.split(Component.translatable(k + ".fix"), pw - 26) : java.util.List.<net.minecraft.util.FormattedCharSequence>of();
            int h = 28 + desc.size() * 10 + (fixL.isEmpty() ? 0 : 16 + fixL.size() * 10) + 4;
            Ui.roundGradient(ctx, px, y, pw, h, SURF2, SURF);
            Ui.outline(ctx, px, y, pw, h, LINE);
            ctx.fill(px + 1, y + 1, px + 3, y + h - 1, col);
            ctx.drawString(font, Component.translatable(k + ".name").withStyle(ChatFormatting.BOLD), px + 8, y + 5, col);
            String tier = Component.translatable("tcgshop.event.tier." + a.getString("tier")).getString();
            ctx.drawString(font, tier, px + pw - 6 - font.width(tier), y + 5, 0xFFB983FF, false);
            long left = Math.max(0, a.getLong("left") - Math.max(0, clientNow - syncedAt));
            Component when = res ? Component.translatable("tcgshop.phone.ev.fixed").withStyle(ChatFormatting.GREEN)
                    : Component.translatable("tcgshop.phone.ev.ends_in", left / 1200, String.format(Locale.ROOT, "%02d", (left / 20) % 60));
            ctx.drawString(font, when, px + 8, y + 16, 0xFFAAB0C8, false);
            int ly = y + 28;
            for (var line : desc) { ctx.drawString(font, line, px + 8, ly, 0xFFDDDDDD, false); ly += 10; }
            if (!fixL.isEmpty()) {
                ly += 3;
                Ui.round(ctx, px + 6, ly - 2, pw - 12, 12 + fixL.size() * 10, 0x33FFD34D);
                ctx.drawString(font, Component.translatable("tcgshop.event.fix_prefix").withStyle(ChatFormatting.BOLD), px + 10, ly, 0xFFFFD34D, false);
                ly += 11;
                for (var line : fixL) { ctx.drawString(font, line, px + 10, ly, 0xFFFFE9A8, false); ly += 10; }
            }
            y += h + 5;
        }
        ctx.drawString(font, Component.translatable("tcgshop.phone.ev.history").withStyle(ChatFormatting.BOLD), px + 2, y + 2, 0xFFFFFFFF);
        y += 14;
        if (hist.isEmpty()) {
            paragraph(ctx, Component.translatable("tcgshop.phone.ev.no_history"), px + 6, y, pw - 12, Ui.MUTED);
            y += 12;
        }
        for (int i = 0; i < hist.size(); i++) {
            net.minecraft.nbt.CompoundTag hh = hist.getCompound(i);
            long ago = hh.getLong("ago");
            String cat = hh.getString("cat"), out = hh.getString("o");
            int col = cat.equals("positive") ? 0xFF43C463 : cat.equals("negative") ? 0xFFE05050 : 0xFF6BC5FF;
            Ui.roundGradient(ctx, px, y, pw, 14, SURF2, SURF);
            ctx.fill(px + 1, y + 1, px + 3, y + 13, col);
            String w = ago <= 0 ? Component.translatable("tcgshop.phone.today").getString() : Component.translatable("tcgshop.phone.days_ago", ago).getString();
            ctx.drawString(font, w, px + 7, y + 3, 0xFF8A8FA8, false);
            String nm = Ui.ellipsis(Component.translatable(hh.getString("k") + ".name").getString(), pw - 150);
            ctx.drawString(font, nm, px + 62, y + 3, 0xFFFFFFFF, false);
            String os = Component.translatable("tcgshop.phone.ev.out." + out).getString();
            int oc = out.equals("resolved") || out.equals("done") ? 0xFF8BE08B : out.equals("expired") || out.equals("failed") ? 0xFFFF9F6B : 0xFFAAAAAA;
            ctx.drawString(font, os, px + pw - 6 - font.width(os), y + 3, oc, false);
            y += 16;
        }
        ctx.disableScissor();
        int content = y + (int) listScroll - top;
        listScroll = Mth.clamp(listScroll, 0, Math.max(0, content - ph));
    }

    private static String stars(int n) {
        n = Math.max(0, Math.min(5, n));
        return "★".repeat(n) + "☆".repeat(5 - n);
    }

    // ------------------------------------------------------------------ titles

    private void titlesApp(GuiGraphics ctx, int mouseX, int mouseY) {
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
            Component label = opt.isEmpty() ? Component.translatable("tcgshop.binder.no_title") : Titles.display(opt);
            Ui.chip(ctx, x, y, cw, 16, label, sel, Ui.in(mouseX, mouseY, x, y, cw, 16), 0xFFB983FF, 0);
            areas.add(new Area(x, y, cw, 16, () -> ClientCollection.action(BinderActionPayload.TITLE_SET, opt, 0)));
        }
        if (ClientCollection.titles.isEmpty())
            paragraph(ctx, Component.translatable("tcgshop.binder.no_titles"), sx + 14, y0 + 30, sw - 28, 0xFFCCCCCC);
    }


    // ------------------------------------------------------------------ CobbleGrade

    private void gradeApp(GuiGraphics ctx, int mouseX, int mouseY, double t) {
        AppDef a = def("grade");
        topBar(ctx, a, mouseX, mouseY);
        ctx.drawString(font, Component.translatable("tcgshop.grade.slogan"), sx + sw - 10 - font.width(Component.translatable("tcgshop.grade.slogan")),
                sy + 20, 0xCCFFFFFF, false);
        String[] tabs = {"send", "orders", "odds", "info"};
        String[] labels = new String[tabs.length];
        int[] counts = new int[tabs.length];
        for (int i = 0; i < tabs.length; i++) {
            labels[i] = Component.translatable("tcgshop.grade.tab." + tabs[i]).getString();
            counts[i] = tabs[i].equals("orders") ? dev.tcgshop.client.ClientGrading.orders.size() : tabs[i].equals("send") ? gradePanel.sel.size() : -1;
        }
        int rw = rail(ctx, tabs, labels, counts, gradeTab, a.color, mouseX, mouseY, k -> { gradeTab = k; listScroll = 0; gradePanel.blur(); });
        int px = sx + rw, py = sy + 41, pw = sw - rw - 8, ph = sh - 52;
        switch (gradeTab) {
            case "orders" -> gradeOrders(ctx, px, py, pw, ph, mouseX, mouseY);
            case "odds" -> gradeOdds(ctx, px, py, pw);
            case "info" -> gradeInfo(ctx, px, py, pw, ph);
            default -> gradeSend(ctx, px, py, pw, ph, mouseX, mouseY, t);
        }
    }

    /** «Send cards» tab: everything is drawn and handled by GradeSendPanel (client/screen/grade). */
    private void gradeSend(GuiGraphics ctx, int px, int py, int pw, int ph, int mouseX, int mouseY, double t) {
        gradePanel.render(ctx, px, py, pw, ph, mouseX, mouseY, 0f);
    }

    private void gradeOrders(GuiGraphics ctx, int px, int py, int pw, int ph, int mouseX, int mouseY) {
        List<dev.tcgshop.network.GradePayloads.Order> orders = dev.tcgshop.client.ClientGrading.orders;
        if (orders.isEmpty()) {
            paragraph(ctx, Component.translatable("tcgshop.grade.no_orders"), px + 6, py + 8, pw - 12, Ui.MUTED);
            return;
        }
        long now = dev.tcgshop.client.ClientGrading.now();
        int rowH = 46;
        // 1.1: receive every ready order at once
        int readyN = dev.tcgshop.client.ClientGrading.readyCount();
        if (readyN >= 2) {
            int cards = 0;
            for (var o : orders) if (now >= o.ready()) cards += o.cards().size();
            Ui.button(ctx, px, py, pw, 18, Component.translatable("tcgshop.grade.collect_all", readyN, cards), mouseX, mouseY, true, 0xFF2E9C5A);
            areas.add(new Area(px, py, pw, 18, () -> ClientPlatform.INSTANCE.send(new dev.tcgshop.network.GradePayloads.Action(
                    dev.tcgshop.network.GradePayloads.COLLECT_ALL, 0, List.of(), 0))));
            py += 22;
            ph -= 22;
        }
        listScroll = Mth.clamp(listScroll, 0, Math.max(0, orders.size() * rowH - ph));
        ctx.enableScissor(px, py, px + pw, py + ph);
        for (int i = 0; i < orders.size(); i++) {
            var o = orders.get(i);
            int y = py + i * rowH - (int) listScroll;
            if (y + rowH < py || y > py + ph) continue;
            boolean ready = now >= o.ready();
            Ui.roundGradient(ctx, px, y, pw, rowH - 4, ready ? 0xF0213A2C : SURF2, SURF);
            Ui.outline(ctx, px, y, pw, rowH - 4, ready ? 0xFF43C463 : LINE);
            // thumbnails
            int tw = 16, th = Math.round(tw / Draw.CARD_RATIO);
            for (int k = 0; k < Math.min(5, o.cards().size()); k++) {
                ItemStack st = o.cards().get(k);
                Draw.cardFace(ctx, dev.masterball.api.card.CardInfo.texture(dev.masterball.api.card.CardInfo.id(st)), st, px + 6 + k * 9, y + 9, tw, th, ready ? 1f : 0.55f);
            }
            int tx = px + 6 + Math.min(5, o.cards().size()) * 9 + 14;
            ctx.drawString(font, Component.translatable("tcgshop.grade.order", o.id(), o.cards().size()).withStyle(ChatFormatting.BOLD), tx, y + 5, 0xFFFFFFFF, false);
            ctx.drawString(font, Component.translatable("tcgshop.grade.service." + dev.tcgshop.grading.Grading.Service.of(o.service()).key), tx, y + 16, 0xFFB8D8E8, false);
            float prog = (float) Mth.clamp((now - o.submitted()) / (double) Math.max(1, o.ready() - o.submitted()), 0, 1);
            int barW = pw - (tx - px) - 96;
            Ui.progress(ctx, tx, y + 28, Math.max(30, barW), 6, prog, ready ? 0xFF43C463 : 0xFF1E8FA8);
            if (ready) {
                int bx = px + pw - 84;
                Ui.button(ctx, bx, y + 11, 76, 18, Component.translatable("tcgshop.grade.collect"), mouseX, mouseY, true, 0xFF3C8A4A);
                int id = o.id();
                areas.add(new Area(bx, y + 11, 76, 18, () -> ClientPlatform.INSTANCE.send(new dev.tcgshop.network.GradePayloads.Action(
                        dev.tcgshop.network.GradePayloads.COLLECT, 0, List.of(), id))));
            } else {
                Component left = dev.tcgshop.client.ClientGrading.duration(o.ready() - now);
                ctx.drawString(font, Component.translatable("tcgshop.grade.left", left), px + pw - 8 - font.width(Component.translatable("tcgshop.grade.left", left)),
                        y + 16, 0xFFDDDDDD, false);
            }
        }
        ctx.disableScissor();
    }

    private void gradeOdds(GuiGraphics ctx, int px, int py, int pw) {
        ctx.drawString(font, Component.translatable("tcgshop.grade.odds_title").withStyle(ChatFormatting.BOLD), px + 2, py + 2, 0xFFFFFFFF, false);
        int y = py + 16, labelW = 62, multW = 70, barMax = pw - labelW - multW - 46;
        double maxPct = 0;
        for (var b : dev.tcgshop.grading.Grading.ODDS) maxPct = Math.max(maxPct, b.percent());
        for (var b : dev.tcgshop.grading.Grading.ODDS) {
            int col = dev.tcgshop.grading.Grading.color(b.to());
            String range = b.from() == b.to() ? dev.tcgshop.grading.Grading.format(b.to())
                    : dev.tcgshop.grading.Grading.format(b.from()) + "–" + dev.tcgshop.grading.Grading.format(b.to());
            Ui.round(ctx, px, y, pw, 15, 0x33000000);
            ctx.drawString(font, Component.literal(range).withStyle(ChatFormatting.BOLD), px + 5, y + 4, col, false);
            int bw = (int) Math.round(barMax * b.percent() / maxPct);
            ctx.fillGradient(px + labelW, y + 4, px + labelW + Math.max(2, bw), y + 11, Ui.brighten(col, 30), col);
            String pct = (b.percent() == Math.floor(b.percent()) ? String.valueOf((int) b.percent()) : String.valueOf(b.percent())) + "%";
            ctx.drawString(font, pct, px + labelW + Math.max(2, bw) + 4, y + 4, 0xFFFFFFFF, false);
            String mult = String.format(Locale.ROOT, b.from() == b.to() ? "%.0f%%" : "%.0f–%.0f%%", 100 * dev.tcgshop.grading.Grading.multiplier(b.from()),
                    100 * dev.tcgshop.grading.Grading.multiplier(b.to()));
            ctx.drawString(font, mult, px + pw - 6 - font.width(mult), y + 4, Ui.GOLD, false);
            y += 17;
        }
        paragraph(ctx, Component.translatable("tcgshop.grade.odds_note"), px + 2, y + 4, pw - 4, 0xFFAAB0C8);
    }


    /** Explanation of the Grade system (texts tcgshop.gradeinfo.N; "#" = title) + examples computed with GradeModel. */
    private void gradeInfo(GuiGraphics ctx, int px, int py, int pw, int ph) {
        List<Component> parts = new ArrayList<>();
        for (int n = 0; net.minecraft.client.resources.language.I18n.exists("tcgshop.gradeinfo." + n); n++)
            parts.add(Component.translatable("tcgshop.gradeinfo." + n));
        // live examples: same calculations as the real price
        parts.add(Component.translatable("tcgshop.gradeinfo.ex_pop_head"));
        for (int rank : new int[]{1, 50, 150, 300, 301, 500, 800, 1025})
            parts.add(Component.literal(String.format(Locale.ROOT, "  #%d → Pfinal = %.3f", rank, dev.tcgshop.pricing.GradeModel.popularity(rank))));
        parts.add(Component.translatable("tcgshop.gradeinfo.ex_mult_head"));
        double[][] ex = {{1, 0}, {0.6, 0.2}, {0.9, 0.6}, {1, 1}}; // (Q, A)
        for (double[] e : ex) {
            double z = 0.45 * e[1] + 0.55 * e[0], m10 = Math.min(100, 1 + 99 * Math.pow(z, 4));
            StringBuilder sb = new StringBuilder(String.format(Locale.ROOT, "  Q=%.1f A=%.1f → Z=%.3f, M10=x%.2f:", e[0], e[1], z, m10));
            for (int g : new int[]{10, 30, 49, 50, 80, 90, 95, 99, 100})
                sb.append(String.format(Locale.ROOT, "  %s x%.2f", dev.tcgshop.grading.Grading.format(g), dev.tcgshop.pricing.GradeModel.mult(m10, g)));
            parts.add(Component.literal(sb.toString()));
        }
        int top = py, total = 0;
        for (Component t : parts) total += font.split(t, pw - 8).size() * 10 + 4;
        listScroll = Mth.clamp(listScroll, 0, Math.max(0, total - ph));
        ctx.enableScissor(px, py, px + pw, py + ph);
        int y = top - (int) listScroll;
        for (Component t : parts) {
            String raw = t.getString();
            boolean head = raw.startsWith("#");
            Component shown = head ? Component.literal(raw.substring(1).trim()).withStyle(ChatFormatting.BOLD) : t;
            for (var line : font.split(shown, pw - 8)) {
                ctx.drawString(font, line, px + 4, y, head ? Ui.GOLD : 0xFFD8DCEA, false);
                y += 10;
            }
            y += 4;
        }
        ctx.disableScissor();
    }

    // ------------------------------------------------------------------ TCG League (real TCG)

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

    private void leagueApp(GuiGraphics ctx, int mouseX, int mouseY, double t) {
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
            labels[i] = Component.translatable("tcgshop.league.tab." + tabs[i]).getString();
            counts[i] = tabs[i].equals("deck") ? deckEdit.size() : -1;
        }
        int rw = rail(ctx, tabs, labels, counts, leagueTab, a.color, mouseX, mouseY, k -> { leagueTab = k; listScroll = 0; deckScroll = 0; });
        int px = sx + rw, py = sy + 41, pw = sw - rw - 8, ph = sh - 52;
        if (L == null) {
            paragraph(ctx, Component.translatable("tcgshop.league.loading"), px + 6, py + 8, pw - 12, Ui.MUTED);
            return;
        }
        switch (leagueTab) {
            case "deck" -> deckPanel.render(ctx, L, px, py, pw, ph, mouseX, mouseY, sx, sy, sw, sh);
            case "rules" -> paragraph(ctx, Component.translatable("tcgshop.ptcg.rules"), px + 4, py + 4, pw - 8, 0xFFDDE3F0);
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

    private void leagueRank(GuiGraphics ctx, com.google.gson.JsonObject L, int px, int py, int pw, int ph) {
        int rating = gi(L, "rating"), rank = rankOf(rating), col = RANK_COLORS[rank];
        int lw = 104;
        Ui.roundGradient(ctx, px, py, lw, ph, SURF2, SURF);
        Ui.outline(ctx, px, py, lw, ph, LINE);
        int cx = px + lw / 2, cy = py + 34;
        StylePainter.circle(ctx, cx, cy, 24, Ui.darken(col, 60));
        StylePainter.circle(ctx, cx, cy, 21, col);
        StylePainter.circle(ctx, cx, cy, 14, Ui.darken(col, 30));
        Draw.scaledCentered(ctx, Component.literal(String.valueOf(rank + 1)).withStyle(ChatFormatting.BOLD), cx, cy - 6, 1.6f, 0xFFFFFFFF);
        Ui.text(ctx, Component.translatable("tcgshop.league.rank." + rank).withStyle(ChatFormatting.BOLD), px, py + 62, lw, 10, col, true);
        Ui.text(ctx, Component.literal(rating + " pts"), px, py + 74, lw, 10, 0xFFFFFFFF, false);
        int w = gi(L, "wins"), l = gi(L, "losses");
        Ui.text(ctx, Component.translatable("tcgshop.league.wl", w, l), px, py + 90, lw, 10, 0xFFCCCCCC, false);
        Ui.text(ctx, Component.translatable("tcgshop.league.winrate", w + l == 0 ? 0 : Math.round(w * 100f / (w + l))), px, py + 101, lw, 10, 0xFFCCCCCC, false);
        Ui.text(ctx, Component.translatable("tcgshop.league.ai_wins", gi(L, "aiWins")), px, py + 112, lw, 10, 0xFF8BE0FF, false);
        int[] at = dev.tcgshop.duel.League.RANK_AT;
        if (rank + 1 < at.length) {
            Ui.progress(ctx, px + 8, py + 126, lw - 16, 5, (rating - at[rank]) / (float) (at[rank + 1] - at[rank]), col);
            Ui.text(ctx, Component.translatable("tcgshop.league.next", at[rank + 1] - rating), px, py + 133, lw, 10, 0xFF9AA0B8, false);
        }
        int x = px + lw + 8, tw = pw - lw - 8;
        ctx.drawString(font, Component.translatable("tcgshop.league.top").withStyle(ChatFormatting.BOLD), x, py + 2, 0xFFFFFFFF, false);
        var top = L.getAsJsonArray("top");
        if (top.isEmpty()) paragraph(ctx, Component.translatable("tcgshop.league.no_top"), x, py + 16, tw, Ui.MUTED);
        for (int i = 0; i < top.size(); i++) {
            var r = top.get(i).getAsJsonObject();
            int y = py + 14 + i * 15;
            if (y > py + ph - 34) break;
            Ui.round(ctx, x, y, tw, 13, i % 2 == 0 ? 0x33000000 : 0x1A000000);
            ctx.drawString(font, "#" + (i + 1), x + 4, y + 3, i < 3 ? 0xFFFFD34D : 0xFFAAAAAA, false);
            ctx.drawString(font, Ui.ellipsis(gs(r, "name"), tw - 110), x + 24, y + 3, 0xFFFFFFFF, false);
            String st = gi(r, "rating") + "  " + gi(r, "wins") + "-" + gi(r, "losses");
            ctx.drawString(font, st, x + tw - 4 - font.width(st), y + 3, RANK_COLORS[rankOf(gi(r, "rating"))], false);
        }
        paragraph(ctx, Component.translatable("tcgshop.league.how"), x, py + ph - 30, tw, 0xFF9AA0B8);
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

    /** Real card image (cobbletcg), or a colored card with the name if there is no image. */
    private void deckCard(GuiGraphics ctx, String f, com.google.gson.JsonObject info, int x, int y, int w, int h, float alpha) {
        ItemStack st = dev.tcgshop.client.ClientPtcg.stack(f);
        ctx.fill(x, y + 1, x + w + 1, y + h + 2, Draw.withAlpha(0xFF000000, 0.35f * alpha));
        if (!st.isEmpty()) {
            Draw.cardFace(ctx, dev.masterball.api.card.CardInfo.texture(dev.masterball.api.card.CardInfo.id(st)), st, x, y, w, h, alpha);
            return;
        }
        int stype = gi(info, "st");
        int col = stype == 1 ? 0xFFE0A040 : stype == 2 ? 0xFF6BC5FF : 0xFFF2CC2E;
        if (info != null && info.has("type")) col = dev.tcgshop.client.ClientPtcg.typeColor(gi(info, "type"));
        ctx.fillGradient(x, y, x + w, y + h, Draw.withAlpha(Ui.brighten(col, 50), alpha), Draw.withAlpha(Ui.darken(col, 30), alpha));
        Ui.outline(ctx, x, y, w, h, Draw.withAlpha(0xFFF4E7A8, alpha));
        String n = info == null ? f : gs(info, "n");
        var m = ctx.pose();
        m.pushPose();
        float sc = Math.min(0.55f, (w - 3f) / Math.max(1, font.width(n)));
        m.translate(x + 1.5f, y + 2, 0);
        m.scale(Math.max(0.3f, sc), Math.max(0.3f, sc), 1);
        ctx.drawString(font, n, 0, 0, Draw.withAlpha(0xFF1A2236, alpha), false);
        m.popPose();
    }

    private static boolean deckFilterOk(int filter, com.google.gson.JsonObject e, boolean energy) {
        int st = energy ? 3 : gi(e, "st");
        return filter == 0 || (filter == 1 && st == 1) || (filter == 2 && st == 2) || (filter == 3 && st == 3);
    }

    private void leagueDeck(GuiGraphics ctx, com.google.gson.JsonObject L, int px, int py, int pw, int ph, int mouseX, int mouseY) {
        String valid = gs(L, "valid");
        String msg = gs(L, "msg");
        boolean checked = deckDirty && L.has("check") && L.get("check").getAsBoolean();
        String shown = !deckDirty || checked ? valid : "";
        // ---- header: counter with bar, status and buttons
        int n = deckEdit.size();
        int col = n == 60 ? 0xFF6BE07B : n > 60 ? 0xFFFF6B6B : 0xFFFFC233;
        ctx.drawString(font, Component.literal(n + "/60").withStyle(ChatFormatting.BOLD), px + 2, py + 1, col, true);
        Ui.progress(ctx, px + 36, py + 3, 60, 5, Math.min(1f, n / 60f), col);
        String status;
        int scol;
        if (!shown.isEmpty() && !shown.equals("EMPTY")) { status = "✖ " + deckErr(shown); scol = 0xFFFF8A80; }
        else if (checked || (!deckDirty && valid.isEmpty() && n == 60)) { status = "✔ " + Component.translatable("tcgshop.league.play.deck_ok").getString(); scol = 0xFF6BE07B; }
        else if (!deckDirty && msg.equals("SAVED")) { status = Component.translatable("tcgshop.league.play.saved").getString(); scol = 0xFF6BE07B; }
        else if (deckDirty) { status = Component.translatable("tcgshop.league.deck.unsaved").getString(); scol = 0xFFFFC233; }
        else { status = ""; scol = 0; }
        int bx = px + pw - 4;
        String[][] btns = {{"save", "tcgshop.league.save"}, {"check", "tcgshop.league.check"}, {"clear", "tcgshop.league.deck.clear"}, {"auto", "tcgshop.league.auto"}};
        for (String[] b : btns) {
            Component label = Component.translatable(b[1]);
            int w = font.width(label) + 10;
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
        if (!status.isEmpty()) ctx.drawString(font, Ui.ellipsis(status, bx - px - 104), px + 102, py + 3, scol, false);
        int top = py + 17, h = ph - 17;
        int colW = Math.round((pw - 6) * 0.6f), dw = pw - 6 - colW, dx = px + colW + 6;
        String hoverF = null;
        com.google.gson.JsonObject hoverInfo = null;
        // ---- left: your collection as cards (click = add)
        Ui.roundGradient(ctx, px, top, colW, h, SURF2, SURF);
        Ui.outline(ctx, px, top, colW, h, LINE);
        int fx = px + 4;
        for (int k = 0; k < DECK_FILTERS.length; k++) {
            Component label = Component.translatable("tcgshop.league.deck.filter." + DECK_FILTERS[k]);
            int w = Ui.chipWidth(label);
            boolean hov = Ui.in(mouseX, mouseY, fx, top + 3, w, 12);
            Ui.chip(ctx, fx, top + 3, w, 12, label, deckFilter == k, hov, 0xFF4FB4FF, 0);
            int kk = k;
            areas.add(new Area(fx, top + 3, w, 12, () -> { deckFilter = kk; listScroll = 0; }));
            fx += w + 3;
        }
        // list of what can be added: basic energies (free) + your cards
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
        listScroll = Mth.clamp(listScroll, 0, Math.max(0, rows * rowH - gh + 4));
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
                var mm = ctx.pose();
                mm.pushPose();
                mm.translate(x + 1, y + 1, 0);
                mm.scale(0.5f, 0.5f, 1);
                ctx.drawString(font, "CG", 0, 0, 0xFF1A2236, false);
                mm.popPose();
            }
            if (inDeck > 0) Ui.badge(ctx, x + cw - 2, y - 2, inDeck);
            String cnt = energy ? "∞" : inDeck + "/" + max;
            var mm = ctx.pose();
            mm.pushPose();
            mm.translate(x + cw / 2f - font.width(cnt) * 0.3f, y + chh + 1.5f, 0);
            mm.scale(0.6f, 0.6f, 1);
            ctx.drawString(font, cnt, 0, 0, can ? 0xFFFFD34D : 0xFF777788, false);
            mm.popPose();
            if (can && y >= gy) areas.add(new Area(x, y, cw, chh, () -> {
                deckEdit.add(f);
                deckDirty = true;
                staleCheck();
                if (minecraft != null) minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(dev.tcgshop.TcgSounds.CARD_SLIDE, 1.3f, 0.4f));
            }));
        }
        ctx.disableScissor();
        if (items.isEmpty()) paragraph(ctx, Component.translatable("tcgshop.league.no_pool"), px + 6, gy + 6, colW - 12, Ui.MUTED);
        // ---- right: the deck as grouped cards (click = remove one)
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
        var mm0 = ctx.pose();
        mm0.pushPose();
        mm0.translate(dx + 4, top + 4, 0);
        mm0.scale(0.75f, 0.75f, 1);
        ctx.drawString(font, Component.translatable("tcgshop.league.deck.split", nP, nT, nE), 0, 0, 0xFFDDE3F0, false);
        mm0.popPose();
        List<String> keys = new ArrayList<>(counts.keySet());
        int dgy = top + 14, dgh = h - 16, dcw = Math.max(20, Math.min(26, (dw - 8) / 4 - 3)), dch = Math.round(dcw / Draw.CARD_RATIO);
        int dcols = Math.max(1, (dw - 6 + 3) / (dcw + 3)), drow = dch + 4;
        int drows = (keys.size() + dcols - 1) / dcols;
        deckScroll = Mth.clamp(deckScroll, 0, Math.max(0, drows * drow - dgh + 4));
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
            int bw = font.width(cs) / 2 + 4;
            ctx.fill(x + dcw - bw, y + dch - 6, x + dcw, y + dch, 0xE0101420);
            var mm = ctx.pose();
            mm.pushPose();
            mm.translate(x + dcw - bw + 2, y + dch - 5, 0);
            mm.scale(0.5f, 0.5f, 1);
            ctx.drawString(font, cs, 0, 0, 0xFFFFD34D, false);
            mm.popPose();
            if (y >= dgy) areas.add(new Area(x, y, dcw, dch, () -> {
                deckEdit.remove(f);
                deckDirty = true;
                staleCheck();
                if (minecraft != null) minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(dev.tcgshop.TcgSounds.CARD_FLIP, 0.9f, 0.4f));
            }));
        }
        ctx.disableScissor();
        if (keys.isEmpty()) paragraph(ctx, Component.translatable("tcgshop.ptcg.deck_empty_hint"), dx + 4, dgy + 4, dw - 8, Ui.MUTED);
        hoverDeckList = Ui.in(mouseX, mouseY, dx, top, dw, h);
        // ---- large preview of the card under the mouse
        if (hoverF != null) {
            int pwid = Math.min(96, sh / 2), phgt = Math.round(pwid / Draw.CARD_RATIO);
            int prx = mouseX < px + pw / 2 ? Math.min(sx + sw - pwid - 6, mouseX + 14) : Math.max(sx + 6, mouseX - 14 - pwid);
            int pry = Mth.clamp(mouseY - phgt / 2, sy + 4, sy + sh - phgt - 4);
            ctx.flush(); // 1.0: flush the list first, so its batched text does not end up on top of the preview
            var m = ctx.pose();
            m.pushPose();
            m.translate(0, 0, 400);
            ctx.fill(prx + 3, pry + 4, prx + pwid + 3, pry + phgt + 4, 0x66000000);
            deckCard(ctx, hoverF, hoverInfo, prx, pry, pwid, phgt, 1f);
            m.popPose();
        }
    }

    private void staleCheck() {
        var L = lg();
        if (L != null) L.remove("check");
    }

    /** Readable reason for an invalid deck ("ACE_SPEC:Name" -> translated text with the name). */
    private static String deckErr(String code) {
        String[] parts = code.split(":", 2);
        return Component.translatable("tcgshop.ptcg.deck_err." + parts[0], parts.length > 1 ? parts[1] : "").getString();
    }

    private static final int[] AI_COLORS = {0xFF5BD68A, 0xFF4FB4FF, 0xFFFF9F43, 0xFFE05AFF};

    private void leaguePlay(GuiGraphics ctx, com.google.gson.JsonObject L, int px, int py, int pw, int ph, int mouseX, int mouseY, double t) {
        boolean queued = L.has("queued") && L.get("queued").getAsBoolean();
        boolean inMatch = L.has("inMatch") && L.get("inMatch").getAsBoolean();
        if ((queued || inMatch) && t - lastLeagueSync > 3) { lastLeagueSync = t; leagueSend("sync", null); }
        String valid = gs(L, "valid");
        boolean hasPool = L.has("pool") && L.getAsJsonArray("pool").size() > 0;
        boolean empty = valid.equals("EMPTY");
        boolean ready = valid.isEmpty() || (empty && hasPool);
        // --- deck card
        int lw = Math.min(150, pw / 3);
        Ui.roundGradient(ctx, px, py, lw, ph, SURF2, SURF);
        Ui.outline(ctx, px, py, lw, ph, LINE);
        int icx = px + lw / 2, icy = py + 28;
        int icol = valid.isEmpty() ? 0xFF4ADE80 : empty && hasPool ? 0xFFFFC233 : 0xFFFF5C7A;
        float pulse = 0.5f + 0.5f * (float) Math.sin(t * 3);
        StylePainter.circle(ctx, icx, icy, 20, Draw.withAlpha(icol, 0.15f + 0.1f * pulse));
        StylePainter.circle(ctx, icx, icy, 15, icol);
        StylePainter.circle(ctx, icx, icy, 12, Ui.darken(icol, 70));
        Draw.scaledCentered(ctx, Component.literal(valid.isEmpty() ? "✔" : empty && hasPool ? "A" : "✖").withStyle(ChatFormatting.BOLD), icx, icy - 6, 1.4f, icol);
        String title = valid.isEmpty() ? "tcgshop.league.play.deck_ok" : empty ? (hasPool ? "tcgshop.league.play.deck_auto" : "tcgshop.league.play.deck_none") : "tcgshop.league.play.deck_bad";
        Ui.text(ctx, Component.translatable(title).withStyle(ChatFormatting.BOLD), px, py + 52, lw, 10, icol, true);
        String detail = valid.isEmpty() ? Component.translatable("tcgshop.league.play.deck_ok_hint", L.getAsJsonArray("deck").size()).getString()
                : empty ? Component.translatable(hasPool ? "tcgshop.league.play.deck_auto_hint" : "tcgshop.league.no_pool").getString() : deckErr(valid);
        paragraph(ctx, Component.literal(detail), px + 6, py + 66, lw - 12, 0xFFC8CEE0);
        Ui.button(ctx, px + 8, py + ph - 38, lw - 16, 14, Component.translatable("tcgshop.league.play.edit_deck"), mouseX, mouseY, true, 0xFF2F6FB0);
        areas.add(new Area(px + 8, py + ph - 38, lw - 16, 14, () -> { leagueTab = "deck"; listScroll = 0; }));
        Ui.button(ctx, px + 8, py + ph - 20, lw - 16, 14, Component.translatable("tcgshop.league.auto"), mouseX, mouseY, hasPool, 0xFF5A4AA0);
        if (hasPool) areas.add(new Area(px + 8, py + ph - 20, lw - 16, 14, () -> { deckDirty = false; leagueSend("auto", deckEdit.size() < 60 ? new ArrayList<>(deckEdit) : null); }));
        int x = px + lw + 8, w = pw - lw - 8, y = py;
        // --- match in progress
        if (inMatch) {
            Ui.roundGradient(ctx, x, y, w, 30, 0xF0203A2A, 0xF0142418);
            Ui.outline(ctx, x, y, w, 30, 0xFF4ADE80);
            ctx.drawString(font, Component.translatable("tcgshop.league.play.in_match").withStyle(ChatFormatting.BOLD), x + 8, y + 11, 0xFF9CF0B8, false);
            Ui.button(ctx, x + w - 98, y + 8, 90, 14, Component.translatable("tcgshop.league.play.resume"), mouseX, mouseY, true, 0xFF1F8A55);
            areas.add(new Area(x + w - 98, y + 8, 90, 14, () -> playMode("casual")));
            y += 36;
        }
        // --- AI level
        ctx.drawString(font, Component.translatable("tcgshop.league.play.vs_ai").withStyle(ChatFormatting.BOLD), x, y + 2, 0xFFFFFFFF, false);
        y += 13;
        com.google.gson.JsonArray levels = L.has("levels") ? L.getAsJsonArray("levels") : new com.google.gson.JsonArray();
        int cw = (w - 3 * 5) / 4;
        for (int k = 0; k < 4; k++) {
            int cx2 = x + k * (cw + 5);
            boolean sel = aiLevel == k, hover = Ui.in(mouseX, mouseY, cx2, y, cw, 34);
            int col = AI_COLORS[k];
            Ui.roundGradient(ctx, cx2, y, cw, 34, sel ? Draw.withAlpha(col, 0.35f) : hover ? 0xF0262B3E : SURF2, sel ? Draw.withAlpha(col, 0.15f) : SURF);
            Ui.outline(ctx, cx2, y, cw, 34, sel ? col : LINE);
            Ui.text(ctx, Component.translatable("tcgshop.ptcg.ui.level." + k).withStyle(ChatFormatting.BOLD), cx2, y + 3, cw, 10, sel ? 0xFFFFFFFF : col, true);
            var lv = k < levels.size() ? levels.get(k).getAsJsonObject() : null;
            Ui.text(ctx, Component.translatable("tcgshop.league.play.ai_rating", gi(lv, "rating")), cx2, y + 14, cw, 9, 0xFFAEB6CC, false);
            boolean locked = lv != null && lv.has("allowed") && !lv.get("allowed").getAsBoolean();
            Ui.text(ctx, locked ? Component.translatable("tcgshop.league.level_locked_short") : Component.literal("$" + gi(lv, "reward")), cx2, y + 23, cw, 9, locked ? 0xFFFF7A7A : 0xFFFFD34D, false);
            int kk = k;
            areas.add(new Area(cx2, y, cw, 34, () -> aiLevel = kk));
        }
        y += 40;
        int half = (w - 6) / 2;
        boolean canPlay = ready && !inMatch && !queued;
        modeCard(ctx, x, y, half, 46, "casual", 0xFF2F8FD0, canPlay, mouseX, mouseY, () -> playMode("casual"));
        var lvSel = aiLevel < levels.size() ? levels.get(aiLevel).getAsJsonObject() : null;
        int lvi = Math.max(0, Math.min(3, aiLevel));
        int gain = lvSel != null && lvSel.has("gain") ? gi(lvSel, "gain") : dev.tcgshop.duel.League.AI_WIN[lvi];
        int loss = lvSel != null && lvSel.has("loss") ? gi(lvSel, "loss") : dev.tcgshop.duel.League.AI_LOSS[lvi];
        boolean lvOk = lvSel == null || !lvSel.has("allowed") || lvSel.get("allowed").getAsBoolean();
        modeCard(ctx, x + half + 6, y, half, 46, "ranked_ai", 0xFFD0A020, canPlay && lvOk, mouseX, mouseY, () -> playMode("ranked_ai"));
        Ui.text(ctx, lvOk ? Component.literal("+" + gain + " / -" + loss + " pts · -$" + dev.tcgshop.ptcg.PtcgDuels.LEVEL_PENALTY[lvi])
                : Component.translatable("tcgshop.league.level_locked_short"), x + half + 6, y + 33, half, 9, lvOk ? 0xFFFFE08A : 0xFFFF7A7A, false);
        y += 52;
        // --- queue against players
        ctx.drawString(font, Component.translatable("tcgshop.league.play.vs_players").withStyle(ChatFormatting.BOLD), x, y + 2, 0xFFFFFFFF, false);
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
            ctx.drawString(font, Component.translatable("tcgshop.league.play.searching"), x + 32, y + qh / 2 - 9, 0xFFBFEFFF, false);
            ctx.drawString(font, Component.translatable("tcgshop.league.play.queue_size", gi(L, "queueSize")), x + 32, y + qh / 2 + 2, 0xFF8C96B4, false);
            Ui.button(ctx, x + w - 78, y + qh / 2 - 7, 70, 14, Component.translatable("tcgshop.league.play.cancel"), mouseX, mouseY, true, 0xFF8A2A3A);
            areas.add(new Area(x + w - 78, y + qh / 2 - 7, 70, 14, () -> playMode("cancel_queue")));
        } else {
            paragraph(ctx, Component.translatable("tcgshop.league.play.queue_hint"), x + 6, y + 4, w - 96, 0xFF8C96B4);
            Ui.button(ctx, x + w - 86, y + qh / 2 - 7, 78, 14, Component.translatable("tcgshop.league.play.find"), mouseX, mouseY, canPlay, 0xFF1F8A55);
            if (canPlay) areas.add(new Area(x + w - 86, y + qh / 2 - 7, 78, 14, () -> playMode("queue")));
        }
    }

    private void modeCard(GuiGraphics ctx, int x, int y, int w, int h, String mode, int accent, boolean enabled, int mouseX, int mouseY, Runnable r) {
        boolean hover = enabled && Ui.in(mouseX, mouseY, x, y, w, h);
        Ui.roundGradient(ctx, x, y, w, h, hover ? Ui.brighten(Ui.darken(accent, 70), 20) : Ui.darken(accent, 80), Ui.darken(accent, 100));
        Ui.outline(ctx, x, y, w, h, hover ? 0xFFFFFFFF : enabled ? accent : LINE);
        ctx.fill(x + 1, y + 1, x + w - 1, y + 3, enabled ? accent : 0xFF3A3F50);
        Ui.text(ctx, Component.translatable("tcgshop.league.play.mode." + mode).withStyle(ChatFormatting.BOLD), x, y + 7, w, 10, enabled ? 0xFFFFFFFF : 0xFF77778A, true);
        Ui.text(ctx, Component.translatable("tcgshop.league.play.mode." + mode + ".sub"), x, y + 19, w, 9, enabled ? 0xFFC8CEE0 : 0xFF66667A, false);
        if (!mode.equals("ranked_ai")) Ui.text(ctx, Component.translatable("tcgshop.league.play.start"), x, y + 32, w, 9, enabled ? accent : 0xFF55556A, false);
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

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (morphing()) return true;
        if (dev.tcgshop.client.phone.PhoneNotices.click(mouseX, mouseY)) { // 1.0: the notice is on top and swallows the click
            long link = dev.tcgshop.client.phone.PhoneNotices.consumeClickedLink();
            if (link >= 0) openMessage(link); // a notice of a new message opens it
            return true;
        }
        if (app == App.MESSAGES && messagesApp.mouseClicked(mouseX, mouseY, button)) return true;
        if (app == App.LEAGUE && leagueHelp.mouseClicked(mouseX, mouseY, button)) return true;
        if (app == App.LEAGUE && leagueTab.equals("deck") && deckPanel.mouseClicked(mouseX, mouseY, button)) return true;
        if (app == App.GRADE && gradeTab.equals("send") && gradePanel.mouseClicked(mouseX, mouseY, button)) return true;
        if ((app == App.MARKET || app == App.PRICES) && search.mouseClicked(mouseX, mouseY, button)) {
            setFocused(search);
            search.setFocused(true);
            return true;
        }
        search.setFocused(false);
        for (int i = areas.size() - 1; i >= 0; i--) {
            Area a = areas.get(i);
            if (Ui.in(mouseX, mouseY, a.x, a.y, a.w, a.h)) {
                App before = app;
                a.action.run();
                if (app == before) uiSound(dev.tcgshop.TcgSounds.PHONE_TAP, 1f); // 1.1: tap (opening an app plays its own sound)
                return true;
            }
        }
        if (app == App.MARKET && market.click(mouseX, mouseY)) return true;
        if (app == App.FURNITURE && furniture.mouseClicked(mouseX, mouseY, button)) return true;
        if (app == App.SELL && sellTab.equals("inv") && quickSell.click(mouseX, mouseY)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (app == App.MESSAGES && messagesApp.charTyped(chr, modifiers)) return true;
        if (app == App.LEAGUE && leagueHelp.isOpen()) return true; // with the help open, typing does not go into the search box underneath
        if (app == App.LEAGUE && leagueTab.equals("deck") && deckPanel.charTyped(chr)) return true;
        if (app == App.FURNITURE && furniture.charTyped(chr, modifiers)) return true;
        if (app == App.GRADE && gradeTab.equals("send") && gradePanel.charTyped(chr, modifiers)) return true;
        return search.isFocused() && search.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (app == App.MESSAGES && messagesApp.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (app == App.LEAGUE && leagueHelp.keyPressed(keyCode)) return true;
        if (app == App.LEAGUE && leagueTab.equals("deck") && deckPanel.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (app == App.FURNITURE && furniture.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (app == App.GRADE && gradeTab.equals("send") && gradePanel.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (search.isFocused() && keyCode != GLFW.GLFW_KEY_ESCAPE) return search.keyPressed(keyCode, scanCode, modifiers) || true;
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && search.isFocused()) {
            search.setFocused(false);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && lockedPrompt != null) {
            lockedPrompt = null;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && app != App.HOME) {
            open(App.HOME);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** Quick sell: hold and drag over several cards to select (or deselect) them all at once. */
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (button == 0 && app == App.GRADE && gradePanel.mouseDragged(mouseX, mouseY)) return true; // CobbleGrade: paint selection
        if (button == 0 && app == App.SELL && sellTab.equals("inv") && quickSell.drag(mouseX, mouseY)) return true;
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        quickSell.release();
        gradePanel.mouseReleased();
        return super.mouseReleased(mouseX, mouseY, button);
    }
}

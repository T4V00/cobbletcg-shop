package dev.tcgshop.client.screen;

import dev.masterball.api.card.CardInfo;
import dev.tcgshop.client.binder.Ui;
import dev.tcgshop.client.render.ShopRenderers;
import dev.tcgshop.network.StoreBuyPayload;
import dev.tcgshop.pricing.PriceService;
import dev.tcgshop.shop.TcgBlocks;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Grid of products for sale (Wholesaler and phone Market): search, choose quantity and buy. */
public class CatalogPanel {
    private final int source;
    private final List<ItemStack> goods;
    private final List<Long> prices;
    /** Phone level required for each product (0.9, sent by the server right before the catalog). */
    private final int[] req;
    private final List<Runnable> clicks = new ArrayList<>();
    private final List<int[]> rects = new ArrayList<>();
    private double scroll;
    private int selected = -1, qty = 1;
    public String filter = "";
    public String category = "all";
    public int sort; // 0 = catalog, 1 = price ascending, 2 = descending
    /** 1.0 (phone Market): 0 = all, 1 = only what your phone tier can buy, 2 = only locked products. */
    public int lockFilter;

    private boolean passesLock(int i) {
        return lockFilter == 0 || (lockFilter == 1) != locked(i);
    }

    public static final String[] CATEGORIES = {"all", "boxes", "packs", "shop", "furniture", "plush", "tools", "special"};

    public static String categoryOf(ItemStack st) {
        if (st.isOf(TcgBlocks.BOOSTER_BOX_ITEM)) return "boxes";
        if (CardInfo.isPack(st)) return "packs";
        if (st.isOf(TcgBlocks.MYSTERY_BOX) || st.isOf(TcgBlocks.ROULETTE_ITEM)) return "special";
        if (dev.tcgshop.shop.ShopServer.isPokedoll(st)) return "plush";
        if (st.getItem() instanceof net.minecraft.item.BlockItem bi && bi.getBlock() instanceof dev.tcgshop.shop.ShopCoreBlock) return "shop";
        if (st.getItem() instanceof net.minecraft.item.BlockItem) return "furniture";
        return "tools";
    }

    /** Category row + sort button. Returns the height used. */
    public int renderFilters(DrawContext ctx, int x, int y, int w, int mouseX, int mouseY) {
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        int cx = x;
        int cy = y;
        for (String c : CATEGORIES) {
            Text label = Text.translatable("tcgshop.cat." + c);
            int cw = Ui.chipWidth(label);
            if (cx + cw > x + w - 40) { cx = x; cy += 15; }
            Ui.chip(ctx, cx, cy, cw, 13, label, category.equals(c), Ui.in(mouseX, mouseY, cx, cy, cw, 13), 0xFFFFD34D, 0);
            rects.add(new int[]{cx, cy, cw, 13});
            clicks.add(() -> { category = c; scroll = 0; selected = -1; });
            cx += cw + 3;
        }
        Text sl = Text.literal(sort == 0 ? "⇅" : sort == 1 ? "₽↑" : "₽↓");
        Ui.chip(ctx, x + w - 34, y, 34, 13, sl, sort != 0, Ui.in(mouseX, mouseY, x + w - 34, y, 34, 13), 0xFF6BC5FF, 0);
        rects.add(new int[]{x + w - 34, y, 34, 13});
        clicks.add(() -> sort = (sort + 1) % 3);
        return cy - y + 16;
    }

    public CatalogPanel(int source, List<ItemStack> goods, List<Long> prices) {
        this.source = source;
        this.goods = goods;
        this.prices = prices;
        this.req = dev.tcgshop.client.phone.PhoneClient.locksFor(source, goods.size());
    }

    /** Is a better phone needed to buy this product? */
    public boolean locked(int i) {
        return i >= 0 && i < req.length && req[i] > dev.tcgshop.client.phone.PhoneClient.tier && dev.tcgshop.client.phone.PhoneClient.enabled;
    }

    /** Keeps search, category, sort and scroll when the catalog refreshes (e.g. after upgrading the phone). */
    public void copyStateFrom(CatalogPanel o) {
        filter = o.filter;
        category = o.category;
        sort = o.sort;
        scroll = o.scroll;
    }

    private static net.minecraft.text.MutableText requires(int tier) {
        return Text.translatable("tcgshop.phoneup.requires", dev.tcgshop.client.phone.PhoneClient.tierName(tier));
    }

    /** Deferred tooltip (drawn outside the phone's clipping). */
    private static void tooltip(List<Text> lines) {
        var screen = MinecraftClient.getInstance().currentScreen;
        if (screen == null) return;
        List<net.minecraft.text.OrderedText> ord = new ArrayList<>();
        for (Text t : lines) ord.add(t.asOrderedText());
        screen.setTooltip(ord, net.minecraft.client.gui.tooltip.HoveredTooltipPositioner.INSTANCE, true);
    }

    private List<Integer> visible() {
        List<Integer> out = new ArrayList<>();
        String q = filter.trim().toLowerCase(Locale.ROOT);
        for (int i = 0; i < goods.size(); i++) {
            ItemStack st = goods.get(i);
            if (!category.equals("all") && !categoryOf(st).equals(category)) continue;
            if (!passesLock(i)) continue;
            if (q.isEmpty() || st.getName().getString().toLowerCase(Locale.ROOT).contains(q)) out.add(i);
        }
        if (sort == 1) out.sort(java.util.Comparator.comparingLong(prices::get));
        else if (sort == 2) out.sort(java.util.Comparator.comparingLong((Integer i) -> prices.get(i)).reversed());
        return out;
    }

    /** Products in a category (for the counters on the phone's rail). */
    public int count(String cat) {
        if (cat.equals("all") && lockFilter == 0) return goods.size();
        int n = 0;
        for (int i = 0; i < goods.size(); i++) if (passesLock(i) && (cat.equals("all") || categoryOf(goods.get(i)).equals(cat))) n++;
        return n;
    }

    public void resetScroll() {
        scroll = 0;
        selected = -1;
    }

    /** Call at the start of each frame (before renderFilters/render). */
    public void beginFrame() {
        clicks.clear();
        rects.clear();
    }

    public void render(DrawContext ctx, int x, int y, int w, int h, int mouseX, int mouseY, double t) {
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        int detailH = selected >= 0 ? 40 : 0;
        int gridH = h - detailH;
        int tileW = w < 220 ? 52 : w < 360 ? 58 : 66, tileH = w < 220 ? 72 : w < 360 ? 78 : 88, gap = w < 220 ? 4 : 6;
        int cols = Math.max(1, (w + gap) / (tileW + gap));
        List<Integer> vis = visible();
        int rows = (vis.size() + cols - 1) / cols;
        scroll = MathHelper.clamp(scroll, 0, Math.max(0, rows * (tileH + gap) - gridH));
        int gx = x + (w - (cols * (tileW + gap) - gap)) / 2;
        ctx.enableScissor(x, y, x + w, y + gridH);
        for (int k = 0; k < vis.size(); k++) {
            int i = vis.get(k);
            int tx = gx + (k % cols) * (tileW + gap), ty = y + (k / cols) * (tileH + gap) - (int) scroll;
            if (ty + tileH < y || ty > y + gridH) continue;
            ItemStack st = goods.get(i);
            boolean hover = Ui.in(mouseX, mouseY, tx, ty, tileW, tileH) && Ui.in(mouseX, mouseY, x, y, w, gridH);
            boolean sel = i == selected, lock = locked(i);
            Ui.roundGradient(ctx, tx, ty, tileW, tileH, sel ? 0xFF2B4A7A : hover ? 0xFF2E3348 : 0xFF1E2232, sel ? 0xFF1A2E50 : 0xFF161925);
            Ui.outline(ctx, tx, ty, tileW, tileH, sel ? Ui.GOLD : hover ? 0xFFFFFFFF : 0xFF2B3045);
            drawProduct(ctx, st, tx + 6, ty + 4, tileW - 12, tileH - 30);
            if (lock) { // product for a higher phone level: dimmed and with a padlock
                ctx.fill(tx + 1, ty + 1, tx + tileW - 1, ty + tileH - 1, 0x99101218);
                int tc = dev.tcgshop.phone.PhoneTier.of(req[i]).color;
                dev.tcgshop.client.phone.LockIcon.badge(ctx, tx + tileW / 2, ty + (tileH - 30) / 2 + 4, 12, tc);
            }
            Ui.text(ctx, st.getName(), tx + 2, ty + tileH - 24, tileW - 4, 10, lock ? Ui.MUTED : Ui.TEXT, false);
            if (lock) Ui.text(ctx, dev.tcgshop.client.phone.PhoneClient.tierName(req[i]), tx + 2, ty + tileH - 12, tileW - 4, 10,
                    dev.tcgshop.phone.PhoneTier.of(req[i]).color, true);
            else Ui.text(ctx, Text.literal(PriceService.format(prices.get(i))), tx + 2, ty + tileH - 12, tileW - 4, 10, Ui.GOLD, true);
            if (hover) {
                List<Text> tip = new ArrayList<>(List.of(st.getName(), Text.literal(PriceService.format(prices.get(i))).formatted(Formatting.GOLD)));
                if (lock) tip.add(requires(req[i]).formatted(Formatting.RED));
                tooltip(tip);
            }
            final int idx = i;
            if (ty >= y - tileH) { rects.add(new int[]{tx, Math.max(ty, y), tileW, Math.min(tileH, y + gridH - Math.max(ty, y))}); clicks.add(() -> { selected = idx; qty = 1; }); }
        }
        ctx.disableScissor();
        if (vis.isEmpty()) Ui.text(ctx, Text.translatable("tcgshop.store.empty"), x, y + gridH / 2 - 5, w, 10, Ui.MUTED, false);

        if (selected >= 0 && selected < goods.size()) {
            int dy = y + h - detailH + 4;
            Ui.roundGradient(ctx, x, dy, w, detailH - 4, 0xFF262A3C, 0xFF1A1D2A);
            ItemStack st = goods.get(selected);
            long total = prices.get(selected) * qty;
            ctx.drawTextWithShadow(tr, Ui.ellipsis(st.getName().getString(), w - 150), x + 6, dy + 5, Ui.TEXT);
            boolean lockSel = locked(selected);
            if (lockSel) ctx.drawTextWithShadow(tr, requires(req[selected]).formatted(Formatting.RED), x + 6, dy + 18, 0xFFFF7070);
            else ctx.drawTextWithShadow(tr, Text.translatable("tcgshop.store.total", PriceService.format(total)), x + 6, dy + 18, Ui.GOLD);
            int bx = x + w - 140;
            Ui.button(ctx, bx, dy + 8, 16, 16, Text.literal("-"), mouseX, mouseY, qty > 1, 0xFF444A60);
            rects.add(new int[]{bx, dy + 8, 16, 16}); clicks.add(() -> qty = Math.max(1, qty - (net.minecraft.client.gui.screen.Screen.hasShiftDown() ? 10 : 1)));
            Ui.text(ctx, Text.literal("x" + qty), bx + 17, dy + 8, 24, 16, 0xFFFFFFFF, true);
            Ui.button(ctx, bx + 42, dy + 8, 16, 16, Text.literal("+"), mouseX, mouseY, qty < 64, 0xFF444A60);
            rects.add(new int[]{bx + 42, dy + 8, 16, 16}); clicks.add(() -> qty = Math.min(64, qty + (net.minecraft.client.gui.screen.Screen.hasShiftDown() ? 10 : 1)));
            Ui.button(ctx, bx + 64, dy + 6, 72, 20, Text.translatable(lockSel ? "tcgshop.phoneup.locked_btn" : "tcgshop.store.buy"), mouseX, mouseY, !lockSel, 0xFFD39B1A);
            int sel = selected, q = qty;
            if (!lockSel) { rects.add(new int[]{bx + 64, dy + 6, 72, 20}); clicks.add(() -> ClientPlayNetworking.send(new StoreBuyPayload(source, sel, q))); }
        }
    }

    public static void drawProduct(DrawContext ctx, ItemStack st, int x, int y, int w, int h) {
        if (st.isOf(TcgBlocks.BOOSTER_BOX_ITEM)) {
            String set = st.getOrDefault(TcgBlocks.BOX_SET, "mew151");
            int bh = Math.round(w * 0.7f);
            int by = y + (h - bh) / 2;
            ctx.fill(x + 2, by + 3, x + w + 2, by + bh + 3, 0x66000000);
            Draw.texture(ctx, ShopRenderers.packTextureForSet(set), x, by, w, bh, 1f);
            Ui.outline(ctx, x, by, w, bh, 0xFFFFFFFF);
            ctx.drawText(MinecraftClient.getInstance().textRenderer, "x36", x + w - 18, by + bh - 9, 0xFFFFFFFF, true);
        } else if (st.isOf(TcgBlocks.MYSTERY_BOX)) {
            int s = Math.min(w, h) - 6;
            int bx = x + (w - s) / 2, by = y + (h - s) / 2;
            Ui.roundGradient(ctx, bx, by, s, s, 0xFF7B3FB0, 0xFF4A2370);
            ctx.fill(bx + s / 2 - 2, by, bx + s / 2 + 2, by + s, 0xFFF2C230);
            Ui.text(ctx, Text.literal("?").formatted(Formatting.BOLD), bx, by, s, s, 0xFFFFFFFF, true);
        } else if (CardInfo.isPack(st)) {
            float pw = h * Draw.PACK_RATIO;
            Draw.pack(ctx, CardInfo.packTexture(CardInfo.id(st)), st, x + (w - pw) / 2, y, pw, h, 1f);
        } else {
            var m = ctx.getMatrices();
            m.push();
            m.translate(x + w / 2f, y + h / 2f, 0);
            m.scale(2.5f, 2.5f, 1);
            ctx.drawItem(st, -8, -8);
            m.pop();
        }
    }

    public boolean click(double mx, double my) {
        for (int i = rects.size() - 1; i >= 0; i--) {
            int[] r = rects.get(i);
            if (Ui.in(mx, my, r[0], r[1], r[2], r[3])) {
                clicks.get(i).run();
                return true;
            }
        }
        return false;
    }

    public void scroll(double v) {
        scroll -= v * 30;
    }
}

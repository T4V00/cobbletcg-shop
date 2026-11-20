package dev.tcgshop.client.screen;

import dev.tcgshop.card.CardInfo;
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

/** Rejilla de productos a la venta (Mayorista y Mercado del teléfono): buscar, elegir cantidad y comprar. */
public class CatalogPanel {
    private final int source;
    private final List<ItemStack> goods;
    private final List<Long> prices;
    private final List<Runnable> clicks = new ArrayList<>();
    private final List<int[]> rects = new ArrayList<>();
    private double scroll;
    private int selected = -1, qty = 1;
    public String filter = "";
    public String category = "all";
    public int sort; // 0 = catálogo, 1 = precio ascendente, 2 = descendente

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

    /** Fila de categorías + botón de orden. Devuelve el alto usado. */
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
    }

    private List<Integer> visible() {
        List<Integer> out = new ArrayList<>();
        String q = filter.trim().toLowerCase(Locale.ROOT);
        for (int i = 0; i < goods.size(); i++) {
            ItemStack st = goods.get(i);
            if (!category.equals("all") && !categoryOf(st).equals(category)) continue;
            if (q.isEmpty() || st.getName().getString().toLowerCase(Locale.ROOT).contains(q)) out.add(i);
        }
        if (sort == 1) out.sort(java.util.Comparator.comparingLong(prices::get));
        else if (sort == 2) out.sort(java.util.Comparator.comparingLong((Integer i) -> prices.get(i)).reversed());
        return out;
    }

    /** Productos de una categoría (para los contadores del carril del teléfono). */
    public int count(String cat) {
        if (cat.equals("all")) return goods.size();
        int n = 0;
        for (ItemStack st : goods) if (categoryOf(st).equals(cat)) n++;
        return n;
    }

    public void resetScroll() {
        scroll = 0;
        selected = -1;
    }

    /** Llamar al principio de cada fotograma (antes de renderFilters/render). */
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
            boolean sel = i == selected;
            Ui.roundGradient(ctx, tx, ty, tileW, tileH, sel ? 0xFF2B4A7A : hover ? 0xFF2E3348 : 0xFF1E2232, sel ? 0xFF1A2E50 : 0xFF161925);
            Ui.outline(ctx, tx, ty, tileW, tileH, sel ? Ui.GOLD : hover ? 0xFFFFFFFF : 0xFF2B3045);
            drawProduct(ctx, st, tx + 6, ty + 4, tileW - 12, tileH - 30);
            Ui.text(ctx, st.getName(), tx + 2, ty + tileH - 24, tileW - 4, 10, Ui.TEXT, false);
            Ui.text(ctx, Text.literal(PriceService.format(prices.get(i))), tx + 2, ty + tileH - 12, tileW - 4, 10, Ui.GOLD, true);
            if (hover) ctx.drawTooltip(tr, List.of(st.getName(), Text.literal(PriceService.format(prices.get(i)))), mouseX, mouseY);
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
            ctx.drawTextWithShadow(tr, Text.translatable("tcgshop.store.total", PriceService.format(total)), x + 6, dy + 18, Ui.GOLD);
            int bx = x + w - 140;
            Ui.button(ctx, bx, dy + 8, 16, 16, Text.literal("-"), mouseX, mouseY, qty > 1, 0xFF444A60);
            rects.add(new int[]{bx, dy + 8, 16, 16}); clicks.add(() -> qty = Math.max(1, qty - (net.minecraft.client.gui.screen.Screen.hasShiftDown() ? 10 : 1)));
            Ui.text(ctx, Text.literal("x" + qty), bx + 17, dy + 8, 24, 16, 0xFFFFFFFF, true);
            Ui.button(ctx, bx + 42, dy + 8, 16, 16, Text.literal("+"), mouseX, mouseY, qty < 64, 0xFF444A60);
            rects.add(new int[]{bx + 42, dy + 8, 16, 16}); clicks.add(() -> qty = Math.min(64, qty + (net.minecraft.client.gui.screen.Screen.hasShiftDown() ? 10 : 1)));
            Ui.button(ctx, bx + 64, dy + 6, 72, 20, Text.translatable("tcgshop.store.buy"), mouseX, mouseY, true, 0xFFD39B1A);
            int sel = selected, q = qty;
            rects.add(new int[]{bx + 64, dy + 6, 72, 20}); clicks.add(() -> ClientPlayNetworking.send(new StoreBuyPayload(source, sel, q)));
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

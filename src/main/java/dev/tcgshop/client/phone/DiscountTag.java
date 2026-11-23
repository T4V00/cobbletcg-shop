package dev.tcgshop.client.phone;

import dev.tcgshop.client.binder.Ui;
import dev.tcgshop.pricing.PriceService;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

/**
 * 1.0 store discounts on the client: the «-30%» tag and the old price of discounted products (phone Market and the
 * Wholesaler's stall). The percentage comes from {@link dev.tcgshop.shop.Discounts#percent} (synced by its owner).
 * <p>
 * The prices the server sends in the catalog (StoreOpenPayload) are the ones it charges, so they already include the
 * discount: the old price shown struck through is worked out back from it.
 */
public final class DiscountTag {
    private DiscountTag() {}

    private static final int RED = 0xFFE53935, RED_DARK = 0xFF9E1B1B;

    /** Discount (0-95) of that product, 0 if none. */
    public static int percent(ItemStack st) {
        if (st == null || st.isEmpty()) return 0;
        if (st.isOf(dev.tcgshop.shop.TcgBlocks.BOOSTER_BOX_ITEM)) return 0; // ShopServer.storePrice never discounts boxes
        try {
            return Math.max(0, Math.min(95, dev.tcgshop.shop.Discounts.percent(st.getItem())));
        } catch (RuntimeException e) {
            return 0;
        }
    }

    /** Price before the discount, from the discounted one the server sent. */
    public static long original(long price, int pct) {
        if (pct <= 0 || pct >= 100) return price;
        return Math.round(price * 100.0 / (100 - pct));
    }

    /** Red ribbon «-30%» with its top-left corner at (x, y). Returns its width. */
    public static int ribbon(DrawContext ctx, int x, int y, int pct) {
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        String s = "-" + pct + "%";
        int w = tr.getWidth(s) + 6;
        ctx.fill(x + 1, y + 1, x + w + 1, y + 11, 0x66000000);
        ctx.fillGradient(x, y, x + w, y + 10, RED, RED_DARK);
        ctx.fill(x, y, x + w, y + 1, 0x66FFFFFF);
        ctx.fill(x + w, y + 2, x + w + 2, y + 8, RED_DARK); // little notch
        ctx.drawText(tr, Text.literal(s).formatted(Formatting.BOLD), x + 3, y + 1, 0xFFFFFFFF, false);
        return w + 2;
    }

    /**
     * Decorates one catalog tile (call it after the tile's product is drawn): the ribbon in its top-left corner and the
     * old price struck through just above the price line. Does nothing for products without a discount.
     */
    public static void tile(DrawContext ctx, ItemStack st, int tx, int ty, int tileW, int tileH, long price) {
        int pct = percent(st);
        if (pct <= 0) return;
        ribbon(ctx, tx + 2, ty + 2, pct);
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        String old = PriceService.format(original(price, pct));
        int w = tr.getWidth(old), ox = tx + (tileW - w) / 2, oy = ty + tileH - 34;
        ctx.drawText(tr, old, ox, oy, 0xFF9A9AAE, false);
        ctx.fill(ox - 1, oy + 3, ox + w + 1, oy + 4, 0xFFE57373);
    }

    /**
     * «On sale» strip above a catalog: every discounted product with its tag, new price and old price. Hover = tooltip.
     * Returns the height used (0 if nothing is discounted, so the caller does not reserve any room).
     */
    public static int strip(DrawContext ctx, int x, int y, int w, List<ItemStack> goods, List<Long> prices, int mouseX, int mouseY) {
        if (goods == null || prices == null) return 0;
        List<Integer> sale = new ArrayList<>();
        int best = 0;
        for (int i = 0; i < Math.min(goods.size(), prices.size()); i++) {
            int p = percent(goods.get(i));
            if (p > 0) {
                sale.add(i);
                best = Math.max(best, p);
            }
        }
        if (sale.isEmpty()) return 0;
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        int h = 22;
        Ui.roundGradient(ctx, x, y, w, h, 0xFF4A1A1E, 0xFF2A0E12);
        Ui.outline(ctx, x, y, w, h, 0xFFB33A3A);
        Text head = Text.translatable("tcgshop.store.sale", sale.size(), best).formatted(Formatting.BOLD);
        int hx = x + 6;
        ctx.drawText(tr, head, hx, y + 7, 0xFFFFD0D0, false);
        int cx = hx + tr.getWidth(head) + 8;
        for (int i : sale) {
            ItemStack st = goods.get(i);
            int pct = percent(st);
            String now = PriceService.format(prices.get(i));
            int cw = 18 + tr.getWidth(now) + 3 + tr.getWidth("-" + pct + "%") + 6;
            if (cx + cw > x + w - 4) {
                ctx.drawText(tr, "…", cx, y + 7, 0xFFFFD0D0, false);
                break;
            }
            boolean hover = Ui.in(mouseX, mouseY, cx, y + 2, cw, h - 4);
            if (hover) Ui.round(ctx, cx - 2, y + 2, cw, h - 4, 0x33FFFFFF);
            var m = ctx.getMatrices();
            m.push();
            m.translate(cx, y + 3, 0);
            ctx.drawItem(st, 0, 0);
            m.pop();
            ctx.drawText(tr, now, cx + 18, y + 7, Ui.GOLD, false);
            ribbonSmall(ctx, cx + 18 + tr.getWidth(now) + 3, y + 6, pct);
            if (hover) {
                var screen = MinecraftClient.getInstance().currentScreen;
                if (screen != null) screen.setTooltip(List.of(st.getName().asOrderedText(),
                                Text.translatable("tcgshop.store.sale_tip", PriceService.format(original(prices.get(i), pct)), now, pct)
                                        .formatted(Formatting.GOLD).asOrderedText()),
                        net.minecraft.client.gui.tooltip.HoveredTooltipPositioner.INSTANCE, true);
            }
            cx += cw + 4;
        }
        return h + 3;
    }

    private static void ribbonSmall(DrawContext ctx, int x, int y, int pct) {
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        String s = "-" + pct + "%";
        int w = tr.getWidth(s) + 4;
        ctx.fill(x, y - 1, x + w, y + 9, RED);
        ctx.drawText(tr, s, x + 2, y, 0xFFFFFFFF, false);
    }
}

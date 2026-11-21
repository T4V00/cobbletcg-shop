package dev.tcgshop.client.screen;

import dev.tcgshop.card.CardInfo;
import dev.tcgshop.card.Rarity;
import dev.tcgshop.client.binder.Ui;
import dev.tcgshop.network.MarketPayloads;
import dev.tcgshop.pricing.PriceService;
import dev.tcgshop.shop.CardSales;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;

import static dev.tcgshop.client.screen.Draw.withAlpha;

/**
 * Puesto del Coleccionista ambulante: a la izquierda sus cartas, a la derecha las tuyas. Elige una de cada lado y,
 * si la tuya vale al menos el 85% de la suya, acepta el cambio. Arriba, la carta tuya que le ha encantado y lo que
 * te paga por ella (más que el mercado).
 */
public class TraderScreen extends Screen {
    private record Area(int x, int y, int w, int h, Runnable action) {}

    private MarketPayloads.TraderOpen data;
    private final CardPickGrid mine = new CardPickGrid();
    private final List<Area> areas = new ArrayList<>();
    private int pick = -1;
    private final double openedAt = System.nanoTime() / 1e9;
    private double swapAt = -10;

    public TraderScreen(MarketPayloads.TraderOpen data) {
        super(Text.translatable("entity.tcgshop.trader"));
        this.data = data;
        mine.multi = false;
        mine.includeBinder = true; // puedes cambiar o vender cartas de tu Binder
        mine.price = CardSales::unitValue;
        mine.highlight = data.wishSlot();
    }

    public int entity() { return data.entity(); }

    public void update(MarketPayloads.TraderOpen p) {
        data = p;
        mine.highlight = p.wishSlot();
        mine.sel.clear();
        swapAt = System.nanoTime() / 1e9;
        if (pick >= p.stock().size()) pick = -1;
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        areas.clear();
        double t = System.nanoTime() / 1e9;
        float in = (float) Draw.easeOutCubic(MathHelper.clamp((t - openedAt) / 0.3, 0, 1));
        ctx.fillGradient(0, 0, width, height, withAlpha(0xFF0B1A16, 0.8f * in), withAlpha(0xFF020605, 0.9f * in));
        int w = Math.min(width - 16, 500), h = Math.min(height - 16, 300), x = (width - w) / 2, y = (height - h) / 2 + Math.round((1 - in) * 16);
        Ui.leather(ctx, x, y, w, h, 0xFF2E6B5A, 0xFF143A30);
        Entity e = client != null && client.world != null ? client.world.getEntityById(data.entity()) : null;
        String who = e != null ? e.getName().getString() : title.getString();
        ctx.drawTextWithShadow(textRenderer, Text.literal(who).formatted(Formatting.BOLD), x + 10, y + 8, Ui.GOLD);
        ctx.drawText(textRenderer, Text.translatable("tcgshop.trader.subtitle"), x + 10, y + 19, 0xFFCDEBDD, false);
        String tl = Text.translatable("tcgshop.trader.trades_left", data.tradesLeft()).getString();
        ctx.drawTextWithShadow(textRenderer, tl, x + w - 10 - textRenderer.getWidth(tl), y + 8, data.tradesLeft() > 0 ? 0xFF8BE08B : 0xFFFF8A8A);

        // ---- sus cartas
        int lw = w * 46 / 100, lx = x + 8, ly = y + 34, lh = h - 84;
        Ui.round(ctx, lx, ly, lw, lh, 0xAA0B1512);
        ctx.drawText(textRenderer, Text.translatable("tcgshop.trader.his").formatted(Formatting.BOLD), lx + 6, ly + 5, 0xFFFFFFFF, false);
        int n = data.stock().size(), cols = 3, rowsN = Math.max(1, (n + cols - 1) / cols);
        int ch = Math.min((lh - 24) / rowsN - 14, Math.round(((lw - 16) / cols - 8) / Draw.CARD_RATIO)), cw = Math.round(ch * Draw.CARD_RATIO);
        int gx0 = lx + (lw - (cols * (cw + 8) - 8)) / 2;
        ItemStack hovered = ItemStack.EMPTY;
        for (int k = 0; k < n; k++) {
            ItemStack st = data.stock().get(k);
            int cx = gx0 + (k % cols) * (cw + 8), cy = ly + 18 + (k / cols) * (ch + 14);
            float ap = (float) Draw.easeOutBack(MathHelper.clamp((t - openedAt - k * 0.05) / 0.3, 0, 1));
            boolean hover = Ui.in(mouseX, mouseY, cx, cy, cw, ch), on = pick == k;
            Rarity r = Rarity.of(st);
            int lift = (hover || on ? 3 : 0) + Math.round((1 - ap) * 12);
            if (on) Draw.glow(ctx, cx + cw / 2f, cy + ch / 2f - lift, cw, ch, Ui.GOLD, 0.45f);
            else if (r.tier >= 2) Draw.glow(ctx, cx + cw / 2f, cy + ch / 2f, cw, ch, r.color, 0.18f);
            ctx.fill(cx + 2, cy + 3, cx + cw + 2, cy + ch + 3, 0x66000000);
            Draw.cardFace(ctx, CardInfo.texture(CardInfo.id(st)), st, cx, cy - lift, cw, ch, Math.min(1, ap));
            if (r.tier > 0 && (hover || on)) Draw.holo(ctx, cx, cy - lift, cw, ch, r.tier, r.color, t, mouseX);
            if (on) Ui.outline(ctx, cx - 1, cy - lift - 1, cw + 2, ch + 2, Ui.GOLD);
            else if (hover) Ui.outline(ctx, cx - 1, cy - lift - 1, cw + 2, ch + 2, 0xFFFFFFFF);
            String pr = PriceService.format(data.prices().get(k));
            Ui.text(ctx, Text.literal(pr), cx - 4, cy + ch + 2, cw + 8, 10, on ? Ui.GOLD : 0xFFE8D9A8, true);
            if (hover) hovered = st;
            int fk = k;
            areas.add(new Area(cx, cy - lift, cw, ch, () -> { pick = pick == fk ? -1 : fk; click(); }));
        }

        // ---- tus cartas
        int rx = lx + lw + 8, rw = x + w - 8 - rx;
        Ui.round(ctx, rx, ly, rw, lh, 0xAA0B1512);
        ctx.drawText(textRenderer, Text.translatable("tcgshop.trader.yours").formatted(Formatting.BOLD), rx + 6, ly + 5, 0xFFFFFFFF, false);
        if (mine.isEmpty()) {
            Ui.text(ctx, Text.translatable("tcgshop.sell.no_cards"), rx, ly + lh / 2 - 5, rw, 10, Ui.MUTED, false);
        } else {
            ItemStack hm = mine.render(ctx, rx + 4, ly + 17, rw - 8, lh - 21, mouseX, mouseY, 30);
            if (!hm.isEmpty()) hovered = hm;
        }

        // ---- barra inferior: comparación y botones
        int by = y + h - 46;
        ItemStack his = pick >= 0 && pick < n ? data.stock().get(pick) : ItemStack.EMPTY;
        int mySlot = mine.sel.isEmpty() ? -1 : mine.sel.iterator().next();
        ItemStack my = mySlot >= 0 && client != null && client.player != null ? CardPickGrid.stackAt(client.player, mySlot) : ItemStack.EMPTY;
        long hv = his.isEmpty() ? 0 : data.prices().get(pick), mv = my.isEmpty() ? 0 : CardSales.unitValue(my);
        Ui.round(ctx, x + 8, by, w - 16, 38, 0xAA0B1512);
        String a = his.isEmpty() ? Text.translatable("tcgshop.trader.pick_his").getString() : Ui.ellipsis(his.getName().getString(), w / 3 - 20);
        String b = my.isEmpty() ? Text.translatable("tcgshop.trader.pick_yours").getString() : Ui.ellipsis(my.getName().getString(), w / 3 - 20);
        ctx.drawText(textRenderer, a, x + 14, by + 5, his.isEmpty() ? Ui.MUTED : 0xFFFFFFFF, false);
        if (!his.isEmpty()) ctx.drawText(textRenderer, PriceService.format(hv), x + 14, by + 16, Ui.GOLD, false);
        ctx.drawText(textRenderer, "⇄", x + w / 3 + 4, by + 10, 0xFFFFFFFF, true);
        ctx.drawText(textRenderer, b, x + w / 3 + 18, by + 5, my.isEmpty() ? Ui.MUTED : 0xFFFFFFFF, false);
        if (!my.isEmpty()) ctx.drawText(textRenderer, PriceService.format(mv), x + w / 3 + 18, by + 16, Ui.GOLD, false);
        boolean both = !his.isEmpty() && !my.isEmpty();
        boolean ok = both && mv >= hv * 0.85 && data.tradesLeft() > 0;
        if (both) { // medidor: tu carta frente a la suya
            float ratio = hv <= 0 ? 2 : mv / (float) hv;
            int mw = w / 3 - 12, mxp = x + 14;
            Ui.progress(ctx, mxp, by + 28, mw * 2 + 10, 4, Math.min(1f, ratio / 1.5f), ok ? (ratio > 1.3f ? 0xFFFFB347 : 0xFF5BD68A) : 0xFFE05050);
            String verdict = Text.translatable(!ok ? "tcgshop.trader.v_low" : ratio > 1.3f ? "tcgshop.trader.v_generous" : "tcgshop.trader.v_fair").getString();
            ctx.drawText(textRenderer, verdict, mxp + mw * 2 + 16, by + 26, ok ? (ratio > 1.3f ? 0xFFFFB347 : 0xFF8BE08B) : 0xFFFF8A8A, false);
        }
        int bw = 96, bx = x + w - bw - 14;
        Ui.button(ctx, bx, by + 9, bw, 20, Text.translatable("tcgshop.trader.trade"), mouseX, mouseY, ok, 0xFF2E9C5A);
        if (ok) {
            int fp = pick, fs = mySlot;
            areas.add(new Area(bx, by + 9, bw, 20, () -> ClientPlayNetworking.send(new MarketPayloads.TraderAction(data.entity(), MarketPayloads.TRADE, fp, fs))));
        }

        // ---- la carta que le interesa
        if (data.wishSlot() >= 0 && client != null && client.player != null) {
            ItemStack ws = CardPickGrid.stackAt(client.player, data.wishSlot());
            if (CardInfo.isCard(ws)) {
                String msg = Text.translatable("tcgshop.trader.wish", ws.getName().getString(), PriceService.format(data.wishPrice())).getString();
                int ww = Math.min(w - 140, textRenderer.getWidth(msg) + 12), wx = x + w - 10 - ww - 80, wy = y + 18;
                float pulse = 0.5f + 0.5f * (float) Math.sin(t * 3);
                Ui.round(ctx, wx, wy - 1, ww, 12, withAlpha(0xFFE0457E, 0.35f + 0.2f * pulse));
                ctx.drawText(textRenderer, Ui.ellipsis(msg, ww - 10), wx + 5, wy + 1, 0xFFFFFFFF, false);
                boolean can = data.tradesLeft() > 0;
                Ui.button(ctx, x + w - 86, wy - 2, 76, 14, Text.translatable("tcgshop.trader.sell_wish"), mouseX, mouseY, can, 0xFFE0457E);
                if (can) areas.add(new Area(x + w - 86, wy - 2, 76, 14, () ->
                        ClientPlayNetworking.send(new MarketPayloads.TraderAction(data.entity(), MarketPayloads.SELL_WISH, -1, data.wishSlot()))));
            }
        }
        double sw = t - swapAt;
        if (sw < 0.5) ctx.fill(x, y, x + w, y + h, withAlpha(0xFFFFFFFF, 0.25f * (float) (1 - sw / 0.5)));
        if (!hovered.isEmpty()) ctx.drawTooltip(textRenderer, List.of(hovered.getName(), Rarity.of(hovered).displayName(),
                Text.literal(PriceService.format(CardSales.unitValue(hovered))).formatted(Formatting.GOLD)), mouseX, mouseY);
    }

    private void click() {
        if (client != null) client.getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.master(
                net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK.value(), 1.2f, 0.4f));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (int i = areas.size() - 1; i >= 0; i--) {
            Area a = areas.get(i);
            if (Ui.in(mouseX, mouseY, a.x, a.y, a.w, a.h)) { a.action.run(); return true; }
        }
        if (mine.click(mouseX, mouseY)) { click(); return true; }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double h, double v) {
        mine.scroll(v);
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (button == 0 && true && mine.drag(mouseX, mouseY)) return true;
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        mine.release();
        return super.mouseReleased(mouseX, mouseY, button);
    }
}

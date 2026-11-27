package dev.tcgshop.client.screen;

import dev.masterball.api.platform.Platform;
import dev.masterball.api.platform.ClientPlatform;
import dev.masterball.api.card.CardInfo;
import dev.masterball.api.card.Rarity;
import dev.tcgshop.client.binder.Ui;
import dev.tcgshop.network.MarketPayloads;
import dev.tcgshop.pricing.PriceService;
import dev.tcgshop.shop.CardSales;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.List;

import static dev.tcgshop.client.screen.Draw.withAlpha;

/**
 * Wandering Collector's stall: his cards on the left, yours on the right. Pick one of his and one or several of yours
 * (1.0: up to TraderEntity.MAX_OFFER) and, if yours add up to at least 85% of his, he accepts the trade. At the top, the
 * card of yours he loves and what he pays you for it (more than market).
 * 1.1: the limits come from your phone tier (TraderOpen.giveMax / takeMax: several of his cards in one deal too); when
 * the offer is a little short, charisma talk buttons show their real chance; your cards can be filtered by price.
 */
public class TraderScreen extends Screen {
    private record Area(int x, int y, int w, int h, Runnable action) {}

    private MarketPayloads.TraderOpen data;
    private final CardPickGrid mine = new CardPickGrid();
    private final List<Area> areas = new ArrayList<>();
    /** 1.1: his cards picked for this deal (up to data.takeMax()). */
    private final java.util.LinkedHashSet<Integer> picks = new java.util.LinkedHashSet<>();
    /** 1.1: price filter of your cards (index in BANDS; 0 = all). */
    private int band;
    private static final long[][] BANDS = {{0, Long.MAX_VALUE}, {0, 999}, {1_000, 9_999}, {10_000, 99_999}, {100_000, Long.MAX_VALUE}};
    private static final String[] BAND_KEYS = {"all", "under_1k", "1k_10k", "10k_100k", "over_100k"};
    private final double openedAt = System.nanoTime() / 1e9;
    private double swapAt = -10;
    /** When the Mega Stone sale button was armed (second click within 3 s confirms). */
    private double megaArmedAt = -10;
    /** 1.0 beta 3: when the Steal button was armed (second click within 3 s steals). */
    private double stealArmedAt = -10;

    public TraderScreen(MarketPayloads.TraderOpen data) {
        super(Component.translatable("entity.tcgshop.trader"));
        this.data = data;
        mine.multi = true; // 1.0: several of your cards for one of his
        mine.includeBinder = true; // you can trade or sell cards from your Binder
        mine.price = CardSales::unitValue;
        mine.highlight = data.wishSlot();
        mine.accept = st -> CardInfo.isCard(st) && inBand(st);
    }

    private boolean inBand(ItemStack st) {
        if (band <= 0) return true;
        long v = CardSales.unitValue(st);
        return v >= BANDS[band][0] && v <= BANDS[band][1];
    }

    public int entity() { return data.entity(); }

    public void update(MarketPayloads.TraderOpen p) {
        data = p;
        mine.highlight = p.wishSlot();
        mine.sel.clear();
        swapAt = System.nanoTime() / 1e9;
        picks.removeIf(i -> i >= p.stock().size());
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        areas.clear();
        double t = System.nanoTime() / 1e9;
        float in = (float) Draw.easeOutCubic(Mth.clamp((t - openedAt) / 0.3, 0, 1));
        ctx.fillGradient(0, 0, width, height, withAlpha(0xFF0B1A16, 0.8f * in), withAlpha(0xFF020605, 0.9f * in));
        int w = Math.min(width - 16, 500), h = Math.min(height - 16, 300), x = (width - w) / 2, y = (height - h) / 2 + Math.round((1 - in) * 16);
        Ui.leather(ctx, x, y, w, h, 0xFF2E6B5A, 0xFF143A30);
        Entity e = minecraft != null && minecraft.level != null ? minecraft.level.getEntity(data.entity()) : null;
        String who = e != null ? e.getName().getString() : title.getString();
        ctx.drawString(font, Component.literal(who).withStyle(ChatFormatting.BOLD), x + 10, y + 8, Ui.GOLD);
        stealButton(ctx, x + 16 + font.width(Component.literal(who).withStyle(ChatFormatting.BOLD)), y + 6, mouseX, mouseY, t); // 1.0 beta 3
        ctx.drawString(font, Component.translatable("tcgshop.trader.subtitle"), x + 10, y + 19, 0xFFCDEBDD, false);
        String tl = Component.translatable("tcgshop.trader.trades_left", data.tradesLeft()).getString();
        ctx.drawString(font, tl, x + w - 10 - font.width(tl), y + 8, data.tradesLeft() > 0 ? 0xFF8BE08B : 0xFFFF8A8A);
        // 1.1: what your phone tier allows in one deal
        String lim = Component.translatable("tcgshop.trader.limits", data.giveMax(), data.takeMax()).getString();
        if (data.wishSlot() < 0) ctx.drawString(font, lim, x + w - 10 - font.width(lim), y + 19, 0xFFB8D8CC, false);

        // ---- his cards
        int lw = w * 46 / 100, lx = x + 8, ly = y + 34, lh = h - 84;
        Ui.round(ctx, lx, ly, lw, lh, 0xAA0B1512);
        ctx.drawString(font, Component.translatable("tcgshop.trader.his").withStyle(ChatFormatting.BOLD), lx + 6, ly + 5, 0xFFFFFFFF, false);
        megaStoneButton(ctx, lx, ly, lw, mouseX, mouseY, t); // 1.0: he also buys Mega Stones
        int n = data.stock().size(), cols = 3, rowsN = Math.max(1, (n + cols - 1) / cols);
        int ch = Math.min((lh - 24) / rowsN - 14, Math.round(((lw - 16) / cols - 8) / Draw.CARD_RATIO)), cw = Math.round(ch * Draw.CARD_RATIO);
        int gx0 = lx + (lw - (cols * (cw + 8) - 8)) / 2;
        ItemStack hovered = ItemStack.EMPTY;
        for (int k = 0; k < n; k++) {
            ItemStack st = data.stock().get(k);
            int cx = gx0 + (k % cols) * (cw + 8), cy = ly + 18 + (k / cols) * (ch + 14);
            float ap = (float) Draw.easeOutBack(Mth.clamp((t - openedAt - k * 0.05) / 0.3, 0, 1));
            boolean hover = Ui.in(mouseX, mouseY, cx, cy, cw, ch), on = picks.contains(k);
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
            Ui.text(ctx, Component.literal(pr), cx - 4, cy + ch + 2, cw + 8, 10, on ? Ui.GOLD : 0xFFE8D9A8, true);
            if (hover) hovered = st;
            int fk = k;
            areas.add(new Area(cx, cy - lift, cw, ch, () -> { // 1.1: several of his cards, up to your tier's limit
                if (!picks.remove(fk)) {
                    if (data.takeMax() <= 1) picks.clear();
                    if (picks.size() < Math.max(1, data.takeMax())) picks.add(fk);
                }
                click();
            }));
        }

        // ---- your cards
        int rx = lx + lw + 8, rw = x + w - 8 - rx;
        Ui.round(ctx, rx, ly, rw, lh, 0xAA0B1512);
        ctx.drawString(font, Component.translatable("tcgshop.trader.yours").withStyle(ChatFormatting.BOLD), rx + 6, ly + 5, 0xFFFFFFFF, false);
        int fy = ly + 16 + bandChips(ctx, rx + 4, ly + 16, rw - 8, mouseX, mouseY); // 1.1: filter by price
        if (mine.isEmpty()) {
            Ui.text(ctx, Component.translatable(band > 0 ? "tcgshop.trader.filter.none" : "tcgshop.sell.no_cards"), rx, ly + lh / 2 - 5, rw, 10, Ui.MUTED, false);
        } else {
            ItemStack hm = mine.render(ctx, rx + 4, fy + 1, rw - 8, ly + lh - 4 - fy - 1, mouseX, mouseY, 30);
            if (!hm.isEmpty()) hovered = hm;
        }

        // ---- bottom bar: comparison and buttons (1.0: one or several of your cards against one of his)
        int by = y + h - 46;
        List<Integer> hisPicks = new ArrayList<>();
        for (int k : picks) if (k >= 0 && k < n) hisPicks.add(k);
        ItemStack his = hisPicks.isEmpty() ? ItemStack.EMPTY : data.stock().get(hisPicks.get(0));
        List<Integer> mySlots = new ArrayList<>();
        ItemStack my = ItemStack.EMPTY;
        long mv = 0;
        if (minecraft != null && minecraft.player != null) for (int s : mine.sel) {
            ItemStack st = CardPickGrid.stackAt(minecraft.player, s);
            if (!CardInfo.isCard(st)) continue;
            if (my.isEmpty()) my = st;
            mySlots.add(s);
            mv += CardSales.unitValue(st); // one card per slot
        }
        long hv = 0;
        for (int k : hisPicks) hv += data.prices().get(k);
        Ui.round(ctx, x + 8, by, w - 16, 38, 0xAA0B1512);
        String a = his.isEmpty() ? Component.translatable("tcgshop.trader.pick_his").getString()
                : hisPicks.size() == 1 ? Ui.ellipsis(his.getHoverName().getString(), w / 3 - 20)
                : Ui.ellipsis(Component.translatable("tcgshop.trader.n_cards", hisPicks.size()).getString(), w / 3 - 20);
        String b = mySlots.isEmpty() ? Component.translatable("tcgshop.trader.pick_yours").getString()
                : mySlots.size() == 1 ? Ui.ellipsis(my.getHoverName().getString(), w / 3 - 20)
                : Ui.ellipsis(Component.translatable("tcgshop.trader.n_cards", mySlots.size()).getString(), w / 3 - 20);
        ctx.drawString(font, a, x + 14, by + 5, his.isEmpty() ? Ui.MUTED : 0xFFFFFFFF, false);
        if (!his.isEmpty()) ctx.drawString(font, PriceService.format(hv), x + 14, by + 16, Ui.GOLD, false);
        ctx.drawString(font, "⇄", x + w / 3 + 4, by + 10, 0xFFFFFFFF, true);
        ctx.drawString(font, b, x + w / 3 + 18, by + 5, mySlots.isEmpty() ? Ui.MUTED : 0xFFFFFFFF, false);
        boolean tooMany = mySlots.size() > data.giveMax();
        if (tooMany) ctx.drawString(font, Ui.ellipsis(Component.translatable("tcgshop.trader.too_many", data.giveMax()).getString(),
                w / 3 - 20), x + w / 3 + 18, by + 16, 0xFFFF8A8A, false);
        else if (!mySlots.isEmpty()) ctx.drawString(font, PriceService.format(mv), x + w / 3 + 18, by + 16, Ui.GOLD, false);
        boolean both = !his.isEmpty() && !mySlots.isEmpty();
        boolean ok = both && !tooMany && mv >= hv * dev.tcgshop.entity.TraderEntity.MIN_RATIO && data.tradesLeft() > 0;
        if (both) { // meter: your cards (sum) versus his
            float ratio = hv <= 0 ? 2 : mv / (float) hv;
            int mw = w / 3 - 12, mxp = x + 14;
            Ui.progress(ctx, mxp, by + 28, mw * 2 + 10, 4, Math.min(1f, ratio / 1.5f), ok ? (ratio > 1.3f ? 0xFFFFB347 : 0xFF5BD68A) : 0xFFE05050);
            boolean enough = mv >= hv * dev.tcgshop.entity.TraderEntity.MIN_RATIO;
            String verdict = Component.translatable(!enough ? "tcgshop.trader.v_low" : ratio > 1.3f ? "tcgshop.trader.v_generous" : "tcgshop.trader.v_fair").getString();
            ctx.drawString(font, verdict, mxp + mw * 2 + 16, by + 26, ok ? (ratio > 1.3f ? 0xFFFFB347 : 0xFF8BE08B) : 0xFFFF8A8A, false);
        }
        int bw = 96, bx = x + w - bw - 14;
        List<Integer> fp = List.copyOf(hisPicks), fs = List.copyOf(mySlots);
        // 1.1: a little short? charisma talk buttons (real chance shown) instead of the Trade button
        double ratio = hv <= 0 ? 2 : mv / (double) hv;
        boolean talkable = both && !tooMany && !ok && data.tradesLeft() > 0 && data.talksLeft() > 0
                && dev.tcgshop.entity.TraderEntity.talkChance(dev.tcgshop.entity.TraderEntity.TALK_PROMISE, data.charisma(), ratio) > 0;
        if (talkable) {
            int tw = 64, tx = x + w - 14 - (tw + 4) * dev.tcgshop.entity.TraderEntity.TALKS + 4;
            String head = Component.translatable("tcgshop.trader.talk.head", data.talksLeft()).getString();
            ctx.drawString(font, head, x + w - 14 - font.width(head), by + 1, 0xFFE0B0FF, false);
            for (int k = 0; k < dev.tcgshop.entity.TraderEntity.TALKS; k++) {
                double tch = dev.tcgshop.entity.TraderEntity.talkChance(k, data.charisma(), ratio);
                int bxk = tx + k * (tw + 4);
                Component lbl = Component.translatable("tcgshop.trader.talk." + dev.tcgshop.entity.TraderEntity.TALK_KEYS[k] + ".btn", Math.round(tch * 100) + "%");
                Ui.button(ctx, bxk, by + 12, tw, 18, Component.literal(Ui.ellipsis(lbl.getString(), tw - 6)), mouseX, mouseY, tch > 0, 0xFF8A4FD0);
                if (Ui.in(mouseX, mouseY, bxk, by + 12, tw, 18)) {
                    List<net.minecraft.util.FormattedCharSequence> tip = new ArrayList<>();
                    tip.add(lbl.copy().withStyle(ChatFormatting.LIGHT_PURPLE).getVisualOrderText());
                    tip.addAll(font.split(Component.translatable("tcgshop.trader.talk." + dev.tcgshop.entity.TraderEntity.TALK_KEYS[k] + ".tip").withStyle(ChatFormatting.GRAY), 200));
                    setTooltipForNextRenderPass(tip, net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner.INSTANCE, true);
                }
                int fk = k;
                if (tch > 0) areas.add(new Area(bxk, by + 12, tw, 18, () ->
                        ClientPlatform.INSTANCE.send(new dev.tcgshop.network.CollectorOfferPayload(data.entity(), fp, fs, fk))));
            }
        } else {
            Ui.button(ctx, bx, by + 9, bw, 20, Component.translatable("tcgshop.trader.trade"), mouseX, mouseY, ok, 0xFF2E9C5A);
            if (ok) areas.add(new Area(bx, by + 9, bw, 20, () -> ClientPlatform.INSTANCE.send(new dev.tcgshop.network.CollectorOfferPayload(data.entity(), fp, fs, -1))));
        }

        // ---- the card he wants
        if (data.wishSlot() >= 0 && minecraft != null && minecraft.player != null) {
            ItemStack ws = CardPickGrid.stackAt(minecraft.player, data.wishSlot());
            if (CardInfo.isCard(ws)) {
                String msg = Component.translatable("tcgshop.trader.wish", ws.getHoverName().getString(), PriceService.format(data.wishPrice())).getString();
                int ww = Math.min(w - 140, font.width(msg) + 12), wx = x + w - 10 - ww - 80, wy = y + 18;
                float pulse = 0.5f + 0.5f * (float) Math.sin(t * 3);
                Ui.round(ctx, wx, wy - 1, ww, 12, withAlpha(0xFFE0457E, 0.35f + 0.2f * pulse));
                ctx.drawString(font, Ui.ellipsis(msg, ww - 10), wx + 5, wy + 1, 0xFFFFFFFF, false);
                boolean can = data.tradesLeft() > 0;
                Ui.button(ctx, x + w - 86, wy - 2, 76, 14, Component.translatable("tcgshop.trader.sell_wish"), mouseX, mouseY, can, 0xFFE0457E);
                if (can) areas.add(new Area(x + w - 86, wy - 2, 76, 14, () ->
                        ClientPlatform.INSTANCE.send(new MarketPayloads.TraderAction(data.entity(), MarketPayloads.SELL_WISH, -1, data.wishSlot()))));
            }
        }
        double sw = t - swapAt;
        if (sw < 0.5) ctx.fill(x, y, x + w, y + h, withAlpha(0xFFFFFFFF, 0.25f * (float) (1 - sw / 0.5)));
        if (!hovered.isEmpty()) ctx.renderComponentTooltip(font, List.of(hovered.getHoverName(), Rarity.of(hovered).displayName(),
                Component.literal(PriceService.format(CardSales.unitValue(hovered))).withStyle(ChatFormatting.GOLD)), mouseX, mouseY);
    }

    /**
     * 1.0: "Sell N Mega Stone(s) · total" in the header of his panel (right side), only while you carry any. They are worth
     * a lot, so it needs a second click within 3 s. Selling them does not use up one of his deals.
     */
    private void megaStoneButton(GuiGraphics ctx, int px, int py, int pw, int mouseX, int mouseY, double t) {
        if (minecraft == null || minecraft.player == null) return;
        int count = dev.tcgshop.shop.TraderGoods.countMegaStones(minecraft.player);
        long unit = dev.tcgshop.shop.TraderGoods.megaStonePrice();
        if (count <= 0 || unit <= 0) return;
        boolean armed = t - megaArmedAt < 3;
        Component label = armed ? Component.translatable("tcgshop.sell.confirm")
                : Component.translatable("tcgshop.sell.mega_btn", count, PriceService.format(unit * count));
        int hisW = font.width(Component.translatable("tcgshop.trader.his").withStyle(ChatFormatting.BOLD));
        int bw = Math.min(pw - hisW - 18, font.width(label) + 12), bx = px + pw - bw - 4, by = py + 3;
        if (bw < 40) return;
        Ui.button(ctx, bx, by, bw, 12, Component.literal(Ui.ellipsis(label.getString(), bw - 8)), mouseX, mouseY, true, armed ? 0xFFB02020 : 0xFF7B4FD0);
        if (Ui.in(mouseX, mouseY, bx, by, bw, 12))
            setTooltipForNextRenderPass(List.of(Component.translatable("tcgshop.sell.mega_hint", PriceService.format(unit)).getVisualOrderText()),
                    net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner.INSTANCE, true);
        areas.add(new Area(bx, by, bw, 12, () -> {
            double now = System.nanoTime() / 1e9;
            if (now - megaArmedAt >= 3) { megaArmedAt = now; click(); return; }
            megaArmedAt = -10;
            ClientPlatform.INSTANCE.send(new MarketPayloads.TraderAction(data.entity(), dev.tcgshop.entity.TraderEntity.SELL_MEGA_STONES, -1, -1));
        }));
    }

    /**
     * 1.0 beta 3: "Steal" next to his name. Pockets one random card from his stall; he then hunts you (see
     * entity/CollectorTheft). The tooltip spells out the consequences and it needs a second click within 3 s.
     */
    private void stealButton(GuiGraphics ctx, int bx, int by, int mouseX, int mouseY, double t) {
        boolean any = false;
        for (ItemStack st : data.stock()) if (!st.isEmpty()) { any = true; break; }
        if (!any) return;
        boolean armed = t - stealArmedAt < 3;
        Component label = Component.translatable(armed ? "tcgshop.collector_theft.steal_confirm" : "tcgshop.collector_theft.steal_btn");
        int bw = font.width(label) + 12;
        Ui.button(ctx, bx, by, bw, 12, label, mouseX, mouseY, true, armed ? 0xFFB02020 : 0xFF6E2A2A);
        if (Ui.in(mouseX, mouseY, bx, by, bw, 12)) {
            List<net.minecraft.util.FormattedCharSequence> tip = new ArrayList<>();
            tip.add(Component.translatable("tcgshop.collector_theft.hint.title").withStyle(ChatFormatting.RED, ChatFormatting.BOLD).getVisualOrderText());
            for (int i = 0; i < 4; i++) tip.addAll(font.split(Component.translatable("tcgshop.collector_theft.hint." + i).withStyle(ChatFormatting.GRAY), 220));
            setTooltipForNextRenderPass(tip, net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner.INSTANCE, true);
        }
        areas.add(new Area(bx, by, bw, 12, () -> {
            double now = System.nanoTime() / 1e9;
            if (now - stealArmedAt >= 3) { stealArmedAt = now; click(); return; }
            stealArmedAt = -10;
            ClientPlatform.INSTANCE.send(new MarketPayloads.TraderAction(data.entity(), dev.tcgshop.entity.TraderEntity.STEAL, -1, -1));
            onClose(); // he is not trading anymore
        }));
    }

    /** 1.1: price filter chips for your cards. Returns the height used. */
    private int bandChips(GuiGraphics ctx, int x, int y, int w, int mouseX, int mouseY) {
        int cx = x, cy = y;
        for (int i = 0; i < BAND_KEYS.length; i++) {
            Component label = Component.translatable("tcgshop.trader.filter." + BAND_KEYS[i]);
            int cw = Ui.chipWidth(label);
            if (cx + cw > x + w) { cx = x; cy += 13; }
            Ui.chip(ctx, cx, cy, cw, 11, label, band == i, Ui.in(mouseX, mouseY, cx, cy, cw, 11), Ui.GOLD, 0);
            int fi = i;
            areas.add(new Area(cx, cy, cw, 11, () -> {
                band = fi;
                mine.sel.removeIf(s -> minecraft == null || minecraft.player == null || !inBand(CardPickGrid.stackAt(minecraft.player, s)));
                mine.restartAppear();
                click();
            }));
            cx += cw + 3;
        }
        return cy - y + 13;
    }

    private void click() {
        if (minecraft != null) minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 1.2f, 0.4f));
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

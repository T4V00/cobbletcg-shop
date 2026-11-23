package dev.tcgshop.client.screen;

import dev.masterball.api.card.CardInfo;
import dev.masterball.api.card.Rarity;
import dev.tcgshop.client.ShopClient;
import dev.tcgshop.client.binder.Ui;
import dev.tcgshop.entity.CustomerEntity;
import dev.tcgshop.network.DealAnswerPayload;
import dev.tcgshop.network.DealOpenPayload;
import dev.tcgshop.pricing.PriceService;
import dev.tcgshop.shop.ShopZones;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Deal with an NPC: they sell you something, want to buy a product from you or trade you a card.
 *
 * You can haggle with someone who SELLS you something (merchant) or wants to BUY something from you (haggling
 * customer). The window shows the NPC (portrait, name, personality), their mood and patience, the market price and
 * your margin (including what your customers overpay thanks to the shop's reputation), your offer (+/- steps, quick
 * market percentages or typed by hand), the history of offers and counteroffers and, at the end, whether there was a
 * deal or they left. Declining politely almost always leaves a neutral review; treating them badly does not.
 */
public class DealScreen extends Screen {
    // history: what happened each turn
    private static final int H_OPEN = 0, H_YOU_OFFER = 1, H_THEY_COUNTER = 2, H_HOLD = 3, H_INSULT = 4, H_THINK = 5, H_ARG = 6, H_ACCEPT = 7,
            H_DEAL = 8, H_WALK = 9, H_REJECT = 10, H_TIMEOUT = 11, H_YOU_THINK = 12;

    private record Hist(boolean you, int kind, long price, int action) {}

    private record Btn(int x, int y, int w, int h, Runnable r) {}

    private DealOpenPayload deal;
    private final double opened = now();
    private boolean answered, waiting;
    /** When the last response was sent (so we don't wait forever if the server doesn't reply). */
    private double waitSince;
    private static final double WAIT_TIMEOUT = 8.0;
    private long counter;
    /** Deal finished: last line, outcome (CustomerEntity.OUT_*), final price and when to close. */
    private Text closing;
    private double closeAt, closingSince;
    private int outcome;
    private long finalPrice;
    private final List<Btn> buttons = new ArrayList<>();
    private double flashAt = -10, shakeAt = -10;
    private final List<Hist> history = new ArrayList<>();
    /** Displayed mood (0-1): smoothly follows the target (patience + last reaction). */
    private float mood, moodTarget;
    /** Price you are typing by hand (null = not typing). */
    private String typed;
    private int lastAction = -1;
    /** Help key of the button under the mouse (drawn at the bottom). */
    private String tip;

    public DealScreen(DealOpenPayload deal) {
        super(Text.translatable("tcgshop.deal.title"));
        this.deal = deal;
        this.counter = suggested(deal);
        history.add(new Hist(false, H_OPEN, deal.price(), -1));
        mood = moodTarget = 0.25f + 0.6f * patienceFrac(deal);
    }

    public int entityId() { return deal.entityId(); }

    private static double now() { return System.nanoTime() / 1e9; }

    private static float patienceFrac(DealOpenPayload p) {
        return p.maxPatience() <= 0 ? 1f : MathHelper.clamp(p.patience() / (float) p.maxPatience(), 0f, 1f);
    }

    /** With haggling: the merchant selling to you (SELL) or the customer buying from you (BUY), both with a personality. */
    private boolean haggle() {
        return deal.persona() >= 0 && (deal.mode() == DealOpenPayload.SELL || deal.mode() == DealOpenPayload.BUY);
    }

    /** true = you buy (offer less); false = you sell (ask for more). */
    private boolean youBuy() { return deal.mode() == DealOpenPayload.SELL; }

    private long suggested(DealOpenPayload p) {
        return p.mode() == DealOpenPayload.SELL ? Math.max(1, Math.round(p.price() * 0.85)) : Math.max(p.price() + 1, Math.round(p.price() * 1.15));
    }

    /** Your offer always makes sense: when buying, no more than they ask; when selling, no less than they offer. */
    private long clampCounter(long v) {
        if (youBuy()) return Math.max(1, Math.min(deal.price(), v));
        long max = Math.max(deal.price() * 3, deal.market() * 3);
        return Math.max(deal.price(), Math.min(Math.max(deal.price(), max), v));
    }

    private void push(Hist h) {
        history.add(h);
        while (history.size() > 8) history.remove(0);
    }

    /** NPC response (counteroffer, anger, closing...). */
    public void update(DealOpenPayload p) {
        waiting = false;
        if (p.mode() == DealOpenPayload.CLOSED) {
            closing = p.line();
            outcome = p.patience();
            finalPrice = p.price();
            closingSince = now();
            closeAt = closingSince + 3.4;
            int kind = switch (outcome) {
                case CustomerEntity.OUT_DEAL -> H_DEAL;
                case CustomerEntity.OUT_WALK -> H_WALK;
                case CustomerEntity.OUT_REJECT -> H_REJECT;
                case CustomerEntity.OUT_TIMEOUT -> H_TIMEOUT;
                default -> -1;
            };
            if (kind >= 0) push(new Hist(false, kind, finalPrice, -1));
            if (outcome == CustomerEntity.OUT_DEAL) moodTarget = 1f;
            else if (outcome != CustomerEntity.OUT_UNKNOWN) moodTarget = Math.min(moodTarget, 0.2f);
            if (client != null && client.player != null) {
                if (outcome == CustomerEntity.OUT_DEAL) client.player.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.2f);
                else if (outcome == CustomerEntity.OUT_WALK || outcome == CustomerEntity.OUT_TIMEOUT)
                    client.player.playSound(SoundEvents.ENTITY_VILLAGER_NO, 0.5f, 1f);
            }
            return;
        }
        boolean priceChanged = p.price() != deal.price();
        int drop = deal.patience() - p.patience();
        if (priceChanged) { push(new Hist(false, H_THEY_COUNTER, p.price(), -1)); flashAt = now(); }
        else if (drop >= 2) { push(new Hist(false, H_INSULT, p.price(), -1)); shakeAt = now(); }
        else if (lastAction == DealAnswerPayload.INTEREST) push(new Hist(false, H_THINK, p.price(), -1));
        else push(new Hist(false, H_HOLD, p.price(), -1));
        deal = p;
        float react = priceChanged ? 0.15f : drop >= 2 ? -0.3f : -0.05f;
        moodTarget = MathHelper.clamp(0.15f + 0.7f * patienceFrac(p) + react, 0f, 1f);
        boolean stale = youBuy() ? counter >= p.price() : counter <= p.price();
        if (stale || counter <= 0) counter = suggested(p);
        counter = clampCounter(counter);
    }

    @Override
    public boolean shouldPause() { return false; }

    private void send(int action) {
        if (answered || waiting || closing != null) return;
        commitTyped();
        lastAction = action;
        if (!haggle()) { // without haggling it closes immediately
            if (action != DealAnswerPayload.ACCEPT && action != DealAnswerPayload.REJECT_POLITE && action != DealAnswerPayload.REJECT_RUDE) return;
            answered = true;
            ClientPlayNetworking.send(new DealAnswerPayload(deal.entityId(), action, counter));
            super.close();
            return;
        }
        switch (action) {
            case DealAnswerPayload.ACCEPT -> push(new Hist(true, H_ACCEPT, deal.price(), action));
            case DealAnswerPayload.INTEREST -> push(new Hist(true, H_YOU_THINK, 0, action));
            case DealAnswerPayload.COUNTER -> push(new Hist(true, H_YOU_OFFER, counter, action));
            case DealAnswerPayload.ARG_MARKET, DealAnswerPayload.ARG_ELSEWHERE, DealAnswerPayload.ARG_BROKE, DealAnswerPayload.ARG_MAX ->
                    push(new Hist(true, H_ARG, counter, action));
            default -> {}
        }
        waiting = true;
        waitSince = now();
        ClientPlayNetworking.send(new DealAnswerPayload(deal.entityId(), action, counter));
    }

    private void commitTyped() {
        if (typed == null) return;
        if (!typed.isEmpty()) {
            try { counter = clampCounter(Long.parseLong(typed)); } catch (NumberFormatException ignored) {}
        }
        typed = null;
    }

    private void nudge(double pct) {
        typed = null;
        long d = Math.max(1, Math.round(deal.price() * Math.abs(pct)));
        counter = clampCounter(counter + (pct < 0 ? -d : d));
    }

    private int[] box() {
        if (haggle()) {
            int w = Math.min(width - 16, 460);
            int h = Math.min(height - 12, 272);
            return new int[]{(width - w) / 2, (height - h) / 2, w, h};
        }
        int w = Math.min(width - 20, deal.mode() == DealOpenPayload.TRADE ? 380 : 340);
        int h = deal.mode() == DealOpenPayload.TRADE ? 230 : 196;
        return new int[]{(width - w) / 2, (height - h) / 2, w, h};
    }

    private void product(DrawContext ctx, ItemStack st, int x, int y, int w, int mouseX, double t) {
        int h = Math.round(w / Draw.CARD_RATIO);
        if (CardInfo.isCard(st)) {
            Rarity r = Rarity.of(st);
            if (r.tier > 0) Draw.glow(ctx, x + w / 2f, y + h / 2f, w, h, r.color, 0.3f);
            Draw.cardFace(ctx, CardInfo.texture(CardInfo.id(st)), st, x, y, w, h, 1f);
            if (r.tier > 0) Draw.holo(ctx, x, y, w, h, r.tier, r.color, t, mouseX);
        } else CatalogPanel.drawProduct(ctx, st, x, y, w, h);
    }

    @Override
    public void tick() {
        if (closing != null) {
            if (now() > closeAt) super.close();
            return;
        }
        // safety net: the NPC no longer exists (left, despawned, went out of range) or the server doesn't reply
        Entity e = client == null || client.world == null ? null : client.world.getEntityById(deal.entityId());
        if (e == null || !e.isAlive() || (waiting && now() - waitSince > WAIT_TIMEOUT)) endLocally();
    }

    /** Closes the deal in the window without a server response (same look as a close with no outcome). */
    private void endLocally() {
        waiting = false;
        typed = null;
        closing = Text.translatable("tcgshop.deal.gone");
        outcome = CustomerEntity.OUT_UNKNOWN;
        finalPrice = 0;
        closingSince = now();
        closeAt = closingSince + 2.5;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        double t = now();
        buttons.clear();
        tip = null;
        mood += (moodTarget - mood) * Math.min(1f, delta * 0.15f);
        float in = (float) Draw.easeOutBack(MathHelper.clamp((t - opened) / 0.3, 0, 1));
        ctx.fillGradient(0, 0, width, height, 0xA0000000, 0xD0000000);
        int[] b = box();
        int x = b[0], y = b[1], w = b[2], h = b[3];
        var m = ctx.getMatrices();
        m.push();
        m.translate(x + w / 2f, y + h / 2f, 0);
        m.scale(0.85f + 0.15f * in, 0.85f + 0.15f * in, 1);
        m.translate(-(x + w / 2f), -(y + h / 2f), 0);
        int accent = switch (deal.mode()) { case DealOpenPayload.SELL -> 0xFF2E9C5A; case DealOpenPayload.TRADE -> 0xFF8E44AD; default -> 0xFFD39B1A; };
        ctx.fill(x + 4, y + 6, x + w + 4, y + h + 6, 0x66000000);
        Ui.roundGradient(ctx, x, y, w, h, 0xFF23273A, 0xFF12141F);
        Ui.roundGradient(ctx, x, y, w, 24, Draw.withAlpha(accent, 0.95f), Draw.withAlpha(Ui.darken(accent, 40), 0.95f));
        String titleKey = switch (deal.mode()) { case DealOpenPayload.SELL -> "tcgshop.deal.sell_title"; case DealOpenPayload.TRADE -> "tcgshop.deal.trade_title";
            default -> "tcgshop.deal.buy_title"; };
        Draw.scaledCentered(ctx, Text.translatable(titleKey).formatted(Formatting.BOLD), x + w / 2f, y + 8, 1.1f, 0xFFFFFFFF);

        if (haggle()) renderHaggle(ctx, x, y, w, h, mouseX, mouseY, t, in);
        else {
            ctx.drawTextWithShadow(textRenderer, deal.name().copy().formatted(Formatting.YELLOW), x + 12, y + 32, 0xFFFFFFFF);
            Text line = closing != null ? closing : deal.line();
            if (deal.mode() == DealOpenPayload.TRADE) renderTrade(ctx, x, y, w, line, mouseX, t);
            else renderOffer(ctx, x, y, w, line, mouseX, t);
            if (closing != null) {
                float a = (float) MathHelper.clamp((closeAt - t) / 0.6, 0, 1);
                Ui.text(ctx, Text.translatable("tcgshop.deal.closed").formatted(Formatting.ITALIC), x, y + h - 22, w, 10, Draw.withAlpha(0xFFAAB0C8, a), false);
            } else {
                int by = y + h - 28;
                Ui.button(ctx, x + 14, by, 130, 20, Text.translatable("tcgshop.deal.accept"), mouseX, mouseY, true, 0xFF3C8A4A);
                Ui.button(ctx, x + w - 144, by, 130, 20, Text.translatable("tcgshop.deal.reject"), mouseX, mouseY, true, 0xFF7A2020);
                buttons.add(new Btn(x + 14, by, 130, 20, () -> send(DealAnswerPayload.ACCEPT)));
                buttons.add(new Btn(x + w - 144, by, 130, 20, () -> send(DealAnswerPayload.REJECT_POLITE)));
            }
        }
        m.pop();
    }

    // ------------------------------------------------------------------ haggling

    private void renderHaggle(DrawContext ctx, int x, int y, int w, int h, int mx, int my, double t, float in) {
        // at the bottom, from bottom to top: help, responses (3x3), quick percentages, your offer
        int footY = y + h - 12;
        int ansH = 17, ansGap = 3, ansY = footY - 3 - (ansH + ansGap) * 3 + ansGap;
        int quickY = ansY - 16, offerY = quickY - 19;
        int top = y + 28, topEnd = offerY - 5;

        // ---- left column: portrait, patience and mood
        int lw = 70, lx = x + 8;
        int portraitH = Math.max(40, topEnd - top - 26);
        portrait(ctx, lx, top, lw, portraitH, mx, my, in);
        int py = top + portraitH + 3;
        Ui.text(ctx, Text.translatable("tcgshop.haggle.patience"), lx, py, lw, 9, 0xFFAAB0C8, false);
        float pf = patienceFrac(deal);
        Ui.progress(ctx, lx + 2, py + 9, lw - 4, 5, closing != null && outcome != CustomerEntity.OUT_DEAL ? 0 : pf,
                pf > 0.6f ? 0xFF43C463 : pf > 0.3f ? 0xFFFFC23D : 0xFFE05050);
        int mc = moodColor();
        Ui.text(ctx, Text.translatable("tcgshop.haggle.mood." + Math.min(4, (int) (mood * 5))), lx, py + 15, lw, 9, mc, true);

        // ---- right column: the product
        int cw = 62, cx = x + w - 8 - cw, ch = Math.round(cw / Draw.CARD_RATIO);
        if (!deal.item().isEmpty()) {
            int cy = top + Math.max(0, (topEnd - top - 12 - ch) / 2);
            product(ctx, deal.item(), cx, cy, cw, mx, t);
            Ui.text(ctx, Text.literal(Ui.ellipsis(deal.item().getName().getString(), (int) ((cw + 10) / 0.8f))), cx - 5, cy + ch + 2, cw + 10, 9, 0xFFFFFFFF, true);
        }

        // ---- center: name + personality, speech bubble, prices and history
        int mx0 = lx + lw + 8, mw = cx - 8 - mx0;
        ctx.drawTextWithShadow(textRenderer, Text.literal(Ui.ellipsis(deal.name().getString(), mw - 80)).formatted(Formatting.YELLOW), mx0, top + 1, 0xFFFFFFFF);
        personaChip(ctx, mx0 + Math.min(mw - 80, textRenderer.getWidth(deal.name())) + 6, top);

        int bubY = top + 13, bubH = Math.max(24, Math.min(40, (topEnd - bubY) - 46));
        float shake = (float) MathHelper.clamp(1 - (t - shakeAt) / 0.45, 0, 1);
        int sx = shake > 0 ? Math.round((float) Math.sin(t * 60) * 3 * shake) : 0;
        bubble(ctx, mx0 + sx, bubY, mw, bubH, closing != null ? closing : deal.line(), t);

        int iy = bubY + bubH + 4, ih = topEnd - iy;
        int infoW = Math.max(110, mw * 52 / 100);
        info(ctx, mx0, iy, infoW, ih, t);
        historyPanel(ctx, mx0 + infoW + 4, iy, mw - infoW - 4, ih);

        if (closing != null) {
            outcomeBanner(ctx, x + 10, offerY, w - 20, footY + 9 - offerY, t);
            return;
        }
        offerRow(ctx, x, offerY, w, mx, my);
        quickRow(ctx, x, quickY, w, mx, my);
        answers(ctx, x + 10, ansY, w - 20, ansH, ansGap, mx, my);
        Text foot = tip != null ? Text.translatable(tip) : Text.translatable("tcgshop.haggle.hint");
        Ui.text(ctx, foot, x + 6, footY, w - 12, 10, tip != null ? 0xFFE8E2C8 : 0xFF6E7490, false);
        if (waiting) {
            String dots = ".".repeat(1 + (int) (t * 3) % 3);
            Ui.text(ctx, Text.translatable("tcgshop.deal.thinking").append(dots), x + w - 90, y + 26 - 12, 84, 10, 0xFFE8E8F0, true);
        }
    }

    private int moodColor() {
        return mood < 0.5f ? Ui.lerpC(0xFFE05050, 0xFFFFD34D, mood * 2) : Ui.lerpC(0xFFFFD34D, 0xFF43C463, (mood - 0.5f) * 2);
    }

    private void portrait(DrawContext ctx, int px, int py, int pw, int ph, int mx, int my, float in) {
        Ui.roundGradient(ctx, px, py, pw, ph, 0xFF2D3350, 0xFF181B2A);
        ctx.fillGradient(px + 1, py + ph / 2, px + pw - 1, py + ph - 1, 0x00000000, Draw.withAlpha(moodColor(), 0.35f));
        Ui.outline(ctx, px, py, pw, ph, 0x55FFFFFF);
        Entity e = client != null && client.world != null ? client.world.getEntityById(deal.entityId()) : null;
        // only once the window is no longer animating (the portrait clipping does not follow the scale)
        if (in >= 0.999f && e instanceof LivingEntity le) {
            int size = Math.max(12, Math.round(ph * 0.4f));
            InventoryScreen.drawEntity(ctx, px + 1, py + 1, px + pw - 1, py + ph - 1, size, 0.0625f, mx, my, le);
        } else if (!(e instanceof LivingEntity)) {
            Draw.scaledCentered(ctx, Text.literal("?").formatted(Formatting.BOLD), px + pw / 2f, py + ph / 2f - 8, 2.2f, 0x88FFFFFF);
        }
    }

    private void personaChip(DrawContext ctx, int x, int y) {
        int pi = Math.min(CustomerEntity.PERSONAS.length - 1, Math.max(0, deal.persona()));
        Text p = Text.translatable((youBuy() ? "tcgshop.persona." : "tcgshop.haggle.buyer.") + CustomerEntity.PERSONAS[pi]);
        int pw = textRenderer.getWidth(p) + 8;
        Ui.round(ctx, x, y - 1, pw, 11, 0x553A6FD8);
        ctx.drawText(textRenderer, p, x + 4, y + 1, 0xFFBFD6FF, false);
    }

    /** Speech bubble with the NPC's line: if it doesn't fit, the font gets smaller. */
    private void bubble(DrawContext ctx, int x, int y, int w, int h, Text line, double t) {
        Ui.roundGradient(ctx, x, y, w, h, 0xFFF4F1E8, 0xFFDCD6C6);
        ctx.fill(x + 10, y - 2, x + 16, y, 0xFFF4F1E8); // little tail pointing to the name
        Ui.outline(ctx, x, y, w, h, 0xFF8A8270);
        Text q = Text.literal("«").append(line).append("»").formatted(Formatting.ITALIC);
        int inner = w - 10, avail = h - 6;
        List<OrderedText> lines = textRenderer.wrapLines(q, inner);
        float s = 1f;
        for (int i = 0; i < 4 && lines.size() * 10 * s > avail; i++) {
            s *= 0.85f;
            lines = textRenderer.wrapLines(q, (int) (inner / s));
        }
        var m = ctx.getMatrices();
        m.push();
        m.translate(x + 5, y + 4, 0);
        m.scale(s, s, 1);
        int ly = 0;
        for (OrderedText l : lines) {
            if ((ly + 9) * s > avail + 1) break;
            ctx.drawText(textRenderer, l, 0, ly, 0xFF2A2620, false);
            ly += 10;
        }
        m.pop();
        if (waiting) {
            String dots = ".".repeat(1 + (int) (t * 3) % 3);
            ctx.drawText(textRenderer, dots, x + w - 14, y + h - 9, 0xFF6A6250, false);
        }
    }

    /** What is asked/offered, the market, your margin and what people overpay in your shop thanks to its reputation. */
    private void info(DrawContext ctx, int x, int y, int w, int h, double t) {
        Ui.roundGradient(ctx, x, y, w, h, 0xFF1C2030, 0xFF151824);
        Ui.outline(ctx, x, y, w, h, 0x33FFFFFF);
        int tx = x + 5, ty = y + 4, tw = w - 10;
        float fl = (float) MathHelper.clamp(1 - (t - flashAt) / 0.8, 0, 1);
        int priceCol = fl > 0 ? Ui.lerpC(Ui.GOLD, 0xFF6BFF8A, fl) : Ui.GOLD;
        long price = closing != null && outcome == CustomerEntity.OUT_DEAL && finalPrice > 0 ? finalPrice : deal.price();
        line(ctx, Text.translatable(youBuy() ? "tcgshop.deal.asks" : "tcgshop.deal.offer", PriceService.format(price)).formatted(Formatting.BOLD), tx, ty, tw, priceCol);
        ty += 10;
        if (ty + 9 > y + h) return;
        double diff = deal.market() == 0 ? 0 : (price - deal.market()) * 100.0 / deal.market();
        boolean good = youBuy() ? diff < 0 : diff > 0;
        line(ctx, Text.translatable("tcgshop.haggle.market_diff", PriceService.format(deal.market()), String.format(Locale.ROOT, "%+.0f%%", diff)),
                tx, ty, tw, good ? 0xFF8BE08B : 0xFFFF8F7A);
        ty += 10;
        if (ty + 9 > y + h) return;
        int rep = shopRep();
        double prem = rep < 0 ? 0 : ShopZones.repPremium(rep, ShopZones.isSealed(deal.item()));
        long margin;
        Text mt;
        if (youBuy()) { // if you resell it at market (plus what your customers overpay for your reputation)
            long resale = Math.round(deal.market() * (1 + Math.max(0, prem)));
            margin = resale - price;
            mt = Text.translatable("tcgshop.haggle.margin_buy", signed(margin));
        } else {
            margin = price - deal.market();
            mt = Text.translatable("tcgshop.haggle.margin_sell", signed(margin));
        }
        line(ctx, mt, tx, ty, tw, margin >= 0 ? 0xFF6BE07B : 0xFFFF7A5C);
        ty += 10;
        if (ty + 9 > y + h || rep < 0) return;
        line(ctx, Text.translatable("tcgshop.haggle.rep_line", rep, String.format(Locale.ROOT, "%+.0f%%", prem * 100)), tx, ty, tw,
                prem > 0.005 ? 0xFF9FD8FF : prem < -0.005 ? 0xFFFF9F7A : 0xFF9A9AAE);
    }

    private void line(DrawContext ctx, Text text, int x, int y, int w, int color) {
        int tw = textRenderer.getWidth(text);
        if (tw <= w) { ctx.drawTextWithShadow(textRenderer, text, x, y, color); return; }
        float s = w / (float) tw;
        var m = ctx.getMatrices();
        m.push();
        m.translate(x, y + (1 - s) * 4, 0);
        m.scale(s, s, 1);
        ctx.drawTextWithShadow(textRenderer, text, 0, 0, color);
        m.pop();
    }

    private static String signed(long v) {
        return (v >= 0 ? "+" : "-") + PriceService.format(Math.abs(v));
    }

    /** Reputation of the shop the NPC is in (or your best one); -1 if unknown. */
    private int shopRep() {
        if (client == null || client.world == null || client.player == null) return -1;
        Entity e = client.world.getEntityById(deal.entityId());
        Vec3d at = e != null ? e.getPos() : client.player.getPos();
        int best = -1;
        for (ShopClient.ZoneInfo z : ShopClient.zones) {
            if (z.box().contains(at)) return z.reputation();
            if (z.owner().equals(client.player.getUuid())) best = Math.max(best, z.reputation());
        }
        return best;
    }

    private void historyPanel(DrawContext ctx, int x, int y, int w, int h) {
        Ui.roundGradient(ctx, x, y, w, h, 0xFF1C2030, 0xFF151824);
        Ui.outline(ctx, x, y, w, h, 0x33FFFFFF);
        ctx.drawText(textRenderer, Text.translatable("tcgshop.haggle.history").formatted(Formatting.BOLD), x + 5, y + 4, 0xFFAAB0C8, false);
        int rows = Math.max(0, (h - 15) / 9);
        int from = Math.max(0, history.size() - rows);
        int ly = y + 14;
        for (int i = from; i < history.size(); i++) {
            Hist e = history.get(i);
            int col = e.you() ? 0xFF8FC8FF : switch (e.kind()) {
                case H_DEAL -> 0xFF6BE07B;
                case H_WALK, H_TIMEOUT, H_INSULT -> 0xFFFF7A5C;
                case H_REJECT -> 0xFF9A9AAE;
                default -> 0xFFFFD08A;
            };
            Text who = Text.translatable(e.you() ? "tcgshop.haggle.h.you" : "tcgshop.haggle.h.them").append(" ");
            MutableText row = who.copy().append(histText(e));
            var m = ctx.getMatrices();
            int tw = textRenderer.getWidth(row);
            float s = Math.min(0.8f, (w - 10) / (float) Math.max(1, tw));
            m.push();
            m.translate(x + 5, ly, 0);
            m.scale(s, s, 1);
            ctx.drawText(textRenderer, row, 0, 0, col, false);
            m.pop();
            ly += 9;
        }
    }

    private Text histText(Hist e) {
        String p = PriceService.format(e.price());
        boolean buy = youBuy();
        return switch (e.kind()) {
            case H_OPEN -> Text.translatable(buy ? "tcgshop.haggle.h.open_sell" : "tcgshop.haggle.h.open_buy", p);
            case H_YOU_OFFER -> Text.translatable(buy ? "tcgshop.haggle.h.you_offer" : "tcgshop.haggle.h.you_ask", p);
            case H_ARG -> Text.translatable(buy ? "tcgshop.haggle.h.you_offer" : "tcgshop.haggle.h.you_ask", p).append(" · ")
                    .append(Text.translatable("tcgshop.haggle.h.arg." + argName(e.action())));
            case H_THEY_COUNTER -> Text.translatable(buy ? "tcgshop.haggle.h.down" : "tcgshop.haggle.h.up", p);
            case H_HOLD -> Text.translatable("tcgshop.haggle.h.hold", p);
            case H_INSULT -> Text.translatable("tcgshop.haggle.h.insult");
            case H_THINK -> Text.translatable("tcgshop.haggle.h.think");
            case H_YOU_THINK -> Text.translatable("tcgshop.haggle.h.you_think");
            case H_ACCEPT -> Text.translatable("tcgshop.haggle.h.accept", p);
            case H_DEAL -> Text.translatable("tcgshop.haggle.h.deal", p);
            case H_WALK -> Text.translatable("tcgshop.haggle.h.walk");
            case H_REJECT -> Text.translatable("tcgshop.haggle.h.reject");
            case H_TIMEOUT -> Text.translatable("tcgshop.haggle.h.timeout");
            default -> Text.empty();
        };
    }

    private String argName(int action) {
        return switch (action) {
            case DealAnswerPayload.ARG_MARKET -> "market";
            case DealAnswerPayload.ARG_ELSEWHERE -> "elsewhere";
            case DealAnswerPayload.ARG_BROKE -> youBuy() ? "broke" : "rep";
            case DealAnswerPayload.ARG_MAX -> "max";
            default -> "other";
        };
    }

    /** Your offer: -10% -5% -1% [price (click to type)] +1% +5% +10%. */
    private void offerRow(DrawContext ctx, int x, int y, int w, int mx, int my) {
        Text lbl = Text.translatable(youBuy() ? "tcgshop.haggle.your_offer" : "tcgshop.haggle.your_ask").formatted(Formatting.BOLD);
        String[] steps = {"-10%", "-5%", "-1%", "+1%", "+5%", "+10%"};
        double[] pct = {-0.10, -0.05, -0.01, 0.01, 0.05, 0.10};
        String cs = typed != null ? (typed.isEmpty() ? "" : PriceService.format(parseOr(typed))) : PriceService.format(counter);
        int boxW = Math.max(80, textRenderer.getWidth(cs) + 16);
        int total = textRenderer.getWidth(lbl) + 8 + boxW + 6;
        for (String s : steps) total += textRenderer.getWidth(s) + 8 + 3;
        int bx = x + (w - total) / 2;
        ctx.drawTextWithShadow(textRenderer, lbl, bx, y + 4, 0xFFFFFFFF);
        bx += textRenderer.getWidth(lbl) + 8;
        for (int i = 0; i < 3; i++) bx = step(ctx, bx, y, steps[i], pct[i], mx, my);
        // price box: click to type it by hand
        boolean hover = Ui.in(mx, my, bx, y, boxW, 15);
        Ui.round(ctx, bx, y, boxW, 15, typed != null ? 0xFF1A1E2C : 0xFF0E1018);
        Ui.outline(ctx, bx, y, boxW, 15, typed != null ? 0xFFFFFFFF : hover ? Ui.brighten(Ui.GOLD, 30) : Ui.GOLD);
        String shown = cs + (typed != null && (now() * 2) % 2 < 1 ? "_" : "");
        Ui.text(ctx, Text.literal(shown).formatted(Formatting.BOLD), bx, y + 4, boxW, 10, typed != null ? 0xFFFFFFFF : Ui.GOLD, false);
        if (hover) tip = "tcgshop.haggle.tip.type";
        buttons.add(new Btn(bx, y, boxW, 15, () -> typed = typed == null ? "" : typed));
        bx += boxW + 3;
        for (int i = 3; i < 6; i++) bx = step(ctx, bx, y, steps[i], pct[i], mx, my);
    }

    private static long parseOr(String s) {
        try { return Long.parseLong(s); } catch (NumberFormatException e) { return 0; }
    }

    private int step(DrawContext ctx, int x, int y, String label, double pct, int mx, int my) {
        int bw = textRenderer.getWidth(label) + 8;
        Ui.button(ctx, x, y, bw, 15, Text.literal(label), mx, my, !waiting, pct < 0 ? 0xFF5A3A4E : 0xFF39415E);
        buttons.add(new Btn(x, y, bw, 15, () -> nudge(pct)));
        return x + bw + 3;
    }

    /** Quick percentages of the market price and what your current offer amounts to. */
    private void quickRow(DrawContext ctx, int x, int y, int w, int mx, int my) {
        int[] pcts = youBuy() ? new int[]{60, 70, 80, 90, 100} : new int[]{100, 110, 120, 135, 150};
        long market = Math.max(1, deal.market());
        int cur = (int) Math.round(counter * 100.0 / market);
        Text now = Text.translatable("tcgshop.haggle.of_market", cur + "%");
        int chipW = 36, gap = 3;
        int total = textRenderer.getWidth(now) + 8 + pcts.length * (chipW + gap);
        int bx = x + (w - total) / 2;
        ctx.drawText(textRenderer, now, bx, y + 2, 0xFFAAB0C8, false);
        bx += textRenderer.getWidth(now) + 8;
        for (int p : pcts) {
            long v = clampCounter(Math.round(market * p / 100.0));
            boolean sel = typed == null && v == counter;
            boolean hover = Ui.in(mx, my, bx, y, chipW, 12);
            Ui.chip(ctx, bx, y, chipW, 12, Text.literal(p + "%"), sel, hover, youBuy() ? 0xFF2E9C5A : 0xFFD39B1A, 0);
            if (hover) tip = "tcgshop.haggle.tip.quick";
            buttons.add(new Btn(bx, y, chipW, 12, () -> { typed = null; counter = v; }));
            bx += chipW + gap;
        }
    }

    /** Responses: accept / counteroffer / final offer, three arguments and think / no thanks / rudeness. */
    private void answers(DrawContext ctx, int x, int y, int w, int bh, int gap, int mx, int my) {
        int bw = (w - gap * 2) / 3;
        String pc = PriceService.format(counter);
        boolean buy = youBuy();
        int rep = shopRep();
        double prem = rep < 0 ? 0 : ShopZones.repPremium(rep, ShopZones.isSealed(deal.item()));
        Object[][] grid = {
                {Text.translatable("tcgshop.haggle.a.accept", PriceService.format(deal.price())), 0xFF2E8A4A, DealAnswerPayload.ACCEPT, "accept"},
                {Text.translatable(buy ? "tcgshop.haggle.a.counter_buy" : "tcgshop.haggle.a.counter_sell", pc), 0xFF3A6FD8, DealAnswerPayload.COUNTER, "counter"},
                {Text.translatable("tcgshop.haggle.a.max", pc), 0xFF8A5A1A, DealAnswerPayload.ARG_MAX, "max"},
                {Text.translatable(buy ? "tcgshop.haggle.a.market_buy" : "tcgshop.haggle.a.market_sell"), 0xFF39415E, DealAnswerPayload.ARG_MARKET, "market"},
                {Text.translatable(buy ? "tcgshop.haggle.a.elsewhere_buy" : "tcgshop.haggle.a.elsewhere_sell"), 0xFF39415E, DealAnswerPayload.ARG_ELSEWHERE, "elsewhere"},
                {Text.translatable(buy ? "tcgshop.haggle.a.broke" : "tcgshop.haggle.a.rep"), !buy && prem > 0.02 ? 0xFF2F6A5A : 0xFF39415E,
                        DealAnswerPayload.ARG_BROKE, buy ? "broke" : "rep"},
                {Text.translatable("tcgshop.haggle.a.think"), 0xFF39415E, DealAnswerPayload.INTEREST, "think"},
                {Text.translatable("tcgshop.haggle.a.no"), 0xFF5A2A2A, DealAnswerPayload.REJECT_POLITE, "no"},
                {Text.translatable("tcgshop.haggle.a.rude"), 0xFF8A1F1F, DealAnswerPayload.REJECT_RUDE, "rude"},
        };
        for (int i = 0; i < grid.length; i++) {
            int bx = x + (i % 3) * (bw + gap), by = y + (i / 3) * (bh + gap);
            answer(ctx, bx, by, bw, bh, (Text) grid[i][0], (Integer) grid[i][1], (Integer) grid[i][2], mx, my);
            if (Ui.in(mx, my, bx, by, bw, bh)) tip = "tcgshop.haggle.tip." + grid[i][3];
        }
    }

    private void answer(DrawContext ctx, int x, int y, int w, int h, Text label, int color, int action, int mx, int my) {
        Ui.button(ctx, x, y, w, h, Text.empty(), mx, my, !waiting, color);
        float s = Math.min(1f, (w - 8) / (float) Math.max(1, textRenderer.getWidth(label)));
        var m = ctx.getMatrices();
        m.push();
        m.translate(x + w / 2f, y + h / 2f - 3.5f * s, 0);
        m.scale(s, s, 1);
        ctx.drawTextWithShadow(textRenderer, label, -textRenderer.getWidth(label) / 2, 0, waiting ? 0xFF8890A8 : 0xFFFFFFFF);
        m.pop();
        buttons.add(new Btn(x, y, w, h, () -> send(action)));
    }

    /** Deal outcome: done (green), they left (orange), you declined (gray) or they got fed up (red). */
    private void outcomeBanner(DrawContext ctx, int x, int y, int w, int h, double t) {
        int col = switch (outcome) {
            case CustomerEntity.OUT_DEAL -> 0xFF2E9C5A;
            case CustomerEntity.OUT_WALK -> 0xFFD07A1A;
            case CustomerEntity.OUT_REJECT -> 0xFF5A5F78;
            case CustomerEntity.OUT_TIMEOUT -> 0xFFB03030;
            default -> 0xFF3A6FD8;
        };
        String icon = switch (outcome) {
            case CustomerEntity.OUT_DEAL -> "✔";
            case CustomerEntity.OUT_WALK -> "↩";
            case CustomerEntity.OUT_REJECT -> "✖";
            case CustomerEntity.OUT_TIMEOUT -> "⌛";
            default -> "•";
        };
        float pop = (float) Draw.easeOutBack(MathHelper.clamp((t - closingSince) / 0.35, 0, 1));
        Ui.roundGradient(ctx, x, y, w, h, Draw.withAlpha(col, 0.55f), Draw.withAlpha(Ui.darken(col, 50), 0.55f));
        Ui.outline(ctx, x, y, w, h, Ui.brighten(col, 40));
        Draw.scaledCentered(ctx, Text.literal(icon).formatted(Formatting.BOLD), x + 24, y + h / 2f - 9 * pop, 2.4f * pop, 0xFFFFFFFF);
        int tx = x + 48, tw = w - 56;
        Text title = switch (outcome) {
            case CustomerEntity.OUT_DEAL -> Text.translatable(youBuy() ? "tcgshop.haggle.out.deal_buy" : "tcgshop.haggle.out.deal_sell", PriceService.format(finalPrice));
            case CustomerEntity.OUT_WALK -> Text.translatable("tcgshop.haggle.out.walk");
            case CustomerEntity.OUT_REJECT -> Text.translatable("tcgshop.haggle.out.reject");
            case CustomerEntity.OUT_TIMEOUT -> Text.translatable("tcgshop.haggle.out.timeout");
            default -> Text.translatable("tcgshop.deal.closed");
        };
        line(ctx, title.copy().formatted(Formatting.BOLD), tx, y + h / 2 - 14, tw, 0xFFFFFFFF);
        Text sub;
        if (outcome == CustomerEntity.OUT_DEAL && deal.market() > 0) {
            double diff = (finalPrice - deal.market()) * 100.0 / deal.market();
            boolean good = youBuy() ? diff <= 0 : diff >= 0;
            sub = Text.translatable(good ? "tcgshop.haggle.out.good" : "tcgshop.haggle.out.bad", String.format(Locale.ROOT, "%+.0f%%", diff));
        } else sub = Text.translatable("tcgshop.haggle.out.sub." + Math.max(0, Math.min(4, outcome)));
        line(ctx, sub, tx, y + h / 2 - 2, tw, 0xFFE8E8F0);
        float left = (float) MathHelper.clamp((closeAt - t) / Math.max(0.1, closeAt - closingSince), 0, 1);
        Ui.progress(ctx, tx, y + h / 2 + 10, Math.min(tw, 160), 4, left, Ui.brighten(col, 30));
        ctx.drawText(textRenderer, Text.translatable("tcgshop.haggle.click_close"), tx + Math.min(tw, 160) + 6, y + h / 2 + 8, 0xFFCCCCCC, false);
    }

    // ------------------------------------------------------------------ deals without haggling

    private void renderTrade(DrawContext ctx, int x, int y, int w, Text lineT, int mouseX, double t) {
        int ty = y + 44;
        for (var line : textRenderer.wrapLines(Text.literal("«").append(lineT).append("»").formatted(Formatting.ITALIC), w - 24)) {
            ctx.drawTextWithShadow(textRenderer, line, x + 12, ty, 0xFFDDDDDD);
            ty += 10;
        }
        if (deal.item().isEmpty()) return;
        int cw = 66, ch = Math.round(cw / Draw.CARD_RATIO), cy = Math.max(ty + 4, y + 66);
        int lx = x + w / 4 - cw / 2, rx = x + 3 * w / 4 - cw / 2;
        Draw.scaledCentered(ctx, Text.translatable("tcgshop.deal.you_get"), lx + cw / 2f, cy - 10, 1f, 0xFF6BE07B);
        Draw.scaledCentered(ctx, Text.translatable("tcgshop.deal.you_give"), rx + cw / 2f, cy - 10, 1f, 0xFFFF9F6B);
        product(ctx, deal.item(), lx, cy, cw, mouseX, t);
        product(ctx, deal.want(), rx, cy, cw, mouseX, t);
        float pulse = (float) (0.5 + 0.5 * Math.sin(t * 4));
        Draw.scaledCentered(ctx, Text.literal("⇄").formatted(Formatting.BOLD), x + w / 2f, cy + ch / 2f - 8, 2.4f, Draw.withAlpha(Ui.GOLD, 0.6f + 0.4f * pulse));
        int iy = cy + ch + 4;
        Ui.text(ctx, Text.literal(Ui.ellipsis(deal.item().getName().getString(), w / 2 - 10)), x + 4, iy, w / 2 - 8, 10, 0xFFFFFFFF, true);
        Ui.text(ctx, Text.literal(Ui.ellipsis(deal.want().getName().getString(), w / 2 - 10)), x + w / 2 + 4, iy, w / 2 - 8, 10, 0xFFFFFFFF, true);
        Ui.text(ctx, Text.literal(PriceService.format(deal.market())), x + 4, iy + 11, w / 2 - 8, 10, Ui.GOLD, true);
        Ui.text(ctx, Text.literal(PriceService.format(deal.wantMarket())), x + w / 2 + 4, iy + 11, w / 2 - 8, 10, Ui.GOLD, true);
        double diff = deal.wantMarket() == 0 ? 0 : (deal.market() - deal.wantMarket()) * 100.0 / deal.wantMarket();
        boolean good = diff >= 0;
        Draw.scaledCentered(ctx, Text.literal(String.format(Locale.ROOT, "%+.0f%% ", diff)).append(Text.translatable(good ? "tcgshop.deal.good" : "tcgshop.deal.bad")),
                x + w / 2f, iy + 24, 1f, good ? 0xFF6BE07B : 0xFFFF7A5C);
    }

    private void renderOffer(DrawContext ctx, int x, int y, int w, Text lineT, int mouseX, double t) {
        int ty = y + 46;
        int textW = w - 112;
        for (var line : textRenderer.wrapLines(Text.literal("«").append(lineT).append("»").formatted(Formatting.ITALIC), textW)) {
            ctx.drawTextWithShadow(textRenderer, line, x + 12, ty, 0xFFDDDDDD);
            ty += 10;
        }
        if (deal.item().isEmpty()) return;
        int cw = 76, cx = x + w - cw - 14, cy = y + 32;
        product(ctx, deal.item(), cx, cy, cw, mouseX, t);
        int py = Math.max(ty + 6, y + 108);
        ctx.drawTextWithShadow(textRenderer, Text.literal(Ui.ellipsis(deal.item().getName().getString(), textW)), x + 12, py, 0xFFFFFFFF);
        double diff = deal.market() == 0 ? 0 : (deal.price() - deal.market()) * 100.0 / deal.market();
        ctx.drawTextWithShadow(textRenderer, Text.translatable("tcgshop.deal.offer", PriceService.format(deal.price())).formatted(Formatting.BOLD), x + 12, py + 12, Ui.GOLD);
        ctx.drawTextWithShadow(textRenderer, Text.translatable("tcgshop.deal.market", PriceService.format(deal.market())), x + 12, py + 24, Ui.MUTED);
        boolean good = deal.sellToYou() ? diff < 0 : diff > 0;
        ctx.drawTextWithShadow(textRenderer, Text.literal(String.format(Locale.ROOT, "%+.0f%% ", diff)).append(Text.translatable(good ? "tcgshop.deal.good" : "tcgshop.deal.bad")),
                x + 12, py + 36, good ? 0xFF6BE07B : 0xFFFF7A5C);
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (closing != null) { super.close(); return true; }
        for (Btn b : new ArrayList<>(buttons))
            if (Ui.in(mouseX, mouseY, b.x, b.y, b.w, b.h)) {
                if (!waiting) {
                    b.r.run();
                    if (client != null && client.player != null) client.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.4f, 1.2f);
                }
                return true;
            }
        commitTyped(); // click outside the price box: keep what was typed
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double h, double v) {
        if (haggle() && !waiting && closing == null) {
            nudge(v > 0 ? 0.01 : -0.01);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, h, v);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (haggle() && closing == null && !waiting && chr >= '0' && chr <= '9') {
            if (typed == null) typed = "";
            if (typed.length() < 12 && !(typed.isEmpty() && chr == '0')) typed += chr;
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (typed != null) {
            switch (keyCode) {
                case 257, 335 -> { commitTyped(); return true; } // Enter
                case 259 -> { if (!typed.isEmpty()) typed = typed.substring(0, typed.length() - 1); return true; } // Backspace
                case 256 -> { typed = null; return true; } // Esc: stop typing
                default -> {}
            }
        } else if (haggle() && closing == null && !waiting) {
            if (keyCode == 265) { nudge(0.01); return true; } // up arrow
            if (keyCode == 264) { nudge(-0.01); return true; } // down arrow
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}

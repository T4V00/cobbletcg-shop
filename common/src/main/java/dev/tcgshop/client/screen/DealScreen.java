package dev.tcgshop.client.screen;

import dev.masterball.api.platform.Platform;
import dev.masterball.api.platform.ClientPlatform;
import dev.masterball.api.card.CardInfo;
import dev.masterball.api.card.Rarity;
import dev.tcgshop.client.ShopClient;
import dev.tcgshop.client.binder.Ui;
import dev.tcgshop.entity.CustomerEntity;
import dev.tcgshop.network.DealAnswerPayload;
import dev.tcgshop.network.DealOpenPayload;
import dev.tcgshop.pricing.PriceService;
import dev.tcgshop.shop.Charisma;
import dev.tcgshop.shop.ShopZones;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
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
 * 1.1: simplified (see {@link #renderHaggle}): one price row and one row of big buttons with a line saying what each
 * does; a customer whose first offer is already good for you gets charm options instead of haggling.
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
    private Component closing;
    private double closeAt, closingSince;
    private int outcome;
    private long finalPrice;
    /** 1.0 tips: your asking price sent with the close (final price above it = tip); 0 = none. */
    private long closedAsk;
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
        super(Component.translatable("tcgshop.deal.title"));
        this.deal = deal;
        this.counter = suggested(deal);
        history.add(new Hist(false, H_OPEN, deal.price(), -1));
        mood = moodTarget = 0.25f + 0.6f * patienceFrac(deal);
    }

    public int entityId() { return deal.entityId(); }

    private static double now() { return System.nanoTime() / 1e9; }

    private static float patienceFrac(DealOpenPayload p) {
        return p.maxPatience() <= 0 ? 1f : Mth.clamp(p.patience() / (float) p.maxPatience(), 0f, 1f);
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
            closedAsk = p.ask();
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
            if (minecraft != null && minecraft.player != null) {
                if (outcome == CustomerEntity.OUT_DEAL) minecraft.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.6f, 1.2f);
                else if (outcome == CustomerEntity.OUT_WALK || outcome == CustomerEntity.OUT_TIMEOUT)
                    minecraft.player.playSound(SoundEvents.VILLAGER_NO, 0.5f, 1f);
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
        moodTarget = Mth.clamp(0.15f + 0.7f * patienceFrac(p) + react, 0f, 1f);
        boolean stale = youBuy() ? counter >= p.price() : counter <= p.price();
        if (stale || counter <= 0) counter = suggested(p);
        counter = clampCounter(counter);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private void send(int action) {
        if (answered || waiting || closing != null) return;
        commitTyped();
        lastAction = action;
        if (!haggle()) { // without haggling it closes immediately
            if (action != DealAnswerPayload.ACCEPT && action != DealAnswerPayload.REJECT_POLITE && action != DealAnswerPayload.REJECT_RUDE) return;
            answered = true;
            ClientPlatform.INSTANCE.send(new DealAnswerPayload(deal.entityId(), action, counter));
            super.onClose();
            return;
        }
        switch (action) {
            case DealAnswerPayload.ACCEPT -> push(new Hist(true, H_ACCEPT, deal.price(), action));
            case DealAnswerPayload.INTEREST -> push(new Hist(true, H_YOU_THINK, 0, action));
            case DealAnswerPayload.COUNTER -> push(new Hist(true, H_YOU_OFFER, counter, action));
            case DealAnswerPayload.ARG_MARKET, DealAnswerPayload.ARG_ELSEWHERE, DealAnswerPayload.ARG_BROKE, DealAnswerPayload.ARG_MAX,
                 DealAnswerPayload.PERSUADE -> push(new Hist(true, H_ARG, counter, action));
            case DealAnswerPayload.JOKE, DealAnswerPayload.COMPLIMENT, DealAnswerPayload.STORY, DealAnswerPayload.SMALLTALK -> push(new Hist(true, H_ARG, 0, action));
            default -> {}
        }
        waiting = true;
        waitSince = now();
        ClientPlatform.INSTANCE.send(new DealAnswerPayload(deal.entityId(), action, counter));
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

    private void product(GuiGraphics ctx, ItemStack st, int x, int y, int w, int mouseX, double t) {
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
            if (now() > closeAt) super.onClose();
            return;
        }
        // safety net: the NPC no longer exists (left, despawned, went out of range) or the server doesn't reply
        Entity e = minecraft == null || minecraft.level == null ? null : minecraft.level.getEntity(deal.entityId());
        if (e == null || !e.isAlive() || (waiting && now() - waitSince > WAIT_TIMEOUT)) endLocally();
    }

    /** Closes the deal in the window without a server response (same look as a close with no outcome). */
    private void endLocally() {
        waiting = false;
        typed = null;
        closing = Component.translatable("tcgshop.deal.gone");
        outcome = CustomerEntity.OUT_UNKNOWN;
        finalPrice = 0;
        closedAsk = 0;
        closingSince = now();
        closeAt = closingSince + 2.5;
    }

    /** 1.0 tips: the tip in the current offer or, once closed with a deal, the one included in the final price. */
    private long tipShown() {
        if (closing != null)
            return outcome == CustomerEntity.OUT_DEAL && closedAsk > 0 && finalPrice > closedAsk ? finalPrice - closedAsk : 0;
        return youBuy() ? 0 : deal.tip();
    }

    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        double t = now();
        buttons.clear();
        tip = null;
        mood += (moodTarget - mood) * Math.min(1f, delta * 0.15f);
        float in = (float) Draw.easeOutBack(Mth.clamp((t - opened) / 0.3, 0, 1));
        ctx.fillGradient(0, 0, width, height, 0xA0000000, 0xD0000000);
        int[] b = box();
        int x = b[0], y = b[1], w = b[2], h = b[3];
        var m = ctx.pose();
        m.pushPose();
        m.translate(x + w / 2f, y + h / 2f, 0);
        m.scale(0.85f + 0.15f * in, 0.85f + 0.15f * in, 1);
        m.translate(-(x + w / 2f), -(y + h / 2f), 0);
        int accent = switch (deal.mode()) { case DealOpenPayload.SELL -> 0xFF2E9C5A; case DealOpenPayload.TRADE -> 0xFF8E44AD; default -> 0xFFD39B1A; };
        ctx.fill(x + 4, y + 6, x + w + 4, y + h + 6, 0x66000000);
        Ui.roundGradient(ctx, x, y, w, h, 0xFF23273A, 0xFF12141F);
        Ui.roundGradient(ctx, x, y, w, 24, Draw.withAlpha(accent, 0.95f), Draw.withAlpha(Ui.darken(accent, 40), 0.95f));
        String titleKey = switch (deal.mode()) { case DealOpenPayload.SELL -> "tcgshop.deal.sell_title"; case DealOpenPayload.TRADE -> "tcgshop.deal.trade_title";
            default -> "tcgshop.deal.buy_title"; };
        Draw.scaledCentered(ctx, Component.translatable(titleKey).withStyle(ChatFormatting.BOLD), x + w / 2f, y + 8, 1.1f, 0xFFFFFFFF);
        if (haggle() && closing == null) charismaChip(ctx, x + 8, y + 6, mouseX, mouseY);

        if (haggle()) renderHaggle(ctx, x, y, w, h, mouseX, mouseY, t, in);
        else {
            ctx.drawString(font, deal.name().copy().withStyle(ChatFormatting.YELLOW), x + 12, y + 32, 0xFFFFFFFF);
            Component line = closing != null ? closing : deal.line();
            if (deal.mode() == DealOpenPayload.TRADE) renderTrade(ctx, x, y, w, line, mouseX, t);
            else renderOffer(ctx, x, y, w, line, mouseX, t);
            if (closing != null) {
                float a = (float) Mth.clamp((closeAt - t) / 0.6, 0, 1);
                Ui.text(ctx, Component.translatable("tcgshop.deal.closed").withStyle(ChatFormatting.ITALIC), x, y + h - 22, w, 10, Draw.withAlpha(0xFFAAB0C8, a), false);
            } else {
                int by = y + h - 28;
                Ui.button(ctx, x + 14, by, 130, 20, Component.translatable("tcgshop.deal.accept"), mouseX, mouseY, true, 0xFF3C8A4A);
                Ui.button(ctx, x + w - 144, by, 130, 20, Component.translatable("tcgshop.deal.reject"), mouseX, mouseY, true, 0xFF7A2020);
                buttons.add(new Btn(x + 14, by, 130, 20, () -> send(DealAnswerPayload.ACCEPT)));
                buttons.add(new Btn(x + w - 144, by, 130, 20, () -> send(DealAnswerPayload.REJECT_POLITE)));
            }
        }
        m.popPose();
    }

    // ------------------------------------------------------------------ haggling

    /**
     * 1.1: simpler haggling window for players with little patience: the NPC and its line on top, ONE row (your price,
     * or a green "great offer" banner when the customer is already generous) and ONE row of big buttons with a short line
     * under each saying what it does. Normal deals: Accept / Offer your price / Persuade (charisma) / No thanks. Generous
     * customers: Accept / Joke / Compliment / Story (with their success chance) / No thanks, because haggling would make
     * them leave.
     */
    private void renderHaggle(GuiGraphics ctx, int x, int y, int w, int h, int mx, int my, double t, float in) {
        boolean gen = deal.generous();
        int footY = y + h - 12;
        int descY = footY - 12;
        int btnH = 24, btnY = descY - 2 - btnH;
        int rowH = 18, rowY = btnY - 6 - rowH;
        int top = y + 28, topEnd = rowY - 6;

        // ---- left column: portrait, patience and mood
        int lw = 70, lx = x + 8;
        int portraitH = Math.max(40, topEnd - top - 26);
        portrait(ctx, lx, top, lw, portraitH, mx, my, in);
        int py = top + portraitH + 3;
        Ui.text(ctx, Component.translatable("tcgshop.haggle.patience"), lx, py, lw, 9, 0xFFAAB0C8, false);
        float pf = patienceFrac(deal);
        Ui.progress(ctx, lx + 2, py + 9, lw - 4, 5, closing != null && outcome != CustomerEntity.OUT_DEAL ? 0 : pf,
                pf > 0.6f ? 0xFF43C463 : pf > 0.3f ? 0xFFFFC23D : 0xFFE05050);
        int mc = moodColor();
        Ui.text(ctx, Component.translatable("tcgshop.haggle.mood." + Math.min(4, (int) (mood * 5))), lx, py + 15, lw, 9, mc, true);

        // ---- right column: the product
        int cw = 62, cx = x + w - 8 - cw, ch = Math.round(cw / Draw.CARD_RATIO);
        if (!deal.item().isEmpty()) {
            int cy = top + Math.max(0, (topEnd - top - 12 - ch) / 2);
            product(ctx, deal.item(), cx, cy, cw, mx, t);
            Ui.text(ctx, Component.literal(Ui.ellipsis(deal.item().getHoverName().getString(), (int) ((cw + 10) / 0.8f))), cx - 5, cy + ch + 2, cw + 10, 9, 0xFFFFFFFF, true);
        }

        // ---- center: name + personality, speech bubble and the prices
        int mx0 = lx + lw + 8, mw = cx - 8 - mx0;
        ctx.drawString(font, Component.literal(Ui.ellipsis(deal.name().getString(), mw - 80)).withStyle(ChatFormatting.YELLOW), mx0, top + 1, 0xFFFFFFFF);
        personaChip(ctx, mx0 + Math.min(mw - 80, font.width(deal.name())) + 6, top);

        int bubY = top + 13, bubH = Math.max(24, Math.min(52, (topEnd - bubY) - 50));
        float shake = (float) Mth.clamp(1 - (t - shakeAt) / 0.45, 0, 1);
        int sx = shake > 0 ? Math.round((float) Math.sin(t * 60) * 3 * shake) : 0;
        bubble(ctx, mx0 + sx, bubY, mw, bubH, closing != null ? closing : deal.line(), t);

        int iy = bubY + bubH + 4, ih = topEnd - iy;
        info(ctx, mx0, iy, mw, ih, t);

        if (closing != null) {
            outcomeBanner(ctx, x + 10, rowY, w - 20, footY + 9 - rowY, t);
            return;
        }
        if (gen) generousBanner(ctx, x + 10, rowY, w - 20, rowH, t);
        else priceRow(ctx, x, rowY, w, mx, my);
        bigButtons(ctx, x + 10, btnY, w - 20, btnH, descY, mx, my, gen);
        Component foot = tip != null ? Component.translatable(tip) : Component.translatable(gen ? "tcgshop.haggle.hint_generous" : "tcgshop.haggle.hint_simple");
        Ui.text(ctx, foot, x + 6, footY, w - 12, 10, tip != null ? 0xFFE8E2C8 : 0xFF8E94B0, false);
        if (waiting) {
            String dots = ".".repeat(1 + (int) (t * 3) % 3);
            Ui.text(ctx, Component.translatable("tcgshop.deal.thinking").append(dots), x + w - 90, y + 26 - 12, 84, 10, 0xFFE8E8F0, true);
        }
    }

    /** 1.1: your charisma level from your shop's zone sync (0 if unknown). */
    private int charismaLevel() {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null) return 0;
        for (ShopClient.ZoneInfo z : ShopClient.zones)
            if (z.owner().equals(mc.player.getUUID()) && z.ext() != null && z.ext().contains("charisma")) return z.ext().getInt("charisma");
        return 0;
    }

    /** 1.1: the customer is already generous: a green banner saying not to haggle, and the charm attempts left. */
    private void generousBanner(GuiGraphics ctx, int x, int y, int w, int h, double t) {
        float pulse = (float) (0.5 + 0.5 * Math.sin(t * 3));
        Ui.roundGradient(ctx, x, y, w, h, Draw.withAlpha(0xFF2E9C5A, 0.55f + 0.2f * pulse), Draw.withAlpha(0xFF1A5A34, 0.7f));
        Ui.outline(ctx, x, y, w, h, 0xFF6BE07B);
        Component left = Component.translatable("tcgshop.haggle.charms_left", deal.charmsLeft());
        int lw = font.width(left);
        Component msg = Component.literal("★ ").append(Component.translatable(youBuy() ? "tcgshop.haggle.generous_sell" : "tcgshop.haggle.generous_buy"))
                .withStyle(ChatFormatting.BOLD);
        line(ctx, msg, x + 6, y + 5, w - lw - 18, 0xFFFFFFFF);
        ctx.drawString(font, left, x + w - lw - 6, y + 5, 0xFFDDFFE4, false);
    }

    /** 1.1: your price: [-5%] [-1%] [price (click to type)] [+1%] [+5%] and what it is of the market. */
    private void priceRow(GuiGraphics ctx, int x, int y, int w, int mx, int my) {
        Component lbl = Component.translatable(youBuy() ? "tcgshop.haggle.your_offer" : "tcgshop.haggle.your_ask").withStyle(ChatFormatting.BOLD);
        String[] steps = {"-5%", "-1%", "+1%", "+5%"};
        double[] pct = {-0.05, -0.01, 0.01, 0.05};
        String cs = typed != null ? (typed.isEmpty() ? "" : PriceService.format(parseOr(typed))) : PriceService.format(counter);
        int boxW = Math.max(90, font.width(cs) + 16);
        long market = Math.max(1, deal.market());
        Component ofm = Component.translatable("tcgshop.haggle.of_market", Math.round(counter * 100.0 / market) + "%");
        int total = font.width(lbl) + 8 + boxW + 6 + 8 + font.width(ofm);
        for (String s : steps) total += font.width(s) + 10 + 3;
        int bx = x + (w - total) / 2;
        ctx.drawString(font, lbl, bx, y + 5, 0xFFFFFFFF);
        bx += font.width(lbl) + 8;
        for (int i = 0; i < 2; i++) bx = step(ctx, bx, y + 1, steps[i], pct[i], mx, my);
        boolean hover = Ui.in(mx, my, bx, y, boxW, rowHeight());
        Ui.round(ctx, bx, y, boxW, rowHeight(), typed != null ? 0xFF1A1E2C : 0xFF0E1018);
        Ui.outline(ctx, bx, y, boxW, rowHeight(), typed != null ? 0xFFFFFFFF : hover ? Ui.brighten(Ui.GOLD, 30) : Ui.GOLD);
        String shown = cs + (typed != null && (now() * 2) % 2 < 1 ? "_" : "");
        Ui.text(ctx, Component.literal(shown).withStyle(ChatFormatting.BOLD), bx, y + 5, boxW, 10, typed != null ? 0xFFFFFFFF : Ui.GOLD, false);
        if (hover) tip = "tcgshop.haggle.tip.type";
        buttons.add(new Btn(bx, y, boxW, rowHeight(), () -> typed = typed == null ? "" : typed));
        bx += boxW + 3;
        for (int i = 2; i < 4; i++) bx = step(ctx, bx, y + 1, steps[i], pct[i], mx, my);
        ctx.drawString(font, ofm, bx + 5, y + 5, 0xFFAAB0C8, false);
    }

    private static int rowHeight() { return 18; }

    private static long parseOr(String s) {
        try { return Long.parseLong(s); } catch (NumberFormatException e) { return 0; }
    }

    private int step(GuiGraphics ctx, int x, int y, String label, double pct, int mx, int my) {
        int bw = font.width(label) + 10;
        Ui.button(ctx, x, y, bw, 16, Component.literal(label), mx, my, !waiting, pct < 0 ? 0xFF5A3A4E : 0xFF39415E);
        buttons.add(new Btn(x, y, bw, 16, () -> nudge(pct)));
        return x + bw + 3;
    }

    /** 1.1: one row of big buttons, each with a one-line description under it. */
    private void bigButtons(GuiGraphics ctx, int x, int y, int w, int bh, int descY, int mx, int my, boolean gen) {
        record B(Component label, String desc, int color, int action, String tipKey, boolean enabled) {}
        List<B> bs = new ArrayList<>();
        int lvl = Math.max(1, charismaLevel());
        Component acc = tipShown() > 0 ? Component.translatable("tcgshop.haggle.a.accept_tip", PriceService.format(deal.price()), PriceService.format(tipShown()))
                : Component.translatable("tcgshop.haggle.a.accept", PriceService.format(deal.price()));
        bs.add(new B(acc, "tcgshop.haggle.d.accept", 0xFF2E8A4A, DealAnswerPayload.ACCEPT, "accept", true));
        if (gen) {
            int[] colors = {0xFFB8741A, 0xFFB03A78, 0xFF3A6FD8, 0xFF2F8C8C};
            for (int k = 0; k < Charisma.CHARM_COUNT; k++) {
                boolean used = deal.charmUsed(k), can = !used && deal.charmsLeft() > 0;
                // the server's real chance (mood and server config included); the local estimate only if it was not sent
                int pct = deal.oddsPct(k) >= 0 ? deal.oddsPct(k) : (int) Math.round(Charisma.charmChance(k, lvl, deal.persona()) * 100);
                String key = Charisma.CHARM_KEYS[k];
                bs.add(new B(Component.translatable("tcgshop.haggle.a." + key, pct + "%"),
                        used ? "tcgshop.haggle.d.used" : !can ? "tcgshop.haggle.d.no_charms" : "tcgshop.haggle.d." + key + (youBuy() ? "_sell" : ""),
                        colors[k], k == Charisma.CHARM_SMALLTALK ? DealAnswerPayload.SMALLTALK : DealAnswerPayload.JOKE + k,
                        key + (youBuy() ? "_sell" : ""), can));
            }
        } else {
            bs.add(new B(Component.translatable(youBuy() ? "tcgshop.haggle.a.counter_buy" : "tcgshop.haggle.a.counter_sell", PriceService.format(counter)),
                    youBuy() ? "tcgshop.haggle.d.counter_buy" : "tcgshop.haggle.d.counter_sell", 0xFF3A6FD8, DealAnswerPayload.COUNTER, "counter", true));
            int pct = deal.oddsPct(DealOpenPayload.ODDS_PERSUADE) >= 0 ? deal.oddsPct(DealOpenPayload.ODDS_PERSUADE)
                    : (int) Math.round(Charisma.persuadeChance(lvl) * 100);
            bs.add(new B(Component.translatable("tcgshop.haggle.a.persuade", pct + "%"), "tcgshop.haggle.d.persuade", 0xFF7B3FA8,
                    DealAnswerPayload.PERSUADE, "persuade", true));
        }
        bs.add(new B(Component.translatable("tcgshop.haggle.a.no"), "tcgshop.haggle.d.no", 0xFF5A2A2A, DealAnswerPayload.REJECT_POLITE, "no", true));
        int n = bs.size(), gap = 5, bw = (w - gap * (n - 1)) / n;
        for (int i = 0; i < n; i++) {
            B b = bs.get(i);
            int bx = x + i * (bw + gap);
            answer(ctx, bx, y, bw, bh, b.label().copy().withStyle(ChatFormatting.BOLD), b.color(), b.action(), mx, my, b.enabled());
            Component d = Component.translatable(b.desc());
            float s = Math.min(0.75f, (bw - 2) / (float) Math.max(1, font.width(d)));
            var m = ctx.pose();
            m.pushPose();
            m.translate(bx + bw / 2f, descY + 1, 0);
            m.scale(s, s, 1);
            ctx.drawString(font, d, -font.width(d) / 2, 0, b.enabled() ? 0xFFC8CCE0 : 0xFF6E7490, false);
            m.popPose();
            if (Ui.in(mx, my, bx, y, bw, bh)) tip = "tcgshop.haggle.tip." + b.tipKey();
        }
    }

    private void answer(GuiGraphics ctx, int x, int y, int w, int h, Component label, int color, int action, int mx, int my, boolean enabled) {
        boolean on = enabled && !waiting;
        Ui.button(ctx, x, y, w, h, Component.empty(), mx, my, on, on ? color : Ui.darken(color, 60));
        float s = Math.min(1f, (w - 8) / (float) Math.max(1, font.width(label)));
        var m = ctx.pose();
        m.pushPose();
        m.translate(x + w / 2f, y + h / 2f - 3.5f * s, 0);
        m.scale(s, s, 1);
        ctx.drawString(font, label, -font.width(label) / 2, 0, on ? 0xFFFFFFFF : 0xFF8890A8);
        m.popPose();
        if (enabled) buttons.add(new Btn(x, y, w, h, () -> send(action)));
    }

    /**
     * 1.0 charisma: your level and haggling bonus (from your shop's zone sync, ShopMissions.writeSync) as a chip in the
     * header; hovering it explains it in the footer. Nothing is drawn without a synced shop.
     */
    private void charismaChip(GuiGraphics ctx, int x, int y, int mx, int my) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null) return;
        java.util.UUID me = mc.player.getUUID();
        int lvl = 0, bonus = 0;
        for (ShopClient.ZoneInfo z : ShopClient.zones) {
            if (!z.owner().equals(me) || z.ext() == null || !z.ext().contains("charisma")) continue;
            lvl = z.ext().getInt("charisma");
            bonus = z.ext().getInt("charisma_bonus");
            break;
        }
        if (lvl <= 0) return;
        Component label = Component.translatable("tcgshop.charisma.chip", lvl, String.format(Locale.ROOT, "+%.1f%%", bonus / 10.0));
        int cw = font.width(label) + 10;
        boolean hover = Ui.in(mx, my, x, y, cw, 12);
        Ui.round(ctx, x, y, cw, 12, hover ? 0xAA7B3FA8 : 0x775A2C80);
        Ui.outline(ctx, x, y, cw, 12, 0x99E0B0FF);
        ctx.drawString(font, label, x + 5, y + 2, 0xFFF0DDFF, false);
        if (hover) tip = "tcgshop.charisma.deal_tip";
    }

    private int moodColor() {
        return mood < 0.5f ? Ui.lerpC(0xFFE05050, 0xFFFFD34D, mood * 2) : Ui.lerpC(0xFFFFD34D, 0xFF43C463, (mood - 0.5f) * 2);
    }

    private void portrait(GuiGraphics ctx, int px, int py, int pw, int ph, int mx, int my, float in) {
        Ui.roundGradient(ctx, px, py, pw, ph, 0xFF2D3350, 0xFF181B2A);
        ctx.fillGradient(px + 1, py + ph / 2, px + pw - 1, py + ph - 1, 0x00000000, Draw.withAlpha(moodColor(), 0.35f));
        Ui.outline(ctx, px, py, pw, ph, 0x55FFFFFF);
        Entity e = minecraft != null && minecraft.level != null ? minecraft.level.getEntity(deal.entityId()) : null;
        // only once the window is no longer animating (the portrait clipping does not follow the scale)
        if (in >= 0.999f && e instanceof LivingEntity le) {
            int size = Math.max(12, Math.round(ph * 0.4f));
            InventoryScreen.renderEntityInInventoryFollowsMouse(ctx, px + 1, py + 1, px + pw - 1, py + ph - 1, size, 0.0625f, mx, my, le);
        } else if (!(e instanceof LivingEntity)) {
            Draw.scaledCentered(ctx, Component.literal("?").withStyle(ChatFormatting.BOLD), px + pw / 2f, py + ph / 2f - 8, 2.2f, 0x88FFFFFF);
        }
    }

    private void personaChip(GuiGraphics ctx, int x, int y) {
        int pi = Math.min(CustomerEntity.PERSONAS.length - 1, Math.max(0, deal.persona()));
        Component p = Component.translatable((youBuy() ? "tcgshop.persona." : "tcgshop.haggle.buyer.") + CustomerEntity.PERSONAS[pi]);
        int pw = font.width(p) + 8;
        Ui.round(ctx, x, y - 1, pw, 11, 0x553A6FD8);
        ctx.drawString(font, p, x + 4, y + 1, 0xFFBFD6FF, false);
    }

    /** Speech bubble with the NPC's line: if it doesn't fit, the font gets smaller. */
    private void bubble(GuiGraphics ctx, int x, int y, int w, int h, Component line, double t) {
        Ui.roundGradient(ctx, x, y, w, h, 0xFFF4F1E8, 0xFFDCD6C6);
        ctx.fill(x + 10, y - 2, x + 16, y, 0xFFF4F1E8); // little tail pointing to the name
        Ui.outline(ctx, x, y, w, h, 0xFF8A8270);
        Component q = Component.literal("«").append(line).append("»").withStyle(ChatFormatting.ITALIC);
        int inner = w - 10, avail = h - 6;
        List<FormattedCharSequence> lines = font.split(q, inner);
        float s = 1f;
        for (int i = 0; i < 4 && lines.size() * 10 * s > avail; i++) {
            s *= 0.85f;
            lines = font.split(q, (int) (inner / s));
        }
        var m = ctx.pose();
        m.pushPose();
        m.translate(x + 5, y + 4, 0);
        m.scale(s, s, 1);
        int ly = 0;
        for (FormattedCharSequence l : lines) {
            if ((ly + 9) * s > avail + 1) break;
            ctx.drawString(font, l, 0, ly, 0xFF2A2620, false);
            ly += 10;
        }
        m.popPose();
        if (waiting) {
            String dots = ".".repeat(1 + (int) (t * 3) % 3);
            ctx.drawString(font, dots, x + w - 14, y + h - 9, 0xFF6A6250, false);
        }
    }

    /** What is asked/offered, the market, your margin and what people overpay in your shop thanks to its reputation. */
    private void info(GuiGraphics ctx, int x, int y, int w, int h, double t) {
        Ui.roundGradient(ctx, x, y, w, h, 0xFF1C2030, 0xFF151824);
        Ui.outline(ctx, x, y, w, h, 0x33FFFFFF);
        int tx = x + 5, ty = y + 4, tw = w - 10;
        float fl = (float) Mth.clamp(1 - (t - flashAt) / 0.8, 0, 1);
        int priceCol = fl > 0 ? Ui.lerpC(Ui.GOLD, 0xFF6BFF8A, fl) : Ui.GOLD;
        long price = closing != null && outcome == CustomerEntity.OUT_DEAL && finalPrice > 0 ? finalPrice : deal.price();
        long tipAmt = tipShown();
        Component first = tipAmt > 0 // 1.0: offer = your asking price + tip
                ? Component.translatable("tcgshop.deal.offer_tip", PriceService.format(price), PriceService.format(price - tipAmt), PriceService.format(tipAmt))
                : Component.translatable(youBuy() ? "tcgshop.deal.asks" : "tcgshop.deal.offer", PriceService.format(price));
        line(ctx, first.copy().withStyle(ChatFormatting.BOLD), tx, ty, tw, priceCol);
        ty += 10;
        if (ty + 9 > y + h) return;
        double diff = deal.market() == 0 ? 0 : (price - deal.market()) * 100.0 / deal.market();
        boolean good = youBuy() ? diff < 0 : diff > 0;
        line(ctx, Component.translatable("tcgshop.haggle.market_diff", PriceService.format(deal.market()), String.format(Locale.ROOT, "%+.0f%%", diff)),
                tx, ty, tw, good ? 0xFF8BE08B : 0xFFFF8F7A);
        ty += 10;
        if (ty + 9 > y + h) return;
        int rep = shopRep();
        double prem = rep < 0 ? 0 : ShopZones.repPremium(rep, ShopZones.isSealed(deal.item()));
        long margin;
        Component mt;
        if (youBuy()) { // if you resell it at market (plus what your customers overpay for your reputation)
            long resale = Math.round(deal.market() * (1 + Math.max(0, prem)));
            margin = resale - price;
            mt = Component.translatable("tcgshop.haggle.margin_buy", signed(margin));
        } else {
            margin = price - deal.market();
            mt = Component.translatable("tcgshop.haggle.margin_sell", signed(margin));
        }
        line(ctx, mt, tx, ty, tw, margin >= 0 ? 0xFF6BE07B : 0xFFFF7A5C);
        ty += 10;
        if (ty + 9 > y + h || rep < 0) return;
        line(ctx, Component.translatable("tcgshop.haggle.rep_line", rep, String.format(Locale.ROOT, "%+.0f%%", prem * 100)), tx, ty, tw,
                prem > 0.005 ? 0xFF9FD8FF : prem < -0.005 ? 0xFFFF9F7A : 0xFF9A9AAE);
    }

    private void line(GuiGraphics ctx, Component text, int x, int y, int w, int color) {
        int tw = font.width(text);
        if (tw <= w) { ctx.drawString(font, text, x, y, color); return; }
        float s = w / (float) tw;
        var m = ctx.pose();
        m.pushPose();
        m.translate(x, y + (1 - s) * 4, 0);
        m.scale(s, s, 1);
        ctx.drawString(font, text, 0, 0, color);
        m.popPose();
    }

    private static String signed(long v) {
        return (v >= 0 ? "+" : "-") + PriceService.format(Math.abs(v));
    }

    /** Reputation of the shop the NPC is in (or your best one); -1 if unknown. */
    private int shopRep() {
        if (minecraft == null || minecraft.level == null || minecraft.player == null) return -1;
        Entity e = minecraft.level.getEntity(deal.entityId());
        Vec3 at = e != null ? e.position() : minecraft.player.position();
        int best = -1;
        for (ShopClient.ZoneInfo z : ShopClient.zones) {
            if (!z.dormant() && z.box().contains(at)) return z.reputation();
            if (z.owner().equals(minecraft.player.getUUID())) best = Math.max(best, z.reputation());
        }
        return best;
    }

    /** Deal outcome: done (green), they left (orange), you declined (gray) or they got fed up (red). */
    private void outcomeBanner(GuiGraphics ctx, int x, int y, int w, int h, double t) {
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
        float pop = (float) Draw.easeOutBack(Mth.clamp((t - closingSince) / 0.35, 0, 1));
        Ui.roundGradient(ctx, x, y, w, h, Draw.withAlpha(col, 0.55f), Draw.withAlpha(Ui.darken(col, 50), 0.55f));
        Ui.outline(ctx, x, y, w, h, Ui.brighten(col, 40));
        Draw.scaledCentered(ctx, Component.literal(icon).withStyle(ChatFormatting.BOLD), x + 24, y + h / 2f - 9 * pop, 2.4f * pop, 0xFFFFFFFF);
        int tx = x + 48, tw = w - 56;
        Component title = switch (outcome) {
            case CustomerEntity.OUT_DEAL -> Component.translatable(youBuy() ? "tcgshop.haggle.out.deal_buy" : "tcgshop.haggle.out.deal_sell", PriceService.format(finalPrice));
            case CustomerEntity.OUT_WALK -> Component.translatable("tcgshop.haggle.out.walk");
            case CustomerEntity.OUT_REJECT -> Component.translatable("tcgshop.haggle.out.reject");
            case CustomerEntity.OUT_TIMEOUT -> Component.translatable("tcgshop.haggle.out.timeout");
            default -> Component.translatable("tcgshop.deal.closed");
        };
        line(ctx, title.copy().withStyle(ChatFormatting.BOLD), tx, y + h / 2 - 14, tw, 0xFFFFFFFF);
        Component sub;
        if (outcome == CustomerEntity.OUT_DEAL && deal.market() > 0) {
            double diff = (finalPrice - deal.market()) * 100.0 / deal.market();
            boolean good = youBuy() ? diff <= 0 : diff >= 0;
            long tipAmt = tipShown();
            sub = tipAmt > 0 ? Component.translatable("tcgshop.haggle.out.tip", PriceService.format(tipAmt), String.format(Locale.ROOT, "%+.0f%%", diff)) // 1.0
                    : Component.translatable(good ? "tcgshop.haggle.out.good" : "tcgshop.haggle.out.bad", String.format(Locale.ROOT, "%+.0f%%", diff));
        } else sub = Component.translatable("tcgshop.haggle.out.sub." + Math.max(0, Math.min(4, outcome)));
        line(ctx, sub, tx, y + h / 2 - 2, tw, 0xFFE8E8F0);
        float left = (float) Mth.clamp((closeAt - t) / Math.max(0.1, closeAt - closingSince), 0, 1);
        Ui.progress(ctx, tx, y + h / 2 + 10, Math.min(tw, 160), 4, left, Ui.brighten(col, 30));
        ctx.drawString(font, Component.translatable("tcgshop.haggle.click_close"), tx + Math.min(tw, 160) + 6, y + h / 2 + 8, 0xFFCCCCCC, false);
    }

    // ------------------------------------------------------------------ deals without haggling

    private void renderTrade(GuiGraphics ctx, int x, int y, int w, Component lineT, int mouseX, double t) {
        int ty = y + 44;
        for (var line : font.split(Component.literal("«").append(lineT).append("»").withStyle(ChatFormatting.ITALIC), w - 24)) {
            ctx.drawString(font, line, x + 12, ty, 0xFFDDDDDD);
            ty += 10;
        }
        if (deal.item().isEmpty()) return;
        int cw = 66, ch = Math.round(cw / Draw.CARD_RATIO), cy = Math.max(ty + 4, y + 66);
        int lx = x + w / 4 - cw / 2, rx = x + 3 * w / 4 - cw / 2;
        Draw.scaledCentered(ctx, Component.translatable("tcgshop.deal.you_get"), lx + cw / 2f, cy - 10, 1f, 0xFF6BE07B);
        Draw.scaledCentered(ctx, Component.translatable("tcgshop.deal.you_give"), rx + cw / 2f, cy - 10, 1f, 0xFFFF9F6B);
        product(ctx, deal.item(), lx, cy, cw, mouseX, t);
        product(ctx, deal.want(), rx, cy, cw, mouseX, t);
        float pulse = (float) (0.5 + 0.5 * Math.sin(t * 4));
        Draw.scaledCentered(ctx, Component.literal("⇄").withStyle(ChatFormatting.BOLD), x + w / 2f, cy + ch / 2f - 8, 2.4f, Draw.withAlpha(Ui.GOLD, 0.6f + 0.4f * pulse));
        int iy = cy + ch + 4;
        Ui.text(ctx, Component.literal(Ui.ellipsis(deal.item().getHoverName().getString(), w / 2 - 10)), x + 4, iy, w / 2 - 8, 10, 0xFFFFFFFF, true);
        Ui.text(ctx, Component.literal(Ui.ellipsis(deal.want().getHoverName().getString(), w / 2 - 10)), x + w / 2 + 4, iy, w / 2 - 8, 10, 0xFFFFFFFF, true);
        Ui.text(ctx, Component.literal(PriceService.format(deal.market())), x + 4, iy + 11, w / 2 - 8, 10, Ui.GOLD, true);
        Ui.text(ctx, Component.literal(PriceService.format(deal.wantMarket())), x + w / 2 + 4, iy + 11, w / 2 - 8, 10, Ui.GOLD, true);
        double diff = deal.wantMarket() == 0 ? 0 : (deal.market() - deal.wantMarket()) * 100.0 / deal.wantMarket();
        boolean good = diff >= 0;
        Draw.scaledCentered(ctx, Component.literal(String.format(Locale.ROOT, "%+.0f%% ", diff)).append(Component.translatable(good ? "tcgshop.deal.good" : "tcgshop.deal.bad")),
                x + w / 2f, iy + 24, 1f, good ? 0xFF6BE07B : 0xFFFF7A5C);
    }

    private void renderOffer(GuiGraphics ctx, int x, int y, int w, Component lineT, int mouseX, double t) {
        int ty = y + 46;
        int textW = w - 112;
        for (var line : font.split(Component.literal("«").append(lineT).append("»").withStyle(ChatFormatting.ITALIC), textW)) {
            ctx.drawString(font, line, x + 12, ty, 0xFFDDDDDD);
            ty += 10;
        }
        if (deal.item().isEmpty()) return;
        int cw = 76, cx = x + w - cw - 14, cy = y + 32;
        product(ctx, deal.item(), cx, cy, cw, mouseX, t);
        int py = Math.max(ty + 6, y + 108);
        ctx.drawString(font, Component.literal(Ui.ellipsis(deal.item().getHoverName().getString(), textW)), x + 12, py, 0xFFFFFFFF);
        double diff = deal.market() == 0 ? 0 : (deal.price() - deal.market()) * 100.0 / deal.market();
        long tipAmt = tipShown();
        Component offer = tipAmt > 0 // 1.0: offer = your asking price + tip
                ? Component.translatable("tcgshop.deal.offer_tip", PriceService.format(deal.price()), PriceService.format(deal.price() - tipAmt), PriceService.format(tipAmt))
                : Component.translatable("tcgshop.deal.offer", PriceService.format(deal.price()));
        ctx.drawString(font, offer.copy().withStyle(ChatFormatting.BOLD), x + 12, py + 12, Ui.GOLD);
        ctx.drawString(font, Component.translatable("tcgshop.deal.market", PriceService.format(deal.market())), x + 12, py + 24, Ui.MUTED);
        boolean good = deal.sellToYou() ? diff < 0 : diff > 0;
        ctx.drawString(font, Component.literal(String.format(Locale.ROOT, "%+.0f%% ", diff)).append(Component.translatable(good ? "tcgshop.deal.good" : "tcgshop.deal.bad")),
                x + 12, py + 36, good ? 0xFF6BE07B : 0xFFFF7A5C);
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (closing != null) { super.onClose(); return true; }
        for (Btn b : new ArrayList<>(buttons))
            if (Ui.in(mouseX, mouseY, b.x, b.y, b.w, b.h)) {
                if (!waiting) {
                    b.r.run();
                    if (minecraft != null && minecraft.player != null) minecraft.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.4f, 1.2f);
                }
                return true;
            }
        commitTyped(); // click outside the price box: keep what was typed
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double h, double v) {
        if (haggle() && !deal.generous() && !waiting && closing == null) {
            nudge(v > 0 ? 0.01 : -0.01);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, h, v);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (haggle() && !deal.generous() && closing == null && !waiting && chr >= '0' && chr <= '9') {
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
        } else if (haggle() && !deal.generous() && closing == null && !waiting) {
            if (keyCode == 265) { nudge(0.01); return true; } // up arrow
            if (keyCode == 264) { nudge(-0.01); return true; } // down arrow
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}

package dev.tcgshop.client.screen;

import dev.tcgshop.card.CardInfo;
import dev.tcgshop.card.Rarity;
import dev.tcgshop.client.binder.Ui;
import dev.tcgshop.network.DealAnswerPayload;
import dev.tcgshop.network.DealOpenPayload;
import dev.tcgshop.pricing.PriceService;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Trato con un NPC: te vende algo, quiere comprarte un producto o cambiarte una carta.
 * Con quien te VENDE algo se puede regatear: eliges tu contraoferta y una respuesta (argumentar el precio de mercado,
 * decir que no encontrará nada mejor, que no puedes pagar más, que es tu última oferta, pensártelo...) y el comerciante
 * reacciona según su personalidad, su motivo y su paciencia. Rechazar con educación casi siempre deja una reseña
 * neutral; tratarle mal, no.
 */
public class DealScreen extends Screen {
    private DealOpenPayload deal;
    private double opened = System.nanoTime() / 1e9;
    private boolean answered, waiting;
    private long counter;
    /** Trato terminado: última frase y cuándo cerrar. */
    private Text closing;
    private double closeAt;
    private final List<Btn> buttons = new ArrayList<>();
    private double flashAt = -10;

    private record Btn(int x, int y, int w, int h, Runnable r) {}

    public DealScreen(DealOpenPayload deal) {
        super(Text.translatable("tcgshop.deal.title"));
        this.deal = deal;
        this.counter = suggested(deal.price());
    }

    public int entityId() { return deal.entityId(); }

    private static long suggested(long ask) {
        return Math.max(1, Math.round(ask * 0.85));
    }

    /** Respuesta del comerciante (contraoferta, enfado, cierre...). */
    public void update(DealOpenPayload p) {
        waiting = false;
        if (p.mode() == DealOpenPayload.CLOSED) {
            closing = p.line();
            closeAt = System.nanoTime() / 1e9 + 2.6;
            return;
        }
        boolean priceChanged = p.price() != deal.price();
        deal = p;
        if (counter >= p.price() || counter <= 0) counter = suggested(p.price());
        if (priceChanged) flashAt = System.nanoTime() / 1e9;
    }

    @Override
    public boolean shouldPause() { return false; }

    private boolean haggle() { return deal.mode() == DealOpenPayload.SELL && deal.persona() >= 0; }

    private void send(int action) {
        if (answered || waiting || closing != null) return;
        if (action == DealAnswerPayload.ACCEPT || action == DealAnswerPayload.REJECT_POLITE || action == DealAnswerPayload.REJECT_RUDE) {
            if (!haggle()) answered = true; // sin regateo se cierra al momento; con regateo, el servidor contesta
        }
        waiting = haggle();
        ClientPlayNetworking.send(new DealAnswerPayload(deal.entityId(), action, counter));
        if (answered) super.close();
    }

    private int[] box() {
        int w = Math.min(width - 20, haggle() ? 400 : deal.mode() == DealOpenPayload.TRADE ? 380 : 340);
        int h = haggle() ? Math.min(height - 16, 286) : deal.mode() == DealOpenPayload.TRADE ? 230 : 196;
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
        if (closing != null && System.nanoTime() / 1e9 > closeAt) super.close();
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        double t = System.nanoTime() / 1e9;
        buttons.clear();
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
        Ui.roundGradient(ctx, x, y, w, 26, Draw.withAlpha(accent, 0.95f), Draw.withAlpha(Ui.darken(accent, 40), 0.95f));
        String titleKey = switch (deal.mode()) { case DealOpenPayload.SELL -> "tcgshop.deal.sell_title"; case DealOpenPayload.TRADE -> "tcgshop.deal.trade_title";
            default -> "tcgshop.deal.buy_title"; };
        Draw.scaledCentered(ctx, Text.translatable(titleKey).formatted(Formatting.BOLD), x + w / 2f, y + 9, 1.1f, 0xFFFFFFFF);
        ctx.drawTextWithShadow(textRenderer, deal.name().copy().formatted(Formatting.YELLOW), x + 12, y + 32, 0xFFFFFFFF);
        if (haggle()) personaChip(ctx, x + 14 + textRenderer.getWidth(deal.name()), y + 30, t);

        Text line = closing != null ? closing : deal.line();
        if (deal.mode() == DealOpenPayload.TRADE) renderTrade(ctx, x, y, w, line, mouseX, t);
        else renderOffer(ctx, x, y, w, line, mouseX, t);

        if (closing != null) {
            float a = (float) MathHelper.clamp((closeAt - t) / 0.6, 0, 1);
            Ui.text(ctx, Text.translatable("tcgshop.deal.closed").formatted(Formatting.ITALIC), x, y + h - 22, w, 10, Draw.withAlpha(0xFFAAB0C8, a), false);
        } else if (haggle()) haggleControls(ctx, x, y, w, h, mouseX, mouseY, t);
        else {
            int by = y + h - 28;
            Ui.button(ctx, x + 14, by, 130, 20, Text.translatable("tcgshop.deal.accept"), mouseX, mouseY, true, 0xFF3C8A4A);
            Ui.button(ctx, x + w - 144, by, 130, 20, Text.translatable("tcgshop.deal.reject"), mouseX, mouseY, true, 0xFF7A2020);
            buttons.add(new Btn(x + 14, by, 130, 20, () -> send(DealAnswerPayload.ACCEPT)));
            buttons.add(new Btn(x + w - 144, by, 130, 20, () -> send(DealAnswerPayload.REJECT_POLITE)));
        }
        m.pop();
    }

    private void personaChip(DrawContext ctx, int x, int y, double t) {
        Text p = Text.translatable("tcgshop.persona." + dev.tcgshop.entity.CustomerEntity.PERSONAS[Math.min(5, Math.max(0, deal.persona()))]);
        int pw = textRenderer.getWidth(p) + 8;
        Ui.round(ctx, x, y, pw, 11, 0x553A6FD8);
        ctx.drawText(textRenderer, p, x + 4, y + 2, 0xFFBFD6FF, false);
        // paciencia: corazones
        int hx = x + pw + 6;
        for (int i = 0; i < deal.maxPatience(); i++) {
            boolean full = i < deal.patience();
            ctx.drawText(textRenderer, full ? "♥" : "♡", hx + i * 8, y + 2, full ? 0xFFFF5A7A : 0xFF5A5F78, false);
        }
    }

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
        int py = Math.max(ty + 6, y + (haggle() ? 92 : 108));
        ctx.drawTextWithShadow(textRenderer, Text.literal(Ui.ellipsis(deal.item().getName().getString(), textW)), x + 12, py, 0xFFFFFFFF);
        double diff = deal.market() == 0 ? 0 : (deal.price() - deal.market()) * 100.0 / deal.market();
        float fl = (float) MathHelper.clamp(1 - (t - flashAt) / 0.8, 0, 1);
        int priceCol = fl > 0 ? Ui.lerpC(Ui.GOLD, 0xFF6BFF8A, fl) : Ui.GOLD;
        ctx.drawTextWithShadow(textRenderer, Text.translatable(haggle() ? "tcgshop.deal.asks" : "tcgshop.deal.offer", PriceService.format(deal.price())).formatted(Formatting.BOLD),
                x + 12, py + 12, priceCol);
        ctx.drawTextWithShadow(textRenderer, Text.translatable("tcgshop.deal.market", PriceService.format(deal.market())), x + 12, py + 24, Ui.MUTED);
        boolean good = deal.sellToYou() ? diff < 0 : diff > 0;
        ctx.drawTextWithShadow(textRenderer, Text.literal(String.format(Locale.ROOT, "%+.0f%% ", diff)).append(Text.translatable(good ? "tcgshop.deal.good" : "tcgshop.deal.bad")),
                x + 12, py + 36, good ? 0xFF6BE07B : 0xFFFF7A5C);
    }

    /** Contraoferta y respuestas del regateo. */
    private void haggleControls(DrawContext ctx, int x, int y, int w, int h, int mx, int my, double t) {
        int gy = y + h - 104;
        // tu oferta: botones -5% -1% [precio] +1% +5% · mercado
        ctx.drawTextWithShadow(textRenderer, Text.translatable("tcgshop.deal.your_offer").formatted(Formatting.BOLD), x + 12, gy + 4, 0xFFFFFFFF);
        int bx = x + 12 + textRenderer.getWidth(Text.translatable("tcgshop.deal.your_offer")) + 8;
        bx = step(ctx, bx, gy, "-5%", -0.05, mx, my);
        bx = step(ctx, bx, gy, "-1%", -0.01, mx, my);
        String cs = PriceService.format(counter);
        int cwid = Math.max(64, textRenderer.getWidth(cs) + 12);
        Ui.round(ctx, bx, gy, cwid, 15, 0xFF0E1018);
        Ui.outline(ctx, bx, gy, cwid, 15, waiting ? 0x66FFFFFF : Ui.GOLD);
        Ui.text(ctx, Text.literal(cs).formatted(Formatting.BOLD), bx, gy + 4, cwid, 10, Ui.GOLD, false);
        bx += cwid + 3;
        bx = step(ctx, bx, gy, "+1%", 0.01, mx, my);
        bx = step(ctx, bx, gy, "+5%", 0.05, mx, my);
        Text mk = Text.translatable("tcgshop.deal.set_market");
        int mw = textRenderer.getWidth(mk) + 10;
        Ui.button(ctx, bx + 4, gy, mw, 15, mk, mx, my, !waiting, 0xFF39415E);
        buttons.add(new Btn(bx + 4, gy, mw, 15, () -> counter = Math.max(1, Math.min(deal.price(), Math.round(deal.market() * 0.9)))));

        // respuestas
        int cols = 2, gap = 4, bw = (w - 24 - gap) / cols, bh = 17, ry = gy + 20;
        answer(ctx, x + 12, ry, bw, bh, Text.translatable("tcgshop.deal.r_accept", PriceService.format(deal.price())), 0xFF2E8A4A, DealAnswerPayload.ACCEPT, mx, my);
        answer(ctx, x + 12 + bw + gap, ry, bw, bh, Text.translatable("tcgshop.deal.r_counter", PriceService.format(counter)), 0xFF3A6FD8, DealAnswerPayload.COUNTER, mx, my);
        ry += bh + 3;
        answer(ctx, x + 12, ry, bw, bh, Text.translatable("tcgshop.deal.r_market"), 0xFF39415E, DealAnswerPayload.ARG_MARKET, mx, my);
        answer(ctx, x + 12 + bw + gap, ry, bw, bh, Text.translatable("tcgshop.deal.r_elsewhere"), 0xFF39415E, DealAnswerPayload.ARG_ELSEWHERE, mx, my);
        ry += bh + 3;
        answer(ctx, x + 12, ry, bw, bh, Text.translatable("tcgshop.deal.r_broke"), 0xFF39415E, DealAnswerPayload.ARG_BROKE, mx, my);
        answer(ctx, x + 12 + bw + gap, ry, bw, bh, Text.translatable("tcgshop.deal.r_max"), 0xFF8A5A1A, DealAnswerPayload.ARG_MAX, mx, my);
        ry += bh + 3;
        int bw3 = (w - 24 - gap * 2) / 3;
        answer(ctx, x + 12, ry, bw3, bh, Text.translatable("tcgshop.deal.r_think"), 0xFF39415E, DealAnswerPayload.INTEREST, mx, my);
        answer(ctx, x + 12 + bw3 + gap, ry, bw3, bh, Text.translatable("tcgshop.deal.r_no_thanks"), 0xFF5A2A2A, DealAnswerPayload.REJECT_POLITE, mx, my);
        answer(ctx, x + 12 + (bw3 + gap) * 2, ry, bw3, bh, Text.translatable("tcgshop.deal.r_rude"), 0xFF8A1F1F, DealAnswerPayload.REJECT_RUDE, mx, my);
        if (waiting) {
            String dots = ".".repeat(1 + (int) (t * 3) % 3);
            Ui.text(ctx, Text.translatable("tcgshop.deal.thinking").append(dots), x, gy - 12, w, 10, 0xFFAAB0C8, false);
        }
    }

    private int step(DrawContext ctx, int x, int y, String label, double pct, int mx, int my) {
        int bw = textRenderer.getWidth(label) + 8;
        Ui.button(ctx, x, y, bw, 15, Text.literal(label), mx, my, !waiting, 0xFF39415E);
        buttons.add(new Btn(x, y, bw, 15, () -> {
            long d = Math.max(1, Math.round(deal.price() * Math.abs(pct)));
            counter = Math.max(1, Math.min(deal.price(), counter + (pct < 0 ? -d : d)));
        }));
        return x + bw + 3;
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

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (closing != null) { super.close(); return true; }
        for (Btn b : new ArrayList<>(buttons))
            if (Ui.in(mouseX, mouseY, b.x, b.y, b.w, b.h)) {
                if (!waiting) {
                    b.r.run();
                    if (client != null && client.player != null) client.player.playSound(net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK.value(), 0.4f, 1.2f);
                }
                return true;
            }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double h, double v) {
        if (haggle() && !waiting && closing == null) {
            long d = Math.max(1, Math.round(deal.price() * 0.01));
            counter = Math.max(1, Math.min(deal.price(), counter + (v > 0 ? d : -d)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, h, v);
    }
}

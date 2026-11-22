package dev.tcgshop.client.phone;

import dev.tcgshop.client.binder.StylePainter;
import dev.tcgshop.client.binder.Ui;
import dev.tcgshop.phone.PhoneTier;
import dev.tcgshop.pricing.PriceService;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;

import java.util.List;

import static dev.tcgshop.client.screen.Draw.withAlpha;

/**
 * App «Mejoras» del teléfono: a la izquierda tu teléfono (nivel, ventajas, llamada diaria) y a la derecha la escalera
 * de los 7 niveles con lo que desbloquea cada uno. Mejorar pide confirmación (segundo clic en 3 s).
 */
public final class UpgradesPanel {
    /** Dónde se registran las zonas clicables (las del teléfono). */
    public interface Sink {
        void add(int x, int y, int w, int h, Runnable action);
    }

    private static final int SURF = 0xF0171A26, SURF2 = 0xF01F2333, LINE = 0xFF2C3149;

    private int armedTier = -1;
    private double armedAt = -10;

    private static double now() { return System.nanoTime() / 1e9; }

    /** Dibuja la app. Devuelve el scroll (corregido) de la lista de niveles. */
    public double render(DrawContext ctx, int x, int y, int w, int h, int mouseX, int mouseY, double t, double scroll, Sink sink) {
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        int tier = PhoneClient.tier;
        PhoneTier cur = PhoneTier.of(tier);
        if (!PhoneClient.enabled) {
            Ui.roundedGradient(ctx, x, y, w, 40, 7, SURF2, SURF);
            Ui.text(ctx, Text.translatable("tcgshop.phoneup.disabled"), x, y, w, 40, 0xFFDDE3F0, false);
            return 0;
        }
        // ---------------------------------------------------------------- izquierda: tu teléfono
        int lw = Math.min(170, Math.max(130, w / 3));
        Ui.roundedGradient(ctx, x, y, lw, h, 8, SURF2, SURF);
        Ui.roundedOutline(ctx, x, y, lw, h, 8, LINE);
        ctx.drawText(tr, Text.translatable("tcgshop.phoneup.your_phone").formatted(Formatting.BOLD), x + 8, y + 7, 0xFF9A9FB8, false);
        // dibujo del teléfono con el color del nivel
        int pw = 30, ph = 52, px = x + 10, py = y + 22;
        Ui.rounded(ctx, px + 2, py + 3, pw, ph, 6, 0x60000000);
        Ui.roundedGradient(ctx, px, py, pw, ph, 6, Ui.brighten(cur.color, 40), Ui.darken(cur.color, 40));
        Ui.roundedGradient(ctx, px + 3, py + 5, pw - 6, ph - 12, 3, 0xFF1C2140, 0xFF0B0D1A);
        float glow = 0.5f + 0.5f * (float) Math.sin(t * 2.2);
        StylePainter.circle(ctx, px + pw / 2, py + ph / 2 - 2, 6, withAlpha(cur.color, 0.35f + 0.3f * glow));
        ctx.fill(px + pw / 2 - 5, py + ph - 5, px + pw / 2 + 5, py + ph - 4, 0xAAFFFFFF);
        int tx = px + pw + 8, tw = lw - (tx - x) - 6;
        ctx.drawText(tr, Text.translatable("tcgshop.phoneup.level", tier, PhoneTier.MAX_LEVEL), tx, py + 2, 0xFF9A9FB8, false);
        var m = ctx.getMatrices();
        Text name = PhoneClient.tierName(tier).copy().formatted(Formatting.BOLD);
        float s = Math.min(1.4f, tw / (float) Math.max(1, tr.getWidth(name)));
        m.push();
        m.translate(tx, py + 14, 0);
        m.scale(s, s, 1);
        ctx.drawText(tr, name, 0, 0, Ui.brighten(cur.color, 50), true);
        m.pop();
        // puntos de progreso
        for (int i = 1; i <= PhoneTier.MAX_LEVEL; i++) {
            int dx = tx + (i - 1) * 9;
            if (dx + 6 > x + lw - 4) break;
            Ui.rounded(ctx, dx, py + 32, 6, 6, 3, i <= tier ? PhoneTier.of(i).color : 0xFF2A2E40);
        }
        if (PhoneClient.legacy) ctx.drawText(tr, Text.translatable("tcgshop.phoneup.legacy"), tx, py + 42, 0xFF6BE07B, false);
        int iy = py + ph + 10;
        // ventajas del nivel actual
        ctx.drawText(tr, Text.translatable("tcgshop.phoneup.now_have").formatted(Formatting.BOLD), x + 8, iy, 0xFFDDE3F0, false);
        iy += 12;
        int maxPerkY = y + h - (tier >= PhoneTier.CALL_TIER ? 70 : 44);
        for (OrderedText line : tr.wrapLines(Text.translatable("tcgshop.phoneup.perks." + cur.key), lw - 16)) {
            if (iy > maxPerkY) break;
            ctx.drawText(tr, line, x + 8, iy, 0xFFB8BCCB, false);
            iy += 10;
        }
        // llamada diaria o próximo nivel
        int by = y + h - (tier >= PhoneTier.CALL_TIER ? 64 : 38);
        ctx.fill(x + 8, by - 4, x + lw - 8, by - 3, 0x22FFFFFF);
        if (tier >= PhoneTier.CALL_TIER) {
            ctx.drawText(tr, Text.translatable("tcgshop.phoneup.call.title").formatted(Formatting.BOLD), x + 8, by, Ui.GOLD, false);
            boolean can = PhoneClient.canCall();
            ctx.drawText(tr, Text.translatable(can ? "tcgshop.phoneup.call.ready" : PhoneClient.calls ? "tcgshop.phoneup.call.used_today" : "tcgshop.phoneup.call.off"),
                    x + 8, by + 11, can ? 0xFF6BE07B : 0xFF9A9FB8, false);
            int bw = (lw - 20) / 2;
            Ui.button(ctx, x + 8, by + 26, bw, 18, Text.translatable("tcgshop.phoneup.call.collector"), mouseX, mouseY, can, 0xFF8E44AD);
            Ui.button(ctx, x + 12 + bw, by + 26, bw, 18, Text.translatable("tcgshop.phoneup.call.wholesaler"), mouseX, mouseY, can, 0xFFB8701E);
            if (can) {
                sink.add(x + 8, by + 26, bw, 18, () -> PhoneClient.call("collector"));
                sink.add(x + 12 + bw, by + 26, bw, 18, () -> PhoneClient.call("wholesaler"));
            }
            if (Ui.in(mouseX, mouseY, x + 8, by + 26, lw - 16, 18))
                tooltip(tr.wrapLines(Text.translatable("tcgshop.phoneup.call.help"), 200));
        } else {
            int next = tier + 1;
            ctx.drawText(tr, Text.translatable("tcgshop.phoneup.next", PhoneClient.tierName(next)).formatted(Formatting.BOLD), x + 8, by, 0xFFDDE3F0, false);
            long cost = PhoneClient.cost(next);
            long bal = balance();
            ctx.drawText(tr, Text.literal("₽ " + PriceService.format(cost)), x + 8, by + 11, bal >= cost ? Ui.GOLD : 0xFFFF7070, false);
            Ui.progress(ctx, x + 8, by + 23, lw - 16, 5, cost <= 0 ? 1f : (float) Math.min(1.0, bal / (double) cost), PhoneTier.of(next).color);
        }

        // ---------------------------------------------------------------- derecha: los niveles
        int rx = x + lw + 8, rw = w - lw - 8, rowH = 50, gap = 4;
        int contentH = PhoneTier.values().length * (rowH + gap) - gap;
        scroll = MathHelper.clamp(scroll, 0, Math.max(0, contentH - h));
        ctx.enableScissor(rx, y, rx + rw, y + h);
        for (PhoneTier pt : PhoneTier.values()) {
            int i = pt.ordinal();
            int ry = y + i * (rowH + gap) - (int) scroll;
            if (ry + rowH < y || ry > y + h) continue;
            boolean owned = i <= tier, next = i == tier + 1, current = i == tier;
            int accent = pt.color;
            Ui.rounded(ctx, rx + 1, ry + 2, rw, rowH, 7, 0x40000000);
            Ui.roundedGradient(ctx, rx, ry, rw, rowH, 7, current ? withAlpha(Ui.darken(accent, 30), 0.95f) : SURF2, SURF);
            Ui.roundedOutline(ctx, rx, ry, rw, rowH, 7, current ? withAlpha(accent, 0.95f) : next ? withAlpha(accent, 0.6f) : LINE);
            Ui.rounded(ctx, rx + 3, ry + 5, 3, rowH - 10, 1, owned || next ? accent : 0xFF3A3F55);
            // nombre y nivel
            ctx.drawText(tr, PhoneClient.tierName(i).copy().formatted(Formatting.BOLD), rx + 12, ry + 6, owned ? 0xFFFFFFFF : next ? Ui.brighten(accent, 50) : 0xFF8A8FA8, true);
            int nameW = tr.getWidth(PhoneClient.tierName(i).copy().formatted(Formatting.BOLD));
            ctx.drawText(tr, Text.translatable("tcgshop.phoneup.lv", i), rx + 16 + nameW, ry + 6, 0xFF6A6F88, false);
            // ventajas (2 líneas)
            int bwBtn = 92, textW = rw - bwBtn - 26;
            List<OrderedText> lines = tr.wrapLines(Text.translatable("tcgshop.phoneup.perks." + pt.key), textW);
            for (int k = 0; k < Math.min(3, lines.size()); k++)
                ctx.drawText(tr, lines.get(k), rx + 12, ry + 18 + k * 10, owned ? 0xFFC8CCDA : 0xFF8A8FA8, false);
            boolean rowHover = Ui.in(mouseX, mouseY, rx, ry, rw, rowH) && Ui.in(mouseX, mouseY, rx, y, rw, h);
            if (rowHover && lines.size() > 3) tooltip(tr.wrapLines(Text.translatable("tcgshop.phoneup.perks." + pt.key), 220));
            // estado / botón
            int bx = rx + rw - bwBtn - 8, bby = ry + (rowH - 20) / 2;
            if (current) Ui.chip(ctx, bx, bby + 2, bwBtn, 16, Text.translatable("tcgshop.phoneup.current"), true, false, accent, 0);
            else if (owned) Ui.text(ctx, Text.translatable("tcgshop.phoneup.owned"), bx, bby, bwBtn, 20, 0xFF6BE07B, true);
            else if (next) {
                long cost = PhoneClient.cost(i);
                boolean armed = armedTier == i && now() - armedAt < 3;
                boolean afford = balance() >= cost;
                Text label = armed ? Text.translatable("tcgshop.phoneup.confirm") : Text.literal("₽ " + compact(cost));
                Ui.button(ctx, bx, bby, bwBtn, 20, label, mouseX, mouseY, afford, armed ? 0xFF2E9C5A : accent);
                if (afford && Ui.in(mouseX, mouseY, rx, y, rw, h)) {
                    sink.add(bx, Math.max(bby, y), bwBtn, Math.min(20, y + h - Math.max(bby, y)), () -> {
                        if (armedTier == i && now() - armedAt < 3) {
                            armedTier = -1;
                            PhoneClient.buy(i);
                        } else {
                            armedTier = i;
                            armedAt = now();
                        }
                    });
                }
                if (Ui.in(mouseX, mouseY, bx, bby, bwBtn, 20))
                    tooltip(List.of(Text.translatable(afford ? "tcgshop.phoneup.buy_tip" : "tcgshop.phoneup.cant_afford",
                            PhoneClient.tierName(i), PriceService.format(cost)).asOrderedText()));
            } else {
                LockIcon.draw(ctx, bx + bwBtn / 2 - 4 - tr.getWidth(compact(PhoneClient.cost(i))) / 2 - 6, bby + 5, 10, 0xFF6A6F88);
                ctx.drawText(tr, "₽ " + compact(PhoneClient.cost(i)), bx + bwBtn / 2 - tr.getWidth(compact(PhoneClient.cost(i))) / 2 + 2, bby + 7, 0xFF8A8FA8, false);
            }
        }
        ctx.disableScissor();
        // barra de scroll
        if (contentH > h) {
            int bh = Math.max(16, h * h / contentH), byy = y + (int) ((h - bh) * (scroll / Math.max(1, contentH - h)));
            Ui.rounded(ctx, rx + rw - 3, byy, 2, bh, 1, 0x66FFFFFF);
        }
        return scroll;
    }

    private static long balance() {
        var c = MinecraftClient.getInstance();
        return c.player != null && dev.tcgshop.economy.CobbleDollarsBridge.isAvailable() ? dev.tcgshop.economy.CobbleDollarsBridge.getBalance(c.player) : Long.MAX_VALUE;
    }

    static String compact(long v) {
        long a = Math.abs(v);
        String s = a >= 1_000_000 ? String.format(java.util.Locale.ROOT, "%.1fM", a / 1e6).replace(".0M", "M")
                : a >= 10_000 ? String.format(java.util.Locale.ROOT, "%.0fk", a / 1e3) : PriceService.format(a);
        return (v < 0 ? "-" : "") + s;
    }

    private static void tooltip(List<OrderedText> lines) {
        var screen = MinecraftClient.getInstance().currentScreen;
        if (screen != null) screen.setTooltip(lines, net.minecraft.client.gui.tooltip.HoveredTooltipPositioner.INSTANCE, true);
    }

    // ------------------------------------------------------------------ aviso de app bloqueada

    /** Tarjeta «app bloqueada» sobre la pantalla de inicio (vertical). onUpgrades abre la app Mejoras; onClose la cierra. */
    public static void lockPrompt(DrawContext ctx, int sx, int sy, int sw, int sh, String appKey, int mouseX, int mouseY,
                                  Sink sink, Runnable onUpgrades, Runnable onClose) {
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        int need = PhoneTier.appTier(appKey);
        PhoneTier nt = PhoneTier.of(need);
        ctx.fill(sx, sy, sx + sw, sy + sh, 0xB0060710);
        sink.add(sx, sy, sw, sh, onClose); // clic fuera = cerrar
        int cw = sw - 24, cx = sx + 12;
        Text body = Text.translatable("tcgshop.phoneup.app_locked_body", Text.translatable("tcgshop.phone." + appKey), PhoneClient.tierName(need));
        List<OrderedText> lines = tr.wrapLines(body, cw - 16);
        int chh = 64 + lines.size() * 10 + 30;
        int cy = sy + (sh - chh) / 2;
        Ui.rounded(ctx, cx + 2, cy + 3, cw, chh, 10, 0x80000000);
        Ui.roundedGradient(ctx, cx, cy, cw, chh, 10, SURF2, SURF);
        Ui.roundedOutline(ctx, cx, cy, cw, chh, 10, withAlpha(nt.color, 0.9f));
        sink.add(cx, cy, cw, chh, () -> {}); // la tarjeta no cierra
        LockIcon.badge(ctx, cx + cw / 2, cy + 18, 14, nt.color);
        Ui.text(ctx, Text.translatable("tcgshop.phoneup.requires", PhoneClient.tierName(need)).formatted(Formatting.BOLD), cx, cy + 34, cw, 12, Ui.brighten(nt.color, 50), true);
        int ly = cy + 50;
        for (OrderedText l : lines) {
            ctx.drawText(tr, l, cx + cw / 2 - tr.getWidth(l) / 2, ly, 0xFFC8CCDA, false);
            ly += 10;
        }
        ctx.drawText(tr, Text.literal("₽ " + PriceService.format(PhoneClient.cost(need))), cx + cw / 2 - tr.getWidth("₽ " + PriceService.format(PhoneClient.cost(need))) / 2, ly + 2, Ui.GOLD, false);
        int bw = (cw - 24) / 2, by = cy + chh - 26;
        Ui.button(ctx, cx + 8, by, bw, 18, Text.translatable("tcgshop.phoneup.see_upgrades"), mouseX, mouseY, true, nt.color);
        Ui.button(ctx, cx + 16 + bw, by, bw, 18, Text.translatable("tcgshop.phoneup.close"), mouseX, mouseY, true, 0xFF39415E);
        sink.add(cx + 8, by, bw, 18, onUpgrades);
        sink.add(cx + 16 + bw, by, bw, 18, onClose);
    }
}

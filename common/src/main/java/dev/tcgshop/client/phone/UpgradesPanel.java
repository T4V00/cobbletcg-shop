package dev.tcgshop.client.phone;

import dev.tcgshop.client.binder.StylePainter;
import dev.tcgshop.client.binder.Ui;
import dev.tcgshop.phone.PhoneTier;
import dev.tcgshop.pricing.PriceService;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import static dev.tcgshop.client.screen.Draw.withAlpha;

/**
 * Phone «Upgrades» app: on the left your phone (level, perks, daily call) and on the right the ladder
 * of the 7 levels with what each one unlocks. Upgrading asks for confirmation (second click within 3 s).
 */
public final class UpgradesPanel {
    /** Where the clickable zones are registered (the phone's). */
    public interface Sink {
        void add(int x, int y, int w, int h, Runnable action);
    }

    private static final int SURF = 0xF0171A26, SURF2 = 0xF01F2333, LINE = 0xFF2C3149;

    private int armedTier = -1;
    private double armedAt = -10;

    private static double now() { return System.nanoTime() / 1e9; }

    /** Draws the app. Returns the (corrected) scroll of the level list. */
    public double render(GuiGraphics ctx, int x, int y, int w, int h, int mouseX, int mouseY, double t, double scroll, Sink sink) {
        Font tr = Minecraft.getInstance().font;
        int tier = PhoneClient.tier;
        PhoneTier cur = PhoneTier.of(tier);
        if (!PhoneClient.enabled) {
            Ui.roundedGradient(ctx, x, y, w, 40, 7, SURF2, SURF);
            Ui.text(ctx, Component.translatable("tcgshop.phoneup.disabled"), x, y, w, 40, 0xFFDDE3F0, false);
            return 0;
        }
        // ---------------------------------------------------------------- left: your phone
        int lw = Math.min(170, Math.max(130, w / 3));
        Ui.roundedGradient(ctx, x, y, lw, h, 8, SURF2, SURF);
        Ui.roundedOutline(ctx, x, y, lw, h, 8, LINE);
        ctx.drawString(tr, Component.translatable("tcgshop.phoneup.your_phone").withStyle(ChatFormatting.BOLD), x + 8, y + 7, 0xFF9A9FB8, false);
        // phone drawing in the level's color
        int pw = 30, ph = 52, px = x + 10, py = y + 22;
        Ui.rounded(ctx, px + 2, py + 3, pw, ph, 6, 0x60000000);
        Ui.roundedGradient(ctx, px, py, pw, ph, 6, Ui.brighten(cur.color, 40), Ui.darken(cur.color, 40));
        Ui.roundedGradient(ctx, px + 3, py + 5, pw - 6, ph - 12, 3, 0xFF1C2140, 0xFF0B0D1A);
        float glow = 0.5f + 0.5f * (float) Math.sin(t * 2.2);
        StylePainter.circle(ctx, px + pw / 2, py + ph / 2 - 2, 6, withAlpha(cur.color, 0.35f + 0.3f * glow));
        ctx.fill(px + pw / 2 - 5, py + ph - 5, px + pw / 2 + 5, py + ph - 4, 0xAAFFFFFF);
        int tx = px + pw + 8, tw = lw - (tx - x) - 6;
        ctx.drawString(tr, Component.translatable("tcgshop.phoneup.level", tier, PhoneTier.MAX_LEVEL), tx, py + 2, 0xFF9A9FB8, false);
        var m = ctx.pose();
        Component name = PhoneClient.tierName(tier).copy().withStyle(ChatFormatting.BOLD);
        float s = Math.min(1.4f, tw / (float) Math.max(1, tr.width(name)));
        m.pushPose();
        m.translate(tx, py + 14, 0);
        m.scale(s, s, 1);
        ctx.drawString(tr, name, 0, 0, Ui.brighten(cur.color, 50), true);
        m.popPose();
        // progress points
        for (int i = 1; i <= PhoneTier.MAX_LEVEL; i++) {
            int dx = tx + (i - 1) * 9;
            if (dx + 6 > x + lw - 4) break;
            Ui.rounded(ctx, dx, py + 32, 6, 6, 3, i <= tier ? PhoneTier.of(i).color : 0xFF2A2E40);
        }
        if (PhoneClient.legacy) ctx.drawString(tr, Component.translatable("tcgshop.phoneup.legacy"), tx, py + 42, 0xFF6BE07B, false);
        int iy = py + ph + 10;
        // current level perks
        ctx.drawString(tr, Component.translatable("tcgshop.phoneup.now_have").withStyle(ChatFormatting.BOLD), x + 8, iy, 0xFFDDE3F0, false);
        iy += 12;
        int maxPerkY = y + h - (tier >= PhoneTier.CALL_TIER ? 70 : 44);
        for (FormattedCharSequence line : tr.split(Component.translatable("tcgshop.phoneup.perks." + cur.key), lw - 16)) {
            if (iy > maxPerkY) break;
            ctx.drawString(tr, line, x + 8, iy, 0xFFB8BCCB, false);
            iy += 10;
        }
        // daily call or next level
        int by = y + h - (tier >= PhoneTier.CALL_TIER ? 64 : 38);
        ctx.fill(x + 8, by - 4, x + lw - 8, by - 3, 0x22FFFFFF);
        if (tier >= PhoneTier.CALL_TIER) {
            ctx.drawString(tr, Component.translatable("tcgshop.phoneup.call.title").withStyle(ChatFormatting.BOLD), x + 8, by, Ui.GOLD, false);
            boolean can = PhoneClient.canCall();
            ctx.drawString(tr, Component.translatable(can ? "tcgshop.phoneup.call.ready" : PhoneClient.calls ? "tcgshop.phoneup.call.used_today" : "tcgshop.phoneup.call.off"),
                    x + 8, by + 11, can ? 0xFF6BE07B : 0xFF9A9FB8, false);
            int bw = (lw - 20) / 2;
            Ui.button(ctx, x + 8, by + 26, bw, 18, Component.translatable("tcgshop.phoneup.call.collector"), mouseX, mouseY, can, 0xFF8E44AD);
            Ui.button(ctx, x + 12 + bw, by + 26, bw, 18, Component.translatable("tcgshop.phoneup.call.wholesaler"), mouseX, mouseY, can, 0xFFB8701E);
            if (can) {
                sink.add(x + 8, by + 26, bw, 18, () -> PhoneClient.call("collector"));
                sink.add(x + 12 + bw, by + 26, bw, 18, () -> PhoneClient.call("wholesaler"));
            }
            if (Ui.in(mouseX, mouseY, x + 8, by + 26, lw - 16, 18))
                tooltip(tr.split(Component.translatable("tcgshop.phoneup.call.help"), 200));
        } else {
            int next = tier + 1;
            ctx.drawString(tr, Component.translatable("tcgshop.phoneup.next", PhoneClient.tierName(next)).withStyle(ChatFormatting.BOLD), x + 8, by, 0xFFDDE3F0, false);
            long cost = PhoneClient.cost(next);
            long bal = balance();
            ctx.drawString(tr, Component.literal(PriceService.format(cost)), x + 8, by + 11, bal >= cost ? Ui.GOLD : 0xFFFF7070, false);
            Ui.progress(ctx, x + 8, by + 23, lw - 16, 5, cost <= 0 ? 1f : (float) Math.min(1.0, bal / (double) cost), PhoneTier.of(next).color);
        }

        // ---------------------------------------------------------------- right: the levels
        int rx = x + lw + 8, rw = w - lw - 8, rowH = 50, gap = 4;
        int contentH = PhoneTier.values().length * (rowH + gap) - gap;
        scroll = Mth.clamp(scroll, 0, Math.max(0, contentH - h));
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
            // name and level
            ctx.drawString(tr, PhoneClient.tierName(i).copy().withStyle(ChatFormatting.BOLD), rx + 12, ry + 6, owned ? 0xFFFFFFFF : next ? Ui.brighten(accent, 50) : 0xFF8A8FA8, true);
            int nameW = tr.width(PhoneClient.tierName(i).copy().withStyle(ChatFormatting.BOLD));
            ctx.drawString(tr, Component.translatable("tcgshop.phoneup.lv", i), rx + 16 + nameW, ry + 6, 0xFF6A6F88, false);
            // perks (2 lines)
            int bwBtn = 92, textW = rw - bwBtn - 26;
            List<FormattedCharSequence> lines = tr.split(Component.translatable("tcgshop.phoneup.perks." + pt.key), textW);
            for (int k = 0; k < Math.min(3, lines.size()); k++)
                ctx.drawString(tr, lines.get(k), rx + 12, ry + 18 + k * 10, owned ? 0xFFC8CCDA : 0xFF8A8FA8, false);
            boolean rowHover = Ui.in(mouseX, mouseY, rx, ry, rw, rowH) && Ui.in(mouseX, mouseY, rx, y, rw, h);
            if (rowHover && lines.size() > 3) tooltip(tr.split(Component.translatable("tcgshop.phoneup.perks." + pt.key), 220));
            // state / button
            int bx = rx + rw - bwBtn - 8, bby = ry + (rowH - 20) / 2;
            if (current) Ui.chip(ctx, bx, bby + 2, bwBtn, 16, Component.translatable("tcgshop.phoneup.current"), true, false, accent, 0);
            else if (owned) Ui.text(ctx, Component.translatable("tcgshop.phoneup.owned"), bx, bby, bwBtn, 20, 0xFF6BE07B, true);
            else if (next) {
                long cost = PhoneClient.cost(i);
                boolean armed = armedTier == i && now() - armedAt < 3;
                boolean afford = balance() >= cost;
                Component label = armed ? Component.translatable("tcgshop.phoneup.confirm") : Component.literal("₽ " + compact(cost));
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
                } else if (!afford && Ui.in(mouseX, mouseY, rx, y, rw, h)) {
                    // 1.0: clicking an upgrade you can't afford explains why, as a notice inside the phone
                    sink.add(bx, Math.max(bby, y), bwBtn, Math.min(20, y + h - Math.max(bby, y)), () -> {
                        long bal = balance();
                        PhoneNotices.push(PhoneNotices.Kind.ERROR, Component.translatable("tcgshop.phone.notice.no_money.title"),
                                Component.translatable("tcgshop.phone.notice.no_money_upgrade", PhoneClient.tierName(i), PriceService.format(cost),
                                        PriceService.format(bal), PriceService.format(Math.max(0, cost - bal))),
                                new net.minecraft.world.item.ItemStack(dev.tcgshop.item.TcgItems.TCG_PHONE));
                    });
                }
                if (Ui.in(mouseX, mouseY, bx, bby, bwBtn, 20))
                    tooltip(List.of(Component.translatable(afford ? "tcgshop.phoneup.buy_tip" : "tcgshop.phoneup.cant_afford",
                            PhoneClient.tierName(i), PriceService.format(cost)).getVisualOrderText()));
            } else {
                LockIcon.draw(ctx, bx + bwBtn / 2 - 4 - tr.width(compact(PhoneClient.cost(i))) / 2 - 6, bby + 5, 10, 0xFF6A6F88);
                ctx.drawString(tr, "₽ " + compact(PhoneClient.cost(i)), bx + bwBtn / 2 - tr.width(compact(PhoneClient.cost(i))) / 2 + 2, bby + 7, 0xFF8A8FA8, false);
            }
        }
        ctx.disableScissor();
        // scroll bar
        if (contentH > h) {
            int bh = Math.max(16, h * h / contentH), byy = y + (int) ((h - bh) * (scroll / Math.max(1, contentH - h)));
            Ui.rounded(ctx, rx + rw - 3, byy, 2, bh, 1, 0x66FFFFFF);
        }
        return scroll;
    }

    private static long balance() {
        var c = Minecraft.getInstance();
        return c.player != null && dev.tcgshop.economy.CobbleDollarsBridge.isAvailable() ? dev.tcgshop.economy.CobbleDollarsBridge.getBalance(c.player) : Long.MAX_VALUE;
    }

    public static String compact(long v) {
        long a = Math.abs(v);
        String s = a >= 1_000_000 ? String.format(java.util.Locale.ROOT, "%.1fM", a / 1e6).replace(".0M", "M")
                : a >= 10_000 ? String.format(java.util.Locale.ROOT, "%.0fk", a / 1e3)
                : java.text.NumberFormat.getIntegerInstance(java.util.Locale.ROOT).format(a).replace(',', '.'); // callers add "₽ "
        return (v < 0 ? "-" : "") + s;
    }

    private static void tooltip(List<FormattedCharSequence> lines) {
        var screen = Minecraft.getInstance().screen;
        if (screen != null) screen.setTooltipForNextRenderPass(lines, net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner.INSTANCE, true);
    }

    // ------------------------------------------------------------------ locked app notice

    /** «Locked app» card over the home screen (portrait). onUpgrades opens the Upgrades app; onClose closes it. */
    public static void lockPrompt(GuiGraphics ctx, int sx, int sy, int sw, int sh, String appKey, int mouseX, int mouseY,
                                  Sink sink, Runnable onUpgrades, Runnable onClose) {
        Font tr = Minecraft.getInstance().font;
        int need = PhoneClient.appTier(appKey); // 1.1: server-side tier for the Furniture Seller call
        PhoneTier nt = PhoneTier.of(need);
        // 1.0: own opaque layer: flush the home screen first and raise the card, so its batched text can't show through
        ctx.flush();
        var mat = ctx.pose();
        mat.pushPose();
        mat.translate(0, 0, PhoneNotices.Z);
        ctx.fill(sx, sy, sx + sw, sy + sh, 0xD8060710);
        sink.add(sx, sy, sw, sh, onClose); // click outside = close
        int cw = sw - 24, cx = sx + 12;
        Component body = Component.translatable("tcgshop.phoneup.app_locked_body", Component.translatable("tcgshop.phone." + appKey), PhoneClient.tierName(need));
        List<FormattedCharSequence> lines = tr.split(body, cw - 16);
        int chh = 64 + lines.size() * 10 + 30;
        int cy = sy + (sh - chh) / 2;
        Ui.rounded(ctx, cx + 2, cy + 3, cw, chh, 10, 0xFF030408);
        Ui.roundedGradient(ctx, cx, cy, cw, chh, 10, 0xFF1F2333, 0xFF171A26); // opaque (SURF/SURF2 are slightly translucent)
        Ui.roundedOutline(ctx, cx, cy, cw, chh, 10, withAlpha(nt.color, 0.9f));
        sink.add(cx, cy, cw, chh, () -> {}); // the card does not close
        LockIcon.badge(ctx, cx + cw / 2, cy + 18, 14, nt.color);
        Ui.text(ctx, Component.translatable("tcgshop.phoneup.requires", PhoneClient.tierName(need)).withStyle(ChatFormatting.BOLD), cx, cy + 34, cw, 12, Ui.brighten(nt.color, 50), true);
        int ly = cy + 50;
        for (FormattedCharSequence l : lines) {
            ctx.drawString(tr, l, cx + cw / 2 - tr.width(l) / 2, ly, 0xFFC8CCDA, false);
            ly += 10;
        }
        String price = PriceService.format(PhoneClient.cost(need)); // already carries the currency suffix
        ctx.drawString(tr, Component.literal(price), cx + cw / 2 - tr.width(price) / 2, ly + 2, Ui.GOLD, false);
        int bw = (cw - 24) / 2, by = cy + chh - 26;
        Ui.button(ctx, cx + 8, by, bw, 18, Component.translatable("tcgshop.phoneup.see_upgrades"), mouseX, mouseY, true, nt.color);
        Ui.button(ctx, cx + 16 + bw, by, bw, 18, Component.translatable("tcgshop.phoneup.close"), mouseX, mouseY, true, 0xFF39415E);
        sink.add(cx + 8, by, bw, 18, onUpgrades);
        sink.add(cx + 16 + bw, by, bw, 18, onClose);
        ctx.flush();
        mat.popPose();
    }
}

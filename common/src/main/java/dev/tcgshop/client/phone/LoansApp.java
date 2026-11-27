package dev.tcgshop.client.phone;

import dev.tcgshop.client.binder.StylePainter;
import dev.tcgshop.client.binder.Ui;
import dev.tcgshop.economy.CobbleDollarsBridge;
import dev.tcgshop.pricing.PriceService;
import java.util.Locale;
import java.util.function.LongConsumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * 1.0 phone «Loans» app (Wholesaler loans, see dev.tcgshop.phone.Loans): credit score and history on the left; on the
 * right the active loan (what you owe, interest, due date, days left or late) with full or partial repayment, or, without
 * a loan, the pending offer and how loans work.
 */
public final class LoansApp {
    private static final int SURF = 0xF0171A26, SURF2 = 0xF01F2333, LINE = 0xFF2C3149, GOLD_LINE = 0xFFE5B21E;

    private double scroll;
    /** «Repay everything» asks for a second click within 3 s. */
    private double armedAll = -10;

    private static double now() { return System.nanoTime() / 1e9; }

    public void onEnter() {
        scroll = 0;
        PhoneAppsClient.requestLoans();
    }

    public void scroll(double v) {
        scroll -= v * 24;
    }

    /** Label of a credit score (excellent ... bad). */
    public static String scoreKey(int score) {
        String k = score >= 80 ? "excellent" : score >= 60 ? "good" : score >= 40 ? "fair" : score >= 20 ? "poor" : "bad";
        return "tcgshop.loan.score." + k;
    }

    public static int scoreColor(int score) {
        return score >= 80 ? 0xFF43C463 : score >= 60 ? 0xFF9BD45B : score >= 40 ? 0xFFFFD34D : score >= 20 ? 0xFFFF9F43 : 0xFFE05050;
    }

    public void render(GuiGraphics ctx, int x, int y, int w, int h, int mouseX, int mouseY, UpgradesPanel.Sink sink, LongConsumer openMessage) {
        Font tr = Minecraft.getInstance().font;
        CompoundTag L = PhoneAppsClient.loans;
        int score = L.contains("score") ? L.getInt("score") : 50;
        // ---------------------------------------------------------------- left: credit
        int lw = Mth.clamp(Math.round(w * 0.36f), 150, 210);
        Ui.roundedGradient(ctx, x, y, lw, h, 8, SURF2, SURF);
        Ui.roundedOutline(ctx, x, y, lw, h, 8, LINE);
        ctx.drawString(tr, Component.translatable("tcgshop.loan.credit").withStyle(ChatFormatting.BOLD), x + 8, y + 7, 0xFF9A9FB8, false);
        int col = scoreColor(score), cx = x + 34, cy = y + 46;
        ring(ctx, cx, cy, 22, 4, score / 100f, col);
        dev.tcgshop.client.screen.Draw.scaledCentered(ctx, Component.literal(String.valueOf(score)).withStyle(ChatFormatting.BOLD), cx, cy - 6, 1.5f, col);
        int tx = x + 64;
        ctx.drawString(tr, Component.translatable(scoreKey(score)).withStyle(ChatFormatting.BOLD), tx, cy - 16, col, true);
        ctx.drawString(tr, Component.translatable("tcgshop.loan.repaid", L.getInt("repaid")), tx, cy - 4, 0xFFCCD2E6, false);
        ctx.drawString(tr, Component.translatable("tcgshop.loan.on_time_late", L.getInt("onTime"), L.getInt("late")), tx, cy + 8, 0xFF9A9FB8, false);
        int hy = y + 80;
        ctx.drawString(tr, Component.translatable("tcgshop.loan.history").withStyle(ChatFormatting.BOLD), x + 8, hy, 0xFFDDE3F0, false);
        hy += 12;
        ListTag hist = L.getList("hist", Tag.TAG_COMPOUND);
        if (hist.isEmpty()) {
            for (FormattedCharSequence line : tr.split(Component.translatable("tcgshop.loan.no_history"), lw - 16)) {
                ctx.drawString(tr, line, x + 8, hy, Ui.MUTED, false);
                hy += 10;
            }
        }
        for (int i = 0; i < hist.size() && hy + 12 < y + h; i++) {
            CompoundTag e = hist.getCompound(i);
            long late = e.getLong("l");
            Ui.rounded(ctx, x + 5, hy - 2, lw - 10, 12, 3, 0x33000000);
            ctx.drawString(tr, PriceService.format(e.getLong("a")), x + 9, hy, 0xFFE4E8F4, false);
            Component st = late == 0 ? Component.translatable("tcgshop.loan.hist.on_time", e.getLong("d"), e.getLong("t"))
                    : Component.translatable("tcgshop.loan.hist.late", late);
            ctx.drawString(tr, st, x + lw - 9 - tr.width(st), hy, late == 0 ? 0xFF6BE07B : 0xFFFF9F6B, false);
            hy += 13;
        }
        // ---------------------------------------------------------------- right: loan / offer
        int px = x + lw + 6, pw = w - lw - 6;
        boolean active = PhoneAppsClient.hasLoan();
        Ui.roundedGradient(ctx, px, y, pw, h, 8, active ? 0xF02A2414 : SURF2, SURF);
        Ui.roundedOutline(ctx, px, y, pw, h, 8, active ? GOLD_LINE : LINE);
        ctx.enableScissor(px + 1, y + 1, px + pw - 1, y + h - 1);
        int ry = y + 8 - (int) scroll;
        if (active) ry = loanView(ctx, tr, L.getCompound("loan"), L, px + 8, ry, pw - 16, mouseX, mouseY, sink);
        else {
            ctx.drawString(tr, Component.translatable("tcgshop.loan.none").withStyle(ChatFormatting.BOLD), px + 8, ry, 0xFFFFFFFF, true);
            ry += 14;
            if (PhoneAppsClient.pendingOffer()) {
                long id = L.getLong("offer");
                Ui.rounded(ctx, px + 6, ry - 2, pw - 12, 34, 5, 0x553A2A0A);
                Ui.roundedOutline(ctx, px + 6, ry - 2, pw - 12, 34, 5, GOLD_LINE);
                ctx.drawString(tr, Component.translatable("tcgshop.loan.offer_waiting").withStyle(ChatFormatting.BOLD), px + 12, ry + 2, Ui.GOLD, false);
                int bw = 90;
                Ui.button(ctx, px + pw - 14 - bw, ry + 12, bw, 16, Component.translatable("tcgshop.loan.see_offer"), mouseX, mouseY, true, 0xFFB8701E);
                if (ry + 12 >= y && ry + 28 <= y + h) sink.add(px + pw - 14 - bw, ry + 12, bw, 16, () -> openMessage.accept(id));
                ry += 40;
            }
            boolean enabled = !L.contains("enabled") || L.getBoolean("enabled");
            Component info;
            if (!enabled) info = Component.translatable("tcgshop.loan.disabled");
            else if (PhoneClient.tier < Math.max(1, L.getInt("minTier"))) info = Component.translatable("tcgshop.loan.need_tier", PhoneClient.tierName(L.getInt("minTier")));
            else if (L.getBoolean("blocked")) info = Component.translatable("tcgshop.loan.blocked");
            else info = Component.translatable("tcgshop.loan.wait");
            ry = paragraph(ctx, tr, info, px + 8, ry, pw - 16, 0xFFDDE3F0) + 6;
            ry = diamondCard(ctx, tr, L, px + 6, ry, pw - 12, y, h, mouseX, mouseY, sink) + 6;
            ry = paragraph(ctx, tr, Component.translatable("tcgshop.loan.how", PriceService.format(L.contains("max") ? L.getLong("max") : 50_000)), px + 8, ry, pw - 16, 0xFF9A9FB8) + 4;
            ry = paragraph(ctx, tr, Component.translatable("tcgshop.loan.how_late", L.getInt("veryLate"),
                    String.format(Locale.ROOT, "%.0f", L.getDouble("fee") * 100)), px + 8, ry, pw - 16, 0xFF9A9FB8);
        }
        ctx.disableScissor();
        int content = ry + (int) scroll - y;
        scroll = Mth.clamp(scroll, 0, Math.max(0, content - h + 8));
    }

    /** 1.1: the $1,000,000 Diamond loan: a request button for Diamond shop owners, or what is still missing. */
    private int diamondCard(GuiGraphics ctx, Font tr, CompoundTag L, int x, int y, int w, int top, int h, int mouseX, int mouseY, UpgradesPanel.Sink sink) {
        long amount = L.getLong("diamondAmount");
        if (amount <= 0) return y;
        String why = L.getString("diamondWhy");
        boolean pending = PhoneAppsClient.pendingOffer();
        int ch = 46;
        Ui.rounded(ctx, x, y, w, ch, 5, 0x55103A40);
        Ui.roundedOutline(ctx, x, y, w, ch, 5, why.isEmpty() ? 0xFF4EE2EC : 0xFF2C5A60);
        ctx.drawString(tr, Component.translatable("tcgshop.loan.diamond.card", PriceService.format(amount)).withStyle(ChatFormatting.BOLD), x + 6, y + 5, 0xFF4EE2EC, false);
        Component sub = why.isEmpty() ? Component.translatable("tcgshop.loan.diamond.terms", L.getInt("diamondTerm")) : Component.translatable(why);
        ctx.drawString(tr, Ui.ellipsis(sub.getString(), w - 12), x + 6, y + 17, why.isEmpty() ? 0xFFBFEFF3 : 0xFF9A9FB8, false);
        int bw = Math.min(110, w - 12);
        boolean ok = why.isEmpty() && !pending;
        Ui.button(ctx, x + w - 6 - bw, y + 28, bw, 14, Component.translatable("tcgshop.loan.diamond.request"), mouseX, mouseY, ok, 0xFF1E8FA8);
        if (ok && y + 28 >= top && y + 42 <= top + h) sink.add(x + w - 6 - bw, y + 28, bw, 14, PhoneAppsClient::requestDiamondLoan);
        return y + ch;
    }

    private int loanView(GuiGraphics ctx, Font tr, CompoundTag loan, CompoundTag L, int x, int y, int w, int mouseX, int mouseY,
                         UpgradesPanel.Sink sink) {
        long owed = loan.getLong("owed"), principal = loan.getLong("principal"), paid = loan.getLong("paid");
        long day = L.getLong("day"), due = loan.getLong("due"), left = due - day;
        boolean late = left < 0, veryLate = loan.getBoolean("veryLate");
        ctx.drawString(tr, Component.translatable("tcgshop.loan.active").withStyle(ChatFormatting.BOLD), x, y, Ui.GOLD, true);
        y += 14;
        ctx.drawString(tr, Component.translatable("tcgshop.loan.owed"), x, y, 0xFF9A9FB8, false);
        var m = ctx.pose();
        m.pushPose();
        m.translate(x, y + 11, 0);
        m.scale(1.6f, 1.6f, 1);
        ctx.drawString(tr, Component.literal(PriceService.format(owed)).withStyle(ChatFormatting.BOLD), 0, 0, late ? 0xFFFF6B6B : 0xFFFFFFFF, true);
        m.popPose();
        Component status = late ? Component.translatable(veryLate ? "tcgshop.loan.status.very_late" : "tcgshop.loan.status.late", -left)
                : left == 0 ? Component.translatable("tcgshop.loan.status.today") : Component.translatable("tcgshop.loan.status.left", left);
        ctx.drawString(tr, status, x + w - tr.width(status), y + 14, late ? 0xFFFF6B6B : left <= 1 ? 0xFFFFC266 : 0xFF6BE07B, false);
        y += 32;
        Ui.progress(ctx, x, y, w, 6, paid / (float) Math.max(1, paid + owed), 0xFF43C463);
        y += 11;
        String[][] rows = {
                {"tcgshop.loan.principal", PriceService.format(principal)},
                {"tcgshop.loan.rate", String.format(Locale.ROOT, "%.1f%%", loan.getDouble("rate") * 100)},
                {"tcgshop.loan.paid", PriceService.format(paid)},
                {"tcgshop.loan.taken", Component.translatable("tcgshop.phone.day", loan.getLong("taken") + 1).getString()},
                {"tcgshop.loan.due", Component.translatable("tcgshop.phone.day", due + 1).getString()}};
        for (String[] r : rows) {
            ctx.drawString(tr, Component.translatable(r[0]), x, y, 0xFFCCD2E6, false);
            ctx.drawString(tr, r[1], x + w - tr.width(r[1]), y, 0xFFE4E8F4, false);
            y += 11;
        }
        if (late) y = paragraph(ctx, tr, Component.translatable(veryLate ? "tcgshop.loan.warn_very_late" : "tcgshop.loan.warn_late",
                String.format(Locale.ROOT, "%.0f", L.getDouble("fee") * 100)), x, y + 2, w, 0xFFFFB0B0);
        y += 6;
        // repay: fixed amounts, half and everything
        Minecraft mc = Minecraft.getInstance();
        long bal = mc.player != null && CobbleDollarsBridge.isAvailable() ? CobbleDollarsBridge.getBalance(mc.player) : 0;
        ctx.drawString(tr, Component.translatable("tcgshop.loan.repay", PriceService.format(bal)), x, y, 0xFFDDE3F0, false);
        y += 12;
        long small = owed > 200_000 ? 10_000 : 1_000, big = owed > 200_000 ? 100_000 : 10_000; // 1.1: bigger steps for the Diamond loan
        long[] amounts = {small, big, Math.max(1, owed / 2)};
        String[] labels = {PriceService.format(small), PriceService.format(big), Component.translatable("tcgshop.loan.half").getString()};
        int bw = (w - 3 * 4) / 4;
        for (int i = 0; i < amounts.length; i++) {
            long a = Math.min(amounts[i], owed);
            boolean ok = bal >= a && a > 0;
            Ui.button(ctx, x + i * (bw + 4), y, bw, 16, Component.literal(labels[i]), mouseX, mouseY, ok, 0xFF3A6FD8);
            if (ok) sink.add(x + i * (bw + 4), y, bw, 16, () -> PhoneAppsClient.repay(a));
        }
        boolean armed = now() - armedAll < 3, okAll = bal >= owed && owed > 0;
        Ui.button(ctx, x + 3 * (bw + 4), y, bw, 16, Component.translatable(armed ? "tcgshop.loan.all_confirm" : "tcgshop.loan.all"), mouseX, mouseY,
                okAll, armed ? 0xFF2E9C5A : 0xFF2E8A4A);
        if (okAll) sink.add(x + 3 * (bw + 4), y, bw, 16, () -> {
            if (now() - armedAll >= 3) { armedAll = now(); return; }
            armedAll = -10;
            PhoneAppsClient.repay(0);
        });
        y += 22;
        y = paragraph(ctx, tr, Component.translatable("tcgshop.loan.no_buying"), x, y, w, 0xFFFFC266);
        return y;
    }

    private static int paragraph(GuiGraphics ctx, Font tr, Component t, int x, int y, int w, int color) {
        for (FormattedCharSequence line : tr.split(t, w)) {
            ctx.drawString(tr, line, x, y, color, false);
            y += 10;
        }
        return y;
    }

    private static void ring(GuiGraphics ctx, int cx, int cy, int r, int thick, float frac, int color) {
        int steps = Math.max(24, r * 5);
        for (int i = 0; i < steps; i++) {
            double a = -Math.PI / 2 + i * Math.PI * 2 / steps;
            boolean on = i < steps * frac;
            for (int k = 0; k < thick; k++) {
                int px = cx + (int) Math.round(Math.cos(a) * (r - k)), py = cy + (int) Math.round(Math.sin(a) * (r - k));
                ctx.fill(px, py, px + 1, py + 1, on ? color : 0x33FFFFFF);
            }
        }
        StylePainter.circle(ctx, cx, cy, r - thick - 1, 0x22000000);
    }
}

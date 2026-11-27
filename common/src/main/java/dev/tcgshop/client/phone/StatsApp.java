package dev.tcgshop.client.phone;

import dev.tcgshop.client.binder.Ui;
import dev.tcgshop.pricing.PriceService;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * 1.0 phone «Stats» app: the money in the registers of all your shops, reputation, tips, charisma, credit and missions
 * on the left, and «what's going for and against you» on the right (pros and cons worked out by the server from your
 * shops, see PhoneStats). Refreshed every few seconds while open.
 */
public final class StatsApp {
    private static final int SURF = 0xF0171A26, SURF2 = 0xF01F2333, LINE = 0xFF2C3149;
    private static final int GOOD = 0xFF6BE07B, BAD = 0xFFFF6B6B;

    private double scroll;
    private double lastRequest;

    private static double now() { return System.nanoTime() / 1e9; }

    public void onEnter() {
        scroll = 0;
        lastRequest = now();
        PhoneAppsClient.requestStats();
    }

    public void scroll(double v) {
        scroll -= v * 24;
    }

    public void render(GuiGraphics ctx, int x, int y, int w, int h, int mouseX, int mouseY) {
        if (now() - lastRequest > 5) { // live while open
            lastRequest = now();
            PhoneAppsClient.requestStats();
        }
        Font tr = Minecraft.getInstance().font;
        CompoundTag s = PhoneAppsClient.stats;
        if (s == null) {
            Ui.text(ctx, Component.translatable("tcgshop.stats.loading"), x, y + h / 2 - 5, w, 10, Ui.MUTED, false);
            return;
        }
        // ---------------------------------------------------------------- tiles
        int lw = Mth.clamp(Math.round(w * 0.46f), 180, 290), gap = 5, tw = (lw - gap) / 2, th = 32;
        long cash = s.getLong("cash"), profit = s.getLong("revenue") - s.getLong("costs");
        int rep = s.getInt("rep"), repAvg = s.getInt("repAvg"), credit = s.getInt("credit");
        int shops = s.getInt("shops");
        Object[][] tiles = {
                {Component.translatable("tcgshop.stats.cash", shops), PriceService.format(cash), Ui.GOLD},
                {Component.translatable("tcgshop.phone.s_profit"), PriceService.format(profit), profit >= 0 ? GOOD : BAD},
                {Component.translatable("tcgshop.stats.rep"), rep < 0 ? "—" : shops > 1 ? rep + " (" + repAvg + ")" : String.valueOf(rep), repColor(rep)},
                {Component.translatable("tcgshop.stats.rating", s.getInt("reviews")), s.getInt("reviews") == 0 ? "—"
                        : String.format(Locale.ROOT, "%.1f ★", s.getDouble("stars")), 0xFFFFD34D},
                {Component.translatable("tcgshop.stats.tips", s.getInt("tipsCount")), PriceService.format(s.getLong("tipsTotal")), 0xFF6BC5FF},
                {Component.translatable("tcgshop.stats.tips_today"), PriceService.format(s.getLong("tipsToday")), 0xFF6BC5FF},
                {Component.translatable("tcgshop.stats.tips_best"), PriceService.format(s.getLong("tipsBest")), 0xFF6BC5FF},
                {Component.translatable("tcgshop.stats.charisma"), String.valueOf(s.getInt("charisma")), 0xFFE07BD8},
                {Component.translatable("tcgshop.stats.credit"), credit + " · " + Component.translatable(LoansApp.scoreKey(credit)).getString(), LoansApp.scoreColor(credit)},
                {Component.translatable("tcgshop.stats.missions"), s.contains("missions") ? s.getInt("missions") + "/" + s.getInt("missionsTotal") : "—", 0xFFB983FF}};
        for (int i = 0; i < tiles.length; i++) {
            int tx = x + (i % 2) * (tw + gap), ty = y + (i / 2) * (th + gap);
            if (ty + th > y + h) break;
            tile(ctx, tr, tx, ty, tw, th, (Component) tiles[i][0], (String) tiles[i][1], (int) tiles[i][2]);
        }
        if (s.contains("loanOwed")) {
            int ly = y + ((tiles.length + 1) / 2) * (th + gap);
            if (ly + 14 <= y + h) {
                long due = s.getLong("loanDue");
                boolean late = s.getLong("day") > due;
                Ui.rounded(ctx, x, ly, lw, 14, 4, late ? 0x88701818 : 0x66503A10);
                Ui.text(ctx, Component.translatable(late ? "tcgshop.stats.loan_late" : "tcgshop.stats.loan", PriceService.format(s.getLong("loanOwed")), due + 1),
                        x, ly + 2, lw, 10, late ? 0xFFFFB0B0 : 0xFFFFE9A8, false);
            }
        }
        // ---------------------------------------------------------------- pros and cons
        int px = x + lw + 8, pw = w - lw - 8;
        Ui.roundedGradient(ctx, px, y, pw, h, 8, SURF2, SURF);
        Ui.roundedOutline(ctx, px, y, pw, h, 8, LINE);
        ctx.enableScissor(px + 1, y + 1, px + pw - 1, y + h - 1);
        int cy = y + 6 - (int) scroll;
        cy = section(ctx, tr, s.getList("pros", Tag.TAG_COMPOUND), "tcgshop.stats.for_you", "▲", GOOD, px + 8, cy, pw - 16);
        cy += 6;
        cy = section(ctx, tr, s.getList("cons", Tag.TAG_COMPOUND), "tcgshop.stats.against_you", "▼", BAD, px + 8, cy, pw - 16);
        ctx.disableScissor();
        int content = cy + (int) scroll - y;
        scroll = Mth.clamp(scroll, 0, Math.max(0, content - h + 6));
    }

    private static int repColor(int rep) {
        return rep >= 80 ? 0xFF43C463 : rep >= 50 ? 0xFFFFD34D : rep < 0 ? 0xFF888888 : 0xFFE05050;
    }

    private static void tile(GuiGraphics ctx, Font tr, int x, int y, int w, int h, Component label, String value, int color) {
        Ui.rounded(ctx, x + 1, y + 2, w, h, 6, 0x40000000);
        Ui.roundedGradient(ctx, x, y, w, h, 6, SURF2, SURF);
        Ui.roundedOutline(ctx, x, y, w, h, 6, LINE);
        Ui.rounded(ctx, x + 2, y + 4, 2, h - 8, 1, color);
        ctx.drawString(tr, Ui.ellipsis(label.getString(), w - 12), x + 8, y + 4, 0xFF9A9FB8, false);
        var m = ctx.pose();
        m.pushPose();
        m.translate(x + 8, y + 16, 0);
        float sc = Math.min(1.3f, (w - 14f) / Math.max(1, tr.width(value)));
        m.scale(sc, sc, 1);
        ctx.drawString(tr, Component.literal(value).withStyle(ChatFormatting.BOLD), 0, 0, color, true);
        m.popPose();
    }

    private static int section(GuiGraphics ctx, Font tr, ListTag items, String titleKey, String mark, int color, int x, int y, int w) {
        ctx.drawString(tr, Component.translatable(titleKey).withStyle(ChatFormatting.BOLD), x, y, color, true);
        y += 13;
        if (items.isEmpty()) {
            ctx.drawString(tr, Component.translatable("tcgshop.stats.nothing"), x + 4, y, Ui.MUTED, false);
            return y + 12;
        }
        for (int i = 0; i < items.size(); i++) {
            CompoundTag c = items.getCompound(i);
            Component t = Component.translatable(c.getString("t"), PhoneAppsClient.args(c.getList("a", Tag.TAG_STRING)));
            ctx.drawString(tr, mark, x, y, color, false);
            for (FormattedCharSequence line : tr.split(t, w - 12)) {
                ctx.drawString(tr, line, x + 10, y, 0xFFE4E8F4, false);
                y += 10;
            }
            String tip = c.getString("tip");
            if (!tip.isEmpty() && I18n.exists(tip)) {
                for (FormattedCharSequence line : tr.split(Component.literal("✦ ").append(Component.translatable(tip)), w - 16)) {
                    ctx.drawString(tr, line, x + 14, y, 0xFFFFD34D, false);
                    y += 10;
                }
            }
            y += 3;
        }
        return y;
    }
}

package dev.tcgshop.client.phone;

import dev.tcgshop.client.binder.Ui;
import dev.tcgshop.pricing.PriceService;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;

import java.util.Locale;

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

    public void render(DrawContext ctx, int x, int y, int w, int h, int mouseX, int mouseY) {
        if (now() - lastRequest > 5) { // live while open
            lastRequest = now();
            PhoneAppsClient.requestStats();
        }
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        NbtCompound s = PhoneAppsClient.stats;
        if (s == null) {
            Ui.text(ctx, Text.translatable("tcgshop.stats.loading"), x, y + h / 2 - 5, w, 10, Ui.MUTED, false);
            return;
        }
        // ---------------------------------------------------------------- tiles
        int lw = MathHelper.clamp(Math.round(w * 0.46f), 180, 290), gap = 5, tw = (lw - gap) / 2, th = 32;
        long cash = s.getLong("cash"), profit = s.getLong("revenue") - s.getLong("costs");
        int rep = s.getInt("rep"), repAvg = s.getInt("repAvg"), credit = s.getInt("credit");
        int shops = s.getInt("shops");
        Object[][] tiles = {
                {Text.translatable("tcgshop.stats.cash", shops), PriceService.format(cash), Ui.GOLD},
                {Text.translatable("tcgshop.phone.s_profit"), PriceService.format(profit), profit >= 0 ? GOOD : BAD},
                {Text.translatable("tcgshop.stats.rep"), rep < 0 ? "—" : shops > 1 ? rep + " (" + repAvg + ")" : String.valueOf(rep), repColor(rep)},
                {Text.translatable("tcgshop.stats.rating", s.getInt("reviews")), s.getInt("reviews") == 0 ? "—"
                        : String.format(Locale.ROOT, "%.1f ★", s.getDouble("stars")), 0xFFFFD34D},
                {Text.translatable("tcgshop.stats.tips", s.getInt("tipsCount")), PriceService.format(s.getLong("tipsTotal")), 0xFF6BC5FF},
                {Text.translatable("tcgshop.stats.tips_today"), PriceService.format(s.getLong("tipsToday")), 0xFF6BC5FF},
                {Text.translatable("tcgshop.stats.tips_best"), PriceService.format(s.getLong("tipsBest")), 0xFF6BC5FF},
                {Text.translatable("tcgshop.stats.charisma"), String.valueOf(s.getInt("charisma")), 0xFFE07BD8},
                {Text.translatable("tcgshop.stats.credit"), credit + " · " + Text.translatable(LoansApp.scoreKey(credit)).getString(), LoansApp.scoreColor(credit)},
                {Text.translatable("tcgshop.stats.missions"), s.contains("missions") ? s.getInt("missions") + "/" + s.getInt("missionsTotal") : "—", 0xFFB983FF}};
        for (int i = 0; i < tiles.length; i++) {
            int tx = x + (i % 2) * (tw + gap), ty = y + (i / 2) * (th + gap);
            if (ty + th > y + h) break;
            tile(ctx, tr, tx, ty, tw, th, (Text) tiles[i][0], (String) tiles[i][1], (int) tiles[i][2]);
        }
        if (s.contains("loanOwed")) {
            int ly = y + ((tiles.length + 1) / 2) * (th + gap);
            if (ly + 14 <= y + h) {
                long due = s.getLong("loanDue");
                boolean late = s.getLong("day") > due;
                Ui.rounded(ctx, x, ly, lw, 14, 4, late ? 0x88701818 : 0x66503A10);
                Ui.text(ctx, Text.translatable(late ? "tcgshop.stats.loan_late" : "tcgshop.stats.loan", PriceService.format(s.getLong("loanOwed")), due + 1),
                        x, ly + 2, lw, 10, late ? 0xFFFFB0B0 : 0xFFFFE9A8, false);
            }
        }
        // ---------------------------------------------------------------- pros and cons
        int px = x + lw + 8, pw = w - lw - 8;
        Ui.roundedGradient(ctx, px, y, pw, h, 8, SURF2, SURF);
        Ui.roundedOutline(ctx, px, y, pw, h, 8, LINE);
        ctx.enableScissor(px + 1, y + 1, px + pw - 1, y + h - 1);
        int cy = y + 6 - (int) scroll;
        cy = section(ctx, tr, s.getList("pros", NbtElement.COMPOUND_TYPE), "tcgshop.stats.for_you", "▲", GOOD, px + 8, cy, pw - 16);
        cy += 6;
        cy = section(ctx, tr, s.getList("cons", NbtElement.COMPOUND_TYPE), "tcgshop.stats.against_you", "▼", BAD, px + 8, cy, pw - 16);
        ctx.disableScissor();
        int content = cy + (int) scroll - y;
        scroll = MathHelper.clamp(scroll, 0, Math.max(0, content - h + 6));
    }

    private static int repColor(int rep) {
        return rep >= 80 ? 0xFF43C463 : rep >= 50 ? 0xFFFFD34D : rep < 0 ? 0xFF888888 : 0xFFE05050;
    }

    private static void tile(DrawContext ctx, TextRenderer tr, int x, int y, int w, int h, Text label, String value, int color) {
        Ui.rounded(ctx, x + 1, y + 2, w, h, 6, 0x40000000);
        Ui.roundedGradient(ctx, x, y, w, h, 6, SURF2, SURF);
        Ui.roundedOutline(ctx, x, y, w, h, 6, LINE);
        Ui.rounded(ctx, x + 2, y + 4, 2, h - 8, 1, color);
        ctx.drawText(tr, Ui.ellipsis(label.getString(), w - 12), x + 8, y + 4, 0xFF9A9FB8, false);
        var m = ctx.getMatrices();
        m.push();
        m.translate(x + 8, y + 16, 0);
        float sc = Math.min(1.3f, (w - 14f) / Math.max(1, tr.getWidth(value)));
        m.scale(sc, sc, 1);
        ctx.drawText(tr, Text.literal(value).formatted(Formatting.BOLD), 0, 0, color, true);
        m.pop();
    }

    private static int section(DrawContext ctx, TextRenderer tr, NbtList items, String titleKey, String mark, int color, int x, int y, int w) {
        ctx.drawText(tr, Text.translatable(titleKey).formatted(Formatting.BOLD), x, y, color, true);
        y += 13;
        if (items.isEmpty()) {
            ctx.drawText(tr, Text.translatable("tcgshop.stats.nothing"), x + 4, y, Ui.MUTED, false);
            return y + 12;
        }
        for (int i = 0; i < items.size(); i++) {
            NbtCompound c = items.getCompound(i);
            Text t = Text.translatable(c.getString("t"), PhoneAppsClient.args(c.getList("a", NbtElement.STRING_TYPE)));
            ctx.drawText(tr, mark, x, y, color, false);
            for (OrderedText line : tr.wrapLines(t, w - 12)) {
                ctx.drawText(tr, line, x + 10, y, 0xFFE4E8F4, false);
                y += 10;
            }
            String tip = c.getString("tip");
            if (!tip.isEmpty() && I18n.hasTranslation(tip)) {
                for (OrderedText line : tr.wrapLines(Text.literal("✦ ").append(Text.translatable(tip)), w - 16)) {
                    ctx.drawText(tr, line, x + 14, y, 0xFFFFD34D, false);
                    y += 10;
                }
            }
            y += 3;
        }
        return y;
    }
}

package dev.tcgshop.client.phone;

import dev.tcgshop.client.binder.StylePainter;
import dev.tcgshop.client.binder.Ui;
import java.util.Locale;
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
 * 1.1 phone «Ranking» app (server side: dev.tcgshop.phone.Ranking). Left: your rank, capital and what it is made of,
 * the next owner to beat and how far Don Cobbledome still is. Right: the table (top 10, your neighbourhood, ranks 90-100)
 * with each owner's shop, profit % and capital. Refreshed every few seconds while open.
 */
public final class RankingApp {
    private static final int SURF = 0xF0171A26, SURF2 = 0xF01F2333, LINE = 0xFF2C3149;
    private static final int GOLD = 0xFFFFD34D, ME = 0xFF4EE2EC, GOOD = 0xFF6BE07B, BAD = 0xFFFF6B6B;

    private double scroll;
    private double lastRequest;

    private static double now() { return System.nanoTime() / 1e9; }

    public void onEnter() {
        scroll = 0;
        lastRequest = now();
        request();
    }

    private static void request() {
        CompoundTag d = new CompoundTag();
        d.putString("a", "ranking");
        PhoneClient.send(d);
    }

    public void scroll(double v) {
        scroll -= v * 24;
    }

    /** 1,234 / 12.5K / 3.4M / 7.8B / 100T. */
    public static String compact(long v) {
        long a = Math.abs(v);
        String s;
        if (a >= 1_000_000_000_000L) s = trim(a / 1e12) + "T";
        else if (a >= 1_000_000_000L) s = trim(a / 1e9) + "B";
        else if (a >= 1_000_000L) s = trim(a / 1e6) + "M";
        else if (a >= 10_000L) s = trim(a / 1e3) + "K";
        else s = String.valueOf(a);
        return (v < 0 ? "-" : "") + "₽" + s;
    }

    private static String trim(double v) {
        String s = v >= 100 ? String.format(Locale.ROOT, "%.0f", v) : String.format(Locale.ROOT, "%.1f", v);
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }

    public void render(GuiGraphics ctx, int x, int y, int w, int h, int mouseX, int mouseY) {
        if (now() - lastRequest > 6) {
            lastRequest = now();
            request();
        }
        Font tr = Minecraft.getInstance().font;
        CompoundTag d = PhoneAppsClient.ranking;
        if (d == null) {
            Ui.text(ctx, Component.translatable("tcgshop.stats.loading"), x, y + h / 2 - 5, w, 10, Ui.MUTED, false);
            return;
        }
        // ---------------------------------------------------------------- left: you
        int lw = Mth.clamp(Math.round(w * 0.36f), 150, 215);
        Ui.roundedGradient(ctx, x, y, lw, h, 8, SURF2, SURF);
        Ui.roundedOutline(ctx, x, y, lw, h, 8, LINE);
        int rank = d.getInt("rank"), total = d.getInt("total");
        ctx.drawString(tr, Component.translatable("tcgshop.rank.goal").withStyle(ChatFormatting.BOLD), x + 8, y + 6, GOLD, false);
        int ly = y + 18;
        for (FormattedCharSequence line : tr.split(Component.translatable("tcgshop.rank.goal_text"), lw - 16)) {
            ctx.drawString(tr, line, x + 8, ly, 0xFFB8BED6, false);
            ly += 10;
        }
        ly += 4;
        // rank medal
        int cx = x + 26, cy = ly + 16;
        StylePainter.circle(ctx, cx, cy, 15, rank == 1 ? GOLD : rank <= 10 ? 0xFFB8C4D6 : 0xFF3A6FD8);
        StylePainter.circle(ctx, cx, cy, 12, 0xFF141826);
        String rk = rank <= 0 ? "—" : "#" + rank;
        dev.tcgshop.client.screen.Draw.scaledCentered(ctx, Component.literal(rk).withStyle(ChatFormatting.BOLD), cx, cy - 4, rk.length() > 4 ? 0.8f : 1f, 0xFFFFFFFF);
        ctx.drawString(tr, Component.translatable("tcgshop.rank.your_rank", rank, total), x + 48, ly + 4, 0xFFE4E8F4, false);
        ctx.drawString(tr, Component.literal(compact(d.getLong("capital"))).withStyle(ChatFormatting.BOLD), x + 48, ly + 16, ME, true);
        double myProfit = d.getDouble("profit");
        ctx.drawString(tr, Component.translatable("tcgshop.rank.profit", String.format(Locale.ROOT, "%+.1f%%", myProfit)), x + 48, ly + 27,
                myProfit >= 0 ? GOOD : BAD, false);
        ly += 42;
        String[][] parts = {
                {"tcgshop.rank.part.wallet", compact(d.getLong("wallet"))},
                {"tcgshop.rank.part.registers", compact(d.getLong("registers"))},
                {"tcgshop.rank.part.shops", compact(d.getLong("shops"))},
                {"tcgshop.rank.part.assets", compact(d.getLong("assets"))},
                {"tcgshop.rank.part.owed", d.getLong("owed") > 0 ? "-" + compact(d.getLong("owed")) : compact(0)}};
        for (String[] p : parts) {
            ctx.drawString(tr, Component.translatable(p[0]), x + 8, ly, 0xFF9A9FB8, false);
            ctx.drawString(tr, p[1], x + lw - 8 - tr.width(p[1]), ly, 0xFFE4E8F4, false);
            ly += 10;
        }
        ly += 4;
        if (d.contains("nextName")) {
            long gap = Math.max(0, d.getLong("nextCapital") - d.getLong("capital"));
            Component who = PhoneAppsClient.arg(d.getString("nextName"));
            for (FormattedCharSequence line : tr.split(Component.translatable("tcgshop.rank.next", who, compact(gap + 1)), lw - 16)) {
                if (ly + 10 > y + h - 30) break;
                ctx.drawString(tr, line, x + 8, ly, 0xFFFFC266, false);
                ly += 10;
            }
        }
        // progress towards Don Cobbledome (log scale: 1 -> 100 trillion)
        long cap = Math.max(1, d.getLong("capital")), don = Math.max(2, d.getLong("don"));
        float f = (float) Mth.clamp(Math.log10(cap) / Math.log10(don), 0, 1);
        int py = y + h - 24;
        ctx.drawString(tr, Component.translatable(d.getBoolean("beatDon") ? "tcgshop.rank.don_beaten" : "tcgshop.rank.don_gap",
                compact(Math.max(0, don - cap))), x + 8, py, d.getBoolean("beatDon") ? GOLD : 0xFF9A9FB8, false);
        Ui.progress(ctx, x + 8, py + 12, lw - 16, 6, f, GOLD);
        // ---------------------------------------------------------------- right: table
        int px = x + lw + 6, pw = w - lw - 6;
        Ui.roundedGradient(ctx, px, y, pw, h, 8, SURF2, SURF);
        Ui.roundedOutline(ctx, px, y, pw, h, 8, LINE);
        ctx.drawString(tr, Component.translatable("tcgshop.rank.col.owner").withStyle(ChatFormatting.BOLD), px + 30, y + 5, 0xFF9A9FB8, false);
        Component cp = Component.translatable("tcgshop.rank.col.capital").withStyle(ChatFormatting.BOLD);
        ctx.drawString(tr, cp, px + pw - 8 - tr.width(cp), y + 5, 0xFF9A9FB8, false);
        Component pf = Component.translatable("tcgshop.rank.col.profit").withStyle(ChatFormatting.BOLD);
        int profitX = px + pw - 76;
        ctx.drawString(tr, pf, profitX + 30 - tr.width(pf), y + 5, 0xFF9A9FB8, false);
        ListTag rows = d.getList("rows", Tag.TAG_COMPOUND);
        int top = y + 16, rowH = 22, listH = h - 18;
        ctx.enableScissor(px + 1, top, px + pw - 1, y + h - 2);
        int ry = top - (int) scroll, prev = 0;
        for (int i = 0; i < rows.size(); i++) {
            CompoundTag r = rows.getCompound(i);
            int rr = r.getInt("r");
            if (prev > 0 && rr > prev + 1) { // gap in the table
                ctx.drawString(tr, "⋮", px + pw / 2, ry + 1, 0xFF5A6078, false);
                ry += 10;
            }
            prev = rr;
            boolean me = r.getBoolean("me"), real = r.getBoolean("real"), isDon = r.contains("npc") && r.getInt("npc") == 0;
            int bg = me ? 0x6624A6B8 : isDon ? 0x55705010 : rr <= 10 ? 0x33FFFFFF : 0x1AFFFFFF;
            Ui.rounded(ctx, px + 4, ry, pw - 8, rowH - 2, 4, bg);
            if (me) Ui.roundedOutline(ctx, px + 4, ry, pw - 8, rowH - 2, 4, ME);
            int medal = rr == 1 ? GOLD : rr == 2 ? 0xFFD0D6E0 : rr == 3 ? 0xFFD08A4A : 0xFF7A8098;
            String num = String.valueOf(rr);
            ctx.drawString(tr, num, px + 20 - tr.width(num) / 2 - 4, ry + 6, medal, true);
            Component name = PhoneAppsClient.arg(r.getString("n"));
            Component shop = r.contains("npc") && r.getInt("npc") < 10 ? PhoneAppsClient.arg(r.getString("s"))
                    : Component.translatable(real || me ? "tcgshop.rank.shop_player" : "tcgshop.rank.shop_of", PhoneAppsClient.arg(r.getString("s")));
            int nameW = profitX - px - 34;
            ctx.drawString(tr, Ui.ellipsis(name.getString(), nameW), px + 30, ry + 2, me ? ME : real ? 0xFF9BD45B : isDon ? GOLD : 0xFFFFFFFF, false);
            var m = ctx.pose();
            m.pushPose();
            m.translate(px + 30, ry + 12, 0);
            m.scale(0.75f, 0.75f, 1);
            ctx.drawString(tr, Ui.ellipsis(shop.getString(), (int) (nameW / 0.75f)), 0, 0, 0xFF8A90A8, false);
            m.popPose();
            double profit = r.getDouble("p");
            String ps = String.format(Locale.ROOT, "%+.1f%%", profit);
            ctx.drawString(tr, ps, profitX + 30 - tr.width(ps), ry + 6, profit >= 0 ? GOOD : BAD, false);
            String c = compact(r.getLong("c"));
            ctx.drawString(tr, c, px + pw - 10 - tr.width(c), ry + 6, rr == 1 ? GOLD : 0xFFE4E8F4, false);
            ry += rowH;
        }
        ctx.disableScissor();
        int content = ry + (int) scroll - top;
        scroll = Mth.clamp(scroll, 0, Math.max(0, content - listH + 4));
    }
}

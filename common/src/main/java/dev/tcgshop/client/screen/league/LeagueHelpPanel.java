package dev.tcgshop.client.screen.league;

import dev.masterball.api.platform.Platform;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.tcgshop.client.binder.Ui;
import dev.tcgshop.duel.Cosmetics;
import dev.tcgshop.duel.League;
import dev.tcgshop.pricing.PriceService;
import dev.tcgshop.ptcg.PtcgDuels;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static dev.tcgshop.client.screen.Draw.withAlpha;

/**
 * TCG League help panel: opens by itself the first time you enter the app (or always, if you tick it) and
 * reopens with the "?" button in the top bar. Briefly explains what each number means: League points and
 * ranks, what each AI level gives and takes, ELO against players, record and streaks, how coins and boards
 * are earned, and the deck rules. All values come from the code (League, PtcgDuels, Cosmetics), they are not
 * hand-written into the texts.
 */
public final class LeagueHelpPanel {
    private static final String[] SECTIONS = {"points", "ai", "pvp", "stats", "rewards", "deck"};
    private static final int[] SECTION_COLORS = {0xFFFFC233, 0xFF5BD68A, 0xFF4FB4FF, 0xFFE05AFF, 0xFFFF9F43, 0xFFE0604A};
    private static final int[] RANK_COLORS = {0xFFB0763A, 0xFFC0C8D4, 0xFFFFC233, 0xFF6FE3D9, 0xFF7FC8FF, 0xFFE05AFF};
    /** Same as League: you start with 1000 points and never drop below 100; ELO with K = 32 and a minimum of 4 per match. */
    private static final int START_RATING = 1000, MIN_RATING = 100, ELO_K = 32, ELO_MIN = 4;
    private static final Path FILE = Platform.INSTANCE.configDir().resolve("tcgshop-league-help.txt");

    private interface Row {
        int height();

        void draw(GuiGraphics ctx, int x, int y, int w);
    }

    private record Hit(int x, int y, int w, int h, Runnable action) {}

    private final List<Hit> hits = new ArrayList<>();
    private boolean open, loaded, seen, always;
    private int section;
    private double scroll;
    private int contentX, contentY, contentW, contentH;
    private int qx, qy, qs;
    private long lastRender;

    private static Font tr() { return Minecraft.getInstance().font; }

    private static int gi(@Nullable JsonObject o, String k) { return o != null && o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsInt() : 0; }

    // ------------------------------------------------------------------ state (remembered in config/tcgshop-league-help.txt)

    private void load() {
        if (loaded) return;
        loaded = true;
        try {
            if (Files.exists(FILE)) {
                String s = Files.readString(FILE, StandardCharsets.UTF_8);
                seen = s.contains("seen=true");
                always = s.contains("always=true");
            }
        } catch (Exception ignored) {}
    }

    private void save() {
        try {
            Files.writeString(FILE, "seen=" + seen + "\nalways=" + always + "\n", StandardCharsets.UTF_8);
        } catch (Exception ignored) {}
    }

    /** Call when opening the League app: the first time (or always, if ticked) the help opens. */
    public void onEnter() {
        load();
        if (!seen || always) {
            open = true;
            section = 0;
            scroll = 0;
        }
    }

    public boolean isOpen() { return open; }

    public void open(int sectionIndex) {
        open = true;
        section = Mth.clamp(sectionIndex, 0, SECTIONS.length - 1);
        scroll = 0;
    }

    public void close() {
        if (!open) return;
        open = false;
        if (!seen) { seen = true; save(); }
    }

    private static void click() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.5f, 1.3f);
    }

    // ------------------------------------------------------------------ drawing

    /**
     * Draws the "?" button in the top bar and, if open, the help on top of everything. (sx, sy, sw, sh) is the
     * phone screen; L is the League data (may be null while loading).
     */
    public void render(GuiGraphics ctx, @Nullable JsonObject L, int sx, int sy, int sw, int sh, int mx, int my, double t) {
        load();
        hits.clear();
        lastRender = System.currentTimeMillis();
        // "?" button (on the right of the app's top bar)
        qs = 16;
        qx = sx + sw - 28;
        qy = sy + 16;
        boolean qh = Ui.in(mx, my, qx, qy, qs, qs);
        if (!seen && !open) {
            float pulse = 0.5f + 0.5f * (float) Math.sin(t * 4);
            Ui.rounded(ctx, qx - 3, qy - 3, qs + 6, qs + 6, 11, withAlpha(0xFFFFD34D, 0.25f + 0.35f * pulse));
        }
        Ui.rounded(ctx, qx, qy, qs, qs, 8, open ? 0xCCFFFFFF : qh ? 0x66FFFFFF : 0x2AFFFFFF);
        if (qh || open) Ui.roundedOutline(ctx, qx, qy, qs, qs, 8, 0xAAFFFFFF);
        Ui.text(ctx, Component.literal("?").withStyle(ChatFormatting.BOLD), qx, qy, qs, qs, open ? 0xFF1A1D2A : 0xFFFFFFFF, !open);
        if (!open) {
            // deferred: the phone draws us inside its scissor and the tooltip would be cut off on the right
            if (qh) DeckBuilderPanel.deferTooltip(List.of(Component.translatable("tcgshop.league.help.button").getVisualOrderText()));
            return;
        }

        // dark backdrop over the whole app. 1.0: flush the app first (its batched text showed through the panel) and
        // draw the panel fully opaque on its own layer
        ctx.flush();
        PoseStack m = ctx.pose();
        m.pushPose();
        m.translate(0, 0, 300);
        ctx.fill(sx, sy + 13, sx + sw, sy + sh - 8, 0xD8060810);
        int x0 = sx + 12, y0 = sy + 18, w = sw - 24, h = sh - 30;
        Ui.rounded(ctx, x0 + 2, y0 + 3, w, h, 8, 0xFF030408);
        Ui.roundedGradient(ctx, x0, y0, w, h, 8, 0xFF1C2030, 0xFF11131D);
        Ui.roundedOutline(ctx, x0, y0, w, h, 8, 0xFF3A4060);
        // header
        ctx.fillGradient(x0 + 1, y0 + 1, x0 + w - 1, y0 + 19, 0x60C62828, 0x10C62828);
        ctx.drawString(tr(), Component.translatable("tcgshop.league.help.title").withStyle(ChatFormatting.BOLD), x0 + 8, y0 + 6, 0xFFFFFFFF, true);
        if (L != null) {
            int rating = gi(L, "rating"), rank = League.rank(rating);
            Component me = Component.translatable("tcgshop.league.help.me", rating, Component.translatable("tcgshop.league.rank." + rank));
            int mw = tr().width(me);
            int titleEnd = x0 + 14 + tr().width(Component.translatable("tcgshop.league.help.title").withStyle(ChatFormatting.BOLD));
            if (x0 + w - 24 - mw > titleEnd) ctx.drawString(tr(), me, x0 + w - 24 - mw, y0 + 6, rankColor(rank), false);
        }
        int cxb = x0 + w - 17, cyb = y0 + 3;
        boolean ch = Ui.in(mx, my, cxb, cyb, 14, 14);
        Ui.rounded(ctx, cxb, cyb, 14, 14, 7, ch ? 0xAAE53935 : 0x33FFFFFF);
        Ui.text(ctx, Component.literal("✕"), cxb, cyb, 14, 14, 0xFFFFFFFF, false);
        hits.add(new Hit(cxb, cyb, 14, 14, () -> { click(); close(); }));

        // sections (left)
        int tabsW = Math.min(104, Math.max(70, w / 4));
        int ty = y0 + 24;
        for (int i = 0; i < SECTIONS.length; i++) {
            int ry = ty + i * 17;
            boolean sel = i == section, hov = Ui.in(mx, my, x0 + 6, ry, tabsW, 15);
            int c = SECTION_COLORS[i];
            if (sel) {
                Ui.roundedGradient(ctx, x0 + 6, ry, tabsW, 15, 5, withAlpha(Ui.brighten(c, 20), 0.5f), withAlpha(c, 0.25f));
                Ui.roundedOutline(ctx, x0 + 6, ry, tabsW, 15, 5, withAlpha(c, 0.9f));
            } else if (hov) Ui.rounded(ctx, x0 + 6, ry, tabsW, 15, 5, 0x22FFFFFF);
            Ui.rounded(ctx, x0 + 9, ry + 4, 2, 7, 1, c);
            String lbl = Ui.ellipsis(Component.translatable("tcgshop.league.help.sec." + SECTIONS[i]).getString(), tabsW - 18);
            ctx.drawString(tr(), lbl, x0 + 15, ry + 4, sel ? 0xFFFFFFFF : hov ? 0xFFE8EAF2 : 0xFFB8BCCB, sel);
            int ii = i;
            hits.add(new Hit(x0 + 6, ry, tabsW, 15, () -> { click(); section = ii; scroll = 0; }));
        }

        // content (right, scrollable)
        contentX = x0 + tabsW + 14;
        contentY = y0 + 24;
        contentW = w - tabsW - 22;
        contentH = h - 24 - 22;
        List<Row> rows = build(L, contentW);
        int total = 0;
        for (Row r : rows) total += r.height();
        scroll = Mth.clamp(scroll, 0, Math.max(0, total - contentH));
        ctx.enableScissor(contentX - 2, contentY, contentX + contentW + 2, contentY + contentH);
        int y = contentY - (int) scroll;
        for (Row r : rows) {
            int rh = r.height();
            if (y + rh >= contentY && y <= contentY + contentH) r.draw(ctx, contentX, y, contentW);
            y += rh;
        }
        ctx.disableScissor();
        if (total > contentH) {
            float frac = (float) (scroll / Math.max(1, total - contentH));
            int bh = Math.max(14, contentH * contentH / total);
            Ui.rounded(ctx, contentX + contentW + 3, contentY + Math.round((contentH - bh) * frac), 2, bh, 1, 0x88FFFFFF);
        }

        // footer: "show on enter" and "Got it"
        int fy = y0 + h - 18;
        ctx.fill(x0 + 6, fy - 3, x0 + w - 6, fy - 2, 0x22FFFFFF);
        boolean cbh = Ui.in(mx, my, x0 + 8, fy + 1, 120, 12);
        Ui.rounded(ctx, x0 + 8, fy + 2, 10, 10, 2, always ? 0xFF3C8A4A : 0xFF1A1D27);
        Ui.roundedOutline(ctx, x0 + 8, fy + 2, 10, 10, 2, cbh ? 0xFFFFFFFF : 0x88FFFFFF);
        if (always) ctx.drawString(tr(), "✔", x0 + 9, fy + 3, 0xFFFFFFFF, false);
        Component alwaysL = Component.translatable("tcgshop.league.help.always");
        ctx.drawString(tr(), alwaysL, x0 + 22, fy + 3, cbh ? 0xFFFFFFFF : 0xFFAEB6CC, false);
        hits.add(new Hit(x0 + 8, fy + 1, 14 + tr().width(alwaysL), 12, () -> { click(); always = !always; seen = true; save(); }));
        Component ok = Component.translatable("tcgshop.league.help.ok");
        int bw = tr().width(ok) + 20;
        Ui.button(ctx, x0 + w - bw - 8, fy, bw, 14, ok, mx, my, true, 0xFF1F8A55);
        hits.add(new Hit(x0 + w - bw - 8, fy, bw, 14, () -> { click(); close(); }));
        if (section < SECTIONS.length - 1) {
            Component next = Component.translatable("tcgshop.league.help.next");
            int nw = tr().width(next) + 14;
            int nx = x0 + w - bw - 8 - nw - 4;
            Ui.button(ctx, nx, fy, nw, 14, next, mx, my, true, 0xFF4A5470);
            hits.add(new Hit(nx, fy, nw, 14, () -> { click(); section++; scroll = 0; }));
        }
        ctx.flush();
        m.popPose();
    }

    private static int rankColor(int rank) { return RANK_COLORS[Mth.clamp(rank, 0, RANK_COLORS.length - 1)]; }

    // ------------------------------------------------------------------ content

    private static Row gap(int h) {
        return new Row() {
            public int height() { return h; }

            public void draw(GuiGraphics ctx, int x, int y, int w) {}
        };
    }

    private static Row heading(Component t, int color) {
        return new Row() {
            public int height() { return 14; }

            public void draw(GuiGraphics ctx, int x, int y, int w) {
                ctx.fill(x, y + 2, x + 2, y + 10, color);
                ctx.drawString(tr(), t.copy().withStyle(ChatFormatting.BOLD), x + 5, y + 2, color, true);
                ctx.fill(x + 5, y + 12, x + w, y + 13, withAlpha(color, 0.25f));
            }
        };
    }

    private static Row para(Component t, int color, int width, boolean bullet) {
        int indent = bullet ? 8 : 0;
        List<FormattedCharSequence> lines = tr().split(t, Math.max(40, width - indent));
        return new Row() {
            public int height() { return lines.size() * 10 + 2; }

            public void draw(GuiGraphics ctx, int x, int y, int w) {
                if (bullet) ctx.drawString(tr(), "•", x + 1, y, 0xFF8A90A8, false);
                for (int i = 0; i < lines.size(); i++) ctx.drawString(tr(), lines.get(i), x + indent, y + i * 10, color, false);
            }
        };
    }

    /** Table row: centered cells that shrink if they do not fit; the first one is left-aligned. */
    private static Row tableRow(Component[] cells, int[] colors, float[] weights, boolean header, boolean highlight, int accent) {
        return new Row() {
            public int height() { return 12; }

            public void draw(GuiGraphics ctx, int x, int y, int w) {
                if (header) Ui.rounded(ctx, x, y, w, 11, 3, withAlpha(accent, 0.28f));
                else if (highlight) {
                    Ui.rounded(ctx, x, y, w, 11, 3, withAlpha(accent, 0.22f));
                    Ui.roundedOutline(ctx, x, y, w, 11, 3, withAlpha(accent, 0.8f));
                } else Ui.rounded(ctx, x, y, w, 11, 3, 0x18FFFFFF);
                float sum = 0;
                for (float f : weights) sum += f;
                float cx = x;
                for (int i = 0; i < cells.length; i++) {
                    int cw = Math.round(w * weights[i] / sum);
                    Component c = header ? cells[i].copy().withStyle(ChatFormatting.BOLD) : cells[i];
                    if (i == 0) {
                        String s = Ui.ellipsis(c.getString(), cw - 6);
                        ctx.drawString(tr(), header ? Component.literal(s).withStyle(ChatFormatting.BOLD) : Component.literal(s), Math.round(cx) + 4, y + 2, colors[i], false);
                    } else Ui.text(ctx, c, Math.round(cx), y + 2, cw, 8, colors[i], false);
                    cx += cw;
                }
            }
        };
    }

    private static Component tx(String key, Object... args) { return Component.translatable("tcgshop.league.help." + key, args); }

    private static Component lit(String s) { return Component.literal(s); }

    private static String money(long v) { return PriceService.format(v); }

    private static String levelName(int k) { return Component.translatable("tcgshop.ptcg.ui.level." + k).getString(); }

    private static String rankName(int r) { return Component.translatable("tcgshop.league.rank." + r).getString(); }

    /** Winner's ELO change (same calculation as League.record). */
    private static int elo(int winner, int loser) {
        double expected = 1 / (1 + Math.pow(10, (loser - winner) / 400.0));
        return Math.max(ELO_MIN, (int) Math.round(ELO_K * (1 - expected)));
    }

    private List<Row> build(@Nullable JsonObject L, int w) {
        List<Row> rows = new ArrayList<>();
        int c = SECTION_COLORS[section];
        int body = 0xFFD6DAE6, soft = 0xFF9AA0B8;
        int rating = L != null ? gi(L, "rating") : START_RATING;
        int[] at = League.RANK_AT;
        int nLevels = League.AI_WIN.length;
        JsonObject cos = L != null && L.has("cos") ? L.getAsJsonObject("cos") : new JsonObject();
        switch (SECTIONS[section]) {
            case "points" -> {
                rows.add(heading(tx("points.h"), c));
                rows.add(para(tx("points.p1", START_RATING, MIN_RATING), body, w, false));
                rows.add(para(tx("points.p2"), body, w, false));
                rows.add(gap(4));
                rows.add(heading(tx("ranks.h"), c));
                float[] wt = {1.4f, 1f, 2.2f};
                rows.add(tableRow(new Component[]{tx("col.rank"), tx("col.from"), tx("col.ai_allowed")}, new int[]{0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF}, wt, true, false, c));
                int myRank = League.rank(rating);
                for (int r = 0; r < at.length; r++) {
                    List<String> allowed = new ArrayList<>();
                    for (int k = 0; k < nLevels; k++) if (r < League.AI_BLOCKED_FROM[k]) allowed.add(levelName(k));
                    rows.add(tableRow(new Component[]{lit(rankName(r)), lit(at[r] + " pts"), lit(String.join(", ", allowed))},
                            new int[]{rankColor(r), 0xFFFFE08A, 0xFFC8CEE0}, wt, false, r == myRank && L != null, rankColor(r)));
                }
                rows.add(gap(3));
                rows.add(para(tx("ranks.p1"), soft, w, false));
                if (L != null) {
                    int rank = League.rank(rating), peak = Math.max(rating, gi(cos, "peak"));
                    Component now = rank + 1 < at.length
                            ? tx("ranks.now", rating, rankName(rank), peak, at[rank + 1] - rating, rankName(rank + 1))
                            : tx("ranks.now_top", rating, rankName(rank), peak);
                    rows.add(para(now, rankColor(rank), w, false));
                }
            }
            case "ai" -> {
                rows.add(heading(tx("ai.h", nLevels), c));
                float[] wt = {1.3f, 1f, 0.9f, 0.9f, 1.2f, 1.2f};
                rows.add(tableRow(new Component[]{tx("col.level"), tx("col.ai_rating"), tx("col.win"), tx("col.loss"), tx("col.reward"), tx("col.penalty")},
                        new int[]{0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF}, wt, true, false, c));
                var levels = L != null && L.has("levels") ? L.getAsJsonArray("levels") : null;
                for (int k = 0; k < nLevels; k++) {
                    JsonObject lv = levels != null && k < levels.size() ? levels.get(k).getAsJsonObject() : null;
                    int aiRating = lv != null && lv.has("rating") ? gi(lv, "rating") : k < PtcgDuels.LEVEL_RATING.length ? PtcgDuels.LEVEL_RATING[k] : 0;
                    int gain = lv != null && lv.has("gain") ? gi(lv, "gain") : League.AI_WIN[k];
                    int loss = lv != null && lv.has("loss") ? gi(lv, "loss") : League.AI_LOSS[k];
                    long reward = lv != null && lv.has("reward") ? lv.get("reward").getAsLong() : k < PtcgDuels.LEVEL_REWARD.length ? PtcgDuels.LEVEL_REWARD[k] : 0;
                    long pen = k < PtcgDuels.LEVEL_PENALTY.length ? PtcgDuels.LEVEL_PENALTY[k] : 0;
                    rows.add(tableRow(new Component[]{lit(levelName(k)), lit(String.valueOf(aiRating)), lit("+" + gain), lit("-" + loss), lit(money(reward)), lit(money(pen))},
                            new int[]{0xFFFFFFFF, 0xFFAEB6CC, 0xFF6BE07B, 0xFFFF8A80, 0xFFFFD34D, 0xFFFF9F80}, wt, false, false, c));
                }
                rows.add(gap(3));
                rows.add(para(tx("ai.cols"), soft, w, false));
                rows.add(para(tx("ai.casual"), body, w, true));
                rows.add(para(tx("ai.ranked"), body, w, true));
                // levels that get locked in League as you rank up
                List<String> blocks = new ArrayList<>();
                for (int k = 0; k < nLevels; k++)
                    if (League.AI_BLOCKED_FROM[k] < at.length) blocks.add(tx("ai.block_item", levelName(k), rankName(League.AI_BLOCKED_FROM[k])).getString());
                if (!blocks.isEmpty()) rows.add(para(tx("ai.blocked", String.join(" · ", blocks)), body, w, true));
                rows.add(para(tx("ai.meta"), body, w, true));
            }
            case "pvp" -> {
                rows.add(heading(tx("pvp.h"), c));
                rows.add(para(tx("pvp.p1"), body, w, false));
                rows.add(para(tx("pvp.formula", ELO_K, ELO_MIN), soft, w, false));
                rows.add(gap(3));
                float[] wt = {1.6f, 1f, 1f};
                rows.add(tableRow(new Component[]{tx("col.diff"), tx("col.if_win"), tx("col.if_loss")}, new int[]{0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF}, wt, true, false, c));
                for (int d : new int[]{-400, -200, 0, 200, 400}) {
                    int me = 1200, opp = me + d;
                    String label = d == 0 ? tx("pvp.same").getString() : tx(d > 0 ? "pvp.above" : "pvp.below", Math.abs(d)).getString();
                    rows.add(tableRow(new Component[]{lit(label), lit("+" + elo(me, opp)), lit("-" + elo(opp, me))},
                            new int[]{0xFFC8CEE0, 0xFF6BE07B, 0xFFFF8A80}, wt, false, d == 0, c));
                }
                rows.add(gap(3));
                rows.add(para(tx("pvp.penalty", money(PtcgDuels.PVP_PENALTY)), body, w, true));
                rows.add(para(tx("pvp.modes"), body, w, true));
                rows.add(para(tx("pvp.friendly"), body, w, true));
            }
            case "stats" -> {
                rows.add(heading(tx("stats.h"), c));
                for (String k : new String[]{"wl", "winrate", "ai_wins", "played", "streak", "peak", "top"})
                    rows.add(para(tx("stats." + k), body, w, true));
                if (L != null) {
                    JsonObject pr = cos.has("progress") ? cos.getAsJsonObject("progress") : new JsonObject();
                    int wins = gi(L, "wins"), losses = gi(L, "losses");
                    rows.add(gap(4));
                    rows.add(para(tx("stats.you", wins, losses, wins + losses == 0 ? 0 : Math.round(wins * 100f / (wins + losses)), gi(L, "aiWins"),
                            gi(pr, "PLAYED"), gi(cos, "streak"), gi(pr, "STREAK"), Math.max(rating, gi(cos, "peak"))), 0xFFFFE08A, w, false));
                }
            }
            case "rewards" -> {
                rows.add(heading(tx("rewards.h"), c));
                rows.add(para(tx("rewards.p1"), body, w, false));
                rows.add(gap(3));
                float[] wt = {2.2f, 1f, 1f};
                rows.add(tableRow(new Component[]{tx("col.rule"), tx("col.coins"), tx("col.boards")}, new int[]{0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF}, wt, true, false, c));
                for (Cosmetics.Rule r : Cosmetics.Rule.values()) {
                    int coins = 0, boards = 0;
                    for (Cosmetics.Coin co : Cosmetics.COINS) if (co.rule() == r) coins++;
                    for (Cosmetics.Board b : Cosmetics.BOARDS) if (b.rule() == r) boards++;
                    if (coins + boards == 0) continue;
                    rows.add(tableRow(new Component[]{tx("rule." + r.name().toLowerCase(Locale.ROOT)), lit(String.valueOf(coins)), lit(String.valueOf(boards))},
                            new int[]{0xFFC8CEE0, 0xFFFFD34D, 0xFF8BE0FF}, wt, false, false, c));
                }
                rows.add(gap(3));
                rows.add(para(tx("rewards.rank"), body, w, true));
                double chance = Cosmetics.LOOT_CHANCE;
                String pct = String.format(Locale.ROOT, "%.2f%%", chance * 100);
                rows.add(para(tx("rewards.loot", pct, chance > 0 ? Math.round(1 / chance) : 0), body, w, true));
                rows.add(para(tx("rewards.money"), body, w, true));
                rows.add(para(tx("rewards.goals"), soft, w, false));
            }
            default -> {
                rows.add(heading(tx("deck.h"), c));
                for (String k : new String[]{"size", "copies", "basic", "special", "combos", "format", "owned", "auto"})
                    rows.add(para(tx("deck." + k), body, w, true));
                rows.add(gap(4));
                rows.add(heading(tx("editor.h"), c));
                for (String k : new String[]{"counter", "controls", "autocomplete", "check", "warnings"})
                    rows.add(para(tx("editor." + k), body, w, true));
            }
        }
        rows.add(gap(6));
        return rows;
    }

    // ------------------------------------------------------------------ input (forwarded by the phone)

    private boolean shown() { return System.currentTimeMillis() - lastRender < 250; }

    /** Click: the "?" button opens / closes; while the help is open it takes all clicks. */
    public boolean mouseClicked(double mx, double my, int button) {
        if (!shown()) return false;
        if (Ui.in(mx, my, qx, qy, qs, qs)) {
            click();
            if (open) close();
            else open(section);
            return true;
        }
        if (!open) return false;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (Ui.in(mx, my, h.x, h.y, h.w, h.h)) { h.action.run(); return true; }
        }
        return true;
    }

    public boolean mouseScrolled(double mx, double my, double v) {
        if (!open || !shown()) return false;
        scroll -= v * 24;
        return true;
    }

    /** Esc closes the help; arrows / Page Up/Down change section. While the help is open no keys get through. */
    public boolean keyPressed(int keyCode) {
        if (!open || !shown()) return false;
        switch (keyCode) {
            case GLFW.GLFW_KEY_ESCAPE, GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> close();
            case GLFW.GLFW_KEY_DOWN -> scroll += 20;
            case GLFW.GLFW_KEY_UP -> scroll -= 20;
            case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_PAGE_DOWN, GLFW.GLFW_KEY_TAB -> { section = (section + 1) % SECTIONS.length; scroll = 0; }
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_PAGE_UP -> { section = Math.floorMod(section - 1, SECTIONS.length); scroll = 0; }
            default -> { }
        }
        return true;
    }
}

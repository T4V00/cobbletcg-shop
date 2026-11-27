package dev.tcgshop.client.screen;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.tcgshop.client.binder.Ui;
import dev.tcgshop.duel.Cosmetics;
import dev.tcgshop.duel.League;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

import static dev.tcgshop.client.screen.Draw.withAlpha;

/**
 * League cosmetics tabs on the phone: Coins (showcase of spinning 3D coins), Backgrounds (HD thumbnails of
 * the boards) and Achievements (what is left for each unlock, with its bar). Click something you own = equip it.
 */
public final class CosmeticsPanel {
    public interface AreaSink { void add(int x, int y, int w, int h, Runnable r); }

    private static final int SURF = 0xF0171A26, SURF2 = 0xF01F2333, LINE = 0xFF2C3149;
    private String hoverKey = "";
    private double hoverAt;

    private static Font tr() { return Minecraft.getInstance().font; }

    // ------------------------------------------------------------------ data

    private static JsonObject cos(JsonObject L) {
        return L != null && L.has("cos") ? L.getAsJsonObject("cos") : new JsonObject();
    }

    private static Set<String> unlocked(JsonObject c) {
        Set<String> s = new HashSet<>();
        if (c.has("unlocked")) for (var e : c.getAsJsonArray("unlocked")) s.add(e.getAsString());
        return s;
    }

    private static int prog(JsonObject c, Cosmetics.Rule r) {
        return c.has("progress") && c.getAsJsonObject("progress").has(r.name()) ? c.getAsJsonObject("progress").get(r.name()).getAsInt() : 0;
    }

    /** Text describing how it is obtained (with the rank translated where applicable). */
    public static Component ruleText(Cosmetics.Rule r, int arg) {
        Object a = r == Cosmetics.Rule.RANK ? Component.translatable("tcgshop.league.rank." + Math.min(arg, League.RANK_AT.length - 1)) : arg;
        return Component.translatable("tcgshop.cos.rule." + r.name().toLowerCase(), a);
    }

    private static float frac(JsonObject c, Cosmetics.Rule r, int arg) {
        if (r == Cosmetics.Rule.FREE) return 1;
        if (r == Cosmetics.Rule.LOOT) return 0;
        return Mth.clamp(prog(c, r) / (float) Math.max(1, arg), 0, 1);
    }

    private static void equip(String kind, String id) {
        JsonObject o = new JsonObject();
        o.addProperty("a", "cosmetic");
        o.addProperty("kind", kind);
        o.addProperty("id", id);
        dev.tcgshop.client.ClientPtcg.leagueAction(o);
    }

    private static void click(boolean ok) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.playSound(ok ? SoundEvents.UI_BUTTON_CLICK.value() : SoundEvents.NOTE_BLOCK_BASS.value(), 0.6f, ok ? 1.3f : 0.6f);
    }

    // ------------------------------------------------------------------ coins

    public double coins(GuiGraphics ctx, JsonObject L, int px, int py, int pw, int ph, int mx, int my, double t, double scroll, AreaSink areas) {
        JsonObject c = cos(L);
        Set<String> un = unlocked(c);
        String eq = c.has("coin") ? c.get("coin").getAsString() : "gold";
        List<Cosmetics.Coin> list = Cosmetics.COINS;
        int have = 0;
        for (Cosmetics.Coin k : list) if (un.contains("coin:" + k.id())) have++;
        // large showcase on the left: the hovered one (or the equipped one)
        int lw = Math.min(118, pw / 3);
        Cosmetics.Coin show = Cosmetics.coin(hoverKey.startsWith("coin:") ? hoverKey.substring(5) : eq);
        if (show == null) show = list.get(0);
        boolean showOwned = un.contains("coin:" + show.id());
        Ui.roundGradient(ctx, px, py, lw, ph, SURF2, SURF);
        Ui.outline(ctx, px, py, lw, ph, LINE);
        float cr = Math.min(lw * 0.3f, ph * 0.18f);
        int ccx = px + lw / 2, ccy = py + 14 + Math.round(cr * 1.3f);
        Draw.glow(ctx, ccx, ccy, cr * 3.2f, cr * 3.2f, show.face() | 0xFF000000, 0.25f + 0.1f * (float) Math.sin(t * 2));
        PoseStack m = ctx.pose();
        m.pushPose();
        m.translate(ccx, ccy + cr * 1.2f, 0);
        Coin3D.shadow(ctx, cr, 0.5f);
        m.popPose();
        m.pushPose();
        m.translate(ccx, ccy + (float) Math.sin(t * 2.2) * 2, 0);
        Coin3D.draw(ctx, cr, (float) ((t * 70) % 360), 14 + 8 * (float) Math.sin(t * 1.3), Cosmetics.coinIndex(show.id()), showOwned ? 1f : 0.55f);
        m.popPose();
        int ty = ccy + Math.round(cr * 1.6f) + 6;
        Ui.text(ctx, Component.translatable("tcgshop.coin." + show.id()).withStyle(ChatFormatting.BOLD), px, ty, lw, 10, showOwned ? Ui.GOLD : 0xFFB8BCCB, true);
        ty += 12;
        ty = wrapC(ctx, ruleText(show.rule(), show.arg()), px + 6, ty, lw - 12, 0xFF9AA0B8);
        if (!showOwned && show.rule() != Cosmetics.Rule.LOOT) {
            float f = frac(c, show.rule(), show.arg());
            Ui.progress(ctx, px + 10, ty + 2, lw - 20, 5, f, 0xFFE8B730);
            String pr = Math.min(prog(c, show.rule()), show.arg()) + " / " + show.arg();
            if (show.rule() == Cosmetics.Rule.RANK) pr = Math.round(f * 100) + "%";
            Ui.text(ctx, Component.literal(pr), px, ty + 9, lw, 10, 0xFFCCCCCC, false);
        } else if (showOwned) {
            boolean on = show.id().equals(eq);
            Ui.text(ctx, Component.translatable(on ? "tcgshop.cos.equipped" : "tcgshop.cos.click_equip"), px, ty + 3, lw, 10, on ? 0xFF6BE07B : 0xFFDDE3F0, false);
        }
        // grid
        int gx = px + lw + 8, gw = pw - lw - 8;
        ctx.drawString(tr(), Component.translatable("tcgshop.cos.coins_title", have, list.size()).withStyle(ChatFormatting.BOLD), gx, py + 2, 0xFFFFFFFF, false);
        int cell = 50, cellH = 62, cols = Math.max(1, gw / cell), rows = (list.size() + cols - 1) / cols;
        int top = py + 14, viewH = ph - 14;
        scroll = Mth.clamp(scroll, 0, Math.max(0, rows * cellH - viewH));
        int ox = gx + (gw - cols * cell) / 2;
        String newHover = "";
        ctx.enableScissor(gx, top, gx + gw, top + viewH);
        for (int i = 0; i < list.size(); i++) {
            Cosmetics.Coin k = list.get(i);
            int cx = ox + (i % cols) * cell, cy = top + (i / cols) * cellH - (int) scroll;
            if (cy + cellH < top || cy > top + viewH) continue;
            boolean owned = un.contains("coin:" + k.id()), on = k.id().equals(eq);
            boolean hover = Ui.in(mx, my, cx + 2, cy + 2, cell - 4, cellH - 4) && Ui.in(mx, my, gx, top, gw, viewH);
            if (hover) newHover = "coin:" + k.id();
            int bg = on ? withAlpha(Ui.GOLD, 0.22f) : hover ? 0x33FFFFFF : 0x1EFFFFFF;
            Ui.roundGradient(ctx, cx + 2, cy + 2, cell - 4, cellH - 4, bg, withAlpha(bg, 0.4f));
            if (on) Ui.outline(ctx, cx + 2, cy + 2, cell - 4, cellH - 4, Ui.GOLD);
            else if (hover) Ui.outline(ctx, cx + 2, cy + 2, cell - 4, cellH - 4, 0x88FFFFFF);
            float r = 14;
            m.pushPose();
            m.translate(cx + cell / 2f, cy + 24, 0);
            float flip = hover ? (float) ((t * 160) % 360) : (float) Math.sin(t * 1.4 + i) * 18;
            Coin3D.draw(ctx, r, flip, 12, i, owned ? 1f : 0.32f);
            m.popPose();
            if (!owned) lock(ctx, cx + cell / 2, cy + 22);
            if (on) {
                Ui.round(ctx, cx + cell - 13, cy + 4, 9, 9, 0xFF2E9C5A);
                ctx.drawString(tr(), "✔", cx + cell - 12, cy + 5, 0xFFFFFFFF, false);
            }
            Component name = Component.translatable("tcgshop.coin." + k.id());
            small(ctx, name, cx + cell / 2f, cy + 44, cell - 6, owned ? 0xFFFFFFFF : 0xFF8A8FA8);
            if (!owned && k.rule() != Cosmetics.Rule.LOOT) Ui.progress(ctx, cx + 8, cy + cellH - 9, cell - 16, 3, frac(c, k.rule(), k.arg()), 0xFFE8B730);
            else if (!owned) small(ctx, Component.translatable("tcgshop.cos.loot"), cx + cell / 2f, cy + cellH - 11, cell - 6, 0xFFC890FF);
            final boolean o2 = owned;
            clip(areas, cx + 2, cy + 2, cell - 4, cellH - 4, top, viewH, () -> {
                click(o2);
                if (o2) equip("coin", k.id());
            });
        }
        ctx.disableScissor();
        scrollbar(ctx, gx + gw - 3, top, viewH, rows * cellH, scroll);
        hoverKey = newHover;
        return scroll;
    }

    // ------------------------------------------------------------------ backgrounds

    public double boards(GuiGraphics ctx, JsonObject L, int px, int py, int pw, int ph, int mx, int my, double t, double scroll, AreaSink areas) {
        JsonObject c = cos(L);
        Set<String> un = unlocked(c);
        String eq = c.has("board") ? c.get("board").getAsString() : "classic";
        List<Cosmetics.Board> list = Cosmetics.BOARDS;
        int have = 0;
        for (Cosmetics.Board b : list) if (un.contains("board:" + b.id())) have++;
        // large preview: a mini board with the hovered background (or the equipped one)
        int lw = Math.min(130, pw / 3);
        Cosmetics.Board show = Cosmetics.board(hoverKey.startsWith("board:") ? hoverKey.substring(6) : eq);
        if (show == null) show = list.get(0);
        boolean showOwned = un.contains("board:" + show.id());
        Ui.roundGradient(ctx, px, py, lw, ph, SURF2, SURF);
        Ui.outline(ctx, px, py, lw, ph, LINE);
        int vx = px + 6, vy = py + 6, vw = lw - 12, vh = Math.min(ph - 70, Math.round(vw * 1.15f));
        swatch(ctx, show.id(), vx, vy, vw, vh, t, 72);
        // mini board on top
        int cw = vw / 6, chh = Math.round(cw / Draw.CARD_RATIO);
        for (int side = 0; side < 2; side++) {
            int ay = side == 0 ? vy + 8 : vy + vh - chh - 8;
            Ui.round(ctx, vx + vw / 2 - cw / 2 - 2, ay - 2, cw + 4, chh + 4, 0x55FFFFFF);
            ctx.fill(vx + vw / 2 - cw / 2, ay, vx + vw / 2 + cw / 2, ay + chh, side == 0 ? 0xCCE05050 : 0xCC3A8FD8);
            for (int b = -2; b <= 2; b++) if (b != 0)
                ctx.fill(vx + vw / 2 + b * (cw + 3) - cw / 2 + 1, side == 0 ? ay + 2 : ay + 2, vx + vw / 2 + b * (cw + 3) + cw / 2 - 1, ay + chh - 2, 0x44FFFFFF);
        }
        ctx.fill(vx + 6, vy + vh / 2, vx + vw - 6, vy + vh / 2 + 1, 0x88FFFFFF);
        if (!showOwned) {
            ctx.fill(vx, vy, vx + vw, vy + vh, 0x88000000);
            lock(ctx, vx + vw / 2, vy + vh / 2);
        }
        Ui.outline(ctx, vx - 1, vy - 1, vw + 2, vh + 2, show.id().equals(eq) ? Ui.GOLD : 0x66FFFFFF);
        int ty = vy + vh + 5;
        Ui.text(ctx, Component.translatable("tcgshop.board." + show.id()).withStyle(ChatFormatting.BOLD), px, ty, lw, 10, showOwned ? (show.accent() | 0xFF000000) : 0xFFB8BCCB, true);
        ty = wrapC(ctx, ruleText(show.rule(), show.arg()), px + 6, ty + 12, lw - 12, 0xFF9AA0B8);
        if (!showOwned) Ui.progress(ctx, px + 10, ty + 2, lw - 20, 5, frac(c, show.rule(), show.arg()), show.accent() | 0xFF000000);
        else Ui.text(ctx, Component.translatable(show.id().equals(eq) ? "tcgshop.cos.equipped" : "tcgshop.cos.click_equip"), px, ty + 1, lw, 10,
                show.id().equals(eq) ? 0xFF6BE07B : 0xFFDDE3F0, false);
        // thumbnail grid
        int gx = px + lw + 8, gw = pw - lw - 8;
        ctx.drawString(tr(), Component.translatable("tcgshop.cos.boards_title", have, list.size()).withStyle(ChatFormatting.BOLD), gx, py + 2, 0xFFFFFFFF, false);
        int cols = Math.max(1, gw / 84), cell = gw / cols, cellH = Math.round(cell * 0.62f) + 14;
        int rows = (list.size() + cols - 1) / cols, top = py + 14, viewH = ph - 14;
        scroll = Mth.clamp(scroll, 0, Math.max(0, rows * cellH - viewH));
        String newHover = "";
        ctx.enableScissor(gx, top, gx + gw, top + viewH);
        for (int i = 0; i < list.size(); i++) {
            Cosmetics.Board b = list.get(i);
            int cx = gx + (i % cols) * cell, cy = top + (i / cols) * cellH - (int) scroll;
            if (cy + cellH < top || cy > top + viewH) continue;
            boolean owned = un.contains("board:" + b.id()), on = b.id().equals(eq);
            boolean hover = Ui.in(mx, my, cx + 2, cy + 2, cell - 4, cellH - 4) && Ui.in(mx, my, gx, top, gw, viewH);
            if (hover) newHover = "board:" + b.id();
            int tw = cell - 6, th = cellH - 16, tx = cx + 3, tyy = cy + 2 - (hover ? 1 : 0);
            ctx.fill(tx + 2, tyy + 3, tx + tw + 2, tyy + th + 3, 0x66000000);
            swatch(ctx, b.id(), tx, tyy, tw, th, hover ? t : 0, 48);
            if (!owned) {
                ctx.fill(tx, tyy, tx + tw, tyy + th, 0x99000000);
                lock(ctx, tx + tw / 2, tyy + th / 2);
            }
            Ui.outline(ctx, tx - 1, tyy - 1, tw + 2, th + 2, on ? Ui.GOLD : hover ? 0xCCFFFFFF : 0x44FFFFFF);
            if (on) {
                Ui.round(ctx, tx + tw - 11, tyy + 2, 9, 9, 0xFF2E9C5A);
                ctx.drawString(tr(), "✔", tx + tw - 10, tyy + 3, 0xFFFFFFFF, false);
            }
            small(ctx, Component.translatable("tcgshop.board." + b.id()), cx + cell / 2f, cy + cellH - 12, cell - 6, owned ? 0xFFFFFFFF : 0xFF8A8FA8);
            final boolean o2 = owned;
            clip(areas, cx + 2, cy + 2, cell - 4, cellH - 4, top, viewH, () -> {
                click(o2);
                if (o2) equip("board", b.id());
            });
        }
        ctx.disableScissor();
        scrollbar(ctx, gx + gw - 3, top, viewH, rows * cellH, scroll);
        hoverKey = newHover;
        return scroll;
    }

    /** Thumbnail of a background (the classic one is its usual gradient). */
    private static void swatch(GuiGraphics ctx, String id, int x, int y, int w, int h, double t, int tile) {
        ctx.fillGradient(x, y, x + w, y + h, 0xFFE9F2FB, 0xFFD3E4F4);
        ctx.enableScissor(x, y, x + w, y + h);
        if (!DuelBackgrounds.draw(ctx, id, x, y, w, h, tile, t, 1f) && !id.equals("classic")) {
            Cosmetics.Board b = Cosmetics.board(id);
            int a = b == null ? 0xFF555555 : b.accent() | 0xFF000000;
            ctx.fillGradient(x, y, x + w, y + h, a, Ui.darken(a, 50)); // while it is being generated
        }
        ctx.disableScissor();
    }

    // ------------------------------------------------------------------ achievements

    private record Goal(String key, Cosmetics.Rule rule, int arg, int accent, boolean coin, int index) {}

    public double goals(GuiGraphics ctx, JsonObject L, int px, int py, int pw, int ph, int mx, int my, double t, double scroll) {
        JsonObject c = cos(L);
        Set<String> un = unlocked(c);
        List<Goal> gs = new ArrayList<>();
        for (int i = 0; i < Cosmetics.COINS.size(); i++) {
            Cosmetics.Coin k = Cosmetics.COINS.get(i);
            if (k.rule() != Cosmetics.Rule.FREE) gs.add(new Goal("coin:" + k.id(), k.rule(), k.arg(), k.face(), true, i));
        }
        for (int i = 0; i < Cosmetics.BOARDS.size(); i++) {
            Cosmetics.Board b = Cosmetics.BOARDS.get(i);
            if (b.rule() != Cosmetics.Rule.FREE) gs.add(new Goal("board:" + b.id(), b.rule(), b.arg(), b.accent(), false, i));
        }
        // first what you are missing (closest first, at the top); completed ones at the end
        gs.sort((a, b) -> {
            boolean da = un.contains(a.key), db = un.contains(b.key);
            if (da != db) return da ? 1 : -1;
            return Float.compare(frac(c, b.rule, b.arg), frac(c, a.rule, a.arg));
        });
        int done = 0;
        for (Goal g : gs) if (un.contains(g.key)) done++;
        ctx.drawString(tr(), Component.translatable("tcgshop.cos.goals_title", done, gs.size()).withStyle(ChatFormatting.BOLD), px + 2, py + 2, 0xFFFFFFFF, false);
        String st = Component.translatable("tcgshop.cos.streak", gi(c, "streak"), prog(c, Cosmetics.Rule.STREAK)).getString();
        ctx.drawString(tr(), st, px + pw - 4 - tr().width(st), py + 2, 0xFFFFB347, false);
        int rowH = 26, top = py + 14, viewH = ph - 14;
        scroll = Mth.clamp(scroll, 0, Math.max(0, gs.size() * rowH - viewH));
        PoseStack m = ctx.pose();
        ctx.enableScissor(px, top, px + pw, top + viewH);
        for (int i = 0; i < gs.size(); i++) {
            Goal g = gs.get(i);
            int y = top + i * rowH - (int) scroll;
            if (y + rowH < top || y > top + viewH) continue;
            boolean ok = un.contains(g.key);
            Ui.roundGradient(ctx, px, y, pw - 6, rowH - 3, ok ? 0x3326A65A : SURF2, ok ? 0x2226A65A : SURF);
            Ui.outline(ctx, px, y, pw - 6, rowH - 3, ok ? 0x6643C463 : LINE);
            if (g.coin) {
                m.pushPose();
                m.translate(px + 13, y + 11.5f, 0);
                Coin3D.draw(ctx, 8, (float) Math.sin(t + i) * 20, 10, g.index, ok ? 1f : 0.5f);
                m.popPose();
            } else swatch(ctx, Cosmetics.BOARDS.get(g.index).id(), px + 4, y + 3, 18, 17, 0, 24);
            String name = Component.translatable(g.coin ? "tcgshop.cos.kind_coin" : "tcgshop.cos.kind_board",
                    Component.translatable((g.coin ? "tcgshop.coin." : "tcgshop.board.") + g.key.substring(g.key.indexOf(':') + 1))).getString();
            ctx.drawString(tr(), Ui.ellipsis(name, pw - 120), px + 28, y + 3, ok ? 0xFF8FF0A8 : 0xFFFFFFFF, false);
            ctx.drawString(tr(), Ui.ellipsis(ruleText(g.rule, g.arg).getString(), pw - 120), px + 28, y + 13, 0xFF9AA0B8, false);
            int bx = px + pw - 92;
            if (ok) Ui.text(ctx, Component.literal("✔ ").append(Component.translatable("tcgshop.cos.done")), bx, y + 6, 80, 10, 0xFF6BE07B, false);
            else if (g.rule == Cosmetics.Rule.LOOT) Ui.text(ctx, Component.translatable("tcgshop.cos.loot"), bx, y + 6, 80, 10, 0xFFC890FF, false);
            else {
                float f = frac(c, g.rule, g.arg);
                Ui.progress(ctx, bx, y + 6, 80, 5, f, g.accent | 0xFF000000);
                String pr = g.rule == Cosmetics.Rule.RANK ? Math.round(f * 100) + "%" : Math.min(prog(c, g.rule), g.arg) + "/" + g.arg;
                Ui.text(ctx, Component.literal(pr), bx, y + 12, 80, 10, 0xFFCCCCCC, false);
            }
        }
        ctx.disableScissor();
        scrollbar(ctx, px + pw - 3, top, viewH, gs.size() * rowH, scroll);
        return scroll;
    }

    // ------------------------------------------------------------------ pieces

    /** Adds the click area only for the part visible inside the scrolling list. */
    private static void clip(AreaSink areas, int x, int y, int w, int h, int top, int viewH, Runnable r) {
        int y0 = Math.max(y, top), y1 = Math.min(y + h, top + viewH);
        if (y1 > y0) areas.add(x, y0, w, y1 - y0, r);
    }

    private static int gi(JsonObject o, String k) { return o.has(k) ? o.get(k).getAsInt() : 0; }

    /** Small padlock centered on (cx, cy). */
    static void lock(GuiGraphics ctx, int cx, int cy) {
        Ui.round(ctx, cx - 6, cy - 2, 12, 9, 0xFF3A3F55);
        Ui.outline(ctx, cx - 6, cy - 2, 12, 9, 0xFFCFD5E6);
        ctx.fill(cx - 4, cy - 7, cx - 2, cy - 2, 0xFFCFD5E6);
        ctx.fill(cx + 2, cy - 7, cx + 4, cy - 2, 0xFFCFD5E6);
        ctx.fill(cx - 3, cy - 8, cx + 3, cy - 6, 0xFFCFD5E6);
        ctx.fill(cx - 1, cy + 1, cx + 1, cy + 4, 0xFFFFD34D);
    }

    private static void small(GuiGraphics ctx, Component t, float cx, float y, int maxW, int color) {
        int w = tr().width(t);
        float s = Math.min(0.7f, maxW / (float) Math.max(1, w));
        PoseStack m = ctx.pose();
        m.pushPose();
        m.translate(cx, y, 0);
        m.scale(s, s, 1);
        ctx.drawString(tr(), t, -w / 2, 0, color, true);
        m.popPose();
    }

    private static int wrapC(GuiGraphics ctx, Component t, int x, int y, int w, int color) {
        for (var line : tr().split(t, w)) {
            ctx.drawString(tr(), line, x + (w - tr().width(line)) / 2, y, color, false);
            y += 10;
        }
        return y;
    }

    private static void scrollbar(GuiGraphics ctx, int x, int y, int h, int content, double scroll) {
        if (content <= h) return;
        int sh = Math.max(12, h * h / content), sy = y + (int) Math.round(scroll / Math.max(1, content - h) * (h - sh));
        Ui.round(ctx, x, sy, 3, sh, 0x88FFFFFF);
    }
}

package dev.tcgshop.client.binder;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import static dev.tcgshop.client.screen.Draw.withAlpha;

/** Hand-drawn UI pieces (panels, chips, buttons, badges) for the mod's screens. */
public final class Ui {
    public static final int GOLD = 0xFFFFD34D;
    public static final int TEXT = 0xFFF2EEE6;
    public static final int MUTED = 0xFF9A9AAE;

    private Ui() {}

    public static TextRenderer font() {
        return MinecraftClient.getInstance().textRenderer;
    }

    public static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

    /** Rectangle with clipped corners (rounded pixel-art look). */
    public static void round(DrawContext ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x + 1, y, x + w - 1, y + h, color);
        ctx.fill(x, y + 1, x + 1, y + h - 1, color);
        ctx.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    public static void roundGradient(DrawContext ctx, int x, int y, int w, int h, int top, int bottom) {
        ctx.fillGradient(x + 1, y, x + w - 1, y + h, top, bottom);
        ctx.fillGradient(x, y + 1, x + 1, y + h - 1, top, bottom);
        ctx.fillGradient(x + w - 1, y + 1, x + w, y + h - 1, top, bottom);
    }

    public static void outline(DrawContext ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x + 1, y, x + w - 1, y + 1, color);
        ctx.fill(x + 1, y + h - 1, x + w - 1, y + h, color);
        ctx.fill(x, y + 1, x + 1, y + h - 1, color);
        ctx.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    /** Stitched leather cover: the frame of the binders. */
    public static void leather(DrawContext ctx, int x, int y, int w, int h, int top, int bottom) {
        ctx.fill(x + 4, y + 6, x + w + 4, y + h + 6, 0x66000000);
        roundGradient(ctx, x, y, w, h, top, bottom);
        outline(ctx, x, y, w, h, 0xFF000000);
        for (int i = x + 6; i < x + w - 6; i += 6) {
            ctx.fill(i, y + 3, i + 3, y + 4, 0x55FFFFFF);
            ctx.fill(i, y + h - 4, i + 3, y + h - 3, 0x55FFFFFF);
        }
        for (int j = y + 6; j < y + h - 6; j += 6) {
            ctx.fill(x + 3, j, x + 4, j + 3, 0x55FFFFFF);
            ctx.fill(x + w - 4, j, x + w - 3, j + 3, 0x55FFFFFF);
        }
    }

    // ------------------------------------------------------------------ truly rounded corners

    public static int lerpC(int a, int b, float t) {
        t = Math.max(0, Math.min(1, t));
        int aa = (a >>> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return (Math.round(aa + (ba - aa) * t) << 24) | (Math.round(ar + (br - ar) * t) << 16) | (Math.round(ag + (bg - ag) * t) << 8) | Math.round(ab + (bb - ab) * t);
    }

    private static int inset(int r, int i) {
        float dy = r - i - 0.5f;
        return Math.round(r - (float) Math.sqrt(Math.max(0, r * r - dy * dy)));
    }

    public static void rounded(DrawContext ctx, int x, int y, int w, int h, int r, int color) {
        roundedGradient(ctx, x, y, w, h, r, color, color);
    }

    /** Rectangle with corners of radius r and a vertical gradient. */
    public static void roundedGradient(DrawContext ctx, int x, int y, int w, int h, int r, int top, int bottom) {
        if (w <= 0 || h <= 0) return;
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        if (r <= 1) { roundGradient(ctx, x, y, w, h, top, bottom); return; }
        if (h - 2 * r > 0) ctx.fillGradient(x, y + r, x + w, y + h - r, lerpC(top, bottom, r / (float) h), lerpC(top, bottom, (h - r) / (float) h));
        for (int i = 0; i < r; i++) {
            int in = inset(r, i);
            ctx.fill(x + in, y + i, x + w - in, y + i + 1, lerpC(top, bottom, i / (float) h));
            ctx.fill(x + in, y + h - 1 - i, x + w - in, y + h - i, lerpC(top, bottom, (h - 1 - i) / (float) h));
        }
    }

    /** 1 px border with corners of radius r. */
    public static void roundedOutline(DrawContext ctx, int x, int y, int w, int h, int r, int color) {
        if (w <= 1 || h <= 1) return;
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        if (r <= 1) { outline(ctx, x, y, w, h, color); return; }
        ctx.fill(x + inset(r, 0), y, x + w - inset(r, 0), y + 1, color);
        ctx.fill(x + inset(r, 0), y + h - 1, x + w - inset(r, 0), y + h, color);
        ctx.fill(x, y + r, x + 1, y + h - r, color);
        ctx.fill(x + w - 1, y + r, x + w, y + h - r, color);
        for (int i = 1; i < r; i++) {
            int in = inset(r, i), prev = inset(r, i - 1), span = Math.max(1, prev - in);
            ctx.fill(x + in, y + i, x + in + span, y + i + 1, color);
            ctx.fill(x + w - in - span, y + i, x + w - in, y + i + 1, color);
            ctx.fill(x + in, y + h - 1 - i, x + in + span, y + h - i, color);
            ctx.fill(x + w - in - span, y + h - 1 - i, x + w - in, y + h - i, color);
        }
    }

    /** Covers the corners outside the radius with a color (to clip already-drawn content to a rounded shape). */
    public static void cornerMask(DrawContext ctx, int x, int y, int w, int h, int r, int color) {
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        for (int i = 0; i < r; i++) {
            int in = inset(r, i);
            if (in <= 0) continue;
            ctx.fill(x, y + i, x + in, y + i + 1, color);
            ctx.fill(x + w - in, y + i, x + w, y + i + 1, color);
            ctx.fill(x, y + h - 1 - i, x + in, y + h - i, color);
            ctx.fill(x + w - in, y + h - 1 - i, x + w, y + h - i, color);
        }
    }

    /** Glass panel: translucent white gradient, highlight on top and a soft border. */
    public static void glass(DrawContext ctx, int x, int y, int w, int h, int r) {
        roundedGradient(ctx, x, y, w, h, r, 0x3CFFFFFF, 0x14FFFFFF);
        ctx.fill(x + r, y + 1, x + w - r, y + 2, 0x30FFFFFF);
        roundedOutline(ctx, x, y, w, h, r, 0x2EFFFFFF);
    }

    /** Selectable tab / chip with an optional notification badge. */
    public static void chip(DrawContext ctx, int x, int y, int w, int h, Text label, boolean selected, boolean hover, int accent, int badge) {
        int r = Math.min(h / 2, 6);
        if (selected) {
            rounded(ctx, x - 1, y - 1, w + 2, h + 2, r + 1, withAlpha(accent, 0.25f));
            roundedGradient(ctx, x, y, w, h, r, brighten(accent, 25), darken(accent, 20));
            roundedGradient(ctx, x + 1, y + 1, w - 2, h / 2, r - 1, 0x48FFFFFF, 0x10FFFFFF);
        } else roundedGradient(ctx, x, y, w, h, r, hover ? 0xFF41465E : 0xFF2A2E40, hover ? 0xFF2C3044 : 0xFF1C1F2C);
        roundedOutline(ctx, x, y, w, h, r, selected ? withAlpha(0xFFFFFFFF, 0.85f) : withAlpha(accent, hover ? 0.9f : 0.55f));
        text(ctx, label, x, y, w, h, selected ? 0xFF15151C : TEXT, !selected);
        if (badge > 0) badge(ctx, x + w - 4, y - 3, badge);
    }
    /** Text centered in a box; if it does not fit, it is scaled down so it is never cut off. */
    public static void text(DrawContext ctx, Text label, int x, int y, int w, int h, int color, boolean shadow) {
        int tw = font().getWidth(label);
        float s = Math.min(1f, (w - 4f) / Math.max(1, tw));
        var m = ctx.getMatrices();
        m.push();
        m.translate(x + w / 2f, y + h / 2f, 0);
        m.scale(s, s, 1);
        ctx.drawText(font(), label, -tw / 2, -4, color, shadow);
        m.pop();
    }

    /** Natural width of a chip so its text fits. */
    public static int chipWidth(Text label) {
        return font().getWidth(label) + 10;
    }

    /** Red circle with a number (notifications). */
    public static void badge(DrawContext ctx, int cx, int cy, int n) {
        String s = n > 99 ? "99+" : String.valueOf(n);
        int w = Math.max(9, font().getWidth(s) + 4);
        round(ctx, cx - w / 2, cy - 1, w, 10, 0xFFE53935);
        outline(ctx, cx - w / 2, cy - 1, w, 10, 0xFFFFFFFF);
        ctx.drawText(font(), s, cx - font().getWidth(s) / 2, cy + 1, 0xFFFFFFFF, false);
    }

    /**
     * Button with depth: shadow, gradient, glassy highlight on top, dark border, a glow on hover, and it sinks
     * one pixel while pressed. Returns whether the mouse is over it.
     */
    public static boolean button(DrawContext ctx, int x, int y, int w, int h, Text label, int mx, int my, boolean enabled, int accent) {
        boolean hover = enabled && in(mx, my, x, y, w, h);
        boolean press = hover && MinecraftClient.getInstance().mouse.wasLeftButtonClicked();
        int r = Math.min(h / 2, 6), dy = press ? 1 : 0;
        if (!press) rounded(ctx, x + 1, y + 2, w, h, r, 0x5A000000);
        if (hover) rounded(ctx, x - 2, y - 2 + dy, w + 4, h + 4, r + 2, withAlpha(brighten(accent, 60), 0.28f));
        int top = !enabled ? 0xFF3A3B44 : hover ? brighten(accent, 38) : brighten(accent, 14);
        int bottom = !enabled ? 0xFF26272D : darken(accent, hover ? 25 : 40);
        roundedGradient(ctx, x, y + dy, w, h, r, top, bottom);
        if (enabled) {
            roundedGradient(ctx, x + 1, y + dy + 1, w - 2, Math.max(2, h / 2 - 1), Math.max(0, r - 1), 0x50FFFFFF, 0x0AFFFFFF);
            ctx.fill(x + r, y + dy + h - 2, x + w - r, y + dy + h - 1, 0x30000000);
        }
        roundedOutline(ctx, x, y + dy, w, h, r, !enabled ? 0xFF16161A : hover ? withAlpha(0xFFFFFFFF, 0.8f) : withAlpha(darken(accent, 75), 0.95f));
        text(ctx, label, x, y + dy, w, h, enabled ? 0xFFFFFFFF : 0xFF77777F, enabled);
        return hover;
    }

    public static void progress(DrawContext ctx, int x, int y, int w, int h, float f, int color) {
        int r = Math.max(1, h / 2);
        roundedGradient(ctx, x, y, w, h, r, 0xFF0B0D13, 0xFF1A1D27);
        int fw = Math.round((w - 2) * Math.max(0, Math.min(1, f)));
        if (fw > 0) {
            roundedGradient(ctx, x + 1, y + 1, Math.max(fw, Math.min(w - 2, 2 * r)), h - 2, Math.max(0, r - 1), brighten(color, 45), color);
            if (h >= 4) ctx.fill(x + 2, y + 1, x + Math.max(2, fw - 1), y + 2, 0x55FFFFFF);
        }
        roundedOutline(ctx, x, y, w, h, r, 0xAA000000);
    }
    public static String ellipsis(String s, int maxWidth) {
        if (font().getWidth(s) <= maxWidth) return s;
        while (s.length() > 1 && font().getWidth(s + "…") > maxWidth) s = s.substring(0, s.length() - 1);
        return s + "…";
    }

    public static int brighten(int argb, int amt) {
        int r = Math.min(255, ((argb >> 16) & 0xFF) + amt), g = Math.min(255, ((argb >> 8) & 0xFF) + amt), b = Math.min(255, (argb & 0xFF) + amt);
        return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    public static int darken(int argb, int amt) {
        int r = Math.max(0, ((argb >> 16) & 0xFF) - amt), g = Math.max(0, ((argb >> 8) & 0xFF) - amt), b = Math.max(0, (argb & 0xFF) - amt);
        return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
    }
}

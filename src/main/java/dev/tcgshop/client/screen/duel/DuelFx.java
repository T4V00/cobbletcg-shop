package dev.tcgshop.client.screen.duel;

import dev.tcgshop.client.binder.Ui;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;

/**
 * Duel animation and drawing helpers: easing curves, FPS-independent exponential approach,
 * HP bars with a "ghost", outlined text and gradient panels. Everything is time-based (seconds), never
 * frame-based.
 */
public final class DuelFx {
    private DuelFx() {}

    // ------------------------------------------------------------------ curves

    public static float clamp01(double x) { return (float) Math.max(0, Math.min(1, x)); }

    /** 0..1 progress of an animation that started at {@code start} and lasts {@code dur} seconds. */
    public static float prog(double now, double start, double dur) { return clamp01((now - start) / dur); }

    public static float outCubic(double x) { x = clamp01(x); return (float) (1 - Math.pow(1 - x, 3)); }

    public static float inCubic(double x) { x = clamp01(x); return (float) (x * x * x); }

    public static float inOutCubic(double x) {
        x = clamp01(x);
        return (float) (x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2);
    }

    public static float outQuint(double x) { x = clamp01(x); return (float) (1 - Math.pow(1 - x, 5)); }

    /** Overshoots slightly and comes back (bouncy entrance). */
    public static float outBack(double x) {
        x = clamp01(x);
        double c1 = 1.70158, c3 = c1 + 1;
        return (float) (1 + c3 * Math.pow(x - 1, 3) + c1 * Math.pow(x - 1, 2));
    }

    /** Elastic spring: for very lively "pops" (damage numbers). */
    public static float outElastic(double x) {
        x = clamp01(x);
        if (x == 0 || x == 1) return (float) x;
        return (float) (Math.pow(2, -10 * x) * Math.sin((x * 10 - 0.75) * (2 * Math.PI / 3)) + 1);
    }

    /** Fades in over {@code fadeIn} s, holds, and fades out over the last {@code fadeOut} s of {@code life}. */
    public static float envelope(double age, double life, double fadeIn, double fadeOut) {
        if (age < 0 || age > life) return 0;
        float a = fadeIn <= 0 ? 1 : clamp01(age / fadeIn);
        float b = fadeOut <= 0 ? 1 : clamp01((life - age) / fadeOut);
        return Math.min(a, b);
    }

    /** FPS-independent exponential approach: {@code rate} = "speed" (1/s). */
    public static float approach(float cur, float target, float rate, float dt) {
        return target + (cur - target) * (float) Math.exp(-rate * dt);
    }

    // ------------------------------------------------------------------ animated HP bar

    /**
     * Displayed HP of a Pokémon: the bar drops smoothly and leaves behind a light "ghost" that lingers for a moment
     * and then catches up; when healing, a green strip moves ahead and the bar rises behind it.
     */
    public static final class HpBar {
        public float shown = -1, ghost = -1;
        private double holdUntil;
        private float lastTarget = -1;

        public void reset() { shown = ghost = lastTarget = -1; }

        public void update(float target, double now, float dt) {
            target = clamp01(target);
            if (shown < 0) { shown = ghost = lastTarget = target; return; }
            if (target != lastTarget) {
                if (target < lastTarget) { ghost = Math.max(ghost, shown); holdUntil = now + 0.45; }
                else { ghost = target; holdUntil = now + 0.25; }
                lastTarget = target;
            }
            if (target < shown) { // damage: the bar drops fast and the ghost waits
                shown = approach(shown, target, 11f, dt);
                if (now > holdUntil) ghost = approach(ghost, shown, 5f, dt);
            } else { // healing: the green goes ahead and the bar rises behind
                if (now > holdUntil) shown = approach(shown, target, 6f, dt);
                ghost = Math.max(target, shown);
            }
            if (Math.abs(shown - target) < 0.002f) shown = target;
            if (Math.abs(ghost - shown) < 0.002f) ghost = shown;
        }

        public boolean healing() { return ghost > shown + 0.002f && lastTarget > shown; }
    }

    // ------------------------------------------------------------------ drawing

    /** Centered, scaled text with a 1 px dark outline (readable on any background). */
    public static void outlinedC(DrawContext ctx, TextRenderer tr, String s, float x, float y, int color, int outline, float scale) {
        MatrixStack m = ctx.getMatrices();
        m.push();
        m.translate(x - tr.getWidth(s) * scale / 2f, y, 0);
        m.scale(scale, scale, 1);
        if ((outline >>> 24) > 8) {
            ctx.drawText(tr, s, -1, 0, outline, false);
            ctx.drawText(tr, s, 1, 0, outline, false);
            ctx.drawText(tr, s, 0, -1, outline, false);
            ctx.drawText(tr, s, 0, 1, outline, false);
            ctx.drawText(tr, s, 1, 1, outline, false);
        }
        ctx.drawText(tr, s, 0, 0, color, false);
        m.pop();
    }

    /** Panel with a vertical gradient, top shine and border (instead of flat gray). */
    public static void gradientPanel(DrawContext ctx, int x, int y, int w, int h, int top, int bottom, int line) {
        Ui.roundedGradient(ctx, x, y, w, h, 4, top, bottom);
        if (h > 10) Ui.roundedGradient(ctx, x + 1, y + 1, w - 2, Math.min(10, h / 3), 3, DuelPalette.alpha(DuelPalette.PANEL_SHINE, (top >>> 24) / 255f), 0x00FFFFFF);
        Ui.roundedOutline(ctx, x, y, w, h, 4, line);
    }

    /** Horizontal strip with a left-to-right gradient (in bands; cheap). */
    public static void hGradient(DrawContext ctx, int x0, int y0, int x1, int y1, int left, int right) {
        int w = x1 - x0;
        if (w <= 0 || y1 <= y0) return;
        int bands = Math.max(1, Math.min(24, w / 6));
        for (int k = 0; k < bands; k++) {
            int a = x0 + w * k / bands, b = x0 + w * (k + 1) / bands;
            ctx.fill(a, y0, b, y1, DuelPalette.lerp(left, right, (k + 0.5f) / bands));
        }
    }
}

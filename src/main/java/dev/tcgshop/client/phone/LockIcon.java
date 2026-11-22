package dev.tcgshop.client.phone;

import net.minecraft.client.gui.DrawContext;

/** Candado dibujado con rectángulos (no depende de que la fuente tenga el emoji). s = alto total en píxeles (>= 8). */
public final class LockIcon {
    private LockIcon() {}

    public static void draw(DrawContext ctx, int x, int y, int s, int color) {
        s = Math.max(8, s);
        int w = Math.round(s * 0.8f), bodyH = Math.round(s * 0.55f), bodyY = y + s - bodyH;
        int sh = Math.max(1, s / 8), arcW = w - sh * 2 - Math.max(2, s / 5);
        int ax = x + (w - arcW) / 2;
        // sombra
        ctx.fill(x + 1, bodyY + 1, x + w + 1, y + s + 1, 0x66000000);
        // arco
        ctx.fill(ax, y, ax + arcW, y + sh, color);
        ctx.fill(ax, y, ax + sh, bodyY, color);
        ctx.fill(ax + arcW - sh, y, ax + arcW, bodyY, color);
        // cuerpo
        ctx.fill(x, bodyY, x + w, y + s, color);
        ctx.fill(x + 1, bodyY + 1, x + w - 1, bodyY + 2, 0x55FFFFFF);
        // ojo de la cerradura
        int kx = x + w / 2;
        ctx.fill(kx - 1, bodyY + bodyH / 3, kx + 1, bodyY + bodyH / 3 + Math.max(2, bodyH / 3), 0xCC000000);
    }

    /** Candado sobre un círculo oscuro (insignia), centrado en (cx, cy). */
    public static void badge(DrawContext ctx, int cx, int cy, int s, int color) {
        int r = s / 2 + 3;
        dev.tcgshop.client.binder.Ui.rounded(ctx, cx - r, cy - r, r * 2, r * 2, r, 0xDD101218);
        dev.tcgshop.client.binder.Ui.roundedOutline(ctx, cx - r, cy - r, r * 2, r * 2, r, color);
        draw(ctx, cx - Math.round(s * 0.4f), cy - s / 2, s, color);
    }
}

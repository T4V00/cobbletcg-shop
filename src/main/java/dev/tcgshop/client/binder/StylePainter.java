package dev.tcgshop.client.binder;

import dev.tcgshop.binder.BinderStyles;
import net.minecraft.client.gui.DrawContext;

import static dev.tcgshop.client.screen.Draw.withAlpha;

/**
 * Draws the Personal Binder styles in code: background scenes inspired by Cobblemon biomes,
 * patterns inspired by Pokémon types, and palettes (colors and gradients). Pixel-art style in line with Minecraft.
 */
public final class StylePainter {
    private StylePainter() {}

    private static double rnd(long seed) {
        long h = seed * 0x9E3779B97F4A7C15L;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return ((h >>> 11) & ((1L << 53) - 1)) * 0x1.0p-53;
    }

    public static void circle(DrawContext ctx, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int dx = (int) Math.sqrt(r * r - dy * dy);
            ctx.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
        }
    }

    private static void ring(DrawContext ctx, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int dx = (int) Math.sqrt(r * r - dy * dy);
            ctx.fill(cx - dx, cy + dy, cx - dx + 1, cy + dy + 1, color);
            ctx.fill(cx + dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
        }
        ctx.fill(cx - r, cy, cx + r + 1, cy + 1, color);
    }

    /** Terrain: columns whose height follows a sum of sines. */
    private static void hills(DrawContext ctx, int x, int y, int w, int h, double base, double amp, double freq, double phase, int top, int bottom) {
        for (int i = 0; i < w; i += 2) {
            double v = base + amp * (Math.sin(i * freq + phase) * 0.6 + Math.sin(i * freq * 2.3 + phase * 1.7) * 0.4);
            int hy = y + (int) (h * v);
            ctx.fillGradient(x + i, hy, x + i + 2, y + h, top, bottom);
        }
    }

    // ------------------------------------------------------------------ backgrounds

    public static void background(DrawContext ctx, String id, int x, int y, int w, int h, double t) {
        switch (id) {
            case "meadow" -> {
                ctx.fillGradient(x, y, x + w, y + h, 0xFF7EC8F2, 0xFFD8F1FF);
                circle(ctx, x + w * 4 / 5, y + h / 5, Math.max(8, h / 12), 0xFFFFF2A8);
                hills(ctx, x, y, w, h, 0.62, 0.05, 0.012, 0.5, 0xFF8BC96B, 0xFF5E9E45);
                hills(ctx, x, y, w, h, 0.74, 0.04, 0.02, 2.0, 0xFF6FB552, 0xFF3F7E32);
                for (int i = 0; i < 60; i++) {
                    int fx = x + (int) (rnd(i) * w), fy = y + (int) (h * (0.8 + rnd(i + 99) * 0.18));
                    int col = new int[]{0xFFFF6B6B, 0xFFFFD93D, 0xFFFFFFFF, 0xFFB983FF}[i % 4];
                    ctx.fill(fx, fy, fx + 2, fy + 2, col);
                }
            }
            case "beach" -> {
                ctx.fillGradient(x, y, x + w, y + h / 2, 0xFF4FB3E8, 0xFFBFE9FF);
                ctx.fillGradient(x, y + h / 2, x + w, y + h * 3 / 4, 0xFF1F8AC0, 0xFF0F5E8C);
                for (int r = 0; r < 6; r++) {
                    int wy = y + h / 2 + 6 + r * (h / 28);
                    for (int i = 0; i < w; i += 4) {
                        int off = (int) (Math.sin(i * 0.05 + t * 1.5 + r) * 2);
                        ctx.fill(x + i, wy + off, x + i + 3, wy + off + 1, 0x66FFFFFF);
                    }
                }
                ctx.fillGradient(x, y + h * 3 / 4, x + w, y + h, 0xFFF2D58A, 0xFFD9B565);
                circle(ctx, x + w / 6, y + h / 6, Math.max(8, h / 13), 0xFFFFF7C2);
            }
            case "night_sky" -> {
                ctx.fillGradient(x, y, x + w, y + h, 0xFF0B1030, 0xFF1F2A5C);
                for (int i = 0; i < 140; i++) {
                    int sx = x + (int) (rnd(i) * w), sy = y + (int) (rnd(i + 500) * h * 0.85);
                    float tw = 0.4f + 0.6f * (float) Math.abs(Math.sin(t * (0.5 + rnd(i + 900) * 2) + i));
                    ctx.fill(sx, sy, sx + 1 + (i % 7 == 0 ? 1 : 0), sy + 1 + (i % 7 == 0 ? 1 : 0), withAlpha(0xFFFFFFFF, tw));
                }
                circle(ctx, x + w * 3 / 4, y + h / 5, Math.max(9, h / 11), 0xFFF4F1DE);
                circle(ctx, x + w * 3 / 4 + h / 30, y + h / 5 - h / 60, Math.max(7, h / 13), 0xFF141B40);
                hills(ctx, x, y, w, h, 0.82, 0.04, 0.015, 1.0, 0xFF0A0E22, 0xFF05070F);
            }
            case "crystal_cave" -> {
                ctx.fillGradient(x, y, x + w, y + h, 0xFF1A0F2E, 0xFF090512);
                for (int i = 0; i < 22; i++) {
                    int cx = x + (int) (rnd(i + 7) * w);
                    boolean top = i % 2 == 0;
                    int len = (int) (h * (0.12 + rnd(i + 70) * 0.18));
                    int col = new int[]{0xFF9B5DE5, 0xFF00BBF9, 0xFFF15BB5, 0xFF00F5D4}[i % 4];
                    float glow = 0.6f + 0.4f * (float) Math.sin(t * 2 + i);
                    for (int k = 0; k < len; k++) {
                        int half = Math.max(1, (len - k) / 4);
                        int yy = top ? y + k : y + h - k;
                        ctx.fill(cx - half, yy, cx + half, yy + 1, withAlpha(col, glow * (0.5f + 0.5f * k / len)));
                    }
                }
            }
            case "snowy_peaks" -> {
                ctx.fillGradient(x, y, x + w, y + h, 0xFF9CC7E8, 0xFFE6F2FA);
                for (int m = 0; m < 4; m++) {
                    int peakX = x + (int) (w * (0.1 + m * 0.27)), peakH = (int) (h * (0.45 + rnd(m) * 0.2));
                    for (int i = -h; i < h; i += 2) {
                        int colTop = y + h - peakH + Math.abs(i) * 3 / 4;
                        if (colTop >= y + h) continue;
                        ctx.fill(peakX + i, colTop, peakX + i + 2, y + h, 0xFF6C7FA0);
                        ctx.fill(peakX + i, colTop, peakX + i + 2, colTop + Math.max(0, peakH / 5 - Math.abs(i) / 4), 0xFFFFFFFF);
                    }
                }
                for (int i = 0; i < 80; i++) {
                    double fall = (t * (12 + rnd(i) * 10) + rnd(i + 3) * h) % h;
                    int sx = x + (int) ((rnd(i + 40) * w + Math.sin(t + i) * 6) % w);
                    ctx.fill(sx, y + (int) fall, sx + 2, y + (int) fall + 2, 0xCCFFFFFF);
                }
            }
            case "berry_grove" -> {
                ctx.fillGradient(x, y, x + w, y + h, 0xFF9EDB7A, 0xFF3F7E32);
                for (int i = 0; i < 9; i++) {
                    int tx = x + (int) (w * (i + 0.5) / 9), ty = y + (int) (h * (0.35 + rnd(i) * 0.15));
                    int r = Math.max(10, h / 9);
                    ctx.fill(tx - 2, ty, tx + 2, y + h, 0xFF6B4423);
                    circle(ctx, tx, ty, r, 0xFF2F7D3A);
                    circle(ctx, tx - r / 3, ty - r / 3, r * 2 / 3, 0xFF3E9A49);
                    for (int b = 0; b < 6; b++) {
                        int bx = tx + (int) ((rnd(i * 10 + b) - 0.5) * r * 1.4), by = ty + (int) ((rnd(i * 10 + b + 5) - 0.5) * r * 1.4);
                        circle(ctx, bx, by, 2, b % 2 == 0 ? 0xFFE63946 : 0xFF7B2CBF);
                    }
                }
            }
            case "volcano" -> {
                ctx.fillGradient(x, y, x + w, y + h, 0xFF3D0C02, 0xFFB33F00);
                for (int i = -w / 2; i < w / 2; i += 2) {
                    int top = y + (int) (h * 0.35) + Math.abs(i) * h / w;
                    if (Math.abs(i) < w / 14) top = y + (int) (h * 0.38);
                    ctx.fill(x + w / 2 + i, top, x + w / 2 + i + 2, y + h, 0xFF1B0B06);
                }
                ctx.fillGradient(x + w / 2 - w / 14, y + (int) (h * 0.36), x + w / 2 + w / 14, y + (int) (h * 0.42), 0xFFFFD166, 0xFFFF6B00);
                for (int i = 0; i < 50; i++) {
                    double rise = (t * (20 + rnd(i) * 30) + rnd(i + 8) * h) % h;
                    int ex = x + w / 2 + (int) ((rnd(i + 20) - 0.5) * w * 0.4 + Math.sin(t * 2 + i) * 8);
                    ctx.fill(ex, y + h - (int) rise, ex + 2, y + h - (int) rise + 2, withAlpha(0xFFFFB703, (float) (1 - rise / h)));
                }
            }
            case "aurora_tundra" -> {
                ctx.fillGradient(x, y, x + w, y + h, 0xFF050A1F, 0xFF12224A);
                int[] cols = {0xFF43E97B, 0xFF38F9D7, 0xFFB983FF};
                for (int b = 0; b < 3; b++) {
                    for (int i = 0; i < w; i += 2) {
                        double v = Math.sin(i * 0.02 + t * 0.6 + b * 1.7) * 0.06 + Math.sin(i * 0.007 + b) * 0.05;
                        int ay = y + (int) (h * (0.2 + b * 0.08 + v));
                        ctx.fillGradient(x + i, ay, x + i + 2, ay + h / 5, withAlpha(cols[b], 0.45f), 0x00000000);
                    }
                }
                hills(ctx, x, y, w, h, 0.8, 0.03, 0.01, 0.2, 0xFFE9F1FA, 0xFFB8C9DB);
            }
            case "ancient_ruins" -> {
                ctx.fillGradient(x, y, x + w, y + h, 0xFFF4A261, 0xFF6D3B1F);
                circle(ctx, x + w / 2, y + h / 2, Math.max(14, h / 6), 0x55FFE8A3);
                for (int i = 0; i < 7; i++) {
                    int cx = x + (int) (w * (i + 0.5) / 7), ch = (int) (h * (0.25 + rnd(i) * 0.35)), cw = Math.max(6, w / 28);
                    ctx.fill(cx - cw / 2, y + h - ch, cx + cw / 2, y + h, 0xFF4A2C17);
                    ctx.fill(cx - cw / 2 - 2, y + h - ch - 3, cx + cw / 2 + 2, y + h - ch, 0xFF3A2212);
                }
                ctx.fill(x, y + h - h / 10, x + w, y + h, 0xFF3A2212);
            }
            default -> ctx.fillGradient(x, y, x + w, y + h, 0xE0101018, 0xF0050508);
        }
    }

    // ------------------------------------------------------------------ cover: palette + pattern

    public static void cover(DrawContext ctx, String paletteId, String patternId, int x, int y, int w, int h, double t) {
        BinderStyles.Style p = BinderStyles.find("palette", paletteId);
        int c1 = p == null ? 0xFFB3262E : p.c1(), c2 = p == null ? 0xFFB3262E : p.c2();
        if (c1 == c2) ctx.fillGradient(x, y, x + w, y + h, Ui.brighten(c1, 18), Ui.darken(c1, 28));
        else ctx.fillGradient(x, y, x + w, y + h, c1, c2);
        ctx.enableScissor(x, y, x + w, y + h);
        pattern(ctx, patternId, x, y, w, h, t);
        ctx.disableScissor();
    }

    public static void pattern(DrawContext ctx, String id, int x, int y, int w, int h, double t) {
        int c = 0x30FFFFFF, d = 0x22000000;
        switch (id) {
            case "dots" -> {
                for (int yy = y + 4; yy < y + h; yy += 8)
                    for (int xx = x + 4 + ((yy / 8) % 2) * 4; xx < x + w; xx += 8) ctx.fill(xx, yy, xx + 2, yy + 2, c);
            }
            case "pokeballs" -> {
                for (int yy = y + 10; yy < y + h + 10; yy += 22)
                    for (int xx = x + 10 + ((yy / 22) % 2) * 11; xx < x + w + 10; xx += 22) {
                        ring(ctx, xx, yy, 7, c);
                        circle(ctx, xx, yy, 2, c);
                    }
            }
            case "lightning" -> {
                for (int i = 0; i < w; i += 26)
                    for (int j = 0; j < h; j += 34) {
                        int bx = x + i + ((j / 34) % 2) * 13, by = y + j;
                        ctx.fill(bx + 6, by, bx + 10, by + 8, c);
                        ctx.fill(bx + 2, by + 8, bx + 10, by + 11, c);
                        ctx.fill(bx + 2, by + 11, bx + 6, by + 20, c);
                    }
            }
            case "flames" -> {
                for (int i = 0; i < w; i += 18)
                    for (int j = 0; j < h; j += 24) {
                        int fx = x + i + ((j / 24) % 2) * 9, fy = y + j;
                        float flick = (float) Math.sin(t * 4 + i + j) * 1.5f;
                        for (int k = 0; k < 12; k++) {
                            int half = (int) (Math.sin(k / 12.0 * Math.PI) * 4 + (k > 6 ? 1 : 0));
                            ctx.fill(fx - half + (int) (flick * (12 - k) / 12), fy + k, fx + half + (int) (flick * (12 - k) / 12), fy + k + 1, c);
                        }
                    }
            }
            case "waves" -> {
                for (int j = 0; j < h; j += 10)
                    for (int i = 0; i < w; i += 2) {
                        int wy = y + j + (int) (Math.sin((i + j * 3) * 0.12 + t) * 2.5);
                        ctx.fill(x + i, wy, x + i + 2, wy + 1, c);
                    }
            }
            case "leaves" -> {
                for (int i = 0; i < w; i += 20)
                    for (int j = 0; j < h; j += 20) {
                        int lx = x + i + ((j / 20) % 2) * 10, ly = y + j;
                        for (int k = 0; k < 9; k++) {
                            int half = (int) (Math.sin(k / 9.0 * Math.PI) * 4);
                            ctx.fill(lx - half + k / 2, ly + k, lx + half + k / 2, ly + k + 1, c);
                        }
                        ctx.fill(lx, ly, lx + 1, ly + 9, d);
                    }
            }
            case "stars" -> {
                for (int i = 0; i < 70; i++) {
                    int sx = x + (int) (rnd(i + 300) * w), sy = y + (int) (rnd(i + 600) * h);
                    int s = 1 + i % 3;
                    float a = 0.5f + 0.5f * (float) Math.sin(t * 2 + i);
                    ctx.fill(sx - s, sy, sx + s + 1, sy + 1, withAlpha(c, a * 1.5f));
                    ctx.fill(sx, sy - s, sx + 1, sy + s + 1, withAlpha(c, a * 1.5f));
                }
            }
            case "scales" -> {
                for (int j = 0; j < h + 8; j += 8)
                    for (int i = 0; i < w + 8; i += 12) {
                        int sx = x + i + ((j / 8) % 2) * 6, sy = y + j;
                        for (int k = -5; k <= 5; k++) {
                            int yy = sy + (int) Math.sqrt(25 - k * k);
                            ctx.fill(sx + k, yy, sx + k + 1, yy + 1, c);
                        }
                    }
            }
            case "paws" -> {
                for (int i = 0; i < w; i += 24)
                    for (int j = 0; j < h; j += 24) {
                        int px = x + i + ((j / 24) % 2) * 12, py = y + j;
                        circle(ctx, px + 6, py + 9, 3, c);
                        circle(ctx, px + 2, py + 4, 1, c);
                        circle(ctx, px + 6, py + 2, 1, c);
                        circle(ctx, px + 10, py + 4, 1, c);
                    }
            }
            case "diamonds" -> {
                for (int i = 0; i < w + 16; i += 16)
                    for (int j = 0; j < h + 16; j += 16) {
                        int cx = x + i + ((j / 16) % 2) * 8, cy = y + j;
                        for (int k = -5; k <= 5; k++) ctx.fill(cx - (5 - Math.abs(k)), cy + k, cx + (5 - Math.abs(k)) + 1, cy + k + 1, k % 2 == 0 ? c : d);
                    }
            }
            case "crystals" -> {
                for (int i = 0; i < w; i += 14) {
                    int ch = 10 + (int) (rnd(i) * 26);
                    int cx = x + i + 7;
                    for (int k = 0; k < ch; k++) {
                        int half = k < 3 ? k : (k > ch - 3 ? ch - k : 3);
                        ctx.fill(cx - half, y + h - k, cx + half + 1, y + h - k + 1, c);
                    }
                }
            }
            default -> {}
        }
    }
}

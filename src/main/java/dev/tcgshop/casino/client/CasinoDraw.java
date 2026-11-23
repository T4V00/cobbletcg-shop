package dev.tcgshop.casino.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.tcgshop.casino.Blackjack;
import dev.tcgshop.casino.SlotMath;
import dev.tcgshop.client.binder.Ui;
import dev.tcgshop.client.screen.Draw;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.joml.Matrix4f;

/**
 * Procedural casino art for the screens (no textures): slot symbols, playing cards, chips and wheels.
 * Everything is drawn with fills and triangles, so there is no third-party artwork involved.
 */
public final class CasinoDraw {
    private CasinoDraw() {}

    // ------------------------------------------------------------------ primitives

    public static void disc(DrawContext ctx, float cx, float cy, float r, int color) {
        ellipse(ctx, cx, cy, r, r, color);
    }

    public static void ellipse(DrawContext ctx, float cx, float cy, float rx, float ry, int color) {
        int y0 = (int) Math.floor(cy - ry), y1 = (int) Math.ceil(cy + ry);
        for (int y = y0; y < y1; y++) {
            float dy = (y + 0.5f - cy) / ry;
            if (Math.abs(dy) > 1) continue;
            float half = rx * (float) Math.sqrt(1 - dy * dy);
            int a = Math.round(cx - half), b = Math.round(cx + half);
            if (b > a) ctx.fill(a, y, b, y + 1, color);
        }
    }

    /** Thick line made of small squares (stems, rays). */
    public static void line(DrawContext ctx, float x0, float y0, float x1, float y1, float w, int color) {
        float len = (float) Math.hypot(x1 - x0, y1 - y0);
        int steps = Math.max(1, (int) (len * 1.5f));
        for (int i = 0; i <= steps; i++) {
            float t = i / (float) steps, x = x0 + (x1 - x0) * t, y = y0 + (y1 - y0) * t;
            ctx.fill(Math.round(x - w / 2), Math.round(y - w / 2), Math.round(x + w / 2), Math.round(y + w / 2), color);
        }
    }

    public static void centered(DrawContext ctx, Text t, float cx, float cy, float scale, int color, boolean shadow) {
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        MatrixStack m = ctx.getMatrices();
        m.push();
        m.translate(cx, cy, 0);
        m.scale(scale, scale, 1);
        ctx.drawText(tr, t, -tr.getWidth(t) / 2, -4, color, shadow);
        m.pop();
    }

    // ------------------------------------------------------------------ slot symbols

    /** Slot symbol in an s x s box at (x, y). */
    public static void symbol(DrawContext ctx, int sym, float x, float y, float s) {
        float cx = x + s / 2, cy = y + s / 2;
        switch (sym) {
            case SlotMath.CHERRY -> {
                line(ctx, x + s * 0.32f, y + s * 0.62f, x + s * 0.58f, y + s * 0.2f, Math.max(1.5f, s * 0.06f), 0xFF2E7D32);
                line(ctx, x + s * 0.68f, y + s * 0.68f, x + s * 0.58f, y + s * 0.2f, Math.max(1.5f, s * 0.06f), 0xFF2E7D32);
                ellipse(ctx, x + s * 0.68f, y + s * 0.2f, s * 0.16f, s * 0.07f, 0xFF43A047);
                for (float[] c : new float[][]{{0.3f, 0.7f}, {0.68f, 0.74f}}) {
                    disc(ctx, x + s * c[0], y + s * c[1], s * 0.19f, 0xFF7A0A12);
                    disc(ctx, x + s * c[0], y + s * c[1], s * 0.16f, 0xFFD3122A);
                    disc(ctx, x + s * (c[0] - 0.05f), y + s * (c[1] - 0.05f), s * 0.05f, 0xFFFF9AA6);
                }
            }
            case SlotMath.LEMON -> {
                ellipse(ctx, cx, cy, s * 0.38f, s * 0.27f, 0xFFB08A00);
                ellipse(ctx, cx, cy, s * 0.35f, s * 0.24f, 0xFFFFD600);
                ellipse(ctx, cx - s * 0.36f, cy, s * 0.06f, s * 0.05f, 0xFFFFD600);
                ellipse(ctx, cx + s * 0.36f, cy, s * 0.06f, s * 0.05f, 0xFFFFD600);
                ellipse(ctx, cx - s * 0.1f, cy - s * 0.09f, s * 0.12f, s * 0.05f, 0xFFFFF59D);
            }
            case SlotMath.BELL -> {
                float top = y + s * 0.16f, bottom = y + s * 0.78f, h = bottom - top;
                for (int i = 0; i < Math.ceil(h); i++) {
                    float t = i / h, half = s * (0.12f + 0.26f * t * t + 0.06f * t);
                    int col = Ui.lerpC(0xFFFFE082, 0xFFE09B00, t);
                    ctx.fill(Math.round(cx - half), Math.round(top + i), Math.round(cx + half), Math.round(top + i + 1), col);
                }
                ctx.fill(Math.round(cx - s * 0.44f), Math.round(bottom), Math.round(cx + s * 0.44f), Math.round(bottom + s * 0.07f), 0xFFB07800);
                disc(ctx, cx, bottom + s * 0.1f, s * 0.08f, 0xFF8D5A00);
                disc(ctx, cx, top - s * 0.02f, s * 0.06f, 0xFFB07800);
                ctx.fill(Math.round(cx - s * 0.2f), Math.round(top + h * 0.35f), Math.round(cx - s * 0.14f), Math.round(top + h * 0.8f), 0x70FFFFFF);
            }
            case SlotMath.BAR -> {
                int x0 = Math.round(x + s * 0.08f), y0 = Math.round(y + s * 0.3f), w = Math.round(s * 0.84f), h = Math.round(s * 0.4f);
                Ui.rounded(ctx, x0, y0, w, h, 3, 0xFF101010);
                Ui.roundedOutline(ctx, x0, y0, w, h, 3, 0xFFE0E0E0);
                centered(ctx, Text.literal("BAR").formatted(Formatting.BOLD), cx, cy + 0.5f, s / 26f, 0xFFFFFFFF, false);
            }
            case SlotMath.STAR -> {
                centered(ctx, Text.literal("★"), cx + 1, cy + 1, s / 10f, 0xFF8A5A00, false);
                centered(ctx, Text.literal("★"), cx, cy, s / 10f, 0xFFFFC928, false);
            }
            case SlotMath.SEVEN -> {
                centered(ctx, Text.literal("7").formatted(Formatting.BOLD), cx + 1.5f, cy + 1.5f, s / 9f, 0xFF5A0000, false);
                centered(ctx, Text.literal("7").formatted(Formatting.BOLD), cx, cy, s / 9f, 0xFFE8132B, false);
            }
            case SlotMath.GEM -> {
                double t = System.nanoTime() / 1e9;
                int c = 0xFF000000 | net.minecraft.util.math.MathHelper.hsvToRgb((float) ((t * 0.15) % 1.0), 0.55f, 1f);
                centered(ctx, Text.literal("♦"), cx + 1, cy + 1, s / 8.5f, 0xFF1A2A6A, false);
                centered(ctx, Text.literal("♦"), cx, cy, s / 8.5f, c, false);
            }
            default -> {}
        }
    }

    // ------------------------------------------------------------------ playing cards

    public static final String[] RANKS = {"A", "2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K"};
    public static final String[] SUITS = {"♠", "♥", "♦", "♣"};

    /** Card at (x, y) of w x h; card = -1 draws its back. */
    public static void card(DrawContext ctx, int card, float x, float y, float w, float h) {
        int ix = Math.round(x), iy = Math.round(y), iw = Math.round(w), ih = Math.round(h);
        Ui.rounded(ctx, ix + 2, iy + 3, iw, ih, 4, 0x66000000);
        if (card < 0) {
            Ui.rounded(ctx, ix, iy, iw, ih, 4, 0xFFF4F4F4);
            Ui.rounded(ctx, ix + 3, iy + 3, iw - 6, ih - 6, 3, 0xFF9C1C2A);
            for (int yy = iy + 5; yy < iy + ih - 5; yy += 4)
                for (int xx = ix + 5 + ((yy - iy) / 4 % 2) * 2; xx < ix + iw - 5; xx += 4) ctx.fill(xx, yy, xx + 1, yy + 1, 0x66FFFFFF);
            Ui.roundedOutline(ctx, ix, iy, iw, ih, 4, 0xFF505050);
            return;
        }
        int rank = Blackjack.rank(card), suit = Blackjack.suit(card);
        int col = suit == 1 || suit == 2 ? 0xFFD32F2F : 0xFF1A1A1A;
        Ui.roundedGradient(ctx, ix, iy, iw, ih, 4, 0xFFFFFFFF, 0xFFEDEBE4);
        Ui.roundedOutline(ctx, ix, iy, iw, ih, 4, 0xFF8A8A8A);
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        ctx.drawText(tr, RANKS[rank], ix + 3, iy + 3, col, false);
        ctx.drawText(tr, SUITS[suit], ix + 3, iy + 12, col, false);
        float big = Math.max(1.5f, h / 22f);
        centered(ctx, Text.literal(rank >= 10 ? RANKS[rank] : SUITS[suit]).formatted(rank >= 10 ? Formatting.BOLD : Formatting.RESET),
                x + w / 2 + 2, y + h / 2 + 2, big, col, false);
    }

    // ------------------------------------------------------------------ chips

    public static int chipColor(long v) {
        if (v >= 100_000) return 0xFF212121;
        if (v >= 10_000) return 0xFF7B1FA2;
        if (v >= 1_000) return 0xFF1E88E5;
        if (v >= 100) return 0xFF2E7D32;
        return 0xFFE53935;
    }

    public static String shortAmount(long v) {
        if (v >= 1_000_000) return (v % 1_000_000 == 0 ? String.valueOf(v / 1_000_000) : String.format(java.util.Locale.ROOT, "%.1f", v / 1e6)) + "M";
        if (v >= 1_000) return (v % 1_000 == 0 ? String.valueOf(v / 1_000) : String.format(java.util.Locale.ROOT, "%.1f", v / 1e3)) + "k";
        return String.valueOf(v);
    }

    public static void chip(DrawContext ctx, float cx, float cy, float r, long value, boolean selected) {
        int c = chipColor(value);
        if (selected) disc(ctx, cx, cy, r + 2, 0xFFFFD34D);
        disc(ctx, cx + 0.5f, cy + 1.5f, r, 0x80000000);
        disc(ctx, cx, cy, r, c);
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            disc(ctx, cx + (float) Math.cos(a) * r * 0.8f, cy + (float) Math.sin(a) * r * 0.8f, Math.max(1, r * 0.14f), 0xFFF5F5F5);
        }
        disc(ctx, cx, cy, r * 0.62f, Ui.brighten(c, 30));
        centered(ctx, Text.literal(shortAmount(value)), cx, cy + 0.5f, Math.min(1f, r / 9f), 0xFFFFFFFF, true);
    }

    // ------------------------------------------------------------------ wheels

    /**
     * Ring of `colors.length` equal sectors between radii r0 and r1, the first sector centered at `angle`
     * (radians, 0 = up, clockwise). Triangles through the GUI matrix.
     */
    public static void ring(DrawContext ctx, float cx, float cy, float r0, float r1, double angle, int[] colors) {
        ctx.draw();
        Matrix4f mat = ctx.getMatrices().peek().getPositionMatrix();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        BufferBuilder bb = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
        int n = colors.length;
        double step = Math.PI * 2 / n;
        int sub = Math.max(1, 96 / n);
        for (int i = 0; i < n; i++) {
            for (int k = 0; k < sub; k++) {
                double a0 = angle + (i - 0.5) * step + k * step / sub, a1 = a0 + step / sub;
                float s0 = (float) Math.sin(a0), c0 = (float) -Math.cos(a0), s1 = (float) Math.sin(a1), c1 = (float) -Math.cos(a1);
                int col = colors[i];
                bb.vertex(mat, cx + s0 * r0, cy + c0 * r0, 0).color(col);
                bb.vertex(mat, cx + s0 * r1, cy + c0 * r1, 0).color(col);
                bb.vertex(mat, cx + s1 * r1, cy + c1 * r1, 0).color(col);
                bb.vertex(mat, cx + s0 * r0, cy + c0 * r0, 0).color(col);
                bb.vertex(mat, cx + s1 * r1, cy + c1 * r1, 0).color(col);
                bb.vertex(mat, cx + s1 * r0, cy + c1 * r0, 0).color(col);
            }
        }
        BufferRenderer.drawWithGlobalProgram(bb.end());
        RenderSystem.enableCull();
    }

    /** Text rotated around (cx, cy), placed at radius r along `angle` (0 = up, clockwise), reading outwards. */
    public static void radialText(DrawContext ctx, Text t, float cx, float cy, float r, double angle, float scale, int color) {
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        MatrixStack m = ctx.getMatrices();
        m.push();
        m.translate(cx + (float) Math.sin(angle) * r, cy - (float) Math.cos(angle) * r, 0);
        m.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotation((float) angle));
        m.scale(scale, scale, 1);
        ctx.drawText(tr, t, -tr.getWidth(t) / 2, -4, color, false);
        m.pop();
    }

    /** Gold pointer pointing down, its tip at (cx, tipY). */
    public static void pointer(DrawContext ctx, float cx, float tipY, float size) {
        for (int i = 0; i < size; i++) {
            float half = (size - i) * 0.55f;
            ctx.fill(Math.round(cx - half), Math.round(tipY - i - 1), Math.round(cx + half), Math.round(tipY - i), i < 2 ? 0xFFB07800 : 0xFFFFD34D);
        }
    }

    public static int alpha(int argb, float a) {
        return Draw.withAlpha(argb, a);
    }
}

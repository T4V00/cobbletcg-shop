package dev.tcgshop.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

import java.util.HashMap;
import java.util.Map;

/** Utilidades de dibujo para cartas, sobres, brillos y cortes. */
public final class Draw {
    public static final float CARD_RATIO = 63f / 88f;
    /** Proporción de un sobre real (≈ 6,6 x 11,8 cm). Las texturas de cobbletcg tienen tamaños distintos: se estiran a esto. */
    public static final float PACK_RATIO = 0.56f;

    private static final Map<Identifier, Boolean> TEXTURE_EXISTS = new HashMap<>();

    private Draw() {}

    public static boolean textureExists(Identifier id) {
        return TEXTURE_EXISTS.computeIfAbsent(id, i -> MinecraftClient.getInstance().getResourceManager().getResource(i).isPresent());
    }

    /**
     * Recorta un círculo de una textura (centro uc,vc y radios ur,vr en coordenadas 0..1) y lo dibuja con radio r en
     * (cx, cy): se pinta fila a fila con el ancho de la cuerda, así queda redondo sin máscaras.
     */
    public static void circleTexture(DrawContext ctx, Identifier tex, int cx, int cy, int r, float uc, float vc, float ur, float vr) {
        ctx.draw();
        final int TW = 1000, TH = 1000;
        for (int dy = -r; dy < r; dy++) {
            double yy = dy + 0.5;
            int half = (int) Math.round(Math.sqrt(Math.max(0, r * r - yy * yy)));
            if (half <= 0) continue;
            float v0 = vc + (dy / (float) r) * vr, vh = vr / r;
            float u0 = uc - (half / (float) r) * ur, uw = 2 * half / (float) r * ur;
            ctx.drawTexture(tex, cx - half, cy + dy, 2 * half, 1, u0 * TW, v0 * TH, Math.max(1, Math.round(uw * TW)), Math.max(1, Math.round(vh * TH)), TW, TH);
        }
    }

    /** Dibuja una textura completa estirada al rectángulo (cualquier tamaño de imagen). */
    public static void texture(DrawContext ctx, Identifier tex, float x, float y, float w, float h, float alpha) {
        // los rectángulos y fondos de DrawContext se acumulan y se pintan al final; la textura se pinta al momento.
        // Sin vaciarlos antes, un panel dibujado ANTES de la carta acababa encima (venta rápida del teléfono sin arte).
        ctx.draw();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1f, 1f, 1f, alpha);
        ctx.drawTexture(tex, Math.round(x), Math.round(y), Math.round(w), Math.round(h), 0f, 0f, 1, 1, 1, 1);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    /** Sobre: textura plana si existe; si no (faltan los resource packs) el icono del ítem escalado. */
    public static void pack(DrawContext ctx, Identifier tex, ItemStack fallback, float x, float y, float w, float h, float alpha) {
        if (textureExists(tex)) {
            ctx.fill(Math.round(x) + 4, Math.round(y) + 6, Math.round(x + w) + 4, Math.round(y + h) + 6, withAlpha(0xFF000000, 0.4f * alpha));
            texture(ctx, tex, x, y, w, h, alpha);
        } else {
            ctx.fillGradient(Math.round(x), Math.round(y), Math.round(x + w), Math.round(y + h), withAlpha(0xFF3A3F6B, alpha), withAlpha(0xFF1D2040, alpha));
            ctx.fill(Math.round(x), Math.round(y), Math.round(x + w), Math.round(y + h * 0.07f), withAlpha(0xFFB0B4C8, alpha));
            MatrixStack m = ctx.getMatrices();
            m.push();
            m.translate(x + w / 2, y + h / 2, 0);
            float s = w / 20f;
            m.scale(s, s, 1);
            ctx.drawItem(fallback, -8, -8);
            m.pop();
        }
    }

    /** Cara de carta sin efectos (textura de los resource packs de cobbletcg, o placeholder con el icono). */
    public static void cardFace(DrawContext ctx, Identifier tex, ItemStack stack, float x, float y, float w, float h, float alpha) {
        if (textureExists(tex)) {
            texture(ctx, tex, x, y, w, h, alpha);
        } else {
            ctx.fillGradient(Math.round(x), Math.round(y), Math.round(x + w), Math.round(y + h), withAlpha(0xFFEFE6C8, alpha), withAlpha(0xFFD2C49A, alpha));
            MatrixStack m = ctx.getMatrices();
            m.push();
            m.translate(x + w / 2, y + h * 0.38f, 0);
            float s = w / 28f;
            m.scale(s, s, 1);
            ctx.drawItem(stack, -8, -8);
            m.pop();
        }
    }

    /** Reverso original dibujado por código (sin assets con copyright), centrado en el origen actual. */
    public static void cardBack(DrawContext ctx, float w, float h, float alpha, int glowColor, double t) {
        int x0 = Math.round(-w / 2), y0 = Math.round(-h / 2), iw = Math.round(w), ih = Math.round(h);
        ctx.fill(x0 + 3, y0 + 5, x0 + iw + 3, y0 + ih + 5, withAlpha(0xFF000000, 0.45f * alpha));
        ctx.fill(x0, y0, x0 + iw, y0 + ih, withAlpha(0xFFF2F2F2, alpha));
        int b = Math.max(3, iw / 18);
        ctx.fillGradient(x0 + b, y0 + b, x0 + iw - b, y0 + ih - b, withAlpha(0xFF2B4FB8, alpha), withAlpha(0xFF14275E, alpha));
        for (int yy = y0 + b + 4; yy < y0 + ih - b - 2; yy += 8) {
            for (int xx = x0 + b + (Math.floorMod(yy / 8, 2) == 0 ? 2 : 6); xx < x0 + iw - b - 2; xx += 8) {
                ctx.fill(xx, yy, xx + 2, yy + 2, withAlpha(0xFF3E66D6, alpha * 0.7f));
            }
        }
        int r = Math.max(4, iw / 4);
        for (int dy = -r; dy <= r; dy++) {
            int dx = (int) Math.sqrt(r * r - dy * dy);
            int col = dy < -1 ? 0xFFE8463C : dy > 1 ? 0xFFF5F5F5 : 0xFF151515;
            ctx.fill(-dx, dy, dx, dy + 1, withAlpha(col, alpha));
        }
        ctx.fill(-r / 3, -r / 3, r / 3, r / 3, withAlpha(0xFF151515, alpha));
        ctx.fill(-r / 5, -r / 5, r / 5, r / 5, withAlpha(0xFFF5F5F5, alpha));
        if (glowColor != 0) {
            float p = 0.5f + 0.5f * (float) Math.sin(t * 6);
            ctx.drawBorder(x0, y0, iw, ih, withAlpha(glowColor, alpha * (0.5f + 0.5f * p)));
            ctx.drawBorder(x0 + 1, y0 + 1, iw - 2, ih - 2, withAlpha(glowColor, alpha * 0.5f * p));
        }
    }

    /**
     * Dibuja un rectángulo partido por un corte: el contenido se pinta en franjas verticales recortadas
     * (scissor) por encima o por debajo de la línea de corte, y cada mitad se desplaza por separado.
     *
     * @param cut   altura del corte en cada franja, normalizada 0..1 dentro del rectángulo
     * @param part  0 = mitad de arriba, 1 = mitad de abajo
     */
    public static void split(DrawContext ctx, float x, float y, float w, float h, float[] cut, int part,
                             float dx, float dy, Runnable painter) {
        int n = cut.length;
        for (int i = 0; i < n; i++) {
            int sx0 = Math.round(x + w * i / n + dx), sx1 = Math.round(x + w * (i + 1) / n + dx);
            float cy = y + h * MathHelper.clamp(cut[i], 0f, 1f);
            int top = part == 0 ? Math.round(y + dy) : Math.round(cy + dy);
            int bot = part == 0 ? Math.round(cy + dy) : Math.round(y + h + dy);
            if (bot <= top || sx1 <= sx0) continue;
            ctx.enableScissor(sx0, top, sx1, bot);
            MatrixStack m = ctx.getMatrices();
            m.push();
            m.translate(dx, dy, 0);
            painter.run();
            m.pop();
            ctx.disableScissor();
        }
    }

    /** Borde rasgado (fibras de papel/cartón) a lo largo del corte. */
    public static void tornEdge(DrawContext ctx, float x, float y, float w, float h, float[] cut, float dx, float dy,
                                int color, float alpha, long seed) {
        int n = cut.length;
        for (int i = 0; i < n; i++) {
            float x0 = x + w * i / n + dx, x1 = x + w * (i + 1) / n + dx;
            float cy = y + h * MathHelper.clamp(cut[i], 0f, 1f) + dy;
            for (float xx = x0; xx < x1; xx += 2) {
                long hsh = (seed * 31 + (long) (xx * 7)) * 0x9E3779B97F4A7C15L;
                int len = 1 + (int) ((hsh >>> 60) & 3);
                int side = ((hsh >>> 50) & 1) == 0 ? -1 : 1;
                int yy = Math.round(cy);
                ctx.fill(Math.round(xx), yy, Math.round(xx) + 2, yy + side * len, withAlpha(color, alpha));
            }
        }
    }

    /** Brillo holográfico (banda diagonal que sigue al ratón) sobre un rectángulo de carta en coordenadas de pantalla. */
    public static void holo(DrawContext ctx, float x, float y, float w, float h, int tier, int color, double t, int mouseX) {
        int x0 = Math.round(x), y0 = Math.round(y);
        ctx.enableScissor(x0, y0, x0 + Math.round(w), y0 + Math.round(h));
        MatrixStack m = ctx.getMatrices();
        m.push();
        float cx = x + w / 2, cy = y + h / 2;
        float follow = MathHelper.clamp((mouseX - cx) / w, -1, 1) * w * 0.5f;
        float sweep = (float) (((t * 0.45) % 1.6) - 0.3) * w * 1.6f - w * 0.8f;
        m.translate(cx + follow * 0.6f + sweep * 0.4f, cy, 0);
        m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(22));
        int strips = 14;
        float bandW = w * 0.55f;
        for (int i = 0; i < strips; i++) {
            float f = i / (float) (strips - 1);
            float a = (float) Math.sin(f * Math.PI) * (0.10f + 0.06f * tier);
            int col = tier >= 3 ? hsv((float) ((f + t * 0.25) % 1.0), 0.55f, 1f) : brighten(color);
            int sx0 = Math.round(-bandW / 2 + f * bandW);
            ctx.fill(sx0, Math.round(-h), sx0 + Math.max(1, Math.round(bandW / strips) + 1), Math.round(h), withAlpha(col, a));
        }
        m.pop();
        ctx.disableScissor();
    }

    public static void glow(DrawContext ctx, float x, float y, float w, float h, int color, float strength) {
        for (int i = 8; i >= 1; i--) {
            float grow = i * 4f;
            float a = strength * (1f - i / 9f) * 0.35f;
            ctx.fill(Math.round(x - w / 2 - grow), Math.round(y - h / 2 - grow), Math.round(x + w / 2 + grow),
                    Math.round(y + h / 2 + grow), withAlpha(color, a));
        }
    }

    public static void rays(DrawContext ctx, float x, float y, float len, double angle, int color, float alpha, int count) {
        MatrixStack m = ctx.getMatrices();
        for (int i = 0; i < count; i++) {
            m.push();
            m.translate(x, y, 0);
            m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) (angle + i * 360.0 / count)));
            for (int s = 0; s < 4; s++) {
                int half = 2 + s * 3;
                ctx.fill(0, -half, Math.round(len * (1 - s * 0.15f)), half, withAlpha(color, alpha / (s + 1)));
            }
            m.pop();
        }
    }

    public static void scaledCentered(DrawContext ctx, net.minecraft.text.Text text, float cx, float y, float scale, int color) {
        MatrixStack m = ctx.getMatrices();
        m.push();
        m.translate(cx, y, 0);
        m.scale(scale, scale, 1);
        ctx.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer, text, 0, 0, color);
        m.pop();
    }

    public static int withAlpha(int argb, float a) {
        int base = (argb >>> 24) & 0xFF;
        int alpha = MathHelper.clamp(Math.round(base * a), 0, 255);
        return (alpha << 24) | (argb & 0xFFFFFF);
    }

    public static int brighten(int argb) {
        int r = Math.min(255, ((argb >> 16) & 0xFF) + 80);
        int g = Math.min(255, ((argb >> 8) & 0xFF) + 80);
        int b = Math.min(255, (argb & 0xFF) + 80);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    public static int hsv(float h, float s, float v) {
        return 0xFF000000 | MathHelper.hsvToRgb(h, s, v);
    }

    public static double easeOutBack(double x) {
        double c1 = 1.70158, c3 = c1 + 1;
        return 1 + c3 * Math.pow(x - 1, 3) + c1 * Math.pow(x - 1, 2);
    }

    public static double easeOutCubic(double x) { return 1 - Math.pow(1 - x, 3); }

    public static double easeInCubic(double x) { return x * x * x; }
}

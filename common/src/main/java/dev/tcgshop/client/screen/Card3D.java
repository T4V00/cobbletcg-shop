package dev.tcgshop.client.screen;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * Card with "TCG Pocket style" volume on a 2D screen: the image is projected with real perspective onto a subdivided
 * mesh (without the distortion of a single quad), with edge/thickness, shading based on tilt, a light reflection
 * that follows the mouse and a rainbow holographic foil for rare cards. Drawn centered on the current origin.
 */
public final class Card3D {
    private Card3D() {}

    /** Projection: Y rotation (ry: + = right side moves away), X rotation (rx: + = top edge moves away) and perspective. */
    private static final class Proj {
        final float cy, sy, cx, sx, d;
        float x, y, z;

        Proj(float rx, float ry, float w, float h) {
            double ax = Math.toRadians(rx), ay = Math.toRadians(ry);
            cx = (float) Math.cos(ax);
            sx = (float) Math.sin(ax);
            cy = (float) Math.cos(ay);
            sy = (float) Math.sin(ay);
            d = Math.max(w, h) * 2.3f;
        }

        void p(float lx, float ly, float lz) {
            float x1 = lx * cy + lz * sy, z1 = -lx * sy + lz * cy;
            float y2 = ly * cx - z1 * sx, z2 = ly * sx + z1 * cx;
            float k = d / Math.max(d * 0.2f, d - z2);
            x = x1 * k;
            y = y2 * k;
            z = z2;
        }
    }

    /**
     * @param tex   card texture (full image 0..1)
     * @param foil  0 = normal, 1 = holo, 2 = intense holo (graded / secret rare)
     * @param glow  color of the surrounding halo (0 = no halo)
     * @param dim   0..1 darkens the card (not playable)
     */
    public static void draw(GuiGraphics ctx, ResourceLocation tex, float w, float h, float rx, float ry, float alpha, int foil, int glow, float dim, double t) {
        ctx.flush();
        Matrix4f mat = ctx.pose().last().pose();
        Proj pr = new Proj(rx, ry, w, h);
        float hw = w / 2f, hh = h / 2f;
        float b = Math.max(1f, w * 0.035f), rad = Math.max(1.5f, w * 0.06f);
        float tilt = Math.min(1f, (float) Math.sqrt(rx * rx + ry * ry) / 22f);
        float light = 1f - 0.12f * tilt;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        // halo (playable, selected, target...)
        if (glow != 0) {
            BufferBuilder g = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
            fan(g, mat, pr, hw + b + 3.5f, hh + b + 3.5f, rad + 3.5f, 0, Draw.withAlpha(glow, 0.22f * alpha));
            fan(g, mat, pr, hw + b + 2f, hh + b + 2f, rad + 2f, 0, Draw.withAlpha(glow, 0.35f * alpha));
            fan(g, mat, pr, hw + b + 1f, hh + b + 1f, rad + 1f, 0, Draw.withAlpha(glow, 0.55f * alpha));
            BufferUploader.drawWithShader(g.buildOrThrow());
        }
        // edge (thickness): a layer further back that peeks out when tilted, and the white frame
        BufferBuilder f = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        float thick = Math.max(1.2f, w * 0.03f);
        if (tilt > 0.02f) {
            for (int k = 3; k >= 1; k--) fan(f, mat, pr, hw + b, hh + b, rad, -thick * k / 3f, Draw.withAlpha(shade(0xFFA9B4C2, 0.85f + 0.05f * k), alpha));
        }
        fan(f, mat, pr, hw + b, hh + b, rad, 0, Draw.withAlpha(shade(0xFFFFFFFF, light * (1 - dim * 0.35f)), alpha));
        BufferUploader.drawWithShader(f.buildOrThrow());

        // image on a subdivided mesh, shaded by depth
        int nx = Mth.clamp(Math.round(w / 9f), 3, 12), ny = Mth.clamp(Math.round(nx * 1.4f), 4, 16);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, tex);
        BufferBuilder q = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        float depthRef = Math.max(w, h) * 0.5f;
        for (int j = 0; j < ny; j++) {
            for (int i = 0; i < nx; i++) {
                float u0 = i / (float) nx, u1 = (i + 1) / (float) nx, v0 = j / (float) ny, v1 = (j + 1) / (float) ny;
                texV(q, mat, pr, hw, hh, u0, v0, depthRef, alpha, dim);
                texV(q, mat, pr, hw, hh, u0, v1, depthRef, alpha, dim);
                texV(q, mat, pr, hw, hh, u1, v1, depthRef, alpha, dim);
                texV(q, mat, pr, hw, hh, u1, v0, depthRef, alpha, dim);
            }
        }
        BufferUploader.drawWithShader(q.buildOrThrow());

        // reflection + holo foil: additive blending
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        float gx = 0.5f + Mth.clamp(ry / 26f, -1, 1) * 0.55f, gy = 0.42f - Mth.clamp(rx / 22f, -1, 1) * 0.55f;
        float gStr = (0.07f + 0.30f * tilt) * alpha * (1 - dim * 0.7f);
        int fx = foil > 0 ? Math.max(nx, 8) : nx, fy = foil > 0 ? Math.max(ny, 11) : ny;
        BufferBuilder s = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int j = 0; j < fy; j++) {
            for (int i = 0; i < fx; i++) {
                float u0 = i / (float) fx, u1 = (i + 1) / (float) fx, v0 = j / (float) fy, v1 = (j + 1) / (float) fy;
                shineV(s, mat, pr, hw, hh, u0, v0, gx, gy, gStr, foil, rx, ry, tilt, alpha, t);
                shineV(s, mat, pr, hw, hh, u0, v1, gx, gy, gStr, foil, rx, ry, tilt, alpha, t);
                shineV(s, mat, pr, hw, hh, u1, v1, gx, gy, gStr, foil, rx, ry, tilt, alpha, t);
                shineV(s, mat, pr, hw, hh, u1, v0, gx, gy, gStr, foil, rx, ry, tilt, alpha, t);
            }
        }
        BufferUploader.drawWithShader(s.buildOrThrow());
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableCull();
        RenderSystem.setShaderColor(1, 1, 1, 1);
    }

    /** Soft shadow cast under the card; lift (0..1+) separates and blurs it more. */
    public static void shadow(GuiGraphics ctx, float w, float h, float rx, float ry, float lift, float alpha) {
        ctx.flush();
        Matrix4f mat = ctx.pose().last().pose();
        Proj pr = new Proj(rx, ry, w, h);
        float b = Math.max(1f, w * 0.035f), rad = Math.max(1.5f, w * 0.06f);
        float ox = 1.5f + lift * w * 0.08f, oy = 2.5f + lift * w * 0.16f;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder g = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        Matrix4f m2 = new Matrix4f(mat).translate(ox, oy, 0);
        float spread = 1.5f + lift * 4f;
        for (int k = 3; k >= 0; k--) {
            float grow = spread * k / 2f;
            float a = (0.16f - 0.03f * k) * alpha / (1 + lift * 0.6f);
            fan(g, m2, pr, w / 2f + b + grow, h / 2f + b + grow, rad + grow, 0, Draw.withAlpha(0xFF0A2A50, a));
        }
        BufferUploader.drawWithShader(g.buildOrThrow());
        RenderSystem.enableCull();
    }

    private static int shade(int argb, float k) {
        int r = Math.min(255, Math.round(((argb >> 16) & 0xFF) * k)), g = Math.min(255, Math.round(((argb >> 8) & 0xFF) * k)),
                bl = Math.min(255, Math.round((argb & 0xFF) * k));
        return (argb & 0xFF000000) | (r << 16) | (g << 8) | bl;
    }

    private static void texV(BufferBuilder q, Matrix4f mat, Proj pr, float hw, float hh, float u, float v, float depthRef, float alpha, float dim) {
        pr.p(-hw + u * hw * 2, -hh + v * hh * 2, 0);
        float far = Mth.clamp(-pr.z / depthRef, 0, 1), near = Mth.clamp(pr.z / depthRef, 0, 1);
        float k = (1f - 0.30f * far + 0.05f * near) * (1f - dim * 0.5f);
        int c = Mth.clamp(Math.round(255 * k), 0, 255);
        q.addVertex(mat, pr.x, pr.y, 0).setUv(u, v).setColor(c, c, Math.min(255, c + (dim > 0 ? 8 : 0)), Math.round(255 * alpha));
    }

    private static void shineV(BufferBuilder s, Matrix4f mat, Proj pr, float hw, float hh, float u, float v, float gx, float gy, float gStr, int foil,
                               float rx, float ry, float tilt, float alpha, double t) {
        pr.p(-hw + u * hw * 2, -hh + v * hh * 2, 0);
        float dx = (u - gx) * 1.1f, dy = v - gy;
        float glare = gStr * (float) Math.exp(-(dx * dx + dy * dy) / 0.07f);
        float r = 1, g = 1, b = 1, a = glare;
        if (foil > 0) {
            float hue = (float) (((u * 0.55f + v * 0.85f + ry * 0.013f - rx * 0.011f + t * 0.03) % 1.0 + 1.0) % 1.0);
            float band = 0.5f + 0.5f * (float) Math.sin((u * 1.4f + v * 2.1f) * Math.PI * 3.2f + (ry - rx) * 0.12f);
            float fa = (foil >= 2 ? 0.20f : 0.13f) * (0.35f + 0.65f * tilt) * band * band * alpha;
            int rgb = Mth.hsvToRgb(hue, 0.55f, 1f);
            float fr = ((rgb >> 16) & 0xFF) / 255f, fg = ((rgb >> 8) & 0xFF) / 255f, fb = (rgb & 0xFF) / 255f;
            float tot = a + fa;
            if (tot > 0.0001f) {
                r = (r * a + fr * fa) / tot;
                g = (g * a + fg * fa) / tot;
                b = (b * a + fb * fa) / tot;
            }
            a = tot;
        }
        s.addVertex(mat, pr.x, pr.y, 0).setColor(Math.round(r * 255), Math.round(g * 255), Math.round(b * 255), Mth.clamp(Math.round(a * 255), 0, 255));
    }

    /** Projected rounded rectangle, as triangles from the center. */
    private static void fan(BufferBuilder bb, Matrix4f mat, Proj pr, float hw, float hh, float r, float lz, int color) {
        final int seg = 4;
        float[] px = new float[seg * 4 + 4], py = new float[seg * 4 + 4];
        int n = 0;
        float[][] cs = {{hw - r, -hh + r, -90}, {hw - r, hh - r, 0}, {-hw + r, hh - r, 90}, {-hw + r, -hh + r, 180}};
        for (float[] c : cs) {
            for (int k = 0; k <= seg; k++) {
                double a = Math.toRadians(c[2] + 90.0 * k / seg);
                pr.p(c[0] + (float) Math.cos(a) * r, c[1] + (float) Math.sin(a) * r, lz);
                px[n] = pr.x;
                py[n] = pr.y;
                n++;
            }
        }
        pr.p(0, 0, lz);
        float ox = pr.x, oy = pr.y;
        for (int k = 0; k < n; k++) {
            int k2 = (k + 1) % n;
            bb.addVertex(mat, ox, oy, 0).setColor(color);
            bb.addVertex(mat, px[k], py[k], 0).setColor(color);
            bb.addVertex(mat, px[k2], py[k2], 0).setColor(color);
        }
    }
}

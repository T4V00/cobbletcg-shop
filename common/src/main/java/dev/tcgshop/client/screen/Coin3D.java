package dev.tcgshop.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.joml.Matrix4f;

import java.util.Arrays;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;

/**
 * Coin with real volume (reeded edge, embossed faces, lighting and metallic shine) drawn on a 2D screen with
 * perspective. Heads = Poké Ball emblem, tails = star. Drawn centered on the current origin.
 * The styles are designed to be extended with collectible coins.
 */
public final class Coin3D {
    private Coin3D() {}

    /** Number of distinct coins (see Cosmetics.COINS). */
    public static int count() { return dev.tcgshop.duel.Cosmetics.COINS.size(); }

    private static final float LX = -0.35f, LY = -0.55f, LZ = 0.76f; // light (y down, z toward the camera)

    private static final class Proj {
        final float cyA, syA, cxA, sxA, d;
        float x, y, z;

        Proj(float rx, float ry, float size) {
            double ax = Math.toRadians(rx), ay = Math.toRadians(ry);
            cxA = (float) Math.cos(ax);
            sxA = (float) Math.sin(ax);
            cyA = (float) Math.cos(ay);
            syA = (float) Math.sin(ay);
            d = size * 2.6f;
        }

        void p(float lx, float ly, float lz) {
            float x1 = lx * cyA + lz * syA, z1 = -lx * syA + lz * cyA;
            float y2 = ly * cxA - z1 * sxA, z2 = ly * sxA + z1 * cxA;
            float k = d / Math.max(d * 0.2f, d - z2);
            x = x1 * k;
            y = y2 * k;
            z = z2;
        }

        /** Rotates a vector (no perspective). */
        float[] rot(float vx, float vy, float vz) {
            float x1 = vx * cyA + vz * syA, z1 = -vx * syA + vz * cyA;
            return new float[]{x1, vy * cxA - z1 * sxA, vy * sxA + z1 * cxA};
        }
    }

    /**
     * @param r     radius in pixels
     * @param flip  rotation around the horizontal axis (degrees; 0 = heads, 180 = tails)
     * @param tilt  sideways tilt (degrees)
     */
    public static void draw(GuiGraphics ctx, float r, float flip, float tilt, int style, float alpha) {
        if (alpha <= 0.01f) return;
        dev.tcgshop.duel.Cosmetics.Coin st = dev.tcgshop.duel.Cosmetics.COINS.get(Math.floorMod(style, count()));
        double now = System.nanoTime() / 1e9;
        ctx.flush();
        Matrix4f mat = ctx.pose().last().pose();
        Proj pr = new Proj(flip, tilt, r * 2);
        float th = Math.max(1.5f, r * 0.16f);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);

        // edge: segments sorted back to front
        final int seg = 48;
        Integer[] order = new Integer[seg];
        float[] depth = new float[seg];
        for (int i = 0; i < seg; i++) {
            order[i] = i;
            double a = (i + 0.5) * Math.PI * 2 / seg;
            depth[i] = pr.rot((float) Math.cos(a), (float) Math.sin(a), 0)[2];
        }
        Arrays.sort(order, (p, q) -> Float.compare(depth[p], depth[q]));
        for (int i : order) {
            double a0 = i * Math.PI * 2 / seg, a1 = (i + 1) * Math.PI * 2 / seg, am = (a0 + a1) / 2;
            float[] n = pr.rot((float) Math.cos(am), (float) Math.sin(am), 0);
            float lit = 0.55f + 0.45f * Math.max(0, n[0] * LX + n[1] * LY + n[2] * LZ);
            float ridge = (i % 2 == 0) ? 1.06f : 0.9f;
            int rim = st.rim();
            if (st.fx() == dev.tcgshop.duel.Cosmetics.FX_RAINBOW)
                rim = 0xFF000000 | net.minecraft.util.Mth.hsvToRgb((float) ((i / (double) seg + now * 0.25) % 1.0), 0.55f, 1f);
            int c = Draw.withAlpha(shade(rim, lit * ridge), alpha);
            float c0 = (float) Math.cos(a0) * r, s0 = (float) Math.sin(a0) * r, c1 = (float) Math.cos(a1) * r, s1 = (float) Math.sin(a1) * r;
            quad(bb, mat, pr, c0, s0, th / 2, c1, s1, th / 2, c1, s1, -th / 2, c0, s0, -th / 2, c);
        }

        // visible face
        float[] fn = pr.rot(0, 0, 1);
        boolean front = fn[2] >= 0;
        float z = front ? th / 2 : -th / 2;
        float[] nn = front ? fn : new float[]{-fn[0], -fn[1], -fn[2]};
        float lit = 0.62f + 0.38f * Math.max(0, nn[0] * LX + nn[1] * LY + nn[2] * LZ);
        float mir = front ? 1 : -1; // seen from behind: mirror the drawing so it reads correctly
        float gloss = (float) Math.sin(Math.toRadians(flip) * 1.0 + Math.toRadians(tilt) * 2) * 1.1f;
        Face f = new Face(bb, mat, pr, z, mir, lit, gloss, alpha, r);
        f.disk(0, 0, r, st.faceDark(), st.faceDark(), 0, 360);
        f.disk(0, 0, r * 0.93f, st.face(), dev.tcgshop.client.binder.Ui.brighten(st.face(), 22), 0, 360);
        f.ring(0, 0, r * 0.84f, r * 0.80f, st.faceDark());
        if (front) emblem(f, st, r);
        else { // star
            f.star(0, r * 0.03f, r * 0.6f, r * 0.26f, shadeRgb(st.faceDark(), 0.8f));
            f.star(0, 0, r * 0.56f, r * 0.24f, dev.tcgshop.client.binder.Ui.brighten(st.face(), 35));
            f.star(0, 0, r * 0.36f, r * 0.15f, st.faceDark());
            for (int k = 0; k < 10; k++) {
                double a = k * Math.PI * 2 / 10 + Math.PI / 10;
                f.disk((float) Math.cos(a) * r * 0.72f, (float) Math.sin(a) * r * 0.72f, r * 0.035f, st.faceDark(), st.faceDark(), 0, 360);
            }
        }
        if (st.fx() != dev.tcgshop.duel.Cosmetics.FX_NONE) { // sparkles that appear and fade out
            for (int k = 0; k < 3; k++) {
                double cyc = now * 0.8 + k * 0.37, ph = cyc % 1.0, gen = Math.floor(cyc);
                float g = (float) Math.sin(ph * Math.PI);
                if (g < 0.05f) continue;
                double ang = k * 2.1 + gen * 1.7;
                float rr = r * (0.35f + 0.4f * (float) ((k * 0.31 + gen * 0.27) % 1.0));
                f.star4((float) Math.cos(ang) * rr, (float) Math.sin(ang) * rr, r * 0.16f * g, r * 0.035f * g, 0xFFFFFFFF);
            }
        }
        BufferUploader.drawWithShader(bb.buildOrThrow());
        RenderSystem.enableCull();
    }

    /** Heads emblem (with its embossed shadow). */
    private static void emblem(Face f, dev.tcgshop.duel.Cosmetics.Coin st, float r) {
        float er = r * 0.56f;
        int A = st.emA(), B = st.emB(), sh = shadeRgb(st.faceDark(), 0.8f), ink = 0xFF1C1C22;
        int Ab = dev.tcgshop.client.binder.Ui.brighten(A, 30);
        switch (st.emblem()) {
            case dev.tcgshop.duel.Cosmetics.E_BALL -> {
                f.disk(0, r * 0.03f, er * 1.08f, sh, sh, 0, 360);
                f.disk(0, 0, er, A, Ab, 180, 360);
                f.disk(0, 0, er, B, B, 0, 180);
                f.rect(-er, -er * 0.09f, er, er * 0.09f, ink);
                f.disk(0, 0, er * 0.36f, ink, ink, 0, 360);
                f.disk(0, 0, er * 0.24f, B, 0xFFFFFFFF, 0, 360);
            }
            case dev.tcgshop.duel.Cosmetics.E_STAR -> {
                f.star(0, r * 0.03f, er * 1.1f, er * 0.46f, sh);
                f.star(0, 0, er * 1.04f, er * 0.43f, A);
                f.star(0, 0, er * 0.55f, er * 0.23f, B);
            }
            case dev.tcgshop.duel.Cosmetics.E_BOLT -> {
                for (int pass = 0; pass < 2; pass++) {
                    float o = pass == 0 ? r * 0.04f : 0, k = er * (pass == 0 ? 1.08f : 1f);
                    int c = pass == 0 ? sh : A;
                    f.tri(0.28f * k, -1f * k + o, -0.42f * k, 0.16f * k + o, 0.12f * k, 0.16f * k + o, c);
                    f.tri(-0.12f * k, -0.16f * k + o, 0.42f * k, -0.16f * k + o, -0.28f * k, 1f * k + o, c);
                }
                f.tri(0.2f * er, -0.7f * er, -0.12f * er, 0.02f * er, 0.06f * er, 0.02f * er, B);
            }
            case dev.tcgshop.duel.Cosmetics.E_FLAME -> {
                flame(f, 0, r * 0.04f, er * 1.08f, sh);
                flame(f, 0, 0, er, A);
                flame(f, 0, er * 0.2f, er * 0.55f, B);
            }
            case dev.tcgshop.duel.Cosmetics.E_DROP -> {
                drop(f, 0, r * 0.04f, er * 1.06f, sh);
                drop(f, 0, 0, er, A);
                f.disk(-er * 0.22f, er * 0.18f, er * 0.16f, B, 0xFFFFFFFF, 0, 360);
            }
            case dev.tcgshop.duel.Cosmetics.E_LEAF -> {
                leaf(f, 0, r * 0.04f, er * 1.06f, sh);
                leaf(f, 0, 0, er, A);
                f.bar(0, 0, er * 1.5f, er * 0.07f, -45, B);
                for (int k = -1; k <= 1; k++) {
                    float t = k * 0.3f * er;
                    f.bar(t * 0.7071f + er * 0.1f, -t * 0.7071f + er * 0.1f, er * 0.38f, er * 0.045f, 45, B);
                    f.bar(t * 0.7071f - er * 0.1f, -t * 0.7071f - er * 0.1f, er * 0.38f, er * 0.045f, 45, B);
                }
            }
            case dev.tcgshop.duel.Cosmetics.E_MOON -> {
                f.disk(0, r * 0.03f, er * 1.05f, sh, sh, 0, 360);
                f.disk(0, 0, er, A, Ab, 0, 360);
                f.disk(er * 0.38f, -er * 0.22f, er * 0.82f, st.face(), st.face(), 0, 360);
                f.star4(er * 0.55f, er * 0.45f, er * 0.2f, er * 0.05f, B);
                f.star4(er * 0.85f, -er * 0.6f, er * 0.14f, er * 0.04f, B);
            }
            case dev.tcgshop.duel.Cosmetics.E_SUN -> {
                for (int k = 0; k < 12; k++) {
                    double a0 = k * Math.PI / 6, a1 = a0 + 0.18, a2 = a0 - 0.18;
                    float ro = er * 1.05f, ri = er * 0.6f;
                    f.tri((float) Math.cos(a0) * ro, (float) Math.sin(a0) * ro, (float) Math.cos(a1) * ri, (float) Math.sin(a1) * ri,
                            (float) Math.cos(a2) * ri, (float) Math.sin(a2) * ri, k % 2 == 0 ? A : B);
                }
                f.disk(0, 0, er * 0.6f, A, Ab, 0, 360);
                f.disk(0, 0, er * 0.38f, B, 0xFFFFFFFF, 0, 360);
            }
            case dev.tcgshop.duel.Cosmetics.E_CROWN -> {
                for (int pass = 0; pass < 2; pass++) {
                    float o = pass == 0 ? r * 0.04f : 0;
                    int c = pass == 0 ? sh : A;
                    f.rect(-er * 0.8f, er * 0.15f + o, er * 0.8f, er * 0.6f + o, c);
                    f.tri(-er * 0.8f, er * 0.2f + o, -er * 0.4f, er * 0.2f + o, -er * 0.85f, -er * 0.55f + o, c);
                    f.tri(-er * 0.4f, er * 0.2f + o, er * 0.4f, er * 0.2f + o, 0, -er * 0.8f + o, c);
                    f.tri(er * 0.4f, er * 0.2f + o, er * 0.8f, er * 0.2f + o, er * 0.85f, -er * 0.55f + o, c);
                }
                f.rect(-er * 0.8f, er * 0.32f, er * 0.8f, er * 0.44f, B);
                f.disk(-er * 0.85f, -er * 0.6f, er * 0.12f, B, 0xFFFFFFFF, 0, 360);
                f.disk(0, -er * 0.86f, er * 0.13f, B, 0xFFFFFFFF, 0, 360);
                f.disk(er * 0.85f, -er * 0.6f, er * 0.12f, B, 0xFFFFFFFF, 0, 360);
            }
            case dev.tcgshop.duel.Cosmetics.E_GEM -> {
                float[] out = {-0.75f, -0.3f, -0.38f, -0.72f, 0.38f, -0.72f, 0.75f, -0.3f, 0f, 0.88f};
                f.poly(out, er * 1.06f, 0, r * 0.04f, sh);
                f.poly(out, er, 0, 0, A);
                f.tri(-0.38f * er, -0.72f * er, 0.38f * er, -0.72f * er, 0, -0.3f * er, Ab);
                f.tri(-0.75f * er, -0.3f * er, 0.75f * er, -0.3f * er, 0, 0.88f * er, dev.tcgshop.client.binder.Ui.darken(A, 25));
                f.tri(-0.2f * er, -0.3f * er, 0.2f * er, -0.3f * er, 0, 0.6f * er, B);
            }
            case dev.tcgshop.duel.Cosmetics.E_SNOW -> {
                for (int pass = 0; pass < 2; pass++) {
                    float o = pass == 0 ? r * 0.04f : 0;
                    int c = pass == 0 ? sh : A;
                    for (int k = 0; k < 3; k++) f.bar(0, o, er * 2f, er * 0.13f, k * 60, c);
                    for (int k = 0; k < 6; k++) {
                        double a = Math.toRadians(k * 60);
                        float bx = (float) Math.cos(a) * er * 0.62f, by = (float) Math.sin(a) * er * 0.62f + o;
                        f.bar(bx, by, er * 0.5f, er * 0.09f, k * 60 + 60, c);
                        f.bar(bx, by, er * 0.5f, er * 0.09f, k * 60 - 60, c);
                    }
                }
                f.disk(0, 0, er * 0.2f, B, 0xFFFFFFFF, 0, 360);
            }
            case dev.tcgshop.duel.Cosmetics.E_HEART -> {
                heart(f, 0, r * 0.04f, er * 1.06f, sh);
                heart(f, 0, 0, er, A);
                f.disk(-er * 0.38f, -er * 0.3f, er * 0.16f, B, 0xFFFFFFFF, 0, 360);
            }
            case dev.tcgshop.duel.Cosmetics.E_SPIRAL -> {
                f.disk(0, r * 0.03f, er * 1.06f, sh, sh, 0, 360);
                f.disk(0, 0, er, A, Ab, 0, 360);
                for (int k = 0; k < 70; k++) {
                    double th = k * 0.17;
                    float rr = er * 0.08f + er * 0.75f * k / 70f;
                    f.disk((float) Math.cos(th) * rr, (float) Math.sin(th) * rr, er * (0.05f + 0.06f * k / 70f), B, B, 0, 360);
                }
            }
            default -> { // shield
                float[] out = {-0.68f, -0.78f, 0.68f, -0.78f, 0.68f, 0.02f, 0f, 0.9f, -0.68f, 0.02f};
                f.poly(out, er * 1.06f, 0, r * 0.04f, sh);
                f.poly(out, er, 0, 0, A);
                f.poly(out, er * 0.72f, 0, -er * 0.04f, dev.tcgshop.client.binder.Ui.brighten(A, 18));
                f.rect(-er * 0.1f, -er * 0.6f, er * 0.1f, er * 0.62f, B);
                f.rect(-er * 0.5f, -er * 0.22f, er * 0.5f, -er * 0.04f, B);
            }
        }
    }

    private static void flame(Face f, float cx, float cy, float s, int c) {
        f.disk(cx, cy + s * 0.32f, s * 0.58f, c, c, 0, 360);
        f.tri(cx - s * 0.56f, cy + s * 0.22f, cx + s * 0.56f, cy + s * 0.22f, cx + s * 0.12f, cy - s * 1.0f, c);
        f.tri(cx - s * 0.56f, cy + s * 0.22f, cx - s * 0.1f, cy - s * 0.2f, cx - s * 0.55f, cy - s * 0.55f, c);
        f.tri(cx + s * 0.56f, cy + s * 0.22f, cx + s * 0.25f, cy - s * 0.2f, cx + s * 0.62f, cy - s * 0.45f, c);
    }

    private static void drop(Face f, float cx, float cy, float s, int c) {
        f.disk(cx, cy + s * 0.3f, s * 0.6f, c, c, 0, 360);
        f.tri(cx - s * 0.57f, cy + s * 0.12f, cx + s * 0.57f, cy + s * 0.12f, cx, cy - s * 0.98f, c);
    }

    private static void leaf(Face f, float cx, float cy, float s, int c) {
        float[] pts = new float[24];
        for (int k = 0; k < 12; k++) {
            double t = k * Math.PI * 2 / 12;
            float lx = (float) Math.sin(t) * 0.42f, ly = (float) Math.cos(t);
            pts[k * 2] = (lx - ly) * 0.7071f;
            pts[k * 2 + 1] = (lx + ly) * 0.7071f;
        }
        f.poly(pts, s, cx, cy, c);
    }

    private static void heart(Face f, float cx, float cy, float s, int c) {
        f.disk(cx - s * 0.36f, cy - s * 0.22f, s * 0.42f, c, c, 0, 360);
        f.disk(cx + s * 0.36f, cy - s * 0.22f, s * 0.42f, c, c, 0, 360);
        f.tri(cx - s * 0.76f, cy - s * 0.08f, cx + s * 0.76f, cy - s * 0.08f, cx, cy + s * 0.88f, c);
    }

    /** Soft shadow on the ground (ellipse), centered on the current origin. */
    public static void shadow(GuiGraphics ctx, float r, float alpha) {
        if (alpha <= 0.01f) return;
        for (int k = 4; k >= 1; k--) {
            float rr = r * (0.6f + k * 0.12f);
            int a = Math.round(255 * alpha * 0.10f);
            for (int y = -Math.round(rr * 0.28f); y <= Math.round(rr * 0.28f); y++) {
                float fy = y / (rr * 0.28f);
                int half = Math.round(rr * (float) Math.sqrt(Math.max(0, 1 - fy * fy)));
                ctx.fill(-half, y, half, y + 1, a << 24);
            }
        }
    }

    // ------------------------------------------------------------------ heads

    private static final class Face {
        final BufferBuilder bb;
        final Matrix4f mat;
        final Proj pr;
        final float z, mir, lit, gloss, alpha, r;

        Face(BufferBuilder bb, Matrix4f mat, Proj pr, float z, float mir, float lit, float gloss, float alpha, float r) {
            this.bb = bb;
            this.mat = mat;
            this.pr = pr;
            this.z = z;
            this.mir = mir;
            this.lit = lit;
            this.gloss = gloss;
            this.alpha = alpha;
            this.r = r;
        }

        void v(float x, float y, int color) {
            float u = x / r, w = y / r;
            float d = (u * 0.7f + w * 0.7f) - gloss;
            float g = 0.5f * (float) Math.exp(-d * d / 0.09f);
            int c = shadeRgb(color, lit);
            int cr = (c >> 16) & 0xFF, cg = (c >> 8) & 0xFF, cb = c & 0xFF;
            cr += Math.round((255 - cr) * g);
            cg += Math.round((255 - cg) * g);
            cb += Math.round((255 - cb) * g);
            pr.p(x * mir, y, z);
            bb.addVertex(mat, pr.x, pr.y, 0).setColor(cr, cg, cb, Math.round(((color >>> 24) & 0xFF) * alpha));
        }

        /** Disc sector from a0 to a1 degrees (0 = right, 90 = down), with a gradient from the edge (c0) to the center (c1). */
        void disk(float cx, float cy, float rad, int c0, int c1, float a0, float a1) {
            int n = Math.max(6, Math.round((a1 - a0) / 7.5f));
            for (int k = 0; k < n; k++) {
                double p = Math.toRadians(a0 + (a1 - a0) * k / n), q = Math.toRadians(a0 + (a1 - a0) * (k + 1) / n);
                v(cx, cy, c1);
                v(cx + (float) Math.cos(p) * rad, cy + (float) Math.sin(p) * rad, c0);
                v(cx + (float) Math.cos(q) * rad, cy + (float) Math.sin(q) * rad, c0);
            }
        }

        void ring(float cx, float cy, float r0, float r1, int c) {
            int n = 48;
            for (int k = 0; k < n; k++) {
                double p = k * Math.PI * 2 / n, q = (k + 1) * Math.PI * 2 / n;
                float px0 = cx + (float) Math.cos(p) * r0, py0 = cy + (float) Math.sin(p) * r0, qx0 = cx + (float) Math.cos(q) * r0, qy0 = cy + (float) Math.sin(q) * r0;
                float px1 = cx + (float) Math.cos(p) * r1, py1 = cy + (float) Math.sin(p) * r1, qx1 = cx + (float) Math.cos(q) * r1, qy1 = cy + (float) Math.sin(q) * r1;
                v(px0, py0, c); v(qx0, qy0, c); v(qx1, qy1, c);
                v(px0, py0, c); v(qx1, qy1, c); v(px1, py1, c);
            }
        }

        void rect(float x0, float y0, float x1, float y1, int c) {
            v(x0, y0, c); v(x1, y0, c); v(x1, y1, c);
            v(x0, y0, c); v(x1, y1, c); v(x0, y1, c);
        }

        void tri(float x0, float y0, float x1, float y1, float x2, float y2, int c) {
            v(x0, y0, c); v(x1, y1, c); v(x2, y2, c);
        }

        /** Convex polygon (points in units, scaled by s and offset to cx, cy). */
        void poly(float[] pts, float s, float cx, float cy, int c) {
            float mx = 0, my = 0;
            int n = pts.length / 2;
            for (int k = 0; k < n; k++) { mx += pts[k * 2]; my += pts[k * 2 + 1]; }
            mx = mx / n * s + cx;
            my = my / n * s + cy;
            for (int k = 0; k < n; k++) {
                int q = (k + 1) % n;
                tri(mx, my, cx + pts[k * 2] * s, cy + pts[k * 2 + 1] * s, cx + pts[q * 2] * s, cy + pts[q * 2 + 1] * s, c);
            }
        }

        /** Rotated bar (deg) centered on (cx, cy). */
        void bar(float cx, float cy, float len, float wid, float deg, int c) {
            double a = Math.toRadians(deg);
            float ux = (float) Math.cos(a) * len / 2, uy = (float) Math.sin(a) * len / 2, vx = (float) -Math.sin(a) * wid / 2, vy = (float) Math.cos(a) * wid / 2;
            tri(cx - ux - vx, cy - uy - vy, cx + ux - vx, cy + uy - vy, cx + ux + vx, cy + uy + vy, c);
            tri(cx - ux - vx, cy - uy - vy, cx + ux + vx, cy + uy + vy, cx - ux + vx, cy - uy + vy, c);
        }

        /** 4-pointed sparkle. */
        void star4(float cx, float cy, float ro, float ri, int c) {
            for (int k = 0; k < 8; k++) {
                double p = k * Math.PI / 4, q = (k + 1) * Math.PI / 4;
                float rp = k % 2 == 0 ? ro : ri, rq = k % 2 == 0 ? ri : ro;
                tri(cx, cy, cx + (float) Math.cos(p) * rp, cy + (float) Math.sin(p) * rp, cx + (float) Math.cos(q) * rq, cy + (float) Math.sin(q) * rq, c);
            }
        }

        void star(float cx, float cy, float ro, float ri, int c) {
            for (int k = 0; k < 10; k++) {
                double p = -Math.PI / 2 + k * Math.PI / 5, q = -Math.PI / 2 + (k + 1) * Math.PI / 5;
                float rp = k % 2 == 0 ? ro : ri, rq = k % 2 == 0 ? ri : ro;
                v(cx, cy, c);
                v(cx + (float) Math.cos(p) * rp, cy + (float) Math.sin(p) * rp, c);
                v(cx + (float) Math.cos(q) * rq, cy + (float) Math.sin(q) * rq, c);
            }
        }
    }

    private static void quad(BufferBuilder bb, Matrix4f mat, Proj pr, float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3, int c) {
        float[] p = new float[8];
        pr.p(x0, y0, z0); p[0] = pr.x; p[1] = pr.y;
        pr.p(x1, y1, z1); p[2] = pr.x; p[3] = pr.y;
        pr.p(x2, y2, z2); p[4] = pr.x; p[5] = pr.y;
        pr.p(x3, y3, z3); p[6] = pr.x; p[7] = pr.y;
        bb.addVertex(mat, p[0], p[1], 0).setColor(c);
        bb.addVertex(mat, p[2], p[3], 0).setColor(c);
        bb.addVertex(mat, p[4], p[5], 0).setColor(c);
        bb.addVertex(mat, p[0], p[1], 0).setColor(c);
        bb.addVertex(mat, p[4], p[5], 0).setColor(c);
        bb.addVertex(mat, p[6], p[7], 0).setColor(c);
    }

    private static int shade(int argb, float k) {
        return (argb & 0xFF000000) | shadeRgb(argb, k) & 0xFFFFFF;
    }

    private static int shadeRgb(int argb, float k) {
        int r = Math.min(255, Math.round(((argb >> 16) & 0xFF) * k)), g = Math.min(255, Math.round(((argb >> 8) & 0xFF) * k)),
                b = Math.min(255, Math.round((argb & 0xFF) * k));
        return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
    }
}

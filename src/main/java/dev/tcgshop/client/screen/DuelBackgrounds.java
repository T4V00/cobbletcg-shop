package dev.tcgshop.client.screen;

import dev.tcgshop.duel.Cosmetics;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * High-resolution board backgrounds generated in code (seamless 512 px tile, no files or downloads):
 * Poké Balls, meadows, waves, flames, lightning, crystals, spirals, will-o'-wisps, sparkles, hexagons, canyon,
 * scales, starry night, Master, Champion, cosmos, toxic bubbles, dojo, sakura and prism.
 * They are generated the first time they are seen (at most one per frame) and stay in memory.
 */
public final class DuelBackgrounds {
    private DuelBackgrounds() {}

    public static final int N = 512;
    private static final Map<String, Identifier> READY = new HashMap<>();
    private static final Map<String, java.util.concurrent.CompletableFuture<Canvas>> PENDING = new HashMap<>();
    /** A single low-priority thread paints the backgrounds: the game doesn't freeze when opening the Backgrounds tab. */
    private static final java.util.concurrent.ExecutorService GEN = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "tcgshop-board-gen");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });
    private static long lastUpload = -1;

    /** Ready texture for the background (null if it's the classic one or still being generated in the background). */
    @Nullable
    public static Identifier texture(String id) {
        if (id == null || id.equals("classic") || Cosmetics.board(id) == null) return null;
        Identifier ready = READY.get(id);
        if (ready != null) return ready;
        java.util.concurrent.CompletableFuture<Canvas> job = PENDING.computeIfAbsent(id, k -> java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            Canvas c = new Canvas(N);
            try {
                paint(k, c);
            } catch (Exception ex) {
                dev.tcgshop.TcgShop.LOGGER.warn("Could not generate background {}", k, ex);
            }
            return c;
        }, GEN));
        if (!job.isDone()) return null;
        long nowMs = System.currentTimeMillis();
        if (nowMs - lastUpload < 16) return null; // at most one GPU upload per frame
        lastUpload = nowMs;
        PENDING.remove(id);
        Canvas c = job.getNow(null);
        if (c == null) return null;
        NativeImage img = new NativeImage(NativeImage.Format.RGBA, N, N, false);
        for (int y = 0; y < N; y++)
            for (int x = 0; x < N; x++) {
                int i = y * N + x;
                int r = clamp255(c.r[i]), g = clamp255(c.g[i]), b = clamp255(c.b[i]);
                img.setColor(x, y, 0xFF000000 | (b << 16) | (g << 8) | r); // ABGR
            }
        NativeImageBackedTexture tex = new NativeImageBackedTexture(img);
        Identifier tid = Identifier.of("tcgshop", "dynamic/board_" + id);
        MinecraftClient.getInstance().getTextureManager().registerTexture(tid, tex);
        tex.setFilter(true, false);
        READY.put(id, tid);
        return tid;
    }

    /** Draws the tiled background covering (x, y, w, h), with slow scrolling. tile = on-screen size of the tile. */
    public static boolean draw(DrawContext ctx, String id, int x, int y, int w, int h, int tile, double t, float alpha) {
        Identifier tex = texture(id);
        if (tex == null) return false;
        float u = (float) ((t * 4) % tile), v = (float) ((t * 2.5) % tile);
        ctx.draw(); // so what was drawn before (backgrounds, gradients) doesn't end up on top of the texture
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        ctx.setShaderColor(1, 1, 1, alpha);
        ctx.drawTexture(tex, x, y, u, v, w, h, tile, tile);
        ctx.setShaderColor(1, 1, 1, 1);
        return true;
    }

    private static int clamp255(float v) { return Math.max(0, Math.min(255, Math.round(v))); }

    // ------------------------------------------------------------------ canvas

    /** Floating-point canvas with antialiased shapes that wrap around the edges (seamless tile). */
    static final class Canvas {
        final int n;
        final float[] r, g, b;

        Canvas(int n) {
            this.n = n;
            r = new float[n * n];
            g = new float[n * n];
            b = new float[n * n];
        }

        void blend(int px, int py, int rgb, float a) {
            if (a <= 0) return;
            if (a > 1) a = 1;
            int i = Math.floorMod(py, n) * n + Math.floorMod(px, n);
            r[i] += (((rgb >> 16) & 0xFF) - r[i]) * a;
            g[i] += (((rgb >> 8) & 0xFF) - g[i]) * a;
            b[i] += ((rgb & 0xFF) - b[i]) * a;
        }

        /** Background with two colors that vary periodically (soft diagonal). */
        void base(int c0, int c1, float waves) {
            for (int y = 0; y < n; y++)
                for (int x = 0; x < n; x++) {
                    float k = 0.5f + 0.22f * (float) Math.cos((x + y) * Math.PI * 2 / n) + 0.18f * (float) Math.cos((x - y) * Math.PI * 4 / n)
                            + 0.1f * (float) Math.sin(x * Math.PI * 2 * waves / n) * (float) Math.cos(y * Math.PI * 2 * waves / n);
                    int c = lerp(c0, c1, MathHelper.clamp(k, 0, 1));
                    int i = y * n + x;
                    r[i] = (c >> 16) & 0xFF;
                    g[i] = (c >> 8) & 0xFF;
                    b[i] = c & 0xFF;
                }
        }

        interface Sdf { float d(float x, float y); }

        /** Paints a shape given by its signed distance, within its box (x0..x1, y0..y1). */
        void shape(float x0, float y0, float x1, float y1, int rgb, float a, Sdf f) {
            for (int py = (int) Math.floor(y0) - 1; py <= (int) Math.ceil(y1) + 1; py++)
                for (int px = (int) Math.floor(x0) - 1; px <= (int) Math.ceil(x1) + 1; px++) {
                    float d = f.d(px + 0.5f, py + 0.5f);
                    float cov = MathHelper.clamp(0.5f - d, 0, 1);
                    if (cov > 0) blend(px, py, rgb, a * cov);
                }
        }

        void circle(float cx, float cy, float rad, int rgb, float a) {
            shape(cx - rad, cy - rad, cx + rad, cy + rad, rgb, a, (x, y) -> (float) Math.hypot(x - cx, y - cy) - rad);
        }

        void ring(float cx, float cy, float rad, float w, int rgb, float a) {
            float o = rad + w;
            shape(cx - o, cy - o, cx + o, cy + o, rgb, a, (x, y) -> Math.abs((float) Math.hypot(x - cx, y - cy) - rad) - w / 2);
        }

        /** Disc with a soft radial glow. */
        void glow(float cx, float cy, float rad, int rgb, float a) {
            shape(cx - rad, cy - rad, cx + rad, cy + rad, rgb, 1, (x, y) -> {
                float k = 1 - (float) Math.hypot(x - cx, y - cy) / rad;
                return k <= 0 ? 1 : 0.5f - k * k * a; // returns a "distance" that yields coverage k² · a
            });
        }

        void line(float ax, float ay, float bx, float by, float w, int rgb, float a) {
            float pad = w + 1;
            shape(Math.min(ax, bx) - pad, Math.min(ay, by) - pad, Math.max(ax, bx) + pad, Math.max(ay, by) + pad, rgb, a, (x, y) -> segDist(x, y, ax, ay, bx, by) - w / 2);
        }

        void ellipse(float cx, float cy, float ra, float rb, float deg, int rgb, float a) {
            double t = Math.toRadians(deg);
            float c = (float) Math.cos(t), s = (float) Math.sin(t), m = Math.max(ra, rb);
            shape(cx - m, cy - m, cx + m, cy + m, rgb, a, (x, y) -> {
                float lx = (x - cx) * c + (y - cy) * s, ly = -(x - cx) * s + (y - cy) * c;
                float k = (float) Math.hypot(lx / ra, ly / rb);
                return (k - 1) * Math.min(ra, rb);
            });
        }

        /** Polygon (any shape, even-odd rule) antialiased by distance to the edges. */
        void poly(float[] pts, int rgb, float a) {
            float x0 = Float.MAX_VALUE, y0 = Float.MAX_VALUE, x1 = -Float.MAX_VALUE, y1 = -Float.MAX_VALUE;
            for (int k = 0; k < pts.length; k += 2) {
                x0 = Math.min(x0, pts[k]); x1 = Math.max(x1, pts[k]);
                y0 = Math.min(y0, pts[k + 1]); y1 = Math.max(y1, pts[k + 1]);
            }
            int n2 = pts.length / 2;
            shape(x0, y0, x1, y1, rgb, a, (x, y) -> {
                boolean in = false;
                float md = Float.MAX_VALUE;
                for (int i = 0, j = n2 - 1; i < n2; j = i++) {
                    float xi = pts[i * 2], yi = pts[i * 2 + 1], xj = pts[j * 2], yj = pts[j * 2 + 1];
                    if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) in = !in;
                    md = Math.min(md, segDist(x, y, xi, yi, xj, yj));
                }
                return in ? -md : md;
            });
        }

        /** Regular polygon / `k`-pointed star centered at (cx, cy). */
        void star(float cx, float cy, float ro, float ri, int k, float deg, int rgb, float a) {
            float[] p = new float[k * 4];
            for (int i = 0; i < k * 2; i++) {
                double ang = Math.toRadians(deg) - Math.PI / 2 + i * Math.PI / k;
                float rr = i % 2 == 0 ? ro : ri;
                p[i * 2] = cx + (float) Math.cos(ang) * rr;
                p[i * 2 + 1] = cy + (float) Math.sin(ang) * rr;
            }
            poly(p, rgb, a);
        }

        void ngon(float cx, float cy, float rad, int k, float deg, int rgb, float a) {
            float[] p = new float[k * 2];
            for (int i = 0; i < k; i++) {
                double ang = Math.toRadians(deg) + i * Math.PI * 2 / k;
                p[i * 2] = cx + (float) Math.cos(ang) * rad;
                p[i * 2 + 1] = cy + (float) Math.sin(ang) * rad;
            }
            poly(p, rgb, a);
        }

        /** Periodic value noise (fbm) in [0, 1]. */
        float noise(float x, float y, int cells, long seed) {
            float sum = 0, amp = 0.5f, norm = 0;
            for (int o = 0; o < 4; o++) {
                int c = cells << o;
                float fx = x / n * c, fy = y / n * c;
                int ix = (int) Math.floor(fx), iy = (int) Math.floor(fy);
                float tx = fx - ix, ty = fy - iy;
                tx = tx * tx * (3 - 2 * tx);
                ty = ty * ty * (3 - 2 * ty);
                float v00 = hash(ix, iy, c, seed + o), v10 = hash(ix + 1, iy, c, seed + o), v01 = hash(ix, iy + 1, c, seed + o), v11 = hash(ix + 1, iy + 1, c, seed + o);
                sum += amp * ((v00 * (1 - tx) + v10 * tx) * (1 - ty) + (v01 * (1 - tx) + v11 * tx) * ty);
                norm += amp;
                amp *= 0.5f;
            }
            return sum / norm;
        }

        private static float hash(int x, int y, int c, long seed) {
            long h = Math.floorMod(x, c) * 374761393L + Math.floorMod(y, c) * 668265263L + seed * 1442695040888963407L;
            h = (h ^ (h >>> 13)) * 1274126177L;
            h ^= h >>> 16;
            return (h & 0xFFFF) / 65535f;
        }
    }

    static float segDist(float x, float y, float ax, float ay, float bx, float by) {
        float dx = bx - ax, dy = by - ay, l2 = dx * dx + dy * dy;
        float t = l2 == 0 ? 0 : MathHelper.clamp(((x - ax) * dx + (y - ay) * dy) / l2, 0, 1);
        return (float) Math.hypot(x - (ax + t * dx), y - (ay + t * dy));
    }

    static int lerp(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return (Math.round(ar + (br - ar) * t) << 16) | (Math.round(ag + (bg - ag) * t) << 8) | Math.round(ab + (bb - ab) * t);
    }

    // ------------------------------------------------------------------ designs

    private static void paint(String id, Canvas c) {
        Random rnd = new Random(id.hashCode() * 7919L);
        int n = c.n;
        switch (id) {
            case "pokeball" -> {
                c.base(0xF4F1EC, 0xFFE3E3, 2);
                for (int row = 0; row < 4; row++)
                    for (int col = 0; col < 4; col++) {
                        float cx = col * 128 + (row % 2) * 64 + 64, cy = row * 128 + 64, rad = 38;
                        ball(c, cx, cy, rad, (row + col) % 3 == 0 ? 0xE33B3B : (row + col) % 3 == 1 ? 0x3A6FD8 : 0xE8B730, 0.3f, ((row * 4 + col) % 5 - 2) * 12f);
                    }
                for (int k = 0; k < 40; k++) c.star(rnd.nextFloat() * n, rnd.nextFloat() * n, 5, 2, 4, 45, 0xFFFFFF, 0.6f);
            }
            case "meadow" -> {
                c.base(0x7CC86E, 0x4E9E52, 3);
                for (int k = 0; k < 140; k++) {
                    float x = rnd.nextFloat() * n, y = rnd.nextFloat() * n, s = 8 + rnd.nextFloat() * 14;
                    int col = new int[]{0x3E8A3E, 0x5FB35A, 0x9AD47C, 0x2F6E34}[k % 4];
                    c.ellipse(x, y, s, s * 0.42f, rnd.nextFloat() * 180, col, 0.55f);
                }
                for (int k = 0; k < 26; k++) flower(c, rnd.nextFloat() * n, rnd.nextFloat() * n, 6 + rnd.nextFloat() * 4,
                        new int[]{0xFFFFFF, 0xFFE27A, 0xFFB0D4}[k % 3], 0.85f);
            }
            case "ocean" -> {
                c.base(0x3A8FD8, 0x1C5A9E, 2);
                for (int band = 0; band < 8; band++) {
                    float y0 = band * 64;
                    for (int x = 0; x < n; x += 2) {
                        float y = y0 + 14 * (float) Math.sin((x + band * 40) * Math.PI * 2 / 128);
                        c.circle(x, y, 5, 0xBDE3FF, 0.18f);
                        c.circle(x, y + 3, 4, 0x0F3F73, 0.10f);
                    }
                }
                for (int k = 0; k < 60; k++) {
                    float x = rnd.nextFloat() * n, y = rnd.nextFloat() * n, s = 2 + rnd.nextFloat() * 7;
                    c.ring(x, y, s, 1.4f, 0xE6F6FF, 0.5f);
                    c.circle(x - s * 0.35f, y - s * 0.35f, s * 0.25f, 0xFFFFFF, 0.6f);
                }
            }
            case "blaze" -> {
                c.base(0xF2873A, 0xB8301A, 2);
                for (int k = 0; k < 70; k++) {
                    float x = rnd.nextFloat() * n, y = rnd.nextFloat() * n, s = 10 + rnd.nextFloat() * 18;
                    flame(c, x, y, s, k % 2 == 0 ? 0xFFD34D : 0xFF6B2A, 0.45f);
                    flame(c, x, y + s * 0.25f, s * 0.5f, 0xFFF4C2, 0.35f);
                }
                for (int k = 0; k < 120; k++) c.circle(rnd.nextFloat() * n, rnd.nextFloat() * n, 1 + rnd.nextFloat() * 2, 0xFFE27A, 0.8f);
            }
            case "storm" -> {
                c.base(0xFFD84A, 0xE0A800, 2);
                for (int k = 0; k < 34; k++) {
                    float x = rnd.nextFloat() * n, y = rnd.nextFloat() * n, s = 16 + rnd.nextFloat() * 20;
                    bolt(c, x, y, s, 0x2B2B33, 0.22f);
                    bolt(c, x - 2, y - 2, s, 0xFFFFFF, 0.75f);
                }
                for (int k = 0; k < 50; k++) c.star(rnd.nextFloat() * n, rnd.nextFloat() * n, 5, 1.5f, 4, 0, 0xFFFFFF, 0.7f);
            }
            case "glacier" -> {
                c.base(0xDDF4FF, 0x9CCDE8, 2);
                for (int k = 0; k < 30; k++) snow(c, rnd.nextFloat() * n, rnd.nextFloat() * n, 10 + rnd.nextFloat() * 18, 0xFFFFFF, 0.75f);
                for (int k = 0; k < 40; k++) {
                    float x = rnd.nextFloat() * n, y = rnd.nextFloat() * n, s = 8 + rnd.nextFloat() * 14;
                    c.poly(new float[]{x, y - s, x + s * 0.4f, y, x, y + s, x - s * 0.4f, y}, 0x6FB6E6, 0.3f);
                }
            }
            case "psyche" -> {
                c.base(0xE88AD8, 0x9C4FC8, 2);
                for (int k = 0; k < 18; k++) {
                    float x = rnd.nextFloat() * n, y = rnd.nextFloat() * n, s = 20 + rnd.nextFloat() * 30;
                    for (int j = 0; j < 90; j++) {
                        double th = j * 0.2;
                        float rr = s * j / 90f;
                        c.circle(x + (float) Math.cos(th) * rr, y + (float) Math.sin(th) * rr, 1.6f + j / 50f, 0xFFE0FA, 0.5f);
                    }
                }
                for (int k = 0; k < 30; k++) c.ring(rnd.nextFloat() * n, rnd.nextFloat() * n, 6 + rnd.nextFloat() * 12, 2, 0x5A1A6E, 0.35f);
            }
            case "haunt" -> {
                c.base(0x3A2858, 0x1A1030, 2);
                for (int k = 0; k < 36; k++) {
                    float x = rnd.nextFloat() * n, y = rnd.nextFloat() * n, s = 7 + rnd.nextFloat() * 10;
                    c.glow(x, y, s * 3, 0xB090FF, 0.5f);
                    for (int j = 0; j < 8; j++) c.circle(x + j * s * 0.25f, y + j * s * 0.35f, s * (1 - j / 9f) * 0.7f, 0xD8C8FF, 0.3f);
                    c.circle(x, y, s * 0.8f, 0xEDE3FF, 0.85f);
                }
            }
            case "pixie" -> {
                c.base(0xFFD6E8, 0xF5A8CC, 2);
                for (int k = 0; k < 70; k++) {
                    float x = rnd.nextFloat() * n, y = rnd.nextFloat() * n, s = 4 + rnd.nextFloat() * 9;
                    if (k % 3 == 0) heart(c, x, y, s, 0xFF7FB5, 0.55f);
                    else c.star(x, y, s, s * 0.3f, 4, k * 13, 0xFFFFFF, 0.85f);
                }
            }
            case "forge" -> {
                c.base(0xB7C1CC, 0x7D8894, 1);
                float hr = 35, hw = 64, rh = 51.2f;
                for (int row = 0; row < 10; row++)
                    for (int col = 0; col < 8; col++) {
                        float cx = col * hw + (row % 2) * hw / 2, cy = row * rh;
                        c.ngon(cx, cy, hr - 2, 6, 30, 0xDCE3EA, 0.35f);
                        c.ngon(cx, cy, hr - 7, 6, 30, 0x8E99A8, 0.3f);
                        c.circle(cx, cy, 2.5f, 0x4A525C, 0.6f);
                    }
            }
            case "canyon" -> {
                for (int y = 0; y < n; y++)
                    for (int x = 0; x < n; x++) {
                        float nv = c.noise(x, y, 4, 11);
                        float band = (float) Math.sin((y + nv * 90) * Math.PI * 2 / 64) * 0.5f + 0.5f;
                        int col = lerp(lerp(0xC0763A, 0xE8B07A, band), 0x8A4A22, nv * 0.6f);
                        int i = y * n + x;
                        c.r[i] = (col >> 16) & 0xFF; c.g[i] = (col >> 8) & 0xFF; c.b[i] = col & 0xFF;
                    }
                for (int k = 0; k < 40; k++) c.ngon(rnd.nextFloat() * n, rnd.nextFloat() * n, 4 + rnd.nextFloat() * 8, 5 + k % 3, k * 17, 0x6B3A1A, 0.35f);
            }
            case "dragon" -> {
                c.base(0x2F9C8E, 0x16564F, 1);
                float sr = 32;
                for (int row = 0; row < 16; row++)
                    for (int col = 0; col < 8; col++) {
                        float cx = col * sr * 2 + (row % 2) * sr, cy = row * sr;
                        c.circle(cx, cy, sr, row % 2 == 0 ? 0x1F7A70 : 0x248A7E, 1f);
                        c.ring(cx, cy, sr - 2, 2.5f, 0x7FE0D0, 0.45f);
                        c.ring(cx, cy - 4, sr - 12, 1.5f, 0xFFD34D, 0.25f);
                    }
                // row 0 repeated at the bottom covers the last row (only the part that falls inside the tile)
                for (int col = 0; col < 8; col++) {
                    float cx = col * sr * 2, cy = n;
                    c.shape(cx - sr, cy - sr, cx + sr, cy, 0x1F7A70, 1f, (x, y) -> Math.max((float) Math.hypot(x - cx, y - cy) - sr, y - n));
                    c.shape(cx - sr, cy - sr, cx + sr, cy, 0x7FE0D0, 0.45f, (x, y) -> Math.max(Math.abs((float) Math.hypot(x - cx, y - cy) - (sr - 2)) - 1.25f, y - n));
                    c.shape(cx - sr, cy - sr, cx + sr, cy, 0xFFD34D, 0.25f, (x, y) -> Math.max(Math.abs((float) Math.hypot(x - cx, y - cy + 4) - (sr - 12)) - 0.75f, y - n));
                }
            }
            case "midnight" -> {
                c.base(0x1F2A55, 0x0B1030, 2);
                for (int k = 0; k < 220; k++) c.circle(rnd.nextFloat() * n, rnd.nextFloat() * n, 0.6f + rnd.nextFloat() * 1.4f, 0xFFFFFF, 0.4f + rnd.nextFloat() * 0.6f);
                for (int k = 0; k < 18; k++) c.star(rnd.nextFloat() * n, rnd.nextFloat() * n, 6, 1.6f, 4, 0, 0xFFF2C8, 0.9f);
                for (int k = 0; k < 6; k++) {
                    float x = rnd.nextFloat() * n, y = rnd.nextFloat() * n, s = 12 + rnd.nextFloat() * 10;
                    c.glow(x, y, s * 3, 0xF2E6A8, 0.35f);
                    c.circle(x, y, s, 0xF2E6A8, 0.95f);
                    c.circle(x + s * 0.45f, y - s * 0.25f, s * 0.85f, 0x161E44, 1f);
                }
            }
            case "master" -> {
                c.base(0x7A4FC8, 0x3E1F78, 2);
                for (int row = 0; row < 4; row++)
                    for (int col = 0; col < 4; col++) {
                        float cx = col * 128 + (row % 2) * 64 + 64, cy = row * 128 + 64;
                        ball(c, cx, cy, 34, 0x8F5BD8, 0.5f, 0);
                        c.circle(cx - 14, cy - 16, 7, 0xD94BC9, 0.85f);
                        c.circle(cx + 14, cy - 16, 7, 0xD94BC9, 0.85f);
                    }
                for (int k = 0; k < 60; k++) {
                    float x = rnd.nextFloat() * n, y = rnd.nextFloat() * n, s = 3 + rnd.nextFloat() * 4;
                    c.poly(new float[]{x, y - s, x + s * 0.6f, y, x, y + s, x - s * 0.6f, y}, 0xEDE3FF, 0.7f);
                }
            }
            case "champion" -> {
                c.base(0xF7D774, 0xC99A2E, 1);
                for (int row = 0; row < 8; row++)
                    for (int col = 0; col < 8; col++) {
                        float cx = col * 64 + (row % 2) * 32, cy = row * 64;
                        c.poly(new float[]{cx, cy - 27, cx + 23, cy, cx, cy + 27, cx - 23, cy}, 0xFFF4D0, 0.45f);
                        c.poly(new float[]{cx, cy - 20, cx + 16, cy, cx, cy + 20, cx - 16, cy}, 0xA8761A, 0.45f);
                        c.circle(cx, cy, 4, 0xFFF4D0, 0.7f);
                    }
                for (int row = 0; row < 4; row++) for (int col = 0; col < 4; col++) crown(c, col * 128 + (row % 2) * 64 + 32, row * 128 + 32, 13, 0x8A1C1C, 0.8f);
                for (int k = 0; k < 50; k++) c.star(rnd.nextFloat() * n, rnd.nextFloat() * n, 5, 1.5f, 4, 0, 0xFFFFFF, 0.8f);
            }
            case "cosmos" -> {
                for (int y = 0; y < n; y++)
                    for (int x = 0; x < n; x++) {
                        float a = c.noise(x, y, 3, 5), b2 = c.noise(x, y, 5, 9);
                        int col = lerp(0x0A0820, 0x3A1F78, MathHelper.clamp(a * 1.6f - 0.4f, 0, 1));
                        col = lerp(col, 0xD94BC9, MathHelper.clamp(b2 * 2f - 1.2f, 0, 0.6f));
                        col = lerp(col, 0x2E9CD8, MathHelper.clamp(a * 2f - 1.3f, 0, 0.5f));
                        int i = y * n + x;
                        c.r[i] = (col >> 16) & 0xFF; c.g[i] = (col >> 8) & 0xFF; c.b[i] = col & 0xFF;
                    }
                for (int k = 0; k < 300; k++) c.circle(rnd.nextFloat() * n, rnd.nextFloat() * n, 0.5f + rnd.nextFloat() * 1.2f, 0xFFFFFF, 0.3f + rnd.nextFloat() * 0.7f);
                for (int k = 0; k < 14; k++) {
                    float x = rnd.nextFloat() * n, y = rnd.nextFloat() * n;
                    c.glow(x, y, 14, 0xFFFFFF, 0.5f);
                    c.star(x, y, 8, 1.4f, 4, 0, 0xFFFFFF, 0.95f);
                }
            }
            case "toxic" -> {
                c.base(0x8A4FB8, 0x4A2270, 2);
                for (int k = 0; k < 110; k++) {
                    float x = rnd.nextFloat() * n, y = rnd.nextFloat() * n, s = 3 + rnd.nextFloat() * 16;
                    c.circle(x, y, s, k % 2 == 0 ? 0xB86BFF : 0x6FE36B, 0.28f);
                    c.ring(x, y, s, 1.5f, 0xE8D8FF, 0.5f);
                    c.circle(x - s * 0.35f, y - s * 0.35f, s * 0.22f, 0xFFFFFF, 0.6f);
                }
            }
            case "dojo" -> {
                c.base(0xD84B3A, 0x8A1C14, 1);
                for (int y = 0; y < n; y++)
                    for (int x = 0; x < n; x++) {
                        int m = Math.floorMod(x + y, 64);
                        float cov = MathHelper.clamp(Math.min(m, 14 - m) + 0.5f, 0, 1);
                        if (m < 15) c.blend(x, y, 0xF2E0C8, 0.12f * cov);
                    }
                for (int k = 0; k < 16; k++) {
                    float x = rnd.nextFloat() * n, y = rnd.nextFloat() * n, s = 16 + rnd.nextFloat() * 12;
                    c.ring(x, y, s, 3, 0xFFF4D0, 0.35f);
                    for (int j = 0; j < 8; j++) {
                        double a = j * Math.PI / 4;
                        c.line(x + (float) Math.cos(a) * s * 1.2f, y + (float) Math.sin(a) * s * 1.2f, x + (float) Math.cos(a) * s * 1.7f, y + (float) Math.sin(a) * s * 1.7f, 3, 0xFFF4D0, 0.4f);
                    }
                }
            }
            case "sakura" -> {
                c.base(0xFFE6EE, 0xFFC4D6, 2);
                for (int k = 0; k < 22; k++) {
                    float x = rnd.nextFloat() * n, y = rnd.nextFloat() * n, s = 10 + rnd.nextFloat() * 8;
                    for (int j = 0; j < 5; j++) {
                        double a = j * Math.PI * 2 / 5 + k;
                        c.ellipse(x + (float) Math.cos(a) * s * 0.55f, y + (float) Math.sin(a) * s * 0.55f, s * 0.55f, s * 0.36f, (float) Math.toDegrees(a), 0xFF9EC0, 0.75f);
                    }
                    c.circle(x, y, s * 0.22f, 0xFFE27A, 0.9f);
                }
                for (int k = 0; k < 70; k++) c.ellipse(rnd.nextFloat() * n, rnd.nextFloat() * n, 4 + rnd.nextFloat() * 3, 2.2f, rnd.nextFloat() * 180, 0xFFB0CC, 0.7f);
            }
            case "prism" -> {
                for (int y = 0; y < n; y++)
                    for (int x = 0; x < n; x++) {
                        float hue = ((x + y) / (float) n) % 1f;
                        int col = MathHelper.hsvToRgb(hue, 0.35f, 1f);
                        int i = y * n + x;
                        c.r[i] = (col >> 16) & 0xFF; c.g[i] = (col >> 8) & 0xFF; c.b[i] = col & 0xFF;
                    }
                for (int row = 0; row < 8; row++)
                    for (int col = 0; col < 8; col++) {
                        float cx = col * 64 + (row % 2) * 32, cy = row * 64;
                        c.poly(new float[]{cx, cy - 20, cx + 12, cy, cx, cy + 20, cx - 12, cy}, 0xFFFFFF, 0.35f);
                    }
                for (int k = 0; k < 40; k++) c.star(rnd.nextFloat() * n, rnd.nextFloat() * n, 7, 1.6f, 4, 0, 0xFFFFFF, 0.9f);
            }
            default -> c.base(0xE9F2FB, 0xD3E4F4, 1);
        }
    }

    // ------------------------------------------------------------------ motifs

    private static void ball(Canvas c, float cx, float cy, float r, int top, float a, float deg) {
        c.circle(cx + 3, cy + 4, r, 0x000000, a * 0.35f);
        double t = Math.toRadians(deg);
        float ca = (float) Math.cos(t), sa = (float) Math.sin(t);
        c.shape(cx - r, cy - r, cx + r, cy + r, top, a + 0.3f, (x, y) -> {
            float d = (float) Math.hypot(x - cx, y - cy) - r;
            float side = -((x - cx) * sa) + (y - cy) * ca; // < 0 = top half
            return Math.max(d, side);
        });
        c.shape(cx - r, cy - r, cx + r, cy + r, 0xFFFFFF, a + 0.3f, (x, y) -> {
            float d = (float) Math.hypot(x - cx, y - cy) - r;
            float side = ((x - cx) * sa) - (y - cy) * ca;
            return Math.max(d, side);
        });
        c.ring(cx, cy, r, 3, 0x1C1C22, a + 0.25f);
        c.line(cx - ca * r, cy - sa * r, cx + ca * r, cy + sa * r, 5, 0x1C1C22, a + 0.25f);
        c.circle(cx, cy, r * 0.3f, 0x1C1C22, a + 0.25f);
        c.circle(cx, cy, r * 0.19f, 0xFFFFFF, a + 0.35f);
    }

    private static void flower(Canvas c, float x, float y, float s, int col, float a) {
        for (int j = 0; j < 5; j++) {
            double ang = j * Math.PI * 2 / 5;
            c.circle(x + (float) Math.cos(ang) * s * 0.6f, y + (float) Math.sin(ang) * s * 0.6f, s * 0.45f, col, a);
        }
        c.circle(x, y, s * 0.35f, 0xF2B530, a);
    }

    private static void flame(Canvas c, float x, float y, float s, int col, float a) {
        c.circle(x, y + s * 0.3f, s * 0.55f, col, a);
        c.poly(new float[]{x - s * 0.53f, y + s * 0.2f, x + s * 0.53f, y + s * 0.2f, x + s * 0.12f, y - s}, col, a);
    }

    private static void bolt(Canvas c, float x, float y, float s, int col, float a) {
        c.poly(new float[]{x + 0.28f * s, y - s, x - 0.45f * s, y + 0.15f * s, x - 0.02f * s, y + 0.15f * s,
                x - 0.28f * s, y + s, x + 0.45f * s, y - 0.15f * s, x + 0.02f * s, y - 0.15f * s}, col, a);
    }

    private static void snow(Canvas c, float x, float y, float s, int col, float a) {
        for (int k = 0; k < 3; k++) {
            double ang = Math.toRadians(k * 60);
            float dx = (float) Math.cos(ang) * s, dy = (float) Math.sin(ang) * s;
            c.line(x - dx, y - dy, x + dx, y + dy, s * 0.12f, col, a);
        }
        for (int k = 0; k < 6; k++) {
            double ang = Math.toRadians(k * 60);
            float bx = x + (float) Math.cos(ang) * s * 0.6f, by = y + (float) Math.sin(ang) * s * 0.6f;
            for (int sgn = -1; sgn <= 1; sgn += 2) {
                double b = ang + sgn * Math.toRadians(45);
                c.line(bx, by, bx + (float) Math.cos(b) * s * 0.3f, by + (float) Math.sin(b) * s * 0.3f, s * 0.08f, col, a);
            }
        }
    }

    private static void heart(Canvas c, float x, float y, float s, int col, float a) {
        c.circle(x - s * 0.36f, y - s * 0.2f, s * 0.42f, col, a);
        c.circle(x + s * 0.36f, y - s * 0.2f, s * 0.42f, col, a);
        c.poly(new float[]{x - s * 0.76f, y - s * 0.06f, x + s * 0.76f, y - s * 0.06f, x, y + s * 0.85f}, col, a);
    }

    private static void crown(Canvas c, float x, float y, float s, int col, float a) {
        c.poly(new float[]{x - s, y + s * 0.5f, x - s, y - s * 0.4f, x - s * 0.5f, y, x, y - s * 0.7f, x + s * 0.5f, y,
                x + s, y - s * 0.4f, x + s, y + s * 0.5f}, col, a);
    }
}

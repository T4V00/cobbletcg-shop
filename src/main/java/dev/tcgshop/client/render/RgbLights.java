package dev.tcgshop.client.render;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.tcgshop.TcgShop;
import dev.tcgshop.shop.RgbDisplays;
import dev.tcgshop.shop.RgbLighting;
import dev.tcgshop.shop.ShopDisplayBlock;
import dev.tcgshop.shop.ShopDisplayBlockEntity;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.registry.Registries;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 1.0 RGB displays: animated LED lighting drawn on top of the static block model (client).
 * The LED layout of each display comes from assets/tcgshop/rgb/&lt;block&gt;.json (written by tools/gen_rgb_displays.py
 * together with the block model, so the light strips sit exactly on the model's dark diffuser channels):
 *   segs:  [x0, y0, z0, x1, y1, z1, phase] small boxes in structure pixels ("facing north" local space, like DisplayKind),
 *          phase = distance along the strip in blocks (the chase animation flows along it);
 *   under: [x0, z0, x1, z1] floor footprint that spills colored light around the fixture (underglow);
 *   back:  [x0, y0, x1, y1] wall area lit behind wall-hung fixtures (backglow).
 * Each segment is a full-bright core (slightly larger than its channel) plus, when the camera is near, a soft
 * translucent emissive halo. Colors follow the display's mode ({@link RgbLighting}) and the world time.
 */
public final class RgbLights {
    private static final Identifier WHITE = Identifier.ofVanilla("textures/block/white_concrete.png");
    private static final int FULL = 0xF000F0;
    /** Halos and spilled light are skipped beyond this distance (blocks, squared). */
    private static final double NEAR_SQ = 40 * 40;

    private record Layout(float[][] segs, float[][] under, float[][] back) {}

    private static final Layout EMPTY = new Layout(new float[0][], new float[0][], new float[0][]);
    private static final Map<Block, Layout> CACHE = new HashMap<>();

    private RgbLights() {}

    /** Client init: RGB displays use smoked glass, so their models go in the translucent layer. */
    public static void initClient() {
        BlockRenderLayerMap.INSTANCE.putBlocks(RenderLayer.getTranslucent(), RgbDisplays.BLOCKS.toArray(new Block[0]));
        // F3+T / resource pack changes: reload the LED layouts with the models
        net.fabricmc.fabric.api.resource.ResourceManagerHelper.get(net.minecraft.resource.ResourceType.CLIENT_RESOURCES)
                .registerReloadListener(new net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener() {
                    @Override
                    public Identifier getFabricId() { return Identifier.of(TcgShop.MOD_ID, "rgb_led_layouts"); }

                    @Override
                    public void reload(net.minecraft.resource.ResourceManager manager) { clearCache(); }
                });
    }

    /** Drops the cached layouts (resource reload). */
    public static void clearCache() {
        CACHE.clear();
    }

    private static Layout layout(ShopDisplayBlock block) {
        return CACHE.computeIfAbsent(block, b -> {
            Identifier id = Registries.BLOCK.getId(b);
            Identifier file = Identifier.of(id.getNamespace(), "rgb/" + id.getPath() + ".json");
            try {
                Optional<Resource> res = MinecraftClient.getInstance().getResourceManager().getResource(file);
                if (res.isEmpty()) {
                    TcgShop.LOGGER.warn("[rgb] no LED layout {}", file);
                    return EMPTY;
                }
                try (Reader r = res.get().getReader()) {
                    JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
                    return new Layout(rows(o, "segs", 7), rows(o, "under", 4), rows(o, "back", 4));
                }
            } catch (Exception e) {
                TcgShop.LOGGER.warn("[rgb] bad LED layout {}: {}", file, e.toString());
                return EMPTY;
            }
        });
    }

    /** Rows of n numbers; coordinates (all but a 7th "phase" column) go from pixels to blocks. */
    private static float[][] rows(JsonObject o, String key, int n) {
        if (!o.has(key) || !o.get(key).isJsonArray()) return new float[0][];
        JsonArray a = o.getAsJsonArray(key);
        float[][] out = new float[a.size()][];
        int k = 0;
        for (JsonElement e : a) {
            JsonArray row = e.getAsJsonArray();
            if (row.size() < n) continue;
            float[] v = new float[n];
            for (int i = 0; i < n; i++) v[i] = row.get(i).getAsFloat() / (i == 6 ? 1f : 16f);
            out[k++] = v;
        }
        return k == out.length ? out : java.util.Arrays.copyOf(out, k);
    }

    // ------------------------------------------------------------------ colors

    private static float frac(float v) {
        return v - (float) Math.floor(v);
    }

    /** Pseudo-random 0..1 per segment (fixed). */
    private static float hash(int i, long salt) {
        long h = (i * 0x9E3779B97F4A7C15L) ^ salt;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return (h & 0xFFFF) / 65535f;
    }

    /** Brightness 0..1 of a segment (used for the halo strength). */
    private static float brightness(int mode, int i, float t, long salt) {
        return switch (mode) {
            case RgbLighting.BREATHE -> 0.3f + 0.7f * (0.5f + 0.5f * MathHelper.sin(t * 0.07f));
            case RgbLighting.SPARKLE -> {
                float h = hash(i, salt);
                float tw = Math.max(0, MathHelper.sin(t * (0.10f + h * 0.12f) + h * 40f));
                yield 0.18f + 0.82f * tw * tw * tw * tw;
            }
            case RgbLighting.STATIC -> 0.85f + 0.15f * MathHelper.sin(t * 0.05f);
            default -> 1f;
        };
    }

    /** 0xRRGGBB of a segment. */
    private static int color(int mode, int staticRgb, float phase, int i, float t, long salt, float v) {
        return switch (mode) {
            case RgbLighting.WAVE -> MathHelper.hsvToRgb(frac(t * 0.006f + salt % 97 / 97f), 0.85f, 1f);
            case RgbLighting.BREATHE -> MathHelper.hsvToRgb(frac(t * 0.0015f + phase * 0.04f), 0.8f, v);
            case RgbLighting.SPARKLE -> MathHelper.hsvToRgb(frac(hash(i, salt ^ 0x51L) + t * 0.004f), 0.75f * (1.2f - v), v);
            case RgbLighting.STATIC -> scale(staticRgb, v);
            default -> MathHelper.hsvToRgb(frac(t * 0.02f - phase * 0.33f), 0.9f, 1f); // CHASE
        };
    }

    private static int scale(int rgb, float k) {
        int r = Math.min(255, (int) (((rgb >> 16) & 0xFF) * k)), g = Math.min(255, (int) (((rgb >> 8) & 0xFF) * k)), b = Math.min(255, (int) ((rgb & 0xFF) * k));
        return (r << 16) | (g << 8) | b;
    }

    // ------------------------------------------------------------------ drawing

    /**
     * Draws the display's LEDs. The matrix is already in the display's "facing north" local space with the origin at the
     * master block's corner (ShopRenderers.DisplayRenderer after rotateToFacing).
     */
    public static void render(ShopDisplayBlockEntity be, ShopDisplayBlock block, MatrixStack m, VertexConsumerProvider vcp, float time, int overlay) {
        int mode = be.rgbMode();
        if (mode == RgbLighting.OFF) return; // the model's dark diffusers show
        Layout l = layout(block);
        if (l.segs.length == 0 && l.under.length == 0 && l.back.length == 0) return;
        long salt = be.getPos().asLong();
        int staticRgb = be.rgbColor();
        MinecraftClient mc = MinecraftClient.getInstance();
        Vec3d cam = mc.gameRenderer == null ? null : mc.gameRenderer.getCamera().getPos();
        boolean near = cam == null || cam.squaredDistanceTo(Vec3d.ofCenter(be.getPos())) < NEAR_SQ;

        int n = l.segs.length;
        int[] cols = new int[n];
        float[] vals = new float[n];
        for (int i = 0; i < n; i++) {
            vals[i] = brightness(mode, i, time, salt);
            cols[i] = color(mode, staticRgb, l.segs[i][6], i, time, salt, vals[i]);
        }
        MatrixStack.Entry e = m.peek();
        // 1) cores: full-bright, slightly larger than the diffuser channel of the model
        VertexConsumer core = vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(WHITE));
        float g = 0.08f / 16f;
        for (int i = 0; i < n; i++) {
            float[] s = l.segs[i];
            box(core, e, s[0] - g, s[1] - g, s[2] - g, s[3] + g, s[4] + g, s[5] + g, 0xFF000000 | cols[i], FULL, overlay);
        }
        if (!near) return;
        // 2) halos: soft translucent emissive shell around each LED
        VertexConsumer glow = vcp.getBuffer(RenderLayer.getEntityTranslucentEmissive(WHITE));
        float hg = 0.75f / 16f;
        for (int i = 0; i < n; i++) {
            float[] s = l.segs[i];
            int a = (int) (70 * vals[i]);
            if (a < 6) continue;
            box(glow, e, s[0] - hg, s[1] - hg, s[2] - hg, s[3] + hg, s[4] + hg, s[5] + hg, (a << 24) | cols[i], FULL, overlay);
        }
        // 3) spilled light: on the floor around the fixture and on the wall behind wall-hung ones
        float avg = 0;
        for (float v : vals) avg += v;
        avg = n == 0 ? 1 : avg / n;
        int spill = n == 0 ? color(mode, staticRgb, 0, 0, time, salt, 1f) : cols[(int) ((time * 0.05f) % n + n) % n];
        if (mode == RgbLighting.CHASE || mode == RgbLighting.SPARKLE) spill = MathHelper.hsvToRgb(frac(time * 0.02f), 0.85f, 1f);
        float[] grow = {0.12f, 0.28f, 0.45f};
        float[] alpha = {0.30f, 0.17f, 0.08f};
        for (float[] u : l.under) {
            for (int k = 0; k < 3; k++) {
                float x0 = u[0] - grow[k], z0 = u[1] - grow[k], x1 = u[2] + grow[k], z1 = u[3] + grow[k], y = 0.012f + k * 0.002f;
                int c = ((int) (alpha[k] * avg * 255) << 24) | spill;
                quad(glow, e, x0, y, z1, x0, y, z0, x1, y, z0, x1, y, z1, 0, 1, 0, c, FULL, overlay);
            }
        }
        float zb = block.kind.d - 0.004f;
        for (float[] b : l.back) {
            for (int k = 0; k < 3; k++) {
                float x0 = b[0] - grow[k] * 0.5f, y0 = b[1] - grow[k] * 0.5f, x1 = b[2] + grow[k] * 0.5f, y1 = b[3] + grow[k] * 0.5f;
                int c = ((int) (alpha[k] * avg * 0.9f * 255) << 24) | spill;
                quad(glow, e, x1, y0, zb, x1, y1, zb, x0, y1, zb, x0, y0, zb, 0, 0, -1, c, FULL, overlay);
            }
        }
    }

    /** Axis-aligned box (all six faces). */
    private static void box(VertexConsumer vc, MatrixStack.Entry e, float x0, float y0, float z0, float x1, float y1, float z1, int argb, int light, int overlay) {
        quad(vc, e, x1, y0, z0, x1, y1, z0, x0, y1, z0, x0, y0, z0, 0, 0, -1, argb, light, overlay);
        quad(vc, e, x0, y0, z1, x0, y1, z1, x1, y1, z1, x1, y0, z1, 0, 0, 1, argb, light, overlay);
        quad(vc, e, x0, y0, z0, x0, y1, z0, x0, y1, z1, x0, y0, z1, -1, 0, 0, argb, light, overlay);
        quad(vc, e, x1, y0, z1, x1, y1, z1, x1, y1, z0, x1, y0, z0, 1, 0, 0, argb, light, overlay);
        quad(vc, e, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, 0, 1, 0, argb, light, overlay);
        quad(vc, e, x0, y0, z1, x0, y0, z0, x1, y0, z0, x1, y0, z1, 0, -1, 0, argb, light, overlay);
    }

    /** Quad whose vertex order is fixed to be counter-clockwise seen from the side its normal points to. */
    private static void quad(VertexConsumer vc, MatrixStack.Entry e, float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz, float nx, float ny, float nz,
                             int argb, int light, int overlay) {
        Matrix4f mat = e.getPositionMatrix();
        int a = (argb >>> 24) & 0xFF, r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        float e1x = bx - ax, e1y = by - ay, e1z = bz - az, e2x = cx - ax, e2y = cy - ay, e2z = cz - az;
        float gx = e1y * e2z - e1z * e2y, gy = e1z * e2x - e1x * e2z, gz = e1x * e2y - e1y * e2x;
        if (gx * nx + gy * ny + gz * nz >= 0) {
            vc.vertex(mat, ax, ay, az).color(r, g, b, a).texture(0, 1).overlay(overlay).light(light).normal(e, nx, ny, nz);
            vc.vertex(mat, bx, by, bz).color(r, g, b, a).texture(0, 0).overlay(overlay).light(light).normal(e, nx, ny, nz);
            vc.vertex(mat, cx, cy, cz).color(r, g, b, a).texture(1, 0).overlay(overlay).light(light).normal(e, nx, ny, nz);
            vc.vertex(mat, dx, dy, dz).color(r, g, b, a).texture(1, 1).overlay(overlay).light(light).normal(e, nx, ny, nz);
        } else {
            vc.vertex(mat, ax, ay, az).color(r, g, b, a).texture(0, 1).overlay(overlay).light(light).normal(e, nx, ny, nz);
            vc.vertex(mat, dx, dy, dz).color(r, g, b, a).texture(1, 1).overlay(overlay).light(light).normal(e, nx, ny, nz);
            vc.vertex(mat, cx, cy, cz).color(r, g, b, a).texture(1, 0).overlay(overlay).light(light).normal(e, nx, ny, nz);
            vc.vertex(mat, bx, by, bz).color(r, g, b, a).texture(0, 0).overlay(overlay).light(light).normal(e, nx, ny, nz);
        }
    }
}

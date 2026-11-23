package dev.tcgshop.client.render;

import dev.masterball.api.card.CardInfo;
import dev.masterball.api.card.Rarity;
import dev.tcgshop.client.screen.Draw;
import dev.tcgshop.pricing.PriceService;
import dev.tcgshop.shop.BoosterBox;
import dev.tcgshop.shop.DecorBlocks;
import dev.tcgshop.shop.DisplayKind;
import dev.tcgshop.shop.Roulette;
import dev.tcgshop.shop.ShopDisplayBlock;
import dev.tcgshop.shop.ShopDisplayBlockEntity;
import dev.tcgshop.shop.ShopServer;
import dev.tcgshop.shop.TcgBlocks;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Shop rendering: life-size products on multi-block furniture (cards are drawn with the
 * same system as a vanilla item frame, plus each furniture piece's tilt), highlight of the targeted
 * product, cardboard booster boxes with folds and opening animation, posters, table, register and roulette.
 */
public final class ShopRenderers {
    private static final Identifier WHITE = Identifier.ofVanilla("textures/block/white_concrete.png");
    private static final Identifier MYSTERY = Identifier.of("tcgshop", "textures/block/mystery_box.png");
    private static final Identifier CARDBOARD = Identifier.of("tcgshop", "textures/block/cardboard.png");
    private static final Identifier CARDBOARD_EDGE = Identifier.of("tcgshop", "textures/block/cardboard_edge.png");
    private static final Identifier PRINT = Identifier.of("tcgshop", "textures/block/box_print.png");
    private static final Identifier WRAP = Identifier.of("tcgshop", "textures/block/shrink_wrap.png");
    private static final Map<String, Identifier> PACK_TEX = new HashMap<>();
    private static final int FULL = 0xF000F0;
    /** Height of a card in FIXED mode space (the API's card_template model: 0.85 x scale 2.2). */
    private static final float FRAME_CARD_H = dev.masterball.api.client.CardItemRenderer.FIXED_HEIGHT;

    private ShopRenderers() {}

    public static Identifier packTextureForSet(String set) {
        return PACK_TEX.computeIfAbsent(set, s -> {
            for (Identifier id : Registries.ITEM.getIds())
                if (CardInfo.NS.equals(id.getNamespace()) && id.getPath().startsWith(s + "/sealed_")) return CardInfo.packTexture(id);
            return Identifier.ofVanilla("textures/misc/unknown_pack.png");
        });
    }

    public static int setColor(String set) {
        float hue = ((set.hashCode() >>> 8) & 0xFF) / 255f;
        return 0xFF000000 | MathHelper.hsvToRgb(hue, 0.62f, 0.62f);
    }

    // ------------------------------------------------------------------ primitives

    private static void quad(VertexConsumer vc, MatrixStack.Entry e, float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz, float nx, float ny, float nz,
                             int argb, int light, int overlay, float u0, float v0, float u1, float v1) {
        Matrix4f m = e.getPositionMatrix();
        int a = (argb >>> 24) & 0xFF, r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        // the game culls faces whose vertex order is not counter-clockwise seen from outside: they are ordered by the normal
        float e1x = bx - ax, e1y = by - ay, e1z = bz - az, e2x = cx - ax, e2y = cy - ay, e2z = cz - az;
        float gx = e1y * e2z - e1z * e2y, gy = e1z * e2x - e1x * e2z, gz = e1x * e2y - e1y * e2x;
        if (gx * nx + gy * ny + gz * nz >= 0) {
            vc.vertex(m, ax, ay, az).color(r, g, b, a).texture(u0, v1).overlay(overlay).light(light).normal(e, nx, ny, nz);
            vc.vertex(m, bx, by, bz).color(r, g, b, a).texture(u0, v0).overlay(overlay).light(light).normal(e, nx, ny, nz);
            vc.vertex(m, cx, cy, cz).color(r, g, b, a).texture(u1, v0).overlay(overlay).light(light).normal(e, nx, ny, nz);
            vc.vertex(m, dx, dy, dz).color(r, g, b, a).texture(u1, v1).overlay(overlay).light(light).normal(e, nx, ny, nz);
        } else {
            vc.vertex(m, ax, ay, az).color(r, g, b, a).texture(u0, v1).overlay(overlay).light(light).normal(e, nx, ny, nz);
            vc.vertex(m, dx, dy, dz).color(r, g, b, a).texture(u1, v1).overlay(overlay).light(light).normal(e, nx, ny, nz);
            vc.vertex(m, cx, cy, cz).color(r, g, b, a).texture(u1, v0).overlay(overlay).light(light).normal(e, nx, ny, nz);
            vc.vertex(m, bx, by, bz).color(r, g, b, a).texture(u0, v0).overlay(overlay).light(light).normal(e, nx, ny, nz);
        }
    }

    /** Vertical rectangle at z, seen from -Z (u from left to right from the viewer's point of view). */
    private static void front(VertexConsumer vc, MatrixStack.Entry e, float x0, float y0, float x1, float y1, float z, int argb, int light, int overlay) {
        front(vc, e, x0, y0, x1, y1, z, argb, light, overlay, 0, 0, 1, 1);
    }

    private static void front(VertexConsumer vc, MatrixStack.Entry e, float x0, float y0, float x1, float y1, float z, int argb, int light, int overlay,
                              float u0, float v0, float u1, float v1) {
        quad(vc, e, x1, y0, z, x1, y1, z, x0, y1, z, x0, y0, z, 0, 0, -1, argb, light, overlay, u0, v0, u1, v1);
    }

    /**
     * Textured box (cuboid) in block coordinates. tex = null uses plain white; UVs repeat every `tile`
     * blocks so the cardboard does not stretch.
     */
    private static void cuboid(MatrixStack m, VertexConsumerProvider vcp, Identifier tex, float tile, float x0, float y0, float z0,
                               float x1, float y1, float z1, int argb, int light, int overlay) {
        java.util.function.Supplier<VertexConsumer> vc = () -> vcp.getBuffer(RenderLayer.getEntityCutout(tex == null ? WHITE : tex));
        MatrixStack.Entry e = m.peek();
        float w = tex == null ? 1 : (x1 - x0) / tile, h = tex == null ? 1 : (y1 - y0) / tile, d = tex == null ? 1 : (z1 - z0) / tile;
        quad(vc.get(), e, x1, y0, z0, x1, y1, z0, x0, y1, z0, x0, y0, z0, 0, 0, -1, argb, light, overlay, 0, 0, w, h);
        quad(vc.get(), e, x0, y0, z1, x0, y1, z1, x1, y1, z1, x1, y0, z1, 0, 0, 1, argb, light, overlay, 0, 0, w, h);
        quad(vc.get(), e, x0, y0, z0, x0, y1, z0, x0, y1, z1, x0, y0, z1, -1, 0, 0, shade(argb, 0.84f), light, overlay, 0, 0, d, h);
        quad(vc.get(), e, x1, y0, z1, x1, y1, z1, x1, y1, z0, x1, y0, z0, 1, 0, 0, shade(argb, 0.84f), light, overlay, 0, 0, d, h);
        quad(vc.get(), e, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, 0, 1, 0, shade(argb, 1.08f), light, overlay, 0, 0, w, d);
        quad(vc.get(), e, x0, y0, z1, x0, y0, z0, x1, y0, z0, x1, y0, z1, 0, -1, 0, shade(argb, 0.6f), light, overlay, 0, 0, w, d);
    }

    private static void cuboid(MatrixStack m, VertexConsumerProvider vcp, float x0, float y0, float z0, float x1, float y1, float z1, int argb, int light, int overlay) {
        cuboid(m, vcp, null, 1, x0, y0, z0, x1, y1, z1, argb, light, overlay);
    }

    private static int shade(int argb, float k) {
        int r = Math.min(255, (int) (((argb >> 16) & 0xFF) * k)), g = Math.min(255, (int) (((argb >> 8) & 0xFF) * k)), b = Math.min(255, (int) ((argb & 0xFF) * k));
        return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    private static void image(MatrixStack m, VertexConsumerProvider vcp, Identifier tex, float w, float h, int light, int overlay) {
        java.util.function.Supplier<VertexConsumer> vc = () -> vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(tex));
        front(vc.get(), m.peek(), -w / 2, -h / 2, w / 2, h / 2, 0, 0xFFFFFFFF, light, overlay);
    }

    /** Card drawn exactly as in an item frame (FIXED mode), centered and `h` blocks tall. */
    public static void frameCard(ItemStack st, MatrixStack m, VertexConsumerProvider vcp, int light, int overlay, float h) {
        MinecraftClient mc = MinecraftClient.getInstance();
        float k = h / FRAME_CARD_H;
        m.push();
        m.translate(0, -0.025f * k, 0.025f * k); // the card_template model puts the card 0.025 above and in front of the center
        m.scale(k, k, k);
        mc.getItemRenderer().renderItem(st, ModelTransformationMode.FIXED, light, OverlayTexture.DEFAULT_UV, m, vcp, mc.world, 0);
        m.pop();
    }

    private static void glow(MatrixStack m, VertexConsumerProvider vcp, float w, float h, int rgb, float alpha) {
        java.util.function.Supplier<VertexConsumer> vc = () -> vcp.getBuffer(RenderLayer.getEntityTranslucentEmissive(WHITE));
        int a = (int) (alpha * 255);
        for (int i = 3; i >= 1; i--) {
            float g = i * 0.035f;
            front(vc.get(), m.peek(), -w / 2 - g, -h / 2 - g, w / 2 + g, h / 2 + g, 0.01f + i * 0.001f, (a / i << 24) | (rgb & 0xFFFFFF), FULL, OverlayTexture.DEFAULT_UV);
        }
    }

    private static void rotateToFacing(MatrixStack m, Direction facing) {
        m.translate(0.5, 0, 0.5);
        float deg = switch (facing) {
            case SOUTH -> 180;
            case WEST -> 90;
            case EAST -> -90;
            default -> 0;
        };
        m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(deg));
        m.translate(-0.5, 0, -0.5);
    }

    private static void text(MatrixStack m, VertexConsumerProvider vcp, Text t, float scale, int color, int bg, int light) {
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        m.push();
        m.scale(-scale, -scale, scale);
        float w = tr.getWidth(t);
        tr.draw(t, -w / 2, 0, color, false, m.peek().getPositionMatrix(), vcp, TextRenderer.TextLayerType.POLYGON_OFFSET, bg, light);
        m.pop();
    }

    // ------------------------------------------------------------------ booster box

    /** Animation state of a box: lid (0 closed .. 1 open), packs coming out (0..1), plastic wrap (1 = intact). */
    public record BoxAnim(float lid, float packs, float wrap, float shake, float time) {
        public static final BoxAnim SEALED = new BoxAnim(0, 0, 1, 0, 0);
    }

    public static void drawBox(MatrixStack m, VertexConsumerProvider vcp, int light, int overlay, String set, float w, float h, float d) {
        float time = MinecraftClient.getInstance().world == null ? 0 : MinecraftClient.getInstance().world.getTime();
        drawBox(m, vcp, light, overlay, set, w, h, d, new BoxAnim(0, 0, 1, 0, time));
    }

    /**
     * Cardboard booster box (36 packs) centered at (0.5, 0, 0.5), front facing -Z. Tray printed in the set's
     * color, overhanging lid with its folds, corrugated cardboard edge, notched flap, framed pack art
     * on the front and top, set name, gold "36 SOBRES" band, shrink wrap with an animated shine and,
     * when opening, the lid swings on the back hinge and the packs inside become visible (and fly out).
     */
    public static void drawBox(MatrixStack m, VertexConsumerProvider vcp, int light, int overlay, String set, float w, float h, float d, BoxAnim a) {
        float x0 = 0.5f - w / 2, x1 = 0.5f + w / 2, z0 = 0.5f - d / 2, z1 = 0.5f + d / 2;
        int col = setColor(set), dark = shade(col, 0.62f), light2 = shade(col, 1.25f);
        float lidY = h * 0.72f, ov = Math.max(0.006f, w * 0.012f), tile = 0.5f;
        m.push();
        if (a.shake > 0) {
            float s = a.shake * (float) Math.sin(a.time * 2.3f) * 3f;
            m.translate(0.5, 0, 0.5);
            m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(s));
            m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(s * 0.6f));
            m.translate(-0.5, 0, -0.5);
        }
        MatrixStack.Entry e = m.peek();

        // --- tray (body): cardboard inside and underneath, printed outside
        cuboid(m, vcp, CARDBOARD, tile, x0, 0, z0, x1, 0.004f, z1, 0xFFFFFFFF, light, overlay); // base
        java.util.function.Supplier<VertexConsumer> print = () -> vcp.getBuffer(RenderLayer.getEntityCutout(PRINT));
        float bw = (x1 - x0) / tile, bh = lidY / tile, bd = (z1 - z0) / tile;
        quad(print.get(), e, x1, 0, z0, x1, lidY, z0, x0, lidY, z0, x0, 0, z0, 0, 0, -1, col, light, overlay, 0, 0, bw, bh);
        quad(print.get(), e, x0, 0, z1, x0, lidY, z1, x1, lidY, z1, x1, 0, z1, 0, 0, 1, dark, light, overlay, 0, 0, bw, bh);
        quad(print.get(), e, x0, 0, z0, x0, lidY, z0, x0, lidY, z1, x0, 0, z1, -1, 0, 0, shade(col, 0.85f), light, overlay, 0, 0, bd, bh);
        quad(print.get(), e, x1, 0, z1, x1, lidY, z1, x1, lidY, z0, x1, 0, z0, 1, 0, 0, shade(col, 0.85f), light, overlay, 0, 0, bd, bh);
        // vertical corner folds (folded cardboard)
        java.util.function.Supplier<VertexConsumer> solid = () -> vcp.getBuffer(RenderLayer.getEntityCutout(WHITE));
        float fold = w * 0.035f;
        int crease = shade(col, 0.72f);
        front(solid.get(), e, x0, 0.01f, x0 + fold * 0.18f, lidY - 0.01f, z0 - 0.0008f, crease, light, overlay);
        front(solid.get(), e, x1 - fold * 0.18f, 0.01f, x1, lidY - 0.01f, z0 - 0.0008f, crease, light, overlay);
        // lower gold band with "36 SOBRES"
        float bandY0 = h * 0.06f, bandY1 = h * 0.17f;
        front(solid.get(), e, x0 + 0.002f, bandY0, x1 - 0.002f, bandY1, z0 - 0.001f, 0xFFE8B730, light, overlay);
        front(solid.get(), e, x0 + 0.002f, bandY1, x1 - 0.002f, bandY1 + h * 0.012f, z0 - 0.001f, 0xFFFFF0B0, light, overlay);
        // framed pack art on the front of the tray (white frame + shadow)
        float artH = lidY - bandY1 - h * 0.08f, artW = Math.min(artH * 0.78f, w * 0.5f);
        float ax0 = 0.5f - artW / 2, ay0 = bandY1 + h * 0.035f;
        front(solid.get(), e, ax0 - 0.012f, ay0 - 0.012f, ax0 + artW + 0.012f, ay0 + artH + 0.012f, z0 - 0.0015f, 0xFFF8F8F8, light, overlay);
        front(solid.get(), e, ax0 - 0.006f + 0.008f, ay0 - 0.02f, ax0 + artW + 0.02f, ay0 + artH - 0.004f, z0 - 0.0012f, shade(col, 0.45f), light, overlay);
        java.util.function.Supplier<VertexConsumer> art = () -> vcp.getBuffer(RenderLayer.getEntityCutout(packTextureForSet(set)));
        front(art.get(), e, ax0, ay0, ax0 + artW, ay0 + artH, z0 - 0.002f, 0xFFFFFFFF, light, overlay, 0, 0.06f, 1, 0.94f);
        // color stripes beside the art
        front(solid.get(), e, x0 + w * 0.06f, ay0, x0 + w * 0.09f, ay0 + artH, z0 - 0.0015f, light2, light, overlay);
        front(solid.get(), e, x1 - w * 0.09f, ay0, x1 - w * 0.06f, ay0 + artH, z0 - 0.0015f, light2, light, overlay);
        // sides: small art
        float sa = Math.min(d * 0.55f, lidY * 0.6f);
        quad(art.get(), e, x1 + 0.0015f, lidY * 0.25f, 0.5f + sa * 0.39f, x1 + 0.0015f, lidY * 0.25f + sa, 0.5f + sa * 0.39f,
                x1 + 0.0015f, lidY * 0.25f + sa, 0.5f - sa * 0.39f, x1 + 0.0015f, lidY * 0.25f, 0.5f - sa * 0.39f, 1, 0, 0, 0xFFEEEEEE, light, overlay, 0, 0, 1, 1);
        quad(art.get(), e, x0 - 0.0015f, lidY * 0.25f, 0.5f - sa * 0.39f, x0 - 0.0015f, lidY * 0.25f + sa, 0.5f - sa * 0.39f,
                x0 - 0.0015f, lidY * 0.25f + sa, 0.5f + sa * 0.39f, x0 - 0.0015f, lidY * 0.25f, 0.5f + sa * 0.39f, -1, 0, 0, 0xFFEEEEEE, light, overlay, 0, 0, 1, 1);

        // --- interior (visible when open)
        if (a.lid > 0.02f) {
            float in = 0.012f;
            java.util.function.Supplier<VertexConsumer> cb = () -> vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(CARDBOARD));
            int inner = 0xFFB08A60;
            quad(cb.get(), e, x0 + in, 0.01f, z0 + in, x0 + in, lidY, z0 + in, x1 - in, lidY, z0 + in, x1 - in, 0.01f, z0 + in, 0, 0, 1, inner, light, overlay, 0, 0, bw, bh);
            quad(cb.get(), e, x1 - in, 0.01f, z1 - in, x1 - in, lidY, z1 - in, x0 + in, lidY, z1 - in, x0 + in, 0.01f, z1 - in, 0, 0, -1, shade(inner, 0.8f), light, overlay, 0, 0, bw, bh);
            quad(cb.get(), e, x0 + in, 0.01f, z1 - in, x0 + in, lidY, z1 - in, x0 + in, lidY, z0 + in, x0 + in, 0.01f, z0 + in, 1, 0, 0, shade(inner, 0.9f), light, overlay, 0, 0, bd, bh);
            quad(cb.get(), e, x1 - in, 0.01f, z0 + in, x1 - in, lidY, z0 + in, x1 - in, lidY, z1 - in, x1 - in, 0.01f, z1 - in, -1, 0, 0, shade(inner, 0.9f), light, overlay, 0, 0, bd, bh);
            // three rows of standing packs (the sealed edges show)
            Identifier ptex = packTextureForSet(set);
            java.util.function.Supplier<VertexConsumer> pv = () -> vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(ptex));
            int perRow = 12;
            float pw = (w - in * 2) / perRow, ph = lidY * 0.92f;
            for (int row = 0; row < 3; row++) {
                float pz = z0 + in + (d - in * 2) * (row + 0.5f) / 3f;
                for (int i = 0; i < perRow; i++) {
                    float px0 = x0 + in + i * pw, rise = 0;
                    int idx = row * perRow + i;
                    if (a.packs > 0) rise = MathHelper.clamp(a.packs * 1.6f - (idx % 9) * 0.07f, 0, 1) * h * 0.25f;
                    float top = ph + rise;
                    quad(pv.get(), e, px0 + pw, top - h * 0.3f, pz, px0 + pw, top, pz + 0.004f, px0, top, pz + 0.004f, px0, top - h * 0.3f, pz,
                            0, 0.6f, -0.8f, i % 2 == 0 ? 0xFFFFFFFF : 0xFFE6E6E6, light, overlay, 0, 0, 1, 0.3f);
                }
            }
        }

        // --- lid (swings on the back hinge)
        m.push();
        if (a.lid > 0) {
            float ang = (float) Draw.easeOutCubic(a.lid) * 112f;
            m.translate(0, h, z1 + ov);
            m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(ang)); // the front (z0) rises and swings back on the back hinge
            m.translate(0, -h, -(z1 + ov));
        }
        drawLid(m, vcp, light, overlay, set, x0 - ov, lidY - h * 0.02f, z0 - ov, x1 + ov, h, z1 + ov, col, dark, tile);
        m.pop();

        // --- shrink wrap with a shine sweeping across the box
        if (a.wrap > 0.01f && a.lid < 0.05f) {
            java.util.function.Supplier<VertexConsumer> wv = () -> vcp.getBuffer(RenderLayer.getEntityTranslucent(WRAP));
            int wa = (int) (255 * a.wrap) << 24 | 0xFFFFFF;
            float g = ov + 0.004f, sweep = (a.time * 0.012f) % 2f;
            float wx0 = x0 - g, wx1 = x1 + g, wz0 = z0 - g, wz1 = z1 + g, wy = h + 0.004f;
            MatrixStack.Entry we = m.peek();
            quad(wv.get(), we, wx1, -0.001f, wz0, wx1, wy, wz0, wx0, wy, wz0, wx0, -0.001f, wz0, 0, 0, -1, wa, light, overlay, sweep, 0, sweep + 1.4f, 1);
            quad(wv.get(), we, wx0, -0.001f, wz1, wx0, wy, wz1, wx1, wy, wz1, wx1, -0.001f, wz1, 0, 0, 1, wa, light, overlay, sweep, 0, sweep + 1.4f, 1);
            quad(wv.get(), we, wx0, -0.001f, wz0, wx0, wy, wz0, wx0, wy, wz1, wx0, -0.001f, wz1, -1, 0, 0, wa, light, overlay, sweep, 0, sweep + 0.8f, 1);
            quad(wv.get(), we, wx1, -0.001f, wz1, wx1, wy, wz1, wx1, wy, wz0, wx1, -0.001f, wz0, 1, 0, 0, wa, light, overlay, sweep, 0, sweep + 0.8f, 1);
            quad(wv.get(), we, wx0, wy, wz0, wx0, wy, wz1, wx1, wy, wz1, wx1, wy, wz0, 0, 1, 0, wa, light, overlay, sweep, 0, sweep + 1.4f, 0.8f);
        }

        // --- texts: "36 SOBRES" on the band and the set name on the sides of the lid
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        if (a.lid < 0.05f) {
            Text name = Text.translatable("masterball.set." + set).formatted(Formatting.BOLD);
            float s = Math.min(0.0055f * h / 0.6f, (d * 0.92f) / Math.max(1, tr.getWidth(name)));
            m.push();
            m.translate(x1 + ov + 0.002f, lidY + (h - lidY) * 0.32f, 0.5f);
            m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-90));
            text(m, vcp, name, s, 0xFFFFFFFF, 0, light);
            m.pop();
            m.push();
            m.translate(x0 - ov - 0.002f, lidY + (h - lidY) * 0.32f, 0.5f);
            m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(90));
            text(m, vcp, name, s, 0xFFFFFFFF, 0, light);
            m.pop();
            float fs = Math.min(0.0055f * h / 0.6f, (w * 0.88f) / Math.max(1, tr.getWidth(name)));
            m.push();
            m.translate(0.5f, lidY + (h - lidY) * 0.3f, z0 - ov - 0.002f);
            text(m, vcp, name, fs, 0xFFFFFFFF, 0, light);
            m.pop();
        }
        m.push();
        m.translate(0.5f, bandY0 + (bandY1 - bandY0) * 0.18f, z0 - 0.003f);
        text(m, vcp, Text.translatable("tcgshop.box.label", BoosterBox.PACKS).formatted(Formatting.BOLD), Math.min(0.0042f, (bandY1 - bandY0) / 10f),
                0xFF3A2A00, 0, light);
        m.pop();

        // --- packs flying out
        if (a.packs > 0) {
            Identifier ptex = packTextureForSet(set);
            for (int i = 0; i < 8; i++) {
                float p = MathHelper.clamp(a.packs * 1.4f - i * 0.05f, 0, 1);
                if (p <= 0 || p >= 1) continue;
                float ang = i * 45f + a.time * 2f;
                float rad = p * 0.45f * w;
                m.push();
                m.translate(0.5 + Math.cos(Math.toRadians(ang)) * rad, h + p * 0.9f, 0.5 + Math.sin(Math.toRadians(ang)) * rad);
                m.multiply(MinecraftClient.getInstance().getEntityRenderDispatcher().getRotation());
                m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) Math.sin(a.time * 0.3f + i) * 25));
                float sz = 0.28f * h / 0.6f * (1 - p * 0.3f);
                m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180));
                image(m, vcp, ptex, sz * Draw.PACK_RATIO, sz, FULL, overlay);
                m.pop();
            }
        }
        m.pop();
    }

    private static void drawLid(MatrixStack m, VertexConsumerProvider vcp, int light, int overlay, String set,
                                float x0, float y0, float z0, float x1, float y1, float z1, int col, int dark, float tile) {
        MatrixStack.Entry e = m.peek();
        java.util.function.Supplier<VertexConsumer> print = () -> vcp.getBuffer(RenderLayer.getEntityCutout(PRINT));
        float w = (x1 - x0) / tile, hgt = (y1 - y0) / tile, d = (z1 - z0) / tile;
        int side = shade(col, 0.78f);
        quad(print.get(), e, x1, y0, z0, x1, y1, z0, x0, y1, z0, x0, y0, z0, 0, 0, -1, side, light, overlay, 0, 0, w, hgt);
        quad(print.get(), e, x0, y0, z1, x0, y1, z1, x1, y1, z1, x1, y0, z1, 0, 0, 1, shade(side, 0.8f), light, overlay, 0, 0, w, hgt);
        quad(print.get(), e, x0, y0, z0, x0, y1, z0, x0, y1, z1, x0, y0, z1, -1, 0, 0, shade(side, 0.88f), light, overlay, 0, 0, d, hgt);
        quad(print.get(), e, x1, y0, z1, x1, y1, z1, x1, y1, z0, x1, y0, z0, 1, 0, 0, shade(side, 0.88f), light, overlay, 0, 0, d, hgt);
        quad(print.get(), e, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, 0, 1, 0, col, light, overlay, 0, 0, w, d);
        // inside: cardboard (visible when open)
        java.util.function.Supplier<VertexConsumer> cb = () -> vcp.getBuffer(RenderLayer.getEntityCutout(CARDBOARD));
        float yi = y1 - 0.004f;
        quad(cb.get(), e, x0, yi, z1, x0, yi, z0, x1, yi, z0, x1, yi, z1, 0, -1, 0, 0xFFB89268, light, overlay, 0, 0, w, d);
        // corrugated cardboard edge on the lower rim of the lid
        java.util.function.Supplier<VertexConsumer> edge = () -> vcp.getBuffer(RenderLayer.getEntityCutout(CARDBOARD_EDGE));
        float eh = (y1 - y0) * 0.14f;
        front(edge.get(), e, x0, y0, x1, y0 + eh, z0 - 0.0008f, 0xFFFFFFFF, light, overlay, 0, 0, (x1 - x0) / 0.08f, 1);
        quad(edge.get(), e, x0 - 0.0008f, y0, z0, x0 - 0.0008f, y0 + eh, z0, x0 - 0.0008f, y0 + eh, z1, x0 - 0.0008f, y0, z1, -1, 0, 0, 0xFFE0E0E0, light, overlay, 0, 0, (z1 - z0) / 0.08f, 1);
        quad(edge.get(), e, x1 + 0.0008f, y0, z1, x1 + 0.0008f, y0 + eh, z1, x1 + 0.0008f, y0 + eh, z0, x1 + 0.0008f, y0, z0, 1, 0, 0, 0xFFE0E0E0, light, overlay, 0, 0, (z1 - z0) / 0.08f, 1);
        // lid folds: crease lines along the top edges
        java.util.function.Supplier<VertexConsumer> solid = () -> vcp.getBuffer(RenderLayer.getEntityCutout(WHITE));
        float yt = y1 + 0.0009f, inset = (x1 - x0) * 0.035f, lw = 0.0035f;
        int crease = shade(col, 0.7f);
        quad(solid.get(), e, x0 + inset, yt, z0 + inset, x0 + inset, yt, z0 + inset + lw, x1 - inset, yt, z0 + inset + lw, x1 - inset, yt, z0 + inset, 0, 1, 0, crease, light, overlay, 0, 0, 1, 1);
        quad(solid.get(), e, x0 + inset, yt, z1 - inset - lw, x0 + inset, yt, z1 - inset, x1 - inset, yt, z1 - inset, x1 - inset, yt, z1 - inset - lw, 0, 1, 0, crease, light, overlay, 0, 0, 1, 1);
        quad(solid.get(), e, x0 + inset, yt, z0 + inset, x0 + inset, yt, z1 - inset, x0 + inset + lw, yt, z1 - inset, x0 + inset + lw, yt, z0 + inset, 0, 1, 0, crease, light, overlay, 0, 0, 1, 1);
        quad(solid.get(), e, x1 - inset - lw, yt, z0 + inset, x1 - inset - lw, yt, z1 - inset, x1 - inset, yt, z1 - inset, x1 - inset, yt, z0 + inset, 0, 1, 0, crease, light, overlay, 0, 0, 1, 1);
        // finger notch on the front flap
        float nw = (x1 - x0) * 0.12f;
        front(solid.get(), e, 0.5f - nw / 2, y0, 0.5f + nw / 2, y0 + (y1 - y0) * 0.38f, z0 - 0.0012f, shade(col, 0.4f), light, overlay);
        front(solid.get(), e, 0.5f - nw / 2 + 0.006f, y0 + (y1 - y0) * 0.38f, 0.5f + nw / 2 - 0.006f, y0 + (y1 - y0) * 0.46f, z0 - 0.0012f, shade(col, 0.4f), light, overlay);
        // pack art on the lid (with white frame) and color bands
        java.util.function.Supplier<VertexConsumer> art = () -> vcp.getBuffer(RenderLayer.getEntityCutout(packTextureForSet(set)));
        float ty = y1 + 0.0018f, ad = (z1 - z0) * 0.62f, aw = Math.min((x1 - x0) * 0.42f, ad * 1.3f);
        float acx = 0.5f, acz = (z0 + z1) / 2;
        quad(solid.get(), e, acx - aw / 2 - 0.012f, ty - 0.0004f, acz - ad / 2 - 0.012f, acx - aw / 2 - 0.012f, ty - 0.0004f, acz + ad / 2 + 0.012f,
                acx + aw / 2 + 0.012f, ty - 0.0004f, acz + ad / 2 + 0.012f, acx + aw / 2 + 0.012f, ty - 0.0004f, acz - ad / 2 - 0.012f, 0, 1, 0, 0xFFF8F8F8, light, overlay, 0, 0, 1, 1);
        quad(art.get(), e, acx - aw / 2, ty, acz - ad / 2, acx - aw / 2, ty, acz + ad / 2, acx + aw / 2, ty, acz + ad / 2, acx + aw / 2, ty, acz - ad / 2,
                0, 1, 0, 0xFFFFFFFF, light, overlay, 0, 0.22f, 1, 0.78f);
        quad(solid.get(), e, x0 + inset * 2, ty - 0.0004f, acz - 0.01f, x0 + inset * 2, ty - 0.0004f, acz + 0.01f, acx - aw / 2 - 0.03f, ty - 0.0004f, acz + 0.01f,
                acx - aw / 2 - 0.03f, ty - 0.0004f, acz - 0.01f, 0, 1, 0, 0xFFE8B730, light, overlay, 0, 0, 1, 1);
        quad(solid.get(), e, acx + aw / 2 + 0.03f, ty - 0.0004f, acz - 0.01f, acx + aw / 2 + 0.03f, ty - 0.0004f, acz + 0.01f, x1 - inset * 2, ty - 0.0004f, acz + 0.01f,
                x1 - inset * 2, ty - 0.0004f, acz - 0.01f, 0, 1, 0, 0xFFE8B730, light, overlay, 0, 0, 1, 1);
    }

    private static void mysteryBox(MatrixStack m, VertexConsumerProvider vcp, int light, int overlay, float size) {
        m.push();
        m.translate(-size / 2, -size / 2, -size / 2);
        cuboid(m, vcp, 0, 0, 0, size, size, size, 0xFF5B2A86, light, overlay);
        cuboid(m, vcp, size * 0.42f, -0.002f, -0.004f, size * 0.58f, size + 0.004f, size + 0.004f, 0xFFF2C230, light, overlay); // ribbon
        java.util.function.Supplier<VertexConsumer> vc = () -> vcp.getBuffer(RenderLayer.getEntityCutout(MYSTERY));
        front(vc.get(), m.peek(), size * 0.1f, size * 0.15f, size * 0.9f, size * 0.85f, -0.006f, 0xFFFFFFFF, light, overlay);
        m.pop();
    }

    /** A product centered at the origin, `size` blocks tall, facing -Z. */
    public static void product(ItemStack st, MatrixStack m, VertexConsumerProvider vcp, int light, int overlay, float size) {
        if (CardInfo.isCard(st)) {
            frameCard(st, m, vcp, light, overlay, size);
            return;
        } else if (CardInfo.isPack(st)) {
            Identifier tex = CardInfo.packTexture(CardInfo.id(st));
            if (Draw.textureExists(tex)) { image(m, vcp, tex, size * Draw.PACK_RATIO, size, light, overlay); return; }
        } else if (st.isOf(TcgBlocks.BOOSTER_BOX_ITEM)) {
            m.push();
            float bw = size * 1.15f;
            m.translate(-0.5 * bw, -bw * 0.31f, -0.5 * bw);
            m.scale(bw, bw, bw);
            drawBox(m, vcp, light, overlay, st.getOrDefault(TcgBlocks.BOX_SET, "mew151"), 1f, 0.62f, 0.8f);
            m.pop();
            return;
        } else if (st.isOf(TcgBlocks.MYSTERY_BOX)) {
            mysteryBox(m, vcp, light, overlay, size * 0.75f);
            return;
        }
        m.push();
        m.scale(size * 1.2f, size * 1.2f, size * 1.2f);
        MinecraftClient.getInstance().getItemRenderer().renderItem(st, ModelTransformationMode.FIXED, light, overlay, m, vcp, MinecraftClient.getInstance().world, 0);
        m.pop();
    }

    private static float productWidth(ItemStack st, float size) {
        return CardInfo.isCard(st) ? size * Draw.CARD_RATIO : CardInfo.isPack(st) ? size * Draw.PACK_RATIO : size;
    }

    // ------------------------------------------------------------------ displays

    public static class DisplayRenderer implements BlockEntityRenderer<ShopDisplayBlockEntity> {
        public DisplayRenderer(BlockEntityRendererFactory.Context ctx) {}

        @Override
        public boolean rendersOutsideBoundingBox(ShopDisplayBlockEntity be) { return true; }

        @Override
        public int getRenderDistance() { return 96; }

        @Override
        public void render(ShopDisplayBlockEntity be, float tickDelta, MatrixStack m, VertexConsumerProvider vcp, int light, int overlay) {
            BlockState state = be.getCachedState();
            if (!(state.getBlock() instanceof ShopDisplayBlock block) || state.get(ShopDisplayBlock.PART) != 0) return;
            DisplayKind kind = block.kind;
            Direction facing = state.get(ShopDisplayBlock.FACING);
            MinecraftClient mc = MinecraftClient.getInstance();
            BlockPos master = be.getPos();
            int hovered = -1;
            if (mc.crosshairTarget instanceof BlockHitResult bhr && mc.world != null) {
                BlockState hs = mc.world.getBlockState(bhr.getBlockPos());
                if (hs.getBlock() instanceof ShopDisplayBlock hb && hb.master(hs, bhr.getBlockPos()).equals(master))
                    hovered = block.slotFor(state, master, bhr, mc.player == null ? null : mc.player.getEyePos(), be); // same ray math as the server
            }
            float time = (be.getWorld() == null ? 0 : be.getWorld().getTime()) + tickDelta;
            SlotAnim anim = SlotAnim.of(be, time);
            // holding a product and allowed to place it here? -> marked slots and preview (off hand counts with the main hand empty)
            ItemStack held = mc.player == null ? ItemStack.EMPTY : mc.player.getMainHandStack();
            if (held.isEmpty() && mc.player != null && ShopServer.isProduct(mc.player.getOffHandStack())) held = mc.player.getOffHandStack();
            boolean canPlace = !held.isEmpty() && ShopServer.isProduct(held) && mc.player != null && mayPlace(be, mc.player.getUuid(), master);
            m.push();
            rotateToFacing(m, facing);
            facingDeg = switch (facing) { case SOUTH -> 180; case WEST -> 90; case EAST -> -90; default -> 0; };
            for (int i = 0; i < kind.slots.length && i < be.size(); i++) {
                ItemStack st = be.getStack(i);
                DisplayKind.Slot s = kind.slots[i];
                boolean hover = i == hovered;
                if (st.isEmpty()) {
                    if (canPlace) placeHint(m, vcp, kind, s, held, hover, time, be, i);
                    continue;
                }
                int sl = slotLight(be, kind, facing, s, light);
                float pa = anim.progress(i, time);
                float pop = (float) Draw.easeOutBack(pa), ease = (float) Draw.easeOutCubic(pa);
                float bump = anim.bump(i, time);
                float size = (float) s.size() * (hover ? 1.06f : 1f) * (0.55f + 0.45f * ease) * (1 + bump * 0.06f) * be.lookScale(i);
                float drop = (1 - pop) * 0.32f;
                boolean restock = canPlace && hover && ItemStack.areItemsAndComponentsEqual(st, held) && st.getCount() < st.getMaxCount();
                m.push();
                if (kind.layout == DisplayKind.Layout.BOXES) {
                    // box resting on its base
                    float bw = (float) s.size(), bh = bw * 0.62f, bd = bw * 0.8f;
                    float k0 = (0.6f + 0.4f * ease) * be.lookScale(i);
                    m.translate(s.x(), s.y() + (hover ? 0.03 : 0) + drop, s.z());
                    if (be.lookRot(i, 1) != 0) m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(be.lookRot(i, 1)));
                    m.scale(k0, k0, k0);
                    m.translate(-0.5 * bw, 0, -0.5 * bw);
                    m.scale(bw, bw, bw);
                    if (st.isOf(TcgBlocks.BOOSTER_BOX_ITEM)) {
                        String set = st.getOrDefault(TcgBlocks.BOX_SET, "mew151");
                        // more than one box: stacked behind, slightly offset
                        for (int k = Math.min(st.getCount(), 3) - 1; k >= 1; k--) {
                            m.push();
                            m.translate(0.04f * k, 0, 0.16f * k);
                            drawBox(m, vcp, sl, overlay, set, 1f, bh / bw, bd / bw, new BoxAnim(0, 0, 1, 0, time + i * 37 + k * 11));
                            m.pop();
                        }
                        drawBox(m, vcp, sl, overlay, set, 1f, bh / bw, bd / bw, new BoxAnim(0, 0, 1, 0, time + i * 37));
                    } else { m.translate(0.5, 0.3, 0.5); product(st, m, vcp, sl, overlay, 0.55f); }
                    m.pop();
                    if (hover) {
                        m.push();
                        m.translate(s.x(), s.y() + bh * 0.5, s.z() - bd / 2 - 0.02);
                        glow(m, vcp, bw + 0.04f, bh + 0.04f, restock ? 0x6BFF8A : 0xFFD34D, 0.3f + 0.15f * (float) Math.sin(time * 0.3));
                        m.pop();
                    }
                    if (pa < 1) sparkle(m, vcp, s.x(), s.y() + bh * 0.5, s.z() - bd / 2 - 0.03, bw, pa, time);
                    priceTag(m, vcp, be, i, st, s.x(), s.y() + 0.015, s.z() - bd / 2 - 0.012, 0, hover, s.y() + bh + 0.32, s.x(), s.z() - bd / 2 - 0.15, sl, restock ? held : null);
                    continue;
                }
                m.translate(s.x(), s.y() + drop, s.z());
                if (s.face() < 0) m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180));
                if (s.spin()) {
                    m.translate(0, Math.sin(time * 0.06) * 0.05, 0);
                    m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(time * 1.5f));
                } else if (pa < 1) m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((1 - ease) * 35f));
                m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(s.tilt()));
                if (be.hasLook(i)) { // size and rotation chosen by the owner (Edit menu)
                    m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(be.lookRot(i, 1)));
                    m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(be.lookRot(i, 0)));
                    m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(be.lookRot(i, 2)));
                }
                // a touch of natural messiness: each product slightly rotated (fixed per slot)
                if (!s.spin() && kind != DisplayKind.FRAME && kind != DisplayKind.CARD_WALL) {
                    float j = jitter(master, i);
                    m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(j * (CardInfo.isPack(st) ? 2.6f : 0.9f)));
                }
                int grade = dev.tcgshop.grading.Grading.grade(st);
                if (CardInfo.isCard(st) && grade == 0) {
                    // ungraded: clear case (graded ones bring their slab from ItemRendererMixin)
                    slab(m, vcp, st, size, sl, overlay, 0);
                    if (kind != DisplayKind.CARD_WALL && kind != DisplayKind.FRAME && !s.spin()) standFoot(m, vcp, size, sl, overlay);
                } else if (CardInfo.isCard(st) && kind != DisplayKind.FRAME && !s.spin() && kind != DisplayKind.CARD_WALL) standFoot(m, vcp, size * 1.05f, sl, overlay);
                if (hover) {
                    m.push();
                    m.translate(0, 0, -0.01);
                    int col = restock ? 0xFF6BFF8A : CardInfo.isCard(st) ? Rarity.of(st).color : 0xFFFFD34D;
                    glow(m, vcp, productWidth(st, size), size, col, 0.35f + 0.2f * (float) Math.sin(time * 0.3));
                    m.pop();
                    m.translate(0, 0, -0.03);
                }
                if (!hover && CardInfo.isCard(st)) { // premium showcase: rare cards glow softly
                    Rarity rr = Rarity.of(st);
                    if (rr.tier >= 2) {
                        m.push();
                        m.translate(0, 0, 0.02);
                        glow(m, vcp, productWidth(st, size) * 1.15f, size * 1.15f, rr.color, (rr.tier >= 3 ? 0.24f : 0.13f) + 0.06f * (float) Math.sin(time * 0.08 + i));
                        m.pop();
                    }
                }
                stackBehind(st, m, vcp, sl, overlay, size, kind.layout == DisplayKind.Layout.LYING);
                product(st, m, vcp, sl, overlay, size);
                if (pa < 1) {
                    m.push();
                    m.translate(0, 0, -0.02);
                    flash(m, vcp, productWidth(st, size), size, pa);
                    m.pop();
                }
                m.pop();
                if (pa < 1) sparkle(m, vcp, s.x(), s.y(), s.face() < 0 ? s.z() + 0.06 : s.z() - 0.06, (float) s.size(), pa, time);
                // price tag
                ItemStack rs = restock ? held : null;
                if (kind.layout == DisplayKind.Layout.SPIN) {
                    if (hover) priceTag(m, vcp, be, i, st, 0, 0, 0, 0, true, s.y() + s.size() / 2 + 0.25, s.x(), s.z() - 0.6, sl, rs);
                } else if (kind.layout == DisplayKind.Layout.LYING) {
                    priceTag(m, vcp, be, i, st, s.x(), 0.5, 0.012, 0, hover, 1.45, s.x(), s.z() - 0.35, sl, rs);
                } else {
                    double tz = s.face() < 0 ? s.z() + 0.07 : s.z() - 0.07, ty = s.y() - s.size() / 2 - 0.045;
                    if (kind == DisplayKind.COUNTER) { ty = 0.32; tz = 0.088; }
                    else if (kind == DisplayKind.LEDGE) { ty = 0.16; tz = 0.305; }
                    priceTag(m, vcp, be, i, st, s.x(), ty, tz, s.face() < 0 ? 180 : 0, hover,
                            s.y() + s.size() / 2 + 0.2, s.x(), s.face() < 0 ? s.z() + 0.45 : s.z() - 0.45, sl, rs);
                }
            }
            // pedestal light beam
            if (kind == DisplayKind.PEDESTAL && !be.getStack(0).isEmpty()) {
                java.util.function.Supplier<VertexConsumer> beam = () -> vcp.getBuffer(RenderLayer.getEntityTranslucentEmissive(WHITE));
                float a = 0.16f + 0.06f * (float) Math.sin(time * 0.1);
                int c = ((int) (a * 255) << 24) | 0xFFF2B0;
                for (int k = 0; k < 2; k++) {
                    m.push();
                    m.translate(0.5, 0, 0.5);
                    m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(45 + k * 90));
                    MatrixStack.Entry e = m.peek();
                    quad(beam.get(), e, -0.4f, 1.02f, 0, -0.08f, 2.7f, 0, 0.08f, 2.7f, 0, 0.4f, 1.02f, 0, 0, 0, -1, c, FULL, overlay, 0, 0, 1, 1);
                    quad(beam.get(), e, 0.4f, 1.02f, 0, 0.08f, 2.7f, 0, -0.08f, 2.7f, 0, -0.4f, 1.02f, 0, 0, 0, 1, c, FULL, overlay, 0, 0, 1, 1);
                    m.pop();
                }
            }
            m.pop();
        }

        /** Light of the block the product is in (on tall furniture each shelf has its own). */
        private static int slotLight(ShopDisplayBlockEntity be, DisplayKind kind, Direction facing, DisplayKind.Slot s, int fallback) {
            if (be.getWorld() == null) return fallback;
            Vec3d rel = DisplayKind.toWorld(facing, new Vec3d(s.x(), s.y(), s.face() < 0 ? s.z() + 0.3 : s.z() - 0.3));
            BlockPos p = BlockPos.ofFloored(be.getPos().getX() + rel.x, be.getPos().getY() + rel.y, be.getPos().getZ() + rel.z);
            return Math.max(fallback & 0xFFFF, WorldRenderer.getLightmapCoordinates(be.getWorld(), p) & 0xFFFF)
                    | Math.max(fallback >>> 16, WorldRenderer.getLightmapCoordinates(be.getWorld(), p) >>> 16) << 16;
        }

        /**
         * Card case, centered on the card (which is `size` tall at z = 0 and faces -Z). No transparent layers
         * in front of the card (they clipped the image): back plate, raised acrylic edges around the
         * window and, if graded, the CobbleGrade label (brand, name, grade, certificate and big score).
         */
        public static void slab(MatrixStack m, VertexConsumerProvider vcp, ItemStack st, float size, int light, int overlay, int grade) {
            float cw = size * Draw.CARD_RATIO, ch = size;
            float side = size * (grade > 0 ? 0.07f : 0.045f), labelH = grade > 0 ? size * 0.24f : 0, gap = size * 0.03f;
            float x0 = -cw / 2 - side, x1 = cw / 2 + side, y0 = -ch / 2 - side;
            float winTop = ch / 2, labelY0 = winTop + gap, labelY1 = labelY0 + labelH, y1 = (grade > 0 ? labelY1 : winTop) + side;
            float t = size * (grade > 0 ? 0.045f : 0.03f), zb = size * 0.012f;
            int rail = grade > 0 ? 0xFFDCE5F0 : 0xFFE4ECF5, back = grade > 0 ? 0xFFC9D5E3 : 0xFFD5DFEA, bevel = 0xFF9FB2C8;
            // back plate
            cuboid(m, vcp, x0, y0, zb, x1, y1, zb + t * 0.6f, back, light, overlay);
            // edges around the card window (they do not cover the card)
            float wx0 = -cw / 2 - size * 0.006f, wx1 = cw / 2 + size * 0.006f, wy0 = -ch / 2 - size * 0.006f, wy1 = winTop + size * 0.006f;
            cuboid(m, vcp, x0, y0, -t, wx0, y1, zb, rail, light, overlay);
            cuboid(m, vcp, wx1, y0, -t, x1, y1, zb, rail, light, overlay);
            cuboid(m, vcp, wx0, y0, -t, wx1, wy0, zb, rail, light, overlay);
            cuboid(m, vcp, wx0, wy1, -t, wx1, y1, zb, rail, light, overlay);
            // dark bevel on the window edge
            MatrixStack.Entry e = m.peek();
            java.util.function.Supplier<VertexConsumer> solid = () -> vcp.getBuffer(RenderLayer.getEntityCutout(WHITE));
            float zf = -t - 0.0006f, bw = size * 0.008f;
            front(solid.get(), e, wx0 - bw, wy0 - bw, wx1 + bw, wy0, zf, bevel, light, overlay);
            front(solid.get(), e, wx0 - bw, wy1, wx1 + bw, wy1 + bw, zf, bevel, light, overlay);
            front(solid.get(), e, wx0 - bw, wy0, wx0, wy1, zf, bevel, light, overlay);
            front(solid.get(), e, wx1, wy0, wx1 + bw, wy1, zf, bevel, light, overlay);
            // acrylic shine: light line on the top-left edge
            front(solid.get(), e, x0 + side * 0.25f, y1 - side * 0.45f, x1 - side * 0.25f, y1 - side * 0.3f, zf, 0xFFFFFFFF, light, overlay);
            front(solid.get(), e, x1 - side * 0.45f, y0 + side * 0.3f, x1 - side * 0.3f, y1 - side * 0.3f, zf, 0xFFFFFFFF, light, overlay);
            if (grade <= 0) return;
            // label
            int col = dev.tcgshop.grading.Grading.color(grade) | 0xFF000000;
            float lx0 = wx0, lx1 = wx1, lh = labelY1 - labelY0, zl = zf - 0.0006f;
            front(solid.get(), e, lx0, labelY0, lx1, labelY1, zl, 0xFFFAFAF7, light, overlay);
            front(solid.get(), e, lx0, labelY1 - lh * 0.16f, lx1, labelY1, zl - 0.0004f, col, light, overlay);
            front(solid.get(), e, lx0, labelY0, lx1, labelY0 + lh * 0.05f, zl - 0.0004f, col, light, overlay);
            float boxW = (lx1 - lx0) * 0.27f;
            // +x is the viewer's left: texts on the left, score on the right (like a real slab)
            front(solid.get(), e, lx0 + lh * 0.06f, labelY0 + lh * 0.1f, lx0 + boxW, labelY1 - lh * 0.22f, zl - 0.0004f, col, light, overlay);
            float textW = (lx1 - lx0) - boxW - lh * 0.15f, tcx = -(lx1 - lh * 0.08f - textW / 2);
            TextRenderer tr = MinecraftClient.getInstance().textRenderer;
            Text brand = Text.literal("COBBLEGRADE").formatted(Formatting.BOLD);
            Text cname = Text.literal(st.getName().getString());
            Text label = Text.translatable(dev.tcgshop.grading.Grading.labelKey(grade));
            Text cert = Text.literal("#" + String.format("%07d", Math.max(0, dev.tcgshop.grading.Grading.cert(st))));
            labelText(m, vcp, Text.literal("COBBLEGRADE").formatted(Formatting.BOLD), -tcx, labelY1 - lh * 0.145f, zl - 0.0008f,
                    Math.min(lh * 0.12f / 8f, textW / Math.max(1, tr.getWidth(brand))), 0xFFFFFFFF, light);
            labelText(m, vcp, cname, -tcx, labelY0 + lh * 0.48f, zl - 0.0008f, Math.min(lh * 0.16f / 8f, textW / Math.max(1, tr.getWidth(cname))), 0xFF1E2A44, light);
            labelText(m, vcp, label, -tcx, labelY0 + lh * 0.26f, zl - 0.0008f, Math.min(lh * 0.13f / 8f, textW / Math.max(1, tr.getWidth(label))), 0xFF4A5468, light);
            labelText(m, vcp, cert, -tcx, labelY0 + lh * 0.09f, zl - 0.0008f, Math.min(lh * 0.1f / 8f, textW / Math.max(1, tr.getWidth(cert))), 0xFF8A93A6, light);
            Text num = Text.literal(dev.tcgshop.grading.Grading.format(grade)).formatted(Formatting.BOLD);
            float bh = labelY1 - lh * 0.22f - (labelY0 + lh * 0.1f), bcx = lx0 + boxW / 2 + lh * 0.03f;
            labelText(m, vcp, num, bcx, labelY0 + lh * 0.1f + bh * 0.2f, zl - 0.0008f, Math.min(bh * 0.62f / 8f, (boxW * 0.85f) / Math.max(1, tr.getWidth(num))), 0xFFFFFFFF, light);
        }

        /** Label text centered at (x, y) in slab space (text baseline at y), facing -Z. */
        private static void labelText(MatrixStack m, VertexConsumerProvider vcp, Text t, float x, float y, float z, float scale, int color, int light) {
            m.push();
            m.translate(x, y + scale * 8f, z);
            text(m, vcp, t, scale, color, 0, light);
            m.pop();
        }

        /** Acrylic stand under the card (vertical displays). */
        private static void standFoot(MatrixStack m, VertexConsumerProvider vcp, float size, int light, int overlay) {
            float cw = size * Draw.CARD_RATIO, y = -size / 2 - size * 0.045f;
            cuboid(m, vcp, -cw * 0.42f, y - size * 0.05f, -size * 0.07f, cw * 0.42f, y, size * 0.09f, 0xFFE9F0F8, light, overlay);
            cuboid(m, vcp, -cw * 0.36f, y - size * 0.056f, -size * 0.062f, cw * 0.36f, y - size * 0.05f, size * 0.082f, 0xFFB9C8DA, light, overlay);
        }

        private static int Ui_darken(int argb) {
            int r = Math.max(0, ((argb >> 16) & 0xFF) - 70), g = Math.max(0, ((argb >> 8) & 0xFF) - 70), b = Math.max(0, (argb & 0xFF) - 70);
            return (r << 16) | (g << 8) | b;
        }

        // ------------------------------------------------------------------ placement: animation, slots and preview

        /** Remembers what was in each slot to animate what was just placed or restocked (client only). */
        private static final class SlotAnim {
            private static final java.util.Map<ShopDisplayBlockEntity, SlotAnim> ALL = new java.util.WeakHashMap<>();
            ItemStack[] last;
            float[] placedAt, bumpAt;

            static SlotAnim of(ShopDisplayBlockEntity be, float time) {
                SlotAnim a = ALL.computeIfAbsent(be, k -> new SlotAnim());
                int n = be.size();
                boolean first = a.last == null;
                if (first || a.last.length != n) {
                    a.last = new ItemStack[n];
                    a.placedAt = new float[n];
                    a.bumpAt = new float[n];
                    java.util.Arrays.fill(a.placedAt, -1000);
                    java.util.Arrays.fill(a.bumpAt, -1000);
                    for (int i = 0; i < n; i++) a.last[i] = be.getStack(i).copy();
                    return a; // nothing is animated when the world loads
                }
                for (int i = 0; i < n; i++) {
                    ItemStack now = be.getStack(i), was = a.last[i];
                    if (now.isEmpty() && was.isEmpty()) continue;
                    if (was.isEmpty() || !ItemStack.areItemsAndComponentsEqual(now, was)) {
                        if (!now.isEmpty()) a.placedAt[i] = time;
                        a.last[i] = now.copy();
                    } else if (now.getCount() != was.getCount()) {
                        if (now.getCount() > was.getCount()) a.bumpAt[i] = time;
                        a.last[i] = now.copy();
                    }
                }
                return a;
            }

            /** 0 just placed .. 1 settled (9 ticks). */
            float progress(int i, float time) {
                return MathHelper.clamp((time - placedAt[i]) / 9f, 0, 1);
            }

            /** Small hop when restocking (1 .. 0 over 6 ticks). */
            float bump(int i, float time) {
                float b = (time - bumpAt[i]) / 6f;
                return b < 0 || b > 1 ? 0 : (float) Math.sin(b * Math.PI);
            }
        }

        /** Can this player place products on the furniture? (home display: its owner; shop display: the shop owner). */
        private static boolean mayPlace(ShopDisplayBlockEntity be, java.util.UUID me, BlockPos master) {
            if (be.isHome()) return be.owner == null || be.owner.equals(me);
            dev.tcgshop.client.ShopClient.ZoneInfo z = dev.tcgshop.client.ShopClient.zoneAt(Vec3d.ofCenter(master));
            return z != null && z.owner().equals(me);
        }

        /** Random but fixed rotation of each slot (-1 .. 1). */
        private static float jitter(BlockPos p, int slot) {
            long h = p.asLong() * 31 + slot * 0x9E3779B97F4A7C15L;
            h ^= h >>> 29;
            h *= 0xBF58476D1CE4E5B9L;
            h ^= h >>> 32;
            return ((h & 0xFFFF) / 32767.5f) - 1f;
        }

        /** Several units in the same slot: shown behind (packs in a row toward the back, cards fanned out). */
        private static void stackBehind(ItemStack st, MatrixStack m, VertexConsumerProvider vcp, int light, int overlay, float size, boolean lying) {
            int extra = Math.min(st.getCount() - 1, CardInfo.isPack(st) ? 5 : 3);
            if (extra <= 0) return;
            Identifier tex = CardInfo.isPack(st) ? CardInfo.packTexture(CardInfo.id(st)) : CardInfo.isCard(st) ? CardInfo.texture(CardInfo.id(st)) : null;
            if (tex == null || !Draw.textureExists(tex)) return;
            float w = productWidth(st, size);
            for (int k = extra; k >= 1; k--) {
                m.push();
                if (CardInfo.isPack(st)) m.translate(0.006f * ((k % 2) * 2 - 1), 0.012f * k, 0.028f * k);
                else if (lying) m.translate(0.018f * k, -0.014f * k, 0.004f * k);
                else m.translate(0.022f * k, 0.004f * k, 0.012f * k);
                m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((CardInfo.isPack(st) ? 1.5f : -2.5f) * k));
                int shadeC = shade(0xFFFFFFFF, 0.86f - k * 0.05f);
                VertexConsumer vc = vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(tex));
                front(vc, m.peek(), -w / 2, -size / 2, w / 2, size / 2, 0, shadeC, light, overlay);
                m.pop();
            }
        }

        /** White flash that fades out when the product finishes being placed. */
        private static void flash(MatrixStack m, VertexConsumerProvider vcp, float w, float h, float p) {
            float a = (1 - p) * 0.55f;
            if (a <= 0.01f) return;
            VertexConsumer vc = vcp.getBuffer(RenderLayer.getEntityTranslucentEmissive(WHITE));
            front(vc, m.peek(), -w / 2, -h / 2, w / 2, h / 2, 0, ((int) (a * 255) << 24) | 0xFFFFFF, FULL, OverlayTexture.DEFAULT_UV);
        }

        /** Gold sparks that come out of the product when it is placed. */
        private static void sparkle(MatrixStack m, VertexConsumerProvider vcp, double x, double y, double z, float size, float p, float time) {
            VertexConsumer vc = vcp.getBuffer(RenderLayer.getEntityTranslucentEmissive(WHITE));
            float a = 1 - p;
            for (int k = 0; k < 10; k++) {
                double ang = k * Math.PI * 2 / 10 + k * 0.7;
                float rad = size * (0.25f + 0.55f * (float) Draw.easeOutCubic(p)) * (0.7f + 0.3f * ((k * 37) % 10) / 10f);
                float px = (float) (x + Math.cos(ang) * rad), py = (float) (y + Math.sin(ang) * rad * 0.9f);
                float s = 0.025f * a * (k % 3 == 0 ? 1.5f : 1f);
                int col = ((int) (a * 230) << 24) | (k % 2 == 0 ? 0xFFE27A : 0xFFFFFF);
                m.push();
                m.translate(px, py, z);
                m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(45 + time * 9 + k * 20));
                front(vc, m.peek(), -s, -s, s, s, 0, col, FULL, OverlayTexture.DEFAULT_UV);
                m.pop();
            }
        }

        /**
         * Free slot while holding a product: marked corners and, on the targeted slot, a translucent
         * preview of the product with its suggested price.
         */
        private static void placeHint(MatrixStack m, VertexConsumerProvider vcp, DisplayKind kind, DisplayKind.Slot s, ItemStack held,
                                      boolean hover, float time, ShopDisplayBlockEntity be, int slot) {
            MinecraftClient mc = MinecraftClient.getInstance();
            boolean boxes = kind.layout == DisplayKind.Layout.BOXES;
            float size = (float) s.size(), w = boxes ? size : productWidth(held, size), h = boxes ? size * 0.62f : size;
            m.push();
            if (boxes) m.translate(s.x(), s.y() + h / 2, s.z() - size * 0.4f - 0.01f);
            else {
                m.translate(s.x(), s.y(), s.z());
                if (s.face() < 0) m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180));
                m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(s.tilt()));
                if (kind.layout == DisplayKind.Layout.LYING) m.translate(0, 0, -0.01);
                else m.translate(0, 0, -0.012);
            }
            float pulse = 0.5f + 0.5f * (float) Math.sin(time * 0.25f);
            VertexConsumer vc = vcp.getBuffer(RenderLayer.getEntityTranslucentEmissive(WHITE));
            int col = hover ? 0x7CFFB0 : 0xFFFFFF;
            int a = (int) ((hover ? 0.75f : 0.22f + 0.12f * pulse) * 255);
            float cl = Math.min(w, h) * 0.28f, th = hover ? 0.014f : 0.009f;
            float x0 = -w / 2, x1 = w / 2, y0 = -h / 2, y1 = h / 2;
            // corners
            MatrixStack.Entry e = m.peek();
            int c = (a << 24) | col;
            front(vc, e, x0, y1 - th, x0 + cl, y1, 0, c, FULL, OverlayTexture.DEFAULT_UV);
            front(vc, e, x0, y1 - cl, x0 + th, y1, 0, c, FULL, OverlayTexture.DEFAULT_UV);
            front(vc, e, x1 - cl, y1 - th, x1, y1, 0, c, FULL, OverlayTexture.DEFAULT_UV);
            front(vc, e, x1 - th, y1 - cl, x1, y1, 0, c, FULL, OverlayTexture.DEFAULT_UV);
            front(vc, e, x0, y0, x0 + cl, y0 + th, 0, c, FULL, OverlayTexture.DEFAULT_UV);
            front(vc, e, x0, y0, x0 + th, y0 + cl, 0, c, FULL, OverlayTexture.DEFAULT_UV);
            front(vc, e, x1 - cl, y0, x1, y0 + th, 0, c, FULL, OverlayTexture.DEFAULT_UV);
            front(vc, e, x1 - th, y0, x1, y0 + cl, 0, c, FULL, OverlayTexture.DEFAULT_UV);
            // soft fill
            front(vc, e, x0 + th, y0 + th, x1 - th, y1 - th, 0.001f, ((int) ((hover ? 0.10f : 0.04f) * 255) << 24) | col, FULL, OverlayTexture.DEFAULT_UV);
            if (hover && !boxes) {
                // translucent product preview
                Identifier tex = CardInfo.isPack(held) ? CardInfo.packTexture(CardInfo.id(held)) : CardInfo.isCard(held) ? CardInfo.texture(CardInfo.id(held)) : null;
                if (tex != null && Draw.textureExists(tex)) {
                    float bob = 0.012f * (float) Math.sin(time * 0.2f);
                    VertexConsumer gv = vcp.getBuffer(RenderLayer.getEntityTranslucent(tex));
                    float k = 0.94f;
                    front(gv, e, -w / 2 * k, -h / 2 * k + bob, w / 2 * k, h / 2 * k + bob, -0.004f, ((int) ((0.5f + 0.15f * pulse) * 255) << 24) | 0xFFFFFF, FULL, OverlayTexture.DEFAULT_UV);
                }
            }
            m.pop();
            if (hover) {
                // floating hint: what you are placing and at what price
                double hx = s.x(), hy = boxes ? s.y() + size * 0.62f + 0.3 : s.y() + size / 2 + 0.22, hz = s.face() < 0 ? s.z() + 0.45 : s.z() - 0.45;
                m.push();
                m.translate(hx, hy, hz);
                m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-facingDeg));
                m.multiply(mc.getEntityRenderDispatcher().getRotation());
                int n = held.getMaxCount() > 1 ? held.getCount() : 1;
                Text what = n > 1 ? Text.literal(n + "× ").append(held.getName()) : held.getName().copy();
                facingText(m, vcp, Text.translatable("tcgshop.display.place_here").formatted(Formatting.BOLD), 0.0095f, 0xFF7CFFB0, 0, 0);
                facingText(m, vcp, what.copy().formatted(Formatting.WHITE), 0.0085f, 0xFFFFFFFF, -0.1f, 0);
                if (!be.isHome())
                    facingText(m, vcp, Text.translatable("tcgshop.display.suggested", PriceService.format(ShopServer.priceOf(held, 1.0))), 0.0085f, 0xFFFFD34D, -0.19f, 0);
                m.pop();
            }
        }

        /** Current furniture rotation (to undo it on camera-facing texts). */
        private static float facingDeg;

        /**
         * Paper price tag stuck to the furniture (with the unit count if there are several) or, if you target the product,
         * name + price + hint floating in front and always facing the camera. With the same product in hand: "restock".
         */
        private static void priceTag(MatrixStack m, VertexConsumerProvider vcp, ShopDisplayBlockEntity be, int i, ItemStack st,
                                     double tx, double ty, double tz, float rotY, boolean hover, double hy, double hx, double hz, int light,
                                     @org.jetbrains.annotations.Nullable ItemStack restock) {
            MinecraftClient mc = MinecraftClient.getInstance();
            m.push();
            boolean home = be.isHome();
            if (hover && (home || restock != null)) {
                m.translate(hx, hy, hz);
                m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-facingDeg));
                m.multiply(mc.getEntityRenderDispatcher().getRotation());
                Text name = st.getCount() > 1 ? Text.literal(st.getCount() + "× ").append(st.getName()) : st.getName().copy();
                facingText(m, vcp, name.copy().formatted(Formatting.WHITE), 0.0105f, 0xFFFFFFFF, 0, 0);
                if (restock != null) {
                    int add = Math.min(restock.getCount(), st.getMaxCount() - st.getCount());
                    facingText(m, vcp, Text.translatable("tcgshop.display.restock", add).formatted(Formatting.BOLD), 0.0095f, 0xFF7CFFB0, -0.115f, 0);
                } else facingText(m, vcp, Text.translatable("tcgshop.home.hover"), 0.0075f, 0xFFB8F5B8, -0.11f, 0);
            } else if (home) {
                // no price tag
            } else if (hover) {
                m.translate(hx, hy, hz);
                m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-facingDeg)); // back to world axes
                m.multiply(mc.getEntityRenderDispatcher().getRotation());
                Text hint = ShopServer.needsCashier(st) ? Text.translatable("tcgshop.display.hint_cashier") : Text.translatable("tcgshop.display.hint");
                Text name = st.getCount() > 1 ? Text.literal(st.getCount() + "× ").append(st.getName()) : st.getName().copy();
                facingText(m, vcp, name.copy().formatted(Formatting.WHITE), 0.0105f, 0xFFFFFFFF, 0, 0);
                facingText(m, vcp, Text.literal(PriceService.format(be.price(i))).formatted(Formatting.BOLD), 0.0145f, 0xFFFFD34D, -0.125f, 0);
                facingText(m, vcp, hint, 0.0075f, 0xFFB8F5B8, -0.235f, 0);
            } else {
                m.translate(tx, ty, tz);
                if (rotY != 0) m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(rotY));
                paperTag(m, vcp, PriceService.format(be.price(i)), st.getCount(), CardInfo.isCard(st) ? Rarity.of(st).color : 0xFFE8B730, light);
            }
            m.pop();
        }

        /** Paper tag: small white card with a color stripe, price in black and, if there are several units, "×N". */
        private static void paperTag(MatrixStack m, VertexConsumerProvider vcp, String price, int count, int accent, int light) {
            TextRenderer tr = MinecraftClient.getInstance().textRenderer;
            float sc = 0.0052f;
            String cnt = count > 1 ? " ×" + count : "";
            float tw = (tr.getWidth(price) + (cnt.isEmpty() ? 0 : tr.getWidth(cnt))) * sc;
            float w = tw + 0.05f, h = 0.068f, y0 = -0.012f;
            // card (slightly offset from the furniture) with shadow
            cuboid(m, vcp, -w / 2 + 0.004f, y0 - 0.004f, 0.0015f, w / 2 + 0.004f, y0 + h - 0.004f, 0.003f, 0xFF2A2A2A, light, OverlayTexture.DEFAULT_UV);
            cuboid(m, vcp, -w / 2, y0, -0.002f, w / 2, y0 + h, 0.0015f, 0xFFF8F5EC, light, OverlayTexture.DEFAULT_UV);
            VertexConsumer vc = vcp.getBuffer(RenderLayer.getEntityCutout(WHITE));
            front(vc, m.peek(), w / 2 - 0.014f, y0, w / 2, y0 + h, -0.0026f, accent | 0xFF000000, light, OverlayTexture.DEFAULT_UV);
            front(vc, m.peek(), -w / 2, y0 + h - 0.006f, w / 2 - 0.014f, y0 + h, -0.0026f, 0xFFE6E0D0, light, OverlayTexture.DEFAULT_UV);
            m.push();
            m.translate(-0.005f, y0 + (h + 8 * sc) / 2, -0.0032f);
            m.scale(-sc, -sc, sc);
            float x = -(tr.getWidth(price) + tr.getWidth(cnt)) / 2f;
            tr.draw(price, x, 0, 0xFF1E2A44, false, m.peek().getPositionMatrix(), vcp, TextRenderer.TextLayerType.POLYGON_OFFSET, 0, light);
            if (!cnt.isEmpty()) tr.draw(cnt, x + tr.getWidth(price), 0, 0xFF8A6A10, false, m.peek().getPositionMatrix(), vcp, TextRenderer.TextLayerType.POLYGON_OFFSET, 0, light);
            m.pop();
        }

        /** Centered camera-facing text (like entity names), visible through blocks. */
        private static void facingText(MatrixStack m, VertexConsumerProvider vcp, Text t, float scale, int color, float dy, int unused) {
            TextRenderer tr = MinecraftClient.getInstance().textRenderer;
            m.push();
            m.translate(0, dy, 0);
            m.scale(scale, -scale, scale);
            float x = -tr.getWidth(t) / 2f;
            Matrix4f mat = m.peek().getPositionMatrix();
            tr.draw(t, x, 0, (color & 0xFFFFFF) | 0x60000000, false, mat, vcp, TextRenderer.TextLayerType.SEE_THROUGH, 0xB0000000, FULL);
            tr.draw(t, x, 0, color, false, mat, vcp, TextRenderer.TextLayerType.NORMAL, 0, FULL);
            m.pop();
        }
    }

    // ------------------------------------------------------------------ booster box (block)

    public static class BoxRenderer implements BlockEntityRenderer<BoosterBox.BoxEntity> {
        public BoxRenderer(BlockEntityRendererFactory.Context ctx) {}

        @Override
        public boolean rendersOutsideBoundingBox(BoosterBox.BoxEntity be) { return be.openStart >= 0; }

        @Override
        public void render(BoosterBox.BoxEntity be, float tickDelta, MatrixStack m, VertexConsumerProvider vcp, int light, int overlay) {
            float now = (be.getWorld() == null ? 0 : be.getWorld().getTime()) + tickDelta;
            BoxAnim anim;
            if (be.openStart >= 0) {
                float t = now - be.openStart;
                anim = new BoxAnim(MathHelper.clamp((t - 12) / 18f, 0, 1), MathHelper.clamp((t - 30) / 40f, 0, 1),
                        1 - MathHelper.clamp(t / 12f, 0, 1), t < 14 ? 1 - t / 14f : 0, now);
            } else anim = new BoxAnim(0, 0, 1, 0, now + be.getPos().hashCode() % 200);
            m.push();
            rotateToFacing(m, be.getCachedState().get(BoosterBox.BoxBlock.FACING));
            drawBox(m, vcp, light, overlay, be.set, BoosterBox.W, BoosterBox.H, BoosterBox.D, anim);
            m.pop();
        }
    }

    // ------------------------------------------------------------------ animated decoration

    public static class DecorRenderer implements BlockEntityRenderer<DecorBlocks.DecorEntity> {
        public DecorRenderer(BlockEntityRendererFactory.Context ctx) {}

        @Override
        public void render(DecorBlocks.DecorEntity be, float tickDelta, MatrixStack m, VertexConsumerProvider vcp, int light, int overlay) {
            BlockState state = be.getCachedState();
            if (!state.contains(Properties.HORIZONTAL_FACING)) return;
            float now = (be.getWorld() == null ? 0 : be.getWorld().getTime()) + tickDelta;
            float since = now - be.animStart;
            m.push();
            rotateToFacing(m, state.get(Properties.HORIZONTAL_FACING));
            if (state.isOf(TcgBlocks.POSTER)) poster(be, m, vcp, light, overlay);
            else if (state.isOf(TcgBlocks.PLAY_TABLE)) table(be, m, vcp, light, overlay, since);
            else if (state.isOf(TcgBlocks.CASH_REGISTER)) register(m, vcp, light, overlay, since, now);
            else if (state.isOf(TcgBlocks.ROULETTE)) roulette(be, m, vcp, light, overlay, since, now);
            m.pop();
        }

        private void poster(DecorBlocks.DecorEntity be, MatrixStack m, VertexConsumerProvider vcp, int light, int overlay) {
            m.push();
            m.translate(0.5, 0.5, 0.925);
            image(m, vcp, packTextureForSet(be.set), 0.52f, 0.92f, light, overlay);
            m.pop();
        }

        private void table(DecorBlocks.DecorEntity be, MatrixStack m, VertexConsumerProvider vcp, int light, int overlay, float since) {
            if (be.board.size() == 8) { // match in progress: large Active and each player's Bench
                for (int side = 0; side < 2; side++) {
                    for (int k = 0; k < 4; k++) {
                        String id = be.board.get(side * 4 + k);
                        Identifier iid = Identifier.tryParse(id);
                        if (id.isEmpty() || iid == null || !Registries.ITEM.containsId(iid)) continue;
                        m.push();
                        double x = k == 0 ? 0.5 : 0.2 + (k - 1) * 0.3, z = k == 0 ? (side == 0 ? 0.36 : 0.64) : (side == 0 ? 0.13 : 0.87);
                        m.translate(x, 0.82, z);
                        m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90));
                        if (side == 1) m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(180));
                        frameCard(new ItemStack(Registries.ITEM.get(iid)), m, vcp, light, overlay, k == 0 ? 0.3f : 0.2f);
                        m.pop();
                    }
                }
                cuboid(m, vcp, 0.06f, 0.815f, 0.42f, 0.16f, 0.88f, 0.58f, 0xFF22408F, light, overlay);
                cuboid(m, vcp, 0.84f, 0.815f, 0.42f, 0.94f, 0.88f, 0.58f, 0xFF8F2222, light, overlay);
                return;
            }
            List<ItemStack> cards = tableCards(be.getPos().asLong() + be.value);
            float shuffle = since < 30 ? (float) Math.sin(since * 0.5) * (1 - since / 30f) : 0;
            for (int i = 0; i < cards.size(); i++) {
                int side = i < 3 ? 0 : 1;
                int k = i % 3;
                m.push();
                m.translate(0.28 + k * 0.22 + (side == 0 ? shuffle * 0.05 : -shuffle * 0.05), 0.82, side == 0 ? 0.22 : 0.78);
                m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90));
                if (side == 1) m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(180));
                frameCard(cards.get(i), m, vcp, light, overlay, 0.22f);
                m.pop();
            }
            // decks
            cuboid(m, vcp, 0.06f, 0.815f, 0.12f, 0.16f, 0.86f, 0.26f, 0xFF22408F, light, overlay);
            cuboid(m, vcp, 0.84f, 0.815f, 0.74f, 0.94f, 0.86f, 0.88f, 0xFF8F2222, light, overlay);
        }

        private static final Map<Long, List<ItemStack>> TABLE_CACHE = new HashMap<>();

        private static List<ItemStack> tableCards(long seed) {
            return TABLE_CACHE.computeIfAbsent(seed, s -> {
                List<Identifier> all = new ArrayList<>();
                for (Identifier id : Registries.ITEM.getIds()) if (CardInfo.isCard(id)) all.add(id);
                java.util.Random r = new java.util.Random(s);
                List<ItemStack> out = new ArrayList<>();
                for (int i = 0; i < 6 && !all.isEmpty(); i++) out.add(new ItemStack(Registries.ITEM.get(all.get(r.nextInt(all.size())))));
                return out;
            });
        }

        private void register(MatrixStack m, VertexConsumerProvider vcp, int light, int overlay, float since, float now) {
            float open = since < 8 ? since / 8f : since < 30 ? 1 : since < 38 ? 1 - (since - 30) / 8f : 0;
            float dz = open * 0.22f;
            cuboid(m, vcp, 0.16f, 0.02f, 0.2f - dz, 0.84f, 0.16f, 0.62f - dz, 0xFF3A3F4A, light, overlay); // drawer
            if (open > 0.3f) {
                for (int i = 0; i < 4; i++) cuboid(m, vcp, 0.24f + i * 0.14f, 0.16f, 0.26f - dz, 0.34f + i * 0.14f, 0.18f, 0.36f - dz,
                        i % 2 == 0 ? 0xFFF2C230 : 0xFFD8D8D8, light, overlay);
            }
            m.push();
            m.translate(0.5, 0.56, 0.585);
            m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-15));
            int pulse = since < 40 ? 0xFFFFD34D : 0xFF6BE07B;
            text(m, vcp, Text.literal(since < 40 ? "₽ ₽ ₽" : "TCG"), 0.008f, pulse, 0, FULL);
            m.pop();
        }

        private void roulette(DecorBlocks.DecorEntity be, MatrixStack m, VertexConsumerProvider vcp, int light, int overlay, float since, float now) {
            int idx = 0;
            for (int i = 0; i < Roulette.WHEEL.length; i++) if (Roulette.WHEEL[i] == be.value) idx = i;
            float sector = 360f / 37;
            float target = 360 * 6 + 270 - idx * sector; // the winning number ends up in front of the pointer (north)
            float p = MathHelper.clamp(since / 100f, 0, 1);
            float ease = 1 - (float) Math.pow(1 - p, 3);
            float angle = be.value < 0 ? now * 0.5f : target * ease;
            m.push();
            m.translate(0.5, 0.885, 0.5);
            m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-angle));
            java.util.function.Supplier<VertexConsumer> vc = () -> vcp.getBuffer(RenderLayer.getEntityCutout(WHITE));
            MatrixStack.Entry e = m.peek();
            float r0 = 0.12f, r1 = 0.42f;
            for (int i = 0; i < 37; i++) {
                int n = Roulette.WHEEL[i];
                int col = n == 0 ? 0xFF1E8C3A : Roulette.RED.contains(n) ? 0xFFC62828 : 0xFF1B1B1F;
                double a0 = Math.toRadians(i * sector - sector / 2), a1 = Math.toRadians(i * sector + sector / 2);
                float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
                quad(vc.get(), e, c0 * r0, 0, s0 * r0, c0 * r1, 0, s0 * r1, c1 * r1, 0, s1 * r1, c1 * r0, 0, s1 * r0, 0, 1, 0, col, light, overlay, 0, 0, 1, 1);
            }
            cuboid(m, vcp, -r0, -0.005f, -r0, r0, 0.03f, r0, 0xFFC8A040, light, overlay); // gold axle
            m.pop();
            float ballA = p >= 1 ? 0 : (float) Math.toRadians(now * 9);
            cuboid(m, vcp, 0.5f + (float) Math.sin(ballA) * 0.33f - 0.02f, 0.89f, 0.5f - (float) Math.cos(ballA) * 0.33f - 0.02f,
                    0.5f + (float) Math.sin(ballA) * 0.33f + 0.02f, 0.93f, 0.5f - (float) Math.cos(ballA) * 0.33f + 0.02f, 0xFFFFFFFF, light, overlay);
            cuboid(m, vcp, 0.48f, 0.88f, 0.03f, 0.52f, 0.96f, 0.08f, 0xFFF2C230, light, overlay); // pointer
        }
    }
}

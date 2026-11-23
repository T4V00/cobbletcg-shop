package dev.tcgshop.casino.client;

import dev.tcgshop.casino.CasinoBlockEntity;
import dev.tcgshop.casino.CasinoGame;
import dev.tcgshop.casino.CasinoMachineBlock;
import dev.tcgshop.casino.RouletteMath;
import dev.tcgshop.casino.SlotMath;
import dev.tcgshop.casino.WheelMath;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;

/**
 * In-world animation of the casino machines, driven by the synced last outcome (CasinoBlockEntity.result /
 * animStart): slot reels rolling and stopping, the roulette wheel and its ball, the fortune wheel turning, and
 * the blackjack totals on the felt. Static parts are block models (tools/gen_casino_assets.py).
 */
public class CasinoRenderer implements BlockEntityRenderer<CasinoBlockEntity> {
    private static final Identifier WHITE = Identifier.ofVanilla("textures/block/white_concrete.png");
    private static final int FULL = 0xF000F0;
    private static final String[] GLYPH = {"●", "●", "▲", "BAR", "★", "7", "♦"};
    private static final int[] GLYPH_COLOR = {0xFFD3122A, 0xFFFFD600, 0xFFE09B00, 0xFF101010, 0xFFFFC928, 0xFFE8132B, 0xFF4FC3F7};

    public CasinoRenderer(BlockEntityRendererFactory.Context ctx) {}

    @Override
    public boolean rendersOutsideBoundingBox(CasinoBlockEntity be) {
        return be.game() == CasinoGame.WHEEL;
    }

    @Override
    public void render(CasinoBlockEntity be, float tickDelta, MatrixStack m, VertexConsumerProvider vcp, int light, int overlay) {
        if (be.getWorld() == null || !(be.getCachedState().getBlock() instanceof CasinoMachineBlock b)) return;
        float now = be.getWorld().getTime() + tickDelta;
        float since = now - be.animStart;
        m.push();
        rotateToFacing(m, be.getCachedState().get(CasinoMachineBlock.FACING).asRotation());
        switch (b.game) {
            case SLOTS, VIP_SLOTS -> slots(be, m, vcp, light, overlay, since, now, b.game == CasinoGame.VIP_SLOTS);
            case ROULETTE -> roulette(be, m, vcp, light, overlay, since, now);
            case WHEEL -> wheel(be, m, vcp, light, overlay, since, now);
            case BLACKJACK -> blackjack(be, m, vcp, light, since);
        }
        m.pop();
    }

    /** Model rotation of the blockstate (north = 0, then clockwise like "y"). */
    private static void rotateToFacing(MatrixStack m, float facingDeg) {
        m.translate(0.5, 0, 0.5);
        // asRotation: south 0, west 90, north 180, east 270; the models face north at y = 0
        m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180 - facingDeg));
        m.translate(-0.5, 0, -0.5);
    }

    // ------------------------------------------------------------------ primitives

    private static void quad(VertexConsumer vc, MatrixStack.Entry e, float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz, float nx, float ny, float nz, int argb, int light, int overlay) {
        Matrix4f mat = e.getPositionMatrix();
        int a = (argb >>> 24) & 0xFF, r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, bl = argb & 0xFF;
        float e1x = bx - ax, e1y = by - ay, e1z = bz - az, e2x = cx - ax, e2y = cy - ay, e2z = cz - az;
        float gx = e1y * e2z - e1z * e2y, gy = e1z * e2x - e1x * e2z, gz = e1x * e2y - e1y * e2x;
        if (gx * nx + gy * ny + gz * nz >= 0) {
            vc.vertex(mat, ax, ay, az).color(r, g, bl, a).texture(0, 1).overlay(overlay).light(light).normal(e, nx, ny, nz);
            vc.vertex(mat, bx, by, bz).color(r, g, bl, a).texture(0, 0).overlay(overlay).light(light).normal(e, nx, ny, nz);
            vc.vertex(mat, cx, cy, cz).color(r, g, bl, a).texture(1, 0).overlay(overlay).light(light).normal(e, nx, ny, nz);
            vc.vertex(mat, dx, dy, dz).color(r, g, bl, a).texture(1, 1).overlay(overlay).light(light).normal(e, nx, ny, nz);
        } else {
            vc.vertex(mat, ax, ay, az).color(r, g, bl, a).texture(0, 1).overlay(overlay).light(light).normal(e, nx, ny, nz);
            vc.vertex(mat, dx, dy, dz).color(r, g, bl, a).texture(1, 1).overlay(overlay).light(light).normal(e, nx, ny, nz);
            vc.vertex(mat, cx, cy, cz).color(r, g, bl, a).texture(1, 0).overlay(overlay).light(light).normal(e, nx, ny, nz);
            vc.vertex(mat, bx, by, bz).color(r, g, bl, a).texture(0, 0).overlay(overlay).light(light).normal(e, nx, ny, nz);
        }
    }

    private static VertexConsumer solid(VertexConsumerProvider vcp) {
        return vcp.getBuffer(RenderLayer.getEntityCutout(WHITE));
    }

    /** Rectangle facing north (-Z) at depth z. */
    private static void front(VertexConsumerProvider vcp, MatrixStack m, float x0, float y0, float x1, float y1, float z, int argb, int light, int overlay) {
        quad(solid(vcp), m.peek(), x1, y0, z, x1, y1, z, x0, y1, z, x0, y0, z, 0, 0, -1, argb, light, overlay);
    }

    private static void cuboid(VertexConsumerProvider vcp, MatrixStack m, float x0, float y0, float z0, float x1, float y1, float z1, int argb, int light, int overlay) {
        MatrixStack.Entry e = m.peek();
        quad(solid(vcp), e, x1, y0, z0, x1, y1, z0, x0, y1, z0, x0, y0, z0, 0, 0, -1, argb, light, overlay);
        quad(solid(vcp), e, x0, y0, z1, x0, y1, z1, x1, y1, z1, x1, y0, z1, 0, 0, 1, argb, light, overlay);
        quad(solid(vcp), e, x0, y0, z0, x0, y1, z0, x0, y1, z1, x0, y0, z1, -1, 0, 0, argb, light, overlay);
        quad(solid(vcp), e, x1, y0, z1, x1, y1, z1, x1, y1, z0, x1, y0, z0, 1, 0, 0, argb, light, overlay);
        quad(solid(vcp), e, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, 0, 1, 0, argb, light, overlay);
        quad(solid(vcp), e, x0, y0, z1, x0, y0, z0, x1, y0, z0, x1, y0, z1, 0, -1, 0, argb, light, overlay);
    }

    /** Text on a north-facing plane, centered at the current origin. */
    private static void text(MatrixStack m, VertexConsumerProvider vcp, Text t, float scale, int color, int light) {
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        m.push();
        m.scale(-scale, -scale, scale);
        float w = tr.getWidth(t);
        tr.draw(t, -w / 2, -4, color, false, m.peek().getPositionMatrix(), vcp, TextRenderer.TextLayerType.POLYGON_OFFSET, 0, light);
        m.pop();
    }

    // ------------------------------------------------------------------ slot machine

    /**
     * Reel window of the model: x 3.5..12.5 px, y 8.5..12.5 px, on the cabinet face at z = 4 px. Each reel shows its
     * symbols rolling down; reel i stops 25 + 10 i ticks after the spin.
     */
    private void slots(CasinoBlockEntity be, MatrixStack m, VertexConsumerProvider vcp, int light, int overlay, float since, float now, boolean vip) {
        float z = 3.95f / 16, y0 = 8.5f / 16, y1 = 12.5f / 16, rw = 3f / 16;
        int[] res = be.result.length == 3 ? be.result : new int[]{SlotMath.SEVEN, SlotMath.SEVEN, SlotMath.SEVEN};
        boolean win = SlotMath.multiplier(res) > 0 && since > 45 && since < 200;
        for (int r = 0; r < 3; r++) {
            float x0 = 3.5f / 16 + r * rw;
            front(vcp, m, x0 + 0.004f, y0, x0 + rw - 0.004f, y1, z, 0xFFF2F2F2, FULL, overlay);
            float stopAt = 25 + 10 * r;
            boolean rolling = since >= 0 && since < stopAt;
            int[] strip = SlotMath.strip(r);
            int sym;
            float off = 0;
            if (rolling) {
                float pos = now * 0.9f + r * 7;
                sym = strip[Math.floorMod((int) pos, strip.length)];
                off = (pos - (float) Math.floor(pos)) * (y1 - y0);
            } else sym = res[r];
            m.push();
            m.translate(x0 + rw / 2, (y0 + y1) / 2 - off + (rolling ? (y1 - y0) / 2 : 0), z - 0.002f);
            if (!rolling || off < (y1 - y0) * 0.8f)
                text(m, vcp, Text.literal(GLYPH[sym]).formatted(sym == SlotMath.BAR ? Formatting.BOLD : Formatting.RESET),
                        sym == SlotMath.BAR ? 0.0075f : 0.018f, GLYPH_COLOR[sym], FULL);
            m.pop();
        }
        // marquee lights over the reels (chase; all on while showing a win)
        int lights = 9;
        for (int i = 0; i < lights; i++) {
            boolean on = win ? ((int) (now / 3)) % 2 == 0 : ((int) (now / 2) + i) % 3 == 0;
            float lx = 3.2f / 16 + i * (9.6f / 16) / (lights - 1);
            int col = on ? (vip ? 0xFFFFE082 : 0xFFFFF59D) : 0xFF5A4A20;
            cuboid(vcp, m, lx - 0.012f, 14.2f / 16, 2.6f / 16, lx + 0.012f, 14.45f / 16, 2.75f / 16, col, on ? FULL : light, overlay);
        }
        if (win) {
            m.push();
            m.translate(0.5, 17.5 / 16, 3.5 / 16);
            text(m, vcp, Text.literal("WIN!").formatted(Formatting.BOLD), 0.012f, 0xFFFFD34D, FULL);
            m.pop();
        }
    }

    // ------------------------------------------------------------------ roulette

    private void roulette(CasinoBlockEntity be, MatrixStack m, VertexConsumerProvider vcp, int light, int overlay, float since, float now) {
        int n = be.result.length > 0 ? be.result[0] : -1;
        int idx = n < 0 ? 0 : RouletteMath.wheelIndex(n);
        float sector = 360f / 37;
        float p = MathHelper.clamp(since / 110f, 0, 1);
        float ease = 1 - (float) Math.pow(1 - p, 3);
        float target = 360 * 5 - idx * sector;
        float angle = n < 0 || since < 0 ? now * 0.6f : target * ease + Math.max(0, since - 110) * 0.6f;
        float cx = 0.5f, cz = 0.62f, y = 14.05f / 16;
        m.push();
        m.translate(cx, y, cz);
        m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-angle));
        MatrixStack.Entry e = m.peek();
        float r0 = 0.1f, r1 = 0.3f;
        for (int i = 0; i < 37; i++) {
            int num = RouletteMath.WHEEL[i];
            int col = num == 0 ? 0xFF1E8C3A : RouletteMath.RED.contains(num) ? 0xFFC62828 : 0xFF1B1B1F;
            double a0 = Math.toRadians(i * sector - sector / 2 - 90), a1 = Math.toRadians(i * sector + sector / 2 - 90);
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            quad(solid(vcp), e, c0 * r0, 0, s0 * r0, c0 * r1, 0, s0 * r1, c1 * r1, 0, s1 * r1, c1 * r0, 0, s1 * r0, 0, 1, 0, col, light, overlay);
        }
        cuboid(vcp, m, -r0, -0.004f, -r0, r0, 0.025f, r0, 0xFFC8A040, light, overlay);
        cuboid(vcp, m, -0.015f, 0.025f, -0.015f, 0.015f, 0.07f, 0.015f, 0xFFE0C060, light, overlay);
        m.pop();
        // ball: circles the rim against the wheel, then drops into the winning pocket (which stops at the front, -Z)
        if (n < 0) return;
        double pocket = Math.toRadians(idx * sector - 90 + angle);
        float ballA = (float) (since < 0 ? pocket : pocket + (1 - ease) * Math.PI * 2 * 8);
        float br = p < 0.75f ? 0.29f : 0.29f - Math.min(1f, (p - 0.75f) / 0.25f) * 0.08f;
        float bx = cx + (float) Math.cos(ballA) * br, bz = cz + (float) Math.sin(ballA) * br;
        cuboid(vcp, m, bx - 0.016f, y + 0.005f, bz - 0.016f, bx + 0.016f, y + 0.037f, bz + 0.016f, 0xFFFFFFFF, light, overlay);
        if (p >= 1 && since < 300) {
            m.push();
            m.translate(cx, y + 0.25f, cz);
            int col = n == 0 ? 0xFF4CAF50 : RouletteMath.RED.contains(n) ? 0xFFFF5252 : 0xFFFFFFFF;
            text(m, vcp, Text.literal(String.valueOf(n)).formatted(Formatting.BOLD), 0.015f, col, FULL);
            m.pop();
        }
    }

    // ------------------------------------------------------------------ fortune wheel

    /** Vertical wheel in front of the model's back plate: centre (0.5, 1.0, 0.31), radius 0.47 blocks. */
    private void wheel(CasinoBlockEntity be, MatrixStack m, VertexConsumerProvider vcp, int light, int overlay, float since, float now) {
        int seg = be.result.length > 0 ? be.result[0] : -1;
        float step = 360f / WheelMath.SEGMENTS;
        float p = MathHelper.clamp(since / 100f, 0, 1);
        float ease = 1 - (float) Math.pow(1 - p, 3);
        float angle = seg < 0 ? 0 : (360 * 4 - seg * step) * ease;
        float cx = 0.5f, cy = 16.5f / 16, z = 4.9f / 16, R = 0.46f, r0 = 0.12f;
        m.push();
        m.translate(cx, cy, z);
        m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(angle));
        MatrixStack.Entry e = m.peek();
        for (int i = 0; i < WheelMath.SEGMENTS; i++) {
            int col = WheelScreen.COLORS[WheelMath.LAYOUT[i]];
            // segment i centred at angle -i*step from the top (clockwise seen from the front)
            double a0 = Math.toRadians(90 + i * step - step / 2), a1 = Math.toRadians(90 + i * step + step / 2);
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            quad(solid(vcp), e, c0 * r0, s0 * r0, 0, c0 * R, s0 * R, 0, c1 * R, s1 * R, 0, c1 * r0, s1 * r0, 0, 0, 0, -1, col, light, overlay);
        }
        cuboid(vcp, m, -r0, -r0, -0.02f, r0, r0, 0.005f, 0xFF2A1640, light, overlay);
        cuboid(vcp, m, -0.03f, -0.03f, -0.035f, 0.03f, 0.03f, -0.015f, 0xFFFFD34D, light, overlay);
        m.pop();
        // clapper at the top
        cuboid(vcp, m, cx - 0.025f, cy + R - 0.04f, z - 0.05f, cx + 0.025f, cy + R + 0.06f, z - 0.01f, 0xFFFFD34D, light, overlay);
        if (seg >= 0 && p >= 1 && since < 300) {
            m.push();
            m.translate(cx, cy + R + 0.18f, z - 0.02f);
            int sym = WheelMath.LAYOUT[seg];
            text(m, vcp, sym >= 5 ? Text.literal(sym == 5 ? "JOKER" : "★").formatted(Formatting.BOLD) : Text.literal(WheelMath.NAMES[sym]).formatted(Formatting.BOLD),
                    0.012f, WheelScreen.COLORS[sym] | 0xFF000000, FULL);
            m.pop();
        }
    }

    // ------------------------------------------------------------------ blackjack

    /** Last hand on the felt: the player's and the dealer's totals for a while after each action. */
    private void blackjack(CasinoBlockEntity be, MatrixStack m, VertexConsumerProvider vcp, int light, float since) {
        if (be.result.length < 2 || since < 0 || since > 400) return;
        int player = be.result[0], dealer = be.result[1];
        m.push();
        m.translate(0.5, 13.1 / 16, 0.3);
        m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90)); // lying on the felt, readable from the front
        text(m, vcp, Text.literal(String.valueOf(player)).formatted(Formatting.BOLD), 0.012f, player > 21 ? 0xFFFF5252 : 0xFFFFFFFF, light);
        if (dealer > 0) {
            m.translate(0, 0.42, 0);
            text(m, vcp, Text.literal(String.valueOf(dealer)).formatted(Formatting.BOLD), 0.012f, dealer > 21 ? 0xFFFF5252 : 0xFFFFD34D, light);
        }
        m.pop();
    }
}

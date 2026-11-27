package dev.tcgshop.casino.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.tcgshop.casino.CasinoBlockEntity;
import dev.tcgshop.casino.CasinoGame;
import dev.tcgshop.casino.CasinoMachineBlock;
import dev.tcgshop.casino.RouletteMath;
import dev.tcgshop.casino.SlotMath;
import dev.tcgshop.casino.WheelMath;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * In-world animation of the casino machines, driven by the synced last outcome (CasinoBlockEntity.result /
 * animStart): slot reels rolling and stopping, the roulette wheel and its ball, the fortune wheel turning, and
 * the blackjack totals on the felt. Static parts are block models (tools/gen_casino_assets.py).
 */
public class CasinoRenderer implements BlockEntityRenderer<CasinoBlockEntity> {
    private static final ResourceLocation WHITE = ResourceLocation.withDefaultNamespace("textures/block/white_concrete.png");
    private static final int FULL = 0xF000F0;
    private static final String[] GLYPH = {"●", "●", "▲", "BAR", "★", "7", "♦"};
    private static final int[] GLYPH_COLOR = {0xFFD3122A, 0xFFFFD600, 0xFFE09B00, 0xFF101010, 0xFFFFC928, 0xFFE8132B, 0xFF4FC3F7};

    public CasinoRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public boolean shouldRenderOffScreen(CasinoBlockEntity be) {
        return be.game() == CasinoGame.WHEEL;
    }

    @Override
    public void render(CasinoBlockEntity be, float tickDelta, PoseStack m, MultiBufferSource vcp, int light, int overlay) {
        if (be.getLevel() == null || !(be.getBlockState().getBlock() instanceof CasinoMachineBlock b)) return;
        float now = be.getLevel().getGameTime() + tickDelta;
        float since = now - be.animStart;
        m.pushPose();
        rotateToFacing(m, be.getBlockState().getValue(CasinoMachineBlock.FACING).toYRot());
        switch (b.game) {
            case SLOTS, VIP_SLOTS -> slots(be, m, vcp, light, overlay, since, now, b.game == CasinoGame.VIP_SLOTS);
            case ROULETTE -> roulette(be, m, vcp, light, overlay, since, now);
            case WHEEL -> wheel(be, m, vcp, light, overlay, since, now);
            case BLACKJACK -> blackjack(be, m, vcp, light, since);
            case DICE -> dice(be, m, vcp, light, overlay, since, now);
            case COIN -> coin(be, m, vcp, light, overlay, since, now);
        }
        m.popPose();
    }

    /** Model rotation of the blockstate (north = 0, then clockwise like "y"). */
    private static void rotateToFacing(PoseStack m, float facingDeg) {
        m.translate(0.5, 0, 0.5);
        // asRotation: south 0, west 90, north 180, east 270; the models face north at y = 0
        m.mulPose(Axis.YP.rotationDegrees(180 - facingDeg));
        m.translate(-0.5, 0, -0.5);
    }

    // ------------------------------------------------------------------ primitives

    private static void quad(VertexConsumer vc, PoseStack.Pose e, float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz, float nx, float ny, float nz, int argb, int light, int overlay) {
        Matrix4f mat = e.pose();
        int a = (argb >>> 24) & 0xFF, r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, bl = argb & 0xFF;
        float e1x = bx - ax, e1y = by - ay, e1z = bz - az, e2x = cx - ax, e2y = cy - ay, e2z = cz - az;
        float gx = e1y * e2z - e1z * e2y, gy = e1z * e2x - e1x * e2z, gz = e1x * e2y - e1y * e2x;
        if (gx * nx + gy * ny + gz * nz >= 0) {
            vc.addVertex(mat, ax, ay, az).setColor(r, g, bl, a).setUv(0, 1).setOverlay(overlay).setLight(light).setNormal(e, nx, ny, nz);
            vc.addVertex(mat, bx, by, bz).setColor(r, g, bl, a).setUv(0, 0).setOverlay(overlay).setLight(light).setNormal(e, nx, ny, nz);
            vc.addVertex(mat, cx, cy, cz).setColor(r, g, bl, a).setUv(1, 0).setOverlay(overlay).setLight(light).setNormal(e, nx, ny, nz);
            vc.addVertex(mat, dx, dy, dz).setColor(r, g, bl, a).setUv(1, 1).setOverlay(overlay).setLight(light).setNormal(e, nx, ny, nz);
        } else {
            vc.addVertex(mat, ax, ay, az).setColor(r, g, bl, a).setUv(0, 1).setOverlay(overlay).setLight(light).setNormal(e, nx, ny, nz);
            vc.addVertex(mat, dx, dy, dz).setColor(r, g, bl, a).setUv(1, 1).setOverlay(overlay).setLight(light).setNormal(e, nx, ny, nz);
            vc.addVertex(mat, cx, cy, cz).setColor(r, g, bl, a).setUv(1, 0).setOverlay(overlay).setLight(light).setNormal(e, nx, ny, nz);
            vc.addVertex(mat, bx, by, bz).setColor(r, g, bl, a).setUv(0, 0).setOverlay(overlay).setLight(light).setNormal(e, nx, ny, nz);
        }
    }

    private static VertexConsumer solid(MultiBufferSource vcp) {
        return vcp.getBuffer(RenderType.entityCutout(WHITE));
    }

    /** Rectangle facing north (-Z) at depth z. */
    private static void front(MultiBufferSource vcp, PoseStack m, float x0, float y0, float x1, float y1, float z, int argb, int light, int overlay) {
        quad(solid(vcp), m.last(), x1, y0, z, x1, y1, z, x0, y1, z, x0, y0, z, 0, 0, -1, argb, light, overlay);
    }

    private static void cuboid(MultiBufferSource vcp, PoseStack m, float x0, float y0, float z0, float x1, float y1, float z1, int argb, int light, int overlay) {
        PoseStack.Pose e = m.last();
        quad(solid(vcp), e, x1, y0, z0, x1, y1, z0, x0, y1, z0, x0, y0, z0, 0, 0, -1, argb, light, overlay);
        quad(solid(vcp), e, x0, y0, z1, x0, y1, z1, x1, y1, z1, x1, y0, z1, 0, 0, 1, argb, light, overlay);
        quad(solid(vcp), e, x0, y0, z0, x0, y1, z0, x0, y1, z1, x0, y0, z1, -1, 0, 0, argb, light, overlay);
        quad(solid(vcp), e, x1, y0, z1, x1, y1, z1, x1, y1, z0, x1, y0, z0, 1, 0, 0, argb, light, overlay);
        quad(solid(vcp), e, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, 0, 1, 0, argb, light, overlay);
        quad(solid(vcp), e, x0, y0, z1, x0, y0, z0, x1, y0, z0, x1, y0, z1, 0, -1, 0, argb, light, overlay);
    }

    /** Text on a north-facing plane, centered at the current origin. */
    private static void text(PoseStack m, MultiBufferSource vcp, Component t, float scale, int color, int light) {
        Font tr = Minecraft.getInstance().font;
        m.pushPose();
        m.scale(-scale, -scale, scale);
        float w = tr.width(t);
        tr.drawInBatch(t, -w / 2, -4, color, false, m.last().pose(), vcp, Font.DisplayMode.POLYGON_OFFSET, 0, light);
        m.popPose();
    }

    // ------------------------------------------------------------------ slot machine

    /**
     * Reel window of the model: x 3.5..12.5 px, y 8.5..12.5 px, on the cabinet face at z = 4 px. Each reel shows its
     * symbols rolling down; reel i stops 25 + 10 i ticks after the spin.
     */
    private void slots(CasinoBlockEntity be, PoseStack m, MultiBufferSource vcp, int light, int overlay, float since, float now, boolean vip) {
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
            m.pushPose();
            m.translate(x0 + rw / 2, (y0 + y1) / 2 - off + (rolling ? (y1 - y0) / 2 : 0), z - 0.002f);
            if (!rolling || off < (y1 - y0) * 0.8f)
                text(m, vcp, Component.literal(GLYPH[sym]).withStyle(sym == SlotMath.BAR ? ChatFormatting.BOLD : ChatFormatting.RESET),
                        sym == SlotMath.BAR ? 0.0075f : 0.018f, GLYPH_COLOR[sym], FULL);
            m.popPose();
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
            m.pushPose();
            m.translate(0.5, 17.5 / 16, 3.5 / 16);
            text(m, vcp, Component.literal("WIN!").withStyle(ChatFormatting.BOLD), 0.012f, 0xFFFFD34D, FULL);
            m.popPose();
        }
    }

    // ------------------------------------------------------------------ roulette

    private void roulette(CasinoBlockEntity be, PoseStack m, MultiBufferSource vcp, int light, int overlay, float since, float now) {
        int n = be.result.length > 0 ? be.result[0] : -1;
        int idx = n < 0 ? 0 : RouletteMath.wheelIndex(n);
        float sector = 360f / 37;
        float p = Mth.clamp(since / 110f, 0, 1);
        float ease = 1 - (float) Math.pow(1 - p, 3);
        float target = 360 * 5 - idx * sector;
        float angle = n < 0 || since < 0 ? now * 0.6f : target * ease + Math.max(0, since - 110) * 0.6f;
        float cx = 0.5f, cz = 0.62f, y = 14.05f / 16;
        m.pushPose();
        m.translate(cx, y, cz);
        m.mulPose(Axis.YP.rotationDegrees(-angle));
        PoseStack.Pose e = m.last();
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
        m.popPose();
        // ball: circles the rim against the wheel, then drops into the winning pocket (which stops at the front, -Z)
        if (n < 0) return;
        double pocket = Math.toRadians(idx * sector - 90 + angle);
        float ballA = (float) (since < 0 ? pocket : pocket + (1 - ease) * Math.PI * 2 * 8);
        float br = p < 0.75f ? 0.29f : 0.29f - Math.min(1f, (p - 0.75f) / 0.25f) * 0.08f;
        float bx = cx + (float) Math.cos(ballA) * br, bz = cz + (float) Math.sin(ballA) * br;
        cuboid(vcp, m, bx - 0.016f, y + 0.005f, bz - 0.016f, bx + 0.016f, y + 0.037f, bz + 0.016f, 0xFFFFFFFF, light, overlay);
        if (p >= 1 && since < 300) {
            m.pushPose();
            m.translate(cx, y + 0.25f, cz);
            int col = n == 0 ? 0xFF4CAF50 : RouletteMath.RED.contains(n) ? 0xFFFF5252 : 0xFFFFFFFF;
            text(m, vcp, Component.literal(String.valueOf(n)).withStyle(ChatFormatting.BOLD), 0.015f, col, FULL);
            m.popPose();
        }
    }

    // ------------------------------------------------------------------ fortune wheel

    /** Vertical wheel in front of the model's back plate: centre (0.5, 1.0, 0.31), radius 0.47 blocks. */
    private void wheel(CasinoBlockEntity be, PoseStack m, MultiBufferSource vcp, int light, int overlay, float since, float now) {
        int seg = be.result.length > 0 ? be.result[0] : -1;
        float step = 360f / WheelMath.SEGMENTS;
        float p = Mth.clamp(since / 100f, 0, 1);
        float ease = 1 - (float) Math.pow(1 - p, 3);
        float angle = seg < 0 ? 0 : (360 * 4 - seg * step) * ease;
        float cx = 0.5f, cy = 16.5f / 16, z = 4.9f / 16, R = 0.46f, r0 = 0.12f;
        m.pushPose();
        m.translate(cx, cy, z);
        m.mulPose(Axis.ZP.rotationDegrees(angle));
        PoseStack.Pose e = m.last();
        for (int i = 0; i < WheelMath.SEGMENTS; i++) {
            int col = WheelScreen.COLORS[WheelMath.LAYOUT[i]];
            // segment i centred at angle -i*step from the top (clockwise seen from the front)
            double a0 = Math.toRadians(90 + i * step - step / 2), a1 = Math.toRadians(90 + i * step + step / 2);
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            quad(solid(vcp), e, c0 * r0, s0 * r0, 0, c0 * R, s0 * R, 0, c1 * R, s1 * R, 0, c1 * r0, s1 * r0, 0, 0, 0, -1, col, light, overlay);
        }
        cuboid(vcp, m, -r0, -r0, -0.02f, r0, r0, 0.005f, 0xFF2A1640, light, overlay);
        cuboid(vcp, m, -0.03f, -0.03f, -0.035f, 0.03f, 0.03f, -0.015f, 0xFFFFD34D, light, overlay);
        m.popPose();
        // clapper at the top
        cuboid(vcp, m, cx - 0.025f, cy + R - 0.04f, z - 0.05f, cx + 0.025f, cy + R + 0.06f, z - 0.01f, 0xFFFFD34D, light, overlay);
        if (seg >= 0 && p >= 1 && since < 300) {
            m.pushPose();
            m.translate(cx, cy + R + 0.18f, z - 0.02f);
            int sym = WheelMath.LAYOUT[seg];
            text(m, vcp, sym >= 5 ? Component.literal(sym == 5 ? "JOKER" : "★").withStyle(ChatFormatting.BOLD) : Component.literal(WheelMath.NAMES[sym]).withStyle(ChatFormatting.BOLD),
                    0.012f, WheelScreen.COLORS[sym] | 0xFF000000, FULL);
            m.popPose();
        }
    }

    // ------------------------------------------------------------------ 1.1: dice table

    /** Pip layout of a die face on a 3x3 grid (index = row * 3 + column). */
    private static final int[][] PIPS = {{4}, {0, 8}, {0, 4, 8}, {0, 2, 6, 8}, {0, 2, 4, 6, 8}, {0, 2, 3, 5, 6, 8}};

    /**
     * Two dice on the felt (top at y = 14 px): they tumble across the table for 30 ticks after a roll and settle showing
     * the result (CasinoBlockEntity.result = {d1, d2}). Idle: they rest showing 3 and 4.
     */
    private void dice(CasinoBlockEntity be, PoseStack m, MultiBufferSource vcp, int light, int overlay, float since, float now) {
        int d1 = be.result.length >= 2 ? be.result[0] : 3, d2 = be.result.length >= 2 ? be.result[1] : 4;
        float p = Mth.clamp(since / 30f, 0, 1), ease = 1 - (float) Math.pow(1 - p, 3);
        for (int k = 0; k < 2; k++) {
            int v = k == 0 ? d1 : d2;
            float tx = (k == 0 ? 6.2f : 9.4f) / 16, tz = (k == 0 ? 8.6f : 7.4f) / 16;
            float sx = 8f / 16, sz = 13.5f / 16; // thrown from the dealer's side (back)
            float x = sx + (tx - sx) * ease, z = sz + (tz - sz) * ease;
            float hop = p < 1 ? Math.abs(Mth.sin(p * 9.4f)) * (1 - p) * 0.12f : 0;
            float s = 1.6f / 16;
            m.pushPose();
            m.translate(x, 14f / 16 + s / 2 + hop, z);
            if (p < 1) { // tumbling
                m.mulPose(Axis.XP.rotationDegrees((1 - ease) * 720 + k * 40));
                m.mulPose(Axis.ZP.rotationDegrees((1 - ease) * 540 - k * 30));
            }
            m.mulPose(Axis.YP.rotationDegrees(k == 0 ? 12 : -18));
            die(vcp, m, s, v, light, overlay);
            m.popPose();
        }
    }

    /** A die centred at the origin, value on top (opposite faces add up to 7). */
    private static void die(MultiBufferSource vcp, PoseStack m, float s, int top, int light, int overlay) {
        float h = s / 2;
        cuboid(vcp, m, -h, -h, -h, h, h, h, 0xFFF6F2E8, light, overlay);
        int[] faces = {top, 7 - top, top == 1 || top == 6 ? 2 : 1, top == 1 || top == 6 ? 5 : 6, top == 3 || top == 4 ? 2 : 3, top == 3 || top == 4 ? 5 : 4};
        float pip = s * 0.17f, off = s * 0.27f, e = h + 0.0015f;
        for (int f = 0; f < 6; f++) {
            for (int idx : PIPS[faces[f] - 1]) {
                float a = (idx % 3 - 1) * off, b = (idx / 3 - 1) * off;
                int col = faces[f] == 1 ? 0xFFD3122A : 0xFF151515;
                switch (f) {
                    case 0 -> cuboid(vcp, m, a - pip / 2, e - 0.001f, b - pip / 2, a + pip / 2, e, b + pip / 2, col, light, overlay);
                    case 1 -> cuboid(vcp, m, a - pip / 2, -e, b - pip / 2, a + pip / 2, -e + 0.001f, b + pip / 2, col, light, overlay);
                    case 2 -> cuboid(vcp, m, a - pip / 2, b - pip / 2, -e, a + pip / 2, b + pip / 2, -e + 0.001f, col, light, overlay);
                    case 3 -> cuboid(vcp, m, a - pip / 2, b - pip / 2, e - 0.001f, a + pip / 2, b + pip / 2, e, col, light, overlay);
                    case 4 -> cuboid(vcp, m, -e, a - pip / 2, b - pip / 2, -e + 0.001f, a + pip / 2, b + pip / 2, col, light, overlay);
                    default -> cuboid(vcp, m, e - 0.001f, a - pip / 2, b - pip / 2, e, a + pip / 2, b + pip / 2, col, light, overlay);
                }
            }
        }
    }

    // ------------------------------------------------------------------ 1.1: coin toss

    /**
     * The big coin above the stand's plate (top at y = 14 px): after a flip it jumps and spins for 40 ticks, then lies
     * flat showing heads (gold star) or tails (silver ring), or stands on its edge. Idle: it turns slowly on its stand.
     */
    private void coin(CasinoBlockEntity be, PoseStack m, MultiBufferSource vcp, int light, int overlay, float since, float now) {
        int side = be.result.length > 0 ? be.result[0] : -1;
        float p = Mth.clamp(since / 40f, 0, 1);
        float r = 3.4f / 16, t = 0.5f / 16;
        m.pushPose();
        m.translate(0.5, 14f / 16, 0.5);
        if (side < 0 || since > 1200) { // idle: standing on its edge, turning
            m.translate(0, r + 0.01f, 0);
            m.mulPose(Axis.YP.rotationDegrees(now * 2));
            m.mulPose(Axis.XP.rotationDegrees(90));
        } else if (p < 1) {
            float hgt = 4 * p * (1 - p) * 0.55f;
            m.translate(0, hgt + t, 0);
            m.mulPose(Axis.XP.rotationDegrees(p * 360 * 6 + (side == 1 ? 180 : 0)));
        } else if (side == 2) {
            m.translate(0, r + 0.01f, 0);
            m.mulPose(Axis.XP.rotationDegrees(90));
        } else {
            m.translate(0, t / 2 + 0.002f, 0);
            if (side == 1) m.mulPose(Axis.XP.rotationDegrees(180));
        }
        coinBody(vcp, m, r, t, light, overlay);
        m.popPose();
    }

    /** Flat coin in the XZ plane centred at the origin: gold rim, heads (star) on +Y, tails (ring) on -Y. */
    private static void coinBody(MultiBufferSource vcp, PoseStack m, float r, float t, int light, int overlay) {
        int seg = 20;
        PoseStack.Pose e = m.last();
        for (int i = 0; i < seg; i++) {
            double a0 = i * Math.PI * 2 / seg, a1 = (i + 1) * Math.PI * 2 / seg;
            float c0 = (float) Math.cos(a0) * r, s0 = (float) Math.sin(a0) * r, c1 = (float) Math.cos(a1) * r, s1 = (float) Math.sin(a1) * r;
            int face = i % 2 == 0 ? 0xFFF2C230 : 0xFFE5B21E;
            quad(solid(vcp), e, 0, t / 2, 0, c0, t / 2, s0, c1, t / 2, s1, 0, t / 2, 0, 0, 1, 0, face, light, overlay);
            quad(solid(vcp), e, 0, -t / 2, 0, c1, -t / 2, s1, c0, -t / 2, s0, 0, -t / 2, 0, 0, -1, 0, 0xFFC9CED6, light, overlay);
            float nx = (float) Math.cos((a0 + a1) / 2), nz = (float) Math.sin((a0 + a1) / 2);
            quad(solid(vcp), e, c0, -t / 2, s0, c0, t / 2, s0, c1, t / 2, s1, c1, -t / 2, s1, nx, 0, nz, 0xFFB8860B, light, overlay);
        }
        // heads: a star of small blocks; tails: a darker ring
        float u = r * 0.12f;
        int[][] star = {{0, 0}, {0, 2}, {0, -2}, {2, 0}, {-2, 0}, {1, 1}, {-1, -1}, {1, -1}, {-1, 1}, {0, 3}};
        for (int[] s : star)
            cuboid(vcp, m, s[0] * u - u / 2, t / 2, s[1] * u - u / 2, s[0] * u + u / 2, t / 2 + 0.002f, s[1] * u + u / 2, 0xFFFFF3C0, light, overlay);
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI * 2 / 12;
            float x = (float) Math.cos(a) * r * 0.6f, z = (float) Math.sin(a) * r * 0.6f;
            cuboid(vcp, m, x - u / 2, -t / 2 - 0.002f, z - u / 2, x + u / 2, -t / 2, z + u / 2, 0xFF6E747C, light, overlay);
        }
    }

    // ------------------------------------------------------------------ blackjack

    /** Last hand on the felt: the player's and the dealer's totals for a while after each action. */
    private void blackjack(CasinoBlockEntity be, PoseStack m, MultiBufferSource vcp, int light, float since) {
        if (be.result.length < 2 || since < 0 || since > 400) return;
        int player = be.result[0], dealer = be.result[1];
        m.pushPose();
        m.translate(0.5, 13.1 / 16, 0.3);
        m.mulPose(Axis.XP.rotationDegrees(90)); // lying on the felt, readable from the front
        text(m, vcp, Component.literal(String.valueOf(player)).withStyle(ChatFormatting.BOLD), 0.012f, player > 21 ? 0xFFFF5252 : 0xFFFFFFFF, light);
        if (dealer > 0) {
            m.translate(0, 0.42, 0);
            text(m, vcp, Component.literal(String.valueOf(dealer)).withStyle(ChatFormatting.BOLD), 0.012f, dealer > 21 ? 0xFFFF5252 : 0xFFFFD34D, light);
        }
        m.popPose();
    }
}

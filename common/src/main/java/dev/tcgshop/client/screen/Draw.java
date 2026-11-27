package dev.tcgshop.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/** Drawing utilities for cards, packs, shines and cuts. */
public final class Draw {
    public static final float CARD_RATIO = 63f / 88f;
    /** Proportions of a real pack (≈ 6.6 x 11.8 cm). cobbletcg textures have different sizes: they are stretched to this. */
    public static final float PACK_RATIO = 0.56f;

    private static final Map<ResourceLocation, Boolean> TEXTURE_EXISTS = new HashMap<>();

    static {
        // 1.0: the card art option (original / official) changes which card pictures exist
        dev.masterball.api.client.art.CardArt.addListener(TEXTURE_EXISTS::clear);
    }

    private Draw() {}

    public static boolean textureExists(ResourceLocation id) {
        return TEXTURE_EXISTS.computeIfAbsent(id, i -> Minecraft.getInstance().getResourceManager().getResource(i).isPresent());
    }

    /**
     * Cuts a circle out of a texture (center uc,vc and radii ur,vr in 0..1 coordinates) and draws it with radius r at
     * (cx, cy): it is painted row by row with the chord width, so it comes out round without masks.
     */
    public static void circleTexture(GuiGraphics ctx, ResourceLocation tex, int cx, int cy, int r, float uc, float vc, float ur, float vr) {
        ctx.flush();
        final int TW = 1000, TH = 1000;
        for (int dy = -r; dy < r; dy++) {
            double yy = dy + 0.5;
            int half = (int) Math.round(Math.sqrt(Math.max(0, r * r - yy * yy)));
            if (half <= 0) continue;
            float v0 = vc + (dy / (float) r) * vr, vh = vr / r;
            float u0 = uc - (half / (float) r) * ur, uw = 2 * half / (float) r * ur;
            ctx.blit(tex, cx - half, cy + dy, 2 * half, 1, u0 * TW, v0 * TH, Math.max(1, Math.round(uw * TW)), Math.max(1, Math.round(vh * TH)), TW, TH);
        }
    }

    /** Draws a full texture stretched to the rectangle (any image size). */
    public static void texture(GuiGraphics ctx, ResourceLocation tex, float x, float y, float w, float h, float alpha) {
        // DrawContext rectangles and fills are batched and drawn at the end; the texture is drawn immediately.
        // Without flushing them first, a panel drawn BEFORE the card ended up on top (phone quick sell without art).
        ctx.flush();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1f, 1f, 1f, alpha);
        ctx.blit(tex, Math.round(x), Math.round(y), Math.round(w), Math.round(h), 0f, 0f, 1, 1, 1, 1);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    /** Pack: flat texture if it exists; otherwise (resource packs missing) the scaled item icon. */
    public static void pack(GuiGraphics ctx, ResourceLocation tex, ItemStack fallback, float x, float y, float w, float h, float alpha) {
        if (textureExists(tex)) {
            ctx.fill(Math.round(x) + 4, Math.round(y) + 6, Math.round(x + w) + 4, Math.round(y + h) + 6, withAlpha(0xFF000000, 0.4f * alpha));
            texture(ctx, tex, x, y, w, h, alpha);
        } else {
            ctx.fillGradient(Math.round(x), Math.round(y), Math.round(x + w), Math.round(y + h), withAlpha(0xFF3A3F6B, alpha), withAlpha(0xFF1D2040, alpha));
            ctx.fill(Math.round(x), Math.round(y), Math.round(x + w), Math.round(y + h * 0.07f), withAlpha(0xFFB0B4C8, alpha));
            PoseStack m = ctx.pose();
            m.pushPose();
            m.translate(x + w / 2, y + h / 2, 0);
            float s = w / 20f;
            m.scale(s, s, 1);
            ctx.renderItem(fallback, -8, -8);
            m.popPose();
        }
    }

    /**
     * Card face without effects (texture from the API image pack, or a placeholder with the icon). 1.0: in "original"
     * card art mode the API serves generated faces under the same ids (API CardArt), so every caller follows the option.
     */
    public static void cardFace(GuiGraphics ctx, ResourceLocation tex, ItemStack stack, float x, float y, float w, float h, float alpha) {
        if (textureExists(tex)) {
            texture(ctx, tex, x, y, w, h, alpha);
        } else {
            ctx.fillGradient(Math.round(x), Math.round(y), Math.round(x + w), Math.round(y + h), withAlpha(0xFFEFE6C8, alpha), withAlpha(0xFFD2C49A, alpha));
            PoseStack m = ctx.pose();
            m.pushPose();
            m.translate(x + w / 2, y + h * 0.38f, 0);
            float s = w / 28f;
            m.scale(s, s, 1);
            ctx.renderItem(stack, -8, -8);
            m.popPose();
        }
    }

    /**
     * Card back centered on the current origin. 1.0: the back of the current card art mode (API CardArt.back(): the jar's
     * own MasterBall back in "original" mode; the official &lt;image folder&gt;/back of cards/card_back.png in "official"
     * mode when present), the same one the card items use. The back drawn in code below is only a fallback.
     */
    public static void cardBack(GuiGraphics ctx, float w, float h, float alpha, int glowColor, double t) {
        int x0 = Math.round(-w / 2), y0 = Math.round(-h / 2), iw = Math.round(w), ih = Math.round(h);
        ResourceLocation img = dev.masterball.api.client.art.CardArt.back();
        if (textureExists(img)) {
            ctx.flush();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            // shadow: the same image tinted black, so it keeps the rounded corners
            RenderSystem.setShaderColor(0f, 0f, 0f, 0.45f * alpha);
            ctx.blit(img, x0 + 3, y0 + 5, iw, ih, 0f, 0f, 1, 1, 1, 1);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            texture(ctx, img, x0, y0, iw, ih, alpha);
            if (glowColor != 0) {
                float p = 0.5f + 0.5f * (float) Math.sin(t * 6);
                ctx.renderOutline(x0, y0, iw, ih, withAlpha(glowColor, alpha * (0.5f + 0.5f * p)));
                ctx.renderOutline(x0 + 1, y0 + 1, iw - 2, ih - 2, withAlpha(glowColor, alpha * 0.5f * p));
            }
            return;
        }
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
            ctx.renderOutline(x0, y0, iw, ih, withAlpha(glowColor, alpha * (0.5f + 0.5f * p)));
            ctx.renderOutline(x0 + 1, y0 + 1, iw - 2, ih - 2, withAlpha(glowColor, alpha * 0.5f * p));
        }
    }

    /**
     * Draws a rectangle split by a cut: the content is painted in vertical strips clipped
     * (scissor) above or below the cut line, and each half is offset separately.
     *
     * @param cut   height of the cut in each strip, normalized 0..1 within the rectangle
     * @param part  0 = top half, 1 = bottom half
     */
    public static void split(GuiGraphics ctx, float x, float y, float w, float h, float[] cut, int part,
                             float dx, float dy, Runnable painter) {
        int n = cut.length;
        for (int i = 0; i < n; i++) {
            int sx0 = Math.round(x + w * i / n + dx), sx1 = Math.round(x + w * (i + 1) / n + dx);
            float cy = y + h * Mth.clamp(cut[i], 0f, 1f);
            int top = part == 0 ? Math.round(y + dy) : Math.round(cy + dy);
            int bot = part == 0 ? Math.round(cy + dy) : Math.round(y + h + dy);
            if (bot <= top || sx1 <= sx0) continue;
            ctx.enableScissor(sx0, top, sx1, bot);
            PoseStack m = ctx.pose();
            m.pushPose();
            m.translate(dx, dy, 0);
            painter.run();
            m.popPose();
            ctx.disableScissor();
        }
    }

    /** Torn edge (paper/cardboard fibers) along the cut. */
    public static void tornEdge(GuiGraphics ctx, float x, float y, float w, float h, float[] cut, float dx, float dy,
                                int color, float alpha, long seed) {
        int n = cut.length;
        for (int i = 0; i < n; i++) {
            float x0 = x + w * i / n + dx, x1 = x + w * (i + 1) / n + dx;
            float cy = y + h * Mth.clamp(cut[i], 0f, 1f) + dy;
            for (float xx = x0; xx < x1; xx += 2) {
                long hsh = (seed * 31 + (long) (xx * 7)) * 0x9E3779B97F4A7C15L;
                int len = 1 + (int) ((hsh >>> 60) & 3);
                int side = ((hsh >>> 50) & 1) == 0 ? -1 : 1;
                int yy = Math.round(cy);
                ctx.fill(Math.round(xx), yy, Math.round(xx) + 2, yy + side * len, withAlpha(color, alpha));
            }
        }
    }

    /** Holographic shine (diagonal band that follows the mouse) over a card rectangle in screen coordinates. */
    public static void holo(GuiGraphics ctx, float x, float y, float w, float h, int tier, int color, double t, int mouseX) {
        int x0 = Math.round(x), y0 = Math.round(y);
        ctx.enableScissor(x0, y0, x0 + Math.round(w), y0 + Math.round(h));
        PoseStack m = ctx.pose();
        m.pushPose();
        float cx = x + w / 2, cy = y + h / 2;
        float follow = Mth.clamp((mouseX - cx) / w, -1, 1) * w * 0.5f;
        float sweep = (float) (((t * 0.45) % 1.6) - 0.3) * w * 1.6f - w * 0.8f;
        m.translate(cx + follow * 0.6f + sweep * 0.4f, cy, 0);
        m.mulPose(Axis.ZP.rotationDegrees(22));
        int strips = 14;
        float bandW = w * 0.55f;
        for (int i = 0; i < strips; i++) {
            float f = i / (float) (strips - 1);
            float a = (float) Math.sin(f * Math.PI) * (0.10f + 0.06f * tier);
            int col = tier >= 3 ? hsv((float) ((f + t * 0.25) % 1.0), 0.55f, 1f) : brighten(color);
            int sx0 = Math.round(-bandW / 2 + f * bandW);
            ctx.fill(sx0, Math.round(-h), sx0 + Math.max(1, Math.round(bandW / strips) + 1), Math.round(h), withAlpha(col, a));
        }
        m.popPose();
        ctx.disableScissor();
    }

    public static void glow(GuiGraphics ctx, float x, float y, float w, float h, int color, float strength) {
        for (int i = 8; i >= 1; i--) {
            float grow = i * 4f;
            float a = strength * (1f - i / 9f) * 0.35f;
            ctx.fill(Math.round(x - w / 2 - grow), Math.round(y - h / 2 - grow), Math.round(x + w / 2 + grow),
                    Math.round(y + h / 2 + grow), withAlpha(color, a));
        }
    }

    public static void rays(GuiGraphics ctx, float x, float y, float len, double angle, int color, float alpha, int count) {
        PoseStack m = ctx.pose();
        for (int i = 0; i < count; i++) {
            m.pushPose();
            m.translate(x, y, 0);
            m.mulPose(Axis.ZP.rotationDegrees((float) (angle + i * 360.0 / count)));
            for (int s = 0; s < 4; s++) {
                int half = 2 + s * 3;
                ctx.fill(0, -half, Math.round(len * (1 - s * 0.15f)), half, withAlpha(color, alpha / (s + 1)));
            }
            m.popPose();
        }
    }

    public static void scaledCentered(GuiGraphics ctx, net.minecraft.network.chat.Component text, float cx, float y, float scale, int color) {
        PoseStack m = ctx.pose();
        m.pushPose();
        m.translate(cx, y, 0);
        m.scale(scale, scale, 1);
        ctx.drawCenteredString(Minecraft.getInstance().font, text, 0, 0, color);
        m.popPose();
    }

    public static int withAlpha(int argb, float a) {
        int base = (argb >>> 24) & 0xFF;
        int alpha = Mth.clamp(Math.round(base * a), 0, 255);
        return (alpha << 24) | (argb & 0xFFFFFF);
    }

    public static int brighten(int argb) {
        int r = Math.min(255, ((argb >> 16) & 0xFF) + 80);
        int g = Math.min(255, ((argb >> 8) & 0xFF) + 80);
        int b = Math.min(255, (argb & 0xFF) + 80);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    public static int hsv(float h, float s, float v) {
        return 0xFF000000 | Mth.hsvToRgb(h, s, v);
    }

    public static double easeOutBack(double x) {
        double c1 = 1.70158, c3 = c1 + 1;
        return 1 + c3 * Math.pow(x - 1, 3) + c1 * Math.pow(x - 1, 2);
    }

    public static double easeOutCubic(double x) { return 1 - Math.pow(1 - x, 3); }

    public static double easeInCubic(double x) { return x * x * x; }
}

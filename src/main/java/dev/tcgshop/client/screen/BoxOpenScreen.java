package dev.tcgshop.client.screen;

import dev.tcgshop.client.binder.Ui;
import dev.tcgshop.client.render.ShopRenderers;
import dev.tcgshop.shop.BoosterBox;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

/**
 * Animación al abrir una caja de sobres desde la mano: la caja en 3D tiembla, se rompe el plástico, se abre la
 * tapa y los 36 sobres salen disparados hasta formar una cuadrícula. Los sobres ya están en tu inventario.
 */
public class BoxOpenScreen extends Screen {
    private final String set;
    private final int packs;
    private final double start = System.nanoTime() / 1e9;
    private boolean popped;

    public BoxOpenScreen(String set, int packs) {
        super(Text.translatable("tcgshop.box.opening"));
        this.set = set;
        this.packs = packs;
    }

    @Override
    public boolean shouldPause() { return false; }

    private double t() { return System.nanoTime() / 1e9 - start; }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        double t = t();
        ctx.fillGradient(0, 0, width, height, 0xC0050812, 0xE0020306);
        // rayos de fondo
        float burst = (float) MathHelper.clamp((t - 1.3) / 0.5, 0, 1);
        if (burst > 0) Draw.rays(ctx, width / 2f, height / 2f - 10, Math.max(width, height), t * 0.4, ShopRenderers.setColor(set), 0.12f * burst, 14);

        // caja en 3D
        float lid = (float) MathHelper.clamp((t - 0.7) / 0.6, 0, 1);
        float wrap = 1 - (float) MathHelper.clamp((t - 0.45) / 0.25, 0, 1);
        float shake = t < 0.7 ? (float) (1 - t / 0.7) : 0;
        float boxFade = 1 - (float) MathHelper.clamp((t - 2.6) / 0.5, 0, 1);
        if (boxFade > 0) {
            if (!popped && t > 0.5) {
                popped = true;
                if (client != null) client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ITEM_BUNDLE_DROP_CONTENTS, 0.8f));
            }
            MatrixStack m = ctx.getMatrices();
            m.push();
            float scale = Math.min(width, height) * 0.42f * (0.9f + 0.1f * boxFade);
            m.translate(width / 2f, height / 2f + scale * 0.32f, 200);
            m.scale(scale, -scale, scale);
            m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(24));
            m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(200 + (float) Math.sin(t * 0.8) * 12));
            m.translate(-0.5, 0, -0.5);
            DiffuseLighting.disableGuiDepthLighting();
            VertexConsumerProvider.Immediate imm = ctx.getVertexConsumers();
            ShopRenderers.drawBox(m, imm, 0xF000F0, OverlayTexture.DEFAULT_UV, set, BoosterBox.W, BoosterBox.H, BoosterBox.D,
                    new ShopRenderers.BoxAnim(lid, 0, wrap, shake, (float) (t * 20)));
            ctx.draw();
            DiffuseLighting.enableGuiDepthLighting();
            m.pop();
        }

        // sobres volando a su sitio en la cuadrícula
        Identifier tex = ShopRenderers.packTextureForSet(set);
        int cols = 9, rows = (packs + cols - 1) / cols;
        float ph = Math.min(height * 0.14f, 46), pw = ph * Draw.PACK_RATIO, gap = 4;
        float gw = cols * (pw + gap) - gap, gx = (width - gw) / 2f, gy = height / 2f - (rows * (ph + gap)) / 2f + 10;
        for (int i = 0; i < packs; i++) {
            double pt = MathHelper.clamp((t - 1.25 - i * 0.03) / 0.55, 0, 1);
            if (pt <= 0) continue;
            float e = (float) Draw.easeOutBack(pt);
            float tx = gx + (i % cols) * (pw + gap), ty = gy + (i / cols) * (ph + gap);
            float sx = width / 2f - pw / 2, sy = height / 2f - ph;
            float x = sx + (tx - sx) * e, y = sy + (ty - sy) * e - (float) Math.sin(pt * Math.PI) * 40;
            var m = ctx.getMatrices();
            m.push();
            m.translate(x + pw / 2, y + ph / 2, 300);
            m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) ((1 - pt) * (i % 2 == 0 ? 220 : -220))));
            ctx.fill((int) (-pw / 2) + 2, (int) (-ph / 2) + 3, (int) (pw / 2) + 2, (int) (ph / 2) + 3, 0x66000000);
            Draw.texture(ctx, tex, -pw / 2, -ph / 2, pw, ph, (float) Math.min(1, pt * 2));
            m.pop();
        }
        if (t > 2.4) {
            float a = (float) MathHelper.clamp((t - 2.4) / 0.4, 0, 1);
            var m = ctx.getMatrices();
            m.push();
            m.translate(0, 0, 400);
            Draw.scaledCentered(ctx, Text.translatable("tcgshop.box.done_title", packs).formatted(Formatting.BOLD), width / 2f, gy - 34, 2f,
                    Draw.withAlpha(Ui.GOLD, a));
            Draw.scaledCentered(ctx, Text.translatable("tcgshop.set." + set), width / 2f, gy - 14, 1f, Draw.withAlpha(0xFFFFFFFF, a));
            Draw.scaledCentered(ctx, Text.translatable("tcgshop.box.click_close"), width / 2f, height - 22, 1f, Draw.withAlpha(0xFFAAAAAA, a));
            m.pop();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (t() > 1.2) close();
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (t() > 1.2 || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) close();
        return true;
    }
}

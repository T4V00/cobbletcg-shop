package dev.tcgshop.client.screen;

import dev.masterball.api.card.CardInfo;
import dev.masterball.api.card.Rarity;
import dev.tcgshop.TcgSounds;
import dev.tcgshop.client.binder.Ui;
import dev.tcgshop.client.render.ShopRenderers;
import dev.tcgshop.grading.Grading;
import dev.tcgshop.network.BoxOpenPayload;
import dev.tcgshop.pricing.PriceService;
import dev.tcgshop.shop.BoosterBox;
import dev.tcgshop.shop.ShopServer;
import dev.tcgshop.shop.TcgBlocks;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

/**
 * Animation for opening a box of packs from the hand: the 3D box shakes, the plastic tears, the lid opens
 * and the 36 packs shoot out until they form a grid. The packs are already in your inventory.
 * <p>Mystery Box mode (1.0, when the payload carries a card): the box shakes and bursts, a card back flies out and
 * flips to reveal the card (in its CobbleGrade slab when graded), its value against the box cost, and golden rays for
 * the 1-in-10,000 jackpot. The card is already in your inventory.
 */
public class BoxOpenScreen extends Screen {
    private final String set;
    private final int packs;
    private double start = System.nanoTime() / 1e9;
    private boolean popped;
    // Mystery Box mode
    private final ItemStack card;
    private final long cost;
    private final boolean jackpot;
    private int stage;

    // Mystery Box timeline (seconds)
    private static final double M_BURST = 1.0, M_POP = 0.25, M_RISE = 0.5, M_FLIP = 1.75, M_FLIP_LEN = 0.4, M_INFO = 2.3;

    public BoxOpenScreen(String set, int packs) {
        this(set, packs, ItemStack.EMPTY, 0, false);
    }

    public BoxOpenScreen(BoxOpenPayload p) {
        this(p.set(), p.packs(), p.card(), p.cost(), p.jackpot());
    }

    private BoxOpenScreen(String set, int packs, ItemStack card, long cost, boolean jackpot) {
        super(Text.translatable(card.isEmpty() ? "tcgshop.box.opening" : "tcgshop.mystery.opening"));
        this.set = set;
        this.packs = packs;
        this.card = card;
        this.cost = cost;
        this.jackpot = jackpot;
    }

    private boolean mystery() { return !card.isEmpty(); }

    @Override
    public boolean shouldPause() { return false; }

    private double t() { return System.nanoTime() / 1e9 - start; }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        double t = t();
        ctx.fillGradient(0, 0, width, height, 0xC0050812, 0xE0020306);
        if (mystery()) {
            renderMystery(ctx, mouseX, t);
            return;
        }
        // background rays
        float burst = (float) MathHelper.clamp((t - 1.3) / 0.5, 0, 1);
        if (burst > 0) Draw.rays(ctx, width / 2f, height / 2f - 10, Math.max(width, height), t * 0.4, ShopRenderers.setColor(set), 0.12f * burst, 14);

        // 3D box
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

        // packs flying to their spot in the grid
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
            Draw.scaledCentered(ctx, Text.translatable("masterball.set." + set), width / 2f, gy - 14, 1f, Draw.withAlpha(0xFFFFFFFF, a));
            Draw.scaledCentered(ctx, Text.translatable("tcgshop.box.click_close"), width / 2f, height - 22, 1f, Draw.withAlpha(0xFFAAAAAA, a));
            m.pop();
        }
    }

    // ------------------------------------------------------------------ Mystery Box

    private void play(net.minecraft.sound.SoundEvent sound, float pitch, float volume) {
        if (client != null) client.getSoundManager().play(PositionedSoundInstance.master(sound, pitch, volume));
    }

    private void sounds(double t, int grade) {
        if (stage == 0) { stage = 1; play(TcgSounds.PACK_SHAKE, 1f, 0.8f); }
        if (stage == 1 && t >= M_BURST) {
            stage = 2;
            play(TcgSounds.PACK_TEAR, 1f, 0.9f);
            play(SoundEvents.ITEM_BUNDLE_DROP_CONTENTS, 0.8f, 0.8f);
        }
        if (stage == 2 && t >= M_FLIP - 0.25) {
            stage = 3;
            play(jackpot || grade >= 95 ? TcgSounds.CARD_ANTICIPATE : TcgSounds.CARD_FLIP, 1f, 0.8f);
        }
        if (stage == 3 && t >= M_FLIP + M_FLIP_LEN / 2) {
            stage = 4;
            play(jackpot ? TcgSounds.CARD_LEGENDARY : grade >= 95 ? TcgSounds.CARD_EPIC : grade > 0 ? TcgSounds.CARD_RARE : TcgSounds.CARD_FLIP, 1f, 1f);
        }
    }

    private void renderMystery(DrawContext ctx, int mouseX, double t) {
        int grade = Grading.grade(card);
        Rarity rarity = Rarity.of(card);
        int accent = jackpot ? 0xFFFFC233 : grade > 0 ? Grading.color(grade) : 0xFF000000 | (rarity.color & 0xFFFFFF);
        sounds(t, grade);

        float cx = width / 2f;
        int ch = Math.max(60, Math.min(height - 130, 170));
        int cw = Math.round(ch * Draw.CARD_RATIO);
        float cardCy = height / 2f + 4;
        float boxCy = height / 2f + 10;
        double flip = MathHelper.clamp((t - M_FLIP) / M_FLIP_LEN, 0, 1);
        boolean revealed = flip >= 1;

        // rays behind everything once the card is turned
        if (t > M_FLIP + M_FLIP_LEN / 2) {
            float a = (float) MathHelper.clamp((t - M_FLIP - M_FLIP_LEN / 2) / 0.4, 0, 1);
            if (jackpot) Draw.rays(ctx, cx, cardCy, Math.max(width, height), t * 0.5, accent, 0.20f * a, 18);
            else if (grade >= 95) Draw.rays(ctx, cx, cardCy, Math.max(width, height), t * 0.4, accent, 0.10f * a, 12);
        }

        // the box shakes harder and harder, then bursts
        double popT = MathHelper.clamp((t - M_BURST) / M_POP, 0, 1);
        if (popT < 1) {
            float amp = t < M_BURST ? (float) (t / M_BURST) * 6f : 0f;
            float dx = (float) Math.sin(t * 55) * amp, rot = (float) Math.sin(t * 41) * amp * 1.6f;
            float s = Math.min(width, height) * 0.30f / 16f * (1f + (float) popT * 0.6f);
            MatrixStack m = ctx.getMatrices();
            m.push();
            m.translate(cx + dx, boxCy, 0);
            m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rot));
            m.scale(s, s, 1);
            ctx.drawItem(new ItemStack(TcgBlocks.MYSTERY_BOX), -8, -8);
            m.pop();
        }

        // the card flies out of the box and flips
        if (t >= M_BURST) {
            double riseT = MathHelper.clamp((t - M_BURST) / M_RISE, 0, 1);
            float rise = (float) Draw.easeOutBack(riseT);
            float y = boxCy + (cardCy - boxCy) * rise - (float) Math.sin(riseT * Math.PI) * 30;
            float sc = 0.3f + 0.7f * Math.min(1f, rise);
            float sx = (float) Math.abs(Math.cos(Math.PI * flip));
            boolean face = flip >= 0.5;
            int pad = grade > 0 ? 12 : 0;
            if (revealed) Draw.glow(ctx, cx, cardCy, cw + pad, ch + pad, accent, jackpot ? 0.6f : 0.3f);
            MatrixStack m = ctx.getMatrices();
            m.push();
            m.translate(cx, y, 0);
            m.scale(Math.max(0.02f, sx) * sc, sc, 1);
            if (!face) {
                Draw.cardBack(ctx, cw, ch, 1f, t > M_FLIP - 0.3 ? accent : 0, t);
            } else {
                if (grade > 0) { // CobbleGrade slab around the card
                    Ui.roundGradient(ctx, -cw / 2 - 6, -ch / 2 - 6, cw + 12, ch + 12, 0x88DDE6F5, 0x66B8C4D8);
                    Ui.outline(ctx, -cw / 2 - 6, -ch / 2 - 6, cw + 12, ch + 12, 0xCCFFFFFF);
                }
                Draw.cardFace(ctx, CardInfo.texture(CardInfo.id(card)), card, -cw / 2f, -ch / 2f, cw, ch, 1f);
            }
            m.pop();
            if (revealed && rarity.tier > 0) Draw.holo(ctx, cx - cw / 2f, cardCy - ch / 2f, cw, ch, rarity.tier, rarity.color, t, mouseX);
            if (revealed && grade > 0) ctx.fill(Math.round(cx - cw / 2f - 3), Math.round(cardCy - ch / 2f - 3),
                    Math.round(cx - cw / 2f - 3 + cw / 5f), Math.round(cardCy + ch / 2f + 3), 0x14FFFFFF); // plastic sheen
        }

        // flash when the box bursts
        if (t >= M_BURST && t < M_BURST + 0.45) {
            float a = 1f - (float) ((t - M_BURST) / 0.45);
            ctx.fill(0, 0, width, height, Draw.withAlpha(0xFFFFFFFF, 0.55f * a));
        }

        // texts
        if (t > M_INFO) {
            float a = (float) MathHelper.clamp((t - M_INFO) / 0.4, 0, 1);
            float top = cardCy - ch / 2f - (grade > 0 ? 6 : 0), bottom = cardCy + ch / 2f + (grade > 0 ? 6 : 0);
            if (jackpot)
                Draw.scaledCentered(ctx, Text.translatable("tcgshop.mystery.jackpot").formatted(Formatting.BOLD), cx, top - 44, 2.4f, Draw.withAlpha(0xFFFFC233, a));
            if (grade > 0) Draw.scaledCentered(ctx, Grading.tooltip(grade), cx, top - 16, 1.2f, Draw.withAlpha(0xFFFFFFFF, a));
            Draw.scaledCentered(ctx, card.getName(), cx, bottom + 8, 1f, Draw.withAlpha(0xFFFFFFFF, a));
            long value = ShopServer.priceOf(card, 1.0);
            Draw.scaledCentered(ctx, Text.translatable("tcgshop.mystery.value", PriceService.format(value), PriceService.format(cost)),
                    cx, bottom + 21, 1f, Draw.withAlpha(Ui.GOLD, a));
            long diff = value - cost;
            Draw.scaledCentered(ctx, Text.literal((diff >= 0 ? "+" : "") + PriceService.format(diff)).formatted(Formatting.BOLD),
                    cx, bottom + 33, 1f, Draw.withAlpha(diff >= 0 ? 0xFF6BE07B : 0xFFFF6B6B, a));
            if (jackpot) Draw.scaledCentered(ctx, Text.translatable("tcgshop.mystery.jackpot_sub"), cx, bottom + 46, 1f, Draw.withAlpha(0xFFFFE08A, a));
            Draw.scaledCentered(ctx, Text.translatable("tcgshop.box.click_close"), cx, height - 14, 1f, Draw.withAlpha(0xFFAAAAAA, a));
        }
    }

    /** Mystery Box: a click before the reveal skips the animation to the end. */
    private boolean skipMystery() {
        if (!mystery() || t() >= M_INFO) return false;
        start -= M_INFO - t();
        stage = Math.max(stage, 3); // the reveal sound still plays once
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (skipMystery()) return true;
        if (t() > 1.2) close();
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) { close(); return true; }
        if (skipMystery()) return true;
        if (t() > 1.2) close();
        return true;
    }
}

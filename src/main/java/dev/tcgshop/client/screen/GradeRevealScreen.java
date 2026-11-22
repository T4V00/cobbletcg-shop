package dev.tcgshop.client.screen;

import dev.masterball.api.card.CardInfo;
import dev.masterball.api.card.Rarity;
import dev.tcgshop.client.binder.Ui;
import dev.tcgshop.grading.Grading;
import dev.tcgshop.pricing.PriceService;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;

import java.util.List;

/**
 * Revelado de CobbleGrade: cada carta llega en su funda, la etiqueta baja y la nota cuenta hasta su valor final con
 * el color del grado (las 10 lanzan rayos dorados). Clic para la siguiente; al final, resumen con el valor ganado.
 */
public class GradeRevealScreen extends Screen {
    private final List<ItemStack> cards;
    private final long before, after;
    private int index;
    private double shown = now();
    private boolean sounded;

    public GradeRevealScreen(List<ItemStack> cards, long before, long after) {
        super(Text.literal("CobbleGrade"));
        this.cards = cards;
        this.before = before;
        this.after = after;
    }

    private static double now() { return System.nanoTime() / 1e9; }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        double t = now(), e = t - shown;
        ctx.fillGradient(0, 0, width, height, 0xD0060812, 0xF0020306);
        Draw.scaledCentered(ctx, Text.literal("COBBLEGRADE").formatted(Formatting.BOLD), width / 2f, 12, 1.6f, 0xFFE8ECFF);
        if (index >= cards.size()) { summary(ctx, t); return; }
        ItemStack st = cards.get(index);
        int g = Grading.grade(st);
        int col = Grading.color(g);
        float in = (float) Draw.easeOutBack(MathHelper.clamp(e / 0.45, 0, 1));
        int ch = Math.min(height - 110, 190), cw = Math.round(ch * Draw.CARD_RATIO);
        int sw = cw + 18, sh = ch + 52;
        int sx = width / 2 - sw / 2, sy = (int) (height / 2 - sh / 2 + 8 + (1 - in) * height * 0.6f);
        if (g >= 100 && e > 1.0) Draw.rays(ctx, width / 2f, sy + sh / 2f, Math.max(width, height), t * 0.5, col, 0.18f, 16);
        Draw.glow(ctx, width / 2f, sy + sh / 2f, sw, sh, col, e > 1.0 ? 0.45f : 0.15f);
        // funda
        Ui.roundGradient(ctx, sx, sy, sw, sh, 0x88DDE6F5, 0x66B8C4D8);
        Ui.outline(ctx, sx, sy, sw, sh, 0xCCFFFFFF);
        // etiqueta (baja desde arriba)
        float drop = (float) Draw.easeOutCubic(MathHelper.clamp((e - 0.45) / 0.35, 0, 1));
        int ly = sy + 5 - Math.round((1 - drop) * 30), lh = 36;
        if (drop > 0) {
            Ui.roundGradient(ctx, sx + 5, ly, sw - 10, lh, 0xFFF6F6F6, 0xFFE2E2E2);
            ctx.fill(sx + 5, ly, sx + 9, ly + lh, col);
            ctx.drawText(textRenderer, Text.literal("COBBLEGRADE").formatted(Formatting.BOLD), sx + 13, ly + 4, 0xFF1E2A44, false);
            ctx.drawText(textRenderer, Text.literal(Ui.ellipsis(Text.translatable(st.getItem().getTranslationKey()).getString(), sw - 70)), sx + 13, ly + 14, 0xFF444A5A, false);
            ctx.drawText(textRenderer, Text.translatable("tcgshop.grade.cert", String.format("%06d", Grading.cert(st))), sx + 13, ly + 24, 0xFF8A8FA0, false);
            // la nota cuenta hacia arriba
            double c = MathHelper.clamp((e - 0.8) / 0.9, 0, 1);
            int showing = (int) Math.round(10 + (g - 10) * Draw.easeOutCubic(c));
            Draw.scaledCentered(ctx, Text.literal(Grading.format(showing)).formatted(Formatting.BOLD), sx + sw - 24, ly + 6, 2.0f, c >= 1 ? col : 0xFF333844);
            if (c >= 1) Draw.scaledCentered(ctx, Text.translatable(Grading.labelKey(g)), sx + sw - 24, ly + 26, 0.75f, col);
            if (c >= 1 && !sounded) {
                sounded = true;
                if (client != null) client.getSoundManager().play(PositionedSoundInstance.master(g >= 95 ? SoundEvents.UI_TOAST_CHALLENGE_COMPLETE
                        : g >= 80 ? SoundEvents.ENTITY_PLAYER_LEVELUP : SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), g >= 95 ? 1f : 1.2f));
            }
        }
        // carta
        int cx = sx + 9, cy = sy + 46;
        Rarity r = Rarity.of(st);
        Draw.cardFace(ctx, CardInfo.texture(CardInfo.id(st)), st, cx, cy, cw, ch, 1f);
        if (r.tier > 0) Draw.holo(ctx, cx, cy, cw, ch, r.tier, r.color, t, mouseX);
        ctx.fill(sx + 3, sy + 3, sx + 3 + sw / 5, sy + sh - 3, 0x14FFFFFF); // brillo del plástico
        // valor
        if (e > 1.7) {
            long v = PriceService.priceCd(st);
            Draw.scaledCentered(ctx, Text.translatable("tcgshop.grade.value_now", PriceService.format(v), String.format("x%.1f", PriceService.gradedMultiplier(CardInfo.id(st), r, g))),
                    width / 2f, sy + sh + 8, 1f, Ui.GOLD);
        }
        Draw.scaledCentered(ctx, Text.literal((index + 1) + " / " + cards.size()), width / 2f, height - 14, 1f, 0xFF8A8FA8);
    }

    private void summary(DrawContext ctx, double t) {
        long diff = after - before;
        Draw.scaledCentered(ctx, Text.translatable("tcgshop.grade.summary", cards.size()).formatted(Formatting.BOLD), width / 2f, height / 2f - 40, 1.6f, 0xFFFFFFFF);
        Draw.scaledCentered(ctx, Text.translatable("tcgshop.grade.summary_value", PriceService.format(before), PriceService.format(after)),
                width / 2f, height / 2f - 12, 1.2f, Ui.GOLD);
        Draw.scaledCentered(ctx, Text.literal((diff >= 0 ? "+" : "") + PriceService.format(diff)).formatted(Formatting.BOLD),
                width / 2f, height / 2f + 8, 1.8f, diff >= 0 ? 0xFF6BE07B : 0xFFFF6B6B);
        int best = 0;
        for (ItemStack st : cards) best = Math.max(best, Grading.grade(st));
        Draw.scaledCentered(ctx, Text.translatable("tcgshop.grade.best", Grading.format(best)), width / 2f, height / 2f + 34, 1f, Grading.color(best));
        Draw.scaledCentered(ctx, Text.translatable("tcgshop.box.click_close"), width / 2f, height - 20, 1f, 0xFFAAAAAA);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (now() - shown < 0.5) return true;
        if (index >= cards.size()) { close(); return true; }
        if (now() - shown < 1.7) { shown = now() - 1.8; return true; } // saltar animación
        index++;
        shown = now();
        sounded = false;
        return true;
    }
}

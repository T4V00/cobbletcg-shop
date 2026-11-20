package dev.tcgshop.mixin.client;

import dev.tcgshop.card.CardInfo;
import dev.tcgshop.grading.Grading;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Nota de CobbleGrade legible en las casillas del inventario (insignia del color de la nota, arriba a la izquierda). */
@Mixin(DrawContext.class)
public abstract class DrawContextMixin {
    @Inject(method = "drawItemInSlot(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/item/ItemStack;IILjava/lang/String;)V", at = @At("TAIL"), require = 0)
    private void tcgshop$gradeBadge(TextRenderer tr, ItemStack stack, int x, int y, @Nullable String countOverride, CallbackInfo ci) {
        if (stack.isEmpty() || !Grading.isGraded(stack) || !CardInfo.isCard(stack)) return;
        DrawContext ctx = (DrawContext) (Object) this;
        int g = Grading.grade(stack);
        String s = Grading.format(g);
        int col = Grading.color(g) | 0xFF000000;
        var m = ctx.getMatrices();
        m.push();
        m.translate(0, 0, 200);
        int w = Math.round(tr.getWidth(s) * 0.5f) + 3;
        ctx.fill(x, y, x + w, y + 6, 0xFF101018);
        ctx.fill(x, y, x + w - 1, y + 5, col);
        m.translate(x + 1.5f, y + 1, 0);
        m.scale(0.5f, 0.5f, 1);
        ctx.drawText(tr, s, 0, 0, 0xFFFFFFFF, true);
        m.pop();
    }
}

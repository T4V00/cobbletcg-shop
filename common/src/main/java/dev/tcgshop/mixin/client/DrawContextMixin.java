package dev.tcgshop.mixin.client;

import dev.masterball.api.card.CardInfo;
import dev.tcgshop.grading.Grading;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Readable CobbleGrade grade on inventory slots (badge in the grade's color, top left). */
@Mixin(GuiGraphics.class)
public abstract class DrawContextMixin {
    @Inject(method = "renderItemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V", at = @At("TAIL"), require = 0)
    private void tcgshop$gradeBadge(Font tr, ItemStack stack, int x, int y, @Nullable String countOverride, CallbackInfo ci) {
        if (stack.isEmpty() || !Grading.isGraded(stack) || !CardInfo.isCard(stack)) return;
        GuiGraphics ctx = (GuiGraphics) (Object) this;
        int g = Grading.grade(stack);
        String s = Grading.format(g);
        int col = Grading.color(g) | 0xFF000000;
        var m = ctx.pose();
        m.pushPose();
        m.translate(0, 0, 200);
        int w = Math.round(tr.width(s) * 0.5f) + 3;
        ctx.fill(x, y, x + w, y + 6, 0xFF101018);
        ctx.fill(x, y, x + w - 1, y + 5, col);
        m.translate(x + 1.5f, y + 1, 0);
        m.scale(0.5f, 0.5f, 1);
        ctx.drawString(tr, s, 0, 0, 0xFFFFFFFF, true);
        m.popPose();
    }
}

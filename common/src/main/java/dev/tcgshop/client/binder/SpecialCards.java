package dev.tcgshop.client.binder;

import dev.masterball.api.card.PrintVariant;
import dev.tcgshop.grading.Grading;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Client: how the binders show special cards (print variants and graded slabs) filed as full stacks. */
public final class SpecialCards {
    private SpecialCards() {}

    /** Small corner tag on the card face: the grade, or a diamond for a print variant. */
    public static void badge(GuiGraphics ctx, Font tr, ItemStack st, int x, int y, int w) {
        int g = Grading.grade(st);
        String s = g > 0 ? Grading.format(g) : "◆";
        int color = g > 0 ? Grading.color(g) : 0xFF7FE3FF;
        int tw = tr.width(s) + 3;
        var m = ctx.pose();
        m.pushPose();
        m.translate(x + 1, y + 1, 0);
        float k = w < 30 ? 0.7f : 1f;
        m.scale(k, k, 1);
        ctx.fill(0, 0, tw, 9, 0xCC000000);
        ctx.drawString(tr, s, 2, 1, color, false);
        m.popPose();
    }

    /** Tooltip / detail lines: variant name and grade. */
    public static List<Component> lines(ItemStack st) {
        List<Component> out = new ArrayList<>();
        PrintVariant v = PrintVariant.of(st);
        if (v != PrintVariant.STANDARD) out.add(v.displayName().copy().withStyle(ChatFormatting.AQUA));
        int g = Grading.grade(st);
        if (g > 0) out.add(Component.translatable("tcgshop.binder.special.grade", Grading.format(g)).withColor(Grading.color(g) & 0xFFFFFF));
        return out;
    }
}

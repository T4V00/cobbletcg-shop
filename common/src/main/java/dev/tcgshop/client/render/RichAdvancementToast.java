package dev.tcgshop.client.render;

import dev.tcgshop.client.binder.Ui;
import java.util.List;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;

/**
 * Advancement toast that shows the DESCRIPTION besides the title (Minecraft's toast only shows the title).
 * Replaces AdvancementToast for all advancements (vanilla, cobbletcg and the add-on's).
 */
public class RichAdvancementToast implements Toast {
    private static final int WIDTH = 200;
    private final AdvancementHolder advancement;
    private final List<FormattedCharSequence> desc;
    private boolean soundPlayed;

    public RichAdvancementToast(AdvancementHolder advancement) {
        this.advancement = advancement;
        Font tr = Minecraft.getInstance().font;
        DisplayInfo d = advancement.value().display().orElse(null);
        List<FormattedCharSequence> lines = d == null ? List.of() : tr.split(d.getDescription(), WIDTH - 40);
        this.desc = lines.size() > 3 ? lines.subList(0, 3) : lines;
    }

    @Override
    public int width() {
        return WIDTH;
    }

    @Override
    public int height() {
        return 28 + desc.size() * 9 + 4;
    }

    @Override
    public Visibility render(GuiGraphics ctx, ToastComponent manager, long startTime) {
        DisplayInfo d = advancement.value().display().orElse(null);
        if (d == null) return Visibility.HIDE;
        Font tr = manager.getMinecraft().font;
        AdvancementType frame = d.getType();
        int accent = frame == AdvancementType.CHALLENGE ? 0xFFD66BFF : frame == AdvancementType.GOAL ? 0xFF6BC5FF : 0xFFFFD34D;
        int h = height();
        // Fully opaque background (it used to be 94% alpha and the text under it showed through). The ToastManager
        // mixin flushes the HUD/screen batch and clears depth before the toasts, so this panel really covers it.
        Ui.roundGradient(ctx, 0, 0, WIDTH, h, 0xFF262A3C, 0xFF161925);
        Ui.outline(ctx, 0, 0, WIDTH, h, accent);
        ctx.fill(1, 1, 4, h - 1, accent);
        // icon
        Ui.round(ctx, 8, 6, 22, 22, 0xFF0E1019);
        Ui.outline(ctx, 8, 6, 22, 22, accent);
        ctx.renderFakeItem(d.getIcon(), 11, 9);
        Component header = Component.translatable("advancements.toast." + frame.getSerializedName());
        ctx.drawString(tr, header, 36, 5, accent, false);
        ctx.drawString(tr, d.getTitle(), 36, 15, 0xFFFFFFFF, true);
        int y = 27;
        for (FormattedCharSequence line : desc) {
            ctx.drawString(tr, line, 36, y, 0xFFB8BCCC, false);
            y += 9;
        }
        ctx.flush(); // this toast is complete before the next one (or anything else) is drawn
        if (!soundPlayed && startTime > 0L) {
            soundPlayed = true;
            if (frame == AdvancementType.CHALLENGE) {
                manager.getMinecraft().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f));
            }
        }
        return startTime >= 6500.0 * manager.getNotificationDisplayTimeMultiplier() ? Visibility.HIDE : Visibility.SHOW;
    }
}

package dev.tcgshop.client.render;

import dev.tcgshop.client.binder.Ui;
import net.minecraft.advancement.AdvancementDisplay;
import net.minecraft.advancement.AdvancementEntry;
import net.minecraft.advancement.AdvancementFrame;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.toast.Toast;
import net.minecraft.client.toast.ToastManager;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.List;

/**
 * Advancement toast that shows the DESCRIPTION besides the title (Minecraft's toast only shows the title).
 * Replaces AdvancementToast for all advancements (vanilla, cobbletcg and the add-on's).
 */
public class RichAdvancementToast implements Toast {
    private static final int WIDTH = 200;
    private final AdvancementEntry advancement;
    private final List<OrderedText> desc;
    private boolean soundPlayed;

    public RichAdvancementToast(AdvancementEntry advancement) {
        this.advancement = advancement;
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        AdvancementDisplay d = advancement.value().display().orElse(null);
        List<OrderedText> lines = d == null ? List.of() : tr.wrapLines(d.getDescription(), WIDTH - 40);
        this.desc = lines.size() > 3 ? lines.subList(0, 3) : lines;
    }

    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return 28 + desc.size() * 9 + 4;
    }

    @Override
    public Visibility draw(DrawContext ctx, ToastManager manager, long startTime) {
        AdvancementDisplay d = advancement.value().display().orElse(null);
        if (d == null) return Visibility.HIDE;
        TextRenderer tr = manager.getClient().textRenderer;
        AdvancementFrame frame = d.getFrame();
        int accent = frame == AdvancementFrame.CHALLENGE ? 0xFFD66BFF : frame == AdvancementFrame.GOAL ? 0xFF6BC5FF : 0xFFFFD34D;
        int h = getHeight();
        Ui.roundGradient(ctx, 0, 0, WIDTH, h, 0xF0262A3C, 0xF0161925);
        Ui.outline(ctx, 0, 0, WIDTH, h, accent);
        ctx.fill(1, 1, 4, h - 1, accent);
        // icon
        Ui.round(ctx, 8, 6, 22, 22, 0xFF0E1019);
        Ui.outline(ctx, 8, 6, 22, 22, accent);
        ctx.drawItemWithoutEntity(d.getIcon(), 11, 9);
        Text header = Text.translatable("advancements.toast." + frame.asString());
        ctx.drawText(tr, header, 36, 5, accent, false);
        ctx.drawText(tr, d.getTitle(), 36, 15, 0xFFFFFFFF, true);
        int y = 27;
        for (OrderedText line : desc) {
            ctx.drawText(tr, line, 36, y, 0xFFB8BCCC, false);
            y += 9;
        }
        if (!soundPlayed && startTime > 0L) {
            soundPlayed = true;
            if (frame == AdvancementFrame.CHALLENGE) {
                manager.getClient().getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f));
            }
        }
        return startTime >= 6500.0 * manager.getNotificationDisplayTimeMultiplier() ? Visibility.HIDE : Visibility.SHOW;
    }
}

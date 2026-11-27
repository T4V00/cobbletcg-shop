package dev.tcgshop.client.options;

import dev.masterball.api.platform.Platform;
import dev.masterball.api.platform.ClientPlatform;
import dev.masterball.api.platform.event.ClientEvents;
import com.mojang.blaze3d.platform.InputConstants;
import dev.tcgshop.client.ClientSettings;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * 1.0: the mod options screen ({@link ModOptionsScreen}) and its key (default O, rebindable in Controls), plus the
 * one-time notice, the first time the player is in a world, that the card art can be changed there.
 */
public final class ModOptions {
    public static final KeyMapping OPEN_OPTIONS = ClientPlatform.INSTANCE.registerKey(new KeyMapping(
            "key.tcgshop.options", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Y, "key.categories.tcgshop"));

    /** Ticks left before the art notice is shown (-1 = nothing pending). */
    private static int noticeIn = -1;

    private ModOptions() {}

    public static void init() {
        ClientEvents.TICK_END.register(ModOptions::tick);
        ClientEvents.JOIN.register(client -> {
            if (!ClientSettings.INSTANCE.artNoticeShown) noticeIn = 100; // ~5 s after joining, once the world is visible
        });
        ClientEvents.DISCONNECT.register(client -> noticeIn = -1);
    }

    private static void tick(Minecraft client) {
        while (OPEN_OPTIONS.consumeClick()) {
            if (client.screen == null) client.setScreen(new ModOptionsScreen(null));
        }
        if (noticeIn > 0 && client.player != null && client.screen == null) noticeIn--;
        if (noticeIn == 0 && client.player != null) {
            noticeIn = -1;
            showArtNotice(client);
        }
    }

    /** How to open the options: the bound key, or where to bind it. */
    public static Component howToOpen() {
        return OPEN_OPTIONS.isUnbound()
                ? Component.translatable("tcgshop.options.notice.unbound")
                : Component.translatable("tcgshop.options.notice.key", OPEN_OPTIONS.getTranslatedKeyMessage().copy().withStyle(ChatFormatting.YELLOW));
    }

    private static void showArtNotice(Minecraft client) {
        ClientSettings.INSTANCE.artNoticeShown = true;
        ClientSettings.save();
        String variant = String.valueOf(1 + java.util.concurrent.ThreadLocalRandom.current().nextInt(3));
        client.player.displayClientMessage(Component.translatable("tcgshop.options.notice.chat." + variant, howToOpen())
                .withStyle(ChatFormatting.GOLD), false);
        SystemToast.add(client.getToasts(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                Component.translatable("tcgshop.options.notice.title"), howToOpen());
    }
}

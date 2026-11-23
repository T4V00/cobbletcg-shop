package dev.tcgshop.client.options;

import dev.tcgshop.client.ClientSettings;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

/**
 * 1.0: the mod options screen ({@link ModOptionsScreen}) and its key (default O, rebindable in Controls), plus the
 * one-time notice, the first time the player is in a world, that the card art can be changed there.
 */
public final class ModOptions {
    public static final KeyBinding OPEN_OPTIONS = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.tcgshop.options", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_O, "key.categories.tcgshop"));

    /** Ticks left before the art notice is shown (-1 = nothing pending). */
    private static int noticeIn = -1;

    private ModOptions() {}

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(ModOptions::tick);
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (!ClientSettings.INSTANCE.artNoticeShown) noticeIn = 100; // ~5 s after joining, once the world is visible
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> noticeIn = -1);
    }

    private static void tick(MinecraftClient client) {
        while (OPEN_OPTIONS.wasPressed()) {
            if (client.currentScreen == null) client.setScreen(new ModOptionsScreen(null));
        }
        if (noticeIn > 0 && client.player != null && client.currentScreen == null) noticeIn--;
        if (noticeIn == 0 && client.player != null) {
            noticeIn = -1;
            showArtNotice(client);
        }
    }

    /** How to open the options: the bound key, or where to bind it. */
    public static Text howToOpen() {
        return OPEN_OPTIONS.isUnbound()
                ? Text.translatable("tcgshop.options.notice.unbound")
                : Text.translatable("tcgshop.options.notice.key", OPEN_OPTIONS.getBoundKeyLocalizedText().copy().formatted(Formatting.YELLOW));
    }

    private static void showArtNotice(MinecraftClient client) {
        ClientSettings.INSTANCE.artNoticeShown = true;
        ClientSettings.save();
        String variant = String.valueOf(1 + java.util.concurrent.ThreadLocalRandom.current().nextInt(3));
        client.player.sendMessage(Text.translatable("tcgshop.options.notice.chat." + variant, howToOpen())
                .formatted(Formatting.GOLD), false);
        SystemToast.add(client.getToastManager(), SystemToast.Type.PERIODIC_NOTIFICATION,
                Text.translatable("tcgshop.options.notice.title"), howToOpen());
    }
}

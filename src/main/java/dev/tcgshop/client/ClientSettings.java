package dev.tcgshop.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Player settings (only on their client): how NPC speech bubbles and their patience bars look.
 * Changed in the phone → Settings and saved in config/tcgshop-client.json.
 */
public final class ClientSettings {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("tcgshop-client.json");

    public static ClientSettings INSTANCE = new ClientSettings();

    /** Text color: 0 automatic (contrast with the background), 1 black, 2 white, 3 yellow, 4 dark blue. */
    public int bubbleTextColor = 0;
    /** Background: 0 white, 1 translucent dark, 2 cream, 3 no background (text with shadow). */
    public int bubbleBackground = 0;
    /** Size: 0 small, 1 normal, 2 large, 3 huge. */
    public int bubbleSize = 1;
    public boolean bubbles = true;
    public boolean patienceBars = true;
    /** 1.0: the one-time notice "card art can be changed in the mod options" was already shown on this client. */
    public boolean artNoticeShown = false;

    public static final int[] TEXT_COLORS = {0, 0xFF16161E, 0xFFFFFFFF, 0xFFFFD34D, 0xFF1A3A8A};
    public static final int[] BG_COLORS = {0xF5FFFFFF, 0xD0101018, 0xF5FFF2D2, 0};
    public static final float[] SIZES = {0.8f, 1f, 1.3f, 1.65f};

    public int bgColor() {
        return BG_COLORS[Math.floorMod(bubbleBackground, BG_COLORS.length)];
    }

    /** Effective text color: the chosen one or, in automatic, black on a light background and white on dark/no background. */
    public int textColor() {
        int c = TEXT_COLORS[Math.floorMod(bubbleTextColor, TEXT_COLORS.length)];
        if (c != 0) return c;
        int bg = bgColor();
        if (bg == 0) return 0xFFFFFFFF;
        int l = ((bg >> 16) & 0xFF) * 3 + ((bg >> 8) & 0xFF) * 6 + (bg & 0xFF);
        return l > 1200 ? 0xFF16161E : 0xFFFFFFFF;
    }

    public float size() {
        return SIZES[Math.floorMod(bubbleSize, SIZES.length)];
    }

    public static void load() {
        try {
            if (Files.exists(FILE)) try (Reader r = Files.newBufferedReader(FILE)) {
                ClientSettings s = GSON.fromJson(r, ClientSettings.class);
                if (s != null) INSTANCE = s;
            }
        } catch (Exception e) {
            dev.tcgshop.TcgShop.LOGGER.warn("Could not read {}", FILE, e);
        }
    }

    public static void save() {
        try (Writer w = Files.newBufferedWriter(FILE)) {
            GSON.toJson(INSTANCE, w);
        } catch (Exception e) {
            dev.tcgshop.TcgShop.LOGGER.warn("Could not save {}", FILE, e);
        }
    }
}

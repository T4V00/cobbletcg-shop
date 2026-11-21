package dev.tcgshop.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Ajustes del jugador (sólo en su cliente): cómo se ven los bocadillos de los NPC y sus barras de paciencia.
 * Se cambian en el teléfono → Ajustes y se guardan en config/tcgshop-client.json.
 */
public final class ClientSettings {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("tcgshop-client.json");

    public static ClientSettings INSTANCE = new ClientSettings();

    /** Color del texto: 0 automático (contraste con el fondo), 1 negro, 2 blanco, 3 amarillo, 4 azul oscuro. */
    public int bubbleTextColor = 0;
    /** Fondo: 0 blanco, 1 oscuro translúcido, 2 crema, 3 sin fondo (texto con sombra). */
    public int bubbleBackground = 0;
    /** Tamaño: 0 pequeño, 1 normal, 2 grande, 3 enorme. */
    public int bubbleSize = 1;
    public boolean bubbles = true;
    public boolean patienceBars = true;

    public static final int[] TEXT_COLORS = {0, 0xFF16161E, 0xFFFFFFFF, 0xFFFFD34D, 0xFF1A3A8A};
    public static final int[] BG_COLORS = {0xF5FFFFFF, 0xD0101018, 0xF5FFF2D2, 0};
    public static final float[] SIZES = {0.8f, 1f, 1.3f, 1.65f};

    public int bgColor() {
        return BG_COLORS[Math.floorMod(bubbleBackground, BG_COLORS.length)];
    }

    /** Color de texto efectivo: el elegido o, en automático, negro sobre fondo claro y blanco sobre oscuro/sin fondo. */
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
            dev.tcgshop.TcgShop.LOGGER.warn("No se pudo leer {}", FILE, e);
        }
    }

    public static void save() {
        try (Writer w = Files.newBufferedWriter(FILE)) {
            GSON.toJson(INSTANCE, w);
        } catch (Exception e) {
            dev.tcgshop.TcgShop.LOGGER.warn("No se pudo guardar {}", FILE, e);
        }
    }
}

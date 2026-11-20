package dev.tcgshop;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/** config/tcgshop.json */
public class TcgShopConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("tcgshop.json");

    public static TcgShopConfig INSTANCE = new TcgShopConfig();
    /** Último error al leer el archivo (null = bien). Si el JSON está roto NO se sobrescribe y se avisa. */
    public static String lastError;

    /**
     * Cuántos CobbleDollars vale 1 USD de mercado real.
     * 250 => un sobre moderno (~5-10 USD) cuesta 1.250-2.500 ₽ (3-6 Poké Balls de la tienda de CobbleDollars),
     * una común (~0,27 USD) ~70 ₽ y una carta top de 100 USD 25.000 ₽ (una élitra en el banco).
     */
    public double cobbleDollarsPerUsd = 1000.0;
    /** Versión del archivo de configuración (para migrar valores por defecto antiguos). */
    public int configVersion = 2;
    /** Texto tras las cifras de dinero. */
    public String currencySuffix = " ₽";

    /** Mercado dinámico: los precios suben y bajan cada día de mercado. */
    public boolean marketEnabled = true;
    /** 0 = precios fijos, 1 = normal (±20-40%), 2 = mercado loco. */
    public double marketVolatility = 1.0;
    /** Duración de un día de mercado en ticks (24000 = un día de Minecraft, 20 min). */
    public int marketDayTicks = 24000;
    /** Semilla del mercado (se genera sola la primera vez). Cambiarla "reinicia" las tendencias. */
    public long marketSeed = 0;
    /** Rareza mínima (tier 0-3) para anunciar el pull en el chat. -1 = nunca. */
    public int announcePullMinTier = 3;
    /** Radio en bloques del anuncio (0 = todo el servidor). */
    public int announceRadius = 0;

    /** CobbleDollars que paga el Mayorista misterioso por cada esmeralda (un bloque = 9). */
    public long emeraldPriceCd = 300;
    /** Al entrar por primera vez en un mundo recibes sobres sellados al azar. */
    public boolean starterPacks = true;
    public int starterPackCount = 5;

    // --- Cliente ---
    public boolean packOpeningAnimation = true;
    public boolean showPriceInTooltip = true;

    public static void load() {
        lastError = null;
        try {
            if (Files.exists(FILE)) {
                try (Reader r = Files.newBufferedReader(FILE)) {
                    TcgShopConfig c = GSON.fromJson(r, TcgShopConfig.class);
                    if (c != null) INSTANCE = c;
                }
            }
            if (INSTANCE.configVersion < 2) { // v2: precios x4 para una economía de tienda equilibrada
                if (INSTANCE.cobbleDollarsPerUsd == 250.0) INSTANCE.cobbleDollarsPerUsd = 1000.0;
                INSTANCE.configVersion = 2;
            }
            if (INSTANCE.marketSeed == 0) INSTANCE.marketSeed = new java.util.Random().nextLong() | 1L;
            if (INSTANCE.marketDayTicks <= 0) INSTANCE.marketDayTicks = 24000;
            save();
        } catch (Exception e) {
            lastError = String.valueOf(e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
            TcgShop.LOGGER.error("No se pudo leer {}", FILE, e);
        }
    }

    public static void save() {
        try (Writer w = Files.newBufferedWriter(FILE)) {
            GSON.toJson(INSTANCE, w);
        } catch (Exception e) {
            TcgShop.LOGGER.error("No se pudo guardar {}", FILE, e);
        }
    }
}

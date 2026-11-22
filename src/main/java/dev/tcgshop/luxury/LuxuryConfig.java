package dev.tcgshop.luxury;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.tcgshop.TcgShop;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * config/tcgshop-luxury.json: estatuas de lujo (sumidero de dinero / símbolo de estatus).
 * Los precios son long: se pueden subir hasta 10^15 (p.ej. 1.000.000.000.000 para la estatua final).
 */
public class LuxuryConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("tcgshop-luxury.json");
    /** Tope de seguridad de cualquier precio (10^15). */
    public static final long MAX_PRICE = 1_000_000_000_000_000L;

    public static LuxuryConfig INSTANCE = new LuxuryConfig();

    public Map<String, String> _help = new LinkedHashMap<>();
    /** Se venden las estatuas (Vendedor de muebles y app de muebles del teléfono). */
    public boolean enabled = true;
    /** Precio de cada estatua en CobbleDollars, por nivel. */
    public Map<String, Long> prices = new LinkedHashMap<>();
    /** Anunciar en el chat del servidor la compra de estatuas a partir de este nivel (1-7; 0 = nunca). */
    public int announceFromTier = 4;
    /** 0.9.5: Llave de la Mansión MasterBall (finca de 100x100 que se construye con clic derecho). */
    public boolean mansionEnabled = true;
    /** Precio de la llave en CobbleDollars (por defecto mil millones, como la estatua MasterBall). */
    public long mansionPrice = 1_000_000_000L;
    /** Bloques que se colocan como máximo por tick al construir (menos = menos tirones, más lento). */
    public int mansionBlocksPerTick = 3000;
    /** Sacar Pokémon residentes (Cobblemon) en la finca al terminarla. */
    public boolean mansionPokemon = true;

    private void sanitize() {
        if (prices == null) prices = new LinkedHashMap<>();
        Map<String, Long> merged = new LinkedHashMap<>();
        for (StatueTier t : StatueTier.values()) {
            Long v = prices.get(t.key);
            merged.put(t.key, v == null ? t.defaultPrice : Math.max(1L, Math.min(MAX_PRICE, v)));
        }
        prices = merged;
        announceFromTier = Math.max(0, Math.min(StatueTier.values().length, announceFromTier));
        mansionPrice = Math.max(1L, Math.min(MAX_PRICE, mansionPrice));
        mansionBlocksPerTick = Math.max(200, Math.min(50_000, mansionBlocksPerTick));
        _help = new LinkedHashMap<>();
        _help.put("aplicar", "Edita este archivo y usa /tcgshop reload (operador) o reinicia el servidor.");
        _help.put("prices", "Precio de cada estatua (bronze, silver, gold, emerald, diamond, netherite, masterball) en CobbleDollars. "
                + "Admite cifras enormes: 1000000000000 = un billón. Máximo 10^15.");
        _help.put("enabled", "false = no se venden estatuas (las ya compradas se quedan).");
        _help.put("announceFromTier", "Nivel mínimo (1-7) de estatua cuya compra se anuncia a todo el servidor. 0 = nunca.");
        _help.put("mansionEnabled", "false = no se vende la Llave de la Mansión MasterBall (las llaves ya compradas siguen funcionando).");
        _help.put("mansionPrice", "Precio de la Llave de la Mansión en CobbleDollars (por defecto 1000000000). Máximo 10^15.");
        _help.put("mansionBlocksPerTick", "Bloques por tick al construir la mansión (200-50000). Bájalo si el servidor da tirones.");
        _help.put("mansionPokemon", "true = la finca trae Pokémon residentes (sólo con Cobblemon): no se capturan, no combaten y no desaparecen.");
    }

    public long price(StatueTier t) {
        Long v = prices == null ? null : prices.get(t.key);
        return v == null ? t.defaultPrice : Math.max(1L, Math.min(MAX_PRICE, v));
    }

    public static void load() {
        LuxuryConfig c = null;
        boolean broken = false;
        try {
            if (Files.exists(FILE)) try (Reader r = Files.newBufferedReader(FILE)) { c = GSON.fromJson(r, LuxuryConfig.class); }
        } catch (Exception e) {
            TcgShop.LOGGER.error("No se pudo leer {} (se usan los valores por defecto y NO se sobrescribe)", FILE, e);
            broken = true;
        }
        if (c == null) c = new LuxuryConfig();
        c.sanitize();
        INSTANCE = c;
        if (!broken) save();
        LuxuryShop.syncAll(); // /tcgshop reload: los clientes ven enseguida los precios / el interruptor nuevos
    }

    public static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            try (Writer w = Files.newBufferedWriter(FILE)) { GSON.toJson(INSTANCE, w); }
        } catch (Exception e) {
            TcgShop.LOGGER.warn("No se pudo guardar {}", FILE, e);
        }
    }
}

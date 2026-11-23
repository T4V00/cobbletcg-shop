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
 * config/tcgshop-luxury.json: luxury statues (money sink / status symbol).
 * Prices are long: they can go up to 10^15 (e.g. 1,000,000,000,000 for the final statue).
 */
public class LuxuryConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("tcgshop-luxury.json");
    /** Safety cap for any price (10^15). */
    public static final long MAX_PRICE = 1_000_000_000_000_000L;

    public static LuxuryConfig INSTANCE = new LuxuryConfig();

    public Map<String, String> _help = new LinkedHashMap<>();
    /** Statues are sold (Furniture Seller and the phone's furniture app). */
    public boolean enabled = true;
    /** Price of each statue in CobbleDollars, per tier. */
    public Map<String, Long> prices = new LinkedHashMap<>();
    /** Announce statue purchases in server chat from this tier upward (1-7; 0 = never). */
    public int announceFromTier = 4;
    /** 0.9.5: MasterBall Mansion Key (100x100 estate built with a right click). */
    public boolean mansionEnabled = true;
    /** Key price in CobbleDollars (default one billion, like the MasterBall statue). */
    public long mansionPrice = 1_000_000_000L;
    /** Maximum blocks placed per tick while building (fewer = less lag, slower). */
    public int mansionBlocksPerTick = 3000;
    /** Spawn resident Pokémon (Cobblemon) on the estate once it is finished. */
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
        _help.put("aplicar", "Edit this file and use /tcgshop reload (operator) or restart the server.");
        _help.put("prices", "Price of each statue (bronze, silver, gold, emerald, diamond, netherite, masterball) in CobbleDollars. "
                + "Accepts huge numbers: 1000000000000 = one trillion. Maximum 10^15.");
        _help.put("enabled", "false = statues are not sold (already purchased ones stay).");
        _help.put("announceFromTier", "Minimum statue tier (1-7) whose purchase is announced to the whole server. 0 = never.");
        _help.put("mansionEnabled", "false = the MasterBall Mansion Key is not sold (keys already purchased keep working).");
        _help.put("mansionPrice", "Price of the Mansion Key in CobbleDollars (default 1000000000). Maximum 10^15.");
        _help.put("mansionBlocksPerTick", "Blocks per tick while building the mansion (200-50000). Lower it if the server lags.");
        _help.put("mansionPokemon", "true = the estate comes with resident Pokémon (Cobblemon only): they cannot be caught, do not battle and do not despawn.");
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
            TcgShop.LOGGER.error("Could not read {} (using default values and NOT overwriting it)", FILE, e);
            broken = true;
        }
        if (c == null) c = new LuxuryConfig();
        c.sanitize();
        INSTANCE = c;
        if (!broken) save();
        LuxuryShop.syncAll(); // /tcgshop reload: clients see the new prices / toggle right away
    }

    public static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            try (Writer w = Files.newBufferedWriter(FILE)) { GSON.toJson(INSTANCE, w); }
        } catch (Exception e) {
            TcgShop.LOGGER.warn("Could not save {}", FILE, e);
        }
    }
}

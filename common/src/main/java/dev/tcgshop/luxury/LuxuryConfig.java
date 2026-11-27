package dev.tcgshop.luxury;

import dev.masterball.api.platform.Platform;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.tcgshop.TcgShop;

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
    private static final Path FILE = Platform.INSTANCE.configDir().resolve("tcgshop-luxury.json");
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
    /** 1.0: customers walking past a statue (from statuePackMinTier up) sometimes drop a sealed pack next to it. */
    public boolean statuePacksEnabled = true;
    /** First statue tier (1-7) where customers can drop packs. Default 3 = gold. */
    public int statuePackMinTier = 3;
    /**
     * Chance per customer walking past one statue (each customer rolls once per statue). Per the 1.0 plan the chance grows
     * with the tier but is "almost never" at gold, the first eligible tier.
     */
    public Map<String, Double> statuePackChance = new LinkedHashMap<>();
    /** How close (blocks) a customer must walk to the statue. */
    public double statuePackRadius = 4.0;
    /** Minimum seconds between two drops at the same statue. */
    public int statuePackCooldownSeconds = 600;

    private void sanitize() {
        if (prices == null) prices = new LinkedHashMap<>();
        Map<String, Long> merged = new LinkedHashMap<>();
        for (StatueTier t : StatueTier.values()) {
            Long v = prices.get(t.key);
            merged.put(t.key, v == null ? t.defaultPrice : Math.max(1L, Math.min(MAX_PRICE, v)));
        }
        prices = merged;
        announceFromTier = Math.max(0, Math.min(StatueTier.values().length, announceFromTier));
        if (statuePackChance == null) statuePackChance = new LinkedHashMap<>();
        Map<String, Double> chances = new LinkedHashMap<>();
        for (StatueTier t : StatueTier.values()) {
            Double v = statuePackChance.get(t.key);
            chances.put(t.key, v == null || v.isNaN() ? defaultPackChance(t) : Math.max(0, Math.min(1, v)));
        }
        statuePackChance = chances;
        statuePackMinTier = Math.max(1, Math.min(StatueTier.values().length, statuePackMinTier));
        statuePackRadius = Double.isNaN(statuePackRadius) ? 4.0 : Math.max(1.0, Math.min(16.0, statuePackRadius));
        statuePackCooldownSeconds = Math.max(0, Math.min(86_400, statuePackCooldownSeconds));
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
        _help.put("statuePacks*", "1.0: customers who walk within statuePackRadius blocks of a statue of tier statuePackMinTier (3 = gold) or higher may drop a random sealed pack next to it (never one of the locked top sets). "
                + "statuePackChance = chance per customer and statue, per tier; gold is almost never, then it grows with the tier. statuePackCooldownSeconds between drops at the same statue.");
        _help.put("mansionPokemon", "true = the estate comes with resident Pokémon (Cobblemon only): they cannot be caught, do not battle and do not despawn.");
    }

    /** Default drop chance per tier: nothing below gold, almost never at gold, then growing with the tier. */
    static double defaultPackChance(StatueTier t) {
        return switch (t) {
            case GOLD -> 0.002;
            case EMERALD -> 0.010;
            case DIAMOND -> 0.018;
            case NETHERITE -> 0.028;
            case MASTERBALL -> 0.040;
            default -> 0.0;
        };
    }

    /** Drop chance for a statue of that tier (0 below statuePackMinTier or when disabled). */
    public double packChance(StatueTier t) {
        if (!statuePacksEnabled || t.index() < statuePackMinTier) return 0;
        Double v = statuePackChance == null ? null : statuePackChance.get(t.key);
        return v == null ? defaultPackChance(t) : Math.max(0, Math.min(1, v));
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

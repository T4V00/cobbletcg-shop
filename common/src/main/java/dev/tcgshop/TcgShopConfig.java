package dev.tcgshop;

import dev.masterball.api.platform.Platform;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/** config/tcgshop.json */
public class TcgShopConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = Platform.INSTANCE.configDir().resolve("tcgshop.json");

    public static TcgShopConfig INSTANCE = new TcgShopConfig();
    /** Last error when reading the file (null = fine). If the JSON is broken it is NOT overwritten and a warning is shown. */
    public static String lastError;

    /**
     * How many CobbleDollars 1 real-market USD is worth.
     * 250 => a modern pack (~5-10 USD) costs 1,250-2,500 ₽ (3-6 Poké Balls from the CobbleDollars shop),
     * a common (~0.27 USD) ~70 ₽ and a top 100 USD card 25,000 ₽ (an elytra at the bank).
     */
    public double cobbleDollarsPerUsd = 1000.0;
    /** Config file version (to migrate old default values). */
    public int configVersion = 2;
    /** Text after money amounts. */
    public String currencySuffix = " ₽";

    /** Dynamic market: prices go up and down every market day. */
    public boolean marketEnabled = true;
    /** 0 = fixed prices, 1 = normal (±20-40%), 2 = crazy market. */
    public double marketVolatility = 1.0;
    /** Length of a market day in ticks (24000 = one Minecraft day, 20 min). */
    public int marketDayTicks = 24000;
    /** Market seed (generated automatically the first time). Changing it "resets" the trends. */
    public long marketSeed = 0;
    /** Minimum rarity (tier 0-3) to announce the pull in chat. -1 = never. */
    public int announcePullMinTier = 3;
    /** Announcement radius in blocks (0 = whole server). */
    public int announceRadius = 0;

    /** CobbleDollars the mysterious Wholesaler pays per emerald (one block = 9). */
    public long emeraldPriceCd = 300;
    /** When joining a world for the first time you receive random sealed packs. */
    public boolean starterPacks = true;
    /** How many welcome packs (random from the cheapest sets; 0 = none). */
    public int starterPackCount = 5; // 1.0: five starter packs
    /** 0.9.5: customers sit for a while on the shop's chairs/sofas/benches (CobbleFurnies, Cozyhome...). */
    public boolean customerSeats = true;
    /** Chance of sitting down for customers who only browse (don't buy). */
    public double customerSitChance = 0.45;
    /** Chance of sitting down after paying. */
    public double customerSitChanceBuyer = 0.35;

    // --- Client ---
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
            if (INSTANCE.configVersion < 2) { // v2: prices x4 for a balanced shop economy
                if (INSTANCE.cobbleDollarsPerUsd == 250.0) INSTANCE.cobbleDollarsPerUsd = 1000.0;
                INSTANCE.configVersion = 2;
            }
            if (INSTANCE.marketSeed == 0) INSTANCE.marketSeed = new java.util.Random().nextLong() | 1L;
            if (INSTANCE.marketDayTicks <= 0) INSTANCE.marketDayTicks = 24000;
            save();
        } catch (Exception e) {
            lastError = String.valueOf(e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
            TcgShop.LOGGER.error("Could not read {}", FILE, e);
        }
    }

    public static void save() {
        try (Writer w = Files.newBufferedWriter(FILE)) {
            GSON.toJson(INSTANCE, w);
        } catch (Exception e) {
            TcgShop.LOGGER.error("Could not save {}", FILE, e);
        }
    }
}

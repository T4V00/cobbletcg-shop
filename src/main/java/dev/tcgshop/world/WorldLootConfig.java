package dev.tcgshop.world;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.tcgshop.TcgShop;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * config/tcgshop-worldloot.json (1.0): searchable trash cans and the "Bankrupt Card Shop" structure.
 *
 * <p>Loot odds and the structure's placement rules are read every time they are used, so /tcgshop reload applies
 * them to newly generated chunks and to the next search. Turning the world generation of trash cans on/off
 * ({@link #trashCansWorldgen}) needs a restart (biome modifications are applied when the server starts).
 * The structure's grid (spacing/separation) lives in data/tcgshop/worldgen/structure_set/bankrupt_card_shop.json.
 */
public class WorldLootConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("tcgshop-worldloot.json");

    public static WorldLootConfig INSTANCE = new WorldLootConfig();

    public Map<String, String> _help = new LinkedHashMap<>();

    // ------------------------------------------------------------------ trash cans: world generation
    /** Natural trash cans generate in new chunks (restart needed). */
    public boolean trashCansWorldgen = true;
    /** Chance that a chunk with a village street (dirt path) gets one trash can next to the path. */
    public double streetCanChance = 0.15;
    /** Chance per chunk of a lonely trash can in plains-like biomes (tag tcgshop:spawns_wild_trash_cans). */
    public double wildCanChance = 0.012;

    // ------------------------------------------------------------------ trash cans: loot
    /** Street/wild can: chance of a sealed pack. */
    public double packChance = 0.10;
    /** Street/wild can: chance of a single card worth less than {@link #cardMaxValue}. */
    public double cardChance = 0.30;
    /** Street/wild can: the card is worth strictly less than this (market price, CobbleDollars). */
    public long cardMaxValue = 2_000;
    /** When there is no pack/card: chance of some junk (paper, string, a bone...) instead of nothing at all. */
    public double junkChance = 0.5;
    /** Minecraft days until a searched street (village) can is full again (0 = never refills). Wild cans never refill. */
    public int refillDays = 7;

    // ------------------------------------------------------------------ Bankrupt Card Shop
    public boolean bankruptShopEnabled = true;
    /** Chance that the shop's unique trash can holds one card. */
    public double bankruptCardChance = 0.5;
    /** Value window of that card (cheap cards are much more likely, see TrashLoot.bankruptCard). */
    public long bankruptMinValue = 300, bankruptMaxValue = 20_000;
    /** Candidate spot with a village nearby: chance of actually building the shop. */
    public double nearVillageChance = 0.6;
    /** Candidate spot with NO village nearby: chance of building a lonely shop anyway. */
    public double standaloneChance = 0.08;
    /** Distance (blocks) between the village's start and the shop. */
    public int villageMinDistance = 44, villageMaxDistance = 76;
    /** Structure sets whose structures count as "villages" (random_spread placement only). */
    public List<String> villageStructureSets = new ArrayList<>(List.of("minecraft:villages"));
    /** Max height difference of the ground under the shop. */
    public int maxSlope = 4;
    /** Chance of one cheap CobbleFurnies piece inside (only when CobbleFurnies is installed). */
    public double furnitureChance = 0.6;
    /** "Cheap" furniture: base price (FurnitureShop.basePrice) up to this. */
    public long furnitureMaxPrice = 2_800;
    /** Chance of a regular (searchable) street trash can by the shop's door. */
    public double outsideCanChance = 0.35;

    /** Format version of this file (absent in older files = 0). Bumped to CURRENT_VERSION by {@link #migrate()}. */
    public int configVersion = 0;

    /** 1: street cans refill after 7 days instead of 3. */
    private static final int CURRENT_VERSION = 1;
    private static final int OLD_REFILL_DAYS = 3;

    /** One-time migrations of files written by older versions; values customized by the server admin are kept. */
    private void migrate() {
        if (configVersion >= CURRENT_VERSION) return;
        if (configVersion < 1 && refillDays == OLD_REFILL_DAYS) refillDays = new WorldLootConfig().refillDays;
        configVersion = CURRENT_VERSION;
    }

    private void sanitize() {
        streetCanChance = clamp01(streetCanChance);
        wildCanChance = clamp01(wildCanChance);
        packChance = clamp01(packChance);
        cardChance = Math.min(clamp01(cardChance), 1 - packChance);
        cardMaxValue = Math.max(2, cardMaxValue);
        junkChance = clamp01(junkChance);
        refillDays = Math.max(0, Math.min(3650, refillDays));
        bankruptCardChance = clamp01(bankruptCardChance);
        bankruptMinValue = Math.max(1, bankruptMinValue);
        bankruptMaxValue = Math.max(bankruptMinValue, bankruptMaxValue);
        nearVillageChance = clamp01(nearVillageChance);
        standaloneChance = clamp01(standaloneChance);
        // the shop must stay within ~7 chunks of its start chunk (structure references reach 8 chunks)
        villageMinDistance = Math.max(16, Math.min(80, villageMinDistance));
        villageMaxDistance = Math.max(villageMinDistance, Math.min(80, villageMaxDistance));
        if (villageStructureSets == null) villageStructureSets = new ArrayList<>(List.of("minecraft:villages"));
        maxSlope = Math.max(0, Math.min(16, maxSlope));
        furnitureChance = clamp01(furnitureChance);
        furnitureMaxPrice = Math.max(0, furnitureMaxPrice);
        outsideCanChance = clamp01(outsideCanChance);
        _help = new LinkedHashMap<>();
        _help.put("apply", "Edit this file and use /tcgshop reload (operator) or restart. trashCansWorldgen needs a restart. Only NEW chunks are affected by world generation changes.");
        _help.put("streetCanChance", "Chance that a chunk with a village street gets a searchable trash can next to the path (0-1).");
        _help.put("wildCanChance", "Chance per chunk of a lonely trash can in plains-like biomes (biome tag tcgshop:spawns_wild_trash_cans).");
        _help.put("packChance/cardChance", "Street can loot: pack chance, then chance of a card worth less than cardMaxValue. Otherwise junk (junkChance) or nothing.");
        _help.put("refillDays", "Minecraft days until a searched street (village) can can be searched again (0 = never). Wild cans and the Bankrupt Card Shop's can are searched only once.");
        _help.put("configVersion", "Internal: format version of this file. Do not edit.");
        _help.put("bankrupt*", "Bankrupt Card Shop: chance its unique trash can holds a card, and the card's value window (cheap is more likely).");
        _help.put("nearVillageChance/standaloneChance", "Bankrupt Card Shop candidate spots: chance to build it when a village is nearby / when none is.");
        _help.put("villageMinDistance/villageMaxDistance", "Distance in blocks from the village start to the shop (16-80).");
    }

    private static double clamp01(double v) {
        return Double.isNaN(v) ? 0 : Math.max(0, Math.min(1, v));
    }

    public static void load() {
        WorldLootConfig c = null;
        boolean broken = false;
        try {
            if (Files.exists(FILE)) try (Reader r = Files.newBufferedReader(FILE)) { c = GSON.fromJson(r, WorldLootConfig.class); }
        } catch (Exception e) {
            TcgShop.LOGGER.error("Could not read {} (using default values and NOT overwriting it)", FILE, e);
            broken = true;
        }
        if (c == null) c = new WorldLootConfig();
        c.migrate();
        c.sanitize();
        INSTANCE = c;
        if (!broken) save();
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

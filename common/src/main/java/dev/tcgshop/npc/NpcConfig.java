package dev.tcgshop.npc;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.masterball.api.platform.Platform;
import dev.tcgshop.TcgShop;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * config/tcgshop-npcs.json (1.1, NPC update): the Mayor and his taxes (npc/TaxOffice, entity/MayorEntity,
 * compat/MayorBattle), the robbed collector's hunting AI (entity/CollectorHunt), collector trades by phone tier
 * (entity/TraderEntity) and the CREATive Engineer (entity/EngineerEntity, npc/EngineerGoods).
 * Applied with /tcgshop reload. {@link #version} marks migrations of changed defaults (a value an admin changed is kept).
 */
public class NpcConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = Platform.INSTANCE.configDir().resolve("tcgshop-npcs.json");
    /** 2: collector per-tier defaults raised so every phone upgrade improves collector deals. */
    public static final int VERSION = 2;
    /** Collector per-tier defaults: every step up improves at least one of them (the selftest checks it). Declared before INSTANCE. */
    static final int[] GIVE_DEF = {10, 11, 12, 14, 16, 18, 20}, TAKE_DEF = {1, 2, 2, 3, 3, 4, 5},
            TRADES_DEF = {4, 5, 5, 6, 6, 7, 8}, TALKS_DEF = {1, 1, 2, 2, 3, 3, 4};

    public static NpcConfig INSTANCE = new NpcConfig();

    public Map<String, String> _help = new LinkedHashMap<>();
    /** Migration marker (absent in a fresh file = 0, then raised to {@link #VERSION}). */
    public int version = 0;

    // ---- Mayor and taxes
    public boolean taxEnabled = true;
    /** First shop tier (ShopTier key) whose owner pays taxes. */
    public String taxMinTier = "iron";
    /** Minecraft days between two tax visits. */
    public int taxIntervalDays = 30;
    /** Share of the revenue earned since the last visit, at the first taxed tier, and how much it grows per tier above it (capped). */
    public double taxIncomeRate = 0.04, taxIncomeRatePerTier = 0.005, taxIncomeRateMax = 0.10;
    /** Property part: this fraction of the price of every shop core you own (Iron 110,000 -> 1,100). */
    public double taxPropertyRate = 0.01;
    public long taxMinimum = 2_000, taxMaximum = 5_000_000;
    /** Days the Mayor waits for an answer; after that he collects by force with taxLateFee on top (or a card if you can't pay). */
    public int taxGraceDays = 3;
    public double taxLateFee = 0.25;
    /** His team's level: your best Pokémon's level + this, clamped to the range. */
    public int mayorTeamLevelBonus = 3, mayorTeamMinLevel = 30, mayorTeamMaxLevel = 100;
    /** Cobblemon's StrongBattleAI skill (0-5). */
    public int mayorAiSkill = 5;
    /** Walking this far from the Mayor during the battle counts as walking away (loss). */
    public int mayorWalkAwayBlocks = 32;

    // ---- robbed collector (hunting AI)
    public boolean collectorBreakBlocks = true, collectorPlaceBlocks = true;
    /** Most blocks one hunt may break plus place; everything is put back when the hunt ends. */
    public int collectorMaxBlockEdits = 48;
    /** Hardest block he breaks (stone 1.5, iron block 5; obsidian 50 is never broken). */
    public double collectorMaxHardness = 6.0;
    public boolean collectorRestoreBlocks = true;
    public boolean collectorElytra = true, collectorTeleportFollow = true, collectorBossBar = true;
    /** He reappears about this far from you after you teleport, at most collectorTeleportFollows times per hunt. */
    public int collectorTeleportDistance = 20, collectorTeleportFollows = 3;

    // ---- collector trades by phone tier (index = PhoneTier level 0..6)
    public int[] collectorGivePerTier = GIVE_DEF.clone();
    public int[] collectorTakePerTier = TAKE_DEF.clone();
    public int[] collectorTradesPerTier = TRADES_DEF.clone();
    public int[] collectorTalksPerTier = TALKS_DEF.clone();

    // ---- CREATive Engineer (only with Create installed)
    public boolean engineerEnabled = true;
    /** Chance every half Minecraft day that a picked player gets his visit: engineerChance if they own a shop or stand near a village, engineerWanderChance otherwise. */
    public double engineerChance = 0.30, engineerWanderChance = 0.12;
    public int engineerStayTicks = 36_000;
    public double engineerPriceMultiplier = 1.0;
    /** Basic materials sold by the stack of 64: item id -> price per stack. */
    public Map<String, Long> engineerBasic = new LinkedHashMap<>();
    /** Machines and tools sold one by one: item id -> price. */
    public Map<String, Long> engineerMachines = new LinkedHashMap<>();

    public static void load() {
        NpcConfig c = null;
        try {
            if (Files.exists(FILE)) try (Reader r = Files.newBufferedReader(FILE)) { c = GSON.fromJson(r, NpcConfig.class); }
        } catch (Exception e) {
            TcgShop.LOGGER.error("Could not read {} (using default values)", FILE, e);
        }
        if (c == null) c = new NpcConfig();
        c.migrate();
        c.sanitize();
        c.version = VERSION;
        c.fillHelp();
        INSTANCE = c;
        save();
    }

    public static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            try (Writer w = Files.newBufferedWriter(FILE)) { GSON.toJson(INSTANCE, w); }
        } catch (Exception e) {
            TcgShop.LOGGER.error("Could not save {}", FILE, e);
        }
    }

    /** Old default values move to the new defaults (a value an admin changed is kept). */
    void migrate() {
        if (version < 2) {
            if (java.util.Arrays.equals(collectorGivePerTier, new int[]{10, 10, 12, 14, 16, 18, 20})) collectorGivePerTier = GIVE_DEF.clone();
            if (java.util.Arrays.equals(collectorTakePerTier, new int[]{1, 1, 2, 2, 3, 4, 5})) collectorTakePerTier = TAKE_DEF.clone();
            if (java.util.Arrays.equals(collectorTradesPerTier, new int[]{4, 4, 5, 5, 6, 7, 8})) collectorTradesPerTier = TRADES_DEF.clone();
            if (java.util.Arrays.equals(collectorTalksPerTier, new int[]{1, 1, 1, 2, 2, 3, 3})) collectorTalksPerTier = TALKS_DEF.clone();
        }
    }

    void sanitize() {
        if (taxMinTier == null || dev.tcgshop.shop.ShopTier.byKey(taxMinTier) == null) taxMinTier = "iron";
        taxIntervalDays = Math.max(1, Math.min(365, taxIntervalDays));
        taxIncomeRate = clamp(taxIncomeRate, 0, 0.5);
        taxIncomeRatePerTier = clamp(taxIncomeRatePerTier, 0, 0.1);
        taxIncomeRateMax = clamp(taxIncomeRateMax, taxIncomeRate, 0.5);
        taxPropertyRate = clamp(taxPropertyRate, 0, 0.2);
        taxMinimum = Math.max(0, taxMinimum);
        taxMaximum = Math.max(taxMinimum, taxMaximum);
        taxGraceDays = Math.max(1, Math.min(30, taxGraceDays));
        taxLateFee = clamp(taxLateFee, 0, 2);
        mayorTeamLevelBonus = Math.max(-50, Math.min(50, mayorTeamLevelBonus));
        mayorTeamMinLevel = Math.max(1, Math.min(100, mayorTeamMinLevel));
        mayorTeamMaxLevel = Math.max(mayorTeamMinLevel, Math.min(100, mayorTeamMaxLevel));
        mayorAiSkill = Math.max(0, Math.min(5, mayorAiSkill));
        mayorWalkAwayBlocks = Math.max(12, Math.min(128, mayorWalkAwayBlocks));
        collectorMaxBlockEdits = Math.max(0, Math.min(512, collectorMaxBlockEdits));
        collectorMaxHardness = clamp(collectorMaxHardness, 0, 49);
        collectorTeleportDistance = Math.max(8, Math.min(48, collectorTeleportDistance));
        collectorTeleportFollows = Math.max(0, Math.min(20, collectorTeleportFollows));
        collectorGivePerTier = tiers(collectorGivePerTier, GIVE_DEF.clone(), 1, 30);
        collectorTakePerTier = tiers(collectorTakePerTier, TAKE_DEF.clone(), 1, 6);
        collectorTradesPerTier = tiers(collectorTradesPerTier, TRADES_DEF.clone(), 1, 50);
        collectorTalksPerTier = tiers(collectorTalksPerTier, TALKS_DEF.clone(), 0, 20);
        engineerChance = clamp(engineerChance, 0, 1);
        engineerWanderChance = clamp(engineerWanderChance, 0, 1);
        engineerStayTicks = Math.max(1200, Math.min(240_000, engineerStayTicks));
        engineerPriceMultiplier = clamp(engineerPriceMultiplier, 0.01, 100);
        if (engineerBasic == null || engineerBasic.isEmpty()) engineerBasic = EngineerGoods.defaultBasic();
        if (engineerMachines == null || engineerMachines.isEmpty()) engineerMachines = EngineerGoods.defaultMachines();
        engineerBasic.replaceAll((k, v) -> v == null ? 0L : Math.max(0, v));
        engineerMachines.replaceAll((k, v) -> v == null ? 0L : Math.max(0, v));
        if (_help == null) _help = new LinkedHashMap<>();
    }

    private void fillHelp() {
        _help.clear();
        _help.put("tax*", "From taxMinTier (iron) a Mayor visits shop owners every taxIntervalDays Minecraft days. Tax = taxPropertyRate of your shop cores' value + an income share of the revenue earned since his last visit (taxIncomeRate at the first taxed tier, +taxIncomeRatePerTier per tier above, up to taxIncomeRateMax), clamped to taxMinimum..taxMaximum. Pay = left alone until the next visit. Refuse = a Cobblemon battle against his competitive team; lose, forfeit or walk away and he takes one random card from your binder. Ignored for taxGraceDays: he collects by force with taxLateFee on top (a card if you can't pay).");
        _help.put("mayor*", "His team's level is your best Pokémon's level + mayorTeamLevelBonus, clamped to mayorTeamMinLevel..mayorTeamMaxLevel; mayorAiSkill is Cobblemon's battle AI skill (0-5); going mayorWalkAwayBlocks away from him mid-battle is walking away.");
        _help.put("collector*", "A robbed Wandering Collector hunts you: he breaks blocks in his way (never shop blocks, bedrock, containers or block entities; hardness up to collectorMaxHardness; needs the mobGriefing gamerule) and places blocks to climb, at most collectorMaxBlockEdits per hunt, and puts everything back afterwards (collectorRestoreBlocks). He flies after you with an elytra and reappears about collectorTeleportDistance blocks from you when you teleport (collectorTeleportFollows times per hunt). A boss bar shows his health.");
        _help.put("collector*PerTier", "Per phone tier (Basic..MasterBall Edition): how many of your cards fit in one collector trade, how many of his cards you can take in one trade, trades per visit, and charisma talks per visit (to talk him into a deal that is slightly below his acceptance threshold).");
        _help.put("engineer*", "CREATive Engineer (only with Create installed): every half day one online player is picked: engineerChance if they own a shop or stand near a village, engineerWanderChance for anyone else; he stays engineerStayTicks. engineerBasic: id -> price per stack of 64; engineerMachines: id -> price each. Unknown ids are skipped.");
    }

    private static int[] tiers(int[] a, int[] def, int lo, int hi) {
        if (a == null || a.length != def.length) return def;
        for (int i = 0; i < a.length; i++) a[i] = Math.max(lo, Math.min(hi, a[i]));
        return a;
    }

    private static double clamp(double v, double lo, double hi) {
        return Double.isNaN(v) ? lo : Math.max(lo, Math.min(hi, v));
    }

    /** Value of a per-tier array at that phone tier (clamped). */
    public static int atTier(int[] a, int tier) {
        if (a == null || a.length == 0) return 1;
        return a[Math.max(0, Math.min(a.length - 1, tier))];
    }
}

package dev.tcgshop;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.tcgshop.duel.Cosmetics;
import dev.tcgshop.grading.Grading;
import dev.tcgshop.ptcg.PtcgDuels;
import dev.tcgshop.shop.CardSales;
import dev.tcgshop.shop.ChestLoot;
import dev.tcgshop.shop.ShopLife;
import dev.tcgshop.shop.ShopServer;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * config/tcgshop-economy.json — all economy numbers that are not card/pack prices
 * (those come from the market data and from cobbleDollarsPerUsd in tcgshop.json).
 * Can be edited with the game running and applied with /tcgshop reload (clients receive the new values).
 */
public class EconomyConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("tcgshop-economy.json");

    public static EconomyConfig INSTANCE = new EconomyConfig();

    public Map<String, String> _help = new LinkedHashMap<>();

    /** Fixed price of furniture, tools, shop cores and Mystery Box (item id -> CobbleDollars). */
    public Map<String, Long> itemPrices = new LinkedHashMap<>();
    public long defaultItemPrice = 5_000;
    public long pokedollPrice = 40_000, giganticPokedollPrice = 150_000;

    /** Wholesaler and phone Market: factor over the market price of packs, boxes and cards. */
    public double wholesalerBuyFactor = 0.6, phoneBuyFactor = 0.8;
    /** Quick-sell of cards: % of the value paid by the phone and the Wholesaler. */
    public double phoneSellRate = 0.5, wholesalerSellRate = 0.4;
    // ---- 0.3: furniture (CobbleFurnies / plushies) and reputation
    public double furniturePriceMultiplier = 1.0, furnitureSellerFactor = 0.9, furnitureWholesalerFactor = 1.0, furniturePhoneFactor = 1.15;
    public double furnitureSellerBuyback = 0.5, furnitureOtherBuyback = 0.35;
    public double furnitureSellerChance = 0.35;
    /** Reputation: above repSoftCap there are diminishing returns (higher repTopExponent = harder to reach 100). */
    public int repSoftCap = 60;
    public double repTopExponent = 1.7;
    /** Speed of reputation toward its target on each recalculation (1 min): fraction of the difference when rising (capped) and when falling. */
    public double repRiseRate = 0.06, repMaxRisePerUpdate = 1.0, repFallRate = 0.3;
    /** Sales needed to be able to reach reputation 100 (ceiling = repSoftCap + rest*sqrt(sales/this)). 0 = no ceiling. */
    public int repSalesForMax = 400;
    /** Markup accepted by reputation: 0 up to repPremiumStart, then up to repPremiumMax (+30%) at 100 with the repPremiumCurve curve. */
    public int repPremiumStart = 40;
    public double repPremiumMax = 0.30, repPremiumCurve = 1.5;
    /** Markup multiplier for packs, boxes and Mystery Box. */
    public double repPremiumSealedBonus = 1.5;
    /** Below this reputation customers want below-market prices (up to -repPenaltyMax at reputation 0). */
    public int repPenaltyBelow = 25;
    public double repPenaltyMax = 0.10;
    /** Mood a customer needs to leave 5 stars (starts at 3). */
    public int fiveStarMood = 6;
    /** Minimum market price. */
    public long minPackPrice = 100, minBoxPrice = 1_000, minCardPrice = 10;

    /** Employees: hiring fee + daily wage. */
    public long cashierHire = 30_000, cashierWage = 4_000, guardHire = 45_000, guardWage = 6_000;
    /** Multiplier for customers arriving at your shop (1 = normal, 2 = double). */
    public double customerDemand = 1.0;

    /** House duels by level (easy, normal, hard, expert): reward on win, penalty on loss in league. */
    public long[] houseDuelRewards = {800, 1_500, 3_000, 6_000};
    public long[] houseDuelPenalties = {200, 400, 800, 1_500};
    /** Penalty for losing a league match against another player. */
    public long pvpPenalty = 600;

    /** Grading: fixed cost per card + % of the value (economy, standard, express). */
    public long[] gradingBaseFees = {2_500, 6_000, 15_000};
    public double[] gradingValuePct = {0.03, 0.06, 0.10};

    /** Chance that a structure chest contains a pack (needs /reload to apply). */
    public double chestPackChance = 0.01;
    /** Chance per opened pack of a loot coin dropping. */
    public double lootCoinChancePerPack = 1 / 140.0;
    /** Locks the N sets with the most expensive pack: not sold nor found in chests, kits, Mystery Box or NPCs (0 = none). */
    public int lockTopPackSets = 5;
    /** Minimum shop tier to be able to BUY those sets on the phone and from the Wholesaler (wood, stone, iron, gold, diamond, emerald, netherite). */
    public String lockedPacksMinShopTier = "diamond";
    /** Chance, in any chest, of finding a pack from the locked sets (0.00001 = 0.001 %). */
    public double lockedPackChestChance = 0.00001;

    // --- Customers and merchants ---
    /** Maximum seconds a customer who wants a deal waits before leaving. */
    public int customerWaitSeconds = 60;
    /** When rejecting a deal: chance of a negative review (otherwise neutral). Polite / rude. */
    public double rejectNegativeChancePolite = 0.18, rejectNegativeChanceRude = 0.75;
    /** At most this many negative reviews from rejections per day and per shop (the rest stay neutral). */
    public int maxRejectPenaltiesPerDay = 2;
    /** Merchants' patience when haggling (multiplier; 1 = normal). */
    public double merchantPatience = 1.0;

    // --- Reputation ---
    /** Weight of each score in the total reputation (normalized). */
    public double repWeightAesthetics = 0.35, repWeightValue = 0.40, repWeightSafety = 0.25;
    /** Reputation lost for killing a shop NPC (customers, Wholesaler, merchant, employees) and how much is recovered per day. */
    public int infamyPerKill = 12, infamyDecayPerDay = 3;

    // --- Special encounters ---
    /** Chance (every half day) of the card beggar / the black-market dealer appearing. */
    public double beggarChance = 0.25, blackMarketChance = 0.02;
    /** Chance that the black-market dealer offers a pack from the locked sets. */
    public double blackMarketLockedChance = 0.12;

    // --- Notifications ---
    /** Announce all of the mod's advancements in chat (not just challenges). */
    public boolean announceAllAdvancements = true;

    private void fillHelp() {
        _help.clear();
        _help.put("aplicar", "Edit this file and run /tcgshop reload (operator) or restart. Card/pack prices are tuned with cobbleDollarsPerUsd in tcgshop.json or with a datapack.");
        _help.put("itemPrices", "Fixed price of each furniture piece/tool/shop core (shop_core_*) in CobbleDollars.");
        _help.put("wholesalerBuyFactor/phoneBuyFactor", "What packs, boxes and cards cost when bought from the Wholesaler / on the phone, relative to market price (0.6 = 60%).");
        _help.put("phoneSellRate/wholesalerSellRate", "What they pay for your cards in quick-sell (0.5 = 50% of the value).");
        _help.put("customerDemand", "Multiplier for customers arriving at your shop.");
        _help.put("houseDuelRewards/houseDuelPenalties", "Reward and penalty of duels against the house per level: easy, normal, hard, expert.");
        _help.put("lockTopPackSets", "The N sets with the most expensive sealed pack (e.g. Base Set) cannot be obtained: not from shops, chests, the starter kit, the Mystery Box or NPCs. 0 = off. Ones you already own are not touched.");
        _help.put("lockedPacksMinShopTier", "Shop tier you need to buy the locked sets on the phone and from the Wholesaler.");
        _help.put("lockedPackChestChance", "Chance per chest to find a pack from the locked sets (0.00001 = 0.001%).");
        _help.put("customerWaitSeconds", "Maximum time a customer who wants a deal waits (shown as a bar above their head).");
        _help.put("rejectNegativeChancePolite/Rude", "Chance of a negative review when you reject a deal politely / rudely. Otherwise the review is neutral.");
        _help.put("repWeightAesthetics/Value/Safety", "Weight of Aesthetics, Value and Safety in the total reputation.");
        _help.put("infamyPerKill/infamyDecayPerDay", "Reputation you lose for killing a shop NPC and how much you recover each day.");
        _help.put("beggarChance/blackMarketChance", "Chance every half Minecraft day that the card beggar / the black marketeer (very rare) appears.");
        _help.put("gradingBaseFees/gradingValuePct", "Grading cost per card: fixed + % of the value (economy, standard, express).");
        _help.put("repSoftCap/repTopExponent", "Above repSoftCap reputation has diminishing returns (higher exponent = harder to reach 100).");
        _help.put("repRiseRate/repMaxRisePerUpdate/repFallRate", "How fast reputation moves toward its target each minute: fraction of the gap when rising (with a points cap) and when falling.");
        _help.put("repSalesForMax", "Sales needed to be able to reach reputation 100 (before that there is a lower ceiling). 0 = no sales ceiling.");
        _help.put("repPremiumStart/repPremiumMax/repPremiumCurve", "Markup customers accept based on reputation: none up to repPremiumStart, then up to repPremiumMax (0.3 = +30%) at reputation 100, following that curve.");
        _help.put("repPremiumSealedBonus", "Multiplier on the accepted markup for packs, boxes and the Mystery Box.");
        _help.put("repPenaltyBelow/repPenaltyMax", "Below that reputation customers want below-market prices (down to -repPenaltyMax at reputation 0).");
        _help.put("fiveStarMood", "Mood a customer needs to leave 5 stars (starts at 3; a good purchase adds 2-3).");
    }

    // ------------------------------------------------------------------ loading

    /** Call after registering the items (ShopServer.init) so the default prices are known. */
    public static void load() {
        EconomyConfig c = null;
        try {
            if (Files.exists(FILE)) try (Reader r = Files.newBufferedReader(FILE)) { c = GSON.fromJson(r, EconomyConfig.class); }
        } catch (Exception e) {
            TcgShop.LOGGER.error("Could not read {} (using default values)", FILE, e);
        }
        if (c == null) c = new EconomyConfig();
        c.sanitize();
        INSTANCE = c;
        c.fillHelp();
        save();
        apply(c);
    }

    private void sanitize() {
        if (itemPrices == null) itemPrices = new LinkedHashMap<>();
        // add any missing items with their default price (new items in updates)
        Map<String, Long> merged = new LinkedHashMap<>(ShopServer.defaultFixedPrices());
        for (Map.Entry<String, Long> e : itemPrices.entrySet()) if (e.getValue() != null) merged.put(e.getKey(), Math.max(0, e.getValue()));
        itemPrices = merged;
        houseDuelRewards = len(houseDuelRewards, new long[]{800, 1_500, 3_000, 6_000});
        houseDuelPenalties = len(houseDuelPenalties, new long[]{200, 400, 800, 1_500});
        gradingBaseFees = len(gradingBaseFees, new long[]{2_500, 6_000, 15_000});
        if (gradingValuePct == null || gradingValuePct.length != 3) gradingValuePct = new double[]{0.03, 0.06, 0.10};
        wholesalerBuyFactor = clamp(wholesalerBuyFactor, 0.01, 100);
        phoneBuyFactor = clamp(phoneBuyFactor, 0.01, 100);
        phoneSellRate = clamp(phoneSellRate, 0, 10);
        wholesalerSellRate = clamp(wholesalerSellRate, 0, 10);
        customerDemand = clamp(customerDemand, 0, 20);
        chestPackChance = clamp(chestPackChance, 0, 1);
        lootCoinChancePerPack = clamp(lootCoinChancePerPack, 0, 1);
        lockTopPackSets = Math.max(0, Math.min(50, lockTopPackSets));
        if (lockedPacksMinShopTier == null) lockedPacksMinShopTier = "diamond";
        lockedPackChestChance = clamp(lockedPackChestChance, 0, 1);
        customerWaitSeconds = Math.max(10, Math.min(600, customerWaitSeconds));
        rejectNegativeChancePolite = clamp(rejectNegativeChancePolite, 0, 1);
        rejectNegativeChanceRude = clamp(rejectNegativeChanceRude, 0, 1);
        maxRejectPenaltiesPerDay = Math.max(0, maxRejectPenaltiesPerDay);
        merchantPatience = clamp(merchantPatience, 0.2, 5);
        repWeightAesthetics = clamp(repWeightAesthetics, 0, 10);
        repWeightValue = clamp(repWeightValue, 0, 10);
        repWeightSafety = clamp(repWeightSafety, 0, 10);
        if (repWeightAesthetics + repWeightValue + repWeightSafety <= 0) { repWeightAesthetics = 0.35; repWeightValue = 0.4; repWeightSafety = 0.25; }
        infamyPerKill = Math.max(0, Math.min(100, infamyPerKill));
        infamyDecayPerDay = Math.max(0, Math.min(100, infamyDecayPerDay));
        beggarChance = clamp(beggarChance, 0, 1);
        blackMarketChance = clamp(blackMarketChance, 0, 1);
        blackMarketLockedChance = clamp(blackMarketLockedChance, 0, 1);
        repSoftCap = Math.max(0, Math.min(100, repSoftCap));
        repTopExponent = clamp(repTopExponent, 1, 5);
        repRiseRate = clamp(repRiseRate, 0.001, 1);
        repMaxRisePerUpdate = clamp(repMaxRisePerUpdate, 0.05, 100);
        repFallRate = clamp(repFallRate, 0.01, 1);
        repSalesForMax = Math.max(0, repSalesForMax);
        repPremiumStart = Math.max(0, Math.min(99, repPremiumStart));
        repPremiumMax = clamp(repPremiumMax, 0, 5);
        repPremiumCurve = clamp(repPremiumCurve, 0.1, 5);
        repPremiumSealedBonus = clamp(repPremiumSealedBonus, 0, 5);
        repPenaltyBelow = Math.max(0, Math.min(100, repPenaltyBelow));
        repPenaltyMax = clamp(repPenaltyMax, 0, 0.9);
        fiveStarMood = Math.max(5, Math.min(12, fiveStarMood));
        if (_help == null) _help = new LinkedHashMap<>();
    }

    private static long[] len(long[] a, long[] def) {
        if (a == null || a.length != def.length) return def;
        for (int i = 0; i < a.length; i++) a[i] = Math.max(0, a[i]);
        return a;
    }

    private static double clamp(double v, double lo, double hi) {
        return Double.isNaN(v) ? lo : Math.max(lo, Math.min(hi, v));
    }

    public static void save() {
        try (Writer w = Files.newBufferedWriter(FILE)) {
            GSON.toJson(INSTANCE, w);
        } catch (Exception e) {
            TcgShop.LOGGER.error("Could not save {}", FILE, e);
        }
    }

    /** Copies the values into each system's constants (server and, on receiving the sync, client). */
    private static void apply(EconomyConfig c) {
        ShopServer.applyEconomy(c);
        CardSales.PHONE_RATE = c.phoneSellRate;
        CardSales.WHOLESALER_RATE = c.wholesalerSellRate;
        dev.tcgshop.shop.FurnitureShop.PRICE_MULT = Math.max(0.01, c.furniturePriceMultiplier);
        dev.tcgshop.shop.FurnitureShop.SELLER_FACTOR = Math.max(0.01, c.furnitureSellerFactor);
        dev.tcgshop.shop.FurnitureShop.WHOLESALER_FACTOR = Math.max(0.01, c.furnitureWholesalerFactor);
        dev.tcgshop.shop.FurnitureShop.PHONE_FACTOR = Math.max(0.01, c.furniturePhoneFactor);
        dev.tcgshop.shop.FurnitureShop.SELLER_BUYBACK = Math.max(0, c.furnitureSellerBuyback);
        dev.tcgshop.shop.FurnitureShop.OTHER_BUYBACK = Math.max(0, c.furnitureOtherBuyback);
        dev.tcgshop.shop.FurnitureShop.SELLER_CHANCE = Math.max(0, Math.min(1, c.furnitureSellerChance));
        PtcgDuels.LEVEL_REWARD = c.houseDuelRewards.clone();
        PtcgDuels.LEVEL_PENALTY = c.houseDuelPenalties.clone();
        PtcgDuels.PVP_PENALTY = c.pvpPenalty;
        Grading.Service[] sv = Grading.Service.values();
        for (int i = 0; i < sv.length && i < 3; i++) {
            sv[i].baseFee = c.gradingBaseFees[i];
            sv[i].pct = c.gradingValuePct[i];
        }
        ChestLoot.CHANCE = (float) c.chestPackChance;
        Cosmetics.LOOT_CHANCE = c.lootCoinChancePerPack;
        ShopLife.DEMAND = c.customerDemand;
    }

    /** For sending to the client. */
    public static String toJson() {
        com.google.gson.JsonObject o = new com.google.gson.JsonObject();
        o.add("economy", GSON.toJsonTree(INSTANCE));
        o.add("vanilla", GSON.toJsonTree(dev.tcgshop.shop.VanillaPrices.table()));
        return GSON.toJson(o);
    }

    /** Client: applies the server's values (without touching the local file). */
    public static void applyJson(String json) {
        if (json == null || json.isEmpty()) return;
        try {
            com.google.gson.JsonObject o = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
            if (o.has("vanilla")) dev.tcgshop.shop.VanillaPrices.applySync(o.getAsJsonObject("vanilla"));
            EconomyConfig c = GSON.fromJson(o.has("economy") ? o.get("economy") : o, EconomyConfig.class);
            if (c == null) return;
            c.sanitize();
            INSTANCE = c;
            apply(c);
        } catch (Exception e) {
            TcgShop.LOGGER.warn("Invalid server economy configuration", e);
        }
    }
}

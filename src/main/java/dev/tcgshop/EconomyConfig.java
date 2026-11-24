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
    // ---- 1.0 (traders): Cobblemon items the traders buy (see shop.TraderGoods); 0 = not bought
    /** Wholesaler: CobbleDollars per Cobblemon Poké Ball (any kind). Any trader: per Mega Stone. */
    public long pokeBallSellPrice = 50, megaStoneSellPrice = 50_000;
    /** 1.0 migration marker for the traders' fixed prices (cash register = 2,000). Absent in old files = 0. */
    public int traderPricesVersion = 0;
    /** 1.0 migration marker for the Manager Goggles price (2,000). Absent in old files = 0. */
    public int gogglesPriceVersion = 0;
    // ---- 1.0 shop tiers (shop/ShopTier, ShopCoreBlock)
    /** One shop per player until one of their shops is Amethyst or higher (players who already own several keep them). */
    public boolean oneShopPerPlayer = true;
    /** Most shops a player may own in total, even from Amethyst on (players already above it keep theirs but can't add more). 0 = no cap. */
    public int maxShopsPerPlayer = 5;
    /** Small tiers with a display limit (the Paper stall): most slots its selling display may have (RGB displays are never allowed there). */
    public int stallMaxDisplaySlots = 4;
    /** 1.0 migration marker for the shop core prices (the Wood shop is now 10×10 and costs 10,000). Absent in old files = 0. */
    public int shopTiersVersion = 0;
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
    /** 1.0: lowest price (fraction of market) a customer who SELLS you something ever accepts, after charisma, missions luck and arguments. */
    public double customerSellFloor = 0.45;

    // --- Reputation ---
    /** Weight of each score in the total reputation (normalized). */
    public double repWeightAesthetics = 0.35, repWeightValue = 0.40, repWeightSafety = 0.25;
    /** Reputation lost for killing a shop NPC (customers, Wholesaler, merchant, employees) and how much is recovered per day. */
    public int infamyPerKill = 12, infamyDecayPerDay = 3;
    /**
     * 1.0 beta 3, stealing from the Wandering Collector (entity/CollectorTheft; clamped where read): reputation points and
     * infamy every shop of the thief loses when they escape (and, with collectorKillPenaltyChance, when they kill him),
     * the smaller loss when he kills the thief, and his health / hit damage (sword included) while he hunts.
     */
    public int collectorTheftRepLoss = 20, collectorTheftInfamy = 35, collectorDeathRepLoss = 8, collectorDeathInfamy = 10;
    public double collectorKillPenaltyChance = 0.5, collectorRobbedHealth = 80, collectorRobbedDamage = 8;

    // --- Special encounters ---
    /** Chance (every half day) of the card beggar / the black-market dealer appearing. */
    public double beggarChance = 0.25, blackMarketChance = 0.02;
    /** Chance that the black-market dealer offers a pack from the locked sets. */
    public double blackMarketLockedChance = 0.12;

    // --- 1.0 onboarding (onboarding/*) ---
    /** Money given once, when a new player (one who got the 1.0 starter kit) opens their first pack. 0 = off. */
    public long startingMoney = 5_000;
    /** One-time unique collector: on/off, play time (ticks of the PLAY_TIME stat) before he visits, and his offer for the card he likes. */
    public boolean uniqueCollectorEnabled = true;
    public int uniqueCollectorPlayTicks = 60_000;
    public long uniqueCollectorOffer = 5_000;
    /** One-time taunting rival: on/off, play time without owning a shop before he shows up (96,000 = 4 Minecraft days), value of the card he throws. */
    public boolean taunterEnabled = true;
    public int taunterNoShopPlayTicks = 96_000;
    public long taunterCardValue = 15_000;
    /** Extra Wholesaler visits for players without a shop: chance of each of the two extra rolls every half day. 0 = off. */
    public double wholesalerNoShopChance = 0.35;
    /** 1.0 migration marker: starterPackCount in tcgshop.json moved from the old default 3 to 5. Absent in old files = 0. */
    public int starterPacksVersion = 0;

    // --- Notifications ---
    /** Announce all of the mod's advancements in chat (not just challenges). */
    public boolean announceAllAdvancements = true;

    // --- 1.0 play table (ptcg/PlayTables; values are clamped where they are read) ---
    /** Chairs (CobbleFurnies or any modded seat) the play table needs nearby before anyone can play at it. 0 = no requirement. */
    public int tableChairsRequired = 2;
    /** How far (blocks, horizontally; one block up or down) a chair may be from the table. */
    public int tableChairRadius = 2;
    /** Without CobbleFurnies or Cozyhome installed, bottom-half stairs count as chairs (so the table stays usable). */
    public boolean tableStairsWithoutFurnitureMods = true;
    /** Chance, every 5 s while you stand near a ready table in a shop, that a customer challenges you to a match. */
    public double tableChallengeChance = 0.02;
    /** Minimum seconds between two customer challenges to the same player. */
    public int tableChallengeCooldownSeconds = 600;
    /** Chance, every 5 s per free ready table in a visited shop, that two customers sit down to play a match. */
    public double tableExhibitionChance = 0.025;
    /** How long (seconds) two customers play at the table: random between min and max. */
    public int tableExhibitionMinSeconds = 60, tableExhibitionMaxSeconds = 150;

    // --- 1.0 Wandering Duelist (world/duelist; values are clamped where they are read) ---
    /** Chance, every 30 s of daytime per eligible player (Overworld surface, outside shops), that the Wandering Duelist sets up nearby. 0 = off. */
    public double duelistChance = 0.008;
    /** Prize for beating him, and the most cash he takes when you lose (with no cash on you he takes one random inventory stack). */
    public long duelistPrize = 10_000, duelistMaxTake = 50_000;
    /** 1.0 beta 3: the stake is held when you accept; who carries cash must stake at least this much (no cash at all = the item rule). */
    public long duelistMinStake = 1_000;
    /** Minimum seconds between two Wandering Duelist encounters for the same player. */
    public int duelistCooldownSeconds = 3_600;

    // --- 1.0 store discount event (shop/Discounts; values are clamped where they are read) ---
    /** Chance, once per Minecraft day (24,000 ticks), that one store item goes on sale. 0 = off. */
    public double discountDailyChance = 0.04;
    /** Relative odds of a 10 %, 30 % and 50 % discount (packs, boxes and the Mystery Box only roll 10 or 30 %). */
    public int[] discountWeights = {80, 17, 3};
    /** How expensive items are avoided: weight = (cheapest price / price) ^ exponent. Higher = cheap items even more likely. */
    public double discountPriceExponent = 1.5;
    /** Duration in Minecraft days: random between min and max. */
    public int discountMinDays = 1, discountMaxDays = 3;

    // --- 1.0 shop missions luck and charisma (shop/ShopMissions, shop/Charisma; values are clamped where they are read) ---
    /** Extra deal acceptance with ALL missions completed (0.15 = +15 %), smoothly less with fewer. */
    public double missionMaxAcceptBonus = 0.15;
    /** Extra checkout tip chance with ALL missions completed (0.08 = +8 points). */
    public double missionMaxTipBonus = 0.08;
    /** Haggling bonus at charisma level 20 (0.25 = customers concede up to 25 % more). */
    public double charismaMaxHaggleBonus = 0.25;
    /** Charisma XP for a closed deal, extra XP when you haggled a better price than the opening one, and per tip received. */
    public int charismaXpDeal = 4, charismaXpHaggle = 6, charismaXpTip = 3;

    // --- 1.0 beta 3 unique customers, thief tiers and guard upgrades (entity/FixedCustomers, entity/ThiefTiers, shop/GuardUpgrades; clamped where read) ---
    /** Chance that a naturally arriving customer is one of the 10 unique customers (the excellent one and the known thief are rarer). 0 = off. */
    public double uniqueCustomerChance = 0.02;
    /** Armed robbers and heist crews at popular shops: on/off and a multiplier for their (already very low) chance. */
    public boolean armedThieves = true;
    public double armedThiefChanceMult = 1.0;
    /** Minimum Minecraft days between two armed robberies / two heists at the same shop. */
    public int robberyCooldownDays = 3, heistCooldownDays = 12;
    /** Price multiplier of the guard upgrades (armor, weapon, training, detection). */
    public double guardUpgradeCostMult = 1.0;

    private void fillHelp() {
        _help.clear();
        _help.put("aplicar", "Edit this file and run /tcgshop reload (operator) or restart. Card/pack prices are tuned with cobbleDollarsPerUsd in tcgshop.json or with a datapack.");
        _help.put("itemPrices", "Fixed price of each furniture piece/tool/shop core (shop_core_*) in CobbleDollars.");
        _help.put("oneShopPerPlayer", "1.0: a player can own only one shop until one of their shops is Amethyst or higher. Players who already own several keep them. false = no limit.");
        _help.put("maxShopsPerPlayer", "1.0: most shops a player may own in total, even from Amethyst on. Players who already own more keep them but cannot open another. 0 = no cap.");
        _help.put("stallMaxDisplaySlots", "1.0: the Paper stall only accepts one small selling display: at most this many slots, and never an RGB display.");
        _help.put("wholesalerBuyFactor/phoneBuyFactor", "What packs, boxes and cards cost when bought from the Wholesaler / on the phone, relative to market price (0.6 = 60%).");
        _help.put("phoneSellRate/wholesalerSellRate", "What they pay for your cards in quick-sell (0.5 = 50% of the value).");
        _help.put("pokeBallSellPrice/megaStoneSellPrice", "What the Wholesaler pays per Cobblemon Poke Ball, and what any trader (Wholesaler, Wandering Collector, Furniture Vendor) pays per Mega Stone. 0 = not bought. Vanilla items are priced in tcgshop-vanilla-prices.json.");
        _help.put("customerDemand", "Multiplier for customers arriving at your shop.");
        _help.put("houseDuelRewards/houseDuelPenalties", "Reward and penalty of duels against the house per level: easy, normal, hard, expert.");
        _help.put("lockTopPackSets", "The N sets with the most expensive sealed pack (e.g. Base Set) cannot be obtained: not from shops, chests, the starter kit, the Mystery Box or NPCs. 0 = off. Ones you already own are not touched.");
        _help.put("lockedPacksMinShopTier", "Shop tier you need to buy the locked sets on the phone and from the Wholesaler.");
        _help.put("lockedPackChestChance", "Chance per chest to find a pack from the locked sets (0.00001 = 0.001%).");
        _help.put("customerWaitSeconds", "Maximum time a customer who wants a deal waits (shown as a bar above their head).");
        _help.put("rejectNegativeChancePolite/Rude", "Chance of a negative review when you reject a deal politely / rudely. Otherwise the review is neutral.");
        _help.put("customerSellFloor", "1.0: the lowest price, as a fraction of market (0.45 = 45%), a customer who sells you a card/pack/box ever asks or accepts, whatever your charisma, mission luck or haggling arguments. Keep it close to phoneSellRate so buying from customers and quick-selling is not free money.");
        _help.put("repWeightAesthetics/Value/Safety", "Weight of Aesthetics, Value and Safety in the total reputation.");
        _help.put("infamyPerKill/infamyDecayPerDay", "Reputation you lose for killing a shop NPC and how much you recover each day.");
        _help.put("collector*", "1.0: stealing a card from the Wandering Collector. collectorTheftRepLoss/Infamy: reputation points and infamy all your shops lose if you escape (and, with collectorKillPenaltyChance, if you kill him). collectorDeathRepLoss/Infamy: the loss if he kills you (the stolen card vanishes too). collectorRobbedHealth/Damage: his health and hit damage while he hunts you.");
        _help.put("beggarChance/blackMarketChance", "Chance every half Minecraft day that the card beggar / the black marketeer (very rare) appears.");
        _help.put("startingMoney", "1.0: money a new player receives once, when they open their first pack (only players who got the 1.0 starter kit). 0 = off.");
        _help.put("uniqueCollector*", "1.0: one-time unique collector. After uniqueCollectorPlayTicks of play time (60,000 = 2.5 Minecraft days) he visits once and offers uniqueCollectorOffer for one card you own.");
        _help.put("taunter*", "1.0: one-time taunting rival. After taunterNoShopPlayTicks of play time without a shop (96,000 = 4 Minecraft days) he throws you a card worth about taunterCardValue and teases you into opening a shop.");
        _help.put("wholesalerNoShopChance", "1.0: players without a shop get two extra Wholesaler rolls every half Minecraft day with this chance each. 0 = off.");
        _help.put("gradingBaseFees/gradingValuePct", "Grading cost per card: fixed + % of the value (economy, standard, express).");
        _help.put("repSoftCap/repTopExponent", "Above repSoftCap reputation has diminishing returns (higher exponent = harder to reach 100).");
        _help.put("repRiseRate/repMaxRisePerUpdate/repFallRate", "How fast reputation moves toward its target each minute: fraction of the gap when rising (with a points cap) and when falling.");
        _help.put("repSalesForMax", "Sales needed to be able to reach reputation 100 (before that there is a lower ceiling). 0 = no sales ceiling.");
        _help.put("repPremiumStart/repPremiumMax/repPremiumCurve", "Markup customers accept based on reputation: none up to repPremiumStart, then up to repPremiumMax (0.3 = +30%) at reputation 100, following that curve.");
        _help.put("repPremiumSealedBonus", "Multiplier on the accepted markup for packs, boxes and the Mystery Box.");
        _help.put("repPenaltyBelow/repPenaltyMax", "Below that reputation customers want below-market prices (down to -repPenaltyMax at reputation 0).");
        _help.put("fiveStarMood", "Mood a customer needs to leave 5 stars (starts at 3; a good purchase adds 2-3).");
        _help.put("table*", "1.0: the play table needs tableChairsRequired chairs (CobbleFurnies or any modded seat) within tableChairRadius blocks. With chairs, customers may challenge you (tableChallengeChance every 5 s, tableChallengeCooldownSeconds between challenges) or two of them sit and play a match for a while (tableExhibitionChance every 5 s).");
        _help.put("duelist*", "1.0: Wandering Duelist. During the day, every 30 s, each player on the Overworld surface outside a shop has duelistChance of a pro player setting up a play table and two chairs on flat natural ground nearby (duelistCooldownSeconds between encounters; needs CobbleDollars). He challenges you to one Hard real-TCG match. On accept he holds your stake: all the cash you carry up to duelistMaxTake (at least duelistMinStake, or no cash at all). Win = the stake back + duelistPrize; lose = he keeps the stake, or one random inventory stack if you carried no cash; a draw or a match that never starts = the stake back. Then he packs up and vanishes.");
        _help.put("uniqueCustomerChance", "1.0: chance that an arriving customer is one of the 10 unique customers (rainbow names, own lines). The excellent customer (Don Aurelio) and the known thief (Vinnie Vex) are rarer; none comes twice the same day.");
        _help.put("armedThieves", "1.0: popular shops (reputation, tier, sales) attract better thieves. Armed robbers (Copper tier+) and heist crews (Gold tier+, empty every display) are very rare; armedThiefChanceMult scales their chance, robberyCooldownDays / heistCooldownDays space them out. Kill them before they escape to get the stock back.");
        _help.put("guardUpgradeCostMult", "1.0: price multiplier of the guard upgrades (armor, weapon, training, detection; sneak + right-click your guard).");
        _help.put("missionMax*", "1.0: luck from shop missions. With every mission done, customers accept deals up to missionMaxAcceptBonus more often and tip up to missionMaxTipBonus more often (scaled smoothly by missions completed).");
        _help.put("charisma*", "1.0: per-player charisma level (1-20). XP: charismaXpDeal per closed deal, +charismaXpHaggle if you haggled a better price, charismaXpTip per tip. At level 20 haggling is charismaMaxHaggleBonus more effective.");
        _help.put("discount*","1.0: very rare sale. Once per Minecraft day, with discountDailyChance, one phone store / Wholesaler item gets 10, 30 or 50% off (discountWeights; packs, boxes and the Mystery Box never get 50%) for discountMinDays-discountMaxDays days. Cheap items are much more likely (discountPriceExponent).");
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
        // 1.0 price migrations: only replace the OLD default, so a price an admin customized is kept (same rule as PhoneConfig)
        if (c.traderPricesVersion < 1) { // the cash register costs 2,000 (phone store and Wholesaler); old default 12,000
            if (c.itemPrices.getOrDefault("tcgshop:cash_register", 12_000L) == 12_000L) c.itemPrices.put("tcgshop:cash_register", 2_000L);
            c.traderPricesVersion = 1;
        }
        if (c.traderPricesVersion < 2) { // 1.0 release: Poke Balls pay 50 at the Wholesaler; old default 200
            if (c.pokeBallSellPrice == 200L) c.pokeBallSellPrice = 50L;
            c.traderPricesVersion = 2;
        }
        if (c.gogglesPriceVersion < 1) { // the Manager Goggles cost 2,000 wherever they are sold; old default 15,000
            if (c.itemPrices.getOrDefault("tcgshop:manager_goggles", 15_000L) == 15_000L) c.itemPrices.put("tcgshop:manager_goggles", 2_000L);
            c.gogglesPriceVersion = 1;
        }
        if (c.shopTiersVersion < 1) { // the Wood shop is now 10×10 and costs 10,000; old default 15,000
            if (c.itemPrices.getOrDefault("tcgshop:shop_core_wood", 15_000L) == 15_000L) c.itemPrices.put("tcgshop:shop_core_wood", 10_000L);
            c.shopTiersVersion = 1;
        }
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
        pokeBallSellPrice = Math.max(0, pokeBallSellPrice);
        megaStoneSellPrice = Math.max(0, megaStoneSellPrice);
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
        customerSellFloor = clamp(customerSellFloor, 0, 2);
        repWeightAesthetics = clamp(repWeightAesthetics, 0, 10);
        repWeightValue = clamp(repWeightValue, 0, 10);
        repWeightSafety = clamp(repWeightSafety, 0, 10);
        if (repWeightAesthetics + repWeightValue + repWeightSafety <= 0) { repWeightAesthetics = 0.35; repWeightValue = 0.4; repWeightSafety = 0.25; }
        infamyPerKill = Math.max(0, Math.min(100, infamyPerKill));
        infamyDecayPerDay = Math.max(0, Math.min(100, infamyDecayPerDay));
        beggarChance = clamp(beggarChance, 0, 1);
        blackMarketChance = clamp(blackMarketChance, 0, 1);
        blackMarketLockedChance = clamp(blackMarketLockedChance, 0, 1);
        startingMoney = Math.max(0, startingMoney);
        uniqueCollectorPlayTicks = Math.max(0, uniqueCollectorPlayTicks);
        uniqueCollectorOffer = Math.max(0, uniqueCollectorOffer);
        taunterNoShopPlayTicks = Math.max(0, taunterNoShopPlayTicks);
        taunterCardValue = Math.max(0, taunterCardValue);
        wholesalerNoShopChance = clamp(wholesalerNoShopChance, 0, 1);
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
        maxShopsPerPlayer = Math.max(0, Math.min(1000, maxShopsPerPlayer));
        stallMaxDisplaySlots = Math.max(1, Math.min(64, stallMaxDisplaySlots));
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

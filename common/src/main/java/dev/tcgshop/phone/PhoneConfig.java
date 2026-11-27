package dev.tcgshop.phone;

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

/** config/tcgshop-phone.json — phone upgrades (0.9). Read on startup and by PhoneConfig.load(). */
public class PhoneConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Path FILE = Platform.INSTANCE.configDir().resolve("tcgshop-phone.json");

    public static PhoneConfig INSTANCE = new PhoneConfig();

    public Map<String, String> _help = new LinkedHashMap<>();

    /** false = upgrade system disabled: everyone has everything unlocked (except the daily call if callsEnabled = false). */
    public boolean enabled = true;
    /** Cost of each tier (index 0 = Basic, always free). */
    public long[] tierCosts = PhoneTier.DEFAULT_COSTS.clone();
    /** Max pack price (market USD) per tier 0..5; tier 6 has no limit. */
    public double[] packUsdLimits = PhoneTier.DEFAULT_PACK_USD.clone();
    /** Daily call of the top tier (Collector or Wholesaler). */
    public boolean callsEnabled = true;
    /** 1.1: phone tier from which the Furniture Seller can be called once per day (1 = Plus); above 6 = never. Absent in older files: 1. */
    public int furnitureCallMinTier = PhoneTier.FURN_CALL_TIER;
    /** Starting tier for players who already had a shop / had already played before 0.9. */
    public int legacyShopOwnerTier = 2, legacyPlayerTier = 1;
    /** Players from before 0.9 keep ALL the apps they already used (Prices, CobbleGrade, League...). */
    public boolean legacyKeepsApps = true;
    /**
     * 1.0: minimum phone tier to buy x36 booster boxes in the phone Market. 0 = a box follows the same tier limit
     * as its pack (packUsdLimits). Absent in older files: 0.
     */
    public int boxMinTier = 0;

    // ---- 1.0: Wholesaler loans (Loans). Absent in older files: these defaults.
    /** false = the Wholesaler never offers loans (active loans can still be repaid). */
    public boolean loansEnabled = true;
    /** Minimum phone tier to receive loan offers (2 = Pro). */
    public int loanMinTier = 2;
    /** Hard cap of any loan offer (never more than this, whatever the tier and credit). */
    public long loanMaxAmount = 50_000;
    /** Largest offer at each phone tier (index = tier) with an average credit score (50); credit scales it x0.5..x1.5. */
    public long[] loanAmountByTier = {0, 0, 2_000, 5_000, 12_000, 25_000, 50_000};
    /** Average in-game days between two offers at each phone tier (index = tier); good credit shortens it, bad credit lengthens it. */
    public int[] loanOfferGapDays = {0, 0, 14, 9, 6, 4, 3};
    /** In-game days to repay a loan at each phone tier (index = tier). */
    public int[] loanTermDays = {0, 0, 5, 6, 7, 9, 12};
    /** Interest range: the best credit gets loanRateMin, the worst loanRateMax (0.10 = 10%). */
    public double loanRateMin = 0.10, loanRateMax = 0.20;
    /** In-game days an offer can be accepted before it expires. */
    public int loanOfferDays = 2;
    /** Late fee added to the debt for every in-game day past the due date (0.02 = 2% of what is still owed). */
    public double loanLateFeePerDay = 0.02;
    /** Days late after which the loan is "very late": credit -15, a small reputation penalty and no offers until repaid. */
    public int loanVeryLateDays = 7;
    /** Reputation points every shop of the borrower loses once when the loan becomes very late. */
    public int loanRepPenalty = 3;
    /** 1.0 beta 2: days past the due date after which the Wholesaler collects the debt by force (grace period). */
    public int loanCollectAfterDays = 7;
    /** Daily installment of the forced collection, as a fraction of what was owed when it started (0 = never collect). */
    public double loanCollectPerDay = 0.25;
    /** 1.1: the big "Diamond loan" anyone owning a Diamond (or better) shop can request from the Loans app. */
    public boolean diamondLoanEnabled = true;
    public long diamondLoanAmount = 1_000_000;
    /** In-game days to repay the Diamond loan (interest follows the credit score like any other loan). */
    public int diamondLoanTermDays = 20;

    /** Format version of this file (absent in older files = 0). Bumped to CURRENT_VERSION by the one-time migrations in load(). */
    public int configVersion = 0;

    /** 1.0: base phone price 25,000 -> 5,000. */
    private static final int CURRENT_VERSION = 2;
    private static final long OLD_PHONE_PRICE = 25_000L, PHONE_PRICE = 5_000L;

    private void fillHelp() {
        _help.clear();
        _help.put("enabled", "false = no upgrades: everyone has the full phone.");
        _help.put("tierCosts", "CobbleDollars cost of each tier: Basic, Plus, Pro, Ultra, Max, Elite, MasterBall Edition.");
        _help.put("packUsdLimits", "Max pack price (market USD) of the sets you can buy at tiers 0..5 (boxes included). Tier 6 has no limit.");
        _help.put("callsEnabled", "Daily call of the MasterBall Edition tier (Collector or Wholesaler).");
        _help.put("furnitureCallMinTier", "Phone tier from which the Furniture Seller can be called once per Minecraft day (1 = Plus). 7 or more = never.");
        _help.put("legacyShopOwnerTier/legacyPlayerTier", "Starting tier for players who already had a shop / had already played before 0.9.");
        _help.put("legacyKeepsApps", "Players from before 0.9 keep all the apps they already had.");
        _help.put("boxMinTier", "Minimum phone tier to buy x36 booster boxes in the phone Market. 0 = each box follows the tier limit of its pack.");
        _help.put("loansEnabled", "Wholesaler loans offered by phone message (from loanMinTier). false = no new offers.");
        _help.put("loanMinTier", "Minimum phone tier to get loan offers (2 = Pro).");
        _help.put("loanMaxAmount", "Hard cap of any loan offer.");
        _help.put("loanAmountByTier", "Largest offer per phone tier (Basic..MasterBall Edition) with average credit; credit scales it x0.5..x1.5.");
        _help.put("loanOfferGapDays", "Average in-game days between offers per phone tier; good credit x0.7, bad credit x1.6.");
        _help.put("loanTermDays", "In-game days to repay per phone tier.");
        _help.put("loanRateMin/loanRateMax", "Interest of the best / worst credit score (0.10 = 10%).");
        _help.put("loanOfferDays", "In-game days a loan offer stays valid.");
        _help.put("loanLateFeePerDay", "Fee added each day past the due date, as a fraction of what is still owed (the debt never exceeds 2x the loan).");
        _help.put("loanVeryLateDays", "Days late that make a loan 'very late': credit -15, reputation penalty, no offers until repaid.");
        _help.put("loanRepPenalty", "Reputation lost once by every shop of the borrower when the loan becomes very late.");
        _help.put("loanCollectAfterDays", "Days past the due date after which the Wholesaler collects the debt by force: first from the borrower's shop registers, then from their CobbleDollars (online only).");
        _help.put("loanCollectPerDay", "Daily installment of that collection, as a fraction of what was owed when it started (0.25 = 25%). 0 = never collect by force.");
        _help.put("diamondLoanEnabled/diamondLoanAmount/diamondLoanTermDays", "1.1: owners of a Diamond (or better) shop can request one big loan from the phone's Loans app (not capped by loanMaxAmount); same interest, late fees and collection rules as the other loans.");
        _help.put("configVersion", "Internal: format version of this file. Do not edit.");
    }

    private void sanitize() {
        long[] c = PhoneTier.DEFAULT_COSTS.clone();
        if (tierCosts != null) for (int i = 1; i < Math.min(c.length, tierCosts.length); i++) c[i] = Math.max(0, Math.min(1_000_000_000_000L, tierCosts[i]));
        c[0] = 0;
        tierCosts = c;
        double[] u = PhoneTier.DEFAULT_PACK_USD.clone();
        if (packUsdLimits != null) for (int i = 0; i < Math.min(u.length, packUsdLimits.length); i++) u[i] = Math.max(0, packUsdLimits[i]);
        packUsdLimits = u;
        legacyShopOwnerTier = Math.max(0, Math.min(PhoneTier.MAX_LEVEL, legacyShopOwnerTier));
        legacyPlayerTier = Math.max(0, Math.min(PhoneTier.MAX_LEVEL, legacyPlayerTier));
        boxMinTier = Math.max(0, Math.min(PhoneTier.MAX_LEVEL, boxMinTier));
        furnitureCallMinTier = Math.max(0, Math.min(PhoneTier.MAX_LEVEL + 1, furnitureCallMinTier));
        // loans: arrays always one entry per tier (missing entries take the defaults)
        loanMinTier = Math.max(0, Math.min(PhoneTier.MAX_LEVEL, loanMinTier));
        loanMaxAmount = Math.max(0, Math.min(1_000_000_000L, loanMaxAmount));
        long[] la = {0, 0, 2_000, 5_000, 12_000, 25_000, 50_000};
        if (loanAmountByTier != null) for (int i = 0; i < Math.min(la.length, loanAmountByTier.length); i++) la[i] = Math.max(0, loanAmountByTier[i]);
        loanAmountByTier = la;
        int[] gap = {0, 0, 14, 9, 6, 4, 3};
        if (loanOfferGapDays != null) for (int i = 0; i < Math.min(gap.length, loanOfferGapDays.length); i++) gap[i] = Math.max(1, loanOfferGapDays[i]);
        loanOfferGapDays = gap;
        int[] term = {0, 0, 5, 6, 7, 9, 12};
        if (loanTermDays != null) for (int i = 0; i < Math.min(term.length, loanTermDays.length); i++) term[i] = Math.max(1, loanTermDays[i]);
        loanTermDays = term;
        loanRateMin = Math.max(0, Math.min(5, loanRateMin));
        loanRateMax = Math.max(loanRateMin, Math.min(5, loanRateMax));
        loanOfferDays = Math.max(1, loanOfferDays);
        loanLateFeePerDay = Math.max(0, Math.min(1, loanLateFeePerDay));
        loanVeryLateDays = Math.max(1, loanVeryLateDays);
        loanRepPenalty = Math.max(0, Math.min(100, loanRepPenalty));
        loanCollectAfterDays = Math.max(1, Math.min(10_000, loanCollectAfterDays));
        loanCollectPerDay = Double.isNaN(loanCollectPerDay) ? 0.25 : Math.max(0, Math.min(1, loanCollectPerDay));
        diamondLoanAmount = Math.max(0, Math.min(1_000_000_000L, diamondLoanAmount));
        diamondLoanTermDays = Math.max(1, Math.min(365, diamondLoanTermDays));
    }

    /**
     * One-time migrations of files written by older versions (configVersion below CURRENT_VERSION). Runs after
     * EconomyConfig.load() (TcgShop.onInitialize loads the economy first), so it can fix values stored there.
     */
    private void migrate() {
        if (configVersion >= CURRENT_VERSION) return;
        if (configVersion < 2) migratePhonePrice();
        configVersion = CURRENT_VERSION;
    }

    /**
     * 1.0: the base phone costs 5,000. tcgshop-economy.json already holds the old default (25,000) in itemPrices:
     * it is replaced only if it still has that default (a price customized by the server admin is kept).
     */
    private static void migratePhonePrice() {
        try {
            dev.tcgshop.EconomyConfig eco = dev.tcgshop.EconomyConfig.INSTANCE;
            if (eco == null || eco.itemPrices == null) return;
            String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(dev.tcgshop.item.TcgItems.TCG_PHONE).toString();
            Long cur = eco.itemPrices.get(id);
            if (cur != null && cur != OLD_PHONE_PRICE) return;
            eco.itemPrices.put(id, PHONE_PRICE);
            dev.tcgshop.EconomyConfig.save();
            dev.tcgshop.shop.ShopServer.applyEconomy(eco);
            TcgShop.LOGGER.info("Phone 1.0: base phone price set to {} in tcgshop-economy.json", PHONE_PRICE);
        } catch (Exception e) {
            TcgShop.LOGGER.warn("Could not migrate the base phone price", e);
        }
    }

    public long cost(int level) {
        return level <= 0 || level > PhoneTier.MAX_LEVEL ? 0 : tierCosts[level];
    }

    public static void load() {
        PhoneConfig c = null;
        try {
            if (Files.exists(FILE)) try (Reader r = Files.newBufferedReader(FILE)) { c = GSON.fromJson(r, PhoneConfig.class); }
        } catch (Exception e) {
            TcgShop.LOGGER.error("Could not read {} (using default values)", FILE, e);
        }
        if (c == null) c = new PhoneConfig();
        c.sanitize();
        c.migrate();
        c.fillHelp();
        INSTANCE = c;
        try (Writer w = Files.newBufferedWriter(FILE)) {
            GSON.toJson(c, w);
        } catch (Exception e) {
            TcgShop.LOGGER.error("Could not save {}", FILE, e);
        }
    }
}

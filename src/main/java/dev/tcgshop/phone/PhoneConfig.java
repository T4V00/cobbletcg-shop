package dev.tcgshop.phone;

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

/** config/tcgshop-phone.json — phone upgrades (0.9). Read on startup and by PhoneConfig.load(). */
public class PhoneConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("tcgshop-phone.json");

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
    /** Starting tier for players who already had a shop / had already played before 0.9. */
    public int legacyShopOwnerTier = 2, legacyPlayerTier = 1;
    /** Players from before 0.9 keep ALL the apps they already used (Prices, CobbleGrade, League...). */
    public boolean legacyKeepsApps = true;

    private void fillHelp() {
        _help.clear();
        _help.put("enabled", "false = no upgrades: everyone has the full phone.");
        _help.put("tierCosts", "CobbleDollars cost of each tier: Basic, Plus, Pro, Ultra, Max, Elite, MasterBall Edition.");
        _help.put("packUsdLimits", "Max pack price (market USD) of the sets you can buy at tiers 0..5 (boxes included). Tier 6 has no limit.");
        _help.put("callsEnabled", "Daily call of the MasterBall Edition tier (Collector or Wholesaler).");
        _help.put("legacyShopOwnerTier/legacyPlayerTier", "Starting tier for players who already had a shop / had already played before 0.9.");
        _help.put("legacyKeepsApps", "Players from before 0.9 keep all the apps they already had.");
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
        c.fillHelp();
        INSTANCE = c;
        try (Writer w = Files.newBufferedWriter(FILE)) {
            GSON.toJson(c, w);
        } catch (Exception e) {
            TcgShop.LOGGER.error("Could not save {}", FILE, e);
        }
    }
}

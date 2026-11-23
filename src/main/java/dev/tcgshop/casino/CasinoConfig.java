package dev.tcgshop.casino;

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
 * config/tcgshop-casino.json (1.0 beta 3): betting limits and owner income of the casino machines.
 * The machine and ticket prices live with the other fixed prices in config/tcgshop-economy.json "itemPrices"
 * (tcgshop:slot_machine, tcgshop:roulette_table...). Applied with /tcgshop reload.
 */
public class CasinoConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("tcgshop-casino.json");

    public static CasinoConfig INSTANCE = new CasinoConfig();

    public Map<String, String> _help = new LinkedHashMap<>();
    public boolean enabled = true;
    /** Smallest bet (each roulette chip, each slot spin...). */
    public long minBet = 10;
    /** Responsible-gambling caps: the most a player can stake in one spin / round. */
    public long slotMaxBet = 25_000, vipSlotMaxBet = 250_000, rouletteMaxTotalBet = 100_000, blackjackMaxBet = 50_000, wheelMaxBet = 25_000;
    /** Most chips on the roulette layout in one spin. */
    public int rouletteMaxChips = 40;
    /** Share (percent) of every bet made by another player that goes to the machine's owner (collected with sneak + right click);
     *  capped at half of the game's house edge (Casino.OWNER_EDGE_CAP). */
    public double ownerSharePercent = 3.0;
    /** Shop customers near a machine sometimes play a round; the owner acts as the house for those rounds. */
    public boolean npcGamblers = true;
    /** Chance every 10 seconds that a nearby customer plays. */
    public double npcPlayChance = 0.12;
    public long npcMinBet = 100, npcMaxBet = 2_000;
    /** Wins of at least this much are announced in chat. 0 = never. */
    public long announceWinsFrom = 1_000_000;
    /** Face value of the Lucky Coin / Gold Rush / Master Jackpot tickets: prizes are multiples of it. */
    public long[] scratchFaceValues = {1_000, 5_000, 25_000};

    private void sanitize() {
        minBet = Math.max(1, minBet);
        slotMaxBet = Math.max(minBet, slotMaxBet);
        vipSlotMaxBet = Math.max(minBet, vipSlotMaxBet);
        rouletteMaxTotalBet = Math.max(minBet, rouletteMaxTotalBet);
        blackjackMaxBet = Math.max(minBet, blackjackMaxBet);
        wheelMaxBet = Math.max(minBet, wheelMaxBet);
        rouletteMaxChips = Math.max(1, Math.min(200, rouletteMaxChips));
        ownerSharePercent = Double.isNaN(ownerSharePercent) ? 0 : Math.max(0, Math.min(20, ownerSharePercent));
        npcPlayChance = Double.isNaN(npcPlayChance) ? 0 : Math.max(0, Math.min(1, npcPlayChance));
        npcMinBet = Math.max(1, npcMinBet);
        npcMaxBet = Math.max(npcMinBet, npcMaxBet);
        announceWinsFrom = Math.max(0, announceWinsFrom);
        long[] def = {1_000, 5_000, 25_000};
        if (scratchFaceValues == null || scratchFaceValues.length != def.length) scratchFaceValues = def;
        for (int i = 0; i < scratchFaceValues.length; i++) scratchFaceValues[i] = Math.max(1, Math.min(1_000_000_000L, scratchFaceValues[i]));
        _help = new LinkedHashMap<>();
        _help.put("apply", "Edit this file and run /tcgshop reload (operator) or restart. Machine and ticket prices: config/tcgshop-economy.json itemPrices.");
        _help.put("odds", "Real-world odds: slots 95.4% RTP, European roulette 97.3% (2.7% house edge), blackjack ~99.6% with basic strategy, "
                + "fortune wheel (Big Six) 76-89%, scratch tickets 59/65/70%. Check them with python tools/casino_rtp.py.");
        _help.put("minBet/*MaxBet", "Smallest and largest stake per spin / round (rouletteMaxTotalBet = all chips together; blackjack: per hand, a double or split adds the same again).");
        _help.put("ownerSharePercent", "Percent of every bet another player makes that goes to the machine owner's tray (sneak + right click with an empty hand to collect). "
                + "Capped at half of the game's house edge (slots ~2.3%, roulette ~1.35%, blackjack 0.2%), so playing at each other's machines never makes money.");
        _help.put("npcGamblers", "Shop customers near a machine sometimes play (npcPlayChance every 10 s, bets npcMinBet-npcMaxBet); the owner is the house and earns its theoretical win (stake x house edge) for those rounds.");
        _help.put("scratchFaceValues_cap", "A ticket never pays multiples of more than its store price (itemPrices), whatever scratchFaceValues says.");
        _help.put("announceWinsFrom", "Wins of at least this amount are announced to the whole server. 0 = never.");
        _help.put("scratchFaceValues", "Face value of the bronze, silver and gold scratch tickets. Prizes are multiples of it (x1 ... x10,000).");
    }

    public long scratchFace(ScratchMath.Tier t) {
        return scratchFaceValues[Math.min(scratchFaceValues.length - 1, t.ordinal())];
    }

    public static void load() {
        CasinoConfig c = null;
        try {
            if (Files.exists(FILE)) try (Reader r = Files.newBufferedReader(FILE)) { c = GSON.fromJson(r, CasinoConfig.class); }
        } catch (Exception e) {
            TcgShop.LOGGER.error("Could not read {} (using default values)", FILE, e);
        }
        if (c == null) c = new CasinoConfig();
        c.sanitize();
        INSTANCE = c;
        try (Writer w = Files.newBufferedWriter(FILE)) {
            GSON.toJson(c, w);
        } catch (Exception e) {
            TcgShop.LOGGER.error("Could not save {}", FILE, e);
        }
    }
}

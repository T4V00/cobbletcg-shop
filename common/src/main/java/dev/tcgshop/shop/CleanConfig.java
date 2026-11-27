package dev.tcgshop.shop;

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
 * config/tcgshop-clean.json (1.1, cleanliness update): how the shop's cleanliness is scored and how much it matters,
 * the droppings ("poop") customers and Pokémon leave behind, and the wall air freshener. Applied with /tcgshop reload.
 * The prices of the new items live with the other fixed prices (config/tcgshop-economy.json "itemPrices").
 */
public class CleanConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = Platform.INSTANCE.configDir().resolve("tcgshop-clean.json");
    /** Current file version (bump it and migrate old defaults in {@link #migrate} when a default changes). */
    public static final int VERSION = 1;

    public static CleanConfig INSTANCE = new CleanConfig();

    public Map<String, String> _help = new LinkedHashMap<>();
    public int configVersion = VERSION;

    // ---- score (0-100): 100 - smell * smellWeight - grime * grimeWeight - min(poopPenaltyCap, droppings * poopPenalty)
    public double smellWeight = 0.5;
    public double grimeWeight = 0.4;
    public int poopPenalty = 12;
    public int poopPenaltyCap = 60;

    // ---- how much it matters
    /** 1 = default impact of a dirty shop on sales; 0 = cleanliness never changes the buy chance. */
    public double salesImpact = 1.0;
    /** Scales the reputation factor "Cleanliness" (+5 spotless .. -20 filthy). */
    public double repImpact = 1.0;
    /** Fewer customers arrive at a dirty shop (down to x0.5 at 0 % with 1.0). */
    public double visitImpact = 1.0;
    /** Mood change when entering: +1 spotless, -1 / -2 dirty (0 turns it off). */
    public boolean moodImpact = true;

    // ---- droppings
    public boolean poopEnabled = true;
    /** Chance per shop every 30 s, before the multipliers (customers inside, Pokémon inside, dirt). */
    public double poopChance = 0.03;
    /** Most droppings at once in a shop (also capped by the shop side: 2 + side / 8). */
    public int poopMax = 6;
    /** Every 30 s, each dropping adds this much bad smell (halved, rounded up, for the whole pile). */
    public int poopSmell = 1;

    // ---- graffiti (rowdy customers tag the walls of a grimy shop; each counts like a dropping in the score)
    public boolean graffitiEnabled = true;
    /** No graffiti below this grime. */
    public int graffitiMinGrime = 35;
    /** Chance per shop every 30 s, before the multipliers (customers inside, grime). */
    public double graffitiChance = 0.03;
    /** Most graffiti at once in a shop. */
    public int graffitiMax = 3;

    // ---- grime (0-100): foot traffic
    /** Chance that a finished customer visit adds 1 grime. */
    public double grimePerVisit = 0.5;
    /** Grime the mop scrubs per swipe on the shop floor. */
    public int mopGrime = 4;

    // ---- wall air freshener
    /** Ticks between two puffs (600 = 30 s). */
    public int sprayInterval = 600;
    public int spraySmell = 3;
    public int sprayGrime = 1;
    /** Most fresheners that work in the same shop at the same time (more ones just wait, without using charges). */
    public int sprayMaxPerShop = 4;
    /** A puff also erases the nearest graffiti of its shop within this many blocks. */
    public int sprayGraffitiRange = 10;

    private void sanitize() {
        smellWeight = clampD(smellWeight, 0, 2);
        grimeWeight = clampD(grimeWeight, 0, 2);
        poopPenalty = Math.max(0, Math.min(50, poopPenalty));
        poopPenaltyCap = Math.max(0, Math.min(100, poopPenaltyCap));
        salesImpact = clampD(salesImpact, 0, 2);
        repImpact = clampD(repImpact, 0, 2);
        visitImpact = clampD(visitImpact, 0, 2);
        poopChance = clampD(poopChance, 0, 1);
        poopMax = Math.max(0, Math.min(32, poopMax));
        poopSmell = Math.max(0, Math.min(10, poopSmell));
        grimePerVisit = clampD(grimePerVisit, 0, 1);
        mopGrime = Math.max(0, Math.min(100, mopGrime));
        sprayInterval = Math.max(100, Math.min(24000, sprayInterval));
        spraySmell = Math.max(0, Math.min(100, spraySmell));
        sprayGrime = Math.max(0, Math.min(100, sprayGrime));
        sprayMaxPerShop = Math.max(1, Math.min(64, sprayMaxPerShop));
        graffitiMinGrime = Math.max(0, Math.min(100, graffitiMinGrime));
        graffitiChance = clampD(graffitiChance, 0, 1);
        graffitiMax = Math.max(0, Math.min(16, graffitiMax));
        sprayGraffitiRange = Math.max(0, Math.min(32, sprayGraffitiRange));
        _help = new LinkedHashMap<>();
        _help.put("apply", "Edit this file and run /tcgshop reload (operator) or restart. Item prices: config/tcgshop-economy.json itemPrices "
                + "(tcgshop:wall_freshener, tcgshop:wide_door, tcgshop:potted_palm...).");
        _help.put("score", "Cleanliness 0-100 = 100 - smell x smellWeight - grime x grimeWeight - min(poopPenaltyCap, visible dirt x poopPenalty), "
                + "visible dirt = droppings + graffiti + floor-mess event incidents (puddle, dirt, droppings, mess). Shown in the Manager Goggles and the shop status.");
        _help.put("salesImpact/repImpact/visitImpact/moodImpact", "How much a dirty shop hurts: buy chance (x0.25 at 0 %, +6 % when spotless), "
                + "the reputation factor (+5 .. -20), arrivals (down to x0.5) and the customer's mood on entering.");
        _help.put("poop*", "Customers and Pokémon inside the shop sometimes leave droppings (more when the shop is busy or already dirty). "
                + "They never pile up beyond poopMax (or 2 + side / 8), add bad smell over time and are cleaned with the mop.");
        _help.put("grime*", "Foot traffic: each finished visit adds 1 grime with grimePerVisit chance. The mop scrubs mopGrime per swipe on the floor; "
                + "wall fresheners remove sprayGrime per puff.");
        _help.put("graffiti*", "When grime is at least graffitiMinGrime, customers inside sometimes paint graffiti on the walls (at most graffitiMax). "
                + "Wall fresheners erase them; the shop owner can scrub one off with the deodorant spray (2 uses).");
        _help.put("spray*", "Wall air freshener: every sprayInterval ticks it removes spraySmell smell and sprayGrime grime and erases the nearest graffiti "
                + "within sprayGraffitiRange blocks, using one charge (240 per can; refill with the deodorant spray). Only works inside a shop; "
                + "at most sprayMaxPerShop per shop.");
    }

    private static double clampD(double v, double lo, double hi) {
        return Double.isNaN(v) ? lo : Math.max(lo, Math.min(hi, v));
    }

    /** Old files: replace a changed default only when the file still holds the old one (none yet: version 1 is the first). */
    private void migrate() {
        if (configVersion < VERSION) configVersion = VERSION;
    }

    public static void load() {
        CleanConfig c = null;
        try {
            if (Files.exists(FILE)) try (Reader r = Files.newBufferedReader(FILE)) { c = GSON.fromJson(r, CleanConfig.class); }
        } catch (Exception e) {
            TcgShop.LOGGER.error("Could not read {} (using default values)", FILE, e);
        }
        if (c == null) c = new CleanConfig();
        c.migrate();
        c.sanitize();
        INSTANCE = c;
        try (Writer w = Files.newBufferedWriter(FILE)) {
            GSON.toJson(c, w);
        } catch (Exception e) {
            TcgShop.LOGGER.error("Could not save {}", FILE, e);
        }
    }
}

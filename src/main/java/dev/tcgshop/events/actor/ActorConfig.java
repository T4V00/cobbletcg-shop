package dev.tcgshop.events.actor;

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
 * config/tcgshop-actors.json — numbers for the event Pokémon (actors). Same pattern as EventsConfig:
 * public fields, help texts (_help) and caps in sanitize(). Read when the mod starts and every time a
 * world/server starts (ActorModule). The event chances and master switch are in tcgshop-events.json.
 */
public class ActorConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("tcgshop-actors.json");

    public static ActorConfig INSTANCE = new ActorConfig();

    public Map<String, String> _help = new LinkedHashMap<>();

    /** Actor switch. Off: events with Pokémon do not happen (those already running end on their own). */
    public boolean enabled = true;
    /** Caps on event Pokémon at once: per shop and across the whole server. */
    public int maxPerShop = 3, maxGlobal = 12;
    /** How often (ticks, random between the two) an actor cries. */
    public int cryMinTicks = 200, cryMaxTicks = 400;
    /** Ambient particles (splashes, sparks, notes...). */
    public boolean particles = true;
    /** Resolving with a battle is allowed (owner only, level capped at their team's average). */
    public boolean allowBattles = true;
    /** Maximum level of an actor even if the owner's team is stronger. */
    public int maxLevel = 50;
    /** Ticks an actor takes to walk away before vanishing (with a cloud). */
    public int leaveTicks = 80;
    /** An actor that wanders farther than this (blocks) from the shop is considered gone. */
    public int leashBlocks = 16;
    /** Maximum value a thief Pokémon can "take" (virtual money; no item is moved). */
    public long theftMaxValue = 800;

    public static void load() {
        ActorConfig c = null;
        try {
            if (Files.exists(FILE)) try (Reader r = Files.newBufferedReader(FILE)) { c = GSON.fromJson(r, ActorConfig.class); }
        } catch (Exception e) {
            TcgShop.LOGGER.warn("[events] config/tcgshop-actors.json could not be read ({}); using default values", e.toString());
        }
        INSTANCE = c == null ? new ActorConfig() : c;
        INSTANCE.sanitize();
        save();
    }

    public static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            try (Writer w = Files.newBufferedWriter(FILE)) { GSON.toJson(INSTANCE, w); }
        } catch (Exception e) {
            TcgShop.LOGGER.warn("[events] could not save config/tcgshop-actors.json: {}", e.toString());
        }
    }

    void sanitize() {
        maxPerShop = Math.max(0, Math.min(6, maxPerShop));
        maxGlobal = Math.max(0, Math.min(48, maxGlobal));
        cryMinTicks = Math.max(60, Math.min(6000, cryMinTicks));
        cryMaxTicks = Math.max(cryMinTicks, Math.min(12000, cryMaxTicks));
        maxLevel = Math.max(1, Math.min(100, maxLevel));
        leaveTicks = Math.max(20, Math.min(400, leaveTicks));
        leashBlocks = Math.max(4, Math.min(64, leashBlocks));
        theftMaxValue = Math.max(0, Math.min(100_000, theftMaxValue));
        _help = new LinkedHashMap<>();
        _help.put("enabled", "Master switch for event Pokémon (actors). false = events with Pokémon do not happen.");
        _help.put("maxPerShop", "Event Pokémon at once per shop (0-6).");
        _help.put("maxGlobal", "Event Pokémon at once on the whole server (0-48).");
        _help.put("cryMinTicks", "How often they cry (ticks, between cryMinTicks and cryMaxTicks; 20 ticks = 1 s).");
        _help.put("particles", "Ambient particles around actors.");
        _help.put("allowBattles", "Allows solving some events by battling (owner only, sneak + right-click; level capped at their team average and maxLevel).");
        _help.put("maxLevel", "Max level of an event Pokémon (1-100).");
        _help.put("leaveTicks", "Ticks it walks away before vanishing (20-400).");
        _help.put("leashBlocks", "If it wanders more than this many blocks from the shop, it counts as gone (4-64).");
        _help.put("theftMaxValue", "Maximum value a thief Pokémon 'steals' if you don't get it back (register money only; no items are moved).");
        _help.put("notas", "Event Pokémon can never be caught, take no damage, drop no items, are not saved in the world (they vanish when the area unloads) and never respawn. Event chances are in tcgshop-events.json.");
    }
}

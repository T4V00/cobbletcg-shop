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
 * config/tcgshop-actors.json — números de los Pokémon de los eventos (actores). Mismo patrón que EventsConfig:
 * campos públicos, textos de ayuda (_help) y topes en sanitize(). Se lee al arrancar el mod y cada vez que arranca un
 * mundo/servidor (ActorModule). Las probabilidades y el interruptor general de los eventos están en tcgshop-events.json.
 */
public class ActorConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("tcgshop-actors.json");

    public static ActorConfig INSTANCE = new ActorConfig();

    public Map<String, String> _help = new LinkedHashMap<>();

    /** Interruptor de los actores. Apagado: los eventos con Pokémon no salen (los que ya estaban terminan solos). */
    public boolean enabled = true;
    /** Topes de Pokémon de eventos a la vez: por tienda y en todo el servidor. */
    public int maxPerShop = 3, maxGlobal = 12;
    /** Cada cuánto (ticks, al azar entre los dos) hace su grito un actor. */
    public int cryMinTicks = 200, cryMaxTicks = 400;
    /** Partículas de ambiente (salpicaduras, chispas, notas...). */
    public boolean particles = true;
    /** Se permite resolver con un combate (sólo el dueño, nivel limitado a la media de su equipo). */
    public boolean allowBattles = true;
    /** Nivel máximo de un actor aunque el equipo del dueño sea más fuerte. */
    public int maxLevel = 50;
    /** Ticks que tarda un actor en irse andando antes de desaparecer (con una nube). */
    public int leaveTicks = 80;
    /** Un actor que se aleja más de esto (bloques) de la tienda se da por ido. */
    public int leashBlocks = 16;
    /** Valor máximo que puede "llevarse" un Pokémon ladrón (dinero virtual; ningún objeto se mueve). */
    public long theftMaxValue = 800;

    public static void load() {
        ActorConfig c = null;
        try {
            if (Files.exists(FILE)) try (Reader r = Files.newBufferedReader(FILE)) { c = GSON.fromJson(r, ActorConfig.class); }
        } catch (Exception e) {
            TcgShop.LOGGER.warn("[eventos] config/tcgshop-actors.json no se pudo leer ({}); se usan los valores por defecto", e.toString());
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
            TcgShop.LOGGER.warn("[eventos] no se pudo guardar config/tcgshop-actors.json: {}", e.toString());
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
        _help.put("enabled", "Interruptor de los Pokémon de eventos (actores). false = los eventos con Pokémon no salen. / Master switch for event Pokémon.");
        _help.put("maxPerShop", "Pokémon de eventos a la vez por tienda (0-6). / Event Pokémon at once per shop.");
        _help.put("maxGlobal", "Pokémon de eventos a la vez en todo el servidor (0-48). / Event Pokémon at once on the whole server.");
        _help.put("cryMinTicks", "Cada cuánto gritan (ticks, entre cryMinTicks y cryMaxTicks; 20 ticks = 1 s). / How often they cry.");
        _help.put("particles", "Partículas de ambiente alrededor de los actores. / Ambient particles around actors.");
        _help.put("allowBattles", "Permite resolver algunos eventos combatiendo (sólo el dueño, agachado + clic derecho; nivel limitado a la media de su equipo y a maxLevel). / Allow owner battles.");
        _help.put("maxLevel", "Nivel máximo de un Pokémon de evento (1-100). / Max level of an event Pokémon.");
        _help.put("leaveTicks", "Ticks que tarda en irse andando antes de desaparecer (20-400). / Ticks it walks away before vanishing.");
        _help.put("leashBlocks", "Si se aleja más de estos bloques de la tienda, se da por ido (4-64). / Leash distance.");
        _help.put("theftMaxValue", "Valor máximo que 'roba' un Pokémon ladrón si no lo recuperas (sólo dinero de la caja; ningún objeto se mueve). / Max virtual theft value.");
        _help.put("notas", "Los Pokémon de eventos nunca se pueden capturar, no reciben daño, no sueltan objetos, no se guardan en el mundo (desaparecen al descargar la zona) y nunca reaparecen. Las probabilidades de los eventos están en tcgshop-events.json.");
    }
}

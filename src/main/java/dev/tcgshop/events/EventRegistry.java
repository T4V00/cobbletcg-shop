package dev.tcgshop.events;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.tcgshop.TcgShop;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

import java.io.Reader;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Definiciones de eventos: data/&lt;ns&gt;/tcgshop_events/&lt;path&gt;.json -> id "&lt;ns&gt;:&lt;path&gt;". Un datapack puede
 * sustituir un evento (mismo archivo) o desactivarlo con {"enabled": false}. Las definiciones inválidas (tipo de
 * efecto desconocido, negativo sin forma de arreglarlo...) se descartan con una línea en el log.
 */
public final class EventRegistry implements SimpleSynchronousResourceReloadListener {
    public static final String FOLDER = "tcgshop_events";
    private static volatile Map<String, EventDef> defs = Map.of();
    private static volatile List<String> lastErrors = List.of();

    public static Collection<EventDef> all() { return defs.values(); }

    public static EventDef get(String id) { return defs.get(id); }

    public static Map<String, EventDef> view() { return defs; }

    public static List<String> lastErrors() { return lastErrors; }

    @Override
    public Identifier getFabricId() { return Identifier.of(TcgShop.MOD_ID, "events"); }

    @Override
    public void reload(ResourceManager manager) {
        Map<String, EventDef> out = new LinkedHashMap<>();
        List<String> errors = new java.util.ArrayList<>();
        for (Map.Entry<Identifier, Resource> e : manager.findResources(FOLDER, p -> p.getPath().endsWith(".json")).entrySet()) {
            Identifier file = e.getKey();
            String path = file.getPath().substring(FOLDER.length() + 1, file.getPath().length() - 5);
            String id = file.getNamespace() + ":" + path;
            try (Reader r = e.getValue().getReader()) {
                JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
                if (o.has("enabled") && !o.get("enabled").getAsBoolean()) continue;
                EventDef def = validate(id, o);
                out.put(id, def);
            } catch (Exception ex) {
                String msg = id + ": " + ex.getMessage();
                errors.add(msg);
                TcgShop.LOGGER.warn("[eventos] definición descartada {}", msg);
            }
        }
        defs = Collections.unmodifiableMap(out);
        lastErrors = List.copyOf(errors);
        EventManager.onDefsReloaded();
        TcgShop.LOGGER.info("[eventos] {} eventos cargados ({} descartados)", out.size(), errors.size());
    }

    /** Lee y valida una definición (también la usa eventtest). */
    public static EventDef validate(String id, JsonObject o) {
        EventDef def = EventDef.parse(id, o);
        if (def.negative() && !def.hasFix()) throw new IllegalArgumentException("evento negativo sin ninguna forma de arreglarlo (fix/resolve)");
        if (def.effects().isEmpty() && def.variants().isEmpty()) throw new IllegalArgumentException("sin efectos");
        return def;
    }
}

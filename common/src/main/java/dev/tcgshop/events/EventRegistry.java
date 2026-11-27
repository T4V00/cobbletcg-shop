package dev.tcgshop.events;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.tcgshop.TcgShop;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import java.io.Reader;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Event definitions: data/&lt;ns&gt;/tcgshop_events/&lt;path&gt;.json -> id "&lt;ns&gt;:&lt;path&gt;". A datapack can
 * replace an event (same file) or disable it with {"enabled": false}. Invalid definitions (unknown effect
 * type, negative with no way to fix it...) are discarded with a line in the log.
 */
public final class EventRegistry implements ResourceManagerReloadListener {
    public static final String FOLDER = "tcgshop_events";
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(TcgShop.MOD_ID, "events");
    private static volatile Map<String, EventDef> defs = Map.of();
    private static volatile List<String> lastErrors = List.of();

    public static Collection<EventDef> all() { return defs.values(); }

    public static EventDef get(String id) { return defs.get(id); }

    public static Map<String, EventDef> view() { return defs; }

    public static List<String> lastErrors() { return lastErrors; }

    @Override
    public void onResourceManagerReload(ResourceManager manager) {
        Map<String, EventDef> out = new LinkedHashMap<>();
        List<String> errors = new java.util.ArrayList<>();
        for (Map.Entry<ResourceLocation, Resource> e : manager.listResources(FOLDER, p -> p.getPath().endsWith(".json")).entrySet()) {
            ResourceLocation file = e.getKey();
            String path = file.getPath().substring(FOLDER.length() + 1, file.getPath().length() - 5);
            String id = file.getNamespace() + ":" + path;
            try (Reader r = e.getValue().openAsReader()) {
                JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
                if (o.has("enabled") && !o.get("enabled").getAsBoolean()) continue;
                EventDef def = validate(id, o);
                out.put(id, def);
            } catch (Exception ex) {
                String msg = id + ": " + ex.getMessage();
                errors.add(msg);
                TcgShop.LOGGER.warn("[events] discarded definition {}", msg);
            }
        }
        defs = Collections.unmodifiableMap(out);
        lastErrors = List.copyOf(errors);
        EventManager.onDefsReloaded();
        TcgShop.LOGGER.info("[events] {} events loaded ({} discarded)", out.size(), errors.size());
    }

    /** Reads and validates a definition (also used by eventtest). */
    public static EventDef validate(String id, JsonObject o) {
        EventDef def = EventDef.parse(id, o);
        if (def.negative() && !def.hasFix()) throw new IllegalArgumentException("negative event with no way to fix it (fix/resolve)");
        if (def.effects().isEmpty() && def.variants().isEmpty()) throw new IllegalArgumentException("no effects");
        return def;
    }
}

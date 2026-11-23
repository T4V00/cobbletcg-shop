package dev.tcgshop.events;

import com.google.gson.JsonObject;
import dev.tcgshop.events.effect.MetricEffect;
import dev.tcgshop.events.effect.ModifierEffect;
import dev.tcgshop.events.effect.NotifyEffect;
import dev.tcgshop.events.effect.RewardEffect;
import dev.tcgshop.events.effect.StaffEffect;
import dev.tcgshop.events.effect.SupplierEffect;
import dev.tcgshop.events.effect.VisitorEffect;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Effect type registry: JSON "type" -> factory. The core registers modifier, visitor, supplier, reward,
 * staff, notify and metric; modules register their own in their init() (IncidentModule: "incident"; ActorModule: "actor").
 * ALWAYS register in onInitialize (before datapacks are loaded).
 */
public final class EffectTypes {
    /** Creates the effect from its JSON object. defId is used for error messages. Throws IllegalArgumentException if invalid. */
    @FunctionalInterface
    public interface Factory {
        EventEffect create(JsonObject json, String defId);
    }

    private static final Map<String, Factory> TYPES = new ConcurrentHashMap<>();

    private EffectTypes() {}

    public static void register(String type, Factory f) {
        if (TYPES.put(type, f) != null) dev.tcgshop.TcgShop.LOGGER.warn("[events] effect type '{}' registered twice", type);
    }

    public static Factory get(String type) { return TYPES.get(type); }

    public static Set<String> types() { return TYPES.keySet(); }

    /** Core types (called by EventManager.register). */
    static void registerCore() {
        register("modifier", ModifierEffect::new);
        register("visitor", VisitorEffect::new);
        register("supplier", SupplierEffect::new);
        register("reward", RewardEffect::new);
        register("staff", StaffEffect::new);
        register("notify", NotifyEffect::new);
        register("metric", MetricEffect::new);
    }
}

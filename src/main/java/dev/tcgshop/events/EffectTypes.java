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
 * Registro de tipos de efecto: "type" del JSON -> fábrica. El núcleo registra modifier, visitor, supplier, reward,
 * staff, notify y metric; los módulos registran los suyos en su init() (IncidentModule: "incident"; ActorModule: "actor").
 * Registrar SIEMPRE en onInitialize (antes de que se carguen los datapacks).
 */
public final class EffectTypes {
    /** Crea el efecto a partir de su objeto JSON. defId sirve para los mensajes de error. Lanza IllegalArgumentException si es inválido. */
    @FunctionalInterface
    public interface Factory {
        EventEffect create(JsonObject json, String defId);
    }

    private static final Map<String, Factory> TYPES = new ConcurrentHashMap<>();

    private EffectTypes() {}

    public static void register(String type, Factory f) {
        if (TYPES.put(type, f) != null) dev.tcgshop.TcgShop.LOGGER.warn("[eventos] tipo de efecto '{}' registrado dos veces", type);
    }

    public static Factory get(String type) { return TYPES.get(type); }

    public static Set<String> types() { return TYPES.keySet(); }

    /** Tipos del núcleo (los llama EventManager.register). */
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

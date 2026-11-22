package dev.tcgshop.compat;

import dev.tcgshop.TcgShop;
import net.minecraft.entity.Entity;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Pokémon de los EVENTOS (actores) por reflexión, sin dependencia de compilación con Cobblemon (probado contra la 1.8.4):
 *  · crear un salvaje "&lt;especie&gt; level=N uncatchable=yes shiny=no" (PokemonProperties.parse + createEntity);
 *  · marcarlo no combatible (tracked data PokemonEntity.UNBATTLEABLE), sin contar para el tope de spawns;
 *  · gritos, ¿está combatiendo?, especie, ¿debilitado?, combate PvE del dueño (BattleBuilder.pve);
 *  · ganchos de CobblemonEvents (Observable.subscribe(Priority, Consumer)):
 *      POKEMON_ENTITY_SAVE_TO_WORLD  cancelar -> nunca se escriben en el mundo (OBLIGATORIO: sin él no hay actores)
 *      THROWN_POKEBALL_HIT           cancelar -> no se pueden capturar (además de uncatchable=yes)
 *      LOOT_DROPPED                  cancelar -> no sueltan objetos al debilitarse
 * Si algo falla se avisa UNA vez en el log y se devuelve null/false (el evento se queda sin actor).
 */
public final class CobblemonActors {
    private static int hookAttempts;
    private static boolean hooksOk;
    private static final Map<String, Boolean> SPECIES_OK = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Map<String, Method>> METHODS = new HashMap<>();
    private static Object unbattleable;
    private static boolean warnedCreate, warnedBattle;

    private CobblemonActors() {}

    public static boolean available() { return CobblemonSpawn.available(); }

    /** ¿Están puestos los ganchos (sobre todo el que impide guardar los actores)? Sin ellos no se saca ningún actor. */
    public static boolean hooksOk() { return hooksOk; }

    // ------------------------------------------------------------------ ganchos de eventos de Cobblemon

    /**
     * Se suscribe a los eventos de Cobblemon. noSave: entidades que no deben guardarse; noCatch: no capturables;
     * noLoot: sin botín. Devuelve true si el gancho de guardado (el imprescindible) quedó puesto.
     */
    public static synchronized boolean installHooks(Predicate<Entity> noSave, Predicate<Entity> noCatch, Predicate<Entity> noLoot) {
        // sólo se reintenta si el gancho imprescindible falló (nunca se suscribe dos veces)
        if (hooksOk || hookAttempts >= 3 || !available()) return hooksOk;
        hookAttempts++;
        try {
            Class<?> events = Class.forName("com.cobblemon.mod.common.api.events.CobblemonEvents");
            Class<?> prio = Class.forName("com.cobblemon.mod.common.api.Priority");
            Object highest = prio.getField("HIGHEST").get(null);
            Method cancel = Class.forName("com.cobblemon.mod.common.api.events.Cancelable").getMethod("cancel");
            // 1) nunca guardar actores ni Pokémon temporales
            Method getSaved = Class.forName("com.cobblemon.mod.common.api.events.entity.PokemonEntitySaveToWorldEvent").getMethod("getPokemonEntity");
            subscribe(events, "POKEMON_ENTITY_SAVE_TO_WORLD", prio, highest, ev -> {
                try {
                    Object pe = getSaved.invoke(ev);
                    if (pe instanceof Entity e && noSave.test(e)) cancel.invoke(ev);
                } catch (Throwable ignored) {}
            });
            hooksOk = true;
            // 2) no capturables (además de uncatchable=yes)
            try {
                Method getHit = Class.forName("com.cobblemon.mod.common.api.events.pokeball.ThrownPokeballHitEvent").getMethod("getPokemon");
                subscribe(events, "THROWN_POKEBALL_HIT", prio, highest, ev -> {
                    try {
                        Object pe = getHit.invoke(ev);
                        if (pe instanceof Entity e && noCatch.test(e)) cancel.invoke(ev);
                    } catch (Throwable ignored) {}
                });
            } catch (Throwable e) {
                TcgShop.LOGGER.warn("[eventos] no se pudo enganchar THROWN_POKEBALL_HIT ({}); los actores siguen siendo uncatchable=yes", e.toString());
            }
            // 3) sin botín
            try {
                Method getEnt = Class.forName("com.cobblemon.mod.common.api.events.drops.LootDroppedEvent").getMethod("getEntity");
                subscribe(events, "LOOT_DROPPED", prio, highest, ev -> {
                    try {
                        Object le = getEnt.invoke(ev);
                        if (le instanceof Entity e && noLoot.test(e)) cancel.invoke(ev);
                    } catch (Throwable ignored) {}
                });
            } catch (Throwable e) {
                TcgShop.LOGGER.warn("[eventos] no se pudo enganchar LOOT_DROPPED: {}", e.toString());
            }
        } catch (Throwable e) {
            TcgShop.LOGGER.warn("[eventos] no se pudo enganchar POKEMON_ENTITY_SAVE_TO_WORLD ({}): los eventos con Pokémon quedan DESACTIVADOS", e.toString());
            hooksOk = false;
        }
        return hooksOk;
    }

    private static void subscribe(Class<?> events, String field, Class<?> prio, Object priority, Consumer<Object> handler) throws Exception {
        Object obs = events.getField(field).get(null);
        Method sub = obs.getClass().getMethod("subscribe", prio, Consumer.class);
        sub.invoke(obs, priority, handler);
    }

    // ------------------------------------------------------------------ especies

    /** ¿Existe la especie y está implementada en este Cobblemon? (cacheado) */
    public static boolean speciesOk(String species) {
        if (!available() || species == null || species.isBlank()) return false;
        String key = normalize(species);
        return SPECIES_OK.computeIfAbsent(key, k -> {
            try {
                Class<?> ps = Class.forName("com.cobblemon.mod.common.api.pokemon.PokemonSpecies");
                Object sp = ps.getMethod("getByName", String.class).invoke(null, k);
                if (sp == null) return false;
                Object impl = sp.getClass().getMethod("getImplemented").invoke(sp);
                return Boolean.TRUE.equals(impl);
            } catch (Throwable e) {
                return false;
            }
        });
    }

    public static String normalize(String species) {
        String s = species.trim().toLowerCase(Locale.ROOT);
        int c = s.indexOf(':');
        return c >= 0 ? s.substring(c + 1) : s;
    }

    // ------------------------------------------------------------------ crear y marcar

    /** Crea (SIN añadir al mundo) un Pokémon salvaje no capturable y no shiny. null si no se puede. */
    @Nullable
    public static Entity create(ServerWorld world, String species, int level) {
        if (!available() || !speciesOk(species)) return null;
        try {
            Class<?> propsClass = Class.forName("com.cobblemon.mod.common.api.pokemon.PokemonProperties");
            Object companion = propsClass.getField("Companion").get(null);
            String props = normalize(species) + " level=" + Math.max(1, Math.min(100, level)) + " uncatchable=yes shiny=no";
            Object p = companion.getClass().getMethod("parse", String.class).invoke(companion, props);
            return (Entity) propsClass.getMethod("createEntity", World.class).invoke(p, world);
        } catch (Throwable e) {
            if (!warnedCreate) {
                warnedCreate = true;
                TcgShop.LOGGER.warn("[eventos] no se pudo crear un Pokémon de evento ({}): {}", species, e.toString());
            }
            return null;
        }
    }

    /** Marca el Pokémon como no combatible (o lo permite). */
    @SuppressWarnings("unchecked")
    public static void setUnbattleable(Entity e, boolean value) {
        try {
            if (unbattleable == null)
                unbattleable = Class.forName("com.cobblemon.mod.common.entity.pokemon.PokemonEntity").getMethod("getUNBATTLEABLE").invoke(null);
            e.getDataTracker().set((TrackedData<Boolean>) unbattleable, value);
        } catch (Throwable ignored) {}
    }

    /** Que no cuente para el tope de apariciones de Cobblemon (opcional). */
    public static void noSpawnCap(Entity e) {
        call(e, "setCountsTowardsSpawnCap", false);
    }

    public static void cry(Entity e) {
        call(e, "cry");
    }

    public static boolean isBattling(Entity e) {
        Object r = call(e, "isBattling");
        return Boolean.TRUE.equals(r);
    }

    /** Objeto Pokemon (datos) de la entidad, o null. */
    @Nullable
    public static Object pokemonOf(Entity e) {
        return call(e, "getPokemon");
    }

    public static boolean isFainted(@Nullable Object pokemon) {
        return pokemon != null && Boolean.TRUE.equals(call(pokemon, "isFainted"));
    }

    /** Nombre de la especie en minúsculas ("psyduck"), o "" si no es un Pokémon. */
    public static String speciesOf(Entity e) {
        Object pk = pokemonOf(e);
        if (pk == null) return "";
        Object sp = call(pk, "getSpecies");
        if (sp == null) return "";
        Object n = call(sp, "getName");
        return n == null ? "" : n.toString().toLowerCase(Locale.ROOT);
    }

    /**
     * Combate PvE del jugador contra este Pokémon (BattleBuilder.pve). Quita el "no combatible" sólo durante la llamada.
     * true si empezó.
     */
    public static boolean startBattle(ServerPlayerEntity player, Entity pokemon) {
        if (!available()) return false;
        setUnbattleable(pokemon, false);
        try {
            Class<?> builder = Class.forName("com.cobblemon.mod.common.battles.BattleBuilder");
            Object inst = builder.getField("INSTANCE").get(null);
            Method pve = null;
            for (Method m : builder.getMethods()) if (m.getName().equals("pve") && m.getParameterCount() == 2) pve = m;
            Object result = pve == null ? null : pve.invoke(inst, player, pokemon);
            return result != null && result.getClass().getSimpleName().equals("SuccessfulBattleStart");
        } catch (Throwable e) {
            if (!warnedBattle) {
                warnedBattle = true;
                TcgShop.LOGGER.warn("[eventos] no se pudo empezar el combate contra un Pokémon de evento: {}", e.toString());
            }
            return false;
        } finally {
            setUnbattleable(pokemon, true);
        }
    }

    // ------------------------------------------------------------------ reflexión con caché

    @Nullable
    private static synchronized Object call(Object target, String name, Object... args) {
        try {
            Map<String, Method> byName = METHODS.computeIfAbsent(target.getClass(), k -> new HashMap<>());
            String key = name + "/" + args.length;
            Method m = byName.get(key);
            if (m == null && !byName.containsKey(key)) {
                for (Method x : target.getClass().getMethods()) if (x.getName().equals(name) && x.getParameterCount() == args.length) { m = x; break; }
                byName.put(key, m);
            }
            return m == null ? null : m.invoke(target, args);
        } catch (Throwable e) {
            return null;
        }
    }
}

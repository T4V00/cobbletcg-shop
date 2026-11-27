package dev.tcgshop.compat;

import dev.tcgshop.TcgShop;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/**
 * EVENT Pokémon (actors) via reflection, with no compile-time dependency on Cobblemon (tested against 1.8.4):
 *  · create a wild "&lt;species&gt; level=N uncatchable=yes shiny=no" (PokemonProperties.parse + createEntity);
 *  · mark it unbattleable (tracked data PokemonEntity.UNBATTLEABLE), not counting toward the spawn cap;
 *  · cries, is it battling?, species, fainted?, owner's PvE battle (BattleBuilder.pve);
 *  · CobblemonEvents hooks (Observable.subscribe(Priority, Consumer)):
 *      POKEMON_ENTITY_SAVE_TO_WORLD  cancel -> never written to the world (REQUIRED: without it there are no actors)
 *      THROWN_POKEBALL_HIT           cancel -> cannot be caught (in addition to uncatchable=yes)
 *      LOOT_DROPPED                  cancel -> drop no items when fainting
 * If something fails, it warns ONCE in the log and returns null/false (the event is left without an actor).
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

    /** Are the hooks installed (especially the one preventing actors from being saved)? Without them no actor is spawned. */
    public static boolean hooksOk() { return hooksOk; }

    // ------------------------------------------------------------------ Cobblemon event hooks

    /**
     * Subscribes to Cobblemon events. noSave: entities that must not be saved; noCatch: uncatchable;
     * noLoot: no loot. Returns true if the save hook (the essential one) was installed.
     */
    public static synchronized boolean installHooks(Predicate<Entity> noSave, Predicate<Entity> noCatch, Predicate<Entity> noLoot) {
        // only retried if the essential hook failed (never subscribes twice)
        if (hooksOk || hookAttempts >= 3 || !available()) return hooksOk;
        hookAttempts++;
        try {
            Class<?> events = Class.forName("com.cobblemon.mod.common.api.events.CobblemonEvents");
            Class<?> prio = Class.forName("com.cobblemon.mod.common.api.Priority");
            Object highest = prio.getField("HIGHEST").get(null);
            Method cancel = Class.forName("com.cobblemon.mod.common.api.events.Cancelable").getMethod("cancel");
            // 1) never save actors or temporary Pokémon
            Method getSaved = Class.forName("com.cobblemon.mod.common.api.events.entity.PokemonEntitySaveToWorldEvent").getMethod("getPokemonEntity");
            subscribe(events, "POKEMON_ENTITY_SAVE_TO_WORLD", prio, highest, ev -> {
                try {
                    Object pe = getSaved.invoke(ev);
                    if (pe instanceof Entity e && noSave.test(e)) cancel.invoke(ev);
                } catch (Throwable ignored) {}
            });
            hooksOk = true;
            // 2) uncatchable (in addition to uncatchable=yes)
            try {
                Method getHit = Class.forName("com.cobblemon.mod.common.api.events.pokeball.ThrownPokeballHitEvent").getMethod("getPokemon");
                subscribe(events, "THROWN_POKEBALL_HIT", prio, highest, ev -> {
                    try {
                        Object pe = getHit.invoke(ev);
                        if (pe instanceof Entity e && noCatch.test(e)) cancel.invoke(ev);
                    } catch (Throwable ignored) {}
                });
            } catch (Throwable e) {
                TcgShop.LOGGER.warn("[events] could not hook THROWN_POKEBALL_HIT ({}); actors are still uncatchable=yes", e.toString());
            }
            // 3) no loot
            try {
                Method getEnt = Class.forName("com.cobblemon.mod.common.api.events.drops.LootDroppedEvent").getMethod("getEntity");
                subscribe(events, "LOOT_DROPPED", prio, highest, ev -> {
                    try {
                        Object le = getEnt.invoke(ev);
                        if (le instanceof Entity e && noLoot.test(e)) cancel.invoke(ev);
                    } catch (Throwable ignored) {}
                });
            } catch (Throwable e) {
                TcgShop.LOGGER.warn("[events] could not hook LOOT_DROPPED: {}", e.toString());
            }
        } catch (Throwable e) {
            TcgShop.LOGGER.warn("[events] could not hook POKEMON_ENTITY_SAVE_TO_WORLD ({}): Pokémon events are DISABLED", e.toString());
            hooksOk = false;
        }
        return hooksOk;
    }

    private static void subscribe(Class<?> events, String field, Class<?> prio, Object priority, Consumer<Object> handler) throws Exception {
        Object obs = events.getField(field).get(null);
        Method sub = obs.getClass().getMethod("subscribe", prio, Consumer.class);
        sub.invoke(obs, priority, handler);
    }

    // ------------------------------------------------------------------ species

    /** Does the species exist and is it implemented in this Cobblemon? (cached) */
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

    // ------------------------------------------------------------------ create and mark

    /** Creates (WITHOUT adding to the world) an uncatchable, non-shiny wild Pokémon. null if not possible. */
    @Nullable
    public static Entity create(ServerLevel world, String species, int level) {
        if (!available() || !speciesOk(species)) return null;
        try {
            Class<?> propsClass = Class.forName("com.cobblemon.mod.common.api.pokemon.PokemonProperties");
            Object companion = propsClass.getField("Companion").get(null);
            String props = normalize(species) + " level=" + Math.max(1, Math.min(100, level)) + " uncatchable=yes shiny=no";
            Object p = companion.getClass().getMethod("parse", String.class).invoke(companion, props);
            return (Entity) propsClass.getMethod("createEntity", Level.class).invoke(p, world);
        } catch (Throwable e) {
            if (!warnedCreate) {
                warnedCreate = true;
                TcgShop.LOGGER.warn("[events] could not create an event Pokémon ({}): {}", species, e.toString());
            }
            return null;
        }
    }

    /** Marks the Pokémon as unbattleable (or allows battling). */
    @SuppressWarnings("unchecked")
    public static void setUnbattleable(Entity e, boolean value) {
        try {
            if (unbattleable == null)
                unbattleable = Class.forName("com.cobblemon.mod.common.entity.pokemon.PokemonEntity").getMethod("getUNBATTLEABLE").invoke(null);
            e.getEntityData().set((EntityDataAccessor<Boolean>) unbattleable, value);
        } catch (Throwable ignored) {}
    }

    /** Makes it not count toward Cobblemon's spawn cap (optional). */
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

    /** The entity's Pokemon object (data), or null. */
    @Nullable
    public static Object pokemonOf(Entity e) {
        return call(e, "getPokemon");
    }

    public static boolean isFainted(@Nullable Object pokemon) {
        return pokemon != null && Boolean.TRUE.equals(call(pokemon, "isFainted"));
    }

    /** Lowercase species name ("psyduck"), or "" if it is not a Pokémon. */
    public static String speciesOf(Entity e) {
        Object pk = pokemonOf(e);
        if (pk == null) return "";
        Object sp = call(pk, "getSpecies");
        if (sp == null) return "";
        Object n = call(sp, "getName");
        return n == null ? "" : n.toString().toLowerCase(Locale.ROOT);
    }

    /**
     * PvE battle of the player against this Pokémon (BattleBuilder.pve). Removes "unbattleable" only during the call.
     * true if it started.
     */
    public static boolean startBattle(ServerPlayer player, Entity pokemon) {
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
                TcgShop.LOGGER.warn("[events] could not start the battle against an event Pokémon: {}", e.toString());
            }
            return false;
        } finally {
            setUnbattleable(pokemon, true);
        }
    }

    // ------------------------------------------------------------------ cached reflection

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

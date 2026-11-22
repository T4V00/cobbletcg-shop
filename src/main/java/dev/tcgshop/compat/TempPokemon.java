package dev.tcgshop.compat;

import net.minecraft.entity.Entity;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Pokémon TEMPORALES que saca el mod fuera de los eventos (el Pokémon de un Scalper/ladrón en CobblemonBattles y los
 * de los duelos entre NPC de NpcPokemonDuel). Antes, si el servidor se cerraba a mitad de combate, quedaban en el mundo
 * como salvajes no capturables para siempre. Ahora:
 *  · llevan la etiqueta {@link #TAG} (puesta ANTES de spawnEntity) y su uuid va a {@link #LIVE};
 *  · el módulo de actores cancela su guardado en el mundo (POKEMON_ENTITY_SAVE_TO_WORLD) y no sueltan botín;
 *  · si aun así aparece uno cargado que no está en LIVE (versión antigua, otro mod...), se borra al cargar.
 * No afecta al Meowth del tutorial (otra etiqueta) ni a los Pokémon normales.
 */
public final class TempPokemon {
    public static final String TAG = "tcgshop_tmp_pokemon";
    private static final Set<UUID> LIVE = Collections.synchronizedSet(new HashSet<>());

    private TempPokemon() {}

    /** Llamar ANTES de world.spawnEntity. */
    public static void mark(Entity e) {
        if (e == null) return;
        e.addCommandTag(TAG);
        LIVE.add(e.getUuid());
    }

    public static void release(Entity e) {
        if (e != null) LIVE.remove(e.getUuid());
    }

    public static boolean isLive(UUID id) { return LIVE.contains(id); }

    public static boolean isTemp(Entity e) { return e.getCommandTags().contains(TAG); }

    /** Al parar el servidor (los combates y duelos en memoria se pierden). */
    public static void clear() { LIVE.clear(); }
}

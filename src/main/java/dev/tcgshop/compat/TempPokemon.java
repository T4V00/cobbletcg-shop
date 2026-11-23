package dev.tcgshop.compat;

import net.minecraft.entity.Entity;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * TEMPORARY Pokémon the mod spawns outside of events (a Scalper/thief's Pokémon in CobblemonBattles and those
 * from NPC duels in NpcPokemonDuel). Previously, if the server shut down mid-battle, they stayed in the world
 * as uncatchable wild Pokémon forever. Now:
 *  · they carry the tag {@link #TAG} (set BEFORE spawnEntity) and their uuid goes into {@link #LIVE};
 *  · the actors module cancels their saving to the world (POKEMON_ENTITY_SAVE_TO_WORLD) and they drop no loot;
 *  · if one still shows up loaded that is not in LIVE (old version, another mod...), it is deleted on load.
 * Does not affect the tutorial Meowth (different tag) or normal Pokémon.
 */
public final class TempPokemon {
    public static final String TAG = "tcgshop_tmp_pokemon";
    private static final Set<UUID> LIVE = Collections.synchronizedSet(new HashSet<>());

    private TempPokemon() {}

    /** Call BEFORE world.spawnEntity. */
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

    /** On server stop (in-memory battles and duels are lost). */
    public static void clear() { LIVE.clear(); }
}

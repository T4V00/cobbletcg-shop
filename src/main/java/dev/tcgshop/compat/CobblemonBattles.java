package dev.tcgshop.compat;

import dev.tcgshop.TcgShop;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Pokémon battles against the mod's NPCs (Scalpers and thieves) if Cobblemon is installed. All via reflection
 * (no compile-time dependency): the NPC "sends out" an uncatchable wild Pokémon of a level similar to your
 * party and starts a normal Cobblemon battle against it. If you knock it out, you win the battle and the NPC falls;
 * if you flee or lose, the NPC gets away with it. While the battle lasts the NPC stands still.
 */
public final class CobblemonBattles {
    /** NPC that can battle. */
    public interface Fighter {
        /** Species it can send out. */
        String[] battlePool();

        void onBattleEnd(ServerPlayerEntity player, boolean playerWon);
    }

    private record Fight(MobEntity npc, Entity pokemon, UUID player, long start) {}

    private static final Map<UUID, Fight> FIGHTS = new HashMap<>();
    private static boolean warned;

    private CobblemonBattles() {}

    public static boolean available() {
        return FabricLoader.getInstance().isModLoaded("cobblemon");
    }

    public static boolean isFighting(Entity npc) {
        return FIGHTS.containsKey(npc.getUuid());
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(CobblemonBattles::tick);
    }

    /** The player challenges the NPC. Returns true if the battle has started. */
    public static boolean challenge(ServerPlayerEntity player, MobEntity npc) {
        if (!available() || !(npc instanceof Fighter f) || !npc.isAlive()) return false;
        if (isFighting(npc) || NpcPokemonDuel.busy(npc)) {
            player.sendMessage(Text.translatable("tcgshop.battle.busy").formatted(Formatting.GRAY), true);
            return false;
        }
        for (Fight x : FIGHTS.values()) {
            if (x.player.equals(player.getUuid())) {
                player.sendMessage(Text.translatable("tcgshop.battle.already").formatted(Formatting.GRAY), true);
                return false;
            }
        }
        try {
            ServerWorld world = player.getServerWorld();
            int level = Math.max(5, Math.min(100, partyLevel(player) + world.random.nextInt(5) - 1));
            String[] pool = f.battlePool();
            String species = pool[world.random.nextInt(pool.length)];
            Class<?> propsClass = Class.forName("com.cobblemon.mod.common.api.pokemon.PokemonProperties");
            Object companion = propsClass.getField("Companion").get(null);
            Object props = companion.getClass().getMethod("parse", String.class).invoke(companion, species + " level=" + level + " uncatchable=yes");
            Entity pokemon = (Entity) propsClass.getMethod("createEntity", World.class).invoke(props, world);
            Vec3d dir = player.getPos().subtract(npc.getPos());
            Vec3d spot = dir.lengthSquared() < 1e-4 ? npc.getPos().add(1.5, 0, 0) : npc.getPos().add(dir.normalize().multiply(1.8));
            pokemon.refreshPositionAndAngles(spot.x, npc.getY(), spot.z, npc.getYaw(), 0);
            TempPokemon.mark(pokemon); // 0.9: never saved to the world (no orphans if closed midway)
            world.spawnEntity(pokemon);

            Class<?> builder = Class.forName("com.cobblemon.mod.common.battles.BattleBuilder");
            Object inst = builder.getField("INSTANCE").get(null);
            Method pve = null;
            for (Method m : builder.getMethods()) if (m.getName().equals("pve") && m.getParameterCount() == 2) pve = m;
            Object result = pve == null ? null : pve.invoke(inst, player, pokemon);
            if (result == null || !result.getClass().getSimpleName().equals("SuccessfulBattleStart")) {
                pokemon.discard();
                TempPokemon.release(pokemon);
                player.sendMessage(Text.translatable("tcgshop.battle.cant").formatted(Formatting.RED), true);
                return false;
            }
            npc.getNavigation().stop();
            npc.setAiDisabled(true);
            npc.getLookControl().lookAt(player);
            FIGHTS.put(npc.getUuid(), new Fight(npc, pokemon, player.getUuid(), world.getTime()));
            return true;
        } catch (Throwable e) {
            if (!warned) {
                warned = true;
                TcgShop.LOGGER.warn("Could not start a Cobblemon battle against an NPC: {}", e.toString());
            }
            player.sendMessage(Text.translatable("tcgshop.battle.cant").formatted(Formatting.RED), true);
            return false;
        }
    }

    /** Level of the strongest Pokémon in the party (20 if it cannot be read). */
    private static int partyLevel(ServerPlayerEntity player) {
        try {
            Class<?> ext = Class.forName("com.cobblemon.mod.common.util.PlayerExtensionsKt");
            Object party = ext.getMethod("party", ServerPlayerEntity.class).invoke(null, player);
            int best = 0;
            for (Object pk : (Iterable<?>) party) {
                if (pk == null) continue;
                best = Math.max(best, (int) pk.getClass().getMethod("getLevel").invoke(pk));
            }
            return best > 0 ? best : 20;
        } catch (Throwable e) {
            return 20;
        }
    }

    private static boolean call(Object o, String method) {
        try {
            return (boolean) o.getClass().getMethod(method).invoke(o);
        } catch (Throwable e) {
            return false;
        }
    }

    private static boolean fainted(Entity pokemon) {
        try {
            Object pk = pokemon.getClass().getMethod("getPokemon").invoke(pokemon);
            return (boolean) pk.getClass().getMethod("isFainted").invoke(pk);
        } catch (Throwable e) {
            return false;
        }
    }

    private static void tick(MinecraftServer server) {
        if (FIGHTS.isEmpty() || server.getTicks() % 10 != 0) return;
        Iterator<Map.Entry<UUID, Fight>> it = FIGHTS.entrySet().iterator();
        while (it.hasNext()) {
            Fight f = it.next().getValue();
            long age = f.npc.getWorld().getTime() - f.start;
            boolean gone = f.pokemon.isRemoved() || !f.pokemon.isAlive();
            boolean battling = !gone && call(f.pokemon, "isBattling");
            if (battling && age < 20 * 60 * 15) continue;
            if (!gone && !battling && age < 40) continue; // the battle is still being set up
            boolean won = fainted(f.pokemon);
            if (!gone) f.pokemon.discard();
            TempPokemon.release(f.pokemon);
            it.remove();
            f.npc.setAiDisabled(false);
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(f.player);
            if (p != null && f.npc.isAlive() && f.npc instanceof Fighter fighter) fighter.onBattleEnd(p, won);
        }
    }
}

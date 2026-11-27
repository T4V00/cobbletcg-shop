package dev.tcgshop.compat;

import dev.masterball.api.platform.Platform;
import dev.masterball.api.platform.event.ServerEvents;
import dev.tcgshop.TcgShop;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
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

        void onBattleEnd(ServerPlayer player, boolean playerWon);
    }

    private record Fight(Mob npc, Entity pokemon, UUID player, long start) {}

    private static final Map<UUID, Fight> FIGHTS = new HashMap<>();
    private static boolean warned;

    private CobblemonBattles() {}

    public static boolean available() {
        return Platform.INSTANCE.isModLoaded("cobblemon");
    }

    public static boolean isFighting(Entity npc) {
        return FIGHTS.containsKey(npc.getUUID());
    }

    public static void register() {
        ServerEvents.TICK_END.register(CobblemonBattles::tick);
    }

    /** The player challenges the NPC. Returns true if the battle has started. */
    public static boolean challenge(ServerPlayer player, Mob npc) {
        if (!available() || !(npc instanceof Fighter f) || !npc.isAlive()) return false;
        if (isFighting(npc) || NpcPokemonDuel.busy(npc)) {
            player.displayClientMessage(Component.translatable("tcgshop.battle.busy").withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        for (Fight x : FIGHTS.values()) {
            if (x.player.equals(player.getUUID())) {
                player.displayClientMessage(Component.translatable("tcgshop.battle.already").withStyle(ChatFormatting.GRAY), true);
                return false;
            }
        }
        try {
            ServerLevel world = player.serverLevel();
            int level = Math.max(5, Math.min(100, partyLevel(player) + world.random.nextInt(5) - 1));
            String[] pool = f.battlePool();
            String species = pool[world.random.nextInt(pool.length)];
            Class<?> propsClass = Class.forName("com.cobblemon.mod.common.api.pokemon.PokemonProperties");
            Object companion = propsClass.getField("Companion").get(null);
            Object props = companion.getClass().getMethod("parse", String.class).invoke(companion, species + " level=" + level + " uncatchable=yes");
            Entity pokemon = (Entity) propsClass.getMethod("createEntity", Level.class).invoke(props, world);
            Vec3 dir = player.position().subtract(npc.position());
            Vec3 spot = dir.lengthSqr() < 1e-4 ? npc.position().add(1.5, 0, 0) : npc.position().add(dir.normalize().scale(1.8));
            pokemon.moveTo(spot.x, npc.getY(), spot.z, npc.getYRot(), 0);
            TempPokemon.mark(pokemon); // 0.9: never saved to the world (no orphans if closed midway)
            world.addFreshEntity(pokemon);

            Class<?> builder = Class.forName("com.cobblemon.mod.common.battles.BattleBuilder");
            Object inst = builder.getField("INSTANCE").get(null);
            Method pve = null;
            for (Method m : builder.getMethods()) if (m.getName().equals("pve") && m.getParameterCount() == 2) pve = m;
            Object result = pve == null ? null : pve.invoke(inst, player, pokemon);
            if (result == null || !result.getClass().getSimpleName().equals("SuccessfulBattleStart")) {
                pokemon.discard();
                TempPokemon.release(pokemon);
                player.displayClientMessage(Component.translatable("tcgshop.battle.cant").withStyle(ChatFormatting.RED), true);
                return false;
            }
            npc.getNavigation().stop();
            npc.setNoAi(true);
            npc.getLookControl().setLookAt(player);
            FIGHTS.put(npc.getUUID(), new Fight(npc, pokemon, player.getUUID(), world.getGameTime()));
            return true;
        } catch (Throwable e) {
            if (!warned) {
                warned = true;
                TcgShop.LOGGER.warn("Could not start a Cobblemon battle against an NPC: {}", e.toString());
            }
            player.displayClientMessage(Component.translatable("tcgshop.battle.cant").withStyle(ChatFormatting.RED), true);
            return false;
        }
    }

    /** Level of the strongest Pokémon in the party (20 if it cannot be read). */
    private static int partyLevel(ServerPlayer player) {
        try {
            Class<?> ext = Class.forName("com.cobblemon.mod.common.util.PlayerExtensionsKt");
            Object party = ext.getMethod("party", ServerPlayer.class).invoke(null, player);
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
        if (FIGHTS.isEmpty() || server.getTickCount() % 10 != 0) return;
        Iterator<Map.Entry<UUID, Fight>> it = FIGHTS.entrySet().iterator();
        // end callbacks run after the loop: one may start a new fight (the Mayor's fallback chain re-challenges), and
        // putting into FIGHTS while iterating it would throw a ConcurrentModificationException
        List<Runnable> ended = new ArrayList<>();
        while (it.hasNext()) {
            Fight f = it.next().getValue();
            long age = f.npc.level().getGameTime() - f.start;
            boolean gone = f.pokemon.isRemoved() || !f.pokemon.isAlive();
            boolean battling = !gone && call(f.pokemon, "isBattling");
            if (battling && age < 20 * 60 * 15) continue;
            if (!gone && !battling && age < 40) continue; // the battle is still being set up
            boolean won = fainted(f.pokemon);
            if (!gone) f.pokemon.discard();
            TempPokemon.release(f.pokemon);
            it.remove();
            f.npc.setNoAi(false);
            ServerPlayer p = server.getPlayerList().getPlayer(f.player);
            if (p != null && f.npc.isAlive() && f.npc instanceof Fighter fighter) ended.add(() -> fighter.onBattleEnd(p, won));
        }
        for (Runnable r : ended) {
            try { r.run(); } catch (Throwable t) { TcgShop.LOGGER.warn("[battle] end callback failed: {}", t.toString()); }
        }
    }
}

package dev.tcgshop.compat;

import dev.masterball.api.platform.event.ServerEvents;
import dev.tcgshop.TcgShop;
import dev.tcgshop.entity.NpcLines;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Pokémon duel between two NPCs (Scalper vs Scalper, guard vs thief...). Cobblemon only sets up real battles
 * when a player (or its own NPCs) is involved, so here each NPC sends out a real Pokémon (uncatchable,
 * stationary) and the battle is simulated turn by turn with its moves, particles, sounds and speech bubbles above the NPC.
 * Whoever runs out of HP loses: their Pokémon faints and the callback decides the outcome.
 */
public final class NpcPokemonDuel {
    public interface End {
        void onEnd(Mob winner, Mob loser);
    }

    private static final class Duel {
        final Mob[] npc = new Mob[2];
        final Entity[] mon = new Entity[2];
        final List<List<String>> moves = new ArrayList<>();
        final int[] hp = {100, 100};
        final int[] power = new int[2];
        final double bias; // extra probability that side 0 wins
        final End end;
        long nextTurn;
        int turn, finishedAt = -1;

        Duel(Mob a, Mob b, double bias, End end) {
            npc[0] = a;
            npc[1] = b;
            this.bias = bias;
            this.end = end;
        }
    }

    private static final List<Duel> DUELS = new ArrayList<>();
    private static boolean warned;

    private NpcPokemonDuel() {}

    public static void register() {
        ServerEvents.TICK_END.register(NpcPokemonDuel::tick);
    }

    public static boolean busy(Entity e) {
        if (e != null && HELD.contains(e)) return true;
        for (Duel d : DUELS) if (d.npc[0] == e || d.npc[1] == e) return true;
        return false;
    }

    /**
     * 1.0: NPCs "held" by another scripted scene (two customers playing at a TCG table, a customer challenging a
     * player...). A held NPC counts as busy: its own AI (CustomerEntity, ScalperEntity, guards) pauses, it cannot be
     * pulled into a Pokémon duel, but its navigation keeps working so the scene can walk it around. Weak references:
     * a removed entity never leaks.
     */
    private static final java.util.Set<Entity> HELD = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());

    /** Marks the NPC as busy for a scripted scene (see {@link #HELD}). */
    public static void hold(Entity e) {
        if (e != null) HELD.add(e);
    }

    /** Ends the hold started with {@link #hold}. */
    public static void unhold(Entity e) {
        if (e != null) HELD.remove(e);
    }

    /** Is it held by a scripted scene (not by a Pokémon duel)? */
    public static boolean held(Entity e) {
        return e != null && HELD.contains(e);
    }

    /**
     * Starts a duel. bias > 0 favors the first one (0.35 = wins ~85%). Returns false if Cobblemon is missing or it fails
     * (then the caller does its fallback, e.g. a fistfight).
     */
    public static boolean start(Mob a, Mob b, String[] poolA, String[] poolB, int level, double bias, End end) {
        if (!CobblemonBattles.available() || busy(a) || busy(b) || !(a.level() instanceof ServerLevel world)) return false;
        Duel d = new Duel(a, b, bias, end);
        try {
            String[][] pools = {poolA, poolB};
            for (int i = 0; i < 2; i++) {
                Mob me = d.npc[i], other = d.npc[1 - i];
                String species = pools[i][world.random.nextInt(pools[i].length)];
                Entity mon = createPokemon(world, species, level + world.random.nextInt(5) - 2);
                Vec3 dir = other.position().subtract(me.position());
                Vec3 spot = me.position().add(dir.lengthSqr() < 1e-3 ? new Vec3(1, 0, 0) : dir.normalize().scale(Math.min(2.2, dir.length() * 0.35)));
                mon.moveTo(spot.x, me.getY(), spot.z, 0, 0);
                if (mon instanceof Mob mm) mm.setNoAi(true);
                mon.setInvulnerable(true);
                TempPokemon.mark(mon); // 0.9: never saved to the world (no orphans if closed midway)
                world.addFreshEntity(mon);
                d.mon[i] = mon;
                d.moves.add(movesOf(mon));
                d.power[i] = 40 + world.random.nextInt(25);
                world.sendParticles(ParticleTypes.CLOUD, spot.x, me.getY() + 0.5, spot.z, 12, 0.3, 0.3, 0.3, 0.02);
                me.getNavigation().stop();
                me.setNoAi(true);
                NpcLines.speak(me, Component.translatable("tcgshop.duel_npc.send_out", mon.getDisplayName()));
            }
            for (int i = 0; i < 2; i++) lookAt(d.mon[i], d.mon[1 - i]);
            world.playSound(null, a.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.NEUTRAL, 1f, 0.7f);
            d.nextTurn = world.getGameTime() + 40;
            DUELS.add(d);
            for (var p : world.getPlayers(pl -> pl.distanceToSqr(a) < 40 * 40))
                p.displayClientMessage(Component.translatable("tcgshop.duel_npc.start", a.getDisplayName(), b.getDisplayName()).withStyle(ChatFormatting.GOLD), false);
            return true;
        } catch (Throwable e) {
            for (Entity m : d.mon) if (m != null) { m.discard(); TempPokemon.release(m); }
            a.setNoAi(false);
            b.setNoAi(false);
            if (!warned) {
                warned = true;
                TcgShop.LOGGER.warn("Could not start a Pokémon duel between NPCs: {}", e.toString());
            }
            return false;
        }
    }

    static Entity createPokemon(ServerLevel world, String species, int level) throws Exception {
        Class<?> propsClass = Class.forName("com.cobblemon.mod.common.api.pokemon.PokemonProperties");
        Object companion = propsClass.getField("Companion").get(null);
        Object props = companion.getClass().getMethod("parse", String.class).invoke(companion,
                species + " level=" + Math.max(5, Math.min(100, level)) + " uncatchable=yes");
        return (Entity) propsClass.getMethod("createEntity", Level.class).invoke(props, world);
    }

    /** Names of the Pokémon's moves (or generic ones if they cannot be read). */
    private static List<String> movesOf(Entity mon) {
        List<String> out = new ArrayList<>();
        try {
            Object pk = mon.getClass().getMethod("getPokemon").invoke(mon);
            Object set = pk.getClass().getMethod("getMoveSet").invoke(pk);
            Object list = set.getClass().getMethod("getMoves").invoke(set);
            for (Object mv : (Iterable<?>) list) {
                Object name = mv.getClass().getMethod("getDisplayName").invoke(mv);
                if (name instanceof Component t) out.add(t.getString());
            }
        } catch (Throwable ignored) {}
        return out;
    }

    private static void lookAt(Entity a, Entity b) {
        Vec3 d = b.position().subtract(a.position());
        float yaw = (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90);
        a.setYRot(yaw);
        a.setYHeadRot(yaw);
        a.setYBodyRot(yaw);
    }

    private static void tick(MinecraftServer server) {
        if (DUELS.isEmpty()) return;
        Iterator<Duel> it = DUELS.iterator();
        while (it.hasNext()) {
            Duel d = it.next();
            ServerLevel world = (ServerLevel) d.npc[0].level();
            long now = world.getGameTime();
            boolean broken = !d.npc[0].isAlive() || !d.npc[1].isAlive() || d.mon[0].isRemoved() || d.mon[1].isRemoved();
            if (broken) {
                cleanup(d);
                it.remove();
                continue;
            }
            if (d.finishedAt >= 0) {
                if (now - d.finishedAt > 50) {
                    cleanup(d);
                    it.remove();
                }
                continue;
            }
            if (now < d.nextTurn) continue;
            int atk = d.turn % 2, def = 1 - atk;
            d.turn++;
            d.nextTurn = now + 34;
            Mob trainer = d.npc[atk];
            Entity mon = d.mon[atk], target = d.mon[def];
            List<String> mv = d.moves.get(atk);
            Component move = mv.isEmpty() ? Component.translatable("tcgshop.duel_npc.move." + world.random.nextInt(6)) : Component.literal(mv.get(world.random.nextInt(mv.size())));
            NpcLines.speak(trainer, Component.translatable("tcgshop.duel_npc.use", mon.getDisplayName(), move));
            // damage (biased toward side 0 if there is a bias)
            double luck = atk == 0 ? 1 + d.bias : 1 - d.bias * 0.6;
            int dmg = (int) Math.round((d.power[atk] * 0.45 + world.random.nextInt(18)) * luck);
            boolean crit = world.random.nextFloat() < 0.12f, miss = world.random.nextFloat() < 0.08f;
            if (miss) dmg = 0;
            else if (crit) dmg = Math.round(dmg * 1.5f);
            d.hp[def] = Math.max(0, d.hp[def] - dmg);
            Vec3 tp = target.position();
            Vec3 mp = mon.position();
            for (int k = 1; k <= 6; k++) {
                Vec3 p = mp.lerp(tp, k / 7.0);
                world.sendParticles(ParticleTypes.CRIT, p.x, p.y + 0.6, p.z, 2, 0.05, 0.05, 0.05, 0.01);
            }
            if (!miss) {
                world.sendParticles(crit ? ParticleTypes.ENCHANTED_HIT : ParticleTypes.DAMAGE_INDICATOR, tp.x, tp.y + target.getBbHeight() * 0.6, tp.z,
                        crit ? 14 : 6, 0.25, 0.25, 0.25, 0.1);
                world.playSound(null, target.blockPosition(), crit ? SoundEvents.PLAYER_ATTACK_CRIT : SoundEvents.PLAYER_ATTACK_STRONG,
                        SoundSource.NEUTRAL, 0.9f, 1f);
                target.setDeltaMovement(tp.subtract(mp).normalize().scale(0.25).add(0, 0.15, 0));
                target.hurtMarked = true;
            } else world.playSound(null, target.blockPosition(), SoundEvents.PLAYER_ATTACK_NODAMAGE, SoundSource.NEUTRAL, 0.8f, 1f);
            for (var p : world.getPlayers(pl -> pl.distanceToSqr(target) < 24 * 24))
                p.displayClientMessage(Component.translatable(miss ? "tcgshop.duel_npc.miss" : crit ? "tcgshop.duel_npc.crit" : "tcgshop.duel_npc.hp",
                        target.getDisplayName(), d.hp[def]).withStyle(miss ? ChatFormatting.GRAY : ChatFormatting.YELLOW), true);
            if (d.hp[def] <= 0) {
                d.finishedAt = (int) now;
                world.sendParticles(ParticleTypes.POOF, tp.x, tp.y + 0.5, tp.z, 20, 0.3, 0.3, 0.3, 0.02);
                world.playSound(null, target.blockPosition(), SoundEvents.GENERIC_DEATH, SoundSource.NEUTRAL, 0.8f, 1.3f);
                target.discard(); // it faints
                TempPokemon.release(target);
                NpcLines.speak(d.npc[def], Component.translatable("tcgshop.duel_npc.fainted", target.getDisplayName()));
                for (var p : world.getPlayers(pl -> pl.distanceToSqr(trainer) < 40 * 40))
                    p.displayClientMessage(Component.translatable("tcgshop.duel_npc.won", trainer.getDisplayName(), d.npc[def].getDisplayName()).withStyle(ChatFormatting.GOLD), false);
                Mob winner = trainer, loser = d.npc[def];
                winner.setNoAi(false);
                loser.setNoAi(false);
                try { d.end.onEnd(winner, loser); } catch (Throwable e) { TcgShop.LOGGER.warn("NPC duel: {}", e.toString()); }
            }
        }
    }

    private static void cleanup(Duel d) {
        for (int i = 0; i < 2; i++) {
            if (d.mon[i] != null && !d.mon[i].isRemoved()) {
                if (d.mon[i].level() instanceof ServerLevel w)
                    w.sendParticles(ParticleTypes.CLOUD, d.mon[i].getX(), d.mon[i].getY() + 0.5, d.mon[i].getZ(), 10, 0.3, 0.3, 0.3, 0.02);
                d.mon[i].discard(); // returns to its Poké Ball
            }
            TempPokemon.release(d.mon[i]);
            d.npc[i].setNoAi(false);
        }
    }
}

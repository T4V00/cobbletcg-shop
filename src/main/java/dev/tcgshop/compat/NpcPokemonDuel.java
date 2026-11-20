package dev.tcgshop.compat;

import dev.tcgshop.TcgShop;
import dev.tcgshop.entity.NpcLines;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Duelo Pokémon entre dos NPC (Scalper contra Scalper, guardia contra ladrón...). Cobblemon sólo monta combates
 * de verdad cuando hay un jugador (o sus propios NPC), así que aquí cada NPC saca un Pokémon real (no capturable,
 * quieto) y el combate se simula por turnos con sus movimientos, partículas, sonidos y bocadillos sobre el NPC.
 * El que se queda sin PS pierde: su Pokémon se debilita y el resultado lo decide el callback.
 */
public final class NpcPokemonDuel {
    public interface End {
        void onEnd(MobEntity winner, MobEntity loser);
    }

    private static final class Duel {
        final MobEntity[] npc = new MobEntity[2];
        final Entity[] mon = new Entity[2];
        final List<List<String>> moves = new ArrayList<>();
        final int[] hp = {100, 100};
        final int[] power = new int[2];
        final double bias; // probabilidad extra de que gane el lado 0
        final End end;
        long nextTurn;
        int turn, finishedAt = -1;

        Duel(MobEntity a, MobEntity b, double bias, End end) {
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
        ServerTickEvents.END_SERVER_TICK.register(NpcPokemonDuel::tick);
    }

    public static boolean busy(Entity e) {
        for (Duel d : DUELS) if (d.npc[0] == e || d.npc[1] == e) return true;
        return false;
    }

    /**
     * Empieza un duelo. bias > 0 favorece al primero (0.35 = gana ~85%). Devuelve false si no hay Cobblemon o falla
     * (entonces el que llama hace su alternativa, p. ej. pelea a puñetazos).
     */
    public static boolean start(MobEntity a, MobEntity b, String[] poolA, String[] poolB, int level, double bias, End end) {
        if (!CobblemonBattles.available() || busy(a) || busy(b) || !(a.getWorld() instanceof ServerWorld world)) return false;
        Duel d = new Duel(a, b, bias, end);
        try {
            String[][] pools = {poolA, poolB};
            for (int i = 0; i < 2; i++) {
                MobEntity me = d.npc[i], other = d.npc[1 - i];
                String species = pools[i][world.random.nextInt(pools[i].length)];
                Entity mon = createPokemon(world, species, level + world.random.nextInt(5) - 2);
                Vec3d dir = other.getPos().subtract(me.getPos());
                Vec3d spot = me.getPos().add(dir.lengthSquared() < 1e-3 ? new Vec3d(1, 0, 0) : dir.normalize().multiply(Math.min(2.2, dir.length() * 0.35)));
                mon.refreshPositionAndAngles(spot.x, me.getY(), spot.z, 0, 0);
                if (mon instanceof MobEntity mm) mm.setAiDisabled(true);
                world.spawnEntity(mon);
                d.mon[i] = mon;
                d.moves.add(movesOf(mon));
                d.power[i] = 40 + world.random.nextInt(25);
                world.spawnParticles(ParticleTypes.CLOUD, spot.x, me.getY() + 0.5, spot.z, 12, 0.3, 0.3, 0.3, 0.02);
                me.getNavigation().stop();
                me.setAiDisabled(true);
                NpcLines.speak(me, Text.translatable("tcgshop.duel_npc.send_out", mon.getDisplayName()));
            }
            for (int i = 0; i < 2; i++) lookAt(d.mon[i], d.mon[1 - i]);
            world.playSound(null, a.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.NEUTRAL, 1f, 0.7f);
            d.nextTurn = world.getTime() + 40;
            DUELS.add(d);
            for (var p : world.getPlayers(pl -> pl.squaredDistanceTo(a) < 40 * 40))
                p.sendMessage(Text.translatable("tcgshop.duel_npc.start", a.getDisplayName(), b.getDisplayName()).formatted(Formatting.GOLD), false);
            return true;
        } catch (Throwable e) {
            for (Entity m : d.mon) if (m != null) m.discard();
            a.setAiDisabled(false);
            b.setAiDisabled(false);
            if (!warned) {
                warned = true;
                TcgShop.LOGGER.warn("No se pudo empezar un duelo Pokémon entre NPC: {}", e.toString());
            }
            return false;
        }
    }

    static Entity createPokemon(ServerWorld world, String species, int level) throws Exception {
        Class<?> propsClass = Class.forName("com.cobblemon.mod.common.api.pokemon.PokemonProperties");
        Object companion = propsClass.getField("Companion").get(null);
        Object props = companion.getClass().getMethod("parse", String.class).invoke(companion,
                species + " level=" + Math.max(5, Math.min(100, level)) + " uncatchable=yes");
        return (Entity) propsClass.getMethod("createEntity", World.class).invoke(props, world);
    }

    /** Nombres de los movimientos del Pokémon (o genéricos si no se pueden leer). */
    private static List<String> movesOf(Entity mon) {
        List<String> out = new ArrayList<>();
        try {
            Object pk = mon.getClass().getMethod("getPokemon").invoke(mon);
            Object set = pk.getClass().getMethod("getMoveSet").invoke(pk);
            Object list = set.getClass().getMethod("getMoves").invoke(set);
            for (Object mv : (Iterable<?>) list) {
                Object name = mv.getClass().getMethod("getDisplayName").invoke(mv);
                if (name instanceof Text t) out.add(t.getString());
            }
        } catch (Throwable ignored) {}
        return out;
    }

    private static void lookAt(Entity a, Entity b) {
        Vec3d d = b.getPos().subtract(a.getPos());
        float yaw = (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90);
        a.setYaw(yaw);
        a.setHeadYaw(yaw);
        a.setBodyYaw(yaw);
    }

    private static void tick(MinecraftServer server) {
        if (DUELS.isEmpty()) return;
        Iterator<Duel> it = DUELS.iterator();
        while (it.hasNext()) {
            Duel d = it.next();
            ServerWorld world = (ServerWorld) d.npc[0].getWorld();
            long now = world.getTime();
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
            MobEntity trainer = d.npc[atk];
            Entity mon = d.mon[atk], target = d.mon[def];
            List<String> mv = d.moves.get(atk);
            Text move = mv.isEmpty() ? Text.translatable("tcgshop.duel_npc.move." + world.random.nextInt(6)) : Text.literal(mv.get(world.random.nextInt(mv.size())));
            NpcLines.speak(trainer, Text.translatable("tcgshop.duel_npc.use", mon.getDisplayName(), move));
            // daño (con sesgo hacia el lado 0 si lo hay)
            double luck = atk == 0 ? 1 + d.bias : 1 - d.bias * 0.6;
            int dmg = (int) Math.round((d.power[atk] * 0.45 + world.random.nextInt(18)) * luck);
            boolean crit = world.random.nextFloat() < 0.12f, miss = world.random.nextFloat() < 0.08f;
            if (miss) dmg = 0;
            else if (crit) dmg = Math.round(dmg * 1.5f);
            d.hp[def] = Math.max(0, d.hp[def] - dmg);
            Vec3d tp = target.getPos();
            Vec3d mp = mon.getPos();
            for (int k = 1; k <= 6; k++) {
                Vec3d p = mp.lerp(tp, k / 7.0);
                world.spawnParticles(ParticleTypes.CRIT, p.x, p.y + 0.6, p.z, 2, 0.05, 0.05, 0.05, 0.01);
            }
            if (!miss) {
                world.spawnParticles(crit ? ParticleTypes.ENCHANTED_HIT : ParticleTypes.DAMAGE_INDICATOR, tp.x, tp.y + target.getHeight() * 0.6, tp.z,
                        crit ? 14 : 6, 0.25, 0.25, 0.25, 0.1);
                world.playSound(null, target.getBlockPos(), crit ? SoundEvents.ENTITY_PLAYER_ATTACK_CRIT : SoundEvents.ENTITY_PLAYER_ATTACK_STRONG,
                        SoundCategory.NEUTRAL, 0.9f, 1f);
                target.setVelocity(tp.subtract(mp).normalize().multiply(0.25).add(0, 0.15, 0));
                target.velocityModified = true;
            } else world.playSound(null, target.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_NODAMAGE, SoundCategory.NEUTRAL, 0.8f, 1f);
            for (var p : world.getPlayers(pl -> pl.squaredDistanceTo(target) < 24 * 24))
                p.sendMessage(Text.translatable(miss ? "tcgshop.duel_npc.miss" : crit ? "tcgshop.duel_npc.crit" : "tcgshop.duel_npc.hp",
                        target.getDisplayName(), d.hp[def]).formatted(miss ? Formatting.GRAY : Formatting.YELLOW), true);
            if (d.hp[def] <= 0) {
                d.finishedAt = (int) now;
                world.spawnParticles(ParticleTypes.POOF, tp.x, tp.y + 0.5, tp.z, 20, 0.3, 0.3, 0.3, 0.02);
                world.playSound(null, target.getBlockPos(), SoundEvents.ENTITY_GENERIC_DEATH, SoundCategory.NEUTRAL, 0.8f, 1.3f);
                target.discard(); // se debilita
                NpcLines.speak(d.npc[def], Text.translatable("tcgshop.duel_npc.fainted", target.getDisplayName()));
                for (var p : world.getPlayers(pl -> pl.squaredDistanceTo(trainer) < 40 * 40))
                    p.sendMessage(Text.translatable("tcgshop.duel_npc.won", trainer.getDisplayName(), d.npc[def].getDisplayName()).formatted(Formatting.GOLD), false);
                MobEntity winner = trainer, loser = d.npc[def];
                winner.setAiDisabled(false);
                loser.setAiDisabled(false);
                try { d.end.onEnd(winner, loser); } catch (Throwable e) { TcgShop.LOGGER.warn("duelo NPC: {}", e.toString()); }
            }
        }
    }

    private static void cleanup(Duel d) {
        for (int i = 0; i < 2; i++) {
            if (d.mon[i] != null && !d.mon[i].isRemoved()) {
                if (d.mon[i].getWorld() instanceof ServerWorld w)
                    w.spawnParticles(ParticleTypes.CLOUD, d.mon[i].getX(), d.mon[i].getY() + 0.5, d.mon[i].getZ(), 10, 0.3, 0.3, 0.3, 0.02);
                d.mon[i].discard(); // vuelve a su Poké Ball
            }
            d.npc[i].setAiDisabled(false);
        }
    }
}

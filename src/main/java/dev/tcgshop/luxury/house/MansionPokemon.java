package dev.tcgshop.luxury.house;

import dev.tcgshop.TcgShop;
import dev.tcgshop.compat.CobblemonActors;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

import java.lang.reflect.Method;
import java.util.function.Consumer;

/**
 * Pokémon residentes de la finca (sólo con Cobblemon, por reflexión a través de {@link CobblemonActors}):
 *  · se crean como salvajes "uncatchable=yes", invulnerables, no combatibles (tracked data UNBATTLEABLE, que Cobblemon
 *    guarda en el NBT como "Unbattleable") y persistentes (MobEntity.setPersistent: PokemonEntity.checkDespawn no
 *    despawnea a los persistentes);
 *  · SÍ se guardan con el mundo (a diferencia de los actores de eventos) y llevan la etiqueta {@link #TAG};
 *  · gancho propio a THROWN_POKEBALL_HIT: cualquier Poké Ball lanzada a un residente se cancela;
 *  · al cargarse (ENTITY_LOAD) se les vuelve a aplicar todo por si alguna versión no guardara algún dato;
 *  · cada 5 s, si un residente cargado se aleja de su casa más de su radio, vuelve a ella (no salen de la finca).
 * Snorlax se queda quieto (sin IA) durmiendo en el césped; el resto pasea un poco dentro de su radio.
 */
public final class MansionPokemon {
    public static final String TAG = "tcgshop_mansion_mon";
    public static final String STILL_TAG = "tcgshop_mansion_still";
    private static boolean hookTried;

    private MansionPokemon() {}

    public static boolean isResident(Entity e) {
        return e.getCommandTags().contains(TAG);
    }

    /** Gancho de Cobblemon (una vez por JVM): no se pueden capturar. Sin Cobblemon no hace nada. */
    static synchronized void installHooks() {
        if (hookTried || !CobblemonActors.available()) return;
        hookTried = true;
        try {
            Class<?> events = Class.forName("com.cobblemon.mod.common.api.events.CobblemonEvents");
            Class<?> prio = Class.forName("com.cobblemon.mod.common.api.Priority");
            Object highest = prio.getField("HIGHEST").get(null);
            Method cancel = Class.forName("com.cobblemon.mod.common.api.events.Cancelable").getMethod("cancel");
            Method getHit = Class.forName("com.cobblemon.mod.common.api.events.pokeball.ThrownPokeballHitEvent").getMethod("getPokemon");
            Object obs = events.getField("THROWN_POKEBALL_HIT").get(null);
            Method sub = obs.getClass().getMethod("subscribe", prio, Consumer.class);
            Consumer<Object> handler = ev -> {
                try {
                    if (getHit.invoke(ev) instanceof Entity e && isResident(e)) cancel.invoke(ev);
                } catch (Throwable ignored) {}
            };
            sub.invoke(obs, highest, handler);
        } catch (Throwable e) {
            TcgShop.LOGGER.warn("[mansión] no se pudo enganchar THROWN_POKEBALL_HIT ({}); los residentes siguen siendo uncatchable=yes", e.toString());
        }
    }

    /** Vuelve a aplicar las reglas a un residente que se carga del disco. */
    static void onLoad(Entity e) {
        if (!isResident(e)) return;
        apply(e, e.getCommandTags().contains(STILL_TAG));
    }

    private static void apply(Entity e, boolean still) {
        e.setInvulnerable(true);
        if (e instanceof MobEntity mob) {
            mob.setPersistent();
            if (still) mob.setAiDisabled(true);
        }
        CobblemonActors.setUnbattleable(e, true);
        CobblemonActors.noSpawnCap(e);
    }

    /** Saca los residentes de la finca. Devuelve cuántos aparecieron (0 sin Cobblemon o si está desactivado). */
    static int spawn(ServerWorld world, MansionState.Record rec, MansionDesign design, MansionPlan plan) {
        if (!CobblemonActors.available()) return 0;
        installHooks();
        int n = 0;
        for (MansionDesign.ResidentSpec r : design.residents) {
            try {
                Entity e = CobblemonActors.create(world, r.species(), r.level());
                if (e == null) continue;
                Vec3d v = plan.center(r.x(), r.y(), r.z());
                float yaw = plan.facing.getOpposite().asRotation() + (world.getRandom().nextFloat() - 0.5f) * 90f;
                e.refreshPositionAndAngles(v.x, v.y, v.z, yaw, 0);
                e.addCommandTag(TAG);
                if (r.still()) e.addCommandTag(STILL_TAG);
                apply(e, r.still());
                if (!world.spawnEntity(e)) continue;
                rec.residents.add(new MansionState.Resident(e.getUuid(), r.species(), v.x, v.y, v.z, r.radius()));
                n++;
            } catch (Throwable t) {
                TcgShop.LOGGER.warn("[mansión] no se pudo sacar al residente {}: {}", r.species(), t.toString());
            }
        }
        return n;
    }

    private static int clock;

    /** Cada 100 ticks: los residentes cargados que se han alejado demasiado vuelven a su sitio. */
    static void tick(MinecraftServer server) {
        if (++clock % 100 != 0) return;
        for (ServerWorld w : server.getWorlds()) {
            MansionState st = MansionState.get(w);
            for (MansionState.Record rec : st.all()) {
                for (MansionState.Resident r : rec.residents) {
                    Entity e = w.getEntity(r.uuid());
                    if (e == null || e.isRemoved() || e.hasVehicle()) continue;
                    double max = Math.max(2, r.radius());
                    if (e.squaredDistanceTo(r.x(), r.y(), r.z()) > max * max) {
                        if (e instanceof MobEntity mob) mob.getNavigation().stop();
                        e.requestTeleport(r.x(), r.y(), r.z());
                    }
                }
            }
        }
    }
}

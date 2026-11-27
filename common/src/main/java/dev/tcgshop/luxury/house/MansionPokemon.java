package dev.tcgshop.luxury.house;

import dev.tcgshop.TcgShop;
import dev.tcgshop.compat.CobblemonActors;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import java.lang.reflect.Method;
import java.util.function.Consumer;

/**
 * Resident Pokémon of the estate (only with Cobblemon, via reflection through {@link CobblemonActors}):
 *  · created as wild "uncatchable=yes", invulnerable, unbattleable (tracked data UNBATTLEABLE, which Cobblemon
 *    stores in NBT as "Unbattleable") and persistent (MobEntity.setPersistent: PokemonEntity.checkDespawn does not
 *    despawn persistent ones);
 *  · they ARE saved with the world (unlike event actors) and carry the {@link #TAG} tag;
 *  · our own hook on THROWN_POKEBALL_HIT: any Poké Ball thrown at a resident is cancelled;
 *  · on load (ENTITY_LOAD) everything is reapplied in case some version doesn't save some data;
 *  · every 5 s, if a loaded resident wanders farther from home than its radius, it returns (they don't leave the estate).
 * Snorlax stays still (no AI) sleeping on the lawn; the rest wander a bit within their radius.
 */
public final class MansionPokemon {
    public static final String TAG = "tcgshop_mansion_mon";
    public static final String STILL_TAG = "tcgshop_mansion_still";
    private static boolean hookTried;

    private MansionPokemon() {}

    public static boolean isResident(Entity e) {
        return e.getTags().contains(TAG);
    }

    /** Cobblemon hook (once per JVM): they cannot be caught. Does nothing without Cobblemon. */
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
            TcgShop.LOGGER.warn("[mansion] could not hook THROWN_POKEBALL_HIT ({}); residents are still uncatchable=yes", e.toString());
        }
    }

    /** Reapplies the rules to a resident loaded from disk. */
    static void onLoad(Entity e) {
        if (!isResident(e)) return;
        apply(e, e.getTags().contains(STILL_TAG));
    }

    private static void apply(Entity e, boolean still) {
        e.setInvulnerable(true);
        if (e instanceof Mob mob) {
            mob.setPersistenceRequired();
            if (still) mob.setNoAi(true);
        }
        CobblemonActors.setUnbattleable(e, true);
        CobblemonActors.noSpawnCap(e);
    }

    /** Spawns the estate's residents. Returns how many appeared (0 without Cobblemon or if disabled). */
    static int spawn(ServerLevel world, MansionState.Record rec, MansionDesign design, MansionPlan plan) {
        if (!CobblemonActors.available()) return 0;
        installHooks();
        int n = 0;
        for (MansionDesign.ResidentSpec r : design.residents) {
            try {
                Entity e = CobblemonActors.create(world, r.species(), r.level());
                if (e == null) continue;
                Vec3 v = plan.center(r.x(), r.y(), r.z());
                float yaw = plan.facing.getOpposite().toYRot() + (world.getRandom().nextFloat() - 0.5f) * 90f;
                e.moveTo(v.x, v.y, v.z, yaw, 0);
                e.addTag(TAG);
                if (r.still()) e.addTag(STILL_TAG);
                apply(e, r.still());
                if (!world.addFreshEntity(e)) continue;
                rec.residents.add(new MansionState.Resident(e.getUUID(), r.species(), v.x, v.y, v.z, r.radius()));
                n++;
            } catch (Throwable t) {
                TcgShop.LOGGER.warn("[mansion] could not spawn resident {}: {}", r.species(), t.toString());
            }
        }
        return n;
    }

    private static int clock;

    /** Every 100 ticks: loaded residents that wandered too far go back to their spot. */
    static void tick(MinecraftServer server) {
        if (++clock % 100 != 0) return;
        for (ServerLevel w : server.getAllLevels()) {
            MansionState st = MansionState.get(w);
            for (MansionState.Record rec : st.all()) {
                for (MansionState.Resident r : rec.residents) {
                    Entity e = w.getEntity(r.uuid());
                    if (e == null || e.isRemoved() || e.isPassenger()) continue;
                    double max = Math.max(2, r.radius());
                    if (e.distanceToSqr(r.x(), r.y(), r.z()) > max * max) {
                        if (e instanceof Mob mob) mob.getNavigation().stop();
                        e.teleportTo(r.x(), r.y(), r.z());
                    }
                }
            }
        }
    }
}

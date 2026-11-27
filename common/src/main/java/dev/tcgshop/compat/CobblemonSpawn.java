package dev.tcgshop.compat;

import dev.masterball.api.platform.Platform;
import dev.tcgshop.TcgShop;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import java.lang.reflect.Method;

/**
 * Spawning of normal (catchable) Cobblemon wild Pokémon, via reflection and with no compile-time dependency.
 * Used by the tutorial: the "Team Rocket Meowth" is just an ordinary wild Meowth with a nickname that does not despawn.
 */
public final class CobblemonSpawn {
    private static boolean warned;

    private CobblemonSpawn() {}

    public static boolean available() {
        return Platform.INSTANCE.isModLoaded("cobblemon");
    }

    /**
     * Spawns a wild Pokémon with the given properties (e.g. "meowth level=10") at {@code pos}, with an optional nickname,
     * persistent (does not despawn) and catchable. Returns the entity or null if Cobblemon is missing or something fails.
     */
    public static Entity spawnWild(ServerLevel world, Vec3 pos, float yaw, String properties, MutableComponent nickname) {
        if (!available()) return null;
        try {
            Class<?> propsClass = Class.forName("com.cobblemon.mod.common.api.pokemon.PokemonProperties");
            Object companion = propsClass.getField("Companion").get(null);
            Object props = companion.getClass().getMethod("parse", String.class).invoke(companion, properties);
            Entity pokemon = (Entity) propsClass.getMethod("createEntity", Level.class).invoke(props, world);
            if (pokemon == null) return null;
            if (nickname != null) {
                try {
                    Object pk = pokemon.getClass().getMethod("getPokemon").invoke(pokemon);
                    Method set = null;
                    for (Method m : pk.getClass().getMethods())
                        if (m.getName().equals("setNickname") && m.getParameterCount() == 1) set = m;
                    if (set != null) set.invoke(pk, nickname);
                } catch (Throwable e) {
                    TcgShop.LOGGER.warn("Could not nickname the tutorial Pokémon: {}", e.toString());
                }
            }
            pokemon.moveTo(pos.x, pos.y, pos.z, yaw, 0);
            // ownerless wild Pokémon only despawn if they are not persistent (PokemonEntity.checkDespawn)
            if (pokemon instanceof Mob mob) mob.setPersistenceRequired();
            if (!world.addFreshEntity(pokemon)) return null;
            return pokemon;
        } catch (Throwable e) {
            if (!warned) {
                warned = true;
                TcgShop.LOGGER.warn("Could not spawn a Cobblemon Pokémon: {}", e.toString());
            }
            return null;
        }
    }

    /** Free spot (2 blocks tall with no collision and solid ground) near {@code around}, or null. */
    public static Vec3 freeSpotNear(ServerLevel world, Vec3 around, Vec3 preferredDir) {
        Vec3 dir = new Vec3(preferredDir.x, 0, preferredDir.z);
        dir = dir.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : dir.normalize();
        // in front first, then rotating around
        for (int step = 0; step < 8; step++) {
            double ang = Math.toRadians(step * 45.0);
            double dx = dir.x * Math.cos(ang) - dir.z * Math.sin(ang), dz = dir.x * Math.sin(ang) + dir.z * Math.cos(ang);
            for (double dist : new double[]{2.0, 1.4}) {
                Vec3 p = around.add(dx * dist, 0, dz * dist);
                for (int dy : new int[]{0, 1, -1}) {
                    BlockPos feet = BlockPos.containing(p.x, around.y + dy, p.z);
                    if (free(world, feet) && free(world, feet.above()) && !free(world, feet.below()))
                        return new Vec3(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
                }
            }
        }
        return null;
    }

    private static boolean free(ServerLevel world, BlockPos p) {
        return world.getBlockState(p).getCollisionShape(world, p).isEmpty() && world.getFluidState(p).isEmpty();
    }
}

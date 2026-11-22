package dev.tcgshop.compat;

import dev.tcgshop.TcgShop;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.MutableText;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.lang.reflect.Method;

/**
 * Aparición de Pokémon salvajes normales (capturables) de Cobblemon, por reflexión y sin dependencia de compilación.
 * Lo usa el tutorial: el "Meowth del Team Rocket" es un Meowth salvaje cualquiera con mote y que no desaparece.
 */
public final class CobblemonSpawn {
    private static boolean warned;

    private CobblemonSpawn() {}

    public static boolean available() {
        return FabricLoader.getInstance().isModLoaded("cobblemon");
    }

    /**
     * Saca un Pokémon salvaje con las propiedades dadas (p.ej. "meowth level=10") en {@code pos}, con mote opcional,
     * persistente (no se despawnea) y capturable. Devuelve la entidad o null si Cobblemon no está o algo falla.
     */
    public static Entity spawnWild(ServerWorld world, Vec3d pos, float yaw, String properties, MutableText nickname) {
        if (!available()) return null;
        try {
            Class<?> propsClass = Class.forName("com.cobblemon.mod.common.api.pokemon.PokemonProperties");
            Object companion = propsClass.getField("Companion").get(null);
            Object props = companion.getClass().getMethod("parse", String.class).invoke(companion, properties);
            Entity pokemon = (Entity) propsClass.getMethod("createEntity", World.class).invoke(props, world);
            if (pokemon == null) return null;
            if (nickname != null) {
                try {
                    Object pk = pokemon.getClass().getMethod("getPokemon").invoke(pokemon);
                    Method set = null;
                    for (Method m : pk.getClass().getMethods())
                        if (m.getName().equals("setNickname") && m.getParameterCount() == 1) set = m;
                    if (set != null) set.invoke(pk, nickname);
                } catch (Throwable e) {
                    TcgShop.LOGGER.warn("No se pudo poner mote al Pokémon del tutorial: {}", e.toString());
                }
            }
            pokemon.refreshPositionAndAngles(pos.x, pos.y, pos.z, yaw, 0);
            // los Pokémon salvajes sin dueño sólo se despawnean si no son persistentes (PokemonEntity.checkDespawn)
            if (pokemon instanceof MobEntity mob) mob.setPersistent();
            if (!world.spawnEntity(pokemon)) return null;
            return pokemon;
        } catch (Throwable e) {
            if (!warned) {
                warned = true;
                TcgShop.LOGGER.warn("No se pudo sacar un Pokémon de Cobblemon: {}", e.toString());
            }
            return null;
        }
    }

    /** Hueco libre (2 bloques de alto sin colisión y suelo sólido) cerca de {@code around}, o null. */
    public static Vec3d freeSpotNear(ServerWorld world, Vec3d around, Vec3d preferredDir) {
        Vec3d dir = new Vec3d(preferredDir.x, 0, preferredDir.z);
        dir = dir.lengthSquared() < 1e-4 ? new Vec3d(1, 0, 0) : dir.normalize();
        // primero delante, luego girando alrededor
        for (int step = 0; step < 8; step++) {
            double ang = Math.toRadians(step * 45.0);
            double dx = dir.x * Math.cos(ang) - dir.z * Math.sin(ang), dz = dir.x * Math.sin(ang) + dir.z * Math.cos(ang);
            for (double dist : new double[]{2.0, 1.4}) {
                Vec3d p = around.add(dx * dist, 0, dz * dist);
                for (int dy : new int[]{0, 1, -1}) {
                    BlockPos feet = BlockPos.ofFloored(p.x, around.y + dy, p.z);
                    if (free(world, feet) && free(world, feet.up()) && !free(world, feet.down()))
                        return new Vec3d(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
                }
            }
        }
        return null;
    }

    private static boolean free(ServerWorld world, BlockPos p) {
        return world.getBlockState(p).getCollisionShape(world, p).isEmpty() && world.getFluidState(p).isEmpty();
    }
}

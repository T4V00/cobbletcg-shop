package dev.tcgshop.compat;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The player's Pokémon party via reflection (no compile-time dependency on Cobblemon): list and remove one.
 * If Cobblemon is missing or something changes in its API, everything returns empty / false and nothing is touched.
 */
public final class CobblemonParty {
    private CobblemonParty() {}

    public record Mon(UUID uuid, Text name, int level, boolean shiny, String species) {}

    @Nullable
    private static Object party(ServerPlayerEntity player) {
        try {
            Class<?> ext = Class.forName("com.cobblemon.mod.common.util.PlayerExtensionsKt");
            return ext.getMethod("party", ServerPlayerEntity.class).invoke(null, player);
        } catch (Throwable e) {
            return null;
        }
    }

    public static List<Mon> list(ServerPlayerEntity player) {
        List<Mon> out = new ArrayList<>();
        Object party = party(player);
        if (party == null) return out;
        try {
            for (Object pk : (Iterable<?>) party) {
                if (pk == null) continue;
                UUID id = (UUID) pk.getClass().getMethod("getUuid").invoke(pk);
                int lvl = (int) pk.getClass().getMethod("getLevel").invoke(pk);
                boolean shiny = (boolean) pk.getClass().getMethod("getShiny").invoke(pk);
                Object species = pk.getClass().getMethod("getSpecies").invoke(pk);
                String sp = String.valueOf(species.getClass().getMethod("getName").invoke(species));
                out.add(new Mon(id, displayName(pk, species), lvl, shiny, sp));
            }
        } catch (Throwable e) {
            dev.tcgshop.TcgShop.LOGGER.warn("Could not read the Pokémon party", e);
            return new ArrayList<>();
        }
        return out;
    }

    private static Text displayName(Object pk, Object species) {
        try {
            for (Method m : pk.getClass().getMethods()) {
                if (!m.getName().equals("getDisplayName")) continue;
                if (m.getParameterCount() == 0) return (Text) m.invoke(pk);
                if (m.getParameterCount() == 1 && m.getParameterTypes()[0] == boolean.class) return (Text) m.invoke(pk, false);
            }
            return (Text) species.getClass().getMethod("getTranslatedName").invoke(species);
        } catch (Throwable e) {
            return Text.literal("?");
        }
    }

    /**
     * Removes the Pokémon with that UUID from the party. Returns true only if Cobblemon confirms it was removed.
     * Never leaves the player without Pokémon (if it is the last one, it is not removed).
     */
    public static boolean remove(ServerPlayerEntity player, UUID uuid) {
        Object party = party(player);
        if (party == null) return false;
        try {
            Object target = null;
            int count = 0;
            for (Object pk : (Iterable<?>) party) {
                if (pk == null) continue;
                count++;
                if (uuid.equals(pk.getClass().getMethod("getUuid").invoke(pk))) target = pk;
            }
            if (target == null || count <= 1) return false;
            for (Method m : party.getClass().getMethods()) {
                if (!m.getName().equals("remove") || m.getParameterCount() != 1) continue;
                if (!m.getParameterTypes()[0].isInstance(target)) continue;
                Object r = m.invoke(party, target);
                return Boolean.TRUE.equals(r);
            }
        } catch (Throwable e) {
            dev.tcgshop.TcgShop.LOGGER.warn("Could not remove the Pokémon from the party", e);
        }
        return false;
    }

    public static int count(ServerPlayerEntity player) {
        return list(player).size();
    }
}

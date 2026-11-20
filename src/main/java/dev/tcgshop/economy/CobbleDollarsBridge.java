package dev.tcgshop.economy;

import dev.tcgshop.TcgShop;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.player.PlayerEntity;

import java.lang.reflect.Method;
import java.math.BigInteger;

/**
 * Acceso a CobbleDollars sin dependencia de compilación.
 * CobbleDollars añade la interfaz fr.harmex.cobbledollars.common.utils.CobbleDollarsPlayer
 * a PlayerEntity con getters/setters de un BigInteger.
 */
public final class CobbleDollarsBridge {
    private static final String IFACE = "fr.harmex.cobbledollars.common.utils.CobbleDollarsPlayer";
    private static Method getter, setter;
    private static boolean initialized, available;

    private CobbleDollarsBridge() {}

    private static void init() {
        if (initialized) return;
        initialized = true;
        if (!FabricLoader.getInstance().isModLoaded("cobbledollars")) return;
        try {
            Class<?> c = Class.forName(IFACE);
            getter = c.getMethod("cobbleDollars$getCobbleDollars");
            setter = c.getMethod("cobbleDollars$setCobbleDollars", BigInteger.class);
            available = true;
        } catch (Throwable t) {
            TcgShop.LOGGER.warn("CobbleDollars encontrado pero su API cambió; la economía quedará desactivada", t);
        }
    }

    public static boolean isAvailable() {
        init();
        return available;
    }

    public static long getBalance(PlayerEntity player) {
        if (!isAvailable()) return 0;
        try {
            return ((BigInteger) getter.invoke(player)).longValue();
        } catch (Throwable t) {
            return 0;
        }
    }

    public static boolean add(PlayerEntity player, long amount) {
        if (!isAvailable()) return false;
        try {
            BigInteger cur = (BigInteger) getter.invoke(player);
            setter.invoke(player, cur.add(BigInteger.valueOf(amount)));
            return true;
        } catch (Throwable t) {
            TcgShop.LOGGER.error("Error modificando CobbleDollars", t);
            return false;
        }
    }

    /** Cobra si hay saldo suficiente. */
    public static boolean tryCharge(PlayerEntity player, long amount) {
        if (getBalance(player) < amount) return false;
        return add(player, -amount);
    }
}

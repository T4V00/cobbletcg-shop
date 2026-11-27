package dev.tcgshop.economy;

import dev.masterball.api.platform.Platform;
import dev.tcgshop.TcgShop;
import net.minecraft.world.entity.player.Player;
import java.lang.reflect.Method;
import java.math.BigInteger;

/**
 * Access to CobbleDollars with no compile-time dependency.
 * CobbleDollars adds the interface fr.harmex.cobbledollars.common.utils.CobbleDollarsPlayer
 * to PlayerEntity with getters/setters for a BigInteger.
 */
public final class CobbleDollarsBridge {
    private static final String IFACE = "fr.harmex.cobbledollars.common.utils.CobbleDollarsPlayer";
    private static Method getter, setter;
    private static boolean initialized, available;

    private CobbleDollarsBridge() {}

    private static void init() {
        if (initialized) return;
        initialized = true;
        if (!Platform.INSTANCE.isModLoaded("cobbledollars")) return;
        try {
            Class<?> c = Class.forName(IFACE);
            getter = c.getMethod("cobbleDollars$getCobbleDollars");
            setter = c.getMethod("cobbleDollars$setCobbleDollars", BigInteger.class);
            available = true;
        } catch (Throwable t) {
            TcgShop.LOGGER.warn("CobbleDollars found but its API changed; the economy will be disabled", t);
        }
    }

    public static boolean isAvailable() {
        init();
        return available;
    }

    public static long getBalance(Player player) {
        if (!isAvailable()) return 0;
        try {
            return ((BigInteger) getter.invoke(player)).longValue();
        } catch (Throwable t) {
            return 0;
        }
    }

    public static boolean add(Player player, long amount) {
        if (!isAvailable()) return false;
        try {
            BigInteger cur = (BigInteger) getter.invoke(player);
            setter.invoke(player, cur.add(BigInteger.valueOf(amount)));
            return true;
        } catch (Throwable t) {
            TcgShop.LOGGER.error("Error modifying CobbleDollars", t);
            return false;
        }
    }

    /** Charges if the balance is sufficient. */
    public static boolean tryCharge(Player player, long amount) {
        if (getBalance(player) < amount) return false;
        return add(player, -amount);
    }
}

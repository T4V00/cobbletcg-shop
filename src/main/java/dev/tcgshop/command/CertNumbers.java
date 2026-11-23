package dev.tcgshop.command;

import dev.tcgshop.TcgShop;
import dev.tcgshop.grading.GradingService;
import net.minecraft.server.MinecraftServer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * CobbleGrade certificate numbers for cards graded via command. They use the same counter as regular
 * orders (GradingService.nextCert), so a certificate is never repeated.
 * <p>
 * If GradingService exposes {@code public static int nextCert(MinecraftServer)} that method is used; otherwise the
 * private field is read via reflection (same mod, no Java modules, so it is always accessible).
 */
final class CertNumbers {
    private static Method method;
    private static Field field;
    private static boolean looked;
    private static int fallback = 900_001; // only if everything else fails (should not happen)

    private CertNumbers() {}

    static synchronized int next(MinecraftServer server) {
        GradingService s = GradingService.get(server);
        if (!looked) {
            looked = true;
            try {
                method = GradingService.class.getMethod("nextCert", MinecraftServer.class);
            } catch (NoSuchMethodException ignored) {
                try {
                    field = GradingService.class.getDeclaredField("nextCert");
                    field.setAccessible(true);
                } catch (ReflectiveOperationException | RuntimeException e) {
                    TcgShop.LOGGER.warn("[CardCommands] CobbleGrade certificate counter not found", e);
                }
            }
        }
        try {
            if (method != null) return (int) method.invoke(null, server);
            if (field != null) {
                int c = field.getInt(s);
                field.setInt(s, c + 1);
                s.markDirty();
                return c;
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            TcgShop.LOGGER.warn("[CardCommands] could not obtain a new certificate", e);
        }
        return fallback++;
    }
}

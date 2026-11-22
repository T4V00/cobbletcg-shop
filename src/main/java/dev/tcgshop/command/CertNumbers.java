package dev.tcgshop.command;

import dev.tcgshop.TcgShop;
import dev.tcgshop.grading.GradingService;
import net.minecraft.server.MinecraftServer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Números de certificado CobbleGrade para las cartas gradeadas por comando. Usan el mismo contador que los pedidos
 * normales (GradingService.nextCert), así nunca se repite un certificado.
 * <p>
 * Si GradingService expone {@code public static int nextCert(MinecraftServer)} se usa ese método; si no, se lee el
 * campo privado por reflexión (mismo mod, sin módulos de Java, así que siempre es accesible).
 */
final class CertNumbers {
    private static Method method;
    private static Field field;
    private static boolean looked;
    private static int fallback = 900_001; // sólo si todo lo demás falla (no debería pasar)

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
                    TcgShop.LOGGER.warn("[CardCommands] no se encontró el contador de certificados de CobbleGrade", e);
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
            TcgShop.LOGGER.warn("[CardCommands] no se pudo obtener un certificado nuevo", e);
        }
        return fallback++;
    }
}

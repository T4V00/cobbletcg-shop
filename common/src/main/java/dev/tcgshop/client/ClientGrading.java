package dev.tcgshop.client;

import dev.masterball.api.platform.Platform;
import dev.masterball.api.platform.ClientPlatform;
import dev.tcgshop.network.GradePayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** CobbleGrade orders on the client (for the phone app). */
public final class ClientGrading {
    public static List<GradePayloads.Order> orders = new ArrayList<>();
    private static long serverNow, localAtSync;

    private ClientGrading() {}

    public static void apply(GradePayloads.Sync s) {
        orders = new ArrayList<>(s.orders());
        serverNow = s.now();
        localAtSync = localTime();
    }

    private static long localTime() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 0 : mc.level.getGameTime();
    }

    /** Estimated server time (the one from the last sync + what has elapsed since then). */
    public static long now() { return serverNow + Math.max(0, localTime() - localAtSync); }

    public static int readyCount() {
        int n = 0;
        long now = now();
        for (GradePayloads.Order o : orders) if (now >= o.ready()) n++;
        return n;
    }

    public static void request() {
        if (ClientPlatform.INSTANCE.canSend(GradePayloads.Action.ID)) ClientPlatform.INSTANCE.send(new GradePayloads.Action(GradePayloads.SYNC, 0, List.of(), 0));
    }

    /** Minecraft ticks to "1 d 4 h" / "3 h 20 min" (1000 ticks = 1 in-game hour). */
    public static Component duration(long ticks) {
        long min = Math.max(0, ticks) * 60 / 1000;
        long d = min / (24 * 60), h = (min / 60) % 24, m = min % 60;
        if (d > 0) return Component.translatable("tcgshop.grade.time_dh", d, h);
        if (h > 0) return Component.translatable("tcgshop.grade.time_hm", h, m);
        return Component.literal(String.format(Locale.ROOT, "%d min", Math.max(1, m)));
    }
}

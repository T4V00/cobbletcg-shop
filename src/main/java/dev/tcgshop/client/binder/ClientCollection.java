package dev.tcgshop.client.binder;

import dev.tcgshop.network.BinderActionPayload;
import dev.tcgshop.network.CollectionSyncPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Copia en el cliente del Binder General (la envía el servidor). */
public final class ClientCollection {
    public static Map<String, Integer> counts = new HashMap<>();
    public static Set<String> fresh = new HashSet<>();
    public static long totalObtained, packsOpened, packsRuined, godPacks, credited;
    public static List<String> unlockedStyles = new ArrayList<>();
    public static Set<String> wishlist = new HashSet<>();
    public static List<String> titles = new ArrayList<>();
    public static String title = "";
    /** uuid de jugador -> título elegido ("especie|tier"), para mostrarlo sobre su nombre. */
    public static Map<String, String> playerTitles = new HashMap<>();

    private static final Set<String> pendingSeen = new HashSet<>();
    private static long lastSeenFlush;

    private ClientCollection() {}

    public static void apply(CollectionSyncPayload p) {
        counts = new HashMap<>(p.counts());
        fresh = new HashSet<>(p.fresh());
        fresh.removeAll(pendingSeen);
        List<Long> s = p.stats();
        totalObtained = s.size() > 0 ? s.get(0) : 0;
        packsOpened = s.size() > 1 ? s.get(1) : 0;
        packsRuined = s.size() > 2 ? s.get(2) : 0;
        godPacks = s.size() > 3 ? s.get(3) : 0;
        credited = s.size() > 4 ? s.get(4) : 0;
        wishlist = new HashSet<>(p.wishlist());
        titles = new ArrayList<>(p.titles());
        title = p.title();
    }

    public static int count(String id) {
        return counts.getOrDefault(id, 0);
    }

    /** Marca una carta nueva como vista: quita la notificación (se envía al servidor en lotes). El binder decide cuándo. */
    public static void markSeen(String id) {
        if (fresh.remove(id)) pendingSeen.add(id);
    }

    public static void tick() {
        long now = System.currentTimeMillis();
        if (!pendingSeen.isEmpty() && now - lastSeenFlush > 500) {
            lastSeenFlush = now;
            ClientPlayNetworking.send(new BinderActionPayload(BinderActionPayload.MARK_SEEN, String.join(",", pendingSeen), 0));
            pendingSeen.clear();
        }
    }

    /** Envía ya las cartas vistas pendientes (al cerrar el binder o salir de «Nuevas»), sin esperar al lote. */
    public static void flushSeen() {
        if (pendingSeen.isEmpty()) return;
        if (net.minecraft.client.MinecraftClient.getInstance().getNetworkHandler() == null) return; // desconectando
        try {
            ClientPlayNetworking.send(new BinderActionPayload(BinderActionPayload.MARK_SEEN, String.join(",", pendingSeen), 0));
            pendingSeen.clear();
            lastSeenFlush = System.currentTimeMillis();
        } catch (IllegalStateException ignored) {
            // sin conexión: el servidor seguirá teniéndolas como nuevas
        }
    }

    public static void action(int action, String arg, int amount) {
        ClientPlayNetworking.send(new BinderActionPayload(action, arg, amount));
    }
}

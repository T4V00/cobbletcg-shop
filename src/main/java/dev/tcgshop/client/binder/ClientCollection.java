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

/** Client-side copy of the General Binder (sent by the server). */
public final class ClientCollection {
    public static Map<String, Integer> counts = new HashMap<>();
    public static Set<String> fresh = new HashSet<>();
    public static long totalObtained, packsOpened, packsRuined, godPacks, credited;
    public static List<String> unlockedStyles = new ArrayList<>();
    public static Set<String> wishlist = new HashSet<>();
    public static List<String> titles = new ArrayList<>();
    public static String title = "";
    /** player uuid -> chosen title ("species|tier"), to show it above their name. */
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

    /** Marks a new card as seen: removes the notification (sent to the server in batches). The binder decides when. */
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

    /** Sends the pending seen cards right away (when closing the binder or leaving «New»), without waiting for the batch. */
    public static void flushSeen() {
        if (pendingSeen.isEmpty()) return;
        if (net.minecraft.client.MinecraftClient.getInstance().getNetworkHandler() == null) return; // disconnecting
        try {
            ClientPlayNetworking.send(new BinderActionPayload(BinderActionPayload.MARK_SEEN, String.join(",", pendingSeen), 0));
            pendingSeen.clear();
            lastSeenFlush = System.currentTimeMillis();
        } catch (IllegalStateException ignored) {
            // no connection: the server will keep them marked as new
        }
    }

    public static void action(int action, String arg, int amount) {
        ClientPlayNetworking.send(new BinderActionPayload(action, arg, amount));
    }
}

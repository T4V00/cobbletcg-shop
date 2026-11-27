package dev.tcgshop.phone;

import dev.masterball.api.platform.Net;
import dev.masterball.api.platform.event.PlayerEvents;
import dev.masterball.api.platform.event.ServerEvents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * 1.0 phone apps, server side glue: Messages ({@link PhoneMessages}), Loans ({@link Loans}) and Stats
 * ({@link PhoneStats}). Registers the {@link PhonePayloads.Apps} payload, routes the client actions that arrive through
 * {@link PhonePayloads.Act}, runs their tick and syncs them on join. Called from PhoneUpgrades.register().
 */
public final class PhoneApps {
    private PhoneApps() {}

    /** Prefix of a notice argument that is a translation key (translated on the client, in the player's language). */
    public static final String KEY_ARG = "@@";

    static void register() {
        Net.INSTANCE.s2c(PhonePayloads.Apps.ID, PhonePayloads.Apps.CODEC);
        ServerEvents.TICK_END.register(PhoneApps::tick);
        PlayerEvents.JOIN.register(player -> {
            ServerPlayer sp = player;
            player.server.execute(() -> {
                PhoneMessages.sync(sp, null);
                Loans.sync(sp);
            });
        });
        PlayerEvents.LEAVE.register(player -> PhoneMessages.forget(player.getUUID()));
        Ranking.register(); // 1.1: Ranking app (capital refresh every minute, climb notices)
        ServerEvents.STOPPED.register(server -> PhoneMessages.clearRuntime());
    }

    /** Client actions of the 1.0 apps (PhoneUpgrades.handle forwards every "a" it does not know). */
    static void handle(ServerPlayer p, CompoundTag d) {
        String a = d.getString("a");
        if (a.equals("messages") || a.startsWith("msg_")) PhoneMessages.handle(p, d);
        else if (a.equals("loans") || a.startsWith("loan_")) Loans.handle(p, d);
        else if (a.equals("stats")) PhoneStats.send(p);
        else if (a.equals("box_open")) dev.tcgshop.pack.BoxGloves.handle(p, d); // 1.1: Box-Opening Gloves answer
        else if (a.equals("ranking")) Ranking.request(p); // 1.1: Ranking app (rate-limited)
    }

    private static void tick(MinecraftServer server) {
        PhoneMessages.tick(server);
        if (server.getTickCount() % 400 == 0) Loans.tick(server); // every 20 s
    }

    /**
     * Shows a notice inside the player's phone (or the Wholesaler's stall). kind = "success", "error" or "info"; args
     * are shown as text (a String starting with {@link #KEY_ARG} is translated on the client).
     */
    public static void notice(ServerPlayer p, String kind, String titleKey, String bodyKey, Object... args) {
        if (p == null || !Net.INSTANCE.canSend(p, PhonePayloads.Apps.ID)) return;
        CompoundTag d = new CompoundTag();
        d.putString("k", "notice");
        d.putString("kind", kind);
        d.putString("title", titleKey);
        d.putString("body", bodyKey);
        ListTag l = new ListTag();
        for (Object o : args) l.add(StringTag.valueOf(String.valueOf(o)));
        d.put("args", l);
        Net.INSTANCE.send(p, new PhonePayloads.Apps(d));
    }
}

package dev.tcgshop.phone;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

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
        PayloadTypeRegistry.playS2C().register(PhonePayloads.Apps.ID, PhonePayloads.Apps.CODEC);
        ServerTickEvents.END_SERVER_TICK.register(PhoneApps::tick);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity sp = handler.getPlayer();
            server.execute(() -> {
                PhoneMessages.sync(sp, null);
                Loans.sync(sp);
            });
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> PhoneMessages.forget(handler.getPlayer().getUuid()));
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server -> PhoneMessages.clearRuntime());
    }

    /** Client actions of the 1.0 apps (PhoneUpgrades.handle forwards every "a" it does not know). */
    static void handle(ServerPlayerEntity p, NbtCompound d) {
        String a = d.getString("a");
        if (a.equals("messages") || a.startsWith("msg_")) PhoneMessages.handle(p, d);
        else if (a.equals("loans") || a.startsWith("loan_")) Loans.handle(p, d);
        else if (a.equals("stats")) PhoneStats.send(p);
    }

    private static void tick(MinecraftServer server) {
        PhoneMessages.tick(server);
        if (server.getTicks() % 400 == 0) Loans.tick(server); // every 20 s
    }

    /**
     * Shows a notice inside the player's phone (or the Wholesaler's stall). kind = "success", "error" or "info"; args
     * are shown as text (a String starting with {@link #KEY_ARG} is translated on the client).
     */
    public static void notice(ServerPlayerEntity p, String kind, String titleKey, String bodyKey, Object... args) {
        if (p == null || !ServerPlayNetworking.canSend(p, PhonePayloads.Apps.ID)) return;
        NbtCompound d = new NbtCompound();
        d.putString("k", "notice");
        d.putString("kind", kind);
        d.putString("title", titleKey);
        d.putString("body", bodyKey);
        NbtList l = new NbtList();
        for (Object o : args) l.add(NbtString.of(String.valueOf(o)));
        d.put("args", l);
        ServerPlayNetworking.send(p, new PhonePayloads.Apps(d));
    }
}

package dev.tcgshop.client;

import dev.tcgshop.shop.Discounts;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.nbt.NbtCompound;

/** 1.0: client copy of the store sale event (Discounts.percent reads it on the client). */
public final class DiscountsClient {
    private DiscountsClient() {}

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(Discounts.SyncPayload.ID, (p, ctx) -> ctx.client().execute(() -> Discounts.applyClient(p.data())));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> Discounts.applyClient(new NbtCompound())));
    }
}

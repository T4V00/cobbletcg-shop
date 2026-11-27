package dev.tcgshop.client;

import dev.tcgshop.shop.Discounts;
import dev.masterball.api.platform.ClientPlatform;
import dev.masterball.api.platform.event.ClientEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

/** 1.0: client copy of the store sale event (Discounts.percent reads it on the client). */
public final class DiscountsClient {
    private DiscountsClient() {}

    public static void register() {
        ClientPlatform.INSTANCE.receive(Discounts.SyncPayload.ID, p -> Minecraft.getInstance().execute(() -> Discounts.applyClient(p.data())));
        ClientEvents.DISCONNECT.register(client -> client.execute(() -> Discounts.applyClient(new CompoundTag())));
    }
}

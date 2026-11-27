package dev.tcgshop.fabric;

import dev.masterball.api.fabric.platform.FabricClientEventBridge;
import dev.tcgshop.client.TcgShopClient;
import net.fabricmc.api.ClientModInitializer;

/** Fabric client entry point. */
public final class TcgShopClientFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        FabricClientEventBridge.install();
        TcgShopClient.init();
    }
}

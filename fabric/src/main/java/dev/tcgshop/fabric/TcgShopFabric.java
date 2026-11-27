package dev.tcgshop.fabric;

import dev.masterball.api.fabric.platform.FabricEventBridge;
import dev.tcgshop.TcgShop;
import net.fabricmc.api.ModInitializer;

/** Fabric entry point: makes sure Fabric's events reach the shared platform layer, then runs the shared init. */
public final class TcgShopFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        FabricEventBridge.install(); // idempotent: the API's entry point may run before or after this one
        TcgShop.init();
    }
}

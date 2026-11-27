package dev.tcgshop.neoforge;

import dev.masterball.api.neoforge.platform.NeoForgeClientPlatform;
import dev.tcgshop.TcgShop;
import dev.tcgshop.client.TcgShopClient;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * NeoForge client entry point. The shared client init references registered items, so it is queued and runs at the
 * first client registration event (after every registry is complete), after the API's own client init.
 */
@Mod(value = TcgShop.MOD_ID, dist = Dist.CLIENT)
public final class TcgShopNeoForgeClient {
    public TcgShopNeoForgeClient(IEventBus modBus, ModContainer container) {
        NeoForgeClientPlatform.onClientInit(TcgShopClient::init);
    }
}

package dev.tcgshop.events.incident;

import dev.masterball.api.platform.Platform;
import dev.masterball.api.platform.ClientPlatform;
import dev.tcgshop.shop.TcgBlocks;
import net.minecraft.client.renderer.RenderType;

/** Client part of the incident module (separate class so client classes are not touched on the server). */
final class IncidentClient {
    private IncidentClient() {}

    static void init() {
        // translucent: puddles and scorch marks use partial transparency
        ClientPlatform.INSTANCE.blockRenderType(RenderType.translucent(), TcgBlocks.INCIDENT);
    }
}

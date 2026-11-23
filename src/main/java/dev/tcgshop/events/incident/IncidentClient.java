package dev.tcgshop.events.incident;

import dev.tcgshop.shop.TcgBlocks;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.minecraft.client.render.RenderLayer;

/** Client part of the incident module (separate class so client classes are not touched on the server). */
final class IncidentClient {
    private IncidentClient() {}

    static void init() {
        // translucent: puddles and scorch marks use partial transparency
        BlockRenderLayerMap.INSTANCE.putBlock(TcgBlocks.INCIDENT, RenderLayer.getTranslucent());
    }
}

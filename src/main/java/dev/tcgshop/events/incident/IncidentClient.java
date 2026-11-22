package dev.tcgshop.events.incident;

import dev.tcgshop.shop.TcgBlocks;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.minecraft.client.render.RenderLayer;

/** Parte de cliente del módulo de incidentes (clase aparte para no tocar clases de cliente en el servidor). */
final class IncidentClient {
    private IncidentClient() {}

    static void init() {
        // translúcida: los charcos y las quemaduras usan transparencia parcial
        BlockRenderLayerMap.INSTANCE.putBlock(TcgBlocks.INCIDENT, RenderLayer.getTranslucent());
    }
}

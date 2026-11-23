package dev.tcgshop.world;

import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.minecraft.client.render.RenderLayer;

/** Client side of the 1.0 world loot: the trash can model has see-through texels (paper scraps, peel). */
public final class WorldLootClient {
    private WorldLootClient() {}

    public static void init() {
        BlockRenderLayerMap.INSTANCE.putBlock(WorldLoot.TRASH_CAN, RenderLayer.getCutout());
    }
}

package dev.tcgshop.world;

import dev.masterball.api.platform.Platform;
import dev.masterball.api.platform.ClientPlatform;
import net.minecraft.client.renderer.RenderType;

/** Client side of the 1.0 world loot: the trash can model has see-through texels (paper scraps, peel). */
public final class WorldLootClient {
    private WorldLootClient() {}

    public static void init() {
        ClientPlatform.INSTANCE.blockRenderType(RenderType.cutout(), WorldLoot.TRASH_CAN);
    }
}

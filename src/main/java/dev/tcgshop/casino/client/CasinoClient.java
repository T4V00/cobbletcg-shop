package dev.tcgshop.casino.client;

import dev.tcgshop.casino.CasinoBlocks;
import dev.tcgshop.casino.CasinoGame;
import dev.tcgshop.casino.CasinoPayloads;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;

/** Client side of the casino: screens for the Open / Update payloads, the machine renderer and render layers. */
public final class CasinoClient {
    private CasinoClient() {}

    public static void init() {
        BlockEntityRendererFactories.register(CasinoBlocks.MACHINE_BE, CasinoRenderer::new);
        BlockRenderLayerMap.INSTANCE.putBlocks(RenderLayer.getCutout(), CasinoBlocks.MACHINES);
        ClientPlayNetworking.registerGlobalReceiver(CasinoPayloads.Open.ID, (p, ctx) -> ctx.client().execute(() -> open(ctx.client(), p)));
        ClientPlayNetworking.registerGlobalReceiver(CasinoPayloads.Update.ID, (p, ctx) -> ctx.client().execute(() -> {
            if (ctx.client().currentScreen instanceof CasinoScreen s && s.game().key.equals(p.game())) s.onUpdate(p.data());
        }));
    }

    private static void open(MinecraftClient mc, CasinoPayloads.Open p) {
        if (p.game().equals("scratch")) {
            mc.setScreen(new ScratchScreen(p.data()));
            return;
        }
        CasinoGame g = CasinoGame.byKey(p.game());
        if (g == null) return;
        mc.setScreen(switch (g) {
            case SLOTS, VIP_SLOTS -> new SlotScreen(g, p.pos(), p.data());
            case ROULETTE -> new RouletteTableScreen(p.pos(), p.data());
            case BLACKJACK -> new BlackjackScreen(p.pos(), p.data());
            case WHEEL -> new WheelScreen(p.pos(), p.data());
        });
    }
}

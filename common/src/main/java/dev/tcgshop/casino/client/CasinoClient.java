package dev.tcgshop.casino.client;

import dev.masterball.api.platform.Platform;
import dev.masterball.api.platform.ClientPlatform;
import dev.tcgshop.casino.CasinoBlocks;
import dev.tcgshop.casino.CasinoGame;
import dev.tcgshop.casino.CasinoPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;

/** Client side of the casino: screens for the Open / Update payloads, the machine renderer and render layers. */
public final class CasinoClient {
    private CasinoClient() {}

    public static void init() {
        ClientPlatform.INSTANCE.blockEntityRenderer(CasinoBlocks.MACHINE_BE, CasinoRenderer::new);
        ClientPlatform.INSTANCE.blockRenderType(RenderType.cutout(), CasinoBlocks.MACHINES);
        ClientPlatform.INSTANCE.receive(CasinoPayloads.Open.ID, p -> Minecraft.getInstance().execute(() -> open(Minecraft.getInstance(), p)));
        ClientPlatform.INSTANCE.receive(CasinoPayloads.Update.ID, p -> Minecraft.getInstance().execute(() -> {
            if (Minecraft.getInstance().screen instanceof CasinoScreen s && s.game().key.equals(p.game())) s.onUpdate(p.data());
        }));
    }

    private static void open(Minecraft mc, CasinoPayloads.Open p) {
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
            case DICE -> new DiceScreen(p.pos(), p.data());
            case COIN -> new CoinScreen(p.pos(), p.data());
        });
    }
}

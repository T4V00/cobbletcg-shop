package dev.tcgshop.client;

import dev.tcgshop.TcgShopConfig;
import dev.tcgshop.card.CardInfo;
import dev.tcgshop.card.Rarity;
import dev.tcgshop.client.binder.ClientCollection;
import dev.tcgshop.client.binder.GeneralBinderScreen;
import dev.tcgshop.client.binder.PersonalBinderScreen;
import dev.tcgshop.client.screen.PackOpeningScreen;
import dev.tcgshop.client.screen.PackSelectScreen;
import dev.tcgshop.client.screen.ScalperOfferScreen;
import dev.tcgshop.entity.TcgEntities;
import dev.tcgshop.network.*;
import dev.tcgshop.pricing.PriceService;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import org.lwjgl.glfw.GLFW;

public class TcgShopClient implements ClientModInitializer {
    /** Abre el Binder General (si lo llevas en el inventario o equipado en un slot de Accessories). */
    public static final KeyBinding OPEN_BINDER = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.tcgshop.open_binder", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G, "key.categories.tcgshop"));

    @Override
    public void onInitializeClient() {
        dev.tcgshop.client.ClientSettings.load();
        EntityRendererRegistry.register(TcgEntities.SCALPER, dev.tcgshop.client.render.TcgNpcRenderer::new);
        dev.tcgshop.client.ShopClient.init();
        ClientPlayNetworking.registerGlobalReceiver(dev.tcgshop.network.TradePayloads.Open.ID, (p, ctx) -> ctx.client().execute(() ->
                ctx.client().setScreen(new dev.tcgshop.client.screen.TradeScreen(p.partner()))));
        ClientPlayNetworking.registerGlobalReceiver(dev.tcgshop.network.TradePayloads.State.ID, (p, ctx) -> ctx.client().execute(() -> {
            if (ctx.client().currentScreen instanceof dev.tcgshop.client.screen.TradeScreen s) s.onState(p);
        }));
        ClientPlayNetworking.registerGlobalReceiver(dev.tcgshop.network.TradePayloads.Done.ID, (p, ctx) -> ctx.client().execute(() -> {
            if (ctx.client().currentScreen instanceof dev.tcgshop.client.screen.TradeScreen s) s.onDone(p);
            else if (!p.success() && ctx.client().player != null) {
                String[] k = p.message().split("\\|", 2);
                ctx.client().player.sendMessage(k.length > 1 ? net.minecraft.text.Text.translatable(k[0], k[1]) : net.minecraft.text.Text.translatable(k[0]), true);
            }
        }));
        ClientPlayNetworking.registerGlobalReceiver(RouletteOpenPayload.ID, (p, ctx) -> ctx.client().execute(() ->
                ctx.client().setScreen(new dev.tcgshop.client.screen.RouletteScreen(p.pos(), p.balance()))));
        ClientPlayNetworking.registerGlobalReceiver(RouletteResultPayload.ID, (p, ctx) -> ctx.client().execute(() -> {
            if (ctx.client().currentScreen instanceof dev.tcgshop.client.screen.RouletteScreen s) s.onResult(p);
        }));
        ClientPlayNetworking.registerGlobalReceiver(PtcgPayloads.View.ID, (p, ctx) -> ctx.client().execute(() -> {
            if (ctx.client().currentScreen instanceof dev.tcgshop.client.screen.PtcgDuelScreen s) s.update(p.json());
            else if (p.open()) ctx.client().setScreen(new dev.tcgshop.client.screen.PtcgDuelScreen(p.json()));
        }));
        ClientPlayNetworking.registerGlobalReceiver(PtcgPayloads.League.ID, (p, ctx) -> ctx.client().execute(() ->
                dev.tcgshop.client.ClientPtcg.applyLeague(p.json())));
        ClientPlayNetworking.registerGlobalReceiver(GradePayloads.Sync.ID, (p, ctx) -> ctx.client().execute(() ->
                dev.tcgshop.client.ClientGrading.apply(p)));
        ClientPlayNetworking.registerGlobalReceiver(GradePayloads.Reveal.ID, (p, ctx) -> ctx.client().execute(() ->
                ctx.client().setScreen(new dev.tcgshop.client.screen.GradeRevealScreen(p.cards(), p.valueBefore(), p.valueAfter()))));
        ClientPlayNetworking.registerGlobalReceiver(NpcSpeechPayload.ID, (p, ctx) -> ctx.client().execute(() ->
                dev.tcgshop.client.ClientSpeech.say(p.entityId(), p.text())));
        ClientTickEvents.END_CLIENT_TICK.register(dev.tcgshop.client.ClientSpeech::tick);
        ClientPlayNetworking.registerGlobalReceiver(BoxOpenPayload.ID, (p, ctx) -> ctx.client().execute(() ->
                ctx.client().setScreen(new dev.tcgshop.client.screen.BoxOpenScreen(p.set(), p.packs()))));
        ClientPlayNetworking.registerGlobalReceiver(dev.tcgshop.network.UiPayloads.Open.ID, (p, ctx) -> ctx.client().execute(() -> {
            switch (p.kind()) {
                case "slot" -> ctx.client().setScreen(new dev.tcgshop.client.screen.SlotEditScreen(p.data()));
                case "beggar" -> ctx.client().setScreen(new dev.tcgshop.client.screen.BeggarScreen(p.data()));
                case "black_market" -> ctx.client().setScreen(new dev.tcgshop.client.screen.BlackMarketScreen(p.data()));
                case "binder_market" -> ctx.client().setScreen(new dev.tcgshop.client.screen.BinderMarketScreen(p.data()));
                case "binder_market_update" -> {
                    if (ctx.client().currentScreen instanceof dev.tcgshop.client.screen.BinderMarketScreen s) s.update(p.data());
                }
                case "friends" -> dev.tcgshop.client.ClientSocial.friends = p.data();
                case "displays" -> dev.tcgshop.client.ClientSocial.displays = p.data();
                default -> {}
            }
        }));
        ClientPlayNetworking.registerGlobalReceiver(DealOpenPayload.ID, (p, ctx) -> ctx.client().execute(() -> {
            // la misma conversación: se actualiza en sitio (regateo); si terminó y no está abierta, nada
            if (ctx.client().currentScreen instanceof dev.tcgshop.client.screen.DealScreen ds && ds.entityId() == p.entityId()) ds.update(p);
            else if (p.mode() != DealOpenPayload.CLOSED) ctx.client().setScreen(new dev.tcgshop.client.screen.DealScreen(p));
        }));
        ClientPlayNetworking.registerGlobalReceiver(ShopZonesPayload.ID, (p, ctx) -> ctx.client().execute(() -> dev.tcgshop.client.ShopClient.apply(p.data())));
        ClientPlayNetworking.registerGlobalReceiver(ShopViewPayload.ID, (p, ctx) -> ctx.client().execute(() -> {
            if (ctx.client().currentScreen instanceof dev.tcgshop.client.screen.ShopScreen s && s.pos().equals(p.pos())) s.update(p);
            else ctx.client().setScreen(new dev.tcgshop.client.screen.ShopScreen(p));
        }));
        ClientPlayNetworking.registerGlobalReceiver(dev.tcgshop.network.MarketPayloads.TraderOpen.ID, (p, ctx) -> ctx.client().execute(() -> {
            if (ctx.client().currentScreen instanceof dev.tcgshop.client.screen.TraderScreen s && s.entity() == p.entity()) s.update(p);
            else ctx.client().setScreen(new dev.tcgshop.client.screen.TraderScreen(p));
        }));
        ClientPlayNetworking.registerGlobalReceiver(StoreOpenPayload.ID, (p, ctx) -> ctx.client().execute(() -> {
            if (p.source() == -1) ctx.client().setScreen(new dev.tcgshop.client.screen.PhoneScreen(p.goods(), p.prices()));
            else ctx.client().setScreen(new dev.tcgshop.client.screen.StoreScreen(p.source(), p.goods(), p.prices()));
        }));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (OPEN_BINDER.wasPressed()) {
                if (client.currentScreen == null && client.player != null)
                    ClientCollection.action(BinderActionPayload.OPEN_GENERAL, "", 0);
            }
        });

        // Clic derecho con un sobre: preguntar (y permitir abrir varios) en vez de abrirlo al instante.
        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack stack = player.getStackInHand(hand);
            if (!world.isClient || !TcgShopConfig.INSTANCE.packOpeningAnimation || !CardInfo.isPack(stack)) return TypedActionResult.pass(stack);
            if (!ClientPlayNetworking.canSend(OpenGroupPayload.ID)) return TypedActionResult.pass(stack); // servidor sin el add-on
            MinecraftClient mc = MinecraftClient.getInstance();
            mc.execute(() -> mc.setScreen(new PackSelectScreen(CardInfo.id(stack))));
            return TypedActionResult.fail(stack); // FAIL en el cliente = no se envía el uso al servidor
        });

        ClientPlayNetworking.registerGlobalReceiver(GroupReadyPayload.ID, (p, ctx) -> ctx.client().execute(() -> {
            if (ctx.client().currentScreen instanceof PackOpeningScreen s) s.onGroupReady(p);
        }));
        ClientPlayNetworking.registerGlobalReceiver(GroupRevealPayload.ID, (p, ctx) -> ctx.client().execute(() -> {
            if (ctx.client().currentScreen instanceof PackOpeningScreen s) s.onGroupReveal(p);
        }));

        ClientPlayNetworking.registerGlobalReceiver(TitlesPayload.ID, (p, ctx) -> ctx.client().execute(() ->
                ClientCollection.playerTitles = new java.util.HashMap<>(p.titles())));
        ClientPlayNetworking.registerGlobalReceiver(CollectionSyncPayload.ID, (p, ctx) -> ctx.client().execute(() -> ClientCollection.apply(p)));
        ClientPlayNetworking.registerGlobalReceiver(OpenBinderPayload.ID, (p, ctx) -> ctx.client().execute(() -> {
            if (p.kind() == 0) ctx.client().setScreen(new GeneralBinderScreen());
            else ctx.client().setScreen(new PersonalBinderScreen(Hand.values()[Math.min(1, p.hand())], p.unlocked()));
        }));
        ClientPlayNetworking.registerGlobalReceiver(FactPayload.ID, (p, ctx) -> ctx.client().execute(() ->
                SystemToast.add(ctx.client().getToastManager(), SystemToast.Type.PERIODIC_NOTIFICATION, p.title(), p.body())));
        ClientPlayNetworking.registerGlobalReceiver(ScalperOfferPayload.ID, (p, ctx) -> ctx.client().execute(() ->
                ctx.client().setScreen(new ScalperOfferScreen(p.entityId(), p.pack(), p.price(), p.market()))));

        ClientPlayNetworking.registerGlobalReceiver(PriceSyncPayload.ID, (p, ctx) -> ctx.client().execute(() -> {
            PriceService.applySync(p.cardsUsd(), p.sealedUsd(), p.rarityMedianUsd(),
                    p.market().cdPerUsd(), p.market().seed(), p.market().volatility(), p.market().day());
            if (!ctx.client().isIntegratedServerRunning()) dev.tcgshop.EconomyConfig.applyJson(p.economy());
        }));
        ClientPlayNetworking.registerGlobalReceiver(MarketDayPayload.ID, (p, ctx) -> ctx.client().execute(() -> PriceService.setMarketDay(p.day())));

        ItemTooltipCallback.EVENT.register((stack, tooltipContext, type, lines) -> {
            if (!CardInfo.isCard(stack)) return;
            int grade = dev.tcgshop.grading.Grading.grade(stack);
            if (grade > 0) {
                lines.add(1, dev.tcgshop.grading.Grading.tooltip(grade));
                lines.add(2, Text.translatable("tcgshop.grade.cert", String.format(java.util.Locale.ROOT, "%06d",
                        dev.tcgshop.grading.Grading.cert(stack))).formatted(Formatting.DARK_GRAY));
            }
            if (!TcgShopConfig.INSTANCE.showPriceInTooltip) return;
            Rarity r = Rarity.of(stack);
            lines.add(r.displayName().copy().withColor(r.color & 0xFFFFFF));
            lines.add(Text.translatable("tcgshop.price", Text.literal(PriceService.format(PriceService.priceCd(stack))).formatted(Formatting.GOLD))
                    .formatted(Formatting.GRAY));
        });
    }
}

package dev.tcgshop.client;

import dev.masterball.api.platform.Platform;
import dev.masterball.api.platform.ClientPlatform;
import dev.masterball.api.platform.event.PlayerEvents;
import dev.masterball.api.platform.event.ClientEvents;
import dev.tcgshop.TcgShopConfig;
import com.mojang.blaze3d.platform.InputConstants;
import dev.masterball.api.card.CardInfo;
import dev.masterball.api.card.Rarity;
import dev.tcgshop.client.binder.ClientCollection;
import dev.tcgshop.client.binder.GeneralBinderScreen;
import dev.tcgshop.client.binder.PersonalBinderScreen;
import dev.tcgshop.client.screen.PackOpeningScreen;
import dev.tcgshop.client.screen.PackSelectScreen;
import dev.tcgshop.client.screen.ScalperOfferScreen;
import dev.tcgshop.entity.TcgEntities;
import dev.tcgshop.network.*;
import dev.tcgshop.pricing.PriceService;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

public class TcgShopClient {
    /** Opens the General Binder (if you carry it in your inventory or have it equipped in an Accessories slot). */
    public static final KeyMapping OPEN_BINDER = ClientPlatform.INSTANCE.registerKey(new KeyMapping(
            "key.tcgshop.open_binder", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, "key.categories.tcgshop"));

    public static void init() {
        dev.tcgshop.client.ClientSettings.load();
        dev.tcgshop.client.options.ModOptions.init(); // 1.0: options screen (key O), card art option and its first-play notice
        ClientPlatform.INSTANCE.entityRenderer(TcgEntities.SCALPER, dev.tcgshop.client.render.TcgNpcRenderer::new);
        // 1.0 onboarding one-time visitors
        ClientPlatform.INSTANCE.entityRenderer(TcgEntities.UNIQUE_COLLECTOR, dev.tcgshop.client.render.TcgNpcRenderer::new);
        ClientPlatform.INSTANCE.entityRenderer(TcgEntities.RIVAL, dev.tcgshop.client.render.TcgNpcRenderer::new);
        ClientPlatform.INSTANCE.entityRenderer(TcgEntities.WANDERING_DUELIST, dev.tcgshop.client.render.TcgNpcRenderer::new); // 1.0 Wandering Duelist
        ClientPlatform.INSTANCE.entityRenderer(TcgEntities.MAYOR, dev.tcgshop.client.render.TcgNpcRenderer::new); // 1.1 Mayor (taxes)
        ClientPlatform.INSTANCE.entityRenderer(TcgEntities.ENGINEER, dev.tcgshop.client.render.TcgNpcRenderer::new); // 1.1 CREATive Engineer
        dev.tcgshop.client.ShopClient.init();
        dev.tcgshop.client.DecorTooltip.register(); // 1.1: decoration points / % in decor items' tooltips
        // 1.0 beta 3: customers and employees get armor rendering and the unique customers' rainbow names (replaces ShopClient's registration)
        ClientPlatform.INSTANCE.entityRenderer(TcgEntities.CUSTOMER, dev.tcgshop.client.render.CustomerNpcRenderer::new);
        ClientPlatform.INSTANCE.entityRenderer(TcgEntities.EMPLOYEE, dev.tcgshop.client.render.CustomerNpcRenderer::new);
        dev.tcgshop.client.render.RgbLights.initClient(); // 1.0 RGB displays (translucent smoked glass)
        dev.tcgshop.client.render.NpcSkins.initClient(); // 1.0 beta 3: re-probe NPC skin sets on resource reload
        dev.tcgshop.client.render.ToolItemRenderer.register(); // 1.0 beta 3: 3D tool items + per-tier 3D phone
        dev.tcgshop.casino.client.CasinoClient.init(); // 1.0 beta 3 casino: screens, renderer, payload receivers
        dev.tcgshop.events.incident.IncidentModule.initClient(); // 0.9: random events (client side of the incident module)
        dev.tcgshop.world.WorldLootClient.init(); // 1.0 trash can (cutout layer)
        dev.tcgshop.client.phone.PhoneClient.register();
        dev.tcgshop.client.DiscountsClient.register(); // 1.0: store sale event (Discounts.percent on the client)
        ClientPlatform.INSTANCE.receive(dev.tcgshop.network.TradePayloads.Open.ID, p -> Minecraft.getInstance().execute(() ->
                Minecraft.getInstance().setScreen(new dev.tcgshop.client.screen.TradeScreen(p.partner()))));
        ClientPlatform.INSTANCE.receive(dev.tcgshop.network.TradePayloads.State.ID, p -> Minecraft.getInstance().execute(() -> {
            if (Minecraft.getInstance().screen instanceof dev.tcgshop.client.screen.TradeScreen s) s.onState(p);
        }));
        ClientPlatform.INSTANCE.receive(dev.tcgshop.network.TradePayloads.Done.ID, p -> Minecraft.getInstance().execute(() -> {
            if (Minecraft.getInstance().screen instanceof dev.tcgshop.client.screen.TradeScreen s) s.onDone(p);
            else if (!p.success() && Minecraft.getInstance().player != null) {
                String[] k = p.message().split("\\|", 2);
                Minecraft.getInstance().player.displayClientMessage(k.length > 1 ? net.minecraft.network.chat.Component.translatable(k[0], k[1]) : net.minecraft.network.chat.Component.translatable(k[0]), true);
            }
        }));
        ClientPlatform.INSTANCE.receive(RouletteOpenPayload.ID, p -> Minecraft.getInstance().execute(() ->
                Minecraft.getInstance().setScreen(new dev.tcgshop.client.screen.RouletteScreen(p.pos(), p.balance()))));
        ClientPlatform.INSTANCE.receive(RouletteResultPayload.ID, p -> Minecraft.getInstance().execute(() -> {
            if (Minecraft.getInstance().screen instanceof dev.tcgshop.client.screen.RouletteScreen s) s.onResult(p);
        }));
        ClientPlatform.INSTANCE.receive(PtcgPayloads.View.ID, p -> Minecraft.getInstance().execute(() -> {
            if (Minecraft.getInstance().screen instanceof dev.tcgshop.client.screen.PtcgDuelScreen s) s.update(p.json());
            else if (p.open()) Minecraft.getInstance().setScreen(new dev.tcgshop.client.screen.PtcgDuelScreen(p.json()));
        }));
        ClientPlatform.INSTANCE.receive(PtcgPayloads.League.ID, p -> Minecraft.getInstance().execute(() ->
                dev.tcgshop.client.ClientPtcg.applyLeague(p.json())));
        ClientPlatform.INSTANCE.receive(GradePayloads.Sync.ID, p -> Minecraft.getInstance().execute(() ->
                dev.tcgshop.client.ClientGrading.apply(p)));
        ClientPlatform.INSTANCE.receive(GradePayloads.Reveal.ID, p -> Minecraft.getInstance().execute(() ->
                Minecraft.getInstance().setScreen(new dev.tcgshop.client.screen.GradeRevealScreen(p.cards(), p.valueBefore(), p.valueAfter()))));
        ClientPlatform.INSTANCE.receive(NpcSpeechPayload.ID, p -> Minecraft.getInstance().execute(() ->
                dev.tcgshop.client.ClientSpeech.say(p.entityId(), p.text())));
        ClientPlatform.INSTANCE.receive(dev.tcgshop.network.TutorialPayload.ID, p -> Minecraft.getInstance().execute(() ->
                dev.tcgshop.client.ClientSpeech.sayScripted(p.entityId(), p.text(), p.duration())));
        ClientEvents.TICK_END.register(dev.tcgshop.client.ClientSpeech::tick);
        ClientPlatform.INSTANCE.receive(dev.tcgshop.network.FurniturePayloads.Open.ID, p -> Minecraft.getInstance().execute(() ->
                dev.tcgshop.client.screen.FurnitureApp.receive(p)));
        ClientPlatform.INSTANCE.receive(BoxOpenPayload.ID, p -> Minecraft.getInstance().execute(() ->
                Minecraft.getInstance().setScreen(new dev.tcgshop.client.screen.BoxOpenScreen(p))));
        ClientPlatform.INSTANCE.receive(dev.tcgshop.network.UiPayloads.Open.ID, p -> Minecraft.getInstance().execute(() -> {
            switch (p.kind()) {
                case "slot" -> Minecraft.getInstance().setScreen(new dev.tcgshop.client.screen.SlotEditScreen(p.data()));
                case "beggar" -> Minecraft.getInstance().setScreen(new dev.tcgshop.client.screen.BeggarScreen(p.data()));
                case "unique_collector" -> Minecraft.getInstance().setScreen(new dev.tcgshop.client.screen.UniqueCollectorScreen(p.data()));
                case "duelist" -> Minecraft.getInstance().setScreen(new dev.tcgshop.client.screen.DuelistStakesScreen(p.data())); // 1.0 Wandering Duelist
                case "shop_status" -> Minecraft.getInstance().setScreen(new dev.tcgshop.client.screen.ShopStatusScreen(p.data()));
                case "register" -> { // 1.1: cash register window (withdraw / deposit)
                    if (p.data().hasUUID("zone"))
                        Minecraft.getInstance().setScreen(new dev.tcgshop.client.screen.RegisterScreen(p.data().getUUID("zone"), false, null));
                }
                case "black_market" -> Minecraft.getInstance().setScreen(new dev.tcgshop.client.screen.BlackMarketScreen(p.data()));
                case "mayor" -> Minecraft.getInstance().setScreen(new dev.tcgshop.client.screen.MayorScreen(p.data())); // 1.1 taxes
                case "engineer" -> Minecraft.getInstance().setScreen(new dev.tcgshop.client.screen.EngineerScreen(p.data())); // 1.1 CREATive Engineer
                case "binder_market" -> Minecraft.getInstance().setScreen(new dev.tcgshop.client.screen.BinderMarketScreen(p.data()));
                case "binder_market_update" -> {
                    if (Minecraft.getInstance().screen instanceof dev.tcgshop.client.screen.BinderMarketScreen s) s.update(p.data());
                }
                case "friends" -> dev.tcgshop.client.ClientSocial.friends = p.data();
                case "displays" -> dev.tcgshop.client.ClientSocial.displays = p.data();
                default -> {}
            }
        }));
        ClientPlatform.INSTANCE.receive(DealOpenPayload.ID, p -> Minecraft.getInstance().execute(() -> {
            // same conversation: update in place (haggling); if it ended and is not open, do nothing
            if (Minecraft.getInstance().screen instanceof dev.tcgshop.client.screen.DealScreen ds && ds.entityId() == p.entityId()) ds.update(p);
            else if (p.mode() != DealOpenPayload.CLOSED) Minecraft.getInstance().setScreen(new dev.tcgshop.client.screen.DealScreen(p));
        }));
        ClientPlatform.INSTANCE.receive(ShopZonesPayload.ID, p -> Minecraft.getInstance().execute(() -> dev.tcgshop.client.ShopClient.apply(p.data())));
        ClientPlatform.INSTANCE.receive(ShopViewPayload.ID, p -> Minecraft.getInstance().execute(() -> {
            if (Minecraft.getInstance().screen instanceof dev.tcgshop.client.screen.ShopScreen s && s.pos().equals(p.pos())) s.update(p);
            else Minecraft.getInstance().setScreen(new dev.tcgshop.client.screen.ShopScreen(p));
        }));
        ClientPlatform.INSTANCE.receive(dev.tcgshop.network.MarketPayloads.TraderOpen.ID, p -> Minecraft.getInstance().execute(() -> {
            if (Minecraft.getInstance().screen instanceof dev.tcgshop.client.screen.TraderScreen s && s.entity() == p.entity()) s.update(p);
            else Minecraft.getInstance().setScreen(new dev.tcgshop.client.screen.TraderScreen(p));
        }));
        ClientPlatform.INSTANCE.receive(StoreOpenPayload.ID, p -> Minecraft.getInstance().execute(() -> {
            if (p.source() == -1) dev.tcgshop.client.screen.PhoneScreen.openOrRefresh(p.goods(), p.prices());
            else Minecraft.getInstance().setScreen(new dev.tcgshop.client.screen.StoreScreen(p.source(), p.goods(), p.prices()));
        }));

        ClientEvents.TICK_END.register(client -> {
            while (OPEN_BINDER.consumeClick()) {
                if (client.screen == null && client.player != null)
                    ClientCollection.action(BinderActionPayload.OPEN_GENERAL, "", 0);
            }
        });

        // Right-click with a pack: ask (and allow opening several) instead of opening it instantly.
        PlayerEvents.USE_ITEM.register((player, world, hand) -> {
            ItemStack stack = player.getItemInHand(hand);
            if (!world.isClientSide || !TcgShopConfig.INSTANCE.packOpeningAnimation || !CardInfo.isPack(stack)) return InteractionResultHolder.pass(stack);
            if (!ClientPlatform.INSTANCE.canSend(OpenGroupPayload.ID)) return InteractionResultHolder.pass(stack); // server without the add-on
            Minecraft mc = Minecraft.getInstance();
            mc.execute(() -> mc.setScreen(new PackSelectScreen(CardInfo.id(stack))));
            return InteractionResultHolder.fail(stack); // FAIL on the client = the use is not sent to the server
        });

        ClientPlatform.INSTANCE.receive(GroupReadyPayload.ID, p -> Minecraft.getInstance().execute(() -> {
            if (Minecraft.getInstance().screen instanceof PackOpeningScreen s) s.onGroupReady(p);
        }));
        ClientPlatform.INSTANCE.receive(GroupRevealPayload.ID, p -> Minecraft.getInstance().execute(() -> {
            if (Minecraft.getInstance().screen instanceof PackOpeningScreen s) s.onGroupReveal(p);
        }));

        ClientPlatform.INSTANCE.receive(TitlesPayload.ID, p -> Minecraft.getInstance().execute(() ->
                ClientCollection.playerTitles = new java.util.HashMap<>(p.titles())));
        ClientPlatform.INSTANCE.receive(CollectionSyncPayload.ID, p -> Minecraft.getInstance().execute(() -> ClientCollection.apply(p)));
        ClientPlatform.INSTANCE.receive(OpenBinderPayload.ID, p -> Minecraft.getInstance().execute(() -> {
            if (p.kind() == 0) Minecraft.getInstance().setScreen(new GeneralBinderScreen());
            else Minecraft.getInstance().setScreen(new PersonalBinderScreen(InteractionHand.values()[Math.min(1, p.hand())], p.unlocked()));
        }));
        ClientPlatform.INSTANCE.receive(FactPayload.ID, p -> Minecraft.getInstance().execute(() ->
                SystemToast.add(Minecraft.getInstance().getToasts(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION, p.title(), p.body())));
        ClientPlatform.INSTANCE.receive(ScalperOfferPayload.ID, p -> Minecraft.getInstance().execute(() ->
                Minecraft.getInstance().setScreen(new ScalperOfferScreen(p.entityId(), p.pack(), p.price(), p.market()))));

        ClientPlatform.INSTANCE.receive(PriceSyncPayload.ID, p -> Minecraft.getInstance().execute(() -> {
            PriceService.applySync(p.cardsUsd(), p.sealedUsd(), p.rarityMedianUsd(), p.gradedUsd(),
                    p.market().cdPerUsd(), p.market().seed(), p.market().volatility(), p.market().day());
            if (!Minecraft.getInstance().hasSingleplayerServer()) dev.tcgshop.EconomyConfig.applyJson(p.economy());
        }));
        ClientPlatform.INSTANCE.receive(MarketDayPayload.ID, p -> Minecraft.getInstance().execute(() -> PriceService.setMarketDay(p.day())));

        ClientEvents.TOOLTIP.register((stack, tooltipContext, type, lines) -> {
            if (!CardInfo.isCard(stack)) return;
            if (dev.tcgshop.entity.CollectorTheft.isStolen(stack)) // 1.0 beta 3: card stolen from a collector who is still after it
                lines.add(Math.min(1, lines.size()), Component.translatable("tcgshop.collector_theft.tooltip").withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
            int grade = dev.tcgshop.grading.Grading.grade(stack);
            if (grade > 0) {
                lines.add(1, dev.tcgshop.grading.Grading.tooltip(grade));
                lines.add(2, Component.translatable("tcgshop.grade.cert", String.format(java.util.Locale.ROOT, "%06d",
                        dev.tcgshop.grading.Grading.cert(stack))).withStyle(ChatFormatting.DARK_GRAY));
            }
            // rarity, variant and foil are added by MasterBall Traders API
            if (!TcgShopConfig.INSTANCE.showPriceInTooltip) return;
            lines.add(Component.translatable("tcgshop.price", Component.literal(PriceService.format(PriceService.priceCd(stack))).withStyle(ChatFormatting.GOLD))
                    .withStyle(ChatFormatting.GRAY));
        });
    }
}

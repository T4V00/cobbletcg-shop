package dev.tcgshop;

import com.mojang.brigadier.CommandDispatcher;
import dev.masterball.api.card.CardInfo;
import dev.masterball.api.card.Rarity;
import dev.tcgshop.advancement.CardAdvancements;
import dev.tcgshop.binder.BinderServer;
import dev.tcgshop.collection.CollectionService;
import dev.tcgshop.entity.ScalperEntity;
import dev.tcgshop.entity.TcgEntities;
import dev.tcgshop.item.TcgItems;
import dev.tcgshop.network.*;
import dev.tcgshop.pack.PackOpener;
import dev.tcgshop.pricing.PriceService;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.resource.ResourceType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TcgShop implements ModInitializer {
    public static final String MOD_ID = "tcgshop";
    public static final Logger LOGGER = LoggerFactory.getLogger("MasterBall Traders Co.");

    @Override
    public void onInitialize() {
        TcgShopConfig.load();
        TcgSounds.init();

        TcgItems.init();
        TcgEntities.init();
        dev.tcgshop.shop.TcgBlocks.init();
        dev.tcgshop.luxury.LuxuryBlocks.register();
        dev.tcgshop.shop.ShopServer.init();
        dev.tcgshop.shop.VanillaPrices.load();
        EconomyConfig.load();
        dev.tcgshop.shop.ShopProtection.register();
        CollectionService.init();

        // red: servidor -> cliente
        PayloadTypeRegistry.playS2C().register(PriceSyncPayload.ID, PriceSyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(MarketDayPayload.ID, MarketDayPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(GroupReadyPayload.ID, GroupReadyPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(GroupRevealPayload.ID, GroupRevealPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(CollectionSyncPayload.ID, CollectionSyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(OpenBinderPayload.ID, OpenBinderPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(FactPayload.ID, FactPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ScalperOfferPayload.ID, ScalperOfferPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(TitlesPayload.ID, TitlesPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ShopViewPayload.ID, ShopViewPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ShopZonesPayload.ID, ShopZonesPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(StoreOpenPayload.ID, StoreOpenPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ShopActionPayload.ID, ShopActionPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(RouletteOpenPayload.ID, RouletteOpenPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(RouletteResultPayload.ID, RouletteResultPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(DealOpenPayload.ID, DealOpenPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(BoxOpenPayload.ID, BoxOpenPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(NpcSpeechPayload.ID, NpcSpeechPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(dev.tcgshop.network.TutorialPayload.ID, dev.tcgshop.network.TutorialPayload.CODEC);
        dev.tcgshop.onboarding.MeowthTalk.register();
        // CobbleGrade
        dev.tcgshop.grading.Grading.init();
        dev.masterball.api.card.PrintVariant.init();
        PayloadTypeRegistry.playC2S().register(GradePayloads.Action.ID, GradePayloads.Action.CODEC);
        PayloadTypeRegistry.playS2C().register(GradePayloads.Sync.ID, GradePayloads.Sync.CODEC);
        PayloadTypeRegistry.playS2C().register(GradePayloads.Reveal.ID, GradePayloads.Reveal.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(GradePayloads.Action.ID, (p, ctx) -> ctx.server().execute(() ->
                dev.tcgshop.grading.GradingService.handle(ctx.player(), p)));
        ServerTickEvents.END_SERVER_TICK.register(dev.tcgshop.grading.GradingService::tick);
        PayloadTypeRegistry.playC2S().register(RouletteSpinPayload.ID, RouletteSpinPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(DealAnswerPayload.ID, DealAnswerPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(dev.tcgshop.network.UiPayloads.Act.ID, dev.tcgshop.network.UiPayloads.Act.CODEC);
        PayloadTypeRegistry.playS2C().register(dev.tcgshop.network.UiPayloads.Open.ID, dev.tcgshop.network.UiPayloads.Open.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(dev.tcgshop.network.UiPayloads.Act.ID, (p, ctx) -> ctx.server().execute(() -> {
            ServerPlayerEntity pl = ctx.player();
            switch (p.kind()) {
                case "slot" -> dev.tcgshop.shop.ShopServer.slotAction(pl, p.data());
                case "binder_open" -> dev.tcgshop.shop.SocialServer.openBinderMarket(pl, false);
                case "shop_collect" -> {
                    if (!p.data().containsUuid("zone")) break;
                    java.util.UUID zid = p.data().getUuid("zone");
                    for (var w : pl.getServer().getWorlds()) {
                        var z = dev.tcgshop.shop.ShopZones.get(w).byId(zid);
                        if (z != null && z.owner.equals(pl.getUuid())) {
                            dev.tcgshop.shop.ShopServer.collect(pl, z, p.data().getBoolean("keep") ? dev.tcgshop.shop.ShopServer.dailyWages(z) * 3 : 0);
                            break;
                        }
                    }
                }
                case "binder" -> dev.tcgshop.shop.SocialServer.binderAction(pl, p.data());
                case "friends" -> dev.tcgshop.shop.SocialServer.friendsAction(pl, p.data());
                case "displays" -> {
                    // 0.9: gestión remota de vitrinas = app Vitrinas del teléfono (con nivel mínimo)
                    if (!dev.tcgshop.phone.PhoneUpgrades.requireApp(pl, "displays")) break;
                    if (p.data().getString("a").equals("list")) dev.tcgshop.shop.SocialServer.sendDisplays(pl);
                    else dev.tcgshop.shop.SocialServer.displaysAction(pl, p.data());
                }
                case "beggar" -> {
                    if (pl.getWorld().getEntityById(p.data().getInt("id")) instanceof dev.tcgshop.entity.BeggarEntity b) b.answer(pl, p.data().getString("action"));
                }
                case "black_market" -> {
                    if (pl.getWorld().getEntityById(p.data().getInt("id")) instanceof dev.tcgshop.entity.BlackMarketEntity m && p.data().containsUuid("mon"))
                        m.trade(pl, p.data().getUuid("mon"));
                }
                default -> {}
            }
        }));
        PayloadTypeRegistry.playC2S().register(StaffPayload.ID, StaffPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(RouletteSpinPayload.ID, (p, ctx) -> ctx.server().execute(() -> dev.tcgshop.shop.Roulette.spin(p, ctx.player())));
        ServerPlayNetworking.registerGlobalReceiver(StaffPayload.ID, (p, ctx) -> ctx.server().execute(() ->
                dev.tcgshop.shop.ShopServer.manageStaff(ctx.player(), p.zone(), p.role(), p.hire())));
        ServerPlayNetworking.registerGlobalReceiver(DealAnswerPayload.ID, (p, ctx) -> ctx.server().execute(() -> {
            if (ctx.player().getWorld().getEntityById(p.entityId()) instanceof dev.tcgshop.entity.CustomerEntity c && c.squaredDistanceTo(ctx.player()) < 12 * 12)
                c.respond(ctx.player(), p.action(), p.counter());
        }));
        dev.tcgshop.shop.ShopLife.register();
        dev.tcgshop.compat.CobblemonBattles.register();
        dev.tcgshop.shop.PackLock.register();
        dev.tcgshop.shop.ChestLoot.register();
        dev.tcgshop.compat.NpcPokemonDuel.register();
        // modo competitivo TCG
        dev.tcgshop.ptcg.PtcgDuels.register();
        PayloadTypeRegistry.playS2C().register(PtcgPayloads.View.ID, PtcgPayloads.View.CODEC);
        PayloadTypeRegistry.playC2S().register(PtcgPayloads.Action.ID, PtcgPayloads.Action.CODEC);
        PayloadTypeRegistry.playS2C().register(PtcgPayloads.League.ID, PtcgPayloads.League.CODEC);
        PayloadTypeRegistry.playC2S().register(PtcgPayloads.LeagueAction.ID, PtcgPayloads.LeagueAction.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(PtcgPayloads.Action.ID, (p, ctx) -> ctx.server().execute(() ->
                dev.tcgshop.ptcg.PtcgDuels.action(ctx.player(), p.json())));
        ServerPlayNetworking.registerGlobalReceiver(PtcgPayloads.LeagueAction.ID, (p, ctx) -> ctx.server().execute(() -> {
            // 0.9: lo exclusivo de la app Liga (clasificatoria, cola, IA alta) exige su nivel de teléfono
            if (dev.tcgshop.phone.PhoneUpgrades.leagueActionAllowed(ctx.player(), p.json()))
                dev.tcgshop.ptcg.PtcgDuels.league(ctx.player(), p.json());
        }));
        // intercambio de cartas entre jugadores
        PayloadTypeRegistry.playC2S().register(dev.tcgshop.network.TradePayloads.Request.ID, dev.tcgshop.network.TradePayloads.Request.CODEC);
        PayloadTypeRegistry.playC2S().register(dev.tcgshop.network.TradePayloads.Select.ID, dev.tcgshop.network.TradePayloads.Select.CODEC);
        PayloadTypeRegistry.playC2S().register(dev.tcgshop.network.TradePayloads.Answer.ID, dev.tcgshop.network.TradePayloads.Answer.CODEC);
        PayloadTypeRegistry.playS2C().register(dev.tcgshop.network.TradePayloads.Open.ID, dev.tcgshop.network.TradePayloads.Open.CODEC);
        PayloadTypeRegistry.playS2C().register(dev.tcgshop.network.TradePayloads.State.ID, dev.tcgshop.network.TradePayloads.State.CODEC);
        PayloadTypeRegistry.playS2C().register(dev.tcgshop.network.TradePayloads.Done.ID, dev.tcgshop.network.TradePayloads.Done.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(dev.tcgshop.network.TradePayloads.Request.ID, (p, ctx) -> ctx.server().execute(() ->
                dev.tcgshop.trade.TradeManager.request(ctx.player(), p.target())));
        ServerPlayNetworking.registerGlobalReceiver(dev.tcgshop.network.TradePayloads.Select.ID, (p, ctx) -> ctx.server().execute(() ->
                dev.tcgshop.trade.TradeManager.select(ctx.player(), p.slot())));
        ServerPlayNetworking.registerGlobalReceiver(dev.tcgshop.network.TradePayloads.Answer.ID, (p, ctx) -> ctx.server().execute(() ->
                dev.tcgshop.trade.TradeManager.answer(ctx.player(), p.confirm())));
        ServerTickEvents.END_SERVER_TICK.register(dev.tcgshop.trade.TradeManager::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> dev.tcgshop.trade.TradeManager.disconnect(handler.getPlayer()));
        PayloadTypeRegistry.playC2S().register(StoreBuyPayload.ID, StoreBuyPayload.CODEC);
        // tienda de muebles (CobbleFurnies / peluches): Vendedor de muebles, Mayorista y teléfono
        PayloadTypeRegistry.playC2S().register(dev.tcgshop.network.FurniturePayloads.Request.ID, dev.tcgshop.network.FurniturePayloads.Request.CODEC);
        PayloadTypeRegistry.playC2S().register(dev.tcgshop.network.FurniturePayloads.Trade.ID, dev.tcgshop.network.FurniturePayloads.Trade.CODEC);
        PayloadTypeRegistry.playS2C().register(dev.tcgshop.network.FurniturePayloads.Open.ID, dev.tcgshop.network.FurniturePayloads.Open.CODEC);
        // 0.9: source -1 = app Muebles del teléfono (con nivel mínimo); el Vendedor y el Mayorista no se bloquean
        ServerPlayNetworking.registerGlobalReceiver(dev.tcgshop.network.FurniturePayloads.Request.ID, (p, ctx) -> ctx.server().execute(() -> {
            if (p.source() == -1 && !dev.tcgshop.phone.PhoneUpgrades.requireApp(ctx.player(), "furniture")) return;
            dev.tcgshop.shop.FurnitureShop.handleRequest(ctx.player(), p);
        }));
        ServerPlayNetworking.registerGlobalReceiver(dev.tcgshop.network.FurniturePayloads.Trade.ID, (p, ctx) -> ctx.server().execute(() -> {
            if (p.source() == -1 && !dev.tcgshop.phone.PhoneUpgrades.requireApp(ctx.player(), "furniture")) return;
            dev.tcgshop.shop.FurnitureShop.handleTrade(ctx.player(), p);
        }));
        // venta rápida de cartas / esmeraldas y Coleccionista ambulante
        PayloadTypeRegistry.playC2S().register(dev.tcgshop.network.MarketPayloads.Sell.ID, dev.tcgshop.network.MarketPayloads.Sell.CODEC);
        PayloadTypeRegistry.playC2S().register(dev.tcgshop.network.MarketPayloads.TraderAction.ID, dev.tcgshop.network.MarketPayloads.TraderAction.CODEC);
        PayloadTypeRegistry.playS2C().register(dev.tcgshop.network.MarketPayloads.TraderOpen.ID, dev.tcgshop.network.MarketPayloads.TraderOpen.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(dev.tcgshop.network.MarketPayloads.Sell.ID, (p, ctx) -> ctx.server().execute(() ->
                dev.tcgshop.shop.CardSales.handle(ctx.player(), p)));
        ServerPlayNetworking.registerGlobalReceiver(dev.tcgshop.network.MarketPayloads.TraderAction.ID, (p, ctx) -> ctx.server().execute(() -> {
            if (ctx.player().getWorld().getEntityById(p.entity()) instanceof dev.tcgshop.entity.TraderEntity tr) tr.action(ctx.player(), p);
        }));
        ServerPlayNetworking.registerGlobalReceiver(ShopActionPayload.ID, (p, ctx) -> ctx.server().execute(() -> dev.tcgshop.shop.ShopServer.handleAction(p, ctx.player())));
        ServerPlayNetworking.registerGlobalReceiver(StoreBuyPayload.ID, (p, ctx) -> ctx.server().execute(() -> {
            ServerPlayerEntity pl = ctx.player();
            if (p.source() == -1) {
                if (pl.getInventory().contains(st -> st.isOf(dev.tcgshop.item.TcgItems.TCG_PHONE)))
                    dev.tcgshop.shop.ShopServer.buy(p, pl, dev.tcgshop.shop.ShopServer.phoneCatalog(), dev.tcgshop.shop.ShopServer.PHONE_FACTOR);
            } else if (pl.getWorld().getEntityById(p.source()) instanceof dev.tcgshop.entity.WholesalerEntity w && w.squaredDistanceTo(pl) < 10 * 10) {
                dev.tcgshop.shop.ShopServer.buy(p, pl, w.goods(), dev.tcgshop.shop.ShopServer.WHOLESALE_FACTOR);
            }
        }));
        net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents.AFTER_RESPAWN.register((o, n, alive) -> dev.tcgshop.shop.ShopServer.syncZones(n));
        net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((pl, from, to) -> dev.tcgshop.shop.ShopServer.syncZones(pl));
        // red: cliente -> servidor
        PayloadTypeRegistry.playC2S().register(OpenGroupPayload.ID, OpenGroupPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(GroupCutPayload.ID, GroupCutPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(BinderActionPayload.ID, BinderActionPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ScalperAnswerPayload.ID, ScalperAnswerPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(OpenGroupPayload.ID, (p, ctx) -> ctx.server().execute(() -> PackOpener.onOpenGroup(p, ctx.player())));
        ServerPlayNetworking.registerGlobalReceiver(GroupCutPayload.ID, (p, ctx) -> ctx.server().execute(() -> PackOpener.onCut(p, ctx.player())));
        ServerPlayNetworking.registerGlobalReceiver(BinderActionPayload.ID, (p, ctx) -> ctx.server().execute(() -> BinderServer.handle(p, ctx.player())));
        ServerPlayNetworking.registerGlobalReceiver(ScalperAnswerPayload.ID, (p, ctx) -> ctx.server().execute(() -> {
            if (ctx.player().getWorld().getEntityById(p.entityId()) instanceof ScalperEntity s && s.squaredDistanceTo(ctx.player()) < 16 * 16) {
                s.answer(ctx.player(), p.accept());
            }
        }));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> PackOpener.onDisconnect(handler.getPlayer()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> dev.tcgshop.shop.SocialServer.snapshot(handler.getPlayer()));
        dev.tcgshop.phone.PhoneUpgrades.register(); // 0.9: mejoras del teléfono (red, migración al entrar, config/tcgshop-phone.json)
        // 0.9: eventos aleatorios (núcleo + módulos de incidentes y actores; los módulos registran sus tipos de efecto aquí)
        dev.tcgshop.events.EventsConfig.load();
        dev.tcgshop.events.EventManager.register();
        dev.tcgshop.events.incident.IncidentModule.init();
        dev.tcgshop.events.actor.ActorModule.init();

        ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(new PriceService.ReloadListener());
        ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(new dev.tcgshop.collection.Titles.ReloadListener());
        ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(new dev.tcgshop.ptcg.PtcgCards.ReloadListener());
        ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(new dev.tcgshop.events.EventRegistry());

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            syncPrices(handler.getPlayer());
            server.execute(() -> {
                dev.tcgshop.collection.Titles.broadcast(server);
                dev.tcgshop.shop.ShopServer.syncZones(handler.getPlayer());
                dev.tcgshop.onboarding.Onboarding.onJoin(handler.getPlayer());
                if (TcgShopConfig.lastError != null && handler.getPlayer().hasPermissionLevel(2))
                    handler.getPlayer().sendMessage(Text.literal("[MasterBall Traders Co.] config/tcgshop.json tiene un error y se están usando los valores de fábrica: " + TcgShopConfig.lastError).formatted(Formatting.RED), false);
            });
        });
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, rm, ok) -> {
            CardAdvancements.invalidate();
            syncAll(server);
        });
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            PriceService.applyConfig();
            PriceService.setMarketDay(currentMarketDay(server));
        });
        ServerTickEvents.END_SERVER_TICK.register(TcgShop::tickMarket);

        CommandRegistrationCallback.EVENT.register((dispatcher, access, env) -> registerCommands(dispatcher));
        LOGGER.info("MasterBall Traders Co. cargado");
    }

    public static void syncPrices(ServerPlayerEntity player) {
        if (ServerPlayNetworking.canSend(player, PriceSyncPayload.ID)) {
            ServerPlayNetworking.send(player, new PriceSyncPayload(
                    PriceService.cardUsdView(), PriceService.sealedUsdView(), PriceService.rarityMedianView(), PriceService.gradedUsdView(),
                    new PriceSyncPayload.Market(PriceService.cdPerUsd(), PriceService.marketSeed(),
                            PriceService.marketVolatility(), PriceService.marketDay()), EconomyConfig.toJson()));
        }
    }

    /** Día de mercado = tiempo total del mundo / duración configurada (no le afecta /time set). */
    public static long currentMarketDay(MinecraftServer server) {
        return server.getOverworld().getTime() / TcgShopConfig.INSTANCE.marketDayTicks;
    }

    private static void tickMarket(MinecraftServer server) {
        if (server.getTicks() % 100 != 0) return;
        long day = currentMarketDay(server);
        if (day != PriceService.marketDay()) {
            PriceService.setMarketDay(day);
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                if (ServerPlayNetworking.canSend(p, MarketDayPayload.ID)) ServerPlayNetworking.send(p, new MarketDayPayload(day));
            }
        }
    }

    private static void syncAll(MinecraftServer server) {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) syncPrices(p);
    }

    private static void registerCommands(CommandDispatcher<ServerCommandSource> d) {
        d.register(CommandManager.literal("tcgduel")
                .then(CommandManager.literal("accept").executes(ctx -> { dev.tcgshop.ptcg.PtcgDuels.answerChallenge(ctx.getSource().getPlayerOrThrow(), true); return 1; }))
                .then(CommandManager.literal("decline").executes(ctx -> { dev.tcgshop.ptcg.PtcgDuels.answerChallenge(ctx.getSource().getPlayerOrThrow(), false); return 1; }))
                .then(CommandManager.argument("target", net.minecraft.command.argument.EntityArgumentType.player())
                        .executes(ctx -> { dev.tcgshop.ptcg.PtcgDuels.challenge(ctx.getSource().getPlayerOrThrow(),
                                net.minecraft.command.argument.EntityArgumentType.getPlayer(ctx, "target")); return 1; })));
        d.register(CommandManager.literal("tcgtrade")
                .then(CommandManager.literal("accept").then(CommandManager.argument("player", com.mojang.brigadier.arguments.StringArgumentType.word())
                        .executes(ctx -> { dev.tcgshop.trade.TradeManager.respond(ctx.getSource().getPlayerOrThrow(),
                                com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "player"), true); return 1; })))
                .then(CommandManager.literal("deny").then(CommandManager.argument("player", com.mojang.brigadier.arguments.StringArgumentType.word())
                        .executes(ctx -> { dev.tcgshop.trade.TradeManager.respond(ctx.getSource().getPlayerOrThrow(),
                                com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "player"), false); return 1; })))
                .then(CommandManager.argument("target", net.minecraft.command.argument.EntityArgumentType.player())
                        .executes(ctx -> { dev.tcgshop.trade.TradeManager.request(ctx.getSource().getPlayerOrThrow(),
                                net.minecraft.command.argument.EntityArgumentType.getPlayer(ctx, "target").getUuid()); return 1; })));
        d.register(CommandManager.literal("tcgshop")
                .then(CommandManager.literal("tutorial").executes(ctx -> dev.tcgshop.onboarding.Onboarding.replay(ctx.getSource().getPlayerOrThrow())))
                .then(dev.tcgshop.command.CardCommands.node())
                .then(dev.tcgshop.events.EventCommands.node())
                .then(CommandManager.literal("offer")
                        .then(CommandManager.literal("accept").then(CommandManager.argument("id", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0))
                                .executes(ctx -> { dev.tcgshop.shop.SocialServer.answerOffer(ctx.getSource().getPlayerOrThrow(),
                                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "id"), true); return 1; })))
                        .then(CommandManager.literal("deny").then(CommandManager.argument("id", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0))
                                .executes(ctx -> { dev.tcgshop.shop.SocialServer.answerOffer(ctx.getSource().getPlayerOrThrow(),
                                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "id"), false); return 1; }))))
                .then(CommandManager.literal("reload").requires(src -> src.hasPermissionLevel(2)).executes(ctx -> {
                    // relee config/tcgshop.json y config/tcgshop-economy.json sin reiniciar
                    MinecraftServer server = ctx.getSource().getServer();
                    TcgShopConfig.load();
                    if (TcgShopConfig.lastError != null) {
                        ctx.getSource().sendError(Text.literal("config/tcgshop.json tiene un error y no se ha aplicado: " + TcgShopConfig.lastError));
                        return 0;
                    }
                    PriceService.applyConfig();
                    PriceService.setMarketDay(currentMarketDay(server));
                    dev.tcgshop.shop.VanillaPrices.load();
                    EconomyConfig.load();
                    dev.tcgshop.luxury.LuxuryConfig.load();
                    syncAll(server);
                    dev.tcgshop.phone.PhoneConfig.load();
                    for (ServerPlayerEntity pl : server.getPlayerManager().getPlayerList()) dev.tcgshop.phone.PhoneUpgrades.sync(pl, null, null);
                    dev.tcgshop.events.EventsConfig.load(); // 0.9: config/tcgshop-events.json
                    dev.tcgshop.events.actor.ActorConfig.load();
                    ctx.getSource().sendFeedback(() -> Text.translatable("tcgshop.cmd.reloaded").formatted(Formatting.GREEN), true);
                    return 1;
                }))
                .then(CommandManager.literal("debug").requires(src -> src.hasPermissionLevel(2))
                        .then(CommandManager.literal("phasebtest").executes(ctx -> {
                            // expositor con aspecto por carta (guardar/cargar), turno de cajeros y aparición de los NPC nuevos
                            var world = ctx.getSource().getServer().getOverworld();
                            var base = world.getSpawnPos().up(3).east(6);
                            try {
                                world.setBlockState(base, dev.tcgshop.shop.TcgBlocks.CARD_STAND.getDefaultState());
                                if (world.getBlockEntity(base) instanceof dev.tcgshop.shop.ShopDisplayBlockEntity be) {
                                    ItemStack card = ItemStack.EMPTY;
                                    for (var id : net.minecraft.registry.Registries.ITEM.getIds()) if (CardInfo.isCard(id)) { card = new ItemStack(net.minecraft.registry.Registries.ITEM.get(id)); break; }
                                    be.setStack(0, card);
                                    be.setLook(0, 1.35f, 10, -45, 200);
                                    var nbt = be.createNbt(world.getRegistryManager());
                                    be.read(nbt, world.getRegistryManager());
                                    boolean ok = Math.abs(be.lookScale(0) - 1.35f) < 0.001f && be.lookRot(0, 1) == -45 && be.lookRot(0, 2) == -160;
                                    be.setStack(0, ItemStack.EMPTY);
                                    boolean reset = !be.hasLook(0);
                                    LOGGER.info("[phasebtest] aspecto por carta: guardado {} · al quitar se restaura {} {}", ok, reset, ok && reset ? "OK" : "FALLO");
                                } else LOGGER.info("[phasebtest] aspecto por carta: FALLO (sin block entity)");
                            } catch (Exception ex) { LOGGER.error("[phasebtest] aspecto FALLO", ex); }
                            long tod = world.getTimeOfDay();
                            world.setTimeOfDay(1000);
                            boolean d1 = dev.tcgshop.shop.ShopServer.isDayShift(world);
                            world.setTimeOfDay(14000);
                            boolean d2 = dev.tcgshop.shop.ShopServer.isDayShift(world);
                            world.setTimeOfDay(tod);
                            LOGGER.info("[phasebtest] turno de día: 7:00 {} · 20:00 {} {}", d1, d2, d1 && !d2 ? "OK" : "FALLO");
                            try {
                                var b = java.util.Objects.requireNonNull(dev.tcgshop.entity.TcgEntities.BEGGAR.create(world));
                                b.refreshPositionAndAngles(base.getX() + 2.5, base.getY(), base.getZ() + 0.5, 0, 0);
                                world.spawnEntity(b);
                                var m = java.util.Objects.requireNonNull(dev.tcgshop.entity.TcgEntities.BLACK_MARKET.create(world));
                                m.refreshPositionAndAngles(base.getX() + 4.5, base.getY(), base.getZ() + 0.5, 0, 0);
                                m.rollOffer();
                                world.spawnEntity(m);
                                var nbt = new net.minecraft.nbt.NbtCompound();
                                m.writeNbt(nbt);
                                m.readNbt(nbt);
                                var nbt2 = new net.minecraft.nbt.NbtCompound();
                                b.writeNbt(nbt2);
                                b.readNbt(nbt2);
                                LOGGER.info("[phasebtest] mendigo y mercader negro creados y guardados (sobre: {}) OK", nbt.contains("Offer"));
                                // datos sociales: guardar y cargar (favoritas, historial, amigos)
                                var sd = dev.tcgshop.shop.SocialServer.Data.get(ctx.getSource().getServer());
                                var root = sd.writeNbt(new net.minecraft.nbt.NbtCompound(), world.getRegistryManager());
                                LOGGER.info("[phasebtest] datos sociales guardados ({} jugadores) OK", root.getCompound("players").getKeys().size());
                            } catch (Exception ex) { LOGGER.error("[phasebtest] NPC FALLO", ex); }
                            return 1;
                        }))
                        .then(CommandManager.literal("eventtest").executes(ctx -> dev.tcgshop.events.EventSelfTest.run(ctx.getSource())))
                        .then(CommandManager.literal("econtest").executes(ctx -> {
                            // tabla vanilla, sets bloqueados y botín rarísimo de cofres (forzando la probabilidad)
                            var world = ctx.getSource().getServer().getOverworld();
                            for (String id : new String[]{"oak_log", "oak_planks", "oak_sapling", "dirt", "iron_ingot", "copper_ingot", "diamond", "emerald",
                                    "netherite_ingot", "nether_star", "diamond_pickaxe", "rotten_flesh", "wheat", "spawner", "pig_spawn_egg"})
                                LOGGER.info("[econtest] vanilla {} = {}", id, dev.tcgshop.shop.VanillaPrices.price(new ItemStack(net.minecraft.registry.Registries.ITEM.get(net.minecraft.util.Identifier.ofVanilla(id)))));
                            LOGGER.info("[econtest] vanilla: {} objetos con precio, máximo {}", dev.tcgshop.shop.VanillaPrices.table().values().stream().filter(v -> v > 0).count(),
                                    dev.tcgshop.shop.VanillaPrices.table().values().stream().mapToDouble(Double::doubleValue).max().orElse(0));
                            LOGGER.info("[econtest] bloqueados {} · tienda mínima {}", dev.tcgshop.shop.PackLock.lockedSets(), dev.tcgshop.shop.PackLock.minTier());
                            var table = ctx.getSource().getServer().getReloadableRegistries().getLootTable(net.minecraft.loot.LootTables.SIMPLE_DUNGEON_CHEST);
                            double old2 = EconomyConfig.INSTANCE.lockedPackChestChance;
                            int locked = 0, unlockedRare = 0;
                            try {
                                EconomyConfig.INSTANCE.lockedPackChestChance = 1.0;
                                for (int k = 0; k < 200; k++) {
                                    var params = new net.minecraft.loot.context.LootContextParameterSet.Builder(world)
                                            .add(net.minecraft.loot.context.LootContextParameters.ORIGIN, world.getSpawnPos().toCenterPos())
                                            .build(net.minecraft.loot.context.LootContextTypes.CHEST);
                                    for (ItemStack st : table.generateLoot(params))
                                        if (CardInfo.isPack(st)) { if (dev.tcgshop.shop.PackLock.locked(st)) locked++; else unlockedRare++; }
                                }
                            } finally {
                                EconomyConfig.INSTANCE.lockedPackChestChance = old2;
                            }
                            LOGGER.info("[econtest] con probabilidad 100%: {} sobres bloqueados en 200 cofres (normales: {}) {}", locked, unlockedRare, locked >= 190 ? "OK" : "FALLO");
                            int real = 0;
                            for (int k = 0; k < 5000; k++) {
                                var params = new net.minecraft.loot.context.LootContextParameterSet.Builder(world)
                                        .add(net.minecraft.loot.context.LootContextParameters.ORIGIN, world.getSpawnPos().toCenterPos())
                                        .build(net.minecraft.loot.context.LootContextTypes.CHEST);
                                for (ItemStack st : table.generateLoot(params)) if (CardInfo.isPack(st) && dev.tcgshop.shop.PackLock.locked(st)) real++;
                            }
                            LOGGER.info("[econtest] con {}: {} sobres bloqueados en 5000 cofres {}", old2, real, real <= 2 ? "OK" : "FALLO");
                            return 1;
                        }))
                        .then(dev.tcgshop.luxury.house.MansionService.debugNode()) // 0.9.5: /tcgshop debug mansion [force|dry]
                        .then(CommandManager.literal("selftest").executes(ctx -> {
                            // crea entidades y bloques de la tienda en el Overworld y registra cualquier error
                            var world = ctx.getSource().getServer().getOverworld();
                            var base = world.getSpawnPos().up(2);
                            String[] steps = {"scalper", "wholesaler", "trader", "display_case", "booster_box", "multiblock", "customer", "scalper_brawl", "grading", "rarity", "masterball_api", "duel", "chest_loot", "home_display", "mansion", "shop_core_wood"};
                            for (String step : steps) {
                                try {
                                    switch (step) {
                                        case "scalper" -> world.spawnEntity(java.util.Objects.requireNonNull(TcgEntities.SCALPER.create(world)));
                                        case "wholesaler" -> world.spawnEntity(java.util.Objects.requireNonNull(TcgEntities.WHOLESALER.create(world)));
                                        case "trader" -> {
                                            var tr = java.util.Objects.requireNonNull(TcgEntities.TRADER.create(world));
                                            tr.refreshPositionAndAngles(base.getX() + 0.5, base.getY(), base.getZ() + 2.5, 0, 0);
                                            world.spawnEntity(tr);
                                            var nbt = new net.minecraft.nbt.NbtCompound();
                                            tr.writeNbt(nbt); // fuerza el stock y prueba el guardado
                                            tr.readNbt(nbt);
                                        }
                                        case "display_case" -> world.setBlockState(base, dev.tcgshop.shop.TcgBlocks.DISPLAY_CASE.getDefaultState());
                                        case "booster_box" -> world.setBlockState(base.east(), dev.tcgshop.shop.TcgBlocks.BOOSTER_BOX.getDefaultState());
                                        case "multiblock" -> {
                                            // coloca cada mueble grande (todas sus partes), comprueba el inventario y rómpelo por una parte cualquiera
                                            int dx = 6;
                                            for (dev.tcgshop.shop.ShopDisplayBlock b : new dev.tcgshop.shop.ShopDisplayBlock[]{dev.tcgshop.shop.TcgBlocks.CARD_STAND,
                                                    dev.tcgshop.shop.TcgBlocks.DISPLAY_CASE, dev.tcgshop.shop.TcgBlocks.PACK_SHELF, dev.tcgshop.shop.TcgBlocks.GONDOLA,
                                                    dev.tcgshop.shop.TcgBlocks.BOX_PALLET, dev.tcgshop.shop.TcgBlocks.PEDESTAL, dev.tcgshop.shop.TcgBlocks.CARD_WALL,
                                                    dev.tcgshop.shop.TcgBlocks.COUNTER_DISPLAY, dev.tcgshop.shop.TcgBlocks.SHOWCASE, dev.tcgshop.shop.TcgBlocks.CARD_LEDGE,
                                                    dev.tcgshop.shop.TcgBlocks.BOX_SHELF, dev.tcgshop.shop.TcgBlocks.CARD_FRAME, dev.tcgshop.shop.TcgBlocks.HOME_FRAME,
                                                    dev.tcgshop.shop.TcgBlocks.HOME_LEDGE, dev.tcgshop.shop.TcgBlocks.HOME_SHOWCASE, dev.tcgshop.shop.TcgBlocks.HOME_CARD_WALL,
                                                    dev.tcgshop.shop.TcgBlocks.HOME_PEDESTAL}) {
                                                for (net.minecraft.util.math.Direction f : new net.minecraft.util.math.Direction[]{net.minecraft.util.math.Direction.NORTH,
                                                        net.minecraft.util.math.Direction.EAST, net.minecraft.util.math.Direction.SOUTH, net.minecraft.util.math.Direction.WEST}) {
                                                    var master = base.add(dx, 4, 0);
                                                    dx += 5;
                                                    var st = b.getDefaultState().with(dev.tcgshop.shop.ShopDisplayBlock.FACING, f);
                                                    world.setBlockState(master, st);
                                                    b.onPlaced(world, master, st, null, net.minecraft.item.ItemStack.EMPTY);
                                                    int ok = 0;
                                                    for (int p = 0; p < b.kind.parts(); p++) {
                                                        var ps = world.getBlockState(b.kind.partPos(master, f, p));
                                                        if (ps.isOf(b) && ps.get(dev.tcgshop.shop.ShopDisplayBlock.PART) == p && b.master(ps, b.kind.partPos(master, f, p)).equals(master)) ok++;
                                                    }
                                                    if (ok != b.kind.parts()) throw new IllegalStateException(b.kind + " " + f + ": partes " + ok + "/" + b.kind.parts());
                                                    if (!(world.getBlockEntity(master) instanceof dev.tcgshop.shop.ShopDisplayBlockEntity be) || be.size() != b.kind.slots.length)
                                                        throw new IllegalStateException(b.kind + ": inventario del maestro");
                                                    var last = b.kind.partPos(master, f, b.kind.parts() - 1);
                                                    world.breakBlock(last, true);
                                                    for (int p = 0; p < b.kind.parts(); p++)
                                                        if (world.getBlockState(b.kind.partPos(master, f, p)).isOf(b)) throw new IllegalStateException(b.kind + " " + f + ": quedó la parte " + p);
                                                }
                                            }
                                        }
                                        case "customer" -> {
                                            var c = java.util.Objects.requireNonNull(TcgEntities.CUSTOMER.create(world));
                                            c.refreshPositionAndAngles(base.getX() + 0.5, base.getY(), base.getZ() + 2.5, 0, 0);
                                            world.spawnEntity(c); // sin tienda: se marcha solo en el siguiente tick
                                        }
                                        case "grading" -> {
                                            // reparto de notas (100.000 tiradas), multiplicadores y precio de una carta gradeada
                                            var rnd = net.minecraft.util.math.random.Random.create(42);
                                            int[] hist = new int[101];
                                            for (int k = 0; k < 100_000; k++) hist[dev.tcgshop.grading.Grading.roll(rnd)]++;
                                            for (var band : dev.tcgshop.grading.Grading.ODDS) {
                                                int n = 0;
                                                for (int g = band.from(); g <= band.to(); g++) n += hist[g];
                                                double pct = n / 1000.0;
                                                if (Math.abs(pct - band.percent()) > 0.6) throw new IllegalStateException("tramo " + band + " salió " + pct + "%");
                                            }
                                            if (hist[0] + java.util.Arrays.stream(hist, 1, 10).sum() > 0) throw new IllegalStateException("notas por debajo de 1.0");
                                            double prev = 0;
                                            for (int g = 10; g <= 100; g++) {
                                                double mlt = dev.tcgshop.grading.Grading.multiplier(g);
                                                if (mlt < prev) throw new IllegalStateException("multiplicador no creciente en " + g);
                                                prev = mlt;
                                            }
                                            net.minecraft.item.Item cardItem = null;
                                            for (var id : net.minecraft.registry.Registries.ITEM.getIds())
                                                if (CardInfo.isCard(id)) { cardItem = net.minecraft.registry.Registries.ITEM.get(id); break; }
                                            if (cardItem != null) {
                                                ItemStack raw = new ItemStack(cardItem);
                                                ItemStack g10 = dev.tcgshop.grading.Grading.apply(raw, 100, 1), g10b = dev.tcgshop.grading.Grading.apply(raw, 10, 2);
                                                long pr = PriceService.priceCd(raw), p10 = PriceService.priceCd(g10), p1 = PriceService.priceCd(g10b);
                                                LOGGER.info("[selftest] grading: {} raw={} Grado10={} Grado1={} hist10={}% 9.5+={}%", raw.getName().getString(), pr, p10, p1,
                                                        hist[100] / 1000.0, java.util.Arrays.stream(hist, 95, 101).sum() / 1000.0);
                                                if (pr > 0 && !(p10 >= pr && p1 < pr)) throw new IllegalStateException("Grado 1 debe valer menos que el precio base y Grado 10 al menos eso");
                                                if (dev.tcgshop.pricing.GradeModel.dataSize() < 7000) throw new IllegalStateException("faltan datos de Grado");
                                                if (Math.abs(dev.tcgshop.pricing.GradeModel.popularity(1) - 1.0) > 1e-9 || dev.tcgshop.pricing.GradeModel.popularity(1025) != 0
                                                        || dev.tcgshop.pricing.GradeModel.popularity(300) < 0.65 || dev.tcgshop.pricing.GradeModel.popularity(301) >= 0.65)
                                                    throw new IllegalStateException("curva de popularidad incorrecta");
                                                if (Math.abs(dev.tcgshop.pricing.GradeModel.mult(50, 100) - 50) > 1e-9 || Math.abs(dev.tcgshop.pricing.GradeModel.mult(50, 10) - 0.40) > 1e-9 || dev.tcgshop.pricing.GradeModel.mult(50, 49) >= 1 || dev.tcgshop.pricing.GradeModel.mult(50, 50) < 1)
                                                    throw new IllegalStateException("Mg incorrecto");
                                                var zard = net.minecraft.util.Identifier.of(dev.masterball.api.card.CardInfo.NS, "baseset/cards/bs004");
                                                LOGGER.info("[selftest] grado: {} datos · Base Set #4 = {} rank {} · M10 x{} · P(10) {}%",
                                                        dev.tcgshop.pricing.GradeModel.dataSize(), dev.tcgshop.pricing.GradeModel.infoOf(zard).cat(), dev.tcgshop.pricing.GradeModel.infoOf(zard).rank(),
                                                        String.format("%.2f", PriceService.gradeM10(zard, dev.masterball.api.card.Rarity.UNKNOWN)), String.format("%.1f", 100 * dev.tcgshop.pricing.GradeModel.prob10(zard)));
                                                if (dev.tcgshop.trade.TradeManager.tradeable(g10)) throw new IllegalStateException("una gradeada no debe intercambiarse");
                                                double pg = 0;
                                                var cid = CardInfo.id(raw); var crr = dev.masterball.api.card.Rarity.of(raw);
                                                for (int g = 10; g <= 100; g++) {
                                                    double u = PriceService.gradedUsd(cid, crr, g);
                                                    if (u + 1e-9 < pg) throw new IllegalStateException("precio gradeado no creciente en " + g);
                                                    pg = u;
                                                }
                                                LOGGER.info("[selftest] graded prices: {} reales, Grado 10 de prueba ${}", PriceService.gradedUsdView().size(), String.format("%.2f", pg));
                                            }
                                        }
                                        case "rarity" -> {
                                            // rarezas oficiales, foil por era y variantes de impresión con su precio
                                            var R = dev.masterball.api.card.Rarity.class;
                                            if (dev.masterball.api.card.RarityData.size() < 7700) throw new IllegalStateException("faltan datos de rareza");
                                            var zard = net.minecraft.util.Identifier.of(dev.masterball.api.card.CardInfo.NS, "baseset/cards/bs004");
                                            var sir = net.minecraft.util.Identifier.of(dev.masterball.api.card.CardInfo.NS, "sviolet/cards/svi245");
                                            if (dev.masterball.api.card.Rarity.of(zard) != dev.masterball.api.card.Rarity.HOLO_RARE) throw new IllegalStateException("Charizard Base Set debe ser Holo Rare");
                                            if (dev.masterball.api.card.RarityData.holoPattern(zard) != dev.masterball.api.card.FoilPattern.STARLIGHT) throw new IllegalStateException("Base Set debe tener foil Starlight");
                                            if (dev.masterball.api.card.Rarity.of(sir) != dev.masterball.api.card.Rarity.SPECIAL_ILLUSTRATION_RARE || !dev.masterball.api.card.RarityData.card(sir).secret())
                                                throw new IllegalStateException("SVI 245 debe ser SIR y Secret Rare numérica");
                                            java.util.Map<dev.masterball.api.card.Rarity, Integer> cnt = new java.util.EnumMap<>(dev.masterball.api.card.Rarity.class);
                                            int unknown = 0;
                                            for (var id : net.minecraft.registry.Registries.ITEM.getIds()) {
                                                if (!CardInfo.isCard(id)) continue;
                                                var r = dev.masterball.api.card.Rarity.of(id);
                                                cnt.merge(r, 1, Integer::sum);
                                                if (r == dev.masterball.api.card.Rarity.UNKNOWN) unknown++;
                                            }
                                            LOGGER.info("[selftest] rarity: {} rarezas en uso, {} cartas sin rareza: {}", cnt.size(), unknown, cnt);
                                            if (unknown > 400) throw new IllegalStateException("demasiadas cartas sin rareza: " + unknown);
                                            // variantes: la 1ª edición vale más que la estándar y la reverse del Exeggcute de Prismatic tiene precio real
                                            ItemStack z = new ItemStack(net.minecraft.registry.Registries.ITEM.get(zard));
                                            long std = PriceService.priceCd(z);
                                            long first = PriceService.priceCd(dev.masterball.api.card.PrintVariant.apply(z.copy(), dev.masterball.api.card.PrintVariant.FIRST_EDITION));
                                            var pre = net.minecraft.util.Identifier.of(dev.masterball.api.card.CardInfo.NS, "prismaticevolutions/cards/pre001");
                                            ItemStack e = new ItemStack(net.minecraft.registry.Registries.ITEM.get(pre));
                                            long es = PriceService.priceCd(e), em = PriceService.priceCd(dev.masterball.api.card.PrintVariant.apply(e.copy(), dev.masterball.api.card.PrintVariant.MASTER_BALL));
                                            LOGGER.info("[selftest] variantes: Charizard estándar={} 1ª ed={} · Exeggcute estándar={} Master Ball={}", std, first, es, em);
                                            if (first <= std * 2 || em <= es * 3) throw new IllegalStateException("las variantes premium deben valer bastante más");
                                            // sorteo: en 20.000 cartas comunes de Prismatic sale alguna Master Ball y la mayoría son estándar
                                            var rnd = net.minecraft.util.math.random.Random.create(7);
                                            int mb = 0, stdc = 0;
                                            for (int k = 0; k < 20_000; k++) {
                                                var v = dev.masterball.api.card.PrintVariant.roll(rnd, pre);
                                                if (v == dev.masterball.api.card.PrintVariant.MASTER_BALL) mb++;
                                                if (v == dev.masterball.api.card.PrintVariant.STANDARD) stdc++;
                                            }
                                            LOGGER.info("[selftest] sorteo Prismatic: Master Ball {} / estándar {} de 20000", mb, stdc);
                                            if (mb < 100 || mb > 350 || stdc < 13_000) throw new IllegalStateException("probabilidades de variante fuera de rango");
                                            if (dev.masterball.api.card.PrintVariant.roll(rnd, sir) != dev.masterball.api.card.PrintVariant.STANDARD) throw new IllegalStateException("una SIR no tiene reverse");
                                        }
                                        case "masterball_api" -> {
                                            // MasterBall Traders API: catálogo, conversión de ids de ScalperTCG, rescate de álbumes y contenido de sobres
                                            var NS = dev.masterball.api.card.CardInfo.NS;
                                            int cat = dev.masterball.api.card.CardCatalog.cardCount();
                                            if (cat < 11_000 || dev.masterball.api.item.CardItems.cardCount() != cat)
                                                throw new IllegalStateException("catálogo " + cat + " vs ítems " + dev.masterball.api.item.CardItems.cardCount());
                                            var zardItem = net.minecraft.registry.Registries.ITEM.get(net.minecraft.util.Identifier.of(NS, "baseset/cards/bs004"));
                                            if (net.minecraft.registry.Registries.ITEM.get(net.minecraft.util.Identifier.of("cobbletcg", "baseset/cards/bs004")) != zardItem
                                                    || !net.minecraft.registry.Registries.ITEM.containsId(net.minecraft.util.Identifier.of("cobbletcg", "mew151/sealed_mew151")))
                                                throw new IllegalStateException("el registro no convierte ids cobbletcg:");
                                            var regs = world.getRegistryManager();
                                            var old = new net.minecraft.nbt.NbtCompound();
                                            old.putString("id", "cobbletcg:baseset/cards/bs004");
                                            old.putInt("count", 3);
                                            ItemStack conv = ItemStack.fromNbt(regs, old).orElse(ItemStack.EMPTY);
                                            if (conv.getItem() != zardItem || conv.getCount() != 3) throw new IllegalStateException("ItemStack guardado con id cobbletcg no se convierte: " + conv);
                                            if (!dev.masterball.api.card.CardInfo.migrate("cobbletcg:mew151/cards/mew006").equals(NS + ":mew151/cards/mew006"))
                                                throw new IllegalStateException("migrate(String)");
                                            // 0.9: TCG Pocket fuera (sólo cartas físicas); lo guardado con esos ids se descarta sin error y sin perder el contenedor
                                            for (var psi : dev.masterball.api.card.CardCatalog.sets())
                                                if (psi.pocket() || dev.masterball.api.card.CardCatalog.REMOVED_SETS.contains(psi.id())) throw new IllegalStateException("set de TCG Pocket cargado: " + psi.id());
                                            if (net.minecraft.registry.Registries.ITEM.containsId(net.minecraft.util.Identifier.of(NS, "gapex/sealed_gapex")))
                                                throw new IllegalStateException("sigue registrado un sobre de TCG Pocket");
                                            if (ItemStack.fromNbt(regs, net.minecraft.nbt.StringNbtReader.parse("{id:\"masterball:gapex/cards/a1-001\",count:2}")).isPresent())
                                                throw new IllegalStateException("una carta de TCG Pocket guardada no se descarta");
                                            var pocketShulker = ItemStack.fromNbt(regs, net.minecraft.nbt.StringNbtReader.parse("{id:\"minecraft:shulker_box\",count:1,components:{\"minecraft:container\":[{slot:0,item:{id:\"cobbletcg:misland/sealed_misland\",count:1}},{slot:1,item:{id:\"masterball:baseset/cards/bs004\",count:1}}]}}")).orElse(ItemStack.EMPTY);
                                            var pocketCont = pocketShulker.get(net.minecraft.component.DataComponentTypes.CONTAINER);
                                            if (pocketShulker.isEmpty() || pocketCont == null || pocketCont.streamNonEmpty().count() != 1)
                                                throw new IllegalStateException("caja con un sobre de TCG Pocket: " + pocketShulker);
                                            var bd = dev.tcgshop.collection.BinderData.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, net.minecraft.nbt.StringNbtReader.parse(
                                                    "{owner:[I;1,2,3,4],counts:{\"cobbletcg:mew151/cards/mew006\":2,\"masterball:mew151/cards/mew006\":1},fresh:[\"cobbletcg:mew151/cards/mew006\"]}"))
                                                    .getOrThrow();
                                            if (bd.count(NS + ":mew151/cards/mew006") != 3 || !bd.fresh.contains(NS + ":mew151/cards/mew006"))
                                                throw new IllegalStateException("el Binder no convierte ids cobbletcg: " + bd.counts);
                                            // álbum de ScalperTCG (estructura desconocida): todo objeto dentro se rescata en barriles
                                            var album = net.minecraft.nbt.StringNbtReader.parse("{id:\"cobbletcg:album_block_entity\",AlbumId:\"mew151\",CustomInventory:[{SlotFullIndex:5,"
                                                    + "itemStack:{id:\"cobbletcg:mew151/cards/mew006\",count:1}}],Items:[{Slot:0b,id:\"cobbletcg:mew151/sealed_mew151\",count:2}]}");
                                            var rescued = dev.masterball.api.migrate.LegacyRescue.scan(album, regs);
                                            if (rescued.size() != 2) throw new IllegalStateException("rescate de álbum: " + rescued);
                                            var at = base.add(-8, 0, -8);
                                            for (int k = 0; k < 3; k++) world.setBlockState(at.up(k), net.minecraft.block.Blocks.AIR.getDefaultState());
                                            dev.masterball.api.migrate.LegacyRescue.place(world, at, rescued);
                                            if (!(world.getBlockEntity(at) instanceof net.minecraft.block.entity.BarrelBlockEntity barrel) || barrel.getStack(0).isEmpty())
                                                throw new IllegalStateException("el rescate no dejó un barril con las cartas");
                                            // sobres: siempre 5 cartas del set; el sobre dios sin comunes; sellado -> 96/3/1
                                            var rnd = net.minecraft.util.math.random.Random.create(11);
                                            int sets = 0;
                                            for (var si : dev.masterball.api.card.CardCatalog.sets()) {
                                                sets++;
                                                for (int k = 0; k < 40; k++) {
                                                    for (int tier = 1; tier <= 3; tier++) {
                                                        var got = dev.masterball.api.pack.PackContents.open(si.id(), tier, rnd);
                                                        if (got.size() != 5) throw new IllegalStateException(si.id() + " tipo " + tier + ": " + got.size() + " cartas");
                                                        for (ItemStack c : got) {
                                                            if (!CardInfo.setId(CardInfo.id(c)).equals(si.id())) throw new IllegalStateException(si.id() + ": carta de otro set " + CardInfo.id(c));
                                                            if (tier == 3 && dev.masterball.api.card.Rarity.of(c).family == dev.masterball.api.card.Rarity.Family.COMMON
                                                                    && si.cards().size() > 60 && !si.pocket()) throw new IllegalStateException(si.id() + ": común en sobre dios " + CardInfo.id(c));
                                                        }
                                                    }
                                                }
                                            }
                                            int[] tiers = new int[4];
                                            for (int k = 0; k < 100_000; k++) tiers[dev.masterball.api.pack.PackContents.rollTier(rnd)]++;
                                            if (Math.abs(tiers[1] - 96_000) > 800 || Math.abs(tiers[2] - 3_000) > 400 || Math.abs(tiers[3] - 1_000) > 250)
                                                throw new IllegalStateException("reparto de sobres " + java.util.Arrays.toString(tiers));
                                            // un set antiguo sin Doble Raras no regala Secretas: Skyridge normal casi nunca da hiper; el sobre vale al menos el 85 % de lo que trae
                                            int hyper = 0;
                                            for (int k = 0; k < 2000; k++)
                                                for (ItemStack c : dev.masterball.api.pack.PackContents.open("sridge", 1, rnd))
                                                    if (dev.masterball.api.card.Rarity.of(c).family == dev.masterball.api.card.Rarity.Family.HYPER_RARE) hyper++;
                                            double ev = PriceService.packExpectedUsd("sridge"), pack = PriceService.sealedUsd("sridge", PriceService.Sealed.PACK);
                                            if (hyper > 60 || pack < ev * 0.84) throw new IllegalStateException("Skyridge: " + hyper + " hiper en 2000 sobres, sobre $" + pack + " vs media $" + ev);
                                            // cartas antiguas (hasta 2017): ~30 % de las tiradas en 4.9 / 5.0 / 5.1
                                            var oldCard = net.minecraft.util.Identifier.of(NS, "baseset/cards/bs004");
                                            int near5 = 0, above = 0;
                                            for (int k = 0; k < 20_000; k++) {
                                                int g = dev.tcgshop.grading.Grading.roll(rnd, oldCard);
                                                if (g >= 49 && g <= 51) near5++;
                                                if (g > 51) above++;
                                            }
                                            LOGGER.info("[selftest] gradeo antiguo: {}% en 4.9-5.1, {}% por encima", near5 / 200.0, above / 200.0);
                                            if (near5 < 5000 || near5 > 7000) throw new IllegalStateException("reparto 4.9-5.1 de cartas antiguas: " + near5);
                                            LOGGER.info("[selftest] sobres: Skyridge {} hiper/2000 · sobre ${} (media ${}) · Jungle ${} · 30th ${}", hyper,
                                                    String.format("%.2f", pack), String.format("%.2f", ev), String.format("%.2f", PriceService.sealedUsd("jungle", PriceService.Sealed.PACK)),
                                                    String.format("%.2f", PriceService.sealedUsd("ani30", PriceService.Sealed.PACK)));
                                            LOGGER.info("[selftest] masterball_api: {} cartas, {} sobres, {} sets · sobres normal/semi/dios {}/{}/{} · álbum rescatado ({} objetos)",
                                                    cat, dev.masterball.api.item.CardItems.packCount(), sets, tiers[1], tiers[2], tiers[3], rescued.size());
                                        }
                                        case "duel" -> {
                                            int mapped = dev.tcgshop.ptcg.PtcgCards.mapped();
                                            LOGGER.info("[selftest] duel: {} cartas jugables en el TCG real", mapped);
                                            if (mapped < 3000) throw new IllegalStateException("pocas cartas emparejadas: " + mapped);
                                        }
                                        case "chest_loot" -> {
                                            // 3000 cofres de mazmorra: ~1% con sobre
                                            var table = ctx.getSource().getServer().getReloadableRegistries().getLootTable(net.minecraft.loot.LootTables.SIMPLE_DUNGEON_CHEST);
                                            int packs = 0;
                                            for (int k = 0; k < 3000; k++) {
                                                var params = new net.minecraft.loot.context.LootContextParameterSet.Builder(world)
                                                        .add(net.minecraft.loot.context.LootContextParameters.ORIGIN, base.toCenterPos())
                                                        .build(net.minecraft.loot.context.LootContextTypes.CHEST);
                                                for (ItemStack st : table.generateLoot(params)) if (CardInfo.isPack(st)) packs++;
                                            }
                                            LOGGER.info("[selftest] chest_loot: {} sobres en 3000 cofres ({}%)", packs, packs / 30.0);
                                            if (packs < 8 || packs > 70) throw new IllegalStateException("probabilidad de sobre fuera de rango: " + packs);
                                        }
                                        case "mansion" -> dev.tcgshop.luxury.house.MansionService.selfTest(); // prueba en seco del generador
                                        case "home_display" -> {
                                            var b = dev.tcgshop.shop.TcgBlocks.HOME_FRAME;
                                            if (b.shopOnly() || !dev.tcgshop.shop.TcgBlocks.CARD_FRAME.shopOnly()) throw new IllegalStateException("shopOnly mal");
                                            var pos = base.add(-8, 6, 0);
                                            world.setBlockState(pos, b.getDefaultState());
                                            if (!(world.getBlockEntity(pos) instanceof dev.tcgshop.shop.ShopDisplayBlockEntity be) || !be.isHome())
                                                throw new IllegalStateException("el expositor de casa no se reconoce");
                                            world.removeBlock(pos, false);
                                        }
                                        case "scalper_brawl" -> {
                                            var a = java.util.Objects.requireNonNull(TcgEntities.SCALPER.create(world));
                                            var b = java.util.Objects.requireNonNull(TcgEntities.SCALPER.create(world));
                                            a.refreshPositionAndAngles(base.getX() - 3, base.getY(), base.getZ(), 0, 0);
                                            b.refreshPositionAndAngles(base.getX() - 5, base.getY(), base.getZ(), 0, 0);
                                            world.spawnEntity(a);
                                            world.spawnEntity(b);
                                            a.startBrawl(b);
                                            b.startBrawl(a);
                                            if (a.mode() != dev.tcgshop.entity.ScalperEntity.Mode.BRAWL || a.getTarget() != b) throw new IllegalStateException("la pelea no empezó");
                                        }
                                        default -> world.setBlockState(base.west(), dev.tcgshop.shop.TcgBlocks.CORES.get(dev.tcgshop.shop.ShopTier.WOOD).getDefaultState());
                                    }
                                    LOGGER.info("[selftest] {} OK", step);
                                } catch (Throwable t) {
                                    LOGGER.error("[selftest] {} FALLÓ", step, t);
                                }
                            }
                            return 1;
                        }))
                        .then(CommandManager.literal("ptcgtest").executes(ctx -> {
                            // TCG real: mazo desde un "pool" como el de un jugador, validación y partida jugador (auto) contra la casa
                            long t0 = System.currentTimeMillis();
                            com.google.gson.JsonObject pool = new com.google.gson.JsonObject();
                            int added = 0;
                            for (var id : net.minecraft.registry.Registries.ITEM.getIds()) {
                                String f = CardInfo.isCard(id) ? dev.tcgshop.ptcg.PtcgCards.engineName(id.toString()) : null;
                                if (f != null && (added++ % 3 == 0)) pool.addProperty(f, 2);
                            }
                            String poolJson = pool.toString();
                            dev.tcgshop.ptcg.PtcgEngine.call(api -> {
                                StringBuilder r = new StringBuilder();
                                String d1 = dev.tcgshop.ptcg.PtcgEngine.str(api, "buildDeck", poolJson, 7);
                                String v1 = dev.tcgshop.ptcg.PtcgEngine.str(api, "validateDeck", d1);
                                String d2 = d1;
                                r.append("pool=").append(pool.size()).append(" validar='").append(v1).append("' ");
                                // mazos de la IA en los 4 niveles (pool de la casa + azar)
                                com.google.gson.JsonObject house = pool.deepCopy();
                                house.addProperty("__house", 1);
                                for (double chaos : new double[]{1.0, 0.6, 0.25, 0.0}) {
                                    String hd = dev.tcgshop.ptcg.PtcgEngine.str(api, "buildDeck", house.toString(), 99, chaos);
                                    r.append("ia").append(chaos).append("='").append(dev.tcgshop.ptcg.PtcgEngine.str(api, "validateDeck", hd)).append("' ");
                                    if (chaos == 0.0) d2 = hd;
                                }
                                String err = dev.tcgshop.ptcg.PtcgEngine.str(api, "create", "t", "{\"id\":1,\"name\":\"Humano\",\"deck\":" + d1 + "}",
                                        "{\"id\":2,\"name\":\"Casa\",\"deck\":" + d2 + ",\"bot\":true}");
                                r.append("create='").append(err).append("' ");
                                int steps = 0, autos = 0, passes = 0, errors = 0, suggested = 0, suggestErr = 0;
                                com.google.gson.JsonObject v = null;
                                while (steps++ < 1500) {
                                    if (dev.tcgshop.ptcg.PtcgEngine.bool(api, "botStep", "t")) continue;
                                    v = com.google.gson.JsonParser.parseString(dev.tcgshop.ptcg.PtcgEngine.str(api, "view", "t", 1)).getAsJsonObject();
                                    if (v.get("phase").getAsInt() == 7 || v.get("winner").getAsInt() != -1) break;
                                    if (v.has("prompt") && v.get("prompt").isJsonObject()) {
                                        String e = dev.tcgshop.ptcg.PtcgEngine.str(api, "auto", "t", 1);
                                        if (!e.isEmpty()) errors++;
                                        autos++;
                                    } else if (v.get("myTurn").getAsBoolean()) {
                                        // el "humano" sigue la sugerencia de la IA (prueba suggest + act con el mismo formato que el cliente)
                                        String sg = dev.tcgshop.ptcg.PtcgEngine.str(api, "suggest", "t", 1);
                                        if (sg.isEmpty() || sg.contains("\"pass\"")) {
                                            dev.tcgshop.ptcg.PtcgEngine.str(api, "act", "t", 1, "{\"t\":\"pass\"}");
                                            passes++;
                                        } else {
                                            String e = dev.tcgshop.ptcg.PtcgEngine.str(api, "act", "t", 1, sg);
                                            suggested++;
                                            if (!e.isEmpty()) { suggestErr++; dev.tcgshop.ptcg.PtcgEngine.str(api, "act", "t", 1, "{\"t\":\"pass\"}"); passes++; }
                                        }
                                        if (!v.has("me") || !v.getAsJsonObject("me").has("atkOk")) errors++;
                                    } else if (!v.get("waiting").getAsBoolean()) {
                                        errors++;
                                        if (errors > 50) break;
                                    }
                                }
                                String view = dev.tcgshop.ptcg.PtcgEngine.str(api, "view", "t", 1);
                                api.run("dispose", "t");
                                r.append("pasos=").append(steps).append(" auto=").append(autos).append(" turnosHumano=").append(passes).append(" errores=").append(errors).append(" sugerencias=").append(suggested).append(" sugFallidas=").append(suggestErr)
                                        .append(" ganador=").append(v == null ? "?" : v.get("winner").getAsString()).append(" vista=").append(view.length());
                                return r.toString();
                            }).whenComplete((r, e) -> LOGGER.info("[ptcgtest] {} en {} ms", e != null ? "FALLÓ " + e : r, System.currentTimeMillis() - t0));
                            return 1;
                        }))
                        .then(CommandManager.literal("dumpitems").executes(ctx -> {
                            // Lista de cartas y sobres registrados por MasterBall Traders API
                            java.util.List<String> ids = new java.util.ArrayList<>();
                            for (var id : net.minecraft.registry.Registries.ITEM.getIds())
                                if (id.getNamespace().equals(dev.masterball.api.card.CardInfo.NS)) ids.add(id.toString());
                            java.util.Collections.sort(ids);
                            try {
                                java.nio.file.Files.write(net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("masterball_items.txt"), ids);
                            } catch (Exception e) {
                                LOGGER.error("dumpitems", e);
                            }
                            ctx.getSource().sendFeedback(() -> Text.literal(ids.size() + " cartas y sobres volcados"), false);
                            return 1;
                        }))
                        .then(CommandManager.literal("rarities").executes(ctx -> {
                            // Cuenta las cartas registradas por rareza y cuántas tienen precio real.
                            java.util.EnumMap<Rarity, Integer> count = new java.util.EnumMap<>(Rarity.class);
                            int total = 0, real = 0;
                            for (var item : net.minecraft.registry.Registries.ITEM) {
                                ItemStack st = new ItemStack(item);
                                if (!CardInfo.isCard(st)) continue;
                                total++;
                                count.merge(Rarity.of(st), 1, Integer::sum);
                                if (PriceService.hasRealPrice(CardInfo.id(st))) real++;
                            }
                            int t = total, r = real;
                            ctx.getSource().sendFeedback(() -> Text.literal("Cartas: " + t + " | con precio real: " + r + " | " + count), false);
                            LOGGER.info("[debug] cartas={} precioReal={} rarezas={}", t, r, count);
                            return 1;
                        })))
                .then(CommandManager.literal("price").executes(ctx -> {
                    ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                    ItemStack st = p.getMainHandStack();
                    if (!CardInfo.isCard(st)) {
                        ctx.getSource().sendError(Text.translatable("tcgshop.cmd.notcard"));
                        return 0;
                    }
                    Rarity r = Rarity.of(st);
                    var id = CardInfo.id(st);
                    long day = PriceService.marketDay();
                    long now = PriceService.priceCd(st);
                    long std = PriceService.priceCd(id, r);
                    // misma copia (variante y Grado) hace 7 días: se escala el precio estándar de entonces
                    long weekAgo = std <= 0 ? 0 : Math.round(PriceService.priceCdOnDay(id, r, Math.max(0, day - 7)) * (double) now / std);
                    double change = weekAgo == 0 ? 0 : (now - weekAgo) * 100.0 / weekAgo;
                    Text trend = Text.literal(String.format(java.util.Locale.ROOT, "%+.1f%%", change))
                            .formatted(change >= 0 ? Formatting.GREEN : Formatting.RED);
                    Text source = Text.translatable(PriceService.hasRealPrice(id) ? "tcgshop.price.source.real" : "tcgshop.price.source.estimate")
                            .formatted(Formatting.DARK_GRAY);
                    ctx.getSource().sendFeedback(() -> Text.translatable("tcgshop.cmd.price",
                            st.toHoverableText(),
                            r.displayName().copy().withColor(r.color & 0xFFFFFF),
                            Text.literal(PriceService.format(now)).formatted(Formatting.GOLD))
                            .append(" ").append(trend).append(" ").append(source), false);
                    return 1;
                }))
                .then(CommandManager.literal("value").executes(ctx -> {
                    ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                    long total = 0;
                    int count = 0;
                    for (int i = 0; i < p.getInventory().size(); i++) {
                        ItemStack st = p.getInventory().getStack(i);
                        if (CardInfo.isCard(st)) {
                            total += PriceService.priceCd(st) * st.getCount();
                            count += st.getCount();
                        }
                    }
                    long t = total;
                    int c = count;
                    ctx.getSource().sendFeedback(() -> Text.translatable("tcgshop.cmd.value", c,
                            Text.literal(PriceService.format(t)).formatted(Formatting.GOLD)), false);
                    return 1;
                })));
    }
}

package dev.tcgshop;

import dev.masterball.api.platform.Platform;
import dev.masterball.api.platform.Net;
import dev.masterball.api.platform.event.ServerEvents;
import dev.masterball.api.platform.event.PlayerEvents;
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
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TcgShop {
    public static final String MOD_ID = "tcgshop";
    public static final Logger LOGGER = LoggerFactory.getLogger("MasterBall Traders Co.");

    public static void init() {
        TcgShopConfig.load();
        TcgSounds.init();

        TcgItems.init();
        TcgEntities.init();
        dev.tcgshop.shop.TcgBlocks.init();
        dev.tcgshop.shop.CleanBlocks.init(); // 1.1 cleanliness update: droppings, wall freshener, wide door, decor (config/tcgshop-clean.json)
        dev.tcgshop.luxury.LuxuryBlocks.register();
        dev.tcgshop.world.WorldLoot.register(); // 1.0 beta 3: trash cans + Bankrupt Card Shop (config/tcgshop-worldloot.json)
        dev.tcgshop.casino.CasinoBlocks.init(); // 1.0 beta 3 casino machines + scratch tickets (before ShopServer.init: fixed prices)
        dev.tcgshop.shop.ShopServer.init();
        dev.tcgshop.shop.VanillaPrices.load();
        EconomyConfig.load();
        dev.tcgshop.shop.ShopProtection.register();
        dev.tcgshop.shop.RgbLighting.register(); // 1.0 RGB displays: sneak + dye / glowstone dust sets the LEDs
        CollectionService.init();

        // network: server -> client
        Net.INSTANCE.s2c(PriceSyncPayload.ID, PriceSyncPayload.CODEC);
        Net.INSTANCE.s2c(MarketDayPayload.ID, MarketDayPayload.CODEC);
        Net.INSTANCE.s2c(GroupReadyPayload.ID, GroupReadyPayload.CODEC);
        Net.INSTANCE.s2c(GroupRevealPayload.ID, GroupRevealPayload.CODEC);
        Net.INSTANCE.s2c(CollectionSyncPayload.ID, CollectionSyncPayload.CODEC);
        Net.INSTANCE.s2c(OpenBinderPayload.ID, OpenBinderPayload.CODEC);
        Net.INSTANCE.s2c(FactPayload.ID, FactPayload.CODEC);
        Net.INSTANCE.s2c(ScalperOfferPayload.ID, ScalperOfferPayload.CODEC);
        Net.INSTANCE.s2c(TitlesPayload.ID, TitlesPayload.CODEC);
        Net.INSTANCE.s2c(ShopViewPayload.ID, ShopViewPayload.CODEC);
        Net.INSTANCE.s2c(ShopZonesPayload.ID, ShopZonesPayload.CODEC);
        Net.INSTANCE.s2c(StoreOpenPayload.ID, StoreOpenPayload.CODEC);
        Net.INSTANCE.c2s(ShopActionPayload.ID, ShopActionPayload.CODEC);
        Net.INSTANCE.s2c(RouletteOpenPayload.ID, RouletteOpenPayload.CODEC);
        Net.INSTANCE.s2c(RouletteResultPayload.ID, RouletteResultPayload.CODEC);
        Net.INSTANCE.s2c(DealOpenPayload.ID, DealOpenPayload.CODEC);
        Net.INSTANCE.s2c(BoxOpenPayload.ID, BoxOpenPayload.CODEC);
        Net.INSTANCE.s2c(NpcSpeechPayload.ID, NpcSpeechPayload.CODEC);
        Net.INSTANCE.s2c(dev.tcgshop.network.TutorialPayload.ID, dev.tcgshop.network.TutorialPayload.CODEC);
        dev.tcgshop.onboarding.MeowthTalk.register();
        dev.tcgshop.onboarding.Onboarding.register(); // 1.0: starter config migration, one-time visitors, first-pack money fallback
        // CobbleGrade
        dev.tcgshop.grading.Grading.init();
        dev.masterball.api.card.PrintVariant.init();
        Net.INSTANCE.c2s(GradePayloads.Action.ID, GradePayloads.Action.CODEC);
        Net.INSTANCE.s2c(GradePayloads.Sync.ID, GradePayloads.Sync.CODEC);
        Net.INSTANCE.s2c(GradePayloads.Reveal.ID, GradePayloads.Reveal.CODEC);
        Net.INSTANCE.receive(GradePayloads.Action.ID, (p, player) -> player.server.execute(() ->
                dev.tcgshop.grading.GradingService.handle(player, p)));
        ServerEvents.TICK_END.register(dev.tcgshop.grading.GradingService::tick);
        Net.INSTANCE.c2s(RouletteSpinPayload.ID, RouletteSpinPayload.CODEC);
        Net.INSTANCE.c2s(DealAnswerPayload.ID, DealAnswerPayload.CODEC);
        Net.INSTANCE.c2s(dev.tcgshop.network.UiPayloads.Act.ID, dev.tcgshop.network.UiPayloads.Act.CODEC);
        Net.INSTANCE.s2c(dev.tcgshop.network.UiPayloads.Open.ID, dev.tcgshop.network.UiPayloads.Open.CODEC);
        Net.INSTANCE.receive(dev.tcgshop.network.UiPayloads.Act.ID, (p, player) -> player.server.execute(() -> {
            ServerPlayer pl = player;
            switch (p.kind()) {
                case "slot" -> dev.tcgshop.shop.ShopServer.slotAction(pl, p.data());
                case "binder_open" -> dev.tcgshop.shop.SocialServer.openBinderMarket(pl, false);
                case "shop_collect" -> {
                    if (!p.data().hasUUID("zone")) break;
                    java.util.UUID zid = p.data().getUUID("zone");
                    for (var w : pl.getServer().getAllLevels()) {
                        var z = dev.tcgshop.shop.ShopZones.get(w).byIdAny(zid); // 1.1: a dormant shop's till too
                        if (z != null && z.owner.equals(pl.getUUID())) {
                            dev.tcgshop.shop.ShopServer.collect(pl, z, p.data().getBoolean("keep") ? dev.tcgshop.shop.ShopServer.dailyWages(z) * 3 : 0);
                            break;
                        }
                    }
                }
                case "shop_withdraw", "shop_deposit" -> { // 1.1: register window (block or phone): withdraw by % / amount, deposit
                    if (!p.data().hasUUID("zone")) break;
                    java.util.UUID zid = p.data().getUUID("zone");
                    for (var w : pl.getServer().getAllLevels()) {
                        var z = dev.tcgshop.shop.ShopZones.get(w).byIdAny(zid); // a dormant shop's till too (withdraw only)
                        if (z != null && z.owner.equals(pl.getUUID())) {
                            if (p.kind().equals("shop_withdraw")) dev.tcgshop.shop.ShopServer.withdraw(pl, z, p.data().getInt("pct"), p.data().getLong("amount"));
                            else dev.tcgshop.shop.ShopServer.deposit(pl, z, p.data().getLong("amount"));
                            break;
                        }
                    }
                }
                case "shop_open_toggle" -> { // 1.1: owner opens / closes a shop (phone, status window)
                    if (p.data().hasUUID("zone")) dev.tcgshop.shop.ShopOpenClose.toggle(pl, p.data().getUUID("zone"));
                }
                case "shop_abandon" -> { // 1.1: owner gives up a dormant shop (phone, after a confirm click)
                    if (p.data().hasUUID("zone")) dev.tcgshop.shop.ShopOpenClose.abandon(pl, p.data().getUUID("zone"));
                }
                case "binder" -> dev.tcgshop.shop.SocialServer.binderAction(pl, p.data());
                case "friends" -> dev.tcgshop.shop.SocialServer.friendsAction(pl, p.data());
                case "displays" -> {
                    // 0.9: remote display management = phone Displays app (with minimum level)
                    if (!dev.tcgshop.phone.PhoneUpgrades.requireApp(pl, "displays")) break;
                    if (p.data().getString("a").equals("list")) dev.tcgshop.shop.SocialServer.sendDisplays(pl);
                    else dev.tcgshop.shop.SocialServer.displaysAction(pl, p.data());
                }
                case "beggar" -> {
                    if (pl.level().getEntity(p.data().getInt("id")) instanceof dev.tcgshop.entity.BeggarEntity b) b.answer(pl, p.data().getString("action"));
                }
                case "unique_collector" -> {
                    if (pl.level().getEntity(p.data().getInt("id")) instanceof dev.tcgshop.entity.UniqueCollectorEntity u) u.answer(pl, p.data().getString("action"));
                }
                case "duelist" -> { // 1.0 Wandering Duelist: stakes window answer
                    if (pl.level().getEntity(p.data().getInt("id")) instanceof dev.tcgshop.entity.WanderingDuelistEntity d) d.answer(pl, p.data().getString("action"));
                }
                case "mayor" -> dev.tcgshop.npc.TaxOffice.answer(pl, p.data()); // 1.1: pay or refuse the Mayor's taxes
                case "black_market" -> {
                    if (pl.level().getEntity(p.data().getInt("id")) instanceof dev.tcgshop.entity.BlackMarketEntity m && p.data().hasUUID("mon"))
                        m.trade(pl, p.data().getUUID("mon"));
                }
                default -> {}
            }
        }));
        Net.INSTANCE.c2s(StaffPayload.ID, StaffPayload.CODEC);
        Net.INSTANCE.receive(RouletteSpinPayload.ID, (p, player) -> player.server.execute(() -> dev.tcgshop.shop.Roulette.spin(p, player)));
        Net.INSTANCE.receive(StaffPayload.ID, (p, player) -> player.server.execute(() ->
                dev.tcgshop.shop.ShopServer.manageStaff(player, p.zone(), p.role(), p.hire())));
        Net.INSTANCE.receive(DealAnswerPayload.ID, (p, player) -> player.server.execute(() -> {
            if (player.level().getEntity(p.entityId()) instanceof dev.tcgshop.entity.CustomerEntity c && c.distanceToSqr(player) < 12 * 12)
                c.respond(player, p.action(), p.counter());
        }));
        dev.tcgshop.shop.ShopLife.register();
        dev.tcgshop.shop.GuardUpgrades.register(); // 1.0 beta 3: /tcgguard (clickable guard upgrade menu)
        dev.tcgshop.entity.CollectorTheft.register(); // 1.0 beta 3: stealing from the Wandering Collector
        dev.tcgshop.compat.CobblemonBattles.register();
        dev.tcgshop.shop.PackLock.register();
        dev.tcgshop.shop.ChestLoot.register();
        dev.tcgshop.compat.NpcPokemonDuel.register();
        dev.tcgshop.npc.TaxOffice.register(); // 1.1: Mayor + taxes (config/tcgshop-npcs.json)
        dev.tcgshop.compat.MayorBattle.register(); // 1.1: the Mayor's real Cobblemon trainer battle
        dev.tcgshop.entity.CollectorHunt.register(); // 1.1: robbed collectors follow thieves who teleport
        // TCG competitive mode
        dev.tcgshop.ptcg.PtcgDuels.register();
        dev.tcgshop.ptcg.PlayTables.register(); // 1.0: chairs requirement, customer challenges and exhibitions
        dev.tcgshop.world.duelist.WanderingDuelists.register(); // 1.0: Wandering Duelist encounters (high-stakes Hard match)
        dev.tcgshop.luxury.StatuePacks.register(); // 1.0: customers drop packs near tier 3+ statues
        dev.tcgshop.shop.Discounts.register(); // 1.0: store sale event (+ its sync payload)
        dev.tcgshop.shop.ShopOpenClose.register(); // 1.1: a removed core always drops its one linked core
        dev.tcgshop.casino.Casino.register(); // 1.0 beta 3: casino (config/tcgshop-casino.json, payloads, blackjack sessions)
        Net.INSTANCE.s2c(PtcgPayloads.View.ID, PtcgPayloads.View.CODEC);
        Net.INSTANCE.c2s(PtcgPayloads.Action.ID, PtcgPayloads.Action.CODEC);
        Net.INSTANCE.s2c(PtcgPayloads.League.ID, PtcgPayloads.League.CODEC);
        Net.INSTANCE.c2s(PtcgPayloads.LeagueAction.ID, PtcgPayloads.LeagueAction.CODEC);
        Net.INSTANCE.receive(PtcgPayloads.Action.ID, (p, player) -> player.server.execute(() ->
                dev.tcgshop.ptcg.PtcgDuels.action(player, p.json())));
        Net.INSTANCE.receive(PtcgPayloads.LeagueAction.ID, (p, player) -> player.server.execute(() -> {
            // 0.9: League-app-exclusive features (ranked, queue, high AI) require its phone level
            if (dev.tcgshop.phone.PhoneUpgrades.leagueActionAllowed(player, p.json()))
                dev.tcgshop.ptcg.PtcgDuels.league(player, p.json());
        }));
        // card trading between players
        Net.INSTANCE.c2s(dev.tcgshop.network.TradePayloads.Request.ID, dev.tcgshop.network.TradePayloads.Request.CODEC);
        Net.INSTANCE.c2s(dev.tcgshop.network.TradePayloads.Select.ID, dev.tcgshop.network.TradePayloads.Select.CODEC);
        Net.INSTANCE.c2s(dev.tcgshop.network.TradePayloads.Answer.ID, dev.tcgshop.network.TradePayloads.Answer.CODEC);
        Net.INSTANCE.s2c(dev.tcgshop.network.TradePayloads.Open.ID, dev.tcgshop.network.TradePayloads.Open.CODEC);
        Net.INSTANCE.s2c(dev.tcgshop.network.TradePayloads.State.ID, dev.tcgshop.network.TradePayloads.State.CODEC);
        Net.INSTANCE.s2c(dev.tcgshop.network.TradePayloads.Done.ID, dev.tcgshop.network.TradePayloads.Done.CODEC);
        Net.INSTANCE.receive(dev.tcgshop.network.TradePayloads.Request.ID, (p, player) -> player.server.execute(() ->
                dev.tcgshop.trade.TradeManager.request(player, p.target())));
        Net.INSTANCE.receive(dev.tcgshop.network.TradePayloads.Select.ID, (p, player) -> player.server.execute(() ->
                dev.tcgshop.trade.TradeManager.select(player, p.slot())));
        Net.INSTANCE.receive(dev.tcgshop.network.TradePayloads.Answer.ID, (p, player) -> player.server.execute(() ->
                dev.tcgshop.trade.TradeManager.answer(player, p.confirm())));
        ServerEvents.TICK_END.register(dev.tcgshop.trade.TradeManager::tick);
        PlayerEvents.LEAVE.register(player -> dev.tcgshop.trade.TradeManager.disconnect(player));
        Net.INSTANCE.c2s(StoreBuyPayload.ID, StoreBuyPayload.CODEC);
        // furniture shop (CobbleFurnies / plushies): Furniture Seller, Wholesaler and phone
        Net.INSTANCE.c2s(dev.tcgshop.network.FurniturePayloads.Request.ID, dev.tcgshop.network.FurniturePayloads.Request.CODEC);
        Net.INSTANCE.c2s(dev.tcgshop.network.FurniturePayloads.Trade.ID, dev.tcgshop.network.FurniturePayloads.Trade.CODEC);
        Net.INSTANCE.s2c(dev.tcgshop.network.FurniturePayloads.Open.ID, dev.tcgshop.network.FurniturePayloads.Open.CODEC);
        // 0.9: source -1 = phone Furniture app (with minimum level); the Seller and the Wholesaler are not locked
        Net.INSTANCE.receive(dev.tcgshop.network.FurniturePayloads.Request.ID, (p, player) -> player.server.execute(() -> {
            if (p.source() == -1 && !dev.tcgshop.phone.PhoneUpgrades.requireApp(player, "furniture")) return;
            dev.tcgshop.shop.FurnitureShop.handleRequest(player, p);
        }));
        Net.INSTANCE.receive(dev.tcgshop.network.FurniturePayloads.Trade.ID, (p, player) -> player.server.execute(() -> {
            if (p.source() == -1 && !dev.tcgshop.phone.PhoneUpgrades.requireApp(player, "furniture")) return;
            if (p.source() == -1 && !p.sell() && dev.tcgshop.phone.Loans.blocksPhonePurchase(player)) return; // 1.0: active loan
            dev.tcgshop.shop.FurnitureShop.handleTrade(player, p);
        }));
        // quick-sell of cards / emeralds and wandering Collector
        Net.INSTANCE.c2s(dev.tcgshop.network.MarketPayloads.Sell.ID, dev.tcgshop.network.MarketPayloads.Sell.CODEC);
        Net.INSTANCE.c2s(dev.tcgshop.network.MarketPayloads.TraderAction.ID, dev.tcgshop.network.MarketPayloads.TraderAction.CODEC);
        Net.INSTANCE.s2c(dev.tcgshop.network.MarketPayloads.TraderOpen.ID, dev.tcgshop.network.MarketPayloads.TraderOpen.CODEC);
        Net.INSTANCE.receive(dev.tcgshop.network.MarketPayloads.Sell.ID, (p, player) -> player.server.execute(() ->
                dev.tcgshop.shop.CardSales.handle(player, p)));
        Net.INSTANCE.receive(dev.tcgshop.network.MarketPayloads.TraderAction.ID, (p, player) -> player.server.execute(() -> {
            if (player.level().getEntity(p.entity()) instanceof dev.tcgshop.entity.TraderEntity tr) tr.action(player, p);
        }));
        // 1.0: several of your cards for one of the wandering Collector's cards
        Net.INSTANCE.c2s(dev.tcgshop.network.CollectorOfferPayload.ID, dev.tcgshop.network.CollectorOfferPayload.CODEC);
        Net.INSTANCE.receive(dev.tcgshop.network.CollectorOfferPayload.ID, (p, player) -> player.server.execute(() -> {
            if (player.level().getEntity(p.entity()) instanceof dev.tcgshop.entity.TraderEntity tr) tr.offer(player, p.indices(), p.slots(), p.talk());
        }));
        Net.INSTANCE.receive(ShopActionPayload.ID, (p, player) -> player.server.execute(() -> dev.tcgshop.shop.ShopServer.handleAction(p, player)));
        Net.INSTANCE.receive(StoreBuyPayload.ID, (p, player) -> player.server.execute(() -> {
            ServerPlayer pl = player;
            if (p.source() == -1) {
                if (pl.getInventory().contains(st -> st.is(dev.tcgshop.item.TcgItems.TCG_PHONE))) // 1.0: PhoneStore adds the result notice
                    dev.tcgshop.phone.PhoneStore.buy(p, pl, dev.tcgshop.shop.ShopServer.phoneCatalog(), dev.tcgshop.shop.ShopServer.PHONE_FACTOR);
            } else if (pl.level().getEntity(p.source()) instanceof dev.tcgshop.entity.WholesalerEntity w && w.distanceToSqr(pl) < 10 * 10) {
                dev.tcgshop.phone.PhoneStore.buy(p, pl, w.goods(), dev.tcgshop.shop.ShopServer.WHOLESALE_FACTOR);
            } else if (pl.level().getEntity(p.source()) instanceof dev.tcgshop.entity.EngineerEntity en) { // 1.1: CREATive Engineer
                en.buy(pl, p.index(), p.qty());
            }
        }));
        PlayerEvents.RESPAWN.register((o, n, alive) -> dev.tcgshop.shop.ShopServer.syncZones(n));
        dev.masterball.api.platform.event.PlayerEvents.CHANGE_LEVEL.register((pl, from, to) -> dev.tcgshop.shop.ShopServer.syncZones(pl));
        // network: client -> server
        Net.INSTANCE.c2s(OpenGroupPayload.ID, OpenGroupPayload.CODEC);
        Net.INSTANCE.c2s(GroupCutPayload.ID, GroupCutPayload.CODEC);
        Net.INSTANCE.c2s(BinderActionPayload.ID, BinderActionPayload.CODEC);
        Net.INSTANCE.c2s(ScalperAnswerPayload.ID, ScalperAnswerPayload.CODEC);
        Net.INSTANCE.receive(OpenGroupPayload.ID, (p, player) -> player.server.execute(() -> PackOpener.onOpenGroup(p, player)));
        Net.INSTANCE.receive(GroupCutPayload.ID, (p, player) -> player.server.execute(() -> PackOpener.onCut(p, player)));
        Net.INSTANCE.receive(BinderActionPayload.ID, (p, player) -> player.server.execute(() -> BinderServer.handle(p, player)));
        Net.INSTANCE.receive(ScalperAnswerPayload.ID, (p, player) -> player.server.execute(() -> {
            if (player.level().getEntity(p.entityId()) instanceof ScalperEntity s && s.distanceToSqr(player) < 16 * 16) {
                s.answer(player, p.accept());
            }
        }));
        PlayerEvents.LEAVE.register(player -> PackOpener.onDisconnect(player));
        PlayerEvents.LEAVE.register(player -> dev.tcgshop.shop.SocialServer.snapshot(player));
        dev.tcgshop.phone.PhoneUpgrades.register(); // 0.9: phone upgrades (network, migration on join, config/tcgshop-phone.json)
        dev.tcgshop.binder.BinderKeep.register(); // 1.1: the mod's binders never drop on death (given back on respawn)
        // 0.9: random events (core + incident and actor modules; the modules register their effect types here)
        dev.tcgshop.events.EventsConfig.load();
        dev.tcgshop.events.EventManager.register();
        dev.tcgshop.events.incident.IncidentModule.init();
        dev.tcgshop.events.actor.ActorModule.init();

        Platform.INSTANCE.serverReloadListener(ResourceLocation.fromNamespaceAndPath(MOD_ID, "prices"), new PriceService.ReloadListener());
        Platform.INSTANCE.serverReloadListener(ResourceLocation.fromNamespaceAndPath(MOD_ID, "species"), new dev.tcgshop.collection.Titles.ReloadListener());
        Platform.INSTANCE.serverReloadListener(ResourceLocation.fromNamespaceAndPath(MOD_ID, "ptcg_cards"), new dev.tcgshop.ptcg.PtcgCards.ReloadListener());
        Platform.INSTANCE.serverReloadListener(ResourceLocation.fromNamespaceAndPath(MOD_ID, "events"), new dev.tcgshop.events.EventRegistry());

        PlayerEvents.JOIN.register(player -> {
            syncPrices(player);
            player.server.execute(() -> {
                dev.tcgshop.collection.Titles.broadcast(player.server);
                dev.tcgshop.shop.ShopServer.syncZones(player);
                dev.tcgshop.onboarding.Onboarding.onJoin(player);
                if (TcgShopConfig.lastError != null && player.hasPermissions(2))
                    player.displayClientMessage(Component.literal("[MasterBall Traders Co.] config/tcgshop.json has an error; the default values are being used: " + TcgShopConfig.lastError).withStyle(ChatFormatting.RED), false);
            });
        });
        ServerEvents.DATAPACK_RELOADED.register((server, ok) -> {
            CardAdvancements.invalidate();
            syncAll(server);
        });
        ServerEvents.STARTED.register(server -> {
            PriceService.applyConfig();
            PriceService.setMarketDay(currentMarketDay(server));
        });
        ServerEvents.TICK_END.register(TcgShop::tickMarket);

        ServerEvents.COMMANDS.register((dispatcher, access, env) -> registerCommands(dispatcher));
        LOGGER.info("MasterBall Traders Co. loaded");
    }

    public static void syncPrices(ServerPlayer player) {
        if (Net.INSTANCE.canSend(player, PriceSyncPayload.ID)) {
            Net.INSTANCE.send(player, new PriceSyncPayload(
                    PriceService.cardUsdView(), PriceService.sealedUsdView(), PriceService.rarityMedianView(), PriceService.gradedUsdView(),
                    new PriceSyncPayload.Market(PriceService.cdPerUsd(), PriceService.marketSeed(),
                            PriceService.marketVolatility(), PriceService.marketDay()), EconomyConfig.toJson()));
        }
    }

    /** Market day = total world time / configured length (not affected by /time set). */
    public static long currentMarketDay(MinecraftServer server) {
        return server.overworld().getGameTime() / TcgShopConfig.INSTANCE.marketDayTicks;
    }

    private static void tickMarket(MinecraftServer server) {
        if (server.getTickCount() % 100 != 0) return;
        long day = currentMarketDay(server);
        if (day != PriceService.marketDay()) {
            PriceService.setMarketDay(day);
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (Net.INSTANCE.canSend(p, MarketDayPayload.ID)) Net.INSTANCE.send(p, new MarketDayPayload(day));
            }
        }
    }

    private static void syncAll(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) syncPrices(p);
    }

    private static void registerCommands(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("tcgduel")
                .then(Commands.literal("accept").executes(ctx -> { dev.tcgshop.ptcg.PtcgDuels.answerChallenge(ctx.getSource().getPlayerOrException(), true); return 1; }))
                .then(Commands.literal("decline").executes(ctx -> { dev.tcgshop.ptcg.PtcgDuels.answerChallenge(ctx.getSource().getPlayerOrException(), false); return 1; }))
                .then(Commands.argument("target", net.minecraft.commands.arguments.EntityArgument.player())
                        .executes(ctx -> { dev.tcgshop.ptcg.PtcgDuels.challenge(ctx.getSource().getPlayerOrException(),
                                net.minecraft.commands.arguments.EntityArgument.getPlayer(ctx, "target")); return 1; })));
        d.register(Commands.literal("tcgtrade")
                .then(Commands.literal("accept").then(Commands.argument("player", com.mojang.brigadier.arguments.StringArgumentType.word())
                        .executes(ctx -> { dev.tcgshop.trade.TradeManager.respond(ctx.getSource().getPlayerOrException(),
                                com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "player"), true); return 1; })))
                .then(Commands.literal("deny").then(Commands.argument("player", com.mojang.brigadier.arguments.StringArgumentType.word())
                        .executes(ctx -> { dev.tcgshop.trade.TradeManager.respond(ctx.getSource().getPlayerOrException(),
                                com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "player"), false); return 1; })))
                .then(Commands.argument("target", net.minecraft.commands.arguments.EntityArgument.player())
                        .executes(ctx -> { dev.tcgshop.trade.TradeManager.request(ctx.getSource().getPlayerOrException(),
                                net.minecraft.commands.arguments.EntityArgument.getPlayer(ctx, "target").getUUID()); return 1; })));
        d.register(Commands.literal("tcgshop")
                .then(Commands.literal("tutorial").executes(ctx -> dev.tcgshop.onboarding.Onboarding.replay(ctx.getSource().getPlayerOrException())))
                .then(dev.tcgshop.command.CardCommands.node())
                .then(dev.tcgshop.events.EventCommands.node())
                .then(Commands.literal("offer")
                        .then(Commands.literal("accept").then(Commands.argument("id", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0))
                                .executes(ctx -> { dev.tcgshop.shop.SocialServer.answerOffer(ctx.getSource().getPlayerOrException(),
                                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "id"), true); return 1; })))
                        .then(Commands.literal("deny").then(Commands.argument("id", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0))
                                .executes(ctx -> { dev.tcgshop.shop.SocialServer.answerOffer(ctx.getSource().getPlayerOrException(),
                                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "id"), false); return 1; }))))
                .then(Commands.literal("reload").requires(src -> src.hasPermission(2)).executes(ctx -> {
                    // rereads config/tcgshop.json and config/tcgshop-economy.json without restarting
                    MinecraftServer server = ctx.getSource().getServer();
                    TcgShopConfig.load();
                    if (TcgShopConfig.lastError != null) {
                        ctx.getSource().sendFailure(Component.literal("config/tcgshop.json has an error and was not applied: " + TcgShopConfig.lastError));
                        return 0;
                    }
                    PriceService.applyConfig();
                    PriceService.setMarketDay(currentMarketDay(server));
                    dev.tcgshop.shop.VanillaPrices.load();
                    EconomyConfig.load();
                    dev.tcgshop.luxury.LuxuryConfig.load();
                    dev.tcgshop.world.WorldLootConfig.load(); // 1.0 trash cans / Bankrupt Card Shop
                    syncAll(server);
                    dev.tcgshop.phone.PhoneConfig.load();
                    for (ServerPlayer pl : server.getPlayerList().getPlayers()) dev.tcgshop.phone.PhoneUpgrades.sync(pl, null, null);
                    dev.tcgshop.events.EventsConfig.load(); // 0.9: config/tcgshop-events.json
                    dev.tcgshop.events.actor.ActorConfig.load();
                    dev.tcgshop.casino.CasinoConfig.load(); // 1.0 beta 3: config/tcgshop-casino.json
                    dev.tcgshop.shop.CleanConfig.load(); // 1.1: config/tcgshop-clean.json
                    dev.tcgshop.npc.NpcConfig.load(); // 1.1: config/tcgshop-npcs.json (Mayor, collectors, Engineer)
                    ctx.getSource().sendSuccess(() -> Component.translatable("tcgshop.cmd.reloaded").withStyle(ChatFormatting.GREEN), true);
                    return 1;
                }))
                .then(Commands.literal("debug").requires(src -> src.hasPermission(2))
                        .then(Commands.literal("phasebtest").executes(ctx -> {
                            // display with per-card look (save/load), cashier shift and spawning of the new NPCs
                            var world = ctx.getSource().getServer().overworld();
                            var base = world.getSharedSpawnPos().above(3).east(6);
                            try {
                                world.setBlockAndUpdate(base, dev.tcgshop.shop.TcgBlocks.CARD_STAND.defaultBlockState());
                                if (world.getBlockEntity(base) instanceof dev.tcgshop.shop.ShopDisplayBlockEntity be) {
                                    ItemStack card = ItemStack.EMPTY;
                                    for (var id : net.minecraft.core.registries.BuiltInRegistries.ITEM.keySet()) if (CardInfo.isCard(id)) { card = new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id)); break; }
                                    be.setItem(0, card);
                                    be.setLook(0, 1.35f, 10, -45, 200);
                                    var nbt = be.saveWithoutMetadata(world.registryAccess());
                                    be.loadWithComponents(nbt, world.registryAccess());
                                    boolean ok = Math.abs(be.lookScale(0) - 1.35f) < 0.001f && be.lookRot(0, 1) == -45 && be.lookRot(0, 2) == -160;
                                    be.setItem(0, ItemStack.EMPTY);
                                    boolean reset = !be.hasLook(0);
                                    LOGGER.info("[phasebtest] per-card look: saved {} · restored on removal {} {}", ok, reset, ok && reset ? "OK" : "FAIL");
                                } else LOGGER.info("[phasebtest] per-card look: FAIL (no block entity)");
                            } catch (Exception ex) { LOGGER.error("[phasebtest] look FAIL", ex); }
                            long tod = world.getDayTime();
                            world.setDayTime(1000);
                            boolean d1 = dev.tcgshop.shop.ShopServer.isDayShift(world);
                            world.setDayTime(14000);
                            boolean d2 = dev.tcgshop.shop.ShopServer.isDayShift(world);
                            world.setDayTime(tod);
                            LOGGER.info("[phasebtest] day shift: 7:00 {} · 20:00 {} {}", d1, d2, d1 && !d2 ? "OK" : "FAIL");
                            try {
                                var b = java.util.Objects.requireNonNull(dev.tcgshop.entity.TcgEntities.BEGGAR.create(world));
                                b.moveTo(base.getX() + 2.5, base.getY(), base.getZ() + 0.5, 0, 0);
                                world.addFreshEntity(b);
                                var m = java.util.Objects.requireNonNull(dev.tcgshop.entity.TcgEntities.BLACK_MARKET.create(world));
                                m.moveTo(base.getX() + 4.5, base.getY(), base.getZ() + 0.5, 0, 0);
                                m.rollOffer();
                                world.addFreshEntity(m);
                                var nbt = new net.minecraft.nbt.CompoundTag();
                                m.saveWithoutId(nbt);
                                m.load(nbt);
                                var nbt2 = new net.minecraft.nbt.CompoundTag();
                                b.saveWithoutId(nbt2);
                                b.load(nbt2);
                                LOGGER.info("[phasebtest] beggar and black-market dealer created and saved (pack: {}) OK", nbt.contains("Offer"));
                                // social data: save and load (favorites, history, friends)
                                var sd = dev.tcgshop.shop.SocialServer.Data.get(ctx.getSource().getServer());
                                var root = sd.save(new net.minecraft.nbt.CompoundTag(), world.registryAccess());
                                LOGGER.info("[phasebtest] social data saved ({} players) OK", root.getCompound("players").getAllKeys().size());
                            } catch (Exception ex) { LOGGER.error("[phasebtest] NPC FAIL", ex); }
                            return 1;
                        }))
                        .then(Commands.literal("eventtest").executes(ctx -> dev.tcgshop.events.EventSelfTest.run(ctx.getSource())))
                        .then(Commands.literal("econtest").executes(ctx -> {
                            // vanilla table, locked sets and ultra-rare chest loot (forcing the chance)
                            var world = ctx.getSource().getServer().overworld();
                            for (String id : new String[]{"oak_log", "oak_planks", "oak_sapling", "dirt", "iron_ingot", "copper_ingot", "diamond", "emerald",
                                    "netherite_ingot", "nether_star", "diamond_pickaxe", "rotten_flesh", "wheat", "spawner", "pig_spawn_egg"})
                                LOGGER.info("[econtest] vanilla {} = {}", id, dev.tcgshop.shop.VanillaPrices.price(new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.withDefaultNamespace(id)))));
                            LOGGER.info("[econtest] vanilla: {} items with a price, max {}", dev.tcgshop.shop.VanillaPrices.table().values().stream().filter(v -> v > 0).count(),
                                    dev.tcgshop.shop.VanillaPrices.table().values().stream().mapToDouble(Double::doubleValue).max().orElse(0));
                            LOGGER.info("[econtest] locked {} · minimum shop {}", dev.tcgshop.shop.PackLock.lockedSets(), dev.tcgshop.shop.PackLock.minTier());
                            var table = ctx.getSource().getServer().reloadableRegistries().getLootTable(net.minecraft.world.level.storage.loot.BuiltInLootTables.SIMPLE_DUNGEON);
                            double old2 = EconomyConfig.INSTANCE.lockedPackChestChance;
                            int locked = 0, unlockedRare = 0;
                            try {
                                EconomyConfig.INSTANCE.lockedPackChestChance = 1.0;
                                for (int k = 0; k < 200; k++) {
                                    var params = new net.minecraft.world.level.storage.loot.LootParams.Builder(world)
                                            .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN, world.getSharedSpawnPos().getCenter())
                                            .create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.CHEST);
                                    for (ItemStack st : table.getRandomItems(params))
                                        if (CardInfo.isPack(st)) { if (dev.tcgshop.shop.PackLock.locked(st)) locked++; else unlockedRare++; }
                                }
                            } finally {
                                EconomyConfig.INSTANCE.lockedPackChestChance = old2;
                            }
                            LOGGER.info("[econtest] with 100% chance: {} locked packs in 200 chests (normal: {}) {}", locked, unlockedRare, locked >= 190 ? "OK" : "FAIL");
                            int real = 0;
                            for (int k = 0; k < 5000; k++) {
                                var params = new net.minecraft.world.level.storage.loot.LootParams.Builder(world)
                                        .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN, world.getSharedSpawnPos().getCenter())
                                        .create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.CHEST);
                                for (ItemStack st : table.getRandomItems(params)) if (CardInfo.isPack(st) && dev.tcgshop.shop.PackLock.locked(st)) real++;
                            }
                            LOGGER.info("[econtest] with {}: {} locked packs in 5000 chests {}", old2, real, real <= 2 ? "OK" : "FAIL");
                            return 1;
                        }))
                        .then(dev.tcgshop.luxury.house.MansionService.debugNode()) // 0.9.5: /tcgshop debug mansion [force|dry]
                        .then(dev.tcgshop.npc.NpcSelfTest.debugMayor()) // 1.1: /tcgshop debug mayor (your taxes fall due now)
                        .then(dev.tcgshop.npc.NpcSelfTest.debugEngineer()) // 1.1: /tcgshop debug engineer (needs Create)
                        .then(Commands.literal("selftest").executes(ctx -> {
                            // creates shop entities and blocks in the Overworld and logs any error
                            var world = ctx.getSource().getServer().overworld();
                            var base = world.getSharedSpawnPos().above(2);
                            String[] steps = {"scalper", "wholesaler", "trader", "display_case", "booster_box", "multiblock", "customer", "scalper_brawl", "grading", "rarity", "masterball_api", "duel", "chest_loot", "box_odds", "home_display", "display_slots", "mansion", "shop_close", "progression", "staff", "customers", "cleanliness", "npcs", "phone_systems", "loader_parity", "shop_core_wood"};
                            for (String step : steps) {
                                try {
                                    switch (step) {
                                        case "scalper" -> world.addFreshEntity(java.util.Objects.requireNonNull(TcgEntities.SCALPER.create(world)));
                                        case "wholesaler" -> world.addFreshEntity(java.util.Objects.requireNonNull(TcgEntities.WHOLESALER.create(world)));
                                        case "trader" -> {
                                            var tr = java.util.Objects.requireNonNull(TcgEntities.TRADER.create(world));
                                            tr.moveTo(base.getX() + 0.5, base.getY(), base.getZ() + 2.5, 0, 0);
                                            world.addFreshEntity(tr);
                                            var nbt = new net.minecraft.nbt.CompoundTag();
                                            tr.saveWithoutId(nbt); // forces the stock and tests saving
                                            tr.load(nbt);
                                        }
                                        case "display_case" -> world.setBlockAndUpdate(base, dev.tcgshop.shop.TcgBlocks.DISPLAY_CASE.defaultBlockState());
                                        case "booster_box" -> world.setBlockAndUpdate(base.east(), dev.tcgshop.shop.TcgBlocks.BOOSTER_BOX.defaultBlockState());
                                        case "multiblock" -> {
                                            // places each large furniture piece (all its parts), checks the inventory and breaks it via any part
                                            int dx = 6;
                                            for (dev.tcgshop.shop.ShopDisplayBlock b : new dev.tcgshop.shop.ShopDisplayBlock[]{dev.tcgshop.shop.TcgBlocks.CARD_STAND,
                                                    dev.tcgshop.shop.TcgBlocks.DISPLAY_CASE, dev.tcgshop.shop.TcgBlocks.PACK_SHELF, dev.tcgshop.shop.TcgBlocks.GONDOLA,
                                                    dev.tcgshop.shop.TcgBlocks.BOX_PALLET, dev.tcgshop.shop.TcgBlocks.PEDESTAL, dev.tcgshop.shop.TcgBlocks.CARD_WALL,
                                                    dev.tcgshop.shop.TcgBlocks.COUNTER_DISPLAY, dev.tcgshop.shop.TcgBlocks.SHOWCASE, dev.tcgshop.shop.TcgBlocks.CARD_LEDGE,
                                                    dev.tcgshop.shop.TcgBlocks.BOX_SHELF, dev.tcgshop.shop.TcgBlocks.CARD_FRAME, dev.tcgshop.shop.TcgBlocks.HOME_FRAME,
                                                    dev.tcgshop.shop.TcgBlocks.HOME_LEDGE, dev.tcgshop.shop.TcgBlocks.HOME_SHOWCASE, dev.tcgshop.shop.TcgBlocks.HOME_CARD_WALL,
                                                    dev.tcgshop.shop.TcgBlocks.HOME_PEDESTAL}) {
                                                for (net.minecraft.core.Direction f : new net.minecraft.core.Direction[]{net.minecraft.core.Direction.NORTH,
                                                        net.minecraft.core.Direction.EAST, net.minecraft.core.Direction.SOUTH, net.minecraft.core.Direction.WEST}) {
                                                    var master = base.offset(dx, 4, 0);
                                                    dx += 5;
                                                    var st = b.defaultBlockState().setValue(dev.tcgshop.shop.ShopDisplayBlock.FACING, f);
                                                    world.setBlockAndUpdate(master, st);
                                                    b.setPlacedBy(world, master, st, null, net.minecraft.world.item.ItemStack.EMPTY);
                                                    int ok = 0;
                                                    for (int p = 0; p < b.kind.parts(); p++) {
                                                        var ps = world.getBlockState(b.kind.partPos(master, f, p));
                                                        if (ps.is(b) && ps.getValue(dev.tcgshop.shop.ShopDisplayBlock.PART) == p && b.master(ps, b.kind.partPos(master, f, p)).equals(master)) ok++;
                                                    }
                                                    if (ok != b.kind.parts()) throw new IllegalStateException(b.kind + " " + f + ": parts " + ok + "/" + b.kind.parts());
                                                    if (!(world.getBlockEntity(master) instanceof dev.tcgshop.shop.ShopDisplayBlockEntity be) || be.getContainerSize() != b.kind.slots.length)
                                                        throw new IllegalStateException(b.kind + ": master inventory");
                                                    var last = b.kind.partPos(master, f, b.kind.parts() - 1);
                                                    world.destroyBlock(last, true);
                                                    for (int p = 0; p < b.kind.parts(); p++)
                                                        if (world.getBlockState(b.kind.partPos(master, f, p)).is(b)) throw new IllegalStateException(b.kind + " " + f + ": part left over " + p);
                                                }
                                            }
                                        }
                                        case "customer" -> {
                                            var c = java.util.Objects.requireNonNull(TcgEntities.CUSTOMER.create(world));
                                            c.moveTo(base.getX() + 0.5, base.getY(), base.getZ() + 2.5, 0, 0);
                                            world.addFreshEntity(c); // no shop: leaves on its own on the next tick
                                        }
                                        case "grading" -> {
                                            // grade distribution (100,000 rolls), multipliers and price of a graded card
                                            var rnd = net.minecraft.util.RandomSource.create(42);
                                            int[] hist = new int[101];
                                            for (int k = 0; k < 100_000; k++) hist[dev.tcgshop.grading.Grading.roll(rnd)]++;
                                            for (var band : dev.tcgshop.grading.Grading.ODDS) {
                                                int n = 0;
                                                for (int g = band.from(); g <= band.to(); g++) n += hist[g];
                                                double pct = n / 1000.0;
                                                if (Math.abs(pct - band.percent()) > 0.6) throw new IllegalStateException("band " + band + " came out " + pct + "%");
                                            }
                                            if (hist[0] + java.util.Arrays.stream(hist, 1, 10).sum() > 0) throw new IllegalStateException("grades below 1.0");
                                            double prev = 0;
                                            for (int g = 10; g <= 100; g++) {
                                                double mlt = dev.tcgshop.grading.Grading.multiplier(g);
                                                if (mlt < prev) throw new IllegalStateException("non-increasing multiplier at " + g);
                                                prev = mlt;
                                            }
                                            net.minecraft.world.item.Item cardItem = null;
                                            for (var id : net.minecraft.core.registries.BuiltInRegistries.ITEM.keySet())
                                                if (CardInfo.isCard(id)) { cardItem = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id); break; }
                                            if (cardItem != null) {
                                                ItemStack raw = new ItemStack(cardItem);
                                                ItemStack g10 = dev.tcgshop.grading.Grading.apply(raw, 100, 1), g10b = dev.tcgshop.grading.Grading.apply(raw, 10, 2);
                                                long pr = PriceService.priceCd(raw), p10 = PriceService.priceCd(g10), p1 = PriceService.priceCd(g10b);
                                                LOGGER.info("[selftest] grading: {} raw={} Grade10={} Grade1={} hist10={}% 9.5+={}%", raw.getHoverName().getString(), pr, p10, p1,
                                                        hist[100] / 1000.0, java.util.Arrays.stream(hist, 95, 101).sum() / 1000.0);
                                                if (pr > 0 && !(p10 >= pr && p1 < pr)) throw new IllegalStateException("Grade 1 must be worth less than the base price and Grade 10 at least that");
                                                if (dev.tcgshop.pricing.GradeModel.dataSize() < 7000) throw new IllegalStateException("missing Grade data");
                                                if (Math.abs(dev.tcgshop.pricing.GradeModel.popularity(1) - 1.0) > 1e-9 || dev.tcgshop.pricing.GradeModel.popularity(1025) != 0
                                                        || dev.tcgshop.pricing.GradeModel.popularity(300) < 0.65 || dev.tcgshop.pricing.GradeModel.popularity(301) >= 0.65)
                                                    throw new IllegalStateException("incorrect popularity curve");
                                                if (Math.abs(dev.tcgshop.pricing.GradeModel.mult(50, 100) - 50) > 1e-9 || Math.abs(dev.tcgshop.pricing.GradeModel.mult(50, 10) - 0.40) > 1e-9 || dev.tcgshop.pricing.GradeModel.mult(50, 49) >= 1 || dev.tcgshop.pricing.GradeModel.mult(50, 50) < 1)
                                                    throw new IllegalStateException("incorrect Mg");
                                                var zard = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(dev.masterball.api.card.CardInfo.NS, "baseset/cards/bs004");
                                                LOGGER.info("[selftest] grade: {} data points · Base Set #4 = {} rank {} · M10 x{} · P(10) {}%",
                                                        dev.tcgshop.pricing.GradeModel.dataSize(), dev.tcgshop.pricing.GradeModel.infoOf(zard).cat(), dev.tcgshop.pricing.GradeModel.infoOf(zard).rank(),
                                                        String.format("%.2f", PriceService.gradeM10(zard, dev.masterball.api.card.Rarity.UNKNOWN)), String.format("%.1f", 100 * dev.tcgshop.pricing.GradeModel.prob10(zard)));
                                                if (dev.tcgshop.trade.TradeManager.tradeable(g10)) throw new IllegalStateException("a graded card must not be tradeable");
                                                double pg = 0;
                                                var cid = CardInfo.id(raw); var crr = dev.masterball.api.card.Rarity.of(raw);
                                                for (int g = 10; g <= 100; g++) {
                                                    double u = PriceService.gradedUsd(cid, crr, g);
                                                    if (u + 1e-9 < pg) throw new IllegalStateException("non-increasing graded price at " + g);
                                                    pg = u;
                                                }
                                                LOGGER.info("[selftest] graded prices: {} real, test Grade 10 ${}", PriceService.gradedUsdView().size(), String.format("%.2f", pg));
                                            }
                                        }
                                        case "rarity" -> {
                                            // official rarities, foil per era and print variants with their price
                                            var R = dev.masterball.api.card.Rarity.class;
                                            if (dev.masterball.api.card.RarityData.size() < 7700) throw new IllegalStateException("missing rarity data");
                                            var zard = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(dev.masterball.api.card.CardInfo.NS, "baseset/cards/bs004");
                                            var sir = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(dev.masterball.api.card.CardInfo.NS, "sviolet/cards/svi245");
                                            if (dev.masterball.api.card.Rarity.of(zard) != dev.masterball.api.card.Rarity.HOLO_RARE) throw new IllegalStateException("Base Set Charizard must be Holo Rare");
                                            if (dev.masterball.api.card.RarityData.holoPattern(zard) != dev.masterball.api.card.FoilPattern.STARLIGHT) throw new IllegalStateException("Base Set must have Starlight foil");
                                            if (dev.masterball.api.card.Rarity.of(sir) != dev.masterball.api.card.Rarity.SPECIAL_ILLUSTRATION_RARE || !dev.masterball.api.card.RarityData.card(sir).secret())
                                                throw new IllegalStateException("SVI 245 must be SIR and a numbered Secret Rare");
                                            java.util.Map<dev.masterball.api.card.Rarity, Integer> cnt = new java.util.EnumMap<>(dev.masterball.api.card.Rarity.class);
                                            int unknown = 0;
                                            for (var id : net.minecraft.core.registries.BuiltInRegistries.ITEM.keySet()) {
                                                if (!CardInfo.isCard(id)) continue;
                                                var r = dev.masterball.api.card.Rarity.of(id);
                                                cnt.merge(r, 1, Integer::sum);
                                                if (r == dev.masterball.api.card.Rarity.UNKNOWN) unknown++;
                                            }
                                            LOGGER.info("[selftest] rarity: {} rarities in use, {} cards without rarity: {}", cnt.size(), unknown, cnt);
                                            if (unknown > 400) throw new IllegalStateException("too many cards without rarity: " + unknown);
                                            // variants: 1st edition is worth more than standard and the Prismatic Exeggcute reverse has a real price
                                            ItemStack z = new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(zard));
                                            long std = PriceService.priceCd(z);
                                            long first = PriceService.priceCd(dev.masterball.api.card.PrintVariant.apply(z.copy(), dev.masterball.api.card.PrintVariant.FIRST_EDITION));
                                            var pre = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(dev.masterball.api.card.CardInfo.NS, "prismaticevolutions/cards/pre001");
                                            ItemStack e = new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(pre));
                                            long es = PriceService.priceCd(e), em = PriceService.priceCd(dev.masterball.api.card.PrintVariant.apply(e.copy(), dev.masterball.api.card.PrintVariant.MASTER_BALL));
                                            LOGGER.info("[selftest] variants: Charizard standard={} 1st ed={} · Exeggcute standard={} Master Ball={}", std, first, es, em);
                                            if (first <= std * 2 || em <= es * 3) throw new IllegalStateException("premium variants must be worth considerably more");
                                            // draw: in 20,000 Prismatic common cards some Master Ball appears and most are standard
                                            var rnd = net.minecraft.util.RandomSource.create(7);
                                            int mb = 0, stdc = 0;
                                            for (int k = 0; k < 20_000; k++) {
                                                var v = dev.masterball.api.card.PrintVariant.roll(rnd, pre);
                                                if (v == dev.masterball.api.card.PrintVariant.MASTER_BALL) mb++;
                                                if (v == dev.masterball.api.card.PrintVariant.STANDARD) stdc++;
                                            }
                                            LOGGER.info("[selftest] Prismatic draw: Master Ball {} / standard {} of 20000", mb, stdc);
                                            if (mb < 100 || mb > 350 || stdc < 13_000) throw new IllegalStateException("variant probabilities out of range");
                                            if (dev.masterball.api.card.PrintVariant.roll(rnd, sir) != dev.masterball.api.card.PrintVariant.STANDARD) throw new IllegalStateException("a SIR has no reverse");
                                        }
                                        case "masterball_api" -> {
                                            // MasterBall Traders API: catalog, ScalperTCG id conversion, album rescue and pack contents
                                            var NS = dev.masterball.api.card.CardInfo.NS;
                                            int cat = dev.masterball.api.card.CardCatalog.cardCount();
                                            if (cat < 11_000 || dev.masterball.api.item.CardItems.cardCount() != cat)
                                                throw new IllegalStateException("catalog " + cat + " vs items " + dev.masterball.api.item.CardItems.cardCount());
                                            // ScalperTCG / pre-0.9 world migration (registry aliases, ItemStack mixins, album rescue) only exists on Fabric:
                                            // old worlds are out of scope for the NeoForge build (NEOFORGE_PORT.md), so those checks run on Fabric only
                                            boolean legacy = "fabric".equals(Platform.INSTANCE.loader());
                                            var zardItem = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(NS, "baseset/cards/bs004"));
                                            var regs = world.registryAccess();
                                            if (legacy) {
                                                if (net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("cobbletcg", "baseset/cards/bs004")) != zardItem
                                                        || !net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("cobbletcg", "mew151/sealed_mew151")))
                                                    throw new IllegalStateException("the registry does not convert cobbletcg: ids");
                                                var old = new net.minecraft.nbt.CompoundTag();
                                                old.putString("id", "cobbletcg:baseset/cards/bs004");
                                                old.putInt("count", 3);
                                                ItemStack conv = ItemStack.parse(regs, old).orElse(ItemStack.EMPTY);
                                                if (conv.getItem() != zardItem || conv.getCount() != 3) throw new IllegalStateException("ItemStack saved with a cobbletcg id is not converted: " + conv);
                                            }
                                            if (!dev.masterball.api.card.CardInfo.migrate("cobbletcg:mew151/cards/mew006").equals(NS + ":mew151/cards/mew006"))
                                                throw new IllegalStateException("migrate(String)");
                                            // 0.9: TCG Pocket removed (physical cards only); anything saved with those ids is discarded without error and without losing the container
                                            for (var psi : dev.masterball.api.card.CardCatalog.sets())
                                                if (psi.pocket() || dev.masterball.api.card.CardCatalog.REMOVED_SETS.contains(psi.id())) throw new IllegalStateException("TCG Pocket set loaded: " + psi.id());
                                            if (net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(NS, "gapex/sealed_gapex")))
                                                throw new IllegalStateException("a TCG Pocket pack is still registered");
                                            if (ItemStack.parse(regs, net.minecraft.nbt.TagParser.parseTag("{id:\"masterball:gapex/cards/a1-001\",count:2}")).isPresent())
                                                throw new IllegalStateException("a saved TCG Pocket card is not discarded");
                                            if (legacy) {
                                                var pocketShulker = ItemStack.parse(regs, net.minecraft.nbt.TagParser.parseTag("{id:\"minecraft:shulker_box\",count:1,components:{\"minecraft:container\":[{slot:0,item:{id:\"cobbletcg:misland/sealed_misland\",count:1}},{slot:1,item:{id:\"masterball:baseset/cards/bs004\",count:1}}]}}")).orElse(ItemStack.EMPTY);
                                                var pocketCont = pocketShulker.get(net.minecraft.core.component.DataComponents.CONTAINER);
                                                if (pocketShulker.isEmpty() || pocketCont == null || pocketCont.nonEmptyStream().count() != 1)
                                                    throw new IllegalStateException("box with a TCG Pocket pack: " + pocketShulker);
                                            }
                                            var bd = dev.tcgshop.collection.BinderData.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, net.minecraft.nbt.TagParser.parseTag(
                                                    "{owner:[I;1,2,3,4],counts:{\"cobbletcg:mew151/cards/mew006\":2,\"masterball:mew151/cards/mew006\":1},fresh:[\"cobbletcg:mew151/cards/mew006\"]}"))
                                                    .getOrThrow();
                                            if (bd.count(NS + ":mew151/cards/mew006") != 3 || !bd.fresh.contains(NS + ":mew151/cards/mew006"))
                                                throw new IllegalStateException("the Binder does not convert cobbletcg ids: " + bd.counts);
                                            int rescuedCount = 0;
                                            if (legacy) {
                                                // ScalperTCG album (unknown structure): every item inside is rescued into barrels
                                                var album = net.minecraft.nbt.TagParser.parseTag("{id:\"cobbletcg:album_block_entity\",AlbumId:\"mew151\",CustomInventory:[{SlotFullIndex:5,"
                                                        + "itemStack:{id:\"cobbletcg:mew151/cards/mew006\",count:1}}],Items:[{Slot:0b,id:\"cobbletcg:mew151/sealed_mew151\",count:2}]}");
                                                var rescued = dev.masterball.api.migrate.LegacyRescue.scan(album, regs);
                                                if (rescued.size() != 2) throw new IllegalStateException("album rescue: " + rescued);
                                                rescuedCount = rescued.size();
                                                var at = base.offset(-8, 0, -8);
                                                for (int k = 0; k < 3; k++) world.setBlockAndUpdate(at.above(k), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                                                dev.masterball.api.migrate.LegacyRescue.place(world, at, rescued);
                                                if (!(world.getBlockEntity(at) instanceof net.minecraft.world.level.block.entity.BarrelBlockEntity barrel) || barrel.getItem(0).isEmpty())
                                                    throw new IllegalStateException("the rescue did not leave a barrel with the cards");
                                            }
                                            // packs: always 5 cards from the set; god pack = only full art (old sets without full arts: no commons); sealed -> PackContents pull odds
                                            var rnd = net.minecraft.util.RandomSource.create(11);
                                            int sets = 0, godFullArtSets = 0;
                                            for (var si : dev.masterball.api.card.CardCatalog.sets()) {
                                                sets++;
                                                boolean godFa = dev.masterball.api.pack.PackContents.godIsFullArt(si.id());
                                                if (godFa) godFullArtSets++;
                                                for (int k = 0; k < 40; k++) {
                                                    for (int tier = 1; tier <= 3; tier++) {
                                                        var got = dev.masterball.api.pack.PackContents.open(si.id(), tier, rnd);
                                                        if (got.size() != 5) throw new IllegalStateException(si.id() + " type " + tier + ": " + got.size() + " cards");
                                                        for (ItemStack c : got) {
                                                            if (!CardInfo.setId(CardInfo.id(c)).equals(si.id())) throw new IllegalStateException(si.id() + ": card from another set " + CardInfo.id(c));
                                                            if (tier == 3 && godFa && !dev.masterball.api.card.FullArt.isFullArt(c))
                                                                throw new IllegalStateException(si.id() + ": non-full-art card in god pack " + CardInfo.id(c));
                                                            if (tier == 3 && dev.masterball.api.card.Rarity.of(c).family == dev.masterball.api.card.Rarity.Family.COMMON
                                                                    && si.cards().size() > 60 && !si.pocket()) throw new IllegalStateException(si.id() + ": common in god pack " + CardInfo.id(c));
                                                        }
                                                    }
                                                }
                                            }
                                            // every set from Black & White (2011) onwards has full arts: ~48 of the 68 sets
                                            LOGGER.info("[selftest] god packs: {} of {} sets full art only (the rest use their highest rarities)", godFullArtSets, sets);
                                            if (godFullArtSets < 40) throw new IllegalStateException("too few sets with full-art god packs: " + godFullArtSets + "/" + sets);
                                            int[] tiers = new int[4];
                                            for (int k = 0; k < 100_000; k++) tiers[dev.masterball.api.pack.PackContents.rollTier(rnd)]++;
                                            // the observed counts must match the API's actual pull odds (± ~5 sigma, 100,000 rolls)
                                            double godP = dev.masterball.api.pack.PackContents.GOD_CHANCE, semiP = dev.masterball.api.pack.PackContents.SEMIGOD_CHANCE;
                                            double[] expTier = {0, 100_000 * (1 - godP - semiP), 100_000 * semiP, 100_000 * godP};
                                            for (int t = 1; t <= 3; t++)
                                                if (Math.abs(tiers[t] - expTier[t]) > 5 * Math.sqrt(expTier[t]) + 10)
                                                    throw new IllegalStateException("pack distribution " + java.util.Arrays.toString(tiers) + " vs expected " + java.util.Arrays.toString(expTier));
                                            // an old set without Double Rares does not hand out Secrets: normal Skyridge almost never gives hyper; the pack is worth at least 85 % of its contents
                                            int hyper = 0;
                                            for (int k = 0; k < 2000; k++)
                                                for (ItemStack c : dev.masterball.api.pack.PackContents.open("sridge", 1, rnd))
                                                    if (dev.masterball.api.card.Rarity.of(c).family == dev.masterball.api.card.Rarity.Family.HYPER_RARE) hyper++;
                                            double ev = PriceService.packExpectedUsd("sridge"), pack = PriceService.sealedUsd("sridge", PriceService.Sealed.PACK);
                                            if (hyper > 60 || pack < ev * 0.84) throw new IllegalStateException("Skyridge: " + hyper + " hyper in 2000 packs, pack $" + pack + " vs average $" + ev);
                                            // old cards (up to 2017): ~30 % of rolls at 4.9 / 5.0 / 5.1
                                            var oldCard = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(NS, "baseset/cards/bs004");
                                            int near5 = 0, above = 0;
                                            for (int k = 0; k < 20_000; k++) {
                                                int g = dev.tcgshop.grading.Grading.roll(rnd, oldCard);
                                                if (g >= 49 && g <= 51) near5++;
                                                if (g > 51) above++;
                                            }
                                            LOGGER.info("[selftest] old grading: {}% at 4.9-5.1, {}% above", near5 / 200.0, above / 200.0);
                                            if (near5 < 5000 || near5 > 7000) throw new IllegalStateException("4.9-5.1 distribution of old cards: " + near5);
                                            LOGGER.info("[selftest] packs: Skyridge {} hyper/2000 · pack ${} (average ${}) · Jungle ${} · 30th ${}", hyper,
                                                    String.format("%.2f", pack), String.format("%.2f", ev), String.format("%.2f", PriceService.sealedUsd("jungle", PriceService.Sealed.PACK)),
                                                    String.format("%.2f", PriceService.sealedUsd("ani30", PriceService.Sealed.PACK)));
                                            LOGGER.info("[selftest] masterball_api: {} cards, {} packs, {} sets · packs normal/semi/god {}/{}/{} · album rescued ({} items)",
                                                    cat, dev.masterball.api.item.CardItems.packCount(), sets, tiers[1], tiers[2], tiers[3], rescuedCount);
                                        }
                                        case "duel" -> {
                                            int mapped = dev.tcgshop.ptcg.PtcgCards.mapped();
                                            LOGGER.info("[selftest] duel: {} playable cards in the real TCG", mapped);
                                            if (mapped < 3000) throw new IllegalStateException("too few cards mapped: " + mapped);
                                        }
                                        case "chest_loot" -> {
                                            // 3000 dungeon chests: ~1% with a pack
                                            var table = ctx.getSource().getServer().reloadableRegistries().getLootTable(net.minecraft.world.level.storage.loot.BuiltInLootTables.SIMPLE_DUNGEON);
                                            int packs = 0;
                                            for (int k = 0; k < 3000; k++) {
                                                var params = new net.minecraft.world.level.storage.loot.LootParams.Builder(world)
                                                        .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN, base.getCenter())
                                                        .create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.CHEST);
                                                for (ItemStack st : table.getRandomItems(params)) if (CardInfo.isPack(st)) packs++;
                                            }
                                            LOGGER.info("[selftest] chest_loot: {} packs in 3000 chests ({}%)", packs, packs / 30.0);
                                            if (packs < 8 || packs > 70) throw new IllegalStateException("pack chance out of range: " + packs);
                                        }
                                        case "box_odds" -> dev.tcgshop.pricing.BoxOddsSelfTest.run(); // booster boxes only rarely pay for themselves
                                        case "mansion" -> dev.tcgshop.luxury.house.MansionService.selfTest(); // dry run of the generator
                                        case "shop_close" -> dev.tcgshop.shop.ShopOpenClose.selfTest(world, base); // 1.1: open/closed + dormant shops
                                        case "progression" -> dev.tcgshop.shop.ProgressionSelfTest.run(world, base); // 1.1: core resale, tier difficulty, shop visits
                                        case "staff" -> dev.tcgshop.shop.StaffSelfTest.run(world, base); // 1.1: guards, cashiers, register, tips, theft risk
                                        case "customers" -> dev.tcgshop.shop.CustomerSelfTest.run(world, base); // 1.1: haggling/charm, markup, decor, roofs, names, lines
                                        case "cleanliness" -> dev.tcgshop.shop.CleanSelfTest.run(world, base); // 1.1: score, droppings + mop, freshener, wide door, decor, furniture call
                                        case "npcs" -> dev.tcgshop.npc.NpcSelfTest.run(world, base); // 1.1: Mayor taxes, collector hunt + trades, Engineer
                                        case "phone_systems" -> dev.tcgshop.phone.PhoneSystemsSelfTest.run(world, base); // 1.1 group E: G10 prices, ranking, Diamond loan, set reward, markup, gloves, binders, tables, events, sounds
                                        case "loader_parity" -> LoaderParitySelfTest.run(world); // NeoForge port: registry/data census (compare loaders), server mixins applied
                                        case "display_slots" -> LOGGER.info("[selftest] display_slots: {} aim checks", // 1.0: every display/facing/slot is clickable
                                                dev.tcgshop.shop.ShopDisplayBlock.selfTest(world, base.offset(6, 10, 8)));
                                        case "home_display" -> {
                                            var b = dev.tcgshop.shop.TcgBlocks.HOME_FRAME;
                                            if (b.shopOnly() || !dev.tcgshop.shop.TcgBlocks.CARD_FRAME.shopOnly()) throw new IllegalStateException("shopOnly wrong");
                                            var pos = base.offset(-8, 6, 0);
                                            world.setBlockAndUpdate(pos, b.defaultBlockState());
                                            if (!(world.getBlockEntity(pos) instanceof dev.tcgshop.shop.ShopDisplayBlockEntity be) || !be.isHome())
                                                throw new IllegalStateException("the home display is not recognized");
                                            world.removeBlock(pos, false);
                                        }
                                        case "scalper_brawl" -> {
                                            var a = java.util.Objects.requireNonNull(TcgEntities.SCALPER.create(world));
                                            var b = java.util.Objects.requireNonNull(TcgEntities.SCALPER.create(world));
                                            a.moveTo(base.getX() - 3, base.getY(), base.getZ(), 0, 0);
                                            b.moveTo(base.getX() - 5, base.getY(), base.getZ(), 0, 0);
                                            world.addFreshEntity(a);
                                            world.addFreshEntity(b);
                                            a.startBrawl(b);
                                            b.startBrawl(a);
                                            if (a.mode() != dev.tcgshop.entity.ScalperEntity.Mode.BRAWL || a.getTarget() != b) throw new IllegalStateException("the brawl did not start");
                                        }
                                        default -> world.setBlockAndUpdate(base.west(), dev.tcgshop.shop.TcgBlocks.CORES.get(dev.tcgshop.shop.ShopTier.WOOD).defaultBlockState());
                                    }
                                    LOGGER.info("[selftest] {} OK", step);
                                } catch (Throwable t) {
                                    LOGGER.error("[selftest] {} FAILED", step, t);
                                }
                            }
                            return 1;
                        }))
                        .then(Commands.literal("ptcgtest").executes(ctx -> {
                            // real TCG: deck from a "pool" like a player's, validation and a player (auto) vs house match
                            long t0 = System.currentTimeMillis();
                            com.google.gson.JsonObject pool = new com.google.gson.JsonObject();
                            int added = 0;
                            for (var id : net.minecraft.core.registries.BuiltInRegistries.ITEM.keySet()) {
                                String f = CardInfo.isCard(id) ? dev.tcgshop.ptcg.PtcgCards.engineName(id.toString()) : null;
                                if (f != null && (added++ % 3 == 0)) pool.addProperty(f, 2);
                            }
                            String poolJson = pool.toString();
                            dev.tcgshop.ptcg.PtcgEngine.call(api -> {
                                StringBuilder r = new StringBuilder();
                                String d1 = dev.tcgshop.ptcg.PtcgEngine.str(api, "buildDeck", poolJson, 7);
                                String v1 = dev.tcgshop.ptcg.PtcgEngine.str(api, "validateDeck", d1);
                                String d2 = d1;
                                r.append("pool=").append(pool.size()).append(" validate='").append(v1).append("' ");
                                // AI decks at the 4 levels (house pool + randomness)
                                com.google.gson.JsonObject house = pool.deepCopy();
                                house.addProperty("__house", 1);
                                for (double chaos : new double[]{1.0, 0.6, 0.25, 0.0}) {
                                    String hd = dev.tcgshop.ptcg.PtcgEngine.str(api, "buildDeck", house.toString(), 99, chaos);
                                    r.append("ai").append(chaos).append("='").append(dev.tcgshop.ptcg.PtcgEngine.str(api, "validateDeck", hd)).append("' ");
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
                                        // the "human" follows the AI's suggestion (tests suggest + act with the same format as the client)
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
                                r.append("steps=").append(steps).append(" auto=").append(autos).append(" humanTurns=").append(passes).append(" errors=").append(errors).append(" suggestions=").append(suggested).append(" failedSuggestions=").append(suggestErr)
                                        .append(" winner=").append(v == null ? "?" : v.get("winner").getAsString()).append(" view=").append(view.length());
                                return r.toString();
                            }).whenComplete((r, e) -> LOGGER.info("[ptcgtest] {} in {} ms", e != null ? "FAILED " + e : r, System.currentTimeMillis() - t0));
                            return 1;
                        }))
                        .then(Commands.literal("dumpitems").executes(ctx -> {
                            // List of cards and packs registered by MasterBall Traders API
                            java.util.List<String> ids = new java.util.ArrayList<>();
                            for (var id : net.minecraft.core.registries.BuiltInRegistries.ITEM.keySet())
                                if (id.getNamespace().equals(dev.masterball.api.card.CardInfo.NS)) ids.add(id.toString());
                            java.util.Collections.sort(ids);
                            try {
                                java.nio.file.Files.write(Platform.INSTANCE.gameDir().resolve("masterball_items.txt"), ids);
                            } catch (Exception e) {
                                LOGGER.error("dumpitems", e);
                            }
                            ctx.getSource().sendSuccess(() -> Component.literal(ids.size() + " cards and packs dumped"), false);
                            return 1;
                        }))
                        .then(Commands.literal("rarities").executes(ctx -> {
                            // Counts the registered cards by rarity and how many have a real price.
                            java.util.EnumMap<Rarity, Integer> count = new java.util.EnumMap<>(Rarity.class);
                            int total = 0, real = 0;
                            for (var item : net.minecraft.core.registries.BuiltInRegistries.ITEM) {
                                ItemStack st = new ItemStack(item);
                                if (!CardInfo.isCard(st)) continue;
                                total++;
                                count.merge(Rarity.of(st), 1, Integer::sum);
                                if (PriceService.hasRealPrice(CardInfo.id(st))) real++;
                            }
                            int t = total, r = real;
                            ctx.getSource().sendSuccess(() -> Component.literal("Cards: " + t + " | with a real price: " + r + " | " + count), false);
                            LOGGER.info("[debug] cards={} realPrice={} rarities={}", t, r, count);
                            return 1;
                        })))
                .then(Commands.literal("price").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    ItemStack st = p.getMainHandItem();
                    if (!CardInfo.isCard(st)) {
                        ctx.getSource().sendFailure(Component.translatable("tcgshop.cmd.notcard"));
                        return 0;
                    }
                    Rarity r = Rarity.of(st);
                    var id = CardInfo.id(st);
                    long day = PriceService.marketDay();
                    long now = PriceService.priceCd(st);
                    long std = PriceService.priceCd(id, r);
                    // same copy (variant and Grade) 7 days ago: the standard price from then is scaled
                    long weekAgo = std <= 0 ? 0 : Math.round(PriceService.priceCdOnDay(id, r, Math.max(0, day - 7)) * (double) now / std);
                    double change = weekAgo == 0 ? 0 : (now - weekAgo) * 100.0 / weekAgo;
                    Component trend = Component.literal(String.format(java.util.Locale.ROOT, "%+.1f%%", change))
                            .withStyle(change >= 0 ? ChatFormatting.GREEN : ChatFormatting.RED);
                    Component source = Component.translatable(PriceService.hasRealPrice(id) ? "tcgshop.price.source.real" : "tcgshop.price.source.estimate")
                            .withStyle(ChatFormatting.DARK_GRAY);
                    ctx.getSource().sendSuccess(() -> Component.translatable("tcgshop.cmd.price",
                            st.getDisplayName(),
                            r.displayName().copy().withColor(r.color & 0xFFFFFF),
                            Component.literal(PriceService.format(now)).withStyle(ChatFormatting.GOLD))
                            .append(" ").append(trend).append(" ").append(source), false);
                    return 1;
                }))
                .then(Commands.literal("value").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    long total = 0;
                    int count = 0;
                    for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
                        ItemStack st = p.getInventory().getItem(i);
                        if (CardInfo.isCard(st)) {
                            total += PriceService.priceCd(st) * st.getCount();
                            count += st.getCount();
                        }
                    }
                    long t = total;
                    int c = count;
                    ctx.getSource().sendSuccess(() -> Component.translatable("tcgshop.cmd.value", c,
                            Component.literal(PriceService.format(t)).withStyle(ChatFormatting.GOLD)), false);
                    return 1;
                })));
    }
}

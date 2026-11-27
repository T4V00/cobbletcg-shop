package dev.tcgshop.phone;

import dev.masterball.api.card.CardInfo;
import dev.masterball.api.card.Rarity;
import dev.tcgshop.TcgShop;
import dev.tcgshop.binder.BinderKeep;
import dev.tcgshop.casino.CasinoBlockEntity;
import dev.tcgshop.casino.CasinoBlocks;
import dev.tcgshop.casino.CasinoGame;
import dev.tcgshop.casino.CoinMath;
import dev.tcgshop.casino.DiceMath;
import dev.tcgshop.events.EventManager;
import dev.tcgshop.events.EventsConfig;
import dev.tcgshop.item.TcgItems;
import dev.tcgshop.pricing.PriceService;
import dev.tcgshop.shop.ShopDisplayBlockEntity;
import dev.tcgshop.shop.ShopServer;
import dev.tcgshop.shop.ShopTier;
import dev.tcgshop.shop.ShopZones;
import dev.tcgshop.shop.TcgBlocks;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 1.1 (Group E) selftest "phone_systems": runs without players and logs one line per area.
 * <ul>
 *   <li>Prices app: Grade 10 value of real cards (never below the raw card);</li>
 *   <li>Ranking: Don Cobbledome at exactly 100 trillion every day, the top 10 above everyone else, deterministic daily
 *       drift, a new player's capital enters around rank 100, a huge one reaches the top 10 / first place;</li>
 *   <li>Diamond loan: 1,000,000 by default, offered only with a Diamond shop and the right phone tier;</li>
 *   <li>set completion reward: 1 Master Ball + 500,000 by default and the "set complete" check;</li>
 *   <li>shelf markup: the rounding rules and a real display case raised by 25% and reset to market;</li>
 *   <li>Box-Opening Gloves: registered, very expensive, sold, no recipe;</li>
 *   <li>binders kept on death: extraction from an inventory and the stash;</li>
 *   <li>new gambling tables: exact odds of the dice table and the coin toss vs a 200,000-round simulation, the blocks
 *       and their block entity;</li>
 *   <li>events: the daily plan hits at dailyChance with the new defaults (and logs the old vs new expected rate), the
 *       config migration of the old defaults;</li>
 *   <li>sounds: every new sound event is registered.</li>
 * </ul>
 */
public final class PhoneSystemsSelfTest {
    private PhoneSystemsSelfTest() {}

    private static void check(boolean ok, String what) {
        if (!ok) throw new IllegalStateException(what);
    }

    public static void run(ServerLevel w, BlockPos near) {
        prices();
        ranking(w);
        diamondLoan(w, near);
        setReward();
        markup(w, near);
        gloves(w);
        binders(w);
        casino(w, near);
        events();
        sounds();
    }

    private static void prices() {
        int n = 0, bad = 0;
        double best = 0;
        for (ResourceLocation id : BuiltInRegistries.ITEM.keySet()) {
            if (!CardInfo.isCard(id)) continue;
            ItemStack st = new ItemStack(BuiltInRegistries.ITEM.get(id));
            Rarity r = Rarity.of(st);
            long raw = PriceService.priceCd(id, r), g10 = PriceService.grade10Cd(id, r);
            if (g10 < raw || g10 <= 0) bad++;
            if (raw > 0) best = Math.max(best, g10 / (double) raw);
            if (++n >= 400) break;
        }
        check(n > 0, "no cards to price");
        check(bad == 0, bad + " cards with a Grade 10 value below the raw card");
        TcgShop.LOGGER.info("[selftest] phone_systems: Grade 10 prices OK on {} cards (best multiplier x{})", n, String.format("%.1f", best));
    }

    private static void ranking(ServerLevel w) {
        long seed = w.getSeed();
        for (long day = 0; day < 400; day += 37) {
            check(Ranking.npcCapital(seed, 0, day) == Ranking.DON_CAPITAL, "Don Cobbledome is not at 100 trillion on day " + day);
            long minTop = Long.MAX_VALUE, maxRest = 0;
            for (int i = 1; i < 10; i++) minTop = Math.min(minTop, Ranking.npcCapital(seed, i, day));
            for (int i = 10; i < Ranking.NPCS; i++) maxRest = Math.max(maxRest, Ranking.npcCapital(seed, i, day));
            check(minTop > maxRest, "a common owner passed the top 10 on day " + day);
            check(minTop >= 1_000_000_000L, "top 10 capital too low: " + minTop);
            for (int i = 1; i < Ranking.NPCS; i++) check(Ranking.npcCapital(seed, i, day) < Ranking.DON_CAPITAL, "someone reached Don");
        }
        check(Ranking.npcCapital(seed, 42, 10) == Ranking.npcCapital(seed, 42, 10), "the drift is not deterministic");
        boolean drifts = false;
        for (int d = 1; d < 10; d++) drifts |= Ranking.npcCapital(seed, 42, d) != Ranking.npcCapital(seed, 42, 0);
        check(drifts, "the capitals never change");
        int newbie = Ranking.rankForCapital(seed, 5, 15_000), early = Ranking.rankForCapital(seed, 5, 200_000),
                rich = Ranking.rankForCapital(seed, 5, 500_000_000_000L), don = Ranking.rankForCapital(seed, 5, Ranking.DON_CAPITAL),
                beat = Ranking.rankForCapital(seed, 5, Ranking.DON_CAPITAL + 1);
        check(newbie >= 95, "a new player starts too high: #" + newbie);
        check(early < newbie && early <= 100, "200k does not climb into the table: #" + early);
        check(rich >= 2 && rich <= 10, "500 billion is not in the top 10: #" + rich);
        check(don == 2, "matching Don exactly must not beat him: #" + don);
        check(beat == 1, "earning more than Don is not first place: #" + beat);
        java.util.Set<String> names = new java.util.HashSet<>();
        for (long s2 : new long[]{seed, 1L, 42L, -7L, 123456789L})
            for (int i = 10; i < Ranking.NPCS; i++) check(names.add(s2 + "|" + Ranking.npcName(s2, i)), "duplicate owner name " + Ranking.npcName(s2, i) + " (seed " + s2 + ")");
        check(Ranking.npcName(seed, 50).equals(Ranking.npcName(seed, 50)), "owner names are not stable");
        check(Ranking.capital(1000, 0, 0, 0, 5000) == 6000 && Ranking.capital(0, 0, 0, 10_000, 3000) == 0, "assets in the capital");
        for (int i = 0; i < Ranking.NPCS; i++) {
            check(!Ranking.npcName(seed, i).isEmpty(), "owner without a name");
            double pr = Ranking.npcProfit(seed, i, 3);
            check(pr > -20 && pr < 80, "odd profit " + pr);
        }
        TcgShop.LOGGER.info("[selftest] phone_systems: ranking OK (Don {}; #10 {}; #100 {}; 15k -> #{}, 200k -> #{}, 500B -> #{}, Don+1 -> #{})",
                Ranking.DON_CAPITAL, Ranking.npcCapital(seed, 9, 5), Ranking.npcCapital(seed, 99, 5), newbie, early, rich, beat);
    }

    private static void diamondLoan(ServerLevel w, BlockPos near) {
        PhoneConfig c = PhoneConfig.INSTANCE;
        check(c.diamondLoanAmount == 1_000_000, "Diamond loan default is " + c.diamondLoanAmount);
        UUID owner = UUID.randomUUID();
        long day = PhoneMessages.day(w.getServer());
        check("tcgshop.loan.diamond.need_shop".equals(Loans.diamondBlock(w.getServer(), owner, PhoneTier.MAX_LEVEL, day)), "loan without a Diamond shop");
        ShopZones zones = ShopZones.get(w);
        ShopZones.Zone gold = new ShopZones.Zone(UUID.randomUUID(), owner, "selftest", near.offset(200, 60, 200), ShopTier.GOLD.rank());
        ShopZones.Zone dia = new ShopZones.Zone(UUID.randomUUID(), owner, "selftest", near.offset(260, 60, 200), ShopTier.DIAMOND.rank());
        try {
            zones.add(gold);
            check("tcgshop.loan.diamond.need_shop".equals(Loans.diamondBlock(w.getServer(), owner, PhoneTier.MAX_LEVEL, day)), "a Gold shop unlocked the Diamond loan");
            zones.add(dia);
            check(Loans.diamondBlock(w.getServer(), owner, PhoneTier.MAX_LEVEL, day) == null, "a Diamond shop did not unlock the loan");
            check("tcgshop.loan.diamond.need_phone".equals(Loans.diamondBlock(w.getServer(), owner, 0, day)), "the Diamond loan ignores the phone tier");
            long[] parts = Ranking.parts(w.getServer(), owner);
            check(parts[1] == ShopTier.GOLD.price + ShopTier.DIAMOND.price, "shop value in the capital: " + parts[1]);
            dia.stockValue = 70_000; // review fix: display stock is part of the capital
            check(Ranking.parts(w.getServer(), owner)[3] == 70_000, "display stock not in the capital");
            check(Loans.diamondBlock(w.getServer(), owner, PhoneTier.MAX_LEVEL, day) == null, "Diamond loan check before accepting");
            zones.remove(dia);
            check("tcgshop.loan.diamond.need_shop".equals(Loans.diamondBlock(w.getServer(), owner, PhoneTier.MAX_LEVEL, day)),
                    "the Diamond loan stays acceptable after the Diamond shop is gone");
        } finally {
            zones.remove(gold);
            zones.remove(dia);
        }
        TcgShop.LOGGER.info("[selftest] phone_systems: Diamond loan OK ({} for {} days, only with a Diamond shop)", c.diamondLoanAmount, c.diamondLoanTermDays);
    }

    private static void setReward() {
        var eco = dev.tcgshop.EconomyConfig.INSTANCE;
        check(eco.setCompleteMoney == 500_000 && eco.setCompleteMasterBalls == 1, "set completion reward defaults changed");
        Set<String> set = Set.of("a:1", "a:2", "a:3");
        Map<String, Integer> have = new HashMap<>(Map.of("a:1", 1, "a:2", 2));
        check(!dev.tcgshop.advancement.CardAdvancements.isSetComplete(set, have), "incomplete set counted as complete");
        have.put("a:3", 1);
        check(dev.tcgshop.advancement.CardAdvancements.isSetComplete(set, have), "complete set not detected");
        // review fix: only cards the player got from the game count (a set handed between accounts pays once)
        dev.tcgshop.collection.BinderData bd = new dev.tcgshop.collection.BinderData(UUID.randomUUID(), "selftest", Map.of("a:1", 1, "a:2", 1, "a:3", 1), Set.of());
        check(dev.tcgshop.advancement.CardAdvancements.isSetComplete(set, dev.tcgshop.advancement.CardAdvancements.rewardCounts(bd, Set.of("a:1", "a:2", "a:3"))),
                "own pulls not counted for the set reward");
        check(!dev.tcgshop.advancement.CardAdvancements.isSetComplete(set, dev.tcgshop.advancement.CardAdvancements.rewardCounts(bd, Set.of("a:1", "a:2"))),
                "a card received from another player counted for the set reward");
        // wording: balls + money, balls only (no CobbleDollars), money only (no Cobblemon), unpaid money
        check("tcgshop.collection.reward".equals(dev.tcgshop.advancement.CardAdvancements.rewardKey(1, 500_000, false)), "reward key both");
        check("tcgshop.collection.reward_balls".equals(dev.tcgshop.advancement.CardAdvancements.rewardKey(1, 0, false)), "reward key balls");
        check("tcgshop.collection.reward_money".equals(dev.tcgshop.advancement.CardAdvancements.rewardKey(0, 500_000, false)), "reward key money");
        check("tcgshop.collection.reward_pending".equals(dev.tcgshop.advancement.CardAdvancements.rewardKey(0, 500_000, true)), "reward key pending");
        check("tcgshop.collection.reward_balls_pending".equals(dev.tcgshop.advancement.CardAdvancements.rewardKey(1, 500_000, true)), "reward key balls pending");
        // the pulled history and unpaid money survive a save; an old save (no "pulled") trusts what was already credited
        dev.tcgshop.collection.CardCollection cc = new dev.tcgshop.collection.CardCollection();
        cc.pulled.add("a:1");
        cc.pendingSetMoney = 500_000;
        var enc = dev.tcgshop.collection.CardCollection.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, cc).getOrThrow();
        var dec = dev.tcgshop.collection.CardCollection.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, enc).getOrThrow();
        check(dec.pulled.contains("a:1") && dec.pendingSetMoney == 500_000, "pulled / unpaid reward lost in the save");
        net.minecraft.nbt.CompoundTag old = new net.minecraft.nbt.CompoundTag();
        net.minecraft.nbt.ListTag cr = new net.minecraft.nbt.ListTag();
        cr.add(net.minecraft.nbt.StringTag.valueOf("a:2"));
        old.put("credited", cr);
        var oldDec = dev.tcgshop.collection.CardCollection.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, old).getOrThrow();
        check(oldDec.pulled.contains("a:2") && oldDec.pendingSetMoney == 0, "old save not seeded from credited");
        Item mb = dev.tcgshop.advancement.CardAdvancements.masterBall();
        TcgShop.LOGGER.info("[selftest] phone_systems: set reward OK ({} Master Ball(s){} + {}; own pulls only, unpaid money kept)", eco.setCompleteMasterBalls,
                mb == null ? " - Cobblemon not loaded, money only" : "", eco.setCompleteMoney);
    }

    private static void markup(ServerLevel w, BlockPos near) {
        check(ShopServer.applyMarkup(1000, 10) == 1100, "1000 +10%");
        check(ShopServer.applyMarkup(1, 5) == 2, "1 +5% must raise by at least 1");
        check(ShopServer.applyMarkup(100, -10) == 90, "100 -10%");
        check(ShopServer.applyMarkup(1, -50) == 1, "never below 1");
        BlockPos pos = near.offset(-14, 12, 6);
        w.setBlockAndUpdate(pos, TcgBlocks.DISPLAY_CASE.defaultBlockState());
        try {
            check(w.getBlockEntity(pos) instanceof ShopDisplayBlockEntity, "no display block entity");
            ShopDisplayBlockEntity be = (ShopDisplayBlockEntity) w.getBlockEntity(pos);
            be.setItem(0, new ItemStack(TcgItems.MOP));
            be.setPrice(0, 1000);
            if (be.getContainerSize() > 1) { be.setItem(1, new ItemStack(TcgItems.REPAIR_KIT)); be.setPrice(1, 400); }
            int n = ShopServer.markupAll(null, be, 25);
            check(n >= 1 && be.price(0) == 1250, "markup +25% gave " + be.price(0));
            if (be.getContainerSize() > 1) check(be.price(1) == 500, "second slot +25% gave " + be.price(1));
            ShopServer.markupAll(null, be, 9999); // clamped to +500%
            check(be.price(0) == 7500, "markup clamp: " + be.price(0));
            for (int i = 0; i < be.getContainerSize(); i++) be.setItem(i, ItemStack.EMPTY);
        } finally {
            w.removeBlock(pos, false);
        }
        TcgShop.LOGGER.info("[selftest] phone_systems: shelf markup OK");
    }

    private static void gloves(ServerLevel w) {
        long price = ShopServer.priceOf(new ItemStack(TcgItems.BOX_GLOVES), 1.0);
        check(price >= 500_000, "Box-Opening Gloves are too cheap: " + price);
        boolean sold = false;
        for (ItemStack st : ShopServer.wholesaleGoods(List.of(), null)) sold |= st.is(TcgItems.BOX_GLOVES);
        check(sold, "Box-Opening Gloves are not sold");
        for (var h : w.getServer().getRecipeManager().getRecipes())
            check(!h.value().getResultItem(w.registryAccess()).is(TcgItems.BOX_GLOVES), "Box-Opening Gloves have a recipe: " + h.id());
        check(dev.tcgshop.shop.BoosterBox.sealedFor("mew151") != null, "no packs for the test box");
        TcgShop.LOGGER.info("[selftest] phone_systems: Box-Opening Gloves OK (price {}, no recipe)", price);
    }

    private static void binders(ServerLevel w) {
        SimpleContainer inv = new SimpleContainer(9);
        inv.setItem(0, new ItemStack(TcgItems.GENERAL_BINDER));
        inv.setItem(3, new ItemStack(Items.DIAMOND, 5));
        inv.setItem(5, new ItemStack(TcgItems.PERSONAL_BINDER));
        List<ItemStack> got = BinderKeep.extract(inv);
        check(got.size() == 2, "binders extracted: " + got.size());
        check(inv.getItem(0).isEmpty() && inv.getItem(5).isEmpty() && inv.getItem(3).getCount() == 5, "the inventory was not left right");
        UUID id = UUID.randomUUID();
        BinderKeep.keepForTest(w.getServer(), id, got);
        check(BinderKeep.pending(w.getServer(), id) == 2, "stash");
        check(BinderKeep.takeForTest(w.getServer(), id).size() == 2 && BinderKeep.pending(w.getServer(), id) == 0, "stash not emptied");
        // review fix: each binder goes back to the slot it was in (and the stash remembers it through a save)
        SimpleContainer inv2 = new SimpleContainer(9);
        inv2.setItem(2, new ItemStack(TcgItems.PERSONAL_BINDER));
        inv2.setItem(7, new ItemStack(TcgItems.GENERAL_BINDER));
        List<BinderKeep.Kept> slots = BinderKeep.extractSlots(inv2, true);
        check(slots.size() == 2 && slots.get(0).invSlot() == 2 && slots.get(1).invSlot() == 7, "binder slots not remembered");
        inv2.setItem(7, new ItemStack(Items.DIRT)); // its old slot got taken meanwhile
        BinderKeep.putBack(null, inv2, slots);
        check(inv2.getItem(2).is(TcgItems.PERSONAL_BINDER), "binder not back in its slot");
        int general = 0;
        for (int i = 0; i < inv2.getContainerSize(); i++) if (inv2.getItem(i).is(TcgItems.GENERAL_BINDER)) general++;
        check(general == 1 && inv2.getItem(7).is(Items.DIRT), "binder whose slot was taken was lost or overwrote the slot");
        check(BinderKeep.roundTripForTest(w.registryAccess()), "kept binder slots lost in the save");
        TcgShop.LOGGER.info("[selftest] phone_systems: binders kept on death OK (slots remembered, saved and restored)");
    }

    private static void casino(ServerLevel w, BlockPos near) {
        java.util.random.RandomGenerator g = java.util.random.RandomGenerator.of("L64X128MixRandom");
        StringBuilder sb = new StringBuilder();
        for (int b = 0; b < DiceMath.COUNT; b++) {
            double exact = DiceMath.rtp(b);
            check(exact > 0.8 && exact < 0.9, "dice " + DiceMath.BETS[b] + " RTP " + exact);
            long paid = 0;
            int n = 200_000;
            for (int i = 0; i < n; i++) paid += DiceMath.payout(b, 1, DiceMath.roll(g), DiceMath.roll(g));
            double sim = paid / (double) n;
            check(Math.abs(sim - exact) < (b >= 4 ? 0.06 : 0.02), "dice " + DiceMath.BETS[b] + " simulated " + sim + " vs " + exact);
            sb.append(DiceMath.BETS[b]).append(' ').append(String.format("%.1f", exact * 100)).append("% ");
        }
        for (int b = 0; b < CoinMath.COUNT; b++) {
            double exact = CoinMath.rtp(b);
            long paid = 0;
            int n = 200_000;
            for (int i = 0; i < n; i++) paid += CoinMath.payout(b, 1, CoinMath.flip(g));
            check(Math.abs(paid / (double) n - exact) < (b == 2 ? 0.05 : 0.01), "coin " + CoinMath.BETS[b] + " simulated " + paid / (double) n);
            sb.append(CoinMath.BETS[b]).append(' ').append(String.format("%.0f", exact * 100)).append("% ");
        }
        check(CasinoGame.DICE.maxBet() > 0 && CasinoGame.COIN.maxBet() > 0, "limits");
        BlockPos pos = near.offset(-18, 12, 6);
        try {
            for (var blk : new net.minecraft.world.level.block.Block[]{CasinoBlocks.DICE_TABLE, CasinoBlocks.COIN_TOSS}) {
                w.setBlockAndUpdate(pos, blk.defaultBlockState());
                check(w.getBlockEntity(pos) instanceof CasinoBlockEntity be && be.game() != null && (be.game() == CasinoGame.DICE || be.game() == CasinoGame.COIN),
                        "no casino block entity for " + blk);
                w.removeBlock(pos, false);
            }
        } finally {
            w.removeBlock(pos, false);
        }
        TcgShop.LOGGER.info("[selftest] phone_systems: gambling tables OK ({})", sb.toString().trim());
    }

    private static void events() {
        EventsConfig cfg = EventsConfig.INSTANCE;
        EventsConfig old = new EventsConfig();
        old.dailyChance = 0.10;
        old.maxPerOwnerPerDay = 1;
        old.ownerRange = 96;
        old.configVersion = 0;
        migrateForTest(old);
        check(old.dailyChance == 0.35 && old.maxPerOwnerPerDay == 2 && old.ownerRange == 128, "old defaults not migrated");
        EventsConfig custom = new EventsConfig();
        custom.dailyChance = 0.2;
        custom.configVersion = 0;
        migrateForTest(custom);
        check(custom.dailyChance == 0.2, "a custom dailyChance was overwritten");
        int hits = 0, days = 0, outside = 0;
        long seed = 12345L;
        for (int o = 0; o < 6; o++) {
            UUID owner = new UUID(o * 31L + 7, o * 17L + 3);
            for (long d = 1; d <= 2000; d++) {
                int[] plan = EventManager.planDay(seed, owner, d, cfg);
                days++;
                if (plan[0] == 1) hits++;
                if (plan[1] < cfg.shiftStart || plan[1] >= cfg.shiftEnd) outside++;
            }
        }
        double rate = hits / (double) days;
        check(Math.abs(rate - cfg.dailyChance) < 0.02, "daily hit rate " + rate + " vs " + cfg.dailyChance);
        check(outside == 0, outside + " events planned outside the shift");
        double expectedNew = cfg.dailyChance * (1 + (cfg.maxPerOwnerPerDay > 1 ? cfg.followupChance : 0));
        TcgShop.LOGGER.info("[selftest] phone_systems: events OK (daily plan hits {}%; ~{} events per 10 days now vs ~1 with the old 10%/1-per-day defaults, which were also lost whenever nothing fitted)",
                String.format("%.1f", rate * 100), String.format("%.1f", expectedNew * 10));
    }

    private static void migrateForTest(EventsConfig c) {
        try {
            var m = EventsConfig.class.getDeclaredMethod("migrate");
            m.setAccessible(true);
            m.invoke(c);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void sounds() {
        String[] ids = {"shop.sale", "shop.sale_big", "shop.price_tag", "phone.open", "phone.close", "phone.app", "phone.tap", "phone.notify", "phone.buy",
                "collection.complete", "phone.ranking_up", "box.open_all", "casino.chip", "casino.win"};
        for (String s : ids)
            check(BuiltInRegistries.SOUND_EVENT.containsKey(ResourceLocation.fromNamespaceAndPath(TcgShop.MOD_ID, s)), "sound not registered: " + s);
        TcgShop.LOGGER.info("[selftest] phone_systems: {} new sound events registered", ids.length);
    }
}

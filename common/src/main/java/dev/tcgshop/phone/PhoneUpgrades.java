package dev.tcgshop.phone;

import dev.masterball.api.platform.Net;
import dev.masterball.api.platform.event.PlayerEvents;
import dev.masterball.api.card.CardInfo;
import dev.tcgshop.TcgShop;
import dev.tcgshop.economy.CobbleDollarsBridge;
import dev.tcgshop.entity.TcgEntities;
import dev.tcgshop.entity.TraderEntity;
import dev.tcgshop.entity.WholesalerEntity;
import dev.tcgshop.item.TcgItems;
import dev.tcgshop.pricing.PriceService;
import dev.tcgshop.shop.BoosterBox;
import dev.tcgshop.shop.CardSales;
import dev.tcgshop.shop.PackLock;
import dev.tcgshop.shop.ShopServer;
import dev.tcgshop.shop.ShopZones;
import dev.tcgshop.shop.TcgBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Phone upgrades (0.9), server side: per-player tier, buying upgrades, Market and
 * Wholesaler locks, discounts, sell bonus and the top tier's daily call. See the table in {@link PhoneTier}.
 */
public final class PhoneUpgrades {
    private PhoneUpgrades() {}

    /** Call ONCE from TcgShop.onInitialize (registers networking, the on-join migration and reads the config). */
    public static void register() {
        PhoneConfig.load();
        Net.INSTANCE.s2c(PhonePayloads.Sync.ID, PhonePayloads.Sync.CODEC);
        Net.INSTANCE.c2s(PhonePayloads.Act.ID, PhonePayloads.Act.CODEC);
        Net.INSTANCE.s2c(PhonePayloads.Result.ID, PhonePayloads.Result.CODEC); // 1.0: purchase notices
        Net.INSTANCE.receive(PhonePayloads.Act.ID, (p, player) -> player.server.execute(() -> handle(player, p.data())));
        PhoneApps.register(); // 1.0: Messages, Loans and Stats apps (payload, tick, join sync)
        // runs before the Onboarding starter kit (deferred with server.execute): a new player doesn't have the tag yet
        PlayerEvents.JOIN.register(player -> {
            ServerPlayer sp = player;
            migrate(sp);
            player.server.execute(() -> sync(sp, null, null));
        });
    }

    // ------------------------------------------------------------------ tier

    private static PhoneState.Entry entry(ServerPlayer p) {
        return PhoneState.get(p.getServer()).getOrCreate(p.getUUID());
    }

    /** Effective tier (with the system disabled, everyone has the top one: apps, catalog and daily call). */
    public static int level(ServerPlayer p) {
        if (!PhoneConfig.INSTANCE.enabled) return PhoneTier.MAX_LEVEL;
        return entry(p).tier;
    }

    /**
     * Tier for economic perks (discounts, sell bonus, extra Wholesaler sets, bypassing PackLock).
     * With the system disabled it is 0: falls back to the pre-0.9 economy instead of handing out the top tier's perks.
     */
    public static int perkLevel(ServerPlayer p) {
        if (!PhoneConfig.INSTANCE.enabled) return 0;
        return entry(p).tier;
    }

    public static boolean appUnlocked(ServerPlayer p, String app) {
        if (!PhoneConfig.INSTANCE.enabled) return true;
        PhoneState.Entry e = entry(p);
        return (e.legacy && PhoneConfig.INSTANCE.legacyKeepsApps) || e.tier >= PhoneTier.appTier(app);
    }

    /**
     * Server-side check of a phone app (the UI locks only stop honest
     * clients): if it is locked, notifies the player and returns false.
     */
    public static boolean requireApp(ServerPlayer p, String app) {
        if (appUnlocked(p, app)) return true;
        p.displayClientMessage(Component.translatable("tcgshop.phoneup.requires", tierName(PhoneTier.appTier(app))).withStyle(ChatFormatting.RED), true);
        return false;
    }

    /**
     * 1.0: every TCG League app action needs the League app (phone tier 2, PhoneTier.APP_TIER): sync, deck editing,
     * cosmetics, ranked matches, the online queue and the AI above level 1. Only what does not come from the app is
     * always allowed: friendly challenges (Cobblemon wheel), leaving the queue and a play-table rematch (casual, level <= 1).
     */
    public static boolean leagueActionAllowed(ServerPlayer p, String json) {
        if (appUnlocked(p, "league")) return true;
        try {
            com.google.gson.JsonObject o = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
            String a = o.has("a") ? o.get("a").getAsString() : "sync";
            if (a.equals("challenge")) return true;
            if (a.equals("play")) {
                String mode = o.has("mode") ? o.get("mode").getAsString() : "casual";
                int lvl = o.has("level") ? o.get("level").getAsInt() : 1;
                if (mode.equals("cancel_queue") || (!mode.equals("ranked_ai") && !mode.equals("queue") && lvl <= 1)) return true;
            }
        } catch (Exception ex) {
            return true; // PtcgDuels.league discards the malformed packet
        }
        return requireApp(p, "league");
    }

    /** Bonus on top of the phone's quick-sell rate (CardSales: rate = PHONE_RATE + sellBonus). */
    public static double sellBonus(ServerPlayer p) {
        return PhoneTier.SELL_BONUS[perkLevel(p)];
    }

    /**
     * 0.9 migration: players who already had a shop start at Pro, and those who had already played (starter kit or purchases) at Plus;
     * both keep all the apps they already used. A new player starts at Basic.
     */
    static void migrate(ServerPlayer sp) {
        PhoneState st = PhoneState.get(sp.getServer());
        if (st.find(sp.getUUID()) != null) return;
        PhoneState.Entry e = st.getOrCreate(sp.getUUID());
        boolean owner = false;
        long spent = 0;
        for (ServerLevel w : sp.getServer().getAllLevels()) {
            ShopZones zones = ShopZones.get(w);
            spent += zones.costs(sp.getUUID());
            for (ShopZones.Zone z : zones.all()) if (z.owner.equals(sp.getUUID())) owner = true;
        }
        boolean played = sp.getTags().contains(CardSales.STARTER_TAG) || spent > 0;
        PhoneConfig c = PhoneConfig.INSTANCE;
        if (owner) { e.tier = c.legacyShopOwnerTier; e.legacy = true; }
        else if (played) { e.tier = c.legacyPlayerTier; e.legacy = true; }
        st.setDirty();
        if (e.legacy) TcgShop.LOGGER.info("Phone 0.9: {} migrated to tier {} (keeps their apps)", sp.getName().getString(), e.tier);
    }

    // ------------------------------------------------------------------ synchronization

    /** Sends the state to the client. source/locks: locks of the catalog about to be opened next (or null). */
    public static void sync(ServerPlayer p, @Nullable Integer source, int[] locks) {
        sync(p, source, locks, false);
    }

    /**
     * Same, and refresh = the following catalog should only refresh a phone that is already open (after buying an
     * upgrade): if the player closed it in the meantime, it is not reopened on top of another screen.
     */
    public static void sync(ServerPlayer p, @Nullable Integer source, int[] locks, boolean refresh) {
        if (!Net.INSTANCE.canSend(p, PhonePayloads.Sync.ID)) return;
        PhoneConfig c = PhoneConfig.INSTANCE;
        PhoneState.Entry e = entry(p);
        CompoundTag d = new CompoundTag();
        d.putBoolean("enabled", c.enabled);
        d.putInt("tier", level(p));
        d.putBoolean("legacy", e.legacy && c.legacyKeepsApps);
        d.putLongArray("costs", c.tierCosts.clone());
        d.putBoolean("calls", c.callsEnabled);
        d.putBoolean("callUsed", callUsed(p.getServer(), e));
        d.putInt("furnTier", c.furnitureCallMinTier); // 1.1: daily Furniture Seller call
        d.putBoolean("furnUsed", used(p.getServer(), e.lastFurnCallTick));
        if (source != null && locks != null) {
            d.putInt("src", source);
            d.putIntArray("locks", locks);
            if (refresh) d.putBoolean("refresh", true);
        }
        Net.INSTANCE.send(p, new PhonePayloads.Sync(d));
    }

    /** Ticks between two calls (one Minecraft day). */
    private static final long CALL_COOLDOWN = 24000L;

    /**
     * Call used in the last 24000 world ticks. Measured with getTime(), which only moves forward: /time set and
     * doDaylightCycle = false don't leave it locked (with the day number it could stay that way for hundreds of days or forever).
     */
    static boolean callUsed(MinecraftServer server, PhoneState.Entry e) {
        return used(server, e.lastCallTick);
    }

    /** Was a call made at tick `last` less than a day ago? */
    static boolean used(MinecraftServer server, long last) {
        if (last < 0) return false;
        long now = server.overworld().getGameTime();
        return now >= last && now - last < CALL_COOLDOWN;
    }

    // ------------------------------------------------------------------ catalogs (phone Market and Wholesaler)

    private static int tierForPackUsd(double usd) {
        double[] lim = PhoneConfig.INSTANCE.packUsdLimits;
        for (int i = 0; i < lim.length; i++) if (usd <= lim[i]) return i;
        return PhoneTier.MAX_LEVEL;
    }

    /** Phone tier required to buy this product (phone = phone Market; otherwise, Wholesaler). */
    public static int requiredTier(ItemStack st, boolean phone) {
        if (!PhoneConfig.INSTANCE.enabled) return 0;
        if (st.is(TcgBlocks.BOOSTER_BOX_ITEM)) {
            // 1.0: a box needs the same tier as its pack (the pack's market price); boxMinTier can raise it on the phone
            int t = tierForPackUsd(PriceService.sealedUsd(st.getOrDefault(TcgBlocks.BOX_SET, "mew151"), PriceService.Sealed.PACK));
            return phone ? Math.max(PhoneConfig.INSTANCE.boxMinTier, t) : t;
        }
        if (CardInfo.isPack(st)) return tierForPackUsd(PriceService.sealedUsd(CardInfo.setId(CardInfo.id(st)), PriceService.Sealed.PACK));
        if (st.is(TcgBlocks.ROULETTE_ITEM)) return PhoneTier.ROULETTE_TIER;
        if (!phone) return 0;
        if (st.is(TcgBlocks.MYSTERY_BOX)) return PhoneTier.PHONE_MYSTERY_TIER;
        if (ShopServer.isPokedoll(st)) return PhoneTier.PHONE_PLUSH_TIER;
        return 0;
    }

    /**
     * What this player sees in that catalog: the Wholesaler brings boxes of extra sets at high tiers, and at the top
     * tier the shop-level set lock is bypassed. It is deterministic: opening and buying produce the same list.
     */
    public static List<ItemStack> storeView(ServerPlayer p, int source, List<ItemStack> goods) {
        return applyPackLock(p, withExtraSets(p, source, goods));
    }

    /**
     * First step of storeView: the catalog with the Wholesaler's extra set boxes (without applying PackLock).
     * Phone (source -1): one x36 box for every pack it sells (see {@link #phoneBoxes}).
     */
    public static List<ItemStack> withExtraSets(ServerPlayer p, int source, List<ItemStack> goods) {
        if (source == -1) return phoneBoxes(goods);
        int lv = perkLevel(p);
        List<ItemStack> g = goods;
        int extra = PhoneTier.EXTRA_BOX_SETS[lv];
        if (source != -1 && extra > 0) {
            Set<String> have = new HashSet<>();
            for (ItemStack st : goods) if (st.is(TcgBlocks.BOOSTER_BOX_ITEM)) have.add(st.getOrDefault(TcgBlocks.BOX_SET, ""));
            List<String> all = new ArrayList<>();
            for (ResourceLocation id : BuiltInRegistries.ITEM.keySet())
                if (CardInfo.NS.equals(id.getNamespace()) && id.getPath().contains("/sealed_")) {
                    String s = CardInfo.setId(id);
                    if (s != null && !have.contains(s) && !all.contains(s)) all.add(s);
                }
            Collections.sort(all);
            Collections.shuffle(all, new java.util.Random(source * 7919L + 104729L));
            g = new ArrayList<>(goods);
            for (int i = 0; i < Math.min(extra, all.size()); i++) g.add(BoosterBox.of(all.get(i)));
        }
        return g;
    }

    /**
     * 1.0: the phone Market sells the x36 booster box of EVERY pack it sells. Boxes of removed sets (or sets without packs)
     * and duplicated boxes are dropped, and a missing box is appended at the end. The tier lock is the pack's one
     * (requiredTier) and PackLock applies to boxes too. Deterministic: opening and buying see the same list (same indices).
     */
    static List<ItemStack> phoneBoxes(List<ItemStack> goods) {
        Set<String> boxed = new HashSet<>();
        boolean changed = false;
        List<ItemStack> out = new ArrayList<>(goods.size() + 8);
        for (ItemStack st : goods) {
            if (st.is(TcgBlocks.BOOSTER_BOX_ITEM)) {
                String set = st.getOrDefault(TcgBlocks.BOX_SET, "");
                if (BoosterBox.isDeadSet(set) || !boxed.add(set)) {
                    changed = true;
                    continue;
                }
            }
            out.add(st);
        }
        for (ItemStack st : goods) {
            if (!CardInfo.isPack(st)) continue;
            String set = CardInfo.setId(CardInfo.id(st));
            if (set == null || BoosterBox.isDeadSet(set) || !boxed.add(set)) continue;
            out.add(BoosterBox.of(set));
            changed = true;
        }
        return changed ? out : goods;
    }

    /** Second step of storeView: removes sets locked by shop level (except at the top tier). */
    public static List<ItemStack> applyPackLock(ServerPlayer p, List<ItemStack> goods) {
        if (perkLevel(p) >= PhoneTier.PACKLOCK_BYPASS_TIER) return goods;
        return PackLock.visibleTo(p, goods);
    }

    /** Required tier of each product in the list (for the UI locks). */
    public static int[] locks(List<ItemStack> goods, int source) {
        int[] out = new int[goods.size()];
        for (int i = 0; i < out.length; i++) out[i] = requiredTier(goods.get(i), source == -1);
        return out;
    }

    /** Price factor with the tier discount. */
    public static double priceFactor(ServerPlayer p, int source, double base) {
        int lv = perkLevel(p);
        return base * (source == -1 ? PhoneTier.PHONE_DISCOUNT[lv] : PhoneTier.WHOLESALE_DISCOUNT[lv]);
    }

    public static Component tierName(int level) {
        return Component.translatable("tcgshop.phoneup.tier." + PhoneTier.of(level).key);
    }

    // ------------------------------------------------------------------ actions

    private static void handle(ServerPlayer p, CompoundTag d) {
        switch (d.getString("a")) {
            case "buy" -> buy(p, d.getInt("tier"));
            case "call" -> call(p, d.getString("kind"));
            case "call_furniture" -> callFurniture(p); // 1.1
            case "sync" -> sync(p, null, null);
            default -> PhoneApps.handle(p, d); // 1.0: Messages, Loans and Stats apps
        }
    }

    /** Does the player carry a phone? (package-private since 1.0: the Messages / Loans apps use it too). */
    static boolean hasPhone(ServerPlayer p) {
        return p.getInventory().contains(st -> st.is(TcgItems.TCG_PHONE));
    }

    static void buy(ServerPlayer p, int target) {
        PhoneConfig c = PhoneConfig.INSTANCE;
        if (!c.enabled || !hasPhone(p)) return;
        if (Loans.blocksPhonePurchase(p)) return; // 1.0: no phone purchases while a loan is active
        PhoneState.Entry e = entry(p);
        if (target != e.tier + 1 || target > PhoneTier.MAX_LEVEL) { sync(p, null, null); return; }
        if (!CobbleDollarsBridge.isAvailable()) {
            p.displayClientMessage(Component.translatable("tcgshop.sell.no_bank").withStyle(ChatFormatting.RED), true);
            PhoneStore.sendResult(p, PhoneStore.NO_BANK, -1, ItemStack.EMPTY, 0, 0, target);
            return;
        }
        long cost = c.cost(target);
        long balance = CobbleDollarsBridge.getBalance(p);
        if (!CobbleDollarsBridge.tryCharge(p, cost)) {
            p.displayClientMessage(Component.translatable("tcgshop.shop.no_money", PriceService.format(cost)).withStyle(ChatFormatting.RED), true);
            p.level().playSound(null, p.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 0.6f, 1f);
            PhoneStore.sendResult(p, PhoneStore.NO_MONEY, -1, ItemStack.EMPTY, 0, cost, target, balance);
            return;
        }
        e.tier = target;
        PhoneState.get(p.getServer()).setDirty();
        PhoneStore.sendResult(p, PhoneStore.UPGRADED, -1, ItemStack.EMPTY, 0, cost, target);
        p.displayClientMessage(Component.translatable("tcgshop.phoneup.bought", tierName(target).copy().withStyle(ChatFormatting.BOLD),
                Component.literal(PriceService.format(cost)).withStyle(ChatFormatting.GOLD)).withStyle(ChatFormatting.GREEN), false);
        p.level().playSound(null, p.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.7f, 1.2f);
        // resend the Market (new products and prices); the open phone updates without closing and,
        // if the player already closed it, it is not reopened
        ShopServer.openStore(p, -1, ShopServer.phoneCatalog(), ShopServer.PHONE_FACTOR, true);
    }

    static void call(ServerPlayer p, String kind) {
        PhoneConfig c = PhoneConfig.INSTANCE;
        if (!c.callsEnabled || !hasPhone(p)) return;
        if (level(p) < PhoneTier.CALL_TIER) return;
        PhoneState.Entry e = entry(p);
        if (callUsed(p.getServer(), e)) {
            p.displayClientMessage(Component.translatable("tcgshop.phoneup.call.used").withStyle(ChatFormatting.YELLOW), true);
            sync(p, null, null);
            return;
        }
        boolean ok = switch (kind) {
            case "collector" -> callCollector(p);
            case "wholesaler" -> callWholesaler(p);
            default -> false;
        };
        if (!ok) return;
        e.lastCallTick = p.getServer().overworld().getGameTime();
        PhoneState.get(p.getServer()).setDirty();
        p.level().playSound(null, p.blockPosition(), SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 0.8f, 1.4f);
        sync(p, null, null);
    }

    /**
     * 1.1: calls the Furniture Seller (Plus tier and up by default, once per Minecraft day, its own cooldown): it comes to
     * your nearest shop if you are within 96 blocks of it, otherwise next to you. The call is only used up if it comes.
     */
    static void callFurniture(ServerPlayer p) {
        PhoneConfig c = PhoneConfig.INSTANCE;
        if (!hasPhone(p)) return;
        if (c.enabled && level(p) < c.furnitureCallMinTier || c.furnitureCallMinTier > PhoneTier.MAX_LEVEL) {
            p.displayClientMessage(Component.translatable("tcgshop.phoneup.furn_call.locked", tierName(Math.min(PhoneTier.MAX_LEVEL, c.furnitureCallMinTier)))
                    .withStyle(ChatFormatting.RED), true);
            sync(p, null, null); // answers the phone (it waits for the result before showing a notice)
            return;
        }
        PhoneState.Entry e = entry(p);
        if (used(p.getServer(), e.lastFurnCallTick)) {
            p.displayClientMessage(Component.translatable("tcgshop.phoneup.furn_call.used").withStyle(ChatFormatting.YELLOW), true);
            sync(p, null, null);
            return;
        }
        ServerLevel w = p.serverLevel();
        if (!w.getEntitiesOfClass(dev.tcgshop.entity.FurnitureSellerEntity.class, p.getBoundingBox().inflate(96), t -> true).isEmpty()) {
            p.displayClientMessage(Component.translatable("tcgshop.phoneup.furn_call.already_near").withStyle(ChatFormatting.YELLOW), true);
            sync(p, null, null);
            return;
        }
        String why = dev.tcgshop.entity.FurnitureSellerEntity.spawnFor(w, p);
        if (why != null) { // the real reason: a seller already at the shop, or no free ground there
            p.displayClientMessage(Component.translatable(why).withStyle(why.endsWith("no_room") ? ChatFormatting.RED : ChatFormatting.YELLOW), true);
            sync(p, null, null);
            return;
        }
        e.lastFurnCallTick = p.getServer().overworld().getGameTime();
        PhoneState.get(p.getServer()).setDirty();
        p.displayClientMessage(Component.translatable("tcgshop.phoneup.furn_call.ok").withStyle(ChatFormatting.GOLD), false);
        p.level().playSound(null, p.blockPosition(), SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 0.8f, 1.2f);
        sync(p, null, null);
    }

    /** If you're in (or near) your shop, a big-spending collector customer comes; otherwise, a wandering Collector. */
    private static boolean callCollector(ServerPlayer p) {
        ServerLevel w = p.serverLevel();
        ShopZones.Zone best = null;
        double bestD = 96 * 96;
        for (ShopZones.Zone z : ShopZones.get(w).all()) {
            if (!z.owner.equals(p.getUUID())) continue;
            double dd = p.distanceToSqr(z.core.getCenter());
            if (dd < bestD) { bestD = dd; best = z; }
        }
        // via the normal customer path: walks up to an open door and respects capacity and queue; if the
        // shop isn't loaded, is full with a full queue, or all its doors are closed, the wanderer comes instead
        if (best != null && best.closed) { // 1.1: a closed shop receives nobody
            p.displayClientMessage(Component.translatable("tcgshop.shop.closed_no_customers").withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (best != null && dev.tcgshop.shop.ShopLife.forceSpawnCustomer(w, best, "WHALE")) {
            p.displayClientMessage(Component.translatable("tcgshop.phoneup.call.collector_shop").withStyle(ChatFormatting.GOLD), false);
            return true;
        }
        if (!w.getEntitiesOfClass(TraderEntity.class, p.getBoundingBox().inflate(48), t -> true).isEmpty()) {
            p.displayClientMessage(Component.translatable("tcgshop.phoneup.call.already_near").withStyle(ChatFormatting.YELLOW), true);
            return false;
        }
        if (spawnNear(w, p, TcgEntities.TRADER.create(w))) {
            p.displayClientMessage(Component.translatable("tcgshop.phoneup.call.collector_near").withStyle(ChatFormatting.GOLD), false);
            return true;
        }
        p.displayClientMessage(Component.translatable("tcgshop.phoneup.call.no_room").withStyle(ChatFormatting.RED), true);
        return false;
    }

    private static boolean callWholesaler(ServerPlayer p) {
        ServerLevel w = p.serverLevel();
        if (!w.getEntitiesOfClass(WholesalerEntity.class, p.getBoundingBox().inflate(48), t -> true).isEmpty()) {
            p.displayClientMessage(Component.translatable("tcgshop.phoneup.call.already_near").withStyle(ChatFormatting.YELLOW), true);
            return false;
        }
        if (spawnNear(w, p, TcgEntities.WHOLESALER.create(w))) {
            p.displayClientMessage(Component.translatable("tcgshop.phoneup.call.wholesaler").withStyle(ChatFormatting.GOLD), false);
            return true;
        }
        p.displayClientMessage(Component.translatable("tcgshop.phoneup.call.no_room").withStyle(ChatFormatting.RED), true);
        return false;
    }

    // ---- 0.9 random events: the same spawns as the phone calls, without using up the daily call

    /** Spawns the Wholesaler next to the player (if there isn't one nearby already). */
    public static boolean spawnWholesalerNear(ServerPlayer p) {
        ServerLevel w = p.serverLevel();
        if (!w.getEntitiesOfClass(WholesalerEntity.class, p.getBoundingBox().inflate(48), t -> true).isEmpty()) return true;
        return spawnNear(w, p, TcgEntities.WHOLESALER.create(w));
    }

    /** Spawns the black market dealer next to the player, with their offer, for that player only (if there isn't one nearby already). */
    public static boolean spawnBlackMarketNear(ServerPlayer p) {
        ServerLevel w = p.serverLevel();
        if (!w.getEntitiesOfClass(dev.tcgshop.entity.BlackMarketEntity.class, p.getBoundingBox().inflate(128), t -> true).isEmpty()) return true;
        dev.tcgshop.entity.BlackMarketEntity m = TcgEntities.BLACK_MARKET.create(w);
        if (m == null) return false;
        m.setForPlayer(p.getUUID());
        m.rollOffer();
        return spawnNear(w, p, m);
    }

    /** Places an entity on free ground 4-9 blocks from the player. */
    public static boolean spawnNearPlayer(ServerLevel w, ServerPlayer p, @Nullable PathfinderMob ent) {
        return spawnNear(w, p, ent);
    }

    private static boolean spawnNear(ServerLevel w, ServerPlayer p, @Nullable PathfinderMob ent) {
        if (ent == null) return false;
        for (int attempt = 0; attempt < 16; attempt++) {
            double ang = w.random.nextDouble() * Math.PI * 2;
            int dist = 4 + w.random.nextInt(6);
            int x = (int) Math.floor(p.getX() + Math.cos(ang) * dist), z = (int) Math.floor(p.getZ() + Math.sin(ang) * dist);
            BlockPos found = null;
            for (int dy = 3; dy >= -3 && found == null; dy--) { // first near the player's height (works indoors)
                BlockPos c = new BlockPos(x, p.getBlockY() + dy, z);
                if (w.getBlockState(c.below()).isRedstoneConductor(w, c.below()) && w.isEmptyBlock(c) && w.isEmptyBlock(c.above())
                        && !dev.tcgshop.shop.ShopZones.isShopRoof(w, c)) found = c; // 1.1: never on a shop's roof
            }
            if (found == null) {
                int y = w.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                BlockPos c = new BlockPos(x, y, z);
                if (Math.abs(y - p.getBlockY()) <= 6 && w.getBlockState(c.below()).isRedstoneConductor(w, c.below()) && w.isEmptyBlock(c) && w.isEmptyBlock(c.above())
                        && !dev.tcgshop.shop.ShopZones.isShopRoof(w, c)) found = c;
            }
            if (found == null) continue;
            ent.moveTo(found.getX() + 0.5, found.getY(), found.getZ() + 0.5, w.random.nextFloat() * 360f, 0f);
            w.addFreshEntity(ent);
            return true;
        }
        return false;
    }
}

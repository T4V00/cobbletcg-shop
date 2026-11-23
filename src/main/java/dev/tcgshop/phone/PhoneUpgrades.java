package dev.tcgshop.phone;

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
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
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
        PayloadTypeRegistry.playS2C().register(PhonePayloads.Sync.ID, PhonePayloads.Sync.CODEC);
        PayloadTypeRegistry.playC2S().register(PhonePayloads.Act.ID, PhonePayloads.Act.CODEC);
        PayloadTypeRegistry.playS2C().register(PhonePayloads.Result.ID, PhonePayloads.Result.CODEC); // 1.0: purchase notices
        ServerPlayNetworking.registerGlobalReceiver(PhonePayloads.Act.ID, (p, ctx) -> ctx.server().execute(() -> handle(ctx.player(), p.data())));
        PhoneApps.register(); // 1.0: Messages, Loans and Stats apps (payload, tick, join sync)
        // runs before the Onboarding starter kit (deferred with server.execute): a new player doesn't have the tag yet
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity sp = handler.getPlayer();
            migrate(sp);
            server.execute(() -> sync(sp, null, null));
        });
    }

    // ------------------------------------------------------------------ tier

    private static PhoneState.Entry entry(ServerPlayerEntity p) {
        return PhoneState.get(p.getServer()).getOrCreate(p.getUuid());
    }

    /** Effective tier (with the system disabled, everyone has the top one: apps, catalog and daily call). */
    public static int level(ServerPlayerEntity p) {
        if (!PhoneConfig.INSTANCE.enabled) return PhoneTier.MAX_LEVEL;
        return entry(p).tier;
    }

    /**
     * Tier for economic perks (discounts, sell bonus, extra Wholesaler sets, bypassing PackLock).
     * With the system disabled it is 0: falls back to the pre-0.9 economy instead of handing out the top tier's perks.
     */
    public static int perkLevel(ServerPlayerEntity p) {
        if (!PhoneConfig.INSTANCE.enabled) return 0;
        return entry(p).tier;
    }

    public static boolean appUnlocked(ServerPlayerEntity p, String app) {
        if (!PhoneConfig.INSTANCE.enabled) return true;
        PhoneState.Entry e = entry(p);
        return (e.legacy && PhoneConfig.INSTANCE.legacyKeepsApps) || e.tier >= PhoneTier.appTier(app);
    }

    /**
     * Server-side check of a phone app (the UI locks only stop honest
     * clients): if it is locked, notifies the player and returns false.
     */
    public static boolean requireApp(ServerPlayerEntity p, String app) {
        if (appUnlocked(p, app)) return true;
        p.sendMessage(Text.translatable("tcgshop.phoneup.requires", tierName(PhoneTier.appTier(app))).formatted(Formatting.RED), true);
        return false;
    }

    /**
     * 1.0: every TCG League app action needs the League app (phone tier 2, PhoneTier.APP_TIER): sync, deck editing,
     * cosmetics, ranked matches, the online queue and the AI above level 1. Only what does not come from the app is
     * always allowed: friendly challenges (Cobblemon wheel), leaving the queue and a play-table rematch (casual, level <= 1).
     */
    public static boolean leagueActionAllowed(ServerPlayerEntity p, String json) {
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
    public static double sellBonus(ServerPlayerEntity p) {
        return PhoneTier.SELL_BONUS[perkLevel(p)];
    }

    /**
     * 0.9 migration: players who already had a shop start at Pro, and those who had already played (starter kit or purchases) at Plus;
     * both keep all the apps they already used. A new player starts at Basic.
     */
    static void migrate(ServerPlayerEntity sp) {
        PhoneState st = PhoneState.get(sp.getServer());
        if (st.find(sp.getUuid()) != null) return;
        PhoneState.Entry e = st.getOrCreate(sp.getUuid());
        boolean owner = false;
        long spent = 0;
        for (ServerWorld w : sp.getServer().getWorlds()) {
            ShopZones zones = ShopZones.get(w);
            spent += zones.costs(sp.getUuid());
            for (ShopZones.Zone z : zones.all()) if (z.owner.equals(sp.getUuid())) owner = true;
        }
        boolean played = sp.getCommandTags().contains(CardSales.STARTER_TAG) || spent > 0;
        PhoneConfig c = PhoneConfig.INSTANCE;
        if (owner) { e.tier = c.legacyShopOwnerTier; e.legacy = true; }
        else if (played) { e.tier = c.legacyPlayerTier; e.legacy = true; }
        st.markDirty();
        if (e.legacy) TcgShop.LOGGER.info("Phone 0.9: {} migrated to tier {} (keeps their apps)", sp.getName().getString(), e.tier);
    }

    // ------------------------------------------------------------------ synchronization

    /** Sends the state to the client. source/locks: locks of the catalog about to be opened next (or null). */
    public static void sync(ServerPlayerEntity p, @Nullable Integer source, int[] locks) {
        sync(p, source, locks, false);
    }

    /**
     * Same, and refresh = the following catalog should only refresh a phone that is already open (after buying an
     * upgrade): if the player closed it in the meantime, it is not reopened on top of another screen.
     */
    public static void sync(ServerPlayerEntity p, @Nullable Integer source, int[] locks, boolean refresh) {
        if (!ServerPlayNetworking.canSend(p, PhonePayloads.Sync.ID)) return;
        PhoneConfig c = PhoneConfig.INSTANCE;
        PhoneState.Entry e = entry(p);
        NbtCompound d = new NbtCompound();
        d.putBoolean("enabled", c.enabled);
        d.putInt("tier", level(p));
        d.putBoolean("legacy", e.legacy && c.legacyKeepsApps);
        d.putLongArray("costs", c.tierCosts.clone());
        d.putBoolean("calls", c.callsEnabled);
        d.putBoolean("callUsed", callUsed(p.getServer(), e));
        if (source != null && locks != null) {
            d.putInt("src", source);
            d.putIntArray("locks", locks);
            if (refresh) d.putBoolean("refresh", true);
        }
        ServerPlayNetworking.send(p, new PhonePayloads.Sync(d));
    }

    /** Ticks between two calls (one Minecraft day). */
    private static final long CALL_COOLDOWN = 24000L;

    /**
     * Call used in the last 24000 world ticks. Measured with getTime(), which only moves forward: /time set and
     * doDaylightCycle = false don't leave it locked (with the day number it could stay that way for hundreds of days or forever).
     */
    static boolean callUsed(MinecraftServer server, PhoneState.Entry e) {
        if (e.lastCallTick < 0) return false;
        long now = server.getOverworld().getTime();
        return now >= e.lastCallTick && now - e.lastCallTick < CALL_COOLDOWN;
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
        if (st.isOf(TcgBlocks.BOOSTER_BOX_ITEM)) {
            // 1.0: a box needs the same tier as its pack (the pack's market price); boxMinTier can raise it on the phone
            int t = tierForPackUsd(PriceService.sealedUsd(st.getOrDefault(TcgBlocks.BOX_SET, "mew151"), PriceService.Sealed.PACK));
            return phone ? Math.max(PhoneConfig.INSTANCE.boxMinTier, t) : t;
        }
        if (CardInfo.isPack(st)) return tierForPackUsd(PriceService.sealedUsd(CardInfo.setId(CardInfo.id(st)), PriceService.Sealed.PACK));
        if (st.isOf(TcgBlocks.ROULETTE_ITEM)) return PhoneTier.ROULETTE_TIER;
        if (!phone) return 0;
        if (st.isOf(TcgBlocks.MYSTERY_BOX)) return PhoneTier.PHONE_MYSTERY_TIER;
        if (ShopServer.isPokedoll(st)) return PhoneTier.PHONE_PLUSH_TIER;
        return 0;
    }

    /**
     * What this player sees in that catalog: the Wholesaler brings boxes of extra sets at high tiers, and at the top
     * tier the shop-level set lock is bypassed. It is deterministic: opening and buying produce the same list.
     */
    public static List<ItemStack> storeView(ServerPlayerEntity p, int source, List<ItemStack> goods) {
        return applyPackLock(p, withExtraSets(p, source, goods));
    }

    /**
     * First step of storeView: the catalog with the Wholesaler's extra set boxes (without applying PackLock).
     * Phone (source -1): one x36 box for every pack it sells (see {@link #phoneBoxes}).
     */
    public static List<ItemStack> withExtraSets(ServerPlayerEntity p, int source, List<ItemStack> goods) {
        if (source == -1) return phoneBoxes(goods);
        int lv = perkLevel(p);
        List<ItemStack> g = goods;
        int extra = PhoneTier.EXTRA_BOX_SETS[lv];
        if (source != -1 && extra > 0) {
            Set<String> have = new HashSet<>();
            for (ItemStack st : goods) if (st.isOf(TcgBlocks.BOOSTER_BOX_ITEM)) have.add(st.getOrDefault(TcgBlocks.BOX_SET, ""));
            List<String> all = new ArrayList<>();
            for (Identifier id : Registries.ITEM.getIds())
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
            if (st.isOf(TcgBlocks.BOOSTER_BOX_ITEM)) {
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
    public static List<ItemStack> applyPackLock(ServerPlayerEntity p, List<ItemStack> goods) {
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
    public static double priceFactor(ServerPlayerEntity p, int source, double base) {
        int lv = perkLevel(p);
        return base * (source == -1 ? PhoneTier.PHONE_DISCOUNT[lv] : PhoneTier.WHOLESALE_DISCOUNT[lv]);
    }

    public static Text tierName(int level) {
        return Text.translatable("tcgshop.phoneup.tier." + PhoneTier.of(level).key);
    }

    // ------------------------------------------------------------------ actions

    private static void handle(ServerPlayerEntity p, NbtCompound d) {
        switch (d.getString("a")) {
            case "buy" -> buy(p, d.getInt("tier"));
            case "call" -> call(p, d.getString("kind"));
            case "sync" -> sync(p, null, null);
            default -> PhoneApps.handle(p, d); // 1.0: Messages, Loans and Stats apps
        }
    }

    /** Does the player carry a phone? (package-private since 1.0: the Messages / Loans apps use it too). */
    static boolean hasPhone(ServerPlayerEntity p) {
        return p.getInventory().contains(st -> st.isOf(TcgItems.TCG_PHONE));
    }

    static void buy(ServerPlayerEntity p, int target) {
        PhoneConfig c = PhoneConfig.INSTANCE;
        if (!c.enabled || !hasPhone(p)) return;
        if (Loans.blocksPhonePurchase(p)) return; // 1.0: no phone purchases while a loan is active
        PhoneState.Entry e = entry(p);
        if (target != e.tier + 1 || target > PhoneTier.MAX_LEVEL) { sync(p, null, null); return; }
        if (!CobbleDollarsBridge.isAvailable()) {
            p.sendMessage(Text.translatable("tcgshop.sell.no_bank").formatted(Formatting.RED), true);
            PhoneStore.sendResult(p, PhoneStore.NO_BANK, -1, ItemStack.EMPTY, 0, 0, target);
            return;
        }
        long cost = c.cost(target);
        long balance = CobbleDollarsBridge.getBalance(p);
        if (!CobbleDollarsBridge.tryCharge(p, cost)) {
            p.sendMessage(Text.translatable("tcgshop.shop.no_money", PriceService.format(cost)).formatted(Formatting.RED), true);
            p.getWorld().playSound(null, p.getBlockPos(), SoundEvents.ENTITY_VILLAGER_NO, SoundCategory.PLAYERS, 0.6f, 1f);
            PhoneStore.sendResult(p, PhoneStore.NO_MONEY, -1, ItemStack.EMPTY, 0, cost, target, balance);
            return;
        }
        e.tier = target;
        PhoneState.get(p.getServer()).markDirty();
        PhoneStore.sendResult(p, PhoneStore.UPGRADED, -1, ItemStack.EMPTY, 0, cost, target);
        p.sendMessage(Text.translatable("tcgshop.phoneup.bought", tierName(target).copy().formatted(Formatting.BOLD),
                Text.literal(PriceService.format(cost)).formatted(Formatting.GOLD)).formatted(Formatting.GREEN), false);
        p.getWorld().playSound(null, p.getBlockPos(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 0.7f, 1.2f);
        // resend the Market (new products and prices); the open phone updates without closing and,
        // if the player already closed it, it is not reopened
        ShopServer.openStore(p, -1, ShopServer.phoneCatalog(), ShopServer.PHONE_FACTOR, true);
    }

    static void call(ServerPlayerEntity p, String kind) {
        PhoneConfig c = PhoneConfig.INSTANCE;
        if (!c.callsEnabled || !hasPhone(p)) return;
        if (level(p) < PhoneTier.CALL_TIER) return;
        PhoneState.Entry e = entry(p);
        if (callUsed(p.getServer(), e)) {
            p.sendMessage(Text.translatable("tcgshop.phoneup.call.used").formatted(Formatting.YELLOW), true);
            sync(p, null, null);
            return;
        }
        boolean ok = switch (kind) {
            case "collector" -> callCollector(p);
            case "wholesaler" -> callWholesaler(p);
            default -> false;
        };
        if (!ok) return;
        e.lastCallTick = p.getServer().getOverworld().getTime();
        PhoneState.get(p.getServer()).markDirty();
        p.getWorld().playSound(null, p.getBlockPos(), SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), SoundCategory.PLAYERS, 0.8f, 1.4f);
        sync(p, null, null);
    }

    /** If you're in (or near) your shop, a big-spending collector customer comes; otherwise, a wandering Collector. */
    private static boolean callCollector(ServerPlayerEntity p) {
        ServerWorld w = p.getServerWorld();
        ShopZones.Zone best = null;
        double bestD = 96 * 96;
        for (ShopZones.Zone z : ShopZones.get(w).all()) {
            if (!z.owner.equals(p.getUuid())) continue;
            double dd = p.squaredDistanceTo(z.core.toCenterPos());
            if (dd < bestD) { bestD = dd; best = z; }
        }
        // via the normal customer path: walks up to an open door and respects capacity and queue; if the
        // shop isn't loaded, is full with a full queue, or all its doors are closed, the wanderer comes instead
        if (best != null && dev.tcgshop.shop.ShopLife.forceSpawnCustomer(w, best, "WHALE")) {
            p.sendMessage(Text.translatable("tcgshop.phoneup.call.collector_shop").formatted(Formatting.GOLD), false);
            return true;
        }
        if (!w.getEntitiesByClass(TraderEntity.class, p.getBoundingBox().expand(48), t -> true).isEmpty()) {
            p.sendMessage(Text.translatable("tcgshop.phoneup.call.already_near").formatted(Formatting.YELLOW), true);
            return false;
        }
        if (spawnNear(w, p, TcgEntities.TRADER.create(w))) {
            p.sendMessage(Text.translatable("tcgshop.phoneup.call.collector_near").formatted(Formatting.GOLD), false);
            return true;
        }
        p.sendMessage(Text.translatable("tcgshop.phoneup.call.no_room").formatted(Formatting.RED), true);
        return false;
    }

    private static boolean callWholesaler(ServerPlayerEntity p) {
        ServerWorld w = p.getServerWorld();
        if (!w.getEntitiesByClass(WholesalerEntity.class, p.getBoundingBox().expand(48), t -> true).isEmpty()) {
            p.sendMessage(Text.translatable("tcgshop.phoneup.call.already_near").formatted(Formatting.YELLOW), true);
            return false;
        }
        if (spawnNear(w, p, TcgEntities.WHOLESALER.create(w))) {
            p.sendMessage(Text.translatable("tcgshop.phoneup.call.wholesaler").formatted(Formatting.GOLD), false);
            return true;
        }
        p.sendMessage(Text.translatable("tcgshop.phoneup.call.no_room").formatted(Formatting.RED), true);
        return false;
    }

    // ---- 0.9 random events: the same spawns as the phone calls, without using up the daily call

    /** Spawns the Wholesaler next to the player (if there isn't one nearby already). */
    public static boolean spawnWholesalerNear(ServerPlayerEntity p) {
        ServerWorld w = p.getServerWorld();
        if (!w.getEntitiesByClass(WholesalerEntity.class, p.getBoundingBox().expand(48), t -> true).isEmpty()) return true;
        return spawnNear(w, p, TcgEntities.WHOLESALER.create(w));
    }

    /** Spawns the black market dealer next to the player, with their offer, for that player only (if there isn't one nearby already). */
    public static boolean spawnBlackMarketNear(ServerPlayerEntity p) {
        ServerWorld w = p.getServerWorld();
        if (!w.getEntitiesByClass(dev.tcgshop.entity.BlackMarketEntity.class, p.getBoundingBox().expand(128), t -> true).isEmpty()) return true;
        dev.tcgshop.entity.BlackMarketEntity m = TcgEntities.BLACK_MARKET.create(w);
        if (m == null) return false;
        m.setForPlayer(p.getUuid());
        m.rollOffer();
        return spawnNear(w, p, m);
    }

    /** Places an entity on free ground 4-9 blocks from the player. */
    public static boolean spawnNearPlayer(ServerWorld w, ServerPlayerEntity p, @Nullable PathAwareEntity ent) {
        return spawnNear(w, p, ent);
    }

    private static boolean spawnNear(ServerWorld w, ServerPlayerEntity p, @Nullable PathAwareEntity ent) {
        if (ent == null) return false;
        for (int attempt = 0; attempt < 16; attempt++) {
            double ang = w.random.nextDouble() * Math.PI * 2;
            int dist = 4 + w.random.nextInt(6);
            int x = (int) Math.floor(p.getX() + Math.cos(ang) * dist), z = (int) Math.floor(p.getZ() + Math.sin(ang) * dist);
            BlockPos found = null;
            for (int dy = 3; dy >= -3 && found == null; dy--) { // first near the player's height (works indoors)
                BlockPos c = new BlockPos(x, p.getBlockY() + dy, z);
                if (w.getBlockState(c.down()).isSolidBlock(w, c.down()) && w.isAir(c) && w.isAir(c.up())) found = c;
            }
            if (found == null) {
                int y = w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
                BlockPos c = new BlockPos(x, y, z);
                if (Math.abs(y - p.getBlockY()) <= 6 && w.getBlockState(c.down()).isSolidBlock(w, c.down()) && w.isAir(c) && w.isAir(c.up())) found = c;
            }
            if (found == null) continue;
            ent.refreshPositionAndAngles(found.getX() + 0.5, found.getY(), found.getZ() + 0.5, w.random.nextFloat() * 360f, 0f);
            w.spawnEntity(ent);
            return true;
        }
        return false;
    }
}

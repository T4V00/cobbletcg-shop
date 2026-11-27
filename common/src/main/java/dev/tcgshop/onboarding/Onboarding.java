package dev.tcgshop.onboarding;

import dev.masterball.api.platform.event.ServerEvents;
import dev.masterball.api.card.CardInfo;
import dev.tcgshop.TcgShopConfig;
import dev.tcgshop.compat.CobblemonSpawn;
import dev.tcgshop.pricing.PriceService;
import dev.tcgshop.shop.CardSales;
import dev.tcgshop.shop.PackLock;
import dev.tcgshop.shop.ShopZones;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Mod welcome. The first time you join the world (1.0): N random sealed packs (TcgShopConfig.starterPackCount, 5 by
 * default) from the cheapest sets, plus the personal starter binder (25 pockets, with your name) already equipped in the
 * Accessories slot (or in the inventory without Accessories). The starting money ({@link dev.tcgshop.EconomyConfig#startingMoney})
 * is paid once, when that new player opens their first pack. Then the "Team Rocket Meowth" tutorial the first time you
 * create a shop (a normal, catchable wild Meowth appears next to you, stands still looking at you and gives you a few
 * short tips in speech bubbles: see MeowthTalk), and the one-time visitors of {@link OnboardingVisits}. The flags are
 * stored as player tags (saved with the player's data, so they last forever in that world).
 */
public final class Onboarding {
    /** Same tag as the old starter kit (CardSales): anyone who already got it does not get packs again. */
    public static final String STARTER_TAG = CardSales.STARTER_TAG;
    public static final String TUTORIAL_TAG = "tcgshop_tutorial";
    /**
     * 1.0: set together with the starter kit; while present, opening a pack pays the starting money once (then removed).
     * Players from before 1.0 never get it, so they are not paid again.
     */
    public static final String START_MONEY_TAG = "tcgshop_start_money_pending";
    /** Packs given at the start unless the config says otherwise (TcgShopConfig.starterPackCount). */
    public static final int STARTER_COUNT = 5;
    /** Default of starterPackCount before 1.0 (migrated to {@link #STARTER_COUNT} once, see {@link #migrateConfig}). */
    private static final int OLD_STARTER_COUNT = 3;
    /** Picked from the N cheapest sets (by pack price). */
    public static final int CHEAPEST_SETS = 8;
    /** Cobblemon properties of the tutorial Meowth. */
    public static final String MEOWTH_PROPS = "meowth level=10";

    private Onboarding() {}

    // ------------------------------------------------------------------ starter packs

    /** Server hooks of the onboarding (called once from TcgShop.onInitialize). */
    public static void register() {
        ServerEvents.STARTED.register(server -> migrateConfig());
        OnboardingVisits.register();
    }

    /**
     * 1.0: 5 starter packs instead of 3. Only the old default is replaced (a value an admin customized is kept), once,
     * with a marker in tcgshop-economy.json.
     */
    public static void migrateConfig() {
        dev.tcgshop.EconomyConfig eco = dev.tcgshop.EconomyConfig.INSTANCE;
        if (eco.starterPacksVersion >= 1) return;
        if (TcgShopConfig.INSTANCE.starterPackCount == OLD_STARTER_COUNT) {
            TcgShopConfig.INSTANCE.starterPackCount = STARTER_COUNT;
            TcgShopConfig.save();
        }
        eco.starterPacksVersion = 1;
        dev.tcgshop.EconomyConfig.save();
    }

    /**
     * On joining the world (ServerPlayConnectionEvents.JOIN): welcome packs and the starter binder only the first time.
     * Anyone who already has {@link #STARTER_TAG} (players from before 1.0 included) gets nothing new.
     */
    public static void onJoin(ServerPlayer player) {
        // players who already own a shop (worlds from before the tutorial) don't need the Meowth: mark it as seen
        if (!player.getTags().contains(TUTORIAL_TAG) && ownedShops(player) > 0) player.addTag(TUTORIAL_TAG);
        if (!TcgShopConfig.INSTANCE.starterPacks || player.getTags().contains(STARTER_TAG)) return;
        int count = Math.max(0, TcgShopConfig.INSTANCE.starterPackCount);
        List<Item> picks = count == 0 ? List.of() : pickStarterPacks(player.getRandom(), count);
        if (count > 0 && picks.isEmpty()) return; // no packs registered yet: retried on the next join
        player.addTag(STARTER_TAG);
        player.addTag(START_MONEY_TAG);
        for (Item it : picks) player.getInventory().placeItemBackInInventory(new ItemStack(it));
        boolean equipped = StarterBinder.give(player);
        if (!picks.isEmpty()) {
            player.displayClientMessage(Component.translatable("tcgshop.onboarding.starter", picks.size()).withStyle(ChatFormatting.GOLD), false);
            player.displayClientMessage(Component.translatable("tcgshop.onboarding.starter_hint").withStyle(ChatFormatting.GRAY), false);
        }
        player.displayClientMessage(Component.translatable(equipped ? "tcgshop.onboarding.binder_equipped" : "tcgshop.onboarding.binder_inventory")
                .withStyle(ChatFormatting.YELLOW), false);
        long money = dev.tcgshop.EconomyConfig.INSTANCE.startingMoney;
        if (money > 0 && dev.tcgshop.economy.CobbleDollarsBridge.isAvailable())
            player.displayClientMessage(Component.translatable("tcgshop.onboarding.money_hint", PriceService.format(money)).withStyle(ChatFormatting.GRAY), false);
        player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.4f);
    }

    /**
     * After a pack opening delivered at least one pack (PackOpener.finish): the first time, a new player gets the
     * starting money. One-time: the pending tag is removed only once the money was actually added (without
     * CobbleDollars it stays pending, so installing it later still pays it).
     */
    public static void onPackOpened(ServerPlayer player) {
        if (!player.getTags().contains(START_MONEY_TAG)) return;
        long money = dev.tcgshop.EconomyConfig.INSTANCE.startingMoney;
        if (money <= 0) {
            player.removeTag(START_MONEY_TAG);
            return;
        }
        if (!dev.tcgshop.economy.CobbleDollarsBridge.isAvailable()) return;
        player.removeTag(START_MONEY_TAG); // first: a failure below can never pay twice
        if (!dev.tcgshop.economy.CobbleDollarsBridge.add(player, money)) {
            player.addTag(START_MONEY_TAG);
            return;
        }
        player.displayClientMessage(Component.translatable("tcgshop.onboarding.start_money", Component.literal(PriceService.format(money)).withStyle(ChatFormatting.GOLD))
                .withStyle(ChatFormatting.YELLOW), false);
        player.displayClientMessage(Component.translatable("tcgshop.onboarding.start_money_hint").withStyle(ChatFormatting.GRAY), false);
        player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.8f, 1.2f);
    }

    /**
     * {@code count} random sealed packs from the {@link #CHEAPEST_SETS} (unlocked) sets with the cheapest pack.
     * Tries to use different sets; if there are fewer sets than packs, it repeats.
     */
    public static List<Item> pickStarterPacks(RandomSource random, int count) {
        Map<String, List<Item>> bySet = new LinkedHashMap<>();
        for (ResourceLocation id : BuiltInRegistries.ITEM.keySet()) {
            if (!CardInfo.NS.equals(id.getNamespace()) || !id.getPath().contains("/sealed_")) continue;
            String set = CardInfo.setId(id);
            if (set == null || set.isEmpty() || PackLock.locked(set)) continue;
            bySet.computeIfAbsent(set, k -> new ArrayList<>()).add(BuiltInRegistries.ITEM.get(id));
        }
        List<Item> out = new ArrayList<>();
        if (bySet.isEmpty() || count <= 0) return out;
        Map<String, Double> usd = new LinkedHashMap<>();
        for (String s : bySet.keySet()) {
            double v;
            try {
                v = PriceService.sealedUsd(s, PriceService.Sealed.PACK);
            } catch (Throwable e) {
                v = PriceService.Sealed.PACK.defaultUsd;
            }
            usd.put(s, v);
        }
        List<String> sets = new ArrayList<>(bySet.keySet());
        sets.sort(Comparator.comparingDouble(usd::get));
        List<String> pool = new ArrayList<>(sets.subList(0, Math.min(CHEAPEST_SETS, sets.size())));
        List<String> left = new ArrayList<>(pool);
        for (int i = 0; i < count; i++) {
            if (left.isEmpty()) left.addAll(pool);
            String set = left.remove(random.nextInt(left.size()));
            List<Item> items = bySet.get(set);
            out.add(items.get(random.nextInt(items.size())));
        }
        return out;
    }

    // ------------------------------------------------------------------ Meowth tutorial

    /** Meowth's name (translatable: each client sees it in its own language). */
    public static Component meowthName() {
        return Component.translatable("tcgshop.tutorial.speaker");
    }

    /**
     * On shop creation (ShopCoreBlock.onPlaced, after the zone is created): if it is the player's first shop,
     * the Team Rocket Meowth appears next to them, stands still looking at them and gives a few tips in speech bubbles.
     * No chat spam: at most one short line (and the "right click" hint goes to the action bar).
     */
    public static void onShopCreated(ServerPlayer player) {
        if (player.getTags().contains(TUTORIAL_TAG)) return;
        player.addTag(TUTORIAL_TAG);
        // the new zone is already created: if they have more than one, they're a veteran (world from before the tutorial)
        if (ownedShops(player) > 1) return;
        Entity meowth = MeowthTalk.findNear(player); // never two Meowths: if one of theirs is already nearby, that one talks
        if (meowth == null) meowth = spawnMeowth(player);
        if (meowth != null && MeowthTalk.start(player, meowth)) {
            player.displayClientMessage(Component.translatable("tcgshop.tutorial.appears", meowthName().copy().withStyle(ChatFormatting.GOLD)).withStyle(ChatFormatting.YELLOW), true);
        } else {
            player.displayClientMessage(Component.translatable("tcgshop.tutorial.no_meowth").withStyle(ChatFormatting.GRAY), false);
        }
    }

    /** The player's shops (zones) across all server dimensions. */
    public static int ownedShops(ServerPlayer player) {
        if (player.getServer() == null) return 0;
        return ShopZones.ownedBy(player.getServer(), player.getUUID()).size(); // 1.1: dormant shops count too
    }

    /**
     * /tcgshop tutorial: if their Meowth is nearby, it repeats the speech bubbles; otherwise the same short lines go to
     * chat (without spawning another Meowth).
     */
    public static int replay(ServerPlayer player) {
        Entity meowth = MeowthTalk.findNear(player);
        if (meowth != null && MeowthTalk.start(player, meowth)) {
            player.displayClientMessage(Component.translatable("tcgshop.tutorial.appears", meowthName().copy().withStyle(ChatFormatting.GOLD)).withStyle(ChatFormatting.YELLOW), true);
            return 1;
        }
        MeowthTalk.stop(player); // a talk paused far away is not resumed later repeating the same thing
        for (int i = 0; i < MeowthTalk.LINES; i++) player.displayClientMessage(MeowthTalk.chatLine(MeowthTalk.line(i)), false);
        return 1;
    }

    /** Normal wild Meowth (catchable, persistent) nicknamed "Team Rocket Meowth". Null if Cobblemon is not present. */
    public static Entity spawnMeowth(ServerPlayer player) {
        if (!CobblemonSpawn.available()) return null;
        ServerLevel world = player.serverLevel();
        Vec3 spot = CobblemonSpawn.freeSpotNear(world, player.position(), player.getLookAngle());
        if (spot == null) spot = player.position();
        // make it face the player
        Vec3 d = player.position().subtract(spot);
        float yaw = (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90.0);
        Entity e = CobblemonSpawn.spawnWild(world, spot, yaw, MEOWTH_PROPS, Component.translatable("tcgshop.tutorial.speaker"));
        if (e == null) return null;
        MeowthTalk.tag(e, player);
        world.sendParticles(ParticleTypes.POOF, spot.x, spot.y + 0.5, spot.z, 14, 0.3, 0.3, 0.3, 0.02);
        world.sendParticles(ParticleTypes.WAX_ON, spot.x, spot.y + 0.8, spot.z, 8, 0.4, 0.4, 0.4, 0.05);
        world.playSound(null, e.blockPosition(), SoundEvents.CAT_AMBIENT, SoundSource.NEUTRAL, 1.0f, 1.1f);
        return e;
    }
}

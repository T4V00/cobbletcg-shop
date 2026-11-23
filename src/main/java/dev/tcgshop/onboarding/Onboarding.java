package dev.tcgshop.onboarding;

import dev.masterball.api.card.CardInfo;
import dev.tcgshop.TcgShopConfig;
import dev.tcgshop.compat.CobblemonSpawn;
import dev.tcgshop.pricing.PriceService;
import dev.tcgshop.shop.CardSales;
import dev.tcgshop.shop.PackLock;
import dev.tcgshop.shop.ShopZones;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(server -> migrateConfig());
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
    public static void onJoin(ServerPlayerEntity player) {
        // players who already own a shop (worlds from before the tutorial) don't need the Meowth: mark it as seen
        if (!player.getCommandTags().contains(TUTORIAL_TAG) && ownedShops(player) > 0) player.addCommandTag(TUTORIAL_TAG);
        if (!TcgShopConfig.INSTANCE.starterPacks || player.getCommandTags().contains(STARTER_TAG)) return;
        int count = Math.max(0, TcgShopConfig.INSTANCE.starterPackCount);
        List<Item> picks = count == 0 ? List.of() : pickStarterPacks(player.getRandom(), count);
        if (count > 0 && picks.isEmpty()) return; // no packs registered yet: retried on the next join
        player.addCommandTag(STARTER_TAG);
        player.addCommandTag(START_MONEY_TAG);
        for (Item it : picks) player.getInventory().offerOrDrop(new ItemStack(it));
        boolean equipped = StarterBinder.give(player);
        if (!picks.isEmpty()) {
            player.sendMessage(Text.translatable("tcgshop.onboarding.starter", picks.size()).formatted(Formatting.GOLD), false);
            player.sendMessage(Text.translatable("tcgshop.onboarding.starter_hint").formatted(Formatting.GRAY), false);
        }
        player.sendMessage(Text.translatable(equipped ? "tcgshop.onboarding.binder_equipped" : "tcgshop.onboarding.binder_inventory")
                .formatted(Formatting.YELLOW), false);
        long money = dev.tcgshop.EconomyConfig.INSTANCE.startingMoney;
        if (money > 0 && dev.tcgshop.economy.CobbleDollarsBridge.isAvailable())
            player.sendMessage(Text.translatable("tcgshop.onboarding.money_hint", PriceService.format(money)).formatted(Formatting.GRAY), false);
        player.getWorld().playSound(null, player.getBlockPos(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.6f, 1.4f);
    }

    /**
     * After a pack opening delivered at least one pack (PackOpener.finish): the first time, a new player gets the
     * starting money. One-time: the pending tag is removed only once the money was actually added (without
     * CobbleDollars it stays pending, so installing it later still pays it).
     */
    public static void onPackOpened(ServerPlayerEntity player) {
        if (!player.getCommandTags().contains(START_MONEY_TAG)) return;
        long money = dev.tcgshop.EconomyConfig.INSTANCE.startingMoney;
        if (money <= 0) {
            player.removeCommandTag(START_MONEY_TAG);
            return;
        }
        if (!dev.tcgshop.economy.CobbleDollarsBridge.isAvailable()) return;
        player.removeCommandTag(START_MONEY_TAG); // first: a failure below can never pay twice
        if (!dev.tcgshop.economy.CobbleDollarsBridge.add(player, money)) {
            player.addCommandTag(START_MONEY_TAG);
            return;
        }
        player.sendMessage(Text.translatable("tcgshop.onboarding.start_money", Text.literal(PriceService.format(money)).formatted(Formatting.GOLD))
                .formatted(Formatting.YELLOW), false);
        player.sendMessage(Text.translatable("tcgshop.onboarding.start_money_hint").formatted(Formatting.GRAY), false);
        player.playSoundToPlayer(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.8f, 1.2f);
    }

    /**
     * {@code count} random sealed packs from the {@link #CHEAPEST_SETS} (unlocked) sets with the cheapest pack.
     * Tries to use different sets; if there are fewer sets than packs, it repeats.
     */
    public static List<Item> pickStarterPacks(Random random, int count) {
        Map<String, List<Item>> bySet = new LinkedHashMap<>();
        for (Identifier id : Registries.ITEM.getIds()) {
            if (!CardInfo.NS.equals(id.getNamespace()) || !id.getPath().contains("/sealed_")) continue;
            String set = CardInfo.setId(id);
            if (set == null || set.isEmpty() || PackLock.locked(set)) continue;
            bySet.computeIfAbsent(set, k -> new ArrayList<>()).add(Registries.ITEM.get(id));
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
    public static Text meowthName() {
        return Text.translatable("tcgshop.tutorial.speaker");
    }

    /**
     * On shop creation (ShopCoreBlock.onPlaced, after the zone is created): if it is the player's first shop,
     * the Team Rocket Meowth appears next to them, stands still looking at them and gives a few tips in speech bubbles.
     * No chat spam: at most one short line (and the "right click" hint goes to the action bar).
     */
    public static void onShopCreated(ServerPlayerEntity player) {
        if (player.getCommandTags().contains(TUTORIAL_TAG)) return;
        player.addCommandTag(TUTORIAL_TAG);
        // the new zone is already created: if they have more than one, they're a veteran (world from before the tutorial)
        if (ownedShops(player) > 1) return;
        Entity meowth = MeowthTalk.findNear(player); // never two Meowths: if one of theirs is already nearby, that one talks
        if (meowth == null) meowth = spawnMeowth(player);
        if (meowth != null && MeowthTalk.start(player, meowth)) {
            player.sendMessage(Text.translatable("tcgshop.tutorial.appears", meowthName().copy().formatted(Formatting.GOLD)).formatted(Formatting.YELLOW), true);
        } else {
            player.sendMessage(Text.translatable("tcgshop.tutorial.no_meowth").formatted(Formatting.GRAY), false);
        }
    }

    /** The player's shops (zones) across all server dimensions. */
    public static int ownedShops(ServerPlayerEntity player) {
        if (player.getServer() == null) return 0;
        int n = 0;
        for (ServerWorld w : player.getServer().getWorlds())
            for (ShopZones.Zone z : ShopZones.get(w).all())
                if (player.getUuid().equals(z.owner)) n++;
        return n;
    }

    /**
     * /tcgshop tutorial: if their Meowth is nearby, it repeats the speech bubbles; otherwise the same short lines go to
     * chat (without spawning another Meowth).
     */
    public static int replay(ServerPlayerEntity player) {
        Entity meowth = MeowthTalk.findNear(player);
        if (meowth != null && MeowthTalk.start(player, meowth)) {
            player.sendMessage(Text.translatable("tcgshop.tutorial.appears", meowthName().copy().formatted(Formatting.GOLD)).formatted(Formatting.YELLOW), true);
            return 1;
        }
        MeowthTalk.stop(player); // a talk paused far away is not resumed later repeating the same thing
        for (int i = 0; i < MeowthTalk.LINES; i++) player.sendMessage(MeowthTalk.chatLine(MeowthTalk.line(i)), false);
        return 1;
    }

    /** Normal wild Meowth (catchable, persistent) nicknamed "Team Rocket Meowth". Null if Cobblemon is not present. */
    public static Entity spawnMeowth(ServerPlayerEntity player) {
        if (!CobblemonSpawn.available()) return null;
        ServerWorld world = player.getServerWorld();
        Vec3d spot = CobblemonSpawn.freeSpotNear(world, player.getPos(), player.getRotationVector());
        if (spot == null) spot = player.getPos();
        // make it face the player
        Vec3d d = player.getPos().subtract(spot);
        float yaw = (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90.0);
        Entity e = CobblemonSpawn.spawnWild(world, spot, yaw, MEOWTH_PROPS, Text.translatable("tcgshop.tutorial.speaker"));
        if (e == null) return null;
        MeowthTalk.tag(e, player);
        world.spawnParticles(ParticleTypes.POOF, spot.x, spot.y + 0.5, spot.z, 14, 0.3, 0.3, 0.3, 0.02);
        world.spawnParticles(ParticleTypes.WAX_ON, spot.x, spot.y + 0.8, spot.z, 8, 0.4, 0.4, 0.4, 0.05);
        world.playSound(null, e.getBlockPos(), SoundEvents.ENTITY_CAT_AMBIENT, SoundCategory.NEUTRAL, 1.0f, 1.1f);
        return e;
    }
}

package dev.tcgshop.shop;

import dev.tcgshop.EconomyConfig;
import dev.tcgshop.economy.CobbleDollarsBridge;
import dev.tcgshop.entity.EmployeeEntity;
import dev.tcgshop.pricing.PriceService;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * 1.1 (staff update): cashier upgrades, bought per shop from the phone (Shop app, Upgrades tab) and kept in
 * {@link SecurityLedger} next to the guard upgrades, so they stay with the shop and every cashier (both registers) uses
 * them.
 *
 *  · SPEED     time to ring a customer up (2 s / 1.5 s / 1.1 s / 0.8 s) and walking speed.
 *  · RANGE     how far from the register a cashier walks to serve a waiting customer (5 / 9 / 14 / 24 blocks).
 *  · ACCURACY  careful service: happier customers and better tip odds (+2 / +4 / +6 points).
 *  · HAGGLE    the top upgrade, one level: a cashier closes haggling customers at a fair price when the owner is not
 *              dealing with them. Hard to get: Speed, Range and Accuracy at level 3, a shop of at least
 *              EconomyConfig.cashierHaggleMinTier, and an expensive price.
 *
 * Bought through StaffPayload role "cashier_up_<stat>" (routed by ShopServer.manageStaff).
 */
public final class CashierUpgrades {
    public enum Stat { SPEED, RANGE, ACCURACY, HAGGLE }

    /** Highest level of each stat. */
    private static final int[] MAX = {3, 3, 3, 1};
    /** Base price of each level per stat, before EconomyConfig.cashierUpgradeCostMult. */
    private static final long[][] COST = {
            {8_000, 20_000, 50_000},   // speed
            {6_000, 15_000, 40_000},   // range
            {6_000, 15_000, 40_000},   // accuracy
            {400_000}};                // handles hagglers
    private static final double[] SERVICE = {1.0, 0.75, 0.55, 0.4};
    private static final int[] RANGE = {5, 9, 14, 24};

    private CashierUpgrades() {}

    public static int maxLevel(Stat s) { return MAX[s.ordinal()]; }

    public static int[] levels(MinecraftServer server, UUID zoneId) {
        if (server == null || zoneId == null) return new int[4];
        return SecurityLedger.zone(server, zoneId).cashier;
    }

    private static int lv(int[] lv, Stat s) {
        return lv == null || lv.length <= s.ordinal() ? 0 : Math.max(0, Math.min(MAX[s.ordinal()], lv[s.ordinal()]));
    }

    /** Multiplier of the time a cashier needs to ring a customer up. */
    public static double serviceMult(int[] lv) { return SERVICE[lv(lv, Stat.SPEED)]; }

    /** Navigation speed of a cashier walking to a customer. */
    public static double walkSpeed(int[] lv) { return 1.0 + 0.1 * lv(lv, Stat.SPEED); }

    /** Blocks from its post a cashier walks to serve someone. */
    public static int range(int[] lv) { return RANGE[lv(lv, Stat.RANGE)]; }

    /** Extra checkout tip chance (0-1). */
    public static double tipBonus(int[] lv) { return 0.02 * lv(lv, Stat.ACCURACY); }

    /** Chance that a served customer leaves a bit happier. */
    public static double moodChance(int[] lv) { return 0.25 * lv(lv, Stat.ACCURACY); }

    public static boolean handlesHagglers(int[] lv) { return lv(lv, Stat.HAGGLE) >= 1; }

    /** Can the next level of `s` be bought at all (the haggle upgrade has extra requirements)? */
    public static boolean unlocked(int[] lv, Stat s, int tierRank) {
        if (s != Stat.HAGGLE) return true;
        return lv(lv, Stat.SPEED) >= 3 && lv(lv, Stat.RANGE) >= 3 && lv(lv, Stat.ACCURACY) >= 3 && tierRank >= haggleMinTier().rank();
    }

    /** Shop tier needed for the haggle upgrade (EconomyConfig.cashierHaggleMinTier, Iron by default). */
    public static ShopTier haggleMinTier() {
        ShopTier t = ShopTier.byKey(EconomyConfig.INSTANCE.cashierHaggleMinTier);
        return t != null ? t : ShopTier.IRON;
    }

    public static long cost(Stat s, int nextLevel) {
        if (nextLevel < 1 || nextLevel > MAX[s.ordinal()]) return -1;
        double mult = Math.max(0, Math.min(100, EconomyConfig.INSTANCE.cashierUpgradeCostMult));
        return Math.max(0, Math.round(COST[s.ordinal()][nextLevel - 1] * mult));
    }

    @org.jetbrains.annotations.Nullable
    public static Stat parse(String key) {
        if (key == null) return null;
        try {
            return Stat.valueOf(key.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static Component statName(Stat s) {
        return Component.translatable("tcgshop.cashup.stat." + s.name().toLowerCase(Locale.ROOT));
    }

    /** Buys the next level of `statKey` for that shop's cashiers (owner only, with a cashier hired). */
    public static void buy(ServerPlayer player, UUID zoneId, String statKey) {
        ServerLevel world = player.serverLevel();
        ShopZones zones = ShopZones.get(world);
        ShopZones.Zone z = zones.byId(zoneId);
        if (z == null || !z.owner.equals(player.getUUID())) return;
        Stat s = parse(statKey);
        if (s == null) return;
        if (!z.cashier && !z.cashier2) {
            player.displayClientMessage(Component.translatable("tcgshop.cashup.no_cashier").withStyle(ChatFormatting.RED), false);
            return;
        }
        int[] lv = levels(world.getServer(), z.id);
        int cur = lv(lv, s);
        if (cur >= MAX[s.ordinal()]) {
            player.displayClientMessage(Component.translatable("tcgshop.cashup.maxed", statName(s)).withStyle(ChatFormatting.YELLOW), false);
            return;
        }
        if (!unlocked(lv, s, z.tier)) {
            player.displayClientMessage(Component.translatable("tcgshop.cashup.locked_haggle",
                    Component.translatable("tcgshop.shop.tier." + haggleMinTier().key)).withStyle(ChatFormatting.RED), false);
            return;
        }
        if (dev.tcgshop.phone.Loans.blocksPurchase(player, "tcgshop.loan.notice.blocked_staff")) return;
        long price = cost(s, cur + 1);
        if (!CobbleDollarsBridge.tryCharge(player, price)) {
            player.displayClientMessage(Component.translatable("tcgshop.shop.no_money", PriceService.format(price)).withStyle(ChatFormatting.RED), false);
            return;
        }
        zones.addCost(player.getUUID(), price);
        lv[s.ordinal()] = cur + 1;
        SecurityLedger.dirty(world.getServer());
        zones.setDirty();
        for (EmployeeEntity e : world.getEntitiesOfClass(EmployeeEntity.class, z.box().inflate(32), e -> !e.isGuard() && z.id.equals(e.zoneId())))
            e.announce(Component.translatable("tcgshop.cashup.thanks." + world.random.nextInt(4)));
        world.playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.3f);
        player.displayClientMessage(Component.translatable("tcgshop.cashup.bought", statName(s), cur + 1, MAX[s.ordinal()],
                Component.literal(PriceService.format(price)).withStyle(ChatFormatting.GOLD)).withStyle(ChatFormatting.GREEN), false);
        ShopServer.syncZones(world.getServer());
    }
}

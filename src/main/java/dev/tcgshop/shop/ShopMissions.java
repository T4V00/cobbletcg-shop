package dev.tcgshop.shop;

import dev.tcgshop.pricing.PriceService;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

/**
 * Shop missions (0.9): concrete steps to attract customers. They are evaluated from the shop's state in
 * ShopLife.recompute (every minute, without scanning blocks on their own) and stored as a bitmask in the zone.
 *
 *  · Customer flow: the chance of someone arriving is multiplied by {@link #flow(int)}, which grows with the
 *    completed missions (0 → 35 %, all → 115 %). Shops that already met the conditions don't lose customers:
 *    missions are state-based, they complete themselves on recompute.
 *  · Capacity: base per shop tier (ShopTier.maxCustomers) + up to {@link #MAX_BONUS} for mission milestones and for
 *    seats / play tables. With the shop full, customers queue outside (ShopLife.queueGate).
 */
public final class ShopMissions {
    private ShopMissions() {}

    /** Order = from easiest to most expensive: the first one not completed is the "Next step". */
    public enum Mission {
        STOCK("stock", 2),        // 2 displays with product
        REGISTER("register", 1),  // cash register or hired cashier
        BUILDING("building", 1),  // premises with roof and walls
        LIGHT("light", 8),        // good lighting (average light >= 8)
        PRICES("prices", 1),      // fair prices (price factor >= 0)
        DECOR("decor", 3),        // minimum decoration
        VARIETY("variety", 6),    // 6 different products
        BOX("box", 1),            // a booster box for sale
        CLEAN("clean", 1),        // no bad smell or monsters inside
        PLAY("play", 1),          // play table or 2 seats
        REVIEWS("reviews", 5),    // 5 reviews averaging 3★ or more
        TIER("tier", 2);          // Stone shop or better (tier 2)

        public final String key;
        public final int goal;

        Mission(String key, int goal) {
            this.key = key;
            this.goal = goal;
        }
    }

    public static final int COUNT = Mission.values().length;
    public static final double FLOW_MIN = 0.35, FLOW_MAX = 1.15;
    /** Maximum capacity bonus over the tier's base. */
    public static final int MAX_BONUS = 3;
    /** Mission milestones that give +1 capacity. */
    public static final int MILESTONE_A = 6, MILESTONE_B = COUNT;
    /** Customers turned away in a day before suggesting a shop upgrade. */
    public static final int OVERFLOW_SUGGEST = 4;

    /** What ShopLife.recompute has seen of the shop. */
    public static final class Snapshot {
        public int stockedDisplays, stocked, variety, decor, price, smell, hostilesInside, seats, playTables;
        public boolean boosterBox, register, envKnown;
        public double roof, walls, light;
    }

    /** Evaluates the missions; stores each one's progress in z.missionProgress and returns the mask. */
    public static int evaluate(ShopZones.Zone z, Snapshot s) {
        int[] prog = new int[COUNT];
        prog[Mission.STOCK.ordinal()] = s.stockedDisplays;
        prog[Mission.REGISTER.ordinal()] = s.register || z.cashier || z.cashier2 ? 1 : 0;
        prog[Mission.BUILDING.ordinal()] = s.envKnown && s.roof >= 0.6 && s.walls >= 0.5 ? 1 : 0;
        prog[Mission.LIGHT.ordinal()] = s.envKnown ? (int) Math.floor(s.light) : 0;
        prog[Mission.PRICES.ordinal()] = s.stocked > 0 && s.price >= 0 ? 1 : 0;
        prog[Mission.DECOR.ordinal()] = s.decor;
        prog[Mission.VARIETY.ordinal()] = s.variety;
        prog[Mission.BOX.ordinal()] = s.boosterBox ? 1 : 0;
        prog[Mission.CLEAN.ordinal()] = s.smell < 20 && s.hostilesInside == 0 ? 1 : 0;
        prog[Mission.PLAY.ordinal()] = s.playTables >= 1 || s.seats >= 2 ? 1 : 0;
        prog[Mission.REVIEWS.ordinal()] = z.reviews.size() >= 5 && z.avgStars() < 3 ? Math.min(4, z.reviews.size()) : z.reviews.size();
        prog[Mission.TIER.ordinal()] = z.tier + 1;
        int mask = 0;
        for (Mission m : Mission.values()) if (prog[m.ordinal()] >= m.goal) mask |= 1 << m.ordinal();
        z.missionProgress = prog;
        return mask;
    }

    public static boolean done(int mask, Mission m) {
        return mask >= 0 && (mask & (1 << m.ordinal())) != 0;
    }

    public static int count(int mask) {
        return mask < 0 ? 0 : Integer.bitCount(mask & ((1 << COUNT) - 1));
    }

    /** First mission not completed (the "Next step") or -1 if all are done. */
    public static int nextStep(int mask) {
        for (Mission m : Mission.values()) if (!done(mask, m)) return m.ordinal();
        return -1;
    }

    /** Customer arrival multiplier: 0.35 with no missions … 1.15 with all (smooth curve). Not computed: 1. */
    public static double flow(int mask) {
        if (mask < 0) return 1.0;
        double f = count(mask) / (double) COUNT;
        return FLOW_MIN + (FLOW_MAX - FLOW_MIN) * Math.pow(f, 0.8);
    }

    /** Capacity bonus: +1 with 6 missions, +1 with all, +1 for a play area (play table or 3+ seats). Max. +3. */
    public static int capacityBonus(ShopZones.Zone z) {
        int n = count(z.missions), b = 0;
        if (n >= MILESTONE_A) b++;
        if (n >= MILESTONE_B) b++;
        if (z.playTables >= 1 || z.seats >= 3) b++;
        return Math.min(MAX_BONUS, b);
    }

    public static int capacity(ShopZones.Zone z) {
        return z.tierInfo().maxCustomers + capacityBonus(z);
    }

    /**
     * Actual capacity right now: the shop's + the events' temporary modifier (Mods.maxCustAdd, e.g. a
     * rush +2 or construction work -2). It's the one used by the door (ShopLife.queueGate), the spawn caps and the window.
     */
    public static int effectiveCapacity(ShopZones.Zone z) {
        return Math.max(1, capacity(z) + dev.tcgshop.events.EventManager.mods(z).maxCustAdd);
    }

    /** How many can wait outside in the queue (based on the shop's own capacity, without events). */
    public static int queueMax(ShopZones.Zone z) {
        return 2 + capacity(z) / 4;
    }

    // ------------------------------------------------------------------ capacity overflows (to suggest an upgrade)

    private static void rollDay(ShopZones.Zone z, long day) {
        if (z.overflowDay == day) return;
        z.overflowPrev = z.overflowDay == day - 1 ? z.overflowToday : 0;
        z.overflowToday = 0;
        z.overflowDay = day;
    }

    public static void addOverflow(ShopZones.Zone z, long day) {
        rollDay(z, day);
        z.overflowToday = Math.min(9999, z.overflowToday + 1);
    }

    public static int overflowToday(ShopZones.Zone z, long day) {
        return z.overflowDay == day ? z.overflowToday : 0;
    }

    public static int overflowPrev(ShopZones.Zone z, long day) {
        if (z.overflowDay == day) return z.overflowPrev;
        return z.overflowDay == day - 1 ? z.overflowToday : 0;
    }

    /** Does it fill up often? Then the window suggests moving to the next tier. */
    public static boolean suggestUpgrade(ShopZones.Zone z, long day) {
        if (z.tierInfo().next() == null) return false;
        return overflowToday(z, day) >= OVERFLOW_SUGGEST || overflowPrev(z, day) >= OVERFLOW_SUGGEST;
    }

    // ------------------------------------------------------------------ data for the window and the phone

    /** What the owner sees when right-clicking the shop block ("shop_status" window). */
    public static NbtCompound statusNbt(ServerWorld world, ShopZones.Zone z, ServerPlayerEntity owner) {
        ShopZones zones = ShopZones.get(world);
        long day = world.getTimeOfDay() / 24000;
        NbtCompound d = new NbtCompound();
        d.putString("owner", z.ownerName);
        d.putInt("tier", z.tier);
        d.putInt("rep", z.reputation);
        d.putString("revenue", PriceService.format(z.revenue));
        d.putString("balance", PriceService.format(z.balance));
        long profit = zones.revenue(owner.getUuid()) - zones.costs(owner.getUuid());
        d.putString("profit", PriceService.format(profit));
        d.putBoolean("profit_neg", profit < 0);
        d.putInt("sales", z.sales);
        d.putInt("reviews", z.reviews.size());
        d.putDouble("stars", z.avgStars());
        d.putInt("customers", z.customers);
        // capacity
        int capEff = effectiveCapacity(z);
        d.putInt("cap", capEff);
        d.putInt("cap_event", capEff - capacity(z));
        d.putInt("cap_base", z.tierInfo().maxCustomers);
        d.putInt("cap_bonus", capacityBonus(z));
        d.putInt("inside", ShopLife.insideCount(world, z));
        d.putInt("queued", ShopLife.queuedCount(z));
        d.putInt("queue_max", queueMax(z));
        d.putInt("seats", z.seats);
        d.putInt("play_tables", z.playTables);
        d.putInt("ovf_today", overflowToday(z, day));
        d.putInt("ovf_prev", overflowPrev(z, day));
        d.putBoolean("upgrade", suggestUpgrade(z, day));
        ShopTier next = z.tierInfo().next();
        d.putString("next_tier", next == null ? "" : next.key);
        d.putInt("next_cap", next == null ? 0 : next.maxCustomers + capacityBonus(z));
        // missions and flow
        d.putInt("missions", z.missions);
        d.putInt("flow", (int) Math.round(flow(z.missions) * 100));
        d.putInt("flow_max", (int) Math.round(FLOW_MAX * 100));
        if (z.missionProgress != null) d.putIntArray("prog", z.missionProgress);
        int[] goals = new int[COUNT];
        for (Mission m : Mission.values()) goals[m.ordinal()] = m.goal;
        d.putIntArray("goal", goals);
        return d;
    }

    /** Summary for the owner's zone sync (phone): goes inside the "ext" compound. */
    public static void writeSync(ShopZones.Zone z, NbtCompound ext) {
        ext.putInt("missions", z.missions);
        ext.putInt("mission_count", count(z.missions));
        ext.putInt("flow", (int) Math.round(flow(z.missions) * 100));
        ext.putInt("cap", effectiveCapacity(z));
        ext.putInt("queued", ShopLife.queuedCount(z));
    }
}

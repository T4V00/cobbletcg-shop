package dev.tcgshop.shop;

import dev.masterball.api.platform.event.ServerEvents;
import dev.tcgshop.pricing.PriceService;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.UUID;

/**
 * Shop missions: concrete steps to attract customers. They are evaluated from the shop's state in
 * ShopLife.recompute (every minute, without scanning blocks on their own) and from a few counters.
 *
 * 1.0: 100 missions in categories (stock, displays, variety, grading, decoration, lighting, building, comfort, staff,
 * cleanliness, security, sales, revenue, customers, reputation, reviews, tiers, events, tips and haggling).
 *  · The first {@link #CORE} missions are the 0.9 ones, with the same bits: they still live in Zone.missions /
 *    missionsEver (an int mask, so older builds and saves keep working). The full set (a BitSet, any size) and the
 *    counters live in {@link MissionLedger}; old worlds are migrated on the first evaluation without notices.
 *  · Customer flow: {@link #flow(ShopZones.Zone)}. The 12 core missions take it from 35 % to 115 % as before; the
 *    other 88 add at most +15 % more (130 %), so 100 missions do not make the flow explode.
 *  · Capacity: base per shop tier + up to {@link #MAX_BONUS} (core milestones, play area, 50 and 90 missions).
 *  · Luck: the more missions completed, the more often customers accept a near-miss haggle or a purchase
 *    ({@link #acceptBonus}) and leave tips ({@link #tipBonus}, hooked into TipLedger.zoneChanceBonus: per shop).
 */
public final class ShopMissions {
    private ShopMissions() {}

    /** Mission groups (tcgshop.mission.cat.<key>) with their accent color in the shop menu. */
    public enum Category {
        STOCK("stock", 0xFF4FC3F7), DISPLAYS("displays", 0xFF9575CD), VARIETY("variety", 0xFF26A69A), GRADING("grading", 0xFFFFCA28),
        DECOR("decor", 0xFFEC407A), LIGHT("light", 0xFFFFEE58), BUILDING("building", 0xFFA1887F), COMFORT("comfort", 0xFFBCAAA4),
        STAFF("staff", 0xFF42A5F5), CLEAN("clean", 0xFF9CCC65), SECURITY("security", 0xFFEF5350), SALES("sales", 0xFF66BB6A),
        REVENUE("revenue", 0xFFFFB300), CUSTOMERS("customers", 0xFF29B6F6), REPUTATION("reputation", 0xFF7986CB), REVIEWS("reviews", 0xFFFFD54F),
        TIER("tier", 0xFFB0BEC5), EVENTS("events", 0xFFFF7043), TIPS("tips", 0xFFD4E157), HAGGLE("haggle", 0xFFCE93D8);

        public final String key;
        public final int color;

        Category(String key, int color) {
            this.key = key;
            this.color = color;
        }
    }

    /**
     * NEVER reorder or remove: the ordinal is the mission's bit. The first 12 are the 0.9 missions (same bits).
     * goal = progress needed; diff = difficulty 1..5 (the "Next step" is the easiest one not done); money = goal in ₽.
     */
    public enum Mission {
        // ---- 0.9 core missions (bits 0..11)
        STOCK("stock", Category.STOCK, 2, 1),          // 2 displays with product
        REGISTER("register", Category.STAFF, 1, 1),    // cash register or hired cashier
        BUILDING("building", Category.BUILDING, 1, 1), // premises with roof and walls
        LIGHT("light", Category.LIGHT, 8, 1),          // good lighting (average light >= 8)
        PRICES("prices", Category.SALES, 1, 1),        // fair prices (price factor >= 0)
        DECOR("decor", Category.DECOR, 3, 1),          // minimum decoration
        VARIETY("variety", Category.VARIETY, 6, 1),    // 6 different products
        BOX("box", Category.STOCK, 1, 2),              // a booster box for sale
        CLEAN("clean", Category.CLEAN, 1, 1),          // no bad smell or monsters inside
        PLAY("play", Category.COMFORT, 1, 1),          // play table or 2 seats
        REVIEWS("reviews", Category.REVIEWS, 5, 2),    // 5 reviews averaging 3★ or more
        TIER("tier", Category.TIER, 1, 2),             // Stone shop or better
        // ---- 1.0: stock
        STOCK_30("stock_30", Category.STOCK, 30, 2),
        STOCK_75("stock_75", Category.STOCK, 75, 3),
        STOCK_150("stock_150", Category.STOCK, 150, 4),
        PACKS_5("packs_5", Category.STOCK, 5, 1),
        BOXES_3("boxes_3", Category.STOCK, 3, 3),
        SHELVES_FULL("shelves_full", Category.STOCK, 75, 3),
        STOCK_VALUE("stock_value", Category.STOCK, 250_000, 4, true),
        // ---- displays
        DISPLAYS_5("displays_5", Category.DISPLAYS, 5, 2),
        DISPLAYS_10("displays_10", Category.DISPLAYS, 10, 3),
        DISPLAYS_20("displays_20", Category.DISPLAYS, 20, 4),
        SHOWCASES_3("showcases_3", Category.DISPLAYS, 3, 3),
        RGB_2("rgb_2", Category.DISPLAYS, 2, 4),
        PREMIUM_8("premium_8", Category.DISPLAYS, 8, 4),
        // ---- variety
        VARIETY_12("variety_12", Category.VARIETY, 12, 2),
        VARIETY_20("variety_20", Category.VARIETY, 20, 3),
        VARIETY_35("variety_35", Category.VARIETY, 35, 4),
        VARIETY_60("variety_60", Category.VARIETY, 60, 5),
        ALL_KINDS("all_kinds", Category.VARIETY, 4, 3),
        // ---- grading
        GRADED_1("graded_1", Category.GRADING, 1, 2),
        GRADED_5("graded_5", Category.GRADING, 5, 3),
        GEM_MINT("gem_mint", Category.GRADING, 1, 4),
        SHOWPIECE("showpiece", Category.GRADING, 50_000, 4, true),
        // ---- decoration
        DECOR_6("decor_6", Category.DECOR, 6, 2),
        DECOR_10("decor_10", Category.DECOR, 10, 3),
        DECOR_20("decor_20", Category.DECOR, 20, 4),
        POSTERS_3("posters_3", Category.DECOR, 3, 2),
        PLUSHIES_3("plushies_3", Category.DECOR, 3, 2),
        PLUSHIES_10("plushies_10", Category.DECOR, 10, 4),
        PEDESTALS_2("pedestals_2", Category.DECOR, 2, 3),
        STATUE("statue", Category.DECOR, 1, 4),
        STATUES_3("statues_3", Category.DECOR, 3, 5),
        // ---- lighting
        LIGHT_11("light_11", Category.LIGHT, 11, 3),
        LIGHT_13("light_13", Category.LIGHT, 13, 4),
        // ---- building
        DOOR("door", Category.BUILDING, 1, 1),
        DOORS_2("doors_2", Category.BUILDING, 2, 3),
        ENCLOSED("enclosed", Category.BUILDING, 1, 3),
        COZY("cozy", Category.BUILDING, 75, 4),
        SAFETY_80("safety_80", Category.BUILDING, 80, 3),
        VALUE_75("value_75", Category.BUILDING, 75, 4),
        // ---- comfort
        SEATS_4("seats_4", Category.COMFORT, 4, 2),
        SEATS_8("seats_8", Category.COMFORT, 8, 3),
        TABLES_2("tables_2", Category.COMFORT, 2, 3),
        PET("pet", Category.COMFORT, 1, 2),
        POKEMON_FRIEND("pokemon_friend", Category.COMFORT, 1, 2),
        // ---- staff
        CASHIER("cashier", Category.STAFF, 1, 2),
        GUARD("guard", Category.STAFF, 1, 2),
        CASHIER2("cashier2", Category.STAFF, 1, 3),
        FULL_STAFF("full_staff", Category.STAFF, 3, 4),
        REGISTERS_2("registers_2", Category.STAFF, 2, 2),
        // ---- cleanliness
        SPOTLESS("spotless", Category.CLEAN, 1, 2),
        PEST_FREE("pest_free", Category.CLEAN, 1, 3),
        // ---- security
        DETECTORS_2("detectors_2", Category.SECURITY, 2, 2),
        CATCH_1("catch_1", Category.SECURITY, 1, 2),
        CATCH_10("catch_10", Category.SECURITY, 10, 4),
        RECOVER_5("recover_5", Category.SECURITY, 5, 3),
        // ---- sales
        SALES_10("sales_10", Category.SALES, 10, 1),
        SALES_50("sales_50", Category.SALES, 50, 2),
        SALES_250("sales_250", Category.SALES, 250, 3),
        SALES_1000("sales_1000", Category.SALES, 1000, 4),
        SALES_5000("sales_5000", Category.SALES, 5000, 5),
        DAY_SALES_10("day_sales_10", Category.SALES, 10, 2),
        DAY_SALES_40("day_sales_40", Category.SALES, 40, 4),
        // ---- revenue
        REV_10K("rev_10k", Category.REVENUE, 10_000, 1, true),
        REV_100K("rev_100k", Category.REVENUE, 100_000, 2, true),
        REV_1M("rev_1m", Category.REVENUE, 1_000_000, 3, true),
        REV_10M("rev_10m", Category.REVENUE, 10_000_000, 4, true),
        DAY_REV_50K("day_rev_50k", Category.REVENUE, 50_000, 3, true),
        DAY_REV_500K("day_rev_500k", Category.REVENUE, 500_000, 5, true),
        // ---- customers
        CUSTOMERS_100("customers_100", Category.CUSTOMERS, 100, 2),
        WHALES_3("whales_3", Category.CUSTOMERS, 3, 3),
        REGULARS_8("regulars_8", Category.CUSTOMERS, 8, 3),
        PEAK_5("peak_5", Category.CUSTOMERS, 5, 3),
        // ---- reputation
        REP_60("rep_60", Category.REPUTATION, 60, 2),
        REP_80("rep_80", Category.REPUTATION, 80, 4),
        REP_95("rep_95", Category.REPUTATION, 95, 5),
        // ---- reviews
        REVIEWS_GOOD("reviews_good", Category.REVIEWS, 10, 3),
        FIVE_STARS_10("five_stars_10", Category.REVIEWS, 10, 4),
        // ---- tiers
        TIER_IRON("tier_iron", Category.TIER, 1, 3),
        TIER_DIAMOND("tier_diamond", Category.TIER, 1, 4),
        TIER_NETHERITE("tier_netherite", Category.TIER, 1, 5),
        // ---- events
        EVENTS_1("events_1", Category.EVENTS, 1, 2),
        EVENTS_15("events_15", Category.EVENTS, 15, 4),
        // ---- tips
        TIPS_1("tips_1", Category.TIPS, 1, 1),
        TIPS_50("tips_50", Category.TIPS, 50, 3),
        TIP_BIG("tip_big", Category.TIPS, 2_500, 3, true),
        // ---- haggling
        DEALS_10("deals_10", Category.HAGGLE, 10, 2),
        HAGGLES_25("haggles_25", Category.HAGGLE, 25, 3),
        CHARISMA_10("charisma_10", Category.HAGGLE, 10, 4);

        public final String key;
        public final Category cat;
        public final int goal;
        public final int diff;
        /** The goal is an amount of money (shown formatted). */
        public final boolean money;

        Mission(String key, Category cat, int goal, int diff) {
            this(key, cat, goal, diff, false);
        }

        Mission(String key, Category cat, int goal, int diff, boolean money) {
            this.key = key;
            this.cat = cat;
            this.goal = goal;
            this.diff = diff;
            this.money = money;
        }

        /** One of the 12 original (0.9) missions, stored in the zone's int mask. */
        public boolean core() { return ordinal() < CORE; }
    }

    /** Total number of missions. */
    public static final int COUNT = Mission.values().length;
    /** The 0.9 missions (bits 0..11 of Zone.missions). */
    public static final int CORE = 12;
    private static final int CORE_MASK = (1 << CORE) - 1;
    /** Flow with no missions, with the 12 core missions and with all of them. */
    public static final double FLOW_MIN = 0.35, FLOW_CORE = 1.15, FLOW_MAX = 1.30;
    /** Maximum capacity bonus over the tier's base. */
    public static final int MAX_BONUS = 5;
    /** Capacity milestones: core missions (A, B = all core) and total missions (C, D). */
    public static final int MILESTONE_A = 6, MILESTONE_B = CORE, MILESTONE_C = 50, MILESTONE_D = 90;
    /** Customers turned away in a day before suggesting a shop upgrade. */
    public static final int OVERFLOW_SUGGEST = 4;

    /** Lifecycle hooks (ShopLife.register calls it): ledgers loaded on start, forgotten on stop; tip luck hook. */
    public static void init() {
        TipLedger.chanceBonus = ShopMissions::ownerTipBonus;
        TipLedger.zoneChanceBonus = ShopMissions::tipBonus; // 1.0: checkout tips use the luck of the shop where the sale happens
        ServerEvents.STARTED.register(server -> {
            MissionLedger.get(server);
            Charisma.get(server);
        });
        ServerEvents.STOPPED.register(server -> {
            MissionLedger.clearActive();
            Charisma.clearActive();
        });
    }

    /** What ShopLife.recompute has seen of the shop. */
    public static final class Snapshot {
        public int stockedDisplays, stocked, variety, decor, price, smell, hostilesInside, seats, playTables;
        public boolean boosterBox, register, envKnown;
        public double roof, walls, light;
        // 1.0
        public int packs, boxes, slots, kinds, posters, dolls, pedestals, statues, showcases, rgbDisplays, premiumDisplays;
        public int graded, gemGraded, registers, doors, woodenDoors, hostilesNear, pets, pokemon, inside;
        public int aesthetics, value, safety;
        public long stockValue, topCard, day = -1;
    }

    private static int clampInt(long v) {
        return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, v));
    }

    private static int bool(boolean b) { return b ? 1 : 0; }

    /**
     * Evaluates all missions: stores each one's progress in z.missionProgress, the full set and counters in the
     * MissionLedger (if a server is given) and returns the 0.9 mask (the first 12 bits) for Zone.missions.
     */
    public static int evaluate(MinecraftServer server, ShopZones.Zone z, Snapshot s) {
        MissionLedger.Data d = server == null ? null : MissionLedger.of(server, z);
        if (d != null) {
            if (s.day >= 0) d.rollDay(z, s.day);
            d.peakInside = Math.max(d.peakInside, s.inside);
            countEvents(z, d);
        }
        TipLedger.Entry tips = TipLedger.of(server, z.owner);
        Charisma.Entry ch = Charisma.of(server, z.owner);
        ShopTier tier = z.tierInfo();
        int nrev = z.reviews.size(), five = 0;
        for (ShopZones.Review r : z.reviews) if (r.stars() >= 5) five++;
        double avg = z.avgStars();
        int bestRegular = 0;
        for (ShopZones.Regular r : z.regulars) bestRegular = Math.max(bestRegular, r.visits());
        int[] p = new int[COUNT];
        for (Mission m : Mission.values()) {
            p[m.ordinal()] = switch (m) {
                case STOCK -> s.stockedDisplays;
                case REGISTER -> bool(s.register || z.cashier || z.cashier2);
                case BUILDING -> bool(s.envKnown && s.roof >= 0.6 && s.walls >= 0.5);
                case LIGHT -> s.envKnown ? (int) Math.floor(s.light) : 0;
                case PRICES -> bool(s.stocked > 0 && s.price >= 0);
                case DECOR, DECOR_6, DECOR_10, DECOR_20 -> s.decor;
                case VARIETY, VARIETY_12, VARIETY_20, VARIETY_35, VARIETY_60 -> s.variety;
                case BOX -> bool(s.boosterBox);
                case CLEAN -> bool(s.smell < 20 && z.poops.isEmpty() && s.hostilesInside == 0); // 1.1: no droppings either
                case PLAY -> bool(s.playTables >= 1 || s.seats >= 2);
                case REVIEWS -> nrev >= 5 && avg < 3 ? Math.min(4, nrev) : nrev;
                case TIER -> bool(tier.atLeast(ShopTier.STONE));
                case STOCK_30, STOCK_75, STOCK_150 -> s.stocked;
                case PACKS_5 -> s.packs;
                case BOXES_3 -> s.boxes;
                case SHELVES_FULL -> s.slots >= 12 ? (int) Math.round(100.0 * s.stocked / s.slots) : 0;
                case STOCK_VALUE -> clampInt(s.stockValue);
                case DISPLAYS_5, DISPLAYS_10, DISPLAYS_20 -> s.stockedDisplays;
                case SHOWCASES_3 -> s.showcases;
                case RGB_2 -> s.rgbDisplays;
                case PREMIUM_8 -> s.premiumDisplays;
                case ALL_KINDS -> s.kinds;
                case GRADED_1, GRADED_5 -> s.graded;
                case GEM_MINT -> s.gemGraded;
                case SHOWPIECE -> clampInt(s.topCard);
                case POSTERS_3 -> s.posters;
                case PLUSHIES_3, PLUSHIES_10 -> s.dolls;
                case PEDESTALS_2 -> s.pedestals;
                case STATUE, STATUES_3 -> s.statues;
                case LIGHT_11, LIGHT_13 -> s.envKnown ? (int) Math.floor(s.light) : 0;
                case DOOR -> bool(s.woodenDoors > 0 || s.doors > 0);
                case DOORS_2 -> s.woodenDoors;
                case ENCLOSED -> bool(s.envKnown && s.roof >= 0.9 && s.walls >= 0.85);
                case COZY -> s.envKnown ? s.aesthetics : 0;
                case SAFETY_80 -> s.safety;
                case VALUE_75 -> s.stocked > 0 ? s.value : 0;
                case SEATS_4, SEATS_8 -> s.seats;
                case TABLES_2 -> s.playTables;
                case PET -> bool(s.pets > 0);
                case POKEMON_FRIEND -> bool(s.pokemon > 0 && s.pokemon <= 4);
                case CASHIER -> bool(z.cashier);
                case GUARD -> bool(z.guard);
                case CASHIER2 -> bool(z.cashier2);
                case FULL_STAFF -> bool(z.cashier) + bool(z.cashier2) + bool(z.guard);
                case REGISTERS_2 -> s.registers;
                case SPOTLESS -> bool(z.smell == 0 && z.poops.isEmpty() && z.grime <= 10 && s.stocked > 0); // 1.1: no droppings, hardly any grime
                case PEST_FREE -> bool(s.hostilesInside == 0 && s.hostilesNear == 0 && s.stocked > 0);
                case DETECTORS_2 -> z.detectors;
                case CATCH_1, CATCH_10 -> z.detained;
                case RECOVER_5 -> z.recovered;
                case SALES_10, SALES_50, SALES_250, SALES_1000, SALES_5000 -> z.sales;
                case DAY_SALES_10, DAY_SALES_40 -> d == null ? 0 : Math.max(d.bestDaySales, d.salesToday(z));
                case REV_10K, REV_100K, REV_1M, REV_10M -> clampInt(z.revenue);
                case DAY_REV_50K, DAY_REV_500K -> d == null ? 0 : clampInt(Math.max(d.bestDayRevenue, d.revenueToday(z)));
                case CUSTOMERS_100 -> z.customers;
                case WHALES_3 -> z.whales;
                case REGULARS_8 -> z.regulars.size();
                case PEAK_5 -> d == null ? s.inside : d.peakInside;
                case REP_60, REP_80, REP_95 -> z.reputation;
                case REVIEWS_GOOD -> nrev >= 10 && avg < 4 ? 9 : nrev;
                case FIVE_STARS_10 -> five;
                case TIER_IRON -> bool(tier.atLeast(ShopTier.IRON));
                case TIER_DIAMOND -> bool(tier.atLeast(ShopTier.DIAMOND));
                case TIER_NETHERITE -> bool(tier.atLeast(ShopTier.NETHERITE));
                case EVENTS_1, EVENTS_15 -> d == null ? 0 : d.eventsResolved;
                case TIPS_1, TIPS_50 -> tips.count;
                case TIP_BIG -> clampInt(tips.best);
                case DEALS_10 -> z.dealsAccepted;
                case HAGGLES_25 -> ch.hagglesWon;
                case CHARISMA_10 -> Charisma.levelForXp(ch.xp);
            };
        }
        BitSet done = new BitSet(COUNT);
        int legacy = 0;
        for (Mission m : Mission.values()) {
            if (p[m.ordinal()] < m.goal) continue;
            done.set(m.ordinal());
            if (m.core()) legacy |= 1 << m.ordinal();
        }
        z.missionProgress = p;
        if (d != null) {
            d.done.clear();
            d.done.or(done);
            d.completed = done.cardinality();
            if (!d.initialized) {
                // first 1.0 evaluation of this shop: what is already met (and what 0.9 already announced) is not announced
                d.ever.or(done);
                for (int i = 0; i < CORE; i++) if ((z.missionsEver & (1 << i)) != 0) d.ever.set(i);
                d.initialized = true;
            }
            MissionLedger.dirty();
        }
        return legacy;
    }

    /** Counts newly resolved events from the zone's event history (kept for 7 days, so nothing is missed). */
    private static void countEvents(ShopZones.Zone z, MissionLedger.Data d) {
        for (dev.tcgshop.events.ZoneEvents.Hist h : z.events.hist) {
            if (!"resolved".equals(h.outcome())) continue;
            String key = h.day() + "|" + h.id();
            if (d.seenEvents.contains(key)) continue;
            d.seenEvents.add(key);
            d.eventsResolved = Math.min(1_000_000, d.eventsResolved + 1);
        }
        while (d.seenEvents.size() > 64) d.seenEvents.remove(0);
    }

    // ------------------------------------------------------------------ queries

    /** 0.9 mask query: only the core missions are in the int mask. */
    public static boolean done(int mask, Mission m) {
        return mask >= 0 && m.core() && (mask & (1 << m.ordinal())) != 0;
    }

    /** Is that mission done in this shop (full set)? */
    public static boolean done(ShopZones.Zone z, Mission m) {
        MissionLedger.Data d = MissionLedger.peek(z);
        if (d != null && d.initialized) return d.done.get(m.ordinal());
        return done(z.missions, m);
    }

    /** Core missions completed in a 0.9 mask. */
    public static int count(int mask) {
        return mask < 0 ? 0 : Integer.bitCount(mask & CORE_MASK);
    }

    /** Missions completed in this shop (all 100; before the first 1.0 evaluation, the core ones). */
    public static int completedCount(ShopZones.Zone z) {
        MissionLedger.Data d = MissionLedger.peek(z);
        if (d != null && d.initialized) return d.completed;
        return count(z.missions);
    }

    /** Total number of missions. */
    public static int total() {
        return COUNT;
    }

    /** First core mission not completed (0.9 "Next step") or -1 if all core missions are done. */
    public static int nextStep(int mask) {
        for (Mission m : Mission.values()) if (m.core() && !done(mask, m)) return m.ordinal();
        return -1;
    }

    /** Next step in this shop: the easiest mission not done (closest to its goal among equals), or -1 if all are done. */
    public static int nextStep(ShopZones.Zone z) {
        if (z.missions < 0) return -1;
        return nextStep(z, z.missionProgress);
    }

    private static int nextStep(ShopZones.Zone z, int[] prog) {
        Mission best = null;
        double bestFrac = -1;
        for (Mission m : Mission.values()) {
            if (done(z, m)) continue;
            double frac = prog != null && m.ordinal() < prog.length ? Math.max(0, Math.min(0.999, prog[m.ordinal()] / (double) Math.max(1, m.goal))) : 0;
            if (best == null || m.diff < best.diff || (m.diff == best.diff && frac > bestFrac + 1e-9)) {
                best = m;
                bestFrac = frac;
            }
        }
        return best == null ? -1 : best.ordinal();
    }

    /** Next step from the client data (done bits + progress), same rule as on the server. */
    public static int nextStep(BitSet done, int[] prog) {
        Mission best = null;
        double bestFrac = -1;
        for (Mission m : Mission.values()) {
            if (done.get(m.ordinal())) continue;
            double frac = prog != null && m.ordinal() < prog.length ? Math.max(0, Math.min(0.999, prog[m.ordinal()] / (double) Math.max(1, m.goal))) : 0;
            if (best == null || m.diff < best.diff || (m.diff == best.diff && frac > bestFrac + 1e-9)) {
                best = m;
                bestFrac = frac;
            }
        }
        return best == null ? -1 : best.ordinal();
    }

    // ------------------------------------------------------------------ flow, capacity and luck

    /** 0.9 flow from a mask (core missions only): 0.35 with none … 1.15 with all 12. Not computed: 1. */
    public static double flow(int mask) {
        if (mask < 0) return 1.0;
        return flowFor(count(mask), 0);
    }

    /** Customer arrival multiplier of this shop: core missions up to 1.15, the other missions up to +0.15 more. */
    public static double flow(ShopZones.Zone z) {
        if (z.missions < 0) return 1.0;
        int core = count(z.missions);
        return flowFor(core, Math.max(0, completedCount(z) - core));
    }

    public static double flowFor(int core, int extra) {
        double fc = Math.max(0, Math.min(1, core / (double) CORE));
        double fe = Math.max(0, Math.min(1, extra / (double) Math.max(1, COUNT - CORE)));
        return FLOW_MIN + (FLOW_CORE - FLOW_MIN) * Math.pow(fc, 0.8) + (FLOW_MAX - FLOW_CORE) * Math.pow(fe, 0.9);
    }

    /**
     * Capacity bonus: +1 with 6 core missions, +1 with all 12, +1 for a play area (play table or 3+ seats), +1 with 50
     * missions and +1 with 90. Max +5. The Paper stall keeps its capacity (no bonus).
     */
    public static int capacityBonus(ShopZones.Zone z) {
        if (!z.tierInfo().atLeast(ShopTier.WOOD)) return 0;
        int core = count(z.missions), total = completedCount(z), b = 0;
        if (core >= MILESTONE_A) b++;
        if (core >= MILESTONE_B) b++;
        if (z.playTables >= 1 || z.seats >= 3) b++;
        if (total >= MILESTONE_C) b++;
        if (total >= MILESTONE_D) b++;
        return Math.min(MAX_BONUS, b);
    }

    public static int capacity(ShopZones.Zone z) {
        return z.baseCapacity() + capacityBonus(z);
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

    /** Luck curve 0..1 from the missions completed (smooth, slightly front-loaded). */
    public static double luck(int completed) {
        return Math.pow(Math.max(0, Math.min(1, completed / (double) COUNT)), 0.9);
    }

    /** Extra deal / purchase acceptance in this shop (0 .. missionMaxAcceptBonus, +15 % by default with all missions). */
    public static double acceptBonus(ShopZones.Zone z) {
        return acceptBonusFor(completedCount(z));
    }

    public static double acceptBonusFor(int completed) {
        return Math.max(0, Math.min(0.5, dev.tcgshop.EconomyConfig.INSTANCE.missionMaxAcceptBonus)) * luck(completed);
    }

    /** Extra checkout tip chance in this shop (0 .. missionMaxTipBonus, +8 points by default with all missions). */
    public static double tipBonus(ShopZones.Zone z) {
        return tipBonusFor(completedCount(z));
    }

    public static double tipBonusFor(int completed) {
        return Math.max(0, Math.min(0.5, dev.tcgshop.EconomyConfig.INSTANCE.missionMaxTipBonus)) * luck(completed);
    }

    /** TipLedger.chanceBonus hook: the owner's best shop (by missions completed) decides the tip luck. */
    public static double ownerTipBonus(MinecraftServer server, UUID owner) {
        if (server == null || owner == null) return 0;
        int best = 0;
        for (ServerLevel w : server.getAllLevels())
            for (ShopZones.Zone z : ShopZones.get(w).all()) if (owner.equals(z.owner)) best = Math.max(best, completedCount(z));
        return tipBonusFor(best);
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
        if (upgradeTarget(z) == null) return false;
        return overflowToday(z, day) >= OVERFLOW_SUGGEST || overflowPrev(z, day) >= OVERFLOW_SUGGEST;
    }

    /**
     * The first higher tier that really adds room: a pre-1.0 shop keeps its old, bigger side (a 16×16 Wood shop), so the
     * very next tier (Brick, 16×16) would not raise its size or its capacity. Null if no tier does.
     */
    public static ShopTier upgradeTarget(ShopZones.Zone z) {
        ShopTier t = z.tierInfo().next();
        while (t != null && t.size <= z.size() && t.maxCustomers <= z.baseCapacity()) t = t.next();
        return t;
    }

    // ------------------------------------------------------------------ data for the window and the phone

    /** What the owner sees when right-clicking the shop block ("shop_status" window, ShopStatusScreen). */
    public static CompoundTag statusNbt(ServerLevel world, ShopZones.Zone z, ServerPlayer owner) {
        MinecraftServer server = world.getServer();
        ShopZones zones = ShopZones.get(world);
        long day = world.getDayTime() / 24000;
        MissionLedger.Data md = MissionLedger.of(server, z);
        CompoundTag d = new CompoundTag();
        // ---- overview
        d.putString("owner", z.ownerName);
        d.putUUID("zone", z.id); // 1.1: for the open / close button
        d.putBoolean("closed", z.closed);
        d.putInt("tier", z.tier);
        d.putString("tier_key", z.tierInfo().key);
        d.putInt("size", z.size());
        d.putInt("rep", z.reputation);
        d.putString("revenue", PriceService.format(z.revenue));
        d.putString("balance", PriceService.format(z.balance));
        long profit = zones.revenue(owner.getUUID()) - zones.costs(owner.getUUID());
        d.putString("profit", PriceService.format(profit));
        d.putBoolean("profit_neg", profit < 0);
        d.putInt("sales", z.sales);
        d.putInt("reviews", z.reviews.size());
        d.putDouble("stars", z.avgStars());
        d.putInt("customers", z.customers);
        md.rollDay(z, day);
        MissionLedger.dirty();
        d.putInt("sales_today", md.salesToday(z));
        d.putString("revenue_today", PriceService.format(md.revenueToday(z)));
        d.putInt("best_day_sales", Math.max(md.bestDaySales, md.salesToday(z)));
        d.putString("best_day_revenue", PriceService.format(Math.max(md.bestDayRevenue, md.revenueToday(z))));
        TipLedger.Entry te = TipLedger.of(server, z.owner);
        d.putString("tips_today", PriceService.format(TipLedger.today(server, z.owner, day)));
        d.putString("tips_total", PriceService.format(te.total));
        d.putInt("tips_count", te.count);
        d.putString("tips_best", PriceService.format(te.best));
        // capacity
        int capEff = effectiveCapacity(z);
        d.putInt("cap", capEff);
        d.putInt("cap_event", capEff - capacity(z));
        d.putInt("cap_base", z.baseCapacity());
        d.putInt("cap_bonus", capacityBonus(z));
        d.putInt("inside", ShopLife.insideCount(world, z));
        d.putInt("queued", ShopLife.queuedCount(z));
        d.putInt("queue_max", queueMax(z));
        d.putInt("seats", z.seats);
        d.putInt("play_tables", z.playTables);
        d.putInt("ovf_today", overflowToday(z, day));
        d.putInt("ovf_prev", overflowPrev(z, day));
        d.putBoolean("upgrade", suggestUpgrade(z, day));
        ShopTier next = upgradeTarget(z);
        d.putString("next_tier", next == null ? "" : next.key);
        d.putInt("next_cap", next == null ? 0 : Math.max(next.maxCustomers, z.baseCapacity()) + capacityBonus(z));
        d.putInt("peak", md.peakInside);
        // ---- missions, flow and luck
        d.putInt("missions", z.missions); // 0.9 mask (-1 = not computed yet)
        d.putLongArray("done", md.initialized ? md.done.toLongArray() : legacyBits(z.missions).toLongArray());
        int completed = completedCount(z);
        d.putInt("completed", completed);
        d.putInt("total", COUNT);
        d.putInt("flow", (int) Math.round(flow(z) * 100));
        d.putInt("flow_core", (int) Math.round(FLOW_CORE * 100));
        d.putInt("flow_max", (int) Math.round(FLOW_MAX * 100));
        if (z.missionProgress != null) d.putIntArray("prog", z.missionProgress);
        d.putInt("luck_accept", (int) Math.round(acceptBonus(z) * 1000));
        d.putInt("luck_tip", (int) Math.round(tipBonus(z) * 1000));
        d.putInt("luck_accept_max", (int) Math.round(acceptBonusFor(COUNT) * 1000));
        d.putInt("luck_tip_max", (int) Math.round(tipBonusFor(COUNT) * 1000));
        d.putInt("events_resolved", md.eventsResolved);
        // ---- charisma (the viewer, who is the owner)
        Charisma.Entry ce = Charisma.of(server, owner.getUUID());
        int lvl = Charisma.levelForXp(ce.xp);
        d.putInt("ch_level", lvl);
        d.putInt("ch_max", Charisma.MAX_LEVEL);
        d.putLong("ch_xp", ce.xp);
        d.putLong("ch_from", Charisma.xpForLevel(lvl));
        d.putLong("ch_to", lvl >= Charisma.MAX_LEVEL ? Charisma.xpForLevel(lvl) : Charisma.xpForLevel(lvl + 1));
        d.putInt("ch_bonus", (int) Math.round(Charisma.bonusForLevel(lvl) * 1000));
        d.putInt("ch_bonus_max", (int) Math.round(Charisma.bonusForLevel(Charisma.MAX_LEVEL) * 1000));
        d.putInt("ch_haggles", ce.hagglesWon);
        d.putInt("ch_deals", ce.deals);
        d.putInt("ch_tips", ce.tips);
        // ---- customers
        d.putInt("whales", z.whales);
        d.putInt("deals", z.dealsAccepted);
        d.putInt("complaints", z.complaints);
        d.putInt("unattended", z.unattended);
        List<ShopZones.Regular> regs = new ArrayList<>(z.regulars);
        regs.sort((a, b) -> Integer.compare(b.visits(), a.visits()));
        ListTag rl = new ListTag();
        for (int i = 0; i < Math.min(8, regs.size()); i++) {
            CompoundTag c = new CompoundTag();
            c.putString("n", regs.get(i).name());
            c.putInt("v", regs.get(i).visits());
            rl.add(c);
        }
        d.put("regulars", rl);
        d.putInt("regulars_n", z.regulars.size());
        ListTag rv = new ListTag();
        for (int i = 0; i < Math.min(8, z.reviews.size()); i++) rv.add(z.reviews.get(i).toNbt());
        d.put("recent", rv);
        // ---- staff and security
        d.putBoolean("cashier", z.cashier);
        d.putString("cashier_name", z.cashierName);
        d.putBoolean("cashier2", z.cashier2);
        d.putString("cashier2_name", z.cashier2Name);
        d.putBoolean("guard", z.guard);
        // 1.1: every hired guard (up to three)
        StringBuilder guards = new StringBuilder();
        for (int i = 0; i < z.guardCount(); i++) { if (i > 0) guards.append(", "); guards.append(z.guardNameOf(i)); }
        d.putString("guard_name", z.guardCount() > 1 ? guards.toString() : z.guardName);
        d.putString("wages", PriceService.format(z.wagesPaid));
        d.putBoolean("day_shift", ShopServer.isDayShift(world));
        d.putInt("detectors", z.detectors);
        d.putInt("stolen", z.stolen);
        d.putInt("detained", z.detained);
        d.putInt("recovered", z.recovered);
        d.putInt("smell", z.smell);
        d.putInt("grime", z.grime); // 1.1 cleanliness
        d.putInt("poops", z.poops.size());
        d.putInt("graffiti", z.graffiti.size()); // 1.1: Group C review
        d.putInt("clean", Cleanliness.score(z));
        return d;
    }

    private static BitSet legacyBits(int mask) {
        BitSet b = new BitSet();
        if (mask < 0) return b;
        for (int i = 0; i < CORE; i++) if ((mask & (1 << i)) != 0) b.set(i);
        return b;
    }

    /** Summary for the owner's zone sync (phone, deal window): goes inside the "ext" compound. */
    public static void writeSync(ShopZones.Zone z, CompoundTag ext) {
        ext.putInt("missions", z.missions);
        ext.putInt("mission_count", completedCount(z));
        ext.putInt("mission_total", COUNT);
        ext.putInt("flow", (int) Math.round(flow(z) * 100));
        ext.putInt("cap", effectiveCapacity(z));
        ext.putInt("queued", ShopLife.queuedCount(z));
        ext.putInt("luck_accept", (int) Math.round(acceptBonus(z) * 1000));
        ext.putInt("luck_tip", (int) Math.round(tipBonus(z) * 1000));
        // charisma of the owner (the sync's details only go to the owner)
        int lvl = Charisma.levelForXp(Charisma.peek(z.owner).xp);
        ext.putInt("charisma", lvl);
        ext.putInt("charisma_bonus", (int) Math.round(Charisma.bonusForLevel(lvl) * 1000));
    }
}

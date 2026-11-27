package dev.tcgshop.shop;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;

/**
 * Shop zones of a dimension. Each shop block creates a square zone centered on it (5x5 … 100x100)
 * that goes from BELOW blocks below to tier.up above. It also stores the shop's metrics: sales,
 * reputation, employees, security and customers.
 */
public class ShopZones extends SavedData {
    public static final int BELOW = 2;

    /** A customer's review on leaving: stars (1-5) and reason (tcgshop.review.<key>). */
    public record Review(String name, int stars, String key, long day) {
        public CompoundTag toNbt() {
            CompoundTag c = new CompoundTag();
            c.putString("n", name);
            c.putInt("s", stars);
            c.putString("k", key);
            c.putLong("d", day);
            return c;
        }

        /** Full review sentence (1120 variants by stars and reason, tools/gen_lang_reviews.py); fixed per customer and day. */
        public String textKey() {
            int n = Math.floorMod((name + "#" + day + "#" + key).hashCode(), 8);
            return "tcgshop.reviewtext." + key + "." + Math.max(1, Math.min(5, stars)) + "." + n;
        }

        public static Review fromNbt(CompoundTag c) {
            return new Review(c.getString("n"), c.getInt("s"), c.getString("k"), c.getLong("d"));
        }
    }

    /** Regular customer (left happy): can come back with the same name and look. */
    public record Regular(String name, int skin, String title, int visits) {}

    public static final class Zone {
        public final UUID id;
        public final UUID owner;
        public String ownerName;
        /** Core position. 1.1: no longer final, a dormant shop is restored wherever its core item is placed again. */
        public BlockPos core;
        /** Tier rank (ShopTier.rank(): Paper = 0 ... Netherite = 11). Changes only with an in-place upgrade (ShopCoreBlock). */
        public int tier;
        /**
         * 1.0: zone side in blocks. Saved in the zone ("size") so a shop never shrinks or moves when the tier table
         * changes: pre-1.0 zones get their old side on load (wood 16 ... netherite 100), new ones the tier's side,
         * and an upgrade only ever grows it.
         */
        public int size;
        // money
        public long revenue, balance, wagesPaid;
        public int sales;
        // employees
        public boolean cashier, guard;
        public String cashierName = "", guardName = "";
        public long lastWageDay = -1;
        // security and customers
        public int stolen, detained, recovered, customers, complaints, dealsAccepted, whales;
        // reputation (0-100) and its breakdown (text key -> points)
        public int reputation = 50;
        public final Map<String, Integer> factors = new LinkedHashMap<>();
        /** 1.1: copies of each decor id in the shop (last recompute; not saved, synced to the owner for the decor tooltips). */
        public Map<net.minecraft.resources.ResourceLocation, Integer> decorCopies = new java.util.HashMap<>();
        public int decor, detectors, displays, buzz;
        /** Shop's bad smell (0-100): brought by some customers and removed with the deodorant spray. */
        public int smell;
        /** 1.1 (cleanliness update): grime from foot traffic (0-100) and the droppings on the floor (block positions). See Cleanliness. */
        public int grime;
        public final java.util.Set<Long> poops = new java.util.LinkedHashSet<>();
        /** 1.1: graffiti on the shop's walls (block positions, GraffitiBlock) and floor-mess incidents seen on the last tick (not saved). */
        public final java.util.Set<Long> graffiti = new java.util.LinkedHashSet<>();
        public int floorDirt;
        /** Latest reviews (most recent first) and regular customers who can come back. */
        public final java.util.List<Review> reviews = new java.util.ArrayList<>();
        public final java.util.List<Regular> regulars = new java.util.ArrayList<>();
        /** Customers who left without being served, the day's rejections (with a negative review) and infamy for killing NPCs. */
        public int unattended, rejectPenalties, infamy;
        /** Second register station. */
        public boolean cashier2;
        /** Good or bad reputation for how you treat the beggar (-15..15, returns to 0 over the days). */
        public int charity;
        public String cashier2Name = "";
        /** Last shift state seen (to notify the owner on start/end). Not saved. */
        public Boolean shiftSeen;
        public long rejectDay = -1, infamyDay = -1;
        /** What customers noticed about the surroundings in the last computation (not saved): no_roof, dark, hostile... */
        public final java.util.Set<String> flags = new java.util.HashSet<>();
        /**
         * Reputation with decimals (rises slowly, falls fast). -1 = not computed yet (old worlds: takes
         * {@link #reputation}). {@link #reputation} is always its rounded value.
         */
        public double repExact = -1;
        /** Last computed reputation target and ceiling (not saved; shown by the phone via factors m_target / m_ceiling). */
        public int repTarget = -1, repCeiling = 100;
        // ---- 0.9: shop missions and capacity (ShopMissions)
        /** Completed missions (bit i = ShopMissions.Mission i). -1 = not computed yet (old worlds: normal flow). */
        public int missions = -1;
        /** Missions ever completed (to notify only the first time, no spam if one comes and goes). */
        public int missionsEver;
        /** The shop's seats (chairs, benches, furniture sofas) and play tables in the last computation. */
        public int seats, playTables;
        /** Customers turned away due to capacity: today and the previous day (to suggest upgrading the shop). */
        public int overflowToday, overflowPrev;
        public long overflowDay = -1;
        /** Not saved: each mission's progress from the last computation (null = not computed) and when it was computed. */
        public int[] missionProgress;
        public long lastCalc = -1;
        /**
         * Not saved: what the lighting and decoration events (blackout, Flaaffy...) currently add / subtract to the
         * reputation. Only counts for the customers' effective reputation (ShopLife.effectiveRep), never for
         * settleReputation, same as Mods.repDelta.
         */
        public int eventEnvRep;
        /** 0.9 random events: active events, memory for reviews, history and revenue per day (ext.events). */
        public dev.tcgshop.events.ZoneEvents events = new dev.tcgshop.events.ZoneEvents();
        /**
         * 1.1: the owner closed the shop (ext "closed"; old worlds: open). Nothing runs for a closed shop: no customers,
         * thieves, new events, shifts, wages or reputation drift; active events are frozen (ShopOpenClose).
         */
        public boolean closed;
        /**
         * 1.1: the core block was removed (ext "dormant"). The record is kept whole (till, reputation, staff, missions...)
         * outside the active list (ShopZones.dormant): it protects nothing and runs nothing until the owner places the
         * core item again (or abandons it from the phone). Always closed too.
         */
        public boolean dormant;
        /** 1.1: game time the shop went dormant (ext "frozen_at"; -1 = not dormant): its events are shifted by the gap on restore. */
        public long frozenAt = -1;
        /**
         * 1.1 (staff update): guards hired beyond the first one (0-2, ext "guards_extra") and their names. Guard slot 0 is
         * the classic {@link #guard} / {@link #guardName}; slots 1-2 live here, so every "has a guard" check stays valid.
         */
        public int extraGuards;
        /** 1.1: market value of the stock on the shop's displays, last seen by ShopLife (ext "stock_v"; for the Ranking capital). */
        public long stockValue;
        public final String[] extraGuardNames = {"", ""};

        /** Guards hired (0-3). */
        public int guardCount() { return guard ? 1 + Math.max(0, Math.min(2, extraGuards)) : 0; }

        /** Name of the guard in `slot` (0-2). */
        public String guardNameOf(int slot) {
            return slot <= 0 ? guardName : slot - 1 < extraGuardNames.length ? extraGuardNames[slot - 1] : "";
        }

        /** Hires one more guard (the next free slot). Returns its slot, or -1 when there are already 3. */
        public int addGuard(String name) {
            if (!guard) { guard = true; guardName = name; extraGuards = 0; return 0; }
            if (extraGuards >= 2) return -1;
            extraGuardNames[extraGuards] = name;
            return 1 + extraGuards++;
        }

        /**
         * Removes the guard of `slot` (fired, fell, quit); the guards after it move down one slot (their entities do the
         * same, see EmployeeEntity.shiftGuardSlots). Returns the removed guard's name.
         */
        public String removeGuard(int slot) {
            int n = guardCount();
            if (n == 0) return "";
            slot = Math.max(0, Math.min(n - 1, slot));
            String who = guardNameOf(slot);
            String[] names = new String[n];
            for (int i = 0; i < n; i++) names[i] = guardNameOf(i);
            java.util.List<String> left = new java.util.ArrayList<>(java.util.Arrays.asList(names));
            left.remove(slot);
            guard = !left.isEmpty();
            guardName = guard ? left.get(0) : guardName;
            extraGuards = Math.max(0, left.size() - 1);
            for (int i = 0; i < 2; i++) extraGuardNames[i] = i + 1 < left.size() ? left.get(i + 1) : "";
            return who;
        }

        /** Current capacity: tier base + missions and seats bonus. */
        public int capacity() { return ShopMissions.capacity(this); }

        /** Customer arrival multiplier from completed missions. */
        public double flow() { return ShopMissions.flow(this); } // 1.0: all ~100 missions, not just the 12 core ones

        public void addReview(String name, int stars, String key, long day) {
            reviews.add(0, new Review(name, stars, key, day));
            while (reviews.size() > 24) reviews.remove(reviews.size() - 1);
        }

        /**
         * Applies the "raw" reputation value coming from the surroundings (ShopLife.recompute) with the reputation
         * economy:
         *  · diminishing returns above repSoftCap (the last points cost much more),
         *  · a ceiling from experience (sales made) and from average stars (with 5+ reviews),
         *  · rises little by little (slower the higher it is) and falls fast.
         */
        public void settleReputation(int raw) {
            dev.tcgshop.EconomyConfig c = dev.tcgshop.EconomyConfig.INSTANCE;
            int knee = Math.max(0, Math.min(100, c.repSoftCap));
            double t = Math.max(0, Math.min(100, raw));
            if (t > knee && knee < 100) t = knee + (100 - knee) * Math.pow((t - knee) / (100.0 - knee), Math.max(1, c.repTopExponent));
            // ceiling: experience (sales) is needed to reach the top, and you can't be very high with bad reviews
            double exp = c.repSalesForMax <= 0 ? 1 : Math.min(1, sales / (double) c.repSalesForMax);
            double ceiling = knee + (100 - knee) * Math.sqrt(exp);
            if (reviews.size() >= 5) ceiling = Math.min(ceiling, 40 + avgStars() * 12); // 5★ = 100, 4★ = 88, 3★ = 76
            t = Math.min(t, ceiling);
            if (repExact < 0) repExact = reputation;
            double gap = t - repExact;
            if (gap > 0) {
                double slow = Math.max(0.15, 1 - repExact / 125.0); // the higher it is, the harder it gets
                double step = Math.min(c.repMaxRisePerUpdate, Math.max(0.05, gap * c.repRiseRate * slow));
                repExact = Math.min(t, repExact + step);
            } else if (gap < 0) {
                // falls fast only if the low target repeats (two computations in a row); a single reading
                // (a passing monster, an odd computation) barely moves it, so as not to lose in a minute what takes hours
                boolean confirmed = repTarget >= 0 && repTarget <= Math.round(t) + 2;
                double rate = confirmed ? c.repFallRate : Math.min(c.repFallRate, 0.05);
                repExact = Math.max(t, repExact + (confirmed ? Math.min(-0.25, gap * rate) : gap * rate));
            }
            repExact = Math.max(0, Math.min(100, repExact));
            reputation = (int) Math.round(repExact);
            repTarget = (int) Math.round(t);
            repCeiling = (int) Math.round(ceiling);
            factors.put("m_target", repTarget);
            factors.put("m_ceiling", repCeiling);
        }

        /** What customers are willing to pay above market in this shop (0.2 = +20%). */
        public double premiumFor(ItemStack st) {
            return repPremium(reputation, isSealed(st));
        }

        public double avgStars() {
            if (reviews.isEmpty()) return 0;
            double s = 0;
            for (Review r : reviews) s += r.stars();
            return s / reviews.size();
        }

        public void remember(String name, int skin, String title) {
            for (int i = 0; i < regulars.size(); i++) {
                Regular r = regulars.get(i);
                if (r.name().equals(name)) { regulars.set(i, new Regular(name, skin, title, r.visits() + 1)); return; }
            }
            regulars.add(new Regular(name, skin, title, 1));
            if (regulars.size() > 16) {
                int worst = 0;
                for (int i = 1; i < regulars.size(); i++) if (regulars.get(i).visits() < regulars.get(worst).visits()) worst = i;
                regulars.remove(worst);
            }
        }

        public Zone(UUID id, UUID owner, String ownerName, BlockPos core, int tier) {
            this.id = id;
            this.owner = owner;
            this.ownerName = ownerName;
            this.core = core;
            this.tier = tier;
            this.size = tierInfo().size;
        }

        public ShopTier tierInfo() {
            return ShopTier.byRank(tier);
        }

        /** Zone side in blocks (may be bigger than the tier's for pre-1.0 shops). */
        public int size() {
            return size > 0 ? size : tierInfo().size;
        }

        /** Protected height above the core. */
        public int up() {
            return tierInfo().up;
        }

        /** Base capacity before missions/seats: the tier's, or more if the zone is bigger than the tier (pre-1.0 wood 16×16). */
        public int baseCapacity() {
            return Math.max(tierInfo().maxCustomers, ShopTier.capacityForSize(size()));
        }

        public AABB box() {
            return boxFor(core, size(), up());
        }

        public boolean contains(BlockPos p) {
            AABB b = box();
            return p.getX() >= b.minX && p.getX() < b.maxX && p.getY() >= b.minY && p.getY() < b.maxY && p.getZ() >= b.minZ && p.getZ() < b.maxZ;
        }

        public CompoundTag write() {
            CompoundTag c = new CompoundTag();
            c.putUUID("id", id);
            c.putUUID("owner", owner);
            c.putString("owner_name", ownerName);
            c.putLong("core", core.asLong());
            // 1.0: "tier" keeps its pre-1.0 meaning (old 7-tier index, for older builds); the real tier is "tier_key"
            c.putInt("tier", tierInfo().legacyIndex());
            c.putString("tier_key", tierInfo().key);
            c.putInt("size", size());
            c.putLong("revenue", revenue);
            c.putLong("balance", balance);
            c.putLong("wages", wagesPaid);
            c.putInt("sales", sales);
            c.putBoolean("cashier", cashier);
            c.putBoolean("guard", guard);
            c.putString("cashier_name", cashierName);
            c.putString("guard_name", guardName);
            c.putLong("last_wage_day", lastWageDay);
            c.putIntArray("metrics", new int[]{stolen, detained, recovered, customers, complaints, dealsAccepted, whales, reputation, decor, detectors, displays, buzz, smell});
            CompoundTag f = new CompoundTag();
            factors.forEach(f::putInt);
            c.put("factors", f);
            net.minecraft.nbt.ListTag rl = new net.minecraft.nbt.ListTag();
            for (Review r : reviews) rl.add(r.toNbt());
            c.put("reviews", rl);
            net.minecraft.nbt.ListTag gl = new net.minecraft.nbt.ListTag();
            for (Regular r : regulars) {
                CompoundTag g = new CompoundTag();
                g.putString("n", r.name());
                g.putInt("k", r.skin());
                g.putString("t", r.title());
                g.putInt("v", r.visits());
                gl.add(g);
            }
            c.put("regulars", gl);
            CompoundTag x = new CompoundTag();
            x.putInt("unattended", unattended);
            x.putInt("reject_pen", rejectPenalties);
            x.putLong("reject_day", rejectDay);
            x.putInt("infamy", infamy);
            x.putLong("infamy_day", infamyDay);
            x.putBoolean("cashier2", cashier2);
            x.putInt("charity", charity);
            x.putString("cashier2_name", cashier2Name);
            x.putDouble("rep_exact", repExact);
            x.putInt("missions", missions);
            x.putInt("missions_ever", missionsEver);
            x.putInt("seats", seats);
            x.putInt("play_tables", playTables);
            x.putInt("ovf_today", overflowToday);
            x.putInt("ovf_prev", overflowPrev);
            x.putLong("ovf_day", overflowDay);
            x.put("events", events.write());
            if (closed) x.putBoolean("closed", true); // 1.1: only written when set, so untouched shops save as before
            if (dormant) x.putBoolean("dormant", true);
            if (frozenAt >= 0) x.putLong("frozen_at", frozenAt);
            if (extraGuards > 0) { // 1.1: only written when set, so shops with one guard save as before
                x.putInt("guards_extra", extraGuards);
                x.putString("guard_name_1", extraGuardNames[0]);
                x.putString("guard_name_2", extraGuardNames[1]);
            }
            if (grime > 0) x.putInt("grime", grime); // 1.1 cleanliness: only written when set
            if (stockValue > 0) x.putLong("stock_v", stockValue); // 1.1 Ranking: only written when set
            if (!poops.isEmpty()) x.putLongArray("poops", poops.stream().mapToLong(Long::longValue).toArray());
            if (!graffiti.isEmpty()) x.putLongArray("graffiti", graffiti.stream().mapToLong(Long::longValue).toArray());
            c.put("ext", x);
            return c;
        }

        /** Tier rank of a saved zone: "tier_key" (1.0+) or, in older saves, the old 7-tier index in "tier". */
        static int savedTier(CompoundTag c) {
            ShopTier t = c.contains("tier_key") ? ShopTier.byKey(c.getString("tier_key")) : null;
            return (t != null ? t : ShopTier.fromLegacy(c.getInt("tier"))).rank();
        }

        /** Zone side of a saved zone: "size" (1.0+) or, in older saves, the side the old tier had (never shrinks the shop). */
        static int savedSize(CompoundTag c, ShopTier tier) {
            if (c.contains("size") && c.getInt("size") > 0) return c.getInt("size");
            return c.contains("tier_key") ? tier.size : ShopTier.legacySize(c.getInt("tier"));
        }

        public static Zone read(CompoundTag c) {
            Zone z = new Zone(c.getUUID("id"), c.getUUID("owner"), c.getString("owner_name"), BlockPos.of(c.getLong("core")), savedTier(c));
            z.size = savedSize(c, z.tierInfo());
            z.revenue = c.getLong("revenue");
            z.balance = c.getLong("balance");
            z.wagesPaid = c.getLong("wages");
            z.sales = c.getInt("sales");
            z.cashier = c.getBoolean("cashier");
            z.guard = c.getBoolean("guard");
            z.cashierName = c.getString("cashier_name");
            z.guardName = c.getString("guard_name");
            z.lastWageDay = c.contains("last_wage_day") ? c.getLong("last_wage_day") : -1;
            int[] m = c.getIntArray("metrics");
            int[] v = new int[13];
            System.arraycopy(m, 0, v, 0, Math.min(m.length, v.length));
            z.stolen = v[0]; z.detained = v[1]; z.recovered = v[2]; z.customers = v[3]; z.complaints = v[4];
            z.dealsAccepted = v[5]; z.whales = v[6]; z.reputation = m.length > 7 ? v[7] : 50; z.decor = v[8]; z.detectors = v[9]; z.displays = v[10]; z.buzz = v[11]; z.smell = v[12];
            CompoundTag f = c.getCompound("factors");
            for (String k : f.getAllKeys()) z.factors.put(k, f.getInt(k));
            net.minecraft.nbt.ListTag rl = c.getList("reviews", 10);
            for (int i = 0; i < rl.size(); i++) z.reviews.add(Review.fromNbt(rl.getCompound(i)));
            net.minecraft.nbt.ListTag gl = c.getList("regulars", 10);
            for (int i = 0; i < gl.size(); i++) {
                CompoundTag g = gl.getCompound(i);
                z.regulars.add(new Regular(g.getString("n"), g.getInt("k"), g.getString("t"), g.getInt("v")));
            }
            CompoundTag x = c.getCompound("ext"); // old worlds: everything at 0
            z.unattended = x.getInt("unattended");
            z.rejectPenalties = x.getInt("reject_pen");
            z.rejectDay = x.contains("reject_day") ? x.getLong("reject_day") : -1;
            z.infamy = x.getInt("infamy");
            z.infamyDay = x.contains("infamy_day") ? x.getLong("infamy_day") : -1;
            z.cashier2 = x.getBoolean("cashier2");
            z.charity = x.getInt("charity");
            z.cashier2Name = x.getString("cashier2_name");
            z.repExact = x.contains("rep_exact") ? x.getDouble("rep_exact") : -1;
            z.missions = x.contains("missions") ? x.getInt("missions") : -1;
            z.missionsEver = x.getInt("missions_ever");
            z.seats = x.getInt("seats");
            z.playTables = x.getInt("play_tables");
            z.overflowToday = x.getInt("ovf_today");
            z.overflowPrev = x.getInt("ovf_prev");
            z.overflowDay = x.contains("ovf_day") ? x.getLong("ovf_day") : -1;
            z.events = dev.tcgshop.events.ZoneEvents.read(x.getCompound("events"));
            z.dormant = x.getBoolean("dormant"); // 1.1: missing = active and open
            z.closed = x.getBoolean("closed") || z.dormant;
            z.frozenAt = x.contains("frozen_at") ? x.getLong("frozen_at") : -1;
            z.stockValue = Math.max(0, x.getLong("stock_v"));
            z.extraGuards = z.guard ? Math.max(0, Math.min(2, x.getInt("guards_extra"))) : 0; // 1.1: absent = one guard at most
            z.extraGuardNames[0] = x.getString("guard_name_1");
            z.extraGuardNames[1] = x.getString("guard_name_2");
            z.grime = Math.max(0, Math.min(100, x.getInt("grime"))); // 1.1 cleanliness: absent = clean
            for (long l : x.getLongArray("poops")) z.poops.add(l);
            for (long l : x.getLongArray("graffiti")) z.graffiti.add(l);
            return z;
        }
    }

    // ------------------------------------------------------------------ reputation economy

    /**
     * Markup customers accept based on the shop's reputation (configurable in tcgshop-economy.json):
     * nothing up to repPremiumStart, then it grows along the repPremiumCurve curve up to repPremiumMax at 100. Packs and boxes
     * (repPremiumSealedBonus) sell even better in a famous shop. Below repPenaltyBelow it's negative:
     * in a shop with a bad reputation they want prices below market. Shared by client and server (the client
     * has EconomyConfig synced), so the deal window can show it.
     */
    public static double repPremium(int rep, boolean sealed) {
        dev.tcgshop.EconomyConfig c = dev.tcgshop.EconomyConfig.INSTANCE;
        if (rep < c.repPenaltyBelow && c.repPenaltyBelow > 0)
            return -c.repPenaltyMax * (c.repPenaltyBelow - rep) / (double) c.repPenaltyBelow;
        if (rep <= c.repPremiumStart || c.repPremiumMax <= 0) return 0;
        double x = Math.min(1, (rep - c.repPremiumStart) / (double) Math.max(1, 100 - c.repPremiumStart));
        double p = c.repPremiumMax * Math.pow(x, Math.max(0.1, c.repPremiumCurve));
        return sealed ? p * Math.max(0, c.repPremiumSealedBonus) : p;
    }

    /** How far past the zone's edge a roof / eaves still count as the shop's roof. */
    static final int ROOF_OVERHANG = 4;

    /**
     * 1.1: is this spot on the roof of a shop (inside a zone's footprint plus {@link #ROOF_OVERHANG} blocks of eaves, at
     * least 2 above the shop's floor, with nothing above it and open space under it)? No NPC may appear there (customers,
     * visitors, beggars, scalpers...). An indoor upper floor is not a roof (it has the roof above it).
     */
    public static boolean isShopRoof(net.minecraft.server.level.ServerLevel w, BlockPos pos) {
        if (w == null || pos == null) return false;
        for (Zone z : get(w).all()) {
            int half = z.size() / 2 + ROOF_OVERHANG; // roofs and eaves may stick out past the zone's edge
            if (Math.abs(pos.getX() - z.core.getX()) > half || Math.abs(pos.getZ() - z.core.getZ()) > half) continue;
            if (pos.getY() < z.core.getY() + 2 || pos.getY() > z.core.getY() + z.up() + 12) continue;
            // on top of a roof nothing is above you; an indoor upper floor or mezzanine has the roof above it (not a roof spot).
            // (heightmap, not sky light: light updates lag behind block changes)
            if (pos.getY() < w.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ())) continue;
            // a roof has a room under it (not a hill): some open space between the shop's floor and the spot
            for (int y = z.core.getY(); y < pos.getY() - 1; y++)
                if (!w.getBlockState(new BlockPos(pos.getX(), y, pos.getZ())).blocksMotion()) return true;
        }
        return false;
    }

    /** Packs, boxes and Mystery Box (the "sealed" products). */
    public static boolean isSealed(ItemStack st) {
        return dev.masterball.api.card.CardInfo.isPack(st) || st.is(TcgBlocks.BOOSTER_BOX_ITEM) || st.is(TcgBlocks.MYSTERY_BOX);
    }

    public static AABB boxFor(BlockPos core, ShopTier t) {
        return boxFor(core, t.size, t.up);
    }

    /** Zone box for a side (in blocks) and a protected height above the core. */
    public static AABB boxFor(BlockPos core, int size, int up) {
        int half = size / 2;
        return new AABB(core.getX() - half, core.getY() - BELOW, core.getZ() - half,
                core.getX() + half + 1, core.getY() + up + 1, core.getZ() + half + 1);
    }

    private static final Factory<ShopZones> TYPE = new Factory<>(ShopZones::new, ShopZones::fromNbt, null);

    private final List<Zone> zones = new ArrayList<>();
    /**
     * 1.1: dormant shops (core removed). Kept apart so every per-shop system, at(), byCore(), byId() and the overlap checks
     * skip them; saved in "zones2" with ext.dormant and split out again on load.
     */
    private final List<Zone> dormant = new ArrayList<>();
    /** Each player's spending on merchandise (Wholesaler / phone): for their shops' profit. */
    private final Map<UUID, Long> costs = new HashMap<>();
    /** 1.0: players already told they may own more shops (only used in the overworld's state). */
    private final java.util.Set<UUID> multiNotified = new java.util.HashSet<>();

    public static ShopZones get(ServerLevel world) {
        return world.getDataStorage().computeIfAbsent(TYPE, "tcgshop_shops");
    }

    /** Active shops only (dormant ones are in {@link #dormant()}). */
    public List<Zone> all() { return zones; }

    /** 1.1: dormant shops of this dimension (core removed, waiting to be restored or abandoned). */
    public List<Zone> dormant() { return dormant; }

    /**
     * 1.1: active and dormant shops of this dimension (a copy). For owner penalties and tills that must not be dodged
     * by removing the core for a while (infamy, reputation, loan / theft collection).
     */
    public List<Zone> allAndDormant() {
        List<Zone> out = new ArrayList<>(zones);
        out.addAll(dormant);
        return out;
    }

    /**
     * 1.1: marks dirty the ShopZones that holds this zone (active or dormant, any dimension). For changes made from
     * somewhere else (phone, another dimension): marking the player's own level would leave the change unsaved.
     */
    public static void markDirty(net.minecraft.server.MinecraftServer server, Zone z) {
        for (ServerLevel w : server.getAllLevels()) {
            ShopZones zs = get(w);
            if (zs.byIdAny(z.id) == z) { zs.setDirty(); return; }
        }
    }

    /** 1.1: an active or dormant shop by id (phone till / abandon, restore). */
    @Nullable
    public Zone byIdAny(UUID id) {
        Zone z = byId(id);
        if (z != null) return z;
        for (Zone d : dormant) if (d.id.equals(id)) return d;
        return null;
    }

    /** 1.1: the core was removed: the shop leaves the active list but keeps its whole record. */
    public void makeDormant(Zone z, long now) {
        if (!zones.remove(z)) return;
        z.dormant = true;
        z.closed = true;
        z.frozenAt = now;
        dormant.add(z);
        setDirty();
    }

    /** 1.1: takes a dormant shop out of this dimension's list (restored here or elsewhere, or abandoned). */
    public boolean removeDormant(Zone z) {
        boolean r = dormant.remove(z);
        if (r) setDirty();
        return r;
    }

    /** 1.1: puts a dormant shop (from any dimension) back in this dimension's active list at a new core position. */
    public void restore(Zone z, BlockPos core) {
        dormant.remove(z);
        if (!z.core.equals(core)) { // moved: the droppings / graffiti of the old site are not in this shop any more
            z.poops.clear();
            z.graffiti.clear();
            z.floorDirt = 0;
        }
        z.core = core.immutable();
        z.dormant = false;
        z.frozenAt = -1;
        zones.add(z);
        setDirty();
    }

    /**
     * 1.1: client-side "is this position inside a shop of this player?" from the synced zones (set by ShopClient.init;
     * false on a dedicated server). Lets common code mirror server decisions on the client.
     */
    public static java.util.function.BiPredicate<net.minecraft.world.entity.player.Player, BlockPos> CLIENT_OWNS = (p, pos) -> false;

    @Nullable
    public Zone at(BlockPos pos) {
        for (Zone z : zones) if (z.contains(pos)) return z;
        return null;
    }

    @Nullable
    public Zone byCore(BlockPos core) {
        for (Zone z : zones) if (z.core.equals(core)) return z;
        return null;
    }

    @Nullable
    public Zone byId(UUID id) {
        for (Zone z : zones) if (z.id.equals(id)) return z;
        return null;
    }

    public boolean overlaps(AABB box) {
        for (Zone z : zones) if (z.box().intersects(box)) return true;
        return false;
    }

    /** Like {@link #overlaps(AABB)} but ignoring one zone (an in-place upgrade grows the zone over itself). */
    public boolean overlapsOther(AABB box, Zone self) {
        for (Zone z : zones) if (z != self && z.box().intersects(box)) return true;
        return false;
    }

    // ------------------------------------------------------------------ 1.0: shops per player

    /** Every shop this player owns, in every dimension. */
    public static List<Zone> ownedBy(net.minecraft.server.MinecraftServer server, UUID player) {
        List<Zone> out = new ArrayList<>();
        for (ServerLevel w : server.getAllLevels())
            for (Zone z : get(w).all()) if (z.owner.equals(player)) out.add(z);
        // 1.1: dormant shops still count toward the per-player limits (restore or abandon them first)
        for (ServerLevel w : server.getAllLevels())
            for (Zone z : get(w).dormant) if (z.owner.equals(player)) out.add(z);
        return out;
    }

    /** Best tier among this player's shops (any dimension), or null if they own none. */
    @Nullable
    public static ShopTier highestOwned(net.minecraft.server.MinecraftServer server, UUID player) {
        ShopTier best = null;
        for (Zone z : ownedBy(server, player)) if (best == null || !best.atLeast(z.tierInfo())) best = z.tierInfo();
        return best;
    }

    /**
     * After a shop is placed or upgraded: the first time one of this player's shops reaches Amethyst (or higher),
     * the phone tells them they may now own more shops. Remembered in the overworld's state ("multi_notified").
     */
    public static void notifyMultiShop(net.minecraft.server.MinecraftServer server, UUID player, ShopTier reached) {
        if (!ShopTier.canOwnMultipleShops(reached)) return;
        ShopZones main = get(server.overworld());
        if (!main.multiNotified.add(player)) return;
        main.setDirty();
        dev.tcgshop.phone.PhoneMessages.send(server, player, "tcgshop.msg.sender.system",
                net.minecraft.network.chat.Component.translatable("tcgshop.shop.multi_unlocked",
                        net.minecraft.network.chat.Component.translatable("tcgshop.shop.tier." + reached.key)), false);
    }

    public void add(Zone z) { zones.add(z); setDirty(); }

    public void remove(Zone z) { zones.remove(z); setDirty(); }

    public long costs(UUID player) { return costs.getOrDefault(player, 0L); }

    public void addCost(UUID player, long amount) {
        costs.merge(player, amount, Long::sum);
        setDirty();
    }

    public long revenue(UUID player) {
        long r = 0;
        for (Zone z : zones) if (z.owner.equals(player)) r += z.revenue;
        for (Zone z : dormant) if (z.owner.equals(player)) r += z.revenue; // 1.1: a dormant shop's revenue still counts
        return r;
    }

    private static ShopZones fromNbt(CompoundTag nbt, HolderLookup.Provider lookup) {
        ShopZones s = new ShopZones();
        ListTag l = nbt.getList("zones2", 10);
        for (int i = 0; i < l.size(); i++) {
            Zone z = Zone.read(l.getCompound(i));
            (z.dormant ? s.dormant : s.zones).add(z);
        }
        if (!nbt.contains("zones2")) { // migration from the previous version (codec format)
            ListTag old = nbt.getList("zones", 10);
            for (int i = 0; i < old.size(); i++) {
                CompoundTag o = old.getCompound(i);
                int[] p = o.getIntArray("core");
                if (p.length < 3) continue;
                Zone z = new Zone(o.getUUID("id"), o.getUUID("owner"), o.getString("owner_name"), new BlockPos(p[0], p[1], p[2]),
                        ShopTier.fromLegacy(o.getInt("tier")).rank());
                z.size = ShopTier.legacySize(o.getInt("tier"));
                z.revenue = o.getLong("revenue");
                z.balance = o.getLong("balance");
                z.sales = o.getInt("sales");
                s.zones.add(z);
            }
        }
        CompoundTag c = nbt.getCompound("costs2");
        for (String k : c.getAllKeys()) s.costs.put(UUID.fromString(k), c.getLong(k));
        ListTag mn = nbt.getList("multi_notified", 8);
        for (int i = 0; i < mn.size(); i++) {
            try { s.multiNotified.add(UUID.fromString(mn.getString(i))); } catch (IllegalArgumentException ignored) {}
        }
        return s;
    }

    @Override
    public CompoundTag save(CompoundTag nbt, HolderLookup.Provider lookup) {
        ListTag l = new ListTag();
        for (Zone z : zones) l.add(z.write());
        for (Zone z : dormant) l.add(z.write());
        nbt.put("zones2", l);
        CompoundTag c = new CompoundTag();
        costs.forEach((k, v) -> c.putLong(k.toString(), v));
        nbt.put("costs2", c);
        if (!multiNotified.isEmpty()) {
            ListTag mn = new ListTag();
            for (UUID u : multiNotified) mn.add(net.minecraft.nbt.StringTag.valueOf(u.toString()));
            nbt.put("multi_notified", mn);
        }
        return nbt;
    }
}

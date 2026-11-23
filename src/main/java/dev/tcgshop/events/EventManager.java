package dev.tcgshop.events;

import dev.tcgshop.TcgShop;
import dev.tcgshop.entity.CustomerEntity;
import dev.tcgshop.entity.EmployeeEntity;
import dev.tcgshop.events.effect.StaffEffect;
import dev.tcgshop.events.effect.VisitorEffect;
import dev.tcgshop.network.FactPayload;
import dev.tcgshop.shop.ShopLife;
import dev.tcgshop.shop.ShopServer;
import dev.tcgshop.shop.ShopZones;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Random event engine.
 *
 * FREQUENCY (EventsConfig): one roll seeded per owner and day (world seed, uuid, day): dailyChance (10%) that
 * something happens. If it hits, a random moment of the day shift is chosen; the event starts when that moment comes, the
 * owner is online and within ownerRange blocks of one of their shops (the nearest one). If they don't show up
 * all day, it is lost. At most maxPerOwnerPerDay (1) per day across all their shops. Nothing on the first day of
 * a new world. Within the roll: category 40/30/30, rarity 65/25/8/2, cooldown per rarity, history x0.25,
 * same template never twice in a row, grace for new shops and a per-world "deck".
 *
 * PUBLIC API for modules: {@link #resolve}, {@link #isActive}, {@link #activeFor}, {@link #logIncident},
 * {@link #invalidate}, {@link #mods}, {@link #state}. See EFFECTS.md.
 */
public final class EventManager {
    /** Tag (command tag) of every event entity: does not count as a shop Pokémon and is not saved. */
    public static final String EVENT_TAG = "tcgshop_event";

    private static MinecraftServer server;
    private static final Map<UUID, Mods> MODS = new HashMap<>();
    private static final Map<UUID, Long> MODS_AT = new HashMap<>();
    /** uid -> zone, of running events (rebuilt every 10 s and on startup). */
    private static final Map<Long, UUID> ACTIVE = new HashMap<>();

    private EventManager() {}

    // ------------------------------------------------------------------ startup

    public static void register() {
        EffectTypes.registerCore();
        ServerLifecycleEvents.SERVER_STARTED.register(s -> { server = s; MODS.clear(); MODS_AT.clear(); rebuildIndex(); });
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> { server = null; MODS.clear(); MODS_AT.clear(); ACTIVE.clear(); });
        ServerTickEvents.END_SERVER_TICK.register(EventManager::serverTick);
    }

    @Nullable
    public static MinecraftServer server() { return server; }

    public static EventState state() { return server == null ? null : EventState.get(server); }

    static void onDefsReloaded() {
        MODS.clear();
        MODS_AT.clear();
    }

    private static long now() { return server == null ? 0 : server.getOverworld().getTime(); }

    public static long today(MinecraftServer s) { return s.getOverworld().getTimeOfDay() / 24000; }

    // ------------------------------------------------------------------ tick

    private static void serverTick(MinecraftServer s) {
        server = s;
        int t = s.getTicks();
        if (t % 20 == 0) {
            for (ServerWorld w : s.getWorlds()) {
                ShopZones zs = ShopZones.get(w);
                for (ShopZones.Zone z : new ArrayList<>(zs.all())) tickZone(w, z);
            }
        }
        if (t % 200 == 0) rebuildIndex();
        if (t % 100 == 50) {
            try {
                scheduler(s);
            } catch (Exception e) {
                TcgShop.LOGGER.error("[events] error in the scheduler", e);
            }
        }
    }

    private static void rebuildIndex() {
        ACTIVE.clear();
        if (server == null) return;
        for (ServerWorld w : server.getWorlds())
            for (ShopZones.Zone z : ShopZones.get(w).all())
                for (EventInstance e : z.events.active) ACTIVE.put(e.uid, z.id);
    }

    private static void tickZone(ServerWorld w, ShopZones.Zone z) {
        ZoneEvents ze = z.events;
        long day = w.getTimeOfDay() / 24000;
        if (ze.createdDay < 0) {
            ze.createdDay = z.sales >= EventsConfig.INSTANCE.graceMinSales ? day - EventsConfig.INSTANCE.graceMinDays : day;
            ShopZones.get(w).markDirty();
        }
        if (ze.active.isEmpty()) return;
        long now = w.getTime();
        boolean loaded = ShopLife.zoneLoaded(w, z);
        for (EventInstance inst : new ArrayList<>(ze.active)) {
            EventDef def = EventRegistry.get(inst.defId);
            if (def == null || now >= inst.end || now < inst.start - 24_000) { end(w, z, inst, def == null ? "removed" : "expired"); continue; }
            if (!loaded) continue;
            List<EventEffect> effs = def.effectsFor(inst.variant);
            for (int i = 0; i < effs.size(); i++) {
                if (!ze.active.contains(inst)) break;
                try {
                    effs.get(i).tick(new EventContext(w, z, inst, def, i, now));
                } catch (Exception e) {
                    TcgShop.LOGGER.error("[events] {} effect {} failed in tick", inst.defId, i, e);
                }
            }
        }
        if (now % 1200 < 20) ze.trimLog(now);
    }

    // ------------------------------------------------------------------ scheduler (frequency)

    /** Seed of an owner's daily roll (does not change when the world is reloaded). */
    public static long daySeed(long worldSeed, UUID owner, long day) {
        long h = worldSeed ^ owner.getMostSignificantBits() * 0x9E3779B97F4A7C15L ^ owner.getLeastSignificantBits() * 0xC2B2AE3D27D4EB4FL;
        return h ^ day * 0x165667B19E3779F9L;
    }

    private static void scheduler(MinecraftServer s) {
        EventsConfig cfg = EventsConfig.INSTANCE;
        if (!cfg.enabled || EventRegistry.all().isEmpty()) return;
        ServerWorld ow = s.getOverworld();
        long tod = ow.getTimeOfDay(), day = tod / 24000;
        int t = (int) Math.floorMod(tod, 24000L);
        if (day < 1 || ow.getTime() < 24_000) return; // never on the first day of a new world
        EventState st = EventState.get(s);
        for (ServerPlayerEntity p : s.getPlayerManager().getPlayerList()) {
            if (!ownsShop(s, p.getUuid())) continue;
            EventState.OwnerData od = st.owner(p.getUuid());
            if (od.rollDay != day) {
                od.rollDay = day;
                od.firedToday = 0;
                Random r = new Random(daySeed(ow.getSeed(), p.getUuid(), day));
                od.rollHit = r.nextDouble() < cfg.dailyChance;
                od.fireTod = cfg.shiftStart + r.nextInt(Math.max(1, cfg.shiftEnd - cfg.shiftStart));
                od.pendingDef = ""; // whatever was waiting for its time window yesterday is lost
                st.markDirty();
            }
            if (!od.rollHit || od.firedToday >= cfg.maxPerOwnerPerDay || t < od.fireTod) continue;
            Target target = pickShop(s, p);
            if (target == null) continue; // waits (until the day ends) for the owner to be near a shop
            Random pick = new Random(daySeed(ow.getSeed(), p.getUuid(), day) ^ 0x5EEDL ^ od.firedToday * 977L);
            if (!od.pendingDef.isEmpty()) {
                // an event chosen earlier waits for its time window (night...): starts if it fits now; if today's window
                // has already passed (or the template disappeared), it is discarded; otherwise it keeps waiting
                EventDef pd = EventRegistry.get(od.pendingDef);
                if (pd != null && eligible(target.world, target.zone, pd, od, day, st, ow.getSeed(), null)) {
                    od.pendingDef = "";
                    od.firedToday++;
                    st.markDirty();
                    start(target.world, target.zone, pd, null, pick, false);
                } else if (pd == null || firstFit(target.world, target.zone, pd, od, day, st, ow.getSeed(), probes(t)) < 0) {
                    od.pendingDef = "";
                    od.firedToday++;
                    st.markDirty();
                }
                continue;
            }
            Map<EventDef, Integer> when = new HashMap<>();
            EventDef def = pickDef(target.world, target.zone, od, pick, day, null, probes(t), when);
            int at = def == null ? t : when.getOrDefault(def, t);
            if (def != null && at > t) {
                // its time window starts later today: postponed to a moment within it
                od.pendingDef = def.id();
                od.fireTod = Math.min(23_900, at + pick.nextInt(3000));
                st.markDirty();
                continue;
            }
            od.firedToday++;
            st.markDirty();
            if (def != null) start(target.world, target.zone, def, null, pick, false);
        }
    }

    /**
     * Times of day at which to check whether an event fits: now (t) and the start of each "time" window (of events and
     * variants) that has not arrived yet today. This way night events can happen even if the owner is only around by day.
     */
    private static int[] probes(int t) {
        java.util.TreeSet<Integer> out = new java.util.TreeSet<>();
        out.add(t);
        for (EventDef d : EventRegistry.all()) {
            addProbe(out, d.conditions().time(), t);
            for (EventDef.Variant v : d.variants()) addProbe(out, v.conditions().time(), t);
        }
        int[] a = new int[out.size()];
        int i = 0;
        for (int x : out) a[i++] = x;
        return a;
    }

    private static void addProbe(java.util.Set<Integer> out, int[] time, int t) {
        if (time != null && time[0] > t && time[0] < 24000) out.add(time[0]);
    }

    /** First time in probes (in order) at which the event would be eligible, or -1. */
    private static int firstFit(ServerWorld w, ShopZones.Zone z, EventDef d, EventState.OwnerData od, long day, EventState st, long seed, int[] probes) {
        for (int p : probes) {
            EventConditions.timeOverride = p;
            try {
                if (eligible(w, z, d, od, day, st, seed, null)) return p;
            } finally {
                EventConditions.timeOverride = -1;
            }
        }
        return -1;
    }

    private record Target(ServerWorld world, ShopZones.Zone zone) {}

    private static boolean ownsShop(MinecraftServer s, UUID owner) {
        for (ServerWorld w : s.getWorlds()) for (ShopZones.Zone z : ShopZones.get(w).all()) if (z.owner.equals(owner)) return true;
        return false;
    }

    /** The owner's shop they are in (or the nearest within ownerRange), loaded and with displays. */
    @Nullable
    private static Target pickShop(MinecraftServer s, ServerPlayerEntity p) {
        ServerWorld w = p.getServerWorld();
        if (EventsConfig.INSTANCE.dimensionBlacklist.contains(w.getRegistryKey().getValue().toString())) return null;
        double range = EventsConfig.INSTANCE.ownerRange, best = range * range;
        ShopZones.Zone pick = null;
        for (ShopZones.Zone z : ShopZones.get(w).all()) {
            if (!z.owner.equals(p.getUuid()) || z.displays <= 0) continue;
            double d = z.contains(p.getBlockPos()) ? -1 : p.squaredDistanceTo(z.core.toCenterPos());
            if (d < best && ShopLife.zoneLoaded(w, z)) { best = d; pick = z; }
        }
        return pick == null ? null : new Target(w, pick);
    }

    /**
     * Picks an event for this shop (or null if none is possible). forceCategory: only that category (testing).
     * Category (weights) -> rarity (weights) -> event (weight x history x chain), only among the eligible ones.
     */
    @Nullable
    public static EventDef pickDef(ServerWorld w, ShopZones.Zone z, EventState.OwnerData od, Random r, long day, @Nullable EventDef.Category forceCategory) {
        return pickDef(w, z, od, r, day, forceCategory, null, null);
    }

    /**
     * Like {@link #pickDef}, but with probes (times of day) an event is eligible if it fits at any of them;
     * when receives the first time at which each eligible event fits (to postpone night ones).
     */
    @Nullable
    private static EventDef pickDef(ServerWorld w, ShopZones.Zone z, EventState.OwnerData od, Random r, long day, @Nullable EventDef.Category forceCategory,
                                    int[] probes, @Nullable Map<EventDef, Integer> when) {
        EventsConfig cfg = EventsConfig.INSTANCE;
        EventState st = EventState.get(w.getServer());
        long seed = w.getServer().getOverworld().getSeed();
        Map<EventDef.Category, Map<EventDef.Tier, List<EventDef>>> pool = new EnumMap<>(EventDef.Category.class);
        for (EventDef d : EventRegistry.all()) {
            if (forceCategory != null && d.category() != forceCategory) continue;
            if (probes == null) {
                if (!eligible(w, z, d, od, day, st, seed, null)) continue;
            } else {
                int at = firstFit(w, z, d, od, day, st, seed, probes);
                if (at < 0) continue;
                if (when != null) when.put(d, at);
            }
            pool.computeIfAbsent(d.category(), k -> new EnumMap<>(EventDef.Tier.class)).computeIfAbsent(d.tier(), k -> new ArrayList<>()).add(d);
        }
        if (pool.isEmpty()) return null;
        EventDef.Category cat = weighted(r, pool.keySet(), cfg::categoryWeight);
        if (cat == null) return null;
        Map<EventDef.Tier, List<EventDef>> tiers = pool.get(cat);
        EventDef.Tier tier = weighted(r, tiers.keySet(), cfg::tierWeight);
        if (tier == null) return null;
        return weighted(r, tiers.get(tier), d -> defWeight(d, od, day));
    }

    /** Final weight of an event for this owner: config x history x chains ("after X, Y is more likely"). */
    static double defWeight(EventDef d, EventState.OwnerData od, long day) {
        EventsConfig cfg = EventsConfig.INSTANCE;
        double w = cfg.weight(d);
        if (od.inRecent(d.id(), cfg.historySize)) w *= cfg.historyPenalty;
        for (EventState.Hist h : od.hist) {
            EventDef prev = EventRegistry.get(h.id());
            if (prev != null && prev.chainNext().equals(d.id()) && day - h.day() <= prev.chainWithinDays()) w *= prev.chainMult();
        }
        return w;
    }

    /** Can this event happen now in this shop? why receives the reason if not. */
    public static boolean eligible(ServerWorld w, ShopZones.Zone z, EventDef d, EventState.OwnerData od, long day, EventState st, long seed, @Nullable StringBuilder why) {
        EventsConfig cfg = EventsConfig.INSTANCE;
        if (!cfg.enabled(d)) return fail(why, "disabled");
        if (cfg.weight(d) <= 0) return fail(why, "weight 0");
        if (!st.inDeck(d, seed)) return fail(why, "not in this world's deck");
        Long last = od.cooldown.get(d.id());
        if (last != null && day - last < cfg.cooldownDays(d)) return fail(why, "cooldown");
        if (!od.lastTemplate().isEmpty() && od.lastTemplate().equals(d.template())) return fail(why, "same template as last");
        if (d.conditions().maxPerWeek() > 0 && od.countSince(d.id(), day - 6) >= d.conditions().maxPerWeek()) return fail(why, "maxPerWeek");
        if (d.negative() && (z.sales < cfg.graceMinSales || z.events.createdDay < 0 || day - z.events.createdDay < cfg.graceMinDays))
            return fail(why, "grace (new shop)");
        if (d.category() == EventDef.Category.POSITIVE && (z.sales < cfg.positiveMinSales || z.displays < cfg.positiveMinDisplays))
            return fail(why, "positive needs sales/displays");
        String group = d.conditions().exclusiveGroup();
        for (EventInstance e : z.events.active) {
            if (e.defId.equals(d.id())) return fail(why, "already active");
            EventDef o = EventRegistry.get(e.defId);
            if (!group.isEmpty() && o != null && group.equals(o.conditions().exclusiveGroup())) return fail(why, "exclusiveGroup");
        }
        if (!d.conditions().test(w, z, implicitRequires(d.effects()), why)) return false;
        if (!d.variants().isEmpty() && pickVariantOrNull(d, w, z, new Random(0)) == null) return fail(why, "no variant fits");
        return true;
    }

    static Set<String> implicitRequires(List<EventEffect> effs) {
        Set<String> s = new HashSet<>();
        for (EventEffect e : effs) s.addAll(e.implicitRequires());
        return s;
    }

    @Nullable
    private static String pickVariantOrNull(EventDef d, ServerWorld w, ShopZones.Zone z, Random r) {
        if (d.variants().isEmpty()) return "";
        List<EventDef.Variant> ok = new ArrayList<>();
        for (EventDef.Variant v : d.variants())
            if (v.weight() > 0 && v.conditions().test(w, z, implicitRequires(d.effectsFor(v.key())), null)) ok.add(v);
        EventDef.Variant v = weighted(r, ok, x -> (double) x.weight());
        return v == null ? null : v.key();
    }

    private static <T> T weighted(Random r, java.util.Collection<T> items, java.util.function.ToDoubleFunction<T> weight) {
        double sum = 0;
        for (T x : items) sum += Math.max(0, weight.applyAsDouble(x));
        if (sum <= 0) return null;
        double roll = r.nextDouble() * sum;
        T lastPositive = null;
        for (T x : items) {
            double wx = Math.max(0, weight.applyAsDouble(x));
            if (wx <= 0) continue;
            lastPositive = x;
            if ((roll -= wx) < 0) return x;
        }
        return lastPositive;
    }

    private static boolean fail(@Nullable StringBuilder why, String s) {
        if (why != null) why.append(s);
        return false;
    }

    // ------------------------------------------------------------------ start / fix / end

    /**
     * Starts an event in a shop. variant null = random among those that fit. forced = from a command
     * (does not check conditions). Returns the instance or null.
     */
    @Nullable
    public static EventInstance start(ServerWorld w, ShopZones.Zone z, EventDef def, @Nullable String variant, Random r, boolean forced) {
        long now = w.getTime(), tod = w.getTimeOfDay(), day = tod / 24000;
        String vk = variant;
        if (vk == null) {
            vk = pickVariantOrNull(def, w, z, r);
            if (vk == null) {
                if (!forced) return null;
                vk = def.variants().isEmpty() ? "" : def.variants().get(0).key();
            }
        }
        long dur;
        if (def.untilDayEnd()) dur = 24000 - Math.floorMod(tod, 24000L);
        else dur = def.durMin() + (def.durMax() > def.durMin() ? (long) (r.nextDouble() * (def.durMax() - def.durMin())) : 0);
        if (!def.untilDayEnd()) dur = Math.round(dur * EventsConfig.INSTANCE.durationMult(def));
        dur = Math.max(400, Math.min(24000, dur));
        List<EventEffect> effs = def.effectsFor(vk);
        if (!forced) for (EventEffect e : effs) if (!e.canStart(w, z)) return null; // no room: does not use up the day or the cooldown
        EventState st = EventState.get(w.getServer());
        EventInstance inst = new EventInstance(st.nextUid(), def.id(), vk, z.id, z.owner, now, now + dur, day);
        z.events.active.add(inst);
        ACTIVE.put(inst.uid, z.id);
        st.owner(z.owner).addHist(def.id(), def.template(), day);
        st.markDirty();
        for (int i = 0; i < effs.size(); i++) {
            try {
                effs.get(i).start(new EventContext(w, z, inst, def, i, now ^ 0x51L));
            } catch (Exception e) {
                TcgShop.LOGGER.error("[events] {} effect {} failed on start", def.id(), i, e);
            }
        }
        // review memory (after start: a module can change the tag with inst.reviewTag)
        if (def.review().present() && z.events.active.contains(inst)) {
            EventDef.Review rv = def.review();
            double fixed = rv.fixedChance() >= 0 ? rv.fixedChance() : EventsConfig.INSTANCE.reviewFixedChance;
            IncidentRecord rec = new IncidentRecord(inst.uid, def.id(), inst.reviewTag.isEmpty() ? rv.tag() : inst.reviewTag, now,
                    def.category() == EventDef.Category.POSITIVE, def.negative(), rv.mood(), rv.chance(), def.negative() ? fixed : 0);
            if (inst.resolved()) rec.resolvedAt = inst.resolvedAt;
            z.events.log.add(rec);
        }
        invalidate(z);
        ShopZones.get(w).markDirty();
        if (z.events.active.contains(inst)) notifyStart(w, z, def, inst);
        TcgShop.LOGGER.info("[events] {} starts in {}'s shop ({}){}", def.id(), z.ownerName, inst.uid, forced ? " [command]" : "");
        sync(w, z);
        return inst;
    }

    /** Fixes an event ("how": mop, guard, talk, metric, visit, shoo, feed...). Returns false if it does not exist or was already fixed. */
    public static boolean resolve(long uid, String how) {
        Found f = find(uid);
        if (f == null || f.inst.resolved()) return false;
        long now = f.world.getTime();
        f.inst.resolvedAt = now;
        f.inst.resolvedHow = how == null ? "" : how;
        for (IncidentRecord r : f.zone.events.log) if (r.uid == uid && r.resolvedAt < 0) r.resolvedAt = now;
        EventDef def = EventRegistry.get(f.inst.defId);
        boolean keep = false;
        if (def != null) {
            List<EventEffect> effs = def.effectsFor(f.inst.variant);
            for (int i = 0; i < effs.size(); i++) {
                try {
                    effs.get(i).resolve(new EventContext(f.world, f.zone, f.inst, def, i, now ^ 0x52L), f.inst.resolvedHow);
                } catch (Exception e) {
                    TcgShop.LOGGER.error("[events] {} effect {} failed on resolve", f.inst.defId, i, e);
                }
                keep |= effs.get(i).keepsAfterResolve();
            }
            notifyResolved(f.world, f.zone, def, f.inst);
        }
        f.inst.clearLocks();
        invalidate(f.zone);
        ShopZones.get(f.world).markDirty();
        if (!keep || def == null) end(f.world, f.zone, f.inst, "resolved");
        else sync(f.world, f.zone);
        return true;
    }

    /** Ends an event right away (command, or a module that needs to cut it short). */
    public static boolean end(long uid, String outcome) {
        Found f = find(uid);
        if (f == null) return false;
        end(f.world, f.zone, f.inst, outcome);
        return true;
    }

    static void end(ServerWorld w, ShopZones.Zone z, EventInstance inst, String outcome) {
        if (!z.events.active.remove(inst)) return;
        ACTIVE.remove(inst.uid);
        long now = w.getTime();
        EventDef def = EventRegistry.get(inst.defId);
        if (def != null) {
            List<EventEffect> effs = def.effectsFor(inst.variant);
            for (int i = 0; i < effs.size(); i++) {
                try {
                    effs.get(i).end(new EventContext(w, z, inst, def, i, now ^ 0x53L));
                } catch (Exception e) {
                    TcgShop.LOGGER.error("[events] {} effect {} failed on end", inst.defId, i, e);
                }
            }
        }
        for (IncidentRecord r : z.events.log) if (r.uid == inst.uid && r.end < 0) r.end = now;
        String out = inst.resolved() ? (inst.resolvedHow.startsWith("failed") ? "failed" : "resolved") : outcome;
        z.events.addHist(w.getTimeOfDay() / 24000, inst.defId, def == null ? "neutral" : def.category().key(), out);
        if (w.getServer() != null) {
            EventState st = EventState.get(w.getServer());
            st.clearActorsOf(inst.uid);
            // perks ("next purchase/hire/grading") last until their until even if the event ends (e.g. the
            // visitor leaves); they are only removed if the event is cut short (command, shop deleted, cancelled)
            if (outcome.equals("removed") || outcome.equals("command") || outcome.equals("cancelled")) {
                EventState.OwnerData od = st.owner(z.owner);
                if (od.perks.removeIf(p -> p.uid == inst.uid)) st.markDirty();
            }
        }
        invalidate(z);
        ShopZones.get(w).markDirty();
        if (def != null && def.negative() && !inst.resolved() && outcome.equals("expired")) {
            ServerPlayerEntity owner = w.getServer().getPlayerManager().getPlayer(z.owner);
            if (owner != null) owner.sendMessage(Text.translatable("tcgshop.event.expired", name(def)).formatted(Formatting.GRAY), false);
        }
        sync(w, z);
    }

    /** The shop disappears (core broken): ends its events so modules clean everything up. */
    public static void onZoneRemoved(ServerWorld w, ShopZones.Zone z) {
        for (EventInstance e : new ArrayList<>(z.events.active)) end(w, z, e, "removed");
    }

    // ------------------------------------------------------------------ queries (public API)

    /** Is this event still running? (false if it does not exist, ended, or its shop was deleted) */
    public static boolean isActive(long uid) {
        UUID zid = ACTIVE.get(uid);
        if (zid == null || server == null) return false;
        for (ServerWorld w : server.getWorlds()) {
            ShopZones.Zone z = ShopZones.get(w).byId(zid);
            if (z != null) return z.events.byUid(uid) != null;
        }
        return false;
    }

    /** Running and not fixed? */
    public static boolean isUnresolved(long uid) {
        Found f = find(uid);
        return f != null && !f.inst.resolved();
    }

    public static List<EventInstance> activeFor(ShopZones.Zone z) { return Collections.unmodifiableList(z.events.active); }

    @Nullable
    public static EventInstance instance(long uid) {
        Found f = find(uid);
        return f == null ? null : f.inst;
    }

    record Found(ServerWorld world, ShopZones.Zone zone, EventInstance inst) {}

    @Nullable
    static Found find(long uid) {
        if (server == null) return null;
        UUID zid = ACTIVE.get(uid);
        for (ServerWorld w : server.getWorlds()) {
            ShopZones zs = ShopZones.get(w);
            if (zid != null) {
                ShopZones.Zone z = zs.byId(zid);
                if (z != null) {
                    EventInstance e = z.events.byUid(uid);
                    if (e != null) return new Found(w, z, e);
                }
                continue;
            }
            for (ShopZones.Zone z : zs.all()) {
                EventInstance e = z.events.byUid(uid);
                if (e != null) return new Found(w, z, e);
            }
        }
        return null;
    }

    /**
     * Records something in the shop's review memory (for modules: e.g. a specific puddle with its tag).
     * It is marked as fixed when the event uid is resolved (or with {@link #resolveIncident}).
     */
    public static void logIncident(ServerWorld w, ShopZones.Zone z, long uid, String tag, boolean negative, int mood, double chance) {
        for (IncidentRecord r : z.events.log) if (r.uid == uid && r.tag.equals(tag) && r.end < 0) return;
        z.events.log.add(new IncidentRecord(uid, "", tag, w.getTime(), !negative && mood > 0, negative, mood, chance,
                negative ? EventsConfig.INSTANCE.reviewFixedChance : 0));
        ShopZones.get(w).markDirty();
    }

    /** Marks that event's records with that tag as fixed (without resolving the whole event). */
    public static void resolveIncident(ServerWorld w, ShopZones.Zone z, long uid, String tag) {
        for (IncidentRecord r : z.events.log) if (r.uid == uid && r.tag.equals(tag) && r.resolvedAt < 0) r.resolvedAt = w.getTime();
        ShopZones.get(w).markDirty();
    }

    // ------------------------------------------------------------------ modifiers

    /** Modifiers of the shop's active events (cached; Mods.NONE if there are none). */
    public static Mods mods(ShopZones.Zone z) {
        if (z.events.active.isEmpty()) return Mods.NONE;
        long now = now();
        Mods m = MODS.get(z.id);
        Long at = MODS_AT.get(z.id);
        if (m == null || at == null || now - at > 200 || now < at) {
            m = build(z, now);
            MODS.put(z.id, m);
            MODS_AT.put(z.id, now);
        }
        return m;
    }

    /** After changing an event (locks, outcome data...). */
    public static void invalidate(ShopZones.Zone z) {
        MODS.remove(z.id);
        MODS_AT.remove(z.id);
    }

    static Mods build(ShopZones.Zone z, long now) {
        Mods.Builder b = new Mods.Builder();
        boolean any = false;
        for (EventInstance inst : z.events.active) {
            EventDef def = EventRegistry.get(inst.defId);
            if (def == null) continue;
            any = true;
            if (!inst.resolved()) b.locks(inst);
            for (EventEffect e : def.effectsFor(inst.variant)) e.contribute(b, inst, now);
            // outcome of a visitor (inspection, influencer...): lasts until o_until
            if (inst.data.getLong("o_until") > now) {
                b.repDelta += inst.data.getInt("o_rep");
                if (inst.data.contains("o_spawn")) b.spawnMult *= Math.max(0.1, inst.data.getDouble("o_spawn"));
            }
        }
        return b.build(any);
    }

    /** "Effective" reputation for customers (arrival and decision): the real one + the events' temporary modifier. Does not touch premiumFor. */
    public static int effectiveRep(ShopZones.Zone z) {
        if (z.events.active.isEmpty()) return z.reputation;
        return Math.max(0, Math.min(100, z.reputation + mods(z).repDelta));
    }

    // ------------------------------------------------------------------ shop hooks

    /** ShopLife.recompute: data for conditions and measurable goals. */
    public static void onRecompute(ServerWorld w, ShopZones.Zone z, boolean register, List<BlockPos> doors) {
        ZoneEvents ze = z.events;
        ze.hasRegister = register;
        ze.doors = doors;
        ze.priceFactor = z.factors.getOrDefault("price", 0);
        ze.lastRecompute = w.getTime();
        if (ze.active.isEmpty()) return;
        int d = mods(z).repDelta;
        if (d != 0) z.factors.put("event", d);
        long now = w.getTime();
        for (EventInstance inst : new ArrayList<>(ze.active)) {
            EventDef def = EventRegistry.get(inst.defId);
            if (def == null) continue;
            List<EventEffect> effs = def.effectsFor(inst.variant);
            for (int i = 0; i < effs.size() && ze.active.contains(inst); i++) {
                try {
                    effs.get(i).onRecompute(new EventContext(w, z, inst, def, i, now ^ 0x54L));
                } catch (Exception e) {
                    TcgShop.LOGGER.error("[events] {} effect {} failed in onRecompute", inst.defId, i, e);
                }
            }
        }
    }

    /**
     * ShopServer.payFromNpc: records the day's revenue (reward caps), notifies effects (goals) and applies
     * revenueMult and the tip (with the per-owner cap). Returns the EXTRA money already added to the till.
     */
    public static long onNpcSale(ServerWorld w, ShopZones.Zone z, ItemStack bought, long price, long earned) {
        long day = w.getTimeOfDay() / 24000;
        z.events.addRevenue(day, earned);
        if (z.events.active.isEmpty() && !hasPerk(w.getServer(), z.owner, "tip")) return 0;
        long market = ShopServer.priceOf(bought.copyWithCount(1), 1.0);
        long now = w.getTime();
        for (EventInstance inst : new ArrayList<>(z.events.active)) {
            EventDef def = EventRegistry.get(inst.defId);
            if (def == null) continue;
            List<EventEffect> effs = def.effectsFor(inst.variant);
            for (int i = 0; i < effs.size() && z.events.active.contains(inst); i++) {
                try {
                    effs.get(i).onSale(new EventContext(w, z, inst, def, i, now ^ 0x55L), bought, price, market);
                } catch (Exception e) {
                    TcgShop.LOGGER.error("[events] {} effect {} failed in onSale", inst.defId, i, e);
                }
            }
        }
        long extra = 0;
        double mult = mods(z).revenueMult;
        if (mult > 1.0001) extra += Math.round(earned * (mult - 1));
        EventState.Perk tip = takePerk(w.getServer(), z.owner, "tip");
        if (tip != null) extra += Math.round(earned * Math.min(EventsConfig.INSTANCE.tipMaxPct, tip.factor));
        if (extra <= 0) return 0;
        long granted = grantMoney(w, z, extra, false);
        if (granted > 0 && tip != null) {
            ServerPlayerEntity owner = w.getServer().getPlayerManager().getPlayer(z.owner);
            if (owner != null) owner.sendMessage(Text.translatable("tcgshop.event.tip_paid", Text.literal(dev.tcgshop.pricing.PriceService.format(granted))
                    .formatted(Formatting.GOLD)).formatted(Formatting.GREEN), false);
        }
        return granted;
    }

    /** A customer spawns: counts toward "customers arrived while it was still unfixed". */
    public static void onCustomerSpawn(ServerWorld w, ShopZones.Zone z) {
        if (z.events.log.isEmpty()) return;
        long now = w.getTime();
        for (IncidentRecord r : z.events.log) if (r.activeAt(now)) r.customersDuring++;
    }

    /** Line from a customer who enters while an event is active (or null). */
    @Nullable
    public static Text enterLine(ShopZones.Zone z, net.minecraft.util.math.random.Random r) {
        for (EventInstance inst : z.events.active) {
            if (inst.resolved()) continue;
            EventDef def = EventRegistry.get(inst.defId);
            if (def == null || def.notice().lines().isEmpty() || def.notice().count() <= 0) continue;
            if (r.nextFloat() < def.notice().chance())
                return Text.translatable("tcgshop.evline." + def.notice().lines() + "." + r.nextInt(def.notice().count()));
        }
        return null;
    }

    // ------------------------------------------------------------------ review memory

    /** What an event changes in a customer's review: key (null = unchanged) and mood to add. */
    public record ReviewOutcome(@Nullable String key, int mood) {}

    private static final Set<String> STRONG_KEYS = Set.of("murder", "hit", "infamy", "shop_full");

    /**
     * When a customer leaves (CustomerEntity.finishVisit, before computing the stars):
     *  · if it overlapped an unfixed event: with its probability, review "ev_&lt;tag&gt;" and its mood (the worst
     *    negative beats any positive);
     *  · if it was fixed before they arrived (that day) and someone got to see it: sometimes "ev_&lt;tag&gt;_fixed", no penalty;
     *  · if it was fixed before any customer arrived: nothing.
     */
    public static ReviewOutcome reviewFor(ShopZones.Zone z, long arrivedAt, long now, String currentKey, net.minecraft.util.math.random.Random r) {
        if (z.events.log.isEmpty() || arrivedAt < 0) return new ReviewOutcome(null, 0);
        IncidentRecord worst = null, best = null, fixed = null;
        for (IncidentRecord rec : z.events.log) {
            if (rec.tag.isEmpty()) continue;
            boolean overlap = rec.start < now && rec.activeUntil() > arrivedAt;
            if (overlap) {
                if (rec.negative && (worst == null || rec.mood < worst.mood)) worst = rec;
                else if (!rec.negative && rec.mood >= 0 && (best == null || rec.mood > best.mood)) best = rec;
            } else if (rec.negative && rec.resolvedAt >= 0 && rec.resolvedAt <= arrivedAt && arrivedAt - rec.resolvedAt < 12_000
                    && rec.customersDuring > 0) fixed = rec;
        }
        boolean canOverride = currentKey == null || !STRONG_KEYS.contains(currentKey);
        if (worst != null && r.nextDouble() < worst.chance) return new ReviewOutcome(canOverride ? "ev_" + worst.tag : null, worst.mood);
        if (best != null && r.nextDouble() < best.chance) return new ReviewOutcome(canOverride ? "ev_" + best.tag : null, best.mood);
        if (worst == null && fixed != null && canOverride && r.nextDouble() < fixed.fixedChance) return new ReviewOutcome("ev_" + fixed.tag + "_fixed", 0);
        return new ReviewOutcome(null, 0);
    }

    // ------------------------------------------------------------------ visitors

    /** Outcome of an event visitor's visit: review key to use (or null) and forced stars (or 0). */
    public record VisitorResult(@Nullable String key, int stars) {}

    /** CustomerEntity.finishVisit of an event visitor. stars = the ones they would leave. */
    public static VisitorResult visitorFinished(ServerWorld w, ShopZones.Zone z, CustomerEntity c, VisitorSpec spec, int stars) {
        Found f = find(spec.eventUid);
        if (f == null || !f.zone.id.equals(z.id)) return new VisitorResult(null, 0);
        EventDef def = EventRegistry.get(f.inst.defId);
        if (def == null) return new VisitorResult(null, 0);
        List<EventEffect> effs = def.effectsFor(f.inst.variant);
        if (spec.effectIndex < 0 || spec.effectIndex >= effs.size() || !(effs.get(spec.effectIndex) instanceof VisitorEffect ve))
            return new VisitorResult(null, 0);
        return ve.finished(new EventContext(w, z, f.inst, def, spec.effectIndex, w.getTime() ^ 0x56L), c, spec, stars);
    }

    /** Owner right-clicks an event visitor (calm down the upset customer). true = consumed. */
    public static boolean onVisitorInteract(ServerPlayerEntity sp, ShopZones.Zone z, CustomerEntity c, VisitorSpec spec) {
        if (!spec.troublemaker || !z.owner.equals(sp.getUuid())) return false;
        EventInstance inst = z.events.byUid(spec.eventUid);
        if (inst == null || inst.resolved()) return false;
        dev.tcgshop.entity.NpcLines.speak(c, Text.translatable("tcgshop.evline.calmed." + sp.getRandom().nextInt(6)));
        sp.getServerWorld().spawnParticles(net.minecraft.particle.ParticleTypes.HAPPY_VILLAGER, c.getX(), c.getY() + 2, c.getZ(), 6, 0.3, 0.3, 0.3, 0);
        resolve(inst.uid, "calm");
        return true;
    }

    /** Owner right-clicks their cashier: "wakes them up" if distracted. true = consumed. */
    public static boolean onEmployeeInteract(ServerPlayerEntity sp, ShopZones.Zone z, EmployeeEntity e) {
        if (!z.owner.equals(sp.getUuid()) || e.isGuard()) return false;
        for (EventInstance inst : new ArrayList<>(z.events.active)) {
            if (inst.resolved()) continue;
            EventDef def = EventRegistry.get(inst.defId);
            if (def == null) continue;
            for (EventEffect eff : def.effectsFor(inst.variant)) {
                if (eff instanceof StaffEffect s && s.distracted()) {
                    e.announce(Text.translatable("tcgshop.evline.focused." + sp.getRandom().nextInt(6)));
                    sp.getServerWorld().playSound(null, e.getBlockPos(), SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), SoundCategory.NEUTRAL, 0.6f, 1.6f);
                    resolve(inst.uid, "talk");
                    return true;
                }
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ money and perks (per-owner caps)

    /** Reward money cap this owner has left today (daily and weekly). */
    public static long moneyLeft(MinecraftServer s, UUID owner) {
        EventsConfig cfg = EventsConfig.INSTANCE;
        long day = today(s);
        double avg = 0;
        for (ServerWorld w : s.getWorlds()) for (ShopZones.Zone z : ShopZones.get(w).all()) if (z.owner.equals(owner)) avg += z.events.avgDaily(day, 3);
        long daily = Math.max(cfg.moneyCapFloor, Math.min(cfg.moneyCapAbs, Math.round(avg * cfg.moneyCapPct)));
        long weekly = Math.round(daily * cfg.weeklyCapMult);
        EventState.OwnerData od = EventState.get(s).owner(owner);
        od.moneyRoll(day);
        return Math.max(0, Math.min(daily - od.money[0], weekly - od.moneyWeek()));
    }

    /** Gives reward money to the shop's till, with the per-owner cap. Returns what was actually given. */
    public static long grantMoney(ServerWorld w, ShopZones.Zone z, long amount, boolean tell) {
        if (amount <= 0) return 0;
        long give = Math.min(amount, moneyLeft(w.getServer(), z.owner));
        if (give <= 0) return 0;
        EventState st = EventState.get(w.getServer());
        EventState.OwnerData od = st.owner(z.owner);
        od.money[0] += give;
        st.markDirty();
        z.balance += give;
        ShopZones.get(w).markDirty();
        if (tell) {
            ServerPlayerEntity owner = w.getServer().getPlayerManager().getPlayer(z.owner);
            if (owner != null) owner.sendMessage(Text.translatable("tcgshop.event.money", Text.literal(dev.tcgshop.pricing.PriceService.format(give))
                    .formatted(Formatting.GOLD)).formatted(Formatting.GREEN), false);
        }
        return give;
    }

    public static void addPerk(MinecraftServer s, UUID owner, long uid, String kind, double factor, int uses, long until) {
        EventState st = EventState.get(s);
        EventState.Perk p = new EventState.Perk();
        p.kind = kind;
        p.factor = factor;
        p.uses = Math.max(1, uses);
        p.until = until;
        p.uid = uid;
        st.owner(owner).perks.add(p);
        st.markDirty();
    }

    private static boolean hasPerk(MinecraftServer s, UUID owner, String kind) {
        if (s == null) return false;
        EventState st = EventState.get(s);
        EventState.OwnerData od = st.owners.get(owner);
        if (od == null || od.perks.isEmpty()) return false;
        long now = s.getOverworld().getTime();
        for (EventState.Perk p : od.perks) if (p.kind.equals(kind) && p.until > now && p.uses > 0) return true;
        return false;
    }

    @Nullable
    private static EventState.Perk peekPerk(MinecraftServer s, UUID owner, String kind) {
        if (s == null) return null;
        EventState.OwnerData od = EventState.get(s).owners.get(owner);
        if (od == null) return null;
        long now = s.getOverworld().getTime();
        od.perks.removeIf(p -> p.until <= now || p.uses <= 0);
        for (EventState.Perk p : od.perks) if (p.kind.equals(kind)) return p;
        return null;
    }

    @Nullable
    private static EventState.Perk takePerk(MinecraftServer s, UUID owner, String kind) {
        EventState.Perk p = peekPerk(s, owner, kind);
        if (p == null) return null;
        p.uses--;
        EventState.get(s).markDirty();
        return p;
    }

    /**
     * Price factor of a purchase from the Wholesaler (source &gt;= 0) or in the phone Market (source -1) due to
     * events: supplier discount (used up on purchase, consume=true) or delay surcharge (phone only).
     */
    public static double purchaseFactor(ServerPlayerEntity p, int source, boolean consume) {
        MinecraftServer s = p.getServer();
        double f = 1.0;
        EventState.Perk d = consume ? takePerk(s, p.getUuid(), "wholesale_discount") : peekPerk(s, p.getUuid(), "wholesale_discount");
        if (d != null) f *= Math.max(0.5, Math.min(1, d.factor));
        if (source == -1) {
            EventState.Perk m = peekPerk(s, p.getUuid(), "phone_markup");
            if (m != null) f *= Math.max(1, Math.min(1.5, m.factor));
        }
        return f;
    }

    /** Discount on the next hire ("someone is looking for work" event). */
    public static double hireFactor(ServerPlayerEntity p, boolean consume) {
        EventState.Perk d = consume ? takePerk(p.getServer(), p.getUuid(), "hire_discount") : peekPerk(p.getServer(), p.getUuid(), "hire_discount");
        return d == null ? 1.0 : Math.max(0.3, Math.min(1, d.factor));
    }

    /** Discount on the next submission to CobbleGrade ("expert visit" event). */
    public static double gradingFeeFactor(ServerPlayerEntity p, boolean consume) {
        EventState.Perk d = consume ? takePerk(p.getServer(), p.getUuid(), "grading_discount") : peekPerk(p.getServer(), p.getUuid(), "grading_discount");
        return d == null ? 1.0 : Math.max(0.5, Math.min(1, d.factor));
    }

    // ------------------------------------------------------------------ notifications

    public static MutableText name(EventDef def) {
        return Text.translatable(def.langKey() + ".name");
    }

    public static Formatting color(EventDef.Category c) {
        return switch (c) { case POSITIVE -> Formatting.GREEN; case NEGATIVE -> Formatting.RED; default -> Formatting.AQUA; };
    }

    private static void notifyStart(ServerWorld w, ShopZones.Zone z, EventDef def, EventInstance inst) {
        ServerPlayerEntity owner = w.getServer().getPlayerManager().getPlayer(z.owner);
        if (owner == null) return;
        String k = def.langKey();
        MutableText title = Text.literal("✦ ").append(name(def)).formatted(color(def.category()));
        MutableText body = Text.translatable(k + ".start");
        if (def.notice().toast()) {
            if (EventsConfig.INSTANCE.toasts && ServerPlayNetworking.canSend(owner, FactPayload.ID))
                ServerPlayNetworking.send(owner, new FactPayload(title, def.hasFix() ? Text.translatable(k + ".fix") : body));
            MutableText chat = Text.literal("✦ ").formatted(color(def.category())).append(name(def).formatted(Formatting.BOLD))
                    .append(Text.literal(" — ").formatted(Formatting.GRAY)).append(body.copy().formatted(Formatting.WHITE));
            if (def.hasFix()) chat.append(Text.literal(" ")).append(Text.translatable("tcgshop.event.fix_prefix").formatted(Formatting.YELLOW))
                    .append(Text.translatable(k + ".fix").formatted(Formatting.YELLOW));
            owner.sendMessage(chat, false);
            owner.playSoundToPlayer(def.negative() ? SoundEvents.BLOCK_NOTE_BLOCK_BASS.value() : SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(),
                    SoundCategory.PLAYERS, 0.8f, def.negative() ? 0.8f : 1.4f);
        }
    }

    private static void notifyResolved(ServerWorld w, ShopZones.Zone z, EventDef def, EventInstance inst) {
        ServerPlayerEntity owner = w.getServer().getPlayerManager().getPlayer(z.owner);
        if (owner == null || !def.negative() && !def.hasFix() || inst.resolvedHow.startsWith("failed")) return;
        MutableText msg = Text.translatable("tcgshop.event.resolved", name(def));
        if (EventsConfig.INSTANCE.toasts && ServerPlayNetworking.canSend(owner, FactPayload.ID))
            ServerPlayNetworking.send(owner, new FactPayload(Text.literal("✔ ").append(name(def)).formatted(Formatting.GREEN),
                    Text.translatable("tcgshop.event.resolved_body")));
        owner.sendMessage(msg.formatted(Formatting.GREEN), false);
        owner.playSoundToPlayer(SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.5f, 1.6f);
    }

    private static void sync(ServerWorld w, ShopZones.Zone z) {
        ServerPlayerEntity owner = w.getServer().getPlayerManager().getPlayer(z.owner);
        if (owner != null) ShopServer.syncZones(owner);
    }

    // ------------------------------------------------------------------ phone

    /** Event data for the owner's phone (ext.ev of ShopServer.syncZones). */
    public static NbtCompound writeSync(ServerWorld w, ShopZones.Zone z) {
        NbtCompound c = new NbtCompound();
        long now = w.getTime(), day = w.getTimeOfDay() / 24000;
        c.putLong("now", now);
        NbtList act = new NbtList();
        for (EventInstance inst : z.events.active) {
            EventDef def = EventRegistry.get(inst.defId);
            if (def == null) continue;
            NbtCompound a = new NbtCompound();
            a.putString("k", def.langKey());
            a.putString("cat", def.category().key());
            a.putString("tier", def.tier().key());
            a.putLong("left", Math.max(0, inst.end - now));
            a.putBoolean("res", inst.resolved());
            a.putBoolean("fix", def.hasFix());
            a.putString("how", inst.resolvedHow);
            act.add(a);
        }
        c.put("active", act);
        NbtList hist = new NbtList();
        for (ZoneEvents.Hist h : z.events.hist) {
            if (day - h.day() > 7) continue;
            NbtCompound x = new NbtCompound();
            x.putLong("ago", day - h.day());
            x.putString("k", EventDef.langKey(h.id()));
            x.putString("cat", h.category());
            x.putString("o", h.outcome());
            hist.add(x);
        }
        c.put("hist", hist);
        return c;
    }

    // ------------------------------------------------------------------ commands

    /** Nearest zone owned by the player (or the one they are in), in their world. */
    @Nullable
    public static ShopZones.Zone nearestZone(ServerPlayerEntity p, boolean anyOwner) {
        ShopZones zs = ShopZones.get(p.getServerWorld());
        ShopZones.Zone in = zs.at(p.getBlockPos());
        if (in != null && (anyOwner || in.owner.equals(p.getUuid()))) return in;
        ShopZones.Zone best = null;
        double bd = Double.MAX_VALUE;
        for (ShopZones.Zone z : zs.all()) {
            if (!anyOwner && !z.owner.equals(p.getUuid())) continue;
            double d = p.squaredDistanceTo(z.core.toCenterPos());
            if (d < bd) { bd = d; best = z; }
        }
        return best;
    }

    /** /tcgshop event roll: redoes the player's roll for today (ignores the chance: it always hits). */
    public static Text forceRoll(ServerPlayerEntity p) {
        MinecraftServer s = p.getServer();
        EventState st = EventState.get(s);
        EventState.OwnerData od = st.owner(p.getUuid());
        long day = today(s);
        od.rollDay = day;
        od.rollHit = true;
        od.firedToday = 0;
        od.pendingDef = "";
        od.fireTod = (int) Math.floorMod(s.getOverworld().getTimeOfDay(), 24000L);
        st.markDirty();
        return Text.translatable("tcgshop.event.cmd.rolled");
    }
}

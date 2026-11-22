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
 * Motor de eventos aleatorios.
 *
 * FRECUENCIA (EventsConfig): una tirada sembrada por dueño y día (semilla del mundo, uuid, día): dailyChance (10%) de
 * que pase algo. Si sale, se elige un momento al azar del turno de día; el evento empieza cuando llega ese momento, el
 * dueño está conectado y a menos de ownerRange bloques de una de sus tiendas (la que tenga más cerca). Si no aparece
 * en todo el día, se pierde. Como mucho maxPerOwnerPerDay (1) al día entre todas sus tiendas. Nada el primer día de
 * un mundo nuevo. Dentro de la tirada: categoría 40/30/30, rareza 65/25/8/2, enfriamiento por rareza, historial x0.25,
 * misma plantilla nunca dos veces seguidas, gracia para tiendas nuevas y "mazo" por mundo.
 *
 * API PÚBLICA para los módulos: {@link #resolve}, {@link #isActive}, {@link #activeFor}, {@link #logIncident},
 * {@link #invalidate}, {@link #mods}, {@link #state}. Ver EFFECTS.md.
 */
public final class EventManager {
    /** Etiqueta (command tag) de toda entidad de evento: no cuenta como Pokémon de la tienda ni se guarda. */
    public static final String EVENT_TAG = "tcgshop_event";

    private static MinecraftServer server;
    private static final Map<UUID, Mods> MODS = new HashMap<>();
    private static final Map<UUID, Long> MODS_AT = new HashMap<>();
    /** uid -> zona, de los eventos en marcha (se reconstruye cada 10 s y al arrancar). */
    private static final Map<Long, UUID> ACTIVE = new HashMap<>();

    private EventManager() {}

    // ------------------------------------------------------------------ arranque

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
                TcgShop.LOGGER.error("[eventos] error en el planificador", e);
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
                    TcgShop.LOGGER.error("[eventos] {} efecto {} falló en tick", inst.defId, i, e);
                }
            }
        }
        if (now % 1200 < 20) ze.trimLog(now);
    }

    // ------------------------------------------------------------------ planificador (frecuencia)

    /** Semilla de la tirada diaria de un dueño (no cambia al recargar el mundo). */
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
        if (day < 1 || ow.getTime() < 24_000) return; // nunca el primer día de un mundo nuevo
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
                od.pendingDef = ""; // lo que esperaba ayer a su franja se pierde
                st.markDirty();
            }
            if (!od.rollHit || od.firedToday >= cfg.maxPerOwnerPerDay || t < od.fireTod) continue;
            Target target = pickShop(s, p);
            if (target == null) continue; // espera (hasta que acabe el día) a que el dueño esté cerca de una tienda
            Random pick = new Random(daySeed(ow.getSeed(), p.getUuid(), day) ^ 0x5EEDL ^ od.firedToday * 977L);
            if (!od.pendingDef.isEmpty()) {
                // un evento elegido antes espera a su franja (noche...): empieza si ya encaja; si su franja de hoy ya
                // pasó (o desapareció la plantilla), se descarta; si no, sigue esperando
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
                // su franja horaria empieza más tarde hoy: se aplaza a un momento dentro de ella
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
     * Horas del día en las que mirar si un evento encaja: ahora (t) y el comienzo de cada franja "time" (de eventos y
     * variantes) que aún no ha llegado hoy. Así los eventos de noche pueden salir aunque el dueño sólo esté de día.
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

    /** Primera hora de probes (en orden) en la que el evento sería elegible, o -1. */
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

    /** La tienda del dueño en la que está (o la más cercana dentro de ownerRange), cargada y con expositores. */
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
     * Elige un evento para esta tienda (o null si no hay ninguno posible). forceCategory: sólo esa categoría (pruebas).
     * Categoría (pesos) -> rareza (pesos) -> evento (peso x historial x cadena), sólo entre los elegibles.
     */
    @Nullable
    public static EventDef pickDef(ServerWorld w, ShopZones.Zone z, EventState.OwnerData od, Random r, long day, @Nullable EventDef.Category forceCategory) {
        return pickDef(w, z, od, r, day, forceCategory, null, null);
    }

    /**
     * Como {@link #pickDef}, pero con probes (horas del día) un evento es elegible si encaja en cualquiera de ellas;
     * when recibe la primera hora en la que encaja cada evento elegible (para aplazar los de noche).
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

    /** Peso final de un evento para este dueño: configuración x historial x cadenas ("después de X, más probable Y"). */
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

    /** ¿Puede salir este evento ahora en esta tienda? why recibe el motivo si no. */
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

    // ------------------------------------------------------------------ empezar / arreglar / terminar

    /**
     * Empieza un evento en una tienda. variant null = al azar entre las que encajen. forced = desde un comando
     * (no comprueba condiciones). Devuelve la instancia o null.
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
        if (!forced) for (EventEffect e : effs) if (!e.canStart(w, z)) return null; // sin sitio: no gasta el día ni el cooldown
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
                TcgShop.LOGGER.error("[eventos] {} efecto {} falló al empezar", def.id(), i, e);
            }
        }
        // memoria de reseñas (después de start: un módulo puede cambiar el tag con inst.reviewTag)
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
        TcgShop.LOGGER.info("[eventos] {} empieza en la tienda de {} ({}){}", def.id(), z.ownerName, inst.uid, forced ? " [comando]" : "");
        sync(w, z);
        return inst;
    }

    /** Arregla un evento ("how": mop, guard, talk, metric, visit, shoo, feed...). Devuelve false si no existe o ya estaba arreglado. */
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
                    TcgShop.LOGGER.error("[eventos] {} efecto {} falló al resolverse", f.inst.defId, i, e);
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

    /** Termina un evento ya (comando, o un módulo que necesita cortarlo). */
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
                    TcgShop.LOGGER.error("[eventos] {} efecto {} falló al terminar", inst.defId, i, e);
                }
            }
        }
        for (IncidentRecord r : z.events.log) if (r.uid == inst.uid && r.end < 0) r.end = now;
        String out = inst.resolved() ? (inst.resolvedHow.startsWith("failed") ? "failed" : "resolved") : outcome;
        z.events.addHist(w.getTimeOfDay() / 24000, inst.defId, def == null ? "neutral" : def.category().key(), out);
        if (w.getServer() != null) {
            EventState st = EventState.get(w.getServer());
            st.clearActorsOf(inst.uid);
            // las ventajas ("próxima compra/contratación/gradeo") siguen hasta su until aunque el evento acabe (p. ej. el
            // visitante se va); sólo se quitan si el evento se corta (comando, tienda borrada, cancelado)
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

    /** La tienda desaparece (núcleo roto): termina sus eventos para que los módulos lo limpien todo. */
    public static void onZoneRemoved(ServerWorld w, ShopZones.Zone z) {
        for (EventInstance e : new ArrayList<>(z.events.active)) end(w, z, e, "removed");
    }

    // ------------------------------------------------------------------ consultas (API pública)

    /** ¿Sigue en marcha este evento? (false si no existe, terminó o su tienda se borró) */
    public static boolean isActive(long uid) {
        UUID zid = ACTIVE.get(uid);
        if (zid == null || server == null) return false;
        for (ServerWorld w : server.getWorlds()) {
            ShopZones.Zone z = ShopZones.get(w).byId(zid);
            if (z != null) return z.events.byUid(uid) != null;
        }
        return false;
    }

    /** ¿En marcha y sin arreglar? */
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
     * Apunta algo en la memoria de reseñas de la tienda (para los módulos: p. ej. un charco concreto con su tag).
     * Se marca como arreglado cuando se resuelve el evento uid (o con {@link #resolveIncident}).
     */
    public static void logIncident(ServerWorld w, ShopZones.Zone z, long uid, String tag, boolean negative, int mood, double chance) {
        for (IncidentRecord r : z.events.log) if (r.uid == uid && r.tag.equals(tag) && r.end < 0) return;
        z.events.log.add(new IncidentRecord(uid, "", tag, w.getTime(), !negative && mood > 0, negative, mood, chance,
                negative ? EventsConfig.INSTANCE.reviewFixedChance : 0));
        ShopZones.get(w).markDirty();
    }

    /** Marca como arreglados los registros de ese evento con ese tag (sin resolver el evento entero). */
    public static void resolveIncident(ServerWorld w, ShopZones.Zone z, long uid, String tag) {
        for (IncidentRecord r : z.events.log) if (r.uid == uid && r.tag.equals(tag) && r.resolvedAt < 0) r.resolvedAt = w.getTime();
        ShopZones.get(w).markDirty();
    }

    // ------------------------------------------------------------------ modificadores

    /** Modificadores de los eventos activos de la tienda (cacheados; Mods.NONE si no hay ninguno). */
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

    /** Tras cambiar un evento (bloqueos, datos de resultado...). */
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
            // resultado de un visitante (inspección, influencer...): dura hasta o_until
            if (inst.data.getLong("o_until") > now) {
                b.repDelta += inst.data.getInt("o_rep");
                if (inst.data.contains("o_spawn")) b.spawnMult *= Math.max(0.1, inst.data.getDouble("o_spawn"));
            }
        }
        return b.build(any);
    }

    /** Reputación "efectiva" para clientes (llegada y decisión): la real + el modificador temporal de los eventos. No toca premiumFor. */
    public static int effectiveRep(ShopZones.Zone z) {
        if (z.events.active.isEmpty()) return z.reputation;
        return Math.max(0, Math.min(100, z.reputation + mods(z).repDelta));
    }

    // ------------------------------------------------------------------ ganchos de la tienda

    /** ShopLife.recompute: datos para condiciones y objetivos medibles. */
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
                    TcgShop.LOGGER.error("[eventos] {} efecto {} falló en onRecompute", inst.defId, i, e);
                }
            }
        }
    }

    /**
     * ShopServer.payFromNpc: anota los ingresos del día (topes de premios), avisa a los efectos (objetivos) y aplica
     * revenueMult y la propina (con el tope por dueño). Devuelve el dinero EXTRA ya sumado a la caja.
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
                    TcgShop.LOGGER.error("[eventos] {} efecto {} falló en onSale", inst.defId, i, e);
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

    /** Un cliente aparece: cuenta para "llegaron clientes mientras seguía sin arreglar". */
    public static void onCustomerSpawn(ServerWorld w, ShopZones.Zone z) {
        if (z.events.log.isEmpty()) return;
        long now = w.getTime();
        for (IncidentRecord r : z.events.log) if (r.activeAt(now)) r.customersDuring++;
    }

    /** Frase de un cliente que entra mientras hay un evento activo (o null). */
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

    // ------------------------------------------------------------------ memoria de reseñas

    /** Lo que un evento cambia en la reseña de un cliente: clave (null = no cambia) y ánimo a sumar. */
    public record ReviewOutcome(@Nullable String key, int mood) {}

    private static final Set<String> STRONG_KEYS = Set.of("murder", "hit", "infamy", "shop_full");

    /**
     * Al irse un cliente (CustomerEntity.finishVisit, antes de calcular las estrellas):
     *  · si coincidió con un evento sin arreglar: con su probabilidad, reseña "ev_&lt;tag&gt;" y su ánimo (el peor
     *    negativo gana a cualquier positivo);
     *  · si se arregló antes de que llegara (ese día) y alguien llegó a verlo: a veces "ev_&lt;tag&gt;_fixed", sin castigo;
     *  · si se arregló antes de que llegara ningún cliente: nada.
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

    // ------------------------------------------------------------------ visitantes

    /** Resultado de la visita de un visitante de evento: clave de reseña a usar (o null) y estrellas forzadas (o 0). */
    public record VisitorResult(@Nullable String key, int stars) {}

    /** CustomerEntity.finishVisit de un visitante de evento. stars = las que dejaría. */
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

    /** Clic derecho del dueño a un visitante de evento (calmar al cliente molesto). true = consumido. */
    public static boolean onVisitorInteract(ServerPlayerEntity sp, ShopZones.Zone z, CustomerEntity c, VisitorSpec spec) {
        if (!spec.troublemaker || !z.owner.equals(sp.getUuid())) return false;
        EventInstance inst = z.events.byUid(spec.eventUid);
        if (inst == null || inst.resolved()) return false;
        dev.tcgshop.entity.NpcLines.speak(c, Text.translatable("tcgshop.evline.calmed." + sp.getRandom().nextInt(6)));
        sp.getServerWorld().spawnParticles(net.minecraft.particle.ParticleTypes.HAPPY_VILLAGER, c.getX(), c.getY() + 2, c.getZ(), 6, 0.3, 0.3, 0.3, 0);
        resolve(inst.uid, "calm");
        return true;
    }

    /** Clic derecho del dueño a su cajero: lo "despierta" si está distraído. true = consumido. */
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

    // ------------------------------------------------------------------ dinero y ventajas (topes por dueño)

    /** Tope de dinero de premios que le queda hoy a este dueño (diario y semanal). */
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

    /** Da dinero de un premio a la caja de la tienda, con el tope por dueño. Devuelve lo que se dio de verdad. */
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
     * Factor de precio de una compra al Mayorista (source &gt;= 0) o en el Mercado del teléfono (source -1) por los
     * eventos: descuento del proveedor (se gasta al comprar, consume=true) o recargo por retraso (sólo teléfono).
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

    /** Descuento en la próxima contratación (evento "alguien busca trabajo"). */
    public static double hireFactor(ServerPlayerEntity p, boolean consume) {
        EventState.Perk d = consume ? takePerk(p.getServer(), p.getUuid(), "hire_discount") : peekPerk(p.getServer(), p.getUuid(), "hire_discount");
        return d == null ? 1.0 : Math.max(0.3, Math.min(1, d.factor));
    }

    /** Descuento en el próximo envío a CobbleGrade (evento "visita de un experto"). */
    public static double gradingFeeFactor(ServerPlayerEntity p, boolean consume) {
        EventState.Perk d = consume ? takePerk(p.getServer(), p.getUuid(), "grading_discount") : peekPerk(p.getServer(), p.getUuid(), "grading_discount");
        return d == null ? 1.0 : Math.max(0.5, Math.min(1, d.factor));
    }

    // ------------------------------------------------------------------ avisos

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

    // ------------------------------------------------------------------ teléfono

    /** Datos de eventos para el teléfono del dueño (ext.ev de ShopServer.syncZones). */
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

    // ------------------------------------------------------------------ comandos

    /** Zona de la que es dueño el jugador más cercana (o en la que está), en su mundo. */
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

    /** /tcgshop event roll: repite la tirada de hoy del jugador (ignora la probabilidad: sale seguro). */
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

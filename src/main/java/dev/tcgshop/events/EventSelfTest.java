package dev.tcgshop.events;

import dev.tcgshop.TcgShop;
import dev.tcgshop.shop.ShopZones;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * /tcgshop debug eventtest: lee todas las definiciones, prueba las condiciones con una tienda inventada, los topes de
 * Mods, ida y vuelta por NBT (zona y estado global), la memoria de reseñas y simula 10.000 días-dueño para comprobar
 * que sale ~10% de días con evento y el reparto por rareza. Todo va al log con el prefijo [eventtest].
 */
public final class EventSelfTest {
    private EventSelfTest() {}

    public static int run(ServerCommandSource src) {
        int fails = 0;
        ServerWorld w = src.getServer().getOverworld();
        // ---- 1. definiciones
        int neg = 0, noFix = 0;
        Map<EventDef.Category, Integer> byCat = new EnumMap<>(EventDef.Category.class);
        Map<EventDef.Tier, Integer> byTier = new EnumMap<>(EventDef.Tier.class);
        for (EventDef d : EventRegistry.all()) {
            byCat.merge(d.category(), 1, Integer::sum);
            byTier.merge(d.tier(), 1, Integer::sum);
            if (d.negative()) { neg++; if (!d.hasFix()) noFix++; }
        }
        log("definiciones: {} cargadas, {} descartadas, por categoría {}, por rareza {}", EventRegistry.all().size(), EventRegistry.lastErrors().size(), byCat, byTier);
        for (String e : EventRegistry.lastErrors()) log("  descartada: {}", e);
        if (noFix > 0) { fails++; log("FALLO: {} negativos sin arreglo", noFix); }
        if (EventRegistry.all().isEmpty()) { fails++; log("FALLO: no hay eventos"); }
        log("tipos de efecto registrados: {}", EffectTypes.types());

        // ---- 2. condiciones con una tienda inventada
        ShopZones.Zone z = new ShopZones.Zone(UUID.randomUUID(), UUID.randomUUID(), "eventtest", w.getSpawnPos(), 2);
        z.sales = 120; z.displays = 6; z.reputation = 62; z.decor = 8; z.events.createdDay = 0; z.cashier = true;
        z.events.hasRegister = true;
        z.events.doors = java.util.List.of(w.getSpawnPos().east(3));
        z.flags.add("cozy");
        int pass = 0;
        Map<String, Integer> reasons = new HashMap<>();
        for (EventDef d : EventRegistry.all()) {
            StringBuilder why = new StringBuilder();
            if (d.conditions().test(w, z, EventManager.implicitRequires(d.effects()), why)) pass++;
            else reasons.merge(why.toString().split(" ")[0], 1, Integer::sum);
        }
        log("condiciones en tienda de prueba (hierro, rep 62, 120 ventas, cajero, caja, puerta): {} de {} pasan; fallos por motivo {}", pass, EventRegistry.all().size(), reasons);

        // ---- 3. topes de Mods
        Mods.Builder b = new Mods.Builder();
        b.spawnMult = 9; b.buyMult = 9; b.perceivedMult = 0.1; b.revenueMult = 3; b.repDelta = -50; b.moodStart = 9; b.serviceMult = 0.01; b.maxCustAdd = 20;
        b.type("whale", 50);
        Mods m = b.build(true);
        EventsConfig.Clamps c = EventsConfig.INSTANCE.clamps;
        boolean clampsOk = m.spawnMult <= c.spawnMult[1] && m.buyMult <= c.buyMult[1] && m.perceivedMult >= c.perceivedMult[0] && m.revenueMult <= 1.15
                && m.repDelta >= c.repDelta[0] && m.moodStart <= c.moodStart[1] && m.serviceMult >= c.serviceMult[0] && m.maxCustAdd <= c.maxCustAdd[1]
                && m.typeWeight(dev.tcgshop.entity.CustomerEntity.Type.WHALE) <= c.typeWeightMax;
        log("topes de Mods: spawn {} buy {} perceived {} revenue {} rep {} mood {} service {} whale x{} {}", m.spawnMult, m.buyMult, m.perceivedMult, m.revenueMult,
                m.repDelta, m.moodStart, m.serviceMult, m.typeWeight(dev.tcgshop.entity.CustomerEntity.Type.WHALE), clampsOk ? "OK" : "FALLO");
        if (!clampsOk) fails++;
        if (Mods.NONE.spawnMult != 1 || Mods.NONE.any) { fails++; log("FALLO: Mods.NONE no es neutro"); }

        // ---- 4. NBT ida y vuelta
        try {
            EventInstance inst = new EventInstance(77, "tcgshop:test", "v1", z.id, z.owner, 1000, 7000, 3);
            inst.disableDisplay(new BlockPos(1, 2, 3));
            inst.disableSlot(new BlockPos(4, 5, 6), 2);
            inst.closeDoor(new BlockPos(7, 8, 9));
            inst.setRegisterDown(true);
            inst.setLightingOverride(4);
            inst.data.putInt("o_rep", 3);
            inst.visitors.add(UUID.randomUUID());
            inst.resolvedAt = 2000;
            inst.resolvedHow = "mop";
            z.events.active.add(inst);
            IncidentRecord r = new IncidentRecord(77, "tcgshop:test", "flood", 1000, false, true, -2, 0.6, 0.25);
            r.customersDuring = 3;
            z.events.log.add(r);
            z.events.addHist(3, "tcgshop:test", "negative", "resolved");
            z.events.addRevenue(3, 5000);
            NbtCompound nbt = z.write();
            ShopZones.Zone back = ShopZones.Zone.read(nbt);
            EventInstance bi = back.events.byUid(77);
            boolean ok = bi != null && bi.variant.equals("v1") && bi.disabledDisplays.contains(new BlockPos(1, 2, 3).asLong())
                    && bi.disabledSlots.contains(new BlockPos(4, 5, 6).asLong() + ":2") && bi.closedDoors.contains(new BlockPos(7, 8, 9).asLong())
                    && bi.registerDown && bi.lightingOverride == 4 && bi.data.getInt("o_rep") == 3 && bi.visitors.size() == 1 && bi.resolvedAt == 2000
                    && "mop".equals(bi.resolvedHow) && back.events.log.size() == 1 && back.events.log.get(0).customersDuring == 3
                    && back.events.hist.size() == 1 && back.events.rev[0] == 5000 && back.events.createdDay == 0;
            // una zona de un mundo antiguo (sin ext.events) carga vacía
            NbtCompound old = z.write();
            old.getCompound("ext").remove("events");
            ShopZones.Zone o2 = ShopZones.Zone.read(old);
            ok &= o2.events.active.isEmpty() && o2.events.createdDay == -1;
            EventState st = new EventState();
            EventState.OwnerData od = st.owner(z.owner);
            od.rollDay = 5; od.rollHit = true; od.fireTod = 4321; od.addHist("tcgshop:test", "tpl", 5); od.money[0] = 99;
            EventState.Perk pk = new EventState.Perk();
            pk.kind = "tip"; pk.factor = 0.1; pk.uses = 1; pk.until = 9999; pk.uid = 77;
            od.perks.add(pk);
            st.registerActor(UUID.randomUUID(), 77);
            EventState sb = st.roundTrip(w.getRegistryManager());
            EventState.OwnerData ob = sb.owner(z.owner);
            ok &= ob.rollHit && ob.fireTod == 4321 && ob.hist.size() == 1 && ob.money[0] == 99 && ob.perks.size() == 1 && sb.actorsOf(77).size() == 1
                    && "tpl".equals(ob.lastTemplate());
            log("NBT ida y vuelta (zona, mundo antiguo, estado global): {}", ok ? "OK" : "FALLO");
            if (!ok) fails++;
            z.events.active.clear();
        } catch (Exception e) {
            fails++;
            TcgShop.LOGGER.error("[eventtest] NBT FALLO", e);
        }

        // ---- 5. memoria de reseñas
        {
            ShopZones.Zone rz = new ShopZones.Zone(UUID.randomUUID(), UUID.randomUUID(), "rv", BlockPos.ORIGIN, 0);
            net.minecraft.util.math.random.Random rr = net.minecraft.util.math.random.Random.create(1);
            IncidentRecord over = new IncidentRecord(1, "a", "flood", 100, false, true, -2, 1.0, 1.0);
            rz.events.log.add(over);
            var o1 = EventManager.reviewFor(rz, 150, 300, "fair", rr);
            over.resolvedAt = 120;
            over.end = 120;
            over.customersDuring = 1;
            var o2 = EventManager.reviewFor(rz, 200, 400, "fair", rr);
            over.customersDuring = 0;
            var o3 = EventManager.reviewFor(rz, 200, 400, "fair", rr);
            boolean ok = "ev_flood".equals(o1.key()) && o1.mood() == -2 && "ev_flood_fixed".equals(o2.key()) && o2.mood() == 0 && o3.key() == null;
            log("memoria de reseñas: solapa={} {} · arreglado antes={} · antes de ningún cliente={} {}", o1.key(), o1.mood(), o2.key(), o3.key(), ok ? "OK" : "FALLO");
            if (!ok) fails++;
        }

        // ---- 6. frecuencia: 10.000 días-dueño (sin condiciones de mundo: todas se cumplen)
        {
            EventsConfig cfg = EventsConfig.INSTANCE;
            Random seedRnd = new Random(12345);
            int days = 0, hits = 0, events = 0, sameInRow = 0;
            Map<EventDef.Category, Integer> cat = new EnumMap<>(EventDef.Category.class);
            Map<EventDef.Tier, Integer> tier = new EnumMap<>(EventDef.Tier.class);
            EventState fake = new EventState();
            long worldSeed = w.getSeed();
            for (int owner = 0; owner < 20; owner++) {
                UUID ou = new UUID(seedRnd.nextLong(), seedRnd.nextLong());
                EventState.OwnerData od = fake.owner(ou);
                String last = "";
                for (long day = 1; day <= 500; day++) {
                    days++;
                    Random r = new Random(EventManager.daySeed(worldSeed, ou, day));
                    if (!(r.nextDouble() < cfg.dailyChance)) continue;
                    hits++;
                    Random pick = new Random(EventManager.daySeed(worldSeed, ou, day) ^ 0x5EEDL);
                    EventDef d = simulatePick(fake, od, pick, day, worldSeed);
                    if (d == null) continue;
                    events++;
                    if (d.id().equals(last)) sameInRow++;
                    last = d.id();
                    cat.merge(d.category(), 1, Integer::sum);
                    tier.merge(d.tier(), 1, Integer::sum);
                    od.addHist(d.id(), d.template(), day);
                }
            }
            double rate = hits * 100.0 / days;
            StringBuilder tp = new StringBuilder();
            for (EventDef.Tier t : EventDef.Tier.values())
                tp.append(t.key()).append(' ').append(String.format(java.util.Locale.ROOT, "%.1f%%", tier.getOrDefault(t, 0) * 100.0 / Math.max(1, events))).append("  ");
            log("frecuencia: {} días-dueño, {} con tirada ({}%), {} eventos; categorías {}; rareza {}; extraordinarios por día {}%; repetidos seguidos {}",
                    days, hits, String.format(java.util.Locale.ROOT, "%.2f", rate), events, cat, tp.toString().trim(),
                    String.format(java.util.Locale.ROOT, "%.3f", tier.getOrDefault(EventDef.Tier.EXTRAORDINARY, 0) * 100.0 / days), sameInRow);
            if (Math.abs(rate - cfg.dailyChance * 100) > 1.5) { fails++; log("FALLO: la tasa diaria se aleja de dailyChance"); }
            if (sameInRow > 0) { fails++; log("FALLO: un evento salió dos veces seguidas"); }
        }
        int f = fails;
        log("resultado: {}", f == 0 ? "TODO OK" : f + " FALLOS");
        src.sendFeedback(() -> Text.literal("[eventtest] " + (f == 0 ? "OK" : f + " fallos") + " (detalles en el log)").formatted(f == 0 ? Formatting.GREEN : Formatting.RED), false);
        return f == 0 ? 1 : 0;
    }

    /** Elección como EventManager.pickDef pero sin mundo (todas las condiciones se cumplen; gracia superada). */
    private static EventDef simulatePick(EventState st, EventState.OwnerData od, Random r, long day, long seed) {
        EventsConfig cfg = EventsConfig.INSTANCE;
        Map<EventDef.Category, Map<EventDef.Tier, java.util.List<EventDef>>> pool = new EnumMap<>(EventDef.Category.class);
        for (EventDef d : EventRegistry.all()) {
            if (!cfg.enabled(d) || cfg.weight(d) <= 0 || !st.inDeck(d, seed)) continue;
            Long last = od.cooldown.get(d.id());
            if (last != null && day - last < cfg.cooldownDays(d)) continue;
            if (!od.lastTemplate().isEmpty() && od.lastTemplate().equals(d.template())) continue;
            if (d.conditions().maxPerWeek() > 0 && od.countSince(d.id(), day - 6) >= d.conditions().maxPerWeek()) continue;
            pool.computeIfAbsent(d.category(), k -> new EnumMap<>(EventDef.Tier.class)).computeIfAbsent(d.tier(), k -> new java.util.ArrayList<>()).add(d);
        }
        if (pool.isEmpty()) return null;
        EventDef.Category c = pickW(r, pool.keySet(), cfg::categoryWeight);
        if (c == null) return null;
        EventDef.Tier t = pickW(r, pool.get(c).keySet(), cfg::tierWeight);
        if (t == null) return null;
        return pickW(r, pool.get(c).get(t), d -> EventManager.defWeight(d, od, day));
    }

    private static <T> T pickW(Random r, java.util.Collection<T> items, java.util.function.ToDoubleFunction<T> wf) {
        double sum = 0;
        for (T x : items) sum += Math.max(0, wf.applyAsDouble(x));
        if (sum <= 0) return null;
        double roll = r.nextDouble() * sum;
        T lastPos = null;
        for (T x : items) {
            double v = Math.max(0, wf.applyAsDouble(x));
            if (v <= 0) continue;
            lastPos = x;
            if ((roll -= v) < 0) return x;
        }
        return lastPos;
    }

    private static void log(String fmt, Object... args) {
        TcgShop.LOGGER.info("[eventtest] " + fmt, args);
    }
}

package dev.tcgshop.shop;

import dev.masterball.api.platform.event.ServerEvents;
import dev.tcgshop.advancement.CardAdvancements;
import dev.tcgshop.entity.CustomerEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import java.util.HashSet;
import java.util.Set;

/**
 * Shop life: reputation, customer spawning and wages.
 *
 * Reputation (0-100) = base 30 + prices + variety + displays + decoration (posters, tables, pedestals and
 * Pokeblocks plushies) + security (guard, detectors) + service (cashier) + satisfaction (sales vs complaints)
 * - thefts + "rumor of the day" (daily random event). More reputation = more customers and more collectors.
 */
public final class ShopLife {
    /** Customer arrival multiplier (EconomyConfig.customerDemand). */
    public static double DEMAND = 1.0;
    private ShopLife() {}

    public static void register() {
        ServerEvents.LEVEL_TICK_END.register(ShopLife::tick);
        ServerEvents.LIVING_DEATH.register(ShopLife::onNpcDeath);
        ShopMissions.init(); // 1.0: mission ledger / charisma lifecycle and the missions' tip luck
    }

    /**
     * Killing a shop NPC (customers, Wholesaler, trader, employees) costs reputation in all your shops.
     * Only counts if you caused the death (hit, arrow, etc.); thieves and scalpers don't count.
     */
    private static void onNpcDeath(net.minecraft.world.entity.LivingEntity entity, net.minecraft.world.damagesource.DamageSource source) {
        if (!(entity instanceof dev.tcgshop.entity.TcgNpc) || !(entity.level() instanceof ServerLevel w)) return;
        if (entity instanceof CustomerEntity c && c.type() == CustomerEntity.Type.THIEF) return;
        if (entity instanceof dev.tcgshop.entity.ScalperEntity) return;
        if (entity instanceof dev.tcgshop.entity.TraderEntity tr && tr.isRobbed()) return; // 1.0 beta 3: CollectorTheft settles it
        if (!(source.getEntity() instanceof ServerPlayer killer)) return;
        int pen = dev.tcgshop.EconomyConfig.INSTANCE.infamyPerKill;
        if (pen <= 0) return;
        boolean any = false;
        for (ServerLevel sw : w.getServer().getAllLevels()) {
            ShopZones zs = ShopZones.get(sw);
            for (ShopZones.Zone z : zs.allAndDormant()) // 1.1: dormant shops too
                if (z.owner.equals(killer.getUUID())) {
                    z.infamy = Math.min(100, z.infamy + pen);
                    zs.setDirty();
                    any = true;
                }
        }
        killer.displayClientMessage(net.minecraft.network.chat.Component.translatable(any ? "tcgshop.infamy.kill" : "tcgshop.infamy.kill_noshop", entity.getDisplayName(), pen)
                .withStyle(net.minecraft.ChatFormatting.RED), false);
        for (CustomerEntity c : w.getEntitiesOfClass(CustomerEntity.class, entity.getBoundingBox().inflate(24), x -> x != entity && x.isAlive()))
            c.witnessMurder();
    }

    private static void tick(ServerLevel world) {
        long t = world.getGameTime();
        lastTick = t;
        if (t % 20 == 0) ShopServer.payWages(world);
        if (t % 100 == 0) ShopServer.shiftTick(world);
        if (t % 200 == 7) cleanQueues(t);
        ShopZones zones = ShopZones.get(world);
        if (zones.all().isEmpty()) return;
        for (ShopZones.Zone z : zones.all()) {
            // spread the work across ticks
            int phase = Math.floorMod(z.id.hashCode(), 100);
            if (t % 1200 == phase) recompute(world, z);
            if (t % 100 == phase % 100) spawnCustomers(world, z);
            if (t % 600 == phase && z.smell > 0) { z.smell--; zones.setDirty(); }
            if (t % 600 == (phase + 300) % 600) Cleanliness.tick(world, z); // 1.1: droppings, their smell
            if (t % 40 == phase % 40 && z.smell >= 45) {
                // little green clouds around the shop when it stinks
                int half = z.size() / 2;
                double x = z.core.getX() + world.random.nextInt(half * 2 + 1) - half + 0.5, zz = z.core.getZ() + world.random.nextInt(half * 2 + 1) - half + 0.5;
                world.sendParticles(new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(0.45f, 0.62f, 0.2f), 1.6f),
                        x, z.core.getY() + 1.2, zz, 4, 0.4, 0.4, 0.4, 0);
            }
        }
    }

    // ------------------------------------------------------------------ reputation

    /**
     * Are all of the zone's chunks loaded? If any is missing, displays and decoration aren't seen and the
     * reputation would come out falsely low (and since it falls fast and rises slowly, it would sink while the owner is away).
     */
    public static boolean zoneLoaded(ServerLevel world, ShopZones.Zone z) {
        int half = z.size() / 2;
        int cx0 = (z.core.getX() - half) >> 4, cx1 = (z.core.getX() + half) >> 4, cz0 = (z.core.getZ() - half) >> 4, cz1 = (z.core.getZ() + half) >> 4;
        for (int cx = cx0; cx <= cx1; cx++)
            for (int cz = cz0; cz <= cz1; cz++)
                if (world.getChunkSource().getChunkNow(cx, cz) == null) return false;
        return true;
    }

    /** Full periodic recompute (every 1200 ticks): moves the reputation and advances the events. */
    public static void recompute(ServerLevel world, ShopZones.Zone z) {
        recompute(world, z, true);
    }

    /**
     * Preview for the owner's status window: redoes factors, scores and missions, but does NOT move the reputation
     * (settleReputation), doesn't forget the day's infamy and doesn't advance the events (EventManager.onRecompute), which are
     * meant for one call per minute. Opening the window many times doesn't speed anything up.
     */
    public static void preview(ServerLevel world, ShopZones.Zone z) {
        recompute(world, z, false);
    }

    /**
     * "Effective" reputation for customers: EventManager.effectiveRep's (with Mods.repDelta) + what the lighting /
     * decoration events temporarily change ({@link ShopZones.Zone#eventEnvRep}). Neither of the two goes into
     * settleReputation.
     */
    public static int effectiveRep(ShopZones.Zone z) {
        int r = dev.tcgshop.events.EventManager.effectiveRep(z);
        if (z.eventEnvRep == 0 || z.events.active.isEmpty()) return r;
        return Math.max(0, Math.min(100, r + z.eventEnvRep));
    }

    private static int lightingFactor(double l) {
        return l >= 11 ? 10 : l >= 8 ? 5 : l < 5 ? -10 : l < 7 ? -4 : 0;
    }

    private static void recompute(ServerLevel world, ShopZones.Zone z, boolean full) {
        // shop not loaded: its state is unknown, so the reputation isn't touched (recomputed on return)
        if (!zoneLoaded(world, z)) return;
        int displays = 0, decor = 0, detectors = 0, stocked = 0, stockedDisplays = 0, playTables = 0;
        boolean boosterBox = false, register = false;
        // 1.0 missions: what else is on display and placed (counted in the same pass)
        int packSlots = 0, boxSlots = 0, posters = 0, pedestals = 0, registers = 0, showcases = 0, rgbDisplays = 0, premiumDisplays = 0,
                graded = 0, gemGraded = 0;
        long stockValue = 0, topCard = 0;
        double ratioSum = 0;
        Set<String> variety = new HashSet<>();
        int half = z.size() / 2;
        int cx0 = (z.core.getX() - half) >> 4, cx1 = (z.core.getX() + half) >> 4, cz0 = (z.core.getZ() - half) >> 4, cz1 = (z.core.getZ() + half) >> 4;
        for (int cx = cx0; cx <= cx1; cx++)
            for (int cz = cz0; cz <= cz1; cz++) {
                LevelChunk ch = world.getChunkSource().getChunkNow(cx, cz);
                if (ch == null) continue;
                for (BlockEntity be : ch.getBlockEntities().values()) {
                    if (!z.contains(be.getBlockPos())) continue;
                    if (be instanceof ShopDisplayBlockEntity d && !d.isHome()) {
                        displays++;
                        if (be.getBlockState().is(TcgBlocks.PEDESTAL)) decor++;
                        BlockState ds = be.getBlockState();
                        boolean rgb = BuiltInRegistries.BLOCK.getKey(ds.getBlock()).getPath().contains("rgb"); // 1.0 RGB displays, by id
                        if (ds.is(TcgBlocks.PEDESTAL)) pedestals++;
                        if (ds.is(TcgBlocks.SHOWCASE)) showcases++;
                        if (rgb) rgbDisplays++;
                        if (rgb || ds.is(TcgBlocks.SHOWCASE) || ds.is(TcgBlocks.CARD_WALL) || ds.is(TcgBlocks.PEDESTAL)) premiumDisplays++;
                        boolean any = false;
                        for (int i = 0; i < d.getContainerSize(); i++) {
                            ItemStack st = d.getItem(i);
                            if (st.isEmpty()) continue;
                            stocked++;
                            any = true;
                            if (st.is(TcgBlocks.BOOSTER_BOX_ITEM)) boosterBox = true;
                            variety.add(BuiltInRegistries.ITEM.getKey(st.getItem()).toString());
                            long market = ShopServer.priceOf(st, 1.0);
                            ratioSum += market <= 0 ? 1 : d.price(i) / (double) market;
                            stockValue += Math.max(0, market) * Math.max(1, st.getCount());
                            if (dev.masterball.api.card.CardInfo.isCard(st)) topCard = Math.max(topCard, market);
                            else if (dev.masterball.api.card.CardInfo.isPack(st)) packSlots++;
                            if (st.is(TcgBlocks.BOOSTER_BOX_ITEM)) boxSlots++;
                            int gr = dev.tcgshop.grading.Grading.grade(st);
                            if (gr > 0) { graded++; if (gr >= 100) gemGraded++; }
                        }
                        if (any) stockedDisplays++;
                    } else if (be instanceof DecorBlocks.DecorEntity) {
                        BlockState s = be.getBlockState();
                        if (s.is(TcgBlocks.THEFT_DETECTOR)) detectors++;
                        else if (s.is(TcgBlocks.CASH_REGISTER)) { register = true; registers++; }
                        if (s.is(TcgBlocks.PLAY_TABLE)) { playTables++; decor++; } // the play table also counts as decoration (as before 0.9)
                        else if (s.is(TcgBlocks.POSTER)) { decor++; posters++; }
                    }
                }
            }
        // placed Pokeblocks plushies (band at the shop's floor height)
        // (in the same pass: furniture seats -chairs, benches, sofas- for the play area and the capacity)
        int dolls = 0, seats = 0, statues = 0, statueBest = 0;
        // 1.1: decoration liking (DecorScore): points of every decor object, repeated copies count less
        double decorPts = 0;
        java.util.Map<ResourceLocation, Integer> decorCopies = new java.util.HashMap<>();
        // 1.1: the zone's whole height (posters high on a wall, a mezzanine, an upper floor); empty chunk sections are skipped
        int scanTop = z.core.getY() + Math.max(3, z.up());
        for (BlockPos p : BlockPos.betweenClosed(z.core.getX() - half, z.core.getY() - 1, z.core.getZ() - half, z.core.getX() + half, scanTop, z.core.getZ() + half)) {
            if (p.getY() > z.core.getY() + 3 && emptySection(world, p)) continue;
            BlockState bs = world.getBlockState(p);
            if (bs.isAir()) continue;
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(bs.getBlock());
            if (id.getNamespace().equals("minecraft")) continue;
            if (DecorScore.isSecondaryPart(bs)) continue; // a 2-block bed / tall furniture counts once
            int dp = DecorScore.points(id);
            if (dp > 0) decorPts += dp * DecorScore.copyWeight(decorCopies.merge(id, 1, Integer::sum));
            if (bs.getBlock() instanceof dev.tcgshop.luxury.StatueBlock sb) { // 1.0 missions: luxury statues in the shop
                statues++;
                statueBest = Math.max(statueBest, sb.tier.index());
            }
            else if (id.getNamespace().equals("pokeblocks") && id.getPath().contains("pokedoll")) dolls++;
            else if (ShopSeats.seatWord(id.getPath())) seats++; // 0.9.5: a single list of seats (ShopSeats.SEAT_WORDS)
        }
        decor += dolls;
        z.decorCopies = decorCopies; // 1.1: synced to the owner so the decor tooltips apply the same copy weight

        long day = world.getDayTime() / 24000;
        java.util.Random daily = new java.util.Random(z.id.getMostSignificantBits() ^ day * 31);
        int buzz = daily.nextInt(11) - 5;
        // each day part of the bad stuff is forgotten: infamy from kills and unattended customers
        if (full && z.infamyDay != day) {
            if (z.infamyDay >= 0 && day > z.infamyDay) {
                long steps = Math.min(30, day - z.infamyDay);
                z.infamy = (int) Math.max(0, z.infamy - steps * dev.tcgshop.EconomyConfig.INSTANCE.infamyDecayPerDay);
                z.unattended = z.unattended >> (int) Math.min(8, steps);
                for (long k = 0; k < steps && z.charity != 0; k++) z.charity -= Integer.signum(z.charity) * Math.min(2, Math.abs(z.charity));
            }
            z.infamyDay = day;
        }
        Env env = scanEnvironment(world, z, half);
        // 0.9 events: blackout / extra light and damaged or improved decoration (what customers "see"). Counts for what
        // is shown (factors, flags, missions) and for the effective reputation (eventEnvRep), but the real reputation
        // settles with the shop as it is, without the event: a 5-minute blackout leaves no lasting effects.
        double rawLight = env.light;
        dev.tcgshop.events.Mods em = dev.tcgshop.events.EventManager.mods(z);
        if (env.known && (em.lightingOverride >= 0 || em.lightDelta != 0))
            env.light = Math.max(0, Math.min(15, (em.lightingOverride >= 0 ? Math.min(env.light, em.lightingOverride) : env.light) + em.lightDelta));

        // preview: keeps what only the full recompute sets (reputation target / ceiling, event)
        java.util.Map<String, Integer> keep = new java.util.HashMap<>();
        if (!full) for (String k : new String[]{"m_target", "m_ceiling", "event"}) if (z.factors.containsKey(k)) keep.put(k, z.factors.get(k));
        z.factors.clear();
        z.factors.putAll(keep);
        z.flags.clear();
        double avg = stocked == 0 ? 1.2 : ratioSum / stocked;
        int price = (int) Math.round(Math.max(-15, Math.min(15, (1.1 - avg) * 50)));
        int var = Math.min(15, variety.size());
        int disp = Math.min(10, displays * 2);
        // 1.1: the decoration factor follows the decoration liking (DecorScore: hard to raise, every decor object counts)
        int decorPct = DecorScore.pct(decorPts, z.size());
        int dec = Math.max(-10, Math.min(15, DecorScore.repFactor(decorPct) + em.decorDelta));
        int decBase = Math.max(-10, Math.min(15, DecorScore.repFactor(decorPct))); // without events (for the real reputation)
        int sec = (z.guard ? 8 : 0) + 2 * Math.max(0, z.guardCount() - 1) + Math.min(6, detectors * 2); // 1.1: +2 per extra guard
        int serv = (z.cashier ? 8 : 0) + (z.cashier2 ? 4 : 0);
        int sat = z.reviews.size() >= 3 ? (int) Math.round(Math.max(-15, Math.min(15, (z.avgStars() - 3) * 7)))
                : (int) Math.max(-15, Math.min(15, (z.customers - z.complaints * 2) / 5.0));
        int theft = -Math.min(15, Math.max(0, z.stolen - z.recovered) * 2);
        z.factors.put("price", price);
        z.factors.put("variety", var);
        z.factors.put("displays", disp);
        z.factors.put("decor", dec);
        z.factors.put("m_decor_pct", decorPct);
        z.factors.put("m_decor_pts", (int) Math.round(decorPts * 10));
        z.factors.put("m_statue", statueBest);
        z.factors.put("security", sec);
        z.factors.put("service", serv);
        z.factors.put("satisfaction", sat);
        z.factors.put("theft", theft);
        z.factors.put("buzz", buzz);
        // 1.1 (cleanliness update): smell, grime and droppings together (+5 spotless .. -20 filthy); replaces the old "smell" factor
        Cleanliness.prune(world, z);
        int cleanPct = Cleanliness.score(z);
        int smell = Cleanliness.repFactor(cleanPct);
        if (smell != 0) z.factors.put("clean", smell);
        z.factors.put("m_clean", cleanPct);

        // ---- three scores: Aesthetics, Value for money and Safety (0-100) -> total reputation with configurable weights
        int roof = 0, walls = 0, light = 0, lightBase = 0, doors = 0, hostiles = 0, animals = 0, enclosure = 0;
        if (env.known) {
            roof = (int) Math.round((env.roof - 0.5) * 16);
            walls = (int) Math.round((env.walls - 0.5) * 16);
            light = lightingFactor(env.light);
            lightBase = lightingFactor(rawLight);
            doors = env.doors > 0 ? 3 : 0;
            double enc = (env.roof + env.walls) / 2;
            enclosure = enc > 0.7 ? 8 : enc < 0.3 ? -8 : 0;
            z.factors.put("roof", roof);
            z.factors.put("walls", walls);
            z.factors.put("lighting", light);
            if (doors > 0) z.factors.put("doors", doors);
            if (env.roof < 0.3) z.flags.add("no_roof"); // 1.1: less strict (a few holes / skylights are fine)
            if (env.walls < 0.25) z.flags.add("no_walls");
            if (env.light < 6) z.flags.add("dark");
            else if (env.light >= 11 && env.roof >= 0.6) z.flags.add("bright");
        }
        hostiles = -Math.min(15, env.hostilesInside * 5 + env.hostilesNear * 2);
        if (hostiles < 0) { z.factors.put("hostiles", hostiles); z.flags.add("hostile"); }
        animals = (env.pets > 0 ? 2 : 0) + (env.pokemon > 0 && env.pokemon <= 4 ? 3 : env.pokemon > 6 ? -4 : 0) - (env.farm > 2 ? Math.min(8, env.farm) : 0);
        if (animals != 0) z.factors.put("animals", Math.max(-8, Math.min(4, animals)));
        if (env.farm > 2) z.flags.add("farm_animals");
        if (env.pets > 0) z.flags.add("pets");
        if (env.pokemon > 0) z.flags.add("pokemon");
        int infamy = -Math.min(25, z.infamy);
        if (infamy < 0) { z.factors.put("infamy", Math.max(-15, infamy)); if (z.infamy >= 10) z.flags.add("infamy"); }
        int unatt = -Math.min(8, z.unattended);
        if (unatt < 0) z.factors.put("unattended", unatt);
        int org = 0;
        if (env.slots > 0) org = (int) Math.round(10.0 * stocked / env.slots);
        int kinds = env.kinds;
        int aest = clamp(20 + roof + walls + light + dec + disp + org + doors + smell);
        int aestBase = clamp(20 + roof + walls + lightBase + decBase + disp + org + doors + smell);
        int value = clamp(30 + (int) Math.round(price * 1.4) + var + Math.min(15, stocked / 2) + (env.packs > 0 ? 6 : 0) + kinds * 2);
        int safetyRest = 55 + sec + theft + hostiles + Math.max(-8, Math.min(4, animals)) + enclosure + infamy;
        int safety = clamp(safetyRest + (env.known && env.light < 5 ? -8 : 0));
        int safetyBase = clamp(safetyRest + (env.known && rawLight < 5 ? -8 : 0));
        if (aest >= 75) z.flags.add("cozy");
        if (price >= 8) z.flags.add("cheap");
        else if (price <= -8) z.flags.add("pricey");
        if (displays > 0 && stocked < 3) z.flags.add("empty");
        var cfg = dev.tcgshop.EconomyConfig.INSTANCE;
        double wsum = cfg.repWeightAesthetics + cfg.repWeightValue + cfg.repWeightSafety;
        double base = (aest * cfg.repWeightAesthetics + value * cfg.repWeightValue + safety * cfg.repWeightSafety) / wsum;
        double baseNoEvent = (aestBase * cfg.repWeightAesthetics + value * cfg.repWeightValue + safetyBase * cfg.repWeightSafety) / wsum;
        int charity = Math.max(-10, Math.min(10, z.charity));
        if (charity != 0) z.factors.put("charity", charity);
        double rest = sat + buzz + serv * 0.6 + unatt + charity;
        int rep = (int) Math.round(base + rest);
        int repNoEvent = (int) Math.round(baseNoEvent + rest);
        // the real reputation only with the shop without events; what the event's lighting and decoration add / subtract
        // goes separately, to the effective reputation (effectiveRep), like Mods.repDelta
        z.eventEnvRep = rep - repNoEvent;
        if (full && !z.closed) z.settleReputation(repNoEvent); // 1.1: frozen while closed. Diminishing returns, ceiling from sales/stars and slow rise (ShopZones)
        z.factors.put("m_aesthetics", aest);
        z.factors.put("m_value", value);
        z.factors.put("m_safety", safety);
        z.displays = displays;
        z.decor = decor;
        z.detectors = detectors;
        z.buzz = buzz;
        // ---- missions (customer flow) and capacity
        ShopMissions.Snapshot snap = new ShopMissions.Snapshot();
        snap.stockedDisplays = stockedDisplays;
        snap.stocked = stocked;
        snap.variety = variety.size();
        snap.decor = decor;
        snap.price = price;
        snap.smell = z.smell;
        snap.hostilesInside = env.hostilesInside;
        snap.seats = seats;
        snap.playTables = playTables;
        snap.boosterBox = boosterBox;
        snap.register = register;
        snap.envKnown = env.known;
        snap.roof = env.roof;
        snap.walls = env.walls;
        snap.light = env.light;
        // 1.0 missions
        java.util.List<BlockPos> doorList = shopDoors(world, z);
        snap.packs = packSlots;
        snap.boxes = boxSlots;
        snap.slots = env.slots;
        snap.kinds = env.kinds;
        snap.posters = posters;
        snap.dolls = dolls;
        snap.pedestals = pedestals;
        snap.statues = statues;
        snap.showcases = showcases;
        snap.rgbDisplays = rgbDisplays;
        snap.premiumDisplays = premiumDisplays;
        snap.graded = graded;
        snap.gemGraded = gemGraded;
        snap.registers = registers;
        snap.doors = env.doors;
        snap.woodenDoors = doorList.size();
        snap.hostilesNear = env.hostilesNear;
        snap.pets = env.pets;
        snap.pokemon = env.pokemon;
        snap.inside = insideCount(world, z);
        snap.aesthetics = aest;
        snap.value = value;
        snap.safety = safety;
        snap.stockValue = stockValue;
        z.stockValue = stockValue; // 1.1: part of the owner's Ranking capital
        snap.topCard = topCard;
        snap.day = day;
        z.seats = seats;
        z.playTables = playTables;
        int before = z.missions;
        z.missions = ShopMissions.evaluate(world.getServer(), z, snap);
        if (before < 0) z.missionsEver |= z.missions; // first computation: what was already met isn't announced
        if (!full) {
            // preview (status window): new missions are announced on the next full recompute
            z.lastCalc = world.getGameTime();
            return;
        }
        dev.tcgshop.events.EventManager.onRecompute(world, z, register, doorList); // 0.9 events: conditions and goals
        dev.tcgshop.events.incident.IncidentModule.cleanupZone(world, z); // 0.9: sweep of orphaned incidents
        z.lastCalc = world.getGameTime();
        ShopZones.get(world).setDirty();
        ServerPlayer owner = world.getServer().getPlayerList().getPlayer(z.owner);
        if (owner != null) {
            if (z.reputation >= 50) CardAdvancements.grant(owner, "reputation_50");
            if (z.reputation >= 80) CardAdvancements.grant(owner, "reputation_80");
            if (z.reputation >= 100) CardAdvancements.grant(owner, "reputation_100");
            announceMissions(owner, z, before);
            ShopServer.syncZones(owner);
        }
    }

    // ------------------------------------------------------------------ customers

    private static void spawnCustomers(ServerLevel world, ShopZones.Zone z) {
        if (z.closed) return; // 1.1: closed shop: nobody comes (thieves and unique customers included)
        ServerPlayer owner = world.getServer().getPlayerList().getPlayer(z.owner);
        if (owner == null || owner.serverLevel() != world || owner.distanceToSqr(z.core.getCenter()) > 96 * 96) return;
        if (z.displays == 0) return;
        int present = nearCount(world, z);
        dev.tcgshop.events.Mods em = dev.tcgshop.events.EventManager.mods(z);
        int cap = ShopMissions.effectiveCapacity(z), full = Math.max(1, cap + ShopMissions.queueMax(z)); // 0.9: maxCustAdd is already included in the capacity
        // happy customers recommend it: each recent 5-star review brings a few more people
        long today = world.getDayTime() / 24000;
        int recs = 0;
        for (ShopZones.Review r : z.reviews) if (r.stars() >= 5 && today - r.day() <= 2) recs++;
        // flow: the more missions completed, the more people arrive (ShopMissions.flow)
        // 0.9 events: effective reputation (with the temporary modifier) and arrival multiplier
        // 1.0: the tier's arrival multiplier (the Paper stall gets very few people, ShopTier.spawnMult)
        double chance = (0.2 + effectiveRep(z) / 180.0 + Math.min(0.15, recs * 0.03)) * DEMAND * ShopMissions.flow(z)
                * em.spawnMult * z.tierInfo().spawnMult
                * (1 + DecorScore.zoneStatue(z) * dev.tcgshop.EconomyConfig.INSTANCE.statueSpawnBonusPerTier) // 1.1: luxury statues attract people
                * Cleanliness.spawnMult(Cleanliness.score(z)); // 1.1: fewer people come into a dirty shop
        if (world.random.nextDouble() > chance) return;
        if (present >= full) {
            // someone was coming, but the shop and the queue are full: they turn around (counts toward suggesting an upgrade)
            ShopMissions.addOverflow(z, today);
            ShopZones.get(world).setDirty();
            return;
        }
        // 1.0 beta 3: now and then one of the 10 unique customers walks in instead (FixedCustomers.roll)
        int fixed = dev.tcgshop.entity.FixedCustomers.roll(world, z);
        if (fixed >= 0 && spawnAt(world, z, dev.tcgshop.entity.FixedCustomers.def(fixed).type(), null, fixed) != null) return;
        doSpawn(world, z, rollType(world, z));
    }

    /**
     * API for other systems (e.g. the phone's "call a collector"): brings a customer of the given type
     * (CustomerEntity.Type name, e.g. "WHALE") without rolling the chance, but respecting the capacity and the queue:
     * if the shop is full, they'll queue outside; if the queue is full too (or the shop isn't loaded), returns false.
     */
    public static boolean forceSpawnCustomer(ServerLevel world, ShopZones.Zone z, String typeName) {
        if (world == null || z == null || z.closed) return false; // 1.1: callers tell the player when the shop is closed
        CustomerEntity.Type type;
        try {
            type = CustomerEntity.Type.valueOf(typeName == null ? "REGULAR" : typeName.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (!zoneLoaded(world, z)) return false;
        if (nearCount(world, z) >= ShopMissions.effectiveCapacity(z) + ShopMissions.queueMax(z)) return false;
        return doSpawn(world, z, type);
    }

    /**
     * 0.9 events: special customer from an event (VisitorSpec), via the same path as the others (arrives walking,
     * respects the capacity and the queue). null if the shop isn't loaded, is full with a full queue, or there's no spot.
     */
    @org.jetbrains.annotations.Nullable
    public static CustomerEntity forceSpawnVisitor(ServerLevel world, ShopZones.Zone z, dev.tcgshop.events.VisitorSpec spec) {
        if (world == null || z == null || spec == null || z.closed) return null; // 1.1: closed shop
        if (!zoneLoaded(world, z)) return null;
        int full = Math.max(1, ShopMissions.effectiveCapacity(z) + ShopMissions.queueMax(z));
        if (nearCount(world, z) >= full) return null;
        return spawnAt(world, z, spec.type, spec);
    }

    private static boolean doSpawn(ServerLevel world, ShopZones.Zone z, CustomerEntity.Type type) {
        return spawnAt(world, z, type, null) != null;
    }

    @org.jetbrains.annotations.Nullable
    private static CustomerEntity spawnAt(ServerLevel world, ShopZones.Zone z, CustomerEntity.Type type, @org.jetbrains.annotations.Nullable dev.tcgshop.events.VisitorSpec spec) {
        return spawnAt(world, z, type, spec, -1);
    }

    /** fixedId: one of the 10 unique customers (1.0 beta 3; -1 = an ordinary customer, who may be a returning regular). */
    @org.jetbrains.annotations.Nullable
    private static CustomerEntity spawnAt(ServerLevel world, ShopZones.Zone z, CustomerEntity.Type type, @org.jetbrains.annotations.Nullable dev.tcgshop.events.VisitorSpec spec,
                                          int fixedId) {
        // they arrive walking from far away (they don't spawn right next to the shop), enter through a door and leave the way they came
        BlockPos door = null;
        Vec3 doorOutside = null, pos = null;
        java.util.List<BlockPos> doors = new java.util.ArrayList<>(shopDoors(world, z));
        // 0.9 events: blocked doors; if all of them are, nobody comes in that way today
        dev.tcgshop.events.Mods em = dev.tcgshop.events.EventManager.mods(z);
        if (!doors.isEmpty() && em.hasClosedDoors()) {
            doors.removeIf(em::isDoorClosed);
            if (doors.isEmpty()) return null;
        }
        if (!doors.isEmpty()) {
            door = doors.get(world.random.nextInt(doors.size()));
            net.minecraft.core.Direction out = outward(world, z, door);
            doorOutside = Vec3.atBottomCenterOf(door.relative(out, 2));
            pos = farSpot(world, z, door, out);
        }
        if (pos == null) pos = edgeSpot(world, z, 16 + world.random.nextInt(10));
        if (pos == null) pos = edgeSpot(world, z, 6);
        if (pos == null) return null;
        Vec3 exit = pos;
        ShopZones.Regular regular = null;
        if (spec != null) regular = spec.regular;
        else if (fixedId < 0 && type != CustomerEntity.Type.THIEF && !z.regulars.isEmpty() && world.random.nextFloat() < 0.25f)
            regular = z.regulars.get(world.random.nextInt(z.regulars.size()));
        return CustomerEntity.spawn(world, z, type, pos, exit, regular, door, doorOutside, spec, fixedId);
    }

    /**
     * 1.1: where an NPC visiting the shop on foot appears (same spots as customers: out of a door, or past the zone edge),
     * or null if there is no free spot. Used by the Wandering Collector's shop visits (TcgEntities).
     */
    @org.jetbrains.annotations.Nullable
    public static Vec3 arrivalSpot(ServerLevel world, ShopZones.Zone z) {
        java.util.List<BlockPos> doors = new java.util.ArrayList<>(shopDoors(world, z));
        dev.tcgshop.events.Mods em = dev.tcgshop.events.EventManager.mods(z);
        if (em.hasClosedDoors()) doors.removeIf(em::isDoorClosed);
        Vec3 pos = null;
        if (!doors.isEmpty()) {
            BlockPos door = doors.get(world.random.nextInt(doors.size()));
            pos = farSpot(world, z, door, outward(world, z, door));
        }
        if (pos == null) pos = edgeSpot(world, z, 16 + world.random.nextInt(10));
        if (pos == null) pos = edgeSpot(world, z, 6);
        return pos;
    }

    // ------------------------------------------------------------------ capacity and queue

    /** Responses of {@link #queueGate}. */
    public static final int GATE_GO = 0, GATE_WAIT = 1, GATE_GIVE_UP = 2;
    /**
     * Gets tired of waiting because an event blocks the door (not because the shop is full): no
     * "shop_full" review, no mood drop for capacity and not counted as overflow.
     */
    public static final int GATE_BLOCKED_GIVE_UP = 3;
    /** Mood lost by whoever gets tired of waiting outside (starts at 3: ends at 2 stars). */
    public static final int FULL_MOOD_PENALTY = 1;
    /** Maximum time waited outside (ticks) before leaving. */
    public static final int QUEUE_TICKS = 20 * 25;

    private record Wait(java.util.UUID zone, long since) {}

    /** Customers waiting outside (entity uuid -> zone and since when). */
    private static final java.util.Map<java.util.UUID, Wait> QUEUE = new java.util.HashMap<>();
    /** Customers just let through who haven't crossed the door yet (they hold their spot for a while). */
    private static final java.util.Map<java.util.UUID, Wait> PASS = new java.util.HashMap<>();
    /** Count of customers inside per zone, cached for a few ticks (zone -> {tick, n}). */
    private static final java.util.Map<java.util.UUID, long[]> INSIDE = new java.util.HashMap<>();

    /** Customers in the shop or arriving (zone + 24 blocks around), as before 0.9. */
    private static int nearCount(ServerLevel world, ShopZones.Zone z) {
        return world.getEntitiesOfClass(CustomerEntity.class, z.box().inflate(24), c -> c.isAlive()).size();
    }

    /** Customers inside the shop right now (cached for 10 ticks). */
    public static int insideCount(ServerLevel world, ShopZones.Zone z) {
        long now = world.getGameTime();
        long[] c = INSIDE.get(z.id);
        if (c != null && now - c[0] >= 0 && now - c[0] < 10) return (int) c[1];
        int n = world.getEntitiesOfClass(CustomerEntity.class, z.box(), x -> x.isAlive() && z.contains(x.blockPosition())).size();
        INSIDE.put(z.id, new long[]{now, n});
        return n;
    }

    /** How many are waiting outside in this shop's queue. */
    public static int queuedCount(ShopZones.Zone z) {
        int n = 0;
        for (Wait w : QUEUE.values()) if (w.zone().equals(z.id) && queueFresh(w, lastTick)) n++;
        return n;
    }

    /** Last world time seen in the tick (to discard old queue entries without having the world at hand). */
    private static long lastTick;

    /** A queue entry is still alive if it hasn't been there more than {@link #QUEUE_TICKS} (after that the customer would have left). */
    private static boolean queueFresh(Wait w, long now) {
        return now - w.since() >= 0 && now - w.since() <= QUEUE_TICKS;
    }

    /** The customer has just crossed into the shop (CustomerEntity): releases their queue spot and their pass reservation. */
    public static void onEntered(java.util.UUID id) {
        QUEUE.remove(id);
        PASS.remove(id);
    }

    /** 1.1: the shop closed: drops every queue / pass reservation held for it. */
    public static void forgetZone(java.util.UUID zoneId) {
        QUEUE.values().removeIf(w -> w.zone().equals(zoneId));
        PASS.values().removeIf(w -> w.zone().equals(zoneId));
        INSIDE.remove(zoneId);
    }

    /** The customer leaves, despawns or dies (CustomerEntity.leave / remove / reload): out of the queue and the pass. */
    public static void forget(java.util.UUID id) {
        QUEUE.remove(id);
        PASS.remove(id);
    }

    private static int passCount(ShopZones.Zone z, long now) {
        int n = 0;
        for (Wait w : PASS.values()) if (w.zone().equals(z.id) && now - w.since() >= 0 && now - w.since() < 200) n++;
        return n;
    }

    /**
     * Shop door for a customer who hasn't entered yet (called by CustomerEntity in its ENTER state).
     * If there's room, they enter (GATE_GO). If it's full, they join the queue outside, next to the door, facing it
     * (GATE_WAIT). If they wait more than {@link #QUEUE_TICKS}, they leave (GATE_GIVE_UP: the customer's mood drops and they leave the
     * "shop_full" review); counts as the day's overflow to suggest upgrading the shop.
     */
    public static int queueGate(CustomerEntity c, ServerLevel world, ShopZones.Zone z,
                                @org.jetbrains.annotations.Nullable BlockPos door, @org.jetbrains.annotations.Nullable Vec3 doorOutside) {
        java.util.UUID id = c.getUUID();
        long now = world.getGameTime();
        if (z.contains(c.blockPosition())) { QUEUE.remove(id); PASS.remove(id); return GATE_GO; }
        boolean queued = QUEUE.containsKey(id);
        // still walking in from far away: let them continue (the queue starts at the door)
        if (!queued && doorOutside != null && c.distanceToSqr(doorOutside) > 4 * 4) return GATE_GO;
        Wait pass = PASS.get(id);
        if (pass != null && now - pass.since() >= 0 && now - pass.since() < 200) {
            // no door (passes from where it spawns, 16-26 blocks away): renews the reservation while walking in
            if (doorOutside == null) PASS.put(id, new Wait(z.id, now));
            return GATE_GO;
        }
        int cap = ShopMissions.effectiveCapacity(z); // 0.9: with the events' capacity modifier
        // 0.9 events: if their door is blocked, try another open door; if there's none, wait outside even if there's room
        dev.tcgshop.events.Mods em = dev.tcgshop.events.EventManager.mods(z);
        boolean blocked = door != null && em.isDoorClosed(door);
        if (blocked) {
            for (BlockPos alt : z.events.doors) {
                if (alt.equals(door) || em.isDoorClosed(alt) || !world.getBlockState(alt).is(net.minecraft.tags.BlockTags.WOODEN_DOORS)) continue;
                QUEUE.remove(id);
                c.redirectDoor(alt, Vec3.atBottomCenterOf(alt.relative(outward(world, z, alt), 2)));
                return GATE_WAIT; // the next step already heads to the new door
            }
        }
        if (!blocked && insideCount(world, z) + passCount(z, now) < cap) {
            QUEUE.remove(id);
            PASS.put(id, new Wait(z.id, now));
            return GATE_GO;
        }
        if (!queued) {
            QUEUE.put(id, new Wait(z.id, now));
            if (world.random.nextFloat() < 0.45f) say(c, "shop_full_wait");
            ServerPlayer owner = world.getServer().getPlayerList().getPlayer(z.owner);
            if (owner != null && owner.distanceToSqr(c) < 96 * 96)
                owner.displayClientMessage(net.minecraft.network.chat.Component.translatable(blocked ? "tcgshop.event.door_blocked_queue" : "tcgshop.shop.full_queue", cap).withStyle(net.minecraft.ChatFormatting.YELLOW), true);
        }
        Wait w = QUEUE.get(id);
        long waited = now - w.since();
        if (waited > QUEUE_TICKS || waited < 0) {
            QUEUE.remove(id);
            if (blocked) {
                // 0.9 events: leaves because the door is blocked, not because of capacity (no overflow and no "full" review)
                ServerPlayer owner = world.getServer().getPlayerList().getPlayer(z.owner);
                if (owner != null && owner.distanceToSqr(c) < 96 * 96)
                    owner.displayClientMessage(net.minecraft.network.chat.Component.translatable("tcgshop.event.door_blocked_left", c.getDisplayName()).withStyle(net.minecraft.ChatFormatting.GOLD), true);
                return GATE_BLOCKED_GIVE_UP;
            }
            ShopMissions.addOverflow(z, world.getDayTime() / 24000);
            ShopZones.get(world).setDirty();
            if (world.random.nextFloat() < 0.6f) say(c, "shop_full_leave");
            ServerPlayer owner = world.getServer().getPlayerList().getPlayer(z.owner);
            if (owner != null && owner.distanceToSqr(c) < 96 * 96)
                owner.displayClientMessage(net.minecraft.network.chat.Component.translatable("tcgshop.shop.full_left", c.getDisplayName(), cap).withStyle(net.minecraft.ChatFormatting.GOLD), true);
            return GATE_GIVE_UP;
        }
        // spot in the line: in a straight line outward from the door, in order of arrival
        Vec3 spot = c.position();
        if (doorOutside != null) {
            int index = 0;
            for (java.util.Map.Entry<java.util.UUID, Wait> e : QUEUE.entrySet()) {
                Wait o = e.getValue();
                if (!e.getKey().equals(id) && o.zone().equals(z.id) && o.since() < w.since() && queueFresh(o, now)) index++;
            }
            Vec3 dir = door == null ? Vec3.ZERO : doorOutside.subtract(Vec3.atBottomCenterOf(door)).multiply(1, 0, 1);
            dir = dir.lengthSqr() < 1e-4 ? Vec3.ZERO : dir.normalize();
            spot = doorOutside.add(dir.scale(index * 1.3));
        }
        if (c.distanceToSqr(spot) > 1.1 * 1.1) {
            if (c.getNavigation().isDone() || c.tickCount % 40 == 0) c.getNavigation().moveTo(spot.x, spot.y, spot.z, 0.7);
        } else {
            c.getNavigation().stop();
            if (door != null) c.getLookControl().setLookAt(door.getX() + 0.5, door.getY() + 1.5, door.getZ() + 0.5);
        }
        return GATE_WAIT;
    }

    private static void say(CustomerEntity c, String cat) {
        dev.tcgshop.entity.NpcLines.speak(c, net.minecraft.network.chat.Component.translatable("tcgshop.npc." + cat + "." + c.getRandom().nextInt(8)));
    }

    private static void cleanQueues(long now) {
        QUEUE.values().removeIf(w -> now - w.since() > QUEUE_TICKS * 3L || now < w.since());
        PASS.values().removeIf(w -> now - w.since() > 400 || now < w.since());
        INSIDE.values().removeIf(c -> now - c[0] > 400 || now < c[0]);
    }

    /** Words (name pieces separated by "_") that make a block a seat. */
    private static final Set<String> SEAT_WORDS = Set.of("chair", "armchair", "stool", "sofa", "couch", "bench", "seat", "loveseat");

    /**
     * Furniture seats (CobbleFurnies and similar): by block name, word by word (so
     * "botanist_workbench" or "toadstool" don't count; "oak_bench" or "red_sofa_left" do).
     */
    private static boolean isSeat(String path) {
        int slash = path.lastIndexOf('/');
        for (String w : path.substring(slash + 1).split("_")) {
            w = w.replaceAll("\\d+$", ""); // chair2 -> chair
            if (SEAT_WORDS.contains(w) || (w.endsWith("s") && SEAT_WORDS.contains(w.substring(0, w.length() - 1)))) return true;
        }
        return false;
    }

    /** Notifies the owner when they complete a new mission. */
    private static void announceMissions(ServerPlayer owner, ShopZones.Zone z, int before) {
        z.missionsEver |= z.missions; // 0.9 mask (kept for older builds)
        // 1.0: the full set lives in the MissionLedger (only the first time each mission is completed is announced)
        net.minecraft.server.MinecraftServer server = owner.getServer();
        if (server == null) return;
        MissionLedger.Data d = MissionLedger.of(server, z);
        java.util.BitSet gained = (java.util.BitSet) d.done.clone();
        gained.andNot(d.ever);
        int everBefore = d.ever.cardinality();
        d.ever.or(d.done);
        MissionLedger.dirty();
        if (before < 0 || gained.isEmpty()) return; // first computation (old worlds / new shop): no bulk notifications
        int completed = ShopMissions.completedCount(z), n = 0, shown = 0;
        String flow = (int) Math.round(ShopMissions.flow(z) * 100) + "%";
        for (int i = gained.nextSetBit(0); i >= 0 && i < ShopMissions.COUNT; i = gained.nextSetBit(i + 1)) {
            n++;
            if (shown >= 3) continue; // several at once: the rest go in one summary line
            shown++;
            ShopMissions.Mission m = ShopMissions.Mission.values()[i];
            owner.displayClientMessage(net.minecraft.network.chat.Component.translatable("tcgshop.mission.completed",
                    net.minecraft.network.chat.Component.translatable("tcgshop.mission." + m.key),
                    completed, ShopMissions.COUNT, flow).withStyle(net.minecraft.ChatFormatting.GREEN), false);
        }
        if (n > shown)
            owner.displayClientMessage(net.minecraft.network.chat.Component.translatable("tcgshop.mission.completed_more", n - shown).withStyle(net.minecraft.ChatFormatting.GREEN), false);
        // every 10 missions ever completed: a phone message with the luck they give
        int everAfter = d.ever.cardinality();
        if (everAfter / 10 > everBefore / 10)
            dev.tcgshop.phone.PhoneMessages.send(server, z.owner, "tcgshop.msg.sender.shop",
                    net.minecraft.network.chat.Component.translatable("tcgshop.mission.milestone_msg", completed, ShopMissions.COUNT,
                            Math.round(ShopMissions.acceptBonus(z) * 1000) / 10.0 + "%", Math.round(ShopMissions.tipBonus(z) * 1000) / 10.0 + "%"), false);
        owner.serverLevel().playSound(null, owner.blockPosition(), net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP,
                net.minecraft.sounds.SoundSource.PLAYERS, 0.5f, 1.4f);
    }

    /**
     * Type of the arriving customer: weight table (0.9: no longer has the previous version's offset, in which the
     * picky one came out 16% instead of 15%). Events multiply each weight (Mods.typeWeight).
     */
    private static CustomerEntity.Type rollType(ServerLevel world, ShopZones.Zone z) {
        dev.tcgshop.events.Mods em = dev.tcgshop.events.EventManager.mods(z);
        double whale = 0.02 + effectiveRep(z) / 2500.0; // 2% .. 6%
        double thief = thiefWeight(world.getServer(), z);
        CustomerEntity.Type[] types = {CustomerEntity.Type.WHALE, CustomerEntity.Type.THIEF, CustomerEntity.Type.SELLER, CustomerEntity.Type.TRADER,
                CustomerEntity.Type.HAGGLER, CustomerEntity.Type.PICKY, CustomerEntity.Type.REGULAR};
        double[] base = {whale, thief, 0.10, 0.07, 0.09, 0.15, 0};
        double rest = 1;
        for (int i = 0; i < 6; i++) rest -= base[i];
        base[6] = Math.max(0.05, rest);
        double sum = 0;
        double[] w = new double[types.length];
        for (int i = 0; i < types.length; i++) sum += (w[i] = base[i] * em.typeWeight(types[i]));
        double r = world.random.nextDouble() * sum;
        for (int i = 0; i < types.length; i++) if ((r -= w[i]) < 0) return types[i];
        return CustomerEntity.Type.REGULAR;
    }

    /**
     * Spawn weight of a thief among arriving customers (before events). 1.0 beta 3: a guard with upgraded detection scares
     * a few more thieves away (never below 2 %). 1.1: every extra guard scares one more point away, and the reputation
     * scales it (EconomyConfig.theftRepInfluence): a shop with a bad name attracts many more thieves, a famous one fewer.
     */
    public static double thiefWeight(net.minecraft.server.MinecraftServer server, ShopZones.Zone z) {
        int det = z.guard && server != null ? GuardUpgrades.level(server, z.id, GuardUpgrades.Stat.DETECTION) : 0;
        int extra = Math.max(0, z.guardCount() - 1);
        double floor = det > 0 || extra > 0 ? 0.02 : 0.03;
        double thief = Math.max(floor, 0.1 - (z.guard ? 0.03 : 0) - extra * 0.01 - z.detectors * 0.01 - det * 0.005);
        double infl = Math.max(0, Math.min(0.9, dev.tcgshop.EconomyConfig.INSTANCE.theftRepInfluence));
        int rep = Math.max(0, Math.min(100, z.reputation));
        thief *= 1 + infl * (50 - rep) / 50.0;
        thief = Math.max(0.01, thief);
        return thief + TierDifficulty.thiefWeightAdd(z); // 1.1: higher tiers attract a few more thieves (up to +4 points at Netherite)
    }

    /** 1.1: share (0-1) of arriving customers who are thieves, before events (the goggles' theft risk bar). */
    public static double theftChance(net.minecraft.server.MinecraftServer server, ShopZones.Zone z) {
        double whale = 0.02 + Math.max(0, Math.min(100, z.reputation)) / 2500.0;
        double thief = thiefWeight(server, z);
        double rest = Math.max(0.05, 1 - (whale + thief + 0.10 + 0.07 + 0.09 + 0.15));
        return thief / (whale + thief + 0.10 + 0.07 + 0.09 + 0.15 + rest);
    }

    /** A walkable point on the zone's edge, at the shop's floor height. */
    /** The shop's wooden doors (lower half), inside the zone and at floor height. */
    public static java.util.List<BlockPos> shopDoors(ServerLevel world, ShopZones.Zone z) {
        java.util.List<BlockPos> out = new java.util.ArrayList<>();
        int half = z.size() / 2;
        for (BlockPos p : BlockPos.betweenClosed(z.core.getX() - half, z.core.getY() - 2, z.core.getZ() - half, z.core.getX() + half, z.core.getY() + 3, z.core.getZ() + half)) {
            BlockState s = world.getBlockState(p);
            if (s.is(net.minecraft.tags.BlockTags.WOODEN_DOORS) && s.getValue(net.minecraft.world.level.block.DoorBlock.HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER
                    && WideDoorBlock.isMainPart(s)) // 1.1: a 3-wide door is one door (its lower center part)
                out.add(p.immutable());
            if (out.size() >= 8) break;
        }
        return out;
    }

    /** Outside side of a door: the one facing the sky (or, if unknown, the one farthest from the shop's center). */
    static net.minecraft.core.Direction outward(ServerLevel world, ShopZones.Zone z, BlockPos door) {
        net.minecraft.core.Direction f = world.getBlockState(door).getValue(net.minecraft.world.level.block.DoorBlock.FACING);
        boolean a = world.canSeeSky(door.relative(f).above()), b = world.canSeeSky(door.relative(f.getOpposite()).above());
        if (a != b) return a ? f : f.getOpposite();
        double da = door.relative(f).distSqr(z.core), db = door.relative(f.getOpposite()).distSqr(z.core);
        return da >= db ? f : f.getOpposite();
    }

    /** Faraway spot (18-28 blocks) on the outside of the door, on solid ground. */
    @org.jetbrains.annotations.Nullable
    private static Vec3 farSpot(ServerLevel world, ShopZones.Zone z, BlockPos door, net.minecraft.core.Direction out) {
        net.minecraft.core.Direction side = out.getClockWise();
        for (int i = 0; i < 10; i++) {
            int d = 18 + world.random.nextInt(11), lat = world.random.nextInt(13) - 6;
            BlockPos c = door.relative(out, d).relative(side, lat);
            if (z.contains(c)) continue;
            if (!world.hasChunkAt(c)) continue;
            int y = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, c.getX(), c.getZ());
            if (Math.abs(y - door.getY()) > 6) continue;
            BlockPos g = new BlockPos(c.getX(), y, c.getZ());
            if (ShopZones.isShopRoof(world, g)) continue; // 1.1: never on a shop's roof
            if (world.getBlockState(g.below()).isRedstoneConductor(world, g.below()) && world.isEmptyBlock(g) && world.isEmptyBlock(g.above()) && world.getFluidState(g.below()).isEmpty())
                return new Vec3(g.getX() + 0.5, g.getY(), g.getZ() + 0.5);
        }
        return null;
    }

    private static Vec3 edgeSpot(ServerLevel world, ShopZones.Zone z, int outside) {
        int half = z.size() / 2 - 1 + outside;
        for (int i = 0; i < 12; i++) {
            int side = world.random.nextInt(4), off = world.random.nextInt(half * 2 + 1) - half;
            int x = z.core.getX() + (side == 0 ? -half : side == 1 ? half : off);
            int zz = z.core.getZ() + (side == 2 ? -half : side == 3 ? half : off);
            // 1.1: closest to the shop's floor first (it used to try 3 blocks up first: the top of a wall or the roof)
            for (int dy : new int[]{0, -1, 1, -2, 2, 3}) {
                BlockPos p = new BlockPos(x, z.core.getY() + dy, zz);
                if (world.getBlockState(p.below()).isRedstoneConductor(world, p.below()) && world.isEmptyBlock(p) && world.isEmptyBlock(p.above())
                        && !ShopZones.isShopRoof(world, p))
                    return new Vec3(x + 0.5, p.getY(), zz + 0.5);
            }
            int y = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, zz);
            if (Math.abs(y - z.core.getY()) <= 3 && !ShopZones.isShopRoof(world, new BlockPos(x, y, zz))) return new Vec3(x + 0.5, y, zz + 0.5);
        }
        return null;
    }

    /** 1.1: does this monster make the shop unsafe? A Scalper only when it is hostile or fighting (like CustomerEntity.hostileNear). */
    static boolean threat(net.minecraft.world.entity.Entity e) {
        if (!(e instanceof net.minecraft.world.entity.monster.Monster m)) return false;
        if (m instanceof dev.tcgshop.entity.ScalperEntity sc)
            return sc.getTarget() != null || sc.mode() == dev.tcgshop.entity.ScalperEntity.Mode.HOSTILE || sc.mode() == dev.tcgshop.entity.ScalperEntity.Mode.BRAWL;
        return true;
    }

    /** Is the chunk section holding this position empty (only air) or not loaded? */
    private static boolean emptySection(ServerLevel world, BlockPos p) {
        LevelChunk ch = world.getChunkSource().getChunkNow(p.getX() >> 4, p.getZ() >> 4);
        if (ch == null) return true;
        int idx = ch.getSectionIndex(p.getY());
        if (idx < 0 || idx >= ch.getSectionsCount()) return true;
        return ch.getSection(idx).hasOnlyAir();
    }

    private static int clamp(int v) { return Math.max(0, Math.min(100, v)); }

    /**
     * 1.1: how enclosed a floor spot is (0-1). Less strict than before: per side, 6 rays (2 heights x 3 lanes) and 4 hits
     * already count as a full wall, so windows, holes and glass panes are fine.
     */
    static double wallScore(net.minecraft.world.level.Level world, BlockPos foot, int reach) {
        double sides = 0;
        for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            net.minecraft.core.Direction lat = dir.getClockWise();
            int hits = 0;
            for (int dy = 0; dy <= 1; dy++)
                for (int l = -1; l <= 1; l++)
                    if (wallRay(world, foot.above(dy).relative(lat, l), dir, reach)) hits++;
            sides += Math.min(1.0, hits / 4.0);
        }
        return sides / 4.0;
    }

    /** 1.1: does a horizontal ray hit a wall-like block (solid, glass, panes, bars, fences, doors) within reach? */
    private static boolean wallRay(net.minecraft.world.level.Level world, BlockPos from, net.minecraft.core.Direction dir, int reach) {
        for (int k = 1; k <= reach; k++) {
            BlockPos q = from.relative(dir, k);
            BlockState s = world.getBlockState(q);
            if (s.isAir() || s.canBeReplaced()) continue;
            // (not just anything with a collision box: displays, counters and furniture inside the shop are not walls)
            if (s.canOcclude() || s.is(net.minecraft.tags.BlockTags.DOORS) || s.is(net.minecraft.tags.BlockTags.IMPERMEABLE)
                    || s.is(net.minecraft.tags.BlockTags.WALLS) || s.is(net.minecraft.tags.BlockTags.FENCES)
                    || s.is(net.minecraft.tags.BlockTags.FENCE_GATES)
                    || s.getBlock() instanceof net.minecraft.world.level.block.IronBarsBlock // glass panes and iron bars
                    || s.getBlock() instanceof net.minecraft.world.level.block.FenceBlock) return true;
        }
        return false;
    }

    /** What a customer sees of the shop and its surroundings. */
    static final class Env {
        boolean known;
        double roof, walls, light;
        int doors, hostilesInside, hostilesNear, farm, pets, pokemon, slots, packs, kinds, wallSamples;
    }

    /**
     * Looks at the shop as a customer would: only the area with furniture (a large plot with the shop in a corner
     * isn't penalized). Roof and walls via "rays" from the floor, block light, doors, nearby monsters,
     * farm animals, pets and Pokémon inside.
     */
    private static Env scanEnvironment(ServerLevel world, ShopZones.Zone z, int half) {
        Env e = new Env();
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        boolean cards = false, packs = false, boxes = false, dolls = false;
        int cx0 = (z.core.getX() - half) >> 4, cx1 = (z.core.getX() + half) >> 4, cz0 = (z.core.getZ() - half) >> 4, cz1 = (z.core.getZ() + half) >> 4;
        for (int cx = cx0; cx <= cx1; cx++)
            for (int cz = cz0; cz <= cz1; cz++) {
                LevelChunk ch = world.getChunkSource().getChunkNow(cx, cz);
                if (ch == null) continue;
                for (BlockEntity be : ch.getBlockEntities().values()) {
                    BlockPos p = be.getBlockPos();
                    if (!z.contains(p)) continue;
                    boolean shopThing = be instanceof ShopDisplayBlockEntity || be instanceof DecorBlocks.DecorEntity;
                    if (!shopThing) continue;
                    minX = Math.min(minX, p.getX()); maxX = Math.max(maxX, p.getX());
                    minZ = Math.min(minZ, p.getZ()); maxZ = Math.max(maxZ, p.getZ());
                    if (be instanceof ShopDisplayBlockEntity d && !d.isHome()) {
                        e.slots += d.getContainerSize();
                        for (int i = 0; i < d.getContainerSize(); i++) {
                            ItemStack st = d.getItem(i);
                            if (st.isEmpty()) continue;
                            if (dev.masterball.api.card.CardInfo.isCard(st)) cards = true;
                            else if (dev.masterball.api.card.CardInfo.isPack(st)) { packs = true; e.packs++; }
                            else if (st.is(TcgBlocks.BOOSTER_BOX_ITEM)) boxes = true;
                            else if (ShopServer.isPokedoll(st)) dolls = true;
                        }
                    }
                }
            }
        e.kinds = (cards ? 1 : 0) + (packs ? 1 : 0) + (boxes ? 1 : 0) + (dolls ? 1 : 0);
        // monsters and animals (even if there's no furniture yet)
        net.minecraft.world.phys.AABB zone = z.box();
        for (net.minecraft.world.entity.Entity ent : world.getEntities(null, zone.inflate(16))) {
            boolean inside = zone.contains(ent.position());
            if (ent instanceof net.minecraft.world.entity.monster.Monster) {
                if (!threat(ent)) continue; // 1.1: a peaceful Scalper (e.g. a shop visitor making an offer)
                if (inside) e.hostilesInside++; else e.hostilesNear++;
            } else if (!inside) continue;
            else if (ent instanceof net.minecraft.world.entity.TamableAnimal t && t.isTame()) e.pets++;
            else if (ent instanceof net.minecraft.world.entity.animal.Animal) e.farm++;
            else if (BuiltInRegistries.ENTITY_TYPE.getKey(ent.getType()).getNamespace().equals("cobblemon")
                    && !ent.getTags().contains(dev.tcgshop.events.EventManager.EVENT_TAG)) e.pokemon++; // 0.9: event Pokémon don't count
        }
        if (minX == Integer.MAX_VALUE) return e; // no furniture: the building isn't judged
        e.known = true;
        minX -= 2; minZ -= 2; maxX += 2; maxZ += 2;
        int fy = z.core.getY();
        int w = maxX - minX, d = maxZ - minZ, step = Math.max(1, Math.max(w, d) / 10);
        int n = 0, covered = 0, lightSum = 0;
        double wallSum = 0;
        int reach = Math.max(6, Math.max(w, d) + 2);
        for (int x = minX; x <= maxX; x += step)
            for (int zz = minZ; zz <= maxZ; zz += step) {
                BlockPos foot = new BlockPos(x, fy, zz);
                if (!world.getBlockState(foot).isAir() && world.getBlockState(foot).isSolidRender(world, foot)) continue; // inside a wall
                n++;
                // 1.1: less strict roof: covered if this spot or most of its neighbours are (a skylight or a few holes are fine)
                boolean roofed = !world.canSeeSky(foot.above());
                if (!roofed) {
                    int near = 0;
                    for (int ox = -1; ox <= 1; ox++)
                        for (int oz = -1; oz <= 1; oz++)
                            if ((ox != 0 || oz != 0) && !world.canSeeSky(foot.offset(ox, 1, oz))) near++;
                    roofed = near >= 6;
                }
                if (roofed) covered++;
                int bl = world.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, foot);
                lightSum += roofed ? bl : Math.max(bl, 12);
                e.wallSamples++;
                wallSum += wallScore(world, foot, reach);
            }
        if (n == 0) { e.known = false; return e; }
        e.roof = covered / (double) n;
        e.walls = wallSum / n;
        e.light = lightSum / (double) n;
        for (BlockPos q : BlockPos.betweenClosed(minX - 2, fy, minZ - 2, maxX + 2, fy + 1, maxZ + 2))
            if (world.getBlockState(q).is(net.minecraft.tags.BlockTags.DOORS) || world.getBlockState(q).is(net.minecraft.tags.BlockTags.FENCE_GATES)) e.doors++;
        return e;
    }
}

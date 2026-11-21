package dev.tcgshop.shop;

import dev.tcgshop.advancement.CardAdvancements;
import dev.tcgshop.entity.CustomerEntity;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.WorldChunk;

import java.util.HashSet;
import java.util.Set;

/**
 * Vida de las tiendas: reputación, aparición de clientes y sueldos.
 *
 * Reputación (0-100) = base 30 + precios + variedad + expositores + decoración (pósteres, mesas, pedestales y
 * peluches de Pokeblocks) + seguridad (guardia, detectores) + servicio (cajero) + satisfacción (ventas vs quejas)
 * - robos + "rumor del día" (evento aleatorio diario). Más reputación = más clientes y más coleccionistas.
 */
public final class ShopLife {
    /** Multiplicador de llegada de clientes (EconomyConfig.customerDemand). */
    public static double DEMAND = 1.0;
    private ShopLife() {}

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(ShopLife::tick);
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DEATH.register(ShopLife::onNpcDeath);
    }

    /**
     * Matar a un NPC de la tienda (clientes, Mayorista, comerciante, empleados) cuesta reputación en todas tus tiendas.
     * Sólo cuenta si la muerte es tuya (golpe, flecha, etc.); ladrones y revendedores no cuentan.
     */
    private static void onNpcDeath(net.minecraft.entity.LivingEntity entity, net.minecraft.entity.damage.DamageSource source) {
        if (!(entity instanceof dev.tcgshop.entity.TcgNpc) || !(entity.getWorld() instanceof ServerWorld w)) return;
        if (entity instanceof CustomerEntity c && c.type() == CustomerEntity.Type.THIEF) return;
        if (entity instanceof dev.tcgshop.entity.ScalperEntity) return;
        if (!(source.getAttacker() instanceof ServerPlayerEntity killer)) return;
        int pen = dev.tcgshop.EconomyConfig.INSTANCE.infamyPerKill;
        if (pen <= 0) return;
        boolean any = false;
        for (ServerWorld sw : w.getServer().getWorlds()) {
            ShopZones zs = ShopZones.get(sw);
            for (ShopZones.Zone z : zs.all())
                if (z.owner.equals(killer.getUuid())) {
                    z.infamy = Math.min(100, z.infamy + pen);
                    zs.markDirty();
                    any = true;
                }
        }
        killer.sendMessage(net.minecraft.text.Text.translatable(any ? "tcgshop.infamy.kill" : "tcgshop.infamy.kill_noshop", entity.getDisplayName(), pen)
                .formatted(net.minecraft.util.Formatting.RED), false);
        for (CustomerEntity c : w.getEntitiesByClass(CustomerEntity.class, entity.getBoundingBox().expand(24), x -> x != entity && x.isAlive()))
            c.witnessMurder();
    }

    private static void tick(ServerWorld world) {
        long t = world.getTime();
        if (t % 20 == 0) ShopServer.payWages(world);
        if (t % 100 == 0) ShopServer.shiftTick(world);
        ShopZones zones = ShopZones.get(world);
        if (zones.all().isEmpty()) return;
        for (ShopZones.Zone z : zones.all()) {
            // repartir el trabajo entre ticks
            int phase = Math.floorMod(z.id.hashCode(), 100);
            if (t % 1200 == phase) recompute(world, z);
            if (t % 100 == phase % 100) spawnCustomers(world, z);
            if (t % 600 == phase && z.smell > 0) { z.smell--; zones.markDirty(); }
            if (t % 40 == phase % 40 && z.smell >= 45) {
                // nubecillas verdes por la tienda cuando apesta
                int half = z.tierInfo().size / 2;
                double x = z.core.getX() + world.random.nextInt(half * 2 + 1) - half + 0.5, zz = z.core.getZ() + world.random.nextInt(half * 2 + 1) - half + 0.5;
                world.spawnParticles(new net.minecraft.particle.DustParticleEffect(new org.joml.Vector3f(0.45f, 0.62f, 0.2f), 1.6f),
                        x, z.core.getY() + 1.2, zz, 4, 0.4, 0.4, 0.4, 0);
            }
        }
    }

    // ------------------------------------------------------------------ reputación

    public static void recompute(ServerWorld world, ShopZones.Zone z) {
        int displays = 0, decor = 0, detectors = 0, stocked = 0;
        double ratioSum = 0;
        Set<String> variety = new HashSet<>();
        int half = z.tierInfo().size / 2;
        int cx0 = (z.core.getX() - half) >> 4, cx1 = (z.core.getX() + half) >> 4, cz0 = (z.core.getZ() - half) >> 4, cz1 = (z.core.getZ() + half) >> 4;
        for (int cx = cx0; cx <= cx1; cx++)
            for (int cz = cz0; cz <= cz1; cz++) {
                WorldChunk ch = world.getChunkManager().getWorldChunk(cx, cz);
                if (ch == null) continue;
                for (BlockEntity be : ch.getBlockEntities().values()) {
                    if (!z.contains(be.getPos())) continue;
                    if (be instanceof ShopDisplayBlockEntity d && !d.isHome()) {
                        displays++;
                        if (be.getCachedState().isOf(TcgBlocks.PEDESTAL)) decor++;
                        for (int i = 0; i < d.size(); i++) {
                            ItemStack st = d.getStack(i);
                            if (st.isEmpty()) continue;
                            stocked++;
                            variety.add(Registries.ITEM.getId(st.getItem()).toString());
                            long market = ShopServer.priceOf(st, 1.0);
                            ratioSum += market <= 0 ? 1 : d.price(i) / (double) market;
                        }
                    } else if (be instanceof DecorBlocks.DecorEntity) {
                        BlockState s = be.getCachedState();
                        if (s.isOf(TcgBlocks.THEFT_DETECTOR)) detectors++;
                        else if (s.isOf(TcgBlocks.POSTER) || s.isOf(TcgBlocks.PLAY_TABLE)) decor++;
                    }
                }
            }
        // peluches de Pokeblocks colocados (franja a la altura del suelo de la tienda)
        int dolls = 0;
        for (BlockPos p : BlockPos.iterate(z.core.getX() - half, z.core.getY() - 1, z.core.getZ() - half, z.core.getX() + half, z.core.getY() + 3, z.core.getZ() + half)) {
            Identifier id = Registries.BLOCK.getId(world.getBlockState(p).getBlock());
            if (id.getNamespace().equals("pokeblocks") && id.getPath().contains("pokedoll")) dolls++;
        }
        decor += dolls;

        long day = world.getTimeOfDay() / 24000;
        java.util.Random daily = new java.util.Random(z.id.getMostSignificantBits() ^ day * 31);
        int buzz = daily.nextInt(11) - 5;
        // cada día se olvida parte de lo malo: mala fama por muertes y clientes sin atender
        if (z.infamyDay != day) {
            if (z.infamyDay >= 0 && day > z.infamyDay) {
                long steps = Math.min(30, day - z.infamyDay);
                z.infamy = (int) Math.max(0, z.infamy - steps * dev.tcgshop.EconomyConfig.INSTANCE.infamyDecayPerDay);
                z.unattended = z.unattended >> (int) Math.min(8, steps);
                for (long k = 0; k < steps && z.charity != 0; k++) z.charity -= Integer.signum(z.charity) * Math.min(2, Math.abs(z.charity));
            }
            z.infamyDay = day;
        }
        Env env = scanEnvironment(world, z, half);

        z.factors.clear();
        z.flags.clear();
        double avg = stocked == 0 ? 1.2 : ratioSum / stocked;
        int price = (int) Math.round(Math.max(-15, Math.min(15, (1.1 - avg) * 50)));
        int var = Math.min(15, variety.size());
        int disp = Math.min(10, displays * 2);
        int dec = Math.min(15, decor * 2);
        int sec = (z.guard ? 8 : 0) + Math.min(6, detectors * 2);
        int serv = (z.cashier ? 8 : 0) + (z.cashier2 ? 4 : 0);
        int sat = z.reviews.size() >= 3 ? (int) Math.round(Math.max(-15, Math.min(15, (z.avgStars() - 3) * 7)))
                : (int) Math.max(-15, Math.min(15, (z.customers - z.complaints * 2) / 5.0));
        int theft = -Math.min(15, Math.max(0, z.stolen - z.recovered) * 2);
        z.factors.put("price", price);
        z.factors.put("variety", var);
        z.factors.put("displays", disp);
        z.factors.put("decor", dec);
        z.factors.put("security", sec);
        z.factors.put("service", serv);
        z.factors.put("satisfaction", sat);
        z.factors.put("theft", theft);
        z.factors.put("buzz", buzz);
        int smell = z.smell > 0 ? -Math.min(15, Math.round(z.smell / 6.5f)) : 0;
        if (smell != 0) z.factors.put("smell", smell);

        // ---- tres notas: Estética, Calidad/precio y Seguridad (0-100) -> reputación total con pesos configurables
        int roof = 0, walls = 0, light = 0, doors = 0, hostiles = 0, animals = 0, enclosure = 0;
        if (env.known) {
            roof = (int) Math.round((env.roof - 0.5) * 16);
            walls = (int) Math.round((env.walls - 0.5) * 16);
            light = env.light >= 11 ? 10 : env.light >= 8 ? 5 : env.light < 5 ? -10 : env.light < 7 ? -4 : 0;
            doors = env.doors > 0 ? 3 : 0;
            double enc = (env.roof + env.walls) / 2;
            enclosure = enc > 0.7 ? 8 : enc < 0.3 ? -8 : 0;
            z.factors.put("roof", roof);
            z.factors.put("walls", walls);
            z.factors.put("lighting", light);
            if (doors > 0) z.factors.put("doors", doors);
            if (env.roof < 0.4) z.flags.add("no_roof");
            if (env.walls < 0.35) z.flags.add("no_walls");
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
        int value = clamp(30 + (int) Math.round(price * 1.4) + var + Math.min(15, stocked / 2) + (env.packs > 0 ? 6 : 0) + kinds * 2);
        int safety = clamp(55 + sec + theft + hostiles + Math.max(-8, Math.min(4, animals)) + enclosure + (env.known && env.light < 5 ? -8 : 0) + infamy);
        if (aest >= 75) z.flags.add("cozy");
        if (price >= 8) z.flags.add("cheap");
        else if (price <= -8) z.flags.add("pricey");
        if (displays > 0 && stocked < 3) z.flags.add("empty");
        var cfg = dev.tcgshop.EconomyConfig.INSTANCE;
        double wsum = cfg.repWeightAesthetics + cfg.repWeightValue + cfg.repWeightSafety;
        double base = (aest * cfg.repWeightAesthetics + value * cfg.repWeightValue + safety * cfg.repWeightSafety) / wsum;
        int charity = Math.max(-10, Math.min(10, z.charity));
        if (charity != 0) z.factors.put("charity", charity);
        int rep = (int) Math.round(base + sat + buzz + serv * 0.6 + unatt + charity);
        z.reputation = Math.max(0, Math.min(100, rep));
        z.factors.put("m_aesthetics", aest);
        z.factors.put("m_value", value);
        z.factors.put("m_safety", safety);
        z.displays = displays;
        z.decor = decor;
        z.detectors = detectors;
        z.buzz = buzz;
        ShopZones.get(world).markDirty();
        ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(z.owner);
        if (owner != null) {
            if (z.reputation >= 50) CardAdvancements.grant(owner, "reputation_50");
            if (z.reputation >= 80) CardAdvancements.grant(owner, "reputation_80");
            if (z.reputation >= 100) CardAdvancements.grant(owner, "reputation_100");
            ShopServer.syncZones(owner);
        }
    }

    // ------------------------------------------------------------------ clientes

    private static void spawnCustomers(ServerWorld world, ShopZones.Zone z) {
        ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(z.owner);
        if (owner == null || owner.getServerWorld() != world || owner.squaredDistanceTo(z.core.toCenterPos()) > 96 * 96) return;
        if (z.displays == 0) return;
        int present = world.getEntitiesByClass(CustomerEntity.class, z.box().expand(24), c -> true).size();
        if (present >= z.tierInfo().maxCustomers) return;
        // los clientes contentos lo recomiendan: cada 5 estrellas reciente trae algo más de gente
        long today = world.getTimeOfDay() / 24000;
        int recs = 0;
        for (ShopZones.Review r : z.reviews) if (r.stars() >= 5 && today - r.day() <= 2) recs++;
        double chance = (0.2 + z.reputation / 180.0 + Math.min(0.15, recs * 0.03)) * DEMAND;
        if (world.random.nextDouble() > chance) return;
        CustomerEntity.Type type = rollType(world, z);
        // llegan andando desde lejos (no aparecen pegados a la tienda), entran por una puerta y se van por donde vinieron
        BlockPos door = null;
        Vec3d doorOutside = null, pos = null;
        java.util.List<BlockPos> doors = shopDoors(world, z);
        if (!doors.isEmpty()) {
            door = doors.get(world.random.nextInt(doors.size()));
            net.minecraft.util.math.Direction out = outward(world, z, door);
            doorOutside = Vec3d.ofBottomCenter(door.offset(out, 2));
            pos = farSpot(world, z, door, out);
        }
        if (pos == null) pos = edgeSpot(world, z, 16 + world.random.nextInt(10));
        if (pos == null) pos = edgeSpot(world, z, 6);
        if (pos == null) return;
        Vec3d exit = pos;
        ShopZones.Regular regular = null;
        if (type != CustomerEntity.Type.THIEF && !z.regulars.isEmpty() && world.random.nextFloat() < 0.25f)
            regular = z.regulars.get(world.random.nextInt(z.regulars.size()));
        CustomerEntity.spawn(world, z, type, pos, exit, regular, door, doorOutside);
    }

    private static CustomerEntity.Type rollType(ServerWorld world, ShopZones.Zone z) {
        double whale = 0.02 + z.reputation / 2500.0; // 2% .. 6%
        double thief = Math.max(0.03, 0.1 - (z.guard ? 0.03 : 0) - z.detectors * 0.01);
        double r = world.random.nextDouble();
        if (r < whale) return CustomerEntity.Type.WHALE;
        if ((r -= whale) < thief) return CustomerEntity.Type.THIEF;
        if ((r -= thief) < 0.10) return CustomerEntity.Type.SELLER;
        if ((r -= 0.10) < 0.07) return CustomerEntity.Type.TRADER;
        if ((r -= 0.07) < 0.09) return CustomerEntity.Type.HAGGLER;
        if ((r -= 0.10) < 0.15) return CustomerEntity.Type.PICKY;
        return CustomerEntity.Type.REGULAR;
    }

    /** Un punto caminable en el borde de la zona, a la altura del suelo de la tienda. */
    /** Puertas de madera de la tienda (la mitad de abajo), dentro de la zona y a la altura del suelo. */
    static java.util.List<BlockPos> shopDoors(ServerWorld world, ShopZones.Zone z) {
        java.util.List<BlockPos> out = new java.util.ArrayList<>();
        int half = z.tierInfo().size / 2;
        for (BlockPos p : BlockPos.iterate(z.core.getX() - half, z.core.getY() - 2, z.core.getZ() - half, z.core.getX() + half, z.core.getY() + 3, z.core.getZ() + half)) {
            BlockState s = world.getBlockState(p);
            if (s.isIn(net.minecraft.registry.tag.BlockTags.WOODEN_DOORS) && s.get(net.minecraft.block.DoorBlock.HALF) == net.minecraft.block.enums.DoubleBlockHalf.LOWER)
                out.add(p.toImmutable());
            if (out.size() >= 8) break;
        }
        return out;
    }

    /** Lado de fuera de una puerta: el que da al cielo (o, si no se sabe, el más alejado del centro de la tienda). */
    static net.minecraft.util.math.Direction outward(ServerWorld world, ShopZones.Zone z, BlockPos door) {
        net.minecraft.util.math.Direction f = world.getBlockState(door).get(net.minecraft.block.DoorBlock.FACING);
        boolean a = world.isSkyVisible(door.offset(f).up()), b = world.isSkyVisible(door.offset(f.getOpposite()).up());
        if (a != b) return a ? f : f.getOpposite();
        double da = door.offset(f).getSquaredDistance(z.core), db = door.offset(f.getOpposite()).getSquaredDistance(z.core);
        return da >= db ? f : f.getOpposite();
    }

    /** Sitio lejano (18-28 bloques) por el lado de fuera de la puerta, en suelo firme. */
    @org.jetbrains.annotations.Nullable
    private static Vec3d farSpot(ServerWorld world, ShopZones.Zone z, BlockPos door, net.minecraft.util.math.Direction out) {
        net.minecraft.util.math.Direction side = out.rotateYClockwise();
        for (int i = 0; i < 10; i++) {
            int d = 18 + world.random.nextInt(11), lat = world.random.nextInt(13) - 6;
            BlockPos c = door.offset(out, d).offset(side, lat);
            if (z.contains(c)) continue;
            if (!world.isChunkLoaded(c)) continue;
            int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, c.getX(), c.getZ());
            if (Math.abs(y - door.getY()) > 6) continue;
            BlockPos g = new BlockPos(c.getX(), y, c.getZ());
            if (world.getBlockState(g.down()).isSolidBlock(world, g.down()) && world.isAir(g) && world.isAir(g.up()) && world.getFluidState(g.down()).isEmpty())
                return new Vec3d(g.getX() + 0.5, g.getY(), g.getZ() + 0.5);
        }
        return null;
    }

    private static Vec3d edgeSpot(ServerWorld world, ShopZones.Zone z, int outside) {
        int half = z.tierInfo().size / 2 - 1 + outside;
        for (int i = 0; i < 12; i++) {
            int side = world.random.nextInt(4), off = world.random.nextInt(half * 2 + 1) - half;
            int x = z.core.getX() + (side == 0 ? -half : side == 1 ? half : off);
            int zz = z.core.getZ() + (side == 2 ? -half : side == 3 ? half : off);
            for (int dy = 3; dy >= -2; dy--) {
                BlockPos p = new BlockPos(x, z.core.getY() + dy, zz);
                if (world.getBlockState(p.down()).isSolidBlock(world, p.down()) && world.isAir(p) && world.isAir(p.up()))
                    return new Vec3d(x + 0.5, p.getY(), zz + 0.5);
            }
            int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, zz);
            if (Math.abs(y - z.core.getY()) <= 3) return new Vec3d(x + 0.5, y, zz + 0.5);
        }
        return null;
    }

    private static int clamp(int v) { return Math.max(0, Math.min(100, v)); }

    /** Lo que ve un cliente de la tienda y alrededores. */
    static final class Env {
        boolean known;
        double roof, walls, light;
        int doors, hostilesInside, hostilesNear, farm, pets, pokemon, slots, packs, kinds;
    }

    /**
     * Mira la tienda como la vería un cliente: sólo la zona donde hay muebles (no se castiga una parcela grande con la
     * tienda en una esquina). Techo y paredes por "rayos" desde el suelo, luz de bloques, puertas, monstruos cerca,
     * animales de granja, mascotas y Pokémon dentro.
     */
    private static Env scanEnvironment(ServerWorld world, ShopZones.Zone z, int half) {
        Env e = new Env();
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        boolean cards = false, packs = false, boxes = false, dolls = false;
        int cx0 = (z.core.getX() - half) >> 4, cx1 = (z.core.getX() + half) >> 4, cz0 = (z.core.getZ() - half) >> 4, cz1 = (z.core.getZ() + half) >> 4;
        for (int cx = cx0; cx <= cx1; cx++)
            for (int cz = cz0; cz <= cz1; cz++) {
                WorldChunk ch = world.getChunkManager().getWorldChunk(cx, cz);
                if (ch == null) continue;
                for (BlockEntity be : ch.getBlockEntities().values()) {
                    BlockPos p = be.getPos();
                    if (!z.contains(p)) continue;
                    boolean shopThing = be instanceof ShopDisplayBlockEntity || be instanceof DecorBlocks.DecorEntity;
                    if (!shopThing) continue;
                    minX = Math.min(minX, p.getX()); maxX = Math.max(maxX, p.getX());
                    minZ = Math.min(minZ, p.getZ()); maxZ = Math.max(maxZ, p.getZ());
                    if (be instanceof ShopDisplayBlockEntity d && !d.isHome()) {
                        e.slots += d.size();
                        for (int i = 0; i < d.size(); i++) {
                            ItemStack st = d.getStack(i);
                            if (st.isEmpty()) continue;
                            if (dev.tcgshop.card.CardInfo.isCard(st)) cards = true;
                            else if (dev.tcgshop.card.CardInfo.isPack(st)) { packs = true; e.packs++; }
                            else if (st.isOf(TcgBlocks.BOOSTER_BOX_ITEM)) boxes = true;
                            else if (ShopServer.isPokedoll(st)) dolls = true;
                        }
                    }
                }
            }
        e.kinds = (cards ? 1 : 0) + (packs ? 1 : 0) + (boxes ? 1 : 0) + (dolls ? 1 : 0);
        // monstruos y animales (aunque no haya muebles todavía)
        net.minecraft.util.math.Box zone = z.box();
        for (net.minecraft.entity.Entity ent : world.getOtherEntities(null, zone.expand(16))) {
            boolean inside = zone.contains(ent.getPos());
            if (ent instanceof net.minecraft.entity.mob.HostileEntity) {
                if (inside) e.hostilesInside++; else e.hostilesNear++;
            } else if (!inside) continue;
            else if (ent instanceof net.minecraft.entity.passive.TameableEntity t && t.isTamed()) e.pets++;
            else if (ent instanceof net.minecraft.entity.passive.AnimalEntity) e.farm++;
            else if (Registries.ENTITY_TYPE.getId(ent.getType()).getNamespace().equals("cobblemon")) e.pokemon++;
        }
        if (minX == Integer.MAX_VALUE) return e; // sin muebles: no se juzga el edificio
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
                if (!world.getBlockState(foot).isAir() && world.getBlockState(foot).isOpaqueFullCube(world, foot)) continue; // dentro de una pared
                n++;
                boolean roofed = !world.isSkyVisible(foot.up());
                if (roofed) covered++;
                int bl = world.getLightLevel(net.minecraft.world.LightType.BLOCK, foot);
                lightSum += roofed ? bl : Math.max(bl, 12);
                int hits = 0;
                for (net.minecraft.util.math.Direction dir : net.minecraft.util.math.Direction.Type.HORIZONTAL) {
                    for (int k = 1; k <= reach; k++) {
                        BlockPos q = foot.offset(dir, k);
                        BlockState s = world.getBlockState(q);
                        if (!s.isAir() && !s.isReplaceable() && (s.isOpaque() || s.isIn(net.minecraft.registry.tag.BlockTags.DOORS)
                                || s.isIn(net.minecraft.registry.tag.BlockTags.IMPERMEABLE) || s.isIn(net.minecraft.registry.tag.BlockTags.WALLS))) { hits++; break; }
                    }
                }
                wallSum += hits / 4.0;
            }
        if (n == 0) { e.known = false; return e; }
        e.roof = covered / (double) n;
        e.walls = wallSum / n;
        e.light = lightSum / (double) n;
        for (BlockPos q : BlockPos.iterate(minX - 2, fy, minZ - 2, maxX + 2, fy + 1, maxZ + 2))
            if (world.getBlockState(q).isIn(net.minecraft.registry.tag.BlockTags.DOORS) || world.getBlockState(q).isIn(net.minecraft.registry.tag.BlockTags.FENCE_GATES)) e.doors++;
        return e;
    }
}

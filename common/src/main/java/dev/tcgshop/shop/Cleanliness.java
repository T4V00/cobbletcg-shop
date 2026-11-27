package dev.tcgshop.shop;

import dev.tcgshop.entity.CustomerEntity;
import dev.tcgshop.entity.NpcLines;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 1.1 (cleanliness update): how clean a shop is, and why it matters.
 * <p>
 * Score 0-100 ({@link #score}) from three things: the bad smell (0-100, smelly customers and droppings; deodorant spray
 * and wall fresheners), the grime (0-100, foot traffic: every finished visit may add some; the mop scrubs it, fresheners
 * slowly too) and the visible dirt: droppings on the floor ({@link PoopBlock}, cleaned with the mop), graffiti on the
 * walls ({@link GraffitiBlock}, erased by wall fresheners and the deodorant spray) and the floor-mess incidents of events
 * (puddles, dirt, droppings, mess; {@link dev.tcgshop.events.incident.IncidentModule#floorDirt}). It now weighs much more than the
 * old smell penalty: it scales the buy chance ({@link #buyMult}), the arrivals ({@link #spawnMult}), the customer's mood
 * on entering ({@link #enterMood}) and the reputation factor "clean" ({@link #repFactor}).
 * <p>
 * Droppings appear over time in open, busy shops ({@link #tick}): near a customer or a Pokémon inside, more often when
 * the shop is crowded or already dirty, never on top of each other or next to another one, and never more than
 * {@link #cap}. Graffiti appears on the walls of grimy shops ({@link CleanConfig#graffitiMinGrime}). Nothing grows while
 * the owner is away (they could not clean it). Everything is tunable in config/tcgshop-clean.json ({@link CleanConfig}).
 */
public final class Cleanliness {
    private Cleanliness() {}

    // ------------------------------------------------------------------ score and effects (pure)

    public static int score(int smell, int grime, int poops) {
        CleanConfig c = CleanConfig.INSTANCE;
        double s = 100 - Math.max(0, smell) * c.smellWeight - Math.max(0, grime) * c.grimeWeight
                - Math.min(c.poopPenaltyCap, Math.max(0, poops) * c.poopPenalty);
        return (int) Math.max(0, Math.min(100, Math.round(s)));
    }

    public static int score(ShopZones.Zone z) {
        return z == null ? 100 : score(z.smell, z.grime, visibleDirt(z));
    }

    /** Droppings + graffiti + floor-mess incidents (each counts like one dropping in the score). */
    public static int visibleDirt(ShopZones.Zone z) {
        return z.poops.size() + z.graffiti.size() + Math.max(0, z.floorDirt);
    }

    /** Buy chance multiplier: +6 % spotless (90+), 1 at 75-89, then down to x0.25 at 0 (salesImpact scales the drop). */
    public static double buyMult(int clean) {
        if (clean >= 90) return 1.06;
        if (clean >= 75) return 1.0;
        double drop = (75 - clean) / 75.0 * CleanConfig.INSTANCE.salesImpact;
        return Math.max(0.25, 1 - drop);
    }

    /** Arrival multiplier: 1 from 70 up, down to x0.5 at 0 (visitImpact scales the drop). */
    public static double spawnMult(int clean) {
        if (clean >= 70) return 1.0;
        return Math.max(0.3, 1 - (70 - clean) / 140.0 * CleanConfig.INSTANCE.visitImpact);
    }

    /** Mood change on entering: +1 spotless, 0 clean, -1 dirty, -2 filthy. */
    public static int enterMood(int clean) {
        if (!CleanConfig.INSTANCE.moodImpact) return 0;
        return clean >= 90 ? 1 : clean >= 65 ? 0 : clean >= 40 ? -1 : -2;
    }

    /** Reputation factor "clean": +5 spotless .. -20 filthy (repImpact scales it). */
    public static int repFactor(int clean) {
        int f = clean >= 95 ? 5 : clean >= 85 ? 3 : clean >= 70 ? 0 : -(int) Math.min(20, Math.round((70 - clean) / 3.5));
        return (int) Math.round(f * CleanConfig.INSTANCE.repImpact);
    }

    /** Most droppings at once in a shop of that side. */
    public static int cap(int zoneSize) {
        return Math.max(0, Math.min(CleanConfig.INSTANCE.poopMax, 2 + Math.max(0, zoneSize) / 8));
    }

    /** Chance (per 30 s) that a dropping appears, for that many customers / Pokémon inside and that cleanliness. */
    public static double poopChance(int customers, int pokemon, int clean) {
        if (customers + pokemon <= 0) return 0;
        double dirt = 1 + (100 - Math.max(0, Math.min(100, clean))) / 50.0; // up to x3 in a filthy shop
        return Math.min(0.9, CleanConfig.INSTANCE.poopChance * (1 + Math.min(8, customers) * 0.25) * (1 + Math.min(6, pokemon) * 0.4) * dirt);
    }

    // ------------------------------------------------------------------ server tick (every 30 s per shop, from ShopLife)

    public static void tick(ServerLevel world, ShopZones.Zone z) {
        ShopZones zones = ShopZones.get(world);
        if (prune(world, z)) zones.setDirty();
        if (z.dormant) return;
        boolean loaded = ShopLife.zoneLoaded(world, z);
        if (loaded) z.floorDirt = dev.tcgshop.events.incident.IncidentModule.floorDirt(world, z);
        // nothing below grows while the owner is away or the shop is not loaded: they could not clean it
        ServerPlayer owner = world.getServer().getPlayerList().getPlayer(z.owner);
        if (!loaded || owner == null || owner.serverLevel() != world || owner.distanceToSqr(z.core.getCenter()) > 96 * 96) return;
        // droppings slowly make the shop smell
        int n = z.poops.size();
        if (n > 0 && CleanConfig.INSTANCE.poopSmell > 0 && z.smell < 100) {
            z.smell = Math.min(100, z.smell + (n * CleanConfig.INSTANCE.poopSmell + 1) / 2);
            zones.setDirty();
        }
        if (z.closed) return;
        List<Entity> sources = new ArrayList<>();
        List<Entity> people = new ArrayList<>();
        int customers = 0, pokemon = 0;
        for (Entity e : world.getEntities((Entity) null, z.box(), e -> e.isAlive() && e instanceof LivingEntity)) {
            if (!z.contains(e.blockPosition())) continue;
            if (e instanceof CustomerEntity c && c.type() != CustomerEntity.Type.THIEF) { customers++; sources.add(e); people.add(e); }
            else if (BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getNamespace().equals("cobblemon")
                    && !e.getTags().contains(dev.tcgshop.events.EventManager.EVENT_TAG)) { pokemon++; sources.add(e); }
        }
        if (sources.isEmpty()) return;
        // graffiti: rowdy customers tag the walls of a grimy shop
        if (!people.isEmpty() && z.graffiti.size() < graffitiCap() && world.random.nextDouble() < graffitiChance(customers, z.grime)) {
            Entity src = people.get(world.random.nextInt(people.size()));
            BlockPos at = placeGraffiti(world, z, src.blockPosition(), world.random);
            if (at != null && owner.distanceToSqr(at.getCenter()) < 48 * 48)
                owner.displayClientMessage(Component.translatable("tcgshop.graffiti.appeared").withStyle(ChatFormatting.GOLD), true);
        }
        if (!CleanConfig.INSTANCE.poopEnabled || n >= cap(z.size())) return;
        if (world.random.nextDouble() >= poopChance(customers, pokemon, score(z))) return;
        Entity src = sources.get(world.random.nextInt(sources.size()));
        boolean big = !(src instanceof CustomerEntity);
        BlockPos at = placePoop(world, z, src.blockPosition(), big);
        if (at == null) return;
        world.playSound(null, at, SoundEvents.SLIME_SQUISH_SMALL, SoundSource.NEUTRAL, 0.6f, 0.8f);
        if (owner.distanceToSqr(at.getCenter()) < 48 * 48)
            owner.displayClientMessage(Component.translatable(big ? "tcgshop.poop.appeared_pokemon" : "tcgshop.poop.appeared", src.getDisplayName())
                    .withStyle(ChatFormatting.GOLD), true);
    }

    // ------------------------------------------------------------------ graffiti

    /** Most graffiti at once in a shop (0 = turned off). */
    public static int graffitiCap() {
        return CleanConfig.INSTANCE.graffitiEnabled ? CleanConfig.INSTANCE.graffitiMax : 0;
    }

    /** Chance (per 30 s) that a customer tags a wall: only from graffitiMinGrime up, more with more customers and grime. */
    public static double graffitiChance(int customers, int grime) {
        CleanConfig c = CleanConfig.INSTANCE;
        if (!c.graffitiEnabled || customers <= 0 || grime < c.graffitiMinGrime) return 0;
        double dirt = 1 + (Math.min(100, grime) - c.graffitiMinGrime) / 50.0;
        return Math.min(0.5, c.graffitiChance * (1 + Math.min(8, customers) * 0.25) * dirt);
    }

    /**
     * Paints a graffiti on a free wall face close to near (same height or one above), inside the zone and never next to
     * another one. Returns where, or null.
     */
    @org.jetbrains.annotations.Nullable
    public static BlockPos placeGraffiti(ServerLevel world, ShopZones.Zone z, BlockPos near, RandomSource r) {
        if (z.graffiti.size() >= graffitiCap()) return null;
        List<BlockPos> spots = new ArrayList<>();
        for (int dx = -3; dx <= 3; dx++)
            for (int dz = -3; dz <= 3; dz++)
                for (int dy = 0; dy <= 1; dy++) spots.add(near.offset(dx, dy, dz));
        java.util.Collections.shuffle(spots, new java.util.Random(r.nextLong()));
        for (BlockPos p : spots) {
            if (!z.contains(p) || !world.isEmptyBlock(p) || !world.getFluidState(p).isEmpty() || nearOther(z.graffiti, p)) continue;
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos wall = p.relative(d);
                if (!world.getBlockState(wall).isFaceSturdy(world, wall, d.getOpposite())) continue;
                BlockState st = CleanBlocks.GRAFFITI.defaultBlockState().setValue(GraffitiBlock.FACING, d.getOpposite())
                        .setValue(GraffitiBlock.VARIANT, r.nextInt(GraffitiBlock.VARIANTS));
                world.setBlock(p, st, 3);
                if (!world.getBlockState(p).is(CleanBlocks.GRAFFITI)) break;
                z.graffiti.add(p.asLong());
                ShopZones.get(world).setDirty();
                world.playSound(null, p, SoundEvents.FIRE_EXTINGUISH, SoundSource.NEUTRAL, 0.25f, 1.6f);
                return p.immutable();
            }
        }
        return null;
    }

    /** Removes a graffiti and forgets it. */
    public static void removeGraffiti(ServerLevel w, BlockPos pos) {
        if (w.getBlockState(pos).is(CleanBlocks.GRAFFITI)) w.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        ShopZones.Zone z = ShopZones.get(w).at(pos);
        if (z != null && z.graffiti.remove(pos.asLong())) ShopZones.get(w).setDirty();
    }

    /** The nearest graffiti of z within range of pos (or null). */
    @org.jetbrains.annotations.Nullable
    static BlockPos nearestGraffiti(ShopZones.Zone z, BlockPos pos, int range) {
        BlockPos best = null;
        double bd = (double) range * range;
        for (long l : z.graffiti) {
            BlockPos q = BlockPos.of(l);
            double d = q.distSqr(pos);
            if (d <= bd) { bd = d; best = q; }
        }
        return best;
    }

    /** Deodorant spray uses it takes to scrub one graffiti by hand. */
    public static final int GRAFFITI_SPRAY_COST = 2;

    /** Click on a graffiti: the deodorant spray scrubs it off (shop owner, an op in creative, or anyone outside shops). */
    static void useGraffiti(ServerLevel w, BlockPos pos, ServerPlayer p, ItemStack stack, InteractionHand hand) {
        ShopZones.Zone z = ShopZones.get(w).at(pos);
        if (z != null && !z.owner.equals(p.getUUID()) && !(p.isCreative() && p.hasPermissions(2))) {
            p.displayClientMessage(Component.translatable("tcgshop.incident.not_owner").withStyle(ChatFormatting.RED), true);
            return;
        }
        if (!stack.is(dev.tcgshop.item.TcgItems.DEODORANT_SPRAY)) {
            p.displayClientMessage(Component.translatable("tcgshop.graffiti.need_spray").withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        stack.hurtAndBreak(GRAFFITI_SPRAY_COST, p, LivingEntity.getSlotForHand(hand));
        p.swing(hand, true);
        p.getCooldowns().addCooldown(stack.getItem(), 12);
        w.sendParticles(ParticleTypes.CLOUD, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.01);
        w.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5f, 1.8f);
        removeGraffiti(w, pos);
        if (z != null) p.displayClientMessage(Component.translatable("tcgshop.graffiti.cleaned", score(z)).withStyle(ChatFormatting.GREEN), true);
    }

    /**
     * The shop moved (a dormant shop restored somewhere else): its droppings and graffiti stay behind at the old site.
     * The ones in loaded chunks of the old world are removed now (the rest remove themselves on a random tick, untracked)
     * and the zone forgets all of them.
     */
    public static void forgetSite(ServerLevel oldWorld, ShopZones.Zone z) {
        for (long l : z.poops) {
            BlockPos p = BlockPos.of(l);
            if (oldWorld.hasChunkAt(p) && oldWorld.getBlockState(p).is(CleanBlocks.POOP)) oldWorld.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
        }
        for (long l : z.graffiti) {
            BlockPos p = BlockPos.of(l);
            if (oldWorld.hasChunkAt(p) && oldWorld.getBlockState(p).is(CleanBlocks.GRAFFITI)) oldWorld.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
        }
        z.poops.clear();
        z.graffiti.clear();
        z.floorDirt = 0;
    }

    /** A dropping / graffiti block that no shop tracks (left behind by a moved shop, a lost zone...) goes away. */
    static boolean untracked(ServerLevel w, BlockPos pos, boolean graffiti) {
        ShopZones.Zone z = ShopZones.get(w).at(pos);
        return z == null || !(graffiti ? z.graffiti : z.poops).contains(pos.asLong());
    }

    /** Places a dropping at (or right next to) near, inside the zone, on a free floor spot away from other droppings. */
    public static BlockPos placePoop(ServerLevel world, ShopZones.Zone z, BlockPos near, boolean big) {
        if (z.poops.size() >= cap(z.size())) return null;
        BlockPos[] tries = {near, near.north(), near.south(), near.east(), near.west(), near.below(), near.above()};
        for (BlockPos p : tries) {
            if (!z.contains(p) || !world.isEmptyBlock(p) || !world.getFluidState(p).isEmpty()) continue;
            BlockState floor = world.getBlockState(p.below());
            if (!floor.isFaceSturdy(world, p.below(), Direction.UP) || floor.is(CleanBlocks.POOP)) continue;
            if (nearOther(z.poops, p)) continue;
            world.setBlock(p, CleanBlocks.POOP.defaultBlockState().setValue(PoopBlock.BIG, big), 3);
            if (!world.getBlockState(p).is(CleanBlocks.POOP)) continue;
            z.poops.add(p.asLong());
            ShopZones.get(world).setDirty();
            return p.immutable();
        }
        return null;
    }

    /** Never two droppings (or two graffiti) side by side: they don't pile up. */
    private static boolean nearOther(java.util.Set<Long> set, BlockPos p) {
        for (long l : set) {
            BlockPos q = BlockPos.of(l);
            if (Math.abs(q.getX() - p.getX()) <= 1 && Math.abs(q.getY() - p.getY()) <= 1 && Math.abs(q.getZ() - p.getZ()) <= 1) return true;
        }
        return false;
    }

    /**
     * Drops the remembered droppings / graffiti that are outside the zone (it moved) or no longer there (loaded chunks
     * only). Returns whether anything changed.
     */
    public static boolean prune(ServerLevel world, ShopZones.Zone z) {
        return prune(world, z, z.poops, CleanBlocks.POOP) | prune(world, z, z.graffiti, CleanBlocks.GRAFFITI);
    }

    private static boolean prune(ServerLevel world, ShopZones.Zone z, java.util.Set<Long> set, net.minecraft.world.level.block.Block block) {
        boolean ch = false;
        for (Iterator<Long> it = set.iterator(); it.hasNext(); ) {
            BlockPos p = BlockPos.of(it.next());
            if (!z.contains(p)) { it.remove(); ch = true; continue; } // checked first: an old site's chunks are usually unloaded
            if (!world.hasChunkAt(p)) continue;
            if (!world.getBlockState(p).is(block)) { it.remove(); ch = true; }
        }
        return ch;
    }

    // ------------------------------------------------------------------ cleaning

    /** Click on a dropping: the mop cleans it (shop owner, an op in creative, or anyone outside shops). */
    static void usePoop(ServerLevel w, BlockPos pos, BlockState state, ServerPlayer p, ItemStack stack, InteractionHand hand) {
        ShopZones.Zone z = ShopZones.get(w).at(pos);
        if (z != null && !z.owner.equals(p.getUUID()) && !(p.isCreative() && p.hasPermissions(2))) {
            p.displayClientMessage(Component.translatable("tcgshop.incident.not_owner").withStyle(ChatFormatting.RED), true);
            return;
        }
        if (!stack.is(dev.tcgshop.item.TcgItems.MOP)) {
            ItemStack off = p.getOffhandItem();
            if (stack.isEmpty() && off.is(dev.tcgshop.item.TcgItems.MOP)) {
                stack = off;
                hand = InteractionHand.OFF_HAND;
            } else {
                p.displayClientMessage(Component.translatable("tcgshop.poop.need_mop").withStyle(ChatFormatting.YELLOW), true);
                return;
            }
        }
        stack.hurtAndBreak(1, p, LivingEntity.getSlotForHand(hand));
        p.swing(hand, true);
        double x = pos.getX() + 0.5, y = pos.getY() + 0.1, zz = pos.getZ() + 0.5;
        w.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), x, y, zz, 8, 0.3, 0.05, 0.3, 0.05);
        w.playSound(null, pos, SoundEvents.WET_GRASS_BREAK, SoundSource.BLOCKS, 0.7f, 0.9f + w.random.nextFloat() * 0.3f);
        if (state.getValue(PoopBlock.BIG)) { // a big pile takes two swipes
            w.setBlock(pos, state.setValue(PoopBlock.BIG, false), 3);
            p.displayClientMessage(Component.translatable("tcgshop.poop.one_more").withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        removePoop(w, pos);
        w.sendParticles(ParticleTypes.POOF, x, y + 0.2, zz, 4, 0.25, 0.1, 0.25, 0.01);
        w.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.4f, 1.4f);
        if (z != null) {
            z.grime = Math.max(0, z.grime - 2);
            ShopZones.get(w).setDirty();
            p.displayClientMessage(Component.translatable("tcgshop.poop.cleaned", score(z)).withStyle(ChatFormatting.GREEN), true);
        }
    }

    /** Removes a dropping and forgets it. */
    public static void removePoop(ServerLevel w, BlockPos pos) {
        if (w.getBlockState(pos).is(CleanBlocks.POOP)) w.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        ShopZones.Zone z = ShopZones.get(w).at(pos);
        if (z != null && z.poops.remove(pos.asLong())) ShopZones.get(w).setDirty();
    }

    /** The mop on your own shop's floor: scrubs some grime (true = it did something). */
    public static boolean mopFloor(ServerLevel w, ServerPlayer p, BlockPos clicked, ItemStack stack, InteractionHand hand) {
        ShopZones.Zone z = ShopZones.get(w).at(clicked.above());
        if (z == null || !z.owner.equals(p.getUUID())) return false;
        if (z.grime <= 0) {
            p.displayClientMessage(Component.translatable("tcgshop.grime.clean_already", score(z)).withStyle(ChatFormatting.GREEN), true);
            return true;
        }
        z.grime = Math.max(0, z.grime - CleanConfig.INSTANCE.mopGrime);
        ShopZones.get(w).setDirty();
        stack.hurtAndBreak(1, p, LivingEntity.getSlotForHand(hand));
        p.getCooldowns().addCooldown(stack.getItem(), 10);
        p.swing(hand, true);
        w.sendParticles(ParticleTypes.SPLASH, clicked.getX() + 0.5, clicked.getY() + 1.05, clicked.getZ() + 0.5, 8, 0.35, 0.02, 0.35, 0.05);
        w.playSound(null, clicked, SoundEvents.BRUSH_GENERIC, SoundSource.BLOCKS, 0.7f, 1.1f);
        p.displayClientMessage(Component.translatable("tcgshop.grime.scrubbed", z.grime, score(z)).withStyle(ChatFormatting.AQUA), true);
        return true;
    }

    /** A finished customer visit: foot traffic leaves a bit of grime. */
    public static void onVisit(ShopZones.Zone z, RandomSource r) {
        if (z.grime < 100 && r.nextDouble() < CleanConfig.INSTANCE.grimePerVisit) z.grime++;
    }

    // ------------------------------------------------------------------ wall fresheners

    /** Last puffs per shop (game time), to cap how many fresheners work in one shop at once. */
    private static final Map<UUID, List<Long>> PUFFS = new HashMap<>();

    /** A freshener in z wants to puff now: removes smell / grime if there is any (true = puffed, one charge used). */
    static boolean freshenerPuff(ServerLevel w, ShopZones.Zone z, BlockPos pos) {
        CleanConfig c = CleanConfig.INSTANCE;
        BlockPos tag = nearestGraffiti(z, pos, c.sprayGraffitiRange);
        if (z.smell <= 0 && z.grime <= 0 && tag == null) return false;
        long now = w.getGameTime();
        List<Long> l = PUFFS.computeIfAbsent(z.id, k -> new ArrayList<>());
        l.removeIf(t -> now - t >= c.sprayInterval || now < t);
        if (l.size() >= c.sprayMaxPerShop) return false;
        l.add(now);
        z.smell = Math.max(0, z.smell - c.spraySmell);
        z.grime = Math.max(0, z.grime - c.sprayGrime);
        if (tag != null) { // the mist dissolves the nearest graffiti
            w.sendParticles(ParticleTypes.CLOUD, tag.getX() + 0.5, tag.getY() + 0.5, tag.getZ() + 0.5, 5, 0.3, 0.3, 0.3, 0.01);
            removeGraffiti(w, tag);
        }
        ShopZones.get(w).setDirty();
        return true;
    }

    /** Forgets the freshener bookkeeping (server stop, registered in CleanBlocks.init; the selftest too). */
    public static void reset() { PUFFS.clear(); }

    // ------------------------------------------------------------------ customers

    /**
     * A customer inside the shop next to a dropping (checked every 2 s): complains once, loses mood and remembers it for
     * the review. Returns the mood change.
     */
    public static int poopNearby(CustomerEntity c, ShopZones.Zone z) {
        if (z.poops.isEmpty()) return 0;
        BlockPos b = c.blockPosition();
        for (long l : z.poops) {
            BlockPos q = BlockPos.of(l);
            if (q.distSqr(b) <= 2.5 * 2.5) {
                if (c.getRandom().nextFloat() < 0.6f) NpcLines.sayNear(c, "poop_complain");
                return -2;
            }
        }
        return 0;
    }
}

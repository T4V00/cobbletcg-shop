package dev.tcgshop.shop;

import dev.tcgshop.compat.CobbleFurniesCompat;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 0.9.5 · Shop seats for customers: chairs, stools, benches, sofas... from any mod (CobbleFurnies,
 * Lucky's Cozyhome and similar, by block name) and, if there are none, stairs placed as a bench.
 * Also stores the furniture and plushies that customers stop to look at.
 *
 * Everything is server-side with no compile dependency on those mods. The list is cached per shop and refreshed
 * at most every 30 s (only when someone asks for it). Each seat is reserved by a single customer.
 */
public final class ShopSeats {
    /** A seat: block, height (absolute Y) where the seat entity goes, which way it faces and whether it's real furniture. */
    public record Seat(BlockPos pos, double seatY, float yaw, boolean furniture) {}

    /** Seat chosen for a customer: the seat and the spot next to it from which they sit down (and return to when getting up). */
    public record Pick(Seat seat, Vec3 stand) {}

    private static final class Cache {
        long at;
        List<Seat> seats = List.of();
        List<BlockPos> decor = List.of();
        /** 1.0: statue bases (any tier) in the shop, never capped (StatuePacks). */
        List<BlockPos> statues = List.of();
        /** 1.0: play tables in the shop (ptcg/PlayTables: exhibitions and customer challenges). */
        List<BlockPos> tables = List.of();
    }

    private record Key(ResourceKey<Level> dim, long pos) {}

    private static final Map<UUID, Cache> CACHE = new HashMap<>();
    private static final Map<Key, UUID> RESERVED = new HashMap<>();
    /** Last alarm (theft detected...) per shop: those sitting get up. */
    private static final Map<UUID, Long> ALARM = new HashMap<>();
    private static final long REFRESH = 20 * 30;
    /** From the floor under the feet to the underside of the thighs in the sitting pose (biped model at scale 1). */
    private static final double THIGH = 0.62;
    private static final Set<String> SEAT_WORDS = Set.of("chair", "armchair", "stool", "sofa", "couch", "bench", "seat", "loveseat", "ottoman");
    /** Lines (tcgshop.seat.<cat>.<n>, tools/gen_lang_095_seat.py). */
    public static final int SIT_LINES = 8, SIT_BUYER_LINES = 6, STAND_LINES = 6, TALK_LINES = 5, STARTLED_LINES = 4, FURNITURE_LINES = 7, PLUSH_LINES = 7;

    private ShopSeats() {}

    // ------------------------------------------------------------------ configuration (optional, via reflection)

    /** Do customers sit down? (TcgShopConfig.customerSeats if it exists; otherwise, yes). */
    public static boolean enabled() {
        Object v = cfg("customerSeats");
        return !(v instanceof Boolean b) || b;
    }

    /** Chance of sitting for a while for someone who hasn't bought anything (TcgShopConfig.customerSitChance). */
    public static double sitChance() {
        return cfg("customerSitChance") instanceof Number n ? clamp(n.doubleValue(), 0, 1) : 0.45;
    }

    /** Chance of sitting down after paying (TcgShopConfig.customerSitChanceBuyer). */
    public static double buyerSitChance() {
        return cfg("customerSitChanceBuyer") instanceof Number n ? clamp(n.doubleValue(), 0, 1) : 0.35;
    }

    @Nullable
    private static Object cfg(String field) {
        try {
            Object inst = dev.tcgshop.TcgShopConfig.INSTANCE;
            return inst.getClass().getField(field).get(inst);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }

    // ------------------------------------------------------------------ detection

    /** Is the block name a seat's? (word by word: "oak_chair", "red_sofa_2", "bar_stools"...). */
    public static boolean seatWord(String path) {
        int slash = path.lastIndexOf('/');
        for (String w : path.substring(slash + 1).split("_")) {
            w = w.replaceAll("\\d+$", "");
            if (SEAT_WORDS.contains(w) || (w.endsWith("s") && SEAT_WORDS.contains(w.substring(0, w.length() - 1)))) return true;
        }
        return false;
    }

    /** Is it a furniture seat (any mod, including this one's)? */
    public static boolean isFurnitureSeat(BlockState s) {
        if (s.isAir()) return false;
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(s.getBlock());
        return !"minecraft".equals(id.getNamespace()) && seatWord(id.getPath());
    }

    /** "Bench" stairs: bottom half, straight, resting on the ground, with the space in front clear and nothing on top. */
    private static boolean isBenchStairs(ServerLevel w, BlockPos p, BlockState s) {
        if (!(s.getBlock() instanceof StairBlock) || s.getValue(StairBlock.HALF) != Half.BOTTOM) return false;
        if (s.getValue(StairBlock.SHAPE) != net.minecraft.world.level.block.state.properties.StairsShape.STRAIGHT) return false;
        Direction front = s.getValue(StairBlock.FACING).getOpposite();
        BlockPos f = p.relative(front), back = p.relative(front.getOpposite());
        if (!w.getBlockState(p.below()).isRedstoneConductor(w, p.below())) return false;
        if (!standable(w, f)) return false;
        // if there's another higher stair behind, it's a real staircase, not a bench
        return !(w.getBlockState(back.above()).getBlock() instanceof StairBlock) && !(w.getBlockState(back).getBlock() instanceof StairBlock && w.getBlockState(back).getValue(StairBlock.HALF) == Half.TOP);
    }

    /** Is there still a seat at that block? (checked by the customer and the seat entity). */
    public static boolean stillSeat(ServerLevel w, BlockPos p) {
        if (!w.hasChunkAt(p)) return false;
        BlockState s = w.getBlockState(p);
        return isFurnitureSeat(s) || s.getBlock() instanceof StairBlock;
    }

    private static boolean standable(ServerLevel w, BlockPos p) {
        return !w.getBlockState(p).blocksMotion() && !w.getBlockState(p.above()).blocksMotion()
                && w.getBlockState(p.below()).isRedstoneConductor(w, p.below());
    }

    /** Height of the seat surface (0-1 within the block): ray through the block's center from top to bottom. */
    private static double surface(ServerLevel w, BlockPos p, BlockState s) {
        if (s.getBlock() instanceof StairBlock) return 0.5;
        VoxelShape sh = s.getCollisionShape(w, p);
        if (sh.isEmpty()) sh = s.getShape(w, p);
        if (sh.isEmpty()) return 0.5;
        BlockHitResult hit = sh.clip(new Vec3(p.getX() + 0.5, p.getY() + 1.5, p.getZ() + 0.5), new Vec3(p.getX() + 0.5, p.getY() - 0.01, p.getZ() + 0.5), p);
        double top = hit != null ? hit.getLocation().y - p.getY() : sh.max(Direction.Axis.Y);
        return clamp(top, 0.25, 0.8);
    }

    /** Which way one faces when seated: furniture faces its FACING (the front, toward whoever placed it); stairs, the opposite. */
    private static float yawOf(BlockState s, BlockPos p, ShopZones.Zone z) {
        if (s.getBlock() instanceof StairBlock) return s.getValue(StairBlock.FACING).getOpposite().toYRot();
        if (s.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) return s.getValue(BlockStateProperties.HORIZONTAL_FACING).toYRot();
        Vec3 d = z.core.getCenter().subtract(p.getCenter());
        return (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90.0);
    }

    private static Cache cache(ServerLevel w, ShopZones.Zone z) {
        Cache c = CACHE.get(z.id);
        long now = w.getGameTime();
        if (c != null && now - c.at < REFRESH && now >= c.at) return c;
        c = scan(w, z);
        c.at = now;
        CACHE.put(z.id, c);
        return c;
    }

    /** Forces a shop's list to be recomputed on the next query. */
    public static void invalidate(UUID zone) { CACHE.remove(zone); }

    private static Cache scan(ServerLevel w, ShopZones.Zone z) {
        Cache c = new Cache();
        // the shop's whole real box (also the upper floor, mezzanines and basement): at most every 30 s
        AABB box = z.box();
        int minY = Math.max(w.getMinBuildHeight(), (int) Math.floor(box.minY)), maxY = Math.min(w.getMaxBuildHeight() - 1, (int) Math.ceil(box.maxY) - 1);
        List<Seat> furn = new ArrayList<>(), stairs = new ArrayList<>();
        List<BlockPos> decor = new ArrayList<>();
        List<BlockPos> statues = new ArrayList<>();
        List<BlockPos> tables = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed((int) Math.floor(box.minX), minY, (int) Math.floor(box.minZ), (int) Math.ceil(box.maxX) - 1, maxY, (int) Math.ceil(box.maxZ) - 1)) {
            if (!z.contains(p) || !w.hasChunkAt(p)) continue;
            BlockState s = w.getBlockState(p);
            if (s.isAir()) continue;
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(s.getBlock());
            boolean vanilla = "minecraft".equals(id.getNamespace());
            if (s.getBlock() instanceof dev.tcgshop.luxury.StatueBlock) statues.add(p.immutable());
            if (s.is(TcgBlocks.PLAY_TABLE)) tables.add(p.immutable());
            if (!vanilla && seatWord(id.getPath())) {
                BlockPos ip = p.immutable();
                furn.add(new Seat(ip, ip.getY() + surface(w, ip, s) - THIGH, yawOf(s, ip, z), true));
                if (CobbleFurniesCompat.isFurnitureBlock(id)) decor.add(ip);
            } else if (vanilla && s.getBlock() instanceof StairBlock) {
                if (isBenchStairs(w, p, s)) {
                    BlockPos ip = p.immutable();
                    stairs.add(new Seat(ip, ip.getY() + 0.5 - THIGH, yawOf(s, ip, z), false));
                }
            } else if (!vanilla && (CobbleFurniesCompat.isFurnitureBlock(id) || CobbleFurniesCompat.isPlushBlock(id) || ownDecor(id))) {
                decor.add(p.immutable());
            }
        }
        // stairs only serve as a bench if the shop has no real seat at all
        c.seats = List.copyOf(furn.isEmpty() ? stairs : furn);
        if (decor.size() > 96) { java.util.Collections.shuffle(decor, new java.util.Random(z.id.hashCode())); decor = decor.subList(0, 96); }
        c.decor = List.copyOf(decor);
        c.statues = List.copyOf(statues);
        c.tables = List.copyOf(tables);
        return c;
    }

    /** This mod's own decoration that is also looked at (statues, posters...). */
    private static boolean ownDecor(ResourceLocation id) {
        return dev.tcgshop.TcgShop.MOD_ID.equals(id.getNamespace()) && (id.getPath().contains("statue") || id.getPath().contains("poster"));
    }

    public static List<Seat> seats(ServerLevel w, ShopZones.Zone z) { return cache(w, z).seats; }

    public static List<BlockPos> decor(ServerLevel w, ShopZones.Zone z) { return cache(w, z).decor; }

    /** 1.0: statue bases in the shop (refreshed with the rest of the cache, at most every 30 s). */
    public static List<BlockPos> statues(ServerLevel w, ShopZones.Zone z) { return cache(w, z).statues; }

    /** 1.0: play tables in the shop (same cache and refresh as the seats). */
    public static List<BlockPos> tables(ServerLevel w, ShopZones.Zone z) { return cache(w, z).tables; }

    /**
     * 1.0 (PlayTables): a seat on that chair block facing yaw (for example toward a play table), with the seat height
     * worked out like the shop scan does. Works for furniture seats and stair benches.
     */
    public static Seat seatFacing(ServerLevel w, BlockPos p, float yaw) {
        BlockState s = w.getBlockState(p);
        BlockPos ip = p.immutable();
        return new Seat(ip, ip.getY() + surface(w, ip, s) - THIGH, yaw, !(s.getBlock() instanceof StairBlock));
    }

    // ------------------------------------------------------------------ reservations

    private static Key key(Level w, BlockPos p) { return new Key(w.dimension(), p.asLong()); }

    /** Does that seat's reservation belong to someone who's no longer there? */
    private static boolean stale(ServerLevel w, UUID who) {
        Entity e = w.getEntity(who);
        return e == null || !e.isAlive();
    }

    public static boolean reservedByOther(ServerLevel w, BlockPos p, UUID who) {
        UUID r = RESERVED.get(key(w, p));
        if (r == null || r.equals(who)) return false;
        if (stale(w, r)) { RESERVED.remove(key(w, p)); return false; }
        return true;
    }

    public static boolean reserve(ServerLevel w, BlockPos p, UUID who) {
        if (reservedByOther(w, p, who)) return false;
        release(who);
        RESERVED.put(key(w, p), who);
        return true;
    }

    /** 1.0: has that NPC reserved a seat (on its way to sit or already seated)? */
    public static boolean hasReservation(UUID who) {
        return RESERVED.containsValue(who);
    }

    /** Releases whatever that customer had reserved. */
    public static void release(UUID who) {
        RESERVED.values().removeIf(who::equals);
    }

    /** Free seat: no players or other creatures on it, nobody riding it and with headroom. */
    public static boolean isFree(ServerLevel w, BlockPos p, Entity self) {
        if (w.getBlockState(p.above()).isRedstoneConductor(w, p.above())) return false;
        AABB b = new AABB(p).expandTowards(0, 1, 0).deflate(0.05);
        return w.getEntities(self, b, e -> e.isAlive() && !e.isSpectator() && e != self.getVehicle()
                && (e instanceof LivingEntity || e.isVehicle())).isEmpty();
    }

    /** Spot next to the seat from which one sits down: first in front, then the sides and behind. */
    @Nullable
    public static Vec3 standSpot(ServerLevel w, ShopZones.Zone z, Seat s) {
        Direction front = Direction.fromYRot(s.yaw());
        Direction[] order = {front, front.getClockWise(), front.getCounterClockWise(), front.getOpposite()};
        for (Direction d : order) {
            BlockPos q = s.pos().relative(d);
            if (z.contains(q) && standable(w, q)) return new Vec3(q.getX() + 0.5, q.getY(), q.getZ() + 0.5);
        }
        return null;
    }

    /** Maximum pathfinding searches (A*) per call: called from the customer's tick and from the owner's packet. */
    private static final int MAX_PATH_TRIES = 3;

    /**
     * Finds a free seat it can walk to (closest first, at most 32 blocks away) and
     * reserves it. null if there's none. At most MAX_PATH_TRIES A* per call (they aren't cheap).
     */
    @Nullable
    public static Pick pickSeat(ServerLevel w, ShopZones.Zone z, Mob mob) {
        List<Seat> all = new ArrayList<>(seats(w, z));
        if (all.isEmpty()) return null;
        Vec3 at = mob.position();
        all.removeIf(s -> s.pos().distToCenterSqr(at) > 32 * 32);
        all.sort(Comparator.comparingDouble(s -> s.pos().distToCenterSqr(at)));
        int tries = 0;
        for (Seat s : all) {
            if (tries >= MAX_PATH_TRIES) break;
            if (reservedByOther(w, s.pos(), mob.getUUID()) || !stillSeat(w, s.pos()) || !isFree(w, s.pos(), mob)) continue;
            Vec3 stand = standSpot(w, z, s);
            if (stand == null) continue;
            tries++;
            Path path = mob.getNavigation().createPath(BlockPos.containing(stand), 0);
            if (path == null || !path.canReach()) continue;
            if (!reserve(w, s.pos(), mob.getUUID())) continue;
            return new Pick(s, stand);
        }
        return null;
    }

    /** A nearby piece of furniture or plushie to stop and look at. */
    @Nullable
    public static BlockPos randomDecorNear(ServerLevel w, ShopZones.Zone z, BlockPos from, int radius, RandomSource r) {
        List<BlockPos> near = new ArrayList<>();
        for (BlockPos p : decor(w, z)) if (p.distSqr(from) <= radius * radius) near.add(p);
        return near.isEmpty() ? null : near.get(r.nextInt(near.size()));
    }

    public static boolean isPlushAt(ServerLevel w, BlockPos p) {
        return CobbleFurniesCompat.isPlushBlock(BuiltInRegistries.BLOCK.getKey(w.getBlockState(p).getBlock()));
    }

    // ------------------------------------------------------------------ alarms

    /** Something startles the shop (theft detected...): those sitting get up. */
    public static void alarm(UUID zone, long time) { ALARM.put(zone, time); }

    public static boolean alarmSince(UUID zone, long since) {
        Long t = ALARM.get(zone);
        return t != null && t >= since;
    }

    // ------------------------------------------------------------------ lines

    private static Component line(RandomSource r, String cat, int n) { return Component.translatable("tcgshop.seat." + cat + "." + r.nextInt(n)); }

    public static Component sitLine(RandomSource r, boolean buyer) { return buyer ? line(r, "sit_buyer", SIT_BUYER_LINES) : line(r, "sit", SIT_LINES); }
    public static Component standLine(RandomSource r) { return line(r, "stand", STAND_LINES); }
    public static Component talkLine(RandomSource r) { return line(r, "talk", TALK_LINES); }
    public static Component startledLine(RandomSource r) { return line(r, "startled", STARTLED_LINES); }
    public static Component lookLine(RandomSource r, boolean plush) { return plush ? line(r, "plush", PLUSH_LINES) : line(r, "furniture", FURNITURE_LINES); }
}

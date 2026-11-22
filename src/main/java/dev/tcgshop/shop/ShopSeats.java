package dev.tcgshop.shop;

import dev.tcgshop.compat.CobbleFurniesCompat;
import net.minecraft.block.BlockState;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 0.9.5 · Asientos de la tienda para los clientes: sillas, taburetes, bancos, sofás... de cualquier mod (CobbleFurnies,
 * Lucky's Cozyhome y similares, por el nombre del bloque) y, si no hay ninguno, escaleras colocadas como banco.
 * También guarda los muebles y peluches que los clientes se paran a mirar.
 *
 * Todo es del servidor y sin dependencia de compilación con esos mods. La lista se cachea por tienda y se refresca
 * como mucho cada 30 s (sólo cuando alguien la pide). Cada asiento lo reserva un único cliente.
 */
public final class ShopSeats {
    /** Un asiento: bloque, altura (Y absoluta) a la que va la entidad asiento, hacia dónde se mira y si es un mueble de verdad. */
    public record Seat(BlockPos pos, double seatY, float yaw, boolean furniture) {}

    /** Asiento elegido para un cliente: el asiento y el sitio de al lado desde el que se sienta (y al que vuelve al levantarse). */
    public record Pick(Seat seat, Vec3d stand) {}

    private static final class Cache {
        long at;
        List<Seat> seats = List.of();
        List<BlockPos> decor = List.of();
    }

    private record Key(RegistryKey<World> dim, long pos) {}

    private static final Map<UUID, Cache> CACHE = new HashMap<>();
    private static final Map<Key, UUID> RESERVED = new HashMap<>();
    /** Última alarma (robo detectado...) por tienda: los que están sentados se levantan. */
    private static final Map<UUID, Long> ALARM = new HashMap<>();
    private static final long REFRESH = 20 * 30;
    /** Del suelo de los pies a la parte de abajo de los muslos en la pose de sentado (modelo bípedo a escala 1). */
    private static final double THIGH = 0.62;
    private static final Set<String> SEAT_WORDS = Set.of("chair", "armchair", "stool", "sofa", "couch", "bench", "seat", "loveseat", "ottoman");
    /** Frases (tcgshop.seat.<cat>.<n>, tools/gen_lang_095_seat.py). */
    public static final int SIT_LINES = 8, SIT_BUYER_LINES = 6, STAND_LINES = 6, TALK_LINES = 5, STARTLED_LINES = 4, FURNITURE_LINES = 7, PLUSH_LINES = 7;

    private ShopSeats() {}

    // ------------------------------------------------------------------ configuración (opcional, por reflexión)

    /** ¿Los clientes se sientan? (TcgShopConfig.customerSeats si existe; si no, sí). */
    public static boolean enabled() {
        Object v = cfg("customerSeats");
        return !(v instanceof Boolean b) || b;
    }

    /** Probabilidad de sentarse un rato quien no ha comprado nada (TcgShopConfig.customerSitChance). */
    public static double sitChance() {
        return cfg("customerSitChance") instanceof Number n ? clamp(n.doubleValue(), 0, 1) : 0.45;
    }

    /** Probabilidad de sentarse después de pagar (TcgShopConfig.customerSitChanceBuyer). */
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

    // ------------------------------------------------------------------ detección

    /** ¿El nombre del bloque es de un asiento? (palabra a palabra: "oak_chair", "red_sofa_2", "bar_stools"...). */
    public static boolean seatWord(String path) {
        int slash = path.lastIndexOf('/');
        for (String w : path.substring(slash + 1).split("_")) {
            w = w.replaceAll("\\d+$", "");
            if (SEAT_WORDS.contains(w) || (w.endsWith("s") && SEAT_WORDS.contains(w.substring(0, w.length() - 1)))) return true;
        }
        return false;
    }

    /** ¿Es un asiento de mueble (cualquier mod, también los de este)? */
    public static boolean isFurnitureSeat(BlockState s) {
        if (s.isAir()) return false;
        Identifier id = Registries.BLOCK.getId(s.getBlock());
        return !"minecraft".equals(id.getNamespace()) && seatWord(id.getPath());
    }

    /** Escalera "de banco": mitad de abajo, recta, apoyada en el suelo, con el hueco de delante libre y nada encima. */
    private static boolean isBenchStairs(ServerWorld w, BlockPos p, BlockState s) {
        if (!(s.getBlock() instanceof StairsBlock) || s.get(StairsBlock.HALF) != BlockHalf.BOTTOM) return false;
        if (s.get(StairsBlock.SHAPE) != net.minecraft.block.enums.StairShape.STRAIGHT) return false;
        Direction front = s.get(StairsBlock.FACING).getOpposite();
        BlockPos f = p.offset(front), back = p.offset(front.getOpposite());
        if (!w.getBlockState(p.down()).isSolidBlock(w, p.down())) return false;
        if (!standable(w, f)) return false;
        // si detrás hay otra escalera más alta es una escalera de verdad, no un banco
        return !(w.getBlockState(back.up()).getBlock() instanceof StairsBlock) && !(w.getBlockState(back).getBlock() instanceof StairsBlock && w.getBlockState(back).get(StairsBlock.HALF) == BlockHalf.TOP);
    }

    /** ¿Sigue habiendo un asiento en ese bloque? (lo comprueban el cliente y la entidad asiento). */
    public static boolean stillSeat(ServerWorld w, BlockPos p) {
        if (!w.isChunkLoaded(p)) return false;
        BlockState s = w.getBlockState(p);
        return isFurnitureSeat(s) || s.getBlock() instanceof StairsBlock;
    }

    private static boolean standable(ServerWorld w, BlockPos p) {
        return !w.getBlockState(p).blocksMovement() && !w.getBlockState(p.up()).blocksMovement()
                && w.getBlockState(p.down()).isSolidBlock(w, p.down());
    }

    /** Altura de la superficie del asiento (0-1 dentro del bloque): rayo por el centro del bloque de arriba abajo. */
    private static double surface(ServerWorld w, BlockPos p, BlockState s) {
        if (s.getBlock() instanceof StairsBlock) return 0.5;
        VoxelShape sh = s.getCollisionShape(w, p);
        if (sh.isEmpty()) sh = s.getOutlineShape(w, p);
        if (sh.isEmpty()) return 0.5;
        BlockHitResult hit = sh.raycast(new Vec3d(p.getX() + 0.5, p.getY() + 1.5, p.getZ() + 0.5), new Vec3d(p.getX() + 0.5, p.getY() - 0.01, p.getZ() + 0.5), p);
        double top = hit != null ? hit.getPos().y - p.getY() : sh.getMax(Direction.Axis.Y);
        return clamp(top, 0.25, 0.8);
    }

    /** Hacia dónde se mira sentado: los muebles miran hacia su FACING (el frente, hacia quien lo colocó); las escaleras, al revés. */
    private static float yawOf(BlockState s, BlockPos p, ShopZones.Zone z) {
        if (s.getBlock() instanceof StairsBlock) return s.get(StairsBlock.FACING).getOpposite().asRotation();
        if (s.contains(Properties.HORIZONTAL_FACING)) return s.get(Properties.HORIZONTAL_FACING).asRotation();
        Vec3d d = z.core.toCenterPos().subtract(p.toCenterPos());
        return (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90.0);
    }

    private static Cache cache(ServerWorld w, ShopZones.Zone z) {
        Cache c = CACHE.get(z.id);
        long now = w.getTime();
        if (c != null && now - c.at < REFRESH && now >= c.at) return c;
        c = scan(w, z);
        c.at = now;
        CACHE.put(z.id, c);
        return c;
    }

    /** Fuerza a recalcular la lista de una tienda en la próxima consulta. */
    public static void invalidate(UUID zone) { CACHE.remove(zone); }

    private static Cache scan(ServerWorld w, ShopZones.Zone z) {
        Cache c = new Cache();
        // toda la caja real de la tienda (también el piso de arriba, entreplantas y sótano): como mucho cada 30 s
        Box box = z.box();
        int minY = Math.max(w.getBottomY(), (int) Math.floor(box.minY)), maxY = Math.min(w.getTopY() - 1, (int) Math.ceil(box.maxY) - 1);
        List<Seat> furn = new ArrayList<>(), stairs = new ArrayList<>();
        List<BlockPos> decor = new ArrayList<>();
        for (BlockPos p : BlockPos.iterate((int) Math.floor(box.minX), minY, (int) Math.floor(box.minZ), (int) Math.ceil(box.maxX) - 1, maxY, (int) Math.ceil(box.maxZ) - 1)) {
            if (!z.contains(p) || !w.isChunkLoaded(p)) continue;
            BlockState s = w.getBlockState(p);
            if (s.isAir()) continue;
            Identifier id = Registries.BLOCK.getId(s.getBlock());
            boolean vanilla = "minecraft".equals(id.getNamespace());
            if (!vanilla && seatWord(id.getPath())) {
                BlockPos ip = p.toImmutable();
                furn.add(new Seat(ip, ip.getY() + surface(w, ip, s) - THIGH, yawOf(s, ip, z), true));
                if (CobbleFurniesCompat.isFurnitureBlock(id)) decor.add(ip);
            } else if (vanilla && s.getBlock() instanceof StairsBlock) {
                if (isBenchStairs(w, p, s)) {
                    BlockPos ip = p.toImmutable();
                    stairs.add(new Seat(ip, ip.getY() + 0.5 - THIGH, yawOf(s, ip, z), false));
                }
            } else if (!vanilla && (CobbleFurniesCompat.isFurnitureBlock(id) || CobbleFurniesCompat.isPlushBlock(id) || ownDecor(id))) {
                decor.add(p.toImmutable());
            }
        }
        // las escaleras sólo sirven de banco si la tienda no tiene ningún asiento de verdad
        c.seats = List.copyOf(furn.isEmpty() ? stairs : furn);
        if (decor.size() > 96) { java.util.Collections.shuffle(decor, new java.util.Random(z.id.hashCode())); decor = decor.subList(0, 96); }
        c.decor = List.copyOf(decor);
        return c;
    }

    /** Decoración propia que también se mira (estatuas, pósters...). */
    private static boolean ownDecor(Identifier id) {
        return dev.tcgshop.TcgShop.MOD_ID.equals(id.getNamespace()) && (id.getPath().contains("statue") || id.getPath().contains("poster"));
    }

    public static List<Seat> seats(ServerWorld w, ShopZones.Zone z) { return cache(w, z).seats; }

    public static List<BlockPos> decor(ServerWorld w, ShopZones.Zone z) { return cache(w, z).decor; }

    // ------------------------------------------------------------------ reservas

    private static Key key(World w, BlockPos p) { return new Key(w.getRegistryKey(), p.asLong()); }

    /** ¿La reserva de ese asiento es de alguien que ya no está? */
    private static boolean stale(ServerWorld w, UUID who) {
        Entity e = w.getEntity(who);
        return e == null || !e.isAlive();
    }

    public static boolean reservedByOther(ServerWorld w, BlockPos p, UUID who) {
        UUID r = RESERVED.get(key(w, p));
        if (r == null || r.equals(who)) return false;
        if (stale(w, r)) { RESERVED.remove(key(w, p)); return false; }
        return true;
    }

    public static boolean reserve(ServerWorld w, BlockPos p, UUID who) {
        if (reservedByOther(w, p, who)) return false;
        release(who);
        RESERVED.put(key(w, p), who);
        return true;
    }

    /** Suelta lo que tuviera reservado ese cliente. */
    public static void release(UUID who) {
        RESERVED.values().removeIf(who::equals);
    }

    /** Asiento libre: sin jugadores ni otras criaturas encima, sin nadie montado y con hueco para la cabeza. */
    public static boolean isFree(ServerWorld w, BlockPos p, Entity self) {
        if (w.getBlockState(p.up()).isSolidBlock(w, p.up())) return false;
        Box b = new Box(p).stretch(0, 1, 0).contract(0.05);
        return w.getOtherEntities(self, b, e -> e.isAlive() && !e.isSpectator() && e != self.getVehicle()
                && (e instanceof LivingEntity || e.hasPassengers())).isEmpty();
    }

    /** Sitio de al lado del asiento desde el que se sienta: primero delante, luego a los lados y detrás. */
    @Nullable
    public static Vec3d standSpot(ServerWorld w, ShopZones.Zone z, Seat s) {
        Direction front = Direction.fromRotation(s.yaw());
        Direction[] order = {front, front.rotateYClockwise(), front.rotateYCounterclockwise(), front.getOpposite()};
        for (Direction d : order) {
            BlockPos q = s.pos().offset(d);
            if (z.contains(q) && standable(w, q)) return new Vec3d(q.getX() + 0.5, q.getY(), q.getZ() + 0.5);
        }
        return null;
    }

    /** Búsquedas de camino (A*) como mucho por llamada: se llama desde el tick del cliente y desde el paquete del dueño. */
    private static final int MAX_PATH_TRIES = 3;

    /**
     * Busca un asiento libre al que pueda llegar andando (los más cercanos primero, como mucho a 32 bloques) y se lo
     * reserva. null si no hay ninguno. Como mucho MAX_PATH_TRIES A* por llamada (no son baratos).
     */
    @Nullable
    public static Pick pickSeat(ServerWorld w, ShopZones.Zone z, MobEntity mob) {
        List<Seat> all = new ArrayList<>(seats(w, z));
        if (all.isEmpty()) return null;
        Vec3d at = mob.getPos();
        all.removeIf(s -> s.pos().getSquaredDistance(at) > 32 * 32);
        all.sort(Comparator.comparingDouble(s -> s.pos().getSquaredDistance(at)));
        int tries = 0;
        for (Seat s : all) {
            if (tries >= MAX_PATH_TRIES) break;
            if (reservedByOther(w, s.pos(), mob.getUuid()) || !stillSeat(w, s.pos()) || !isFree(w, s.pos(), mob)) continue;
            Vec3d stand = standSpot(w, z, s);
            if (stand == null) continue;
            tries++;
            Path path = mob.getNavigation().findPathTo(BlockPos.ofFloored(stand), 0);
            if (path == null || !path.reachesTarget()) continue;
            if (!reserve(w, s.pos(), mob.getUuid())) continue;
            return new Pick(s, stand);
        }
        return null;
    }

    /** Un mueble o peluche cercano para pararse a mirarlo. */
    @Nullable
    public static BlockPos randomDecorNear(ServerWorld w, ShopZones.Zone z, BlockPos from, int radius, Random r) {
        List<BlockPos> near = new ArrayList<>();
        for (BlockPos p : decor(w, z)) if (p.getSquaredDistance(from) <= radius * radius) near.add(p);
        return near.isEmpty() ? null : near.get(r.nextInt(near.size()));
    }

    public static boolean isPlushAt(ServerWorld w, BlockPos p) {
        return CobbleFurniesCompat.isPlushBlock(Registries.BLOCK.getId(w.getBlockState(p).getBlock()));
    }

    // ------------------------------------------------------------------ alarmas

    /** Algo asusta a la tienda (robo detectado...): los que están sentados se levantan. */
    public static void alarm(UUID zone, long time) { ALARM.put(zone, time); }

    public static boolean alarmSince(UUID zone, long since) {
        Long t = ALARM.get(zone);
        return t != null && t >= since;
    }

    // ------------------------------------------------------------------ frases

    private static Text line(Random r, String cat, int n) { return Text.translatable("tcgshop.seat." + cat + "." + r.nextInt(n)); }

    public static Text sitLine(Random r, boolean buyer) { return buyer ? line(r, "sit_buyer", SIT_BUYER_LINES) : line(r, "sit", SIT_LINES); }
    public static Text standLine(Random r) { return line(r, "stand", STAND_LINES); }
    public static Text talkLine(Random r) { return line(r, "talk", TALK_LINES); }
    public static Text startledLine(Random r) { return line(r, "startled", STARTLED_LINES); }
    public static Text lookLine(Random r, boolean plush) { return plush ? line(r, "plush", PLUSH_LINES) : line(r, "furniture", FURNITURE_LINES); }
}

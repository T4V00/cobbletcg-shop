package dev.tcgshop.events.incident;

import dev.tcgshop.TcgShop;
import dev.tcgshop.shop.DecorBlocks;
import dev.tcgshop.shop.ShopDisplayBlockEntity;
import dev.tcgshop.shop.ShopLife;
import dev.tcgshop.shop.ShopZones;
import dev.tcgshop.shop.TcgBlocks;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.LightningRodBlock;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.ObserverBlock;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.TargetBlock;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.TripWireBlock;
import net.minecraft.world.level.block.TripWireHookBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.piston.MovingPistonBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Where an incident can go. Only called on PLACEMENT (once per event), never every tick (critique B8/G12).
 *
 * Rules (critique E1): in AIR, inside z.box(), on top of a full solid top face of a block that has
 * no block entity and is not a shop block; and with no neighbor (sides and above) that is redstone, fluid, door,
 * trapdoor, fence gate, rail, pressure plate, button, fire, portal, block entity or tcgshop block (displays, statue
 * pieces, core...). "Near" something = 2 blocks or less but never touching it on a face.
 */
final class IncidentPlacer {
    private IncidentPlacer() {}

    // ------------------------------------------------------------------ anchors

    /** Shop doors (those cached in the recompute; if there has been no recompute yet, they are searched once). */
    static List<BlockPos> doors(ServerLevel w, ShopZones.Zone z) {
        List<BlockPos> d = z.events.doors;
        if (z.events.lastRecompute < 0 || d == null) d = ShopLife.shopDoors(w, z);
        return d == null ? List.of() : d;
    }

    /** The shop's sales displays (block entities in the zone's chunks, as the recompute does). */
    static List<ShopDisplayBlockEntity> displays(ServerLevel w, ShopZones.Zone z) {
        List<ShopDisplayBlockEntity> out = new ArrayList<>();
        for (BlockEntity be : zoneBlockEntities(w, z)) if (be instanceof ShopDisplayBlockEntity d && !d.isHome()) out.add(d);
        return out;
    }

    static List<BlockPos> registers(ServerLevel w, ShopZones.Zone z) {
        List<BlockPos> out = new ArrayList<>();
        for (BlockEntity be : zoneBlockEntities(w, z))
            if (be instanceof DecorBlocks.DecorEntity && be.getBlockState().is(TcgBlocks.CASH_REGISTER)) out.add(be.getBlockPos());
        return out;
    }

    /** Block entities inside the zone, only from already-loaded chunks (never loads chunks). */
    static List<BlockEntity> zoneBlockEntities(ServerLevel w, ShopZones.Zone z) {
        List<BlockEntity> out = new ArrayList<>();
        int half = z.size() / 2;
        int cx0 = (z.core.getX() - half) >> 4, cx1 = (z.core.getX() + half) >> 4, cz0 = (z.core.getZ() - half) >> 4, cz1 = (z.core.getZ() + half) >> 4;
        for (int cx = cx0; cx <= cx1; cx++)
            for (int cz = cz0; cz <= cz1; cz++) {
                LevelChunk ch = w.getChunkSource().getChunkNow(cx, cz);
                if (ch == null) continue;
                for (BlockEntity be : ch.getBlockEntities().values()) if (z.contains(be.getBlockPos())) out.add(be);
            }
        return out;
    }

    /** Anchors by "near": door | outside_door | display | register | core | floor (empty list = random floor). */
    static List<BlockPos> anchors(ServerLevel w, ShopZones.Zone z, String near) {
        return switch (near) {
            case "door", "outside_door" -> doors(w, z);
            case "display" -> {
                List<BlockPos> l = new ArrayList<>();
                for (ShopDisplayBlockEntity d : displays(w, z)) l.add(d.getBlockPos());
                yield l;
            }
            case "register" -> registers(w, z);
            case "core" -> List.of(z.core);
            default -> List.of();
        };
    }

    // ------------------------------------------------------------------ candidates

    /**
     * Valid spots, already shuffled with rng and sorted by preference (inside the building first; outside first if
     * preferOutside). With anchors: around them; without anchors: random sampling of the shop floor.
     */
    static List<BlockPos> candidates(ServerLevel w, ShopZones.Zone z, List<BlockPos> anchors, boolean preferOutside, Random rng, int want) {
        Set<BlockPos> found = new LinkedHashSet<>();
        if (!anchors.isEmpty()) {
            List<BlockPos> a = new ArrayList<>(anchors);
            Collections.shuffle(a, rng);
            for (BlockPos an : a) {
                for (int dy = -1; dy <= 1; dy++)
                    for (int dx = -2; dx <= 2; dx++)
                        for (int dz = -2; dz <= 2; dz++) {
                            if (Math.abs(dx) + Math.abs(dz) <= 1 && dy == 0) continue; // never touching the anchor on a face
                            BlockPos p = an.offset(dx, dy, dz);
                            if (safe(w, z, p)) found.add(p.immutable());
                        }
                if (found.size() >= want * 6) break;
            }
        } else {
            int half = z.size() / 2, size = half * 2 + 1;
            for (int i = 0; i < 160 && found.size() < want * 6; i++) {
                int x = z.core.getX() - half + rng.nextInt(size), zz = z.core.getZ() - half + rng.nextInt(size);
                for (int y = z.core.getY() + 2; y >= z.core.getY() - ShopZones.BELOW + 1; y--) {
                    BlockPos p = new BlockPos(x, y, zz);
                    if (safe(w, z, p)) { found.add(p); break; }
                }
            }
        }
        List<BlockPos> in = new ArrayList<>(), out = new ArrayList<>();
        for (BlockPos p : found) (w.canSeeSky(p) ? out : in).add(p);
        Collections.shuffle(in, rng);
        Collections.shuffle(out, rng);
        List<BlockPos> all = new ArrayList<>(preferOutside ? out : in);
        all.addAll(preferOutside ? in : out);
        return all;
    }

    /** Can an incident be placed here without touching anything of the player's? */
    static boolean safe(ServerLevel w, ShopZones.Zone z, BlockPos p) {
        if (!z.contains(p) || !w.hasChunk(p.getX() >> 4, p.getZ() >> 4)) return false;
        if (!w.getBlockState(p).isAir()) return false;
        BlockPos d = p.below();
        BlockState floor = w.getBlockState(d);
        if (!floor.isFaceSturdy(w, d, Direction.UP) || floor.hasBlockEntity() || floor.isSignalSource() || isOurs(floor)
                || !floor.getFluidState().isEmpty() || floor.getBlock() instanceof TntBlock || floor.getBlock() instanceof RedstoneLampBlock)
            return false;
        for (Direction dir : Direction.values()) {
            if (dir == Direction.DOWN) continue;
            BlockPos n = p.relative(dir);
            // neighbor in another unloaded chunk (zone edge): not read (loading it would stall the main thread)
            if (!w.hasChunk(n.getX() >> 4, n.getZ() >> 4)) return false;
            if (bad(w.getBlockState(n))) return false;
        }
        return true;
    }

    private static boolean isOurs(BlockState s) {
        return BuiltInRegistries.BLOCK.getKey(s.getBlock()).getNamespace().equals(TcgShop.MOD_ID);
    }

    /** Forbidden neighbor: redstone, fluids, doors, block entities, fire, tcgshop pieces (including another incident). */
    static boolean bad(BlockState n) {
        if (n.isAir()) return false;
        if (!n.getFluidState().isEmpty() || n.hasBlockEntity() || n.isSignalSource()) return true;
        if (n.is(BlockTags.DOORS) || n.is(BlockTags.TRAPDOORS) || n.is(BlockTags.FENCE_GATES) || n.is(BlockTags.RAILS)
                || n.is(BlockTags.PRESSURE_PLATES) || n.is(BlockTags.BUTTONS) || n.is(BlockTags.FIRE) || n.is(BlockTags.CAMPFIRES)
                || n.is(BlockTags.PORTALS)) return true;
        Block b = n.getBlock();
        if (b instanceof RedStoneWireBlock || b instanceof DiodeBlock || b instanceof ObserverBlock || b instanceof PistonBaseBlock
                || b instanceof PistonHeadBlock || b instanceof MovingPistonBlock || b instanceof TripWireBlock || b instanceof TripWireHookBlock
                || b instanceof NoteBlock || b instanceof TntBlock || b instanceof RedstoneLampBlock || b instanceof TargetBlock
                || b instanceof LightningRodBlock || b instanceof LeverBlock) return true;
        return isOurs(n);
    }

    // ------------------------------------------------------------------ utilities

    /** The closest to p within maxDist (or null). */
    static BlockPos nearest(BlockPos p, List<BlockPos> list, double maxDist) {
        BlockPos best = null;
        double bd = maxDist * maxDist;
        for (BlockPos q : list) {
            double d = q.distSqr(p);
            if (d <= bd) { bd = d; best = q; }
        }
        return best;
    }
}

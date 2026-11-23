package dev.tcgshop.events.incident;

import dev.tcgshop.TcgShop;
import dev.tcgshop.shop.DecorBlocks;
import dev.tcgshop.shop.ShopDisplayBlockEntity;
import dev.tcgshop.shop.ShopLife;
import dev.tcgshop.shop.ShopZones;
import dev.tcgshop.shop.TcgBlocks;
import net.minecraft.block.AbstractRedstoneGateBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.LeverBlock;
import net.minecraft.block.LightningRodBlock;
import net.minecraft.block.NoteBlock;
import net.minecraft.block.ObserverBlock;
import net.minecraft.block.PistonBlock;
import net.minecraft.block.PistonExtensionBlock;
import net.minecraft.block.PistonHeadBlock;
import net.minecraft.block.RedstoneLampBlock;
import net.minecraft.block.RedstoneWireBlock;
import net.minecraft.block.TargetBlock;
import net.minecraft.block.TntBlock;
import net.minecraft.block.TripwireBlock;
import net.minecraft.block.TripwireHookBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

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
    static List<BlockPos> doors(ServerWorld w, ShopZones.Zone z) {
        List<BlockPos> d = z.events.doors;
        if (z.events.lastRecompute < 0 || d == null) d = ShopLife.shopDoors(w, z);
        return d == null ? List.of() : d;
    }

    /** The shop's sales displays (block entities in the zone's chunks, as the recompute does). */
    static List<ShopDisplayBlockEntity> displays(ServerWorld w, ShopZones.Zone z) {
        List<ShopDisplayBlockEntity> out = new ArrayList<>();
        for (BlockEntity be : zoneBlockEntities(w, z)) if (be instanceof ShopDisplayBlockEntity d && !d.isHome()) out.add(d);
        return out;
    }

    static List<BlockPos> registers(ServerWorld w, ShopZones.Zone z) {
        List<BlockPos> out = new ArrayList<>();
        for (BlockEntity be : zoneBlockEntities(w, z))
            if (be instanceof DecorBlocks.DecorEntity && be.getCachedState().isOf(TcgBlocks.CASH_REGISTER)) out.add(be.getPos());
        return out;
    }

    /** Block entities inside the zone, only from already-loaded chunks (never loads chunks). */
    static List<BlockEntity> zoneBlockEntities(ServerWorld w, ShopZones.Zone z) {
        List<BlockEntity> out = new ArrayList<>();
        int half = z.size() / 2;
        int cx0 = (z.core.getX() - half) >> 4, cx1 = (z.core.getX() + half) >> 4, cz0 = (z.core.getZ() - half) >> 4, cz1 = (z.core.getZ() + half) >> 4;
        for (int cx = cx0; cx <= cx1; cx++)
            for (int cz = cz0; cz <= cz1; cz++) {
                WorldChunk ch = w.getChunkManager().getWorldChunk(cx, cz);
                if (ch == null) continue;
                for (BlockEntity be : ch.getBlockEntities().values()) if (z.contains(be.getPos())) out.add(be);
            }
        return out;
    }

    /** Anchors by "near": door | outside_door | display | register | core | floor (empty list = random floor). */
    static List<BlockPos> anchors(ServerWorld w, ShopZones.Zone z, String near) {
        return switch (near) {
            case "door", "outside_door" -> doors(w, z);
            case "display" -> {
                List<BlockPos> l = new ArrayList<>();
                for (ShopDisplayBlockEntity d : displays(w, z)) l.add(d.getPos());
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
    static List<BlockPos> candidates(ServerWorld w, ShopZones.Zone z, List<BlockPos> anchors, boolean preferOutside, Random rng, int want) {
        Set<BlockPos> found = new LinkedHashSet<>();
        if (!anchors.isEmpty()) {
            List<BlockPos> a = new ArrayList<>(anchors);
            Collections.shuffle(a, rng);
            for (BlockPos an : a) {
                for (int dy = -1; dy <= 1; dy++)
                    for (int dx = -2; dx <= 2; dx++)
                        for (int dz = -2; dz <= 2; dz++) {
                            if (Math.abs(dx) + Math.abs(dz) <= 1 && dy == 0) continue; // never touching the anchor on a face
                            BlockPos p = an.add(dx, dy, dz);
                            if (safe(w, z, p)) found.add(p.toImmutable());
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
        for (BlockPos p : found) (w.isSkyVisible(p) ? out : in).add(p);
        Collections.shuffle(in, rng);
        Collections.shuffle(out, rng);
        List<BlockPos> all = new ArrayList<>(preferOutside ? out : in);
        all.addAll(preferOutside ? in : out);
        return all;
    }

    /** Can an incident be placed here without touching anything of the player's? */
    static boolean safe(ServerWorld w, ShopZones.Zone z, BlockPos p) {
        if (!z.contains(p) || !w.isChunkLoaded(p.getX() >> 4, p.getZ() >> 4)) return false;
        if (!w.getBlockState(p).isAir()) return false;
        BlockPos d = p.down();
        BlockState floor = w.getBlockState(d);
        if (!floor.isSideSolidFullSquare(w, d, Direction.UP) || floor.hasBlockEntity() || floor.emitsRedstonePower() || isOurs(floor)
                || !floor.getFluidState().isEmpty() || floor.getBlock() instanceof TntBlock || floor.getBlock() instanceof RedstoneLampBlock)
            return false;
        for (Direction dir : Direction.values()) {
            if (dir == Direction.DOWN) continue;
            BlockPos n = p.offset(dir);
            // neighbor in another unloaded chunk (zone edge): not read (loading it would stall the main thread)
            if (!w.isChunkLoaded(n.getX() >> 4, n.getZ() >> 4)) return false;
            if (bad(w.getBlockState(n))) return false;
        }
        return true;
    }

    private static boolean isOurs(BlockState s) {
        return Registries.BLOCK.getId(s.getBlock()).getNamespace().equals(TcgShop.MOD_ID);
    }

    /** Forbidden neighbor: redstone, fluids, doors, block entities, fire, tcgshop pieces (including another incident). */
    static boolean bad(BlockState n) {
        if (n.isAir()) return false;
        if (!n.getFluidState().isEmpty() || n.hasBlockEntity() || n.emitsRedstonePower()) return true;
        if (n.isIn(BlockTags.DOORS) || n.isIn(BlockTags.TRAPDOORS) || n.isIn(BlockTags.FENCE_GATES) || n.isIn(BlockTags.RAILS)
                || n.isIn(BlockTags.PRESSURE_PLATES) || n.isIn(BlockTags.BUTTONS) || n.isIn(BlockTags.FIRE) || n.isIn(BlockTags.CAMPFIRES)
                || n.isIn(BlockTags.PORTALS)) return true;
        Block b = n.getBlock();
        if (b instanceof RedstoneWireBlock || b instanceof AbstractRedstoneGateBlock || b instanceof ObserverBlock || b instanceof PistonBlock
                || b instanceof PistonHeadBlock || b instanceof PistonExtensionBlock || b instanceof TripwireBlock || b instanceof TripwireHookBlock
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
            double d = q.getSquaredDistance(p);
            if (d <= bd) { bd = d; best = q; }
        }
        return best;
    }
}

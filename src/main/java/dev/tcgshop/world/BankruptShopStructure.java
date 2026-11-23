package dev.tcgshop.world;

import com.mojang.serialization.MapCodec;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.structure.StructureSet;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.ChunkRandom;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeCoords;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.gen.structure.Structure;
import net.minecraft.world.gen.structure.StructureType;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * "Bankrupt Card Shop" (1.0): a small abandoned card shop, built in code by {@link BankruptShopPiece}.
 *
 * <p>Placement. The structure set (data/tcgshop/worldgen/structure_set/bankrupt_card_shop.json, random_spread)
 * proposes one candidate chunk per region. For each candidate this class looks for a village start of the
 * configured structure sets ({@link WorldLootConfig#villageStructureSets}, "minecraft:villages" by default) within
 * {@link #VILLAGE_SEARCH} blocks, computed from the village set's own random_spread placement and checked against
 * the biome at the village spot (so it is a village that really generates, as long as its jigsaw succeeds).
 * <ul>
 *   <li>Village found: with {@link WorldLootConfig#nearVillageChance} the shop is moved to the village's outskirts,
 *   {@code villageMinDistance..villageMaxDistance} blocks from the village start, on the side of the candidate
 *   chunk (up to 6 tries for flat, dry ground).</li>
 *   <li>No village: with {@link WorldLootConfig#standaloneChance} a lonely shop is built at the candidate chunk.</li>
 * </ul>
 * The shop always stays within {@link #MAX_OFFSET} blocks of the start chunk (structure references reach 8 chunks).
 * The biome tag tcgshop:has_structure/bankrupt_card_shop is checked by vanilla at the final position.
 * Note: /locate points at the start chunk, which can be up to ~6 chunks from the actual building.
 */
public class BankruptShopStructure extends Structure {
    public static final MapCodec<BankruptShopStructure> CODEC = createCodec(BankruptShopStructure::new);
    /** Max distance (blocks) from the candidate chunk to a village start that still counts as "nearby". */
    static final int VILLAGE_SEARCH = 112;
    /** Max offset (blocks, per axis) from the candidate chunk's center to the shop's center. */
    static final int MAX_OFFSET = 96;
    private static final int TRIES = 6;

    public BankruptShopStructure(Config config) {
        super(config);
    }

    @Override
    public StructureType<?> getType() {
        return WorldLoot.BANKRUPT_SHOP_TYPE;
    }

    @Override
    protected Optional<StructurePosition> getStructurePosition(Context ctx) {
        WorldLootConfig c = WorldLootConfig.INSTANCE;
        if (!c.bankruptShopEnabled) return Optional.empty();
        ChunkRandom rnd = ctx.random();
        ChunkPos cp = ctx.chunkPos();
        int cx = cp.getCenterX(), cz = cp.getCenterZ();
        BlockPos village = nearestVillage(ctx, cx, cz);
        if (village != null) {
            if (rnd.nextDouble() >= c.nearVillageChance) return Optional.empty();
            // towards the candidate chunk (or any side if the candidate is the village chunk itself)
            double base = (cx == village.getX() && cz == village.getZ()) ? rnd.nextDouble() * Math.PI * 2
                    : Math.atan2(cz - village.getZ(), cx - village.getX());
            for (int i = 0; i < TRIES; i++) {
                double ang = base + (rnd.nextDouble() - 0.5) * (Math.PI * 2 / 3); // +-60 degrees
                int dist = c.villageMinDistance + rnd.nextInt(Math.max(1, c.villageMaxDistance - c.villageMinDistance + 1));
                int x = village.getX() + (int) Math.round(Math.cos(ang) * dist);
                int z = village.getZ() + (int) Math.round(Math.sin(ang) * dist);
                if (Math.abs(x - cx) > MAX_OFFSET || Math.abs(z - cz) > MAX_OFFSET) continue;
                Integer y = flatGround(ctx, x, z, c.maxSlope);
                if (y != null) return Optional.of(position(rnd, x, y, z));
            }
            return Optional.empty();
        }
        if (rnd.nextDouble() >= c.standaloneChance) return Optional.empty();
        Integer y = flatGround(ctx, cx, cz, c.maxSlope);
        return y == null ? Optional.empty() : Optional.of(position(rnd, cx, y, cz));
    }

    /** Shop centered on (x, z) with its floor at y; orientation and the layout seed are fixed here. */
    private static StructurePosition position(ChunkRandom rnd, int x, int y, int z) {
        Direction facing = Direction.Type.HORIZONTAL.random(rnd);
        long seed = rnd.nextLong();
        return new StructurePosition(new BlockPos(x, y, z), collector ->
                collector.addPiece(new BankruptShopPiece(seed, x - BankruptShopPiece.W / 2, y, z - BankruptShopPiece.D / 2, facing)));
    }

    /**
     * Floor level (y of the ground block) if the 11x12 footprint around (x, z) is dry and flat enough, else null.
     * Samples the center and the four corners with the noise-based heightmaps (no chunk needed).
     */
    @Nullable
    private static Integer flatGround(Context ctx, int x, int z, int maxSlope) {
        ChunkGenerator gen = ctx.chunkGenerator();
        int[][] pts = {{0, 0}, {-6, -6}, {6, -6}, {-6, 6}, {6, 6}};
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE, sum = 0;
        for (int[] p : pts) {
            int floor = gen.getHeight(x + p[0], z + p[1], Heightmap.Type.OCEAN_FLOOR_WG, ctx.world(), ctx.noiseConfig());
            int surface = gen.getHeight(x + p[0], z + p[1], Heightmap.Type.WORLD_SURFACE_WG, ctx.world(), ctx.noiseConfig());
            if (surface > floor) return null; // water (or lava) on top
            if (floor <= gen.getSeaLevel() - 2) return null; // dry pit far below the sea: odd spot for a shop
            min = Math.min(min, floor);
            max = Math.max(max, floor);
            sum += floor;
        }
        if (max - min > maxSlope) return null;
        return Math.round(sum / (float) pts.length) - 1; // getHeight = first free block above the ground
    }

    /** Center of the closest village start within VILLAGE_SEARCH blocks of (x, z), or null. */
    @Nullable
    private static BlockPos nearestVillage(Context ctx, int x, int z) {
        Registry<StructureSet> sets = ctx.dynamicRegistryManager().getOptional(RegistryKeys.STRUCTURE_SET).orElse(null);
        if (sets == null) return null;
        BlockPos best = null;
        long bestD = (long) VILLAGE_SEARCH * VILLAGE_SEARCH;
        for (String key : WorldLootConfig.INSTANCE.villageStructureSets) {
            Identifier id = key == null ? null : Identifier.tryParse(key);
            StructureSet set = id == null ? null : sets.get(id);
            if (set == null || !(set.placement() instanceof RandomSpreadStructurePlacement placement)) continue;
            int spacing = Math.max(1, placement.getSpacing());
            int rx = Math.floorDiv(x >> 4, spacing), rz = Math.floorDiv(z >> 4, spacing);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    ChunkPos start = placement.getStartChunk(ctx.seed(), (rx + dx) * spacing, (rz + dz) * spacing);
                    int vx = start.getCenterX(), vz = start.getCenterZ();
                    long d2 = (long) (vx - x) * (vx - x) + (long) (vz - z) * (vz - z);
                    if (d2 > bestD) continue;
                    if (!villageBiome(ctx, set, vx, vz)) continue;
                    best = new BlockPos(vx, 0, vz);
                    bestD = d2;
                }
            }
        }
        return best;
    }

    /** Would one of the set's structures accept the biome at that spot? (what vanilla checks before building it) */
    private static boolean villageBiome(Context ctx, StructureSet set, int x, int z) {
        int y = ctx.chunkGenerator().getHeightInGround(x, z, Heightmap.Type.WORLD_SURFACE_WG, ctx.world(), ctx.noiseConfig());
        RegistryEntry<Biome> biome = ctx.biomeSource().getBiome(BiomeCoords.fromBlock(x), BiomeCoords.fromBlock(y), BiomeCoords.fromBlock(z),
                ctx.noiseConfig().getMultiNoiseSampler());
        for (StructureSet.WeightedEntry e : set.structures())
            if (e.structure().value().getValidBiomes().contains(biome)) return true;
        return false;
    }
}

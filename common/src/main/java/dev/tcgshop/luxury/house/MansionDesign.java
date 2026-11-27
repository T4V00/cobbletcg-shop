package dev.tcgshop.luxury.house;

import dev.masterball.api.platform.Platform;
import dev.tcgshop.luxury.LuxuryBlocks;
import dev.tcgshop.luxury.StatueTier;
import dev.tcgshop.shop.TcgBlocks;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

/**
 * Procedural design of the MasterBall Mansion estate (100x100), without NBT files. LOCAL coordinates: x 0..99 from
 * west to east, z 0..99 from north to south (the gate is at z = 0 and faces north), y relative to the ground (y = -1 is the grass
 * layer; you walk at y = 0). Oriented blocks are stored facing local directions and
 * {@link MansionBuilder} rotates them according to {@link MansionPlan}.
 * <p>
 * Two layers:
 *  · structure ({@link #cells}): walls, floors, roof, paths... placed bottom to top;
 *  · decoration ({@link #decor}, in insertion order): furniture, carpets, lanterns, plants, water, beds, doors...
 *    placed afterwards, once everything that supports them exists.
 * CobbleFurnies furniture, Pokéblocks plushies and Cobblemon blocks by registry id: if the mod is missing, a
 * vanilla equivalent is used (or the piece is skipped). No copyrighted art: only blocks from the installed mods.
 */
public final class MansionDesign {
    public static final int W = 100, D = 100, Y_MIN = -8, Y_MAX = 40, H = Y_MAX - Y_MIN + 1;
    /** Special values of {@link #cells}: don't touch (subsoil) and air (cleared). */
    public static final short KEEP = 0, AIR = 1;

    /** A decoration piece. hook = call Block.onPlaced (multi-block statues and displays). */
    public record Deco(int x, int y, int z, BlockState state, boolean hook) {}

    /** Resident Pokémon: species, local position, level, radius it may wander and whether it stays still (no AI). */
    public record ResidentSpec(String species, double x, double y, double z, int level, int radius, boolean still) {}

    final short[] cells = new short[W * D * H];
    final List<BlockState> palette = new ArrayList<>();
    private final Map<BlockState, Short> index = new HashMap<>();
    private final LinkedHashMap<Integer, Deco> decoMap = new LinkedHashMap<>();
    public final List<ResidentSpec> residents = new ArrayList<>();

    public final boolean furnies, cobblemon, plush;
    private final Random rnd;

    // ------------------------------------------------------------------ base palette
    private static final BlockState GRASS = Blocks.GRASS_BLOCK.defaultBlockState();
    private static final BlockState QUARTZ = Blocks.QUARTZ_BLOCK.defaultBlockState();
    private static final BlockState SMOOTH_QUARTZ = Blocks.SMOOTH_QUARTZ.defaultBlockState();
    private static final BlockState QUARTZ_BRICKS = Blocks.QUARTZ_BRICKS.defaultBlockState();
    private static final BlockState PILLAR = Blocks.QUARTZ_PILLAR.defaultBlockState();
    private static final BlockState CHISELED = Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState();
    private static final BlockState STONE_BRICKS = Blocks.STONE_BRICKS.defaultBlockState();
    private static final BlockState MOSSY = Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
    private static final BlockState RED = Blocks.RED_CONCRETE.defaultBlockState();
    private static final BlockState WHITE = Blocks.WHITE_CONCRETE.defaultBlockState();
    private static final BlockState BLACK = Blocks.BLACK_CONCRETE.defaultBlockState();
    private static final BlockState ROOF = Blocks.RED_NETHER_BRICKS.defaultBlockState();
    private static final BlockState ROOF_STAIRS = Blocks.RED_NETHER_BRICK_STAIRS.defaultBlockState();
    private static final BlockState DARK_PLANKS = Blocks.DARK_OAK_PLANKS.defaultBlockState();
    private static final BlockState SPRUCE_PLANKS = Blocks.SPRUCE_PLANKS.defaultBlockState();
    private static final BlockState PATH = Blocks.POLISHED_DIORITE.defaultBlockState();
    private static final BlockState PATH_EDGE = Blocks.POLISHED_ANDESITE.defaultBlockState();
    private static final BlockState GLASS = Blocks.GLASS_PANE.defaultBlockState();
    private static final BlockState FENCE = Blocks.DARK_OAK_FENCE.defaultBlockState();
    private static final BlockState WATER = Blocks.WATER.defaultBlockState();
    private static final BlockState LANTERN = Blocks.LANTERN.defaultBlockState();
    private static final BlockState HANGING = Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
    private static final BlockState BLACKSTONE = Blocks.POLISHED_BLACKSTONE.defaultBlockState();

    private static final String F = "cobblefurnies:", PB = "pokeblocks:", CM = "cobblemon:";

    private MansionDesign(long seed) {
        this.furnies = Platform.INSTANCE.isModLoaded("cobblefurnies");
        this.cobblemon = Platform.INSTANCE.isModLoaded("cobblemon");
        this.plush = Platform.INSTANCE.isModLoaded("pokeblocks");
        this.rnd = new Random(seed);
        palette.add(null); // KEEP
        palette.add(Blocks.AIR.defaultBlockState()); // AIR
        index.put(Blocks.AIR.defaultBlockState(), AIR);
    }

    /** Generates the whole estate (deterministic for a seed). */
    public static MansionDesign generate(long seed) {
        MansionDesign d = new MansionDesign(seed);
        d.build();
        return d;
    }

    // ------------------------------------------------------------------ access (MansionBuilder)

    public static int idx(int x, int y, int z) {
        return ((y - Y_MIN) * D + z) * W + x;
    }

    public short cell(int x, int y, int z) {
        return cells[idx(x, y, z)];
    }

    @Nullable
    public BlockState stateOf(short v) {
        return v <= AIR ? (v == AIR ? palette.get(AIR) : null) : palette.get(v);
    }

    public Collection<Deco> decor() {
        return decoMap.values();
    }

    /** Structure blocks (not counting air or subsoil) + decoration pieces: for the dry run. */
    public int blockCount() {
        int n = 0;
        for (short c : cells) if (c > AIR) n++;
        return n + decoMap.size();
    }

    // ------------------------------------------------------------------ block utilities

    @Nullable
    static BlockState block(String id) {
        ResourceLocation i = ResourceLocation.tryParse(id.contains(":") ? id : "minecraft:" + id);
        if (i == null || !BuiltInRegistries.BLOCK.containsKey(i)) return null;
        Block b = BuiltInRegistries.BLOCK.get(i);
        return b == Blocks.AIR ? null : b.defaultBlockState();
    }

    /** Sets a property by name and text value (if the block has it and the value is valid). */
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Nullable
    static BlockState prop(@Nullable BlockState s, String name, String value) {
        if (s == null) return null;
        for (Property<?> p : s.getProperties()) {
            if (!p.getName().equals(name)) continue;
            Optional<?> v = p.getValue(value);
            if (v.isPresent()) return s.setValue((Property) p, (Comparable) v.get());
        }
        return s;
    }

    /** Orients a block ("facing" property, if it has one). */
    @Nullable
    static BlockState face(@Nullable BlockState s, Direction d) {
        return prop(s, "facing", d.getSerializedName());
    }

    /** Maximum value of an integer property (e.g. an apricorn's age: ripe). */
    @Nullable
    static BlockState maxInt(@Nullable BlockState s, String name) {
        if (s == null) return null;
        for (Property<?> p : s.getProperties())
            if (p instanceof IntegerProperty ip && p.getName().equals(name)) {
                int max = ip.getPossibleValues().stream().mapToInt(Integer::intValue).max().orElse(0);
                return s.setValue(ip, max);
            }
        return s;
    }

    /** CobbleFurnies furniture (if the mod is present) or the vanilla equivalent (may be null = nothing is placed). */
    @Nullable
    private BlockState furn(String path, @Nullable BlockState fallback) {
        if (furnies) {
            BlockState s = block(F + path);
            if (s != null) return s;
        }
        return fallback;
    }

    @Nullable
    private BlockState cob(String path, @Nullable BlockState fallback) {
        if (cobblemon) {
            BlockState s = block(CM + path);
            if (s != null) return s;
        }
        return fallback;
    }

    @Nullable
    private BlockState doll(String species) {
        return plush ? block(PB + "pokedoll_" + species) : null;
    }

    private static BlockState stairs(BlockState s, Direction tall) {
        return s.setValue(StairBlock.FACING, tall).setValue(StairBlock.HALF, Half.BOTTOM);
    }

    private static BlockState slab(BlockState s, SlabType t) {
        return s.setValue(SlabBlock.TYPE, t);
    }

    private static BlockState leaves(BlockState s) {
        return s.hasProperty(LeavesBlock.PERSISTENT) ? s.setValue(LeavesBlock.PERSISTENT, true) : prop(s, "persistent", "true");
    }

    // ------------------------------------------------------------------ canvas writing

    private short id(BlockState s) {
        Short v = index.get(s);
        if (v != null) return v;
        short n = (short) palette.size();
        palette.add(s);
        index.put(s, n);
        return n;
    }

    private static boolean in(int x, int y, int z) {
        return x >= 0 && x < W && z >= 0 && z < D && y >= Y_MIN && y <= Y_MAX;
    }

    private void set(int x, int y, int z, @Nullable BlockState s) {
        if (s == null || !in(x, y, z)) return;
        int i = idx(x, y, z);
        cells[i] = s.isAir() ? AIR : id(s);
        decoMap.remove(i); // structure overrides an earlier decoration in the same spot
    }

    private void air(int x, int y, int z) {
        if (in(x, y, z)) {
            cells[idx(x, y, z)] = AIR;
            decoMap.remove(idx(x, y, z));
        }
    }

    private void fill(int x0, int y0, int z0, int x1, int y1, int z1, @Nullable BlockState s) {
        if (s == null) return;
        for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++)
            for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++)
                for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) set(x, y, z, s);
    }

    private void fillAir(int x0, int y0, int z0, int x1, int y1, int z1) {
        for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++)
            for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++)
                for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) air(x, y, z);
    }

    /** Decoration (null = nothing). The spot is left as air in the structure; if there was already decoration there, it is replaced. */
    private void deco(int x, int y, int z, @Nullable BlockState s) {
        deco(x, y, z, s, false);
    }

    private void deco(int x, int y, int z, @Nullable BlockState s, boolean hook) {
        if (s == null || !in(x, y, z)) return;
        int i = idx(x, y, z);
        cells[i] = AIR;
        decoMap.put(i, new Deco(x, y, z, s, hook));
    }

    private boolean hasDeco(int x, int y, int z) {
        return in(x, y, z) && decoMap.containsKey(idx(x, y, z));
    }

    private boolean isAir(int x, int y, int z) {
        return in(x, y, z) && cells[idx(x, y, z)] == AIR && !hasDeco(x, y, z);
    }

    private boolean solidAt(int x, int y, int z) {
        return in(x, y, z) && cells[idx(x, y, z)] > AIR;
    }

    /** Two-tall block (tall plant, PC...): lower and upper half with the "half"/"part" property. */
    private void deco2(int x, int y, int z, @Nullable BlockState s, String prop, String lower, String upper) {
        if (s == null) return;
        deco(x, y, z, prop(s, prop, lower));
        deco(x, y + 1, z, prop(s, prop, upper));
    }

    /** Seat: furniture facing front or, without CobbleFurnies, wooden stairs (which sit the other way round). */
    private void seat(int x, int y, int z, String furniesId, BlockState stairsFallback, Direction front) {
        BlockState f = furnies ? block(F + furniesId) : null;
        deco(x, y, z, f != null ? face(f, front) : stairs(stairsFallback, front.getOpposite()));
    }

    /** Oriented furniture (or its equivalent). */
    private void piece(int x, int y, int z, String furniesId, @Nullable BlockState fallback, Direction front) {
        deco(x, y, z, face(furn(furniesId, fallback), front));
    }

    private void table(int x0, int z0, int x1, int z1, int y, String furniesId) {
        BlockState t = furn(furniesId, slab(Blocks.DARK_OAK_SLAB.defaultBlockState(), SlabType.TOP));
        for (int z = z0; z <= z1; z++) for (int x = x0; x <= x1; x++) deco(x, y, z, t);
    }

    private void lamp(int x, int y, int z, String color) {
        BlockState l = furn(color + "_lamp", null);
        if (l != null) deco(x, y, z, prop(prop(l, "mount_type", "floor"), "lit", "true"));
        else deco(x, y, z, LANTERN);
    }

    private void plant(int x, int y, int z) {
        BlockState p = furn("potted_pothos", Blocks.POTTED_FERN.defaultBlockState());
        deco(x, y, z, face(p, Direction.SOUTH));
    }

    private void tallPlant(int x, int y, int z, String furniesId) {
        BlockState p = furnies ? block(F + furniesId) : null;
        if (p != null) deco2(x, y, z, p, "half", "lower", "upper");
        else deco(x, y, z, Blocks.POTTED_AZALEA.defaultBlockState());
    }

    private void bed(int x, int y, int footZ, Direction towardHead, BlockState bed) {
        int hx = x + towardHead.getStepX(), hz = footZ + towardHead.getStepZ();
        deco(x, y, footZ, bed.setValue(BedBlock.FACING, towardHead).setValue(BedBlock.PART, BedPart.FOOT));
        deco(hx, y, hz, bed.setValue(BedBlock.FACING, towardHead).setValue(BedBlock.PART, BedPart.HEAD));
    }

    private void door(int x, int y, int z, Direction facing, DoorHingeSide hinge) {
        BlockState d = Blocks.DARK_OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, facing).setValue(DoorBlock.HINGE, hinge);
        deco(x, y, z, d.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        deco(x, y + 1, z, d.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
    }

    private void carpet(int x0, int z0, int x1, int z1, int y, String inner, String border) {
        for (int z = z0; z <= z1; z++)
            for (int x = x0; x <= x1; x++) {
                boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
                String c = edge ? border : inner;
                BlockState s = furn(c + "_poke_wool_carpet", block(c + "_carpet"));
                if (isAir(x, y, z)) deco(x, y, z, s);
            }
    }

    /** Lanterns hanging from the ceiling in a grid (y = lantern height, right under the ceiling). */
    private void hangGrid(int x0, int z0, int x1, int z1, int y, int step) {
        for (int z = z0 + step / 2; z <= z1; z += step)
            for (int x = x0 + step / 2; x <= x1; x += step)
                if (isAir(x, y, z) && solidAt(x, y + 1, z)) deco(x, y, z, HANGING);
    }

    /** Color of the Poké Ball motif at point (du, dv) of a disc of radius r (dv < 0 = red half). null = outside. */
    @Nullable
    private static BlockState ball(double du, double dv, double r) {
        double d = Math.sqrt(du * du + dv * dv);
        if (d > r) return null;
        if (d > r - 0.9) return BLACK;
        double btn = Math.max(1.3, r * 0.34);
        if (d <= btn * 0.55) return WHITE;
        if (d <= btn) return BLACK;
        if (Math.abs(dv) <= Math.max(0.6, r * 0.12)) return BLACK;
        return dv < 0 ? RED : WHITE;
    }

    /** Poké Ball medallion on the floor (layer y), centered at (cx, cz) (block-edge coordinates). */
    private void floorBall(double cx, double cz, double r, int y) {
        for (int z = (int) Math.floor(cz - r); z <= (int) Math.ceil(cz + r); z++)
            for (int x = (int) Math.floor(cx - r); x <= (int) Math.ceil(cx + r); x++) {
                BlockState s = ball(x + 0.5 - cx, z + 0.5 - cz, r);
                if (s != null) set(x, y, z, s);
            }
    }

    // ------------------------------------------------------------------ design

    private void build() {
        // ground: grass across the whole estate (y = -1); the rest of the subsoil is kept (foundations fill gaps)
        fill(0, -1, 0, W - 1, -1, D - 1, GRASS);
        fillAir(0, 0, 0, W - 1, Y_MAX, D - 1);

        gardenWall();
        paths();
        fountain(20, 16);
        fountain(79, 16);
        pool();
        house();
        hedgesAndBeds();
        trees();
        gardenLamps();
        flowers();
        residentsPlan();
    }

    private void gardenWall() {
        for (int i = 0; i < W; i++) {
            wallColumn(i, 0);
            wallColumn(i, D - 1);
        }
        for (int i = 1; i < D - 1; i++) {
            wallColumn(0, i);
            wallColumn(W - 1, i);
        }
        // gate: 10-wide opening with pillars, black/red lintel (Poké Ball band) and lanterns
        fillAir(45, 0, 0, 54, 4, 0);
        fill(45, -1, 0, 54, -1, 0, PATH);
        fill(44, 0, 0, 44, 6, 0, PILLAR);
        fill(55, 0, 0, 55, 6, 0, PILLAR);
        fill(45, 5, 0, 54, 5, 0, BLACK);
        fill(44, 6, 0, 55, 6, 0, RED);
        fill(44, 7, 0, 55, 7, 0, slab(Blocks.RED_NETHER_BRICK_SLAB.defaultBlockState(), SlabType.BOTTOM));
        set(49, 5, 0, WHITE);
        set(50, 5, 0, WHITE);
        deco(47, 4, 0, HANGING);
        deco(52, 4, 0, HANGING);
        set(44, 7, 0, CHISELED);
        set(55, 7, 0, CHISELED);
        deco(44, 8, 0, LANTERN);
        deco(55, 8, 0, LANTERN);
    }

    private void wallColumn(int x, int z) {
        boolean post = (x % 9 == 0 && (z == 0 || z == D - 1)) || (z % 9 == 0 && (x == 0 || x == W - 1));
        if (post) {
            fill(x, 0, z, x, 3, z, PILLAR);
            set(x, 4, z, CHISELED);
            deco(x, 5, z, LANTERN);
        } else {
            for (int y = 0; y <= 2; y++) set(x, y, z, rnd.nextInt(100) < 15 ? MOSSY : STONE_BRICKS);
            set(x, 3, z, slab(Blocks.STONE_BRICK_SLAB.defaultBlockState(), SlabType.BOTTOM));
        }
    }

    private void paths() {
        // avenue from the gate to the portico
        for (int z = 1; z <= 25; z++)
            for (int x = 46; x <= 53; x++) set(x, -1, z, x == 46 || x == 53 ? PATH_EDGE : PATH);
        // plaza with the large Poké Ball medallion
        floorBall(50.0, 16.0, 7.0, -1);
        // cross path to the fountains and from the fountains to the pool
        fill(25, -1, 15, 42, -1, 16, PATH_EDGE);
        fill(57, -1, 15, 74, -1, 16, PATH_EDGE);
        fill(19, -1, 21, 20, -1, 77, PATH_EDGE);
        fill(79, -1, 21, 80, -1, 77, PATH_EDGE);
        fill(19, -1, 76, 27, -1, 77, PATH_EDGE);
        fill(72, -1, 76, 80, -1, 77, PATH_EDGE);
        // back exit from the dining room to the pool
        fill(29, -1, 72, 30, -1, 75, PATH_EDGE);
        // avenue lamp posts
        for (int z : new int[]{4, 23}) {
            lampPost(45, z);
            lampPost(54, z);
        }
    }

    private void lampPost(int x, int z) {
        set(x, 0, z, FENCE);
        set(x, 1, z, FENCE);
        deco(x, 2, z, LANTERN);
    }

    /** Round fountain with a 3x3x3 Poké Ball on top of the central pillar. */
    private void fountain(int cx, int cz) {
        BlockState floor = furn("pool_ceramic", Blocks.PRISMARINE_BRICKS.defaultBlockState());
        for (int z = cz - 5; z <= cz + 5; z++)
            for (int x = cx - 5; x <= cx + 5; x++) {
                double d = Math.sqrt((x - cx) * (x - cx) + (z - cz) * (z - cz));
                if (d > 4.6) continue;
                if (d > 3.5) {
                    set(x, -1, z, STONE_BRICKS);
                    set(x, 0, z, SMOOTH_QUARTZ);
                } else if (d < 1) {
                    fill(x, -2, z, x, 1, z, PILLAR);
                } else {
                    set(x, -2, z, floor);
                    deco(x, -1, z, WATER);
                    deco(x, 0, z, WATER);
                }
            }
        // the ball: white at the bottom, black band with a white button on each face and red on top
        for (int dz = -1; dz <= 1; dz++)
            for (int dx = -1; dx <= 1; dx++) {
                boolean cross = dx == 0 || dz == 0;
                if (cross) set(cx + dx, 2, cz + dz, WHITE);
                set(cx + dx, 3, cz + dz, (Math.abs(dx) + Math.abs(dz) == 1) ? WHITE : BLACK);
                if (cross) set(cx + dx, 4, cz + dz, RED);
            }
        set(cx, 3, cz, BLACK);
    }

    private void pool() {
        BlockState ceramic = furn("pool_ceramic", Blocks.LIGHT_BLUE_CONCRETE.defaultBlockState());
        // terrace
        fill(28, -1, 76, 71, -1, 95, SMOOTH_QUARTZ);
        // basin: floor at y = -4, walls up to y = -2, water from y = -3 to y = -1
        fill(31, -4, 79, 68, -2, 92, ceramic);
        for (int z = 80; z <= 91; z++)
            for (int x = 32; x <= 67; x++) {
                for (int y = -3; y <= -1; y++) deco(x, y, z, WATER);
            }
        floorBall(50.0, 86.0, 5.0, -4);
        // lights in the walls
        for (int x = 34; x <= 66; x += 6) {
            set(x, -2, 79, Blocks.SEA_LANTERN.defaultBlockState());
            set(x, -2, 92, Blocks.SEA_LANTERN.defaultBlockState());
        }
        for (int z = 82; z <= 90; z += 4) {
            set(31, -2, z, Blocks.SEA_LANTERN.defaultBlockState());
            set(68, -2, z, Blocks.SEA_LANTERN.defaultBlockState());
        }
        // entry steps (submerged)
        BlockState step = stairs(Blocks.QUARTZ_STAIRS.defaultBlockState(), Direction.NORTH).setValue(StairBlock.WATERLOGGED, true);
        for (int x = 48; x <= 51; x++) {
            deco(x, -2, 80, step);
            deco(x, -3, 81, step);
            deco(x, -3, 80, QUARTZ);
        }
        // loungers facing the pool and Poké Ball parasols
        for (int x : new int[]{33, 36, 39, 60, 63, 66}) seat(x, 0, 77, "light_blue_armchair", Blocks.QUARTZ_STAIRS.defaultBlockState(), Direction.SOUTH);
        for (int x : new int[]{42, 57}) {
            fill(x, 0, 77, x, 2, 77, FENCE);
            for (int dz = -1; dz <= 1; dz++)
                for (int dx = -1; dx <= 1; dx++)
                    set(x + dx, 3, 77 + dz, ((dx + dz) & 1) == 0 ? Blocks.RED_WOOL.defaultBlockState() : Blocks.WHITE_WOOL.defaultBlockState());
        }
        for (int[] c : new int[][]{{28, 76}, {71, 76}, {28, 95}, {71, 95}}) tallPlant(c[0], 0, c[1], "mini_topiary");
    }

    // ------------------------------------------------------------------ the house

    private static final int X0 = 20, X1 = 79, Z0 = 32, Z1 = 71;

    private void house() {
        // floors (ground floor at y = 0, first floor at y = 7) and ceiling (y = 13)
        fill(X0, 0, Z0, X1, 0, Z1, STONE_BRICKS);
        fill(X0 + 1, 7, Z0 + 1, X1 - 1, 7, Z1 - 1, DARK_PLANKS);
        floors();
        shell();
        partitions();
        roof();
        portico();
        hall();
        pokemonCenter();
        livingRoom();
        diningRoom();
        kitchen();
        trophyRoom();
        masterBedroom();
        blueBedroom();
        greenBedroom();
        gameRoom();
        landing();
    }

    private void floors() {
        // entrance hall: smooth quartz with a black border and medallion
        fill(41, 0, 33, 58, 0, 50, SMOOTH_QUARTZ);
        for (int x = 41; x <= 58; x++) {
            set(x, 0, 33, BLACKSTONE);
            set(x, 0, 50, BLACKSTONE);
        }
        for (int z = 33; z <= 50; z++) {
            set(41, 0, z, BLACKSTONE);
            set(58, 0, z, BLACKSTONE);
        }
        floorBall(50.0, 40.0, 5.0, 0);
        // Pokémon Center: white with a red border
        fill(41, 0, 51, 58, 0, 70, WHITE);
        for (int x = 41; x <= 58; x++) set(x, 0, 70, RED);
        for (int z = 51; z <= 70; z++) {
            set(41, 0, z, RED);
            set(58, 0, z, RED);
        }
        fill(21, 0, 33, 39, 0, 52, DARK_PLANKS); // living room
        fill(21, 0, 54, 39, 0, 70, SPRUCE_PLANKS); // dining room
        fill(60, 0, 33, 78, 0, 52, Blocks.DEEPSLATE_TILES.defaultBlockState()); // trophies
        BlockState kf = furn("kitchen_floor", null);
        for (int z = 54; z <= 70; z++)
            for (int x = 60; x <= 78; x++) set(x, 0, z, kf != null ? kf : ((x + z) & 1) == 0 ? SMOOTH_QUARTZ : BLACKSTONE);
        // double-height void over the entrance hall
        fillAir(41, 7, 33, 58, 7, 50);
    }

    private void shell() {
        for (int x = X0; x <= X1; x++) {
            wallColumnHouse(x, Z0, true, x);
            wallColumnHouse(x, Z1, true, x);
        }
        for (int z = Z0 + 1; z < Z1; z++) {
            wallColumnHouse(X0, z, false, z);
            wallColumnHouse(X1, z, false, z);
        }
        // main door (double) and dining room back door
        door(49, 1, Z0, Direction.SOUTH, DoorHingeSide.LEFT);
        door(50, 1, Z0, Direction.SOUTH, DoorHingeSide.RIGHT);
        set(49, 3, Z0, GLASS);
        set(50, 3, Z0, GLASS);
        door(29, 1, Z1, Direction.NORTH, DoorHingeSide.RIGHT);
        door(30, 1, Z1, Direction.NORTH, DoorHingeSide.LEFT);
        // Poké Ball mural on the back facade (Pokémon Center)
        for (int y = 1; y <= 6; y++)
            for (int x = 46; x <= 53; x++) {
                BlockState s = ball(x + 0.5 - 50.0, -(y + 0.5 - 4.0), 3.6);
                if (s != null) set(x, y, Z1, s);
            }
    }

    /** Exterior wall column: plinth, white, black band at y = 7, white and windows. */
    private void wallColumnHouse(int x, int z, boolean longWall, int along) {
        int base = longWall ? X0 : Z0;
        boolean corner = (x == X0 || x == X1) && (z == Z0 || z == Z1);
        int m = (along - base) % 8;
        boolean pillar = corner || m == 0 || along == (longWall ? X1 : Z1);
        boolean window = !pillar && m >= 3 && m <= 5;
        set(x, 0, z, STONE_BRICKS);
        for (int y = 1; y <= 12; y++) {
            BlockState s = y == 7 ? BLACK : pillar ? PILLAR : QUARTZ;
            set(x, y, z, s);
        }
        if (!window) return;
        boolean front = z == Z0 && longWall;
        boolean lower = !(front && x >= 47 && x <= 52) && !(!longWall && z == 53);
        boolean upper = !(front && x >= 42 && x <= 57) && !(!longWall && z == 53);
        if (lower) for (int y = 2; y <= 4; y++) set(x, y, z, GLASS);
        if (upper) for (int y = 9; y <= 11; y++) set(x, y, z, GLASS);
    }

    private void partitions() {
        for (int y = 1; y <= 12; y++) {
            for (int z = 33; z <= 70; z++) {
                set(40, y, z, QUARTZ);
                set(59, y, z, QUARTZ);
            }
            for (int x = 21; x <= 39; x++) set(x, y, 53, QUARTZ);
            for (int x = 60; x <= 78; x++) set(x, y, 53, QUARTZ);
        }
        // doorways 2 wide and 3 tall (ground and first floor)
        fillAir(40, 1, 42, 40, 3, 43);
        fillAir(59, 1, 42, 59, 3, 43);
        fillAir(29, 1, 53, 30, 3, 53);
        fillAir(69, 1, 53, 70, 3, 53);
        fillAir(40, 1, 61, 40, 3, 62);
        fillAir(59, 1, 61, 59, 3, 62);
        fillAir(40, 8, 51, 40, 10, 52);
        fillAir(59, 8, 51, 59, 10, 52);
        fillAir(40, 8, 61, 40, 10, 62);
        fillAir(59, 8, 61, 59, 10, 62);
        // Pokémon Center pillars under the landing
        fill(41, 1, 51, 41, 6, 51, PILLAR);
        fill(58, 1, 51, 58, 6, 51, PILLAR);
    }

    private void roof() {
        // quartz interior ceiling and stepped red roof (rings of stairs going inward)
        fill(X0, 13, Z0, X1, 13, Z1, SMOOTH_QUARTZ);
        for (int k = 0; k <= 6; k++) {
            int x0 = X0 - 1 + k, x1 = X1 + 1 - k, z0 = Z0 - 1 + k, z1 = Z1 + 1 - k, y = 13 + k;
            if (k >= 1) fill(x0 + 1, y, z0 + 1, x1 - 1, y, z1 - 1, ROOF);
            for (int x = x0; x <= x1; x++) {
                set(x, y, z0, stairs(ROOF_STAIRS, Direction.SOUTH));
                set(x, y, z1, stairs(ROOF_STAIRS, Direction.NORTH));
            }
            for (int z = z0 + 1; z < z1; z++) {
                set(x0, y, z, stairs(ROOF_STAIRS, Direction.EAST));
                set(x1, y, z, stairs(ROOF_STAIRS, Direction.WEST));
            }
        }
        fill(X0 + 6, 20, Z0 + 6, X1 - 6, 20, Z1 - 6, slab(Blocks.RED_NETHER_BRICK_SLAB.defaultBlockState(), SlabType.BOTTOM));
    }

    private void portico() {
        // open portico in front of the door and tower with the Poké Ball emblem
        fill(43, 0, 26, 56, 0, 31, QUARTZ_BRICKS);
        for (int x = 45; x <= 54; x++) set(x, 0, 25, stairs(Blocks.QUARTZ_STAIRS.defaultBlockState(), Direction.SOUTH));
        for (int[] c : new int[][]{{43, 26}, {46, 26}, {53, 26}, {56, 26}, {43, 31}, {56, 31}})
            fill(c[0], 1, c[1], c[0], 6, c[1], PILLAR);
        fill(43, 7, 26, 56, 21, 31, QUARTZ);
        fill(43, 7, 26, 56, 7, 31, BLACK); // the black band also wraps around the tower
        for (int x = 43; x <= 56; x++) {
            set(x, 21, 26, CHISELED);
            set(x, 21, 31, CHISELED);
        }
        for (int y = 9; y <= 20; y++)
            for (int x = 44; x <= 55; x++) {
                BlockState s = ball(x + 0.5 - 50.0, -(y + 0.5 - 15.0), 6.0);
                if (s != null) set(x, y, 26, s);
            }
        // tower's small roof
        for (int j = 0; j <= 3; j++) {
            int x0 = 42 + j, x1 = 57 - j, z0 = 25 + j, z1 = 32 - j, y = 22 + j;
            if (z1 - z0 >= 2) fill(x0 + 1, y, z0 + 1, x1 - 1, y, z1 - 1, ROOF);
            for (int x = x0; x <= x1; x++) {
                set(x, y, z0, stairs(ROOF_STAIRS, Direction.SOUTH));
                set(x, y, z1, stairs(ROOF_STAIRS, Direction.NORTH));
            }
            for (int z = z0 + 1; z < z1; z++) {
                set(x0, y, z, stairs(ROOF_STAIRS, Direction.EAST));
                set(x1, y, z, stairs(ROOF_STAIRS, Direction.WEST));
            }
        }
        set(49, 26, 28, Blocks.GOLD_BLOCK.defaultBlockState());
        set(50, 26, 28, Blocks.GOLD_BLOCK.defaultBlockState());
        set(49, 26, 29, Blocks.GOLD_BLOCK.defaultBlockState());
        set(50, 26, 29, Blocks.GOLD_BLOCK.defaultBlockState());
        for (int x : new int[]{45, 50, 54}) deco(x, 6, 28, HANGING);
    }

    private void hall() {
        // 6-wide staircase going up southward to the landing (the last step, at y = 7, is already the landing)
        BlockState st = stairs(Blocks.DARK_OAK_STAIRS.defaultBlockState(), Direction.SOUTH);
        for (int k = 0; k <= 6; k++) {
            int z = 45 + k, y = 1 + k;
            for (int x = 47; x <= 52; x++) {
                if (k > 0) fill(x, 1, z, x, y - 1, z, DARK_PLANKS);
                set(x, y, z, st);
            }
            fill(46, 1, z, 46, y, z, QUARTZ);
            fill(53, 1, z, 53, y, z, QUARTZ);
            set(46, y + 1, z, FENCE);
            set(53, y + 1, z, FENCE);
        }
        // chandelier
        for (int x = 49; x <= 50; x++)
            for (int z = 39; z <= 40; z++) {
                fill(x, 10, z, x, 12, z, Blocks.CHAIN.defaultBlockState());
                deco(x, 9, z, HANGING);
            }
        // red carpet from the door to the medallion
        for (int x = 49; x <= 50; x++) deco(x, 1, 34, block("red_carpet"));
        // sofas on the sides, lamps, statues and plants
        for (int z = 36; z <= 38; z++) {
            seat(42, 1, z, "red_sofa", Blocks.DARK_OAK_STAIRS.defaultBlockState(), Direction.EAST);
            seat(57, 1, z, "red_sofa", Blocks.DARK_OAK_STAIRS.defaultBlockState(), Direction.WEST);
        }
        lamp(42, 1, 35, "white");
        lamp(42, 1, 39, "white");
        lamp(57, 1, 35, "white");
        lamp(57, 1, 39, "white");
        piece(44, 1, 34, "statue_pikachu", Blocks.DECORATED_POT.defaultBlockState(), Direction.SOUTH);
        piece(55, 1, 34, "statue_squirtle", Blocks.DECORATED_POT.defaultBlockState(), Direction.SOUTH);
        plant(41, 1, 34);
        plant(58, 1, 34);
        plant(41, 1, 46);
        plant(58, 1, 46);
        deco(44, 1, 48, doll("eevee"));
        deco(55, 1, 48, doll("pikachu") != null ? doll("pikachu") : doll("mimikyu"));
        hangGrid(41, 33, 58, 50, 12, 6);
    }

    private void pokemonCenter() {
        // red counter with the healing machine and the PC
        fill(45, 1, 65, 54, 1, 65, RED);
        BlockState heal = face(cob("healing_machine", Blocks.BREWING_STAND.defaultBlockState()), Direction.NORTH);
        deco(49, 1, 65, heal);
        BlockState pc = cob("pc", null);
        if (pc != null) deco2(51, 1, 65, face(pc, Direction.NORTH), "part", "bottom", "top");
        else deco(51, 1, 65, face(Blocks.LECTERN.defaultBlockState(), Direction.NORTH));
        seat(47, 1, 67, "pink_stool", Blocks.QUARTZ_STAIRS.defaultBlockState(), Direction.NORTH);
        // waiting room
        for (int x = 43; x <= 46; x++) seat(x, 1, 57, "red_sofa", Blocks.QUARTZ_STAIRS.defaultBlockState(), Direction.SOUTH);
        for (int x = 53; x <= 56; x++) seat(x, 1, 57, "red_sofa", Blocks.QUARTZ_STAIRS.defaultBlockState(), Direction.SOUTH);
        table(44, 59, 45, 59, 1, "white_table");
        table(54, 59, 55, 59, 1, "white_table");
        plant(42, 1, 69);
        plant(57, 1, 69);
        plant(42, 1, 53);
        plant(57, 1, 53);
        deco(56, 1, 67, doll("happiny"));
        hangGrid(41, 51, 58, 70, 6, 6);
    }

    private void livingRoom() {
        for (int z = 34; z <= 37; z++) fill(21, 1, z, 21, 3, z, Blocks.BOOKSHELF.defaultBlockState());
        for (int z = 47; z <= 51; z++) fill(21, 1, z, 21, 3, z, Blocks.BOOKSHELF.defaultBlockState());
        carpet(26, 40, 34, 48, 1, "red", "white");
        for (int x = 27; x <= 33; x++) seat(x, 1, 38, "red_sofa", Blocks.DARK_OAK_STAIRS.defaultBlockState(), Direction.SOUTH);
        seat(25, 1, 44, "red_armchair", Blocks.DARK_OAK_STAIRS.defaultBlockState(), Direction.EAST);
        seat(35, 1, 44, "red_armchair", Blocks.DARK_OAK_STAIRS.defaultBlockState(), Direction.WEST);
        table(29, 43, 31, 44, 1, "dark_oak_table");
        piece(30, 1, 52, "tv", null, Direction.NORTH);
        lamp(28, 1, 52, "red");
        lamp(32, 1, 52, "red");
        lamp(26, 1, 38, "brown");
        lamp(34, 1, 38, "brown");
        plant(21, 1, 33);
        plant(39, 1, 33);
        plant(21, 1, 52);
        plant(39, 1, 50);
        deco(22, 1, 44, doll("snorlax"));
        deco(36, 1, 38, doll("eevee"));
        deco(24, 1, 38, doll("munchlax"));
        hangGrid(21, 33, 39, 52, 6, 6);
    }

    private void diningRoom() {
        table(26, 61, 34, 62, 1, "dark_oak_table");
        for (int x = 26; x <= 34; x += 2) {
            seat(x, 1, 60, "dark_oak_chair", Blocks.DARK_OAK_STAIRS.defaultBlockState(), Direction.SOUTH);
            seat(x, 1, 63, "dark_oak_chair", Blocks.DARK_OAK_STAIRS.defaultBlockState(), Direction.NORTH);
        }
        seat(25, 1, 61, "dark_oak_chair", Blocks.DARK_OAK_STAIRS.defaultBlockState(), Direction.EAST);
        seat(35, 1, 62, "dark_oak_chair", Blocks.DARK_OAK_STAIRS.defaultBlockState(), Direction.WEST);
        for (int z = 56; z <= 68; z += 2) piece(21, 1, z, "dark_oak_cabinet", Blocks.BARREL.defaultBlockState(), Direction.EAST);
        tallPlant(38, 1, 55, "bonsai_plant");
        tallPlant(38, 1, 69, "bonsai_plant");
        tallPlant(22, 1, 69, "bonsai_plant");
        deco(37, 1, 66, doll("bulbasaur"));
        for (int x : new int[]{27, 30, 33}) deco(x, 6, 61, HANGING);
        hangGrid(21, 54, 39, 70, 6, 6);
    }

    private void kitchen() {
        BlockState cab = furn("white_cabinetry", SMOOTH_QUARTZ);
        for (int x = 63; x <= 76; x++) deco(x, 1, 70, face(cab, Direction.NORTH));
        piece(68, 1, 70, "white_sink", Blocks.WATER_CAULDRON.defaultBlockState().setValue(BlockStateProperties.LEVEL_CAULDRON, 3), Direction.NORTH);
        piece(71, 1, 70, "white_stove", Blocks.SMOKER.defaultBlockState(), Direction.NORTH);
        piece(72, 1, 70, "white_stove", Blocks.SMOKER.defaultBlockState(), Direction.NORTH);
        piece(61, 1, 70, "light_fridge", Blocks.IRON_BLOCK.defaultBlockState(), Direction.NORTH);
        piece(60, 1, 70, "light_freezer", Blocks.IRON_BLOCK.defaultBlockState(), Direction.NORTH);
        for (int x = 63; x <= 67; x++) piece(x, 3, 70, "white_upper_cabinet", null, Direction.NORTH);
        for (int x = 73; x <= 76; x++) piece(x, 3, 70, "white_upper_cabinet", null, Direction.NORTH);
        for (int z = 56; z <= 66; z++) deco(78, 1, z, face(cab, Direction.WEST));
        for (int x = 66; x <= 71; x++) {
            deco(x, 1, 64, face(cab, Direction.NORTH));
            seat(x, 1, 63, "white_stool", Blocks.QUARTZ_STAIRS.defaultBlockState(), Direction.SOUTH);
        }
        table(63, 57, 64, 57, 1, "white_table");
        seat(62, 1, 57, "white_chair", Blocks.BIRCH_STAIRS.defaultBlockState(), Direction.EAST);
        seat(65, 1, 57, "white_chair", Blocks.BIRCH_STAIRS.defaultBlockState(), Direction.WEST);
        deco(62, 1, 69, doll("snorlax"));
        hangGrid(60, 54, 78, 70, 6, 6);
    }

    private void trophyRoom() {
        // collector's room: home pedestals and showcases (MasterBall Traders), statues and red carpet
        for (int x = 60; x <= 67; x++) for (int z = 42; z <= 43; z++) deco(x, 1, z, block("red_carpet"));
        for (int z : new int[]{36, 39, 46, 49}) {
            deco(62, 1, z, face(TcgBlocks.HOME_PEDESTAL.defaultBlockState(), Direction.EAST), true);
            deco(76, 1, z, face(TcgBlocks.HOME_PEDESTAL.defaultBlockState(), Direction.WEST), true);
        }
        for (int x = 64; x <= 74; x += 2) deco(x, 1, 34, face(TcgBlocks.HOME_SHOWCASE.defaultBlockState(), Direction.SOUTH), true);
        deco(69, 1, 40, face(LuxuryBlocks.BLOCKS.get(StatueTier.BRONZE).defaultBlockState(), Direction.WEST), true);
        deco(69, 1, 44, face(LuxuryBlocks.BLOCKS.get(StatueTier.SILVER).defaultBlockState(), Direction.WEST), true);
        deco(69, 1, 48, face(LuxuryBlocks.BLOCKS.get(StatueTier.GOLD).defaultBlockState(), Direction.WEST), true);
        BlockState dc = cob("display_case", null);
        if (dc != null) {
            deco(73, 1, 41, face(dc, Direction.WEST));
            deco(73, 1, 47, face(dc, Direction.WEST));
        }
        piece(75, 1, 52, "poke_ball_desk", slab(Blocks.DARK_OAK_SLAB.defaultBlockState(), SlabType.TOP), Direction.NORTH);
        seat(75, 1, 51, "poke_ball_chair", Blocks.QUARTZ_STAIRS.defaultBlockState(), Direction.SOUTH);
        plant(60, 1, 33);
        plant(78, 1, 33);
        hangGrid(60, 33, 78, 52, 6, 6);
    }

    private void masterBedroom() {
        int y = 8;
        bed(29, y, 34, Direction.NORTH, Blocks.RED_BED.defaultBlockState());
        bed(30, y, 34, Direction.NORTH, Blocks.WHITE_BED.defaultBlockState());
        piece(28, y, 33, "dark_oak_nightstand", Blocks.BARREL.defaultBlockState(), Direction.SOUTH);
        piece(31, y, 33, "dark_oak_nightstand", Blocks.BARREL.defaultBlockState(), Direction.SOUTH);
        lamp(28, y + 1, 33, "red");
        lamp(31, y + 1, 33, "white");
        carpet(27, 35, 32, 38, y, "white", "red");
        for (int z = 44; z <= 47; z++) {
            piece(21, y, z, "dark_oak_cabinet", Blocks.BARREL.defaultBlockState(), Direction.EAST);
            piece(21, y + 1, z, "dark_oak_cabinet", null, Direction.EAST);
        }
        piece(38, y, 45, "poke_ball_desk", slab(Blocks.DARK_OAK_SLAB.defaultBlockState(), SlabType.TOP), Direction.WEST);
        seat(37, y, 45, "poke_ball_chair", Blocks.QUARTZ_STAIRS.defaultBlockState(), Direction.EAST);
        for (int x = 33; x <= 35; x++) seat(x, y, 49, "red_sofa", Blocks.DARK_OAK_STAIRS.defaultBlockState(), Direction.NORTH);
        piece(30, y, 52, "tv", null, Direction.NORTH);
        deco(27, y, 33, doll("eevee"));
        deco(32, y, 33, doll("mimikyu"));
        plant(21, y, 33);
        plant(39, y, 33);
        hangGrid(21, 33, 39, 52, 12, 6);
    }

    private void blueBedroom() {
        int y = 8;
        bed(25, y, 69, Direction.SOUTH, Blocks.BLUE_BED.defaultBlockState());
        bed(33, y, 69, Direction.SOUTH, Blocks.LIGHT_BLUE_BED.defaultBlockState());
        piece(29, y, 70, "blue_drawer", Blocks.BARREL.defaultBlockState(), Direction.NORTH);
        lamp(29, y + 1, 70, "blue");
        piece(21, y, 58, "blue_drawer", Blocks.BARREL.defaultBlockState(), Direction.EAST);
        piece(21, y, 60, "blue_drawer", Blocks.BARREL.defaultBlockState(), Direction.EAST);
        carpet(24, 63, 34, 66, y, "light_blue", "blue");
        table(37, 57, 37, 57, y, "blue_table");
        seat(36, y, 57, "blue_chair", Blocks.SPRUCE_STAIRS.defaultBlockState(), Direction.EAST);
        deco(22, y, 70, doll("squirtle"));
        deco(37, y, 70, doll("wartortle"));
        deco(38, y, 55, doll("blastoise"));
        deco(22, y, 55, doll("wooper"));
        hangGrid(21, 54, 39, 70, 12, 6);
    }

    private void greenBedroom() {
        int y = 8;
        bed(69, y, 34, Direction.NORTH, Blocks.GREEN_BED.defaultBlockState());
        bed(70, y, 34, Direction.NORTH, Blocks.LIME_BED.defaultBlockState());
        piece(68, y, 33, "dark_oak_nightstand", Blocks.BARREL.defaultBlockState(), Direction.SOUTH);
        piece(71, y, 33, "dark_oak_nightstand", Blocks.BARREL.defaultBlockState(), Direction.SOUTH);
        lamp(68, y + 1, 33, "green");
        lamp(71, y + 1, 33, "lime");
        carpet(67, 35, 72, 38, y, "lime", "green");
        seat(76, y, 44, "green_armchair", Blocks.JUNGLE_STAIRS.defaultBlockState(), Direction.WEST);
        seat(76, y, 47, "green_armchair", Blocks.JUNGLE_STAIRS.defaultBlockState(), Direction.WEST);
        table(74, 45, 74, 46, y, "green_table");
        piece(78, y, 37, "green_drawer", Blocks.BARREL.defaultBlockState(), Direction.WEST);
        piece(78, y, 39, "green_drawer", Blocks.BARREL.defaultBlockState(), Direction.WEST);
        deco(67, y, 33, doll("bulbasaur"));
        deco(72, y, 33, doll("ivysaur"));
        deco(77, y, 51, doll("venusaur"));
        deco(61, y, 34, doll("rowlet"));
        tallPlant(78, y, 50, "bonsai_plant");
        hangGrid(60, 33, 78, 52, 12, 6);
    }

    private void gameRoom() {
        int y = 8;
        for (int z = 55; z <= 69; z++) fill(78, y, z, 78, y + 2, z, Blocks.BOOKSHELF.defaultBlockState());
        table(67, 61, 70, 62, y, "gray_dark_table");
        for (int x = 67; x <= 70; x++) {
            seat(x, y, 60, "orange_chair", Blocks.ACACIA_STAIRS.defaultBlockState(), Direction.SOUTH);
            seat(x, y, 63, "orange_chair", Blocks.ACACIA_STAIRS.defaultBlockState(), Direction.NORTH);
        }
        for (int x = 62; x <= 65; x++) seat(x, y, 69, "orange_sofa", Blocks.ACACIA_STAIRS.defaultBlockState(), Direction.NORTH);
        piece(63, y, 55, "tv", null, Direction.SOUTH);
        lamp(61, y, 69, "orange");
        lamp(66, y, 69, "orange");
        deco(61, y, 55, doll("charmander"));
        deco(77, y, 70, doll("gengar"));
        deco(60, y, 66, doll("gastly"));
        hangGrid(60, 54, 78, 70, 12, 6);
    }

    private void landing() {
        int y = 8;
        for (int x = 42; x <= 46; x++) set(x, y, 51, FENCE);
        for (int x = 53; x <= 57; x++) set(x, y, 51, FENCE);
        carpet(45, 58, 54, 66, y, "red", "white");
        for (int x = 46; x <= 53; x++) seat(x, y, 67, "red_sofa", Blocks.DARK_OAK_STAIRS.defaultBlockState(), Direction.NORTH);
        seat(44, y, 62, "white_armchair", Blocks.QUARTZ_STAIRS.defaultBlockState(), Direction.EAST);
        seat(55, y, 62, "white_armchair", Blocks.QUARTZ_STAIRS.defaultBlockState(), Direction.WEST);
        table(48, 61, 51, 62, y, "white_table");
        piece(49, y, 56, "tv", null, Direction.SOUTH);
        piece(50, y, 56, "tv", null, Direction.SOUTH);
        piece(42, y, 69, "statue_bulbasaur", Blocks.DECORATED_POT.defaultBlockState(), Direction.NORTH);
        piece(57, y, 69, "statue_charmander", Blocks.DECORATED_POT.defaultBlockState(), Direction.NORTH);
        piece(42, y, 55, "statue_squirtle", Blocks.DECORATED_POT.defaultBlockState(), Direction.EAST);
        piece(57, y, 55, "statue_pikachu", Blocks.DECORATED_POT.defaultBlockState(), Direction.WEST);
        lamp(45, y, 67, "red");
        lamp(54, y, 67, "red");
        deco(47, y, 70, doll("snorlax"));
        deco(52, y, 70, doll("lapras") != null ? doll("lapras") : doll("kyogre"));
        hangGrid(41, 51, 58, 70, 12, 6);
    }

    // ------------------------------------------------------------------ garden

    private void hedgesAndBeds() {
        BlockState hedge = leaves(Blocks.AZALEA_LEAVES.defaultBlockState());
        BlockState hedge2 = leaves(Blocks.FLOWERING_AZALEA_LEAVES.defaultBlockState());
        for (int x = 21; x <= 78; x++) {
            if (x >= 42 && x <= 57) continue; // portico
            set(x, 0, 29, (x % 4 == 0) ? hedge2 : hedge);
        }
        // flower beds along the facade
        String[] fl = {"poppy", "dandelion", "azure_bluet", "oxeye_daisy", "cornflower", "allium", "red_tulip", "white_tulip", "pink_tulip"};
        for (int z = 30; z <= 31; z++)
            for (int x = 21; x <= 78; x++) {
                if (x >= 42 && x <= 57) continue;
                if (isAir(x, 0, z)) deco(x, 0, z, block(fl[rnd.nextInt(fl.length)]));
            }
    }

    private void trees() {
        int[][] pos = {{8, 6}, {30, 6}, {69, 6}, {91, 6}, {8, 30}, {33, 25}, {66, 25}, {91, 30}, {9, 50}, {90, 50},
                {9, 68}, {90, 68}, {12, 88}, {87, 88}, {21, 93}, {78, 93}};
        String[] colors = {"red", "yellow", "green", "blue", "pink", "black", "white"};
        BlockState log = cob("apricorn_log", Blocks.OAK_LOG.defaultBlockState());
        BlockState leaf = leaves(cob("apricorn_leaves", Blocks.OAK_LEAVES.defaultBlockState()));
        for (int i = 0; i < pos.length; i++) {
            int cx = pos[i][0], cz = pos[i][1];
            fill(cx, 0, cz, cx, 5, cz, log);
            double[] radius = {2.2, 2.8, 2.8, 2.0, 1.2};
            for (int l = 0; l < radius.length; l++) {
                int y = 3 + l;
                double r = radius[l];
                for (int dz = -3; dz <= 3; dz++)
                    for (int dx = -3; dx <= 3; dx++) {
                        if (dx * dx + dz * dz > r * r || (dx == 0 && dz == 0 && y <= 5)) continue;
                        if (isAir(cx + dx, y, cz + dz)) set(cx + dx, y, cz + dz, leaf);
                    }
            }
            // ripe apricorns hanging on the sides (only with Cobblemon)
            BlockState fruit = maxInt(cob(colors[i % colors.length] + "_apricorn", null), "age");
            if (fruit == null) continue;
            for (Direction d : Direction.Plane.HORIZONTAL) {
                int fx = cx + d.getStepX() * 3, fz = cz + d.getStepZ() * 3;
                if (isAir(fx, 3, fz) && solidAt(fx - d.getStepX(), 3, fz - d.getStepZ()))
                    deco(fx, 3, fz, face(fruit, d.getOpposite())); // faces the leaf that holds it
            }
        }
    }

    /** Lamp posts in a grid across the whole garden (so monsters don't spawn at night). */
    private void gardenLamps() {
        for (int z = 5; z <= 95; z += 12)
            for (int x = 7; x <= 92; x += 12) {
                if (cell(x, -1, z) != id(GRASS)) continue;
                if (!isAir(x, 0, z) || !isAir(x, 1, z) || !isAir(x, 2, z) || !isAir(x, 3, z)) continue;
                lampPost(x, z);
            }
    }

    private void flowers() {
        String[] fl = {"poppy", "dandelion", "azure_bluet", "oxeye_daisy", "cornflower", "allium", "red_tulip", "white_tulip",
                "pink_tulip", "orange_tulip", "lily_of_the_valley"};
        short grass = id(GRASS);
        for (int z = 1; z < D - 1; z++)
            for (int x = 1; x < W - 1; x++) {
                if (cell(x, -1, z) != grass || !isAir(x, 0, z)) continue;
                int roll = rnd.nextInt(100);
                if (roll < 5) deco(x, 0, z, block(fl[rnd.nextInt(fl.length)]));
                else if (roll < 9) deco(x, 0, z, block("short_grass"));
            }
    }

    private void residentsPlan() {
        // garden
        residents.add(new ResidentSpec("pikachu", 41.5, 0, 12.5, 25, 8, false));
        residents.add(new ResidentSpec("eevee", 58.5, 0, 12.5, 25, 8, false));
        residents.add(new ResidentSpec("snorlax", 14.5, 0, 42.5, 40, 2, true));
        residents.add(new ResidentSpec("bulbasaur", 85.5, 0, 42.5, 20, 8, false));
        residents.add(new ResidentSpec("squirtle", 26.5, 0, 22.5, 20, 6, false));
        residents.add(new ResidentSpec("charmander", 73.5, 0, 22.5, 20, 6, false));
        residents.add(new ResidentSpec("jigglypuff", 12.5, 0, 78.5, 20, 8, false));
        residents.add(new ResidentSpec("psyduck", 29.5, 0, 94.5, 20, 5, false));
        residents.add(new ResidentSpec("lapras", 50.5, -3, 86.5, 40, 5, false));
        // inside the house
        residents.add(new ResidentSpec("chansey", 49.5, 1, 67.5, 30, 2, false));
        residents.add(new ResidentSpec("togepi", 55.5, 1, 61.5, 10, 3, false));
        residents.add(new ResidentSpec("meowth", 30.5, 1, 46.5, 20, 4, false));
    }
}

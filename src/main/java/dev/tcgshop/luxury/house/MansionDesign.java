package dev.tcgshop.luxury.house;

import dev.tcgshop.luxury.LuxuryBlocks;
import dev.tcgshop.luxury.StatueTier;
import dev.tcgshop.shop.TcgBlocks;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.LanternBlock;
import net.minecraft.block.LeavesBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.TallPlantBlock;
import net.minecraft.block.enums.BedPart;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.DoorHinge;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.block.enums.SlabType;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
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
 * Diseño procedural de la finca de la Mansión MasterBall (100x100), sin archivos NBT. Coordenadas LOCALES: x 0..99 de
 * oeste a este, z 0..99 de norte a sur (la verja está en z = 0 y mira al norte), y relativa al suelo (y = -1 es la capa
 * de hierba; se camina en y = 0). Los bloques con orientación se guardan mirando en direcciones locales y
 * {@link MansionBuilder} los gira según {@link MansionPlan}.
 * <p>
 * Dos capas:
 *  · estructura ({@link #cells}): muros, suelos, tejado, caminos... se colocan de abajo arriba;
 *  · decoración ({@link #decor}, en orden de inserción): muebles, alfombras, faroles, plantas, agua, camas, puertas...
 *    se colocan después, cuando ya existe todo lo que los sostiene.
 * Muebles de CobbleFurnies, peluches de Pokéblocks y bloques de Cobblemon por id de registro: si el mod no está, se usa
 * un equivalente vanilla (o se omite la pieza). Nada de arte con copyright: sólo bloques de los mods instalados.
 */
public final class MansionDesign {
    public static final int W = 100, D = 100, Y_MIN = -8, Y_MAX = 40, H = Y_MAX - Y_MIN + 1;
    /** Valores especiales de {@link #cells}: no tocar (subsuelo) y aire (se despeja). */
    public static final short KEEP = 0, AIR = 1;

    /** Una pieza de decoración. hook = llamar a Block.onPlaced (estatuas y expositores de varios bloques). */
    public record Deco(int x, int y, int z, BlockState state, boolean hook) {}

    /** Pokémon residente: especie, posición local, nivel, radio que puede alejarse y si se queda quieto (sin IA). */
    public record ResidentSpec(String species, double x, double y, double z, int level, int radius, boolean still) {}

    final short[] cells = new short[W * D * H];
    final List<BlockState> palette = new ArrayList<>();
    private final Map<BlockState, Short> index = new HashMap<>();
    private final LinkedHashMap<Integer, Deco> decoMap = new LinkedHashMap<>();
    public final List<ResidentSpec> residents = new ArrayList<>();

    public final boolean furnies, cobblemon, plush;
    private final Random rnd;

    // ------------------------------------------------------------------ paleta base
    private static final BlockState GRASS = Blocks.GRASS_BLOCK.getDefaultState();
    private static final BlockState QUARTZ = Blocks.QUARTZ_BLOCK.getDefaultState();
    private static final BlockState SMOOTH_QUARTZ = Blocks.SMOOTH_QUARTZ.getDefaultState();
    private static final BlockState QUARTZ_BRICKS = Blocks.QUARTZ_BRICKS.getDefaultState();
    private static final BlockState PILLAR = Blocks.QUARTZ_PILLAR.getDefaultState();
    private static final BlockState CHISELED = Blocks.CHISELED_QUARTZ_BLOCK.getDefaultState();
    private static final BlockState STONE_BRICKS = Blocks.STONE_BRICKS.getDefaultState();
    private static final BlockState MOSSY = Blocks.MOSSY_STONE_BRICKS.getDefaultState();
    private static final BlockState RED = Blocks.RED_CONCRETE.getDefaultState();
    private static final BlockState WHITE = Blocks.WHITE_CONCRETE.getDefaultState();
    private static final BlockState BLACK = Blocks.BLACK_CONCRETE.getDefaultState();
    private static final BlockState ROOF = Blocks.RED_NETHER_BRICKS.getDefaultState();
    private static final BlockState ROOF_STAIRS = Blocks.RED_NETHER_BRICK_STAIRS.getDefaultState();
    private static final BlockState DARK_PLANKS = Blocks.DARK_OAK_PLANKS.getDefaultState();
    private static final BlockState SPRUCE_PLANKS = Blocks.SPRUCE_PLANKS.getDefaultState();
    private static final BlockState PATH = Blocks.POLISHED_DIORITE.getDefaultState();
    private static final BlockState PATH_EDGE = Blocks.POLISHED_ANDESITE.getDefaultState();
    private static final BlockState GLASS = Blocks.GLASS_PANE.getDefaultState();
    private static final BlockState FENCE = Blocks.DARK_OAK_FENCE.getDefaultState();
    private static final BlockState WATER = Blocks.WATER.getDefaultState();
    private static final BlockState LANTERN = Blocks.LANTERN.getDefaultState();
    private static final BlockState HANGING = Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, true);
    private static final BlockState BLACKSTONE = Blocks.POLISHED_BLACKSTONE.getDefaultState();

    private static final String F = "cobblefurnies:", PB = "pokeblocks:", CM = "cobblemon:";

    private MansionDesign(long seed) {
        this.furnies = FabricLoader.getInstance().isModLoaded("cobblefurnies");
        this.cobblemon = FabricLoader.getInstance().isModLoaded("cobblemon");
        this.plush = FabricLoader.getInstance().isModLoaded("pokeblocks");
        this.rnd = new Random(seed);
        palette.add(null); // KEEP
        palette.add(Blocks.AIR.getDefaultState()); // AIR
        index.put(Blocks.AIR.getDefaultState(), AIR);
    }

    /** Genera la finca entera (determinista para una semilla). */
    public static MansionDesign generate(long seed) {
        MansionDesign d = new MansionDesign(seed);
        d.build();
        return d;
    }

    // ------------------------------------------------------------------ acceso (MansionBuilder)

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

    /** Bloques de estructura (sin contar aire ni subsuelo) + piezas de decoración: para la prueba en seco. */
    public int blockCount() {
        int n = 0;
        for (short c : cells) if (c > AIR) n++;
        return n + decoMap.size();
    }

    // ------------------------------------------------------------------ utilidades de bloques

    @Nullable
    static BlockState block(String id) {
        Identifier i = Identifier.tryParse(id.contains(":") ? id : "minecraft:" + id);
        if (i == null || !Registries.BLOCK.containsId(i)) return null;
        Block b = Registries.BLOCK.get(i);
        return b == Blocks.AIR ? null : b.getDefaultState();
    }

    /** Pone una propiedad por nombre y valor de texto (si el bloque la tiene y el valor es válido). */
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Nullable
    static BlockState prop(@Nullable BlockState s, String name, String value) {
        if (s == null) return null;
        for (Property<?> p : s.getProperties()) {
            if (!p.getName().equals(name)) continue;
            Optional<?> v = p.parse(value);
            if (v.isPresent()) return s.with((Property) p, (Comparable) v.get());
        }
        return s;
    }

    /** Orienta un bloque (propiedad "facing", si la tiene). */
    @Nullable
    static BlockState face(@Nullable BlockState s, Direction d) {
        return prop(s, "facing", d.asString());
    }

    /** Valor máximo de una propiedad entera (p.ej. la edad de un apricorn: maduro). */
    @Nullable
    static BlockState maxInt(@Nullable BlockState s, String name) {
        if (s == null) return null;
        for (Property<?> p : s.getProperties())
            if (p instanceof IntProperty ip && p.getName().equals(name)) {
                int max = ip.getValues().stream().mapToInt(Integer::intValue).max().orElse(0);
                return s.with(ip, max);
            }
        return s;
    }

    /** Mueble de CobbleFurnies (si está el mod) o el equivalente vanilla (puede ser null = no se pone nada). */
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
        return s.with(StairsBlock.FACING, tall).with(StairsBlock.HALF, BlockHalf.BOTTOM);
    }

    private static BlockState slab(BlockState s, SlabType t) {
        return s.with(SlabBlock.TYPE, t);
    }

    private static BlockState leaves(BlockState s) {
        return s.contains(LeavesBlock.PERSISTENT) ? s.with(LeavesBlock.PERSISTENT, true) : prop(s, "persistent", "true");
    }

    // ------------------------------------------------------------------ escritura del lienzo

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
        decoMap.remove(i); // la estructura manda sobre una decoración anterior en el mismo sitio
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

    /** Decoración (null = nada). El sitio queda en aire en la estructura; si ya había decoración ahí, se sustituye. */
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

    /** Bloque de dos alturas (planta alta, PC...): mitad de abajo y de arriba con la propiedad "half"/"part". */
    private void deco2(int x, int y, int z, @Nullable BlockState s, String prop, String lower, String upper) {
        if (s == null) return;
        deco(x, y, z, prop(s, prop, lower));
        deco(x, y + 1, z, prop(s, prop, upper));
    }

    /** Asiento: mueble mirando hacia front o, sin CobbleFurnies, escalera de madera (que se sienta al revés). */
    private void seat(int x, int y, int z, String furniesId, BlockState stairsFallback, Direction front) {
        BlockState f = furnies ? block(F + furniesId) : null;
        deco(x, y, z, f != null ? face(f, front) : stairs(stairsFallback, front.getOpposite()));
    }

    /** Mueble orientado (o su equivalente). */
    private void piece(int x, int y, int z, String furniesId, @Nullable BlockState fallback, Direction front) {
        deco(x, y, z, face(furn(furniesId, fallback), front));
    }

    private void table(int x0, int z0, int x1, int z1, int y, String furniesId) {
        BlockState t = furn(furniesId, slab(Blocks.DARK_OAK_SLAB.getDefaultState(), SlabType.TOP));
        for (int z = z0; z <= z1; z++) for (int x = x0; x <= x1; x++) deco(x, y, z, t);
    }

    private void lamp(int x, int y, int z, String color) {
        BlockState l = furn(color + "_lamp", null);
        if (l != null) deco(x, y, z, prop(prop(l, "mount_type", "floor"), "lit", "true"));
        else deco(x, y, z, LANTERN);
    }

    private void plant(int x, int y, int z) {
        BlockState p = furn("potted_pothos", Blocks.POTTED_FERN.getDefaultState());
        deco(x, y, z, face(p, Direction.SOUTH));
    }

    private void tallPlant(int x, int y, int z, String furniesId) {
        BlockState p = furnies ? block(F + furniesId) : null;
        if (p != null) deco2(x, y, z, p, "half", "lower", "upper");
        else deco(x, y, z, Blocks.POTTED_AZALEA_BUSH.getDefaultState());
    }

    private void bed(int x, int y, int footZ, Direction towardHead, BlockState bed) {
        int hx = x + towardHead.getOffsetX(), hz = footZ + towardHead.getOffsetZ();
        deco(x, y, footZ, bed.with(BedBlock.FACING, towardHead).with(BedBlock.PART, BedPart.FOOT));
        deco(hx, y, hz, bed.with(BedBlock.FACING, towardHead).with(BedBlock.PART, BedPart.HEAD));
    }

    private void door(int x, int y, int z, Direction facing, DoorHinge hinge) {
        BlockState d = Blocks.DARK_OAK_DOOR.getDefaultState().with(DoorBlock.FACING, facing).with(DoorBlock.HINGE, hinge);
        deco(x, y, z, d.with(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        deco(x, y + 1, z, d.with(DoorBlock.HALF, DoubleBlockHalf.UPPER));
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

    /** Faroles colgados del techo en cuadrícula (y = altura del farol, justo bajo el techo). */
    private void hangGrid(int x0, int z0, int x1, int z1, int y, int step) {
        for (int z = z0 + step / 2; z <= z1; z += step)
            for (int x = x0 + step / 2; x <= x1; x += step)
                if (isAir(x, y, z) && solidAt(x, y + 1, z)) deco(x, y, z, HANGING);
    }

    /** Color del motivo de Poké Ball en el punto (du, dv) de un disco de radio r (dv < 0 = mitad roja). null = fuera. */
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

    /** Medallón de Poké Ball en el suelo (capa y), centrado en (cx, cz) (coordenadas de borde de bloque). */
    private void floorBall(double cx, double cz, double r, int y) {
        for (int z = (int) Math.floor(cz - r); z <= (int) Math.ceil(cz + r); z++)
            for (int x = (int) Math.floor(cx - r); x <= (int) Math.ceil(cx + r); x++) {
                BlockState s = ball(x + 0.5 - cx, z + 0.5 - cz, r);
                if (s != null) set(x, y, z, s);
            }
    }

    // ------------------------------------------------------------------ diseño

    private void build() {
        // suelo: hierba en toda la finca (y = -1); el resto del subsuelo se respeta (los cimientos rellenan huecos)
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
        // verja: hueco de 10 con columnas, dintel negro/rojo (franja de Poké Ball) y faroles
        fillAir(45, 0, 0, 54, 4, 0);
        fill(45, -1, 0, 54, -1, 0, PATH);
        fill(44, 0, 0, 44, 6, 0, PILLAR);
        fill(55, 0, 0, 55, 6, 0, PILLAR);
        fill(45, 5, 0, 54, 5, 0, BLACK);
        fill(44, 6, 0, 55, 6, 0, RED);
        fill(44, 7, 0, 55, 7, 0, slab(Blocks.RED_NETHER_BRICK_SLAB.getDefaultState(), SlabType.BOTTOM));
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
            set(x, 3, z, slab(Blocks.STONE_BRICK_SLAB.getDefaultState(), SlabType.BOTTOM));
        }
    }

    private void paths() {
        // avenida de la verja al pórtico
        for (int z = 1; z <= 25; z++)
            for (int x = 46; x <= 53; x++) set(x, -1, z, x == 46 || x == 53 ? PATH_EDGE : PATH);
        // plaza con el gran medallón de Poké Ball
        floorBall(50.0, 16.0, 7.0, -1);
        // camino transversal a las fuentes y de las fuentes a la piscina
        fill(25, -1, 15, 42, -1, 16, PATH_EDGE);
        fill(57, -1, 15, 74, -1, 16, PATH_EDGE);
        fill(19, -1, 21, 20, -1, 77, PATH_EDGE);
        fill(79, -1, 21, 80, -1, 77, PATH_EDGE);
        fill(19, -1, 76, 27, -1, 77, PATH_EDGE);
        fill(72, -1, 76, 80, -1, 77, PATH_EDGE);
        // salida trasera del comedor a la piscina
        fill(29, -1, 72, 30, -1, 75, PATH_EDGE);
        // farolas de la avenida
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

    /** Fuente redonda con una Poké Ball de 3x3x3 encima del pilar central. */
    private void fountain(int cx, int cz) {
        BlockState floor = furn("pool_ceramic", Blocks.PRISMARINE_BRICKS.getDefaultState());
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
        // la bola: blanca abajo, franja negra con botón blanco en cada cara y roja arriba
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
        BlockState ceramic = furn("pool_ceramic", Blocks.LIGHT_BLUE_CONCRETE.getDefaultState());
        // terraza
        fill(28, -1, 76, 71, -1, 95, SMOOTH_QUARTZ);
        // vaso: suelo en y = -4, paredes hasta y = -2, agua de y = -3 a y = -1
        fill(31, -4, 79, 68, -2, 92, ceramic);
        for (int z = 80; z <= 91; z++)
            for (int x = 32; x <= 67; x++) {
                for (int y = -3; y <= -1; y++) deco(x, y, z, WATER);
            }
        floorBall(50.0, 86.0, 5.0, -4);
        // focos en las paredes
        for (int x = 34; x <= 66; x += 6) {
            set(x, -2, 79, Blocks.SEA_LANTERN.getDefaultState());
            set(x, -2, 92, Blocks.SEA_LANTERN.getDefaultState());
        }
        for (int z = 82; z <= 90; z += 4) {
            set(31, -2, z, Blocks.SEA_LANTERN.getDefaultState());
            set(68, -2, z, Blocks.SEA_LANTERN.getDefaultState());
        }
        // escalones para entrar (sumergidos)
        BlockState step = stairs(Blocks.QUARTZ_STAIRS.getDefaultState(), Direction.NORTH).with(StairsBlock.WATERLOGGED, true);
        for (int x = 48; x <= 51; x++) {
            deco(x, -2, 80, step);
            deco(x, -3, 81, step);
            deco(x, -3, 80, QUARTZ);
        }
        // tumbonas mirando a la piscina y sombrillas de Poké Ball
        for (int x : new int[]{33, 36, 39, 60, 63, 66}) seat(x, 0, 77, "light_blue_armchair", Blocks.QUARTZ_STAIRS.getDefaultState(), Direction.SOUTH);
        for (int x : new int[]{42, 57}) {
            fill(x, 0, 77, x, 2, 77, FENCE);
            for (int dz = -1; dz <= 1; dz++)
                for (int dx = -1; dx <= 1; dx++)
                    set(x + dx, 3, 77 + dz, ((dx + dz) & 1) == 0 ? Blocks.RED_WOOL.getDefaultState() : Blocks.WHITE_WOOL.getDefaultState());
        }
        for (int[] c : new int[][]{{28, 76}, {71, 76}, {28, 95}, {71, 95}}) tallPlant(c[0], 0, c[1], "mini_topiary");
    }

    // ------------------------------------------------------------------ la casa

    private static final int X0 = 20, X1 = 79, Z0 = 32, Z1 = 71;

    private void house() {
        // suelos (planta baja en y = 0, primera planta en y = 7) y techo (y = 13)
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
        // vestíbulo: cuarzo liso con cenefa negra y medallón
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
        // Centro Pokémon: blanco con cenefa roja
        fill(41, 0, 51, 58, 0, 70, WHITE);
        for (int x = 41; x <= 58; x++) set(x, 0, 70, RED);
        for (int z = 51; z <= 70; z++) {
            set(41, 0, z, RED);
            set(58, 0, z, RED);
        }
        fill(21, 0, 33, 39, 0, 52, DARK_PLANKS); // salón
        fill(21, 0, 54, 39, 0, 70, SPRUCE_PLANKS); // comedor
        fill(60, 0, 33, 78, 0, 52, Blocks.DEEPSLATE_TILES.getDefaultState()); // trofeos
        BlockState kf = furn("kitchen_floor", null);
        for (int z = 54; z <= 70; z++)
            for (int x = 60; x <= 78; x++) set(x, 0, z, kf != null ? kf : ((x + z) & 1) == 0 ? SMOOTH_QUARTZ : BLACKSTONE);
        // hueco a doble altura del vestíbulo
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
        // puerta principal (doble) y puerta trasera del comedor
        door(49, 1, Z0, Direction.SOUTH, DoorHinge.LEFT);
        door(50, 1, Z0, Direction.SOUTH, DoorHinge.RIGHT);
        set(49, 3, Z0, GLASS);
        set(50, 3, Z0, GLASS);
        door(29, 1, Z1, Direction.NORTH, DoorHinge.RIGHT);
        door(30, 1, Z1, Direction.NORTH, DoorHinge.LEFT);
        // mural de Poké Ball en la fachada trasera (Centro Pokémon)
        for (int y = 1; y <= 6; y++)
            for (int x = 46; x <= 53; x++) {
                BlockState s = ball(x + 0.5 - 50.0, -(y + 0.5 - 4.0), 3.6);
                if (s != null) set(x, y, Z1, s);
            }
    }

    /** Columna de muro exterior: zócalo, blanco, franja negra en y = 7, blanco y ventanas. */
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
        // pasos de 2 de ancho y 3 de alto (planta baja y primera)
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
        // columnas del Centro Pokémon bajo el rellano
        fill(41, 1, 51, 41, 6, 51, PILLAR);
        fill(58, 1, 51, 58, 6, 51, PILLAR);
    }

    private void roof() {
        // techo interior de cuarzo y tejado rojo escalonado (anillos de escaleras hacia dentro)
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
        fill(X0 + 6, 20, Z0 + 6, X1 - 6, 20, Z1 - 6, slab(Blocks.RED_NETHER_BRICK_SLAB.getDefaultState(), SlabType.BOTTOM));
    }

    private void portico() {
        // pórtico abierto delante de la puerta y torre con el emblema de Poké Ball
        fill(43, 0, 26, 56, 0, 31, QUARTZ_BRICKS);
        for (int x = 45; x <= 54; x++) set(x, 0, 25, stairs(Blocks.QUARTZ_STAIRS.getDefaultState(), Direction.SOUTH));
        for (int[] c : new int[][]{{43, 26}, {46, 26}, {53, 26}, {56, 26}, {43, 31}, {56, 31}})
            fill(c[0], 1, c[1], c[0], 6, c[1], PILLAR);
        fill(43, 7, 26, 56, 21, 31, QUARTZ);
        fill(43, 7, 26, 56, 7, 31, BLACK); // la franja negra rodea también la torre
        for (int x = 43; x <= 56; x++) {
            set(x, 21, 26, CHISELED);
            set(x, 21, 31, CHISELED);
        }
        for (int y = 9; y <= 20; y++)
            for (int x = 44; x <= 55; x++) {
                BlockState s = ball(x + 0.5 - 50.0, -(y + 0.5 - 15.0), 6.0);
                if (s != null) set(x, y, 26, s);
            }
        // tejadillo de la torre
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
        set(49, 26, 28, Blocks.GOLD_BLOCK.getDefaultState());
        set(50, 26, 28, Blocks.GOLD_BLOCK.getDefaultState());
        set(49, 26, 29, Blocks.GOLD_BLOCK.getDefaultState());
        set(50, 26, 29, Blocks.GOLD_BLOCK.getDefaultState());
        for (int x : new int[]{45, 50, 54}) deco(x, 6, 28, HANGING);
    }

    private void hall() {
        // escalera de 6 de ancho que sube hacia el sur hasta el rellano (el último peldaño, en y = 7, ya es el rellano)
        BlockState st = stairs(Blocks.DARK_OAK_STAIRS.getDefaultState(), Direction.SOUTH);
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
        // lámpara de araña
        for (int x = 49; x <= 50; x++)
            for (int z = 39; z <= 40; z++) {
                fill(x, 10, z, x, 12, z, Blocks.CHAIN.getDefaultState());
                deco(x, 9, z, HANGING);
            }
        // alfombra roja de la puerta al medallón
        for (int x = 49; x <= 50; x++) deco(x, 1, 34, block("red_carpet"));
        // sofás a los lados, lámparas, estatuas y plantas
        for (int z = 36; z <= 38; z++) {
            seat(42, 1, z, "red_sofa", Blocks.DARK_OAK_STAIRS.getDefaultState(), Direction.EAST);
            seat(57, 1, z, "red_sofa", Blocks.DARK_OAK_STAIRS.getDefaultState(), Direction.WEST);
        }
        lamp(42, 1, 35, "white");
        lamp(42, 1, 39, "white");
        lamp(57, 1, 35, "white");
        lamp(57, 1, 39, "white");
        piece(44, 1, 34, "statue_pikachu", Blocks.DECORATED_POT.getDefaultState(), Direction.SOUTH);
        piece(55, 1, 34, "statue_squirtle", Blocks.DECORATED_POT.getDefaultState(), Direction.SOUTH);
        plant(41, 1, 34);
        plant(58, 1, 34);
        plant(41, 1, 46);
        plant(58, 1, 46);
        deco(44, 1, 48, doll("eevee"));
        deco(55, 1, 48, doll("pikachu") != null ? doll("pikachu") : doll("mimikyu"));
        hangGrid(41, 33, 58, 50, 12, 6);
    }

    private void pokemonCenter() {
        // mostrador rojo con la máquina de curación y el PC
        fill(45, 1, 65, 54, 1, 65, RED);
        BlockState heal = face(cob("healing_machine", Blocks.BREWING_STAND.getDefaultState()), Direction.NORTH);
        deco(49, 1, 65, heal);
        BlockState pc = cob("pc", null);
        if (pc != null) deco2(51, 1, 65, face(pc, Direction.NORTH), "part", "bottom", "top");
        else deco(51, 1, 65, face(Blocks.LECTERN.getDefaultState(), Direction.NORTH));
        seat(47, 1, 67, "pink_stool", Blocks.QUARTZ_STAIRS.getDefaultState(), Direction.NORTH);
        // sala de espera
        for (int x = 43; x <= 46; x++) seat(x, 1, 57, "red_sofa", Blocks.QUARTZ_STAIRS.getDefaultState(), Direction.SOUTH);
        for (int x = 53; x <= 56; x++) seat(x, 1, 57, "red_sofa", Blocks.QUARTZ_STAIRS.getDefaultState(), Direction.SOUTH);
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
        for (int z = 34; z <= 37; z++) fill(21, 1, z, 21, 3, z, Blocks.BOOKSHELF.getDefaultState());
        for (int z = 47; z <= 51; z++) fill(21, 1, z, 21, 3, z, Blocks.BOOKSHELF.getDefaultState());
        carpet(26, 40, 34, 48, 1, "red", "white");
        for (int x = 27; x <= 33; x++) seat(x, 1, 38, "red_sofa", Blocks.DARK_OAK_STAIRS.getDefaultState(), Direction.SOUTH);
        seat(25, 1, 44, "red_armchair", Blocks.DARK_OAK_STAIRS.getDefaultState(), Direction.EAST);
        seat(35, 1, 44, "red_armchair", Blocks.DARK_OAK_STAIRS.getDefaultState(), Direction.WEST);
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
            seat(x, 1, 60, "dark_oak_chair", Blocks.DARK_OAK_STAIRS.getDefaultState(), Direction.SOUTH);
            seat(x, 1, 63, "dark_oak_chair", Blocks.DARK_OAK_STAIRS.getDefaultState(), Direction.NORTH);
        }
        seat(25, 1, 61, "dark_oak_chair", Blocks.DARK_OAK_STAIRS.getDefaultState(), Direction.EAST);
        seat(35, 1, 62, "dark_oak_chair", Blocks.DARK_OAK_STAIRS.getDefaultState(), Direction.WEST);
        for (int z = 56; z <= 68; z += 2) piece(21, 1, z, "dark_oak_cabinet", Blocks.BARREL.getDefaultState(), Direction.EAST);
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
        piece(68, 1, 70, "white_sink", Blocks.WATER_CAULDRON.getDefaultState().with(Properties.LEVEL_3, 3), Direction.NORTH);
        piece(71, 1, 70, "white_stove", Blocks.SMOKER.getDefaultState(), Direction.NORTH);
        piece(72, 1, 70, "white_stove", Blocks.SMOKER.getDefaultState(), Direction.NORTH);
        piece(61, 1, 70, "light_fridge", Blocks.IRON_BLOCK.getDefaultState(), Direction.NORTH);
        piece(60, 1, 70, "light_freezer", Blocks.IRON_BLOCK.getDefaultState(), Direction.NORTH);
        for (int x = 63; x <= 67; x++) piece(x, 3, 70, "white_upper_cabinet", null, Direction.NORTH);
        for (int x = 73; x <= 76; x++) piece(x, 3, 70, "white_upper_cabinet", null, Direction.NORTH);
        for (int z = 56; z <= 66; z++) deco(78, 1, z, face(cab, Direction.WEST));
        for (int x = 66; x <= 71; x++) {
            deco(x, 1, 64, face(cab, Direction.NORTH));
            seat(x, 1, 63, "white_stool", Blocks.QUARTZ_STAIRS.getDefaultState(), Direction.SOUTH);
        }
        table(63, 57, 64, 57, 1, "white_table");
        seat(62, 1, 57, "white_chair", Blocks.BIRCH_STAIRS.getDefaultState(), Direction.EAST);
        seat(65, 1, 57, "white_chair", Blocks.BIRCH_STAIRS.getDefaultState(), Direction.WEST);
        deco(62, 1, 69, doll("snorlax"));
        hangGrid(60, 54, 78, 70, 6, 6);
    }

    private void trophyRoom() {
        // sala del coleccionista: pedestales y vitrinas de casa (MasterBall Traders), estatuas y alfombra roja
        for (int x = 60; x <= 67; x++) for (int z = 42; z <= 43; z++) deco(x, 1, z, block("red_carpet"));
        for (int z : new int[]{36, 39, 46, 49}) {
            deco(62, 1, z, face(TcgBlocks.HOME_PEDESTAL.getDefaultState(), Direction.EAST), true);
            deco(76, 1, z, face(TcgBlocks.HOME_PEDESTAL.getDefaultState(), Direction.WEST), true);
        }
        for (int x = 64; x <= 74; x += 2) deco(x, 1, 34, face(TcgBlocks.HOME_SHOWCASE.getDefaultState(), Direction.SOUTH), true);
        deco(69, 1, 40, face(LuxuryBlocks.BLOCKS.get(StatueTier.BRONZE).getDefaultState(), Direction.WEST), true);
        deco(69, 1, 44, face(LuxuryBlocks.BLOCKS.get(StatueTier.SILVER).getDefaultState(), Direction.WEST), true);
        deco(69, 1, 48, face(LuxuryBlocks.BLOCKS.get(StatueTier.GOLD).getDefaultState(), Direction.WEST), true);
        BlockState dc = cob("display_case", null);
        if (dc != null) {
            deco(73, 1, 41, face(dc, Direction.WEST));
            deco(73, 1, 47, face(dc, Direction.WEST));
        }
        piece(75, 1, 52, "poke_ball_desk", slab(Blocks.DARK_OAK_SLAB.getDefaultState(), SlabType.TOP), Direction.NORTH);
        seat(75, 1, 51, "poke_ball_chair", Blocks.QUARTZ_STAIRS.getDefaultState(), Direction.SOUTH);
        plant(60, 1, 33);
        plant(78, 1, 33);
        hangGrid(60, 33, 78, 52, 6, 6);
    }

    private void masterBedroom() {
        int y = 8;
        bed(29, y, 34, Direction.NORTH, Blocks.RED_BED.getDefaultState());
        bed(30, y, 34, Direction.NORTH, Blocks.WHITE_BED.getDefaultState());
        piece(28, y, 33, "dark_oak_nightstand", Blocks.BARREL.getDefaultState(), Direction.SOUTH);
        piece(31, y, 33, "dark_oak_nightstand", Blocks.BARREL.getDefaultState(), Direction.SOUTH);
        lamp(28, y + 1, 33, "red");
        lamp(31, y + 1, 33, "white");
        carpet(27, 35, 32, 38, y, "white", "red");
        for (int z = 44; z <= 47; z++) {
            piece(21, y, z, "dark_oak_cabinet", Blocks.BARREL.getDefaultState(), Direction.EAST);
            piece(21, y + 1, z, "dark_oak_cabinet", null, Direction.EAST);
        }
        piece(38, y, 45, "poke_ball_desk", slab(Blocks.DARK_OAK_SLAB.getDefaultState(), SlabType.TOP), Direction.WEST);
        seat(37, y, 45, "poke_ball_chair", Blocks.QUARTZ_STAIRS.getDefaultState(), Direction.EAST);
        for (int x = 33; x <= 35; x++) seat(x, y, 49, "red_sofa", Blocks.DARK_OAK_STAIRS.getDefaultState(), Direction.NORTH);
        piece(30, y, 52, "tv", null, Direction.NORTH);
        deco(27, y, 33, doll("eevee"));
        deco(32, y, 33, doll("mimikyu"));
        plant(21, y, 33);
        plant(39, y, 33);
        hangGrid(21, 33, 39, 52, 12, 6);
    }

    private void blueBedroom() {
        int y = 8;
        bed(25, y, 69, Direction.SOUTH, Blocks.BLUE_BED.getDefaultState());
        bed(33, y, 69, Direction.SOUTH, Blocks.LIGHT_BLUE_BED.getDefaultState());
        piece(29, y, 70, "blue_drawer", Blocks.BARREL.getDefaultState(), Direction.NORTH);
        lamp(29, y + 1, 70, "blue");
        piece(21, y, 58, "blue_drawer", Blocks.BARREL.getDefaultState(), Direction.EAST);
        piece(21, y, 60, "blue_drawer", Blocks.BARREL.getDefaultState(), Direction.EAST);
        carpet(24, 63, 34, 66, y, "light_blue", "blue");
        table(37, 57, 37, 57, y, "blue_table");
        seat(36, y, 57, "blue_chair", Blocks.SPRUCE_STAIRS.getDefaultState(), Direction.EAST);
        deco(22, y, 70, doll("squirtle"));
        deco(37, y, 70, doll("wartortle"));
        deco(38, y, 55, doll("blastoise"));
        deco(22, y, 55, doll("wooper"));
        hangGrid(21, 54, 39, 70, 12, 6);
    }

    private void greenBedroom() {
        int y = 8;
        bed(69, y, 34, Direction.NORTH, Blocks.GREEN_BED.getDefaultState());
        bed(70, y, 34, Direction.NORTH, Blocks.LIME_BED.getDefaultState());
        piece(68, y, 33, "dark_oak_nightstand", Blocks.BARREL.getDefaultState(), Direction.SOUTH);
        piece(71, y, 33, "dark_oak_nightstand", Blocks.BARREL.getDefaultState(), Direction.SOUTH);
        lamp(68, y + 1, 33, "green");
        lamp(71, y + 1, 33, "lime");
        carpet(67, 35, 72, 38, y, "lime", "green");
        seat(76, y, 44, "green_armchair", Blocks.JUNGLE_STAIRS.getDefaultState(), Direction.WEST);
        seat(76, y, 47, "green_armchair", Blocks.JUNGLE_STAIRS.getDefaultState(), Direction.WEST);
        table(74, 45, 74, 46, y, "green_table");
        piece(78, y, 37, "green_drawer", Blocks.BARREL.getDefaultState(), Direction.WEST);
        piece(78, y, 39, "green_drawer", Blocks.BARREL.getDefaultState(), Direction.WEST);
        deco(67, y, 33, doll("bulbasaur"));
        deco(72, y, 33, doll("ivysaur"));
        deco(77, y, 51, doll("venusaur"));
        deco(61, y, 34, doll("rowlet"));
        tallPlant(78, y, 50, "bonsai_plant");
        hangGrid(60, 33, 78, 52, 12, 6);
    }

    private void gameRoom() {
        int y = 8;
        for (int z = 55; z <= 69; z++) fill(78, y, z, 78, y + 2, z, Blocks.BOOKSHELF.getDefaultState());
        table(67, 61, 70, 62, y, "gray_dark_table");
        for (int x = 67; x <= 70; x++) {
            seat(x, y, 60, "orange_chair", Blocks.ACACIA_STAIRS.getDefaultState(), Direction.SOUTH);
            seat(x, y, 63, "orange_chair", Blocks.ACACIA_STAIRS.getDefaultState(), Direction.NORTH);
        }
        for (int x = 62; x <= 65; x++) seat(x, y, 69, "orange_sofa", Blocks.ACACIA_STAIRS.getDefaultState(), Direction.NORTH);
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
        for (int x = 46; x <= 53; x++) seat(x, y, 67, "red_sofa", Blocks.DARK_OAK_STAIRS.getDefaultState(), Direction.NORTH);
        seat(44, y, 62, "white_armchair", Blocks.QUARTZ_STAIRS.getDefaultState(), Direction.EAST);
        seat(55, y, 62, "white_armchair", Blocks.QUARTZ_STAIRS.getDefaultState(), Direction.WEST);
        table(48, 61, 51, 62, y, "white_table");
        piece(49, y, 56, "tv", null, Direction.SOUTH);
        piece(50, y, 56, "tv", null, Direction.SOUTH);
        piece(42, y, 69, "statue_bulbasaur", Blocks.DECORATED_POT.getDefaultState(), Direction.NORTH);
        piece(57, y, 69, "statue_charmander", Blocks.DECORATED_POT.getDefaultState(), Direction.NORTH);
        piece(42, y, 55, "statue_squirtle", Blocks.DECORATED_POT.getDefaultState(), Direction.EAST);
        piece(57, y, 55, "statue_pikachu", Blocks.DECORATED_POT.getDefaultState(), Direction.WEST);
        lamp(45, y, 67, "red");
        lamp(54, y, 67, "red");
        deco(47, y, 70, doll("snorlax"));
        deco(52, y, 70, doll("lapras") != null ? doll("lapras") : doll("kyogre"));
        hangGrid(41, 51, 58, 70, 12, 6);
    }

    // ------------------------------------------------------------------ jardín

    private void hedgesAndBeds() {
        BlockState hedge = leaves(Blocks.AZALEA_LEAVES.getDefaultState());
        BlockState hedge2 = leaves(Blocks.FLOWERING_AZALEA_LEAVES.getDefaultState());
        for (int x = 21; x <= 78; x++) {
            if (x >= 42 && x <= 57) continue; // pórtico
            set(x, 0, 29, (x % 4 == 0) ? hedge2 : hedge);
        }
        // arriates de flores pegados a la fachada
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
        BlockState log = cob("apricorn_log", Blocks.OAK_LOG.getDefaultState());
        BlockState leaf = leaves(cob("apricorn_leaves", Blocks.OAK_LEAVES.getDefaultState()));
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
            // apricorns maduros colgando a los lados (sólo con Cobblemon)
            BlockState fruit = maxInt(cob(colors[i % colors.length] + "_apricorn", null), "age");
            if (fruit == null) continue;
            for (Direction d : Direction.Type.HORIZONTAL) {
                int fx = cx + d.getOffsetX() * 3, fz = cz + d.getOffsetZ() * 3;
                if (isAir(fx, 3, fz) && solidAt(fx - d.getOffsetX(), 3, fz - d.getOffsetZ()))
                    deco(fx, 3, fz, face(fruit, d.getOpposite())); // mira hacia la hoja que lo sostiene
            }
        }
    }

    /** Farolas en cuadrícula por todo el jardín (para que no aparezcan monstruos de noche). */
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
        // jardín
        residents.add(new ResidentSpec("pikachu", 41.5, 0, 12.5, 25, 8, false));
        residents.add(new ResidentSpec("eevee", 58.5, 0, 12.5, 25, 8, false));
        residents.add(new ResidentSpec("snorlax", 14.5, 0, 42.5, 40, 2, true));
        residents.add(new ResidentSpec("bulbasaur", 85.5, 0, 42.5, 20, 8, false));
        residents.add(new ResidentSpec("squirtle", 26.5, 0, 22.5, 20, 6, false));
        residents.add(new ResidentSpec("charmander", 73.5, 0, 22.5, 20, 6, false));
        residents.add(new ResidentSpec("jigglypuff", 12.5, 0, 78.5, 20, 8, false));
        residents.add(new ResidentSpec("psyduck", 29.5, 0, 94.5, 20, 5, false));
        residents.add(new ResidentSpec("lapras", 50.5, -3, 86.5, 40, 5, false));
        // dentro de la casa
        residents.add(new ResidentSpec("chansey", 49.5, 1, 67.5, 30, 2, false));
        residents.add(new ResidentSpec("togepi", 55.5, 1, 61.5, 10, 3, false));
        residents.add(new ResidentSpec("meowth", 30.5, 1, 46.5, 20, 4, false));
    }
}

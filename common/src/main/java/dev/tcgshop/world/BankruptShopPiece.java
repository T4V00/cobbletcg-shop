package dev.tcgshop.world;

import dev.tcgshop.TcgShop;
import dev.tcgshop.compat.CobbleFurniesCompat;
import dev.tcgshop.shop.FurnitureShop;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.StructureTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The Bankrupt Card Shop building (1.0), placed block by block.
 *
 * <p>Local frame (StructurePiece convention): x = 0..{@value #W}-1 left to right, y = 0 is the floor,
 * z = 0 is the street side (a 1-block front yard), the front wall is z = 1 and the back wall z = {@value #D}-1.
 * In this frame a block state's "south" points to the street (local -z) and "east" to local +x; addBlock rotates
 * and mirrors states to the piece's orientation.
 *
 * <p>Every random choice comes from a Random seeded with {@link #seed}, drawn in the same order on every call, so
 * the parts of the building that fall in different chunks (generate runs once per chunk) always match.
 *
 * <p>Contents: cracked/mossy brick walls with holes up top, broken (and dirty) windows, an open or missing door,
 * dusty floor (carpets), empty chiseled bookshelves, an empty counter, cobwebs, a faded red/white sign board,
 * a waxed sign "Permanently Closed" by the door, maybe one cheap CobbleFurnies piece, maybe a street trash can
 * outside, and the shop's unique trash can ({@code loot=bankrupt}) behind the counter.
 */
public class BankruptShopPiece extends StructurePiece {
    static final int W = 9, H = 7, D = 10;
    private final long seed;
    /** CobbleFurnies block chosen when the piece was created ("" = none). */
    private final String furniture;

    public BankruptShopPiece(long seed, int x, int y, int z, Direction facing) {
        super(WorldLoot.BANKRUPT_SHOP_PIECE, 0, makeBoundingBox(x, y, z, facing, W, H, D));
        setOrientation(facing);
        this.seed = seed;
        this.furniture = pickFurniture(seed);
    }

    public BankruptShopPiece(CompoundTag nbt) {
        super(WorldLoot.BANKRUPT_SHOP_PIECE, nbt);
        this.seed = nbt.getLong("Seed");
        this.furniture = nbt.getString("Furniture");
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag nbt) {
        nbt.putLong("Seed", seed);
        nbt.putString("Furniture", furniture);
    }

    // ------------------------------------------------------------------ furniture

    private static List<ResourceLocation> cheapFurniture;

    /** Cheap CobbleFurnies seats and nightstands (sorted, so the pick only depends on the seed). */
    private static synchronized List<ResourceLocation> cheapFurniture() {
        if (cheapFurniture == null) {
            List<ResourceLocation> out = new ArrayList<>();
            long max = WorldLootConfig.INSTANCE.furnitureMaxPrice;
            for (ResourceLocation id : BuiltInRegistries.BLOCK.keySet()) {
                if (!CobbleFurniesCompat.FURNIES.equals(id.getNamespace()) || !CobbleFurniesCompat.isFurnitureBlock(id)) continue;
                String p = id.getPath();
                if (!(p.endsWith("_chair") || p.endsWith("_stool") || p.endsWith("_nightstand"))) continue;
                if (FurnitureShop.basePrice(id) > max) continue;
                out.add(id);
            }
            out.sort(Comparator.comparing(ResourceLocation::toString));
            cheapFurniture = out;
        }
        return cheapFurniture;
    }

    private static String pickFurniture(long seed) {
        if (!CobbleFurniesCompat.furniesLoaded()) return "";
        RandomSource r = RandomSource.create(seed ^ 0x5DEECE66DL);
        if (r.nextDouble() >= WorldLootConfig.INSTANCE.furnitureChance) return "";
        List<ResourceLocation> list = cheapFurniture();
        return list.isEmpty() ? "" : list.get(r.nextInt(list.size())).toString();
    }

    // ------------------------------------------------------------------ build

    private static BlockState s(Block b) { return b.defaultBlockState(); }

    @Override
    public void postProcess(WorldGenLevel world, StructureManager structureAccessor, ChunkGenerator chunkGenerator, RandomSource random,
                         BoundingBox box, ChunkPos chunkPos, BlockPos pivot) {
        // never cut through a village: the shop sits on the outskirts, but big villages can reach that far. The check
        // only reads fixed structure data, so every chunk of the piece gets the same answer (all or nothing).
        if (overlapsVillage(structureAccessor)) return;
        RandomSource r = RandomSource.create(seed);
        BlockState air = s(Blocks.AIR);

        // clear the volume (the beard terrain adaptation already carves most of it)
        for (int y = 1; y < H; y++)
            for (int x = 0; x < W; x++)
                for (int z = 0; z < D; z++) placeBlock(world, air, x, y, z, box);

        // floor + foundation
        for (int x = 0; x < W; x++) {
            for (int z = 0; z < D; z++) {
                BlockState f;
                float k = r.nextFloat();
                if (z == 0) f = k < 0.5f ? s(Blocks.COBBLESTONE) : k < 0.75f ? s(Blocks.MOSSY_COBBLESTONE) : s(Blocks.COARSE_DIRT);
                else if (x == 0 || x == W - 1 || z == 1 || z == D - 1) f = k < 0.7f ? s(Blocks.STONE_BRICKS) : s(Blocks.CRACKED_STONE_BRICKS);
                else f = k < 0.62f ? s(Blocks.SPRUCE_PLANKS) : k < 0.78f ? s(Blocks.DARK_OAK_PLANKS) : k < 0.9f ? s(Blocks.COARSE_DIRT) : s(Blocks.PACKED_MUD);
                placeBlock(world, f, x, 0, z, box);
                fillColumnDown(world, z == 0 ? s(Blocks.DIRT) : s(Blocks.COBBLESTONE), x, -1, z, box);
            }
        }

        // walls (y 1..4)
        for (int y = 1; y <= 4; y++) {
            for (int x = 0; x < W; x++) {
                wall(world, box, r, x, y, 1, false);
                wall(world, box, r, x, y, D - 1, true);
            }
            for (int z = 2; z < D - 1; z++) {
                wall(world, box, r, 0, y, z, true);
                wall(world, box, r, W - 1, y, z, true);
            }
        }

        // door: open, or kicked in (missing)
        float door = r.nextFloat();
        boolean open = r.nextBoolean();
        if (door < 0.8f) {
            BlockState d = s(Blocks.SPRUCE_DOOR).setValue(DoorBlock.FACING, Direction.SOUTH).setValue(DoorBlock.OPEN, open)
                    .setValue(DoorBlock.HINGE, DoorHingeSide.LEFT);
            placeBlock(world, d.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), 4, 1, 1, box);
            placeBlock(world, d.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 4, 2, 1, box);
        } else {
            placeBlock(world, air, 4, 1, 1, box);
            placeBlock(world, air, 4, 2, 1, box);
        }

        // broken windows. Front: x 1-2 and 6-7 (y 2-3); sides: z 3-4 (y 2-3); back: x 4 (y 3).
        for (int y = 2; y <= 3; y++) {
            boolean[] front = new boolean[W];
            for (int x : new int[]{1, 2, 6, 7}) front[x] = r.nextFloat() < 0.55f;
            for (int x : new int[]{1, 2, 6, 7}) {
                // x 1 / 6 touch a wall on the west, x 2 / 7 on the east; the other side is the twin pane (if any)
                boolean west = x == 1 || x == 6 || front[x - 1];
                boolean east = x == 2 || x == 7 || front[x + 1];
                windowCell(world, box, r, x, y, 1, front[x], west, east, false);
            }
            for (int side : new int[]{0, W - 1}) {
                boolean a = r.nextFloat() < 0.5f, b = r.nextFloat() < 0.5f;
                windowCell(world, box, r, side, y, 3, a, true, b, true);  // z 3: south = wall, north = pane at z 4
                windowCell(world, box, r, side, y, 4, b, a, true, true);  // z 4: south = pane at z 3, north = wall
            }
        }
        windowCell(world, box, r, 4, 3, D - 1, r.nextFloat() < 0.5f, true, true, false);

        // roof (y 5): spruce slabs with holes; false front (z 1, y 5-6) with a faded sign board
        for (int x = 0; x < W; x++) {
            for (int z = 2; z < D; z++) {
                float k = r.nextFloat();
                boolean inner = x > 0 && x < W - 1 && z > 2 && z < D - 1;
                BlockState roof = inner && k < 0.12f ? air
                        : k < 0.2f ? s(Blocks.MOSSY_COBBLESTONE_SLAB).setValue(SlabBlock.TYPE, SlabType.BOTTOM)
                        : s(Blocks.SPRUCE_SLAB).setValue(SlabBlock.TYPE, SlabType.BOTTOM);
                placeBlock(world, roof, x, 5, z, box);
            }
        }
        for (int x = 0; x < W; x++) {
            float k = r.nextFloat();
            BlockState board;
            if (x == 0 || x == W - 1) board = s(Blocks.SPRUCE_LOG);
            else if (x >= 2 && x <= W - 3) board = k < 0.2f ? s(Blocks.BROWN_TERRACOTTA) : x % 2 == 0 ? s(Blocks.RED_TERRACOTTA) : s(Blocks.WHITE_TERRACOTTA);
            else board = k < 0.6f ? s(Blocks.STONE_BRICKS) : s(Blocks.CRACKED_STONE_BRICKS);
            placeBlock(world, board, x, 5, 1, box);
            float t = r.nextFloat();
            if (x > 0 && x < W - 1 && t < 0.75f) placeBlock(world, s(Blocks.STONE_BRICK_SLAB).setValue(SlabBlock.TYPE, SlabType.BOTTOM), x, 6, 1, box);
        }

        // interior dust (carpets: grey dust, brown stains, white flyers)
        for (int x = 1; x < W - 1; x++) {
            for (int z = 2; z < D - 1; z++) {
                float k = r.nextFloat();
                BlockState c = k < 0.12f ? s(Blocks.LIGHT_GRAY_CARPET) : k < 0.16f ? s(Blocks.BROWN_CARPET) : k < 0.2f ? s(Blocks.WHITE_CARPET) : null;
                if (c != null) placeBlock(world, c, x, 1, z, box);
            }
        }

        // empty shelves along both side walls (z 5..8), some missing or webbed
        for (int z = 5; z <= D - 2; z++) {
            for (int side = 0; side < 2; side++) {
                int x = side == 0 ? 1 : W - 2;
                Direction face = side == 0 ? Direction.EAST : Direction.WEST;
                BlockState shelf = s(Blocks.CHISELED_BOOKSHELF).setValue(BlockStateProperties.HORIZONTAL_FACING, face);
                placeBlock(world, r.nextFloat() < 0.85f ? shelf : air, x, 1, z, box);
                float k = r.nextFloat();
                placeBlock(world, k < 0.6f ? shelf : k < 0.8f ? s(Blocks.COBWEB) : air, x, 2, z, box);
            }
        }

        // the counter (x 3..5, z 6) with an empty pot on top
        placeBlock(world, s(Blocks.BARREL).setValue(BlockStateProperties.FACING, Direction.UP), 3, 1, 6, box);
        placeBlock(world, s(Blocks.SPRUCE_PLANKS), 4, 1, 6, box);
        placeBlock(world, s(Blocks.SPRUCE_PLANKS), 5, 1, 6, box);
        placeBlock(world, r.nextBoolean() ? s(Blocks.SPRUCE_SLAB).setValue(SlabBlock.TYPE, SlabType.BOTTOM) : air, 3, 2, 6, box);
        if (r.nextFloat() < 0.5f) placeBlock(world, s(Blocks.COBWEB), 4, 2, 6, box);
        if (r.nextFloat() < 0.5f) placeBlock(world, s(Blocks.FLOWER_POT), 5, 2, 6, box);

        // a fallen barrel in the back corner
        placeBlock(world, s(Blocks.BARREL).setValue(BlockStateProperties.FACING, Direction.Plane.HORIZONTAL.getRandomDirection(r)), 2, 1, D - 2, box);

        // the unique trash can behind the counter, facing the shop floor, with cobwebs around it
        placeBlock(world, TrashCanBlock.natural(TrashCanBlock.LootKind.BANKRUPT, Direction.SOUTH), 6, 1, D - 2, box);
        if (r.nextFloat() < 0.6f) placeBlock(world, s(Blocks.COBWEB), 5, 1, D - 2, box);
        if (r.nextFloat() < 0.7f) placeBlock(world, s(Blocks.COBWEB), 6, 2, D - 2, box);

        // cobwebs: ceiling corners always, ceiling edges and lower corners sometimes
        for (int x = 1; x < W - 1; x++) {
            for (int z = 2; z < D - 1; z++) {
                boolean edgeX = x == 1 || x == W - 2, edgeZ = z == 2 || z == D - 2;
                if (!edgeX && !edgeZ) continue;
                float k = r.nextFloat();
                if ((edgeX && edgeZ) || k < 0.22f) placeBlock(world, s(Blocks.COBWEB), x, 4, z, box);
                if (edgeX && edgeZ && k < 0.5f) placeBlock(world, s(Blocks.COBWEB), x, 3, z, box);
            }
        }

        // optional cheap CobbleFurnies piece in the front area (left or right of the door path)
        float fx = r.nextFloat();
        Direction fFacing = Direction.Plane.HORIZONTAL.getRandomDirection(r);
        BlockState furn = furnitureState(fFacing);
        if (furn != null) placeBlock(world, furn, fx < 0.5f ? 2 : 6, 1, 3, box);

        // outside: a dead bush pot, maybe a street can, and the sign
        if (r.nextFloat() < 0.6f) placeBlock(world, s(Blocks.POTTED_DEAD_BUSH), 1, 1, 0, box);
        if (r.nextFloat() < WorldLootConfig.INSTANCE.outsideCanChance)
            placeBlock(world, TrashCanBlock.natural(TrashCanBlock.LootKind.STREET, Direction.SOUTH), W - 2, 1, 0, box);
        placeBlock(world, s(Blocks.SPRUCE_WALL_SIGN).setValue(WallSignBlock.FACING, Direction.SOUTH), 3, 2, 0, box);
        writeSign(world, box, getWorldPos(3, 2, 0));
    }

    /** Does a village piece (house, street...) cover any of a 3x3 grid of points of the footprint (floor and mid height)? */
    private boolean overlapsVillage(StructureManager structureAccessor) {
        for (int x : new int[]{0, W / 2, W - 1})
            for (int z : new int[]{0, D / 2, D - 1})
                for (int y : new int[]{0, 3})
                    if (structureAccessor.getStructureWithPieceAt(getWorldPos(x, y, z).immutable(), StructureTags.VILLAGE).isValid()) return true;
        return false;
    }

    /** One wall block: logs at the corners, a cobblestone base course, cracked/mossy bricks above, rare holes up top. */
    private void wall(WorldGenLevel world, BoundingBox box, RandomSource r, int x, int y, int z, boolean holes) {
        float k = r.nextFloat();
        BlockState b;
        boolean corner = (x == 0 || x == W - 1) && (z == 1 || z == D - 1);
        if (corner) b = s(Blocks.SPRUCE_LOG);
        else if (y == 1) b = k < 0.7f ? s(Blocks.COBBLESTONE) : s(Blocks.MOSSY_COBBLESTONE);
        else if (holes && y == 4 && k < 0.06f) b = s(Blocks.AIR);
        else b = k < 0.55f ? s(Blocks.STONE_BRICKS) : k < 0.8f ? s(Blocks.CRACKED_STONE_BRICKS) : s(Blocks.MOSSY_STONE_BRICKS);
        placeBlock(world, b, x, y, z, box);
    }

    /**
     * A window cell: a (sometimes dirty) glass pane when present, else air or a cobweb. Connections are given in the
     * local frame: along x (west/east) for front/back walls, along z (south/north) for side walls.
     */
    private void windowCell(WorldGenLevel world, BoundingBox box, RandomSource r, int x, int y, int z, boolean present,
                            boolean negSide, boolean posSide, boolean alongZ) {
        float k = r.nextFloat();
        BlockState b;
        if (present) {
            b = s(k < 0.3f ? Blocks.GRAY_STAINED_GLASS_PANE : Blocks.GLASS_PANE);
            b = alongZ ? b.setValue(CrossCollisionBlock.SOUTH, negSide).setValue(CrossCollisionBlock.NORTH, posSide)
                    : b.setValue(CrossCollisionBlock.WEST, negSide).setValue(CrossCollisionBlock.EAST, posSide);
        } else {
            b = k < 0.25f ? s(Blocks.COBWEB) : s(Blocks.AIR);
        }
        placeBlock(world, b, x, y, z, box);
    }

    @Nullable
    private BlockState furnitureState(Direction facing) {
        if (furniture.isEmpty()) return null;
        ResourceLocation id = ResourceLocation.tryParse(furniture);
        Block b = id == null ? null : BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
        if (b == null || b == Blocks.AIR) return null;
        BlockState st = b.defaultBlockState();
        if (st.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) st = st.setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
        return st;
    }

    /** "Permanently Closed" (English, red, waxed). Written as NBT: the block entity has no world during generation. */
    private static void writeSign(WorldGenLevel world, BoundingBox box, BlockPos pos) {
        if (!box.isInside(pos) || !(world.getBlockEntity(pos) instanceof SignBlockEntity sign)) return;
        SignText text = new SignText().setMessage(1, Component.literal("Permanently")).setMessage(2, Component.literal("Closed")).setColor(DyeColor.RED);
        CompoundTag nbt = new CompoundTag();
        SignText.DIRECT_CODEC.encodeStart(world.registryAccess().createSerializationContext(NbtOps.INSTANCE), text).resultOrPartial(err ->
                TcgShop.LOGGER.warn("Bankrupt Card Shop sign: {}", err)).ifPresent(e -> nbt.put("front_text", e));
        nbt.putBoolean("is_waxed", true);
        sign.loadWithComponents(nbt, world.registryAccess());
    }
}

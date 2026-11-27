package dev.tcgshop.shop;

import dev.tcgshop.TcgShop;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 1.1 (cleanliness update) blocks: droppings ({@link PoopBlock}), graffiti ({@link GraffitiBlock}), the wall air freshener ({@link WallFreshenerBlock}),
 * the 3-wide storefront shutter ({@link WideDoorBlock}) and ten new decorative objects ({@link DecorPropBlock}).
 * Everything except the droppings is sold by the Furniture Seller and the phone's Furniture app (category "shop",
 * {@link #CATALOG}); the decor objects also give decoration points ({@link DecorScore}). Default prices go into
 * ShopServer's fixed prices (overridable in config/tcgshop-economy.json "itemPrices").
 * Models and textures: tools/gen_clean_assets.py.
 */
public final class CleanBlocks {
    private static final List<Item> CATALOG_LIST = new ArrayList<>();
    private static final Map<Item, Long> PRICE_MAP = new LinkedHashMap<>();
    private static final Map<ResourceLocation, Integer> POINTS = new LinkedHashMap<>();
    /** What the Furniture Seller / Furniture app sells from this file, in catalog order. */
    public static final List<Item> CATALOG = Collections.unmodifiableList(CATALOG_LIST);
    /** Default prices (ShopServer.init copies them into the fixed prices). */
    public static final Map<Item, Long> PRICES = Collections.unmodifiableMap(PRICE_MAP);
    /** The decor objects (subset of the catalog). */
    public static final List<Item> DECOR = new ArrayList<>();

    private static BlockBehaviour.Properties props(SoundType sound, float strength) {
        return BlockBehaviour.Properties.of().strength(strength, 6f).sound(sound).noOcclusion().pushReaction(PushReaction.BLOCK);
    }

    private static VoxelShape box(double x0, double y0, double z0, double x1, double y1, double z1) {
        return Block.box(x0, y0, z0, x1, y1, z1);
    }

    // ---- droppings: no item, unbreakable, no drops, no collision, "solid" so water doesn't wash them, pistons can't move them
    public static final PoopBlock POOP = block("poop", new PoopBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BROWN)
            .strength(-1.0f, 3_600_000.0f).noCollission().noOcclusion().noLootTable().forceSolidOn().randomTicks().sound(SoundType.SLIME_BLOCK)
            .isValidSpawn((st, w, p, type) -> false).pushReaction(PushReaction.BLOCK)));

    // ---- graffiti on shop walls: same rules as the droppings (no item, no drops, cleaned only with the spray / fresheners)
    public static final GraffitiBlock GRAFFITI = block("graffiti", new GraffitiBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE)
            .strength(-1.0f, 3_600_000.0f).noCollission().noOcclusion().noLootTable().forceSolidOn().randomTicks().sound(SoundType.WOOL)
            .isValidSpawn((st, w, p, type) -> false).pushReaction(PushReaction.BLOCK)));

    // ---- wall air freshener
    public static final WallFreshenerBlock WALL_FRESHENER = block("wall_freshener", new WallFreshenerBlock(props(SoundType.METAL, 0.8f)));
    public static final Item WALL_FRESHENER_ITEM = item("wall_freshener", new WallFreshenerBlock.FreshenerItem(WALL_FRESHENER,
            new Item.Properties().stacksTo(1)), 3_500L, 0); // charges in DAMAGE without MAX_DAMAGE: no anvil/grindstone/crafting repair

    // ---- 3-wide storefront shutter
    public static final WideDoorBlock WIDE_DOOR = block("wide_door", new WideDoorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
            .strength(3.0f, 6.0f).noOcclusion().pushReaction(PushReaction.BLOCK)));
    public static final Item WIDE_DOOR_ITEM = item("wide_door", new BlockItem(WIDE_DOOR, new Item.Properties().rarity(Rarity.UNCOMMON)) {
        @Override
        public void appendHoverText(net.minecraft.world.item.ItemStack stack, TooltipContext context, List<net.minecraft.network.chat.Component> tooltip,
                                    net.minecraft.world.item.TooltipFlag type) {
            tooltip.add(net.minecraft.network.chat.Component.translatable("block.tcgshop.wide_door.tip").withStyle(net.minecraft.ChatFormatting.GRAY));
            tooltip.add(net.minecraft.network.chat.Component.translatable("block.tcgshop.wide_door.tip2").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        }
    }, 8_000L, 0);

    // ---- decor objects (north-facing shapes)
    public static final DecorPropBlock POTTED_PALM = decor("potted_palm", SoundType.GRASS, 0, 3, 3_200L,
            new DecorPropBlock.Spec(Shapes.or(box(4, 0, 4, 12, 7, 12), box(2, 7, 2, 14, 16, 14)), false, false));
    public static final DecorPropBlock NEON_SIGN = decor("neon_sign", SoundType.GLASS, 11, 3, 6_500L,
            new DecorPropBlock.Spec(box(1, 3, 14, 15, 13, 16), true, true));
    public static final DecorPropBlock SHOP_RUG = decor("shop_rug", SoundType.WOOL, 0, 2, 1_200L,
            new DecorPropBlock.Spec(box(0, 0, 0, 16, 1, 16), false, true));
    public static final DecorPropBlock WALL_CLOCK = decor("wall_clock", SoundType.WOOD, 0, 2, 2_400L,
            new DecorPropBlock.Spec(box(3, 3, 14, 13, 13, 16), true, true));
    public static final DecorPropBlock GUMBALL_MACHINE = decor("gumball_machine", SoundType.GLASS, 0, 3, 4_500L,
            new DecorPropBlock.Spec(Shapes.or(box(5, 0, 5, 11, 7, 11), box(4, 7, 4, 12, 15, 12)), false, false));
    public static final DecorPropBlock FISH_TANK = decor("fish_tank", SoundType.GLASS, 6, 4, 9_000L,
            new DecorPropBlock.Spec(box(1, 0, 3, 15, 14, 13), false, false));
    public static final DecorPropBlock FLOOR_LAMP = decor("floor_lamp", SoundType.LANTERN, 14, 2, 3_000L,
            new DecorPropBlock.Spec(Shapes.or(box(5, 0, 5, 11, 2, 11), box(7, 2, 7, 9, 12, 9), box(4, 12, 4, 12, 16, 12)), false, false));
    public static final DecorPropBlock CARD_BANNER = decor("card_banner", SoundType.WOOL, 0, 2, 1_800L,
            new DecorPropBlock.Spec(box(2, 0, 15, 14, 16, 16), true, true));
    public static final DecorPropBlock SHOPKEEPER_STANDEE = decor("shopkeeper_standee", SoundType.WOOD, 0, 3, 5_000L,
            new DecorPropBlock.Spec(box(3, 0, 7, 13, 16, 9), false, false));
    public static final DecorPropBlock WELCOME_MAT = decor("welcome_mat", SoundType.WOOL, 0, 1, 900L,
            new DecorPropBlock.Spec(box(1, 0, 3, 15, 1, 13), false, true));

    public static final BlockEntityType<WallFreshenerBlock.FreshenerEntity> FRESHENER_BE = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(TcgShop.MOD_ID, "wall_freshener"),
            BlockEntityType.Builder.of(WallFreshenerBlock.FreshenerEntity::new, WALL_FRESHENER).build(null));

    private CleanBlocks() {}

    private static <T extends Block> T block(String name, T block) {
        return Registry.register(BuiltInRegistries.BLOCK, ResourceLocation.fromNamespaceAndPath(TcgShop.MOD_ID, name), block);
    }

    private static Item item(String name, Item item, long price, int points) {
        Item it = Registry.register(BuiltInRegistries.ITEM, ResourceLocation.fromNamespaceAndPath(TcgShop.MOD_ID, name), item);
        CATALOG_LIST.add(it);
        PRICE_MAP.put(it, price);
        POINTS.put(ResourceLocation.fromNamespaceAndPath(TcgShop.MOD_ID, name), points);
        return it;
    }

    private static DecorPropBlock decor(String name, SoundType sound, int light, int points, long price, DecorPropBlock.Spec spec) {
        BlockBehaviour.Properties p = props(sound, 1.0f).lightLevel(s -> light);
        if (spec.walkThrough()) p = p.noCollission();
        DecorPropBlock b = block(name, new DecorPropBlock(p, spec));
        DECOR.add(item(name, new DecorPropBlock.PropItem(b, new Item.Properties()), price, points));
        return b;
    }

    /** Is this one of ours that the Furniture Seller sells? */
    public static boolean isCatalog(ResourceLocation id) {
        return id != null && BuiltInRegistries.ITEM.containsKey(id) && CATALOG_LIST.contains(BuiltInRegistries.ITEM.get(id));
    }

    /** Call from TcgShop.init (after TcgBlocks): loads the config and gives the decor objects their decoration points. */
    public static void init() {
        CleanConfig.load();
        dev.masterball.api.platform.event.ServerEvents.STOPPED.register(s -> Cleanliness.reset());
        POINTS.forEach((id, pts) -> { if (pts > 0) DecorScore.register(id, pts); });
    }
}

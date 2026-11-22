package dev.tcgshop.shop;

import com.mojang.serialization.Codec;
import dev.tcgshop.TcgShop;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.component.ComponentType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;

import java.util.EnumMap;
import java.util.Map;

/** Bloques de la tienda: zonas por nivel, expositores, decoración, caja registradora, ruleta y cajas de sobres. */
public final class TcgBlocks {
    public static final ComponentType<String> BOX_SET = Registry.register(Registries.DATA_COMPONENT_TYPE,
            Identifier.of(TcgShop.MOD_ID, "box_set"),
            ComponentType.<String>builder().codec(Codec.STRING).packetCodec(PacketCodecs.STRING).build());

    public static final Map<ShopTier, ShopCoreBlock> CORES = new EnumMap<>(ShopTier.class);
    public static final Map<ShopTier, Item> CORE_ITEMS = new EnumMap<>(ShopTier.class);

    static {
        for (ShopTier t : ShopTier.values()) {
            ShopCoreBlock b = register("shop_core_" + t.key, new ShopCoreBlock(t, AbstractBlock.Settings.create()
                    .mapColor(MapColor.OAK_TAN).strength(2.5f, 1200f).sounds(t == ShopTier.WOOD ? BlockSoundGroup.WOOD : BlockSoundGroup.METAL)));
            CORES.put(t, b);
            CORE_ITEMS.put(t, item("shop_core_" + t.key, new BlockItem(b, new Item.Settings().maxCount(1)
                    .rarity(t.ordinal() >= 5 ? Rarity.EPIC : t.ordinal() >= 3 ? Rarity.RARE : Rarity.COMMON))));
        }
    }

    private static AbstractBlock.Settings furniture(BlockSoundGroup sound) {
        return AbstractBlock.Settings.create().strength(1.5f, 6f).sounds(sound).nonOpaque()
                .pistonBehavior(net.minecraft.block.piston.PistonBehavior.BLOCK);
    }

    // --- expositores ---
    public static final ShopDisplayBlock CARD_STAND = register("card_stand", new ShopDisplayBlock(DisplayKind.CARD_STAND, furniture(BlockSoundGroup.WOOD)));
    public static final ShopDisplayBlock DISPLAY_CASE = register("display_case", new ShopDisplayBlock(DisplayKind.DISPLAY_CASE, furniture(BlockSoundGroup.GLASS)));
    public static final ShopDisplayBlock PACK_SHELF = register("pack_shelf", new ShopDisplayBlock(DisplayKind.PACK_SHELF, furniture(BlockSoundGroup.WOOD)));
    public static final ShopDisplayBlock GONDOLA = register("gondola", new ShopDisplayBlock(DisplayKind.GONDOLA, furniture(BlockSoundGroup.METAL)));
    public static final ShopDisplayBlock BOX_PALLET = register("box_pallet", new ShopDisplayBlock(DisplayKind.BOX_PALLET, furniture(BlockSoundGroup.WOOD)));
    public static final ShopDisplayBlock PEDESTAL = register("pedestal", new ShopDisplayBlock(DisplayKind.PEDESTAL,
            furniture(BlockSoundGroup.STONE).luminance(s -> 8)));
    public static final ShopDisplayBlock CARD_WALL = register("card_wall", new ShopDisplayBlock(DisplayKind.CARD_WALL, furniture(BlockSoundGroup.WOOD)));
    public static final ShopDisplayBlock COUNTER_DISPLAY = register("counter_display", new ShopDisplayBlock(DisplayKind.COUNTER, furniture(BlockSoundGroup.WOOD)));
    public static final ShopDisplayBlock SHOWCASE = register("showcase", new ShopDisplayBlock(DisplayKind.SHOWCASE, furniture(BlockSoundGroup.GLASS).luminance(st -> 10)));
    public static final ShopDisplayBlock CARD_LEDGE = register("card_ledge", new ShopDisplayBlock(DisplayKind.LEDGE, furniture(BlockSoundGroup.WOOD)));
    public static final ShopDisplayBlock BOX_SHELF = register("box_shelf", new ShopDisplayBlock(DisplayKind.BOX_SHELF, furniture(BlockSoundGroup.WOOD)));
    public static final ShopDisplayBlock CARD_FRAME = register("card_frame", new ShopDisplayBlock(DisplayKind.FRAME, furniture(BlockSoundGroup.WOOD).noCollision()));
    // --- expositores de casa (cualquier sitio, sin venta)
    public static final ShopDisplayBlock HOME_FRAME = register("home_frame", new ShopDisplayBlock(DisplayKind.FRAME, true, furniture(BlockSoundGroup.WOOD).noCollision()));
    public static final ShopDisplayBlock HOME_LEDGE = register("home_ledge", new ShopDisplayBlock(DisplayKind.LEDGE, true, furniture(BlockSoundGroup.WOOD)));
    public static final ShopDisplayBlock HOME_SHOWCASE = register("home_showcase", new ShopDisplayBlock(DisplayKind.SHOWCASE, true, furniture(BlockSoundGroup.GLASS).luminance(st -> 10)));
    public static final ShopDisplayBlock HOME_CARD_WALL = register("home_card_wall", new ShopDisplayBlock(DisplayKind.CARD_WALL, true, furniture(BlockSoundGroup.WOOD)));
    public static final ShopDisplayBlock HOME_PEDESTAL = register("home_pedestal", new ShopDisplayBlock(DisplayKind.PEDESTAL, true, furniture(BlockSoundGroup.STONE).luminance(st -> 8)));
    // --- decoración / servicio ---
    public static final DecorBlocks.RegisterBlock CASH_REGISTER = register("cash_register", new DecorBlocks.RegisterBlock(furniture(BlockSoundGroup.METAL)));
    public static final DecorBlocks.PosterBlock POSTER = register("poster", new DecorBlocks.PosterBlock(furniture(BlockSoundGroup.WOOL).noCollision()));
    public static final DecorBlocks.PlayTableBlock PLAY_TABLE = register("play_table", new DecorBlocks.PlayTableBlock(furniture(BlockSoundGroup.WOOD)));
    public static final DecorBlocks.TheftDetectorBlock THEFT_DETECTOR = register("theft_detector", new DecorBlocks.TheftDetectorBlock(furniture(BlockSoundGroup.METAL)
            .luminance(s -> s.get(DecorBlocks.TheftDetectorBlock.ALARM) ? 15 : 3)));
    public static final DecorBlocks.RouletteBlock ROULETTE = register("roulette", new DecorBlocks.RouletteBlock(furniture(BlockSoundGroup.WOOD)));
    public static final BoosterBox.BoxBlock BOOSTER_BOX = register("booster_box", new BoosterBox.BoxBlock(
            AbstractBlock.Settings.create().strength(0.6f).sounds(BlockSoundGroup.WOOL).nonOpaque()));
    /**
     * 0.9 eventos: incidente (charco, escombros, chispas...). Sin objeto ni pestaña creativa; irrompible a mano, sin
     * drops, sin colisión, "sólido" para que el agua no lo arrastre, no inflamable, los pistones no lo mueven.
     */
    public static final dev.tcgshop.events.incident.IncidentBlock INCIDENT = register("incident", new dev.tcgshop.events.incident.IncidentBlock(
            AbstractBlock.Settings.create().mapColor(MapColor.CLEAR).strength(-1.0f, 3_600_000.0f).noCollision().nonOpaque().dropsNothing()
                    .solid().ticksRandomly().sounds(BlockSoundGroup.WOOL).allowsSpawning((st, w, p, type) -> false)
                    .luminance(st -> st.get(dev.tcgshop.events.incident.IncidentBlock.KIND) == dev.tcgshop.events.incident.IncidentKind.SPARKS ? 4 : 0)
                    .pistonBehavior(net.minecraft.block.piston.PistonBehavior.BLOCK)));

    public static final Item CARD_STAND_ITEM = item("card_stand", new BlockItem(CARD_STAND, new Item.Settings()));
    public static final Item DISPLAY_CASE_ITEM = item("display_case", new BlockItem(DISPLAY_CASE, new Item.Settings()));
    public static final Item PACK_SHELF_ITEM = item("pack_shelf", new BlockItem(PACK_SHELF, new Item.Settings()));
    public static final Item GONDOLA_ITEM = item("gondola", new BlockItem(GONDOLA, new Item.Settings()));
    public static final Item BOX_PALLET_ITEM = item("box_pallet", new BlockItem(BOX_PALLET, new Item.Settings()));
    public static final Item PEDESTAL_ITEM = item("pedestal", new BlockItem(PEDESTAL, new Item.Settings().rarity(Rarity.UNCOMMON)));
    public static final Item CARD_WALL_ITEM = item("card_wall", new BlockItem(CARD_WALL, new Item.Settings().rarity(Rarity.UNCOMMON)));
    public static final Item COUNTER_DISPLAY_ITEM = item("counter_display", new BlockItem(COUNTER_DISPLAY, new Item.Settings()));
    public static final Item SHOWCASE_ITEM = item("showcase", new BlockItem(SHOWCASE, new Item.Settings().rarity(Rarity.UNCOMMON)));
    public static final Item CARD_LEDGE_ITEM = item("card_ledge", new BlockItem(CARD_LEDGE, new Item.Settings()));
    public static final Item BOX_SHELF_ITEM = item("box_shelf", new BlockItem(BOX_SHELF, new Item.Settings()));
    public static final Item CARD_FRAME_ITEM = item("card_frame", new BlockItem(CARD_FRAME, new Item.Settings()));
    public static final Item HOME_FRAME_ITEM = item("home_frame", new BlockItem(HOME_FRAME, new Item.Settings()));
    public static final Item HOME_LEDGE_ITEM = item("home_ledge", new BlockItem(HOME_LEDGE, new Item.Settings()));
    public static final Item HOME_SHOWCASE_ITEM = item("home_showcase", new BlockItem(HOME_SHOWCASE, new Item.Settings().rarity(Rarity.UNCOMMON)));
    public static final Item HOME_CARD_WALL_ITEM = item("home_card_wall", new BlockItem(HOME_CARD_WALL, new Item.Settings().rarity(Rarity.UNCOMMON)));
    public static final Item HOME_PEDESTAL_ITEM = item("home_pedestal", new BlockItem(HOME_PEDESTAL, new Item.Settings().rarity(Rarity.UNCOMMON)));
    /** Muebles nuevos (para el catálogo y la pestaña creativa). */
    public static final Item[] NEW_SHOP_DISPLAYS = {COUNTER_DISPLAY_ITEM, SHOWCASE_ITEM, CARD_LEDGE_ITEM, BOX_SHELF_ITEM, CARD_FRAME_ITEM};
    public static final Item[] HOME_DISPLAYS = {HOME_FRAME_ITEM, HOME_LEDGE_ITEM, HOME_SHOWCASE_ITEM, HOME_CARD_WALL_ITEM, HOME_PEDESTAL_ITEM};
    public static final Item CASH_REGISTER_ITEM = item("cash_register", new BlockItem(CASH_REGISTER, new Item.Settings()));
    public static final Item POSTER_ITEM = item("poster", new BlockItem(POSTER, new Item.Settings()));
    public static final Item PLAY_TABLE_ITEM = item("play_table", new BlockItem(PLAY_TABLE, new Item.Settings()));
    public static final Item THEFT_DETECTOR_ITEM = item("theft_detector", new BlockItem(THEFT_DETECTOR, new Item.Settings()));
    public static final Item ROULETTE_ITEM = item("roulette", new BlockItem(ROULETTE, new Item.Settings().rarity(Rarity.EPIC)));
    public static final Item BOOSTER_BOX_ITEM = item("booster_box", new BoosterBox.BoxItem(BOOSTER_BOX, new Item.Settings().maxCount(16)
            .component(BOX_SET, "mew151")));
    public static final Item MYSTERY_BOX = item("mystery_box", new MysteryBoxItem(new Item.Settings().maxCount(16).rarity(Rarity.RARE)));

    public static final BlockEntityType<ShopDisplayBlockEntity> DISPLAY_BE = Registry.register(Registries.BLOCK_ENTITY_TYPE,
            Identifier.of(TcgShop.MOD_ID, "shop_display"),
            BlockEntityType.Builder.create(ShopDisplayBlockEntity::new, CARD_STAND, DISPLAY_CASE, PACK_SHELF, GONDOLA, BOX_PALLET, PEDESTAL, CARD_WALL,
                    COUNTER_DISPLAY, SHOWCASE, CARD_LEDGE, BOX_SHELF, CARD_FRAME, HOME_FRAME, HOME_LEDGE, HOME_SHOWCASE, HOME_CARD_WALL, HOME_PEDESTAL).build(null));
    public static final BlockEntityType<BoosterBox.BoxEntity> BOX_BE = Registry.register(Registries.BLOCK_ENTITY_TYPE,
            Identifier.of(TcgShop.MOD_ID, "booster_box"),
            BlockEntityType.Builder.create(BoosterBox.BoxEntity::new, BOOSTER_BOX).build(null));
    public static final BlockEntityType<DecorBlocks.DecorEntity> DECOR_BE = Registry.register(Registries.BLOCK_ENTITY_TYPE,
            Identifier.of(TcgShop.MOD_ID, "decor"),
            BlockEntityType.Builder.create(DecorBlocks.DecorEntity::new, CASH_REGISTER, POSTER, PLAY_TABLE, THEFT_DETECTOR, ROULETTE).build(null));
    public static final BlockEntityType<dev.tcgshop.events.incident.IncidentBlockEntity> INCIDENT_BE = Registry.register(Registries.BLOCK_ENTITY_TYPE,
            Identifier.of(TcgShop.MOD_ID, "incident"),
            BlockEntityType.Builder.create(dev.tcgshop.events.incident.IncidentBlockEntity::new, INCIDENT).build(null));

    private TcgBlocks() {}

    private static <T extends Block> T register(String name, T block) {
        return Registry.register(Registries.BLOCK, Identifier.of(TcgShop.MOD_ID, name), block);
    }

    private static Item item(String name, Item item) {
        return Registry.register(Registries.ITEM, Identifier.of(TcgShop.MOD_ID, name), item);
    }

    public static void init() {}
}

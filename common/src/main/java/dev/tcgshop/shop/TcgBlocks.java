package dev.tcgshop.shop;

import com.mojang.serialization.Codec;
import dev.tcgshop.TcgShop;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/** Shop blocks: tiered zones, displays, decoration, cash register, roulette and booster boxes. */
public final class TcgBlocks {
    public static final DataComponentType<String> BOX_SET = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
            ResourceLocation.fromNamespaceAndPath(TcgShop.MOD_ID, "box_set"),
            DataComponentType.<String>builder().persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8).build());
    /**
     * 1.1: on a shop core dropped by a dormant shop: {zone: UUID, owner, tier, size}. Placing it (its owner) restores that
     * shop (ShopOpenClose.tryRestore); the rest is only for the tooltip (ShopCoreItem).
     */
    public static final DataComponentType<net.minecraft.nbt.CompoundTag> SHOP_LINK = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
            ResourceLocation.fromNamespaceAndPath(TcgShop.MOD_ID, "shop_link"),
            DataComponentType.<net.minecraft.nbt.CompoundTag>builder().persistent(net.minecraft.nbt.CompoundTag.CODEC)
                    .networkSynchronized(ByteBufCodecs.COMPOUND_TAG).build());

    public static final Map<ShopTier, ShopCoreBlock> CORES = new EnumMap<>(ShopTier.class);
    public static final Map<ShopTier, Item> CORE_ITEMS = new EnumMap<>(ShopTier.class);

    static {
        // 1.0: 12 tiers (Paper ... Netherite); ids are "shop_core_<key>", so the pre-1.0 blocks keep theirs
        for (ShopTier t : ShopTier.values()) {
            ShopCoreBlock b = register("shop_core_" + t.key, new ShopCoreBlock(t, BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD).strength(2.5f, 1200f).sound(coreSound(t))
                    .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK))); // 1.1: pistons never move or remove it
            CORES.put(t, b);
            CORE_ITEMS.put(t, item("shop_core_" + t.key, new ShopCoreItem(b, new Item.Properties().stacksTo(1)
                    .rarity(t.atLeast(ShopTier.EMERALD) ? Rarity.EPIC : t.atLeast(ShopTier.GOLD) ? Rarity.RARE
                            : t.atLeast(ShopTier.COPPER) ? Rarity.UNCOMMON : Rarity.COMMON))));
        }
    }

    private static SoundType coreSound(ShopTier t) {
        return switch (t) {
            case PAPER, WOOD -> SoundType.WOOD;
            case BRICK, STONE -> SoundType.STONE;
            case AMETHYST -> SoundType.AMETHYST;
            case COPPER -> SoundType.COPPER;
            default -> SoundType.METAL;
        };
    }

    private static BlockBehaviour.Properties furniture(SoundType sound) {
        return BlockBehaviour.Properties.of().strength(1.5f, 6f).sound(sound).noOcclusion()
                .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK);
    }

    // --- displays ---
    public static final ShopDisplayBlock CARD_STAND = register("card_stand", new ShopDisplayBlock(DisplayKind.CARD_STAND, furniture(SoundType.WOOD)));
    public static final ShopDisplayBlock DISPLAY_CASE = register("display_case", new ShopDisplayBlock(DisplayKind.DISPLAY_CASE, furniture(SoundType.GLASS)));
    public static final ShopDisplayBlock PACK_SHELF = register("pack_shelf", new ShopDisplayBlock(DisplayKind.PACK_SHELF, furniture(SoundType.WOOD)));
    public static final ShopDisplayBlock GONDOLA = register("gondola", new ShopDisplayBlock(DisplayKind.GONDOLA, furniture(SoundType.METAL)));
    public static final ShopDisplayBlock BOX_PALLET = register("box_pallet", new ShopDisplayBlock(DisplayKind.BOX_PALLET, furniture(SoundType.WOOD)));
    public static final ShopDisplayBlock PEDESTAL = register("pedestal", new ShopDisplayBlock(DisplayKind.PEDESTAL,
            furniture(SoundType.STONE).lightLevel(s -> 8)));
    public static final ShopDisplayBlock CARD_WALL = register("card_wall", new ShopDisplayBlock(DisplayKind.CARD_WALL, furniture(SoundType.WOOD)));
    public static final ShopDisplayBlock COUNTER_DISPLAY = register("counter_display", new ShopDisplayBlock(DisplayKind.COUNTER, furniture(SoundType.WOOD)));
    public static final ShopDisplayBlock SHOWCASE = register("showcase", new ShopDisplayBlock(DisplayKind.SHOWCASE, furniture(SoundType.GLASS).lightLevel(st -> 10)));
    public static final ShopDisplayBlock CARD_LEDGE = register("card_ledge", new ShopDisplayBlock(DisplayKind.LEDGE, furniture(SoundType.WOOD)));
    public static final ShopDisplayBlock BOX_SHELF = register("box_shelf", new ShopDisplayBlock(DisplayKind.BOX_SHELF, furniture(SoundType.WOOD)));
    public static final ShopDisplayBlock CARD_FRAME = register("card_frame", new ShopDisplayBlock(DisplayKind.FRAME, furniture(SoundType.WOOD).noCollission()));
    // --- home displays (anywhere, no selling)
    public static final ShopDisplayBlock HOME_FRAME = register("home_frame", new ShopDisplayBlock(DisplayKind.FRAME, true, furniture(SoundType.WOOD).noCollission()));
    public static final ShopDisplayBlock HOME_LEDGE = register("home_ledge", new ShopDisplayBlock(DisplayKind.LEDGE, true, furniture(SoundType.WOOD)));
    public static final ShopDisplayBlock HOME_SHOWCASE = register("home_showcase", new ShopDisplayBlock(DisplayKind.SHOWCASE, true, furniture(SoundType.GLASS).lightLevel(st -> 10)));
    public static final ShopDisplayBlock HOME_CARD_WALL = register("home_card_wall", new ShopDisplayBlock(DisplayKind.CARD_WALL, true, furniture(SoundType.WOOD)));
    public static final ShopDisplayBlock HOME_PEDESTAL = register("home_pedestal", new ShopDisplayBlock(DisplayKind.PEDESTAL, true, furniture(SoundType.STONE).lightLevel(st -> 8)));
    // --- decoration / service ---
    public static final DecorBlocks.RegisterBlock CASH_REGISTER = register("cash_register", new DecorBlocks.RegisterBlock(furniture(SoundType.METAL)));
    public static final DecorBlocks.PosterBlock POSTER = register("poster", new DecorBlocks.PosterBlock(furniture(SoundType.WOOL).noCollission()));
    public static final DecorBlocks.PlayTableBlock PLAY_TABLE = register("play_table", new DecorBlocks.PlayTableBlock(furniture(SoundType.WOOD)));
    public static final DecorBlocks.TheftDetectorBlock THEFT_DETECTOR = register("theft_detector", new DecorBlocks.TheftDetectorBlock(furniture(SoundType.METAL)
            .lightLevel(s -> s.getValue(DecorBlocks.TheftDetectorBlock.ALARM) ? 15 : 3)));
    public static final DecorBlocks.RouletteBlock ROULETTE = register("roulette", new DecorBlocks.RouletteBlock(furniture(SoundType.WOOD)));
    public static final BoosterBox.BoxBlock BOOSTER_BOX = register("booster_box", new BoosterBox.BoxBlock(
            BlockBehaviour.Properties.of().strength(0.6f).sound(SoundType.WOOL).noOcclusion()));
    /**
     * 0.9 events: incident (puddle, debris, sparks...). No item or creative tab; unbreakable by hand, no
     * drops, no collision, "solid" so water does not wash it away, non-flammable, pistons cannot move it.
     */
    public static final dev.tcgshop.events.incident.IncidentBlock INCIDENT = register("incident", new dev.tcgshop.events.incident.IncidentBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(-1.0f, 3_600_000.0f).noCollission().noOcclusion().noLootTable()
                    .forceSolidOn().randomTicks().sound(SoundType.WOOL).isValidSpawn((st, w, p, type) -> false)
                    .lightLevel(st -> st.getValue(dev.tcgshop.events.incident.IncidentBlock.KIND) == dev.tcgshop.events.incident.IncidentKind.SPARKS ? 4 : 0)
                    .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)));

    public static final Item CARD_STAND_ITEM = item("card_stand", new BlockItem(CARD_STAND, new Item.Properties()));
    public static final Item DISPLAY_CASE_ITEM = item("display_case", new BlockItem(DISPLAY_CASE, new Item.Properties()));
    public static final Item PACK_SHELF_ITEM = item("pack_shelf", new BlockItem(PACK_SHELF, new Item.Properties()));
    public static final Item GONDOLA_ITEM = item("gondola", new BlockItem(GONDOLA, new Item.Properties()));
    public static final Item BOX_PALLET_ITEM = item("box_pallet", new BlockItem(BOX_PALLET, new Item.Properties()));
    public static final Item PEDESTAL_ITEM = item("pedestal", new BlockItem(PEDESTAL, new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final Item CARD_WALL_ITEM = item("card_wall", new BlockItem(CARD_WALL, new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final Item COUNTER_DISPLAY_ITEM = item("counter_display", new BlockItem(COUNTER_DISPLAY, new Item.Properties()));
    public static final Item SHOWCASE_ITEM = item("showcase", new BlockItem(SHOWCASE, new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final Item CARD_LEDGE_ITEM = item("card_ledge", new BlockItem(CARD_LEDGE, new Item.Properties()));
    public static final Item BOX_SHELF_ITEM = item("box_shelf", new BlockItem(BOX_SHELF, new Item.Properties()));
    public static final Item CARD_FRAME_ITEM = item("card_frame", new BlockItem(CARD_FRAME, new Item.Properties()));
    public static final Item HOME_FRAME_ITEM = item("home_frame", new BlockItem(HOME_FRAME, new Item.Properties()));
    public static final Item HOME_LEDGE_ITEM = item("home_ledge", new BlockItem(HOME_LEDGE, new Item.Properties()));
    public static final Item HOME_SHOWCASE_ITEM = item("home_showcase", new BlockItem(HOME_SHOWCASE, new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final Item HOME_CARD_WALL_ITEM = item("home_card_wall", new BlockItem(HOME_CARD_WALL, new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final Item HOME_PEDESTAL_ITEM = item("home_pedestal", new BlockItem(HOME_PEDESTAL, new Item.Properties().rarity(Rarity.UNCOMMON)));
    /** New furniture (for the catalog and the creative tab). */
    public static final Item[] NEW_SHOP_DISPLAYS = {COUNTER_DISPLAY_ITEM, SHOWCASE_ITEM, CARD_LEDGE_ITEM, BOX_SHELF_ITEM, CARD_FRAME_ITEM};
    public static final Item[] HOME_DISPLAYS = {HOME_FRAME_ITEM, HOME_LEDGE_ITEM, HOME_SHOWCASE_ITEM, HOME_CARD_WALL_ITEM, HOME_PEDESTAL_ITEM};
    public static final Item CASH_REGISTER_ITEM = item("cash_register", new BlockItem(CASH_REGISTER, new Item.Properties()));
    public static final Item POSTER_ITEM = item("poster", new BlockItem(POSTER, new Item.Properties()));
    public static final Item PLAY_TABLE_ITEM = item("play_table", new BlockItem(PLAY_TABLE, new Item.Properties()));
    public static final Item THEFT_DETECTOR_ITEM = item("theft_detector", new BlockItem(THEFT_DETECTOR, new Item.Properties()));
    public static final Item ROULETTE_ITEM = item("roulette", new BlockItem(ROULETTE, new Item.Properties().rarity(Rarity.EPIC)));
    public static final Item BOOSTER_BOX_ITEM = item("booster_box", new BoosterBox.BoxItem(BOOSTER_BOX, new Item.Properties().stacksTo(16)
            .component(BOX_SET, "mew151")));
    public static final Item MYSTERY_BOX = item("mystery_box", new MysteryBoxItem(new Item.Properties().stacksTo(16).rarity(Rarity.RARE)));

    public static final BlockEntityType<ShopDisplayBlockEntity> DISPLAY_BE = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(TcgShop.MOD_ID, "shop_display"),
            BlockEntityType.Builder.of(ShopDisplayBlockEntity::new, RgbDisplays.with(CARD_STAND, DISPLAY_CASE, PACK_SHELF, GONDOLA, BOX_PALLET, PEDESTAL, CARD_WALL,
                    COUNTER_DISPLAY, SHOWCASE, CARD_LEDGE, BOX_SHELF, CARD_FRAME, HOME_FRAME, HOME_LEDGE, HOME_SHOWCASE, HOME_CARD_WALL, HOME_PEDESTAL)).build(null)); // 1.0: + RGB displays
    public static final BlockEntityType<BoosterBox.BoxEntity> BOX_BE = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(TcgShop.MOD_ID, "booster_box"),
            BlockEntityType.Builder.of(BoosterBox.BoxEntity::new, BOOSTER_BOX).build(null));
    public static final BlockEntityType<DecorBlocks.DecorEntity> DECOR_BE = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(TcgShop.MOD_ID, "decor"),
            BlockEntityType.Builder.of(DecorBlocks.DecorEntity::new, CASH_REGISTER, POSTER, PLAY_TABLE, THEFT_DETECTOR, ROULETTE).build(null));
    public static final BlockEntityType<dev.tcgshop.events.incident.IncidentBlockEntity> INCIDENT_BE = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(TcgShop.MOD_ID, "incident"),
            BlockEntityType.Builder.of(dev.tcgshop.events.incident.IncidentBlockEntity::new, INCIDENT).build(null));

    private TcgBlocks() {}

    private static <T extends Block> T register(String name, T block) {
        return Registry.register(BuiltInRegistries.BLOCK, ResourceLocation.fromNamespaceAndPath(TcgShop.MOD_ID, name), block);
    }

    private static Item item(String name, Item item) {
        return Registry.register(BuiltInRegistries.ITEM, ResourceLocation.fromNamespaceAndPath(TcgShop.MOD_ID, name), item);
    }

    public static void init() {}
}

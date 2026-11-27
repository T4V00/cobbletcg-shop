package dev.tcgshop.luxury;

import dev.tcgshop.TcgShop;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * Registration of the luxury statues (v0.9). Call {@link #register()} from TcgShop.onInitialize (before the
 * creative tab is built or right after TcgBlocks.init: the static registrations happen when the class loads).
 */
public final class LuxuryBlocks {
    /** All statues (ShopLife can count them as decoration: {@code state.isIn(LuxuryBlocks.STATUES_TAG)}). */
    public static final TagKey<Block> STATUES_TAG = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(TcgShop.MOD_ID, "luxury_statues"));
    public static final TagKey<Item> STATUE_ITEMS_TAG = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(TcgShop.MOD_ID, "luxury_statues"));

    public static final Map<StatueTier, StatueBlock> BLOCKS = new EnumMap<>(StatueTier.class);
    public static final Map<StatueTier, StatueItem> ITEMS = new EnumMap<>(StatueTier.class);
    private static final List<Item> ITEM_LIST = new ArrayList<>();

    public static final StatuePartBlock STATUE_PART;
    /** 0.9.5: MasterBall Mansion Key (Exclusives, the most expensive piece). */
    public static final dev.tcgshop.luxury.house.MansionKeyItem MANSION_KEY;

    static {
        for (StatueTier t : StatueTier.values()) {
            StatueBlock b = Registry.register(BuiltInRegistries.BLOCK, ResourceLocation.fromNamespaceAndPath(TcgShop.MOD_ID, t.blockName()), new StatueBlock(t, settings(t)));
            StatueItem it = Registry.register(BuiltInRegistries.ITEM, ResourceLocation.fromNamespaceAndPath(TcgShop.MOD_ID, t.blockName()),
                    new StatueItem(b, new Item.Properties().stacksTo(1).fireResistant().rarity(t.rarity)));
            BLOCKS.put(t, b);
            ITEMS.put(t, it);
            ITEM_LIST.add(it);
        }
        STATUE_PART = Registry.register(BuiltInRegistries.BLOCK, ResourceLocation.fromNamespaceAndPath(TcgShop.MOD_ID, "statue_part"),
                new StatuePartBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(3.0f, 1200f)
                        .sound(SoundType.METAL).noOcclusion().noLootTable().pushReaction(PushReaction.BLOCK)
                        .lightLevel(s -> {
                            StatueTier t = StatueTier.byIndex(s.getValue(StatuePartBlock.TIER));
                            return t == null ? 0 : t.light;
                        })));
        MANSION_KEY = Registry.register(BuiltInRegistries.ITEM, ResourceLocation.fromNamespaceAndPath(TcgShop.MOD_ID, "mansion_key"),
                new dev.tcgshop.luxury.house.MansionKeyItem(new Item.Properties().stacksTo(1).fireResistant().rarity(net.minecraft.world.item.Rarity.EPIC)));
        ITEM_LIST.add(MANSION_KEY); // creative tab, after the statues
    }

    private static BlockBehaviour.Properties settings(StatueTier t) {
        MapColor color = switch (t) {
            case BRONZE -> MapColor.COLOR_ORANGE;
            case SILVER -> MapColor.METAL;
            case GOLD -> MapColor.GOLD;
            case EMERALD -> MapColor.EMERALD;
            case DIAMOND -> MapColor.DIAMOND;
            case NETHERITE -> MapColor.COLOR_PURPLE;
            case MASTERBALL -> MapColor.COLOR_MAGENTA;
        };
        // extremely high blast resistance and no pistons: it is a very expensive investment
        return BlockBehaviour.Properties.of().mapColor(color).strength(3.0f, 1200f)
                .sound(t.ordinal() >= 4 ? SoundType.AMETHYST : SoundType.METAL)
                .noOcclusion().pushReaction(PushReaction.BLOCK).lightLevel(s -> t.light);
    }

    private LuxuryBlocks() {}

    public static StatueItem item(StatueTier t) {
        return ITEMS.get(t);
    }

    /** Items in tier order (for the creative tab). */
    public static List<Item> items() {
        return Collections.unmodifiableList(ITEM_LIST);
    }

    /**
     * Forces the class to load (registrations), reads config/tcgshop-luxury.json and hooks up the zone protection for the
     * pieces and the syncing of sale info with clients.
     */
    public static void register() {
        LuxuryConfig.load();
        StatueProtection.register();
        LuxuryShop.registerSync();
        dev.tcgshop.luxury.house.MansionService.register();
    }
}

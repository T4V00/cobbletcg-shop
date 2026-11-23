package dev.tcgshop.luxury;

import dev.tcgshop.TcgShop;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Registration of the luxury statues (v0.9). Call {@link #register()} from TcgShop.onInitialize (before the
 * creative tab is built or right after TcgBlocks.init: the static registrations happen when the class loads).
 */
public final class LuxuryBlocks {
    /** All statues (ShopLife can count them as decoration: {@code state.isIn(LuxuryBlocks.STATUES_TAG)}). */
    public static final TagKey<Block> STATUES_TAG = TagKey.of(RegistryKeys.BLOCK, Identifier.of(TcgShop.MOD_ID, "luxury_statues"));
    public static final TagKey<Item> STATUE_ITEMS_TAG = TagKey.of(RegistryKeys.ITEM, Identifier.of(TcgShop.MOD_ID, "luxury_statues"));

    public static final Map<StatueTier, StatueBlock> BLOCKS = new EnumMap<>(StatueTier.class);
    public static final Map<StatueTier, StatueItem> ITEMS = new EnumMap<>(StatueTier.class);
    private static final List<Item> ITEM_LIST = new ArrayList<>();

    public static final StatuePartBlock STATUE_PART;
    /** 0.9.5: MasterBall Mansion Key (Exclusives, the most expensive piece). */
    public static final dev.tcgshop.luxury.house.MansionKeyItem MANSION_KEY;

    static {
        for (StatueTier t : StatueTier.values()) {
            StatueBlock b = Registry.register(Registries.BLOCK, Identifier.of(TcgShop.MOD_ID, t.blockName()), new StatueBlock(t, settings(t)));
            StatueItem it = Registry.register(Registries.ITEM, Identifier.of(TcgShop.MOD_ID, t.blockName()),
                    new StatueItem(b, new Item.Settings().maxCount(1).fireproof().rarity(t.rarity)));
            BLOCKS.put(t, b);
            ITEMS.put(t, it);
            ITEM_LIST.add(it);
        }
        STATUE_PART = Registry.register(Registries.BLOCK, Identifier.of(TcgShop.MOD_ID, "statue_part"),
                new StatuePartBlock(AbstractBlock.Settings.create().mapColor(MapColor.CLEAR).strength(3.0f, 1200f)
                        .sounds(BlockSoundGroup.METAL).nonOpaque().dropsNothing().pistonBehavior(PistonBehavior.BLOCK)
                        .luminance(s -> {
                            StatueTier t = StatueTier.byIndex(s.get(StatuePartBlock.TIER));
                            return t == null ? 0 : t.light;
                        })));
        MANSION_KEY = Registry.register(Registries.ITEM, Identifier.of(TcgShop.MOD_ID, "mansion_key"),
                new dev.tcgshop.luxury.house.MansionKeyItem(new Item.Settings().maxCount(1).fireproof().rarity(net.minecraft.util.Rarity.EPIC)));
        ITEM_LIST.add(MANSION_KEY); // creative tab, after the statues
    }

    private static AbstractBlock.Settings settings(StatueTier t) {
        MapColor color = switch (t) {
            case BRONZE -> MapColor.ORANGE;
            case SILVER -> MapColor.IRON_GRAY;
            case GOLD -> MapColor.GOLD;
            case EMERALD -> MapColor.EMERALD_GREEN;
            case DIAMOND -> MapColor.DIAMOND_BLUE;
            case NETHERITE -> MapColor.PURPLE;
            case MASTERBALL -> MapColor.MAGENTA;
        };
        // extremely high blast resistance and no pistons: it is a very expensive investment
        return AbstractBlock.Settings.create().mapColor(color).strength(3.0f, 1200f)
                .sounds(t.ordinal() >= 4 ? BlockSoundGroup.AMETHYST_BLOCK : BlockSoundGroup.METAL)
                .nonOpaque().pistonBehavior(PistonBehavior.BLOCK).luminance(s -> t.light);
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

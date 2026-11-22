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
 * Registro de las estatuas de lujo (v0.9). Llamar a {@link #register()} desde TcgShop.onInitialize (antes de que se
 * construya la pestaña creativa o justo después de TcgBlocks.init: los registros estáticos se hacen al cargar la clase).
 */
public final class LuxuryBlocks {
    /** Todas las estatuas (ShopLife puede contarlas como decoración: {@code state.isIn(LuxuryBlocks.STATUES_TAG)}). */
    public static final TagKey<Block> STATUES_TAG = TagKey.of(RegistryKeys.BLOCK, Identifier.of(TcgShop.MOD_ID, "luxury_statues"));
    public static final TagKey<Item> STATUE_ITEMS_TAG = TagKey.of(RegistryKeys.ITEM, Identifier.of(TcgShop.MOD_ID, "luxury_statues"));

    public static final Map<StatueTier, StatueBlock> BLOCKS = new EnumMap<>(StatueTier.class);
    public static final Map<StatueTier, StatueItem> ITEMS = new EnumMap<>(StatueTier.class);
    private static final List<Item> ITEM_LIST = new ArrayList<>();

    public static final StatuePartBlock STATUE_PART;
    /** 0.9.5: Llave de la Mansión MasterBall (Exclusivos, la pieza más cara). */
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
        ITEM_LIST.add(MANSION_KEY); // pestaña creativa, detrás de las estatuas
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
        // resistencia a explosiones altísima y sin pistones: es una inversión carísima
        return AbstractBlock.Settings.create().mapColor(color).strength(3.0f, 1200f)
                .sounds(t.ordinal() >= 4 ? BlockSoundGroup.AMETHYST_BLOCK : BlockSoundGroup.METAL)
                .nonOpaque().pistonBehavior(PistonBehavior.BLOCK).luminance(s -> t.light);
    }

    private LuxuryBlocks() {}

    public static StatueItem item(StatueTier t) {
        return ITEMS.get(t);
    }

    /** Objetos en orden de nivel (para la pestaña creativa). */
    public static List<Item> items() {
        return Collections.unmodifiableList(ITEM_LIST);
    }

    /**
     * Fuerza la carga de la clase (registros), lee config/tcgshop-luxury.json y engancha la protección de zonas de las
     * piezas y la sincronización de la info de venta con los clientes.
     */
    public static void register() {
        LuxuryConfig.load();
        StatueProtection.register();
        LuxuryShop.registerSync();
        dev.tcgshop.luxury.house.MansionService.register();
    }
}

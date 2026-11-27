package dev.tcgshop.shop;

import dev.tcgshop.TcgShop;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * 1.0 beta 2: premium RGB displays. They are ordinary shop displays ({@link ShopDisplayBlock}: same inventory, buying,
 * stocking, slot hit-testing and selftest) with animated LED lighting drawn by client.render.RgbLights, and they cost
 * clearly more than the classic ones (25,000 to 500,000; overridable in config/tcgshop-economy.json "itemPrices").
 * Sold by the Wholesaler and in the phone store (ShopServer.wholesaleGoods), listed in the creative tab.
 * Models, textures, loot tables and LED layouts: tools/gen_rgb_displays.py (assets/tcgshop/rgb/*.json).
 */
public final class RgbDisplays {
    /** One RGB display: registry id, shape, default price, default LED animation and block light. */
    public record Def(String id, DisplayKind kind, long price, int mode, int light, SoundType sound, boolean noCollision) {}

    public static final List<Def> DEFS = List.of(
            new Def("rgb_card_frame", DisplayKind.FRAME, 25_000L, RgbLighting.CHASE, 6, SoundType.METAL, true),
            new Def("rgb_card_ledge", DisplayKind.LEDGE, 30_000L, RgbLighting.WAVE, 6, SoundType.METAL, false),
            new Def("rgb_counter", DisplayKind.COUNTER, 35_000L, RgbLighting.CHASE, 7, SoundType.METAL, false),
            new Def("rgb_card_stand", DisplayKind.CARD_STAND, 40_000L, RgbLighting.CHASE, 7, SoundType.METAL, false),
            new Def("rgb_turntable", DisplayKind.TURNTABLE, 45_000L, RgbLighting.CHASE, 7, SoundType.METAL, false),
            new Def("rgb_wall_trio", DisplayKind.WALL_TRIO, 60_000L, RgbLighting.CHASE, 7, SoundType.METAL, false),
            new Def("rgb_glass_cube", DisplayKind.GLASS_CUBE, 65_000L, RgbLighting.WAVE, 8, SoundType.GLASS, false),
            new Def("rgb_pack_shelf", DisplayKind.PACK_SHELF, 70_000L, RgbLighting.CHASE, 8, SoundType.METAL, false),
            new Def("rgb_display_case", DisplayKind.DISPLAY_CASE, 80_000L, RgbLighting.WAVE, 8, SoundType.GLASS, false),
            new Def("rgb_showcase", DisplayKind.SHOWCASE, 85_000L, RgbLighting.BREATHE, 10, SoundType.GLASS, false),
            new Def("rgb_box_shelf", DisplayKind.BOX_SHELF, 90_000L, RgbLighting.WAVE, 8, SoundType.METAL, false),
            new Def("rgb_gondola", DisplayKind.GONDOLA, 95_000L, RgbLighting.CHASE, 8, SoundType.METAL, false),
            new Def("rgb_slab_tower", DisplayKind.SLAB_TOWER, 110_000L, RgbLighting.CHASE, 9, SoundType.METAL, false),
            new Def("rgb_box_stand", DisplayKind.BOX_STAND, 120_000L, RgbLighting.WAVE, 9, SoundType.METAL, false),
            new Def("rgb_card_wall", DisplayKind.CARD_WALL, 140_000L, RgbLighting.CHASE, 9, SoundType.METAL, false),
            new Def("rgb_multi_shelf", DisplayKind.MULTI_SHELF, 160_000L, RgbLighting.CHASE, 9, SoundType.METAL, false),
            new Def("rgb_neon_poster", DisplayKind.BIG_FRAME, 190_000L, RgbLighting.CHASE, 10, SoundType.METAL, false),
            new Def("rgb_grand_wall", DisplayKind.GRAND_WALL, 260_000L, RgbLighting.CHASE, 11, SoundType.METAL, false),
            new Def("rgb_pedestal", DisplayKind.PEDESTAL, 300_000L, RgbLighting.BREATHE, 11, SoundType.STONE, false),
            new Def("rgb_prism_pedestal", DisplayKind.PEDESTAL, 500_000L, RgbLighting.CHASE, 13, SoundType.AMETHYST, false));

    private static final List<ShopDisplayBlock> BLOCK_LIST = new ArrayList<>();
    private static final List<Item> ITEM_LIST = new ArrayList<>();
    private static final Map<Item, Long> PRICE_MAP = new LinkedHashMap<>();
    /** Registered blocks / items in {@link #DEFS} order, and their default prices (ShopServer.init copies them). */
    public static final List<ShopDisplayBlock> BLOCKS = Collections.unmodifiableList(BLOCK_LIST);
    public static final List<Item> ITEMS = Collections.unmodifiableList(ITEM_LIST);
    public static final Map<Item, Long> PRICES = Collections.unmodifiableMap(PRICE_MAP);

    static {
        // registered while TcgBlocks builds the display block entity type (TcgBlocks.DISPLAY_BE -> with(...)):
        // nothing here may read TcgBlocks fields
        for (Def d : DEFS) {
            int light = d.light();
            BlockBehaviour.Properties s = BlockBehaviour.Properties.of().strength(1.5f, 6f).sound(d.sound()).noOcclusion()
                    .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK).lightLevel(st -> light);
            if (d.noCollision()) s = s.noCollission();
            ShopDisplayBlock b = Registry.register(BuiltInRegistries.BLOCK, ResourceLocation.fromNamespaceAndPath(TcgShop.MOD_ID, d.id()), new ShopDisplayBlock(d.kind(), false, d.mode(), s));
            Rarity r = d.price() >= 150_000 ? Rarity.EPIC : d.price() >= 60_000 ? Rarity.RARE : Rarity.UNCOMMON;
            Item it = Registry.register(BuiltInRegistries.ITEM, ResourceLocation.fromNamespaceAndPath(TcgShop.MOD_ID, d.id()), new RgbDisplayItem(b, new Item.Properties().rarity(r)));
            BLOCK_LIST.add(b);
            ITEM_LIST.add(it);
            PRICE_MAP.put(it, d.price());
        }
    }

    private RgbDisplays() {}

    /** `base` followed by every RGB display block (for the shared display block entity type). */
    public static Block[] with(Block... base) {
        Block[] out = new Block[base.length + BLOCK_LIST.size()];
        System.arraycopy(base, 0, out, 0, base.length);
        for (int i = 0; i < BLOCK_LIST.size(); i++) out[base.length + i] = BLOCK_LIST.get(i);
        return out;
    }

    public static void init() {}

    /** Item of an RGB display: tooltip with the slot count and the LED controls. */
    public static class RgbDisplayItem extends BlockItem {
        public RgbDisplayItem(Block block, Properties settings) {
            super(block, settings);
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag type) {
            super.appendHoverText(stack, context, tooltip, type);
            if (getBlock() instanceof ShopDisplayBlock b) {
                tooltip.add(Component.translatable("tcgshop.rgb.tip.slots", b.slots(), b.kind.w, b.kind.h).withStyle(ChatFormatting.GRAY));
                tooltip.add(Component.translatable("tcgshop.rgb.tip.lights").withStyle(ChatFormatting.LIGHT_PURPLE));
                tooltip.add(Component.translatable("tcgshop.rgb.tip.controls").withStyle(ChatFormatting.DARK_AQUA));
            }
        }
    }
}

package dev.tcgshop.casino;

import dev.tcgshop.TcgShop;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 1.0 beta 3 casino: the machines (one block each, placeable anywhere, only their owner can break them), the
 * scratch tickets and the shared block entity. Default prices go into ShopServer's fixed prices (overridable in
 * config/tcgshop-economy.json "itemPrices"); they are sold by the Wholesaler and in the phone Market.
 * Models and textures: tools/gen_casino_assets.py.
 */
public final class CasinoBlocks {
    private static final List<Item> ITEM_LIST = new ArrayList<>();
    private static final Map<Item, Long> PRICE_MAP = new LinkedHashMap<>();
    public static final List<Item> ITEMS = Collections.unmodifiableList(ITEM_LIST);
    /** Default prices (ShopServer.init copies them into the fixed prices). */
    public static final Map<Item, Long> PRICES = Collections.unmodifiableMap(PRICE_MAP);

    private static AbstractBlock.Settings machine(BlockSoundGroup sound, int light) {
        return AbstractBlock.Settings.create().mapColor(MapColor.RED).strength(3.0f, 1200f).sounds(sound).nonOpaque()
                .luminance(s -> light).pistonBehavior(net.minecraft.block.piston.PistonBehavior.BLOCK);
    }

    private static VoxelShape box(double x0, double y0, double z0, double x1, double y1, double z1) {
        return Block.createCuboidShape(x0, y0, z0, x1, y1, z1);
    }

    // --- machines (north-facing shapes; the block rotates them)
    public static final CasinoMachineBlock SLOT_MACHINE = block("slot_machine", new CasinoMachineBlock(CasinoGame.SLOTS,
            VoxelShapes.union(box(2, 0, 3, 14, 16, 14), box(1.5, 6, 1, 14.5, 8, 3)), machine(BlockSoundGroup.METAL, 7)));
    public static final CasinoMachineBlock VIP_SLOT_MACHINE = block("vip_slot_machine", new CasinoMachineBlock(CasinoGame.VIP_SLOTS,
            VoxelShapes.union(box(2, 0, 3, 14, 16, 14), box(1.5, 6, 1, 14.5, 8, 3)), machine(BlockSoundGroup.METAL, 10)));
    public static final CasinoMachineBlock ROULETTE_TABLE = block("roulette_table", new CasinoMachineBlock(CasinoGame.ROULETTE,
            box(0, 0, 0, 16, 14, 16), machine(BlockSoundGroup.WOOD, 4)));
    public static final CasinoMachineBlock BLACKJACK_TABLE = block("blackjack_table", new CasinoMachineBlock(CasinoGame.BLACKJACK,
            box(0, 0, 0, 16, 13, 16), machine(BlockSoundGroup.WOOD, 4)));
    public static final CasinoMachineBlock FORTUNE_WHEEL = block("fortune_wheel", new CasinoMachineBlock(CasinoGame.WHEEL,
            box(2, 0, 5, 14, 16, 11), machine(BlockSoundGroup.WOOD, 6)));

    public static final Item SLOT_MACHINE_ITEM = machineItem("slot_machine", SLOT_MACHINE, Rarity.RARE, 120_000L);
    public static final Item VIP_SLOT_MACHINE_ITEM = machineItem("vip_slot_machine", VIP_SLOT_MACHINE, Rarity.EPIC, 500_000L);
    public static final Item ROULETTE_TABLE_ITEM = machineItem("roulette_table", ROULETTE_TABLE, Rarity.EPIC, 250_000L);
    public static final Item BLACKJACK_TABLE_ITEM = machineItem("blackjack_table", BLACKJACK_TABLE, Rarity.EPIC, 200_000L);
    public static final Item FORTUNE_WHEEL_ITEM = machineItem("fortune_wheel", FORTUNE_WHEEL, Rarity.RARE, 60_000L);

    // --- scratch tickets (price = face value by default)
    public static final Item SCRATCH_BRONZE = ticket("scratch_card_bronze", ScratchMath.Tier.BRONZE, Rarity.COMMON, 1_000L);
    public static final Item SCRATCH_SILVER = ticket("scratch_card_silver", ScratchMath.Tier.SILVER, Rarity.UNCOMMON, 5_000L);
    public static final Item SCRATCH_GOLD = ticket("scratch_card_gold", ScratchMath.Tier.GOLD, Rarity.RARE, 25_000L);

    public static final BlockEntityType<CasinoBlockEntity> MACHINE_BE = Registry.register(Registries.BLOCK_ENTITY_TYPE,
            Identifier.of(TcgShop.MOD_ID, "casino_machine"),
            BlockEntityType.Builder.create(CasinoBlockEntity::new, SLOT_MACHINE, VIP_SLOT_MACHINE, ROULETTE_TABLE, BLACKJACK_TABLE, FORTUNE_WHEEL).build(null));

    public static final Block[] MACHINES = {SLOT_MACHINE, VIP_SLOT_MACHINE, ROULETTE_TABLE, BLACKJACK_TABLE, FORTUNE_WHEEL};

    private CasinoBlocks() {}

    private static <T extends Block> T block(String name, T block) {
        return Registry.register(Registries.BLOCK, Identifier.of(TcgShop.MOD_ID, name), block);
    }

    private static Item machineItem(String name, CasinoMachineBlock b, Rarity r, long price) {
        Item it = Registry.register(Registries.ITEM, Identifier.of(TcgShop.MOD_ID, name), new MachineItem(b, new Item.Settings().maxCount(1).rarity(r)));
        ITEM_LIST.add(it);
        PRICE_MAP.put(it, price);
        return it;
    }

    private static Item ticket(String name, ScratchMath.Tier tier, Rarity r, long price) {
        Item it = Registry.register(Registries.ITEM, Identifier.of(TcgShop.MOD_ID, name), new ScratchCardItem(tier, new Item.Settings().maxCount(64).rarity(r)));
        ITEM_LIST.add(it);
        PRICE_MAP.put(it, price);
        return it;
    }

    public static void init() {}

    /** Machine item: what the game is, its odds and the owner perks. */
    public static class MachineItem extends BlockItem {
        public MachineItem(Block block, Settings settings) {
            super(block, settings);
        }

        @Override
        public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
            super.appendTooltip(stack, context, tooltip, type);
            if (getBlock() instanceof CasinoMachineBlock b) {
                tooltip.add(Text.translatable("tcgshop.casino.tip." + b.game.key).formatted(Formatting.GRAY));
                tooltip.add(Text.translatable("tcgshop.casino.tip.max_bet", dev.tcgshop.pricing.PriceService.format(b.game.maxBet())).formatted(Formatting.DARK_GRAY));
                tooltip.add(Text.translatable("tcgshop.casino.tip.owner").formatted(Formatting.DARK_AQUA));
            }
        }
    }
}

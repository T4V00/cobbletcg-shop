package dev.tcgshop.casino;

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
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

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

    private static BlockBehaviour.Properties machine(SoundType sound, int light) {
        return BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(3.0f, 1200f).sound(sound).noOcclusion()
                .lightLevel(s -> light).pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK);
    }

    private static VoxelShape box(double x0, double y0, double z0, double x1, double y1, double z1) {
        return Block.box(x0, y0, z0, x1, y1, z1);
    }

    // --- machines (north-facing shapes; the block rotates them)
    public static final CasinoMachineBlock SLOT_MACHINE = block("slot_machine", new CasinoMachineBlock(CasinoGame.SLOTS,
            Shapes.or(box(2, 0, 3, 14, 16, 14), box(1.5, 6, 1, 14.5, 8, 3)), machine(SoundType.METAL, 7)));
    public static final CasinoMachineBlock VIP_SLOT_MACHINE = block("vip_slot_machine", new CasinoMachineBlock(CasinoGame.VIP_SLOTS,
            Shapes.or(box(2, 0, 3, 14, 16, 14), box(1.5, 6, 1, 14.5, 8, 3)), machine(SoundType.METAL, 10)));
    public static final CasinoMachineBlock ROULETTE_TABLE = block("roulette_table", new CasinoMachineBlock(CasinoGame.ROULETTE,
            box(0, 0, 0, 16, 14, 16), machine(SoundType.WOOD, 4)));
    public static final CasinoMachineBlock BLACKJACK_TABLE = block("blackjack_table", new CasinoMachineBlock(CasinoGame.BLACKJACK,
            box(0, 0, 0, 16, 13, 16), machine(SoundType.WOOD, 4)));
    public static final CasinoMachineBlock FORTUNE_WHEEL = block("fortune_wheel", new CasinoMachineBlock(CasinoGame.WHEEL,
            box(2, 0, 5, 14, 16, 11), machine(SoundType.WOOD, 6)));

    /** 1.1: dice table (Lucky Seven) and coin toss stand (Double or Nothing). Models: tools/gen_casino_tables_11.py. */
    public static final CasinoMachineBlock DICE_TABLE = block("dice_table", new CasinoMachineBlock(CasinoGame.DICE,
            box(0, 0, 0, 16, 14, 16), machine(SoundType.WOOD, 4)));
    public static final CasinoMachineBlock COIN_TOSS = block("coin_toss", new CasinoMachineBlock(CasinoGame.COIN,
            Shapes.or(box(3, 0, 3, 13, 12, 13), box(1.5, 12, 1.5, 14.5, 14, 14.5)), machine(SoundType.METAL, 6)));

    public static final Item SLOT_MACHINE_ITEM = machineItem("slot_machine", SLOT_MACHINE, Rarity.RARE, 120_000L);
    public static final Item VIP_SLOT_MACHINE_ITEM = machineItem("vip_slot_machine", VIP_SLOT_MACHINE, Rarity.EPIC, 500_000L);
    public static final Item ROULETTE_TABLE_ITEM = machineItem("roulette_table", ROULETTE_TABLE, Rarity.EPIC, 250_000L);
    public static final Item BLACKJACK_TABLE_ITEM = machineItem("blackjack_table", BLACKJACK_TABLE, Rarity.EPIC, 200_000L);
    public static final Item FORTUNE_WHEEL_ITEM = machineItem("fortune_wheel", FORTUNE_WHEEL, Rarity.RARE, 60_000L);
    public static final Item DICE_TABLE_ITEM = machineItem("dice_table", DICE_TABLE, Rarity.RARE, 90_000L);
    public static final Item COIN_TOSS_ITEM = machineItem("coin_toss", COIN_TOSS, Rarity.RARE, 70_000L);

    // --- scratch tickets (price = face value by default)
    public static final Item SCRATCH_BRONZE = ticket("scratch_card_bronze", ScratchMath.Tier.BRONZE, Rarity.COMMON, 1_000L);
    public static final Item SCRATCH_SILVER = ticket("scratch_card_silver", ScratchMath.Tier.SILVER, Rarity.UNCOMMON, 5_000L);
    public static final Item SCRATCH_GOLD = ticket("scratch_card_gold", ScratchMath.Tier.GOLD, Rarity.RARE, 25_000L);

    public static final BlockEntityType<CasinoBlockEntity> MACHINE_BE = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(TcgShop.MOD_ID, "casino_machine"),
            BlockEntityType.Builder.of(CasinoBlockEntity::new, SLOT_MACHINE, VIP_SLOT_MACHINE, ROULETTE_TABLE, BLACKJACK_TABLE, FORTUNE_WHEEL,
                    DICE_TABLE, COIN_TOSS).build(null));

    public static final Block[] MACHINES = {SLOT_MACHINE, VIP_SLOT_MACHINE, ROULETTE_TABLE, BLACKJACK_TABLE, FORTUNE_WHEEL, DICE_TABLE, COIN_TOSS};

    private CasinoBlocks() {}

    private static <T extends Block> T block(String name, T block) {
        return Registry.register(BuiltInRegistries.BLOCK, ResourceLocation.fromNamespaceAndPath(TcgShop.MOD_ID, name), block);
    }

    private static Item machineItem(String name, CasinoMachineBlock b, Rarity r, long price) {
        Item it = Registry.register(BuiltInRegistries.ITEM, ResourceLocation.fromNamespaceAndPath(TcgShop.MOD_ID, name), new MachineItem(b, new Item.Properties().stacksTo(1).rarity(r)));
        ITEM_LIST.add(it);
        PRICE_MAP.put(it, price);
        return it;
    }

    private static Item ticket(String name, ScratchMath.Tier tier, Rarity r, long price) {
        Item it = Registry.register(BuiltInRegistries.ITEM, ResourceLocation.fromNamespaceAndPath(TcgShop.MOD_ID, name), new ScratchCardItem(tier, new Item.Properties().stacksTo(64).rarity(r)));
        ITEM_LIST.add(it);
        PRICE_MAP.put(it, price);
        return it;
    }

    public static void init() {}

    /** Machine item: what the game is, its odds and the owner perks. */
    public static class MachineItem extends BlockItem {
        public MachineItem(Block block, Properties settings) {
            super(block, settings);
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag type) {
            super.appendHoverText(stack, context, tooltip, type);
            if (getBlock() instanceof CasinoMachineBlock b) {
                tooltip.add(Component.translatable("tcgshop.casino.tip." + b.game.key).withStyle(ChatFormatting.GRAY));
                tooltip.add(Component.translatable("tcgshop.casino.tip.max_bet", dev.tcgshop.pricing.PriceService.format(b.game.maxBet())).withStyle(ChatFormatting.DARK_GRAY));
                tooltip.add(Component.translatable("tcgshop.casino.tip.owner").withStyle(ChatFormatting.DARK_AQUA));
            }
        }
    }
}

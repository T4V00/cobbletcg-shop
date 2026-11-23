package dev.tcgshop.luxury.house;

import dev.tcgshop.luxury.LuxuryConfig;
import dev.tcgshop.pricing.PriceService;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.List;

/**
 * MasterBall Mansion key (0.9.5): right click = preview of the 100x100 estate in front of the player;
 * sneak + right click within the next 10 s = it gets built (see {@link MansionService}). The key is only consumed
 * when the build actually starts.
 */
public class MansionKeyItem extends Item {
    /** Name color (gold). */
    public static final int COLOR = 0xFFC640;
    /** Price sent by the server (client tooltip); -1 = use the local config. */
    public static long clientPrice = -1;

    public MansionKeyItem(Settings settings) {
        super(settings);
    }

    @Override
    public Text getName(ItemStack stack) {
        return Text.translatable(getTranslationKey()).withColor(COLOR).formatted(Formatting.BOLD);
    }

    @Override
    public boolean hasGlint(ItemStack stack) {
        return true;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack st = user.getStackInHand(hand);
        if (world.isClient) return TypedActionResult.success(st);
        if (user instanceof ServerPlayerEntity sp) MansionService.onKeyUse(sp, hand);
        return TypedActionResult.consume(st);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("tcgshop.mansion.tip_size").withColor(COLOR));
        tooltip.add(Text.translatable("tcgshop.mansion.flavor").formatted(Formatting.DARK_GRAY, Formatting.ITALIC));
        tooltip.add(Text.translatable("tcgshop.mansion.tip_use1").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("tcgshop.mansion.tip_use2").formatted(Formatting.GRAY));
        long price = clientPrice > 0 ? clientPrice : LuxuryConfig.INSTANCE.mansionPrice;
        tooltip.add(Text.translatable("tcgshop.lux.tip_price", PriceService.format(price)).formatted(Formatting.GOLD));
        tooltip.add(Text.translatable("tcgshop.lux.tip_no_resale").formatted(Formatting.RED));
    }
}

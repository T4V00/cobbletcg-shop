package dev.tcgshop.casino;

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
 * Scratch ticket. Right click: the server rolls the prize, pays it at once and opens the scratching screen
 * (purely visual: closing it early loses nothing). Sneak + right click: scratch it instantly.
 */
public class ScratchCardItem extends Item {
    public final ScratchMath.Tier tier;

    public ScratchCardItem(ScratchMath.Tier tier, Settings settings) {
        super(settings);
        this.tier = tier;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (!world.isClient && user instanceof ServerPlayerEntity sp) Casino.scratch(sp, stack, tier, sp.isSneaking());
        return TypedActionResult.success(stack, world.isClient());
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        long face = Casino.faceValue(tier, stack);
        double win = 0;
        for (int n : tier.oneIn) win += 1.0 / n;
        tooltip.add(Text.translatable("tcgshop.casino.scratch.tip.rule").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("tcgshop.casino.scratch.tip.top", PriceService.format(face * tier.top())).formatted(Formatting.GOLD));
        tooltip.add(Text.translatable("tcgshop.casino.scratch.tip.odds", String.format(java.util.Locale.ROOT, "%.2f", 1 / win),
                Math.round(ScratchMath.rtp(tier) * 100)).formatted(Formatting.DARK_GRAY));
        tooltip.add(Text.translatable("tcgshop.casino.scratch.tip.use").formatted(Formatting.DARK_AQUA));
    }
}

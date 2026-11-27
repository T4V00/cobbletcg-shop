package dev.tcgshop.casino;

import dev.tcgshop.pricing.PriceService;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * Scratch ticket. Right click: the server rolls the prize, pays it at once and opens the scratching screen
 * (purely visual: closing it early loses nothing). Sneak + right click: scratch it instantly.
 */
public class ScratchCardItem extends Item {
    public final ScratchMath.Tier tier;

    public ScratchCardItem(ScratchMath.Tier tier, Properties settings) {
        super(settings);
        this.tier = tier;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);
        if (!world.isClientSide && user instanceof ServerPlayer sp) Casino.scratch(sp, stack, tier, sp.isShiftKeyDown());
        return InteractionResultHolder.sidedSuccess(stack, world.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag type) {
        long face = Casino.faceValue(tier, stack);
        double win = 0;
        for (int n : tier.oneIn) win += 1.0 / n;
        tooltip.add(Component.translatable("tcgshop.casino.scratch.tip.rule").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tcgshop.casino.scratch.tip.top", PriceService.format(face * tier.top())).withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tcgshop.casino.scratch.tip.odds", String.format(java.util.Locale.ROOT, "%.2f", 1 / win),
                Math.round(ScratchMath.rtp(tier) * 100)).withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tcgshop.casino.scratch.tip.use").withStyle(ChatFormatting.DARK_AQUA));
    }
}

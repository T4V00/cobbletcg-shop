package dev.tcgshop.luxury.house;

import dev.tcgshop.luxury.LuxuryConfig;
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
 * MasterBall Mansion key (0.9.5): right click = preview of the 100x100 estate in front of the player;
 * sneak + right click within the next 10 s = it gets built (see {@link MansionService}). The key is only consumed
 * when the build actually starts.
 */
public class MansionKeyItem extends Item {
    /** Name color (gold). */
    public static final int COLOR = 0xFFC640;
    /** Price sent by the server (client tooltip); -1 = use the local config. */
    public static long clientPrice = -1;

    public MansionKeyItem(Properties settings) {
        super(settings);
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable(getDescriptionId()).withColor(COLOR).withStyle(ChatFormatting.BOLD);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
        ItemStack st = user.getItemInHand(hand);
        if (world.isClientSide) return InteractionResultHolder.success(st);
        if (user instanceof ServerPlayer sp) MansionService.onKeyUse(sp, hand);
        return InteractionResultHolder.consume(st);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag type) {
        tooltip.add(Component.translatable("tcgshop.mansion.tip_size").withColor(COLOR));
        tooltip.add(Component.translatable("tcgshop.mansion.flavor").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("tcgshop.mansion.tip_use1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tcgshop.mansion.tip_use2").withStyle(ChatFormatting.GRAY));
        long price = clientPrice > 0 ? clientPrice : LuxuryConfig.INSTANCE.mansionPrice;
        tooltip.add(Component.translatable("tcgshop.lux.tip_price", PriceService.format(price)).withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tcgshop.lux.tip_no_resale").withStyle(ChatFormatting.RED));
    }
}

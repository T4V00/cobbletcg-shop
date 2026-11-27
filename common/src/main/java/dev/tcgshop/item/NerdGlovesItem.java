package dev.tcgshop.item;

import dev.tcgshop.compat.AccessoriesCompat;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * Nerd Gloves: while wearing them (Accessories "hand" slot or in the off hand) the space bar
 * opens packs in one go with a perfect cut. Without gloves, Space opens nothing.
 */
public class NerdGlovesItem extends Item {
    public NerdGlovesItem(Properties settings) {
        super(settings);
    }

    public static boolean isWearing(LivingEntity entity) {
        return entity.getOffhandItem().is(TcgItems.NERD_GLOVES) || entity.getMainHandItem().is(TcgItems.NERD_GLOVES)
                || AccessoriesCompat.isEquipped(entity, TcgItems.NERD_GLOVES)
                || BoxGlovesItem.isWearing(entity); // 1.1: the Box-Opening Gloves do everything these do
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag type) {
        tooltip.add(Component.translatable("item.tcgshop.nerd_gloves.tip1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.tcgshop.nerd_gloves.tip2").withStyle(ChatFormatting.DARK_GRAY));
    }
}

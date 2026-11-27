package dev.tcgshop.item;

import dev.tcgshop.compat.AccessoriesCompat;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * 1.1 Box-Opening Gloves: everything the Nerd Gloves do (Space opens packs with a perfect cut) and, while worn (Accessories
 * hand slot, either hand), right-clicking a booster box asks "Open the whole box?": yes opens all its packs at once
 * straight into your binder (see dev.tcgshop.pack.BoxGloves). Very expensive, sold only (no recipe).
 */
public class BoxGlovesItem extends NerdGlovesItem {
    public BoxGlovesItem(Properties settings) {
        super(settings);
    }

    public static boolean isWearing(LivingEntity entity) {
        return entity.getOffhandItem().is(TcgItems.BOX_GLOVES) || entity.getMainHandItem().is(TcgItems.BOX_GLOVES)
                || AccessoriesCompat.isEquipped(entity, TcgItems.BOX_GLOVES);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag type) {
        tooltip.add(Component.translatable("item.tcgshop.box_gloves.tip1").withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("item.tcgshop.nerd_gloves.tip1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.tcgshop.nerd_gloves.tip2").withStyle(ChatFormatting.DARK_GRAY));
    }
}

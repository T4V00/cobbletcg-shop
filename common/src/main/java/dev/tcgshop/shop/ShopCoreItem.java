package dev.tcgshop.shop;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

/** 1.1: shop core item. A core dropped by a dormant shop (TcgBlocks.SHOP_LINK) shows which shop it restores. */
public class ShopCoreItem extends BlockItem {
    public ShopCoreItem(Block block, Properties settings) {
        super(block, settings);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, lines, flag);
        CompoundTag l = stack.get(TcgBlocks.SHOP_LINK);
        if (l == null || !l.hasUUID("zone")) return;
        lines.add(Component.translatable("tcgshop.core_item.linked", l.getString("owner")).withStyle(ChatFormatting.GOLD));
        int size = l.getInt("size");
        lines.add(Component.translatable("tcgshop.core_item.linked_tier", Component.translatable("tcgshop.shop.tier." + l.getString("tier")), size, size)
                .withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("tcgshop.core_item.linked_hint").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}

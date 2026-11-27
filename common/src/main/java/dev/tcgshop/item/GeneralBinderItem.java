package dev.tcgshop.item;

import dev.tcgshop.binder.BinderServer;
import dev.tcgshop.collection.BinderTag;
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
 * General Binder: a single inventory with all your cards. Right click or key (G).
 * The first time you open it, it becomes bound to your name: it is unique and nobody else can open it.
 */
public class GeneralBinderItem extends Item {
    public GeneralBinderItem(Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
        if (user instanceof ServerPlayer sp) BinderServer.openGeneral(sp, user.getItemInHand(hand));
        return InteractionResultHolder.sidedSuccess(user.getItemInHand(hand), world.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag type) {
        BinderTag tag = stack.get(TcgItems.BINDER_TAG);
        if (tag != null) tooltip.add(Component.translatable("tcgshop.binder.owner", tag.ownerName()).withStyle(ChatFormatting.GOLD));
        else tooltip.add(Component.translatable("tcgshop.binder.unbound").withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.translatable("item.tcgshop.general_binder.tip1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.tcgshop.general_binder.tip2").withStyle(ChatFormatting.DARK_GRAY));
    }
}

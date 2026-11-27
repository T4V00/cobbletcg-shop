package dev.tcgshop.item;

import dev.tcgshop.compat.AccessoriesCompat;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * Manager Goggles: worn on the head (or in the Accessories "face" slot) they show shop borders,
 * who owns them, and the profit or loss of your own.
 */
public class ManagerGogglesItem extends Item implements Equipable {
    public ManagerGogglesItem(Properties settings) {
        super(settings);
    }

    public static boolean isWearing(LivingEntity e) {
        return e.getItemBySlot(EquipmentSlot.HEAD).is(TcgItems.MANAGER_GOGGLES) || AccessoriesCompat.isEquipped(e, TcgItems.MANAGER_GOGGLES);
    }

    @Override
    public EquipmentSlot getEquipmentSlot() {
        return EquipmentSlot.HEAD;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
        return swapWithEquipmentSlot(this, world, user, hand);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag type) {
        tooltip.add(Component.translatable("item.tcgshop.manager_goggles.tip1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.tcgshop.manager_goggles.tip2").withStyle(ChatFormatting.DARK_GRAY));
    }
}

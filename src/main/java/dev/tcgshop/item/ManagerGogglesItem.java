package dev.tcgshop.item;

import dev.tcgshop.compat.AccessoriesCompat;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Equipment;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.List;

/**
 * Gafas de Gerente: puestas en la cabeza (o en el slot "face" de Accessories) muestran el borde de las tiendas,
 * de quién son, y el beneficio o la pérdida de las tuyas.
 */
public class ManagerGogglesItem extends Item implements Equipment {
    public ManagerGogglesItem(Settings settings) {
        super(settings);
    }

    public static boolean isWearing(LivingEntity e) {
        return e.getEquippedStack(EquipmentSlot.HEAD).isOf(TcgItems.MANAGER_GOGGLES) || AccessoriesCompat.isEquipped(e, TcgItems.MANAGER_GOGGLES);
    }

    @Override
    public EquipmentSlot getSlotType() {
        return EquipmentSlot.HEAD;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        return equipAndSwap(this, world, user, hand);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("item.tcgshop.manager_goggles.tip1").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("item.tcgshop.manager_goggles.tip2").formatted(Formatting.DARK_GRAY));
    }
}

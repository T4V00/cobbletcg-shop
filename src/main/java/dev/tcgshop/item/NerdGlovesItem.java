package dev.tcgshop.item;

import dev.tcgshop.compat.AccessoriesCompat;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * Guantes de friki: con ellos puestos (slot "hand" de Accessories o en la mano secundaria) la barra espaciadora
 * abre los sobres de un tirón con un corte perfecto. Sin guantes, Espacio no abre nada.
 */
public class NerdGlovesItem extends Item {
    public NerdGlovesItem(Settings settings) {
        super(settings);
    }

    public static boolean isWearing(LivingEntity entity) {
        return entity.getOffHandStack().isOf(TcgItems.NERD_GLOVES) || entity.getMainHandStack().isOf(TcgItems.NERD_GLOVES)
                || AccessoriesCompat.isEquipped(entity, TcgItems.NERD_GLOVES);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("item.tcgshop.nerd_gloves.tip1").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("item.tcgshop.nerd_gloves.tip2").formatted(Formatting.DARK_GRAY));
    }
}

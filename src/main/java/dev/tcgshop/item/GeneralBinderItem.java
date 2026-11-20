package dev.tcgshop.item;

import dev.tcgshop.binder.BinderServer;
import dev.tcgshop.collection.BinderTag;
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
 * Binder General: un único inventario con todas tus cartas. Clic derecho o tecla (G).
 * Al abrirlo por primera vez queda vinculado a tu nombre: es único y nadie más puede abrirlo.
 */
public class GeneralBinderItem extends Item {
    public GeneralBinderItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        if (user instanceof ServerPlayerEntity sp) BinderServer.openGeneral(sp, user.getStackInHand(hand));
        return TypedActionResult.success(user.getStackInHand(hand), world.isClient);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        BinderTag tag = stack.get(TcgItems.BINDER_TAG);
        if (tag != null) tooltip.add(Text.translatable("tcgshop.binder.owner", tag.ownerName()).formatted(Formatting.GOLD));
        else tooltip.add(Text.translatable("tcgshop.binder.unbound").formatted(Formatting.GREEN));
        tooltip.add(Text.translatable("item.tcgshop.general_binder.tip1").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("item.tcgshop.general_binder.tip2").formatted(Formatting.DARK_GRAY));
    }
}

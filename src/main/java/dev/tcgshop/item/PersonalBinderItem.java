package dev.tcgshop.item;

import dev.tcgshop.binder.BinderServer;
import dev.tcgshop.binder.PersonalBinderData;
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

/** Personal Binder: organize your cards and customize it. Opening it binds it to your name. */
public class PersonalBinderItem extends Item {
    public PersonalBinderItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        if (user instanceof ServerPlayerEntity sp) BinderServer.openPersonal(sp, hand);
        return TypedActionResult.success(user.getStackInHand(hand), world.isClient);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        BinderTag tag = stack.get(TcgItems.BINDER_TAG);
        if (tag != null) tooltip.add(Text.translatable("tcgshop.binder.owner", tag.ownerName()).formatted(Formatting.GOLD));
        else tooltip.add(Text.translatable("tcgshop.binder.unbound").formatted(Formatting.GREEN));
        PersonalBinderData d = stack.getOrDefault(TcgItems.PERSONAL_BINDER_DATA, PersonalBinderData.DEFAULT);
        tooltip.add(Text.translatable("item.tcgshop.personal_binder.tip", d.filled(), d.pages()).formatted(Formatting.GRAY));
    }
}

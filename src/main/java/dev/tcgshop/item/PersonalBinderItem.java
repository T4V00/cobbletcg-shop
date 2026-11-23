package dev.tcgshop.item;

import dev.tcgshop.binder.BinderServer;
import dev.tcgshop.binder.PersonalBinderData;
import dev.tcgshop.collection.BinderTag;
import net.minecraft.component.DataComponentTypes;
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
import java.util.UUID;

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
        if (d.fixedSize()) tooltip.add(Text.translatable("item.tcgshop.personal_binder.tip_capacity", d.cards(), d.limit()).formatted(Formatting.GRAY));
        else tooltip.add(Text.translatable("item.tcgshop.personal_binder.tip", d.filled(), d.pages()).formatted(Formatting.GRAY));
    }

    /**
     * 1.0 starter binder: a Personal Binder already bound to the player, with their name on it and room for
     * {@link PersonalBinderData#STARTER_CAPACITY} cards (5 starter packs x 5 cards). The onboarding hands it out.
     */
    public static ItemStack createStarter(PlayerEntity player) {
        ItemStack st = new ItemStack(TcgItems.PERSONAL_BINDER);
        String name = player.getName().getString();
        st.set(TcgItems.BINDER_TAG, new BinderTag(UUID.randomUUID(), player.getUuid(), name));
        st.set(TcgItems.PERSONAL_BINDER_DATA, PersonalBinderData.starter());
        st.set(DataComponentTypes.ITEM_NAME, Text.translatable("item.tcgshop.personal_binder.starter_name", name));
        return st;
    }
}

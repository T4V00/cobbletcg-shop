package dev.tcgshop.item;

import dev.tcgshop.binder.BinderServer;
import dev.tcgshop.binder.PersonalBinderData;
import dev.tcgshop.collection.BinderTag;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** Personal Binder: organize your cards and customize it. Opening it binds it to your name. */
public class PersonalBinderItem extends Item {
    public PersonalBinderItem(Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
        if (user instanceof ServerPlayer sp) BinderServer.openPersonal(sp, hand);
        return InteractionResultHolder.sidedSuccess(user.getItemInHand(hand), world.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag type) {
        BinderTag tag = stack.get(TcgItems.BINDER_TAG);
        if (tag != null) tooltip.add(Component.translatable("tcgshop.binder.owner", tag.ownerName()).withStyle(ChatFormatting.GOLD));
        else tooltip.add(Component.translatable("tcgshop.binder.unbound").withStyle(ChatFormatting.GREEN));
        PersonalBinderData d = stack.getOrDefault(TcgItems.PERSONAL_BINDER_DATA, PersonalBinderData.DEFAULT);
        if (d.fixedSize()) tooltip.add(Component.translatable("item.tcgshop.personal_binder.tip_capacity", d.cards(), d.limit()).withStyle(ChatFormatting.GRAY));
        else tooltip.add(Component.translatable("item.tcgshop.personal_binder.tip", d.filled(), d.pages()).withStyle(ChatFormatting.GRAY));
    }

    /**
     * 1.0 starter binder: a Personal Binder already bound to the player, with their name on it and room for
     * {@link PersonalBinderData#STARTER_CAPACITY} cards (5 starter packs x 5 cards). The onboarding hands it out.
     */
    public static ItemStack createStarter(Player player) {
        ItemStack st = new ItemStack(TcgItems.PERSONAL_BINDER);
        String name = player.getName().getString();
        st.set(TcgItems.BINDER_TAG, new BinderTag(UUID.randomUUID(), player.getUUID(), name));
        st.set(TcgItems.PERSONAL_BINDER_DATA, PersonalBinderData.starter());
        st.set(DataComponents.ITEM_NAME, Component.translatable("item.tcgshop.personal_binder.starter_name", name));
        return st;
    }
}

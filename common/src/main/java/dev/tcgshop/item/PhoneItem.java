package dev.tcgshop.item;

import com.mojang.serialization.Codec;
import dev.tcgshop.TcgShop;
import dev.tcgshop.phone.PhoneTier;
import dev.tcgshop.shop.ShopServer;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * TCG Phone: online Market, Prices with charts, your Collection, your Shops and Titles. Opens silently
 * (no chat messages); the apps and catalog depend on the phone's tier (dev.tcgshop.phone.PhoneTier).
 *
 * <p>1.0 beta 3: the tier still belongs to the PLAYER, but the server copies it onto the phone stack they carry
 * ({@link #TIER}, refreshed every second while the phone is in the inventory) so every client can draw the right 3D
 * model (body, frame and app icons of that tier) in anyone's hand, on the ground or in item frames. It is only a
 * visual hint: nothing reads it for gameplay.</p>
 */
public class PhoneItem extends Item {
    /** Visual tier of this phone (0..PhoneTier.MAX_LEVEL); absent on phones that were never in a player's inventory. */
    public static final DataComponentType<Integer> TIER = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
            ResourceLocation.fromNamespaceAndPath(TcgShop.MOD_ID, "phone_tier"),
            DataComponentType.<Integer>builder().persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT).build());

    public PhoneItem(Properties settings) {
        super(settings);
    }

    /** Tier shown by a phone stack (clamped), or -1 when it was never stamped. */
    public static int shownTier(ItemStack stack) {
        Integer t = stack.get(TIER);
        return t == null ? -1 : Math.max(0, Math.min(PhoneTier.MAX_LEVEL, t));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
        if (user instanceof ServerPlayer sp) {
            ShopServer.syncZones(sp);
            dev.tcgshop.collection.CollectionService.sync(sp);
            ShopServer.openStore(sp, -1, ShopServer.phoneCatalog(), ShopServer.PHONE_FACTOR);
            world.playSound(null, user.blockPosition(), SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.PLAYERS, 0.5f, 1.8f);
        }
        return InteractionResultHolder.sidedSuccess(user.getItemInHand(hand), world.isClientSide);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level world, Entity entity, int slot, boolean selected) {
        // server only, once per second per slot: copy the owner's tier onto the stack (changes only when it differs,
        // so the stack is not re-sent every second)
        if (world.isClientSide || !(entity instanceof ServerPlayer sp) || (world.getGameTime() + slot) % 20 != 0) return;
        int tier = dev.tcgshop.phone.PhoneUpgrades.level(sp);
        Integer cur = stack.get(TIER);
        if (cur == null || cur != tier) stack.set(TIER, tier);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag type) {
        tooltip.add(Component.translatable("item.tcgshop.tcg_phone.tip").withStyle(ChatFormatting.GRAY));
        int tier = shownTier(stack);
        if (tier >= 0) { // existing tier names (tcgshop.phoneup.tier.*), in the tier's colour
            PhoneTier t = PhoneTier.of(tier);
            tooltip.add(Component.translatable("tcgshop.phoneup.tier." + t.key).withColor(t.color & 0xFFFFFF));
        }
    }
}

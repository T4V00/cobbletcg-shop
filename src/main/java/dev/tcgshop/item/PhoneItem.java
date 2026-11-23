package dev.tcgshop.item;

import com.mojang.serialization.Codec;
import dev.tcgshop.TcgShop;
import dev.tcgshop.phone.PhoneTier;
import dev.tcgshop.shop.ShopServer;
import net.minecraft.component.ComponentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.List;

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
    public static final ComponentType<Integer> TIER = Registry.register(Registries.DATA_COMPONENT_TYPE,
            Identifier.of(TcgShop.MOD_ID, "phone_tier"),
            ComponentType.<Integer>builder().codec(Codec.INT).packetCodec(PacketCodecs.VAR_INT).build());

    public PhoneItem(Settings settings) {
        super(settings);
    }

    /** Tier shown by a phone stack (clamped), or -1 when it was never stamped. */
    public static int shownTier(ItemStack stack) {
        Integer t = stack.get(TIER);
        return t == null ? -1 : Math.max(0, Math.min(PhoneTier.MAX_LEVEL, t));
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        if (user instanceof ServerPlayerEntity sp) {
            ShopServer.syncZones(sp);
            dev.tcgshop.collection.CollectionService.sync(sp);
            ShopServer.openStore(sp, -1, ShopServer.phoneCatalog(), ShopServer.PHONE_FACTOR);
            world.playSound(null, user.getBlockPos(), SoundEvents.BLOCK_NOTE_BLOCK_BIT.value(), SoundCategory.PLAYERS, 0.5f, 1.8f);
        }
        return TypedActionResult.success(user.getStackInHand(hand), world.isClient);
    }

    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        // server only, once per second per slot: copy the owner's tier onto the stack (changes only when it differs,
        // so the stack is not re-sent every second)
        if (world.isClient || !(entity instanceof ServerPlayerEntity sp) || (world.getTime() + slot) % 20 != 0) return;
        int tier = dev.tcgshop.phone.PhoneUpgrades.level(sp);
        Integer cur = stack.get(TIER);
        if (cur == null || cur != tier) stack.set(TIER, tier);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("item.tcgshop.tcg_phone.tip").formatted(Formatting.GRAY));
        int tier = shownTier(stack);
        if (tier >= 0) { // existing tier names (tcgshop.phoneup.tier.*), in the tier's colour
            PhoneTier t = PhoneTier.of(tier);
            tooltip.add(Text.translatable("tcgshop.phoneup.tier." + t.key).withColor(t.color & 0xFFFFFF));
        }
    }
}

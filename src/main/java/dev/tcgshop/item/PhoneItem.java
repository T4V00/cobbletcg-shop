package dev.tcgshop.item;

import dev.tcgshop.shop.ShopServer;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.List;

/**
 * Teléfono TCG: Mercado online, Precios con gráfica, tu Colección, tus Tiendas y Títulos. Se abre en silencio
 * (sin mensajes en el chat); las apps y el catálogo dependen del nivel del teléfono (dev.tcgshop.phone.PhoneTier).
 */
public class PhoneItem extends Item {
    public PhoneItem(Settings settings) {
        super(settings);
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
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("item.tcgshop.tcg_phone.tip").formatted(Formatting.GRAY));
    }
}

package dev.tcgshop.shop;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

/**
 * Shop zone protection (in X, Y and Z):
 *  · Only the owner can break or place blocks inside their zone (and opening other people's chests is not allowed).
 *  · Shop furniture can only be placed inside a shop zone of YOURS.
 */
public final class ShopProtection {
    private ShopProtection() {}

    private static boolean bypass(PlayerEntity p) {
        return p.isCreative() && p.hasPermissionLevel(2);
    }

    public static void register() {
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, be) -> {
            if (!(world instanceof ServerWorld sw) || bypass(player)) return true;
            ShopZones.Zone z = ShopZones.get(sw).at(pos);
            if (z != null && !z.owner.equals(player.getUuid())) {
                player.sendMessage(Text.translatable("tcgshop.shop.protected", z.ownerName).formatted(Formatting.RED), true);
                return false;
            }
            return true;
        });

        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            BlockPos target = hit.getBlockPos();
            if (!(world instanceof ServerWorld sw) || bypass(player)) return ActionResult.PASS;
            ShopZones zones = ShopZones.get(sw);
            ItemStack held = player.getStackInHand(hand);

            // opening chests/containers in someone else's shop: not allowed
            ShopZones.Zone targetZone = zones.at(target);
            BlockEntity be = world.getBlockEntity(target);
            if (targetZone != null && !targetZone.owner.equals(player.getUuid()) && be instanceof Inventory && !(be instanceof ShopDisplayBlockEntity)
                    && !player.isSneaking()) {
                player.sendMessage(Text.translatable("tcgshop.shop.protected", targetZone.ownerName).formatted(Formatting.RED), true);
                return ActionResult.FAIL;
            }

            if (!(held.getItem() instanceof BlockItem bi)) return ActionResult.PASS;
            BlockPos place = new ItemPlacementContext(player, hand, held, hit).getBlockPos();
            ShopZones.Zone z = zones.at(place);
            if (z != null && !z.owner.equals(player.getUuid())) {
                player.sendMessage(Text.translatable("tcgshop.shop.protected", z.ownerName).formatted(Formatting.RED), true);
                return ActionResult.FAIL;
            }
            if (bi.getBlock() instanceof ShopFurniture sf && sf.shopOnly() && (z == null || !z.owner.equals(player.getUuid()))) {
                player.sendMessage(Text.translatable("tcgshop.shop.furniture_outside").formatted(Formatting.RED), true);
                return ActionResult.FAIL;
            }
            return ActionResult.PASS;
        });
    }
}

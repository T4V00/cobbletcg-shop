package dev.tcgshop.shop;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.BlockState;
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
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;

/**
 * Shop zone protection (in X, Y and Z):
 *  · Only the owner can break or place blocks inside their zone (and opening other people's chests is not allowed).
 *  · Shop furniture can only be placed inside a shop zone of YOURS.
 *  · 1.0: small tiers (ShopTier.maxDisplays, the Paper stall) only hold that many selling displays, and only small
 *    ones (at most EconomyConfig.stallMaxDisplaySlots slots, never RGB).
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
            // 1.0: the Paper stall has room for a single selling display (home displays don't count)
            if (bi.getBlock() instanceof ShopDisplayBlock db && !db.home && z != null) {
                int max = z.tierInfo().maxDisplays;
                if (max > 0 && sellingDisplays(sw, z) >= max) {
                    player.sendMessage(Text.translatable("tcgshop.shop.stall_display_limit",
                            Text.translatable("tcgshop.shop.tier." + z.tierInfo().key), max).formatted(Formatting.RED), true);
                    return ActionResult.FAIL;
                }
                // ...and it must be a small one: few slots (EconomyConfig.stallMaxDisplaySlots) and no RGB display
                if (max > 0 && !isSmallDisplay(db)) {
                    player.sendMessage(Text.translatable("tcgshop.shop.stall_display_too_big",
                            Text.translatable("tcgshop.shop.tier." + z.tierInfo().key), stallMaxSlots()).formatted(Formatting.RED), true);
                    return ActionResult.FAIL;
                }
            }
            return ActionResult.PASS;
        });
    }

    /** Most slots the selling display of a small tier (ShopTier.maxDisplays > 0, the Paper stall) may have. */
    public static int stallMaxSlots() {
        return Math.max(1, dev.tcgshop.EconomyConfig.INSTANCE.stallMaxDisplaySlots);
    }

    /** Is this display small enough for the Paper stall? At most stallMaxSlots() slots and not an RGB display. */
    public static boolean isSmallDisplay(ShopDisplayBlock db) {
        return !db.isRgb() && db.slots() <= stallMaxSlots();
    }

    /** Selling displays (master blocks of non-home displays) inside the zone. Only used for the small tiers, so the scan is tiny. */
    public static int sellingDisplays(ServerWorld world, ShopZones.Zone z) {
        Box b = z.box();
        int n = 0;
        for (BlockPos p : BlockPos.iterate(MathHelper.floor(b.minX), MathHelper.floor(b.minY), MathHelper.floor(b.minZ),
                MathHelper.floor(b.maxX) - 1, MathHelper.floor(b.maxY) - 1, MathHelper.floor(b.maxZ) - 1)) {
            BlockState s = world.getBlockState(p);
            if (s.getBlock() instanceof ShopDisplayBlock d && !d.home && s.get(ShopDisplayBlock.PART) == 0) n++;
        }
        return n;
    }
}

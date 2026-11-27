package dev.tcgshop.shop;

import dev.masterball.api.platform.event.PlayerEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Shop zone protection (in X, Y and Z):
 *  · Only the owner can break or place blocks inside their zone (and opening other people's chests is not allowed).
 *  · Shop furniture can only be placed inside a shop zone of YOURS.
 *  · 1.0: small tiers (ShopTier.maxDisplays, the Paper stall) only hold that many selling displays, and only small
 *    ones (at most EconomyConfig.stallMaxDisplaySlots slots, never RGB).
 */
public final class ShopProtection {
    private ShopProtection() {}

    private static boolean bypass(Player p) {
        return p.isCreative() && p.hasPermissions(2);
    }

    public static void register() {
        PlayerEvents.BREAK_BLOCK.register((world, player, pos, state, be) -> {
            if (!(world instanceof ServerLevel sw) || bypass(player)) return true;
            ShopZones.Zone z = ShopZones.get(sw).at(pos);
            // 1.1: breaking any part of a wide door removes (and drops) all six, so every part must be breakable by this player
            if ((z == null || z.owner.equals(player.getUUID())) && state.getBlock() instanceof WideDoorBlock)
                z = WideDoorBlock.foreignZone(sw, player, WideDoorBlock.parts(WideDoorBlock.mainPos(state, pos), state.getValue(WideDoorBlock.FACING)));
            if (z != null && !z.owner.equals(player.getUUID())) {
                player.displayClientMessage(Component.translatable("tcgshop.shop.protected", z.ownerName).withStyle(ChatFormatting.RED), true);
                return false;
            }
            return true;
        });

        PlayerEvents.USE_BLOCK.register((player, world, hand, hit) -> {
            BlockPos target = hit.getBlockPos();
            if (!(world instanceof ServerLevel sw) || bypass(player)) return InteractionResult.PASS;
            ShopZones zones = ShopZones.get(sw);
            ItemStack held = player.getItemInHand(hand);

            // opening chests/containers in someone else's shop: not allowed
            ShopZones.Zone targetZone = zones.at(target);
            BlockEntity be = world.getBlockEntity(target);
            if (targetZone != null && !targetZone.owner.equals(player.getUUID()) && be instanceof Container && !(be instanceof ShopDisplayBlockEntity)
                    && !player.isShiftKeyDown()) {
                player.displayClientMessage(Component.translatable("tcgshop.shop.protected", targetZone.ownerName).withStyle(ChatFormatting.RED), true);
                return InteractionResult.FAIL;
            }

            if (!(held.getItem() instanceof BlockItem bi)) return InteractionResult.PASS;
            BlockPos place = new BlockPlaceContext(player, hand, held, hit).getClickedPos();
            ShopZones.Zone z = zones.at(place);
            if (z != null && !z.owner.equals(player.getUUID())) {
                player.displayClientMessage(Component.translatable("tcgshop.shop.protected", z.ownerName).withStyle(ChatFormatting.RED), true);
                return InteractionResult.FAIL;
            }
            if (bi.getBlock() instanceof ShopFurniture sf && sf.shopOnly() && (z == null || !z.owner.equals(player.getUUID()))) {
                player.displayClientMessage(Component.translatable("tcgshop.shop.furniture_outside").withStyle(ChatFormatting.RED), true);
                return InteractionResult.FAIL;
            }
            // 1.0: the Paper stall has room for a single selling display (home displays don't count)
            if (bi.getBlock() instanceof ShopDisplayBlock db && !db.home && z != null) {
                int max = z.tierInfo().maxDisplays;
                if (max > 0 && sellingDisplays(sw, z) >= max) {
                    player.displayClientMessage(Component.translatable("tcgshop.shop.stall_display_limit",
                            Component.translatable("tcgshop.shop.tier." + z.tierInfo().key), max).withStyle(ChatFormatting.RED), true);
                    return InteractionResult.FAIL;
                }
                // ...and it must be a small one: few slots (EconomyConfig.stallMaxDisplaySlots) and no RGB display
                if (max > 0 && !isSmallDisplay(db)) {
                    player.displayClientMessage(Component.translatable("tcgshop.shop.stall_display_too_big",
                            Component.translatable("tcgshop.shop.tier." + z.tierInfo().key), stallMaxSlots()).withStyle(ChatFormatting.RED), true);
                    return InteractionResult.FAIL;
                }
            }
            return InteractionResult.PASS;
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
    public static int sellingDisplays(ServerLevel world, ShopZones.Zone z) {
        AABB b = z.box();
        int n = 0;
        for (BlockPos p : BlockPos.betweenClosed(Mth.floor(b.minX), Mth.floor(b.minY), Mth.floor(b.minZ),
                Mth.floor(b.maxX) - 1, Mth.floor(b.maxY) - 1, Mth.floor(b.maxZ) - 1)) {
            BlockState s = world.getBlockState(p);
            if (s.getBlock() instanceof ShopDisplayBlock d && !d.home && s.getValue(ShopDisplayBlock.PART) == 0) n++;
        }
        return n;
    }
}

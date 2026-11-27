package dev.tcgshop.shop;

import dev.masterball.api.platform.Net;
import com.mojang.serialization.MapCodec;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Shop block: placing it creates a protected shop zone (centered on it) sized by its tier.
 * 1.1: breaking it (only the owner can) makes the shop dormant instead of deleting it: the core drops one item linked to
 * the shop and placing that item restores it, wherever it goes (ShopOpenClose). Sneak + empty hand opens / closes it. Right-clicking it with a higher-tier shop block upgrades the
 * zone in place (keeps everything). One shop per player until they own an Amethyst one, and never more than
 * EconomyConfig.maxShopsPerPlayer in total (1.0).
 */
public class ShopCoreBlock extends Block {
    public final ShopTier tier;

    public ShopCoreBlock(ShopTier tier, Properties settings) {
        super(settings);
        this.tier = tier;
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return simpleCodec(s -> new ShopCoreBlock(tier, s));
    }

    @Override
    public void setPlacedBy(Level world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(world, pos, state, placer, stack);
        if (!(world instanceof ServerLevel sw) || !(placer instanceof ServerPlayer player)) return;
        ShopZones zones = ShopZones.get(sw);
        // 1.1: a core dropped by a dormant shop restores it (its owner) or is refused (anyone else); a stale link is a plain core
        switch (ShopOpenClose.tryRestore(sw, pos, player, this, stack)) {
            case RESTORED -> { return; }
            case REFUSED -> { refund(world, pos, player, stack); return; }
            default -> {}
        }
        // 1.0: one shop per player until one of theirs is Amethyst or higher (players who already had several keep them)
        if (!mayOpenAnother(player)) {
            player.displayClientMessage(Component.translatable("tcgshop.shop.one_per_player",
                    Component.translatable("tcgshop.shop.tier." + ShopTier.MULTI_SHOP_TIER.key)).withStyle(ChatFormatting.RED), false);
            player.displayClientMessage(Component.translatable("tcgshop.shop.one_per_player.hint").withStyle(ChatFormatting.GRAY), false);
            refund(world, pos, player, stack);
            return;
        }
        // 1.0: and never more than maxShopsPerPlayer shops in total, even from Amethyst on (owners already above it keep theirs)
        if (!belowShopCap(player)) {
            player.displayClientMessage(Component.translatable("tcgshop.shop.max_shops", dev.tcgshop.EconomyConfig.INSTANCE.maxShopsPerPlayer)
                    .withStyle(ChatFormatting.RED), false);
            player.displayClientMessage(Component.translatable("tcgshop.shop.max_shops.hint").withStyle(ChatFormatting.GRAY), false);
            refund(world, pos, player, stack);
            return;
        }
        AABB box = ShopZones.boxFor(pos, tier);
        if (zones.overlaps(box)) {
            player.displayClientMessage(Component.translatable("tcgshop.shop.overlap").withStyle(ChatFormatting.RED), false);
            refund(world, pos, player, stack);
            return;
        }
        zones.add(new ShopZones.Zone(UUID.randomUUID(), player.getUUID(), player.getName().getString(), pos, tier.rank()));
        player.displayClientMessage(Component.translatable("tcgshop.shop.created", Component.translatable("tcgshop.shop.tier." + tier.key), tier.size, tier.size)
                .withStyle(ChatFormatting.GREEN), false);
        if (tier.maxDisplays > 0)
            player.displayClientMessage(Component.translatable("tcgshop.shop.stall_hint", tier.maxDisplays, tier.maxCustomers).withStyle(ChatFormatting.GRAY), false);
        dev.tcgshop.advancement.CardAdvancements.grant(player, "open_shop");
        if (tier == ShopTier.NETHERITE) dev.tcgshop.advancement.CardAdvancements.grant(player, "shop_netherite");
        dev.tcgshop.onboarding.Onboarding.onShopCreated(player);
        ShopZones.notifyMultiShop(sw.getServer(), player.getUUID(), tier);
        ShopServer.syncZones(sw.getServer());
    }

    /** Removes the just-placed core and gives it back (refused placement), keeping its components (1.1: shop link). */
    private void refund(Level world, BlockPos pos, ServerPlayer player, ItemStack stack) {
        world.destroyBlock(pos, false);
        if (!player.isCreative()) player.getInventory().placeItemBackInInventory(stack.isEmpty() ? new ItemStack(asItem()) : stack.copyWithCount(1));
    }

    /**
     * Can this player open one more shop? Yes if they own none, or if their best shop is Amethyst or higher
     * (ShopTier.canOwnMultipleShops). Operators in creative mode and servers with oneShopPerPlayer = false skip the rule.
     * Called before the new zone is added, so the shop being placed never counts.
     */
    public static boolean mayOpenAnother(ServerPlayer player) {
        return mayOpenAnother(player, null);
    }

    /** 1.1: restoring = the dormant shop being restored, which does not count as "another" shop. */
    public static boolean mayOpenAnother(ServerPlayer player, @org.jetbrains.annotations.Nullable UUID restoring) {
        if (!dev.tcgshop.EconomyConfig.INSTANCE.oneShopPerPlayer) return true;
        if (player.isCreative() && player.hasPermissions(2)) return true;
        net.minecraft.server.MinecraftServer server = player.getServer();
        if (server == null) return true;
        java.util.List<ShopZones.Zone> owned = ShopZones.ownedBy(server, player.getUUID());
        if (restoring != null) owned.removeIf(z -> z.id.equals(restoring));
        if (owned.isEmpty()) return true;
        return ShopTier.canOwnMultipleShops(ShopZones.highestOwned(server, player.getUUID()));
    }

    /**
     * Is this player under the total shop cap (EconomyConfig.maxShopsPerPlayer, 0 = no cap)? Players who already own
     * more keep their shops but cannot open another. Operators in creative mode skip it, same as mayOpenAnother.
     * Called before the new zone is added, so the shop being placed never counts.
     */
    public static boolean belowShopCap(ServerPlayer player) {
        return belowShopCap(player, null);
    }

    /** 1.1: restoring = the dormant shop being restored (it already counts, so it is left out). */
    public static boolean belowShopCap(ServerPlayer player, @org.jetbrains.annotations.Nullable UUID restoring) {
        int cap = dev.tcgshop.EconomyConfig.INSTANCE.maxShopsPerPlayer;
        if (cap <= 0) return true;
        if (player.isCreative() && player.hasPermissions(2)) return true;
        net.minecraft.server.MinecraftServer server = player.getServer();
        if (server == null) return true;
        java.util.List<ShopZones.Zone> owned = ShopZones.ownedBy(server, player.getUUID());
        if (restoring != null) owned.removeIf(z -> z.id.equals(restoring));
        return owned.size() < cap;
    }

    /**
     * 1.0 in-place upgrade: right-click your own shop block with a higher-tier shop block. The zone keeps its id, stats,
     * reviews, employees and events; it only grows (never shrinks: a pre-1.0 16×16 wood shop stays at least 16×16) and the
     * old block is given back, same as breaking it and placing the new one used to (which lost the stats).
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(stack.getItem() instanceof BlockItem bi) || !(bi.getBlock() instanceof ShopCoreBlock target))
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!(world instanceof ServerLevel sw) || !(player instanceof ServerPlayer sp)) return ItemInteractionResult.SUCCESS;
        ShopZones zones = ShopZones.get(sw);
        ShopZones.Zone z = zones.byCore(pos);
        if (z == null) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!z.owner.equals(sp.getUUID())) {
            sp.displayClientMessage(Component.translatable("tcgshop.shop.protected", z.ownerName).withStyle(ChatFormatting.RED), true);
            return ItemInteractionResult.FAIL;
        }
        // 1.1: a core that is still the key of a dormant shop is never used up as an upgrade (it would strand that shop)
        if (CoreResale.lockedByDormantShop(sw.getServer(), stack)) {
            sp.displayClientMessage(Component.translatable("tcgshop.shop.upgrade_linked").withStyle(ChatFormatting.RED), false);
            return ItemInteractionResult.FAIL;
        }
        ShopTier from = z.tierInfo(), to = target.tier;
        if (to.rank() <= from.rank()) { // any higher tier works (Paper → Wood → ... → Netherite, or skipping tiers)
            sp.displayClientMessage(Component.translatable("tcgshop.shop.upgrade_not_higher", Component.translatable("tcgshop.shop.tier." + from.key))
                    .withStyle(ChatFormatting.RED), true);
            return ItemInteractionResult.FAIL;
        }
        int newSize = Math.max(z.size(), to.size);
        if (zones.overlapsOther(ShopZones.boxFor(pos, newSize, to.up), z)) {
            sp.displayClientMessage(Component.translatable("tcgshop.shop.upgrade_overlap", newSize, newSize).withStyle(ChatFormatting.RED), false);
            return ItemInteractionResult.FAIL;
        }
        z.tier = to.rank();
        z.size = newSize;
        zones.setDirty();
        // the zone survives the block swap: onStateReplaced keeps it when the new block is also a shop block
        world.setBlock(pos, target.defaultBlockState(), Block.UPDATE_ALL);
        if (!sp.isCreative()) {
            stack.shrink(1);
            sp.getInventory().placeItemBackInInventory(new ItemStack(asItem()));
        }
        world.playSound(null, pos, net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP, net.minecraft.sounds.SoundSource.BLOCKS, 0.8f, 1.1f);
        sp.displayClientMessage(Component.translatable("tcgshop.shop.upgraded", Component.translatable("tcgshop.shop.tier." + to.key), newSize, newSize)
                .withStyle(ChatFormatting.GREEN), false);
        if (to == ShopTier.NETHERITE) dev.tcgshop.advancement.CardAdvancements.grant(sp, "shop_netherite");
        ShopZones.notifyMultiShop(sw.getServer(), sp.getUUID(), to);
        ShopServer.syncZones(sw.getServer());
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    protected void onRemove(BlockState state, Level world, BlockPos pos, BlockState newState, boolean moved) {
        // replaced by another shop block = in-place upgrade (onUseWithItem): the zone stays
        if (!state.is(newState.getBlock()) && !(newState.getBlock() instanceof ShopCoreBlock) && world instanceof ServerLevel sw) {
            ShopZones.Zone z = ShopZones.get(sw).byCore(pos);
            // 1.1: the shop goes dormant (whole record kept, closed); its core item drops linked to it (getDrops, or the
            // end-of-tick fallback when the removal drops nothing). If getDrops already ran before the removal, it made the
            // shop dormant itself, so byCore finds nothing here and nothing is done twice.
            if (z != null) {
                ShopOpenClose.onCoreRemoved(sw, z);
                ShopOpenClose.expectDrop(sw, pos, z.id);
            }
        }
        super.onRemove(state, world, pos, newState, moved);
    }

    /**
     * 1.1: the loot table drops the plain core; for a shop core, that one item is swapped for the linked core, so exactly
     * one key drops and never a plain duplicate:
     *  · player break: onRemove ran first (shop just went dormant, pending drop) -> this call claims the pending drop;
     *  · drops computed BEFORE the removal (Level.destroyBlock, the Wither, Create drills, explosions): the shop is still
     *    active and the block is still this core -> it goes dormant right here, and the later onRemove does nothing;
     *  · a second call for the same break (dormant this tick, drop already claimed) drops no core at all;
     *  · no call at all (creative, /setblock...) -> ShopOpenClose.flushPendingDrops drops it at the end of the tick.
     */
    @Override
    public java.util.List<ItemStack> getDrops(BlockState state, net.minecraft.world.level.storage.loot.LootParams.Builder params) {
        java.util.List<ItemStack> drops = super.getDrops(state, params);
        net.minecraft.world.phys.Vec3 at = params.getOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN);
        if (at == null) return drops;
        ServerLevel level = params.getLevel();
        BlockPos pos = BlockPos.containing(at);
        ItemStack linked = null;
        ShopZones.Zone z = ShopOpenClose.dormantAt(level, pos);
        if (z != null) {
            if (ShopOpenClose.takeDrop(level, pos, z.id)) linked = ShopOpenClose.linkedItem(z);
        } else {
            ShopZones.Zone active = ShopZones.get(level).byCore(pos);
            if (active == null || level.getBlockState(pos).getBlock() != this) return drops; // not a shop's core
            ShopOpenClose.onCoreRemoved(level, active); // drops before removal: dormant now, onRemove finds nothing later
            linked = ShopOpenClose.linkedItem(active);
        }
        java.util.List<ItemStack> out = new java.util.ArrayList<>(drops);
        out.removeIf(st -> st.getItem() instanceof BlockItem bi && bi.getBlock() instanceof ShopCoreBlock);
        if (linked != null) out.add(linked);
        return out;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit) {
        if (world instanceof ServerLevel sw && player instanceof ServerPlayer sp) {
            ShopZones.Zone z = ShopZones.get(sw).byCore(pos);
            if (z != null) {
                if (z.owner.equals(sp.getUUID()) && sp.isShiftKeyDown()) ShopOpenClose.toggle(sp, z.id); // 1.1: sneak = open / close
                else if (z.owner.equals(sp.getUUID())) showStatus(sw, sp, z);
                else ShopServer.describe(sp, z);
            }
        }
        return InteractionResult.sidedSuccess(world.isClientSide);
    }

    /**
     * Owner: window with the stats, capacity, customer flow and the missions to attract more people
     * (ShopMissions). If the calculation is stale (over 10 s), it is redone as a preview (ShopLife.preview: factors and
     * missions, without moving reputation or advancing events, which only the per-minute recalculation does). Without the
     * add-on on the client, the same summarized in chat.
     */
    private static void showStatus(ServerLevel sw, ServerPlayer sp, ShopZones.Zone z) {
        if (z.lastCalc < 0 || z.missionProgress == null || sw.getGameTime() - z.lastCalc > 200 || sw.getGameTime() < z.lastCalc)
            ShopLife.preview(sw, z);
        if (Net.INSTANCE.canSend(sp, dev.tcgshop.network.UiPayloads.Open.ID)) {
            Net.INSTANCE.send(sp,
                    new dev.tcgshop.network.UiPayloads.Open("shop_status", ShopMissions.statusNbt(sw, z, sp)));
            return;
        }
        ShopServer.describe(sp, z);
        sp.displayClientMessage(Component.translatable("tcgshop.status.chat_line", ShopLife.insideCount(sw, z), ShopMissions.effectiveCapacity(z),
                ShopMissions.completedCount(z), ShopMissions.COUNT, (int) Math.round(ShopMissions.flow(z) * 100) + "%").withStyle(ChatFormatting.AQUA), false);
        int next = ShopMissions.nextStep(z);
        if (next >= 0) {
            ShopMissions.Mission m = ShopMissions.Mission.values()[next];
            sp.displayClientMessage(Component.translatable("tcgshop.status.chat_next", Component.translatable("tcgshop.mission." + m.key),
                    Component.translatable("tcgshop.mission." + m.key + ".hint")).withStyle(ChatFormatting.YELLOW), false);
        }
    }
}

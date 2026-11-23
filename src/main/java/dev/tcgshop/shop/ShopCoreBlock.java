package dev.tcgshop.shop;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

import java.util.UUID;

/**
 * Shop block: placing it creates a protected shop zone (centered on it) sized by its tier.
 * Breaking it (only the owner can) removes the zone. Right-clicking it with a higher-tier shop block upgrades the
 * zone in place (keeps everything). One shop per player until they own an Amethyst one, and never more than
 * EconomyConfig.maxShopsPerPlayer in total (1.0).
 */
public class ShopCoreBlock extends Block {
    public final ShopTier tier;

    public ShopCoreBlock(ShopTier tier, Settings settings) {
        super(settings);
        this.tier = tier;
    }

    @Override
    protected MapCodec<? extends Block> getCodec() {
        return createCodec(s -> new ShopCoreBlock(tier, s));
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.onPlaced(world, pos, state, placer, stack);
        if (!(world instanceof ServerWorld sw) || !(placer instanceof ServerPlayerEntity player)) return;
        ShopZones zones = ShopZones.get(sw);
        // 1.0: one shop per player until one of theirs is Amethyst or higher (players who already had several keep them)
        if (!mayOpenAnother(player)) {
            player.sendMessage(Text.translatable("tcgshop.shop.one_per_player",
                    Text.translatable("tcgshop.shop.tier." + ShopTier.MULTI_SHOP_TIER.key)).formatted(Formatting.RED), false);
            player.sendMessage(Text.translatable("tcgshop.shop.one_per_player.hint").formatted(Formatting.GRAY), false);
            refund(world, pos, player);
            return;
        }
        // 1.0: and never more than maxShopsPerPlayer shops in total, even from Amethyst on (owners already above it keep theirs)
        if (!belowShopCap(player)) {
            player.sendMessage(Text.translatable("tcgshop.shop.max_shops", dev.tcgshop.EconomyConfig.INSTANCE.maxShopsPerPlayer)
                    .formatted(Formatting.RED), false);
            player.sendMessage(Text.translatable("tcgshop.shop.max_shops.hint").formatted(Formatting.GRAY), false);
            refund(world, pos, player);
            return;
        }
        Box box = ShopZones.boxFor(pos, tier);
        if (zones.overlaps(box)) {
            player.sendMessage(Text.translatable("tcgshop.shop.overlap").formatted(Formatting.RED), false);
            refund(world, pos, player);
            return;
        }
        zones.add(new ShopZones.Zone(UUID.randomUUID(), player.getUuid(), player.getName().getString(), pos, tier.rank()));
        player.sendMessage(Text.translatable("tcgshop.shop.created", Text.translatable("tcgshop.shop.tier." + tier.key), tier.size, tier.size)
                .formatted(Formatting.GREEN), false);
        if (tier.maxDisplays > 0)
            player.sendMessage(Text.translatable("tcgshop.shop.stall_hint", tier.maxDisplays, tier.maxCustomers).formatted(Formatting.GRAY), false);
        dev.tcgshop.advancement.CardAdvancements.grant(player, "open_shop");
        if (tier == ShopTier.NETHERITE) dev.tcgshop.advancement.CardAdvancements.grant(player, "shop_netherite");
        dev.tcgshop.onboarding.Onboarding.onShopCreated(player);
        ShopZones.notifyMultiShop(sw.getServer(), player.getUuid(), tier);
        ShopServer.syncZones(sw.getServer());
    }

    /** Removes the just-placed core and gives it back (refused placement). */
    private void refund(World world, BlockPos pos, ServerPlayerEntity player) {
        world.breakBlock(pos, false);
        if (!player.isCreative()) player.getInventory().offerOrDrop(new ItemStack(asItem()));
    }

    /**
     * Can this player open one more shop? Yes if they own none, or if their best shop is Amethyst or higher
     * (ShopTier.canOwnMultipleShops). Operators in creative mode and servers with oneShopPerPlayer = false skip the rule.
     * Called before the new zone is added, so the shop being placed never counts.
     */
    public static boolean mayOpenAnother(ServerPlayerEntity player) {
        if (!dev.tcgshop.EconomyConfig.INSTANCE.oneShopPerPlayer) return true;
        if (player.isCreative() && player.hasPermissionLevel(2)) return true;
        net.minecraft.server.MinecraftServer server = player.getServer();
        if (server == null || ShopZones.ownedBy(server, player.getUuid()).isEmpty()) return true;
        return ShopTier.canOwnMultipleShops(ShopZones.highestOwned(server, player.getUuid()));
    }

    /**
     * Is this player under the total shop cap (EconomyConfig.maxShopsPerPlayer, 0 = no cap)? Players who already own
     * more keep their shops but cannot open another. Operators in creative mode skip it, same as mayOpenAnother.
     * Called before the new zone is added, so the shop being placed never counts.
     */
    public static boolean belowShopCap(ServerPlayerEntity player) {
        int cap = dev.tcgshop.EconomyConfig.INSTANCE.maxShopsPerPlayer;
        if (cap <= 0) return true;
        if (player.isCreative() && player.hasPermissionLevel(2)) return true;
        net.minecraft.server.MinecraftServer server = player.getServer();
        return server == null || ShopZones.ownedBy(server, player.getUuid()).size() < cap;
    }

    /**
     * 1.0 in-place upgrade: right-click your own shop block with a higher-tier shop block. The zone keeps its id, stats,
     * reviews, employees and events; it only grows (never shrinks: a pre-1.0 16×16 wood shop stays at least 16×16) and the
     * old block is given back, same as breaking it and placing the new one used to (which lost the stats).
     */
    @Override
    protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (!(stack.getItem() instanceof BlockItem bi) || !(bi.getBlock() instanceof ShopCoreBlock target))
            return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!(world instanceof ServerWorld sw) || !(player instanceof ServerPlayerEntity sp)) return ItemActionResult.SUCCESS;
        ShopZones zones = ShopZones.get(sw);
        ShopZones.Zone z = zones.byCore(pos);
        if (z == null) return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!z.owner.equals(sp.getUuid())) {
            sp.sendMessage(Text.translatable("tcgshop.shop.protected", z.ownerName).formatted(Formatting.RED), true);
            return ItemActionResult.FAIL;
        }
        ShopTier from = z.tierInfo(), to = target.tier;
        if (to.rank() <= from.rank()) { // any higher tier works (Paper → Wood → ... → Netherite, or skipping tiers)
            sp.sendMessage(Text.translatable("tcgshop.shop.upgrade_not_higher", Text.translatable("tcgshop.shop.tier." + from.key))
                    .formatted(Formatting.RED), true);
            return ItemActionResult.FAIL;
        }
        int newSize = Math.max(z.size(), to.size);
        if (zones.overlapsOther(ShopZones.boxFor(pos, newSize, to.up), z)) {
            sp.sendMessage(Text.translatable("tcgshop.shop.upgrade_overlap", newSize, newSize).formatted(Formatting.RED), false);
            return ItemActionResult.FAIL;
        }
        z.tier = to.rank();
        z.size = newSize;
        zones.markDirty();
        // the zone survives the block swap: onStateReplaced keeps it when the new block is also a shop block
        world.setBlockState(pos, target.getDefaultState(), Block.NOTIFY_ALL);
        if (!sp.isCreative()) {
            stack.decrement(1);
            sp.getInventory().offerOrDrop(new ItemStack(asItem()));
        }
        world.playSound(null, pos, net.minecraft.sound.SoundEvents.ENTITY_PLAYER_LEVELUP, net.minecraft.sound.SoundCategory.BLOCKS, 0.8f, 1.1f);
        sp.sendMessage(Text.translatable("tcgshop.shop.upgraded", Text.translatable("tcgshop.shop.tier." + to.key), newSize, newSize)
                .formatted(Formatting.GREEN), false);
        if (to == ShopTier.NETHERITE) dev.tcgshop.advancement.CardAdvancements.grant(sp, "shop_netherite");
        ShopZones.notifyMultiShop(sw.getServer(), sp.getUuid(), to);
        ShopServer.syncZones(sw.getServer());
        return ItemActionResult.SUCCESS;
    }

    @Override
    protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        // replaced by another shop block = in-place upgrade (onUseWithItem): the zone stays
        if (!state.isOf(newState.getBlock()) && !(newState.getBlock() instanceof ShopCoreBlock) && world instanceof ServerWorld sw) {
            ShopZones zones = ShopZones.get(sw);
            ShopZones.Zone z = zones.byCore(pos);
            if (z != null) {
                dev.tcgshop.events.EventManager.onZoneRemoved(sw, z); // 0.9: events clean up their own state before the zone is deleted
                zones.remove(z);
                MissionLedger.forget(sw.getServer(), z.id); // 1.0: its mission data goes with it
                ShopServer.syncZones(sw.getServer());
            }
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (world instanceof ServerWorld sw && player instanceof ServerPlayerEntity sp) {
            ShopZones.Zone z = ShopZones.get(sw).byCore(pos);
            if (z != null) {
                if (z.owner.equals(sp.getUuid())) showStatus(sw, sp, z);
                else ShopServer.describe(sp, z);
            }
        }
        return ActionResult.success(world.isClient);
    }

    /**
     * Owner: window with the stats, capacity, customer flow and the missions to attract more people
     * (ShopMissions). If the calculation is stale (over 10 s), it is redone as a preview (ShopLife.preview: factors and
     * missions, without moving reputation or advancing events, which only the per-minute recalculation does). Without the
     * add-on on the client, the same summarized in chat.
     */
    private static void showStatus(ServerWorld sw, ServerPlayerEntity sp, ShopZones.Zone z) {
        if (z.lastCalc < 0 || z.missionProgress == null || sw.getTime() - z.lastCalc > 200 || sw.getTime() < z.lastCalc)
            ShopLife.preview(sw, z);
        if (net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(sp, dev.tcgshop.network.UiPayloads.Open.ID)) {
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(sp,
                    new dev.tcgshop.network.UiPayloads.Open("shop_status", ShopMissions.statusNbt(sw, z, sp)));
            return;
        }
        ShopServer.describe(sp, z);
        sp.sendMessage(Text.translatable("tcgshop.status.chat_line", ShopLife.insideCount(sw, z), ShopMissions.effectiveCapacity(z),
                ShopMissions.completedCount(z), ShopMissions.COUNT, (int) Math.round(ShopMissions.flow(z) * 100) + "%").formatted(Formatting.AQUA), false);
        int next = ShopMissions.nextStep(z);
        if (next >= 0) {
            ShopMissions.Mission m = ShopMissions.Mission.values()[next];
            sp.sendMessage(Text.translatable("tcgshop.status.chat_next", Text.translatable("tcgshop.mission." + m.key),
                    Text.translatable("tcgshop.mission." + m.key + ".hint")).formatted(Formatting.YELLOW), false);
        }
    }
}

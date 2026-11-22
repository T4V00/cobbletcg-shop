package dev.tcgshop.shop;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

import java.util.UUID;

/**
 * Bloque de tienda: al colocarlo crea una zona de tienda protegida (centrada en él) del tamaño de su nivel.
 * Romperlo (sólo el dueño puede) elimina la zona.
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
        Box box = ShopZones.boxFor(pos, tier);
        if (zones.overlaps(box)) {
            player.sendMessage(Text.translatable("tcgshop.shop.overlap").formatted(Formatting.RED), false);
            world.breakBlock(pos, false);
            if (!player.isCreative()) player.getInventory().offerOrDrop(new ItemStack(asItem()));
            return;
        }
        zones.add(new ShopZones.Zone(UUID.randomUUID(), player.getUuid(), player.getName().getString(), pos, tier.ordinal()));
        player.sendMessage(Text.translatable("tcgshop.shop.created", Text.translatable("tcgshop.shop.tier." + tier.key), tier.size, tier.size)
                .formatted(Formatting.GREEN), false);
        dev.tcgshop.advancement.CardAdvancements.grant(player, "open_shop");
        if (tier == ShopTier.NETHERITE) dev.tcgshop.advancement.CardAdvancements.grant(player, "shop_netherite");
        dev.tcgshop.onboarding.Onboarding.onShopCreated(player);
        ShopServer.syncZones(sw.getServer());
    }

    @Override
    protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock()) && world instanceof ServerWorld sw) {
            ShopZones zones = ShopZones.get(sw);
            ShopZones.Zone z = zones.byCore(pos);
            if (z != null) {
                dev.tcgshop.events.EventManager.onZoneRemoved(sw, z); // 0.9: los eventos limpian lo suyo antes de borrar la zona
                zones.remove(z);
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
     * Dueño: ventana con las estadísticas, el aforo, el flujo de clientes y las misiones para atraer más gente
     * (ShopMissions). Si el cálculo es viejo (más de 10 s), se rehace como vista previa (ShopLife.preview: factores y
     * misiones, sin mover la reputación ni avanzar los eventos, que sólo lo hace el recálculo de cada minuto). Sin el
     * add-on en el cliente, lo mismo resumido en el chat.
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
                ShopMissions.count(z.missions), ShopMissions.COUNT, (int) Math.round(z.flow() * 100) + "%").formatted(Formatting.AQUA), false);
        int next = ShopMissions.nextStep(z.missions);
        if (next >= 0) {
            ShopMissions.Mission m = ShopMissions.Mission.values()[next];
            sp.sendMessage(Text.translatable("tcgshop.status.chat_next", Text.translatable("tcgshop.mission." + m.key),
                    Text.translatable("tcgshop.mission." + m.key + ".hint")).formatted(Formatting.YELLOW), false);
        }
    }
}

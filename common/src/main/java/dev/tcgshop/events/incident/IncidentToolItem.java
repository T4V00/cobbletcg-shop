package dev.tcgshop.events.incident;

import dev.tcgshop.shop.TcgBlocks;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Tools for fixing incidents: tcgshop:mop (mop: puddles, droppings, mess) and tcgshop:repair_kit
 * (repair kit: sparks, scorch marks, broken showcases). On their own they do nothing: the incident block
 * checks the tag #tcgshop:fix/&lt;kind&gt; and takes durability from them.
 */
public class IncidentToolItem extends Item {
    private final String key;

    public IncidentToolItem(Properties settings, String key) {
        super(settings);
        this.key = key;
    }

    /** While sneaking, the game does not call the block: the tool fixes it anyway (same path as a normal click). */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level w = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = w.getBlockState(pos);
        if (!state.is(TcgBlocks.INCIDENT)) {
            // 1.1 (cleanliness update): the mop also cleans droppings (even while sneaking) and scrubs grime off your shop's floor
            if (!"mop".equals(key)) return super.useOn(context);
            if (w.isClientSide) { // mirror the server: only a dropping or your own shop's floor consumes the click (else the off hand still works)
                boolean floor = context.getClickedFace() == net.minecraft.core.Direction.UP && context.getPlayer() != null
                        && dev.tcgshop.shop.ShopZones.CLIENT_OWNS.test(context.getPlayer(), pos.above());
                return state.is(dev.tcgshop.shop.CleanBlocks.POOP) || floor ? InteractionResult.SUCCESS : super.useOn(context);
            }
            if (!(context.getPlayer() instanceof ServerPlayer sp)) return InteractionResult.PASS;
            if (state.is(dev.tcgshop.shop.CleanBlocks.POOP)) {
                state.useItemOn(context.getItemInHand(), w, sp, context.getHand(), new net.minecraft.world.phys.BlockHitResult(
                        context.getClickLocation(), context.getClickedFace(), pos, context.isInside()));
                return InteractionResult.SUCCESS;
            }
            if (context.getClickedFace() == net.minecraft.core.Direction.UP
                    && dev.tcgshop.shop.Cleanliness.mopFloor((ServerLevel) w, sp, pos, context.getItemInHand(), context.getHand()))
                return InteractionResult.SUCCESS;
            return super.useOn(context);
        }
        if (w.isClientSide) return InteractionResult.SUCCESS;
        if (context.getPlayer() instanceof ServerPlayer sp)
            IncidentModule.onPlayerUse((ServerLevel) w, pos, state, sp, context.getItemInHand(), context.getHand());
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag type) {
        tooltip.add(Component.translatable("item.tcgshop." + key + ".tip").withStyle(ChatFormatting.GRAY));
        int max = stack.getMaxDamage();
        if (max > 0) tooltip.add(Component.translatable("item.tcgshop.incident_tool.uses", max - stack.getDamageValue(), max).withStyle(ChatFormatting.DARK_AQUA));
    }
}

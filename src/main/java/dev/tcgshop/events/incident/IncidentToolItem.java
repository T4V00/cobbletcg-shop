package dev.tcgshop.events.incident;

import dev.tcgshop.shop.TcgBlocks;
import net.minecraft.block.BlockState;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;

/**
 * Tools for fixing incidents: tcgshop:mop (mop: puddles, droppings, mess) and tcgshop:repair_kit
 * (repair kit: sparks, scorch marks, broken showcases). On their own they do nothing: the incident block
 * checks the tag #tcgshop:fix/&lt;kind&gt; and takes durability from them.
 */
public class IncidentToolItem extends Item {
    private final String key;

    public IncidentToolItem(Settings settings, String key) {
        super(settings);
        this.key = key;
    }

    /** While sneaking, the game does not call the block: the tool fixes it anyway (same path as a normal click). */
    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        World w = context.getWorld();
        BlockPos pos = context.getBlockPos();
        BlockState state = w.getBlockState(pos);
        if (!state.isOf(TcgBlocks.INCIDENT)) return super.useOnBlock(context);
        if (w.isClient) return ActionResult.SUCCESS;
        if (context.getPlayer() instanceof ServerPlayerEntity sp)
            IncidentModule.onPlayerUse((ServerWorld) w, pos, state, sp, context.getStack(), context.getHand());
        return ActionResult.SUCCESS;
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("item.tcgshop." + key + ".tip").formatted(Formatting.GRAY));
        int max = stack.getMaxDamage();
        if (max > 0) tooltip.add(Text.translatable("item.tcgshop.incident_tool.uses", max - stack.getDamage(), max).formatted(Formatting.DARK_AQUA));
    }
}

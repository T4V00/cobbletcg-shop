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
 * Herramientas para arreglar incidentes: tcgshop:mop (fregona: charcos, excrementos, desorden) y tcgshop:repair_kit
 * (kit de reparación: chispas, quemaduras, vitrinas rotas). Por sí solas no hacen nada: el bloque de incidente
 * comprueba el tag #tcgshop:fix/&lt;kind&gt; y les quita durabilidad.
 */
public class IncidentToolItem extends Item {
    private final String key;

    public IncidentToolItem(Settings settings, String key) {
        super(settings);
        this.key = key;
    }

    /** Agachado, el juego no llama al bloque: la herramienta lo arregla igual (mismo camino que el clic normal). */
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

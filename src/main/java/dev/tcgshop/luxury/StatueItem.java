package dev.tcgshop.luxury;

import dev.tcgshop.pricing.PriceService;
import dev.tcgshop.shop.ShopZones;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;

import java.util.List;

/** Objeto de una estatua de lujo: nombre con el color de su nivel, nivel, tamaño, precio de catálogo y "no se revende". */
public class StatueItem extends BlockItem {
    public final StatueTier tier;

    public StatueItem(StatueBlock block, Settings settings) {
        super(block, settings);
        this.tier = block.tier;
    }

    @Override
    public Text getName(ItemStack stack) {
        MutableText t = Text.translatable(getTranslationKey()).withColor(tier.color);
        return tier == StatueTier.MASTERBALL ? t.formatted(Formatting.BOLD) : t;
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        int n = tier.index(), max = StatueTier.values().length;
        String stars = "★".repeat(n) + "☆".repeat(max - n);
        tooltip.add(Text.literal(stars + " ").withColor(tier.color)
                .append(Text.translatable("tcgshop.lux.tip_tier", n, max).formatted(Formatting.GRAY)));
        tooltip.add(Text.translatable("tcgshop.lux.flavor." + tier.key).formatted(Formatting.DARK_GRAY, Formatting.ITALIC));
        tooltip.add(Text.translatable("tcgshop.lux.tip_size", tier.heightBlocks()).formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("tcgshop.lux.tip_price", PriceService.format(tier.displayPrice())).formatted(Formatting.GOLD));
        tooltip.add(Text.translatable("tcgshop.lux.tip_no_resale").formatted(Formatting.RED));
        if (tier.light > 0) tooltip.add(Text.translatable("tcgshop.lux.tip_glow").formatted(Formatting.LIGHT_PURPLE));
    }

    @Override
    public ActionResult place(ItemPlacementContext ctx) {
        ActionResult r = super.place(ctx);
        if (r == ActionResult.FAIL && ctx.getPlayer() != null && !ctx.getWorld().isClient && tier.parts > 0) {
            // explicar el motivo real: zona ajena o falta de altura (si es una entidad en medio, sin mensaje, como vanilla)
            ShopZones.Zone z = StatueProtection.blockingStatue(ctx.getWorld(), ctx.getPlayer(), ctx.getBlockPos(), tier);
            if (z != null) StatueProtection.sendProtected(ctx.getPlayer(), z);
            else if (!StatueBlock.fitsAbove(ctx, tier.parts))
                ctx.getPlayer().sendMessage(Text.translatable("tcgshop.lux.no_space", tier.heightBlocks()).formatted(Formatting.RED), true);
        }
        return r;
    }
}

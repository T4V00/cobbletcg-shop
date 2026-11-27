package dev.tcgshop.luxury;

import dev.tcgshop.pricing.PriceService;
import dev.tcgshop.shop.ShopZones;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;

/** Item for a luxury statue: name in its tier color, tier, size, catalog price and "cannot be resold". */
public class StatueItem extends BlockItem {
    public final StatueTier tier;

    public StatueItem(StatueBlock block, Properties settings) {
        super(block, settings);
        this.tier = block.tier;
    }

    @Override
    public Component getName(ItemStack stack) {
        MutableComponent t = Component.translatable(getDescriptionId()).withColor(tier.color);
        return tier == StatueTier.MASTERBALL ? t.withStyle(ChatFormatting.BOLD) : t;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag type) {
        int n = tier.index(), max = StatueTier.values().length;
        String stars = "★".repeat(n) + "☆".repeat(max - n);
        tooltip.add(Component.literal(stars + " ").withColor(tier.color)
                .append(Component.translatable("tcgshop.lux.tip_tier", n, max).withStyle(ChatFormatting.GRAY)));
        tooltip.add(Component.translatable("tcgshop.lux.flavor." + tier.key).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("tcgshop.lux.tip_size", tier.heightBlocks()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tcgshop.lux.tip_price", PriceService.format(tier.displayPrice())).withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tcgshop.lux.tip_no_resale").withStyle(ChatFormatting.RED));
        if (tier.light > 0) tooltip.add(Component.translatable("tcgshop.lux.tip_glow").withStyle(ChatFormatting.LIGHT_PURPLE));
        // 1.1: in a shop it attracts customers and makes them buy more (the best statue of the shop counts)
        dev.tcgshop.EconomyConfig c = dev.tcgshop.EconomyConfig.INSTANCE;
        tooltip.add(Component.translatable("tcgshop.lux.tip_customers", Math.round(n * c.statueSpawnBonusPerTier * 100) + "%",
                Math.round(n * c.statueBuyBonusPerTier * 100) + "%").withStyle(ChatFormatting.AQUA));
    }

    @Override
    public InteractionResult place(BlockPlaceContext ctx) {
        InteractionResult r = super.place(ctx);
        if (r == InteractionResult.FAIL && ctx.getPlayer() != null && !ctx.getLevel().isClientSide && tier.parts > 0) {
            // explain the real reason: someone else's zone or not enough height (if an entity is in the way, no message, like vanilla)
            ShopZones.Zone z = StatueProtection.blockingStatue(ctx.getLevel(), ctx.getPlayer(), ctx.getClickedPos(), tier);
            if (z != null) StatueProtection.sendProtected(ctx.getPlayer(), z);
            else if (!StatueBlock.fitsAbove(ctx, tier.parts))
                ctx.getPlayer().displayClientMessage(Component.translatable("tcgshop.lux.no_space", tier.heightBlocks()).withStyle(ChatFormatting.RED), true);
        }
        return r;
    }
}

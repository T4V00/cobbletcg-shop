package dev.tcgshop.client;

import dev.masterball.api.platform.event.ClientEvents;
import dev.tcgshop.shop.DecorScore;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * 1.1 (customers update): every item that raises the shop's decoration liking says how much in its tooltip
 * (plushies, furniture, shelves and displays, posters, play tables, luxury statues...): its decoration points and about
 * how many percent it would add to the shop you are in (or your shop; the curve flattens, so the same object adds less
 * to an already well-decorated shop).
 */
public final class DecorTooltip {
    private DecorTooltip() {}

    /** A side for the "undecorated shop" estimate when you have no shop (a Wood shop). */
    private static final int DEFAULT_SIDE = 16;

    public static void register() {
        ClientEvents.TOOLTIP.register((stack, ctx, flag, lines) -> append(stack, lines));
    }

    private static void append(ItemStack stack, java.util.List<Component> lines) {
        int pts = DecorScore.points(stack);
        if (pts <= 0) return;
        Minecraft mc = Minecraft.getInstance();
        ShopClient.ZoneInfo zone = null;
        if (mc.player != null) {
            for (ShopClient.ZoneInfo z : ShopClient.zones) {
                if (!z.owner().equals(mc.player.getUUID()) || z.dormant()) continue;
                if (zone == null || z.box().contains(mc.player.position())) zone = z;
            }
        }
        lines.add(Component.translatable("tcgshop.decor.tooltip", pts).withStyle(ChatFormatting.LIGHT_PURPLE));
        if (zone != null) {
            double cur = zone.factors().getOrDefault("m_decor_pts", 0) / 10.0;
            // the server's k (decorDifficulty is a server setting) and the copies the shop already has (copy weight)
            double k = zone.ext().contains("decor_k") ? zone.ext().getDouble("decor_k") : DecorScore.k(zone.size());
            String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            int copies = zone.ext().getCompound("decor_copies").getInt(id);
            double now = DecorScore.pctExactK(cur, k), gain = DecorScore.pctExactK(cur + DecorScore.addedPoints(pts, copies), k) - now;
            lines.add(Component.translatable("tcgshop.decor.tooltip_shop", pct(gain), Math.round(now) + "%").withStyle(ChatFormatting.DARK_PURPLE));
        } else {
            lines.add(Component.translatable("tcgshop.decor.tooltip_empty", pct(DecorScore.pctExact(pts, DEFAULT_SIDE))).withStyle(ChatFormatting.DARK_PURPLE));
        }
    }

    private static String pct(double v) {
        return String.format(Locale.ROOT, "+%.1f%%", Math.max(0, v));
    }
}

package dev.tcgshop.events.effect;

import com.google.gson.JsonObject;
import dev.tcgshop.events.EventContext;
import dev.tcgshop.events.EventDef;
import dev.tcgshop.events.EventEffect;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * "metric": the event is fixed when the shop meets a measurable goal.
 * Checked on every recompute (every minute) and must hold for count CONSECUTIVE recomputes (if one fails, it
 * resets to 0, so lowering prices for a moment and raising them afterwards does not work):
 *  · price_ok      reputation price factor &gt;= 0 (reasonable prices)
 *  · smell_clean   shop smell &lt;= 5 and cleanliness score &gt;= 80 (1.1: droppings, graffiti, grime)
 *  · lit           the shop is not dark (no "dark" flag)
 *  · no_hostiles   no monsters nearby
 *  · stocked       no "empty" flag
 *  · guarded       guard hired or at least 2 theft detectors
 * By sales (they add up):
 *  · fair_sales    sales to NPCs at market price or less
 */
public final class MetricEffect implements EventEffect {
    static final List<String> METRICS = List.of("price_ok", "smell_clean", "lit", "no_hostiles", "stocked", "guarded", "fair_sales");
    final String metric;
    final int count;

    public MetricEffect(JsonObject o, String defId) {
        metric = EventDef.str(o, "metric", "");
        count = Math.max(1, EventDef.integer(o, "count", metric.equals("fair_sales") ? 5 : 2));
        if (!METRICS.contains(metric)) throw new IllegalArgumentException("unknown metric: " + metric);
    }

    @Override
    public void onRecompute(EventContext ctx) {
        if (ctx.instance.resolved() || metric.equals("fair_sales")) return;
        boolean ok = switch (metric) {
            case "price_ok" -> ctx.zone.factors.getOrDefault("price", 0) >= 0;
            case "smell_clean" -> ctx.zone.smell <= 5 && dev.tcgshop.shop.Cleanliness.score(ctx.zone) >= 80;
            case "lit" -> !ctx.zone.flags.contains("dark");
            case "no_hostiles" -> !ctx.zone.flags.contains("hostile");
            case "stocked" -> !ctx.zone.flags.contains("empty");
            case "guarded" -> ctx.zone.guard || ctx.zone.detectors >= 2;
            default -> false;
        };
        int n = ok ? ctx.data().getInt("n") + 1 : 0;
        ctx.data().putInt("n", n);
        ctx.markDirty();
        if (ok && n < count) ctx.tellOwner(Component.translatable("tcgshop.event.metric_progress", n, count).withStyle(ChatFormatting.YELLOW), true);
        if (n >= count) ctx.resolve("metric");
    }

    @Override
    public void onSale(EventContext ctx, ItemStack item, long price, long market) {
        if (ctx.instance.resolved() || !metric.equals("fair_sales") || market <= 0 || price > market) return;
        int n = ctx.data().getInt("n") + 1;
        ctx.data().putInt("n", n);
        ctx.markDirty();
        if (n >= count) ctx.resolve("metric");
        else ctx.tellOwner(Component.translatable("tcgshop.event.metric_progress", n, count).withStyle(ChatFormatting.YELLOW), true);
    }

    @Override
    public boolean providesFix() { return true; }

    /**
     * Does not start already "fixed": goals that the event itself does not break require that they are NOT met today
     * (otherwise it would resolve itself on the next recompute, wasting the day's event).
     */
    @Override
    public java.util.Set<String> implicitRequires() {
        return switch (metric) {
            case "guarded" -> java.util.Set.of("unguarded");
            case "price_ok" -> java.util.Set.of("overpriced");
            default -> java.util.Set.of();
        };
    }
}

package dev.tcgshop.events.effect;

import com.google.gson.JsonObject;
import dev.tcgshop.events.EventContext;
import dev.tcgshop.events.EventDef;
import dev.tcgshop.events.EventEffect;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * "metric": el evento se arregla cuando la tienda cumple un objetivo medible.
 * Comprobados en cada recálculo (cada minuto) y que deben mantenerse count recálculos SEGUIDOS (si falla uno, se
 * vuelve a 0, así no vale bajar precios un momento y subirlos después):
 *  · price_ok      factor de precio de la reputación &gt;= 0 (precios razonables)
 *  · smell_clean   olor de la tienda &lt;= 5
 *  · lit           la tienda no está oscura (sin flag "dark")
 *  · no_hostiles   sin monstruos cerca
 *  · stocked       sin flag "empty"
 *  · guarded       guardia contratado o al menos 2 detectores de robo
 * Por ventas (se suman):
 *  · fair_sales    ventas a NPC a precio de mercado o menos
 */
public final class MetricEffect implements EventEffect {
    static final List<String> METRICS = List.of("price_ok", "smell_clean", "lit", "no_hostiles", "stocked", "guarded", "fair_sales");
    final String metric;
    final int count;

    public MetricEffect(JsonObject o, String defId) {
        metric = EventDef.str(o, "metric", "");
        count = Math.max(1, EventDef.integer(o, "count", metric.equals("fair_sales") ? 5 : 2));
        if (!METRICS.contains(metric)) throw new IllegalArgumentException("metric desconocida: " + metric);
    }

    @Override
    public void onRecompute(EventContext ctx) {
        if (ctx.instance.resolved() || metric.equals("fair_sales")) return;
        boolean ok = switch (metric) {
            case "price_ok" -> ctx.zone.factors.getOrDefault("price", 0) >= 0;
            case "smell_clean" -> ctx.zone.smell <= 5;
            case "lit" -> !ctx.zone.flags.contains("dark");
            case "no_hostiles" -> !ctx.zone.flags.contains("hostile");
            case "stocked" -> !ctx.zone.flags.contains("empty");
            case "guarded" -> ctx.zone.guard || ctx.zone.detectors >= 2;
            default -> false;
        };
        int n = ok ? ctx.data().getInt("n") + 1 : 0;
        ctx.data().putInt("n", n);
        ctx.markDirty();
        if (ok && n < count) ctx.tellOwner(Text.translatable("tcgshop.event.metric_progress", n, count).formatted(Formatting.YELLOW), true);
        if (n >= count) ctx.resolve("metric");
    }

    @Override
    public void onSale(EventContext ctx, ItemStack item, long price, long market) {
        if (ctx.instance.resolved() || !metric.equals("fair_sales") || market <= 0 || price > market) return;
        int n = ctx.data().getInt("n") + 1;
        ctx.data().putInt("n", n);
        ctx.markDirty();
        if (n >= count) ctx.resolve("metric");
        else ctx.tellOwner(Text.translatable("tcgshop.event.metric_progress", n, count).formatted(Formatting.YELLOW), true);
    }

    @Override
    public boolean providesFix() { return true; }

    /**
     * No sale ya "arreglado": los objetivos que el propio evento no estropea exigen que hoy NO se cumplan
     * (si no, se resolvería solo en el siguiente recálculo gastando el evento del día).
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

package dev.tcgshop.events.effect;

import com.google.gson.JsonObject;
import dev.tcgshop.events.EventContext;
import dev.tcgshop.events.EventDef;
import dev.tcgshop.events.EventEffect;
import dev.tcgshop.events.EventManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.Set;

/**
 * P5 "supplier" (ligero, sin sistema de pedidos nuevo):
 *  · perk: "wholesale_discount" (factor &lt; 1 en la próxima compra al Mayorista o en el teléfono), "phone_markup"
 *    (factor &gt; 1 en el teléfono mientras dure: "retraso del proveedor"), "grading_discount" (próximo envío a
 *    CobbleGrade), "hire_discount" (próxima contratación). Valen mientras dura el evento (uses = cuántas compras).
 *  · spawn: "wholesaler" o "black_market" junto al dueño (los mismos que llama el teléfono).
 *  · speedup: fracción (0-0.9) del tiempo que le queda a UN pedido pendiente de CobbleGrade.
 */
public final class SupplierEffect implements EventEffect {
    static final List<String> PERKS = List.of("", "wholesale_discount", "phone_markup", "grading_discount", "hire_discount");
    final String perk, spawn;
    final double factor, speedup;
    final int uses;

    public SupplierEffect(JsonObject o, String defId) {
        perk = EventDef.str(o, "perk", "");
        spawn = EventDef.str(o, "spawn", "");
        factor = EventDef.dbl(o, "factor", 1);
        speedup = EventDef.clamp(EventDef.dbl(o, "speedup", 0), 0, 0.9);
        uses = Math.max(1, EventDef.integer(o, "uses", 1));
        if (!PERKS.contains(perk)) throw new IllegalArgumentException("supplier.perk desconocido: " + perk);
        if (!List.of("", "wholesaler", "black_market").contains(spawn)) throw new IllegalArgumentException("supplier.spawn desconocido: " + spawn);
        if (perk.equals("phone_markup") ? factor < 1 || factor > 1.5 : !perk.isEmpty() && (factor <= 0.3 || factor > 1))
            throw new IllegalArgumentException("supplier.factor fuera de rango");
    }

    @Override
    public void start(EventContext ctx) {
        if (!perk.isEmpty()) EventManager.addPerk(ctx.world.getServer(), ctx.zone.owner, ctx.instance.uid, perk, factor,
                perk.equals("phone_markup") ? 9999 : uses, ctx.instance.end);
        ServerPlayerEntity owner = ctx.owner();
        if (owner != null && !spawn.isEmpty()) {
            boolean ok = spawn.equals("wholesaler") ? dev.tcgshop.phone.PhoneUpgrades.spawnWholesalerNear(owner)
                    : dev.tcgshop.phone.PhoneUpgrades.spawnBlackMarketNear(owner);
            if (!ok) owner.sendMessage(Text.translatable("tcgshop.event.supplier.no_room").formatted(Formatting.GRAY), true);
        }
        if (speedup > 0) {
            boolean ok = dev.tcgshop.grading.GradingService.speedUpOne(ctx.world.getServer(), ctx.zone.owner, speedup);
            ctx.tellOwner(Text.translatable(ok ? "tcgshop.event.supplier.grading_faster" : "tcgshop.event.supplier.grading_none")
                    .formatted(ok ? Formatting.GREEN : Formatting.GRAY), false);
        }
    }

    @Override
    public Set<String> implicitRequires() { return speedup > 0 ? Set.of("grading_order") : Set.of(); }
}

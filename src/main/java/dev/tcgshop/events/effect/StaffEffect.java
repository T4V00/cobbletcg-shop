package dev.tcgshop.events.effect;

import com.google.gson.JsonObject;
import dev.tcgshop.entity.EmployeeEntity;
import dev.tcgshop.events.EventContext;
import dev.tcgshop.events.EventDef;
import dev.tcgshop.events.EventEffect;
import dev.tcgshop.events.EventInstance;
import dev.tcgshop.events.Mods;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Set;

/**
 * P7 "staff": the cashier checks out slower (serviceMult &gt; 1) or faster (&lt; 1). With "distracted": true they look
 * distracted (music notes and lines) and it is fixed by the owner right-clicking the cashier (EventManager.onEmployeeInteract).
 */
public final class StaffEffect implements EventEffect {
    final double serviceMult;
    final boolean distracted;
    final String lines;
    final int linesCount;

    public StaffEffect(JsonObject o, String defId) {
        serviceMult = EventDef.dbl(o, "serviceMult", 1);
        distracted = EventDef.bool(o, "distracted", false);
        lines = EventDef.str(o, "lines", distracted ? "distracted" : "");
        linesCount = EventDef.integer(o, "count", lines.isEmpty() ? 0 : 6);
    }

    public boolean distracted() { return distracted; }

    @Override
    public void contribute(Mods.Builder b, EventInstance inst, long now) {
        if (distracted && inst.resolved()) return;
        b.serviceMult *= serviceMult;
    }

    @Override
    public void tick(EventContext ctx) {
        if (ctx.instance.resolved() || (ctx.now() % 200) >= 20) return;
        List<EmployeeEntity> staff = ctx.world.getEntitiesByClass(EmployeeEntity.class, ctx.zone.box().expand(8),
                e -> ctx.zone.id.equals(e.zoneId()) && !e.isGuard());
        for (EmployeeEntity e : staff) {
            if (distracted) ctx.world.spawnParticles(ParticleTypes.NOTE, e.getX(), e.getY() + 2.3, e.getZ(), 2, 0.3, 0.2, 0.3, 1);
            else ctx.world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, e.getX(), e.getY() + 2.1, e.getZ(), 2, 0.3, 0.2, 0.3, 0);
            if (linesCount > 0 && ctx.rng.nextFloat() < 0.35f) e.announce(Text.translatable("tcgshop.evline." + lines + "." + ctx.rng.nextInt(linesCount)));
        }
    }

    @Override
    public boolean providesFix() { return distracted; }

    @Override
    public Set<String> implicitRequires() { return Set.of("cashier"); }
}

package dev.tcgshop.events.effect;

import com.google.gson.JsonObject;
import dev.tcgshop.events.EventContext;
import dev.tcgshop.events.EventDef;
import dev.tcgshop.events.EventEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * "notify": extra notices to the owner (chat message at start, only to them) and particles around the shop while it lasts
 * (so it is VISIBLE that something is happening). Customer lines go in the definition's "notify" (EventDef.Notify).
 * particles: happy, note, heart, smoke, angry, glow, crit, cloud.
 */
public final class NotifyEffect implements EventEffect {
    final String message;
    final String particles;
    final boolean whileUnresolved;

    public NotifyEffect(JsonObject o, String defId) {
        message = EventDef.str(o, "message", "");
        particles = EventDef.str(o, "particles", "");
        whileUnresolved = EventDef.bool(o, "whileUnresolved", true);
        if (!particles.isEmpty() && particle(particles) == null) throw new IllegalArgumentException("unknown notify.particles: " + particles);
    }

    private static ParticleEffect particle(String k) {
        return switch (k) {
            case "happy" -> ParticleTypes.HAPPY_VILLAGER;
            case "note" -> ParticleTypes.NOTE;
            case "heart" -> ParticleTypes.HEART;
            case "smoke" -> ParticleTypes.SMOKE;
            case "angry" -> ParticleTypes.ANGRY_VILLAGER;
            case "glow" -> ParticleTypes.GLOW;
            case "crit" -> ParticleTypes.ENCHANTED_HIT;
            case "cloud" -> ParticleTypes.CLOUD;
            default -> null;
        };
    }

    @Override
    public void start(EventContext ctx) {
        if (!message.isEmpty()) ctx.tellOwner(Text.translatable(message).formatted(Formatting.GRAY), false);
    }

    @Override
    public void tick(EventContext ctx) {
        if (particles.isEmpty() || (whileUnresolved && ctx.instance.resolved()) || ctx.now() % 40 >= 20) return;
        ParticleEffect p = particle(particles);
        int half = ctx.zone.tierInfo().size / 2;
        for (int i = 0; i < 2; i++) {
            double x = ctx.zone.core.getX() + ctx.rng.nextInt(half * 2 + 1) - half + 0.5, z = ctx.zone.core.getZ() + ctx.rng.nextInt(half * 2 + 1) - half + 0.5;
            ctx.world.spawnParticles(p, x, ctx.zone.core.getY() + 1.5, z, 3, 0.4, 0.4, 0.4, 0);
        }
    }
}

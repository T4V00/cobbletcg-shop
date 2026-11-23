package dev.tcgshop.events.incident;

import dev.tcgshop.events.EventManager;
import dev.tcgshop.shop.TcgBlocks;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import org.joml.Vector3f;

import java.util.UUID;

/**
 * Data of a placed incident: event (uid), shop, effect index in the definition, hits remaining and absolute
 * end. Every 40 ticks: cheap particles/sound, and if its event is no longer active and unfixed (or its end has passed),
 * it removes itself (no orphaned junk after crashes, deleted events or broken shops).
 */
public class IncidentBlockEntity extends BlockEntity {
    long eventUid = -1;
    UUID zoneId;
    int effectIndex = -1;
    int hitsLeft = 1;
    long expiresAt = -1;
    private int age;

    private static final DustParticleEffect STINK = new DustParticleEffect(new Vector3f(0.45f, 0.62f, 0.2f), 1.0f);
    private static final DustParticleEffect DUST = new DustParticleEffect(new Vector3f(0.55f, 0.5f, 0.45f), 1.2f);

    public IncidentBlockEntity(BlockPos pos, BlockState state) {
        super(TcgBlocks.INCIDENT_BE, pos, state);
    }

    void init(long uid, UUID zone, int index, int hits, long expires) {
        eventUid = uid;
        zoneId = zone;
        effectIndex = index;
        hitsLeft = Math.max(1, hits);
        expiresAt = expires;
        markDirty();
    }

    public long eventUid() { return eventUid; }

    public int hitsLeft() { return hitsLeft; }

    /** Is it still needed? (event active and unfixed, and its end has not passed) */
    boolean stillWanted(ServerWorld world) {
        if (EventManager.server() == null) return true; // starting up: don't decide yet
        if (eventUid < 0) return false;
        // the live end of the instance (a visitor can extend the event); the saved one only if it no longer exists
        dev.tcgshop.events.EventInstance inst = EventManager.instance(eventUid);
        if (inst != null && inst.end != expiresAt) {
            expiresAt = inst.end;
            markDirty();
        }
        if (expiresAt > 0 && world.getTime() > expiresAt + 200) return false;
        return inst != null && !inst.resolved();
    }

    void serverTick(ServerWorld world, BlockState state) {
        age++;
        if ((world.getTime() + (pos.asLong() & 31)) % 40 != 0) return;
        if (age >= 40 && !stillWanted(world)) {
            IncidentModule.removeSilently(world, pos);
            return;
        }
        effects(world, state.get(IncidentBlock.KIND));
    }

    /** Particles and the occasional sound (every 40 ticks, only if players are nearby to save resources). */
    private void effects(ServerWorld w, IncidentKind kind) {
        if (w.getClosestPlayer(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 32, false) == null) return;
        double x = pos.getX() + 0.5, y = pos.getY() + 0.1, z = pos.getZ() + 0.5;
        var r = w.random;
        switch (kind) {
            case PUDDLE -> {
                w.spawnParticles(ParticleTypes.SPLASH, x, y, z, 3, 0.35, 0.02, 0.35, 0.05);
                if (r.nextInt(4) == 0) w.playSound(null, pos, SoundEvents.BLOCK_POINTED_DRIPSTONE_DRIP_WATER, SoundCategory.BLOCKS, 0.6f, 0.9f + r.nextFloat() * 0.3f);
            }
            case SPARKS -> {
                w.spawnParticles(ParticleTypes.ELECTRIC_SPARK, x, y + 0.2, z, 5, 0.25, 0.2, 0.25, 0.15);
                if (r.nextInt(3) == 0) w.spawnParticles(ParticleTypes.SMOKE, x, y + 0.1, z, 1, 0.1, 0.05, 0.1, 0.01);
                if (r.nextInt(3) == 0) w.playSound(null, pos, SoundEvents.BLOCK_REDSTONE_TORCH_BURNOUT, SoundCategory.BLOCKS, 0.35f, 1.6f + r.nextFloat() * 0.3f);
            }
            case SCORCH -> w.spawnParticles(ParticleTypes.SMOKE, x, y, z, 2, 0.3, 0.02, 0.3, 0.01);
            case DROPPINGS -> w.spawnParticles(STINK, x, y + 0.2, z, 2, 0.25, 0.15, 0.25, 0);
            case DIRT, DEBRIS -> {
                if (r.nextInt(2) == 0) w.spawnParticles(DUST, x, y + 0.1, z, 2, 0.3, 0.05, 0.3, 0);
            }
            case PACKAGE -> {
                if (r.nextInt(2) == 0) w.spawnParticles(ParticleTypes.HAPPY_VILLAGER, x, y + 0.6, z, 1, 0.2, 0.1, 0.2, 0);
            }
            default -> {}
        }
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        super.writeNbt(nbt, lookup);
        nbt.putLong("uid", eventUid);
        if (zoneId != null) nbt.putUuid("zone", zoneId);
        nbt.putInt("idx", effectIndex);
        nbt.putInt("hits", hitsLeft);
        nbt.putLong("exp", expiresAt);
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        super.readNbt(nbt, lookup);
        eventUid = nbt.contains("uid") ? nbt.getLong("uid") : -1;
        zoneId = nbt.containsUuid("zone") ? nbt.getUuid("zone") : null;
        effectIndex = nbt.contains("idx") ? nbt.getInt("idx") : -1;
        hitsLeft = Math.max(1, nbt.getInt("hits"));
        expiresAt = nbt.contains("exp") ? nbt.getLong("exp") : -1;
    }
}

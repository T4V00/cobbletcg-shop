package dev.tcgshop.events;

import dev.tcgshop.shop.ShopZones;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.util.Random;

/**
 * Lo que recibe cada efecto en sus llamadas: mundo, zona, instancia, definición, índice del efecto y un Random
 * sembrado por (uid del evento, índice, llamada), así repetir no cambia lo que sale.
 */
public final class EventContext {
    public final ServerWorld world;
    public final ShopZones.Zone zone;
    public final EventInstance instance;
    public final EventDef def;
    public final int index;
    public final Random rng;

    EventContext(ServerWorld world, ShopZones.Zone zone, EventInstance instance, EventDef def, int index, long salt) {
        this.world = world;
        this.zone = zone;
        this.instance = instance;
        this.def = def;
        this.index = index;
        this.rng = new Random(instance.uid * 0x9E3779B97F4A7C15L ^ index * 31L ^ salt);
    }

    /** Datos persistentes de ESTE efecto en ESTA instancia (se guardan con la zona). */
    public NbtCompound data() {
        String k = "e" + index;
        if (!instance.data.contains(k)) instance.data.put(k, new NbtCompound());
        return instance.data.getCompound(k);
    }

    public long now() { return world.getTime(); }

    public long day() { return world.getTimeOfDay() / 24000; }

    @Nullable
    public ServerPlayerEntity owner() { return world.getServer().getPlayerManager().getPlayer(zone.owner); }

    /** Marca el evento como arreglado (llama a resolve de todos sus efectos, avisa al dueño y lo apunta en el log). */
    public boolean resolve(String how) { return EventManager.resolve(instance.uid, how); }

    /** Tras cambiar bloqueos o datos que afectan a los modificadores. */
    public void invalidate() { EventManager.invalidate(zone); }

    public void markDirty() { ShopZones.get(world).markDirty(); }

    /** Mensaje sólo para el dueño (barra de acción si actionBar). */
    public void tellOwner(Text msg, boolean actionBar) {
        ServerPlayerEntity p = owner();
        if (p != null) p.sendMessage(msg, actionBar);
    }
}

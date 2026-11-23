package dev.tcgshop.events;

import dev.tcgshop.shop.ShopZones;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.util.Random;

/**
 * What each effect receives in its calls: world, zone, instance, definition, effect index and a Random
 * seeded by (event uid, index, call), so repeating does not change the outcome.
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

    /** Persistent data of THIS effect in THIS instance (saved with the zone). */
    public NbtCompound data() {
        String k = "e" + index;
        if (!instance.data.contains(k)) instance.data.put(k, new NbtCompound());
        return instance.data.getCompound(k);
    }

    public long now() { return world.getTime(); }

    public long day() { return world.getTimeOfDay() / 24000; }

    @Nullable
    public ServerPlayerEntity owner() { return world.getServer().getPlayerManager().getPlayer(zone.owner); }

    /** Marks the event as fixed (calls resolve on all its effects, notifies the owner and records it in the log). */
    public boolean resolve(String how) { return EventManager.resolve(instance.uid, how); }

    /** After changing locks or data that affect the modifiers. */
    public void invalidate() { EventManager.invalidate(zone); }

    public void markDirty() { ShopZones.get(world).markDirty(); }

    /** Message for the owner only (action bar if actionBar). */
    public void tellOwner(Text msg, boolean actionBar) {
        ServerPlayerEntity p = owner();
        if (p != null) p.sendMessage(msg, actionBar);
    }
}

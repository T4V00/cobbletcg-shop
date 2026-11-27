package dev.tcgshop.events;

import dev.tcgshop.shop.ShopZones;
import org.jetbrains.annotations.Nullable;

import java.util.Random;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * What each effect receives in its calls: world, zone, instance, definition, effect index and a Random
 * seeded by (event uid, index, call), so repeating does not change the outcome.
 */
public final class EventContext {
    public final ServerLevel world;
    public final ShopZones.Zone zone;
    public final EventInstance instance;
    public final EventDef def;
    public final int index;
    public final Random rng;

    EventContext(ServerLevel world, ShopZones.Zone zone, EventInstance instance, EventDef def, int index, long salt) {
        this.world = world;
        this.zone = zone;
        this.instance = instance;
        this.def = def;
        this.index = index;
        this.rng = new Random(instance.uid * 0x9E3779B97F4A7C15L ^ index * 31L ^ salt);
    }

    /** Persistent data of THIS effect in THIS instance (saved with the zone). */
    public CompoundTag data() {
        String k = "e" + index;
        if (!instance.data.contains(k)) instance.data.put(k, new CompoundTag());
        return instance.data.getCompound(k);
    }

    public long now() { return world.getGameTime(); }

    public long day() { return world.getDayTime() / 24000; }

    @Nullable
    public ServerPlayer owner() { return world.getServer().getPlayerList().getPlayer(zone.owner); }

    /** Marks the event as fixed (calls resolve on all its effects, notifies the owner and records it in the log). */
    public boolean resolve(String how) { return EventManager.resolve(instance.uid, how); }

    /** After changing locks or data that affect the modifiers. */
    public void invalidate() { EventManager.invalidate(zone); }

    public void markDirty() { ShopZones.get(world).setDirty(); }

    /** Message for the owner only (action bar if actionBar). */
    public void tellOwner(Component msg, boolean actionBar) {
        ServerPlayer p = owner();
        if (p != null) p.displayClientMessage(msg, actionBar);
    }
}

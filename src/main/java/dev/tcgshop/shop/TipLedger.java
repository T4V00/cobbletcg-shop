package dev.tcgshop.shop;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.ToDoubleBiFunction;
import java.util.function.ToDoubleFunction;

/**
 * 1.0 tips: whatever a customer pays above the owner's asking price. This world state keeps, per shop owner,
 * the total tipped, how many tips, the biggest one and today's tips (for the Stats app and missions later).
 * Tips themselves go into the shop's till together with the sale (ShopZones.Zone balance/revenue).
 */
public final class TipLedger extends PersistentState {
    private static final Type<TipLedger> TYPE = new Type<>(TipLedger::new, TipLedger::fromNbt, null);

    /** Tip record of one owner. */
    public static final class Entry {
        public long total, best, today;
        public int count;
        /** In-game day of {@link #today} (-1 = none yet). */
        public long day = -1;
    }

    /**
     * Extra chance (0-1) that a happy customer leaves a tip at checkout, per owner. 0 by default; ShopMissions.init
     * replaces it with the missions' luck (ShopMissions.ownerTipBonus: up to +8 points with every mission done).
     */
    public static ToDoubleBiFunction<MinecraftServer, UUID> chanceBonus = (server, owner) -> 0.0;

    /**
     * Zone-aware version of {@link #chanceBonus}: the extra tip chance of the shop where the sale happens. 0 by default;
     * ShopMissions.init replaces it with ShopMissions.tipBonus (that shop's missions, not the owner's best shop).
     */
    public static ToDoubleFunction<ShopZones.Zone> zoneChanceBonus = z -> 0.0;

    /** Extra checkout tip chance (0-1) in that shop (0 for a null zone). */
    public static double chanceBonusAt(ShopZones.Zone z) {
        return z == null ? 0 : Math.max(0, zoneChanceBonus.applyAsDouble(z));
    }

    private final Map<UUID, Entry> owners = new HashMap<>();

    public static TipLedger get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE, "tcgshop_tips");
    }

    /** Records a tip for that owner (tip <= 0 is ignored). */
    public static void record(MinecraftServer server, UUID owner, long tip, long day) {
        if (server == null || owner == null || tip <= 0) return;
        TipLedger l = get(server);
        Entry e = l.owners.computeIfAbsent(owner, u -> new Entry());
        e.total += tip;
        e.count++;
        e.best = Math.max(e.best, tip);
        if (e.day != day) { e.day = day; e.today = 0; }
        e.today += tip;
        l.markDirty();
        Charisma.onTip(server, owner); // 1.0: tips make the owner more charismatic
    }

    /** That owner's tip record (an empty one if they never got a tip; do not modify). */
    public static Entry of(MinecraftServer server, UUID owner) {
        Entry e = server == null || owner == null ? null : get(server).owners.get(owner);
        return e != null ? e : new Entry();
    }

    /** Tips received today (in-game day). */
    public static long today(MinecraftServer server, UUID owner, long day) {
        Entry e = of(server, owner);
        return e.day == day ? e.today : 0;
    }

    private static TipLedger fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        TipLedger s = new TipLedger();
        NbtCompound o = nbt.getCompound("owners");
        for (String k : o.getKeys()) {
            try {
                NbtCompound c = o.getCompound(k);
                Entry e = new Entry();
                e.total = c.getLong("total");
                e.count = c.getInt("count");
                e.best = c.getLong("best");
                e.day = c.contains("day") ? c.getLong("day") : -1;
                e.today = c.getLong("today");
                s.owners.put(UUID.fromString(k), e);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return s;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        NbtCompound o = new NbtCompound();
        for (Map.Entry<UUID, Entry> me : owners.entrySet()) {
            Entry e = me.getValue();
            NbtCompound c = new NbtCompound();
            c.putLong("total", e.total);
            c.putInt("count", e.count);
            c.putLong("best", e.best);
            c.putLong("day", e.day);
            c.putLong("today", e.today);
            o.put(me.getKey().toString(), c);
        }
        nbt.put("owners", o);
        return nbt;
    }
}

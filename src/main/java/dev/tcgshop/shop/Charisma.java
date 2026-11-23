package dev.tcgshop.shop;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.PersistentState;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 1.0 Charisma: a per-player level (1..{@link #MAX_LEVEL}) that makes haggling more effective. It grows with XP from
 * haggles won, closed deals and tips received. {@link #haggleBonus} (0 .. EconomyConfig.charismaMaxHaggleBonus, 0.25
 * by default) is used by CustomerEntity: customers concede more per round, take offense later and accept a price
 * closer to yours.
 *
 * Stored per player in the overworld PersistentState "tcgshop_charisma" (works for offline players). The last loaded
 * instance is also kept in {@link #active} so server code without a MinecraftServer at hand (the zone sync) can read it.
 */
public final class Charisma extends PersistentState {
    private static final Type<Charisma> TYPE = new Type<>(Charisma::new, Charisma::fromNbt, null);

    public static final int MAX_LEVEL = 20;

    /** One player's charisma record. */
    public static final class Entry {
        public long xp;
        public int hagglesWon, deals, tips;
    }

    private final Map<UUID, Entry> players = new HashMap<>();

    /** Last instance loaded by {@link #get} (server thread only; cleared when the server stops). */
    @Nullable
    private static Charisma active;

    public static Charisma get(MinecraftServer server) {
        Charisma c = server.getOverworld().getPersistentStateManager().getOrCreate(TYPE, "tcgshop_charisma");
        active = c;
        return c;
    }

    /** Called when the server stops (ShopMissions.init registers it): no state leaks into the next world. */
    static void clearActive() { active = null; }

    // ------------------------------------------------------------------ levels

    /** Total XP needed to reach a level (level 1 = 0 XP; level 20 = 3,363 XP). */
    public static long xpForLevel(int level) {
        int l = Math.max(1, Math.min(MAX_LEVEL, level)) - 1;
        return 25L * l + 8L * l * l;
    }

    public static int levelForXp(long xp) {
        int lv = 1;
        while (lv < MAX_LEVEL && xp >= xpForLevel(lv + 1)) lv++;
        return lv;
    }

    /** Charisma level 1..20 of that player (1 if they never haggled). */
    public static int level(MinecraftServer server, UUID player) {
        if (server == null || player == null) return 1;
        Entry e = get(server).players.get(player);
        return e == null ? 1 : levelForXp(e.xp);
    }

    /** Haggling bonus (0 at level 1 .. EconomyConfig.charismaMaxHaggleBonus at level 20, ~0.25). */
    public static double haggleBonus(MinecraftServer server, UUID player) {
        return bonusForLevel(level(server, player));
    }

    public static double bonusForLevel(int level) {
        double max = Math.max(0, Math.min(0.5, dev.tcgshop.EconomyConfig.INSTANCE.charismaMaxHaggleBonus));
        return max * (Math.max(1, Math.min(MAX_LEVEL, level)) - 1) / (double) (MAX_LEVEL - 1);
    }

    /** That player's record (an empty one if none; do not modify). */
    public static Entry of(MinecraftServer server, UUID player) {
        Entry e = server == null || player == null ? null : get(server).players.get(player);
        return e != null ? e : new Entry();
    }

    /** Same as {@link #of} but from the last loaded instance (no server needed; empty if not loaded yet). */
    public static Entry peek(UUID player) {
        Charisma c = active;
        Entry e = c == null || player == null ? null : c.players.get(player);
        return e != null ? e : new Entry();
    }

    // ------------------------------------------------------------------ XP sources

    /** A deal was closed with a customer (accepted by both); won = you haggled them to a better price than their opening one. */
    public static void onDeal(MinecraftServer server, UUID player, boolean won) {
        if (server == null || player == null) return;
        dev.tcgshop.EconomyConfig cfg = dev.tcgshop.EconomyConfig.INSTANCE;
        Charisma c = get(server);
        Entry e = c.players.computeIfAbsent(player, u -> new Entry());
        e.deals++;
        if (won) e.hagglesWon++;
        c.markDirty();
        addXp(server, player, Math.max(0, cfg.charismaXpDeal) + (won ? Math.max(0, cfg.charismaXpHaggle) : 0));
    }

    /** A customer left a tip (TipLedger.record). */
    public static void onTip(MinecraftServer server, UUID player) {
        if (server == null || player == null) return;
        Charisma c = get(server);
        Entry e = c.players.computeIfAbsent(player, u -> new Entry());
        e.tips++;
        c.markDirty();
        addXp(server, player, Math.max(0, dev.tcgshop.EconomyConfig.INSTANCE.charismaXpTip));
    }

    /** Adds XP; on level up the player hears it, gets a chat line and, every 5 levels, a phone message. */
    public static void addXp(MinecraftServer server, UUID player, long amount) {
        if (server == null || player == null || amount <= 0) return;
        Charisma c = get(server);
        Entry e = c.players.computeIfAbsent(player, u -> new Entry());
        int before = levelForXp(e.xp);
        e.xp = Math.min(Long.MAX_VALUE / 4, e.xp + amount);
        int after = levelForXp(e.xp);
        c.markDirty();
        if (after <= before) return;
        int pct = (int) Math.round(bonusForLevel(after) * 100);
        ServerPlayerEntity p = server.getPlayerManager().getPlayer(player);
        if (p != null) {
            p.sendMessage(Text.translatable("tcgshop.charisma.level_up", after, MAX_LEVEL, pct + "%").formatted(Formatting.LIGHT_PURPLE), false);
            p.getServerWorld().playSound(null, p.getBlockPos(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.6f, 1.7f);
        }
        if (after % 5 == 0 || after == MAX_LEVEL)
            dev.tcgshop.phone.PhoneMessages.send(server, player, "tcgshop.msg.sender.system",
                    Text.translatable("tcgshop.charisma.msg", after, pct + "%"), false);
    }

    // ------------------------------------------------------------------ NBT

    private static Charisma fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        Charisma s = new Charisma();
        NbtCompound o = nbt.getCompound("players");
        for (String k : o.getKeys()) {
            try {
                NbtCompound c = o.getCompound(k);
                Entry e = new Entry();
                e.xp = Math.max(0, c.getLong("xp"));
                e.hagglesWon = c.getInt("haggles_won");
                e.deals = c.getInt("deals");
                e.tips = c.getInt("tips");
                s.players.put(UUID.fromString(k), e);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return s;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        NbtCompound o = new NbtCompound();
        for (Map.Entry<UUID, Entry> me : players.entrySet()) {
            Entry e = me.getValue();
            NbtCompound c = new NbtCompound();
            c.putLong("xp", e.xp);
            c.putInt("haggles_won", e.hagglesWon);
            c.putInt("deals", e.deals);
            c.putInt("tips", e.tips);
            o.put(me.getKey().toString(), c);
        }
        nbt.put("players", o);
        return nbt;
    }
}

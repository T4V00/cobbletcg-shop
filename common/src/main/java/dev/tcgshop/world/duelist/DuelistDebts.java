package dev.tcgshop.world.duelist;

import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 1.0 · Wandering Duelist money that must survive a restart. Stored in the Overworld (data/tcgshop_duelist.dat).
 * <ul>
 *   <li><b>Escrow</b>: the stake held from the moment the player accepts (min(cash, duelistMaxTake)), tagged with the
 *   encounter (the duelist entity's UUID). One per player. Whoever removes it ({@link #take}) settles it, so it is
 *   settled exactly once: kept on a loss, refunded on a win / draw / abort / encounter gone. Any escrow still held when
 *   the server starts belongs to a match that cannot be running any more: it becomes a pending refund.</li>
 *   <li><b>Pending</b>: results and refunds for a player who was offline (disconnecting mid-match concedes it, so it is
 *   a loss), settled with the same rules as an online result when the player joins again.</li>
 * </ul>
 */
public final class DuelistDebts extends SavedData {
    /** Entry kinds: a result saved before the escrow existed (settled the old way), a result, a stake refund. */
    private static final int LEGACY = 0, RESULT = 1, REFUND = 2;

    /** held: the escrow refunded with a win / kept on a loss (or the refund amount); noCash: the item rule applies. */
    private record Entry(int kind, boolean won, String npc, long prize, long cap, long held, boolean noCash) {}

    /** A stake held while a duel is on. noCash: the player carried no cash at all on accept (a loss costs one item). */
    public record Escrow(UUID encounter, long amount, boolean noCash, String npc) {}

    private static final Factory<DuelistDebts> TYPE = new Factory<>(DuelistDebts::new, DuelistDebts::fromNbt, null);

    private final Map<UUID, List<Entry>> pending = new HashMap<>();
    private final Map<UUID, Escrow> escrow = new HashMap<>();

    public static DuelistDebts get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE, "tcgshop_duelist");
    }

    /** A result for an offline player: held = the escrow (refunded with a win, already kept on a loss). */
    void add(UUID player, boolean won, String npc, long prize, long held, boolean noCash) {
        pending.computeIfAbsent(player, k -> new ArrayList<>()).add(new Entry(RESULT, won, npc == null ? "" : npc, prize, 0, Math.max(0, held), noCash));
        setDirty();
    }

    /** A stake refund that could not be paid now (player offline, server stopping, bank error): paid on join. */
    void addRefund(UUID player, String npc, long amount) {
        if (amount <= 0) return;
        pending.computeIfAbsent(player, k -> new ArrayList<>()).add(new Entry(REFUND, false, npc == null ? "" : npc, 0, 0, amount, false));
        setDirty();
    }

    // ------------------------------------------------------------------ escrow

    @Nullable
    Escrow escrow(UUID player) {
        return escrow.get(player);
    }

    void hold(UUID player, UUID encounter, long amount, boolean noCash, String npc) {
        escrow.put(player, new Escrow(encounter, Math.max(0, amount), noCash, npc == null ? "" : npc));
        setDirty();
    }

    /** Removes and returns this player's escrow if it belongs to that encounter (null = any). The one settlement gate. */
    @Nullable
    Escrow take(UUID player, @Nullable UUID encounter) {
        Escrow e = escrow.get(player);
        if (e == null || (encounter != null && !encounter.equals(e.encounter()))) return null;
        escrow.remove(player);
        setDirty();
        return e;
    }

    /** Players whose stake is held by that encounter. */
    List<UUID> heldBy(UUID encounter) {
        List<UUID> out = new ArrayList<>();
        for (var en : escrow.entrySet()) if (en.getValue().encounter().equals(encounter)) out.add(en.getKey());
        return out;
    }

    /** Server start: no match survives a restart, so every stake still held is owed back (paid on join). */
    void releaseAll() {
        if (escrow.isEmpty()) return;
        for (var en : escrow.entrySet()) addRefund(en.getKey(), en.getValue().npc(), en.getValue().amount());
        escrow.clear();
        setDirty();
    }

    // ------------------------------------------------------------------ join

    /** On join: settles whatever happened while the player was away. */
    static void settle(ServerPlayer p) {
        if (p.getServer() == null) return;
        DuelistDebts st = get(p.getServer());
        List<Entry> list = st.pending.remove(p.getUUID());
        if (list == null || list.isEmpty()) return;
        st.setDirty();
        for (Entry e : list) {
            switch (e.kind()) {
                case REFUND -> WanderingDuelists.refund(p.getServer(), p.getUUID(), e.held(), e.npc()); // re-queued if it fails again
                case RESULT -> {
                    if (!e.won()) p.displayClientMessage(Component.translatable("tcgshop.duelist.forfeit_join").withStyle(ChatFormatting.GRAY), false);
                    WanderingDuelists.settle(p, e.won(), e.npc(), e.prize(), e.held(), e.noCash());
                }
                default -> {
                    if (!e.won()) p.displayClientMessage(Component.translatable("tcgshop.duelist.forfeit_join").withStyle(ChatFormatting.GRAY), false);
                    WanderingDuelists.settleLegacy(p, e.won(), e.npc(), e.prize(), e.cap());
                }
            }
        }
    }

    // ------------------------------------------------------------------ save

    private static DuelistDebts fromNbt(CompoundTag nbt, HolderLookup.Provider lookup) {
        DuelistDebts d = new DuelistDebts();
        ListTag players = nbt.getList("players", Tag.TAG_COMPOUND);
        for (int i = 0; i < players.size(); i++) {
            CompoundTag c = players.getCompound(i);
            if (!c.hasUUID("u")) continue;
            List<Entry> list = new ArrayList<>();
            ListTag entries = c.getList("e", Tag.TAG_COMPOUND);
            for (int j = 0; j < entries.size(); j++) {
                CompoundTag x = entries.getCompound(j);
                list.add(new Entry(x.getInt("k"), x.getBoolean("w"), x.getString("n"), x.getLong("p"), x.getLong("c"),
                        x.getLong("h"), x.getBoolean("x")));
            }
            if (!list.isEmpty()) d.pending.put(c.getUUID("u"), list);
        }
        ListTag held = nbt.getList("escrow", Tag.TAG_COMPOUND);
        for (int i = 0; i < held.size(); i++) {
            CompoundTag c = held.getCompound(i);
            if (!c.hasUUID("u") || !c.hasUUID("e")) continue;
            d.escrow.put(c.getUUID("u"), new Escrow(c.getUUID("e"), Math.max(0, c.getLong("a")), c.getBoolean("x"), c.getString("n")));
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag nbt, HolderLookup.Provider lookup) {
        ListTag players = new ListTag();
        for (var en : pending.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putUUID("u", en.getKey());
            ListTag entries = new ListTag();
            for (Entry e : en.getValue()) {
                CompoundTag x = new CompoundTag();
                x.putInt("k", e.kind());
                x.putBoolean("w", e.won());
                x.putString("n", e.npc());
                x.putLong("p", e.prize());
                x.putLong("c", e.cap());
                x.putLong("h", e.held());
                x.putBoolean("x", e.noCash());
                entries.add(x);
            }
            c.put("e", entries);
            players.add(c);
        }
        nbt.put("players", players);
        ListTag held = new ListTag();
        for (var en : escrow.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putUUID("u", en.getKey());
            c.putUUID("e", en.getValue().encounter());
            c.putLong("a", en.getValue().amount());
            c.putBoolean("x", en.getValue().noCash());
            c.putString("n", en.getValue().npc());
            held.add(c);
        }
        nbt.put("escrow", held);
        return nbt;
    }
}

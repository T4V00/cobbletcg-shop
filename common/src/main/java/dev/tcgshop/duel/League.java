package dev.tcgshop.duel;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** TCG League: ELO rating (starts at 1000), wins / losses, wins against the house and each player's saved deck. */
public final class League extends SavedData {
    private static final Factory<League> TYPE = new Factory<>(League::new, League::fromNbt, null);
    public static final int[] RANK_AT = {0, 1100, 1250, 1400, 1550, 1700};

    public static final class Entry {
        public String name = "";
        public int rating = 1000, wins, losses, aiWins;
        /** Best rating reached, current / best win streak and finished matches (any mode). */
        public int peak = 1000, streak, bestStreak, played;
        public final List<String> deck = new ArrayList<>();
        /** Unlocked ("coin:id" / "board:id") and equipped cosmetics. */
        public final java.util.Set<String> unlocked = new java.util.LinkedHashSet<>();
        public String coin = "gold", board = "classic";

        /** End of a match for this player (any mode): streak and counter. */
        public void finished(boolean won, boolean ranked) {
            played++;
            if (ranked) { // streaks only count in league matches
                if (won) { streak++; bestStreak = Math.max(bestStreak, streak); }
                else streak = 0;
            }
            peak = Math.max(peak, rating);
        }
    }

    private final Map<UUID, Entry> players = new HashMap<>();

    public static League get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE, "tcgshop_league");
    }

    public Entry entry(UUID id, String name) {
        Entry e = players.computeIfAbsent(id, k -> new Entry());
        if (name != null && !name.isEmpty()) e.name = name;
        return e;
    }

    public static int rank(int rating) {
        int r = 0;
        for (int i = 0; i < RANK_AT.length; i++) if (rating >= RANK_AT[i]) r = i;
        return r;
    }

    /** Result of a league match. Returns the winner's rating change. */
    public int record(UUID winner, String wName, UUID loser, String lName) {
        Entry w = entry(winner, wName), l = entry(loser, lName);
        double expected = 1 / (1 + Math.pow(10, (l.rating - w.rating) / 400.0));
        int delta = Math.max(4, (int) Math.round(32 * (1 - expected)));
        w.rating += delta;
        l.rating = Math.max(100, l.rating - delta);
        w.wins++;
        l.losses++;
        setDirty();
        return delta;
    }

    /**
     * League points against the AI: fixed table per level (beating the Champion is worth much more than several easy ones, and losing
     * to the easy one hurts more than losing to the Champion).
     */
    public static final int[] AI_WIN = {4, 9, 16, 28};
    public static final int[] AI_LOSS = {16, 12, 10, 10};
    /** Minimum rank (index into RANK_AT) from which each level is no longer allowed in league matches. */
    public static final int[] AI_BLOCKED_FROM = {2, 3, 4, 99}; // Gold+ no easy, Platinum+ no normal, Diamond+ Champion only

    public static boolean aiLevelAllowed(int rating, int level) {
        level = Math.max(0, Math.min(AI_WIN.length - 1, level));
        return rank(rating) < AI_BLOCKED_FROM[level];
    }

    /** League match against the AI. Returns the player's change (positive or negative). */
    public int recordVsAi(UUID player, String name, int level, boolean won) {
        Entry e = entry(player, name);
        level = Math.max(0, Math.min(AI_WIN.length - 1, level));
        int delta = won ? AI_WIN[level] : -AI_LOSS[level];
        e.rating = Math.max(100, e.rating + delta);
        if (won) { e.wins++; e.aiWins++; }
        else e.losses++;
        setDirty();
        return delta;
    }

    public List<Map.Entry<UUID, Entry>> top(int n) {
        List<Map.Entry<UUID, Entry>> l = new ArrayList<>(players.entrySet());
        l.removeIf(e -> e.getValue().wins + e.getValue().losses == 0);
        l.sort(Comparator.comparingInt((Map.Entry<UUID, Entry> e) -> e.getValue().rating).reversed());
        return l.subList(0, Math.min(n, l.size()));
    }

    private static League fromNbt(CompoundTag nbt, HolderLookup.Provider lookup) {
        League lg = new League();
        CompoundTag p = nbt.getCompound("players");
        for (String k : p.getAllKeys()) {
            UUID id;
            try { id = UUID.fromString(k); } catch (IllegalArgumentException ex) { continue; }
            CompoundTag c = p.getCompound(k);
            Entry e = new Entry();
            e.name = c.getString("name");
            e.rating = c.contains("rating") ? c.getInt("rating") : 1000;
            e.wins = c.getInt("wins");
            e.losses = c.getInt("losses");
            e.aiWins = c.getInt("ai_wins");
            ListTag d = c.getList("deck", Tag.TAG_STRING);
            for (int i = 0; i < d.size(); i++) e.deck.add(d.getString(i));
            e.peak = Math.max(e.rating, c.contains("peak") ? c.getInt("peak") : 1000);
            e.streak = c.getInt("streak");
            e.bestStreak = c.getInt("best_streak");
            e.played = c.contains("played") ? c.getInt("played") : e.wins + e.losses;
            ListTag u = c.getList("unlocked", Tag.TAG_STRING);
            for (int i = 0; i < u.size(); i++) e.unlocked.add(u.getString(i));
            if (c.contains("coin")) e.coin = c.getString("coin");
            if (c.contains("board")) e.board = c.getString("board");
            lg.players.put(id, e);
        }
        return lg;
    }

    @Override
    public CompoundTag save(CompoundTag nbt, HolderLookup.Provider lookup) {
        CompoundTag p = new CompoundTag();
        players.forEach((k, e) -> {
            CompoundTag c = new CompoundTag();
            c.putString("name", e.name);
            c.putInt("rating", e.rating);
            c.putInt("wins", e.wins);
            c.putInt("losses", e.losses);
            c.putInt("ai_wins", e.aiWins);
            ListTag d = new ListTag();
            for (String s : e.deck) d.add(StringTag.valueOf(s));
            c.put("deck", d);
            c.putInt("peak", e.peak);
            c.putInt("streak", e.streak);
            c.putInt("best_streak", e.bestStreak);
            c.putInt("played", e.played);
            ListTag u = new ListTag();
            for (String s : e.unlocked) u.add(StringTag.valueOf(s));
            c.put("unlocked", u);
            c.putString("coin", e.coin);
            c.putString("board", e.board);
            p.put(k.toString(), c);
        });
        nbt.put("players", p);
        return nbt;
    }
}

package dev.tcgshop.duel;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Liga TCG: puntuación ELO (empieza en 1000), victorias / derrotas, victorias contra la casa y el mazo guardado de cada jugador. */
public final class League extends PersistentState {
    private static final Type<League> TYPE = new Type<>(League::new, League::fromNbt, null);
    public static final int[] RANK_AT = {0, 1100, 1250, 1400, 1550, 1700};

    public static final class Entry {
        public String name = "";
        public int rating = 1000, wins, losses, aiWins;
        /** Mejor puntuación alcanzada, racha actual / mejor racha de victorias y partidas terminadas (cualquier modo). */
        public int peak = 1000, streak, bestStreak, played;
        public final List<String> deck = new ArrayList<>();
        /** Cosméticos desbloqueados ("coin:id" / "board:id") y equipados. */
        public final java.util.Set<String> unlocked = new java.util.LinkedHashSet<>();
        public String coin = "gold", board = "classic";

        /** Fin de una partida de este jugador (cualquier modo): racha y contador. */
        public void finished(boolean won, boolean ranked) {
            played++;
            if (ranked) { // las rachas sólo cuentan en partidas de liga
                if (won) { streak++; bestStreak = Math.max(bestStreak, streak); }
                else streak = 0;
            }
            peak = Math.max(peak, rating);
        }
    }

    private final Map<UUID, Entry> players = new HashMap<>();

    public static League get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE, "tcgshop_league");
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

    /** Resultado de una partida de liga. Devuelve el cambio de puntuación del ganador. */
    public int record(UUID winner, String wName, UUID loser, String lName) {
        Entry w = entry(winner, wName), l = entry(loser, lName);
        double expected = 1 / (1 + Math.pow(10, (l.rating - w.rating) / 400.0));
        int delta = Math.max(4, (int) Math.round(32 * (1 - expected)));
        w.rating += delta;
        l.rating = Math.max(100, l.rating - delta);
        w.wins++;
        l.losses++;
        markDirty();
        return delta;
    }

    /**
     * Puntos de liga contra la IA: tabla fija por nivel (ganar al Campeón vale mucho más que varias fáciles, y perder
     * contra la fácil duele más que perder contra el Campeón).
     */
    public static final int[] AI_WIN = {4, 9, 16, 28};
    public static final int[] AI_LOSS = {16, 12, 10, 10};
    /** Rango mínimo (índice de RANK_AT) a partir del cual ya no se permite cada nivel en partidas de liga. */
    public static final int[] AI_BLOCKED_FROM = {2, 3, 4, 99}; // Oro+ sin fácil, Platino+ sin normal, Diamante+ sólo Campeón

    public static boolean aiLevelAllowed(int rating, int level) {
        level = Math.max(0, Math.min(AI_WIN.length - 1, level));
        return rank(rating) < AI_BLOCKED_FROM[level];
    }

    /** Partida de liga contra la IA. Devuelve el cambio (positivo o negativo) del jugador. */
    public int recordVsAi(UUID player, String name, int level, boolean won) {
        Entry e = entry(player, name);
        level = Math.max(0, Math.min(AI_WIN.length - 1, level));
        int delta = won ? AI_WIN[level] : -AI_LOSS[level];
        e.rating = Math.max(100, e.rating + delta);
        if (won) { e.wins++; e.aiWins++; }
        else e.losses++;
        markDirty();
        return delta;
    }

    public List<Map.Entry<UUID, Entry>> top(int n) {
        List<Map.Entry<UUID, Entry>> l = new ArrayList<>(players.entrySet());
        l.removeIf(e -> e.getValue().wins + e.getValue().losses == 0);
        l.sort(Comparator.comparingInt((Map.Entry<UUID, Entry> e) -> e.getValue().rating).reversed());
        return l.subList(0, Math.min(n, l.size()));
    }

    private static League fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        League lg = new League();
        NbtCompound p = nbt.getCompound("players");
        for (String k : p.getKeys()) {
            UUID id;
            try { id = UUID.fromString(k); } catch (IllegalArgumentException ex) { continue; }
            NbtCompound c = p.getCompound(k);
            Entry e = new Entry();
            e.name = c.getString("name");
            e.rating = c.contains("rating") ? c.getInt("rating") : 1000;
            e.wins = c.getInt("wins");
            e.losses = c.getInt("losses");
            e.aiWins = c.getInt("ai_wins");
            NbtList d = c.getList("deck", NbtElement.STRING_TYPE);
            for (int i = 0; i < d.size(); i++) e.deck.add(d.getString(i));
            e.peak = Math.max(e.rating, c.contains("peak") ? c.getInt("peak") : 1000);
            e.streak = c.getInt("streak");
            e.bestStreak = c.getInt("best_streak");
            e.played = c.contains("played") ? c.getInt("played") : e.wins + e.losses;
            NbtList u = c.getList("unlocked", NbtElement.STRING_TYPE);
            for (int i = 0; i < u.size(); i++) e.unlocked.add(u.getString(i));
            if (c.contains("coin")) e.coin = c.getString("coin");
            if (c.contains("board")) e.board = c.getString("board");
            lg.players.put(id, e);
        }
        return lg;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        NbtCompound p = new NbtCompound();
        players.forEach((k, e) -> {
            NbtCompound c = new NbtCompound();
            c.putString("name", e.name);
            c.putInt("rating", e.rating);
            c.putInt("wins", e.wins);
            c.putInt("losses", e.losses);
            c.putInt("ai_wins", e.aiWins);
            NbtList d = new NbtList();
            for (String s : e.deck) d.add(NbtString.of(s));
            c.put("deck", d);
            c.putInt("peak", e.peak);
            c.putInt("streak", e.streak);
            c.putInt("best_streak", e.bestStreak);
            c.putInt("played", e.played);
            NbtList u = new NbtList();
            for (String s : e.unlocked) u.add(NbtString.of(s));
            c.put("unlocked", u);
            c.putString("coin", e.coin);
            c.putString("board", e.board);
            p.put(k.toString(), c);
        });
        nbt.put("players", p);
        return nbt;
    }
}

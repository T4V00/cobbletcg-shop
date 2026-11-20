package dev.tcgshop.duel;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.tcgshop.collection.CollectionService;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Cosméticos del modo duelo: monedas 3D y fondos de tablero. Se desbloquean por rango de Liga (también el mejor
 * rango alcanzado), por logros (victorias, partidas, rachas, sobres abiertos) o como botín raro al abrir sobres.
 * El servidor decide qué tienes; el cliente sólo dibuja lo que le dicen.
 */
public final class Cosmetics {
    private Cosmetics() {}

    /** Cómo se consigue. RANK usa el mejor rango alcanzado; PEAK la mejor puntuación. */
    public enum Rule { FREE, RANK, PEAK, WINS, AI_WINS, PLAYED, STREAK, PACKS, LOOT }

    /** Emblemas de la cara de la moneda (la cruz siempre lleva estrella). */
    public static final int E_BALL = 0, E_STAR = 1, E_BOLT = 2, E_FLAME = 3, E_DROP = 4, E_LEAF = 5, E_MOON = 6, E_SUN = 7,
            E_CROWN = 8, E_GEM = 9, E_SNOW = 10, E_HEART = 11, E_SPIRAL = 12, E_SHIELD = 13;
    /** Efectos: brillo arcoíris en el canto / destellos. */
    public static final int FX_NONE = 0, FX_RAINBOW = 1, FX_SPARKLE = 2;
    /** Probabilidad por sobre de botín (EconomyConfig). */
    public static double LOOT_CHANCE = 1 / 140.0;

    public record Coin(String id, Rule rule, int arg, int rim, int face, int faceDark, int emA, int emB, int emblem, int fx) {}

    public record Board(String id, Rule rule, int arg, int accent) {}

    public static final List<Coin> COINS = List.of(
            new Coin("gold", Rule.FREE, 0, 0xFFB8860B, 0xFFF5C542, 0xFFC99A1E, 0xFFE0302A, 0xFFF7F3E8, E_BALL, FX_NONE),
            new Coin("copper", Rule.FREE, 0, 0xFF7A3E1D, 0xFFD9824A, 0xFF9C5228, 0xFFFFE2C2, 0xFF6B3415, E_STAR, FX_NONE),
            new Coin("silver", Rule.PLAYED, 3, 0xFF8E99A8, 0xFFE3E8EF, 0xFFA9B4C2, 0xFF3A6FD8, 0xFFF4F6FA, E_BALL, FX_NONE),
            new Coin("great", Rule.RANK, 1, 0xFF1F4E9C, 0xFF5B9BE8, 0xFF2D64B8, 0xFFE33B3B, 0xFFF4F6FA, E_BALL, FX_NONE),
            new Coin("ultra", Rule.RANK, 2, 0xFF1A1A1E, 0xFF3C3C44, 0xFF26262C, 0xFFFFC928, 0xFFF4F6FA, E_BALL, FX_NONE),
            new Coin("platinum", Rule.RANK, 3, 0xFF6E8C94, 0xFFD4ECEF, 0xFF8FB2BA, 0xFF2BB5A8, 0xFFFFFFFF, E_SHIELD, FX_NONE),
            new Coin("diamond", Rule.RANK, 4, 0xFF2A6FA8, 0xFFB9E4FF, 0xFF6FB6E6, 0xFF4FD2FF, 0xFFFFFFFF, E_GEM, FX_SPARKLE),
            new Coin("master", Rule.RANK, 5, 0xFF4B2A7A, 0xFF8F5BD8, 0xFF5B3596, 0xFFD94BC9, 0xFFEDE3FF, E_BALL, FX_SPARKLE),
            new Coin("champion", Rule.PEAK, 1850, 0xFF8A1C1C, 0xFFF2C14E, 0xFFB8862A, 0xFFD62828, 0xFFFFF4D0, E_CROWN, FX_SPARKLE),
            new Coin("legend", Rule.PEAK, 2000, 0xFFFFFFFF, 0xFFFFF1C9, 0xFFE8C77A, 0xFFFF9E2C, 0xFFFFFFFF, E_SUN, FX_RAINBOW),
            new Coin("volt", Rule.WINS, 5, 0xFF8A6A00, 0xFFFFE04A, 0xFFD9A800, 0xFF2B2B33, 0xFFFFFFFF, E_BOLT, FX_NONE),
            new Coin("ember", Rule.WINS, 10, 0xFF7A1A0A, 0xFFF26B3A, 0xFFB83A18, 0xFFFFD34D, 0xFFFFF0C0, E_FLAME, FX_NONE),
            new Coin("tide", Rule.WINS, 25, 0xFF0D3F73, 0xFF4FA3E8, 0xFF1F6CB8, 0xFFE6F6FF, 0xFFBDE3FF, E_DROP, FX_NONE),
            new Coin("shield", Rule.WINS, 50, 0xFF4A525C, 0xFFB7C1CC, 0xFF7D8894, 0xFF2F3A48, 0xFFE8EDF2, E_SHIELD, FX_NONE),
            new Coin("verdant", Rule.AI_WINS, 10, 0xFF1F5A2A, 0xFF6CC46F, 0xFF33884A, 0xFFE8FFD8, 0xFFB9F0A8, E_LEAF, FX_NONE),
            new Coin("frost", Rule.AI_WINS, 25, 0xFF4F7FA0, 0xFFDDF4FF, 0xFF9CCDE8, 0xFF3A8FC8, 0xFFFFFFFF, E_SNOW, FX_SPARKLE),
            new Coin("lunar", Rule.STREAK, 3, 0xFF1A1F3D, 0xFF3A4478, 0xFF242B52, 0xFFF2E6A8, 0xFFFFFFFF, E_MOON, FX_NONE),
            new Coin("solar", Rule.STREAK, 5, 0xFFA34A00, 0xFFFFB347, 0xFFE07B12, 0xFFFFF4C2, 0xFFFFFFFF, E_SUN, FX_NONE),
            new Coin("heart", Rule.PLAYED, 10, 0xFFA8326E, 0xFFFFB0D4, 0xFFE070A6, 0xFFE0245E, 0xFFFFFFFF, E_HEART, FX_NONE),
            new Coin("mind", Rule.PLAYED, 50, 0xFF5A1A6E, 0xFFE06FD8, 0xFFA03CA8, 0xFFFFE0FA, 0xFFFFFFFF, E_SPIRAL, FX_NONE),
            new Coin("premier", Rule.PACKS, 25, 0xFFB0B0B8, 0xFFFFFFFF, 0xFFDADADF, 0xFFE33B3B, 0xFFFFFFFF, E_BALL, FX_NONE),
            new Coin("luxury", Rule.PACKS, 100, 0xFF8A6A1A, 0xFF2A2A30, 0xFF16161A, 0xFFE8B730, 0xFFE33B3B, E_BALL, FX_NONE),
            new Coin("obsidian", Rule.PACKS, 500, 0xFF101014, 0xFF2E2440, 0xFF1A1426, 0xFFB06BFF, 0xFFE8D8FF, E_GEM, FX_SPARKLE),
            new Coin("clover", Rule.LOOT, 0, 0xFF2E6B1F, 0xFFE8D27A, 0xFFB89A3A, 0xFF3FA34D, 0xFFFFFFFF, E_LEAF, FX_SPARKLE),
            new Coin("shiny", Rule.LOOT, 0, 0xFFFFFFFF, 0xFFF0F4FF, 0xFFB8C4E8, 0xFFFFD34D, 0xFFFFFFFF, E_STAR, FX_RAINBOW),
            new Coin("phantom", Rule.LOOT, 0, 0xFF2A1840, 0xFF7E5BB0, 0xFF4A3270, 0xFFE0D0FF, 0xFFB090E0, E_MOON, FX_SPARKLE),
            new Coin("wyrm", Rule.LOOT, 0, 0xFF0E4A4A, 0xFF3FB8A8, 0xFF1F7A70, 0xFFFFD34D, 0xFFFFF0C0, E_FLAME, FX_SPARKLE)
    );

    public static final List<Board> BOARDS = List.of(
            new Board("classic", Rule.FREE, 0, 0xFF7FB2E5),
            new Board("pokeball", Rule.FREE, 0, 0xFFE33B3B),
            new Board("meadow", Rule.PLAYED, 1, 0xFF56B85A),
            new Board("ocean", Rule.PLAYED, 5, 0xFF2E86DE),
            new Board("blaze", Rule.WINS, 3, 0xFFF26B3A),
            new Board("storm", Rule.WINS, 10, 0xFFFFD23F),
            new Board("glacier", Rule.AI_WINS, 5, 0xFF8FD8FF),
            new Board("psyche", Rule.AI_WINS, 20, 0xFFE06FD8),
            new Board("haunt", Rule.STREAK, 3, 0xFF7E5BB0),
            new Board("pixie", Rule.PLAYED, 25, 0xFFFF9EC8),
            new Board("forge", Rule.RANK, 1, 0xFF9AA6B4),
            new Board("canyon", Rule.RANK, 2, 0xFFC08A4E),
            new Board("dragon", Rule.RANK, 3, 0xFF3FB8A8),
            new Board("midnight", Rule.RANK, 4, 0xFF3A4478),
            new Board("master", Rule.RANK, 5, 0xFF8F5BD8),
            new Board("champion", Rule.PEAK, 1850, 0xFFF2C14E),
            new Board("cosmos", Rule.PEAK, 2000, 0xFF6B4FD8),
            new Board("toxic", Rule.PACKS, 50, 0xFFA55BD8),
            new Board("dojo", Rule.WINS, 25, 0xFFD84B3A),
            new Board("sakura", Rule.PACKS, 200, 0xFFFFB7C9),
            new Board("prism", Rule.STREAK, 7, 0xFFFFFFFF)
    );

    public static int coinIndex(String id) {
        for (int i = 0; i < COINS.size(); i++) if (COINS.get(i).id.equals(id)) return i;
        return 0;
    }

    public static int boardIndex(String id) {
        for (int i = 0; i < BOARDS.size(); i++) if (BOARDS.get(i).id.equals(id)) return i;
        return 0;
    }

    @Nullable
    public static Coin coin(String id) {
        for (Coin c : COINS) if (c.id.equals(id)) return c;
        return null;
    }

    @Nullable
    public static Board board(String id) {
        for (Board b : BOARDS) if (b.id.equals(id)) return b;
        return null;
    }

    // ------------------------------------------------------------------ servidor

    /** Progreso de una regla para este jugador (para la barra de logros) y si ya la cumple. */
    public static int progress(Rule r, League.Entry e, int packs) {
        return switch (r) {
            case RANK -> League.rank(e.peak);
            case PEAK -> e.peak;
            case WINS -> e.wins;
            case AI_WINS -> e.aiWins;
            case PLAYED -> e.played;
            case STREAK -> e.bestStreak;
            case PACKS -> packs;
            default -> 0;
        };
    }

    public static boolean met(Rule r, int arg, League.Entry e, int packs) {
        return switch (r) {
            case FREE -> true;
            case LOOT -> false;
            default -> progress(r, e, packs) >= arg;
        };
    }

    private static int packs(ServerPlayerEntity p) {
        try { return CollectionService.playerData(p).packsOpened; } catch (Exception ex) { return 0; }
    }

    public static boolean owns(League.Entry e, String key) {
        if (key.startsWith("coin:")) {
            Coin c = coin(key.substring(5));
            return c != null && (c.rule == Rule.FREE || e.unlocked.contains(key));
        }
        if (key.startsWith("board:")) {
            Board b = board(key.substring(6));
            return b != null && (b.rule == Rule.FREE || e.unlocked.contains(key));
        }
        return false;
    }

    /** Revisa todo lo desbloqueable y avisa de lo nuevo. Llamar tras una partida, al abrir sobres y al abrir la Liga. */
    public static void check(ServerPlayerEntity p) {
        League lg = League.get(p.getServer());
        League.Entry e = lg.entry(p.getUuid(), p.getName().getString());
        e.peak = Math.max(e.peak, e.rating);
        int packs = packs(p);
        List<Text> got = new ArrayList<>();
        for (Coin c : COINS) {
            if (c.rule == Rule.FREE || c.rule == Rule.LOOT) continue;
            String key = "coin:" + c.id;
            if (!e.unlocked.contains(key) && met(c.rule, c.arg, e, packs)) {
                e.unlocked.add(key);
                got.add(Text.translatable("tcgshop.cos.unlocked_coin", Text.translatable("tcgshop.coin." + c.id).formatted(Formatting.GOLD)));
            }
        }
        for (Board b : BOARDS) {
            if (b.rule == Rule.FREE || b.rule == Rule.LOOT) continue;
            String key = "board:" + b.id;
            if (!e.unlocked.contains(key) && met(b.rule, b.arg, e, packs)) {
                e.unlocked.add(key);
                got.add(Text.translatable("tcgshop.cos.unlocked_board", Text.translatable("tcgshop.board." + b.id).formatted(Formatting.AQUA)));
            }
        }
        if (!got.isEmpty()) {
            lg.markDirty();
            for (Text t : got) p.sendMessage(Text.literal("✦ ").formatted(Formatting.YELLOW).append(t), false);
            p.playSoundToPlayer(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 0.7f, 1.3f);
        }
    }

    /** Botín: cada sobre abierto tiene una pequeña probabilidad de traer una moneda de botín que aún no tengas. */
    public static void rollLoot(ServerPlayerEntity p, int packsOpened) {
        if (packsOpened <= 0) return;
        League lg = League.get(p.getServer());
        League.Entry e = lg.entry(p.getUuid(), p.getName().getString());
        List<Coin> left = new ArrayList<>();
        for (Coin c : COINS) if (c.rule == Rule.LOOT && !e.unlocked.contains("coin:" + c.id)) left.add(c);
        if (left.isEmpty()) return;
        double chance = 1 - Math.pow(1 - LOOT_CHANCE, packsOpened);
        if (p.getRandom().nextDouble() >= chance) return;
        Coin c = left.get(p.getRandom().nextInt(left.size()));
        e.unlocked.add("coin:" + c.id);
        lg.markDirty();
        p.sendMessage(Text.literal("✦ ").formatted(Formatting.LIGHT_PURPLE)
                .append(Text.translatable("tcgshop.cos.loot_coin", Text.translatable("tcgshop.coin." + c.id).formatted(Formatting.GOLD))), false);
        p.playSoundToPlayer(SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.8f, 1.6f);
    }

    /** Equipar una moneda o un fondo (sólo si es tuyo). */
    public static boolean equip(ServerPlayerEntity p, String kind, String id) {
        League lg = League.get(p.getServer());
        League.Entry e = lg.entry(p.getUuid(), p.getName().getString());
        if (kind.equals("coin") && owns(e, "coin:" + id)) e.coin = id;
        else if (kind.equals("board") && owns(e, "board:" + id)) e.board = id;
        else return false;
        lg.markDirty();
        return true;
    }

    public static String coinOf(@Nullable ServerPlayerEntity p) {
        if (p == null) return "gold";
        League.Entry e = League.get(p.getServer()).entry(p.getUuid(), p.getName().getString());
        return owns(e, "coin:" + e.coin) ? e.coin : "gold";
    }

    public static String boardOf(@Nullable ServerPlayerEntity p) {
        if (p == null) return "classic";
        League.Entry e = League.get(p.getServer()).entry(p.getUuid(), p.getName().getString());
        return owns(e, "board:" + e.board) ? e.board : "classic";
    }

    /** Lo que se manda al teléfono: equipado, desbloqueado y el progreso de cada regla. */
    public static JsonObject json(ServerPlayerEntity p) {
        League.Entry e = League.get(p.getServer()).entry(p.getUuid(), p.getName().getString());
        int packs = packs(p);
        JsonObject o = new JsonObject();
        o.addProperty("coin", owns(e, "coin:" + e.coin) ? e.coin : "gold");
        o.addProperty("board", owns(e, "board:" + e.board) ? e.board : "classic");
        JsonArray un = new JsonArray();
        for (Coin c : COINS) if (owns(e, "coin:" + c.id)) un.add("coin:" + c.id);
        for (Board b : BOARDS) if (owns(e, "board:" + b.id)) un.add("board:" + b.id);
        o.add("unlocked", un);
        JsonObject pr = new JsonObject();
        for (Rule r : Rule.values()) pr.addProperty(r.name(), progress(r, e, packs));
        o.add("progress", pr);
        o.addProperty("peak", e.peak);
        o.addProperty("streak", e.streak);
        return o;
    }
}

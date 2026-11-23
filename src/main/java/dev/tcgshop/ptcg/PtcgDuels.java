package dev.tcgshop.ptcg;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import dev.tcgshop.advancement.CardAdvancements;
import dev.masterball.api.card.CardInfo;
import dev.tcgshop.collection.BinderData;
import dev.tcgshop.collection.CollectionService;
import dev.tcgshop.duel.League;
import dev.tcgshop.economy.CobbleDollarsBridge;
import dev.tcgshop.grading.Grading;
import dev.tcgshop.network.PtcgPayloads;
import dev.tcgshop.pricing.PriceService;
import dev.tcgshop.shop.DecorBlocks;
import dev.tcgshop.shop.TcgBlocks;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REAL Pokémon TCG matches (twinleaf.gg engine).
 *  · Play table: click = wait for an opponent (player-vs-player league) · Shift + click = friendly match against the AI.
 *  · Phone (TCG League → Play): friendly vs the AI (4 levels), LEAGUE vs the AI (ELO against its rating) and
 *    league queue against other players. Phone matches don't need a table.
 *  · Before starting, the deck is checked (60 legal cards and that you own the copies) and what's wrong is reported.
 *  · Suggestions: the engine's AI proposes your next move. Attack/ability animations come from the engine.
 */
public final class PtcgDuels {
    public static final String[] LEVELS = {"easy", "normal", "hard", "champion"};
    private static final double[] LEVEL_CHAOS = {1.0, 0.6, 0.25, 0.0};
    public static final int[] LEVEL_RATING = {900, 1100, 1300, 1550};
    public static long[] LEVEL_REWARD = {800, 1_500, 3_000, 6_000};
    /** The cost of losing a competitive match (League vs the AI per level; between players, fixed). */
    public static long[] LEVEL_PENALTY = {200, 400, 800, 1_500};
    public static long PVP_PENALTY = 600; // EconomyConfig

    private record MetaDeck(String id, String name, List<String> cards) {}

    private static List<MetaDeck> metaDecks;

    /** Competitive metagame decks (data/tcgshop/ptcg_meta_decks.json, generated with engine/meta_decks.js). */
    private static List<MetaDeck> metaDecks() {
        if (metaDecks == null) {
            metaDecks = new ArrayList<>();
            try (var in = PtcgDuels.class.getResourceAsStream("/data/tcgshop/ptcg_meta_decks.json")) {
                if (in != null) for (JsonElement e : JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonArray()) {
                    JsonObject o = e.getAsJsonObject();
                    List<String> cards = new ArrayList<>();
                    for (JsonElement c : o.getAsJsonArray("cards")) cards.add(c.getAsString());
                    metaDecks.add(new MetaDeck(o.get("id").getAsString(), o.get("name").getAsString(), cards));
                }
            } catch (Exception ex) {
                dev.tcgshop.TcgShop.LOGGER.warn("Could not read the meta decks: {}", ex.toString());
            }
        }
        return metaDecks;
    }
    private static final int IDLE_SECONDS = 150;
    private static final Gson GSON = new Gson();

    private enum Mode { TABLE_PVP, CASUAL_AI, RANKED_AI, QUEUE_PVP, CHALLENGE }

    /** Bot pacing (ticks): pause between moves and before the first one. */
    private static final int BOT_STEP_DELAY = 38, BOT_FIRST_DELAY = 90;

    private static final class Match {
        final String id;
        final ServerWorld world;
        @Nullable final BlockPos table;
        final Mode mode;
        final int level;
        final UUID[] players = new UUID[2]; // null = AI
        final String[] names = new String[2];
        final JsonObject[] views = new JsonObject[2];
        final JsonObject[] gradedCards = {new JsonObject(), new JsonObject()};
        final long[] penalty = new long[2];
        final String[] suggestion = {"", ""};
        final int[] timeouts = new int[2];
        long lastChange, endedAt = -1;
        /** Next moment (world time) when the bot may act: gives time to read and see each move. */
        long botNext;
        int pending, ratingDelta;
        boolean over, started;
        /** 1.0 beta 3: told how a hooked NPC match ended (Wandering Duelist); it pays its own stakes. Fired once. */
        @Nullable NpcMatchHook hook;
        /** Prize shown on the result screen instead of the house reward (-1 = the house reward). */
        long shownReward = -1;

        Match(String id, ServerWorld world, @Nullable BlockPos table, Mode mode, int level) {
            this.id = id;
            this.world = world;
            this.table = table;
            this.mode = mode;
            this.level = level;
        }

        int seat(UUID u) { return u.equals(players[0]) ? 0 : u.equals(players[1]) ? 1 : -1; }

        boolean ai() { return players[1] == null; }

        boolean ranked() { return mode == Mode.TABLE_PVP || mode == Mode.QUEUE_PVP || mode == Mode.RANKED_AI; } // CHALLENGE = friendly
    }

    /**
     * 1.0 beta 3 (Wandering Duelist): how a hooked NPC match ended. outcome: 1 = the player won; 0 = the player lost
     * (conceding, walking away from the table or disconnecting included); -1 = no winner after it started (draw or
     * aborted); -2 = it never started (deck refused, table taken...: the player was told why). Called once, on the
     * server thread; the player may be offline by then. With a hook the house reward is not paid (the hook pays).
     */
    public interface NpcMatchHook { void onEnd(UUID player, int outcome); }

    private static void fireHook(@Nullable NpcMatchHook h, UUID player, int outcome) {
        if (h == null || player == null) return;
        try {
            h.onEnd(player, outcome);
        } catch (RuntimeException ex) {
            dev.tcgshop.TcgShop.LOGGER.warn("NPC match hook failed: {}", ex.toString());
        }
    }

    private static final Map<UUID, Match> BY_PLAYER = new HashMap<>();
    private static final Map<String, Match> MATCHES = new LinkedHashMap<>();
    private static final Map<BlockPos, UUID> WAITING = new HashMap<>();
    private static final List<UUID> QUEUE = new ArrayList<>();
    /** Pending challenges: challenged -> (challenger, expires at server tick). */
    private static final Map<UUID, Object[]> CHALLENGES = new HashMap<>();
    private static int counter;

    private PtcgDuels() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(PtcgDuels::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((h, s) -> {
            UUID u = h.getPlayer().getUuid();
            Match m = BY_PLAYER.get(u);
            // 1.0 beta 3: leaving a hooked NPC match (Wandering Duelist) is a loss settled right now, not after the async
            // concede below (on a server stop or a single-player quit that result may never arrive)
            if (m != null && m.hook != null && m.started && !m.over && u.equals(m.players[0])) {
                NpcMatchHook hook = m.hook;
                m.hook = null;
                fireHook(hook, u, 0);
            }
            if (m != null) concede(m, u);
            WAITING.values().removeIf(x -> x.equals(u));
            QUEUE.remove(u);
        });
    }

    private static int clientId(int seat) { return seat + 1; }

    public static boolean inQueue(UUID u) { return QUEUE.contains(u); }

    @Nullable
    private static Match tableMatch(BlockPos table) {
        for (Match m : MATCHES.values()) if (table.equals(m.table)) return m;
        return null;
    }

    private static boolean engineReady(ServerPlayerEntity p) {
        if (PtcgEngine.ready() && PtcgCards.briefReady()) return true;
        if (PtcgEngine.failed()) p.sendMessage(Text.translatable("tcgshop.ptcg.engine_failed").formatted(Formatting.RED), false);
        else {
            p.sendMessage(Text.translatable("tcgshop.ptcg.loading").formatted(Formatting.GRAY), true);
            PtcgEngine.preload().thenRun(PtcgCards::loadBrief);
        }
        return false;
    }

    // ------------------------------------------------------------------ tables

    public static void useTable(ServerPlayerEntity p, BlockPos tablePos, boolean vsAi) {
        final BlockPos table = tablePos.toImmutable();
        Match mine = BY_PLAYER.get(p.getUuid());
        if (mine != null) { sendViews(mine, true); return; }
        // 1.0 beta 3: the Wandering Duelist's own table (stakes window, busy with someone else...)
        if (dev.tcgshop.world.duelist.WanderingDuelists.interceptTable(p, table)) return;
        // 1.0: chairs requirement, customer exhibitions holding the table and pending customer challenges (PlayTables)
        if (PlayTables.intercept(p, table)) return;
        if (!engineReady(p)) return;
        if (tableMatch(table) != null) {
            p.sendMessage(Text.translatable("tcgshop.duel.table_busy").formatted(Formatting.RED), true);
            return;
        }
        if (vsAi) {
            startAi(p, table, Mode.CASUAL_AI, 1);
            return;
        }
        UUID waiting = WAITING.get(table);
        ServerPlayerEntity other = waiting == null ? null : p.getServer().getPlayerManager().getPlayer(waiting);
        if (other == p) {
            WAITING.remove(table);
            p.sendMessage(Text.translatable("tcgshop.duel.wait_cancel").formatted(Formatting.GRAY), true);
            return;
        }
        if (other == null || other.squaredDistanceTo(table.toCenterPos()) > 12 * 12 || BY_PLAYER.containsKey(waiting)) {
            deckFor(p, deck -> {
                if (deck == null) return;
                WAITING.put(table, p.getUuid());
                p.sendMessage(Text.translatable("tcgshop.duel.waiting").formatted(Formatting.AQUA), false);
                for (ServerPlayerEntity o : p.getServerWorld().getPlayers(o -> o != p && o.squaredDistanceTo(table.toCenterPos()) < 24 * 24))
                    o.sendMessage(Text.translatable("tcgshop.duel.waiting_other", p.getDisplayName()).formatted(Formatting.AQUA), false);
            });
            return;
        }
        WAITING.remove(table);
        startPvp(other, p, table, Mode.TABLE_PVP);
    }

    // ------------------------------------------------------------------ 1.0: helpers for PlayTables (customer challenges)

    /** Is that table taken: a match on it (running or just finished) or a player waiting there for an opponent? */
    public static boolean tableInUse(BlockPos table) {
        return tableMatch(table) != null || WAITING.containsKey(table);
    }

    /** Is a match running (not yet over) on that table? */
    public static boolean tableMatchRunning(BlockPos table) {
        Match m = tableMatch(table);
        return m != null && !m.over;
    }

    /** Is that player in a match (any mode) or in the league queue? */
    public static boolean busy(UUID player) {
        return BY_PLAYER.containsKey(player) || QUEUE.contains(player);
    }

    /** Starts loading the engine in the background (so an accepted challenge can begin right away). */
    public static void warmUp() {
        if (!PtcgEngine.ready() && !PtcgEngine.failed()) PtcgEngine.preload().thenRun(PtcgCards::loadBrief);
    }

    /**
     * 1.0: a shop customer challenged the player at a play table and they accepted: a friendly match against the AI at
     * that level, shown with the customer's name (same rewards as a friendly match against the house, no penalty).
     * false = it could not start now (busy, table taken or the engine still loading: the player was told why).
     */
    public static boolean startNpcMatch(ServerPlayerEntity p, BlockPos table, String npcName, int level) {
        return startNpcMatch(p, table, npcName, level, -1, null);
    }

    /**
     * 1.0 beta 3 (Wandering Duelist): same as above, but the hook is told the result and pays its own stakes (no house
     * reward); shownReward is the prize shown on the result screen. When this returns true the hook is always called
     * later (with -2 if the match then could not start).
     */
    public static boolean startNpcMatch(ServerPlayerEntity p, BlockPos table, String npcName, int level, long shownReward, @Nullable NpcMatchHook hook) {
        BlockPos t = table.toImmutable();
        if (BY_PLAYER.containsKey(p.getUuid())) return false;
        if (tableMatch(t) != null) {
            p.sendMessage(Text.translatable("tcgshop.duel.table_busy").formatted(Formatting.RED), true);
            return false;
        }
        if (!engineReady(p)) return false;
        WAITING.values().removeIf(x -> x.equals(p.getUuid()));
        QUEUE.remove(p.getUuid());
        startAi(p, t, Mode.CASUAL_AI, level, npcName, hook, shownReward);
        return true;
    }

    private static void startAi(ServerPlayerEntity p, @Nullable BlockPos table, Mode mode, int level) {
        startAi(p, table, mode, level, null);
    }

    /** opponentName: shown instead of the AI level name (1.0: a shop customer who challenged you at a table). */
    private static void startAi(ServerPlayerEntity p, @Nullable BlockPos table, Mode mode, int level, @Nullable String opponentName) {
        startAi(p, table, mode, level, opponentName, null, -1);
    }

    private static void startAi(ServerPlayerEntity p, @Nullable BlockPos table, Mode mode, int level, @Nullable String opponentName,
                                @Nullable NpcMatchHook hook, long shownReward) {
        UUID who = p.getUuid();
        deckFor(p, deck -> {
            if (deck == null || BY_PLAYER.containsKey(p.getUuid())) { fireHook(hook, who, -2); return; }
            if (opponentName != null && table != null && tableMatch(table) != null) { fireHook(hook, who, -2); return; } // someone took the table meanwhile
            Match m = new Match("m" + (++counter), p.getServerWorld(), table, mode, Math.max(0, Math.min(3, level)));
            m.hook = hook;
            m.shownReward = shownReward;
            m.players[0] = p.getUuid();
            m.names[0] = p.getName().getString();
            m.names[1] = opponentName != null ? opponentName : Text.translatable("tcgshop.ptcg.ai_name." + LEVELS[m.level]).getString();
            // Champion AI always and Veteran AI half the time: a top metagame deck
            List<MetaDeck> metas = metaDecks();
            java.util.Random r = new java.util.Random();
            if (!metas.isEmpty() && (m.level >= 3 || (m.level == 2 && r.nextBoolean()))) {
                MetaDeck md = metas.get(r.nextInt(metas.size()));
                m.names[1] = m.names[1] + " · " + md.name();
                start(m, deck, null, md.cards());
                return;
            }
            start(m, deck, null);
        });
    }

    private static void startPvp(ServerPlayerEntity a, ServerPlayerEntity b, @Nullable BlockPos table, Mode mode) {
        deckFor(a, d1 -> {
            if (d1 == null) { b.sendMessage(Text.translatable("tcgshop.ptcg.opp_no_deck", a.getDisplayName()).formatted(Formatting.RED), false); return; }
            deckFor(b, d2 -> {
                if (d2 == null) { a.sendMessage(Text.translatable("tcgshop.ptcg.opp_no_deck", b.getDisplayName()).formatted(Formatting.RED), false); return; }
                if (BY_PLAYER.containsKey(a.getUuid()) || BY_PLAYER.containsKey(b.getUuid()) || (table != null && tableMatch(table) != null)) return;
                Match m = new Match("m" + (++counter), b.getServerWorld(), table, mode, 1);
                m.players[0] = a.getUuid();
                m.names[0] = a.getName().getString();
                m.players[1] = b.getUuid();
                m.names[1] = b.getName().getString();
                start(m, d1, d2);
            });
        });
    }

    private static void start(Match m, List<String> deck1, @Nullable List<String> deck2) { start(m, deck1, deck2, null); }

    private static void start(Match m, List<String> deck1, @Nullable List<String> deck2, @Nullable List<String> aiDeck) {
        MATCHES.put(m.id, m);
        MinecraftServer srv = m.world.getServer();
        m.gradedCards[0] = gradedInDeck(m.players[0] == null ? null : srv.getPlayerManager().getPlayer(m.players[0]), deck1);
        if (deck2 != null) m.gradedCards[1] = gradedInDeck(m.players[1] == null ? null : srv.getPlayerManager().getPlayer(m.players[1]), deck2);
        for (UUID u : m.players) if (u != null) { BY_PLAYER.put(u, m); QUEUE.remove(u); }
        m.pending++;
        MinecraftServer server = m.world.getServer();
        PtcgEngine.call(api -> {
            List<String> d2 = deck2 != null ? deck2 : aiDeck != null && PtcgEngine.str(api, "validateDeck", GSON.toJson(aiDeck)).isEmpty() ? aiDeck : GSON.fromJson(PtcgEngine.str(api, "buildDeck", housePool(), (int) (System.nanoTime() & 0xFFFFFF),
                    LEVEL_CHAOS[m.level]), new TypeToken<List<String>>() {}.getType());
            JsonObject p1 = new JsonObject(), p2 = new JsonObject();
            p1.addProperty("id", 1);
            p1.addProperty("name", m.names[0]);
            p1.add("deck", GSON.toJsonTree(deck1));
            p2.addProperty("id", 2);
            p2.addProperty("name", m.names[1]);
            p2.add("deck", GSON.toJsonTree(d2));
            p2.addProperty("bot", m.ai());
            return PtcgEngine.str(api, "create", m.id, p1.toString(), p2.toString());
        }).whenComplete((err, ex) -> server.execute(() -> {
            m.pending--;
            if (ex != null || (err != null && !err.isEmpty())) {
                for (UUID u : m.players) {
                    ServerPlayerEntity pl = u == null ? null : server.getPlayerManager().getPlayer(u);
                    if (pl != null) pl.sendMessage(Text.translatable("tcgshop.ptcg.start_failed", ex != null ? ex.getMessage() : err).formatted(Formatting.RED), false);
                }
                remove(m);
                return;
            }
            m.started = true;
            m.lastChange = m.world.getTime();
            for (UUID u : m.players) {
                ServerPlayerEntity pl = u == null ? null : server.getPlayerManager().getPlayer(u);
                if (pl != null) pl.playSoundToPlayer(SoundEvents.ITEM_BOOK_PAGE_TURN, SoundCategory.PLAYERS, 1f, 0.9f);
            }
            refresh(m, true);
        }));
    }

    // ------------------------------------------------------------------ decks

    private static String poolCache;

    /** AI pool: every cobbletcg card the engine has implemented (so they always have an image). */
    private static String housePool() {
        if (poolCache == null) {
            JsonObject o = new JsonObject();
            for (var id : net.minecraft.registry.Registries.ITEM.getIds()) {
                String f = CardInfo.isCard(id) ? PtcgCards.engineName(id.toString()) : null;
                if (f != null) o.addProperty(f, 4);
            }
            o.addProperty("__house", 1);
            poolCache = o.toString();
        }
        return poolCache;
    }

    /** Your cards playable in the real TCG: engine name -> copies (General Binder you carry, your Personal Binders + inventory). */
    public static Map<String, Integer> pool(ServerPlayerEntity p) {
        Map<String, Integer> out = new LinkedHashMap<>();
        BinderData d = CollectionService.ownedBinder(p);
        if (d != null) for (var e : d.counts.entrySet()) {
            String f = PtcgCards.engineName(e.getKey());
            if (f != null) out.merge(f, e.getValue(), Integer::sum);
        }
        // 1.0: print variants and graded cards are filed in the General Binder as full stacks, and cards can live in a
        // Personal Binder (the starter one); before 1.0 both stayed in the inventory, so they must still count as owned
        if (d != null) for (BinderData.Special s : d.specials) {
            String f = PtcgCards.engineName(CardInfo.id(s.stack).toString());
            if (f != null && s.count > 0) out.merge(f, s.count, Integer::sum);
        }
        for (ItemStack pb : personalBinders(p)) {
            for (String id : pb.getOrDefault(dev.tcgshop.item.TcgItems.PERSONAL_BINDER_DATA, dev.tcgshop.binder.PersonalBinderData.DEFAULT).slots()) {
                if (id.isEmpty() || id.startsWith("wish:")) continue;
                String f = PtcgCards.engineName(id);
                if (f != null) out.merge(f, 1, Integer::sum);
            }
        }
        for (int i = 0; i < p.getInventory().size(); i++) {
            ItemStack st = p.getInventory().getStack(i);
            if (!CardInfo.isCard(st)) continue;
            String f = PtcgCards.engineName(CardInfo.id(st).toString());
            if (f != null) out.merge(f, st.getCount(), Integer::sum);
        }
        return out;
    }

    /** Graded cards in your inventory: engine name -> {copies, best grade}. */
    public static Map<String, int[]> graded(ServerPlayerEntity p) {
        Map<String, int[]> out = new HashMap<>();
        for (int i = 0; i < p.getInventory().size(); i++) {
            ItemStack st = p.getInventory().getStack(i);
            if (!CardInfo.isCard(st) || !Grading.isGraded(st)) continue;
            String f = PtcgCards.engineName(CardInfo.id(st).toString());
            if (f == null) continue;
            int[] g = out.computeIfAbsent(f, k -> new int[2]);
            g[0] += st.getCount();
            g[1] = Math.max(g[1], Grading.grade(st));
        }
        // 1.0: graded slabs filed in the General Binder (specials) or in a Personal Binder pocket
        List<ItemStack> slabs = new ArrayList<>();
        BinderData d = CollectionService.ownedBinder(p);
        if (d != null) for (BinderData.Special s : d.specials) if (s.count > 0) slabs.add(s.stack.copyWithCount(s.count));
        for (ItemStack pb : personalBinders(p))
            slabs.addAll(pb.getOrDefault(dev.tcgshop.item.TcgItems.PERSONAL_BINDER_DATA, dev.tcgshop.binder.PersonalBinderData.DEFAULT).extra().values());
        for (ItemStack st : slabs) {
            if (!CardInfo.isCard(st) || !Grading.isGraded(st)) continue;
            String f = PtcgCards.engineName(CardInfo.id(st).toString());
            if (f == null) continue;
            int[] g = out.computeIfAbsent(f, k -> new int[2]);
            g[0] += st.getCount();
            g[1] = Math.max(g[1], Grading.grade(st));
        }
        return out;
    }

    /** Personal Binders the player carries that are bound to them (their pockets hold real cards). */
    private static List<ItemStack> personalBinders(ServerPlayerEntity p) {
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack st : dev.tcgshop.binder.PersonalBinders.carried(p, null)) {
            dev.tcgshop.collection.BinderTag tag = st.get(dev.tcgshop.item.TcgItems.BINDER_TAG);
            if (tag != null && tag.owner().equals(p.getUuid())) out.add(st);
        }
        return out;
    }

    /** Grade of the deck cards you have a graded copy of (the table shows them in their slab). */
    private static JsonObject gradedInDeck(@Nullable ServerPlayerEntity p, List<String> deck) {
        JsonObject o = new JsonObject();
        if (p == null) return o;
        Map<String, int[]> g = graded(p);
        for (String f : deck) if (g.containsKey(f)) o.addProperty(f, g.get(f)[1]);
        return o;
    }

    /** Validates ownership (basic Energies are free). Returns null if valid, or the missing card. */
    @Nullable
    public static String ownership(List<String> deck, Map<String, Integer> pool) {
        Map<String, Integer> used = new HashMap<>();
        for (String f : deck) {
            if (PtcgCards.isBasicEnergy(f)) continue;
            if (used.merge(f, 1, Integer::sum) > pool.getOrDefault(f, 0)) return f;
        }
        return null;
    }

    private interface DeckCallback { void accept(@Nullable List<String> deck); }

    /**
     * Checks the deck before playing. If you have a saved one and it's invalid, tells you exactly why (and doesn't play);
     * if you have none saved, uses an automatic one built from your cards.
     */
    private static void deckFor(ServerPlayerEntity p, DeckCallback cb) {
        Map<String, Integer> pool = pool(p);
        League.Entry e = League.get(p.getServer()).entry(p.getUuid(), p.getName().getString());
        List<String> saved = new ArrayList<>(e.deck);
        MinecraftServer server = p.getServer();
        PtcgEngine.call(api -> {
            if (!saved.isEmpty()) {
                String own = ownership(saved, pool);
                String v = own != null ? "NOT_OWNED:" + own : PtcgEngine.str(api, "validateDeck", GSON.toJson(saved));
                return new Object[]{v.isEmpty() ? saved : null, v, false};
            }
            if (pool.isEmpty()) return new Object[]{null, "NO_CARDS", true};
            List<String> auto = GSON.fromJson(PtcgEngine.str(api, "buildDeck", GSON.toJson(pool), p.getUuid().hashCode()),
                    new TypeToken<List<String>>() {}.getType());
            String v = PtcgEngine.str(api, "validateDeck", GSON.toJson(auto));
            return new Object[]{v.isEmpty() ? auto : null, v, true};
        }).whenComplete((r, ex) -> server.execute(() -> {
            if (ex != null || r == null) {
                p.sendMessage(Text.translatable("tcgshop.ptcg.no_deck").formatted(Formatting.RED), false);
                cb.accept(null);
                return;
            }
            @SuppressWarnings("unchecked") List<String> deck = (List<String>) r[0];
            String why = (String) r[1];
            if (deck == null) {
                p.sendMessage(Text.translatable("tcgshop.ptcg.deck_invalid", reason(why)).formatted(Formatting.RED), false);
                p.sendMessage(Text.translatable("tcgshop.ptcg.deck_fix_hint").formatted(Formatting.GRAY), false);
                cb.accept(null);
                return;
            }
            if ((Boolean) r[2]) p.sendMessage(Text.translatable("tcgshop.ptcg.using_auto_deck").formatted(Formatting.GRAY), true);
            cb.accept(deck);
        }));
    }

    /** Readable text of the reason a deck is invalid. */
    public static Text reason(String why) {
        if (why == null || why.isEmpty()) return Text.empty();
        String[] parts = why.split(":", 2);
        return Text.translatable("tcgshop.ptcg.deck_err." + parts[0], parts.length > 1 ? parts[1] : "");
    }

    // ------------------------------------------------------------------ actions

    public static void action(ServerPlayerEntity p, String json) {
        Match m = BY_PLAYER.get(p.getUuid());
        if (m == null || !m.started || m.over) return;
        int seat = m.seat(p.getUuid());
        if (seat < 0) return;
        JsonObject o;
        try { o = JsonParser.parseString(json).getAsJsonObject(); } catch (Exception ex) { return; }
        m.timeouts[seat] = 0;
        if ("suggest".equals(o.has("k") ? o.get("k").getAsString() : "")) { suggest(m, seat); return; }
        run(m, seat, o, p);
    }

    private static void suggest(Match m, int seat) {
        m.pending++;
        MinecraftServer server = m.world.getServer();
        PtcgEngine.call(api -> PtcgEngine.str(api, "suggest", m.id, clientId(seat))).whenComplete((s, ex) -> server.execute(() -> {
            m.pending--;
            m.suggestion[seat] = ex != null || s == null || s.isEmpty() ? "none" : s;
            sendViews(m, false);
            m.suggestion[seat] = "";
        }));
    }

    private static void run(Match m, int seat, JsonObject o, @Nullable ServerPlayerEntity p) {
        int cid = clientId(seat);
        String kind = o.has("k") ? o.get("k").getAsString() : "";
        m.pending++;
        MinecraftServer server = m.world.getServer();
        PtcgEngine.call(api -> switch (kind) {
            case "act" -> PtcgEngine.str(api, "act", m.id, cid, o.get("a").toString());
            case "resolve" -> PtcgEngine.str(api, "resolve", m.id, cid, o.get("id").getAsInt(), o.get("r").toString());
            case "auto" -> PtcgEngine.str(api, "auto", m.id, cid);
            default -> "UNKNOWN_ACTION";
        }).whenComplete((err, ex) -> server.execute(() -> {
            m.pending--;
            if (p != null && (ex != null || (err != null && !err.isEmpty()))) {
                JsonObject v = m.views[seat];
                if (v != null) v.addProperty("error", ex != null ? "ERROR" : err);
                sendViews(m, false);
                if (v != null) v.remove("error");
            }
            m.lastChange = m.world.getTime();
            refresh(m, false);
        }));
    }

    private static void concede(Match m, UUID u) {
        if (m.over || !m.started) { remove(m); return; }
        int seat = m.seat(u);
        if (seat < 0) return;
        JsonObject o = new JsonObject();
        o.addProperty("k", "act");
        JsonObject a = new JsonObject();
        a.addProperty("t", "concede");
        o.add("a", a);
        run(m, seat, o, null);
    }

    // ------------------------------------------------------------------ views

    private static void refresh(Match m, boolean open) {
        m.pending++;
        MinecraftServer server = m.world.getServer();
        PtcgEngine.call(api -> new String[]{PtcgEngine.str(api, "view", m.id, 1), PtcgEngine.str(api, "view", m.id, 2)})
                .whenComplete((v, ex) -> server.execute(() -> {
                    m.pending--;
                    if (ex != null || v == null) return;
                    for (int i = 0; i < 2; i++) {
                        try { m.views[i] = JsonParser.parseString(v[i]).getAsJsonObject(); } catch (Exception ignored) {}
                    }
                    checkOver(m);
                    updateTable(m);
                    sendViews(m, open);
                }));
    }

    /** The house's coin according to its level. */
    private static final String[] BOT_COINS = {"copper", "silver", "ultra", "master", "obsidian"};

    private static void sendViews(Match m, boolean open) {
        MinecraftServer server = m.world.getServer();
        for (int i = 0; i < 2; i++) {
            if (m.players[i] == null || m.views[i] == null) continue;
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(m.players[i]);
            if (p == null || !ServerPlayNetworking.canSend(p, PtcgPayloads.View.ID)) continue;
            JsonObject v = m.views[i];
            v.addProperty("ranked", m.ranked());
            v.addProperty("mode", m.mode.name().toLowerCase());
            v.addProperty("level", m.level);
            v.addProperty("ai", m.ai());
            v.addProperty("idle", IDLE_SECONDS);
            v.addProperty("idleLeft", Math.max(0, IDLE_SECONDS - (int) ((m.world.getTime() - m.lastChange) / 20)));
            v.addProperty("ratingDelta", m.over && m.ranked() ? (winnerSeat(m) == i ? Math.abs(m.ratingDelta) : -Math.abs(m.ratingDelta)) : 0);
            if (m.mode == Mode.RANKED_AI && m.over) v.addProperty("ratingDelta", m.ratingDelta);
            v.addProperty("suggest", m.suggestion[i]);
            v.add("gMe", m.gradedCards[i]);
            v.addProperty("penalty", m.penalty[i]);
            v.add("gOpp", m.gradedCards[1 - i]);
            v.addProperty("reward", m.over && winnerSeat(m) == 0 && m.ai() ? (m.shownReward >= 0 ? m.shownReward : LEVEL_REWARD[m.level]) : 0);
            v.addProperty("coinMe", dev.tcgshop.duel.Cosmetics.coinOf(p));
            v.addProperty("board", dev.tcgshop.duel.Cosmetics.boardOf(p));
            ServerPlayerEntity op = m.players[1 - i] == null ? null : server.getPlayerManager().getPlayer(m.players[1 - i]);
            v.addProperty("coinOpp", op != null ? dev.tcgshop.duel.Cosmetics.coinOf(op) : BOT_COINS[Math.floorMod(m.level, BOT_COINS.length)]);
            ServerPlayNetworking.send(p, new PtcgPayloads.View(v.toString(), open));
        }
    }

    private static int winnerSeat(Match m) {
        JsonObject v = m.views[0];
        if (v == null) return -1;
        int w = v.get("winner").getAsInt();
        return w == 0 || w == 1 ? w : -1;
    }

    private static void checkOver(Match m) {
        if (m.over || m.views[0] == null) return;
        int phase = m.views[0].get("phase").getAsInt(), w = m.views[0].get("winner").getAsInt();
        if (phase != 7 && w == -1) return;
        m.over = true;
        m.endedAt = m.world.getTime();
        MinecraftServer server = m.world.getServer();
        League lg = League.get(server);
        int ws = w == 0 || w == 1 ? w : -1;
        if (ws >= 0) {
            int ls = 1 - ws;
            ServerPlayerEntity wp = m.players[ws] == null ? null : server.getPlayerManager().getPlayer(m.players[ws]);
            if (!m.ai()) {
                if (m.ranked()) {
                    m.ratingDelta = lg.record(m.players[ws], m.names[ws], m.players[ls], m.names[ls]);
                    if (wp != null) CardAdvancements.grant(wp, "duel_ranked_win");
                    penalize(server.getPlayerManager().getPlayer(m.players[ls]), PVP_PENALTY, m, ls);
                }
            } else {
                boolean humanWon = ws == 0;
                ServerPlayerEntity hp = server.getPlayerManager().getPlayer(m.players[0]);
                if (m.mode == Mode.RANKED_AI) m.ratingDelta = lg.recordVsAi(m.players[0], m.names[0], m.level, humanWon);
                else if (humanWon) { lg.entry(m.players[0], m.names[0]).aiWins++; lg.markDirty(); }
                if (!humanWon && m.mode == Mode.RANKED_AI) penalize(hp, LEVEL_PENALTY[m.level], m, 0);
                if (humanWon && hp != null) {
                    if (m.hook == null) { // a hooked NPC match (Wandering Duelist) pays its own stakes
                        long reward = LEVEL_REWARD[m.level];
                        CobbleDollarsBridge.add(hp, reward);
                        hp.sendMessage(Text.translatable("tcgshop.duel.reward", PriceService.format(reward)).formatted(Formatting.GOLD), false);
                    }
                    CardAdvancements.grant(hp, "duel_house_win");
                    if (m.mode == Mode.RANKED_AI) CardAdvancements.grant(hp, "duel_ranked_win");
                }
            }
            for (UUID u : m.players) {
                ServerPlayerEntity pl = u == null ? null : server.getPlayerManager().getPlayer(u);
                if (pl == null) continue;
                int rating = lg.entry(u, pl.getName().getString()).rating;
                if (rating >= League.RANK_AT[3]) CardAdvancements.grant(pl, "duel_gold");
                if (rating >= League.RANK_AT[5]) CardAdvancements.grant(pl, "duel_master");
            }
            if (wp != null) {
                CardAdvancements.grant(wp, "duel_win");
                wp.playSoundToPlayer(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 0.8f, 1f);
            }
            if (m.table != null)
                for (ServerPlayerEntity o : m.world.getPlayers(o -> o.squaredDistanceTo(m.table.toCenterPos()) < 24 * 24))
                    o.sendMessage(Text.translatable("tcgshop.ptcg.result", m.names[ws], m.names[ls]).formatted(Formatting.GOLD), false);
        }
        for (int seat = 0; seat < 2; seat++) {
            UUID u = m.players[seat];
            ServerPlayerEntity pl = u == null ? null : server.getPlayerManager().getPlayer(u);
            if (pl == null) continue;
            CardAdvancements.grant(pl, "duel_play");
            lg.entry(u, pl.getName().getString()).finished(ws == seat, m.ranked());
            lg.markDirty();
            dev.tcgshop.duel.Cosmetics.check(pl);
        }
        if (m.hook != null) { // 1.0 beta 3: the Wandering Duelist settles its stakes
            NpcMatchHook h = m.hook;
            m.hook = null;
            fireHook(h, m.players[0], ws == 0 ? 1 : ws == 1 ? 0 : -1);
        }
    }

    /** Competitive loss: you lose a bit of money (never more than you have). */
    private static void penalize(@Nullable ServerPlayerEntity pl, long amount, Match m, int seat) {
        if (pl == null || !CobbleDollarsBridge.isAvailable()) return;
        long take = Math.min(amount, Math.max(0, CobbleDollarsBridge.getBalance(pl)));
        if (take <= 0) return;
        CobbleDollarsBridge.add(pl, -take);
        m.penalty[seat] = take;
        pl.sendMessage(Text.translatable("tcgshop.ptcg.penalty", PriceService.format(take)).formatted(Formatting.RED), false);
    }

    /** Cards in play (Active + 3 Bench on each side) are shown on the table. */
    private static void updateTable(Match m) {
        if (m.table == null || !(m.world.getBlockEntity(m.table) instanceof DecorBlocks.DecorEntity be) || m.views[0] == null) return;
        be.board.clear();
        for (String side : new String[]{"me", "opp"}) {
            JsonObject pl = m.views[0].has(side) && m.views[0].get(side).isJsonObject() ? m.views[0].getAsJsonObject(side) : null;
            be.board.add(itemFor(pl == null ? null : pl.get("active")));
            JsonArray bench = pl == null ? new JsonArray() : pl.getAsJsonArray("bench");
            for (int i = 0; i < 3; i++) be.board.add(itemFor(i < bench.size() ? bench.get(i) : null));
        }
        be.markDirty();
        m.world.updateListeners(m.table, be.getCachedState(), be.getCachedState(), 3);
    }

    private static String itemFor(@Nullable JsonElement mon) {
        if (mon == null || !mon.isJsonObject()) return "";
        String id = PtcgCards.itemId(mon.getAsJsonObject().get("f").getAsString());
        return id == null ? "" : id;
    }

    private static void remove(Match m) {
        if (m.hook != null) { // removed without a result (start failed or aborted)
            NpcMatchHook h = m.hook;
            m.hook = null;
            fireHook(h, m.players[0], m.started ? -1 : -2);
        }
        MATCHES.remove(m.id);
        for (UUID u : m.players) if (u != null) BY_PLAYER.remove(u, m);
        if (m.table != null && m.world.getBlockEntity(m.table) instanceof DecorBlocks.DecorEntity be) {
            be.board.clear();
            be.markDirty();
            m.world.updateListeners(m.table, be.getCachedState(), be.getCachedState(), 3);
        }
        PtcgEngine.call(api -> { api.run("dispose", m.id); return true; });
    }

    // ------------------------------------------------------------------ loop

    private static void tick(MinecraftServer server) {
        // phone league queue
        if (QUEUE.size() >= 2 && server.getTicks() % 20 == 0) {
            ServerPlayerEntity a = server.getPlayerManager().getPlayer(QUEUE.get(0)), b = server.getPlayerManager().getPlayer(QUEUE.get(1));
            QUEUE.remove(0);
            QUEUE.remove(0);
            if (a != null && b != null) {
                a.sendMessage(Text.translatable("tcgshop.ptcg.match_found", b.getDisplayName()).formatted(Formatting.GREEN), false);
                b.sendMessage(Text.translatable("tcgshop.ptcg.match_found", a.getDisplayName()).formatted(Formatting.GREEN), false);
                startPvp(a, b, null, Mode.QUEUE_PVP);
            }
        }
        if (MATCHES.isEmpty()) return;
        for (Match m : new ArrayList<>(MATCHES.values())) {
            long now = m.world.getTime();
            if (!m.started) continue;
            if (m.over) {
                if (now - m.endedAt > 300 && m.pending == 0) remove(m);
                continue;
            }
            if (m.table != null && !m.world.getBlockState(m.table).isOf(TcgBlocks.PLAY_TABLE)) {
                for (UUID u : m.players) if (u != null) concede(m, u);
                continue;
            }
            if (now % 20 == 0) {
                for (int i = 0; i < 2; i++) {
                    if (m.players[i] == null) continue;
                    ServerPlayerEntity p = server.getPlayerManager().getPlayer(m.players[i]);
                    boolean far = m.table != null && (p == null || p.getServerWorld() != m.world || p.squaredDistanceTo(m.table.toCenterPos()) > 20 * 20);
                    if (p == null || far) {
                        concede(m, m.players[i]);
                        break;
                    }
                }
            }
            if (m.pending > 0) continue;
            if (m.ai() && m.botNext == 0) m.botNext = now + BOT_FIRST_DELAY; // lets the intro and the coin flip be seen
            if (m.ai() && now >= m.botNext) {
                m.pending++;
                m.botNext = now + 10;
                PtcgEngine.call(api -> PtcgEngine.bool(api, "botStep", m.id)).whenComplete((acted, ex) -> server.execute(() -> {
                    m.pending--;
                    if (Boolean.TRUE.equals(acted)) {
                        m.lastChange = m.world.getTime();
                        m.botNext = m.world.getTime() + BOT_STEP_DELAY;
                        refresh(m, false);
                    }
                }));
                continue;
            }
            if (now - m.lastChange > IDLE_SECONDS * 20L) {
                for (int i = 0; i < 2; i++) {
                    JsonObject v = m.views[i];
                    if (m.players[i] == null || v == null) continue;
                    boolean prompt = v.has("prompt") && v.get("prompt").isJsonObject();
                    boolean turn = v.get("myTurn").getAsBoolean() && !(v.has("waiting") && v.get("waiting").getAsBoolean());
                    if (!prompt && !turn) continue;
                    m.timeouts[i]++;
                    if (m.timeouts[i] >= 3) { concede(m, m.players[i]); break; }
                    JsonObject o = new JsonObject();
                    if (prompt) o.addProperty("k", "auto");
                    else {
                        o.addProperty("k", "act");
                        JsonObject a = new JsonObject();
                        a.addProperty("t", "pass");
                        o.add("a", a);
                    }
                    run(m, i, o, null);
                    break;
                }
                m.lastChange = now;
            } else if (now % 100 == 0) sendViews(m, false); // refreshes the idle timer
        }
    }

    // ------------------------------------------------------------------ challenges (Cobblemon wheel / /tcgduel)

    /** Challenges another player to a friendly real-TCG match (no league points). */
    public static void challenge(ServerPlayerEntity from, @Nullable ServerPlayerEntity to) {
        if (to == null || to == from) return;
        if (BY_PLAYER.containsKey(from.getUuid())) { from.sendMessage(Text.translatable("tcgshop.ptcg.challenge.busy_self").formatted(Formatting.RED), true); return; }
        if (BY_PLAYER.containsKey(to.getUuid())) { from.sendMessage(Text.translatable("tcgshop.ptcg.challenge.busy", to.getDisplayName()).formatted(Formatting.RED), true); return; }
        if (!engineReady(from)) return;
        deckFor(from, deck -> {
            if (deck == null) return;
            CHALLENGES.put(to.getUuid(), new Object[]{from.getUuid(), from.getServer().getTicks() + 20 * 60});
            from.sendMessage(Text.translatable("tcgshop.ptcg.challenge.sent", to.getDisplayName()).formatted(Formatting.AQUA), false);
            Text accept = Text.translatable("tcgshop.ptcg.challenge.accept").styled(s -> s.withColor(Formatting.GREEN).withBold(true)
                    .withClickEvent(new net.minecraft.text.ClickEvent(net.minecraft.text.ClickEvent.Action.RUN_COMMAND, "/tcgduel accept"))
                    .withHoverEvent(new net.minecraft.text.HoverEvent(net.minecraft.text.HoverEvent.Action.SHOW_TEXT, Text.translatable("tcgshop.ptcg.challenge.accept_hint"))));
            Text decline = Text.translatable("tcgshop.ptcg.challenge.decline").styled(s -> s.withColor(Formatting.RED)
                    .withClickEvent(new net.minecraft.text.ClickEvent(net.minecraft.text.ClickEvent.Action.RUN_COMMAND, "/tcgduel decline")));
            to.sendMessage(Text.translatable("tcgshop.ptcg.challenge.received", from.getDisplayName()).formatted(Formatting.GOLD), false);
            to.sendMessage(Text.literal("   ").append(accept).append(Text.literal("   ")).append(decline), false);
            to.playSoundToPlayer(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), SoundCategory.PLAYERS, 0.8f, 1.4f);
        });
    }

    public static void answerChallenge(ServerPlayerEntity to, boolean accept) {
        Object[] c = CHALLENGES.remove(to.getUuid());
        ServerPlayerEntity from = c == null ? null : to.getServer().getPlayerManager().getPlayer((UUID) c[0]);
        if (c == null || from == null || to.getServer().getTicks() > (Integer) c[1]) {
            to.sendMessage(Text.translatable("tcgshop.ptcg.challenge.none").formatted(Formatting.GRAY), true);
            return;
        }
        if (!accept) {
            from.sendMessage(Text.translatable("tcgshop.ptcg.challenge.declined", to.getDisplayName()).formatted(Formatting.GRAY), false);
            to.sendMessage(Text.translatable("tcgshop.ptcg.challenge.you_declined").formatted(Formatting.GRAY), true);
            return;
        }
        if (!engineReady(to)) return;
        from.sendMessage(Text.translatable("tcgshop.ptcg.challenge.accepted", to.getDisplayName()).formatted(Formatting.GREEN), false);
        startPvp(from, to, null, Mode.CHALLENGE);
    }

    // ------------------------------------------------------------------ league (phone)

    public static void league(ServerPlayerEntity p, String json) {
        JsonObject o;
        try { o = JsonParser.parseString(json).getAsJsonObject(); } catch (Exception ex) { return; }
        String a = o.has("a") ? o.get("a").getAsString() : "sync";
        PtcgCards.loadBrief(); // the engine loads the first time someone opens the League or a table
        switch (a) {
            case "play" -> {
                Match mine = BY_PLAYER.get(p.getUuid());
                if (mine != null && mine.over && mine.pending == 0) { remove(mine); mine = null; } // rematch
                if (mine != null) { sendViews(mine, true); return; }
                if (!engineReady(p)) return;
                String mode = o.has("mode") ? o.get("mode").getAsString() : "casual";
                int level = o.has("level") ? o.get("level").getAsInt() : 1;
                switch (mode) {
                    case "ranked_ai" -> {
                        int r = League.get(p.getServer()).entry(p.getUuid(), p.getName().getString()).rating;
                        if (!League.aiLevelAllowed(r, level)) {
                            p.sendMessage(Text.translatable("tcgshop.league.level_locked",
                                    Text.translatable("tcgshop.ptcg.ui.level." + Math.max(0, Math.min(3, level))),
                                    Text.translatable("tcgshop.league.rank." + League.rank(r))).formatted(Formatting.RED), false);
                            return;
                        }
                        startAi(p, null, Mode.RANKED_AI, level);
                    }
                    case "queue" -> deckFor(p, deck -> {
                        if (deck == null) return;
                        if (!QUEUE.contains(p.getUuid())) QUEUE.add(p.getUuid());
                        p.sendMessage(Text.translatable("tcgshop.ptcg.queue_joined", QUEUE.size()).formatted(Formatting.AQUA), true);
                        leagueSync(p, "QUEUED", null);
                    });
                    case "cancel_queue" -> {
                        QUEUE.remove(p.getUuid());
                        leagueSync(p, "UNQUEUED", null);
                    }
                    default -> startAi(p, null, Mode.CASUAL_AI, level);
                }
                return;
            }
            case "cosmetic" -> {
                String kind = o.has("kind") ? o.get("kind").getAsString() : "", id = o.has("id") ? o.get("id").getAsString() : "";
                if (dev.tcgshop.duel.Cosmetics.equip(p, kind, id))
                    p.playSoundToPlayer(SoundEvents.ITEM_ARMOR_EQUIP_GOLD.value(), SoundCategory.PLAYERS, 0.8f, 1.2f);
                leagueSync(p, "sync", null);
            }
            case "challenge" -> {
                try {
                    challenge(p, p.getServer().getPlayerManager().getPlayer(UUID.fromString(o.get("target").getAsString())));
                } catch (Exception ignored) {}
            }
            default -> leagueSync(p, a, o.has("deck") ? o.getAsJsonArray("deck") : null);
        }
    }

    private static void leagueSync(ServerPlayerEntity p, String a, @Nullable JsonArray deckArr) {
        League lg = League.get(p.getServer());
        League.Entry e = lg.entry(p.getUuid(), p.getName().getString());
        Map<String, Integer> pool = pool(p);
        Map<String, int[]> gradedPool = graded(p);
        MinecraftServer server = p.getServer();
        List<String> wanted = new ArrayList<>();
        if (deckArr != null) for (JsonElement el : deckArr) wanted.add(el.getAsString());
        PtcgEngine.call(api -> {
            String msg = "";
            List<String> deck = new ArrayList<>(e.deck);
            if (a.equals("save") || a.equals("check")) {
                String own = ownership(wanted, pool);
                String v = own != null ? "NOT_OWNED:" + own : PtcgEngine.str(api, "validateDeck", GSON.toJson(wanted));
                if (a.equals("check")) return new Object[]{wanted, "CHECK", v};
                if (!v.isEmpty()) msg = v;
                else { deck = wanted; msg = "SAVED"; }
            } else if (a.equals("auto") && !pool.isEmpty()) {
                // with cards already placed in the deck, auto-build fills the rest around them (evolution lines, type, Trainers)
                List<String> seeds = new ArrayList<>();
                Map<String, Integer> left = new HashMap<>(pool);
                for (String f : wanted) {
                    if (PtcgCards.isBasicEnergy(f)) continue;
                    if (left.getOrDefault(f, 0) > 0) { seeds.add(f); left.merge(f, -1, Integer::sum); }
                }
                deck = GSON.fromJson(PtcgEngine.str(api, "buildDeck", GSON.toJson(pool), (int) System.nanoTime(), -1.0, GSON.toJson(seeds)),
                        new TypeToken<List<String>>() {}.getType());
                msg = seeds.isEmpty() ? "AUTO" : "AUTO_SEED";
            } else if (a.equals("clear")) {
                deck = new ArrayList<>();
                msg = "CLEARED";
            } else if (!a.equals("sync")) msg = a;
            String valid = deck.isEmpty() ? "EMPTY" : ownership(deck, pool) != null ? "NOT_OWNED:" + ownership(deck, pool)
                    : PtcgEngine.str(api, "validateDeck", GSON.toJson(deck));
            return new Object[]{deck, msg, valid};
        }).whenComplete((r, ex) -> server.execute(() -> {
            if (ex != null || r == null) {
                p.sendMessage(Text.translatable(PtcgEngine.failed() ? "tcgshop.ptcg.engine_failed" : "tcgshop.ptcg.loading").formatted(Formatting.GRAY), true);
                return;
            }
            @SuppressWarnings("unchecked") List<String> deck = (List<String>) r[0];
            String msg = (String) r[1], valid = (String) r[2];
            boolean check = msg.equals("CHECK");
            if (msg.equals("SAVED") || msg.startsWith("AUTO") || msg.equals("CLEARED")) {
                e.deck.clear();
                e.deck.addAll(deck);
                lg.markDirty();
            }
            JsonObject out = new JsonObject();
            out.addProperty("rating", e.rating);
            out.addProperty("wins", e.wins);
            out.addProperty("losses", e.losses);
            out.addProperty("aiWins", e.aiWins);
            out.addProperty("msg", msg);
            out.addProperty("valid", valid);
            out.addProperty("check", check);
            out.addProperty("queued", QUEUE.contains(p.getUuid()));
            out.addProperty("queueSize", QUEUE.size());
            out.addProperty("inMatch", BY_PLAYER.containsKey(p.getUuid()));
            out.add("deck", GSON.toJsonTree(check ? e.deck : deck));
            if (check) out.add("checked", GSON.toJsonTree(deck));
            JsonArray levels = new JsonArray();
            for (int i = 0; i < LEVELS.length; i++) {
                JsonObject l = new JsonObject();
                l.addProperty("key", LEVELS[i]);
                l.addProperty("rating", LEVEL_RATING[i]);
                l.addProperty("reward", LEVEL_REWARD[i]);
                l.addProperty("gain", League.AI_WIN[i]);
                l.addProperty("loss", League.AI_LOSS[i]);
                l.addProperty("allowed", League.aiLevelAllowed(e.rating, i));
                levels.add(l);
            }
            out.add("levels", levels);
            JsonArray poolArr = new JsonArray();
            for (var en : pool.entrySet()) {
                JsonObject b = PtcgCards.brief(en.getKey());
                JsonObject c = b == null ? new JsonObject() : b.deepCopy();
                c.addProperty("f", en.getKey());
                c.addProperty("count", Math.min(4, en.getValue()));
                int[] gp = gradedPool.get(en.getKey());
                if (gp != null) { c.addProperty("graded", gp[0]); c.addProperty("grade", gp[1]); }
                String item = PtcgCards.itemId(en.getKey());
                if (item != null) c.addProperty("item", item);
                poolArr.add(c);
            }
            out.add("pool", poolArr);
            JsonArray en = new JsonArray();
            for (JsonObject x : PtcgCards.ENERGIES) en.add(x);
            out.add("energies", en);
            JsonArray top = new JsonArray();
            for (var t : lg.top(10)) {
                JsonObject r1 = new JsonObject();
                r1.addProperty("name", t.getValue().name);
                r1.addProperty("rating", t.getValue().rating);
                r1.addProperty("wins", t.getValue().wins);
                r1.addProperty("losses", t.getValue().losses);
                top.add(r1);
            }
            out.add("top", top);
            dev.tcgshop.duel.Cosmetics.check(p);
            out.add("cos", dev.tcgshop.duel.Cosmetics.json(p));
            if (ServerPlayNetworking.canSend(p, PtcgPayloads.League.ID)) ServerPlayNetworking.send(p, new PtcgPayloads.League(out.toString()));
        }));
    }
}

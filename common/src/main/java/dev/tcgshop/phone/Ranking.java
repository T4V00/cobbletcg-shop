package dev.tcgshop.phone;

import dev.masterball.api.platform.Net;
import dev.masterball.api.platform.event.PlayerEvents;
import dev.masterball.api.platform.event.ServerEvents;
import dev.tcgshop.TcgShop;
import dev.tcgshop.TcgSounds;
import dev.tcgshop.economy.CobbleDollarsBridge;
import dev.tcgshop.shop.ShopTier;
import dev.tcgshop.shop.ShopZones;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * 1.1 phone «Ranking» app: the goal of the mod is to earn more than <b>Don Cobbledome, owner of the Cobbledale
 * Cobbledome</b>. The ranking mixes 100 fictional shop owners (no structures, they only exist in the phone) with every
 * real player of the world, ordered by capital:
 * <ul>
 *   <li>ranks 1-10: famous tycoons with very high capitals; Don Cobbledome is always first with exactly 100 trillion;</li>
 *   <li>ranks 11-100: ordinary shop owners, from about 1.5 billion down to about 25,000 (where a new player enters);</li>
 *   <li>every fictional capital and profit % drifts a little each in-game day, deterministically from the world seed
 *       (the same world and day always give the same table).</li>
 * </ul>
 * <b>Player capital</b> = CobbleDollars in the wallet + money in the registers of all your shops (dormant ones too) + the
 * value of your shops (the price of each shop's tier) + assets: the market value of the stock on your shops' displays
 * (sealed boxes included; as last seen by ShopLife) and of the cards in your General Binders (graded slabs at their
 * graded value) - what you still owe the Wholesaler. The wallet of an offline player is the last one seen. Profit % =
 * (revenue - costs) / revenue of all your shops.
 * <p>
 * Ordinary owners (ranks 11-100) get unique generated names (a collision is re-rolled). The app's request is limited to
 * one every {@link #ASK_COOLDOWN} ticks per player (each one scans every shop and binder of that player).
 * <p>
 * Every minute the capital of online players is refreshed; climbing to a better rank than ever before shows a phone
 * notice, and passing Don Cobbledome is announced to the whole server (once per player). Stored in the Overworld:
 * data/tcgshop_ranking.dat. The app asks with PhonePayloads.Act a = "ranking"; the answer is PhonePayloads.Apps k = "ranking".
 */
public final class Ranking extends SavedData {
    /** Fictional owners in the table. */
    public static final int NPCS = 100;
    /** Least ticks between two Ranking app requests of one player (review fix: a spammed request is ignored). */
    public static final int ASK_COOLDOWN = 40;
    private static final Map<UUID, Integer> LAST_ASK = new HashMap<>();
    /** Generated names of the ordinary owners, per world seed (unique within a world). */
    private static final Map<Long, String[]> NAMES = new HashMap<>();
    /** Don Cobbledome's capital: 100 trillion, never drifts. */
    public static final long DON_CAPITAL = 100_000_000_000_000L;
    /** Capitals of ranks 2-10 before the daily drift. */
    static final long[] TOP_BASE = {2_400_000_000_000L, 910_000_000_000L, 420_000_000_000L, 175_000_000_000L, 76_000_000_000L,
            31_000_000_000L, 13_500_000_000L, 6_800_000_000L, 3_300_000_000L};
    /** Capital (before drift) of rank 11 and of rank 100. */
    static final double FIRST_COMMON = 1.5e9, LAST_COMMON = 25_000;
    /** Base profit % of the top 10 (Don first). */
    static final double[] TOP_PROFIT = {61, 44, 39, 35, 31, 28, 26, 24, 22, 20};

    private static final String[] FIRST = {"Alba", "Bruno", "Carla", "Dario", "Elena", "Fabio", "Gala", "Hugo", "Irene", "Joel", "Kira", "Leo",
            "Marta", "Nico", "Olga", "Pablo", "Quinn", "Rosa", "Saul", "Tania", "Ulises", "Vera", "Walter", "Ximena", "Yago", "Zoe", "Abel", "Bea",
            "Ciro", "Dana", "Emil", "Fiona", "Gus", "Hilda", "Ivan", "Jade", "Kai", "Luna", "Milo", "Nora", "Oscar", "Paz", "Remy", "Sara", "Teo",
            "Uma", "Victor", "Wendy", "Axel", "Ines"};
    private static final String[] LAST = {"Brightwood", "Copperfield", "Dunmore", "Ellery", "Fairbanks", "Greystone", "Hollins", "Ironside",
            "Jarvis", "Kettering", "Lockhart", "Marlowe", "Northam", "Oakley", "Pemberton", "Quill", "Redfern", "Sutton", "Thorne", "Underhill",
            "Vance", "Whitlock", "Yardley", "Zeller", "Ashby", "Blackwell", "Crane", "Delacroix", "Everett", "Finch"};

    /** One row of the table: a fictional owner (npc >= 0) or a real player (npc = -1). */
    public record Row(int npc, UUID player, String name, long capital, double profit) {}

    /** What the world remembers of each real player. */
    static final class Snap {
        String name = "";
        long wallet, capital, revenue, costs;
        int bestRank = Integer.MAX_VALUE;
        boolean beatDon;

        CompoundTag write() {
            CompoundTag c = new CompoundTag();
            c.putString("n", name);
            c.putLong("w", wallet);
            c.putLong("c", capital);
            c.putLong("r", revenue);
            c.putLong("k", costs);
            c.putInt("b", bestRank);
            c.putBoolean("d", beatDon);
            return c;
        }

        static Snap read(CompoundTag c) {
            Snap s = new Snap();
            s.name = c.getString("n");
            s.wallet = c.getLong("w");
            s.capital = c.getLong("c");
            s.revenue = c.getLong("r");
            s.costs = c.getLong("k");
            s.bestRank = c.contains("b") ? c.getInt("b") : Integer.MAX_VALUE;
            s.beatDon = c.getBoolean("d");
            return s;
        }
    }

    private static final Factory<Ranking> TYPE = new Factory<>(Ranking::new, Ranking::load, null);
    private final Map<UUID, Snap> players = new HashMap<>();

    private Ranking() {}

    public static Ranking get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE, "tcgshop_ranking");
    }

    static void register() {
        ServerEvents.TICK_END.register(s -> { if (s.getTickCount() % 1200 == 600) tick(s); });
        PlayerEvents.JOIN.register(p -> p.server.execute(() -> refresh(p, false)));
        PlayerEvents.LEAVE.register(p -> LAST_ASK.remove(p.getUUID()));
        ServerEvents.STOPPED.register(s -> { LAST_ASK.clear(); NAMES.clear(); });
    }

    /** The app asks for the table (client action a = "ranking"): at most once every ASK_COOLDOWN ticks. */
    public static void request(ServerPlayer p) {
        int now = p.getServer().getTickCount();
        Integer last = LAST_ASK.get(p.getUUID());
        if (last != null && now >= last && now - last < ASK_COOLDOWN) return; // the app keeps the table it has
        LAST_ASK.put(p.getUUID(), now);
        send(p);
    }

    // ------------------------------------------------------------------ fictional owners

    /** Capital of fictional owner i (0 = Don Cobbledome, 1-9 the other tycoons, 10-99 ordinary owners) on that day. */
    public static long npcCapital(long worldSeed, int i, long day) {
        if (i == 0) return DON_CAPITAL;
        double base = i < 10 ? TOP_BASE[i - 1]
                : Math.exp(Math.log(FIRST_COMMON) + (Math.log(LAST_COMMON) - Math.log(FIRST_COMMON)) * (i - 10) / 89.0);
        Random r = new Random(worldSeed * 31 + i * 0x9E3779B97F4A7C15L);
        double jitter = i < 10 ? 1 : 0.82 + 0.36 * r.nextDouble();
        double trend = (-0.25 + 0.75 * r.nextDouble()) * Math.tanh(Math.max(0, day) / 300.0); // slow long-term growth (or decline)
        double wave = 0;
        for (int k = 0; k < 3; k++) { // market swings: three slow waves of a few percent
            double period = 6 + r.nextDouble() * 34, phase = r.nextDouble() * Math.PI * 2, amp = 0.015 + r.nextDouble() * 0.035;
            wave += amp * Math.sin(day * Math.PI * 2 / period + phase);
        }
        long v = Math.round(base * jitter * Math.exp(trend + wave));
        return Math.max(1_000, Math.min(DON_CAPITAL - 1, v)); // nobody but Don reaches 100 trillion
    }

    /** Profit % of fictional owner i on that day. */
    public static double npcProfit(long worldSeed, int i, long day) {
        Random r = new Random(worldSeed * 17 + i * 0xC2B2AE3D27D4EB4FL + 7);
        double base = i < 10 ? TOP_PROFIT[i] : 2 + 30 * r.nextDouble();
        double swing = (i == 0 ? 1.5 : 5) * Math.sin(day * Math.PI * 2 / (9 + r.nextDouble() * 20) + r.nextDouble() * 6.3);
        return Math.round((base + swing) * 10) / 10.0;
    }

    /** Display name of fictional owner i: a translation key for the top 10, a generated name for the rest. */
    public static String npcName(long worldSeed, int i) {
        if (i < 10) return PhoneApps.KEY_ARG + "tcgshop.rank.npc." + i;
        if (i >= NPCS) return "";
        String[] names;
        synchronized (NAMES) {
            names = NAMES.computeIfAbsent(worldSeed, Ranking::generateNames);
        }
        return names[i];
    }

    /** Names of owners 10-99 for a world: seeded per index like before, re-rolled on a collision so all are unique. */
    static String[] generateNames(long worldSeed) {
        String[] out = new String[NPCS];
        java.util.Set<String> used = new java.util.HashSet<>();
        for (int i = 10; i < NPCS; i++) {
            Random r = new Random(worldSeed ^ (i * 0x5DEECE66DL + 11));
            String n;
            int tries = 0;
            do {
                n = FIRST[r.nextInt(FIRST.length)] + " " + LAST[r.nextInt(LAST.length)];
            } while (!used.add(n) && ++tries < 10_000);
            out[i] = n;
        }
        return out;
    }

    // ------------------------------------------------------------------ players

    /** Capital parts of a player: {registers, shop value, loan owed, display stock value, binder card value}. */
    static long[] parts(MinecraftServer server, UUID id) {
        long registers = 0, shops = 0, stock = 0;
        for (ServerLevel w : server.getAllLevels())
            for (ShopZones.Zone z : ShopZones.get(w).allAndDormant()) {
                if (!z.owner.equals(id)) continue;
                registers += Math.max(0, z.balance);
                shops += ShopTier.byRank(z.tier).price;
                stock += Math.max(0, z.stockValue);
            }
        Loans.Credit cr = Loans.peek(server, id);
        long owed = cr != null && cr.loan != null ? cr.loan.owed : 0;
        return new long[]{registers, shops, owed, stock, binderValue(server, id)};
    }

    /** Market value of every card in the General Binders that player owns (print variants and graded slabs included). */
    static long binderValue(MinecraftServer server, UUID id) {
        long v = 0;
        Map<String, Long> unit = new HashMap<>();
        for (dev.tcgshop.collection.BinderData b : dev.tcgshop.collection.BinderRegistry.get(server).ownedBy(id)) {
            for (Map.Entry<String, Integer> e : b.counts.entrySet()) {
                if (e.getValue() <= 0) continue;
                long u = unit.computeIfAbsent(e.getKey(), k -> {
                    net.minecraft.resources.ResourceLocation rl = net.minecraft.resources.ResourceLocation.tryParse(k);
                    net.minecraft.world.item.Item item = rl == null ? null : dev.masterball.api.card.CardInfo.item(rl);
                    return item == null ? 0L : Math.max(0, dev.tcgshop.pricing.PriceService.priceCd(new net.minecraft.world.item.ItemStack(item)));
                });
                v = sat(v, mul(u, e.getValue()));
            }
            for (dev.tcgshop.collection.BinderData.Special s : b.specials)
                if (s.count > 0 && !s.stack.isEmpty()) v = sat(v, mul(Math.max(0, dev.tcgshop.shop.ShopServer.priceOf(s.stack, 1.0)), s.count));
        }
        return v;
    }

    private static long sat(long a, long b) { // saturating a + b (b >= 0)
        return b >= 0 && a > Long.MAX_VALUE - b ? Long.MAX_VALUE : a + b;
    }

    private static long mul(long unit, int n) { // saturating unit * n
        return unit > 0 && n > Long.MAX_VALUE / unit ? Long.MAX_VALUE : unit * n;
    }

    /** Capital = wallet + registers + shop value - loan owed (never below 0). */
    public static long capital(long wallet, long registers, long shops, long owed) {
        return capital(wallet, registers, shops, owed, 0);
    }

    /** Capital = wallet + registers + shop value + assets (display stock, binder cards) - loan owed (never below 0). */
    public static long capital(long wallet, long registers, long shops, long owed, long assets) {
        long v = sat(sat(sat(Math.max(0, wallet), Math.max(0, registers)), Math.max(0, shops)), Math.max(0, assets)) - owed;
        return Math.max(0, v);
    }

    /** Refreshes the player's snapshot (wallet, capital, profit) and checks for a new best rank. */
    static Snap refresh(ServerPlayer p, boolean quiet) {
        MinecraftServer server = p.getServer();
        Ranking st = get(server);
        Snap s = st.players.computeIfAbsent(p.getUUID(), k -> new Snap());
        s.name = p.getGameProfile().getName();
        if (CobbleDollarsBridge.isAvailable()) s.wallet = CobbleDollarsBridge.getBalance(p);
        long[] pt = parts(server, p.getUUID());
        s.capital = capital(s.wallet, pt[0], pt[1], pt[2], sat(pt[3], pt[4]));
        long rev = 0, costs = 0;
        for (ServerLevel w : server.getAllLevels()) {
            ShopZones zs = ShopZones.get(w);
            costs += zs.costs(p.getUUID());
            for (ShopZones.Zone z : zs.allAndDormant()) if (z.owner.equals(p.getUUID())) rev += z.revenue;
        }
        s.revenue = rev;
        s.costs = costs;
        st.setDirty();
        if (!quiet) checkClimb(p, st, s);
        return s;
    }

    private static void checkClimb(ServerPlayer p, Ranking st, Snap s) {
        if (s.capital <= 0) return;
        List<Row> table = st.table(p.getServer());
        int rank = rankOf(table, p.getUUID());
        if (rank <= 0) return;
        boolean first = s.bestRank == Integer.MAX_VALUE;
        if (rank < s.bestRank) {
            int before = s.bestRank;
            s.bestRank = rank;
            st.setDirty();
            if (!first && rank <= NPCS) {
                PhoneApps.notice(p, "success", "tcgshop.rank.notice.title", "tcgshop.rank.notice.climb", rank, before > NPCS ? "100+" : String.valueOf(before));
                p.playNotifySound(TcgSounds.RANKING_UP, SoundSource.PLAYERS, 0.8f, 1f);
            }
        }
        if (rank == 1 && !s.beatDon) {
            s.beatDon = true;
            st.setDirty();
            p.getServer().getPlayerList().broadcastSystemMessage(Component.translatable("tcgshop.rank.beat_don", p.getDisplayName())
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
            p.playNotifySound(net.minecraft.sounds.SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1f, 1f);
            TcgShop.LOGGER.info("Ranking: {} passed Don Cobbledome ({} capital)", s.name, s.capital);
        }
    }

    private static void tick(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            try {
                refresh(p, false);
            } catch (Exception e) {
                TcgShop.LOGGER.error("[ranking] refresh failed for {}", p.getName().getString(), e);
            }
        }
    }

    // ------------------------------------------------------------------ table

    /** The whole table for today: the 100 fictional owners and every real player with a capital, best first. */
    List<Row> table(MinecraftServer server) {
        return table(server.overworld().getSeed(), PhoneMessages.day(server), players);
    }

    static List<Row> table(long seed, long day, Map<UUID, Snap> real) {
        List<Row> rows = new ArrayList<>(NPCS + real.size());
        for (int i = 0; i < NPCS; i++) rows.add(new Row(i, null, npcName(seed, i), npcCapital(seed, i, day), npcProfit(seed, i, day)));
        real.forEach((id, s) -> {
            if (s.capital <= 0 && s.revenue <= 0) return;
            double profit = s.revenue <= 0 ? 0 : Math.round((s.revenue - s.costs) * 1000.0 / s.revenue) / 10.0;
            rows.add(new Row(-1, id, s.name, s.capital, profit));
        });
        // ties: the fictional owner keeps the place (you have to EARN MORE than them)
        rows.sort(Comparator.comparingLong(Row::capital).reversed().thenComparingInt(r -> r.npc() < 0 ? 1 : 0));
        return rows;
    }

    /** 1-based rank of that player in the table, or 0 if absent. */
    static int rankOf(List<Row> table, UUID id) {
        for (int i = 0; i < table.size(); i++) if (id.equals(table.get(i).player())) return i + 1;
        return 0;
    }

    /** Rank a capital would have among the fictional owners alone on that day (for the selftest). */
    public static int rankForCapital(long seed, long day, long capital) {
        UUID fake = new UUID(0, 1);
        Snap s = new Snap();
        s.capital = capital;
        s.revenue = 1;
        return rankOf(table(seed, day, Map.of(fake, s)), fake);
    }

    // ------------------------------------------------------------------ sync

    /** Sends the Ranking app data: the rows to show, the player's own numbers and the next one to beat. */
    public static void send(ServerPlayer p) {
        if (!Net.INSTANCE.canSend(p, PhonePayloads.Apps.ID) || !PhoneUpgrades.hasPhone(p)) return;
        MinecraftServer server = p.getServer();
        Snap me = refresh(p, false);
        Ranking st = get(server);
        List<Row> table = st.table(server);
        int rank = rankOf(table, p.getUUID());
        CompoundTag d = new CompoundTag();
        d.putString("k", "ranking");
        d.putLong("day", PhoneMessages.day(server));
        d.putInt("rank", rank);
        d.putInt("total", table.size());
        d.putInt("best", me.bestRank == Integer.MAX_VALUE ? 0 : me.bestRank);
        d.putBoolean("beatDon", me.beatDon);
        long[] pt = parts(server, p.getUUID());
        d.putLong("capital", me.capital);
        d.putLong("wallet", Math.max(0, me.wallet));
        d.putLong("registers", pt[0]);
        d.putLong("shops", pt[1]);
        d.putLong("owed", pt[2]);
        d.putLong("assets", sat(pt[3], pt[4]));
        d.putDouble("profit", me.revenue <= 0 ? 0 : Math.round((me.revenue - me.costs) * 1000.0 / me.revenue) / 10.0);
        d.putLong("don", DON_CAPITAL);
        if (rank > 1) {
            Row next = table.get(rank - 2);
            d.putString("nextName", next.name());
            d.putLong("nextCapital", next.capital());
        }
        // rows: top 10, the player's neighbourhood, ranks 90-100 (and the player if below)
        java.util.TreeSet<Integer> show = new java.util.TreeSet<>();
        for (int r = 1; r <= Math.min(10, table.size()); r++) show.add(r);
        for (int r = 90; r <= Math.min(NPCS, table.size()); r++) show.add(r);
        if (rank > 0) for (int r = Math.max(1, rank - 2); r <= Math.min(table.size(), rank + 2); r++) show.add(r);
        ListTag rows = new ListTag();
        for (int r : show) {
            Row row = table.get(r - 1);
            CompoundTag c = new CompoundTag();
            c.putInt("r", r);
            c.putString("n", row.name());
            c.putLong("c", row.capital());
            c.putDouble("p", row.profit());
            if (row.npc() >= 0) {
                c.putInt("npc", row.npc());
                c.putString("s", row.npc() < 10 ? PhoneApps.KEY_ARG + "tcgshop.rank.npc." + row.npc() + ".shop" : row.name());
            } else c.putString("s", row.name());
            if (p.getUUID().equals(row.player())) c.putBoolean("me", true);
            else if (row.npc() < 0) c.putBoolean("real", true);
            rows.add(c);
        }
        d.put("rows", rows);
        Net.INSTANCE.send(p, new PhonePayloads.Apps(d));
    }

    // ------------------------------------------------------------------ persistence

    private static Ranking load(CompoundTag nbt, HolderLookup.Provider lookup) {
        Ranking s = new Ranking();
        CompoundTag p = nbt.getCompound("players");
        for (String k : p.getAllKeys()) {
            try {
                s.players.put(UUID.fromString(k), Snap.read(p.getCompound(k)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return s;
    }

    @Override
    public CompoundTag save(CompoundTag nbt, HolderLookup.Provider lookup) {
        CompoundTag p = new CompoundTag();
        players.forEach((k, v) -> p.put(k.toString(), v.write()));
        nbt.put("players", p);
        return nbt;
    }
}

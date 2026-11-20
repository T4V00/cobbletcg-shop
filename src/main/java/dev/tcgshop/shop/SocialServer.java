package dev.tcgshop.shop;

import dev.tcgshop.card.CardInfo;
import dev.tcgshop.card.Rarity;
import dev.tcgshop.collection.BinderData;
import dev.tcgshop.collection.CollectionService;
import dev.tcgshop.duel.League;
import dev.tcgshop.economy.CobbleDollarsBridge;
import dev.tcgshop.network.UiPayloads;
import dev.tcgshop.pricing.PriceService;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Lo social y de gestión que va por el teléfono / el Binder:
 *  · Mercado del Binder: venta rápida de varias cartas a la vez directamente desde tu Binder General, cartas
 *    favoritas protegidas, ofertas de venta a otros jugadores con precio y confirmación, e historial.
 *  · Amigos: solicitudes, lista, y las tiendas y estadísticas de cada amigo.
 *  · Gestor de expositores: todos los productos de tus tiendas con su precio, editable desde el teléfono.
 * Todo se valida en el servidor en el momento de aplicarse (sin duplicar ni perder cartas).
 */
public final class SocialServer {
    private SocialServer() {}

    // ================================================================== datos guardados

    public static final class Data extends PersistentState {
        final Map<UUID, Set<String>> favorites = new HashMap<>();
        final Map<UUID, List<NbtCompound>> history = new HashMap<>();
        final Map<UUID, Set<UUID>> friends = new HashMap<>();
        final Map<UUID, Set<UUID>> requests = new HashMap<>(); // destinatario -> quién se lo pidió
        final Map<UUID, String> names = new HashMap<>();
        final Map<UUID, NbtCompound> stats = new HashMap<>(); // última foto de cada jugador (para verla desconectado)

        static final Type<Data> TYPE = new Type<>(Data::new, Data::read, null);

        public static Data get(MinecraftServer server) {
            return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE, "tcgshop_social");
        }

        static Data read(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
            Data d = new Data();
            NbtCompound p = nbt.getCompound("players");
            for (String k : p.getKeys()) {
                UUID u;
                try { u = UUID.fromString(k); } catch (Exception e) { continue; }
                NbtCompound c = p.getCompound(k);
                d.names.put(u, c.getString("name"));
                Set<String> fav = new LinkedHashSet<>();
                for (NbtElement e : c.getList("fav", NbtElement.STRING_TYPE)) fav.add(e.asString());
                if (!fav.isEmpty()) d.favorites.put(u, fav);
                List<NbtCompound> h = new ArrayList<>();
                for (NbtElement e : c.getList("hist", NbtElement.COMPOUND_TYPE)) h.add((NbtCompound) e);
                if (!h.isEmpty()) d.history.put(u, h);
                d.friends.put(u, uuids(c.getList("friends", NbtElement.STRING_TYPE)));
                d.requests.put(u, uuids(c.getList("req", NbtElement.STRING_TYPE)));
                if (c.contains("stats")) d.stats.put(u, c.getCompound("stats"));
            }
            return d;
        }

        private static Set<UUID> uuids(NbtList l) {
            Set<UUID> s = new LinkedHashSet<>();
            for (NbtElement e : l) try { s.add(UUID.fromString(e.asString())); } catch (Exception ignored) {}
            return s;
        }

        private static NbtList list(Set<UUID> s) {
            NbtList l = new NbtList();
            for (UUID u : s) l.add(NbtString.of(u.toString()));
            return l;
        }

        @Override
        public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
            Set<UUID> all = new LinkedHashSet<>(names.keySet());
            all.addAll(favorites.keySet());
            all.addAll(friends.keySet());
            NbtCompound p = new NbtCompound();
            for (UUID u : all) {
                NbtCompound c = new NbtCompound();
                c.putString("name", names.getOrDefault(u, ""));
                NbtList fav = new NbtList();
                for (String s : favorites.getOrDefault(u, Set.of())) fav.add(NbtString.of(s));
                c.put("fav", fav);
                NbtList h = new NbtList();
                h.addAll(history.getOrDefault(u, List.of()));
                c.put("hist", h);
                c.put("friends", list(friends.getOrDefault(u, Set.of())));
                c.put("req", list(requests.getOrDefault(u, Set.of())));
                if (stats.containsKey(u)) c.put("stats", stats.get(u));
                p.put(u.toString(), c);
            }
            nbt.put("players", p);
            return nbt;
        }

        Set<String> fav(UUID u) { return favorites.computeIfAbsent(u, k -> new LinkedHashSet<>()); }
        Set<UUID> fr(UUID u) { return friends.computeIfAbsent(u, k -> new LinkedHashSet<>()); }
        Set<UUID> req(UUID u) { return requests.computeIfAbsent(u, k -> new LinkedHashSet<>()); }

        void log(UUID u, String kind, String card, int qty, long amount, String other) {
            NbtCompound e = new NbtCompound();
            e.putString("k", kind);
            e.putString("c", card);
            e.putInt("q", qty);
            e.putLong("p", amount);
            e.putString("w", other == null ? "" : other);
            e.putLong("t", System.currentTimeMillis());
            List<NbtCompound> h = history.computeIfAbsent(u, k -> new ArrayList<>());
            h.add(0, e);
            while (h.size() > 60) h.remove(h.size() - 1);
            markDirty();
        }
    }

    private static void send(ServerPlayerEntity p, String kind, NbtCompound data) {
        if (ServerPlayNetworking.canSend(p, UiPayloads.Open.ID)) ServerPlayNetworking.send(p, new UiPayloads.Open(kind, data));
    }

    /** Recuerda nombre y estadísticas actuales del jugador (para que sus amigos le vean aunque no esté). */
    public static void snapshot(ServerPlayerEntity p) {
        Data d = Data.get(p.getServer());
        d.names.put(p.getUuid(), p.getName().getString());
        d.stats.put(p.getUuid(), liveStats(p.getServer(), p.getUuid(), p));
        d.markDirty();
    }

    // ================================================================== mercado del Binder

    public static void openBinderMarket(ServerPlayerEntity p, boolean update) {
        Data d = Data.get(p.getServer());
        d.names.put(p.getUuid(), p.getName().getString());
        NbtCompound o = new NbtCompound();
        NbtList fav = new NbtList();
        for (String s : d.fav(p.getUuid())) fav.add(NbtString.of(s));
        o.put("fav", fav);
        NbtList h = new NbtList();
        h.addAll(d.history.getOrDefault(p.getUuid(), List.of()));
        o.put("hist", h);
        NbtList online = new NbtList();
        for (ServerPlayerEntity q : p.getServer().getPlayerManager().getPlayerList()) {
            if (q == p) continue;
            NbtCompound c = new NbtCompound();
            c.putUuid("u", q.getUuid());
            c.putString("n", q.getName().getString());
            online.add(c);
        }
        o.put("online", online);
        o.putBoolean("binder", CollectionService.ownedBinder(p) != null);
        o.putDouble("rate", CardSales.PHONE_RATE);
        CollectionService.sync(p);
        send(p, update ? "binder_market_update" : "binder_market", o);
    }

    public static void binderAction(ServerPlayerEntity p, NbtCompound a) {
        Data d = Data.get(p.getServer());
        BinderData b = CollectionService.ownedBinder(p);
        switch (a.getString("a")) {
            case "fav" -> {
                String id = a.getString("id");
                if (!d.fav(p.getUuid()).remove(id)) d.fav(p.getUuid()).add(id);
                d.markDirty();
            }
            case "sell" -> {
                if (b == null) { p.sendMessage(Text.translatable("tcgshop.binder.need").formatted(Formatting.RED), true); break; }
                if (!CobbleDollarsBridge.isAvailable()) { p.sendMessage(Text.translatable("tcgshop.sell.no_bank").formatted(Formatting.RED), true); break; }
                long total = 0;
                int n = 0, skipped = 0;
                for (NbtElement e : a.getList("items", NbtElement.COMPOUND_TYPE)) {
                    NbtCompound it = (NbtCompound) e;
                    String id = it.getString("id");
                    Identifier ident = Identifier.tryParse(id);
                    Item item = ident == null ? null : CardInfo.item(ident);
                    if (item == null || !CardInfo.isCard(ident)) continue;
                    if (d.fav(p.getUuid()).contains(id)) { skipped++; continue; } // las favoritas no se venden
                    int want = Math.max(1, Math.min(9999, it.getInt("q")));
                    int got = b.remove(id, want);
                    if (got <= 0) continue;
                    long unit = CardSales.offer(new ItemStack(item), CardSales.PHONE_RATE);
                    total += unit * got;
                    n += got;
                    d.log(p.getUuid(), "sell", id, got, unit * got, null);
                }
                if (n > 0) {
                    CobbleDollarsBridge.add(p, total);
                    CollectionService.dirty(p);
                    p.sendMessage(Text.translatable("tcgshop.bm2.sold", n, Text.literal(PriceService.format(total)).formatted(Formatting.GOLD)), false);
                    p.playSoundToPlayer(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.8f, 1.3f);
                }
                if (skipped > 0) p.sendMessage(Text.translatable("tcgshop.bm2.fav_skipped", skipped).formatted(Formatting.YELLOW), false);
            }
            case "offer" -> offer(p, a);
            default -> {}
        }
        openBinderMarket(p, true);
    }

    // --- ofertas a otros jugadores (la carta sigue en tu Binder hasta que el otro acepta y paga)
    private record Offer(int id, UUID seller, String sellerName, UUID buyer, String card, int qty, long price, long expires) {}

    private static final Map<Integer, Offer> OFFERS = new HashMap<>();
    private static int nextOffer = 1;

    private static void offer(ServerPlayerEntity p, NbtCompound a) {
        BinderData b = CollectionService.ownedBinder(p);
        String id = a.getString("id");
        int qty = Math.max(1, Math.min(64, a.getInt("q")));
        long price = Math.max(1, Math.min(1_000_000_000L, a.getLong("price")));
        if (b == null || b.count(id) < qty) { p.sendMessage(Text.translatable("tcgshop.bm2.not_enough").formatted(Formatting.RED), false); return; }
        if (!a.containsUuid("to")) return;
        ServerPlayerEntity to = p.getServer().getPlayerManager().getPlayer(a.getUuid("to"));
        if (to == null || to == p) { p.sendMessage(Text.translatable("tcgshop.bm2.offline").formatted(Formatting.RED), false); return; }
        Identifier ident = Identifier.tryParse(id);
        Item item = ident == null ? null : CardInfo.item(ident);
        if (item == null) return;
        OFFERS.values().removeIf(o -> o.expires < System.currentTimeMillis());
        Offer o = new Offer(nextOffer++, p.getUuid(), p.getName().getString(), to.getUuid(), id, qty, price, System.currentTimeMillis() + 120_000);
        OFFERS.put(o.id, o);
        Text cardName = new ItemStack(item).toHoverableText();
        p.sendMessage(Text.translatable("tcgshop.bm2.offer_sent", to.getDisplayName(), qty, cardName, PriceService.format(price)).formatted(Formatting.GREEN), false);
        Text accept = Text.translatable("tcgshop.bm2.accept").formatted(Formatting.GREEN, Formatting.BOLD).styled(s -> s
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tcgshop offer accept " + o.id))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.translatable("tcgshop.bm2.accept_tip", PriceService.format(price)))));
        Text deny = Text.translatable("tcgshop.bm2.deny").formatted(Formatting.RED).styled(s -> s
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tcgshop offer deny " + o.id)));
        to.sendMessage(Text.translatable("tcgshop.bm2.offer_got", p.getDisplayName(), qty, cardName, PriceService.format(price)).formatted(Formatting.GOLD), false);
        to.sendMessage(Text.literal("   ").append(accept).append("   ").append(deny), false);
        to.playSoundToPlayer(SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), SoundCategory.PLAYERS, 0.8f, 1.4f);
    }

    /** /tcgshop offer accept|deny <id>: todo se comprueba al aceptar (que el vendedor aún la tenga, que tengas dinero). */
    public static void answerOffer(ServerPlayerEntity buyer, int id, boolean accept) {
        Offer o = OFFERS.get(id);
        if (o == null || !o.buyer.equals(buyer.getUuid()) || o.expires < System.currentTimeMillis()) {
            OFFERS.remove(id);
            buyer.sendMessage(Text.translatable("tcgshop.bm2.expired").formatted(Formatting.GRAY), false);
            return;
        }
        OFFERS.remove(id);
        ServerPlayerEntity seller = buyer.getServer().getPlayerManager().getPlayer(o.seller);
        if (!accept) {
            buyer.sendMessage(Text.translatable("tcgshop.bm2.denied_you").formatted(Formatting.GRAY), false);
            if (seller != null) seller.sendMessage(Text.translatable("tcgshop.bm2.denied", buyer.getDisplayName()).formatted(Formatting.YELLOW), false);
            return;
        }
        if (seller == null) { buyer.sendMessage(Text.translatable("tcgshop.bm2.offline").formatted(Formatting.RED), false); return; }
        BinderData b = CollectionService.ownedBinder(seller);
        Identifier ident = Identifier.tryParse(o.card);
        Item item = ident == null ? null : CardInfo.item(ident);
        if (b == null || item == null || b.count(o.card) < o.qty) {
            buyer.sendMessage(Text.translatable("tcgshop.bm2.gone").formatted(Formatting.RED), false);
            return;
        }
        if (!CobbleDollarsBridge.tryCharge(buyer, o.price)) {
            buyer.sendMessage(Text.translatable("tcgshop.shop.no_money", PriceService.format(o.price)).formatted(Formatting.RED), false);
            return;
        }
        int got = b.remove(o.card, o.qty);
        if (got < o.qty) { // carrera muy improbable: devolver todo
            if (got > 0) b.add(o.card, got);
            CobbleDollarsBridge.add(buyer, o.price);
            buyer.sendMessage(Text.translatable("tcgshop.bm2.gone").formatted(Formatting.RED), false);
            return;
        }
        CobbleDollarsBridge.add(seller, o.price);
        CollectionService.dirty(seller);
        CollectionService.sync(seller);
        List<ItemStack> cards = new ArrayList<>();
        for (int i = 0; i < got; i++) cards.add(new ItemStack(item));
        if (!CollectionService.store(buyer, cards)) for (ItemStack st : cards) buyer.getInventory().offerOrDrop(st);
        Data d = Data.get(buyer.getServer());
        d.log(seller.getUuid(), "offer_sold", o.card, got, o.price, buyer.getName().getString());
        d.log(buyer.getUuid(), "offer_bought", o.card, got, o.price, seller.getName().getString());
        Text cardName = new ItemStack(item).toHoverableText();
        buyer.sendMessage(Text.translatable("tcgshop.bm2.bought", got, cardName, PriceService.format(o.price)).formatted(Formatting.GREEN), false);
        seller.sendMessage(Text.translatable("tcgshop.bm2.sold_to", buyer.getDisplayName(), got, cardName, PriceService.format(o.price)).formatted(Formatting.GREEN), false);
        seller.playSoundToPlayer(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.8f, 1.2f);
    }

    // ================================================================== amigos

    private static NbtCompound liveStats(MinecraftServer server, UUID u, ServerPlayerEntity online) {
        NbtCompound s = new NbtCompound();
        NbtList shops = new NbtList();
        long revenue = 0;
        int sales = 0, bestRep = 0;
        for (ServerWorld w : server.getWorlds())
            for (ShopZones.Zone z : ShopZones.get(w).all()) {
                if (!z.owner.equals(u)) continue;
                NbtCompound c = new NbtCompound();
                c.putInt("tier", z.tier);
                c.putInt("rep", z.reputation);
                c.putInt("sales", z.sales);
                c.putLong("rev", z.revenue);
                c.putInt("x", z.core.getX());
                c.putInt("y", z.core.getY());
                c.putInt("z", z.core.getZ());
                c.putString("dim", w.getRegistryKey().getValue().getPath());
                c.putInt("stars", (int) Math.round(z.avgStars() * 10));
                shops.add(c);
                revenue += z.revenue;
                sales += z.sales;
                bestRep = Math.max(bestRep, z.reputation);
            }
        s.put("shops", shops);
        s.putLong("revenue", revenue);
        s.putInt("sales", sales);
        s.putInt("rep", bestRep);
        if (online != null) {
            var pd = CollectionService.playerData(online);
            s.putInt("unique", pd.credited.size());
            s.putInt("packs", pd.packsOpened);
            s.putInt("god", pd.godPacks);
            League.Entry e = League.get(server).entry(u, online.getName().getString());
            s.putInt("rating", e.rating);
            s.putInt("wins", e.wins);
            s.putInt("losses", e.losses);
        } else {
            NbtCompound old = Data.get(server).stats.get(u);
            if (old != null) for (String k : new String[]{"unique", "packs", "god", "rating", "wins", "losses"}) s.putInt(k, old.getInt(k));
        }
        return s;
    }

    public static void friendsAction(ServerPlayerEntity p, NbtCompound a) {
        Data d = Data.get(p.getServer());
        UUID me = p.getUuid();
        d.names.put(me, p.getName().getString());
        UUID other = a.containsUuid("u") ? a.getUuid("u") : null;
        switch (a.getString("a")) {
            case "request" -> {
                if (other == null || other.equals(me) || d.fr(me).contains(other)) break;
                if (d.req(me).remove(other)) { befriend(d, me, other); notifyFriend(p, other, "tcgshop.friends.accepted_you"); break; } // ya te lo había pedido
                d.req(other).add(me);
                ServerPlayerEntity o = p.getServer().getPlayerManager().getPlayer(other);
                if (o != null) o.sendMessage(Text.translatable("tcgshop.friends.request_got", p.getDisplayName()).formatted(Formatting.AQUA), false);
                p.sendMessage(Text.translatable("tcgshop.friends.request_sent").formatted(Formatting.GREEN), true);
            }
            case "accept" -> {
                if (other != null && d.req(me).remove(other)) { befriend(d, me, other); notifyFriend(p, other, "tcgshop.friends.accepted_you"); }
            }
            case "decline" -> { if (other != null) d.req(me).remove(other); }
            case "remove" -> {
                if (other != null) { d.fr(me).remove(other); d.fr(other).remove(me); }
            }
            default -> {}
        }
        d.markDirty();
        sendFriends(p);
    }

    private static void befriend(Data d, UUID a, UUID b) {
        d.fr(a).add(b);
        d.fr(b).add(a);
        d.req(b).remove(a);
    }

    private static void notifyFriend(ServerPlayerEntity p, UUID other, String key) {
        ServerPlayerEntity o = p.getServer().getPlayerManager().getPlayer(other);
        if (o != null) o.sendMessage(Text.translatable(key, p.getDisplayName()).formatted(Formatting.GREEN), false);
    }

    public static void sendFriends(ServerPlayerEntity p) {
        Data d = Data.get(p.getServer());
        MinecraftServer server = p.getServer();
        snapshot(p);
        NbtCompound o = new NbtCompound();
        NbtList fl = new NbtList();
        for (UUID u : d.fr(p.getUuid())) {
            ServerPlayerEntity on = server.getPlayerManager().getPlayer(u);
            NbtCompound c = new NbtCompound();
            c.putUuid("u", u);
            c.putString("n", on != null ? on.getName().getString() : d.names.getOrDefault(u, "?"));
            c.putBoolean("on", on != null);
            c.put("s", liveStats(server, u, on)); // sólo amigos ven esto
            fl.add(c);
        }
        o.put("friends", fl);
        NbtList in = new NbtList();
        for (UUID u : d.req(p.getUuid())) {
            NbtCompound c = new NbtCompound();
            c.putUuid("u", u);
            c.putString("n", d.names.getOrDefault(u, "?"));
            in.add(c);
        }
        o.put("incoming", in);
        NbtList online = new NbtList();
        for (ServerPlayerEntity q : server.getPlayerManager().getPlayerList()) {
            if (q == p || d.fr(p.getUuid()).contains(q.getUuid())) continue;
            NbtCompound c = new NbtCompound();
            c.putUuid("u", q.getUuid());
            c.putString("n", q.getName().getString());
            c.putBoolean("sent", d.req(q.getUuid()).contains(p.getUuid()));
            online.add(c);
        }
        o.put("online", online);
        send(p, "friends", o);
    }

    // ================================================================== gestor de expositores

    public static void sendDisplays(ServerPlayerEntity p) {
        NbtCompound o = new NbtCompound();
        NbtList zl = new NbtList();
        ServerWorld world = p.getServerWorld();
        for (ShopZones.Zone z : ShopZones.get(world).all()) {
            if (!z.owner.equals(p.getUuid())) continue;
            NbtCompound zc = new NbtCompound();
            zc.putInt("tier", z.tier);
            zc.putLong("core", z.core.asLong());
            NbtList dl = new NbtList();
            int half = z.tierInfo().size / 2;
            int cx0 = (z.core.getX() - half) >> 4, cx1 = (z.core.getX() + half) >> 4, cz0 = (z.core.getZ() - half) >> 4, cz1 = (z.core.getZ() + half) >> 4;
            for (int cx = cx0; cx <= cx1; cx++)
                for (int cz = cz0; cz <= cz1; cz++) {
                    WorldChunk ch = world.getChunkManager().getWorldChunk(cx, cz);
                    if (ch == null) continue;
                    for (BlockEntity be : ch.getBlockEntities().values()) {
                        if (!(be instanceof ShopDisplayBlockEntity d) || d.isHome() || !z.contains(be.getPos())) continue;
                        NbtCompound dc = new NbtCompound();
                        dc.putLong("pos", be.getPos().asLong());
                        dc.putString("block", be.getCachedState().getBlock().getTranslationKey());
                        NbtList sl = new NbtList();
                        for (int i = 0; i < d.size(); i++) {
                            ItemStack st = d.getStack(i);
                            if (st.isEmpty()) continue;
                            NbtCompound sc = new NbtCompound();
                            sc.putInt("i", i);
                            sc.put("item", st.encode(p.getRegistryManager()));
                            sc.putLong("price", d.price(i));
                            sc.putLong("market", ShopServer.priceOf(st.copyWithCount(1), 1.0));
                            sl.add(sc);
                        }
                        dc.put("slots", sl);
                        dl.add(dc);
                    }
                }
            zc.put("displays", dl);
            zl.add(zc);
        }
        o.put("zones", zl);
        send(p, "displays", o);
    }

    /** price: una casilla; all_market: todo a precio de mercado (× factor). */
    public static void displaysAction(ServerPlayerEntity p, NbtCompound a) {
        ServerWorld world = p.getServerWorld();
        ShopZones zones = ShopZones.get(world);
        switch (a.getString("a")) {
            case "price" -> {
                BlockPos pos = BlockPos.fromLong(a.getLong("pos"));
                ShopZones.Zone z = zones.at(pos);
                if (z == null || !z.owner.equals(p.getUuid()) || !(world.getBlockEntity(pos) instanceof ShopDisplayBlockEntity d) || d.isHome()) break;
                int i = a.getInt("i");
                if (i < 0 || i >= d.size() || d.getStack(i).isEmpty()) break;
                d.setPrice(i, a.getLong("price"));
            }
            case "all_market" -> {
                double f = Math.max(0.1, Math.min(10, a.getDouble("f")));
                for (ShopZones.Zone z : zones.all()) {
                    if (!z.owner.equals(p.getUuid())) continue;
                    int half = z.tierInfo().size / 2;
                    for (int cx = (z.core.getX() - half) >> 4; cx <= (z.core.getX() + half) >> 4; cx++)
                        for (int cz = (z.core.getZ() - half) >> 4; cz <= (z.core.getZ() + half) >> 4; cz++) {
                            WorldChunk ch = world.getChunkManager().getWorldChunk(cx, cz);
                            if (ch == null) continue;
                            for (BlockEntity be : ch.getBlockEntities().values())
                                if (be instanceof ShopDisplayBlockEntity d && !d.isHome() && z.contains(be.getPos()))
                                    for (int i = 0; i < d.size(); i++)
                                        if (!d.getStack(i).isEmpty()) d.setPrice(i, Math.round(ShopServer.priceOf(d.getStack(i).copyWithCount(1), 1.0) * f));
                        }
                }
                p.sendMessage(Text.translatable("tcgshop.displays.all_set", Math.round(f * 100)).formatted(Formatting.GREEN), true);
            }
            default -> {}
        }
        sendDisplays(p);
    }

    /** Valor de mercado de una unidad de una carta por id (para el historial y el cliente). */
    public static long unit(String id) {
        Identifier ident = Identifier.tryParse(id);
        Item item = ident == null ? null : CardInfo.item(ident);
        return item == null ? 0 : PriceService.priceCd(new ItemStack(item));
    }

    @SuppressWarnings("unused")
    private static int tier(String id) {
        Identifier ident = Identifier.tryParse(id);
        Item item = ident == null ? null : CardInfo.item(ident);
        return item == null ? 0 : Rarity.of(new ItemStack(item)).tier;
    }
}

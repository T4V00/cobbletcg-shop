package dev.tcgshop.shop;

import dev.masterball.api.platform.Net;
import dev.masterball.api.card.CardInfo;
import dev.masterball.api.card.Rarity;
import dev.tcgshop.collection.BinderData;
import dev.tcgshop.collection.CollectionService;
import dev.tcgshop.duel.League;
import dev.tcgshop.economy.CobbleDollarsBridge;
import dev.tcgshop.network.UiPayloads;
import dev.tcgshop.pricing.PriceService;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The social and management features that go through the phone / the Binder:
 *  · Binder Market: quick-sell several cards at once directly from your General Binder, protected
 *    favorite cards, sale offers to other players with price and confirmation, and history.
 *  · Friends: requests, list, and each friend's shops and stats.
 *  · Display manager: every product in your shops with its price, editable from the phone.
 * Everything is validated on the server at the moment it is applied (no duplicated or lost cards).
 */
public final class SocialServer {
    private SocialServer() {}

    // ================================================================== saved data

    public static final class Data extends SavedData {
        final Map<UUID, Set<String>> favorites = new HashMap<>();
        final Map<UUID, List<CompoundTag>> history = new HashMap<>();
        final Map<UUID, Set<UUID>> friends = new HashMap<>();
        final Map<UUID, Set<UUID>> requests = new HashMap<>(); // recipient -> who requested it
        final Map<UUID, String> names = new HashMap<>();
        final Map<UUID, CompoundTag> stats = new HashMap<>(); // latest snapshot of each player (to view it while offline)

        static final Factory<Data> TYPE = new Factory<>(Data::new, Data::read, null);

        public static Data get(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(TYPE, "tcgshop_social");
        }

        static Data read(CompoundTag nbt, HolderLookup.Provider lookup) {
            Data d = new Data();
            CompoundTag p = nbt.getCompound("players");
            for (String k : p.getAllKeys()) {
                UUID u;
                try { u = UUID.fromString(k); } catch (Exception e) { continue; }
                CompoundTag c = p.getCompound(k);
                d.names.put(u, c.getString("name"));
                Set<String> fav = new LinkedHashSet<>();
                for (Tag e : c.getList("fav", Tag.TAG_STRING)) {
                    String id = dev.masterball.api.card.CardInfo.migrate(e.getAsString());
                    if (!dev.masterball.api.card.CardCatalog.isRemovedId(id)) fav.add(id); // TCG Pocket: removed in 0.9
                }
                if (!fav.isEmpty()) d.favorites.put(u, fav);
                List<CompoundTag> h = new ArrayList<>();
                for (Tag e : c.getList("hist", Tag.TAG_COMPOUND)) {
                    CompoundTag he = (CompoundTag) e;
                    if (he.contains("c")) {
                        String id = dev.masterball.api.card.CardInfo.migrate(he.getString("c"));
                        if (dev.masterball.api.card.CardCatalog.isRemovedId(id)) continue; // TCG Pocket: removed in 0.9
                        he.putString("c", id);
                    }
                    h.add(he);
                }
                if (!h.isEmpty()) d.history.put(u, h);
                d.friends.put(u, uuids(c.getList("friends", Tag.TAG_STRING)));
                d.requests.put(u, uuids(c.getList("req", Tag.TAG_STRING)));
                if (c.contains("stats")) d.stats.put(u, c.getCompound("stats"));
            }
            return d;
        }

        private static Set<UUID> uuids(ListTag l) {
            Set<UUID> s = new LinkedHashSet<>();
            for (Tag e : l) try { s.add(UUID.fromString(e.getAsString())); } catch (Exception ignored) {}
            return s;
        }

        private static ListTag list(Set<UUID> s) {
            ListTag l = new ListTag();
            for (UUID u : s) l.add(StringTag.valueOf(u.toString()));
            return l;
        }

        @Override
        public CompoundTag save(CompoundTag nbt, HolderLookup.Provider lookup) {
            Set<UUID> all = new LinkedHashSet<>(names.keySet());
            all.addAll(favorites.keySet());
            all.addAll(friends.keySet());
            CompoundTag p = new CompoundTag();
            for (UUID u : all) {
                CompoundTag c = new CompoundTag();
                c.putString("name", names.getOrDefault(u, ""));
                ListTag fav = new ListTag();
                for (String s : favorites.getOrDefault(u, Set.of())) fav.add(StringTag.valueOf(s));
                c.put("fav", fav);
                ListTag h = new ListTag();
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
            CompoundTag e = new CompoundTag();
            e.putString("k", kind);
            e.putString("c", card);
            e.putInt("q", qty);
            e.putLong("p", amount);
            e.putString("w", other == null ? "" : other);
            e.putLong("t", System.currentTimeMillis());
            List<CompoundTag> h = history.computeIfAbsent(u, k -> new ArrayList<>());
            h.add(0, e);
            while (h.size() > 60) h.remove(h.size() - 1);
            setDirty();
        }
    }

    private static void send(ServerPlayer p, String kind, CompoundTag data) {
        if (Net.INSTANCE.canSend(p, UiPayloads.Open.ID)) Net.INSTANCE.send(p, new UiPayloads.Open(kind, data));
    }

    /** Remembers the player's current name and stats (so their friends can see them even when they are offline). */
    public static void snapshot(ServerPlayer p) {
        Data d = Data.get(p.getServer());
        d.names.put(p.getUUID(), p.getName().getString());
        d.stats.put(p.getUUID(), liveStats(p.getServer(), p.getUUID(), p));
        d.setDirty();
    }

    // ================================================================== Binder market

    public static void openBinderMarket(ServerPlayer p, boolean update) {
        Data d = Data.get(p.getServer());
        d.names.put(p.getUUID(), p.getName().getString());
        CompoundTag o = new CompoundTag();
        ListTag fav = new ListTag();
        for (String s : d.fav(p.getUUID())) fav.add(StringTag.valueOf(s));
        o.put("fav", fav);
        ListTag h = new ListTag();
        h.addAll(d.history.getOrDefault(p.getUUID(), List.of()));
        o.put("hist", h);
        ListTag online = new ListTag();
        for (ServerPlayer q : p.getServer().getPlayerList().getPlayers()) {
            if (q == p) continue;
            CompoundTag c = new CompoundTag();
            c.putUUID("u", q.getUUID());
            c.putString("n", q.getName().getString());
            online.add(c);
        }
        o.put("online", online);
        o.putBoolean("binder", CollectionService.ownedBinder(p) != null);
        o.putDouble("rate", CardSales.PHONE_RATE);
        CollectionService.sync(p);
        send(p, update ? "binder_market_update" : "binder_market", o);
    }

    public static void binderAction(ServerPlayer p, CompoundTag a) {
        Data d = Data.get(p.getServer());
        BinderData b = CollectionService.ownedBinder(p);
        switch (a.getString("a")) {
            case "fav" -> {
                String id = a.getString("id");
                if (!d.fav(p.getUUID()).remove(id)) d.fav(p.getUUID()).add(id);
                d.setDirty();
            }
            case "sell" -> {
                if (b == null) { p.displayClientMessage(Component.translatable("tcgshop.binder.need").withStyle(ChatFormatting.RED), true); break; }
                if (!CobbleDollarsBridge.isAvailable()) { p.displayClientMessage(Component.translatable("tcgshop.sell.no_bank").withStyle(ChatFormatting.RED), true); break; }
                long total = 0;
                int n = 0, skipped = 0;
                for (Tag e : a.getList("items", Tag.TAG_COMPOUND)) {
                    CompoundTag it = (CompoundTag) e;
                    String id = it.getString("id");
                    ResourceLocation ident = ResourceLocation.tryParse(id);
                    Item item = ident == null ? null : CardInfo.item(ident);
                    if (item == null || !CardInfo.isCard(ident)) continue;
                    if (d.fav(p.getUUID()).contains(id)) { skipped++; continue; } // favorites are not sold
                    int want = Math.max(1, Math.min(9999, it.getInt("q")));
                    int got = b.remove(id, want);
                    if (got <= 0) continue;
                    long unit = CardSales.offer(new ItemStack(item), CardSales.PHONE_RATE);
                    total += unit * got;
                    n += got;
                    d.log(p.getUUID(), "sell", id, got, unit * got, null);
                }
                if (n > 0) {
                    CobbleDollarsBridge.add(p, total);
                    CollectionService.dirty(p);
                    p.displayClientMessage(Component.translatable("tcgshop.bm2.sold", n, Component.literal(PriceService.format(total)).withStyle(ChatFormatting.GOLD)), false);
                    p.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.8f, 1.3f);
                }
                if (skipped > 0) p.displayClientMessage(Component.translatable("tcgshop.bm2.fav_skipped", skipped).withStyle(ChatFormatting.YELLOW), false);
            }
            case "offer" -> offer(p, a);
            default -> {}
        }
        openBinderMarket(p, true);
    }

    // --- offers to other players (the card stays in your Binder until the other player accepts and pays)
    private record Offer(int id, UUID seller, String sellerName, UUID buyer, String card, int qty, long price, long expires) {}

    private static final Map<Integer, Offer> OFFERS = new HashMap<>();
    private static int nextOffer = 1;

    private static void offer(ServerPlayer p, CompoundTag a) {
        BinderData b = CollectionService.ownedBinder(p);
        String id = a.getString("id");
        int qty = Math.max(1, Math.min(64, a.getInt("q")));
        long price = Math.max(1, Math.min(1_000_000_000L, a.getLong("price")));
        if (b == null || b.count(id) < qty) { p.displayClientMessage(Component.translatable("tcgshop.bm2.not_enough").withStyle(ChatFormatting.RED), false); return; }
        if (!a.hasUUID("to")) return;
        ServerPlayer to = p.getServer().getPlayerList().getPlayer(a.getUUID("to"));
        if (to == null || to == p) { p.displayClientMessage(Component.translatable("tcgshop.bm2.offline").withStyle(ChatFormatting.RED), false); return; }
        ResourceLocation ident = ResourceLocation.tryParse(id);
        Item item = ident == null ? null : CardInfo.item(ident);
        if (item == null) return;
        OFFERS.values().removeIf(o -> o.expires < System.currentTimeMillis());
        Offer o = new Offer(nextOffer++, p.getUUID(), p.getName().getString(), to.getUUID(), id, qty, price, System.currentTimeMillis() + 120_000);
        OFFERS.put(o.id, o);
        Component cardName = new ItemStack(item).getDisplayName();
        p.displayClientMessage(Component.translatable("tcgshop.bm2.offer_sent", to.getDisplayName(), qty, cardName, PriceService.format(price)).withStyle(ChatFormatting.GREEN), false);
        Component accept = Component.translatable("tcgshop.bm2.accept").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD).withStyle(s -> s
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tcgshop offer accept " + o.id))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("tcgshop.bm2.accept_tip", PriceService.format(price)))));
        Component deny = Component.translatable("tcgshop.bm2.deny").withStyle(ChatFormatting.RED).withStyle(s -> s
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tcgshop offer deny " + o.id)));
        to.displayClientMessage(Component.translatable("tcgshop.bm2.offer_got", p.getDisplayName(), qty, cardName, PriceService.format(price)).withStyle(ChatFormatting.GOLD), false);
        to.displayClientMessage(Component.literal("   ").append(accept).append("   ").append(deny), false);
        to.playNotifySound(SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 0.8f, 1.4f);
    }

    /** /tcgshop offer accept|deny <id>: everything is checked on accept (that the seller still has it, that you have the money). */
    public static void answerOffer(ServerPlayer buyer, int id, boolean accept) {
        Offer o = OFFERS.get(id);
        if (o == null || !o.buyer.equals(buyer.getUUID()) || o.expires < System.currentTimeMillis()) {
            OFFERS.remove(id);
            buyer.displayClientMessage(Component.translatable("tcgshop.bm2.expired").withStyle(ChatFormatting.GRAY), false);
            return;
        }
        // 1.0: no buying from other players while a Wholesaler loan is active (the offer stays open until it expires)
        if (accept && dev.tcgshop.phone.Loans.blocksPurchase(buyer, "tcgshop.loan.notice.blocked_offer")) return;
        OFFERS.remove(id);
        ServerPlayer seller = buyer.getServer().getPlayerList().getPlayer(o.seller);
        if (!accept) {
            buyer.displayClientMessage(Component.translatable("tcgshop.bm2.denied_you").withStyle(ChatFormatting.GRAY), false);
            if (seller != null) seller.displayClientMessage(Component.translatable("tcgshop.bm2.denied", buyer.getDisplayName()).withStyle(ChatFormatting.YELLOW), false);
            return;
        }
        if (seller == null) { buyer.displayClientMessage(Component.translatable("tcgshop.bm2.offline").withStyle(ChatFormatting.RED), false); return; }
        BinderData b = CollectionService.ownedBinder(seller);
        ResourceLocation ident = ResourceLocation.tryParse(o.card);
        Item item = ident == null ? null : CardInfo.item(ident);
        if (b == null || item == null || b.count(o.card) < o.qty) {
            buyer.displayClientMessage(Component.translatable("tcgshop.bm2.gone").withStyle(ChatFormatting.RED), false);
            return;
        }
        if (!CobbleDollarsBridge.tryCharge(buyer, o.price)) {
            buyer.displayClientMessage(Component.translatable("tcgshop.shop.no_money", PriceService.format(o.price)).withStyle(ChatFormatting.RED), false);
            return;
        }
        int got = b.remove(o.card, o.qty);
        if (got < o.qty) { // very unlikely race: refund everything
            if (got > 0) b.add(o.card, got);
            CobbleDollarsBridge.add(buyer, o.price);
            buyer.displayClientMessage(Component.translatable("tcgshop.bm2.gone").withStyle(ChatFormatting.RED), false);
            return;
        }
        CobbleDollarsBridge.add(seller, o.price);
        CollectionService.dirty(seller);
        CollectionService.sync(seller);
        List<ItemStack> cards = new ArrayList<>();
        for (int i = 0; i < got; i++) cards.add(new ItemStack(item));
        if (!CollectionService.store(buyer, cards)) for (ItemStack st : cards) buyer.getInventory().placeItemBackInInventory(st);
        Data d = Data.get(buyer.getServer());
        d.log(seller.getUUID(), "offer_sold", o.card, got, o.price, buyer.getName().getString());
        d.log(buyer.getUUID(), "offer_bought", o.card, got, o.price, seller.getName().getString());
        Component cardName = new ItemStack(item).getDisplayName();
        buyer.displayClientMessage(Component.translatable("tcgshop.bm2.bought", got, cardName, PriceService.format(o.price)).withStyle(ChatFormatting.GREEN), false);
        seller.displayClientMessage(Component.translatable("tcgshop.bm2.sold_to", buyer.getDisplayName(), got, cardName, PriceService.format(o.price)).withStyle(ChatFormatting.GREEN), false);
        seller.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.8f, 1.2f);
    }

    // ================================================================== friends

    private static CompoundTag liveStats(MinecraftServer server, UUID u, ServerPlayer online) {
        CompoundTag s = new CompoundTag();
        ListTag shops = new ListTag();
        long revenue = 0;
        int sales = 0, bestRep = 0;
        for (ServerLevel w : server.getAllLevels())
            for (ShopZones.Zone z : ShopZones.get(w).all()) {
                if (!z.owner.equals(u)) continue;
                CompoundTag c = new CompoundTag();
                c.putInt("tier", z.tier);
                c.putInt("rep", z.reputation);
                c.putInt("sales", z.sales);
                c.putLong("rev", z.revenue);
                c.putInt("x", z.core.getX());
                c.putInt("y", z.core.getY());
                c.putInt("z", z.core.getZ());
                c.putString("dim", w.dimension().location().getPath());
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
            CompoundTag old = Data.get(server).stats.get(u);
            if (old != null) for (String k : new String[]{"unique", "packs", "god", "rating", "wins", "losses"}) s.putInt(k, old.getInt(k));
        }
        return s;
    }

    public static void friendsAction(ServerPlayer p, CompoundTag a) {
        Data d = Data.get(p.getServer());
        UUID me = p.getUUID();
        d.names.put(me, p.getName().getString());
        UUID other = a.hasUUID("u") ? a.getUUID("u") : null;
        switch (a.getString("a")) {
            case "request" -> {
                if (other == null || other.equals(me) || d.fr(me).contains(other)) break;
                if (d.req(me).remove(other)) { befriend(d, me, other); notifyFriend(p, other, "tcgshop.friends.accepted_you"); break; } // they had already asked you
                d.req(other).add(me);
                ServerPlayer o = p.getServer().getPlayerList().getPlayer(other);
                if (o != null) o.displayClientMessage(Component.translatable("tcgshop.friends.request_got", p.getDisplayName()).withStyle(ChatFormatting.AQUA), false);
                p.displayClientMessage(Component.translatable("tcgshop.friends.request_sent").withStyle(ChatFormatting.GREEN), true);
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
        d.setDirty();
        sendFriends(p);
    }

    private static void befriend(Data d, UUID a, UUID b) {
        d.fr(a).add(b);
        d.fr(b).add(a);
        d.req(b).remove(a);
    }

    private static void notifyFriend(ServerPlayer p, UUID other, String key) {
        ServerPlayer o = p.getServer().getPlayerList().getPlayer(other);
        if (o != null) o.displayClientMessage(Component.translatable(key, p.getDisplayName()).withStyle(ChatFormatting.GREEN), false);
    }

    public static void sendFriends(ServerPlayer p) {
        Data d = Data.get(p.getServer());
        MinecraftServer server = p.getServer();
        snapshot(p);
        CompoundTag o = new CompoundTag();
        ListTag fl = new ListTag();
        for (UUID u : d.fr(p.getUUID())) {
            ServerPlayer on = server.getPlayerList().getPlayer(u);
            CompoundTag c = new CompoundTag();
            c.putUUID("u", u);
            c.putString("n", on != null ? on.getName().getString() : d.names.getOrDefault(u, "?"));
            c.putBoolean("on", on != null);
            c.put("s", liveStats(server, u, on)); // only friends see this
            fl.add(c);
        }
        o.put("friends", fl);
        ListTag in = new ListTag();
        for (UUID u : d.req(p.getUUID())) {
            CompoundTag c = new CompoundTag();
            c.putUUID("u", u);
            c.putString("n", d.names.getOrDefault(u, "?"));
            in.add(c);
        }
        o.put("incoming", in);
        ListTag online = new ListTag();
        for (ServerPlayer q : server.getPlayerList().getPlayers()) {
            if (q == p || d.fr(p.getUUID()).contains(q.getUUID())) continue;
            CompoundTag c = new CompoundTag();
            c.putUUID("u", q.getUUID());
            c.putString("n", q.getName().getString());
            c.putBoolean("sent", d.req(q.getUUID()).contains(p.getUUID()));
            online.add(c);
        }
        o.put("online", online);
        send(p, "friends", o);
    }

    // ================================================================== display manager

    public static void sendDisplays(ServerPlayer p) {
        CompoundTag o = new CompoundTag();
        ListTag zl = new ListTag();
        ServerLevel world = p.serverLevel();
        for (ShopZones.Zone z : ShopZones.get(world).all()) {
            if (!z.owner.equals(p.getUUID())) continue;
            CompoundTag zc = new CompoundTag();
            zc.putInt("tier", z.tier);
            zc.putLong("core", z.core.asLong());
            ListTag dl = new ListTag();
            int half = z.size() / 2;
            int cx0 = (z.core.getX() - half) >> 4, cx1 = (z.core.getX() + half) >> 4, cz0 = (z.core.getZ() - half) >> 4, cz1 = (z.core.getZ() + half) >> 4;
            for (int cx = cx0; cx <= cx1; cx++)
                for (int cz = cz0; cz <= cz1; cz++) {
                    LevelChunk ch = world.getChunkSource().getChunkNow(cx, cz);
                    if (ch == null) continue;
                    for (BlockEntity be : ch.getBlockEntities().values()) {
                        if (!(be instanceof ShopDisplayBlockEntity d) || d.isHome() || !z.contains(be.getBlockPos())) continue;
                        CompoundTag dc = new CompoundTag();
                        dc.putLong("pos", be.getBlockPos().asLong());
                        dc.putString("block", be.getBlockState().getBlock().getDescriptionId());
                        ListTag sl = new ListTag();
                        for (int i = 0; i < d.getContainerSize(); i++) {
                            ItemStack st = d.getItem(i);
                            if (st.isEmpty()) continue;
                            CompoundTag sc = new CompoundTag();
                            sc.putInt("i", i);
                            sc.put("item", st.save(p.registryAccess()));
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

    /** price: one slot; all_market: everything at market price (× factor). */
    public static void displaysAction(ServerPlayer p, CompoundTag a) {
        ServerLevel world = p.serverLevel();
        ShopZones zones = ShopZones.get(world);
        switch (a.getString("a")) {
            case "price" -> {
                BlockPos pos = BlockPos.of(a.getLong("pos"));
                ShopZones.Zone z = zones.at(pos);
                if (z == null || !z.owner.equals(p.getUUID()) || !(world.getBlockEntity(pos) instanceof ShopDisplayBlockEntity d) || d.isHome()) break;
                int i = a.getInt("i");
                if (i < 0 || i >= d.getContainerSize() || d.getItem(i).isEmpty()) break;
                d.setPrice(i, a.getLong("price"));
            }
            case "all_market" -> {
                double f = Math.max(0.1, Math.min(10, a.getDouble("f")));
                for (ShopZones.Zone z : zones.all()) {
                    if (!z.owner.equals(p.getUUID())) continue;
                    int half = z.size() / 2;
                    for (int cx = (z.core.getX() - half) >> 4; cx <= (z.core.getX() + half) >> 4; cx++)
                        for (int cz = (z.core.getZ() - half) >> 4; cz <= (z.core.getZ() + half) >> 4; cz++) {
                            LevelChunk ch = world.getChunkSource().getChunkNow(cx, cz);
                            if (ch == null) continue;
                            for (BlockEntity be : ch.getBlockEntities().values())
                                if (be instanceof ShopDisplayBlockEntity d && !d.isHome() && z.contains(be.getBlockPos()))
                                    for (int i = 0; i < d.getContainerSize(); i++)
                                        if (!d.getItem(i).isEmpty()) d.setPrice(i, Math.round(ShopServer.priceOf(d.getItem(i).copyWithCount(1), 1.0) * f));
                        }
                }
                p.displayClientMessage(Component.translatable("tcgshop.displays.all_set", Math.round(f * 100)).withStyle(ChatFormatting.GREEN), true);
            }
            default -> {}
        }
        sendDisplays(p);
    }

    /** Market value of one unit of a card by id (for the history and the client). */
    public static long unit(String id) {
        ResourceLocation ident = ResourceLocation.tryParse(id);
        Item item = ident == null ? null : CardInfo.item(ident);
        return item == null ? 0 : PriceService.priceCd(new ItemStack(item));
    }

    @SuppressWarnings("unused")
    private static int tier(String id) {
        ResourceLocation ident = ResourceLocation.tryParse(id);
        Item item = ident == null ? null : CardInfo.item(ident);
        return item == null ? 0 : Rarity.of(new ItemStack(item)).tier;
    }
}

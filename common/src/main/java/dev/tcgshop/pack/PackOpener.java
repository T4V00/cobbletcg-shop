package dev.tcgshop.pack;

import dev.masterball.api.platform.Net;
import dev.tcgshop.TcgShop;
import dev.tcgshop.TcgShopConfig;
import dev.tcgshop.advancement.CardAdvancements;
import dev.masterball.api.card.CardInfo;
import dev.masterball.api.card.Rarity;
import dev.tcgshop.collection.CardCollection;
import dev.tcgshop.collection.CollectionService;
import dev.tcgshop.network.GroupCutPayload;
import dev.tcgshop.network.GroupReadyPayload;
import dev.tcgshop.network.GroupRevealPayload;
import dev.tcgshop.network.OpenGroupPayload;
import dev.tcgshop.pricing.PriceService;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import dev.masterball.api.card.PrintVariant;
import dev.masterball.api.pack.PackContents;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Group pack opening (1-100 per rip), server-controlled using the pack contents from
 * MasterBall Traders API (PackContents):
 *
 *  1. OpenGroup(ids): for each pack, if sealed, roll normal/semi-god/god and draw its 5 cards (with their
 *     print variant). Everything stays pending.
 *     Replies GroupReady with each pack's type and the highest rarity of its contents (for the glow).
 *  2. GroupCut(damaged): delivers the cards of cleanly cut packs (to the General Binder if you have it, or
 *     to the inventory) and destroys those of badly cut ones. Replies GroupReveal.
 *  If the player closes the screen or disconnects with a pending group, everything is delivered as clean.
 */
public final class PackOpener {
    private record Pending(ResourceLocation ripped, int tier, List<ItemStack> cards, List<Long> prices) {}

    private static final Map<UUID, List<Pending>> PENDING = new HashMap<>();
    private static final Map<UUID, Long> LAST_ACTIVITY = new HashMap<>();
    public static final int MAX_GROUP = 100;

    private PackOpener() {}

    /** Is the player opening packs right now? (used by the Scalper) */
    public static boolean isOpening(ServerPlayer player) {
        if (PENDING.containsKey(player.getUUID())) return true;
        Long last = LAST_ACTIVITY.get(player.getUUID());
        return last != null && player.getServer().getTickCount() - last < 200;
    }

    private static void touch(ServerPlayer player) {
        LAST_ACTIVITY.put(player.getUUID(), (long) player.getServer().getTickCount());
    }

    public static void onOpenGroup(OpenGroupPayload p, ServerPlayer player) {
        if (PENDING.containsKey(player.getUUID())) finish(player, List.of()); // previous group not finished
        touch(player);
        List<Pending> group = new ArrayList<>();
        List<ResourceLocation> rippedIds = new ArrayList<>();
        List<Integer> tiers = new ArrayList<>(), best = new ArrayList<>();
        int n = Math.min(MAX_GROUP, p.items().size());
        for (int i = 0; i < n; i++) {
            Pending pk = prepare(player, p.items().get(i));
            if (pk == null) {
                rippedIds.add(p.items().get(i));
                tiers.add(0);
                best.add(-1);
                group.add(new Pending(p.items().get(i), 0, List.of(), List.of()));
                continue;
            }
            group.add(pk);
            rippedIds.add(pk.ripped);
            tiers.add(pk.tier);
            int top = 0;
            for (ItemStack c : pk.cards) top = Math.max(top, Rarity.of(c).tier);
            best.add(top);
        }
        PENDING.put(player.getUUID(), group);
        reply(player, new GroupReadyPayload(rippedIds, tiers, best));
    }

    /** Opens a pack (sealed or already ripped) and leaves its cards pending. null on failure. */
    private static Pending prepare(ServerPlayer player, ResourceLocation id) {
        Item item = CardInfo.item(id);
        if (item == null) return null;
        int tier = CardInfo.packTier(new ItemStack(item));
        if (tier < 0) return null;
        ItemStack one = takeOne(player, item);
        if (one.isEmpty()) return null;
        String set = CardInfo.setId(CardInfo.id(one));
        int rippedTier = tier == 0 ? PackContents.rollTier(player.getRandom()) : tier;  // sealed: normal / semi-god / god
        ResourceLocation rippedId = CardInfo.rippedOf(set, rippedTier);
        List<ItemStack> cards = new ArrayList<>(PackContents.open(set, rippedTier, player.getRandom()));
        if (rippedId == null || cards.isEmpty()) { // unknown set: give the pack back
            player.getInventory().placeItemBackInInventory(one);
            return null;
        }
        cards.sort(Comparator.comparingInt((ItemStack st) -> revealRank(Rarity.of(st))).thenComparing(PrintVariant::isSpecial).thenComparingLong(PriceService::priceCd));
        List<Long> prices = new ArrayList<>();
        for (ItemStack st : cards) prices.add(PriceService.priceCd(st));
        return new Pending(rippedId, rippedTier, cards, prices);
    }

    public static void onCut(GroupCutPayload p, ServerPlayer player) {
        finish(player, p.damaged());
    }

    /** Delivers or destroys the cards of the pending group. Empty damaged = all clean (cancelled). */
    public static void finish(ServerPlayer player, List<Boolean> damaged) {
        List<Pending> group = PENDING.remove(player.getUUID());
        if (group == null) return;
        touch(player);
        boolean toBinder = CollectionService.hasCardStorage(player); // 1.0: General Binder or, failing that, the Personal (starter) one
        CardCollection col = CollectionService.playerData(player);
        List<List<ItemStack>> cards = new ArrayList<>();
        List<List<Long>> prices = new ArrayList<>();
        List<Boolean> ruined = new ArrayList<>();
        List<ItemStack> toStore = new ArrayList<>();
        int opened = 0;
        for (int i = 0; i < group.size(); i++) {
            Pending pk = group.get(i);
            boolean bad = i < damaged.size() && damaged.get(i);
            cards.add(pk.cards);
            prices.add(pk.prices);
            ruined.add(bad);
            if (pk.cards.isEmpty()) continue;
            opened++;
            col.packsOpened++;
            if (pk.tier == 3) { col.godPacks++; CardAdvancements.grant(player, "god_pack"); }
            if (pk.tier >= 2) CardAdvancements.grant(player, "semigod_pack");
            if (bad) {
                col.packsRuined++;
                CardAdvancements.grant(player, "ruined_pack");
                if (col.packsRuined >= 10) CardAdvancements.grant(player, "ruined_10");
                TcgShop.LOGGER.info("{} botched opening a pack: {} cards destroyed", player.getName().getString(), pk.cards.size());
                continue;
            }
            for (ItemStack st : pk.cards) {
                if (toBinder) toStore.add(st.copy());
                else player.getInventory().placeItemBackInInventory(st.copy());
            }
            CardAdvancements.creditPull(player, pk.cards, pk.prices);
            dev.tcgshop.collection.Titles.creditPulls(player, col, pk.cards);
            announce(player, pk.cards, pk.prices);
        }
        if (!toStore.isEmpty() && !CollectionService.store(player, toStore)) {
            for (ItemStack st : toStore) player.getInventory().placeItemBackInInventory(st);
            toBinder = false;
        }
        dev.tcgshop.duel.Cosmetics.rollLoot(player, opened);
        dev.tcgshop.duel.Cosmetics.check(player);
        if (opened >= 10) CardAdvancements.grant(player, "multi_open_10");
        if (col.packsOpened >= 10) CardAdvancements.grant(player, "packs_10");
        if (col.packsOpened >= 50) CardAdvancements.grant(player, "packs_50");
        if (col.packsOpened >= 250) CardAdvancements.grant(player, "packs_250");
        if (col.packsOpened >= 1000) CardAdvancements.grant(player, "packs_1000");
        if (opened > 0) dev.tcgshop.onboarding.Onboarding.onPackOpened(player); // 1.0: one-time starting money on the first pack
        reply(player, new GroupRevealPayload(cards, prices, ruined, toBinder));
    }

    /** 1.1: result of {@link #openDirect}: packs and cards opened, their value, the 3 best pulls and god packs. */
    public record DirectResult(int packs, int cards, long value, List<ItemStack> best, List<Long> bestPrices, int godPacks, boolean toBinder) {}

    /**
     * 1.1 (Box-Opening Gloves): opens {@code packs} sealed packs of a set at once without the opening screen, as a
     * perfect cut (nothing ruined). The packs are not taken from the inventory (the caller already consumed the boxes).
     * Cards go to the binder (or the inventory) with the same advancements, titles and cosmetics loot as a normal opening.
     * The caller (BoxGloves.handle) only allows big openings with a General Binder, which files any number of cards, so
     * thousands of cards never spill into the inventory or onto the ground. Pull announces are aggregated: one broadcast
     * for the best hit of the whole opening (with how many more hits it had) instead of one chat line per card.
     */
    public static DirectResult openDirect(ServerPlayer player, String set, int packs) {
        touch(player);
        boolean toBinder = CollectionService.hasCardStorage(player);
        CardCollection col = CollectionService.playerData(player);
        List<ItemStack> toStore = new ArrayList<>();
        List<ItemStack> all = new ArrayList<>();
        List<Long> allPrices = new ArrayList<>();
        int opened = 0, god = 0, hits = 0;
        long value = 0;
        int minTier = TcgShopConfig.INSTANCE.announcePullMinTier;
        ItemStack bestHit = ItemStack.EMPTY;
        long bestHitPrice = -1;
        for (int i = 0; i < packs; i++) {
            int tier = PackContents.rollTier(player.getRandom());
            List<ItemStack> cards = new ArrayList<>(PackContents.open(set, tier, player.getRandom()));
            if (cards.isEmpty()) continue;
            List<Long> prices = new ArrayList<>();
            for (ItemStack st : cards) {
                long pr = PriceService.priceCd(st);
                prices.add(pr);
                value += pr * st.getCount();
                all.add(st);
                allPrices.add(pr);
            }
            opened++;
            col.packsOpened++;
            if (tier == 3) { col.godPacks++; god++; CardAdvancements.grant(player, "god_pack"); }
            if (tier >= 2) CardAdvancements.grant(player, "semigod_pack");
            for (ItemStack st : cards) {
                if (toBinder) toStore.add(st.copy());
                else player.getInventory().placeItemBackInInventory(st.copy());
            }
            CardAdvancements.creditPull(player, cards, prices);
            dev.tcgshop.collection.Titles.creditPulls(player, col, cards);
            if (minTier >= 0) for (int k = 0; k < cards.size(); k++) {
                if (Rarity.of(cards.get(k)).tier < minTier) continue;
                hits++;
                if (prices.get(k) > bestHitPrice) { bestHitPrice = prices.get(k); bestHit = cards.get(k); }
            }
        }
        if (!toStore.isEmpty() && !CollectionService.store(player, toStore)) {
            for (ItemStack st : toStore) player.getInventory().placeItemBackInInventory(st);
            toBinder = false;
        }
        dev.tcgshop.duel.Cosmetics.rollLoot(player, opened);
        dev.tcgshop.duel.Cosmetics.check(player);
        if (opened >= 10) CardAdvancements.grant(player, "multi_open_10");
        if (col.packsOpened >= 10) CardAdvancements.grant(player, "packs_10");
        if (col.packsOpened >= 50) CardAdvancements.grant(player, "packs_50");
        if (col.packsOpened >= 250) CardAdvancements.grant(player, "packs_250");
        if (col.packsOpened >= 1000) CardAdvancements.grant(player, "packs_1000");
        if (opened > 0) dev.tcgshop.onboarding.Onboarding.onPackOpened(player);
        if (hits > 0) announceBox(player, bestHit, bestHitPrice, hits - 1, opened);
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) idx.add(i);
        idx.sort((a, b) -> Long.compare(allPrices.get(b), allPrices.get(a)));
        List<ItemStack> best = new ArrayList<>();
        List<Long> bestPrices = new ArrayList<>();
        for (int i = 0; i < Math.min(3, idx.size()); i++) {
            best.add(all.get(idx.get(i)).copy());
            bestPrices.add(allPrices.get(idx.get(i)));
        }
        return new DirectResult(opened, all.size(), value, best, bestPrices, god, toBinder);
    }

    public static void onDisconnect(ServerPlayer player) {
        finish(player, List.of());
        LAST_ACTIVITY.remove(player.getUUID());
    }

    // ------------------------------------------------------------------ controlled use

    private static ItemStack takeOne(ServerPlayer player, Item item) {
        Inventory inv = player.getInventory();
        int slot = -1;
        if (inv.getItem(inv.selected).is(item)) slot = inv.selected;
        else if (inv.getItem(Inventory.SLOT_OFFHAND).is(item)) slot = Inventory.SLOT_OFFHAND;
        else for (int i = 0; i < inv.items.size(); i++) if (inv.getItem(i).is(item)) { slot = i; break; }
        return slot < 0 ? ItemStack.EMPTY : inv.getItem(slot).split(1);
    }

    private static void reply(ServerPlayer player, net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        if (Net.INSTANCE.canSend(player, payload.type())) Net.INSTANCE.send(player, payload);
    }

    /** TCG Pocket-style reveal order: the rarest last. */
    public static int revealRank(Rarity r) {
        return r.rank;
    }

    /** 1.1: one broadcast for a whole Box-Opening Gloves opening: its best hit and how many more hits it had. */
    private static void announceBox(ServerPlayer player, ItemStack best, long price, int more, int packs) {
        if (best.isEmpty()) return;
        if (more <= 0) { announce(player, List.of(best), List.of(price)); return; }
        Rarity r = Rarity.of(best);
        Component msg = Component.translatable("tcgshop.broadcast.box", player.getDisplayName(), packs, best.getDisplayName(),
                r.displayName().copy().withColor(r.color & 0xFFFFFF),
                Component.literal(PriceService.format(price)).withStyle(ChatFormatting.GOLD), more);
        broadcast(player, msg);
    }

    private static void broadcast(ServerPlayer player, Component msg) {
        int radius = TcgShopConfig.INSTANCE.announceRadius;
        for (ServerPlayer other : player.getServer().getPlayerList().getPlayers()) {
            if (radius <= 0 || (other.level() == player.level() && other.distanceToSqr(player) <= (double) radius * radius)) {
                other.displayClientMessage(msg, false);
            }
        }
    }

    private static void announce(ServerPlayer player, List<ItemStack> cards, List<Long> prices) {
        int min = TcgShopConfig.INSTANCE.announcePullMinTier;
        if (min < 0) return;
        int radius = TcgShopConfig.INSTANCE.announceRadius;
        for (int i = 0; i < cards.size(); i++) {
            ItemStack c = cards.get(i);
            Rarity r = Rarity.of(c);
            if (r.tier < min) continue;
            Component msg = Component.translatable("tcgshop.broadcast", player.getDisplayName(), c.getDisplayName(),
                    r.displayName().copy().withColor(r.color & 0xFFFFFF),
                    Component.literal(PriceService.format(prices.get(i))).withStyle(ChatFormatting.GOLD));
            for (ServerPlayer other : player.getServer().getPlayerList().getPlayers()) {
                if (radius <= 0 || (other.level() == player.level() && other.distanceToSqr(player) <= (double) radius * radius)) {
                    other.displayClientMessage(msg, false);
                }
            }
        }
    }
}

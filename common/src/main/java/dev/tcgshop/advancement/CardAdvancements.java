package dev.tcgshop.advancement;

import dev.tcgshop.TcgShop;
import dev.masterball.api.card.CardInfo;
import dev.masterball.api.card.Rarity;
import dev.tcgshop.collection.CardCollection;
import dev.tcgshop.pricing.PriceService;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.advancements.critereon.ItemPredicate;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Card advancements. There is NO separate tracker of seen cards: everything is credited at the moment
 * the player OPENS a binder (general, personal or set album), with the cards it contains.
 *
 *  · Collection advancements from other mods ("inventory_changed" criterion for a single card): granted
 *    directly (so they count even if the card is stored in a binder and not in the inventory).
 *  · Our own advancements (data/tcgshop/advancement): unique cards, collection value, complete sets...
 */
public final class CardAdvancements {
    private record Crit(AdvancementHolder entry, String name) {}

    private static Map<String, List<Crit>> index;
    private static Map<String, Set<String>> setCards;

    public static final int[] UNIQUE_MILESTONES = {25, 100, 250, 500, 1000, 2500, 5000};
    public static final long[] VALUE_MILESTONES = {10_000, 100_000, 1_000_000};

    private CardAdvancements() {}

    /** Called when datapacks are reloaded: the index is rebuilt the next time. */
    public static void invalidate() {
        index = null;
    }

    private static Map<String, List<Crit>> index(MinecraftServer server) {
        if (index != null) return index;
        Map<String, List<Crit>> map = new HashMap<>();
        for (AdvancementHolder e : server.getAdvancements().getAllAdvancements()) {
            if (e.id().getNamespace().equals("minecraft")) continue;
            for (Map.Entry<String, Criterion<?>> c : e.value().criteria().entrySet()) {
                if (!(c.getValue().triggerInstance() instanceof InventoryChangeTrigger.TriggerInstance cond)) continue;
                if (cond.items().size() != 1) continue;
                ItemPredicate pred = cond.items().get(0);
                if (pred.items().isEmpty()) continue;
                HolderSet<Item> list = pred.items().get();
                if (list.size() != 1) continue;
                Holder<Item> item = list.get(0);
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(item.value());
                if (CardInfo.isCard(id)) map.computeIfAbsent(id.toString(), k -> new ArrayList<>()).add(new Crit(e, c.getKey()));
            }
        }
        TcgShop.LOGGER.info("Card advancements indexed: {} cards with collection criteria", map.size());
        index = map;
        return map;
    }

    /** All registered cards, grouped by set. */
    public static Map<String, Set<String>> setCards() {
        if (setCards != null) return setCards;
        Map<String, Set<String>> m = new HashMap<>();
        for (ResourceLocation id : BuiltInRegistries.ITEM.keySet()) {
            if (CardInfo.isCard(id)) m.computeIfAbsent(CardInfo.setId(id), k -> new HashSet<>()).add(id.toString());
        }
        setCards = m;
        return m;
    }

    /** Credits the cards of a just-opened binder. */
    public static void credit(ServerPlayer player, CardCollection c, dev.tcgshop.collection.BinderData binder, Collection<String> ids) {
        Map<String, List<Crit>> idx = index(player.getServer());
        int oldUnique = c.credited.size();
        for (String id : ids) {
            if (dev.masterball.api.card.CardCatalog.isRemovedId(id)) continue; // TCG Pocket: removed in 0.9
            c.credited.add(id);
            List<Crit> crits = idx.get(id);
            if (crits != null) for (Crit k : crits) player.getAdvancements().award(k.entry, k.name);
        }
        int unique = c.credited.size();
        if (unique > 0) grant(player, "first_binder");
        for (int m : UNIQUE_MILESTONES) if (unique >= m) grant(player, "unique_" + m);

        long value = 0;
        for (Map.Entry<String, Integer> e : (binder == null ? Map.<String, Integer>of() : binder.counts).entrySet()) {
            Item item = CardInfo.item(ResourceLocation.parse(e.getKey()));
            if (item != null) value += PriceService.priceCd(new ItemStack(item)) * e.getValue();
        }
        for (long v : VALUE_MILESTONES) if (value >= v) grant(player, "value_" + (v >= 1_000_000 ? "1m" : (v / 1000) + "k"));

        if (binder != null) rewardCompletedSets(player, c, binder); // 1.1: Master Ball + money once per completed set
        if (unique > oldUnique) {
            for (Set<String> set : setCards().values()) {
                if (!set.isEmpty() && c.credited.containsAll(set)) {
                    grant(player, "set_complete");
                    break;
                }
            }
            FunFacts.check(player, c, oldUnique, unique);
        }
    }

    /**
     * 1.1: the first time every card of a set is in this General Binder, the player gets the set completion reward
     * (EconomyConfig.setCompleteMoney CobbleDollars + setCompleteMasterBalls Cobblemon Master Balls), once per set.
     * Only cards the player got from the game itself count (CardCollection.pulled: packs and NPC gifts), so one set of
     * cards handed from account to account cannot collect the reward on each of them. Money that could not be paid
     * (CobbleDollars missing or refusing) is kept in CardCollection.pendingSetMoney and paid on a later call.
     * Returns the sets rewarded now.
     */
    public static List<String> rewardCompletedSets(ServerPlayer player, CardCollection c, dev.tcgshop.collection.BinderData binder) {
        payPendingSetMoney(player, c);
        List<String> done = new java.util.ArrayList<>();
        Map<String, Integer> have = rewardCounts(binder, c.pulled);
        for (Map.Entry<String, Set<String>> e : setCards().entrySet()) {
            if (e.getValue().isEmpty() || c.rewardedSets.contains(e.getKey())) continue;
            if (!isSetComplete(e.getValue(), have)) continue;
            c.rewardedSets.add(e.getKey());
            done.add(e.getKey());
            giveSetReward(player, c, e.getKey());
        }
        return done;
    }

    /** 1.1: a card the player got from the game itself (pack pull, NPC gift): it counts for the set completion reward. */
    public static void noteObtained(ServerPlayer player, ItemStack card) {
        if (card.isEmpty() || !CardInfo.isCard(card)) return;
        dev.tcgshop.collection.CollectionService.playerData(player).pulled.add(CardInfo.id(card).toString());
    }

    /** Pays set completion money left unpaid earlier. True when nothing is owed any more. */
    public static boolean payPendingSetMoney(ServerPlayer player, CardCollection c) {
        if (c.pendingSetMoney <= 0) return true;
        if (!dev.tcgshop.economy.CobbleDollarsBridge.isAvailable() || !dev.tcgshop.economy.CobbleDollarsBridge.add(player, c.pendingSetMoney)) return false;
        player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("tcgshop.collection.reward_paid",
                dev.tcgshop.pricing.PriceService.format(c.pendingSetMoney)).withStyle(net.minecraft.ChatFormatting.GOLD));
        TcgShop.LOGGER.info("Set completion reward: paid {} owed to {}", c.pendingSetMoney, player.getName().getString());
        c.pendingSetMoney = 0;
        return true;
    }

    /** The chat line of a set completion reward: wording for balls + money, balls only, money only, unpaid money or neither. */
    public static String rewardKey(int balls, long money, boolean pending) {
        if (pending) return balls > 0 ? "tcgshop.collection.reward_balls_pending" : "tcgshop.collection.reward_pending";
        if (balls > 0 && money > 0) return "tcgshop.collection.reward";
        if (balls > 0) return "tcgshop.collection.reward_balls";
        if (money > 0) return "tcgshop.collection.reward_money";
        return "tcgshop.collection.reward_none";
    }

    /**
     * The binder cards that count for the set reward: plain cards and print variants (graded slabs don't), only those
     * whose id the player got from the game itself ({@code pulled}); cards merely received from other players don't.
     */
    public static Map<String, Integer> rewardCounts(dev.tcgshop.collection.BinderData binder, Set<String> pulled) {
        Map<String, Integer> have = new HashMap<>(binder.counts);
        for (dev.tcgshop.collection.BinderData.Special s : binder.specials)
            if (s.count > 0 && !s.stack.isEmpty() && !dev.tcgshop.grading.Grading.isGraded(s.stack)) have.merge(CardInfo.id(s.stack).toString(), s.count, Integer::sum);
        have.keySet().retainAll(pulled);
        return have;
    }

    /** Is every (non-removed) card of the set present in those binder counts? */
    public static boolean isSetComplete(Set<String> set, Map<String, Integer> counts) {
        boolean any = false;
        for (String id : set) {
            if (dev.masterball.api.card.CardCatalog.isRemovedId(id)) continue;
            any = true;
            if (counts.getOrDefault(id, 0) <= 0) return false;
        }
        return any;
    }

    /** Cobblemon's Master Ball item, or null when Cobblemon is not installed. */
    public static Item masterBall() {
        return BuiltInRegistries.ITEM.getOptional(ResourceLocation.fromNamespaceAndPath("cobblemon", "master_ball")).orElse(null);
    }

    private static void giveSetReward(ServerPlayer player, CardCollection c, String setId) {
        dev.tcgshop.EconomyConfig eco = dev.tcgshop.EconomyConfig.INSTANCE;
        long money = Math.max(0, eco.setCompleteMoney);
        boolean paid = money > 0 && dev.tcgshop.economy.CobbleDollarsBridge.isAvailable() && dev.tcgshop.economy.CobbleDollarsBridge.add(player, money);
        boolean pending = money > 0 && !paid;
        if (pending) c.pendingSetMoney += money; // review fix: never lost, paid on a later call
        int balls = 0;
        Item mb = masterBall();
        if (mb != null && eco.setCompleteMasterBalls > 0) {
            balls = eco.setCompleteMasterBalls;
            ItemStack st = new ItemStack(mb, balls);
            if (!player.getInventory().add(st) && !st.isEmpty()) player.drop(st, false);
        }
        String key = rewardKey(balls, money, pending);
        net.minecraft.network.chat.Component set = net.minecraft.network.chat.Component.translatable("masterball.set." + setId);
        String cash = dev.tcgshop.pricing.PriceService.format(money);
        net.minecraft.network.chat.Component line = switch (key) {
            case "tcgshop.collection.reward", "tcgshop.collection.reward_balls_pending" -> net.minecraft.network.chat.Component.translatable(key, set, balls, cash);
            case "tcgshop.collection.reward_balls" -> net.minecraft.network.chat.Component.translatable(key, set, balls);
            case "tcgshop.collection.reward_money", "tcgshop.collection.reward_pending" -> net.minecraft.network.chat.Component.translatable(key, set, cash);
            default -> net.minecraft.network.chat.Component.translatable(key, set);
        };
        player.sendSystemMessage(line.copy().withStyle(net.minecraft.ChatFormatting.GOLD));
        player.playNotifySound(dev.tcgshop.TcgSounds.COLLECTION_COMPLETE, net.minecraft.sounds.SoundSource.PLAYERS, 1f, 1f);
        TcgShop.LOGGER.info("Set completion reward: {} completed {} ({} Master Balls, {} paid, {} owed)", player.getName().getString(), setId, balls,
                paid ? money : 0, pending ? money : 0);
    }

    /** Advancements for pulling cards of a certain rarity (called when opening a clean pack). */
    public static void creditPull(ServerPlayer player, List<ItemStack> cards, List<Long> prices) {
        for (int i = 0; i < cards.size(); i++) {
            noteObtained(player, cards.get(i)); // 1.1: pulled cards count for the set completion reward
            switch (Rarity.of(cards.get(i)).family) {
                case DOUBLE_RARE -> grant(player, "pull_double_rare");
                case ULTRA_RARE -> grant(player, "pull_ultra_rare");
                case ILLUSTRATION_RARE -> grant(player, "pull_illustration_rare");
                case SPECIAL_ILLUSTRATION_RARE -> grant(player, "pull_special_illustration");
                case HYPER_RARE -> grant(player, "pull_hyper_rare");
                default -> {}
            }
            if (prices.get(i) >= 100_000) grant(player, "jackpot");
        }
    }

    public static void grant(ServerPlayer player, String path) {
        AdvancementHolder e = player.getServer().getAdvancements().get(ResourceLocation.fromNamespaceAndPath(TcgShop.MOD_ID, path));
        if (e == null) return;
        for (String crit : e.value().criteria().keySet()) player.getAdvancements().award(e, crit);
    }

    public static boolean isDone(ServerPlayer player, String fullId) {
        AdvancementHolder e = player.getServer().getAdvancements().get(ResourceLocation.parse(fullId));
        return e != null && player.getAdvancements().getOrStartProgress(e).isDone();
    }
}

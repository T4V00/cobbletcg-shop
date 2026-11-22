package dev.tcgshop.advancement;

import dev.tcgshop.TcgShop;
import dev.masterball.api.card.CardInfo;
import dev.masterball.api.card.Rarity;
import dev.tcgshop.collection.CardCollection;
import dev.tcgshop.pricing.PriceService;
import net.minecraft.advancement.AdvancementCriterion;
import net.minecraft.advancement.AdvancementEntry;
import net.minecraft.advancement.criterion.InventoryChangedCriterion;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.predicate.item.ItemPredicate;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Logros de cartas. NO hay rastreador aparte de cartas vistas: todo se acredita en el momento en que
 * el jugador ABRE un binder (general, personal o álbum de set), con las cartas que contiene.
 *
 *  · Logros de colección de otros mods (criterio "inventory_changed" de una sola carta): se otorgan
 *    directamente (así cuentan aunque la carta esté guardada en un binder y no en el inventario).
 *  · Logros propios (data/tcgshop/advancement): cartas únicas, valor de la colección, sets completos...
 */
public final class CardAdvancements {
    private record Crit(AdvancementEntry entry, String name) {}

    private static Map<String, List<Crit>> index;
    private static Map<String, Set<String>> setCards;

    public static final int[] UNIQUE_MILESTONES = {25, 100, 250, 500, 1000, 2500, 5000};
    public static final long[] VALUE_MILESTONES = {10_000, 100_000, 1_000_000};

    private CardAdvancements() {}

    /** Se llama al recargar datapacks: el índice se reconstruye la próxima vez. */
    public static void invalidate() {
        index = null;
    }

    private static Map<String, List<Crit>> index(MinecraftServer server) {
        if (index != null) return index;
        Map<String, List<Crit>> map = new HashMap<>();
        for (AdvancementEntry e : server.getAdvancementLoader().getAdvancements()) {
            if (e.id().getNamespace().equals("minecraft")) continue;
            for (Map.Entry<String, AdvancementCriterion<?>> c : e.value().criteria().entrySet()) {
                if (!(c.getValue().conditions() instanceof InventoryChangedCriterion.Conditions cond)) continue;
                if (cond.items().size() != 1) continue;
                ItemPredicate pred = cond.items().get(0);
                if (pred.items().isEmpty()) continue;
                RegistryEntryList<Item> list = pred.items().get();
                if (list.size() != 1) continue;
                RegistryEntry<Item> item = list.get(0);
                Identifier id = Registries.ITEM.getId(item.value());
                if (CardInfo.isCard(id)) map.computeIfAbsent(id.toString(), k -> new ArrayList<>()).add(new Crit(e, c.getKey()));
            }
        }
        TcgShop.LOGGER.info("Logros de cartas indexados: {} cartas con criterios de colección", map.size());
        index = map;
        return map;
    }

    /** Todas las cartas registradas, agrupadas por set. */
    public static Map<String, Set<String>> setCards() {
        if (setCards != null) return setCards;
        Map<String, Set<String>> m = new HashMap<>();
        for (Identifier id : Registries.ITEM.getIds()) {
            if (CardInfo.isCard(id)) m.computeIfAbsent(CardInfo.setId(id), k -> new HashSet<>()).add(id.toString());
        }
        setCards = m;
        return m;
    }

    /** Acredita las cartas de un binder recién abierto. */
    public static void credit(ServerPlayerEntity player, CardCollection c, dev.tcgshop.collection.BinderData binder, Collection<String> ids) {
        Map<String, List<Crit>> idx = index(player.getServer());
        int oldUnique = c.credited.size();
        for (String id : ids) {
            if (dev.masterball.api.card.CardCatalog.isRemovedId(id)) continue; // TCG Pocket: quitado en la 0.9
            c.credited.add(id);
            List<Crit> crits = idx.get(id);
            if (crits != null) for (Crit k : crits) player.getAdvancementTracker().grantCriterion(k.entry, k.name);
        }
        int unique = c.credited.size();
        if (unique > 0) grant(player, "first_binder");
        for (int m : UNIQUE_MILESTONES) if (unique >= m) grant(player, "unique_" + m);

        long value = 0;
        for (Map.Entry<String, Integer> e : (binder == null ? Map.<String, Integer>of() : binder.counts).entrySet()) {
            Item item = CardInfo.item(Identifier.of(e.getKey()));
            if (item != null) value += PriceService.priceCd(new ItemStack(item)) * e.getValue();
        }
        for (long v : VALUE_MILESTONES) if (value >= v) grant(player, "value_" + (v >= 1_000_000 ? "1m" : (v / 1000) + "k"));

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

    /** Logros por sacar cartas de cierta rareza (se llama al abrir un sobre limpio). */
    public static void creditPull(ServerPlayerEntity player, List<ItemStack> cards, List<Long> prices) {
        for (int i = 0; i < cards.size(); i++) {
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

    public static void grant(ServerPlayerEntity player, String path) {
        AdvancementEntry e = player.getServer().getAdvancementLoader().get(Identifier.of(TcgShop.MOD_ID, path));
        if (e == null) return;
        for (String crit : e.value().criteria().keySet()) player.getAdvancementTracker().grantCriterion(e, crit);
    }

    public static boolean isDone(ServerPlayerEntity player, String fullId) {
        AdvancementEntry e = player.getServer().getAdvancementLoader().get(Identifier.of(fullId));
        return e != null && player.getAdvancementTracker().getProgress(e).isDone();
    }
}

package dev.tcgshop.pack;

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
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import dev.masterball.api.card.PrintVariant;
import dev.masterball.api.pack.PackContents;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Apertura de sobres en grupo (1-100 por rasgada), controlada por el servidor con el contenido de sobres de
 * MasterBall Traders API (PackContents):
 *
 *  1. OpenGroup(ids): por cada sobre, si está sellado se sortea normal/semi-dios/dios y se sacan sus 5 cartas (con su
 *     variante de impresión). Todo queda pendiente.
 *     Responde GroupReady con el tipo de cada sobre y la rareza máxima de su contenido (para el brillo).
 *  2. GroupCut(dañados): entrega las cartas de los sobres bien cortados (al Binder General si lo tienes, o
 *     al inventario) y destruye las de los mal cortados. Responde GroupReveal.
 *  Si el jugador cierra la pantalla o se desconecta con un grupo pendiente, se entrega todo como limpio.
 */
public final class PackOpener {
    private record Pending(Identifier ripped, int tier, List<ItemStack> cards, List<Long> prices) {}

    private static final Map<UUID, List<Pending>> PENDING = new HashMap<>();
    private static final Map<UUID, Long> LAST_ACTIVITY = new HashMap<>();
    public static final int MAX_GROUP = 100;

    private PackOpener() {}

    /** ¿Está el jugador abriendo sobres ahora mismo? (lo usa el Scalper) */
    public static boolean isOpening(ServerPlayerEntity player) {
        if (PENDING.containsKey(player.getUuid())) return true;
        Long last = LAST_ACTIVITY.get(player.getUuid());
        return last != null && player.getServer().getTicks() - last < 200;
    }

    private static void touch(ServerPlayerEntity player) {
        LAST_ACTIVITY.put(player.getUuid(), (long) player.getServer().getTicks());
    }

    public static void onOpenGroup(OpenGroupPayload p, ServerPlayerEntity player) {
        if (PENDING.containsKey(player.getUuid())) finish(player, List.of()); // grupo anterior sin terminar
        touch(player);
        List<Pending> group = new ArrayList<>();
        List<Identifier> rippedIds = new ArrayList<>();
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
        PENDING.put(player.getUuid(), group);
        reply(player, new GroupReadyPayload(rippedIds, tiers, best));
    }

    /** Abre un sobre (sellado o ya abierto) y deja sus cartas pendientes. null si falla. */
    private static Pending prepare(ServerPlayerEntity player, Identifier id) {
        Item item = CardInfo.item(id);
        if (item == null) return null;
        int tier = CardInfo.packTier(new ItemStack(item));
        if (tier < 0) return null;
        ItemStack one = takeOne(player, item);
        if (one.isEmpty()) return null;
        String set = CardInfo.setId(CardInfo.id(one));
        int rippedTier = tier == 0 ? PackContents.rollTier(player.getRandom()) : tier;  // sellado: normal / semi-dios / dios
        Identifier rippedId = CardInfo.rippedOf(set, rippedTier);
        List<ItemStack> cards = new ArrayList<>(PackContents.open(set, rippedTier, player.getRandom()));
        if (rippedId == null || cards.isEmpty()) { // set desconocido: devolver el sobre
            player.getInventory().offerOrDrop(one);
            return null;
        }
        cards.sort(Comparator.comparingInt((ItemStack st) -> revealRank(Rarity.of(st))).thenComparing(PrintVariant::isSpecial).thenComparingLong(PriceService::priceCd));
        List<Long> prices = new ArrayList<>();
        for (ItemStack st : cards) prices.add(PriceService.priceCd(st));
        return new Pending(rippedId, rippedTier, cards, prices);
    }

    public static void onCut(GroupCutPayload p, ServerPlayerEntity player) {
        finish(player, p.damaged());
    }

    /** Entrega o destruye las cartas del grupo pendiente. damaged vacío = todo limpio (cancelado). */
    public static void finish(ServerPlayerEntity player, List<Boolean> damaged) {
        List<Pending> group = PENDING.remove(player.getUuid());
        if (group == null) return;
        touch(player);
        boolean toBinder = CollectionService.ownedBinderId(player) != null;
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
                TcgShop.LOGGER.info("{} abrió mal un sobre: {} cartas destruidas", player.getName().getString(), pk.cards.size());
                continue;
            }
            for (ItemStack st : pk.cards) {
                if (toBinder) toStore.add(st.copy());
                else player.getInventory().offerOrDrop(st.copy());
            }
            CardAdvancements.creditPull(player, pk.cards, pk.prices);
            dev.tcgshop.collection.Titles.creditPulls(player, col, pk.cards);
            announce(player, pk.cards, pk.prices);
        }
        if (!toStore.isEmpty() && !CollectionService.store(player, toStore)) {
            for (ItemStack st : toStore) player.getInventory().offerOrDrop(st);
            toBinder = false;
        }
        dev.tcgshop.duel.Cosmetics.rollLoot(player, opened);
        dev.tcgshop.duel.Cosmetics.check(player);
        if (opened >= 10) CardAdvancements.grant(player, "multi_open_10");
        if (col.packsOpened >= 10) CardAdvancements.grant(player, "packs_10");
        if (col.packsOpened >= 50) CardAdvancements.grant(player, "packs_50");
        if (col.packsOpened >= 250) CardAdvancements.grant(player, "packs_250");
        if (col.packsOpened >= 1000) CardAdvancements.grant(player, "packs_1000");
        reply(player, new GroupRevealPayload(cards, prices, ruined, toBinder));
    }

    public static void onDisconnect(ServerPlayerEntity player) {
        finish(player, List.of());
        LAST_ACTIVITY.remove(player.getUuid());
    }

    // ------------------------------------------------------------------ uso controlado

    private static ItemStack takeOne(ServerPlayerEntity player, Item item) {
        PlayerInventory inv = player.getInventory();
        int slot = -1;
        if (inv.getStack(inv.selectedSlot).isOf(item)) slot = inv.selectedSlot;
        else if (inv.getStack(PlayerInventory.OFF_HAND_SLOT).isOf(item)) slot = PlayerInventory.OFF_HAND_SLOT;
        else for (int i = 0; i < inv.main.size(); i++) if (inv.getStack(i).isOf(item)) { slot = i; break; }
        return slot < 0 ? ItemStack.EMPTY : inv.getStack(slot).split(1);
    }

    private static void reply(ServerPlayerEntity player, net.minecraft.network.packet.CustomPayload payload) {
        if (ServerPlayNetworking.canSend(player, payload.getId())) ServerPlayNetworking.send(player, payload);
    }

    /** Orden de revelado estilo TCG Pocket: la más rara al final. */
    public static int revealRank(Rarity r) {
        return r.rank;
    }

    private static void announce(ServerPlayerEntity player, List<ItemStack> cards, List<Long> prices) {
        int min = TcgShopConfig.INSTANCE.announcePullMinTier;
        if (min < 0) return;
        int radius = TcgShopConfig.INSTANCE.announceRadius;
        for (int i = 0; i < cards.size(); i++) {
            ItemStack c = cards.get(i);
            Rarity r = Rarity.of(c);
            if (r.tier < min) continue;
            Text msg = Text.translatable("tcgshop.broadcast", player.getDisplayName(), c.toHoverableText(),
                    r.displayName().copy().withColor(r.color & 0xFFFFFF),
                    Text.literal(PriceService.format(prices.get(i))).formatted(Formatting.GOLD));
            for (ServerPlayerEntity other : player.getServer().getPlayerManager().getPlayerList()) {
                if (radius <= 0 || (other.getWorld() == player.getWorld() && other.squaredDistanceTo(player) <= (double) radius * radius)) {
                    other.sendMessage(msg, false);
                }
            }
        }
    }
}

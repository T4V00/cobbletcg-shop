package dev.tcgshop.pack;

import dev.tcgshop.TcgShop;
import dev.tcgshop.TcgShopConfig;
import dev.tcgshop.advancement.CardAdvancements;
import dev.tcgshop.card.CardInfo;
import dev.tcgshop.card.Rarity;
import dev.tcgshop.collection.CardCollection;
import dev.tcgshop.collection.CollectionService;
import dev.tcgshop.network.GroupCutPayload;
import dev.tcgshop.network.GroupReadyPayload;
import dev.tcgshop.network.GroupRevealPayload;
import dev.tcgshop.network.OpenGroupPayload;
import dev.tcgshop.pricing.PriceService;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Apertura de sobres en grupo (1-100 por rasgada), controlada por el servidor con la lógica original de
 * cobbletcg (sus loot tables):
 *
 *  1. OpenGroup(ids): por cada sobre, si está sellado lo "usa" (cobbletcg sortea normal/semi-dios/dios y
 *     entrega el sobre abierto) y luego usa el abierto (cobbletcg entrega las 5 cartas). Todo queda pendiente.
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
        ItemStack ripped;
        if (tier == 0) {
            ItemStack sealed = takeOne(player, item);
            if (sealed.isEmpty()) return null;
            List<ItemStack> got = useInHand(player, sealed);
            ripped = ItemStack.EMPTY;
            for (ItemStack st : got) {
                if (ripped.isEmpty() && CardInfo.packTier(st) > 0) ripped = st.copyWithCount(1);
                else player.getInventory().offerOrDrop(st);
            }
            if (ripped.isEmpty()) {
                if (got.isEmpty()) player.getInventory().offerOrDrop(new ItemStack(item)); // cobbletcg no hizo nada
                return null;
            }
        } else {
            ripped = takeOne(player, item);
            if (ripped.isEmpty()) return null;
        }
        Identifier rippedId = CardInfo.id(ripped);
        int rippedTier = CardInfo.packTier(ripped);
        List<ItemStack> got = useInHand(player, ripped);
        List<ItemStack> cards = new ArrayList<>();
        for (ItemStack st : got) {
            if (CardInfo.isCard(st)) cards.add(st);
            else player.getInventory().offerOrDrop(st);
        }
        if (got.isEmpty()) { // no se pudo abrir: devolver el sobre abierto
            player.getInventory().offerOrDrop(new ItemStack(CardInfo.item(rippedId)));
            return null;
        }
        cards.sort(Comparator.comparingInt((ItemStack st) -> revealRank(Rarity.of(st))).thenComparingLong(PriceService::priceCd));
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

    /**
     * Pone el ítem en la mano principal, llama a Item.use (donde está el mixin de cobbletcg) y devuelve lo que
     * cobbletcg intentó entregar (capturado, no entra al inventario). Restaura la mano después.
     */
    private static List<ItemStack> useInHand(ServerPlayerEntity player, ItemStack one) {
        PlayerInventory inv = player.getInventory();
        int sel = inv.selectedSlot;
        ItemStack prev = inv.getStack(sel);
        inv.setStack(sel, one);
        List<ItemStack> captured;
        PackCapture.start(player);
        try {
            one.use(player.getWorld(), player, Hand.MAIN_HAND);
        } catch (Exception e) {
            TcgShop.LOGGER.error("Error abriendo sobre {}", CardInfo.id(one), e);
        } finally {
            captured = PackCapture.stop();
        }
        inv.setStack(sel, prev);
        inv.markDirty();
        return captured;
    }

    private static void reply(ServerPlayerEntity player, net.minecraft.network.packet.CustomPayload payload) {
        if (ServerPlayNetworking.canSend(player, payload.getId())) ServerPlayNetworking.send(player, payload);
    }

    /** Orden de revelado estilo TCG Pocket: la más rara al final. */
    public static int revealRank(Rarity r) {
        return switch (r) {
            case UNKNOWN -> -1;
            case COMMON -> 0;
            case DOUBLE_RARE -> 1;
            case ULTRA_RARE -> 2;
            case ILLUSTRATION_RARE -> 3;
            case HYPER_RARE -> 4;
            case SPECIAL_ILLUSTRATION_RARE -> 5;
        };
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

package dev.tcgshop.collection;

import dev.tcgshop.TcgShop;
import dev.tcgshop.advancement.CardAdvancements;
import dev.tcgshop.card.CardInfo;
import dev.tcgshop.card.Rarity;
import dev.tcgshop.compat.AccessoriesCompat;
import dev.tcgshop.item.TcgItems;
import dev.tcgshop.network.CollectionSyncPayload;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Binder General: cada binder es ÚNICO. Al abrirlo por primera vez queda vinculado a quien lo abre (id + dueño
 * en el ítem) y su contenido se guarda en BinderRegistry. Si tienes dos binders, el segundo que registres
 * empieza vacío. Los binders de otro jugador no se pueden abrir.
 */
public final class CollectionService {
    @SuppressWarnings("UnstableApiUsage")
    public static final AttachmentType<CardCollection> PLAYER_DATA = AttachmentRegistry.<CardCollection>builder()
            .persistent(CardCollection.CODEC)
            .copyOnDeath()
            .initializer(CardCollection::new)
            .buildAndRegister(Identifier.of(TcgShop.MOD_ID, "collection"));

    /** Binder que cada jugador tiene abierto en pantalla (las acciones de la pantalla van a ese). */
    private static final Map<UUID, UUID> OPEN = new HashMap<>();

    private CollectionService() {}

    public static void init() {}

    @SuppressWarnings("UnstableApiUsage")
    public static CardCollection playerData(ServerPlayerEntity player) {
        return player.getAttachedOrCreate(PLAYER_DATA);
    }

    // ------------------------------------------------------------------ localizar / vincular binders

    /** Todos los Binders Generales que lleva el jugador (inventario + slots de Accessories). */
    public static List<ItemStack> carried(ServerPlayerEntity player) {
        List<ItemStack> out = new ArrayList<>();
        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < inv.size(); i++) if (inv.getStack(i).isOf(TcgItems.GENERAL_BINDER)) out.add(inv.getStack(i));
        out.addAll(AccessoriesCompat.equipped(player, TcgItems.GENERAL_BINDER));
        return out;
    }

    public static boolean hasGeneralBinder(ServerPlayerEntity player) {
        return !carried(player).isEmpty();
    }

    /**
     * Vincula el binder al jugador si aún no tiene dueño. Devuelve su contenido, o null si es de otro jugador.
     * La primera vez que un jugador registra un binder, recibe la colección de versiones anteriores del mod.
     */
    @Nullable
    public static BinderData bind(ServerPlayerEntity player, ItemStack stack) {
        BinderRegistry reg = BinderRegistry.get(player.getServer());
        BinderTag tag = stack.get(TcgItems.BINDER_TAG);
        if (tag == null) {
            UUID id = UUID.randomUUID();
            stack.set(TcgItems.BINDER_TAG, new BinderTag(id, player.getUuid(), player.getName().getString()));
            BinderData d = reg.create(id, player.getUuid(), player.getName().getString());
            CardCollection pd = playerData(player);
            if (!pd.legacyCounts.isEmpty()) { // migración desde la versión anterior
                d.counts.putAll(pd.legacyCounts);
                d.fresh.addAll(pd.legacyFresh);
                pd.legacyCounts.clear();
                pd.legacyFresh.clear();
            }
            reg.markDirty();
            player.sendMessage(Text.translatable("tcgshop.binder.bound").formatted(Formatting.GREEN), true);
            return d;
        }
        if (!tag.owner().equals(player.getUuid())) return null;
        BinderData d = reg.get(tag.id());
        if (d == null) d = reg.create(tag.id(), player.getUuid(), player.getName().getString()); // datos perdidos: vacío
        return d;
    }

    /** El binder propio que lleva el jugador (si no tiene ninguno vinculado, vincula uno sin dueño). */
    @Nullable
    public static UUID ownedBinderId(ServerPlayerEntity player) {
        ItemStack unbound = null;
        for (ItemStack st : carried(player)) {
            BinderTag tag = st.get(TcgItems.BINDER_TAG);
            if (tag == null) { if (unbound == null) unbound = st; }
            else if (tag.owner().equals(player.getUuid())) return tag.id();
        }
        if (unbound != null && bind(player, unbound) != null) return unbound.get(TcgItems.BINDER_TAG).id();
        return null;
    }

    @Nullable
    public static BinderData ownedBinder(ServerPlayerEntity player) {
        UUID id = ownedBinderId(player);
        return id == null ? null : BinderRegistry.get(player.getServer()).get(id);
    }

    /** Binder abierto en pantalla, si el jugador todavía lo lleva encima. */
    @Nullable
    private static BinderData openBinder(ServerPlayerEntity player) {
        UUID id = OPEN.get(player.getUuid());
        if (id == null) return null;
        for (ItemStack st : carried(player)) {
            BinderTag tag = st.get(TcgItems.BINDER_TAG);
            if (tag != null && tag.id().equals(id) && tag.owner().equals(player.getUuid())) return BinderRegistry.get(player.getServer()).get(id);
        }
        return null;
    }

    /** Binder para acciones: el abierto en pantalla o, si no, el propio. */
    @Nullable
    public static BinderData activeBinder(ServerPlayerEntity player) {
        BinderData d = openBinder(player);
        return d != null ? d : ownedBinder(player);
    }

    public static void dirty(ServerPlayerEntity player) {
        BinderRegistry.get(player.getServer()).markDirty();
    }

    // ------------------------------------------------------------------ operaciones

    /** Abre un binder concreto (clic derecho) o el propio (tecla). Cuenta sus cartas para los logros. */
    public static boolean openGeneral(ServerPlayerEntity player, @Nullable ItemStack stack) {
        BinderData d;
        if (stack != null) {
            d = bind(player, stack);
            if (d == null) {
                BinderTag tag = stack.get(TcgItems.BINDER_TAG);
                player.sendMessage(Text.translatable("tcgshop.binder.foreign", tag == null ? "?" : tag.ownerName()).formatted(Formatting.RED), true);
                return false;
            }
            OPEN.put(player.getUuid(), stack.get(TcgItems.BINDER_TAG).id());
        } else {
            UUID id = ownedBinderId(player);
            if (id == null) {
                player.sendMessage(Text.translatable("tcgshop.binder.need").formatted(Formatting.RED), true);
                return false;
            }
            OPEN.put(player.getUuid(), id);
            d = BinderRegistry.get(player.getServer()).get(id);
        }
        CardAdvancements.credit(player, playerData(player), d, new ArrayList<>(d.counts.keySet()));
        sync(player);
        return true;
    }

    /** Guarda cartas en el binder propio. false si el jugador no lleva ningún binder suyo. */
    public static boolean store(ServerPlayerEntity player, List<ItemStack> cards) {
        BinderData d = ownedBinder(player);
        if (d == null) return false;
        CardCollection pd = playerData(player);
        for (ItemStack st : cards) {
            if (!CardInfo.isCard(st)) continue;
            if (dev.tcgshop.grading.Grading.isGraded(st)) { player.getInventory().offerOrDrop(st.copy()); continue; } // las gradeadas no van al binder
            String id = CardInfo.id(st).toString();
            if (d.count(id) == 0 && !pd.credited.contains(id)) d.fresh.add(id);
            d.add(id, st.getCount());
            pd.totalObtained += st.getCount();
        }
        dirty(player);
        sync(player);
        return true;
    }

    public static void withdraw(ServerPlayerEntity player, String id, int amount) {
        BinderData d = openBinder(player);
        Identifier ident = Identifier.tryParse(id);
        Item item = ident == null ? null : CardInfo.item(ident);
        if (d == null || item == null || !CardInfo.isCard(ident)) return;
        int got = d.remove(id, Math.max(1, Math.min(amount, 64)));
        if (got > 0) player.getInventory().offerOrDrop(new ItemStack(item, got));
        dirty(player);
        sync(player);
    }

    public static void depositAll(ServerPlayerEntity player) {
        BinderData d = openBinder(player);
        if (d == null) return;
        PlayerInventory inv = player.getInventory();
        CardCollection pd = playerData(player);
        for (int i = 0; i < inv.size(); i++) {
            ItemStack st = inv.getStack(i);
            if (!CardInfo.isCard(st) || dev.tcgshop.grading.Grading.isGraded(st)) continue;
            String id = CardInfo.id(st).toString();
            if (d.count(id) == 0 && !pd.credited.contains(id)) d.fresh.add(id);
            d.add(id, st.getCount());
            inv.setStack(i, ItemStack.EMPTY);
        }
        dirty(player);
        sync(player);
    }

    public static void markSeen(ServerPlayerEntity player, Collection<String> ids) {
        BinderData d = openBinder(player);
        if (d != null && d.fresh.removeAll(ids)) dirty(player);
    }

    /** Doble clic en una pestaña: quita las notificaciones de ese filtro ("all", "set:<id>", "rarity:<clave>"). */
    public static void clearFresh(ServerPlayerEntity player, String filter) {
        BinderData d = openBinder(player);
        if (d == null) return;
        if (filter.equals("all")) d.fresh.clear();
        else if (filter.startsWith("set:")) {
            String set = filter.substring(4);
            d.fresh.removeIf(id -> CardInfo.setId(Identifier.of(id)).equals(set));
        } else if (filter.startsWith("rarity:")) {
            String key = filter.substring(7);
            d.fresh.removeIf(id -> {
                Item item = CardInfo.item(Identifier.of(id));
                return item != null && Rarity.of(new ItemStack(item)).key.equals(key);
            });
        }
        dirty(player);
        sync(player);
    }

    public static void toggleWish(ServerPlayerEntity player, String id) {
        Identifier ident = Identifier.tryParse(id);
        if (ident == null || !CardInfo.isCard(ident)) return;
        CardCollection pd = playerData(player);
        if (!pd.wishlist.remove(id)) pd.wishlist.add(id);
        sync(player);
    }

    public static void sync(ServerPlayerEntity player) {
        if (!ServerPlayNetworking.canSend(player, CollectionSyncPayload.ID)) return;
        BinderData d = activeBinder(player);
        CardCollection pd = playerData(player);
        ServerPlayNetworking.send(player, new CollectionSyncPayload(
                d == null ? Map.of() : d.counts,
                d == null ? List.of() : List.copyOf(d.fresh),
                List.of(pd.totalObtained, (long) pd.packsOpened, (long) pd.packsRuined, (long) pd.godPacks, (long) pd.credited.size()),
                List.copyOf(pd.wishlist),
                Titles.available(pd),
                pd.title == null ? "" : pd.title));
    }
}

package dev.tcgshop.collection;

import dev.tcgshop.TcgShop;
import dev.tcgshop.advancement.CardAdvancements;
import dev.masterball.api.card.CardInfo;
import dev.masterball.api.card.Rarity;
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
 * General Binder: each binder is UNIQUE. When first opened it becomes bound to whoever opens it (id + owner
 * on the item) and its contents are stored in BinderRegistry. If you have two binders, the second one you register
 * starts empty. Another player's binders cannot be opened.
 */
public final class CollectionService {
    @SuppressWarnings("UnstableApiUsage")
    public static final AttachmentType<CardCollection> PLAYER_DATA = AttachmentRegistry.<CardCollection>builder()
            .persistent(CardCollection.CODEC)
            .copyOnDeath()
            .initializer(CardCollection::new)
            .buildAndRegister(Identifier.of(TcgShop.MOD_ID, "collection"));

    /** Binder each player has open on screen (screen actions go to that one). */
    private static final Map<UUID, UUID> OPEN = new HashMap<>();

    private CollectionService() {}

    public static void init() {}

    @SuppressWarnings("UnstableApiUsage")
    public static CardCollection playerData(ServerPlayerEntity player) {
        return player.getAttachedOrCreate(PLAYER_DATA);
    }

    // ------------------------------------------------------------------ locate / bind binders

    /** All General Binders the player carries (inventory + Accessories slots). */
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
     * Binds the binder to the player if it has no owner yet. Returns its contents, or null if it belongs to another player.
     * The first time a player registers a binder, they receive their collection from previous versions of the mod.
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
            if (!pd.legacyCounts.isEmpty()) { // migration from the previous version
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
        if (d == null) d = reg.create(tag.id(), player.getUuid(), player.getName().getString()); // lost data: empty
        return d;
    }

    /** The player's own binder they carry (if none is bound, binds an unowned one). */
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

    /** Binder open on screen, if the player still carries it. */
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

    /** Binder for actions: the one open on screen or, failing that, the player's own. */
    @Nullable
    public static BinderData activeBinder(ServerPlayerEntity player) {
        BinderData d = openBinder(player);
        return d != null ? d : ownedBinder(player);
    }

    public static void dirty(ServerPlayerEntity player) {
        BinderRegistry.get(player.getServer()).markDirty();
    }

    // ------------------------------------------------------------------ operations

    /** Opens a specific binder (right click) or the player's own (key). Counts its cards for advancements. */
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

    /** Stores cards in the player's own binder. false if the player carries no binder of their own. */
    public static boolean store(ServerPlayerEntity player, List<ItemStack> cards) {
        BinderData d = ownedBinder(player);
        if (d == null) return false;
        CardCollection pd = playerData(player);
        for (ItemStack st : cards) {
            if (!CardInfo.isCard(st)) continue;
            if (dev.tcgshop.grading.Grading.isGraded(st)) { player.getInventory().offerOrDrop(st.copy()); continue; } // graded cards don't go into the binder
            if (dev.masterball.api.card.PrintVariant.isSpecial(st)) { // neither do variants (reverse, 1st edition...), but they count toward the collection
                player.getInventory().offerOrDrop(st.copy());
                pd.totalObtained += st.getCount();
                String vid = CardInfo.id(st).toString();
                if (!pd.credited.contains(vid)) CardAdvancements.credit(player, pd, d, List.of(vid));
                continue;
            }
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
            if (!CardInfo.isCard(st) || dev.tcgshop.grading.Grading.isGraded(st) || dev.masterball.api.card.PrintVariant.isSpecial(st)) continue;
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

    /** Double click on a tab: clears the notifications for that filter ("all", "set:<id>", "rarity:<key>"). */
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
                return item != null && Rarity.of(new ItemStack(item)).family.key.equals(key);
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

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
        List<String> ids = new ArrayList<>(d.counts.keySet());
        for (BinderData.Special s : d.specials) { // print variants count toward the collection too (graded slabs don't)
            String id = CardInfo.id(s.stack).toString();
            if (!dev.tcgshop.grading.Grading.isGraded(s.stack) && !ids.contains(id)) ids.add(id);
        }
        CardAdvancements.credit(player, playerData(player), d, ids);
        sync(player);
        return true;
    }

    /**
     * Can {@link #store} file cards for this player? (an own/unbound General Binder, or failing that an own/unbound
     * Personal Binder such as the 1.0 starter binder). Pack opening should use this instead of only checking the
     * General Binder, so starter players' pack cards go into their Personal Binder.
     */
    public static boolean hasCardStorage(ServerPlayerEntity player) {
        return ownedBinderId(player) != null || !dev.tcgshop.binder.PersonalBinders.ownedOrUnbound(player, null).isEmpty();
    }

    /**
     * Stores cards in the player's own General Binder or, if they carry none, in their Personal Binder (1.0: the
     * starter binder in the accessory slot). Every card item can be filed: plain cards by id, print variants and graded
     * cards as full stacks (before 1.0 those two were silently handed back, which is why "some cards stayed in your hand").
     * Anything that does not fit (full Personal Binder) or is not a card goes to the inventory, with a message.
     * false only if the player carries no binder at all (the caller then gives the cards itself).
     */
    public static boolean store(ServerPlayerEntity player, List<ItemStack> cards) {
        BinderData d = ownedBinder(player);
        if (d == null) return dev.tcgshop.binder.BinderServer.storePersonal(player, cards);
        CardCollection pd = playerData(player);
        List<String> newVariants = new ArrayList<>();
        for (ItemStack st : cards) {
            if (st.isEmpty()) continue;
            if (!CardInfo.isCard(st)) { player.getInventory().offerOrDrop(st.copy()); continue; } // never lose a non-card
            file(d, pd, st, newVariants);
            if (!dev.tcgshop.grading.Grading.isGraded(st)) pd.totalObtained += st.getCount();
        }
        if (!newVariants.isEmpty()) CardAdvancements.credit(player, pd, d, newVariants);
        dirty(player);
        sync(player);
        return true;
    }

    /**
     * Files one stack into a General Binder: plain cards by id (marked as new if never seen), variants and graded cards
     * as special stacks. Variant ids not yet counted for advancements are added to creditLater.
     */
    private static void file(BinderData d, CardCollection pd, ItemStack st, List<String> creditLater) {
        String id = CardInfo.id(st).toString();
        if (BinderData.isSpecial(st)) {
            d.addSpecial(st, st.getCount());
            if (!dev.tcgshop.grading.Grading.isGraded(st) && !pd.credited.contains(id) && !creditLater.contains(id)) creditLater.add(id);
            return;
        }
        if (d.count(id) == 0 && !pd.credited.contains(id)) d.fresh.add(id);
        d.add(id, st.getCount());
    }

    /** Upper bound of cards per withdraw request (a full inventory of stacks). */
    private static final int MAX_WITHDRAW = 64 * 36;
    /** Upper bound of entries per batch request (the client splits bigger selections into several packets). */
    private static final int MAX_BATCH = 400;

    /** Withdraws copies of one card. id = card id, or "#" + signature for a special card (variant / graded). */
    public static void withdraw(ServerPlayerEntity player, String id, int amount) {
        BinderData d = openBinder(player);
        if (d == null) return;
        ItemStack out = takeOut(d, id, Math.max(1, Math.min(amount, 64)));
        if (!out.isEmpty()) player.getInventory().offerOrDrop(out);
        dirty(player);
        sync(player);
    }

    /** Takes up to n copies of that entry out of the binder (validated). EMPTY if there is nothing valid to take. */
    private static ItemStack takeOut(BinderData d, String key, int n) {
        if (n <= 0) return ItemStack.EMPTY;
        if (key.startsWith("#")) {
            BinderData.Special s = d.findSpecial(key.substring(1));
            return s == null ? ItemStack.EMPTY : d.removeSpecial(s, n);
        }
        Identifier ident = Identifier.tryParse(key);
        Item item = ident == null ? null : CardInfo.item(ident);
        if (item == null || !CardInfo.isCard(ident)) return ItemStack.EMPTY;
        int got = d.remove(key, n);
        return got > 0 ? new ItemStack(item, got) : ItemStack.EMPTY;
    }

    /**
     * Multi-select: takes several cards out at once. arg = "key=amount;key=amount..." (key as in {@link #withdraw}).
     * Only what fits in the inventory is taken (nothing is dropped on the ground); the rest stays in the binder.
     */
    public static void withdrawMany(ServerPlayerEntity player, String arg) {
        BinderData d = openBinder(player);
        if (d == null || arg == null || arg.isEmpty()) return;
        String[] parts = arg.split(";");
        java.util.Set<String> seen = new java.util.HashSet<>();
        int taken = 0, stayed = 0;
        for (int i = 0; i < parts.length && i < MAX_BATCH; i++) {
            String part = parts[i];
            int eq = part.lastIndexOf('=');
            if (eq <= 0) continue;
            String key = part.substring(0, eq);
            int n;
            try { n = Integer.parseInt(part.substring(eq + 1)); } catch (NumberFormatException e) { continue; }
            if (n <= 0 || !seen.add(key)) continue;
            ItemStack out = takeOut(d, key, Math.min(n, MAX_WITHDRAW));
            if (out.isEmpty()) continue;
            int total = out.getCount();
            int left = giveToInventory(player, out);
            if (left > 0) { // no room: put the rest back where it was
                ItemStack back = out.copyWithCount(left);
                if (BinderData.isSpecial(back)) d.addSpecial(back, left);
                else d.add(CardInfo.id(back).toString(), left);
                stayed += left;
            }
            taken += total - left;
        }
        if (taken > 0) player.sendMessage(Text.translatable("tcgshop.binder.took_many", taken).formatted(Formatting.GREEN), true);
        if (stayed > 0) player.sendMessage(Text.translatable("tcgshop.binder.inv_full", stayed).formatted(Formatting.RED), false);
        dirty(player);
        sync(player);
    }

    /**
     * Puts the stack in the inventory without dropping anything. Returns how many did not fit (the stack is not
     * modified). In creative the vanilla insert would delete what does not fit, so the free space is checked first.
     */
    public static int giveToInventory(ServerPlayerEntity player, ItemStack stack) {
        if (stack.isEmpty()) return 0;
        int room = roomFor(player.getInventory(), stack);
        int put = Math.min(room, stack.getCount());
        if (put > 0) {
            ItemStack in = stack.copyWithCount(put);
            player.getInventory().insertStack(in);
            if (!in.isEmpty()) { put -= in.getCount(); } // should not happen: room was checked
        }
        return stack.getCount() - put;
    }

    /** How many more of this exact item fit in the main inventory (36 slots). */
    private static int roomFor(PlayerInventory inv, ItemStack stack) {
        int room = 0, max = stack.getMaxCount();
        for (int i = 0; i < inv.main.size(); i++) {
            ItemStack s = inv.main.get(i);
            if (s.isEmpty()) room += max;
            else if (ItemStack.areItemsAndComponentsEqual(s, stack)) room += Math.max(0, Math.min(max, s.getMaxCount()) - s.getCount());
        }
        return room;
    }

    public static void depositAll(ServerPlayerEntity player) {
        BinderData d = openBinder(player);
        if (d == null) return;
        PlayerInventory inv = player.getInventory();
        CardCollection pd = playerData(player);
        List<String> newVariants = new ArrayList<>();
        int filed = 0;
        for (int i = 0; i < inv.size(); i++) {
            ItemStack st = inv.getStack(i);
            if (!CardInfo.isCard(st)) continue;
            file(d, pd, st, newVariants);
            filed += st.getCount();
            inv.setStack(i, ItemStack.EMPTY);
        }
        if (!newVariants.isEmpty()) CardAdvancements.credit(player, pd, d, newVariants);
        player.sendMessage(filed > 0 ? Text.translatable("tcgshop.binder.deposited", filed).formatted(Formatting.GREEN)
                : Text.translatable("tcgshop.binder.nothing_to_deposit").formatted(Formatting.GRAY), true);
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
        List<ItemStack> specials = new ArrayList<>();
        List<Integer> specialCounts = new ArrayList<>();
        if (d != null) for (BinderData.Special s : d.specials) {
            if (s.count <= 0 || s.stack.isEmpty()) continue;
            specials.add(s.stack.copy());
            specialCounts.add(s.count);
        }
        ServerPlayNetworking.send(player, new CollectionSyncPayload(
                d == null ? Map.of() : d.counts,
                d == null ? List.of() : List.copyOf(d.fresh),
                List.of(pd.totalObtained, (long) pd.packsOpened, (long) pd.packsRuined, (long) pd.godPacks, (long) pd.credited.size()),
                List.copyOf(pd.wishlist),
                Titles.available(pd),
                pd.title == null ? "" : pd.title,
                specials,
                specialCounts,
                d != null));
    }
}

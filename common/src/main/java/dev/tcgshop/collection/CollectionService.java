package dev.tcgshop.collection;

import dev.masterball.api.platform.Net;
import dev.masterball.api.platform.Platform;
import dev.masterball.api.platform.PlayerData;
import dev.tcgshop.TcgShop;
import dev.tcgshop.advancement.CardAdvancements;
import dev.masterball.api.card.CardInfo;
import dev.masterball.api.card.Rarity;
import dev.tcgshop.compat.AccessoriesCompat;
import dev.tcgshop.item.TcgItems;
import dev.tcgshop.network.CollectionSyncPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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
    public static final PlayerData<CardCollection> PLAYER_DATA = Platform.INSTANCE.playerData(
            ResourceLocation.fromNamespaceAndPath(TcgShop.MOD_ID, "collection"), CardCollection.CODEC, CardCollection::new, true);

    /** Binder each player has open on screen (screen actions go to that one). */
    private static final Map<UUID, UUID> OPEN = new HashMap<>();

    private CollectionService() {}

    public static void init() {}

    public static CardCollection playerData(ServerPlayer player) {
        return PLAYER_DATA.get(player);
    }

    // ------------------------------------------------------------------ locate / bind binders

    /** All General Binders the player carries (inventory + Accessories slots). */
    public static List<ItemStack> carried(ServerPlayer player) {
        List<ItemStack> out = new ArrayList<>();
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).is(TcgItems.GENERAL_BINDER)) out.add(inv.getItem(i));
        out.addAll(AccessoriesCompat.equipped(player, TcgItems.GENERAL_BINDER));
        return out;
    }

    public static boolean hasGeneralBinder(ServerPlayer player) {
        return !carried(player).isEmpty();
    }

    /**
     * Binds the binder to the player if it has no owner yet. Returns its contents, or null if it belongs to another player.
     * The first time a player registers a binder, they receive their collection from previous versions of the mod.
     */
    @Nullable
    public static BinderData bind(ServerPlayer player, ItemStack stack) {
        BinderRegistry reg = BinderRegistry.get(player.getServer());
        BinderTag tag = stack.get(TcgItems.BINDER_TAG);
        if (tag == null) {
            UUID id = UUID.randomUUID();
            stack.set(TcgItems.BINDER_TAG, new BinderTag(id, player.getUUID(), player.getName().getString()));
            BinderData d = reg.create(id, player.getUUID(), player.getName().getString());
            CardCollection pd = playerData(player);
            if (!pd.legacyCounts.isEmpty()) { // migration from the previous version
                d.counts.putAll(pd.legacyCounts);
                d.fresh.addAll(pd.legacyFresh);
                pd.legacyCounts.clear();
                pd.legacyFresh.clear();
            }
            reg.setDirty();
            player.displayClientMessage(Component.translatable("tcgshop.binder.bound").withStyle(ChatFormatting.GREEN), true);
            return d;
        }
        if (!tag.owner().equals(player.getUUID())) return null;
        BinderData d = reg.get(tag.id());
        if (d == null) d = reg.create(tag.id(), player.getUUID(), player.getName().getString()); // lost data: empty
        return d;
    }

    /** The player's own binder they carry (if none is bound, binds an unowned one). */
    @Nullable
    public static UUID ownedBinderId(ServerPlayer player) {
        ItemStack unbound = null;
        for (ItemStack st : carried(player)) {
            BinderTag tag = st.get(TcgItems.BINDER_TAG);
            if (tag == null) { if (unbound == null) unbound = st; }
            else if (tag.owner().equals(player.getUUID())) return tag.id();
        }
        if (unbound != null && bind(player, unbound) != null) return unbound.get(TcgItems.BINDER_TAG).id();
        return null;
    }

    @Nullable
    public static BinderData ownedBinder(ServerPlayer player) {
        UUID id = ownedBinderId(player);
        return id == null ? null : BinderRegistry.get(player.getServer()).get(id);
    }

    /** Binder open on screen, if the player still carries it. */
    @Nullable
    private static BinderData openBinder(ServerPlayer player) {
        UUID id = OPEN.get(player.getUUID());
        if (id == null) return null;
        for (ItemStack st : carried(player)) {
            BinderTag tag = st.get(TcgItems.BINDER_TAG);
            if (tag != null && tag.id().equals(id) && tag.owner().equals(player.getUUID())) return BinderRegistry.get(player.getServer()).get(id);
        }
        return null;
    }

    /** Binder for actions: the one open on screen or, failing that, the player's own. */
    @Nullable
    public static BinderData activeBinder(ServerPlayer player) {
        BinderData d = openBinder(player);
        return d != null ? d : ownedBinder(player);
    }

    public static void dirty(ServerPlayer player) {
        BinderRegistry.get(player.getServer()).setDirty();
    }

    // ------------------------------------------------------------------ operations

    /** Opens a specific binder (right click) or the player's own (key). Counts its cards for advancements. */
    public static boolean openGeneral(ServerPlayer player, @Nullable ItemStack stack) {
        BinderData d;
        if (stack != null) {
            d = bind(player, stack);
            if (d == null) {
                BinderTag tag = stack.get(TcgItems.BINDER_TAG);
                player.displayClientMessage(Component.translatable("tcgshop.binder.foreign", tag == null ? "?" : tag.ownerName()).withStyle(ChatFormatting.RED), true);
                return false;
            }
            OPEN.put(player.getUUID(), stack.get(TcgItems.BINDER_TAG).id());
        } else {
            UUID id = ownedBinderId(player);
            if (id == null) {
                player.displayClientMessage(Component.translatable("tcgshop.binder.need").withStyle(ChatFormatting.RED), true);
                return false;
            }
            OPEN.put(player.getUUID(), id);
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
    public static boolean hasCardStorage(ServerPlayer player) {
        return ownedBinderId(player) != null || !dev.tcgshop.binder.PersonalBinders.ownedOrUnbound(player, null).isEmpty();
    }

    /**
     * Stores cards in the player's own General Binder or, if they carry none, in their Personal Binder (1.0: the
     * starter binder in the accessory slot). Every card item can be filed: plain cards by id, print variants and graded
     * cards as full stacks (before 1.0 those two were silently handed back, which is why "some cards stayed in your hand").
     * Anything that does not fit (full Personal Binder) or is not a card goes to the inventory, with a message.
     * false only if the player carries no binder at all (the caller then gives the cards itself).
     */
    public static boolean store(ServerPlayer player, List<ItemStack> cards) {
        BinderData d = ownedBinder(player);
        if (d == null) return dev.tcgshop.binder.BinderServer.storePersonal(player, cards);
        CardCollection pd = playerData(player);
        List<String> newVariants = new ArrayList<>();
        for (ItemStack st : cards) {
            if (st.isEmpty()) continue;
            if (!CardInfo.isCard(st)) { player.getInventory().placeItemBackInInventory(st.copy()); continue; } // never lose a non-card
            // 1.0 beta 3: a card stolen from a collector who is still hunting for it stays in the inventory
            if (dev.tcgshop.entity.CollectorTheft.refuseHot(player, st)) { player.getInventory().placeItemBackInInventory(st.copy()); continue; }
            file(d, pd, st, newVariants);
            if (!dev.tcgshop.grading.Grading.isGraded(st)) pd.totalObtained += st.getCount();
        }
        if (!newVariants.isEmpty()) CardAdvancements.credit(player, pd, d, newVariants);
        else CardAdvancements.rewardCompletedSets(player, pd, d); // 1.1: set completion reward as soon as the last card is filed
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
    public static void withdraw(ServerPlayer player, String id, int amount) {
        BinderData d = openBinder(player);
        if (d == null) return;
        ItemStack out = takeOut(d, id, Math.max(1, Math.min(amount, 64)));
        if (!out.isEmpty()) player.getInventory().placeItemBackInInventory(out);
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
        ResourceLocation ident = ResourceLocation.tryParse(key);
        Item item = ident == null ? null : CardInfo.item(ident);
        if (item == null || !CardInfo.isCard(ident)) return ItemStack.EMPTY;
        int got = d.remove(key, n);
        return got > 0 ? new ItemStack(item, got) : ItemStack.EMPTY;
    }

    /**
     * Multi-select: takes several cards out at once. arg = "key=amount;key=amount..." (key as in {@link #withdraw}).
     * Only what fits in the inventory is taken (nothing is dropped on the ground); the rest stays in the binder.
     */
    public static void withdrawMany(ServerPlayer player, String arg) {
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
        if (taken > 0) player.displayClientMessage(Component.translatable("tcgshop.binder.took_many", taken).withStyle(ChatFormatting.GREEN), true);
        if (stayed > 0) player.displayClientMessage(Component.translatable("tcgshop.binder.inv_full", stayed).withStyle(ChatFormatting.RED), false);
        dirty(player);
        sync(player);
    }

    /**
     * Puts the stack in the inventory without dropping anything. Returns how many did not fit (the stack is not
     * modified). In creative the vanilla insert would delete what does not fit, so the free space is checked first.
     */
    public static int giveToInventory(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) return 0;
        int room = roomFor(player.getInventory(), stack);
        int put = Math.min(room, stack.getCount());
        if (put > 0) {
            ItemStack in = stack.copyWithCount(put);
            player.getInventory().add(in);
            if (!in.isEmpty()) { put -= in.getCount(); } // should not happen: room was checked
        }
        return stack.getCount() - put;
    }

    /** How many more of this exact item fit in the main inventory (36 slots). */
    private static int roomFor(Inventory inv, ItemStack stack) {
        int room = 0, max = stack.getMaxStackSize();
        for (int i = 0; i < inv.items.size(); i++) {
            ItemStack s = inv.items.get(i);
            if (s.isEmpty()) room += max;
            else if (ItemStack.isSameItemSameComponents(s, stack)) room += Math.max(0, Math.min(max, s.getMaxStackSize()) - s.getCount());
        }
        return room;
    }

    public static void depositAll(ServerPlayer player) {
        BinderData d = openBinder(player);
        if (d == null) return;
        Inventory inv = player.getInventory();
        CardCollection pd = playerData(player);
        List<String> newVariants = new ArrayList<>();
        int filed = 0;
        boolean hot = false;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (!CardInfo.isCard(st)) continue;
            if (dev.tcgshop.entity.CollectorTheft.isHot(player.getServer(), st)) { hot = true; continue; } // 1.0 beta 3: stays in the inventory
            file(d, pd, st, newVariants);
            filed += st.getCount();
            inv.setItem(i, ItemStack.EMPTY);
        }
        if (!newVariants.isEmpty()) CardAdvancements.credit(player, pd, d, newVariants);
        if (hot) dev.tcgshop.entity.CollectorTheft.sayHot(player);
        player.displayClientMessage(filed > 0 ? Component.translatable("tcgshop.binder.deposited", filed).withStyle(ChatFormatting.GREEN)
                : Component.translatable("tcgshop.binder.nothing_to_deposit").withStyle(ChatFormatting.GRAY), true);
        dirty(player);
        sync(player);
    }

    public static void markSeen(ServerPlayer player, Collection<String> ids) {
        BinderData d = openBinder(player);
        if (d != null && d.fresh.removeAll(ids)) dirty(player);
    }

    /** Double click on a tab: clears the notifications for that filter ("all", "set:<id>", "rarity:<key>"). */
    public static void clearFresh(ServerPlayer player, String filter) {
        BinderData d = openBinder(player);
        if (d == null) return;
        if (filter.equals("all")) d.fresh.clear();
        else if (filter.startsWith("set:")) {
            String set = filter.substring(4);
            d.fresh.removeIf(id -> CardInfo.setId(ResourceLocation.parse(id)).equals(set));
        } else if (filter.startsWith("rarity:")) {
            String key = filter.substring(7);
            d.fresh.removeIf(id -> {
                Item item = CardInfo.item(ResourceLocation.parse(id));
                return item != null && Rarity.of(new ItemStack(item)).family.key.equals(key);
            });
        }
        dirty(player);
        sync(player);
    }

    public static void toggleWish(ServerPlayer player, String id) {
        ResourceLocation ident = ResourceLocation.tryParse(id);
        if (ident == null || !CardInfo.isCard(ident)) return;
        CardCollection pd = playerData(player);
        if (!pd.wishlist.remove(id)) pd.wishlist.add(id);
        sync(player);
    }

    public static void sync(ServerPlayer player) {
        if (!Net.INSTANCE.canSend(player, CollectionSyncPayload.ID)) return;
        BinderData d = activeBinder(player);
        CardCollection pd = playerData(player);
        List<ItemStack> specials = new ArrayList<>();
        List<Integer> specialCounts = new ArrayList<>();
        if (d != null) for (BinderData.Special s : d.specials) {
            if (s.count <= 0 || s.stack.isEmpty()) continue;
            specials.add(s.stack.copy());
            specialCounts.add(s.count);
        }
        Net.INSTANCE.send(player, new CollectionSyncPayload(
                d == null ? Map.of() : d.counts,
                d == null ? List.of() : List.copyOf(d.fresh),
                // 1.1: [5] set completion reward money, [6] its Master Balls (0 without Cobblemon), shown in the General Binder
                List.of(pd.totalObtained, (long) pd.packsOpened, (long) pd.packsRuined, (long) pd.godPacks, (long) pd.credited.size(),
                        dev.tcgshop.EconomyConfig.INSTANCE.setCompleteMoney,
                        (long) (dev.tcgshop.advancement.CardAdvancements.masterBall() == null ? 0 : dev.tcgshop.EconomyConfig.INSTANCE.setCompleteMasterBalls)),
                List.copyOf(pd.wishlist),
                Titles.available(pd),
                pd.title == null ? "" : pd.title,
                specials,
                specialCounts,
                d != null));
    }
}

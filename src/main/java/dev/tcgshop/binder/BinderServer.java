package dev.tcgshop.binder;

import dev.tcgshop.advancement.CardAdvancements;
import dev.masterball.api.card.CardCatalog;
import dev.masterball.api.card.CardInfo;
import dev.tcgshop.collection.BinderData;
import dev.tcgshop.collection.BinderRegistry;
import dev.tcgshop.collection.BinderSlots;
import dev.tcgshop.collection.BinderTag;
import dev.tcgshop.collection.CardCollection;
import dev.tcgshop.collection.CollectionService;
import dev.tcgshop.collection.Titles;
import dev.tcgshop.item.TcgItems;
import dev.tcgshop.network.BinderActionPayload;
import dev.tcgshop.network.OpenBinderPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Server: opening binders and applying the actions that come from their screens. */
public final class BinderServer {
    /** Prefix of the "wanted card" slots in the Personal Binder (they don't consume cards). */
    public static final String WISH = "wish:";

    private BinderServer() {}

    public static void openGeneral(ServerPlayerEntity player, @Nullable ItemStack stack) {
        if (stack == null && CollectionService.ownedBinderId(player) == null) {
            // No General Binder: the binder key opens the Personal Binder (1.0 starter binder, e.g. in the accessory slot)
            ItemStack personal = PersonalBinders.ownedOrUnbound(player, null);
            if (!personal.isEmpty()) {
                openPersonal(player, personal, Hand.MAIN_HAND);
                return;
            }
        }
        if (CollectionService.openGeneral(player, stack)) send(player, new OpenBinderPayload(0, 0, List.of()));
    }

    public static void openPersonal(ServerPlayerEntity player, Hand hand) {
        ItemStack st = player.getStackInHand(hand);
        if (!st.isOf(TcgItems.PERSONAL_BINDER)) return;
        openPersonal(player, st, hand);
    }

    private static void openPersonal(ServerPlayerEntity player, ItemStack st, Hand hand) {
        BinderTag tag = st.get(TcgItems.BINDER_TAG);
        if (tag == null) {
            st.set(TcgItems.BINDER_TAG, new BinderTag(UUID.randomUUID(), player.getUuid(), player.getName().getString()));
            player.sendMessage(Text.translatable("tcgshop.binder.bound").formatted(Formatting.GREEN), true);
        } else if (!tag.owner().equals(player.getUuid())) {
            player.sendMessage(Text.translatable("tcgshop.binder.foreign", tag.ownerName()).formatted(Formatting.RED), true);
            return;
        }
        PersonalBinderData d = st.getOrDefault(TcgItems.PERSONAL_BINDER_DATA, PersonalBinderData.DEFAULT);
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < d.slots().size(); i++) {
            String s = d.slots().get(i);
            if (s.isEmpty() || s.startsWith(WISH) || CardCatalog.isRemovedId(s)) continue;
            ItemStack sp = d.special(i);
            if (sp != null && dev.tcgshop.grading.Grading.isGraded(sp)) continue; // graded slabs don't count
            ids.add(s);
        }
        CardAdvancements.credit(player, CollectionService.playerData(player), null, ids);
        CardAdvancements.grant(player, "first_binder");
        CollectionService.sync(player); // the drawer shows the cards in your General Binder
        List<String> unlocked = new ArrayList<>();
        for (BinderStyles.Style s : BinderStyles.ALL) {
            if (s.advancement() == null || CardAdvancements.isDone(player, s.advancement())) unlocked.add(s.kind() + "=" + s.id());
        }
        send(player, new OpenBinderPayload(1, hand.ordinal(), unlocked));
    }

    public static void handle(BinderActionPayload p, ServerPlayerEntity player) {
        switch (p.action()) {
            case BinderActionPayload.OPEN_GENERAL -> openGeneral(player, null);
            case BinderActionPayload.WITHDRAW -> CollectionService.withdraw(player, p.arg(), p.amount());
            case BinderActionPayload.WITHDRAW_MANY -> CollectionService.withdrawMany(player, p.arg());
            case BinderActionPayload.DEPOSIT_ALL -> CollectionService.depositAll(player);
            case BinderActionPayload.MARK_SEEN -> CollectionService.markSeen(player, Arrays.asList(p.arg().split(",")));
            case BinderActionPayload.CLEAR_FRESH -> CollectionService.clearFresh(player, p.arg());
            case BinderActionPayload.WISH_TOGGLE -> CollectionService.toggleWish(player, p.arg());
            case BinderActionPayload.TITLE_SET -> {
                Titles.select(player, p.arg());
                CollectionService.sync(player);
            }
            case BinderActionPayload.PERSONAL_PLACE, BinderActionPayload.PERSONAL_REMOVE,
                 BinderActionPayload.PERSONAL_ADD_PAGE, BinderActionPayload.PERSONAL_STYLE,
                 BinderActionPayload.PERSONAL_REMOVE_MANY, BinderActionPayload.PERSONAL_MOVE,
                 BinderActionPayload.PERSONAL_DEPOSIT -> personal(p, player);
            default -> {}
        }
    }

    /**
     * Personal Binder actions. Cards move between your General Binder and the personal one; without a General Binder
     * (1.0 starter binder) they come from and go back to the inventory.
     */
    private static void personal(BinderActionPayload p, ServerPlayerEntity player) {
        ItemStack st = PersonalBinders.owned(player, null);
        if (st.isEmpty()) return;
        PersonalBinderData d = st.getOrDefault(TcgItems.PERSONAL_BINDER_DATA, PersonalBinderData.DEFAULT);
        BinderData general = CollectionService.ownedBinder(player);
        boolean generalChanged = false;

        switch (p.action()) {
            case BinderActionPayload.PERSONAL_PLACE -> {
                int slot = p.amount();
                if (slot < 0 || slot >= d.limit()) return;
                String arg = p.arg();
                boolean wish = arg.startsWith(WISH);
                Identifier id = Identifier.tryParse(wish ? arg.substring(WISH.length()) : arg);
                if (id == null || !CardInfo.isCard(id)) return;
                if (!wish) {
                    if (general == null) {
                        player.sendMessage(Text.translatable("tcgshop.binder.need").formatted(Formatting.RED), true);
                        return;
                    }
                    if (general.count(arg) <= 0) return; // you don't have it in the General Binder
                }
                String prev = d.slots().get(slot);
                if (isRealCard(prev) && !returnCard(player, general, prev, d.special(slot))) return;
                if (!wish) general.remove(arg, 1);
                d = d.withSlot(slot, arg);
                generalChanged = true;
            }
            case BinderActionPayload.PERSONAL_REMOVE -> {
                int slot = p.amount();
                if (slot < 0 || slot >= d.slots().size() || d.slots().get(slot).isEmpty()) return;
                String prev = d.slots().get(slot);
                // wishes and TCG Pocket cards (removed) just empty the slot
                if (isRealCard(prev) && !returnCard(player, general, prev, d.special(slot))) return;
                d = d.withSlot(slot, "");
                generalChanged = true;
            }
            case BinderActionPayload.PERSONAL_REMOVE_MANY -> {
                Set<Integer> slots = new LinkedHashSet<>();
                for (String s : p.arg().split(",")) {
                    try { slots.add(Integer.parseInt(s.trim())); } catch (NumberFormatException ignored) {}
                    if (slots.size() >= d.slots().size()) break;
                }
                int moved = 0, stayed = 0;
                for (int slot : slots) {
                    if (slot < 0 || slot >= d.slots().size()) continue;
                    String prev = d.slots().get(slot);
                    if (prev.isEmpty()) continue;
                    if (isRealCard(prev)) {
                        if (stayed > 0 || !returnCard(player, general, prev, d.special(slot), false)) { stayed++; continue; }
                        moved++;
                    }
                    d = d.withSlot(slot, "");
                }
                if (moved > 0) player.sendMessage(Text.translatable(general != null ? "tcgshop.personal.took_many_general" : "tcgshop.personal.took_many_inv",
                        moved).formatted(Formatting.GREEN), true);
                if (stayed > 0) player.sendMessage(Text.translatable("tcgshop.personal.inv_full_many", stayed).formatted(Formatting.RED), false);
                generalChanged = true;
            }
            case BinderActionPayload.PERSONAL_MOVE -> {
                int from, to = p.amount();
                try { from = Integer.parseInt(p.arg().trim()); } catch (NumberFormatException e) { return; }
                if (from == to || from < 0 || from >= d.slots().size() || to < 0 || to >= d.limit() || d.slots().get(from).isEmpty()) return;
                d = d.swap(from, to);
            }
            case BinderActionPayload.PERSONAL_DEPOSIT -> d = depositInventory(player, d);
            case BinderActionPayload.PERSONAL_ADD_PAGE -> {
                if (d.fixedSize()) {
                    player.sendMessage(Text.translatable("tcgshop.personal.fixed_size", d.capacity()).formatted(Formatting.RED), true);
                    return;
                }
                if (d.pages() >= PersonalBinderData.MAX_PAGES) return;
                d = d.withPages(d.pages() + 2);
            }
            case BinderActionPayload.PERSONAL_STYLE -> {
                String[] kv = p.arg().split("=", 2);
                if (kv.length != 2) return;
                BinderStyles.Style s = BinderStyles.find(kv[0], kv[1]);
                if (s == null) return;
                if (s.advancement() != null && !CardAdvancements.isDone(player, s.advancement())) return;
                d = d.withStyle(kv[0], kv[1]);
                CardAdvancements.grant(player, "customize_binder");
            }
            default -> { return; }
        }
        st.set(TcgItems.PERSONAL_BINDER_DATA, d);
        if (generalChanged && general != null) BinderRegistry.get(player.getServer()).markDirty();
        CollectionService.sync(player);
    }

    private static boolean isRealCard(String slot) {
        return !slot.isEmpty() && !slot.startsWith(WISH) && !CardCatalog.isRemovedId(slot);
    }

    private static boolean returnCard(ServerPlayerEntity player, @Nullable BinderData general, String id, @Nullable ItemStack special) {
        return returnCard(player, general, id, special, true);
    }

    /**
     * A card leaves a pocket: to the General Binder if you have one, otherwise to the inventory. false (and nothing
     * changes) if the inventory has no room.
     */
    private static boolean returnCard(ServerPlayerEntity player, @Nullable BinderData general, String id, @Nullable ItemStack special, boolean warn) {
        if (general != null) {
            if (special != null) general.addSpecial(special, 1);
            else general.add(id, 1);
            return true;
        }
        ItemStack out = special != null ? special.copyWithCount(1) : BinderSlots.stackFor(id, 1);
        if (out.isEmpty()) return true; // the card no longer exists: the pocket is just emptied
        if (CollectionService.giveToInventory(player, out) > 0) {
            if (warn) player.sendMessage(Text.translatable("tcgshop.personal.inv_full").formatted(Formatting.RED), true);
            return false;
        }
        return true;
    }

    // ------------------------------------------------------------------ filing cards into a Personal Binder

    /** Pocket where this card goes: one reserved for it as a wish, else the first free one. -1 if full. */
    private static int pickSlot(PersonalBinderData d, ItemStack card) {
        int w = d.wishSlot(CardInfo.id(card).toString());
        return w >= 0 ? w : d.firstFree();
    }

    private static PersonalBinderData placeOne(PersonalBinderData d, int slot, ItemStack card) {
        return BinderData.isSpecial(card) ? d.withSpecial(slot, card) : d.withSlot(slot, CardInfo.id(card).toString());
    }

    /**
     * Files cards straight into the player's Personal Binder (used by CollectionService.store when they carry no General
     * Binder). Cards that don't fit go to the inventory with a clear "binder full" message. false if they carry no
     * Personal Binder of their own either.
     */
    public static boolean storePersonal(ServerPlayerEntity player, List<ItemStack> cards) {
        ItemStack st = PersonalBinders.ownedOrUnbound(player, null);
        if (st.isEmpty()) return false;
        if (st.get(TcgItems.BINDER_TAG) == null) {
            st.set(TcgItems.BINDER_TAG, new BinderTag(UUID.randomUUID(), player.getUuid(), player.getName().getString()));
            player.sendMessage(Text.translatable("tcgshop.binder.bound").formatted(Formatting.GREEN), false);
        }
        PersonalBinderData d = st.getOrDefault(TcgItems.PERSONAL_BINDER_DATA, PersonalBinderData.DEFAULT);
        CardCollection pd = CollectionService.playerData(player);
        List<String> credit = new ArrayList<>();
        int filed = 0, left = 0;
        for (ItemStack card : cards) {
            if (card.isEmpty()) continue;
            if (!CardInfo.isCard(card)) { player.getInventory().offerOrDrop(card.copy()); continue; }
            boolean graded = dev.tcgshop.grading.Grading.isGraded(card);
            if (!graded) pd.totalObtained += card.getCount();
            ItemStack rest = card.copy();
            while (!rest.isEmpty()) {
                int slot = pickSlot(d, rest);
                if (slot < 0) break;
                d = placeOne(d, slot, rest);
                rest.decrement(1);
                filed++;
            }
            String id = CardInfo.id(card).toString();
            if (!graded && rest.getCount() < card.getCount() && !credit.contains(id)) credit.add(id);
            if (!rest.isEmpty()) {
                left += rest.getCount();
                player.getInventory().offerOrDrop(rest);
            }
        }
        st.set(TcgItems.PERSONAL_BINDER_DATA, d);
        if (!credit.isEmpty()) CardAdvancements.credit(player, pd, null, credit);
        notifyFiled(player, d, filed, left);
        CollectionService.sync(player); // stats (cards obtained) shown in the binder screens
        return true;
    }

    /** Personal Binder button: files the inventory cards into free pockets (the rest stays in the inventory). */
    private static PersonalBinderData depositInventory(ServerPlayerEntity player, PersonalBinderData d) {
        PlayerInventory inv = player.getInventory();
        List<String> credit = new ArrayList<>();
        int filed = 0, left = 0;
        for (int i = 0; i < inv.size(); i++) {
            ItemStack card = inv.getStack(i);
            if (!CardInfo.isCard(card)) continue;
            String id = CardInfo.id(card).toString();
            boolean graded = dev.tcgshop.grading.Grading.isGraded(card);
            int before = card.getCount();
            while (!card.isEmpty()) {
                int slot = pickSlot(d, card);
                if (slot < 0) break;
                d = placeOne(d, slot, card);
                card.decrement(1);
                filed++;
            }
            if (!graded && card.getCount() < before && !credit.contains(id)) credit.add(id);
            if (card.isEmpty()) inv.setStack(i, ItemStack.EMPTY);
            else left += card.getCount();
        }
        if (!credit.isEmpty()) CardAdvancements.credit(player, CollectionService.playerData(player), null, credit);
        if (filed == 0 && left == 0) player.sendMessage(Text.translatable("tcgshop.binder.nothing_to_deposit").formatted(Formatting.GRAY), true);
        else notifyFiled(player, d, filed, left);
        return d;
    }

    private static void notifyFiled(ServerPlayerEntity player, PersonalBinderData d, int filed, int left) {
        if (left > 0) {
            // truly full: say it clearly (chat, not just the action bar) instead of silently leaving cards in the inventory
            player.sendMessage(Text.translatable(d.fixedSize() ? "tcgshop.personal.full_store_fixed" : "tcgshop.personal.full_store",
                    left, d.cards(), d.limit()).formatted(Formatting.RED), false);
        } else if (filed > 0) {
            player.sendMessage(Text.translatable("tcgshop.personal.stored", filed, d.cards(), d.limit()).formatted(Formatting.GREEN), true);
        }
    }

    private static void send(ServerPlayerEntity player, net.minecraft.network.packet.CustomPayload payload) {
        if (ServerPlayNetworking.canSend(player, payload.getId())) ServerPlayNetworking.send(player, payload);
    }
}

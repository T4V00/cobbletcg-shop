package dev.tcgshop.onboarding;

import dev.masterball.api.card.CardInfo;
import dev.masterball.api.card.PrintVariant;
import dev.tcgshop.binder.PersonalBinderData;
import dev.tcgshop.binder.PersonalBinders;
import dev.tcgshop.collection.BinderData;
import dev.tcgshop.collection.BinderSlots;
import dev.tcgshop.collection.CollectionService;
import dev.tcgshop.grading.Grading;
import dev.tcgshop.item.TcgItems;
import dev.tcgshop.shop.CardSales;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * 1.0 onboarding: the (ungraded) cards a player owns, wherever they are: inventory, own General Binder and own
 * Personal Binder (the starter binder lives in the Accessories slot). Used by the one-time unique collector to pick the
 * card he likes and, when the player accepts, to take exactly one copy of it. Everything is re-read from the live
 * data on every call (nothing is cached), so the server always validates against what the player really has.
 */
public final class OwnedCards {
    private OwnedCards() {}

    /** A card as "card id" + print variant key (standard, reverse_holo, ...). */
    public record Want(String id, String variant) {
        public Want {
            variant = variant == null || variant.isEmpty() ? PrintVariant.STANDARD.key : variant;
        }

        public boolean standard() {
            return variant.equals(PrintVariant.STANDARD.key);
        }

        /** "id" or "id#variant" (same format as the wandering collector's wishes). */
        public String key() {
            return standard() ? id : id + "#" + variant;
        }

        @Nullable
        public static Want parse(String s) {
            if (s == null || s.isEmpty()) return null;
            int hash = s.indexOf('#');
            return hash < 0 ? new Want(s, null) : new Want(s.substring(0, hash), s.substring(hash + 1));
        }

        /** One copy of the card for display (EMPTY if the id no longer exists). */
        public ItemStack display() {
            ItemStack st = BinderSlots.stackFor(id, 1);
            PrintVariant v = standard() ? null : PrintVariant.byKey(variant);
            if (!st.isEmpty() && v != null) st = PrintVariant.apply(st, v);
            return st;
        }

        boolean matches(ItemStack st) {
            return CardInfo.isCard(st) && !Grading.isGraded(st) && CardInfo.id(st).toString().equals(id)
                    && PrintVariant.of(st).key.equals(variant)
                    && !dev.tcgshop.entity.CollectorTheft.isStolen(st); // 1.0 beta 3: a stolen card is never sold to a collector
        }
    }

    /** One distinct owned card with a sample stack and its market value (one unit). */
    public record Owned(Want want, ItemStack stack, long value) {}

    /** Distinct ungraded cards the player owns (inventory, General Binder, Personal Binder). */
    public static List<Owned> list(ServerPlayer p) {
        Map<String, Owned> out = new LinkedHashMap<>();
        for (int i = 0; i < 36; i++) {
            ItemStack st = p.getInventory().getItem(i);
            if (!CardInfo.isCard(st) || Grading.isGraded(st) || dev.tcgshop.entity.CollectorTheft.isStolen(st)) continue;
            add(out, new Want(CardInfo.id(st).toString(), PrintVariant.of(st).key), st.copyWithCount(1));
        }
        BinderData b = CollectionService.ownedBinder(p);
        if (b != null) for (String id : BinderSlots.ids(b.counts)) add(out, new Want(id, null), BinderSlots.stackFor(id, 1));
        ItemStack pb = PersonalBinders.owned(p, null);
        if (!pb.isEmpty()) {
            PersonalBinderData d = pb.getOrDefault(TcgItems.PERSONAL_BINDER_DATA, PersonalBinderData.DEFAULT);
            int lim = Math.min(d.limit(), d.slots().size());
            for (int i = 0; i < lim; i++) {
                String s = d.slots().get(i);
                if (s.isEmpty() || s.startsWith("wish:")) continue;
                ItemStack special = d.special(i);
                if (special != null) {
                    if (Grading.isGraded(special) || !CardInfo.isCard(special)) continue;
                    add(out, new Want(CardInfo.id(special).toString(), PrintVariant.of(special).key), special.copyWithCount(1));
                } else {
                    // id from the resolved stack (a legacy pocket id resolves to the current card), so take() can match it
                    ItemStack plain = BinderSlots.stackFor(s, 1);
                    if (CardInfo.isCard(plain)) add(out, new Want(CardInfo.id(plain).toString(), null), plain);
                }
            }
        }
        return new ArrayList<>(out.values());
    }

    private static void add(Map<String, Owned> out, Want w, ItemStack st) {
        if (st.isEmpty() || out.containsKey(w.key())) return;
        out.put(w.key(), new Owned(w, st, CardSales.unitValue(st)));
    }

    /** Does the player still own at least one copy of that card? */
    public static boolean has(ServerPlayer p, Want w) {
        for (Owned o : list(p)) if (o.want().equals(w)) return true;
        return false;
    }

    /**
     * Takes exactly one copy of that card (inventory first, then the General Binder, then the Personal Binder) and
     * returns it; EMPTY if the player has none. The caller must hand it back if what it does next fails.
     */
    public static ItemStack take(ServerPlayer p, Want w) {
        for (int i = 0; i < 36; i++) {
            ItemStack st = p.getInventory().getItem(i);
            if (w.matches(st)) return st.split(1);
        }
        if (w.standard()) {
            int slot = BinderSlots.findInBinder(p, BinderSlots.stackFor(w.id(), 1));
            if (slot >= 0) {
                ItemStack got = BinderSlots.take(p, slot, 1);
                if (!got.isEmpty()) {
                    BinderSlots.sync(p);
                    return got;
                }
            }
        }
        ItemStack pb = PersonalBinders.owned(p, null);
        if (pb.isEmpty()) return ItemStack.EMPTY;
        PersonalBinderData d = pb.getOrDefault(TcgItems.PERSONAL_BINDER_DATA, PersonalBinderData.DEFAULT);
        int lim = Math.min(d.limit(), d.slots().size());
        for (int i = 0; i < lim; i++) {
            String s = d.slots().get(i);
            if (s.isEmpty() || s.startsWith("wish:")) continue;
            ItemStack special = d.special(i);
            ItemStack card = special != null ? special.copyWithCount(1) : BinderSlots.stackFor(s, 1);
            if (card.isEmpty() || !w.matches(card)) continue;
            pb.set(TcgItems.PERSONAL_BINDER_DATA, d.withSlot(i, ""));
            CollectionService.sync(p);
            return card;
        }
        return ItemStack.EMPTY;
    }
}

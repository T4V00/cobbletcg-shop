package dev.tcgshop.client.binder;

import dev.masterball.api.card.CardInfo;
import dev.masterball.api.card.Rarity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Catalog of all cobbletcg cards grouped by set, in number order (client). */
public final class Catalog {
    /** Chronological order of the sets (from 1999 to 2026). */
    private static final String[] SET_ORDER = {
            "baseset", "trocket", "gchallange", "rskies", "hfates", "teamup", "ubounds", "uminds", "vvoltage", "bstyles",
            "creign", "eskies", "celebr25", "fstrike", "bstars", "aradiance", "lorigin", "silvertempest", "czenith",
            "sviolet", "paldeanevolved", "oflames", "mew151", "prift", "pfates", "tforces", "tmasquerade", "sfable",
            "scrown", "ssparks", "prismaticevolutions", "jtogether", "drivals", "bbolt", "wflare", "mevolution",
            "pflames", "ascended", "porder", "crising", "pblack"
    };

    public record Card(ResourceLocation id, String key, ItemStack stack, Rarity rarity, int number, ResourceLocation texture, String set) {
        public Component name() { return stack.getHoverName(); }
    }

    public record CardSet(String id, Component name, List<Card> cards, ResourceLocation packTexture, ItemStack packStack) {}

    private static List<CardSet> sets;
    private static Map<String, Card> byKey;

    private Catalog() {}

    /** Rebuilt every time a binder is opened (rarities depend on the server's tags). */
    public static void rebuild() {
        Map<String, List<Card>> grouped = new HashMap<>();
        Map<String, Card> keys = new HashMap<>();
        Map<String, ResourceLocation> sealedOf = new HashMap<>();
        for (ResourceLocation id : BuiltInRegistries.ITEM.keySet()) {
            if (!CardInfo.NS.equals(id.getNamespace())) continue;
            Item item = BuiltInRegistries.ITEM.get(id);
            if (CardInfo.isCard(id)) {
                ItemStack st = new ItemStack(item);
                Card c = new Card(id, id.toString(), st, Rarity.of(st), CardInfo.cardNumber(id), CardInfo.texture(id), CardInfo.setId(id));
                grouped.computeIfAbsent(c.set, k -> new ArrayList<>()).add(c);
                keys.put(c.key, c);
            } else if (id.getPath().contains("/sealed_")) {
                sealedOf.put(CardInfo.setId(id), id);
            }
        }
        Map<String, CardSet> ordered = new LinkedHashMap<>();
        List<String> order = new ArrayList<>(List.of(SET_ORDER));
        for (String s : grouped.keySet()) if (!order.contains(s)) order.add(s);
        for (String s : order) {
            List<Card> cs = grouped.get(s);
            if (cs == null) continue;
            cs.sort(Comparator.comparingInt(Card::number).thenComparing(Card::key));
            ResourceLocation sealed = sealedOf.get(s);
            Item sealedItem = sealed == null ? null : BuiltInRegistries.ITEM.get(sealed);
            ordered.put(s, new CardSet(s, Component.translatable("masterball.set." + s), cs,
                    sealed == null ? ResourceLocation.fromNamespaceAndPath("minecraft", "missingno") : CardInfo.packTexture(sealed),
                    sealedItem == null ? ItemStack.EMPTY : new ItemStack(sealedItem)));
        }
        sets = new ArrayList<>(ordered.values());
        byKey = keys;
    }

    public static List<CardSet> sets() {
        if (sets == null) rebuild();
        return sets;
    }

    public static Card card(String key) {
        if (byKey == null) rebuild();
        return byKey.get(key);
    }

    public static int totalCards() {
        int n = 0;
        for (CardSet s : sets()) n += s.cards.size();
        return n;
    }
}

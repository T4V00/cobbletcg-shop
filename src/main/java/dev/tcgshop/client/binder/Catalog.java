package dev.tcgshop.client.binder;

import dev.masterball.api.card.CardInfo;
import dev.masterball.api.card.Rarity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Catálogo de todas las cartas de cobbletcg agrupadas por set, en orden de número (cliente). */
public final class Catalog {
    /** Orden cronológico de los sets (de 1999 a 2026). */
    private static final String[] SET_ORDER = {
            "baseset", "trocket", "gchallange", "rskies", "hfates", "teamup", "ubounds", "uminds", "vvoltage", "bstyles",
            "creign", "eskies", "celebr25", "fstrike", "bstars", "aradiance", "lorigin", "silvertempest", "czenith",
            "sviolet", "paldeanevolved", "oflames", "mew151", "prift", "pfates", "tforces", "tmasquerade", "sfable",
            "scrown", "ssparks", "prismaticevolutions", "jtogether", "drivals", "bbolt", "wflare", "mevolution",
            "pflames", "ascended", "porder", "crising", "pblack"
    };

    public record Card(Identifier id, String key, ItemStack stack, Rarity rarity, int number, Identifier texture, String set) {
        public Text name() { return stack.getName(); }
    }

    public record CardSet(String id, Text name, List<Card> cards, Identifier packTexture, ItemStack packStack) {}

    private static List<CardSet> sets;
    private static Map<String, Card> byKey;

    private Catalog() {}

    /** Se reconstruye cada vez que se abre un binder (las rarezas dependen de los tags del servidor). */
    public static void rebuild() {
        Map<String, List<Card>> grouped = new HashMap<>();
        Map<String, Card> keys = new HashMap<>();
        Map<String, Identifier> sealedOf = new HashMap<>();
        for (Identifier id : Registries.ITEM.getIds()) {
            if (!CardInfo.NS.equals(id.getNamespace())) continue;
            Item item = Registries.ITEM.get(id);
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
            Identifier sealed = sealedOf.get(s);
            Item sealedItem = sealed == null ? null : Registries.ITEM.get(sealed);
            ordered.put(s, new CardSet(s, Text.translatable("masterball.set." + s), cs,
                    sealed == null ? Identifier.of("minecraft", "missingno") : CardInfo.packTexture(sealed),
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

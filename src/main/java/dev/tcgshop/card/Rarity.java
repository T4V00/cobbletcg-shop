package dev.tcgshop.card;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Rarezas de CobbleTCG. Se leen de los tags globales que trae el mod
 * (data/cobbletcg/tags/item/all*.json), que el servidor sincroniza al cliente.
 *
 * baseUsd = mediana real (TCGplayer, nov-2026) de todas las cartas de esa rareza en cobbletcg.
 * Último respaldo: sólo se usa si no hay precio real ni mediana del set en los datos.
 */
public enum Rarity {
    COMMON("common", "allcommons", 0.27, 0xFFB8C0CC, 0),
    DOUBLE_RARE("double_rare", "alldoublerares", 0.96, 0xFF5FB4FF, 1),
    ULTRA_RARE("ultra_rare", "allalternative", 6.13, 0xFFC77DFF, 2),
    ILLUSTRATION_RARE("illustration_rare", "allilustrationrare", 4.08, 0xFF4DE6B8, 2),
    SPECIAL_ILLUSTRATION_RARE("special_illustration_rare", "allspecialilustrationrare", 17.86, 0xFFFFB347, 3),
    HYPER_RARE("hyper_rare", "allhyperrare", 10.39, 0xFFFFE066, 3),
    UNKNOWN("unknown", null, 0.84, 0xFFFFFFFF, 0);

    public final String key;
    public final TagKey<Item> tag;
    public final double baseUsd;
    /** Color ARGB usado en brillos, bordes y texto. */
    public final int color;
    /** 0 = sin efectos, 1 = brillo, 2 = holo + partículas, 3 = celebración completa. */
    public final int tier;

    Rarity(String key, String tagPath, double baseUsd, int color, int tier) {
        this.key = key;
        this.tag = tagPath == null ? null : TagKey.of(RegistryKeys.ITEM, Identifier.of("cobbletcg", tagPath));
        this.baseUsd = baseUsd;
        this.color = color;
        this.tier = tier;
    }

    public Text displayName() {
        return Text.translatable("tcgshop.rarity." + key);
    }

    public static Rarity of(ItemStack stack) {
        if (!CardInfo.isCard(stack)) return UNKNOWN;
        // De más rara a menos rara: si una carta estuviera en dos tags, gana la más alta.
        for (Rarity r : new Rarity[]{SPECIAL_ILLUSTRATION_RARE, HYPER_RARE, ILLUSTRATION_RARE, ULTRA_RARE, DOUBLE_RARE, COMMON}) {
            if (stack.isIn(r.tag)) return r;
        }
        return UNKNOWN;
    }
}

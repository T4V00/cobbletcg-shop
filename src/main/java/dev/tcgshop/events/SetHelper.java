package dev.tcgshop.events;

import dev.masterball.api.card.CardCatalog;
import dev.masterball.api.card.CardInfo;
import dev.masterball.api.card.RarityData;
import dev.tcgshop.shop.TcgBlocks;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Set and year of a product (card, pack or box) via the MasterBall Traders API: for visitors looking for a specific
 * set (collector, nostalgic) and the "new set" events.
 */
public final class SetHelper {
    private static int newestYear = -1;

    private SetHelper() {}

    /** Set id ("mew151") or null if it is not a TCG product. */
    @Nullable
    public static String setOf(ItemStack st) {
        if (st == null || st.isEmpty()) return null;
        if (st.isOf(TcgBlocks.BOOSTER_BOX_ITEM)) return st.getOrDefault(TcgBlocks.BOX_SET, null);
        if (CardInfo.isCard(st) || CardInfo.isPack(st)) return CardInfo.setId(CardInfo.id(st));
        return null;
    }

    /** Year of the set (0 if unknown). */
    public static int year(@Nullable String set) {
        if (set == null) return 0;
        RarityData.SetData d = RarityData.set(set);
        return d == null ? 0 : d.year();
    }

    public static int newestYear() {
        if (newestYear < 0) {
            int y = 0;
            for (CardCatalog.SetInfo s : CardCatalog.sets()) y = Math.max(y, year(s.id()));
            newestYear = y;
        }
        return newestYear;
    }

    public static boolean isNewestSet(ItemStack st) {
        int y = year(setOf(st));
        return y > 0 && y >= newestYear();
    }

    /** "Old": 12 or more years behind the newest set (Base Set, Neo, EX...). */
    public static boolean isOldSet(ItemStack st) {
        int y = year(setOf(st)), n = newestYear();
        return y > 0 && n > 0 && y <= n - 12;
    }
}

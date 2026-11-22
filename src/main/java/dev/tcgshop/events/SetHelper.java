package dev.tcgshop.events;

import dev.masterball.api.card.CardCatalog;
import dev.masterball.api.card.CardInfo;
import dev.masterball.api.card.RarityData;
import dev.tcgshop.shop.TcgBlocks;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Set y año de un producto (carta, sobre o caja) con la MasterBall Traders API: para los visitantes que buscan un set
 * concreto (coleccionista, nostálgico) y los eventos de "set nuevo".
 */
public final class SetHelper {
    private static int newestYear = -1;

    private SetHelper() {}

    /** Id del set ("mew151") o null si no es un producto TCG. */
    @Nullable
    public static String setOf(ItemStack st) {
        if (st == null || st.isEmpty()) return null;
        if (st.isOf(TcgBlocks.BOOSTER_BOX_ITEM)) return st.getOrDefault(TcgBlocks.BOX_SET, null);
        if (CardInfo.isCard(st) || CardInfo.isPack(st)) return CardInfo.setId(CardInfo.id(st));
        return null;
    }

    /** Año del set (0 si no se sabe). */
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

    /** "Antiguo": 12 años o más por detrás del set más nuevo (Base Set, Neo, EX...). */
    public static boolean isOldSet(ItemStack st) {
        int y = year(setOf(st)), n = newestYear();
        return y > 0 && n > 0 && y <= n - 12;
    }
}

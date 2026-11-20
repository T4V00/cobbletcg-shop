package dev.tcgshop.card;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Utilidades para reconocer los ítems de CobbleTCG por su ID (sin depender de sus clases).
 *
 *  carta:          cobbletcg:<set>/cards/<id>        p.ej. cobbletcg:baseset/cards/bs004
 *  sobre sellado:  cobbletcg:<set>/sealed_<set>
 *  sobre abierto:  cobbletcg:<set>/riped2_<set>  (normal) · riped3 (semi-dios) · riped4 (dios)
 */
public final class CardInfo {
    public static final String NS = "cobbletcg";

    private CardInfo() {}

    public static Identifier id(ItemStack stack) {
        return Registries.ITEM.getId(stack.getItem());
    }

    public static boolean isCard(ItemStack stack) {
        return !stack.isEmpty() && isCard(id(stack));
    }

    public static boolean isCard(Identifier id) {
        return NS.equals(id.getNamespace()) && id.getPath().contains("/cards/");
    }

    /** "baseset" para cobbletcg:baseset/cards/bs004 */
    public static String setId(Identifier id) {
        String p = id.getPath();
        int i = p.indexOf('/');
        return i < 0 ? p : p.substring(0, i);
    }

    /** "bs004" para cobbletcg:baseset/cards/bs004 */
    public static String cardId(Identifier id) {
        String p = id.getPath();
        return p.substring(p.lastIndexOf('/') + 1);
    }

    /** Número de carta dentro del set (dígitos finales del id), o -1. */
    public static int cardNumber(Identifier id) {
        String c = cardId(id);
        int end = c.length(), start = end;
        while (start > 0 && Character.isDigit(c.charAt(start - 1))) start--;
        if (start == end) return -1;
        try { return Integer.parseInt(c.substring(start, end)); } catch (NumberFormatException e) { return -1; }
    }

    /** Misma ruta que usa cobbletcg.texture.CardTextureManager (texturas de los resource packs). */
    public static Identifier texture(Identifier id) {
        return Identifier.of(NS, "texturas/cards/" + setId(id) + "/" + cardId(id) + ".png");
    }

    /** Tipo de sobre abierto: 1 normal, 2 semi-dios, 3 dios; 0 si es un sobre sellado; -1 si no es sobre. */
    public static int packTier(ItemStack stack) {
        if (stack.isEmpty()) return -1;
        Identifier id = id(stack);
        if (!NS.equals(id.getNamespace())) return -1;
        String leaf = id.getPath().substring(id.getPath().lastIndexOf('/') + 1);
        if (leaf.startsWith("sealed_")) return 0;
        if (leaf.startsWith("riped2_")) return 1;
        if (leaf.startsWith("riped3_")) return 2;
        if (leaf.startsWith("riped4_")) return 3;
        return -1;
    }

    public static boolean isPack(ItemStack stack) {
        return packTier(stack) >= 0;
    }

    /**
     * Textura plana del sobre (la misma que usa cobbletcg.texture.PackTextureManager):
     * cobbletcg:mew151/sealed_mew151 -> texturas/sobres/mew151/sealed_mew151.png
     * cobbletcg:mew151/riped3_mew151 -> texturas/sobres/mew151/riped_mew151.png (versión rasgada)
     */
    public static Identifier packTexture(Identifier id) {
        String[] parts = id.getPath().split("/");
        String leaf = parts[parts.length - 1].replaceAll("riped\\d+", "riped");
        return Identifier.of(NS, "texturas/sobres/" + parts[0] + "/" + leaf + ".png");
    }

    /** Textura del sobre sellado del mismo set (para dibujar el sobre entero antes de cortarlo). */
    public static Identifier sealedTexture(Identifier id) {
        return packTexture(Identifier.of(id.getNamespace(), id.getPath().replaceAll("riped\\d+_", "sealed_")));
    }

    @Nullable
    public static Item item(Identifier id) {
        return Registries.ITEM.containsId(id) ? Registries.ITEM.get(id) : null;
    }
}

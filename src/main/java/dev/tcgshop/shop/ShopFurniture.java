package dev.tcgshop.shop;

/** Marca los bloques de categoría "tienda": sólo se pueden colocar dentro de una zona de tienda propia. */
public interface ShopFurniture {
    /** false = mueble "de casa": se puede colocar en cualquier sitio. */
    default boolean shopOnly() { return true; }
}

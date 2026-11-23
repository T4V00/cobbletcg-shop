package dev.tcgshop.shop;

/** Marks "shop" category blocks: they can only be placed inside your own shop zone. */
public interface ShopFurniture {
    /** false = "home" furniture: can be placed anywhere. */
    default boolean shopOnly() { return true; }
}

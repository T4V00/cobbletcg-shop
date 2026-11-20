package dev.tcgshop.shop;

/**
 * Niveles del bloque de tienda: lado de la zona en bloques, altura protegida por encima del bloque, precio en ₽,
 * clientes simultáneos máximos y color del material. Por debajo siempre se protegen ShopZones.BELOW bloques.
 */
public enum ShopTier {
    WOOD("wood", 16, 6, 15_000, 3, 0xFF9C6B3C),
    STONE("stone", 24, 8, 45_000, 4, 0xFF8A8A8A),
    IRON("iron", 32, 10, 110_000, 5, 0xFFD8D8D8),
    GOLD("gold", 48, 12, 250_000, 6, 0xFFF2C230),
    DIAMOND("diamond", 64, 16, 550_000, 8, 0xFF4EE2EC),
    EMERALD("emerald", 80, 20, 1_000_000, 10, 0xFF2ECC71),
    NETHERITE("netherite", 100, 24, 2_000_000, 12, 0xFF4A3F45);

    public final String key;
    public final int size;
    public final int up;
    public final long price;
    public final int maxCustomers;
    public final int color;

    ShopTier(String key, int size, int up, long price, int maxCustomers, int color) {
        this.key = key;
        this.size = size;
        this.up = up;
        this.price = price;
        this.maxCustomers = maxCustomers;
        this.color = color;
    }
}

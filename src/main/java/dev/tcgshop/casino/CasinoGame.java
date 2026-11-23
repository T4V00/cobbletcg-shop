package dev.tcgshop.casino;

/** The table / machine games. The key travels in the network payloads and names the screen. */
public enum CasinoGame {
    SLOTS("slots"),
    VIP_SLOTS("vip_slots"),
    ROULETTE("roulette"),
    BLACKJACK("blackjack"),
    WHEEL("wheel");

    public final String key;

    CasinoGame(String key) {
        this.key = key;
    }

    /** Largest stake per spin / round (roulette: all chips together; blackjack: per hand). */
    public long maxBet() {
        CasinoConfig c = CasinoConfig.INSTANCE;
        return switch (this) {
            case SLOTS -> c.slotMaxBet;
            case VIP_SLOTS -> c.vipSlotMaxBet;
            case ROULETTE -> c.rouletteMaxTotalBet;
            case BLACKJACK -> c.blackjackMaxBet;
            case WHEEL -> c.wheelMaxBet;
        };
    }

    public boolean slots() {
        return this == SLOTS || this == VIP_SLOTS;
    }

    public static CasinoGame byKey(String key) {
        for (CasinoGame g : values()) if (g.key.equals(key)) return g;
        return null;
    }
}

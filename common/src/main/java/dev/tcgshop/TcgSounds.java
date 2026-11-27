package dev.tcgshop;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

/**
 * Custom sounds. In assets/tcgshop/sounds.json they currently point to vanilla events;
 * they can be replaced with custom .ogg files without touching the code.
 */
public final class TcgSounds {
    public static final SoundEvent PACK_SHAKE = register("pack.shake");
    public static final SoundEvent PACK_TEAR = register("pack.tear");
    public static final SoundEvent PACK_OPEN = register("pack.open");
    public static final SoundEvent CARD_SLIDE = register("card.slide");
    public static final SoundEvent CARD_FLIP = register("card.flip");
    public static final SoundEvent CARD_RARE = register("card.rare");
    public static final SoundEvent CARD_EPIC = register("card.epic");
    public static final SoundEvent CARD_LEGENDARY = register("card.legendary");
    public static final SoundEvent CARD_ANTICIPATE = register("card.anticipate");
    public static final SoundEvent UI_COINS = register("ui.coins");
    // 1.1 (Group E): shop, phone and other mod actions (sounds.json maps them to tuned vanilla events, no external audio)
    public static final SoundEvent SALE = register("shop.sale");
    public static final SoundEvent SALE_BIG = register("shop.sale_big");
    public static final SoundEvent PRICE_TAG = register("shop.price_tag");
    public static final SoundEvent PHONE_OPEN = register("phone.open");
    public static final SoundEvent PHONE_CLOSE = register("phone.close");
    public static final SoundEvent PHONE_APP = register("phone.app");
    public static final SoundEvent PHONE_TAP = register("phone.tap");
    public static final SoundEvent PHONE_NOTIFY = register("phone.notify");
    public static final SoundEvent PHONE_BUY = register("phone.buy");
    public static final SoundEvent COLLECTION_COMPLETE = register("collection.complete");
    public static final SoundEvent RANKING_UP = register("phone.ranking_up");
    public static final SoundEvent BOX_OPEN_ALL = register("box.open_all");
    public static final SoundEvent CASINO_CHIP = register("casino.chip");
    public static final SoundEvent CASINO_WIN = register("casino.win");

    private TcgSounds() {}

    private static SoundEvent register(String name) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(TcgShop.MOD_ID, name);
        return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
    }

    public static void init() {}
}

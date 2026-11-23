package dev.tcgshop;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

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

    private TcgSounds() {}

    private static SoundEvent register(String name) {
        Identifier id = Identifier.of(TcgShop.MOD_ID, name);
        return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
    }

    public static void init() {}
}

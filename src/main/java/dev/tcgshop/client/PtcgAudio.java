package dev.tcgshop.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * TCG table audio: uses at runtime the battle music and Pokémon cries that ship with
 * Cobblemon (no audio is copied). If Cobblemon is not present, vanilla sounds are used and there is no music.
 */
public final class PtcgAudio {
    private static final Map<String, Identifier> CRY_CACHE = new HashMap<>();
    private static final Pattern POSSESSIVE = Pattern.compile("^.*?'s\\s+");
    private static final String[] PREFIXES = {"Radiant ", "Alolan ", "Galarian ", "Hisuian ", "Paldean ", "Dark ", "Light ", "Shining ", "Mega ",
            "Shadow ", "Rapid Strike ", "Single Strike ", "Dusk Mane ", "Dawn Wings ", "Ultra ", "Black ", "White "};
    private static final Pattern SUFFIX = Pattern.compile("\\s+(ex|EX|GX|V|VMAX|VSTAR|V-UNION|BREAK|LEGEND|Prime|LV\\.X|Lv\\.X|δ|☆|◇|Star|Prism Star|Origin Forme.*|Forme.*)$");
    private static Music music;

    private PtcgAudio() {}

    private static boolean has(Identifier id) {
        return MinecraftClient.getInstance().getSoundManager().get(id) != null;
    }

    public static Identifier cobblemon(String path) { return Identifier.of("cobblemon", path); }

    /** Plays a Cobblemon sound (or the vanilla fallback if it doesn't exist). */
    public static void play(String cobblemonPath, @Nullable SoundEvent fallback, float pitch, float volume) {
        Identifier id = cobblemon(cobblemonPath);
        if (has(id)) playId(id, pitch, volume);
        else if (fallback != null) playId(fallback.getId(), pitch, volume);
    }

    public static void playId(Identifier id, float pitch, float volume) {
        MinecraftClient.getInstance().getSoundManager().play(new PositionedSoundInstance(id, SoundCategory.MASTER, volume, pitch,
                SoundInstance.createRandom(), false, 0, SoundInstance.AttenuationType.NONE, 0, 0, 0, true));
    }

    /** Cobblemon species from the card name ("Iono's Bellibolt ex" -> bellibolt, "Mr. Mime" -> mrmime). */
    public static String species(String cardName) {
        String n = cardName.trim();
        int amp = n.indexOf(" & ");
        if (amp > 0) n = n.substring(0, amp);
        n = POSSESSIVE.matcher(n).replaceFirst("");
        for (String p : PREFIXES) if (n.startsWith(p)) n = n.substring(p.length());
        for (int k = 0; k < 3; k++) n = SUFFIX.matcher(n).replaceFirst("");
        n = n.replace("♀", "f").replace("♂", "m");
        n = Normalizer.normalize(n, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    /** Cry of the card's Pokémon (if Cobblemon has it). */
    public static void cry(String cardName, float pitch, float volume) {
        Identifier id = CRY_CACHE.computeIfAbsent(cardName, n -> {
            Identifier c = cobblemon("pokemon." + species(n) + ".cry");
            return has(c) ? c : null;
        });
        if (id != null) playId(id, pitch, volume);
    }

    // ------------------------------------------------------------------ music

    /** Starts (or keeps) a looping Cobblemon battle track, with fade. */
    public static void startMusic(String track) {
        Identifier id = cobblemon(track);
        if (!has(id)) return;
        if (music != null && !music.isDone() && music.getId().equals(id) && !music.fading) return;
        if (music != null) music.fadeOut();
        music = new Music(id);
        MinecraftClient.getInstance().getSoundManager().play(music);
    }

    public static void stopMusic() {
        if (music != null) music.fadeOut();
        music = null;
    }

    private static final class Music extends MovingSoundInstance {
        private float target = 0.75f;
        boolean fading;
        private int age;

        Music(Identifier id) {
            super(SoundEvent.of(id), SoundCategory.RECORDS, SoundInstance.createRandom());
            this.repeat = true;
            this.repeatDelay = 0;
            this.volume = 0.01f;
            this.relative = true;
            this.attenuationType = AttenuationType.NONE;
            this.x = 0;
            this.y = 0;
            this.z = 0;
        }

        void fadeOut() { fading = true; }

        @Override
        public boolean shouldAlwaysPlay() { return true; }

        @Override
        public void tick() {
            age++;
            if (fading) {
                volume -= 0.03f;
                if (volume <= 0.01f) { volume = 0; setDone(); }
                return;
            }
            if (volume < target) volume = Math.min(target, volume + 0.02f);
            // the game's normal music does not mix with the battle music
            if (age % 20 == 0) MinecraftClient.getInstance().getSoundManager().stopSounds(null, SoundCategory.MUSIC);
        }
    }
}

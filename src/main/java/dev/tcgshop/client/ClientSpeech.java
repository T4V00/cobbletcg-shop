package dev.tcgshop.client;

import dev.tcgshop.entity.ScalperEntity;
import dev.tcgshop.entity.WholesalerEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Bocadillos de diálogo de los NPC: el texto aparece letra a letra sobre su cabeza mientras "balbucean" con
 * pitidos de bloque musical (cada NPC con su voz: tono e instrumento según quién sea).
 */
public final class ClientSpeech {
    /** Letras por tick que se van mostrando. */
    public static final float CHARS_PER_TICK = 1.6f;
    public static final int MAX_WIDTH = 150;

    public static final class Speech {
        public final List<String> lines;
        public final String plain;
        public final long start;
        public final int duration;
        int cursor, letters;

        Speech(String plain, List<String> lines, long start, int duration) {
            this.plain = plain;
            this.lines = lines;
            this.start = start;
            this.duration = duration;
        }

        public int visibleChars(float now) { return (int) Math.max(0, (now - start) * CHARS_PER_TICK); }

        public float alpha(float now) {
            float left = start + duration - now;
            return Math.max(0, Math.min(1, left / 10f)) * Math.min(1, (now - start + 1) / 3f);
        }
    }

    private static final Map<Integer, Speech> SPEECH = new HashMap<>();

    private ClientSpeech() {}

    public static Speech get(int entityId) { return SPEECH.get(entityId); }

    public static void say(int entityId, Text text) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;
        String plain = text.getString();
        List<String> lines = wrap(mc.textRenderer, plain, MAX_WIDTH);
        int duration = Math.min(240, 50 + Math.round(plain.length() / CHARS_PER_TICK) + plain.length());
        SPEECH.put(entityId, new Speech(plain, lines, mc.world.getTime(), duration));
    }

    /** Partido por palabras (y por letras si una palabra no cabe). */
    public static List<String> wrap(TextRenderer tr, String s, int max) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : s.split(" ")) {
            String cand = line.isEmpty() ? word : line + " " + word;
            if (tr.getWidth(cand) <= max) { line = new StringBuilder(cand); continue; }
            if (!line.isEmpty()) out.add(line.toString());
            line = new StringBuilder(word);
            while (tr.getWidth(line.toString()) > max && line.length() > 1) {
                int cut = line.length() - 1;
                while (cut > 1 && tr.getWidth(line.substring(0, cut)) > max) cut--;
                out.add(line.substring(0, cut));
                line = new StringBuilder(line.substring(cut));
            }
        }
        if (!line.isEmpty()) out.add(line.toString());
        return out;
    }

    /** Sonidos de "hablar" a medida que salen las letras, y limpieza de los bocadillos terminados. */
    public static void tick(MinecraftClient mc) {
        if (mc.world == null) { SPEECH.clear(); return; }
        long now = mc.world.getTime();
        Iterator<Map.Entry<Integer, Speech>> it = SPEECH.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Speech> e = it.next();
            Speech sp = e.getValue();
            Entity who = mc.world.getEntityById(e.getKey());
            if (who == null || !who.isAlive() && now - sp.start > 20 || now > sp.start + sp.duration) { it.remove(); continue; }
            int visible = Math.min(sp.plain.length(), sp.visibleChars(now));
            boolean played = false;
            while (sp.cursor < visible) {
                char c = sp.plain.charAt(sp.cursor++);
                if (!Character.isLetterOrDigit(c)) continue;
                if (sp.letters++ % 45 != 0 || played) continue;
                blip(mc, who, c);
                played = true;
            }
        }
    }

    private static void blip(MinecraftClient mc, Entity who, char c) {
        int h = who.getUuid().hashCode();
        SoundEvent sound;
        float base;
        if (who instanceof ScalperEntity) { sound = SoundEvents.ENTITY_PILLAGER_AMBIENT; base = 0.95f + Math.floorMod(h, 20) / 100f; }
        else if (who instanceof WholesalerEntity) { sound = SoundEvents.ENTITY_WANDERING_TRADER_AMBIENT; base = 0.95f + Math.floorMod(h, 15) / 100f; }
        else {
            sound = Math.floorMod(h, 4) == 0 ? SoundEvents.ENTITY_VILLAGER_TRADE : SoundEvents.ENTITY_VILLAGER_AMBIENT;
            base = 0.9f + Math.floorMod(h, 35) / 100f; // cada cliente con su "voz"
        }
        float pitch = base + (who.getRandom().nextFloat() - 0.5f) * 0.1f;
        mc.world.playSound(who.getX(), who.getEyeY(), who.getZ(), sound, SoundCategory.NEUTRAL, 0.55f, pitch, false);
    }
}

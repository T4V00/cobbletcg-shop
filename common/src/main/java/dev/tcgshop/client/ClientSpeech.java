package dev.tcgshop.client;

import dev.masterball.api.platform.event.ClientEvents;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.tcgshop.entity.ScalperEntity;
import dev.tcgshop.entity.TcgNpc;
import dev.tcgshop.entity.WholesalerEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * NPC speech bubbles: the text appears letter by letter above their head while they "babble" with
 * note block beeps (each NPC with its own voice: pitch and instrument depending on who it is).
 * <p>
 * The mod's own NPCs (TcgNpc) draw their bubble in their renderer (TcgNpcRenderer). For any OTHER entity
 * (e.g. Team Rocket's Meowth, which is a Cobblemon Pokémon) the bubble is drawn here, in the world
 * render event, above the entity with that id.
 */
public final class ClientSpeech {
    /** Letters shown per tick. */
    public static final float CHARS_PER_TICK = 1.6f;
    public static final int MAX_WIDTH = 150;

    public static final class Speech {
        public final List<String> lines;
        public final String plain;
        public final long start;
        public final int duration;
        /** Scripted bubble (tutorial): visible even if the player has turned off NPC bubbles. */
        public final boolean forced;
        int cursor, letters;

        Speech(String plain, List<String> lines, long start, int duration, boolean forced) {
            this.plain = plain;
            this.lines = lines;
            this.start = start;
            this.duration = duration;
            this.forced = forced;
        }

        public int visibleChars(float now) { return (int) Math.max(0, (now - start) * CHARS_PER_TICK); }

        public float alpha(float now) {
            float left = start + duration - now;
            return Math.max(0, Math.min(1, left / 10f)) * Math.min(1, (now - start + 1) / 3f);
        }
    }

    private static final Map<Integer, Speech> SPEECH = new HashMap<>();
    private static boolean worldHooked;

    static {
        // the class is initialized on the first client tick (ClientTickEvents -> tick): the render is hooked there
        hookWorldRender();
    }

    private ClientSpeech() {}

    /** Hooks the bubble render above entities that are not TcgNpc (idempotent). */
    public static void hookWorldRender() {
        if (worldHooked) return;
        worldHooked = true;
        ClientEvents.WORLD_AFTER_ENTITIES.register(ClientSpeech::renderWorld);
    }

    public static Speech get(int entityId) { return SPEECH.get(entityId); }

    public static void say(int entityId, Component text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        String plain = text.getString();
        List<String> lines = wrap(mc.font, plain, MAX_WIDTH);
        int duration = Math.min(240, 50 + Math.round(plain.length() / CHARS_PER_TICK) + plain.length());
        SPEECH.put(entityId, new Speech(plain, lines, mc.level.getGameTime(), duration, false));
    }

    /**
     * Scripted bubble (Meowth tutorial): lasts at least {@code minTicks} and always long enough to be typed out
     * fully and read; visible even if NPC bubbles are disabled in the settings.
     */
    public static void sayScripted(int entityId, Component text, int minTicks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        hookWorldRender();
        String plain = text.getString();
        List<String> lines = wrap(mc.font, plain, MAX_WIDTH);
        int typing = Math.round(plain.length() / CHARS_PER_TICK);
        int duration = Math.max(Math.max(20, minTicks), typing + 40);
        SPEECH.put(entityId, new Speech(plain, lines, mc.level.getGameTime(), duration, true));
    }

    /** Split by words (and by letters if a word does not fit). */
    public static List<String> wrap(Font tr, String s, int max) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : s.split(" ")) {
            String cand = line.isEmpty() ? word : line + " " + word;
            if (tr.width(cand) <= max) { line = new StringBuilder(cand); continue; }
            if (!line.isEmpty()) out.add(line.toString());
            line = new StringBuilder(word);
            while (tr.width(line.toString()) > max && line.length() > 1) {
                int cut = line.length() - 1;
                while (cut > 1 && tr.width(line.substring(0, cut)) > max) cut--;
                out.add(line.substring(0, cut));
                line = new StringBuilder(line.substring(cut));
            }
        }
        if (!line.isEmpty()) out.add(line.toString());
        return out;
    }

    /** "Talking" sounds as the letters appear, and cleanup of finished bubbles. */
    public static void tick(Minecraft mc) {
        if (mc.level == null) { SPEECH.clear(); return; }
        long now = mc.level.getGameTime();
        Iterator<Map.Entry<Integer, Speech>> it = SPEECH.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Speech> e = it.next();
            Speech sp = e.getValue();
            Entity who = mc.level.getEntity(e.getKey());
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

    private static void blip(Minecraft mc, Entity who, char c) {
        int h = who.getUUID().hashCode();
        SoundEvent sound;
        float base, volume = 0.55f;
        if (who instanceof ScalperEntity) { sound = SoundEvents.PILLAGER_AMBIENT; base = 0.95f + Math.floorMod(h, 20) / 100f; }
        else if (who instanceof WholesalerEntity) { sound = SoundEvents.WANDERING_TRADER_AMBIENT; base = 0.95f + Math.floorMod(h, 15) / 100f; }
        else if (who instanceof dev.tcgshop.entity.FurnitureSellerEntity) { sound = SoundEvents.WANDERING_TRADER_AMBIENT; base = 1.08f + Math.floorMod(h, 15) / 100f; }
        else if (!(who instanceof TcgNpc)) { sound = SoundEvents.CAT_AMBIENT; base = 1.15f; volume = 0.35f; } // the tutorial Meowth
        else {
            sound = Math.floorMod(h, 4) == 0 ? SoundEvents.VILLAGER_TRADE : SoundEvents.VILLAGER_AMBIENT;
            base = 0.9f + Math.floorMod(h, 35) / 100f; // each customer with its own "voice"
        }
        float pitch = base + (who.getRandom().nextFloat() - 0.5f) * 0.1f;
        mc.level.playLocalSound(who.getX(), who.getEyeY(), who.getZ(), sound, SoundSource.NEUTRAL, volume, pitch, false);
    }

    // ------------------------------------------------------------------ bubbles above entities that are not TcgNpc

    private static void renderWorld(ClientEvents.WorldRender ctx) {
        if (SPEECH.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || ctx.camera() == null) return;
        PoseStack matrices = ctx.poseStack();
        MultiBufferSource consumers = ctx.consumers();
        if (consumers == null) consumers = mc.renderBuffers().bufferSource();
        if (matrices == null) return;
        float tickDelta = ctx.tickCounter().getGameTimeDeltaPartialTick(true);
        Vec3 cam = ctx.camera().getPosition();
        Quaternionf rot = ctx.camera().rotation();
        boolean drew = false;
        for (Map.Entry<Integer, Speech> e : SPEECH.entrySet()) {
            Entity who = mc.level.getEntity(e.getKey());
            if (who == null || who instanceof TcgNpc || who.isInvisible()) continue; // TcgNpcs draw it in their renderer
            Speech sp = e.getValue();
            if (!sp.forced && !ClientSettings.INSTANCE.bubbles) continue;
            double x = Mth.lerp(tickDelta, who.xo, who.getX());
            double y = Mth.lerp(tickDelta, who.yo, who.getY());
            double z = Mth.lerp(tickDelta, who.zo, who.getZ());
            if (cam.distanceToSqr(x, y, z) > 40 * 40) continue;
            matrices.pushPose();
            matrices.translate(x - cam.x, y - cam.y, z - cam.z);
            drew |= bubble(mc.font, who, sp, tickDelta, matrices, consumers, rot);
            matrices.popPose();
        }
        if (drew && consumers instanceof MultiBufferSource.BufferSource imm) imm.endBatch();
    }

    /** Same comic bubble as TcgNpcRenderer: background, tail pointing at the entity and letter-by-letter text. */
    private static boolean bubble(Font tr, Entity who, Speech sp, float tickDelta, PoseStack matrices,
                                  MultiBufferSource consumers, Quaternionf cameraRotation) {
        var cfg = ClientSettings.INSTANCE;
        float now = who.level().getGameTime() + tickDelta;
        float alpha = sp.alpha(now);
        if (alpha <= 0.02f) return false;
        int w = 0;
        for (String l : sp.lines) w = Math.max(w, tr.width(l));
        int lineH = 10, padX = 5, padY = 4;
        int bw = w + padX * 2, bh = sp.lines.size() * lineH + padY * 2 - 1;
        float pop = Math.min(1, (now - sp.start) / 4f);
        float scale = 0.021f * cfg.size() * (0.7f + 0.3f * (float) Math.sin(pop * Math.PI / 2));
        matrices.pushPose();
        // above the name (nickname) drawn by the entity's renderer
        matrices.translate(0, who.getBbHeight() + 0.85f + (cfg.size() - 1f) * 0.25f, 0);
        matrices.mulPose(cameraRotation);
        matrices.scale(scale, -scale, scale);
        float x0 = -bw / 2f, y0 = -6 - bh;
        int bg = cfg.bgColor();
        if (bg == 0 && sp.forced) bg = ClientSettings.BG_COLORS[0]; // scripted: always with a background so it reads well
        int bgCol = bg == 0 ? 0 : (Math.round(((bg >>> 24) / 255f) * alpha * 255) << 24) | (bg & 0xFFFFFF);
        int tc = cfg.textColor();
        if (sp.forced && cfg.bgColor() == 0) tc = 0xFF16161E;
        int textCol = (Math.max(8, Math.round(alpha * 255)) << 24) | (tc & 0xFFFFFF);
        int visible = sp.visibleChars(now), used = 0;
        int target = w + padX * 2;
        boolean typing = visible < sp.plain.length();
        // 1) the background alone (lines of spaces with a background color): Minecraft draws it slightly in front of the text
        if (bgCol != 0) {
            Matrix4f mb = matrices.last().pose();
            for (int i = 0; i < sp.lines.size(); i++) {
                StringBuilder pad = new StringBuilder(" ");
                while (tr.width(pad.toString()) < target - 2) pad.append(' ');
                tr.drawInBatch(pad.toString(), x0, y0 + padY + i * lineH, 0x01000000, false, mb, consumers, Font.DisplayMode.NORMAL, bgCol, 0xF000F0);
            }
            String tail = "▼";
            tr.drawInBatch(tail, -tr.width(tail) / 2f, y0 + padY + sp.lines.size() * lineH - 2, (Math.max(8, Math.round(alpha * 255)) << 24) | (bg & 0xFFFFFF),
                    false, mb, consumers, Font.DisplayMode.NORMAL, 0, 0xF000F0);
        }
        // 2) the text, moved toward the camera and with depth offset
        matrices.pushPose();
        matrices.translate(0, 0, 1.5f);
        Matrix4f mt = matrices.last().pose();
        boolean shadow = bgCol == 0;
        for (int i = 0; i < sp.lines.size(); i++) {
            String l = sp.lines.get(i);
            int show = Math.max(0, Math.min(l.length(), visible - used));
            used += l.length() + 1;
            if (show <= 0) continue;
            String txt = " " + l.substring(0, show) + (typing && show < l.length() ? "_" : "");
            tr.drawInBatch(txt, x0, y0 + padY + i * lineH, textCol, shadow, mt, consumers, Font.DisplayMode.POLYGON_OFFSET, 0, 0xF000F0);
        }
        matrices.popPose();
        matrices.popPose();
        return true;
    }
}

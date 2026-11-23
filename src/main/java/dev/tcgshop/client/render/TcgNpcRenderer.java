package dev.tcgshop.client.render;

import dev.tcgshop.TcgShop;
import dev.tcgshop.collection.Titles;
import dev.tcgshop.entity.NpcIdentity;
import dev.tcgshop.entity.TcgNpc;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.BipedEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.ArmorEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;

/**
 * Renderer for the mod's NPCs (customers, employees, Scalper, Wholesaler...): generated skin (NpcSkins) on the
 * player model with the outer layer (hat, jacket, sleeves, pants) visible, slim model for female skins, a 3D
 * backpack for some of them, name + a line above with their role and Pokémon title, and the emotion speech bubble.
 */
public class TcgNpcRenderer<T extends MobEntity & TcgNpc> extends BipedEntityRenderer<T, PlayerEntityModel<T>> {
    private static final Identifier[] BUBBLES = {
            null,
            Identifier.of(TcgShop.MOD_ID, "textures/entity/scalper_think_money.png"),
            Identifier.of(TcgShop.MOD_ID, "textures/entity/scalper_think_love.png"),
            Identifier.of(TcgShop.MOD_ID, "textures/entity/npc_think_deal.png"),
            Identifier.of(TcgShop.MOD_ID, "textures/entity/npc_think_angry.png"),
            Identifier.of(TcgShop.MOD_ID, "textures/entity/npc_think_question.png")};

    private final PlayerEntityModel<T> wide, slim;

    public TcgNpcRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new PlayerEntityModel<>(ctx.getPart(EntityModelLayers.PLAYER), false), 0.5f);
        this.wide = this.model;
        this.slim = new PlayerEntityModel<>(ctx.getPart(EntityModelLayers.PLAYER_SLIM), true);
        // armor worn by NPCs (e.g. top thief tiers), drawn like on players. Subclasses (CustomerNpcRenderer) may add
        // their own armor layer: this one then stays quiet so the armor is never drawn twice.
        addFeature(new SharedArmorFeature(
                new ArmorEntityModel<T>(ctx.getPart(EntityModelLayers.PLAYER_INNER_ARMOR)),
                new ArmorEntityModel<T>(ctx.getPart(EntityModelLayers.PLAYER_OUTER_ARMOR)),
                ctx));
        addFeature(new BackpackFeature<>(this));
    }

    /** Vanilla armor layer that skips rendering when the renderer has another armor layer (added by a subclass). */
    private final class SharedArmorFeature extends ArmorFeatureRenderer<T, PlayerEntityModel<T>, ArmorEntityModel<T>> {
        private Boolean duplicate;

        SharedArmorFeature(ArmorEntityModel<T> inner, ArmorEntityModel<T> outer, EntityRendererFactory.Context ctx) {
            super(TcgNpcRenderer.this, inner, outer, ctx.getModelManager());
        }

        @Override
        public void render(MatrixStack matrices, VertexConsumerProvider consumers, int light, T entity, float limbAngle,
                           float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch) {
            if (duplicate == null) {
                boolean dup = false;
                for (FeatureRenderer<T, PlayerEntityModel<T>> f : TcgNpcRenderer.this.features)
                    if (f != this && f instanceof ArmorFeatureRenderer) dup = true;
                duplicate = dup;
            }
            if (duplicate) return;
            super.render(matrices, consumers, light, entity, limbAngle, limbDistance, tickDelta, animationProgress, headYaw, headPitch);
        }
    }

    /** Skin look of an NPC: role, skin int and its name (the name decides the sex of skins that don't store it). */
    static NpcSkins.Look lookOf(MobEntity entity, TcgNpc npc) {
        Text name = entity.getCustomName();
        return NpcSkins.look(npc.roleKey(), npc.npcSkin(), name == null ? null : name.getString());
    }

    @Override
    public Identifier getTexture(T entity) {
        return lookOf(entity, entity).texture();
    }

    @Override
    public void render(T entity, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
        this.model = lookOf(entity, entity).slim() ? slim : wide;
        // second skin layer: hair volume, caps, jackets, skirts... (the parts are visible by default, but make sure)
        this.model.setVisible(true);
        super.render(entity, yaw, tickDelta, matrices, consumers, light);
        dev.tcgshop.client.ClientSpeech.Speech speech = dev.tcgshop.client.ClientSpeech.get(entity.getId());
        if (speech != null && dispatcher.getSquaredDistanceToCamera(entity) < 40 * 40) {
            speechBubble(entity, speech, tickDelta, matrices, consumers);
            return;
        }
        if (entity.wantsAttention()) {
            // gold "!" that bounces and spins, visible through walls: wants a deal with you
            float t = entity.age + tickDelta;
            matrices.push();
            matrices.translate(0, entity.getHeight() + 1.25f + Math.abs((float) Math.sin(t * 0.12f)) * 0.18f, 0);
            matrices.multiply(dispatcher.getRotation());
            float s = 0.055f * (1 + 0.08f * (float) Math.sin(t * 0.3f));
            matrices.scale(s, -s, s);
            TextRenderer tr = getTextRenderer();
            Text mark = Text.literal("!").formatted(Formatting.BOLD);
            float x = -tr.getWidth(mark) / 2f;
            Matrix4f mm = matrices.peek().getPositionMatrix();
            tr.draw(mark, x, -4, 0x80FFD34D, false, mm, consumers, TextRenderer.TextLayerType.SEE_THROUGH, 0, 0xF000F0);
            tr.draw(mark, x + 0.6f, -3.4f, 0xFF7A4A00, false, mm, consumers, TextRenderer.TextLayerType.NORMAL, 0, 0xF000F0);
            tr.draw(mark, x, -4, 0xFFFFD34D, false, mm, consumers, TextRenderer.TextLayerType.POLYGON_OFFSET, 0, 0xF000F0);
            int wait = entity.waitPermille();
            if (wait >= 0) {
                // patience bar: green -> yellow -> red; leaves when it reaches 0
                float f = wait / 1000f;
                int col = f > 0.5f ? 0xFF5BD46B : f > 0.25f ? 0xFFFFC233 : 0xFFFF5050;
                if (f <= 0.25f && ((int) (t / 4)) % 2 == 0) col = 0xFFFFFFFF;
                wheel(tr, matrices, consumers, 0, -0.5f, 9.5f, 1.8f, 3.4f, f, col); // wheel around the "!"
            }
            matrices.pop();
            return;
        }
        int waitQ = entity.waitPermille();
        if (waitQ >= 0) {
            // in the register queue: their patience bar above the head
            float tq = entity.age + tickDelta, f = waitQ / 1000f;
            int col = f > 0.5f ? 0xFF5BD46B : f > 0.25f ? 0xFFFFC233 : 0xFFFF5050;
            if (f <= 0.25f && ((int) (tq / 4)) % 2 == 0) col = 0xFFFFFFFF;
            matrices.push();
            matrices.translate(0, entity.getHeight() + 0.62f, 0);
            matrices.multiply(dispatcher.getRotation());
            matrices.scale(0.025f, -0.025f, 0.025f);
            wheel(getTextRenderer(), matrices, consumers, 0, -8f, 8f, 2.2f, 4.2f, f, col);
            matrices.pop();
        }
        int emote = entity.emote();
        if (emote <= 0 || emote >= BUBBLES.length) return;
        matrices.push();
        float bob = (float) Math.sin((entity.age + tickDelta) * 0.15f) * 0.05f;
        matrices.translate(0.4f, entity.getHeight() + 0.95f + bob, 0);
        matrices.multiply(dispatcher.getRotation());
        matrices.scale(-0.55f, -0.55f, 0.55f);
        VertexConsumer vc = consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(BUBBLES[emote]));
        Matrix4f m = matrices.peek().getPositionMatrix();
        vertex(vc, matrices, m, -0.5f, -0.5f, 0f, 0f);
        vertex(vc, matrices, m, -0.5f, 0.5f, 0f, 1f);
        vertex(vc, matrices, m, 0.5f, 0.5f, 1f, 1f);
        vertex(vc, matrices, m, 0.5f, -0.5f, 1f, 0f);
        matrices.pop();
    }

    /**
     * Comic speech bubble above the head: white background with border and a tail toward the NPC, text that appears letter by
     * letter (the "talking" beeps come from ClientSpeech) and fades out when done.
     */
    private void speechBubble(T entity, dev.tcgshop.client.ClientSpeech.Speech sp, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers) {
        var cfg = dev.tcgshop.client.ClientSettings.INSTANCE;
        if (!cfg.bubbles) return;
        float now = entity.getWorld().getTime() + tickDelta;
        float alpha = sp.alpha(now);
        if (alpha <= 0.02f) return;
        TextRenderer tr = getTextRenderer();
        int w = 0;
        for (String l : sp.lines) w = Math.max(w, tr.getWidth(l));
        int lineH = 10, padX = 5, padY = 4;
        int bw = w + padX * 2, bh = sp.lines.size() * lineH + padY * 2 - 1;
        float pop = Math.min(1, (now - sp.start) / 4f);
        float scale = 0.021f * cfg.size() * (0.7f + 0.3f * (float) Math.sin(pop * Math.PI / 2));
        matrices.push();
        matrices.translate(0, entity.getHeight() + 1.08f + (cfg.size() - 1f) * 0.25f, 0);
        matrices.multiply(dispatcher.getRotation());
        matrices.scale(scale, -scale, scale);
        float x0 = -bw / 2f, y0 = -6 - bh;
        int bg = cfg.bgColor();
        int bgCol = bg == 0 ? 0 : (Math.round(((bg >>> 24) / 255f) * alpha * 255) << 24) | (bg & 0xFFFFFF);
        int tc = cfg.textColor();
        int textCol = (Math.max(8, Math.round(alpha * 255)) << 24) | (tc & 0xFFFFFF);
        int visible = sp.visibleChars(now), used = 0;
        int target = w + padX * 2;
        boolean typing = visible < sp.plain.length();
        // Minecraft draws a text line's background slightly IN FRONT of the text. That is why there are two passes:
        // 1) the background alone (lines of spaces with a background color)
        if (bgCol != 0) {
            Matrix4f mb = matrices.peek().getPositionMatrix();
            for (int i = 0; i < sp.lines.size(); i++) {
                StringBuilder pad = new StringBuilder(" ");
                while (tr.getWidth(pad.toString()) < target - 2) pad.append(' ');
                tr.draw(pad.toString(), x0, y0 + padY + i * lineH, 0x01000000, false, mb, consumers, TextRenderer.TextLayerType.NORMAL, bgCol, 0xF000F0);
            }
            String tail = "▼";
            tr.draw(tail, -tr.getWidth(tail) / 2f, y0 + padY + sp.lines.size() * lineH - 2, (Math.max(8, Math.round(alpha * 255)) << 24) | (bg & 0xFFFFFF),
                    false, mb, consumers, TextRenderer.TextLayerType.NORMAL, 0, 0xF000F0);
        }
        // 2) the text, moved toward the camera and with a depth offset: it always stays in front of the background
        matrices.push();
        matrices.translate(0, 0, 1.5f);
        Matrix4f mt = matrices.peek().getPositionMatrix();
        boolean shadow = bgCol == 0; // no background: with shadow so it reads over anything
        for (int i = 0; i < sp.lines.size(); i++) {
            String l = sp.lines.get(i);
            int show = Math.max(0, Math.min(l.length(), visible - used));
            used += l.length() + 1;
            if (show <= 0) continue;
            String txt = " " + l.substring(0, show) + (typing && show < l.length() ? "_" : "");
            tr.draw(txt, x0, y0 + padY + i * lineH, textCol, shadow, mt, consumers, TextRenderer.TextLayerType.POLYGON_OFFSET, 0, 0xF000F0);
        }
        matrices.pop();
        matrices.pop();
    }

    /**
     * Bar made with the TextRenderer background (spaces with a background color): looks as good as vanilla
     * names with any graphics settings. width in text units; f from 0 to 1.
     */
    /**
     * Wheel-shaped timer: 24 segments in a circle that switch off clockwise from the top.
     * Each segment is the background of a text space (looks the same with or without shaders, like the speech bubbles).
     */
    private static void wheel(TextRenderer tr, MatrixStack matrices, VertexConsumerProvider consumers, float cx, float cy,
                              float radius, float segW, float segH, float f, int col) {
        if (!dev.tcgshop.client.ClientSettings.INSTANCE.patienceBars) return;
        int n = 24;
        int fill = Math.round(Math.max(0, Math.min(1, f)) * n);
        for (int i = 0; i < n; i++) {
            matrices.push();
            matrices.translate(cx, cy, 0);
            matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotationDegrees(i * 360f / n));
            matrices.translate(0, -radius, 0);
            matrices.scale(segW / 5f, segH / 10f, 1); // the background of " " is 5 x 10
            matrices.translate(-1.5f, -4f, 0);         // centered on the circle point
            boolean lit = i < fill;
            tr.draw(" ", 0, 0, 0x01000000, false, matrices.peek().getPositionMatrix(), consumers, TextRenderer.TextLayerType.NORMAL,
                    lit ? col : 0xB0101018, 0xF000F0);
            matrices.pop();
        }
    }

    /** Background rectangle (same vertex order as the vanilla text background). */
    private static MatrixStack.Entry bubbleEntry;

    private static void v(VertexConsumer vc, Matrix4f m, float x, float y, float z, int argb, float u, float vv) {
        vc.vertex(m, x, y, z).color(argb).light(0xF000F0); // text background format: position, color and light
    }

    private static void quad(VertexConsumer vc, Matrix4f m, float ax, float ay, float bx, float by, float cx, float cy, float dx, float dy, float z, int argb) {
        v(vc, m, ax, ay, z, argb, 0, 1);
        v(vc, m, bx, by, z, argb, 1, 1);
        v(vc, m, cx, cy, z, argb, 1, 0);
        v(vc, m, dx, dy, z, argb, 0, 0);
        // back face (in case the layer culls faces by orientation)
        v(vc, m, dx, dy, z, argb, 0, 0);
        v(vc, m, cx, cy, z, argb, 1, 0);
        v(vc, m, bx, by, z, argb, 1, 1);
        v(vc, m, ax, ay, z, argb, 0, 1);
    }

    private static void rect(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, float z, int argb) {
        quad(vc, m, x0, y1, x1, y1, x1, y0, x0, y0, z * 40, argb);
    }

    private static final net.minecraft.util.Identifier BUBBLE_TEX = net.minecraft.util.Identifier.of("tcgshop", "textures/misc/white.png");

    /** Speech bubble tail: base at y = top between xl and xr, point at y = tip. */
    private static void tail(VertexConsumer vc, Matrix4f m, float xl, float top, float xr, float tip, float z, int argb) {
        quad(vc, m, -0.6f, tip, 0.6f, tip, xr, top, xl, top, z * 40, argb);
    }

    private static void vertex(VertexConsumer vc, MatrixStack matrices, Matrix4f m, float x, float y, float u, float v) {
        vc.vertex(m, x, y, 0).color(255, 255, 255, 255).texture(u, v).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0)
                .normal(matrices.peek(), 0, 1, 0);
    }

    /**
     * 3D backpack on the NPC's back (customers and scalpers with straps painted on their skin, and some fixed
     * customers). Its own small model (bag 7x9x3, front pocket, handle) follows the body part, so it leans when
     * sneaking. UV layout matches tools/gen_npc_skins.py backpack_texture().
     */
    private static final class BackpackFeature<T extends MobEntity & TcgNpc> extends FeatureRenderer<T, PlayerEntityModel<T>> {
        private final ModelPart bag;

        BackpackFeature(FeatureRendererContext<T, PlayerEntityModel<T>> context) {
            super(context);
            ModelData data = new ModelData();
            data.getRoot().addChild("bag", ModelPartBuilder.create()
                    .uv(0, 0).cuboid(-3.5f, 1.0f, 2.3f, 7, 9, 3)     // bag, just behind the jacket layer (z 2.25)
                    .uv(0, 12).cuboid(-2.5f, 5.0f, 5.3f, 5, 4, 1)    // front pocket
                    .uv(0, 18).cuboid(-1.5f, 0.0f, 3.3f, 3, 1, 1),   // handle
                    ModelTransform.NONE);
            this.bag = TexturedModelData.of(data, 32, 32).createModel().getChild("bag");
        }

        @Override
        public void render(MatrixStack matrices, VertexConsumerProvider consumers, int light, T entity, float limbAngle,
                           float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch) {
            if (entity.isInvisible() || !entity.getEquippedStack(EquipmentSlot.CHEST).isEmpty()) return;
            int color = lookOf(entity, entity).backpack();
            if (color < 0) return;
            matrices.push();
            getContextModel().body.rotate(matrices);
            VertexConsumer vc = consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(NpcSkins.backpackTexture(color)));
            bag.render(matrices, vc, light, LivingEntityRenderer.getOverlay(entity, 0));
            matrices.pop();
        }
    }

    /** Name (vanilla) + role / title line above. */
    @Override
    protected void renderLabelIfPresent(T entity, Text text, MatrixStack matrices, VertexConsumerProvider consumers, int light, float tickDelta) {
        super.renderLabelIfPresent(entity, text, matrices, consumers, light, tickDelta);
        if (dispatcher.getSquaredDistanceToCamera(entity) > 32 * 32) return;
        MutableText line = Text.translatable(entity.roleKey()).formatted(Formatting.GRAY);
        String title = entity.npcTitle();
        if (title != null && !title.isEmpty()) line = line.append(Text.literal(" · ").formatted(Formatting.DARK_GRAY))
                .append(Titles.display(title).copy().formatted(Formatting.GOLD, Formatting.ITALIC));
        matrices.push();
        matrices.translate(0, entity.getHeight() + 0.77f, 0);
        matrices.multiply(dispatcher.getRotation());
        matrices.scale(0.02f, -0.02f, 0.02f);
        TextRenderer tr = getTextRenderer();
        float x = -tr.getWidth(line) / 2f;
        tr.draw(line, x, 0, 0xFFFFFFFF, false, matrices.peek().getPositionMatrix(), consumers, TextRenderer.TextLayerType.SEE_THROUGH, 0x40000000, light);
        tr.draw(line, x, 0, 0xFFFFFFFF, false, matrices.peek().getPositionMatrix(), consumers, TextRenderer.TextLayerType.NORMAL, 0, light);
        matrices.pop();
    }
}

package dev.tcgshop.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.tcgshop.TcgShop;
import dev.tcgshop.collection.Titles;
import dev.tcgshop.entity.NpcIdentity;
import dev.tcgshop.entity.TcgNpc;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.HumanoidArmorModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import org.joml.Matrix4f;

/**
 * Renderer for the mod's NPCs (customers, employees, Scalper, Wholesaler...): generated skin (NpcSkins) on the
 * player model with the outer layer (hat, jacket, sleeves, pants) visible, slim model for female skins, a 3D
 * backpack for some of them, name + a line above with their role and Pokémon title, and the emotion speech bubble.
 */
public class TcgNpcRenderer<T extends Mob & TcgNpc> extends HumanoidMobRenderer<T, PlayerModel<T>> {
    private static final ResourceLocation[] BUBBLES = {
            null,
            ResourceLocation.fromNamespaceAndPath(TcgShop.MOD_ID, "textures/entity/scalper_think_money.png"),
            ResourceLocation.fromNamespaceAndPath(TcgShop.MOD_ID, "textures/entity/scalper_think_love.png"),
            ResourceLocation.fromNamespaceAndPath(TcgShop.MOD_ID, "textures/entity/npc_think_deal.png"),
            ResourceLocation.fromNamespaceAndPath(TcgShop.MOD_ID, "textures/entity/npc_think_angry.png"),
            ResourceLocation.fromNamespaceAndPath(TcgShop.MOD_ID, "textures/entity/npc_think_question.png")};

    private final PlayerModel<T> wide, slim;

    public TcgNpcRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
        this.wide = this.model;
        this.slim = new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER_SLIM), true);
        // armor worn by NPCs (e.g. top thief tiers), drawn like on players. Subclasses (CustomerNpcRenderer) may add
        // their own armor layer: this one then stays quiet so the armor is never drawn twice.
        addLayer(new SharedArmorFeature(
                new HumanoidArmorModel<T>(ctx.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidArmorModel<T>(ctx.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                ctx));
        addLayer(new BackpackFeature<>(this));
        // 1.1: the elytra a robbed collector puts on to chase you through the air (any NPC with one in the chest slot)
        addLayer(new net.minecraft.client.renderer.entity.layers.ElytraLayer<>(this, ctx.getModelSet()));
    }

    /** Gliding pose while fall-flying (elytra), like a player: the body tilts forward along the flight. */
    @Override
    protected void setupRotations(T entity, PoseStack matrices, float bob, float yBodyRot, float tickDelta, float scale) {
        super.setupRotations(entity, matrices, bob, yBodyRot, tickDelta, scale);
        if (!entity.isFallFlying()) return;
        float ticks = entity.getFallFlyingTicks() + tickDelta;
        float k = net.minecraft.util.Mth.clamp(ticks * ticks / 100f, 0f, 1f);
        matrices.mulPose(com.mojang.math.Axis.XP.rotationDegrees(k * (-90f - entity.getViewXRot(tickDelta))));
    }

    /** Vanilla armor layer that skips rendering when the renderer has another armor layer (added by a subclass). */
    private final class SharedArmorFeature extends HumanoidArmorLayer<T, PlayerModel<T>, HumanoidArmorModel<T>> {
        private Boolean duplicate;

        SharedArmorFeature(HumanoidArmorModel<T> inner, HumanoidArmorModel<T> outer, EntityRendererProvider.Context ctx) {
            super(TcgNpcRenderer.this, inner, outer, ctx.getModelManager());
        }

        @Override
        public void render(PoseStack matrices, MultiBufferSource consumers, int light, T entity, float limbAngle,
                           float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch) {
            if (duplicate == null) {
                boolean dup = false;
                for (RenderLayer<T, PlayerModel<T>> f : TcgNpcRenderer.this.layers)
                    if (f != this && f instanceof HumanoidArmorLayer) dup = true;
                duplicate = dup;
            }
            if (duplicate) return;
            super.render(matrices, consumers, light, entity, limbAngle, limbDistance, tickDelta, animationProgress, headYaw, headPitch);
        }
    }

    /** Skin look of an NPC: role, skin int and its name (the name decides the sex of skins that don't store it). */
    static NpcSkins.Look lookOf(Mob entity, TcgNpc npc) {
        Component name = entity.getCustomName();
        return NpcSkins.look(npc.roleKey(), npc.npcSkin(), name == null ? null : name.getString());
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return lookOf(entity, entity).texture();
    }

    @Override
    public void render(T entity, float yaw, float tickDelta, PoseStack matrices, MultiBufferSource consumers, int light) {
        this.model = lookOf(entity, entity).slim() ? slim : wide;
        // second skin layer: hair volume, caps, jackets, skirts... (the parts are visible by default, but make sure)
        this.model.setAllVisible(true);
        super.render(entity, yaw, tickDelta, matrices, consumers, light);
        dev.tcgshop.client.ClientSpeech.Speech speech = dev.tcgshop.client.ClientSpeech.get(entity.getId());
        if (speech != null && entityRenderDispatcher.distanceToSqr(entity) < 40 * 40) {
            speechBubble(entity, speech, tickDelta, matrices, consumers);
            return;
        }
        if (entity.wantsAttention()) {
            // gold "!" that bounces and spins, visible through walls: wants a deal with you
            float t = entity.tickCount + tickDelta;
            matrices.pushPose();
            matrices.translate(0, entity.getBbHeight() + 1.25f + Math.abs((float) Math.sin(t * 0.12f)) * 0.18f, 0);
            matrices.mulPose(entityRenderDispatcher.cameraOrientation());
            float s = 0.055f * (1 + 0.08f * (float) Math.sin(t * 0.3f));
            matrices.scale(s, -s, s);
            Font tr = getFont();
            Component mark = Component.literal("!").withStyle(ChatFormatting.BOLD);
            float x = -tr.width(mark) / 2f;
            Matrix4f mm = matrices.last().pose();
            tr.drawInBatch(mark, x, -4, 0x80FFD34D, false, mm, consumers, Font.DisplayMode.SEE_THROUGH, 0, 0xF000F0);
            tr.drawInBatch(mark, x + 0.6f, -3.4f, 0xFF7A4A00, false, mm, consumers, Font.DisplayMode.NORMAL, 0, 0xF000F0);
            tr.drawInBatch(mark, x, -4, 0xFFFFD34D, false, mm, consumers, Font.DisplayMode.POLYGON_OFFSET, 0, 0xF000F0);
            int wait = entity.waitPermille();
            if (wait >= 0) {
                // patience bar: green -> yellow -> red; leaves when it reaches 0
                float f = wait / 1000f;
                int col = f > 0.5f ? 0xFF5BD46B : f > 0.25f ? 0xFFFFC233 : 0xFFFF5050;
                if (f <= 0.25f && ((int) (t / 4)) % 2 == 0) col = 0xFFFFFFFF;
                wheel(tr, matrices, consumers, 0, -0.5f, 9.5f, 1.8f, 3.4f, f, col); // wheel around the "!"
            }
            matrices.popPose();
            return;
        }
        int waitQ = entity.waitPermille();
        if (waitQ >= 0) {
            // in the register queue: their patience bar above the head
            float tq = entity.tickCount + tickDelta, f = waitQ / 1000f;
            int col = f > 0.5f ? 0xFF5BD46B : f > 0.25f ? 0xFFFFC233 : 0xFFFF5050;
            if (f <= 0.25f && ((int) (tq / 4)) % 2 == 0) col = 0xFFFFFFFF;
            matrices.pushPose();
            matrices.translate(0, entity.getBbHeight() + 0.62f, 0);
            matrices.mulPose(entityRenderDispatcher.cameraOrientation());
            matrices.scale(0.025f, -0.025f, 0.025f);
            wheel(getFont(), matrices, consumers, 0, -8f, 8f, 2.2f, 4.2f, f, col);
            matrices.popPose();
        }
        int emote = entity.emote();
        if (emote <= 0 || emote >= BUBBLES.length) return;
        matrices.pushPose();
        float bob = (float) Math.sin((entity.tickCount + tickDelta) * 0.15f) * 0.05f;
        matrices.translate(0.4f, entity.getBbHeight() + 0.95f + bob, 0);
        matrices.mulPose(entityRenderDispatcher.cameraOrientation());
        matrices.scale(-0.55f, -0.55f, 0.55f);
        VertexConsumer vc = consumers.getBuffer(RenderType.entityCutoutNoCull(BUBBLES[emote]));
        Matrix4f m = matrices.last().pose();
        vertex(vc, matrices, m, -0.5f, -0.5f, 0f, 0f);
        vertex(vc, matrices, m, -0.5f, 0.5f, 0f, 1f);
        vertex(vc, matrices, m, 0.5f, 0.5f, 1f, 1f);
        vertex(vc, matrices, m, 0.5f, -0.5f, 1f, 0f);
        matrices.popPose();
    }

    /**
     * Comic speech bubble above the head: white background with border and a tail toward the NPC, text that appears letter by
     * letter (the "talking" beeps come from ClientSpeech) and fades out when done.
     */
    private void speechBubble(T entity, dev.tcgshop.client.ClientSpeech.Speech sp, float tickDelta, PoseStack matrices, MultiBufferSource consumers) {
        var cfg = dev.tcgshop.client.ClientSettings.INSTANCE;
        if (!cfg.bubbles) return;
        float now = entity.level().getGameTime() + tickDelta;
        float alpha = sp.alpha(now);
        if (alpha <= 0.02f) return;
        Font tr = getFont();
        int w = 0;
        for (String l : sp.lines) w = Math.max(w, tr.width(l));
        int lineH = 10, padX = 5, padY = 4;
        int bw = w + padX * 2, bh = sp.lines.size() * lineH + padY * 2 - 1;
        float pop = Math.min(1, (now - sp.start) / 4f);
        float scale = 0.021f * cfg.size() * (0.7f + 0.3f * (float) Math.sin(pop * Math.PI / 2));
        matrices.pushPose();
        matrices.translate(0, entity.getBbHeight() + 1.08f + (cfg.size() - 1f) * 0.25f, 0);
        matrices.mulPose(entityRenderDispatcher.cameraOrientation());
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
        // 2) the text, moved toward the camera and with a depth offset: it always stays in front of the background
        matrices.pushPose();
        matrices.translate(0, 0, 1.5f);
        Matrix4f mt = matrices.last().pose();
        boolean shadow = bgCol == 0; // no background: with shadow so it reads over anything
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
    }

    /**
     * Bar made with the TextRenderer background (spaces with a background color): looks as good as vanilla
     * names with any graphics settings. width in text units; f from 0 to 1.
     */
    /**
     * Wheel-shaped timer: 24 segments in a circle that switch off clockwise from the top.
     * Each segment is the background of a text space (looks the same with or without shaders, like the speech bubbles).
     */
    private static void wheel(Font tr, PoseStack matrices, MultiBufferSource consumers, float cx, float cy,
                              float radius, float segW, float segH, float f, int col) {
        if (!dev.tcgshop.client.ClientSettings.INSTANCE.patienceBars) return;
        int n = 24;
        int fill = Math.round(Math.max(0, Math.min(1, f)) * n);
        for (int i = 0; i < n; i++) {
            matrices.pushPose();
            matrices.translate(cx, cy, 0);
            matrices.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(i * 360f / n));
            matrices.translate(0, -radius, 0);
            matrices.scale(segW / 5f, segH / 10f, 1); // the background of " " is 5 x 10
            matrices.translate(-1.5f, -4f, 0);         // centered on the circle point
            boolean lit = i < fill;
            tr.drawInBatch(" ", 0, 0, 0x01000000, false, matrices.last().pose(), consumers, Font.DisplayMode.NORMAL,
                    lit ? col : 0xB0101018, 0xF000F0);
            matrices.popPose();
        }
    }

    /** Background rectangle (same vertex order as the vanilla text background). */
    private static PoseStack.Pose bubbleEntry;

    private static void v(VertexConsumer vc, Matrix4f m, float x, float y, float z, int argb, float u, float vv) {
        vc.addVertex(m, x, y, z).setColor(argb).setLight(0xF000F0); // text background format: position, color and light
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

    private static final net.minecraft.resources.ResourceLocation BUBBLE_TEX = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("tcgshop", "textures/misc/white.png");

    /** Speech bubble tail: base at y = top between xl and xr, point at y = tip. */
    private static void tail(VertexConsumer vc, Matrix4f m, float xl, float top, float xr, float tip, float z, int argb) {
        quad(vc, m, -0.6f, tip, 0.6f, tip, xr, top, xl, top, z * 40, argb);
    }

    private static void vertex(VertexConsumer vc, PoseStack matrices, Matrix4f m, float x, float y, float u, float v) {
        vc.addVertex(m, x, y, 0).setColor(255, 255, 255, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0)
                .setNormal(matrices.last(), 0, 1, 0);
    }

    /**
     * 3D backpack on the NPC's back (customers and scalpers with straps painted on their skin, and some fixed
     * customers). Its own small model (bag 7x9x3, front pocket, handle) follows the body part, so it leans when
     * sneaking. UV layout matches tools/gen_npc_skins.py backpack_texture().
     */
    private static final class BackpackFeature<T extends Mob & TcgNpc> extends RenderLayer<T, PlayerModel<T>> {
        private final ModelPart bag;

        BackpackFeature(RenderLayerParent<T, PlayerModel<T>> context) {
            super(context);
            MeshDefinition data = new MeshDefinition();
            data.getRoot().addOrReplaceChild("bag", CubeListBuilder.create()
                    .texOffs(0, 0).addBox(-3.5f, 1.0f, 2.3f, 7, 9, 3)     // bag, just behind the jacket layer (z 2.25)
                    .texOffs(0, 12).addBox(-2.5f, 5.0f, 5.3f, 5, 4, 1)    // front pocket
                    .texOffs(0, 18).addBox(-1.5f, 0.0f, 3.3f, 3, 1, 1),   // handle
                    PartPose.ZERO);
            this.bag = LayerDefinition.create(data, 32, 32).bakeRoot().getChild("bag");
        }

        @Override
        public void render(PoseStack matrices, MultiBufferSource consumers, int light, T entity, float limbAngle,
                           float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch) {
            if (entity.isInvisible() || !entity.getItemBySlot(EquipmentSlot.CHEST).isEmpty()) return;
            int color = lookOf(entity, entity).backpack();
            if (color < 0) return;
            matrices.pushPose();
            getParentModel().body.translateAndRotate(matrices);
            VertexConsumer vc = consumers.getBuffer(RenderType.entityCutoutNoCull(NpcSkins.backpackTexture(color)));
            bag.render(matrices, vc, light, LivingEntityRenderer.getOverlayCoords(entity, 0));
            matrices.popPose();
        }
    }

    /** Name (vanilla) + role / title line above. */
    @Override
    protected void renderNameTag(T entity, Component text, PoseStack matrices, MultiBufferSource consumers, int light, float tickDelta) {
        super.renderNameTag(entity, text, matrices, consumers, light, tickDelta);
        if (entityRenderDispatcher.distanceToSqr(entity) > 32 * 32) return;
        MutableComponent line = Component.translatable(entity.roleKey()).withStyle(ChatFormatting.GRAY);
        String title = entity.npcTitle();
        if (title != null && !title.isEmpty()) line = line.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Titles.display(title).copy().withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC));
        matrices.pushPose();
        matrices.translate(0, entity.getBbHeight() + 0.77f, 0);
        matrices.mulPose(entityRenderDispatcher.cameraOrientation());
        matrices.scale(0.02f, -0.02f, 0.02f);
        Font tr = getFont();
        float x = -tr.width(line) / 2f;
        tr.drawInBatch(line, x, 0, 0xFFFFFFFF, false, matrices.last().pose(), consumers, Font.DisplayMode.SEE_THROUGH, 0x40000000, light);
        tr.drawInBatch(line, x, 0, 0xFFFFFFFF, false, matrices.last().pose(), consumers, Font.DisplayMode.NORMAL, 0, light);
        matrices.popPose();
    }
}

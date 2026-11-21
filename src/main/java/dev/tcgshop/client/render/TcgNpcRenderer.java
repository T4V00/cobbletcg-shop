package dev.tcgshop.client.render;

import dev.tcgshop.TcgShop;
import dev.tcgshop.collection.Titles;
import dev.tcgshop.entity.NpcIdentity;
import dev.tcgshop.entity.TcgNpc;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.BipedEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;

/**
 * Renderizador de los NPC del mod (clientes, empleados, Scalper, Mayorista): piel por defecto de Minecraft
 * (modelo ancho o fino), nombre + una línea encima con su rol y su título de Pokémon, y el bocadillo de emoción.
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
    }

    private static int skin(TcgNpc npc) {
        return Math.floorMod(npc.npcSkin(), 64);
    }

    @Override
    public Identifier getTexture(T entity) {
        return NpcSkins.texture(entity.roleKey(), skin(entity));
    }

    @Override
    public void render(T entity, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
        this.model = NpcSkins.slim(skin(entity)) ? slim : wide;
        super.render(entity, yaw, tickDelta, matrices, consumers, light);
        dev.tcgshop.client.ClientSpeech.Speech speech = dev.tcgshop.client.ClientSpeech.get(entity.getId());
        if (speech != null && dispatcher.getSquaredDistanceToCamera(entity) < 40 * 40) {
            speechBubble(entity, speech, tickDelta, matrices, consumers);
            return;
        }
        if (entity.wantsAttention()) {
            // "!" dorado que bota y gira, visible a través de las paredes: quiere un trato contigo
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
                // barra de paciencia: verde -> amarilla -> roja; si llega a 0 se marcha
                float f = wait / 1000f;
                int col = f > 0.5f ? 0xFF5BD46B : f > 0.25f ? 0xFFFFC233 : 0xFFFF5050;
                if (f <= 0.25f && ((int) (t / 4)) % 2 == 0) col = 0xFFFFFFFF;
                textBar(tr, matrices, consumers, 0, 6.5f, 18f, f, col);
            }
            matrices.pop();
            return;
        }
        int waitQ = entity.waitPermille();
        if (waitQ >= 0) {
            // en la fila de la caja: su barra de paciencia sobre la cabeza
            float tq = entity.age + tickDelta, f = waitQ / 1000f;
            int col = f > 0.5f ? 0xFF5BD46B : f > 0.25f ? 0xFFFFC233 : 0xFFFF5050;
            if (f <= 0.25f && ((int) (tq / 4)) % 2 == 0) col = 0xFFFFFFFF;
            matrices.push();
            matrices.translate(0, entity.getHeight() + 0.62f, 0);
            matrices.multiply(dispatcher.getRotation());
            matrices.scale(0.025f, -0.025f, 0.025f);
            textBar(getTextRenderer(), matrices, consumers, 0, -2.5f, 22f, f, col);
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
     * Bocadillo de cómic sobre la cabeza: fondo blanco con borde y piquito hacia el NPC, texto que aparece letra a
     * letra (los pitidos de "hablar" los pone ClientSpeech) y se desvanece al terminar.
     */
    private void speechBubble(T entity, dev.tcgshop.client.ClientSpeech.Speech sp, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers) {
        float now = entity.getWorld().getTime() + tickDelta;
        float alpha = sp.alpha(now);
        if (alpha <= 0.02f) return;
        TextRenderer tr = getTextRenderer();
        int w = 0;
        for (String l : sp.lines) w = Math.max(w, tr.getWidth(l));
        int lineH = 10, padX = 5, padY = 4;
        int bw = w + padX * 2, bh = sp.lines.size() * lineH + padY * 2 - 1;
        float pop = Math.min(1, (now - sp.start) / 4f);
        float scale = 0.021f * (0.7f + 0.3f * (float) Math.sin(pop * Math.PI / 2));
        matrices.push();
        matrices.translate(0, entity.getHeight() + 1.08f, 0);
        matrices.multiply(dispatcher.getRotation());
        matrices.scale(scale, -scale, scale);
        Matrix4f m = matrices.peek().getPositionMatrix();
        bubbleEntry = matrices.peek();
        int a = Math.round(alpha * 245);
        int bgCol = (a << 24) | 0xFFFFFF;
        int textCol = (Math.max(8, Math.round(alpha * 255)) << 24) | 0x1E1E28;
        float x0 = -bw / 2f, y0 = -6 - bh;
        // El fondo blanco lo pinta el propio TextRenderer (como el fondo de los nombres vanilla): fondo y letras salen de
        // la misma llamada y en la misma capa, así se ven bien en cualquier ángulo, con Fabulous y con shaders (Iris).
        // Cada línea se rellena con espacios hasta el ancho de la burbuja para que el fondo quede como un bloque.
        int visible = sp.visibleChars(now), used = 0;
        int target = w + padX * 2;
        boolean typing = visible < sp.plain.length();
        for (int i = 0; i < sp.lines.size(); i++) {
            String l = sp.lines.get(i);
            int show = Math.max(0, Math.min(l.length(), visible - used));
            used += l.length() + 1;
            StringBuilder line = new StringBuilder(" ").append(l, 0, show);
            if (typing && show > 0 && show < l.length()) line.append('_');
            while (tr.getWidth(line.toString()) < target - 2) line.append(' ');
            tr.draw(line.toString(), x0, y0 + padY + i * lineH, textCol, false, m, consumers, TextRenderer.TextLayerType.NORMAL, bgCol, 0xF000F0);
        }
        // piquito hacia el NPC
        String tail = "▼";
        tr.draw(tail, -tr.getWidth(tail) / 2f, y0 + padY + sp.lines.size() * lineH - 2, (Math.max(8, Math.round(alpha * 255)) << 24) | 0xFFFFFF, false, m, consumers,
                TextRenderer.TextLayerType.NORMAL, 0, 0xF000F0);
        matrices.pop();
    }

    /**
     * Barra hecha con el fondo del TextRenderer (espacios con color de fondo): se ve igual de bien que los nombres
     * vanilla con cualquier configuración gráfica. width en unidades del texto; f de 0 a 1.
     */
    private static void textBar(TextRenderer tr, MatrixStack matrices, VertexConsumerProvider consumers, float cx, float y, float width, float f, int col) {
        int cells = 24;
        matrices.push();
        float sx = width / (cells * 4f); // un espacio mide 4 unidades
        matrices.translate(cx - width / 2f, y, 0);
        matrices.scale(sx, 1, 1);
        Matrix4f m = matrices.peek().getPositionMatrix();
        String bg = " ".repeat(cells);
        tr.draw(bg, 0, 0, 0x01000000, false, m, consumers, TextRenderer.TextLayerType.NORMAL, 0xCC101018, 0xF000F0);
        int fill = Math.round(Math.max(0, Math.min(1, f)) * cells);
        if (fill > 0) {
            matrices.translate(0, 0, -0.01f);
            tr.draw(" ".repeat(fill), 0, 0, 0x01000000, false, matrices.peek().getPositionMatrix(), consumers, TextRenderer.TextLayerType.NORMAL, col, 0xF000F0);
        }
        matrices.pop();
    }

    /** Rectángulo de fondo (mismo orden de vértices que el fondo de texto vanilla). */
    private static MatrixStack.Entry bubbleEntry;

    private static void v(VertexConsumer vc, Matrix4f m, float x, float y, float z, int argb, float u, float vv) {
        vc.vertex(m, x, y, z).color(argb).light(0xF000F0); // formato del fondo de texto: posición, color y luz
    }

    private static void quad(VertexConsumer vc, Matrix4f m, float ax, float ay, float bx, float by, float cx, float cy, float dx, float dy, float z, int argb) {
        v(vc, m, ax, ay, z, argb, 0, 1);
        v(vc, m, bx, by, z, argb, 1, 1);
        v(vc, m, cx, cy, z, argb, 1, 0);
        v(vc, m, dx, dy, z, argb, 0, 0);
        // cara trasera (por si la capa descarta las caras por su orientación)
        v(vc, m, dx, dy, z, argb, 0, 0);
        v(vc, m, cx, cy, z, argb, 1, 0);
        v(vc, m, bx, by, z, argb, 1, 1);
        v(vc, m, ax, ay, z, argb, 0, 1);
    }

    private static void rect(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, float z, int argb) {
        quad(vc, m, x0, y1, x1, y1, x1, y0, x0, y0, z * 40, argb);
    }

    private static final net.minecraft.util.Identifier BUBBLE_TEX = net.minecraft.util.Identifier.of("tcgshop", "textures/misc/white.png");

    /** Piquito del bocadillo: base en y = top entre xl y xr, punta en y = tip. */
    private static void tail(VertexConsumer vc, Matrix4f m, float xl, float top, float xr, float tip, float z, int argb) {
        quad(vc, m, -0.6f, tip, 0.6f, tip, xr, top, xl, top, z * 40, argb);
    }

    private static void vertex(VertexConsumer vc, MatrixStack matrices, Matrix4f m, float x, float y, float u, float v) {
        vc.vertex(m, x, y, 0).color(255, 255, 255, 255).texture(u, v).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0)
                .normal(matrices.peek(), 0, 1, 0);
    }

    /** Nombre (vanilla) + línea de rol / título encima. */
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

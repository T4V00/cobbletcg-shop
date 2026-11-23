package dev.tcgshop.client.binder;

import dev.masterball.api.card.PrintVariant;
import dev.tcgshop.client.screen.Card3D;
import dev.tcgshop.client.screen.Draw;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import dev.tcgshop.TcgSounds;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import static dev.tcgshop.client.screen.Draw.withAlpha;

/**
 * Zoomed view of a card inside the binders (overlay on the SAME screen, so the binder keeps its
 * scroll, filters and page). Dimmed background, the card centered at the largest scale that fits (63:88 ratio) with
 * a slight tilt that follows the mouse and holo foil depending on rarity, and below it name, set, rarity and variant.
 * Closed with ESC, right-click or a click outside the card.
 */
public final class CardZoom {
    private static final double OPEN_S = 0.18;

    private Catalog.Card card;
    private boolean owned;
    private double openedAt;
    private float rx, ry;
    private double lastT;
    /** Current card rectangle (to know whether a click lands inside or outside). */
    private int cardX, cardY, cardW, cardH;

    public boolean isOpen() {
        return card != null;
    }

    public void open(Catalog.Card c, boolean owned) {
        if (c == null) return;
        this.card = c;
        this.owned = owned;
        this.openedAt = now();
        this.rx = 0;
        this.ry = 0;
        this.lastT = openedAt;
        sound(1.25f);
    }

    public void close() {
        if (card == null) return;
        card = null;
        sound(0.9f);
    }

    private static double now() {
        return System.nanoTime() / 1e9;
    }

    private static void sound(float pitch) {
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.getSoundManager().play(PositionedSoundInstance.master(TcgSounds.CARD_FLIP, pitch, 0.6f));
    }

    // ------------------------------------------------------------------ render

    public void render(DrawContext ctx, int width, int height, int mouseX, int mouseY) {
        if (card == null) return;
        double t = now();
        float open = (float) Math.min(1.0, (t - openedAt) / OPEN_S);
        float ease = (float) Draw.easeOutCubic(open);
        var font = Ui.font();

        // space reserved for the text below
        int textH = 48;
        int maxH = Math.max(60, Math.min(height - 24 - textH, Math.round(height * 0.86f) - textH));
        int maxW = Math.max(40, width - 40);
        int ch = maxH, cw = Math.round(ch * Draw.CARD_RATIO);
        if (cw > maxW) { cw = maxW; ch = Math.round(cw / Draw.CARD_RATIO); }
        int top = (height - ch - textH) / 2;
        int cx = width / 2, cy = top + ch / 2;
        cardX = cx - cw / 2;
        cardY = top;
        cardW = cw;
        cardH = ch;

        var m = ctx.getMatrices();
        m.push();
        m.translate(0, 0, 600);
        ctx.fill(0, 0, width, height, withAlpha(0xE0050508, ease));
        int rc = card.rarity().color;
        if (card.rarity().tier > 0 && owned) Draw.glow(ctx, cx, cy, cw * 1.05f, ch * 1.05f, rc, 0.35f * ease);
        ctx.draw();

        // gentle tilt: follows the mouse if it is over the card; otherwise, a slow sway
        boolean over = Ui.in(mouseX, mouseY, cardX, cardY, cardW, cardH);
        float tx, ty;
        if (over) {
            ty = MathHelper.clamp((mouseX - cx) / (cw / 2f), -1, 1) * 11f;
            tx = -MathHelper.clamp((mouseY - cy) / (ch / 2f), -1, 1) * 8f;
        } else {
            ty = (float) Math.sin(t * 0.9) * 3f;
            tx = (float) Math.cos(t * 0.7) * 1.5f;
        }
        float dt = (float) MathHelper.clamp(t - lastT, 0, 0.1);
        lastT = t;
        float k = 1f - (float) Math.exp(-dt * 10f);
        rx += (tx - rx) * k;
        ry += (ty - ry) * k;

        float scale = 0.85f + 0.15f * ease;
        m.push();
        m.translate(cx, cy, 10);
        m.scale(scale, scale, 1);
        if (Draw.textureExists(card.texture())) {
            int tier = card.rarity().tier;
            int foil = !owned ? 0 : tier >= 3 ? 2 : tier >= 1 ? 1 : 0;
            Card3D.shadow(ctx, cw, ch, rx, ry, 0.6f, ease);
            Card3D.draw(ctx, card.texture(), cw, ch, rx, ry, ease, foil, 0, owned ? 0f : 0.55f, t);
        } else {
            ctx.fill(-cw / 2 + 4, -ch / 2 + 6, cw / 2 + 4, ch / 2 + 6, withAlpha(0xFF000000, 0.45f * ease));
            Draw.cardFace(ctx, card.texture(), card.stack(), -cw / 2f, -ch / 2f, cw, ch, ease);
            Ui.outline(ctx, -cw / 2, -ch / 2, cw, ch, withAlpha(rc, ease));
        }
        if (!owned) {
            ctx.draw();
            m.translate(0, 0, 5);
            int bw = Math.min(cw - 10, font.getWidth(Text.translatable("tcgshop.binder.not_owned")) + 20);
            Ui.roundedGradient(ctx, -bw / 2, -9, bw, 18, 6, withAlpha(0xFF1A1D2A, 0.85f * ease), withAlpha(0xFF0E1018, 0.85f * ease));
            Ui.text(ctx, Text.translatable("tcgshop.binder.not_owned"), -bw / 2, -9, bw, 18, withAlpha(0xFFDDDDDD, Math.max(0.05f, ease)), true);
        }
        m.pop();

        // text: name, set, rarity and variant
        int a = Math.max(5, Math.round(255 * ease)) << 24;
        int y = top + ch + 8;
        Draw.scaledCentered(ctx, card.name().copy().formatted(Formatting.BOLD), cx, y, 1.25f, (Ui.TEXT & 0xFFFFFF) | a);
        y += 15;
        Text info = Text.translatable("masterball.set." + card.set()).copy();
        if (card.number() >= 0) info = info.copy().append(Text.literal("  #" + String.format(java.util.Locale.ROOT, "%03d", card.number())));
        ctx.drawCenteredTextWithShadow(font, info, cx, y, (Ui.MUTED & 0xFFFFFF) | a);
        y += 11;
        Text rar = card.rarity().displayName().copy();
        PrintVariant v = PrintVariant.of(card.stack());
        if (v != PrintVariant.STANDARD && v != PrintVariant.UNLIMITED) rar = rar.copy().append(Text.literal("  ·  ")).append(v.displayName());
        ctx.drawCenteredTextWithShadow(font, rar, cx, y, (rc & 0xFFFFFF) | a);

        Text hint = Text.translatable("tcgshop.binder.zoom_close");
        Draw.scaledCentered(ctx, hint, cx, height - 12, 0.75f, (0x8A8FA8) | (Math.round(200 * ease) << 24));
        m.pop();
    }

    // ------------------------------------------------------------------ input

    /** While the view is open it consumes all clicks: a right-click or a click outside the card closes it. */
    public boolean mouseClicked(double mx, double my, int button) {
        if (card == null) return false;
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT || !Ui.in(mx, my, cardX, cardY, cardW, cardH)) close();
        return true;
    }

    /** ESC (or the binder key) closes the view; all other keys are ignored while it is open. */
    public boolean keyPressed(int keyCode, int scanCode) {
        if (card == null) return false;
        if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_BACKSPACE || dev.tcgshop.client.TcgShopClient.OPEN_BINDER.matchesKey(keyCode, scanCode)) close();
        return true;
    }
}

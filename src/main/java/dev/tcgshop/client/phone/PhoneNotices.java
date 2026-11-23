package dev.tcgshop.client.phone;

import dev.tcgshop.client.binder.Ui;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;

import java.util.List;

/**
 * 1.0: notices shown INSIDE the phone (and the Wholesaler's stall): purchase done, not enough money, locked product...
 * <p>
 * Drawn as an opaque card on its own layer: DrawContext batches fills and text and flushes them later, so a popup drawn at
 * the same depth let the app's text show through it. Here the app is flushed first (ctx.draw()), the card is raised to
 * {@link #Z} (above the GUI items, below vanilla tooltips) and fully opaque, and clicks on it never reach the app underneath.
 */
public final class PhoneNotices {
    private PhoneNotices() {}

    public enum Kind {
        SUCCESS(0xFF3FB86A), ERROR(0xFFE0533D), INFO(0xFF4F8FE8);

        public final int color;

        Kind(int color) { this.color = color; }
    }

    private record Notice(Kind kind, Text title, Text body, ItemStack icon, double at, double life) {}

    /** Depth of the phone's overlay layer (GUI items are drawn at about 150-260, vanilla tooltips at 400). */
    public static final float Z = 300;
    private static final int PANEL_TOP = 0xFF262B3D, PANEL_BOTTOM = 0xFF171A27, SHADOW = 0xFF06070C;

    private static Notice current;
    /** Rectangle drawn in the last frame (x, y, w, h), or null. */
    private static int[] rect;

    private static double now() { return System.nanoTime() / 1e9; }

    public static void push(Kind kind, Text title, Text body, ItemStack icon) {
        current = new Notice(kind, title, body, icon == null ? ItemStack.EMPTY : icon, now(), kind == Kind.ERROR ? 5.0 : 3.8);
    }

    public static void clear() {
        current = null;
        rect = null;
    }

    public static boolean active() {
        return current != null && now() - current.at < current.life;
    }

    /** Is the mouse over the notice drawn last frame? (the screen hides the mouse from the app underneath). */
    public static boolean contains(double mx, double my) {
        return rect != null && Ui.in(mx, my, rect[0], rect[1], rect[2], rect[3]);
    }

    /** A click on the notice closes it and is consumed (never reaches the app underneath). */
    public static boolean click(double mx, double my) {
        if (!contains(mx, my)) return false;
        clear();
        return true;
    }

    /**
     * Draws the current notice as a card of width w whose top edge is y (it slides in from above). Call it AFTER the
     * content it covers and inside the area's scissor.
     */
    public static void render(DrawContext ctx, int x, int y, int w, int mouseX, int mouseY) {
        rect = null;
        Notice n = current;
        if (n == null) return;
        double age = now() - n.at;
        if (age >= n.life) {
            current = null;
            return;
        }
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        boolean icon = !n.icon.isEmpty();
        int tx0 = x + 11 + (icon ? 22 : 0), textW = Math.max(40, x + w - 8 - tx0);
        List<OrderedText> lines = tr.wrapLines(n.body, textW);
        if (lines.size() > 4) lines = lines.subList(0, 4);
        int h = Math.max(icon ? 32 : 0, 19 + lines.size() * 10 + 6);
        double in = MathHelper.clamp(age / 0.18, 0, 1), out = MathHelper.clamp((n.life - age) / 0.22, 0, 1);
        double k = Math.min(1 - Math.pow(1 - in, 3), 1 - Math.pow(1 - out, 3));
        int yy = y - (int) Math.round((1 - k) * (h + 10));
        boolean hover = Ui.in(mouseX, mouseY, x, yy, w, h);

        ctx.draw(); // flush the app (batched fills/text) before the opaque layer
        MatrixStack m = ctx.getMatrices();
        m.push();
        m.translate(0, 0, Z);
        Ui.rounded(ctx, x + 1, yy + 2, w, h, 8, SHADOW);
        Ui.roundedGradient(ctx, x, yy, w, h, 8, hover ? Ui.brighten(PANEL_TOP, 10) : PANEL_TOP, PANEL_BOTTOM);
        Ui.roundedOutline(ctx, x, yy, w, h, 8, n.kind.color);
        Ui.rounded(ctx, x + 4, yy + 5, 3, h - 10, 1, n.kind.color);
        if (icon) ctx.drawItem(n.icon, x + 12, yy + (h - 16) / 2);
        ctx.drawText(tr, n.title.copy().formatted(Formatting.BOLD), tx0, yy + 6, Ui.brighten(n.kind.color, 60), false);
        int ly = yy + 18;
        for (OrderedText l : lines) {
            ctx.drawText(tr, l, tx0, ly, 0xFFE4E8F4, false);
            ly += 10;
        }
        ctx.drawText(tr, "✕", x + w - 11, yy + 5, hover ? 0xFFFFFFFF : 0xFF8A90A8, false);
        // remaining time
        int bw = (int) Math.round((w - 20) * (1 - age / n.life));
        ctx.fill(x + 10, yy + h - 3, x + 10 + Math.max(0, bw), yy + h - 2, n.kind.color);
        ctx.draw();
        m.pop();
        rect = new int[]{x, Math.max(yy, y - 2), w, h - Math.max(0, Math.max(yy, y - 2) - yy)};
    }
}

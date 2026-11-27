package dev.tcgshop.client.phone;

import dev.masterball.api.platform.Platform;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.tcgshop.client.binder.StylePainter;
import dev.tcgshop.client.binder.Ui;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

import static dev.tcgshop.client.screen.Draw.withAlpha;

/**
 * 1.0 phone wallpapers: built-in procedural designs (gradients and simple shapes drawn in code, no textures) plus a
 * solid color with a picker. Chosen in the phone's Settings → Wallpaper and saved only on this client
 * (config/tcgshop-phone-client.json). Drawn behind the home screen.
 */
public final class PhoneWallpaper {
    private PhoneWallpaper() {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = Platform.INSTANCE.configDir().resolve("tcgshop-phone-client.json");

    /** Wallpaper keys (translation: tcgshop.wallpaper.<key>). The last one is the solid color. */
    public static final String[] KEYS = {"aurora", "sunset", "ocean", "forest", "synth", "candy", "midnight", "cards", "solid"};
    public static final int SOLID = KEYS.length - 1;
    /** Ready-made colors of the picker. */
    public static final int[] SWATCHES = {
            0x1B1F3A, 0x2B2A8A, 0x1565C0, 0x00897B, 0x2E7D32, 0x9E9D24, 0xF9A825, 0xEF6C00,
            0xC62828, 0xAD1457, 0x6A1B9A, 0x4E342E, 0x37474F, 0x000000, 0x9E9E9E, 0xECEFF1};

    /** Saved settings. Absent fields keep these defaults. */
    public static final class Settings {
        public int wallpaper = 0;
        /** Solid color (0xRRGGBB) and the picker's hue (0-1) and brightness (0-1). */
        public int color = 0x2B2A8A;
        public float hue = 0.67f, value = 0.55f;
    }

    public static Settings INSTANCE = new Settings();

    public static void load() {
        try {
            if (Files.exists(FILE)) try (Reader r = Files.newBufferedReader(FILE)) {
                Settings s = GSON.fromJson(r, Settings.class);
                if (s != null) INSTANCE = s;
            }
        } catch (Exception e) {
            dev.tcgshop.TcgShop.LOGGER.warn("Could not read {}", FILE, e);
        }
        INSTANCE.wallpaper = Math.floorMod(INSTANCE.wallpaper, KEYS.length);
        INSTANCE.color &= 0xFFFFFF;
        INSTANCE.hue = Mth.clamp(INSTANCE.hue, 0, 1);
        INSTANCE.value = Mth.clamp(INSTANCE.value, 0, 1);
    }

    public static void save() {
        try (Writer w = Files.newBufferedWriter(FILE)) {
            GSON.toJson(INSTANCE, w);
        } catch (Exception e) {
            dev.tcgshop.TcgShop.LOGGER.warn("Could not save {}", FILE, e);
        }
    }

    public static void select(int index) {
        INSTANCE.wallpaper = Math.floorMod(index, KEYS.length);
        save();
    }

    public static void setColor(int rgb) {
        INSTANCE.color = rgb & 0xFFFFFF;
        INSTANCE.wallpaper = SOLID;
        save();
    }

    /** Sets the solid color from the picker's hue / brightness (full saturation, softened a little). */
    public static void setHsv(float hue, float value) {
        INSTANCE.hue = Mth.clamp(hue, 0, 1);
        INSTANCE.value = Mth.clamp(value, 0, 1);
        setColor(Mth.hsvToRgb(INSTANCE.hue, 0.78f, INSTANCE.value));
    }

    public static Component name(int index) {
        return Component.translatable("tcgshop.wallpaper." + KEYS[Math.floorMod(index, KEYS.length)]);
    }

    // ------------------------------------------------------------------ drawing

    /** Draws the chosen wallpaper in that rectangle. */
    public static void draw(GuiGraphics ctx, int x, int y, int w, int h, double t) {
        draw(ctx, x, y, w, h, t, INSTANCE.wallpaper, INSTANCE.color);
    }

    /** Draws wallpaper {@code index} (solid = {@code rgb}) clipped to that rectangle. */
    public static void draw(GuiGraphics ctx, int x, int y, int w, int h, double t, int index, int rgb) {
        if (w <= 0 || h <= 0) return;
        ctx.enableScissor(x, y, x + w, y + h);
        switch (KEYS[Math.floorMod(index, KEYS.length)]) {
            case "sunset" -> sunset(ctx, x, y, w, h, t);
            case "ocean" -> ocean(ctx, x, y, w, h, t);
            case "forest" -> forest(ctx, x, y, w, h, t);
            case "synth" -> synth(ctx, x, y, w, h, t);
            case "candy" -> candy(ctx, x, y, w, h, t);
            case "midnight" -> midnight(ctx, x, y, w, h, t);
            case "cards" -> cards(ctx, x, y, w, h, t);
            case "solid" -> solid(ctx, x, y, w, h, rgb);
            default -> aurora(ctx, x, y, w, h, t);
        }
        ctx.disableScissor();
    }

    /** The original phone wallpaper (until 1.0 the only one). */
    private static void aurora(GuiGraphics ctx, int x, int y, int w, int h, double t) {
        ctx.fillGradient(x, y, x + w, y + h, 0xFF2B2A8A, 0xFF0B0A26);
        int[] cols = {0xFF7A4FE8, 0xFF2EA8E8, 0xFFE84FA8, 0xFF4FE8B0};
        for (int i = 0; i < 4; i++) {
            int gx = x + (int) (w * (0.15 + i * 0.25) + Math.sin(t * 0.27 + i * 2.1) * w * 0.12);
            int gy = y + (int) (h * (0.22 + 0.18 * i) + Math.cos(t * 0.21 + i * 1.3) * h * 0.06);
            for (int r = Math.min(w, h) / 3; r > 0; r -= 7) StylePainter.circle(ctx, gx, gy, r, withAlpha(cols[i], 0.03f));
        }
        stars(ctx, x, y, w, h, t, 26, 0.55f);
        int cx = x + w / 2, cy = y + h - 70, r = Math.min(w, h) / 4;
        StylePainter.circle(ctx, cx, cy, r, 0x0CFFFFFF);
        ctx.fill(cx - r, cy - 1, cx + r, cy + 2, 0x10000000);
        StylePainter.circle(ctx, cx, cy, r / 4, 0x14FFFFFF);
    }

    private static void stars(GuiGraphics ctx, int x, int y, int w, int h, double t, int n, float strength) {
        for (int k = 0; k < n; k++) {
            int px = x + 6 + (int) ((k * 97L) % Math.max(1, w - 12)), py = y + 16 + (int) ((k * 61L) % Math.max(1, h - 30));
            float tw = 0.3f + 0.7f * (float) Math.max(0, Math.sin(t * 1.7 + k * 1.9));
            ctx.fill(px, py, px + 1, py + 1, withAlpha(0xFFFFFFFF, 0.25f + strength * tw));
        }
    }

    /** Filled triangle-ish mountain: one column at a time. */
    private static void mountain(GuiGraphics ctx, int x, int w, int baseY, int peakX, int peakH, int halfW, int color) {
        for (int c = Math.max(x, peakX - halfW); c < Math.min(x + w, peakX + halfW); c++) {
            int hh = peakH - Math.abs(c - peakX) * peakH / Math.max(1, halfW);
            if (hh > 0) ctx.fill(c, baseY - hh, c + 1, baseY, color);
        }
    }

    private static void sunset(GuiGraphics ctx, int x, int y, int w, int h, double t) {
        ctx.fillGradient(x, y, x + w, y + h * 2 / 3, 0xFF3A1C6B, 0xFFFF8A4C);
        ctx.fillGradient(x, y + h * 2 / 3, x + w, y + h, 0xFF5A2A4A, 0xFF1A0E22);
        int cx = x + w / 2, cy = y + h * 2 / 3 - 6, r = Math.min(w, h) / 4;
        for (int k = 4; k >= 1; k--) StylePainter.circle(ctx, cx, cy, r + k * 5, withAlpha(0xFFFFC46B, 0.05f));
        StylePainter.circle(ctx, cx, cy, r, 0xFFFFC46B);
        for (int i = 0; i < 5; i++) { // sun stripes
            int sy = cy + r / 6 + i * (r / 5 + 1);
            ctx.fill(cx - r, sy, cx + r, sy + 1 + i / 2, 0xFFFF8A4C);
        }
        int base = y + h * 2 / 3 + 4;
        mountain(ctx, x, w, base, x + w / 5, h / 6, w / 3, 0xFF2A1638);
        mountain(ctx, x, w, base, x + w * 4 / 5, h / 5, w / 3, 0xFF22122E);
        mountain(ctx, x, w, base, x + w / 2, h / 9, w / 4, 0xFF331A40);
        ctx.fill(x, base, x + w, y + h, 0xFF1A0E22);
        for (int i = 0; i < 6; i++) { // reflections on the water
            int ry = base + 6 + i * 9, rw = Math.max(4, r - i * 6);
            int off = (int) Math.round(Math.sin(t * 1.3 + i) * 3);
            ctx.fill(cx - rw + off, ry, cx + rw + off, ry + 1, withAlpha(0xFFFFC46B, 0.5f - i * 0.07f));
        }
    }

    private static void ocean(GuiGraphics ctx, int x, int y, int w, int h, double t) {
        ctx.fillGradient(x, y, x + w, y + h, 0xFF0D4C7A, 0xFF041526);
        for (int i = 0; i < 8; i++) { // light rays
            int rx = x + (int) (w * (i / 8.0) + Math.sin(t * 0.3 + i) * 6);
            ctx.fillGradient(rx, y, rx + 3, y + h / 2, 0x14FFFFFF, 0x00FFFFFF);
        }
        for (int row = 0; row < 7; row++) { // waves
            int wy = y + h / 4 + row * (h / 9);
            int col = withAlpha(0xFF6FD3FF, 0.18f - row * 0.015f);
            for (int c = x; c < x + w; c += 2) {
                int dy = (int) Math.round(Math.sin(c * 0.06 + t * (0.8 + row * 0.1) + row) * 3);
                ctx.fill(c, wy + dy, c + 2, wy + dy + 1, col);
            }
        }
        for (int k = 0; k < 14; k++) { // rising bubbles
            double p = (t * (0.05 + (k % 5) * 0.012) + k * 0.137) % 1.0;
            int bx = x + 8 + (int) ((k * 53L) % Math.max(1, w - 16)) + (int) Math.round(Math.sin(t + k) * 2);
            int by = y + h - (int) (p * h);
            StylePainter.circle(ctx, bx, by, 1 + k % 3, withAlpha(0xFFBFEFFF, 0.35f));
        }
    }

    private static void forest(GuiGraphics ctx, int x, int y, int w, int h, double t) {
        ctx.fillGradient(x, y, x + w, y + h, 0xFF1F4A3A, 0xFF07150F);
        StylePainter.circle(ctx, x + w * 3 / 4, y + h / 5, Math.min(w, h) / 9, 0x55FFF4C8);
        int[] layer = {0xFF173A2C, 0xFF102A20, 0xFF0A1C15};
        for (int l = 0; l < 3; l++) {
            int base = y + h * (5 + l) / 8 + l * 10, n = 6 + l * 2, tw = w / n + 6;
            for (int i = 0; i <= n; i++) {
                int tx = x + i * w / n + (l * 13) % 17 - 6, th = h / 5 + ((i * 37 + l * 11) % 23);
                for (int k = 0; k < th; k++) { // pine: wider towards the bottom
                    int half = Math.max(1, k * tw / 2 / Math.max(1, th));
                    ctx.fill(tx - half, base - th + k, tx + half, base - th + k + 1, layer[l]);
                }
                ctx.fill(tx - 1, base, tx + 1, base + 6, layer[l]);
            }
            ctx.fill(x, base + 6, x + w, y + h, layer[l]);
        }
        for (int k = 0; k < 18; k++) { // fireflies
            double a = t * 0.4 + k * 2.3;
            int fx = x + (int) ((k * 71L) % Math.max(1, w)) + (int) Math.round(Math.sin(a) * 6);
            int fy = y + h / 2 + (int) ((k * 43L) % Math.max(1, h / 2)) + (int) Math.round(Math.cos(a * 1.3) * 4);
            float glow = (float) Math.max(0, Math.sin(t * 2 + k));
            ctx.fill(fx, fy, fx + 1, fy + 1, withAlpha(0xFFE8FF8A, 0.2f + 0.7f * glow));
        }
    }

    private static void synth(GuiGraphics ctx, int x, int y, int w, int h, double t) {
        int horizon = y + h * 11 / 20;
        ctx.fillGradient(x, y, x + w, horizon, 0xFF12052A, 0xFF6A1B6A);
        ctx.fillGradient(x, horizon, x + w, y + h, 0xFF1A0630, 0xFF05010C);
        int cx = x + w / 2, r = Math.min(w, h) / 4;
        StylePainter.circle(ctx, cx, horizon - r / 3, r, 0xFFFF5FA2);
        for (int i = 0; i < 6; i++) ctx.fill(cx - r, horizon - r / 3 + i * 5, cx + r, horizon - r / 3 + i * 5 + 2, 0xFF6A1B6A);
        ctx.fill(x, horizon, x + w, y + h, 0xFF14052A);
        int grid = 0xAAFF4FD8;
        double scroll = (t * 0.6) % 1.0;
        for (int i = 0; i < 10; i++) { // horizontal lines, closer near the horizon
            double f = (i + scroll) / 10.0;
            int ly = horizon + (int) (Math.pow(f, 2.2) * (y + h - horizon));
            ctx.fill(x, ly, x + w, ly + 1, grid);
        }
        for (int i = -8; i <= 8; i++) { // converging vertical lines
            for (int ly = horizon; ly < y + h; ly += 2) {
                double k = (ly - horizon) / (double) Math.max(1, y + h - horizon);
                int lx = cx + (int) Math.round(i * (w / 10.0) * (0.15 + k * 1.6));
                ctx.fill(lx, ly, lx + 1, ly + 2, grid);
            }
        }
        stars(ctx, x, y, w, horizon - y - 10, t, 14, 0.5f);
    }

    private static void candy(GuiGraphics ctx, int x, int y, int w, int h, double t) {
        int[] cols = {0xFFFFB3C7, 0xFFFFE3A3, 0xFFB5F0D0, 0xFFB8D8FF, 0xFFE2C4FF};
        int band = 14, off = (int) ((t * 8) % (band * cols.length));
        for (int r = 0; r < h; r += 2) { // diagonal stripes: one 2-pixel row at a time, one fill per stripe
            int c = 0;
            while (c < w) {
                int u = c + r + off;
                int idx = Math.floorMod(Math.floorDiv(u, band), cols.length);
                int end = Math.min(w, c + band - Math.floorMod(u, band));
                ctx.fill(x + c, y + r, x + end, Math.min(y + h, y + r + 2), cols[idx]);
                c = end;
            }
        }
        ctx.fillGradient(x, y, x + w, y + h, 0x22FFFFFF, 0x33000000);
    }

    private static void midnight(GuiGraphics ctx, int x, int y, int w, int h, double t) {
        ctx.fillGradient(x, y, x + w, y + h, 0xFF0A1030, 0xFF000208);
        stars(ctx, x, y, w, h, t, 60, 0.7f);
        int mx = x + w / 4, my = y + h / 5, r = Math.min(w, h) / 8;
        for (int k = 3; k >= 1; k--) StylePainter.circle(ctx, mx, my, r + k * 4, withAlpha(0xFFE8F0FF, 0.04f));
        StylePainter.circle(ctx, mx, my, r, 0xFFE8F0FF);
        StylePainter.circle(ctx, mx + r / 2, my - r / 4, r, 0xFF0A1030); // crescent
        double p = (t * 0.25) % 3.0; // a shooting star every 12 s
        if (p < 1) {
            int sx = x + (int) (w * (0.9 - p * 0.7)), sy = y + (int) (h * (0.1 + p * 0.3));
            for (int k = 0; k < 14; k++) ctx.fill(sx + k, sy - k / 2, sx + k + 1, sy - k / 2 + 1, withAlpha(0xFFFFFFFF, 0.9f - k * 0.06f));
        }
        ctx.fillGradient(x, y + h * 3 / 4, x + w, y + h, 0x00000000, 0x66000000);
    }

    /** Tilted rows of generic card backs (our own simple diamond pattern). */
    private static void cards(GuiGraphics ctx, int x, int y, int w, int h, double t) {
        ctx.fillGradient(x, y, x + w, y + h, 0xFF2A0E14, 0xFF12060A);
        int cw = 18, ch = 25, gap = 6;
        int drift = (int) ((t * 6) % (ch + gap));
        for (int row = -1; row * (ch + gap) < h + ch; row++) {
            for (int col = -1; col * (cw + gap) < w + cw; col++) {
                int cx = x + col * (cw + gap) + (row % 2 == 0 ? 0 : (cw + gap) / 2);
                int cy = y + row * (ch + gap) + drift - (ch + gap);
                Ui.round(ctx, cx, cy, cw, ch, 0x55B3262E);
                Ui.outline(ctx, cx, cy, cw, ch, 0x66FFD34D);
                int mx = cx + cw / 2, my = cy + ch / 2;
                for (int k = 0; k < 5; k++) ctx.fill(mx - (4 - k), my - k, mx + (4 - k) + 1, my - k + 1, 0x44FFD34D); // diamond
                for (int k = 1; k < 5; k++) ctx.fill(mx - (4 - k), my + k, mx + (4 - k) + 1, my + k + 1, 0x44FFD34D);
            }
        }
        ctx.fillGradient(x, y, x + w, y + h, 0x30000000, 0x90000000);
    }

    private static void solid(GuiGraphics ctx, int x, int y, int w, int h, int rgb) {
        int c = 0xFF000000 | rgb;
        ctx.fillGradient(x, y, x + w, y + h, Ui.brighten(c, 18), Ui.darken(c, 40));
    }

    // ------------------------------------------------------------------ Settings → Wallpaper panel

    /**
     * The picker shown in Settings: thumbnails of every wallpaper (click = choose), the solid color swatches, a hue bar
     * and a brightness bar, and a preview of the chosen one on the right.
     */
    public static void renderPicker(GuiGraphics ctx, int x, int y, int w, int h, int mouseX, int mouseY, double t, UpgradesPanel.Sink sink) {
        var tr = Minecraft.getInstance().font;
        int prevW = Math.min(110, Math.max(70, w / 4));
        int gw = w - prevW - 10;
        int cols = Math.max(3, Math.min(KEYS.length, gw / 52)), tw = (gw - (cols - 1) * 6) / cols, th = Math.round(tw * 1.25f);
        int rowsN = (KEYS.length + cols - 1) / cols;
        int maxTh = Math.max(28, (h - 70) / rowsN - 14);
        if (th > maxTh) { th = maxTh; }
        for (int i = 0; i < KEYS.length; i++) {
            int cx = x + (i % cols) * (tw + 6), cy = y + (i / cols) * (th + 14);
            boolean sel = i == INSTANCE.wallpaper, hover = Ui.in(mouseX, mouseY, cx, cy, tw, th);
            draw(ctx, cx, cy, tw, th, t, i, INSTANCE.color);
            Ui.outline(ctx, cx, cy, tw, th, sel ? Ui.GOLD : hover ? 0xFFFFFFFF : 0x55FFFFFF);
            if (sel) Ui.outline(ctx, cx - 1, cy - 1, tw + 2, th + 2, Ui.GOLD);
            Ui.text(ctx, name(i), cx, cy + th + 1, tw, 11, sel ? Ui.GOLD : 0xFFD8DCF0, false);
            final int idx = i;
            sink.add(cx, cy, tw, th + 12, () -> select(idx));
        }
        // solid color picker
        int py = y + rowsN * (th + 14) + 2;
        ctx.drawString(tr, Component.translatable("tcgshop.wallpaper.color").withStyle(ChatFormatting.BOLD), x, py, 0xFFDDE3F0, false);
        py += 11;
        int sw = Math.max(8, Math.min(14, (gw - (SWATCHES.length - 1) * 2) / SWATCHES.length));
        for (int i = 0; i < SWATCHES.length; i++) {
            int sx = x + i * (sw + 2);
            int c = 0xFF000000 | SWATCHES[i];
            boolean sel = INSTANCE.wallpaper == SOLID && INSTANCE.color == SWATCHES[i];
            ctx.fill(sx, py, sx + sw, py + sw, c);
            Ui.outline(ctx, sx - 1, py - 1, sw + 2, sw + 2, sel ? Ui.GOLD : Ui.in(mouseX, mouseY, sx, py, sw, sw) ? 0xFFFFFFFF : 0x55000000);
            final int rgb = SWATCHES[i];
            sink.add(sx, py, sw, sw, () -> setColor(rgb));
        }
        py += sw + 6;
        int barW = Math.min(gw, 200), segs = 48;
        // hue bar
        ctx.drawString(tr, Component.translatable("tcgshop.wallpaper.hue"), x, py + 1, 0xFF9A9FB8, false);
        int bx = x + 52, bw = Math.max(48, barW - 52);
        for (int s = 0; s < segs; s++) {
            int sx0 = bx + s * bw / segs, sx1 = bx + (s + 1) * bw / segs;
            float hue = (s + 0.5f) / segs;
            ctx.fill(sx0, py, sx1, py + 9, 0xFF000000 | Mth.hsvToRgb(hue, 0.78f, 1f));
            sink.add(sx0, py - 1, Math.max(1, sx1 - sx0), 11, () -> setHsv(hue, INSTANCE.value));
        }
        int hx = bx + Math.round(INSTANCE.hue * bw);
        ctx.fill(hx - 1, py - 2, hx + 1, py + 11, 0xFFFFFFFF);
        py += 14;
        // brightness bar
        ctx.drawString(tr, Component.translatable("tcgshop.wallpaper.brightness"), x, py + 1, 0xFF9A9FB8, false);
        for (int s = 0; s < segs; s++) {
            int sx0 = bx + s * bw / segs, sx1 = bx + (s + 1) * bw / segs;
            float v = (s + 0.5f) / segs;
            ctx.fill(sx0, py, sx1, py + 9, 0xFF000000 | Mth.hsvToRgb(INSTANCE.hue, 0.78f, v));
            sink.add(sx0, py - 1, Math.max(1, sx1 - sx0), 11, () -> setHsv(INSTANCE.hue, v));
        }
        int vx = bx + Math.round(INSTANCE.value * bw);
        ctx.fill(vx - 1, py - 2, vx + 1, py + 11, 0xFFFFFFFF);
        // preview: a little phone with the chosen wallpaper
        int pvx = x + w - prevW, pvh = Math.min(h, Math.round(prevW * 1.8f)), pvy = y;
        Ui.rounded(ctx, pvx - 3, pvy - 3, prevW + 6, pvh + 6, 10, 0xFF0A0B0E);
        draw(ctx, pvx, pvy, prevW, pvh, t);
        Ui.cornerMask(ctx, pvx, pvy, prevW, pvh, 8, 0xFF0A0B0E);
        long tod = Minecraft.getInstance().level != null ? (Minecraft.getInstance().level.getDayTime() + 6000) % 24000 : 0;
        String clock = String.format(java.util.Locale.ROOT, "%02d:%02d", tod / 1000, (tod % 1000) * 60 / 1000);
        dev.tcgshop.client.screen.Draw.scaledCentered(ctx, Component.literal(clock).withStyle(ChatFormatting.BOLD), pvx + prevW / 2f, pvy + 16, 2f, 0xFFFFFFFF);
        Ui.text(ctx, name(INSTANCE.wallpaper), pvx, pvy + pvh - 16, prevW, 10, 0xFFFFFFFF, true);
    }
}

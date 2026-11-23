package dev.tcgshop.casino.client;

import dev.tcgshop.TcgSounds;
import dev.tcgshop.casino.ScratchMath;
import dev.tcgshop.client.binder.Ui;
import dev.tcgshop.client.screen.Draw;
import dev.tcgshop.pricing.PriceService;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * Scratch ticket. The prize was already rolled and paid by the server when the ticket was used; scratching only
 * uncovers it. Drag the mouse over the foil (a cell uncovers itself at 60%) or press "Reveal all".
 */
public class ScratchScreen extends Screen {
    private static final int FX = 14, FY = 9;
    private final ScratchMath.Tier tier;
    private final int[] grid;
    private final int mult;
    private final long face, prize, balance;
    private final boolean[][] foil = new boolean[9][FX * FY];
    private final boolean[] revealed = new boolean[9];
    private double lastSound, doneAt = -1;
    private int gx, gy, cw, chh;
    private int closeX, closeY, closeW, revealX;

    public ScratchScreen(NbtCompound d) {
        super(Text.translatable("tcgshop.casino.scratch." + ScratchMath.Tier.of(d.getInt("Tier")).key));
        this.tier = ScratchMath.Tier.of(d.getInt("Tier"));
        int[] g = d.getIntArray("Grid");
        this.grid = g.length == 9 ? g : new int[9];
        this.mult = d.getInt("Mult");
        this.face = d.getLong("Face");
        this.prize = d.getLong("Prize");
        this.balance = d.getLong("Balance");
        for (boolean[] f : foil) java.util.Arrays.fill(f, true);
    }

    @Override
    public boolean shouldPause() { return false; }

    private boolean allRevealed() {
        for (boolean r : revealed) if (!r) return false;
        return true;
    }

    private int[] colors() {
        return switch (tier) {
            case BRONZE -> new int[]{0xFFCD7F32, 0xFF7A4A1A};
            case SILVER -> new int[]{0xFFB8C4CF, 0xFF5A6670};
            case GOLD -> new int[]{0xFFFFD34D, 0xFF9A6A00};
        };
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        double t = System.nanoTime() / 1e9;
        ctx.fillGradient(0, 0, width, height, 0xB0100A06, 0xD0050302);
        int w = 250, h = 236, x = (width - w) / 2, y = (height - h) / 2;
        int[] c = colors();
        Ui.rounded(ctx, x + 4, y + 6, w, h, 10, 0x66000000);
        Ui.roundedGradient(ctx, x, y, w, h, 10, c[0], c[1]);
        Ui.roundedOutline(ctx, x, y, w, h, 10, 0xFF1A1A1A);
        // ticket pattern
        for (int i = 0; i < 12; i++) {
            float sx = x + 10 + (i * 37) % (w - 20), sy = y + 8 + (i * 53) % 30;
            Draw.scaledCentered(ctx, Text.literal("★"), sx, sy, 0.8f, 0x40FFFFFF);
        }
        Draw.scaledCentered(ctx, title.copy().formatted(Formatting.BOLD), x + w / 2f, y + 10, 1.5f, 0xFFFFFFFF);
        Draw.scaledCentered(ctx, Text.translatable("tcgshop.casino.scratch.rule"), x + w / 2f, y + 28, 0.8f, 0xFF1A1A1A);
        Draw.scaledCentered(ctx, Text.translatable("tcgshop.casino.scratch.top_prize", PriceService.format(face * tier.top())), x + w / 2f, y + 38, 0.75f, 0xFF3A1A00);

        cw = 70;
        chh = 42;
        gx = x + (w - 3 * cw - 8) / 2;
        gy = y + 50;
        List<Integer> wins = allRevealed() ? ScratchMath.winningCells(grid, mult) : List.of();
        for (int i = 0; i < 9; i++) {
            int cx = gx + (i % 3) * (cw + 4), cy = gy + (i / 3) * (chh + 4);
            boolean win = wins.contains(i);
            Ui.rounded(ctx, cx, cy, cw, chh, 4, win && ((int) (t * 4)) % 2 == 0 ? 0xFFFFF59D : 0xFFFFFDF5);
            Ui.roundedOutline(ctx, cx, cy, cw, chh, 4, win ? 0xFFE53935 : 0xFF6A6A6A);
            Text amount = Text.literal(PriceService.format(face * grid[i])).formatted(Formatting.BOLD);
            Ui.text(ctx, amount, cx + 2, cy, cw - 4, chh, win ? 0xFFC62828 : 0xFF202020, false);
            if (!revealed[i]) {
                int fw = cw / FX + 1, fh = chh / FY + 1;
                for (int fy = 0; fy < FY; fy++)
                    for (int fx = 0; fx < FX; fx++) {
                        if (!foil[i][fy * FX + fx]) continue;
                        int px = cx + fx * cw / FX, py = cy + fy * chh / FY;
                        int shade = ((fx * 7 + fy * 13) % 5) * 6;
                        boolean glint = ((int) (t * 3 + fx * 0.3 - fy * 0.2)) % 9 == 0;
                        ctx.fill(px, py, Math.min(cx + cw, px + fw), Math.min(cy + chh, py + fh), glint ? 0xFFF5F5F5 : Ui.brighten(0xFF9EA4AA, shade));
                    }
                Ui.text(ctx, Text.literal("?"), cx, cy, cw, chh, 0x60FFFFFF, false);
            }
        }

        // result
        int ry = gy + 3 * (chh + 4) + 4;
        if (allRevealed()) {
            if (doneAt < 0) {
                doneAt = t;
                sound(prize > 0 ? (mult >= 100 ? TcgSounds.CARD_LEGENDARY : TcgSounds.UI_COINS) : SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), 1f, 0.8f);
            }
            Text res = prize > 0 ? Text.translatable("tcgshop.casino.win", PriceService.format(prize)).formatted(Formatting.DARK_GREEN, Formatting.BOLD)
                    : Text.translatable("tcgshop.casino.scratch.no_luck").formatted(Formatting.DARK_RED, Formatting.BOLD);
            Draw.scaledCentered(ctx, res, x + w / 2f, ry + 2, 1.2f, 0xFFFFFFFF);
        } else Draw.scaledCentered(ctx, Text.translatable("tcgshop.casino.scratch.hint"), x + w / 2f, ry + 2, 0.8f, 0xFF1A1A1A);

        int by = y + h - 24;
        revealX = x + 12;
        closeW = 100;
        closeX = x + w - 12 - closeW;
        closeY = by;
        Ui.button(ctx, revealX, by, 100, 18, Text.translatable("tcgshop.casino.scratch.reveal_all"), mx, my, !allRevealed(), 0xFF555A70);
        Ui.button(ctx, closeX, by, closeW, 18, Text.translatable("tcgshop.casino.close"), mx, my, true, 0xFF3A6EA5);
        Draw.scaledCentered(ctx, Text.translatable("tcgshop.casino.balance", PriceService.format(balance)), x + w / 2f, by - 9, 0.7f, 0xFF1A1A1A);
    }

    private void scratchAt(double mx, double my) {
        boolean any = false;
        for (int i = 0; i < 9; i++) {
            if (revealed[i]) continue;
            int cx = gx + (i % 3) * (cw + 4), cy = gy + (i / 3) * (chh + 4);
            if (mx < cx - 6 || mx > cx + cw + 6 || my < cy - 6 || my > cy + chh + 6) continue;
            int left = 0;
            for (int fy = 0; fy < FY; fy++)
                for (int fx = 0; fx < FX; fx++) {
                    int k = fy * FX + fx;
                    double px = cx + (fx + 0.5) * cw / FX, py = cy + (fy + 0.5) * chh / FY;
                    if (foil[i][k] && Math.hypot(px - mx, py - my) < 7) {
                        foil[i][k] = false;
                        any = true;
                    }
                    if (foil[i][k]) left++;
                }
            if (left < FX * FY * 0.4) revealed[i] = true;
        }
        double t = System.nanoTime() / 1e9;
        if (any && t - lastSound > 0.09) {
            lastSound = t;
            sound(SoundEvents.ITEM_BRUSH_BRUSHING_GENERIC, 1.6f, 0.35f);
        }
    }

    private void sound(SoundEvent s, float pitch, float volume) {
        if (client != null) client.getSoundManager().play(PositionedSoundInstance.master(s, pitch, volume));
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            if (Ui.in(mx, my, closeX, closeY, closeW, 18)) {
                close();
                return true;
            }
            if (Ui.in(mx, my, revealX, closeY, 100, 18)) {
                java.util.Arrays.fill(revealed, true);
                return true;
            }
            scratchAt(mx, my);
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (button == 0) {
            int steps = (int) Math.ceil(Math.hypot(dx, dy) / 3);
            for (int s = 0; s <= steps; s++) scratchAt(mx - dx * (1 - s / (double) Math.max(1, steps)), my - dy * (1 - s / (double) Math.max(1, steps)));
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
    }
}

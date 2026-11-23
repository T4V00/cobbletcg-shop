package dev.tcgshop.casino.client;

import dev.tcgshop.TcgSounds;
import dev.tcgshop.casino.CasinoGame;
import dev.tcgshop.casino.WheelMath;
import dev.tcgshop.client.binder.Ui;
import dev.tcgshop.client.screen.Draw;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

/** Fortune wheel (Big Six): pick a symbol, spin, the clapper ticks over the pegs until it stops. */
public class WheelScreen extends CasinoScreen {
    public static final int[] COLORS = {0xFF3A7BD5, 0xFF43A047, 0xFFF4511E, 0xFF8E24AA, 0xFFFDD835, 0xFF212121, 0xFFE53935};
    private static final double SPIN_TIME = 5.0;
    private int symbol;
    private int seg = -1, lastPeg = -1;
    private long payout, staked;
    private double spinStart = -10, angleFrom, angleNow;
    private boolean celebrated = true;

    public WheelScreen(BlockPos pos, NbtCompound d) {
        super(CasinoGame.WHEEL, pos, d, "block.tcgshop.fortune_wheel");
    }

    public static Text label(int sym) {
        return sym >= 5 ? Text.translatable("tcgshop.casino.wheel." + WheelMath.NAMES[sym]) : Text.literal(WheelMath.NAMES[sym]);
    }

    private boolean spinning() {
        return now() - spinStart < SPIN_TIME;
    }

    @Override
    public void onUpdate(NbtCompound d) {
        answered(d);
        seg = d.getInt("Seg");
        payout = d.getLong("Payout");
        staked = d.getLong("Bet");
        spinStart = now();
        angleFrom = angleNow;
        celebrated = false;
    }

    private void spin() {
        if (spinning() || waiting() || !economy || bet > balance) return;
        NbtCompound d = new NbtCompound();
        d.putInt("Sym", symbol);
        d.putLong("Bet", bet);
        act(d);
    }

    /** Wheel angle (radians, clockwise): segment `seg` ends under the pointer at the top. */
    private double angle(double t) {
        if (seg < 0) return angleNow;
        double step = Math.PI * 2 / WheelMath.SEGMENTS;
        double target = -seg * step;
        double base = angleFrom;
        double dist = ((target - base) % (Math.PI * 2) + Math.PI * 4) % (Math.PI * 2) + Math.PI * 2 * 4;
        double p = MathHelper.clamp((t - spinStart) / SPIN_TIME, 0, 1);
        return base + dist * (1 - Math.pow(1 - p, 3));
    }

    @Override
    protected void draw(DrawContext ctx, int mx, int my, float delta) {
        double t = now();
        int w = Math.min(width - 16, 420), h = Math.min(height - 16, 260), x = (width - w) / 2, y = (height - h) / 2;
        int top = frame(ctx, x, y, w, h, 0xFF3B1E5A, 0xFF1A0C2A);
        float R = Math.min(85, (h - 80) / 2f), cx = x + 16 + R, cy = top + 8 + R;
        angleNow = angle(t);
        int[] cols = new int[WheelMath.SEGMENTS];
        for (int i = 0; i < cols.length; i++) cols[i] = COLORS[WheelMath.LAYOUT[i]];
        CasinoDraw.disc(ctx, cx, cy + 3, R + 8, 0x80000000);
        CasinoDraw.disc(ctx, cx, cy, R + 7, 0xFFB8860B);
        CasinoDraw.disc(ctx, cx, cy, R + 4, 0xFF5D3A0A);
        CasinoDraw.ring(ctx, cx, cy, R * 0.3f, R, angleNow, cols);
        double step = Math.PI * 2 / WheelMath.SEGMENTS;
        for (int i = 0; i < WheelMath.SEGMENTS; i++) {
            int s = WheelMath.LAYOUT[i];
            CasinoDraw.radialText(ctx, s >= 5 ? Text.literal(s == 5 ? "J" : "★") : Text.literal(WheelMath.NAMES[s]), cx, cy, R * 0.82f,
                    angleNow + i * step, R / 130f, s == 4 ? 0xFF000000 : 0xFFFFFFFF);
            double a = angleNow + (i + 0.5) * step;
            CasinoDraw.disc(ctx, cx + (float) Math.sin(a) * (R + 2), cy - (float) Math.cos(a) * (R + 2), 1.5f, 0xFFE0E0E0);
        }
        CasinoDraw.disc(ctx, cx, cy, R * 0.3f, 0xFF2A1640);
        Draw.scaledCentered(ctx, Text.literal("★").formatted(Formatting.BOLD), cx, cy - 4, R / 30f, 0xFFFFD34D);
        // clapper at the top: ticks on every peg
        int peg = (int) Math.floor(angleNow / step + 0.5);
        if (spinning() && peg != lastPeg) {
            lastPeg = peg;
            sound(SoundEvents.BLOCK_WOODEN_BUTTON_CLICK_ON, 1.6f, 0.25f);
        }
        float wob = spinning() ? (float) Math.sin(t * 40) * 1.5f : 0;
        CasinoDraw.pointer(ctx, cx + wob, cy - R + 8, 12);

        // ---- result
        if (seg >= 0 && !spinning()) {
            int s = WheelMath.LAYOUT[seg];
            Text msg = payout > 0 ? Text.translatable("tcgshop.casino.win", fmt(payout)).formatted(Formatting.GREEN, Formatting.BOLD)
                    : Text.translatable("tcgshop.casino.wheel.landed", label(s)).formatted(Formatting.RED);
            Draw.scaledCentered(ctx, msg, cx, cy + R + 12, 1f, 0xFFFFFFFF);
            if (!celebrated) {
                celebrated = true;
                sound(payout > 0 ? (s >= 5 ? TcgSounds.CARD_LEGENDARY : TcgSounds.UI_COINS) : SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), 1f, 0.8f);
            }
        }

        // ---- bet board
        int bx = (int) (cx + R + 20), bw = x + w - 10 - bx, row = top + 6;
        ctx.drawTextWithShadow(textRenderer, Text.translatable("tcgshop.casino.wheel.pick").formatted(Formatting.BOLD), bx, row, Ui.GOLD);
        row += 12;
        boolean can = !spinning() && !waiting();
        for (int s = 0; s < WheelMath.SYMBOLS; s++) {
            int rh = 17;
            boolean sel = s == symbol, hover = can && Ui.in(mx, my, bx, row, bw, rh - 2);
            Ui.rounded(ctx, bx, row, bw, rh - 2, 4, sel ? Ui.brighten(COLORS[s], 30) : hover ? 0xFF3A3050 : 0xFF241A34);
            if (sel) Ui.roundedOutline(ctx, bx, row, bw, rh - 2, 4, 0xFFFFFFFF);
            Ui.rounded(ctx, bx + 3, row + 3, 9, 9, 2, COLORS[s]);
            ctx.drawTextWithShadow(textRenderer, label(s), bx + 16, row + 4, 0xFFFFFFFF);
            Text pays = Text.translatable("tcgshop.casino.wheel.pays", WheelMath.PAYS[s], WheelMath.COUNTS[s]);
            ctx.drawTextWithShadow(textRenderer, pays, bx + bw - 4 - textRenderer.getWidth(pays), row + 4, 0xFFD0C8E0);
            final int sym = s;
            if (can) areas.add(new Area(bx, row, bw, rh - 2, () -> symbol = sym));
            row += rh;
        }
        Draw.scaledCentered(ctx, Text.translatable("tcgshop.casino.wheel.rtp", String.format(java.util.Locale.ROOT, "%.1f", WheelMath.rtp(symbol) * 100)),
                bx + bw / 2f, row + 2, 0.75f, 0xFFB0A8C0);

        int by = y + h - 34;
        boolean canSpin = can && economy && bet <= balance;
        betBar(ctx, x + 10, by, w - 20 - 92, mx, my, can);
        Ui.button(ctx, x + w - 98, by - 2, 88, 22, Text.translatable("tcgshop.casino.spin").formatted(Formatting.BOLD), mx, my, canSpin, 0xFF8E24AA);
        if (canSpin) areas.add(new Area(x + w - 98, by - 2, 88, 22, this::spin));
        Draw.scaledCentered(ctx, Text.translatable("tcgshop.casino.limits", fmt(min), fmt(max)).formatted(Formatting.DARK_GRAY), x + w / 2f, y + h - 11, 0.75f, 0xFF8A8A9A);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_SPACE || keyCode == GLFW.GLFW_KEY_ENTER) {
            spin();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}

package dev.tcgshop.casino.client;

import dev.tcgshop.TcgSounds;
import dev.tcgshop.casino.CasinoGame;
import dev.tcgshop.casino.RouletteMath;
import dev.tcgshop.client.binder.Ui;
import dev.tcgshop.client.screen.Draw;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

/**
 * European roulette table: the wheel with its ball on the left, the betting layout on the right. Click a number
 * for a straight bet, a line between two numbers for a split, a corner for a corner bet, the bottom edge of a
 * column for a street (or between two columns for a six line), or any outside box. The server checks that every
 * chip is a real roulette bet and spins.
 */
public class RouletteTableScreen extends CasinoScreen {
    private static final double SPIN_TIME = 5.5;

    private record Chip(long mask, long amount, float ax, float ay) {}

    private final List<Chip> chips = new ArrayList<>();
    private List<Chip> lastChips = new ArrayList<>();
    private final List<Integer> history = new ArrayList<>();
    private final int maxChips;
    private long chipValue = 100;
    private int result = -1, lastPocket = -1;
    private long payout, staked;
    private long[] won = new long[0];
    private double spinStart = -10, ballFrom;
    private boolean settled, celebrated = true;
    // layout geometry (set every frame)
    private int gx, gy, cw, ch;

    public RouletteTableScreen(BlockPos pos, CompoundTag d) {
        super(CasinoGame.ROULETTE, pos, d, "block.tcgshop.roulette_table");
        this.maxChips = Math.max(1, d.getInt("MaxChips"));
        this.chipValue = clampBet(100);
    }

    private boolean spinning() {
        return now() - spinStart < SPIN_TIME;
    }

    private long total() {
        long s = 0;
        for (Chip c : chips) s += c.amount();
        return s;
    }

    @Override
    public void onUpdate(CompoundTag d) {
        answered(d);
        result = d.getInt("N");
        payout = d.getLong("Payout");
        staked = d.getLong("Bet");
        won = d.getLongArray("Won");
        spinStart = now();
        ballFrom = Math.random() * Math.PI * 2;
        settled = true;
        celebrated = false;
        lastPocket = -1;
    }

    private void spin() {
        if (chips.isEmpty() || spinning() || waiting() || !economy || total() > balance) return;
        ListTag list = new ListTag();
        for (Chip c : chips) {
            CompoundTag b = new CompoundTag();
            b.putLong("M", c.mask());
            b.putLong("A", c.amount());
            list.add(b);
        }
        CompoundTag d = new CompoundTag();
        d.put("Bets", list);
        lastChips = new ArrayList<>(chips);
        act(d);
        sound(SoundEvents.NOTE_BLOCK_HAT.value(), 1.2f, 0.5f);
    }

    private void place(long mask, float ax, float ay) {
        if (spinning() || waiting()) return;
        if (settled) { // a new round: the previous chips go
            chips.clear();
            settled = false;
        }
        if (!RouletteMath.legal(mask)) return;
        long add = Math.min(chipValue, max - total());
        if (add < min) {
            sound(SoundEvents.VILLAGER_NO, 1f, 0.4f);
            return;
        }
        for (int i = 0; i < chips.size(); i++) {
            Chip c = chips.get(i);
            if (c.mask() == mask) {
                chips.set(i, new Chip(mask, c.amount() + add, c.ax(), c.ay()));
                sound(TcgSounds.CARD_SLIDE, 1.6f, 0.5f);
                return;
            }
        }
        if (chips.size() >= maxChips) return;
        chips.add(new Chip(mask, add, ax, ay));
        sound(TcgSounds.CARD_SLIDE, 1.6f, 0.5f);
    }

    // ------------------------------------------------------------------ layout

    /** Number in column c (0..11) and row r (0 = top: 3, 6, 9...). */
    private static int number(int c, int r) {
        return c * 3 + (3 - r);
    }

    /** Inside bet under the mouse (relative to the number grid), snapped to its anchor. */
    private void clickGrid(double lx, double ly) {
        int c = (int) Math.floor(lx / cw), r = (int) Math.floor(ly / ch);
        if (r == 3 && ly - 3 * ch < 4) r = 2; // just under the layout: still the bottom edge (streets, six lines)
        if (c < 0 || c > 11 || r < 0 || r > 2) return;
        double fx = lx - c * cw, fy = ly - r * ch;
        int e = 4;
        boolean left = fx < e, right = fx > cw - e, up = fy < e, down = fy > ch - e;
        int n = number(c, r);
        // edges are shared: normalize to "left" and "up" of a neighbour cell when possible
        if (right && c < 11) { c++; left = true; right = false; fx = 0; n = number(c, r); }
        if (down && r < 2) { r++; up = true; down = false; fy = 0; n = number(c, r); }
        if (down && r == 2) { // bottom edge of the layout: street, or six line on a column boundary
            if (left || right) {
                int c0 = left ? c - 1 : c;
                if (c0 >= 0 && c0 + 1 <= 11) {
                    int b = c0 * 3 + 1;
                    place(RouletteMath.mask(b, b + 1, b + 2, b + 3, b + 4, b + 5), (c0 + 1) * cw, 3 * ch);
                    return;
                }
                if (c0 < 0) { // left end: first four (0, 1, 2, 3)
                    place(RouletteMath.mask(0, 1, 2, 3), 0, 3 * ch);
                    return;
                }
            }
            int b = c * 3 + 1;
            place(RouletteMath.mask(b, b + 1, b + 2), c * cw + cw / 2f, 3 * ch);
            return;
        }
        if (left && up && r > 0) { // corner
            if (c == 0) place(RouletteMath.mask(0, n, n + 1), 0, r * ch); // trio with the zero
            else place(RouletteMath.mask(n, n + 1, n - 3, n - 2), c * cw, r * ch);
            return;
        }
        if (left) { // split with the left neighbour (or the zero)
            place(c == 0 ? RouletteMath.mask(0, n) : RouletteMath.mask(n, n - 3), c * cw, r * ch + ch / 2f);
            return;
        }
        if (up && r > 0) { // split with the number above
            place(RouletteMath.mask(n, n + 1), c * cw + cw / 2f, r * ch);
            return;
        }
        place(RouletteMath.mask(n), c * cw + cw / 2f, r * ch + ch / 2f);
    }

    private int colorOf(int n) {
        return switch (RouletteMath.color(n)) {
            case 0 -> 0xFF1E8C3A;
            case 1 -> 0xFFC62828;
            default -> 0xFF1B1B1F;
        };
    }

    @Override
    protected void draw(GuiGraphics ctx, int mx, int my, float delta) {
        double t = now();
        int w = Math.min(width - 12, 480), h = Math.min(height - 12, 270), x = (width - w) / 2, y = (height - h) / 2;
        int top = frame(ctx, x, y, w, h, 0xFF1F5A30, 0xFF0F2E18);

        // ---- wheel
        float R = Math.min(70, (h - 90) / 2f), cx = x + 14 + R, cy = top + 6 + R;
        double wheel = t * 0.5; // the wheel always turns slowly (clockwise)
        double step = Math.PI * 2 / 37;
        int idx = result < 0 ? 0 : RouletteMath.wheelIndex(result);
        CasinoDraw.disc(ctx, cx, cy + 2, R + 7, 0x80000000);
        CasinoDraw.disc(ctx, cx, cy, R + 6, 0xFF4E2A12);
        CasinoDraw.disc(ctx, cx, cy, R + 2, 0xFF2E1608);
        int[] cols = new int[37];
        for (int i = 0; i < 37; i++) cols[i] = colorOf(RouletteMath.WHEEL[i]);
        CasinoDraw.ring(ctx, cx, cy, R * 0.62f, R * 0.96f, wheel, cols);
        for (int i = 0; i < 37; i++)
            CasinoDraw.radialText(ctx, Component.literal(String.valueOf(RouletteMath.WHEEL[i])), cx, cy, R * 0.85f, wheel + i * step, R / 120f, 0xFFFFFFFF);
        CasinoDraw.disc(ctx, cx, cy, R * 0.62f, 0xFF6D3B17);
        CasinoDraw.disc(ctx, cx, cy, R * 0.45f, 0xFF8A5222);
        for (int k = 0; k < 4; k++) {
            double a = wheel + k * Math.PI / 2;
            CasinoDraw.line(ctx, cx, cy, cx + (float) Math.sin(a) * R * 0.5f, cy - (float) Math.cos(a) * R * 0.5f, 2.5f, 0xFFE0B040);
        }
        CasinoDraw.disc(ctx, cx, cy, R * 0.12f, 0xFFFFD34D);
        // ball
        if (result >= 0) {
            double p = Mth.clamp((t - spinStart) / SPIN_TIME, 0, 1);
            double pocket = wheel + idx * step;
            double ease = 1 - Math.pow(1 - p, 2.6);
            double ballA = pocket + (1 - ease) * (Math.PI * 2 * 7 + ballFrom); // counter-clockwise, slowing down
            float br = (float) (p < 0.7 ? R * 1.0 : R * (1.0 - (p - 0.7) / 0.3 * 0.2));
            if (p >= 1) br = R * 0.8f;
            CasinoDraw.disc(ctx, cx + (float) Math.sin(ballA) * br, cy - (float) Math.cos(ballA) * br, Math.max(2.5f, R / 22f), 0xFFF5F5F5);
            int pk = (int) Math.floor((ballA - wheel) / step + 0.5);
            if (p < 1 && p > 0.6 && pk != lastPocket) {
                lastPocket = pk;
                sound(SoundEvents.NOTE_BLOCK_HAT.value(), 2f, 0.12f);
            }
        }

        // ---- result + history under the wheel
        int hy = (int) (cy + R + 12);
        if (result >= 0 && !spinning()) {
            Component res = Component.literal(String.valueOf(result)).withStyle(ChatFormatting.BOLD);
            Ui.rounded(ctx, (int) cx - 16, hy, 32, 18, 5, colorOf(result));
            Ui.text(ctx, res, (int) cx - 16, hy, 32, 18, 0xFFFFFFFF, true);
            if (!celebrated) {
                celebrated = true;
                history.add(result);
                if (history.size() > 10) history.remove(0);
                sound(payout > 0 ? TcgSounds.UI_COINS : SoundEvents.NOTE_BLOCK_BASS.value(), payout > 0 ? 1f : 0.7f, 0.8f);
            }
        }
        int hx = x + 10;
        for (int n : history) {
            Ui.round(ctx, hx, y + h - 14, 13, 10, colorOf(n));
            Ui.text(ctx, Component.literal(String.valueOf(n)), hx, y + h - 14, 13, 10, 0xFFFFFFFF, false);
            hx += 14;
        }

        // ---- layout
        int lx = (int) (cx + R + 16), avail = x + w - 10 - lx;
        cw = Math.max(12, (avail - 18 - 24) / 12);
        ch = Math.max(14, Math.min(22, cw + 2));
        gx = lx + 18;
        gy = top + 8;
        boolean can = !spinning() && !waiting();
        boolean hoverGrid = can && mx >= gx && mx < gx + 12 * cw && my >= gy && my < gy + 3 * ch + 3;
        // zero
        drawCell(ctx, lx, gy, 18, 3 * ch, 0, mx, my);
        if (can) areas.add(new Area(lx, gy, 18, 3 * ch, () -> place(RouletteMath.mask(0), -9, 1.5f * ch)));
        for (int c = 0; c < 12; c++)
            for (int r = 0; r < 3; r++) drawCell(ctx, gx + c * cw, gy + r * ch, cw, ch, number(c, r), mx, my);
        if (can) areas.add(new Area(gx, gy, 12 * cw, 3 * ch + 3, () -> clickGrid(lastMx - gx, lastMy - gy)));
        // columns 2 to 1
        for (int r = 0; r < 3; r++) {
            int col = 2 - r; // top row = third column
            outside(ctx, gx + 12 * cw, gy + r * ch, 24, ch, Component.literal("2:1"), RouletteMath.COLUMNS[col], mx, my, can, 0xFF1F5A30);
        }
        // dozens
        String[] dz = {"1st 12", "2nd 12", "3rd 12"};
        for (int k = 0; k < 3; k++)
            outside(ctx, gx + k * 4 * cw, gy + 3 * ch, 4 * cw, 15, Component.literal(dz[k]), RouletteMath.DOZENS[k], mx, my, can, 0xFF1F5A30);
        // even money
        Component[] em = {Component.literal("1-18"), Component.translatable("tcgshop.casino.roulette.even"), Component.translatable("tcgshop.casino.roulette.red"),
                Component.translatable("tcgshop.casino.roulette.black"), Component.translatable("tcgshop.casino.roulette.odd"), Component.literal("19-36")};
        long[] emm = {RouletteMath.LOW_MASK, RouletteMath.EVEN_MASK, RouletteMath.RED_MASK, RouletteMath.BLACK_MASK, RouletteMath.ODD_MASK, RouletteMath.HIGH_MASK};
        int[] emc = {0xFF1F5A30, 0xFF1F5A30, 0xFFC62828, 0xFF1B1B1F, 0xFF1F5A30, 0xFF1F5A30};
        for (int k = 0; k < 6; k++)
            outside(ctx, gx + k * 2 * cw, gy + 3 * ch + 15, 2 * cw, 15, em[k], emm[k], mx, my, can, emc[k]);
        if (hoverGrid) hoverHint(ctx, mx, my);
        // chips on the layout
        for (Chip c : chips) {
            boolean win = false;
            for (long m : won) if (m == c.mask()) win = true;
            boolean pulse = settled && !spinning() && win && ((int) (t * 4)) % 2 == 0;
            CasinoDraw.chip(ctx, gx + c.ax(), gy + c.ay(), Math.min(7, cw / 2.4f), c.amount(), pulse);
        }

        // ---- controls
        int by = gy + 3 * ch + 36;
        ctx.drawString(font, Component.translatable("tcgshop.casino.chip"), lx, by + 5, 0xFFE0E0E0);
        int cxp = lx + font.width(Component.translatable("tcgshop.casino.chip")) + 6;
        for (long v : CHIPS) {
            if (v > max) break;
            CasinoDraw.chip(ctx, cxp + 9, by + 9, 9, v, chipValue == v);
            areas.add(new Area(cxp, by, 18, 18, () -> chipValue = v));
            cxp += 22;
        }
        int ry = by + 24;
        long tot = total();
        ctx.drawString(font, Component.translatable("tcgshop.casino.total_bet", fmt(tot)), lx, ry + 4, tot <= balance ? Ui.GOLD : 0xFFFF6060);
        int bx = x + w - 10;
        Component[] labels = {Component.translatable("tcgshop.casino.spin").withStyle(ChatFormatting.BOLD), Component.translatable("tcgshop.casino.rebet"),
                Component.translatable("tcgshop.casino.undo"), Component.translatable("tcgshop.casino.clear")};
        Runnable[] acts = {this::spin, this::rebet, () -> { if (!chips.isEmpty() && !settled) chips.remove(chips.size() - 1); }, () -> { chips.clear(); settled = false; }};
        int[] accents = {0xFFD39B1A, 0xFF3A6EA5, 0xFF555A70, 0xFF555A70};
        int by2 = ry + 16;
        for (int i = 0; i < labels.length; i++) {
            int bw = i == 0 ? 70 : 52;
            bx -= bw;
            boolean en = can && (i != 0 || (!chips.isEmpty() && !settled && economy && tot <= balance)) && (i != 1 || !lastChips.isEmpty());
            Ui.button(ctx, bx, by2, bw, 20, labels[i], mx, my, en, accents[i]);
            if (en) areas.add(new Area(bx, by2, bw, 20, acts[i]));
            bx -= 4;
        }
        // result message
        if (result >= 0 && !spinning()) {
            Component msg = payout > 0 ? Component.translatable("tcgshop.casino.win", fmt(payout)).withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
                    : Component.translatable("tcgshop.casino.lose", fmt(staked)).withStyle(ChatFormatting.RED);
            Draw.scaledCentered(ctx, msg, lx + (x + w - 10 - lx) / 2f, by2 + 26, 1.1f, 0xFFFFFFFF);
        }
        Draw.scaledCentered(ctx, Component.translatable("tcgshop.casino.roulette.rules", fmt(max)).withStyle(ChatFormatting.DARK_GRAY),
                x + w / 2f, y + h - 26, 0.7f, 0xFF9AB09A);
    }

    private double lastMx, lastMy;

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        lastMx = mouseX;
        lastMy = mouseY;
        if (button == 1) { // right click: remove the chip under the mouse
            for (int i = chips.size() - 1; i >= 0; i--) {
                Chip c = chips.get(i);
                if (!settled && Math.hypot(mouseX - gx - c.ax(), mouseY - gy - c.ay()) < 7) {
                    chips.remove(i);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void rebet() {
        chips.clear();
        long sum = 0;
        for (Chip c : lastChips) {
            if (sum + c.amount() > max) break;
            chips.add(c);
            sum += c.amount();
        }
        settled = false;
    }

    private void drawCell(GuiGraphics ctx, int x, int y, int w, int h, int n, int mx, int my) {
        int col = colorOf(n);
        boolean hit = result == n && !spinning() && settled;
        ctx.fill(x, y, x + w, y + h, 0xFF1F5A30);
        Ui.round(ctx, x + 2, y + 2, w - 4, h - 4, hit && ((int) (now() * 4)) % 2 == 0 ? 0xFFFFD34D : col);
        Ui.outline(ctx, x, y, w, h, 0xFFE8E8E8);
        Ui.text(ctx, Component.literal(String.valueOf(n)), x, y, w, h, hit ? 0xFF000000 : 0xFFFFFFFF, !hit);
    }

    private void outside(GuiGraphics ctx, int x, int y, int w, int h, Component label, long mask, int mx, int my, boolean can, int col) {
        boolean hover = can && Ui.in(mx, my, x, y, w, h);
        boolean hit = result >= 0 && settled && !spinning() && RouletteMath.covers(mask, result);
        ctx.fill(x, y, x + w, y + h, hover ? Ui.brighten(col, 30) : col);
        if (hit) Ui.outline(ctx, x + 1, y + 1, w - 2, h - 2, 0xFFFFD34D);
        Ui.outline(ctx, x, y, w, h, 0xFFE8E8E8);
        Ui.text(ctx, label, x, y, w, h, 0xFFFFFFFF, true);
        if (can) areas.add(new Area(x, y, w, h, () -> place(mask, x - gx + w / 2f, y - gy + h / 2f)));
    }

    /** Small marker where a chip would go, so splits, corners and streets are easy to aim. */
    private void hoverHint(GuiGraphics ctx, int mx, int my) {
        double lx = mx - gx, ly = my - gy;
        double fx = ((lx % cw) + cw) % cw, fy = ((ly % ch) + ch) % ch;
        float ax = (float) (fx < 4 ? lx - fx : fx > cw - 4 ? lx - fx + cw : Math.floor(lx / cw) * cw + cw / 2.0);
        float ay = (float) (fy < 4 ? ly - fy : fy > ch - 4 ? ly - fy + ch : Math.floor(ly / ch) * ch + ch / 2.0);
        CasinoDraw.disc(ctx, gx + ax, gy + ay, 3, 0xB0FFFFFF);
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

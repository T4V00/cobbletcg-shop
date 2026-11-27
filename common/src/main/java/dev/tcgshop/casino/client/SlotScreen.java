package dev.tcgshop.casino.client;

import dev.tcgshop.TcgSounds;
import dev.tcgshop.casino.CasinoGame;
import dev.tcgshop.casino.SlotMath;
import dev.tcgshop.client.binder.Ui;
import dev.tcgshop.client.screen.Draw;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

/**
 * Slot machine: three reels on one payline, a lever, the paytable on the side. The server draws the symbols;
 * the reels spin and stop one after the other on them (with a small bounce), then the win is counted up.
 */
public class SlotScreen extends CasinoScreen {
    private static final double[] STOP = {1.1, 1.6, 2.1};
    private final boolean vip;
    /** Reel positions (strip index, fractional) and the spin each reel is doing. */
    private final double[] posNow = new double[3], posFrom = new double[3], posTo = new double[3];
    private final int[] lastCell = {-1, -1, -1};
    private final boolean[] stopped = {true, true, true};
    private int[] result;
    private int mult;
    private long payout, lastBet;
    private double spinStart = -10, leverAt = -10;
    private boolean celebrated = true;

    public SlotScreen(CasinoGame game, BlockPos pos, CompoundTag d) {
        super(game, pos, d, game == CasinoGame.VIP_SLOTS ? "block.tcgshop.vip_slot_machine" : "block.tcgshop.slot_machine");
        this.vip = game == CasinoGame.VIP_SLOTS;
        for (int r = 0; r < 3; r++) posNow[r] = (r * 17) % SlotMath.strip(r).length;
    }

    private boolean spinning() {
        return now() - spinStart < STOP[2] + 0.2;
    }

    @Override
    public void onUpdate(CompoundTag d) {
        answered(d);
        result = d.getIntArray("Reels");
        if (result.length != 3) return;
        mult = d.getInt("Mult");
        payout = d.getLong("Payout");
        lastBet = d.getLong("Bet");
        spinStart = now();
        celebrated = false;
        java.util.Random rnd = new java.util.Random();
        for (int r = 0; r < 3; r++) {
            int[] strip = SlotMath.strip(r);
            int n = strip.length;
            // pick a stop on the strip with the server's symbol, then add whole turns
            int target = 0, seen = 0;
            for (int i = 0; i < n; i++) if (strip[i] == result[r] && rnd.nextInt(++seen) == 0) target = i;
            double from = posNow[r] % n;
            double dist = (target - from + n) % n + n * (2 + r);
            posFrom[r] = from;
            posTo[r] = from + dist;
            stopped[r] = false;
        }
    }

    private void spin() {
        if (spinning() || waiting() || bet > balance || !economy) return;
        leverAt = now();
        CompoundTag d = new CompoundTag();
        d.putLong("Bet", bet);
        act(d);
        sound(SoundEvents.LEVER_CLICK, 0.9f, 0.6f);
    }

    @Override
    protected void draw(GuiGraphics ctx, int mx, int my, float delta) {
        double t = now();
        int w = Math.min(width - 16, 420), h = Math.min(height - 16, 250), x = (width - w) / 2, y = (height - h) / 2;
        int top = frame(ctx, x, y, w, h, vip ? 0xFF3A2A0A : 0xFF5A1018, vip ? 0xFF1A1205 : 0xFF2A060A);

        // ---- cabinet
        int mw = Math.min(250, w - 150), mxL = x + 10, myT = top + 2, mh = h - (myT - y) - 40;
        Ui.roundedGradient(ctx, mxL, myT, mw, mh, 8, vip ? 0xFFE8C25A : 0xFFC62828, vip ? 0xFF8A6A1E : 0xFF6A0E14);
        Ui.roundedOutline(ctx, mxL, myT, mw, mh, 8, 0xFF1A0A0A);
        // marquee lights
        int lights = 14;
        for (int i = 0; i < lights; i++) {
            boolean on = ((int) (t * 6) + i) % 3 == 0 || (!celebrated && result != null && mult > 0);
            float lx = mxL + 10 + i * (mw - 20) / (float) (lights - 1);
            CasinoDraw.disc(ctx, lx, myT + 7, 2.5f, on ? 0xFFFFF59D : 0xFF6A5A20);
        }
        Draw.scaledCentered(ctx, Component.translatable(vip ? "tcgshop.casino.slots.vip_marquee" : "tcgshop.casino.slots.marquee").withStyle(ChatFormatting.BOLD),
                mxL + mw / 2f, myT + 13, 1.1f, 0xFFFFF3C0);

        // ---- reels
        int rw = (mw - 40) / 3, rh = Math.min(rw + 30, mh - 70), rx0 = mxL + 14, ry = myT + 28;
        ctx.fill(rx0 - 4, ry - 4, rx0 + 3 * rw + 2 * 3 + 4, ry + rh + 4, 0xFF15080A);
        for (int r = 0; r < 3; r++) {
            int rx = rx0 + r * (rw + 3);
            updateReel(r, t);
            ctx.fillGradient(rx, ry, rx + rw, ry + rh, 0xFFE8E8E8, 0xFFFFFFFF);
            ctx.enableScissor(rx, ry, rx + rw, ry + rh);
            int[] strip = SlotMath.strip(r);
            float cell = rw * 0.9f;
            double p = posNow[r];
            int base = (int) Math.floor(p);
            float frac = (float) (p - base);
            for (int k = -2; k <= 2; k++) {
                int sym = strip[Math.floorMod(base + k, strip.length)];
                float cy = ry + rh / 2f - (k - frac) * cell; // the next symbols come from above: the reel rolls down
                float s = cell * 0.82f;
                CasinoDraw.symbol(ctx, sym, rx + (rw - s) / 2, cy - s / 2, s);
            }
            ctx.disableScissor();
            // glass shading top/bottom
            ctx.fillGradient(rx, ry, rx + rw, ry + rh / 4, 0x90000000, 0x00000000);
            ctx.fillGradient(rx, ry + rh * 3 / 4, rx + rw, ry + rh, 0x00000000, 0x90000000);
            Ui.outline(ctx, rx, ry, rw, rh, 0xFF000000);
        }
        // payline
        boolean flash = !spinning() && result != null && mult > 0 && ((int) (t * 4)) % 2 == 0;
        int ly = ry + rh / 2;
        ctx.fill(rx0 - 8, ly, rx0 + 3 * rw + 14, ly + 1, flash ? 0xFFFFFF00 : 0xAAFF3030);
        ctx.fill(rx0 - 10, ly - 3, rx0 - 5, ly + 4, 0xFFFFD34D);
        ctx.fill(rx0 + 3 * rw + 11, ly - 3, rx0 + 3 * rw + 16, ly + 4, 0xFFFFD34D);

        // ---- win / message strip
        int wy = ry + rh + 10;
        Ui.rounded(ctx, mxL + 14, wy, mw - 28, 18, 4, 0xFF0A0A0A);
        Component msg;
        if (spinning() || waiting()) msg = Component.translatable("tcgshop.casino.slots.good_luck").withStyle(ChatFormatting.GRAY);
        else if (result != null && payout > 0) {
            double c = Mth.clamp((t - spinStart - STOP[2] - 0.2) / 1.2, 0, 1);
            msg = Component.translatable("tcgshop.casino.win", fmt(Math.round(payout * c))).withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD);
        } else if (result != null) msg = Component.translatable("tcgshop.casino.slots.no_win").withStyle(ChatFormatting.RED);
        else msg = Component.translatable("tcgshop.casino.slots.insert").withStyle(ChatFormatting.YELLOW);
        Ui.text(ctx, msg, mxL + 14, wy, mw - 28, 18, 0xFFFFFFFF, true);
        if (!spinning() && !celebrated && result != null) {
            celebrated = true;
            if (mult >= 100) sound(TcgSounds.CARD_LEGENDARY, 1f, 1f);
            else if (mult > 0) sound(TcgSounds.UI_COINS, 1f, 0.9f);
        }

        // ---- lever
        int lx = mxL + mw + 4, lyTop = myT + 20;
        double pull = Mth.clamp(1 - Math.abs((t - leverAt) - 0.25) / 0.25, 0, 1);
        int knobY = (int) (lyTop + pull * 50);
        ctx.fill(lx + 3, lyTop + 6, lx + 6, lyTop + 62, 0xFF9E9E9E);
        ctx.fill(lx + 2, Math.min(knobY, lyTop + 56), lx + 7, Math.max(knobY, lyTop + 56), 0xFFBDBDBD);
        CasinoDraw.disc(ctx, lx + 4.5f, knobY, 6, 0xFFD32F2F);
        CasinoDraw.disc(ctx, lx + 3, knobY - 2, 2, 0xFFFF8A80);
        ctx.fill(lx, lyTop + 58, lx + 10, lyTop + 66, 0xFF424242);
        boolean can = !spinning() && !waiting() && economy && bet <= balance;
        if (can) areas.add(new Area(lx - 4, lyTop - 6, 18, 76, this::spin));

        // ---- paytable
        int px = mxL + mw + 22, pw = x + w - 10 - px;
        if (pw > 80) paytable(ctx, px, top + 2, pw);

        // ---- bet + spin
        int by = y + h - 34;
        betBar(ctx, x + 10, by, w - 20 - 92, mx, my, can);
        Ui.button(ctx, x + w - 98, by - 2, 88, 22, Component.translatable("tcgshop.casino.spin").withStyle(ChatFormatting.BOLD), mx, my, can, vip ? 0xFFD39B1A : 0xFFC62828);
        if (can) areas.add(new Area(x + w - 98, by - 2, 88, 22, this::spin));
        Draw.scaledCentered(ctx, Component.translatable("tcgshop.casino.limits", fmt(min), fmt(max)).withStyle(ChatFormatting.DARK_GRAY), x + w / 2f, y + h - 11, 0.75f, 0xFF8A8A9A);
    }

    private void updateReel(int r, double t) {
        if (stopped[r]) return;
        double since = t - spinStart, end = STOP[r];
        double p = Mth.clamp(since / end, 0, 1);
        // fast spin, then a decelerating stop with a small overshoot
        double ease = p < 1 ? 1 - Math.pow(1 - p, 3) : 1;
        double over = p > 0.85 && p < 1 ? Math.sin((p - 0.85) / 0.15 * Math.PI) * 0.12 : 0;
        posNow[r] = posFrom[r] + (posTo[r] - posFrom[r]) * ease + over;
        int cell = (int) Math.floor(posNow[r]);
        if (cell != lastCell[r]) {
            lastCell[r] = cell;
            sound(SoundEvents.NOTE_BLOCK_HAT.value(), 1.8f, 0.15f);
        }
        if (p >= 1) {
            stopped[r] = true;
            posNow[r] = posTo[r];
            sound(SoundEvents.WOODEN_BUTTON_CLICK_ON, 0.7f + r * 0.1f, 0.7f);
        }
    }

    private void paytable(GuiGraphics ctx, int x, int y, int w) {
        Ui.rounded(ctx, x, y, w, 150, 6, 0xC0000000);
        ctx.drawString(font, Component.translatable("tcgshop.casino.paytable").withStyle(ChatFormatting.BOLD), x + 6, y + 4, Ui.GOLD);
        int row = y + 16;
        float s = 9;
        for (int sym = SlotMath.GEM; sym >= SlotMath.CHERRY; sym--) {
            for (int k = 0; k < 3; k++) CasinoDraw.symbol(ctx, sym, x + 5 + k * 10, row, s);
            ctx.drawString(font, "×" + SlotMath.THREE[sym], x + 40, row + 1, 0xFFFFFFFF);
            row += 12;
        }
        ctx.drawString(font, Component.translatable("tcgshop.casino.slots.any_premium", SlotMath.MIXED_PREMIUM), x + 5, row + 1, 0xFFE0E0E0);
        row += 11;
        CasinoDraw.symbol(ctx, SlotMath.CHERRY, x + 5, row, s);
        CasinoDraw.symbol(ctx, SlotMath.CHERRY, x + 15, row, s);
        ctx.drawString(font, "×" + SlotMath.TWO_CHERRIES, x + 40, row + 1, 0xFFFFFFFF);
        row += 12;
        CasinoDraw.symbol(ctx, SlotMath.CHERRY, x + 5, row, s);
        ctx.drawString(font, "×" + SlotMath.ONE_CHERRY, x + 40, row + 1, 0xFFFFFFFF);
        row += 13;
        Draw.scaledCentered(ctx, Component.translatable("tcgshop.casino.slots.rtp"), x + w / 2f, row, 0.7f, 0xFFB0B0C0);
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

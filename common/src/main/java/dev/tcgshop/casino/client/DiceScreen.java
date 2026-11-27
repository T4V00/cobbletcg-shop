package dev.tcgshop.casino.client;

import dev.tcgshop.TcgSounds;
import dev.tcgshop.casino.CasinoGame;
import dev.tcgshop.casino.DiceMath;
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

/** 1.1 dice table ("Lucky Seven"): pick a bet, roll; two dice tumble across the felt and settle on the server's roll. */
public class DiceScreen extends CasinoScreen {
    private static final double ROLL_TIME = 1.6;
    private static final int[][] PIPS = {{4}, {0, 8}, {0, 4, 8}, {0, 2, 6, 8}, {0, 2, 4, 6, 8}, {0, 2, 3, 5, 6, 8}};
    private static final int[] COLORS = {0xFF3A7BD5, 0xFFE5B21E, 0xFFD9472B, 0xFF8E24AA, 0xFF43A047, 0xFF43A047};
    private int pick, d1 = 3, d2 = 4, lastTick = -1;
    private long payout, staked;
    private double rollStart = -10;
    private boolean celebrated = true, rolledOnce;

    public DiceScreen(BlockPos pos, CompoundTag d) {
        super(CasinoGame.DICE, pos, d, "block.tcgshop.dice_table");
    }

    private boolean rolling() {
        return now() - rollStart < ROLL_TIME;
    }

    @Override
    public void onUpdate(CompoundTag d) {
        answered(d);
        d1 = d.getInt("D1");
        d2 = d.getInt("D2");
        payout = d.getLong("Payout");
        staked = d.getLong("Bet");
        rollStart = now();
        celebrated = false;
        rolledOnce = true;
        sound(TcgSounds.CASINO_CHIP, 1f, 0.7f);
    }

    private void roll() {
        if (rolling() || waiting() || !economy || bet > balance) return;
        CompoundTag d = new CompoundTag();
        d.putInt("Pick", pick);
        d.putLong("Bet", bet);
        act(d);
    }

    @Override
    protected void draw(GuiGraphics ctx, int mx, int my, float delta) {
        double t = now();
        int w = Math.min(width - 16, 430), h = Math.min(height - 16, 262), x = (width - w) / 2, y = (height - h) / 2;
        int top = frame(ctx, x, y, w, h, 0xFF3A1E5A, 0xFF160C26);
        // felt
        int fw = w * 52 / 100, fh = h - 86, fx = x + 10, fy = top + 4;
        Ui.rounded(ctx, fx - 3, fy - 3, fw + 6, fh + 6, 10, 0xFF5D3A0A);
        Ui.roundedGradient(ctx, fx, fy, fw, fh, 8, 0xFF3D1F66, 0xFF24103F);
        Ui.roundedOutline(ctx, fx + 4, fy + 4, fw - 8, fh - 8, 6, 0x55FFE27A);
        Draw.scaledCentered(ctx, Component.translatable("tcgshop.casino.dice.felt").withStyle(ChatFormatting.BOLD), fx + fw / 2f, fy + 8, 0.8f, 0x99FFE27A);
        // dice
        double p = Mth.clamp((t - rollStart) / ROLL_TIME, 0, 1);
        boolean rolling = p < 1;
        int s = Math.min(34, fh / 3);
        for (int k = 0; k < 2; k++) {
            int v = k == 0 ? d1 : d2;
            if (rolling) v = 1 + (int) ((t * 18 + k * 3) % 6); // tumbling faces
            double ease = 1 - Math.pow(1 - p, 3);
            float tx = fx + fw / 2f + (k == 0 ? -s * 0.8f : s * 0.8f), ty = fy + fh / 2f + (k == 0 ? -4 : 6);
            float sx = fx + fw - 12, sy = fy + 14;
            float cx = (float) (sx + (tx - sx) * ease), cy = (float) (sy + (ty - sy) * ease - Math.abs(Math.sin(p * 9)) * (1 - p) * 30);
            die(ctx, cx, cy, s, v, rolling ? (float) ((1 - ease) * 40 * (k == 0 ? 1 : -1)) : 0);
        }
        int tick = (int) (p * 12);
        if (rolling && tick != lastTick) {
            lastTick = tick;
            sound(SoundEvents.WOODEN_BUTTON_CLICK_ON, 1.7f + tick * 0.02f, 0.3f);
        }
        if (!rolling && rolledOnce) {
            int sum = d1 + d2;
            Component res = payout > 0 ? Component.translatable("tcgshop.casino.win", fmt(payout)).withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
                    : Component.translatable("tcgshop.casino.dice.rolled", sum).withStyle(ChatFormatting.RED);
            Draw.scaledCentered(ctx, res, fx + fw / 2f, fy + fh - 16, 1f, 0xFFFFFFFF);
            if (!celebrated) {
                celebrated = true;
                sound(payout > 0 ? (payout >= staked * 10 ? TcgSounds.CARD_LEGENDARY : TcgSounds.CASINO_WIN) : SoundEvents.NOTE_BLOCK_BASS.value(), 1f, 0.8f);
            }
        }
        // ---- bets
        int bx = fx + fw + 12, bw = x + w - 10 - bx, row = top + 4;
        ctx.drawString(font, Component.translatable("tcgshop.casino.dice.pick").withStyle(ChatFormatting.BOLD), bx, row, Ui.GOLD);
        row += 12;
        boolean can = !rolling && !waiting();
        for (int b = 0; b < DiceMath.COUNT; b++) {
            int rh = 18;
            boolean sel = b == pick, hover = can && Ui.in(mx, my, bx, row, bw, rh - 2);
            Ui.rounded(ctx, bx, row, bw, rh - 2, 4, sel ? Ui.brighten(COLORS[b], 25) : hover ? 0xFF3A3050 : 0xFF241A34);
            if (sel) Ui.roundedOutline(ctx, bx, row, bw, rh - 2, 4, 0xFFFFFFFF);
            ctx.drawString(font, Component.translatable("tcgshop.casino.dice." + DiceMath.BETS[b]), bx + 5, row + 4, 0xFFFFFFFF);
            Component pays = Component.translatable("tcgshop.casino.dice.pays", DiceMath.PAYS[b]);
            ctx.drawString(font, pays, bx + bw - 4 - font.width(pays), row + 4, 0xFFD0C8E0);
            final int fb = b;
            if (can) areas.add(new Area(bx, row, bw, rh - 2, () -> pick = fb));
            row += rh;
        }
        Draw.scaledCentered(ctx, Component.translatable("tcgshop.casino.wheel.rtp", String.format(java.util.Locale.ROOT, "%.1f", DiceMath.rtp(pick) * 100)),
                bx + bw / 2f, row + 2, 0.75f, 0xFFB0A8C0);
        int by = y + h - 34;
        boolean canRoll = can && economy && bet <= balance;
        betBar(ctx, x + 10, by, w - 20 - 92, mx, my, can);
        Ui.button(ctx, x + w - 98, by - 2, 88, 22, Component.translatable("tcgshop.casino.dice.roll").withStyle(ChatFormatting.BOLD), mx, my, canRoll, 0xFF8E24AA);
        if (canRoll) areas.add(new Area(x + w - 98, by - 2, 88, 22, this::roll));
        Draw.scaledCentered(ctx, Component.translatable("tcgshop.casino.limits", fmt(min), fmt(max)).withStyle(ChatFormatting.DARK_GRAY), x + w / 2f, y + h - 11, 0.75f, 0xFF8A8A9A);
    }

    /** A die seen from above, centred at (cx, cy), slightly rotated while tumbling. */
    private void die(GuiGraphics ctx, float cx, float cy, int s, int v, float rot) {
        var m = ctx.pose();
        m.pushPose();
        m.translate(cx, cy, 0);
        m.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(rot));
        int h = s / 2;
        Ui.rounded(ctx, -h + 2, -h + 3, s, s, 6, 0x66000000);
        Ui.roundedGradient(ctx, -h, -h, s, s, 6, 0xFFFFFFFF, 0xFFE2DCCD);
        Ui.roundedOutline(ctx, -h, -h, s, s, 6, 0xFF8A8270);
        float off = s * 0.27f, r = Math.max(1.5f, s * 0.09f);
        for (int idx : PIPS[Math.max(1, Math.min(6, v)) - 1])
            CasinoDraw.disc(ctx, (idx % 3 - 1) * off, (idx / 3 - 1) * off, r, v == 1 ? 0xFFD3122A : 0xFF151515);
        m.popPose();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_SPACE || keyCode == GLFW.GLFW_KEY_ENTER) {
            roll();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}

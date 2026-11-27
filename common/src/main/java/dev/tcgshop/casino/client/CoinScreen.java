package dev.tcgshop.casino.client;

import dev.tcgshop.TcgSounds;
import dev.tcgshop.casino.CasinoGame;
import dev.tcgshop.casino.CoinMath;
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

/** 1.1 coin toss stand ("Double or Nothing"): heads, tails or the rare edge; the coin spins up and lands. */
public class CoinScreen extends CasinoScreen {
    private static final double FLIP_TIME = 2.0;
    private int pick, side = -1;
    private long payout;
    private double flipStart = -10;
    private boolean celebrated = true;

    public CoinScreen(BlockPos pos, CompoundTag d) {
        super(CasinoGame.COIN, pos, d, "block.tcgshop.coin_toss");
    }

    private boolean flipping() {
        return now() - flipStart < FLIP_TIME;
    }

    @Override
    public void onUpdate(CompoundTag d) {
        answered(d);
        side = d.getInt("Side");
        payout = d.getLong("Payout");
        flipStart = now();
        celebrated = false;
        sound(TcgSounds.CASINO_CHIP, 1.5f, 0.8f);
    }

    private void flip() {
        if (flipping() || waiting() || !economy || bet > balance) return;
        CompoundTag d = new CompoundTag();
        d.putInt("Pick", pick);
        d.putLong("Bet", bet);
        act(d);
    }

    @Override
    protected void draw(GuiGraphics ctx, int mx, int my, float delta) {
        double t = now();
        int w = Math.min(width - 16, 400), h = Math.min(height - 16, 250), x = (width - w) / 2, y = (height - h) / 2;
        int top = frame(ctx, x, y, w, h, 0xFF1E3A5A, 0xFF0A1626);
        int aw = w * 50 / 100, ah = h - 86, ax = x + 10, ay = top + 4;
        Ui.roundedGradient(ctx, ax, ay, aw, ah, 8, 0xFF16283E, 0xFF0B1422);
        Ui.roundedOutline(ctx, ax, ay, aw, ah, 8, 0x66E5B21E);
        float cx = ax + aw / 2f, base = ay + ah - 30, R = Math.min(34, ah / 4f);
        // stand
        Ui.rounded(ctx, (int) (cx - R - 8), (int) base + 6, (int) (R * 2 + 16), 8, 3, 0xFF5D3A0A);
        Ui.rounded(ctx, (int) (cx - R / 2), (int) base + 14, (int) R, 10, 2, 0xFF3A2410);
        double p = Mth.clamp((t - flipStart) / FLIP_TIME, 0, 1);
        boolean flying = p < 1 && side >= 0;
        float hgt = flying ? (float) (4 * p * (1 - p)) * (ah - 2 * R - 40) : 0;
        float cy = base - R + 4 - hgt;
        // the coin seen from the side while it spins: its height shrinks and grows (cos of the spin angle)
        double spin = flying ? p * Math.PI * 12 : side == 2 ? Math.PI / 2 : 0;
        float squash = (float) Math.abs(Math.cos(spin));
        boolean showTails = flying ? Math.cos(spin) < 0 ^ side == 1 : side == 1;
        if (side == 2 && !flying) squash = 0.12f;
        coin(ctx, cx, cy, R, Math.max(0.08f, squash), showTails);
        if (!flying && side >= 0) {
            Component res = payout > 0 ? Component.translatable("tcgshop.casino.win", fmt(payout)).withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
                    : Component.translatable("tcgshop.casino.coin.landed", Component.translatable("tcgshop.casino.coin." + CoinMath.BETS[side])).withStyle(ChatFormatting.RED);
            Draw.scaledCentered(ctx, res, cx, ay + 8, 1f, 0xFFFFFFFF);
            if (!celebrated) {
                celebrated = true;
                sound(payout > 0 ? (side == 2 ? TcgSounds.CARD_LEGENDARY : TcgSounds.CASINO_WIN) : SoundEvents.NOTE_BLOCK_BASS.value(), 1f, 0.8f);
            }
        }
        // ---- bets
        int bx = ax + aw + 12, bw = x + w - 10 - bx, row = top + 4;
        ctx.drawString(font, Component.translatable("tcgshop.casino.coin.pick").withStyle(ChatFormatting.BOLD), bx, row, Ui.GOLD);
        row += 12;
        boolean can = !flying && !waiting();
        int[] cols = {0xFFE5B21E, 0xFF9AA4B0, 0xFF8E24AA};
        for (int b = 0; b < CoinMath.COUNT; b++) {
            int rh = 24;
            boolean sel = b == pick, hover = can && Ui.in(mx, my, bx, row, bw, rh - 3);
            Ui.rounded(ctx, bx, row, bw, rh - 3, 5, sel ? Ui.brighten(cols[b], 10) : hover ? 0xFF2A3A50 : 0xFF182432);
            if (sel) Ui.roundedOutline(ctx, bx, row, bw, rh - 3, 5, 0xFFFFFFFF);
            ctx.drawString(font, Component.translatable("tcgshop.casino.coin." + CoinMath.BETS[b]).withStyle(ChatFormatting.BOLD), bx + 6, row + 3, sel ? 0xFF101010 : 0xFFFFFFFF, false);
            Component pays = Component.translatable("tcgshop.casino.coin.odds", CoinMath.PAYS[b], CoinMath.WEIGHTS[b]);
            ctx.drawString(font, pays, bx + 6, row + 12, sel ? 0xFF303030 : 0xFFB0B8C8, false);
            final int fb = b;
            if (can) areas.add(new Area(bx, row, bw, rh - 3, () -> pick = fb));
            row += rh;
        }
        Draw.scaledCentered(ctx, Component.translatable("tcgshop.casino.wheel.rtp", String.format(java.util.Locale.ROOT, "%.1f", CoinMath.rtp(pick) * 100)),
                bx + bw / 2f, row + 2, 0.75f, 0xFFB0A8C0);
        int by = y + h - 34;
        boolean canFlip = can && economy && bet <= balance;
        betBar(ctx, x + 10, by, w - 20 - 92, mx, my, can);
        Ui.button(ctx, x + w - 98, by - 2, 88, 22, Component.translatable("tcgshop.casino.coin.flip").withStyle(ChatFormatting.BOLD), mx, my, canFlip, 0xFF1E8FA8);
        if (canFlip) areas.add(new Area(x + w - 98, by - 2, 88, 22, this::flip));
        Draw.scaledCentered(ctx, Component.translatable("tcgshop.casino.limits", fmt(min), fmt(max)).withStyle(ChatFormatting.DARK_GRAY), x + w / 2f, y + h - 11, 0.75f, 0xFF8A8A9A);
    }

    /** The coin face-on, squashed vertically by `sy` (1 = flat towards the viewer, ~0 = seen edge-on). */
    private void coin(GuiGraphics ctx, float cx, float cy, float r, float sy, boolean tails) {
        CasinoDraw.ellipse(ctx, cx, cy + 3, r + 1, (r + 1) * sy, 0x66000000);
        CasinoDraw.ellipse(ctx, cx, cy, r, r * sy, 0xFFB8860B);
        CasinoDraw.ellipse(ctx, cx, cy, r - 3, (r - 3) * sy, tails ? 0xFFC9CED6 : 0xFFF2C230);
        CasinoDraw.ellipse(ctx, cx, cy, r - 7, (r - 7) * sy, tails ? 0xFFAEB4BE : 0xFFE5B21E);
        if (sy > 0.35f) {
            var m = ctx.pose();
            m.pushPose();
            m.translate(cx, cy, 0);
            m.scale(1, sy, 1);
            Draw.scaledCentered(ctx, Component.literal(tails ? "◎" : "★").withStyle(ChatFormatting.BOLD), 0, -r * 0.3f, r / 14f, tails ? 0xFF6E747C : 0xFFFFF3C0);
            m.popPose();
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_SPACE || keyCode == GLFW.GLFW_KEY_ENTER) {
            flip();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}

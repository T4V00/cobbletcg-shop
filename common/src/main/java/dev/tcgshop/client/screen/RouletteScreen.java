package dev.tcgshop.client.screen;

import dev.masterball.api.platform.Platform;
import dev.masterball.api.platform.ClientPlatform;
import dev.tcgshop.TcgSounds;
import dev.tcgshop.client.binder.Ui;
import dev.tcgshop.network.RouletteResultPayload;
import dev.tcgshop.network.RouletteSpinPayload;
import dev.tcgshop.pricing.PriceService;
import dev.tcgshop.shop.Roulette;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** Poké Roulette: pick red or black, any amount you want, and spin. The number strip slows down on the result. */
public class RouletteScreen extends Screen {
    private record Area(int x, int y, int w, int h, Runnable action) {}

    private final BlockPos pos;
    private long balance;
    private boolean red = true;
    private EditBox amount;
    private final List<Area> areas = new ArrayList<>();
    private final List<Integer> history = new ArrayList<>();
    private double spinStart = -10, lastTick;
    private int result = -1, lastCell = -1;
    private boolean win, waiting;
    private long payout;

    public RouletteScreen(BlockPos pos, long balance) {
        super(Component.translatable("block.tcgshop.roulette"));
        this.pos = pos;
        this.balance = balance;
    }

    @Override
    protected void init() {
        String prev = amount == null ? "1000" : amount.getValue();
        amount = new EditBox(font, width / 2 - 60, height / 2 + 30, 120, 16, Component.empty());
        amount.setFilter(s -> s.matches("\\d{0,12}"));
        amount.setValue(prev);
        addWidget(amount);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    public void onResult(RouletteResultPayload p) {
        result = p.number();
        win = p.win();
        payout = p.payout();
        balance = p.balance();
        spinStart = System.nanoTime() / 1e9;
        waiting = false;
        lastCell = -1;
    }

    private long bet() {
        try { return Long.parseLong(amount.getValue()); } catch (NumberFormatException e) { return 0; }
    }

    private boolean spinning() { return System.nanoTime() / 1e9 - spinStart < 4.2; }

    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        areas.clear();
        double t = System.nanoTime() / 1e9;
        ctx.fillGradient(0, 0, width, height, 0xC0100A06, 0xE0050302);
        int w = Math.min(width - 20, 380), h = 230, x = (width - w) / 2, y = (height - h) / 2;
        Ui.leather(ctx, x, y, w, h, 0xFF1F5A30, 0xFF0F2E18);
        Draw.scaledCentered(ctx, title.copy().withStyle(ChatFormatting.BOLD), width / 2f, y + 8, 1.4f, Ui.GOLD);
        ctx.drawCenteredString(font, Component.translatable("tcgshop.roulette.balance", PriceService.format(balance)), width / 2, y + 24, 0xFFFFFFFF);

        // number strip
        int sx = x + 12, sw = w - 24, sy = y + 40, sh = 44, cell = 34;
        ctx.fill(sx, sy, sx + sw, sy + sh, 0xFF0A0A0A);
        double p = Mth.clamp((t - spinStart) / 4.0, 0, 1);
        double ease = 1 - Math.pow(1 - p, 4);
        int resultIdx = 0;
        for (int i = 0; i < Roulette.WHEEL.length; i++) if (Roulette.WHEEL[i] == result) resultIdx = i;
        double total = (Roulette.WHEEL.length * 5 + resultIdx) * cell;
        double offset = result < 0 ? t * 20 : total * ease;
        ctx.enableScissor(sx, sy, sx + sw, sy + sh);
        int centerX = sx + sw / 2;
        int first = (int) Math.floor((offset - sw / 2.0) / cell) - 1;
        for (int k = first; k < first + sw / cell + 3; k++) {
            int n = Roulette.WHEEL[Math.floorMod(k, Roulette.WHEEL.length)];
            int cx = (int) (centerX + k * cell - offset) - cell / 2;
            int col = n == 0 ? 0xFF1E8C3A : Roulette.RED.contains(n) ? 0xFFC62828 : 0xFF1B1B1F;
            Ui.roundGradient(ctx, cx + 1, sy + 3, cell - 2, sh - 6, Ui.brighten(col, 25), col);
            Ui.text(ctx, Component.literal(String.valueOf(n)).withStyle(ChatFormatting.BOLD), cx, sy + 3, cell, sh - 6, 0xFFFFFFFF, true);
        }
        ctx.disableScissor();
        // tick-tock as each cell passes
        int cellNow = (int) (offset / cell);
        if (spinning() && cellNow != lastCell && minecraft != null) {
            lastCell = cellNow;
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_HAT.value(), 1.6f, 0.4f));
        }
        ctx.fill(centerX - 1, sy - 4, centerX + 1, sy + sh + 4, 0xFFF2C230);
        Ui.outline(ctx, sx, sy, sw, sh, 0xFF000000);

        // result
        if (result >= 0 && !spinning()) {
            Component res = win ? Component.translatable("tcgshop.roulette.win", PriceService.format(payout)).withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
                    : Component.translatable("tcgshop.roulette.lose").withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
            Draw.scaledCentered(ctx, res, width / 2f, sy + sh + 6, 1.2f, 0xFFFFFFFF);
            if (history.isEmpty() || history.get(history.size() - 1) != result || lastTick < spinStart) {
                lastTick = t;
                history.add(result);
                if (history.size() > 12) history.remove(0);
                if (minecraft != null) minecraft.getSoundManager().play(SimpleSoundInstance.forUI(win ? TcgSounds.CARD_LEGENDARY : SoundEvents.VILLAGER_NO, 1f, 1f));
            }
        }
        // history
        int hx = x + 12;
        for (int n : history) {
            int col = n == 0 ? 0xFF1E8C3A : Roulette.RED.contains(n) ? 0xFFC62828 : 0xFF1B1B1F;
            Ui.round(ctx, hx, y + h - 16, 14, 10, col);
            Ui.text(ctx, Component.literal(String.valueOf(n)), hx, y + h - 16, 14, 10, 0xFFFFFFFF, false);
            hx += 16;
        }

        // color
        int by = sy + sh + 22;
        int bw = 80;
        Ui.button(ctx, width / 2 - bw - 6, by, bw, 20, Component.translatable("tcgshop.roulette.red"), mouseX, mouseY, true, red ? 0xFFE53935 : 0xFF5A2020);
        areas.add(new Area(width / 2 - bw - 6, by, bw, 20, () -> red = true));
        Ui.button(ctx, width / 2 + 6, by, bw, 20, Component.translatable("tcgshop.roulette.black"), mouseX, mouseY, true, !red ? 0xFF3A3A44 : 0xFF1A1A1E);
        areas.add(new Area(width / 2 + 6, by, bw, 20, () -> red = false));
        if (red) Ui.outline(ctx, width / 2 - bw - 7, by - 1, bw + 2, 22, 0xFFFFFFFF);
        else Ui.outline(ctx, width / 2 + 5, by - 1, bw + 2, 22, 0xFFFFFFFF);

        // amount
        amount.setY(by + 26);
        amount.render(ctx, mouseX, mouseY, delta);
        String[] quick = {"+1k", "+10k", "+100k", "×2", "½", "Max"};
        int qx = width / 2 - (quick.length * 36) / 2, qy = by + 46;
        for (String q : quick) {
            Ui.button(ctx, qx, qy, 34, 14, Component.literal(q), mouseX, mouseY, !spinning(), 0xFF444A60);
            areas.add(new Area(qx, qy, 34, 14, () -> quick(q)));
            qx += 36;
        }
        long b = bet();
        boolean can = !waiting && !spinning() && b > 0 && b <= balance;
        Ui.button(ctx, width / 2 - 60, qy + 20, 120, 22, Component.translatable("tcgshop.roulette.spin").withStyle(ChatFormatting.BOLD), mouseX, mouseY, can, 0xFFD39B1A);
        if (can) areas.add(new Area(width / 2 - 60, qy + 20, 120, 22, this::spin));
    }

    private void quick(String q) {
        long b = bet();
        b = switch (q) {
            case "+1k" -> b + 1000;
            case "+10k" -> b + 10_000;
            case "+100k" -> b + 100_000;
            case "×2" -> b * 2;
            case "½" -> Math.max(1, b / 2);
            default -> balance;
        };
        amount.setValue(String.valueOf(Math.min(balance, Math.max(0, b))));
    }

    private void spin() {
        waiting = true;
        result = -1;
        ClientPlatform.INSTANCE.send(new RouletteSpinPayload(pos, red, bet()));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (amount.mouseClicked(mouseX, mouseY, button)) { setFocused(amount); return true; }
        amount.setFocused(false);
        for (Area a : areas) if (Ui.in(mouseX, mouseY, a.x, a.y, a.w, a.h)) { a.action.run(); return true; }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        return amount.isFocused() && amount.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (amount.isFocused() && keyCode != GLFW.GLFW_KEY_ESCAPE) return amount.keyPressed(keyCode, scanCode, modifiers) || true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}

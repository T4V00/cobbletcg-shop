package dev.tcgshop.client.screen;

import dev.tcgshop.TcgSounds;
import dev.tcgshop.client.binder.Ui;
import dev.tcgshop.network.RouletteResultPayload;
import dev.tcgshop.network.RouletteSpinPayload;
import dev.tcgshop.pricing.PriceService;
import dev.tcgshop.shop.Roulette;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** Ruleta Poké: elige rojo o negro, la cantidad que quieras, y gira. La cinta de números frena en el resultado. */
public class RouletteScreen extends Screen {
    private record Area(int x, int y, int w, int h, Runnable action) {}

    private final BlockPos pos;
    private long balance;
    private boolean red = true;
    private TextFieldWidget amount;
    private final List<Area> areas = new ArrayList<>();
    private final List<Integer> history = new ArrayList<>();
    private double spinStart = -10, lastTick;
    private int result = -1, lastCell = -1;
    private boolean win, waiting;
    private long payout;

    public RouletteScreen(BlockPos pos, long balance) {
        super(Text.translatable("block.tcgshop.roulette"));
        this.pos = pos;
        this.balance = balance;
    }

    @Override
    protected void init() {
        String prev = amount == null ? "1000" : amount.getText();
        amount = new TextFieldWidget(textRenderer, width / 2 - 60, height / 2 + 30, 120, 16, Text.empty());
        amount.setTextPredicate(s -> s.matches("\\d{0,12}"));
        amount.setText(prev);
        addSelectableChild(amount);
    }

    @Override
    public boolean shouldPause() { return false; }

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
        try { return Long.parseLong(amount.getText()); } catch (NumberFormatException e) { return 0; }
    }

    private boolean spinning() { return System.nanoTime() / 1e9 - spinStart < 4.2; }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        areas.clear();
        double t = System.nanoTime() / 1e9;
        ctx.fillGradient(0, 0, width, height, 0xC0100A06, 0xE0050302);
        int w = Math.min(width - 20, 380), h = 230, x = (width - w) / 2, y = (height - h) / 2;
        Ui.leather(ctx, x, y, w, h, 0xFF1F5A30, 0xFF0F2E18);
        Draw.scaledCentered(ctx, title.copy().formatted(Formatting.BOLD), width / 2f, y + 8, 1.4f, Ui.GOLD);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.translatable("tcgshop.roulette.balance", PriceService.format(balance)), width / 2, y + 24, 0xFFFFFFFF);

        // cinta de números
        int sx = x + 12, sw = w - 24, sy = y + 40, sh = 44, cell = 34;
        ctx.fill(sx, sy, sx + sw, sy + sh, 0xFF0A0A0A);
        double p = MathHelper.clamp((t - spinStart) / 4.0, 0, 1);
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
            Ui.text(ctx, Text.literal(String.valueOf(n)).formatted(Formatting.BOLD), cx, sy + 3, cell, sh - 6, 0xFFFFFFFF, true);
        }
        ctx.disableScissor();
        // tic-tac al pasar cada casilla
        int cellNow = (int) (offset / cell);
        if (spinning() && cellNow != lastCell && client != null) {
            lastCell = cellNow;
            client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(), 1.6f, 0.4f));
        }
        ctx.fill(centerX - 1, sy - 4, centerX + 1, sy + sh + 4, 0xFFF2C230);
        Ui.outline(ctx, sx, sy, sw, sh, 0xFF000000);

        // resultado
        if (result >= 0 && !spinning()) {
            Text res = win ? Text.translatable("tcgshop.roulette.win", PriceService.format(payout)).formatted(Formatting.GREEN, Formatting.BOLD)
                    : Text.translatable("tcgshop.roulette.lose").formatted(Formatting.RED, Formatting.BOLD);
            Draw.scaledCentered(ctx, res, width / 2f, sy + sh + 6, 1.2f, 0xFFFFFFFF);
            if (history.isEmpty() || history.get(history.size() - 1) != result || lastTick < spinStart) {
                lastTick = t;
                history.add(result);
                if (history.size() > 12) history.remove(0);
                if (client != null) client.getSoundManager().play(PositionedSoundInstance.master(win ? TcgSounds.CARD_LEGENDARY : SoundEvents.ENTITY_VILLAGER_NO, 1f, 1f));
            }
        }
        // historial
        int hx = x + 12;
        for (int n : history) {
            int col = n == 0 ? 0xFF1E8C3A : Roulette.RED.contains(n) ? 0xFFC62828 : 0xFF1B1B1F;
            Ui.round(ctx, hx, y + h - 16, 14, 10, col);
            Ui.text(ctx, Text.literal(String.valueOf(n)), hx, y + h - 16, 14, 10, 0xFFFFFFFF, false);
            hx += 16;
        }

        // color
        int by = sy + sh + 22;
        int bw = 80;
        Ui.button(ctx, width / 2 - bw - 6, by, bw, 20, Text.translatable("tcgshop.roulette.red"), mouseX, mouseY, true, red ? 0xFFE53935 : 0xFF5A2020);
        areas.add(new Area(width / 2 - bw - 6, by, bw, 20, () -> red = true));
        Ui.button(ctx, width / 2 + 6, by, bw, 20, Text.translatable("tcgshop.roulette.black"), mouseX, mouseY, true, !red ? 0xFF3A3A44 : 0xFF1A1A1E);
        areas.add(new Area(width / 2 + 6, by, bw, 20, () -> red = false));
        if (red) Ui.outline(ctx, width / 2 - bw - 7, by - 1, bw + 2, 22, 0xFFFFFFFF);
        else Ui.outline(ctx, width / 2 + 5, by - 1, bw + 2, 22, 0xFFFFFFFF);

        // cantidad
        amount.setY(by + 26);
        amount.render(ctx, mouseX, mouseY, delta);
        String[] quick = {"+1k", "+10k", "+100k", "×2", "½", "Max"};
        int qx = width / 2 - (quick.length * 36) / 2, qy = by + 46;
        for (String q : quick) {
            Ui.button(ctx, qx, qy, 34, 14, Text.literal(q), mouseX, mouseY, !spinning(), 0xFF444A60);
            areas.add(new Area(qx, qy, 34, 14, () -> quick(q)));
            qx += 36;
        }
        long b = bet();
        boolean can = !waiting && !spinning() && b > 0 && b <= balance;
        Ui.button(ctx, width / 2 - 60, qy + 20, 120, 22, Text.translatable("tcgshop.roulette.spin").formatted(Formatting.BOLD), mouseX, mouseY, can, 0xFFD39B1A);
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
        amount.setText(String.valueOf(Math.min(balance, Math.max(0, b))));
    }

    private void spin() {
        waiting = true;
        result = -1;
        ClientPlayNetworking.send(new RouletteSpinPayload(pos, red, bet()));
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

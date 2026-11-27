package dev.tcgshop.client.screen;

import dev.masterball.api.platform.ClientPlatform;
import dev.tcgshop.client.binder.Ui;
import dev.tcgshop.network.UiPayloads;
import dev.tcgshop.pricing.PriceService;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

import static dev.tcgshop.client.screen.Draw.withAlpha;

/**
 * 1.1: the Mayor's tax bill (npc/TaxOffice). Shows how the tax is made up (property part + income part), what you can
 * pay with (wallet + registers) and the two answers, each with one line saying what it does: Pay, or Refuse (a
 * Cobblemon battle; losing, forfeiting or walking away costs one random binder card). Refuse needs a second click.
 */
public class MayorScreen extends Screen {
    private final CompoundTag d;
    private final List<int[]> rects = new ArrayList<>();
    private final List<Runnable> actions = new ArrayList<>();
    private final double openedAt = System.nanoTime() / 1e9;
    private double refuseArmedAt = -10;

    public MayorScreen(CompoundTag d) {
        super(Component.translatable("tcgshop.mayor.title"));
        this.d = d;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void render(GuiGraphics ctx, int mx, int my, float delta) {
        rects.clear();
        actions.clear();
        double t = System.nanoTime() / 1e9;
        float in = (float) Draw.easeOutCubic(Mth.clamp((t - openedAt) / 0.25, 0, 1));
        ctx.fillGradient(0, 0, width, height, withAlpha(0xFF10131C, 0.75f * in), withAlpha(0xFF05060A, 0.88f * in));
        int w = Math.min(width - 16, 320), h = Math.min(height - 16, 222), x = (width - w) / 2, y = (height - h) / 2 + Math.round((1 - in) * 12);
        ctx.fill(x + 3, y + 4, x + w + 3, y + h + 4, 0x66000000);
        Ui.roundGradient(ctx, x, y, w, h, 0xF0262A36, 0xF014161E);
        Ui.roundGradient(ctx, x, y, w, 24, 0xFF8A6A1E, 0xFF5A4410);
        Draw.scaledCentered(ctx, title.copy().withStyle(ChatFormatting.BOLD), x + w / 2f, y + 8, 1f, 0xFFFFF2C8);

        long amount = d.getLong("amount"), wallet = d.getLong("wallet"), regs = d.getLong("registers");
        boolean cobblemon = d.getBoolean("cobblemon");
        int ty = y + 30;
        ctx.drawString(font, Component.literal(d.getString("name")).withStyle(ChatFormatting.YELLOW), x + 10, ty, 0xFFFFFFFF);
        ty += 11;
        for (FormattedCharSequence l : font.split(Component.translatable("tcgshop.mayor.ui.ask", Component.translatable("tcgshop.shop.tier." + d.getString("tier")))
                .withStyle(ChatFormatting.ITALIC), w - 20)) {
            ctx.drawString(font, l, x + 10, ty, 0xFFD8D2C4);
            ty += 10;
        }
        // ---- the bill
        ty += 3;
        int bh = 50;
        Ui.round(ctx, x + 8, ty, w - 16, bh, 0xAA0B0D14);
        line(ctx, x + 14, ty + 5, w - 28, Component.translatable("tcgshop.mayor.ui.property"), d.getLong("property"), 0xFFCFCFCF);
        line(ctx, x + 14, ty + 16, w - 28, Component.translatable("tcgshop.mayor.ui.income",
                String.format(java.util.Locale.ROOT, "%.1f%%", d.getDouble("rate") * 100), PriceService.format(d.getLong("earned")), d.getInt("interval")), d.getLong("income"), 0xFFCFCFCF);
        ctx.fill(x + 14, ty + 28, x + w - 14, ty + 29, 0x55FFFFFF);
        line(ctx, x + 14, ty + 33, w - 28, Component.translatable("tcgshop.mayor.ui.total").withStyle(ChatFormatting.BOLD), amount, Ui.GOLD);
        ty += bh + 4;
        boolean afford = wallet + regs >= amount;
        Ui.text(ctx, Component.translatable("tcgshop.mayor.ui.funds", PriceService.format(wallet), PriceService.format(regs)), x, ty, w, 10,
                afford ? 0xFF8BE08B : 0xFFFF8A8A, false);
        ty += 11;
        Ui.text(ctx, Component.translatable("tcgshop.mayor.ui.deadline", d.getLong("daysLeft")), x, ty, w, 10, 0xFF9A9486, false);

        // ---- the answers: one big button each, one line under it saying what it does
        int bw = (w - 30) / 2, by = y + h - 58;
        Ui.button(ctx, x + 10, by, bw, 22, Component.translatable("tcgshop.mayor.ui.pay", PriceService.format(amount)), mx, my, afford, 0xFF2E9C5A);
        if (afford) add(x + 10, by, bw, 22, () -> send("pay"));
        boolean armed = t - refuseArmedAt < 3;
        Component refuse = Component.translatable(armed ? "tcgshop.mayor.ui.refuse_confirm" : cobblemon ? "tcgshop.mayor.ui.refuse_battle" : "tcgshop.mayor.ui.refuse");
        Ui.button(ctx, x + 20 + bw, by, bw, 22, refuse, mx, my, true, armed ? 0xFFB02020 : 0xFF8A2A2A);
        add(x + 20 + bw, by, bw, 22, () -> {
            double now = System.nanoTime() / 1e9;
            if (now - refuseArmedAt >= 3) { refuseArmedAt = now; click(); return; }
            send("refuse");
        });
        int hy = by + 25;
        for (FormattedCharSequence l : font.split(Component.translatable("tcgshop.mayor.ui.pay_hint", d.getInt("interval")), bw)) {
            ctx.drawString(font, l, x + 10, hy, 0xFF9FD8B0, false);
            hy += 9;
        }
        hy = by + 25;
        for (FormattedCharSequence l : font.split(Component.translatable(cobblemon ? "tcgshop.mayor.ui.refuse_hint_battle" : "tcgshop.mayor.ui.refuse_hint",
                d.getLong("binder")), bw)) {
            ctx.drawString(font, l, x + 20 + bw, hy, 0xFFFFA8A8, false);
            hy += 9;
        }
    }

    private void line(GuiGraphics ctx, int x, int y, int w, Component label, long v, int color) {
        String amt = PriceService.format(v);
        ctx.drawString(font, Ui.ellipsis(label.getString(), w - font.width(amt) - 8), x, y, color, false);
        ctx.drawString(font, amt, x + w - font.width(amt), y, Ui.GOLD, false);
    }

    private void add(int x, int y, int w, int h, Runnable r) {
        rects.add(new int[]{x, y, w, h});
        actions.add(r);
    }

    private void send(String action) {
        CompoundTag a = new CompoundTag();
        a.putInt("id", d.getInt("id"));
        a.putString("action", action);
        ClientPlatform.INSTANCE.send(new UiPayloads.Act("mayor", a));
        onClose();
    }

    private void click() {
        if (minecraft != null) minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 1.2f, 0.4f));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (int i = rects.size() - 1; i >= 0; i--) {
            int[] r = rects.get(i);
            if (Ui.in(mouseX, mouseY, r[0], r[1], r[2], r[3])) {
                actions.get(i).run();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}

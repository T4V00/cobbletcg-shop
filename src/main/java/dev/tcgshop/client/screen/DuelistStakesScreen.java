package dev.tcgshop.client.screen;

import dev.tcgshop.client.binder.Ui;
import dev.tcgshop.network.UiPayloads;
import dev.tcgshop.pricing.PriceService;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * 1.0 · Wandering Duelist: the stakes, shown clearly before the match (Accept / Decline). What you win, the stake he
 * holds as soon as you accept (all the cash you carry up to the cap, kept if you lose; with no cash one random inventory
 * stack, taken only if you lose), the minimum stake and the rules. The server revalidates everything
 * (WanderingDuelistEntity.answer → WanderingDuelists.holdStake).
 */
public class DuelistStakesScreen extends Screen {
    private final int id;
    private final String name;
    private final long prize, cap, cash, take, min;
    private final double opened = System.nanoTime() / 1e9;
    private int[] acceptRect, declineRect;

    public DuelistStakesScreen(NbtCompound d) {
        super(Text.translatable("tcgshop.duelist.ui.title"));
        id = d.getInt("id");
        name = d.getString("name");
        prize = d.getLong("prize");
        cap = d.getLong("cap");
        cash = d.getLong("cash");
        take = d.getLong("take");
        min = d.getLong("min");
    }

    /** Carries some cash but less than the minimum stake: he will not play (no cash at all is fine). */
    private boolean tooLittle() { return cash > 0 && cash < min; }

    @Override
    public boolean shouldPause() { return false; }

    private List<OrderedText> wrap(Text t, int w) {
        return new ArrayList<>(textRenderer.wrapLines(t, w));
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        double t = System.nanoTime() / 1e9;
        float in = (float) MathHelper.clamp((t - opened) / 0.35, 0, 1);
        ctx.fillGradient(0, 0, width, height, 0x90101018, 0xC0080810);
        int w = Math.min(310, width - 20), x = (width - w) / 2, iw = w - 24;
        // text blocks (measured first so the panel fits them)
        List<OrderedText> intro = wrap(Text.translatable("tcgshop.duelist.ui.intro").formatted(Formatting.ITALIC), iw);
        List<OrderedText> lose = wrap(Text.translatable("tcgshop.duelist.ui.lose", PriceService.format(cap)), iw - 12);
        Text nowT = tooLittle() ? Text.translatable("tcgshop.duelist.ui.lose_short", PriceService.format(cash), PriceService.format(min))
                : cash > 0 ? Text.translatable("tcgshop.duelist.ui.lose_now", PriceService.format(cash), PriceService.format(take))
                : Text.translatable("tcgshop.duelist.ui.lose_none");
        List<OrderedText> now = wrap(nowT, iw - 12);
        List<OrderedText> item = wrap(Text.translatable("tcgshop.duelist.ui.lose_item"), iw - 12);
        List<OrderedText> rules = wrap(Text.translatable("tcgshop.duelist.ui.rules"), iw);
        int winH = 30, loseH = 16 + 10 * (lose.size() + now.size() + item.size()) + 4;
        int h = 26 + 14 + 10 * intro.size() + 6 + winH + 6 + loseH + 6 + 10 * rules.size() + 8 + 26;
        int y = Math.max(4, (height - h) / 2);
        ctx.fill(x + 3, y + 4, x + w + 3, y + h + 4, 0x66000000);
        Ui.roundGradient(ctx, x, y, w, h, 0xF0221A24, 0xF0120E14);
        Ui.roundGradient(ctx, x, y, w, 22, 0xFF7A2A3A, 0xFF4A1422);
        Draw.scaledCentered(ctx, Text.translatable("tcgshop.duelist.ui.title").formatted(Formatting.BOLD), x + w / 2f, y + 7, 1f, 0xFFFFFFFF);
        int ty = y + 28, tx = x + 12;
        // who and how hard
        ctx.drawTextWithShadow(textRenderer, Text.literal(name).formatted(Formatting.YELLOW), tx, ty, 0xFFFFFFFF);
        Text lvl = Text.translatable("tcgshop.duelist.ui.level", Text.translatable("tcgshop.duelist.ui.hard").formatted(Formatting.RED, Formatting.BOLD));
        ctx.drawTextWithShadow(textRenderer, lvl, x + w - 12 - textRenderer.getWidth(lvl), ty, 0xFFDDD6C8);
        ty += 14;
        for (OrderedText l : intro) { ctx.drawTextWithShadow(textRenderer, l, tx, ty, 0xFFDDD6C8); ty += 10; }
        ty += 6;
        // if you win
        Ui.roundGradient(ctx, tx - 2, ty, iw + 4, winH, 0xC0204A28, 0xC0142E18);
        Draw.glow(ctx, x + w / 2f, ty + winH / 2f, iw * 0.9f, winH * 1.4f, Ui.GOLD, (0.10f + 0.05f * (float) Math.sin(t * 2.5)) * in);
        ctx.drawTextWithShadow(textRenderer, Text.translatable("tcgshop.duelist.ui.if_win").formatted(Formatting.GREEN, Formatting.BOLD), tx + 4, ty + 4, 0xFFFFFFFF);
        Text win = (take > 0 && !tooLittle() ? Text.translatable("tcgshop.duelist.ui.win_back", PriceService.format(prize))
                : Text.translatable("tcgshop.duelist.ui.win", PriceService.format(prize))).formatted(Formatting.BOLD);
        ctx.drawTextWithShadow(textRenderer, win, tx + 4, ty + 17, Ui.GOLD);
        ty += winH + 6;
        // if you lose
        Ui.roundGradient(ctx, tx - 2, ty, iw + 4, loseH, 0xC05A1A1A, 0xC0341010);
        ctx.drawTextWithShadow(textRenderer, Text.translatable("tcgshop.duelist.ui.if_lose").formatted(Formatting.RED, Formatting.BOLD), tx + 4, ty + 4, 0xFFFFFFFF);
        int ly = ty + 16;
        for (OrderedText l : lose) { ctx.drawTextWithShadow(textRenderer, l, tx + 8, ly, 0xFFF0D0D0); ly += 10; }
        for (OrderedText l : now) { ctx.drawTextWithShadow(textRenderer, l, tx + 8, ly, cash > 0 ? 0xFFFF8A80 : 0xFFB8B8C8); ly += 10; }
        for (OrderedText l : item) { ctx.drawTextWithShadow(textRenderer, l, tx + 8, ly, cash > 0 ? 0xFF9A8A8A : 0xFFFF8A80); ly += 10; }
        ty += loseH + 6;
        for (OrderedText l : rules) { ctx.drawTextWithShadow(textRenderer, l, tx, ty, 0xFF8A8478); ty += 10; }
        // buttons
        int bw = (w - 30) / 2, by = y + h - 26;
        acceptRect = new int[]{x + 10, by, bw, 18};
        declineRect = new int[]{x + 20 + bw, by, bw, 18};
        Ui.button(ctx, acceptRect[0], by, bw, 18, Text.translatable("tcgshop.duelist.ui.accept"), mx, my, !tooLittle(), 0xFF3A9A4A);
        Ui.button(ctx, declineRect[0], by, bw, 18, Text.translatable("tcgshop.duelist.ui.decline"), mx, my, true, 0xFF8A1F1F);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        String action = null;
        if (acceptRect != null && !tooLittle() && Ui.in(mouseX, mouseY, acceptRect[0], acceptRect[1], acceptRect[2], acceptRect[3])) action = "accept";
        else if (declineRect != null && Ui.in(mouseX, mouseY, declineRect[0], declineRect[1], declineRect[2], declineRect[3])) action = "decline";
        if (action == null) return super.mouseClicked(mouseX, mouseY, button);
        send(action);
        close();
        return true;
    }

    private void send(String action) {
        NbtCompound d = new NbtCompound();
        d.putInt("id", id);
        d.putString("action", action);
        ClientPlayNetworking.send(new UiPayloads.Act("duelist", d));
    }
}

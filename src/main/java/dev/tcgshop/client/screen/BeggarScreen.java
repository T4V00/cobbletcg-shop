package dev.tcgshop.client.screen;

import dev.tcgshop.client.binder.Ui;
import dev.tcgshop.network.UiPayloads;
import dev.tcgshop.pricing.PriceService;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

/** Mendigo de cartas: darle monedas, darle la carta que llevas en la mano, decirle que no o echarle. */
public class BeggarScreen extends Screen {
    private final int id;
    private final String name;
    private final boolean hasCard;
    private final List<int[]> rects = new ArrayList<>();
    private final List<String> actions = new ArrayList<>();

    public BeggarScreen(NbtCompound d) {
        super(Text.translatable("tcgshop.beggar.title"));
        id = d.getInt("id");
        name = d.getString("name");
        hasCard = d.getBoolean("card");
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        rects.clear();
        actions.clear();
        int w = 260, h = 172, x = (width - w) / 2, y = (height - h) / 2;
        ctx.fill(x + 3, y + 4, x + w + 3, y + h + 4, 0x66000000);
        Ui.roundGradient(ctx, x, y, w, h, 0xF02A2620, 0xF0161410);
        Ui.roundGradient(ctx, x, y, w, 22, 0xFF7A6A52, 0xFF4A4038);
        Draw.scaledCentered(ctx, Text.translatable("tcgshop.beggar.title").formatted(Formatting.BOLD), x + w / 2f, y + 7, 1f, 0xFFFFFFFF);
        ctx.drawTextWithShadow(textRenderer, Text.literal(name).formatted(Formatting.YELLOW), x + 10, y + 28, 0xFFFFFFFF);
        int ty = y + 40;
        for (var l : textRenderer.wrapLines(Text.translatable("tcgshop.beggar.ask").formatted(Formatting.ITALIC), w - 20)) {
            ctx.drawTextWithShadow(textRenderer, l, x + 10, ty, 0xFFDDD6C8);
            ty += 10;
        }
        int by = y + h - 92, bw = (w - 28) / 3;
        btn(ctx, x + 10, by, bw, 16, Text.literal(PriceService.format(50)), 0xFF5A7A3A, "give_50", mx, my);
        btn(ctx, x + 14 + bw, by, bw, 16, Text.literal(PriceService.format(250)), 0xFF4A8A3A, "give_250", mx, my);
        btn(ctx, x + 18 + bw * 2, by, bw, 16, Text.literal(PriceService.format(1000)), 0xFF3A9A4A, "give_1000", mx, my);
        by += 20;
        if (hasCard) btn(ctx, x + 10, by, w - 20, 16, Text.translatable("tcgshop.beggar.give_card"), 0xFF3A6FD8, "give_card", mx, my);
        else Ui.text(ctx, Text.translatable("tcgshop.beggar.hold_card"), x, by + 4, w, 10, 0xFF8A8478, false);
        by += 24;
        int bw2 = (w - 24) / 2;
        btn(ctx, x + 10, by, bw2, 16, Text.translatable("tcgshop.beggar.polite"), 0xFF4A4A58, "polite", mx, my);
        btn(ctx, x + 14 + bw2, by, bw2, 16, Text.translatable("tcgshop.beggar.rude"), 0xFF8A1F1F, "rude", mx, my);
        Ui.text(ctx, Text.translatable("tcgshop.beggar.hint"), x, y + h - 14, w, 10, 0xFF8A8478, false);
    }

    private void btn(DrawContext ctx, int x, int y, int w, int h, Text label, int color, String action, int mx, int my) {
        Ui.button(ctx, x, y, w, h, label, mx, my, true, color);
        rects.add(new int[]{x, y, w, h});
        actions.add(action);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (int i = 0; i < rects.size(); i++) {
            int[] r = rects.get(i);
            if (!Ui.in(mouseX, mouseY, r[0], r[1], r[2], r[3])) continue;
            NbtCompound d = new NbtCompound();
            d.putInt("id", id);
            d.putString("action", actions.get(i));
            ClientPlayNetworking.send(new UiPayloads.Act("beggar", d));
            close();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}

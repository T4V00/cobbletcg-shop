package dev.tcgshop.client.screen;

import dev.tcgshop.client.binder.Ui;
import dev.tcgshop.client.phone.PhoneClient;
import dev.tcgshop.client.render.ShopRenderers;
import dev.tcgshop.shop.BoosterBox;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/**
 * 1.1 Box-Opening Gloves: "Open the whole box?" asked when right-clicking a booster box while wearing them. Yes opens
 * every pack of the box at once (or of every box in the stack); No gives the 36 sealed packs as usual. Esc = nothing.
 * The server re-checks everything (dev.tcgshop.pack.BoxGloves).
 */
public class BoxGlovesScreen extends Screen {
    private record Area(int x, int y, int w, int h, Runnable action) {}

    private final String set;
    private final int hand, count, packs;
    private final boolean binder;
    private final List<Area> areas = new ArrayList<>();

    public BoxGlovesScreen(CompoundTag d) {
        super(Component.translatable("tcgshop.box_gloves.title"));
        this.set = d.getString("set");
        this.hand = d.getInt("hand");
        this.count = Math.max(1, d.getInt("count"));
        this.packs = Math.max(1, d.getInt("packs"));
        this.binder = !d.contains("binder") || d.getBoolean("binder"); // review fix: whole boxes need a General Binder
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        areas.clear();
        ctx.fillGradient(0, 0, width, height, 0xB0101018, 0xD0050508);
        int w = 250, h = count > 1 ? 168 : 146, x = (width - w) / 2, y = (height - h) / 2;
        Ui.leather(ctx, x, y, w, h, 0xFF3A2A5A, 0xFF1E1530);
        Ui.outline(ctx, x, y, w, h, 0xFFE5B21E);
        Draw.scaledCentered(ctx, title.copy().withStyle(ChatFormatting.BOLD), x + w / 2f, y + 8, 1.2f, Ui.GOLD);
        // the box art
        int bw = 60, bh = 38, bx = x + (w - bw) / 2, by = y + 24;
        Draw.texture(ctx, ShopRenderers.packTextureForSet(set), bx, by, bw, bh, 1f);
        Ui.outline(ctx, bx, by, bw, bh, 0xFFFFFFFF);
        Component name = BoosterBox.of(set).getHoverName();
        Ui.text(ctx, name, x + 6, by + bh + 4, w - 12, 10, Ui.TEXT, true);
        if (binder) Ui.text(ctx, Component.translatable("tcgshop.box_gloves.question", packs), x + 6, by + bh + 16, w - 12, 10, 0xFFDDE3F0, false);
        else Ui.text(ctx, Component.translatable("tcgshop.box_gloves.need_binder"), x + 6, by + bh + 16, w - 12, 10, 0xFFFF7070, false);
        int ry = by + bh + 32, bwid = w - 24;
        button(ctx, x + 12, ry, bwid, 18, Component.translatable("tcgshop.box_gloves.yes", packs), binder ? 0xFF2E9C5A : 0xFF555A66, mouseX, mouseY, 1);
        ry += 22;
        if (count > 1) {
            int n = Math.min(dev.tcgshop.pack.BoxGloves.MAX_BOXES, count);
            button(ctx, x + 12, ry, bwid, 18, Component.translatable("tcgshop.box_gloves.all", n, n * packs), binder ? 0xFFB8701E : 0xFF555A66, mouseX, mouseY, 2);
            ry += 22;
        }
        button(ctx, x + 12, ry, bwid, 16, Component.translatable("tcgshop.box_gloves.no", packs), 0xFF4A5070, mouseX, mouseY, 0);
        Draw.scaledCentered(ctx, Component.translatable("tcgshop.box_gloves.esc"), x + w / 2f, y + h - 10, 0.75f, Ui.MUTED);
    }

    private void button(GuiGraphics ctx, int x, int y, int w, int h, Component label, int color, int mx, int my, int mode) {
        Ui.button(ctx, x, y, w, h, label, mx, my, true, color);
        areas.add(new Area(x, y, w, h, () -> {
            CompoundTag d = new CompoundTag();
            d.putString("a", "box_open");
            d.putString("set", set);
            d.putInt("hand", hand);
            d.putInt("mode", mode);
            PhoneClient.send(d);
            onClose();
        }));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (Area a : areas) if (Ui.in(mouseX, mouseY, a.x, a.y, a.w, a.h)) { a.action.run(); return true; }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}

package dev.tcgshop.client.screen;

import dev.tcgshop.client.binder.Ui;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * 1.1: the CREATive Engineer's stall (entity/EngineerEntity): his Create catalog in the same product grid as the
 * Wholesaler (CatalogPanel: search, price sort, quantity, buy). Basic materials are whole stacks (the "x64" mark).
 * Purchases go through StoreBuyPayload with his entity id as the source; the result notices are the phone's.
 */
public class EngineerScreen extends Screen {
    private final CatalogPanel panel;
    private EditBox search;

    public EngineerScreen(CompoundTag d) {
        super(Component.translatable("entity.tcgshop.engineer"));
        List<ItemStack> goods = new ArrayList<>();
        List<Long> prices = new ArrayList<>();
        var level = net.minecraft.client.Minecraft.getInstance().level;
        ListTag l = d.getList("goods", 10);
        long[] pr = d.getLongArray("prices");
        for (int i = 0; i < l.size() && i < pr.length; i++) {
            ItemStack st = level == null ? ItemStack.EMPTY : ItemStack.parse(level.registryAccess(), l.getCompound(i)).orElse(ItemStack.EMPTY);
            goods.add(st); // keeps the indexes aligned with the server's catalog even if one fails to parse
            prices.add(pr[i]);
        }
        panel = new CatalogPanel(d.getInt("id"), goods, prices);
    }

    @Override
    protected void init() {
        int w = Math.min(width - 20, 460);
        search = new EditBox(font, (width - w) / 2 + w - 130, 26, 124, 14, Component.translatable("tcgshop.binder.search"));
        search.setHint(Component.translatable("tcgshop.binder.search").withStyle(ChatFormatting.DARK_GRAY));
        search.setResponder(s -> panel.filter = s);
        addWidget(search);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        boolean overNotice = dev.tcgshop.client.phone.PhoneNotices.contains(mouseX, mouseY);
        int mx = overNotice ? -9999 : mouseX, my = overNotice ? -9999 : mouseY;
        ctx.fillGradient(0, 0, width, height, 0xB0101418, 0xD0050708);
        int w = Math.min(width - 20, 460), h = height - 20, x = (width - w) / 2, y = 10;
        // brass-and-steel workshop look (Create-like colors, our own drawing)
        Ui.leather(ctx, x, y, w, h, 0xFF8A6A3A, 0xFF4A3A22);
        ctx.fill(x + 6, y + 32, x + w - 6, y + 34, 0xFFC9A24A);
        ctx.drawString(font, title.copy().withStyle(ChatFormatting.BOLD), x + 10, y + 8, 0xFFFFD98A);
        ctx.drawString(font, Component.translatable("tcgshop.engineer.subtitle"), x + 10, y + 19, 0xFFE8D8B8, false);
        search.render(ctx, mx, my, delta);
        ctx.fill(x + 6, y + 36, x + w - 6, y + h - 6, 0xFF1C1F24);
        panel.beginFrame();
        int fy = y + 40;
        Ui.text(ctx, Component.translatable("tcgshop.engineer.hint"), x + 10, fy, w - 20, 10, 0xFFB8AE98, false);
        panel.render(ctx, x + 10, fy + 14, w - 20, h - 64, mx, my, System.nanoTime() / 1e9);
        int nw = Math.min(w - 24, 260);
        ctx.enableScissor(x + 6, y + 36, x + w - 6, y + h - 6);
        dev.tcgshop.client.phone.PhoneNotices.render(ctx, (width - nw) / 2, y + 40, nw, mouseX, mouseY);
        ctx.disableScissor();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (dev.tcgshop.client.phone.PhoneNotices.click(mouseX, mouseY)) return true;
        if (search.mouseClicked(mouseX, mouseY, button)) { setFocused(search); return true; }
        search.setFocused(false);
        return panel.click(mouseX, mouseY) || super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double h, double v) {
        if (dev.tcgshop.client.phone.PhoneNotices.contains(mouseX, mouseY)) return true;
        panel.scroll(v);
        return true;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        return search.isFocused() && search.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (search.isFocused() && keyCode != GLFW.GLFW_KEY_ESCAPE) return search.keyPressed(keyCode, scanCode, modifiers) || true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}

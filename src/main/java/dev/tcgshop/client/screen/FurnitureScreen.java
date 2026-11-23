package dev.tcgshop.client.screen;

import dev.tcgshop.client.binder.Ui;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Furniture Seller's stall: CobbleFurnies furniture and plushies, buying and selling to him (FurnitureApp panel), and the
 * luxury statues in the Exclusives tab (header button).
 */
public class FurnitureScreen extends Screen {
    private final FurnitureApp app;
    private final int source;
    /** "★ Exclusives" header button (jumps to the luxury statues); x, y, w, h from the last frame. */
    private int exX, exY, exW, exH;

    public FurnitureScreen(int source) {
        super(Text.translatable("entity.tcgshop.furniture_seller"));
        this.source = source;
        this.app = new FurnitureApp(source, this);
    }

    FurnitureApp app() { return app; }

    public int source() { return source; }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fillGradient(0, 0, width, height, 0xB0101018, 0xD0050508);
        int w = Math.min(width - 20, 520), h = Math.min(height - 20, 330), x = (width - w) / 2, y = (height - h) / 2;
        // warm wood: it's a furniture shop
        Ui.leather(ctx, x, y, w, h, 0xFF6B4626, 0xFF3A2411);
        ctx.fill(x + 6, y + 31, x + w - 6, y + 32, 0x55FFD9A0);
        ctx.drawItem(FurnitureApp.icon(), x + 9, y + 8);
        ctx.drawTextWithShadow(textRenderer, title.copy().formatted(Formatting.BOLD), x + 30, y + 7, Ui.GOLD);
        ctx.drawText(textRenderer, Text.translatable("tcgshop.furn.subtitle"), x + 30, y + 19, 0xFFE8D2B4, false);
        exW = 0;
        if (app.hasLuxury()) { // based on the server's catalog, not the client's local config
            Text ex = Text.translatable("tcgshop.lux.tab_button");
            exW = Ui.chipWidth(ex) + 6;
            exH = 16;
            exX = x + w - exW - 8;
            exY = y + 8;
            Ui.chip(ctx, exX, exY, exW, exH, ex, false, Ui.in(mouseX, mouseY, exX, exY, exW, exH), 0xFFD15CFF, 0);
        }
        app.render(ctx, x + 6, y + 36, w - 12, h - 42, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && exW > 0 && Ui.in(mouseX, mouseY, exX, exY, exW, exH)) {
            app.showCategory("luxury");
            return true;
        }
        return app.mouseClicked(mouseX, mouseY, button) || super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        return app.mouseScrolled(mouseX, mouseY, vertical) || super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        return app.charTyped(chr, modifiers) || super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (app.keyPressed(keyCode, scanCode, modifiers)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}

package dev.tcgshop.client.screen;

import dev.masterball.api.platform.Platform;
import dev.masterball.api.platform.ClientPlatform;
import dev.tcgshop.client.binder.Ui;
import dev.tcgshop.network.FurniturePayloads;
import dev.tcgshop.pricing.PriceService;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.network.chat.Component;
import java.util.List;

/**
 * Furniture Seller's stall: CobbleFurnies furniture and plushies, buying and selling to him (FurnitureApp panel), and the
 * luxury statues in the Exclusives tab (header button).
 */
public class FurnitureScreen extends Screen {
    private final FurnitureApp app;
    private final int source;
    /** "★ Exclusives" header button (jumps to the luxury statues); x, y, w, h from the last frame. */
    private int exX, exY, exW, exH;
    /** 1.0: "Sell Mega Stones" header button (mgW = 0: hidden), what it showed, and when it was armed (2nd click confirms). */
    private int mgX, mgY, mgW, mgH, megaCount;
    private long megaUnit;
    private double megaArmedAt = -10;

    public FurnitureScreen(int source) {
        super(Component.translatable("entity.tcgshop.furniture_seller"));
        this.source = source;
        this.app = new FurnitureApp(source, this);
    }

    FurnitureApp app() { return app; }

    public int source() { return source; }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        ctx.fillGradient(0, 0, width, height, 0xB0101018, 0xD0050508);
        int w = Math.min(width - 20, 520), h = Math.min(height - 20, 330), x = (width - w) / 2, y = (height - h) / 2;
        // warm wood: it's a furniture shop
        Ui.leather(ctx, x, y, w, h, 0xFF6B4626, 0xFF3A2411);
        ctx.fill(x + 6, y + 31, x + w - 6, y + 32, 0x55FFD9A0);
        ctx.renderItem(FurnitureApp.icon(), x + 9, y + 8);
        ctx.drawString(font, title.copy().withStyle(ChatFormatting.BOLD), x + 30, y + 7, Ui.GOLD);
        ctx.drawString(font, Component.translatable("tcgshop.furn.subtitle"), x + 30, y + 19, 0xFFE8D2B4, false);
        exW = 0;
        if (app.hasLuxury()) { // based on the server's catalog, not the client's local config
            Component ex = Component.translatable("tcgshop.lux.tab_button");
            exW = Ui.chipWidth(ex) + 6;
            exH = 16;
            exX = x + w - exW - 8;
            exY = y + 8;
            Ui.chip(ctx, exX, exY, exW, exH, ex, false, Ui.in(mouseX, mouseY, exX, exY, exW, exH), 0xFFD15CFF, 0);
        }
        megaStoneButton(ctx, x, y, w, mouseX, mouseY);
        app.render(ctx, x + 6, y + 36, w - 12, h - 42, mouseX, mouseY, delta);
    }

    /** 1.0: "Sell N Mega Stone(s) · total" in the header (left of Exclusives), only while you carry any; 2 clicks to confirm. */
    private void megaStoneButton(GuiGraphics ctx, int x, int y, int w, int mouseX, int mouseY) {
        mgW = 0;
        if (minecraft == null || minecraft.player == null) return;
        megaCount = dev.tcgshop.shop.TraderGoods.countMegaStones(minecraft.player);
        megaUnit = dev.tcgshop.shop.TraderGoods.megaStonePrice();
        if (megaCount <= 0 || megaUnit <= 0) return;
        boolean armed = now() - megaArmedAt < 3;
        Component label = armed ? Component.translatable("tcgshop.sell.confirm")
                : Component.translatable("tcgshop.sell.mega_btn", megaCount, PriceService.format(megaUnit * megaCount));
        int right = exW > 0 ? exX - 6 : x + w - 8;
        int titleEnd = x + 36 + Math.max(font.width(title.copy().withStyle(ChatFormatting.BOLD)),
                font.width(Component.translatable("tcgshop.furn.subtitle")));
        mgW = Math.min(font.width(label) + 12, right - titleEnd);
        if (mgW < 40) { mgW = 0; return; }
        mgH = 16;
        mgX = right - mgW;
        mgY = y + 8;
        Ui.button(ctx, mgX, mgY, mgW, mgH, Component.literal(Ui.ellipsis(label.getString(), mgW - 8)), mouseX, mouseY, true, armed ? 0xFFB02020 : 0xFF7B4FD0);
        if (Ui.in(mouseX, mouseY, mgX, mgY, mgW, mgH))
            setTooltipForNextRenderPass(List.of(Component.translatable("tcgshop.sell.mega_hint", PriceService.format(megaUnit)).getVisualOrderText()),
                    DefaultTooltipPositioner.INSTANCE, true);
    }

    private static double now() { return System.nanoTime() / 1e9; }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && exW > 0 && Ui.in(mouseX, mouseY, exX, exY, exW, exH)) {
            app.showCategory("luxury");
            return true;
        }
        if (button == 0 && mgW > 0 && Ui.in(mouseX, mouseY, mgX, mgY, mgW, mgH)) {
            if (now() - megaArmedAt >= 3) { megaArmedAt = now(); return true; } // first click arms it
            megaArmedAt = -10;
            ClientPlatform.INSTANCE.send(new FurniturePayloads.Trade(source, dev.tcgshop.shop.TraderGoods.MEGA_TRADE_ID, megaCount, true, megaUnit));
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

package dev.tcgshop.client.screen;

import dev.masterball.api.card.CardInfo;
import dev.tcgshop.client.binder.Ui;
import dev.tcgshop.network.ScalperAnswerPayload;
import dev.tcgshop.pricing.PriceService;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** A Scalper's offer: buy the pack at an inflated price or refuse it (and it turns hostile). */
public class ScalperOfferScreen extends Screen {
    private final int entityId;
    private final ItemStack pack;
    private final long price, market;
    private boolean answered;

    public ScalperOfferScreen(int entityId, ItemStack pack, long price, long market) {
        super(Text.translatable("entity.tcgshop.scalper"));
        this.entityId = entityId;
        this.pack = pack;
        this.price = price;
        this.market = market;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private void answer(boolean accept) {
        if (answered) return;
        answered = true;
        ClientPlayNetworking.send(new ScalperAnswerPayload(entityId, accept));
        super.close();
    }

    @Override
    public void close() {
        answer(false); // close = refuse
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fillGradient(0, 0, width, height, 0x99000000, 0xCC000000);
        int w = 260, h = 150, x = (width - w) / 2, y = (height - h) / 2;
        Ui.leather(ctx, x, y, w, h, 0xFF2A1F1A, 0xFF120C09);
        Draw.scaledCentered(ctx, Text.translatable("tcgshop.scalper.offer_title").formatted(Formatting.BOLD), x + w / 2f, y + 8, 1.2f, 0xFFE0B33C);
        float ph = 90, pw = ph * Draw.PACK_RATIO;
        double t = System.nanoTime() / 1e9;
        Draw.glow(ctx, x + 14 + pw / 2, y + 30 + ph / 2, pw, ph, 0xFFE0B33C, 0.2f + 0.1f * (float) Math.sin(t * 3));
        Draw.pack(ctx, CardInfo.packTexture(CardInfo.id(pack)), pack, x + 14, y + 30, pw, ph, 1f);
        int tx = x + 24 + (int) pw, ty = y + 32, tw = w - (tx - x) - 10;
        for (var line : textRenderer.wrapLines(Text.translatable("tcgshop.scalper.offer_text", pack.getName()), tw)) {
            ctx.drawTextWithShadow(textRenderer, line, tx, ty, Ui.TEXT);
            ty += 10;
        }
        ty += 4;
        ctx.drawTextWithShadow(textRenderer, Text.translatable("tcgshop.scalper.price", PriceService.format(price)), tx, ty, 0xFFFF7A5C);
        ty += 11;
        Text mk = Text.translatable("tcgshop.scalper.market", PriceService.format(market));
        ctx.drawTextWithShadow(textRenderer, mk, tx, ty, Ui.MUTED);
        ty += 11;
        String pct = "+" + Math.round((price - market) * 100.0 / Math.max(1, market)) + "%";
        ctx.drawTextWithShadow(textRenderer, Text.translatable("tcgshop.scalper.markup", pct), tx, ty, 0xFFFF5555);
        int by = y + h - 28;
        Ui.button(ctx, x + 14, by, 110, 20, Text.translatable("tcgshop.scalper.buy", PriceService.format(price)), mouseX, mouseY, true, 0xFF8A6A1C);
        Ui.button(ctx, x + w - 124, by, 110, 20, Text.translatable("tcgshop.scalper.refuse"), mouseX, mouseY, true, 0xFF7A2020);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int w = 260, h = 150, x = (width - w) / 2, y = (height - h) / 2, by = y + h - 28;
        if (Ui.in(mouseX, mouseY, x + 14, by, 110, 20)) { answer(true); return true; }
        if (Ui.in(mouseX, mouseY, x + w - 124, by, 110, 20)) { answer(false); return true; }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}

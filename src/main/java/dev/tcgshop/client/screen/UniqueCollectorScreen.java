package dev.tcgshop.client.screen;

import dev.masterball.api.card.CardInfo;
import dev.tcgshop.client.binder.Ui;
import dev.tcgshop.network.UiPayloads;
import dev.tcgshop.pricing.PriceService;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;

/**
 * 1.0 onboarding: the unique collector's one-time offer. Shows the card he likes, its market value and his offer, with
 * Accept / Decline. The server revalidates everything (UniqueCollectorEntity.answer).
 */
public class UniqueCollectorScreen extends Screen {
    private final int id;
    private final ItemStack card;
    private final long offer, value;
    private final double opened = System.nanoTime() / 1e9;
    private int[] acceptRect, declineRect;

    public UniqueCollectorScreen(NbtCompound d) {
        super(Text.translatable("tcgshop.onb.collector.title"));
        MinecraftClient mc = MinecraftClient.getInstance();
        id = d.getInt("id");
        card = mc.world == null || !d.contains("card") ? ItemStack.EMPTY
                : ItemStack.fromNbt(mc.world.getRegistryManager(), d.get("card")).orElse(ItemStack.EMPTY);
        offer = d.getLong("offer");
        value = d.getLong("value");
    }

    @Override
    public boolean shouldPause() { return false; }

    private Text npcName() {
        MinecraftClient mc = MinecraftClient.getInstance();
        Entity e = mc.world == null ? null : mc.world.getEntityById(id);
        return e != null ? e.getDisplayName() : Text.translatable("tcgshop.onb.collector.name");
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        double t = System.nanoTime() / 1e9;
        float in = (float) MathHelper.clamp((t - opened) / 0.35, 0, 1);
        ctx.fillGradient(0, 0, width, height, 0x90101018, 0xC0080810);
        int w = Math.min(300, width - 20), h = 196, x = (width - w) / 2, y = (height - h) / 2;
        ctx.fill(x + 3, y + 4, x + w + 3, y + h + 4, 0x66000000);
        Ui.roundGradient(ctx, x, y, w, h, 0xF0262A20, 0xF0141610);
        Ui.roundGradient(ctx, x, y, w, 22, 0xFF8A7A3A, 0xFF5A4A1E);
        Draw.scaledCentered(ctx, Text.translatable("tcgshop.onb.collector.title").formatted(Formatting.BOLD), x + w / 2f, y + 7, 1f, 0xFFFFFFFF);
        // the card he likes
        int cw = 74, ch = Math.round(cw / Draw.CARD_RATIO), cx = x + 14, cy = y + 32;
        Draw.glow(ctx, cx + cw / 2f, cy + ch / 2f, cw * 1.4f, ch * 1.2f, Ui.GOLD, (0.25f + 0.1f * (float) Math.sin(t * 2.5)) * in);
        if (!card.isEmpty()) Draw.cardFace(ctx, CardInfo.texture(CardInfo.id(card)), card, cx, cy, cw, ch, in);
        Ui.outline(ctx, cx, cy, cw, ch, 0xFFFFFFFF);
        // text
        int tx = cx + cw + 14, tw = x + w - 12 - tx, ty = y + 30;
        ctx.drawTextWithShadow(textRenderer, npcName().copy().formatted(Formatting.YELLOW), tx, ty, 0xFFFFFFFF);
        ty += 13;
        for (var l : textRenderer.wrapLines(Text.translatable("tcgshop.onb.collector.ask").formatted(Formatting.ITALIC), tw)) {
            ctx.drawTextWithShadow(textRenderer, l, tx, ty, 0xFFDDD6C8);
            ty += 10;
        }
        ty += 4;
        Text name = Text.literal(Ui.ellipsis(card.isEmpty() ? "?" : card.getName().getString(), tw)).formatted(Formatting.GOLD);
        ctx.drawTextWithShadow(textRenderer, name, tx, ty, 0xFFFFFFFF);
        ty += 14;
        ctx.drawTextWithShadow(textRenderer, Text.translatable("tcgshop.onb.collector.value", PriceService.format(value)), tx, ty, 0xFFB8B8C8);
        ty += 12;
        ctx.drawTextWithShadow(textRenderer, Text.translatable("tcgshop.onb.collector.offer", PriceService.format(offer)).formatted(Formatting.BOLD), tx, ty, Ui.GOLD);
        // note + buttons
        int ny = y + h - 52;
        for (var l : textRenderer.wrapLines(Text.translatable("tcgshop.onb.collector.once"), w - 24)) {
            ctx.drawCenteredTextWithShadow(textRenderer, l, x + w / 2, ny, 0xFF8A8478);
            ny += 10;
        }
        int bw = (w - 30) / 2, by = y + h - 26;
        acceptRect = new int[]{x + 10, by, bw, 18};
        declineRect = new int[]{x + 20 + bw, by, bw, 18};
        Ui.button(ctx, acceptRect[0], by, bw, 18, Text.translatable("tcgshop.onb.collector.accept_btn"), mx, my, !card.isEmpty(), 0xFF3A9A4A);
        Ui.button(ctx, declineRect[0], by, bw, 18, Text.translatable("tcgshop.onb.collector.decline_btn"), mx, my, true, 0xFF8A1F1F);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        String action = null;
        if (acceptRect != null && !card.isEmpty() && Ui.in(mouseX, mouseY, acceptRect[0], acceptRect[1], acceptRect[2], acceptRect[3])) action = "accept";
        else if (declineRect != null && Ui.in(mouseX, mouseY, declineRect[0], declineRect[1], declineRect[2], declineRect[3])) action = "decline";
        if (action == null) return super.mouseClicked(mouseX, mouseY, button);
        NbtCompound d = new NbtCompound();
        d.putInt("id", id);
        d.putString("action", action);
        ClientPlayNetworking.send(new UiPayloads.Act("unique_collector", d));
        close();
        return true;
    }
}

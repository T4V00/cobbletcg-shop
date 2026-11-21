package dev.tcgshop.client.screen;

import dev.tcgshop.card.CardInfo;
import dev.tcgshop.card.Rarity;
import dev.tcgshop.client.binder.Ui;
import dev.tcgshop.network.TradePayloads;
import dev.tcgshop.pricing.PriceService;
import dev.tcgshop.trade.TradeManager;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;

/** Mesa de intercambio: tu carta a la izquierda, la suya a la derecha, tus cartas intercambiables abajo. */
public class TradeScreen extends Screen {
    private record Area(int x, int y, int w, int h, Runnable action) {}

    private final String partner;
    private final List<Area> areas = new ArrayList<>();
    private ItemStack mine = ItemStack.EMPTY, theirs = ItemStack.EMPTY;
    private boolean myOk, theirOk, finished, success;
    private String message = "";
    private double messageAt = -10, doneAt = -10, scroll;
    private ItemStack gave = ItemStack.EMPTY, got = ItemStack.EMPTY;

    public TradeScreen(String partner) {
        super(Text.translatable("tcgshop.trade.title"));
        this.partner = partner;
    }

    @Override
    public boolean shouldPause() { return false; }

    public void onState(TradePayloads.State s) {
        if (!theirs.isEmpty() && !ItemStack.areItemsAndComponentsEqual(theirs, s.theirs()) || theirs.isEmpty() && !s.theirs().isEmpty()) ding(1.2f);
        mine = s.mine();
        theirs = s.theirs();
        myOk = s.myOk();
        theirOk = s.theirOk();
        if (!s.message().isEmpty()) { message = s.message(); messageAt = now(); }
    }

    public void onDone(TradePayloads.Done d) {
        finished = true;
        success = d.success();
        gave = d.gave();
        got = d.got();
        message = d.message();
        messageAt = doneAt = now();
        if (success) ding(1.6f);
    }

    private void ding(float pitch) {
        if (client != null) client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), pitch, 0.6f));
    }

    private static double now() { return System.nanoTime() / 1e9; }

    private Text messageText() {
        String[] parts = message.split("\\|", 2);
        return parts.length > 1 ? Text.translatable(parts[0], parts[1]) : Text.translatable(parts[0]);
    }

    @Override
    public void tick() {
        if (finished && now() - doneAt > (success ? 2.6 : 1.6)) super.close();
    }

    @Override
    public void close() {
        if (!finished) ClientPlayNetworking.send(new TradePayloads.Answer(false));
        super.close();
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        areas.clear();
        double t = now();
        ctx.fillGradient(0, 0, width, height, 0xC0081020, 0xE0030610);
        int w = Math.min(width - 16, 420), h = Math.min(height - 16, 300), x = (width - w) / 2, y = (height - h) / 2;
        Ui.leather(ctx, x, y, w, h, 0xFF23406E, 0xFF101E36);
        Draw.scaledCentered(ctx, title.copy().formatted(Formatting.BOLD), width / 2f, y + 7, 1.3f, Ui.GOLD);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.translatable("tcgshop.trade.with", partner), width / 2, y + 21, 0xFFCCD6FF);

        int ch = Math.min(118, h / 2 - 20), cw = Math.round(ch * Draw.CARD_RATIO);
        int cy = y + 34;
        int lx = x + w / 4 - cw / 2, rx = x + 3 * w / 4 - cw / 2;
        if (finished && success) {
            // animación: las cartas cruzan la mesa
            double p = Draw.easeOutCubic(MathHelper.clamp((t - doneAt) / 1.1, 0, 1));
            int ax = (int) MathHelper.lerp(p, lx, rx), bx = (int) MathHelper.lerp(p, rx, lx);
            int arc = (int) (Math.sin(p * Math.PI) * 20);
            slot(ctx, gave, ax, cy - arc, cw, ch, t, mouseX, false);
            slot(ctx, got, bx, cy + arc, cw, ch, t, mouseX, false);
            if (p >= 1) Draw.scaledCentered(ctx, messageText().copy().formatted(Formatting.GREEN, Formatting.BOLD), width / 2f, cy + ch + 10, 1.3f, 0xFFFFFFFF);
            return;
        }
        ctx.drawCenteredTextWithShadow(textRenderer, Text.translatable("tcgshop.trade.you"), lx + cw / 2, cy - 10, 0xFFFFFFFF);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(partner), rx + cw / 2, cy - 10, 0xFFFFFFFF);
        slot(ctx, mine, lx, cy, cw, ch, t, mouseX, myOk);
        slot(ctx, theirs, rx, cy, cw, ch, t, mouseX, theirOk);
        // indicador central
        boolean both = !mine.isEmpty() && !theirs.isEmpty();
        boolean same = both && Rarity.of(mine) == Rarity.of(theirs);
        int mx = width / 2;
        float pulse = (float) (0.6 + 0.4 * Math.sin(t * 4));
        Draw.scaledCentered(ctx, Text.literal("⇄").formatted(Formatting.BOLD), mx, cy + ch / 2f - 10, 2.4f,
                both ? (same ? Draw.withAlpha(0xFF6BE07B, pulse) : 0xFFFF6B6B) : 0xFF6A7390);
        if (both) Ui.text(ctx, Text.translatable(same ? "tcgshop.trade.match" : "tcgshop.trade.same_rarity"), mx - 50, cy + ch / 2 + 14, 100, 10,
                same ? 0xFF6BE07B : 0xFFFF6B6B, false);

        // botones
        int by = cy + ch + 8;
        boolean canConfirm = same && !myOk && !finished;
        Ui.button(ctx, mx - 104, by, 100, 18, Text.translatable(myOk ? "tcgshop.trade.waiting" : "tcgshop.trade.confirm"), mouseX, mouseY, canConfirm, 0xFF3C8A4A);
        if (canConfirm) areas.add(new Area(mx - 104, by, 100, 18, () -> ClientPlayNetworking.send(new TradePayloads.Answer(true))));
        Ui.button(ctx, mx + 4, by, 100, 18, Text.translatable("tcgshop.trade.cancel"), mouseX, mouseY, !finished, 0xFF7A2020);
        if (!finished) areas.add(new Area(mx + 4, by, 100, 18, this::close));

        // tus cartas intercambiables
        int gy = by + 24, gh = y + h - 8 - gy;
        ctx.fill(x + 8, gy, x + w - 8, gy + gh, 0x55000000);
        ctx.drawText(textRenderer, Text.translatable("tcgshop.trade.pick"), x + 12, gy + 3, 0xFFCCCCCC, false);
        List<Integer> slots = new ArrayList<>();
        if (client != null && client.player != null)
            for (int i = 0; i < 36; i++) if (TradeManager.tradeable(client.player.getInventory().getStack(i))) slots.add(i);
            for (int bsl : dev.tcgshop.client.binder.ClientBinderSlots.slots())
                if (TradeManager.tradeable(CardPickGrid.stackAt(client.player, bsl))) slots.add(bsl); // y las del Binder
        int th = Math.min(52, gh - 16), tw = Math.round(th * Draw.CARD_RATIO), gap = 4;
        int cols = Math.max(1, (w - 24 + gap) / (tw + gap));
        int rows = (slots.size() + cols - 1) / cols;
        scroll = MathHelper.clamp(scroll, 0, Math.max(0, rows * (th + gap) - (gh - 14)));
        ctx.enableScissor(x + 8, gy + 13, x + w - 8, gy + gh);
        for (int k = 0; k < slots.size(); k++) {
            int s = slots.get(k);
            ItemStack st = CardPickGrid.stackAt(client.player, s);
            int tx = x + 12 + (k % cols) * (tw + gap), ty = gy + 14 + (k / cols) * (th + gap) - (int) scroll;
            if (ty + th < gy + 13 || ty > gy + gh) continue;
            boolean hover = Ui.in(mouseX, mouseY, tx, ty, tw, th) && mouseY >= gy + 13 && mouseY <= gy + gh;
            boolean picked = ItemStack.areItemsAndComponentsEqual(st, mine);
            Rarity r = Rarity.of(st);
            int lift = hover ? 2 : 0;
            Draw.cardFace(ctx, CardInfo.texture(CardInfo.id(st)), st, tx, ty - lift, tw, th, 1f);
            Ui.outline(ctx, tx - 1, ty - 1 - lift, tw + 2, th + 2, picked ? Ui.GOLD : hover ? 0xFFFFFFFF : r.color);
            if (st.getCount() > 1) ctx.drawText(textRenderer, "x" + st.getCount(), tx + tw - 13, ty + th - 9 - lift, 0xFFFFFFFF, true);
            if (hover) ctx.drawTooltip(textRenderer, List.of(st.getName(), r.displayName().copy().withColor(r.color),
                    Text.literal(PriceService.format(PriceService.priceCd(st))).formatted(Formatting.GOLD)), mouseX, mouseY);
            if (hover && !finished) areas.add(new Area(tx, ty, tw, th, () -> ClientPlayNetworking.send(new TradePayloads.Select(s))));
        }
        ctx.disableScissor();
        if (slots.isEmpty()) Ui.text(ctx, Text.translatable("tcgshop.trade.none"), x + 8, gy + gh / 2 - 4, w - 16, 10, Ui.MUTED, false);

        if (!message.isEmpty() && t - messageAt < 3) {
            Text m = messageText();
            int mw = textRenderer.getWidth(m) + 12;
            Ui.round(ctx, width / 2 - mw / 2, y + h - 22, mw, 14, 0xE0101010);
            ctx.drawCenteredTextWithShadow(textRenderer, m, width / 2, y + h - 19, finished && !success ? 0xFFFF6B6B : 0xFFFFD34D);
        }
    }

    private void slot(DrawContext ctx, ItemStack st, int x, int y, int w, int h, double t, int mouseX, boolean ok) {
        if (st.isEmpty()) {
            Ui.round(ctx, x, y, w, h, 0x33000000);
            Ui.outline(ctx, x, y, w, h, 0xFF4A5068);
            Ui.text(ctx, Text.literal("?"), x, y, w, h, 0xFF6A7390, true);
            return;
        }
        Rarity r = Rarity.of(st);
        if (r.tier > 0) Draw.glow(ctx, x + w / 2f, y + h / 2f, w, h, r.color, 0.3f);
        Draw.cardFace(ctx, CardInfo.texture(CardInfo.id(st)), st, x, y, w, h, 1f);
        if (r.tier > 0) Draw.holo(ctx, x, y, w, h, r.tier, r.color, t, mouseX);
        Ui.text(ctx, r.displayName(), x - 20, y + h + 2, w + 40, 10, r.color, false);
        if (ok) {
            Ui.round(ctx, x + w - 16, y - 6, 20, 20, 0xFF3CB043);
            Ui.text(ctx, Text.literal("✔").formatted(Formatting.BOLD), x + w - 16, y - 6, 20, 20, 0xFFFFFFFF, true);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (int i = areas.size() - 1; i >= 0; i--) {
            Area a = areas.get(i);
            if (Ui.in(mouseX, mouseY, a.x, a.y, a.w, a.h)) { a.action.run(); return true; }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double h, double v) {
        scroll -= v * 20;
        return true;
    }
}

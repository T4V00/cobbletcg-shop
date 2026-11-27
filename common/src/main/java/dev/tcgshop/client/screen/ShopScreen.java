package dev.tcgshop.client.screen;

import dev.masterball.api.platform.Platform;
import dev.masterball.api.platform.ClientPlatform;
import dev.masterball.api.card.CardInfo;
import dev.masterball.api.card.Rarity;
import dev.tcgshop.client.binder.Ui;
import dev.tcgshop.client.render.ShopRenderers;
import dev.tcgshop.network.ShopActionPayload;
import dev.tcgshop.network.ShopViewPayload;
import dev.tcgshop.pricing.PriceService;
import dev.tcgshop.shop.TcgBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** Shop furniture: the customer buys; the owner changes prices (with the market price as reference) or withdraws. */
public class ShopScreen extends Screen {
    private record Area(int x, int y, int w, int h, Runnable action) {}

    private ShopViewPayload data;
    private final List<Area> areas = new ArrayList<>();
    private final List<EditBox> priceFields = new ArrayList<>();

    public ShopScreen(ShopViewPayload data) {
        super(Component.translatable(data.owner() ? "tcgshop.shop.manage" : "tcgshop.shop.buy_title", data.ownerName()));
        this.data = data;
    }

    /** The server resends the contents after every action. */
    public void update(ShopViewPayload p) {
        this.data = p;
        rebuildWidgets();
    }

    public net.minecraft.core.BlockPos pos() { return data.pos(); }

    private int cellW() { return 96; }
    private int cellH() { return data.owner() ? 168 : 150; }

    private int x0() {
        int n = data.items().size(), cols = Math.min(n, 3);
        int base = (width - (cols * (cellW() + 8) - 8)) / 2;
        return data.owner() ? Math.max(86, base) : base; // 1.1: room for the markup column on the left
    }

    private int y0() {
        int rows = (data.items().size() + 2) / 3;
        return Math.max(30, (height - rows * (cellH() + 8)) / 2 + 10);
    }

    @Override
    protected void init() {
        priceFields.clear();
        if (!data.owner()) return;
        for (int i = 0; i < data.items().size(); i++) {
            int x = x0() + (i % 3) * (cellW() + 8), y = y0() + (i / 3) * (cellH() + 8);
            EditBox f = new EditBox(font, x + 6, y + cellH() - 44, cellW() - 12, 14, Component.empty());
            f.setValue(String.valueOf(data.prices().get(i)));
            f.setFilter(s -> s.matches("\\d{0,10}"));
            f.setVisible(!data.items().get(i).isEmpty());
            priceFields.add(f);
            addWidget(f);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        areas.clear();
        ctx.fillGradient(0, 0, width, height, 0xB0101018, 0xD0050508);
        Draw.scaledCentered(ctx, title.copy().withStyle(ChatFormatting.BOLD), width / 2f, y0() - 22, 1.3f, Ui.GOLD);
        double t = System.nanoTime() / 1e9;
        for (int i = 0; i < data.items().size(); i++) {
            int x = x0() + (i % 3) * (cellW() + 8), y = y0() + (i / 3) * (cellH() + 8);
            ItemStack st = data.items().get(i);
            Ui.leather(ctx, x, y, cellW(), cellH(), 0xFF2B2F42, 0xFF181B27);
            if (st.isEmpty()) {
                Ui.text(ctx, Component.translatable("tcgshop.shop.empty_slot"), x, y + cellH() / 2 - 6, cellW(), 12, Ui.MUTED, false);
                continue;
            }
            // product
            int iw = 52, ih = 72, ix = x + (cellW() - iw) / 2, iy = y + 8;
            if (CardInfo.isCard(st)) {
                Rarity r = Rarity.of(st);
                if (r.tier > 0) Draw.glow(ctx, ix + iw / 2f, iy + ih / 2f, iw, ih, r.color, 0.3f);
                Draw.cardFace(ctx, CardInfo.texture(CardInfo.id(st)), st, ix, iy, iw, ih, 1f);
                if (r.tier > 0 && Ui.in(mouseX, mouseY, ix, iy, iw, ih)) Draw.holo(ctx, ix, iy, iw, ih, r.tier, r.color, t, mouseX);
            } else if (CardInfo.isPack(st)) {
                Draw.pack(ctx, CardInfo.packTexture(CardInfo.id(st)), st, ix + (iw - ih * Draw.PACK_RATIO) / 2, iy, ih * Draw.PACK_RATIO, ih, 1f);
            } else if (st.is(TcgBlocks.BOOSTER_BOX_ITEM)) {
                String set = st.getOrDefault(TcgBlocks.BOX_SET, "mew151");
                Draw.texture(ctx, ShopRenderers.packTextureForSet(set), ix - 6, iy + 14, iw + 12, ih - 28, 1f);
                Ui.outline(ctx, ix - 6, iy + 14, iw + 12, ih - 28, 0xFFFFFFFF);
            } else {
                var m = ctx.pose();
                m.pushPose();
                m.translate(x + cellW() / 2f, iy + ih / 2f, 0);
                m.scale(3, 3, 1);
                ctx.renderItem(st, -8, -8);
                m.popPose();
            }
            int ty = iy + ih + 4;
            Ui.text(ctx, st.getHoverName(), x + 4, ty, cellW() - 8, 10, Ui.TEXT, true);
            ty += 11;
            if (st.getCount() > 1) {
                Ui.text(ctx, Component.translatable("tcgshop.shop.stock", st.getCount()), x + 4, ty, cellW() - 8, 10, Ui.MUTED, false);
            }
            ty += 11;
            long market = dev.tcgshop.shop.ShopServer.priceOf(st, 1.0);
            final int slot = i;
            if (data.owner()) {
                Ui.text(ctx, Component.translatable("tcgshop.shop.market_ref", PriceService.format(market)), x + 4, ty, cellW() - 8, 10, Ui.MUTED, false);
                int by = y + cellH() - 26;
                int bw = (cellW() - 16) / 3;
                Ui.button(ctx, x + 6, by, bw, 14, Component.translatable("tcgshop.shop.set_price"), mouseX, mouseY, true, 0xFF3C8A4A);
                areas.add(new Area(x + 6, by, bw, 14, () -> setPrice(slot)));
                Ui.button(ctx, x + 8 + bw, by, bw, 14, Component.translatable("tcgshop.shop.market_btn"), mouseX, mouseY, true, 0xFF2F6FB0);
                areas.add(new Area(x + 8 + bw, by, bw, 14, () -> { priceFields.get(slot).setValue(String.valueOf(market)); setPrice(slot); }));
                Ui.button(ctx, x + 10 + bw * 2, by, bw, 14, Component.translatable("tcgshop.shop.take"), mouseX, mouseY, true, 0xFF7A2020);
                areas.add(new Area(x + 10 + bw * 2, by, bw, 14, () -> send(ShopActionPayload.TAKE, slot, 0)));
                priceFields.get(i).render(ctx, mouseX, mouseY, delta);
            } else {
                long price = data.prices().get(i);
                double diff = market == 0 ? 0 : (price - market) * 100.0 / market;
                Ui.text(ctx, Component.literal(PriceService.format(price)).withStyle(ChatFormatting.BOLD), x + 4, ty, cellW() - 8, 10, Ui.GOLD, true);
                ty += 11;
                Ui.text(ctx, Component.literal(String.format(java.util.Locale.ROOT, "%+.0f%% vs mercado", diff)), x + 4, ty, cellW() - 8, 9,
                        diff > 15 ? 0xFFFF7A5C : diff < -5 ? 0xFF6BE07B : Ui.MUTED, false);
                int by = y + cellH() - 22;
                Ui.button(ctx, x + 8, by, cellW() - 16, 16, Component.translatable("tcgshop.shop.buy_btn"), mouseX, mouseY, true, 0xFFD39B1A);
                areas.add(new Area(x + 8, by, cellW() - 16, 16, () -> send(ShopActionPayload.BUY, slot, 0)));
            }
        }
        if (data.owner()) {
            ctx.drawCenteredString(font, Component.translatable("tcgshop.shop.manage_hint"), width / 2, height - 16, Ui.MUTED);
            markupColumn(ctx, mouseX, mouseY);
        }
    }

    /** 1.1: raise / lower every price in this fixture at once, or reset them all to the market price. */
    private static final int[] MARKUPS = {5, 10, 25, 50, 100, -10, -25};

    private void markupColumn(GuiGraphics ctx, int mouseX, int mouseY) {
        boolean any = false;
        for (ItemStack st : data.items()) any |= !st.isEmpty();
        if (!any) return;
        int x = 6, y = 30, w = 74;
        ctx.drawString(font, Component.translatable("tcgshop.shop.markup_title").withStyle(ChatFormatting.BOLD), x, y, Ui.GOLD, true);
        y += 12;
        for (int pct : MARKUPS) {
            String lbl = (pct > 0 ? "+" : "") + pct + "%";
            Ui.button(ctx, x, y, w, 14, Component.literal(lbl), mouseX, mouseY, true, pct > 0 ? 0xFF3C8A4A : 0xFF7A4A20);
            areas.add(new Area(x, y, w, 14, () -> send(ShopActionPayload.MARKUP_ALL, 0, pct)));
            if (Ui.in(mouseX, mouseY, x, y, w, 14)) setTooltipForNextRenderPass(Component.translatable("tcgshop.shop.markup_tip", lbl));
            y += 16;
        }
        Ui.button(ctx, x, y + 2, w, 14, Component.translatable("tcgshop.shop.market_all"), mouseX, mouseY, true, 0xFF2F6FB0);
        areas.add(new Area(x, y + 2, w, 14, () -> send(ShopActionPayload.MARKET_ALL, 0, 0)));
    }

    private void setPrice(int slot) {
        try {
            long v = Long.parseLong(priceFields.get(slot).getValue());
            send(ShopActionPayload.SET_PRICE, slot, v);
        } catch (NumberFormatException ignored) {
        }
    }

    private void send(int action, int slot, long value) {
        ClientPlatform.INSTANCE.send(new ShopActionPayload(data.pos(), action, slot, value));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (EditBox f : priceFields) {
            if (f.isVisible() && f.mouseClicked(mouseX, mouseY, button)) {
                setFocused(f);
                return true;
            }
            f.setFocused(false);
        }
        for (Area a : areas) {
            if (Ui.in(mouseX, mouseY, a.x, a.y, a.w, a.h)) {
                a.action.run();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        for (EditBox f : priceFields) if (f.isFocused()) return f.charTyped(chr, modifiers);
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        for (int i = 0; i < priceFields.size(); i++) {
            EditBox f = priceFields.get(i);
            if (f.isFocused()) {
                if (keyCode == GLFW.GLFW_KEY_ENTER) { setPrice(i); return true; }
                if (keyCode != GLFW.GLFW_KEY_ESCAPE) return f.keyPressed(keyCode, scanCode, modifiers) || true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}

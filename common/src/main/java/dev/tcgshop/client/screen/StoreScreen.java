package dev.tcgshop.client.screen;

import dev.masterball.api.platform.Platform;
import dev.masterball.api.platform.ClientPlatform;
import dev.tcgshop.client.binder.Ui;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Mysterious Wholesaler's stall: pack boxes at wholesale price, shop blocks, furniture and tools. */
public class StoreScreen extends Screen {
    private final CatalogPanel panel;
    private final int source;
    /** The catalog as the server sent it (1.0: «On sale» strip with the store discounts). */
    private final List<ItemStack> goods;
    private final List<Long> prices;
    private EditBox search;
    /** Tab: false = buy, true = sell him cards (40%) and emeralds. */
    private boolean selling;
    private final CardPickGrid sellGrid = new CardPickGrid();
    private final java.util.List<int[]> tabs = new java.util.ArrayList<>();
    private final java.util.List<Runnable> tabActions = new java.util.ArrayList<>();
    /** Furniture tab (CobbleFurnies / plushies): created the first time it is opened. */
    private boolean furnitureTab;
    private FurnitureApp furniture;

    public StoreScreen(int source, List<ItemStack> goods, List<Long> prices) {
        super(Component.translatable("entity.tcgshop.wholesaler"));
        this.panel = new CatalogPanel(source, goods, prices);
        this.source = source;
        this.goods = goods;
        this.prices = prices;
        sellGrid.price = st -> dev.tcgshop.shop.CardSales.offer(st, dev.tcgshop.shop.CardSales.WHOLESALER_RATE);
        // 1.1: old shop cores too (coreResaleRate of their price); one still linked to a dormant shop (or to a shop this client cannot see) is shown locked; crafted tiers (Paper) are not bought
        sellGrid.accept = st -> dev.masterball.api.card.CardInfo.isCard(st) || dev.tcgshop.shop.VanillaPrices.sellable(st)
                || dev.tcgshop.shop.CoreResale.unitPrice(st) > 0;
        sellGrid.extra = st -> dev.tcgshop.shop.CoreResale.isCore(st) ? dev.tcgshop.shop.CoreResale.unitPrice(st) : dev.tcgshop.shop.VanillaPrices.price(st);
        sellGrid.locked = CardPickGrid::lockedCore;
        sellGrid.includeBinder = true; // Binder cards too
    }

    private int emeralds() {
        if (minecraft == null || minecraft.player == null) return 0;
        int n = 0;
        var inv = minecraft.player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (st.is(net.minecraft.world.item.Items.EMERALD)) n += st.getCount();
            else if (st.is(net.minecraft.world.item.Items.EMERALD_BLOCK)) n += st.getCount() * 9;
        }
        return n;
    }

    private void tab(GuiGraphics ctx, int x, int y, int w, Component label, boolean sel, int mouseX, int mouseY, Runnable r) {
        Ui.chip(ctx, x, y, w, 15, label, sel, Ui.in(mouseX, mouseY, x, y, w, 15), Ui.GOLD, 0);
        tabs.add(new int[]{x, y, w, 15});
        tabActions.add(r);
    }

    private void renderSell(GuiGraphics ctx, int x, int y, int w, int h, int mouseX, int mouseY) {
        // emeralds -> CobbleDollars (Wholesaler's tip)
        int em = emeralds();
        long ep = dev.tcgshop.shop.CardSales.emeraldPrice();
        Ui.roundGradient(ctx, x, y, w, 30, 0xFF1E4D33, 0xFF12301F);
        ctx.renderItem(new ItemStack(net.minecraft.world.item.Items.EMERALD), x + 6, y + 7);
        ctx.drawString(font, Component.translatable("tcgshop.sell.emerald_rate", dev.tcgshop.pricing.PriceService.format(ep)).withStyle(ChatFormatting.BOLD), x + 28, y + 5, 0xFFB8F5C8, false);
        ctx.drawString(font, Component.translatable("tcgshop.sell.emerald_have", em, dev.tcgshop.pricing.PriceService.format(em * ep)), x + 28, y + 17, 0xFFE0F0E6, false);
        int bw = 92, bx = x + w - bw - 6;
        Ui.button(ctx, bx, y + 6, bw, 18, Component.translatable("tcgshop.sell.emerald_btn"), mouseX, mouseY, em > 0, 0xFF2E9C5A);
        if (em > 0) {
            tabs.add(new int[]{bx, y + 6, bw, 18});
            tabActions.add(() -> ClientPlatform.INSTANCE.send(
                    new dev.tcgshop.network.MarketPayloads.Sell(source, dev.tcgshop.network.MarketPayloads.SELL_EMERALDS, java.util.List.of())));
        }
        // cards at 40%
        int gy = y + 36, gh = h - 36 - 30;
        ctx.drawString(font, Component.translatable("tcgshop.sell.wholesaler_hint"), x + 2, gy, 0xFFE0C8A8, false);
        if (dev.tcgshop.shop.CoreResale.rate() > 0) { // 1.1: old shop cores, right-aligned (scaled down if the hint leaves no room)
            Component ch = Component.translatable("tcgshop.sell.core_short", Math.round(dev.tcgshop.shop.CoreResale.rate() * 100));
            int free = w - 8 - font.width(Component.translatable("tcgshop.sell.wholesaler_hint")), cwid = font.width(ch);
            float sc = cwid <= free ? 1f : Math.max(0.6f, free / (float) Math.max(1, cwid));
            if (cwid * sc <= free + 1) {
                var m = ctx.pose();
                m.pushPose();
                m.translate(x + w - 2 - cwid * sc, gy + (1 - sc) * 4, 0);
                m.scale(sc, sc, 1);
                ctx.drawString(font, ch, 0, 0, 0xFFFFD34D, false);
                m.popPose();
            }
        }
        if (sellGrid.isEmpty()) Ui.text(ctx, Component.translatable("tcgshop.sell.no_cards"), x, gy + gh / 2, w, 10, Ui.MUTED, false);
        ItemStack hov = sellGrid.render(ctx, x, gy + 12, w, gh - 12, mouseX, mouseY, 30);
        long total = minecraft != null && minecraft.player != null ? sellGrid.total(minecraft.player) : 0;
        int fy = y + h - 24;
        ctx.drawString(font, Component.translatable("tcgshop.sell.selected", sellGrid.sel.size(),
                dev.tcgshop.pricing.PriceService.format(total)).withStyle(ChatFormatting.BOLD), x + 2, fy + 6, Ui.GOLD, false);
        boolean can = !sellGrid.sel.isEmpty();
        Ui.button(ctx, x + w - 120, fy, 120, 20, Component.translatable("tcgshop.sell.sell_btn"), mouseX, mouseY, can, 0xFFB8701E);
        if (can) {
            tabs.add(new int[]{x + w - 120, fy, 120, 20});
            tabActions.add(() -> {
                ClientPlatform.INSTANCE.send(new dev.tcgshop.network.MarketPayloads.Sell(source,
                        dev.tcgshop.network.MarketPayloads.SELL_CARDS, new java.util.ArrayList<>(sellGrid.sel)));
                sellGrid.sel.clear();
            });
        }
        if (!hov.isEmpty()) {
            java.util.List<Component> tip = new java.util.ArrayList<>(java.util.List.of(hov.getHoverName(),
                    Component.translatable("tcgshop.sell.market", dev.tcgshop.pricing.PriceService.format(dev.tcgshop.shop.CardSales.unitValue(hov)))
                            .withStyle(ChatFormatting.GRAY)));
            if (dev.tcgshop.shop.CoreResale.isCore(hov)) { // 1.1
                String lockWhy = CardPickGrid.coreLockReason(hov);
                if (lockWhy != null) tip.add(Component.translatable(lockWhy).withStyle(ChatFormatting.RED));
                else {
                    tip.add(Component.translatable("tcgshop.sell.core_tip", Math.round(dev.tcgshop.shop.CoreResale.rate() * 100)).withStyle(ChatFormatting.YELLOW));
                    tip.add(Component.translatable("tcgshop.sell.you_get", dev.tcgshop.pricing.PriceService.format(dev.tcgshop.shop.CoreResale.unitPrice(hov)))
                            .withStyle(ChatFormatting.GOLD));
                }
            }
            ctx.renderComponentTooltip(font, tip, mouseX, mouseY);
        }
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
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        // 1.0: purchase notices (bought / not enough money / locked) on their own opaque layer; under one, the stall ignores the mouse
        boolean overNotice = dev.tcgshop.client.phone.PhoneNotices.contains(mouseX, mouseY);
        renderStall(ctx, overNotice ? -9999 : mouseX, overNotice ? -9999 : mouseY, delta);
        int w = Math.min(width - 20, 460), h = height - 20, x = (width - w) / 2, y = 10, nw = Math.min(w - 24, 260);
        ctx.enableScissor(x + 6, y + 36, x + w - 6, y + h - 6);
        dev.tcgshop.client.phone.PhoneNotices.render(ctx, (width - nw) / 2, y + 40, nw, mouseX, mouseY);
        ctx.disableScissor();
    }

    private void renderStall(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        ctx.fillGradient(0, 0, width, height, 0xB0101018, 0xD0050508);
        int w = Math.min(width - 20, 460), h = height - 20, x = (width - w) / 2, y = 10;
        Ui.leather(ctx, x, y, w, h, 0xFF5B3A1E, 0xFF331F0E);
        ctx.drawString(font, title.copy().withStyle(ChatFormatting.BOLD), x + 10, y + 8, Ui.GOLD);
        ctx.drawString(font, Component.translatable("tcgshop.wholesaler.subtitle"), x + 10, y + 19, 0xFFE0C8A8, false);
        tabs.clear();
        tabActions.clear();
        // the Wholesaler doesn't sell luxury statues: without CobbleFurnies/Pokéblocks there is no Furniture tab (as before 0.9)
        boolean furn = dev.tcgshop.compat.CobbleFurniesCompat.available();
        int tw0 = furn ? 60 : 70, tx0 = furn ? x + w / 2 - (tw0 * 3 + 8) / 2 : x + w / 2 - tw0 - 2;
        tab(ctx, tx0, y + 9, tw0, Component.translatable("tcgshop.sell.tab_buy"), !selling && !furnitureTab, mouseX, mouseY, () -> { selling = false; furnitureTab = false; });
        tab(ctx, tx0 + tw0 + 4, y + 9, tw0, Component.translatable("tcgshop.sell.tab_sell"), selling && !furnitureTab, mouseX, mouseY, () -> {
            selling = true;
            furnitureTab = false;
            sellGrid.restartAppear();
        });
        if (furn) tab(ctx, tx0 + (tw0 + 4) * 2, y + 9, tw0, Component.translatable("tcgshop.furn.tab"), furnitureTab, mouseX, mouseY, this::openFurniture);
        ctx.fill(x + 6, y + 36, x + w - 6, y + h - 6, 0xFF1A1D2A);
        if (furnitureTab && furniture != null) {
            furniture.render(ctx, x + 10, y + 40, w - 20, h - 50, mouseX, mouseY, delta);
            return;
        }
        if (selling) {
            renderSell(ctx, x + 10, y + 40, w - 20, h - 50, mouseX, mouseY);
            return;
        }
        search.render(ctx, mouseX, mouseY, delta);
        panel.beginFrame();
        int fh = panel.renderFilters(ctx, x + 10, y + 40, w - 20, mouseX, mouseY);
        // 1.0: discounted products (store sale event) above the catalog; nothing is reserved when there is no sale
        fh += dev.tcgshop.client.phone.DiscountTag.strip(ctx, x + 10, y + 42 + fh, w - 20, goods, prices, mouseX, mouseY);
        panel.render(ctx, x + 10, y + 42 + fh, w - 20, h - 52 - fh, mouseX, mouseY, System.nanoTime() / 1e9);
    }

    /** Furniture tab: requests the catalog with the Wholesaler's prices from the server. */
    private void openFurniture() {
        if (furniture == null) furniture = new FurnitureApp(source, this);
        furnitureTab = true;
        selling = false; // so the card-selling drag doesn't act on this tab
        search.setFocused(false);
        furniture.request();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (dev.tcgshop.client.phone.PhoneNotices.click(mouseX, mouseY)) return true; // 1.0: the notice swallows the click
        for (int i = tabs.size() - 1; i >= 0; i--) {
            int[] r = tabs.get(i);
            if (Ui.in(mouseX, mouseY, r[0], r[1], r[2], r[3])) { tabActions.get(i).run(); return true; }
        }
        if (furnitureTab && furniture != null) return furniture.mouseClicked(mouseX, mouseY, button) || super.mouseClicked(mouseX, mouseY, button);
        if (selling) return sellGrid.click(mouseX, mouseY) || super.mouseClicked(mouseX, mouseY, button);
        if (search.mouseClicked(mouseX, mouseY, button)) { setFocused(search); return true; }
        search.setFocused(false);
        return panel.click(mouseX, mouseY) || super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double h, double v) {
        if (dev.tcgshop.client.phone.PhoneNotices.contains(mouseX, mouseY)) return true;
        if (furnitureTab && furniture != null) { furniture.mouseScrolled(mouseX, mouseY, v); return true; }
        if (selling) { sellGrid.scroll(v); return true; }
        panel.scroll(v);
        return true;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (furnitureTab && furniture != null) return furniture.charTyped(chr, modifiers);
        return search.isFocused() && search.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (furnitureTab && furniture != null && furniture.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (search.isFocused() && keyCode != GLFW.GLFW_KEY_ESCAPE) return search.keyPressed(keyCode, scanCode, modifiers) || true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (button == 0 && selling && sellGrid.drag(mouseX, mouseY)) return true;
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        sellGrid.release();
        return super.mouseReleased(mouseX, mouseY, button);
    }
}

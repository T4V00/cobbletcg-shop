package dev.tcgshop.client.screen;

import dev.tcgshop.client.binder.Ui;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/** Mysterious Wholesaler's stall: pack boxes at wholesale price, shop blocks, furniture and tools. */
public class StoreScreen extends Screen {
    private final CatalogPanel panel;
    private final int source;
    private TextFieldWidget search;
    /** Tab: false = buy, true = sell him cards (40%) and emeralds. */
    private boolean selling;
    private final CardPickGrid sellGrid = new CardPickGrid();
    private final java.util.List<int[]> tabs = new java.util.ArrayList<>();
    private final java.util.List<Runnable> tabActions = new java.util.ArrayList<>();
    /** Furniture tab (CobbleFurnies / plushies): created the first time it is opened. */
    private boolean furnitureTab;
    private FurnitureApp furniture;

    public StoreScreen(int source, List<ItemStack> goods, List<Long> prices) {
        super(Text.translatable("entity.tcgshop.wholesaler"));
        this.panel = new CatalogPanel(source, goods, prices);
        this.source = source;
        sellGrid.price = st -> dev.tcgshop.shop.CardSales.offer(st, dev.tcgshop.shop.CardSales.WHOLESALER_RATE);
        sellGrid.accept = st -> dev.masterball.api.card.CardInfo.isCard(st) || dev.tcgshop.shop.VanillaPrices.sellable(st);
        sellGrid.extra = dev.tcgshop.shop.VanillaPrices::price;
        sellGrid.includeBinder = true; // Binder cards too
    }

    private int emeralds() {
        if (client == null || client.player == null) return 0;
        int n = 0;
        var inv = client.player.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack st = inv.getStack(i);
            if (st.isOf(net.minecraft.item.Items.EMERALD)) n += st.getCount();
            else if (st.isOf(net.minecraft.item.Items.EMERALD_BLOCK)) n += st.getCount() * 9;
        }
        return n;
    }

    private void tab(DrawContext ctx, int x, int y, int w, Text label, boolean sel, int mouseX, int mouseY, Runnable r) {
        Ui.chip(ctx, x, y, w, 15, label, sel, Ui.in(mouseX, mouseY, x, y, w, 15), Ui.GOLD, 0);
        tabs.add(new int[]{x, y, w, 15});
        tabActions.add(r);
    }

    private void renderSell(DrawContext ctx, int x, int y, int w, int h, int mouseX, int mouseY) {
        // emeralds -> CobbleDollars (Wholesaler's tip)
        int em = emeralds();
        long ep = dev.tcgshop.shop.CardSales.emeraldPrice();
        Ui.roundGradient(ctx, x, y, w, 30, 0xFF1E4D33, 0xFF12301F);
        ctx.drawItem(new ItemStack(net.minecraft.item.Items.EMERALD), x + 6, y + 7);
        ctx.drawText(textRenderer, Text.translatable("tcgshop.sell.emerald_rate", dev.tcgshop.pricing.PriceService.format(ep)).formatted(Formatting.BOLD), x + 28, y + 5, 0xFFB8F5C8, false);
        ctx.drawText(textRenderer, Text.translatable("tcgshop.sell.emerald_have", em, dev.tcgshop.pricing.PriceService.format(em * ep)), x + 28, y + 17, 0xFFE0F0E6, false);
        int bw = 92, bx = x + w - bw - 6;
        Ui.button(ctx, bx, y + 6, bw, 18, Text.translatable("tcgshop.sell.emerald_btn"), mouseX, mouseY, em > 0, 0xFF2E9C5A);
        if (em > 0) {
            tabs.add(new int[]{bx, y + 6, bw, 18});
            tabActions.add(() -> net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                    new dev.tcgshop.network.MarketPayloads.Sell(source, dev.tcgshop.network.MarketPayloads.SELL_EMERALDS, java.util.List.of())));
        }
        // cards at 40%
        int gy = y + 36, gh = h - 36 - 30;
        ctx.drawText(textRenderer, Text.translatable("tcgshop.sell.wholesaler_hint"), x + 2, gy, 0xFFE0C8A8, false);
        if (sellGrid.isEmpty()) Ui.text(ctx, Text.translatable("tcgshop.sell.no_cards"), x, gy + gh / 2, w, 10, Ui.MUTED, false);
        ItemStack hov = sellGrid.render(ctx, x, gy + 12, w, gh - 12, mouseX, mouseY, 30);
        long total = client != null && client.player != null ? sellGrid.total(client.player) : 0;
        int fy = y + h - 24;
        ctx.drawText(textRenderer, Text.translatable("tcgshop.sell.selected", sellGrid.sel.size(),
                dev.tcgshop.pricing.PriceService.format(total)).formatted(Formatting.BOLD), x + 2, fy + 6, Ui.GOLD, false);
        boolean can = !sellGrid.sel.isEmpty();
        Ui.button(ctx, x + w - 120, fy, 120, 20, Text.translatable("tcgshop.sell.sell_btn"), mouseX, mouseY, can, 0xFFB8701E);
        if (can) {
            tabs.add(new int[]{x + w - 120, fy, 120, 20});
            tabActions.add(() -> {
                net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new dev.tcgshop.network.MarketPayloads.Sell(source,
                        dev.tcgshop.network.MarketPayloads.SELL_CARDS, new java.util.ArrayList<>(sellGrid.sel)));
                sellGrid.sel.clear();
            });
        }
        if (!hov.isEmpty()) ctx.drawTooltip(textRenderer, java.util.List.of(hov.getName(),
                Text.translatable("tcgshop.sell.market", dev.tcgshop.pricing.PriceService.format(dev.tcgshop.shop.CardSales.unitValue(hov)))
                        .formatted(Formatting.GRAY)), mouseX, mouseY);
    }

    @Override
    protected void init() {
        int w = Math.min(width - 20, 460);
        search = new TextFieldWidget(textRenderer, (width - w) / 2 + w - 130, 26, 124, 14, Text.translatable("tcgshop.binder.search"));
        search.setPlaceholder(Text.translatable("tcgshop.binder.search").formatted(Formatting.DARK_GRAY));
        search.setChangedListener(s -> panel.filter = s);
        addSelectableChild(search);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fillGradient(0, 0, width, height, 0xB0101018, 0xD0050508);
        int w = Math.min(width - 20, 460), h = height - 20, x = (width - w) / 2, y = 10;
        Ui.leather(ctx, x, y, w, h, 0xFF5B3A1E, 0xFF331F0E);
        ctx.drawTextWithShadow(textRenderer, title.copy().formatted(Formatting.BOLD), x + 10, y + 8, Ui.GOLD);
        ctx.drawText(textRenderer, Text.translatable("tcgshop.wholesaler.subtitle"), x + 10, y + 19, 0xFFE0C8A8, false);
        tabs.clear();
        tabActions.clear();
        // the Wholesaler doesn't sell luxury statues: without CobbleFurnies/Pokéblocks there is no Furniture tab (as before 0.9)
        boolean furn = dev.tcgshop.compat.CobbleFurniesCompat.available();
        int tw0 = furn ? 60 : 70, tx0 = furn ? x + w / 2 - (tw0 * 3 + 8) / 2 : x + w / 2 - tw0 - 2;
        tab(ctx, tx0, y + 9, tw0, Text.translatable("tcgshop.sell.tab_buy"), !selling && !furnitureTab, mouseX, mouseY, () -> { selling = false; furnitureTab = false; });
        tab(ctx, tx0 + tw0 + 4, y + 9, tw0, Text.translatable("tcgshop.sell.tab_sell"), selling && !furnitureTab, mouseX, mouseY, () -> {
            selling = true;
            furnitureTab = false;
            sellGrid.restartAppear();
        });
        if (furn) tab(ctx, tx0 + (tw0 + 4) * 2, y + 9, tw0, Text.translatable("tcgshop.furn.tab"), furnitureTab, mouseX, mouseY, this::openFurniture);
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

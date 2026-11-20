package dev.tcgshop.client.binder;

import cobbletcg.networking.PageChangePayload;
import cobbletcg.procedures.AlbumConfig;
import cobbletcg.procedures.AlbumScreenHandler;
import dev.tcgshop.TcgSounds;
import dev.tcgshop.card.CardInfo;
import dev.tcgshop.card.Rarity;
import dev.tcgshop.client.screen.Draw;
import dev.tcgshop.pricing.PriceService;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import java.util.List;
import java.util.Locale;

import static dev.tcgshop.client.screen.Draw.withAlpha;

/**
 * Rework visual de los álbumes por set de cobbletcg, estilo álbum Panini: doble página con huecos numerados
 * en el orden oficial del set (nombre y número de cada carta impresos en su hueco), cartas "pegadas" con
 * brillo, barra de progreso, y tu inventario abajo para colocar cartas.
 *
 * Usa el AlbumScreenHandler original de cobbletcg (mismas casillas, mismo servidor, mismo cambio de página);
 * sólo cambia cómo se ve y cómo se interactúa. Sin las opciones estéticas del Personal Binder.
 */
public class PaniniAlbumScreen extends Screen {
    private static final int PER_PAGE = 18; // 9 por página, 2 páginas
    private final AlbumScreenHandler handler;
    private double flipStart = -10;
    private int flipDir = 1;
    private int lastPage = -1;

    private int bookX, bookY, bookW, bookH, pageW, pageH, invX, invY;

    public PaniniAlbumScreen(AlbumScreenHandler handler, Text title) {
        super(title);
        this.handler = handler;
    }

    @Override
    protected void init() {
        int invH = 4 * 18 + 10;
        bookH = Math.min(height - invH - 30, 250);
        bookW = Math.min(width - 60, Math.round(bookH * 1.5f));
        bookX = (width - bookW) / 2;
        bookY = 18;
        pageW = (bookW - 20) / 2;
        pageH = bookH - 12;
        invX = (width - 9 * 18) / 2;
        invY = bookY + bookH + 12;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private AlbumConfig config() {
        return handler.getConfig();
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fillGradient(0, 0, width, height, 0xD0151020, 0xF0070510);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderBackground(ctx, mouseX, mouseY, delta);
        double t = System.nanoTime() / 1e9;
        AlbumConfig cfg = config();
        int page = handler.getCurrentPage();
        if (lastPage >= 0 && page != lastPage) {
            flipStart = t;
            flipDir = page > lastPage ? 1 : -1;
        }
        lastPage = page;

        // tapas
        ctx.fill(bookX + 6, bookY + 8, bookX + bookW + 6, bookY + bookH + 8, 0x77000000);
        Ui.roundGradient(ctx, bookX - 4, bookY - 4, bookW + 8, bookH + 8, 0xFF1C3D7A, 0xFF0F2247);
        Ui.outline(ctx, bookX - 4, bookY - 4, bookW + 8, bookH + 8, 0xFF000000);

        Slot hovered = null;
        Catalog.Card hoveredMissing = null;
        for (int side = 0; side < 2; side++) {
            int px = bookX + 4 + side * (pageW + 12), py = bookY + 6;
            // papel con textura de puntos y cabecera estilo cromos
            ctx.fillGradient(px, py, px + pageW, py + pageH, 0xFFFBF7EC, 0xFFEDE5D0);
            for (int yy = py + 3; yy < py + pageH; yy += 6)
                for (int xx = px + 3 + ((yy / 6) % 2) * 3; xx < px + pageW; xx += 6) ctx.fill(xx, yy, xx + 1, yy + 1, 0x14000000);
            ctx.fillGradient(px, py, px + pageW, py + 14, 0xFFE63946, 0xFFB51F2C);
            String header = side == 0 ? Ui.ellipsis(Text.translatable("tcgshop.set." + cfg.getAlbumId()).getString(), pageW - 10)
                    : Text.translatable("tcgshop.panini.page", page * 2 + side + 1, cfg.getTotalPages() * 2).getString();
            ctx.drawText(textRenderer, header, px + 5, py + 3, 0xFFFFFFFF, true);

            int cellW = (pageW - 16) / 3, cellH = (pageH - 26) / 3;
            for (int i = 0; i < 9; i++) {
                int slotIndex = side * 9 + i;
                int global = page * PER_PAGE + slotIndex;
                int cx = px + 5 + (i % 3) * (cellW + 3), cy = py + 18 + (i / 3) * (cellH + 2);
                Slot slot = slotIndex < handler.slots.size() ? handler.slots.get(slotIndex) : null;
                boolean exists = global < cfg.getTotalCartas();
                boolean hover = Ui.in(mouseX, mouseY, cx, cy, cellW, cellH);
                Catalog.Card missing = drawSticker(ctx, cfg, global, exists, slot, cx, cy, cellW, cellH, hover, t, mouseX);
                if (hover && exists) {
                    hovered = slot;
                    hoveredMissing = missing;
                }
            }
        }
        // hoja girando
        double fp = (t - flipStart) / 0.35;
        if (fp < 1) {
            int spine = bookX + bookW / 2;
            float s = (float) Math.abs(Math.cos(fp * Math.PI));
            int w = Math.round(pageW * s);
            boolean right = (flipDir > 0) == (fp < 0.5);
            int x0 = right ? spine : spine - w;
            ctx.fillGradient(x0, bookY + 6, x0 + w, bookY + 6 + pageH, 0xFFFBF7EC, 0xFFD8CFB8);
            Ui.outline(ctx, x0, bookY + 6, Math.max(2, w), pageH, 0xFF9A907A);
        }
        // lomo
        int spine = bookX + bookW / 2;
        ctx.fillGradient(spine - 3, bookY + 4, spine + 3, bookY + bookH - 4, 0x55000000, 0x22000000);

        // progreso del álbum
        int filled = 0;
        // (sólo sabemos lo que hay en las casillas visibles; el progreso total lo calcula el servidor en el título)
        for (int i = 0; i < Math.min(PER_PAGE, handler.slots.size()); i++) if (handler.slots.get(i).hasStack()) filled++;
        Text prog = Text.translatable("tcgshop.panini.visible", filled, Math.min(PER_PAGE, Math.max(0, cfg.getTotalCartas() - page * PER_PAGE)));
        ctx.drawCenteredTextWithShadow(textRenderer, prog, width / 2, 6, 0xFFCCCCCC);

        // navegación
        int ay = bookY + bookH / 2 - 10;
        if (page > 0) {
            Ui.button(ctx, bookX - 26, ay, 18, 20, Text.literal("◀"), mouseX, mouseY, true, 0xFF1C3D7A);
        }
        if (page < cfg.getTotalPages() - 1) {
            Ui.button(ctx, bookX + bookW + 8, ay, 18, 20, Text.literal("▶"), mouseX, mouseY, true, 0xFF1C3D7A);
        }

        // inventario del jugador
        renderInventory(ctx, mouseX, mouseY);

        // objeto en el cursor
        ItemStack cursor = handler.getCursorStack();
        if (!cursor.isEmpty()) {
            ctx.getMatrices().push();
            ctx.getMatrices().translate(0, 0, 300);
            if (CardInfo.isCard(cursor)) {
                int cw = 30, ch = Math.round(cw / Draw.CARD_RATIO);
                Draw.cardFace(ctx, CardInfo.texture(CardInfo.id(cursor)), cursor, mouseX - cw / 2f, mouseY - ch / 2f, cw, ch, 0.95f);
            } else {
                ctx.drawItem(cursor, mouseX - 8, mouseY - 8);
                ctx.drawItemInSlot(textRenderer, cursor, mouseX - 8, mouseY - 8);
            }
            ctx.getMatrices().pop();
        }

        if (cursor.isEmpty()) {
            if (hovered != null && hovered.hasStack()) {
                ItemStack st = hovered.getStack();
                Rarity r = Rarity.of(st);
                ctx.drawTooltip(textRenderer, List.of(st.getName(), r.displayName().copy().withColor(r.color & 0xFFFFFF),
                        Text.literal(PriceService.format(PriceService.priceCd(st))).withColor(0xFFD34D)), mouseX, mouseY);
            } else if (hoveredMissing != null) {
                ctx.drawTooltip(textRenderer, List.of(hoveredMissing.name(), Text.translatable("tcgshop.panini.missing").formatted(Formatting.GRAY),
                        Text.translatable("tcgshop.binder.have", ClientCollection.count(hoveredMissing.key())).formatted(Formatting.DARK_GRAY)), mouseX, mouseY);
            }
        }
    }

    /** Dibuja un hueco del álbum. Devuelve la carta que falta (para el tooltip) si está vacío. */
    private Catalog.Card drawSticker(DrawContext ctx, AlbumConfig cfg, int global, boolean exists, Slot slot, int x, int y, int w, int h,
                                     boolean hover, double t, int mouseX) {
        if (!exists) {
            ctx.fill(x, y, x + w, y + h, 0x10000000);
            return null;
        }
        String itemId = cfg.getItemIdForSlot(global);
        Identifier id = itemId == null ? null : Identifier.tryParse(itemId.contains(":") ? itemId : "cobbletcg:" + itemId);
        Catalog.Card card = id == null ? null : Catalog.card(id.toString());
        int cw = Math.min(w - 4, Math.round((h - 4) * Draw.CARD_RATIO)), ch = Math.round(cw / Draw.CARD_RATIO);
        int cx = x + (w - cw) / 2, cy = y + (h - ch) / 2;
        boolean has = slot != null && slot.hasStack();
        if (has) {
            ItemStack st = slot.getStack();
            Rarity r = Rarity.of(st);
            ctx.fill(cx + 1, cy + 2, cx + cw + 1, cy + ch + 2, 0x55000000);
            Draw.cardFace(ctx, CardInfo.texture(CardInfo.id(st)), st, cx, cy, cw, ch, 1f);
            // brillo de cromo recién pegado
            ctx.fill(cx + 1, cy + 1, cx + cw - 1, cy + 2, 0x55FFFFFF);
            if (r.tier > 0 && hover) Draw.holo(ctx, cx, cy, cw, ch, r.tier, r.color, t, mouseX);
            Ui.outline(ctx, cx, cy, cw, ch, r.tier > 0 ? r.color : 0xFF333333);
        } else {
            // hueco Panini: marco discontinuo, número grande y nombre
            int rc = card == null ? 0xFF999999 : card.rarity().color;
            for (int xx = cx; xx < cx + cw; xx += 4) {
                ctx.fill(xx, cy, xx + 2, cy + 1, 0xFFB5AA90);
                ctx.fill(xx, cy + ch - 1, xx + 2, cy + ch, 0xFFB5AA90);
            }
            for (int yy = cy; yy < cy + ch; yy += 4) {
                ctx.fill(cx, yy, cx + 1, yy + 2, 0xFFB5AA90);
                ctx.fill(cx + cw - 1, yy, cx + cw, yy + 2, 0xFFB5AA90);
            }
            ctx.fill(cx + 1, cy + ch - 4, cx + cw - 1, cy + ch - 1, withAlpha(rc, 0.6f));
            String num = card == null || card.number() < 0 ? String.valueOf(global + 1) : String.format(Locale.ROOT, "%03d", card.number());
            Draw.scaledCentered(ctx, Text.literal(num), cx + cw / 2f, cy + ch / 2f - 10, 1.3f, 0xFFB09C70);
            if (card != null) {
                String name = card.name().getString().replaceFirst("^[A-Z0-9]+\\s+\\d+\\s+", "").trim();
                List<net.minecraft.text.OrderedText> lines = textRenderer.wrapLines(Text.literal(name), (int) ((cw - 2) / 0.6f));
                ctx.getMatrices().push();
                ctx.getMatrices().translate(cx + cw / 2f, cy + ch / 2f + 2, 0);
                ctx.getMatrices().scale(0.6f, 0.6f, 1);
                for (int i = 0; i < Math.min(2, lines.size()); i++) {
                    int lw = textRenderer.getWidth(lines.get(i));
                    ctx.drawText(textRenderer, lines.get(i), -lw / 2, i * 9, 0xFF8C7B57, false);
                }
                ctx.getMatrices().pop();
                if (ClientCollection.count(card.key()) > 0) {
                    // la tienes en el Binder General: indicador
                    ctx.drawText(textRenderer, "●", cx + cw - 7, cy + 2, 0xFF43C463, false);
                }
            }
            if (hover) ctx.fill(cx, cy, cx + cw, cy + ch, 0x22FFE066);
        }
        return has ? null : card;
    }

    private void renderInventory(DrawContext ctx, int mouseX, int mouseY) {
        Ui.leather(ctx, invX - 8, invY - 6, 9 * 18 + 16, 4 * 18 + 16, 0xFF2B2F42, 0xFF1A1D2B);
        for (int i = PER_PAGE; i < handler.slots.size(); i++) {
            int inv = i - PER_PAGE; // 0..26 inventario, 27..35 hotbar
            int col = inv % 9, row = inv / 9;
            int x = invX + col * 18, y = invY + row * 18 + (row == 3 ? 4 : 0);
            ctx.fill(x, y, x + 16, y + 16, 0xFF0F1118);
            Ui.outline(ctx, x - 1, y - 1, 18, 18, 0xFF3A3F55);
            Slot s = handler.slots.get(i);
            if (s.hasStack()) {
                ctx.drawItem(s.getStack(), x, y);
                ctx.drawItemInSlot(textRenderer, s.getStack(), x, y);
            }
            if (Ui.in(mouseX, mouseY, x, y, 16, 16)) {
                ctx.fill(x, y, x + 16, y + 16, 0x55FFFFFF);
                if (s.hasStack() && handler.getCursorStack().isEmpty()) ctx.drawItemTooltip(textRenderer, s.getStack(), mouseX, mouseY);
            }
        }
    }

    private int slotAt(double mx, double my) {
        AlbumConfig cfg = config();
        int page = handler.getCurrentPage();
        for (int side = 0; side < 2; side++) {
            int px = bookX + 4 + side * (pageW + 12), py = bookY + 6;
            int cellW = (pageW - 16) / 3, cellH = (pageH - 26) / 3;
            for (int i = 0; i < 9; i++) {
                int cx = px + 5 + (i % 3) * (cellW + 3), cy = py + 18 + (i / 3) * (cellH + 2);
                if (Ui.in(mx, my, cx, cy, cellW, cellH) && page * PER_PAGE + side * 9 + i < cfg.getTotalCartas()) return side * 9 + i;
            }
        }
        for (int i = PER_PAGE; i < handler.slots.size(); i++) {
            int inv = i - PER_PAGE, col = inv % 9, row = inv / 9;
            int x = invX + col * 18, y = invY + row * 18 + (row == 3 ? 4 : 0);
            if (Ui.in(mx, my, x, y, 16, 16)) return i;
        }
        return -1;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int ay = bookY + bookH / 2 - 10;
        int page = handler.getCurrentPage();
        if (page > 0 && Ui.in(mouseX, mouseY, bookX - 26, ay, 18, 20)) { changePage(page - 1); return true; }
        if (page < config().getTotalPages() - 1 && Ui.in(mouseX, mouseY, bookX + bookW + 8, ay, 18, 20)) { changePage(page + 1); return true; }
        int slot = slotAt(mouseX, mouseY);
        if (slot >= 0 && client != null && client.interactionManager != null) {
            SlotActionType type = hasShiftDown() ? SlotActionType.QUICK_MOVE : SlotActionType.PICKUP;
            client.interactionManager.clickSlot(handler.syncId, slot, button, type, client.player);
            if (slot < PER_PAGE) client.getSoundManager().play(PositionedSoundInstance.master(TcgSounds.CARD_SLIDE, 1.2f, 0.6f));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void changePage(int p) {
        ClientPlayNetworking.send(new PageChangePayload(p));
        handler.setPage(p);
        if (client != null) client.getSoundManager().play(PositionedSoundInstance.master(TcgSounds.CARD_FLIP, 1f, 0.7f));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double h, double v) {
        int page = handler.getCurrentPage();
        int np = MathHelper.clamp(page - (int) Math.signum(v), 0, config().getTotalPages() - 1);
        if (np != page) changePage(np);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (client != null && (client.options.inventoryKey.matchesKey(keyCode, scanCode))) {
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void close() {
        if (client != null && client.player != null) client.player.closeHandledScreen();
        super.close();
    }

    @Override
    public void removed() {
        if (client != null && client.player != null) handler.onClosed(client.player);
    }
}

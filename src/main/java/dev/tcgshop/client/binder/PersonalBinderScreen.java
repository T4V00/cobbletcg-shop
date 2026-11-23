package dev.tcgshop.client.binder;

import dev.tcgshop.TcgSounds;
import dev.tcgshop.binder.BinderServer;
import dev.tcgshop.binder.BinderStyles;
import dev.tcgshop.binder.PersonalBinderData;
import dev.tcgshop.client.screen.Draw;
import dev.tcgshop.item.TcgItems;
import dev.tcgshop.network.BinderActionPayload;
import dev.tcgshop.pricing.PriceService;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static dev.tcgshop.client.screen.Draw.withAlpha;

/**
 * Personal Binder: freely arrange your cards on pages of 9 pockets.
 *  · "My cards" drawer (your General Binder) and "♥ Wishlist".
 *  · Drag a card into a pocket, or double-click to put it in the first free slot.
 *  · Wishlisted cards you don't own are placed as a veiled slot with ♥ (they don't use up cards).
 *  · Click an occupied pocket = return the card to the General Binder. Drag between pockets = move.
 *  · Shift+click or right-click a card (pocket or drawer) = view it large.
 *  · Customize the cover (color + pattern) and background with what you unlock through advancements.
 */
public class PersonalBinderScreen extends Screen {
    private record Area(int x, int y, int w, int h, Runnable action) {}

    private final Hand hand;
    private final Set<String> unlocked;
    private final List<Area> areas = new ArrayList<>();
    private int spread;
    private double flipStart = -10;
    private int flipDir = 1;
    private boolean wishTab;
    private TextFieldWidget search;
    private double drawerScroll;
    private boolean customizing;
    private String customizeTab = "palette";
    private String lastDrawerClick;
    private long lastDrawerClickTime;
    /** Zoomed view of a card (overlay: the binder keeps page, tab and scroll). */
    private final CardZoom zoom = new CardZoom();

    // dragging
    private String dragCard;
    private int dragFromSlot = -1;
    private double pressX, pressY;
    private boolean dragging;

    private int drawerX, drawerY, drawerW, drawerH, bookX, bookY, bookW, bookH, pageW, pageH;

    public PersonalBinderScreen(Hand hand, List<String> unlocked) {
        super(Text.translatable("item.tcgshop.personal_binder"));
        this.hand = hand;
        this.unlocked = new HashSet<>(unlocked);
    }

    private PersonalBinderData data() {
        if (client == null || client.player == null) return PersonalBinderData.DEFAULT;
        ItemStack st = client.player.getStackInHand(hand);
        return st.getOrDefault(TcgItems.PERSONAL_BINDER_DATA, PersonalBinderData.DEFAULT);
    }

    @Override
    protected void init() {
        Catalog.rebuild();
        drawerW = 128;
        drawerX = 8;
        drawerY = 30;
        drawerH = height - 56;
        int availW = width - drawerW - 46;
        bookH = Math.min(height - 70, Math.round(availW / 1.45f));
        bookW = Math.round(bookH * 1.45f);
        bookX = drawerX + drawerW + 24 + (availW - bookW) / 2;
        bookY = (height - bookH) / 2 - 4;
        pageW = (bookW - 24) / 2;
        pageH = bookH - 16;
        String prev = search == null ? "" : search.getText();
        search = new TextFieldWidget(textRenderer, drawerX + 5, drawerY + 22, drawerW - 10, 13, Text.translatable("tcgshop.binder.search"));
        search.setPlaceholder(Text.translatable("tcgshop.binder.search").formatted(Formatting.DARK_GRAY));
        search.setText(prev);
        search.setChangedListener(s -> drawerScroll = 0);
        addSelectableChild(search);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        String bg = data().background();
        if (bg.equals("none")) ctx.fillGradient(0, 0, width, height, 0xB0101018, 0xD0050508);
        else {
            StylePainter.background(ctx, bg, 0, 0, width, height, System.nanoTime() / 1e9);
            ctx.fillGradient(0, height - 40, width, height, 0x00000000, 0x55000000);
        }
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        int realX = mouseX, realY = mouseY;
        if (zoom.isOpen()) { mouseX = -10000; mouseY = -10000; } // no hover or tooltips underneath the zoomed view
        ClientCollection.tick();
        areas.clear();
        double t = System.nanoTime() / 1e9;
        PersonalBinderData d = data();
        int spreads = Math.max(1, (d.pages() + 1) / 2);
        spread = MathHelper.clamp(spread, 0, spreads - 1);

        renderBackground(ctx, mouseX, mouseY, delta);
        renderBook(ctx, d, mouseX, mouseY, t, spreads);
        renderDrawer(ctx, mouseX, mouseY, t);
        search.render(ctx, mouseX, mouseY, delta);

        int bx = width - 104, by = 6;
        Ui.button(ctx, bx, by, 96, 18, Text.translatable("tcgshop.personal.customize"), mouseX, mouseY, true, 0xFF8E44AD);
        areas.add(new Area(bx, by, 96, 18, () -> customizing = !customizing));
        Ui.roundGradient(ctx, bookX + bookW / 2 - 70, 6, 140, 18, 0xCC1A1D2B, 0xCC12141F);
        Ui.text(ctx, Text.translatable("item.tcgshop.personal_binder").formatted(Formatting.BOLD), bookX + bookW / 2 - 70, 6, 140, 18, Ui.GOLD, true);

        // help bar
        Text help = Text.translatable(dragCard != null && dragging ? "tcgshop.personal.help_drag" : "tcgshop.personal.help");
        int hw = Math.min(width - 20, textRenderer.getWidth(help) + 16);
        Ui.roundGradient(ctx, (width - hw) / 2, height - 20, hw, 15, 0xCC12141F, 0xCC0A0B12);
        Ui.text(ctx, help, (width - hw) / 2, height - 20, hw, 15, 0xFFDDDDDD, false);

        if (customizing) renderCustomize(ctx, mouseX, mouseY, t, d);

        if (dragging && dragCard != null) {
            Catalog.Card c = Catalog.card(dragCard.startsWith(BinderServer.WISH) ? dragCard.substring(BinderServer.WISH.length()) : dragCard);
            if (c != null) {
                int cw = 42, ch = Math.round(cw / Draw.CARD_RATIO);
                ctx.getMatrices().push();
                ctx.getMatrices().translate(0, 0, 400);
                Draw.glow(ctx, mouseX, mouseY, cw, ch, Ui.GOLD, 0.4f);
                Draw.cardFace(ctx, c.texture(), c.stack(), mouseX - cw / 2f, mouseY - ch / 2f, cw, ch, 0.95f);
                ctx.getMatrices().pop();
            }
        }
        if (zoom.isOpen()) zoom.render(ctx, width, height, realX, realY);
    }

    // ------------------------------------------------------------------ book

    private int pocketW() { return (pageW - 16) / 3; }
    private int pocketH() { return (pageH - 18) / 3; }

    private int pocketX(int side, int i) {
        int spineX = bookX + bookW / 2;
        int pageX = side == 0 ? bookX + 8 : spineX + 4;
        return pageX + 5 + (i % 3) * (pocketW() + 3);
    }

    private int pocketY(int i) { return bookY + 8 + 5 + (i / 3) * (pocketH() + 3); }

    private void renderBook(DrawContext ctx, PersonalBinderData d, int mouseX, int mouseY, double t, int spreads) {
        ctx.fill(bookX + 6, bookY + 8, bookX + bookW + 6, bookY + bookH + 8, 0x77000000);
        StylePainter.cover(ctx, d.palette(), d.pattern(), bookX, bookY, bookW, bookH, t);
        Ui.outline(ctx, bookX, bookY, bookW, bookH, 0xFF000000);
        int spineX = bookX + bookW / 2;
        ctx.fillGradient(spineX - 6, bookY, spineX + 6, bookY + bookH, 0x66000000, 0x33000000);
        for (int i = 0; i < 4; i++) {
            int ry = bookY + bookH * (i + 1) / 5;
            Ui.round(ctx, spineX - 5, ry - 2, 10, 5, 0xFFC9CED6);
            ctx.fill(spineX - 4, ry - 1, spineX + 4, ry, 0xFFFFFFFF);
        }
        for (int side = 0; side < 2; side++) renderPage(ctx, d, spread * 2 + side, side, mouseX, mouseY, t);

        double fp = (t - flipStart) / 0.35;
        if (fp < 1) {
            float s = (float) Math.abs(Math.cos(fp * Math.PI));
            int w = Math.round(pageW * s);
            boolean rightSide = (flipDir > 0) == (fp < 0.5);
            int x0 = rightSide ? spineX + 4 : spineX - 4 - w;
            ctx.fillGradient(x0, bookY + 8, x0 + w, bookY + 8 + pageH, 0xFFF7F4ED, 0xFFDCD7CA);
            Ui.outline(ctx, x0, bookY + 8, Math.max(2, w), pageH, 0xFF8A8577);
        }

        int ay = bookY + bookH / 2 - 12;
        if (spread > 0) {
            Ui.button(ctx, bookX - 24, ay, 18, 24, Text.literal("◀"), mouseX, mouseY, true, 0xFF444A60);
            areas.add(new Area(bookX - 24, ay, 18, 24, () -> turn(-1)));
        }
        if (spread < spreads - 1) {
            Ui.button(ctx, bookX + bookW + 6, ay, 18, 24, Text.literal("▶"), mouseX, mouseY, true, 0xFF444A60);
            areas.add(new Area(bookX + bookW + 6, ay, 18, 24, () -> turn(1)));
        } else if (d.pages() < PersonalBinderData.MAX_PAGES) {
            Ui.button(ctx, bookX + bookW + 6, ay, 18, 24, Text.literal("+"), mouseX, mouseY, true, 0xFF3C8A4A);
            areas.add(new Area(bookX + bookW + 6, ay, 18, 24, () -> ClientCollection.action(BinderActionPayload.PERSONAL_ADD_PAGE, "", 0)));
            if (Ui.in(mouseX, mouseY, bookX + bookW + 6, ay, 18, 24)) setTooltip(Text.translatable("tcgshop.personal.add_pages"));
        }
        Text pages = Text.translatable("tcgshop.personal.pages", spread * 2 + 1, spread * 2 + 2, d.pages(), d.filled());
        int pw = textRenderer.getWidth(pages) + 12;
        Ui.roundGradient(ctx, bookX + bookW / 2 - pw / 2, bookY + bookH + 4, pw, 13, 0xCC12141F, 0xCC0A0B12);
        Ui.text(ctx, pages, bookX + bookW / 2 - pw / 2, bookY + bookH + 4, pw, 13, 0xFFFFFFFF, false);
    }

    private void renderPage(DrawContext ctx, PersonalBinderData d, int page, int side, int mouseX, int mouseY, double t) {
        int spineX = bookX + bookW / 2;
        int x = side == 0 ? bookX + 8 : spineX + 4, y = bookY + 8;
        ctx.fillGradient(x, y, x + pageW, y + pageH, 0xFFF7F4ED, 0xFFE6E1D4);
        Ui.outline(ctx, x, y, pageW, pageH, 0xFF8A8577);
        int pw = pocketW(), ph = pocketH();
        boolean placing = dragging && dragCard != null;
        for (int i = 0; i < 9; i++) {
            int slot = page * 9 + i;
            int px = pocketX(side, i), py = pocketY(i);
            boolean hover = Ui.in(mouseX, mouseY, px, py, pw, ph) && !customizing;
            String id = slot < d.slots().size() ? d.slots().get(slot) : "";
            int bg = hover && placing ? 0x5543C463 : hover ? 0x40A0C8FF : 0x2090A0B0;
            ctx.fill(px, py, px + pw, py + ph, bg);
            Ui.outline(ctx, px, py, pw, ph, hover && placing ? 0xFF43C463 : 0x7070788A);
            boolean wish = id.startsWith(BinderServer.WISH);
            String cardKey = wish ? id.substring(BinderServer.WISH.length()) : id;
            if (!id.isEmpty() && !(dragging && dragFromSlot == slot)) {
                Catalog.Card c = Catalog.card(cardKey);
                int cw = Math.min(pw - 4, Math.round((ph - 4) * Draw.CARD_RATIO)), ch = Math.round(cw / Draw.CARD_RATIO);
                int cx = px + (pw - cw) / 2, cy = py + (ph - ch) / 2;
                if (c != null) {
                    ctx.fill(cx + 1, cy + 2, cx + cw + 1, cy + ch + 2, 0x55000000);
                    Draw.cardFace(ctx, c.texture(), c.stack(), cx, cy, cw, ch, 1f);
                    if (wish) {
                        boolean owned = ClientCollection.count(cardKey) > 0;
                        ctx.fill(cx, cy, cx + cw, cy + ch, owned ? 0x8010301A : 0xB0101018);
                        ctx.drawText(textRenderer, "♥", cx + cw / 2 - 3, cy + ch / 2 - 4, owned ? 0xFF8BE08B : 0xFFFF6FA8, true);
                    } else if (hover && c.rarity().tier > 0) Draw.holo(ctx, cx, cy, cw, ch, c.rarity().tier, c.rarity().color, t, mouseX);
                    if (hover) {
                        List<OrderedText> tip = new ArrayList<>();
                        tip.add(c.name().asOrderedText());
                        tip.add(c.rarity().displayName().copy().withColor(c.rarity().color & 0xFFFFFF).asOrderedText());
                        tip.add(Text.literal(PriceService.format(PriceService.priceCd(c.stack()))).withColor(0xFFD34D).asOrderedText());
                        if (wish) tip.add(Text.translatable(ClientCollection.count(cardKey) > 0 ? "tcgshop.personal.wish_owned" : "tcgshop.personal.wish_missing")
                                .formatted(Formatting.GRAY).asOrderedText());
                        else tip.add(Text.translatable("tcgshop.personal.click_return").formatted(Formatting.DARK_GRAY).asOrderedText());
                        tip.add(Text.translatable("tcgshop.binder.zoom_hint_personal").formatted(Formatting.DARK_GRAY).asOrderedText());
                        setTooltip(tip);
                    }
                }
            } else if (id.isEmpty() && hover) {
                ctx.drawCenteredTextWithShadow(textRenderer, "+", px + pw / 2, py + ph / 2 - 4, placing ? 0xFF43C463 : 0x883A7BD5);
            }
            ctx.fill(px + 2, py + 2, px + pw / 3, py + 3, 0x55FFFFFF); // plastic sleeve reflection
        }
        String num = String.valueOf(page + 1);
        ctx.drawText(textRenderer, num, x + pageW / 2 - textRenderer.getWidth(num) / 2, y + pageH - 9, 0xFF8A8577, false);
    }

    private void clickPocket(int slot, String current) {
        if (current.startsWith(BinderServer.WISH)) {
            String key = current.substring(BinderServer.WISH.length());
            if (ClientCollection.count(key) > 0) { // you own it now!: place it for real
                ClientCollection.action(BinderActionPayload.PERSONAL_PLACE, key, slot);
                play(1.4f);
            } else {
                ClientCollection.action(BinderActionPayload.PERSONAL_REMOVE, "", slot);
                play(0.9f);
            }
        } else if (!current.isEmpty()) {
            ClientCollection.action(BinderActionPayload.PERSONAL_REMOVE, "", slot);
            play(0.9f);
        }
    }

    /** Places the card in the first free pocket (current spread first; otherwise, any). */
    private void autoPlace(String card) {
        PersonalBinderData d = data();
        int start = spread * 18;
        int target = -1;
        for (int i = start; i < Math.min(d.slots().size(), start + 18); i++) if (d.slots().get(i).isEmpty()) { target = i; break; }
        if (target < 0) for (int i = 0; i < d.slots().size(); i++) if (d.slots().get(i).isEmpty()) { target = i; break; }
        if (target < 0) {
            if (client != null && client.player != null) client.player.sendMessage(Text.translatable("tcgshop.personal.full"), true);
            return;
        }
        place(card, target);
        int targetSpread = target / 18;
        if (targetSpread != spread) turn(targetSpread - spread);
    }

    private void place(String card, int slot) {
        boolean owned = ClientCollection.count(card) > 0;
        String arg = wishTab && !owned ? BinderServer.WISH + card : card;
        if (!owned && !wishTab) return;
        ClientCollection.action(BinderActionPayload.PERSONAL_PLACE, arg, slot);
        play(1.2f);
    }

    private void turn(int dir) {
        spread += dir;
        flipStart = System.nanoTime() / 1e9;
        flipDir = dir;
        if (client != null) client.getSoundManager().play(PositionedSoundInstance.master(TcgSounds.CARD_FLIP, 0.9f + dir * 0.05f, 0.7f));
    }

    private void play(float pitch) {
        if (client != null) client.getSoundManager().play(PositionedSoundInstance.master(TcgSounds.CARD_SLIDE, pitch, 0.6f));
    }

    // ------------------------------------------------------------------ drawer

    private List<Catalog.Card> drawerCards() {
        List<Catalog.Card> list = new ArrayList<>();
        String q = search.getText().trim().toLowerCase(Locale.ROOT);
        for (Catalog.CardSet s : Catalog.sets())
            for (Catalog.Card c : s.cards()) {
                boolean in = wishTab ? ClientCollection.wishlist.contains(c.key()) : ClientCollection.count(c.key()) > 0;
                if (in && (q.isEmpty() || c.name().getString().toLowerCase(Locale.ROOT).contains(q))) list.add(c);
            }
        return list;
    }

    private int drawerTop() { return drawerY + 40; }

    private void renderDrawer(DrawContext ctx, int mouseX, int mouseY, double t) {
        Ui.leather(ctx, drawerX, drawerY, drawerW, drawerH, 0xFF2B2F42, 0xFF1A1D2B);
        int tw = (drawerW - 12) / 2;
        Ui.chip(ctx, drawerX + 5, drawerY + 5, tw, 14, Text.translatable("tcgshop.personal.tab_mine"), !wishTab,
                Ui.in(mouseX, mouseY, drawerX + 5, drawerY + 5, tw, 14), 0xFFE0B33C, 0);
        areas.add(new Area(drawerX + 5, drawerY + 5, tw, 14, () -> { wishTab = false; drawerScroll = 0; }));
        Ui.chip(ctx, drawerX + 7 + tw, drawerY + 5, tw, 14, Text.translatable("tcgshop.personal.tab_wish"), wishTab,
                Ui.in(mouseX, mouseY, drawerX + 7 + tw, drawerY + 5, tw, 14), 0xFFFF6FA8, 0);
        areas.add(new Area(drawerX + 7 + tw, drawerY + 5, tw, 14, () -> { wishTab = true; drawerScroll = 0; }));

        List<Catalog.Card> cards = drawerCards();
        int cols = 3, cw = (drawerW - 14) / cols - 2, ch = Math.round(cw / Draw.CARD_RATIO);
        int top = drawerTop(), h = drawerH - 44;
        int rows = (cards.size() + cols - 1) / cols;
        drawerScroll = MathHelper.clamp(drawerScroll, 0, Math.max(0, rows * (ch + 3) - h));
        ctx.enableScissor(drawerX + 2, top, drawerX + drawerW - 2, top + h);
        for (int i = 0; i < cards.size(); i++) {
            int x = drawerX + 6 + (i % cols) * (cw + 2), y = top + (i / cols) * (ch + 3) - (int) drawerScroll;
            if (y + ch < top || y > top + h) continue;
            Catalog.Card c = cards.get(i);
            boolean hover = Ui.in(mouseX, mouseY, x, y, cw, ch) && Ui.in(mouseX, mouseY, drawerX, top, drawerW, h);
            int n = ClientCollection.count(c.key());
            Draw.cardFace(ctx, c.texture(), c.stack(), x, y, cw, ch, 1f);
            if (n == 0) ctx.fill(x, y, x + cw, y + ch, 0xB0101018);
            Ui.outline(ctx, x, y, cw, ch, hover ? 0xFFFFFFFF : 0xFF000000);
            if (n > 1) ctx.drawText(textRenderer, "x" + n, x + cw - textRenderer.getWidth("x" + n) - 1, y + ch - 8, 0xFFFFFFFF, true);
            if (wishTab) ctx.drawText(textRenderer, "♥", x + 2, y + 2, n > 0 ? 0xFF8BE08B : 0xFFFF6FA8, true);
            if (hover) setTooltip(List.of(c.name().asOrderedText(), Text.translatable("tcgshop.personal.drawer_tip").formatted(Formatting.DARK_GRAY).asOrderedText(),
                    Text.translatable("tcgshop.binder.zoom_hint_personal").formatted(Formatting.DARK_GRAY).asOrderedText()));
        }
        ctx.disableScissor();
        if (cards.isEmpty()) {
            int y = top + 10;
            for (var line : textRenderer.wrapLines(Text.translatable(wishTab ? "tcgshop.personal.wish_empty" : "tcgshop.personal.drawer_empty"), drawerW - 12)) {
                ctx.drawTextWithShadow(textRenderer, line, drawerX + 6, y, Ui.MUTED);
                y += 10;
            }
        }
    }

    // ------------------------------------------------------------------ customization

    private void renderCustomize(DrawContext ctx, int mouseX, int mouseY, double t, PersonalBinderData d) {
        int w = Math.min(360, width - 40), h = Math.min(240, height - 40);
        int x = (width - w) / 2, y = (height - h) / 2;
        ctx.getMatrices().push();
        ctx.getMatrices().translate(0, 0, 300);
        ctx.fill(0, 0, width, height, 0x88000000);
        Ui.leather(ctx, x, y, w, h, 0xFF2B2F42, 0xFF181B27);
        Ui.text(ctx, Text.translatable("tcgshop.personal.customize").formatted(Formatting.BOLD), x, y + 5, w, 12, Ui.GOLD, true);
        String[] tabs = {"palette", "pattern", "background"};
        int tw = (w - 20) / 3;
        for (int i = 0; i < tabs.length; i++) {
            int tx = x + 8 + i * (tw + 2);
            String tab = tabs[i];
            Ui.chip(ctx, tx, y + 22, tw, 16, Text.translatable("tcgshop.style.tab." + tab), customizeTab.equals(tab), Ui.in(mouseX, mouseY, tx, y + 22, tw, 16), 0xFFB983FF, 0);
            areas.add(new Area(tx, y + 22, tw, 16, () -> customizeTab = tab));
        }
        List<BinderStyles.Style> styles = BinderStyles.ofKind(customizeTab);
        int sw = 56, sh = 36, cols = Math.max(1, (w - 16) / (sw + 6));
        String current = switch (customizeTab) {
            case "background" -> d.background();
            case "pattern" -> d.pattern();
            default -> d.palette();
        };
        for (int i = 0; i < styles.size(); i++) {
            BinderStyles.Style s = styles.get(i);
            int sx = x + 8 + (i % cols) * (sw + 6), sy = y + 44 + (i / cols) * (sh + 16);
            boolean open = unlocked.contains(s.kind() + "=" + s.id());
            boolean hover = Ui.in(mouseX, mouseY, sx, sy, sw, sh);
            ctx.enableScissor(sx, sy, sx + sw, sy + sh);
            switch (s.kind()) {
                case "background" -> StylePainter.background(ctx, s.id(), sx, sy, sw, sh, t);
                case "pattern" -> StylePainter.cover(ctx, d.palette(), s.id(), sx, sy, sw, sh, t);
                default -> StylePainter.cover(ctx, s.id(), "none", sx, sy, sw, sh, t);
            }
            ctx.disableScissor();
            if (!open) {
                ctx.fill(sx, sy, sx + sw, sy + sh, 0xB0000000);
                Ui.outline(ctx, sx + sw / 2 - 4, sy + sh / 2 - 9, 8, 7, 0xFFCCCCCC);
                Ui.round(ctx, sx + sw / 2 - 6, sy + sh / 2 - 3, 12, 9, 0xFFCCCCCC);
            }
            Ui.outline(ctx, sx, sy, sw, sh, s.id().equals(current) ? Ui.GOLD : hover ? 0xFFFFFFFF : 0xFF000000);
            Ui.text(ctx, Text.translatable(s.translationKey()), sx - 2, sy + sh + 1, sw + 4, 11, open ? Ui.TEXT : Ui.MUTED, false);
            if (hover) {
                if (open) setTooltip(Text.translatable(s.translationKey()));
                else setTooltip(Text.translatable("tcgshop.style.locked",
                        Text.translatable("advancement." + s.advancement().replace(':', '.') + ".title").formatted(Formatting.GOLD)));
            }
            if (open) areas.add(new Area(sx, sy, sw, sh, () -> ClientCollection.action(BinderActionPayload.PERSONAL_STYLE, s.kind() + "=" + s.id(), 0)));
        }
        int bx = x + w - 60, by = y + h - 22;
        Ui.button(ctx, bx, by, 52, 16, Text.translatable("gui.done"), mouseX, mouseY, true, 0xFF3C8A4A);
        areas.add(new Area(bx, by, 52, 16, () -> customizing = false));
        ctx.getMatrices().pop();
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (zoom.isOpen()) return zoom.mouseClicked(mouseX, mouseY, button);
        if (search.mouseClicked(mouseX, mouseY, button)) {
            setFocused(search);
            return true;
        }
        search.setFocused(false);
        // Shift+click or right-click on a card = view it large (a normal click still returns/drags)
        if (!customizing && (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT || hasShiftDown()) && tryZoom(mouseX, mouseY)) return true;
        pressX = mouseX;
        pressY = mouseY;
        dragging = false;
        dragCard = null;
        dragFromSlot = -1;
        if (!customizing) {
            PersonalBinderData d = data();
            int slot = pocketAt(mouseX, mouseY);
            if (slot >= 0 && slot < d.slots().size() && !d.slots().get(slot).isEmpty()) {
                dragCard = d.slots().get(slot);
                dragFromSlot = slot;
                return true;
            }
            String drawer = drawerCardAt(mouseX, mouseY);
            if (drawer != null) {
                dragCard = drawer;
                return true;
            }
        }
        for (int i = areas.size() - 1; i >= 0; i--) {
            Area a = areas.get(i);
            if (Ui.in(mouseX, mouseY, a.x, a.y, a.w, a.h)) {
                a.action.run();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (zoom.isOpen()) return true;
        if (dragCard != null && Math.hypot(mouseX - pressX, mouseY - pressY) > 4) dragging = true;
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragCard == null) return false;
        String card = dragCard;
        int from = dragFromSlot;
        dragCard = null;
        dragFromSlot = -1;
        if (!dragging) {
            if (from >= 0) clickPocket(from, data().slots().get(from));
            else {
                // double-click in the drawer = place in the first free slot
                long now = System.currentTimeMillis();
                if (card.equals(lastDrawerClick) && now - lastDrawerClickTime < 350) {
                    autoPlace(card);
                    lastDrawerClick = null;
                } else {
                    lastDrawerClick = card;
                    lastDrawerClickTime = now;
                }
            }
            return true;
        }
        dragging = false;
        int target = pocketAt(mouseX, mouseY);
        if (from >= 0) {
            if (target < 0 && Ui.in(mouseX, mouseY, drawerX, drawerY, drawerW, drawerH)) {
                ClientCollection.action(BinderActionPayload.PERSONAL_REMOVE, "", from);
                play(0.9f);
            } else if (target >= 0 && target != from) {
                ClientCollection.action(BinderActionPayload.PERSONAL_REMOVE, "", from);
                ClientCollection.action(BinderActionPayload.PERSONAL_PLACE, card, target);
                play(1.1f);
            }
        } else if (target >= 0) {
            place(card, target);
        }
        return true;
    }

    private boolean tryZoom(double mx, double my) {
        String key;
        boolean placed = false; // real card placed in a pocket (no longer counted in the General Binder, but it's yours)
        PersonalBinderData d = data();
        int slot = pocketAt(mx, my);
        if (slot >= 0 && slot < d.slots().size() && !d.slots().get(slot).isEmpty()) {
            key = d.slots().get(slot);
            placed = !key.startsWith(BinderServer.WISH);
        } else key = drawerCardAt(mx, my);
        if (key == null) return false;
        if (key.startsWith(BinderServer.WISH)) key = key.substring(BinderServer.WISH.length());
        Catalog.Card c = Catalog.card(key);
        if (c == null) return false;
        dragCard = null;
        dragFromSlot = -1;
        dragging = false;
        zoom.open(c, placed || ClientCollection.count(key) > 0);
        return true;
    }

    private int pocketAt(double mx, double my) {
        for (int side = 0; side < 2; side++)
            for (int i = 0; i < 9; i++)
                if (Ui.in(mx, my, pocketX(side, i), pocketY(i), pocketW(), pocketH())) return (spread * 2 + side) * 9 + i;
        return -1;
    }

    private String drawerCardAt(double mx, double my) {
        List<Catalog.Card> cards = drawerCards();
        int cols = 3, cw = (drawerW - 14) / cols - 2, ch = Math.round(cw / Draw.CARD_RATIO);
        int top = drawerTop(), h = drawerH - 44;
        if (!Ui.in(mx, my, drawerX, top, drawerW, h)) return null;
        for (int i = 0; i < cards.size(); i++) {
            int x = drawerX + 6 + (i % cols) * (cw + 2), y = top + (i / cols) * (ch + 3) - (int) drawerScroll;
            if (Ui.in(mx, my, x, y, cw, ch)) return cards.get(i).key();
        }
        return null;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double h, double v) {
        if (zoom.isOpen()) return true;
        if (Ui.in(mouseX, mouseY, drawerX, drawerY, drawerW, drawerH)) drawerScroll -= v * 30;
        else if (v != 0 && !customizing) {
            int spreads = Math.max(1, (data().pages() + 1) / 2);
            int ns = MathHelper.clamp(spread - (int) Math.signum(v), 0, spreads - 1);
            if (ns != spread) turn(ns - spread);
        }
        return true;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (zoom.isOpen()) return true;
        return search.isFocused() && search.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (zoom.isOpen()) return zoom.keyPressed(keyCode, scanCode);
        if (search.isFocused() && keyCode != GLFW.GLFW_KEY_ESCAPE) return search.keyPressed(keyCode, scanCode, modifiers) || true;
        if (customizing && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            customizing = false;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}

package dev.tcgshop.client.phone;

import dev.tcgshop.client.binder.Ui;
import dev.tcgshop.phone.PhoneMessages;
import dev.tcgshop.pricing.PriceService;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static dev.tcgshop.client.screen.Draw.withAlpha;

/**
 * 1.0 phone «Messages» app: inbox on the left (unread dot, gold border for the Wholesaler), the selected message on the
 * right (Reply, Delete and, for loan offers, Accept / Decline), and «New» to write to a player (online or offline, by
 * name) or to an NPC contact (@wholesaler, @collector, @grader), who answers after a few seconds.
 */
public final class MessagesApp {
    private static final int SURF = 0xF0171A26, SURF2 = 0xF01F2333, LINE = 0xFF2C3149, GOLD_LINE = 0xFFE5B21E;

    private long selected = -1;
    private boolean compose;
    private double listScroll;
    private TextFieldWidget toField, bodyField;
    /** «Delete» needs a second click within 3 s on read messages that are still pending offers. */
    private double armedDelete = -10;

    private static double now() { return System.nanoTime() / 1e9; }

    private TextRenderer tr() { return MinecraftClient.getInstance().textRenderer; }

    private void ensureFields() {
        if (toField != null) return;
        toField = new TextFieldWidget(tr(), 0, 0, 100, 12, Text.translatable("tcgshop.msg.to"));
        toField.setMaxLength(32);
        toField.setDrawsBackground(false);
        toField.setPlaceholder(Text.translatable("tcgshop.msg.to_hint").formatted(Formatting.DARK_GRAY));
        bodyField = new TextFieldWidget(tr(), 0, 0, 100, 12, Text.translatable("tcgshop.msg.body"));
        bodyField.setMaxLength(PhoneMessages.MAX_BODY);
        bodyField.setDrawsBackground(false);
        bodyField.setPlaceholder(Text.translatable("tcgshop.msg.body_hint").formatted(Formatting.DARK_GRAY));
    }

    /** Opening the app: asks for the inbox again (it is also pushed on every change). */
    public void onEnter() {
        PhoneAppsClient.requestMessages();
        listScroll = 0;
        unfocus();
    }

    /** Opens that message (from a notice or the Loans app). */
    public void open(long id) {
        compose = false;
        selected = id;
        PhoneAppsClient.Msg m = PhoneAppsClient.find(id);
        if (m != null) PhoneAppsClient.markRead(m);
        int idx = 0;
        for (PhoneAppsClient.Msg x : PhoneAppsClient.inbox) { if (x.id() == id) break; idx++; }
        listScroll = Math.max(0, (idx - 2) * 30);
        unfocus();
    }

    /** Starts writing to someone (empty = choose). */
    public void composeTo(String to) {
        ensureFields();
        compose = true;
        toField.setText(to == null ? "" : to);
        bodyField.setText("");
        toField.setFocused(to == null || to.isEmpty());
        bodyField.setFocused(to != null && !to.isEmpty());
    }

    public boolean typing() {
        return toField != null && (toField.isFocused() || bodyField.isFocused());
    }

    private void unfocus() {
        if (toField == null) return;
        toField.setFocused(false);
        bodyField.setFocused(false);
    }

    // ------------------------------------------------------------------ drawing

    public void render(DrawContext ctx, int x, int y, int w, int h, int mouseX, int mouseY, float delta, UpgradesPanel.Sink sink) {
        ensureFields();
        int lw = MathHelper.clamp(Math.round(w * 0.38f), 140, 200);
        list(ctx, x, y, lw, h, mouseX, mouseY, sink);
        int px = x + lw + 6, pw = w - lw - 6;
        if (compose) composeView(ctx, px, y, pw, h, mouseX, mouseY, delta, sink);
        else {
            PhoneAppsClient.Msg m = PhoneAppsClient.find(selected);
            if (m == null) {
                Ui.roundedGradient(ctx, px, y, pw, h, 8, SURF2, SURF);
                Ui.roundedOutline(ctx, px, y, pw, h, 8, LINE);
                Ui.text(ctx, Text.translatable(PhoneAppsClient.inbox.isEmpty() ? "tcgshop.msg.empty" : "tcgshop.msg.pick"), px, y + h / 2 - 10, pw, 10, Ui.MUTED, false);
                Ui.text(ctx, Text.translatable("tcgshop.msg.empty_hint"), px, y + h / 2 + 4, pw, 10, 0xFF6F7590, false);
            } else messageView(ctx, m, px, y, pw, h, mouseX, mouseY, sink);
        }
    }

    private void list(DrawContext ctx, int x, int y, int w, int h, int mouseX, int mouseY, UpgradesPanel.Sink sink) {
        TextRenderer tr = tr();
        Ui.roundedGradient(ctx, x, y, w, h, 8, SURF2, SURF);
        Ui.roundedOutline(ctx, x, y, w, h, 8, LINE);
        // toolbar
        int bw = (w - 16) / 3, by = y + 4;
        Ui.button(ctx, x + 4, by, bw, 15, Text.translatable("tcgshop.msg.new"), mouseX, mouseY, true, 0xFF2E8A4A);
        sink.add(x + 4, by, bw, 15, () -> composeTo(""));
        boolean anyUnread = PhoneAppsClient.unread() > 0;
        Ui.button(ctx, x + 8 + bw, by, bw, 15, Text.translatable("tcgshop.msg.read_all"), mouseX, mouseY, anyUnread, 0xFF3A6FD8);
        if (anyUnread) sink.add(x + 8 + bw, by, bw, 15, PhoneAppsClient::readAll);
        boolean anyRead = false;
        for (PhoneAppsClient.Msg m : PhoneAppsClient.inbox) if (m.read() && !(m.loanOffer() && m.offerState().isEmpty())) { anyRead = true; break; }
        Ui.button(ctx, x + 12 + bw * 2, by, bw, 15, Text.translatable("tcgshop.msg.clear"), mouseX, mouseY, anyRead, 0xFF7A2A3A);
        if (anyRead) sink.add(x + 12 + bw * 2, by, bw, 15, () -> { PhoneAppsClient.clearRead(); selected = -1; });
        // rows
        int top = y + 23, lh = h - 27, rowH = 30;
        List<PhoneAppsClient.Msg> inbox = PhoneAppsClient.inbox;
        listScroll = MathHelper.clamp(listScroll, 0, Math.max(0, inbox.size() * rowH - lh));
        if (inbox.isEmpty()) Ui.text(ctx, Text.translatable("tcgshop.msg.empty"), x, top + 20, w, 10, Ui.MUTED, false);
        ctx.enableScissor(x + 1, top, x + w - 1, top + lh);
        for (int i = 0; i < inbox.size(); i++) {
            int ry = top + i * rowH - (int) listScroll;
            if (ry + rowH < top || ry > top + lh) continue;
            PhoneAppsClient.Msg m = inbox.get(i);
            boolean sel = m.id() == selected && !compose, hover = Ui.in(mouseX, mouseY, x + 3, ry, w - 6, rowH - 2) && Ui.in(mouseX, mouseY, x, top, w, lh);
            int bg = sel ? 0xFF2B4A7A : hover ? 0xFF2E3348 : m.read() ? 0xFF1B1E2C : 0xFF232842;
            Ui.rounded(ctx, x + 3, ry, w - 6, rowH - 2, 5, bg);
            if (m.gold()) {
                Ui.roundedOutline(ctx, x + 3, ry, w - 6, rowH - 2, 5, GOLD_LINE);
                ctx.fill(x + 4, ry + 3, x + 6, ry + rowH - 5, GOLD_LINE);
            } else if (sel) Ui.roundedOutline(ctx, x + 3, ry, w - 6, rowH - 2, 5, 0xFF6BA6FF);
            if (!m.read()) dev.tcgshop.client.binder.StylePainter.circle(ctx, x + 11, ry + 8, 2, 0xFF4FB4FF);
            String when = when(m.day());
            int ww = tr.getWidth(when);
            ctx.drawText(tr, when, x + w - 8 - ww, ry + 4, 0xFF8A8FA8, false);
            Text from = m.from().copy().formatted(Formatting.BOLD);
            ctx.drawText(tr, Text.literal(Ui.ellipsis(from.getString(), w - 30 - ww)).formatted(Formatting.BOLD), x + 16, ry + 4,
                    m.gold() ? Ui.GOLD : m.read() ? 0xFFCCD2E6 : 0xFFFFFFFF, false);
            String snippet = m.loanOffer() ? offerLine(m).getString() : m.body().getString();
            ctx.drawText(tr, Ui.ellipsis(snippet.replace('\n', ' '), w - 24), x + 16, ry + 16, m.loanOffer() ? 0xFFFFE9A8 : 0xFF9A9FB8, false);
            final long id = m.id();
            if (ry + rowH > top && ry < top + lh) sink.add(x + 3, Math.max(ry, top), w - 6, Math.min(rowH - 2, top + lh - Math.max(ry, top)), () -> open(id));
        }
        ctx.disableScissor();
    }

    private static Text offerLine(PhoneAppsClient.Msg m) {
        String st = m.offerState();
        Text amount = Text.literal(PriceService.format(m.extra().getLong("amount")));
        return st.isEmpty() ? Text.translatable("tcgshop.msg.offer.snippet", amount) : Text.translatable("tcgshop.msg.offer.state." + st);
    }

    private static String when(long day) {
        long ago = PhoneAppsClient.day - day;
        if (ago <= 0) return Text.translatable("tcgshop.phone.today").getString();
        return Text.translatable("tcgshop.phone.day", day + 1).getString();
    }

    private void messageView(DrawContext ctx, PhoneAppsClient.Msg m, int x, int y, int w, int h, int mouseX, int mouseY, UpgradesPanel.Sink sink) {
        TextRenderer tr = tr();
        Ui.roundedGradient(ctx, x, y, w, h, 8, m.gold() ? 0xF02A2414 : SURF2, SURF);
        Ui.roundedOutline(ctx, x, y, w, h, 8, m.gold() ? GOLD_LINE : LINE);
        if (m.gold()) Ui.roundedOutline(ctx, x + 2, y + 2, w - 4, h - 4, 7, withAlpha(GOLD_LINE, 0.35f));
        // header
        ctx.drawText(tr, m.from().copy().formatted(Formatting.BOLD), x + 10, y + 8, m.gold() ? Ui.GOLD : 0xFFFFFFFF, true);
        String when = when(m.day());
        ctx.drawText(tr, when, x + w - 10 - tr.getWidth(when), y + 8, 0xFF8A8FA8, false);
        ctx.fill(x + 8, y + 20, x + w - 8, y + 21, m.gold() ? withAlpha(GOLD_LINE, 0.5f) : LINE);
        int by = y + h - 22;
        // body
        int ty = y + 27;
        ctx.enableScissor(x + 4, y + 22, x + w - 4, by - 2);
        for (OrderedText line : tr.wrapLines(m.body(), w - 20)) {
            ctx.drawText(tr, line, x + 10, ty, 0xFFE4E8F4, false);
            ty += 10;
        }
        if (m.loanOffer()) ty = offerBox(ctx, m, x + 8, ty + 6, w - 16, mouseX, mouseY, sink);
        ctx.disableScissor();
        // buttons
        String reply = m.replyTo();
        int bw = Math.min(90, (w - 24) / 2);
        if (reply != null) {
            Ui.button(ctx, x + 8, by, bw, 16, Text.translatable("tcgshop.msg.reply"), mouseX, mouseY, true, 0xFF3A6FD8);
            sink.add(x + 8, by, bw, 16, () -> composeTo(reply));
        }
        boolean armed = now() - armedDelete < 3;
        Ui.button(ctx, x + w - 8 - bw, by, bw, 16, Text.translatable(armed ? "tcgshop.msg.delete_confirm" : "tcgshop.msg.delete"),
                mouseX, mouseY, true, armed ? 0xFFB02020 : 0xFF7A2A3A);
        sink.add(x + w - 8 - bw, by, bw, 16, () -> {
            boolean pendingOffer = m.loanOffer() && m.offerState().isEmpty();
            if (pendingOffer && now() - armedDelete >= 3) { armedDelete = now(); return; } // deleting a live offer: confirm
            armedDelete = -10;
            PhoneAppsClient.delete(m.id());
            selected = -1;
        });
    }

    /** Loan offer details and its Accept / Decline buttons. Returns the y below it. */
    private int offerBox(DrawContext ctx, PhoneAppsClient.Msg m, int x, int y, int w, int mouseX, int mouseY, UpgradesPanel.Sink sink) {
        TextRenderer tr = tr();
        var x0 = m.extra();
        long amount = x0.getLong("amount");
        double rate = x0.getDouble("rate");
        int term = x0.getInt("term");
        long total = amount + Math.round(amount * rate);
        String st = m.offerState();
        int h = 64 + (st.isEmpty() ? 22 : 0);
        Ui.rounded(ctx, x, y, w, h, 6, 0x553A2A0A);
        Ui.roundedOutline(ctx, x, y, w, h, 6, GOLD_LINE);
        String[][] rows = {
                {"tcgshop.msg.offer.amount", PriceService.format(amount)},
                {"tcgshop.msg.offer.rate", String.format(Locale.ROOT, "%.1f%%", rate * 100)},
                {"tcgshop.msg.offer.term", Text.translatable("tcgshop.msg.offer.days", term).getString()},
                {"tcgshop.msg.offer.total", PriceService.format(total)}};
        int ry = y + 5;
        for (String[] r : rows) {
            ctx.drawText(tr, Text.translatable(r[0]), x + 8, ry, 0xFFD8C8A0, false);
            ctx.drawText(tr, r[1], x + w - 8 - tr.getWidth(r[1]), ry, Ui.GOLD, false);
            ry += 11;
        }
        Text status = st.isEmpty() ? Text.translatable("tcgshop.msg.offer.expires", x0.getLong("exp") + 1)
                : Text.translatable("tcgshop.msg.offer.state." + st);
        ctx.drawText(tr, status, x + 8, ry + 2, st.isEmpty() ? 0xFFAAB0C8 : st.equals("accepted") ? 0xFF6BE07B : 0xFFFF9F6B, false);
        if (st.isEmpty()) {
            boolean busy = PhoneAppsClient.hasLoan();
            int bw = (w - 24) / 2, bby = y + h - 20;
            Ui.button(ctx, x + 8, bby, bw, 16, Text.translatable("tcgshop.msg.offer.accept"), mouseX, mouseY, !busy, 0xFF2E8A4A);
            if (!busy) sink.add(x + 8, bby, bw, 16, () -> PhoneAppsClient.acceptLoan(m.id()));
            Ui.button(ctx, x + 16 + bw, bby, bw, 16, Text.translatable("tcgshop.msg.offer.decline"), mouseX, mouseY, true, 0xFF7A2A3A);
            sink.add(x + 16 + bw, bby, bw, 16, () -> PhoneAppsClient.declineLoan(m.id()));
        }
        return y + h + 4;
    }

    private void composeView(DrawContext ctx, int x, int y, int w, int h, int mouseX, int mouseY, float delta, UpgradesPanel.Sink sink) {
        TextRenderer tr = tr();
        Ui.roundedGradient(ctx, x, y, w, h, 8, SURF2, SURF);
        Ui.roundedOutline(ctx, x, y, w, h, 8, LINE);
        ctx.drawText(tr, Text.translatable("tcgshop.msg.new_title").formatted(Formatting.BOLD), x + 10, y + 8, 0xFFFFFFFF, true);
        int fy = y + 24;
        ctx.drawText(tr, Text.translatable("tcgshop.msg.to"), x + 10, fy + 4, 0xFF9A9FB8, false);
        field(ctx, toField, x + 52, fy, w - 62, mouseX, mouseY, delta);
        // quick picks: NPC contacts and online players
        int cy = fy + 20, cx = x + 10;
        List<String[]> picks = new ArrayList<>();
        for (String c : PhoneMessages.NPC_CONTACTS) picks.add(new String[]{"@" + c, Text.translatable("tcgshop.msg.sender." + c).getString()});
        var mc = MinecraftClient.getInstance();
        if (mc.getNetworkHandler() != null && mc.player != null) {
            List<PlayerListEntry> online = new ArrayList<>(mc.getNetworkHandler().getPlayerList());
            online.sort((a, b) -> a.getProfile().getName().compareToIgnoreCase(b.getProfile().getName()));
            for (PlayerListEntry e : online) {
                if (e.getProfile().getId().equals(mc.player.getUuid())) continue;
                picks.add(new String[]{e.getProfile().getName(), e.getProfile().getName()});
            }
        }
        for (String[] p : picks) {
            Text label = Text.literal(p[0].startsWith("@") ? "★ " + p[1] : p[1]);
            int cw = Ui.chipWidth(label) + 4;
            if (cx + cw > x + w - 8) {
                cx = x + 10;
                cy += 17;
                if (cy > y + h - 80) break;
            }
            boolean sel = toField.getText().equalsIgnoreCase(p[0]);
            Ui.chip(ctx, cx, cy, cw, 14, label, sel, Ui.in(mouseX, mouseY, cx, cy, cw, 14), p[0].startsWith("@") ? Ui.GOLD : 0xFF6BC5FF, 0);
            final String to = p[0];
            sink.add(cx, cy, cw, 14, () -> {
                toField.setText(to);
                toField.setFocused(false);
                bodyField.setFocused(true);
            });
            cx += cw + 4;
        }
        int my = cy + 22;
        ctx.drawText(tr, Text.translatable("tcgshop.msg.body"), x + 10, my + 4, 0xFF9A9FB8, false);
        field(ctx, bodyField, x + 52, my, w - 62, mouseX, mouseY, delta);
        String count = bodyField.getText().length() + "/" + PhoneMessages.MAX_BODY;
        ctx.drawText(tr, count, x + w - 10 - tr.getWidth(count), my + 18, 0xFF6F7590, false);
        paragraphs(ctx, Text.translatable("tcgshop.msg.compose_hint"), x + 10, my + 30, w - 20, 0xFF8A90A8, y + h - 26);
        int by = y + h - 22, bw = Math.min(90, (w - 24) / 2);
        boolean can = !toField.getText().isBlank() && !bodyField.getText().isBlank();
        Ui.button(ctx, x + 8, by, bw, 16, Text.translatable("gui.cancel"), mouseX, mouseY, true, 0xFF39415E);
        sink.add(x + 8, by, bw, 16, () -> { compose = false; unfocus(); });
        Ui.button(ctx, x + w - 8 - bw, by, bw, 16, Text.translatable("tcgshop.msg.send"), mouseX, mouseY, can, 0xFF2E8A4A);
        if (can) sink.add(x + w - 8 - bw, by, bw, 16, this::send);
    }

    private void paragraphs(DrawContext ctx, Text t, int x, int y, int w, int color, int maxY) {
        for (OrderedText line : tr().wrapLines(t, w)) {
            if (y + 9 > maxY) break;
            ctx.drawText(tr(), line, x, y, color, false);
            y += 10;
        }
    }

    private void field(DrawContext ctx, TextFieldWidget f, int x, int y, int w, int mouseX, int mouseY, float delta) {
        Ui.round(ctx, x, y, w, 15, f.isFocused() ? 0xEE0E1018 : 0xAA0E1018);
        Ui.outline(ctx, x, y, w, 15, f.isFocused() ? 0xFFFFFFFF : 0x66FFFFFF);
        f.setX(x + 4);
        f.setY(y + 4);
        f.setWidth(w - 8);
        f.render(ctx, mouseX, mouseY, delta);
    }

    private void send() {
        String to = toField.getText().trim(), body = bodyField.getText().trim();
        if (to.isEmpty() || body.isEmpty()) return;
        PhoneAppsClient.sendMessage(to, body);
        bodyField.setText("");
        compose = false;
        unfocus();
    }

    // ------------------------------------------------------------------ input

    /** Clicks on the text fields (everything else goes through the phone's click areas). */
    public boolean mouseClicked(double mx, double my, int button) {
        if (!compose || toField == null) return false;
        for (TextFieldWidget f : new TextFieldWidget[]{toField, bodyField}) {
            if (Ui.in(mx, my, f.getX() - 4, f.getY() - 4, f.getWidth() + 8, 15)) {
                toField.setFocused(f == toField);
                bodyField.setFocused(f == bodyField);
                f.mouseClicked(mx, my, button);
                return true;
            }
        }
        unfocus();
        return false;
    }

    public boolean charTyped(char chr, int modifiers) {
        if (!compose || toField == null) return false;
        if (toField.isFocused()) return toField.charTyped(chr, modifiers);
        if (bodyField.isFocused()) return bodyField.charTyped(chr, modifiers);
        return false;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!compose || !typing()) return false;
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            unfocus();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_TAB) {
            boolean toBody = toField.isFocused();
            toField.setFocused(!toBody);
            bodyField.setFocused(toBody);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (toField.isFocused()) {
                toField.setFocused(false);
                bodyField.setFocused(true);
            } else send();
            return true;
        }
        TextFieldWidget f = toField.isFocused() ? toField : bodyField;
        f.keyPressed(keyCode, scanCode, modifiers);
        return true; // while typing, no key reaches the phone (E, numbers...)
    }

    public void scroll(double v) {
        listScroll -= v * 30;
    }
}

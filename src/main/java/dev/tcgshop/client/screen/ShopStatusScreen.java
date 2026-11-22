package dev.tcgshop.client.screen;

import dev.tcgshop.client.binder.Ui;
import dev.tcgshop.shop.ShopMissions;
import dev.tcgshop.shop.ShopTier;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

/**
 * Clic derecho del dueño en el bloque de tienda (0.9): estadísticas, aforo y cola, flujo de clientes y las misiones
 * para atraer más gente, con el "Siguiente paso" destacado. Datos de ShopMissions.statusNbt (sólo lectura).
 */
public class ShopStatusScreen extends Screen {
    private static final int GREEN = 0xFF6BE07A, RED = 0xFFFF6B6B, ACCENT = 0xFF3A8FD8;

    private final NbtCompound d;
    private final ShopTier tier;
    private final int missions;
    private final int[] prog, goal;
    private double scroll;
    private int listY, listH, contentH;
    private int closeX, closeY, closeW, closeH;

    public ShopStatusScreen(NbtCompound d) {
        super(Text.translatable("tcgshop.status.missions"));
        this.d = d;
        this.tier = ShopTier.values()[Math.max(0, Math.min(ShopTier.values().length - 1, d.getInt("tier")))];
        this.missions = d.getInt("missions");
        this.prog = d.getIntArray("prog");
        this.goal = d.getIntArray("goal");
    }

    @Override
    public boolean shouldPause() { return false; }

    /** Misiones en las que tiene sentido enseñar "x/y". */
    private static boolean countable(ShopMissions.Mission m) {
        return switch (m) {
            case STOCK, LIGHT, DECOR, VARIETY, REVIEWS -> true;
            default -> false;
        };
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        super.render(ctx, mx, my, delta);
        int w = Math.min(width - 16, 430), h = Math.min(height - 16, 270);
        int x = (width - w) / 2, y = (height - h) / 2;
        ctx.fill(x + 3, y + 4, x + w + 3, y + h + 4, 0x66000000);
        Ui.roundedGradient(ctx, x, y, w, h, 6, 0xF0222634, 0xF0141620);
        Ui.roundedGradient(ctx, x, y, w, 22, 6, Ui.brighten(tier.color | 0xFF000000, 10), Ui.darken(tier.color | 0xFF000000, 60));
        ctx.fill(x, y + 16, x + w, y + 22, Ui.darken(tier.color | 0xFF000000, 60));
        Ui.roundedOutline(ctx, x, y, w, h, 6, 0x55FFFFFF);
        Text title = Text.translatable("tcgshop.shop.describe", d.getString("owner"), Text.translatable("tcgshop.shop.tier." + tier.key), tier.size, tier.size)
                .formatted(Formatting.BOLD);
        Ui.text(ctx, title, x, y + 3, w, 16, 0xFFFFFFFF, true);

        List<Text> tooltip = null;
        int lw = (int) ((w - 24) * 0.42), lx = x + 8, rx = lx + lw + 8, rw = x + w - 8 - rx;
        int bottom = y + h - 24;

        // ---------------------------------------------------------------- estadísticas
        int ly = y + 28;
        Ui.glass(ctx, lx, ly - 3, lw, bottom - ly + 3, 5);
        ctx.drawTextWithShadow(textRenderer, Text.translatable("tcgshop.status.stats").formatted(Formatting.BOLD), lx + 6, ly + 1, Ui.GOLD);
        ly += 13;
        ly = row(ctx, lx, ly, lw, Text.translatable("tcgshop.status.rep"), Text.literal(d.getInt("rep") + "/100"), 0xFF7FD6FF);
        Ui.progress(ctx, lx + 6, ly - 1, lw - 12, 4, d.getInt("rep") / 100f, 0xFF3AA0E0);
        ly += 6;
        ly = row(ctx, lx, ly, lw, Text.translatable("tcgshop.status.revenue"), Text.literal(d.getString("revenue")), Ui.TEXT);
        ly = row(ctx, lx, ly, lw, Text.translatable("tcgshop.status.sales"), Text.literal(String.valueOf(d.getInt("sales"))), Ui.TEXT);
        ly = row(ctx, lx, ly, lw, Text.translatable("tcgshop.status.balance"), Text.literal(d.getString("balance")), Ui.GOLD);
        ly = row(ctx, lx, ly, lw, Text.translatable("tcgshop.status.profit"), Text.literal(d.getString("profit")), d.getBoolean("profit_neg") ? RED : GREEN);
        int nrev = d.getInt("reviews");
        ly = row(ctx, lx, ly, lw, Text.translatable("tcgshop.status.reviews"),
                nrev == 0 ? Text.literal("-") : Text.translatable("tcgshop.status.reviews_val", String.format("%.1f", d.getDouble("stars")), nrev), 0xFFFFD34D);
        ctx.fill(lx + 6, ly + 1, lx + lw - 6, ly + 2, 0x30FFFFFF);
        ly += 5;
        // aforo
        int inside = d.getInt("inside"), cap = Math.max(1, d.getInt("cap")), queued = d.getInt("queued");
        int capRowY = ly;
        ly = row(ctx, lx, ly, lw, Text.translatable("tcgshop.status.capacity"), Text.literal(inside + "/" + cap), inside >= cap ? RED : GREEN);
        if (Ui.in(mx, my, lx, capRowY, lw, 11)) {
            tooltip = new ArrayList<>();
            tooltip.add(Text.translatable("tcgshop.status.capacity").formatted(Formatting.GOLD));
            tooltip.add(Text.translatable("tcgshop.status.capacity_base", d.getInt("cap_base"), Text.translatable("tcgshop.shop.tier." + tier.key)).formatted(Formatting.GRAY));
            tooltip.add(Text.translatable("tcgshop.status.capacity_bonus", d.getInt("cap_bonus"), ShopMissions.MAX_BONUS).formatted(Formatting.GRAY));
            int capEv = d.getInt("cap_event"); // 0.9: modificador de aforo de los eventos activos
            if (capEv != 0)
                tooltip.add(Text.translatable("tcgshop.status.capacity_event", (capEv > 0 ? "+" : "") + capEv).formatted(capEv > 0 ? Formatting.GREEN : Formatting.RED));
            tooltip.add(Text.translatable("tcgshop.status.capacity_how", ShopMissions.MILESTONE_A, ShopMissions.MILESTONE_B).formatted(Formatting.DARK_GRAY));
            tooltip.add(Text.translatable("tcgshop.status.capacity_queue").formatted(Formatting.DARK_GRAY));
        }
        ly = row(ctx, lx, ly, lw, Text.translatable("tcgshop.status.queue"), Text.literal(queued + "/" + d.getInt("queue_max")), queued > 0 ? 0xFFFFB347 : Ui.MUTED);
        int ovf = d.getInt("ovf_today");
        ly = row(ctx, lx, ly, lw, Text.translatable("tcgshop.status.overflow"), Text.literal(String.valueOf(ovf)), ovf > 0 ? 0xFFFFB347 : Ui.MUTED);
        // flujo
        int flow = d.getInt("flow"), flowMax = Math.max(1, d.getInt("flow_max"));
        int flowRowY = ly;
        ly = row(ctx, lx, ly, lw, Text.translatable("tcgshop.status.flow"), Text.literal(flow + "%"), flow >= 100 ? GREEN : flow >= 70 ? 0xFFFFD34D : 0xFFFFB347);
        Ui.progress(ctx, lx + 6, ly - 1, lw - 12, 5, flow / (float) flowMax, flow >= 100 ? 0xFF3FBF5A : 0xFFE0A030);
        ly += 7;
        if (Ui.in(mx, my, lx, flowRowY, lw, 18)) {
            tooltip = new ArrayList<>();
            tooltip.add(Text.translatable("tcgshop.status.flow").formatted(Formatting.GOLD));
            tooltip.add(Text.translatable("tcgshop.status.flow_tip", (int) Math.round(ShopMissions.FLOW_MIN * 100) + "%", flowMax + "%").formatted(Formatting.GRAY));
        }
        for (OrderedText l : textRenderer.wrapLines(Text.translatable("tcgshop.status.flow_hint"), (int) ((lw - 12) / 0.75f))) {
            if (ly > bottom - 8) break;
            small(ctx, l, lx + 6, ly, Ui.MUTED, 0.75f);
            ly += 7;
        }
        // sugerencia de mejorar la tienda cuando se llena a menudo
        if (d.getBoolean("upgrade") && !d.getString("next_tier").isEmpty()) {
            Text up = Text.translatable("tcgshop.status.upgrade", Text.translatable("tcgshop.shop.tier." + d.getString("next_tier")), d.getInt("next_cap"));
            List<OrderedText> lines = textRenderer.wrapLines(up, lw - 16);
            int bh = 6 + lines.size() * 10;
            int by = Math.max(ly + 2, bottom - bh - 4);
            if (by + bh <= bottom) {
                Ui.rounded(ctx, lx + 4, by, lw - 8, bh, 4, 0x55B8860B);
                Ui.roundedOutline(ctx, lx + 4, by, lw - 8, bh, 4, 0xFFFFD34D);
                int ty = by + 4;
                for (OrderedText l : lines) { ctx.drawTextWithShadow(textRenderer, l, lx + 8, ty, 0xFFFFE9A8); ty += 10; }
            }
        }

        // ---------------------------------------------------------------- misiones
        int ry = y + 28;
        Ui.glass(ctx, rx, ry - 3, rw, bottom - ry + 3, 5);
        int done = ShopMissions.count(missions);
        ctx.drawTextWithShadow(textRenderer, Text.translatable("tcgshop.status.missions").formatted(Formatting.BOLD), rx + 6, ry + 1, Ui.GOLD);
        String cnt = missions < 0 ? "?" : done + "/" + ShopMissions.COUNT;
        ctx.drawTextWithShadow(textRenderer, cnt, rx + rw - 6 - textRenderer.getWidth(cnt), ry + 1, done >= ShopMissions.COUNT ? GREEN : Ui.TEXT);
        ry += 13;
        int next = missions < 0 ? -1 : ShopMissions.nextStep(missions);
        if (missions < 0) {
            for (OrderedText l : textRenderer.wrapLines(Text.translatable("tcgshop.status.not_calc"), rw - 12)) {
                ctx.drawTextWithShadow(textRenderer, l, rx + 6, ry, Ui.MUTED);
                ry += 10;
            }
        } else if (next < 0) {
            List<OrderedText> lines = textRenderer.wrapLines(Text.translatable("tcgshop.status.all_done"), rw - 16);
            int bh = 6 + lines.size() * 10;
            Ui.rounded(ctx, rx + 4, ry, rw - 8, bh, 4, 0x553FBF5A);
            Ui.roundedOutline(ctx, rx + 4, ry, rw - 8, bh, 4, GREEN);
            int ty = ry + 4;
            for (OrderedText l : lines) { ctx.drawTextWithShadow(textRenderer, l, rx + 8, ty, 0xFFD8FFDD); ty += 10; }
            ry += bh + 4;
        } else {
            ShopMissions.Mission m = ShopMissions.Mission.values()[next];
            List<OrderedText> hint = textRenderer.wrapLines(Text.translatable("tcgshop.mission." + m.key + ".hint"), rw - 16);
            if (hint.size() > 2) hint = hint.subList(0, 2);
            int bh = 17 + hint.size() * 10;
            float pulse = (float) (0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 300.0));
            Ui.rounded(ctx, rx + 4, ry, rw - 8, bh, 4, 0x55B8860B);
            Ui.roundedOutline(ctx, rx + 4, ry, rw - 8, bh, 4, Ui.lerpC(0xFFB8860B, 0xFFFFE070, pulse));
            ctx.drawTextWithShadow(textRenderer, Text.translatable("tcgshop.status.next").formatted(Formatting.BOLD)
                    .append(Text.literal(": ").formatted(Formatting.BOLD)).append(Text.translatable("tcgshop.mission." + m.key)), rx + 8, ry + 4, Ui.GOLD);
            int ty = ry + 15;
            for (OrderedText l : hint) { ctx.drawTextWithShadow(textRenderer, l, rx + 8, ty, 0xFFFFE9A8); ty += 10; }
            ry += bh + 4;
        }
        // lista (con rueda del ratón si no cabe)
        listY = ry;
        listH = Math.max(10, bottom - 3 - ry);
        contentH = 0;
        for (ShopMissions.Mission m : ShopMissions.Mission.values()) contentH += ShopMissions.done(missions, m) ? 12 : 20;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, contentH - listH)));
        ctx.enableScissor(rx + 1, listY, rx + rw - 1, listY + listH);
        int iy = listY - (int) scroll;
        for (ShopMissions.Mission m : ShopMissions.Mission.values()) {
            boolean ok = ShopMissions.done(missions, m);
            int rh = ok ? 12 : 20;
            boolean isNext = m.ordinal() == next;
            boolean hover = Ui.in(mx, my, rx + 2, iy, rw - 4, rh) && my >= listY && my < listY + listH;
            if (isNext) Ui.rounded(ctx, rx + 3, iy, rw - 6, rh - 1, 3, 0x33FFD34D);
            else if (hover) Ui.rounded(ctx, rx + 3, iy, rw - 6, rh - 1, 3, 0x22FFFFFF);
            ctx.drawTextWithShadow(textRenderer, ok ? "✔" : "○", rx + 7, iy + 2, ok ? GREEN : isNext ? Ui.GOLD : Ui.MUTED);
            String status = "";
            if (!ok && countable(m) && m.ordinal() < prog.length && m.ordinal() < goal.length)
                status = Math.min(prog[m.ordinal()], 999) + "/" + goal[m.ordinal()];
            int sw = status.isEmpty() ? 0 : textRenderer.getWidth(status) + 4;
            String name = Ui.ellipsis(Text.translatable("tcgshop.mission." + m.key).getString(), rw - 26 - sw);
            ctx.drawTextWithShadow(textRenderer, name, rx + 18, iy + 2, ok ? 0xFFB8E8C0 : isNext ? 0xFFFFFFFF : Ui.TEXT);
            if (!status.isEmpty()) ctx.drawTextWithShadow(textRenderer, status, rx + rw - 6 - textRenderer.getWidth(status), iy + 2, 0xFFFFB347);
            if (!ok) {
                String h1 = Text.translatable("tcgshop.mission." + m.key + ".hint").getString();
                small(ctx, Text.literal(ellipsisScaled(h1, (int) ((rw - 24) / 0.75f))).asOrderedText(), rx + 18, iy + 12, Ui.MUTED, 0.75f);
            }
            if (hover) {
                tooltip = new ArrayList<>();
                tooltip.add(Text.translatable("tcgshop.mission." + m.key).formatted(ok ? Formatting.GREEN : Formatting.GOLD));
                for (OrderedText l : textRenderer.wrapLines(Text.translatable("tcgshop.mission." + m.key + ".hint"), 200))
                    tooltip.add(Text.literal(toPlain(l)).formatted(Formatting.GRAY));
                if (ok) tooltip.add(Text.translatable("tcgshop.status.done").formatted(Formatting.GREEN));
            }
            iy += rh;
        }
        ctx.disableScissor();
        if (contentH > listH) {
            int barH = Math.max(12, listH * listH / contentH);
            int barY = listY + (int) ((listH - barH) * (scroll / Math.max(1, contentH - listH)));
            ctx.fill(rx + rw - 3, listY, rx + rw - 2, listY + listH, 0x22FFFFFF);
            ctx.fill(rx + rw - 3, barY, rx + rw - 2, barY + barH, 0x88FFFFFF);
        }

        // ---------------------------------------------------------------- cerrar
        closeW = 80; closeH = 14; closeX = x + (w - closeW) / 2; closeY = y + h - 19;
        Ui.button(ctx, closeX, closeY, closeW, closeH, Text.translatable("tcgshop.status.close"), mx, my, true, ACCENT);
        if (tooltip != null) ctx.drawTooltip(textRenderer, tooltip, mx, my);
    }

    /** Fila "etiqueta ........ valor". Devuelve la y de la siguiente fila. */
    private int row(DrawContext ctx, int x, int y, int w, Text label, Text value, int color) {
        int vw = textRenderer.getWidth(value);
        String l = Ui.ellipsis(label.getString(), w - 16 - vw);
        ctx.drawTextWithShadow(textRenderer, l, x + 6, y, Ui.MUTED);
        ctx.drawTextWithShadow(textRenderer, value, x + w - 6 - vw, y, color);
        return y + 11;
    }

    private void small(DrawContext ctx, OrderedText t, int x, int y, int color, float s) {
        var m = ctx.getMatrices();
        m.push();
        m.translate(x, y, 0);
        m.scale(s, s, 1);
        ctx.drawText(textRenderer, t, 0, 0, color, false);
        m.pop();
    }

    private String ellipsisScaled(String s, int maxWidth) {
        if (textRenderer.getWidth(s) <= maxWidth) return s;
        while (s.length() > 1 && textRenderer.getWidth(s + "…") > maxWidth) s = s.substring(0, s.length() - 1);
        return s + "…";
    }

    private static String toPlain(OrderedText t) {
        StringBuilder sb = new StringBuilder();
        t.accept((i, style, cp) -> { sb.appendCodePoint(cp); return true; });
        return sb.toString();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        scroll -= vertical * 16;
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (Ui.in(mouseX, mouseY, closeX, closeY, closeW, closeH)) { close(); return true; }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (client != null && client.options.inventoryKey.matchesKey(keyCode, scanCode)) { close(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}

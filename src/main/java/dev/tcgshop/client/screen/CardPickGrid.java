package dev.tcgshop.client.screen;

import dev.masterball.api.card.CardInfo;
import dev.masterball.api.card.Rarity;
import dev.tcgshop.client.binder.Ui;
import dev.tcgshop.pricing.PriceService;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.ToLongFunction;

import static dev.tcgshop.client.screen.Draw.withAlpha;

/**
 * Rejilla con las cartas de tu inventario para elegir una o varias (venta rápida, Mayorista, Coleccionista).
 * Debajo de cada carta: lo que te dan por ella. Se desplaza con la rueda; las elegidas brillan en dorado.
 */
public final class CardPickGrid {
    public final Set<Integer> sel = new LinkedHashSet<>();
    public boolean multi = true;
    /** Hueco destacado (♥, la carta que le interesa al Coleccionista). */
    public int highlight = -1;
    public ToLongFunction<ItemStack> price = st -> 0;
    /** Qué objetos del inventario se muestran (por defecto sólo cartas). */
    public java.util.function.Predicate<ItemStack> accept = CardInfo::isCard;
    /** Precio por unidad con decimales para lo que no es carta (objetos vanilla del Mayorista). */
    public java.util.function.ToDoubleFunction<ItemStack> extra = null;
    /** Mostrar también las cartas del Binder General (casillas 1000+), con una marca "B". */
    public boolean includeBinder = false;

    /** Lo que hay en una casilla (inventario o Binder). */
    public static ItemStack stackAt(PlayerEntity p, int slot) {
        return dev.tcgshop.client.binder.ClientBinderSlots.stack(p, slot);
    }
    private double scroll;
    private final List<int[]> rects = new ArrayList<>();
    private double appearAt = System.nanoTime() / 1e9;

    public static List<Integer> cardSlots(PlayerEntity p) {
        List<Integer> out = new ArrayList<>();
        if (p == null) return out;
        for (int i = 0; i < 36; i++) if (CardInfo.isCard(p.getInventory().getStack(i))) out.add(i);
        return out;
    }

    public List<Integer> slots(PlayerEntity p) {
        List<Integer> out = new ArrayList<>();
        if (p == null) return out;
        for (int i = 0; i < 36; i++) {
            ItemStack st = p.getInventory().getStack(i);
            if (!st.isEmpty() && accept.test(st)) out.add(i);
        }
        if (includeBinder) for (int s : dev.tcgshop.client.binder.ClientBinderSlots.slots()) if (accept.test(stackAt(p, s))) out.add(s);
        return out;
    }

    private double unit(ItemStack st) {
        if (CardInfo.isCard(st)) return price.applyAsLong(st);
        return extra == null ? 0 : extra.applyAsDouble(st);
    }

    private static String money(double v) {
        if (v >= 10 || Math.abs(v - Math.rint(v)) < 1e-6) return PriceService.format((long) Math.floor(v));
        return String.format(java.util.Locale.ROOT, "%.2f", v).replace('.', ',') + dev.tcgshop.TcgShopConfig.INSTANCE.currencySuffix;
    }

    public void scroll(double v) { scroll -= v * 26; }

    public void restartAppear() { appearAt = System.nanoTime() / 1e9; }

    /** Total de lo elegido (precio por unidad × cantidad). */
    public long total(PlayerEntity p) {
        double t = 0;
        for (int s : sel) {
            ItemStack st = stackAt(p, s);
            if (!st.isEmpty() && accept.test(st)) t += unit(st) * st.getCount();
        }
        return (long) Math.floor(t);
    }

    /** Dibuja la rejilla; devuelve la carta bajo el ratón (o vacía). */
    public ItemStack render(DrawContext ctx, int x, int y, int w, int h, int mx, int my, int cw) {
        MinecraftClient mc = MinecraftClient.getInstance();
        TextRenderer tr = mc.textRenderer;
        rects.clear();
        PlayerEntity p = mc.player;
        List<Integer> slots = slots(p);
        sel.retainAll(slots);
        int ch = Math.round(cw / Draw.CARD_RATIO), gap = 6, rowH = ch + 13;
        int cols = Math.max(1, (w - 6 + gap) / (cw + gap)), rows = (slots.size() + cols - 1) / cols;
        scroll = MathHelper.clamp(scroll, 0, Math.max(0, rows * rowH + 6 - h));
        int ox = x + (w - (cols * (cw + gap) - gap)) / 2;
        double t = System.nanoTime() / 1e9;
        ItemStack hovered = ItemStack.EMPTY;
        ctx.enableScissor(x, y, x + w, y + h);
        for (int k = 0; k < slots.size(); k++) {
            int slot = slots.get(k);
            ItemStack st = stackAt(p, slot);
            int cx = ox + (k % cols) * (cw + gap), cy = y + 4 + (k / cols) * rowH - (int) scroll;
            if (cy + rowH < y || cy > y + h) continue;
            float ap = (float) MathHelper.clamp((t - appearAt - Math.min(k, 24) * 0.02) / 0.25, 0, 1);
            int lift = Math.round((1 - (float) Draw.easeOutCubic(ap)) * 10);
            boolean on = sel.contains(slot), hover = Ui.in(mx, my, cx, cy, cw, ch) && Ui.in(mx, my, x, y, w, h);
            Rarity r = Rarity.of(st);
            if (on) Draw.glow(ctx, cx + cw / 2f, cy + ch / 2f, cw, ch, Ui.GOLD, 0.35f);
            else if (slot == highlight) Draw.glow(ctx, cx + cw / 2f, cy + ch / 2f, cw, ch, 0xFFFF6FA8, 0.25f + 0.15f * (float) Math.sin(t * 4));
            ctx.fill(cx + 2, cy + 3 + lift - (hover ? 2 : 0), cx + cw + 2, cy + ch + 3 + lift - (hover ? 2 : 0), 0x55000000);
            boolean card = CardInfo.isCard(st);
            if (card) {
                Draw.cardFace(ctx, CardInfo.texture(CardInfo.id(st)), st, cx, cy + lift - (hover ? 2 : 0), cw, ch, on || hover ? 1f : 0.88f * ap);
                if (r.tier > 0 && hover) Draw.holo(ctx, cx, cy - 2, cw, ch, r.tier, r.color, t, mx);
            } else {
                // objeto vanilla: baldosa con el icono grande
                int ty = cy + lift - (hover ? 2 : 0);
                Ui.roundGradient(ctx, cx, ty, cw, ch, hover ? 0xFF3A4260 : 0xFF2A3047, 0xFF1A1E2E);
                Ui.outline(ctx, cx, ty, cw, ch, 0x44FFFFFF);
                float sc = Math.min(2.2f, (cw - 6) / 16f);
                var mm = ctx.getMatrices();
                mm.push();
                mm.translate(cx + cw / 2f - 8 * sc, ty + ch / 2f - 8 * sc, 0);
                mm.scale(sc, sc, 1);
                ctx.drawItem(st, 0, 0);
                mm.pop();
            }
            if (st.getCount() > 1) {
                String c = "x" + st.getCount();
                Ui.round(ctx, cx + 1, cy + ch - 10 + lift, tr.getWidth(c) + 4, 9, 0xCC000000);
                ctx.drawText(tr, c, cx + 3, cy + ch - 9 + lift, 0xFFFFFFFF, false);
            }
            if (on) {
                Ui.outline(ctx, cx - 1, cy - 1 + lift, cw + 2, ch + 2, Ui.GOLD);
                Ui.round(ctx, cx + cw - 11, cy + 2 + lift, 9, 9, 0xFF2E9C5A);
                ctx.drawText(tr, "✔", cx + cw - 10, cy + 3 + lift, 0xFFFFFFFF, false);
            } else if (slot == highlight) {
                Ui.round(ctx, cx + cw - 11, cy + 2 + lift, 10, 9, 0xFFE0457E);
                ctx.drawText(tr, "♥", cx + cw - 9, cy + 3 + lift, 0xFFFFFFFF, false);
            } else if (hover) Ui.outline(ctx, cx - 1, cy - 3, cw + 2, ch + 2, 0xFFFFFFFF);
            if (dev.tcgshop.collection.BinderSlots.isBinder(slot)) {
                Ui.round(ctx, cx + 1, cy + 1 + lift, 9, 9, 0xEE3A6FD8);
                ctx.drawText(tr, "B", cx + 3, cy + 2 + lift, 0xFFFFFFFF, false);
            }
            String pr = card ? PriceService.format(price.applyAsLong(st)) : money(unit(st) * st.getCount());
            float s = Math.min(1f, (cw + 4f) / Math.max(1, tr.getWidth(pr)));
            var m = ctx.getMatrices();
            m.push();
            m.translate(cx + cw / 2f, cy + ch + 3 + lift, 0);
            m.scale(s, s, 1);
            ctx.drawText(tr, pr, -tr.getWidth(pr) / 2, 0, withAlpha(on ? Ui.GOLD : 0xFFE8D9A8, Math.max(0.1f, ap)), true);
            m.pop();
            if (hover) hovered = st;
            if (cy >= y - ch / 2 && cy + ch <= y + h + ch / 2) rects.add(new int[]{cx, Math.max(cy, y), cw, Math.min(ch, y + h - Math.max(cy, y)), slot});
        }
        ctx.disableScissor();
        if (rows * rowH + 6 > h) {
            float frac = h / (float) (rows * rowH + 6);
            int sh = Math.max(12, Math.round(h * frac)), sy = y + Math.round((float) (scroll / Math.max(1, rows * rowH + 6 - h)) * (h - sh));
            Ui.round(ctx, x + w - 3, sy, 3, sh, 0x88FFFFFF);
        }
        return hovered;
    }

    /** Al arrastrar tras un clic: true = va marcando, false = va desmarcando, null = no se está arrastrando. */
    private Boolean dragAdd;

    /** Clic: elige / quita una carta. Devuelve true si tocó una. Si luego arrastras, se aplica lo mismo a las demás. */
    public boolean click(double mx, double my) {
        for (int[] r : rects) {
            if (!Ui.in(mx, my, r[0], r[1], r[2], r[3])) continue;
            int slot = r[4];
            if (!sel.remove(slot)) {
                if (!multi) sel.clear();
                if (sel.size() < 64) sel.add(slot);
            }
            dragAdd = multi ? sel.contains(slot) : null;
            return true;
        }
        dragAdd = null;
        return false;
    }

    /** Selección rápida: mantener pulsado y pasar el ratón por encima de varias cartas. */
    public boolean drag(double mx, double my) {
        if (dragAdd == null) return false;
        for (int[] r : rects) {
            if (!Ui.in(mx, my, r[0], r[1], r[2], r[3])) continue;
            if (dragAdd) { if (sel.size() < 64) sel.add(r[4]); }
            else sel.remove(r[4]);
            return true;
        }
        return true;
    }

    public void release() { dragAdd = null; }

    public boolean isEmpty() {
        return slots(MinecraftClient.getInstance().player).isEmpty();
    }
}

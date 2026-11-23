package dev.tcgshop.client.screen;

import dev.tcgshop.client.binder.Ui;
import dev.tcgshop.pricing.PriceService;
import dev.tcgshop.shop.ShopMissions;
import dev.tcgshop.shop.ShopTier;
import dev.tcgshop.shop.ShopZones;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Locale;

/**
 * Owner's right-click on the shop block. 1.0: a tabbed shop menu.
 *  · Overview: tier and size, reputation, money, today's sales / revenue / tips, capacity, flow, missions, the luck the
 *    missions give (deals and tips) and your charisma.
 *  · Missions: the 100 missions by category, with progress, completed/total and the "Next step".
 *  · Customers: who is inside or queuing, totals, regulars and the latest reviews.
 *  · Staff: cashier, second register, guard, wages, shift and security.
 * Data from ShopMissions.statusNbt (read-only; reopen the menu to refresh).
 */
public class ShopStatusScreen extends Screen {
    private static final int GREEN = 0xFF6BE07A, RED = 0xFFFF6B6B, ACCENT = 0xFF3A8FD8, ORANGE = 0xFFFFB347, PURPLE = 0xFFC792EA;
    private static final int TAB_OVERVIEW = 0, TAB_MISSIONS = 1, TAB_CUSTOMERS = 2, TAB_STAFF = 3;
    private static final String[] TAB_KEYS = {"overview", "missions", "customers", "staff"};
    /** Last tab opened (kept while the game runs). */
    private static int lastTab = TAB_OVERVIEW;

    private final NbtCompound d;
    private final ShopTier tier;
    private final int missions;
    private final int[] prog;
    private final BitSet done;
    private final int completed, total, next;
    private int tab = lastTab;
    /** Mission category filter (-1 = all). */
    private int cat = -1;
    private double scroll, catScroll;
    private int listY, listH, contentH;
    private int catX, catY, catW, catH, catContentH;
    private int closeX, closeY, closeW, closeH;
    private final int[][] tabBox = new int[4][4];
    private int missionsRowX, missionsRowY, missionsRowW;

    public ShopStatusScreen(NbtCompound d) {
        super(Text.translatable("tcgshop.status.missions"));
        this.d = d;
        ShopTier t = d.contains("tier_key") ? ShopTier.byKey(d.getString("tier_key")) : null;
        this.tier = t != null ? t : ShopTier.byRank(d.getInt("tier"));
        this.missions = d.getInt("missions");
        this.prog = d.getIntArray("prog");
        this.done = BitSet.valueOf(d.getLongArray("done"));
        this.completed = d.contains("completed") ? d.getInt("completed") : done.cardinality();
        this.total = d.contains("total") ? Math.max(1, d.getInt("total")) : ShopMissions.COUNT;
        this.next = missions < 0 ? -1 : ShopMissions.nextStep(done, prog);
    }

    @Override
    public boolean shouldPause() { return false; }

    /** Real zone side (pre-1.0 shops keep theirs). */
    private int size() {
        return d.getInt("size") > 0 ? d.getInt("size") : tier.size;
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        super.render(ctx, mx, my, delta);
        int w = Math.min(width - 16, 460), h = Math.min(height - 16, 290);
        int x = (width - w) / 2, y = (height - h) / 2;
        ctx.fill(x + 3, y + 4, x + w + 3, y + h + 4, 0x66000000);
        Ui.roundedGradient(ctx, x, y, w, h, 6, 0xF0222634, 0xF0141620);
        Ui.roundedGradient(ctx, x, y, w, 22, 6, Ui.brighten(tier.color | 0xFF000000, 10), Ui.darken(tier.color | 0xFF000000, 60));
        ctx.fill(x, y + 16, x + w, y + 22, Ui.darken(tier.color | 0xFF000000, 60));
        Ui.roundedOutline(ctx, x, y, w, h, 6, 0x55FFFFFF);
        Text title = Text.translatable("tcgshop.shop.describe", d.getString("owner"), Text.translatable("tcgshop.shop.tier." + tier.key), size(), size())
                .formatted(Formatting.BOLD);
        Ui.text(ctx, title, x, y + 3, w, 16, 0xFFFFFFFF, true);

        // ---------------------------------------------------------------- tabs
        int tx = x + 8, ty = y + 26;
        for (int i = 0; i < TAB_KEYS.length; i++) {
            Text label = Text.translatable("tcgshop.status.tab." + TAB_KEYS[i]);
            if (i == TAB_MISSIONS) label = label.copy().append(Text.literal(" " + (missions < 0 ? "?" : String.valueOf(completed)) + "/" + total));
            int tw = Ui.chipWidth(label) + 8;
            boolean hover = Ui.in(mx, my, tx, ty, tw, 14);
            Ui.chip(ctx, tx, ty, tw, 14, label, tab == i, hover, i == TAB_MISSIONS ? 0xFFE0A030 : ACCENT, 0);
            tabBox[i] = new int[]{tx, ty, tw, 14};
            tx += tw + 4;
        }

        int top = y + 46, bottom = y + h - 24;
        List<Text> tooltip = switch (tab) {
            case TAB_MISSIONS -> renderMissions(ctx, x, top, w, bottom, mx, my);
            case TAB_CUSTOMERS -> renderCustomers(ctx, x, top, w, bottom, mx, my);
            case TAB_STAFF -> renderStaff(ctx, x, top, w, bottom);
            default -> renderOverview(ctx, x, top, w, bottom, mx, my);
        };

        // ---------------------------------------------------------------- close
        closeW = 80; closeH = 14; closeX = x + (w - closeW) / 2; closeY = y + h - 19;
        Ui.button(ctx, closeX, closeY, closeW, closeH, Text.translatable("tcgshop.status.close"), mx, my, true, ACCENT);
        if (tooltip != null) ctx.drawTooltip(textRenderer, tooltip, mx, my);
    }

    // ==================================================================== overview

    private List<Text> renderOverview(DrawContext ctx, int x, int top, int w, int bottom, int mx, int my) {
        List<Text> tooltip = null;
        int lw = (w - 24) / 2, lx = x + 8, rx = lx + lw + 8, rw = x + w - 8 - rx;
        missionsRowW = 0;

        // ---- left: the shop and today
        int ly = top;
        Ui.glass(ctx, lx, ly - 3, lw, bottom - ly + 3, 5);
        ctx.drawTextWithShadow(textRenderer, Text.translatable("tcgshop.status.shop").formatted(Formatting.BOLD), lx + 6, ly + 1, Ui.GOLD);
        ly += 13;
        ly = row(ctx, lx, ly, lw, Text.translatable("tcgshop.status.tier"),
                Text.translatable("tcgshop.status.tier_val", Text.translatable("tcgshop.shop.tier." + tier.key), size(), size()), Ui.brighten(tier.color | 0xFF000000, 30));
        ly = row(ctx, lx, ly, lw, Text.translatable("tcgshop.status.rep"), Text.literal(d.getInt("rep") + "/100"), 0xFF7FD6FF);
        Ui.progress(ctx, lx + 6, ly - 1, lw - 12, 4, d.getInt("rep") / 100f, 0xFF3AA0E0);
        ly += 6;
        ly = row(ctx, lx, ly, lw, Text.translatable("tcgshop.status.sales"), Text.literal(String.valueOf(d.getInt("sales"))), Ui.TEXT);
        ly = row(ctx, lx, ly, lw, Text.translatable("tcgshop.status.revenue"), Text.literal(d.getString("revenue")), Ui.TEXT);
        ly = row(ctx, lx, ly, lw, Text.translatable("tcgshop.status.balance"), Text.literal(d.getString("balance")), Ui.GOLD);
        ly = row(ctx, lx, ly, lw, Text.translatable("tcgshop.status.profit"), Text.literal(d.getString("profit")), d.getBoolean("profit_neg") ? RED : GREEN);
        int nrev = d.getInt("reviews");
        ly = row(ctx, lx, ly, lw, Text.translatable("tcgshop.status.reviews"),
                nrev == 0 ? Text.literal("-") : Text.translatable("tcgshop.status.reviews_val", String.format(Locale.ROOT, "%.1f", d.getDouble("stars")), nrev), 0xFFFFD34D);
        ctx.fill(lx + 6, ly + 1, lx + lw - 6, ly + 2, 0x30FFFFFF);
        ly += 5;
        if (ly + 11 <= bottom) {
            ctx.drawTextWithShadow(textRenderer, Text.translatable("tcgshop.status.today").formatted(Formatting.BOLD), lx + 6, ly, Ui.GOLD);
            ly += 12;
        }
        if (ly + 11 <= bottom) ly = row(ctx, lx, ly, lw, Text.translatable("tcgshop.status.sales_today"), Text.literal(String.valueOf(d.getInt("sales_today"))), GREEN);
        if (ly + 11 <= bottom) ly = row(ctx, lx, ly, lw, Text.translatable("tcgshop.status.revenue_today"), Text.literal(d.getString("revenue_today")), GREEN);
        if (ly + 11 <= bottom) {
            int tipRowY = ly;
            ly = row(ctx, lx, ly, lw, Text.translatable("tcgshop.status.tips_today"), Text.literal(d.getString("tips_today")), 0xFFE6EE9C);
            if (Ui.in(mx, my, lx, tipRowY, lw, 11)) {
                tooltip = new ArrayList<>();
                tooltip.add(Text.translatable("tcgshop.status.tips_today").formatted(Formatting.GOLD));
                tooltip.add(Text.translatable("tcgshop.status.tips_all", d.getString("tips_total"), d.getInt("tips_count"), d.getString("tips_best")).formatted(Formatting.GRAY));
            }
        }
        if (ly + 11 <= bottom)
            row(ctx, lx, ly, lw, Text.translatable("tcgshop.status.best_day"),
                    Text.translatable("tcgshop.status.best_day_val", d.getInt("best_day_sales"), d.getString("best_day_revenue")), Ui.MUTED);

        // ---- right: capacity, flow, missions, luck and charisma
        int ry = top;
        Ui.glass(ctx, rx, ry - 3, rw, bottom - ry + 3, 5);
        ctx.drawTextWithShadow(textRenderer, Text.translatable("tcgshop.status.stats").formatted(Formatting.BOLD), rx + 6, ry + 1, Ui.GOLD);
        ry += 13;
        int inside = d.getInt("inside"), cap = Math.max(1, d.getInt("cap")), queued = d.getInt("queued");
        int capRowY = ry;
        ry = row(ctx, rx, ry, rw, Text.translatable("tcgshop.status.capacity"), Text.literal(inside + "/" + cap), inside >= cap ? RED : GREEN);
        if (Ui.in(mx, my, rx, capRowY, rw, 11)) tooltip = capacityTooltip();
        ry = row(ctx, rx, ry, rw, Text.translatable("tcgshop.status.queue"), Text.literal(queued + "/" + d.getInt("queue_max")), queued > 0 ? ORANGE : Ui.MUTED);
        // flow
        int flow = d.getInt("flow"), flowMax = Math.max(1, d.getInt("flow_max"));
        int flowRowY = ry;
        ry = row(ctx, rx, ry, rw, Text.translatable("tcgshop.status.flow"), Text.literal(flow + "%"), flow >= 100 ? GREEN : flow >= 70 ? 0xFFFFD34D : ORANGE);
        Ui.progress(ctx, rx + 6, ry - 1, rw - 12, 4, flow / (float) flowMax, flow >= 100 ? 0xFF3FBF5A : 0xFFE0A030);
        ry += 6;
        if (Ui.in(mx, my, rx, flowRowY, rw, 16)) {
            tooltip = new ArrayList<>();
            tooltip.add(Text.translatable("tcgshop.status.flow").formatted(Formatting.GOLD));
            for (OrderedText l : textRenderer.wrapLines(Text.translatable("tcgshop.status.flow_tip2", (int) Math.round(ShopMissions.FLOW_MIN * 100) + "%",
                    d.getInt("flow_core") + "%", flowMax + "%"), 220))
                tooltip.add(Text.literal(toPlain(l)).formatted(Formatting.GRAY));
            tooltip.add(Text.translatable("tcgshop.status.flow_hint").formatted(Formatting.DARK_GRAY));
        }
        // missions (click: missions tab)
        int misRowY = ry;
        ry = row(ctx, rx, ry, rw, Text.translatable("tcgshop.status.missions_done"), Text.literal((missions < 0 ? "?" : String.valueOf(completed)) + "/" + total),
                completed >= total ? GREEN : 0xFFFFD34D);
        Ui.progress(ctx, rx + 6, ry - 1, rw - 12, 4, missions < 0 ? 0 : completed / (float) total, 0xFFE0A030);
        ry += 6;
        missionsRowX = rx; missionsRowY = misRowY; missionsRowW = rw;
        if (Ui.in(mx, my, rx, misRowY, rw, 16)) {
            Ui.rounded(ctx, rx + 2, misRowY - 1, rw - 4, 17, 3, 0x18FFFFFF);
            tooltip = new ArrayList<>();
            tooltip.add(Text.translatable("tcgshop.status.missions_done").formatted(Formatting.GOLD));
            if (next >= 0) tooltip.add(Text.translatable("tcgshop.status.next").append(": ")
                    .append(Text.translatable("tcgshop.mission." + ShopMissions.Mission.values()[next].key)).formatted(Formatting.YELLOW));
            tooltip.add(Text.translatable("tcgshop.status.missions_click").formatted(Formatting.DARK_GRAY));
        }
        // luck
        int luckRowY = ry;
        ry = row(ctx, rx, ry, rw, Text.translatable("tcgshop.status.luck"),
                Text.translatable("tcgshop.status.luck_val", pct(d.getInt("luck_accept")), pct(d.getInt("luck_tip"))), 0xFF9CE89C);
        if (Ui.in(mx, my, rx, luckRowY, rw, 11)) {
            tooltip = new ArrayList<>();
            tooltip.add(Text.translatable("tcgshop.status.luck").formatted(Formatting.GOLD));
            for (OrderedText l : textRenderer.wrapLines(Text.translatable("tcgshop.status.luck_tip1"), 220)) tooltip.add(Text.literal(toPlain(l)).formatted(Formatting.GRAY));
            tooltip.add(Text.translatable("tcgshop.status.luck_tip2", total, pct(d.getInt("luck_accept_max")), pct(d.getInt("luck_tip_max"))).formatted(Formatting.DARK_GRAY));
        }
        ctx.fill(rx + 6, ry + 1, rx + rw - 6, ry + 2, 0x30FFFFFF);
        ry += 5;
        // charisma
        int lvl = Math.max(1, d.getInt("ch_level")), lmax = Math.max(1, d.getInt("ch_max"));
        int chRowY = ry;
        ry = row(ctx, rx, ry, rw, Text.translatable("tcgshop.status.charisma"), Text.translatable("tcgshop.status.charisma_val", lvl, lmax), PURPLE);
        long xp = d.getLong("ch_xp"), from = d.getLong("ch_from"), to = d.getLong("ch_to");
        float frac = lvl >= lmax || to <= from ? 1f : (xp - from) / (float) (to - from);
        Ui.progress(ctx, rx + 6, ry - 1, rw - 12, 4, frac, 0xFF9C5CD0);
        ry += 6;
        ry = row(ctx, rx, ry, rw, Text.translatable("tcgshop.status.charisma_bonus"), Text.literal(pct(d.getInt("ch_bonus"))), PURPLE);
        if (Ui.in(mx, my, rx, chRowY, rw, ry - chRowY)) {
            tooltip = new ArrayList<>();
            tooltip.add(Text.translatable("tcgshop.status.charisma").formatted(Formatting.LIGHT_PURPLE));
            for (OrderedText l : textRenderer.wrapLines(Text.translatable("tcgshop.status.charisma_tip1"), 220)) tooltip.add(Text.literal(toPlain(l)).formatted(Formatting.GRAY));
            tooltip.add(Text.translatable("tcgshop.status.charisma_tip2", xp, lvl >= lmax ? xp : to).formatted(Formatting.GRAY));
            tooltip.add(Text.translatable("tcgshop.status.charisma_tip3", d.getInt("ch_haggles"), d.getInt("ch_deals"), d.getInt("ch_tips")).formatted(Formatting.GRAY));
            for (OrderedText l : textRenderer.wrapLines(Text.translatable("tcgshop.status.charisma_tip4", pct(d.getInt("ch_bonus_max"))), 220))
                tooltip.add(Text.literal(toPlain(l)).formatted(Formatting.DARK_GRAY));
        }
        // suggestion to upgrade the shop when it fills up often
        if (d.getBoolean("upgrade") && !d.getString("next_tier").isEmpty()) {
            Text up = Text.translatable("tcgshop.status.upgrade", Text.translatable("tcgshop.shop.tier." + d.getString("next_tier")), d.getInt("next_cap"));
            List<OrderedText> lines = textRenderer.wrapLines(up, rw - 16);
            int bh = 6 + lines.size() * 10;
            int by = Math.max(ry + 2, bottom - bh - 4);
            if (by + bh <= bottom) {
                Ui.rounded(ctx, rx + 4, by, rw - 8, bh, 4, 0x55B8860B);
                Ui.roundedOutline(ctx, rx + 4, by, rw - 8, bh, 4, 0xFFFFD34D);
                int yy = by + 4;
                for (OrderedText l : lines) { ctx.drawTextWithShadow(textRenderer, l, rx + 8, yy, 0xFFFFE9A8); yy += 10; }
            }
        }
        return tooltip;
    }

    private List<Text> capacityTooltip() {
        List<Text> tooltip = new ArrayList<>();
        tooltip.add(Text.translatable("tcgshop.status.capacity").formatted(Formatting.GOLD));
        tooltip.add(Text.translatable("tcgshop.status.capacity_base", d.getInt("cap_base"), Text.translatable("tcgshop.shop.tier." + tier.key)).formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("tcgshop.status.capacity_bonus", d.getInt("cap_bonus"), ShopMissions.MAX_BONUS).formatted(Formatting.GRAY));
        int capEv = d.getInt("cap_event"); // 0.9: capacity modifier from active events
        if (capEv != 0)
            tooltip.add(Text.translatable("tcgshop.status.capacity_event", (capEv > 0 ? "+" : "") + capEv).formatted(capEv > 0 ? Formatting.GREEN : Formatting.RED));
        for (OrderedText l : textRenderer.wrapLines(Text.translatable("tcgshop.status.capacity_how2", ShopMissions.MILESTONE_A, ShopMissions.MILESTONE_B,
                ShopMissions.MILESTONE_C, ShopMissions.MILESTONE_D), 220))
            tooltip.add(Text.literal(toPlain(l)).formatted(Formatting.DARK_GRAY));
        for (OrderedText l : textRenderer.wrapLines(Text.translatable("tcgshop.status.capacity_queue"), 220))
            tooltip.add(Text.literal(toPlain(l)).formatted(Formatting.DARK_GRAY));
        return tooltip;
    }

    // ==================================================================== missions

    private List<ShopMissions.Mission> filtered() {
        List<ShopMissions.Mission> out = new ArrayList<>();
        for (ShopMissions.Mission m : ShopMissions.Mission.values()) if (cat < 0 || m.cat.ordinal() == cat) out.add(m);
        // not done first (easiest first), then the done ones
        out.sort((a, b) -> {
            boolean da = done.get(a.ordinal()), db = done.get(b.ordinal());
            if (da != db) return da ? 1 : -1;
            if (!da && a.diff != b.diff) return Integer.compare(a.diff, b.diff);
            return Integer.compare(a.ordinal(), b.ordinal());
        });
        return out;
    }

    private List<Text> renderMissions(DrawContext ctx, int x, int top, int w, int bottom, int mx, int my) {
        List<Text> tooltip = null;
        // ---- left: categories
        catX = x + 8; catY = top; catW = Math.min(128, (w - 24) * 32 / 100); catH = bottom - top;
        Ui.glass(ctx, catX, catY - 3, catW, catH + 3, 5);
        ShopMissions.Category[] cats = ShopMissions.Category.values();
        catContentH = (cats.length + 1) * 12;
        catScroll = Math.max(0, Math.min(catScroll, Math.max(0, catContentH - catH)));
        ctx.enableScissor(catX + 1, catY, catX + catW - 1, catY + catH);
        int cy = catY - (int) catScroll;
        for (int i = -1; i < cats.length; i++) {
            int dn = 0, tt = 0;
            for (ShopMissions.Mission m : ShopMissions.Mission.values())
                if (i < 0 || m.cat.ordinal() == i) { tt++; if (done.get(m.ordinal())) dn++; }
            boolean sel = cat == i, hover = Ui.in(mx, my, catX + 2, cy, catW - 4, 12) && my >= catY && my < catY + catH;
            if (sel) Ui.rounded(ctx, catX + 3, cy, catW - 6, 11, 3, 0x44FFD34D);
            else if (hover) Ui.rounded(ctx, catX + 3, cy, catW - 6, 11, 3, 0x22FFFFFF);
            int color = i < 0 ? Ui.GOLD : cats[i].color;
            ctx.fill(catX + 6, cy + 3, catX + 10, cy + 7, color);
            String cnt = dn + "/" + tt;
            int cw = textRenderer.getWidth(cnt);
            String name = Ui.ellipsis(Text.translatable(i < 0 ? "tcgshop.status.cat_all" : "tcgshop.mission.cat." + cats[i].key).getString(), catW - 22 - cw);
            ctx.drawTextWithShadow(textRenderer, name, catX + 13, cy + 2, sel ? 0xFFFFFFFF : Ui.TEXT);
            ctx.drawTextWithShadow(textRenderer, cnt, catX + catW - 6 - cw, cy + 2, dn >= tt ? GREEN : Ui.MUTED);
            cy += 12;
        }
        ctx.disableScissor();
        scrollbar(ctx, catX + catW - 3, catY, catH, catContentH, catScroll);

        // ---- right: header, next step and the list
        int rx = catX + catW + 8, rw = x + w - 8 - rx, ry = top;
        Ui.glass(ctx, rx, ry - 3, rw, bottom - ry + 3, 5);
        Text head = Text.translatable(cat < 0 ? "tcgshop.status.missions" : "tcgshop.mission.cat." + ShopMissions.Category.values()[cat].key).formatted(Formatting.BOLD);
        ctx.drawTextWithShadow(textRenderer, head, rx + 6, ry + 1, Ui.GOLD);
        String cnt = missions < 0 ? "?" : completed + "/" + total;
        ctx.drawTextWithShadow(textRenderer, cnt, rx + rw - 6 - textRenderer.getWidth(cnt), ry + 1, completed >= total ? GREEN : Ui.TEXT);
        ry += 11;
        Text luck = Text.translatable("tcgshop.status.luck_line", pct(d.getInt("luck_accept")), pct(d.getInt("luck_tip")));
        small(ctx, Text.literal(ellipsisScaled(luck.getString(), (int) ((rw - 12) / 0.75f))).asOrderedText(), rx + 6, ry, 0xFF9CE89C, 0.75f);
        ry += 9;
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
            int yy = ry + 4;
            for (OrderedText l : lines) { ctx.drawTextWithShadow(textRenderer, l, rx + 8, yy, 0xFFD8FFDD); yy += 10; }
            ry += bh + 4;
        } else {
            ShopMissions.Mission m = ShopMissions.Mission.values()[next];
            List<OrderedText> hint = textRenderer.wrapLines(Text.translatable("tcgshop.mission." + m.key + ".hint"), rw - 16);
            if (hint.size() > 2) hint = hint.subList(0, 2);
            int bh = 17 + hint.size() * 10;
            float pulse = (float) (0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 300.0));
            Ui.rounded(ctx, rx + 4, ry, rw - 8, bh, 4, 0x55B8860B);
            Ui.roundedOutline(ctx, rx + 4, ry, rw - 8, bh, 4, Ui.lerpC(0xFFB8860B, 0xFFFFE070, pulse));
            String st = status(m);
            String nt = Text.translatable("tcgshop.status.next").getString() + ": " + Text.translatable("tcgshop.mission." + m.key).getString();
            ctx.drawTextWithShadow(textRenderer, Text.literal(Ui.ellipsis(nt, rw - 22 - textRenderer.getWidth(st))).formatted(Formatting.BOLD), rx + 8, ry + 4, Ui.GOLD);
            if (!st.isEmpty()) ctx.drawTextWithShadow(textRenderer, st, rx + rw - 8 - textRenderer.getWidth(st), ry + 4, ORANGE);
            int yy = ry + 15;
            for (OrderedText l : hint) { ctx.drawTextWithShadow(textRenderer, l, rx + 8, yy, 0xFFFFE9A8); yy += 10; }
            ry += bh + 4;
        }
        // list (scrolls with the mouse wheel if it doesn't fit)
        List<ShopMissions.Mission> list = filtered();
        listY = ry;
        listH = Math.max(10, bottom - 3 - ry);
        contentH = 0;
        for (ShopMissions.Mission m : list) contentH += done.get(m.ordinal()) ? 12 : 20;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, contentH - listH)));
        ctx.enableScissor(rx + 1, listY, rx + rw - 1, listY + listH);
        int iy = listY - (int) scroll;
        for (ShopMissions.Mission m : list) {
            boolean ok = done.get(m.ordinal());
            int rh = ok ? 12 : 20;
            if (iy + rh < listY || iy > listY + listH) { iy += rh; continue; }
            boolean isNext = m.ordinal() == next;
            boolean hover = Ui.in(mx, my, rx + 2, iy, rw - 4, rh) && my >= listY && my < listY + listH;
            if (isNext) Ui.rounded(ctx, rx + 3, iy, rw - 6, rh - 1, 3, 0x33FFD34D);
            else if (hover) Ui.rounded(ctx, rx + 3, iy, rw - 6, rh - 1, 3, 0x22FFFFFF);
            ctx.fill(rx + 4, iy + 2, rx + 5, iy + rh - 2, m.cat.color);
            ctx.drawTextWithShadow(textRenderer, ok ? "✔" : "○", rx + 8, iy + 2, ok ? GREEN : isNext ? Ui.GOLD : Ui.MUTED);
            String status = ok ? "" : status(m);
            int sw = status.isEmpty() ? 0 : textRenderer.getWidth(status) + 4;
            String name = Ui.ellipsis(Text.translatable("tcgshop.mission." + m.key).getString(), rw - 30 - sw);
            ctx.drawTextWithShadow(textRenderer, name, rx + 19, iy + 2, ok ? 0xFFB8E8C0 : isNext ? 0xFFFFFFFF : Ui.TEXT);
            if (!status.isEmpty()) ctx.drawTextWithShadow(textRenderer, status, rx + rw - 7 - textRenderer.getWidth(status), iy + 2, ORANGE);
            if (!ok) {
                String h1 = Text.translatable("tcgshop.mission." + m.key + ".hint").getString();
                small(ctx, Text.literal(ellipsisScaled(h1, (int) ((rw - 26) / 0.75f))).asOrderedText(), rx + 19, iy + 12, Ui.MUTED, 0.75f);
            }
            if (hover) {
                tooltip = new ArrayList<>();
                tooltip.add(Text.translatable("tcgshop.mission." + m.key).formatted(ok ? Formatting.GREEN : Formatting.GOLD));
                tooltip.add(Text.translatable("tcgshop.mission.cat." + m.cat.key).formatted(Formatting.DARK_AQUA));
                for (OrderedText l : textRenderer.wrapLines(Text.translatable("tcgshop.mission." + m.key + ".hint"), 220))
                    tooltip.add(Text.literal(toPlain(l)).formatted(Formatting.GRAY));
                tooltip.add(Text.translatable("tcgshop.status.difficulty", "★".repeat(m.diff) + "☆".repeat(Math.max(0, 5 - m.diff))).formatted(Formatting.YELLOW));
                if (ok) tooltip.add(Text.translatable("tcgshop.status.done").formatted(Formatting.GREEN));
                else tooltip.add(Text.translatable("tcgshop.status.progress", value(m, progress(m)), value(m, m.goal)).formatted(Formatting.GOLD));
            }
            iy += rh;
        }
        ctx.disableScissor();
        scrollbar(ctx, rx + rw - 3, listY, listH, contentH, scroll);
        return tooltip;
    }

    private int progress(ShopMissions.Mission m) {
        return m.ordinal() < prog.length ? prog[m.ordinal()] : 0;
    }

    /** A mission's value (money formatted). */
    private static String value(ShopMissions.Mission m, int v) {
        return m.money ? PriceService.format(v) : String.valueOf(v);
    }

    /** "x/y" for missions with a count, "%" for the shelf one, empty for yes/no missions. */
    private String status(ShopMissions.Mission m) {
        if (m.goal <= 1 || done.get(m.ordinal())) return "";
        int p = Math.max(0, progress(m));
        if (m == ShopMissions.Mission.SHELVES_FULL) return Math.min(p, 100) + "%/" + m.goal + "%";
        if (m.money) return PriceService.format(Math.min(p, m.goal));
        return Math.min(p, 9999) + "/" + m.goal;
    }

    private void scrollbar(DrawContext ctx, int x, int y, int h, int content, double scroll) {
        if (content <= h) return;
        int barH = Math.max(12, h * h / content);
        int barY = y + (int) ((h - barH) * (scroll / Math.max(1, content - h)));
        ctx.fill(x, y, x + 1, y + h, 0x22FFFFFF);
        ctx.fill(x, barY, x + 1, barY + barH, 0x88FFFFFF);
    }

    // ==================================================================== customers

    private List<Text> renderCustomers(DrawContext ctx, int x, int top, int w, int bottom, int mx, int my) {
        List<Text> tooltip = null;
        int lw = (w - 24) * 45 / 100, lx = x + 8, rx = lx + lw + 8, rw = x + w - 8 - rx;
        int ly = top;
        Ui.glass(ctx, lx, ly - 3, lw, bottom - ly + 3, 5);
        ctx.drawTextWithShadow(textRenderer, Text.translatable("tcgshop.status.now").formatted(Formatting.BOLD), lx + 6, ly + 1, Ui.GOLD);
        ly += 13;
        int inside = d.getInt("inside"), cap = Math.max(1, d.getInt("cap")), queued = d.getInt("queued");
        int capRowY = ly;
        ly = row(ctx, lx, ly, lw, Text.translatable("tcgshop.status.capacity"), Text.literal(inside + "/" + cap), inside >= cap ? RED : GREEN);
        if (Ui.in(mx, my, lx, capRowY, lw, 11)) tooltip = capacityTooltip();
        ly = row(ctx, lx, ly, lw, Text.translatable("tcgshop.status.queue"), Text.literal(queued + "/" + d.getInt("queue_max")), queued > 0 ? ORANGE : Ui.MUTED);
        int ovf = d.getInt("ovf_today"), ovp = d.getInt("ovf_prev");
        ly = row(ctx, lx, ly, lw, Text.translatable("tcgshop.status.overflow"), Text.literal(String.valueOf(ovf)), ovf > 0 ? ORANGE : Ui.MUTED);
        ly = row(ctx, lx, ly, lw, Text.translatable("tcgshop.status.overflow_prev"), Text.literal(String.valueOf(ovp)), ovp > 0 ? ORANGE : Ui.MUTED);
        ly = row(ctx, lx, ly, lw, Text.translatable("tcgshop.status.peak"), Text.literal(String.valueOf(d.getInt("peak"))), Ui.TEXT);
        ctx.fill(lx + 6, ly + 1, lx + lw - 6, ly + 2, 0x30FFFFFF);
        ly += 5;
        ctx.drawTextWithShadow(textRenderer, Text.translatable("tcgshop.status.alltime").formatted(Formatting.BOLD), lx + 6, ly, Ui.GOLD);
        ly += 12;
        String[] keys = {"served", "whales", "deals", "complaints", "unattended"};
        int[] vals = {d.getInt("customers"), d.getInt("whales"), d.getInt("deals"), d.getInt("complaints"), d.getInt("unattended")};
        int[] colors = {Ui.TEXT, 0xFF7FD6FF, GREEN, RED, ORANGE};
        for (int i = 0; i < keys.length && ly + 11 <= bottom; i++)
            ly = row(ctx, lx, ly, lw, Text.translatable("tcgshop.status." + keys[i]), Text.literal(String.valueOf(vals[i])), vals[i] == 0 ? Ui.MUTED : colors[i]);

        // ---- right: regulars and reviews
        int ry = top;
        Ui.glass(ctx, rx, ry - 3, rw, bottom - ry + 3, 5);
        ctx.drawTextWithShadow(textRenderer, Text.translatable("tcgshop.status.regulars").formatted(Formatting.BOLD), rx + 6, ry + 1, Ui.GOLD);
        String rn = String.valueOf(d.getInt("regulars_n"));
        ctx.drawTextWithShadow(textRenderer, rn, rx + rw - 6 - textRenderer.getWidth(rn), ry + 1, Ui.TEXT);
        ry += 13;
        NbtList regs = d.getList("regulars", 10);
        if (regs.isEmpty()) {
            for (OrderedText l : textRenderer.wrapLines(Text.translatable("tcgshop.status.regulars_none"), rw - 12)) {
                ctx.drawTextWithShadow(textRenderer, l, rx + 6, ry, Ui.MUTED);
                ry += 10;
            }
        } else {
            for (int i = 0; i < regs.size() && i < 5; i++) {
                NbtCompound c = regs.getCompound(i);
                ry = row(ctx, rx, ry, rw, Text.literal(c.getString("n")), Text.translatable("tcgshop.status.visits", c.getInt("v")), 0xFF9CE89C);
            }
        }
        ctx.fill(rx + 6, ry + 1, rx + rw - 6, ry + 2, 0x30FFFFFF);
        ry += 5;
        ctx.drawTextWithShadow(textRenderer, Text.translatable("tcgshop.status.recent").formatted(Formatting.BOLD), rx + 6, ry, Ui.GOLD);
        ry += 12;
        NbtList rv = d.getList("recent", 10);
        if (rv.isEmpty()) ctx.drawTextWithShadow(textRenderer, Text.translatable("tcgshop.status.reviews_none"), rx + 6, ry, Ui.MUTED);
        for (int i = 0; i < rv.size() && ry + 18 <= bottom; i++) {
            ShopZones.Review r = ShopZones.Review.fromNbt(rv.getCompound(i));
            int s = Math.max(1, Math.min(5, r.stars()));
            String stars = "★".repeat(s) + "☆".repeat(5 - s);
            int sw = textRenderer.getWidth(stars);
            ctx.drawTextWithShadow(textRenderer, stars, rx + 6, ry, s >= 4 ? 0xFFFFD34D : s <= 2 ? RED : Ui.MUTED);
            ctx.drawTextWithShadow(textRenderer, Ui.ellipsis(r.name(), rw - 18 - sw), rx + 10 + sw, ry, Ui.TEXT);
            String txt = Text.translatable(r.textKey()).getString();
            small(ctx, Text.literal(ellipsisScaled(txt, (int) ((rw - 14) / 0.75f))).asOrderedText(), rx + 8, ry + 10, Ui.MUTED, 0.75f);
            if (Ui.in(mx, my, rx, ry, rw, 18)) {
                tooltip = new ArrayList<>();
                tooltip.add(Text.literal(r.name() + "  " + stars).formatted(Formatting.GOLD));
                for (OrderedText l : textRenderer.wrapLines(Text.translatable(r.textKey()), 220)) tooltip.add(Text.literal(toPlain(l)).formatted(Formatting.GRAY));
            }
            ry += 19;
        }
        return tooltip;
    }

    // ==================================================================== staff

    private List<Text> renderStaff(DrawContext ctx, int x, int top, int w, int bottom) {
        int lw = (w - 24) / 2, lx = x + 8, rx = lx + lw + 8, rw = x + w - 8 - rx;
        int ly = top;
        Ui.glass(ctx, lx, ly - 3, lw, bottom - ly + 3, 5);
        ctx.drawTextWithShadow(textRenderer, Text.translatable("tcgshop.status.staff").formatted(Formatting.BOLD), lx + 6, ly + 1, Ui.GOLD);
        ly += 14;
        ly = staffCard(ctx, lx, ly, lw, "cashier", d.getBoolean("cashier"), d.getString("cashier_name"), 0xFF42A5F5);
        ly = staffCard(ctx, lx, ly, lw, "cashier2", d.getBoolean("cashier2"), d.getString("cashier2_name"), 0xFF26A69A);
        ly = staffCard(ctx, lx, ly, lw, "guard", d.getBoolean("guard"), d.getString("guard_name"), 0xFFEF5350);
        ly += 2;
        ly = row(ctx, lx, ly, lw, Text.translatable("tcgshop.status.wages"), Text.literal(d.getString("wages")), ORANGE);
        ly = row(ctx, lx, ly, lw, Text.translatable("tcgshop.status.shift"),
                Text.translatable(d.getBoolean("day_shift") ? "tcgshop.status.shift_day" : "tcgshop.status.shift_night"), Ui.TEXT);
        ly += 2;
        for (OrderedText l : textRenderer.wrapLines(Text.translatable("tcgshop.status.staff_hint"), (int) ((lw - 12) / 0.75f))) {
            if (ly > bottom - 8) break;
            small(ctx, l, lx + 6, ly, Ui.MUTED, 0.75f);
            ly += 7;
        }

        int ry = top;
        Ui.glass(ctx, rx, ry - 3, rw, bottom - ry + 3, 5);
        ctx.drawTextWithShadow(textRenderer, Text.translatable("tcgshop.status.security").formatted(Formatting.BOLD), rx + 6, ry + 1, Ui.GOLD);
        ry += 13;
        int stolen = d.getInt("stolen"), recovered = d.getInt("recovered");
        ry = row(ctx, rx, ry, rw, Text.translatable("tcgshop.status.detectors"), Text.literal(String.valueOf(d.getInt("detectors"))), Ui.TEXT);
        ry = row(ctx, rx, ry, rw, Text.translatable("tcgshop.status.stolen"), Text.literal(String.valueOf(stolen)), stolen > 0 ? RED : Ui.MUTED);
        ry = row(ctx, rx, ry, rw, Text.translatable("tcgshop.status.detained"), Text.literal(String.valueOf(d.getInt("detained"))), GREEN);
        ry = row(ctx, rx, ry, rw, Text.translatable("tcgshop.status.recovered"), Text.literal(String.valueOf(recovered)), GREEN);
        int lost = Math.max(0, stolen - recovered);
        ry = row(ctx, rx, ry, rw, Text.translatable("tcgshop.status.lost"), Text.literal(String.valueOf(lost)), lost > 0 ? RED : Ui.MUTED);
        ctx.fill(rx + 6, ry + 1, rx + rw - 6, ry + 2, 0x30FFFFFF);
        ry += 5;
        ctx.drawTextWithShadow(textRenderer, Text.translatable("tcgshop.status.comfort").formatted(Formatting.BOLD), rx + 6, ry, Ui.GOLD);
        ry += 12;
        ry = row(ctx, rx, ry, rw, Text.translatable("tcgshop.status.seats"), Text.literal(String.valueOf(d.getInt("seats"))), Ui.TEXT);
        ry = row(ctx, rx, ry, rw, Text.translatable("tcgshop.status.play_tables"), Text.literal(String.valueOf(d.getInt("play_tables"))), Ui.TEXT);
        int smell = d.getInt("smell");
        if (ry + 11 <= bottom)
            row(ctx, rx, ry, rw, Text.translatable("tcgshop.status.smell"), Text.literal(smell + "/100"), smell >= 45 ? RED : smell >= 20 ? ORANGE : GREEN);
        return null;
    }

    /** One staff member: role, name or "not hired". Returns the next y. */
    private int staffCard(DrawContext ctx, int x, int y, int w, String role, boolean hired, String name, int color) {
        Ui.rounded(ctx, x + 4, y, w - 8, 22, 4, hired ? 0x33000000 : 0x22000000);
        Ui.roundedOutline(ctx, x + 4, y, w - 8, 22, 4, hired ? (color & 0x00FFFFFF) | 0xAA000000 : 0x33FFFFFF);
        ctx.fill(x + 8, y + 5, x + 11, y + 17, hired ? color : 0xFF555566);
        ctx.drawTextWithShadow(textRenderer, Text.translatable("tcgshop.status." + role).formatted(Formatting.BOLD), x + 15, y + 3, hired ? 0xFFFFFFFF : Ui.MUTED);
        String who = hired ? (name == null || name.isEmpty() ? "-" : name) : Text.translatable("tcgshop.status.not_hired").getString();
        small(ctx, Text.literal(ellipsisScaled(who, (int) ((w - 26) / 0.75f))).asOrderedText(), x + 15, y + 13, hired ? 0xFF9CE89C : Ui.MUTED, 0.75f);
        return y + 25;
    }

    // ==================================================================== helpers

    /** Permille as a percentage ("+7.5%"). */
    private static String pct(int permille) {
        return String.format(Locale.ROOT, "+%.1f%%", permille / 10.0);
    }

    /** Row "label ........ value". Returns the y of the next row. */
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
        if (tab == TAB_MISSIONS && Ui.in(mouseX, mouseY, catX, catY, catW, catH)) catScroll -= vertical * 12;
        else scroll -= vertical * 16;
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (Ui.in(mouseX, mouseY, closeX, closeY, closeW, closeH)) { close(); return true; }
        for (int i = 0; i < tabBox.length; i++) {
            int[] b = tabBox[i];
            if (b[2] > 0 && Ui.in(mouseX, mouseY, b[0], b[1], b[2], b[3])) {
                setTab(i);
                return true;
            }
        }
        if (tab == TAB_OVERVIEW && missionsRowW > 0 && Ui.in(mouseX, mouseY, missionsRowX, missionsRowY, missionsRowW, 16)) {
            setTab(TAB_MISSIONS);
            return true;
        }
        if (tab == TAB_MISSIONS && catW > 0 && Ui.in(mouseX, mouseY, catX, catY, catW, catH)) {
            int i = (int) Math.floor((mouseY - catY + catScroll) / 12) - 1;
            if (i >= -1 && i < ShopMissions.Category.values().length) {
                cat = cat == i ? -1 : i;
                scroll = 0;
                click();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void setTab(int t) {
        if (tab == t) return;
        tab = lastTab = t;
        scroll = 0;
        click();
    }

    private void click() {
        if (client != null && client.player != null) client.player.playSound(net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK.value(), 0.35f, 1.2f);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (client != null && client.options.inventoryKey.matchesKey(keyCode, scanCode)) { close(); return true; }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_TAB) { setTab((tab + 1) % TAB_KEYS.length); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}

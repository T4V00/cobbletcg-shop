package dev.tcgshop.client.screen;

import dev.tcgshop.TcgSounds;
import dev.masterball.api.card.CardInfo;
import dev.tcgshop.client.binder.Ui;
import dev.tcgshop.pricing.PriceService;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static dev.tcgshop.client.screen.Draw.*;

/**
 * Pantalla que aparece al hacer clic derecho con un sobre. Escaparate con el sobre destacado, rejilla con todos
 * tus sobres (sellados y abiertos), contador por tipo, cuántos sobres rasgar a la vez (1, 5, 10, 20, 50, 100),
 * resumen con el valor de mercado y un botón grande para abrir. "Ahora no" no toca nada.
 *
 * Controles en la rejilla: clic = +1 · clic derecho = -1 · rueda = ±1 · Mayús = ±10.
 */
public class PackSelectScreen extends Screen {
    private static final int[] PER_TEAR = {1, 5, 10, 20, 50, 100};

    private record Area(int x, int y, int w, int h, int button, Runnable action) {}

    private record Tile(int x, int y, int w, int h, Entry entry) {}

    private final List<Tile> tiles = new ArrayList<>();

    private static final class Entry {
        final Identifier id;
        final ItemStack stack;
        final int owned;
        final int tier;
        int selected;

        Entry(Identifier id, ItemStack stack, int owned) {
            this.id = id;
            this.stack = stack;
            this.owned = owned;
            this.tier = CardInfo.packTier(stack);
        }
    }

    private static int lastPerTear = 1;

    private final Identifier initial;
    private final List<Entry> entries = new ArrayList<>();
    private final List<Area> areas = new ArrayList<>();
    private Entry featured;
    private int perTear = lastPerTear;
    private double gridScroll;
    private double openTime;

    public PackSelectScreen(Identifier initial) {
        super(Text.translatable("tcgshop.select.title"));
        this.initial = initial;
    }

    @Override
    protected void init() {
        if (entries.isEmpty()) collect();
        openTime = System.nanoTime() / 1e9;
    }

    private void collect() {
        PlayerInventory inv = MinecraftClient.getInstance().player.getInventory();
        Map<Identifier, Integer> counts = new LinkedHashMap<>();
        Map<Identifier, ItemStack> stacks = new LinkedHashMap<>();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack st = inv.getStack(i);
            if (!CardInfo.isPack(st)) continue;
            Identifier id = CardInfo.id(st);
            counts.merge(id, st.getCount(), Integer::sum);
            stacks.putIfAbsent(id, st.copyWithCount(1));
        }
        if (counts.containsKey(initial)) {
            Entry e = new Entry(initial, stacks.get(initial), counts.remove(initial));
            e.selected = 1;
            entries.add(e);
            featured = e;
        }
        counts.forEach((id, n) -> entries.add(new Entry(id, stacks.get(id), n)));
        if (featured == null && !entries.isEmpty()) featured = entries.get(0);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private int total() {
        int n = 0;
        for (Entry e : entries) n += e.selected;
        return n;
    }

    private void change(Entry e, int d) {
        int step = hasShiftDown() ? 10 : 1;
        int ns = MathHelper.clamp(e.selected + d * step, 0, e.owned);
        if (ns != e.selected) {
            e.selected = ns;
            sound(d > 0 ? 1.3f : 0.9f);
        }
        featured = e;
    }

    private void sound(float pitch) {
        if (client != null) client.getSoundManager().play(PositionedSoundInstance.master(TcgSounds.CARD_SLIDE, pitch, 0.4f));
    }

    private void open() {
        List<Identifier> queue = new ArrayList<>();
        for (Entry e : entries) for (int i = 0; i < e.selected; i++) queue.add(e.id);
        if (queue.isEmpty()) return;
        lastPerTear = perTear;
        MinecraftClient.getInstance().setScreen(new PackOpeningScreen(queue, perTear));
    }

    // ------------------------------------------------------------------ render

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fillGradient(0, 0, width, height, 0xE80D0F1C, 0xF5050609);
        // patrón animado de reversos de carta muy tenue
        double t = System.nanoTime() / 1e9;
        int off = (int) (t * 8) % 40;
        for (int y = -40 + off; y < height; y += 40)
            for (int x = ((y / 40) % 2) * 20 - 20; x < width; x += 40) {
                Ui.round(ctx, x, y, 14, 20, 0x0E3E66D6);
                StylePainterLite.dot(ctx, x + 7, y + 10);
            }
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        areas.clear();
        tiles.clear();
        double t = System.nanoTime() / 1e9;
        renderBackground(ctx, mouseX, mouseY, delta);
        float appear = (float) easeOutCubic(MathHelper.clamp((t - openTime) / 0.35, 0, 1));
        MatrixStack m = ctx.getMatrices();
        m.push();
        m.translate(0, (1 - appear) * 20, 0);

        // cabecera
        int bannerW = Math.min(width - 40, 360);
        Ui.roundGradient(ctx, (width - bannerW) / 2, 8, bannerW, 22, 0xFFB3262E, 0xFF6E1218);
        Ui.outline(ctx, (width - bannerW) / 2, 8, bannerW, 22, 0xFF000000);
        scaledCentered(ctx, title.copy().formatted(Formatting.BOLD), width / 2f, 13, 1.3f, Ui.GOLD);

        if (entries.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer, Text.translatable("tcgshop.select.none"), width / 2, height / 2, Ui.MUTED);
            int bw = 120;
            Ui.button(ctx, width / 2 - bw / 2, height / 2 + 20, bw, 20, Text.translatable("tcgshop.select.cancel"), mouseX, mouseY, true, 0xFF444A60);
            areas.add(new Area(width / 2 - bw / 2, height / 2 + 20, bw, 20, 0, this::close));
            m.pop();
            return;
        }

        int bottomH = 58;
        int showW = Math.min(170, width / 3);
        int top = 38, bottom = height - bottomH - 6;
        renderShowcase(ctx, 12, top, showW, bottom - top, mouseX, mouseY, t);
        renderGrid(ctx, 12 + showW + 10, top, width - showW - 34, bottom - top, mouseX, mouseY, t);
        renderBottomBar(ctx, 12, height - bottomH, width - 24, bottomH - 6, mouseX, mouseY, t);
        m.pop();
    }

    private void renderShowcase(DrawContext ctx, int x, int y, int w, int h, int mouseX, int mouseY, double t) {
        Ui.leather(ctx, x, y, w, h, 0xFF262B40, 0xFF161927);
        Entry e = featured;
        if (e == null) return;
        float ph = Math.min(h - 96, (w - 30) / PACK_RATIO), pw = ph * PACK_RATIO;
        float cx = x + w / 2f, cy = y + 12 + ph / 2 + (float) Math.sin(t * 2) * 3;
        int aura = e.tier == 3 ? 0xFFFFD34D : e.tier == 2 ? 0xFFC77DFF : 0xFF7FB2FF;
        rays(ctx, cx, cy, ph * 0.75f, t * 12, aura, 0.06f, 12);
        glow(ctx, cx, cy, pw, ph, aura, 0.25f + 0.1f * (float) Math.sin(t * 3));
        MatrixStack m = ctx.getMatrices();
        m.push();
        m.translate(cx, cy, 0);
        m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) Math.sin(t * 1.3) * 3));
        pack(ctx, CardInfo.packTexture(e.id), e.stack, -pw / 2, -ph / 2, pw, ph, 1f);
        m.pop();

        int ty = Math.round(y + 18 + ph);
        for (var line : textRenderer.wrapLines(e.stack.getName(), w - 12)) {
            ctx.drawCenteredTextWithShadow(textRenderer, line, Math.round(cx), ty, Ui.TEXT);
            ty += 10;
        }
        ctx.drawCenteredTextWithShadow(textRenderer, Text.translatable("masterball.set." + CardInfo.setId(e.id)), Math.round(cx), ty, Ui.MUTED);
        ty += 11;
        Text state = Text.translatable(e.tier == 0 ? "tcgshop.select.state_sealed" : e.tier == 3 ? "tcgshop.tier.god" : e.tier == 2 ? "tcgshop.tier.semigod" : "tcgshop.select.state_opened");
        ctx.drawCenteredTextWithShadow(textRenderer, state, Math.round(cx), ty, aura);
        ty += 11;
        long price = PriceService.sealedCd(CardInfo.setId(e.id), PriceService.Sealed.PACK);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.translatable("tcgshop.select.market", PriceService.format(price)), Math.round(cx), ty, Ui.GOLD);
        ty += 14;
        // contador del destacado
        int bw = 18, gap = 4, cw = 34;
        int rowW = bw * 2 + cw + gap * 2 + 40;
        int bx = Math.round(cx - rowW / 2f);
        Ui.button(ctx, bx, ty, bw, 16, Text.literal("-"), mouseX, mouseY, e.selected > 0, 0xFF444A60);
        areas.add(new Area(bx, ty, bw, 16, 0, () -> change(e, -1)));
        Ui.round(ctx, bx + bw + gap, ty, cw, 16, 0xFF0C0E16);
        String sel = e.selected + "/" + e.owned;
        ctx.drawText(textRenderer, sel, bx + bw + gap + (cw - textRenderer.getWidth(sel)) / 2, ty + 4, e.selected > 0 ? 0xFFFFFFFF : Ui.MUTED, false);
        Ui.button(ctx, bx + bw + gap * 2 + cw, ty, bw, 16, Text.literal("+"), mouseX, mouseY, e.selected < e.owned, 0xFF444A60);
        areas.add(new Area(bx + bw + gap * 2 + cw, ty, bw, 16, 0, () -> change(e, 1)));
        int ax = bx + bw * 2 + gap * 3 + cw;
        Ui.button(ctx, ax, ty, 36, 16, Text.translatable("tcgshop.select.all"), mouseX, mouseY, true, 0xFF2F6FB0);
        areas.add(new Area(ax, ty, 36, 16, 0, () -> { e.selected = e.selected == e.owned ? 0 : e.owned; sound(1.1f); }));
    }

    private void renderGrid(DrawContext ctx, int x, int y, int w, int h, int mouseX, int mouseY, double t) {
        Ui.round(ctx, x, y, w, h, 0xCC0E1019);
        Ui.outline(ctx, x, y, w, h, 0xFF2A2F44);
        int tileW = 62, tileH = 92, gap = 8;
        int cols = Math.max(1, (w - 12 + gap) / (tileW + gap));
        int rows = (entries.size() + cols - 1) / cols;
        int content = rows * (tileH + gap);
        gridScroll = MathHelper.clamp(gridScroll, 0, Math.max(0, content - h + 12));
        int gx = x + (w - (cols * (tileW + gap) - gap)) / 2;
        ctx.enableScissor(x + 1, y + 1, x + w - 1, y + h - 1);
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            int tx = gx + (i % cols) * (tileW + gap), ty = y + 8 + (i / cols) * (tileH + gap) - (int) gridScroll;
            if (ty + tileH < y || ty > y + h) continue;
            boolean hover = Ui.in(mouseX, mouseY, tx, ty, tileW, tileH) && Ui.in(mouseX, mouseY, x, y, w, h);
            boolean sel = e.selected > 0;
            int accent = e.tier == 3 ? 0xFFFFD34D : e.tier == 2 ? 0xFFC77DFF : sel ? 0xFF3A7BD5 : 0xFF3A3F55;
            float lift = hover ? 3 : 0;
            Ui.roundGradient(ctx, tx, Math.round(ty - lift), tileW, tileH, sel ? 0xFF1F3354 : 0xFF1A1D2B, sel ? 0xFF14223A : 0xFF12141E);
            Ui.outline(ctx, tx, Math.round(ty - lift), tileW, tileH, e == featured ? 0xFFFFFFFF : accent);
            float ph = tileH - 26, pw = ph * PACK_RATIO;
            if (sel) glow(ctx, tx + tileW / 2f, ty - lift + 4 + ph / 2, pw, ph, accent, 0.25f);
            pack(ctx, CardInfo.packTexture(e.id), e.stack, tx + (tileW - pw) / 2, ty - lift + 4, pw, ph, sel || hover ? 1f : 0.75f);
            String name = Ui.ellipsis(Text.translatable("masterball.set." + CardInfo.setId(e.id)).getString(), tileW - 6);
            ctx.drawText(textRenderer, name, tx + (tileW - textRenderer.getWidth(name)) / 2, Math.round(ty - lift + tileH - 20), Ui.TEXT, false);
            String own = (e.tier == 0 ? "" : "✂ ") + "x" + e.owned;
            ctx.drawText(textRenderer, own, tx + (tileW - textRenderer.getWidth(own)) / 2, Math.round(ty - lift + tileH - 10), Ui.MUTED, false);
            if (sel) {
                // contador grande en la esquina
                String s = String.valueOf(e.selected);
                int bw = Math.max(14, textRenderer.getWidth(s) + 6);
                Ui.round(ctx, tx + tileW - bw - 2, Math.round(ty - lift + 2), bw, 12, 0xFF3A7BD5);
                Ui.outline(ctx, tx + tileW - bw - 2, Math.round(ty - lift + 2), bw, 12, 0xFFFFFFFF);
                ctx.drawText(textRenderer, s, tx + tileW - bw - 2 + (bw - textRenderer.getWidth(s)) / 2, Math.round(ty - lift + 4), 0xFFFFFFFF, false);
            }
            if (hover) setTooltip(List.of(e.stack.getName().asOrderedText(), Text.translatable("tcgshop.select.controls").formatted(Formatting.DARK_GRAY).asOrderedText()));
            if (ty >= y - tileH) {
                areas.add(new Area(tx, Math.max(ty, y), tileW, tileH, 0, () -> change(e, 1)));
                areas.add(new Area(tx, Math.max(ty, y), tileW, tileH, 1, () -> change(e, -1)));
                tiles.add(new Tile(tx, Math.max(ty, y), tileW, tileH, e));
            }
        }
        ctx.disableScissor();
    }

    private void renderBottomBar(DrawContext ctx, int x, int y, int w, int h, int mouseX, int mouseY, double t) {
        Ui.leather(ctx, x, y, w, h, 0xFF2B2F42, 0xFF181B27);
        int total = total();
        // selector "por rasgada"
        ctx.drawTextWithShadow(textRenderer, Text.translatable("tcgshop.select.per_tear"), x + 10, y + 8, Ui.TEXT);
        int cx = x + 10, cy = y + 20, chipW = 28;
        for (int n : PER_TEAR) {
            boolean selected = perTear == n;
            Ui.chip(ctx, cx, cy, chipW, 18, Text.literal(String.valueOf(n)), selected, Ui.in(mouseX, mouseY, cx, cy, chipW, 18), 0xFFFFD34D, 0);
            int nn = n;
            areas.add(new Area(cx, cy, chipW, 18, 0, () -> { perTear = nn; sound(1.0f + Math.min(nn, 40) / 40f); }));
            cx += chipW + 4;
        }
        // resumen
        int tears = total == 0 ? 0 : (total + perTear - 1) / perTear;
        long value = 0;
        for (Entry e : entries) value += e.selected * PriceService.sealedCd(CardInfo.setId(e.id), PriceService.Sealed.PACK);
        int sx = cx + 12;
        ctx.drawTextWithShadow(textRenderer, Text.translatable("tcgshop.select.summary", total, tears), sx, y + 10, total > 0 ? Ui.TEXT : Ui.MUTED);
        ctx.drawTextWithShadow(textRenderer, Text.translatable("tcgshop.select.value", PriceService.format(value)), sx, y + 24, Ui.GOLD);

        // botones
        int ow = 128, oh = 30, ox = x + w - ow - 10, oy = y + (h - oh) / 2;
        boolean can = total > 0;
        if (can) glow(ctx, ox + ow / 2f, oy + oh / 2f, ow, oh, 0xFFFFD34D, 0.2f + 0.12f * (float) Math.sin(t * 4));
        Ui.button(ctx, ox, oy, ow, oh, Text.translatable(total == 1 ? "tcgshop.select.open_one" : "tcgshop.select.open_many", total).formatted(Formatting.BOLD),
                mouseX, mouseY, can, 0xFFD39B1A);
        if (can) areas.add(new Area(ox, oy, ow, oh, 0, this::open));
        int nw = 76, nx = ox - nw - 8;
        Ui.button(ctx, nx, oy + 6, nw, 18, Text.translatable("tcgshop.select.cancel"), mouseX, mouseY, true, 0xFF444A60);
        areas.add(new Area(nx, oy + 6, nw, 18, 0, this::close));
    }

    // ------------------------------------------------------------------ entrada

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (int i = areas.size() - 1; i >= 0; i--) {
            Area a = areas.get(i);
            if (a.button == button && Ui.in(mouseX, mouseY, a.x, a.y, a.w, a.h)) {
                a.action.run();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double h, double v) {
        // rueda sobre un sobre = ±1; en otra parte de la rejilla = desplazar
        for (Tile tl : tiles) {
            if (Ui.in(mouseX, mouseY, tl.x, tl.y, tl.w, tl.h)) {
                change(tl.entry, v > 0 ? 1 : -1);
                return true;
            }
        }
        gridScroll -= v * 30;
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER && total() > 0) {
            open();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** Mini helper para el fondo. */
    private static final class StylePainterLite {
        static void dot(DrawContext ctx, int x, int y) {
            ctx.fill(x - 2, y - 2, x + 2, y + 2, 0x0EFFFFFF);
        }
    }
}

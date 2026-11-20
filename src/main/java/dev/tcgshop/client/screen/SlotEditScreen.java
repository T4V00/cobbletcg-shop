package dev.tcgshop.client.screen;

import dev.tcgshop.card.CardInfo;
import dev.tcgshop.card.Rarity;
import dev.tcgshop.client.binder.Ui;
import dev.tcgshop.network.UiPayloads;
import dev.tcgshop.pricing.PriceService;
import dev.tcgshop.shop.ShopDisplayBlockEntity;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Clic derecho sobre una carta (o sobre/caja) colocada en un expositor: Tomar carta o Editar carta.
 * Editar: precio (en la tienda), tamaño y giro en los tres ejes, con vista previa en vivo sobre el propio mueble
 * (la ventana deja ver el mundo). Restaurar = aspecto por defecto y precio de mercado.
 */
public class SlotEditScreen extends Screen {
    private final BlockPos pos;
    private final int slot;
    private final ItemStack item;
    private final long market;
    private final boolean home;
    private final float scale0;
    private final int rx0, ry0, rz0;
    private long price;
    private float scale;
    private int rx, ry, rz;
    private boolean editing, sent;
    private final double opened = System.nanoTime() / 1e9;
    private final List<Btn> buttons = new ArrayList<>();

    private record Btn(int x, int y, int w, int h, Runnable r) {}

    public SlotEditScreen(NbtCompound d) {
        super(Text.translatable("tcgshop.slot.title"));
        pos = BlockPos.fromLong(d.getLong("pos"));
        slot = d.getInt("slot");
        var mc = net.minecraft.client.MinecraftClient.getInstance();
        item = mc.world == null ? ItemStack.EMPTY : ItemStack.fromNbt(mc.world.getRegistryManager(), d.get("item")).orElse(ItemStack.EMPTY);
        price = d.getLong("price");
        market = d.getLong("market");
        home = d.getBoolean("home");
        scale = scale0 = d.getFloat("scale");
        rx = rx0 = d.getInt("rx");
        ry = ry0 = d.getInt("ry");
        rz = rz0 = d.getInt("rz");
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // sin fondo oscuro: se ve el mueble con la vista previa
    }

    private ShopDisplayBlockEntity be() {
        var mc = net.minecraft.client.MinecraftClient.getInstance();
        return mc.world != null && mc.world.getBlockEntity(pos) instanceof ShopDisplayBlockEntity b ? b : null;
    }

    /** Vista previa en el mueble (sólo en tu cliente hasta que guardes). */
    private void preview() {
        ShopDisplayBlockEntity b = be();
        if (b != null) b.setLook(slot, scale, rx, ry, rz);
    }

    private void send(String action) {
        if (sent) return;
        sent = true;
        NbtCompound d = new NbtCompound();
        d.putString("action", action);
        d.putLong("pos", pos.asLong());
        d.putInt("slot", slot);
        d.putLong("price", price);
        d.putFloat("scale", scale);
        d.putInt("rx", rx);
        d.putInt("ry", ry);
        d.putInt("rz", rz);
        ClientPlayNetworking.send(new UiPayloads.Act("slot", d));
        super.close();
    }

    @Override
    public void close() {
        // cancelar: deja el mueble como estaba
        if (!sent) {
            ShopDisplayBlockEntity b = be();
            if (b != null) b.setLook(slot, scale0, rx0, ry0, rz0);
        }
        super.close();
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        buttons.clear();
        double t = System.nanoTime() / 1e9;
        float in = (float) Draw.easeOutCubic(MathHelper.clamp((t - opened) / 0.25, 0, 1));
        int w = editing ? 236 : 200, h = editing ? (home ? 176 : 206) : 92;
        int x = width - w - 14 + Math.round((1 - in) * 40), y = (height - h) / 2;
        ctx.fill(x + 3, y + 4, x + w + 3, y + h + 4, 0x55000000);
        Ui.roundGradient(ctx, x, y, w, h, 0xF023273A, 0xF012141F);
        Ui.roundGradient(ctx, x, y, w, 22, 0xFF3A6FD8, 0xFF2A4FA0);
        Draw.scaledCentered(ctx, Text.translatable(editing ? "tcgshop.slot.edit_title" : "tcgshop.slot.title").formatted(Formatting.BOLD), x + w / 2f, y + 7, 1f, 0xFFFFFFFF);
        // miniatura y nombre
        int cw = 34, ch = Math.round(cw / Draw.CARD_RATIO);
        if (CardInfo.isCard(item)) {
            Rarity r = Rarity.of(item);
            Draw.cardFace(ctx, CardInfo.texture(CardInfo.id(item)), item, x + 8, y + 28, cw, ch, 1f);
            if (r.tier > 0) Draw.holo(ctx, x + 8, y + 28, cw, ch, r.tier, r.color, t, mx);
        } else CatalogPanel.drawProduct(ctx, item, x + 8, y + 28, cw, ch);
        int tx = x + cw + 16;
        ctx.drawTextWithShadow(textRenderer, Text.literal(Ui.ellipsis(item.getName().getString(), w - cw - 24)), tx, y + 30, 0xFFFFFFFF);
        if (!home) ctx.drawText(textRenderer, Text.translatable("tcgshop.slot.price_now", PriceService.format(price)), tx, y + 42, Ui.GOLD, false);
        ctx.drawText(textRenderer, Text.translatable("tcgshop.deal.market", PriceService.format(market)), tx, y + (home ? 42 : 53), Ui.MUTED, false);
        if (!editing) {
            int by = y + h - 24, bw = (w - 24) / 2;
            btn(ctx, x + 8, by, bw, 16, Text.translatable("tcgshop.slot.take"), 0xFF8A5A1A, mx, my, () -> send("take"));
            btn(ctx, x + 16 + bw, by, bw, 16, Text.translatable("tcgshop.slot.edit"), 0xFF3A6FD8, mx, my, () -> editing = true);
            return;
        }
        int ry0 = y + 28 + ch + 8;
        if (!home) {
            ctx.drawText(textRenderer, Text.translatable("tcgshop.slot.price"), x + 8, ry0 + 4, 0xFFDDE3F0, false);
            int bx = x + 62;
            bx = step(ctx, bx, ry0, "-10%", mx, my, () -> price = Math.max(1, Math.round(price * 0.9)));
            bx = step(ctx, bx, ry0, "-1%", mx, my, () -> price = Math.max(1, price - Math.max(1, Math.round(price * 0.01))));
            String ps = PriceService.format(price);
            int pw = Math.max(54, textRenderer.getWidth(ps) + 8);
            Ui.round(ctx, bx, ry0, pw, 15, 0xFF0E1018);
            Ui.text(ctx, Text.literal(ps).formatted(Formatting.BOLD), bx, ry0 + 4, pw, 10, Ui.GOLD, false);
            bx += pw + 3;
            bx = step(ctx, bx, ry0, "+1%", mx, my, () -> price = Math.min(1_000_000_000L, price + Math.max(1, Math.round(price * 0.01))));
            step(ctx, bx, ry0, "+10%", mx, my, () -> price = Math.min(1_000_000_000L, Math.round(price * 1.1) + 1));
            ry0 += 19;
            btn(ctx, x + 62, ry0, 80, 13, Text.translatable("tcgshop.slot.market_price"), 0xFF39415E, mx, my, () -> price = Math.max(1, market));
            ry0 += 18;
        }
        // tamaño
        ctx.drawText(textRenderer, Text.translatable("tcgshop.slot.size"), x + 8, ry0 + 4, 0xFFDDE3F0, false);
        int bx = x + 62;
        bx = step(ctx, bx, ry0, "-", mx, my, () -> { scale = Math.max(ShopDisplayBlockEntity.MIN_SCALE, Math.round((scale - 0.05f) * 100) / 100f); preview(); });
        String ss = Math.round(scale * 100) + "%";
        Ui.round(ctx, bx, ry0, 44, 15, 0xFF0E1018);
        Ui.text(ctx, Text.literal(ss), bx, ry0 + 4, 44, 10, 0xFFFFFFFF, false);
        step(ctx, bx + 47, ry0, "+", mx, my, () -> { scale = Math.min(ShopDisplayBlockEntity.MAX_SCALE, Math.round((scale + 0.05f) * 100) / 100f); preview(); });
        ry0 += 19;
        // giros
        String[] axes = {"X", "Y", "Z"};
        for (int a = 0; a < 3; a++) {
            final int ax = a;
            ctx.drawText(textRenderer, Text.translatable("tcgshop.slot.rotate", axes[a]), x + 8, ry0 + 4, 0xFFDDE3F0, false);
            int b2 = x + 62;
            b2 = step(ctx, b2, ry0, "-15", mx, my, () -> rot(ax, -15));
            b2 = step(ctx, b2, ry0, "-5", mx, my, () -> rot(ax, -5));
            int v = a == 0 ? rx : a == 1 ? ry : rz;
            Ui.round(ctx, b2, ry0, 40, 15, 0xFF0E1018);
            Ui.text(ctx, Text.literal(v + "°"), b2, ry0 + 4, 40, 10, 0xFFFFFFFF, false);
            b2 += 43;
            b2 = step(ctx, b2, ry0, "+5", mx, my, () -> rot(ax, 5));
            step(ctx, b2, ry0, "+15", mx, my, () -> rot(ax, 15));
            ry0 += 18;
        }
        int by = y + h - 22, bw = (w - 28) / 3;
        btn(ctx, x + 8, by, bw, 16, Text.translatable("tcgshop.slot.reset"), 0xFF5A2A2A, mx, my, () -> send("reset"));
        btn(ctx, x + 14 + bw, by, bw, 16, Text.translatable("tcgshop.slot.take"), 0xFF8A5A1A, mx, my, () -> send("take"));
        btn(ctx, x + 20 + bw * 2, by, bw, 16, Text.translatable("tcgshop.slot.save"), 0xFF2E8A4A, mx, my, () -> send("save"));
    }

    private void rot(int axis, int d) {
        if (axis == 0) rx = wrap(rx + d);
        else if (axis == 1) ry = wrap(ry + d);
        else rz = wrap(rz + d);
        preview();
    }

    private static int wrap(int deg) { return Math.floorMod(deg + 180, 360) - 180; }

    private int step(DrawContext ctx, int x, int y, String label, int mx, int my, Runnable r) {
        int bw = textRenderer.getWidth(label) + 8;
        btn(ctx, x, y, bw, 15, Text.literal(label), 0xFF39415E, mx, my, r);
        return x + bw + 3;
    }

    private void btn(DrawContext ctx, int x, int y, int w, int h, Text label, int color, int mx, int my, Runnable r) {
        Ui.button(ctx, x, y, w, h, label, mx, my, true, color);
        buttons.add(new Btn(x, y, w, h, r));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (Btn b : new ArrayList<>(buttons))
            if (Ui.in(mouseX, mouseY, b.x, b.y, b.w, b.h)) {
                b.r.run();
                if (client != null && client.player != null) client.player.playSound(net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK.value(), 0.4f, 1.3f);
                return true;
            }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}

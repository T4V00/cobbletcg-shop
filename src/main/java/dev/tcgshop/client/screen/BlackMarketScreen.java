package dev.tcgshop.client.screen;

import dev.tcgshop.client.binder.Ui;
import dev.tcgshop.network.UiPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Black-market dealer: you see the pack on offer, pick which Pokémon from your party to give and confirm twice
 * (pick + "Sí, cambiar"). The trade only happens if the server manages to remove the Pokémon.
 */
public class BlackMarketScreen extends Screen {
    private record Mon(UUID uuid, Text name, int level, boolean shiny, String species) {}

    private final int id;
    private final ItemStack pack;
    private final boolean rare;
    private final List<Mon> mons = new ArrayList<>();
    private int selected = -1;
    private boolean confirming;
    private final double opened = System.nanoTime() / 1e9;
    private final List<int[]> rects = new ArrayList<>();
    private final List<Runnable> acts = new ArrayList<>();

    public BlackMarketScreen(NbtCompound d) {
        super(Text.translatable("tcgshop.bm.title"));
        var mc = net.minecraft.client.MinecraftClient.getInstance();
        id = d.getInt("id");
        pack = mc.world == null ? ItemStack.EMPTY : ItemStack.fromNbt(mc.world.getRegistryManager(), d.get("pack")).orElse(ItemStack.EMPTY);
        rare = d.getBoolean("rare");
        NbtList l = d.getList("mons", 10);
        for (int i = 0; i < l.size(); i++) {
            NbtCompound c = l.getCompound(i);
            Text name;
            try {
                name = mc.world == null ? Text.literal(c.getString("sp")) : Text.Serialization.fromJson(c.getString("n"), mc.world.getRegistryManager());
            } catch (Exception e) {
                name = Text.literal(c.getString("sp"));
            }
            mons.add(new Mon(c.getUuid("u"), name == null ? Text.literal(c.getString("sp")) : name, c.getInt("l"), c.getBoolean("s"), c.getString("sp")));
        }
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        rects.clear();
        acts.clear();
        double t = System.nanoTime() / 1e9;
        ctx.fillGradient(0, 0, width, height, 0xB0100818, 0xE0050208);
        int w = 320, h = 214, x = (width - w) / 2, y = (height - h) / 2;
        float in = (float) MathHelper.clamp((t - opened) / 0.4, 0, 1);
        ctx.fill(x + 3, y + 4, x + w + 3, y + h + 4, 0x88000000);
        Ui.roundGradient(ctx, x, y, w, h, 0xF01A1426, 0xF00A0812);
        Ui.roundGradient(ctx, x, y, w, 22, 0xFF4A2A6A, 0xFF24143A);
        Draw.scaledCentered(ctx, Text.translatable("tcgshop.bm.title").formatted(Formatting.BOLD), x + w / 2f, y + 7, 1f, Draw.withAlpha(0xFFE0D0FF, in));
        // the pack on offer
        int cw = 70, ch = Math.round(cw / Draw.CARD_RATIO), cx = x + 14, cy = y + 32;
        Draw.glow(ctx, cx + cw / 2f, cy + ch / 2f, cw * 1.4f, ch * 1.2f, rare ? 0xFFFFD34D : 0xFFB06BFF, 0.3f + 0.12f * (float) Math.sin(t * 2.5));
        CatalogPanel.drawProduct(ctx, pack, cx, cy, cw, ch);
        Ui.text(ctx, Text.literal(Ui.ellipsis(pack.getName().getString(), cw + 30)), cx - 15, cy + ch + 4, cw + 30, 10, rare ? Ui.GOLD : 0xFFE0D0FF, true);
        if (rare) Ui.text(ctx, Text.translatable("tcgshop.bm.rare").formatted(Formatting.BOLD), cx - 15, cy + ch + 15, cw + 30, 10, Ui.GOLD, false);
        // party
        int lx = x + cw + 34, lw = w - cw - 46;
        ctx.drawTextWithShadow(textRenderer, Text.translatable("tcgshop.bm.pick"), lx, y + 30, 0xFFCFC2E8);
        int ry = y + 44;
        for (int i = 0; i < mons.size(); i++) {
            Mon m = mons.get(i);
            boolean on = i == selected, hover = Ui.in(mx, my, lx, ry, lw, 18);
            Ui.round(ctx, lx, ry, lw, 18, on ? 0xFF5A3A8A : hover ? 0xFF33284A : 0xFF201A30);
            if (on) Ui.outline(ctx, lx, ry, lw, 18, 0xFFE0D0FF);
            Text label = m.name().copy().append(Text.literal("  Nv. " + m.level()).formatted(Formatting.GRAY));
            if (m.shiny()) label = Text.literal("✦ ").formatted(Formatting.GOLD).append(label);
            ctx.drawText(textRenderer, label, lx + 6, ry + 5, 0xFFFFFFFF, false);
            final int k = i;
            rects.add(new int[]{lx, ry, lw, 18});
            acts.add(() -> { selected = k; confirming = false; });
            ry += 21;
        }
        int by = y + h - 28;
        if (selected >= 0) {
            Mon m = mons.get(selected);
            Text q = confirming ? Text.translatable("tcgshop.bm.sure", m.name(), pack.getName()) : Text.translatable("tcgshop.bm.offer", m.name());
            Ui.text(ctx, q, x, by - 14, w, 10, confirming ? 0xFFFF9F6B : 0xFFDDD0F0, false);
            String key = confirming ? "tcgshop.bm.confirm" : "tcgshop.bm.trade";
            Ui.button(ctx, x + w - 150, by, 136, 18, Text.translatable(key), mx, my, true, confirming ? 0xFF8A1F5A : 0xFF5A3A8A);
            rects.add(new int[]{x + w - 150, by, 136, 18});
            acts.add(() -> {
                if (!confirming) { confirming = true; return; }
                NbtCompound d = new NbtCompound();
                d.putInt("id", id);
                d.putUuid("mon", m.uuid());
                ClientPlayNetworking.send(new UiPayloads.Act("black_market", d));
                close();
            });
        } else Ui.text(ctx, Text.translatable("tcgshop.bm.choose"), x, by + 5, w, 10, 0xFF8A80A0, false);
        Ui.button(ctx, x + 14, by, 90, 18, Text.translatable("tcgshop.bm.leave"), mx, my, true, 0xFF39324A);
        rects.add(new int[]{x + 14, by, 90, 18});
        acts.add(this::close);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (int i = 0; i < rects.size(); i++) {
            int[] r = rects.get(i);
            if (Ui.in(mouseX, mouseY, r[0], r[1], r[2], r[3])) {
                acts.get(i).run();
                if (client != null && client.player != null) client.player.playSound(net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK.value(), 0.4f, 0.9f);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}

package dev.tcgshop.client.screen;

import dev.masterball.api.platform.Platform;
import dev.masterball.api.platform.ClientPlatform;
import dev.tcgshop.client.binder.Ui;
import dev.tcgshop.network.UiPayloads;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Black-market dealer: you see the pack on offer, pick which Pokémon from your party to give and confirm twice
 * (pick + "Sí, cambiar"). The trade only happens if the server manages to remove the Pokémon.
 */
public class BlackMarketScreen extends Screen {
    private record Mon(UUID uuid, Component name, int level, boolean shiny, String species) {}

    private final int id;
    private final ItemStack pack;
    private final boolean rare;
    private final List<Mon> mons = new ArrayList<>();
    private int selected = -1;
    private boolean confirming;
    private final double opened = System.nanoTime() / 1e9;
    private final List<int[]> rects = new ArrayList<>();
    private final List<Runnable> acts = new ArrayList<>();

    public BlackMarketScreen(CompoundTag d) {
        super(Component.translatable("tcgshop.bm.title"));
        var mc = net.minecraft.client.Minecraft.getInstance();
        id = d.getInt("id");
        pack = mc.level == null ? ItemStack.EMPTY : ItemStack.parse(mc.level.registryAccess(), d.get("pack")).orElse(ItemStack.EMPTY);
        rare = d.getBoolean("rare");
        ListTag l = d.getList("mons", 10);
        for (int i = 0; i < l.size(); i++) {
            CompoundTag c = l.getCompound(i);
            Component name;
            try {
                name = mc.level == null ? Component.literal(c.getString("sp")) : Component.Serializer.fromJson(c.getString("n"), mc.level.registryAccess());
            } catch (Exception e) {
                name = Component.literal(c.getString("sp"));
            }
            mons.add(new Mon(c.getUUID("u"), name == null ? Component.literal(c.getString("sp")) : name, c.getInt("l"), c.getBoolean("s"), c.getString("sp")));
        }
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void render(GuiGraphics ctx, int mx, int my, float delta) {
        rects.clear();
        acts.clear();
        double t = System.nanoTime() / 1e9;
        ctx.fillGradient(0, 0, width, height, 0xB0100818, 0xE0050208);
        int w = 320, h = 214, x = (width - w) / 2, y = (height - h) / 2;
        float in = (float) Mth.clamp((t - opened) / 0.4, 0, 1);
        ctx.fill(x + 3, y + 4, x + w + 3, y + h + 4, 0x88000000);
        Ui.roundGradient(ctx, x, y, w, h, 0xF01A1426, 0xF00A0812);
        Ui.roundGradient(ctx, x, y, w, 22, 0xFF4A2A6A, 0xFF24143A);
        Draw.scaledCentered(ctx, Component.translatable("tcgshop.bm.title").withStyle(ChatFormatting.BOLD), x + w / 2f, y + 7, 1f, Draw.withAlpha(0xFFE0D0FF, in));
        // the pack on offer
        int cw = 70, ch = Math.round(cw / Draw.CARD_RATIO), cx = x + 14, cy = y + 32;
        Draw.glow(ctx, cx + cw / 2f, cy + ch / 2f, cw * 1.4f, ch * 1.2f, rare ? 0xFFFFD34D : 0xFFB06BFF, 0.3f + 0.12f * (float) Math.sin(t * 2.5));
        CatalogPanel.drawProduct(ctx, pack, cx, cy, cw, ch);
        Ui.text(ctx, Component.literal(Ui.ellipsis(pack.getHoverName().getString(), cw + 30)), cx - 15, cy + ch + 4, cw + 30, 10, rare ? Ui.GOLD : 0xFFE0D0FF, true);
        if (rare) Ui.text(ctx, Component.translatable("tcgshop.bm.rare").withStyle(ChatFormatting.BOLD), cx - 15, cy + ch + 15, cw + 30, 10, Ui.GOLD, false);
        // party
        int lx = x + cw + 34, lw = w - cw - 46;
        ctx.drawString(font, Component.translatable("tcgshop.bm.pick"), lx, y + 30, 0xFFCFC2E8);
        int ry = y + 44;
        for (int i = 0; i < mons.size(); i++) {
            Mon m = mons.get(i);
            boolean on = i == selected, hover = Ui.in(mx, my, lx, ry, lw, 18);
            Ui.round(ctx, lx, ry, lw, 18, on ? 0xFF5A3A8A : hover ? 0xFF33284A : 0xFF201A30);
            if (on) Ui.outline(ctx, lx, ry, lw, 18, 0xFFE0D0FF);
            Component label = m.name().copy().append(Component.literal("  Nv. " + m.level()).withStyle(ChatFormatting.GRAY));
            if (m.shiny()) label = Component.literal("✦ ").withStyle(ChatFormatting.GOLD).append(label);
            ctx.drawString(font, label, lx + 6, ry + 5, 0xFFFFFFFF, false);
            final int k = i;
            rects.add(new int[]{lx, ry, lw, 18});
            acts.add(() -> { selected = k; confirming = false; });
            ry += 21;
        }
        int by = y + h - 28;
        if (selected >= 0) {
            Mon m = mons.get(selected);
            Component q = confirming ? Component.translatable("tcgshop.bm.sure", m.name(), pack.getHoverName()) : Component.translatable("tcgshop.bm.offer", m.name());
            Ui.text(ctx, q, x, by - 14, w, 10, confirming ? 0xFFFF9F6B : 0xFFDDD0F0, false);
            String key = confirming ? "tcgshop.bm.confirm" : "tcgshop.bm.trade";
            Ui.button(ctx, x + w - 150, by, 136, 18, Component.translatable(key), mx, my, true, confirming ? 0xFF8A1F5A : 0xFF5A3A8A);
            rects.add(new int[]{x + w - 150, by, 136, 18});
            acts.add(() -> {
                if (!confirming) { confirming = true; return; }
                CompoundTag d = new CompoundTag();
                d.putInt("id", id);
                d.putUUID("mon", m.uuid());
                ClientPlatform.INSTANCE.send(new UiPayloads.Act("black_market", d));
                onClose();
            });
        } else Ui.text(ctx, Component.translatable("tcgshop.bm.choose"), x, by + 5, w, 10, 0xFF8A80A0, false);
        Ui.button(ctx, x + 14, by, 90, 18, Component.translatable("tcgshop.bm.leave"), mx, my, true, 0xFF39324A);
        rects.add(new int[]{x + 14, by, 90, 18});
        acts.add(this::onClose);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (int i = 0; i < rects.size(); i++) {
            int[] r = rects.get(i);
            if (Ui.in(mouseX, mouseY, r[0], r[1], r[2], r[3])) {
                acts.get(i).run();
                if (minecraft != null && minecraft.player != null) minecraft.player.playSound(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 0.4f, 0.9f);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}

package dev.tcgshop.client.screen;

import dev.masterball.api.platform.ClientPlatform;
import dev.tcgshop.client.ShopClient;
import dev.tcgshop.client.binder.Ui;
import dev.tcgshop.network.UiPayloads;
import dev.tcgshop.pricing.PriceService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * 1.1 (staff update): cash register window, opened by right-clicking your register (or your cashier) and from the phone's
 * Shop app. Two modes:
 *  · Withdraw: 25 / 50 / 75 / 100 % of what is in the register or a custom amount;
 *  · Deposit: 25 / 50 / 75 / 100 % of your wallet or a custom amount (e.g. to cover wages).
 * The big button asks "Are you sure?" first (Yes / Cancel). The balance shown is the live one from the zone sync, and the
 * server always re-checks it (a percentage is applied to the balance at that moment).
 */
public class RegisterScreen extends Screen {
    private static final int[] PCTS = {25, 50, 75, 100};
    private static final int GREEN = 0xFF2E8A4A, BLUE = 0xFF3A6FD8, GREY = 0xFF39415E;

    private final UUID zone;
    @Nullable
    private final Screen parent;
    private boolean deposit;
    /** Selected percentage (0 = custom amount). */
    private int pct = 100;
    private boolean confirming;
    private EditBox custom;
    private double doneAt = -10;
    private final List<Btn> buttons = new ArrayList<>();

    private record Btn(int x, int y, int w, int h, Runnable r) {}

    public RegisterScreen(UUID zone, boolean deposit, @Nullable Screen parent) {
        super(Component.translatable("tcgshop.register.title"));
        this.zone = zone;
        this.deposit = deposit;
        this.parent = parent;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private int panelW() { return Math.min(width - 16, 280); }

    private int panelH() { return 196; }

    @Override
    protected void init() {
        int w = panelW(), x = (width - w) / 2, y = (height - panelH()) / 2;
        custom = new EditBox(font, x + 14 + 4 * 46, y + 104, w - 28 - 4 * 46, 16, Component.translatable("tcgshop.register.custom"));
        custom.setMaxLength(13);
        custom.setFilter(s -> s.chars().allMatch(Character::isDigit));
        custom.setHint(Component.translatable("tcgshop.register.custom").withStyle(ChatFormatting.DARK_GRAY));
        custom.setResponder(s -> { if (!s.isEmpty()) { pct = 0; confirming = false; } });
        addRenderableWidget(custom);
    }

    @Nullable
    private ShopClient.ZoneInfo zoneInfo() {
        for (ShopClient.ZoneInfo z : ShopClient.zones) if (z.id().equals(zone)) return z;
        return null;
    }

    private long balance() {
        ShopClient.ZoneInfo z = zoneInfo();
        return z == null ? 0 : Math.max(0, z.balance());
    }

    private long wallet() {
        var mc = net.minecraft.client.Minecraft.getInstance();
        return mc.player != null && dev.tcgshop.economy.CobbleDollarsBridge.isAvailable() ? Math.max(0, dev.tcgshop.economy.CobbleDollarsBridge.getBalance(mc.player)) : 0;
    }

    private long customAmount() {
        String s = custom == null ? "" : custom.getValue();
        if (s.isEmpty()) return 0;
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** What the button would move right now. */
    private long amount() {
        long base = deposit ? wallet() : balance();
        if (pct > 0) return pct >= 100 ? base : Math.round(base * pct / 100.0);
        return Math.min(base, customAmount());
    }

    private void send() {
        CompoundTag d = new CompoundTag();
        d.putUUID("zone", zone);
        if (!deposit && pct > 0) d.putInt("pct", pct);
        d.putLong("amount", amount());
        ClientPlatform.INSTANCE.send(new UiPayloads.Act(deposit ? "shop_deposit" : "shop_withdraw", d));
        confirming = false;
        doneAt = System.nanoTime() / 1e9;
        if (custom != null) custom.setValue("");
        pct = 100;
    }

    @Override
    public void render(GuiGraphics ctx, int mx, int my, float delta) {
        buttons.clear();
        renderBackground(ctx, mx, my, delta);
        double t = System.nanoTime() / 1e9;
        int w = panelW(), h = panelH(), x = (width - w) / 2, y = (height - h) / 2;
        ShopClient.ZoneInfo z = zoneInfo();
        ctx.fill(x + 3, y + 4, x + w + 3, y + h + 4, 0x55000000);
        Ui.roundGradient(ctx, x, y, w, h, 0xF023273A, 0xF012141F);
        Ui.roundGradient(ctx, x, y, w, 22, deposit ? 0xFF3A6FD8 : 0xFF2E8A4A, deposit ? 0xFF2A4FA0 : 0xFF1F6234);
        Draw.scaledCentered(ctx, Component.translatable("tcgshop.register.title").withStyle(ChatFormatting.BOLD), x + w / 2f, y + 7, 1f, 0xFFFFFFFF);
        // mode tabs
        int tw = (w - 28) / 2;
        chip(ctx, x + 10, y + 28, tw, 16, Component.translatable("tcgshop.register.withdraw"), !deposit, mx, my, () -> { deposit = false; confirming = false; pct = 100; });
        chip(ctx, x + 18 + tw, y + 28, tw, 16, Component.translatable("tcgshop.register.deposit"), deposit, mx, my, () -> {
            if (z != null && z.dormant()) return;
            deposit = true;
            confirming = false;
            pct = 100;
        });
        // balances
        long bal = balance(), wal = wallet();
        ctx.drawString(font, Component.translatable("tcgshop.register.balance", Component.literal(PriceService.format(bal)).withStyle(ChatFormatting.GOLD)), x + 12, y + 52, 0xFFDDE3F0, false);
        ctx.drawString(font, Component.translatable("tcgshop.register.wallet", Component.literal(PriceService.format(wal)).withStyle(ChatFormatting.YELLOW)), x + 12, y + 64, 0xFFDDE3F0, false);
        if (z != null) {
            long wages = (z.cashier() ? dev.tcgshop.shop.ShopServer.CASHIER_WAGE : 0) + (z.ext().getBoolean("cashier2") ? dev.tcgshop.shop.ShopServer.CASHIER_WAGE : 0)
                    + dev.tcgshop.shop.ShopServer.GUARD_WAGE * Math.max(z.guard() ? 1 : 0, z.ext().getInt("guards"));
            if (wages > 0) ctx.drawString(font, Ui.ellipsis(Component.translatable("tcgshop.register.wages_hint", PriceService.format(wages)).getString(), w - 24), x + 12, y + 76, 0xFF9A9FB8, false);
        }
        // presets
        ctx.drawString(font, Component.translatable(deposit ? "tcgshop.register.of_wallet" : "tcgshop.register.of_register"), x + 12, y + 92, 0xFF9A9FB8, false);
        for (int i = 0; i < PCTS.length; i++) {
            final int p = PCTS[i];
            chip(ctx, x + 12 + i * 46, y + 104, 42, 16, Component.literal(p + "%"), pct == p, mx, my, () -> { pct = p; confirming = false; if (custom != null) custom.setValue(""); });
        }
        long amt = amount();
        Draw.scaledCentered(ctx, Component.literal(PriceService.format(amt)).withStyle(ChatFormatting.BOLD), x + w / 2f, y + 128, 1.6f, deposit ? 0xFF8FC3FF : Ui.GOLD);
        if (t - doneAt < 2.5)
            Ui.text(ctx, Component.translatable("tcgshop.register.done").withStyle(ChatFormatting.GREEN), x, y + 146, w, 10, 0xFF6BE07B, false);
        int by = y + h - 28;
        boolean can = amt > 0 && (z == null || !(deposit && z.dormant()));
        if (!confirming) {
            Component label = Component.translatable(deposit ? "tcgshop.register.do_deposit" : "tcgshop.register.do_withdraw", PriceService.format(amt));
            Ui.button(ctx, x + 12, by, w - 24, 20, label, mx, my, can, deposit ? BLUE : GREEN);
            if (can) buttons.add(new Btn(x + 12, by, w - 24, 20, () -> confirming = true));
        } else {
            // "Are you sure?" on top of the buttons
            Ui.text(ctx, Component.translatable(deposit ? "tcgshop.register.confirm_deposit" : "tcgshop.register.confirm_withdraw", PriceService.format(amt))
                    .withStyle(ChatFormatting.BOLD), x, by - 14, w, 10, 0xFFFFD34D, true);
            int bw = (w - 32) / 2;
            Ui.button(ctx, x + 12, by, bw, 20, Component.translatable("tcgshop.register.yes"), mx, my, can, deposit ? BLUE : GREEN);
            if (can) buttons.add(new Btn(x + 12, by, bw, 20, this::send));
            Ui.button(ctx, x + 20 + bw, by, bw, 20, Component.translatable("tcgshop.register.cancel"), mx, my, true, GREY);
            buttons.add(new Btn(x + 20 + bw, by, bw, 20, () -> confirming = false));
        }
        if (custom != null) custom.render(ctx, mx, my, delta);
    }

    private void chip(GuiGraphics ctx, int x, int y, int w, int h, Component label, boolean selected, int mx, int my, Runnable r) {
        Ui.chip(ctx, x, y, w, h, label, selected, Ui.in(mx, my, x, y, w, h), deposit ? BLUE : GREEN, 0);
        buttons.add(new Btn(x, y, w, h, r));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (Btn b : new ArrayList<>(buttons))
            if (Ui.in(mouseX, mouseY, b.x, b.y, b.w, b.h)) {
                b.r.run();
                if (minecraft != null && minecraft.player != null) minecraft.player.playSound(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 0.4f, 1.3f);
                return true;
            }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        if (minecraft != null && parent != null) minecraft.setScreen(parent);
        else super.onClose();
    }
}

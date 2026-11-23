package dev.tcgshop.casino.client;

import dev.tcgshop.casino.CasinoGame;
import dev.tcgshop.casino.CasinoPayloads;
import dev.tcgshop.client.binder.Ui;
import dev.tcgshop.client.screen.Draw;
import dev.tcgshop.pricing.PriceService;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Base of the casino screens: limits and balance from the server, the stake selector, clickable areas and the
 * "waiting for the server" state. Screens never decide outcomes: they send a bet ({@link #act}) and animate the
 * Update the server answers with ({@link #onUpdate}).
 */
public abstract class CasinoScreen extends Screen {
    protected record Area(int x, int y, int w, int h, Runnable action) {}

    protected static final long[] CHIPS = {10, 100, 1_000, 10_000, 100_000};

    protected final List<Area> areas = new ArrayList<>();
    protected final CasinoGame game;
    protected final BlockPos pos;
    protected long balance, min, max, bet;
    protected boolean economy;
    protected final String ownerName;
    private double waitSince = -10;

    protected CasinoScreen(CasinoGame game, BlockPos pos, NbtCompound d, String titleKey) {
        super(Text.translatable(titleKey));
        this.game = game;
        this.pos = pos;
        this.balance = d.getLong("Balance");
        this.economy = d.getBoolean("Economy");
        this.min = Math.max(1, d.getLong("Min"));
        this.max = Math.max(min, d.getLong("Max"));
        this.ownerName = d.getString("OwnerName");
        this.bet = clampBet(Math.max(min, Math.min(1_000, max)));
    }

    public CasinoGame game() { return game; }

    /** Server answer (outcome, payout, balance). */
    public abstract void onUpdate(NbtCompound d);

    protected static double now() {
        return System.nanoTime() / 1e9;
    }

    @Override
    public boolean shouldPause() { return false; }

    // ------------------------------------------------------------------ network

    protected void act(NbtCompound d) {
        waitSince = now();
        ClientPlayNetworking.send(new CasinoPayloads.Act(game.key, pos, d));
    }

    /** Waiting for the server (gives up after 2 s: a rejected bet gets no answer, just an action-bar message). */
    protected boolean waiting() {
        return now() - waitSince < 2.0;
    }

    protected void answered(NbtCompound d) {
        waitSince = -10;
        if (d.contains("Balance")) balance = d.getLong("Balance");
    }

    // ------------------------------------------------------------------ helpers

    protected long clampBet(long b) {
        return Math.max(min, Math.min(max, b));
    }

    protected static String fmt(long v) {
        return PriceService.format(v);
    }

    protected void sound(SoundEvent s, float pitch, float volume) {
        if (client != null) client.getSoundManager().play(PositionedSoundInstance.master(s, pitch, volume));
    }

    /** Panel, title, balance and (if any) the owner's name. Returns the y under the header. */
    protected int frame(DrawContext ctx, int x, int y, int w, int h, int top, int bottom) {
        ctx.fillGradient(0, 0, width, height, 0xC0100A06, 0xE0050302);
        Ui.leather(ctx, x, y, w, h, top, bottom);
        Draw.scaledCentered(ctx, title.copy().formatted(Formatting.BOLD), x + w / 2f, y + 7, 1.35f, Ui.GOLD);
        Text bal = Text.translatable("tcgshop.casino.balance", fmt(balance));
        ctx.drawTextWithShadow(textRenderer, bal, x + 8, y + 8, 0xFFFFFFFF);
        if (!ownerName.isEmpty()) {
            Text o = Text.translatable("tcgshop.casino.owner", ownerName);
            ctx.drawTextWithShadow(textRenderer, o, x + w - 8 - textRenderer.getWidth(o), y + 8, 0xFFB0B0C0);
        }
        if (!economy) Draw.scaledCentered(ctx, Text.translatable("tcgshop.casino.no_economy").formatted(Formatting.RED), x + w / 2f, y + 20, 0.8f, 0xFFFF6060);
        return y + 24;
    }

    /**
     * Stake selector: chips add to the bet, plus ½, ×2, min and max. Returns its height.
     * The bet never exceeds the machine's limit (and the server checks it again).
     */
    protected int betBar(DrawContext ctx, int x, int y, int w, int mx, int my, boolean enabled) {
        Text label = Text.translatable("tcgshop.casino.bet", fmt(bet)).formatted(Formatting.BOLD);
        ctx.drawTextWithShadow(textRenderer, label, x, y + 5, bet <= balance ? Ui.GOLD : 0xFFFF6060);
        int cx = x + Math.max(90, textRenderer.getWidth(label) + 10);
        for (long c : CHIPS) {
            if (c > max) break;
            CasinoDraw.chip(ctx, cx + 9, y + 9, 9, c, false);
            if (enabled) areas.add(new Area(cx, y, 18, 18, () -> bet = clampBet(bet == min && c > min ? c : bet + c)));
            cx += 21;
        }
        String[] ops = {"½", "×2", "Min", "Max"};
        int bw = 26;
        int bx = Math.max(cx + 4, x + w - ops.length * (bw + 2));
        for (String op : ops) {
            Ui.button(ctx, bx, y + 2, bw, 14, Text.literal(op), mx, my, enabled, 0xFF444A60);
            if (enabled) areas.add(new Area(bx, y + 2, bw, 14, () -> bet = switch (op) {
                case "½" -> clampBet(bet / 2);
                case "×2" -> clampBet(bet * 2);
                case "Min" -> min;
                default -> clampBet(Math.max(min, balance));
            }));
            bx += bw + 2;
        }
        return 20;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) for (Area a : List.copyOf(areas)) {
            if (Ui.in(mouseX, mouseY, a.x(), a.y(), a.w(), a.h())) {
                a.action().run();
                sound(net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK.value(), 1.4f, 0.25f);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        areas.clear();
        draw(ctx, mouseX, mouseY, delta);
    }

    /** Screen content (areas are rebuilt every frame). */
    protected abstract void draw(DrawContext ctx, int mouseX, int mouseY, float delta);

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // drawn by frame(): no blur behind the table
    }
}

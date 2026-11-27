package dev.tcgshop.casino.client;

import dev.tcgshop.TcgSounds;
import dev.tcgshop.casino.Blackjack;
import dev.tcgshop.casino.CasinoGame;
import dev.tcgshop.client.binder.Ui;
import dev.tcgshop.client.screen.Draw;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

/**
 * Blackjack table. Every decision goes to the server, which owns the shoe; the screen deals the cards it receives
 * one by one (sliding from the shoe) and flips the dealer's hole card at the end.
 * Keys: H hit, S stand, D double, P split, Space deal / stand.
 */
public class BlackjackScreen extends CasinoScreen {
    private static final double DEAL_GAP = 0.28, SLIDE = 0.25;

    private CompoundTag state;
    /** When each visible card (key "d3", "h0.2") first appeared, for the slide-in. */
    private final Map<String, Double> appeared = new HashMap<>();
    private final java.util.Set<String> sounded = new java.util.HashSet<>();
    private double nextSlot;
    private boolean celebrated = true;

    public BlackjackScreen(BlockPos pos, CompoundTag d) {
        super(CasinoGame.BLACKJACK, pos, d, "block.tcgshop.blackjack_table");
        if (d.contains("State")) onState(d.getCompound("State"));
    }

    private boolean inRound() {
        return state != null && !state.getBoolean("Finished") && !state.getBoolean("Idle");
    }

    @Override
    public void onUpdate(CompoundTag d) {
        answered(d);
        onState(d);
    }

    private void onState(CompoundTag d) {
        if (d.getBoolean("Idle") && state != null && state.getBoolean("Finished")) return; // keep showing the last round
        boolean fresh = state == null || state.getBoolean("Finished") || state.getBoolean("Idle");
        if (fresh) {
            appeared.clear();
            sounded.clear();
        }
        state = d;
        if (d.getBoolean("Finished")) celebrated = false;
    }

    private void send(String action) {
        if (waiting()) return;
        CompoundTag d = new CompoundTag();
        d.putString("A", action);
        if (action.equals("deal")) {
            if (!economy || bet > balance) return;
            d.putLong("Bet", bet);
        }
        act(d);
    }

    /** Time a card becomes visible (cards that arrive together are dealt one after the other). */
    private double shown(String key) {
        Double at = appeared.get(key);
        if (at == null) {
            double t = now();
            nextSlot = Math.max(nextSlot, t);
            at = nextSlot;
            nextSlot += DEAL_GAP;
            appeared.put(key, at);
        }
        return at;
    }

    private boolean dealing() {
        return now() < nextSlot + SLIDE;
    }

    private void drawCard(GuiGraphics ctx, String key, int card, float x, float y, float cw, float chh, float shoeX, float shoeY) {
        double at = shown(key), t = now();
        if (t < at) return;
        double p = Mth.clamp((t - at) / SLIDE, 0, 1);
        if (sounded.add(key)) sound(TcgSounds.CARD_SLIDE, 1.1f, 0.6f);
        float e = (float) (1 - Math.pow(1 - p, 3));
        float dx = shoeX + (x - shoeX) * e, dy = shoeY + (y - shoeY) * e;
        // hole card flips when the round ends
        boolean flipped = card >= 0;
        CasinoDraw.card(ctx, p < 1 ? -1 : flipped ? card : -1, dx, dy, cw, chh);
    }

    @Override
    protected void draw(GuiGraphics ctx, int mx, int my, float delta) {
        double t = now();
        int w = Math.min(width - 16, 420), h = Math.min(height - 16, 270), x = (width - w) / 2, y = (height - h) / 2;
        int top = frame(ctx, x, y, w, h, 0xFF0E5A34, 0xFF06331C);
        // felt arc and rules
        CasinoDraw.ellipse(ctx, x + w / 2f, top + 64, w * 0.42f, 22, 0x2AFFFFFF);
        CasinoDraw.ellipse(ctx, x + w / 2f, top + 64, w * 0.42f - 2, 20, 0xFF0B4A2B);
        Draw.scaledCentered(ctx, Component.translatable("tcgshop.casino.bj.felt1").withStyle(ChatFormatting.BOLD), x + w / 2f, top + 58, 0.9f, 0xFFFFD34D);
        Draw.scaledCentered(ctx, Component.translatable("tcgshop.casino.bj.felt2"), x + w / 2f, top + 68, 0.7f, 0xFFCDE8D5);

        float cw = 30, chh = 42, shoeX = x + w - 50, shoeY = top + 4;
        // shoe
        Ui.rounded(ctx, (int) shoeX - 4, (int) shoeY - 2, 40, 48, 4, 0xFF3A2414);
        CasinoDraw.card(ctx, -1, shoeX, shoeY, cw, chh);

        boolean deal = state != null && !state.getBoolean("Idle");
        if (deal) {
            // dealer
            List<Integer> dealer = Blackjack.cards(state, "Dealer");
            float dx0 = x + w / 2f - (dealer.size() * (cw + 4)) / 2f;
            for (int i = 0; i < dealer.size(); i++) drawCard(ctx, "d" + i, dealer.get(i), dx0 + i * (cw + 4), top + 6, cw, chh, shoeX, shoeY);
            boolean hidden = dealer.contains(-1);
            List<Integer> known = new ArrayList<>();
            for (int c : dealer) if (c >= 0) known.add(c);
            if (!dealing() || !hidden) {
                Component dt = Component.translatable("tcgshop.casino.bj.dealer", hidden ? Blackjack.total(known) + "+?" : String.valueOf(Blackjack.total(known)));
                ctx.drawString(font, dt, x + 12, top + 20, 0xFFFFFFFF);
            }
            // player hands
            ListTag hands = Blackjack.hands(state);
            int active = state.getInt("Active");
            float hw = (w - 40) / (float) Math.max(1, hands.size());
            for (int k = 0; k < hands.size(); k++) {
                CompoundTag hd = hands.getCompound(k);
                List<Integer> cards = Blackjack.cards(hd, "Cards");
                float hcx = x + 20 + hw * k + hw / 2;
                float hy = top + 96;
                float spread = Math.min(cw * 0.6f, (hw - cw - 10) / Math.max(1, cards.size() - 1));
                float hx0 = hcx - (cw + spread * (cards.size() - 1)) / 2;
                boolean isActive = !state.getBoolean("Finished") && k == active;
                if (isActive) Ui.roundedOutline(ctx, (int) hx0 - 4, (int) hy - 4, (int) (cw + spread * (cards.size() - 1)) + 8, (int) chh + 8 + (cards.size() > 4 ? 8 : 0), 5, ((int) (t * 3)) % 2 == 0 ? 0xFFFFD34D : 0x80FFD34D);
                for (int i = 0; i < cards.size(); i++)
                    drawCard(ctx, "h" + k + "." + i, cards.get(i), hx0 + i * spread, hy + (hd.getBoolean("Doubled") && i == 2 ? 6 : 0), cw, chh, shoeX, shoeY);
                int tot = Blackjack.total(cards);
                String totS = (Blackjack.soft(cards) && tot < 21 ? (tot - 10) + "/" : "") + tot;
                Draw.scaledCentered(ctx, Component.literal(totS).withStyle(ChatFormatting.BOLD), hcx, hy + chh + 10, 1.1f, tot > 21 ? 0xFFFF6060 : 0xFFFFFFFF);
                Draw.scaledCentered(ctx, Component.translatable("tcgshop.casino.bj.hand_bet", fmt(hd.getLong("Bet"))), hcx, hy + chh + 22, 0.75f, Ui.GOLD);
                String out = hd.getString("Outcome");
                if (!out.isEmpty() && !dealing()) {
                    int col = switch (out) {
                        case "win", "blackjack" -> 0xFF4CAF50;
                        case "push" -> 0xFF9E9E9E;
                        default -> 0xFFE53935;
                    };
                    Ui.rounded(ctx, (int) hcx - 40, (int) hy + 12, 80, 16, 4, col);
                    Ui.text(ctx, Component.translatable("tcgshop.casino.bj." + out).withStyle(ChatFormatting.BOLD), (int) hcx - 40, (int) hy + 12, 80, 16, 0xFFFFFFFF, true);
                }
            }
            if (state.getBoolean("Finished") && !dealing()) {
                long pay = state.getLong("Payout"), staked = state.getLong("Bet");
                Component msg = pay > staked ? Component.translatable("tcgshop.casino.win", fmt(pay)).withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
                        : pay == staked ? Component.translatable("tcgshop.casino.bj.push_msg").withStyle(ChatFormatting.GRAY)
                        : Component.translatable("tcgshop.casino.lose", fmt(staked - pay)).withStyle(ChatFormatting.RED);
                Draw.scaledCentered(ctx, msg, x + w / 2f, top + 80, 1f, 0xFFFFFFFF);
                if (!celebrated) {
                    celebrated = true;
                    sound(pay > staked ? TcgSounds.UI_COINS : SoundEvents.NOTE_BLOCK_BASS.value(), 1f, 0.7f);
                }
            }
        } else Draw.scaledCentered(ctx, Component.translatable("tcgshop.casino.bj.place_bet").withStyle(ChatFormatting.ITALIC), x + w / 2f, top + 110, 1f, 0xFFCDE8D5);

        // ---- controls
        int by = y + h - 34;
        boolean busy = waiting() || dealing();
        if (inRound()) {
            String[] acts = {"hit", "stand", "double", "split"};
            boolean[] en = {true, true, state.getBoolean("CanDouble"), state.getBoolean("CanSplit")};
            int bw = 70, bx = x + w / 2 - (acts.length * (bw + 6)) / 2;
            for (int i = 0; i < acts.length; i++) {
                boolean e = en[i] && !busy;
                Ui.button(ctx, bx, by - 2, bw, 22, Component.translatable("tcgshop.casino.bj." + acts[i]).withStyle(ChatFormatting.BOLD), mx, my, e,
                        i == 0 ? 0xFF2E7D32 : i == 1 ? 0xFFC62828 : 0xFF3A6EA5);
                final String a = acts[i];
                if (e) areas.add(new Area(bx, by - 2, bw, 22, () -> send(a)));
                bx += bw + 6;
            }
        } else {
            boolean can = !busy && economy && bet <= balance;
            betBar(ctx, x + 10, by, w - 20 - 92, mx, my, !busy);
            Ui.button(ctx, x + w - 98, by - 2, 88, 22, Component.translatable("tcgshop.casino.bj.deal").withStyle(ChatFormatting.BOLD), mx, my, can, 0xFFD39B1A);
            if (can) areas.add(new Area(x + w - 98, by - 2, 88, 22, () -> send("deal")));
        }
        Draw.scaledCentered(ctx, Component.translatable("tcgshop.casino.limits", fmt(min), fmt(max)).withStyle(ChatFormatting.DARK_GRAY), x + w / 2f, y + h - 11, 0.75f, 0xFF8A8A9A);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!waiting() && !dealing()) {
            if (inRound()) {
                switch (keyCode) {
                    case GLFW.GLFW_KEY_H -> { send("hit"); return true; }
                    case GLFW.GLFW_KEY_S, GLFW.GLFW_KEY_SPACE -> { send("stand"); return true; }
                    case GLFW.GLFW_KEY_D -> { if (state.getBoolean("CanDouble")) send("double"); return true; }
                    case GLFW.GLFW_KEY_P -> { if (state.getBoolean("CanSplit")) send("split"); return true; }
                    default -> {}
                }
            } else if (keyCode == GLFW.GLFW_KEY_SPACE || keyCode == GLFW.GLFW_KEY_ENTER) {
                send("deal");
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}

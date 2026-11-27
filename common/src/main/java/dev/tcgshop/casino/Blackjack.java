package dev.tcgshop.casino;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * One round of blackjack, played entirely on the server. House rules (printed on the felt):
 * <ul>
 *   <li>6 decks, shuffled before every round (continuous shuffler: no card counting);</li>
 *   <li>the dealer <b>stands on soft 17</b> (S17, the player-friendly rule) and peeks for blackjack with an ace or
 *       ten up, so against a dealer blackjack you only lose your original bet;</li>
 *   <li>blackjack pays <b>3:2</b>; a win pays 1:1; ties push;</li>
 *   <li>double down on any first two cards, also after a split;</li>
 *   <li>one split per round (any two cards of the same value); split aces get one card each, and 21 after a split
 *       is not a blackjack;</li>
 *   <li>no insurance and no surrender.</li>
 * </ul>
 * With basic strategy the house edge is about 0.4% (tools/casino_rtp.py simulates these exact rules).
 * Cards are 0..51: rank = card % 13 (0 = ace, 1..8 = 2..9, 9..12 = 10, J, Q, K), suit = card / 13.
 */
public final class Blackjack {
    public static final int DECKS = 6;

    public static final class Hand {
        public final List<Integer> cards = new ArrayList<>();
        public long bet;
        public boolean doubled, fromSplit, splitAces, done;
        /** Total return once settled (stake included). */
        public long payout;
        /** "", "win", "blackjack", "push", "lose", "bust". */
        public String outcome = "";
    }

    private final int[] shoe;
    private int next;
    public final List<Integer> dealer = new ArrayList<>();
    public final List<Hand> hands = new ArrayList<>();
    public int active;
    public boolean finished, dealerBlackjack;

    /** Deals a new round for `bet` (the stake must already be charged). */
    public Blackjack(long bet, RandomGenerator rnd) {
        shoe = new int[52 * DECKS];
        for (int i = 0; i < shoe.length; i++) shoe[i] = i % 52;
        for (int i = shoe.length - 1; i > 0; i--) {
            int j = rnd.nextInt(i + 1), t = shoe[i];
            shoe[i] = shoe[j];
            shoe[j] = t;
        }
        Hand h = new Hand();
        h.bet = bet;
        hands.add(h);
        h.cards.add(draw());
        dealer.add(draw());
        h.cards.add(draw());
        dealer.add(draw());
        boolean pbj = total(h.cards) == 21, dbj = total(dealer) == 21;
        dealerBlackjack = dbj;
        if (dbj || pbj) finish(); // the dealer peeks: a dealer blackjack ends the round before anyone plays
    }

    private int draw() {
        return shoe[next++ % shoe.length];
    }

    // ------------------------------------------------------------------ card values

    public static int rank(int card) { return card % 13; }

    public static int suit(int card) { return Math.floorMod(card, 52) / 13; }

    public static int value(int card) {
        int r = rank(card);
        return r == 0 ? 11 : r >= 9 ? 10 : r + 1;
    }

    public static int total(List<Integer> cards) {
        int t = 0, aces = 0;
        for (int c : cards) {
            t += value(c);
            if (rank(c) == 0) aces++;
        }
        while (t > 21 && aces > 0) {
            t -= 10;
            aces--;
        }
        return t;
    }

    /** Soft hand: an ace still counted as 11. */
    public static boolean soft(List<Integer> cards) {
        int t = 0, aces = 0;
        for (int c : cards) {
            t += value(c);
            if (rank(c) == 0) aces++;
        }
        while (t > 21 && aces > 0) {
            t -= 10;
            aces--;
        }
        return aces > 0;
    }

    public static boolean isBlackjack(Hand h) {
        return !h.fromSplit && h.cards.size() == 2 && total(h.cards) == 21;
    }

    // ------------------------------------------------------------------ player actions

    public Hand current() {
        return finished || active >= hands.size() ? null : hands.get(active);
    }

    public boolean canDouble() {
        Hand h = current();
        return h != null && h.cards.size() == 2 && !h.splitAces && !h.doubled;
    }

    public boolean canSplit() {
        Hand h = current();
        return h != null && hands.size() == 1 && h.cards.size() == 2 && value(h.cards.get(0)) == value(h.cards.get(1));
    }

    public void hit() {
        Hand h = current();
        if (h == null) return;
        h.cards.add(draw());
        if (total(h.cards) >= 21) advance();
    }

    public void stand() {
        if (current() != null) advance();
    }

    /** The extra stake (equal to the hand's bet) must be charged before calling this. */
    public void doubleDown() {
        Hand h = current();
        if (h == null || !canDouble()) return;
        h.bet *= 2;
        h.doubled = true;
        h.cards.add(draw());
        advance();
    }

    /** The second stake must be charged before calling this. */
    public void split() {
        if (!canSplit()) return;
        Hand a = hands.get(0), b = new Hand();
        b.bet = a.bet;
        b.cards.add(a.cards.remove(1));
        a.fromSplit = b.fromSplit = true;
        boolean aces = rank(a.cards.get(0)) == 0;
        a.splitAces = b.splitAces = aces;
        hands.add(b);
        a.cards.add(draw());
        b.cards.add(draw());
        if (aces) {
            a.done = b.done = true; // split aces: one card each, no more decisions
            active = hands.size();
            finish();
        } else if (total(a.cards) == 21) advance();
    }

    private void advance() {
        Hand h = current();
        if (h != null) h.done = true;
        active++;
        while (active < hands.size() && total(hands.get(active).cards) == 21) {
            hands.get(active).done = true;
            active++;
        }
        if (active >= hands.size()) finish();
    }

    /** Dealer plays (stands on every 17, soft ones included) and every hand is settled. */
    private void finish() {
        if (finished) return;
        finished = true;
        active = hands.size();
        boolean live = false;
        for (Hand h : hands) if (total(h.cards) <= 21) live = true;
        boolean pbj = hands.size() == 1 && isBlackjack(hands.get(0));
        if (live && !dealerBlackjack && !pbj) while (total(dealer) < 17) dealer.add(draw());
        int d = total(dealer);
        for (Hand h : hands) {
            h.done = true;
            int t = total(h.cards);
            if (dealerBlackjack) {
                if (isBlackjack(h)) { h.payout = h.bet; h.outcome = "push"; }
                else { h.payout = 0; h.outcome = "lose"; }
            } else if (isBlackjack(h)) {
                h.payout = h.bet + h.bet * 3 / 2; // 3:2
                h.outcome = "blackjack";
            } else if (t > 21) {
                h.payout = 0;
                h.outcome = "bust";
            } else if (d > 21 || t > d) {
                h.payout = h.bet * 2;
                h.outcome = "win";
            } else if (t == d) {
                h.payout = h.bet;
                h.outcome = "push";
            } else {
                h.payout = 0;
                h.outcome = "lose";
            }
        }
    }

    /** Resolves a round the player walked away from: every open hand stands. */
    public void forceStand() {
        while (!finished) {
            if (current() == null) {
                finish();
                break;
            }
            stand();
        }
    }

    public long totalBet() {
        long s = 0;
        for (Hand h : hands) s += h.bet;
        return s;
    }

    public long totalPayout() {
        long s = 0;
        for (Hand h : hands) s += h.payout;
        return s;
    }

    /** Simple house-player strategy for NPC customers: hit below 17, never double or split. */
    public void autoPlay() {
        while (!finished) {
            Hand h = current();
            if (h == null) break;
            if (total(h.cards) < 17) hit(); else stand();
        }
    }

    // ------------------------------------------------------------------ client view

    /** State for the screen. The dealer's hole card is hidden (-1) until the round is over. */
    public CompoundTag toNbt() {
        CompoundTag n = new CompoundTag();
        int[] d = new int[dealer.size()];
        for (int i = 0; i < d.length; i++) d[i] = !finished && i == 1 ? -1 : dealer.get(i);
        n.putIntArray("Dealer", d);
        ListTag hl = new ListTag();
        for (Hand h : hands) {
            CompoundTag c = new CompoundTag();
            int[] cs = new int[h.cards.size()];
            for (int i = 0; i < cs.length; i++) cs[i] = h.cards.get(i);
            c.put("Cards", new IntArrayTag(cs));
            c.putLong("Bet", h.bet);
            c.putLong("Payout", h.payout);
            c.putString("Outcome", h.outcome);
            c.putBoolean("Doubled", h.doubled);
            hl.add(c);
        }
        n.put("Hands", hl);
        n.putInt("Active", active);
        n.putBoolean("Finished", finished);
        n.putBoolean("CanDouble", canDouble());
        n.putBoolean("CanSplit", canSplit());
        return n;
    }

    /** Client: list of cards from the state above. */
    public static List<Integer> cards(CompoundTag n, String key) {
        List<Integer> out = new ArrayList<>();
        for (int c : n.getIntArray(key)) out.add(c);
        return out;
    }

    public static ListTag hands(CompoundTag n) {
        return n.getList("Hands", Tag.TAG_COMPOUND);
    }
}

#!/usr/bin/env python3
"""1.0 beta 3 casino: return-to-player (RTP) math and a Monte Carlo check of every game.

The tables below MUST mirror the Java sources in common/src/main/java/dev/tcgshop/casino:
  * SlotMath.java     3 reels x 64 virtual stops, single payline (exact enumeration + simulation)
  * RouletteMath.java European single-zero wheel, every standard bet pays 36/k - 1 (exact: 36/37)
  * WheelMath.java    Big Six money wheel, 54 segments (exact)
  * ScratchMath.java  three scratch tickets, "1 in N" prize tables (exact + simulation)
  * Blackjack.java    6 decks shuffled every round, dealer stands on soft 17, peek, 3:2, double any two,
                      double after split, one split (aces get one card each), no insurance, no surrender
                      (simulation with basic strategy)

Usage: python tools/casino_rtp.py [hands]   (reads and writes nothing; prints a report)
"""
import random
import sys
from itertools import product

# ---------------------------------------------------------------- slots (SlotMath.java)
SYMS = ["cherry", "lemon", "bell", "bar", "star", "seven", "gem"]
CH, LE, BE, BA, ST, SE, GE = range(7)
WEIGHTS = [[12, 14, 12, 10, 8, 5, 3],
           [10, 16, 12, 11, 8, 5, 2],
           [10, 16, 12, 11, 8, 5, 2]]
THREE = [12, 7, 12, 20, 35, 120, 1000]
MIXED_PREMIUM, TWO_CHERRIES, ONE_CHERRY = 4, 4, 2
PREMIUM = (ST, SE, GE)


def slot_mult(a, b, c):
    if a == b == c:
        return THREE[a]
    if a in PREMIUM and b in PREMIUM and c in PREMIUM:
        return MIXED_PREMIUM
    if a == CH and b == CH:
        return TWO_CHERRIES
    if a == CH:
        return ONE_CHERRY
    return 0


def slots_exact():
    tot = [sum(w) for w in WEIGHTS]
    rtp = hit = sq = 0.0
    for a, b, c in product(range(7), repeat=3):
        p = WEIGHTS[0][a] * WEIGHTS[1][b] * WEIGHTS[2][c] / (tot[0] * tot[1] * tot[2])
        m = slot_mult(a, b, c)
        rtp += p * m
        sq += p * m * m
        hit += p if m > 0 else 0
    jackpot = 1 / (WEIGHTS[0][GE] * WEIGHTS[1][GE] * WEIGHTS[2][GE] / (tot[0] * tot[1] * tot[2]))
    return rtp, hit, (sq - rtp * rtp) ** 0.5, jackpot


def slots_sim(n, rnd):
    reels = [[s for s in range(7) for _ in range(w[s])] for w in WEIGHTS]
    paid = 0
    for _ in range(n):
        paid += slot_mult(rnd.choice(reels[0]), rnd.choice(reels[1]), rnd.choice(reels[2]))
    return paid / n


# ---------------------------------------------------------------- roulette (RouletteMath.java)
def roulette_exact():
    # every legal bet covers k numbers and returns 36/k times the stake when one of them comes up
    return {k: (k / 37) * (36 / k) for k in (1, 2, 3, 4, 6, 12, 18)}


# ---------------------------------------------------------------- Big Six wheel (WheelMath.java)
WHEEL = [("1", 24, 1), ("2", 15, 2), ("5", 7, 5), ("10", 4, 10), ("20", 2, 20), ("joker", 1, 40), ("logo", 1, 40)]


def wheel_exact():
    return {name: count * (pays + 1) / 54 for name, count, pays in WHEEL}


def wheel_layout():
    """Mirror of the WheelMath static block: rarest first, spread evenly over a snapshot of the free segments."""
    layout = [-1] * 54
    for s in range(len(WHEEL) - 1, -1, -1):
        free_idx = [i for i in range(54) if layout[i] < 0]
        free, n = len(free_idx), WHEEL[s][1]
        step, at = free / n, (free / 2 if s == 6 else 0)
        for k in range(n):
            layout[free_idx[int(at + k * step) % free]] = s
    return layout


def wheel_layout_rtp():
    """RTP of every symbol measured on the actual 54-segment layout (catches a layout that drifts from the counts)."""
    layout = wheel_layout()
    assert -1 not in layout, "empty segment on the wheel"
    return {name: sum(1 for v in layout if v == s) * (pays + 1) / 54 for s, (name, _count, pays) in enumerate(WHEEL)}


def roulette_legal():
    """Mirror of RouletteMath.LEGAL: every standard European bet as (frozenset of numbers)."""
    red = {1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36}
    bets = {frozenset([n]) for n in range(37)}
    bets |= {frozenset(s) for s in ([0, 1], [0, 2], [0, 3], [0, 1, 2], [0, 2, 3], [0, 1, 2, 3])}
    for n in range(1, 37):
        if n % 3:
            bets.add(frozenset([n, n + 1]))
        if n <= 33:
            bets.add(frozenset([n, n + 3]))
        if n % 3 and n <= 32:
            bets.add(frozenset([n, n + 1, n + 3, n + 4]))
    bets |= {frozenset(range(n, n + 3)) for n in range(1, 35, 3)}
    bets |= {frozenset(range(n, n + 6)) for n in range(1, 32, 3)}
    nums = range(1, 37)
    for s in ([n for n in nums if n in red], [n for n in nums if n not in red], [n for n in nums if n % 2],
              [n for n in nums if n % 2 == 0], list(range(1, 19)), list(range(19, 37))):
        bets.add(frozenset(s))
    for k in range(3):
        bets.add(frozenset(range(12 * k + 1, 12 * k + 13)))
        bets.add(frozenset(n for n in nums if (n - 1) % 3 == k))
    return bets


def roulette_check():
    """Exact RTP of every legal bet over the 37 pockets (payout = stake * 36 // k, as RouletteMath.payout)."""
    worst = best = None
    bets = roulette_legal()
    for b in bets:
        rtp = sum(36 // len(b) for n in range(37) if n in b) / 37
        worst = rtp if worst is None else min(worst, rtp)
        best = rtp if best is None else max(best, rtp)
    return len(bets), worst, best


# ---------------------------------------------------------------- scratch cards (ScratchMath.java)
SCRATCH = {
    "bronze": [(1, 5), (2, 12), (5, 50), (10, 200), (20, 500), (50, 2_500), (100, 10_000), (1_000, 250_000)],
    "silver": [(1, 5), (2, 10), (3, 100), (5, 40), (10, 250), (25, 1_000), (100, 5_000), (500, 50_000), (2_000, 500_000)],
    "gold": [(1, 5), (2, 8), (3, 80), (5, 50), (10, 150), (50, 2_000), (200, 20_000), (1_000, 200_000), (10_000, 5_000_000)],
}


def scratch_exact(table):
    return sum(m / n for m, n in table), sum(1 / n for m, n in table)


def scratch_sim(table, n, rnd):
    paid = 0
    for _ in range(n):
        u = rnd.random()
        acc = 0.0
        for m, one_in in table:
            acc += 1 / one_in
            if u < acc:
                paid += m
                break
    return paid / n


# ---------------------------------------------------------------- blackjack (Blackjack.java)
def card_value(c):
    r = c % 13
    return 11 if r == 0 else 10 if r >= 9 else r + 1


def total(cards):
    t = sum(card_value(c) for c in cards)
    aces = sum(1 for c in cards if c % 13 == 0)
    while t > 21 and aces:
        t -= 10
        aces -= 1
    return t, aces > 0  # (total, soft)


def basic(cards, up, can_double, can_split):
    """'H' hit, 'S' stand, 'D' double, 'P' split. Multi-deck, dealer stands on soft 17, double after split."""
    d = 11 if up % 13 == 0 else card_value(up)
    if can_split and len(cards) == 2 and card_value(cards[0]) == card_value(cards[1]):
        v = card_value(cards[0])
        if v == 11 or v == 8:
            return "P"
        if v == 9 and d not in (7, 10, 11):
            return "P"
        if v in (7, 3, 2) and d <= 7:
            return "P"
        if v == 6 and d <= 6:
            return "P"
        if v == 4 and d in (5, 6):
            return "P"
    t, soft = total(cards)
    if soft and len(cards) >= 2:
        if t >= 19:
            return "S"
        if t == 18:
            if 3 <= d <= 6:
                return "D" if can_double else "S"
            return "S" if d in (2, 7, 8) else "H"
        if t == 17:
            return "D" if can_double and 3 <= d <= 6 else "H"
        if t in (15, 16):
            return "D" if can_double and 4 <= d <= 6 else "H"
        if t in (13, 14):
            return "D" if can_double and 5 <= d <= 6 else "H"
        return "H"
    if t >= 17:
        return "S"
    if t >= 13:
        return "S" if d <= 6 else "H"
    if t == 12:
        return "S" if 4 <= d <= 6 else "H"
    if t == 11:
        return "D" if can_double and d <= 10 else "H"
    if t == 10:
        return "D" if can_double and d <= 9 else "H"
    if t == 9:
        return "D" if can_double and 3 <= d <= 6 else "H"
    return "H"


def blackjack_round(rnd):
    shoe = list(range(52)) * 6
    rnd.shuffle(shoe)
    pos = [0]

    def draw():
        c = shoe[pos[0]]
        pos[0] += 1
        return c

    p = [draw()]
    dl = [draw()]
    p.append(draw())
    dl.append(draw())
    p_bj = total(p)[0] == 21
    d_bj = total(dl)[0] == 21
    if d_bj:  # dealer peeks: only the original bet is lost (push against a player blackjack)
        return 0.0 if p_bj else -1.0
    if p_bj:
        return 1.5
    hands = [{"cards": p, "bet": 1.0, "split": False, "aces": False}]
    i = 0
    while i < len(hands):
        h = hands[i]
        if h["aces"]:
            i += 1
            continue
        while True:
            if len(h["cards"]) == 1:
                h["cards"].append(draw())
            t, _ = total(h["cards"])
            if t >= 21:
                break
            two = len(h["cards"]) == 2
            a = basic(h["cards"], dl[0], two, two and len(hands) == 1)
            if a == "P":
                c2 = h["cards"].pop()
                aces = card_value(c2) == 11
                h["split"] = True
                h["aces"] = aces
                nh = {"cards": [c2], "bet": 1.0, "split": True, "aces": aces}
                hands.append(nh)
                h["cards"].append(draw())
                if aces:
                    nh["cards"].append(draw())
                    break
                continue
            if a == "D":
                h["bet"] = 2.0
                h["cards"].append(draw())
                break
            if a == "S":
                break
            h["cards"].append(draw())
        i += 1
    live = [h for h in hands if total(h["cards"])[0] <= 21]
    if live:
        while total(dl)[0] < 17:
            dl.append(draw())
    dt = total(dl)[0]
    net = 0.0
    for h in hands:
        t = total(h["cards"])[0]
        if t > 21:
            net -= h["bet"]
        elif dt > 21 or t > dt:
            net += h["bet"]
        elif t < dt:
            net -= h["bet"]
    return net


def main():
    hands = int(sys.argv[1]) if len(sys.argv) > 1 else 400_000
    rnd = random.Random(2026_11_23)
    rtp, hit, sd, jp = slots_exact()
    print(f"SLOTS     exact RTP {rtp * 100:.3f}%  hit frequency {hit * 100:.2f}%  sd {sd:.2f} bets  jackpot 1 in {jp:,.0f}")
    print(f"          simulated RTP ({hands:,} spins) {slots_sim(hands, rnd) * 100:.3f}%")
    for k, v in roulette_exact().items():
        print(f"ROULETTE  {k:>2} numbers: RTP {v * 100:.3f}%  house edge {(1 - v) * 100:.3f}%")
    count, worst, best = roulette_check()
    print(f"ROULETTE  {count} legal bets enumerated: RTP min {worst * 100:.3f}%  max {best * 100:.3f}%")
    on_layout = wheel_layout_rtp()
    for name, v in wheel_exact().items():
        flag = "" if abs(on_layout[name] - v) < 1e-12 else f"  LAYOUT MISMATCH: {on_layout[name] * 100:.2f}%"
        print(f"WHEEL     {name:>6}: RTP {v * 100:.2f}%  house edge {(1 - v) * 100:.2f}%{flag}")
    for name, table in SCRATCH.items():
        r, win = scratch_exact(table)
        print(f"SCRATCH   {name:>6}: exact RTP {r * 100:.2f}%  wins 1 in {1 / win:.2f}  simulated {scratch_sim(table, hands, rnd) * 100:.2f}%")
    net = 0.0
    for _ in range(hands):
        net += blackjack_round(rnd)
    print(f"BLACKJACK basic strategy, {hands:,} rounds: RTP {(1 + net / hands) * 100:.3f}%  (house edge {-net / hands * 100:.3f}% per initial bet)")


if __name__ == "__main__":
    main()

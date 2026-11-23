package dev.tcgshop.casino;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * European roulette (single zero, 37 pockets). A bet is the set of numbers it covers, sent as a 37-bit mask
 * (bit n = number n). Only the standard bets are legal (built once in {@link #LEGAL}):
 * <pre>
 * straight 1 number   35:1      split 2 numbers  17:1     street / trio 3  11:1
 * corner / first four 4  8:1    six line 6        5:1     column / dozen 12  2:1
 * red, black, odd, even, 1-18, 19-36 (18 numbers)  1:1
 * </pre>
 * Every bet returns 36/k times the stake (stake included) when one of its k numbers comes up, so its RTP is
 * k/37 * 36/k = 36/37 = 97.30%: the 2.70% house edge of a real European wheel, whatever you bet on.
 */
public final class RouletteMath {
    public static final Set<Integer> RED = Set.of(1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36);
    /** Pocket order on a European wheel (clockwise, starting at the zero). */
    public static final int[] WHEEL = {0, 32, 15, 19, 4, 21, 2, 25, 17, 34, 6, 27, 13, 36, 11, 30, 8, 23, 10, 5, 24, 16, 33, 1, 20, 14,
            31, 9, 22, 18, 29, 7, 28, 12, 35, 3, 26};
    /** Legal bet mask -> numbers covered. */
    public static final Map<Long, Integer> LEGAL = new HashMap<>();
    public static final long RED_MASK, BLACK_MASK, ODD_MASK, EVEN_MASK, LOW_MASK, HIGH_MASK;
    public static final long[] DOZENS = new long[3], COLUMNS = new long[3];

    static {
        for (int n = 0; n <= 36; n++) add(n);
        add(0, 1);
        add(0, 2);
        add(0, 3);
        add(0, 1, 2);
        add(0, 2, 3);
        add(0, 1, 2, 3);
        for (int n = 1; n <= 36; n++) {
            if (n % 3 != 0) add(n, n + 1);          // split, same street
            if (n <= 33) add(n, n + 3);             // split, next street
            if (n % 3 != 0 && n <= 32) add(n, n + 1, n + 3, n + 4); // corner
        }
        for (int n = 1; n <= 34; n += 3) add(n, n + 1, n + 2);                      // street
        for (int n = 1; n <= 31; n += 3) add(n, n + 1, n + 2, n + 3, n + 4, n + 5); // six line
        long red = 0, black = 0, odd = 0, even = 0, low = 0, high = 0;
        for (int n = 1; n <= 36; n++) {
            long b = 1L << n;
            if (RED.contains(n)) red |= b; else black |= b;
            if (n % 2 == 1) odd |= b; else even |= b;
            if (n <= 18) low |= b; else high |= b;
            DOZENS[(n - 1) / 12] |= b;
            COLUMNS[(n - 1) % 3] |= b;
        }
        RED_MASK = red;
        BLACK_MASK = black;
        ODD_MASK = odd;
        EVEN_MASK = even;
        LOW_MASK = low;
        HIGH_MASK = high;
        for (long m : new long[]{red, black, odd, even, low, high}) LEGAL.put(m, 18);
        for (long m : DOZENS) LEGAL.put(m, 12);
        for (long m : COLUMNS) LEGAL.put(m, 12);
    }

    private RouletteMath() {}

    private static void add(int... ns) {
        LEGAL.put(mask(ns), ns.length);
    }

    public static long mask(int... ns) {
        long m = 0;
        for (int n : ns) if (n >= 0 && n <= 36) m |= 1L << n;
        return m;
    }

    public static boolean legal(long mask) {
        return LEGAL.containsKey(mask);
    }

    public static boolean covers(long mask, int number) {
        return number >= 0 && number <= 36 && (mask & (1L << number)) != 0;
    }

    /** Total return of a bet (0 = lost; stake included when won). The mask must be legal. */
    public static long payout(long mask, long amount, int result) {
        Integer k = LEGAL.get(mask);
        if (k == null || !covers(mask, result)) return 0;
        return amount * (36 / k);
    }

    /** 0 green, 1 red, 2 black. */
    public static int color(int n) {
        return n == 0 ? 0 : RED.contains(n) ? 1 : 2;
    }

    public static int wheelIndex(int n) {
        for (int i = 0; i < WHEEL.length; i++) if (WHEEL[i] == n) return i;
        return 0;
    }
}

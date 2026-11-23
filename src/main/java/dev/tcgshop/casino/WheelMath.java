package dev.tcgshop.casino;

import java.util.random.RandomGenerator;

/**
 * Fortune wheel: the casino "Big Six" money wheel with 54 equal segments. You bet on one symbol; if the pointer
 * stops on it you get your stake back plus the symbol's payout.
 * <pre>
 * symbol  segments  pays   RTP      house edge
 * 1          24      1:1   88.89%   11.11%
 * 2          15      2:1   83.33%   16.67%
 * 5           7      5:1   77.78%   22.22%
 * 10          4     10:1   81.48%   18.52%
 * 20          2     20:1   77.78%   22.22%
 * joker       1     40:1   75.93%   24.07%
 * logo        1     40:1   75.93%   24.07%
 * </pre>
 * These are the real-world Big Six odds (the worst game in a casino, which is why it is the cheapest machine).
 */
public final class WheelMath {
    public static final String[] NAMES = {"1", "2", "5", "10", "20", "joker", "logo"};
    public static final int[] COUNTS = {24, 15, 7, 4, 2, 1, 1};
    public static final int[] PAYS = {1, 2, 5, 10, 20, 40, 40};
    public static final int SYMBOLS = 7, SEGMENTS = 54;
    /** Symbol of each segment, clockwise from the top. */
    public static final int[] LAYOUT = new int[SEGMENTS];

    static {
        // rarest first, each symbol spread evenly over the segments still free (joker and logo end up opposite).
        // The free segments are snapshotted per symbol: indexing a list that shrinks while placing would skip
        // placements and silently change the odds.
        java.util.Arrays.fill(LAYOUT, -1);
        for (int s = SYMBOLS - 1; s >= 0; s--) {
            int[] freeIdx = new int[SEGMENTS];
            int free = 0;
            for (int i = 0; i < SEGMENTS; i++) if (LAYOUT[i] < 0) freeIdx[free++] = i;
            int n = COUNTS[s];
            double step = free / (double) n, at = s == 6 ? free / 2.0 : 0;
            for (int k = 0; k < n; k++) LAYOUT[freeIdx[(int) Math.floor(at + k * step) % free]] = s;
        }
        // the layout must match COUNTS exactly: it is what the odds (and tools/casino_rtp.py) are computed from
        int[] seen = new int[SYMBOLS];
        for (int v : LAYOUT) {
            if (v < 0) throw new IllegalStateException("Fortune wheel layout has an empty segment");
            seen[v]++;
        }
        if (!java.util.Arrays.equals(seen, COUNTS)) throw new IllegalStateException("Fortune wheel layout does not match COUNTS");
    }

    private WheelMath() {}

    public static int spin(RandomGenerator rnd) {
        return rnd.nextInt(SEGMENTS);
    }

    /** Total return (stake included) of a bet of `amount` on `symbol` when the wheel stops on `segment`. */
    public static long payout(int symbol, long amount, int segment) {
        if (symbol < 0 || symbol >= SYMBOLS || segment < 0 || segment >= SEGMENTS || LAYOUT[segment] != symbol) return 0;
        return amount * (PAYS[symbol] + 1L);
    }

    public static double rtp(int symbol) {
        return COUNTS[symbol] * (PAYS[symbol] + 1.0) / SEGMENTS;
    }
}

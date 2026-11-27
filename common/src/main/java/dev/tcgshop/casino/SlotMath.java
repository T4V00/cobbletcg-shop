package dev.tcgshop.casino;

import java.util.random.RandomGenerator;

/**
 * Slot machine math: 3 reels with 64 weighted virtual stops each and one payline (the middle row).
 *
 * <pre>
 * symbol   reel 1  reel 2  reel 3   three of a kind
 * cherry     12      10      10       x12
 * lemon      14      16      16       x7
 * bell       12      12      12       x12
 * bar        10      11      11       x20
 * star        8       8       8       x35
 * seven       5       5       5       x120
 * gem         3       2       2       x1000 (jackpot, 1 in 21,845 spins)
 * any three premium symbols (star / seven / gem, mixed)  x4
 * cherry on reels 1 and 2 (not three)                    x4
 * cherry on reel 1 only                                  x2
 * </pre>
 * Multipliers are the total return (stake included). Exact RTP by enumerating every stop combination:
 * 95.37% (house edge 4.63%), hit frequency 22.6%. tools/casino_rtp.py enumerates the same table and checks it
 * with a Monte Carlo simulation; {@link #exactRtp()} is logged when the server starts.
 */
public final class SlotMath {
    public static final int CHERRY = 0, LEMON = 1, BELL = 2, BAR = 3, STAR = 4, SEVEN = 5, GEM = 6, SYMBOLS = 7;
    public static final String[] NAMES = {"cherry", "lemon", "bell", "bar", "star", "seven", "gem"};
    public static final int[][] WEIGHTS = {
            {12, 14, 12, 10, 8, 5, 3},
            {10, 16, 12, 11, 8, 5, 2},
            {10, 16, 12, 11, 8, 5, 2}};
    /** Three of a kind, by symbol. */
    public static final int[] THREE = {12, 7, 12, 20, 35, 120, 1000};
    public static final int MIXED_PREMIUM = 4, TWO_CHERRIES = 4, ONE_CHERRY = 2;
    public static final int REELS = 3;
    private static final int[][] STRIPS = new int[REELS][];

    static {
        // physical strip for the animation: the symbols spread evenly (largest remainder), so a spinning reel looks
        // like a real one; the outcome itself is drawn from WEIGHTS on the server
        for (int r = 0; r < REELS; r++) {
            int total = 0;
            for (int w : WEIGHTS[r]) total += w;
            int[] strip = new int[total];
            double[] next = new double[SYMBOLS];
            for (int s = 0; s < SYMBOLS; s++) next[s] = (s * 0.37 + r * 0.11) % 1.0 * total / Math.max(1, WEIGHTS[r][s]);
            int[] left = WEIGHTS[r].clone();
            for (int i = 0; i < total; i++) {
                int best = -1;
                for (int s = 0; s < SYMBOLS; s++)
                    if (left[s] > 0 && (best < 0 || next[s] < next[best])) best = s;
                strip[i] = best;
                left[best]--;
                next[best] += total / (double) WEIGHTS[r][best];
            }
            STRIPS[r] = strip;
        }
    }

    private SlotMath() {}

    public static boolean premium(int s) {
        return s == STAR || s == SEVEN || s == GEM;
    }

    public static int spinReel(int reel, RandomGenerator rnd) {
        int total = 0;
        for (int w : WEIGHTS[reel]) total += w;
        int x = rnd.nextInt(total);
        for (int s = 0; s < SYMBOLS; s++) {
            x -= WEIGHTS[reel][s];
            if (x < 0) return s;
        }
        return LEMON;
    }

    public static int[] spin(RandomGenerator rnd) {
        int[] out = new int[REELS];
        for (int r = 0; r < REELS; r++) out[r] = spinReel(r, rnd);
        return out;
    }

    /** Total return in bets (0 = lost) for the payline. */
    public static int multiplier(int[] s) {
        if (s == null || s.length != REELS) return 0;
        for (int v : s) if (v < 0 || v >= SYMBOLS) return 0;
        if (s[0] == s[1] && s[1] == s[2]) return THREE[s[0]];
        if (premium(s[0]) && premium(s[1]) && premium(s[2])) return MIXED_PREMIUM;
        if (s[0] == CHERRY && s[1] == CHERRY) return TWO_CHERRIES;
        if (s[0] == CHERRY) return ONE_CHERRY;
        return 0;
    }

    /** Exact return to player over every combination (weights multiplied). */
    public static double exactRtp() {
        double[] tot = new double[REELS];
        for (int r = 0; r < REELS; r++) for (int w : WEIGHTS[r]) tot[r] += w;
        double rtp = 0;
        for (int a = 0; a < SYMBOLS; a++)
            for (int b = 0; b < SYMBOLS; b++)
                for (int c = 0; c < SYMBOLS; c++)
                    rtp += WEIGHTS[0][a] / tot[0] * WEIGHTS[1][b] / tot[1] * WEIGHTS[2][c] / tot[2] * multiplier(new int[]{a, b, c});
        return rtp;
    }

    /** Animation strip of a reel (client). */
    public static int[] strip(int reel) {
        return STRIPS[Math.floorMod(reel, REELS)];
    }
}

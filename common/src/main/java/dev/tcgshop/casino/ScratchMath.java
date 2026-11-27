package dev.tcgshop.casino;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * Scratch cards ("match three amounts" on a 3x3 grid). Prizes are multiples of the ticket's face value
 * (CasinoConfig.scratchFaceValues) with lottery-style "1 in N" odds; like real instant tickets they return well
 * under 100% (tools/casino_rtp.py prints the same numbers):
 * <pre>
 * Lucky Coin (bronze)   RTP 59.1%   wins 1 in 3.22   top prize x1,000   (1 in 250,000)
 * Gold Rush (silver)    RTP 65.4%   wins 1 in 2.94   top prize x2,000   (1 in 500,000)
 * Master Jackpot (gold) RTP 69.6%   wins 1 in 2.74   top prize x10,000  (1 in 5,000,000)
 * </pre>
 * The outcome is rolled on the server when the ticket is used; the grid is then built to show exactly that
 * outcome (three equal amounts when it won, no amount three times when it lost).
 */
public final class ScratchMath {
    public enum Tier {
        BRONZE("bronze", new int[]{1, 2, 5, 10, 20, 50, 100, 1_000}, new int[]{5, 12, 50, 200, 500, 2_500, 10_000, 250_000}),
        SILVER("silver", new int[]{1, 2, 3, 5, 10, 25, 100, 500, 2_000}, new int[]{5, 10, 100, 40, 250, 1_000, 5_000, 50_000, 500_000}),
        GOLD("gold", new int[]{1, 2, 3, 5, 10, 50, 200, 1_000, 10_000}, new int[]{5, 8, 80, 50, 150, 2_000, 20_000, 200_000, 5_000_000});

        public final String key;
        /** Prize multipliers (of the face value) and their odds, "1 in N". */
        public final int[] mult, oneIn;

        Tier(String key, int[] mult, int[] oneIn) {
            this.key = key;
            this.mult = mult;
            this.oneIn = oneIn;
        }

        public int top() { return mult[mult.length - 1]; }

        public static Tier of(int ordinal) {
            Tier[] v = values();
            return v[Math.floorMod(ordinal, v.length)];
        }
    }

    public static final int CELLS = 9;

    private ScratchMath() {}

    /** Prize multiplier of a new ticket (0 = no prize). */
    public static int roll(Tier t, RandomGenerator rnd) {
        double u = rnd.nextDouble(), acc = 0;
        for (int i = 0; i < t.mult.length; i++) {
            acc += 1.0 / t.oneIn[i];
            if (u < acc) return t.mult[i];
        }
        return 0;
    }

    public static double rtp(Tier t) {
        double r = 0;
        for (int i = 0; i < t.mult.length; i++) r += t.mult[i] / (double) t.oneIn[i];
        return r;
    }

    /**
     * The 9 amounts (as multipliers) printed under the foil. Won: `prize` exactly three times and no other amount
     * more than twice. Lost (prize 0): no amount three times.
     */
    public static int[] grid(Tier t, int prize, RandomGenerator rnd) {
        List<Integer> cells = new ArrayList<>();
        if (prize > 0) for (int i = 0; i < 3; i++) cells.add(prize);
        List<Integer> pool = new ArrayList<>();
        for (int m : t.mult) if (m != prize) { pool.add(m); pool.add(m); }
        // decoys: mostly the small amounts, sometimes a near miss with a big one
        double[] key = new double[pool.size()];
        Integer[] order = new Integer[pool.size()];
        for (int i = 0; i < key.length; i++) {
            key[i] = weight(pool.get(i), rnd); // computed once: a random comparator would break the sort contract
            order[i] = i;
        }
        java.util.Arrays.sort(order, (a, b) -> Double.compare(key[a], key[b]));
        for (int i = 0; cells.size() < CELLS && i < order.length; i++) cells.add(pool.get(order[i]));
        while (cells.size() < CELLS) cells.add(t.mult[0]); // never happens with the tables above
        Collections.shuffle(cells, new java.util.Random(rnd.nextLong()));
        int[] out = new int[CELLS];
        for (int i = 0; i < CELLS; i++) out[i] = cells.get(i);
        return out;
    }

    private static double weight(int mult, RandomGenerator rnd) {
        return Math.log10(mult + 1) * rnd.nextDouble();
    }

    /** Index (0..8) of the cells that make the winning line, for the highlight (empty when lost). */
    public static List<Integer> winningCells(int[] grid, int prize) {
        List<Integer> out = new ArrayList<>();
        if (prize <= 0) return out;
        for (int i = 0; i < grid.length; i++) if (grid[i] == prize) out.add(i);
        return out;
    }
}

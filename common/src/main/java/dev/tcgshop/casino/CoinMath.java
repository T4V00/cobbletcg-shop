package dev.tcgshop.casino;

import java.util.random.RandomGenerator;

/**
 * 1.1 coin toss stand ("Double or Nothing"): a big minted coin is flipped once per bet. It lands heads 49%, tails 49%
 * and, 2% of the time, on its edge (the house's margin). Total return (stake included):
 * <pre>
 * bet     chance   pays   RTP
 * heads    49%     1:1    98%
 * tails    49%     1:1    98%
 * edge      2%    40:1    82%
 * </pre>
 */
public final class CoinMath {
    public static final String[] BETS = {"heads", "tails", "edge"};
    public static final int[] PAYS = {1, 1, 40};
    /** Out of 100: heads, tails, edge. */
    public static final int[] WEIGHTS = {49, 49, 2};
    public static final int COUNT = BETS.length;

    private CoinMath() {}

    /** 0 heads, 1 tails, 2 edge. */
    public static int flip(RandomGenerator rnd) {
        int r = rnd.nextInt(100);
        return r < WEIGHTS[0] ? 0 : r < WEIGHTS[0] + WEIGHTS[1] ? 1 : 2;
    }

    public static long payout(int bet, long amount, int outcome) {
        if (bet < 0 || bet >= COUNT || bet != outcome) return 0;
        return amount * (PAYS[bet] + 1L);
    }

    public static double rtp(int bet) {
        return WEIGHTS[bet] / 100.0 * (PAYS[bet] + 1.0);
    }
}

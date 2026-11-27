package dev.tcgshop.casino;

import java.util.random.RandomGenerator;

/**
 * 1.1 dice table ("Lucky Seven"): two fair dice, one bet per roll. Total return (stake included) when the bet wins:
 * <pre>
 * bet        wins on        chance   pays   RTP
 * under 7    2-6            15/36    1:1    83.33%
 * seven      7               6/36    4:1    83.33%
 * over 7     8-12           15/36    1:1    83.33%
 * doubles    1-1 ... 6-6     6/36    4:1    83.33%
 * snake eyes 1-1             1/36   30:1    86.11%
 * boxcars    6-6             1/36   30:1    86.11%
 * </pre>
 * Real-world "under / over 7" odds: the same house edge on every main bet, slightly better on the long shots.
 */
public final class DiceMath {
    public static final String[] BETS = {"under", "seven", "over", "doubles", "snake", "boxcars"};
    public static final int[] PAYS = {1, 4, 1, 4, 30, 30};
    public static final int COUNT = BETS.length;

    private DiceMath() {}

    /** One die: 1..6. */
    public static int roll(RandomGenerator rnd) {
        return 1 + rnd.nextInt(6);
    }

    public static boolean wins(int bet, int d1, int d2) {
        int s = d1 + d2;
        return switch (bet) {
            case 0 -> s < 7;
            case 1 -> s == 7;
            case 2 -> s > 7;
            case 3 -> d1 == d2;
            case 4 -> d1 == 1 && d2 == 1;
            case 5 -> d1 == 6 && d2 == 6;
            default -> false;
        };
    }

    /** Total return (stake included) of `amount` on `bet` for that roll. */
    public static long payout(int bet, long amount, int d1, int d2) {
        if (bet < 0 || bet >= COUNT || d1 < 1 || d1 > 6 || d2 < 1 || d2 > 6 || !wins(bet, d1, d2)) return 0;
        return amount * (PAYS[bet] + 1L);
    }

    /** Exact return to player of a bet (over the 36 equally likely rolls). */
    public static double rtp(int bet) {
        int w = 0;
        for (int a = 1; a <= 6; a++) for (int b = 1; b <= 6; b++) if (wins(bet, a, b)) w++;
        return w * (PAYS[bet] + 1.0) / 36.0;
    }
}

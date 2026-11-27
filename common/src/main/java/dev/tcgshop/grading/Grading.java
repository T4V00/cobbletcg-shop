package dev.tcgshop.grading;

import com.mojang.serialization.Codec;
import dev.tcgshop.TcgShop;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

/**
 * CobbleGrade: professional card grading. The grade goes from 1.0 to 10.0 in steps of 0.1 and is stored on the card
 * (tcgshop:grade component in tenths, 10..100) together with a certificate number. The grade multiplies the card's
 * market value.
 */
public final class Grading {
    public static final DataComponentType<Integer> GRADE = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, ResourceLocation.fromNamespaceAndPath(TcgShop.MOD_ID, "grade"),
            DataComponentType.<Integer>builder().persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT).build());
    public static final DataComponentType<Integer> CERT = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, ResourceLocation.fromNamespaceAndPath(TcgShop.MOD_ID, "grade_cert"),
            DataComponentType.<Integer>builder().persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT).build());

    /** Grade bands (tenths, both inclusive) and their probability in %. Within each band, every tenth is equally likely. */
    public record Band(int from, int to, double percent) {}

    public static final Band[] ODDS = {
    new Band(100, 100, 0.1),
    new Band(95, 99, 0.9),
    new Band(90, 94, 3),
    new Band(80, 89, 8),
    new Band(70, 79, 13),
    new Band(50, 69, 30),
    new Band(30, 49, 25),
    new Band(10, 29, 20)
};

    /** Services: Minecraft days/hours it takes and cost per card (fixed + % of the card's value). */
    public enum Service {
        ECONOMY("economy", 72_000, 2_500, 0.03),
        STANDARD("standard", 24_000, 6_000, 0.06),
        EXPRESS("express", 2_000, 15_000, 0.10);

        public final String key;
        public final long ticks;
        public long baseFee; // EconomyConfig
        public double pct;

        Service(String key, long ticks, long baseFee, double pct) {
            this.key = key;
            this.ticks = ticks;
            this.baseFee = baseFee;
            this.pct = pct;
        }

        public long fee(long cardValue) { return baseFee + Math.round(cardValue * pct); }

        public static Service of(int i) { return values()[Math.floorMod(i, values().length)]; }
    }

    public static final int MAX_PER_ORDER = 20;

    private Grading() {}

    public static void init() {}

    public static int grade(ItemStack st) {
        return st.isEmpty() ? 0 : st.getOrDefault(GRADE, 0);
    }

    public static boolean isGraded(ItemStack st) { return grade(st) > 0; }

    public static int cert(ItemStack st) { return st.getOrDefault(CERT, 0); }

    /**
     * Random grade for that card: Grade 10 with probability 0.45·e^(-1.7A) (A = age, see GradeModel);
     * otherwise, the remaining ODDS bands in the same proportion. Cards from OLD_YEAR or earlier: OLD_CLUSTER_CHANCE of landing
     * on 4.9/5.0/5.1 and, if not, an OLD_HIGH_FACTOR penalty on every Grade >= 5.0.
     */
    public static int roll(RandomSource r, net.minecraft.resources.ResourceLocation card) {
        boolean old = dev.tcgshop.pricing.GradeModel.year(card) <= OLD_YEAR; // old print: worse centering and edges
        // Old cards: a dedicated band around 5 (4.9, 5.0 and 5.1, each equally likely). This band
        // does not carry OLD_HIGH_FACTOR: it is the "realistic" way for an old card to survive in good condition.
        if (old && r.nextDouble() < OLD_CLUSTER_CHANCE) return OLD_CLUSTER_FROM + r.nextInt(OLD_CLUSTER_TO - OLD_CLUSTER_FROM + 1);
        double p10 = dev.tcgshop.pricing.GradeModel.prob10(card) * (old ? OLD_HIGH_FACTOR : NEW_HIGH_FACTOR);
        if (r.nextDouble() < p10) return 100;
        double[] w = new double[100];
        double total = 0;
        for (Band b : ODDS) {
            if (b.from >= 100) continue;
            for (int g = b.from; g <= b.to; g++) {
                // For old cards an exact 5.0 is penalized too: every Grade >= 5.0.
                w[g] = b.percent / (b.to - b.from + 1) * (old ? (g >= 50 ? OLD_HIGH_FACTOR : 1) : (g >= NEW_HIGH_FROM ? NEW_HIGH_FACTOR : 1));
                total += w[g];
            }
        }
        double x = r.nextDouble() * total;
        for (int g = 10; g < 100; g++) { x -= w[g]; if (x < 0) return g; }
        return 70;
    }

    /**
     * Prints from 2017 or earlier have an extremely low probability
     * of reaching Grade 5.0 or higher. The factor is applied to every result >= 5.0.
     * 0.005 means those results keep only 0.5% of their original weight.
     */
    public static final int OLD_YEAR = 2017;
    public static final double OLD_HIGH_FACTOR = 0.005;
    /**
     * Exception for old cards: 30% of their rolls land directly on 4.9, 5.0 or 5.1 (10% each),
     * without the OLD_HIGH_FACTOR penalty. The remaining 70% follows the ODDS table with the usual penalty.
     */
    public static final double OLD_CLUSTER_CHANCE = 0.30;
    /**
     * Newer prints (after OLD_YEAR): every result above Grade 5.5 (5.6 and up, Grade 10 included) keeps only 5% of
     * its weight, so a high grade is rare: about 4% of new cards land above 5.5 (it was about 46%).
     */
    public static final int NEW_HIGH_FROM = 56;
    public static final double NEW_HIGH_FACTOR = 0.05;
    public static final int OLD_CLUSTER_FROM = 49, OLD_CLUSTER_TO = 51;

    /**
     * Generic roll without identifying the card. Does not apply the age modifier.
     * To grade real cards, always use roll(Random, Identifier).
     */
    public static int roll(RandomSource r) {
        double x = r.nextDouble() * 100, acc = 0;
        for (Band b : ODDS) {
            acc += b.percent;
            if (x < acc) return b.from + r.nextInt(b.to - b.from + 1);
        }
        return 80;
    }

    /** Fraction of the jump up to Grade 10 reached at Grade g: ((grade-1)/9)^3 (0 at Grade 1, 1 at Grade 10). */
    public static double multiplier(int g) {
        double v = Math.max(1, Math.min(10, g / 10.0));
        return Math.pow((v - 1) / 9.0, 3);
    }

    public static String format(int g) {
        return g >= 100 ? "10" : String.format(Locale.ROOT, "%.1f", g / 10.0);
    }

    /** Grade name (GEM MINT, MINT, NM...). */
    public static String labelKey(int g) {
        if (g >= 100) return "tcgshop.grade.label.10";
        if (g >= 95) return "tcgshop.grade.label.95";
        return "tcgshop.grade.label." + Math.max(1, g / 10);
    }

    public static int color(int g) {
        if (g >= 100) return 0xFFFFC233;
        if (g >= 90) return 0xFF43D9A0;
        if (g >= 80) return 0xFF5AA9FF;
        if (g >= 60) return 0xFFB8BCC8;
        if (g >= 40) return 0xFFFF9F43;
        return 0xFFFF5A5A;
    }

    /** Applies the grade to a copy of the card (name with the grade, certificate). */
    public static ItemStack apply(ItemStack card, int grade, int cert) {
        ItemStack out = card.copyWithCount(1);
        Component original = card.getHoverName();
        out.set(GRADE, grade);
        out.set(CERT, cert);
        MutableComponent name = Component.empty().append(original).append(Component.literal(" [CG " + format(grade) + "]")
                .withColor(color(grade) & 0xFFFFFF));
        out.set(DataComponents.ITEM_NAME, name);
        return out;
    }

    public static Component tooltip(int g) {
        return Component.literal("CobbleGrade " + format(g) + " · ").withStyle(ChatFormatting.BOLD).withColor(color(g) & 0xFFFFFF)
                .append(Component.translatable(labelKey(g)).withColor(color(g) & 0xFFFFFF));
    }
}

package dev.tcgshop.grading;

import com.mojang.serialization.Codec;
import dev.tcgshop.TcgShop;
import net.minecraft.component.ComponentType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;

import java.util.Locale;

/**
 * CobbleGrade: gradeo profesional de cartas. La nota va de 1.0 a 10.0 en pasos de 0.1 y se guarda en la carta
 * (componente tcgshop:grade en décimas, 10..100) junto a un número de certificado. La nota multiplica el valor de
 * mercado de la carta.
 */
public final class Grading {
    public static final ComponentType<Integer> GRADE = Registry.register(Registries.DATA_COMPONENT_TYPE, Identifier.of(TcgShop.MOD_ID, "grade"),
            ComponentType.<Integer>builder().codec(Codec.INT).packetCodec(PacketCodecs.VAR_INT).build());
    public static final ComponentType<Integer> CERT = Registry.register(Registries.DATA_COMPONENT_TYPE, Identifier.of(TcgShop.MOD_ID, "grade_cert"),
            ComponentType.<Integer>builder().codec(Codec.INT).packetCodec(PacketCodecs.VAR_INT).build());

    /** Tramos de nota (décimas, ambos incluidos) y su probabilidad en %. Dentro de cada tramo, cada décima es igual de probable. */
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

    /** Servicios: días/horas de Minecraft que tarda y coste por carta (fijo + % del valor de la carta). */
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
     * Grado al azar para esa carta: Grado 10 con probabilidad 0,45·e^(-1,7A) (A = antigüedad, ver GradeModel);
     * si no, el resto de tramos de ODDS en la misma proporción. Cartas de OLD_YEAR o antes: OLD_CLUSTER_CHANCE de caer
     * en 4.9/5.0/5.1 y, si no, castigo OLD_HIGH_FACTOR a todo Grado >= 5.0.
     */
    public static int roll(Random r, net.minecraft.util.Identifier card) {
        boolean old = dev.tcgshop.pricing.GradeModel.year(card) <= OLD_YEAR; // impresión antigua: peor centrado y bordes
        // Cartas antiguas: un tramo propio alrededor del 5 (4.9, 5.0 y 5.1, cada uno igual de probable). Este tramo
        // no lleva OLD_HIGH_FACTOR: es la forma "realista" de que una carta vieja sobreviva bien conservada.
        if (old && r.nextDouble() < OLD_CLUSTER_CHANCE) return OLD_CLUSTER_FROM + r.nextInt(OLD_CLUSTER_TO - OLD_CLUSTER_FROM + 1);
        double p10 = dev.tcgshop.pricing.GradeModel.prob10(card) * (old ? OLD_HIGH_FACTOR : 1);
        if (r.nextDouble() < p10) return 100;
        double[] w = new double[100];
        double total = 0;
        for (Band b : ODDS) {
            if (b.from >= 100) continue;
            for (int g = b.from; g <= b.to; g++) {
                // Para cartas antiguas se penaliza también el 5.0 exacto: todo Grado >= 5.0.
                w[g] = b.percent / (b.to - b.from + 1) * (old && g >= 50 ? OLD_HIGH_FACTOR : 1);
                total += w[g];
            }
        }
        double x = r.nextDouble() * total;
        for (int g = 10; g < 100; g++) { x -= w[g]; if (x < 0) return g; }
        return 70;
    }

    /**
     * Las impresiones de 2017 o anteriores tienen una probabilidad extremadamente baja
     * de alcanzar Grado 5.0 o superior. El factor se aplica a cada resultado >= 5.0.
     * 0.005 significa que esos resultados conservan solo el 0.5% de su peso original.
     */
    public static final int OLD_YEAR = 2017;
    public static final double OLD_HIGH_FACTOR = 0.005;
    /**
     * Excepción para cartas antiguas: el 30% de sus tiradas cae directamente en 4.9, 5.0 o 5.1 (10% cada uno),
     * sin el castigo OLD_HIGH_FACTOR. El 70% restante sigue la tabla ODDS con el castigo de siempre.
     */
    public static final double OLD_CLUSTER_CHANCE = 0.30;
    public static final int OLD_CLUSTER_FROM = 49, OLD_CLUSTER_TO = 51;

    /**
     * Tirada genérica sin identificar la carta. No aplica el modificador por antigüedad.
     * Para graduar cartas reales, usar siempre roll(Random, Identifier).
     */
    public static int roll(Random r) {
        double x = r.nextDouble() * 100, acc = 0;
        for (Band b : ODDS) {
            acc += b.percent;
            if (x < acc) return b.from + r.nextInt(b.to - b.from + 1);
        }
        return 80;
    }

    /** Parte del salto hasta el Grado 10 que se alcanza con el Grado g: ((grado-1)/9)^3 (0 en Grado 1, 1 en Grado 10). */
    public static double multiplier(int g) {
        double v = Math.max(1, Math.min(10, g / 10.0));
        return Math.pow((v - 1) / 9.0, 3);
    }

    public static String format(int g) {
        return g >= 100 ? "10" : String.format(Locale.ROOT, "%.1f", g / 10.0);
    }

    /** Nombre del grado (GEM MINT, MINT, NM...). */
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

    /** Aplica la nota a una copia de la carta (nombre con la nota, certificado). */
    public static ItemStack apply(ItemStack card, int grade, int cert) {
        ItemStack out = card.copyWithCount(1);
        Text original = card.getName();
        out.set(GRADE, grade);
        out.set(CERT, cert);
        MutableText name = Text.empty().append(original).append(Text.literal(" [CG " + format(grade) + "]")
                .withColor(color(grade) & 0xFFFFFF));
        out.set(DataComponentTypes.ITEM_NAME, name);
        return out;
    }

    public static Text tooltip(int g) {
        return Text.literal("CobbleGrade " + format(g) + " · ").formatted(Formatting.BOLD).withColor(color(g) & 0xFFFFFF)
                .append(Text.translatable(labelKey(g)).withColor(color(g) & 0xFFFFFF));
    }
}

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
            new Band(100, 100, 2), new Band(95, 99, 10), new Band(90, 94, 20), new Band(80, 89, 30),
            new Band(70, 79, 18), new Band(50, 69, 12), new Band(30, 49, 6), new Band(10, 29, 2)};

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

    /** Nota al azar según ODDS (en décimas). */
    public static int roll(Random r) {
        double x = r.nextDouble() * 100, acc = 0;
        for (Band b : ODDS) {
            acc += b.percent;
            if (x < acc) return b.from + r.nextInt(b.to - b.from + 1);
        }
        return 80;
    }

    /** Multiplicador del valor de mercado según la nota. 1.0 -> x0,4 · 7.0 -> x1 · 9.0 -> x2,2 · 9.9 -> x4 · 10 -> x8. */
    public static double multiplier(int g) {
        double v = g / 10.0;
        if (g >= 100) return 8.0;
        if (v >= 9.0) return 2.2 + (v - 9.0) * 2.0;
        if (v >= 7.0) return 1.0 + (v - 7.0) * 0.6;
        return 0.4 + 0.6 * (v - 1.0) / 6.0;
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

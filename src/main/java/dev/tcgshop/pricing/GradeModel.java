package dev.tcgshop.pricing;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.masterball.api.card.CardInfo;
import dev.masterball.api.card.Rarity;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Sistema de Grado (1.0-10.0). Las explicaciones del teléfono (tcgshop.gradeinfo.*) describen exactamente esto.
 *
 * <pre>
 * A  = clamp(años desde el lanzamiento / 25, 0, 1)
 * R  = rareza de impresión (0-1), B = atractivo de la ilustración (0-1, por rareza)
 * E  = escasez de la colección = clamp(ln(precio del sobre / 4,5) / ln(100), 0, 1)
 * S  = 0,45R + 0,55E
 * Pokémon:     P = ((1025-rank)/1024)^2;  Pfinal = rank ≤ 300 ? 0,65 + 0,35P : 0,65·((1025-rank)/725)^2
 * Sin Pokémon: U = 0,40K + 0,30F + 0,20V + 0,10H;  T = 0,35C + 0,30R + 0,20S + 0,15U
 * Q  = 0,70·(Pfinal o T) + 0,15S + 0,10R + 0,05B
 * Z  = 0,45A + 0,55Q
 * M10 = min(100, 1 + 99·Z^4);  Mg = 1 + (M10-1)·((grado-1)/9)^3 (Grado ≥ 5);  Grado < 5: Mg = 0,40 + 0,59·((grado-1)/3,9)^1,5;  precio = precio sin Grado · Mg
 * Probabilidad de Grado 10 = 0,45·e^(-1,7A)
 * </pre>
 * Datos por carta (categoría, rank, uso competitivo K, versatilidad V): data/tcgshop/tcgshop_grade/cards.json,
 * generado por tools/gen_grade_data.py. F (efectividad) y H (relevancia histórica) no tienen datos fiables: 0,5.
 */
public final class GradeModel {
    private GradeModel() {}

    public static final int CURRENT_YEAR = 2026;
    /** Popularidad neutra (Pokémon sin identificar): la del Pokémon mediano, rank 513. */
    public static final int NEUTRAL_RANK = 513;
    public static final double NEUTRAL = 0.5;

    /** Importancia de cada categoría (C) para cartas sin Pokémon. */
    public static double categoryWeight(String cat) {
        return switch (cat) {
            case "supporter" -> 0.85;
            case "item" -> 0.70;
            case "tool" -> 0.55;
            case "stadium" -> 0.50;
            case "energy_special" -> 0.45;
            case "energy_basic" -> 0.10;
            default -> NEUTRAL;
        };
    }

    public record Info(String cat, int rank, double k, double v) {}

    private static Map<String, Info> data;

    private static synchronized Info info(Identifier id) {
        if (data == null) {
            data = new HashMap<>();
            try (InputStream in = GradeModel.class.getClassLoader().getResourceAsStream("data/tcgshop/tcgshop_grade/cards.json")) {
                if (in != null) {
                    JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                    for (var e : o.entrySet()) {
                        JsonObject c = e.getValue().getAsJsonObject();
                        data.put(e.getKey(), new Info(c.get("c").getAsString(), c.has("r") ? c.get("r").getAsInt() : 0,
                                c.has("k") ? c.get("k").getAsDouble() : 0, c.has("v") ? c.get("v").getAsDouble() : NEUTRAL));
                    }
                }
            } catch (Exception e) {
                dev.tcgshop.TcgShop.LOGGER.warn("No se pudieron leer los datos de Grado", e);
            }
        }
        return data.getOrDefault(id.toString(), new Info("unknown", 0, 0, NEUTRAL));
    }

    public static int dataSize() { info(Identifier.of("tcgshop", "x")); return data.size(); }

    public static Info infoOf(Identifier id) { return info(id); }

    // ------------------------------------------------------------------ factores

    /** Año de lanzamiento del set (MasterBall Traders API, de TCGplayer). */
    public static int year(Identifier id) {
        dev.masterball.api.card.RarityData.SetData s = dev.masterball.api.card.RarityData.set(CardInfo.setId(id));
        return s == null ? CURRENT_YEAR : s.year();
    }

    public static double age(Identifier id) { return clamp((CURRENT_YEAR - year(id)) / 25.0); }

    /** R: rareza de impresión (0-1), según el símbolo oficial de la carta (Rarity.scarcity). */
    public static double rarity(Rarity r) {
        return r.scarcity;
    }

    /** B: atractivo de la ilustración (0-1) según su rareza (Rarity.art). */
    public static double art(Rarity r) {
        return r.art;
    }

    public static double collectionScarcity(double packUsd) {
        return clamp(Math.log(Math.max(packUsd, 0.01) / 4.5) / Math.log(100));
    }

    public static double popularity(int rank) {
        if (rank <= 0) rank = NEUTRAL_RANK;
        rank = Math.max(1, Math.min(1025, rank));
        double p = Math.pow((1025 - rank) / 1024.0, 2);
        return rank <= 300 ? 0.65 + 0.35 * p : 0.65 * Math.pow((1025 - rank) / 725.0, 2);
    }

    /** Índice de demanda de cartas sin Pokémon. */
    public static double trainerDemand(Info in, double r, double s) {
        double u = 0.40 * in.k() + 0.30 * NEUTRAL + 0.20 * in.v() + 0.10 * NEUTRAL;
        return 0.35 * categoryWeight(in.cat()) + 0.30 * r + 0.20 * s + 0.15 * u;
    }

    /** Multiplicador máximo (Grado 10) de esa carta. */
    public static double m10(Identifier id, Rarity rarity, double packUsd) {
        Info in = info(id);
        double a = age(id), r = rarity(rarity), b = art(rarity);
        double s = 0.45 * r + 0.55 * collectionScarcity(packUsd);
        double demand = in.cat().equals("pokemon") ? popularity(in.rank()) : trainerDemand(in, r, s);
        double q = 0.70 * demand + 0.15 * s + 0.10 * r + 0.05 * b;
        double z = 0.45 * a + 0.55 * q;
        return Math.min(100, 1 + 99 * Math.pow(z, 4));
    }

    /** Multiplicador del Grado g (décimas: 95 = 9.5, 100 = 10). */
    public static double mult(double m10, int g) {
        double grade = Math.max(1, Math.min(10, g / 10.0));
        if (grade < 5.0) return 0.40 + 0.59 * Math.pow((grade - 1) / 3.9, 1.5); // Grado 4.9 o menos: pierde valor (x0,40 en Grado 1, x0,99 en 4.9)
        return 1 + (m10 - 1) * Math.pow((grade - 1) / 9.0, 3);
    }

    /** Probabilidad de sacar Grado 10 con esta carta. */
    public static double prob10(Identifier id) {
        return 0.45 * Math.exp(-1.7 * age(id));
    }

    private static double clamp(double x) { return Math.max(0, Math.min(1, x)); }
}

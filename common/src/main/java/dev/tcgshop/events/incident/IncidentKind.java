package dev.tcgshop.events.incident;

import java.util.Locale;
import net.minecraft.util.StringRepresentable;

/**
 * Incident kinds (KIND property of the tcgshop:incident block). Each has its default fix
 * (tag #tcgshop:fix/&lt;kind&gt; and/or empty hand), its outline height and how many hits it takes by default.
 */
public enum IncidentKind implements StringRepresentable {
    PUDDLE(false, 2, 1),
    DEBRIS(true, 5, 3),
    DIRT(false, 2, 2),
    DROPPINGS(false, 2, 1),
    SCORCH(false, 1, 1),
    SPARKS(false, 3, 1),
    MESS(false, 3, 2),
    PACKAGE(true, 9, 1);

    /** Is it fixed with an empty hand if the definition says nothing else? */
    public final boolean handByDefault;
    /** Outline height (in 1/16 of a block) so it can be targeted. */
    public final int height;
    /** Default hits to remove it. */
    public final int defaultHits;

    IncidentKind(boolean hand, int height, int hits) {
        this.handByDefault = hand;
        this.height = height;
        this.defaultHits = hits;
    }

    public String key() { return name().toLowerCase(Locale.ROOT); }

    @Override
    public String getSerializedName() { return key(); }

    public static IncidentKind parse(String s) {
        try {
            return valueOf(s.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("unknown incident.kind '" + s + "' (puddle|debris|dirt|droppings|scorch|sparks|mess|package)");
        }
    }
}

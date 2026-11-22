package dev.tcgshop.events.incident;

import net.minecraft.util.StringIdentifiable;

import java.util.Locale;

/**
 * Tipos de incidente (propiedad KIND del bloque tcgshop:incident). Cada uno tiene su arreglo por defecto
 * (tag #tcgshop:fix/&lt;kind&gt; y/o mano vacía), su altura de contorno y cuántos golpes pide de serie.
 */
public enum IncidentKind implements StringIdentifiable {
    PUDDLE(false, 2, 1),
    DEBRIS(true, 5, 3),
    DIRT(false, 2, 2),
    DROPPINGS(false, 2, 1),
    SCORCH(false, 1, 1),
    SPARKS(false, 3, 1),
    MESS(false, 3, 2),
    PACKAGE(true, 9, 1);

    /** ¿Se arregla con la mano vacía si la definición no dice otra cosa? */
    public final boolean handByDefault;
    /** Altura del contorno (en 1/16 de bloque) para poder apuntarlo. */
    public final int height;
    /** Golpes de serie para quitarlo. */
    public final int defaultHits;

    IncidentKind(boolean hand, int height, int hits) {
        this.handByDefault = hand;
        this.height = height;
        this.defaultHits = hits;
    }

    public String key() { return name().toLowerCase(Locale.ROOT); }

    @Override
    public String asString() { return key(); }

    public static IncidentKind parse(String s) {
        try {
            return valueOf(s.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("incident.kind desconocido '" + s + "' (puddle|debris|dirt|droppings|scorch|sparks|mess|package)");
        }
    }
}

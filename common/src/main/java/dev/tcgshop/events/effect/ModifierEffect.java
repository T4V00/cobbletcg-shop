package dev.tcgshop.events.effect;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.tcgshop.events.EventDef;
import dev.tcgshop.events.EventEffect;
import dev.tcgshop.events.EventInstance;
import dev.tcgshop.events.EventRegistry;
import dev.tcgshop.events.Mods;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * P1 "modifier": changes shop numbers while the event lasts (see EFFECTS.md). Global caps are set by Mods.
 * while: "unresolved" (only until it is fixed; the norm for negative events), "always" (the whole event, also after
 * being fixed), "resolved" (only AFTER being fixed) or "auto" (unresolved for negative events, always for the rest).
 */
public final class ModifierEffect implements EventEffect {
    final double spawnMult, buyMult, perceivedMult, haggleMult, patienceMult, serviceMult, revenueMult;
    final int maxCustAdd, moodStart, repDelta, lightDelta, decorDelta, lighting;
    final Map<String, Double> typeW = new LinkedHashMap<>();
    final List<Mods.FilteredMult> buy = new ArrayList<>();
    final String mode;

    public ModifierEffect(JsonObject o, String defId) {
        spawnMult = EventDef.dbl(o, "spawnMult", 1);
        buyMult = EventDef.dbl(o, "buyMult", 1);
        perceivedMult = EventDef.dbl(o, "perceivedMult", 1);
        haggleMult = EventDef.dbl(o, "haggleMult", 1);
        patienceMult = EventDef.dbl(o, "patienceMult", 1);
        serviceMult = EventDef.dbl(o, "serviceMult", 1);
        revenueMult = Math.min(1.15, EventDef.dbl(o, "revenueMult", 1));
        maxCustAdd = EventDef.integer(o, "maxCustAdd", 0);
        moodStart = EventDef.integer(o, "moodStart", 0);
        repDelta = EventDef.integer(o, "repDelta", 0);
        lightDelta = EventDef.integer(o, "lightDelta", 0);
        decorDelta = EventDef.integer(o, "decorDelta", 0);
        lighting = EventDef.integer(o, "lighting", -1);
        if (o.has("typeW")) for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("typeW").entrySet()) typeW.put(e.getKey(), e.getValue().getAsDouble());
        if (o.has("buy")) for (JsonElement e : o.getAsJsonArray("buy")) {
            JsonObject b = e.getAsJsonObject();
            buy.add(new Mods.FilteredMult(EventDef.str(b, "filter", "any"), EventDef.dbl(b, "mult", 1)));
        }
        mode = EventDef.str(o, "while", "auto");
        if (!List.of("auto", "unresolved", "always", "resolved").contains(mode)) throw new IllegalArgumentException("invalid modifier.while: " + mode);
    }

    private boolean activeNow(EventInstance inst) {
        String m = mode;
        if (m.equals("auto")) {
            EventDef def = EventRegistry.get(inst.defId);
            m = def != null && def.negative() ? "unresolved" : "always";
        }
        return switch (m) {
            case "unresolved" -> !inst.resolved();
            case "resolved" -> inst.resolved();
            default -> true;
        };
    }

    @Override
    public void contribute(Mods.Builder b, EventInstance inst, long now) {
        if (!activeNow(inst)) return;
        b.spawnMult *= spawnMult;
        b.buyMult *= buyMult;
        b.perceivedMult *= perceivedMult;
        b.haggleMult *= haggleMult;
        b.patienceMult *= patienceMult;
        b.serviceMult *= serviceMult;
        b.revenueMult *= revenueMult;
        b.maxCustAdd += maxCustAdd;
        b.moodStart += moodStart;
        b.repDelta += repDelta;
        b.lightDelta += lightDelta;
        b.decorDelta += decorDelta;
        if (lighting >= 0) b.lightingOverride = b.lightingOverride < 0 ? lighting : Math.min(b.lightingOverride, lighting);
        typeW.forEach(b::type);
        b.buyFilters.addAll(buy);
    }

    @Override
    public boolean keepsAfterResolve() { return mode.equals("always") || mode.equals("resolved"); }
}

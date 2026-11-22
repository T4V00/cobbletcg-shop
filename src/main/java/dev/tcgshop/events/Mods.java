package dev.tcgshop.events;

import dev.tcgshop.entity.CustomerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Suma de todos los modificadores de los eventos activos de una tienda, ya con los topes globales
 * (EventsConfig.clamps). La cachea EventManager.mods(zone) y se reconstruye cuando cambia algún evento.
 * {@link #NONE} = sin eventos (todo neutro).
 */
public final class Mods {
    public final double spawnMult, buyMult, perceivedMult, haggleMult, patienceMult, serviceMult, revenueMult;
    public final int maxCustAdd, moodStart, repDelta, lightDelta, decorDelta, lightingOverride;
    /** Peso de cada tipo de cliente (índice = CustomerEntity.Type.ordinal()), 1 = normal. */
    private final double[] typeW;
    private final List<FilteredMult> buyFilters;
    private final Set<Long> disabledDisplays, closedDoors;
    private final Set<String> disabledSlots;
    public final boolean registerDown;
    /** Sólo para el teléfono / depuración: hay algún evento activo. */
    public final boolean any;

    /** Multiplicador de compra que sólo se aplica a ciertos productos ("packs", "cards", "fair_price", "newest_set", "set:&lt;id&gt;"). */
    public record FilteredMult(String filter, double mult) {}

    public static final Mods NONE = new Builder().build(false);

    private Mods(Builder b, boolean any) {
        EventsConfig.Clamps c = EventsConfig.INSTANCE.clamps;
        spawnMult = clamp(b.spawnMult, c.spawnMult);
        buyMult = clamp(b.buyMult, c.buyMult);
        perceivedMult = clamp(b.perceivedMult, c.perceivedMult);
        haggleMult = clamp(b.haggleMult, c.haggleMult);
        patienceMult = clamp(b.patienceMult, c.patienceMult);
        serviceMult = clamp(b.serviceMult, c.serviceMult);
        revenueMult = Math.max(1.0, Math.min(Math.min(1.15, c.revenueMultMax), b.revenueMult));
        maxCustAdd = (int) clamp(b.maxCustAdd, c.maxCustAdd);
        moodStart = (int) clamp(b.moodStart, c.moodStart);
        repDelta = (int) clamp(b.repDelta, c.repDelta);
        lightDelta = Math.max(-8, Math.min(8, b.lightDelta));
        decorDelta = Math.max(-10, Math.min(10, b.decorDelta));
        lightingOverride = b.lightingOverride;
        typeW = b.typeW.clone();
        for (int i = 0; i < typeW.length; i++) typeW[i] = Math.max(0, Math.min(c.typeWeightMax, typeW[i]));
        buyFilters = Collections.unmodifiableList(new ArrayList<>(b.buyFilters));
        disabledDisplays = Set.copyOf(b.disabledDisplays);
        disabledSlots = Set.copyOf(b.disabledSlots);
        closedDoors = Set.copyOf(b.closedDoors);
        registerDown = b.registerDown;
        this.any = any;
    }

    private static double clamp(double v, double[] range) {
        if (range == null || range.length < 2) return v;
        return Double.isNaN(v) ? range[0] : Math.max(range[0], Math.min(range[1], v));
    }

    public double typeWeight(CustomerEntity.Type t) { return typeW[t.ordinal()]; }

    public boolean isDisplayDisabled(BlockPos pos, int slot) {
        if (disabledDisplays.isEmpty() && disabledSlots.isEmpty()) return false;
        long l = pos.asLong();
        return disabledDisplays.contains(l) || disabledSlots.contains(l + ":" + slot);
    }

    public boolean isDoorClosed(BlockPos door) { return !closedDoors.isEmpty() && closedDoors.contains(door.asLong()); }

    public boolean hasClosedDoors() { return !closedDoors.isEmpty(); }

    /**
     * Multiplicador de probabilidad de compra para ese producto: el general por los filtrados que encajen, con el tope
     * global de buyMult. ratio = precio expuesto / mercado.
     */
    public double buyMultFor(ItemStack st, double ratio) {
        double m = buyMult;
        for (FilteredMult f : buyFilters) if (matchesFilter(f.filter(), st, ratio)) m *= f.mult();
        return clamp(m, EventsConfig.INSTANCE.clamps.buyMult);
    }

    public static boolean matchesFilter(String filter, ItemStack st, double ratio) {
        String f = filter.toLowerCase(Locale.ROOT);
        if (f.equals("packs")) return dev.masterball.api.card.CardInfo.isPack(st) || st.isOf(dev.tcgshop.shop.TcgBlocks.BOOSTER_BOX_ITEM);
        if (f.equals("cards")) return dev.masterball.api.card.CardInfo.isCard(st);
        if (f.equals("fair_price")) return ratio <= 1.0001;
        if (f.equals("newest_set")) return SetHelper.isNewestSet(st);
        if (f.equals("old_sets")) return SetHelper.isOldSet(st);
        if (f.startsWith("set:")) return f.substring(4).equals(SetHelper.setOf(st));
        return false;
    }

    // ------------------------------------------------------------------ construcción

    /** Acumulador: multiplicadores se multiplican, sumandos se suman (los efectos llaman a esto en contribute). */
    public static final class Builder {
        public double spawnMult = 1, buyMult = 1, perceivedMult = 1, haggleMult = 1, patienceMult = 1, serviceMult = 1, revenueMult = 1;
        public int maxCustAdd, moodStart, repDelta, lightDelta, decorDelta, lightingOverride = -1;
        public final double[] typeW = new double[CustomerEntity.Type.values().length];
        public final List<FilteredMult> buyFilters = new ArrayList<>();
        public final Set<Long> disabledDisplays = new HashSet<>(), closedDoors = new HashSet<>();
        public final Set<String> disabledSlots = new HashSet<>();
        public boolean registerDown;

        public Builder() {
            java.util.Arrays.fill(typeW, 1.0);
        }

        /** typeName: whale, thief, seller, trader, haggler, picky, regular. */
        public Builder type(String typeName, double mult) {
            try {
                typeW[CustomerEntity.Type.valueOf(typeName.trim().toUpperCase(Locale.ROOT)).ordinal()] *= mult;
            } catch (IllegalArgumentException ignored) {}
            return this;
        }

        /** Añade los bloqueos de una instancia (expositores, puertas, caja, luz). */
        public Builder locks(EventInstance e) {
            disabledDisplays.addAll(e.disabledDisplays);
            disabledSlots.addAll(e.disabledSlots);
            closedDoors.addAll(e.closedDoors);
            registerDown |= e.registerDown;
            if (e.lightingOverride >= 0) lightingOverride = lightingOverride < 0 ? e.lightingOverride : Math.min(lightingOverride, e.lightingOverride);
            return this;
        }

        public Mods build(boolean any) { return new Mods(this, any); }
    }
}

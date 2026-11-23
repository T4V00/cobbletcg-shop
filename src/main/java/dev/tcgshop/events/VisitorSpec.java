package dev.tcgshop.events;

import dev.tcgshop.entity.CustomerEntity;
import dev.tcgshop.shop.ShopZones;
import org.jetbrains.annotations.Nullable;

/**
 * "Special" customer of an event (ShopLife.forceSpawnVisitor). Not saved: if the world reloads, the customer
 * leaves (like any customer) and the event counts it as finished (it is never recreated: no duplicates).
 */
public final class VisitorSpec {
    public CustomerEntity.Type type = CustomerEntity.Type.REGULAR;
    /** Event and effect it belongs to (-1 = none). */
    public long eventUid = -1;
    public int effectIndex = -1;
    public int moodStart;
    public double patienceMult = 1, buyMult = 1;
    /** Maximum price it accepts, over market (displayed price / market). */
    public double priceCap = 1.15;
    /** Products it browses (-1 = normal) and purchases it can make before leaving. */
    public int browses = -1, purchases = 1;
    /** Prefers products from this set ("set:&lt;id&gt;"), "packs", "cards" or "old_sets" (null = any). */
    @Nullable
    public String filter;
    /** Troublemaker (calmed by the owner's right click or the guard), inspector (does not buy and rates the shop), fair seller (asks at least market). */
    public boolean troublemaker, inspector, fairSeller, noBuy;
    /** Title shown above the head (language key; empty = a normal one). */
    public String titleKey = "";
    /** Lines on entering: tcgshop.evline.&lt;lines&gt;.&lt;n&gt; (n &lt; linesCount). */
    public String lines = "";
    public int linesCount;
    /** Returning regular customer (null = a new one). */
    @Nullable
    public ShopZones.Regular regular;
}

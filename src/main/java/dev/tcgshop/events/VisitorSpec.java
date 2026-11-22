package dev.tcgshop.events;

import dev.tcgshop.entity.CustomerEntity;
import dev.tcgshop.shop.ShopZones;
import org.jetbrains.annotations.Nullable;

/**
 * Cliente "especial" de un evento (ShopLife.forceSpawnVisitor). No se guarda: si el mundo se recarga, el cliente se
 * marcha (como cualquier cliente) y el evento lo cuenta como terminado (nunca se vuelve a crear: sin duplicados).
 */
public final class VisitorSpec {
    public CustomerEntity.Type type = CustomerEntity.Type.REGULAR;
    /** Evento y efecto al que pertenece (-1 = ninguno). */
    public long eventUid = -1;
    public int effectIndex = -1;
    public int moodStart;
    public double patienceMult = 1, buyMult = 1;
    /** Precio máximo que acepta, sobre el de mercado (precio expuesto / mercado). */
    public double priceCap = 1.15;
    /** Productos que mira (-1 = lo normal) y compras que puede hacer antes de irse. */
    public int browses = -1, purchases = 1;
    /** Prefiere productos de este set ("set:&lt;id&gt;"), "packs", "cards" u "old_sets" (null = cualquiera). */
    @Nullable
    public String filter;
    /** Molesto (lo calma el dueño con clic derecho o el guardia), inspector (no compra y puntúa la tienda), vendedor justo (pide al menos el mercado). */
    public boolean troublemaker, inspector, fairSeller, noBuy;
    /** Título que se ve sobre la cabeza (clave de idioma; vacío = uno normal). */
    public String titleKey = "";
    /** Frases al entrar: tcgshop.evline.&lt;lines&gt;.&lt;n&gt; (n &lt; linesCount). */
    public String lines = "";
    public int linesCount;
    /** Cliente habitual que vuelve (null = uno nuevo). */
    @Nullable
    public ShopZones.Regular regular;
}

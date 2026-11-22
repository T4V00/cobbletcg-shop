package dev.tcgshop.events;

/**
 * Pieza de un evento. Las instancias son de CONFIGURACIÓN (una por efecto de la definición, compartida por todas las
 * instancias de evento): el estado de cada evento en marcha va en {@link EventContext#data()} (NBT que se guarda con
 * la zona) o en los "bloqueos" de {@link EventInstance} (expositores, puertas, caja, luz).
 *
 * Ciclo de vida (todo en el hilo del servidor):
 *  · start   al empezar el evento (la zona está cargada y el dueño cerca).
 *  · tick    cada 20 ticks, SÓLO mientras la zona está cargada (si no, el evento sigue contando su fin absoluto).
 *  · resolve cuando alguien lo arregla (EventManager.resolve), con el "cómo" (p. ej. "mop", "guard", "metric").
 *  · end     siempre, una vez, al terminar (resuelto, caducado, /tcgshop event end o tienda borrada). Debe dejarlo todo
 *            limpio: quitar bloques/entidades propios y bloqueos. Puede llamarse con la zona descargada.
 * Ver EFFECTS.md para el contrato completo.
 */
public interface EventEffect {
    default void start(EventContext ctx) {}

    default void tick(EventContext ctx) {}

    default void resolve(EventContext ctx, String how) {}

    default void end(EventContext ctx) {}

    /** Tras cada recálculo de la tienda (cada minuto, con la zona cargada): para objetivos medibles (MetricEffect). */
    default void onRecompute(EventContext ctx) {}

    /** Cada venta a un NPC en la tienda (price = lo pagado, market = precio de mercado de 1 unidad). */
    default void onSale(EventContext ctx, net.minecraft.item.ItemStack item, long price, long market) {}

    /** Aporta modificadores (Mods) mientras el evento está activo. Se recalcula al cambiar el estado del evento. */
    default void contribute(Mods.Builder b, EventInstance inst, long now) {}

    /** ¿Ofrece al jugador una forma de arreglar el evento? (los negativos necesitan al menos una) */
    default boolean providesFix() { return false; }

    /** Si el efecto puede empezar en esta tienda (p. ej. hay sitio para colocar incidentes). */
    default boolean canStart(net.minecraft.server.world.ServerWorld w, dev.tcgshop.shop.ShopZones.Zone z) { return true; }

    /** ¿Sigue teniendo efecto tras resolverse? (si ningún efecto lo pide, el evento termina al resolverse) */
    default boolean keepsAfterResolve() { return false; }

    /** El evento necesita que la tienda tenga algo para tener sentido ("cashier", "register", "door", "playtable", "cobblemon"). */
    default java.util.Set<String> implicitRequires() { return java.util.Set.of(); }
}

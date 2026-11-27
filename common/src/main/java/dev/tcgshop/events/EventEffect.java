package dev.tcgshop.events;

/**
 * Building block of an event. Instances are CONFIGURATION (one per effect in the definition, shared by all
 * event instances): the state of each running event lives in {@link EventContext#data()} (NBT saved with
 * the zone) or in the "locks" of {@link EventInstance} (displays, doors, register, light).
 *
 * Lifecycle (all on the server thread):
 *  · start   when the event begins (the zone is loaded and the owner nearby).
 *  · tick    every 20 ticks, ONLY while the zone is loaded (otherwise the event keeps counting toward its absolute end).
 *  · resolve when someone fixes it (EventManager.resolve), with the "how" (e.g. "mop", "guard", "metric").
 *  · end     always, once, when it finishes (resolved, expired, /tcgshop event end or shop deleted). It must leave everything
 *            clean: remove its own blocks/entities and locks. May be called with the zone unloaded.
 * See EFFECTS.md for the full contract.
 */
public interface EventEffect {
    default void start(EventContext ctx) {}

    default void tick(EventContext ctx) {}

    default void resolve(EventContext ctx, String how) {}

    default void end(EventContext ctx) {}

    /** After each shop recompute (every minute, with the zone loaded): for measurable goals (MetricEffect). */
    default void onRecompute(EventContext ctx) {}

    /** Each sale to an NPC in the shop (price = amount paid, market = market price of 1 unit). */
    default void onSale(EventContext ctx, net.minecraft.world.item.ItemStack item, long price, long market) {}

    /** Contributes modifiers (Mods) while the event is active. Recomputed when the event state changes. */
    default void contribute(Mods.Builder b, EventInstance inst, long now) {}

    /** Does it give the player a way to fix the event? (negative ones need at least one) */
    default boolean providesFix() { return false; }

    /** Whether the effect can start in this shop (e.g. there is room to place incidents). */
    default boolean canStart(net.minecraft.server.level.ServerLevel w, dev.tcgshop.shop.ShopZones.Zone z) { return true; }

    /** Does it stay in effect after being resolved? (if no effect asks for it, the event ends when resolved) */
    default boolean keepsAfterResolve() { return false; }

    /** The event needs the shop to have something for it to make sense ("cashier", "register", "door", "playtable", "cobblemon"). */
    default java.util.Set<String> implicitRequires() { return java.util.Set.of(); }
}

package dev.tcgshop.client;

import dev.masterball.api.platform.Platform;
import dev.masterball.api.platform.ClientPlatform;
import com.google.common.collect.Multimap;
import dev.tcgshop.TcgShop;
import dev.tcgshop.network.TradePayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Adds "Trade cards" and "TCG Challenge" to Cobblemon's player interaction wheel. All via reflection: if Cobblemon
 * changes its internal classes, the option simply does not appear (and /tcgtrade is still available).
 */
public final class CobblemonWheelCompat {
    private static final String PKG = "com.cobblemon.mod.common.client.gui.interact.wheel.";
    private static final ResourceLocation ICON = ResourceLocation.fromNamespaceAndPath(TcgShop.MOD_ID, "textures/gui/card_trade_icon.png");
    private static final ResourceLocation DUEL_ICON = ResourceLocation.fromNamespaceAndPath(TcgShop.MOD_ID, "textures/gui/tcg_duel_icon.png");
    private static boolean warned;

    private CobblemonWheelCompat() {}

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void addCardTrade(Object gui) {
        try {
            Minecraft mc = Minecraft.getInstance();
            UUID target = null;
            if (mc.crosshairPickEntity instanceof Player p) target = p.getUUID();
            else if (mc.hitResult instanceof EntityHitResult ehr && ehr.getEntity() instanceof Player p) target = p.getUUID();
            if (target == null || gui == null || mc.player == null || target.equals(mc.player.getUUID())) return;

            Field f = gui.getClass().getDeclaredField("options");
            f.setAccessible(true);
            Multimap<Object, Object> options = (Multimap<Object, Object>) f.get(gui);
            Class<? extends Enum> orientation = (Class<? extends Enum>) Class.forName(PKG + "Orientation");
            Object slot = freeSlot(options, orientation, null);

            Class<?> fn0 = Class.forName("kotlin.jvm.functions.Function0");
            Object unit = Class.forName("kotlin.Unit").getField("INSTANCE").get(null);
            UUID finalTarget = target;
            Object colour = function(fn0, () -> null);
            Object press = function(fn0, () -> {
                ClientPlatform.INSTANCE.send(new TradePayloads.Request(finalTarget));
                mc.setScreen(null);
                return unit;
            });
            Class<?> optionClass = Class.forName(PKG + "InteractWheelOption");
            Constructor<?> ctor = optionClass.getConstructor(ResourceLocation.class, ResourceLocation.class, boolean.class, String.class, fn0, fn0);
            options.put(slot, ctor.newInstance(ICON, null, true, "tcgshop.trade.wheel", colour, press));
            // real TCG challenge (friendly match)
            Object duel = function(fn0, () -> {
                com.google.gson.JsonObject o = new com.google.gson.JsonObject();
                o.addProperty("a", "challenge");
                o.addProperty("target", finalTarget.toString());
                ClientPtcg.leagueAction(o);
                mc.setScreen(null);
                return unit;
            });
            options.put(freeSlot(options, orientation, slot), ctor.newInstance(DUEL_ICON, null, true, "tcgshop.ptcg.challenge.wheel", colour, duel));
        } catch (Throwable e) {
            if (!warned) {
                warned = true;
                TcgShop.LOGGER.warn("Could not add card trading to the Cobblemon wheel: {}", e.toString());
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object freeSlot(Multimap<Object, Object> options, Class<? extends Enum> orientation, Object taken) {
        for (String name : new String[]{"SOUTH", "EAST", "WEST", "SOUTHEAST", "SOUTHWEST", "NORTHWEST", "NORTHEAST"}) {
            Object o = Enum.valueOf(orientation, name);
            if (!options.containsKey(o) && o != taken) return o;
        }
        return Enum.valueOf(orientation, "SOUTH");
    }

    private static Object function(Class<?> fn0, Supplier<Object> body) {
        return Proxy.newProxyInstance(fn0.getClassLoader(), new Class<?>[]{fn0}, (proxy, method, args) -> switch (method.getName()) {
            case "invoke" -> body.get();
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            case "toString" -> "tcgshop-fn";
            default -> null;
        });
    }
}

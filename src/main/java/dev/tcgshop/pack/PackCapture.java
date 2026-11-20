package dev.tcgshop.pack;

import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Captura lo que cobbletcg entrega (con PlayerInventory.offerOrDrop) mientras abrimos un sobre de forma
 * controlada. Los ítems capturados NO entran al inventario: PackOpener decide qué hacer con ellos
 * (devolver el sobre abierto, dar las cartas, o destruirlas si el sobre se abrió mal).
 */
public final class PackCapture {
    private static final ThreadLocal<Session> CURRENT = new ThreadLocal<>();

    private record Session(ServerPlayerEntity player, List<ItemStack> captured) {}

    private PackCapture() {}

    static void start(ServerPlayerEntity player) {
        CURRENT.set(new Session(player, new ArrayList<>()));
    }

    static List<ItemStack> stop() {
        Session s = CURRENT.get();
        CURRENT.remove();
        return s == null ? List.of() : s.captured;
    }

    /** Llamado desde PlayerInventoryMixin. true = capturado (cancelar la inserción). */
    public static boolean onOffer(ServerPlayerEntity player, ItemStack stack) {
        Session s = CURRENT.get();
        if (s == null || s.player != player || stack.isEmpty()) return false;
        s.captured.add(stack.copy());
        stack.setCount(0);
        return true;
    }
}

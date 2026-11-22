package dev.tcgshop.network;

import dev.tcgshop.TcgShop;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/** Paquetes de CobbleGrade (gradeo de cartas). */
public final class GradePayloads {
    private GradePayloads() {}

    public static final int SYNC = 0, SUBMIT = 1, COLLECT = 2;

    private static List<ItemStack> readStacks(RegistryByteBuf buf) {
        int n = buf.readVarInt();
        List<ItemStack> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) out.add(ItemStack.OPTIONAL_PACKET_CODEC.decode(buf));
        return out;
    }

    private static void writeStacks(RegistryByteBuf buf, List<ItemStack> l) {
        buf.writeVarInt(l.size());
        for (ItemStack st : l) ItemStack.OPTIONAL_PACKET_CODEC.encode(buf, st);
    }

    /**
     * Cliente -> servidor: pedir datos, enviar cartas (huecos del inventario + servicio) o recoger un pedido.
     * {@code expected} va en paralelo a {@code slots}: el id de la carta que el jugador eligió en cada hueco ("" = sin
     * comprobar), para que el servidor no gradee otra carta si el hueco cambió por el camino.
     */
    public record Action(int action, int service, List<Integer> slots, List<String> expected, int order) implements CustomPayload {
        public static final Id<Action> ID = new Id<>(Identifier.of(TcgShop.MOD_ID, "grade_action"));

        /** Sin ids esperados (SYNC, COLLECT). */
        public Action(int action, int service, List<Integer> slots, int order) {
            this(action, service, slots, List.of(), order);
        }

        /** Id esperado del hueco i-ésimo ("" si no viene). */
        public String expectedAt(int i) {
            return i >= 0 && i < expected.size() ? expected.get(i) : "";
        }

        public static final PacketCodec<RegistryByteBuf, Action> CODEC = new PacketCodec<>() {
            @Override
            public Action decode(RegistryByteBuf buf) {
                int a = buf.readVarInt(), s = buf.readVarInt(), n = buf.readVarInt();
                if (n < 0 || n > 64) throw new io.netty.handler.codec.DecoderException("grade_action: demasiados huecos " + n);
                List<Integer> slots = new ArrayList<>(n);
                List<String> expected = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    slots.add(buf.readVarInt());
                    expected.add(buf.readString(256));
                }
                return new Action(a, s, slots, expected, buf.readVarInt());
            }

            @Override
            public void encode(RegistryByteBuf buf, Action p) {
                buf.writeVarInt(p.action);
                buf.writeVarInt(p.service);
                buf.writeVarInt(p.slots.size());
                for (int i = 0; i < p.slots.size(); i++) {
                    buf.writeVarInt(p.slots.get(i));
                    buf.writeString(p.expectedAt(i), 256);
                }
                buf.writeVarInt(p.order);
            }
        };

        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }

    /** Un pedido en curso. */
    public record Order(int id, int service, long submitted, long ready, List<ItemStack> cards) {}

    /** Servidor -> cliente: pedidos del jugador y la hora del servidor. */
    public record Sync(long now, List<Order> orders) implements CustomPayload {
        public static final Id<Sync> ID = new Id<>(Identifier.of(TcgShop.MOD_ID, "grade_sync"));
        public static final PacketCodec<RegistryByteBuf, Sync> CODEC = new PacketCodec<>() {
            @Override
            public Sync decode(RegistryByteBuf buf) {
                long now = buf.readVarLong();
                int n = buf.readVarInt();
                List<Order> l = new ArrayList<>(n);
                for (int i = 0; i < n; i++) l.add(new Order(buf.readVarInt(), buf.readVarInt(), buf.readVarLong(), buf.readVarLong(), readStacks(buf)));
                return new Sync(now, l);
            }

            @Override
            public void encode(RegistryByteBuf buf, Sync p) {
                buf.writeVarLong(p.now);
                buf.writeVarInt(p.orders.size());
                for (Order o : p.orders) {
                    buf.writeVarInt(o.id);
                    buf.writeVarInt(o.service);
                    buf.writeVarLong(o.submitted);
                    buf.writeVarLong(o.ready);
                    writeStacks(buf, o.cards);
                }
            }
        };

        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }

    /** Servidor -> cliente: cartas recién gradeadas (pantalla de revelado). */
    public record Reveal(List<ItemStack> cards, long valueBefore, long valueAfter) implements CustomPayload {
        public static final Id<Reveal> ID = new Id<>(Identifier.of(TcgShop.MOD_ID, "grade_reveal"));
        public static final PacketCodec<RegistryByteBuf, Reveal> CODEC = new PacketCodec<>() {
            @Override
            public Reveal decode(RegistryByteBuf buf) {
                return new Reveal(readStacks(buf), buf.readVarLong(), buf.readVarLong());
            }

            @Override
            public void encode(RegistryByteBuf buf, Reveal p) {
                writeStacks(buf, p.cards);
                buf.writeVarLong(p.valueBefore);
                buf.writeVarLong(p.valueAfter);
            }
        };

        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }
}

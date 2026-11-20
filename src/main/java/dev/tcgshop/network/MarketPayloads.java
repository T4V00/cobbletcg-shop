package dev.tcgshop.network;

import dev.tcgshop.TcgShop;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/** Venta rápida de cartas (teléfono / Mayorista), esmeraldas y el Coleccionista ambulante (intercambios). */
public final class MarketPayloads {
    private MarketPayloads() {}

    /** source: -1 = teléfono, si no id de la entidad (Mayorista). */
    public static final int SELL_CARDS = 0, SELL_EMERALDS = 1;
    public static final int TRADE = 0, SELL_WISH = 1;

    private static List<Integer> readInts(RegistryByteBuf buf, int max) {
        int n = Math.min(max, buf.readVarInt());
        List<Integer> l = new ArrayList<>(n);
        for (int i = 0; i < n; i++) l.add(buf.readVarInt());
        return l;
    }

    private static void writeInts(RegistryByteBuf buf, List<Integer> l) {
        buf.writeVarInt(l.size());
        for (int v : l) buf.writeVarInt(v);
    }

    /** Cliente -> servidor: vender cartas (huecos del inventario) o todas las esmeraldas. */
    public record Sell(int source, int mode, List<Integer> slots) implements CustomPayload {
        public static final Id<Sell> ID = new Id<>(Identifier.of(TcgShop.MOD_ID, "card_sell"));
        public static final PacketCodec<RegistryByteBuf, Sell> CODEC = new PacketCodec<>() {
            @Override
            public Sell decode(RegistryByteBuf buf) {
                return new Sell(buf.readVarInt(), buf.readVarInt(), readInts(buf, 64));
            }

            @Override
            public void encode(RegistryByteBuf buf, Sell p) {
                buf.writeVarInt(p.source);
                buf.writeVarInt(p.mode);
                writeInts(buf, p.slots);
            }
        };

        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }

    /** Servidor -> cliente: el puesto del Coleccionista (sus cartas, la carta tuya que le interesa y cuántos tratos le quedan). */
    public record TraderOpen(int entity, List<ItemStack> stock, List<Long> prices, int wishSlot, long wishPrice, int tradesLeft)
            implements CustomPayload {
        public static final Id<TraderOpen> ID = new Id<>(Identifier.of(TcgShop.MOD_ID, "trader_open"));
        public static final PacketCodec<RegistryByteBuf, TraderOpen> CODEC = new PacketCodec<>() {
            @Override
            public TraderOpen decode(RegistryByteBuf buf) {
                int e = buf.readVarInt(), n = Math.min(16, buf.readVarInt());
                List<ItemStack> st = new ArrayList<>(n);
                List<Long> pr = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    st.add(ItemStack.OPTIONAL_PACKET_CODEC.decode(buf));
                    pr.add(buf.readVarLong());
                }
                return new TraderOpen(e, st, pr, buf.readVarInt(), buf.readVarLong(), buf.readVarInt());
            }

            @Override
            public void encode(RegistryByteBuf buf, TraderOpen p) {
                buf.writeVarInt(p.entity);
                buf.writeVarInt(p.stock.size());
                for (int i = 0; i < p.stock.size(); i++) {
                    ItemStack.OPTIONAL_PACKET_CODEC.encode(buf, p.stock.get(i));
                    buf.writeVarLong(p.prices.get(i));
                }
                buf.writeVarInt(p.wishSlot);
                buf.writeVarLong(p.wishPrice);
                buf.writeVarInt(p.tradesLeft);
            }
        };

        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }

    /** Cliente -> servidor: cambiar una carta suya (index) por una tuya (slot) o venderle la que le interesa. */
    public record TraderAction(int entity, int action, int index, int slot) implements CustomPayload {
        public static final Id<TraderAction> ID = new Id<>(Identifier.of(TcgShop.MOD_ID, "trader_action"));
        public static final PacketCodec<RegistryByteBuf, TraderAction> CODEC = new PacketCodec<>() {
            @Override
            public TraderAction decode(RegistryByteBuf buf) {
                return new TraderAction(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
            }

            @Override
            public void encode(RegistryByteBuf buf, TraderAction p) {
                buf.writeVarInt(p.entity);
                buf.writeVarInt(p.action);
                buf.writeVarInt(p.index);
                buf.writeVarInt(p.slot);
            }
        };

        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }
}

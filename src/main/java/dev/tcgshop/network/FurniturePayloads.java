package dev.tcgshop.network;

import dev.tcgshop.TcgShop;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Tienda de muebles (CobbleFurnies) y peluches (Pokéblocks): Vendedor de muebles, Mayorista y teléfono.
 * source: -1 = teléfono; si no, id de la entidad (Vendedor de muebles o Mayorista).
 */
public final class FurniturePayloads {
    private FurniturePayloads() {}

    /** Máximo de entradas que aceptamos al decodificar (por seguridad). */
    public static final int MAX_ENTRIES = 4096;

    /**
     * source especial de {@link Open} (servidor -> cliente, al entrar y tras /tcgshop reload): no es un catálogo, sino la
     * info de las estatuas de lujo según la config del servidor (ids = estatuas a la venta, vacío si están desactivadas;
     * buy = sus precios). Ver LuxuryShop.syncTo.
     */
    public static final int SOURCE_LUXURY_SYNC = -2;

    /** Cliente -> servidor: pide el catálogo (al abrir la pestaña / app). */
    public record Request(int source) implements CustomPayload {
        public static final Id<Request> ID = new Id<>(Identifier.of(TcgShop.MOD_ID, "furniture_request"));
        public static final PacketCodec<RegistryByteBuf, Request> CODEC = new PacketCodec<>() {
            @Override
            public Request decode(RegistryByteBuf buf) {
                return new Request(buf.readVarInt());
            }

            @Override
            public void encode(RegistryByteBuf buf, Request p) {
                buf.writeVarInt(p.source);
            }
        };

        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }

    /**
     * Servidor -> cliente: catálogo completo (full = true) o sólo el saldo actualizado tras una compra/venta (full = false,
     * listas vacías). open = abrir la pantalla del Vendedor si no está abierta. error = clave de traducción del motivo
     * si el servidor no puede atenderte (sin teléfono, NPC lejos, mods ausentes...); "" si todo va bien.
     */
    public record Open(int source, boolean open, boolean full, long balance, List<String> ids, List<Long> buy, List<Long> sell,
                       String error) implements CustomPayload {
        public static final Id<Open> ID = new Id<>(Identifier.of(TcgShop.MOD_ID, "furniture_open"));

        /** Respuesta de rechazo: catálogo vacío con el motivo (el cliente deja de esperar y lo muestra). */
        public static Open denied(int source, long balance, String errorKey) {
            return new Open(source, false, true, balance, List.of(), List.of(), List.of(), errorKey == null ? "" : errorKey);
        }
        public static final PacketCodec<RegistryByteBuf, Open> CODEC = new PacketCodec<>() {
            @Override
            public Open decode(RegistryByteBuf buf) {
                int source = buf.readVarInt();
                boolean open = buf.readBoolean(), full = buf.readBoolean();
                long balance = buf.readVarLong();
                int n = Math.min(MAX_ENTRIES, buf.readVarInt());
                List<String> ids = new ArrayList<>(n);
                List<Long> buy = new ArrayList<>(n), sell = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    ids.add(buf.readString(256));
                    buy.add(buf.readVarLong());
                    sell.add(buf.readVarLong());
                }
                String error = buf.readString(256);
                return new Open(source, open, full, balance, ids, buy, sell, error);
            }

            @Override
            public void encode(RegistryByteBuf buf, Open p) {
                buf.writeVarInt(p.source);
                buf.writeBoolean(p.open);
                buf.writeBoolean(p.full);
                buf.writeVarLong(p.balance);
                int n = Math.min(MAX_ENTRIES, Math.min(p.ids.size(), Math.min(p.buy.size(), p.sell.size())));
                buf.writeVarInt(n);
                for (int i = 0; i < n; i++) {
                    buf.writeString(p.ids.get(i), 256);
                    buf.writeVarLong(p.buy.get(i));
                    buf.writeVarLong(p.sell.get(i));
                }
                buf.writeString(p.error == null ? "" : p.error, 256);
            }
        };

        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }

    /**
     * Cliente -> servidor: comprar (sell = false) o vender (sell = true) qty unidades de un mueble/peluche/estatua.
     * price = precio por unidad que el jugador vio y confirmó; si al comprar ya no coincide con el del servidor (p.ej.
     * tras /tcgshop reload), el servidor rechaza la compra y reenvía el catálogo.
     */
    public record Trade(int source, String item, int qty, boolean sell, long price) implements CustomPayload {
        public static final Id<Trade> ID = new Id<>(Identifier.of(TcgShop.MOD_ID, "furniture_trade"));
        public static final PacketCodec<RegistryByteBuf, Trade> CODEC = new PacketCodec<>() {
            @Override
            public Trade decode(RegistryByteBuf buf) {
                return new Trade(buf.readVarInt(), buf.readString(256), buf.readVarInt(), buf.readBoolean(), buf.readVarLong());
            }

            @Override
            public void encode(RegistryByteBuf buf, Trade p) {
                buf.writeVarInt(p.source);
                buf.writeString(p.item, 256);
                buf.writeVarInt(p.qty);
                buf.writeBoolean(p.sell);
                buf.writeVarLong(p.price);
            }
        };

        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }
}

package dev.tcgshop.network;

import dev.tcgshop.TcgShop;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Furniture (CobbleFurnies) and plushie (Pokéblocks) shop: Furniture Seller, Wholesaler and phone.
 * source: -1 = phone; otherwise, entity id (Furniture Seller or Wholesaler).
 */
public final class FurniturePayloads {
    private FurniturePayloads() {}

    /** Maximum number of entries we accept when decoding (for safety). */
    public static final int MAX_ENTRIES = 4096;

    /**
     * Special source of {@link Open} (server -> client, on join and after /tcgshop reload): not a catalog, but the
     * luxury statue info from the server config (ids = statues for sale, empty if disabled;
     * buy = their prices). See LuxuryShop.syncTo.
     */
    public static final int SOURCE_LUXURY_SYNC = -2;

    /** Client -> server: requests the catalog (when opening the tab / app). */
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
     * Server -> client: full catalog (full = true) or just the updated balance after a purchase/sale (full = false,
     * empty lists). open = open the Seller screen if it is not open. error = translation key of the reason
     * if the server cannot serve you (no phone, NPC too far, missing mods...); "" if all is well.
     */
    public record Open(int source, boolean open, boolean full, long balance, List<String> ids, List<Long> buy, List<Long> sell,
                       String error) implements CustomPayload {
        public static final Id<Open> ID = new Id<>(Identifier.of(TcgShop.MOD_ID, "furniture_open"));

        /** Denial response: empty catalog with the reason (the client stops waiting and shows it). */
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
     * Client -> server: buy (sell = false) or sell (sell = true) qty units of a furniture piece/plushie/statue.
     * price = unit price the player saw and confirmed; if on purchase it no longer matches the server's (e.g.
     * after /tcgshop reload), the server rejects the purchase and resends the catalog.
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

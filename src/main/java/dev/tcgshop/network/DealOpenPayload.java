package dev.tcgshop.network;

import dev.tcgshop.TcgShop;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;
import net.minecraft.util.Identifier;

/**
 * Servidor -> cliente: un NPC te propone un trato.
 *  · mode 0 (BUY)   quiere comprarte `item` (de tus expositores) por `price`.
 *  · mode 1 (SELL)  te vende `item` por `price`.
 *  · mode 2 (TRADE) te cambia su carta `item` por tu carta `want` (expuesta en la tienda); price = valor de la suya.
 */
public record DealOpenPayload(int entityId, Text name, Text line, ItemStack item, long price, long market, int mode, ItemStack want, long wantMarket,
                              int persona, int patience, int maxPatience)
        implements CustomPayload {
    /** CLOSED: el trato terminó (se muestra la última frase y se cierra la ventana). */
    public static final int BUY = 0, SELL = 1, TRADE = 2, CLOSED = 9;
    public static final Id<DealOpenPayload> ID = new Id<>(Identifier.of(TcgShop.MOD_ID, "deal_open"));
    public static final PacketCodec<RegistryByteBuf, DealOpenPayload> CODEC = new PacketCodec<>() {
        @Override
        public DealOpenPayload decode(RegistryByteBuf buf) {
            return new DealOpenPayload(buf.readVarInt(), TextCodecs.REGISTRY_PACKET_CODEC.decode(buf), TextCodecs.REGISTRY_PACKET_CODEC.decode(buf),
                    ItemStack.OPTIONAL_PACKET_CODEC.decode(buf), buf.readVarLong(), buf.readVarLong(), buf.readVarInt(),
                    ItemStack.OPTIONAL_PACKET_CODEC.decode(buf), buf.readVarLong(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
        }

        @Override
        public void encode(RegistryByteBuf buf, DealOpenPayload p) {
            buf.writeVarInt(p.entityId);
            TextCodecs.REGISTRY_PACKET_CODEC.encode(buf, p.name);
            TextCodecs.REGISTRY_PACKET_CODEC.encode(buf, p.line);
            ItemStack.OPTIONAL_PACKET_CODEC.encode(buf, p.item);
            buf.writeVarLong(p.price);
            buf.writeVarLong(p.market);
            buf.writeVarInt(p.mode);
            ItemStack.OPTIONAL_PACKET_CODEC.encode(buf, p.want);
            buf.writeVarLong(p.wantMarket);
            buf.writeVarInt(p.persona);
            buf.writeVarInt(p.patience);
            buf.writeVarInt(p.maxPatience);
        }
    };

    public boolean sellToYou() { return mode == SELL; }

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}

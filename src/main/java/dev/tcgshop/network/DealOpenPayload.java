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
 * Server -> client: an NPC offers you a deal.
 *  · mode 0 (BUY)   wants to buy `item` (from your displays) from you for `price`.
 *  · mode 1 (SELL)  sells you `item` for `price`.
 *  · mode 2 (TRADE) swaps its card `item` for your card `want` (displayed in the shop); price = value of theirs.
 * 1.0: `ask` = your asking price for the product (BUY deals and their CLOSED message; 0 = none). Whatever the
 * customer pays above it is a tip (see {@link #tip()}).
 */
public record DealOpenPayload(int entityId, Text name, Text line, ItemStack item, long price, long market, int mode, ItemStack want, long wantMarket,
                              int persona, int patience, int maxPatience, long ask)
        implements CustomPayload {
    /** CLOSED: the deal ended (the last line is shown and the window closes). */
    public static final int BUY = 0, SELL = 1, TRADE = 2, CLOSED = 9;
    public static final Id<DealOpenPayload> ID = new Id<>(Identifier.of(TcgShop.MOD_ID, "deal_open"));
    public static final PacketCodec<RegistryByteBuf, DealOpenPayload> CODEC = new PacketCodec<>() {
        @Override
        public DealOpenPayload decode(RegistryByteBuf buf) {
            return new DealOpenPayload(buf.readVarInt(), TextCodecs.REGISTRY_PACKET_CODEC.decode(buf), TextCodecs.REGISTRY_PACKET_CODEC.decode(buf),
                    ItemStack.OPTIONAL_PACKET_CODEC.decode(buf), buf.readVarLong(), buf.readVarLong(), buf.readVarInt(),
                    ItemStack.OPTIONAL_PACKET_CODEC.decode(buf), buf.readVarLong(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarLong());
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
            buf.writeVarLong(p.ask);
        }
    };

    public boolean sellToYou() { return mode == SELL; }

    /** Tip: what the customer pays above your asking price (0 when there is no asking price or no overpay). */
    public long tip() { return ask > 0 && price > ask ? price - ask : 0; }

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}

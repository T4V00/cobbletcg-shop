package dev.tcgshop.network;

import dev.tcgshop.TcgShop;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Quick-sell of cards (phone / Wholesaler), emeralds and the wandering Collector (trades). */
public final class MarketPayloads {
    private MarketPayloads() {}

    /** source: -1 = phone, otherwise entity id (Wholesaler). */
    public static final int SELL_CARDS = 0, SELL_EMERALDS = 1;
    public static final int TRADE = 0, SELL_WISH = 1;

    private static List<Integer> readInts(RegistryFriendlyByteBuf buf, int max) {
        int n = Math.min(max, buf.readVarInt());
        List<Integer> l = new ArrayList<>(n);
        for (int i = 0; i < n; i++) l.add(buf.readVarInt());
        return l;
    }

    private static void writeInts(RegistryFriendlyByteBuf buf, List<Integer> l) {
        buf.writeVarInt(l.size());
        for (int v : l) buf.writeVarInt(v);
    }

    /** Client -> server: sell cards (inventory slots) or all emeralds. */
    public record Sell(int source, int mode, List<Integer> slots) implements CustomPacketPayload {
        public static final Type<Sell> ID = new Type<>(ResourceLocation.fromNamespaceAndPath(TcgShop.MOD_ID, "card_sell"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Sell> CODEC = new StreamCodec<>() {
            @Override
            public Sell decode(RegistryFriendlyByteBuf buf) {
                return new Sell(buf.readVarInt(), buf.readVarInt(), readInts(buf, 64));
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buf, Sell p) {
                buf.writeVarInt(p.source);
                buf.writeVarInt(p.mode);
                writeInts(buf, p.slots);
            }
        };

        @Override
        public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    /**
     * Server -> client: the Collector's stall (their cards, the card of yours they want and how many deals they have left).
     * 1.1: your limits by phone tier (cards you can give / take in one deal), charisma talks left and your charisma level.
     */
    public record TraderOpen(int entity, List<ItemStack> stock, List<Long> prices, int wishSlot, long wishPrice, int tradesLeft,
                             int giveMax, int takeMax, int talksLeft, int charisma)
            implements CustomPacketPayload {
        public static final Type<TraderOpen> ID = new Type<>(ResourceLocation.fromNamespaceAndPath(TcgShop.MOD_ID, "trader_open"));
        public static final StreamCodec<RegistryFriendlyByteBuf, TraderOpen> CODEC = new StreamCodec<>() {
            @Override
            public TraderOpen decode(RegistryFriendlyByteBuf buf) {
                int e = buf.readVarInt(), n = Math.min(16, buf.readVarInt());
                List<ItemStack> st = new ArrayList<>(n);
                List<Long> pr = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    st.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
                    pr.add(buf.readVarLong());
                }
                return new TraderOpen(e, st, pr, buf.readVarInt(), buf.readVarLong(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buf, TraderOpen p) {
                buf.writeVarInt(p.entity);
                buf.writeVarInt(p.stock.size());
                for (int i = 0; i < p.stock.size(); i++) {
                    ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, p.stock.get(i));
                    buf.writeVarLong(p.prices.get(i));
                }
                buf.writeVarInt(p.wishSlot);
                buf.writeVarLong(p.wishPrice);
                buf.writeVarInt(p.tradesLeft);
                buf.writeVarInt(p.giveMax);
                buf.writeVarInt(p.takeMax);
                buf.writeVarInt(p.talksLeft);
                buf.writeVarInt(p.charisma);
            }
        };

        @Override
        public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    /** Client -> server: swap one of their cards (index) for one of yours (slot) or sell them the one they want. */
    public record TraderAction(int entity, int action, int index, int slot) implements CustomPacketPayload {
        public static final Type<TraderAction> ID = new Type<>(ResourceLocation.fromNamespaceAndPath(TcgShop.MOD_ID, "trader_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, TraderAction> CODEC = new StreamCodec<>() {
            @Override
            public TraderAction decode(RegistryFriendlyByteBuf buf) {
                return new TraderAction(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buf, TraderAction p) {
                buf.writeVarInt(p.entity);
                buf.writeVarInt(p.action);
                buf.writeVarInt(p.index);
                buf.writeVarInt(p.slot);
            }
        };

        @Override
        public Type<? extends CustomPacketPayload> type() { return ID; }
    }
}

package dev.tcgshop.network;

import dev.tcgshop.TcgShop;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Server -> client: an NPC offers you a deal.
 *  · mode 0 (BUY)   wants to buy `item` (from your displays) from you for `price`.
 *  · mode 1 (SELL)  sells you `item` for `price`.
 *  · mode 2 (TRADE) swaps its card `item` for your card `want` (displayed in the shop); price = value of theirs.
 * 1.0: `ask` = your asking price for the product (BUY deals and their CLOSED message; 0 = none). Whatever the
 * customer pays above it is a tip (see {@link #tip()}).
 * 1.1: `flags`: bit 0 = generous (the first offer is already good for you: haggling makes them leave, charm instead),
 * bits 1-4 = charm options already used (joke, compliment, story, small talk), bits 5-6 = charm attempts left.
 * `odds`: the server's real success chances (see {@link #odds(double...)}): charm options in Charisma.CHARM_* order,
 * then Persuade; 0 = not sent.
 */
public record DealOpenPayload(int entityId, Component name, Component line, ItemStack item, long price, long market, int mode, ItemStack want, long wantMarket,
                              int persona, int patience, int maxPatience, long ask, int flags, long odds)
        implements CustomPacketPayload {
    /** CLOSED: the deal ended (the last line is shown and the window closes). */
    public static final int BUY = 0, SELL = 1, TRADE = 2, CLOSED = 9;
    public static final Type<DealOpenPayload> ID = new Type<>(ResourceLocation.fromNamespaceAndPath(TcgShop.MOD_ID, "deal_open"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DealOpenPayload> CODEC = new StreamCodec<>() {
        @Override
        public DealOpenPayload decode(RegistryFriendlyByteBuf buf) {
            return new DealOpenPayload(buf.readVarInt(), ComponentSerialization.STREAM_CODEC.decode(buf), ComponentSerialization.STREAM_CODEC.decode(buf),
                    ItemStack.OPTIONAL_STREAM_CODEC.decode(buf), buf.readVarLong(), buf.readVarLong(), buf.readVarInt(),
                    ItemStack.OPTIONAL_STREAM_CODEC.decode(buf), buf.readVarLong(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarLong(),
                    buf.readVarInt(), buf.readVarLong());
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, DealOpenPayload p) {
            buf.writeVarInt(p.entityId);
            ComponentSerialization.STREAM_CODEC.encode(buf, p.name);
            ComponentSerialization.STREAM_CODEC.encode(buf, p.line);
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, p.item);
            buf.writeVarLong(p.price);
            buf.writeVarLong(p.market);
            buf.writeVarInt(p.mode);
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, p.want);
            buf.writeVarLong(p.wantMarket);
            buf.writeVarInt(p.persona);
            buf.writeVarInt(p.patience);
            buf.writeVarInt(p.maxPatience);
            buf.writeVarLong(p.ask);
            buf.writeVarInt(p.flags);
            buf.writeVarLong(p.odds);
        }
    };

    public boolean sellToYou() { return mode == SELL; }

    public static final int F_GENEROUS = 1;

    /** 1.1: the customer's first offer was already good for you (charm options instead of haggling). */
    public boolean generous() { return (flags & F_GENEROUS) != 0; }

    /** 1.1: that charm option (Charisma.CHARM_*) was already used in this deal. */
    public boolean charmUsed(int kind) { return (flags >> (1 + kind) & 1) != 0; }

    /** Index of Persuade in {@link #odds}. */
    public static final int ODDS_PERSUADE = 4;

    /** Packs up to 8 chances (0-1) as 7-bit percents + 1 (0 = unknown). */
    public static long odds(double... chances) {
        long o = 0;
        for (int i = 0; i < Math.min(8, chances.length); i++)
            o |= (long) (Math.max(0, Math.min(100, (int) Math.round(chances[i] * 100))) + 1) << (7 * i);
        return o;
    }

    /** 1.1: the server's real chance (percent) for that index (charm kind or {@link #ODDS_PERSUADE}); -1 if not sent. */
    public int oddsPct(int i) {
        return (int) (odds >> (7 * i) & 127) - 1;
    }

    /** 1.1: charm attempts left in this deal. */
    public int charmsLeft() { return flags >> 5 & 3; }

    public static int flags(boolean generous, int usedMask, int left) {
        return (generous ? F_GENEROUS : 0) | (usedMask & 15) << 1 | Math.max(0, Math.min(3, left)) << 5;
    }

    /** Tip: what the customer pays above your asking price (0 when there is no asking price or no overpay). */
    public long tip() { return ask > 0 && price > ask ? price - ask : 0; }

    @Override
    public Type<? extends CustomPacketPayload> type() { return ID; }
}

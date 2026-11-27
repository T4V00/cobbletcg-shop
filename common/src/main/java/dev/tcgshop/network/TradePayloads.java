package dev.tcgshop.network;

import dev.tcgshop.TcgShop;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Packets for card trading between players (TCG Pocket style). */
public final class TradePayloads {
    private TradePayloads() {}

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> id(String path) {
        return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(TcgShop.MOD_ID, path));
    }

    /** C→S: request a trade with the given player (from the Cobblemon wheel). */
    public record Request(UUID target) implements CustomPacketPayload {
        public static final Type<Request> ID = id("trade_request");
        public static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC = StreamCodec.composite(UUIDUtil.STREAM_CODEC, Request::target, Request::new);
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    /** C→S: offer the card in that inventory slot (0-35). */
    public record Select(int slot) implements CustomPacketPayload {
        public static final Type<Select> ID = id("trade_select");
        public static final StreamCodec<RegistryFriendlyByteBuf, Select> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Select::slot, Select::new);
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    /** C→S: confirm (true) or cancel the trade (false). */
    public record Answer(boolean confirm) implements CustomPacketPayload {
        public static final Type<Answer> ID = id("trade_answer");
        public static final StreamCodec<RegistryFriendlyByteBuf, Answer> CODEC = StreamCodec.composite(ByteBufCodecs.BOOL, Answer::confirm, Answer::new);
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    /** S→C: open the trade screen with that partner. */
    public record Open(String partner) implements CustomPacketPayload {
        public static final Type<Open> ID = id("trade_open");
        public static final StreamCodec<RegistryFriendlyByteBuf, Open> CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, Open::partner, Open::new);
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    /** S→C: current state. message = language key ("" if none). */
    public record State(ItemStack mine, ItemStack theirs, boolean myOk, boolean theirOk, String message) implements CustomPacketPayload {
        public static final Type<State> ID = id("trade_state");
        public static final StreamCodec<RegistryFriendlyByteBuf, State> CODEC = StreamCodec.composite(
                ItemStack.OPTIONAL_STREAM_CODEC, State::mine, ItemStack.OPTIONAL_STREAM_CODEC, State::theirs,
                ByteBufCodecs.BOOL, State::myOk, ByteBufCodecs.BOOL, State::theirOk, ByteBufCodecs.STRING_UTF8, State::message, State::new);
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    /** S→C: end of the trade (completed or cancelled). */
    public record Done(ItemStack gave, ItemStack got, boolean success, String message) implements CustomPacketPayload {
        public static final Type<Done> ID = id("trade_done");
        public static final StreamCodec<RegistryFriendlyByteBuf, Done> CODEC = StreamCodec.composite(
                ItemStack.OPTIONAL_STREAM_CODEC, Done::gave, ItemStack.OPTIONAL_STREAM_CODEC, Done::got,
                ByteBufCodecs.BOOL, Done::success, ByteBufCodecs.STRING_UTF8, Done::message, Done::new);
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }
}

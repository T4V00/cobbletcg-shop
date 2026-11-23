package dev.tcgshop.network;

import dev.tcgshop.TcgShop;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;

import java.util.UUID;

/** Packets for card trading between players (TCG Pocket style). */
public final class TradePayloads {
    private TradePayloads() {}

    private static <T extends CustomPayload> CustomPayload.Id<T> id(String path) {
        return new CustomPayload.Id<>(Identifier.of(TcgShop.MOD_ID, path));
    }

    /** C→S: request a trade with the given player (from the Cobblemon wheel). */
    public record Request(UUID target) implements CustomPayload {
        public static final Id<Request> ID = id("trade_request");
        public static final PacketCodec<RegistryByteBuf, Request> CODEC = PacketCodec.tuple(Uuids.PACKET_CODEC, Request::target, Request::new);
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    /** C→S: offer the card in that inventory slot (0-35). */
    public record Select(int slot) implements CustomPayload {
        public static final Id<Select> ID = id("trade_select");
        public static final PacketCodec<RegistryByteBuf, Select> CODEC = PacketCodec.tuple(PacketCodecs.VAR_INT, Select::slot, Select::new);
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    /** C→S: confirm (true) or cancel the trade (false). */
    public record Answer(boolean confirm) implements CustomPayload {
        public static final Id<Answer> ID = id("trade_answer");
        public static final PacketCodec<RegistryByteBuf, Answer> CODEC = PacketCodec.tuple(PacketCodecs.BOOL, Answer::confirm, Answer::new);
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    /** S→C: open the trade screen with that partner. */
    public record Open(String partner) implements CustomPayload {
        public static final Id<Open> ID = id("trade_open");
        public static final PacketCodec<RegistryByteBuf, Open> CODEC = PacketCodec.tuple(PacketCodecs.STRING, Open::partner, Open::new);
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    /** S→C: current state. message = language key ("" if none). */
    public record State(ItemStack mine, ItemStack theirs, boolean myOk, boolean theirOk, String message) implements CustomPayload {
        public static final Id<State> ID = id("trade_state");
        public static final PacketCodec<RegistryByteBuf, State> CODEC = PacketCodec.tuple(
                ItemStack.OPTIONAL_PACKET_CODEC, State::mine, ItemStack.OPTIONAL_PACKET_CODEC, State::theirs,
                PacketCodecs.BOOL, State::myOk, PacketCodecs.BOOL, State::theirOk, PacketCodecs.STRING, State::message, State::new);
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    /** S→C: end of the trade (completed or cancelled). */
    public record Done(ItemStack gave, ItemStack got, boolean success, String message) implements CustomPayload {
        public static final Id<Done> ID = id("trade_done");
        public static final PacketCodec<RegistryByteBuf, Done> CODEC = PacketCodec.tuple(
                ItemStack.OPTIONAL_PACKET_CODEC, Done::gave, ItemStack.OPTIONAL_PACKET_CODEC, Done::got,
                PacketCodecs.BOOL, Done::success, PacketCodecs.STRING, Done::message, Done::new);
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }
}

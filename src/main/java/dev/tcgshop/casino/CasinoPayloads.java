package dev.tcgshop.casino;

import dev.tcgshop.TcgShop;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/**
 * Casino network. Open (server -> client): opens a game screen with the limits and the balance. Act (client ->
 * server): a bet or a decision; the server validates it, settles it and answers with Update (outcome, payout,
 * new balance). The client never decides anything: it only animates what the server already settled.
 */
public final class CasinoPayloads {
    private CasinoPayloads() {}

    public record Open(String game, BlockPos pos, NbtCompound data) implements CustomPayload {
        public static final Id<Open> ID = new Id<>(Identifier.of(TcgShop.MOD_ID, "casino_open"));
        public static final PacketCodec<RegistryByteBuf, Open> CODEC = PacketCodec.tuple(
                PacketCodecs.STRING, Open::game, BlockPos.PACKET_CODEC, Open::pos, PacketCodecs.NBT_COMPOUND, Open::data, Open::new);

        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record Update(String game, NbtCompound data) implements CustomPayload {
        public static final Id<Update> ID = new Id<>(Identifier.of(TcgShop.MOD_ID, "casino_update"));
        public static final PacketCodec<RegistryByteBuf, Update> CODEC = PacketCodec.tuple(
                PacketCodecs.STRING, Update::game, PacketCodecs.NBT_COMPOUND, Update::data, Update::new);

        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record Act(String game, BlockPos pos, NbtCompound data) implements CustomPayload {
        public static final Id<Act> ID = new Id<>(Identifier.of(TcgShop.MOD_ID, "casino_act"));
        public static final PacketCodec<RegistryByteBuf, Act> CODEC = PacketCodec.tuple(
                PacketCodecs.STRING, Act::game, BlockPos.PACKET_CODEC, Act::pos, PacketCodecs.NBT_COMPOUND, Act::data, Act::new);

        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }
}

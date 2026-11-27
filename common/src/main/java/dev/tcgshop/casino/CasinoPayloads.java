package dev.tcgshop.casino;

import dev.tcgshop.TcgShop;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Casino network. Open (server -> client): opens a game screen with the limits and the balance. Act (client ->
 * server): a bet or a decision; the server validates it, settles it and answers with Update (outcome, payout,
 * new balance). The client never decides anything: it only animates what the server already settled.
 */
public final class CasinoPayloads {
    private CasinoPayloads() {}

    public record Open(String game, BlockPos pos, CompoundTag data) implements CustomPacketPayload {
        public static final Type<Open> ID = new Type<>(ResourceLocation.fromNamespaceAndPath(TcgShop.MOD_ID, "casino_open"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Open> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Open::game, BlockPos.STREAM_CODEC, Open::pos, ByteBufCodecs.COMPOUND_TAG, Open::data, Open::new);

        @Override
        public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public record Update(String game, CompoundTag data) implements CustomPacketPayload {
        public static final Type<Update> ID = new Type<>(ResourceLocation.fromNamespaceAndPath(TcgShop.MOD_ID, "casino_update"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Update> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Update::game, ByteBufCodecs.COMPOUND_TAG, Update::data, Update::new);

        @Override
        public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public record Act(String game, BlockPos pos, CompoundTag data) implements CustomPacketPayload {
        public static final Type<Act> ID = new Type<>(ResourceLocation.fromNamespaceAndPath(TcgShop.MOD_ID, "casino_act"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Act> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Act::game, BlockPos.STREAM_CODEC, Act::pos, ByteBufCodecs.COMPOUND_TAG, Act::data, Act::new);

        @Override
        public Type<? extends CustomPacketPayload> type() { return ID; }
    }
}

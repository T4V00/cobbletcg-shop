package dev.tcgshop.network;

import dev.tcgshop.TcgShop;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Generic channel for the new windows (edit a display's card, beggar, black market dealer...):
 * Open (server -> client) opens a window with its data; Act (client -> server) sends what the player chose.
 * The server always validates everything that arrives.
 */
public final class UiPayloads {
    private UiPayloads() {}

    public record Open(String kind, CompoundTag data) implements CustomPacketPayload {
        public static final Type<Open> ID = new Type<>(ResourceLocation.fromNamespaceAndPath(TcgShop.MOD_ID, "ui_open"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Open> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Open::kind, ByteBufCodecs.COMPOUND_TAG, Open::data, Open::new);

        @Override
        public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public record Act(String kind, CompoundTag data) implements CustomPacketPayload {
        public static final Type<Act> ID = new Type<>(ResourceLocation.fromNamespaceAndPath(TcgShop.MOD_ID, "ui_act"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Act> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Act::kind, ByteBufCodecs.COMPOUND_TAG, Act::data, Act::new);

        @Override
        public Type<? extends CustomPacketPayload> type() { return ID; }
    }
}

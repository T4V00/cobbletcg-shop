package dev.tcgshop.network;

import dev.tcgshop.TcgShop;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Generic channel for the new windows (edit a display's card, beggar, black market dealer...):
 * Open (server -> client) opens a window with its data; Act (client -> server) sends what the player chose.
 * The server always validates everything that arrives.
 */
public final class UiPayloads {
    private UiPayloads() {}

    public record Open(String kind, NbtCompound data) implements CustomPayload {
        public static final Id<Open> ID = new Id<>(Identifier.of(TcgShop.MOD_ID, "ui_open"));
        public static final PacketCodec<RegistryByteBuf, Open> CODEC = PacketCodec.tuple(
                PacketCodecs.STRING, Open::kind, PacketCodecs.NBT_COMPOUND, Open::data, Open::new);

        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record Act(String kind, NbtCompound data) implements CustomPayload {
        public static final Id<Act> ID = new Id<>(Identifier.of(TcgShop.MOD_ID, "ui_act"));
        public static final PacketCodec<RegistryByteBuf, Act> CODEC = PacketCodec.tuple(
                PacketCodecs.STRING, Act::kind, PacketCodecs.NBT_COMPOUND, Act::data, Act::new);

        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }
}

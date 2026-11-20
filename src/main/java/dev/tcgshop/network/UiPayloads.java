package dev.tcgshop.network;

import dev.tcgshop.TcgShop;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Canal genérico para las ventanas nuevas (editar carta de un expositor, mendigo, mercader negro...):
 * Open (servidor -> cliente) abre una ventana con sus datos; Act (cliente -> servidor) manda lo que eligió el jugador.
 * El servidor valida siempre todo lo que llega.
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

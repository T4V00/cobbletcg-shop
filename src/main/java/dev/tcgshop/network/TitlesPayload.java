package dev.tcgshop.network;

import dev.tcgshop.TcgShop;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

/** Servidor -> cliente: título elegido por cada jugador conectado (uuid -> "especie|tier"). */
public record TitlesPayload(Map<String, String> titles) implements CustomPayload {
    public static final Id<TitlesPayload> ID = new Id<>(Identifier.of(TcgShop.MOD_ID, "titles"));
    public static final PacketCodec<RegistryByteBuf, TitlesPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.<RegistryByteBuf, String, String, Map<String, String>>map(HashMap::new, PacketCodecs.STRING, PacketCodecs.STRING),
            TitlesPayload::titles, TitlesPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}

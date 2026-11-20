package dev.tcgshop.network;

import dev.tcgshop.TcgShop;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.List;

/**
 * Servidor -> cliente: el grupo está listo para cortarse.
 * Por sobre: id del sobre abierto, tipo (1 normal, 2 semi-dios, 3 dios) y la rareza máxima de su contenido
 * (tier 0-3, para el brillo especial al rasgarlo; -1 = falló).
 */
public record GroupReadyPayload(List<Identifier> ripped, List<Integer> tiers, List<Integer> best) implements CustomPayload {
    public static final Id<GroupReadyPayload> ID = new Id<>(Identifier.of(TcgShop.MOD_ID, "group_ready"));
    public static final PacketCodec<RegistryByteBuf, GroupReadyPayload> CODEC = PacketCodec.tuple(
            Identifier.PACKET_CODEC.collect(PacketCodecs.toList()), GroupReadyPayload::ripped,
            PacketCodecs.VAR_INT.collect(PacketCodecs.toList()), GroupReadyPayload::tiers,
            PacketCodecs.VAR_INT.collect(PacketCodecs.toList()), GroupReadyPayload::best,
            GroupReadyPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}

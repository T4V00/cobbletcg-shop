package dev.tcgshop.network;

import dev.tcgshop.TcgShop;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.List;

/**
 * Server -> client: the group is ready to be cut.
 * Per pack: id of the opened pack, type (1 normal, 2 demi-god, 3 god) and the highest rarity of its contents
 * (tier 0-3, for the special glow when tearing it; -1 = failed).
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

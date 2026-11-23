package dev.tcgshop.network;

import dev.tcgshop.TcgShop;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.List;

/** Client -> server: cut result of each pack in the group (true = badly cut, cards destroyed). Empty list = cancel (delivered undamaged). */
public record GroupCutPayload(List<Boolean> damaged) implements CustomPayload {
    public static final Id<GroupCutPayload> ID = new Id<>(Identifier.of(TcgShop.MOD_ID, "group_cut"));
    public static final PacketCodec<RegistryByteBuf, GroupCutPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.BOOL.collect(PacketCodecs.toList()), GroupCutPayload::damaged, GroupCutPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}

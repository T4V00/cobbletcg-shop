package dev.tcgshop.network;

import dev.tcgshop.TcgShop;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;

import java.util.UUID;

/** Cliente -> servidor (teléfono): contratar o despedir un empleado ("cashier" / "guard") en una tienda. */
public record StaffPayload(UUID zone, String role, boolean hire) implements CustomPayload {
    public static final Id<StaffPayload> ID = new Id<>(Identifier.of(TcgShop.MOD_ID, "staff"));
    public static final PacketCodec<RegistryByteBuf, StaffPayload> CODEC = PacketCodec.tuple(
            Uuids.PACKET_CODEC, StaffPayload::zone, PacketCodecs.STRING, StaffPayload::role, PacketCodecs.BOOL, StaffPayload::hire, StaffPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}

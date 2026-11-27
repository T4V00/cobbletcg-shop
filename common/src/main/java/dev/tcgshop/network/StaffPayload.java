package dev.tcgshop.network;

import dev.tcgshop.TcgShop;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client -> server (phone): hire or fire an employee ("cashier" / "guard") at a shop. */
public record StaffPayload(UUID zone, String role, boolean hire) implements CustomPacketPayload {
    public static final Type<StaffPayload> ID = new Type<>(ResourceLocation.fromNamespaceAndPath(TcgShop.MOD_ID, "staff"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StaffPayload> CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, StaffPayload::zone, ByteBufCodecs.STRING_UTF8, StaffPayload::role, ByteBufCodecs.BOOL, StaffPayload::hire, StaffPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return ID; }
}

package dev.tcgshop.network;

import dev.tcgshop.TcgShop;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client -> server: cut result of each pack in the group (true = badly cut, cards destroyed). Empty list = cancel (delivered undamaged). */
public record GroupCutPayload(List<Boolean> damaged) implements CustomPacketPayload {
    public static final Type<GroupCutPayload> ID = new Type<>(ResourceLocation.fromNamespaceAndPath(TcgShop.MOD_ID, "group_cut"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GroupCutPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL.apply(ByteBufCodecs.list()), GroupCutPayload::damaged, GroupCutPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return ID; }
}

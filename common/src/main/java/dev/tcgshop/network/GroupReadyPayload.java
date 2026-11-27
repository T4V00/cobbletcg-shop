package dev.tcgshop.network;

import dev.tcgshop.TcgShop;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server -> client: the group is ready to be cut.
 * Per pack: id of the opened pack, type (1 normal, 2 demi-god, 3 god) and the highest rarity of its contents
 * (tier 0-3, for the special glow when tearing it; -1 = failed).
 */
public record GroupReadyPayload(List<ResourceLocation> ripped, List<Integer> tiers, List<Integer> best) implements CustomPacketPayload {
    public static final Type<GroupReadyPayload> ID = new Type<>(ResourceLocation.fromNamespaceAndPath(TcgShop.MOD_ID, "group_ready"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GroupReadyPayload> CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list()), GroupReadyPayload::ripped,
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), GroupReadyPayload::tiers,
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), GroupReadyPayload::best,
            GroupReadyPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return ID; }
}

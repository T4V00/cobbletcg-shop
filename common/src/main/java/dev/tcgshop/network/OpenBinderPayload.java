package dev.tcgshop.network;

import dev.tcgshop.TcgShop;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server -> client: opens a binder screen. kind 0 = general, 1 = personal (hand = the hand holding it). */
public record OpenBinderPayload(int kind, int hand, java.util.List<String> unlocked) implements CustomPacketPayload {
    public static final Type<OpenBinderPayload> ID = new Type<>(ResourceLocation.fromNamespaceAndPath(TcgShop.MOD_ID, "open_binder"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenBinderPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, OpenBinderPayload::kind,
            ByteBufCodecs.VAR_INT, OpenBinderPayload::hand,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), OpenBinderPayload::unlocked,
            OpenBinderPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return ID; }
}

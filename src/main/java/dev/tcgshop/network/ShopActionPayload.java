package dev.tcgshop.network;

import dev.tcgshop.TcgShop;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/** Client -> server: buy / change price / withdraw a slot of a shop fixture. */
public record ShopActionPayload(BlockPos pos, int action, int slot, long value) implements CustomPayload {
    public static final int BUY = 0, SET_PRICE = 1, TAKE = 2;
    public static final Id<ShopActionPayload> ID = new Id<>(Identifier.of(TcgShop.MOD_ID, "shop_action"));
    public static final PacketCodec<RegistryByteBuf, ShopActionPayload> CODEC = PacketCodec.tuple(
            BlockPos.PACKET_CODEC, ShopActionPayload::pos,
            PacketCodecs.VAR_INT, ShopActionPayload::action,
            PacketCodecs.VAR_INT, ShopActionPayload::slot,
            PacketCodecs.VAR_LONG, ShopActionPayload::value,
            ShopActionPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}

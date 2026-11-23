package dev.tcgshop.network;

import dev.tcgshop.TcgShop;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.List;

/** Server -> client: contents of a shop fixture (to buy, or to manage it if you are the owner). */
public record ShopViewPayload(BlockPos pos, List<ItemStack> items, List<Long> prices, boolean owner, String ownerName) implements CustomPayload {
    public static final Id<ShopViewPayload> ID = new Id<>(Identifier.of(TcgShop.MOD_ID, "shop_view"));
    public static final PacketCodec<RegistryByteBuf, ShopViewPayload> CODEC = PacketCodec.tuple(
            BlockPos.PACKET_CODEC, ShopViewPayload::pos,
            ItemStack.OPTIONAL_LIST_PACKET_CODEC, ShopViewPayload::items,
            PacketCodecs.VAR_LONG.collect(PacketCodecs.toList()), ShopViewPayload::prices,
            PacketCodecs.BOOL, ShopViewPayload::owner,
            PacketCodecs.STRING, ShopViewPayload::ownerName,
            ShopViewPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}

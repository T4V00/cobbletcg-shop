package dev.tcgshop.network;

import dev.tcgshop.TcgShop;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.List;

/** Servidor -> cliente: cartas de cada sobre del grupo, sus precios y si se destruyeron. toBinder = fueron al Binder General. */
public record GroupRevealPayload(List<List<ItemStack>> cards, List<List<Long>> prices, List<Boolean> damaged, boolean toBinder) implements CustomPayload {
    public static final Id<GroupRevealPayload> ID = new Id<>(Identifier.of(TcgShop.MOD_ID, "group_reveal"));
    public static final PacketCodec<RegistryByteBuf, GroupRevealPayload> CODEC = PacketCodec.tuple(
            ItemStack.LIST_PACKET_CODEC.collect(PacketCodecs.toList()), GroupRevealPayload::cards,
            PacketCodecs.VAR_LONG.collect(PacketCodecs.toList()).collect(PacketCodecs.toList()), GroupRevealPayload::prices,
            PacketCodecs.BOOL.collect(PacketCodecs.toList()), GroupRevealPayload::damaged,
            PacketCodecs.BOOL, GroupRevealPayload::toBinder,
            GroupRevealPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}

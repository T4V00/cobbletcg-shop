package dev.tcgshop.network;

import dev.tcgshop.TcgShop;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * 1.0, client -> server: offer one or more of your cards (inventory slots 0-35 or Binder slots 1000+) for one card of the
 * wandering Collector's stall (index). The server revalidates everything (TraderEntity.offer): the sum of the offered
 * cards' values must reach his acceptance threshold for that card.
 */
public record CollectorOfferPayload(int entity, int index, List<Integer> slots) implements CustomPayload {
    /** Hard cap of slots read from the network (the trade itself allows TraderEntity.MAX_OFFER). */
    public static final int MAX_SLOTS = 32;
    public static final Id<CollectorOfferPayload> ID = new Id<>(Identifier.of(TcgShop.MOD_ID, "collector_offer"));
    public static final PacketCodec<RegistryByteBuf, CollectorOfferPayload> CODEC = new PacketCodec<>() {
        @Override
        public CollectorOfferPayload decode(RegistryByteBuf buf) {
            int e = buf.readVarInt(), idx = buf.readVarInt(), n = buf.readVarInt();
            if (n < 0 || n > MAX_SLOTS) throw new IllegalArgumentException("Too many slots in collector offer: " + n);
            List<Integer> l = new ArrayList<>(n);
            for (int i = 0; i < n; i++) l.add(buf.readVarInt());
            return new CollectorOfferPayload(e, idx, l);
        }

        @Override
        public void encode(RegistryByteBuf buf, CollectorOfferPayload p) {
            buf.writeVarInt(p.entity);
            buf.writeVarInt(p.index);
            int n = Math.min(MAX_SLOTS, p.slots.size());
            buf.writeVarInt(n);
            for (int i = 0; i < n; i++) buf.writeVarInt(p.slots.get(i));
        }
    };

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}

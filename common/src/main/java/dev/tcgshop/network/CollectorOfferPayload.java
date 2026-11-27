package dev.tcgshop.network;

import dev.tcgshop.TcgShop;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 1.0, client -> server: offer one or more of your cards (inventory slots 0-35 or Binder slots 1000+) for one card of the
 * wandering Collector's stall (index). The server revalidates everything (TraderEntity.offer): the sum of the offered
 * cards' values must reach his acceptance threshold for that card.
 * 1.1: several of his cards (indices, up to your phone tier's limit) and an optional charisma talk (-1 = none,
 * else TraderEntity.TALK_*) for an offer a little short of his threshold.
 */
public record CollectorOfferPayload(int entity, List<Integer> indices, List<Integer> slots, int talk) implements CustomPacketPayload {
    /** Hard cap of slots read from the network (the trade itself allows TraderEntity.MAX_OFFER). */
    public static final int MAX_SLOTS = 32;
    public static final Type<CollectorOfferPayload> ID = new Type<>(ResourceLocation.fromNamespaceAndPath(TcgShop.MOD_ID, "collector_offer"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CollectorOfferPayload> CODEC = new StreamCodec<>() {
        @Override
        public CollectorOfferPayload decode(RegistryFriendlyByteBuf buf) {
            int e = buf.readVarInt(), m = buf.readVarInt();
            if (m < 0 || m > 16) throw new IllegalArgumentException("Too many cards picked in collector offer: " + m);
            List<Integer> idx = new ArrayList<>(m);
            for (int i = 0; i < m; i++) idx.add(buf.readVarInt());
            int n = buf.readVarInt();
            if (n < 0 || n > MAX_SLOTS) throw new IllegalArgumentException("Too many slots in collector offer: " + n);
            List<Integer> l = new ArrayList<>(n);
            for (int i = 0; i < n; i++) l.add(buf.readVarInt());
            return new CollectorOfferPayload(e, idx, l, buf.readVarInt());
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, CollectorOfferPayload p) {
            buf.writeVarInt(p.entity);
            int m = Math.min(16, p.indices.size());
            buf.writeVarInt(m);
            for (int i = 0; i < m; i++) buf.writeVarInt(p.indices.get(i));
            int n = Math.min(MAX_SLOTS, p.slots.size());
            buf.writeVarInt(n);
            for (int i = 0; i < n; i++) buf.writeVarInt(p.slots.get(i));
            buf.writeVarInt(p.talk);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() { return ID; }
}

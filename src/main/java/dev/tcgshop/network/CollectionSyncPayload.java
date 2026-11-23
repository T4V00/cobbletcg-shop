package dev.tcgshop.network;

import dev.tcgshop.TcgShop;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Server -> client: contents of the opened General Binder + player data.
 * stats = [cards obtained, packs opened, packs ruined, god packs, cards counted for advancements]
 * titles = available titles ("species|tier"); title = the chosen one.
 */
public record CollectionSyncPayload(Map<String, Integer> counts, List<String> fresh, List<Long> stats, List<String> wishlist,
                                    List<String> titles, String title) implements CustomPayload {
    public static final Id<CollectionSyncPayload> ID = new Id<>(Identifier.of(TcgShop.MOD_ID, "collection_sync"));
    public static final PacketCodec<RegistryByteBuf, CollectionSyncPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.<RegistryByteBuf, String, Integer, Map<String, Integer>>map(HashMap::new, PacketCodecs.STRING, PacketCodecs.VAR_INT),
            CollectionSyncPayload::counts,
            PacketCodecs.STRING.collect(PacketCodecs.toList()), CollectionSyncPayload::fresh,
            PacketCodecs.VAR_LONG.collect(PacketCodecs.toList()), CollectionSyncPayload::stats,
            PacketCodecs.STRING.collect(PacketCodecs.toList()), CollectionSyncPayload::wishlist,
            PacketCodecs.STRING.collect(PacketCodecs.toList()), CollectionSyncPayload::titles,
            PacketCodecs.STRING, CollectionSyncPayload::title,
            CollectionSyncPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}

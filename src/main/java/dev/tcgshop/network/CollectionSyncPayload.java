package dev.tcgshop.network;

import dev.tcgshop.TcgShop;
import net.minecraft.item.ItemStack;
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
 * specials / specialCounts (1.0) = print variants and graded cards filed as full stacks, with how many copies of each.
 * hasGeneral (1.0) = the player has a General Binder of their own (otherwise counts are empty because there is none).
 */
public record CollectionSyncPayload(Map<String, Integer> counts, List<String> fresh, List<Long> stats, List<String> wishlist,
                                    List<String> titles, String title, List<ItemStack> specials, List<Integer> specialCounts,
                                    boolean hasGeneral) implements CustomPayload {
    public static final Id<CollectionSyncPayload> ID = new Id<>(Identifier.of(TcgShop.MOD_ID, "collection_sync"));

    private static final PacketCodec<RegistryByteBuf, Map<String, Integer>> COUNTS =
            PacketCodecs.<RegistryByteBuf, String, Integer, Map<String, Integer>>map(HashMap::new, PacketCodecs.STRING, PacketCodecs.VAR_INT);
    private static final PacketCodec<RegistryByteBuf, List<String>> STRINGS = PacketCodecs.STRING.<RegistryByteBuf>cast().collect(PacketCodecs.toList());
    private static final PacketCodec<RegistryByteBuf, List<Long>> LONGS = PacketCodecs.VAR_LONG.<RegistryByteBuf>cast().collect(PacketCodecs.toList());
    private static final PacketCodec<RegistryByteBuf, List<Integer>> INTS = PacketCodecs.VAR_INT.<RegistryByteBuf>cast().collect(PacketCodecs.toList());
    private static final PacketCodec<RegistryByteBuf, List<ItemStack>> STACKS = ItemStack.PACKET_CODEC.collect(PacketCodecs.toList());

    public static final PacketCodec<RegistryByteBuf, CollectionSyncPayload> CODEC = new PacketCodec<>() {
        @Override
        public CollectionSyncPayload decode(RegistryByteBuf buf) {
            Map<String, Integer> counts = COUNTS.decode(buf);
            List<String> fresh = STRINGS.decode(buf);
            List<Long> stats = LONGS.decode(buf);
            List<String> wishlist = STRINGS.decode(buf);
            List<String> titles = STRINGS.decode(buf);
            String title = PacketCodecs.STRING.decode(buf);
            List<ItemStack> specials = STACKS.decode(buf);
            List<Integer> specialCounts = INTS.decode(buf);
            boolean hasGeneral = buf.readBoolean();
            return new CollectionSyncPayload(counts, fresh, stats, wishlist, titles, title, specials, specialCounts, hasGeneral);
        }

        @Override
        public void encode(RegistryByteBuf buf, CollectionSyncPayload p) {
            COUNTS.encode(buf, p.counts);
            STRINGS.encode(buf, p.fresh);
            LONGS.encode(buf, p.stats);
            STRINGS.encode(buf, p.wishlist);
            STRINGS.encode(buf, p.titles);
            PacketCodecs.STRING.encode(buf, p.title);
            STACKS.encode(buf, p.specials);
            INTS.encode(buf, p.specialCounts);
            buf.writeBoolean(p.hasGeneral);
        }
    };

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}

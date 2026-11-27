package dev.tcgshop.network;

import dev.tcgshop.TcgShop;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Server -> client: contents of the opened General Binder + player data.
 * stats = [cards obtained, packs opened, packs ruined, god packs, cards counted for advancements]
 * titles = available titles ("species|tier"); title = the chosen one.
 * specials / specialCounts (1.0) = print variants and graded cards filed as full stacks, with how many copies of each.
 * hasGeneral (1.0) = the player has a General Binder of their own (otherwise counts are empty because there is none).
 */
public record CollectionSyncPayload(Map<String, Integer> counts, List<String> fresh, List<Long> stats, List<String> wishlist,
                                    List<String> titles, String title, List<ItemStack> specials, List<Integer> specialCounts,
                                    boolean hasGeneral) implements CustomPacketPayload {
    public static final Type<CollectionSyncPayload> ID = new Type<>(ResourceLocation.fromNamespaceAndPath(TcgShop.MOD_ID, "collection_sync"));

    private static final StreamCodec<RegistryFriendlyByteBuf, Map<String, Integer>> COUNTS =
            ByteBufCodecs.<RegistryFriendlyByteBuf, String, Integer, Map<String, Integer>>map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_INT);
    private static final StreamCodec<RegistryFriendlyByteBuf, List<String>> STRINGS = ByteBufCodecs.STRING_UTF8.<RegistryFriendlyByteBuf>cast().apply(ByteBufCodecs.list());
    private static final StreamCodec<RegistryFriendlyByteBuf, List<Long>> LONGS = ByteBufCodecs.VAR_LONG.<RegistryFriendlyByteBuf>cast().apply(ByteBufCodecs.list());
    private static final StreamCodec<RegistryFriendlyByteBuf, List<Integer>> INTS = ByteBufCodecs.VAR_INT.<RegistryFriendlyByteBuf>cast().apply(ByteBufCodecs.list());
    private static final StreamCodec<RegistryFriendlyByteBuf, List<ItemStack>> STACKS = ItemStack.STREAM_CODEC.apply(ByteBufCodecs.list());

    public static final StreamCodec<RegistryFriendlyByteBuf, CollectionSyncPayload> CODEC = new StreamCodec<>() {
        @Override
        public CollectionSyncPayload decode(RegistryFriendlyByteBuf buf) {
            Map<String, Integer> counts = COUNTS.decode(buf);
            List<String> fresh = STRINGS.decode(buf);
            List<Long> stats = LONGS.decode(buf);
            List<String> wishlist = STRINGS.decode(buf);
            List<String> titles = STRINGS.decode(buf);
            String title = ByteBufCodecs.STRING_UTF8.decode(buf);
            List<ItemStack> specials = STACKS.decode(buf);
            List<Integer> specialCounts = INTS.decode(buf);
            boolean hasGeneral = buf.readBoolean();
            return new CollectionSyncPayload(counts, fresh, stats, wishlist, titles, title, specials, specialCounts, hasGeneral);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, CollectionSyncPayload p) {
            COUNTS.encode(buf, p.counts);
            STRINGS.encode(buf, p.fresh);
            LONGS.encode(buf, p.stats);
            STRINGS.encode(buf, p.wishlist);
            STRINGS.encode(buf, p.titles);
            ByteBufCodecs.STRING_UTF8.encode(buf, p.title);
            STACKS.encode(buf, p.specials);
            INTS.encode(buf, p.specialCounts);
            buf.writeBoolean(p.hasGeneral);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() { return ID; }
}

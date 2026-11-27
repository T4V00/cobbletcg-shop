package dev.tcgshop.network;

import dev.tcgshop.TcgShop;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server -> client on join / data reload: price table and market parameters.
 * (~7,700 cards ≈ 300 KB; sent only once, the day change goes in MarketDayPayload).
 */
public record PriceSyncPayload(Map<String, Double> cardsUsd, Map<String, Double> sealedUsd, Map<String, Double> rarityMedianUsd,
                               Map<String, Double> gradedUsd, Market market, String economy) implements CustomPacketPayload {
    public static final Type<PriceSyncPayload> ID = new Type<>(ResourceLocation.fromNamespaceAndPath(TcgShop.MOD_ID, "price_sync"));

    public record Market(double cdPerUsd, long seed, double volatility, long day) {
        static final StreamCodec<RegistryFriendlyByteBuf, Market> CODEC = StreamCodec.composite(
                ByteBufCodecs.DOUBLE, Market::cdPerUsd,
                ByteBufCodecs.VAR_LONG, Market::seed,
                ByteBufCodecs.DOUBLE, Market::volatility,
                ByteBufCodecs.VAR_LONG, Market::day,
                Market::new);
    }

    private static final StreamCodec<RegistryFriendlyByteBuf, Map<String, Double>> MAP =
            ByteBufCodecs.<RegistryFriendlyByteBuf, String, Double, Map<String, Double>>map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.DOUBLE);

    public static final StreamCodec<RegistryFriendlyByteBuf, PriceSyncPayload> CODEC = StreamCodec.composite(
            MAP, PriceSyncPayload::cardsUsd,
            MAP, PriceSyncPayload::sealedUsd,
            MAP, PriceSyncPayload::rarityMedianUsd,
            MAP, PriceSyncPayload::gradedUsd,
            Market.CODEC, PriceSyncPayload::market,
            ByteBufCodecs.stringUtf8(262_144), PriceSyncPayload::economy,
            PriceSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}

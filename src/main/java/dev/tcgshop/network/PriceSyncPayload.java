package dev.tcgshop.network;

import dev.tcgshop.TcgShop;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * Servidor -> cliente al entrar / recargar datos: tabla de precios y parámetros del mercado.
 * (~7.700 cartas ≈ 300 KB; se envía una sola vez, el cambio de día va en MarketDayPayload).
 */
public record PriceSyncPayload(Map<String, Double> cardsUsd, Map<String, Double> sealedUsd, Map<String, Double> rarityMedianUsd,
                               Map<String, Double> gradedUsd, Market market, String economy) implements CustomPayload {
    public static final Id<PriceSyncPayload> ID = new Id<>(Identifier.of(TcgShop.MOD_ID, "price_sync"));

    public record Market(double cdPerUsd, long seed, double volatility, long day) {
        static final PacketCodec<RegistryByteBuf, Market> CODEC = PacketCodec.tuple(
                PacketCodecs.DOUBLE, Market::cdPerUsd,
                PacketCodecs.VAR_LONG, Market::seed,
                PacketCodecs.DOUBLE, Market::volatility,
                PacketCodecs.VAR_LONG, Market::day,
                Market::new);
    }

    private static final PacketCodec<RegistryByteBuf, Map<String, Double>> MAP =
            PacketCodecs.<RegistryByteBuf, String, Double, Map<String, Double>>map(HashMap::new, PacketCodecs.STRING, PacketCodecs.DOUBLE);

    public static final PacketCodec<RegistryByteBuf, PriceSyncPayload> CODEC = PacketCodec.tuple(
            MAP, PriceSyncPayload::cardsUsd,
            MAP, PriceSyncPayload::sealedUsd,
            MAP, PriceSyncPayload::rarityMedianUsd,
            MAP, PriceSyncPayload::gradedUsd,
            Market.CODEC, PriceSyncPayload::market,
            PacketCodecs.string(262_144), PriceSyncPayload::economy,
            PriceSyncPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}

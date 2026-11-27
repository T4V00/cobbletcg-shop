package dev.tcgshop.network;

import dev.tcgshop.TcgShop;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Real TCG packets (twinleaf.gg engine): state and actions travel as compressed JSON. */
public final class PtcgPayloads {
    private PtcgPayloads() {}

    private static final int MAX = 4 * 1024 * 1024;

    static void writeJson(RegistryFriendlyByteBuf b, String s) {
        try {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            try (GZIPOutputStream gz = new GZIPOutputStream(bo)) { gz.write(s.getBytes(StandardCharsets.UTF_8)); }
            b.writeByteArray(bo.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static String readJson(RegistryFriendlyByteBuf b) {
        byte[] data = b.readByteArray(MAX);
        try (GZIPInputStream gz = new GZIPInputStream(new ByteArrayInputStream(data))) {
            return new String(gz.readNBytes(MAX * 4), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Server -> client: match state for that player. open = open the screen if it is closed. */
    public record View(String json, boolean open) implements CustomPacketPayload {
        public static final Type<View> ID = new Type<>(ResourceLocation.fromNamespaceAndPath(TcgShop.MOD_ID, "ptcg_view"));
        public static final StreamCodec<RegistryFriendlyByteBuf, View> CODEC = new StreamCodec<>() {
            @Override
            public View decode(RegistryFriendlyByteBuf b) { return new View(readJson(b), b.readBoolean()); }

            @Override
            public void encode(RegistryFriendlyByteBuf b, View v) {
                writeJson(b, v.json);
                b.writeBoolean(v.open);
            }
        };

        @Override
        public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    /** Client -> server: {"k":"act", "a":{...}} | {"k":"resolve","id":n,"r":...} | {"k":"auto"}. */
    public record Action(String json) implements CustomPacketPayload {
        public static final Type<Action> ID = new Type<>(ResourceLocation.fromNamespaceAndPath(TcgShop.MOD_ID, "ptcg_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Action> CODEC = new StreamCodec<>() {
            @Override
            public Action decode(RegistryFriendlyByteBuf b) { return new Action(readJson(b)); }

            @Override
            public void encode(RegistryFriendlyByteBuf b, Action a) { writeJson(b, a.json); }
        };

        @Override
        public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    /** Server -> client: TCG League (profile, standings, 60-card deck, available cards, energies). */
    public record League(String json) implements CustomPacketPayload {
        public static final Type<League> ID = new Type<>(ResourceLocation.fromNamespaceAndPath(TcgShop.MOD_ID, "ptcg_league"));
        public static final StreamCodec<RegistryFriendlyByteBuf, League> CODEC = new StreamCodec<>() {
            @Override
            public League decode(RegistryFriendlyByteBuf b) { return new League(readJson(b)); }

            @Override
            public void encode(RegistryFriendlyByteBuf b, League a) { writeJson(b, a.json); }
        };

        @Override
        public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    /** Client -> server: {"a":"sync"|"save"|"auto", "deck":[...]} */
    public record LeagueAction(String json) implements CustomPacketPayload {
        public static final Type<LeagueAction> ID = new Type<>(ResourceLocation.fromNamespaceAndPath(TcgShop.MOD_ID, "ptcg_league_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, LeagueAction> CODEC = new StreamCodec<>() {
            @Override
            public LeagueAction decode(RegistryFriendlyByteBuf b) { return new LeagueAction(readJson(b)); }

            @Override
            public void encode(RegistryFriendlyByteBuf b, LeagueAction a) { writeJson(b, a.json); }
        };

        @Override
        public Type<? extends CustomPacketPayload> type() { return ID; }
    }
}

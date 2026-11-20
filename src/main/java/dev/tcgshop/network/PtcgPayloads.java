package dev.tcgshop.network;

import dev.tcgshop.TcgShop;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** Paquetes del TCG real (motor de twinleaf.gg): el estado y las acciones viajan como JSON comprimido. */
public final class PtcgPayloads {
    private PtcgPayloads() {}

    private static final int MAX = 4 * 1024 * 1024;

    static void writeJson(RegistryByteBuf b, String s) {
        try {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            try (GZIPOutputStream gz = new GZIPOutputStream(bo)) { gz.write(s.getBytes(StandardCharsets.UTF_8)); }
            b.writeByteArray(bo.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static String readJson(RegistryByteBuf b) {
        byte[] data = b.readByteArray(MAX);
        try (GZIPInputStream gz = new GZIPInputStream(new ByteArrayInputStream(data))) {
            return new String(gz.readNBytes(MAX * 4), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Servidor -> cliente: estado de la partida para ese jugador. open = abrir la pantalla si está cerrada. */
    public record View(String json, boolean open) implements CustomPayload {
        public static final Id<View> ID = new Id<>(Identifier.of(TcgShop.MOD_ID, "ptcg_view"));
        public static final PacketCodec<RegistryByteBuf, View> CODEC = new PacketCodec<>() {
            @Override
            public View decode(RegistryByteBuf b) { return new View(readJson(b), b.readBoolean()); }

            @Override
            public void encode(RegistryByteBuf b, View v) {
                writeJson(b, v.json);
                b.writeBoolean(v.open);
            }
        };

        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }

    /** Cliente -> servidor: {"k":"act", "a":{...}} | {"k":"resolve","id":n,"r":...} | {"k":"auto"}. */
    public record Action(String json) implements CustomPayload {
        public static final Id<Action> ID = new Id<>(Identifier.of(TcgShop.MOD_ID, "ptcg_action"));
        public static final PacketCodec<RegistryByteBuf, Action> CODEC = new PacketCodec<>() {
            @Override
            public Action decode(RegistryByteBuf b) { return new Action(readJson(b)); }

            @Override
            public void encode(RegistryByteBuf b, Action a) { writeJson(b, a.json); }
        };

        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }

    /** Servidor -> cliente: Liga TCG (ficha, clasificación, mazo de 60, cartas disponibles, energías). */
    public record League(String json) implements CustomPayload {
        public static final Id<League> ID = new Id<>(Identifier.of(TcgShop.MOD_ID, "ptcg_league"));
        public static final PacketCodec<RegistryByteBuf, League> CODEC = new PacketCodec<>() {
            @Override
            public League decode(RegistryByteBuf b) { return new League(readJson(b)); }

            @Override
            public void encode(RegistryByteBuf b, League a) { writeJson(b, a.json); }
        };

        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }

    /** Cliente -> servidor: {"a":"sync"|"save"|"auto", "deck":[...]} */
    public record LeagueAction(String json) implements CustomPayload {
        public static final Id<LeagueAction> ID = new Id<>(Identifier.of(TcgShop.MOD_ID, "ptcg_league_action"));
        public static final PacketCodec<RegistryByteBuf, LeagueAction> CODEC = new PacketCodec<>() {
            @Override
            public LeagueAction decode(RegistryByteBuf b) { return new LeagueAction(readJson(b)); }

            @Override
            public void encode(RegistryByteBuf b, LeagueAction a) { writeJson(b, a.json); }
        };

        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }
}

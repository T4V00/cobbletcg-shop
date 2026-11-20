package dev.tcgshop.client;

import net.minecraft.nbt.NbtCompound;

/** Datos sociales y de gestión recibidos del servidor (amigos, expositores) para las apps del teléfono. */
public final class ClientSocial {
    private ClientSocial() {}

    public static NbtCompound friends = new NbtCompound();
    public static NbtCompound displays = new NbtCompound();
    public static String selectedFriend = "";

    public static void act(String kind, String action) {
        NbtCompound d = new NbtCompound();
        d.putString("a", action);
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new dev.tcgshop.network.UiPayloads.Act(kind, d));
    }

    public static void act(String kind, NbtCompound d) {
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new dev.tcgshop.network.UiPayloads.Act(kind, d));
    }
}

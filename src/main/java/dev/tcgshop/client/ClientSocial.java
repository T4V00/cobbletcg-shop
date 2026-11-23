package dev.tcgshop.client;

import net.minecraft.nbt.NbtCompound;

/** Social and management data received from the server (friends, displays) for the phone apps. */
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

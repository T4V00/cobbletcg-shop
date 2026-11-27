package dev.tcgshop.client;

import dev.masterball.api.platform.Platform;
import dev.masterball.api.platform.ClientPlatform;
import net.minecraft.nbt.CompoundTag;

/** Social and management data received from the server (friends, displays) for the phone apps. */
public final class ClientSocial {
    private ClientSocial() {}

    public static CompoundTag friends = new CompoundTag();
    public static CompoundTag displays = new CompoundTag();
    public static String selectedFriend = "";

    public static void act(String kind, String action) {
        CompoundTag d = new CompoundTag();
        d.putString("a", action);
        ClientPlatform.INSTANCE.send(new dev.tcgshop.network.UiPayloads.Act(kind, d));
    }

    public static void act(String kind, CompoundTag d) {
        ClientPlatform.INSTANCE.send(new dev.tcgshop.network.UiPayloads.Act(kind, d));
    }
}

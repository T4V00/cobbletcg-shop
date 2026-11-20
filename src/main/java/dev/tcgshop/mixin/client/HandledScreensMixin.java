package dev.tcgshop.mixin.client;

import cobbletcg.procedures.AlbumScreenHandler;
import dev.tcgshop.client.binder.PaniniAlbumScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Sustituye la pantalla de los álbumes de cobbletcg por nuestra pantalla estilo Panini (mismo ScreenHandler). */
@Mixin(HandledScreens.class)
public abstract class HandledScreensMixin {
    private static final Identifier ALBUM = Identifier.of("cobbletcg", "album_screen_handler");

    @Inject(method = "open", at = @At("HEAD"), cancellable = true)
    private static <T extends ScreenHandler> void tcgshop$panini(ScreenHandlerType<T> type, MinecraftClient client, int id, Text title, CallbackInfo ci) {
        if (type == null || client.player == null || !ALBUM.equals(Registries.SCREEN_HANDLER.getId(type))) return;
        T handler = type.create(id, client.player.getInventory());
        if (!(handler instanceof AlbumScreenHandler album)) return;
        client.player.currentScreenHandler = album;
        client.setScreen(new PaniniAlbumScreen(album, title));
        ci.cancel();
    }
}

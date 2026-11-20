package dev.tcgshop.mixin;

import dev.tcgshop.advancement.CardAdvancements;
import dev.tcgshop.card.CardInfo;
import dev.tcgshop.collection.CollectionService;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

/** Al abrir un álbum de set de cobbletcg, sus cartas cuentan para los logros (como cualquier binder). */
@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerEntityMixin {
    private static Field tcgshop$albumInventory;

    @Inject(method = "openHandledScreen", at = @At("RETURN"))
    private void tcgshop$creditAlbum(NamedScreenHandlerFactory factory, CallbackInfoReturnable<OptionalInt> cir) {
        ServerPlayerEntity self = (ServerPlayerEntity) (Object) this;
        ScreenHandler h = self.currentScreenHandler;
        if (h == null || !h.getClass().getName().equals("cobbletcg.procedures.AlbumScreenHandler")) return;
        try {
            if (tcgshop$albumInventory == null) {
                tcgshop$albumInventory = h.getClass().getDeclaredField("inventory");
                tcgshop$albumInventory.setAccessible(true);
            }
            Inventory inv = (Inventory) tcgshop$albumInventory.get(h);
            List<String> ids = new ArrayList<>();
            for (int i = 0; i < inv.size(); i++) {
                ItemStack st = inv.getStack(i);
                if (CardInfo.isCard(st)) ids.add(CardInfo.id(st).toString());
            }
            CardAdvancements.credit(self, CollectionService.playerData(self), null, ids);
        } catch (Exception ignored) {
        }
    }
}

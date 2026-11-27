package dev.tcgshop.neoforge;

import dev.masterball.api.neoforge.MasterballApiNeoForge;
import dev.tcgshop.TcgShop;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * NeoForge entry point. The shared init registers blocks, items and entities with vanilla Registry.register, which
 * NeoForge only allows while its registries are unfrozen (the RegisterEvent window), and it must run after the API's
 * init (MasterballApiNeoForge.afterInit).
 *
 * <p>It also builds the entities' AttributeSupplier.Builders, and NeoForge's patched
 * LivingEntity.createLivingAttributes() reads NeoForge's own attributes (neoforge:swim_speed...). Those are only in
 * the attribute registry once the attribute RegisterEvent (the FIRST one, which is also when the API's init runs)
 * has been fully dispatched. So the init runs from the first RegisterEvent in which they exist (normally the second
 * one, data_component_type): every registry is still unfrozen for the whole window.
 */
@Mod(TcgShop.MOD_ID)
public final class TcgShopNeoForge {
    private static final ResourceLocation NEOFORGE_ATTRIBUTE = ResourceLocation.fromNamespaceAndPath("neoforge", "swim_speed");
    private static boolean started;

    public TcgShopNeoForge(IEventBus modBus, ModContainer container) {
        modBus.addListener(RegisterEvent.class, TcgShopNeoForge::onRegister);
    }

    private static void onRegister(RegisterEvent event) {
        if (started || !BuiltInRegistries.ATTRIBUTE.containsKey(NEOFORGE_ATTRIBUTE)) return;
        started = true;
        TcgShop.LOGGER.debug("Running the shared init from RegisterEvent {}", event.getRegistryKey().location());
        MasterballApiNeoForge.afterInit(TcgShop::init);
    }
}

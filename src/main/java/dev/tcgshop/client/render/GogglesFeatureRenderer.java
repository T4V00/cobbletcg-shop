package dev.tcgshop.client.render;

import dev.tcgshop.TcgShop;
import dev.tcgshop.compat.AccessoriesCompat;
import dev.tcgshop.item.TcgItems;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.feature.HeadFeatureRenderer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.model.ModelWithHead;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.ZombieVillagerEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

/**
 * Manager Goggles worn in the Accessories "face" slot.
 *
 * <p>In the vanilla head slot the goggles are drawn by Minecraft's HeadFeatureRenderer with the item model's
 * "head" display transform (models/item/manager_goggles.json), which puts them on the eyes. Accessories' default
 * renderer uses a different transform (FIXED mode on the face plane: flush with the skin, behind the hat layer and
 * facing into the head), so for the face slot we turn it off and draw the goggles here with exactly the same
 * transform as the head slot. They follow head yaw/pitch, sneaking and baby scale because the matrices come from
 * the model's head part, like vanilla helmets.</p>
 */
public class GogglesFeatureRenderer<T extends LivingEntity, M extends EntityModel<T> & ModelWithHead> extends FeatureRenderer<T, M> {
    /** When something else is on the head (helmet, hat), the goggles grow a bit so they sit over it instead of inside it. */
    private static final float OVER_HELMET_SCALE = 1.15f;

    private final HeldItemRenderer heldItemRenderer;

    public GogglesFeatureRenderer(FeatureRendererContext<T, M> context, HeldItemRenderer heldItemRenderer) {
        super(context);
        this.heldItemRenderer = heldItemRenderer;
    }

    /** Client init: player feature renderer + no default Accessories render for the goggles. */
    public static void register() {
        LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, helper, context) -> {
            if (entityRenderer instanceof PlayerEntityRenderer player)
                helper.register(new GogglesFeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>>(
                        player, context.getHeldItemRenderer()));
        });
        if (FabricLoader.getInstance().isModLoaded("accessories")) {
            try {
                Class<?> registry = Class.forName("io.wispforest.accessories.api.client.AccessoriesRendererRegistry");
                registry.getMethod("registerNoRenderer", Item.class).invoke(null, TcgItems.MANAGER_GOGGLES);
            } catch (Throwable t) {
                TcgShop.LOGGER.warn("Could not replace the Accessories renderer of the Manager Goggles", t);
            }
        }
    }

    private static Method capGetOptionally, capGetEquipped, entryReference, entryStack, refSlotContainer, refSlot, containerShouldRender;
    private static boolean reflectionTried;

    /**
     * First face-slot goggles the owner has not hidden with Accessories' per-slot visibility toggle (the default
     * Accessories renderer respects it, so ours must too). Falls back to any equipped goggles if that API is missing.
     */
    private static ItemStack visibleFaceGoggles(LivingEntity entity) {
        if (!FabricLoader.getInstance().isModLoaded("accessories")) return ItemStack.EMPTY;
        if (!reflectionTried) {
            reflectionTried = true;
            try {
                Class<?> cap = Class.forName("io.wispforest.accessories.api.AccessoriesCapability");
                Class<?> entry = Class.forName("io.wispforest.accessories.api.slot.SlotEntryReference");
                Class<?> ref = Class.forName("io.wispforest.accessories.api.slot.SlotReference");
                Class<?> container = Class.forName("io.wispforest.accessories.api.AccessoriesContainer");
                capGetOptionally = cap.getMethod("getOptionally", LivingEntity.class);
                capGetEquipped = cap.getMethod("getEquipped", Item.class);
                entryReference = entry.getMethod("reference");
                entryStack = entry.getMethod("stack");
                refSlotContainer = ref.getMethod("slotContainer");
                refSlot = ref.getMethod("slot");
                containerShouldRender = container.getMethod("shouldRender", int.class);
            } catch (Throwable t) {
                capGetOptionally = null;
            }
        }
        if (capGetOptionally == null) {
            List<ItemStack> worn = AccessoriesCompat.equipped(entity, TcgItems.MANAGER_GOGGLES);
            return worn.isEmpty() || worn.get(0) == null ? ItemStack.EMPTY : worn.get(0);
        }
        try {
            Optional<?> cap = (Optional<?>) capGetOptionally.invoke(null, entity);
            if (cap == null || cap.isEmpty()) return ItemStack.EMPTY;
            for (Object e : (List<?>) capGetEquipped.invoke(cap.get(), TcgItems.MANAGER_GOGGLES)) {
                ItemStack s = (ItemStack) entryStack.invoke(e);
                if (s == null || s.isEmpty()) continue;
                boolean show = true;
                try {
                    Object ref = entryReference.invoke(e);
                    Object slots = ref == null ? null : refSlotContainer.invoke(ref);
                    if (slots != null) show = (Boolean) containerShouldRender.invoke(slots, (Integer) refSlot.invoke(ref));
                } catch (Throwable ignored) {
                    // unknown layout (nested slot...): draw it
                }
                if (show) return s;
            }
        } catch (Throwable ignored) {
        }
        return ItemStack.EMPTY;
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, T entity, float limbAngle, float limbDistance,
                       float tickDelta, float animationProgress, float headYaw, float headPitch) {
        ItemStack head = entity.getEquippedStack(EquipmentSlot.HEAD);
        if (head.isOf(TcgItems.MANAGER_GOGGLES)) return; // already drawn by the vanilla head layer
        ItemStack stack = visibleFaceGoggles(entity);
        if (stack.isEmpty()) return;
        matrices.push();
        // same steps as HeadFeatureRenderer.render
        if (entity.isBaby() && !(entity instanceof VillagerEntity)) {
            matrices.translate(0.0F, 0.03125F, 0.0F);
            matrices.scale(0.7F, 0.7F, 0.7F);
            matrices.translate(0.0F, 1.0F, 0.0F);
        }
        getContextModel().getHead().rotate(matrices);
        HeadFeatureRenderer.translate(matrices, entity instanceof VillagerEntity || entity instanceof ZombieVillagerEntity);
        // the origin is now the center of the head: scaling here keeps the goggles centered on the eyes
        if (!head.isEmpty()) matrices.scale(OVER_HELMET_SCALE, OVER_HELMET_SCALE, OVER_HELMET_SCALE);
        heldItemRenderer.renderItem(entity, stack, ModelTransformationMode.HEAD, false, matrices, vertexConsumers, light);
        matrices.pop();
    }
}

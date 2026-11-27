package dev.tcgshop.item;

import dev.tcgshop.entity.CustomerEntity;
import dev.tcgshop.shop.ShopZones;
import org.joml.Vector3f;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Deodorant spray: a cloud of air freshener in front of you. Inside your shop it lowers the bad smell and, if you spray a
 * customer who smells bad, they stop smelling (and thank you for it... more or less). Comes with 72 uses.
 */
public class DeodorantSprayItem extends Item {
    public static final int SMELL_REMOVED = 20;

    public DeodorantSprayItem(Properties settings) { super(settings); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);
        user.getCooldowns().addCooldown(this, 12);
        world.playSound(null, user.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.5f, 1.8f);
        if (world instanceof ServerLevel sw) {
            Vec3 eye = user.getEyePosition(), look = user.getViewVector(1f);
            DustParticleOptions mist = new DustParticleOptions(new Vector3f(0.75f, 0.95f, 1f), 1.4f);
            for (int i = 1; i <= 10; i++) {
                Vec3 p = eye.add(look.scale(i * 0.35));
                sw.sendParticles(mist, p.x, p.y - 0.1, p.z, 3, 0.08 * i, 0.08 * i, 0.08 * i, 0);
                if (i % 3 == 0) sw.sendParticles(ParticleTypes.CLOUD, p.x, p.y - 0.1, p.z, 1, 0.1, 0.1, 0.1, 0.01);
            }
            ShopZones.Zone z = ShopZones.get(sw).at(user.blockPosition());
            if (z != null && z.smell > 0) {
                z.smell = Math.max(0, z.smell - SMELL_REMOVED);
                ShopZones.get(sw).setDirty();
                user.displayClientMessage(Component.translatable("tcgshop.smell.sprayed", z.smell).withStyle(ChatFormatting.AQUA), true);
            }
            // smelly customers in the spray cone
            List<CustomerEntity> near = sw.getEntitiesOfClass(CustomerEntity.class, user.getBoundingBox().inflate(4.5), c -> c.isSmelly());
            for (CustomerEntity c : near) {
                Vec3 to = c.position().add(0, 1, 0).subtract(eye);
                if (to.normalize().dot(look) > 0.75) c.sprayed(user);
            }
            stack.hurtAndBreak(1, user, hand == InteractionHand.MAIN_HAND ? net.minecraft.world.entity.EquipmentSlot.MAINHAND : net.minecraft.world.entity.EquipmentSlot.OFFHAND);
        }
        return InteractionResultHolder.sidedSuccess(stack, world.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag type) {
        tooltip.add(Component.translatable("item.tcgshop.deodorant_spray.tip").withStyle(ChatFormatting.GRAY));
        // remaining uses (72 by default: triple the previous amount)
        int max = stack.getMaxDamage();
        if (max > 0) tooltip.add(Component.translatable("item.tcgshop.deodorant_spray.uses", max - stack.getDamageValue(), max).withStyle(ChatFormatting.DARK_AQUA));
    }
}

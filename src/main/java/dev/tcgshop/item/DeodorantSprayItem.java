package dev.tcgshop.item;

import dev.tcgshop.entity.CustomerEntity;
import dev.tcgshop.shop.ShopZones;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3f;

import java.util.List;

/**
 * Deodorant spray: a cloud of air freshener in front of you. Inside your shop it lowers the bad smell and, if you spray a
 * customer who smells bad, they stop smelling (and thank you for it... more or less). Comes with 72 uses.
 */
public class DeodorantSprayItem extends Item {
    public static final int SMELL_REMOVED = 20;

    public DeodorantSprayItem(Settings settings) { super(settings); }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        user.getItemCooldownManager().set(this, 12);
        world.playSound(null, user.getBlockPos(), SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 0.5f, 1.8f);
        if (world instanceof ServerWorld sw) {
            Vec3d eye = user.getEyePos(), look = user.getRotationVec(1f);
            DustParticleEffect mist = new DustParticleEffect(new Vector3f(0.75f, 0.95f, 1f), 1.4f);
            for (int i = 1; i <= 10; i++) {
                Vec3d p = eye.add(look.multiply(i * 0.35));
                sw.spawnParticles(mist, p.x, p.y - 0.1, p.z, 3, 0.08 * i, 0.08 * i, 0.08 * i, 0);
                if (i % 3 == 0) sw.spawnParticles(ParticleTypes.CLOUD, p.x, p.y - 0.1, p.z, 1, 0.1, 0.1, 0.1, 0.01);
            }
            ShopZones.Zone z = ShopZones.get(sw).at(user.getBlockPos());
            if (z != null && z.smell > 0) {
                z.smell = Math.max(0, z.smell - SMELL_REMOVED);
                ShopZones.get(sw).markDirty();
                user.sendMessage(Text.translatable("tcgshop.smell.sprayed", z.smell).formatted(Formatting.AQUA), true);
            }
            // smelly customers in the spray cone
            List<CustomerEntity> near = sw.getEntitiesByClass(CustomerEntity.class, user.getBoundingBox().expand(4.5), c -> c.isSmelly());
            for (CustomerEntity c : near) {
                Vec3d to = c.getPos().add(0, 1, 0).subtract(eye);
                if (to.normalize().dotProduct(look) > 0.75) c.sprayed(user);
            }
            stack.damage(1, user, hand == Hand.MAIN_HAND ? net.minecraft.entity.EquipmentSlot.MAINHAND : net.minecraft.entity.EquipmentSlot.OFFHAND);
        }
        return TypedActionResult.success(stack, world.isClient);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("item.tcgshop.deodorant_spray.tip").formatted(Formatting.GRAY));
        // remaining uses (72 by default: triple the previous amount)
        int max = stack.getMaxDamage();
        if (max > 0) tooltip.add(Text.translatable("item.tcgshop.deodorant_spray.uses", max - stack.getDamage(), max).formatted(Formatting.DARK_AQUA));
    }
}

package dev.tcgshop.mixin;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Access to mob targets so that Minecraft monsters also attack the Scalpers. */
@Mixin(Mob.class)
public interface MobEntityAccessor {
    @Accessor("targetSelector")
    GoalSelector tcgshop$targetSelector();
}

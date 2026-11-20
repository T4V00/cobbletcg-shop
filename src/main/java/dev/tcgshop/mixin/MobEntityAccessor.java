package dev.tcgshop.mixin;

import net.minecraft.entity.ai.goal.GoalSelector;
import net.minecraft.entity.mob.MobEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Acceso a los objetivos de los mobs para que los monstruos de Minecraft también ataquen a los Scalpers. */
@Mixin(MobEntity.class)
public interface MobEntityAccessor {
    @Accessor("targetSelector")
    GoalSelector tcgshop$targetSelector();
}

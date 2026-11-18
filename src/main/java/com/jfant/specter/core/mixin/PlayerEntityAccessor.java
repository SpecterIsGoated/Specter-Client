package com.jfant.specter.core.mixin;

import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin({Player.class})
public interface PlayerEntityAccessor {
   @Invoker("canPlayerFitWithinBlocksAndEntitiesWhen")
   boolean meteor$canChangeIntoPose(Pose var1);
}

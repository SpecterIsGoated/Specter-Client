package com.jfant.specter.mixin;

import com.jfant.specter.modules.SwingSpeed;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin({LivingEntity.class})
public abstract class ArmSwingLengthMixin {
   @ModifyReturnValue(
      method = {"getCurrentSwingDuration"},
      at = {@At("RETURN")}
   )
   private int specter$swingTicks(int ticks) {
      return (Object)this == Minecraft.getInstance().player ? SwingSpeed.swingTicks(ticks) : ticks;
   }
}

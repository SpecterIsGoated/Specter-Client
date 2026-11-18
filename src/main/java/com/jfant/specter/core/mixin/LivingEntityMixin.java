package com.jfant.specter.core.mixin;

import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.events.entity.player.CanWalkOnFluidEvent;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin({LivingEntity.class})
public abstract class LivingEntityMixin extends Entity {
   public LivingEntityMixin(EntityType<?> type, Level world) {
      super(type, world);
   }

   @ModifyReturnValue(
      method = {"canStandOnFluid"},
      at = {@At("RETURN")}
   )
   private boolean onCanWalkOnFluid(boolean original, FluidState fluidState) {
      if (this != SpecterCore.mc.player) {
         return original;
      } else {
         CanWalkOnFluidEvent event = (CanWalkOnFluidEvent)SpecterCore.EVENT_BUS.post(CanWalkOnFluidEvent.get(fluidState));
         return event.walkOnFluid;
      }
   }
}

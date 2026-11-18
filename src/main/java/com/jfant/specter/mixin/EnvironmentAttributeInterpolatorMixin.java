package com.jfant.specter.mixin;

import com.jfant.specter.modules.Atmosphere;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.EnvironmentAttributeProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({EnvironmentAttributeProbe.class})
public abstract class EnvironmentAttributeInterpolatorMixin {
   @Inject(
      method = {"getValue"},
      at = {@At("RETURN")},
      cancellable = true
   )
   private <V> void specter$atmosphere(EnvironmentAttribute<V> attribute, float tickProgress, CallbackInfoReturnable<V> cir) {
      if (Atmosphere.on()) {
         cir.setReturnValue(Atmosphere.override(attribute, (V)cir.getReturnValue()));
      }
   }
}

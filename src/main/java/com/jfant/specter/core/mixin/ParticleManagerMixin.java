package com.jfant.specter.core.mixin;

import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.events.world.ParticleEvent;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({ParticleEngine.class})
public abstract class ParticleManagerMixin {
   @Shadow
   @Nullable
   protected abstract <T extends ParticleOptions> Particle makeParticle(T var1, double var2, double var4, double var6, double var8, double var10, double var12);

   @Inject(
      method = {"createParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)Lnet/minecraft/client/particle/Particle;"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onAddParticle(
      ParticleOptions parameters, double x, double y, double z, double velocityX, double velocityY, double velocityZ, CallbackInfoReturnable<Particle> info
   ) {
      ParticleEvent event = (ParticleEvent)SpecterCore.EVENT_BUS.post(ParticleEvent.get(parameters));
      if (event.isCancelled()) {
         if (parameters.getType() == ParticleTypes.FLASH) {
            info.setReturnValue(this.makeParticle(parameters, x, y, z, velocityX, velocityY, velocityZ));
         } else {
            info.cancel();
         }
      }
   }
}

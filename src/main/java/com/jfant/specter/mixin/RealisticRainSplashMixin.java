package com.jfant.specter.mixin;

import com.jfant.specter.modules.RealisticRain;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Realistic Rain: fewer weather splashes, and the ones left are made white (see RealisticRainDropMixin). */
@Mixin(ClientLevel.class)
public abstract class RealisticRainSplashMixin {
   @WrapOperation(
      method = "tickWeatherEffects",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V")
   )
   private void specter$rainSplash(
      ClientLevel level, ParticleOptions type, double x, double y, double z, double xd, double yd, double zd, Operation<Void> original
   ) {
      if (type != ParticleTypes.RAIN) {
         original.call(level, type, x, y, z, xd, yd, zd);
         return;
      }

      int mode = RealisticRain.splashMode(level.getRandom().nextFloat());
      if (mode < 0) {
         return;
      }

      RealisticRain.spawningWhiteSplash = mode == 1;
      try {
         original.call(level, type, x, y, z, xd, yd, zd);
      } finally {
         RealisticRain.spawningWhiteSplash = false;
      }
   }
}

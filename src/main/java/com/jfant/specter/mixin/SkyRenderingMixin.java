package com.jfant.specter.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SkyRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({SkyRenderer.class})
public abstract class SkyRenderingMixin {
   @Inject(
      method = {"renderSkyDisc"},
      at = {@At("TAIL")}
   )
   private void specter$sky(int color, CallbackInfo ci) {
      com.jfant.specter.sky.SkyRenderer.drawSky();
   }

   @Inject(
      method = {"renderSunriseAndSunset"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void specter$sunsetGlow(PoseStack matrices, float solarAngle, int color, CallbackInfo ci) {
      if (com.jfant.specter.sky.SkyRenderer.replacesSunsetGlow()) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"renderStars"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void specter$stars(float brightness, PoseStack matrices, CallbackInfo ci) {
      if (com.jfant.specter.sky.SkyRenderer.replacesStars()) {
         ci.cancel();
      }
   }
}

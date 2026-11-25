package com.jfant.specter.mixin;

import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.client.renderer.SkyRenderer;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// 26.x: the vanilla sky steps all take the open sky RenderPass first.
@Mixin({SkyRenderer.class})
public abstract class SkyRenderingMixin {
   @Inject(
      method = {"render"},
      at = {@At("HEAD")}
   )
   private void specter$prepareSky(GpuBufferSlice skyFog, SkyRenderState state, CallbackInfo ci) {
      com.jfant.specter.sky.SkyboxRenderer.frame();
      com.jfant.specter.sky.SkyRenderer.prepareSky();
   }

   @Inject(
      method = {"renderSkyDisc"},
      at = {@At("TAIL")}
   )
   private void specter$sky(RenderPass pass, Vector3fc skyColor, CallbackInfo ci) {
      com.jfant.specter.sky.SkyRenderer.drawSky(pass);
   }

   @Inject(
      method = {"renderSunriseAndSunset"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void specter$sunsetGlow(CallbackInfo ci) {
      if (com.jfant.specter.sky.SkyRenderer.replacesSunsetGlow()) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"renderStars"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void specter$stars(CallbackInfo ci) {
      if (com.jfant.specter.sky.SkyRenderer.replacesStars()) {
         ci.cancel();
      }
   }
}

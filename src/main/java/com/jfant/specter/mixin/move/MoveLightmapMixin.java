package com.jfant.specter.mixin.move;

import com.jfant.specter.move.Brightness;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// 26.x: the lightmap uniforms are now filled from LightmapRenderState by LightmapRenderStateExtractor,
// so Specter's night vision / gamma boost is applied to the extracted state instead of the Std140 writes.
@Mixin(LightmapRenderStateExtractor.class)
public abstract class MoveLightmapMixin {
   @Shadow
   private boolean needsUpdate;

   @Inject(method = "extract", at = @At("HEAD"))
   private void specter$redrawWhileFading(LightmapRenderState state, float partialTicks, CallbackInfo ci) {
      if (Brightness.fading()) {
         this.needsUpdate = true;
      }
   }

   @Inject(method = "extract", at = @At("TAIL"))
   private void specter$boost(LightmapRenderState state, float partialTicks, CallbackInfo ci) {
      float ours = Brightness.nightVision();
      if (ours > state.nightVisionEffectIntensity) {
         state.nightVisionEffectIntensity = ours;
      }

      state.brightness += Brightness.gamma();
   }
}

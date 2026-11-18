package com.jfant.specter.mixin.move;

import com.jfant.specter.move.Brightness;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({LightTexture.class})
public abstract class MoveLightmapMixin {
   private static final String PUT_FLOAT = "Lcom/mojang/blaze3d/buffers/Std140Builder;putFloat(F)Lcom/mojang/blaze3d/buffers/Std140Builder;";
   @Shadow
   private boolean updateLightTexture;

   @Inject(
      method = {"updateLightTexture"},
      at = {@At("HEAD")}
   )
   private void specter$redrawWhileFading(float tickProgress, CallbackInfo ci) {
      if (Brightness.fading()) {
         this.updateLightTexture = true;
      }
   }

   @ModifyArg(
      method = {"updateLightTexture"},
      at = @At(
         value = "INVOKE",
         target = "Lcom/mojang/blaze3d/buffers/Std140Builder;putFloat(F)Lcom/mojang/blaze3d/buffers/Std140Builder;",
         ordinal = 3
      ),
      index = 0
   )
   private float specter$nightVision(float own) {
      float ours = Brightness.nightVision();
      return ours > own ? ours : own;
   }

   @ModifyArg(
      method = {"updateLightTexture"},
      at = @At(
         value = "INVOKE",
         target = "Lcom/mojang/blaze3d/buffers/Std140Builder;putFloat(F)Lcom/mojang/blaze3d/buffers/Std140Builder;",
         ordinal = 6
      ),
      index = 0
   )
   private float specter$gamma(float own) {
      return own + Brightness.gamma();
   }
}

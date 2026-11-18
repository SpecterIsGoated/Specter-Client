package com.jfant.specter.mixin;

import com.jfant.specter.fx.WorldFx;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(
   value = {GameRenderer.class},
   priority = 900
)
public abstract class WorldFxMixin {
   @Inject(
      method = {"renderLevel"},
      at = {@At(
         value = "INVOKE_STRING",
         target = "Lnet/minecraft/util/profiling/ProfilerFiller;popPush(Ljava/lang/String;)V",
         args = {"ldc=hand"}
      )}
   )
   private void specter$worldFx(
      DeltaTracker tickCounter,
      CallbackInfo ci,
      @Local(ordinal = 0) Matrix4f projection,
      @Local(ordinal = 1) Matrix4f position,
      @Local(ordinal = 0) float tickDelta
   ) {
      WorldFx.afterWorld(projection, position, tickDelta);
   }
}

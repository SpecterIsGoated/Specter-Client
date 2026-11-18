package com.jfant.specter.mixin.compat;

import com.jfant.specter.modules.HitEffects;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.SubmitNodeCollector.CustomGeometryRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Pseudo
@Mixin(
   targets = {"com/jvn/cirrus/client/CirrusLightningRenderer"},
   remap = false
)
public abstract class CirrusLightningRendererMixin {
   @ModifyArg(
      method = {"submit"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitCustomGeometry(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;Lnet/minecraft/client/renderer/SubmitNodeCollector$CustomGeometryRenderer;)V"
      ),
      index = 2,
      require = 0
   )
   private static CustomGeometryRenderer specter$tintBolt(CustomGeometryRenderer draw, @Local(argsOnly = true) long seed) {
      return HitEffects.tintBolt(seed, draw, "Cirrus");
   }
}

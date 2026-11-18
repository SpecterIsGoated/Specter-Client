package com.jfant.specter.mixin;

import com.jfant.specter.modules.HitEffects;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.SubmitNodeCollector.CustomGeometryRenderer;
import net.minecraft.client.renderer.entity.LightningBoltRenderer;
import net.minecraft.client.renderer.entity.state.LightningBoltRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin({LightningBoltRenderer.class})
public abstract class LightningEntityRendererMixin {
   @ModifyArg(
      method = {"submit(Lnet/minecraft/client/renderer/entity/state/LightningBoltRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitCustomGeometry(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;Lnet/minecraft/client/renderer/SubmitNodeCollector$CustomGeometryRenderer;)V"
      ),
      index = 2
   )
   private CustomGeometryRenderer specter$tintBolt(CustomGeometryRenderer draw, @Local(argsOnly = true) LightningBoltRenderState state) {
      return HitEffects.tintBolt(state.seed, draw, "vanilla");
   }
}

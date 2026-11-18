package com.jfant.specter.core.mixin;

import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.client.renderer.feature.ShadowFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({ShadowFeatureRenderer.class})
public abstract class ShadowPiecesCommandRendererMixin {
   @Inject(
      method = {"render"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void meteor$onRender(SubmitNodeCollection queue, BufferSource vertexConsumers, CallbackInfo info) {
      if (queue.getShadowSubmits().isEmpty()) {
         info.cancel();
      }
   }
}

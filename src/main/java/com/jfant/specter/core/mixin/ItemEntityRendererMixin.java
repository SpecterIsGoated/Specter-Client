package com.jfant.specter.core.mixin;

import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.events.render.RenderItemEntityEvent;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.state.CameraRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({ItemEntityRenderer.class})
public abstract class ItemEntityRendererMixin {
   @Shadow
   @Final
   private ItemModelResolver itemModelResolver;

   @Inject(
      method = {"submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void renderStack(
      ItemEntityRenderState itemEntityRenderState, PoseStack matrixStack, SubmitNodeCollector orderedRenderCommandQueue, CameraRenderState arg, CallbackInfo ci
   ) {
      RenderItemEntityEvent event = (RenderItemEntityEvent)SpecterCore.EVENT_BUS
         .post(
            RenderItemEntityEvent.get(
               itemEntityRenderState,
               SpecterCore.mc.getDeltaTracker().getGameTimeDeltaPartialTick(true),
               matrixStack,
               null,
               itemEntityRenderState.lightCoords,
               this.itemModelResolver,
               orderedRenderCommandQueue
            )
         );
      if (event.isCancelled()) {
         ci.cancel();
      }
   }
}

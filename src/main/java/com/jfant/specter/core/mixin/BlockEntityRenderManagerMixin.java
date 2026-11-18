package com.jfant.specter.core.mixin;

import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.events.render.RenderBlockEntityEvent;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({BlockEntityRenderDispatcher.class})
public abstract class BlockEntityRenderManagerMixin {
   @Inject(
      method = {"submit"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private <S extends BlockEntityRenderState> void onRenderEntity(
      S renderState, PoseStack matrices, SubmitNodeCollector queue, CameraRenderState arg, CallbackInfo ci
   ) {
      RenderBlockEntityEvent event = (RenderBlockEntityEvent)SpecterCore.EVENT_BUS.post(RenderBlockEntityEvent.get(renderState));
      if (event.isCancelled()) {
         ci.cancel();
      }
   }
}

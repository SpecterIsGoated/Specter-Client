package com.jfant.specter.core.mixin;

import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.events.render.ApplyTransformationEvent;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import net.minecraft.client.renderer.block.model.ItemTransform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({ItemTransform.class})
public abstract class TransformationMixin {
   @Inject(
      method = {"apply"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onApply(boolean leftHanded, Pose entry, CallbackInfo info) {
      ApplyTransformationEvent event = (ApplyTransformationEvent)SpecterCore.EVENT_BUS.post(ApplyTransformationEvent.get((ItemTransform)this, leftHanded));
      if (event.isCancelled()) {
         info.cancel();
      }
   }
}

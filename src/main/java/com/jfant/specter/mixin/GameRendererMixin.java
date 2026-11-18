package com.jfant.specter.mixin;

import com.jfant.specter.anim.Motion;
import com.jfant.specter.modules.GhostFreecam;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({GameRenderer.class})
public abstract class GameRendererMixin {
   @Inject(
      method = {"render"},
      at = {@At("HEAD")}
   )
   private void specter$frame(CallbackInfo ci) {
      Motion.frame();
   }

   @Inject(
      method = {"shouldRenderBlockOutline"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void specter$noOutlineInFreecam(CallbackInfoReturnable<Boolean> cir) {
      if (GhostFreecam.on() != null) {
         cir.setReturnValue(false);
      }
   }

}

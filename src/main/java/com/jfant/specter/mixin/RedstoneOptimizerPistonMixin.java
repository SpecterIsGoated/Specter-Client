package com.jfant.specter.mixin;

import com.jfant.specter.modules.RedstoneOptimizer;
import net.minecraft.client.renderer.blockentity.PistonHeadRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Redstone Optimizer: draw blocks moved by pistons only near the camera. */
@Mixin(PistonHeadRenderer.class)
public abstract class RedstoneOptimizerPistonMixin {
   @Inject(method = "getViewDistance", at = @At("RETURN"), cancellable = true)
   private void specter$pistonViewDistance(CallbackInfoReturnable<Integer> cir) {
      RedstoneOptimizer m = RedstoneOptimizer.on();
      if (m != null) {
         cir.setReturnValue(m.pistonViewDistance(cir.getReturnValueI()));
      }
   }
}

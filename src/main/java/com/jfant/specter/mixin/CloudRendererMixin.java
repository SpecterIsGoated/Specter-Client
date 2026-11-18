package com.jfant.specter.mixin;

import com.jfant.specter.modules.Atmosphere;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.renderer.CloudRenderer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({CloudRenderer.class})
public abstract class CloudRendererMixin {
   @Inject(
      method = {"render"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void specter$hide(int color, CloudStatus mode, float cloudHeight, Vec3 cameraPos, long worldTime, float tickProgress, CallbackInfo ci) {
      if (Atmosphere.hidesVanillaClouds()) {
         ci.cancel();
      }
   }

   @ModifyVariable(
      method = {"render"},
      at = @At("HEAD"),
      argsOnly = true
   )
   private long specter$cloudSpeed(long worldTime) {
      return Atmosphere.cloudTime(worldTime);
   }
}

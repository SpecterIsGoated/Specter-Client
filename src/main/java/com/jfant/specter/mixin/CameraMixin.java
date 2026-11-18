package com.jfant.specter.mixin;

import com.jfant.specter.modules.GhostFreecam;
import com.jfant.specter.modules.GhostFreelook;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({Camera.class})
public abstract class CameraMixin {
   @Shadow
   private boolean detached;

   @Shadow
   protected abstract void setPosition(double var1, double var3, double var5);

   @Inject(
      method = {"setup"},
      at = {@At("HEAD")}
   )
   private void specter$stepGhostCamera(Level area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickProgress, CallbackInfo ci) {
      GhostFreecam cam = GhostFreecam.on();
      if (cam != null) {
         cam.stepFrame();
      }
   }

   @WrapOperation(
      method = {"setup"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/Camera;setRotation(FF)V"
      )}
   )
   private void specter$ghostRotation(Camera camera, float yaw, float pitch, Operation<Void> original) {
      GhostFreecam cam = GhostFreecam.on();
      if (cam != null) {
         original.call(new Object[]{camera, cam.yaw, cam.pitch});
      } else {
         GhostFreelook look = GhostFreelook.on();
         if (look != null) {
            original.call(new Object[]{camera, look.yaw, look.pitch});
         } else {
            original.call(new Object[]{camera, yaw, pitch});
         }
      }
   }

   @Inject(
      method = {"setup"},
      at = {@At("TAIL")}
   )
   private void specter$ghostPosition(Level area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickProgress, CallbackInfo ci) {
      GhostFreecam cam = GhostFreecam.on();
      if (cam != null) {
         this.setPosition(cam.x, cam.y, cam.z);
         this.detached = true;
      }
   }

   @Inject(
      method = {"getMaxZoom"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void specter$seeThroughWalls(float desiredDistance, CallbackInfoReturnable<Float> cir) {
      GhostFreelook look = GhostFreelook.on();
      if (look != null && look.seeThroughWalls() && GhostFreecam.on() == null) {
         cir.setReturnValue(desiredDistance);
      }
   }
}

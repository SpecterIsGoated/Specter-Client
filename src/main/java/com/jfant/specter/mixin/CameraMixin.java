package com.jfant.specter.mixin;

import com.jfant.specter.camera.GhostView;
import com.jfant.specter.modules.CustomFov;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
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

   // 26.x: Camera#setup became alignWithEntity(partialTicks), called from Camera#update every frame.
   @Inject(
      method = {"alignWithEntity"},
      at = {@At("HEAD")}
   )
   private void specter$stepGhostCamera(float partialTicks, CallbackInfo ci) {
      GhostFreecam cam = GhostFreecam.on();
      if (cam != null) {
         cam.stepFrame();
      }
   }

   @WrapOperation(
      method = {"alignWithEntity"},
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
      method = {"alignWithEntity"},
      at = {@At("TAIL")}
   )
   private void specter$ghostPosition(float partialTicks, CallbackInfo ci) {
      GhostFreecam cam = GhostFreecam.on();
      if (cam != null) {
         this.setPosition(cam.x, cam.y, cam.z);
         this.detached = true;
      }
   }

   // A ghost camera inside blocks would have every cave culled away (and Sodium 0.9 never reaches the old VisGraph
   // hook). Cave culling is off while one can be in there - what vanilla does for a spectator inside a block; vanilla's
   // SectionOcclusionGraph and Sodium's terrain setup both read this flag after it is filled in here, the same frame.
   @Inject(
      method = {"extractRenderState"},
      at = {@At("TAIL")}
   )
   private void specter$ghostSeesCaves(CameraRenderState cameraState, DeltaTracker deltaTracker, CallbackInfo ci) {
      if (GhostView.seesThroughBlocks()) {
         cameraState.smartCull = false;
      }
   }

   // 26.x: the FOV moved from GameRenderer#getFov(camera, partialTicks, useFovSetting) to Camera#calculateFov (the world,
   // uses the FOV setting) and Camera#calculateHudFov (the hand, fixed 70).
   @ModifyReturnValue(
      method = {"calculateFov"},
      at = {@At("RETURN")}
   )
   private float specter$worldFov(float fov) {
      fov = CustomFov.apply(fov);
      float zoom = GhostFreecam.zoomFactor();
      return zoom != 1.0F ? fov / zoom : fov;
   }

   @ModifyReturnValue(
      method = {"calculateHudFov"},
      at = {@At("RETURN")}
   )
   private float specter$handFov(float fov) {
      float zoom = GhostFreecam.zoomFactor();
      return zoom != 1.0F ? fov / zoom : fov;
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

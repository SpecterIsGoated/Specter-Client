package com.jfant.specter.mixin;

import com.jfant.specter.fx.WorldFx;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// 26.x: the world is finished when GameRenderer#renderLevel hands over to render3dHud (the hand); the projection and
// the camera's view rotation now come from the extracted CameraRenderState.
@Mixin(
   value = {GameRenderer.class},
   priority = 900
)
public abstract class WorldFxMixin {
   @Inject(
      method = {"renderLevel"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/GameRenderer;render3dHud(Lnet/minecraft/client/renderer/state/level/CameraRenderState;Lnet/minecraft/client/renderer/state/level/PlayerRenderState;Lnet/minecraft/client/renderer/state/OptionsRenderState;Z)V"
      )}
   )
   private void specter$worldFx(
      CallbackInfo ci,
      @Local(name = "projectionMatrix") Matrix4f projection,
      @Local(name = "cameraState") CameraRenderState camera,
      @Local(name = "worldPartialTicks") float partialTicks
   ) {
      WorldFx.afterWorld(projection, camera.viewRotationMatrix, partialTicks);
   }
}

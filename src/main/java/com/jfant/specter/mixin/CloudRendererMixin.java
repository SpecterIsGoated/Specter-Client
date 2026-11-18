package com.jfant.specter.mixin;

import com.jfant.specter.modules.Atmosphere;
import net.minecraft.client.renderer.CloudRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// 26.x: CloudRenderer#render was split into prepare(...) (builds the mesh, takes the game time) and render / renderOit
// (draw into a pass). Hiding cancels both draw paths; the speed change goes into prepare's game time.
@Mixin({CloudRenderer.class})
public abstract class CloudRendererMixin {
   @Inject(
      method = {"render(Lnet/minecraft/client/CloudStatus;Lcom/mojang/renderpearl/api/commands/RenderPass;)V", "renderOit"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void specter$hide(CallbackInfo ci) {
      if (Atmosphere.hidesVanillaClouds()) {
         ci.cancel();
      }
   }

   @ModifyVariable(
      method = {"prepare(ILnet/minecraft/client/CloudStatus;FILnet/minecraft/world/phys/Vec3;JF)V"},
      at = @At("HEAD"),
      argsOnly = true
   )
   private long specter$cloudSpeed(long gameTime) {
      return Atmosphere.cloudTime(gameTime);
   }
}

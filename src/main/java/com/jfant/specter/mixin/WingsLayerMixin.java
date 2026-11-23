package com.jfant.specter.mixin;

import com.jfant.specter.modules.AngelWings;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.WingsLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Angel Wings: the vanilla elytra model of the local player is hidden while the feathered wings replace it. */
@Mixin(WingsLayer.class)
public abstract class WingsLayerMixin {
   @Inject(
      method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/HumanoidRenderState;FF)V",
      at = @At("HEAD"),
      cancellable = true
   )
   private void specter$hideElytra(PoseStack poseStack, SubmitNodeCollector collector, int light, HumanoidRenderState state, float yRot, float xRot, CallbackInfo ci) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null && state instanceof AvatarRenderState avatar && avatar.id == mc.player.getId() && AngelWings.hidesElytra()) ci.cancel();
   }
}

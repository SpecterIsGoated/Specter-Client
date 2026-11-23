package com.jfant.specter.mixin;

import com.jfant.specter.phone.PhoneOS;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Donut Phone: while the phone is out, the first-person hands are the phone and the arm holding it. */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class PhoneHandsMixin {
   @Inject(method = "submitHandsWithItems", at = @At("HEAD"), cancellable = true)
   private void specter$phoneInHand(float partialTicks, PoseStack poseStack, SubmitNodeCollector collector, PlayerRenderState playerState,
                                    FirstPersonHandsAndItemsRenderState state, CallbackInfo ci) {
      if (PhoneOS.started() && PhoneOS.get().submitHands(poseStack, collector, playerState)) ci.cancel();
   }
}

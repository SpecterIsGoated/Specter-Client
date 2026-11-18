package com.jfant.specter.mixin.move;

import com.jfant.specter.move.AutoSprint;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin({LocalPlayer.class})
public abstract class MoveSprintMixin {
   private static final String FORWARD = "Lnet/minecraft/client/input/Input;hasForwardMovement()Z";

   @ModifyExpressionValue(
      method = {"canStartSprinting"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/player/ClientInput;hasForwardImpulse()Z"
      )}
   )
   private boolean specter$startAnyDirection(boolean forward) {
      return forward || AutoSprint.omniMoving();
   }

   @ModifyExpressionValue(
      method = {"shouldStopRunSprinting"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/player/ClientInput;hasForwardImpulse()Z"
      )}
   )
   private boolean specter$keepAnyDirection(boolean forward) {
      return forward || AutoSprint.omniMoving();
   }
}

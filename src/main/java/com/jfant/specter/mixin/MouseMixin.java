package com.jfant.specter.mixin;

import com.jfant.specter.modules.GhostFreecam;
import com.jfant.specter.modules.GhostFreelook;
import com.jfant.specter.modules.InventoryScale;
import com.jfant.specter.pvp.MouseTurns;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({MouseHandler.class})
public abstract class MouseMixin {
   private static final double DEGREES_PER_COUNT = 0.15;

   @WrapOperation(
      method = {"turnPlayer"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"
      )}
   )
   private void specter$ghostLook(LocalPlayer player, double deltaX, double deltaY, Operation<Void> original) {
      GhostFreecam cam = GhostFreecam.on();
      if (cam != null) {
         cam.turn(deltaX * 0.15, deltaY * 0.15);
      } else {
         GhostFreelook look = GhostFreelook.on();
         if (look != null) {
            look.turn(deltaX * 0.15, deltaY * 0.15);
         } else {
            MouseTurns.note(deltaX, deltaY);
            original.call(new Object[]{player, deltaX, deltaY});
         }
      }
   }

   @Inject(
      // 26.x added a static getScaledXPos(Window, double) overload; Specter scales the instance one.
      method = {"getScaledXPos(Lcom/mojang/blaze3d/platform/Window;)D"},
      at = {@At("RETURN")},
      cancellable = true
   )
   private void specter$scaledX(Window window, CallbackInfoReturnable<Double> cir) {
      float ratio = InventoryScale.ratio(Minecraft.getInstance().gui.screen());
      if (ratio != 1.0F) {
         cir.setReturnValue((Double)cir.getReturnValue() / ratio);
      }
   }

   @Inject(
      // 26.x added a static getScaledYPos(Window, double) overload; Specter scales the instance one.
      method = {"getScaledYPos(Lcom/mojang/blaze3d/platform/Window;)D"},
      at = {@At("RETURN")},
      cancellable = true
   )
   private void specter$scaledY(Window window, CallbackInfoReturnable<Double> cir) {
      float ratio = InventoryScale.ratio(Minecraft.getInstance().gui.screen());
      if (ratio != 1.0F) {
         cir.setReturnValue((Double)cir.getReturnValue() / ratio);
      }
   }
}

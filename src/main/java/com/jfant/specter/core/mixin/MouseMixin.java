package com.jfant.specter.core.mixin;

import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.events.meteor.MouseClickEvent;
import com.jfant.specter.core.events.meteor.MouseScrollEvent;
import com.jfant.specter.core.utils.misc.input.Input;
import com.jfant.specter.core.utils.misc.input.KeyAction;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({MouseHandler.class})
public abstract class MouseMixin {
   @Shadow
   @Final
   private Minecraft minecraft;

   @Shadow
   public abstract double getScaledXPos(Window var1);

   @Shadow
   public abstract double getScaledYPos(Window var1);

   @Inject(
      method = {"onButton"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onMouseButton(long window, MouseButtonInfo mouseInput, int action, CallbackInfo ci) {
      Input.setButtonState(mouseInput.button(), action != 0);
      MouseButtonEvent click = new MouseButtonEvent(this.getScaledXPos(this.minecraft.getWindow()), this.getScaledYPos(this.minecraft.getWindow()), mouseInput);
      if (((MouseClickEvent)SpecterCore.EVENT_BUS.post(MouseClickEvent.get(click, KeyAction.get(action)))).isCancelled()) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"onScroll"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onMouseScroll(long window, double horizontal, double vertical, CallbackInfo info) {
      if (((MouseScrollEvent)SpecterCore.EVENT_BUS.post(MouseScrollEvent.get(vertical))).isCancelled()) {
         info.cancel();
      }
   }
}

package com.jfant.specter.mixin;

import com.jfant.specter.anim.Motion;
import com.jfant.specter.modules.CustomFov;
import com.jfant.specter.modules.GhostFreecam;
import com.jfant.specter.modules.InventoryScale;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({GameRenderer.class})
public abstract class GameRendererMixin {
   @WrapOperation(
      method = {"render"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/gui/screens/Screen;renderWithTooltipAndSubtitles(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"
      )}
   )
   private void specter$scaleScreen(Screen screen, GuiGraphics context, int mouseX, int mouseY, float delta, Operation<Void> original) {
      float ratio = InventoryScale.ratio(screen);
      if (ratio == 1.0F) {
         original.call(new Object[]{screen, context, mouseX, mouseY, delta});
      } else {
         context.pose().pushMatrix();
         context.pose().scale(ratio, ratio);
         InventoryScale.drawing = true;

         try {
            original.call(new Object[]{screen, context, mouseX, mouseY, delta});
         } finally {
            InventoryScale.drawing = false;
            context.pose().popMatrix();
         }
      }
   }

   @Inject(
      method = {"render"},
      at = {@At("HEAD")}
   )
   private void specter$frame(DeltaTracker tickCounter, boolean tick, CallbackInfo ci) {
      Motion.frame();
   }

   @Inject(
      method = {"shouldRenderBlockOutline"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void specter$noOutlineInFreecam(CallbackInfoReturnable<Boolean> cir) {
      if (GhostFreecam.on() != null) {
         cir.setReturnValue(false);
      }
   }

   @Inject(
      method = {"getFov"},
      at = {@At("RETURN")},
      cancellable = true
   )
   private void specter$ghostZoom(Camera camera, float tickProgress, boolean changingFov, CallbackInfoReturnable<Float> cir) {
      if (changingFov) {
         cir.setReturnValue(CustomFov.apply((Float)cir.getReturnValue()));
      }

      float zoom = GhostFreecam.zoomFactor();
      if (zoom != 1.0F) {
         cir.setReturnValue((Float)cir.getReturnValue() / zoom);
      }
   }
}

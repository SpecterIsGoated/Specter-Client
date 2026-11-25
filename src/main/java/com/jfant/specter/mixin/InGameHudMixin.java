package com.jfant.specter.mixin;

import com.jfant.specter.modules.Crosshair;
import com.jfant.specter.modules.HotbarScale;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({Hud.class})
public abstract class InGameHudMixin {
   @Unique
   private boolean specter$barsMoved;

   @WrapOperation(
      method = {"extractHotbarAndDecorations"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/gui/Hud;extractItemHotbar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V"
      )}
   )
   private void specter$scaleHotbar(Hud hud, GuiGraphicsExtractor context, DeltaTracker tickCounter, Operation<Void> original) {
      float ratio = HotbarScale.ratio();
      if (ratio == 1.0F) {
         original.call(new Object[]{hud, context, tickCounter});
      } else {
         float anchorX = context.guiWidth() / 2.0F;
         float anchorY = context.guiHeight();
         context.pose().pushMatrix();
         context.pose().translate(anchorX, anchorY);
         context.pose().scale(ratio, ratio);
         context.pose().translate(-anchorX, -anchorY);

         try {
            original.call(new Object[]{hud, context, tickCounter});
         } finally {
            context.pose().popMatrix();
         }

         float offset = HotbarScale.barsOffset(ratio);
         if (offset != 0.0F) {
            context.pose().pushMatrix();
            context.pose().translate(0.0F, offset);
            this.specter$barsMoved = true;
         }
      }
   }

   /** The Donut Phone's screen goes under the hotbar and the rest of the HUD, like the 3D phone it sits on. */
   @Inject(
      method = {"extractRenderState"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/gui/Hud;extractHotbarAndDecorations(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V"
      )}
   )
   private void specter$phoneUnderHud(GuiGraphicsExtractor context, DeltaTracker tickCounter, CallbackInfo ci) {
      if (com.jfant.specter.phone.PhoneOS.started()) com.jfant.specter.phone.PhoneOS.get().hudPass(context);
   }

   @Inject(
      method = {"extractCrosshair"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void specter$crosshair(GuiGraphicsExtractor context, DeltaTracker tickCounter, CallbackInfo ci) {
      if (Crosshair.replaces()) {
         Crosshair.draw(context, tickCounter.getGameTimeDeltaPartialTick(false));
         ci.cancel();
      }
   }

   @Inject(
      method = {"extractHotbarAndDecorations"},
      at = {@At("TAIL")}
   )
   private void specter$restoreBars(GuiGraphicsExtractor context, DeltaTracker tickCounter, CallbackInfo ci) {
      if (this.specter$barsMoved) {
         this.specter$barsMoved = false;
         context.pose().popMatrix();
      }
   }
}

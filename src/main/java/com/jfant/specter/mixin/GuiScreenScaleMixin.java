package com.jfant.specter.mixin;

import com.jfant.specter.modules.InventoryScale;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// 26.x: screens are extracted from Gui#extractRenderState instead of GameRenderer#render, so Inventory Scale's
// screen scaling (which used to wrap Screen#renderWithTooltipAndSubtitles in GameRendererMixin) lives here now.
@Mixin({Gui.class})
public abstract class GuiScreenScaleMixin {
   @WrapOperation(
      method = {"extractRenderState"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/gui/screens/Screen;extractRenderStateWithTooltipAndSubtitles(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V"
      )}
   )
   private void specter$scaleScreen(Screen screen, GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, Operation<Void> original) {
      float ratio = InventoryScale.ratio(screen);
      if (ratio == 1.0F) {
         original.call(screen, context, mouseX, mouseY, delta);
      } else {
         context.pose().pushMatrix();
         context.pose().scale(ratio, ratio);
         InventoryScale.drawing = true;

         try {
            original.call(screen, context, mouseX, mouseY, delta);
         } finally {
            InventoryScale.drawing = false;
            context.pose().popMatrix();
         }
      }
   }
}

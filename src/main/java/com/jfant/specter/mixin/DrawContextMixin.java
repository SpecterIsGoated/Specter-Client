package com.jfant.specter.mixin;

import com.jfant.specter.modules.InventoryScale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({GuiGraphicsExtractor.class})
public abstract class DrawContextMixin {
   @Inject(
      method = {"guiWidth"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void specter$width(CallbackInfoReturnable<Integer> cir) {
      if (InventoryScale.drawing) {
         cir.setReturnValue(InventoryScale.width());
      }
   }

   @Inject(
      method = {"guiHeight"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void specter$height(CallbackInfoReturnable<Integer> cir) {
      if (InventoryScale.drawing) {
         cir.setReturnValue(InventoryScale.height());
      }
   }
}

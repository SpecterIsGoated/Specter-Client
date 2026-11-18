package com.jfant.specter.mixin;

import com.jfant.specter.modules.InventoryScale;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin({Screen.class})
public abstract class ScreenMixin {
   @ModifyVariable(
      method = {"init(II)V"},
      at = @At("HEAD"),
      argsOnly = true,
      ordinal = 0
   )
   private int specter$initWidth(int width) {
      return InventoryScale.appliesTo((Screen)this) ? InventoryScale.width() : width;
   }

   @ModifyVariable(
      method = {"init(II)V"},
      at = @At("HEAD"),
      argsOnly = true,
      ordinal = 1
   )
   private int specter$initHeight(int height) {
      return InventoryScale.appliesTo((Screen)this) ? InventoryScale.height() : height;
   }

   @ModifyVariable(
      method = {"resize"},
      at = @At("HEAD"),
      argsOnly = true,
      ordinal = 0
   )
   private int specter$resizeWidth(int width) {
      return InventoryScale.appliesTo((Screen)this) ? InventoryScale.width() : width;
   }

   @ModifyVariable(
      method = {"resize"},
      at = @At("HEAD"),
      argsOnly = true,
      ordinal = 1
   )
   private int specter$resizeHeight(int height) {
      return InventoryScale.appliesTo((Screen)this) ? InventoryScale.height() : height;
   }
}

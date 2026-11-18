package com.jfant.specter.mixin;

import com.jfant.specter.festive.Festive;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({TitleScreen.class})
public abstract class TitleScreenSnowMixin {
   @Inject(
      method = {"render"},
      at = {@At("TAIL")}
   )
   private void specter$snow(GuiGraphics context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
      Festive.title(context, context.guiWidth(), context.guiHeight());
   }
}

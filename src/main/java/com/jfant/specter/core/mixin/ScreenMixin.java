package com.jfant.specter.core.mixin;

import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.commands.Commands;
import com.jfant.specter.core.systems.config.Config;
import com.jfant.specter.core.utils.misc.text.MeteorClickEvent;
import com.jfant.specter.core.utils.misc.text.RunnableClickEvent;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.ClickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(
   value = {Screen.class},
   priority = 500
)
public abstract class ScreenMixin {
   @Inject(
      method = {"defaultHandleClickEvent"},
      at = {@At(
         value = "INVOKE",
         target = "Lorg/slf4j/Logger;error(Ljava/lang/String;Ljava/lang/Object;)V",
         remap = false
      )},
      cancellable = true
   )
   private static void onHandleBasicClickEvent(ClickEvent clickEvent, Minecraft client, Screen screen, CallbackInfo ci) {
      if (clickEvent instanceof RunnableClickEvent runnableClickEvent) {
         runnableClickEvent.runnable.run();
         ci.cancel();
      } else if (clickEvent instanceof MeteorClickEvent meteorClickEvent && meteorClickEvent.value.startsWith(Config.get().prefix.get())) {
         try {
            Commands.dispatch(meteorClickEvent.value.substring(Config.get().prefix.get().length()));
         } catch (CommandSyntaxException var10) {
            SpecterCore.LOG.error("Failed to run command", var10);
         } finally {
            ci.cancel();
         }
      }
   }
}

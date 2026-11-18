package com.jfant.specter.core.mixin;

import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.events.game.ChangePerspectiveEvent;
import com.jfant.specter.core.systems.modules.Modules;
import com.jfant.specter.core.utils.misc.input.KeyBinds;
import java.io.File;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({Options.class})
public abstract class GameOptionsMixin {
   @Shadow
   @Final
   @Mutable
   public KeyMapping[] keyMappings;

   @Inject(
      method = {"<init>"},
      at = {@At(
         value = "FIELD",
         target = "Lnet/minecraft/client/Options;keyMappings:[Lnet/minecraft/client/KeyMapping;",
         opcode = 181,
         shift = Shift.AFTER
      )}
   )
   private void onInitAfterKeysAll(Minecraft client, File optionsFile, CallbackInfo info) {
      this.keyMappings = KeyBinds.apply(this.keyMappings);
   }

   @Inject(
      method = {"setCameraType"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void setPerspective(CameraType perspective, CallbackInfo info) {
      if (Modules.get() != null) {
         ChangePerspectiveEvent event = (ChangePerspectiveEvent)SpecterCore.EVENT_BUS.post(ChangePerspectiveEvent.get(perspective));
         if (event.isCancelled()) {
            info.cancel();
         }
      }
   }
}

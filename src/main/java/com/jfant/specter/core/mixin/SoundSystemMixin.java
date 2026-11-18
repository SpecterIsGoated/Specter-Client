package com.jfant.specter.core.mixin;

import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.events.world.PlaySoundEvent;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundEngine.PlayResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({SoundEngine.class})
public abstract class SoundSystemMixin {
   @Shadow
   public abstract void stop(SoundInstance var1);

   @Inject(
      method = {"play(Lnet/minecraft/client/resources/sounds/SoundInstance;)Lnet/minecraft/client/sounds/SoundEngine$PlayResult;"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onPlay(SoundInstance soundInstance, CallbackInfoReturnable<PlayResult> cir) {
      PlaySoundEvent event = (PlaySoundEvent)SpecterCore.EVENT_BUS.post(PlaySoundEvent.get(soundInstance));
      if (event.isCancelled()) {
         cir.cancel();
      }
   }
}

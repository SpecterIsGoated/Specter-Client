package com.jfant.specter.mixin;

import com.jfant.specter.modules.HitEffects;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({LocalPlayer.class})
public abstract class ClientPlayerHitParticlesMixin {
   @Inject(
      method = {"crit"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void specter$hideCrit(Entity target, CallbackInfo ci) {
      if (HitEffects.hidesOwnVanilla()) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"magicCrit"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void specter$hideEnchantedHit(Entity target, CallbackInfo ci) {
      if (HitEffects.hidesOwnVanilla()) {
         ci.cancel();
      }
   }
}

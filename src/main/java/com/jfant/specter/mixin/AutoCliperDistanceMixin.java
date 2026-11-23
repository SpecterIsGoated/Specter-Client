package com.jfant.specter.mixin;

import com.jfant.specter.modules.AutoCliper;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Auto Cliper: you stay drawn however far the orbit camera is (vanilla stops drawing a player beyond about 64 blocks). */
@Mixin(Entity.class)
public abstract class AutoCliperDistanceMixin {
   @Inject(method = "shouldRenderAtSqrDistance(D)Z", at = @At("HEAD"), cancellable = true)
   private void specter$keepSelfInView(double distanceSqr, CallbackInfoReturnable<Boolean> cir) {
      if ((Object)this == Minecraft.getInstance().player && AutoCliper.on() != null) cir.setReturnValue(true);
   }
}

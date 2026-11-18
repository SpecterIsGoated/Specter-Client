package com.jfant.specter.core.mixin;

import com.jfant.specter.core.mixininterface.ICapabilityTracker;
import com.mojang.blaze3d.opengl.GlStateManager.BooleanState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin({BooleanState.class})
public abstract class CapabilityTrackerMixin implements ICapabilityTracker {
   @Shadow
   private boolean enabled;

   @Shadow
   public abstract void setEnabled(boolean var1);

   @Override
   public boolean meteor$get() {
      return this.enabled;
   }

   @Override
   public void meteor$set(boolean state) {
      this.setEnabled(state);
   }
}

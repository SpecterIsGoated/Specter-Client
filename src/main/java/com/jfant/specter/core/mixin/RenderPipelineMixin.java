package com.jfant.specter.core.mixin;

import com.jfant.specter.core.mixininterface.IRenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin({RenderPipeline.class})
public abstract class RenderPipelineMixin implements IRenderPipeline {
   @Unique
   private boolean lineSmooth;

   @Override
   public void meteor$setLineSmooth(boolean lineSmooth) {
      this.lineSmooth = lineSmooth;
   }

   @Override
   public boolean meteor$getLineSmooth() {
      return this.lineSmooth;
   }
}

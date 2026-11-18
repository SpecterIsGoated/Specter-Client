package com.jfant.specter.core.mixin;

import com.jfant.specter.core.mixininterface.IGpuDevice;
import com.mojang.blaze3d.opengl.GlDevice;
import com.mojang.blaze3d.systems.RenderPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin({GlDevice.class})
public abstract class GlBackendMixin implements IGpuDevice {
   @Unique
   private int x;
   @Unique
   private int y;
   @Unique
   private int width;
   @Unique
   private int height;
   @Unique
   private boolean set;

   @Override
   public void meteor$pushScissor(int x, int y, int width, int height) {
      if (this.set) {
         throw new IllegalStateException("Currently there can only be one global scissor pushed");
      } else {
         this.x = x;
         this.y = y;
         this.width = width;
         this.height = height;
         this.set = true;
      }
   }

   @Override
   public void meteor$popScissor() {
      if (!this.set) {
         throw new IllegalStateException("No scissor pushed");
      } else {
         this.set = false;
      }
   }

   @Deprecated
   @Override
   public void meteor$onCreateRenderPass(RenderPass pass) {
      if (this.set) {
         pass.enableScissor(this.x, this.y, this.width, this.height);
      }
   }
}

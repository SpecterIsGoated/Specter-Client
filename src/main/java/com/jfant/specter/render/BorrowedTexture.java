package com.jfant.specter.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;
import net.minecraft.client.renderer.texture.AbstractTexture;

public final class BorrowedTexture extends AbstractTexture {
   public BorrowedTexture() {
      this.sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
   }

   public void point(RenderTarget framebuffer) {
      this.texture = framebuffer.getDepthTexture();
      this.textureView = framebuffer.getDepthTextureView();
   }

   @Override
   public void close() {
      this.texture = null;
      this.textureView = null;
   }
}

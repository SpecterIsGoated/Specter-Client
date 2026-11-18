package com.jfant.specter.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.textures.GpuTexture;
import java.nio.ByteBuffer;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.util.Mth;
import org.lwjgl.system.MemoryUtil;

/**
 * Small helpers shared by Specter's own GPU passes (sky, world effects, sky lyrics) for the 26.x renderpearl API.
 * In 1.21.11 these lived inline in each renderer and talked to the blaze3d device directly.
 */
public final class GpuKit {
   /** One triangle that covers the whole screen, POSITION format (3 floats per vertex). */
   public static GpuBuffer fullscreenTriangle(String label) {
      ByteBuffer data = MemoryUtil.memAlloc(36);

      try {
         data.putFloat(-1.0F).putFloat(-1.0F).putFloat(0.0F)
            .putFloat(3.0F).putFloat(-1.0F).putFloat(0.0F)
            .putFloat(-1.0F).putFloat(3.0F).putFloat(0.0F)
            .flip();
         return RenderSystem.getDevice().createBuffer(() -> label, GpuBuffer.USAGE_VERTEX, data);
      } finally {
         MemoryUtil.memFree(data);
      }
   }

   /** A ring of CPU-written uniform buffers, rotated once per frame. */
   public static MappableRingBuffer uniformRing(String label, int size) {
      return new MappableRingBuffer(() -> label, GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE, size);
   }

   /** A uniform-buffer bind group with a single member. */
   public static BindGroupLayout ubo(String name) {
      return BindGroupLayout.builder().withUniform(name, UniformType.UNIFORM_BUFFER).build();
   }

   /** A combined image sampler bind group with a single member. */
   public static BindGroupLayout sampler(String name) {
      return BindGroupLayout.builder().withUniform(name, UniformType.COMBINED_IMAGE_SAMPLER).build();
   }

   /** Uploads an RGBA image as a sampled texture (single mip level). */
   public static GpuTexture upload(String label, NativeImage image) {
      GpuTexture texture = RenderSystem.getDevice()
         .createTexture(() -> label, GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING, GpuFormat.RGBA8_UNORM, image.getWidth(), image.getHeight(), 1, 1);
      RenderSystem.getDevice().createCommandEncoder().writeToTexture(texture, image);
      return texture;
   }

   /** Uploads an RGBA image with a full box-filtered mip chain. Takes ownership of (and closes) {@code level}. */
   public static GpuTexture uploadMipmapped(String label, NativeImage level) {
      int levels = 1 + Mth.log2(Math.max(1, Math.min(level.getWidth(), level.getHeight())));
      GpuTexture texture = RenderSystem.getDevice()
         .createTexture(() -> label, GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING, GpuFormat.RGBA8_UNORM, level.getWidth(), level.getHeight(), 1, levels);
      CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();

      try {
         for (int i = 0; i < levels; i++) {
            encoder.writeToTexture(texture, level, i, 0, 0, 0);
            if (i + 1 < levels) {
               NativeImage next = halve(level);
               level.close();
               level = next;
            }
         }
      } finally {
         level.close();
      }

      return texture;
   }

   /** Keeps {@code copy} the size of {@code main} and copies main's depth into it (depth-only target, same format). */
   public static TextureTarget copyDepth(TextureTarget copy, String name, RenderTarget main) {
      if (copy == null) {
         copy = new TextureTarget(name, main.width, main.height, null, GpuFormat.D32_FLOAT);
      } else if (copy.width != main.width || copy.height != main.height) {
         copy.resize(main.width, main.height);
      }

      copy.copyDepthFrom(main);
      return copy;
   }

   public static NativeImage halve(NativeImage src) {
      int w = Math.max(1, src.getWidth() / 2);
      int h = Math.max(1, src.getHeight() / 2);
      NativeImage out = new NativeImage(w, h, false);

      for (int y = 0; y < h; y++) {
         for (int x = 0; x < w; x++) {
            int a = 0;
            int r = 0;
            int g = 0;
            int b = 0;

            for (int dy = 0; dy < 2; dy++) {
               for (int dx = 0; dx < 2; dx++) {
                  int c = src.getPixel(Math.min(src.getWidth() - 1, x * 2 + dx), Math.min(src.getHeight() - 1, y * 2 + dy));
                  a += c >>> 24;
                  r += c >> 16 & 0xFF;
                  g += c >> 8 & 0xFF;
                  b += c & 0xFF;
               }
            }

            out.setPixel(x, y, (a + 2) / 4 << 24 | (r + 2) / 4 << 16 | (g + 2) / 4 << 8 | (b + 2) / 4);
         }
      }

      return out;
   }

   private GpuKit() {
   }
}

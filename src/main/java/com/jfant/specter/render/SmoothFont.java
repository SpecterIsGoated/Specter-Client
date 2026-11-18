package com.jfant.specter.render;

import com.jfant.specter.SpecterClientAddon;
import it.unimi.dsi.fastutil.ints.Int2BooleanMap;
import it.unimi.dsi.fastutil.ints.Int2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2FloatMap;
import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2FloatMap;
import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import org.lwjgl.stb.STBTTFontinfo;
import org.lwjgl.stb.STBTruetype;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

final class SmoothFont {
   private static final float CAP_HEIGHT = 6.6F;
   private static final float TRACKING = 0.15F;
   private final STBTTFontinfo info;
   private final ByteBuffer data;
   private final float unit;
   private final Int2BooleanMap hasCache = new Int2BooleanOpenHashMap();
   private final Int2FloatMap advanceCache = new Int2FloatOpenHashMap();
   private final Long2FloatMap kernCache = new Long2FloatOpenHashMap();

   private SmoothFont(STBTTFontinfo info, ByteBuffer data, float unit) {
      this.info = info;
      this.data = data;
      this.unit = unit;
   }

   static SmoothFont load(String resource) {
      try {
         SmoothFont var16;
         try (InputStream in = SmoothFont.class.getResourceAsStream(resource)) {
            if (in == null) {
               throw new IllegalStateException("missing " + resource);
            }

            byte[] bytes = in.readAllBytes();
            ByteBuffer data = MemoryUtil.memAlloc(bytes.length);
            data.put(bytes).flip();
            STBTTFontinfo info = STBTTFontinfo.create();
            if (!STBTruetype.stbtt_InitFont(info, data)) {
               throw new IllegalStateException("not a font: " + resource);
            }

            MemoryStack stack = MemoryStack.stackPush();

            float capUnits;
            try {
               IntBuffer x0 = stack.mallocInt(1);
               IntBuffer y0 = stack.mallocInt(1);
               IntBuffer x1 = stack.mallocInt(1);
               IntBuffer y1 = stack.mallocInt(1);
               STBTruetype.stbtt_GetCodepointBox(info, 72, x0, y0, x1, y1);
               capUnits = Math.max(1, y1.get(0));
            } catch (Throwable var13) {
               if (stack != null) {
                  try {
                     stack.close();
                  } catch (Throwable var12) {
                     var13.addSuppressed(var12);
                  }
               }

               throw var13;
            }

            if (stack != null) {
               stack.close();
            }

            var16 = new SmoothFont(info, data, 6.6F / capUnits);
         }

         return var16;
      } catch (Exception var15) {
         SpecterClientAddon.LOG.warn("Clean theme: could not read the font " + resource, var15);
         return null;
      }
   }

   boolean has(int codePoint) {
      return this.hasCache.computeIfAbsent(codePoint, cp -> cp == 32 || STBTruetype.stbtt_FindGlyphIndex(this.info, cp) != 0);
   }

   float advance(int codePoint) {
      return this.advanceCache.computeIfAbsent(codePoint, this::readAdvance);
   }

   private float readAdvance(int codePoint) {
      MemoryStack stack = MemoryStack.stackPush();

      float var5;
      try {
         IntBuffer advance = stack.mallocInt(1);
         IntBuffer bearing = stack.mallocInt(1);
         STBTruetype.stbtt_GetCodepointHMetrics(this.info, codePoint, advance, bearing);
         var5 = advance.get(0) * this.unit + 0.15F;
      } catch (Throwable var7) {
         if (stack != null) {
            try {
               stack.close();
            } catch (Throwable var6) {
               var7.addSuppressed(var6);
            }
         }

         throw var7;
      }

      if (stack != null) {
         stack.close();
      }

      return var5;
   }

   float kern(int first, int second) {
      return this.kernCache
         .computeIfAbsent((long)first << 32 | second & 4294967295L, key -> STBTruetype.stbtt_GetCodepointKernAdvance(this.info, first, second) * this.unit);
   }

   SmoothFont.Bitmap raster(int codePoint, float scale, int baseline) {
      float s = this.unit * scale;
      MemoryStack stack = MemoryStack.stackPush();

      SmoothFont.Bitmap var24;
      label103: {
         Object pixels;
         try {
            IntBuffer x0 = stack.mallocInt(1);
            IntBuffer y0 = stack.mallocInt(1);
            IntBuffer x1 = stack.mallocInt(1);
            IntBuffer y1 = stack.mallocInt(1);
            STBTruetype.stbtt_GetCodepointBitmapBox(this.info, codePoint, s, s, x0, y0, x1, y1);
            int w = x1.get(0) - x0.get(0);
            int h = y1.get(0) - y0.get(0);
            if (w > 0 && h > 0) {
               ByteBuffer pixelsx = MemoryUtil.memAlloc(w * h);

               try {
                  STBTruetype.stbtt_MakeCodepointBitmap(this.info, pixelsx, w, h, w, s, s, codePoint);
                  float[] alpha = new float[w * h];

                  for (int i = 0; i < alpha.length; i++) {
                     float a = (pixelsx.get(i) & 255) / 255.0F;
                     alpha[i] = (float)Math.pow(a, 0.85);
                  }

                  var24 = new SmoothFont.Bitmap(alpha, w, h, x0.get(0), baseline + y0.get(0));
                  break label103;
               } finally {
                  MemoryUtil.memFree(pixelsx);
               }
            }

            pixels = null;
         } catch (Throwable var22) {
            if (stack != null) {
               try {
                  stack.close();
               } catch (Throwable var20) {
                  var22.addSuppressed(var20);
               }
            }

            throw var22;
         }

         if (stack != null) {
            stack.close();
         }

         return (SmoothFont.Bitmap)pixels;
      }

      if (stack != null) {
         stack.close();
      }

      return var24;
   }

   record Bitmap(float[] alpha, int width, int height, int dx, int dy) {
   }
}

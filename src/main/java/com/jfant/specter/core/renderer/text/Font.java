package com.jfant.specter.core.renderer.text;

import com.jfant.specter.core.renderer.MeshBuilder;
import com.jfant.specter.core.renderer.Texture;
import com.jfant.specter.core.utils.render.color.Color;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.TextureFormat;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBTTFontinfo;
import org.lwjgl.stb.STBTTPackContext;
import org.lwjgl.stb.STBTTPackRange;
import org.lwjgl.stb.STBTTPackedchar;
import org.lwjgl.stb.STBTruetype;
import org.lwjgl.stb.STBTTPackedchar.Buffer;
import org.lwjgl.system.MemoryStack;

public class Font {
   public final Texture texture;
   private final int height;
   private final float scale;
   private final float ascent;
   private final Int2ObjectOpenHashMap<Font.CharData> charMap = new Int2ObjectOpenHashMap();
   private static final int size = 2048;

   public Font(ByteBuffer buffer, int height) {
      this.height = height;
      STBTTFontinfo fontInfo = STBTTFontinfo.create();
      STBTruetype.stbtt_InitFont(fontInfo, buffer);
      ByteBuffer bitmap = BufferUtils.createByteBuffer(4194304);
      Buffer[] cdata = new Buffer[]{
         STBTTPackedchar.create(95),
         STBTTPackedchar.create(96),
         STBTTPackedchar.create(128),
         STBTTPackedchar.create(144),
         STBTTPackedchar.create(256),
         STBTTPackedchar.create(1)
      };
      STBTTPackContext packContext = STBTTPackContext.create();
      STBTruetype.stbtt_PackBegin(packContext, bitmap, 2048, 2048, 0, 1);
      org.lwjgl.stb.STBTTPackRange.Buffer packRange = STBTTPackRange.create(cdata.length);
      packRange.put(STBTTPackRange.create().set(height, 32, null, 95, cdata[0], (byte)2, (byte)2));
      packRange.put(STBTTPackRange.create().set(height, 160, null, 96, cdata[1], (byte)2, (byte)2));
      packRange.put(STBTTPackRange.create().set(height, 256, null, 128, cdata[2], (byte)2, (byte)2));
      packRange.put(STBTTPackRange.create().set(height, 880, null, 144, cdata[3], (byte)2, (byte)2));
      packRange.put(STBTTPackRange.create().set(height, 1024, null, 256, cdata[4], (byte)2, (byte)2));
      packRange.put(STBTTPackRange.create().set(height, 8734, null, 1, cdata[5], (byte)2, (byte)2));
      packRange.flip();
      STBTruetype.stbtt_PackFontRanges(packContext, buffer, 0, packRange);
      STBTruetype.stbtt_PackEnd(packContext);
      this.texture = new Texture(2048, 2048, TextureFormat.RED8, FilterMode.LINEAR, FilterMode.LINEAR);
      this.texture.upload(bitmap);
      this.scale = STBTruetype.stbtt_ScaleForPixelHeight(fontInfo, height);
      MemoryStack stack = MemoryStack.stackPush();

      try {
         IntBuffer ascent = stack.mallocInt(1);
         STBTruetype.stbtt_GetFontVMetrics(fontInfo, ascent, null, null);
         this.ascent = ascent.get(0);
      } catch (Throwable var16) {
         if (stack != null) {
            try {
               stack.close();
            } catch (Throwable var15) {
               var16.addSuppressed(var15);
            }
         }

         throw var16;
      }

      if (stack != null) {
         stack.close();
      }

      for (int i = 0; i < cdata.length; i++) {
         Buffer cbuf = cdata[i];
         int offset = ((STBTTPackRange)packRange.get(i)).first_unicode_codepoint_in_range();

         for (int j = 0; j < cbuf.capacity(); j++) {
            STBTTPackedchar packedChar = (STBTTPackedchar)cbuf.get(j);
            float ipw = 4.8828125E-4F;
            float iph = 4.8828125E-4F;
            this.charMap
               .put(
                  j + offset,
                  new Font.CharData(
                     packedChar.xoff(),
                     packedChar.yoff(),
                     packedChar.xoff2(),
                     packedChar.yoff2(),
                     packedChar.x0() * ipw,
                     packedChar.y0() * iph,
                     packedChar.x1() * ipw,
                     packedChar.y1() * iph,
                     packedChar.xadvance()
                  )
               );
         }
      }
   }

   public double getWidth(String string, int length) {
      double width = 0.0;

      for (int i = 0; i < length; i++) {
         int cp = string.charAt(i);
         Font.CharData c = (Font.CharData)this.charMap.get(cp);
         if (c == null) {
            c = (Font.CharData)this.charMap.get(32);
         }

         width += c.xAdvance;
      }

      return width;
   }

   public int getHeight() {
      return this.height;
   }

   public double render(MeshBuilder mesh, String string, double x, double y, Color color, double scale) {
      y += this.ascent * this.scale * scale;
      int length = string.length();
      mesh.ensureCapacity(length * 4, length * 6);

      for (int i = 0; i < length; i++) {
         int cp = string.charAt(i);
         Font.CharData c = (Font.CharData)this.charMap.get(cp);
         if (c == null) {
            c = (Font.CharData)this.charMap.get(32);
         }

         mesh.quad(
            mesh.vec2(x + c.x0 * scale, y + c.y0 * scale).vec2(c.u0, c.v0).color(color).next(),
            mesh.vec2(x + c.x0 * scale, y + c.y1 * scale).vec2(c.u0, c.v1).color(color).next(),
            mesh.vec2(x + c.x1 * scale, y + c.y1 * scale).vec2(c.u1, c.v1).color(color).next(),
            mesh.vec2(x + c.x1 * scale, y + c.y0 * scale).vec2(c.u1, c.v0).color(color).next()
         );
         x += c.xAdvance * scale;
      }

      return x;
   }

   private record CharData(float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1, float xAdvance) {
   }
}

package com.jfant.specter.cosmetic;

import com.jfant.specter.SpecterClientAddon;
import com.mojang.blaze3d.platform.NativeImage;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * A player rebuilt as small coloured cubes from their own skin: every surface pixel of the head, body, arms and legs
 * becomes one cube with that pixel's colour (outer layers - hat, jacket, sleeves, pants - win where they are opaque).
 * Death animations take these apart. Model space: x to the player's left, y up from the feet, z forwards, in blocks.
 *
 * <p>Skins are read from the texture the game already holds (downloaded skins) or from the resource pack (default
 * skins); if neither works the cubes get a plain shirt/trousers palette. Results are cached per skin.
 */
public final class SkinVoxels {
   /** Block size of one skin pixel on the player model (the model is drawn at 15/16 scale). */
   public static final float PIXEL = 0.9375F / 16.0F;

   public final float[] x, y, z;
   public final int[] color;
   public final int count;
   public final float size;

   private SkinVoxels(FloatArrayList xs, FloatArrayList ys, FloatArrayList zs, IntArrayList colors, float size) {
      this.x = xs.toFloatArray();
      this.y = ys.toFloatArray();
      this.z = zs.toFloatArray();
      this.color = colors.toIntArray();
      this.count = this.color.length;
      this.size = size;
   }

   private static final Map<String, int[]> SKINS = new LinkedHashMap<>(16, 0.75F, true) {
      @Override
      protected boolean removeEldestEntry(Map.Entry<String, int[]> eldest) {
         return this.size() > 24;
      }
   };

   /** {width, height, pixels...} of the skin, ARGB, or null. */
   private static int[] pixels(AbstractClientPlayer player) {
      Identifier id = player.getSkin().body().texturePath();
      String key = id.toString();
      int[] cached = SKINS.get(key);
      if (cached != null) return cached;
      int[] out = null;
      Minecraft mc = Minecraft.getInstance();
      try {
         if (id.getPath().startsWith("textures/")) {
            try (InputStream in = mc.getResourceManager().open(id); NativeImage img = NativeImage.read(in)) {
               out = copy(img);
            }
         } else {
            AbstractTexture texture = mc.getTextureManager().getTexture(id);
            if (texture instanceof DynamicTexture dynamic && dynamic.getPixels() != null) out = copy(dynamic.getPixels());
         }
      } catch (Exception e) {
         SpecterClientAddon.LOG.debug("Specter death fx: no skin pixels for {}", key, e);
      }
      if (out != null) SKINS.put(key, out);
      return out;
   }

   private static int[] copy(NativeImage img) {
      int w = img.getWidth(), h = img.getHeight();
      if (w != 64 || (h != 64 && h != 32)) return null;
      int[] out = new int[2 + w * h];
      out[0] = w;
      out[1] = h;
      for (int yy = 0; yy < h; yy++) {
         for (int xx = 0; xx < w; xx++) out[2 + yy * w + xx] = img.getPixel(xx, yy);
      }
      return out;
   }

   /** Builds the cubes; {@code step} 1 = one cube per skin pixel, 2 = one per 2x2x2 pixels (fewer, bigger cubes). */
   public static SkinVoxels of(AbstractClientPlayer player, int step) {
      int[] skin = pixels(player);
      FloatArrayList xs = new FloatArrayList(), ys = new FloatArrayList(), zs = new FloatArrayList();
      IntArrayList colors = new IntArrayList();
      boolean legacy = skin != null && skin[1] == 32;
      // part: texture u, v, size w h d, overlay u, v (or -1), model min x, min y, min z (pixels; x to the left, z forwards)
      int[][] parts = new int[][]{
         {0, 0, 8, 8, 8, 32, 0, -4, 24, -4},                                  // head (+hat)
         {16, 16, 8, 12, 4, 16, 32, -4, 12, -2},                               // body (+jacket)
         {40, 16, 4, 12, 4, 40, 32, -8, 12, -2},                               // right arm (player's right = -x)
         {legacy ? 40 : 32, legacy ? 16 : 48, 4, 12, 4, legacy ? -1 : 48, 48, 4, 12, -2}, // left arm
         {0, 16, 4, 12, 4, 0, 32, -4, 0, -2},                                  // right leg
         {legacy ? 0 : 16, legacy ? 16 : 48, 4, 12, 4, legacy ? -1 : 0, 48, 0, 0, -2} // left leg
      };
      int[] palette = new int[]{0xFFC69C7B, 0xFF2E8FB8, 0xFFC69C7B, 0xFFC69C7B, 0xFF3A3A8C, 0xFF3A3A8C};
      for (int p = 0; p < parts.length; p++) {
         int[] part = parts[p];
         int w = part[2], h = part[3], d = part[4];
         for (int iy = 0; iy < h; iy += step) {
            for (int ix = 0; ix < w; ix += step) {
               for (int iz = 0; iz < d; iz += step) {
                  boolean surface = ix == 0 || iy == 0 || iz == 0 || ix + step >= w || iy + step >= h || iz + step >= d;
                  if (!surface) continue;
                  int c = skin == null ? palette[p] : sample(skin, part, ix, iy, iz, w, h, d, step);
                  if ((c >>> 24) < 16) c = palette[p];
                  float s = step;
                  xs.add((part[7] + ix + s * 0.5F) * PIXEL);
                  ys.add((part[8] + iy + s * 0.5F) * PIXEL);
                  zs.add((part[9] + iz + s * 0.5F) * PIXEL);
                  colors.add(0xFF000000 | c);
               }
            }
         }
      }
      return new SkinVoxels(xs, ys, zs, colors, step * PIXEL);
   }

   /** Colour of the surface cell (ix, iy, iz) of a part: the face it lies on, outer layer first. */
   private static int sample(int[] skin, int[] part, int ix, int iy, int iz, int w, int h, int d, int step) {
      int over = part[5] >= 0 ? face(skin, part[5], part[6], ix, iy, iz, w, h, d, step) : 0;
      if ((over >>> 24) >= 128) return over;
      return face(skin, part[0], part[1], ix, iy, iz, w, h, d, step);
   }

   private static int face(int[] skin, int u, int v, int ix, int iy, int iz, int w, int h, int d, int step) {
      int row = h - 1 - iy; // texture rows run top to bottom
      // which face: front/back win over the sides, the top only for the top layer of cells
      if (iy + step >= h) return px(skin, u + d + ix, v + iz);                          // top
      if (iz + step >= d) return px(skin, u + d + ix, v + d + row);                                    // front (+z)
      if (iz == 0) return px(skin, u + d + w + d + (w - 1 - ix), v + d + row);                       // back
      if (ix == 0) return px(skin, u + iz, v + d + row);                                            // player's right side
      if (ix + step >= w) return px(skin, u + d + w + (d - 1 - iz), v + d + row);                       // player's left side
      return px(skin, u + d + w + ix, v + iz);                                                      // bottom
   }

   private static int px(int[] skin, int x, int y) {
      int w = skin[0], h = skin[1];
      if (x < 0 || y < 0 || x >= w || y >= h) return 0;
      return skin[2 + y * w + x];
   }
}

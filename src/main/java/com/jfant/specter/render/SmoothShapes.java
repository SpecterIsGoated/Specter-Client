package com.jfant.specter.render;

import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.core.SpecterCore;
import com.mojang.blaze3d.platform.NativeImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2fStack;

final class SmoothShapes {
   private static final int FILL = 0;
   private static final int MASK = -1;
   private static final int WHITE = 4;
   private static final Map<SmoothShapes.Key, SmoothShapes.Tile> tiles = new HashMap<>();
   private static Identifier atlasId;
   private static DynamicTexture atlas;
   private static int atlasSize = 256;
   private static int nextId;
   private static int penX;
   private static int penY;
   private static int shelf;
   private static int tilesMade;
   private static long tilesSince;

   private SmoothShapes() {
   }

   static void fill(GuiGraphicsExtractor ctx, float left, float top, float right, float bottom, float radius, int corners, int topColor, int bottomColor) {
      SmoothShapes.Space sp = SmoothShapes.Space.of(ctx, left, top, right, bottom, radius);
      if (sp != null) {
         int w = sp.width();
         int h = sp.height();
         float r = Math.min(sp.r, Math.min(w, h) / 2.0F);
         int rc = Math.min((int)Math.ceil(r), Math.min(w, h) / 2);
         boolean flat = topColor == bottomColor || h <= 48;
         int flatColor = flat ? PurpleCard.lerp(topColor, bottomColor, 0.5F) : 0;
         SmoothShapes.Tile tile = rc > 0 ? tile(r, rc, 0) : null;
         sp.begin(ctx);
         QuadBatch b = batch(ctx);
         if (b == null) {
            sp.end(ctx);
         } else if (tile == null) {
            if (flat) {
               solid(b, sp.left, sp.top, sp.right, sp.bottom, flatColor);
            } else {
               gradient(b, sp.left, sp.top, sp.right, sp.bottom, topColor, bottomColor);
            }

            b.submit(ctx);
            sp.end(ctx);
         } else {
            int topRow = flat ? flatColor : row(topColor, bottomColor, rc / 2, h);
            int bottomRow = flat ? flatColor : row(topColor, bottomColor, h - 1 - rc / 2, h);
            quad(b, tile, corners, 1, sp.left, sp.top, 0, 0, topRow);
            quad(b, tile, corners, 2, sp.right - rc, sp.top, rc, 0, topRow);
            quad(b, tile, corners, 4, sp.left, sp.bottom - rc, 0, rc, bottomRow);
            quad(b, tile, corners, 8, sp.right - rc, sp.bottom - rc, rc, rc, bottomRow);
            if (flat) {
               if (w > 2 * rc) {
                  solid(b, sp.left + rc, sp.top, sp.right - rc, sp.bottom, flatColor);
               }

               if (h > 2 * rc) {
                  solid(b, sp.left, sp.top + rc, sp.left + rc, sp.bottom - rc, flatColor);
                  solid(b, sp.right - rc, sp.top + rc, sp.right, sp.bottom - rc, flatColor);
               }
            } else {
               if (w > 2 * rc) {
                  gradient(b, sp.left + rc, sp.top, sp.right - rc, sp.top + rc, topColor, row(topColor, bottomColor, rc - 1, h));
                  gradient(b, sp.left + rc, sp.bottom - rc, sp.right - rc, sp.bottom, row(topColor, bottomColor, h - rc, h), bottomColor);
               }

               if (h > 2 * rc) {
                  gradient(b, sp.left, sp.top + rc, sp.right, sp.bottom - rc, row(topColor, bottomColor, rc, h), row(topColor, bottomColor, h - rc - 1, h));
               }
            }

            b.submit(ctx);
            sp.end(ctx);
         }
      }
   }

   static void ring(GuiGraphicsExtractor ctx, float left, float top, float right, float bottom, float radius, int inset, int thickness, int topColor, int bottomColor) {
      SmoothShapes.Space sp = SmoothShapes.Space.of(ctx, left, top, right, bottom, radius);
      if (sp != null) {
         sp.shrink(inset);
         int w = sp.width();
         int h = sp.height();
         if (w > 2 * thickness && h > 2 * thickness) {
            float r = Math.min(sp.r, Math.min(w, h) / 2.0F);
            int rc = Math.min(Math.max((int)Math.ceil(r), thickness), Math.min(w, h) / 2);
            SmoothShapes.Tile tile = tile(r, rc, thickness);
            sp.begin(ctx);
            QuadBatch b = batch(ctx);
            if (b == null) {
               sp.end(ctx);
            } else {
               int topRow = row(topColor, bottomColor, rc / 2, h);
               int bottomRow = row(topColor, bottomColor, h - 1 - rc / 2, h);
               quad(b, tile, 15, 1, sp.left, sp.top, 0, 0, topRow);
               quad(b, tile, 15, 2, sp.right - rc, sp.top, rc, 0, topRow);
               quad(b, tile, 15, 4, sp.left, sp.bottom - rc, 0, rc, bottomRow);
               quad(b, tile, 15, 8, sp.right - rc, sp.bottom - rc, rc, rc, bottomRow);
               if (w > 2 * rc) {
                  solid(b, sp.left + rc, sp.top, sp.right - rc, sp.top + thickness, topColor);
                  solid(b, sp.left + rc, sp.bottom - thickness, sp.right - rc, sp.bottom, bottomColor);
               }

               if (h > 2 * rc) {
                  int from = row(topColor, bottomColor, rc, h);
                  int to = row(topColor, bottomColor, h - rc - 1, h);
                  if (from == to) {
                     solid(b, sp.left, sp.top + rc, sp.left + thickness, sp.bottom - rc, from);
                     solid(b, sp.right - thickness, sp.top + rc, sp.right, sp.bottom - rc, from);
                  } else {
                     gradient(b, sp.left, sp.top + rc, sp.left + thickness, sp.bottom - rc, from, to);
                     gradient(b, sp.right - thickness, sp.top + rc, sp.right, sp.bottom - rc, from, to);
                  }
               }

               b.submit(ctx);
               sp.end(ctx);
            }
         }
      }
   }

   static void mask(GuiGraphicsExtractor ctx, float left, float top, float right, float bottom, float radius, int color) {
      SmoothShapes.Space sp = SmoothShapes.Space.of(ctx, left, top, right, bottom, radius);
      if (sp != null) {
         int w = sp.width();
         int h = sp.height();
         float r = Math.min(sp.r, Math.min(w, h) / 2.0F);
         int rc = Math.min((int)Math.ceil(r), Math.min(w, h) / 2);
         if (rc > 0) {
            SmoothShapes.Tile tile = tile(r, rc, -1);
            sp.begin(ctx);
            QuadBatch b = batch(ctx);
            if (b == null) {
               sp.end(ctx);
            } else {
               quad(b, tile, 15, 1, sp.left, sp.top, 0, 0, color);
               quad(b, tile, 15, 2, sp.right - rc, sp.top, rc, 0, color);
               quad(b, tile, 15, 4, sp.left, sp.bottom - rc, 0, rc, color);
               quad(b, tile, 15, 8, sp.right - rc, sp.bottom - rc, rc, rc, color);
               b.submit(ctx);
               sp.end(ctx);
            }
         }
      }
   }

   private static void quad(QuadBatch b, SmoothShapes.Tile tile, int rounded, int which, int x, int y, int u, int v, int color) {
      int rc = tile.rc();
      if ((rounded & which) == 0) {
         if (tile.kind() == 0) {
            solid(b, x, y, x + rc, y + rc, color);
         }
      } else {
         float s = atlasSize;
         b.textured(x, y, x + rc, y + rc, (tile.u() + u) / s, (tile.v() + v) / s, (tile.u() + u + rc) / s, (tile.v() + v + rc) / s, color, color);
      }
   }

   private static void solid(QuadBatch b, int left, int top, int right, int bottom, int color) {
      gradient(b, left, top, right, bottom, color, color);
   }

   private static void gradient(QuadBatch b, int left, int top, int right, int bottom, int topColor, int bottomColor) {
      float w = 2.0F / atlasSize;
      b.textured(left, top, right, bottom, w, w, w, w, topColor, bottomColor);
   }

   private static QuadBatch batch(GuiGraphicsExtractor ctx) {
      ensureAtlas();
      return QuadBatch.textured(ctx, atlasId);
   }

   private static void ensureAtlas() {
      TextureRetirement.sweep();
      if (atlas == null) {
         newAtlas(atlasSize, List.of());
      }
   }

   private static void newAtlas(int size, List<SmoothShapes.Key> keep) {
      if (atlas != null) {
         TextureRetirement.retire(atlasId);
      }

      atlasSize = size;
      NativeImage image = new NativeImage(size, size, true);
      image.fillRect(0, 0, size, size, 0);
      image.fillRect(0, 0, 4, 4, -1);
      atlas = new DynamicTexture(() -> "specter-smooth-shapes", image);
      atlasId = Identifier.fromNamespaceAndPath("specter-client", "smooth_shapes_" + nextId++);
      SpecterCore.mc.getTextureManager().register(atlasId, atlas);
      tiles.clear();
      penX = 5;
      penY = 0;
      shelf = 4;

      for (SmoothShapes.Key key : keep) {
         place(key);
      }

      atlas.upload();
   }

   private static SmoothShapes.Tile tile(float r, int rc, int kind) {
      ensureAtlas();
      SmoothShapes.Key key = new SmoothShapes.Key(Math.round(r * 4.0F), rc, kind);
      SmoothShapes.Tile tile = tiles.get(key);
      if (tile != null) {
         return tile;
      } else if (fits(2 * rc)) {
         noteTile();
         tile = place(key);
         atlas.upload();
         return tile;
      } else {
         List<SmoothShapes.Key> keep = new ArrayList<>(tiles.keySet());
         keep.add(key);
         int size = atlasSize * 2;

         while (size < 2 * rc + 4 + 2) {
            size *= 2;
         }

         newAtlas(size, keep);
         return tiles.get(key);
      }
   }

   private static void noteTile() {
      long now = System.nanoTime();
      if (tilesSince == 0L) {
         tilesSince = now;
      }

      tilesMade++;
      if (now - tilesSince >= 5000000000L) {
         if (tilesMade > 40) {
            SpecterClientAddon.LOG.warn("Specter shapes: {} corner tiles made in the last 5 s ({} kept)", tilesMade, tiles.size());
         }

         tilesMade = 0;
         tilesSince = now;
      }
   }

   private static boolean fits(int size) {
      int x = penX;
      int y = penY;
      int s = shelf;
      if (x + size > atlasSize) {
         y += s + 1;
         int var4 = false;
      }

      return y + size <= atlasSize && size <= atlasSize;
   }

   private static SmoothShapes.Tile place(SmoothShapes.Key key) {
      int size = 2 * key.rc();
      if (penX + size > atlasSize) {
         penX = 0;
         penY = penY + shelf + 1;
         shelf = 0;
      }

      SmoothShapes.Tile tile = new SmoothShapes.Tile(penX, penY, key.rc(), key.kind());
      draw(atlas.getPixels(), tile, key.radiusQuarters() / 4.0F);
      tiles.put(key, tile);
      penX += size + 1;
      shelf = Math.max(shelf, size);
      return tile;
   }

   private static void draw(NativeImage image, SmoothShapes.Tile tile, float radius) {
      int size = 2 * tile.rc();

      for (int y = 0; y < size; y++) {
         for (int x = 0; x < size; x++) {
            if (tile.u() + x < image.getWidth() && tile.v() + y < image.getHeight()) {
               float c = cover(size, x, y, 0, radius);
               if (tile.kind() == -1) {
                  c = 1.0F - c;
               } else if (tile.kind() > 0) {
                  c -= cover(size, x, y, tile.kind(), Math.max(0.0F, radius - tile.kind()));
               }

               int alpha = Math.round(Math.clamp(c, 0.0F, 1.0F) * 255.0F);
               image.setPixel(tile.u() + x, tile.v() + y, alpha << 24 | 16777215);
            }
         }
      }
   }

   private static float cover(int size, int x, int y, int inset, float radius) {
      float half = size / 2.0F - inset;
      if (half <= 0.0F) {
         return 0.0F;
      } else {
         float r = Math.min(radius, half);
         float px = x + 0.5F - size / 2.0F;
         float py = y + 0.5F - size / 2.0F;
         float qx = Math.abs(px) - (half - r);
         float qy = Math.abs(py) - (half - r);
         float ox = Math.max(qx, 0.0F);
         float oy = Math.max(qy, 0.0F);
         float distance = (float)Math.sqrt(ox * ox + oy * oy) + Math.min(Math.max(qx, qy), 0.0F) - r;
         return Math.clamp(0.5F - distance, 0.0F, 1.0F);
      }
   }

   private static int row(int topColor, int bottomColor, int y, int h) {
      return topColor == bottomColor ? topColor : PurpleCard.lerp(topColor, bottomColor, (y + 0.5F) / h);
   }

   private record Key(int radiusQuarters, int rc, int kind) {
   }

   private static final class Space {
      int left;
      int top;
      int right;
      int bottom;
      float r;
      private final int gui;

      private Space(int left, int top, int right, int bottom, float r, int gui) {
         this.left = left;
         this.top = top;
         this.right = right;
         this.bottom = bottom;
         this.r = r;
         this.gui = gui;
      }

      static SmoothShapes.Space of(GuiGraphicsExtractor ctx, float left, float top, float right, float bottom, float radius) {
         Matrix3x2fStack m = ctx.pose();
         int gui = SpecterCore.mc.getWindow().getGuiScale();
         float scale = m.m00 * gui;
         if (scale <= 0.0F) {
            return null;
         } else {
            int l = Math.round((m.m00 * left + m.m10 * top + m.m20) * gui);
            int t = Math.round((m.m01 * left + m.m11 * top + m.m21) * gui);
            int r = Math.round((m.m00 * right + m.m10 * bottom + m.m20) * gui);
            int b = Math.round((m.m01 * right + m.m11 * bottom + m.m21) * gui);
            return r > l && b > t ? new SmoothShapes.Space(l, t, r, b, Math.max(0.0F, radius * scale), gui) : null;
         }
      }

      void shrink(int by) {
         this.left += by;
         this.top += by;
         this.right -= by;
         this.bottom -= by;
         this.r = Math.max(0.0F, this.r - by);
      }

      int width() {
         return this.right - this.left;
      }

      int height() {
         return this.bottom - this.top;
      }

      void begin(GuiGraphicsExtractor ctx) {
         Matrix3x2fStack m = ctx.pose();
         m.pushMatrix();
         m.identity();
         m.scale(1.0F / this.gui, 1.0F / this.gui);
      }

      void end(GuiGraphicsExtractor ctx) {
         ctx.pose().popMatrix();
      }
   }

   private record Tile(int u, int v, int rc, int kind) {
   }
}

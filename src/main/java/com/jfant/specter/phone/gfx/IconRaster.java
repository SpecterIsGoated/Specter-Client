package com.jfant.specter.phone.gfx;

import com.mojang.blaze3d.platform.NativeImage;

/**
 * A tiny anti-aliased vector painter for app icons, wallpapers and phone materials.
 *
 * <p>Shapes are signed distance functions in a normalised space ({@code 0..1} across the width, the height scaled by
 * the aspect ratio), so the same painter produces a crisp image at any pixel size. Coverage is analytic (distance to
 * the edge in pixels), so edges are smooth without supersampling. Layers are composited source-over in premultiplied
 * floats and converted once at the end.
 *
 * <p>Future apps describe their icon with these primitives (see {@code AppIcons}) instead of shipping image files.
 */
public final class IconRaster {
   /** Signed distance in normalised units: negative inside, positive outside. */
   @FunctionalInterface
   public interface Sdf {
      float at(float x, float y);
   }

   /** Colour (ARGB) at a normalised point. */
   @FunctionalInterface
   public interface Paint {
      int at(float x, float y);
   }

   public final int width;
   public final int height;
   /** Height in normalised units ({@code height / width}). */
   public final float aspect;
   private final float[] r;
   private final float[] g;
   private final float[] b;
   private final float[] a;

   public IconRaster(int width, int height) {
      this.width = Math.max(1, width);
      this.height = Math.max(1, height);
      this.aspect = (float)this.height / this.width;
      int n = this.width * this.height;
      this.r = new float[n];
      this.g = new float[n];
      this.b = new float[n];
      this.a = new float[n];
   }

   // ------------------------------------------------------------------------------------------------ painting

   public IconRaster fill(Sdf shape, int color) {
      return this.fill(shape, solid(color));
   }

   public IconRaster fill(Sdf shape, Paint paint) {
      return this.layer(shape, paint, 0.0F, 0.0F, 0.0F);
   }

   /** A soft copy of {@code shape} moved by (dx, dy) and blurred over {@code soft} units: drop shadows and glows. */
   public IconRaster shadow(Sdf shape, float dx, float dy, float soft, int color) {
      return this.layer(shape, solid(color), dx, dy, Math.max(soft, 1.0E-4F));
   }

   private IconRaster layer(Sdf shape, Paint paint, float dx, float dy, float soft) {
      float unit = 1.0F / this.width;
      for (int y = 0; y < this.height; y++) {
         float v = (y + 0.5F) * unit;
         for (int x = 0; x < this.width; x++) {
            float u = (x + 0.5F) * unit;
            float d = shape.at(u - dx, v - dy);
            float cover;
            if (soft > 0.0F) {
               float t = Math.clamp(0.5F - d / soft, 0.0F, 1.0F);
               cover = t * t * (3.0F - 2.0F * t);
            } else {
               cover = Math.clamp(0.5F - d / unit, 0.0F, 1.0F);
            }
            if (cover <= 0.0F) continue;
            int c = paint.at(u, v);
            float ca = (c >>> 24) / 255.0F * cover;
            if (ca <= 0.0F) continue;
            int i = y * this.width + x;
            float keep = 1.0F - ca;
            this.r[i] = (c >> 16 & 255) / 255.0F * ca + this.r[i] * keep;
            this.g[i] = (c >> 8 & 255) / 255.0F * ca + this.g[i] * keep;
            this.b[i] = (c & 255) / 255.0F * ca + this.b[i] * keep;
            this.a[i] = ca + this.a[i] * keep;
         }
      }
      return this;
   }

   /** Adds a little ordered noise to break up banding in big gradients (wallpapers). */
   public IconRaster dither(float amount) {
      for (int y = 0; y < this.height; y++) {
         for (int x = 0; x < this.width; x++) {
            int i = y * this.width + x;
            if (this.a[i] <= 0.0F) continue;
            int h = x * 73856093 ^ y * 19349663;
            h ^= h >>> 13;
            h *= 0x5bd1e995;
            float n = ((h >>> 8 & 255) / 255.0F - 0.5F) * amount * this.a[i];
            this.r[i] = Math.clamp(this.r[i] + n, 0.0F, this.a[i]);
            this.g[i] = Math.clamp(this.g[i] + n, 0.0F, this.a[i]);
            this.b[i] = Math.clamp(this.b[i] + n, 0.0F, this.a[i]);
         }
      }
      return this;
   }

   public NativeImage toImage() {
      NativeImage image = new NativeImage(this.width, this.height, false);
      for (int y = 0; y < this.height; y++) {
         for (int x = 0; x < this.width; x++) {
            image.setPixel(x, y, this.argb(x, y));
         }
      }
      return image;
   }

   /** Straight (not premultiplied) ARGB of one pixel. */
   public int argb(int x, int y) {
      int i = y * this.width + x;
      float al = this.a[i];
      if (al <= 0.0F) return 0;
      int ai = Math.round(Math.clamp(al, 0.0F, 1.0F) * 255.0F);
      int ri = Math.round(Math.clamp(this.r[i] / al, 0.0F, 1.0F) * 255.0F);
      int gi = Math.round(Math.clamp(this.g[i] / al, 0.0F, 1.0F) * 255.0F);
      int bi = Math.round(Math.clamp(this.b[i] / al, 0.0F, 1.0F) * 255.0F);
      return ai << 24 | ri << 16 | gi << 8 | bi;
   }

   // ------------------------------------------------------------------------------------------------ paints

   public static Paint solid(int color) {
      return (x, y) -> color;
   }

   public static Paint linear(float x0, float y0, float x1, float y1, int from, int to) {
      float dx = x1 - x0, dy = y1 - y0;
      float len2 = Math.max(dx * dx + dy * dy, 1.0E-6F);
      return (x, y) -> Colors.lerp(from, to, Math.clamp(((x - x0) * dx + (y - y0) * dy) / len2, 0.0F, 1.0F));
   }

   public static Paint radial(float cx, float cy, float radius, int inner, int outer) {
      return (x, y) -> {
         float d = (float)Math.sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy)) / radius;
         return Colors.lerp(inner, outer, Math.clamp(d, 0.0F, 1.0F));
      };
   }

   // ------------------------------------------------------------------------------------------------ shapes

   public static Sdf circle(float cx, float cy, float radius) {
      return (x, y) -> (float)Math.sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy)) - radius;
   }

   public static Sdf ring(float cx, float cy, float radius, float thickness) {
      float half = thickness / 2.0F;
      return (x, y) -> Math.abs((float)Math.sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy)) - radius) - half;
   }

   /** Rounded box from its centre and half size. */
   public static Sdf box(float cx, float cy, float halfW, float halfH, float radius) {
      return rotatedBox(cx, cy, halfW, halfH, radius, 0.0F);
   }

   public static Sdf rect(float x0, float y0, float x1, float y1, float radius) {
      return box((x0 + x1) / 2.0F, (y0 + y1) / 2.0F, Math.abs(x1 - x0) / 2.0F, Math.abs(y1 - y0) / 2.0F, radius);
   }

   /** Rounded box turned by {@code angle} radians (clockwise on screen). */
   public static Sdf rotatedBox(float cx, float cy, float halfW, float halfH, float radius, float angle) {
      float c = (float)Math.cos(angle), s = (float)Math.sin(angle);
      float r = Math.min(radius, Math.min(halfW, halfH));
      return (x, y) -> {
         float px = x - cx, py = y - cy;
         float lx = Math.abs(px * c + py * s), ly = Math.abs(-px * s + py * c);
         float qx = lx - halfW + r, qy = ly - halfH + r;
         float ox = Math.max(qx, 0.0F), oy = Math.max(qy, 0.0F);
         return (float)Math.sqrt(ox * ox + oy * oy) + Math.min(Math.max(qx, qy), 0.0F) - r;
      };
   }

   /** A line from a to b with round ends, {@code radius} thick on each side. */
   public static Sdf capsule(float ax, float ay, float bx, float by, float radius) {
      float ex = bx - ax, ey = by - ay;
      float len2 = Math.max(ex * ex + ey * ey, 1.0E-9F);
      return (x, y) -> {
         float wx = x - ax, wy = y - ay;
         float t = Math.clamp((wx * ex + wy * ey) / len2, 0.0F, 1.0F);
         float dx = wx - ex * t, dy = wy - ey * t;
         return (float)Math.sqrt(dx * dx + dy * dy) - radius;
      };
   }

   /** Closed polygon from x,y pairs (any winding, may be concave). */
   public static Sdf polygon(float... xy) {
      int n = xy.length / 2;
      return (x, y) -> {
         float d = (x - xy[0]) * (x - xy[0]) + (y - xy[1]) * (y - xy[1]);
         float sign = 1.0F;
         for (int i = 0, j = n - 1; i < n; j = i, i++) {
            float vix = xy[i * 2], viy = xy[i * 2 + 1], vjx = xy[j * 2], vjy = xy[j * 2 + 1];
            float ex = vjx - vix, ey = vjy - viy;
            float wx = x - vix, wy = y - viy;
            float t = Math.clamp((wx * ex + wy * ey) / Math.max(ex * ex + ey * ey, 1.0E-9F), 0.0F, 1.0F);
            float bx = wx - ex * t, by = wy - ey * t;
            d = Math.min(d, bx * bx + by * by);
            boolean c1 = y >= viy, c2 = y < vjy, c3 = ex * wy > ey * wx;
            if (c1 && c2 && c3 || !c1 && !c2 && !c3) sign = -sign;
         }
         return sign * (float)Math.sqrt(d);
      };
   }

   /**
    * Part of a ring with round ends, from angle {@code from} to {@code to} (radians, 0 = right, growing clockwise on
    * screen).
    */
   public static Sdf arc(float cx, float cy, float radius, float thickness, float from, float to) {
      float half = thickness / 2.0F;
      float span = to - from;
      float ax = cx + (float)Math.cos(from) * radius, ay = cy + (float)Math.sin(from) * radius;
      float bx = cx + (float)Math.cos(to) * radius, by = cy + (float)Math.sin(to) * radius;
      return (x, y) -> {
         float ang = (float)Math.atan2(y - cy, x - cx) - from;
         ang = (float)(ang - Math.floor(ang / (Math.PI * 2)) * Math.PI * 2);
         if (ang <= span) {
            return Math.abs((float)Math.sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy)) - radius) - half;
         }
         float da = (float)Math.sqrt((x - ax) * (x - ax) + (y - ay) * (y - ay));
         float db = (float)Math.sqrt((x - bx) * (x - bx) + (y - by) * (y - by));
         return Math.min(da, db) - half;
      };
   }

   public static Sdf union(Sdf... parts) {
      return (x, y) -> {
         float d = Float.MAX_VALUE;
         for (Sdf p : parts) d = Math.min(d, p.at(x, y));
         return d;
      };
   }

   public static Sdf subtract(Sdf base, Sdf cut) {
      return (x, y) -> Math.max(base.at(x, y), -cut.at(x, y));
   }

   public static Sdf intersect(Sdf a, Sdf b) {
      return (x, y) -> Math.max(a.at(x, y), b.at(x, y));
   }

   /** Grows a shape by {@code by} units (negative shrinks), which also rounds its corners. */
   public static Sdf grow(Sdf shape, float by) {
      return (x, y) -> shape.at(x, y) - by;
   }
}

package com.jfant.specter.ping;

/** Pure maths for the Ping System (easing, fading, screen-edge pinning, labels). No Minecraft classes. */
public final class PingMath {
   private PingMath() {
   }

   public static float clamp01(float v) {
      return v < 0.0F ? 0.0F : Math.min(v, 1.0F);
   }

   public static float easeOutCubic(float t) {
      float u = 1.0F - clamp01(t);
      return 1.0F - u * u * u;
   }

   /** Overshoots to about 1.1 and settles at 1 - the "pop" when a marker lands. */
   public static float easeOutBack(float t) {
      t = clamp01(t);
      float c1 = 1.70158F;
      float c3 = c1 + 1.0F;
      float u = t - 1.0F;
      return 1.0F + c3 * u * u * u + c1 * u * u;
   }

   /** 1 while the ping is fresh, falling to 0 over the last {@code fadeMs} of its life. */
   public static float fade(long age, long life, long fadeMs) {
      if (age >= life) {
         return 0.0F;
      }

      long left = life - age;
      return left >= fadeMs ? 1.0F : clamp01((float)left / fadeMs);
   }

   public static double distSq(double ax, double ay, double az, double bx, double by, double bz) {
      double dx = ax - bx;
      double dy = ay - by;
      double dz = az - bz;
      return dx * dx + dy * dy + dz * dz;
   }

   public static boolean near(double ax, double ay, double az, double bx, double by, double bz, double radius) {
      return distSq(ax, ay, az, bx, by, bz) <= radius * radius;
   }

   public static boolean inside(double x, double y, double width, double height, double margin) {
      return x >= margin && x <= width - margin && y >= margin && y <= height - margin;
   }

   /**
    * Pins a point that is off screen to the screen edge (inset by {@code margin}) along the line from the
    * screen centre, so the marker points the way to the ping. Writes x, y into {@code out}.
    */
   public static void clampToEdge(double x, double y, double width, double height, double margin, double[] out) {
      double cx = width / 2.0;
      double cy = height / 2.0;
      double dx = x - cx;
      double dy = y - cy;
      double halfW = Math.max(1.0, cx - margin);
      double halfH = Math.max(1.0, cy - margin);
      if (Math.abs(dx) < 1.0E-6 && Math.abs(dy) < 1.0E-6) {
         // Exactly behind the camera: show it at the bottom middle.
         out[0] = cx;
         out[1] = cy + halfH;
         return;
      }

      double t = Math.min(Math.abs(dx) < 1.0E-9 ? Double.MAX_VALUE : halfW / Math.abs(dx), Math.abs(dy) < 1.0E-9 ? Double.MAX_VALUE : halfH / Math.abs(dy));
      out[0] = cx + dx * t;
      out[1] = cy + dy * t;
   }

   public static int argb(int a, int r, int g, int b) {
      a = Math.max(0, Math.min(255, a));
      return a << 24 | (r & 0xFF) << 16 | (g & 0xFF) << 8 | b & 0xFF;
   }

   /**
    * World size of the ping gem at {@code dist} blocks from the camera. It grows with distance, but slower than
    * distance does, so the gem looks smaller the farther away it is (a depth cue) while staying visible from afar.
    */
   public static double gemSize(double dist) {
      return 0.28 + Math.max(0.0, dist) * 0.016;
   }

   /**
    * Brightness of a face with unit normal (nx, ny, nz) under a light from direction (lx, ly, lz): a dim ambient part so
    * faces turned away are dark but not black, plus a lit part that can brighten the colour a little.
    */
   public static float shade(double nx, double ny, double nz, double lx, double ly, double lz) {
      double len = Math.sqrt(lx * lx + ly * ly + lz * lz);
      double d = len < 1.0E-9 ? 0.0 : (nx * lx + ny * ly + nz * lz) / len;
      return (float)(0.42 + 0.78 * Math.max(0.0, d));
   }
}

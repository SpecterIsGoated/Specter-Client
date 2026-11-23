package com.jfant.specter.phone.gfx;

/** ARGB colour helpers shared by the phone's painters and UI. */
public final class Colors {
   private Colors() {
   }

   public static int argb(int a, int r, int g, int b) {
      return (a & 255) << 24 | (r & 255) << 16 | (g & 255) << 8 | b & 255;
   }

   public static int rgb(int rgb) {
      return 0xFF000000 | rgb;
   }

   public static int alpha(int color, float alpha) {
      int a = Math.round((color >>> 24) * Math.clamp(alpha, 0.0F, 1.0F));
      return a << 24 | color & 0xFFFFFF;
   }

   public static int withAlpha(int color, int alpha) {
      return (alpha & 255) << 24 | color & 0xFFFFFF;
   }

   public static int lerp(int from, int to, float t) {
      t = Math.clamp(t, 0.0F, 1.0F);
      int a = Math.round((from >>> 24) + ((to >>> 24) - (from >>> 24)) * t);
      int r = Math.round((from >> 16 & 255) + ((to >> 16 & 255) - (from >> 16 & 255)) * t);
      int g = Math.round((from >> 8 & 255) + ((to >> 8 & 255) - (from >> 8 & 255)) * t);
      int b = Math.round((from & 255) + ((to & 255) - (from & 255)) * t);
      return a << 24 | r << 16 | g << 8 | b;
   }

   /** Multiplies the RGB channels (not alpha) by {@code k}. */
   public static int shade(int color, float k) {
      int r = Math.min(255, Math.round((color >> 16 & 255) * k));
      int g = Math.min(255, Math.round((color >> 8 & 255) * k));
      int b = Math.min(255, Math.round((color & 255) * k));
      return color & 0xFF000000 | r << 16 | g << 8 | b;
   }

   /** HSV (all 0..1) to opaque ARGB. */
   public static int hsv(float h, float s, float v) {
      h = (h - (float)Math.floor(h)) * 6.0F;
      int sector = (int)h;
      float f = h - sector;
      float p = v * (1.0F - s), q = v * (1.0F - s * f), t = v * (1.0F - s * (1.0F - f));
      float r, g, b;
      switch (sector) {
         case 0 -> { r = v; g = t; b = p; }
         case 1 -> { r = q; g = v; b = p; }
         case 2 -> { r = p; g = v; b = t; }
         case 3 -> { r = p; g = q; b = v; }
         case 4 -> { r = t; g = p; b = v; }
         default -> { r = v; g = p; b = q; }
      }
      return argb(255, Math.round(r * 255), Math.round(g * 255), Math.round(b * 255));
   }

   /** Relative brightness 0..1, for picking readable text on a colour. */
   public static float luma(int color) {
      return ((color >> 16 & 255) * 0.299F + (color >> 8 & 255) * 0.587F + (color & 255) * 0.114F) / 255.0F;
   }
}

package com.jfant.specter.phone.core;

import com.jfant.specter.phone.gfx.Colors;
import com.jfant.specter.phone.gfx.IconRaster;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;

/**
 * Painted wallpapers. Each paints into a raster whose width is 1 unit (height = aspect), so it is sharp at whatever
 * size the phone is on screen. The same texture is shown on the 3D phone and behind the home screen, so the moment
 * the interface fades in over the 3D screen is seamless.
 */
public final class Wallpapers {
   public record Wallpaper(String id, String name, int swatchTop, int swatchBottom, Consumer<IconRaster> painter) {
   }

   private static final Map<String, Wallpaper> ALL = new LinkedHashMap<>();
   private static final IconRaster.Sdf EVERYWHERE = (x, y) -> -1.0F;

   static {
      add(new Wallpaper("aurora", "Aurora", 0xFF1B0F3A, 0xFF071A24, r -> {
         r.fill(EVERYWHERE, IconRaster.linear(0, 0, 0, r.aspect, 0xFF150B2E, 0xFF04030A));
         glow(r, 0.15F, 0.38F, 0.75F, 0xA07C3AED);
         glow(r, 0.95F, 0.85F, 0.80F, 0x800EA5A4);
         glow(r, 0.35F, 1.70F, 0.70F, 0x70DB2777);
         glow(r, 0.80F, 0.10F, 0.45F, 0x504F46E5);
         ribbon(r, 0.62F, 0x3322D3EE);
         r.dither(0.018F);
      }));
      add(new Wallpaper("sunset", "Sunset", 0xFF2A1050, 0xFFFF7A45, r -> {
         r.fill(EVERYWHERE, IconRaster.linear(0, 0, 0, r.aspect, 0xFF241046, 0xFFFF7444));
         glow(r, 0.70F, r.aspect * 0.66F, 0.55F, 0xD0FFC46B);
         glow(r, 0.15F, r.aspect * 0.35F, 0.80F, 0x70FF4F8B);
         glow(r, 0.90F, 0.10F, 0.60F, 0x505B21B6);
         r.fill(IconRaster.circle(0.70F, r.aspect * 0.66F, 0.16F), IconRaster.radial(0.70F, r.aspect * 0.62F, 0.2F, 0xFFFFF4D6, 0xFFFFB25C));
         hills(r, r.aspect * 0.80F, 0xE0190A2E, 1);
         hills(r, r.aspect * 0.88F, 0xFF0E0618, 2);
         r.dither(0.02F);
      }));
      add(new Wallpaper("ocean", "Lagoon", 0xFF0B3D91, 0xFF34D1BF, r -> {
         r.fill(EVERYWHERE, IconRaster.linear(0, 0, 0, r.aspect, 0xFF0A3A8C, 0xFF31CDBB));
         glow(r, 0.25F, 0.45F, 0.60F, 0x608BE9FD);
         glow(r, 0.85F, r.aspect * 0.80F, 0.70F, 0x601E3A8A);
         glow(r, 0.60F, 0.05F, 0.50F, 0x40FFFFFF);
         for (int i = 0; i < 5; i++) {
            float y = r.aspect * (0.42F + i * 0.11F);
            waves(r, y, 0x18FFFFFF, i);
         }
         r.dither(0.016F);
      }));
      add(new Wallpaper("donut", "Donut", 0xFF3B0F2E, 0xFFFF6FB5, r -> {
         r.fill(EVERYWHERE, IconRaster.linear(0, 0, 0, r.aspect, 0xFF3A0E2D, 0xFF0F050C));
         glow(r, 0.20F, 0.30F, 0.70F, 0x70FF5CA8);
         glow(r, 0.90F, r.aspect * 0.75F, 0.80F, 0x60A855F7);
         float cx = 0.62F, cy = r.aspect * 0.70F;
         r.shadow(IconRaster.ring(cx, cy, 0.34F, 0.36F), 0.0F, 0.025F, 0.08F, 0x90000000);
         r.fill(IconRaster.ring(cx, cy, 0.34F, 0.36F), IconRaster.linear(cx, cy - 0.5F, cx, cy + 0.5F, 0xFFE9B47C, 0xFFB7743A));
         r.fill(IconRaster.ring(cx, cy - 0.012F, 0.34F, 0.30F), IconRaster.linear(cx - 0.4F, cy - 0.4F, cx + 0.4F, cy + 0.4F, 0xFFFF8CC6, 0xFFE0408F));
         sprinkles(r, cx, cy - 0.012F, 0.34F, 0.13F, 70, 7L);
         r.fill(IconRaster.ring(cx - 0.05F, cy - 0.09F, 0.30F, 0.03F), 0x30FFFFFF);
         r.dither(0.02F);
      }));
      add(new Wallpaper("graphite", "Graphite", 0xFF1C1C22, 0xFF0A0A0D, r -> {
         r.fill(EVERYWHERE, IconRaster.linear(0, 0, 0, r.aspect, 0xFF1D1D25, 0xFF09090C));
         glow(r, 0.0F, 0.0F, 1.1F, 0x30FFFFFF);
         r.fill(IconRaster.rotatedBox(0.5F, r.aspect * 0.5F, 0.06F, r.aspect, 0, -0.5F), 0x0CFFFFFF);
         r.fill(IconRaster.rotatedBox(0.66F, r.aspect * 0.5F, 0.02F, r.aspect, 0, -0.5F), 0x08FFFFFF);
         r.dither(0.012F);
      }));
   }

   private Wallpapers() {
   }

   private static void add(Wallpaper w) {
      ALL.put(w.id(), w);
   }

   public static Iterable<Wallpaper> all() {
      return ALL.values();
   }

   public static Wallpaper get(String id) {
      Wallpaper w = ALL.get(id);
      return w != null ? w : ALL.get("aurora");
   }

   /** A soft radial light. */
   private static void glow(IconRaster r, float cx, float cy, float radius, int color) {
      r.fill(EVERYWHERE, IconRaster.radial(cx, cy, radius, color, color & 0x00FFFFFF));
   }

   private static void ribbon(IconRaster r, float y0, int color) {
      IconRaster.Sdf band = (x, y) -> {
         float centre = y0 + 0.18F * (float)Math.sin(x * 4.2F + 0.6F) + 0.25F * x;
         return Math.abs(y - centre) - 0.035F - 0.03F * (float)Math.sin(x * 9.0F);
      };
      r.shadow(band, 0, 0, 0.12F, color);
   }

   private static void hills(IconRaster r, float base, int color, int seed) {
      r.fill((x, y) -> {
         float h = base - 0.04F * (float)Math.sin(x * (5.0F + seed) + seed) - 0.025F * (float)Math.sin(x * 13.0F + seed * 2);
         return h - y;
      }, color);
   }

   private static void waves(IconRaster r, float y0, int color, int i) {
      r.fill((x, y) -> Math.abs(y - (y0 + 0.012F * (float)Math.sin(x * 18.0F + i * 1.3F))) - 0.0035F, color);
   }

   /** Little capsules scattered over a ring (the donut's frosting). */
   static void sprinkles(IconRaster r, float cx, float cy, float radius, float spread, int count, long seed) {
      Random rnd = new Random(seed);
      int[] colors = {0xFFFFFFFF, 0xFF7DD3FC, 0xFFFDE047, 0xFF86EFAC, 0xFFC4B5FD, 0xFFFB923C};
      for (int i = 0; i < count; i++) {
         double a = rnd.nextDouble() * Math.PI * 2;
         float d = radius + (rnd.nextFloat() - 0.5F) * spread * 2.0F;
         float x = cx + (float)Math.cos(a) * d, y = cy + (float)Math.sin(a) * d;
         float turn = rnd.nextFloat() * (float)Math.PI;
         float len = 0.018F, th = 0.0075F;
         float dx = (float)Math.cos(turn) * len, dy = (float)Math.sin(turn) * len;
         r.fill(IconRaster.capsule(x - dx, y - dy, x + dx, y + dy, th), colors[rnd.nextInt(colors.length)]);
      }
   }

   /** Average colour, for the 3D screen while the texture is still painting. */
   public static int swatch(String id) {
      Wallpaper w = get(id);
      return Colors.lerp(w.swatchTop(), w.swatchBottom(), 0.5F);
   }
}

package com.jfant.specter.gui;

import com.jfant.specter.core.gui.renderer.GuiRenderer;
import com.jfant.specter.core.utils.render.color.Color;

public final class Glass {
   public static final Color PANE_TOP = argb(-1438901396);
   public static final Color PANE_BOTTOM = argb(-1205399476);
   public static final Color FRAME = argb(-662749262);
   public static final Color FRAME_INNER = argb(1355258869);
   public static final Color PURPLE = new Color(127, 63, 178, 235);
   public static final Color PINK = new Color(255, 110, 199, 235);
   public static final Color HEADER_DARK = new Color(52, 22, 92, 235);
   private static final Color glint = new Color();
   private static final Color spark = new Color();
   private static final Color headLeft = new Color();
   private static final Color headRight = new Color();

   private Glass() {
   }

   public static void pane(GuiRenderer r, double x, double y, double w, double h, double s, boolean top) {
      r.quad(x, y, w, h, PANE_TOP, PANE_TOP, PANE_BOTTOM, PANE_BOTTOM);
      frame(r, x, y, w, h, s, top);
      glints(r, x, y, w, h, s);
   }

   public static void frame(GuiRenderer r, double x, double y, double w, double h, double s, boolean top) {
      double side = top ? s : 0.0;
      if (top) {
         r.quad(x + s, y, w - s * 2.0, s, FRAME);
      }

      r.quad(x + s, y + h - s, w - s * 2.0, s, FRAME);
      r.quad(x, y + side, s, h - s - side, FRAME);
      r.quad(x + w - s, y + side, s, h - s - side, FRAME);
      if (top) {
         r.quad(x + s, y + s, w - s * 2.0, s, FRAME_INNER);
      }

      r.quad(x + s, y + side * 2.0, s, h - s * 2.0 - side * 2.0 + (top ? 0.0 : s), FRAME_INNER);
   }

   public static void glints(GuiRenderer r, double x, double y, double w, double h, double s) {
      if (!(w < s * 16.0) && !(h < s * 10.0)) {
         double beat = System.currentTimeMillis() % 5200L / 5200.0;
         int a = (int)(153.0 * (0.55 + 0.45 * Math.sin(beat * 3.141592653589793 * 2.0)));
         glint.set(201, 162, 247, Math.max(0, Math.min(255, a)));

         for (int i = 0; i < 3; i++) {
            r.quad(x + s * (3 + i), y + s * (5 - i), s, s, glint);
            r.quad(x + w - s * (4 + i), y + h - s * (4 + (2 - i)), s, s, glint);
         }
      }
   }

   public static void header(GuiRenderer r, double x, double y, double w, double h, double s, int seed) {
      double phase = System.currentTimeMillis() % 7000L / 7000.0 * 3.141592653589793 * 2.0;
      double t = 0.5 + 0.5 * Math.sin(phase);
      lerp(PURPLE, PINK, t, headLeft);
      lerp(PURPLE, PINK, 1.0 - t, headRight);
      headLeft.a = headRight.a = 150;
      r.quad(x, y, w, h, HEADER_DARK);
      r.quad(x, y, w, h, headLeft, headRight, headRight, headLeft);
      r.quad(x + s, y, w - s * 2.0, s, FRAME);
      r.quad(x + s, y + s, w - s * 2.0, s, FRAME_INNER);
      r.quad(x, y + s, s, h - s, FRAME);
      r.quad(x + w - s, y + s, s, h - s, FRAME);
      r.quad(x + s, y + h - s, w - s * 2.0, s, FRAME);
      sparkles(r, x, y, w, h, s, seed);
   }

   public static void sparkles(GuiRenderer r, double x, double y, double w, double h, double s, int seed) {
      int count = Math.max(1, Math.min(4, (int)(w / (s * 60.0))));
      long now = System.currentTimeMillis();

      for (int i = 0; i < count; i++) {
         int bits = scramble(seed * 31 + i);
         long period = 2800 + Math.floorMod(bits, 3700);
         double age = Math.floorMod(now + (bits >>> 12), period) / 600.0;
         if (!(age >= 1.0)) {
            double k = Math.sin(age * 3.141592653589793);
            double cx = x + s * 4.0 + Math.floor((bits >>> 4 & 0xFF) / 255.0 * Math.max(0.0, w / s - 8.0)) * s;
            double cy = y + s * 2.0 + Math.floor((bits >>> 20 & 15) / 15.0 * Math.max(0.0, h / s - 4.0)) * s;
            spark.set(245, 230, 255, (int)(230.0 * k));
            r.quad(cx, cy, s, s, spark);
            double arm = Math.round(k * 2.0) * s;
            if (arm > 0.0) {
               spark.a = (int)(150.0 * k);
               r.quad(cx - arm, cy, arm, s, spark);
               r.quad(cx + s, cy, arm, s, spark);
               r.quad(cx, cy - arm, s, arm, spark);
               r.quad(cx, cy + s, s, arm, spark);
            }
         }
      }
   }

   public static void lerp(Color from, Color to, double t, Color into) {
      into.set(
         (int)Math.round(from.r + (to.r - from.r) * t),
         (int)Math.round(from.g + (to.g - from.g) * t),
         (int)Math.round(from.b + (to.b - from.b) * t),
         (int)Math.round(from.a + (to.a - from.a) * t)
      );
   }

   public static Color argb(int v) {
      return new Color(v >> 16 & 0xFF, v >> 8 & 0xFF, v & 0xFF, v >>> 24);
   }

   private static int scramble(int v) {
      v ^= v >>> 16;
      v *= 2146121005;
      v ^= v >>> 15;
      v *= -2073254261;
      return v ^ v >>> 16;
   }
}

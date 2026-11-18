package com.jfant.specter.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Util;

public final class PurpleCard {
   public static final int BACKGROUND_TOP = -1438901396;
   public static final int BACKGROUND_BOTTOM = -1205399476;
   public static final int FRAME = -662749262;
   public static final int FRAME_INNER = 1355258869;
   public static final int GLARE = -1714838793;
   private static final int GLARE_ALPHA = 160;
   private static final int GLARE_SWING = 72;
   private static final long SHIMMER_MS = 3200L;
   private static final int SPARKLE = 16182527;
   private static final int SPARKLE_SPACING = 110;
   private static final long SPARKLE_FLARE_MS = 900L;
   private static final long SPARKLE_MIN_MS = 2800L;
   private static final long SPARKLE_MAX_MS = 6500L;
   public static final int TITLE = -1065473;
   public static final int LABEL = -1911561;
   public static final int VALUE = -1;
   public static final int DIM = -4149795;
   public static final int DIVIDER = 1440926975;
   public static final int INSET = -2145973978;
   public static final int INSET_EDGE = -1333837902;
   private static final int CLEAN_FRAME = -1197843504;
   private static final int CLEAN_SHEEN = 385875967;
   public static final int RADIUS = 4;
   public static final int TOP_LEFT = 1;
   public static final int TOP_RIGHT = 2;
   public static final int BOTTOM_LEFT = 4;
   public static final int BOTTOM_RIGHT = 8;
   public static final int ALL = 15;
   private static final int TEXEL = 2;
   private static QuadBatch open;

   private PurpleCard() {
   }

   public static void card(GuiGraphicsExtractor ctx, int left, int top, int right, int bottom) {
      long started = GuiProfile.shapeStart();
      QuadBatch mine = openBatch(ctx);

      try {
         cardShape(ctx, left, top, right, bottom);
      } finally {
         closeBatch(ctx, mine);
         GuiProfile.shapeEnd(started);
      }
   }

   private static void cardShape(GuiGraphicsExtractor ctx, int left, int top, int right, int bottom) {
      if (Theme.clean()) {
         cleanGlass(ctx, left, top, right, bottom);
      } else {
         fillRounded(ctx, left, top, right, bottom, 4, 15, -1438901396, -1205399476);
         strokeRounded(ctx, left, top, right, bottom, 4, -662749262, -662749262);
         strokeRounded(ctx, left + 1, top + 1, right - 1, bottom - 1, 3, 1355258869, 1355258869);
         long now = Util.getMillis();
         int first = shimmer(now, 0);
         int second = shimmer(now, 1);
         glint(ctx, left + 8, top + 4, first);
         glint(ctx, left + 6, top + 6, first);
         glint(ctx, left + 4, top + 8, first);
         glint(ctx, right - 6, bottom - 8, second);
         glint(ctx, right - 8, bottom - 6, second);
         int edges = 2 * (right - left + bottom - top);
         sparkles(ctx, left, top, right, bottom, Math.clamp((long)(edges / 110), 2, 6), 2, now);
      }
   }

   public static void bubble(GuiGraphicsExtractor ctx, int left, int top, int right, int bottom) {
      long started = GuiProfile.shapeStart();
      QuadBatch mine = openBatch(ctx);

      try {
         bubbleShape(ctx, left, top, right, bottom);
      } finally {
         closeBatch(ctx, mine);
         GuiProfile.shapeEnd(started);
      }
   }

   private static void bubbleShape(GuiGraphicsExtractor ctx, int left, int top, int right, int bottom) {
      if (Theme.clean()) {
         cleanGlass(ctx, left, top, right, bottom);
      } else {
         fillRounded(ctx, left, top, right, bottom, 4, 15, -1438901396, -1205399476);
         strokeRounded(ctx, left, top, right, bottom, 4, -662749262, -662749262);
         strokeRounded(ctx, left + 1, top + 1, right - 1, bottom - 1, 3, 1355258869, 1355258869);
         long now = Util.getMillis();
         int first = shimmer(now, 0);
         int second = shimmer(now, 1);
         rect(ctx, left + 4, top + 2, left + 5, top + 3, first);
         rect(ctx, left + 3, top + 3, left + 4, top + 4, first);
         rect(ctx, left + 2, top + 4, left + 3, top + 5, first);
         rect(ctx, right - 3, bottom - 4, right - 2, bottom - 3, second);
         rect(ctx, right - 4, bottom - 3, right - 3, bottom - 2, second);
         sparkles(ctx, left, top, right, bottom, 1, 1, now);
      }
   }

   private static void cleanGlass(GuiGraphicsExtractor ctx, int left, int top, int right, int bottom) {
      int line = cleanLine(ctx);
      SmoothShapes.fill(ctx, left, top, right, bottom, 4.0F, 15, -1438901396, -1205399476);
      SmoothShapes.fill(ctx, left, top, right, top + (bottom - top) * 0.5F, 4.0F, 3, 385875967, 16777215);
      SmoothShapes.ring(ctx, left, top, right, bottom, 4.0F, 0, line, -1197843504, -1197843504);
      SmoothShapes.ring(ctx, left, top, right, bottom, 4.0F, line, 1, 1355258869, 281517045);
   }

   private static int cleanLine(GuiGraphicsExtractor ctx) {
      float scale = ctx.pose().m00 * Minecraft.getInstance().getWindow().getGuiScale();
      return Math.max(1, Math.round(scale * 0.5F));
   }

   private static void glint(GuiGraphicsExtractor ctx, int x, int y, int color) {
      rect(ctx, x, y, x + 2, y + 2, color);
   }

   private static int shimmer(long now, int corner) {
      double wave = Math.sin(6.283185307179586 * (now % 3200L) / 3200.0 + corner * 2.4);
      int alpha = (int)(160.0 + 72.0 * wave);
      return alpha << 24 | 13214455;
   }

   private static void sparkles(GuiGraphicsExtractor ctx, int left, int top, int right, int bottom, int count, int reach, long now) {
      int width = right - left;
      int height = bottom - top;
      int margin = reach + 2;
      int across = width - 2 * margin;
      int down = height - 2 * margin;
      if (across >= 1 && down >= 1) {
         int seed = width * 73856093 ^ height * 19349663;

         for (int i = 0; i < count; i++) {
            int h = mix(seed + i * 83492791);
            long period = 2800L + (h >>> 16 & 0xFF) * 3700L / 255L;
            long clock = now + (mix(h) & 2147483647) % period;
            long t = clock % period;
            if (t < 900L) {
               float k = (float)Math.sin(3.141592653589793 * t / 900.0);
               int spot = mix(h ^ (int)(clock / period) * -1640531527);
               int x = left + margin + (spot & 65535) % across;
               int y = top + margin + (spot >>> 16) % down;
               star(ctx, x, y, k, reach);
            }
         }
      }
   }

   private static void star(GuiGraphicsExtractor ctx, int x, int y, float k, int reach) {
      rect(ctx, x, y, x + 1, y + 1, sparkle(k));
      int arm = sparkle(k * 0.6F);
      rect(ctx, x - 1, y, x, y + 1, arm);
      rect(ctx, x + 1, y, x + 2, y + 1, arm);
      rect(ctx, x, y - 1, x + 1, y, arm);
      rect(ctx, x, y + 1, x + 1, y + 2, arm);
      if (reach >= 2 && !(k < 0.55F)) {
         int tip = sparkle((k - 0.55F) / 0.45F * 0.35F);
         rect(ctx, x - 2, y, x - 1, y + 1, tip);
         rect(ctx, x + 2, y, x + 3, y + 1, tip);
         rect(ctx, x, y - 2, x + 1, y - 1, tip);
         rect(ctx, x, y + 2, x + 1, y + 3, tip);
      }
   }

   private static int sparkle(float k) {
      return Math.round(Math.clamp(k, 0.0F, 1.0F) * 255.0F) << 24 | 16182527;
   }

   private static int mix(int h) {
      h ^= h >>> 16;
      h *= 2146121005;
      h ^= h >>> 15;
      h *= -2073254261;
      return h ^ h >>> 16;
   }

   public static void fillRounded(GuiGraphicsExtractor ctx, int left, int top, int right, int bottom, int radius, int corners, int topColor, int bottomColor) {
      long started = GuiProfile.shapeStart();
      QuadBatch mine = openBatch(ctx);

      try {
         fillRoundedShape(ctx, left, top, right, bottom, radius, corners, topColor, bottomColor);
      } finally {
         closeBatch(ctx, mine);
         GuiProfile.shapeEnd(started);
      }
   }

   private static void fillRoundedShape(GuiGraphicsExtractor ctx, int left, int top, int right, int bottom, int radius, int corners, int topColor, int bottomColor) {
      if (Theme.clean()) {
         SmoothShapes.fill(ctx, left, top, right, bottom, radius, corners, topColor, bottomColor);
      } else {
         int height = bottom - top;
         if (right > left && height > 0) {
            int r = Math.min(radius, Math.min(right - left, height) / 2);

            for (int i = 0; i < r; i++) {
               int cut = inset(r, i);
               rect(
                  ctx,
                  left + ((corners & 1) != 0 ? cut : 0),
                  top + i,
                  right - ((corners & 2) != 0 ? cut : 0),
                  top + i + 1,
                  rowColor(topColor, bottomColor, i, height)
               );
               int row = height - 1 - i;
               rect(
                  ctx,
                  left + ((corners & 4) != 0 ? cut : 0),
                  top + row,
                  right - ((corners & 8) != 0 ? cut : 0),
                  top + row + 1,
                  rowColor(topColor, bottomColor, row, height)
               );
            }

            if (height > 2 * r) {
               grad(
                  ctx,
                  left,
                  top + r,
                  right,
                  bottom - r,
                  lerp(topColor, bottomColor, (float)r / height),
                  lerp(topColor, bottomColor, (float)(height - r) / height)
               );
            }
         }
      }
   }

   public static void strokeRounded(GuiGraphicsExtractor ctx, int left, int top, int right, int bottom, int radius, int topColor, int bottomColor) {
      long started = GuiProfile.shapeStart();
      QuadBatch mine = openBatch(ctx);

      try {
         strokeRoundedShape(ctx, left, top, right, bottom, radius, topColor, bottomColor);
      } finally {
         closeBatch(ctx, mine);
         GuiProfile.shapeEnd(started);
      }
   }

   private static void strokeRoundedShape(GuiGraphicsExtractor ctx, int left, int top, int right, int bottom, int radius, int topColor, int bottomColor) {
      if (Theme.clean()) {
         SmoothShapes.ring(ctx, left, top, right, bottom, radius, 0, cleanLine(ctx), topColor, bottomColor);
      } else {
         int width = right - left;
         int height = bottom - top;
         if (width > 2 && height > 2) {
            int r = Math.min(radius, Math.min(width, height) / 2);
            int straight = Math.max(r, 1);

            for (int i = 0; i < height; i++) {
               if (i < straight || i >= height - straight) {
                  int color = rowColor(topColor, bottomColor, i, height);
                  int outer = rowInset(r, i, height);
                  if (i != 0 && i != height - 1) {
                     int inner = Math.max(outer + 1, rowInset(r - 1, i - 1, height - 2) + 1);
                     rect(ctx, left + outer, top + i, left + inner, top + i + 1, color);
                     rect(ctx, right - inner, top + i, right - outer, top + i + 1, color);
                  } else {
                     rect(ctx, left + outer, top + i, right - outer, top + i + 1, color);
                  }
               }
            }

            if (height > 2 * straight) {
               int from = lerp(topColor, bottomColor, (float)straight / height);
               int to = lerp(topColor, bottomColor, (float)(height - straight) / height);
               grad(ctx, left, top + straight, left + 1, bottom - straight, from, to);
               grad(ctx, right - 1, top + straight, right, bottom - straight, from, to);
            }
         }
      }
   }

   public static void maskCorners(GuiGraphicsExtractor ctx, int left, int top, int right, int bottom, int radius, int color) {
      QuadBatch mine = openBatch(ctx);

      try {
         maskCornersShape(ctx, left, top, right, bottom, radius, color);
      } finally {
         closeBatch(ctx, mine);
      }
   }

   private static void maskCornersShape(GuiGraphicsExtractor ctx, int left, int top, int right, int bottom, int radius, int color) {
      if (Theme.clean()) {
         SmoothShapes.mask(ctx, left, top, right, bottom, radius, color);
      } else {
         int r = Math.min(radius, Math.min(right - left, bottom - top) / 2);

         for (int i = 0; i < r; i++) {
            int cut = inset(r, i);
            if (cut != 0) {
               rect(ctx, left, top + i, left + cut, top + i + 1, color);
               rect(ctx, right - cut, top + i, right, top + i + 1, color);
               rect(ctx, left, bottom - 1 - i, left + cut, bottom - i, color);
               rect(ctx, right - cut, bottom - 1 - i, right, bottom - i, color);
            }
         }
      }
   }

   public static int inset(int radius, int row) {
      double dy = radius - row - 0.5;
      double dx = Math.sqrt(Math.max(0.0, radius * radius - dy * dy));
      return Math.max(0, (int)Math.ceil(radius - 0.5 - dx));
   }

   private static int rowInset(int radius, int row, int height) {
      if (radius <= 0) {
         return 0;
      } else if (row < radius) {
         return inset(radius, row);
      } else {
         return row >= height - radius ? inset(radius, height - 1 - row) : 0;
      }
   }

   private static int rowColor(int topColor, int bottomColor, int row, int height) {
      return topColor == bottomColor ? topColor : lerp(topColor, bottomColor, (row + 0.5F) / height);
   }

   private static QuadBatch openBatch(GuiGraphicsExtractor ctx) {
      if (open == null && !Theme.clean()) {
         open = QuadBatch.colored(ctx);
         return open;
      } else {
         return null;
      }
   }

   private static void closeBatch(GuiGraphicsExtractor ctx, QuadBatch mine) {
      if (mine != null) {
         open = null;
         mine.submit(ctx);
      }
   }

   private static void rect(GuiGraphicsExtractor ctx, int x0, int y0, int x1, int y1, int color) {
      if (open != null) {
         open.fill(Math.min(x0, x1), Math.min(y0, y1), Math.max(x0, x1), Math.max(y0, y1), color);
      } else {
         ctx.fill(x0, y0, x1, y1, color);
      }
   }

   private static void grad(GuiGraphicsExtractor ctx, int x0, int y0, int x1, int y1, int topColor, int bottomColor) {
      if (open != null) {
         open.gradient(x0, y0, x1, y1, topColor, bottomColor);
      } else {
         ctx.fillGradient(x0, y0, x1, y1, topColor, bottomColor);
      }
   }

   public static int lerp(int from, int to, float t) {
      int out = 0;

      for (int shift = 0; shift < 32; shift += 8) {
         int a = from >>> shift & 0xFF;
         int b = to >>> shift & 0xFF;
         out |= Math.round(a + (b - a) * t) << shift;
      }

      return out;
   }
}

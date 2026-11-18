package com.jfant.specter.render;

import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.events.render.Render2DEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import meteordevelopment.orbit.EventHandler;

public final class CardLayout {
   private static final int GAP = 2;
   private static final float SLACK = 0.5F;
   private static final CardLayout.Listener LISTENER = new CardLayout.Listener();
   private static boolean subscribed;
   private static final Map<CardSpot, CardLayout.Entry> entries = new LinkedHashMap<>();
   private static long frame;

   private CardLayout() {
   }

   static void init() {
      if (!subscribed) {
         subscribed = true;
         SpecterCore.EVENT_BUS.subscribe(LISTENER);
      }
   }

   private static CardLayout.Entry entry(CardSpot spot) {
      return entries.computeIfAbsent(spot, s -> new CardLayout.Entry(s, entries.size()));
   }

   static float[] shown(CardSpot spot, float width, float height) {
      CardLayout.Entry e = entry(spot);
      float x = spot.spotX(width);
      float y = spot.spotY(height);
      if (e.solved == frame && CardDrag.dragged() != spot) {
         x += e.dx;
         y += e.dy;
      }

      int screenWidth = SpecterCore.mc.getWindow().getGuiScaledWidth();
      int screenHeight = SpecterCore.mc.getWindow().getGuiScaledHeight();
      return new float[]{Math.clamp(x, 0.0F, Math.max(0.0F, screenWidth - width)), Math.clamp(y, 0.0F, Math.max(0.0F, screenHeight - height))};
   }

   static float[] place(CardSpot spot, float width, float height) {
      CardLayout.Entry e = entry(spot);
      if (e.width != width || e.height != height) {
         e.resized = frame;
      }

      e.width = width;
      e.height = height;
      float[] at = shown(spot, width, height);
      e.x = at[0];
      e.y = at[1];
      e.drawn = frame;
      return at;
   }

   static float[] free(CardSpot spot, float left, float top, float width, float height) {
      List<float[]> others = new ArrayList<>();

      for (CardLayout.Entry e : entries.values()) {
         if (e.spot != spot && e.drawn >= frame - 1L) {
            others.add(new float[]{e.x, e.y, e.width, e.height});
         }
      }

      float[] at = nearestFree(left, top, width, height, others);
      return at != null ? at : new float[]{left, top};
   }

   public static float[] drawnRect(CardSpot spot) {
      CardLayout.Entry e = entries.get(spot);
      return e != null && e.drawn >= 0L ? new float[]{e.x, e.y, e.width, e.height} : null;
   }

   public static float[] liveRect(CardSpot spot) {
      CardLayout.Entry e = entries.get(spot);
      return e != null && e.drawn >= frame - 1L ? new float[]{e.x, e.y, e.width, e.height} : null;
   }

   public static CardSpot at(double x, double y) {
      CardSpot top = null;

      for (CardLayout.Entry e : entries.values()) {
         if (e.drawn >= frame - 1L && x >= e.x && x <= e.x + e.width && y >= e.y && y <= e.y + e.height) {
            top = e.spot;
         }
      }

      return top;
   }

   private static void solve() {
      List<CardLayout.Entry> live = new ArrayList<>();

      for (CardLayout.Entry e : entries.values()) {
         if (e.drawn == frame - 1L) {
            live.add(e);
         }
      }

      if (!live.isEmpty()) {
         CardSpot dragged = CardDrag.dragged();
         live.sort(
            Comparator.<CardLayout.Entry>comparingInt(ex -> ex.spot == dragged ? 0 : (ex.atSpot() ? 1 : 2))
               .thenComparing(Comparator.<CardLayout.Entry>comparingLong(ex -> ex.atSpot() ? ex.resized : 0L).reversed())
               .thenComparingInt(ex -> ex.order)
         );
         List<float[]> placed = new ArrayList<>();

         for (CardLayout.Entry ex : live) {
            float hx = ex.spot.spotX(ex.width);
            float hy = ex.spot.spotY(ex.height);
            float[] at = new float[]{hx, hy};
            if (overlapsAny(hx, hy, ex.width, ex.height, placed)) {
               float[] free = nearestFree(hx, hy, ex.width, ex.height, placed);
               if (free != null) {
                  at = free;
               }
            }

            ex.dx = at[0] - hx;
            ex.dy = at[1] - hy;
            ex.solved = frame;
            placed.add(new float[]{at[0], at[1], ex.width, ex.height});
         }
      }
   }

   private static float[] nearestFree(float left, float top, float width, float height, List<float[]> others) {
      if (!overlapsAny(left, top, width, height, others)) {
         return new float[]{left, top};
      } else {
         int screenWidth = SpecterCore.mc.getWindow().getGuiScaledWidth();
         int screenHeight = SpecterCore.mc.getWindow().getGuiScaledHeight();
         float[] xs = new float[1 + 2 * others.size()];
         float[] ys = new float[1 + 2 * others.size()];
         xs[0] = left;
         ys[0] = top;

         for (int i = 0; i < others.size(); i++) {
            float[] o = others.get(i);
            xs[1 + 2 * i] = o[0] - width - 2.0F;
            xs[2 + 2 * i] = o[0] + o[2] + 2.0F;
            ys[1 + 2 * i] = o[1] - height - 2.0F;
            ys[2 + 2 * i] = o[1] + o[3] + 2.0F;
         }

         float[] best = null;
         float bestDistance = 3.4028235E38F;

         for (float x : xs) {
            if (!(x < 0.0F) && !(x + width > screenWidth)) {
               for (float y : ys) {
                  if (!(y < 0.0F) && !(y + height > screenHeight)) {
                     float distance = (x - left) * (x - left) + (y - top) * (y - top);
                     if (!(distance >= bestDistance) && !overlapsAny(x, y, width, height, others)) {
                        best = new float[]{x, y};
                        bestDistance = distance;
                     }
                  }
               }
            }
         }

         return best;
      }
   }

   private static boolean overlapsAny(float x, float y, float width, float height, List<float[]> others) {
      for (float[] o : others) {
         if (x < o[0] + o[2] - 0.5F && o[0] < x + width - 0.5F && y < o[1] + o[3] - 0.5F && o[1] < y + height - 0.5F) {
            return true;
         }
      }

      return false;
   }

   private static final class Entry {
      final CardSpot spot;
      final int order;
      float width;
      float height;
      float x;
      float y;
      float dx;
      float dy;
      long drawn = -1L;
      long solved = -1L;
      long resized;

      Entry(CardSpot spot, int order) {
         this.spot = spot;
         this.order = order;
      }

      boolean atSpot() {
         return this.dx == 0.0F && this.dy == 0.0F;
      }
   }

   private static final class Listener {
      @EventHandler(
         priority = 200
      )
      private void onRender2D(Render2DEvent event) {
         CardLayout.frame++;
         CardLayout.solve();
      }
   }
}

package com.jfant.specter.festive;

import com.jfant.specter.anim.Channel;
import com.jfant.specter.anim.Motion;
import com.jfant.specter.core.systems.config.Config;
import com.jfant.specter.render.QuadBatch;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;

public final class Festive {
   private static final double SETTLE_SECONDS = 26.0;
   private static final int CAP_MAX = 4;
   private static final Snowfield guiSnow = new Snowfield();
   private static final Snowfield titleSnow = new Snowfield();
   private static final Map<Object, Festive.Cap> caps = new IdentityHashMap<>();
   private static final int[] BULBS = new int[]{16734815, 16765286, 4052620, 8377599, 16740039};

   private Festive() {
   }

   private static Config config() {
      return Config.get();
   }

   public static void backdrop(GuiGraphics ctx, int width, int height, float alpha) {
      Config c = config();
      if (c != null && Season.wintry() && c.guiSnow.get()) {
         guiSnow.draw(ctx, width, height, c.guiSnowAmount.get().floatValue(), 1.0F, 0.0F, 1.0F, alpha);
      }
   }

   public static void title(GuiGraphics ctx, int width, int height) {
      Config c = config();
      if (c != null && Season.wintry() && c.titleSnow.get()) {
         titleSnow.draw(ctx, width, height, 0.9F, 1.0F, 0.1F, 1.0F, 0.9F);
      }
   }

   public static void panel(GuiGraphics ctx, Object key, int x, int y, int w, int h) {
      if (Season.wintry()) {
         Config c = config();
         float settled = c != null && c.settlingSnow.get() ? settle(key, x, y, w) : 0.0F;
         if (settled > 0.01F) {
            cap(ctx, capFor(key), x, y, w, settled);
         }

         if (h > 30) {
            frost(ctx, x, y, w, h, key);
         }
      }
   }

   public static void bar(GuiGraphics ctx, Object key, int x, int y, int w, int h, int wordX, int freeLeft, int freeRight) {
      if (Season.wintry()) {
         Config c = config();
         float settled = c != null && c.settlingSnow.get() ? settle(key, x, y, w) : 0.0F;
         if (settled > 0.01F) {
            cap(ctx, capFor(key), x, y, w, settled);
         }

         if (Season.christmas()) {
            if (freeRight - freeLeft >= 48) {
               lights(ctx, freeLeft, freeRight, y + 5, h - 10);
            }

            hat(ctx, wordX - 3, y + 2);
         }
      }
   }

   private static Festive.Cap capFor(Object key) {
      return caps.computeIfAbsent(key, k -> new Festive.Cap(System.identityHashCode(k) * -1640531527));
   }

   private static float settle(Object key, int x, int y, int w) {
      Festive.Cap cap = capFor(key);
      double now = Motion.now();
      double dt = !(cap.seen < 0.0) && !(now - cap.seen > 0.5) ? now - cap.seen : 0.0;
      boolean moved = cap.seen >= 0.0 && now - cap.seen <= 0.5 && (cap.x != x || cap.y != y);
      if (moved && cap.amount > 0.05 && Motion.plays(Channel.FESTIVE)) {
         float push = Math.clamp((x - cap.x) * 30.0F, -220.0F, 220.0F);
         int clumps = (int)Math.ceil(cap.amount * Math.min(10.0F, w / 12.0F));

         for (int i = 0; i < clumps; i++) {
            float at = x + 4 + (float)((cap.seed * 31L + i * 7919L + (long)(now * 1000.0)) % 997L) / 997.0F * (w - 8);
            guiSnow.shed(at, y - 1, push * 0.25F);
         }

         cap.amount = 0.0;
      } else if (!moved) {
         cap.amount = Math.min(1.0, cap.amount + dt / 26.0);
      }

      cap.x = x;
      cap.y = y;
      cap.w = w;
      cap.seen = now;
      if (caps.size() > 64) {
         caps.values().removeIf(old -> now - old.seen > 30.0);
      }

      return (float)cap.amount;
   }

   private static void cap(GuiGraphics ctx, Festive.Cap cap, int x, int y, int w, float amount) {
      QuadBatch b = QuadBatch.colored(ctx);
      int from = x + 3;
      int to = x + w - 3;
      int runStart = from;
      int runDepth = -1;

      for (int px = from; px <= to; px++) {
         int depth = px == to ? -1 : depthAt(cap.seed, px - from, to - from, amount);
         if (depth != runDepth) {
            if (runDepth > 0) {
               b.gradient(runStart, y - runDepth + 1, px, y + 1, -184549377, -254352129);
               b.fill(runStart, y - runDepth, px, y - runDepth + 1, -1);
            }

            runStart = px;
            runDepth = depth;
         }
      }

      b.submit(ctx);
   }

   private static int depthAt(int seed, int i, int n, float amount) {
      if (n <= 0) {
         return 0;
      } else {
         double u = (double)i / n;
         double phase = (seed & 65535) / 65535.0 * 3.141592653589793 * 2.0;
         double drift = 0.55 + 0.25 * Math.sin(u * 7.3 + phase) + 0.2 * Math.sin(u * 17.1 + phase * 2.7);
         double taper = Math.min(1.0, Math.min(i, n - i) / 6.0);
         return (int)Math.round(4.0F * amount * drift * taper);
      }
   }

   private static void frost(GuiGraphics ctx, int x, int y, int w, int h, Object key) {
      QuadBatch b = QuadBatch.colored(ctx);
      double t = Motion.plays(Channel.FESTIVE) ? Motion.now() : 0.0;
      int seed = System.identityHashCode(key);

      for (int side = 0; side < 2; side++) {
         int cx = side == 0 ? x + 3 : x + w - 4;
         int dir = side == 0 ? 1 : -1;
         int cy = y + h - 4;

         for (int feather = 0; feather < 3; feather++) {
            int length = 5 + (seed >>> feather * 4 + side * 12 & 3);

            for (int i = 0; i < length; i++) {
               int px = cx + dir * (feather == 0 ? i : (feather == 1 ? i : i / 2));
               int py = cy - (feather == 0 ? i / 2 : (feather == 1 ? i : i));
               float fade = 1.0F - (float)i / length;
               float shimmer = 0.75F + 0.25F * (float)Math.sin(t * 1.3 + i * 0.9 + feather * 2 + side);
               int a = Math.round(106.0F * fade * shimmer);
               if (a >= 6) {
                  b.fill(px, py, px + 1, py + 1, a << 24 | 15135743);
                  if (i % 2 == 1 && i < length - 1) {
                     b.fill(px + dir, py - 1, px + dir + 1, py, a / 2 << 24 | 15135743);
                  }
               }
            }
         }
      }

      b.submit(ctx);
   }

   private static void lights(GuiGraphics ctx, int left, int right, int top, int room) {
      QuadBatch b = QuadBatch.colored(ctx);
      double t = Motion.plays(Channel.FESTIVE) ? Motion.now() : 0.0;
      int span = right - left;
      int swags = Math.max(1, span / 56);
      float swagW = (float)span / swags;
      int sag = Math.min(4, Math.max(2, room - 5));
      int wire = -803980762;

      for (int px = 0; px <= span; px++) {
         float u = px % swagW / swagW;
         int wy = top + Math.round(sag * 4 * u * (1.0F - u));
         b.fill(left + px, wy, left + px + 1, wy + 1, wire);
      }

      for (int i = 0; i <= swags; i++) {
         int hx = left + Math.round(i * swagW);
         b.fill(hx, top - 1, hx + 1, top + 1, -523656457);
      }

      int index = 0;

      for (float at = 5.0F; at < span - 3; index++) {
         float u = at % swagW / swagW;
         int bx = left + Math.round(at);
         int by = top + Math.round(sag * 4 * u * (1.0F - u)) + 1;
         int rgb = BULBS[index % BULBS.length];
         double own = 0.62 + 0.38 * Math.sin(t * 1.7 + index * 2.1);
         double wave = Math.max(0.0, 1.0 - Math.abs(t * 0.2 % 1.0 * (span / 9.0 + 6.0) - 3.0 - index) / 2.5);
         float glow = (float)Math.min(1.0, own * 0.75 + wave * 0.6);
         int halo = Math.round(52.0F * glow) << 24 | rgb;
         b.fill(bx - 2, by - 1, bx + 3, by + 4, halo);
         b.fill(bx - 1, by - 2, bx + 2, by + 5, halo);
         int body = Math.round(255.0F * (0.55F + 0.45F * glow)) << 24 | rgb;
         b.fill(bx, by, bx + 1, by + 3, body);
         b.fill(bx - 1, by + 1, bx + 2, by + 2, body);
         if (glow > 0.55F) {
            b.fill(bx, by + 1, bx + 1, by + 2, Math.round(255.0F * (glow - 0.55F) / 0.45F) << 24 | 16777215);
         }

         at += 9.0F;
      }

      b.submit(ctx);
   }

   private static void hat(GuiGraphics ctx, int x, int y) {
      QuadBatch b = QuadBatch.colored(ctx);
      int red = -1750963;
      int dark = -4705734;
      int white = -1;
      int shade = -2300171;
      b.fill(x + 4, y, x + 6, y + 1, red);
      b.fill(x + 3, y + 1, x + 6, y + 2, red);
      b.fill(x + 2, y + 2, x + 6, y + 3, red);
      b.fill(x + 1, y + 3, x + 6, y + 4, dark);
      b.fill(x, y + 4, x + 7, y + 5, white);
      b.fill(x, y + 5, x + 7, y + 6, shade);
      b.fill(x + 6, y - 1, x + 8, y + 1, white);
      b.submit(ctx);
   }

   private static final class Cap {
      double amount;
      int x;
      int y;
      int w;
      double seen = -1.0;
      final int seed;

      Cap(int seed) {
         this.seed = seed;
      }
   }
}

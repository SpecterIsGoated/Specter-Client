package com.jfant.specter.notify;

import com.jfant.specter.anim.Animator;
import com.jfant.specter.anim.Channel;
import com.jfant.specter.anim.Motion;
import com.jfant.specter.anim.Spec;
import com.jfant.specter.anim.Tween;
import com.jfant.specter.render.CardText;
import com.jfant.specter.render.PurpleCard;
import com.jfant.specter.render.QuadBatch;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.client.gui.GuiGraphics;
import org.joml.Matrix3x2fStack;

public final class Notifications {
   public static final long DEFAULT_MILLIS = 3200L;
   public static final int MAX_SHOWN = 5;
   private static final int WIDTH = 150;
   private static final int PAD = 6;
   private static final int GAP = 4;
   private static final int MARGIN = 6;
   private static final int LINE = 10;
   private static final ConcurrentLinkedQueue<Notifications.Notification> incoming = new ConcurrentLinkedQueue<>();
   private static final List<Notifications.Notification> shown = new ArrayList<>();
   private static final List<Notifications.Notification> waiting = new ArrayList<>();
   private static volatile boolean enabled = true;
   private static volatile boolean moduleToggles = true;
   private static volatile Notifications.Corner corner = Notifications.Corner.TopRight;
   private static volatile double seconds = 3.2;
   private static long lastFrame = -1L;

   private Notifications() {
   }

   public static Notifications.Notification info(String title, String text) {
      return post(new Notifications.Notification(null, Notifications.Kind.INFO, title, text));
   }

   public static Notifications.Notification success(String title, String text) {
      return post(new Notifications.Notification(null, Notifications.Kind.SUCCESS, title, text));
   }

   public static Notifications.Notification warning(String title, String text) {
      return post(new Notifications.Notification(null, Notifications.Kind.WARNING, title, text));
   }

   public static Notifications.Notification error(String title, String text) {
      return post(new Notifications.Notification(null, Notifications.Kind.ERROR, title, text));
   }

   public static Notifications.Notification keyed(Object key, Notifications.Kind kind, String title, String text) {
      return post(new Notifications.Notification(key, kind, title, text));
   }

   public static Notifications.Notification progress(String title, String text) {
      Notifications.Notification n = new Notifications.Notification(null, Notifications.Kind.INFO, title, text);
      n.sticky = true;
      n.progress = 0.0;
      return post(n);
   }

   public static void module(Object module, String title, boolean on) {
      if (moduleToggles) {
         post(new Notifications.Notification(module, on ? Notifications.Kind.ON : Notifications.Kind.OFF, title, on ? "Enabled" : "Disabled"));
      }
   }

   private static Notifications.Notification post(Notifications.Notification n) {
      if (enabled) {
         incoming.add(n);
      }

      return n;
   }

   public static void clear() {
      incoming.clear();

      for (Notifications.Notification n : shown) {
         n.dismiss();
      }

      waiting.clear();
   }

   public static void configure(boolean on, boolean modules, Notifications.Corner where, double lifeSeconds) {
      enabled = on;
      moduleToggles = modules;
      corner = where;
      seconds = lifeSeconds;
      if (!on) {
         incoming.clear();
      }
   }

   public static void render(GuiGraphics ctx, int width, int height) {
      long frame = Motion.frameCount();
      if (frame != lastFrame) {
         lastFrame = frame;
         take();
         if (!shown.isEmpty()) {
            double now = Motion.now();
            Notifications.Corner where = corner;
            boolean right = where == Notifications.Corner.TopRight || where == Notifications.Corner.BottomRight;
            boolean bottom = where == Notifications.Corner.BottomRight || where == Notifications.Corner.BottomLeft;
            float cursor = 0.0F;
            Iterator<Notifications.Notification> it = shown.iterator();

            while (it.hasNext()) {
               Notifications.Notification n = it.next();
               if (!n.leaving && !n.sticky && now >= n.until) {
                  n.leaving = true;
               }

               if (n.dismissed) {
                  n.leaving = true;
               }

               float present = n.presence().to(n.leaving ? 0.0 : 1.0, n.leaving ? Spec.TOAST_OUT : Spec.TOAST_IN).getF();
               if (n.leaving && n.presence().done()) {
                  it.remove();
                  Animator.forget(n);
               } else {
                  int h = n.height();
                  float slot = Animator.to(n, "slot", cursor, Spec.TOAST_MOVE);
                  draw(ctx, n, width, height, right, bottom, slot, present, now);
                  cursor += (h + 4) * (n.leaving ? present : 1.0F);
               }
            }
         }
      }
   }

   private static void take() {
      Notifications.Notification n;
      while ((n = incoming.poll()) != null) {
         Notifications.Notification old = n.key == null ? null : find(n.key);
         if (old != null) {
            old.rewrite(n);
         } else {
            waiting.add(n);
         }
      }

      int live = 0;

      for (Notifications.Notification s : shown) {
         if (!s.leaving) {
            live++;
         }
      }

      while (live < 5 && !waiting.isEmpty()) {
         Notifications.Notification next = waiting.remove(0);
         next.show(seconds);
         shown.add(next);
         live++;
      }

      while (waiting.size() > 12) {
         waiting.remove(0);
      }
   }

   private static Notifications.Notification find(Object key) {
      for (Notifications.Notification s : shown) {
         if (!s.leaving && key.equals(s.key)) {
            return s;
         }
      }

      for (Notifications.Notification w : waiting) {
         if (key.equals(w.key)) {
            return w;
         }
      }

      return null;
   }

   private static void draw(
      GuiGraphics ctx, Notifications.Notification n, int width, int height, boolean right, boolean bottom, float slot, float present, double now
   ) {
      int h = n.height();
      float travel = (float)(28.0 * Motion.amount(Channel.HUD));
      float x = right ? width - 6 - 150 + (1.0F - present) * travel : 6.0F - (1.0F - present) * travel;
      float y = bottom ? height - 6 - h - slot : 6.0F + slot;
      Matrix3x2fStack m = ctx.pose();
      m.pushMatrix();
      m.translate(x, y);
      QuadBatch.pushAlpha(present);

      try {
         PurpleCard.bubble(ctx, 0, 0, 150, h);
         int accent = n.kind.color;
         PurpleCard.fillRounded(ctx, 3, 4, 5, h - 4, 1, 15, accent, accent);
         float flash = Animator.pulse(n, "rewrite");
         if (flash > 0.01F) {
            int wash = Math.round(flash * 56.0F) << 24 | accent & 16777215;
            PurpleCard.fillRounded(ctx, 1, 1, 149, h - 1, 3, 15, wash, wash);
         }

         icon(ctx, n.kind, 10, 6, accent);
         CardText.draw(ctx, CardText.fit(n.title, 120), 21.0F, 5.0F, -1, true);
         int ty = 16;

         for (String line : n.lines()) {
            CardText.draw(ctx, line, 21.0F, (float)ty, -4149795, true);
            ty += 10;
         }

         double p = n.progress >= 0.0 ? n.shownProgress() : (n.sticky ? -1.0 : Math.clamp((n.until - now) / n.life, 0.0, 1.0));
         if (p >= 0.0) {
            int full = 142;
            PurpleCard.fillRounded(ctx, 4, h - 3, 4 + full, h - 2, 0, 0, 822083583, 822083583);
            int w = (int)Math.round(full * p);
            if (w > 0) {
               PurpleCard.fillRounded(ctx, 4, h - 3, 4 + w, h - 2, 0, 0, accent, accent);
            }
         }
      } finally {
         QuadBatch.popAlpha();
         m.popMatrix();
      }
   }

   private static void icon(GuiGraphics ctx, Notifications.Kind kind, int x, int y, int color) {
      QuadBatch b = QuadBatch.colored(ctx);
      switch (kind) {
         case INFO:
            b.fill(x + 3, y, x + 4, y + 1, color);
            b.fill(x + 3, y + 2, x + 4, y + 7, color);
            break;
         case SUCCESS:
            b.fill(x, y + 3, x + 1, y + 5, color);
            b.fill(x + 1, y + 4, x + 2, y + 6, color);
            b.fill(x + 2, y + 5, x + 3, y + 7, color);
            b.fill(x + 3, y + 4, x + 4, y + 6, color);
            b.fill(x + 4, y + 3, x + 5, y + 5, color);
            b.fill(x + 5, y + 2, x + 6, y + 4, color);
            b.fill(x + 6, y + 1, x + 7, y + 3, color);
            break;
         case WARNING:
            b.fill(x + 3, y, x + 4, y + 5, color);
            b.fill(x + 3, y + 6, x + 4, y + 7, color);
            break;
         case ERROR:
            for (int i = 0; i < 7; i++) {
               b.fill(x + i, y + i, x + i + 1, y + i + 1, color);
               b.fill(x + 6 - i, y + i, x + 7 - i, y + i + 1, color);
            }
            break;
         case ON:
            b.fill(x + 2, y + 1, x + 5, y + 6, color);
            b.fill(x + 1, y + 2, x + 6, y + 5, color);
            break;
         case OFF:
            b.fill(x + 2, y + 1, x + 5, y + 2, color);
            b.fill(x + 2, y + 5, x + 5, y + 6, color);
            b.fill(x + 1, y + 2, x + 2, y + 5, color);
            b.fill(x + 5, y + 2, x + 6, y + 5, color);
      }

      b.submit(ctx);
   }

   public static enum Corner {
      TopRight("Top right"),
      TopLeft("Top left"),
      BottomRight("Bottom right"),
      BottomLeft("Bottom left");

      private final String title;

      private Corner(String title) {
         this.title = title;
      }

      @Override
      public String toString() {
         return this.title;
      }
   }

   public static enum Kind {
      INFO(-4879105),
      SUCCESS(-12724596),
      WARNING(-14249),
      ERROR(-1750963),
      ON(-12724596),
      OFF(-6648648);

      final int color;

      private Kind(int color) {
         this.color = color;
      }
   }

   public static final class Notification {
      final Object key;
      volatile Notifications.Kind kind;
      volatile String title;
      volatile String text;
      volatile double progress = -1.0;
      volatile boolean sticky;
      volatile boolean dismissed;
      boolean leaving;
      double until;
      double life;
      private List<String> lines;
      private String wrapped;
      private Tween presence;
      private volatile boolean finished;

      Notification(Object key, Notifications.Kind kind, String title, String text) {
         this.key = key;
         this.kind = kind;
         this.title = title == null ? "" : title;
         this.text = text == null ? "" : text;
      }

      public Notifications.Notification progress(double fraction) {
         this.progress = Math.clamp(fraction, 0.0, 1.0);
         return this;
      }

      public Notifications.Notification text(String text) {
         this.text = text == null ? "" : text;
         return this;
      }

      public Notifications.Notification finish(Notifications.Kind kind, String text) {
         this.kind = kind;
         this.text = text == null ? "" : text;
         this.progress = 1.0;
         this.finished = true;
         return this;
      }

      public void dismiss() {
         this.dismissed = true;
      }

      Tween presence() {
         if (this.presence == null) {
            this.presence = Tween.progress(Spec.TOAST_IN, false);
         }

         return this.presence;
      }

      void show(double seconds) {
         this.life = Math.max(0.8, seconds);
         this.until = Motion.now() + this.life;
      }

      void rewrite(Notifications.Notification other) {
         boolean changed = other.kind != this.kind || !other.text.equals(this.text) || !other.title.equals(this.title);
         this.kind = other.kind;
         this.title = other.title;
         this.text = other.text;
         this.progress = other.progress;
         this.sticky = other.sticky;
         if (this.life > 0.0) {
            this.until = Motion.now() + this.life;
         }

         if (changed) {
            Animator.fire(this, "rewrite", Spec.MODULE.on(Channel.HUD));
         }
      }

      double shownProgress() {
         if (this.finished && this.sticky) {
            this.sticky = false;
            this.until = Motion.now() + Math.min(this.life, 2.0);
         }

         return Animator.to(this, "progress", this.progress, 0.0, Spec.VALUE.on(Channel.HUD));
      }

      List<String> lines() {
         String t = this.text;
         if (this.lines == null || !t.equals(this.wrapped)) {
            this.wrapped = t;
            this.lines = wrap(t, 123);
         }

         return this.lines;
      }

      int height() {
         return 16 + this.lines().size() * 10 + 5;
      }

      private static List<String> wrap(String text, int max) {
         List<String> out = new ArrayList<>();
         if (text.isEmpty()) {
            return out;
         } else {
            String line = "";

            for (String word : text.split(" ")) {
               String tried = line.isEmpty() ? word : line + " " + word;
               if (!line.isEmpty() && CardText.width(tried) > max) {
                  out.add(line);
                  line = word;
               } else {
                  line = tried;
               }

               if (out.size() == 3) {
                  break;
               }
            }

            if (!line.isEmpty() && out.size() < 3) {
               out.add(CardText.fit(line, max));
            }

            return out;
         }
      }
   }
}

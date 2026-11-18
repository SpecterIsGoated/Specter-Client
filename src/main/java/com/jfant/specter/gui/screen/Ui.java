package com.jfant.specter.gui.screen;

import com.jfant.specter.anim.Animator;
import com.jfant.specter.anim.Channel;
import com.jfant.specter.anim.Motion;
import com.jfant.specter.anim.Spec;
import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.systems.config.Config;
import com.jfant.specter.core.utils.render.color.Color;
import com.jfant.specter.render.CardText;
import com.jfant.specter.render.PurpleCard;
import com.jfant.specter.render.QuadBatch;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

final class Ui {
   static final int PINK = -37177;
   static final int PINK_FILL_TOP = 2030005959;
   static final int PINK_FILL_BOTTOM = 1090481863;
   static final int PINK_GLOW = 822046407;
   static final int TRACK_OFF = -14019002;
   static final int KNOB = -726785;
   static final int TEXT_HEIGHT = 8;
   static String hint = "";
   static String tip = "";
   static Ui.Draggable dragging;
   private static Config config;
   static final int SWITCH_ON = -12724596;
   static final int SWITCH_OFF = -1750963;

   private Ui() {
   }

   static boolean shiftDown() {
      long window = SpecterCore.mc.getWindow().handle();
      return GLFW.glfwGetKey(window, 340) == 1 || GLFW.glfwGetKey(window, 344) == 1;
   }

   static boolean in(int mx, int my, int x, int y, int w, int h) {
      return mx >= x && mx < x + w && my >= y && my < y + h;
   }

   static void text(GuiGraphics ctx, String text, int x, int y, int color) {
      CardText.draw(ctx, text, (float)x, (float)y, color, true);
   }

   static int width(String text) {
      return CardText.width(text);
   }

   static int textY(int rowTop, int rowHeight) {
      return rowTop + (rowHeight - 8) / 2;
   }

   static void textRight(GuiGraphics ctx, String text, int right, int y, int color) {
      text(ctx, text, right - width(text), y, color);
   }

   static void textCentered(GuiGraphics ctx, String text, int centerX, int y, int color) {
      text(ctx, text, centerX - width(text) / 2, y, color);
   }

   static String fit(String text, int maxWidth) {
      return CardText.fit(text, maxWidth);
   }

   static List<String> wrap(String text, int maxWidth) {
      List<String> lines = new ArrayList<>();
      String line = "";

      for (String word : text.split(" ")) {
         String tried = line.isEmpty() ? word : line + " " + word;
         if (!line.isEmpty() && width(tried) > maxWidth) {
            lines.add(line);
            line = word;
         } else {
            line = tried;
         }
      }

      if (!line.isEmpty()) {
         lines.add(line);
      }

      return lines;
   }

   static Config config() {
      if (config == null) {
         config = Config.get();
      }

      return config;
   }

   private static boolean hoverEffects() {
      Config c = config();
      return c == null || c.hoverEffects.get();
   }

   static float hover(Object key, boolean over) {
      if (!hoverEffects()) {
         return over ? 1.0F : 0.0F;
      } else {
         return Animator.progress(key, "hover", over, Spec.HOVER, Spec.HOVER.scaled(1.7));
      }
   }

   static float hover(Object key, String part, boolean over) {
      if (!hoverEffects()) {
         return over ? 1.0F : 0.0F;
      } else {
         return Animator.progress(key, part, over, Spec.HOVER, Spec.HOVER.scaled(1.7));
      }
   }

   static void press(Object key) {
      if (hoverEffects()) {
         Animator.fire(key, "press", Spec.PRESS.scaled(2.2));
      }
   }

   static float pressed(Object key) {
      return Animator.pulse(key, "press");
   }

   static int alpha(int color, float factor) {
      return Math.round((color >>> 24) * Math.clamp(factor, 0.0F, 1.0F)) << 24 | color & 16777215;
   }

   static int mix(int from, int to, float t) {
      return t <= 0.0F ? from : (t >= 1.0F ? to : PurpleCard.lerp(from, to, t));
   }

   static void fill(GuiGraphics ctx, int x0, int y0, int x1, int y1, int color) {
      int c = QuadBatch.faded(color);
      if (c >>> 24 != 0) {
         ctx.fill(x0, y0, x1, y1, c);
      }
   }

   static void box(GuiGraphics ctx, int x, int y, int w, int h, boolean hover) {
      if (hover) {
         PurpleCard.fillRounded(ctx, x, y, x + w, y + h, 2, 15, 587202559, 385875967);
      }
   }

   static void box(GuiGraphics ctx, int x, int y, int w, int h, float lit) {
      if (lit > 0.01F) {
         PurpleCard.fillRounded(ctx, x, y, x + w, y + h, 2, 15, alpha(587202559, lit), alpha(385875967, lit));
      }
   }

   static void glow(GuiGraphics ctx, int x, int y, int w, int h) {
      glow(ctx, x, y, w, h, 1.0F);
   }

   static void glow(GuiGraphics ctx, int x, int y, int w, int h, float lit) {
      if (!(lit <= 0.01F)) {
         PurpleCard.fillRounded(ctx, x - 1, y - 1, x + w + 1, y + h + 1, 4, 15, alpha(822046407, lit), alpha(822046407, lit));
         PurpleCard.fillRounded(ctx, x, y, x + w, y + h, 3, 15, alpha(2030005959, lit), alpha(1090481863, lit));
         PurpleCard.strokeRounded(ctx, x, y, x + w, y + h, 3, alpha(-37177, lit), alpha(-37177, lit));
      }
   }

   static void wash(GuiGraphics ctx, int x, int y, int w, int h) {
      wash(ctx, x, y, w, h, 1.0F);
   }

   static void wash(GuiGraphics ctx, int x, int y, int w, int h, float lit) {
      if (lit > 0.01F) {
         PurpleCard.fillRounded(ctx, x, y, x + w, y + h, 3, 15, alpha(620756991, lit), alpha(352321535, lit));
      }
   }

   static void modulePill(GuiGraphics ctx, int x, int y, int w, int h, boolean on) {
      if (on) {
         PurpleCard.fillRounded(ctx, x, y, x + w, y + h, h / 2, 15, -37177, -3649632);
         textCentered(ctx, "ON", x + w / 2, textY(y, h), -1);
      } else {
         PurpleCard.fillRounded(ctx, x, y, x + w, y + h, h / 2, 15, -14019002, -14019002);
         PurpleCard.strokeRounded(ctx, x, y, x + w, y + h, h / 2, -1333837902, -1333837902);
         textCentered(ctx, "off", x + w / 2, textY(y, h), -4149795);
      }
   }

   static void knobSwitch(GuiGraphics ctx, int x, int y, boolean on) {
      toggle(ctx, null, x, y, on);
   }

   static void toggle(GuiGraphics ctx, Object key, int x, int y, boolean on) {
      float t = on ? 1.0F : 0.0F;
      if (key != null) {
         t = Animator.progress(key, "switch", on, Spec.TOGGLE);
      }

      int w = 22;
      int h = 10;
      int track = PurpleCard.lerp(-1750963, -12724596, t);
      PurpleCard.fillRounded(ctx, x, y, x + w, y + h, 5, 15, track, PurpleCard.lerp(track, -16777216, 0.18F));
      float stretch = (float)(Math.sin(3.141592653589793 * t) * 3.0 * Math.min(1.0, Motion.amount(Channel.GUI)));
      float left = x + 2 + t * (w - 10) - (on ? stretch : 0.0F);
      float right = x + 2 + t * (w - 10) + 6.0F + (on ? 0.0F : stretch);
      int kl = Math.round(Math.max((float)(x + 2), left));
      int kr = Math.round(Math.min((float)(x + w - 2), right));
      PurpleCard.fillRounded(ctx, kl, y + 2, kr, y + h - 2, 3, 15, -1, -1513240);
   }

   static void arrow(GuiGraphics ctx, int x, int y, float open, int color) {
      int down = alpha(color, open);
      int side = alpha(color, 1.0F - open);
      QuadBatch b = QuadBatch.colored(ctx);

      for (int i = 0; i < 4; i++) {
         if (down >>> 24 != 0) {
            b.fill(x + i, y + 1 + i, x + 7 - i, y + 2 + i, down);
         }

         if (side >>> 24 != 0) {
            b.fill(x + 1 + i, y + i, x + 2 + i, y + 7 - i, side);
         }
      }

      b.submit(ctx);
   }

   static void arrow(GuiGraphics ctx, int x, int y, boolean open, int color) {
      arrow(ctx, x, y, open ? 1.0F : 0.0F, color);
   }

   static void button(GuiGraphics ctx, String label, int x, int y, int w, int h, boolean hover) {
      button(ctx, label, label, x, y, w, h, hover);
   }

   static void button(GuiGraphics ctx, Object key, String label, int x, int y, int w, int h, boolean hover) {
      float lit = hover(key, "button", hover);
      float down = pressed(key);
      int top = mix(1617964960, -2135932704, lit);
      int bottom = mix(1077942384, 1617964960, lit);
      if (down > 0.01F) {
         top = mix(top, -1596956417, down * 0.7F);
         bottom = mix(bottom, -2135932704, down * 0.7F);
      }

      PurpleCard.fillRounded(ctx, x, y, x + w, y + h, 3, 15, top, bottom);
      int edge = mix(-1333837902, -37177, lit);
      PurpleCard.strokeRounded(ctx, x, y, x + w, y + h, 3, edge, edge);
      textCentered(ctx, label, x + w / 2, textY(y, h), mix(-1911561, -1, lit));
   }

   static void item(GuiGraphics ctx, ItemStack stack, int x, int y) {
      ctx.renderItem(stack, x, y);
   }

   static int argb(Color c) {
      return (c.a & 0xFF) << 24 | (c.r & 0xFF) << 16 | (c.g & 0xFF) << 8 | c.b & 0xFF;
   }

   static int[] hsv(float h, float s, float v) {
      float hh = (h - (float)Math.floor(h)) * 6.0F;
      int sector = (int)hh;
      float f = hh - sector;
      float p = v * (1.0F - s);
      float q = v * (1.0F - s * f);
      float t = v * (1.0F - s * (1.0F - f));
      float r;
      float g;
      float b;
      switch (sector) {
         case 0:
            r = v;
            g = t;
            b = p;
            break;
         case 1:
            r = q;
            g = v;
            b = p;
            break;
         case 2:
            r = p;
            g = v;
            b = t;
            break;
         case 3:
            r = p;
            g = q;
            b = v;
            break;
         case 4:
            r = t;
            g = p;
            b = v;
            break;
         default:
            r = v;
            g = p;
            b = q;
      }

      return new int[]{Math.round(r * 255.0F), Math.round(g * 255.0F), Math.round(b * 255.0F)};
   }

   static float[] toHsv(int r, int g, int b) {
      float rf = r / 255.0F;
      float gf = g / 255.0F;
      float bf = b / 255.0F;
      float max = Math.max(rf, Math.max(gf, bf));
      float min = Math.min(rf, Math.min(gf, bf));
      float d = max - min;
      float h;
      if (d == 0.0F) {
         h = 0.0F;
      } else if (max == rf) {
         h = (gf - bf) / d % 6.0F / 6.0F;
      } else if (max == gf) {
         h = ((bf - rf) / d + 2.0F) / 6.0F;
      } else {
         h = ((rf - gf) / d + 4.0F) / 6.0F;
      }

      if (h < 0.0F) {
         h++;
      }

      return new float[]{h, max == 0.0F ? 0.0F : d / max, max};
   }

   interface Draggable {
      void drag(int var1, int var2);

      void release();
   }
}

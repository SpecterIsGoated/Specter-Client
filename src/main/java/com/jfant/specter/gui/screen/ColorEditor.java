package com.jfant.specter.gui.screen;

import com.jfant.specter.core.settings.ColorSetting;
import com.jfant.specter.core.utils.render.color.SettingColor;
import com.jfant.specter.render.PurpleCard;
import net.minecraft.client.gui.GuiGraphicsExtractor;

final class ColorEditor extends Editor implements Ui.Draggable {
   private static final int SQUARE_HEIGHT = 72;
   private static final int BAR_HEIGHT = 10;
   private static final int CONTENT_HEIGHT = 160;
   private final ColorSetting setting;
   private final TextField hex = new TextField("RRGGBB");
   private float hue;
   private float saturation;
   private float value = 1.0F;
   private int lastR = -1;
   private int lastG = -1;
   private int lastB = -1;
   private ColorEditor.Drag drag = ColorEditor.Drag.NONE;
   private int left;
   private int width;
   private int squareTop;
   private int hueTop;
   private int alphaTop;
   private int rowTop;
   private int rainbowTop;

   ColorEditor(ColorSetting setting, Runnable back) {
      super(setting.title, back);
      this.setting = setting;
      this.hex.maxLength = 6;
      this.hex.filter = cp -> cp >= 48 && cp <= 57 || cp >= 97 && cp <= 102 || cp >= 65 && cp <= 70;
      this.hex.onChange = this::typedHex;
      SettingColor c = setting.get();
      float[] hsv = Ui.toHsv(c.r, c.g, c.b);
      this.hue = hsv[0];
      this.saturation = hsv[1];
      this.value = hsv[2];
      this.lastR = c.r;
      this.lastG = c.g;
      this.lastB = c.b;
   }

   private void follow() {
      SettingColor c = this.setting.get();
      if (c.r != this.lastR || c.g != this.lastG || c.b != this.lastB) {
         float[] hsv = Ui.toHsv(c.r, c.g, c.b);
         if (hsv[1] > 0.0F && hsv[2] > 0.0F) {
            this.hue = hsv[0];
         }

         this.saturation = hsv[1];
         this.value = hsv[2];
         this.lastR = c.r;
         this.lastG = c.g;
         this.lastB = c.b;
      }
   }

   private void write() {
      SettingColor c = this.setting.get();
      int[] rgb = Ui.hsv(this.hue, this.saturation, this.value);
      c.set(rgb[0], rgb[1], rgb[2], c.a);
      this.lastR = rgb[0];
      this.lastG = rgb[1];
      this.lastB = rgb[2];
      this.setting.onChanged();
   }

   private void typedHex() {
      if (this.hex.text.length() == 6) {
         try {
            int rgb = Integer.parseInt(this.hex.text, 16);
            SettingColor c = this.setting.get();
            c.set(rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF, c.a);
            this.setting.onChanged();
         } catch (NumberFormatException var3) {
         }
      }
   }

   private static int opaque(int[] rgb) {
      return 0xFF000000 | rgb[0] << 16 | rgb[1] << 8 | rgb[2];
   }

   @Override
   void body(GuiGraphicsExtractor ctx, int mx, int my) {
      this.follow();
      SettingColor c = this.setting.get();
      this.scroll.size(this.h, 160);
      this.left = this.x + 6;
      this.width = this.w - 12 - 4;
      int top = this.y - this.scroll.offset() + 4;
      this.squareTop = top;
      this.hueTop = this.squareTop + 72 + 6;
      this.alphaTop = this.hueTop + 10 + 6;
      this.rowTop = this.alphaTop + 10 + 8;
      this.rainbowTop = this.rowTop + 14 + 6;
      ctx.enableScissor(this.x, this.y, this.x + this.w, this.y + this.h);
      if (this.drag != ColorEditor.Drag.NONE) {
         this.drag(mx, my);
      }

      for (int i = 0; i < this.width; i += 2) {
         float s = (float)i / (this.width - 1);
         ctx.fillGradient(
            this.left + i,
            this.squareTop,
            Math.min(this.left + i + 2, this.left + this.width),
            this.squareTop + 72,
            opaque(Ui.hsv(this.hue, s, 1.0F)),
            -16777216
         );
      }

      PurpleCard.strokeRounded(ctx, this.left - 1, this.squareTop - 1, this.left + this.width + 1, this.squareTop + 72 + 1, 2, -1333837902, -1333837902);
      int markX = this.left + Math.round(this.saturation * (this.width - 1));
      int markY = this.squareTop + Math.round((1.0F - this.value) * 71.0F);
      Ui.fill(ctx, markX - 3, markY - 3, markX + 4, markY + 4, -1);
      Ui.fill(ctx, markX - 2, markY - 2, markX + 3, markY + 3, 0xFF000000 | rgbOf(c));

      for (int i = 0; i < this.width; i += 2) {
         Ui.fill(
            ctx,
            this.left + i,
            this.hueTop,
            Math.min(this.left + i + 2, this.left + this.width),
            this.hueTop + 10,
            opaque(Ui.hsv((float)i / this.width, 1.0F, 1.0F))
         );
      }

      PurpleCard.strokeRounded(ctx, this.left - 1, this.hueTop - 1, this.left + this.width + 1, this.hueTop + 10 + 1, 2, -1333837902, -1333837902);
      int hueMark = this.left + Math.round(this.hue * (this.width - 1));
      Ui.fill(ctx, hueMark - 1, this.hueTop - 2, hueMark + 1, this.hueTop + 10 + 2, -1);
      Ui.fill(ctx, this.left, this.alphaTop, this.left + this.width, this.alphaTop + 10, -15070162);
      int rgb = rgbOf(c);

      for (int i = 0; i < this.width; i += 2) {
         int alpha = Math.round(255.0F * i / (this.width - 1));
         Ui.fill(ctx, this.left + i, this.alphaTop, Math.min(this.left + i + 2, this.left + this.width), this.alphaTop + 10, alpha << 24 | rgb);
      }

      PurpleCard.strokeRounded(ctx, this.left - 1, this.alphaTop - 1, this.left + this.width + 1, this.alphaTop + 10 + 1, 2, -1333837902, -1333837902);
      int alphaMark = this.left + Math.round(c.a / 255.0F * (this.width - 1));
      Ui.fill(ctx, alphaMark - 1, this.alphaTop - 2, alphaMark + 1, this.alphaTop + 10 + 2, -1);
      if (!this.hex.focused()) {
         this.hex.setText(String.format("%02X%02X%02X", c.r, c.g, c.b));
      }

      Ui.text(ctx, "#", this.left, Ui.textY(this.rowTop, 14), -4149795);
      this.hex.render(ctx, this.left + 8, this.rowTop, 62, 14);
      PurpleCard.fillRounded(ctx, this.left + 76, this.rowTop, this.left + 100, this.rowTop + 14, 3, 15, Ui.argb(c), Ui.argb(c));
      PurpleCard.strokeRounded(ctx, this.left + 76, this.rowTop, this.left + 100, this.rowTop + 14, 3, -1333837902, -1333837902);
      Ui.button(ctx, "Reset", this.left + this.width - 44, this.rowTop, 44, 14, Ui.in(mx, my, this.left + this.width - 44, this.rowTop, 44, 14));
      Ui.box(ctx, this.left, this.rainbowTop, this.width, 20, Ui.in(mx, my, this.left, this.rainbowTop, this.width, 20));
      Ui.text(ctx, "Rainbow", this.left + 6, Ui.textY(this.rainbowTop, 20), -1911561);
      Ui.knobSwitch(ctx, this.left + this.width - 28, this.rainbowTop + 5, c.rainbow);
      ctx.disableScissor();
      this.scroll.bar(ctx, this.x + this.w - 1, this.y, this.h);
   }

   @Override
   void bodyClick(int mx, int my, int button) {
      if (!this.hex.click(mx, my)) {
         if (Ui.in(mx, my, this.left, this.squareTop, this.width, 72)) {
            this.drag = ColorEditor.Drag.SQUARE;
         } else if (Ui.in(mx, my, this.left, this.hueTop - 2, this.width, 14)) {
            this.drag = ColorEditor.Drag.HUE;
         } else {
            if (!Ui.in(mx, my, this.left, this.alphaTop - 2, this.width, 14)) {
               if (Ui.in(mx, my, this.left + this.width - 44, this.rowTop, 44, 14)) {
                  this.setting.reset();
                  this.follow();
                  return;
               }

               if (Ui.in(mx, my, this.left, this.rainbowTop, this.width, 20)) {
                  SettingColor c = this.setting.get();
                  c.rainbow = !c.rainbow;
                  this.setting.onChanged();
                  return;
               }

               return;
            }

            this.drag = ColorEditor.Drag.ALPHA;
         }

         Ui.dragging = this;
         this.drag(mx, my);
      }
   }

   @Override
   public void drag(int mx, int my) {
      SettingColor c = this.setting.get();
      switch (this.drag) {
         case SQUARE:
            this.saturation = clamp((float)(mx - this.left) / (this.width - 1));
            this.value = 1.0F - clamp((my - this.squareTop) / 71.0F);
            this.write();
            break;
         case HUE:
            this.hue = Math.min(0.999F, clamp((float)(mx - this.left) / (this.width - 1)));
            this.write();
            break;
         case ALPHA:
            c.a = Math.round(clamp((float)(mx - this.left) / (this.width - 1)) * 255.0F);
            this.setting.onChanged();
      }
   }

   @Override
   public void release() {
      this.drag = ColorEditor.Drag.NONE;
   }

   private static int rgbOf(SettingColor c) {
      return c.r << 16 | c.g << 8 | c.b;
   }

   private static float clamp(float v) {
      return Math.max(0.0F, Math.min(1.0F, v));
   }

   private static enum Drag {
      NONE,
      SQUARE,
      HUE,
      ALPHA;
   }
}

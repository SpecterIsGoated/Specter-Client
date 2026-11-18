package com.jfant.specter.gui.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;

abstract class Editor {
   private static final int HEADER = 24;
   final String title;
   private final Runnable back;
   final Scroll scroll = new Scroll();
   int x;
   int y;
   int w;
   int h;
   private int backX;
   private int backY;

   Editor(String title, Runnable back) {
      this.title = title;
      this.back = back;
   }

   final void render(GuiGraphicsExtractor ctx, int rx, int ry, int rw, int rh, int mx, int my) {
      this.backX = rx + 4;
      this.backY = ry + 3;
      boolean overBack = Ui.in(mx, my, this.backX, this.backY, 40, 14);
      Ui.button(ctx, "< Back", this.backX, this.backY, 40, 14, overBack);
      Ui.text(ctx, Ui.fit(this.title, rw - 56), rx + 50, Ui.textY(ry + 3, 14), -1065473);
      Ui.fill(ctx, rx + 4, ry + 20, rx + rw - 4, ry + 21, 1440926975);
      this.x = rx;
      this.y = ry + 24;
      this.w = rw;
      this.h = rh - 24;
      this.body(ctx, mx, my);
   }

   final boolean click(int mx, int my, int button) {
      if (Ui.in(mx, my, this.backX, this.backY, 40, 14)) {
         this.back.run();
         return true;
      } else {
         if (Ui.in(mx, my, this.x, this.y, this.w, this.h)) {
            this.bodyClick(mx, my, button);
         }

         return true;
      }
   }

   final void scrolled(double amount) {
      this.scroll.by(amount);
   }

   abstract void body(GuiGraphicsExtractor var1, int var2, int var3);

   abstract void bodyClick(int var1, int var2, int var3);

   void close() {
      this.back.run();
   }
}

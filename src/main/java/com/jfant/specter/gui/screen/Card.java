package com.jfant.specter.gui.screen;

import net.minecraft.client.gui.GuiGraphics;

abstract class Card {
   final String label;
   final String description;
   int x;
   int y;
   int w;
   int h;

   Card(String label, String description) {
      this.label = label;
      this.description = description == null ? "" : description;
   }

   boolean compact() {
      return false;
   }

   int minWidth() {
      return 0;
   }

   abstract int height();

   final void render(GuiGraphics ctx, int mx, int my) {
      boolean hover = Ui.in(mx, my, this.x, this.y, this.w, this.h);
      if (hover) {
         Ui.hint = this.description.isEmpty() ? this.label : this.description;
      }

      Ui.box(ctx, this.x, this.y, this.w, this.h, Ui.hover(this, hover));
      float down = Ui.pressed(this);
      if (down > 0.01F) {
         Ui.box(ctx, this.x, this.y, this.w, this.h, down * 1.4F);
      }

      this.content(ctx, mx, my, hover);
   }

   abstract void content(GuiGraphics var1, int var2, int var3, boolean var4);

   abstract void click(int var1, int var2, int var3, boolean var4);

   void reset() {
   }

   boolean contains(int mx, int my) {
      return Ui.in(mx, my, this.x, this.y, this.w, this.h);
   }
}

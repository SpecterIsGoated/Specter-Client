package com.jfant.specter.gui.screen;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;

final class ToggleCard extends Card {
   private final BooleanSupplier get;
   private final Consumer<Boolean> set;
   private final Runnable reset;

   ToggleCard(String label, String description, BooleanSupplier get, Consumer<Boolean> set, Runnable reset) {
      super(label, description);
      this.get = get;
      this.set = set;
      this.reset = reset;
   }

   @Override
   boolean compact() {
      return true;
   }

   @Override
   int minWidth() {
      return Ui.width(this.label) + 22 + 18;
   }

   @Override
   int height() {
      return 20;
   }

   @Override
   void content(GuiGraphics ctx, int mx, int my, boolean hover) {
      Ui.text(ctx, Ui.fit(this.label, this.w - 22 - 16), this.x + 6, Ui.textY(this.y, this.h), -1911561);
      Ui.toggle(ctx, this, this.x + this.w - 28, this.y + (this.h - 10) / 2, this.get.getAsBoolean());
   }

   @Override
   void click(int mx, int my, int button, boolean shift) {
      Ui.press(this);
      this.set.accept(!this.get.getAsBoolean());
   }

   @Override
   void reset() {
      this.reset.run();
   }
}

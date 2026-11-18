package com.jfant.specter.gui.screen;

import com.jfant.specter.anim.Motion;
import net.minecraft.client.gui.GuiGraphicsExtractor;

final class ButtonCard extends Card {
   private final Runnable action;
   private final boolean danger;
   private static final long CONFIRM_MS = 3000L;
   private long armedAt;

   ButtonCard(String label, String description, Runnable action) {
      this(label, description, false, action);
   }

   ButtonCard(String label, String description, boolean danger, Runnable action) {
      super(label, description);
      this.action = action;
      this.danger = danger;
   }

   @Override
   boolean compact() {
      return true;
   }

   @Override
   int minWidth() {
      return Ui.width(this.label) + 16;
   }

   @Override
   int height() {
      return 20;
   }

   @Override
   void content(GuiGraphicsExtractor ctx, int mx, int my, boolean hover) {
      int color = this.danger ? -37177 : Ui.mix(-1911561, -1, Ui.hover(this, "label", hover));
      float asking = Ui.hover(this, "armed", this.armed());
      if (asking > 0.01F) {
         float breath = 0.65F + 0.35F * (float)Math.sin(Motion.now() * 5.0);
         Ui.glow(ctx, this.x + 1, this.y + 1, this.w - 2, this.h - 2, asking * breath);
      }

      String text = this.armed() ? "Sure? Click again" : this.label;
      Ui.textCentered(ctx, Ui.fit(text, this.w - 8), this.x + this.w / 2, Ui.textY(this.y, this.h), color);
   }

   @Override
   void click(int mx, int my, int button, boolean shift) {
      Ui.press(this);
      if (this.danger && !this.armed()) {
         this.armedAt = System.currentTimeMillis();
      } else {
         this.armedAt = 0L;
         this.action.run();
      }
   }

   private boolean armed() {
      return this.armedAt != 0L && System.currentTimeMillis() - this.armedAt < 3000L;
   }
}

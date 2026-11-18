package com.jfant.specter.gui.screen;

import com.jfant.specter.anim.Channel;
import com.jfant.specter.anim.Motion;
import com.jfant.specter.anim.Spec;
import com.jfant.specter.anim.Spring;
import com.jfant.specter.anim.Tween;
import com.jfant.specter.core.systems.config.Config;
import com.jfant.specter.render.PurpleCard;
import net.minecraft.client.gui.GuiGraphics;

final class Scroll {
   private static final int STEP = 18;
   private static final double RESPONSE = 0.16;
   private static final double BAR_LINGER = 0.9;
   private double offset;
   private int view;
   private int content;
   private final Spring shown = new Spring(0.16, Channel.GUI, 0.0);
   private final Tween bar = Tween.progress(Spec.TINT, false);
   private double lastMoved = -10.0;

   void size(int view, int content) {
      this.view = view;
      this.content = content;
      this.clamp();
   }

   void by(double notches) {
      double before = this.offset;
      this.offset -= notches * 18.0;
      this.clamp();
      if (this.offset != before) {
         this.lastMoved = Motion.now();
      }
   }

   void reset() {
      this.offset = 0.0;
      this.shown.set(0.0);
   }

   int max() {
      return Math.max(0, this.content - this.view);
   }

   int offset() {
      Config config = Ui.config();
      if (config != null && !config.smoothScrolling.get()) {
         this.shown.set(this.offset);
         return (int)Math.round(this.offset);
      } else {
         double value = this.shown.follow(this.offset);
         return (int)Math.round(Math.max(0.0, Math.min(value, (double)this.max())));
      }
   }

   private void clamp() {
      this.offset = Math.max(0.0, Math.min(this.offset, (double)this.max()));
   }

   void bar(GuiGraphics ctx, int right, int top, int height) {
      if (this.max() > 0) {
         boolean wanted = Motion.now() - this.lastMoved < 0.9 || !this.shown.resting();
         float lit = this.bar.to(wanted ? 1.0 : 0.0, wanted ? Spec.TINT : Spec.TINT.scaled(3.0)).getF();
         float a = 0.3F + 0.7F * lit;
         int thumb = Math.max(10, height * this.view / this.content);
         double at = Math.max(0.0, Math.min(this.shown.get(), (double)this.max()));
         int y = top + (int)((height - thumb) * (at / this.max()));
         PurpleCard.fillRounded(ctx, right - 3, y, right, y + thumb, 1, 15, Ui.alpha(-1595749121, a), Ui.alpha(1893911807, a));
      }
   }
}

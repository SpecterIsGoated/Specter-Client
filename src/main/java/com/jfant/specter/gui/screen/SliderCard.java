package com.jfant.specter.gui.screen;

import com.jfant.specter.anim.Channel;
import com.jfant.specter.anim.Spring;
import com.jfant.specter.render.PurpleCard;
import java.util.Locale;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;

final class SliderCard extends Card implements Ui.Draggable {
   private final DoubleSupplier get;
   private final DoubleConsumer set;
   private final Runnable reset;
   private final double min;
   private final double max;
   private final double sliderMin;
   private final double sliderMax;
   private final int decimals;
   private final boolean noSlider;
   private final boolean onRelease;
   private final TextField edit = new TextField("");
   private boolean dragging;
   private double pending;
   private int barX;
   private int barW;
   private final Spring fill = new Spring(0.11, Channel.GUI, -1.0);

   SliderCard(
      String label,
      String description,
      DoubleSupplier get,
      DoubleConsumer set,
      Runnable reset,
      double min,
      double max,
      double sliderMin,
      double sliderMax,
      int decimals,
      boolean noSlider,
      boolean onRelease
   ) {
      super(label, description);
      this.get = get;
      this.set = set;
      this.reset = reset;
      this.min = min;
      this.max = max;
      this.sliderMin = sliderMin;
      this.sliderMax = sliderMax <= sliderMin ? sliderMin + 1.0 : sliderMax;
      this.decimals = decimals;
      this.noSlider = noSlider;
      this.onRelease = onRelease;
      this.edit.maxLength = 16;
      this.edit.filter = cp -> cp >= 48 && cp <= 57 || cp == 46 || cp == 45;
      this.edit.onCommit = this::commitTyped;
   }

   @Override
   boolean compact() {
      return this.noSlider;
   }

   @Override
   int minWidth() {
      return Ui.width(this.label) + Ui.width(this.format(this.get.getAsDouble())) + 20;
   }

   @Override
   int height() {
      return this.noSlider ? 20 : 30;
   }

   private double shown() {
      return this.dragging && this.onRelease ? this.pending : this.get.getAsDouble();
   }

   private String format(double value) {
      if (this.decimals <= 0) {
         return Long.toString(Math.round(value));
      } else {
         String text = String.format(Locale.ROOT, "%." + this.decimals + "f", value);
         if (text.indexOf(46) >= 0) {
            text = text.replaceAll("0+$", "").replaceAll("\\.$", "");
         }

         return text;
      }
   }

   private int numberLeft(String text) {
      return this.x + this.w - 6 - Ui.width(text) - 3;
   }

   @Override
   void content(GuiGraphicsExtractor ctx, int mx, int my, boolean hover) {
      boolean editing = this.edit.focused();
      int lineHeight = this.noSlider ? this.h : 16;
      int textY = Ui.textY(this.y + (this.noSlider ? 0 : 1), lineHeight);
      String number = this.format(this.shown());
      Ui.text(ctx, Ui.fit(this.label, this.w - 12 - Ui.width(number) - 10), this.x + 6, textY, -1911561);
      if (editing) {
         this.edit.render(ctx, this.x + this.w - 6 - 58, this.y + (this.noSlider ? 4 : 3), 58, 12);
      } else {
         boolean overNumber = Ui.in(mx, my, this.numberLeft(number), this.y, this.w - (this.numberLeft(number) - this.x), lineHeight);
         Ui.textRight(ctx, number, this.x + this.w - 6, textY, Ui.mix(-1, -37177, Ui.hover(this, "number", overNumber)));
      }

      if (!this.noSlider) {
         this.barX = this.x + 6;
         this.barW = this.w - 12;
         int barY = this.y + this.h - 10;
         PurpleCard.fillRounded(ctx, this.barX, barY, this.barX + this.barW, barY + 6, 3, 15, -15463900, -14806986);
         double fraction = Math.max(0.0, Math.min(1.0, (this.shown() - this.sliderMin) / (this.sliderMax - this.sliderMin)));
         if (this.fill.target() < 0.0 || this.dragging) {
            this.fill.set(fraction);
         }

         double drawn = this.dragging ? fraction : this.fill.follow(fraction);
         int filled = (int)Math.round(this.barW * drawn);
         if (filled > 0) {
            PurpleCard.fillRounded(ctx, this.barX, barY, this.barX + Math.max(filled, 4), barY + 6, 3, 15, -3645216, -37177);
         }

         int knobX = this.barX + Math.max(2, Math.min(this.barW - 2, filled));
         boolean overBar = hover && my >= this.y + this.h - 14;
         float grab = Ui.hover(this, "grab", this.dragging || overBar);
         float held = Ui.hover(this, "held", this.dragging);
         if (held > 0.01F) {
            PurpleCard.fillRounded(ctx, knobX - 5, barY - 4, knobX + 5, barY + 10, 5, 15, Ui.alpha(1358917319, held), Ui.alpha(1358917319, held));
         }

         int half = 2 + Math.round(grab);
         int tall = Math.round(grab);
         PurpleCard.fillRounded(ctx, knobX - half, barY - 2 - tall, knobX + half, barY + 8 + tall, 2 + tall, 15, -726785, -726785);
      }
   }

   @Override
   void click(int mx, int my, int button, boolean shift) {
      if (!this.edit.focused() || !this.edit.click(mx, my)) {
         String number = this.format(this.shown());
         int lineHeight = this.noSlider ? this.h : 16;
         if (this.noSlider || Ui.in(mx, my, this.numberLeft(number), this.y, this.w - (this.numberLeft(number) - this.x), lineHeight)) {
            this.edit.setText(this.format(this.get.getAsDouble()));
            this.edit.focus();
         } else if (my >= this.y + this.h - 14) {
            this.dragging = true;
            Ui.dragging = this;
            this.drag(mx, my);
         }
      }
   }

   @Override
   public void drag(int mx, int my) {
      if (this.barW > 0) {
         double fraction = Math.max(0.0, Math.min(1.0, (double)(mx - this.barX) / this.barW));
         double value = this.clampAndRound(this.sliderMin + fraction * (this.sliderMax - this.sliderMin));
         if (this.onRelease) {
            this.pending = value;
         } else if (value != this.get.getAsDouble()) {
            this.set.accept(value);
         }
      }
   }

   @Override
   public void release() {
      if (this.dragging && this.onRelease) {
         this.set.accept(this.pending);
      }

      this.dragging = false;
   }

   private double clampAndRound(double value) {
      if (this.decimals <= 0) {
         value = Math.round(value);
      } else {
         double scale = Math.pow(10.0, this.decimals);
         value = Math.round(value * scale) / scale;
      }

      return Math.max(this.min, Math.min(this.max, value));
   }

   private void commitTyped() {
      try {
         double typed = Double.parseDouble(this.edit.text.trim());
         this.set.accept(this.clampAndRound(typed));
      } catch (NumberFormatException var3) {
      }
   }

   @Override
   void reset() {
      this.reset.run();
   }
}

package com.jfant.specter.gui.screen;

import com.jfant.specter.render.PurpleCard;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

final class OpenCard extends Card {
   private final Supplier<String> value;
   private final Runnable open;
   private final Runnable reset;
   private final IntSupplier swatch;
   private final Supplier<ItemStack> icon;

   OpenCard(String label, String description, Supplier<String> value, IntSupplier swatch, Supplier<ItemStack> icon, Runnable open, Runnable reset) {
      super(label, description);
      this.value = value;
      this.swatch = swatch;
      this.icon = icon;
      this.open = open;
      this.reset = reset;
   }

   @Override
   boolean compact() {
      return true;
   }

   private int decorationWidth() {
      return this.swatch != null ? 20 : (this.icon != null ? 20 : 0);
   }

   @Override
   int minWidth() {
      return Ui.width(this.label) + Ui.width(this.value.get()) + this.decorationWidth() + 18;
   }

   @Override
   int height() {
      return 20;
   }

   @Override
   void content(GuiGraphicsExtractor ctx, int mx, int my, boolean hover) {
      int textY = Ui.textY(this.y, this.h);
      int right = this.x + this.w - 6;
      String text = this.value.get();
      int valueRoom = Math.max(20, this.w - 12 - this.decorationWidth() - Ui.width(this.label) - 6);
      String shown = Ui.fit(text, Math.max(valueRoom, this.w / 3));
      Ui.textRight(ctx, shown, right, textY, Ui.mix(-1, -37177, Ui.hover(this, "value", hover)));
      int left = right - Ui.width(shown) - 4;
      if (this.swatch != null) {
         int sx = left - 16;
         int sy = this.y + (this.h - 10) / 2;
         PurpleCard.fillRounded(ctx, sx - 1, sy - 1, sx + 17, sy + 11, 3, 15, -1, -1);
         PurpleCard.fillRounded(ctx, sx, sy, sx + 16, sy + 10, 2, 15, this.swatch.getAsInt() | 0xFF000000, this.swatch.getAsInt() | 0xFF000000);
         left = sx - 4;
      } else if (this.icon != null) {
         Ui.item(ctx, this.icon.get(), left - 16, this.y + (this.h - 16) / 2);
         left -= 20;
      }

      Ui.text(ctx, Ui.fit(this.label, Math.max(16, left - (this.x + 6))), this.x + 6, textY, -1911561);
   }

   @Override
   void click(int mx, int my, int button, boolean shift) {
      Ui.press(this);
      if (this.open != null) {
         this.open.run();
      }
   }

   @Override
   void reset() {
      if (this.reset != null) {
         this.reset.run();
      }
   }
}

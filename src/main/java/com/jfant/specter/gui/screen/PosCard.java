package com.jfant.specter.gui.screen;

import com.jfant.specter.core.settings.BlockPosSetting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;

final class PosCard extends Card {
   private final BlockPosSetting setting;
   private final TextField[] fields = new TextField[3];

   PosCard(BlockPosSetting setting) {
      super(setting.title, setting.description);
      this.setting = setting;
      String[] names = new String[]{"x", "y", "z"};

      for (int i = 0; i < 3; i++) {
         TextField field = new TextField(names[i]);
         field.maxLength = 10;
         field.filter = cp -> cp >= 48 && cp <= 57 || cp == 45;
         field.onChange = this::apply;
         this.fields[i] = field;
      }

      this.follow();
   }

   @Override
   int height() {
      return 32;
   }

   private void follow() {
      BlockPos pos = this.setting.get();
      this.fields[0].setText(Integer.toString(pos.getX()));
      this.fields[1].setText(Integer.toString(pos.getY()));
      this.fields[2].setText(Integer.toString(pos.getZ()));
   }

   private void apply() {
      try {
         this.setting.set(new BlockPos(Integer.parseInt(this.fields[0].text), Integer.parseInt(this.fields[1].text), Integer.parseInt(this.fields[2].text)));
      } catch (NumberFormatException var2) {
      }
   }

   @Override
   void content(GuiGraphics ctx, int mx, int my, boolean hover) {
      boolean typing = this.fields[0].focused() || this.fields[1].focused() || this.fields[2].focused();
      if (!typing) {
         this.follow();
      }

      Ui.text(ctx, Ui.fit(this.label, this.w - 12), this.x + 6, this.y + 4, -1911561);
      int gap = 4;
      int each = (this.w - 10 - 2 * gap) / 3;

      for (int i = 0; i < 3; i++) {
         this.fields[i].render(ctx, this.x + 5 + i * (each + gap), this.y + 15, each, 13);
      }
   }

   @Override
   void click(int mx, int my, int button, boolean shift) {
      for (TextField field : this.fields) {
         if (field.click(mx, my)) {
            return;
         }
      }
   }

   @Override
   void reset() {
      this.setting.reset();
      this.follow();
   }
}

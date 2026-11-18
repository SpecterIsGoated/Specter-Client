package com.jfant.specter.gui.screen;

import com.jfant.specter.core.settings.StringListSetting;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;

final class StringListEditor extends Editor {
   private static final int ROW = 18;
   private final StringListSetting setting;
   private final TextField field = new TextField("Type, then Enter");

   StringListEditor(StringListSetting setting, Runnable back) {
      super(setting.title, back);
      this.setting = setting;
      if (setting.filter != null) {
         this.field.filter = cp -> setting.filter.filter(this.field.text, (char)cp);
      }

      this.field.onCommit = this::add;
   }

   private void add() {
      String text = this.field.text.trim();
      if (!text.isEmpty()) {
         this.setting.get().add(text);
         this.setting.onChanged();
         this.field.setText("");
      }
   }

   @Override
   void body(GuiGraphics ctx, int mx, int my) {
      int addWidth = 36;
      this.field.render(ctx, this.x + 6, this.y + 2, this.w - 12 - addWidth - 4, 14);
      Ui.button(ctx, "Add", this.x + this.w - 6 - addWidth, this.y + 2, addWidth, 14, Ui.in(mx, my, this.x + this.w - 6 - addWidth, this.y + 2, addWidth, 14));
      List<String> entries = this.setting.get();
      int regionTop = this.y + 20;
      int regionHeight = this.h - 20;
      this.scroll.size(regionHeight, Math.max(1, entries.size()) * 20 + 4);
      int origin = regionTop - this.scroll.offset();
      ctx.enableScissor(this.x, regionTop, this.x + this.w, regionTop + regionHeight);
      if (entries.isEmpty()) {
         Ui.text(ctx, "nothing yet", this.x + 8, origin + 5, -4149795);
      }

      for (int i = 0; i < entries.size(); i++) {
         int ry = origin + i * 20;
         if (ry + 18 >= regionTop && ry <= regionTop + regionHeight) {
            boolean overRegion = Ui.in(mx, my, this.x, regionTop, this.w, regionHeight);
            boolean hover = overRegion && Ui.in(mx, my, this.x + 6, ry, this.w - 16, 18);
            Ui.box(ctx, this.x + 6, ry, this.w - 16, 18, hover);
            Ui.text(ctx, Ui.fit(entries.get(i), this.w - 16 - 30), this.x + 12, Ui.textY(ry, 18), -1);
            boolean overX = overRegion && Ui.in(mx, my, this.x + this.w - 10 - 18, ry, 18, 18);
            Ui.textCentered(ctx, "x", this.x + this.w - 10 - 9, Ui.textY(ry, 18), overX ? -37177 : -4149795);
         }
      }

      ctx.disableScissor();
      this.scroll.bar(ctx, this.x + this.w - 1, regionTop, regionHeight);
   }

   @Override
   void bodyClick(int mx, int my, int button) {
      if (!this.field.click(mx, my)) {
         int addWidth = 36;
         if (Ui.in(mx, my, this.x + this.w - 6 - addWidth, this.y + 2, addWidth, 14)) {
            this.add();
         } else {
            int regionTop = this.y + 20;
            if (my >= regionTop) {
               int row = (my - regionTop + this.scroll.offset()) / 20;
               List<String> entries = this.setting.get();
               if (row >= 0 && row < entries.size()) {
                  if (mx >= this.x + this.w - 10 - 18) {
                     entries.remove(row);
                     this.setting.onChanged();
                  }
               }
            }
         }
      }
   }
}

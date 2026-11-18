package com.jfant.specter.gui.screen;

import com.jfant.specter.core.settings.StringSetting;
import net.minecraft.client.gui.GuiGraphics;

final class TextCard extends Card {
   private final StringSetting setting;
   private final TextField field;

   TextCard(StringSetting setting) {
      super(setting.title, setting.description);
      this.setting = setting;
      this.field = new TextField(setting.placeholder == null ? "" : setting.placeholder);
      this.field.setText(setting.get());
      if (setting.filter != null) {
         this.field.filter = cp -> setting.filter.filter(this.field.text, (char)cp);
      }

      this.field.onChange = () -> setting.set(this.field.text);
   }

   @Override
   int height() {
      return 32;
   }

   @Override
   void content(GuiGraphics ctx, int mx, int my, boolean hover) {
      if (!this.field.focused() && !this.field.text.equals(this.setting.get())) {
         this.field.setText(this.setting.get());
      }

      Ui.text(ctx, Ui.fit(this.label, this.w - 12), this.x + 6, this.y + 4, -1911561);
      this.field.render(ctx, this.x + 5, this.y + 15, this.w - 10, 13);
   }

   @Override
   void click(int mx, int my, int button, boolean shift) {
      this.field.click(mx, my);
   }

   @Override
   void reset() {
      this.setting.reset();
      this.field.setText(this.setting.get());
   }
}

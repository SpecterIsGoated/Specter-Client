package com.jfant.specter.core.gui.themes.meteor.widgets;

import com.jfant.specter.core.gui.renderer.GuiRenderer;
import com.jfant.specter.core.gui.themes.meteor.MeteorWidget;
import com.jfant.specter.core.gui.widgets.WMultiLabel;
import com.jfant.specter.core.utils.render.color.Color;

public class WMeteorMultiLabel extends WMultiLabel implements MeteorWidget {
   public WMeteorMultiLabel(String text, boolean title, double maxWidth) {
      super(text, title, maxWidth);
   }

   @Override
   protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
      double h = this.theme.textHeight(this.title);
      Color defaultColor = this.theme().textColor.get();

      for (int i = 0; i < this.lines.size(); i++) {
         renderer.text(this.lines.get(i), this.x, this.y + h * i, this.color != null ? this.color : defaultColor, false);
      }
   }
}

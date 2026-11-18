package com.jfant.specter.core.gui.themes.meteor.widgets;

import com.jfant.specter.core.gui.renderer.GuiRenderer;
import com.jfant.specter.core.gui.themes.meteor.MeteorWidget;
import com.jfant.specter.core.gui.widgets.WTooltip;

public class WMeteorTooltip extends WTooltip implements MeteorWidget {
   public WMeteorTooltip(String text) {
      super(text);
   }

   @Override
   protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
      renderer.quad(this, this.theme().backgroundColor.get());
   }
}

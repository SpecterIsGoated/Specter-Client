package com.jfant.specter.gui.widgets;

import com.jfant.specter.core.gui.renderer.GuiRenderer;
import com.jfant.specter.core.gui.themes.meteor.MeteorWidget;
import com.jfant.specter.core.gui.widgets.WTooltip;
import com.jfant.specter.gui.Glass;

public class WSpecterTooltip extends WTooltip implements MeteorWidget {
   public WSpecterTooltip(String text) {
      super(text);
   }

   @Override
   protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
      Glass.pane(renderer, this.x, this.y, this.width, this.height, this.theme.scale(2.0), true);
   }
}

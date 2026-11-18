package com.jfant.specter.core.gui.themes.meteor.widgets;

import com.jfant.specter.core.gui.renderer.GuiRenderer;
import com.jfant.specter.core.gui.widgets.WQuad;
import com.jfant.specter.core.utils.render.color.Color;

public class WMeteorQuad extends WQuad {
   public WMeteorQuad(Color color) {
      super(color);
   }

   @Override
   protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
      renderer.quad(this.x, this.y, this.width, this.height, this.color);
   }
}

package com.jfant.specter.gui.widgets;

import com.jfant.specter.core.gui.renderer.GuiRenderer;
import com.jfant.specter.core.gui.themes.meteor.MeteorGuiTheme;
import com.jfant.specter.core.gui.themes.meteor.MeteorWidget;
import com.jfant.specter.core.gui.widgets.input.WSlider;
import com.jfant.specter.core.utils.render.color.Color;
import com.jfant.specter.gui.Glass;

public class WSpecterSlider extends WSlider implements MeteorWidget {
   private final Color fillEnd = new Color();

   public WSpecterSlider(double value, double min, double max) {
      super(value, min, max);
   }

   @Override
   protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
      MeteorGuiTheme theme = this.theme();
      double valueWidth = this.valueWidth();
      double s = theme.scale(3.0);
      double handle = this.handleSize();
      double bx = this.x + handle / 2.0;
      double by = this.y + this.height / 2.0 - s / 2.0;
      double share = this.width - handle <= 0.0 ? 0.0 : valueWidth / (this.width - handle);
      Glass.lerp(Glass.PURPLE, Glass.PINK, share, this.fillEnd);
      this.fillEnd.a = 255;
      renderer.quad(bx, by, valueWidth, s, Glass.PURPLE, this.fillEnd, this.fillEnd, Glass.PURPLE);
      renderer.quad(bx + valueWidth, by, this.width - valueWidth - handle, s, theme.sliderRight.get());
      renderer.quad(this.x + valueWidth, this.y, handle, handle, GuiRenderer.CIRCLE, theme.sliderHandle.get(this.dragging, this.handleMouseOver));
   }
}

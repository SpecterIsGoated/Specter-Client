package com.jfant.specter.core.gui.themes.meteor.widgets.pressable;

import com.jfant.specter.core.gui.renderer.GuiRenderer;
import com.jfant.specter.core.gui.themes.meteor.MeteorGuiTheme;
import com.jfant.specter.core.gui.themes.meteor.MeteorWidget;
import com.jfant.specter.core.gui.widgets.pressable.WConfirmedMinus;
import com.jfant.specter.core.utils.render.color.Color;

public class WMeteorConfirmedMinus extends WConfirmedMinus implements MeteorWidget {
   @Override
   protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
      MeteorGuiTheme theme = this.theme();
      double pad = this.pad();
      double s = theme.scale(3.0);
      Color outline = theme.outlineColor.get(this.pressed, this.mouseOver);
      Color fg = (Color)(this.pressedOnce ? theme.backgroundColor.get(this.pressed, this.mouseOver) : this.theme().minusColor.get());
      Color bg = (Color)(this.pressedOnce ? this.theme().minusColor.get() : theme.backgroundColor.get(this.pressed, this.mouseOver));
      this.renderBackground(renderer, this, outline, bg);
      renderer.quad(this.x + pad, this.y + this.height / 2.0 - s / 2.0, this.width - pad * 2.0, s, fg);
   }
}

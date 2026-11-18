package com.jfant.specter.core.gui.themes.meteor.widgets.pressable;

import com.jfant.specter.core.gui.renderer.GuiRenderer;
import com.jfant.specter.core.gui.renderer.packer.GuiTexture;
import com.jfant.specter.core.gui.themes.meteor.MeteorGuiTheme;
import com.jfant.specter.core.gui.themes.meteor.MeteorWidget;
import com.jfant.specter.core.gui.widgets.pressable.WConfirmedButton;
import com.jfant.specter.core.utils.render.color.Color;

public class WMeteorConfirmedButton extends WConfirmedButton implements MeteorWidget {
   public WMeteorConfirmedButton(String text, String confirmText, GuiTexture texture) {
      super(text, confirmText, texture);
   }

   @Override
   protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
      MeteorGuiTheme theme = this.theme();
      double pad = this.pad();
      Color outline = theme.outlineColor.get(this.pressed, this.mouseOver);
      Color fg = (Color)(this.pressedOnce ? theme.backgroundColor.get(this.pressed, this.mouseOver) : theme.textColor.get());
      Color bg = (Color)(this.pressedOnce ? theme.textColor.get() : theme.backgroundColor.get(this.pressed, this.mouseOver));
      this.renderBackground(renderer, this, outline, bg);
      String text = this.getText();
      if (text != null) {
         renderer.text(text, this.x + this.width / 2.0 - this.textWidth / 2.0, this.y + pad, fg, false);
      } else {
         double ts = theme.textHeight();
         renderer.quad(this.x + this.width / 2.0 - ts / 2.0, this.y + pad, ts, ts, this.texture, fg);
      }
   }
}

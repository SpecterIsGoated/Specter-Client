package com.jfant.specter.core.gui.themes.meteor.widgets.pressable;

import com.jfant.specter.core.gui.renderer.GuiRenderer;
import com.jfant.specter.core.gui.renderer.packer.GuiTexture;
import com.jfant.specter.core.gui.themes.meteor.MeteorGuiTheme;
import com.jfant.specter.core.gui.themes.meteor.MeteorWidget;
import com.jfant.specter.core.gui.widgets.pressable.WButton;

public class WMeteorButton extends WButton implements MeteorWidget {
   public WMeteorButton(String text, GuiTexture texture) {
      super(text, texture);
   }

   @Override
   protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
      MeteorGuiTheme theme = this.theme();
      double pad = this.pad();
      this.renderBackground(renderer, this, this.pressed, this.mouseOver);
      if (this.text != null) {
         renderer.text(this.text, this.x + this.width / 2.0 - this.textWidth / 2.0, this.y + pad, theme.textColor.get(), false);
      } else {
         double ts = theme.textHeight();
         renderer.quad(this.x + this.width / 2.0 - ts / 2.0, this.y + pad, ts, ts, this.texture, theme.textColor.get());
      }
   }
}

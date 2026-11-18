package com.jfant.specter.core.gui.themes.meteor.widgets;

import com.jfant.specter.core.gui.renderer.GuiRenderer;
import com.jfant.specter.core.gui.themes.meteor.MeteorWidget;
import com.jfant.specter.core.gui.widgets.containers.WView;

public class WMeteorView extends WView implements MeteorWidget {
   @Override
   protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
      if (this.canScroll && this.hasScrollBar) {
         renderer.quad(
            this.handleX(), this.handleY(), this.handleWidth(), this.handleHeight(), this.theme().scrollbarColor.get(this.focused, this.handleMouseOver)
         );
      }
   }
}

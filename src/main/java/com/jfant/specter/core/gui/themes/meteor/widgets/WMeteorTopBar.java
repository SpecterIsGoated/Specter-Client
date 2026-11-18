package com.jfant.specter.core.gui.themes.meteor.widgets;

import com.jfant.specter.core.gui.themes.meteor.MeteorWidget;
import com.jfant.specter.core.gui.widgets.WTopBar;
import com.jfant.specter.core.utils.render.color.Color;

public class WMeteorTopBar extends WTopBar implements MeteorWidget {
   @Override
   protected Color getButtonColor(boolean pressed, boolean hovered) {
      return this.theme().backgroundColor.get(pressed, hovered);
   }

   @Override
   protected Color getNameColor() {
      return this.theme().textColor.get();
   }
}

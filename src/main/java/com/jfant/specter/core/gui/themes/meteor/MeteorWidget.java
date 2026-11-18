package com.jfant.specter.core.gui.themes.meteor;

import com.jfant.specter.core.gui.renderer.GuiRenderer;
import com.jfant.specter.core.gui.utils.BaseWidget;
import com.jfant.specter.core.gui.widgets.WWidget;
import com.jfant.specter.core.utils.render.color.Color;

public interface MeteorWidget extends BaseWidget {
   default MeteorGuiTheme theme() {
      return (MeteorGuiTheme)this.getTheme();
   }

   default void renderBackground(GuiRenderer renderer, WWidget widget, Color outlineColor, Color backgroundColor) {
      MeteorGuiTheme theme = this.theme();
      double s = theme.scale(2.0);
      renderer.quad(widget.x + s, widget.y + s, widget.width - s * 2.0, widget.height - s * 2.0, backgroundColor);
      renderer.quad(widget.x, widget.y, widget.width, s, outlineColor);
      renderer.quad(widget.x, widget.y + widget.height - s, widget.width, s, outlineColor);
      renderer.quad(widget.x, widget.y + s, s, widget.height - s * 2.0, outlineColor);
      renderer.quad(widget.x + widget.width - s, widget.y + s, s, widget.height - s * 2.0, outlineColor);
   }

   default void renderBackground(GuiRenderer renderer, WWidget widget, boolean pressed, boolean mouseOver) {
      MeteorGuiTheme theme = this.theme();
      this.renderBackground(renderer, widget, theme.outlineColor.get(pressed, mouseOver), theme.backgroundColor.get(pressed, mouseOver));
   }
}

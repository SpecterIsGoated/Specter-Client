package com.jfant.specter.gui.widgets;

import com.jfant.specter.core.gui.renderer.GuiRenderer;
import com.jfant.specter.core.gui.themes.meteor.MeteorWidget;
import com.jfant.specter.core.gui.widgets.WWidget;
import com.jfant.specter.core.gui.widgets.containers.WWindow;
import com.jfant.specter.gui.Glass;

public class WSpecterWindow extends WWindow implements MeteorWidget {
   public WSpecterWindow(WWidget icon, String title) {
      super(icon, title);
   }

   @Override
   protected WWindow.WHeader header(WWidget icon) {
      return new WSpecterWindow.WSpecterHeader(icon);
   }

   @Override
   protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
      if (this.expanded || this.animProgress > 0.0) {
         Glass.pane(renderer, this.x, this.y + this.header.height, this.width, this.height - this.header.height, this.theme.scale(2.0), false);
      }
   }

   private class WSpecterHeader extends WWindow.WHeader {
      WSpecterHeader(WWidget icon) {
         super(icon);
      }

      @Override
      protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
         Glass.header(
            renderer,
            this.x,
            this.y,
            this.width,
            this.height,
            this.theme.scale(2.0),
            WSpecterWindow.this.title == null ? 0 : WSpecterWindow.this.title.hashCode()
         );
      }
   }
}

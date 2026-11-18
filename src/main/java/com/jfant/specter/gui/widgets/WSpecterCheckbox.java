package com.jfant.specter.gui.widgets;

import com.jfant.specter.core.gui.renderer.GuiRenderer;
import com.jfant.specter.core.gui.themes.meteor.MeteorWidget;
import com.jfant.specter.core.gui.widgets.pressable.WCheckbox;
import com.jfant.specter.core.utils.render.color.Color;
import com.jfant.specter.gui.Glass;
import net.minecraft.util.Mth;

public class WSpecterCheckbox extends WCheckbox implements MeteorWidget {
   private final Color top = new Color();
   private final Color bottom = new Color();
   private double animProgress;

   public WSpecterCheckbox(boolean checked) {
      super(checked);
      this.animProgress = checked ? 1.0 : 0.0;
   }

   @Override
   protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
      this.animProgress = Mth.clamp(this.animProgress + (this.checked ? 1 : -1) * delta * 14.0, 0.0, 1.0);
      this.renderBackground(renderer, this, this.pressed, this.mouseOver);
      if (this.animProgress > 0.0) {
         double size = (this.width - this.theme.scale(2.0)) / 1.75 * this.animProgress;
         Glass.lerp(Glass.PINK, Glass.PURPLE, 0.0, this.top);
         Glass.lerp(Glass.PINK, Glass.PURPLE, 0.6, this.bottom);
         this.top.a = this.bottom.a = 255;
         renderer.quad(this.x + (this.width - size) / 2.0, this.y + (this.height - size) / 2.0, size, size, this.top, this.top, this.bottom, this.bottom);
      }
   }
}

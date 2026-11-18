package com.jfant.specter.gui.widgets;

import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.gui.renderer.GuiRenderer;
import com.jfant.specter.core.gui.themes.meteor.MeteorGuiTheme;
import com.jfant.specter.core.gui.themes.meteor.MeteorWidget;
import com.jfant.specter.core.gui.utils.AlignmentX;
import com.jfant.specter.core.gui.widgets.pressable.WPressable;
import com.jfant.specter.core.systems.modules.Module;
import com.jfant.specter.core.utils.render.color.Color;
import com.jfant.specter.gui.Glass;
import net.minecraft.util.Mth;

public class WSpecterModule extends WPressable implements MeteorWidget {
   private static final Color WASH = new Color(255, 110, 199, 70);
   private static final Color WASH_END = new Color(255, 110, 199, 0);
   private static final Color HOVER = new Color(200, 160, 245, 40);
   private static final Color HOVER_END = new Color(200, 160, 245, 8);
   private static final Color GLOW = new Color(255, 110, 199, 60);
   private static final Color OFF_TEXT = new Color(214, 200, 236, 255);
   private static final Color ON_TEXT = new Color(255, 255, 255, 255);
   private final Module module;
   private final String title;
   private double titleWidth;
   private double hover;
   private double active;
   private final Color wash = new Color();
   private final Color washEnd = new Color();
   private final Color hoverNow = new Color();
   private final Color hoverEnd = new Color();
   private final Color text = new Color();

   public WSpecterModule(Module module, String title) {
      this.module = module;
      this.title = title;
      this.tooltip = module.description;
      this.active = module.isActive() ? 1.0 : 0.0;
   }

   @Override
   public double pad() {
      return this.theme.scale(4.0);
   }

   @Override
   protected void onCalculateSize() {
      double pad = this.pad();
      if (this.titleWidth == 0.0) {
         this.titleWidth = this.theme.textWidth(this.title);
      }

      this.width = pad + this.titleWidth + pad;
      this.height = pad + this.theme.textHeight() + pad;
   }

   @Override
   protected void onPressed(int button) {
      if (button == 0) {
         this.module.toggle();
      } else if (button == 1) {
         SpecterCore.mc.gui.setScreen(this.theme.moduleScreen(this.module));
      }
   }

   @Override
   protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
      MeteorGuiTheme theme = this.theme();
      double pad = this.pad();
      this.hover = Mth.clamp(this.hover + delta * 6.0 * (this.mouseOver ? 1 : -1), 0.0, 1.0);
      this.active = Mth.clamp(this.active + delta * 5.0 * (this.module.isActive() ? 1 : -1), 0.0, 1.0);
      if (this.hover > 0.0) {
         scale(HOVER, this.hover, this.hoverNow);
         scale(HOVER_END, this.hover, this.hoverEnd);
         renderer.quad(this.x, this.y, this.width, this.height, this.hoverNow, this.hoverEnd, this.hoverEnd, this.hoverNow);
      }

      if (this.active > 0.0) {
         scale(WASH, this.active, this.wash);
         scale(WASH_END, this.active, this.washEnd);
         renderer.quad(this.x, this.y, this.width * (0.4 + 0.6 * this.active), this.height, this.wash, this.washEnd, this.washEnd, this.wash);
         double barHeight = this.height * this.active;
         double barY = this.y + (this.height - barHeight) / 2.0;
         renderer.quad(this.x, barY, theme.scale(5.0), barHeight, GLOW);
         renderer.quad(this.x, barY, theme.scale(2.0), barHeight, theme.accentColor.get());
      }

      double tx = this.x + pad;
      double w = this.width - pad * 2.0;
      if (theme.moduleAlignment.get() == AlignmentX.Center) {
         tx += w / 2.0 - this.titleWidth / 2.0;
      } else if (theme.moduleAlignment.get() == AlignmentX.Right) {
         tx += w - this.titleWidth;
      } else {
         tx += theme.scale(3.0) * this.active;
      }

      Glass.lerp(OFF_TEXT, ON_TEXT, this.active, this.text);
      renderer.text(this.title, tx, this.y + pad, this.text, false);
   }

   private static void scale(Color base, double k, Color into) {
      into.set(base.r, base.g, base.b, (int)(base.a * k));
   }
}

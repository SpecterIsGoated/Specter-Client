package com.jfant.specter.gui;

import com.jfant.specter.core.gui.themes.meteor.MeteorGuiTheme;
import com.jfant.specter.core.gui.widgets.WTooltip;
import com.jfant.specter.core.gui.widgets.WWidget;
import com.jfant.specter.core.gui.widgets.containers.WWindow;
import com.jfant.specter.core.gui.widgets.input.WSlider;
import com.jfant.specter.core.gui.widgets.pressable.WCheckbox;
import com.jfant.specter.core.settings.ColorSetting;
import com.jfant.specter.core.systems.modules.Module;
import com.jfant.specter.core.utils.render.color.SettingColor;
import com.jfant.specter.gui.widgets.WSpecterCheckbox;
import com.jfant.specter.gui.widgets.WSpecterModule;
import com.jfant.specter.gui.widgets.WSpecterSlider;
import com.jfant.specter.gui.widgets.WSpecterTooltip;
import com.jfant.specter.gui.widgets.WSpecterWindow;
import com.jfant.specter.mixin.GuiThemeAccessor;

public class SpecterGuiTheme extends MeteorGuiTheme {
   public SpecterGuiTheme() {
      ((GuiThemeAccessor)this).specter$setName("Specter");
      this.recolor("accent-color", 255, 110, 199, 255);
      this.recolor("checkbox-color", 255, 110, 199, 255);
      this.recolor("text-color", 236, 226, 250, 255);
      this.recolor("text-secondary-text-color", 192, 173, 221, 255);
      this.recolor("title-text-color", 239, 189, 255, 255);
      this.recolor("text-highlight-color", 255, 110, 199, 90);
      this.recolor("background-color", 40, 18, 76, 200);
      this.recolor("hovered-background-color", 60, 28, 104, 210);
      this.recolor("pressed-background-color", 78, 36, 128, 220);
      this.recolor("module-background-color", 120, 60, 180, 70);
      this.recolor("outline-color", 127, 63, 178, 216);
      this.recolor("hovered-outline-color", 165, 100, 220, 230);
      this.recolor("pressed-outline-color", 205, 160, 245, 240);
      this.recolor("separator-text-color", 239, 189, 255, 255);
      this.recolor("separator-center-color", 205, 160, 245, 255);
      this.recolor("separator-edges-color", 127, 63, 178, 150);
      this.recolor("Scrollbar-color", 70, 34, 116, 200);
      this.recolor("hovered-Scrollbar-color", 100, 50, 150, 210);
      this.recolor("pressed-Scrollbar-color", 130, 66, 180, 220);
      this.recolor("slider-handle-color", 255, 110, 199, 255);
      this.recolor("hovered-slider-handle-color", 255, 145, 215, 255);
      this.recolor("pressed-slider-handle-color", 255, 175, 228, 255);
      this.recolor("slider-left-color", 200, 90, 220, 255);
      this.recolor("slider-right-color", 40, 18, 76, 200);
   }

   private void recolor(String name, int r, int g, int b, int a) {
      if (this.settings.get(name) instanceof ColorSetting color) {
         SettingColor value = color.getDefaultValue();
         value.set(r, g, b, a);
         color.reset();
      }
   }

   @Override
   public WWindow window(WWidget icon, String title) {
      return this.w(new WSpecterWindow(icon, title));
   }

   @Override
   public WWidget module(Module module, String title) {
      return this.w(new WSpecterModule(module, title));
   }

   @Override
   public WCheckbox checkbox(boolean checked) {
      return this.w(new WSpecterCheckbox(checked));
   }

   @Override
   public WSlider slider(double value, double min, double max) {
      return this.w(new WSpecterSlider(value, min, max));
   }

   @Override
   public WTooltip tooltip(String text) {
      return this.w(new WSpecterTooltip(text));
   }
}

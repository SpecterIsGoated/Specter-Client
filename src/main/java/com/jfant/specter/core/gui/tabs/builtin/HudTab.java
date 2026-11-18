package com.jfant.specter.core.gui.tabs.builtin;

import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.gui.GuiTheme;
import com.jfant.specter.core.gui.renderer.GuiRenderer;
import com.jfant.specter.core.gui.tabs.Tab;
import com.jfant.specter.core.gui.tabs.TabScreen;
import com.jfant.specter.core.gui.tabs.WindowTabScreen;
import com.jfant.specter.core.gui.widgets.containers.WContainer;
import com.jfant.specter.core.gui.widgets.containers.WHorizontalList;
import com.jfant.specter.core.gui.widgets.pressable.WButton;
import com.jfant.specter.core.gui.widgets.pressable.WCheckbox;
import com.jfant.specter.core.systems.hud.Hud;
import com.jfant.specter.core.systems.hud.screens.HudEditorScreen;
import com.jfant.specter.core.utils.misc.NbtUtils;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

public class HudTab extends Tab {
   public HudTab() {
      super("HUD");
   }

   @Override
   public TabScreen createScreen(GuiTheme theme) {
      return new HudTab.HudScreen(theme, this);
   }

   @Override
   public boolean isScreen(Screen screen) {
      return screen instanceof HudTab.HudScreen;
   }

   public static class HudScreen extends WindowTabScreen {
      private WContainer settingsContainer;
      private final Hud hud = Hud.get();

      public HudScreen(GuiTheme theme, Tab tab) {
         super(theme, tab);
         this.hud.settings.onActivated();
      }

      @Override
      public void initWidgets() {
         this.settingsContainer = this.add(this.theme.verticalList()).expandX().widget();
         this.settingsContainer.add(this.theme.settings(this.hud.settings)).expandX().widget();
         this.add(this.theme.horizontalSeparator()).expandX();
         WButton openEditor = this.add(this.theme.button("Edit")).expandX().widget();
         openEditor.action = () -> SpecterCore.mc.setScreen(new HudEditorScreen(this.theme));
         WHorizontalList buttons = this.add(this.theme.horizontalList()).expandX().widget();
         buttons.add(this.theme.confirmedButton("Clear", "Confirm")).expandX().widget().action = this.hud::clear;
         buttons.add(this.theme.confirmedButton("Reset to default elements", "Confirm")).expandX().widget().action = this.hud::resetToDefaultElements;
         this.add(this.theme.horizontalSeparator()).expandX();
         WHorizontalList bottom = this.add(this.theme.horizontalList()).expandX().widget();
         bottom.add(this.theme.label("Active: "));
         WCheckbox active = bottom.add(this.theme.checkbox(this.hud.active)).expandCellX().widget();
         active.action = () -> this.hud.active = active.checked;
         WButton resetSettings = bottom.add(this.theme.button(GuiRenderer.RESET)).widget();
         resetSettings.action = this.hud.settings::reset;
         resetSettings.tooltip = "Reset";
      }

      @Override
      protected void onRenderBefore(GuiGraphics drawContext, float delta) {
         HudEditorScreen.renderElements(drawContext);
      }

      @Override
      public void tick() {
         super.tick();
         this.hud.settings.tick(this.settingsContainer, this.theme);
      }

      @Override
      public boolean toClipboard() {
         return NbtUtils.toClipboard(this.hud);
      }

      @Override
      public boolean fromClipboard() {
         return NbtUtils.fromClipboard(this.hud);
      }
   }
}

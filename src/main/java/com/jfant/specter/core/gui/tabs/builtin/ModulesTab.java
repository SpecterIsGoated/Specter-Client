package com.jfant.specter.core.gui.tabs.builtin;

import com.jfant.specter.core.gui.GuiTheme;
import com.jfant.specter.core.gui.GuiThemes;
import com.jfant.specter.core.gui.tabs.Tab;
import com.jfant.specter.core.gui.tabs.TabScreen;
import net.minecraft.client.gui.screens.Screen;

public class ModulesTab extends Tab {
   public ModulesTab() {
      super("Modules");
   }

   @Override
   public TabScreen createScreen(GuiTheme theme) {
      return theme.modulesScreen();
   }

   @Override
   public boolean isScreen(Screen screen) {
      return GuiThemes.get().isModulesScreen(screen);
   }
}
